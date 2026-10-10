'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const cloudinarySdk = require('cloudinary');
const { createCloudinary, httpCodeOf } = require('../src/cloudinary');

const ID = '0f8fad5b-d9cb-469f-a165-70867728950e';
const PUBLIC_ID = `inlove_mem_${ID}`;
const CLOUD = 'inlove-test';
const API_KEY = '123456789012345';
const API_SECRET = 'test-api-secret';
const TOKEN_KEY = 'a'.repeat(64);

const config = {
  cloudName: CLOUD,
  apiKey: API_KEY,
  apiSecret: API_SECRET,
  tokenKey: TOKEN_KEY,
};

// SDK thật: dùng để kiểm tra chữ ký và URL phân phát (không cần mạng).
const make = (overrides = {}) => createCloudinary({ sdk: cloudinarySdk, ...config, ...overrides });

// SDK giả: ghi lại lệnh gọi, trả về kết quả do test quy định (không cần mạng).
function fakeSdk({ resource, destroy, rename } = {}) {
  const calls = [];
  const v2 = {
    config() {},
    utils: cloudinarySdk.v2.utils,
    url: cloudinarySdk.v2.url,
    api: {
      resource: async (id, opts) => {
        calls.push({ op: 'resource', id, opts });
        return resource(id, opts);
      },
    },
    uploader: {
      destroy: async (id, opts) => {
        calls.push({ op: 'destroy', id, opts });
        return destroy(id, opts);
      },
      rename: async (from, to, opts) => {
        calls.push({ op: 'rename', from, to, opts });
        return rename(from, to, opts);
      },
    },
  };
  return { sdk: { v2 }, calls };
}

const sha1Hex = (text) => crypto.createHash('sha1').update(text).digest('hex');

test('signUpload: chữ ký khớp quy tắc của Cloudinary (tham số sắp xếp, nối &, thêm secret, SHA-1)', () => {
  const signed = make().signUpload({ publicId: PUBLIC_ID, kind: 'IMAGE', format: 'jpg', timestampSeconds: 1700000000 });
  const toSign = `allowed_formats=jpg&overwrite=false&public_id=${PUBLIC_ID}&timestamp=1700000000&type=authenticated`;
  assert.equal(signed.fields.signature, sha1Hex(toSign + API_SECRET));
  assert.equal(signed.fields.api_key, API_KEY);
  assert.equal(signed.fields.type, 'authenticated');
  assert.equal(signed.fields.overwrite, 'false');
  assert.equal(signed.fields.allowed_formats, 'jpg');
  assert.equal(signed.uploadUrl, `https://api.cloudinary.com/v1_1/${CLOUD}/image/upload`);
});

test('signUpload: video dùng đường dẫn video; secret không xuất hiện trong kết quả trả về app', () => {
  const signed = make().signUpload({ publicId: PUBLIC_ID, kind: 'VIDEO', format: 'mp4', timestampSeconds: 1 });
  assert.equal(signed.uploadUrl, `https://api.cloudinary.com/v1_1/${CLOUD}/video/upload`);
  assert.ok(!JSON.stringify(signed).includes(API_SECRET));
});

test('deliveryUrl (token): đường dẫn đúng, không có chữ ký s--, token hết hạn sau đúng TTL, HMAC khớp khoá', () => {
  const ttl = 900;
  const before = Math.floor(Date.now() / 1000);
  const url = new URL(make().deliveryUrl(PUBLIC_ID, 'IMAGE', ttl));
  const after = Math.floor(Date.now() / 1000);
  assert.equal(url.pathname, `/${CLOUD}/image/authenticated/${PUBLIC_ID}`);
  assert.doesNotMatch(url.pathname, /\/s--/);

  const match = /^exp=(\d+)~hmac=([0-9a-f]{64})$/.exec(url.searchParams.get('__cld_token__') ?? '');
  assert.ok(match, 'thiếu token');
  const exp = Number(match[1]);
  assert.ok(exp >= before + ttl - 1 && exp <= after + ttl + 1, `exp ${exp} ngoài khoảng TTL`);

  // Oracle: HMAC-SHA256 với khoá giải mã hex; thông điệp "exp=<exp>~url=<đường dẫn, '/' thành %2f>".
  const escapedPath = url.pathname.replace(/\//g, '%2f');
  const expected = crypto.createHmac('sha256', Buffer.from(TOKEN_KEY, 'hex'))
    .update(`exp=${exp}~url=${escapedPath}`)
    .digest('hex');
  assert.equal(match[2], expected);
});

test('createCloudinary: từ chối cấu hình thiếu hoặc sai; cấu hình đủ thì được nhận', () => {
  assert.throws(() => make({ cloudName: '' }), /cloud name/);
  assert.throws(() => make({ apiSecret: undefined }), /API secret/);
  assert.throws(() => make({ tokenKey: undefined }), /TOKEN_KEY/);
  assert.throws(() => make({ tokenKey: 'a'.repeat(30) }), /hex/);
  assert.throws(() => make({ tokenKey: 'z'.repeat(64) }), /hex/);
  assert.doesNotThrow(() => make());
});

test('fetchResource: 404 trả về null, lỗi khác thì ném ra; gọi đúng loại tài nguyên và kiểu authenticated', async () => {
  const missing = fakeSdk({ resource: async () => { throw { error: { http_code: 404, message: 'Resource not found' } }; } });
  assert.equal(await make({ sdk: missing.sdk }).fetchResource(PUBLIC_ID, 'VIDEO'), null);
  assert.deepEqual(missing.calls[0].opts, { resource_type: 'video', type: 'authenticated' });

  const broken = fakeSdk({ resource: async () => { throw { error: { http_code: 500, message: 'boom' } }; } });
  await assert.rejects(make({ sdk: broken.sdk }).fetchResource(PUBLIC_ID, 'IMAGE'), (err) => httpCodeOf(err) === 500);
});

test('destroy: "ok" và "not found" đều coi là đã xoá; kết quả khác thì báo lỗi để sweeper thử lại', async () => {
  for (const result of ['ok', 'not found']) {
    const fake = fakeSdk({ destroy: async () => ({ result }) });
    await make({ sdk: fake.sdk }).destroy(PUBLIC_ID, 'VIDEO');
    assert.deepEqual(fake.calls[0].opts, { resource_type: 'video', type: 'authenticated', invalidate: true }, result);
  }
  const odd = fakeSdk({ destroy: async () => ({ result: 'error' }) });
  await assert.rejects(make({ sdk: odd.sdk }).destroy(PUBLIC_ID, 'IMAGE'), /error/);
});

test('moveToPrivate: đổi từ upload sang authenticated, không ghi đè, xoá cache CDN', async () => {
  const fake = fakeSdk({ rename: async (from, to) => ({ public_id: to }) });
  await make({ sdk: fake.sdk }).moveToPrivate('inlove/old', PUBLIC_ID, 'IMAGE');
  assert.deepEqual(fake.calls[0], {
    op: 'rename',
    from: 'inlove/old',
    to: PUBLIC_ID,
    opts: { resource_type: 'image', type: 'upload', to_type: 'authenticated', overwrite: false, invalidate: true },
  });
});

test('httpCodeOf: đọc được cả hai dạng lỗi của SDK', () => {
  assert.equal(httpCodeOf({ error: { http_code: 404 } }), 404);
  assert.equal(httpCodeOf({ http_code: 420 }), 420);
  assert.equal(httpCodeOf(new Error('x')), undefined);
});
