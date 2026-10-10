'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const cfg = require('../src/config');
const media = require('../src/media');

// Thời điểm cố định: 8/10/2026 12:00 UTC.
const NOW = Date.UTC(2026, 9, 8, 12, 0, 0);
const UID = 'user-a';
const REL = 'rel-1';
const MEMORY = '0f8fad5b-d9cb-469f-a165-70867728950e';
const HOUR = 60 * 60 * 1000;

const activeRel = { status: 'ACTIVE', partnerAId: UID, partnerBId: 'user-b' };
const input = { relationshipId: REL, memoryId: MEMORY, kind: 'IMAGE', sizeBytes: 1024, durationSeconds: 0, format: 'jpg' };
const noUsage = { userCount: 0, relationshipCount: 0, globalCount: 0, storedBytes: 0, globalStoredBytes: 0 };
const reserved = (over = {}) => ({ state: 'reserved', ownerUid: UID, relationshipId: REL, expiresAtMs: NOW + HOUR, ...over });

test('isMember: khớp bất kỳ trường thành viên nào; uid trống hoặc không có cặp đôi thì không khớp', () => {
  for (const field of ['partnerAId', 'partnerBId', 'user1', 'user2', 'user1Uid', 'user2Uid']) {
    assert.equal(media.isMember({ [field]: UID }, UID), true, field);
  }
  assert.equal(media.isMember({ partnerAId: 'someone' }, UID), false);
  assert.equal(media.isMember(undefined, UID), false);
  assert.equal(media.isMember({ partnerAId: '' }, ''), false);
});

test('planDelivery: chỉ thành viên của cặp đôi còn ACTIVE mới nhận link xem; người ngoài và cặp đôi đã chia tay thì bị từ chối', () => {
  assert.equal(media.planDelivery({ rel: activeRel, uid: UID }), null);
  assert.equal(media.planDelivery({ rel: activeRel, uid: 'stranger' }).code, 'permission-denied');
  assert.equal(media.planDelivery({ rel: undefined, uid: UID }).code, 'permission-denied');
  assert.equal(media.planDelivery({ rel: { ...activeRel, status: 'TERMINATED' }, uid: UID }).code, 'failed-precondition');
  assert.equal(media.planDelivery({ rel: { ...activeRel, status: undefined }, uid: UID }).code, 'failed-precondition');
});

test('planReserve: từ chối người ngoài, cặp đôi không active, tệp đã có, vượt quota', () => {
  assert.ok(media.planReserve({ rel: activeRel, uid: UID, input, existing: null, usage: noUsage, nowMs: NOW }).asset);
  const outsider = media.planReserve({ rel: activeRel, uid: 'stranger', input, existing: null, usage: noUsage, nowMs: NOW });
  assert.equal(outsider.error.code, 'permission-denied');
  const ended = media.planReserve({ rel: { ...activeRel, status: 'TERMINATED' }, uid: UID, input, existing: null, usage: noUsage, nowMs: NOW });
  assert.equal(ended.error.code, 'failed-precondition');
  const dup = media.planReserve({ rel: activeRel, uid: UID, input, existing: reserved({ state: 'active' }), usage: noUsage, nowMs: NOW });
  assert.equal(dup.error.code, 'already-exists');
  const full = media.planReserve({
    rel: activeRel, uid: UID, input, existing: null, usage: { ...noUsage, userCount: cfg.quotas.userDailyUploads }, nowMs: NOW,
  });
  assert.equal(full.error.code, 'resource-exhausted');
});

test('planReserve: gọi lại đúng phiên còn hiệu lực thì ký lại, không cộng quota; khác chủ, khác loại, hết hạn thì từ chối', () => {
  const full = { ...noUsage, userCount: cfg.quotas.userDailyUploads };
  const again = media.planReserve({ rel: activeRel, uid: UID, input, existing: reserved({ kind: 'IMAGE', format: 'jpg' }), usage: full, nowMs: NOW });
  assert.equal(again.resign, true);
  assert.equal(again.error, undefined);
  const other = reserved({ kind: 'IMAGE', format: 'jpg', ownerUid: 'other' });
  assert.equal(media.planReserve({ rel: activeRel, uid: UID, input, existing: other, usage: noUsage, nowMs: NOW }).error.code, 'already-exists');
  const video = reserved({ kind: 'VIDEO', format: 'mp4' });
  assert.equal(media.planReserve({ rel: activeRel, uid: UID, input, existing: video, usage: noUsage, nowMs: NOW }).error.code, 'already-exists');
  const late = reserved({ kind: 'IMAGE', format: 'jpg', expiresAtMs: NOW });
  assert.equal(media.planReserve({ rel: activeRel, uid: UID, input, existing: late, usage: noUsage, nowMs: NOW }).error.code, 'deadline-exceeded');
});

test('planReserve: phiên có public_id theo quy ước, trạng thái reserved, hạn đúng 2 giờ', () => {
  const { asset } = media.planReserve({ rel: activeRel, uid: UID, input, existing: null, usage: noUsage, nowMs: NOW });
  assert.equal(asset.state, 'reserved');
  assert.equal(asset.publicId, `inlove_mem_${MEMORY}`);
  assert.equal(asset.ownerUid, UID);
  assert.equal(asset.expiresAt.getTime(), NOW + cfg.timing.reservationTtlMs);
  assert.equal(asset.countedBytes, 0);
});

test('planConfirm: chỉ chủ phiên, đúng cặp đôi, còn reserved và chưa hết hạn (đúng mốc là hết hạn)', () => {
  assert.equal(media.planConfirm({ asset: reserved(), uid: UID, relationshipId: REL, nowMs: NOW }), null);
  assert.equal(media.planConfirm({ asset: undefined, uid: UID, relationshipId: REL, nowMs: NOW }).code, 'not-found');
  assert.equal(media.planConfirm({ asset: reserved({ ownerUid: 'other' }), uid: UID, relationshipId: REL, nowMs: NOW }).code, 'permission-denied');
  assert.equal(media.planConfirm({ asset: reserved({ relationshipId: 'rel-2' }), uid: UID, relationshipId: REL, nowMs: NOW }).code, 'permission-denied');
  assert.equal(media.planConfirm({ asset: reserved({ state: 'active' }), uid: UID, relationshipId: REL, nowMs: NOW }).code, 'failed-precondition');
  assert.equal(media.planConfirm({ asset: reserved({ state: 'expired' }), uid: UID, relationshipId: REL, nowMs: NOW }).code, 'failed-precondition');
  assert.equal(media.planConfirm({ asset: reserved({ expiresAtMs: NOW }), uid: UID, relationshipId: REL, nowMs: NOW }).code, 'deadline-exceeded');
});

test('planCapacity: đúng ngưỡng 2 GB vẫn nhận, vượt 1 byte thì từ chối', () => {
  const cap = cfg.quotas.relationshipStoredBytes;
  assert.equal(media.planCapacity({ storedBytes: cap - 10, bytes: 10 }), null);
  assert.equal(media.planCapacity({ storedBytes: cap - 9, bytes: 10 })?.code, 'resource-exhausted');
});

test('planGlobalCapacity: đúng trần toàn hệ thống vẫn nhận, vượt 1 byte thì từ chối; reserve cũng bị chặn sớm', () => {
  const cap = cfg.quotas.globalStoredBytes;
  assert.equal(media.planGlobalCapacity({ globalBytes: cap - 10, bytes: 10 }), null);
  assert.equal(media.planGlobalCapacity({ globalBytes: cap - 9, bytes: 10 })?.code, 'resource-exhausted');
  const blocked = media.planReserve({ rel: activeRel, uid: UID, input, existing: null, usage: { ...noUsage, globalStoredBytes: cap }, nowMs: NOW });
  assert.equal(blocked.error.code, 'resource-exhausted');
});

test('alreadyConfirmed: đã active đúng chủ và đúng cặp đôi thì coi là đã xác nhận (app gửi lại); còn lại thì không', () => {
  const active = { state: 'active', ownerUid: UID, relationshipId: REL, bytes: 2048, publicId: `inlove_mem_${MEMORY}` };
  assert.equal(media.alreadyConfirmed(active, UID, REL), true);
  assert.equal(media.alreadyConfirmed({ ...active, ownerUid: 'other' }, UID, REL), false);
  assert.equal(media.alreadyConfirmed({ ...active, relationshipId: 'rel-2' }, UID, REL), false);
  assert.equal(media.alreadyConfirmed({ ...active, state: 'reserved' }, UID, REL), false);
  assert.equal(media.alreadyConfirmed({ ...active, state: 'deleted' }, UID, REL), false);
  assert.equal(media.alreadyConfirmed(null, UID, REL), false);
});

test('planRelease: chỉ giải phóng tài sản active hoặc đang deleting của đúng cặp đôi', () => {
  assert.equal(media.planRelease({ state: 'active', relationshipId: REL }, REL), true);
  assert.equal(media.planRelease({ state: 'deleting', relationshipId: REL }, REL), true);
  assert.equal(media.planRelease({ state: 'reserved', relationshipId: REL }, REL), false);
  assert.equal(media.planRelease({ state: 'deleted', relationshipId: REL }, REL), false);
  assert.equal(media.planRelease({ state: 'active', relationshipId: 'rel-2' }, REL), false);
  assert.equal(media.planRelease(null, REL), false);
});

test('planExpire: reserved quá hạn (kể cả đúng mốc) mới chuyển sang expired; không chạm vào tệp active', () => {
  assert.equal(media.planExpire({ state: 'reserved', expiresAtMs: NOW }, NOW), true);
  assert.equal(media.planExpire({ state: 'reserved', expiresAtMs: NOW + 1 }, NOW), false);
  assert.equal(media.planExpire({ state: 'active', expiresAtMs: NOW - 1 }, NOW), false);
});

test('planPurge: expired phải đủ 1 giờ ân hạn (lateUploadGraceMs) kể từ lúc đánh dấu mới được xoá tệp', () => {
  assert.equal(cfg.timing.lateUploadGraceMs, HOUR);
  const expiredAt = NOW - cfg.timing.lateUploadGraceMs;
  assert.equal(media.planPurge({ state: 'expired', expiredAtMs: expiredAt }, NOW), true);
  assert.equal(media.planPurge({ state: 'expired', expiredAtMs: expiredAt + 1 }, NOW), false);
  assert.equal(media.planPurge({ state: 'reserved', expiredAtMs: expiredAt }, NOW), false);
});

test('dailyKeys và storageKey: khoá theo ngày UTC, tách theo người, cặp đôi và toàn hệ thống', () => {
  assert.deepEqual(media.dailyKeys(UID, REL, NOW), {
    user: 'user_user-a_20261008',
    relationship: 'rel_rel-1_20261008',
    global: 'global_20261008',
  });
  assert.equal(media.storageKey(REL), 'storage_rel-1');
});

test('readAsset: đổi Timestamp thành millis; thiếu expiresAt thì coi là 0 (fail-closed); không tồn tại thì null', () => {
  const snap = { exists: true, data: () => ({ state: 'reserved', expiresAt: { toMillis: () => NOW } }) };
  assert.equal(media.readAsset(snap).expiresAtMs, NOW);
  assert.equal(media.readAsset({ exists: true, data: () => ({ state: 'reserved' }) }).expiresAtMs, 0);
  assert.equal(media.readAsset({ exists: false }), null);
  assert.equal(media.readAsset({ exists: true, data: () => ({ orphanCheckAt: { toMillis: () => NOW } }) }).orphanCheckAtMs, NOW);
});

test('planOrphan: chỉ xét tài sản active đến hạn; memory còn trỏ tới thì hẹn kiểm tra lại, không còn thì xoá', () => {
  const due = { state: 'active', publicId: `inlove_mem_${MEMORY}`, orphanCheckAtMs: NOW };
  assert.equal(media.planOrphan({ asset: due, memoryPublicId: undefined, nowMs: NOW }), 'purge');
  assert.equal(media.planOrphan({ asset: due, memoryPublicId: '', nowMs: NOW }), 'purge');
  assert.equal(media.planOrphan({ asset: due, memoryPublicId: `inlove_mem_${MEMORY}`, nowMs: NOW }), 'recheck');
  assert.equal(media.planOrphan({ asset: due, memoryPublicId: 'inlove_mem_other', nowMs: NOW }), 'purge');
  assert.equal(media.planOrphan({ asset: { ...due, orphanCheckAtMs: NOW + 1 }, memoryPublicId: undefined, nowMs: NOW }), null);
  assert.equal(media.planOrphan({ asset: { ...due, orphanCheckAtMs: 0 }, memoryPublicId: undefined, nowMs: NOW }), null);
  assert.equal(media.planOrphan({ asset: { ...due, state: 'reserved' }, memoryPublicId: undefined, nowMs: NOW }), null);
  assert.equal(media.planOrphan({ asset: undefined, memoryPublicId: undefined, nowMs: NOW }), null);
});
