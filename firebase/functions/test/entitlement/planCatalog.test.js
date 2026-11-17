'use strict';

// Danh mục gói VIP (src/entitlement/planCatalog.js) đối chiếu với golden GV-060 trong shared/ (Kotlin đọc cùng file ở M-49b).
// require đặt trong từng test để mỗi test đỏ riêng khi module chưa có.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const GOLDEN = path.join(__dirname, '..', '..', '..', '..', 'shared', 'src', 'commonTest', 'resources', 'golden', 'GV-060-plan-catalog.json');
const load = () => require('../../src/entitlement/planCatalog');
const readGolden = () => JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));

test('planForProduct_knownProducts_matchGolden', () => {
  const { PLANS, planForProduct } = load();
  const golden = readGolden();
  assert.deepEqual(PLANS.map((p) => p.productId).sort(), golden.products.map((p) => p.productId).sort());
  for (const store of golden.stores) {
    for (const p of golden.products) {
      assert.deepEqual(planForProduct(store, p.productId), { planId: p.planId, kind: p.kind, productId: p.productId });
    }
  }
});

test('planForProduct_unknown_returnsNull', () => {
  const { planForProduct } = load();
  assert.equal(planForProduct('apple', 'vip_weekly'), null);
  assert.equal(planForProduct('google', ''), null);
});

test('planForProduct_wrongStore_returnsNull', () => {
  const { planForProduct } = load();
  assert.equal(planForProduct('admin', 'vip_monthly'), null);
  assert.equal(planForProduct('stripe', 'vip_lifetime'), null);
  assert.equal(planForProduct(undefined, 'vip_yearly'), null);
});
