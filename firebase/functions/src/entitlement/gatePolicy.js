'use strict';

// Phân loại callable theo cổng quyền (p6 B8) + hàm thuần evaluateGate (không firebase-admin, không đọc đồng hồ).
// Mặc định TỪ CHỐI: chỉ `entitlement.vip === true` VÀ `environment === 'Production'` mới là VIP thật; Sandbox không bao giờ qua.
// Callable mới PHẢI có hàng ở đây (gateCoverage.test.js, M-84). Gemini (firebase-ai) chưa có callable server; proxy sau này PHẢI đặt gate `vip`.

// Quyết định chủ dự án (p6 §9 mục 17): true = người dùng miễn phí vẫn dùng media (giữ hành vi Android); false = chỉ VIP.
const MEDIA_FREE_ALLOWED = true;

const free = Object.freeze({ gate: 'free' });
const media = Object.freeze({ gate: 'tiered', freeAllowed: MEDIA_FREE_ALLOWED });

const GATES = Object.freeze({
  acceptCoupleInvite: free,
  requestAccountDeletion: free,
  getAccountToken: free,
  claimPurchase: free,
  getEntitlement: free,
  signAvatarUpload: free,
  signMemoryUpload: media,
  confirmMemoryUpload: media,
  getMemoryMediaUrl: media,
});

const DENIED = 'vip-required';

// Trả { tier } (tier: null | 'free' | 'vip') hoặc { denied: 'vip-required' }; gate lạ thì ném Error.
function evaluateGate({ gate, freeAllowed, entitlement } = {}) {
  const realVip = entitlement?.vip === true && entitlement?.environment === 'Production';
  switch (gate) {
    case 'free':
      return { tier: null };
    case 'vip':
      return realVip ? { tier: 'vip' } : { denied: DENIED };
    case 'tiered':
      if (realVip) return { tier: 'vip' };
      return freeAllowed === true ? { tier: 'free' } : { denied: DENIED };
    default:
      throw new Error(`Unknown gate: ${String(gate)}`);
  }
}

module.exports = { GATES, MEDIA_FREE_ALLOWED, evaluateGate };
