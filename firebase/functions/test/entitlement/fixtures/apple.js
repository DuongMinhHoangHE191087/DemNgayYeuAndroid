'use strict';

// Dựng giao dịch / renewalInfo Apple đã giải mã, đúng tên trường của JWSTransactionDecodedPayload và
// JWSRenewalInfoDecodedPayload (app-store-server-library). Không phải test (không có đuôi .test.js).
const TEST_BUNDLE_ID = 'com.example.inlove';
const DAY = 24 * 3600000;
const APPLE_NOW_MS = 2000000000000;

function appleTransaction(overrides = {}) {
  return {
    transactionId: '2000',
    originalTransactionId: '1000',
    bundleId: TEST_BUNDLE_ID,
    productId: 'vip_monthly',
    type: 'Auto-Renewable Subscription',
    environment: 'Production',
    inAppOwnershipType: 'PURCHASED',
    originalPurchaseDate: APPLE_NOW_MS - 30 * DAY,
    purchaseDate: APPLE_NOW_MS - DAY,
    expiresDate: APPLE_NOW_MS + 29 * DAY,
    signedDate: APPLE_NOW_MS,
    appAccountToken: '11111111-1111-4111-8111-111111111111',
    ...overrides,
  };
}

function appleRenewalInfo(overrides = {}) {
  return {
    originalTransactionId: '1000',
    autoRenewProductId: 'vip_monthly',
    productId: 'vip_monthly',
    autoRenewStatus: 1,
    environment: 'Production',
    signedDate: APPLE_NOW_MS,
    ...overrides,
  };
}

module.exports = { TEST_BUNDLE_ID, DAY, APPLE_NOW_MS, appleTransaction, appleRenewalInfo };
