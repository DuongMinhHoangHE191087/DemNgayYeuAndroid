'use strict';

// Token entitlement ký ES256 (JWS compact) mà client chỉ tin token này (p6 B9). Hàm thuần: chỉ node:crypto,
// không firebase-admin, không đọc đồng hồ (nowMs do caller tiêm). Mọi lỗi kiểm chứng dùng MỘT thông điệp cố định,
// không nêu lý do và không bao giờ chứa khoá hoặc nội dung token, để không thành oracle.
const crypto = require('node:crypto');
const { DEFAULT_POLICY } = require('./resolveEntitlement');

const BAD_TOKEN = 'invalid entitlement token';
const BAD_KEY = 'invalid signing key';
const KID_RE = /^[A-Za-z0-9_-]{1,64}$/;
const B64U_RE = /^[A-Za-z0-9_-]+$/;
const CLAIM_KEYS = ['environment', 'expiresAtMs', 'iat', 'nonce', 'notAfterMs', 'planId', 'source', 'uid', 'v', 'vip'];
const isStr = (s) => typeof s === 'string' && s.length > 0;
const isNum = (n) => typeof n === 'number' && Number.isFinite(n);

function buildClaims({ uid, nonce, entitlement, verifiedAtMs, nowMs, policy }) {
  if (!isStr(uid) || !isStr(nonce) || !isNum(nowMs) || !entitlement || typeof entitlement !== 'object') {
    throw new TypeError('invalid claims input');
  }
  const pol = { ...DEFAULT_POLICY, ...policy };
  const ttlEnd = nowMs + pol.tokenTtlMs;
  if (!entitlement.vip) {
    return { v: 1, uid, vip: false, planId: null, expiresAtMs: null, source: null, environment: null, iat: nowMs, notAfterMs: ttlEnd, nonce };
  }
  const exp = entitlement.expiresAtMs ?? Infinity;
  const verified = verifiedAtMs !== undefined ? verifiedAtMs : entitlement.verifiedAtMs;
  const staleEnd = verified == null ? Infinity : verified + pol.maxStaleMs;
  if (Number.isNaN(exp) || Number.isNaN(staleEnd)) throw new TypeError('invalid claims input');
  return {
    v: 1, uid, vip: true, planId: entitlement.planId, expiresAtMs: entitlement.expiresAtMs ?? null,
    source: entitlement.source, environment: entitlement.environment, iat: nowMs,
    notAfterMs: Math.min(exp, ttlEnd, staleEnd), nonce,
  };
}

const isP256 = (k) => k.asymmetricKeyType === 'ec' && k.asymmetricKeyDetails?.namedCurve === 'prime256v1';

// Chỉ nhận khoá riêng P-256; mọi sai khác ném lỗi cố định.
function privateKeyOf(pem) {
  let k;
  try { k = crypto.createPrivateKey(pem); } catch { throw new Error(BAD_KEY); }
  if (k.type !== 'private' || !isP256(k)) throw new Error(BAD_KEY);
  return k;
}

function parseSigningKey(json) {
  let o;
  try { o = JSON.parse(json); } catch { throw new Error(BAD_KEY); }
  if (!o || typeof o !== 'object' || Array.isArray(o) || typeof o.kid !== 'string' || !KID_RE.test(o.kid) || typeof o.pem !== 'string') {
    throw new Error(BAD_KEY);
  }
  privateKeyOf(o.pem);
  return { kid: o.kid, pem: o.pem };
}

function signEntitlementToken(claims, { kid, pem } = {}) {
  if (typeof kid !== 'string' || !KID_RE.test(kid)) throw new Error(BAD_KEY);
  const key = privateKeyOf(pem);
  const data = `${Buffer.from(JSON.stringify({ alg: 'ES256', kid })).toString('base64url')}.${Buffer.from(JSON.stringify(claims)).toString('base64url')}`;
  // dsaEncoding ieee-p1363 = r||s 64 byte đúng RFC 7518 mục 3.4 (mặc định của Node là DER, sai cho JWS).
  const sig = crypto.sign('sha256', Buffer.from(data), { key, dsaEncoding: 'ieee-p1363' });
  return `${data}.${sig.toString('base64url')}`;
}

// publicKeys: { kid: SPKI-DER-base64 | KeyObject }. Trả claims hoặc ném đúng một lỗi cố định.
function verifyEntitlementToken(token, publicKeys, { nowMs, uid, nonce } = {}) {
  try {
    if (typeof token !== 'string' || !isNum(nowMs) || !isStr(uid) || !isStr(nonce) || !publicKeys || typeof publicKeys !== 'object') throw 0;
    const parts = token.split('.');
    if (parts.length !== 3) throw 0;
    // Chỉ chấp nhận base64url chuẩn tắc (không đệm '=', không bit thừa): một token chỉ có một dạng chuỗi.
    const bytes = parts.map((p) => {
      if (!B64U_RE.test(p)) throw 0;
      const b = Buffer.from(p, 'base64url');
      if (b.toString('base64url') !== p) throw 0;
      return b;
    });
    const header = JSON.parse(bytes[0].toString('utf8'));
    if (!header || typeof header !== 'object' || Object.keys(header).sort().join() !== 'alg,kid') throw 0;
    if (header.alg !== 'ES256' || typeof header.kid !== 'string' || !KID_RE.test(header.kid)) throw 0;
    if (!Object.hasOwn(publicKeys, header.kid) || bytes[2].length !== 64) throw 0;
    const raw = publicKeys[header.kid];
    const key = typeof raw === 'string' ? crypto.createPublicKey({ key: Buffer.from(raw, 'base64'), format: 'der', type: 'spki' }) : raw;
    if (!key || key.type !== 'public' || !isP256(key)) throw 0;
    const ok = crypto.verify('sha256', Buffer.from(`${parts[0]}.${parts[1]}`), { key, dsaEncoding: 'ieee-p1363' }, bytes[2]);
    if (ok !== true) throw 0;

    const c = JSON.parse(bytes[1].toString('utf8'));
    if (!c || typeof c !== 'object' || Array.isArray(c) || Object.keys(c).sort().join() !== CLAIM_KEYS.join()) throw 0;
    if (c.v !== 1 || typeof c.vip !== 'boolean' || !isStr(c.uid) || !isStr(c.nonce) || !isNum(c.iat) || !isNum(c.notAfterMs) || c.notAfterMs <= c.iat) throw 0;
    if (c.vip) {
      if (!isStr(c.planId) || !isStr(c.source) || !isStr(c.environment) || !(c.expiresAtMs === null || isNum(c.expiresAtMs))) throw 0;
    } else if (c.planId !== null || c.expiresAtMs !== null || c.source !== null || c.environment !== null) throw 0;
    if (c.uid !== uid || c.nonce !== nonce || !(nowMs < c.notAfterMs)) throw 0;
    return c;
  } catch {
    throw new Error(BAD_TOKEN);
  }
}

module.exports = { buildClaims, signEntitlementToken, parseSigningKey, verifyEntitlementToken };
