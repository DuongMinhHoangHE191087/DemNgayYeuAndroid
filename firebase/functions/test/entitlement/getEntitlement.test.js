'use strict';

// M-18: getEntitlement tính lại quyền VIP từ các purchase vừa tra mới, bằng giờ SERVER, rồi ký token ES256 gắn uid + nonce.
// Chống bypass: mặc định từ chối; entitlements/{uid} chỉ được đọc adminGrant (kết quả đã lưu ở đó bị bỏ qua); không bao giờ dùng giờ của client.
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { getEntitlement, parseNonce } = require('../../src/entitlement/getEntitlement');
const { verifyEntitlementToken } = require('../../src/entitlement/entitlementToken');
const { DEFAULT_POLICY } = require('../../src/entitlement/resolveEntitlement');
const { makeDeps, fakeApple } = require('./depsBuilder');
const { BASE_NOW_MS: T, HOUR, DAY, makeStoredPurchase } = require('./builders');
const { appleTransaction } = require('./fixtures/apple');

const MIN = 60000;
const UID = 'uid-1';
const NONCE = 'abcdEFGH_-123456';

// Purchase đã lưu, "tươi" mặc định (xác minh và tra 1 giờ trước, ack xong) nên ensureFresh không gọi store; ghi kèm vào purchaseKeys của uid.
async function seed(deps, over = {}) {
  const p = makeStoredPurchase({
    asOfMs: T - HOUR, verifiedAtMs: T - HOUR, lastCheckedAtMs: T - HOUR, ackState: 'done', ackAttempts: null, ackNextAtMs: null, ...over,
  });
  const account = deps.db.read(`entitlementAccounts/${p.uid}`);
  await deps.db.collection('purchases').doc(p.key).set(p);
  await deps.db.collection('entitlementAccounts').doc(p.uid).set({ purchaseKeys: [...(account?.purchaseKeys ?? []), p.key] });
  return p;
}
const setEntitlementDoc = (deps, doc, uid = UID) => deps.db.collection('entitlements').doc(uid).set(doc);
// Khoá công khai sinh từ khoá ký của deps (token kiểm bằng khoá công khai, không bằng khoá riêng).
const publicKeysOf = (deps) => ({
  [deps.signingKey.kid]: crypto.createPublicKey(deps.signingKey.pem).export({ type: 'spki', format: 'der' }).toString('base64'),
});
const run = (deps, over = {}) => getEntitlement(deps, { uid: UID, nonce: NONCE, ...over });
// Kiểm token bằng khoá công khai ở thời điểm của deps; trả claims.
const claimsOf = (deps, token, uid = UID, nonce = NONCE) => verifyEntitlementToken(token, publicKeysOf(deps), { nowMs: deps.nowMs(), uid, nonce });
const invalidArgument = (err) => err.code === 'invalid-argument';

test('badNonce_invalidArgument', async () => {
  const bad = [
    ['thiếu', {}],
    ['data không có', undefined],
    ['null', null],
    ['không phải chuỗi (số)', { nonce: 1234567890123456 }],
    ['không phải chuỗi (mảng)', { nonce: ['a'.repeat(16)] }],
    ['rỗng', { nonce: '' }],
    ['ngắn hơn 16', { nonce: 'a'.repeat(15) }],
    ['dài hơn 64', { nonce: 'a'.repeat(65) }],
    ['ký tự cách', { nonce: `${'a'.repeat(15)} ` }],
    ['ký tự chấm', { nonce: `${'a'.repeat(15)}.` }],
    ['ký tự cộng', { nonce: `${'a'.repeat(15)}+` }],
    ['ký tự gạch chéo', { nonce: `${'a'.repeat(15)}/` }],
    ['ký tự có dấu', { nonce: `${'a'.repeat(15)}é` }],
    ['xuống dòng ở cuối', { nonce: `${'a'.repeat(16)}\n` }],
  ];
  for (const [name, data] of bad) assert.throws(() => parseNonce(data), invalidArgument, name);
  // Biên hợp lệ: đúng 16 và đúng 64, đủ cả bảng ký tự.
  assert.equal(parseNonce({ nonce: 'a'.repeat(16) }), 'a'.repeat(16));
  assert.equal(parseNonce({ nonce: 'Z-_9'.repeat(16) }), 'Z-_9'.repeat(16));
  assert.equal(parseNonce({ nonce: NONCE }), NONCE);
  // getEntitlement cũng từ chối nonce sai, trước khi đụng tới store hay dữ liệu.
  const deps = makeDeps();
  await seed(deps);
  const before = deps.db.snapshot();
  for (const nonce of [undefined, 'short', 'a'.repeat(65), `${'a'.repeat(15)}!`]) {
    await assert.rejects(run(deps, { nonce }), invalidArgument);
  }
  assert.deepEqual(deps.db.snapshot(), before, 'không ghi gì');
  assert.equal(deps.apple.calls.length + deps.google.calls.length, 0);
});

test('noPurchases_notVipSignedToken', async () => {
  const deps = makeDeps();
  const res = await run(deps);
  assert.equal(res.vip, false);
  assert.equal(res.state, 'none');
  assert.equal(res.planId, null);
  assert.equal(res.expiresAtMs, null);
  assert.equal(res.source, null);
  assert.equal(res.environment, null);
  const claims = claimsOf(deps, res.token);
  assert.equal(claims.vip, false);
  assert.equal(claims.planId, null);
  assert.equal(claims.notAfterMs, T + DEFAULT_POLICY.tokenTtlMs);
});

test('activeDoc_vipSignedTokenEchoesNonce', async () => {
  const deps = makeDeps();
  const p = await seed(deps);
  const res = await run(deps);
  assert.equal(res.vip, true);
  assert.equal(res.planId, 'monthly');
  assert.equal(res.state, 'active');
  assert.equal(res.source, 'apple');
  assert.equal(res.environment, 'Production');
  assert.equal(res.expiresAtMs, p.expiresAtMs);
  const claims = claimsOf(deps, res.token);
  assert.equal(claims.nonce, NONCE);
  assert.equal(claims.vip, true);
  assert.equal(claims.planId, 'monthly');
  assert.equal(claims.source, 'apple');
  assert.equal(claims.environment, 'Production');
  assert.equal(claims.expiresAtMs, p.expiresAtMs);
  assert.equal(claims.iat, T);
  assert.equal(claims.notAfterMs, T + DEFAULT_POLICY.tokenTtlMs);
});

test('tokenBindsUid', async () => {
  const deps = makeDeps();
  await seed(deps);
  const mine = await run(deps);
  assert.equal(claimsOf(deps, mine.token).uid, UID);
  // Token của uid này không dùng được cho uid khác hay nonce khác (chống phát lại / đem token sang tài khoản khác).
  assert.throws(() => claimsOf(deps, mine.token, 'uid-2'), { message: 'invalid entitlement token' });
  assert.throws(() => claimsOf(deps, mine.token, UID, 'z'.repeat(16)), { message: 'invalid entitlement token' });
  // uid khác không có purchase: không VIP và token mang đúng uid đó.
  const other = await run(deps, { uid: 'uid-2', nonce: 'y'.repeat(20) });
  assert.equal(other.vip, false);
  assert.equal(claimsOf(deps, other.token, 'uid-2', 'y'.repeat(20)).uid, 'uid-2');
  assert.notEqual(other.token, mine.token);
});

test('expiredByServerClock_notVip', async () => {
  const deps = makeDeps();
  await seed(deps, { expiresAtMs: T + DAY });
  assert.equal((await run(deps)).vip, true);
  deps.clock.set(T + DAY); // hết hạn đúng theo đồng hồ server, dù status lưu vẫn là 'active'
  const res = await run(deps);
  assert.equal(res.vip, false);
  assert.equal(res.state, 'expired');
  assert.equal(res.serverNowMs, T + DAY);
  const claims = claimsOf(deps, res.token);
  assert.equal(claims.vip, false);
  assert.equal(claims.iat, T + DAY);
});

test('lifetime_vipNoExpiry_tokenNotAfterCapped', async () => {
  const deps = makeDeps();
  await seed(deps, { kind: 'lifetime', productId: 'vip_lifetime', planId: 'lifetime', expiresAtMs: null });
  const res = await run(deps);
  assert.equal(res.vip, true);
  assert.equal(res.expiresAtMs, null);
  const claims = claimsOf(deps, res.token);
  assert.equal(claims.expiresAtMs, null);
  assert.equal(claims.notAfterMs, T + DEFAULT_POLICY.tokenTtlMs, 'không vô hạn: bị chặn bởi TTL token');
  // Xác minh cũ (ngoài reverify, trong maxStale; store không đổi): token còn bị chặn bởi hạn tươi của store, ngắn hơn TTL.
  const old = makeDeps();
  await seed(old, { kind: 'lifetime', productId: 'vip_lifetime', planId: 'lifetime', expiresAtMs: null, verifiedAtMs: T - 50 * HOUR, lastCheckedAtMs: T - 50 * HOUR });
  const res2 = await run(old);
  assert.equal(res2.vip, true);
  assert.equal(claimsOf(old, res2.token).notAfterMs, T - 50 * HOUR + DEFAULT_POLICY.maxStaleMs);
});

test('adminGrant_respected', async () => {
  const deps = makeDeps();
  await setEntitlementDoc(deps, { adminGrant: { untilMs: T + DAY } });
  const res = await run(deps);
  assert.equal(res.vip, true);
  assert.equal(res.planId, 'admin');
  assert.equal(res.source, 'admin');
  assert.equal(res.expiresAtMs, T + DAY);
  assert.equal(claimsOf(deps, res.token).notAfterMs, T + DAY, 'token không vượt hạn grant');
  // Không hạn.
  const forever = makeDeps();
  await setEntitlementDoc(forever, { adminGrant: { untilMs: null } });
  const res2 = await run(forever);
  assert.equal(res2.vip, true);
  assert.equal(res2.expiresAtMs, null);
  // Hết hạn theo giờ server thì mất.
  deps.clock.set(T + DAY);
  assert.equal((await run(deps)).vip, false);
});

test('sandboxPurchase_tokenCarriesEnvironmentSandbox', async () => {
  const deps = makeDeps();
  await seed(deps, { environment: 'Sandbox', firstSeenAtMs: T - HOUR });
  const res = await run(deps);
  assert.equal(res.vip, true);
  assert.equal(res.environment, 'Sandbox');
  const claims = claimsOf(deps, res.token);
  assert.equal(claims.environment, 'Sandbox');
  assert.equal(claims.notAfterMs, T - HOUR + DEFAULT_POLICY.sandboxTtlMs, 'sandbox chỉ có quyền có trần');
});

test('staleVerified_notVipStale', async () => {
  const deps = makeDeps(); // store không trả lời được thì purchase giữ nguyên
  await seed(deps, { verifiedAtMs: T - 97 * HOUR, lastCheckedAtMs: T - 97 * HOUR });
  const res = await run(deps);
  assert.equal(res.vip, false);
  assert.equal(res.state, 'stale');
  assert.equal(claimsOf(deps, res.token).vip, false);
});

test('policyComesFromDeps', async () => {
  const deps = makeDeps({ policy: { ...DEFAULT_POLICY, maxStaleMs: 10 * HOUR, tokenTtlMs: 2 * HOUR } });
  await seed(deps, { verifiedAtMs: T - 5 * HOUR, lastCheckedAtMs: T - 5 * HOUR });
  const res = await run(deps);
  assert.equal(res.vip, true);
  assert.equal(claimsOf(deps, res.token).notAfterMs, T + 2 * HOUR);
  deps.clock.advance(6 * HOUR); // xác minh đã 11 giờ > maxStaleMs 10 giờ của deps (mặc định là 96 giờ)
  assert.equal((await run(deps)).state, 'stale');
});

test('callsEnsureFresh', async () => {
  const deps = makeDeps({ apple: fakeApple() });
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR, expiresAtMs: T + 2 * DAY });
  deps.apple.states[1000] = {
    environment: 'Production',
    transaction: appleTransaction({ signedDate: T, expiresDate: T + 29 * DAY }),
    renewalInfo: null,
    status: 1,
  };
  const res = await run(deps);
  assert.deepEqual(deps.apple.calls.map((c) => [c.method, c.args]), [['fetchState', ['1000']]]);
  assert.equal(deps.db.read('purchases/apple_1000').verifiedAtMs, T);
  assert.equal(res.vip, true);
  assert.equal(res.expiresAtMs, T + 29 * DAY, 'dùng kết quả vừa tra, không phải dữ liệu cũ');
  // Tra thấy đã bị hoàn tiền thì mất VIP ngay trong lần gọi này.
  const refunded = makeDeps({ apple: fakeApple() });
  await seed(refunded, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  refunded.apple.states[1000] = {
    environment: 'Production',
    transaction: appleTransaction({ signedDate: T, expiresDate: T + 29 * DAY, revocationDate: T - MIN, revocationReason: 0 }),
    renewalInfo: null,
    status: 5,
  };
  const res2 = await run(refunded);
  assert.equal(res2.vip, false);
  assert.equal(res2.state, 'revoked');
});

test('serverNowMs_isDepsNow_notClient', async () => {
  const deps = makeDeps();
  await seed(deps);
  deps.clock.set(T + 5 * MIN);
  // Mọi thứ kiểu "giờ của client" gửi kèm đều bị bỏ qua.
  const res = await run(deps, { nowMs: 1, serverNowMs: 1, clientNowMs: 1, timestamp: 1, iat: 1 });
  assert.equal(res.serverNowMs, T + 5 * MIN);
  assert.notEqual(res.serverNowMs, Date.now());
  const claims = claimsOf(deps, res.token);
  assert.equal(claims.iat, T + 5 * MIN);
  assert.equal(claims.notAfterMs, T + 5 * MIN + DEFAULT_POLICY.tokenTtlMs);
});

test('response_hasAllFields', async () => {
  const deps = makeDeps();
  await seed(deps);
  const res = await run(deps);
  assert.deepEqual(Object.keys(res).sort(), ['environment', 'expiresAtMs', 'planId', 'serverNowMs', 'source', 'state', 'token', 'vip']);
  assert.equal(typeof res.token, 'string');
  assert.equal(res.token.split('.').length, 3);
  const none = await run(makeDeps());
  assert.deepEqual(Object.keys(none).sort(), Object.keys(res).sort());
});

test('storedEntitlementDocIgnored_recomputedFromPurchases', async () => {
  // Doc đã lưu nói VIP, nhưng không có purchase nào: không được tin.
  const forged = makeDeps();
  await setEntitlementDoc(forged, {
    vip: true, planId: 'lifetime', state: 'active', expiresAtMs: null, source: 'apple', environment: 'Production', verifiedAtMs: T, updatedAtMs: T,
  });
  const res = await run(forged);
  assert.equal(res.vip, false);
  assert.equal(res.state, 'none');
  assert.equal(claimsOf(forged, res.token).vip, false);
  // Doc đã lưu nói không VIP, nhưng purchase còn hạn: vẫn VIP (tính lại, không dùng bản cũ).
  const stale = makeDeps();
  await seed(stale);
  await setEntitlementDoc(stale, { vip: false, planId: null, state: 'expired', expiresAtMs: null, source: null, environment: null, adminGrant: null });
  assert.equal((await run(stale)).vip, true);
  // adminGrant trong cùng doc vẫn được đọc, phần kết quả đã lưu thì không.
  const mixed = makeDeps();
  await setEntitlementDoc(mixed, { vip: true, planId: 'monthly', state: 'active', source: 'apple', adminGrant: { untilMs: T + HOUR } });
  const res3 = await run(mixed);
  assert.equal(res3.planId, 'admin');
  assert.equal(res3.source, 'admin');
});
