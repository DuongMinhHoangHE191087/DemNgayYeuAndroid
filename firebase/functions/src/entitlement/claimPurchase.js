'use strict';

// Client claim một purchase của store (p6 M-17). BẢO MẬT: client chỉ gửi {store, id}, mọi trường khác bị bỏ qua; server tự hỏi store
// (fetchNormalized) rồi để applyStoreState/bindPurchase quyết định quyền sở hữu. Mặc định TỪ CHỐI. Phản hồi {vip, planId, state}
// không ký, chỉ để hiển thị: client không bao giờ dùng nó để cấp VIP (VIP luôn đọc từ entitlements/{uid}).
const { HttpsError } = require('firebase-functions/v2/https');
const { fetchNormalized } = require('./fetchNormalized');
const { applyStoreState } = require('./applyStoreState');
const { ackIfPending } = require('./ackIfPending');

const MAX_ID_LENGTH = 4096;
const HOUR_MS = 3600000;
// Lý do bị từ chối mà bộ phận hỗ trợ cần để tìm `key` cho rebindPurchase: ghi audit. Hai lý do còn lại (không phải lỗi của người dùng) thì không.
const AUDITED = ['already-bound', 'owner-deleted', 'token-required', 'legacy-window-closed'];
const REPORTED = [...AUDITED, 'family-shared-unsupported', 'too-many-active'];
const DENY_STATES = ['pending', 'retry', 'revoked', 'expired'];

// Chỉ giữ store và id; mọi trường khác (uid, kind, planId, vip...) bị bỏ.
function parseClaimInput(data) {
  const { store, id } = data ?? {};
  if (store !== 'apple' && store !== 'google') throw new HttpsError('invalid-argument', 'invalid store');
  if (typeof id !== 'string' || id === '' || id.length > MAX_ID_LENGTH) throw new HttpsError('invalid-argument', 'invalid id');
  return { store, id };
}

// Một tài liệu mỗi purchase mỗi giờ (cùng id thì ghi đè). Một `set` NGOÀI transaction; lỗi ghi bị nuốt, không đổi kết quả từ chối.
async function auditDenied(deps, { uid, key, reason }) {
  try {
    const atMs = deps.nowMs();
    await deps.db.collection('entitlementAudit').doc(`claim-denied_${key}_${Math.floor(atMs / HOUR_MS)}`).set({ uid, key, reason, atMs });
  } catch {
    // nuốt lỗi: audit chỉ là dữ liệu hỗ trợ
  }
}

function responseOf(purchase, entitlement) {
  if (entitlement?.vip === true) return { vip: true, planId: entitlement.planId, state: entitlement.state };
  return { vip: false, planId: purchase.planId, state: DENY_STATES.includes(purchase.status) ? purchase.status : 'expired' };
}

async function claimPurchase(deps, { uid, store, id } = {}) {
  let fetched;
  try {
    fetched = await fetchNormalized(deps, { store, id });
  } catch (e) {
    if (e?.code === 'unavailable') throw new HttpsError('unavailable', 'store unavailable');
    throw e;
  }
  if (fetched.notOurs) throw new HttpsError('permission-denied', 'not-ours', { reason: 'not-ours' }); // mọi lý do, kể cả not-found
  const { purchase } = fetched;

  const result = await applyStoreState(deps, { purchase, claimerUid: uid, dedupeKey: null });
  if (!result.ok) {
    const reason = REPORTED.includes(result.reason) ? result.reason : 'denied'; // lý do lạ (invalid-input...) không lộ ra ngoài
    if (AUDITED.includes(reason)) await auditDenied(deps, { uid, key: purchase.key, reason });
    throw new HttpsError('permission-denied', reason, { reason });
  }

  if (purchase.store === 'google') {
    try {
      await ackIfPending(deps, { key: purchase.key });
    } catch {
      // purchase vẫn được cấp, ackState ở `pending` để lần sau hoặc RTDN thử lại
    }
  }
  return responseOf(purchase, result.entitlement);
}

module.exports = { claimPurchase, parseClaimInput };
