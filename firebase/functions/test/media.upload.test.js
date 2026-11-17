'use strict';

// Ký upload (reserveUpload) và xác nhận (confirmUpload) với hợp đồng kho đối tượng mới:
// cloud.signUpload trả { uploadUrl, headers }, cloud.fetchResource trả { bytes, contentType, head }.
// Firestore giả (fakeFirestore) + cloud giả ghi lại lời gọi.
const test = require('node:test');
const assert = require('node:assert/strict');
const { Timestamp, FieldValue } = require('firebase-admin/firestore');
const cfg = require('../src/config');
const policy = require('../src/policy');
const media = require('../src/media');
const { createFakeFirestore } = require('./helpers/fakeFirestore');

const UID = 'uid-1';
const REL = 'rel-1';
const MEMORY = '2f1c0e4a-9b7d-4c1e-8a3f-5d6e7f8a9b0c';
const PUBLIC_ID = policy.publicIdFor(MEMORY);
const NOW = Date.UTC(2026, 9, 21, 8, 0, 0);
const TTL_MS = cfg.timing.reservationTtlMs; // 2 giờ
const JPEG_HEAD = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 0x10, 0x4a, 0x46, 0x49, 0x46, 0, 1]);

function setup({ resource = null } = {}) {
  const db = createFakeFirestore();
  const calls = { sign: [], fetch: [], destroy: [] };
  const cloud = {
    signUpload(args) {
      calls.sign.push(args);
      return {
        uploadUrl: 'https://r2.test/put',
        headers: { 'content-type': args.contentType, 'content-length': String(args.contentLength), 'if-none-match': '*' },
      };
    },
    fetchResource: async (publicId, kind) => { calls.fetch.push([publicId, kind]); return resource; },
    destroy: async (publicId, kind) => { calls.destroy.push([publicId, kind]); },
  };
  return { db, cloud, calls };
}

const seedRel = (db) => db.collection('relationships').doc(REL).set({ partnerAId: UID, status: 'ACTIVE' });

// Seed bản ghi reserved; expiresAt phải là Timestamp để readAsset đọc được.
const seedAsset = (db, over = {}) => db.collection('mediaAssets').doc(MEMORY).set({
  state: 'reserved',
  ownerUid: UID,
  relationshipId: REL,
  kind: 'IMAGE',
  format: 'jpg',
  publicId: PUBLIC_ID,
  declaredBytes: 4096,
  countedBytes: 0,
  reservedAt: Timestamp.fromMillis(NOW),
  expiresAt: Timestamp.fromMillis(NOW + 3_600_000),
  ...over,
});

const reserveInput = (over = {}) => ({
  relationshipId: REL, memoryId: MEMORY, kind: 'IMAGE', sizeBytes: 4096, durationSeconds: 0, format: 'jpg', ...over,
});
const reserve = ({ db, cloud }, input = reserveInput()) =>
  media.reserveUpload({ db, cloud, uid: UID, input, nowMs: NOW, tier: 'free' });
const confirm = ({ db, cloud }) =>
  media.confirmUpload({ db, cloud, uid: UID, input: { relationshipId: REL, memoryId: MEMORY }, nowMs: NOW });

const goodResource = (over = {}) => ({ bytes: 4096, contentType: 'image/jpeg', head: JPEG_HEAD, ...over });
const codeIs = (code) => (e) => e.code === code;

// ---------- reserveUpload ----------

test('reserveUpload_phienMoi_kyVoiDungHopDongVaTraHeaders', async () => {
  const ctx = setup();
  await seedRel(ctx.db);
  const res = await reserve(ctx);
  assert.deepEqual(ctx.calls.sign, [
    { publicId: PUBLIC_ID, contentType: 'image/jpeg', contentLength: 4096, expiresInSeconds: 7200 },
  ]);
  assert.equal(res.uploadUrl, 'https://r2.test/put');
  assert.deepEqual(res.headers, { 'content-type': 'image/jpeg', 'content-length': '4096', 'if-none-match': '*' });
  assert.equal('fields' in res, false);
  assert.equal(res.publicId, PUBLIC_ID);
  assert.equal(res.expiresAtMillis, NOW + TTL_MS);
});

test('reserveUpload_video_kyContentTypeQuicktime', async () => {
  const ctx = setup();
  await seedRel(ctx.db);
  await reserve(ctx, reserveInput({ kind: 'VIDEO', format: 'mov', durationSeconds: 5 }));
  assert.equal(ctx.calls.sign[0].contentType, 'video/quicktime');
});

test('reserveUpload_kyLaiPhienConHan_kyTheoDungLuongDaLuuVaHanConLai', async () => {
  const ctx = setup();
  await seedRel(ctx.db);
  await seedAsset(ctx.db); // còn 1 giờ, declaredBytes 4096
  const before = ctx.db.writes;
  const res = await reserve(ctx, reserveInput({ sizeBytes: 9999 }));
  assert.equal(ctx.calls.sign[0].contentLength, 4096);
  assert.equal(ctx.calls.sign[0].expiresInSeconds, 3600);
  assert.equal(res.expiresAtMillis, NOW + 3_600_000);
  assert.equal(ctx.db.writes, before, 'ký lại không được ghi quota');
});

test('reserveUpload_kyLaiKhiConDuoi1Giay_lamTronLenToiThieu1', async () => {
  const ctx = setup();
  await seedRel(ctx.db);
  await seedAsset(ctx.db, { expiresAt: Timestamp.fromMillis(NOW + 500) });
  await reserve(ctx);
  assert.equal(ctx.calls.sign[0].expiresInSeconds, 1);
});

// ---------- confirmUpload ----------

test('confirmUpload_khongCoBanGhi_notFoundVaKhongGoiFetch', async () => {
  const ctx = setup({ resource: goodResource() });
  await assert.rejects(confirm(ctx), codeIs('not-found'));
  assert.equal(ctx.calls.fetch.length, 0);
});

test('confirmUpload_tepChuaCo_notFoundGiuReservedVaKhongXoa', async () => {
  const ctx = setup({ resource: null });
  await seedAsset(ctx.db);
  await assert.rejects(confirm(ctx), codeIs('not-found'));
  assert.equal(ctx.db.read(`mediaAssets/${MEMORY}`).state, 'reserved');
  assert.equal(ctx.calls.destroy.length, 0);
});

test('confirmUpload_saiDungLuong_xoaTepVaDanhDauDeleted', async () => {
  const ctx = setup({ resource: goodResource({ bytes: 4097 }) });
  await seedAsset(ctx.db);
  await assert.rejects(confirm(ctx), codeIs('failed-precondition'));
  assert.deepEqual(ctx.calls.destroy, [[PUBLIC_ID, 'IMAGE']]);
  assert.equal(ctx.db.read(`mediaAssets/${MEMORY}`).state, 'deleted');
  assert.equal(ctx.db.read(`mediaQuota/storage_${REL}`), undefined);
});

test('confirmUpload_dauTepKhongPhaiMedia_xoaTep', async () => {
  const ctx = setup({ resource: goodResource({ head: Buffer.from('this is not a picture at all.........') }) });
  await seedAsset(ctx.db);
  await assert.rejects(confirm(ctx), codeIs('failed-precondition'));
  assert.equal(ctx.calls.destroy.length, 1);
});

test('confirmUpload_saiContentType_xoaTep', async () => {
  const ctx = setup({ resource: goodResource({ contentType: 'image/png' }) });
  await seedAsset(ctx.db);
  await assert.rejects(confirm(ctx), codeIs('failed-precondition'));
  assert.equal(ctx.calls.destroy.length, 1);
});

test('confirmUpload_hopLe_kichHoatCongQuotaVaGoiLaiKhongCongThem', async () => {
  const ctx = setup({ resource: goodResource() });
  await seedAsset(ctx.db);
  const res = await confirm(ctx);
  assert.deepEqual(res, { publicId: PUBLIC_ID, bytes: 4096 });
  const asset = ctx.db.read(`mediaAssets/${MEMORY}`);
  assert.equal(asset.state, 'active');
  assert.equal(asset.bytes, 4096);
  assert.equal(asset.countedBytes, 4096);
  assert.ok(ctx.db.read(`mediaQuota/storage_${REL}`).bytes.isEqual(FieldValue.increment(4096)));
  assert.ok(ctx.db.read('mediaQuota/global_storage').bytes.isEqual(FieldValue.increment(4096)));
  assert.equal(ctx.calls.destroy.length, 0);

  const again = await confirm(ctx);
  assert.deepEqual(again, { publicId: PUBLIC_ID, bytes: 4096 });
  assert.equal(ctx.calls.fetch.length, 1, 'xác nhận lại không được hỏi kho lần nữa');
});
