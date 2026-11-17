'use strict';

// Dựng purchase cho test entitlement. Không phải test (không có đuôi .test.js nên node --test glob không chạy).
// makePurchase = NormalizedPurchase (p6 §4.10); makeStoredPurchase = dạng lưu ở purchases/{key}.
const HOUR = 3600000;
const DAY = 24 * HOUR;
const BASE_NOW_MS = 2000000000000;

function makePurchase(overrides = {}) {
  return {
    store: 'apple',
    key: 'apple_1000',
    lookupId: '1000',
    productId: 'vip_monthly',
    planId: 'monthly',
    kind: 'subscription',
    environment: 'Production',
    accountToken: '11111111-1111-4111-8111-111111111111',
    ownership: 'PURCHASED',
    acknowledged: true,
    status: 'active',
    expiresAtMs: BASE_NOW_MS + 30 * DAY,
    graceExpiresAtMs: null,
    revokedAtMs: null,
    asOfMs: BASE_NOW_MS,
    predecessorLookupId: null,
    latestTransactionId: '2000',
    revokedTransactionId: null,
    purchaseTimeMs: BASE_NOW_MS - 30 * DAY,
    ...overrides,
  };
}

function makeStoredPurchase(overrides = {}) {
  return makePurchase({
    uid: 'uid-1',
    firstSeenAtMs: BASE_NOW_MS - DAY,
    boundAtMs: BASE_NOW_MS - DAY,
    verifiedAtMs: BASE_NOW_MS - HOUR,
    supersededBy: null,
    ...overrides,
  });
}

module.exports = { HOUR, DAY, BASE_NOW_MS, makePurchase, makeStoredPurchase };
