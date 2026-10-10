'use strict';

// Vòng đời tài sản media: ký upload -> xác nhận -> cấp URL xem có hạn -> xoá khi memory bị xoá hoặc bỏ ảnh,
// khi phiên bỏ dở, hoặc khi tài sản mồ côi. Quyết định nằm trong các hàm plan* (thuần, có test).
// Hàm Firestore chỉ đọc, áp dụng quyết định rồi ghi.
//
// Trạng thái mediaAssets/{memoryId}:
//   reserved -> active -> deleting -> deleted          (memory bị xoá hoặc bỏ ảnh)
//   active -> deleting -> deleted                      (mồ côi: không memory nào còn trỏ tới, sau orphanAfterMs)
//   reserved -> expired -> deleting -> deleted         (phiên quá hạn: sweeper, sau thời gian ân hạn)
//   reserved -> deleting -> deleted                    (tệp không đạt khi confirm)
// Mỗi lần tải lên dùng memoryId mới (app sinh UUID). Đã có bản ghi cho memoryId thì từ chối, vì public_id không ghi đè.
// Ngoại lệ an toàn: gọi lại đúng phiên còn reserved (ký lại) hoặc xác nhận lại phiên đã active (app gửi lại khi mất phản hồi).
const { FieldValue } = require('firebase-admin/firestore');
const { HttpsError } = require('firebase-functions/v2/https');
const logger = require('firebase-functions/logger');
const cfg = require('./config');
const policy = require('./policy');

// Trùng với firestore.rules (isRelationshipMember): bất kỳ field nào khớp uid đều là thành viên.
const MEMBER_FIELDS = ['partnerAId', 'partnerBId', 'user1', 'user2', 'user1Uid', 'user2Uid'];

const fail = (code, message) => new HttpsError(code, message);

const refs = {
  rel: (db, relId) => db.collection('relationships').doc(relId),
  memory: (db, relId, memoryId) => db.collection('relationships').doc(relId).collection('memories').doc(memoryId),
  asset: (db, memoryId) => db.collection('mediaAssets').doc(memoryId),
  quota: (db, key) => db.collection('mediaQuota').doc(key),
};

// Khoá bộ đếm theo ngày (UTC): theo người dùng, theo cặp đôi, toàn hệ thống.
function dailyKeys(uid, relationshipId, nowMs) {
  const day = policy.dayKey(nowMs);
  return {
    user: `user_${uid}_${day}`,
    relationship: `rel_${relationshipId}_${day}`,
    global: `global_${day}`,
  };
}
// Dung lượng lũy kế của cặp đôi (không theo ngày).
const storageKey = (relationshipId) => `storage_${relationshipId}`;
// Dung lượng lũy kế của toàn hệ thống: trần chi phí lưu trữ (cfg.quotas.globalStoredBytes). Không có TTL.
const GLOBAL_STORAGE_KEY = 'global_storage';

const millisOf = (ts) => (ts && typeof ts.toMillis === 'function' ? ts.toMillis() : 0);

// Đọc snapshot thành object, thời điểm dạng millis để planner so sánh trực tiếp.
function readAsset(snap) {
  if (!snap || !snap.exists) return null;
  const data = snap.data();
  return {
    ...data,
    expiresAtMs: millisOf(data.expiresAt),
    expiredAtMs: millisOf(data.expiredAt),
    orphanCheckAtMs: millisOf(data.orphanCheckAt),
  };
}

function isMember(rel, uid) {
  return !!rel && !!uid && MEMBER_FIELDS.some((field) => rel[field] === uid);
}

// ---------- Quyết định thuần (có test) ----------

// Trả về { error }, { asset } (bản ghi reserved mới: ghi vào Firestore và cộng quota), hoặc { asset, resign: true }
// (phiên còn hiệu lực: chỉ ký lại, không ghi và không cộng quota). existing là bản ghi đã có của memoryId, hoặc null.
function planReserve({ rel, uid, input, existing, usage, nowMs }) {
  if (!isMember(rel, uid)) return { error: fail('permission-denied', 'Bạn không thuộc cặp đôi này') };
  if (rel.status !== 'ACTIVE') return { error: fail('failed-precondition', 'Cặp đôi không còn hoạt động') };
  if (existing) {
    // App gọi lại sau khi mất phản hồi lần trước: cùng người, cùng cặp đôi, cùng loại tệp thì ký lại phiên.
    const sameSession = existing.state === 'reserved' && existing.ownerUid === uid
      && existing.relationshipId === input.relationshipId && existing.kind === input.kind && existing.format === input.format;
    if (!sameSession) return { error: fail('already-exists', 'Kỷ niệm này đã có tệp đính kèm') };
    if (existing.expiresAtMs <= nowMs) return { error: fail('deadline-exceeded', 'Phiên tải lên đã hết hạn, hãy thử lại') };
    return { asset: existing, resign: true };
  }
  // Dung lượng khai báo chỉ kiểm tra sơ bộ. Giới hạn thật được kiểm tra lại khi confirm, dựa trên dung lượng Cloudinary báo.
  const problem = policy.quotaProblem(usage, input.sizeBytes)
    ?? planGlobalCapacity({ globalBytes: usage.globalStoredBytes, bytes: input.sizeBytes });
  if (problem) return { error: problem };
  return {
    asset: {
      state: 'reserved',
      ownerUid: uid,
      relationshipId: input.relationshipId,
      kind: input.kind,
      format: input.format,
      publicId: policy.publicIdFor(input.memoryId),
      declaredBytes: input.sizeBytes,
      countedBytes: 0,
      reservedAt: new Date(nowMs),
      expiresAt: new Date(nowMs + cfg.timing.reservationTtlMs),
    },
  };
}

// Kiểm tra phiên trước khi xác nhận. Trả về HttpsError hoặc null.
function planConfirm({ asset, uid, relationshipId, nowMs }) {
  if (!asset) return fail('not-found', 'Không tìm thấy phiên tải lên');
  if (asset.ownerUid !== uid || asset.relationshipId !== relationshipId) {
    return fail('permission-denied', 'Phiên tải lên không thuộc về bạn');
  }
  if (asset.state !== 'reserved') return fail('failed-precondition', 'Phiên tải lên đã được xử lý');
  if (asset.expiresAtMs <= nowMs) return fail('deadline-exceeded', 'Phiên tải lên đã hết hạn');
  return null;
}

// Kho của cặp đôi còn chỗ cho tệp này không. Trả về HttpsError hoặc null.
function planCapacity({ storedBytes, bytes }) {
  if (storedBytes + bytes > cfg.quotas.relationshipStoredBytes) {
    return fail('resource-exhausted', 'Kho ảnh của cặp đôi đã đầy');
  }
  return null;
}

// Kho của cả hệ thống còn chỗ không: lớp chặn cuối cùng cho chi phí lưu trữ. Trả về HttpsError hoặc null.
function planGlobalCapacity({ globalBytes, bytes }) {
  if (globalBytes + bytes > cfg.quotas.globalStoredBytes) {
    return fail('resource-exhausted', 'Kho ảnh chung của hệ thống đã đầy, tạm thời chưa nhận thêm ảnh');
  }
  return null;
}

// Phiên đã active đúng chủ và đúng cặp đôi: app gửi lại confirm sau khi mất phản hồi. Trả kết quả cũ, không cộng thêm.
function alreadyConfirmed(asset, uid, relationshipId) {
  return !!asset && asset.state === 'active' && asset.ownerUid === uid && asset.relationshipId === relationshipId;
}

// Memory bị xoá hoặc bỏ ảnh: giải phóng tài sản của đúng cặp đôi. Tài sản đang deleting (lần trước dở) cũng được hoàn tất.
function planRelease(asset, relationshipId) {
  return !!asset && asset.relationshipId === relationshipId && (asset.state === 'active' || asset.state === 'deleting');
}

// Phiên reserved quá hạn mà chưa confirm: chuyển sang expired. Chưa xoá tệp vì app có thể vẫn đang upload.
function planExpire(asset, nowMs) {
  return !!asset && asset.state === 'reserved' && asset.expiresAtMs <= nowMs;
}

// Phiên expired đã đủ thời gian ân hạn để tệp upload muộn, nếu có, đã xuất hiện: xoá tệp.
function planPurge(asset, nowMs) {
  return !!asset && asset.state === 'expired' && asset.expiredAtMs + cfg.timing.lateUploadGraceMs <= nowMs;
}

// Tài sản active mà không memory nào còn trỏ tới (app mất mạng trước khi ghi memory, hoặc trigger lỡ việc).
// Trả về 'recheck' (memory vẫn trỏ tới: hẹn kiểm tra lại), 'purge' (không ai dùng: xoá), hoặc null (chưa đến hạn).
function planOrphan({ asset, memoryPublicId, nowMs }) {
  if (!asset || asset.state !== 'active') return null;
  if (!(asset.orphanCheckAtMs > 0 && asset.orphanCheckAtMs <= nowMs)) return null;
  return memoryPublicId === asset.publicId ? 'recheck' : 'purge';
}

// ---------- Ghi Firestore ----------

// Chuyển trạng thái trong transaction, chỉ khi bản ghi còn thoả điều kiện. Trả về bản ghi đã claim hoặc null.
async function claim(db, memoryId, shouldClaim, patch) {
  const ref = refs.asset(db, memoryId);
  return db.runTransaction(async (tx) => {
    const asset = readAsset(await tx.get(ref));
    if (!asset || !shouldClaim(asset)) return null;
    tx.update(ref, patch);
    return asset;
  });
}

// Đánh dấu deleted và trừ dung lượng đã cộng (của cặp đôi và toàn hệ thống). Gọi lại nhiều lần vẫn an toàn.
async function markDeleted(db, memoryId, nowMs) {
  const ref = refs.asset(db, memoryId);
  await db.runTransaction(async (tx) => {
    const asset = readAsset(await tx.get(ref));
    if (!asset || asset.state === 'deleted') return;
    tx.update(ref, {
      state: 'deleted',
      countedBytes: 0,
      deletedAt: new Date(nowMs),
      purgeAt: new Date(nowMs + cfg.timing.deletedRecordTtlMs),
    });
    if (asset.countedBytes > 0) {
      const decrement = { bytes: FieldValue.increment(-asset.countedBytes) };
      tx.set(refs.quota(db, storageKey(asset.relationshipId)), decrement, { merge: true });
      tx.set(refs.quota(db, GLOBAL_STORAGE_KEY), decrement, { merge: true });
    }
  });
}

// Claim rồi xoá tệp trên Cloudinary, sau đó ghi deleted. Nếu xoá lỗi thì bản ghi còn deleting và sweeper thử lại.
async function purgeOne({ db, cloud, memoryId, nowMs, shouldClaim, reason }) {
  const claimed = await claim(db, memoryId, shouldClaim, { state: 'deleting', reason });
  if (!claimed) return false;
  await cloud.destroy(claimed.publicId, claimed.kind);
  await markDeleted(db, memoryId, nowMs);
  return true;
}

// ---------- Các thao tác công khai ----------

async function reserveUpload({ db, cloud, uid, input, nowMs }) {
  const keys = dailyKeys(uid, input.relationshipId, nowMs);
  const plan = await db.runTransaction(async (tx) => {
    const [relSnap, assetSnap, userSnap, relCountSnap, globalSnap, storageSnap, globalStorageSnap] = await Promise.all([
      tx.get(refs.rel(db, input.relationshipId)),
      tx.get(refs.asset(db, input.memoryId)),
      tx.get(refs.quota(db, keys.user)),
      tx.get(refs.quota(db, keys.relationship)),
      tx.get(refs.quota(db, keys.global)),
      tx.get(refs.quota(db, storageKey(input.relationshipId))),
      tx.get(refs.quota(db, GLOBAL_STORAGE_KEY)),
    ]);
    const usage = {
      userCount: userSnap.get('count') ?? 0,
      relationshipCount: relCountSnap.get('count') ?? 0,
      globalCount: globalSnap.get('count') ?? 0,
      storedBytes: storageSnap.get('bytes') ?? 0,
      globalStoredBytes: globalStorageSnap.get('bytes') ?? 0,
    };
    const result = planReserve({ rel: relSnap.data(), uid, input, existing: readAsset(assetSnap), usage, nowMs });
    if (result.error) throw result.error;
    if (result.resign) return result; // ký lại: không ghi, không cộng quota

    tx.set(refs.asset(db, input.memoryId), result.asset);
    for (const key of [keys.user, keys.relationship, keys.global]) {
      tx.set(refs.quota(db, key),
        { count: FieldValue.increment(1), expiresAt: new Date(nowMs + cfg.timing.dailyCounterTtlMs) },
        { merge: true });
    }
    return result;
  });

  const { asset } = plan;
  const signed = cloud.signUpload({
    publicId: asset.publicId,
    kind: asset.kind,
    format: asset.format,
    timestampSeconds: Math.floor(nowMs / 1000),
  });
  return {
    publicId: asset.publicId,
    uploadUrl: signed.uploadUrl,
    fields: signed.fields,
    expiresAtMillis: plan.resign ? asset.expiresAtMs : nowMs + cfg.timing.reservationTtlMs,
  };
}

// Sau khi app upload xong: đối chiếu tệp với Cloudinary rồi mới kích hoạt tài sản và cộng dung lượng.
// Gọi lại khi đã xác nhận rồi thì trả kết quả cũ, không cộng thêm.
async function confirmUpload({ db, cloud, uid, input, nowMs }) {
  const { relationshipId, memoryId } = input;
  const ref = refs.asset(db, memoryId);
  const asset = readAsset(await ref.get());
  if (alreadyConfirmed(asset, uid, relationshipId)) return { publicId: asset.publicId, bytes: asset.bytes };
  const early = planConfirm({ asset, uid, relationshipId, nowMs });
  if (early) throw early;

  // Chưa thấy tệp: không đổi trạng thái để app thử lại trong thời hạn phiên.
  const resource = await cloud.fetchResource(asset.publicId, asset.kind);
  if (!resource) throw fail('not-found', 'Tệp chưa có trên máy chủ, thử tải lại');

  const problem = policy.resourceProblem(resource, { publicId: asset.publicId, kind: asset.kind, format: asset.format });
  if (problem) {
    await purgeOne({ db, cloud, memoryId, nowMs, shouldClaim: (a) => a.state === 'reserved', reason: problem });
    throw fail('failed-precondition', problem);
  }

  const bytes = resource.bytes;
  const storageRef = refs.quota(db, storageKey(relationshipId));
  const globalRef = refs.quota(db, GLOBAL_STORAGE_KEY);
  const outcome = await db.runTransaction(async (tx) => {
    const [assetSnap, storageSnap, globalSnap] = await Promise.all([tx.get(ref), tx.get(storageRef), tx.get(globalRef)]);
    const current = readAsset(assetSnap);
    // Hai lần xác nhận chạy song song: lần sau thấy đã active thì trả kết quả cũ, không cộng lần nữa.
    if (alreadyConfirmed(current, uid, relationshipId)) return { publicId: current.publicId, bytes: current.bytes };
    const stale = planConfirm({ asset: current, uid, relationshipId, nowMs });
    if (stale) throw stale;
    const rejection = planCapacity({ storedBytes: storageSnap.get('bytes') ?? 0, bytes })
      ?? planGlobalCapacity({ globalBytes: globalSnap.get('bytes') ?? 0, bytes });
    if (rejection) return { rejection };
    tx.update(ref, {
      state: 'active',
      bytes,
      countedBytes: bytes,
      confirmedAt: new Date(nowMs),
      orphanCheckAt: new Date(nowMs + cfg.timing.orphanAfterMs),
    });
    tx.set(storageRef, { bytes: FieldValue.increment(bytes) }, { merge: true });
    tx.set(globalRef, { bytes: FieldValue.increment(bytes) }, { merge: true });
    return { publicId: asset.publicId, bytes };
  });
  if (outcome.rejection) {
    await purgeOne({ db, cloud, memoryId, nowMs, shouldClaim: (a) => a.state === 'reserved', reason: outcome.rejection.message });
    throw outcome.rejection;
  }
  return { publicId: outcome.publicId, bytes: outcome.bytes };
}

// Quyết định thuần: link xem chỉ cấp cho thành viên của cặp đôi còn ACTIVE. Sau khi chia tay thì link mới bị từ chối;
// link đã cấp vẫn sống tới deliveryTtlSeconds (xem docs/architecture/MEDIA_CLOUDINARY.md).
function planDelivery({ rel, uid }) {
  if (!isMember(rel, uid)) return { code: 'permission-denied', message: 'Bạn không thuộc cặp đôi này' };
  if (rel.status !== 'ACTIVE') return { code: 'failed-precondition', message: 'Cặp đôi không còn hoạt động' };
  return null;
}

// URL xem có hạn cho thành viên cặp đôi. Chỉ cấp khi memory thật sự trỏ tới tài sản này.
async function getDeliveryUrl({ db, cloud, uid, input, nowMs }) {
  const { relationshipId, memoryId } = input;
  const rel = (await refs.rel(db, relationshipId).get()).data();
  const problem = planDelivery({ rel, uid });
  if (problem) throw fail(problem.code, problem.message);

  const publicId = policy.publicIdFor(memoryId);
  const memory = await refs.memory(db, relationshipId, memoryId).get();
  if (!memory.exists || memory.get('cloudinaryPublicId') !== publicId) {
    throw fail('not-found', 'Không tìm thấy tệp của kỷ niệm này');
  }
  const asset = readAsset(await refs.asset(db, memoryId).get());
  if (!asset || asset.state !== 'active' || asset.relationshipId !== relationshipId) {
    throw fail('not-found', 'Tệp chưa sẵn sàng');
  }
  const ttl = cfg.timing.deliveryTtlSeconds;
  return { url: cloud.deliveryUrl(publicId, asset.kind, ttl), expiresAtMillis: nowMs + ttl * 1000 };
}

// Memory bị xoá hoặc bỏ ảnh: xoá tệp của nó. Lỗi thì trigger retry, hoặc sweeper dọn ở bước deleting.
async function releaseAssetForMemory({ db, cloud, relationshipId, memoryId, nowMs }) {
  await purgeOne({
    db,
    cloud,
    memoryId,
    nowMs,
    shouldClaim: (a) => planRelease(a, relationshipId),
    reason: 'memory_deleted',
  });
}

// Kiểm tra một tài sản active đã đến hạn. Đọc memory trong cùng transaction với việc đổi trạng thái,
// để không xoá tệp mà memory vừa ghi xong. Trả về 'orphaned', 'rechecked', hoặc null.
async function checkOrphan({ db, cloud, memoryId, nowMs }) {
  const ref = refs.asset(db, memoryId);
  const decision = await db.runTransaction(async (tx) => {
    const asset = readAsset(await tx.get(ref));
    if (!asset) return null;
    const memorySnap = await tx.get(refs.memory(db, asset.relationshipId, memoryId));
    const memoryPublicId = memorySnap.exists ? (memorySnap.get('cloudinaryPublicId') ?? '') : undefined;
    const action = planOrphan({ asset, memoryPublicId, nowMs });
    if (action === 'recheck') {
      tx.update(ref, { orphanCheckAt: new Date(nowMs + cfg.timing.orphanAfterMs) });
    } else if (action === 'purge') {
      tx.update(ref, { state: 'deleting', reason: 'orphan' });
    }
    return action ? { action, asset } : null;
  });
  if (!decision) return null;
  if (decision.action === 'recheck') return 'rechecked';
  await cloud.destroy(decision.asset.publicId, decision.asset.kind);
  await markDeleted(db, memoryId, nowMs);
  return 'orphaned';
}

// Dọn định kỳ, bốn bước:
//   1) reserved quá hạn -> expired (chưa xoá tệp)
//   2) expired đủ thời gian ân hạn -> xoá tệp trên Cloudinary (kể cả tệp upload muộn)
//   3) deleting còn sót (lần xoá trước lỗi) -> thử xoá lại
//   4) active đến hạn kiểm tra mà không memory nào trỏ tới -> xoá (mồ côi); còn trỏ tới thì hẹn kiểm tra lại
async function sweep({ db, cloud, nowMs, batchSize = cfg.timing.sweepBatchSize }) {
  const summary = { expired: 0, purged: 0, retried: 0, orphaned: 0, rechecked: 0, failed: 0 };
  const assets = db.collection('mediaAssets');

  const reserved = await assets.where('state', '==', 'reserved')
    .where('expiresAt', '<=', new Date(nowMs)).limit(batchSize).get();
  await sweepEach(reserved.docs, summary, 'expire', async (memoryId) => {
    const done = await claim(db, memoryId, (a) => planExpire(a, nowMs), { state: 'expired', expiredAt: new Date(nowMs) });
    return done ? 'expired' : null;
  });

  const expired = await assets.where('state', '==', 'expired')
    .where('expiredAt', '<=', new Date(nowMs - cfg.timing.lateUploadGraceMs)).limit(batchSize).get();
  await sweepEach(expired.docs, summary, 'purge', async (memoryId) => {
    const done = await purgeOne({ db, cloud, memoryId, nowMs, shouldClaim: (a) => planPurge(a, nowMs), reason: 'expired' });
    return done ? 'purged' : null;
  });

  // ponytail: không có con trỏ, mỗi lượt chỉ lấy batchSize mục đầu. Nếu chính các mục đó luôn lỗi thì mục sau không bao giờ được thử lại.
  // Nâng cấp: phân trang bằng startAfter theo mốc cập nhật trạng thái (cần thêm trường và index).
  const deleting = await assets.where('state', '==', 'deleting').limit(batchSize).get();
  await sweepEach(deleting.docs, summary, 'retry', async (memoryId) => {
    const done = await purgeOne({ db, cloud, memoryId, nowMs, shouldClaim: (a) => a.state === 'deleting', reason: 'retry' });
    return done ? 'retried' : null;
  });

  const due = await assets.where('state', '==', 'active')
    .where('orphanCheckAt', '<=', new Date(nowMs)).limit(batchSize).get();
  await sweepEach(due.docs, summary, 'orphan', (memoryId) => checkOrphan({ db, cloud, memoryId, nowMs }));

  return summary;
}

// Chạy từng bản ghi; lỗi của một bản ghi không làm dừng cả lô. step trả về khoá cần cộng trong summary, hoặc null.
async function sweepEach(docs, summary, stage, step) {
  for (const doc of docs) {
    try {
      const outcome = await step(doc.id);
      if (outcome) summary[outcome] += 1;
    } catch (err) {
      summary.failed += 1;
      const message = err?.message ?? err?.error?.message ?? String(err);
      logger.error('sweep: lỗi', { stage, memoryId: doc.id, message });
    }
  }
}

module.exports = {
  isMember,
  planReserve,
  planConfirm,
  planCapacity,
  planGlobalCapacity,
  alreadyConfirmed,
  planRelease,
  planExpire,
  planPurge,
  planOrphan,
  planDelivery,
  dailyKeys,
  storageKey,
  GLOBAL_STORAGE_KEY,
  readAsset,
  reserveUpload,
  confirmUpload,
  getDeliveryUrl,
  releaseAssetForMemory,
  purgeOne,
  sweep,
};
