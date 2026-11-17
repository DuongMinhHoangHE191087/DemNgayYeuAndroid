'use strict';

// M-89: rebindPurchase là đường DUY NHẤT chuyển purchase đã ràng buộc/đã có tombstone sang uid khác; mặc định TỪ CHỐI, một transaction.
const test = require('node:test');
const assert = require('node:assert/strict');
const { rebindPurchase } = require('../../src/entitlement/rebindPurchase');
const { applyStoreState } = require('../../src/entitlement/applyStoreState');
const { releaseEntitlementBindings } = require('../../src/entitlement/release');
const { DEFAULT_POLICY } = require('../../src/entitlement/resolveEntitlement');
const { parseArgs } = require('../../scripts/rebind-purchase');
const { makeDeps } = require('./depsBuilder');
const { BASE_NOW_MS: T, makePurchase, makeStoredPurchase } = require('./builders');

const TOKEN = '11111111-1111-4111-8111-111111111111';
const PKEY = 'apple_1000';
const ARGS = { key: PKEY, toUid: 'uid-2', evidence: 'Đơn GPA.1234 đối chiếu Play Console', operator: 'ops-1' };

// uid-1 sở hữu purchase (qua applyStoreState như thật); uid-2 là tài khoản đích đã tồn tại.
async function setup(purchaseOver = {}, depsOver = {}) {
  const deps = makeDeps(depsOver);
  await deps.db.collection('appAccountTokens').doc(TOKEN).set({ uid: 'uid-1', createdAtMs: 0 });
  await deps.db.collection('entitlementAccounts').doc('uid-1').set({ token: TOKEN });
  await deps.db.collection('entitlementAccounts').doc('uid-2').set({});
  const res = await applyStoreState(deps, { purchase: makePurchase(purchaseOver), claimerUid: 'uid-1', dedupeKey: null });
  assert.equal(res.ok, true);
  deps.clock.advance(1000);
  return deps;
}

async function assertDenied(deps, args, reason) {
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  assert.deepEqual(await rebindPurchase(deps, args), { ok: false, reason });
  assert.deepEqual(deps.db.snapshot(), before, 'không được ghi gì');
  assert.equal(deps.db.writes, writes);
}

test('rebind_movesPurchaseToTargetAndGrants', async () => {
  const deps = await setup();
  const res = await rebindPurchase(deps, ARGS);
  assert.equal(res.ok, true);
  assert.equal(res.applied, true);
  assert.equal(res.fromUid, 'uid-1');
  assert.equal(res.toUid, 'uid-2');
  const p = deps.db.read(`purchases/${PKEY}`);
  assert.equal(p.uid, 'uid-2');
  assert.equal(p.boundAtMs, deps.nowMs());
  assert.deepEqual(deps.db.read('entitlementAccounts/uid-2'), { purchaseKeys: [PKEY] });
  const ent = deps.db.read('entitlements/uid-2');
  assert.equal(ent.vip, true);
  assert.equal(ent.planId, 'monthly');
  assert.equal(res.entitlement.vip, true);
  assert.equal(res.entitlement.planId, 'monthly');
});

test('rebind_previousOwnerLosesVip', async () => {
  const deps = await setup();
  assert.equal(deps.db.read('entitlements/uid-1').vip, true);
  await rebindPurchase(deps, ARGS);
  assert.deepEqual(deps.db.read('entitlementAccounts/uid-1'), { token: TOKEN, purchaseKeys: [] });
  assert.equal(deps.db.read('entitlements/uid-1').vip, false);
});

test('rebind_fromTombstone_clearsTombstone', async () => {
  const deps = await setup();
  // Chủ cũ đã xoá tài khoản: purchase mang tombstone, tài khoản biến mất.
  await deps.db.collection('purchases').doc(PKEY).set({ tombstone: true }, { merge: true });
  await deps.db.collection('entitlementAccounts').doc('uid-1').delete();
  const res = await rebindPurchase(deps, ARGS);
  assert.equal(res.ok, true);
  const p = deps.db.read(`purchases/${PKEY}`);
  assert.equal(p.uid, 'uid-2');
  assert.ok(!p.tombstone, 'tombstone phải được xoá');
  assert.equal(deps.db.read('entitlements/uid-2').vip, true);
  assert.equal(deps.db.read('entitlementAccounts/uid-1'), undefined, 'không dựng lại tài khoản đã xoá');
  // Sau khi gỡ tombstone, chủ mới cập nhật purchase bình thường (không còn owner-deleted).
  const again = await applyStoreState(deps, { purchase: makePurchase({ asOfMs: T + 5 }), claimerUid: 'uid-2', dedupeKey: null });
  assert.equal(again.ok, true);
});

test('rebind_afterRealRelease_objectTombstone_dryRunReportsAndApplyClears', async () => {
  const deps = await setup();
  // Chuỗi thật: chủ cũ xoá tài khoản, M-21 ghi tombstone dạng object { uidHash, atMs } (không phải `true`).
  await deps.db.collection('accountDeletions').doc('uid-1').set({ state: 'done' });
  await releaseEntitlementBindings(deps, { uid: 'uid-1' });
  const tomb = deps.db.read(`purchases/${PKEY}`);
  assert.equal(tomb.uid, null);
  assert.equal(typeof tomb.tombstone, 'object');
  const before = deps.db.snapshot();
  const plan = await rebindPurchase(deps, { ...ARGS, dryRun: true });
  assert.deepEqual(plan, { ok: true, applied: false, dryRun: true, fromUid: null, toUid: 'uid-2', tombstone: true });
  assert.deepEqual(deps.db.snapshot(), before, 'dry-run không ghi gì');
  const res = await rebindPurchase(deps, ARGS);
  assert.equal(res.applied, true);
  const p = deps.db.read(`purchases/${PKEY}`);
  assert.equal(p.uid, 'uid-2');
  assert.ok(!p.tombstone);
  assert.equal(deps.db.read('entitlements/uid-2').vip, true);
  assert.equal(deps.db.read('entitlementAccounts/uid-1'), undefined, 'không dựng lại tài khoản đã xoá');
  assert.equal(deps.db.read(`entitlementAudit/rebind_${PKEY}_${deps.nowMs()}`).fromUid, null);
});

test('rebind_requiresEvidenceAndOperator', async () => {
  const deps = await setup();
  for (const bad of [{ evidence: '' }, { evidence: '   ' }, { evidence: undefined }, { evidence: 42 },
    { operator: '' }, { operator: undefined }, { operator: null }, { toUid: '' }, { toUid: 'a/b' }, { key: undefined }]) {
    await assertDenied(deps, { ...ARGS, ...bad }, 'invalid-input');
  }
  assert.deepEqual(await rebindPurchase(deps), { ok: false, reason: 'invalid-input' });
});

test('rebind_targetAccountMustExist', async () => {
  const deps = await setup();
  await assertDenied(deps, { ...ARGS, toUid: 'uid-ghost' }, 'target-account-missing');
});

test('rebind_unknownKey_rejected', async () => {
  const deps = await setup();
  await assertDenied(deps, { ...ARGS, key: 'apple_9999' }, 'unknown-purchase');
});

test('rebind_sandboxPurchase_refused', async () => {
  const deps = await setup({ environment: 'Sandbox' });
  assert.equal(deps.db.read(`purchases/${PKEY}`).environment, 'Sandbox');
  await assertDenied(deps, ARGS, 'sandbox-refused');
});

test('rebind_onlyProductionEnvironment', async () => {
  const deps = await setup();
  // Thiếu hoặc lạ cũng bị từ chối, không chỉ đúng chữ 'Sandbox'.
  for (const environment of [null, 'Xcode', 'production', 'LocalTesting']) {
    await deps.db.collection('purchases').doc(PKEY).set({ environment }, { merge: true });
    await assertDenied(deps, ARGS, 'sandbox-refused');
  }
});

test('rebind_nonFiniteClock_denied', async () => {
  const deps = await setup();
  deps.clock.set(NaN);
  await assertDenied(deps, ARGS, 'invalid-input');
});

test('rebind_corruptOwnerUid_treatedAsUnowned', async () => {
  const deps = await setup();
  await deps.db.collection('purchases').doc(PKEY).set({ uid: 'a/b' }, { merge: true });
  const res = await rebindPurchase(deps, ARGS);
  assert.equal(res.applied, true);
  assert.equal(res.fromUid, null);
  assert.equal(deps.db.read(`purchases/${PKEY}`).uid, 'uid-2');
});

test('rebind_sameUidWithTombstone_clearsFlag_keyOnce_noRedundantWrites', async () => {
  // Hình dạng mâu thuẫn (còn uid nhưng có tombstone, dạng `true` hoặc object): vẫn xoá cờ cho chính uid đó.
  for (const tombstone of [true, { uidHash: 'h', atMs: T }]) {
    const deps = await setup();
    await deps.db.collection('purchases').doc(PKEY).set({ tombstone }, { merge: true });
    const writes = deps.db.writes;
    const res = await rebindPurchase(deps, { ...ARGS, toUid: 'uid-1' });
    assert.equal(res.applied, true);
    assert.equal(res.fromUid, 'uid-1');
    assert.ok(!deps.db.read(`purchases/${PKEY}`).tombstone, 'tombstone phải được xoá');
    assert.deepEqual(deps.db.read('entitlementAccounts/uid-1').purchaseKeys, [PKEY]);
    assert.equal(deps.db.writes - writes, 4, 'purchase, tài khoản, entitlement, audit: mỗi cái một lần');
  }
});

test('rebind_adminGrantsSurviveOnBothOwners', async () => {
  const deps = await setup();
  for (const uid of ['uid-1', 'uid-2']) await deps.db.collection('entitlements').doc(uid).set({ adminGrant: { untilMs: null } }, { merge: true });
  const res = await rebindPurchase(deps, ARGS);
  assert.equal(res.entitlement.planId, 'admin', 'đích vẫn tính adminGrant');
  assert.equal(deps.db.read('entitlements/uid-1').vip, true, 'chủ cũ còn VIP nhờ adminGrant');
  assert.equal(deps.db.read('entitlements/uid-1').planId, 'admin');
});

test('rebind_bindLimitsRechecked_tooManyActive', async () => {
  const deps = await setup({}, { policy: { ...DEFAULT_POLICY, maxUnfinished: 1 } });
  // uid-2 đã có một purchase chưa kết thúc khác: đạt trần maxUnfinished.
  await deps.db.collection('purchases').doc('apple_2000').set(makeStoredPurchase({ key: 'apple_2000', uid: 'uid-2' }));
  await deps.db.collection('entitlementAccounts').doc('uid-2').set({ purchaseKeys: ['apple_2000'] });
  await assertDenied(deps, ARGS, 'too-many-active');
});

test('rebind_writesAuditWithEvidence', async () => {
  const deps = await setup();
  const nowMs = deps.nowMs();
  await rebindPurchase(deps, { ...ARGS, evidence: `  ${ARGS.evidence}  `, operator: '  ops-1  ' });
  assert.deepEqual(deps.db.read(`entitlementAudit/rebind_${PKEY}_${nowMs}`), {
    key: PKEY, fromUid: 'uid-1', toUid: 'uid-2', evidence: ARGS.evidence, operator: 'ops-1', atMs: nowMs,
  });
});

test('rebind_singleTransaction_failNextCommitLeavesNoPartial', async () => {
  const deps = await setup();
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  deps.db.failNextCommit(new Error('commit-failed'));
  await assert.rejects(rebindPurchase(deps, ARGS), /commit-failed/);
  assert.deepEqual(deps.db.snapshot(), before);
  assert.equal(deps.db.writes, writes);
  // Lần sau (commit tốt) vẫn chuyển được: không để lại trạng thái dở.
  assert.equal((await rebindPurchase(deps, ARGS)).applied, true);
});

test('rebind_sameOwner_noop', async () => {
  const deps = await setup();
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  const res = await rebindPurchase(deps, { ...ARGS, toUid: 'uid-1' });
  assert.deepEqual(res, { ok: true, applied: false, ignored: 'same-owner', fromUid: 'uid-1', toUid: 'uid-1' });
  assert.deepEqual(deps.db.snapshot(), before);
  assert.equal(deps.db.writes, writes);
});

test('rebind_dryRun_noWrites', async () => {
  const deps = await setup();
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  const res = await rebindPurchase(deps, { ...ARGS, dryRun: true });
  assert.deepEqual(res, { ok: true, applied: false, dryRun: true, fromUid: 'uid-1', toUid: 'uid-2', tombstone: false });
  assert.deepEqual(deps.db.snapshot(), before);
  assert.equal(deps.db.writes, writes);
});

test('afterRebind_oldOwnerClaim_alreadyBound', async () => {
  const deps = await setup();
  await rebindPurchase(deps, ARGS);
  const before = deps.db.snapshot();
  const res = await applyStoreState(deps, { purchase: makePurchase({ asOfMs: T + 5 }), claimerUid: 'uid-1', dedupeKey: null });
  assert.deepEqual(res, { ok: false, reason: 'already-bound' });
  assert.deepEqual(deps.db.snapshot(), before);
});

test('afterRebind_newOwnerClaim_idempotent', async () => {
  const deps = await setup();
  await rebindPurchase(deps, ARGS);
  const res = await applyStoreState(deps, { purchase: makePurchase({ asOfMs: T + 5 }), claimerUid: 'uid-2', dedupeKey: null });
  assert.equal(res.ok, true);
  assert.equal(res.uid, 'uid-2');
  assert.equal(res.entitlement.vip, true);
  assert.deepEqual(deps.db.read('entitlementAccounts/uid-2'), { purchaseKeys: [PKEY] });
});

test('cli_parseArgs_requiresKeyAndToUid', () => {
  const ok = ['--key', 'k1', '--to-uid', 'u2', '--evidence', 'đơn 1', '--operator', 'ops'];
  assert.deepEqual(parseArgs(ok), { key: 'k1', toUid: 'u2', evidence: 'đơn 1', operator: 'ops', apply: false });
  for (const flag of ['--key', '--to-uid', '--evidence', '--operator']) {
    const at = ok.indexOf(flag);
    assert.throws(() => parseArgs(ok.filter((_, i) => i !== at && i !== at + 1)), new RegExp(`Thiếu ${flag}`));
  }
  assert.throws(() => parseArgs([...ok, '--bogus']), /Tham số lạ/);
  assert.throws(() => parseArgs(['--key', '--to-uid', 'u2']), /cần một giá trị/);
});

test('cli_defaultsToDryRun', () => {
  const base = ['--key', 'k1', '--to-uid', 'u2', '--evidence', 'e', '--operator', 'o'];
  assert.equal(parseArgs(base).apply, false);
});

test('cli_applyFlagParsed', () => {
  const base = ['--key', 'k1', '--to-uid', 'u2', '--evidence', 'e', '--operator', 'o'];
  assert.equal(parseArgs([...base, '--apply']).apply, true);
  assert.equal(parseArgs(['--apply', ...base]).apply, true);
});
