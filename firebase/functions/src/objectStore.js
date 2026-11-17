'use strict';

// Lớp mỏng quanh Cloudflare R2 (S3-compatible). Mọi khoá bí mật và việc ký URL đi qua đây; test tiêm fetchImpl giả.
// App chỉ nhận URL presigned hết hạn, gắn với đúng 1 key. Lỗi chỉ mang HTTP status, không URL/chữ ký. Không log.
const { createHmac, createHash } = require('node:crypto');

const MAX_EXPIRES_SECONDS = 604800;
const hmac = (key, data) => createHmac('sha256', key).update(data).digest();
const sha256Hex = (data) => createHash('sha256').update(data).digest('hex');

// RFC3986: encodeURIComponent chưa mã hoá !'()* nên mã hoá thêm.
const enc = (s) => encodeURIComponent(s).replace(/[!'()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);

// ponytail: tự ký SigV4 (~20 dòng) thay @aws-sdk; chỉ dùng query-presign, UNSIGNED-PAYLOAD.
function presign({ method, host, path, region, accessKeyId, secretAccessKey, nowMs, expiresInSeconds, headers = {} }) {
  if (!Number.isInteger(expiresInSeconds) || expiresInSeconds < 1 || expiresInSeconds > MAX_EXPIRES_SECONDS) {
    throw new RangeError(`expiresInSeconds phải là số nguyên trong [1, ${MAX_EXPIRES_SECONDS}]`);
  }
  const amzDate = new Date(nowMs).toISOString().replace(/[-:]|\.\d{3}/g, '');
  const day = amzDate.slice(0, 8);
  const scope = `${day}/${region}/s3/aws4_request`;

  // Header đã ký: tên viết thường, giá trị đã trim, thêm host, sắp theo tên.
  const signed = { ...Object.fromEntries(Object.entries(headers).map(([k, v]) => [k.toLowerCase(), String(v).trim()])), host };
  const names = Object.keys(signed).sort();
  const signedHeaders = names.join(';');

  const query = {
    'X-Amz-Algorithm': 'AWS4-HMAC-SHA256',
    'X-Amz-Credential': `${accessKeyId}/${scope}`,
    'X-Amz-Date': amzDate,
    'X-Amz-Expires': String(expiresInSeconds),
    'X-Amz-SignedHeaders': signedHeaders,
  };
  const canonicalQuery = Object.keys(query)
    .sort()
    .map((k) => `${enc(k)}=${enc(query[k])}`)
    .join('&');
  const canonicalPath = path.split('/').map(enc).join('/');

  const canonicalRequest = [
    method,
    canonicalPath,
    canonicalQuery,
    names.map((n) => `${n}:${signed[n]}\n`).join(''),
    signedHeaders,
    'UNSIGNED-PAYLOAD',
  ].join('\n');
  const stringToSign = ['AWS4-HMAC-SHA256', amzDate, scope, sha256Hex(canonicalRequest)].join('\n');
  const signingKey = [day, region, 's3', 'aws4_request'].reduce(hmac, `AWS4${secretAccessKey}`);
  const signature = hmac(signingKey, stringToSign).toString('hex');

  return `https://${host}${canonicalPath}?${canonicalQuery}&X-Amz-Signature=${signature}`;
}

function createObjectStore({ accountId, bucket, accessKeyId, secretAccessKey, fetchImpl = fetch, now = Date.now }) {
  // Chỉ nêu tên tham số thiếu, không bao giờ nêu giá trị.
  const missing = Object.entries({ accountId, bucket, accessKeyId, secretAccessKey })
    .filter(([, v]) => !v)
    .map(([k]) => k);
  if (missing.length) throw new Error(`Thiếu cấu hình R2: ${missing.join(', ')}`);

  const host = `${accountId}.r2.cloudflarestorage.com`;
  const sign = (method, key, expiresInSeconds, headers = {}) =>
    presign({ method, host, path: `/${bucket}/${key}`, region: 'auto', accessKeyId, secretAccessKey, nowMs: now(), expiresInSeconds, headers });

  return {
    // Ký cả content-type, content-length và If-None-Match: * (không ghi đè được tệp đã có).
    // overwrite (avatar: khoá cố định theo uid) bỏ If-None-Match; cacheControl thêm header cache-control ĐÃ KÝ.
    signUpload({ publicId, contentType, contentLength, expiresInSeconds, overwrite = false, cacheControl }) {
      const headers = { 'content-type': contentType, 'content-length': String(contentLength) };
      if (!overwrite) headers['if-none-match'] = '*';
      if (cacheControl) headers['cache-control'] = cacheControl;
      return { uploadUrl: sign('PUT', publicId, expiresInSeconds, headers), headers };
    },
    // Một Range GET 32 byte đầu (1 Class B). 200 là lỗi: không tải cả tệp vào RAM của Function.
    async fetchResource(publicId, _kind) {
      const range = { range: 'bytes=0-31' };
      const res = await fetchImpl(sign('GET', publicId, 60, range), { headers: range });
      if (res.status === 404) return null;
      if (res.status === 416) return { bytes: 0, contentType: null, head: Buffer.alloc(0) };
      if (res.status !== 206) throw new Error(`R2 GET ${res.status}`); // chỉ status, KHÔNG URL
      const total = Number(/\/(\d+)$/.exec(res.headers.get('content-range') ?? '')?.[1]);
      if (!Number.isFinite(total)) throw new Error('R2 GET 206 without Content-Range total');
      return { bytes: total, contentType: res.headers.get('content-type'), head: Buffer.from(await res.arrayBuffer()) };
    },
    // 404 coi như đã xoá (idempotent, an toàn khi retry).
    async destroy(publicId, _kind) {
      const res = await fetchImpl(sign('DELETE', publicId, 60), { method: 'DELETE' });
      if (res.status !== 204 && res.status !== 404) throw new Error(`R2 DELETE ${res.status}`);
    },
    deliveryUrl: (publicId, _kind, ttlSeconds, _format) => sign('GET', publicId, ttlSeconds),
  };
}

module.exports = { createObjectStore, presign };
