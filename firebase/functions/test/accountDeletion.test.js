'use strict';

// Đặc tả hành vi hiện có của src/accountDeletion.js trên Firestore giả: xoá tài khoản idempotent, Auth xoá cuối cùng.
const test = require('node:test');
const assert = require('node:assert/strict');
const { createFakeFirestore } = require('./helpers/fakeFirestore');
const { processAccountDeletion, runJob, requireRecentLogin, requestAccountDeletion, RECENT_LOGIN_MS } = require('../src/accountDeletion');

const UID = 'u1';
const NOW = 1_700_000_000_000;

// Auth giả: ghi lại uid bị xoá, có thể ép ném lỗi.
function makeAuth(err) {
  const deleted = [];
  return { deleted, deleteUser: async (uid) => { deleted.push(uid); if (err) throw err; } };
}

// purge giả: chỉ ghi lại đối số của mỗi lời gọi (test tự gọi shouldClaim để kiểm điều kiện claim).
function makePurge() {
  const calls = [];
  return { calls, purge: async (arg) => { calls.push(arg); } };
}

const run = (db, extra = {}) => processAccountDeletion({ db, auth: makeAuth(), purge: makePurge().purge, uid: UID, nowMs: NOW, ...extra });

test('relationships_terminatedWithAccountDeletedReason', async () => {
  const db = createFakeFirestore();
  // Bốn bộ trường khác nhau cùng trỏ tới uid; một cặp đã TERMINATED; một cặp của người khác.
  await db.collection('relationships').doc('a').set({ partnerAId: UID, status: 'ACTIVE' });
  await db.collection('relationships').doc('b').set({ partnerBId: UID, status: 'ACTIVE' });
  await db.collection('relationships').doc('c').set({ user1: UID, status: 'ACTIVE' });
  await db.collection('relationships').doc('d').set({ user2: UID, status: 'ACTIVE' });
  await db.collection('relationships').doc('done').set({ partnerAId: UID, status: 'TERMINATED', terminatedReason: 'BREAKUP', updatedAt: 1 });
  await db.collection('relationships').doc('other').set({ partnerAId: 'x', partnerBId: 'y', status: 'ACTIVE' });

  await run(db);

  for (const id of ['a', 'b', 'c', 'd']) {
    assert.deepEqual(db.read(`relationships/${id}`).status, 'TERMINATED', id);
    assert.equal(db.read(`relationships/${id}`).terminatedReason, 'ACCOUNT_DELETED', id);
    assert.equal(db.read(`relationships/${id}`).updatedAt, NOW, id);
  }
  // Đã TERMINATED thì giữ nguyên lý do cũ.
  assert.equal(db.read('relationships/done').terminatedReason, 'BREAKUP');
  assert.equal(db.read('relationships/done').updatedAt, 1);
  // Cặp của người khác không bị đụng tới.
  assert.equal(db.read('relationships/other').status, 'ACTIVE');
});

test('ownMemoriesDeletedBeforeTerminate', async () => {
  const seed = async (db) => {
    await db.collection('relationships').doc('r').set({ partnerAId: UID, partnerBId: 'p', status: 'ACTIVE' });
    const mem = db.collection('relationships').doc('r').collection('memories');
    await mem.doc('mine').set({ authorId: UID });
    await mem.doc('theirs').set({ authorId: 'p' });
  };

  // Thành công: kỷ niệm của uid mất, của đối phương còn.
  const db = createFakeFirestore();
  await seed(db);
  await run(db);
  assert.equal(db.read('relationships/r/memories/mine'), undefined);
  assert.deepEqual(db.read('relationships/r/memories/theirs'), { authorId: 'p' });
  assert.equal(db.read('relationships/r').status, 'TERMINATED');

  // Thứ tự: batch xoá kỷ niệm lỗi thì cặp CHƯA bị ngắt (xoá kỷ niệm đi trước terminate).
  const db2 = createFakeFirestore();
  await seed(db2);
  db2.failNextCommit(new Error('boom'));
  await assert.rejects(run(db2), /boom/);
  assert.equal(db2.read('relationships/r').status, 'ACTIVE');
  assert.deepEqual(db2.read('relationships/r/memories/mine'), { authorId: UID });
});

test('mediaAssets_purgedWhenNeeded', async () => {
  const db = createFakeFirestore();
  for (const [id, state] of [['up', 'uploaded'], ['pend', 'pending'], ['ing', 'deleting'], ['del', 'deleted'], ['exp', 'expired']]) {
    await db.collection('mediaAssets').doc(id).set({ ownerUid: UID, state });
  }
  await db.collection('mediaAssets').doc('theirs').set({ ownerUid: 'other', state: 'uploaded' });
  const { calls, purge } = makePurge();

  await run(db, { purge });

  // Chỉ tệp của uid còn tồn tại (kể cả đang deleting) được gom; deleted/expired và tệp người khác thì bỏ qua.
  assert.deepEqual(calls.map((c) => c.memoryId).sort(), ['ing', 'pend', 'up']);
  for (const c of calls) {
    assert.equal(c.nowMs, NOW);
    assert.equal(c.reason, 'account-deleted');
  }
  // shouldClaim: chỉ claim khi tệp chưa deleted/expired.
  const claim = calls[0].shouldClaim;
  assert.equal(claim({ state: 'uploaded' }), true);
  assert.equal(claim({ state: 'deleting' }), true);
  assert.equal(claim({ state: 'deleted' }), false);
  assert.equal(claim({ state: 'expired' }), false);
});

test('invitesCoupleCodesMemoriesAndUserDocDeleted', async () => {
  const db = createFakeFirestore();
  await db.collection('invites').doc('i1').set({ senderUid: UID });
  await db.collection('invites').doc('i2').set({ receiverUid: UID });
  await db.collection('invites').doc('i3').set({ targetUid: UID });
  await db.collection('invites').doc('keep').set({ senderUid: 'x', receiverUid: 'y', targetUid: 'z' });
  await db.collection('coupleCodes').doc('c1').set({ ownerUid: UID });
  await db.collection('coupleCodes').doc('ckeep').set({ ownerUid: 'x' });
  await db.collection('memories').doc('m1').set({ authorUid: UID });
  await db.collection('memories').doc('mkeep').set({ authorUid: 'x' });
  await db.collection('users').doc(UID).set({ name: 'a' });
  await db.collection('users').doc('x').set({ name: 'b' });

  await run(db);

  for (const p of ['invites/i1', 'invites/i2', 'invites/i3', 'coupleCodes/c1', 'memories/m1', `users/${UID}`]) {
    assert.equal(db.read(p), undefined, p);
  }
  for (const p of ['invites/keep', 'coupleCodes/ckeep', 'memories/mkeep', 'users/x']) {
    assert.notEqual(db.read(p), undefined, p);
  }
  // Việc được đánh dấu done.
  const job = db.read(`accountDeletions/${UID}`);
  assert.equal(job.state, 'done');
  assert.equal(job.doneAt, NOW);
  assert.equal(job.lastError, null);
});

test('deletesBlockedUsersBothDirections', async () => {
  const db = createFakeFirestore();
  await db.collection('blockedUsers').doc('b1').set({ blockerUid: UID, blockedUid: 'x' });
  await db.collection('blockedUsers').doc('b2').set({ blockerUid: 'x', blockedUid: UID });
  await db.collection('blockedUsers').doc('bkeep').set({ blockerUid: 'x', blockedUid: 'y' });

  await run(db);

  // Cả hai chiều chặn của uid bị xoá; bản ghi không liên quan còn nguyên.
  assert.equal(db.read('blockedUsers/b1'), undefined);
  assert.equal(db.read('blockedUsers/b2'), undefined);
  assert.deepEqual(db.read('blockedUsers/bkeep'), { blockerUid: 'x', blockedUid: 'y' });
});

test('deletesReportsBothDirections', async () => {
  const db = createFakeFirestore();
  await db.collection('userReports').doc('r1').set({ reporterUid: UID, reportedUid: 'x' });
  await db.collection('userReports').doc('r2').set({ reporterUid: 'x', reportedUid: UID });
  await db.collection('userReports').doc('rkeep').set({ reporterUid: 'x', reportedUid: 'y' });

  await run(db);

  // Báo cáo uid gửi đi lẫn bị báo cáo đều bị xoá; báo cáo không liên quan còn nguyên.
  assert.equal(db.read('userReports/r1'), undefined);
  assert.equal(db.read('userReports/r2'), undefined);
  assert.deepEqual(db.read('userReports/rkeep'), { reporterUid: 'x', reportedUid: 'y' });
});

test('blockedAndReportsCleaned_beforeUserDocDeleted', async () => {
  // Dọn chặn/báo cáo hỏng giữa chừng thì users/{uid} còn nguyên, lần chạy lại vẫn dọn tiếp được.
  const db = createFakeFirestore();
  await db.collection('users').doc(UID).set({ name: 'a' });
  await db.collection('userReports').doc('r1').set({ reporterUid: UID, reportedUid: 'x' });
  const real = db.collection.bind(db);
  db.collection = (name) => {
    if (name === 'userReports') throw new Error('boom');
    return real(name);
  };
  await assert.rejects(run(db), /boom/);
  assert.notEqual(db.read(`users/${UID}`), undefined);
});

test('deletesAuthUser_ignoresUserNotFound', async () => {
  // Gọi deleteUser(uid) đúng một lần, ở bước cuối.
  const db = createFakeFirestore();
  const auth = makeAuth();
  await run(db, { auth });
  assert.deepEqual(auth.deleted, [UID]);

  // auth/user-not-found: coi như đã xoá, vẫn done.
  const db2 = createFakeFirestore();
  await run(db2, { auth: makeAuth(Object.assign(new Error('nf'), { code: 'auth/user-not-found' })) });
  assert.equal(db2.read(`accountDeletions/${UID}`).state, 'done');

  // Lỗi khác thì ném lại và KHÔNG đánh dấu done.
  const db3 = createFakeFirestore();
  const other = Object.assign(new Error('internal'), { code: 'auth/internal-error' });
  await assert.rejects(run(db3, { auth: makeAuth(other) }), /internal/);
  assert.equal(db3.read(`accountDeletions/${UID}`), undefined);
});

test('pagesMoreThan400Docs', async () => {
  const db = createFakeFirestore();
  for (let i = 0; i < 850; i += 1) await db.collection('invites').doc(`i${i}`).set({ senderUid: UID });
  await db.collection('invites').doc('keep').set({ senderUid: 'x' });

  let commits = 0;
  const realBatch = db.batch;
  db.batch = () => {
    const b = realBatch();
    const commit = b.commit;
    b.commit = async () => { commits += 1; return commit(); };
    return b;
  };

  await run(db);

  // 850 doc chia theo trang 400: 400 + 400 + 50 -> 3 batch cho invites.senderUid.
  assert.equal(commits, 3);
  assert.equal((await db.collection('invites').where('senderUid', '==', UID).get()).size, 0);
  assert.notEqual(db.read('invites/keep'), undefined);
});

test('runJob_onlyForPending', async () => {
  const mk = async (state) => {
    const db = createFakeFirestore();
    if (state) await db.collection('accountDeletions').doc(UID).set({ state });
    await db.collection('users').doc(UID).set({ name: 'a' });
    const auth = makeAuth();
    await runJob({ db, auth, purge: makePurge().purge, uid: UID, nowMs: NOW });
    return { db, auth };
  };

  // Không có việc, hoặc đã done: không làm gì.
  for (const state of [null, 'done']) {
    const { db, auth } = await mk(state);
    assert.deepEqual(auth.deleted, [], String(state));
    assert.notEqual(db.read(`users/${UID}`), undefined, String(state));
  }

  // pending: chạy và đánh dấu done.
  const { db, auth } = await mk('pending');
  assert.deepEqual(auth.deleted, [UID]);
  assert.equal(db.read(`users/${UID}`), undefined);
  assert.equal(db.read(`accountDeletions/${UID}`).state, 'done');

  // pending nhưng lỗi: ghi lastError rồi ném lại để trigger retry, vẫn pending.
  const db2 = createFakeFirestore();
  await db2.collection('accountDeletions').doc(UID).set({ state: 'pending' });
  const boom = Object.assign(new Error('x'.repeat(600)), { code: 'auth/internal-error' });
  await assert.rejects(runJob({ db: db2, auth: makeAuth(boom), purge: makePurge().purge, uid: UID, nowMs: NOW }), /xxx/);
  const job = db2.read(`accountDeletions/${UID}`);
  assert.equal(job.state, 'pending');
  assert.equal(job.lastError.length, 500);
  assert.equal(job.lastErrorAt, NOW);
});

test('purgeAvatar: gọi với uid TRƯỚC khi xoá users/{uid}, qua cả processAccountDeletion và runJob', async () => {
  const order = [];
  const mk = async () => {
    const db = createFakeFirestore();
    await db.collection('users').doc(UID).set({ name: 'a' });
    await db.collection('accountDeletions').doc(UID).set({ state: 'pending' });
    const purgeAvatar = async (uid) => { order.push([uid, db.read(`users/${UID}`) !== undefined]); };
    return { db, purgeAvatar };
  };

  const a = await mk();
  await processAccountDeletion({ db: a.db, auth: makeAuth(), purge: makePurge().purge, purgeAvatar: a.purgeAvatar, uid: UID, nowMs: NOW });
  const b = await mk();
  await runJob({ db: b.db, auth: makeAuth(), purge: makePurge().purge, purgeAvatar: b.purgeAvatar, uid: UID, nowMs: NOW });

  // Mỗi lần đúng một lời gọi, lúc đó users/{uid} vẫn còn.
  assert.deepEqual(order, [[UID, true], [UID, true]]);
  assert.equal(b.db.read(`accountDeletions/${UID}`).state, 'done');
});

test('purgeAvatar: lỗi thì ném lại, users/{uid} còn, job vẫn pending để retry', async () => {
  const db = createFakeFirestore();
  await db.collection('users').doc(UID).set({ name: 'a' });
  await db.collection('accountDeletions').doc(UID).set({ state: 'pending' });
  const auth = makeAuth();
  let fail = true;
  const purgeAvatar = async () => { if (fail) throw new Error('r2-down'); };

  await assert.rejects(runJob({ db, auth, purge: makePurge().purge, purgeAvatar, uid: UID, nowMs: NOW }), /r2-down/);
  assert.notEqual(db.read(`users/${UID}`), undefined);
  assert.deepEqual(auth.deleted, []);
  assert.equal(db.read(`accountDeletions/${UID}`).state, 'pending');
  assert.equal(db.read(`accountDeletions/${UID}`).lastError, 'r2-down');

  fail = false;
  await runJob({ db, auth, purge: makePurge().purge, purgeAvatar, uid: UID, nowMs: NOW });
  assert.equal(db.read(`users/${UID}`), undefined);
  assert.equal(db.read(`accountDeletions/${UID}`).state, 'done');
});

test('requireRecentLogin_rejectsOlderThan5Min', () => {
  const authTimeSec = NOW / 1000;
  assert.equal(RECENT_LOGIN_MS, 5 * 60 * 1000);

  // Đúng 5 phút vẫn hợp lệ; trễ 1 giây thì bị từ chối.
  assert.doesNotThrow(() => requireRecentLogin(authTimeSec, NOW + RECENT_LOGIN_MS));
  assert.throws(
    () => requireRecentLogin(authTimeSec, NOW + RECENT_LOGIN_MS + 1000),
    (err) => err.code === 'failed-precondition' && err.details.reason === 'recent-login-required',
  );
  // Thiếu hoặc sai auth_time cũng bị từ chối.
  // Infinity và chuỗi số cũng không phải số hữu hạn: `nowMs - Infinity` sẽ lọt qua phép so sánh nếu bỏ kiểm Number.isFinite.
  for (const bad of [undefined, null, NaN, 'abc', Infinity, String(authTimeSec)]) {
    assert.throws(() => requireRecentLogin(bad, NOW), (err) => err.code === 'failed-precondition' && err.details.reason === 'recent-login-required', String(bad));
  }
});

test('requestAccountDeletion_writesPendingOnlyAfterRecentLogin', async () => {
  const db = createFakeFirestore();
  await db.collection('accountDeletions').doc(UID).set({ state: 'done', keep: 1 });

  // Đăng nhập cũ: từ chối và KHÔNG ghi gì.
  await assert.rejects(
    requestAccountDeletion({ db, uid: UID, authTimeSec: NOW / 1000 - 3600, nowMs: NOW }),
    (err) => err.code === 'failed-precondition' && err.details.reason === 'recent-login-required',
  );
  assert.deepEqual(db.read(`accountDeletions/${UID}`), { state: 'done', keep: 1 });

  // Đăng nhập gần đây: ghi pending kiểu merge (giữ trường cũ, xoá lastError) để kích hoạt lại trigger.
  assert.deepEqual(await requestAccountDeletion({ db, uid: UID, authTimeSec: NOW / 1000, nowMs: NOW }), { accepted: true });
  assert.deepEqual(db.read(`accountDeletions/${UID}`), { state: 'pending', keep: 1, requestedAt: NOW, lastError: null });
});
