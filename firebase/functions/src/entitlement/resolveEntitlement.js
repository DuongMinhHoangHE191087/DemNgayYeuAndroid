'use strict';

// Quyết định VIP từ các purchase đã lưu: hàm thuần, không firebase-admin, không đọc đồng hồ (nowMs do caller tiêm).
// Luật: p6 §4.2; golden GV-061 (shared/.../golden/GV-061-entitlement-resolve.json). Mặc định TỪ CHỐI: mọi dữ liệu
// thiếu, lạ hoặc không hữu hạn đều dẫn tới không VIP.
const HOUR = 3600000;
const DAY = 24 * HOUR;

const DEFAULT_POLICY = Object.freeze({
  graceMaxDays: 16,
  sandboxTtlMs: 24 * HOUR,
  reverifyAfterMs: 24 * HOUR,
  maxStaleMs: 96 * HOUR,
  tokenTtlMs: 72 * HOUR,
  maxUnfinished: 10,
  maxNewBindings24h: 5,
  maxSandboxBindings: 3,
  recheckGapMs: 10 * 60000,
  nearExpiryMs: 5 * 60000,
  terminalRecheckGapMs: 24 * HOUR,
  terminalWindowMs: 30 * DAY,
});

// Thứ tự ưu tiên của state khi không có VIP (đứng trước thắng).
const DENY_RANK = ['revoked', 'retry', 'pending', 'expired'];

// Hạn quyền của một purchase, chưa xét đồng hồ và độ tươi: { until } (Infinity = vô hạn) hoặc { deny } (state lý do, có thể null).
function claim(p, pol) {
  if (p.revokedAtMs != null || p.status === 'revoked') return { deny: 'revoked' };
  const sandbox = p.environment === 'Sandbox';
  if (!sandbox && p.environment !== 'Production') return { deny: null };
  let until;
  if (p.status === 'active') {
    if (p.kind === 'lifetime') until = Infinity;
    else if (p.kind === 'subscription') until = p.expiresAtMs;
    else return { deny: null };
  } else if (p.status === 'grace') {
    if (p.kind !== 'subscription') return { deny: null };
    if (p.store === 'google') until = p.expiresAtMs; // Google tự kéo expiryTime trong grace: không cộng ngày
    else if (p.store === 'apple') {
      // Không có graceExpiresAtMs thật từ Apple thì KHÔNG bịa khoảng grace.
      if (!Number.isFinite(p.graceExpiresAtMs)) return { deny: 'retry' };
      if (!Number.isFinite(p.expiresAtMs)) return { deny: 'expired' };
      until = Math.min(p.graceExpiresAtMs, p.expiresAtMs + pol.graceMaxDays * DAY);
    } else return { deny: null };
  } else if (p.status === 'retry' || p.status === 'pending' || p.status === 'expired') {
    return { deny: p.status };
  } else return { deny: null };
  if (until !== Infinity && !Number.isFinite(until)) return { deny: 'expired' };
  if (sandbox) {
    // Sandbox chỉ là quyền có trần, tính từ lần đầu thấy; thiếu mốc thì từ chối.
    if (!Number.isFinite(p.firstSeenAtMs)) return { deny: null };
    until = Math.min(until, p.firstSeenAtMs + pol.sandboxTtlMs);
  }
  return { until };
}

// Trả { vip, planId, state, expiresAtMs, source, environment, verifiedAtMs }; expiresAtMs null = vô hạn (khi vip) hoặc không có.
function resolveEntitlement(purchases, nowMs, policy, adminGrant) {
  const pol = { ...DEFAULT_POLICY, ...policy };
  const candidates = [];
  let stale = false;
  let denyState = null;
  const deny = (s) => {
    if (s && (denyState === null || DENY_RANK.indexOf(s) < DENY_RANK.indexOf(denyState))) denyState = s;
  };

  for (const p of purchases || []) {
    if (!p || p.supersededBy != null) continue;
    const c = claim(p, pol);
    if (c.until === undefined) { deny(c.deny); continue; }
    if (!(nowMs < c.until)) { deny('expired'); continue; }
    // Đã có quyền theo dữ liệu lưu, nhưng chỉ tính khi store được xác minh trong maxStaleMs.
    if (!(Number.isFinite(p.verifiedAtMs) && p.verifiedAtMs + pol.maxStaleMs > nowMs)) { stale = true; continue; }
    candidates.push({ until: c.until, prod: p.environment === 'Production', purchase: p });
  }

  if (adminGrant && typeof adminGrant === 'object') {
    const u = adminGrant.untilMs;
    if (u === null || (Number.isFinite(u) && nowMs < u)) {
      candidates.push({ until: u === null ? Infinity : u, prod: true, purchase: null });
    }
  }

  // Production/admin thắng Sandbox; rồi hạn xa nhất (lifetime = Infinity); hoà thì purchase thắng admin, rồi key tăng dần.
  candidates.sort((a, b) => (
    (b.prod - a.prod)
    || (a.until === b.until ? 0 : (b.until > a.until ? 1 : -1))
    || ((b.purchase !== null) - (a.purchase !== null))
    || String(a.purchase?.key).localeCompare(String(b.purchase?.key))
  ));
  const w = candidates[0];
  if (!w) {
    return { vip: false, planId: null, state: stale ? 'stale' : (denyState || 'none'), expiresAtMs: null, source: null, environment: null, verifiedAtMs: null };
  }
  const expiresAtMs = w.until === Infinity ? null : w.until;
  if (!w.purchase) {
    return { vip: true, planId: 'admin', state: 'active', expiresAtMs, source: 'admin', environment: 'Production', verifiedAtMs: null };
  }
  const p = w.purchase;
  return { vip: true, planId: p.planId, state: p.status, expiresAtMs, source: p.store, environment: p.environment, verifiedAtMs: p.verifiedAtMs };
}

module.exports = { resolveEntitlement, DEFAULT_POLICY };
