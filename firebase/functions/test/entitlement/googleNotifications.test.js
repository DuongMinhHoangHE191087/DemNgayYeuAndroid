'use strict';

// M-20: RTDN của Google Play (Pub/Sub). Nội dung thông báo chỉ là gợi ý: luôn tra lại store (fetchNormalized); thu hồi (voided, loại 12) ép revoked;
// dấu dedupe nằm CÙNG transaction với bản ghi; lỗi tạm của store và lỗi ack ném lại để Pub/Sub gửi lại; mặc định TỪ CHỐI.
const test = require('node:test');
const assert = require('node:assert/strict');
const { handleGoogleNotification } = require('../../src/entitlement/googleNotifications');
const { googleKey } = require('../../src/entitlement/normalizeGoogle');
const { makeDeps, fakeGoogle, unavailableError } = require('./depsBuilder');
const { BASE_NOW_MS: T, DAY, HOUR } = require('./builders');
const { TEST_TOKEN, googleSubscription, googleProduct, iso } = require('./fixtures/google');

const TOK = 'sub-token-1';
const PTOK = 'product-token-1';
const KEY = googleKey(TOK);
const PKEY = googleKey(PTOK);
const PENDING = 'ACKNOWLEDGEMENT_STATE_PENDING';

// Loại thông báo subscription theo RTDN reference.
const RENEWED = 2;
const PURCHASED = 4;
const ON_HOLD = 5;
const REVOKED = 12;
const EXPIRED = 13;

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64');
const head = (deps, extra = {}) => ({ version: '1.0', packageName: deps.packageName, eventTimeMillis: String(deps.nowMs()), ...extra });
const subBody = (deps, type, token = TOK, extra = {}) => head(deps, {
  subscriptionNotification: { version: '1.0', notificationType: type, purchaseToken: token, subscriptionId: 'vip_monthly' }, ...extra,
});
const voidedBody = (deps, productType = 1, token = TOK) => head(deps, {
  voidedPurchaseNotification: { purchaseToken: token, orderId: 'GPA.0000-0000-0000-00000', productType, refundType: 1 },
});
const msg = (body, messageId = 'm1') => ({ data: b64(body), messageId });
const stored = (deps, key = KEY) => deps.db.read(`purchases/${key}`);
const vipOf = (deps) => deps.db.read('entitlements/uid-1');

// uid-1 sở hữu TEST_TOKEN (accountToken trong phản hồi Google giả) nên thông báo (claimerUid null) gắn được purchase.
async function setup({ subscription, product } = {}) {
  const google = fakeGoogle({
    subscriptions: subscription === null ? {} : { [TOK]: subscription ?? googleSubscription() },
    products: product ? { [PTOK]: product } : {},
  });
  const deps = makeDeps({ google });
  await deps.db.collection('appAccountTokens').doc(TEST_TOKEN).set({ uid: 'uid-1', createdAtMs: 0 });
  await deps.db.collection('entitlementAccounts').doc('uid-1').set({ token: TEST_TOKEN });
  return deps;
}
// Áp thông báo PURCHASED ban đầu rồi để đồng hồ chạy 1 giờ.
async function activate(deps) {
  const res = await handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'm0'));
  assert.equal(res.applied, true);
  assert.equal(vipOf(deps).vip, true);
  deps.clock.advance(HOUR);
}
async function assertNoWrites(deps, run) {
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  await run();
  assert.deepEqual(deps.db.snapshot(), before, 'không được ghi gì');
  assert.equal(deps.db.writes, writes);
}

test('badBase64_ackNoWrites', async () => {
  const deps = await setup();
  await assertNoWrites(deps, async () => {
    for (const data of ['!!!', Buffer.from('not json').toString('base64'), b64([1, 2]), b64('str'), '', undefined, 42]) {
      // Trả bình thường (không ném) để Pub/Sub không gửi lại một thông báo không bao giờ đọc được.
      const res = await handleGoogleNotification(deps, { data, messageId: 'bad' });
      assert.equal(res.ignored, 'bad-payload');
    }
  });
  assert.deepEqual(deps.google.calls, []);
});

test('missingMessageId_ignoredNoWritesNoStoreCall', async () => {
  const deps = await setup();
  await assertNoWrites(deps, async () => {
    for (const messageId of [undefined, null, '', 42]) {
      const res = await handleGoogleNotification(deps, { data: b64(subBody(deps, PURCHASED)), messageId });
      assert.equal(res.ignored, 'bad-message-id');
    }
  });
  assert.deepEqual(deps.google.calls, []);
});

test('duplicateMessageId_noSecondApply', async () => {
  const deps = await setup();
  const first = await handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'dup'));
  assert.equal(first.applied, true);
  const before = deps.db.snapshot();
  const second = await handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'dup'));
  assert.equal(second.applied, false);
  assert.equal(second.ignored, 'duplicate');
  assert.deepEqual(deps.db.snapshot(), before);
});

test('subscriptionRenewed_refetchesAndExtends', async () => {
  const deps = await setup();
  await activate(deps);
  assert.equal(vipOf(deps).expiresAtMs, T + 30 * DAY);
  // Gia hạn: hạn mới chỉ có ở store, thông báo không mang hạn nào.
  deps.google.subscriptions[TOK] = googleSubscription({ lineItems: [{ productId: 'vip_monthly', expiryTime: iso(T + 60 * DAY) }] });
  const res = await handleGoogleNotification(deps, msg(subBody(deps, RENEWED), 'm1'));
  assert.equal(res.applied, true);
  assert.equal(vipOf(deps).vip, true);
  assert.equal(vipOf(deps).expiresAtMs, T + 60 * DAY);
  assert.equal(stored(deps).expiresAtMs, T + 60 * DAY);
  assert.equal(deps.google.calls.filter((c) => c.method === 'getSubscription').length, 2);
});

test('subscriptionExpired_notEntitled', async () => {
  const deps = await setup();
  await activate(deps);
  deps.google.subscriptions[TOK] = googleSubscription({
    subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED', lineItems: [{ productId: 'vip_monthly', expiryTime: iso(T) }],
  });
  const res = await handleGoogleNotification(deps, msg(subBody(deps, EXPIRED), 'm1'));
  assert.equal(res.applied, true);
  assert.equal(vipOf(deps).vip, false);
  assert.equal(stored(deps).status, 'expired');
});

test('subscriptionOnHold_notEntitled', async () => {
  const deps = await setup();
  await activate(deps);
  deps.google.subscriptions[TOK] = googleSubscription({ subscriptionState: 'SUBSCRIPTION_STATE_ON_HOLD' });
  const res = await handleGoogleNotification(deps, msg(subBody(deps, ON_HOLD), 'm1'));
  assert.equal(res.applied, true);
  assert.equal(vipOf(deps).vip, false);
  assert.equal(stored(deps).status, 'retry');
});

test('subscriptionRevoked_revokes', async () => {
  const deps = await setup();
  await activate(deps);
  const eventMs = deps.nowMs();
  // Store còn báo ACTIVE (trễ): loại 12 vẫn ép revoked với revokedAtMs = eventTimeMillis.
  const res = await handleGoogleNotification(deps, msg(subBody(deps, REVOKED, TOK, { eventTimeMillis: String(eventMs) }), 'm1'));
  assert.equal(res.applied, true);
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(stored(deps).revokedAtMs, eventMs);
  assert.equal(vipOf(deps).vip, false);
});

test('oneTimeProductPurchased_grantsLifetime', async () => {
  const deps = await setup({ subscription: null, product: googleProduct() });
  const body = head(deps, { oneTimeProductNotification: { version: '1.0', notificationType: 1, purchaseToken: PTOK, sku: 'vip_lifetime' } });
  const res = await handleGoogleNotification(deps, msg(body, 'm1'));
  assert.equal(res.applied, true);
  assert.equal(stored(deps, PKEY).kind, 'lifetime');
  assert.equal(vipOf(deps).vip, true);
  assert.equal(vipOf(deps).expiresAtMs, null);
  assert.equal(deps.google.calls.some((c) => c.method === 'getSubscription'), false);
});

test('voidedPurchase_revokes', async () => {
  const deps = await setup();
  await activate(deps);
  const eventMs = deps.nowMs() - 1000;
  const res = await handleGoogleNotification(deps, msg({ ...voidedBody(deps, 1), eventTimeMillis: String(eventMs) }, 'm1'));
  assert.equal(res.applied, true);
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(stored(deps).revokedAtMs, eventMs);
  assert.equal(vipOf(deps).vip, false);
});

test('voidedPurchase_storeGone_stillRevokesKnownPurchase', async () => {
  const deps = await setup();
  await activate(deps);
  delete deps.google.subscriptions[TOK]; // store không còn trả purchase (null)
  const eventMs = deps.nowMs();
  const res = await handleGoogleNotification(deps, msg(voidedBody(deps, 1), 'm1'));
  assert.equal(res.applied, true);
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(stored(deps).revokedAtMs, eventMs);
  assert.equal(stored(deps).uid, 'uid-1');
  assert.equal(stored(deps).asOfMs, deps.nowMs()); // bản thu hồi mang mốc hiện tại, không phải mốc cũ của bản đã lưu
  assert.equal(vipOf(deps).vip, false);
});

test('voidedPurchase_sameMillisecondAsStored_stillRevokes', async () => {
  const deps = await setup();
  const first = await handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'm0'));
  assert.equal(first.applied, true);
  delete deps.google.subscriptions[TOK]; // đồng hồ không chạy: asOfMs của lần thu hồi phải vẫn mới hơn bản đã lưu
  const res = await handleGoogleNotification(deps, msg(voidedBody(deps, 1), 'm1'));
  assert.equal(res.applied, true);
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(vipOf(deps).vip, false);
});

test('voidedPurchase_kindFollowsProductType', async () => {
  const deps = await setup({ product: googleProduct() });
  await activate(deps);
  deps.google.calls.length = 0;
  await handleGoogleNotification(deps, msg(voidedBody(deps, 1), 'v1')); // đăng ký
  assert.deepEqual(deps.google.calls.map((c) => c.method), ['getSubscription']);
  deps.google.calls.length = 0;
  const res = await handleGoogleNotification(deps, msg(voidedBody(deps, 2, PTOK), 'v2')); // mua một lần
  assert.deepEqual(deps.google.calls.map((c) => c.method), ['getProduct']);
  assert.equal(res.applied, true);
  assert.equal(stored(deps, PKEY).status, 'revoked');
});

test('voidedPurchase_unknownPurchase_ignored', async () => {
  const deps = await setup({ subscription: null });
  await assertNoWrites(deps, async () => {
    const res = await handleGoogleNotification(deps, msg(voidedBody(deps, 1, 'never-seen'), 'm1'));
    assert.equal(res.ignored, 'unknown-purchase');
  });
});

test('testNotification_ackNoWrite', async () => {
  const deps = await setup();
  await assertNoWrites(deps, async () => {
    const res = await handleGoogleNotification(deps, msg(head(deps, { testNotification: { version: '1.0' } }), 'm1'));
    assert.equal(res.ignored, 'test');
  });
  assert.deepEqual(deps.google.calls, []);
});

test('foreignPackage_ackIgnored', async () => {
  const deps = await setup();
  await assertNoWrites(deps, async () => {
    const res = await handleGoogleNotification(deps, msg(subBody(deps, PURCHASED, TOK, { packageName: 'com.other.app' }), 'm1'));
    assert.equal(res.ignored, 'foreign-package');
  });
  assert.deepEqual(deps.google.calls, []); // không hỏi store cho gói của người khác
});

test('payloadClaimsActive_butStoreSaysExpired_expired', async () => {
  const deps = await setup();
  await activate(deps);
  deps.google.subscriptions[TOK] = googleSubscription({
    subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED', lineItems: [{ productId: 'vip_monthly', expiryTime: iso(T) }],
  });
  // Thông báo tự nhận RENEWED kèm trạng thái và hạn giả: chỉ phản hồi của store có giá trị.
  const body = subBody(deps, RENEWED, TOK, { subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE', expiryTime: iso(T + 365 * DAY) });
  body.subscriptionNotification.expiryTime = iso(T + 365 * DAY);
  await handleGoogleNotification(deps, msg(body, 'm1'));
  assert.equal(vipOf(deps).vip, false);
  assert.equal(stored(deps).status, 'expired');
  assert.equal(stored(deps).expiresAtMs, T);
});

test('storeTransientError_throwsForRetry', async () => {
  const deps = await setup();
  deps.google.fail('getSubscription', unavailableError());
  await assertNoWrites(deps, async () => {
    await assert.rejects(() => handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'm1')), /unavailable/);
  });
  assert.equal(deps.db.read('notificationLog/google_m1'), undefined); // chưa đánh dấu: lần gửi lại vẫn áp dụng
  assert.equal((await handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'm1'))).applied, true);
  assert.equal(vipOf(deps).vip, true);
});

test('dedupeCommitted_applyFails_retryStillApplies', async () => {
  const deps = await setup();
  deps.db.failNextCommit(unavailableError());
  await assertNoWrites(deps, async () => {
    await assert.rejects(() => handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'm1')), /unavailable/);
  });
  // Dấu dedupe cùng transaction nên commit hỏng thì không có dấu: Pub/Sub gửi lại vẫn được áp dụng.
  assert.equal(deps.db.read('notificationLog/google_m1'), undefined);
  const res = await handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'm1'));
  assert.equal(res.applied, true);
  assert.deepEqual(deps.db.read('notificationLog/google_m1').key, KEY);
  assert.equal(vipOf(deps).vip, true);
});

test('concurrentDuplicates_exactlyOneApply', async () => {
  const deps = await setup();
  const results = await Promise.all([1, 2, 3].map(() => handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'same'))));
  assert.equal(results.filter((r) => r.applied === true).length, 1);
  assert.equal(results.filter((r) => r.ignored === 'duplicate').length, 2);
  assert.equal(vipOf(deps).vip, true);
});

test('pendingAck_forceRetriedAndThrowsOnFailure', async () => {
  const deps = await setup({ subscription: googleSubscription({ acknowledgementState: PENDING }) });
  deps.google.fail('acknowledgeSubscription', unavailableError());
  await assert.rejects(() => handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'm1')), /unavailable/);
  // Bản ghi đã commit (kèm dấu dedupe) nhưng ack lỗi: lịch lùi được ghi, ackState vẫn pending.
  assert.equal(stored(deps).ackState, 'pending');
  assert.equal(stored(deps).ackAttempts, 1);
  assert.ok(stored(deps).ackNextAtMs > deps.nowMs());
  assert.ok(deps.db.read('notificationLog/google_m1'));
  // Gửi lại cùng messageId (duplicate) ngay lập tức: force bỏ qua lịch lùi và ack thành công.
  const res = await handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'm1'));
  assert.equal(res.ignored, 'duplicate');
  assert.equal(stored(deps).ackState, 'done');
  assert.equal(deps.google.calls.filter((c) => c.method === 'acknowledgeSubscription').length, 2);
});

test('subscriptionStoreNotFound_ignoredNotOurs_noWrites', async () => {
  const deps = await setup({ subscription: null });
  await assertNoWrites(deps, async () => {
    const res = await handleGoogleNotification(deps, msg(subBody(deps, RENEWED), 'm1'));
    assert.equal(res.ignored, 'not-ours');
  });
});

test('unboundPurchase_notificationNeverBinds_orphanRecorded', async () => {
  // Token chưa đăng ký cho ai: thông báo (không có claimerUid) chỉ ghi orphan, không bao giờ gắn vào một uid hay cấp VIP.
  const unknown = '33333333-3333-4333-8333-333333333333';
  const deps = await setup({ subscription: googleSubscription({ externalAccountIdentifiers: { obfuscatedExternalAccountId: unknown } }) });
  const res = await handleGoogleNotification(deps, msg(subBody(deps, PURCHASED), 'm1'));
  assert.equal(res.ok, true);
  assert.equal(res.orphan, true);
  assert.equal(stored(deps).uid ?? null, null);
  assert.equal(vipOf(deps), undefined);
});

test('notificationWithoutPurchaseToken_ignoredNoWritesNoStoreCall', async () => {
  const deps = await setup();
  await assertNoWrites(deps, async () => {
    const bodies = [
      head(deps, { subscriptionNotification: { notificationType: PURCHASED } }),
      head(deps, { subscriptionNotification: { notificationType: PURCHASED, purchaseToken: '' } }),
      head(deps, { subscriptionNotification: { notificationType: PURCHASED, purchaseToken: 7 } }),
      head(deps, { oneTimeProductNotification: { notificationType: 1 } }),
      head(deps, { voidedPurchaseNotification: { productType: 1 } }),
      head(deps, { somethingNew: { purchaseToken: TOK } }),
    ];
    for (const [i, body] of bodies.entries()) {
      const res = await handleGoogleNotification(deps, msg(body, `n${i}`));
      assert.equal(res.ignored, 'unknown-notification', `body ${i}`);
    }
  });
  assert.deepEqual(deps.google.calls, []);
});
