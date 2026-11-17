'use strict';

// M-16: appAccountToken là UUID v4 do server sinh, lưu hai chiều, không bao giờ ghi đè hay tái dùng.
const test = require('node:test');
const assert = require('node:assert/strict');
const { createFakeFirestore } = require('../helpers/fakeFirestore');
const { getOrCreateAccountToken } = require('../../src/entitlement/accountToken');

const V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const NOW = 1_700_000_000_000;
const uuid = (n) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;

// randomUUID tuần tự; bỏ ids thì dùng crypto.randomUUID mặc định.
function makeDeps(ids) {
  const db = createFakeFirestore();
  const deps = { db, nowMs: () => NOW };
  if (ids) {
    let i = 0;
    deps.randomUUID = () => ids[i++];
  }
  return deps;
}

test('firstCall_createsV4UuidBothDirections', async () => {
  const deps = makeDeps();
  const token = await getOrCreateAccountToken(deps, { uid: 'u1' });
  assert.match(token, V4);
  assert.equal(deps.db.read('entitlementAccounts/u1').token, token);
  assert.deepEqual(deps.db.read(`appAccountTokens/${token}`), { uid: 'u1', createdAtMs: NOW });
});

test('firstCall_lowercasesInjectedUuid', async () => {
  const deps = makeDeps([uuid(1).toUpperCase()]);
  const token = await getOrCreateAccountToken(deps, { uid: 'u1' });
  assert.equal(token, uuid(1));
  assert.ok(deps.db.read(`appAccountTokens/${uuid(1)}`));
});

test('secondCall_returnsSameNoWrites', async () => {
  const deps = makeDeps([uuid(1), uuid(2)]);
  const first = await getOrCreateAccountToken(deps, { uid: 'u1' });
  const writes = deps.db.writes;
  const before = deps.db.snapshot();
  assert.equal(await getOrCreateAccountToken(deps, { uid: 'u1' }), first);
  assert.equal(deps.db.writes, writes);
  assert.deepEqual(deps.db.snapshot(), before);
});

test('existingToken_neverOverwritten', async () => {
  const deps = makeDeps([uuid(9)]);
  await deps.db.collection('entitlementAccounts').doc('u1').set({ token: 'tok-cu', purchaseKeys: ['k1'] });
  const before = deps.db.snapshot();
  assert.equal(await getOrCreateAccountToken(deps, { uid: 'u1' }), 'tok-cu');
  assert.deepEqual(deps.db.snapshot(), before);
});

test('accountDocWithoutToken_addsTokenKeepsOtherFields', async () => {
  const deps = makeDeps([uuid(1)]);
  await deps.db.collection('entitlementAccounts').doc('u1').set({ purchaseKeys: ['k1'], legacyClaims: 2 });
  assert.equal(await getOrCreateAccountToken(deps, { uid: 'u1' }), uuid(1));
  assert.deepEqual(deps.db.read('entitlementAccounts/u1'), { purchaseKeys: ['k1'], legacyClaims: 2, token: uuid(1) });
});

test('collisionWithOtherUid_picksNewUuid', async () => {
  const deps = makeDeps([uuid(1), uuid(2)]);
  await deps.db.collection('appAccountTokens').doc(uuid(1)).set({ uid: 'other', createdAtMs: 1 });
  assert.equal(await getOrCreateAccountToken(deps, { uid: 'u1' }), uuid(2));
  assert.deepEqual(deps.db.read(`appAccountTokens/${uuid(1)}`), { uid: 'other', createdAtMs: 1 });
  assert.equal(deps.db.read(`appAccountTokens/${uuid(2)}`).uid, 'u1');
});

test('collisionWithDeletedToken_picksNewUuid', async () => {
  const deps = makeDeps([uuid(1), uuid(2)]);
  await deps.db.collection('appAccountTokens').doc(uuid(1)).set({ uid: null, deleted: true, createdAtMs: 1 });
  assert.equal(await getOrCreateAccountToken(deps, { uid: 'u1' }), uuid(2));
  assert.equal(deps.db.read(`appAccountTokens/${uuid(1)}`).deleted, true);
});

test('allAttemptsCollide_throwsInternalNoWrites', async () => {
  const ids = [1, 2, 3, 4, 5, 6].map(uuid);
  const deps = makeDeps(ids);
  for (const id of ids) await deps.db.collection('appAccountTokens').doc(id).set({ uid: 'other', createdAtMs: 1 });
  const before = deps.db.snapshot();
  await assert.rejects(getOrCreateAccountToken(deps, { uid: 'u1' }), { code: 'internal' });
  assert.deepEqual(deps.db.snapshot(), before);
});

test('concurrentFirstCalls_sameToken', async () => {
  const deps = makeDeps([uuid(1), uuid(2), uuid(3)]);
  const [a, b, c] = await Promise.all([1, 2, 3].map(() => getOrCreateAccountToken(deps, { uid: 'u1' })));
  assert.equal(a, b);
  assert.equal(b, c);
  const tokens = Object.keys(deps.db.snapshot()).filter((p) => p.startsWith('appAccountTokens/'));
  assert.deepEqual(tokens, [`appAccountTokens/${a}`]);
});

test('badUid_rejectedNoWrites', async () => {
  const deps = makeDeps([uuid(1)]);
  for (const uid of [undefined, null, '', 5, 'a/b']) {
    await assert.rejects(getOrCreateAccountToken(deps, { uid }), { code: 'invalid-argument' });
  }
  assert.equal(deps.db.writes, 0);
});
