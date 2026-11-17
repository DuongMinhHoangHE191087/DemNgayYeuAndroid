'use strict';

// Chuyển một purchase đã ràng buộc hoặc đã có tombstone sang uid khác (p6 M-89, B3). Đây là đường DUY NHẤT làm việc đó,
// dành cho hỗ trợ khi người dùng hợp pháp mất tài khoản; chạy từ CLI scripts/rebind-purchase.js, không có endpoint công khai.
// Mặc định TỪ CHỐI: thiếu bằng chứng/người thao tác, tài khoản đích không tồn tại, không phải Production, vượt bindLimits thì không ghi gì.
// MỘT transaction: mọi lần đọc đứng trước mọi lần ghi, commit lỗi thì không có ghi dở.
const { bindLimits } = require('./bindLimits');
const { readUidState, writeEntitlement } = require('./applyStoreState');

const deny = (reason) => ({ ok: false, reason });
// Id tài liệu an toàn (cùng luật với applyStoreState).
const isId = (v) => typeof v === 'string' && v !== '' && !v.includes('/') && v !== '.' && v !== '..' && !/^__.*__$/.test(v);
const isText = (v) => typeof v === 'string' && v.trim() !== '';

// dryRun (tuỳ chọn, cho CLI): chạy mọi kiểm tra rồi trả kế hoạch, không ghi gì.
async function rebindPurchase(deps, { key, toUid, evidence, operator, dryRun = false } = {}) {
  if (!isId(key) || !isId(toUid) || !isText(evidence) || !isText(operator)) return deny('invalid-input');
  const nowMs = deps.nowMs();
  if (!Number.isFinite(nowMs)) return deny('invalid-input');
  const col = (name) => deps.db.collection(name);

  return deps.db.runTransaction(async (tx) => {
    // ---- đọc ----
    const purchaseRef = col('purchases').doc(key);
    const snap = await tx.get(purchaseRef);
    if (!snap.exists) return deny('unknown-purchase');
    const doc = snap.data();
    if (doc.environment !== 'Production') return deny('sandbox-refused');
    const fromUid = isId(doc.uid) ? doc.uid : null;
    if (fromUid === toUid && !doc.tombstone) return { ok: true, applied: false, ignored: 'same-owner', fromUid, toUid };

    const to = await readUidState(tx, deps, toUid);
    if (!to.account) return deny('target-account-missing');
    const limits = bindLimits({ uidPurchases: to.purchases, purchase: doc, nowMs, policy: deps.policy });
    if (!limits.ok) return deny(limits.reason);
    const from = fromUid !== null && fromUid !== toUid ? await readUidState(tx, deps, fromUid) : null;

    if (dryRun) return { ok: true, applied: false, dryRun: true, fromUid, toUid, tombstone: !!doc.tombstone };

    // ---- ghi ----
    // Chủ cũ: gỡ key rồi tính lại (mất VIP nếu không còn purchase khác). Tài khoản cũ đã xoá thì không còn gì để tính lại.
    if (from?.account) {
      const keys = Array.isArray(from.account.purchaseKeys) ? from.account.purchaseKeys : [];
      tx.set(col('entitlementAccounts').doc(fromUid), { purchaseKeys: keys.filter((k) => k !== key) }, { merge: true });
      writeEntitlement(tx, deps, { uid: fromUid, purchases: from.purchases.filter((p) => p.key !== key), adminGrant: from.adminGrant });
    }
    // tombstone: null (không phải FieldValue.delete) để mọi nơi đọc `tombstone` truthy đều coi là đã xoá cờ.
    const record = { ...doc, uid: toUid, boundAtMs: nowMs, tombstone: null };
    tx.set(purchaseRef, record, { merge: true });
    const keys = Array.isArray(to.account.purchaseKeys) ? to.account.purchaseKeys : [];
    tx.set(col('entitlementAccounts').doc(toUid), { purchaseKeys: keys.includes(key) ? keys : [...keys, key] }, { merge: true });
    const entitlement = writeEntitlement(tx, deps, { uid: toUid, purchases: [...to.purchases.filter((p) => p.key !== key), record], adminGrant: to.adminGrant });
    tx.set(col('entitlementAudit').doc(`rebind_${key}_${nowMs}`), { key, fromUid, toUid, evidence: evidence.trim(), operator: operator.trim(), atMs: nowMs });
    return { ok: true, applied: true, fromUid, toUid, entitlement };
  });
}

module.exports = { rebindPurchase };
