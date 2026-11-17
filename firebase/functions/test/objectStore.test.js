'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { createObjectStore, presign } = require('../src/objectStore');

// Khoá mẫu trong tài liệu công khai của AWS, không phải bí mật.
const AWS_KEY = 'AKIAIOSFODNN7EXAMPLE';
const AWS_SECRET = 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY';

const ACCOUNT = 'acc123';
const BUCKET = 'inlove-media';
const KEY_ID = 'test-access-key-id';
const SECRET = 'test-secret-access-key';
const PUBLIC_ID = 'inlove_mem_0f8fad5b-d9cb-469f-a165-70867728950e';
const NOW_MS = Date.UTC(2026, 9, 21, 8, 30, 0);

const make = (overrides = {}) =>
  createObjectStore({
    accountId: ACCOUNT,
    bucket: BUCKET,
    accessKeyId: KEY_ID,
    secretAccessKey: SECRET,
    now: () => NOW_MS,
    ...overrides,
  });

const parse = (url) => new URL(url);
const sigOf = (url) => parse(url).searchParams.get('X-Amz-Signature');

// fetch giả: ghi lại lệnh gọi, trả về Response do test quy định (không cần mạng).
function fakeFetch(response) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init });
    return response;
  };
  fn.calls = calls;
  return fn;
}

function assertSafeError(err) {
  assert.ok(!err.message.includes('X-Amz-Signature'));
  assert.ok(!err.message.includes(SECRET));
  assert.ok(!err.message.includes(KEY_ID));
  assert.ok(!err.message.includes('https://'));
  return true;
}

test('presign: khớp vector mẫu AWS (KAT)', () => {
  const url = presign({
    method: 'GET',
    host: 'examplebucket.s3.amazonaws.com',
    path: '/test.txt',
    region: 'us-east-1',
    accessKeyId: AWS_KEY,
    secretAccessKey: AWS_SECRET,
    nowMs: Date.UTC(2013, 4, 24, 0, 0, 0),
    expiresInSeconds: 86400,
  });
  assert.equal(
    url,
    'https://examplebucket.s3.amazonaws.com/test.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256' +
      '&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request' +
      '&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host' +
      '&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404',
  );
});

test('presign: từ chối hạn ngoài [1, 604800] hoặc không nguyên', () => {
  for (const expiresInSeconds of [0, 604801, 1.5]) {
    assert.throws(
      () =>
        presign({
          method: 'GET',
          host: 'h.example.com',
          path: '/a',
          region: 'auto',
          accessKeyId: KEY_ID,
          secretAccessKey: SECRET,
          nowMs: NOW_MS,
          expiresInSeconds,
        }),
      RangeError,
    );
  }
});

test('signUpload: ký content-type, content-length, if-none-match', () => {
  const store = make();
  const input = { publicId: PUBLIC_ID, contentType: 'image/jpeg', contentLength: 12345, expiresInSeconds: 7200 };
  const { uploadUrl, headers } = store.signUpload(input);
  const u = parse(uploadUrl);
  assert.equal(u.host, `${ACCOUNT}.r2.cloudflarestorage.com`);
  assert.equal(u.pathname, `/${BUCKET}/${PUBLIC_ID}`);
  assert.ok(uploadUrl.includes('X-Amz-Expires=7200'));
  assert.ok(uploadUrl.includes('X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost%3Bif-none-match'));
  assert.deepEqual(headers, { 'content-type': 'image/jpeg', 'content-length': '12345', 'if-none-match': '*' });

  // Xác định (now được tiêm) và đổi content-type / content-length thì đổi chữ ký.
  assert.equal(sigOf(store.signUpload(input).uploadUrl), sigOf(uploadUrl));
  assert.notEqual(sigOf(store.signUpload({ ...input, contentType: 'image/png' }).uploadUrl), sigOf(uploadUrl));
  assert.notEqual(sigOf(store.signUpload({ ...input, contentLength: 12346 }).uploadUrl), sigOf(uploadUrl));
});

test('signUpload: overwrite bỏ if-none-match, cacheControl được KÝ, mặc định vẫn ký if-none-match', () => {
  const store = make();
  const input = { publicId: PUBLIC_ID, contentType: 'image/jpeg', contentLength: 100, expiresInSeconds: 600 };
  const cc = 'public, max-age=31536000, immutable';
  const { uploadUrl, headers } = store.signUpload({ ...input, overwrite: true, cacheControl: cc });
  assert.deepEqual(headers, { 'content-type': 'image/jpeg', 'content-length': '100', 'cache-control': cc });
  assert.ok(uploadUrl.includes('X-Amz-SignedHeaders=cache-control%3Bcontent-length%3Bcontent-type%3Bhost&'));

  // Chỉ overwrite: không cache-control. Chỉ cacheControl: vẫn if-none-match.
  assert.deepEqual(store.signUpload({ ...input, overwrite: true }).headers, { 'content-type': 'image/jpeg', 'content-length': '100' });
  const only = store.signUpload({ ...input, cacheControl: cc });
  assert.equal(only.headers['if-none-match'], '*');
  assert.equal(only.headers['cache-control'], cc);

  // Đổi cache-control thì đổi chữ ký; mặc định giữ nguyên chữ ký cũ.
  assert.notEqual(sigOf(store.signUpload({ ...input, overwrite: true, cacheControl: 'no-store' }).uploadUrl), sigOf(uploadUrl));
  const plain = store.signUpload(input);
  assert.deepEqual(plain.headers, { 'content-type': 'image/jpeg', 'content-length': '100', 'if-none-match': '*' });
  assert.ok(plain.uploadUrl.includes('X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost%3Bif-none-match'));
});

test('fetchResource: 206 trả bytes tổng, content-type và head', async () => {
  const body = Buffer.from([0xff, 0xd8, 0xff, 0xe0]);
  const fetchImpl = fakeFetch(
    new Response(body, { status: 206, headers: { 'content-range': 'bytes 0-31/1234', 'content-type': 'image/jpeg' } }),
  );
  const out = await make({ fetchImpl }).fetchResource(PUBLIC_ID, 'IMAGE');
  assert.equal(out.bytes, 1234);
  assert.equal(out.contentType, 'image/jpeg');
  assert.deepEqual(out.head, body);

  const [{ url, init }] = fetchImpl.calls;
  assert.ok(url.includes('X-Amz-SignedHeaders=host%3Brange'));
  assert.equal(init.headers.range, 'bytes=0-31');
});

test('fetchResource: 404 -> null, 416 -> rỗng', async () => {
  const none = await make({ fetchImpl: fakeFetch(new Response(null, { status: 404 })) }).fetchResource(PUBLIC_ID, 'IMAGE');
  assert.equal(none, null);
  const empty = await make({ fetchImpl: fakeFetch(new Response(null, { status: 416 })) }).fetchResource(PUBLIC_ID, 'IMAGE');
  assert.deepEqual(empty, { bytes: 0, contentType: null, head: Buffer.alloc(0) });
});

test('fetchResource: 200, 500, 206 thiếu Content-Range đều ném lỗi không lộ URL/chữ ký', async () => {
  const cases = [
    [new Response('x', { status: 200 }), 'R2 GET 200'],
    [new Response('x', { status: 500 }), 'R2 GET 500'],
    [new Response('x', { status: 206 }), 'R2 GET 206 without Content-Range total'],
  ];
  for (const [res, message] of cases) {
    await assert.rejects(make({ fetchImpl: fakeFetch(res) }).fetchResource(PUBLIC_ID, 'IMAGE'), (err) => {
      assert.equal(err.message, message);
      return assertSafeError(err);
    });
  }
});

test('destroy: 204 và 404 là thành công, DELETE; 403 ném lỗi', async () => {
  for (const status of [204, 404]) {
    const fetchImpl = fakeFetch(new Response(null, { status }));
    await make({ fetchImpl }).destroy(PUBLIC_ID, 'IMAGE');
    assert.equal(fetchImpl.calls[0].init.method, 'DELETE');
    assert.ok(fetchImpl.calls[0].url.includes('X-Amz-Expires=60'));
  }
  await assert.rejects(make({ fetchImpl: fakeFetch(new Response(null, { status: 403 })) }).destroy(PUBLIC_ID, 'IMAGE'), (err) => {
    assert.equal(err.message, 'R2 DELETE 403');
    return assertSafeError(err);
  });
});

test('deliveryUrl: chỉ ký host, hạn theo ttl, key trong đường dẫn', () => {
  const url = make().deliveryUrl(PUBLIC_ID, 'IMAGE', 900, 'jpg');
  const u = parse(url);
  assert.equal(u.host, `${ACCOUNT}.r2.cloudflarestorage.com`);
  assert.equal(u.pathname, `/${BUCKET}/${PUBLIC_ID}`);
  assert.ok(url.includes('X-Amz-Expires=900'));
  assert.ok(url.includes('X-Amz-SignedHeaders=host&'));
});

// Canh quyết định HEIC giao nguyên bản (DEC-272): máy chủ không chuyển đổi định dạng.
const queryNames = (url) => [...parse(url).searchParams.keys()];

test('deliveryUrl_heic_servedAsIs: HEIC giống hệt JPG, chỉ có tham số X-Amz-*', () => {
  const store = make();
  const heic = store.deliveryUrl(PUBLIC_ID, 'IMAGE', 900, 'heic');
  assert.equal(heic, store.deliveryUrl(PUBLIC_ID, 'IMAGE', 900, 'jpg'));
  assert.ok(queryNames(heic).every((n) => n.startsWith('X-Amz-')), queryNames(heic).join());
});

test('deliveryUrl_jpg_samePathAndNoTransformQuery: đường dẫn /<bucket>/<publicId>, không tham số biến đổi', () => {
  const url = make().deliveryUrl(PUBLIC_ID, 'IMAGE', 900, 'jpg');
  assert.equal(parse(url).pathname, `/${BUCKET}/${PUBLIC_ID}`);
  assert.ok(queryNames(url).every((n) => n.startsWith('X-Amz-')), queryNames(url).join());
});

test('createObjectStore: thiếu cấu hình ném lỗi nêu tên tham số, không nêu giá trị', () => {
  assert.throws(
    () => make({ secretAccessKey: '' }),
    (err) => {
      assert.ok(err.message.includes('secretAccessKey'));
      assert.ok(!err.message.includes(KEY_ID));
      assert.ok(!err.message.includes(ACCOUNT));
      assert.ok(!err.message.includes(BUCKET));
      return true;
    },
  );
});
