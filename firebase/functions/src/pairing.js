'use strict';

// Ghép đôi nguyên tử phía máy chủ: chấp nhận lời mời + tạo relationship trong MỘT transaction.
// Máy khách không còn tự ghi relationships (firestore.rules chặn create), nên "mỗi uid chỉ một quan hệ
// đang hoạt động" và "nhận lời mời hai lần" được ép tại đây, không phụ thuộc client.
const { HttpsError } = require('firebase-functions/v2/https');
const { FieldValue } = require('firebase-admin/firestore');
const { isMember } = require('./media');

const fail = (code, message) => new HttpsError(code, message);

// Quan hệ còn chiếm chỗ của uid: đang hoạt động hoặc đang chờ chia tay. TERMINATED thì uid được ghép lại.
const OCCUPYING_STATUSES = ['ACTIVE', 'PENDING_BREAKUP'];

const refs = {
  invite: (db, id) => db.collection('invites').doc(id),
  rel: (db, id) => db.collection('relationships').doc(id),
};

// ---------- Quyết định thuần ----------

function parseAcceptInput(data) {
  const inviteId = typeof data?.inviteId === 'string' ? data.inviteId.trim() : '';
  if (!inviteId || inviteId.length > 128 || inviteId.includes('/')) {
    throw fail('invalid-argument', 'Thiếu mã lời mời hợp lệ');
  }
  const raw = data?.startDateMillis;
  const startDateMillis = Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : null;
  const text = typeof data?.startDateText === 'string' ? data.startDateText.trim().slice(0, 32) : '';
  return { inviteId, startDateMillis, startDateText: text || null };
}

// ponytail: dd/MM/yyyy theo giờ Việt Nam (UTC+7 cố định, không DST). Máy khách gửi startDateText thì dùng nguyên văn.
function formatVnDate(ms) {
  const d = new Date(ms + 7 * 3600 * 1000);
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getUTCDate())}/${p(d.getUTCMonth() + 1)}/${d.getUTCFullYear()}`;
}

/**
 * Quyết định cho một lần nhận lời mời. Trả về {action: 'create' | 'noop'}; ném HttpsError nếu không hợp lệ.
 * - invite: dữ liệu invites/{id} (hoặc null); existingRel: relationships/{id} (hoặc null).
 * - occupied: {uid: relId[]} các quan hệ khác đang chiếm chỗ của hai bên (đã loại relationship cùng id).
 */
function planAccept({ invite, existingRel, callerUid, occupied }) {
  if (!invite) throw fail('not-found', 'Không tìm thấy lời mời');
  const target = invite.targetUid || invite.receiverUid;
  if (target !== callerUid) throw fail('permission-denied', 'Lời mời này không dành cho bạn');
  const senderUid = invite.senderUid;
  if (!senderUid || senderUid === callerUid) throw fail('failed-precondition', 'Lời mời không hợp lệ');

  // Nhận lại lời mời đã nhận xong: idempotent, chỉ cần quan hệ đã tồn tại đúng hai người.
  if (invite.status === 'ACCEPTED') {
    if (existingRel && isMember(existingRel, callerUid) && isMember(existingRel, senderUid)) return { action: 'noop' };
    throw fail('failed-precondition', 'Lời mời đã được xử lý');
  }
  if (invite.status !== 'PENDING') throw fail('failed-precondition', 'Lời mời không còn hiệu lực');

  for (const uid of [callerUid, senderUid]) {
    if ((occupied?.[uid] ?? []).length > 0) {
      throw fail('failed-precondition', uid === callerUid
        ? 'Bạn đang có một mối quan hệ khác'
        : 'Người gửi lời mời đã có một mối quan hệ khác');
    }
  }
  return { action: 'create', senderUid };
}

function buildRelationship({ senderUid, callerUid, startDateMillis, startDateText, nowMs }) {
  return {
    partnerAId: senderUid,
    partnerBId: callerUid,
    user1: senderUid,
    user2: callerUid,
    startDate: startDateMillis,
    startDateText,
    status: 'ACTIVE',
    createdAt: nowMs,
    updatedAt: nowMs,
    createdAtServer: FieldValue.serverTimestamp(),
  };
}

// ---------- Ghi dữ liệu ----------

async function findOccupied(tx, db, uid, excludeRelId) {
  const found = [];
  for (const field of ['partnerAId', 'partnerBId']) {
    // ponytail: hai trường × một truy vấn; quan hệ cũ chỉ có user1/user2 sẽ lọt. Thêm hai truy vấn nữa nếu dữ liệu cũ còn.
    const snap = await tx.get(db.collection('relationships')
      .where(field, '==', uid).where('status', 'in', OCCUPYING_STATUSES));
    snap.forEach((doc) => { if (doc.id !== excludeRelId) found.push(doc.id); });
  }
  return found;
}

async function acceptCoupleInvite({ db, uid, input, nowMs }) {
  return db.runTransaction(async (tx) => {
    const inviteSnap = await tx.get(refs.invite(db, input.inviteId));
    const invite = inviteSnap.exists ? inviteSnap.data() : null;
    const relSnap = await tx.get(refs.rel(db, input.inviteId));
    const existingRel = relSnap.exists ? relSnap.data() : null;

    const occupied = {};
    if (invite && invite.status === 'PENDING') {
      for (const who of [uid, invite.senderUid]) {
        if (who && !(who in occupied)) occupied[who] = await findOccupied(tx, db, who, input.inviteId);
      }
    }
    const plan = planAccept({ invite, existingRel, callerUid: uid, occupied });
    if (plan.action === 'noop') return { relationshipId: input.inviteId, alreadyAccepted: true };

    const startDateMillis = input.startDateMillis ?? (invite.proposedStartDate > 0 ? invite.proposedStartDate : nowMs);
    tx.update(refs.invite(db, input.inviteId), { status: 'ACCEPTED' });
    tx.set(refs.rel(db, input.inviteId), buildRelationship({
      senderUid: plan.senderUid,
      callerUid: uid,
      startDateMillis,
      startDateText: input.startDateText ?? formatVnDate(startDateMillis),
      nowMs,
    }));
    return { relationshipId: input.inviteId, alreadyAccepted: false, senderUid: plan.senderUid, startDateMillis };
  });
}

module.exports = { parseAcceptInput, planAccept, buildRelationship, acceptCoupleInvite, OCCUPYING_STATUSES };
