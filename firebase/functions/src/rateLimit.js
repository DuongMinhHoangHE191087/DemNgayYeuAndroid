'use strict';

// Rate limit theo uid cho callable: đếm trong collection mediaQuota (TTL xoá theo expiresAt), mỗi (name, uid, cửa sổ) một doc.
// Không bắt lỗi: Firestore lỗi thì callable cũng lỗi (fail-closed).
const { FieldValue } = require('firebase-admin/firestore');
const { HttpsError } = require('firebase-functions/v2/https');
const cfg = require('./config');

// ponytail: cửa sổ cố định nên có thể lọt tới 2 lần max khi vắt qua ranh giới cửa sổ; đổi sang sliding window nếu cần chặt hơn.
async function enforce({ db, uid, name, nowMs }) {
  const limit = cfg.rateLimits[name];
  if (!limit) throw new Error(`rateLimit: không có cấu hình cho "${name}"`);
  const windowIndex = Math.floor(nowMs / limit.windowMs);
  const ref = db.collection('mediaQuota').doc(`rl_${name}_${uid}_${windowIndex}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if ((snap.get('count') ?? 0) >= limit.max) throw new HttpsError('resource-exhausted', 'Bạn thao tác quá nhanh, hãy thử lại sau');
    tx.set(ref, { count: FieldValue.increment(1), expiresAt: new Date(nowMs + cfg.timing.dailyCounterTtlMs) }, { merge: true });
  });
}

module.exports = { enforce };
