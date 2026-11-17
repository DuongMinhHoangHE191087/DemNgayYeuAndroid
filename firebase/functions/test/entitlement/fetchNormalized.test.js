'use strict';

// M-100: fetchNormalized hỏi store rồi chuẩn hoá; asOfMs KHÔNG BAO GIỜ là lúc phản hồi về (chống phục hồi VIP sau REFUND).
const test = require('node:test');
const assert = require('node:assert/strict');
const { fetchNormalized } = require('../../src/entitlement/fetchNormalized');
const { applyStoreState } = require('../../src/entitlement/applyStoreState');
const { normalizeApple } = require('../../src/entitlement/normalizeApple');
const { normalizeGoogle, googleKey } = require('../../src/entitlement/normalizeGoogle');
const { makeDeps, fakeApple, fakeGoogle, unavailableError, TEST_BUNDLE_ID } = require('./depsBuilder');
const { BASE_NOW_MS: T } = require('./builders');
const { appleTransaction, appleRenewalInfo } = require('./fixtures/apple');
const { googleSubscription, googleProduct } = require('./fixtures/google');

const TOKEN = '11111111-1111-4111-8111-111111111111';
const GTOKEN = 'google-token-1';
const TEN_MIN = 10 * 60000;

const appleState = (txOver = {}, extra = {}) => ({ environment: 'Production', transaction: appleTransaction(txOver), renewalInfo: null, status: 1, ...extra });
const appleDeps = (state) => makeDeps({ apple: fakeApple({ states: { 1000: state } }) });
const apple = (deps, extra = {}) => fetchNormalized(deps, { store: 'apple', id: '1000', ...extra });
const googleDeps = (g) => makeDeps({ google: fakeGoogle(g) });
const google = (deps, kind) => fetchNormalized(deps, { store: 'google', id: GTOKEN, kind });
const methods = (deps) => deps.google.calls.map((c) => c.method);

// Cổng làm store trả lời chậm 10 phút (đồng hồ chạy tiếp trước khi phản hồi về).
const slow = (deps, gate, method) => {
  const orig = gate[method];
  gate[method] = async (...args) => {
    const res = await orig(...args);
    deps.clock.advance(TEN_MIN);
    return res;
  };
};

test('apple_found_normalized', async () => {
  const deps = appleDeps(appleState({ signedDate: T }));
  const res = await apple(deps);
  const expected = normalizeApple({ ...appleState({ signedDate: T }), bundleId: TEST_BUNDLE_ID, asOfMs: T });
  assert.deepEqual(res, { purchase: expected });
  assert.equal(res.purchase.status, 'active');
  assert.deepEqual(deps.apple.calls.map((c) => c.method), ['fetchState']);
});

test('apple_notFound_notOurs', async () => {
  assert.deepEqual(await apple(makeDeps()), { notOurs: true, reason: 'not-found' });
});

test('apple_unavailable_rethrown', async () => {
  const deps = appleDeps(appleState());
  const err = unavailableError();
  deps.apple.fail('fetchState', err);
  await assert.rejects(apple(deps), (e) => e === err);
});

test('apple_bundleMismatch_notOurs', async () => {
  const res = await apple(appleDeps(appleState({ bundleId: 'com.other.app' })));
  assert.deepEqual(res, { notOurs: true, reason: 'bundle-mismatch' });
});

test('google_subscriptionFound', async () => {
  const subscription = googleSubscription();
  const deps = googleDeps({ subscriptions: { [GTOKEN]: subscription } });
  const res = await google(deps);
  assert.deepEqual(res, { purchase: normalizeGoogle({ purchaseToken: GTOKEN, subscription, asOfMs: T }) });
  assert.equal(res.purchase.key, googleKey(GTOKEN));
  assert.equal(res.purchase.kind, 'subscription');
  assert.deepEqual(methods(deps), ['getSubscription']);
});

test('google_productFound', async () => {
  const product = googleProduct();
  const deps = googleDeps({ products: { [GTOKEN]: product } });
  const res = await google(deps);
  assert.deepEqual(res, { purchase: normalizeGoogle({ purchaseToken: GTOKEN, product, asOfMs: T }) });
  assert.equal(res.purchase.kind, 'lifetime');
  assert.deepEqual(methods(deps), ['getSubscription', 'getProduct']);
});

test('google_bothNull_notOurs', async () => {
  assert.deepEqual(await google(makeDeps()), { notOurs: true, reason: 'not-found' });
});

test('subscriptionsUnavailable_productFound_usesProduct', async () => {
  const deps = googleDeps({ products: { [GTOKEN]: googleProduct() } });
  deps.google.fail('getSubscription', unavailableError());
  const res = await google(deps);
  assert.equal(res.purchase.kind, 'lifetime');
  assert.deepEqual(methods(deps), ['getSubscription', 'getProduct']);
});

test('subscriptionsUnavailable_productNull_throwsUnavailable', async () => {
  const deps = makeDeps();
  const err = unavailableError();
  deps.google.fail('getSubscription', err);
  await assert.rejects(google(deps), (e) => e === err);
  assert.deepEqual(methods(deps), ['getSubscription', 'getProduct']);
});

test('google_nonUnavailableError_rethrownWithoutFallback', async () => {
  const deps = makeDeps();
  const err = Object.assign(new Error('unverified'), { code: 'unverified' });
  deps.google.fail('getSubscription', err);
  await assert.rejects(google(deps), (e) => e === err);
  assert.deepEqual(methods(deps), ['getSubscription']);
});

test('kindHint_subscription_neverCallsProduct', async () => {
  const deps = googleDeps({ products: { [GTOKEN]: googleProduct() } });
  assert.deepEqual(await google(deps, 'subscription'), { notOurs: true, reason: 'not-found' });
  assert.deepEqual(methods(deps), ['getSubscription']);
});

test('kindHint_product_neverCallsSubscription', async () => {
  const deps = googleDeps({ subscriptions: { [GTOKEN]: googleSubscription() } });
  assert.deepEqual(await google(deps, 'product'), { notOurs: true, reason: 'not-found' });
  assert.deepEqual(methods(deps), ['getProduct']);
});

test('apple_asOf_isVerifiedSignedDate_notArrivalTime', async () => {
  const signedDate = T - 5000;
  const deps = appleDeps(appleState({ signedDate }));
  slow(deps, deps.apple, 'fetchState');
  const res = await apple(deps);
  assert.equal(deps.nowMs(), T + TEN_MIN);
  assert.equal(res.purchase.asOfMs, signedDate);
});

test('apple_asOf_isLargestOfTransactionAndRenewalSignedDate', async () => {
  const deps = appleDeps(appleState({ signedDate: T - 5000 }, { renewalInfo: appleRenewalInfo({ signedDate: T - 1000 }) }));
  assert.equal((await apple(deps)).purchase.asOfMs, T - 1000);
});

test('apple_noSignedDate_asOfIsPreRequestMs', async () => {
  const deps = appleDeps(appleState({ signedDate: undefined }));
  slow(deps, deps.apple, 'fetchState');
  const res = await apple(deps);
  assert.equal(res.purchase.asOfMs, T);
});

test('apple_nonFiniteSignedDate_ignored_fallsBackToPreRequestMs', async () => {
  // Chỉ số hữu hạn mới tính: Infinity/NaN/chuỗi/null mà được nhận thì asOfMs hỏng (Infinity thắng mọi so sánh stale).
  for (const signedDate of [Infinity, NaN, String(T - 1000), null]) {
    const deps = appleDeps(appleState({ signedDate }));
    assert.equal((await apple(deps)).purchase.asOfMs, T, String(signedDate));
  }
});

test('google_asOf_isPreRequestMs_notArrivalTime', async () => {
  const deps = googleDeps({ subscriptions: { [GTOKEN]: googleSubscription() } });
  slow(deps, deps.google, 'getSubscription');
  const res = await google(deps);
  assert.equal(deps.nowMs(), T + TEN_MIN);
  assert.equal(res.purchase.asOfMs, T);
});

test('apple_activeSnapshotRequestedBeforeRefund_returnedAfter_carriesOlderAsOf', async () => {
  const deps = makeDeps();
  await deps.db.collection('appAccountTokens').doc(TOKEN).set({ uid: 'uid-1', createdAtMs: 0 });
  await deps.db.collection('entitlementAccounts').doc('uid-1').set({ token: TOKEN });
  const claim = async () => {
    const { purchase } = await apple(deps);
    return applyStoreState(deps, { purchase, claimerUid: 'uid-1', dedupeKey: null });
  };
  deps.apple.states[1000] = appleState({ signedDate: T - 1000 });
  assert.equal((await claim()).entitlement.vip, true);

  // Ảnh chụp active ký lúc T; trong lúc phản hồi còn trên đường về, thông báo REFUND (ký lúc T + 1000) đã được ghi.
  deps.apple.states[1000] = appleState({ signedDate: T });
  const orig = deps.apple.fetchState;
  deps.apple.fetchState = async (id) => {
    const snapshot = await orig(id);
    deps.clock.advance(TEN_MIN);
    const refund = normalizeApple({
      transaction: appleTransaction({ revocationDate: T + 1000, signedDate: T + 1000 }), renewalInfo: null, status: 5, bundleId: TEST_BUNDLE_ID, asOfMs: T + 1000,
    });
    await applyStoreState(deps, { purchase: refund, claimerUid: null, dedupeKey: null });
    return snapshot;
  };
  const late = await claim();
  assert.equal(late.ignored, 'stale');
  assert.equal(late.entitlement.vip, false);
  assert.equal(deps.db.read('entitlements/uid-1').vip, false);
  assert.equal(deps.db.read('purchases/apple_1000').revokedAtMs, T + 1000);
});

test('apple_latestTransactionId_passedThrough', async () => {
  const res = await apple(appleDeps(appleState({ transactionId: '2999' })));
  assert.equal(res.purchase.latestTransactionId, '2999');
  assert.equal(res.purchase.lookupId, '1000');
});

test('unknownStore_invalidArgument', async () => {
  for (const store of ['amazon', undefined, null]) {
    await assert.rejects(fetchNormalized(makeDeps(), { store, id: 'x' }), (e) => e.code === 'invalid-argument');
  }
});
