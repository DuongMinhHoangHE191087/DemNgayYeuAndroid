'use strict';

// Callable lõi getEntitlement (p6 M-18): server là nguồn sự thật duy nhất. Mặc định TỪ CHỐI.
// BẢO MẬT: kết quả đã lưu ở entitlements/{uid} KHÔNG bao giờ được tin (chỉ adminGrant được đọc, qua readUidState); luôn tính lại từ các purchase vừa
// tra mới bằng giờ server (deps.nowMs), không dùng giờ của client; token ký ES256 gắn uid + nonce của đúng lời gọi này.
const { HttpsError } = require('firebase-functions/v2/https');
const { ensureFresh } = require('./ensureFresh');
const { resolveEntitlement } = require('./resolveEntitlement');
const { buildClaims, signEntitlementToken } = require('./entitlementToken');

const NONCE_RE = /^[A-Za-z0-9_-]{16,64}$/;

function parseNonce(data) {
  const nonce = data?.nonce;
  if (typeof nonce !== 'string' || !NONCE_RE.test(nonce)) throw new HttpsError('invalid-argument', 'nonce is invalid');
  return nonce;
}

async function getEntitlement(deps, { uid, nonce }) {
  parseNonce({ nonce }); // nơi gọi đã parse; kiểm lại để hàm này không bao giờ ký token cho nonce sai
  const { purchases, adminGrant } = await ensureFresh(deps, { uid });
  const nowMs = deps.nowMs();
  const entitlement = resolveEntitlement(purchases, nowMs, deps.policy, adminGrant);
  const claims = buildClaims({ uid, nonce, entitlement, nowMs, policy: deps.policy });
  const { vip, planId, state, expiresAtMs, source, environment } = entitlement;
  return { vip, planId, state, expiresAtMs, source, environment, serverNowMs: nowMs, token: signEntitlementToken(claims, deps.signingKey) };
}

module.exports = { getEntitlement, parseNonce };
