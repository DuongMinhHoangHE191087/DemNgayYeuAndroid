'use strict';

// M-23: nối callable / webhook / trigger entitlement vào Firebase Functions. Nạp src/entitlement/{index,options,runtime}.js trong `vm` với
// fakeRequire (cùng kỹ thuật test 12 của rateLimit.test.js): ghi lại tuỳ chọn truyền cho onCall/onRequest/...; không chạm Firebase thật.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const { HttpsError } = require('firebase-functions/v2/https');
const cfg = require('../../src/config');
const { generateSigningKey, secretJson } = require('../../scripts/make-signing-key');

const SRC = path.join(__dirname, '..', '..', 'src', 'entitlement');

function load(file, modules) {
  const src = fs.readFileSync(path.join(SRC, file), 'utf8');
  const fakeRequire = (id) => {
    if (!(id in modules)) throw new Error(`wiring test: require chưa được giả: ${id}`);
    return modules[id];
  };
  const mod = { exports: {} };
  vm.runInContext(`(function (exports, require, module) {${src}\n})`, vm.createContext({}))(mod.exports, fakeRequire, mod);
  return mod.exports;
}

// defineSecret/defineString giả: ghi lại tên mỗi lần .value() được gọi (để chứng minh không đọc param lúc nạp module).
function fakeParams(values = {}) {
  const reads = [];
  const make = (kind) => (name, opts) => ({ name, kind, opts, value: () => { reads.push(name); return values[name] ?? ''; } });
  return { reads, params: { defineSecret: make('secret'), defineString: make('string') } };
}

const loadOptions = (values) => {
  const p = fakeParams(values);
  return { ...p, options: load('options.js', { 'firebase-functions/params': p.params, '../config': cfg }) };
};

const DEPS = { fake: 'deps' };

// index.js với options thật (params giả), runtime và module nghiệp vụ giả ghi vào `log`.
function loadIndex() {
  const { options } = loadOptions();
  const log = [];
  const seen = { logged: [] };
  const rec = (kind) => (opts, handler) => ({ kind, opts, handler });
  const runtime = {
    requireUid: (request) => { log.push('requireUid'); return request.auth.uid; },
    limit: async (name, uid) => { log.push(`limit:${name}:${uid}`); },
    makeRealDeps: () => DEPS,
  };
  const modules = {
    'firebase-functions/v2/https': { onCall: rec('onCall'), onRequest: rec('onRequest'), HttpsError },
    'firebase-functions/v2/pubsub': { onMessagePublished: rec('onMessagePublished') },
    'firebase-functions/v2/firestore': { onDocumentWritten: rec('onDocumentWritten') },
    'firebase-functions/logger': { error: (...a) => { seen.logged.push(a); }, warn() {}, info() {} },
    './options': options,
    './runtime': runtime,
    './accountToken': { getOrCreateAccountToken: async (deps, args) => { seen.account = { deps, args }; return 'tok-1'; } },
    './claimPurchase': {
      parseClaimInput: (data) => {
        if (data.store === 'bad') throw new HttpsError('invalid-argument', 'bad store');
        return { store: data.store, id: data.id };
      },
      claimPurchase: async (deps, args) => { seen.claim = { deps, args }; return { vip: false }; },
    },
    './getEntitlement': {
      parseNonce: (data) => {
        if (data.nonce === 'bad') throw new HttpsError('invalid-argument', 'bad nonce');
        return data.nonce;
      },
      getEntitlement: async (deps, args) => { seen.entitlement = { deps, args }; return { vip: false }; },
    },
    './appleNotifications': {
      handleAppleNotification: async (deps, args) => {
        seen.apple = { deps, args };
        if (seen.appleThrow) throw seen.appleThrow;
        return seen.appleResult ?? { httpStatus: 200 };
      },
    },
    './googleNotifications': { handleGoogleNotification: async (deps, args) => { seen.google = { deps, args }; return {}; } },
    './release': { releaseEntitlementBindings: async (deps, args) => { seen.release = { deps, args }; return {}; } },
  };
  return { exp: load('index.js', modules), options, log, seen };
}

function fakeRes() {
  const res = { code: null, sent: false, status(n) { this.code = n; return this; }, send() { this.sent = true; return this; } };
  return res;
}

const NAMES = ['getAccountToken', 'claimPurchase', 'getEntitlement', 'appleNotifications', 'googlePlayNotifications', 'releaseEntitlementBindings'];
const CALLABLES = ['getAccountToken', 'claimPurchase', 'getEntitlement'];
const STORE_FUNCTIONS = ['claimPurchase', 'getEntitlement', 'appleNotifications', 'googlePlayNotifications'];
const secretNames = (opts) => Array.from(opts.secrets ?? [], (s) => s.name).sort();

test('callables_enforce_app_check', () => {
  const { exp, options } = loadIndex();
  const onCalls = Object.values(exp).filter((e) => e.kind === 'onCall');
  assert.equal(onCalls.length, 3);
  for (const { opts } of onCalls) {
    assert.ok(opts === options.callableOptions || opts === options.storeCallableOptions, 'onCall phải dùng callableOptions hoặc storeCallableOptions');
    assert.equal(opts.enforceAppCheck, true);
  }
  assert.equal(options.callableOptions.region, cfg.region);
  assert.equal(options.callableOptions.maxInstances, 10);
});

test('exports_haveSixNames', () => {
  assert.deepEqual(Object.keys(loadIndex().exp).sort(), [...NAMES].sort());
});

test('rateLimits_haveNewKeys', () => {
  const hour = 60 * 60 * 1000;
  assert.deepEqual({ ...cfg.rateLimits.getAccountToken }, { max: 20, windowMs: hour });
  assert.deepEqual({ ...cfg.rateLimits.claimPurchase }, { max: 30, windowMs: hour });
  assert.deepEqual({ ...cfg.rateLimits.getEntitlement }, { max: 60, windowMs: hour });
  assert.ok(Object.isFrozen(cfg.rateLimits.getEntitlement));
});

test('secretsBinding_perFunction', () => {
  const { exp, options } = loadIndex();
  const store = ['APPLE_IAP_PRIVATE_KEY', 'ENTITLEMENT_SIGNING_KEY'];
  assert.deepEqual([...options.STORE_SECRETS.map((s) => s.name)].sort(), store);
  for (const name of STORE_FUNCTIONS) assert.deepEqual(secretNames(exp[name].opts), store, `${name} phải bind STORE_SECRETS`);
  for (const name of ['getAccountToken', 'releaseEntitlementBindings']) {
    assert.deepEqual(secretNames(exp[name].opts), [], `${name} không được bind secret`);
  }
});

test('callables_limitRunsBeforeBusiness', async () => {
  const { exp, log, seen } = loadIndex();
  await exp.getAccountToken.handler({ auth: { uid: 'u1' }, data: {} });
  await exp.claimPurchase.handler({ auth: { uid: 'u1' }, data: { store: 'apple', id: 'tx-1', uid: 'evil' } });
  await exp.getEntitlement.handler({ auth: { uid: 'u1' }, data: { nonce: 'n'.repeat(16) } });
  assert.deepEqual(log, CALLABLES.flatMap((n) => ['requireUid', `limit:${n}:u1`]));
  assert.equal(seen.account.deps, DEPS);
  assert.equal(seen.account.args.uid, 'u1');
  assert.equal(seen.claim.args.uid, 'u1');
  assert.equal(seen.claim.args.store, 'apple');
  assert.equal(Object.hasOwn(seen.claim.args, 'evil'), false);
  assert.equal(seen.entitlement.args.uid, 'u1');
  assert.equal(seen.entitlement.args.nonce, 'n'.repeat(16));
});

test('retry_enabledOnRtdnAndRelease', () => {
  const { exp } = loadIndex();
  assert.equal(exp.googlePlayNotifications.kind, 'onMessagePublished');
  assert.equal(exp.googlePlayNotifications.opts.topic, 'play-rtdn');
  assert.equal(exp.googlePlayNotifications.opts.retry, true);
  assert.equal(exp.releaseEntitlementBindings.kind, 'onDocumentWritten');
  assert.equal(exp.releaseEntitlementBindings.opts.document, 'accountDeletions/{uid}');
  assert.equal(exp.releaseEntitlementBindings.opts.retry, true);
});

test('releaseTrigger_onlyWhenStateDone', async () => {
  const { exp, seen } = loadIndex();
  const ev = (state) => ({ params: { uid: 'u9' }, data: { after: { exists: true, get: () => state } } });
  await exp.releaseEntitlementBindings.handler(ev('pending'));
  assert.equal(seen.release, undefined);
  await exp.releaseEntitlementBindings.handler({ params: { uid: 'u9' }, data: { after: { exists: false } } });
  assert.equal(seen.release, undefined);
  await exp.releaseEntitlementBindings.handler(ev('done'));
  assert.equal(seen.release.deps, DEPS);
  assert.equal(seen.release.args.uid, 'u9');
});

test('appleRequest_getMethod_405', async () => {
  const { exp, seen } = loadIndex();
  const res = fakeRes();
  await exp.appleNotifications.handler({ method: 'GET', body: { signedPayload: 'x' } }, res);
  assert.equal(res.code, 405);
  assert.equal(seen.apple, undefined);
});

test('appleRequest_postMissingBody_400', async () => {
  const { exp, seen } = loadIndex();
  for (const body of [undefined, null, 'text']) {
    const res = fakeRes();
    await exp.appleNotifications.handler({ method: 'POST', body }, res);
    assert.equal(res.code, 400);
  }
  assert.equal(seen.apple, undefined);
});

test('appleRequest_postValid_usesHandlerStatus', async () => {
  const { exp, seen } = loadIndex();
  for (const httpStatus of [200, 400, 500]) {
    seen.appleResult = { httpStatus };
    const res = fakeRes();
    await exp.appleNotifications.handler({ method: 'POST', body: { signedPayload: 'jws' } }, res);
    assert.equal(res.code, httpStatus);
    assert.equal(res.sent, true);
    assert.equal(seen.apple.deps, DEPS);
    assert.equal(seen.apple.args.signedPayload, 'jws');
  }
});

test('appleRequest_unexpectedThrow_500', async () => {
  const { exp } = loadIndex();
  // Handler ném không phải do dữ liệu: deps hỏng (secret/param thiếu) cũng phải ra 500 để Apple gửi lại, không lộ stack.
  const bad = load('index.js', {
    'firebase-functions/v2/https': { onCall: () => ({}), onRequest: (opts, handler) => ({ handler }), HttpsError },
    'firebase-functions/v2/pubsub': { onMessagePublished: () => ({}) },
    'firebase-functions/v2/firestore': { onDocumentWritten: () => ({}) },
    'firebase-functions/logger': { error() {} },
    './options': loadOptions().options,
    './runtime': { requireUid() {}, limit() {}, makeRealDeps: () => { throw new Error('boom'); } },
    './accountToken': {}, './claimPurchase': {}, './getEntitlement': {}, './release': {}, './googleNotifications': {},
    './appleNotifications': { handleAppleNotification: async () => ({ httpStatus: 200 }) },
  });
  const res = fakeRes();
  await bad.appleNotifications.handler({ method: 'POST', body: { signedPayload: 'jws' } }, res);
  assert.equal(res.code, 500);
  assert.ok(exp.appleNotifications);
});

test('appleRequest_handlerThrows_500AndLogsOnlyTheCode', async () => {
  const { exp, seen } = loadIndex();
  seen.appleThrow = Object.assign(new Error('chi tiết nhạy cảm: signedPayload=jws-secret'), { code: 'ECODE' });
  const res = fakeRes();
  await exp.appleNotifications.handler({ method: 'POST', body: { signedPayload: 'jws-secret' } }, res);
  assert.equal(res.code, 500);
  assert.equal(seen.logged.length, 1);
  assert.deepEqual({ ...seen.logged[0][1] }, { code: 'ECODE' }); // object tạo trong vm context khác realm: sao chép trước khi so sánh
  assert.equal('message' in seen.logged[0][1], false, 'không được log cả đối tượng lỗi (message/stack), chỉ mã');
  assert.equal(JSON.stringify(seen.logged).includes('jws-secret'), false, 'không được ghi thân yêu cầu/thông điệp lỗi vào log');
});

test('callables_invalidInput_rejectedByParsers_neverReachBusiness', async () => {
  const { exp, seen } = loadIndex();
  await assert.rejects(exp.claimPurchase.handler({ auth: { uid: 'u1' }, data: { store: 'bad', id: 'x' } }), (e) => e.code === 'invalid-argument');
  await assert.rejects(exp.getEntitlement.handler({ auth: { uid: 'u1' }, data: { nonce: 'bad' } }), (e) => e.code === 'invalid-argument');
  assert.equal(seen.claim, undefined);
  assert.equal(seen.entitlement, undefined);
});

test('options_appleHttpHasNoAppCheck_paramsDefaultClosed', () => {
  const { exp, options } = loadIndex();
  assert.equal(exp.appleNotifications.opts.enforceAppCheck, undefined, 'webhook Apple xác thực bằng JWS, không phải App Check');
  assert.equal(options.LEGACY_CLAIM_UNTIL_MS.opts.default, '', 'mặc định rỗng = đường legacy đóng');
  assert.equal(options.ACCOUNT_TOKEN_CUTOVER_MS.opts.default, '');
});

test('googleMessage_passesDataAndMessageId', async () => {
  const { exp, seen } = loadIndex();
  await exp.googlePlayNotifications.handler({ data: { message: { data: 'YmFzZTY0', messageId: 'm-1', other: 'ignored' } } });
  assert.equal(seen.google.deps, DEPS);
  assert.equal(seen.google.args.data, 'YmFzZTY0');
  assert.equal(seen.google.args.messageId, 'm-1');
});

// ---- runtime.js ----

function loadRuntime(values = {}, extra = {}) {
  const p = fakeParams(values);
  const options = load('options.js', { 'firebase-functions/params': p.params, '../config': cfg });
  const created = { apple: [], google: [] };
  const modules = {
    'node:crypto': crypto,
    'firebase-admin/firestore': { getFirestore: () => ({ fake: 'db' }) },
    'firebase-admin/auth': { getAuth: () => ({ getUser: async () => ({ metadata: { creationTime: 'Tue, 01 Jan 2030 00:00:00 GMT' } }) }) },
    'firebase-functions/v2/https': { HttpsError },
    '../rateLimit': { enforce: async () => {} },
    './options': options,
    './apple': { createAppleStore: (args) => { created.apple.push(args); return { store: 'apple' }; } },
    './google': { createGoogleStore: (args) => { created.google.push(args); return { store: 'google' }; } },
    './entitlementToken': require('../../src/entitlement/entitlementToken'),
    './resolveEntitlement': require('../../src/entitlement/resolveEntitlement'),
    './appleRootCertificates': require('../../src/entitlement/appleRootCertificates'),
    ...extra,
  };
  return { runtime: load('runtime.js', modules), reads: p.reads, created };
}

test('makeRealDeps_isLazy_noParamRead_untilUsed', () => {
  const { runtime, reads } = loadRuntime({ APPLE_BUNDLE_ID: 'com.example.app' });
  assert.deepEqual(reads, [], 'không được đọc param lúc nạp module');
  const deps = runtime.makeRealDeps();
  assert.equal(runtime.makeRealDeps(), deps, 'phải có bộ nhớ đệm');
  assert.equal(typeof deps.nowMs(), 'number');
  assert.equal(typeof deps.randomUUID(), 'string');
  assert.ok(deps.db);
  assert.equal(typeof deps.authCreatedMs, 'function');
  assert.deepEqual(reads, [], 'getAccountToken/release chỉ dùng db/nowMs/randomUUID: không được đọc param hay secret');
  assert.equal(deps.bundleId, 'com.example.app');
  assert.deepEqual(reads, ['APPLE_BUNDLE_ID']);
  assert.equal(deps.bundleId, 'com.example.app');
  assert.equal(reads.length, 1, 'đọc một lần rồi nhớ');
});

test('makeRealDeps_buildsStoreClientsLazily', () => {
  const { runtime, created } = loadRuntime({
    APPLE_BUNDLE_ID: 'com.example.app', APPLE_APP_APPLE_ID: '1234567890', APPLE_IAP_KEY_ID: 'KEYID', APPLE_IAP_ISSUER_ID: 'ISSUER',
    APPLE_IAP_PRIVATE_KEY: 'PEM', GOOGLE_PACKAGE_NAME: 'com.example.app.android',
  });
  const deps = runtime.makeRealDeps();
  assert.equal(created.apple.length + created.google.length, 0);
  assert.equal(deps.apple, deps.apple);
  assert.equal(created.apple.length, 1);
  const a = created.apple[0];
  assert.equal(a.bundleId, 'com.example.app');
  assert.equal(a.appAppleId, 1234567890);
  assert.equal(a.keyId, 'KEYID');
  assert.equal(a.issuerId, 'ISSUER');
  assert.equal(a.privateKey, 'PEM');
  assert.equal(a.rootCertificates.length, 1);
  assert.equal(deps.google, deps.google);
  assert.equal(created.google.length, 1);
  assert.equal(created.google[0].packageName, 'com.example.app.android');
  assert.equal(deps.packageName, 'com.example.app.android');
  assert.equal(deps.policy, require('../../src/entitlement/resolveEntitlement').DEFAULT_POLICY);
});

const readMs = (name, value) => loadRuntime({ [name]: value }).runtime.makeRealDeps()[name === 'LEGACY_CLAIM_UNTIL_MS' ? 'legacyClaimUntilMs' : 'accountTokenCutoverMs'];

test('legacyClaimUntil_unsetIsNull', () => {
  assert.equal(readMs('LEGACY_CLAIM_UNTIL_MS', undefined), null);
  assert.equal(readMs('LEGACY_CLAIM_UNTIL_MS', ''), null);
});

test('legacyClaimUntil_badValueIsNull', () => {
  for (const v of ['abc', '  ', '12abc', '-5', '1.5', '0', '1e12', 'NaN', 'Infinity', '99999999999999999999']) {
    assert.equal(readMs('LEGACY_CLAIM_UNTIL_MS', v), null, `giá trị ${JSON.stringify(v)} phải đóng đường legacy`);
  }
});

test('legacyClaimUntil_validParsed', () => {
  assert.equal(readMs('LEGACY_CLAIM_UNTIL_MS', '1900000000000'), 1900000000000);
  assert.equal(readMs('LEGACY_CLAIM_UNTIL_MS', ' 1900000000000 '), 1900000000000);
});

test('accountTokenCutover_unsetIsNull_badValueIsNull_validParsed', () => {
  assert.equal(readMs('ACCOUNT_TOKEN_CUTOVER_MS', undefined), null);
  assert.equal(readMs('ACCOUNT_TOKEN_CUTOVER_MS', 'abc'), null);
  assert.equal(readMs('ACCOUNT_TOKEN_CUTOVER_MS', '-1'), null);
  assert.equal(readMs('ACCOUNT_TOKEN_CUTOVER_MS', '1800000000000'), 1800000000000);
});

test('authCreatedMs_readsAuthMetadata_errorIsNull', async () => {
  const { runtime } = loadRuntime();
  assert.equal(await runtime.makeRealDeps().authCreatedMs('u1'), Date.UTC(2030, 0, 1));

  const failing = loadRuntime({}, { 'firebase-admin/auth': { getAuth: () => ({ getUser: async () => { throw new Error('auth down'); } }) } });
  assert.equal(await failing.runtime.makeRealDeps().authCreatedMs('u1'), null);

  const noMeta = loadRuntime({}, { 'firebase-admin/auth': { getAuth: () => ({ getUser: async () => ({ metadata: {} }) }) } });
  assert.equal(await noMeta.runtime.makeRealDeps().authCreatedMs('u1'), null);
});

test('signingKey_parsedLazilyAndCached', () => {
  const key = generateSigningKey();
  const { runtime, reads } = loadRuntime({ ENTITLEMENT_SIGNING_KEY: secretJson(key) });
  const deps = runtime.makeRealDeps();
  assert.deepEqual(reads, []);
  const first = deps.signingKey;
  assert.equal(first.kid, key.kid);
  assert.equal(deps.signingKey, first, 'cùng một đối tượng, không phân tích lại');
  assert.equal(reads.filter((n) => n === 'ENTITLEMENT_SIGNING_KEY').length, 1);

  const bad = loadRuntime({ ENTITLEMENT_SIGNING_KEY: 'not-json' }).runtime.makeRealDeps();
  assert.throws(() => bad.signingKey, /invalid signing key/);
});

test('signingKey_throwNotCached_retriesOnNextAccess', () => {
  const values = { ENTITLEMENT_SIGNING_KEY: 'not-json' };
  const deps = loadRuntime(values).runtime.makeRealDeps();
  assert.throws(() => deps.signingKey, /invalid signing key/);
  const key = generateSigningKey();
  values.ENTITLEMENT_SIGNING_KEY = secretJson(key);
  assert.equal(deps.signingKey.kid, key.kid, 'lần sau phải thử lại, không trả giá trị nửa vời');
});

test('appAppleId_invalidBecomesUndefined_neverGuessed', () => {
  for (const v of ['', 'abc', '0', '-5', '1.5', '99999999999999999999']) {
    const { runtime, created } = loadRuntime({ APPLE_BUNDLE_ID: 'com.example.app', APPLE_APP_APPLE_ID: v });
    void runtime.makeRealDeps().apple;
    assert.equal(created.apple[0].appAppleId, undefined, `APPLE_APP_APPLE_ID=${JSON.stringify(v)} phải thành undefined`);
  }
});

test('requireUidAndLimit_sameShapeAsIndex', async () => {
  const enforceCalls = [];
  const { runtime } = loadRuntime({}, { '../rateLimit': { enforce: async (a) => { enforceCalls.push(a); } } });
  assert.equal(runtime.requireUid({ auth: { uid: 'u1' } }), 'u1');
  assert.throws(() => runtime.requireUid({}), (e) => e.code === 'unauthenticated');
  await runtime.limit('claimPurchase', 'u1');
  assert.equal(enforceCalls.length, 1);
  assert.equal(enforceCalls[0].uid, 'u1');
  assert.equal(enforceCalls[0].name, 'claimPurchase');
  assert.equal(enforceCalls[0].db.fake, 'db');
  assert.ok(Number.isFinite(enforceCalls[0].nowMs) && enforceCalls[0].nowMs > 0);
});
