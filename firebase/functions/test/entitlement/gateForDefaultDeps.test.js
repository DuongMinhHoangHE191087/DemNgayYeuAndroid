'use strict';

// M-84: gateFor(name, uid) không truyền deps (đúng cách index.js gọi) phải dựng deps THẬT bằng makeRealDeps(), tức là đọc Firestore qua getFirestore().
// Giả getFirestore trong require.cache TRƯỚC khi nạp runtime.js (nó destructure lúc nạp); file riêng nên chạy trong process riêng của `node --test`.
const test = require('node:test');
const assert = require('node:assert/strict');
const { makeDeps } = require('./depsBuilder');

const deps = makeDeps();
require.cache[require.resolve('firebase-admin/firestore')].exports = { getFirestore: () => deps.db };
const runtime = require('../../src/entitlement/runtime');

test('gateFor_withoutDeps_readsFirestoreThroughRealDeps', async () => {
  assert.deepEqual(await runtime.gateFor('signMemoryUpload', 'uid-none'), { tier: 'free' });
});
