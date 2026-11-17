'use strict';

// Firestore giả: limit, orderBy, snapshot.ref, where ==, và batch (set/delete, chỉ áp dụng khi commit).
const test = require('node:test');
const assert = require('node:assert/strict');
const { createFakeFirestore } = require('./fakeFirestore');

test('limit_pagesQuery', async () => {
  const db = createFakeFirestore();
  for (let i = 1; i <= 5; i += 1) await db.collection('c').doc(`d${i}`).set({ n: i });
  const sizes = [];
  // Lấy theo trang rồi xoá, như accountDeletion.deleteQuery.
  // Tối đa 10 vòng: nếu delete hỏng thì test đỏ thay vì treo.
  for (let round = 0; round < 10; round += 1) {
    const snap = await db.collection('c').limit(2).get();
    if (snap.empty) break;
    sizes.push(snap.size);
    for (const doc of snap.docs) await db.collection('c').doc(doc.id).delete();
  }
  assert.deepEqual(sizes, [2, 2, 1]);
  assert.deepEqual(db.snapshot(), {});
});

test('batch_deleteAppliesOnCommit', async () => {
  const db = createFakeFirestore();
  await db.collection('c').doc('d').set({ a: 1 });
  const batch = db.batch();
  batch.delete(db.collection('c').doc('d')).set(db.collection('c').doc('e'), { b: 2 });
  // Chưa commit: delete và set chỉ xếp hàng, dữ liệu không đổi.
  assert.deepEqual(db.read('c/d'), { a: 1 });
  assert.equal(db.read('c/e'), undefined);
  await batch.commit();
  assert.equal(db.read('c/d'), undefined);
  assert.deepEqual(db.read('c/e'), { b: 2 });

  // Commit lỗi thì không ghi gì, như transaction.
  const before = db.snapshot();
  const writes = db.writes;
  const err = new Error('commit lỗi');
  db.failNextCommit(err);
  await assert.rejects(db.batch().delete(db.collection('c').doc('e')).commit(), err);
  assert.deepEqual(db.snapshot(), before);
  assert.equal(db.writes, writes);
});

test('transaction_deleteAppliesOnCommit', async () => {
  const db = createFakeFirestore();
  await db.collection('c').doc('d').set({ a: 1 });
  await db.runTransaction(async (tx) => {
    tx.delete(db.collection('c').doc('d'));
    // Chưa commit: delete chỉ xếp hàng.
    assert.deepEqual(db.read('c/d'), { a: 1 });
  });
  assert.equal(db.read('c/d'), undefined);
});

test('snapshot_hasRef', async () => {
  const db = createFakeFirestore();
  await db.collection('c').doc('d').set({ a: 1 });
  const snap = await db.collection('c').doc('d').get();
  assert.equal(snap.ref.path, 'c/d');
  const [doc] = (await db.collection('c').get()).docs;
  assert.equal(doc.ref.path, 'c/d');
  // ref dùng được ngay, như batch.delete(doc.ref) trong accountDeletion.
  await doc.ref.delete();
  assert.equal(db.read('c/d'), undefined);
});

test('orderBy_sortsByField', async () => {
  const db = createFakeFirestore();
  await db.collection('c').doc('x').set({ n: 3 });
  await db.collection('c').doc('y').set({ n: 1 });
  await db.collection('c').doc('z').set({ n: 2 });
  await db.collection('c').doc('w').set({ other: true });
  // Doc thiếu trường n bị bỏ, như Firestore thật.
  const asc = await db.collection('c').orderBy('n').get();
  assert.deepEqual(asc.docs.map((d) => d.id), ['y', 'z', 'x']);
  const desc = await db.collection('c').orderBy('n', 'desc').get();
  assert.deepEqual(desc.docs.map((d) => d.id), ['x', 'z', 'y']);
  assert.throws(() => db.collection('c').orderBy('n').orderBy('other'), /một orderBy/);
});

test('orderBy_survivesLimitAndWhereInEitherOrder', async () => {
  const db = createFakeFirestore();
  await db.collection('c').doc('a').set({ owner: 'u1', n: 3 });
  await db.collection('c').doc('b').set({ owner: 'u2', n: 0 });
  await db.collection('c').doc('d').set({ owner: 'u1', n: 1 });
  await db.collection('c').doc('e').set({ owner: 'u1', n: 2 });
  const ids = async (q) => (await q.get()).docs.map((d) => d.id);
  // limit sau orderBy: lấy 2 doc nhỏ nhất, không phải 2 doc chèn trước.
  assert.deepEqual(await ids(db.collection('c').orderBy('n').limit(2)), ['b', 'd']);
  // orderBy sau limit: cùng kết quả, orderBy không bị limit làm mất.
  assert.deepEqual(await ids(db.collection('c').limit(2).orderBy('n')), ['b', 'd']);
  // where rồi orderBy, và orderBy rồi where: thứ tự vẫn giữ.
  assert.deepEqual(await ids(db.collection('c').where('owner', '==', 'u1').orderBy('n')), ['d', 'e', 'a']);
  assert.deepEqual(await ids(db.collection('c').orderBy('n').where('owner', '==', 'u1')), ['d', 'e', 'a']);
  assert.deepEqual(await ids(db.collection('c').orderBy('n', 'desc').where('owner', '==', 'u1').limit(2)), ['a', 'e']);
});

test('where_filtersByEquality', async () => {
  const db = createFakeFirestore();
  await db.collection('c').doc('a').set({ owner: 'u1' });
  await db.collection('c').doc('b').set({ owner: 'u2' });
  await db.collection('c').doc('d').set({ owner: 'u1' });
  const snap = await db.collection('c').where('owner', '==', 'u1').get();
  assert.deepEqual(snap.docs.map((d) => d.id), ['a', 'd']);
});
