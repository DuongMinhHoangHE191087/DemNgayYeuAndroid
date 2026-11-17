'use strict';

// Ghi trạng thái store (đã chuẩn hoá) vào Firestore trong MỘT transaction (p6 M-15). Server là nguồn sự thật, mặc định TỪ CHỐI:
// đầu vào hỏng, bindPurchase từ chối hoặc purchase mồ côi thì không bao giờ có quyền VIP, và bị từ chối thì KHÔNG ghi gì (kể cả dấu dedupe).
// Mọi lần đọc đứng trước mọi lần ghi. Không ghi trường undefined (Firestore thật từ chối): dùng null.
const { FieldValue } = require('firebase-admin/firestore');
const { bindPurchase } = require('./bindPurchase');
const { googleKey } = require('./normalizeGoogle');
const { resolveEntitlement } = require('./resolveEntitlement');

const deny = (reason) => ({ ok: false, reason });
const isNum = (v) => Number.isFinite(v);
// Id tài liệu an toàn: không rỗng, không có '/', không phải '.', '..' hay '__x__'.
const isId = (v) => typeof v === 'string' && v !== '' && !v.includes('/') && v !== '.' && v !== '..' && !/^__.*__$/.test(v);
const nn = (v) => (v === undefined ? null : v);

// Chỉ các trường này của NormalizedPurchase được lưu: purchase không thể tự mang uid, boundAtMs, supersededBy, tombstone...
const PURCHASE_FIELDS = [
  'store', 'key', 'lookupId', 'productId', 'planId', 'kind', 'environment', 'accountToken', 'ownership', 'acknowledged', 'status',
  'expiresAtMs', 'graceExpiresAtMs', 'revokedAtMs', 'asOfMs', 'predecessorLookupId', 'latestTransactionId', 'revokedTransactionId',
  'purchaseTimeMs',
];

const col = (deps, name) => deps.db.collection(name);

// Đọc trạng thái của một uid: tài khoản, mọi purchase theo purchaseKeys (chỉ của chính uid này) và adminGrant. Chỉ đọc.
async function readUidState(tx, deps, uid) {
  if (!isId(uid)) throw new TypeError('uid is invalid');
  const accountSnap = await tx.get(col(deps, 'entitlementAccounts').doc(uid));
  const account = accountSnap.exists ? accountSnap.data() : null;
  const keys = Array.isArray(account?.purchaseKeys) ? account.purchaseKeys.filter(isId) : [];
  const [snaps, entSnap] = await Promise.all([
    Promise.all(keys.map((k) => tx.get(col(deps, 'purchases').doc(k)))),
    tx.get(col(deps, 'entitlements').doc(uid)),
  ]);
  const purchases = snaps.filter((s) => s.exists).map((s) => s.data()).filter((p) => p.uid === uid);
  return { account, purchases, adminGrant: entSnap.exists ? nn(entSnap.data().adminGrant) : null };
}

// Ghi entitlements/{uid} (merge, không đụng adminGrant) từ các purchase đã gom; trả kết quả resolve.
function writeEntitlement(tx, deps, { uid, purchases, adminGrant }) {
  const nowMs = deps.nowMs();
  const entitlement = resolveEntitlement(purchases, nowMs, deps.policy, adminGrant);
  tx.set(col(deps, 'entitlements').doc(uid), { ...entitlement, updatedAtMs: nowMs }, { merge: true });
  return entitlement;
}

// Chủ đã lưu trên một purchase: {tombstone: true} (tombstone là true hoặc object, chỉ xét truthy), {uid}, hoặc null (không có/orphan).
function storedOwner(doc) {
  if (!doc) return null;
  if (doc.tombstone) return { tombstone: true };
  return doc.uid !== null && doc.uid !== undefined ? { uid: doc.uid } : null;
}

function ownerOfToken(snap) {
  if (!snap.exists) return null;
  const d = snap.data();
  return d.deleted === true ? { deleted: true } : { uid: d.uid };
}

// Thời điểm tạo tài khoản Auth, null khi không rõ (thiếu cổng, lỗi đọc, giá trị lạ). Gọi TRƯỚC transaction vì transaction có thể chạy lại.
async function readAuthCreatedMs(deps, uid) {
  try {
    const v = await deps.authCreatedMs?.(uid);
    return isNum(v) ? v : null;
  } catch {
    return null;
  }
}

// Đầu vào hợp lệ -> { purchase (bản sao chỉ các trường cho phép), claimerUid, dedupeKey }; ngược lại null.
function parseInput(args) {
  try {
    const { purchase, claimerUid, dedupeKey } = args;
    if (!purchase || typeof purchase !== 'object' || (purchase.store !== 'apple' && purchase.store !== 'google')) return null;
    const p = {};
    for (const f of PURCHASE_FIELDS) p[f] = nn(purchase[f]);
    if (!isId(p.key) || !isNum(p.asOfMs)) return null;
    if (claimerUid !== null && !isId(claimerUid)) return null; // undefined cũng bị từ chối: thông báo phải truyền null rõ ràng
    if (dedupeKey !== undefined && dedupeKey !== null && !isId(dedupeKey)) return null;
    return { purchase: p, claimerUid, dedupeKey: dedupeKey ?? null };
  } catch {
    return null;
  }
}

async function applyStoreState(deps, args) {
  const input = parseInput(args);
  if (!input) return deny('invalid-input');
  const { purchase, claimerUid, dedupeKey } = input;
  const nowMs = deps.nowMs();
  if (!isNum(nowMs)) return deny('invalid-input');
  const uidCreatedMs = claimerUid !== null && purchase.store === 'google' && purchase.accountToken === null
    ? await readAuthCreatedMs(deps, claimerUid) : null;

  return deps.db.runTransaction(async (tx) => {
    // ---- đọc ----
    const markRef = dedupeKey === null ? null : col(deps, 'notificationLog').doc(dedupeKey);
    if (markRef && (await tx.get(markRef)).exists) return { ok: true, applied: false, ignored: 'duplicate' };

    const purchaseRef = col(deps, 'purchases').doc(purchase.key);
    const existingSnap = await tx.get(purchaseRef);
    const existingDoc = existingSnap.exists ? existingSnap.data() : null;
    const tokenOwner = isId(purchase.accountToken) ? ownerOfToken(await tx.get(col(deps, 'appAccountTokens').doc(purchase.accountToken))) : null;

    // Dòng dõi (M-90): đọc purchase tiền nhiệm TRƯỚC mọi lần ghi, cùng lượt đọc. predecessorLookupId do server điền từ phản hồi Google
    // (kẻ cầm mã của người khác không đặt được), nên chủ của tiền nhiệm là bằng chứng sở hữu, kể cả khi tiền nhiệm là legacy không token.
    const predecessorKey = purchase.store === 'google' && typeof purchase.predecessorLookupId === 'string' && purchase.predecessorLookupId !== ''
      ? googleKey(purchase.predecessorLookupId) : null;
    const predecessorRef = predecessorKey === null || predecessorKey === purchase.key ? null : col(deps, 'purchases').doc(predecessorKey);
    const predecessorSnap = predecessorRef ? await tx.get(predecessorRef) : null;
    const predecessorDoc = predecessorSnap?.exists ? predecessorSnap.data() : null;

    const existing = storedOwner(existingDoc); // orphan (uid null) coi như chưa ràng buộc
    const predecessorOwner = storedOwner(predecessorDoc);
    const candidate = [claimerUid, existing?.uid, predecessorOwner?.uid, tokenOwner?.uid].find(isId) ?? null;
    const state = candidate === null ? null : await readUidState(tx, deps, candidate);

    // ---- quyết định ----
    const legacy = {
      cutoverMs: deps.accountTokenCutoverMs,
      untilMs: deps.legacyClaimUntilMs,
      uidCreatedMs,
      uidClaims: state?.account ? state.account.legacyClaims ?? 0 : 0,
    };
    const bind = bindPurchase({
      claimerUid, purchase, tokenOwner, predecessorOwner, existing, uidPurchases: state?.purchases ?? [], nowMs, policy: deps.policy, legacy,
    });
    if (!bind.ok) return deny(bind.reason);
    if (existing?.tombstone) return deny('owner-deleted'); // không bao giờ ghi đè bản đã xoá, kể cả thông báo mồ côi
    if (!bind.orphan && bind.uid !== candidate) return deny('invalid-input'); // trạng thái đã đọc không phải của uid được gắn
    // Tiền nhiệm của uid khác còn sống không bao giờ bị thay thế (kể cả khi purchase mới đã gắn cho uid khác từ trước): không ghi gì.
    if (predecessorOwner?.uid !== undefined && (bind.orphan || predecessorOwner.uid !== bind.uid)) return deny('already-bound');

    const current = bind.orphan ? null : resolveEntitlement(state.purchases, nowMs, deps.policy, state.adminGrant);
    const prevAsOf = existingDoc?.asOfMs;
    if (isNum(prevAsOf) && prevAsOf >= purchase.asOfMs) {
      return { ok: true, applied: false, ignored: 'stale', uid: bind.uid, entitlement: current };
    }

    // ---- ghi ----
    // Google: thu hồi dính mãi. Apple: chỉ ảnh chụp ký mới hơn (asOfMs lớn hơn đã lưu) mới xoá được thu hồi.
    const clearsRevocation = purchase.store === 'apple' && isNum(prevAsOf) && purchase.asOfMs > prevAsOf;
    const keepRevocation = !clearsRevocation && existingDoc?.revokedAtMs != null;
    const revokedAtMs = keepRevocation ? existingDoc.revokedAtMs : purchase.revokedAtMs;
    const revokedTransactionId = keepRevocation ? nn(existingDoc.revokedTransactionId) : purchase.revokedTransactionId;
    const pending = purchase.store === 'google' && !bind.orphan && revokedAtMs === null
      && (purchase.status === 'active' || purchase.status === 'grace');
    const record = {
      ...purchase,
      uid: bind.uid ?? null,
      firstSeenAtMs: existingDoc ? nn(existingDoc.firstSeenAtMs) : nowMs,
      boundAtMs: bind.orphan ? null : (isId(existingDoc?.uid) ? nn(existingDoc.boundAtMs) : nowMs),
      verifiedAtMs: Math.min(nowMs, purchase.asOfMs),
      lastCheckedAtMs: nowMs,
      revokedAtMs,
      revokedTransactionId,
      ackState: purchase.acknowledged === true || existingDoc?.ackState === 'done' ? 'done' : (pending ? 'pending' : 'none'),
      ackAttempts: existingDoc?.ackAttempts ?? 0,
      ackNextAtMs: nn(existingDoc?.ackNextAtMs),
      supersededBy: nn(existingDoc?.supersededBy),
    };
    tx.set(purchaseRef, record, { merge: true }); // merge: giữ tombstone và các trường của task khác
    // Thay thế tiền nhiệm cùng transaction (cùng uid, orphan hoặc tombstone; uid khác còn sống đã bị từ chối ở trên). Đã bị thay rồi thì giữ nguyên.
    const supersedes = predecessorRef !== null && predecessorDoc !== null && nn(predecessorDoc.supersededBy) === null;
    if (supersedes) tx.set(predecessorRef, { supersededBy: purchase.key }, { merge: true });

    let entitlement = null;
    if (!bind.orphan) {
      const keys = Array.isArray(state.account?.purchaseKeys) ? state.account.purchaseKeys : [];
      const accountWrite = { purchaseKeys: keys.includes(purchase.key) ? keys : [...keys, purchase.key] };
      if (bind.legacy) {
        accountWrite.legacyClaims = FieldValue.increment(1);
        tx.set(col(deps, 'entitlementAudit').doc(`legacy_${purchase.key}`), {
          type: 'legacy-bind', uid: bind.uid, key: purchase.key, store: purchase.store, purchaseTimeMs: purchase.purchaseTimeMs, atMs: nowMs,
        });
      }
      // Chủ suy ra từ tiền nhiệm (lần ràng buộc đầu): ghi vết dòng dõi, không bao giờ ghi đè.
      if (predecessorOwner?.uid === bind.uid && !isId(existingDoc?.uid)) {
        tx.set(col(deps, 'entitlementAudit').doc(`lineage_${purchase.key}`), { key: purchase.key, predecessorKey, uid: bind.uid, atMs: nowMs });
      }
      tx.set(col(deps, 'entitlementAccounts').doc(bind.uid), accountWrite, { merge: true });
      const others = state.purchases.filter((p) => p.key !== purchase.key)
        .map((p) => (supersedes && p.key === predecessorKey ? { ...p, supersededBy: purchase.key } : p)); // tiền nhiệm mất VIP ngay
      entitlement = writeEntitlement(tx, deps, { uid: bind.uid, purchases: [...others, record], adminGrant: state.adminGrant });
    }
    if (markRef) tx.set(markRef, { atMs: nowMs, key: purchase.key });
    return { ok: true, applied: true, ignored: null, uid: bind.uid ?? null, orphan: bind.orphan === true, entitlement };
  });
}

module.exports = { applyStoreState, readUidState, writeEntitlement };
