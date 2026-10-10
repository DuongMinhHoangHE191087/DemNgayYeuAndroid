'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { parseLegacyUrl, planLegacyMemory } = require('../src/legacy');

const CLOUD = 'inlove-test';
const ID = '0f8fad5b-d9cb-469f-a165-70867728950e';
const TARGET = `inlove_mem_${ID}`;
const legacy = (path) => `https://res.cloudinary.com/${CLOUD}/${path}`;

test('parseLegacyUrl: bỏ phiên bản, biến đổi và phần mở rộng, giữ thư mục của public_id', () => {
  assert.deepEqual(parseLegacyUrl(legacy('image/upload/v1700000000/inlove/abc.jpg')),
    { cloudName: CLOUD, resourceType: 'image', kind: 'IMAGE', publicId: 'inlove/abc' });
  assert.deepEqual(parseLegacyUrl(legacy('video/upload/w_720,q_auto/v12/clip.mp4')),
    { cloudName: CLOUD, resourceType: 'video', kind: 'VIDEO', publicId: 'clip' });
  assert.deepEqual(parseLegacyUrl(legacy('image/upload/c_fill,w_200/a.b.jpg')),
    { cloudName: CLOUD, resourceType: 'image', kind: 'IMAGE', publicId: 'a.b' });
  assert.deepEqual(parseLegacyUrl(legacy('image/upload/noext')),
    { cloudName: CLOUD, resourceType: 'image', kind: 'IMAGE', publicId: 'noext' });
});

test('parseLegacyUrl: từ chối link không phải Cloudinary public cần chuyển', () => {
  for (const url of [
    `http://res.cloudinary.com/${CLOUD}/image/upload/a.jpg`,
    `https://example.com/${CLOUD}/image/upload/a.jpg`,
    legacy('image/authenticated/s--abc--/a.jpg'),
    legacy('raw/upload/a.txt'),
    legacy('image/upload/v1/'),
    legacy('image/upload/.jpg'),
    'content://media/external/images/1',
    '/data/user/0/file.jpg',
    undefined,
  ]) {
    assert.equal(parseLegacyUrl(url), null, String(url));
  }
});

const memoryOf = (over = {}) => ({ mediaType: 'IMAGE', photoUri: '', videoUri: '', ...over });

test('planLegacyMemory: ảnh public của cloud này -> migrate, đổi public_id và xoá link cũ', () => {
  const url = legacy('image/upload/v1/inlove/abc.jpg');
  const plan = planLegacyMemory({
    memoryId: ID,
    memory: memoryOf({ photoUri: url, cloudinaryUrl: url, isCloudinaryStored: true }),
    cloudName: CLOUD,
  });
  assert.equal(plan.action, 'migrate');
  assert.equal(plan.from.publicId, 'inlove/abc');
  assert.equal(plan.to, TARGET);
  assert.deepEqual(plan.patch, { cloudinaryPublicId: TARGET, photoUri: '', cloudinaryUrl: null, isCloudinaryStored: null });
});

test('planLegacyMemory: video public -> migrate kiểu VIDEO và xoá videoUri', () => {
  const plan = planLegacyMemory({
    memoryId: ID,
    memory: memoryOf({ mediaType: 'VIDEO', videoUri: legacy('video/upload/v2/clip.mp4') }),
    cloudName: CLOUD,
  });
  assert.equal(plan.action, 'migrate');
  assert.equal(plan.kind, 'VIDEO');
  assert.deepEqual(plan.patch, { cloudinaryPublicId: TARGET, videoUri: '' });
});

test('planLegacyMemory: ảnh preset của app được giữ nguyên', () => {
  const preset = 'https://images.example.com/preset/cat.jpg';
  const plan = planLegacyMemory({
    memoryId: ID,
    memory: memoryOf({ photoUri: preset }),
    cloudName: CLOUD,
    presetUrls: new Set([preset]),
  });
  assert.equal(plan.action, 'clear');
  assert.deepEqual(plan.patch, {});
});

test('planLegacyMemory: đường dẫn cục bộ và link không rõ nguồn bị bỏ khỏi memory', () => {
  const plan = planLegacyMemory({
    memoryId: ID,
    memory: memoryOf({ photoUri: '/data/user/0/app/files/x.jpg' }),
    cloudName: CLOUD,
  });
  assert.equal(plan.action, 'clear');
  assert.deepEqual(plan.patch, { photoUri: '' });
});

test('planLegacyMemory: link thuộc cloud khác thì bỏ qua, không đụng tới', () => {
  const plan = planLegacyMemory({
    memoryId: ID,
    memory: memoryOf({ photoUri: 'https://res.cloudinary.com/other-cloud/image/upload/a.jpg' }),
    cloudName: CLOUD,
  });
  assert.equal(plan.action, 'skip');
});

test('planLegacyMemory: memory đã theo mô hình mới thì giữ nguyên, không chuyển lại', () => {
  const plan = planLegacyMemory({ memoryId: ID, memory: memoryOf({ cloudinaryPublicId: TARGET }), cloudName: CLOUD });
  assert.equal(plan.action, 'keep');
  assert.deepEqual(plan.patch, {});
});

test('planLegacyMemory: public_id cũ không còn link vẫn được chuyển như tệp của cloud này', () => {
  const plan = planLegacyMemory({
    memoryId: ID,
    memory: memoryOf({ mediaType: 'VIDEO', cloudinaryPublicId: 'inlove/clip' }),
    cloudName: CLOUD,
  });
  assert.equal(plan.action, 'migrate');
  assert.equal(plan.kind, 'VIDEO');
  assert.equal(plan.from.publicId, 'inlove/clip');
  assert.equal(plan.from.resourceType, 'video');
  assert.deepEqual(plan.patch, { cloudinaryPublicId: TARGET });
});

test('planLegacyMemory: preset nằm trên chính cloud này vẫn không bị chuyển sang riêng tư (dùng chung cho mọi người)', () => {
  const preset = legacy('image/upload/v1/presets/cat.jpg');
  const plan = planLegacyMemory({
    memoryId: ID,
    memory: memoryOf({ photoUri: preset, cloudinaryUrl: preset }),
    cloudName: CLOUD,
    presetUrls: new Set([preset]),
  });
  assert.equal(plan.action, 'clear');
  assert.deepEqual(plan.patch, { cloudinaryUrl: null });
});
