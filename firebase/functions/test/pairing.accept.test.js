'use strict';

// acceptCoupleInvite trên Firestore giả: idempotency (gọi lại, gọi đồng thời) và các lỗi chính.
const test = require('node:test');
const assert = require('node:assert/strict');
const pairing = require('../src/pairing');
const { createFakeFirestore } = require('./helpers/fakeFirestore');

// Thời điểm cố định: 8/10/2026 12:00 UTC.
const NOW = Date.UTC(2026, 9, 8, 12, 0, 0);
const SENDER = 'user-a';
const CALLER = 'user-b';
const INVITE = 'invite-1';

const pendingInvite = (over = {}) => ({ senderUid: SENDER, targetUid: CALLER, status: 'PENDING', proposedStartDate: 0, ...over });
const accept = (db, uid = CALLER) => pairing.acceptCoupleInvite({
  db, uid, input: pairing.parseAcceptInput({ inviteId: INVITE }), nowMs: NOW,
});

async function setup(invite = pendingInvite()) {
  const db = createFakeFirestore();
  await db.collection('invites').doc(INVITE).set(invite);
  db.writes = 0; // chỉ đếm ghi do acceptCoupleInvite gây ra
  return db;
}

test('accept lần đầu: tạo relationships/{inviteId} ACTIVE, invite thành ACCEPTED, alreadyAccepted=false', async () => {
  const db = await setup();
  const res = await accept(db);
  assert.equal(res.alreadyAccepted, false);
  assert.equal(res.relationshipId, INVITE);
  const rel = db.read(`relationships/${INVITE}`);
  assert.equal(rel.partnerAId, SENDER);
  assert.equal(rel.partnerBId, CALLER);
  assert.equal(rel.status, 'ACTIVE');
  assert.equal(db.read(`invites/${INVITE}`).status, 'ACCEPTED');
});

test('accept lại: alreadyAccepted=true, không ghi gì, dữ liệu không đổi', async () => {
  const db = await setup();
  await accept(db);
  const before = db.snapshot();
  db.writes = 0;
  const res = await accept(db);
  assert.equal(res.alreadyAccepted, true);
  assert.equal(db.writes, 0);
  assert.deepEqual(db.snapshot(), before);
});

test('hai accept đồng thời: đúng một lần tạo, một lần alreadyAccepted, chỉ một relationship', async () => {
  const db = await setup();
  const results = await Promise.all([accept(db), accept(db)]);
  assert.deepEqual(results.map((r) => r.alreadyAccepted).sort(), [false, true]);
  const rels = Object.keys(db.snapshot()).filter((p) => p.startsWith('relationships/'));
  assert.deepEqual(rels, [`relationships/${INVITE}`]);
});

test('invite đã ACCEPTED nhưng thiếu relationship: failed-precondition', async () => {
  const db = await setup(pendingInvite({ status: 'ACCEPTED' }));
  await assert.rejects(accept(db), { code: 'failed-precondition' });
});

test('uid không phải người nhận lời mời: permission-denied', async () => {
  const db = await setup();
  await assert.rejects(accept(db, 'stranger'), { code: 'permission-denied' });
});

test('caller đã có relationship ACTIVE khác: failed-precondition, không ghi gì', async () => {
  const db = await setup();
  await db.collection('relationships').doc('other').set({ partnerAId: CALLER, partnerBId: 'user-c', status: 'ACTIVE' });
  const before = db.snapshot();
  db.writes = 0;
  await assert.rejects(accept(db), { code: 'failed-precondition' });
  assert.equal(db.writes, 0);
  assert.deepEqual(db.snapshot(), before);
});
