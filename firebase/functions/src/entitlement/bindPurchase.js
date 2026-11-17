'use strict';

// Ràng buộc purchase -> uid (p6 M-12, §9 luật bindPurchase). Hàm thuần, không đọc db, không ghi gì.
// Mặc định TỪ CHỐI: đầu vào hỏng hoặc thiếu thì chặn, không bao giờ cho qua. Một purchase chỉ gắn cho chủ token đã
// đăng ký; mã giao dịch không phải bằng chứng sở hữu. Bên gọi (M-15) tự ghi `boundAtMs` (= nowMs) khi lưu bản ràng buộc mới.
const { bindLimits } = require('./bindLimits');

const LEGACY_MAX_CLAIMS_PER_UID = 3;

const deny = (reason) => ({ ok: false, reason });
const bound = (uid, legacy = false) => ({ ok: true, uid, orphan: false, legacy });
const ORPHAN = Object.freeze({ ok: true, uid: null, orphan: true });
const ORPHAN_LIMITED = Object.freeze({ ok: true, uid: null, orphan: true, limited: true });

const isStr = (v) => typeof v === 'string' && v !== '';
const isTime = (v) => Number.isFinite(v);

// Phân loại chủ: none (null/undefined), tombstone/deleted, uid hợp lệ, còn lại (hình dạng lạ) là bad.
function ownerOf(o) {
  if (o === null || o === undefined) return { kind: 'none' };
  if (typeof o !== 'object' || Array.isArray(o)) return { kind: 'bad' };
  if (o.tombstone === true || o.deleted === true) return { kind: 'gone' };
  if (isStr(o.uid)) return { kind: 'uid', uid: o.uid };
  return { kind: 'bad' };
}

const validPurchase = (p) => !!p && typeof p === 'object' && (p.store === 'apple' || p.store === 'google') && isStr(p.key);

// Google không token chỉ đi đường legacy khi đủ bằng chứng; thiếu một điều kiện thì token-required, hết cửa sổ thì legacy-window-closed.
function legacyGate(purchase, legacy, nowMs) {
  const l = legacy && typeof legacy === 'object' ? legacy : {};
  const evidence = isTime(l.cutoverMs) && isTime(l.uidCreatedMs) && isTime(purchase.purchaseTimeMs)
    && purchase.purchaseTimeMs < l.cutoverMs && l.uidCreatedMs < l.cutoverMs;
  if (!evidence) return deny('token-required');
  const open = isTime(l.untilMs) && isTime(nowMs) && nowMs < l.untilMs
    && Number.isInteger(l.uidClaims) && l.uidClaims >= 0 && l.uidClaims < LEGACY_MAX_CLAIMS_PER_UID;
  return open ? null : deny('legacy-window-closed');
}

function claim({ claimerUid, purchase, tokenOwner, predecessorOwner, existing, uidPurchases, nowMs, policy, legacy }) {
  // Kiểm tra sở hữu đã gắn trước, bất kể token.
  const ex = ownerOf(existing);
  if (ex.kind === 'gone') return deny('owner-deleted');
  if (ex.kind === 'bad') return deny('already-bound');
  if (ex.kind === 'uid') return ex.uid === claimerUid ? bound(claimerUid) : deny('already-bound'); // cùng uid: idempotent, bỏ qua mọi trần

  // Dòng dõi (M-90) đi TRƯỚC token và legacy; thay thế ròng bằng không nên bỏ qua bindLimits.
  const pre = ownerOf(predecessorOwner);
  if (pre.kind === 'gone') return deny('owner-deleted');
  if (pre.kind === 'bad') return deny('already-bound');
  if (pre.kind === 'uid') return pre.uid === claimerUid ? bound(claimerUid) : deny('already-bound');

  const tok = ownerOf(tokenOwner);
  let viaLegacy = false;
  if (tok.kind === 'gone') return deny('owner-deleted');
  if (tok.kind === 'bad') return deny('already-bound');
  if (tok.kind === 'uid') {
    if (tok.uid !== claimerUid) return deny('already-bound');
    if (!isStr(purchase.accountToken)) return deny('token-required'); // chủ token mà giao dịch không mang token: dữ liệu mâu thuẫn
  } else {
    // Chưa có token đăng ký: Apple và Google mang token lạ không bao giờ đi đường legacy.
    if (purchase.store !== 'google' || purchase.accountToken !== null) return deny('token-required');
    const gate = legacyGate(purchase, legacy, nowMs);
    if (gate) return gate;
    viaLegacy = true;
  }

  const limits = bindLimits({ uidPurchases, purchase, nowMs, policy });
  return limits.ok ? bound(claimerUid, viaLegacy) : deny(limits.reason);
}

// Thông báo từ store không có người claim: chỉ giữ hoặc gắn cho chủ đã xác minh, còn lại ghi nhận mồ côi (không cấp VIP).
function notification({ purchase, tokenOwner, predecessorOwner, existing, uidPurchases, nowMs, policy }) {
  const ex = ownerOf(existing);
  if (ex.kind === 'uid') return bound(ex.uid);
  if (ex.kind !== 'none') return ORPHAN;
  if (purchase.ownership !== 'PURCHASED') return ORPHAN;

  const pre = ownerOf(predecessorOwner);
  if (pre.kind === 'uid') return bound(pre.uid);
  if (pre.kind !== 'none') return ORPHAN;

  const tok = ownerOf(tokenOwner);
  if (tok.kind !== 'uid' || !isStr(purchase.accountToken)) return ORPHAN;
  return bindLimits({ uidPurchases, purchase, nowMs, policy }).ok ? bound(tok.uid) : ORPHAN_LIMITED;
}

function bindPurchase(args) {
  try {
    if (!args || typeof args !== 'object') return deny('invalid-input');
    const { claimerUid, purchase } = args;
    if (!validPurchase(purchase)) return deny('invalid-input');
    if (claimerUid === null) {
      return notification(args);
    }
    if (!isStr(claimerUid)) return deny('invalid-input');
    // FAMILY_SHARED bị từ chối trước mọi kiểm tra khác; giá trị lạ cũng bị coi là FAMILY_SHARED.
    if (purchase.ownership !== 'PURCHASED') return deny('family-shared-unsupported');
    return claim(args);
  } catch {
    return deny('invalid-input');
  }
}

module.exports = { bindPurchase };
