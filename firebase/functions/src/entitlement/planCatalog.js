'use strict';

// Danh mục gói VIP phía server. Nguồn sự thật đối chiếu: golden shared/.../GV-060-plan-catalog.json (Kotlin đọc ở M-49b).
// Cùng product id trên App Store (apple) và Google Play (google). Không import firebase-admin (DEC-166).
const PLANS = Object.freeze([
  { productId: 'vip_monthly', planId: 'monthly', kind: 'subscription' },
  { productId: 'vip_yearly', planId: 'yearly', kind: 'subscription' },
  { productId: 'vip_lifetime', planId: 'lifetime', kind: 'lifetime' },
]);
const STORES = Object.freeze(['apple', 'google']);

// Trả { planId, kind, productId } khi cửa hàng và product id đều đã biết; còn lại trả null.
function planForProduct(store, productId) {
  if (!STORES.includes(store)) return null;
  const plan = PLANS.find((p) => p.productId === productId);
  return plan ? { planId: plan.planId, kind: plan.kind, productId: plan.productId } : null;
}

module.exports = { PLANS, planForProduct };
