'use strict';

// Xử lý App Store Server Notifications V2 (p6 M-19). Trả { httpStatus }: 200 = đã nhận (kể cả bỏ qua), 400 = đầu vào/chữ ký hỏng (Apple không cần gửi lại),
// 500 = lỗi tạm thời hoặc cập nhật hỏng (Apple gửi lại). Mặc định TỪ CHỐI: chữ ký hỏng không ghi gì; dấu dedupe do applyStoreState ghi CÙNG transaction với cập nhật.
const { applyStoreState } = require('./applyStoreState');
const { fetchNormalized } = require('./fetchNormalized');
const { normalizeApple } = require('./normalizeApple');

const MAX_PAYLOAD_CHARS = 65536;
const OK = { httpStatus: 200 };
const BAD = { httpStatus: 400 };
const RETRY = { httpStatus: 500 };

const isRevocation = (t) => t.revocationDate !== undefined && t.revocationDate !== null;
const nonEmpty = (v) => typeof v === 'string' && v !== '';

// REFUND/REVOKE là thông báo về MỘT giao dịch, nhưng mọi giao dịch của chuỗi dùng chung apple_<originalTransactionId>: áp thẳng giao dịch bị hoàn tiền
// sẽ tắt cả chuỗi. Hỏi trạng thái hiện tại; kỳ bị hoàn tiền không phải kỳ mới nhất và chuỗi chưa bị thu hồi thì áp ảnh chụp đó (kỳ mới đang trả giữ nguyên).
// Quyết định theo latestTransactionId chứ không theo status 5 (UNVERIFIED: Apple có đánh status 5 cho cả chuỗi khi chỉ hoàn tiền kỳ cũ hay không).
// Trả purchase cần áp, hoặc null khi không xác định được (caller trả 500 để Apple gửi lại); fetchNormalized ném `unavailable` thì ném lại.
async function purchaseForRevocation(deps, notificationPurchase) {
  const snap = await fetchNormalized(deps, { store: 'apple', id: notificationPurchase.lookupId });
  if (snap.notOurs) return null;
  const { purchase } = snap;
  if (purchase.key !== notificationPurchase.key) return null;
  if (purchase.latestTransactionId !== notificationPurchase.latestTransactionId && purchase.status !== 'revoked') return purchase;
  return notificationPurchase;
}

async function handleAppleNotification(deps, args) {
  const signedPayload = args?.signedPayload;
  if (!nonEmpty(signedPayload) || signedPayload.length > MAX_PAYLOAD_CHARS) return BAD;

  let n;
  try {
    n = await deps.apple.verifyNotification(signedPayload);
  } catch (e) {
    return e?.code === 'unverified' ? BAD : RETRY; // unavailable và mọi lỗi lạ: không ghi gì, để Apple gửi lại
  }
  if (!n || typeof n !== 'object') return RETRY;
  if (n.foreign === true) return OK;
  if (n.notificationType === 'TEST' || !n.transaction) return OK;
  // Không có UUID hoặc signedDate hữu hạn thì không dedupe/so sánh stale được: từ chối thay vì đoán.
  if (!nonEmpty(n.notificationUUID) || !Number.isFinite(n.signedDateMs)) return BAD;

  try {
    let purchase = normalizeApple({
      transaction: n.transaction, renewalInfo: n.renewalInfo, status: n.status, bundleId: deps.bundleId, asOfMs: n.signedDateMs,
    });
    if (purchase.notOurs) return OK;
    if (isRevocation(n.transaction)) {
      purchase = await purchaseForRevocation(deps, purchase);
      if (purchase === null) return RETRY;
    }
    await applyStoreState(deps, { purchase, claimerUid: null, dedupeKey: `apple_${n.notificationUUID}` });
    return OK; // kể cả ignored (duplicate, stale) hoặc bị từ chối: gửi lại không đổi kết quả
  } catch {
    return RETRY;
  }
}

module.exports = { handleAppleNotification };
