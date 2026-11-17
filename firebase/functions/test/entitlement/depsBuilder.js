'use strict';

// Dựng `deps` giả cho test entitlement (p6 M-15). Không phải test (không có đuôi .test.js).
// Đồng hồ chỉnh được, randomUUID tuần tự, khoá ký ES256 sinh ngay, cổng Apple/Google giả sửa được giữa các lần gọi.
const { createFakeFirestore } = require('../helpers/fakeFirestore');
const { generateSigningKey } = require('../../scripts/make-signing-key');
const { DEFAULT_POLICY } = require('../../src/entitlement/resolveEntitlement');
const { BASE_NOW_MS } = require('./builders');
const { TEST_BUNDLE_ID } = require('./fixtures/apple');
const { TEST_PACKAGE_NAME, ACKNOWLEDGED } = require('./fixtures/google');

const unavailableError = () => Object.assign(new Error('unavailable'), { code: 'unavailable' });
const unverifiedError = () => Object.assign(new Error('unverified'), { code: 'unverified' });

// Cổng giả: ghi mọi lời gọi vào `calls` ({method, args}); fail(method, error) làm lần gọi KẾ TIẾP của method ném error.
function gate(handlers) {
  const calls = [];
  const failures = new Map();
  const wrap = (method, fn) => async (...args) => {
    calls.push({ method, args });
    if (failures.has(method)) {
      const error = failures.get(method);
      failures.delete(method);
      throw error;
    }
    return fn(...args);
  };
  const g = { calls, fail: (method, error) => { failures.set(method, error); } };
  for (const [method, fn] of Object.entries(handlers)) g[method] = wrap(method, fn);
  return g;
}

const has = (o, k) => Object.hasOwn(o, k);

// states: transactionId -> kết quả fetchState; notifications: signedPayload -> kết quả verifyNotification. Cả hai sửa được tại chỗ.
function fakeApple({ states = {}, notifications = {} } = {}) {
  const g = gate({
    fetchState: (transactionId) => (has(g.states, transactionId) ? g.states[transactionId] : null),
    verifyNotification: (signedPayload) => {
      if (!has(g.notifications, signedPayload)) throw unverifiedError();
      return g.notifications[signedPayload];
    },
  });
  g.states = states;
  g.notifications = notifications;
  return g;
}

// subscriptions / products: purchaseToken -> phản hồi Google (SubscriptionPurchaseV2 / ProductPurchaseV2), sửa được tại chỗ.
function fakeGoogle({ subscriptions = {}, products = {} } = {}) {
  const ack = (table) => ({ purchaseToken }) => {
    if (has(table(), purchaseToken)) table()[purchaseToken].acknowledgementState = ACKNOWLEDGED;
  };
  const g = gate({
    getSubscription: (token) => (has(g.subscriptions, token) ? g.subscriptions[token] : null),
    getProduct: (token) => (has(g.products, token) ? g.products[token] : null),
    acknowledgeSubscription: ack(() => g.subscriptions),
    acknowledgeProduct: ack(() => g.products),
  });
  g.subscriptions = subscriptions;
  g.products = products;
  return g;
}

function makeDeps({
  startMs = BASE_NOW_MS, apple, google, policy, legacyClaimUntilMs = null, accountTokenCutoverMs = null, authCreatedMs,
} = {}) {
  let now = startMs;
  let uuidSeq = 0;
  const { kid, pem } = generateSigningKey();
  return {
    db: createFakeFirestore(),
    apple: apple ?? fakeApple(),
    google: google ?? fakeGoogle(),
    clock: { set: (ms) => { now = ms; }, advance: (ms) => { now += ms; } },
    nowMs: () => now,
    randomUUID: () => { uuidSeq += 1; return `00000000-0000-4000-8000-${String(uuidSeq).padStart(12, '0')}`; },
    signingKey: { kid, pem },
    legacyClaimUntilMs,
    accountTokenCutoverMs,
    authCreatedMs: authCreatedMs ?? (async () => null),
    policy: policy ?? DEFAULT_POLICY,
    bundleId: TEST_BUNDLE_ID,
    packageName: TEST_PACKAGE_NAME,
  };
}

module.exports = { makeDeps, fakeApple, fakeGoogle, unavailableError, TEST_BUNDLE_ID, TEST_PACKAGE_NAME };
