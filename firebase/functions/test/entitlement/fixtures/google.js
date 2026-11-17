'use strict';

// Phản hồi Google Play giả cho test normalizeGoogle. Tên trường theo SubscriptionPurchaseV2 (purchases.subscriptionsv2) và
// ProductPurchaseV2 (purchases.productsv2), đối chiếu 2026-10-13. Không phải test (không có đuôi .test.js).
// overrides được trải nông: truyền `undefined` để bỏ một trường.
const { BASE_NOW_MS, DAY } = require('../builders');

const TEST_PACKAGE_NAME = 'com.example.inlove';
const TEST_TOKEN = '22222222-2222-4222-8222-222222222222';
const ACKNOWLEDGED = 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED';
const iso = (ms) => new Date(ms).toISOString();

function googleSubscription(overrides = {}) {
  return {
    kind: 'androidpublisher#subscriptionPurchaseV2',
    regionCode: 'VN',
    latestOrderId: 'GPA.0000-0000-0000-00000',
    lineItems: [{ productId: 'vip_monthly', expiryTime: iso(BASE_NOW_MS + 30 * DAY) }],
    startTime: iso(BASE_NOW_MS - 30 * DAY),
    subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
    acknowledgementState: ACKNOWLEDGED,
    externalAccountIdentifiers: { obfuscatedExternalAccountId: TEST_TOKEN },
    ...overrides,
  };
}

function googleProduct(overrides = {}) {
  return {
    kind: 'androidpublisher#productPurchaseV2',
    regionCode: 'VN',
    orderId: 'GPA.0000-0000-0000-00001',
    productLineItem: [{ productId: 'vip_lifetime', productOfferDetails: { refundableQuantity: 1 } }],
    purchaseStateContext: { purchaseState: 'PURCHASED' },
    purchaseCompletionTime: iso(BASE_NOW_MS - 30 * DAY),
    acknowledgementState: ACKNOWLEDGED,
    obfuscatedExternalAccountId: TEST_TOKEN,
    ...overrides,
  };
}

module.exports = { TEST_PACKAGE_NAME, TEST_TOKEN, ACKNOWLEDGED, iso, googleSubscription, googleProduct };
