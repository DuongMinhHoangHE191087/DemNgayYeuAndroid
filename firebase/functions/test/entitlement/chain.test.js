'use strict';

// M-26: kịch bản tấn công ĐẦU-CUỐI qua nhiều handler thật (claimPurchase, getEntitlement, hai webhook, ensureFresh, requireEntitlement,
// release/rebind, token ký) trên Firestore và cổng store giả. Mỗi kịch bản khẳng định kết quả BẢO MẬT (mã lỗi, lý do, trạng thái đã lưu),
// đỏ nếu một mắt xích sai. Mặc định TỪ CHỐI; bị từ chối thì không ghi gì (ngoài dòng audit claim-denied).
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { HttpsError } = require('firebase-functions/v2/https');
const { claimPurchase } = require('../../src/entitlement/claimPurchase');
const { getEntitlement } = require('../../src/entitlement/getEntitlement');
const { getOrCreateAccountToken } = require('../../src/entitlement/accountToken');
const { handleAppleNotification } = require('../../src/entitlement/appleNotifications');
const { handleGoogleNotification } = require('../../src/entitlement/googleNotifications');
const { requireEntitlement, enforceGate } = require('../../src/entitlement/requireEntitlement');
const { releaseEntitlementBindings } = require('../../src/entitlement/release');
const { rebindPurchase } = require('../../src/entitlement/rebindPurchase');
const { verifyEntitlementToken } = require('../../src/entitlement/entitlementToken');
const { googleKey } = require('../../src/entitlement/normalizeGoogle');
const { makeDeps, fakeApple, fakeGoogle } = require('./depsBuilder');
const { BASE_NOW_MS: T, DAY, HOUR } = require('./builders');
const { appleTransaction } = require('./fixtures/apple');
const { googleSubscription, iso } = require('./fixtures/google');

const MIN = 60000;
const NONCE = 'abcdEFGH_-123456';
const AKEY = 'apple_1000';
const AUDIT_PREFIX = 'entitlementAudit/claim-denied_';

// ---- dụng cụ ----

// Hai tài khoản uid-1, uid-2 đã có appAccountToken do server cấp (đường getOrCreateAccountToken thật).
async function world(opts = {}) {
  const deps = makeDeps({ apple: fakeApple(), google: fakeGoogle(), ...opts });
  const token1 = await getOrCreateAccountToken(deps, { uid: 'uid-1' });
  const token2 = await getOrCreateAccountToken(deps, { uid: 'uid-2' });
  return { deps, token1, token2 };
}
// Cửa sổ legacy Google đang mở: cutover hôm qua, hết hạn ngày mai; mọi tài khoản tạo từ 100 ngày trước, trừ 'uid-new' (tạo sau cutover).
const legacyOpts = () => ({
  legacyClaimUntilMs: T + DAY,
  accountTokenCutoverMs: T - DAY,
  authCreatedMs: async (uid) => (uid === 'uid-new' ? T : T - 100 * DAY),
});

// Apple: trạng thái chuỗi 1000 (ký lúc đồng hồ hiện tại); revoked = bị hoàn tiền.
const appleState = (deps, token, tx = {}, extra = {}) => ({
  environment: 'Production', transaction: appleTransaction({ signedDate: deps.nowMs(), appAccountToken: token, ...tx }), renewalInfo: null, status: 1, ...extra,
});
const refundedState = (deps, token) => appleState(deps, token, { revocationDate: deps.nowMs() }, { status: 5 });
const setApple = (deps, state) => { deps.apple.states['1000'] = state; };

let seq = 0;
// Đăng ký một thông báo Apple đã "xác minh" trong cổng giả, trả signedPayload.
function appleNotice(deps, token, over = {}, txOver = {}) {
  seq += 1;
  const signedPayload = `chain-jws-${seq}`;
  deps.apple.notifications[signedPayload] = {
    notificationType: 'DID_RENEW', subtype: null, notificationUUID: `chain-uuid-${seq}`, signedDateMs: deps.nowMs(), environment: 'Production',
    bundleId: deps.bundleId, status: 1, transaction: appleTransaction({ signedDate: deps.nowMs(), appAccountToken: token, ...txOver }), renewalInfo: null, ...over,
  };
  return signedPayload;
}
const sendApple = (deps, signedPayload) => handleAppleNotification(deps, { signedPayload });

// Google: phản hồi đăng ký mang token (null = giao dịch legacy không token).
const gsub = (token, o = {}) => googleSubscription({ externalAccountIdentifiers: token === null ? {} : { obfuscatedExternalAccountId: token }, ...o });
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64');
const rtdn = (deps, notificationType, purchaseToken, messageId) => ({
  data: b64({
    version: '1.0', packageName: deps.packageName, eventTimeMillis: String(deps.nowMs()),
    subscriptionNotification: { version: '1.0', notificationType, purchaseToken, subscriptionId: 'vip_monthly' },
  }),
  messageId,
});
const PURCHASED = 4;

const claim = (deps, uid, store, id, extra = {}) => claimPurchase(deps, { ...extra, uid, store, id }); // uid lấy từ xác thực, trường tự khai của client không đè được
const publicKeysOf = (deps) => ({
  [deps.signingKey.kid]: crypto.createPublicKey(deps.signingKey.pem).export({ type: 'spki', format: 'der' }).toString('base64'),
});
// getEntitlement rồi kiểm token bằng khoá công khai: trả { res, claims } (claims khớp uid + nonce của lời gọi).
async function entitlementOf(deps, uid, nonce = NONCE) {
  const res = await getEntitlement(deps, { uid, nonce });
  const claims = verifyEntitlementToken(res.token, publicKeysOf(deps), { nowMs: deps.nowMs(), uid, nonce });
  assert.equal(claims.uid, uid);
  assert.equal(claims.vip, res.vip);
  return { res, claims };
}
const stored = (deps, path) => deps.db.read(path);
const auditDocs = (deps, prefix) => Object.keys(deps.db.snapshot()).filter((p) => p.startsWith(prefix));
// Ảnh chụp không tính dòng audit claim-denied: bị từ chối thì chỉ được thêm đúng các dòng đó.
const withoutClaimAudit = (deps) => Object.fromEntries(Object.entries(deps.db.snapshot()).filter(([p]) => !p.startsWith(AUDIT_PREFIX)));

async function assertHttps(promise, code, message) {
  await assert.rejects(promise, (e) => {
    assert.ok(e instanceof HttpsError, `HttpsError mong đợi, nhận ${e}`);
    assert.equal(e.code, code);
    assert.equal(e.message, message);
    return true;
  });
}
const assertDenied = (promise, reason) => assertHttps(promise, 'permission-denied', reason);
async function assertNoWrites(deps, run) {
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  await run();
  assert.deepEqual(deps.db.snapshot(), before, 'không được ghi gì');
  assert.equal(deps.db.writes, writes);
}
// Cổng vip: ném permission-denied 'vip-required'.
const assertVipRequired = (deps, uid) => assertDenied(requireEntitlement(deps, { uid }), 'vip-required');

// ---- Apple: phát lại, khác uid, khác tài khoản ----

test('replayRefundedTransaction_notGranted', async () => {
  const { deps, token1 } = await world();
  setApple(deps, refundedState(deps, token1));
  // Client phát lại giao dịch đã hoàn tiền, kèm cả các trường tự khai: server chỉ nhận {store, id} và hỏi lại Apple.
  const forged = { vip: true, planId: 'lifetime', status: 'active', expiresAtMs: T + 999 * DAY, revocationDate: null };
  assert.deepEqual(await claim(deps, 'uid-1', 'apple', '1000', forged), { vip: false, planId: 'monthly', state: 'revoked' });
  assert.equal(stored(deps, `purchases/${AKEY}`).status, 'revoked');
  assert.equal(stored(deps, 'entitlements/uid-1').vip, false);
  // Phát lại lần nữa muộn hơn, Apple vẫn ký bản hoàn tiền mới hơn: không bao giờ sống lại.
  deps.clock.advance(HOUR);
  setApple(deps, refundedState(deps, token1));
  assert.deepEqual(await claim(deps, 'uid-1', 'apple', '1000'), { vip: false, planId: 'monthly', state: 'revoked' });
  const { res, claims } = await entitlementOf(deps, 'uid-1');
  assert.equal(res.vip, false);
  assert.equal(res.state, 'revoked');
  assert.equal(claims.vip, false);
  await assertVipRequired(deps, 'uid-1');
});

test('otherUidClaimsSameTransaction_rejected', async () => {
  const { deps, token1 } = await world();
  setApple(deps, appleState(deps, token1));
  assert.equal((await claim(deps, 'uid-1', 'apple', '1000')).vip, true);
  deps.clock.advance(HOUR);
  const before = withoutClaimAudit(deps);
  // uid-2 gửi lại cùng giao dịch, cố khai uid của chủ: bị từ chối, trạng thái của uid-1 không đổi.
  await assertDenied(claim(deps, 'uid-2', 'apple', '1000', { uid: 'uid-1', ownerUid: 'uid-2' }), 'already-bound');
  assert.deepEqual(withoutClaimAudit(deps), before);
  assert.equal(stored(deps, `purchases/${AKEY}`).uid, 'uid-1');
  assert.deepEqual(auditDocs(deps, AUDIT_PREFIX), [`${AUDIT_PREFIX}${AKEY}_${Math.floor(deps.nowMs() / HOUR)}`]);
  assert.equal(stored(deps, `${AUDIT_PREFIX}${AKEY}_${Math.floor(deps.nowMs() / HOUR)}`).reason, 'already-bound');
  const other = await entitlementOf(deps, 'uid-2');
  assert.equal(other.res.vip, false);
  assert.equal(other.res.state, 'none');
  assert.equal((await entitlementOf(deps, 'uid-1')).res.vip, true);
  await assertVipRequired(deps, 'uid-2');
});

test('sameAppleIdTwoFirebaseAccounts_secondRejected', async () => {
  const { deps, token1 } = await world();
  setApple(deps, appleState(deps, token1));
  assert.equal((await claim(deps, 'uid-1', 'apple', '1000')).vip, true);
  deps.clock.advance(HOUR);
  // Cùng Apple ID đăng nhập vào tài khoản Firebase thứ hai rồi "khôi phục mua": thử nhiều lần vẫn bị chặn.
  for (let i = 0; i < 3; i += 1) await assertDenied(claim(deps, 'uid-2', 'apple', '1000'), 'already-bound');
  // Gia hạn của Apple (mang token của tài khoản thứ nhất) chỉ đi về chủ thật.
  const renewal = appleNotice(deps, token1, {}, { transactionId: '2001', expiresDate: T + 59 * DAY });
  setApple(deps, appleState(deps, token1, { transactionId: '2001', expiresDate: T + 59 * DAY }));
  assert.deepEqual(await sendApple(deps, renewal), { httpStatus: 200 });
  assert.equal(stored(deps, 'entitlements/uid-1').expiresAtMs, T + 59 * DAY);
  assert.equal(stored(deps, 'entitlements/uid-2'), undefined);
  assert.deepEqual(stored(deps, 'entitlementAccounts/uid-2').purchaseKeys, undefined);
  assert.equal(stored(deps, `purchases/${AKEY}`).uid, 'uid-1');
  const second = await entitlementOf(deps, 'uid-2');
  assert.equal(second.res.vip, false);
  assert.equal(second.claims.vip, false);
});

test('staleNotificationAfterRefund_ignored', async () => {
  const { deps, token1 } = await world();
  setApple(deps, appleState(deps, token1));
  assert.equal((await claim(deps, 'uid-1', 'apple', '1000')).vip, true);
  deps.clock.advance(HOUR);
  // Hoàn tiền ký lúc T+1h.
  setApple(deps, refundedState(deps, token1));
  const refund = appleNotice(deps, token1, { notificationType: 'REFUND', status: 5 }, { revocationDate: deps.nowMs() });
  assert.deepEqual(await sendApple(deps, refund), { httpStatus: 200 });
  assert.equal(stored(deps, `purchases/${AKEY}`).status, 'revoked');
  assert.equal(stored(deps, 'entitlements/uid-1').vip, false);
  // DID_RENEW ký TRƯỚC lần hoàn tiền (T+30 phút) đến muộn: bị bỏ qua, không ghi gì, không khôi phục VIP.
  const late = appleNotice(deps, token1, { signedDateMs: T + 30 * MIN }, { transactionId: '2001', expiresDate: T + 59 * DAY, signedDate: T + 30 * MIN });
  await assertNoWrites(deps, async () => assert.deepEqual(await sendApple(deps, late), { httpStatus: 200 }));
  assert.equal(stored(deps, `purchases/${AKEY}`).status, 'revoked');
  assert.equal(stored(deps, `purchases/${AKEY}`).latestTransactionId, '2000');
  const { res } = await entitlementOf(deps, 'uid-1');
  assert.equal(res.vip, false);
  assert.equal(res.state, 'revoked');
});

test('forgedSignature_rejected', async () => {
  const { deps, token1 } = await world();
  setApple(deps, appleState(deps, token1));
  assert.equal((await claim(deps, 'uid-1', 'apple', '1000')).vip, true);
  deps.clock.advance(HOUR);
  const before = deps.db.snapshot();
  // Thông báo Apple giả (chữ ký không qua được verifyNotification): 400, không ghi gì, VIP nguyên vẹn.
  await assertNoWrites(deps, async () => {
    assert.deepEqual(await sendApple(deps, 'jws-gia-mao-hoan-tien'), { httpStatus: 400 });
    assert.deepEqual(await sendApple(deps, ''), { httpStatus: 400 });
    assert.deepEqual(await sendApple(deps, 'x'.repeat(65537)), { httpStatus: 400 });
  });
  // Google: RTDN giả chỉ là gợi ý, store không biết token đó thì không có gì để áp dụng.
  await assertNoWrites(deps, async () => {
    const res = await handleGoogleNotification(deps, rtdn(deps, PURCHASED, 'token-gia-mao', 'forged-1'));
    assert.equal(res.ignored, 'not-ours');
    const foreign = JSON.parse(Buffer.from(rtdn(deps, PURCHASED, 'token-gia-mao', 'forged-2').data, 'base64').toString());
    foreign.packageName = 'com.attacker.app';
    assert.equal((await handleGoogleNotification(deps, { data: b64(foreign), messageId: 'forged-3' })).ignored, 'foreign-package');
  });
  // Claim giao dịch Apple không có thật: not-ours, không ghi gì ngoài audit (không có audit cho not-ours).
  await assertDenied(claim(deps, 'uid-1', 'apple', '999999'), 'not-ours');
  await assertDenied(claim(deps, 'uid-2', 'google', 'token-gia-mao'), 'not-ours');
  assert.deepEqual(deps.db.snapshot(), before);
  assert.equal((await entitlementOf(deps, 'uid-1')).res.vip, true);
  assert.equal((await entitlementOf(deps, 'uid-2')).res.vip, false);
});

// ---- hoàn tiền ----

test('claimThenRefundNotification_vipOffOnNextGetEntitlement', async () => {
  const { deps, token1 } = await world();
  setApple(deps, appleState(deps, token1));
  assert.deepEqual(await claim(deps, 'uid-1', 'apple', '1000'), { vip: true, planId: 'monthly', state: 'active' });
  const before = await entitlementOf(deps, 'uid-1');
  assert.equal(before.res.vip, true);
  assert.equal(before.claims.planId, 'monthly');
  assert.equal(before.claims.environment, 'Production');
  assert.deepEqual(await requireEntitlement(deps, { uid: 'uid-1' }), { tier: 'vip' });
  deps.clock.advance(HOUR);
  setApple(deps, refundedState(deps, token1));
  assert.deepEqual(await sendApple(deps, appleNotice(deps, token1, { notificationType: 'REFUND', status: 5 }, { revocationDate: deps.nowMs() })), { httpStatus: 200 });
  const after = await entitlementOf(deps, 'uid-1', 'nonce-moi-1234567890');
  assert.equal(after.res.vip, false);
  assert.equal(after.res.state, 'revoked');
  assert.equal(after.claims.vip, false);
  assert.equal(after.claims.planId, null);
  // Token VIP cũ không đổi được thành VIP cho nonce mới; cổng vip từ chối.
  assert.throws(() => verifyEntitlementToken(before.res.token, publicKeysOf(deps), { nowMs: deps.nowMs(), uid: 'uid-1', nonce: 'nonce-moi-1234567890' }), /invalid entitlement token/);
  await assertVipRequired(deps, 'uid-1');
});

test('refundWithoutAnyNotification_nextGetEntitlementRevokes', async () => {
  const { deps, token1 } = await world();
  setApple(deps, appleState(deps, token1));
  assert.equal((await claim(deps, 'uid-1', 'apple', '1000')).vip, true);
  // Apple hoàn tiền nhưng webhook bị mất: không có thông báo nào. 25 giờ sau dữ liệu lưu đã cũ, ensureFresh hỏi lại Apple.
  deps.clock.advance(25 * HOUR);
  setApple(deps, refundedState(deps, token1));
  const fetchesBefore = deps.apple.calls.filter((c) => c.method === 'fetchState').length;
  const { res, claims } = await entitlementOf(deps, 'uid-1');
  assert.equal(deps.apple.calls.filter((c) => c.method === 'fetchState').length, fetchesBefore + 1, 'ensureFresh phải hỏi lại Apple');
  assert.equal(res.vip, false);
  assert.equal(res.state, 'revoked');
  assert.equal(claims.vip, false);
  assert.equal(stored(deps, `purchases/${AKEY}`).status, 'revoked');
  assert.equal(stored(deps, 'entitlements/uid-1').vip, false);
  await assertVipRequired(deps, 'uid-1');
});

// ---- xoá tài khoản, RTDN, token ----

test('deleteAccountThenReclaim_ownerDeleted_thenRebindWorks', async () => {
  const { deps, token1 } = await world();
  setApple(deps, appleState(deps, token1));
  assert.equal((await claim(deps, 'uid-1', 'apple', '1000')).vip, true);
  deps.clock.advance(HOUR);
  await deps.db.collection('accountDeletions').doc('uid-1').set({ state: 'done' });
  assert.equal((await releaseEntitlementBindings(deps, { uid: 'uid-1' })).released, true);
  assert.equal(stored(deps, 'appAccountTokens/' + token1).deleted, true);
  // Sau khi xoá: uid khác (và cả uid cũ tạo lại) claim bị chặn; gia hạn của Apple không cấp VIP cho ai.
  for (const uid of ['uid-2', 'uid-1']) await assertDenied(claim(deps, uid, 'apple', '1000'), 'owner-deleted');
  assert.deepEqual(await sendApple(deps, appleNotice(deps, token1, {}, { transactionId: '2001', expiresDate: T + 59 * DAY })), { httpStatus: 200 });
  assert.equal(stored(deps, `purchases/${AKEY}`).uid, null);
  assert.equal(stored(deps, 'entitlements/uid-2'), undefined);
  assert.equal((await entitlementOf(deps, 'uid-2')).res.vip, false);
  // Chỉ rebindPurchase (hỗ trợ có bằng chứng) mới gắn lại được.
  const res = await rebindPurchase(deps, { key: AKEY, toUid: 'uid-2', evidence: 'Đơn Apple đối chiếu', operator: 'ops-1' });
  assert.equal(res.ok, true);
  assert.equal(res.applied, true);
  assert.equal(stored(deps, `purchases/${AKEY}`).uid, 'uid-2');
  const owner = await entitlementOf(deps, 'uid-2');
  assert.equal(owner.res.vip, true);
  assert.equal(owner.res.planId, 'monthly');
  assert.equal((await entitlementOf(deps, 'uid-1')).res.vip, false);
  // Chủ mới claim lại: idempotent; chủ cũ vẫn không.
  assert.equal((await claim(deps, 'uid-2', 'apple', '1000')).vip, true);
  await assertDenied(claim(deps, 'uid-1', 'apple', '1000'), 'already-bound');
});

test('googleRtdnThenReclaim_idempotent', async () => {
  const { deps, token1 } = await world();
  const GTOK = 'g-tok-1';
  const GKEY = googleKey(GTOK);
  deps.google.subscriptions[GTOK] = gsub(token1, { acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING' });
  // RTDN đến trước client: gắn cho chủ token và acknowledge.
  const first = await handleGoogleNotification(deps, rtdn(deps, PURCHASED, GTOK, 'm1'));
  assert.equal(first.applied, true);
  assert.equal(stored(deps, `purchases/${GKEY}`).uid, 'uid-1');
  const bound = stored(deps, `purchases/${GKEY}`).boundAtMs;
  deps.clock.advance(HOUR);
  // Client claim lại: cùng uid, không ràng buộc mới, không acknowledge lần hai, không nhân đôi khoá.
  assert.deepEqual(await claim(deps, 'uid-1', 'google', GTOK), { vip: true, planId: 'monthly', state: 'active' });
  assert.deepEqual(await claim(deps, 'uid-1', 'google', GTOK), { vip: true, planId: 'monthly', state: 'active' });
  assert.equal(stored(deps, `purchases/${GKEY}`).boundAtMs, bound);
  assert.equal(stored(deps, `purchases/${GKEY}`).ackState, 'done');
  assert.deepEqual(stored(deps, 'entitlementAccounts/uid-1').purchaseKeys, [GKEY]);
  assert.equal(deps.google.calls.filter((c) => c.method === 'acknowledgeSubscription').length, 1);
  // RTDN gửi lại cùng messageId: bỏ qua; uid khác vẫn bị chặn.
  const dup = await handleGoogleNotification(deps, rtdn(deps, PURCHASED, GTOK, 'm1'));
  assert.equal(dup.ignored, 'duplicate');
  await assertDenied(claim(deps, 'uid-2', 'google', GTOK), 'already-bound');
  const { res } = await entitlementOf(deps, 'uid-1');
  assert.equal(res.vip, true);
  assert.equal(res.source, 'google');
  assert.equal(stored(deps, 'entitlements/uid-1').vip, true);
  assert.equal(stored(deps, 'entitlements/uid-2'), undefined);
});

test('tokenReplayAcrossUid_wrongUidInClaims', async () => {
  const { deps, token1 } = await world();
  setApple(deps, appleState(deps, token1));
  assert.equal((await claim(deps, 'uid-1', 'apple', '1000')).vip, true);
  const victim = await getEntitlement(deps, { uid: 'uid-1', nonce: NONCE });
  assert.equal(victim.vip, true);
  const keys = publicKeysOf(deps);
  const verifyAs = (token, uid, nonce = NONCE) => verifyEntitlementToken(token, keys, { nowMs: deps.nowMs(), uid, nonce });
  assert.equal(verifyAs(victim.token, 'uid-1').uid, 'uid-1');
  // uid-2 phát lại token VIP của uid-1 (cả cùng nonce): claims.uid là uid-1 nên bị từ chối.
  assert.throws(() => verifyAs(victim.token, 'uid-2'), /invalid entitlement token/);
  assert.throws(() => verifyAs(victim.token, 'uid-1', 'nonce-khac-1234567890'), /invalid entitlement token/);
  // Sửa uid trong payload mà giữ chữ ký: chữ ký hỏng.
  const [h, p, s] = victim.token.split('.');
  const body = JSON.parse(Buffer.from(p, 'base64url').toString());
  body.uid = 'uid-2';
  const tampered = [h, Buffer.from(JSON.stringify(body)).toString('base64url'), s].join('.');
  assert.throws(() => verifyAs(tampered, 'uid-2'), /invalid entitlement token/);
  // Token hợp lệ của uid-2 là của uid-2, không VIP.
  const mine = await getEntitlement(deps, { uid: 'uid-2', nonce: NONCE });
  assert.equal(mine.vip, false);
  const claims = verifyAs(mine.token, 'uid-2');
  assert.equal(claims.uid, 'uid-2');
  assert.equal(claims.vip, false);
  assert.throws(() => verifyAs(mine.token, 'uid-1'), /invalid entitlement token/);
});

test('sandboxPurchaseNeverSatisfiesVipGate', async () => {
  const { deps, token1, token2 } = await world();
  // uid-1 mua Sandbox (TestFlight); uid-2 mua Production làm đối chứng.
  setApple(deps, appleState(deps, token1, { environment: 'Sandbox' }, { environment: 'Sandbox' }));
  await claim(deps, 'uid-1', 'apple', '1000');
  const sandbox = await entitlementOf(deps, 'uid-1');
  if (sandbox.res.vip) assert.equal(sandbox.res.environment, 'Sandbox'); // quyền có trần, không bao giờ đội lốt Production
  assert.equal(sandbox.claims.environment, sandbox.res.vip ? 'Sandbox' : null);
  await assertVipRequired(deps, 'uid-1');
  await assert.rejects(enforceGate(deps, { uid: 'uid-1', gate: 'vip' }), (e) => e.code === 'permission-denied');
  // Cổng tiered chỉ cho tầng free (không bao giờ vip); không cho free thì từ chối.
  assert.deepEqual(await enforceGate(deps, { uid: 'uid-1', gate: 'tiered', freeAllowed: true }), { tier: 'free' });
  await assertDenied(enforceGate(deps, { uid: 'uid-1', gate: 'tiered', freeAllowed: false }), 'vip-required');
  // Hết trần 24 giờ thì không còn VIP kể cả khi Apple vẫn trả active.
  deps.clock.advance(25 * HOUR);
  setApple(deps, appleState(deps, token1, { environment: 'Sandbox' }, { environment: 'Sandbox' }));
  assert.equal((await entitlementOf(deps, 'uid-1')).res.vip, false);
  await assertVipRequired(deps, 'uid-1');
  // Đối chứng: Production qua cổng vip.
  deps.apple.states['1000'] = undefined;
  delete deps.apple.states['1000'];
  deps.apple.states['3000'] = undefined;
  delete deps.apple.states['3000'];
  const prod = await makeProdControl(deps, token2);
  assert.deepEqual(prod, { tier: 'vip' });
});

// Đối chứng Production cho uid-2: chuỗi 3000 (không đụng chuỗi Sandbox 1000) rồi gọi cổng vip.
async function makeProdControl(deps, token2) {
  deps.apple.states['3000'] = {
    environment: 'Production',
    transaction: appleTransaction({ transactionId: '3001', originalTransactionId: '3000', signedDate: deps.nowMs(), expiresDate: deps.nowMs() + 29 * DAY, appAccountToken: token2 }),
    renewalInfo: null,
    status: 1,
  };
  assert.equal((await claim(deps, 'uid-2', 'apple', '3000')).vip, true);
  return requireEntitlement(deps, { uid: 'uid-2' });
}

test('upgradeChain_onlyNewestCounts', async () => {
  const { deps, token1 } = await world();
  const keyOf = (t) => googleKey(t);
  deps.google.subscriptions['tok-A'] = gsub(token1, { lineItems: [{ productId: 'vip_monthly', expiryTime: iso(T + 30 * DAY) }] });
  assert.equal((await claim(deps, 'uid-1', 'google', 'tok-A')).vip, true);
  deps.clock.advance(HOUR);
  deps.google.subscriptions['tok-B'] = gsub(token1, { linkedPurchaseToken: 'tok-A', lineItems: [{ productId: 'vip_monthly', expiryTime: iso(T + 7 * DAY) }] });
  assert.equal((await claim(deps, 'uid-1', 'google', 'tok-B')).vip, true);
  deps.clock.advance(HOUR);
  deps.google.subscriptions['tok-C'] = gsub(token1, { linkedPurchaseToken: 'tok-B', lineItems: [{ productId: 'vip_monthly', expiryTime: iso(T + 3 * DAY) }] });
  assert.equal((await claim(deps, 'uid-1', 'google', 'tok-C')).vip, true);
  // Chỉ bản mới nhất được tính (hạn 3 ngày), dù A và B còn hạn xa hơn.
  assert.equal(stored(deps, `purchases/${keyOf('tok-A')}`).supersededBy, keyOf('tok-B'));
  assert.equal(stored(deps, `purchases/${keyOf('tok-B')}`).supersededBy, keyOf('tok-C'));
  assert.equal(stored(deps, `purchases/${keyOf('tok-C')}`).supersededBy, null);
  let { res } = await entitlementOf(deps, 'uid-1');
  assert.equal(res.vip, true);
  assert.equal(res.expiresAtMs, T + 3 * DAY);
  // Phát lại claim bản cũ (store vẫn trả active): không sống lại, quyền vẫn theo bản mới nhất.
  deps.clock.advance(HOUR);
  await claim(deps, 'uid-1', 'google', 'tok-A');
  assert.equal(stored(deps, `purchases/${keyOf('tok-A')}`).supersededBy, keyOf('tok-B'));
  ({ res } = await entitlementOf(deps, 'uid-1'));
  assert.equal(res.expiresAtMs, T + 3 * DAY);
  // Kẻ khác không chen vào chuỗi nâng cấp.
  await assertDenied(claim(deps, 'uid-2', 'google', 'tok-A'), 'already-bound');
  await assertDenied(claim(deps, 'uid-2', 'google', 'tok-C'), 'already-bound');
  assert.equal((await entitlementOf(deps, 'uid-2')).res.vip, false);
});

// ---- kẻ trộm và cửa sổ legacy Google ----

test('thiefClaimsTokenBearingPurchaseBeforeOwner_alreadyBound', async () => {
  const { deps, token1 } = await world(legacyOpts()); // cửa sổ legacy ĐANG MỞ: vẫn không giúp được kẻ trộm
  deps.google.subscriptions['tok-V'] = gsub(token1, { startTime: iso(T - 100 * DAY) });
  const key = googleKey('tok-V');
  // Chủ thật chưa kịp claim; kẻ trộm (uid-9) có mã mua hàng của họ claim trước.
  await assertDenied(claim(deps, 'uid-9', 'google', 'tok-V'), 'already-bound');
  assert.equal(stored(deps, `purchases/${key}`), undefined);
  assert.equal(stored(deps, 'entitlementAccounts/uid-9'), undefined);
  assert.equal(stored(deps, 'entitlements/uid-9'), undefined);
  assert.equal(stored(deps, `${AUDIT_PREFIX}${key}_${Math.floor(deps.nowMs() / HOUR)}`).reason, 'already-bound');
  assert.deepEqual(auditDocs(deps, 'entitlementAudit/legacy_'), []);
  assert.equal((await entitlementOf(deps, 'uid-9')).res.vip, false);
  // Chủ thật claim sau đó thì được, và kẻ trộm thử lại vẫn bị chặn.
  assert.deepEqual(await claim(deps, 'uid-1', 'google', 'tok-V'), { vip: true, planId: 'monthly', state: 'active' });
  await assertDenied(claim(deps, 'uid-9', 'google', 'tok-V'), 'already-bound');
  assert.equal(stored(deps, `purchases/${key}`).uid, 'uid-1');
});

test('legacyWindowOpen_thiefWithUnknownGoogleToken_tokenRequired', async () => {
  const { deps } = await world(legacyOpts());
  const unknownToken = '33333333-3333-4333-8333-333333333333'; // chưa đăng ký cho ai
  deps.google.subscriptions['tok-U'] = gsub(unknownToken, { startTime: iso(T - 100 * DAY) });
  const key = googleKey('tok-U');
  // Giao dịch mang token lạ không bao giờ đi đường legacy dù tài khoản cũ và mua trước cutover.
  await assertDenied(claim(deps, 'uid-1', 'google', 'tok-U'), 'token-required');
  assert.equal(stored(deps, `purchases/${key}`), undefined);
  assert.deepEqual(auditDocs(deps, 'entitlementAudit/legacy_'), []);
  assert.equal(stored(deps, 'entitlementAccounts/uid-1').legacyClaims, undefined);
  assert.equal((await entitlementOf(deps, 'uid-1')).res.vip, false);
  // Đối chứng: cửa sổ thật sự đang mở, giao dịch không token cũ vẫn đi đường legacy.
  deps.google.subscriptions['tok-L'] = gsub(null, { startTime: iso(T - 100 * DAY) });
  assert.equal((await claim(deps, 'uid-1', 'google', 'tok-L')).vip, true);
  assert.equal(stored(deps, 'entitlementAccounts/uid-1').legacyClaims, 1);
});

test('legacyWindowOpen_thiefAccountCreatedAfterCutover_tokenRequired', async () => {
  const { deps } = await world(legacyOpts());
  deps.google.subscriptions['tok-L'] = gsub(null, { startTime: iso(T - 100 * DAY) });
  const key = googleKey('tok-L');
  // Tài khoản tạo SAU cutover (uid-new) không có bằng chứng sở hữu legacy: phải có token.
  await assertDenied(claim(deps, 'uid-new', 'google', 'tok-L'), 'token-required');
  assert.equal(stored(deps, `purchases/${key}`), undefined);
  assert.equal(stored(deps, 'entitlementAccounts/uid-new'), undefined);
  assert.deepEqual(auditDocs(deps, 'entitlementAudit/legacy_'), []);
  assert.equal((await entitlementOf(deps, 'uid-new')).res.vip, false);
  // Chủ thật (tài khoản cũ) vẫn claim được cùng giao dịch.
  assert.equal((await claim(deps, 'uid-1', 'google', 'tok-L')).vip, true);
  assert.equal(stored(deps, `purchases/${key}`).uid, 'uid-1');
  assert.equal(auditDocs(deps, 'entitlementAudit/legacy_').length, 1);
});

test('legacyWindowOpen_thiefWithFourthClaim_rejected', async () => {
  const { deps } = await world(legacyOpts());
  for (const t of ['tok-L1', 'tok-L2', 'tok-L3', 'tok-L4']) deps.google.subscriptions[t] = gsub(null, { startTime: iso(T - 100 * DAY) });
  for (const t of ['tok-L1', 'tok-L2', 'tok-L3']) assert.equal((await claim(deps, 'uid-1', 'google', t)).vip, true);
  assert.equal(stored(deps, 'entitlementAccounts/uid-1').legacyClaims, 3);
  // Lần thứ tư của cùng uid bị chặn (tối đa 3 ràng buộc legacy mỗi uid), không ghi gì ngoài audit từ chối.
  const before = withoutClaimAudit(deps);
  await assertDenied(claim(deps, 'uid-1', 'google', 'tok-L4'), 'legacy-window-closed');
  assert.deepEqual(withoutClaimAudit(deps), before);
  assert.equal(stored(deps, `purchases/${googleKey('tok-L4')}`), undefined);
  assert.equal(stored(deps, 'entitlementAccounts/uid-1').legacyClaims, 3);
  assert.equal(auditDocs(deps, 'entitlementAudit/legacy_').length, 3);
  assert.equal(stored(deps, `${AUDIT_PREFIX}${googleKey('tok-L4')}_${Math.floor(deps.nowMs() / HOUR)}`).reason, 'legacy-window-closed');
});

test('legacyRepurchaseAfterRebind_followsRebindOwner_withoutReopeningApp', async () => {
  const { deps } = await world(legacyOpts());
  deps.google.subscriptions['tok-L'] = gsub(null, { startTime: iso(T - 100 * DAY) });
  const keyL = googleKey('tok-L');
  const keyL2 = googleKey('tok-L2');
  // uid-1 nhận giao dịch legacy qua cửa sổ; rồi mất tài khoản, hỗ trợ rebind sang uid-2.
  assert.equal((await claim(deps, 'uid-1', 'google', 'tok-L')).vip, true);
  assert.equal((await rebindPurchase(deps, { key: keyL, toUid: 'uid-2', evidence: 'Đơn GPA.1234 đối chiếu Play Console', operator: 'ops-1' })).applied, true);
  assert.equal(stored(deps, `purchases/${keyL}`).uid, 'uid-2');
  assert.equal((await entitlementOf(deps, 'uid-1')).res.vip, false);
  // Cửa sổ legacy ĐÃ ĐÓNG (quá ngày mai), rồi người dùng nâng cấp: giao dịch mới không token, mua SAU cutover, nối L bằng linkedPurchaseToken.
  deps.clock.advance(2 * DAY);
  deps.google.subscriptions['tok-L2'] = gsub(null, {
    linkedPurchaseToken: 'tok-L', startTime: iso(deps.nowMs() - HOUR), lineItems: [{ productId: 'vip_monthly', expiryTime: iso(deps.nowMs() + 30 * DAY) }],
  });
  // Kẻ khác và chủ cũ đều không cướp được bản nâng cấp: chủ HIỆN TẠI của tiền nhiệm là uid-2.
  await assertDenied(claim(deps, 'uid-3', 'google', 'tok-L2'), 'already-bound');
  await assertDenied(claim(deps, 'uid-1', 'google', 'tok-L2'), 'already-bound');
  assert.equal(stored(deps, `purchases/${keyL2}`), undefined);
  // uid-2 (chủ sau rebind) nhận được mà không dùng lại cửa sổ legacy.
  assert.deepEqual(await claim(deps, 'uid-2', 'google', 'tok-L2'), { vip: true, planId: 'monthly', state: 'active' });
  assert.equal(stored(deps, `purchases/${keyL2}`).uid, 'uid-2');
  assert.equal(stored(deps, `purchases/${keyL}`).supersededBy, keyL2);
  assert.equal(stored(deps, 'entitlementAccounts/uid-2').legacyClaims, undefined);
  assert.equal(stored(deps, `entitlementAudit/legacy_${keyL2}`), undefined);
  assert.equal(stored(deps, `entitlementAudit/lineage_${keyL2}`).uid, 'uid-2');
  const { res } = await entitlementOf(deps, 'uid-2');
  assert.equal(res.vip, true);
  assert.equal(res.expiresAtMs, deps.nowMs() + 30 * DAY);
  // Đối chứng: cửa sổ legacy thật sự đã đóng với giao dịch không token khác.
  deps.google.subscriptions['tok-L9'] = gsub(null, { startTime: iso(T - 100 * DAY) });
  await assertDenied(claim(deps, 'uid-2', 'google', 'tok-L9'), 'legacy-window-closed');
});
