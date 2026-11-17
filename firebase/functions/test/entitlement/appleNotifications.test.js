'use strict';

// M-19: handleAppleNotification (App Store Server Notifications V2). Mặc định TỪ CHỐI: chữ ký hỏng không ghi gì; dấu dedupe nằm CÙNG transaction
// với cập nhật (cập nhật hỏng thì Apple gửi lại vẫn áp dụng); REFUND của kỳ cũ không được tắt kỳ mới đang trả; lỗi tạm thời trả 500 để Apple gửi lại.
const test = require('node:test');
const assert = require('node:assert/strict');
const { handleAppleNotification } = require('../../src/entitlement/appleNotifications');
const { applyStoreState } = require('../../src/entitlement/applyStoreState');
const { normalizeApple } = require('../../src/entitlement/normalizeApple');
const { makeDeps, unavailableError, TEST_BUNDLE_ID } = require('./depsBuilder');
const { BASE_NOW_MS: T, DAY, HOUR } = require('./builders');
const { appleTransaction, appleRenewalInfo } = require('./fixtures/apple');

const TOKEN = '11111111-1111-4111-8111-111111111111'; // chủ: uid-1
const MAX_PAYLOAD = 65536;

let seq = 0;
// Đăng ký một thông báo đã "xác minh" vào cổng Apple giả, trả signedPayload. Mặc định: DID_RENEW của giao dịch 2000, ký lúc đồng hồ hiện tại.
function notify(deps, over = {}) {
  seq += 1;
  const signedPayload = `jws-${seq}`;
  deps.apple.notifications[signedPayload] = {
    notificationType: 'DID_RENEW',
    subtype: null,
    notificationUUID: `uuid-${seq}`,
    signedDateMs: deps.nowMs(),
    environment: 'Production',
    bundleId: TEST_BUNDLE_ID,
    status: 1,
    transaction: appleTransaction({ signedDate: deps.nowMs() }),
    renewalInfo: null,
    ...over,
  };
  return signedPayload;
}
const send = (deps, signedPayload) => handleAppleNotification(deps, { signedPayload });
const stored = (deps) => deps.db.read('purchases/apple_1000');
const entitlement = (deps) => deps.db.read('entitlements/uid-1');
const logged = (deps, uuid) => deps.db.read(`notificationLog/apple_${uuid}`);
const fetchCalls = (deps) => deps.apple.calls.filter((c) => c.method === 'fetchState').length;

// Trạng thái chuỗi hiện tại mà fetchState trả (REFUND gọi nó). Ký lúc đồng hồ hiện tại.
function setState(deps, txOver = {}, extra = {}) {
  deps.apple.states['1000'] = {
    environment: 'Production', transaction: appleTransaction({ signedDate: deps.nowMs(), ...txOver }), renewalInfo: null, status: 1, ...extra,
  };
}

// uid-1 sở hữu TOKEN và đã có chuỗi 1000 đang trả (giao dịch 2000, hết hạn sau 29 ngày); đồng hồ chạy tiếp 1 giờ.
async function setup(deps = makeDeps()) {
  await deps.db.collection('appAccountTokens').doc(TOKEN).set({ uid: 'uid-1', createdAtMs: 0 });
  await deps.db.collection('entitlementAccounts').doc('uid-1').set({ token: TOKEN });
  const purchase = normalizeApple({
    transaction: appleTransaction({ signedDate: T }), renewalInfo: null, status: 1, bundleId: TEST_BUNDLE_ID, asOfMs: T,
  });
  const res = await applyStoreState(deps, { purchase, claimerUid: 'uid-1', dedupeKey: null });
  assert.equal(res.entitlement.vip, true);
  deps.clock.advance(HOUR);
  return deps;
}
async function assertNoWrites(deps, run) {
  const before = deps.db.snapshot();
  const writes = deps.db.writes;
  await run();
  assert.deepEqual(deps.db.snapshot(), before, 'không được ghi gì');
  assert.equal(deps.db.writes, writes);
}

test('invalidSignature_400NoWrites', async () => {
  const deps = await setup();
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, 'khong-co-trong-cong-gia'), { httpStatus: 400 }));
});

test('duplicateNotification_noSecondApply', async () => {
  const deps = await setup();
  const sp = notify(deps, { transaction: appleTransaction({ expiresDate: T + 59 * DAY, signedDate: deps.nowMs() }) });
  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.ok(logged(deps, `uuid-${seq}`), 'dấu dedupe được ghi');
  const after = deps.db.snapshot();
  const writes = deps.db.writes;
  deps.clock.advance(HOUR);
  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.deepEqual(deps.db.snapshot(), after);
  assert.equal(deps.db.writes, writes);
});

test('didRenew_extendsExpiry', async () => {
  const deps = await setup();
  const expiresDate = T + 59 * DAY;
  assert.deepEqual(await send(deps, notify(deps, { transaction: appleTransaction({ transactionId: '2001', expiresDate, signedDate: deps.nowMs() }) })), { httpStatus: 200 });
  assert.equal(stored(deps).expiresAtMs, expiresDate);
  assert.equal(stored(deps).latestTransactionId, '2001');
  assert.equal(entitlement(deps).vip, true);
  assert.equal(entitlement(deps).expiresAtMs, expiresDate);
});

test('expired_notEntitled', async () => {
  const deps = await setup();
  const sp = notify(deps, {
    notificationType: 'EXPIRED', subtype: 'VOLUNTARY', status: 2, transaction: appleTransaction({ expiresDate: deps.nowMs() - 1000, signedDate: deps.nowMs() }),
  });
  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.equal(stored(deps).status, 'expired');
  assert.equal(entitlement(deps).vip, false);
});

test('refund_revokes', async () => {
  const deps = await setup();
  const revocationDate = deps.nowMs();
  // Kỳ bị hoàn tiền (2000) chính là kỳ mới nhất của chuỗi.
  setState(deps, { revocationDate }, { status: 5 });
  const sp = notify(deps, { notificationType: 'REFUND', status: 5, transaction: appleTransaction({ revocationDate, signedDate: revocationDate }) });
  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.equal(fetchCalls(deps), 1, 'REFUND luôn hỏi trạng thái hiện tại của chuỗi');
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(stored(deps).revokedAtMs, revocationDate);
  assert.equal(stored(deps).revokedTransactionId, '2000');
  assert.equal(entitlement(deps).vip, false);
});

test('refundPreviousPeriod_currentPaidRenewalRemainsVip', async () => {
  const deps = await setup();
  // Giao dịch 1500 (kỳ cũ) bị hoàn tiền; kỳ 2000 đang trả vẫn là kỳ mới nhất và không bị thu hồi.
  setState(deps, { transactionId: '2000' });
  const revocationDate = deps.nowMs();
  const sp = notify(deps, {
    notificationType: 'REFUND', status: 1, transaction: appleTransaction({ transactionId: '1500', revocationDate, signedDate: revocationDate }),
  });
  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.equal(fetchCalls(deps), 1);
  assert.equal(stored(deps).revokedAtMs, null);
  assert.equal(stored(deps).revokedTransactionId, null);
  assert.equal(stored(deps).status, 'active');
  assert.equal(stored(deps).latestTransactionId, '2000');
  assert.equal(entitlement(deps).vip, true);
});

test('refundPreviousPeriod_statusFetchUnavailable_500Retry', async () => {
  const deps = await setup();
  setState(deps, { transactionId: '2000' });
  const revocationDate = deps.nowMs();
  const sp = notify(deps, {
    notificationType: 'REFUND', status: 1, transaction: appleTransaction({ transactionId: '1500', revocationDate, signedDate: revocationDate }),
  });
  deps.apple.fail('fetchState', unavailableError());
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), { httpStatus: 500 }));
  assert.equal(logged(deps, `uuid-${seq}`), undefined, 'không dấu dedupe, Apple gửi lại vẫn được áp dụng');
  // Apple gửi lại, store đã sống lại: áp ảnh chụp, VIP giữ nguyên.
  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.equal(stored(deps).revokedAtMs, null);
  assert.equal(entitlement(deps).vip, true);
});

test('refundPreviousPeriod_thenLaterRefundOfLatest_revokes', async () => {
  const deps = await setup();
  setState(deps, { transactionId: '2000' });
  let revocationDate = deps.nowMs();
  await send(deps, notify(deps, {
    notificationType: 'REFUND', status: 1, transaction: appleTransaction({ transactionId: '1500', revocationDate, signedDate: revocationDate }),
  }));
  assert.equal(entitlement(deps).vip, true);

  // Sau đó chính kỳ mới nhất (2000) cũng bị hoàn tiền: phải thu hồi.
  deps.clock.advance(HOUR);
  revocationDate = deps.nowMs();
  setState(deps, { transactionId: '2000', revocationDate }, { status: 5 });
  assert.deepEqual(await send(deps, notify(deps, {
    notificationType: 'REFUND', status: 5, transaction: appleTransaction({ transactionId: '2000', revocationDate, signedDate: revocationDate }),
  })), { httpStatus: 200 });
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(stored(deps).revokedAtMs, revocationDate);
  assert.equal(entitlement(deps).vip, false);
});

test('revoke_revokes', async () => {
  const deps = await setup();
  const revocationDate = deps.nowMs();
  setState(deps, { revocationDate, inAppOwnershipType: 'FAMILY_SHARED' }, { status: 5 });
  const sp = notify(deps, {
    notificationType: 'REVOKE', status: 5, transaction: appleTransaction({ revocationDate, signedDate: revocationDate, inAppOwnershipType: 'FAMILY_SHARED' }),
  });
  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(entitlement(deps).vip, false);
});

test('didFailToRenew_entersRetryOrGrace', async () => {
  // Không có grace: billing retry, mất VIP.
  const retry = await setup();
  const expired = retry.nowMs() - 1000;
  assert.deepEqual(await send(retry, notify(retry, {
    notificationType: 'DID_FAIL_TO_RENEW', status: 3, transaction: appleTransaction({ expiresDate: expired, signedDate: retry.nowMs() }),
  })), { httpStatus: 200 });
  assert.equal(stored(retry).status, 'retry');
  assert.equal(entitlement(retry).vip, false);
  assert.equal(entitlement(retry).state, 'retry');

  // Có grace: giữ VIP đến graceExpiresAtMs thật của Apple.
  const grace = await setup();
  const graceEnd = grace.nowMs() + 5 * DAY;
  assert.deepEqual(await send(grace, notify(grace, {
    notificationType: 'DID_FAIL_TO_RENEW',
    subtype: 'GRACE_PERIOD',
    status: 4,
    transaction: appleTransaction({ expiresDate: grace.nowMs() - 1000, signedDate: grace.nowMs() }),
    renewalInfo: appleRenewalInfo({ gracePeriodExpiresDate: graceEnd, signedDate: grace.nowMs() }),
  })), { httpStatus: 200 });
  assert.equal(stored(grace).status, 'grace');
  assert.equal(stored(grace).graceExpiresAtMs, graceEnd);
  assert.equal(entitlement(grace).vip, true);
});

test('refundReversed_restoresWhenNewer', async () => {
  const deps = await setup();
  const revocationDate = deps.nowMs();
  setState(deps, { revocationDate }, { status: 5 });
  await send(deps, notify(deps, { notificationType: 'REFUND', status: 5, transaction: appleTransaction({ revocationDate, signedDate: revocationDate }) }));
  assert.equal(entitlement(deps).vip, false);

  // REFUND_REVERSED ký cũ hơn lúc hoàn tiền: bỏ qua, vẫn thu hồi.
  const staleSp = notify(deps, { notificationType: 'REFUND_REVERSED', signedDateMs: revocationDate - 1000, transaction: appleTransaction({ signedDate: revocationDate - 1000 }) });
  await send(deps, staleSp);
  assert.equal(entitlement(deps).vip, false);

  // REFUND_REVERSED ký mới hơn: khôi phục.
  deps.clock.advance(HOUR);
  assert.deepEqual(await send(deps, notify(deps, { notificationType: 'REFUND_REVERSED', transaction: appleTransaction({ signedDate: deps.nowMs() }) })), { httpStatus: 200 });
  assert.equal(stored(deps).revokedAtMs, null);
  assert.equal(entitlement(deps).vip, true);
});

test('staleSignedDate_ignored', async () => {
  const deps = await setup();
  const sp = notify(deps, {
    notificationType: 'EXPIRED', status: 2, signedDateMs: T - 1000, transaction: appleTransaction({ expiresDate: T - 2000, signedDate: T - 1000 }),
  });
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), { httpStatus: 200 }));
  assert.equal(entitlement(deps).vip, true);
});

test('testNotification_ack200NoWrite', async () => {
  const deps = await setup();
  const sp = notify(deps, { notificationType: 'TEST', transaction: null, status: null });
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), { httpStatus: 200 }));
});

test('foreignBundle_ack200Ignored', async () => {
  const deps = await setup();
  deps.apple.notifications['jws-foreign'] = { foreign: true };
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, 'jws-foreign'), { httpStatus: 200 }));
  // Giao dịch của bundle khác lọt qua verifier cũng không được ghi.
  const sp = notify(deps, { transaction: appleTransaction({ bundleId: 'com.other.app', signedDate: deps.nowMs() }) });
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), { httpStatus: 200 }));
});

test('storeTransientError_500', async () => {
  const deps = await setup();
  const sp = notify(deps);
  deps.apple.fail('verifyNotification', unavailableError());
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), { httpStatus: 500 }));
  assert.equal(logged(deps, `uuid-${seq}`), undefined);
});

test('oversizePayload_400', async () => {
  const deps = await setup();
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, 'x'.repeat(MAX_PAYLOAD + 1)), { httpStatus: 400 }));
  assert.equal(deps.apple.calls.filter((c) => c.method === 'verifyNotification').length, 0, 'không gọi verifier với payload quá lớn');
  // Đúng giới hạn thì vẫn được đưa tới verifier (ở đây không hợp lệ nên 400 do chữ ký).
  assert.deepEqual(await send(deps, 'x'.repeat(MAX_PAYLOAD)), { httpStatus: 400 });
  assert.equal(deps.apple.calls.filter((c) => c.method === 'verifyNotification').length, 1);
});

test('missingPayload_400', async () => {
  const deps = await setup();
  await assertNoWrites(deps, async () => {
    for (const args of [undefined, null, {}, { signedPayload: '' }, { signedPayload: 123 }, { signedPayload: null }, { signedPayload: {} }, { signedPayload: ['a'] }]) {
      assert.deepEqual(await handleAppleNotification(deps, args), { httpStatus: 400 }, JSON.stringify(args));
    }
  });
  assert.equal(deps.apple.calls.length, 0);
});

test('dedupeCommitted_applyFails_retryStillApplies', async () => {
  const deps = await setup();
  const sp = notify(deps, { transaction: appleTransaction({ transactionId: '2001', expiresDate: T + 59 * DAY, signedDate: deps.nowMs() }) });
  const before = deps.db.snapshot();
  deps.db.failNextCommit(new Error('commit failed'));
  assert.deepEqual(await send(deps, sp), { httpStatus: 500 });
  assert.deepEqual(deps.db.snapshot(), before, 'commit hỏng thì không để lại cập nhật lẫn dấu dedupe');
  assert.equal(logged(deps, `uuid-${seq}`), undefined);

  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.equal(stored(deps).latestTransactionId, '2001');
  assert.ok(logged(deps, `uuid-${seq}`));
});

test('concurrentDuplicates_exactlyOneApply', async () => {
  const run = async (copies) => {
    const deps = await setup();
    const sp = notify(deps, { transaction: appleTransaction({ transactionId: '2001', expiresDate: T + 59 * DAY, signedDate: deps.nowMs() }) });
    const writes = deps.db.writes;
    const res = await Promise.all(Array.from({ length: copies }, () => send(deps, sp)));
    assert.deepEqual(res, Array(copies).fill({ httpStatus: 200 }));
    return { applyWrites: deps.db.writes - writes, deps };
  };
  const single = await run(1);
  const double = await run(2);
  assert.ok(single.applyWrites > 0);
  assert.equal(double.applyWrites, single.applyWrites, 'bản trùng thứ hai không được ghi thêm');
  assert.equal(stored(double.deps).latestTransactionId, '2001');
});

test('sandboxNotification_recordsSandbox', async () => {
  const deps = await setup();
  const sp = notify(deps, { environment: 'Sandbox', transaction: appleTransaction({ environment: 'Sandbox', signedDate: deps.nowMs() }) });
  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.equal(stored(deps).environment, 'Sandbox');
});

test('unboundPurchase_orphanRecordedNoGrant', async () => {
  const deps = makeDeps(); // chưa có chủ nào; giao dịch mang token lạ
  const stranger = '99999999-9999-4999-8999-999999999999';
  const sp = notify(deps, { transaction: appleTransaction({ appAccountToken: stranger, signedDate: deps.nowMs() }) });
  assert.deepEqual(await send(deps, sp), { httpStatus: 200 });
  assert.equal(stored(deps).uid, null);
  assert.equal(stored(deps).boundAtMs, null);
  assert.equal(deps.db.read('entitlements/uid-1'), undefined);
  assert.deepEqual(Object.keys(deps.db.snapshot()).filter((p) => p.startsWith('entitlements/')), [], 'mồ côi không bao giờ cấp VIP');
});

const OK_200 = { httpStatus: 200 };

test('testNotificationCarryingTransaction_ack200NoWrite', async () => {
  const deps = await setup();
  const sp = notify(deps, { notificationType: 'TEST', transaction: appleTransaction({ transactionId: '2001', expiresDate: T + 59 * DAY, signedDate: deps.nowMs() }) });
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), OK_200));
});

test('foreignFlagWithTransaction_ack200NoWrite', async () => {
  const deps = await setup();
  const sp = notify(deps, { foreign: true, transaction: appleTransaction({ transactionId: '2001', expiresDate: T + 59 * DAY, signedDate: deps.nowMs() }) });
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), OK_200));
});

test('foreignBundleRevocation_ack200NoStatusFetchNoWrite', async () => {
  const deps = await setup();
  const revocationDate = deps.nowMs();
  const sp = notify(deps, {
    notificationType: 'REFUND', status: 5, transaction: appleTransaction({ bundleId: 'com.other.app', revocationDate, signedDate: revocationDate }),
  });
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), OK_200));
  assert.equal(fetchCalls(deps), 0, 'giao dịch của bundle khác không được làm ta hỏi store');
});

test('missingTransaction_ack200NoWrite', async () => {
  const deps = await setup();
  const sp = notify(deps, { transaction: null });
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), OK_200));
});

test('missingUuidOrSignedDate_400NoWrites', async () => {
  const deps = await setup();
  const cases = [{ notificationUUID: undefined }, { notificationUUID: '' }, { notificationUUID: 123 }, { signedDateMs: undefined }, { signedDateMs: NaN }, { signedDateMs: '123' }];
  await assertNoWrites(deps, async () => {
    for (const over of cases) {
      assert.deepEqual(await send(deps, notify(deps, over)), { httpStatus: 400 }, JSON.stringify(over));
    }
  });
});

test('verifierReturnsNonObject_500NoWrites', async () => {
  const deps = await setup();
  deps.apple.notifications['jws-null'] = null;
  deps.apple.notifications['jws-string'] = 'khong-phai-doi-tuong';
  await assertNoWrites(deps, async () => {
    assert.deepEqual(await send(deps, 'jws-null'), { httpStatus: 500 });
    assert.deepEqual(await send(deps, 'jws-string'), { httpStatus: 500 });
  });
});

test('refundWithoutStatusSnapshot_500NoWritesThenRetryApplies', async () => {
  const deps = await setup();
  const revocationDate = deps.nowMs();
  const sp = notify(deps, { notificationType: 'REFUND', status: 5, transaction: appleTransaction({ revocationDate, signedDate: revocationDate }) });
  // Store chưa có chuỗi này (fetchState trả null): không đoán, để Apple gửi lại.
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), { httpStatus: 500 }));
  assert.equal(logged(deps, `uuid-${seq}`), undefined);
  setState(deps, { revocationDate }, { status: 5 });
  assert.deepEqual(await send(deps, sp), OK_200);
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(entitlement(deps).vip, false);
});

test('refundSnapshotOfOtherChain_500NoWrites', async () => {
  const deps = await setup();
  setState(deps, { originalTransactionId: '9999', transactionId: '9000' });
  const revocationDate = deps.nowMs();
  const sp = notify(deps, { notificationType: 'REFUND', status: 5, transaction: appleTransaction({ revocationDate, signedDate: revocationDate }) });
  await assertNoWrites(deps, async () => assert.deepEqual(await send(deps, sp), { httpStatus: 500 }));
  assert.equal(deps.db.read('purchases/apple_9999'), undefined, 'không áp chuỗi khác');
});

test('refundLatestPeriod_snapshotStillActive_appliesNotificationRevocation', async () => {
  const deps = await setup();
  // Store chưa cập nhật (vẫn active) nhưng thông báo đã ký hoàn tiền đúng kỳ mới nhất: phải thu hồi theo thông báo, không tin ảnh chụp cũ hơn sự thật.
  setState(deps, { transactionId: '2000' });
  const revocationDate = deps.nowMs();
  const sp = notify(deps, { notificationType: 'REFUND', status: 5, transaction: appleTransaction({ transactionId: '2000', revocationDate, signedDate: revocationDate }) });
  assert.deepEqual(await send(deps, sp), OK_200);
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(stored(deps).revokedTransactionId, '2000');
  assert.equal(entitlement(deps).vip, false);
});

test('refundOlderPeriod_snapshotChainRevoked_appliesNotificationOwnRevocation', async () => {
  const deps = await setup();
  // Cả chuỗi đã bị thu hồi (kỳ mới nhất 2000 cũng bị hoàn): không áp ảnh chụp, áp thu hồi riêng của giao dịch trong thông báo.
  const revocationDate = deps.nowMs();
  setState(deps, { transactionId: '2000', revocationDate }, { status: 5 });
  const sp = notify(deps, { notificationType: 'REFUND', status: 5, transaction: appleTransaction({ transactionId: '1500', revocationDate, signedDate: revocationDate }) });
  assert.deepEqual(await send(deps, sp), OK_200);
  assert.equal(stored(deps).status, 'revoked');
  assert.equal(stored(deps).revokedTransactionId, '1500');
  assert.equal(entitlement(deps).vip, false);
});

test('nullRevocationDate_isNotARefund_noStatusFetch', async () => {
  const deps = await setup();
  const expiresDate = T + 59 * DAY;
  const sp = notify(deps, { transaction: appleTransaction({ transactionId: '2001', expiresDate, revocationDate: null, signedDate: deps.nowMs() }) });
  assert.deepEqual(await send(deps, sp), OK_200);
  assert.equal(fetchCalls(deps), 0);
  assert.equal(stored(deps).latestTransactionId, '2001');
  assert.equal(stored(deps).revokedAtMs, null);
  assert.equal(entitlement(deps).vip, true);
});
