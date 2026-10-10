'use strict';

// Hợp đồng giữa app Android và Functions. App không import config.js, nên test này đọc mã Kotlin như văn bản
// và so với config.js. Đổi tên, đổi số hay đổi định dạng ở một phía mà quên phía kia thì test này báo ngay.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const cfg = require('../src/config');

const ROOT = path.join(__dirname, '..', '..', '..');
const readRepo = (...parts) => fs.readFileSync(path.join(ROOT, ...parts), 'utf8');
const MEDIA_KT = readRepo('app', 'src', 'main', 'java', 'com', 'example', 'domain', 'media', 'MemoryMedia.kt');
const REPO_KT = readRepo('app', 'src', 'main', 'java', 'com', 'example', 'data', 'media', 'FirebaseMemoryMediaRepository.kt');
const INDEX_JS = readRepo('firebase', 'functions', 'index.js');

// "10L * 1024 * 1024" -> số byte
function bytesConst(source, name) {
  const m = new RegExp(`${name} = (\\d+)L \\* 1024 \\* 1024`).exec(source);
  assert.ok(m, `không tìm thấy ${name}`);
  return Number(m[1]) * 1024 * 1024;
}

function intConst(source, name) {
  const m = new RegExp(`${name} = (\\d+)\\b`).exec(source);
  assert.ok(m, `không tìm thấy ${name}`);
  return Number(m[1]);
}

// mapOf("image/jpeg" to "jpg", ...) -> { 'image/jpeg': 'jpg', ... }
const mimeMap = (block) => Object.fromEntries(
  [...block.matchAll(/"((?:image|video)\/[^"]+)" to "([^"]+)"/g)].map((m) => [m[1], m[2]]),
);

test('public_id: tiền tố app và server dùng giống nhau', () => {
  assert.equal(/MEMORY_PUBLIC_ID_PREFIX = "([^"]+)"/.exec(MEDIA_KT)?.[1], cfg.publicIdPrefix);
});

test('giới hạn dung lượng và thời lượng: app khớp config.js', () => {
  assert.equal(bytesConst(MEDIA_KT, 'IMAGE_MAX_BYTES'), cfg.limits.imageMaxBytes);
  assert.equal(bytesConst(MEDIA_KT, 'VIDEO_MAX_BYTES'), cfg.limits.videoMaxBytes);
  assert.equal(intConst(MEDIA_KT, 'VIDEO_MAX_SECONDS'), cfg.limits.videoMaxSeconds);
});

test('định dạng upload: MIME trong app khớp mimeFormats của config.js (cả ảnh và video)', () => {
  const image = /IMAGE_FORMATS = mapOf\(([\s\S]*?)\)/.exec(MEDIA_KT);
  const video = /VIDEO_FORMATS = mapOf\(([^)]*)\)/.exec(MEDIA_KT);
  assert.ok(image && video, 'không tìm thấy IMAGE_FORMATS hoặc VIDEO_FORMATS');
  assert.deepEqual(mimeMap(image[1]), { ...cfg.mimeFormats.IMAGE });
  assert.deepEqual(mimeMap(video[1]), { ...cfg.mimeFormats.VIDEO });
});

test('region: app gọi Functions cùng region với config.js', () => {
  assert.equal(/const val REGION = "([^"]+)"/.exec(REPO_KT)?.[1], cfg.region);
});

test('callable: mọi hàm app gọi đều được server export', () => {
  for (const name of ['signMemoryUpload', 'confirmMemoryUpload', 'getMemoryMediaUrl']) {
    assert.match(REPO_KT, new RegExp(`"${name}"`), `app không gọi ${name}`);
    assert.match(INDEX_JS, new RegExp(`exports\\.${name} = onCall\\(`), `server không export ${name}`);
  }
});
