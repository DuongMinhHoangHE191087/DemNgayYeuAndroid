'use strict';

// Xoá tài khoản bền vững phía máy chủ. Callable chỉ kiểm tra đăng nhập gần đây rồi ghi việc
// accountDeletions/{uid} = pending; trigger xử lý và tự thử lại (retry) tới khi xong, nên mất mạng
// giữa chừng không để lại tài khoản xoá dở. Mọi bước idempotent.
// Quyết định chủ sở hữu: xoá nội dung/tệp do người yêu cầu tạo, ngắt liên kết cặp đôi (TERMINATED)
// nhưng GIỮ dữ liệu của đối phương. Không xoá kỷ niệm do đối phương viết.
const { HttpsError } = require('firebase-functions/v2/https');
const logger = require('firebase-functions/logger');

// Phải đăng nhập lại trong khoảng này mới được xoá (auth_time của ID token, đơn vị giây).
const RECENT_LOGIN_MS = 5 * 60 * 1000;
const PAGE = 400;

const jobRef = (db, uid) => db.collection('accountDeletions').doc(uid);

// ---------- Quyết định thuần ----------

function requireRecentLogin(authTimeSec, nowMs) {
  const ok = Number.isFinite(authTimeSec) && nowMs - authTimeSec * 1000 <= RECENT_LOGIN_MS;
  if (!ok) {
    throw new HttpsError('failed-precondition', 'Cần đăng nhập lại gần đây để xoá tài khoản', { reason: 'recent-login-required' });
  }
}

// Tệp còn tồn tại thì gom; 'deleted' đã xong. 'deleting' vẫn thử lại vì destroy idempotent.
const needsPurge = (asset) => asset.state !== 'deleted' && asset.state !== 'expired';

// ---------- Ghi dữ liệu ----------

// Xoá hết kết quả truy vấn theo trang để không vượt giới hạn batch. Trả về số tài liệu đã xoá.
async function deleteQuery(db, query) {
  let total = 0;
  for (;;) {
    const snap = await query.limit(PAGE).get();
    if (snap.empty) return total;
    const batch = db.batch();
    snap.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    total += snap.size;
    if (snap.size < PAGE) return total;
  }
}

async function requestAccountDeletion({ db, uid, authTimeSec, nowMs }) {
  requireRecentLogin(authTimeSec, nowMs);
  // set merge: gọi lại sau khi lỗi sẽ kích hoạt lại trigger.
  await jobRef(db, uid).set({ state: 'pending', requestedAt: nowMs, lastError: null }, { merge: true });
  return { accepted: true };
}

// Mối quan hệ của uid: tìm theo cả hai bộ trường để không sót dữ liệu cũ chỉ có user1/user2.
async function relationshipsOf(db, uid) {
  const found = new Map();
  for (const field of ['partnerAId', 'partnerBId', 'user1', 'user2']) {
    const snap = await db.collection('relationships').where(field, '==', uid).get();
    snap.forEach((doc) => found.set(doc.id, doc));
  }
  return [...found.values()];
}

async function processAccountDeletion({ db, auth, purge, uid, nowMs }) {
  // 1. Mọi mối quan hệ: xoá kỷ niệm của uid rồi ngắt liên kết, giữ phần của đối phương.
  for (const rel of await relationshipsOf(db, uid)) {
    await deleteQuery(db, rel.ref.collection('memories').where('authorId', '==', uid));
    if (rel.get('status') !== 'TERMINATED') {
      await rel.ref.update({ status: 'TERMINATED', updatedAt: nowMs, terminatedReason: 'ACCOUNT_DELETED' });
    }
  }

  // 2. Tệp Cloudinary do uid sở hữu (kể cả tệp upload dở không có memory).
  const assets = await db.collection('mediaAssets').where('ownerUid', '==', uid).get();
  for (const doc of assets.docs) {
    if (!needsPurge(doc.data())) continue;
    await purge({ memoryId: doc.id, nowMs, shouldClaim: needsPurge, reason: 'account-deleted' });
  }

  // 3. Lời mời, mã cặp đôi, kỷ niệm cũ ở cấp gốc, hồ sơ.
  await deleteQuery(db, db.collection('invites').where('senderUid', '==', uid));
  await deleteQuery(db, db.collection('invites').where('receiverUid', '==', uid));
  await deleteQuery(db, db.collection('invites').where('targetUid', '==', uid));
  await deleteQuery(db, db.collection('coupleCodes').where('ownerUid', '==', uid));
  await deleteQuery(db, db.collection('memories').where('authorUid', '==', uid));
  await db.collection('users').doc(uid).delete();

  // 4. Danh tính Auth đi cuối: nếu bước trước lỗi thì tài khoản còn, người dùng đăng nhập thử lại được.
  try {
    await auth.deleteUser(uid);
  } catch (err) {
    if (err?.code !== 'auth/user-not-found') throw err;
  }
  await jobRef(db, uid).set({ state: 'done', doneAt: nowMs, lastError: null }, { merge: true });
  logger.info('processAccountDeletion: xong', { uid });
}

// Trigger gọi hàm này: chỉ chạy khi việc đang pending; lỗi thì ghi lastError và ném lại để Firebase retry.
async function runJob({ db, auth, purge, uid, nowMs }) {
  const snap = await jobRef(db, uid).get();
  if (!snap.exists || snap.get('state') !== 'pending') return;
  try {
    await processAccountDeletion({ db, auth, purge, uid, nowMs });
  } catch (err) {
    const message = err?.message ?? String(err);
    await jobRef(db, uid).set({ lastError: message.slice(0, 500), lastErrorAt: nowMs }, { merge: true });
    throw err;
  }
}

module.exports = { requireRecentLogin, needsPurge, requestAccountDeletion, processAccountDeletion, runJob, RECENT_LOGIN_MS };
