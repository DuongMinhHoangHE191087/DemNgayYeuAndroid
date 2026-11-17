'use strict';

// Firestore giả: docRef.delete() và tx.delete(ref). tx.delete chỉ xếp hàng, áp dụng khi commit, như tx.set.
const test = require('node:test');
const assert = require('node:assert/strict');
const { createFakeFirestore } = require('./fakeFirestore');

test('docRef_delete_removesDoc', async () => {
  const db = createFakeFirestore();
  await db.collection('c').doc('d').set({ a: 1 });
  await db.collection('c').doc('d').delete();
  assert.equal(db.read('c/d'), undefined);
  assert.equal((await db.collection('c').doc('d').get()).exists, false);
});

test('tx_delete_appliedOnCommit', async () => {
  const db = createFakeFirestore();
  await db.collection('c').doc('d').set({ a: 1 });
  const ref = db.collection('c').doc('d');
  await db.runTransaction(async (tx) => {
    tx.delete(ref);
    // Chưa commit: doc vẫn còn, delete chỉ xếp hàng.
    assert.deepEqual(db.read('c/d'), { a: 1 });
  });
  assert.equal(db.read('c/d'), undefined);
});

test('tx_delete_discardedOnFailedCommit', async () => {
  const db = createFakeFirestore();
  await db.collection('c').doc('d').set({ a: 1 });
  const ref = db.collection('c').doc('d');
  const before = db.snapshot();
  const writes = db.writes;
  const err = new Error('commit lỗi');
  db.failNextCommit(err);
  await assert.rejects(
    db.runTransaction(async (tx) => {
      tx.delete(ref);
    }),
    err,
  );
  assert.deepEqual(db.snapshot(), before);
  assert.equal(db.writes, writes);
});
