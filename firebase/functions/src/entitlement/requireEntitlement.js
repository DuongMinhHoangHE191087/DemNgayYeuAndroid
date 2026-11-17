'use strict';

// Cổng quyền phía server (p6 M-25): quyết định VIP chỉ từ dữ liệu lưu + đồng hồ server (deps.nowMs), không bao giờ từ client.
// Mặc định TỪ CHỐI: Sandbox, stale, hết hạn, bị thu hồi không qua cổng `vip` (luật nằm ở resolveEntitlement / evaluateGate, không lặp lại ở đây).
// Gate `free` không đọc store; `tiered` / `vip` tra lại store (ensureFresh) TRƯỚC khi quyết định để bắt hoàn tiền bị lỡ webhook.
const { HttpsError } = require('firebase-functions/v2/https');
const { ensureFresh } = require('./ensureFresh');
const { resolveEntitlement } = require('./resolveEntitlement');
const { evaluateGate } = require('./gatePolicy');

// Trả { tier } (null | 'free' | 'vip') hoặc ném permission-denied 'vip-required'; gate lạ thì evaluateGate ném Error.
async function enforceGate(deps, { uid, gate, freeAllowed }) {
  if (gate === 'free') return evaluateGate({ gate });
  const { purchases, adminGrant } = await ensureFresh(deps, { uid });
  const entitlement = resolveEntitlement(purchases, deps.nowMs(), deps.policy, adminGrant);
  const result = evaluateGate({ gate, freeAllowed, entitlement });
  if (result.denied) throw new HttpsError('permission-denied', result.denied, { reason: result.denied });
  return { tier: result.tier };
}

// Cho callable `vip` thêm sau (ví dụ proxy Gemini).
const requireEntitlement = (deps, { uid }) => enforceGate(deps, { uid, gate: 'vip' });

module.exports = { enforceGate, requireEntitlement };
