'use strict';

// Dọn ràng buộc entitlement của tài khoản đã xoá (p6 M-21). Chỉ chạy khi accountDeletions/{uid}.state == 'done'.
// KHÔNG giải phóng purchase hay token: purchase chuyển thành tombstone (uid null), token chỉ đánh dấu deleted, nên sau khi xoá
// tài khoản uid khác claim sẽ nhận 'owner-deleted'; chỉ rebindPurchase (M-89) mới gắn lại được. Mỗi bước chạy lại an toàn:
// (1) tombstone purchase theo chunk, (2) đánh dấu token deleted, (3) cuối cùng mới xoá entitlementAccounts và entitlements.
const { createHash } = require('node:crypto');

const CHUNK = 200; // số purchase tối đa mỗi transaction
const isId = (v) => typeof v === 'string' && v !== '' && !v.includes('/') && v !== '.' && v !== '..' && !/^__.*__$/.test(v);
const keysOf = (account) => (Array.isArray(account?.purchaseKeys) ? [...new Set(account.purchaseKeys.filter(isId))] : []);

async function releaseEntitlementBindings(deps, { uid }) {
  if (!isId(uid)) throw new TypeError('uid is invalid');
  const col = (name) => deps.db.collection(name);
  const job = await col('accountDeletions').doc(uid).get();
  if (!job.exists || job.get('state') !== 'done') return { released: false, reason: 'not-done' };

  const accountRef = col('entitlementAccounts').doc(uid);
  const keys = keysOf((await accountRef.get()).data());
  const uidHash = createHash('sha256').update(uid).digest('hex');

  // (1) Tombstone purchase của uid này: giữ tombstone cũ, giữ lookupId, status, environment (merge).
  for (let i = 0; i < keys.length; i += CHUNK) {
    await deps.db.runTransaction(async (tx) => {
      const atMs = deps.nowMs();
      const snaps = await Promise.all(keys.slice(i, i + CHUNK).map((k) => tx.get(col('purchases').doc(k))));
      for (const s of snaps) {
        if (s.exists && s.data().uid === uid) tx.set(s.ref, { uid: null, tombstone: s.data().tombstone ?? { uidHash, atMs } }, { merge: true });
      }
    });
  }

  // (2) Token chỉ đánh dấu deleted (không xoá) để không bao giờ được cấp lại. Gồm token ghi ở tài khoản và mọi token mang uid này.
  await deps.db.runTransaction(async (tx) => {
    const acctToken = (await tx.get(accountRef)).data()?.token;
    const owned = (await tx.get(col('appAccountTokens').where('uid', '==', uid))).docs;
    const refs = new Map(owned.map((s) => [s.id, { ref: s.ref, data: s.data() }]));
    if (isId(acctToken) && !refs.has(acctToken)) {
      const ref = col('appAccountTokens').doc(acctToken);
      const s = await tx.get(ref);
      if (!s.exists || s.data().uid === uid) refs.set(acctToken, { ref, data: s.data() ?? {} }); // token của uid khác: không đụng
    }
    for (const { ref, data } of refs.values()) if (data.deleted !== true) tx.set(ref, { deleted: true }, { merge: true });
  });

  // (3) Xoá tài khoản sau cùng. Có purchase mới xuất hiện giữa chừng thì ném lỗi để chạy lại, không làm mất khoá.
  await deps.db.runTransaction(async (tx) => {
    const account = (await tx.get(accountRef)).data();
    if (keysOf(account).some((k) => !keys.includes(k))) throw new Error('purchaseKeys changed during release, retry');
    tx.delete(accountRef);
    tx.delete(col('entitlements').doc(uid));
  });
  return { released: true, purchases: keys.length };
}

module.exports = { releaseEntitlementBindings };
