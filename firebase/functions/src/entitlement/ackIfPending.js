'use strict';

// Acknowledge phía server bền vững (p6 M-101, B7): Google hoàn tiền purchase không ack trong 3 ngày.
// Chỉ ack purchase Google đã ràng buộc, chưa thu hồi, còn hiệu lực và đang `pending`. Thất bại thì lùi lịch thử lại và KHÔNG ném lỗi;
// riêng `force` (trình xử lý RTDN) bỏ qua lịch lùi và ném lại lỗi để Pub/Sub gửi lại.
const BACKOFF_MS = [60000, 300000, 1800000, 21600000]; // 1 phút, 5 phút, 30 phút, rồi 6 giờ

async function ackIfPending(deps, { key, force = false }) {
  const ref = deps.db.collection('purchases').doc(key);
  const snap = await ref.get();
  const p = snap.exists ? snap.data() : null;
  if (!p || p.store !== 'google' || p.tombstone || p.uid === null || p.uid === undefined) return;
  if (p.ackState !== 'pending' || p.revokedAtMs != null || (p.status !== 'active' && p.status !== 'grace')) return;
  const nowMs = deps.nowMs();
  if (!force && nowMs < p.ackNextAtMs) return;

  const method = p.kind === 'lifetime' ? 'acknowledgeProduct' : 'acknowledgeSubscription';
  try {
    await deps.google[method]({ productId: p.productId, purchaseToken: p.lookupId });
  } catch (err) {
    const attempts = (p.ackAttempts ?? 0) + 1;
    await ref.update({ ackAttempts: attempts, ackNextAtMs: nowMs + BACKOFF_MS[Math.min(attempts, BACKOFF_MS.length) - 1] });
    if (force) throw err;
    return;
  }
  await ref.update({ ackState: 'done', ackAttempts: null, ackNextAtMs: null });
}

module.exports = { ackIfPending };
