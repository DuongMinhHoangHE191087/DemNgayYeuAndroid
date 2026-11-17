'use strict';

// Điểm nối entitlement vào Cloud Functions (p6 M-23): ba callable (App Check bắt buộc), webhook Apple, RTDN Google, trigger dọn khi xoá tài khoản.
// Logic nằm ở các module cùng thư mục; file này chỉ nối. Ba dòng đầu mỗi callable là requireUid -> limit -> nghiệp vụ (rateLimit.test.js kiểm tĩnh).
const { onCall, onRequest } = require('firebase-functions/v2/https');
const { onMessagePublished } = require('firebase-functions/v2/pubsub');
const { onDocumentWritten } = require('firebase-functions/v2/firestore');
const logger = require('firebase-functions/logger');
const { callableOptions, storeCallableOptions, appleHttpOptions, rtdnOptions, releaseOptions } = require('./options');
const { requireUid, limit, makeRealDeps } = require('./runtime');
const { getOrCreateAccountToken } = require('./accountToken');
const { claimPurchase, parseClaimInput } = require('./claimPurchase');
const { getEntitlement, parseNonce } = require('./getEntitlement');
const { handleAppleNotification } = require('./appleNotifications');
const { handleGoogleNotification } = require('./googleNotifications');
const { releaseEntitlementBindings } = require('./release');

// Không bind secret của store: chỉ sinh/đọc appAccountToken trong Firestore.
exports.getAccountToken = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('getAccountToken', uid);
  return { token: await getOrCreateAccountToken(makeRealDeps(), { uid }) };
});

// Client chỉ gửi {store, id}; parseClaimInput bỏ mọi trường khác (uid, planId, vip...).
exports.claimPurchase = onCall(storeCallableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('claimPurchase', uid);
  const { store, id } = parseClaimInput(request.data);
  return claimPurchase(makeRealDeps(), { uid, store, id });
});

exports.getEntitlement = onCall(storeCallableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('getEntitlement', uid);
  const nonce = parseNonce(request.data);
  return getEntitlement(makeRealDeps(), { uid, nonce });
});

// App Store Server Notifications V2. Xác thực bằng chữ ký JWS của Apple (không có App Check cho onRequest). 405 không phải POST, 400 thiếu thân,
// còn lại theo httpStatus của handler; lỗi bất ngờ trả 500 để Apple gửi lại (không lộ chi tiết lỗi).
exports.appleNotifications = onRequest(appleHttpOptions, async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).send('Method Not Allowed');
    return;
  }
  const body = req.body;
  if (!body || typeof body !== 'object') {
    res.status(400).send('Bad Request');
    return;
  }
  try {
    const { httpStatus } = await handleAppleNotification(makeRealDeps(), { signedPayload: body.signedPayload });
    res.status(httpStatus).send();
  } catch (e) {
    logger.error('appleNotifications failed', { code: e?.code });
    res.status(500).send();
  }
});

// RTDN Google Play qua Pub/Sub; chỉ truyền data + messageId, handler tự tra lại store. retry: lỗi tạm thì Pub/Sub gửi lại.
exports.googlePlayNotifications = onMessagePublished(rtdnOptions, async (event) => {
  const message = event.data?.message;
  await handleGoogleNotification(makeRealDeps(), { data: message?.data, messageId: message?.messageId });
});

// Khi accountDeletions/{uid} sang 'done': tombstone purchase, đánh dấu token deleted, xoá khoá. Mỗi bước chạy lại an toàn nên retry: true.
exports.releaseEntitlementBindings = onDocumentWritten(releaseOptions, async (event) => {
  const after = event.data?.after;
  if (!after?.exists || after.get('state') !== 'done') return;
  await releaseEntitlementBindings(makeRealDeps(), { uid: event.params.uid });
});
