'use strict';

// Giao dịch Apple đã xác minh và giải mã -> NormalizedPurchase (p6 §4.10), hàm thuần, không I/O.
// Mặc định TỪ CHỐI: trường thiếu hoặc lạ ra trạng thái không hưởng quyền (expired) hoặc notOurs, không bao giờ đoán thành VIP.
const { planForProduct } = require('./planCatalog');

// Trạng thái đăng ký của Apple (Get All Subscription Statuses): 1 active, 2 expired, 3 billing retry, 4 grace, 5 revoked.
const STATUS = Object.freeze({ 1: 'active', 2: 'expired', 3: 'retry', 4: 'grace', 5: 'revoked' });
// Chỉ Production và Sandbox (gồm TestFlight); Xcode, LocalTesting và giá trị lạ bị từ chối.
const ENVIRONMENTS = Object.freeze(['Production', 'Sandbox']);

const notOurs = (reason) => ({ notOurs: true, reason });
const num = (v) => (Number.isFinite(v) ? v : null);

// Thiếu thì PURCHASED (Apple bỏ trường này với giao dịch thường); giá trị lạ thì coi là FAMILY_SHARED (bị hạn chế hơn).
function ownershipOf(t) {
  const o = t.inAppOwnershipType;
  if (o === undefined || o === null || o === 'PURCHASED') return 'PURCHASED';
  return 'FAMILY_SHARED';
}

function normalizeApple({ transaction, renewalInfo, status, bundleId, asOfMs }) {
  const t = transaction;
  if (!t || typeof t !== 'object' || !t.originalTransactionId || !t.transactionId) return notOurs('malformed-transaction');
  if (!bundleId || t.bundleId !== bundleId) return notOurs('bundle-mismatch');
  const plan = planForProduct('apple', t.productId);
  if (!plan) return notOurs('unknown-product');
  if (!ENVIRONMENTS.includes(t.environment)) return notOurs('environment-not-supported');

  const revoked = t.revocationDate !== undefined && t.revocationDate !== null;
  const expiresAtMs = plan.kind === 'lifetime' ? null : num(t.expiresDate);
  let state;
  let graceExpiresAtMs = null;
  if (revoked) state = 'revoked';
  else if (plan.kind === 'lifetime') state = 'active';
  else if (status === undefined || status === null) state = expiresAtMs !== null && expiresAtMs > asOfMs ? 'active' : 'expired';
  else state = (typeof status === 'number' && STATUS[status]) || 'expired'; // status lạ (kể cả chuỗi '1') không bao giờ là active
  if (state === 'grace') graceExpiresAtMs = num(renewalInfo && renewalInfo.gracePeriodExpiresDate);

  return {
    store: 'apple',
    key: `apple_${t.originalTransactionId}`,
    lookupId: t.originalTransactionId,
    productId: plan.productId,
    planId: plan.planId,
    kind: plan.kind,
    environment: t.environment,
    accountToken: typeof t.appAccountToken === 'string' ? t.appAccountToken.toLowerCase() : null,
    ownership: ownershipOf(t),
    acknowledged: true,
    status: state,
    expiresAtMs,
    graceExpiresAtMs,
    revokedAtMs: state === 'revoked' ? (revoked ? num(t.revocationDate) ?? asOfMs : asOfMs) : null,
    asOfMs,
    predecessorLookupId: null,
    latestTransactionId: t.transactionId,
    revokedTransactionId: revoked ? t.transactionId : null,
    purchaseTimeMs: num(t.originalPurchaseDate),
  };
}

module.exports = { normalizeApple };
