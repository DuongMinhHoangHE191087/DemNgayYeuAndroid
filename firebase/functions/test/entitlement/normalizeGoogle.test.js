'use strict';

// normalizeGoogle: SubscriptionPurchaseV2 / ProductPurchaseV2 -> NormalizedPurchase (p6 §4.10). Hàm thuần.
// require đặt trong từng test để mỗi test đỏ riêng khi module chưa có.
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { BASE_NOW_MS, DAY, makeStoredPurchase } = require('./builders');
const { TEST_TOKEN, iso, googleSubscription, googleProduct } = require('./fixtures/google');

const load = () => require('../../src/entitlement/normalizeGoogle');
const TOKEN = 'raw-purchase-token-abc';
const sub = (overrides, extra = {}) => load().normalizeGoogle({ purchaseToken: TOKEN, subscription: googleSubscription(overrides), product: null, asOfMs: BASE_NOW_MS, ...extra });
const prod = (overrides, extra = {}) => load().normalizeGoogle({ purchaseToken: TOKEN, subscription: null, product: googleProduct(overrides), asOfMs: BASE_NOW_MS, ...extra });
const state = (s) => `SUBSCRIPTION_STATE_${s}`;
const item = (productId, expiryTime) => (expiryTime === undefined ? { productId } : { productId, expiryTime });
const future = iso(BASE_NOW_MS + 10 * DAY);
const past = iso(BASE_NOW_MS - DAY);

test('active_isActive', () => {
  const p = sub();
  assert.equal(p.status, 'active');
  assert.equal(p.store, 'google');
  assert.equal(p.kind, 'subscription');
  assert.equal(p.productId, 'vip_monthly');
  assert.equal(p.planId, 'monthly');
  assert.equal(p.environment, 'Production');
  assert.equal(p.ownership, 'PURCHASED');
  assert.equal(p.expiresAtMs, BASE_NOW_MS + 30 * DAY);
  assert.equal(p.graceExpiresAtMs, null);
  assert.equal(p.revokedAtMs, null);
  assert.equal(p.latestTransactionId, null);
  assert.equal(p.revokedTransactionId, null);
  assert.equal(p.asOfMs, BASE_NOW_MS);
});

test('canceledNotExpired_active', () => {
  const p = sub({ subscriptionState: state('CANCELED') });
  assert.equal(p.status, 'active');
  assert.equal(p.expiresAtMs, BASE_NOW_MS + 30 * DAY);
});

test('inGrace_grace', () => {
  const p = sub({ subscriptionState: state('IN_GRACE_PERIOD') });
  assert.equal(p.status, 'grace');
  assert.equal(p.graceExpiresAtMs, p.expiresAtMs);
});

// Ánh xạ ON_HOLD/PAUSED -> retry là giả định ngoài hàng kế hoạch: resolver không bao giờ cấp VIP cho retry.
test('onHold_notEntitled', () => assert.equal(sub({ subscriptionState: state('ON_HOLD') }).status, 'retry'));
test('paused_notEntitled', () => assert.equal(sub({ subscriptionState: state('PAUSED') }).status, 'retry'));
test('expired_expired', () => assert.equal(sub({ subscriptionState: state('EXPIRED'), lineItems: [item('vip_monthly', past)] }).status, 'expired'));
test('pending_notEntitled', () => assert.equal(sub({ subscriptionState: state('PENDING') }).status, 'pending'));
test('pendingPurchaseCanceled_notEntitled', () => assert.equal(sub({ subscriptionState: state('PENDING_PURCHASE_CANCELED') }).status, 'expired'));

test('unknownState_pendingNotEntitled', () => {
  for (const s of [state('UNSPECIFIED'), 'SUBSCRIPTION_STATE_FROM_THE_FUTURE', '', undefined, null, 7]) {
    assert.equal(sub({ subscriptionState: s }).status, 'pending', String(s));
  }
});

test('subscriptionWithoutExpiry_pending', () => {
  const p = sub({ lineItems: [item('vip_monthly')] });
  assert.equal(p.status, 'pending');
  assert.equal(p.expiresAtMs, null);
});

test('unparseableExpiry_pending', () => {
  for (const bad of ['not-a-date', '', 12345, {}, '2026']) {
    const p = sub({ lineItems: [item('vip_monthly', bad)] });
    assert.equal(p.status, 'pending', String(bad));
    assert.equal(p.expiresAtMs, null);
  }
});

test('multipleLineItems_picksCatalogProduct', () => {
  const p = sub({ lineItems: [item('other_sku', iso(BASE_NOW_MS + 90 * DAY)), item('vip_yearly', future)] });
  assert.equal(p.productId, 'vip_yearly');
  assert.equal(p.planId, 'yearly');
  assert.equal(p.expiresAtMs, BASE_NOW_MS + 10 * DAY);
});

test('deferredReplacement_beforeTransition_oldItemEntitles', () => {
  const old = item('vip_monthly', future);
  const next = item('vip_yearly'); // chưa chuyển: mục mới chưa có expiryTime
  for (const lineItems of [[old, next], [next, old]]) {
    const p = sub({ lineItems });
    assert.equal(p.status, 'active');
    assert.equal(p.productId, 'vip_monthly');
    assert.equal(p.expiresAtMs, BASE_NOW_MS + 10 * DAY);
  }
});

test('deferredReplacement_afterTransition_newItemEntitles_bothArrayOrders', () => {
  const old = item('vip_monthly', past);
  const next = item('vip_yearly', iso(BASE_NOW_MS + 365 * DAY));
  for (const lineItems of [[old, next], [next, old]]) {
    const p = sub({ lineItems });
    assert.equal(p.status, 'active');
    assert.equal(p.productId, 'vip_yearly');
    assert.equal(p.planId, 'yearly');
    assert.equal(p.expiresAtMs, BASE_NOW_MS + 365 * DAY);
  }
});

test('twoLiveItems_picksLatestExpiry_bothArrayOrders', () => {
  const soon = item('vip_monthly', iso(BASE_NOW_MS + 5 * DAY));
  const later = item('vip_yearly', iso(BASE_NOW_MS + 365 * DAY));
  for (const lineItems of [[soon, later], [later, soon]]) {
    const p = sub({ lineItems });
    assert.equal(p.productId, 'vip_yearly');
    assert.equal(p.expiresAtMs, BASE_NOW_MS + 365 * DAY);
  }
});

test('deferredReplacement_futureItemWithoutExpiry_pendingNotOldExpired', () => {
  const old = item('vip_monthly', past);
  const next = item('vip_yearly');
  for (const lineItems of [[old, next], [next, old]]) {
    const p = sub({ lineItems });
    assert.equal(p.status, 'pending');
    assert.equal(p.expiresAtMs, null); // không mượn hạn của mục cũ
    assert.equal(p.productId, 'vip_yearly');
  }
});

test('allItemsExpired_picksLatestExpiry_expired', () => {
  const older = item('vip_monthly', iso(BASE_NOW_MS - 20 * DAY));
  const newer = item('vip_yearly', iso(BASE_NOW_MS - 2 * DAY));
  for (const lineItems of [[older, newer], [newer, older]]) {
    const p = sub({ subscriptionState: state('EXPIRED'), lineItems });
    assert.equal(p.status, 'expired');
    assert.equal(p.productId, 'vip_yearly');
    assert.equal(p.expiresAtMs, BASE_NOW_MS - 2 * DAY);
  }
});

test('purchaseTimeMs_isStartTime', () => {
  assert.equal(sub().purchaseTimeMs, BASE_NOW_MS - 30 * DAY);
  assert.equal(sub({ startTime: undefined }).purchaseTimeMs, null);
});

test('lifetimePurchased_active', () => {
  const p = prod();
  assert.equal(p.status, 'active');
  assert.equal(p.kind, 'lifetime');
  assert.equal(p.planId, 'lifetime');
  assert.equal(p.productId, 'vip_lifetime');
  assert.equal(p.expiresAtMs, null);
  assert.equal(p.graceExpiresAtMs, null);
  assert.equal(p.revokedAtMs, null);
  assert.equal(p.purchaseTimeMs, BASE_NOW_MS - 30 * DAY);
  assert.equal(p.environment, 'Production');
  assert.equal(p.predecessorLookupId, null);
  assert.equal(prod({ purchaseCompletionTime: undefined }).purchaseTimeMs, null);
});

test('lifetimePending_pending', () => assert.equal(prod({ purchaseStateContext: { purchaseState: 'PENDING' } }).status, 'pending'));

test('lifetimeUnknownOrMissingState_pending', () => {
  for (const ctx of [{ purchaseState: 'PURCHASE_STATE_UNSPECIFIED' }, { purchaseState: 'FROM_THE_FUTURE' }, {}, undefined, null]) {
    assert.equal(prod({ purchaseStateContext: ctx }).status, 'pending', JSON.stringify(ctx));
  }
});

test('lifetimeCanceled_revoked', () => {
  const p = prod({ purchaseStateContext: { purchaseState: 'CANCELLED' } });
  assert.equal(p.status, 'revoked');
  assert.equal(p.revokedAtMs, BASE_NOW_MS);
});

test('lifetimeRefundableQuantityZero_revoked', () => {
  const p = prod({ productLineItem: [{ productId: 'vip_lifetime', productOfferDetails: { refundableQuantity: 0 } }] });
  assert.equal(p.status, 'revoked');
  assert.equal(p.revokedAtMs, BASE_NOW_MS);
});

test('lifetimeOnlyAcceptsLifetimeKind', () => {
  for (const product of [googleProduct({ productLineItem: [{ productId: 'vip_monthly' }] }), googleProduct({ productLineItem: [{ productId: 'nope' }] }), googleProduct({ productLineItem: [] }), googleProduct({ productLineItem: undefined })]) {
    const p = load().normalizeGoogle({ purchaseToken: TOKEN, subscription: null, product, asOfMs: BASE_NOW_MS });
    assert.deepEqual(p, { notOurs: true, reason: 'unknown-product' });
  }
});

test('subscriptionWithLifetimeProductId_unknownProduct', () => {
  // Chống đội lốt: gói lifetime nằm trong lineItems của đăng ký không được thành kind lifetime (vô hạn ở resolver).
  assert.deepEqual(sub({ lineItems: [item('vip_lifetime', future)] }), { notOurs: true, reason: 'unknown-product' });
});

test('obfuscatedAccountId_mapsToAccountToken', () => {
  assert.equal(sub({ externalAccountIdentifiers: { obfuscatedExternalAccountId: 'AAAAAAAA-1111-4111-8111-AAAAAAAAAAAA' } }).accountToken, 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa');
  assert.equal(sub().accountToken, TEST_TOKEN);
  assert.equal(sub({ externalAccountIdentifiers: undefined }).accountToken, null);
  assert.equal(sub({ externalAccountIdentifiers: { obfuscatedExternalAccountId: '' } }).accountToken, null);
  assert.equal(prod({ obfuscatedExternalAccountId: 'BBBBBBBB-1111-4111-8111-BBBBBBBBBBBB' }).accountToken, 'bbbbbbbb-1111-4111-8111-bbbbbbbbbbbb');
  assert.equal(prod({ obfuscatedExternalAccountId: undefined }).accountToken, null);
});

test('keyUsesSha256OfPurchaseToken', () => {
  const expected = `google_${crypto.createHash('sha256').update(TOKEN).digest('hex')}`;
  assert.equal(load().googleKey(TOKEN), expected);
  assert.equal(sub().key, expected);
  assert.equal(prod().key, expected);
  assert.throws(() => load().googleKey(''));
  assert.throws(() => load().googleKey(undefined));
});

test('lookupId_isRawPurchaseToken', () => {
  assert.equal(sub().lookupId, TOKEN);
  assert.equal(prod().lookupId, TOKEN);
});

test('testPurchase_environmentSandbox', () => {
  assert.equal(sub({ testPurchase: {} }).environment, 'Sandbox');
  assert.equal(sub({ testPurchase: null }).environment, 'Production');
});

test('testPurchaseContext_environmentSandbox', () => {
  assert.equal(prod({ testPurchaseContext: { fopType: 'TEST' } }).environment, 'Sandbox');
  assert.equal(prod({ testPurchaseContext: {} }).environment, 'Sandbox');
  assert.equal(prod({ testPurchaseContext: null }).environment, 'Production');
});

test('linkedPurchaseToken_isPredecessor', () => {
  assert.equal(sub({ linkedPurchaseToken: 'old-token' }).predecessorLookupId, 'old-token');
  assert.equal(sub().predecessorLookupId, null);
});

test('outOfApp_expiredPurchaseToken_isPredecessor', () => {
  assert.equal(sub({ outOfAppPurchaseContext: { expiredPurchaseToken: 'expired-token' } }).predecessorLookupId, 'expired-token');
  // linkedPurchaseToken thắng khi có cả hai
  assert.equal(sub({ linkedPurchaseToken: 'old-token', outOfAppPurchaseContext: { expiredPurchaseToken: 'expired-token' } }).predecessorLookupId, 'old-token');
});

test('outOfApp_expiredAccountIdentifiers_mapToAccountToken', () => {
  const ctx = { expiredExternalAccountIdentifiers: { obfuscatedExternalAccountId: 'CCCCCCCC-1111-4111-8111-CCCCCCCCCCCC' } };
  assert.equal(sub({ externalAccountIdentifiers: undefined, outOfAppPurchaseContext: ctx }).accountToken, 'cccccccc-1111-4111-8111-cccccccccccc');
  // token trực tiếp thắng token của hợp đồng đã hết hạn
  assert.equal(sub({ outOfAppPurchaseContext: ctx }).accountToken, TEST_TOKEN);
});

test('acknowledgementPending_notAcknowledged', () => {
  assert.equal(sub({ acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING' }).acknowledged, false);
  assert.equal(sub({ acknowledgementState: undefined }).acknowledged, false);
  assert.equal(prod({ acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING' }).acknowledged, false);
});

test('acknowledged_true', () => {
  assert.equal(sub().acknowledged, true);
  assert.equal(prod().acknowledged, true);
});

test('unknownProduct_notOurs', () => {
  for (const lineItems of [[item('other_sku', future)], [], undefined, null, 'x', [null]]) {
    assert.deepEqual(sub({ lineItems }), { notOurs: true, reason: 'unknown-product' }, JSON.stringify(lineItems));
  }
});

test('invalidInput_throws', () => {
  const { normalizeGoogle } = load();
  const base = { purchaseToken: TOKEN, asOfMs: BASE_NOW_MS };
  assert.throws(() => normalizeGoogle({ ...base, subscription: null, product: null }));
  assert.throws(() => normalizeGoogle({ ...base, subscription: googleSubscription(), product: googleProduct() }));
  assert.throws(() => normalizeGoogle({ ...base, subscription: googleSubscription(), product: null, asOfMs: NaN }));
  assert.throws(() => normalizeGoogle({ ...base, purchaseToken: '', subscription: googleSubscription(), product: null }));
});

test('feedsResolveEntitlement', () => {
  const { resolveEntitlement } = require('../../src/entitlement/resolveEntitlement');
  const stored = (n) => makeStoredPurchase({ ...n, verifiedAtMs: BASE_NOW_MS });
  assert.equal(resolveEntitlement([stored(sub())], BASE_NOW_MS).vip, true);
  assert.equal(resolveEntitlement([stored(prod())], BASE_NOW_MS).vip, true);
  assert.equal(resolveEntitlement([stored(sub({ subscriptionState: state('IN_GRACE_PERIOD') }))], BASE_NOW_MS).vip, true);
  for (const s of ['ON_HOLD', 'PAUSED', 'PENDING', 'UNSPECIFIED']) {
    assert.equal(resolveEntitlement([stored(sub({ subscriptionState: state(s) }))], BASE_NOW_MS).vip, false, s);
  }
  assert.equal(resolveEntitlement([stored(sub({ lineItems: [item('vip_monthly')] }))], BASE_NOW_MS).vip, false);
  assert.equal(resolveEntitlement([stored(prod({ purchaseStateContext: { purchaseState: 'CANCELLED' } }))], BASE_NOW_MS).vip, false);
  assert.equal(resolveEntitlement([stored(prod({ purchaseStateContext: { purchaseState: 'PENDING' } }))], BASE_NOW_MS).vip, false);
});
