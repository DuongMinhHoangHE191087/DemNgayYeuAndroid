'use strict';

// M-101: ackIfPending acknowledge phía server bền vững (B7): chỉ purchase Google đã ràng buộc, còn hiệu lực, đang `pending`;
// thất bại thì lùi lịch thử lại (1 phút, 5 phút, 30 phút, 6 giờ) và không ném lỗi, riêng `force` (RTDN) ném lại để Pub/Sub gửi lại.
const test = require('node:test');
const assert = require('node:assert/strict');
const { ackIfPending } = require('../../src/entitlement/ackIfPending');
const { makeDeps, fakeGoogle, unavailableError } = require('./depsBuilder');
const { BASE_NOW_MS: T, HOUR, makeStoredPurchase } = require('./builders');

const MIN = 60000;
const KEY = 'google_k1';
const path = `purchases/${KEY}`;

// Purchase Google đã ràng buộc, chờ ack: dạng lưu ở purchases/{key} (ackState/ackAttempts/ackNextAtMs do applyStoreState ghi).
const pendingDoc = (o = {}) => makeStoredPurchase({
  store: 'google', key: KEY, lookupId: 'tok-1', productId: 'vip_monthly', acknowledged: false,
  ackState: 'pending', ackAttempts: 0, ackNextAtMs: null, revokedAtMs: null, ...o,
});
async function setup(doc = pendingDoc()) {
  const deps = makeDeps({ google: fakeGoogle({ subscriptions: { 'tok-1': {} }, products: { 'tok-1': {} } }) });
  await deps.db.collection('purchases').doc(KEY).set(doc);
  return deps;
}
const methods = (deps) => deps.google.calls.map((c) => c.method);

test('pending_acksSubscription', async () => {
  const deps = await setup();
  await ackIfPending(deps, { key: KEY });
  assert.deepEqual(deps.google.calls, [{ method: 'acknowledgeSubscription', args: [{ productId: 'vip_monthly', purchaseToken: 'tok-1' }] }]);
  assert.equal(deps.db.read(path).ackState, 'done');
});

test('pending_acksProductForLifetime', async () => {
  const deps = await setup(pendingDoc({ kind: 'lifetime', productId: 'vip_lifetime' }));
  await ackIfPending(deps, { key: KEY });
  assert.deepEqual(deps.google.calls, [{ method: 'acknowledgeProduct', args: [{ productId: 'vip_lifetime', purchaseToken: 'tok-1' }] }]);
  assert.equal(deps.db.read(path).ackState, 'done');
});

test('grace_alsoAcks', async () => {
  const deps = await setup(pendingDoc({ status: 'grace' }));
  await ackIfPending(deps, { key: KEY });
  assert.deepEqual(methods(deps), ['acknowledgeSubscription']);
});

test('done_noCall', async () => {
  const deps = await setup(pendingDoc({ ackState: 'done' }));
  await ackIfPending(deps, { key: KEY, force: true });
  assert.deepEqual(methods(deps), []);
});

test('appleOrOrphan_noCall', async () => {
  const apple = await setup(pendingDoc({ store: 'apple' }));
  await ackIfPending(apple, { key: KEY });
  assert.deepEqual(methods(apple), []);
  const orphan = await setup(pendingDoc({ uid: null, boundAtMs: null }));
  await ackIfPending(orphan, { key: KEY });
  assert.deepEqual(methods(orphan), []);
  const missing = await setup();
  await ackIfPending(missing, { key: 'google_khong_co' });
  assert.deepEqual(methods(missing), []);
});

test('notBound_noAck', async () => {
  const deps = await setup(pendingDoc({ uid: null }));
  await ackIfPending(deps, { key: KEY, force: true });
  assert.deepEqual(methods(deps), []);
  assert.equal(deps.db.read(path).ackState, 'pending');
});

test('notBound_missingUidField_orTombstoned_noAck', async () => {
  const { uid, ...noUid } = pendingDoc();
  const missing = await setup(noUid);
  await ackIfPending(missing, { key: KEY, force: true });
  assert.deepEqual(methods(missing), []);
  // Hình dạng mâu thuẫn (còn uid nhưng đã tombstone) cũng không ack.
  const tomb = await setup(pendingDoc({ tombstone: { uidHash: 'h', atMs: T } }));
  await ackIfPending(tomb, { key: KEY, force: true });
  assert.deepEqual(methods(tomb), []);
  assert.equal(tomb.db.read(path).ackState, 'pending');
});

test('revoked_noAck', async () => {
  const revoked = await setup(pendingDoc({ revokedAtMs: T - HOUR }));
  await ackIfPending(revoked, { key: KEY, force: true });
  assert.deepEqual(methods(revoked), []);
  for (const status of ['expired', 'revoked']) {
    const deps = await setup(pendingDoc({ status }));
    await ackIfPending(deps, { key: KEY, force: true });
    assert.deepEqual(methods(deps), [], status);
    assert.equal(deps.db.read(path).ackState, 'pending');
  }
});

test('failure_recordsAttemptsAndBackoff_noThrow', async () => {
  const deps = await setup();
  deps.google.fail('acknowledgeSubscription', unavailableError());
  await ackIfPending(deps, { key: KEY }); // không ném
  const d = deps.db.read(path);
  assert.equal(d.ackState, 'pending');
  assert.equal(d.ackAttempts, 1);
  assert.equal(d.ackNextAtMs, T + MIN);
});

test('inBackoff_noCall', async () => {
  const deps = await setup(pendingDoc({ ackAttempts: 1, ackNextAtMs: T + MIN }));
  await ackIfPending(deps, { key: KEY });
  assert.deepEqual(methods(deps), []);
  assert.equal(deps.db.read(path).ackAttempts, 1);
  deps.clock.set(T + MIN); // hết lùi: nowMs không còn nhỏ hơn ackNextAtMs
  await ackIfPending(deps, { key: KEY });
  assert.deepEqual(methods(deps), ['acknowledgeSubscription']);
});

test('force_ignoresBackoff_andThrowsOnFailure', async () => {
  const deps = await setup(pendingDoc({ ackAttempts: 1, ackNextAtMs: T + HOUR }));
  deps.google.fail('acknowledgeSubscription', unavailableError());
  await assert.rejects(ackIfPending(deps, { key: KEY, force: true }), { code: 'unavailable' });
  assert.deepEqual(methods(deps), ['acknowledgeSubscription']); // bỏ qua lùi dù chưa tới hạn
  const d = deps.db.read(path);
  assert.equal(d.ackAttempts, 2); // vẫn ghi lịch lùi trước khi ném lại
  assert.equal(d.ackNextAtMs, T + 5 * MIN);
  await ackIfPending(deps, { key: KEY, force: true }); // lần sau thành công, vẫn bỏ qua lùi
  assert.equal(deps.db.read(path).ackState, 'done');
});

test('success_clearsBackoffFields', async () => {
  const deps = await setup(pendingDoc({ ackAttempts: 3, ackNextAtMs: T - MIN }));
  await ackIfPending(deps, { key: KEY });
  const d = deps.db.read(path);
  assert.equal(d.ackState, 'done');
  assert.equal(d.ackAttempts, null);
  assert.equal(d.ackNextAtMs, null);
});

test('backoff_schedule', async () => {
  const deps = await setup();
  const expected = [MIN, 5 * MIN, 30 * MIN, 6 * HOUR, 6 * HOUR];
  for (let i = 0; i < expected.length; i += 1) {
    deps.clock.set(T + i * 10 * HOUR);
    deps.google.fail('acknowledgeSubscription', unavailableError());
    await ackIfPending(deps, { key: KEY });
    const d = deps.db.read(path);
    assert.equal(d.ackAttempts, i + 1);
    assert.equal(d.ackNextAtMs, T + i * 10 * HOUR + expected[i], `lần ${i + 1}`);
  }
});
