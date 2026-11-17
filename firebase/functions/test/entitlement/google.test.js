'use strict';

// Client Google Play (src/entitlement/google.js) với fetchFn giả: không gọi mạng, không dùng thông tin xác thực thật.
// require đặt trong từng test để mỗi test đỏ riêng khi module chưa có.
const test = require('node:test');
const assert = require('node:assert/strict');

const BASE = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.example.inlove/purchases/';
const load = () => require('../../src/entitlement/google');

// fetchFn giả ghi lại lời gọi; `reply` là { status, body } hoặc hàm ném lỗi.
function setup(reply = { status: 200, body: {} }) {
  const calls = [];
  const fetchFn = async (url, init) => {
    calls.push({ url, init });
    if (typeof reply === 'function') return reply();
    return { status: reply.status, ok: reply.status >= 200 && reply.status < 300, json: async () => reply.body };
  };
  const store = load().createGoogleStore({
    packageName: 'com.example.inlove',
    getAccessToken: async () => 'tok-123',
    fetchFn,
  });
  return { calls, store };
}

const isUnavailable = (e) => e instanceof Error && e.code === 'unavailable';

test('getSubscription_callsSubscriptionsV2WithBearer', async () => {
  const { calls, store } = setup({ status: 200, body: { subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE' } });
  const out = await store.getSubscription('ptok');
  assert.deepEqual(out, { subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE' });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, `${BASE}subscriptionsv2/tokens/ptok`);
  assert.equal(calls[0].init.method, 'GET');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer tok-123');
});

test('getProduct_callsProductsV2WithBearer', async () => {
  const { calls, store } = setup({ status: 200, body: { productLineItem: [] } });
  const out = await store.getProduct('ptok');
  assert.deepEqual(out, { productLineItem: [] });
  assert.equal(calls[0].url, `${BASE}productsv2/tokens/ptok`);
  assert.equal(calls[0].init.method, 'GET');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer tok-123');
});

test('acknowledgeSubscription_postsAcknowledge', async () => {
  const { calls, store } = setup({ status: 200, body: {} });
  await store.acknowledgeSubscription({ productId: 'vip_monthly', purchaseToken: 'ptok' });
  assert.equal(calls[0].url, `${BASE}subscriptions/vip_monthly/tokens/ptok:acknowledge`);
  assert.equal(calls[0].init.method, 'POST');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer tok-123');
});

test('acknowledgeProduct_postsAcknowledge', async () => {
  const { calls, store } = setup({ status: 204, body: null });
  await store.acknowledgeProduct({ productId: 'vip_lifetime', purchaseToken: 'ptok' });
  assert.equal(calls[0].url, `${BASE}products/vip_lifetime/tokens/ptok:acknowledge`);
  assert.equal(calls[0].init.method, 'POST');
});

test('acknowledge_non2xx_throwsUnavailable', async () => {
  for (const status of [400, 401, 403, 404, 410, 429, 500, 503]) {
    const { store } = setup({ status });
    await assert.rejects(() => store.acknowledgeSubscription({ productId: 'vip_monthly', purchaseToken: 't' }), isUnavailable, `sub ${status}`);
    await assert.rejects(() => store.acknowledgeProduct({ productId: 'vip_lifetime', purchaseToken: 't' }), isUnavailable, `prod ${status}`);
  }
});

test('http404_returnsNull', async () => {
  const { store } = setup({ status: 404 });
  assert.equal(await store.getSubscription('t'), null);
  assert.equal(await store.getProduct('t'), null);
});

test('http410_returnsNull', async () => {
  const { store } = setup({ status: 410 });
  assert.equal(await store.getSubscription('t'), null);
  assert.equal(await store.getProduct('t'), null);
});

test('http400InvalidToken_returnsNull', async () => {
  const { store } = setup({ status: 400, body: { error: { status: 'INVALID_ARGUMENT', message: 'Invalid purchase token' } } });
  assert.equal(await store.getSubscription('t'), null);
  assert.equal(await store.getProduct('t'), null);
});

test('http401And403_throwUnavailable', async () => {
  for (const status of [401, 403]) {
    const { store } = setup({ status });
    await assert.rejects(() => store.getSubscription('t'), isUnavailable, `sub ${status}`);
    await assert.rejects(() => store.getProduct('t'), isUnavailable, `prod ${status}`);
  }
});

test('http429And5xx_throwUnavailable', async () => {
  for (const status of [429, 500, 502, 503, 504]) {
    const { store } = setup({ status });
    await assert.rejects(() => store.getSubscription('t'), isUnavailable, `sub ${status}`);
    await assert.rejects(() => store.getProduct('t'), isUnavailable, `prod ${status}`);
  }
});

test('networkFailure_throwsUnavailable', async () => {
  const boom = () => { throw new TypeError('fetch failed'); };
  const { store } = setup(boom);
  await assert.rejects(() => store.getSubscription('t'), isUnavailable);
  await assert.rejects(() => store.getProduct('t'), isUnavailable);
  await assert.rejects(() => store.acknowledgeSubscription({ productId: 'p', purchaseToken: 't' }), isUnavailable);
  await assert.rejects(() => store.acknowledgeProduct({ productId: 'p', purchaseToken: 't' }), isUnavailable);
});

test('tokenFetchFailure_throwsUnavailable', async () => {
  let fetched = 0;
  const store = load().createGoogleStore({
    packageName: 'com.example.inlove',
    getAccessToken: async () => { throw new Error('metadata server down'); },
    fetchFn: async () => { fetched += 1; return { status: 200, ok: true, json: async () => ({}) }; },
  });
  await assert.rejects(() => store.getSubscription('t'), isUnavailable);
  await assert.rejects(() => store.getProduct('t'), isUnavailable);
  await assert.rejects(() => store.acknowledgeSubscription({ productId: 'p', purchaseToken: 't' }), isUnavailable);
  await assert.rejects(() => store.acknowledgeProduct({ productId: 'p', purchaseToken: 't' }), isUnavailable);
  assert.equal(fetched, 0, 'không gọi Google khi chưa có access token');
});

test('purchaseToken_urlEncoded', async () => {
  const { calls, store } = setup({ status: 200, body: {} });
  const evil = 'a/b?c=d#e:f g';
  const enc = encodeURIComponent(evil);
  await store.getSubscription(evil);
  await store.getProduct(evil);
  await store.acknowledgeSubscription({ productId: 'vip/x', purchaseToken: evil });
  await store.acknowledgeProduct({ productId: 'vip/x', purchaseToken: evil });
  assert.equal(calls[0].url, `${BASE}subscriptionsv2/tokens/${enc}`);
  assert.equal(calls[1].url, `${BASE}productsv2/tokens/${enc}`);
  assert.equal(calls[2].url, `${BASE}subscriptions/vip%2Fx/tokens/${enc}:acknowledge`);
  assert.equal(calls[3].url, `${BASE}products/vip%2Fx/tokens/${enc}:acknowledge`);
});

test('requestHasAbortSignal', async () => {
  const { calls, store } = setup({ status: 200, body: {} });
  await store.getSubscription('t');
  await store.getProduct('t');
  await store.acknowledgeSubscription({ productId: 'p', purchaseToken: 't' });
  await store.acknowledgeProduct({ productId: 'p', purchaseToken: 't' });
  assert.equal(calls.length, 4);
  for (const c of calls) assert.ok(c.init.signal instanceof AbortSignal, c.url);
});
