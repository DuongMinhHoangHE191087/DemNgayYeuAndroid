'use strict';

// normalizeApple (src/entitlement/normalizeApple.js, M-13): hàm thuần, JWS đã giải mã -> NormalizedPurchase hoặc notOurs.
// Tiêu chí chống bypass: trường thiếu hoặc lạ không bao giờ ra trạng thái hưởng quyền.
const test = require('node:test');
const assert = require('node:assert/strict');
const { resolveEntitlement } = require('../../src/entitlement/resolveEntitlement');
const { TEST_BUNDLE_ID, DAY, APPLE_NOW_MS, appleTransaction, appleRenewalInfo } = require('./fixtures/apple');

const load = () => require('../../src/entitlement/normalizeApple').normalizeApple;
const run = (over = {}) => load()({ transaction: appleTransaction(), renewalInfo: null, status: 1, bundleId: TEST_BUNDLE_ID, asOfMs: APPLE_NOW_MS, ...over });
const LIFETIME = { productId: 'vip_lifetime', type: 'Non-Consumable', expiresDate: undefined };
const stored = (p) => ({ ...p, firstSeenAtMs: APPLE_NOW_MS - DAY, verifiedAtMs: APPLE_NOW_MS });

test('status1_active', () => {
  const p = run();
  assert.equal(p.status, 'active');
  assert.equal(p.expiresAtMs, APPLE_NOW_MS + 29 * DAY);
  assert.equal(p.store, 'apple');
  assert.equal(p.planId, 'monthly');
  assert.equal(p.kind, 'subscription');
  assert.equal(p.productId, 'vip_monthly');
  assert.equal(p.environment, 'Production');
  assert.equal(p.acknowledged, true);
  assert.equal(p.predecessorLookupId, null);
  assert.equal(p.graceExpiresAtMs, null);
  assert.equal(p.revokedAtMs, null);
  assert.equal(resolveEntitlement([stored(p)], APPLE_NOW_MS).vip, true);
});

test('status4_grace_usesGraceExpires', () => {
  const grace = APPLE_NOW_MS + 5 * DAY;
  const p = run({ status: 4, renewalInfo: appleRenewalInfo({ gracePeriodExpiresDate: grace }), transaction: appleTransaction({ expiresDate: APPLE_NOW_MS - DAY }) });
  assert.equal(p.status, 'grace');
  assert.equal(p.graceExpiresAtMs, grace);
  assert.equal(p.expiresAtMs, APPLE_NOW_MS - DAY);
});

test('status4_noRenewalInfo_graceExpiresNull', () => {
  const p = run({ status: 4, renewalInfo: null });
  assert.equal(p.status, 'grace');
  assert.equal(p.graceExpiresAtMs, null);
  // resolveEntitlement không bịa khoảng grace: không VIP
  assert.equal(resolveEntitlement([stored(p)], APPLE_NOW_MS).vip, false);
});

test('status3_retry_notEntitled', () => {
  const p = run({ status: 3, transaction: appleTransaction({ expiresDate: APPLE_NOW_MS - DAY }) });
  assert.equal(p.status, 'retry');
  assert.equal(p.graceExpiresAtMs, null);
  assert.equal(resolveEntitlement([stored(p)], APPLE_NOW_MS).vip, false);
});

test('status2_expired', () => {
  const p = run({ status: 2, transaction: appleTransaction({ expiresDate: APPLE_NOW_MS - DAY }) });
  assert.equal(p.status, 'expired');
  assert.equal(p.revokedAtMs, null);
});

test('status5_revoked', () => {
  const p = run({ status: 5 });
  assert.equal(p.status, 'revoked');
  assert.equal(p.revokedAtMs, APPLE_NOW_MS); // không có revocationDate: lấy asOfMs
  assert.equal(p.revokedTransactionId, null);
  assert.equal(resolveEntitlement([stored(p)], APPLE_NOW_MS).vip, false);
});

test('statusMissing_derivesFromExpiresDate', () => {
  assert.equal(run({ status: undefined }).status, 'active');
  assert.equal(run({ status: null }).status, 'active');
  assert.equal(run({ status: null, transaction: appleTransaction({ expiresDate: APPLE_NOW_MS - 1 }) }).status, 'expired');
  assert.equal(run({ status: null, transaction: appleTransaction({ expiresDate: APPLE_NOW_MS }) }).status, 'expired');
  // không có expiresDate thì không bịa: hết hạn
  const noExp = run({ status: null, transaction: appleTransaction({ expiresDate: undefined }) });
  assert.equal(noExp.status, 'expired');
  assert.equal(noExp.expiresAtMs, null);
});

test('unknownStatusValue_neverActive', () => {
  for (const status of [0, 6, 99, '1', 1.5, NaN]) assert.equal(run({ status }).status, 'expired', String(status));
});

test('revocationDate_overridesActive', () => {
  const rd = APPLE_NOW_MS - 1000;
  for (const status of [1, 4, null]) {
    const p = run({ status, transaction: appleTransaction({ revocationDate: rd }) });
    assert.equal(p.status, 'revoked');
    assert.equal(p.revokedAtMs, rd);
  }
});

test('revocationDate_setsRevokedTransactionId', () => {
  const p = run({ transaction: appleTransaction({ transactionId: '2500', revocationDate: APPLE_NOW_MS - 1 }) });
  assert.equal(p.revokedTransactionId, '2500');
  assert.equal(run().revokedTransactionId, null);
});

test('familyShared_ownership', () => {
  assert.equal(run({ transaction: appleTransaction({ inAppOwnershipType: 'FAMILY_SHARED' }) }).ownership, 'FAMILY_SHARED');
  assert.equal(run({ transaction: appleTransaction({ inAppOwnershipType: 'PURCHASED' }) }).ownership, 'PURCHASED');
  assert.equal(run({ transaction: appleTransaction({ inAppOwnershipType: undefined }) }).ownership, 'PURCHASED');
  // giá trị lạ: coi là hạn chế hơn, không bao giờ là PURCHASED
  assert.equal(run({ transaction: appleTransaction({ inAppOwnershipType: 'SOMETHING_NEW' }) }).ownership, 'FAMILY_SHARED');
});

test('lifetime_nonConsumable', () => {
  const p = run({ status: null, transaction: appleTransaction(LIFETIME) });
  assert.equal(p.kind, 'lifetime');
  assert.equal(p.planId, 'lifetime');
  assert.equal(p.status, 'active');
  assert.equal(p.expiresAtMs, null);
  assert.equal(resolveEntitlement([stored(p)], APPLE_NOW_MS + DAY).vip, true);
  const r = run({ status: null, transaction: appleTransaction({ ...LIFETIME, revocationDate: APPLE_NOW_MS - 5 }) });
  assert.equal(r.status, 'revoked');
  assert.equal(r.revokedAtMs, APPLE_NOW_MS - 5);
  assert.equal(r.expiresAtMs, null);
});

test('environmentXcode_rejected', () => {
  assert.deepEqual(run({ transaction: appleTransaction({ environment: 'Xcode' }) }), { notOurs: true, reason: 'environment-not-supported' });
});

test('environmentLocalTesting_rejected', () => {
  assert.deepEqual(run({ transaction: appleTransaction({ environment: 'LocalTesting' }) }), { notOurs: true, reason: 'environment-not-supported' });
});

test('environmentUnknownOrMissing_rejected', () => {
  for (const environment of ['production', 'Staging', '', null, undefined, 7]) {
    assert.deepEqual(run({ transaction: appleTransaction({ environment }) }), { notOurs: true, reason: 'environment-not-supported' }, String(environment));
  }
});

test('environmentSandbox_acceptedAndRecorded', () => {
  const p = run({ transaction: appleTransaction({ environment: 'Sandbox' }) });
  assert.equal(p.environment, 'Sandbox');
  assert.equal(p.status, 'active');
});

test('sandboxLifetime_kindLifetime_environmentSandbox', () => {
  const p = run({ status: null, transaction: appleTransaction({ ...LIFETIME, environment: 'Sandbox' }) });
  assert.equal(p.kind, 'lifetime');
  assert.equal(p.environment, 'Sandbox');
  assert.equal(p.status, 'active');
});

test('bundleIdMismatch_notOurs', () => {
  assert.deepEqual(run({ transaction: appleTransaction({ bundleId: 'com.other.app' }) }), { notOurs: true, reason: 'bundle-mismatch' });
  assert.deepEqual(run({ transaction: appleTransaction({ bundleId: undefined }) }), { notOurs: true, reason: 'bundle-mismatch' });
  assert.deepEqual(run({ bundleId: undefined }), { notOurs: true, reason: 'bundle-mismatch' });
});

test('unknownProduct_notOurs', () => {
  assert.deepEqual(run({ transaction: appleTransaction({ productId: 'vip_weekly' }) }), { notOurs: true, reason: 'unknown-product' });
  assert.deepEqual(run({ transaction: appleTransaction({ productId: undefined }) }), { notOurs: true, reason: 'unknown-product' });
});

test('malformedTransaction_notOurs', () => {
  assert.equal(run({ transaction: appleTransaction({ originalTransactionId: undefined }) }).notOurs, true);
  assert.equal(run({ transaction: appleTransaction({ transactionId: undefined }) }).notOurs, true);
  assert.equal(run({ transaction: null }).notOurs, true);
});

test('appAccountToken_mappedLowercase', () => {
  assert.equal(run({ transaction: appleTransaction({ appAccountToken: 'AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE' }) }).accountToken, 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee');
  assert.equal(run({ transaction: appleTransaction({ appAccountToken: undefined }) }).accountToken, null);
  assert.equal(run({ transaction: appleTransaction({ appAccountToken: 123 }) }).accountToken, null);
});

test('lookupId_isOriginalTransactionId', () => {
  assert.equal(run({ transaction: appleTransaction({ originalTransactionId: '777', transactionId: '888' }) }).lookupId, '777');
});

test('key_hasApplePrefix', () => {
  assert.equal(run({ transaction: appleTransaction({ originalTransactionId: '777' }) }).key, 'apple_777');
});

test('asOfMs_copiedFromInput', () => {
  assert.equal(run({ asOfMs: 123456 }).asOfMs, 123456);
});

test('latestTransactionId_isTransactionId', () => {
  assert.equal(run({ transaction: appleTransaction({ transactionId: '4242' }) }).latestTransactionId, '4242');
});

test('purchaseTimeMs_isOriginalPurchaseDate', () => {
  const p = run({ transaction: appleTransaction({ originalPurchaseDate: 1111, purchaseDate: 2222 }) });
  assert.equal(p.purchaseTimeMs, 1111);
});

test('outputKeys_matchNormalizedPurchase', () => {
  assert.deepEqual(Object.keys(run()).sort(), [
    'acknowledged', 'accountToken', 'asOfMs', 'environment', 'expiresAtMs', 'graceExpiresAtMs', 'key', 'kind', 'latestTransactionId',
    'lookupId', 'ownership', 'planId', 'predecessorLookupId', 'productId', 'purchaseTimeMs', 'revokedAtMs', 'revokedTransactionId', 'status', 'store',
  ].sort());
});
