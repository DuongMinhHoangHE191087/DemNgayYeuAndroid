'use strict';

// Client cửa hàng Apple (src/entitlement/apple.js, M-22a): dùng client và verifier giả, không gọi mạng.
// require đặt trong từng test để mỗi test đỏ riêng khi module chưa có.
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const lib = require('@apple/app-store-server-library');

const { APIException, VerificationException, VerificationStatus } = lib;
const load = () => require('../../src/entitlement/apple');
const loadCerts = () => require('../../src/entitlement/appleRootCertificates');

// SHA-256 của AppleRootCA-G3.cer (DER) do M-04 ghi trong docs/superpowers/spikes/p6-m04-server-libs.md.
const G3_SHA256 = '63343ABFB89A6A03EBB57E9B3F5FA7BE7C4F5C756F3017B3A8C488C3653E9179';

const BUNDLE_ID = 'com.example.inlove';
const APP_APPLE_ID = 123456789;
const jws = (obj) => `jws:${JSON.stringify(obj)}`;
const unjws = (s) => JSON.parse(s.slice(4));
const unavailable = { code: 'unavailable' };
const unverified = { code: 'unverified' };

const SUB = { transactionId: 'T2', originalTransactionId: 'O1', productId: 'vip_monthly', type: 'Auto-Renewable Subscription' };
const LIFETIME = { transactionId: 'L1', originalTransactionId: 'L1', productId: 'vip_lifetime', type: 'Non-Consumable' };

// script: { Production: {info, statuses}, Sandbox: {info, statuses} }; verify: { Production: {...}, Sandbox: {...} } ghi đè verifier.
function harness({ script = {}, verify = {}, config = {} } = {}) {
  const calls = [];
  const verifierEnvs = [];
  const defaultVerifier = (env) => ({
    verifyAndDecodeTransaction: async (s) => { calls.push(['verifyTx', env]); return unjws(s); },
    verifyAndDecodeRenewalInfo: async (s) => { calls.push(['verifyRenewal', env]); return unjws(s); },
    verifyAndDecodeNotification: async (s) => { calls.push(['verifyNotification', env]); return unjws(s); },
  });
  const store = load().createAppleStore({
    bundleId: BUNDLE_ID,
    appAppleId: APP_APPLE_ID,
    keyId: 'KEY',
    issuerId: 'ISS',
    privateKey: 'PK',
    rootCertificates: [Buffer.from('root')],
    clientFactory: (env) => ({
      getTransactionInfo: async (id) => {
        calls.push(['info', env, id]);
        return script[env].info(id);
      },
      getAllSubscriptionStatuses: async (id) => {
        calls.push(['statuses', env, id]);
        return script[env].statuses(id);
      },
    }),
    verifierFactory: (env) => {
      verifierEnvs.push(env);
      return { ...defaultVerifier(env), ...(verify[env] || {}) };
    },
    ...config,
  });
  return { store, calls, verifierEnvs };
}

const found = (tx) => async () => ({ signedTransactionInfo: jws(tx) });
const failing = (status) => async () => { throw new APIException(status); };
const statusesFor = (items) => async () => ({ data: [{ subscriptionGroupIdentifier: 'g', lastTransactions: items }] });

test('production404_thenSandboxFound', async () => {
  const { store, calls } = harness({ script: { Production: { info: failing(404) }, Sandbox: { info: found(LIFETIME) } } });
  const r = await store.fetchState('L1');
  assert.equal(r.environment, 'Sandbox');
  assert.equal(r.transaction.transactionId, 'L1');
  assert.deepEqual(calls.filter((c) => c[0] === 'info'), [['info', 'Production', 'L1'], ['info', 'Sandbox', 'L1']]);
});

test('production400_thenSandboxFound', async () => {
  const { store } = harness({ script: { Production: { info: failing(400) }, Sandbox: { info: found(LIFETIME) } } });
  assert.equal((await store.fetchState('L1')).environment, 'Sandbox');
});

test('bothNotFound_returnsNull', async () => {
  const { store } = harness({ script: { Production: { info: failing(404) }, Sandbox: { info: failing(400) } } });
  assert.equal(await store.fetchState('X'), null);
});

test('serverError_throwsUnavailable', async () => {
  // 5xx ở Production: không được rơi sang Sandbox; 5xx ở Sandbox sau 404 Production cũng là unavailable, không phải null.
  const prod = harness({ script: { Production: { info: failing(500) }, Sandbox: { info: found(LIFETIME) } } });
  await assert.rejects(prod.store.fetchState('L1'), unavailable);
  assert.equal(prod.calls.some((c) => c[1] === 'Sandbox'), false);
  const sandbox = harness({ script: { Production: { info: failing(404) }, Sandbox: { info: failing(503) } } });
  await assert.rejects(sandbox.store.fetchState('L1'), unavailable);
});

test('http401And429_throwUnavailable', async () => {
  for (const status of [401, 429]) {
    const { store, calls } = harness({ script: { Production: { info: failing(status) }, Sandbox: { info: found(LIFETIME) } } });
    await assert.rejects(store.fetchState('L1'), unavailable, `status ${status}`);
    assert.equal(calls.some((c) => c[1] === 'Sandbox'), false);
  }
});

test('networkFailure_throwsUnavailable', async () => {
  const { store } = harness({ script: { Production: { info: async () => { throw new TypeError('fetch failed'); } } } });
  await assert.rejects(store.fetchState('L1'), unavailable);
});

test('verifierRejects_throwsUnverified', async () => {
  for (const status of [VerificationStatus.INVALID_APP_IDENTIFIER, VerificationStatus.INVALID_ENVIRONMENT, VerificationStatus.INVALID_CERTIFICATE, VerificationStatus.INVALID_CHAIN_LENGTH, VerificationStatus.FAILURE, VerificationStatus.VERIFICATION_FAILURE]) {
    const { store } = harness({
      script: { Production: { info: found(LIFETIME) } },
      verify: { Production: { verifyAndDecodeTransaction: async () => { throw new VerificationException(status); } } },
    });
    await assert.rejects(store.fetchState('L1'), unverified, `status ${status}`);
  }
});

test('verifierRetryable_throwsUnavailable', async () => {
  const { store } = harness({
    script: { Production: { info: found(LIFETIME) } },
    verify: { Production: { verifyAndDecodeTransaction: async () => { throw new VerificationException(VerificationStatus.RETRYABLE_VERIFICATION_FAILURE); } } },
  });
  await assert.rejects(store.fetchState('L1'), unavailable);
});

test('rootCertificates_sha256Pinned', () => {
  const certs = loadCerts().appleRootCertificates();
  assert.equal(certs.length, 1);
  assert.ok(Buffer.isBuffer(certs[0]));
  assert.equal(crypto.createHash('sha256').update(certs[0]).digest('hex').toUpperCase(), G3_SHA256);
  const x509 = new crypto.X509Certificate(certs[0]);
  assert.match(x509.subject, /CN=Apple Root CA - G3/);
  assert.equal(x509.subject, x509.issuer);
  assert.equal(x509.fingerprint256.replace(/:/g, ''), G3_SHA256);
});

test('lifetimeUsesGetTransactionInfoOnly', async () => {
  const { store, calls } = harness({ script: { Production: { info: found(LIFETIME) } } });
  const r = await store.fetchState('L1');
  assert.deepEqual(r, { environment: 'Production', transaction: LIFETIME, renewalInfo: null, status: null });
  assert.equal(calls.some((c) => c[0] === 'statuses'), false);
});

test('subscriptionAddsGetAllSubscriptionStatuses', async () => {
  const latest = { ...SUB, transactionId: 'T9' };
  const renewal = { autoRenewStatus: 1, originalTransactionId: 'O1' };
  const { store, calls } = harness({
    script: {
      Production: {
        info: found(SUB),
        statuses: statusesFor([{ status: 1, originalTransactionId: 'O1', signedTransactionInfo: jws(latest), signedRenewalInfo: jws(renewal) }]),
      },
    },
  });
  const r = await store.fetchState('T2');
  assert.deepEqual(r, { environment: 'Production', transaction: latest, renewalInfo: renewal, status: 1 });
  assert.deepEqual(calls.filter((c) => c[0] === 'statuses'), [['statuses', 'Production', 'T2']]);
});

test('latestTransaction_usesMatchingOriginalTransactionId', async () => {
  const other = { ...SUB, transactionId: 'OTHER', originalTransactionId: 'O2' };
  const mine = { ...SUB, transactionId: 'MINE' };
  const { store } = harness({
    script: {
      Production: {
        info: found(SUB),
        statuses: async () => ({
          data: [
            { lastTransactions: [{ status: 1, originalTransactionId: 'O2', signedTransactionInfo: jws(other) }] },
            { lastTransactions: [{ status: 2, originalTransactionId: 'O1', signedTransactionInfo: jws(mine) }] },
          ],
        }),
      },
    },
  });
  const r = await store.fetchState('T2');
  assert.equal(r.transaction.transactionId, 'MINE');
  assert.equal(r.status, 2);
  // Trường originalTransactionId chưa ký khớp nhưng dữ liệu đã ký thuộc chuỗi khác: không tin.
  const forged = harness({
    script: { Production: { info: found(SUB), statuses: statusesFor([{ status: 1, originalTransactionId: 'O1', signedTransactionInfo: jws(other) }]) } },
  });
  await assert.rejects(forged.store.fetchState('T2'), unverified);
});

test('renewalInfoVerifiedAndReturned', async () => {
  const renewal = { autoRenewStatus: 0, expirationIntent: 1 };
  const { store, calls } = harness({
    script: {
      Production: { info: found(SUB), statuses: statusesFor([{ status: 1, originalTransactionId: 'O1', signedTransactionInfo: jws(SUB), signedRenewalInfo: jws(renewal) }]) },
    },
  });
  const r = await store.fetchState('T2');
  assert.deepEqual(r.renewalInfo, renewal);
  assert.ok(calls.some((c) => c[0] === 'verifyRenewal' && c[1] === 'Production'));
  // Renewal info không qua được verifier thì không được trả về.
  const rejecting = harness({
    script: { Production: { info: found(SUB), statuses: statusesFor([{ status: 1, originalTransactionId: 'O1', signedTransactionInfo: jws(SUB), signedRenewalInfo: jws(renewal) }]) } },
    verify: { Production: { verifyAndDecodeRenewalInfo: async () => { throw new VerificationException(VerificationStatus.VERIFICATION_FAILURE); } } },
  });
  await assert.rejects(rejecting.store.fetchState('T2'), unverified);
});

test('statusesNotFound_statusNull', async () => {
  const { store } = harness({ script: { Production: { info: found(SUB), statuses: failing(404) } } });
  const r = await store.fetchState('T2');
  assert.equal(r.status, null);
  assert.equal(r.renewalInfo, null);
  assert.equal(r.transaction.transactionId, 'T2');
  // Mọi lỗi khác của getAllSubscriptionStatuses vẫn là unavailable.
  const down = harness({ script: { Production: { info: found(SUB), statuses: failing(500) } } });
  await assert.rejects(down.store.fetchState('T2'), unavailable);
});

test('verifierFactory_productionGetsAppAppleId', () => {
  const made = [];
  class FakeVerifier {
    constructor(...args) { made.push(args); }
  }
  const roots = [Buffer.from('root')];
  const factory = load().defaultVerifierFactory({ rootCertificates: roots, enableOnlineChecks: true, bundleId: BUNDLE_ID, appAppleId: APP_APPLE_ID, SignedDataVerifier: FakeVerifier });
  factory('Production');
  factory('Sandbox');
  assert.deepEqual(made[0], [roots, true, 'Production', BUNDLE_ID, APP_APPLE_ID]);
  assert.equal(made[1][2], 'Sandbox');
  assert.equal(made[1][3], BUNDLE_ID);
  // Production thật cần appAppleId: thiếu thì tạo store phải lỗi ngay, không để verifier lỏng.
  assert.throws(() => load().createAppleStore({ bundleId: BUNDLE_ID, keyId: 'k', issuerId: 'i', privateKey: 'p', rootCertificates: roots }));
  // Mặc định bật OCSP.
  const made2 = [];
  class Fake2 { constructor(...a) { made2.push(a); } }
  load().defaultVerifierFactory({ rootCertificates: roots, bundleId: BUNDLE_ID, appAppleId: APP_APPLE_ID, SignedDataVerifier: Fake2 })('Production');
  assert.equal(made2[0][1], true);
});

const notificationPayload = (extra = {}) => ({
  notificationType: 'DID_RENEW',
  subtype: 'BILLING_RECOVERY',
  notificationUUID: 'uuid-1',
  signedDate: 1700000000000,
  data: { environment: 'Production', bundleId: BUNDLE_ID, status: 1, signedTransactionInfo: jws(SUB), signedRenewalInfo: jws({ autoRenewStatus: 1 }) },
  ...extra,
});

test('notification_productionFirst_thenSandboxOnInvalidEnvironment', async () => {
  const payload = notificationPayload({ data: { environment: 'Sandbox', bundleId: BUNDLE_ID, status: 1, signedTransactionInfo: jws(SUB) } });
  const { store, calls } = harness({
    verify: {
      Production: { verifyAndDecodeNotification: async () => { throw new VerificationException(VerificationStatus.INVALID_ENVIRONMENT); } },
    },
  });
  const r = await store.verifyNotification(jws(payload));
  assert.equal(r.environment, 'Sandbox');
  assert.deepEqual(calls.filter((c) => c[0] === 'verifyNotification'), [['verifyNotification', 'Sandbox']]);
  // Cả hai môi trường đều INVALID_ENVIRONMENT (ví dụ Xcode): không tin.
  const both = harness({
    verify: {
      Production: { verifyAndDecodeNotification: async () => { throw new VerificationException(VerificationStatus.INVALID_ENVIRONMENT); } },
      Sandbox: { verifyAndDecodeNotification: async () => { throw new VerificationException(VerificationStatus.INVALID_ENVIRONMENT); } },
    },
  });
  await assert.rejects(both.store.verifyNotification('jws:{}'), unverified);
});

test('notification_foreignApp_returnsForeign', async () => {
  const { store, calls } = harness({
    verify: { Production: { verifyAndDecodeNotification: async () => { throw new VerificationException(VerificationStatus.INVALID_APP_IDENTIFIER); } } },
  });
  assert.deepEqual(await store.verifyNotification('jws:{}'), { foreign: true });
  assert.equal(calls.some((c) => c[1] === 'Sandbox'), false);
});

test('notification_retryableFailure_throwsUnavailable', async () => {
  const { store } = harness({
    verify: { Production: { verifyAndDecodeNotification: async () => { throw new VerificationException(VerificationStatus.RETRYABLE_VERIFICATION_FAILURE); } } },
  });
  await assert.rejects(store.verifyNotification('jws:{}'), unavailable);
});

test('notification_badSignature_throwsUnverified', async () => {
  for (const status of [VerificationStatus.VERIFICATION_FAILURE, VerificationStatus.INVALID_CERTIFICATE, VerificationStatus.INVALID_CHAIN_LENGTH, VerificationStatus.FAILURE]) {
    const { store } = harness({
      verify: { Production: { verifyAndDecodeNotification: async () => { throw new VerificationException(status); } } },
    });
    await assert.rejects(store.verifyNotification('jws:{}'), unverified, `status ${status}`);
  }
});

test('notification_decodesTransactionAndRenewalInfo', async () => {
  const { store } = harness();
  const r = await store.verifyNotification(jws(notificationPayload()));
  assert.deepEqual(r, {
    notificationType: 'DID_RENEW',
    subtype: 'BILLING_RECOVERY',
    notificationUUID: 'uuid-1',
    signedDateMs: 1700000000000,
    environment: 'Production',
    bundleId: BUNDLE_ID,
    status: 1,
    transaction: SUB,
    renewalInfo: { autoRenewStatus: 1 },
  });
});

test('notification_withoutData_transactionNull', async () => {
  const { store } = harness();
  const r = await store.verifyNotification(jws({ notificationType: 'TEST', notificationUUID: 'u', signedDate: 5 }));
  assert.equal(r.transaction, null);
  assert.equal(r.renewalInfo, null);
  assert.equal(r.status, null);
  assert.equal(r.notificationType, 'TEST');
  assert.equal(r.environment, 'Production');
  assert.equal(r.subtype ?? null, null);
});
