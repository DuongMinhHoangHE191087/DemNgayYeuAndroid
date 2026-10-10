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

test('resourceProblem: tệp khớp mọi điều kiện thì không có lỗi', () => {
  const expected = { publicId: `inlove_mem_${ID}`, kind: 'IMAGE', format: 'jpg' };
  const good = { public_id: expected.publicId, type: 'authenticated', resource_type: 'image', format: 'jpg', bytes: 1000 };
  assert.equal(policy.resourceProblem(good, expected), null);
  assert.equal(policy.resourceProblem({ ...good, bytes: 10 * MB }, expected), null);
  assert.match(policy.resourceProblem(null, expected), /Không tìm thấy/);
  assert.match(policy.resourceProblem({ ...good, public_id: 'other' }, expected), /public_id/);
  assert.match(policy.resourceProblem({ ...good, type: 'upload' }, expected), /riêng tư/);
  assert.match(policy.resourceProblem({ ...good, resource_type: 'video' }, expected), /Loại tài nguyên/);
  assert.match(policy.resourceProblem({ ...good, format: 'png' }, expected), /Định dạng/);
  assert.match(policy.resourceProblem({ ...good, bytes: 0 }, expected), /Dung lượng/);
  assert.match(policy.resourceProblem({ ...good, bytes: 10 * MB + 1 }, expected), /Dung lượng/);
});

test('resourceProblem: video phải có thời lượng, không quá 61 giây (dung sai 1 giây do làm tròn)', () => {
  const expected = { publicId: `inlove_mem_${ID}`, kind: 'VIDEO', format: 'mp4' };
  const clip = { public_id: expected.publicId, type: 'authenticated', resource_type: 'video', format: 'mp4', bytes: 1000 };
  assert.equal(policy.resourceProblem({ ...clip, duration: 60.5 }, expected), null);
  assert.match(policy.resourceProblem({ ...clip, duration: 61.5 }, expected), /thời lượng/);
  assert.match(policy.resourceProblem({ ...clip }, expected), /thời lượng/);
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

test('dayKey theo UTC: 23:59 UTC vẫn là ngày đó, 00:00 UTC là ngày sau', () => {
  assert.equal(policy.dayKey(Date.UTC(2026, 9, 8, 23, 59)), '20261008');
  assert.equal(policy.dayKey(Date.UTC(2026, 9, 9, 0, 0)), '20261009');
});
