'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { FieldValue } = require('firebase-admin/firestore');
const { legacyProblem, fingerprint, toUpdate, MEMORY_PATH } = require('../scripts/migrate-legacy-media');

const MB = 1024 * 1024;

test('legacyProblem: đúng loại, đúng giới hạn dung lượng và thời lượng video', () => {
  const image = { resource_type: 'image', bytes: 10 * MB };
  assert.equal(legacyProblem(image, 'IMAGE'), null);
  assert.match(legacyProblem({ ...image, bytes: 10 * MB + 1 }, 'IMAGE'), /dung lượng/);
  assert.match(legacyProblem({ ...image, resource_type: 'video' }, 'IMAGE'), /loại/);

  const video = { resource_type: 'video', bytes: 50 * MB, duration: 61 };
  assert.equal(legacyProblem(video, 'VIDEO'), null);
  assert.match(legacyProblem({ ...video, duration: 61.5 }, 'VIDEO'), /thời lượng/);
  assert.match(legacyProblem({ ...video, duration: undefined }, 'VIDEO'), /thời lượng/);
});

test('fingerprint: đổi một field legacy thì khác; field không liên quan thì giống nhau', () => {
  const base = { photoUri: 'a', videoUri: '', cloudinaryPublicId: 'x', title: 'bỏ qua' };
  assert.notEqual(fingerprint(base), fingerprint({ ...base, photoUri: 'b' }));
  assert.equal(fingerprint(base), fingerprint({ ...base, title: 'khác' }));
  assert.equal(fingerprint({}), fingerprint({ photoUri: null }));
});

test('toUpdate: null thành FieldValue.delete(), giá trị khác giữ nguyên', () => {
  const out = toUpdate({ photoUri: '', cloudinaryUrl: null });
  assert.equal(out.photoUri, '');
  assert.equal(out.cloudinaryUrl.isEqual(FieldValue.delete()), true);
});

test('MEMORY_PATH: chỉ nhận relationships/{id}/memories/{id}', () => {
  assert.ok(MEMORY_PATH.test('relationships/r1/memories/0f8f'));
  assert.equal(MEMORY_PATH.test('memories/0f8f'), false);
  assert.equal(MEMORY_PATH.test('relationships/r1/memories/0f8f/extra/x'), false);
});
