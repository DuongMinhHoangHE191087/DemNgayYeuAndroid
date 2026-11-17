'use strict';

// M-90: dòng dõi mua nâng cấp. purchase mới có predecessorLookupId (Google) thay thế tiền nhiệm trong CÙNG transaction và đi theo chủ
// HIỆN TẠI của tiền nhiệm; purchase của uid khác còn sống không bao giờ bị cướp; mặc định TỪ CHỐI, bị từ chối thì không ghi gì.
const test = require('node:test');
const assert = require('node:assert/strict');
const { applyStoreState } = require('../../src/entitlement/applyStoreState');
const { rebindPurchase } = require('../../src/entitlement/rebindPurchase');
const { releaseEntitlementBindings } = require('../../src/entitlement/release');
const { googleKey } = require('../../src/entitlement/normalizeGoogle');
const { makeDeps, unavailableError } = require('./depsBuilder');
const { BASE_NOW_MS: T, DAY, HOUR, makePurchase, makeStoredPurchase } = require('./builders');

const TOKEN = '11111111-1111-4111-8111-111111111111'; // chủ: uid-1
const TOKEN2 = '22222222-2222-4222-8222-222222222222'; // chủ: uid-2
const KEY_A = googleKey('tok-A');
const KEY_B = googleKey('tok-B');
const KEY_C = googleKey('tok-C');

const gp = (tok, o = {}) => makePurchase({
  store: 'google', key: googleKey(tok), lookupId: tok, productId: 'vip_monthly', acknowledged: false, latestTransactionId: null, ...o,
});
const seedOwner = async (deps, uid, token) => {
  await deps.db.collection('appAccountTokens').doc(token).set({ uid, createdAtMs: 0 });
  await deps.db.collection('entitlementAccounts').doc(uid).set({ token });
};
const claim = (deps, purchase, uid = 'uid-1', extra = {}) => applyStoreState(deps, { purchase, claimerUid: uid, dedupeKey: null, ...extra });
const notify = (deps, purchase, extra = {}) => applyStoreState(deps, { purchase, claimerUid: null, dedupeKey: null, ...extra });
const stored = (deps, key) => deps.db.read(`purchases/${key}`);
// uid-1 (token TOKEN) và uid-2 (token TOKEN2) đã đăng ký; A của uid-1 hết hạn sau 30 ngày.
async function setup(deps = makeDeps()) {
  await seedOwner(deps, 'uid-1', TOKEN);
  await seedOwner(deps, 'uid-2', TOKEN2);
  assert.equal((await claim(deps, gp('tok-A', { expiresAtMs: T + 30 * DAY }))).ok, true);
  deps.clock.advance(HOUR);
  return deps;
}
// Chủ cũ xoá tài khoản thật sự: tombstone dạng object {uidHash, atMs} do releaseEntitlementBindings ghi.
async function deleteOwner(deps, uid) {
  await deps.db.collection('accountDeletions').doc(uid).set({ state: 'done' });
  assert.equal((await releaseEntitlementBindings(deps, { uid })).released, true);
}
async function assertDenied(deps, run, reason) {
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  assert.deepEqual(await run(), { ok: false, reason });
  assert.deepEqual(deps.db.snapshot(), before, 'không được ghi gì');
  assert.equal(deps.db.writes, writes);
}
const legacyDeps = () => makeDeps({ legacyClaimUntilMs: T + DAY, accountTokenCutoverMs: T - DAY, authCreatedMs: async () => T - 100 * DAY });

test('upgrade_newPurchaseSupersedesPredecessor', async () => {
  const deps = await setup();
  const res = await claim(deps, gp('tok-B', { predecessorLookupId: 'tok-A', expiresAtMs: T + 7 * DAY, asOfMs: deps.nowMs() }));
  assert.equal(res.ok, true);
  assert.equal(res.uid, 'uid-1');
  assert.equal(stored(deps, KEY_A).supersededBy, KEY_B);
  assert.equal(stored(deps, KEY_A).uid, 'uid-1');
  assert.equal(stored(deps, KEY_B).supersededBy, null);
  assert.equal(stored(deps, KEY_B).uid, 'uid-1');
  assert.deepEqual(deps.db.read('entitlementAccounts/uid-1').purchaseKeys, [KEY_A, KEY_B]);
});

test('upgrade_predecessorLosesVipSameTransaction', async () => {
  const deps = await setup();
  const upgrade = gp('tok-B', { predecessorLookupId: 'tok-A', expiresAtMs: T + 7 * DAY, asOfMs: deps.nowMs() });
  // Commit hỏng: cả bản ghi mới lẫn dấu thay thế đều không được ghi (cùng một transaction).
  const before = deps.db.snapshot();
  deps.db.failNextCommit(unavailableError());
  await assert.rejects(() => claim(deps, upgrade), /unavailable/);
  assert.deepEqual(deps.db.snapshot(), before);
  assert.equal(stored(deps, KEY_A).supersededBy, null);
  const res = await claim(deps, upgrade);
  // A còn hạn xa hơn (30 ngày) mà không được tính nữa: quyền chỉ theo B (7 ngày).
  assert.equal(res.entitlement.vip, true);
  assert.equal(res.entitlement.expiresAtMs, T + 7 * DAY);
  assert.equal(deps.db.read('entitlements/uid-1').expiresAtMs, T + 7 * DAY);
});

test('predecessorUnknown_noEffect', async () => {
  const deps = await setup();
  const before = Object.keys(deps.db.snapshot());
  const res = await claim(deps, gp('tok-B', { predecessorLookupId: 'tok-unknown', asOfMs: deps.nowMs() }));
  assert.equal(res.ok, true);
  assert.equal(stored(deps, googleKey('tok-unknown')), undefined, 'không tạo bản ghi cho tiền nhiệm không tồn tại');
  assert.equal(stored(deps, KEY_A).supersededBy, null);
  assert.deepEqual(Object.keys(deps.db.snapshot()).filter((p) => !before.includes(p)), [`purchases/${KEY_B}`]);
});

test('predecessorBoundToOtherLiveUid_alreadyBound_noWrites', async () => {
  const deps = await setup();
  // uid-2 (token TOKEN2) cố nâng cấp "từ" purchase của uid-1: không cướp được.
  const hijack = gp('tok-B', { accountToken: TOKEN2, predecessorLookupId: 'tok-A', asOfMs: deps.nowMs() });
  await assertDenied(deps, () => claim(deps, hijack, 'uid-2', { dedupeKey: 'n1' }), 'already-bound');
  // B đã gắn cho uid-2 từ trước: thông báo mang tiền nhiệm của uid-1 cũng không được thay thế A.
  await deps.db.collection('purchases').doc(KEY_B).set({ ...makeStoredPurchase({ ...hijack, asOfMs: 0 }), uid: 'uid-2' });
  await assertDenied(deps, () => notify(deps, { ...hijack, asOfMs: deps.nowMs() }, { dedupeKey: 'n2' }), 'already-bound');
  assert.equal(stored(deps, KEY_A).supersededBy, null);
});

test('predecessorOrphanOrTombstone_stillSuperseded', async () => {
  // Orphan: uid null, không tombstone.
  const orphan = makeDeps();
  assert.equal((await notify(orphan, gp('tok-A'))).orphan, true);
  await seedOwner(orphan, 'uid-1', TOKEN);
  const r1 = await claim(orphan, gp('tok-B', { predecessorLookupId: 'tok-A', asOfMs: T + HOUR }));
  assert.equal(r1.ok, true);
  assert.equal(stored(orphan, KEY_A).supersededBy, KEY_B);
  assert.equal(stored(orphan, KEY_A).uid, null);
  // Tombstone dạng `true`: chỉ thông báo mới đi tiếp (orphan), tiền nhiệm vẫn bị thay.
  const tomb = makeDeps();
  await tomb.db.collection('purchases').doc(KEY_A).set({ ...makeStoredPurchase({ store: 'google', key: KEY_A, lookupId: 'tok-A' }), uid: null, tombstone: true, asOfMs: 0 });
  const r2 = await notify(tomb, gp('tok-B', { predecessorLookupId: 'tok-A' }));
  assert.equal(r2.ok, true);
  assert.equal(r2.orphan, true);
  assert.equal(stored(tomb, KEY_A).supersededBy, KEY_B);
  assert.equal(stored(tomb, KEY_A).tombstone, true, 'giữ tombstone');
});

test('appleNoPredecessor_noEffect', async () => {
  const deps = await setup();
  // Apple không có dòng dõi: predecessorLookupId (nếu lạc vào) không được thay thế purchase Google của ai.
  const res = await claim(deps, makePurchase({ predecessorLookupId: 'tok-A', asOfMs: deps.nowMs() }));
  assert.equal(res.ok, true);
  assert.equal(stored(deps, KEY_A).supersededBy, null);
  assert.equal(res.entitlement.expiresAtMs, T + 30 * DAY);
});

test('staleNewPurchase_doesNotSupersede', async () => {
  const deps = await setup();
  assert.equal((await claim(deps, gp('tok-B', { asOfMs: T + 10 * HOUR }))).ok, true);
  const before = deps.db.snapshot();
  const res = await claim(deps, gp('tok-B', { predecessorLookupId: 'tok-A', asOfMs: T + 5 * HOUR }));
  assert.equal(res.ignored, 'stale');
  assert.deepEqual(deps.db.snapshot(), before);
  assert.equal(stored(deps, KEY_A).supersededBy, null);
});

test('rejectedBind_noSupersede', async () => {
  const deps = await setup();
  await assertDenied(deps, () => claim(deps, gp('tok-B', { predecessorLookupId: 'tok-A', ownership: 'FAMILY_SHARED', asOfMs: deps.nowMs() })), 'family-shared-unsupported');
  assert.equal(stored(deps, KEY_A).supersededBy, null);
});

test('chainOfThree_onlyLastCounts', async () => {
  const deps = await setup();
  await claim(deps, gp('tok-B', { predecessorLookupId: 'tok-A', expiresAtMs: T + 20 * DAY, asOfMs: deps.nowMs() }));
  const res = await claim(deps, gp('tok-C', { predecessorLookupId: 'tok-B', expiresAtMs: T + 10 * DAY, asOfMs: deps.nowMs() }));
  assert.equal(stored(deps, KEY_A).supersededBy, KEY_B);
  assert.equal(stored(deps, KEY_B).supersededBy, KEY_C);
  assert.equal(stored(deps, KEY_C).supersededBy, null);
  assert.equal(res.entitlement.expiresAtMs, T + 10 * DAY);
});

test('lateNotificationOnSupersededPredecessor_doesNotRevive', async () => {
  const deps = await setup();
  await claim(deps, gp('tok-B', { predecessorLookupId: 'tok-A', expiresAtMs: T + 7 * DAY, asOfMs: deps.nowMs() }));
  deps.clock.advance(HOUR);
  const late = await notify(deps, gp('tok-A', { expiresAtMs: T + 30 * DAY, asOfMs: deps.nowMs() }), { dedupeKey: 'late-1' });
  assert.equal(late.applied, true);
  assert.equal(stored(deps, KEY_A).supersededBy, KEY_B);
  assert.equal(late.entitlement.expiresAtMs, T + 7 * DAY, 'A cũ không sống lại');
  assert.equal(deps.db.read('entitlements/uid-1').expiresAtMs, T + 7 * DAY);
});

test('legacyPredecessor_outOfAppRepurchase_rtdnBindsToPredecessorOwner_noReopen', async () => {
  const deps = legacyDeps();
  const legacyA = gp('tok-A', { accountToken: null, purchaseTimeMs: T - 30 * DAY });
  assert.equal((await claim(deps, legacyA)).ok, true);
  assert.equal(deps.db.read('entitlementAccounts/uid-1').legacyClaims, 1);
  deps.clock.advance(2 * DAY); // cửa sổ legacy đã đóng
  const res = await notify(deps, gp('tok-B', { accountToken: null, predecessorLookupId: 'tok-A', purchaseTimeMs: deps.nowMs(), asOfMs: deps.nowMs() }));
  assert.equal(res.ok, true);
  assert.equal(res.orphan, false);
  assert.equal(res.uid, 'uid-1');
  assert.equal(res.entitlement.vip, true);
  assert.equal(deps.db.read('entitlementAccounts/uid-1').legacyClaims, 1, 'không mở lại legacy');
  assert.equal(deps.db.read(`entitlementAudit/legacy_${KEY_B}`), undefined);
  assert.equal(deps.db.read(`entitlementAudit/lineage_${KEY_B}`).uid, 'uid-1');
  assert.equal(stored(deps, KEY_A).supersededBy, KEY_B);
});

test('reboundPredecessor_outOfAppRepurchase_rtdnBindsToNewOwner', async () => {
  const deps = await setup();
  const moved = await rebindPurchase(deps, { key: KEY_A, toUid: 'uid-2', evidence: 'Đơn GPA.1234', operator: 'ops-1' });
  assert.equal(moved.ok, true);
  deps.clock.advance(HOUR);
  // Token trong giao dịch vẫn là của uid-1 (cũ): vẫn theo chủ HIỆN TẠI của tiền nhiệm.
  const res = await notify(deps, gp('tok-B', { predecessorLookupId: 'tok-A', asOfMs: deps.nowMs() }));
  assert.equal(res.uid, 'uid-2');
  assert.equal(res.entitlement.vip, true);
  assert.equal(stored(deps, KEY_B).uid, 'uid-2');
  assert.equal(stored(deps, KEY_A).supersededBy, KEY_B);
  assert.equal(deps.db.read('entitlements/uid-1').vip, false);
  assert.equal(deps.db.read('entitlements/uid-2').vip, true);
});

test('predecessorOwnerDeletedTokenOwnerRegistered_predecessorWins', async () => {
  const deps = await setup();
  await deleteOwner(deps, 'uid-1');
  deps.clock.advance(HOUR);
  // Token của B thuộc uid-2 đang sống, nhưng tiền nhiệm đã xoá thì thắng: không gắn cho chủ token.
  const res = await notify(deps, gp('tok-B', { accountToken: TOKEN2, predecessorLookupId: 'tok-A', asOfMs: deps.nowMs() }));
  assert.equal(res.ok, true);
  assert.equal(res.orphan, true);
  assert.equal(stored(deps, KEY_B).uid, null);
  assert.equal(deps.db.read('entitlements/uid-2'), undefined);
});

test('predecessorTombstone_rtdnRepurchase_orphanNoGrant', async () => {
  const deps = await setup();
  await deleteOwner(deps, 'uid-1');
  deps.clock.advance(HOUR);
  const res = await notify(deps, gp('tok-B', { predecessorLookupId: 'tok-A', asOfMs: deps.nowMs() }), { dedupeKey: 'n1' });
  assert.deepEqual(res, { ok: true, applied: true, ignored: null, uid: null, orphan: true, entitlement: null });
  assert.equal(stored(deps, KEY_B).boundAtMs, null);
  assert.equal(stored(deps, KEY_A).supersededBy, KEY_B);
  assert.ok(stored(deps, KEY_A).tombstone, 'tombstone (object) còn nguyên');
  assert.equal(deps.db.read('entitlements/uid-1'), undefined);
});

test('predecessorTombstone_claimByOther_ownerDeleted', async () => {
  const deps = await setup();
  await deleteOwner(deps, 'uid-1');
  deps.clock.advance(HOUR);
  const attempt = gp('tok-B', { accountToken: TOKEN2, predecessorLookupId: 'tok-A', asOfMs: deps.nowMs() });
  await assertDenied(deps, () => claim(deps, attempt, 'uid-2', { dedupeKey: 'n1' }), 'owner-deleted');
});

test('rebindThenRepurchase_auditLineageKept', async () => {
  const deps = await setup();
  await rebindPurchase(deps, { key: KEY_A, toUid: 'uid-2', evidence: 'Đơn GPA.1234', operator: 'ops-1' });
  deps.clock.advance(HOUR);
  await notify(deps, gp('tok-B', { predecessorLookupId: 'tok-A', asOfMs: deps.nowMs() }));
  assert.deepEqual(deps.db.read(`entitlementAudit/lineage_${KEY_B}`), { key: KEY_B, predecessorKey: KEY_A, uid: 'uid-2', atMs: deps.nowMs() });
  const rebinds = Object.keys(deps.db.snapshot()).filter((p) => p.startsWith(`entitlementAudit/rebind_${KEY_A}_`));
  assert.equal(rebinds.length, 1, 'audit rebind còn nguyên');
  // Cập nhật sau của B không ghi đè lineage.
  deps.clock.advance(HOUR);
  await notify(deps, gp('tok-B', { predecessorLookupId: 'tok-A', asOfMs: deps.nowMs() }));
  assert.equal(deps.db.read(`entitlementAudit/lineage_${KEY_B}`).atMs, deps.nowMs() - HOUR);
});

test('predecessorLookupIdEmpty_ignoredNotThrown', async () => {
  const deps = await setup();
  // googleKey('') ném lỗi: chuỗi rỗng phải bị bỏ qua trước khi tính key, không làm hỏng transaction.
  const res = await claim(deps, gp('tok-B', { predecessorLookupId: '', asOfMs: deps.nowMs() }));
  assert.equal(res.ok, true);
  assert.equal(stored(deps, KEY_A).supersededBy, null);
});

test('predecessorIsSelf_neverSupersedesItself', async () => {
  const deps = await setup();
  // Gửi lại chính A mang predecessorLookupId = tok-A: A không được tự thay thế và vẫn giữ VIP.
  deps.clock.advance(HOUR);
  const res = await claim(deps, gp('tok-A', { predecessorLookupId: 'tok-A', expiresAtMs: T + 30 * DAY, asOfMs: deps.nowMs() }));
  assert.equal(res.ok, true);
  assert.equal(stored(deps, KEY_A).supersededBy, null);
  assert.equal(res.entitlement.vip, true);
  assert.equal(res.entitlement.expiresAtMs, T + 30 * DAY);
});

test('predecessorAlreadySuperseded_keepsFirstSuccessor', async () => {
  const deps = await setup();
  await claim(deps, gp('tok-B', { predecessorLookupId: 'tok-A', expiresAtMs: T + 20 * DAY, asOfMs: deps.nowMs() }));
  deps.clock.advance(HOUR);
  // C cũng khai A là tiền nhiệm: A đã thuộc B, dấu thay thế đầu tiên không bị ghi đè.
  const res = await claim(deps, gp('tok-C', { predecessorLookupId: 'tok-A', expiresAtMs: T + 10 * DAY, asOfMs: deps.nowMs() }));
  assert.equal(res.ok, true);
  assert.equal(stored(deps, KEY_A).supersededBy, KEY_B);
});