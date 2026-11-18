'use strict';

// M-84: nối cổng quyền vào ba callable media của index.js. Nạp index.js trong `vm` với module giả (cùng cách test 12 của rateLimit.test.js).
// Thứ tự bắt buộc: limit -> gateFor -> media.*; gate từ chối thì media.* KHÔNG được gọi; `tier` đi tới media.*.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { HttpsError } = require('firebase-functions/v2/https');
const cfg = require('../../src/config');
const runtime = require('../../src/entitlement/runtime');
const { GATES } = require('../../src/entitlement/gatePolicy');
const { makeDeps } = require('./depsBuilder');
const { BASE_NOW_MS, HOUR, makeStoredPurchase } = require('./builders');

const INDEX_SRC = fs.readFileSync(path.join(__dirname, '..', '..', 'index.js'), 'utf8');
const FAKE_DB = { fake: 'db' };
const ENTITLEMENT_EXPORTS = { claimPurchase: 'claimPurchase-sentinel', appleNotifications: 'appleNotifications-sentinel' };
const CALLS = {
  signMemoryUpload: 'reserveUpload',
  confirmMemoryUpload: 'confirmUpload',
  getMemoryMediaUrl: 'getDeliveryUrl',
};
const NAMES = Object.keys(CALLS);

// gateFor/enforce mặc định cho qua; events ghi thứ tự gọi, optsByName giữ options truyền vào onCall.
function load({ gateFor = async () => ({ tier: 'free' }), enforce = async () => {} } = {}) {
  const events = [];
  const mediaArgs = [];
  const optsByName = {};
  const secret = (name) => ({ name, value: () => `v-${name}` });
  const media = new Proxy({}, {
    get: (_, k) => async (args) => { events.push(`media.${String(k)}`); mediaArgs.push(args); return { served: String(k) }; },
  });
  const modules = {
    'firebase-admin/app': { initializeApp: () => {}, getApps: () => [] },
    'firebase-admin/firestore': { getFirestore: () => FAKE_DB },
    'firebase-admin/auth': { getAuth: () => ({}) },
    'firebase-functions/v2/https': { onCall: (opts, handler) => Object.assign(handler, { opts }), HttpsError },
    'firebase-functions/v2/firestore': { onDocumentWritten: (opts, handler) => handler },
    'firebase-functions/v2/scheduler': { onSchedule: (opts, handler) => handler },
    'firebase-functions/params': { defineSecret: secret, defineString: secret },
    'firebase-functions/logger': { info() {} },
    './src/config': cfg,
    './src/policy': { parseReserveInput: (d) => d, parseIds: (d) => d },
    './src/media': media,
    './src/pairing': {},
    './src/accountDeletion': {},
    './src/avatar': {},
    './src/aiAdvisor': {},
    './src/objectStore': { createObjectStore: () => ({}) },
    './src/entitlement': ENTITLEMENT_EXPORTS,
    './src/entitlement/options': { STORE_SECRETS: [secret('STORE_SECRET')] },
    './src/entitlement/runtime': {
      gateFor: async (name, uid) => { events.push('gateFor'); return gateFor(name, uid); },
    },
    './src/rateLimit': { enforce: async (args) => { events.push('limit'); return enforce(args); } },
  };
  const fakeRequire = (id) => {
    if (!(id in modules)) throw new Error(`mediaGate test: require chưa được giả: ${id}`);
    return modules[id];
  };
  const exp = {};
  vm.runInContext(`(function (exports, require, module) {${INDEX_SRC}\n})`, vm.createContext({}))(exp, fakeRequire, { exports: exp });
  for (const n of NAMES) optsByName[n] = exp[n].opts;
  return { exp, events, mediaArgs, optsByName };
}

const request = (uid = 'uid-1') => ({ auth: { uid }, data: { id: 'x' } });

test('signMemoryUpload_gateForCalledAfterLimit_tierPassedToMedia', async () => {
  for (const tier of ['free', 'vip']) {
    const gateCalls = [];
    const { exp, events, mediaArgs } = load({ gateFor: async (name, uid) => { gateCalls.push([name, uid]); return { tier }; } });
    await exp.signMemoryUpload(request('uid-9'));
    assert.deepEqual(events, ['limit', 'gateFor', 'media.reserveUpload']);
    assert.deepEqual(gateCalls, [['signMemoryUpload', 'uid-9']]);
    assert.equal(mediaArgs[0].tier, tier);
    assert.equal(mediaArgs[0].uid, 'uid-9');
  }
});

test('confirmMemoryUpload_tierPassed', async () => {
  const { exp, events, mediaArgs } = load({ gateFor: async () => ({ tier: 'vip' }) });
  await exp.confirmMemoryUpload(request());
  assert.deepEqual(events, ['limit', 'gateFor', 'media.confirmUpload']);
  assert.equal(mediaArgs[0].tier, 'vip');
});

test('getMemoryMediaUrl_tierPassed', async () => {
  const { exp, events, mediaArgs } = load({ gateFor: async () => ({ tier: 'vip' }) });
  await exp.getMemoryMediaUrl(request());
  assert.deepEqual(events, ['limit', 'gateFor', 'media.getDeliveryUrl']);
  assert.equal(mediaArgs[0].tier, 'vip');
});

test('gateDenied_mediaNotCalled', async () => {
  const denied = new HttpsError('permission-denied', 'vip-required', { reason: 'vip-required' });
  for (const name of NAMES) {
    const { exp, events } = load({ gateFor: async () => { throw denied; } });
    await assert.rejects(exp[name](request()), (e) => e === denied);
    assert.deepEqual(events, ['limit', 'gateFor'], `${name}: media.* bị gọi dù gate từ chối`);
  }
});

test('limitDenied_gateForNotCalled', async () => {
  const blocked = new Error('limit-sentinel');
  for (const name of NAMES) {
    const { exp, events } = load({ enforce: async () => { throw blocked; } });
    await assert.rejects(exp[name](request()), (e) => e === blocked);
    assert.deepEqual(events, ['limit'], `${name}: gateFor/media bị gọi sau khi limit chặn`);
  }
});

// Hai test dưới chạy gateFor THẬT (runtime.js + enforceGate + evaluateGate) trên deps giả: người dùng chưa mua gì.
test('mediaFreeAllowedTrue_freeUserStillServed', async () => {
  const { exp, mediaArgs } = load({ gateFor: (name, uid) => runtime.gateFor(name, uid, makeDeps(), GATES) });
  for (const name of NAMES) {
    assert.deepEqual(await exp[name](request()), { served: CALLS[name] });
  }
  assert.deepEqual(mediaArgs.map((a) => a.tier), ['free', 'free', 'free']);
});

test('mediaFreeAllowedFalse_freeUserDenied', async () => {
  const gates = Object.fromEntries(NAMES.map((n) => [n, { gate: 'tiered', freeAllowed: false }]));
  for (const name of NAMES) {
    const { exp, mediaArgs } = load({ gateFor: (n, uid) => runtime.gateFor(n, uid, makeDeps(), gates) });
    await assert.rejects(exp[name](request()), (e) => e.code === 'permission-denied' && e.message === 'vip-required');
    assert.equal(mediaArgs.length, 0, `${name}: media.* bị gọi cho người dùng miễn phí khi freeAllowed=false`);
  }
});

test('callableOptions_keepAppCheckAndBindR2AndStoreSecrets', () => {
  const { optsByName } = load();
  for (const name of NAMES) {
    const o = optsByName[name];
    assert.equal(o.enforceAppCheck, true);
    assert.deepEqual(Array.from(o.secrets, (s) => s.name).sort(), [
      'R2_ACCESS_KEY_ID', 'R2_SECRET_ACCESS_KEY', 'STORE_SECRET',
    ]);
  }
});

// Hàm entitlement (claimPurchase, webhook...) chỉ được deploy nếu index.js re-export chúng: bỏ dòng Object.assign là mất cả sáu hàm.
test('index_reExportsEveryEntitlementFunction', () => {
  const { exp } = load();
  for (const [name, sentinel] of Object.entries(ENTITLEMENT_EXPORTS)) assert.equal(exp[name], sentinel, name);
});

// Gọi như index.js: không truyền `gates` nên phải dùng GATES thật; ba callable media đã phân loại thì không được ném internal.
test('gateFor_defaultGates_classifyTheThreeMediaCallables', async () => {
  for (const name of NAMES) {
    assert.deepEqual(await runtime.gateFor(name, 'uid-1', makeDeps()), { tier: 'free' }, name);
  }
  await assert.rejects(runtime.gateFor('nope', 'uid-1', makeDeps()), (e) => e.code === 'internal');
});

test('gateFor_unknownName_throwsInternal', async () => {
  await assert.rejects(runtime.gateFor('nope', 'u', makeDeps(), GATES), (e) => e.code === 'internal');
});

// Tên kế thừa từ Object.prototype không phải callable đã phân loại: phải ném internal, không được lọt vào enforceGate.
test('gateFor_inheritedObjectNames_throwInternal', async () => {
  for (const name of ['constructor', 'toString', 'hasOwnProperty', '__proto__']) {
    await assert.rejects(runtime.gateFor(name, 'u', makeDeps(), GATES), (e) => e.code === 'internal', name);
  }
});

// uid truyền vào gateFor là uid đã xác thực: chủ gói (uid-1) nhận vip, uid khác không được thừa hưởng gói đó.
test('gateFor_looksUpTheCallersOwnPurchases', async () => {
  const deps = makeDeps();
  const p = makeStoredPurchase({
    asOfMs: BASE_NOW_MS - HOUR, verifiedAtMs: BASE_NOW_MS - HOUR, lastCheckedAtMs: BASE_NOW_MS - HOUR, ackState: 'done', ackAttempts: null, ackNextAtMs: null,
  });
  await deps.db.collection('purchases').doc(p.key).set(p);
  await deps.db.collection('entitlementAccounts').doc(p.uid).set({ purchaseKeys: [p.key] });
  assert.deepEqual(await runtime.gateFor('signMemoryUpload', p.uid, deps, GATES), { tier: 'vip' });
  assert.deepEqual(await runtime.gateFor('signMemoryUpload', 'uid-other', deps, GATES), { tier: 'free' });
});
