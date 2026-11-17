'use strict';

// appAccountToken: UUID v4 ngẫu nhiên do server sinh cho mỗi uid, lưu hai chiều:
// entitlementAccounts/{uid}.token và appAccountTokens/{token} = {uid, createdAtMs}.
// Token đã cấp không bao giờ bị ghi đè, và token đã dùng (kể cả bản deleted: true) không bao giờ được cấp lại.
const crypto = require('node:crypto');
const { HttpsError } = require('firebase-functions/v2/https');

const MAX_ATTEMPTS = 5;

async function getOrCreateAccountToken(deps, { uid }) {
  if (typeof uid !== 'string' || uid === '' || uid.includes('/')) {
    throw new HttpsError('invalid-argument', 'uid không hợp lệ');
  }
  const { db } = deps;
  const newUuid = deps.randomUUID ?? crypto.randomUUID;

  return db.runTransaction(async (tx) => {
    const accountRef = db.collection('entitlementAccounts').doc(uid);
    const account = await tx.get(accountRef);
    const existing = account.exists ? account.get('token') : undefined;
    if (typeof existing === 'string' && existing !== '') return existing;

    // Mọi lần đọc đứng trước lần ghi. Tài liệu tồn tại (của uid nào, kể cả đã xoá) là va chạm: sinh lại.
    for (let i = 0; i < MAX_ATTEMPTS; i += 1) {
      const token = String(newUuid()).toLowerCase();
      const tokenRef = db.collection('appAccountTokens').doc(token);
      if ((await tx.get(tokenRef)).exists) continue;
      tx.set(accountRef, { token }, { merge: true });
      tx.set(tokenRef, { uid, createdAtMs: deps.nowMs() });
      return token;
    }
    throw new HttpsError('internal', 'Không cấp được appAccountToken');
  });
}

module.exports = { getOrCreateAccountToken };
