'use strict';

// Chuẩn hoá phản hồi Google Play (SubscriptionPurchaseV2 / ProductPurchaseV2) thành NormalizedPurchase (p6 §4.10). Hàm thuần, không I/O.
// Mặc định TỪ CHỐI: trường thiếu, không đọc được hoặc giá trị lạ không bao giờ thành quyền (status pending, hoặc notOurs).
// Hai phản hồi không mang packageName lẫn purchaseToken: kiểm gói của thông báo RTDN nằm ở M-20, không ở đây.
const crypto = require('node:crypto');
const { planForProduct } = require('./planCatalog');

const ACKNOWLEDGED = 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED';
// RFC 3339 như Google trả; chuỗi khác (năm trần, số, rác) không được Date.parse đoán hộ.
const RFC3339 = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$/;

// ON_HOLD/PAUSED -> retry theo hàng kế hoạch M-14 (resolver không cấp VIP cho retry).
const SUBSCRIPTION_STATUS = Object.freeze({
  SUBSCRIPTION_STATE_ACTIVE: 'active',
  SUBSCRIPTION_STATE_CANCELED: 'active', // vẫn có quyền đến expiryTime; resolver hết hạn theo thời gian
  SUBSCRIPTION_STATE_IN_GRACE_PERIOD: 'grace',
  SUBSCRIPTION_STATE_ON_HOLD: 'retry',
  SUBSCRIPTION_STATE_PAUSED: 'retry',
  SUBSCRIPTION_STATE_EXPIRED: 'expired',
  SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED: 'expired',
});

const notOurs = (reason) => ({ notOurs: true, reason });
const isObj = (v) => v !== null && typeof v === 'object';
const parseTime = (v) => {
  if (typeof v !== 'string' || !RFC3339.test(v)) return null;
  const ms = Date.parse(v);
  return Number.isFinite(ms) ? ms : null;
};
const str = (v) => (typeof v === 'string' && v !== '' ? v : null);
const lower = (v) => (str(v) === null ? null : v.toLowerCase());

function googleKey(purchaseToken) {
  if (str(purchaseToken) === null) throw new TypeError('purchaseToken is required');
  return `google_${crypto.createHash('sha256').update(purchaseToken).digest('hex')}`;
}

// Chọn mục đang mang quyền (không lấy mục đầu, không phụ thuộc thứ tự mảng):
// 1) expiryTime sau asOfMs, muộn nhất; 2) không expiryTime (pending, không mượn hạn mục cũ); 3) expiryTime muộn nhất (đã hết hạn).
function pickLineItem(lineItems, asOfMs) {
  const items = (Array.isArray(lineItems) ? lineItems : [])
    .filter(isObj)
    // Chỉ gói đăng ký của danh mục: gói lifetime trong lineItems đăng ký không được thành kind lifetime (vô hạn ở resolver).
    .map((li) => ({ li, plan: planForProduct('google', li.productId), ms: parseTime(li.expiryTime) }))
    .filter((x) => x.plan && x.plan.kind === 'subscription');
  if (items.length === 0) return null;
  const latest = (arr) => arr.reduce((a, b) => (b.ms > a.ms ? b : a));
  const live = items.filter((x) => x.ms !== null && x.ms > asOfMs);
  if (live.length > 0) return latest(live);
  const noExpiry = items.filter((x) => x.ms === null);
  if (noExpiry.length > 0) return noExpiry[0];
  return latest(items);
}

function base(purchaseToken, asOfMs, plan, fields) {
  return {
    store: 'google',
    key: googleKey(purchaseToken),
    lookupId: purchaseToken,
    productId: plan.productId,
    planId: plan.planId,
    kind: plan.kind,
    ownership: 'PURCHASED',
    graceExpiresAtMs: null,
    revokedAtMs: null,
    asOfMs,
    latestTransactionId: null,
    revokedTransactionId: null,
    ...fields,
  };
}

function normalizeSubscription(purchaseToken, s, asOfMs) {
  const picked = pickLineItem(s.lineItems, asOfMs);
  if (!picked) return notOurs('unknown-product');
  const expiresAtMs = picked.ms;
  // Hạn thiếu hoặc không đọc được thì không bao giờ có quyền, bất kể subscriptionState.
  const status = expiresAtMs === null ? 'pending' : (SUBSCRIPTION_STATUS[s.subscriptionState] || 'pending');
  const ext = isObj(s.externalAccountIdentifiers) ? s.externalAccountIdentifiers : {};
  const outOf = isObj(s.outOfAppPurchaseContext) ? s.outOfAppPurchaseContext : {};
  const expiredIds = isObj(outOf.expiredExternalAccountIdentifiers) ? outOf.expiredExternalAccountIdentifiers : {};
  return base(purchaseToken, asOfMs, picked.plan, {
    environment: s.testPurchase != null ? 'Sandbox' : 'Production',
    accountToken: lower(ext.obfuscatedExternalAccountId) || lower(expiredIds.obfuscatedExternalAccountId),
    acknowledged: s.acknowledgementState === ACKNOWLEDGED,
    status,
    expiresAtMs,
    graceExpiresAtMs: status === 'grace' ? expiresAtMs : null,
    predecessorLookupId: str(s.linkedPurchaseToken) || str(outOf.expiredPurchaseToken),
    purchaseTimeMs: parseTime(s.startTime),
  });
}

function normalizeProduct(purchaseToken, p, asOfMs) {
  const matched = (Array.isArray(p.productLineItem) ? p.productLineItem : [])
    .filter(isObj)
    .map((li) => ({ li, plan: planForProduct('google', li.productId) }))
    .filter((x) => x.plan && x.plan.kind === 'lifetime');
  if (matched.length === 0) return notOurs('unknown-product');
  const { plan } = matched[0];
  // UNVERIFIED #109: hai tín hiệu hoàn tiền; thu hồi chính đến từ RTDN voidedPurchaseNotification (M-20).
  const purchaseState = isObj(p.purchaseStateContext) ? p.purchaseStateContext.purchaseState : undefined;
  const refunded = matched.some((x) => isObj(x.li.productOfferDetails) && x.li.productOfferDetails.refundableQuantity === 0);
  let status = 'pending';
  if (purchaseState === 'CANCELLED' || refunded) status = 'revoked';
  else if (purchaseState === 'PURCHASED') status = 'active';
  return base(purchaseToken, asOfMs, plan, {
    environment: p.testPurchaseContext != null ? 'Sandbox' : 'Production',
    accountToken: lower(p.obfuscatedExternalAccountId),
    acknowledged: p.acknowledgementState === ACKNOWLEDGED,
    status,
    expiresAtMs: null,
    revokedAtMs: status === 'revoked' ? asOfMs : null,
    predecessorLookupId: null,
    purchaseTimeMs: parseTime(p.purchaseCompletionTime),
  });
}

// Đúng một trong subscription / product khác null. Trả NormalizedPurchase hoặc { notOurs: true, reason: 'unknown-product' }.
function normalizeGoogle({ purchaseToken, subscription = null, product = null, asOfMs } = {}) {
  if (str(purchaseToken) === null) throw new TypeError('purchaseToken is required');
  if (!Number.isFinite(asOfMs)) throw new TypeError('asOfMs must be a finite number');
  if ((subscription != null) === (product != null)) throw new TypeError('exactly one of subscription or product is required');
  if (subscription != null) return isObj(subscription) ? normalizeSubscription(purchaseToken, subscription, asOfMs) : notOurs('unknown-product');
  return isObj(product) ? normalizeProduct(purchaseToken, product, asOfMs) : notOurs('unknown-product');
}

module.exports = { normalizeGoogle, googleKey };
