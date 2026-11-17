'use strict';

// Firestore giả trong bộ nhớ, đủ cho test của Functions: doc/collection/doc con, truy vấn where/orderBy/limit, transaction, batch.
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
    ref: docRef(path),
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

  // Xoá doc (không lỗi nếu doc chưa có, như SDK thật); vẫn tính là một lần ghi.
  function remove(path) {
    store.delete(path);
    db.writes += 1;
  }

  // Áp các ghi đã xếp hàng khi commit. Lần commit kế tiếp (transaction hoặc batch) reject nếu đã đặt failNextCommit.
  function commitWrites(queued) {
    if (nextCommitError) {
      const err = nextCommitError;
      nextCommitError = null;
      throw err;
    }
    queued.forEach((w) => w());
  }

  function docRef(path) {
    return {
      path,
      id: path.split('/').pop(),
      collection: (name) => collectionRef(`${path}/${name}`),
      get: async () => snapOf(path, store.get(path)),
      set: async (data, opts) => write(path, 'set', data, opts),
      update: async (data) => write(path, 'update', data),
      delete: async () => remove(path),
    };
  }

  // Truy vấn: where (==, in), orderBy (một trường, asc hoặc desc), limit. Mỗi lời gọi trả truy vấn mới.
  // ponytail: không orderBy thì giữ thứ tự chèn (Firestore thật sắp theo id); orderBy so sánh thô (số, chuỗi, Date).
  // Cần thứ tự hoặc so sánh đúng như Firestore thì dùng emulator.
  function collectionRef(path, filters = [], order = null, max = Infinity) {
    const run = () => {
      const hits = [];
      for (const [p, data] of store) {
        const inCol = p.startsWith(`${path}/`) && !p.slice(path.length + 1).includes('/');
        const ok = filters.every(([f, op, v]) => (op === 'in' ? v.includes(data[f]) : data[f] === v));
        // orderBy bỏ doc thiếu trường đó, như Firestore thật.
        if (inCol && ok && (!order || data[order.field] !== undefined)) hits.push([p, data]);
      }
      if (order) {
        const { field, sign } = order;
        hits.sort(([, a], [, b]) => sign * (a[field] < b[field] ? -1 : a[field] > b[field] ? 1 : 0));
      }
      return querySnap(hits.slice(0, max).map(([p, data]) => snapOf(p, data)));
    };
    return {
      path,
      doc: (id) => docRef(`${path}/${id}`),
      where: (field, op, v) => {
        if (op !== '==' && op !== 'in') throw new Error(`fake: toán tử ${op} chưa hỗ trợ`);
        return collectionRef(path, [...filters, [field, op, v]], order, max);
      },
      orderBy: (field, dir = 'asc') => {
        if (order) throw new Error('fake: chỉ hỗ trợ một orderBy');
        if (dir !== 'asc' && dir !== 'desc') throw new Error(`fake: chiều sắp xếp ${dir} chưa hỗ trợ`);
        return collectionRef(path, filters, { field, sign: dir === 'desc' ? -1 : 1 }, max);
      },
      limit: (n) => collectionRef(path, filters, order, n),
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
        delete(ref) { queued.push(() => remove(ref.path)); return tx; },
      };
      const value = await fn(tx);
      commitWrites(queued);
      return value;
    });
    chain = result.catch(() => {});
    return result;
  };

  // WriteBatch như SDK thật: set/delete chỉ xếp hàng, áp dụng khi commit; commit lỗi thì không ghi gì.
  // ponytail: commit hai lần sẽ áp lại (SDK thật ném lỗi); chưa có code nào làm vậy, thêm guard khi cần.
  db.batch = () => {
    const queued = [];
    const batch = {
      set(ref, data, opts) { queued.push(() => write(ref.path, 'set', data, opts)); return batch; },
      delete(ref) { queued.push(() => remove(ref.path)); return batch; },
      commit: async () => commitWrites(queued),
    };
    return batch;
  };

  return db;
}

module.exports = { createFakeFirestore };
