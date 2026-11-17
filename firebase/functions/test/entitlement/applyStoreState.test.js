'use strict';

// M-15: applyStoreState ghi trạng thái store vào Firestore trong MỘT transaction, server là nguồn sự thật, mặc định TỪ CHỐI.
const test = require('node:test');
const assert = require('node:assert/strict');
const { applyStoreState, readUidState, writeEntitlement } = require('../../src/entitlement/applyStoreState');
const { normalizeApple } = require('../../src/entitlement/normalizeApple');
const { DEFAULT_POLICY } = require('../../src/entitlement/resolveEntitlement');
const { makeDeps, fakeApple, fakeGoogle, unavailableError, TEST_BUNDLE_ID } = require('./depsBuilder');
const { BASE_NOW_MS: T, DAY, HOUR, makePurchase, makeStoredPurchase } = require('./builders');
const { appleTransaction } = require('./fixtures/apple');

const TOKEN = '11111111-1111-4111-8111-111111111111';
const PKEY = 'apple_1000';

const googleP = (o = {}) => makePurchase({
  store: 'google', key: 'google_k1', lookupId: 'tok-1', productId: 'vip_monthly', acknowledged: false, latestTransactionId: null, ...o,
});
const appleP = (txOver, asOfMs, status = 1) => normalizeApple({
  transaction: appleTransaction({ signedDate: asOfMs, ...txOver }), renewalInfo: null, status, bundleId: TEST_BUNDLE_ID, asOfMs,
});

// Đăng ký chủ token như getAccountToken: appAccountTokens/{token} và entitlementAccounts/{uid}.token.
async function seedOwner(deps, uid, token = TOKEN) {
  await deps.db.collection('appAccountTokens').doc(token).set({ uid, createdAtMs: 0 });
  await deps.db.collection('entitlementAccounts').doc(uid).set({ token });
}
const claim = (deps, purchase, extra = {}) => applyStoreState(deps, { purchase, claimerUid: 'uid-1', dedupeKey: null, ...extra });
const notify = (deps, purchase, extra = {}) => applyStoreState(deps, { purchase, claimerUid: null, dedupeKey: null, ...extra });
const stored = (deps, key = PKEY) => deps.db.read(`purchases/${key}`);
const legacyDeps = (extra = {}) => makeDeps({ legacyClaimUntilMs: T + DAY, accountTokenCutoverMs: T - DAY, authCreatedMs: async () => T - 100 * DAY, ...extra });
const legacyP = (o = {}) => googleP({ accountToken: null, purchaseTimeMs: T - 30 * DAY, ...o });

async function assertDenied(deps, run, reason) {
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  const res = await run();
  assert.deepEqual(res, { ok: false, reason });
  assert.deepEqual(deps.db.snapshot(), before, 'không được ghi gì');
  assert.equal(deps.db.writes, writes);
}

test('firstClaim_bindsAndGrants', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const res = await claim(deps, makePurchase());
  assert.equal(res.ok, true);
  assert.equal(res.applied, true);
  assert.equal(res.ignored, null);
  assert.equal(res.uid, 'uid-1');
  assert.equal(res.orphan, false);
  assert.equal(res.entitlement.vip, true);
  assert.equal(res.entitlement.planId, 'monthly');
  const p = stored(deps);
  assert.equal(p.uid, 'uid-1');
  assert.equal(p.boundAtMs, T);
  assert.equal(p.firstSeenAtMs, T);
  assert.deepEqual(deps.db.read('entitlementAccounts/uid-1'), { token: TOKEN, purchaseKeys: [PKEY] });
  assert.equal(deps.db.read('entitlements/uid-1').vip, true);
});

test('replaySameEvent_idempotentNoExtraWrite', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  assert.equal((await claim(deps, makePurchase(), { dedupeKey: 'n1' })).applied, true);
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  const again = await claim(deps, makePurchase(), { dedupeKey: 'n1' });
  assert.deepEqual(again, { ok: true, applied: false, ignored: 'duplicate' });
  assert.deepEqual(deps.db.snapshot(), before);
  assert.equal(deps.db.writes, writes);
  const noKey = await claim(deps, makePurchase()); // cùng asOf, không dấu dedupe: bị coi là cũ
  assert.equal(noKey.ignored, 'stale');
  assert.equal(deps.db.writes, writes);
});

test('staleAsOf_ignored', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, makePurchase({ asOfMs: T + 10 }));
  const before = deps.db.snapshot();
  const res = await claim(deps, makePurchase({ asOfMs: T + 5, status: 'expired' }));
  assert.equal(res.ok, true);
  assert.equal(res.applied, false);
  assert.equal(res.ignored, 'stale');
  assert.equal(res.uid, 'uid-1');
  assert.equal(res.entitlement.vip, true);
  assert.deepEqual(deps.db.snapshot(), before);
});

test('refundThenLateRenewal_staysRevoked', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, googleP());
  deps.clock.advance(1000);
  const refund = await claim(deps, googleP({ status: 'revoked', revokedAtMs: T + 1000, asOfMs: T + 1000 }));
  assert.equal(refund.entitlement.vip, false);
  deps.clock.advance(1000);
  const late = await claim(deps, googleP({ status: 'active', revokedAtMs: null, asOfMs: T + 2000 }));
  assert.equal(late.applied, true);
  assert.equal(late.entitlement.vip, false);
  assert.equal(late.entitlement.state, 'revoked');
  assert.equal(deps.db.read('entitlements/uid-1').vip, false);
});

test('claimByOtherUid_alreadyBound_noWrites', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, makePurchase());
  await assertDenied(deps, () => applyStoreState(deps, { purchase: makePurchase({ asOfMs: T + 5 }), claimerUid: 'uid-2', dedupeKey: 'n9' }), 'already-bound');
});

test('rejectedBind_writesNoDedupeMarker', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const res = await applyStoreState(deps, { purchase: makePurchase(), claimerUid: 'uid-2', dedupeKey: 'n1' });
  assert.equal(res.ok, false);
  assert.equal(deps.db.read('notificationLog/n1'), undefined);
  // Quyền sở hữu được giải quyết sau đó thì cùng thông báo vẫn áp dụng được.
  const ok = await applyStoreState(deps, { purchase: makePurchase(), claimerUid: 'uid-1', dedupeKey: 'n1' });
  assert.equal(ok.applied, true);
  assert.ok(deps.db.read('notificationLog/n1'));
});

test('twoUidsRace_exactlyOneWins', async () => {
  const deps = legacyDeps();
  const results = await Promise.all(['uid-1', 'uid-2'].map((uid) => applyStoreState(deps, { purchase: legacyP(), claimerUid: uid, dedupeKey: null })));
  const winners = results.filter((r) => r.ok);
  assert.equal(winners.length, 1);
  assert.equal(results.find((r) => !r.ok).reason, 'already-bound');
  assert.equal(stored(deps, 'google_k1').uid, winners[0].uid);
  const loser = winners[0].uid === 'uid-1' ? 'uid-2' : 'uid-1';
  assert.equal(deps.db.read(`entitlements/${loser}`), undefined);
});

test('keepsAdminGrant', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const grant = { untilMs: null, grantedBy: 'admin-x' };
  await deps.db.collection('entitlements').doc('uid-1').set({ adminGrant: grant });
  const res = await claim(deps, makePurchase({ status: 'expired', expiresAtMs: T - DAY }));
  assert.equal(res.entitlement.vip, true);
  assert.equal(res.entitlement.source, 'admin');
  assert.deepEqual(deps.db.read('entitlements/uid-1').adminGrant, grant);
});

test('notificationWithoutOwner_recordedOrphanNoGrant', async () => {
  const deps = makeDeps();
  const res = await notify(deps, makePurchase(), { dedupeKey: 'n1' });
  assert.deepEqual(res, { ok: true, applied: true, ignored: null, uid: null, orphan: true, entitlement: null });
  const p = stored(deps);
  assert.equal(p.uid, null);
  assert.equal(p.boundAtMs, null);
  assert.equal(p.ackState, 'done'); // Apple luôn acknowledged
  assert.ok(deps.db.read('notificationLog/n1'));
  assert.deepEqual(Object.keys(deps.db.snapshot()).sort(), ['notificationLog/n1', `purchases/${PKEY}`]);
});

test('notificationWithRegisteredToken_bindsToTokenOwner', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const res = await notify(deps, makePurchase());
  assert.equal(res.uid, 'uid-1');
  assert.equal(res.orphan, false);
  assert.equal(res.entitlement.vip, true);
  assert.equal(stored(deps).boundAtMs, T);
});

test('orphanThenClaim_bindsToClaimer', async () => {
  const deps = makeDeps();
  await notify(deps, makePurchase());
  assert.equal(stored(deps).uid, null);
  await seedOwner(deps, 'uid-1');
  deps.clock.advance(HOUR);
  const res = await claim(deps, makePurchase({ asOfMs: T + HOUR }));
  assert.equal(res.applied, true);
  assert.equal(res.orphan, false);
  assert.equal(res.entitlement.vip, true);
  const p = stored(deps);
  assert.equal(p.uid, 'uid-1');
  assert.equal(p.boundAtMs, T + HOUR);
  assert.equal(p.firstSeenAtMs, T);
});

test('graceThenHold_losesVip', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const grace = await claim(deps, googleP({ status: 'grace', expiresAtMs: T + 3 * DAY }));
  assert.equal(grace.entitlement.vip, true);
  assert.equal(grace.entitlement.state, 'grace');
  deps.clock.advance(HOUR);
  const hold = await claim(deps, googleP({ status: 'retry', expiresAtMs: T + 3 * DAY, asOfMs: T + HOUR }));
  assert.equal(hold.entitlement.vip, false);
  assert.equal(hold.entitlement.state, 'retry');
});

test('holdThenRecovered_regainsVip', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const hold = await claim(deps, googleP({ status: 'retry' }));
  assert.equal(hold.entitlement.vip, false);
  deps.clock.advance(HOUR);
  const back = await claim(deps, googleP({ status: 'active', asOfMs: T + HOUR }));
  assert.equal(back.entitlement.vip, true);
  assert.equal(deps.db.read('entitlements/uid-1').vip, true);
});

test('delayedRtdn_staleAsOfIgnored', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, googleP({ asOfMs: T + 100 }));
  const before = deps.db.snapshot();
  const res = await notify(deps, googleP({ status: 'expired', asOfMs: T + 10 }), { dedupeKey: 'rtdn-1' });
  assert.equal(res.ignored, 'stale');
  assert.equal(res.entitlement.vip, true);
  assert.deepEqual(deps.db.snapshot(), before);
  assert.equal(stored(deps, 'google_k1').status, 'active');
});

test('dedupeCommitted_applyFails_retryStillApplies', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const before = deps.db.snapshot();
  deps.db.failNextCommit(unavailableError());
  await assert.rejects(() => claim(deps, makePurchase(), { dedupeKey: 'n1' }), /unavailable/);
  assert.deepEqual(deps.db.snapshot(), before, 'commit hỏng thì không được để lại dấu dedupe');
  const retry = await claim(deps, makePurchase(), { dedupeKey: 'n1' });
  assert.equal(retry.applied, true);
  assert.equal(retry.entitlement.vip, true);
});

test('concurrentDuplicates_exactlyOneApply', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const results = await Promise.all(Array.from({ length: 5 }, () => claim(deps, makePurchase(), { dedupeKey: 'n1' })));
  assert.equal(results.filter((r) => r.applied === true).length, 1);
  assert.equal(results.filter((r) => r.ignored === 'duplicate').length, 4);
  assert.deepEqual(deps.db.read('entitlementAccounts/uid-1').purchaseKeys, [PKEY]);
});

test('legacyBind_writesAuditAndCountsUidClaim', async () => {
  const deps = legacyDeps();
  const res = await claim(deps, legacyP());
  assert.equal(res.ok, true);
  assert.equal(res.entitlement.vip, true);
  const audit = deps.db.read('entitlementAudit/legacy_google_k1');
  assert.equal(audit.uid, 'uid-1');
  assert.equal(audit.key, 'google_k1');
  assert.equal(audit.atMs, T);
  assert.equal(deps.db.read('entitlementAccounts/uid-1').legacyClaims, 1);
  assert.equal(stored(deps, 'google_k1').boundAtMs, T);
  // Cập nhật sau của cùng uid không phải ràng buộc mới: không đếm thêm.
  deps.clock.advance(HOUR);
  const again = await claim(deps, legacyP({ asOfMs: T + HOUR }));
  assert.equal(again.applied, true);
  assert.equal(deps.db.read('entitlementAccounts/uid-1').legacyClaims, 1);
});

test('legacyBind_missingCutoverOrAuthCreation_tokenRequiredNoWrites', async () => {
  const variants = {
    noCutover: legacyDeps({ accountTokenCutoverMs: null }),
    authNull: legacyDeps({ authCreatedMs: async () => null }),
    authThrows: legacyDeps({ authCreatedMs: async () => { throw unavailableError(); } }),
    authSync: legacyDeps({ authCreatedMs: undefined }),
    createdAfterCutover: legacyDeps({ authCreatedMs: async () => T }),
  };
  for (const [name, deps] of Object.entries(variants)) {
    await assertDenied(deps, () => claim(deps, legacyP(), { dedupeKey: 'n1' }), 'token-required').catch((e) => { e.message = `${name}: ${e.message}`; throw e; });
  }
});

test('storesBoundAtAndAckPending', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, googleP());
  let p = stored(deps, 'google_k1');
  assert.equal(p.ackState, 'pending');
  assert.equal(p.boundAtMs, T);
  assert.equal(p.ackAttempts, 0);
  assert.equal(p.ackNextAtMs, null);
  await deps.db.collection('purchases').doc('google_k1').set({ ackAttempts: 2, ackNextAtMs: 123 }, { merge: true });
  deps.clock.advance(HOUR);
  await claim(deps, googleP({ asOfMs: T + HOUR }));
  p = stored(deps, 'google_k1');
  assert.equal(p.ackState, 'pending');
  assert.equal(p.ackAttempts, 2, 'giữ ackAttempts cũ');
  assert.equal(p.ackNextAtMs, 123, 'giữ ackNextAtMs cũ');
  assert.equal(p.boundAtMs, T, 'không ghi đè boundAtMs');
  assert.equal(p.firstSeenAtMs, T, 'không ghi đè firstSeenAtMs');
  deps.clock.advance(HOUR);
  await claim(deps, googleP({ asOfMs: T + 2 * HOUR, acknowledged: true }));
  assert.equal(stored(deps, 'google_k1').ackState, 'done');
  deps.clock.advance(HOUR);
  await claim(deps, googleP({ asOfMs: T + 3 * HOUR, acknowledged: false }));
  assert.equal(stored(deps, 'google_k1').ackState, 'done', 'đã done thì không quay lại pending');

  // Apple luôn acknowledged -> done; Google chưa ràng buộc (orphan) hoặc đã hết hiệu lực -> none.
  await claim(deps, makePurchase({ asOfMs: T + 3 * HOUR }));
  assert.equal(stored(deps).ackState, 'done');
  await notify(deps, googleP({ key: 'google_orphan', lookupId: 'tok-2', accountToken: 'ffffffff-ffff-4fff-8fff-ffffffffffff' }));
  assert.equal(stored(deps, 'google_orphan').ackState, 'none');
  await claim(deps, googleP({ key: 'google_exp', lookupId: 'tok-3', status: 'expired', expiresAtMs: T - DAY, asOfMs: T + 3 * HOUR }));
  assert.equal(stored(deps, 'google_exp').ackState, 'none');
});

test('googleRevoked_isSticky', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, googleP());
  deps.clock.advance(1000);
  await claim(deps, googleP({ status: 'revoked', revokedAtMs: T + 500, revokedTransactionId: 'order-1', asOfMs: T + 1000 }));
  deps.clock.advance(1000);
  await claim(deps, googleP({ status: 'active', revokedAtMs: null, revokedTransactionId: null, asOfMs: T + 2000 }));
  const p = stored(deps, 'google_k1');
  assert.equal(p.revokedAtMs, T + 500);
  assert.equal(p.revokedTransactionId, 'order-1');
  assert.equal(p.ackState, 'none', 'thu hồi rồi thì không chờ acknowledge');
});

test('appleRefundReversed_newerSignedSnapshotClearsRevocation', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, appleP({}, T));
  deps.clock.advance(10000);
  const refund = await claim(deps, appleP({ transactionId: '2001', revocationDate: T + 10000 }, T + 10000, 5));
  assert.equal(refund.entitlement.vip, false);
  assert.equal(stored(deps).revokedAtMs, T + 10000);
  deps.clock.advance(10000);
  const back = await claim(deps, appleP({ transactionId: '3000' }, T + 20000));
  assert.equal(back.applied, true);
  assert.equal(stored(deps).revokedAtMs, null);
  assert.equal(back.entitlement.vip, true);
});

test('activeFetchCompletesAfterRefund_staysRevoked', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, appleP({ transactionId: '2001', revocationDate: T + 10000 }, T + 10000, 5));
  deps.clock.advance(20000);
  const before = deps.db.snapshot();
  const res = await claim(deps, appleP({}, T)); // ảnh chụp "active" lấy trước REFUND, về sau
  assert.equal(res.ignored, 'stale');
  assert.equal(res.entitlement.vip, false);
  assert.equal(stored(deps).revokedAtMs, T + 10000);
  assert.deepEqual(deps.db.snapshot(), before);
});

test('appleSnapshotWithoutSignedDate_usesPreRequestTime_notArrivalTime', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const preRequestMs = deps.nowMs(); // trước khi gửi yêu cầu lấy ảnh chụp
  deps.clock.advance(20000);
  await notify(deps, appleP({ transactionId: '2001', revocationDate: T + 10000 }, T + 10000, 5));
  deps.clock.advance(5000); // ảnh chụp về sau, không có signedDate: asOf = preRequestMs, KHÔNG phải lúc này
  const snapshotWithoutSignedDate = appleP({ signedDate: undefined }, preRequestMs);
  const res = await claim(deps, snapshotWithoutSignedDate);
  assert.equal(res.ignored, 'stale');
  assert.equal(stored(deps).revokedAtMs, T + 10000);
  assert.equal(deps.db.read('entitlements/uid-1')?.vip ?? false, false);
});

test('appleRevokedLatestTransactionId_storedAndKeptUntilNewerSnapshot', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, appleP({}, T));
  assert.equal(stored(deps).latestTransactionId, '2000');
  assert.equal(stored(deps).revokedTransactionId, null);
  deps.clock.advance(10000);
  await claim(deps, appleP({ transactionId: '2001', revocationDate: T + 10000 }, T + 10000, 5));
  assert.equal(stored(deps).latestTransactionId, '2001');
  assert.equal(stored(deps).revokedTransactionId, '2001');
  await claim(deps, appleP({ transactionId: '2000' }, T + 5)); // cũ: không đổi gì
  assert.equal(stored(deps).latestTransactionId, '2001');
  assert.equal(stored(deps).revokedTransactionId, '2001');
  deps.clock.advance(10000);
  await claim(deps, appleP({ transactionId: '3000' }, T + 20000));
  assert.equal(stored(deps).latestTransactionId, '3000');
  assert.equal(stored(deps).revokedTransactionId, null);
});

test('sandboxFirstSeenAtNotReset', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const first = await claim(deps, makePurchase({ environment: 'Sandbox' }));
  assert.equal(first.entitlement.vip, true);
  deps.clock.advance(2 * HOUR);
  await claim(deps, makePurchase({ environment: 'Sandbox', asOfMs: T + 2 * HOUR }));
  assert.equal(stored(deps).firstSeenAtMs, T);
  assert.equal(stored(deps).boundAtMs, T);
  deps.clock.advance(23 * HOUR); // 25h kể từ lần đầu thấy
  const late = await claim(deps, makePurchase({ environment: 'Sandbox', asOfMs: deps.nowMs() }));
  assert.equal(stored(deps).firstSeenAtMs, T);
  assert.equal(late.entitlement.vip, false);
});

test('verifiedAtMs_isMinOfNowAndAsOf', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, makePurchase({ asOfMs: T - HOUR }));
  assert.equal(stored(deps).verifiedAtMs, T - HOUR);
  assert.equal(stored(deps).lastCheckedAtMs, T);
  deps.clock.advance(1000);
  await claim(deps, makePurchase({ asOfMs: T + 9 * HOUR })); // asOf ở tương lai (lệch đồng hồ)
  assert.equal(stored(deps).verifiedAtMs, T + 1000);
  assert.equal(stored(deps).lastCheckedAtMs, T + 1000);
});

test('readUidState_returnsAccountPurchasesAndAdminGrant', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, makePurchase());
  const grant = { untilMs: T + DAY };
  await deps.db.collection('entitlements').doc('uid-1').set({ adminGrant: grant }, { merge: true });
  const state = await deps.db.runTransaction((tx) => readUidState(tx, deps, 'uid-1'));
  assert.equal(state.account.token, TOKEN);
  assert.deepEqual(state.account.purchaseKeys, [PKEY]);
  assert.deepEqual(state.purchases.map((p) => p.key), [PKEY]);
  assert.deepEqual(state.adminGrant, grant);
  const none = await deps.db.runTransaction((tx) => readUidState(tx, deps, 'uid-9'));
  assert.deepEqual(none, { account: null, purchases: [], adminGrant: null });
});

test('writeEntitlement_mergesWithoutTouchingAdminGrant', async () => {
  const deps = makeDeps();
  const grant = { untilMs: T - DAY };
  await deps.db.collection('entitlements').doc('uid-9').set({ adminGrant: grant });
  const ent = await deps.db.runTransaction(async (tx) => writeEntitlement(tx, deps, { uid: 'uid-9', purchases: [makeStoredPurchase({ uid: 'uid-9' })], adminGrant: grant }));
  assert.equal(ent.vip, true);
  const doc = deps.db.read('entitlements/uid-9');
  assert.equal(doc.vip, true);
  assert.deepEqual(doc.adminGrant, grant);
});

// ---- bảo mật: mặc định từ chối ----

test('invalidInput_deniedNoWrites', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  const thrower = new Proxy({}, { get() { throw new Error('boom'); } });
  const cases = {
    nullArgs: () => applyStoreState(deps, null),
    nullPurchase: () => claim(deps, null),
    notOurs: () => claim(deps, { notOurs: true, reason: 'malformed-transaction' }),
    throwingPurchase: () => claim(deps, thrower),
    slashKey: () => claim(deps, makePurchase({ key: 'a/b' })),
    emptyKey: () => claim(deps, makePurchase({ key: '' })),
    nanAsOf: () => claim(deps, makePurchase({ asOfMs: NaN })),
    unknownStore: () => claim(deps, makePurchase({ store: 'steam' })),
    undefinedClaimer: () => applyStoreState(deps, { purchase: makePurchase(), claimerUid: undefined, dedupeKey: null }),
    slashClaimer: () => applyStoreState(deps, { purchase: makePurchase(), claimerUid: 'a/b', dedupeKey: null }),
    slashDedupe: () => claim(deps, makePurchase(), { dedupeKey: 'x/y' }),
  };
  for (const [name, run] of Object.entries(cases)) {
    await assertDenied(deps, run, 'invalid-input').catch((e) => { e.message = `${name}: ${e.message}`; throw e; });
  }
});

test('familyShared_deniedNoWrites', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await assertDenied(deps, () => claim(deps, makePurchase({ ownership: 'FAMILY_SHARED' }), { dedupeKey: 'n1' }), 'family-shared-unsupported');
});

test('tombstonedPurchase_neverRebound_noWrites', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await deps.db.collection('purchases').doc(PKEY).set({ ...makeStoredPurchase(), tombstone: true, asOfMs: 0 });
  await assertDenied(deps, () => claim(deps, makePurchase()), 'owner-deleted');
  await assertDenied(deps, () => notify(deps, makePurchase()), 'owner-deleted');
});

test('bindLimits_tooManyActive_noWrites', async () => {
  const deps = makeDeps({ policy: { ...DEFAULT_POLICY, maxUnfinished: 1 } });
  await seedOwner(deps, 'uid-1');
  assert.equal((await claim(deps, makePurchase())).ok, true);
  await assertDenied(deps, () => claim(deps, googleP()), 'too-many-active');
});

test('notificationOverLimit_recordedOrphanNoGrant', async () => {
  const deps = makeDeps({ policy: { ...DEFAULT_POLICY, maxUnfinished: 1 } });
  await seedOwner(deps, 'uid-1');
  assert.equal((await claim(deps, makePurchase())).ok, true);
  const res = await notify(deps, googleP(), { dedupeKey: 'n1' });
  assert.deepEqual(res, { ok: true, applied: true, ignored: null, uid: null, orphan: true, entitlement: null });
  assert.equal(stored(deps, 'google_k1').uid, null);
  assert.deepEqual(deps.db.read('entitlementAccounts/uid-1').purchaseKeys, [PKEY]);
});

test('purchaseKeysPointingAtForeignPurchase_notCounted', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, makePurchase());
  await deps.db.collection('entitlementAccounts').doc('uid-3').set({ token: 'x', purchaseKeys: [PKEY] });
  const state = await deps.db.runTransaction((tx) => readUidState(tx, deps, 'uid-3'));
  assert.deepEqual(state.purchases, []);
});

test('storedFields_whitelisted_noInjectedOwnerOrTombstone', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await claim(deps, makePurchase({ uid: 'evil', boundAtMs: 1, supersededBy: 'x', tombstone: true, adminGrant: { untilMs: null } }));
  const p = stored(deps);
  assert.equal(p.uid, 'uid-1');
  assert.equal(p.boundAtMs, T);
  assert.equal(p.supersededBy, null);
  assert.equal(p.tombstone, undefined);
  assert.equal(p.adminGrant, undefined);
  for (const [k, v] of Object.entries(p)) assert.notEqual(v, undefined, k);
});

test('fakeGates_recordCallsFailAndAcknowledge', async () => {
  const apple = fakeApple({ states: { 1: { ok: 1 } } });
  assert.deepEqual(await apple.fetchState('1'), { ok: 1 });
  assert.equal(await apple.fetchState('2'), null);
  apple.fail('fetchState', unavailableError());
  await assert.rejects(() => apple.fetchState('1'), { code: 'unavailable' });
  assert.deepEqual(await apple.fetchState('1'), { ok: 1 });
  assert.equal(apple.calls.length, 4);
  const google = fakeGoogle({ subscriptions: { t: { acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING' } } });
  await google.acknowledgeSubscription({ productId: 'vip_monthly', purchaseToken: 't' });
  assert.equal((await google.getSubscription('t')).acknowledgementState, 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED');
  assert.equal(await google.getProduct('t'), null);
});

test('storedOwnerWithUnsafeUid_notification_deniedNoWrites', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-1');
  await deps.db.collection('purchases').doc(PKEY).set({ ...makeStoredPurchase(), uid: 'a/b', asOfMs: 0 });
  await assertDenied(deps, () => notify(deps, makePurchase({ asOfMs: T }), { dedupeKey: 'n1' }), 'invalid-input');
});

test('notificationStoredOwnerBeatsTokenOwner_appliesToStoredOwner', async () => {
  const deps = makeDeps();
  await seedOwner(deps, 'uid-2'); // token đăng ký cho uid-2, nhưng purchase đã gắn cho uid-1
  await deps.db.collection('entitlementAccounts').doc('uid-1').set({ token: 'other', purchaseKeys: [PKEY] });
  await deps.db.collection('purchases').doc(PKEY).set({ ...makeStoredPurchase(), uid: 'uid-1', asOfMs: 0, boundAtMs: T - DAY, firstSeenAtMs: T - DAY });
  const res = await notify(deps, makePurchase({ asOfMs: T }));
  assert.equal(res.ok, true);
  assert.equal(res.uid, 'uid-1');
  assert.equal(res.entitlement.vip, true);
  assert.equal(deps.db.read('entitlements/uid-2'), undefined);
  assert.equal(stored(deps).boundAtMs, T - DAY);
});

test('legacyBind_uidAtClaimCap_windowClosed', async () => {
  const capped = legacyDeps();
  await capped.db.collection('entitlementAccounts').doc('uid-1').set({ legacyClaims: 3 });
  await assertDenied(capped, () => claim(capped, legacyP()), 'legacy-window-closed');
  const room = legacyDeps();
  await room.db.collection('entitlementAccounts').doc('uid-1').set({ legacyClaims: 2 });
  assert.equal((await claim(room, legacyP())).ok, true);
  assert.equal(room.db.read('entitlementAccounts/uid-1').legacyClaims, 3);
});

test('deletedAccountToken_claimDenied_notificationOrphan', async () => {
  const deps = makeDeps();
  await deps.db.collection('appAccountTokens').doc(TOKEN).set({ uid: 'uid-1', createdAtMs: 0, deleted: true });
  await assertDenied(deps, () => claim(deps, makePurchase(), { dedupeKey: 'n1' }), 'owner-deleted');
  const res = await notify(deps, makePurchase(), { dedupeKey: 'n2' });
  assert.equal(res.orphan, true);
  assert.equal(stored(deps).uid, null);
  assert.equal(deps.db.read('entitlements/uid-1'), undefined);
});
