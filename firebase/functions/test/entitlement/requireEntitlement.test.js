'use strict';

// M-25: enforceGate / requireEntitlement. Server là nguồn sự thật: mặc định TỪ CHỐI, giờ chỉ lấy từ deps.nowMs() (không bao giờ từ client),
// Sandbox / stale / hết hạn / bị thu hồi không bao giờ qua cổng `vip`, và ensureFresh chạy TRƯỚC khi quyết định để bắt hoàn tiền bị lỡ.
const test = require('node:test');
const assert = require('node:assert/strict');
const { HttpsError } = require('firebase-functions/v2/https');
const { enforceGate, requireEntitlement } = require('../../src/entitlement/requireEntitlement');
const { DEFAULT_POLICY } = require('../../src/entitlement/resolveEntitlement');
const { googleKey } = require('../../src/entitlement/normalizeGoogle');
const { makeDeps, fakeApple } = require('./depsBuilder');
const { BASE_NOW_MS: T, DAY, HOUR, makeStoredPurchase } = require('./builders');
const { appleTransaction } = require('./fixtures/apple');

const UID = 'uid-1';
const GTOKEN = 'tok-1';

// Purchase Apple đã lưu, "tươi" mặc định (xác minh và tra 1 giờ trước); ghi kèm vào purchaseKeys của uid.
async function seed(deps, over = {}) {
  const p = makeStoredPurchase({
    asOfMs: T - HOUR, verifiedAtMs: T - HOUR, lastCheckedAtMs: T - HOUR, ackState: 'done', ackAttempts: null, ackNextAtMs: null, ...over,
  });
  const account = deps.db.read(`entitlementAccounts/${p.uid}`);
  await deps.db.collection('purchases').doc(p.key).set(p);
  await deps.db.collection('entitlementAccounts').doc(p.uid).set({ purchaseKeys: [...(account?.purchaseKeys ?? []), p.key] });
  return p;
}
const seedLifetime = (deps, over = {}) => seed(deps, {
  store: 'google', key: googleKey(GTOKEN), lookupId: GTOKEN, productId: 'vip_lifetime', kind: 'lifetime', planId: 'lifetime', expiresAtMs: null, acknowledged: true, latestTransactionId: null, ...over,
});
const grantAdmin = (deps, adminGrant) => deps.db.collection('entitlements').doc(UID).set({ adminGrant });
const appleState = (deps, tx = {}) => ({
  environment: 'Production',
  transaction: appleTransaction({ signedDate: deps.nowMs(), expiresDate: deps.nowMs() + 29 * DAY, ...tx }),
  renewalInfo: null,
  status: 1,
});

const vip = (deps, extra = {}) => requireEntitlement(deps, { uid: UID, ...extra });
const gate = (deps, g, freeAllowed, extra = {}) => enforceGate(deps, { uid: UID, gate: g, freeAllowed, ...extra });

const assertVipRequired = (promise) => assert.rejects(promise, (e) => {
  assert.ok(e instanceof HttpsError, `HttpsError mong đợi, nhận ${e}`);
  assert.equal(e.code, 'permission-denied');
  assert.equal(e.message, 'vip-required');
  assert.deepEqual(e.details, { reason: 'vip-required' });
  return true;
});

// ---- cổng vip: ai được qua ----

test('vipActive_passes', async () => {
  const deps = makeDeps();
  await seed(deps);
  assert.deepEqual(await vip(deps), { tier: 'vip' });
});

test('lifetime_passes', async () => {
  const deps = makeDeps();
  await seedLifetime(deps);
  assert.deepEqual(await vip(deps), { tier: 'vip' });
});

test('adminGrantActive_passes', async () => {
  const deps = makeDeps();
  await grantAdmin(deps, { untilMs: T + DAY });
  assert.deepEqual(await vip(deps), { tier: 'vip' });
  // Hết hạn theo đồng hồ server thì không qua.
  deps.clock.advance(2 * DAY);
  await assertVipRequired(vip(deps));
});

// ---- cổng vip: ai bị từ chối ----

test('noPurchases_deniedVipRequired', async () => {
  const deps = makeDeps();
  await assertVipRequired(vip(deps));
  // Có tài khoản nhưng không có purchase nào cũng vậy.
  await deps.db.collection('entitlementAccounts').doc(UID).set({ purchaseKeys: [] });
  await assertVipRequired(vip(deps));
});

test('expiredByServerClock_denied', async () => {
  const deps = makeDeps();
  await seed(deps, { expiresAtMs: T + DAY }); // đồng hồ server qua hạn; store không trả lời nên dữ liệu lưu giữ nguyên
  assert.deepEqual(await vip(deps), { tier: 'vip' });
  deps.clock.advance(DAY);
  await assertVipRequired(vip(deps));
});

test('staleVerified_deniedVipRequired', async () => {
  const deps = makeDeps();
  await seed(deps, { verifiedAtMs: T - 97 * HOUR, lastCheckedAtMs: T - 97 * HOUR, expiresAtMs: T + 30 * DAY }); // store không tìm thấy quá maxStaleMs
  await assertVipRequired(vip(deps));
});

test('policyComesFromDeps', async () => {
  const deps = makeDeps({ policy: { ...DEFAULT_POLICY, maxStaleMs: 10 * HOUR } });
  await seed(deps, { verifiedAtMs: T - 5 * HOUR, lastCheckedAtMs: T - 5 * HOUR });
  assert.deepEqual(await vip(deps), { tier: 'vip' });
  deps.clock.advance(6 * HOUR); // xác minh đã 11 giờ > maxStaleMs 10 giờ của deps (mặc định là 96 giờ)
  await assertVipRequired(vip(deps));
});

test('sandboxOnly_deniedVipRequired', async () => {
  const deps = makeDeps();
  await seed(deps, { environment: 'Sandbox', firstSeenAtMs: T - HOUR });
  await assertVipRequired(vip(deps));
});

test('revoked_denied', async () => {
  const deps = makeDeps();
  await seed(deps, { status: 'revoked', revokedAtMs: T - HOUR, revokedTransactionId: '2000' });
  await assertVipRequired(vip(deps));
});

// ---- thời gian và độ tươi ----

test('clientSuppliedTime_ignored', async () => {
  const lies = { nowMs: T - 10 * DAY, now: T - 10 * DAY, clientNowMs: T - 10 * DAY, nowSeconds: (T - 10 * DAY) / 1000 };
  // Hết hạn theo server, client nói còn hạn: vẫn bị từ chối.
  const expired = makeDeps();
  await seed(expired, { expiresAtMs: T - HOUR, verifiedAtMs: T - HOUR });
  await assertVipRequired(vip(expired, lies));
  await assertVipRequired(gate(expired, 'vip', true, lies));
  // Còn hạn theo server, client nói đã qua rất lâu: vẫn được qua.
  const live = makeDeps();
  await seed(live);
  assert.deepEqual(await vip(live, { nowMs: T + 365 * DAY, now: T + 365 * DAY }), { tier: 'vip' });
});

test('callsEnsureFresh_refundDetectedBeforeDecision', async () => {
  // Dữ liệu lưu còn "active" nhưng đã cũ; Apple đã hoàn tiền mà webhook bị mất: phải tra store trước khi quyết định.
  const deps = makeDeps({ apple: fakeApple() });
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  deps.apple.states[1000] = appleState(deps, { revocationDate: T - HOUR });
  await assertVipRequired(vip(deps));
  assert.equal(deps.apple.calls.length, 1);
  assert.equal(deps.db.read('purchases/apple_1000').status, 'revoked');
});

// ---- enforceGate ----

test('enforceGate_free_returnsTierNull_noStoreRead', async () => {
  // deps mà chạm vào db / đồng hồ / store đều ném: gate `free` không được đọc gì.
  const boom = () => { throw new Error('không được chạm vào deps'); };
  const deps = new Proxy({}, { get: boom });
  assert.deepEqual(await enforceGate(deps, { uid: UID, gate: 'free', freeAllowed: false }), { tier: null });
  assert.deepEqual(await enforceGate(deps, { uid: UID, gate: 'free' }), { tier: null });
});

test('enforceGate_tiered_vipReturnsTierVip', async () => {
  const deps = makeDeps();
  await seed(deps);
  assert.deepEqual(await gate(deps, 'tiered', true), { tier: 'vip' });
  assert.deepEqual(await gate(deps, 'tiered', false), { tier: 'vip' });
});

test('enforceGate_tiered_freeUserReturnsTierFreeWhenAllowed', async () => {
  const deps = makeDeps();
  assert.deepEqual(await gate(deps, 'tiered', true), { tier: 'free' });
  // Sandbox / hết hạn không phải VIP nhưng vẫn là người dùng miễn phí.
  const sandbox = makeDeps();
  await seed(sandbox, { environment: 'Sandbox', firstSeenAtMs: T - HOUR });
  assert.deepEqual(await gate(sandbox, 'tiered', true), { tier: 'free' });
});

test('enforceGate_tiered_freeUserDeniedWhenNotAllowed', async () => {
  const deps = makeDeps();
  await assertVipRequired(gate(deps, 'tiered', false));
  await assertVipRequired(gate(deps, 'tiered', undefined)); // mặc định từ chối
  const sandbox = makeDeps();
  await seed(sandbox, { environment: 'Sandbox', firstSeenAtMs: T - HOUR });
  await assertVipRequired(gate(sandbox, 'tiered', false));
});

test('enforceGate_vip_deniedForSandbox', async () => {
  const deps = makeDeps();
  await seed(deps, { environment: 'Sandbox', firstSeenAtMs: T - HOUR });
  await assertVipRequired(gate(deps, 'vip', true)); // freeAllowed không mở được cổng vip
  await assertVipRequired(gate(deps, 'vip', false));
});
