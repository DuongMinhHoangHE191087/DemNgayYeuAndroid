'use strict';

// Firestore giả trong bộ nhớ, đủ cho test của Functions: doc/collection/doc con, truy vấn where, transaction.
// ponytail: fake chỉ tuần tự hoá transaction bằng mutex, không mô phỏng lock, contention hay retry thật của Firestore;
// khi cần kiểm hành vi đó thì dùng emulator.
const { FieldValue } = require('firebase-admin/firestore');

const ONE = FieldValue.increment(1);
const isIncrement = (v) => !!v?.isEqual?.(ONE);

// Áp một lần ghi lên dữ liệu cũ: increment cộng 1, các giá trị khác (kể cả serverTimestamp) lưu nguyên.
function apply(base, data) {
  const out = { ...base };
  for (const [k, v] of Object.entries(data)) out[k] = isIncrement(v) ? (typeof base[k] === 'number' ? base[k] : 0) + 1 : v;
  return out;
}

function createFakeFirestore() {
  const store = new Map(); // path -> dữ liệu
  let chain = Promise.resolve(); // mutex của runTransaction
  let nextCommitError = null;

  const db = {
    writes: 0,
    // Lần commit kế tiếp reject với err; dữ liệu và bộ đếm writes giữ nguyên.
    failNextCommit(err) { nextCommitError = err; },
    // Đọc dữ liệu đã lưu (bản sao nông) hoặc undefined.
    read: (path) => (store.has(path) ? { ...store.get(path) } : undefined),
    // Ảnh chụp toàn bộ dữ liệu, để so "không đổi".
    snapshot: () => Object.fromEntries([...store].map(([p, d]) => [p, { ...d }])),
  };

  const snapOf = (path, data) => ({
    id: path.split('/').pop(),
    exists: data !== undefined,
    data: () => (data === undefined ? undefined : { ...data }),
    get: (field) => data?.[field],
  });

  const querySnap = (docs) => ({ docs, size: docs.length, empty: docs.length === 0, forEach: (fn) => docs.forEach(fn) });

  function write(path, kind, data, opts) {
    const old = store.get(path);
    if (kind === 'update' && !old) throw new Error(`NOT_FOUND: ${path}`);
    store.set(path, apply(kind === 'update' || opts?.merge ? old ?? {} : {}, data));
    db.writes += 1;
  }

  function docRef(path) {
    return {
      path,
      id: path.split('/').pop(),
      collection: (name) => collectionRef(`${path}/${name}`),
      get: async () => snapOf(path, store.get(path)),
      set: async (data, opts) => write(path, 'set', data, opts),
      update: async (data) => write(path, 'update', data),
    };
  }

  function collectionRef(path, filters = []) {
    const run = () => {
      const docs = [];
      for (const [p, data] of store) {
        const inCol = p.startsWith(`${path}/`) && !p.slice(path.length + 1).includes('/');
        const ok = filters.every(([f, op, v]) => (op === 'in' ? v.includes(data[f]) : data[f] === v));
        if (inCol && ok) docs.push(snapOf(p, data));
      }
      return querySnap(docs);
    };
    return {
      path,
      doc: (id) => docRef(`${path}/${id}`),
      where: (field, op, v) => {
        if (op !== '==' && op !== 'in') throw new Error(`fake: toán tử ${op} chưa hỗ trợ`);
        return collectionRef(path, [...filters, [field, op, v]]);
      },
      get: async () => run(),
      _run: run,
    };
  }

  db.collection = (name) => collectionRef(name);

  db.runTransaction = (fn) => {
    const result = chain.then(async () => {
      const queued = [];
      const tx = {
        get: async (ref) => (ref._run ? ref._run() : snapOf(ref.path, store.get(ref.path))),
        // Đồng bộ như SDK thật: chỉ xếp hàng, trả tx, áp dụng khi commit.
        set(ref, data, opts) { queued.push(() => write(ref.path, 'set', data, opts)); return tx; },
        update(ref, data) { queued.push(() => write(ref.path, 'update', data)); return tx; },
      };
      const value = await fn(tx);
      if (nextCommitError) {
        const err = nextCommitError;
        nextCommitError = null;
        throw err;
      }
      queued.forEach((w) => w());
      return value;
    });
    chain = result.catch(() => {});
    return result;
  };

  return db;
}

module.exports = { createFakeFirestore };
