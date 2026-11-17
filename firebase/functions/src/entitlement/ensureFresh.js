'use strict';

// Tra lại store cho các purchase của một uid khi dữ liệu lưu đã cũ (p6 M-91): chỗ bắt hoàn tiền hoặc thông báo bị mất mà không phụ thuộc webhook.
// BẢO MẬT / chi phí: không bao giờ ném lỗi store ra ngoài (giữ nguyên trạng thái đã lưu, chỉ ghi lastCheckedAtMs); Google revoked là sticky nên
// không bao giờ tra lại; trạng thái cuối chỉ tra lại cho Apple, trong terminalWindowMs, cách nhau terminalRecheckGapMs; mỗi purchase tối đa
// một lần tra mỗi recheckGapMs. Store không tìm thấy quá maxStaleMs thì resolveEntitlement tự cho `stale` (không VIP).
const { applyStoreState, readUidState } = require('./applyStoreState');
const { fetchNormalized } = require('./fetchNormalized');
const { ackIfPending } = require('./ackIfPending');

const isNum = (v) => Number.isFinite(v);
const isTerminal = (p) => p.status === 'expired' || p.status === 'revoked' || p.revokedAtMs != null;

const readState = (deps, uid) => deps.db.runTransaction((tx) => readUidState(tx, deps, uid));

// Có cần tra store cho purchase này lúc nowMs không (chỉ xét purchase chưa bị thay thế).
function needsFetch(p, nowMs, pol) {
  const sinceCheck = nowMs - (isNum(p.lastCheckedAtMs) ? p.lastCheckedAtMs : -Infinity);
  if (isTerminal(p)) {
    // Google revoked dính mãi (mua lại đến qua RTDN hoặc claim); Google expired cũng không tra. Chỉ Apple có thể phục hồi
    // (REFUND_REVERSED, gia hạn bị mất), và chỉ trong cửa sổ tính từ mốc cuối cùng của quyền.
    if (p.store !== 'apple') return false;
    const edge = Math.max(...[p.expiresAtMs, p.revokedAtMs].filter(isNum));
    return isNum(edge) && nowMs - edge <= pol.terminalWindowMs && sinceCheck >= pol.terminalRecheckGapMs;
  }
  if (sinceCheck < pol.recheckGapMs) return false;
  const stale = !(isNum(p.verifiedAtMs) && nowMs - p.verifiedAtMs < pol.reverifyAfterMs);
  const nearExpiry = isNum(p.expiresAtMs) && p.expiresAtMs - nowMs <= pol.nearExpiryMs; // kể cả đã quá hạn
  return stale || nearExpiry;
}

// Tra một purchase; mọi lỗi (store hoặc ghi) đều nuốt, purchase khác vẫn được xử lý.
async function refresh(deps, p) {
  const ref = deps.db.collection('purchases').doc(p.key);
  let fetched = false;
  try {
    const kind = p.kind === 'lifetime' ? 'product' : 'subscription';
    let res = null;
    try {
      res = await fetchNormalized(deps, { store: p.store, id: p.lookupId, kind });
    } catch {
      // store không khả dụng hoặc lỗi lạ: giữ nguyên trạng thái, chỉ ghi nhận đã thử để không tra dồn dập
    }
    const applied = res?.purchase
      ? await applyStoreState(deps, { purchase: res.purchase, claimerUid: null, dedupeKey: null })
      : null;
    fetched = applied?.applied === true;
    // applyStoreState đã ghi lastCheckedAtMs khi applied; còn lại (store lỗi, notOurs, ảnh chụp cũ, bị từ chối) thì tự ghi.
    if (!fetched) await ref.update({ lastCheckedAtMs: deps.nowMs() });
  } catch {
    return false;
  }
  return fetched;
}

async function ensureFresh(deps, { uid }) {
  const state = await readState(deps, uid);
  const nowMs = deps.nowMs();
  const pol = deps.policy;
  const todo = state.purchases
    .filter((p) => p.supersededBy == null && !p.tombstone)
    .map((p) => ({ p, fetch: needsFetch(p, nowMs, pol) }))
    .filter(({ p, fetch }) => fetch || p.ackState === 'pending')
    .slice(0, pol.maxUnfinished);
  if (todo.length === 0) return state;

  // ponytail: tuần tự, chạy song song nếu độ trễ getEntitlement thành vấn đề
  for (const { p, fetch } of todo) {
    const applied = fetch ? await refresh(deps, p) : false;
    if (p.store === 'google' && (p.ackState === 'pending' || applied)) {
      try {
        await ackIfPending(deps, { key: p.key });
      } catch {
        // ackIfPending tự lùi lịch khi Google lỗi; lỗi ghi Firestore thì lần getEntitlement sau thử lại
      }
    }
  }
  return readState(deps, uid);
}

module.exports = { ensureFresh };
