'use strict';

// Giới hạn ràng buộc purchase cho một uid (p6, M-88). Thuần, không đọc db.
// bindPurchase (M-12) gọi sau mọi kiểm tra sở hữu, chỉ với purchase chưa có trong uidPurchases.
const DAY_MS = 24 * 3600000;
const DENY = Object.freeze({ ok: false, reason: 'too-many-active' });
const OK = Object.freeze({ ok: true });

const isLimit = (n) => Number.isInteger(n) && n >= 0;

// Mặc định từ chối: đầu vào hỏng thì chặn, không bao giờ cho qua.
function bindLimits({ uidPurchases, purchase, nowMs, policy } = {}) {
  if (!Array.isArray(uidPurchases) || !purchase || !Number.isFinite(nowMs) || !policy) return DENY;
  const { maxUnfinished, maxNewBindings24h, maxSandboxBindings } = policy;
  if (!isLimit(maxUnfinished) || !isLimit(maxNewBindings24h) || !isLimit(maxSandboxBindings)) return DENY;

  // Cùng key đã ràng buộc cho uid này: không phải ràng buộc mới, không tự đếm chính nó.
  const others = uidPurchases.filter((p) => p && p.key !== purchase.key);

  // Chưa kết thúc = không expired/revoked, chưa bị supersede. Trạng thái lạ vẫn đếm (an toàn hơn).
  const unfinished = others.filter((p) =>
    p.status !== 'expired' && p.status !== 'revoked' && p.revokedAtMs == null && p.supersededBy == null).length;
  if (unfinished >= maxUnfinished) return DENY;

  // boundAtMs thiếu/hỏng thì tính là mới (an toàn hơn).
  const recent = others.filter((p) => !(Number.isFinite(p.boundAtMs) && p.boundAtMs <= nowMs - DAY_MS)).length;
  if (recent >= maxNewBindings24h) return DENY;

  if (purchase.environment === 'Sandbox') {
    const sandbox = others.filter((p) => p.environment === 'Sandbox').length;
    if (sandbox >= maxSandboxBindings) return DENY;
  }
  return OK;
}

module.exports = { bindLimits };
