'use strict';

// M-91: ensureFresh tra lại store cho các purchase của uid khi dữ liệu lưu đã cũ, để bắt hoàn tiền / thông báo bị mất mà không cần webhook.
// Chống bypass: lỗi store không bao giờ ném ra ngoài, Google revoked không bao giờ tra lại, trạng thái cuối chỉ tra trong cửa sổ,
// và mọi lần tra đều bị giới hạn theo từng purchase (10 phút / 24 giờ).
const test = require('node:test');
const assert = require('node:assert/strict');
const { ensureFresh } = require('../../src/entitlement/ensureFresh');
const { resolveEntitlement } = require('../../src/entitlement/resolveEntitlement');
const { googleKey } = require('../../src/entitlement/normalizeGoogle');
const { makeDeps, fakeApple, fakeGoogle, unavailableError } = require('./depsBuilder');
const { BASE_NOW_MS: T, HOUR, DAY, makeStoredPurchase } = require('./builders');
const { appleTransaction } = require('./fixtures/apple');
const { googleSubscription, googleProduct } = require('./fixtures/google');

const MIN = 60000;
const UID = 'uid-1';
const GTOKEN = 'tok-1';
const GKEY = googleKey(GTOKEN);

// Purchase đã lưu, "tươi" mặc định (xác minh và tra 1 giờ trước, ack xong); ghi kèm vào purchaseKeys của uid.
async function seed(deps, over = {}) {
  const p = makeStoredPurchase({
    asOfMs: T - HOUR, verifiedAtMs: T - HOUR, lastCheckedAtMs: T - HOUR, ackState: 'done', ackAttempts: null, ackNextAtMs: null, ...over,
  });
  const account = deps.db.read(`entitlementAccounts/${p.uid}`);
  await deps.db.collection('purchases').doc(p.key).set(p);
  await deps.db.collection('entitlementAccounts').doc(p.uid).set({ purchaseKeys: [...(account?.purchaseKeys ?? []), p.key] });
  return p;
}
const googleSeed = (deps, over = {}) => seed(deps, {
  store: 'google', key: GKEY, lookupId: GTOKEN, productId: 'vip_monthly', acknowledged: true, latestTransactionId: null, ...over,
});
// Trả lời của Apple cho '1000': đăng ký còn hạn, ký ở thời điểm hiện tại của deps.
const appleState = (deps, tx = {}) => ({
  environment: 'Production',
  transaction: appleTransaction({ signedDate: deps.nowMs(), expiresDate: deps.nowMs() + 29 * DAY, ...tx }),
  renewalInfo: null,
  status: 1,
});
const withApple = (state) => makeDeps({ apple: fakeApple({ states: state ? { 1000: state } : {} }) });
const run = (deps, uid = UID) => ensureFresh(deps, { uid });
const stored = (deps, key = 'apple_1000') => deps.db.read(`purchases/${key}`);
const fetches = (deps) => deps.apple.calls.length;

test('fresh_noStoreCall', async () => {
  const deps = withApple(null);
  await seed(deps);
  await googleSeed(deps, { key: 'google_other', lookupId: 'tok-2' });
  const before = deps.db.snapshot();
  const res = await run(deps);
  assert.equal(fetches(deps), 0);
  assert.equal(deps.google.calls.length, 0);
  assert.deepEqual(deps.db.snapshot(), before, 'không ghi gì');
  assert.equal(res.purchases.length, 2);
});

test('olderThanReverifyAfter_refetches', async () => {
  const deps = withApple(null);
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  deps.apple.states[1000] = appleState(deps);
  await run(deps);
  assert.deepEqual(deps.apple.calls.map((c) => [c.method, c.args]), [['fetchState', ['1000']]]);
  assert.equal(stored(deps).verifiedAtMs, T);
  assert.equal(stored(deps).lastCheckedAtMs, T);
  // Vừa dưới 24 giờ thì chưa tra.
  const ok = withApple(null);
  await seed(ok, { verifiedAtMs: T - 24 * HOUR + MIN, lastCheckedAtMs: T - 24 * HOUR + MIN });
  await run(ok);
  assert.equal(fetches(ok), 0);
});

test('lifetimeActive_olderThan24h_refetches', async () => {
  const deps = makeDeps({ google: fakeGoogle({ products: { [GTOKEN]: googleProduct() } }) });
  await googleSeed(deps, { kind: 'lifetime', productId: 'vip_lifetime', expiresAtMs: null, verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  await run(deps);
  assert.deepEqual(deps.google.calls.map((c) => c.method), ['getProduct']); // lifetime -> product, không thử subscription
  assert.equal(stored(deps, GKEY).verifiedAtMs, T);
  // Subscription Google thì hỏi subscription.
  const sub = makeDeps({ google: fakeGoogle({ subscriptions: { [GTOKEN]: googleSubscription() } }) });
  await googleSeed(sub, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  await run(sub);
  assert.deepEqual(sub.google.calls.map((c) => c.method), ['getSubscription']);
});

test('nearExpiry_refetches', async () => {
  const deps = withApple(null);
  await seed(deps, { expiresAtMs: T + 5 * MIN });
  deps.apple.states[1000] = appleState(deps);
  await run(deps);
  assert.equal(fetches(deps), 1);
  assert.equal(stored(deps).expiresAtMs, T + 29 * DAY);
  // Còn 5 phút + 1ms thì chưa tra.
  const far = withApple(null);
  await seed(far, { expiresAtMs: T + 5 * MIN + 1 });
  await run(far);
  assert.equal(fetches(far), 0);
});

test('pastExpiryNonTerminal_refetches', async () => {
  const deps = withApple(null);
  await seed(deps, { status: 'active', expiresAtMs: T - HOUR });
  deps.apple.states[1000] = appleState(deps);
  await run(deps);
  assert.equal(fetches(deps), 1);
  assert.equal(stored(deps).status, 'active');
  assert.equal(stored(deps).expiresAtMs, T + 29 * DAY);
});

test('atMostOncePer10MinPerPurchase', async () => {
  const deps = withApple(null); // store không có giao dịch: verifiedAtMs vẫn cũ nên chỉ chặn bằng recheckGapMs
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  await run(deps);
  assert.equal(fetches(deps), 1);
  await run(deps);
  deps.clock.advance(10 * MIN - 1);
  await run(deps);
  assert.equal(fetches(deps), 1, 'chưa đủ 10 phút kể từ lần tra trước');
  deps.clock.advance(1);
  await run(deps);
  assert.equal(fetches(deps), 2);
});

test('googleRevoked_neverPolled_sticky', async () => {
  const deps = makeDeps({ google: fakeGoogle({ subscriptions: { [GTOKEN]: googleSubscription() } }) });
  await googleSeed(deps, { status: 'revoked', revokedAtMs: T - DAY, expiresAtMs: T + 10 * DAY, verifiedAtMs: T - 40 * DAY, lastCheckedAtMs: T - 40 * DAY });
  await run(deps);
  deps.clock.advance(5 * DAY);
  await run(deps);
  deps.clock.advance(60 * DAY);
  await run(deps);
  assert.equal(deps.google.calls.length, 0);
  assert.equal(stored(deps, GKEY).status, 'revoked');
  // revokedAtMs có giá trị dù status chưa kịp đổi cũng là thu hồi dính mãi.
  const flag = makeDeps();
  await googleSeed(flag, { status: 'active', revokedAtMs: T - DAY, verifiedAtMs: T - 40 * DAY, lastCheckedAtMs: T - 40 * DAY });
  await run(flag);
  assert.equal(flag.google.calls.length, 0);
});

test('googleExpired_notPolled', async () => {
  const deps = makeDeps();
  await googleSeed(deps, { status: 'expired', expiresAtMs: T - 2 * DAY, verifiedAtMs: T - 3 * DAY, lastCheckedAtMs: T - 3 * DAY });
  await run(deps);
  assert.equal(deps.google.calls.length, 0);
});

// Apple ở trạng thái cuối trong cửa sổ: store không trả giao dịch (notOurs) nên trạng thái giữ nguyên, chỉ lastCheckedAtMs đổi.
async function assertDailyRecheck(over) {
  const deps = withApple(null);
  await seed(deps, { lastCheckedAtMs: T - DAY - MIN, ...over });
  await run(deps);
  assert.equal(fetches(deps), 1);
  assert.equal(stored(deps).lastCheckedAtMs, T);
  deps.clock.advance(DAY - 1);
  await run(deps);
  assert.equal(fetches(deps), 1, 'chưa đủ 24 giờ');
  deps.clock.advance(1);
  await run(deps);
  assert.equal(fetches(deps), 2);
}

test('appleRevokedWithinWindow_refetchedAtMostOncePerDay', () => assertDailyRecheck({
  status: 'revoked', revokedAtMs: T - 2 * DAY, expiresAtMs: T - 5 * DAY, verifiedAtMs: T - 2 * DAY,
}));

test('appleExpiredWithinWindow_refetchedAtMostOncePerDay', () => assertDailyRecheck({
  status: 'expired', expiresAtMs: T - 3 * DAY, verifiedAtMs: T - 3 * DAY,
}));

test('appleTerminalBeyondWindow_notPolled', async () => {
  const deps = withApple(appleState(makeDeps()));
  await seed(deps, { status: 'expired', expiresAtMs: T - 30 * DAY - 1, verifiedAtMs: T - 31 * DAY, lastCheckedAtMs: T - 31 * DAY });
  await seed(deps, { key: 'apple_1001', lookupId: '1001', status: 'revoked', revokedAtMs: T - 40 * DAY, expiresAtMs: T - 45 * DAY, lastCheckedAtMs: T - 40 * DAY });
  await run(deps);
  assert.equal(fetches(deps), 0);
  // Đúng mép cửa sổ (30 ngày) thì còn được tra.
  const edge = withApple(null);
  await seed(edge, { status: 'expired', expiresAtMs: T - 30 * DAY, lastCheckedAtMs: T - 31 * DAY });
  await run(edge);
  assert.equal(fetches(edge), 1);
  // Cửa sổ tính từ max(expiresAtMs, revokedAtMs): revokedAtMs gần hơn thì vẫn trong cửa sổ.
  const rev = withApple(null);
  await seed(rev, { status: 'revoked', revokedAtMs: T - 10 * DAY, expiresAtMs: T - 60 * DAY, lastCheckedAtMs: T - 11 * DAY });
  await run(rev);
  assert.equal(fetches(rev), 1);
});

test('terminalFetchUnavailable_keepsStateNoThrow', async () => {
  const deps = withApple(null);
  await seed(deps, { status: 'expired', expiresAtMs: T - 3 * DAY, verifiedAtMs: T - 3 * DAY, lastCheckedAtMs: T - 2 * DAY });
  const before = stored(deps);
  deps.apple.fail('fetchState', unavailableError());
  const res = await run(deps);
  assert.deepEqual(stored(deps), { ...before, lastCheckedAtMs: T });
  assert.equal(res.purchases[0].status, 'expired');
  await run(deps); // đã ghi lastCheckedAtMs: không tra lại ngay
  assert.equal(fetches(deps), 1);
});

test('missedAppleRecovery_androidConsumer_regainsVip', async () => {
  // REFUND_REVERSED bị mất: dữ liệu lưu là revoked, Apple đã khôi phục. Chỉ Android mở app, getEntitlement tự tra Apple.
  const deps = withApple(null);
  await seed(deps, { status: 'revoked', revokedAtMs: T - 2 * DAY, revokedTransactionId: '2000', expiresAtMs: T + 20 * DAY, asOfMs: T - 2 * DAY, verifiedAtMs: T - 2 * DAY, lastCheckedAtMs: T - 2 * DAY });
  assert.equal(resolveEntitlement([stored(deps)], T, deps.policy, null).vip, false);
  deps.apple.states[1000] = appleState(deps, { expiresDate: T + 20 * DAY });
  const res = await run(deps);
  assert.equal(stored(deps).status, 'active');
  assert.equal(stored(deps).revokedAtMs, null);
  assert.equal(deps.db.read(`entitlements/${UID}`).vip, true);
  assert.equal(resolveEntitlement(res.purchases, T, deps.policy, res.adminGrant).vip, true);
});

test('missedAppleRenewalAfterExpiry_androidConsumer_regainsVip', async () => {
  // Gia hạn bị mất: dữ liệu lưu là expired từ 2 ngày trước, Apple thật ra đã gia hạn.
  const deps = withApple(null);
  await seed(deps, { status: 'expired', expiresAtMs: T - 2 * DAY, asOfMs: T - 2 * DAY, verifiedAtMs: T - 2 * DAY, lastCheckedAtMs: T - 2 * DAY });
  deps.apple.states[1000] = appleState(deps, { expiresDate: T + 28 * DAY });
  const res = await run(deps);
  assert.equal(stored(deps).status, 'active');
  assert.equal(stored(deps).expiresAtMs, T + 28 * DAY);
  assert.equal(deps.db.read(`entitlements/${UID}`).vip, true);
  assert.equal(resolveEntitlement(res.purchases, T, deps.policy, res.adminGrant).vip, true);
});

test('superseded_notPolled', async () => {
  const deps = withApple(appleState(makeDeps()));
  await seed(deps, { supersededBy: GKEY, verifiedAtMs: T - 40 * DAY, lastCheckedAtMs: T - 40 * DAY, expiresAtMs: T - DAY });
  await run(deps);
  assert.equal(fetches(deps), 0);
});

test('storeUnavailable_recordsLastChecked_keepsStoredState_noThrow', async () => {
  const deps = withApple(null);
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  const before = stored(deps);
  const writes = deps.db.writes;
  deps.apple.fail('fetchState', unavailableError());
  const res = await run(deps);
  assert.deepEqual(stored(deps), { ...before, lastCheckedAtMs: T });
  assert.equal(deps.db.writes, writes + 1, 'chỉ ghi lastCheckedAtMs');
  assert.equal(res.purchases[0].verifiedAtMs, T - 25 * HOUR);
  // Google cũng vậy, và lỗi lạ (không phải unavailable) cũng không ném ra ngoài.
  const g = makeDeps();
  await googleSeed(g, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  g.google.fail('getSubscription', new Error('boom'));
  await run(g);
  assert.equal(stored(g, GKEY).lastCheckedAtMs, T);
  assert.equal(stored(g, GKEY).status, 'active');
});

test('storeNotFound_keepsStoredState', async () => {
  const deps = withApple(null);
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  const before = stored(deps);
  await run(deps);
  assert.deepEqual(stored(deps), { ...before, lastCheckedAtMs: T });
  // Không tìm thấy quá maxStaleMs (96 giờ) thì tự thành stale, không VIP.
  const stale = withApple(null);
  await seed(stale, { verifiedAtMs: T - 97 * HOUR, lastCheckedAtMs: T - 97 * HOUR });
  const res = await run(stale);
  const ent = resolveEntitlement(res.purchases, T, stale.policy, res.adminGrant);
  assert.equal(ent.vip, false);
  assert.equal(ent.state, 'stale');
});

test('staleSnapshot_updatesLastChecked_rateLimited', async () => {
  // Ảnh chụp Apple không mới hơn bản đã lưu: applyStoreState bỏ qua (stale) nhưng vẫn phải bị giới hạn nhịp tra.
  const deps = withApple(null);
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR, asOfMs: T - HOUR });
  deps.apple.states[1000] = appleState(deps, { signedDate: T - 2 * HOUR });
  await run(deps);
  await run(deps);
  assert.equal(fetches(deps), 1);
  assert.equal(stored(deps).lastCheckedAtMs, T);
});

test('refundDetected_revokesThroughApply', async () => {
  const deps = withApple(null);
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  await run(deps);
  deps.apple.states[1000] = appleState(deps, { revocationDate: T - HOUR });
  deps.clock.advance(10 * MIN);
  const res = await run(deps);
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(stored(deps).revokedAtMs, T - HOUR);
  assert.equal(deps.db.read(`entitlements/${UID}`).vip, false);
  assert.equal(resolveEntitlement(res.purchases, deps.nowMs(), deps.policy, res.adminGrant).vip, false);
});

test('pendingAck_retriesAck', async () => {
  const deps = makeDeps({ google: fakeGoogle({ subscriptions: { [GTOKEN]: {} } }) });
  await googleSeed(deps, { acknowledged: false, ackState: 'pending', ackAttempts: 0, ackNextAtMs: null });
  const res = await run(deps);
  assert.deepEqual(deps.google.calls.map((c) => c.method), ['acknowledgeSubscription']); // purchase còn tươi: không tra store, chỉ ack
  assert.equal(stored(deps, GKEY).ackState, 'done');
  assert.equal(res.purchases[0].ackState, 'done');
  // Chưa tới lịch lùi thì không ack (không force).
  const wait = makeDeps({ google: fakeGoogle({ subscriptions: { [GTOKEN]: {} } }) });
  await googleSeed(wait, { acknowledged: false, ackState: 'pending', ackAttempts: 1, ackNextAtMs: T + MIN });
  await run(wait);
  assert.equal(wait.google.calls.length, 0);
});

test('refetchedUnackedGoogle_acksSameCall', async () => {
  const deps = makeDeps({
    google: fakeGoogle({ subscriptions: { [GTOKEN]: googleSubscription({ acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING' }) } }),
  });
  // Đang retry (chưa cần ack) rồi store báo active mà chưa ack: lần tra này đặt ackState pending và ack ngay trong cùng lượt.
  await googleSeed(deps, { status: 'retry', acknowledged: false, ackState: 'none', verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  await run(deps);
  assert.deepEqual(deps.google.calls.map((c) => c.method), ['getSubscription', 'acknowledgeSubscription']);
  assert.equal(stored(deps, GKEY).ackState, 'done');
});

test('oneFailureDoesNotSkipOthers', async () => {
  const deps = withApple(null);
  const stale = { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR };
  await seed(deps, stale);
  await seed(deps, { key: 'apple_1001', lookupId: '1001', ...stale });
  deps.apple.states[1001] = appleState(deps, { originalTransactionId: '1001', transactionId: '3000' });
  deps.apple.fail('fetchState', new Error('boom')); // lần gọi đầu (purchase đầu tiên) lỗi
  const res = await run(deps);
  assert.deepEqual(deps.apple.calls.map((c) => c.args[0]), ['1000', '1001']);
  assert.equal(stored(deps, 'apple_1000').lastCheckedAtMs, T);
  assert.equal(stored(deps, 'apple_1000').verifiedAtMs, T - 25 * HOUR);
  assert.equal(stored(deps, 'apple_1001').verifiedAtMs, T);
  assert.equal(res.purchases.length, 2);
});

test('capsAtMaxUnfinished', async () => {
  const deps = withApple(null);
  for (let i = 0; i < 12; i += 1) {
    await seed(deps, { key: `apple_${2000 + i}`, lookupId: `${2000 + i}`, verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  }
  await run(deps);
  assert.equal(fetches(deps), 10);
  assert.deepEqual(deps.apple.calls.map((c) => c.args[0]), Array.from({ length: 10 }, (_, i) => `${2000 + i}`), 'tuần tự theo thứ tự lưu');
});

test('returnsFreshState', async () => {
  const deps = withApple(null);
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  await deps.db.collection('entitlements').doc(UID).set({ adminGrant: { untilMs: null } });
  deps.apple.states[1000] = appleState(deps);
  const res = await run(deps);
  assert.deepEqual(Object.keys(res).sort(), ['account', 'adminGrant', 'purchases']);
  assert.deepEqual(res.account, { purchaseKeys: ['apple_1000'] });
  assert.deepEqual(res.purchases, [stored(deps)]);
  assert.equal(res.purchases[0].verifiedAtMs, T, 'đọc lại sau khi tra');
  assert.deepEqual(res.adminGrant, { untilMs: null });
});

test('noAccount_returnsEmptyNoStoreCall', async () => {
  const deps = withApple(null);
  assert.deepEqual(await run(deps), { account: null, purchases: [], adminGrant: null });
  assert.equal(fetches(deps), 0);
});

test('tombstonedPurchase_neverPolled_noWrites', async () => {
  const deps = withApple(null);
  await seed(deps, { tombstone: true, verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  deps.apple.states[1000] = appleState(deps);
  const before = deps.db.snapshot();
  await run(deps);
  assert.equal(fetches(deps), 0);
  assert.deepEqual(deps.db.snapshot(), before);
});

test('writeFailureOnOnePurchase_swallowedOthersStillProcessed', async () => {
  const deps = withApple(null);
  const stale = { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR };
  await seed(deps, stale);
  await seed(deps, { key: 'apple_1001', lookupId: '1001', ...stale });
  deps.apple.states[1001] = appleState(deps, { originalTransactionId: '1001', transactionId: '3000' });
  const real = deps.apple.fetchState.bind(deps.apple);
  // Tài liệu biến mất giữa chừng (xoá tài khoản): lần ghi lastCheckedAtMs sau đó ném NOT_FOUND, phải bị nuốt.
  deps.apple.fetchState = async (...args) => {
    if (args[0] === '1000') {
      await deps.db.collection('purchases').doc('apple_1000').delete();
      throw unavailableError();
    }
    return real(...args);
  };
  const res = await run(deps);
  assert.equal(stored(deps, 'apple_1001').verifiedAtMs, T);
  assert.deepEqual(res.purchases.map((p) => p.key), ['apple_1001']);
});

test('ackStoreWriteFailure_swallowedOthersStillProcessed', async () => {
  const deps = makeDeps({
    google: fakeGoogle({ subscriptions: { [GTOKEN]: {} } }),
    apple: fakeApple({ states: {} }),
  });
  await googleSeed(deps, { acknowledged: false, ackState: 'pending', ackAttempts: 0, ackNextAtMs: null });
  await seed(deps, { verifiedAtMs: T - 25 * HOUR, lastCheckedAtMs: T - 25 * HOUR });
  deps.apple.states[1000] = appleState(deps);
  // Google lỗi và tài liệu mất: ackIfPending không ghi được lịch lùi nên ném; ensureFresh vẫn phải đi tiếp.
  deps.google.acknowledgeSubscription = async () => {
    await deps.db.collection('purchases').doc(GKEY).delete();
    throw unavailableError();
  };
  const res = await run(deps);
  assert.equal(stored(deps).verifiedAtMs, T);
  assert.deepEqual(res.purchases.map((p) => p.key), ['apple_1000']);
});
