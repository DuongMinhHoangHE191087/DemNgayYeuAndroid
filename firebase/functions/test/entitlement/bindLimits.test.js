'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { bindLimits } = require('../../src/entitlement/bindLimits');
const { DEFAULT_POLICY } = require('../../src/entitlement/resolveEntitlement');
const { DAY, HOUR, BASE_NOW_MS, makePurchase, makeStoredPurchase } = require('./builders');

const NOW = BASE_NOW_MS;
const OLD = NOW - 10 * DAY; // ngoài cửa sổ 24 giờ
const DENY = { ok: false, reason: 'too-many-active' };

// n purchase đã lưu, key riêng, mặc định đã ràng buộc từ lâu để không dính cửa sổ 24 giờ.
function many(n, overrides = {}) {
  return Array.from({ length: n }, (_, i) =>
    makeStoredPurchase({ key: `apple_${5000 + i}`, boundAtMs: OLD, ...overrides }));
}
const run = (uidPurchases, purchase = makePurchase({ key: 'apple_new' })) =>
  bindLimits({ uidPurchases, purchase, nowMs: NOW, policy: DEFAULT_POLICY });

test('underTenUnfinished_ok', () => {
  assert.deepEqual(run(many(9)), { ok: true });
});

test('tenUnfinished_tooManyActive', () => {
  assert.deepEqual(run(many(10)), DENY);
});

test('every unfinished status counts: active, grace, retry, pending', () => {
  const list = ['active', 'grace', 'retry', 'pending', 'active', 'grace', 'retry', 'pending', 'active', 'grace']
    .map((status, i) => makeStoredPurchase({ key: `apple_${6000 + i}`, status, boundAtMs: OLD }));
  assert.deepEqual(run(list), DENY);
});

test('expiredRevokedSupersededNotCounted', () => {
  const list = [
    ...many(9),
    ...many(5, { status: 'expired' }).map((p, i) => ({ ...p, key: `apple_e${i}` })),
    ...many(5, { status: 'revoked' }).map((p, i) => ({ ...p, key: `apple_r${i}` })),
    ...many(5, { supersededBy: 'apple_x' }).map((p, i) => ({ ...p, key: `apple_s${i}` })),
    ...many(5, { revokedAtMs: OLD }).map((p, i) => ({ ...p, key: `apple_v${i}` })),
  ];
  assert.deepEqual(run(list), { ok: true });
});

test('eleventhHistoricalPurchase_stillOk', () => {
  const list = [...many(10, { status: 'expired' }).map((p, i) => ({ ...p, key: `apple_h${i}` })), ...many(9)];
  assert.deepEqual(run(list), { ok: true });
});

test('repeatedRepurchaseOverYears_neverBlocked', () => {
  // mỗi năm mua lại, bản cũ expired và bị supersede: 20 năm vẫn ok
  const list = Array.from({ length: 20 }, (_, i) => makeStoredPurchase({
    key: `apple_y${i}`, status: 'expired', supersededBy: `apple_y${i + 1}`, boundAtMs: NOW - (i + 2) * 365 * DAY,
  }));
  assert.deepEqual(run(list), { ok: true });
});

test('lifetimeAfterManyExpiredSubscriptions_ok', () => {
  const list = many(12, { status: 'expired' }).map((p, i) => ({ ...p, key: `apple_l${i}` }));
  const lifetime = makePurchase({ key: 'google_life', store: 'google', kind: 'lifetime', expiresAtMs: null });
  assert.deepEqual(run(list, lifetime), { ok: true });
});

test('fifthNewBindingIn24h_stillOk', () => {
  assert.deepEqual(run(many(4, { boundAtMs: NOW - HOUR })), { ok: true });
});

test('sixthNewBindingIn24h_tooManyActive', () => {
  assert.deepEqual(run(many(5, { boundAtMs: NOW - HOUR })), DENY);
});

test('rate window counts expired purchases too (binding is binding)', () => {
  assert.deepEqual(run(many(5, { boundAtMs: NOW - HOUR, status: 'expired' })), DENY);
});

test('bindingsOlderThan24h_notCountedForRate', () => {
  const list = [...many(5, { boundAtMs: NOW - DAY - 1 }), ...many(4, { boundAtMs: NOW - HOUR }).map((p, i) => ({ ...p, key: `apple_f${i}` }))];
  assert.deepEqual(run(list), { ok: true });
});

test('sandboxFourthBindingForUid_tooManyActive', () => {
  const sandbox = makePurchase({ key: 'apple_sbx_new', environment: 'Sandbox' });
  assert.deepEqual(run(many(3, { environment: 'Sandbox' }), sandbox), DENY);
});

test('sandboxBindingsCountedInTotal_notWindowed', () => {
  // 3 sandbox cũ, đã expired, bind cách đây rất lâu: vẫn tính vào tổng
  const sandbox = makePurchase({ key: 'apple_sbx_new', environment: 'Sandbox' });
  const list = many(3, { environment: 'Sandbox', status: 'expired', boundAtMs: NOW - 400 * DAY });
  assert.deepEqual(run(list, sandbox), DENY);
  assert.deepEqual(run(list.slice(0, 2), sandbox), { ok: true });
});

test('productionNotLimitedBySandboxCount', () => {
  assert.deepEqual(run(many(3, { environment: 'Sandbox' })), { ok: true });
});

test('sameKeyAlreadyBound_notCounted', () => {
  const list = many(10);
  const same = makePurchase({ key: list[0].key });
  // 9 purchase khác + chính nó: không tự đếm, ok
  assert.deepEqual(run(list, same), { ok: true });
  // 10 purchase khác ngoài nó vẫn bị chặn
  assert.deepEqual(run([...list, makeStoredPurchase({ key: 'apple_extra', boundAtMs: OLD })], same), DENY);
});

test('custom policy is honoured; empty history ok', () => {
  assert.deepEqual(run([]), { ok: true });
  const policy = { ...DEFAULT_POLICY, maxUnfinished: 2 };
  assert.deepEqual(bindLimits({ uidPurchases: many(2), purchase: makePurchase({ key: 'apple_new' }), nowMs: NOW, policy }), DENY);
});

test('malformed input denies (default deny)', () => {
  assert.deepEqual(bindLimits({ uidPurchases: null, purchase: makePurchase(), nowMs: NOW, policy: DEFAULT_POLICY }), DENY);
  assert.deepEqual(bindLimits({ uidPurchases: [], purchase: makePurchase(), nowMs: NOW, policy: { ...DEFAULT_POLICY, maxUnfinished: undefined } }), DENY);
});
