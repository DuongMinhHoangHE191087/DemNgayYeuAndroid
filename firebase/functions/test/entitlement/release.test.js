'use strict';

// M-21: releaseEntitlementBindings dọn ràng buộc purchase của tài khoản đã xoá, KHÔNG bao giờ tự giải phóng purchase hay token.
const test = require('node:test');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { releaseEntitlementBindings } = require('../../src/entitlement/release');
const { applyStoreState } = require('../../src/entitlement/applyStoreState');
const { makeDeps } = require('./depsBuilder');
const { BASE_NOW_MS: T, makePurchase, makeStoredPurchase } = require('./builders');

const TOKEN = '11111111-1111-4111-8111-111111111111';
const UID_HASH = createHash('sha256').update('uid-1').digest('hex');

const setDeletion = (deps, uid, state) => deps.db.collection('accountDeletions').doc(uid).set({ state });
const pkey = (i) => `apple_${i}`;

// Dựng tài khoản uid-1 với n purchase đã ràng buộc, token đã đăng ký, entitlements và một purchase của uid-2.
async function seed(deps, n = 2) {
  const keys = [];
  for (let i = 0; i < n; i += 1) {
    keys.push(pkey(i));
    await deps.db.collection('purchases').doc(pkey(i)).set(makeStoredPurchase({ key: pkey(i), lookupId: String(i), asOfMs: T }));
  }
  await deps.db.collection('entitlementAccounts').doc('uid-1').set({ token: TOKEN, purchaseKeys: keys });
  await deps.db.collection('appAccountTokens').doc(TOKEN).set({ uid: 'uid-1', createdAtMs: 0 });
  await deps.db.collection('entitlements').doc('uid-1').set({ vip: true });
  await deps.db.collection('purchases').doc('apple_other').set(makeStoredPurchase({ key: 'apple_other', lookupId: 'other', uid: 'uid-2' }));
}

// Đếm số transaction và (tuỳ chọn) làm commit của transaction thứ failAt thất bại.
function spyTransactions(deps, failAt = null) {
  const real = deps.db.runTransaction;
  const spy = { count: 0 };
  deps.db.runTransaction = (fn) => {
    spy.count += 1;
    if (spy.count === failAt) deps.db.failNextCommit(new Error('commit-failed'));
    return real(fn);
  };
  return spy;
}

// Dựng uid-1 sở hữu apple_1000 qua đường claim thật, rồi xoá tài khoản và chạy release.
async function claimThenRelease(deps) {
  await deps.db.collection('appAccountTokens').doc(TOKEN).set({ uid: 'uid-1', createdAtMs: 0 });
  await deps.db.collection('entitlementAccounts').doc('uid-1').set({ token: TOKEN });
  const res = await applyStoreState(deps, { purchase: makePurchase(), claimerUid: 'uid-1', dedupeKey: null });
  assert.equal(res.entitlement.vip, true);
  await setDeletion(deps, 'uid-1', 'done');
  await releaseEntitlementBindings(deps, { uid: 'uid-1' });
}

test('stateDone_tombstonesPurchasesAndMarksTokenDeleted', async () => {
  const deps = makeDeps();
  await seed(deps, 2);
  await setDeletion(deps, 'uid-1', 'done');
  const other = deps.db.read('purchases/apple_other');

  const res = await releaseEntitlementBindings(deps, { uid: 'uid-1' });
  assert.equal(res.released, true);
  assert.equal(res.purchases, 2);
  for (const k of [pkey(0), pkey(1)]) {
    const p = deps.db.read(`purchases/${k}`);
    assert.equal(p.uid, null);
    assert.deepEqual(p.tombstone, { uidHash: UID_HASH, atMs: T });
    assert.equal(p.lookupId, k.slice('apple_'.length));
    assert.equal(p.status, 'active');
    assert.equal(p.environment, 'Production');
  }
  assert.equal(deps.db.read('appAccountTokens/' + TOKEN).deleted, true); // đánh dấu, không xoá
  assert.equal(deps.db.read('entitlementAccounts/uid-1'), undefined);
  assert.equal(deps.db.read('entitlements/uid-1'), undefined);
  assert.deepEqual(deps.db.read('purchases/apple_other'), other, 'purchase của uid khác không bị đụng');
});

test('statePending_noop', async () => {
  // Chỉ 'done' mới được giải phóng: 'pending' (giá trị thật của accountDeletion.js) và mọi trạng thái lạ đều là noop.
  for (const state of ['pending', 'failed', 'running', 'DONE', '', null]) {
    const deps = makeDeps();
    await seed(deps);
    await setDeletion(deps, 'uid-1', state);
    const before = deps.db.snapshot();
    const writes = deps.db.writes;
    const res = await releaseEntitlementBindings(deps, { uid: 'uid-1' });
    assert.equal(res.released, false, String(state));
    assert.deepEqual(deps.db.snapshot(), before);
    assert.equal(deps.db.writes, writes);
  }
});

test('invalidUid_throwsBeforeAnyRead', async () => {
  const deps = makeDeps();
  await seed(deps);
  await setDeletion(deps, 'uid-1', 'done');
  const before = deps.db.snapshot();
  for (const bad of ['', 'a/b', '.', '..', '__x__', undefined, null, 42]) {
    await assert.rejects(() => releaseEntitlementBindings(deps, { uid: bad }), TypeError, String(bad));
  }
  assert.deepEqual(deps.db.snapshot(), before);
});

test('existingTombstoneKept_otherOwnerPurchaseUntouched', async () => {
  const deps = makeDeps();
  await seed(deps, 2);
  const old = { uidHash: 'old-hash', atMs: 1 };
  await deps.db.collection('purchases').doc(pkey(0)).set(makeStoredPurchase({ key: pkey(0), lookupId: '0', asOfMs: T, tombstone: old }));
  // apple_other thuộc uid-2 nhưng lỡ nằm trong purchaseKeys của uid-1 (dữ liệu hỏng): không được đụng.
  await deps.db.collection('entitlementAccounts').doc('uid-1').set({ token: TOKEN, purchaseKeys: [pkey(0), pkey(1), 'apple_other'] });
  await setDeletion(deps, 'uid-1', 'done');
  const other = deps.db.read('purchases/apple_other');
  await releaseEntitlementBindings(deps, { uid: 'uid-1' });
  assert.deepEqual(deps.db.read(`purchases/${pkey(0)}`).tombstone, old, 'tombstone cũ được giữ');
  assert.deepEqual(deps.db.read(`purchases/${pkey(1)}`).tombstone, { uidHash: UID_HASH, atMs: T });
  assert.deepEqual(deps.db.read('purchases/apple_other'), other);
});

test('tokens_allOwnedMarked_missingAccountTokenDocCreated_otherUidTokenUntouched', async () => {
  const deps = makeDeps();
  await seed(deps, 1);
  // Token thứ hai cũng mang uid-1 (không phải token của tài khoản): vẫn phải đánh dấu deleted.
  const T2 = '22222222-2222-4222-8222-222222222222';
  await deps.db.collection('appAccountTokens').doc(T2).set({ uid: 'uid-1', createdAtMs: 5 });
  await setDeletion(deps, 'uid-1', 'done');
  await releaseEntitlementBindings(deps, { uid: 'uid-1' });
  assert.equal(deps.db.read(`appAccountTokens/${T2}`).deleted, true);
  assert.equal(deps.db.read(`appAccountTokens/${T2}`).createdAtMs, 5, 'merge: giữ trường cũ');

  // Token của tài khoản chưa có tài liệu appAccountTokens: tạo bản deleted để không bao giờ cấp lại.
  const d2 = makeDeps();
  const T3 = '33333333-3333-4333-8333-333333333333';
  await d2.db.collection('entitlementAccounts').doc('uid-1').set({ token: T3, purchaseKeys: [] });
  await setDeletion(d2, 'uid-1', 'done');
  await releaseEntitlementBindings(d2, { uid: 'uid-1' });
  assert.deepEqual(d2.db.read(`appAccountTokens/${T3}`), { deleted: true });

  // Token ghi ở tài khoản nhưng thuộc uid khác: không đụng.
  const d3 = makeDeps();
  await d3.db.collection('appAccountTokens').doc(T3).set({ uid: 'uid-2', createdAtMs: 9 });
  await d3.db.collection('entitlementAccounts').doc('uid-1').set({ token: T3, purchaseKeys: [] });
  await setDeletion(d3, 'uid-1', 'done');
  await releaseEntitlementBindings(d3, { uid: 'uid-1' });
  assert.deepEqual(d3.db.read(`appAccountTokens/${T3}`), { uid: 'uid-2', createdAtMs: 9 });
});

test('purchaseKeysGrowDuringRelease_throwsAndKeepsAccount_retryTombstonesNewKey', async () => {
  const deps = makeDeps();
  await seed(deps, 2);
  await setDeletion(deps, 'uid-1', 'done');
  await deps.db.collection('purchases').doc('apple_new').set(makeStoredPurchase({ key: 'apple_new', lookupId: 'new' }));
  const real = deps.db.runTransaction;
  let n = 0;
  deps.db.runTransaction = async (fn) => {
    n += 1;
    // Ngay trước transaction xoá tài khoản (thứ 3: 1 chunk + token + xoá), một purchase mới được gắn vào tài khoản.
    if (n === 3) await deps.db.collection('entitlementAccounts').doc('uid-1').set({ token: TOKEN, purchaseKeys: [pkey(0), pkey(1), 'apple_new'] });
    return real(fn);
  };
  await assert.rejects(() => releaseEntitlementBindings(deps, { uid: 'uid-1' }), /purchaseKeys changed/);
  assert.notEqual(deps.db.read('entitlementAccounts/uid-1'), undefined, 'không xoá tài khoản khi còn khoá chưa xử lý');
  deps.db.runTransaction = real;
  await releaseEntitlementBindings(deps, { uid: 'uid-1' });
  assert.equal(deps.db.read('purchases/apple_new').uid, null);
  assert.ok(deps.db.read('purchases/apple_new').tombstone);
  assert.equal(deps.db.read('entitlementAccounts/uid-1'), undefined);
});

test('stateMissing_noop', async () => {
  const deps = makeDeps();
  await seed(deps);
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  const res = await releaseEntitlementBindings(deps, { uid: 'uid-1' });
  assert.equal(res.released, false);
  assert.deepEqual(deps.db.snapshot(), before);
  assert.equal(deps.db.writes, writes);
});

test('doneTwice_idempotent', async () => {
  const deps = makeDeps();
  await seed(deps);
  await setDeletion(deps, 'uid-1', 'done');
  await releaseEntitlementBindings(deps, { uid: 'uid-1' });
  const before = deps.db.snapshot();
  deps.clock.advance(5000);
  const again = await releaseEntitlementBindings(deps, { uid: 'uid-1' });
  assert.equal(again.released, true);
  assert.deepEqual(deps.db.snapshot(), before, 'lần hai không đổi gì, tombstone cũ giữ nguyên');
});

test('afterRelease_otherUidClaim_ownerDeleted', async () => {
  const deps = makeDeps();
  await claimThenRelease(deps);
  const after = deps.db.snapshot();
  deps.clock.advance(1000);
  // Cùng purchase, uid khác (hoặc chính uid cũ): bị chặn, không ghi gì.
  for (const claimerUid of ['uid-2', 'uid-1']) {
    const res = await applyStoreState(deps, { purchase: makePurchase({ asOfMs: T + 10 }), claimerUid, dedupeKey: null });
    assert.deepEqual(res, { ok: false, reason: 'owner-deleted' });
  }
  // Purchase khác mang token đã xoá: cũng bị chặn.
  const viaToken = await applyStoreState(deps, {
    purchase: makePurchase({ key: 'apple_2000', lookupId: '2000', asOfMs: T + 10 }), claimerUid: 'uid-2', dedupeKey: null,
  });
  assert.deepEqual(viaToken, { ok: false, reason: 'owner-deleted' });
  assert.deepEqual(deps.db.snapshot(), after);
});

test('afterRelease_sameTokenNotification_orphanNoGrant', async () => {
  const deps = makeDeps();
  await claimThenRelease(deps);
  const after = deps.db.snapshot();
  deps.clock.advance(1000);
  // Thông báo cho purchase đã tombstone: từ chối, không ghi.
  const same = await applyStoreState(deps, { purchase: makePurchase({ asOfMs: T + 10 }), claimerUid: null, dedupeKey: null });
  assert.deepEqual(same, { ok: false, reason: 'owner-deleted' });
  assert.deepEqual(deps.db.snapshot(), after);
  // Purchase mới cùng token: ghi nhận mồ côi, không cấp VIP cho ai.
  const fresh = await applyStoreState(deps, {
    purchase: makePurchase({ key: 'apple_2000', lookupId: '2000', asOfMs: T + 10 }), claimerUid: null, dedupeKey: null,
  });
  assert.equal(fresh.ok, true);
  assert.equal(fresh.orphan, true);
  assert.equal(fresh.entitlement, null);
  assert.equal(deps.db.read('purchases/apple_2000').uid, null);
  assert.equal(deps.db.read('entitlements/uid-1'), undefined);
  assert.equal(deps.db.read('appAccountTokens/' + TOKEN).deleted, true);
});

test('partialFailure_retryCompletes', async () => {
  // 250 purchase: transaction 1-2 là hai chunk, 3 là token, 4 là xoá tài khoản. Thất bại ở từng bước đều chạy lại được.
  for (const failAt of [1, 2, 3, 4]) {
    const deps = makeDeps();
    await seed(deps, 250);
    await setDeletion(deps, 'uid-1', 'done');
    spyTransactions(deps, failAt);
    await assert.rejects(() => releaseEntitlementBindings(deps, { uid: 'uid-1' }), /commit-failed/);
    assert.notEqual(deps.db.read('entitlementAccounts/uid-1'), undefined, 'tài khoản chỉ bị xoá sau cùng');
    assert.equal(deps.db.read('appAccountTokens/' + TOKEN).deleted, failAt > 3 ? true : undefined);

    deps.clock.advance(1000);
    const res = await releaseEntitlementBindings(deps, { uid: 'uid-1' });
    assert.equal(res.released, true);
    for (let i = 0; i < 250; i += 1) {
      const p = deps.db.read(`purchases/${pkey(i)}`);
      assert.equal(p.uid, null);
      // Chunk đã commit trước lỗi giữ tombstone cũ, phần còn lại mang thời điểm của lần chạy lại.
      const committedBefore = failAt > 2 || (failAt === 2 && i < 200);
      assert.deepEqual(p.tombstone, { uidHash: UID_HASH, atMs: committedBefore ? T : T + 1000 });
    }
    assert.equal(deps.db.read('appAccountTokens/' + TOKEN).deleted, true);
    assert.equal(deps.db.read('entitlementAccounts/uid-1'), undefined);
    assert.equal(deps.db.read('entitlements/uid-1'), undefined);
  }
});

test('manyPurchases_chunked', async () => {
  const deps = makeDeps();
  await seed(deps, 450);
  await setDeletion(deps, 'uid-1', 'done');
  const spy = spyTransactions(deps);
  await releaseEntitlementBindings(deps, { uid: 'uid-1' });
  assert.equal(spy.count, 3 + 1 + 1, '450 purchase = 3 chunk (tối đa 200) + token + xoá tài khoản');
  for (let i = 0; i < 450; i += 1) assert.deepEqual(deps.db.read(`purchases/${pkey(i)}`).tombstone, { uidHash: UID_HASH, atMs: T });
  assert.equal(deps.db.read('entitlementAccounts/uid-1'), undefined);
});
