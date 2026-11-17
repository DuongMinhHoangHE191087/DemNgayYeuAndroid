'use strict';

// resolveEntitlement: hàm thuần quyết định VIP từ các purchase đã lưu (p6 §4.2, golden GV-061).
// require đặt trong từng test để mỗi test đỏ riêng khi module chưa có.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { HOUR, DAY, BASE_NOW_MS: NOW, makeStoredPurchase: sp } = require('./builders');

const GOLDEN = path.join(__dirname, '..', '..', '..', '..', 'shared', 'src', 'commonTest', 'resources', 'golden', 'GV-061-entitlement-resolve.json');
const load = () => require('../../src/entitlement/resolveEntitlement');
const resolve = (purchases, adminGrant = null, nowMs = NOW) => {
  const { resolveEntitlement, DEFAULT_POLICY } = load();
  return resolveEntitlement(purchases, nowMs, DEFAULT_POLICY, adminGrant);
};
const NOT_VIP = { vip: false, planId: null, expiresAtMs: null, source: null, environment: null, verifiedAtMs: null };
const notVip = (state) => ({ ...NOT_VIP, state });

test('DEFAULT_POLICY_hasPlannedValues', () => {
  const { DEFAULT_POLICY } = load();
  assert.deepEqual(DEFAULT_POLICY, {
    graceMaxDays: 16, sandboxTtlMs: 24 * HOUR, reverifyAfterMs: 24 * HOUR, maxStaleMs: 96 * HOUR, tokenTtlMs: 72 * HOUR,
    maxUnfinished: 10, maxNewBindings24h: 5, maxSandboxBindings: 3, recheckGapMs: 10 * 60000, nearExpiryMs: 5 * 60000,
    terminalRecheckGapMs: 24 * HOUR, terminalWindowMs: 30 * DAY,
  });
});

test('active_isVip', () => {
  const p = sp({ expiresAtMs: NOW + DAY });
  assert.deepEqual(resolve([p]), {
    vip: true, planId: 'monthly', state: 'active', expiresAtMs: NOW + DAY, source: 'apple', environment: 'Production', verifiedAtMs: NOW - HOUR,
  });
});

test('expiresAtEqualsNow_notVip', () => {
  assert.deepEqual(resolve([sp({ expiresAtMs: NOW })]), notVip('expired'));
  assert.equal(resolve([sp({ expiresAtMs: NOW + 1 })]).vip, true);
});

test('apple_grace_withinCap_isVip', () => {
  const r = resolve([sp({ status: 'grace', expiresAtMs: NOW - 2 * DAY, graceExpiresAtMs: NOW + 3 * DAY })]);
  assert.equal(r.vip, true);
  assert.equal(r.state, 'grace');
});

test('apple_grace_beyondGraceMaxDays_notVip', () => {
  // hết hạn đã 17 ngày: dù graceExpiresAtMs còn xa, trần expiresAtMs + 16 ngày đã qua.
  const r = resolve([sp({ status: 'grace', expiresAtMs: NOW - 17 * DAY, graceExpiresAtMs: NOW + 5 * DAY })]);
  assert.deepEqual(r, notVip('expired'));
});

test('apple_grace_usesGraceExpires_whenEarlierThanCap', () => {
  const p = sp({ status: 'grace', expiresAtMs: NOW - DAY, graceExpiresAtMs: NOW + 2 * DAY });
  const r = resolve([p]);
  assert.equal(r.vip, true);
  assert.equal(r.expiresAtMs, NOW + 2 * DAY);
  assert.equal(resolve([p], null, NOW + 2 * DAY - 1).vip, true);
  assert.equal(resolve([p], null, NOW + 2 * DAY).vip, false);
});

test('apple_grace_nullGraceExpires_notVip_noFabricatedInterval', () => {
  for (const expiresAtMs of [NOW - DAY, NOW + DAY]) {
    assert.deepEqual(resolve([sp({ status: 'grace', expiresAtMs, graceExpiresAtMs: null })]), notVip('retry'));
  }
});

test('apple_grace_graceExpiresBeyondCap_cappedAtGraceMaxDays', () => {
  const cap = NOW - DAY + 16 * DAY;
  // verifiedAtMs tươi tại mốc cap để kiểm trần, không phải kiểm stale.
  const p = sp({ status: 'grace', expiresAtMs: NOW - DAY, graceExpiresAtMs: NOW + 100 * DAY, verifiedAtMs: cap - HOUR });
  assert.equal(resolve([p]).expiresAtMs, cap);
  assert.equal(resolve([p], null, cap - 1).vip, true);
  assert.equal(resolve([p], null, cap).vip, false);
});

test('google_grace_untilExpiryTime_noExtraDays', () => {
  // Google tự kéo expiryTime trong grace: không cộng graceMaxDays, graceExpiresAtMs (nếu có) bị bỏ qua.
  const p = sp({ store: 'google', status: 'grace', expiresAtMs: NOW + HOUR, graceExpiresAtMs: NOW + 10 * DAY });
  assert.equal(resolve([p]).expiresAtMs, NOW + HOUR);
  assert.equal(resolve([p], null, NOW + HOUR - 1).vip, true);
  assert.equal(resolve([p], null, NOW + HOUR).vip, false);
});

test('google_onHold_notVip', () => {
  // normalizeGoogle đưa ON_HOLD/PAUSED về 'retry'; trạng thái lạ cũng phải bị từ chối.
  assert.deepEqual(resolve([sp({ store: 'google', status: 'retry', expiresAtMs: NOW + DAY })]), notVip('retry'));
  assert.equal(resolve([sp({ store: 'google', status: 'on_hold', expiresAtMs: NOW + DAY })]).vip, false);
});

test('google_canceledNotExpired_isVip', () => {
  const r = resolve([sp({ store: 'google', status: 'active', expiresAtMs: NOW + 5 * DAY })]);
  assert.equal(r.vip, true);
  assert.equal(r.source, 'google');
});

test('grace_atExpiryMinus1_isVip', () => {
  const p = sp({ store: 'google', status: 'grace', expiresAtMs: NOW + 1000 });
  assert.equal(resolve([p], null, NOW + 999).vip, true);
});

test('grace_atExpiry_notVip', () => {
  const p = sp({ store: 'google', status: 'grace', expiresAtMs: NOW + 1000 });
  assert.deepEqual(resolve([p], null, NOW + 1000), notVip('expired'));
});

test('recovered_active_isVip', () => {
  // grace -> thanh toán lại thành công: purchase đã ghi đè thành active, hạn mới.
  const r = resolve([sp({ status: 'active', graceExpiresAtMs: NOW - DAY, expiresAtMs: NOW + 30 * DAY })]);
  assert.equal(r.vip, true);
  assert.equal(r.state, 'active');
  assert.equal(r.expiresAtMs, NOW + 30 * DAY);
});

test('retry_notVip', () => {
  assert.deepEqual(resolve([sp({ status: 'retry', expiresAtMs: NOW + DAY })]), notVip('retry'));
});

test('revoked_notVip', () => {
  assert.deepEqual(resolve([sp({ status: 'revoked', expiresAtMs: NOW + DAY })]), notVip('revoked'));
  for (const status of ['expired', 'pending']) {
    assert.equal(resolve([sp({ status, expiresAtMs: NOW + DAY })]).vip, false);
  }
});

test('revocationDate_notVip', () => {
  // status còn 'active' nhưng đã có revokedAtMs (hoàn tiền): không VIP.
  assert.deepEqual(resolve([sp({ status: 'active', expiresAtMs: NOW + DAY, revokedAtMs: NOW - HOUR })]), notVip('revoked'));
});

test('lifetime_beatsSubscription', () => {
  const sub = sp({ key: 'google_a', store: 'google', planId: 'yearly', expiresAtMs: NOW + 300 * DAY });
  const life = sp({ key: 'apple_b', planId: 'lifetime', kind: 'lifetime', expiresAtMs: null });
  for (const list of [[sub, life], [life, sub]]) {
    const r = resolve(list);
    assert.equal(r.planId, 'lifetime');
    assert.equal(r.expiresAtMs, null);
    assert.equal(r.vip, true);
  }
});

test('subscriptionWithNullExpiry_notVip', () => {
  assert.deepEqual(resolve([sp({ kind: 'subscription', expiresAtMs: null })]), notVip('expired'));
  assert.equal(resolve([sp({ status: 'grace', expiresAtMs: null, graceExpiresAtMs: NOW + DAY })]).vip, false);
});

test('lifetimeRevoked_notVip', () => {
  assert.deepEqual(resolve([sp({ planId: 'lifetime', kind: 'lifetime', expiresAtMs: null, status: 'revoked', revokedAtMs: NOW - DAY })]), notVip('revoked'));
  assert.equal(resolve([sp({ planId: 'lifetime', kind: 'lifetime', expiresAtMs: null, status: 'active', revokedAtMs: NOW - DAY })]).vip, false);
});

test('adminGrant_untilNull_isVip', () => {
  assert.deepEqual(resolve([], { untilMs: null }), {
    vip: true, planId: 'admin', state: 'active', expiresAtMs: null, source: 'admin', environment: 'Production', verifiedAtMs: null,
  });
});

test('adminGrant_expired_notVip', () => {
  assert.deepEqual(resolve([], { untilMs: NOW }), notVip('none'));
  assert.deepEqual(resolve([], { untilMs: NOW - DAY }), notVip('none'));
  assert.equal(resolve([], { untilMs: NOW + 1 }).vip, true);
  // grant hỏng (thiếu untilMs) không được coi là vô hạn.
  assert.equal(resolve([], {}).vip, false);
  assert.equal(resolve([], { untilMs: 'never' }).vip, false);
});

test('sandbox_cappedBySandboxTtl', () => {
  const p = sp({ environment: 'Sandbox', firstSeenAtMs: NOW - 23 * HOUR, expiresAtMs: NOW + 30 * DAY });
  const cap = NOW + HOUR;
  const r = resolve([p]);
  assert.equal(r.vip, true);
  assert.equal(r.expiresAtMs, cap);
  assert.equal(r.environment, 'Sandbox');
  assert.equal(resolve([p], null, cap).vip, false);
  // sandbox tự gia hạn (expiresAtMs mới) không kéo dài trần: firstSeenAtMs giữ nguyên.
  assert.equal(resolve([{ ...p, expiresAtMs: NOW + 400 * DAY }], null, cap).vip, false);
  // thiếu firstSeenAtMs thì không có mốc để chặn trần: từ chối.
  assert.equal(resolve([sp({ environment: 'Sandbox', firstSeenAtMs: null })]).vip, false);
});

test('sandbox_losesToProduction', () => {
  const sandbox = sp({ key: 'apple_s', environment: 'Sandbox', planId: 'lifetime', kind: 'lifetime', expiresAtMs: null, firstSeenAtMs: NOW - 23 * HOUR });
  const prod = sp({ key: 'google_p', store: 'google', expiresAtMs: NOW + 10 * 60000 });
  for (const list of [[sandbox, prod], [prod, sandbox]]) {
    const r = resolve(list);
    assert.equal(r.environment, 'Production');
    assert.equal(r.source, 'google');
    assert.equal(r.expiresAtMs, NOW + 10 * 60000);
  }
  // adminGrant còn sống cũng thắng sandbox.
  const r = resolve([sandbox], { untilMs: NOW + 60000 });
  assert.equal(r.source, 'admin');
  assert.equal(r.environment, 'Production');
});

test('sandboxLifetime_cappedAndMarkedSandbox', () => {
  const p = sp({ environment: 'Sandbox', planId: 'lifetime', kind: 'lifetime', expiresAtMs: null, firstSeenAtMs: NOW - 2 * HOUR });
  const r = resolve([p]);
  assert.equal(r.vip, true);
  assert.equal(r.planId, 'lifetime');
  assert.equal(r.environment, 'Sandbox');
  assert.equal(r.expiresAtMs, NOW + 22 * HOUR);
  assert.equal(resolve([p], null, NOW + 22 * HOUR).vip, false);
});

test('purchaseOlderThanMaxStale_notVipStale', () => {
  const stale = sp({ expiresAtMs: NOW + DAY, verifiedAtMs: NOW - 96 * HOUR });
  assert.deepEqual(resolve([stale]), notVip('stale'));
  assert.equal(resolve([sp({ expiresAtMs: NOW + DAY, verifiedAtMs: NOW - 96 * HOUR + 1 })]).vip, true);
  // thiếu verifiedAtMs = chưa từng xác minh: không tính.
  assert.deepEqual(resolve([sp({ expiresAtMs: NOW + DAY, verifiedAtMs: null })]), notVip('stale'));
  // purchase cũ nhưng vốn đã hết quyền thì state phản ánh hết hạn, không phải stale.
  assert.equal(resolve([sp({ expiresAtMs: NOW - DAY, verifiedAtMs: NOW - 100 * HOUR })]).state, 'expired');
  // purchase cũ không chặn purchase mới còn tươi.
  const fresh = sp({ key: 'google_f', store: 'google', expiresAtMs: NOW + HOUR });
  assert.equal(resolve([stale, fresh]).source, 'google');
});

test('superseded_isIgnored', () => {
  const old = sp({ key: 'google_old', store: 'google', expiresAtMs: NOW + 300 * DAY, supersededBy: 'google_new' });
  assert.deepEqual(resolve([old]), notVip('none'));
  const cur = sp({ key: 'google_new', store: 'google', expiresAtMs: NOW + DAY });
  assert.equal(resolve([old, cur]).expiresAtMs, NOW + DAY);
});

test('winnerVerifiedAtMs_returned', () => {
  const a = sp({ key: 'apple_a', expiresAtMs: NOW + DAY, verifiedAtMs: NOW - 5 * HOUR });
  const b = sp({ key: 'google_b', store: 'google', expiresAtMs: NOW + 2 * DAY, verifiedAtMs: NOW - 2 * HOUR });
  assert.equal(resolve([a, b]).verifiedAtMs, NOW - 2 * HOUR);
  assert.equal(resolve([a, b]).source, 'google');
  assert.equal(resolve([]).verifiedAtMs, null);
});

test('adminGrantWinner_sourceAdmin', () => {
  const p = sp({ expiresAtMs: NOW + DAY });
  const r = resolve([p], { untilMs: NOW + 5 * DAY });
  assert.deepEqual(r, { vip: true, planId: 'admin', state: 'active', expiresAtMs: NOW + 5 * DAY, source: 'admin', environment: 'Production', verifiedAtMs: null });
  // purchase xa hơn grant thì purchase thắng.
  assert.equal(resolve([sp({ expiresAtMs: NOW + 9 * DAY })], { untilMs: NOW + DAY }).source, 'apple');
});

test('golden_matches', () => {
  const { resolveEntitlement, DEFAULT_POLICY } = load();
  const golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
  for (const [k, v] of Object.entries(golden.policy)) assert.equal(DEFAULT_POLICY[k], v, `policy.${k}`);
  assert.ok(golden.cases.length >= 10);
  for (const c of golden.cases) {
    assert.deepEqual(resolveEntitlement(c.purchases, golden.nowMs, DEFAULT_POLICY, c.adminGrant), c.expected, c.name);
  }
});
