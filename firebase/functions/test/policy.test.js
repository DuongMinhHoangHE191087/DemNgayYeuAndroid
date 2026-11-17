'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const cfg = require('../src/config');
const policy = require('../src/policy');

const MB = 1024 * 1024;
const ID = '0f8fad5b-d9cb-469f-a165-70867728950e';
const image = (over = {}) => ({
  relationshipId: 'rel-1', memoryId: ID, kind: 'IMAGE', sizeBytes: MB, mimeType: 'image/jpeg', ...over,
});
const video = (over = {}) => ({
  relationshipId: 'rel-1', memoryId: ID, kind: 'VIDEO', sizeBytes: 5 * MB, mimeType: 'video/mp4', durationSeconds: 30, ...over,
});

test('publicIdFor: tiền tố cố định, không có thư mục hay tên người dùng', () => {
  assert.equal(policy.publicIdFor(ID), `inlove_mem_${ID}`);
});

test('ảnh: đúng 10 MB được nhận, vượt 1 byte thì bị từ chối', () => {
  assert.equal(policy.parseReserveInput(image({ sizeBytes: 10 * MB })).format, 'jpg');
  assert.throws(() => policy.parseReserveInput(image({ sizeBytes: 10 * MB + 1 })), { code: 'invalid-argument' });
});

test('dung lượng không hợp lệ bị từ chối', () => {
  for (const sizeBytes of [0, -1, 1.5, '100', null]) {
    assert.throws(() => policy.parseReserveInput(image({ sizeBytes })), { code: 'invalid-argument' }, String(sizeBytes));
  }
});

test('video: đúng 50 MB và 60 giây được nhận, vượt ngưỡng hoặc thiếu thời lượng thì bị từ chối', () => {
  assert.equal(policy.parseReserveInput(video({ sizeBytes: 50 * MB, durationSeconds: 60 })).durationSeconds, 60);
  assert.throws(() => policy.parseReserveInput(video({ sizeBytes: 50 * MB + 1 })), { code: 'invalid-argument' });
  assert.throws(() => policy.parseReserveInput(video({ durationSeconds: 61 })), { code: 'invalid-argument' });
  assert.throws(() => policy.parseReserveInput(video({ durationSeconds: 0 })), { code: 'invalid-argument' });
  assert.throws(() => policy.parseReserveInput(video({ durationSeconds: undefined })), { code: 'invalid-argument' });
});

test('kind chỉ nhận IMAGE hoặc VIDEO', () => {
  assert.throws(() => policy.parseReserveInput(image({ kind: 'AUDIO' })), { code: 'invalid-argument' });
});

test('id: memoryId là UUID viết thường, relationshipId không có ký tự lạ', () => {
  assert.throws(() => policy.parseReserveInput(image({ memoryId: ID.toUpperCase() })), { code: 'invalid-argument' });
  assert.throws(() => policy.parseReserveInput(image({ memoryId: 'not-a-uuid' })), { code: 'invalid-argument' });
  assert.throws(() => policy.parseReserveInput(image({ relationshipId: 'a b' })), { code: 'invalid-argument' });
  assert.throws(() => policy.parseReserveInput(image({ relationshipId: '' })), { code: 'invalid-argument' });
});

test('MIME: chỉ nhận đúng bảng, không lọt khoá kiểu prototype, không khớp nhầm loại', () => {
  assert.equal(policy.formatForMime('IMAGE', 'IMAGE/JPEG'), 'jpg');
  assert.equal(policy.formatForMime('IMAGE', 'image/heif'), 'heic');
  for (const mimeType of ['image/gif', 'constructor', '__proto__', 'toString', '']) {
    assert.throws(() => policy.formatForMime('IMAGE', mimeType), { code: 'invalid-argument' }, mimeType);
  }
  assert.throws(() => policy.formatForMime('IMAGE', 'video/mp4'), { code: 'invalid-argument' });
});

test('contentTypeOf: đúng bảng, định dạng lạ hoặc khoá prototype thì ném lỗi thường', () => {
  const table = {
    jpg: 'image/jpeg', png: 'image/png', webp: 'image/webp', heic: 'image/heic', mp4: 'video/mp4', mov: 'video/quicktime',
  };
  for (const [format, type] of Object.entries(table)) assert.equal(policy.contentTypeOf(format), type);
  for (const format of ['webm', 'gif', 'constructor']) {
    assert.throws(() => policy.contentTypeOf(format), (e) => e instanceof Error && !e.code, format);
  }
});

const pad = (buf) => Buffer.concat([buf, Buffer.alloc(32)]).subarray(0, 32);
const ftyp = (major, ...compat) => pad(Buffer.concat([
  Buffer.from([0, 0, 0, 0x20]), Buffer.from('ftyp'), Buffer.from(major), Buffer.alloc(4),
  ...compat.map((b) => Buffer.from(b)),
]));
const JPG = pad(Buffer.from([0xff, 0xd8, 0xff, 0xe0]));
const PNG = pad(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
const riff = (tag) => pad(Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from(tag)]));

test('sniffFamily: nhận đúng họ IMAGE và VIDEO theo bảng magic bytes', () => {
  assert.equal(policy.sniffFamily(JPG), 'IMAGE');
  assert.equal(policy.sniffFamily(PNG), 'IMAGE');
  assert.equal(policy.sniffFamily(riff('WEBP')), 'IMAGE');
  assert.equal(policy.sniffFamily(ftyp('heic')), 'IMAGE');
  assert.equal(policy.sniffFamily(ftyp('mif1', 'heic')), 'IMAGE');
  assert.equal(policy.sniffFamily(ftyp('isom')), 'VIDEO');
  assert.equal(policy.sniffFamily(ftyp('mp42')), 'VIDEO');
  assert.equal(policy.sniffFamily(ftyp('qt  ')), 'VIDEO');
  assert.equal(policy.sniffFamily(ftyp('M4V ')), 'VIDEO');
});

test('sniffFamily: avif, RIFF không phải WEBP, PDF, brand lạ, đầu vào hỏng đều trả null', () => {
  assert.equal(policy.sniffFamily(ftyp('avif')), null);
  assert.equal(policy.sniffFamily(ftyp('mif1', 'avif')), null);
  assert.equal(policy.sniffFamily(ftyp('heic', 'avis')), null);
  assert.equal(policy.sniffFamily(riff('WAVE')), null);
  assert.equal(policy.sniffFamily(pad(Buffer.from('%PDF-1.7'))), null);
  assert.equal(policy.sniffFamily(ftyp('zzzz')), null);
  for (const head of [Buffer.alloc(0), Buffer.from([0xff, 0xd8]), Buffer.from([0x89, 0x50, 0x4e]), undefined, null, 'ftyp']) {
    assert.equal(policy.sniffFamily(head), null, String(head));
  }
  assert.equal(policy.sniffFamily(Buffer.from([0xff, 0xd8, 0xff])), 'IMAGE');
});

test('resourceProblem: tệp khớp thì null; thiếu tệp, lệch dung lượng, vượt trần, sai content-type, sai họ thì có lý do', () => {
  const img = { kind: 'IMAGE', format: 'jpg', declaredBytes: 1000 };
  const vid = { kind: 'VIDEO', format: 'mp4', declaredBytes: 5 * MB };
  const goodImg = { bytes: 1000, contentType: 'image/jpeg', head: JPG };
  const goodVid = { bytes: 5 * MB, contentType: 'video/mp4', head: ftyp('isom') };
  assert.equal(policy.resourceProblem(goodImg, img), null);
  assert.equal(policy.resourceProblem(goodVid, vid), null);
  assert.match(policy.resourceProblem(null, img), /Không tìm thấy/);
  assert.match(policy.resourceProblem({ ...goodImg, bytes: 999 }, img), /không khớp khai báo/);
  assert.match(policy.resourceProblem({ ...goodImg, bytes: 0 }, { ...img, declaredBytes: 0 }), /vượt giới hạn/);
  const cap = policy.maxBytesOf('IMAGE');
  assert.equal(policy.resourceProblem({ ...goodImg, bytes: cap }, { ...img, declaredBytes: cap }), null);
  assert.match(policy.resourceProblem({ ...goodImg, bytes: cap + 1 }, { ...img, declaredBytes: cap + 1 }), /vượt giới hạn/);
  assert.match(policy.resourceProblem({ ...goodImg, contentType: 'image/png' }, img), /Định dạng/);
  assert.match(policy.resourceProblem({ ...goodImg, contentType: null }, img), /Định dạng/);
  assert.match(policy.resourceProblem({ ...goodVid, head: PNG }, vid), /Định dạng/);
  assert.match(policy.resourceProblem({ ...goodImg, head: ftyp('isom') }, img), /Định dạng/);
});

test('quotaProblem: đúng ngưỡng vẫn được, vượt ngưỡng thì từ chối', () => {
  const q = cfg.quotas;
  const usage = { userCount: 0, relationshipCount: 0, globalCount: 0, storedBytes: 0 };
  assert.equal(policy.quotaProblem(usage, MB), null);
  assert.equal(policy.quotaProblem({ ...usage, userCount: q.userDailyUploads - 1 }, MB), null);
  assert.equal(policy.quotaProblem({ ...usage, userCount: q.userDailyUploads }, MB)?.code, 'resource-exhausted');
  assert.equal(policy.quotaProblem({ ...usage, relationshipCount: q.relationshipDailyUploads }, MB)?.code, 'resource-exhausted');
  assert.equal(policy.quotaProblem({ ...usage, globalCount: q.globalDailyUploads }, MB)?.code, 'resource-exhausted');
  assert.equal(policy.quotaProblem({ ...usage, storedBytes: q.relationshipStoredBytes - MB }, MB), null);
  assert.equal(policy.quotaProblem({ ...usage, storedBytes: q.relationshipStoredBytes - MB + 1 }, MB)?.code, 'resource-exhausted');
});

const FREE_T = { userDailyUploads: 2, relationshipDailyUploads: 4, relationshipStoredBytes: 10 * MB };
const VIP_T = { userDailyUploads: 200, relationshipDailyUploads: 400, relationshipStoredBytes: 100 * MB };

test('limitsFor_freeAndVipEqualInV1: free bằng vip bằng cfg.quotas', () => {
  const q = cfg.quotas;
  const expected = {
    userDailyUploads: q.userDailyUploads,
    relationshipDailyUploads: q.relationshipDailyUploads,
    relationshipStoredBytes: q.relationshipStoredBytes,
  };
  assert.deepEqual(policy.limitsFor('free'), expected);
  assert.deepEqual(policy.limitsFor('vip'), expected);
});

test('unknownTier_fallsBackToFree: bậc lạ, rỗng, khoá prototype đều nhận hạn mức free, không bao giờ là vip', () => {
  const table = { free: FREE_T, vip: VIP_T };
  for (const tier of ['gold', '', undefined, null, 'VIP', 'constructor', '__proto__', 'toString', 42]) {
    assert.deepEqual(policy.limitsFor(tier, table), FREE_T, String(tier));
  }
  assert.deepEqual(policy.limitsFor('vip', table), VIP_T);
});

test('limitsFor_customTable_usedForVip: bảng truyền vào quyết định hạn mức; quotaProblem dùng đúng limits', () => {
  const table = { free: FREE_T, vip: VIP_T };
  assert.equal(policy.limitsFor('vip', table).userDailyUploads, 200);
  const usage = { userCount: 2, relationshipCount: 0, globalCount: 0, storedBytes: 0 };
  assert.equal(policy.quotaProblem(usage, MB, policy.limitsFor('free', table))?.code, 'resource-exhausted');
  assert.equal(policy.quotaProblem(usage, MB, policy.limitsFor('vip', table)), null);
  const stored = { ...usage, userCount: 0, storedBytes: 10 * MB };
  assert.equal(policy.quotaProblem(stored, MB, policy.limitsFor('free', table))?.code, 'resource-exhausted');
  assert.equal(policy.quotaProblem(stored, MB, policy.limitsFor('vip', table)), null);
});

test('dayKey theo UTC: 23:59 UTC vẫn là ngày đó, 00:00 UTC là ngày sau', () => {
  assert.equal(policy.dayKey(Date.UTC(2026, 9, 8, 23, 59)), '20261008');
  assert.equal(policy.dayKey(Date.UTC(2026, 9, 9, 0, 0)), '20261009');
});
