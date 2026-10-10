'use strict';

// Rate limit theo uid (src/rateLimit.js) trên Firestore giả, cộng hai test phủ index.js: một test tĩnh và một test wiring.
// require('../src/rateLimit') đặt lười trong từng test để mỗi test đỏ riêng khi module chưa có.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { HttpsError } = require('firebase-functions/v2/https');
const cfg = require('../src/config');
const { createFakeFirestore } = require('./helpers/fakeFirestore');

const load = () => require('../src/rateLimit');
const NOW = Date.UTC(2026, 9, 8, 12, 0, 0);
const UID = 'user-a';
const NAME = 'acceptCoupleInvite';
const keyOf = (name, uid, nowMs) => `mediaQuota/rl_${name}_${uid}_${Math.floor(nowMs / cfg.rateLimits[name].windowMs)}`;
const exhausted = { code: 'resource-exhausted' };
const INDEX_SRC = fs.readFileSync(path.join(__dirname, '..', 'index.js'), 'utf8');
const HANDLERS = ['acceptCoupleInvite', 'requestAccountDeletion', 'signMemoryUpload', 'confirmMemoryUpload', 'getMemoryMediaUrl'];

// Gọi enforce đúng n lần, tuần tự.
async function callN(enforce, args, n) {
  for (let i = 0; i < n; i += 1) await enforce(args);
}

test('1. đúng max lần qua, lần max+1 bị resource-exhausted', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  const { max } = cfg.rateLimits[NAME];
  await callN(enforce, { db, uid: UID, name: NAME, nowMs: NOW }, max);
  await assert.rejects(enforce({ db, uid: UID, name: NAME, nowMs: NOW }), exhausted);
});

test('2. lần bị từ chối không ghi gì', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  await callN(enforce, { db, uid: UID, name: NAME, nowMs: NOW }, cfg.rateLimits[NAME].max);
  const before = db.snapshot();
  const writes = db.writes;
  await assert.rejects(enforce({ db, uid: UID, name: NAME, nowMs: NOW }), exhausted);
  assert.deepEqual(db.snapshot(), before);
  assert.equal(db.writes, writes);
});

test('3. uid khác đếm riêng', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  await callN(enforce, { db, uid: UID, name: NAME, nowMs: NOW }, cfg.rateLimits[NAME].max);
  await enforce({ db, uid: 'user-b', name: NAME, nowMs: NOW });
});

test('4. name khác đếm riêng', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  await callN(enforce, { db, uid: UID, name: NAME, nowMs: NOW }, cfg.rateLimits[NAME].max);
  await enforce({ db, uid: UID, name: 'requestAccountDeletion', nowMs: NOW });
});

test('5. sang window kế tiếp thì đếm lại từ đầu', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  const { max, windowMs } = cfg.rateLimits[NAME];
  await callN(enforce, { db, uid: UID, name: NAME, nowMs: NOW }, max);
  await assert.rejects(enforce({ db, uid: UID, name: NAME, nowMs: NOW }), exhausted);
  await callN(enforce, { db, uid: UID, name: NAME, nowMs: NOW + windowMs }, max);
  await assert.rejects(enforce({ db, uid: UID, name: NAME, nowMs: NOW + windowMs }), exhausted);
});

test('6. doc counter có expiresAt là Date lớn hơn nowMs, count=1', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  await enforce({ db, uid: UID, name: NAME, nowMs: NOW });
  const doc = db.read(keyOf(NAME, UID, NOW));
  assert.ok(doc, 'phải ghi counter vào mediaQuota/rl_<name>_<uid>_<windowIndex>');
  assert.equal(doc.count, 1);
  assert.ok(doc.expiresAt instanceof Date);
  assert.ok(doc.expiresAt.getTime() > NOW);
});

test('7. name không có trong cfg.rateLimits thì ném lỗi rõ ràng, không ghi gì', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  await assert.rejects(async () => enforce({ db, uid: UID, name: 'nope', nowMs: NOW }), (e) => /nope/.test(e.message));
  assert.equal(db.writes, 0);
});

test('8. đồng thời: counter ở max-1, 5 lần enforce thì đúng 1 qua, 4 resource-exhausted, counter cuối = max', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  const { max } = cfg.rateLimits[NAME];
  const key = keyOf(NAME, UID, NOW);
  await db.collection('mediaQuota').doc(key.split('/')[1]).set({ count: max - 1 });
  const res = await Promise.allSettled(Array.from({ length: 5 }, () => enforce({ db, uid: UID, name: NAME, nowMs: NOW })));
  assert.equal(res.filter((r) => r.status === 'fulfilled').length, 1);
  const rejected = res.filter((r) => r.status === 'rejected');
  assert.equal(rejected.length, 4);
  for (const r of rejected) assert.equal(r.reason.code, 'resource-exhausted');
  assert.equal(db.read(key).count, max);
});

test('9. fail-closed khi đọc counter lỗi: enforce reject đúng lỗi đó, không ghi gì', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  const boom = new Error('boom-read');
  const runTransaction = db.runTransaction;
  db.runTransaction = (fn) => runTransaction((tx) => fn({ ...tx, get: async () => { throw boom; } }));
  const collection = db.collection;
  db.collection = (name) => {
    const col = collection(name);
    const doc = col.doc;
    return { ...col, doc: (id) => ({ ...doc(id), get: async () => { throw boom; } }) };
  };
  await assert.rejects(enforce({ db, uid: UID, name: NAME, nowMs: NOW }), (e) => e === boom);
  assert.equal(db.writes, 0);
});

test('10. fail-closed khi commit lỗi: reject đúng lỗi đã inject, dữ liệu và writes không đổi', async () => {
  const { enforce } = load();
  const db = createFakeFirestore();
  const { max } = cfg.rateLimits[NAME];
  await callN(enforce, { db, uid: UID, name: NAME, nowMs: NOW }, max - 1);
  const before = db.snapshot();
  const writes = db.writes;
  const err = new Error('boom-commit');
  db.failNextCommit(err);
  await assert.rejects(enforce({ db, uid: UID, name: NAME, nowMs: NOW }), (e) => e === err);
  assert.deepEqual(db.snapshot(), before);
  assert.equal(db.writes, writes);
});

test('11. tĩnh: mỗi callable onCall trong index.js gọi limit ngay sau requireUid, và khớp cfg.rateLimits', () => {
  const found = [...INDEX_SRC.matchAll(/^exports\.(\w+) = onCall\(/gm)];
  assert.ok(found.length > 0, 'không tìm thấy callable nào');
  const names = [];
  found.forEach((m, i) => {
    const name = m[1];
    names.push(name);
    const end = i + 1 < found.length ? found[i + 1].index : INDEX_SRC.length;
    const body = INDEX_SRC.slice(m.index, end);
    const re = new RegExp(`async \\(request\\) => \\{\\r?\\n\\s*const uid = requireUid\\(request\\);\\r?\\n\\s*await limit\\('${name}', uid\\);`);
    assert.match(body, re, `${name}: thiếu await limit('${name}', uid); ngay sau requireUid`);
    assert.ok(cfg.rateLimits?.[name], `${name}: thiếu trong cfg.rateLimits`);
  });
  assert.deepEqual(Object.keys(cfg.rateLimits ?? {}).sort(), [...names].sort());
});

test('12. wiring: index.js gọi rateLimit.enforce đúng db/uid/name/nowMs, trước mọi logic nghiệp vụ', async () => {
  const SENTINEL = new Error('rate-limited-sentinel');
  const fakeDb = { fake: 'db' };
  const calls = []; // gọi vào module nghiệp vụ giả
  const enforceCalls = [];
  const rec = (mod) => new Proxy({}, { get: (_, k) => (...a) => { calls.push(`${mod}.${String(k)}`); return {}; } });
  const secret = (name) => ({ name, value: () => `v-${name}` });
  const noop = (opts, handler) => handler;
  const modules = {
    'firebase-admin/app': { initializeApp: () => {}, getApps: () => [] },
    'firebase-admin/firestore': { getFirestore: () => fakeDb },
    'firebase-admin/auth': { getAuth: () => ({}) },
    'firebase-functions/v2/https': { onCall: noop, HttpsError },
    'firebase-functions/v2/firestore': { onDocumentWritten: noop },
    'firebase-functions/v2/scheduler': { onSchedule: noop },
    'firebase-functions/params': { defineSecret: secret, defineString: secret },
    'firebase-functions/logger': { info() {} },
    cloudinary: {},
    './src/config': cfg,
    './src/policy': rec('policy'),
    './src/media': rec('media'),
    './src/pairing': rec('pairing'),
    './src/accountDeletion': rec('accountDeletion'),
    './src/cloudinary': { createCloudinary: () => ({}) },
    './src/rateLimit': { enforce: async (args) => { enforceCalls.push(args); throw SENTINEL; } },
  };
  const fakeRequire = (id) => {
    if (!(id in modules)) throw new Error(`wiring test: require chưa được giả: ${id}`);
    return modules[id];
  };
  const exp = {};
  const ctx = vm.createContext({});
  vm.runInContext(`(function (exports, require, module) {${INDEX_SRC}\n})`, ctx)(exp, fakeRequire, { exports: exp });

  for (const name of HANDLERS) {
    assert.equal(typeof exp[name], 'function', `${name}: không có export`);
    for (const uid of ['uid-one', 'uid-two']) {
      calls.length = 0;
      enforceCalls.length = 0;
      await assert.rejects(exp[name]({ auth: { uid }, data: {} }), (e) => e === SENTINEL);
      assert.deepEqual(calls, [], `${name}/${uid}: module nghiệp vụ bị gọi trước khi limit chặn`);
      assert.equal(enforceCalls.length, 1, `${name}/${uid}: enforce phải được gọi đúng 1 lần`);
      const arg = enforceCalls[0];
      assert.equal(arg.db, fakeDb);
      assert.equal(arg.uid, uid);
      assert.equal(arg.name, name);
      assert.ok(Number.isFinite(arg.nowMs) && arg.nowMs > 0, 'nowMs phải là số hợp lệ');
    }
  }
});
