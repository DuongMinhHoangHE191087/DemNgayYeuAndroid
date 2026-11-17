'use strict';

// src/avatar.js: kiểm đầu vào, khoá cố định theo uid, URL công khai có ?v=, hạn ký 600 giây, bucket chưa cấu hình.
const test = require('node:test');
const assert = require('node:assert/strict');
const cfg = require('../src/config');
const { createObjectStore } = require('../src/objectStore');
const avatar = require('../src/avatar');

const NOW = Date.UTC(2026, 9, 22, 8, 0, 0);
const UID = 'uid_A-1';
const BASE = 'https://avatars.example.com';
const OK = { contentType: 'image/jpeg', contentLength: 1000 };

function fakeStore() {
  const signCalls = [];
  const destroyed = [];
  return {
    signCalls,
    destroyed,
    signUpload: (args) => { signCalls.push(args); return { uploadUrl: 'https://upload.test', headers: { 'content-type': 'image/jpeg' } }; },
    destroy: async (key) => { destroyed.push(key); },
  };
}

test('parseSignInput: chỉ image/jpeg, độ dài nguyên trong [1, avatarMaxBytes]', () => {
  assert.deepEqual(avatar.parseSignInput(OK), OK);
  assert.deepEqual(avatar.parseSignInput({ ...OK, contentLength: cfg.limits.avatarMaxBytes }), { ...OK, contentLength: cfg.limits.avatarMaxBytes });
  const bad = [
    undefined, null, {}, { ...OK, contentType: 'image/png' }, { ...OK, contentType: 'IMAGE/JPEG' },
    { ...OK, contentLength: 0 }, { ...OK, contentLength: -1 }, { ...OK, contentLength: 1.5 }, { ...OK, contentLength: '1000' },
    { ...OK, contentLength: cfg.limits.avatarMaxBytes + 1 }, { ...OK, contentLength: NaN }, { contentType: 'image/jpeg' },
  ];
  for (const data of bad) {
    assert.throws(() => avatar.parseSignInput(data), (e) => e.code === 'invalid-argument', JSON.stringify(data));
  }
});

test('config: giới hạn, tiền tố và rate limit của avatar', () => {
  assert.equal(cfg.limits.avatarMaxBytes, 1024 * 1024);
  assert.equal(cfg.avatarPublicIdPrefix, 'inlove_avt_');
  assert.deepEqual({ ...cfg.rateLimits.signAvatarUpload }, { max: 10, windowMs: 3600000 });
  assert.ok(Object.isFrozen(cfg.rateLimits.signAvatarUpload));
});

test('isConfigured: bucket khác rỗng VÀ base URL https', () => {
  assert.equal(avatar.isConfigured({ bucket: 'b', baseUrl: BASE }), true);
  assert.equal(avatar.isConfigured({ bucket: 'b', baseUrl: `${BASE}/avatars/` }), true);
  for (const c of [{ bucket: '', baseUrl: BASE }, { bucket: 'b', baseUrl: '' }, { bucket: 'b', baseUrl: 'http://x.com' },
    { bucket: 'b', baseUrl: 'ftp://x.com' }, { bucket: 'b', baseUrl: 'avatars.example.com' }, {},
    // `?` hoặc `#` trong base URL làm `sign` ghép ra URL công khai sai (`https://x.com?q=1/inlove_avt_<uid>?v=…`).
    { bucket: 'b', baseUrl: 'https://x.com?q=1' }, { bucket: 'b', baseUrl: 'https://x.com/a#frag' },
    { bucket: 'b', baseUrl: 'https://x.com/a b' }]) {
    assert.equal(avatar.isConfigured(c), false, JSON.stringify(c));
  }
});

test('sign: khoá inlove_avt_<uid>, ghi đè + cache-control immutable, hạn 600 giây, URL công khai có ?v=', () => {
  const store = fakeStore();
  const out = avatar.sign({ store, uid: UID, input: OK, baseUrl: `${BASE}//`, nowMs: NOW });
  assert.deepEqual(store.signCalls, [{
    publicId: 'inlove_avt_uid_A-1', contentType: 'image/jpeg', contentLength: 1000, expiresInSeconds: 600,
    overwrite: true, cacheControl: 'public, max-age=31536000, immutable',
  }]);
  assert.deepEqual(out, {
    uploadUrl: 'https://upload.test',
    headers: { 'content-type': 'image/jpeg' },
    publicId: 'inlove_avt_uid_A-1',
    publicUrl: `${BASE}/inlove_avt_uid_A-1?v=${NOW}`,
  });
});

test('sign: uid sai định dạng bị từ chối trước khi ký', () => {
  for (const uid of ['', 'a/b', 'a b', '../x', 'é', 'a'.repeat(129), undefined]) {
    const store = fakeStore();
    assert.throws(() => avatar.sign({ store, uid, input: OK, baseUrl: BASE, nowMs: NOW }), (e) => e.code === 'invalid-argument', String(uid));
    assert.equal(store.signCalls.length, 0);
  }
  assert.doesNotThrow(() => avatar.sign({ store: fakeStore(), uid: 'a'.repeat(128), input: OK, baseUrl: BASE, nowMs: NOW }));
});

test('sign: chưa cấu hình (store rỗng) thì failed-precondition avatars-not-configured', () => {
  assert.throws(
    () => avatar.sign({ store: null, uid: UID, input: OK, baseUrl: BASE, nowMs: NOW }),
    (e) => e.code === 'failed-precondition' && e.message === 'avatars-not-configured' && e.details.reason === 'avatars-not-configured',
  );
});

test('sign với objectStore thật: URL hạn 600 giây, ký cache-control, không ký if-none-match', () => {
  const store = createObjectStore({ accountId: 'acc', bucket: 'avt', accessKeyId: 'k', secretAccessKey: 's', now: () => NOW });
  const { uploadUrl, headers } = avatar.sign({ store, uid: UID, input: OK, baseUrl: BASE, nowMs: NOW });
  assert.ok(uploadUrl.includes('X-Amz-Expires=600'));
  assert.ok(uploadUrl.includes('X-Amz-SignedHeaders=cache-control%3Bcontent-length%3Bcontent-type%3Bhost'));
  assert.equal(headers['cache-control'], 'public, max-age=31536000, immutable');
  assert.equal('if-none-match' in headers, false);
});

test('purge: xoá đúng khoá của uid; chưa cấu hình hoặc uid lạ thì không làm gì', async () => {
  const store = fakeStore();
  await avatar.purge({ store, uid: UID });
  assert.deepEqual(store.destroyed, ['inlove_avt_uid_A-1']);

  await avatar.purge({ store: null, uid: UID });
  await avatar.purge({ store, uid: 'a/b' });
  assert.deepEqual(store.destroyed, ['inlove_avt_uid_A-1']);
});
