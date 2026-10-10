'use strict';

// Điểm vào Cloud Functions. Logic nằm trong src/; file này chỉ nối callable, trigger, lịch dọn và secret.
const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { onDocumentWritten } = require('firebase-functions/v2/firestore');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { defineSecret, defineString } = require('firebase-functions/params');
const logger = require('firebase-functions/logger');
const cloudinarySdk = require('cloudinary');
const cfg = require('./src/config');
const policy = require('./src/policy');
const media = require('./src/media');
const pairing = require('./src/pairing');
const accountDeletion = require('./src/accountDeletion');
const rateLimit = require('./src/rateLimit');
const { getAuth } = require('firebase-admin/auth');
const { createCloudinary } = require('./src/cloudinary');

if (getApps().length === 0) initializeApp();

// Cấu hình không bí mật đặt bằng defineString (firebase functions:config không dùng nữa); secret ở Secret Manager.
const CLOUDINARY_CLOUD_NAME = defineString('CLOUDINARY_CLOUD_NAME');
const CLOUDINARY_API_KEY = defineSecret('CLOUDINARY_API_KEY');
const CLOUDINARY_API_SECRET = defineSecret('CLOUDINARY_API_SECRET');
const CLOUDINARY_TOKEN_KEY = defineSecret('CLOUDINARY_TOKEN_KEY');
const CLOUDINARY_SECRETS = [CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET, CLOUDINARY_TOKEN_KEY];

// Một client cho mỗi instance. Secret không đổi trong vòng đời instance; đổi secret thì deploy lại.
let cloudinary = null;
function cloud() {
  if (!cloudinary) {
    cloudinary = createCloudinary({
      sdk: cloudinarySdk,
      cloudName: CLOUDINARY_CLOUD_NAME.value(),
      apiKey: CLOUDINARY_API_KEY.value(),
      apiSecret: CLOUDINARY_API_SECRET.value(),
      tokenKey: CLOUDINARY_TOKEN_KEY.value(),
    });
  }
  return cloudinary;
}

const db = () => getFirestore();

// Giới hạn tần suất theo uid; gọi ngay sau requireUid, trước mọi logic nghiệp vụ. Vượt giới hạn: resource-exhausted.
const limit = (name, uid) => rateLimit.enforce({ db: db(), uid, name, nowMs: Date.now() });

// Khách (chưa đăng nhập) không có tài khoản Firebase nên không có uid: từ chối trước khi chạm dữ liệu.
function requireUid(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Cần đăng nhập để dùng tính năng cặp đôi');
  return uid;
}

// App Check bắt buộc: chặn client không phải app chính chính thức gọi trực tiếp.
const callableOptions = {
  region: cfg.region,
  enforceAppCheck: true,
  secrets: CLOUDINARY_SECRETS,
  maxInstances: 10,
};

// Ghép đôi: nhận lời mời + tạo relationship trong một transaction; client không còn tự ghi relationships.
// Không cần secret Cloudinary nên không khai báo secrets.
exports.acceptCoupleInvite = onCall({ region: cfg.region, enforceAppCheck: true, maxInstances: 10 }, async (request) => {
  const uid = requireUid(request);
  await limit('acceptCoupleInvite', uid);
  const input = pairing.parseAcceptInput(request.data);
  return pairing.acceptCoupleInvite({ db: db(), uid, input, nowMs: Date.now() });
});

// Xoá tài khoản: callable chỉ nhận việc (cần đăng nhập gần đây); trigger bên dưới xử lý và tự thử lại.
exports.requestAccountDeletion = onCall({ region: cfg.region, enforceAppCheck: true, maxInstances: 10 }, async (request) => {
  const uid = requireUid(request);
  await limit('requestAccountDeletion', uid);
  return accountDeletion.requestAccountDeletion({
    db: db(), uid, authTimeSec: request.auth?.token?.auth_time, nowMs: Date.now(),
  });
});

// Chạy khi việc mới/được yêu cầu lại (requestedAt đổi); ghi lastError không kích hoạt lại vì requestedAt giữ nguyên.
// retry: mọi bước idempotent. ponytail: retry của Firebase có trần thời gian; quá trần thì gọi lại callable để kích hoạt lại.
exports.processAccountDeletion = onDocumentWritten({
  document: 'accountDeletions/{uid}',
  region: cfg.region,
  secrets: CLOUDINARY_SECRETS,
  retry: true,
  timeoutSeconds: 540,
  maxInstances: 10,
}, async (event) => {
  const after = event.data?.after;
  const before = event.data?.before;
  if (!after?.exists || after.get('state') !== 'pending') return;
  if (before?.exists && before.get('requestedAt') === after.get('requestedAt')) return;
  const cl = cloud();
  await accountDeletion.runJob({
    db: db(),
    auth: getAuth(),
    purge: (args) => media.purgeOne({ db: db(), cloud: cl, ...args }),
    uid: event.params.uid,
    nowMs: Date.now(),
  });
});

exports.signMemoryUpload = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('signMemoryUpload', uid);
  const input = policy.parseReserveInput(request.data);
  return media.reserveUpload({ db: db(), cloud: cloud(), uid, input, nowMs: Date.now() });
});

exports.confirmMemoryUpload = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('confirmMemoryUpload', uid);
  const input = policy.parseIds(request.data ?? {});
  return media.confirmUpload({ db: db(), cloud: cloud(), uid, input, nowMs: Date.now() });
});

exports.getMemoryMediaUrl = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('getMemoryMediaUrl', uid);
  const input = policy.parseIds(request.data ?? {});
  return media.getDeliveryUrl({ db: db(), cloud: cloud(), uid, input, nowMs: Date.now() });
});

// Memory bị xoá (bởi bất kỳ thành viên nào), hoặc bị bỏ ảnh (cloudinaryPublicId về rỗng), thì giải phóng tệp của nó.
// Thay đổi khác không liên quan tới tệp nên bỏ qua. retry: thao tác idempotent nên thử lại an toàn.
exports.onMemoryChanged = onDocumentWritten({
  document: 'relationships/{relationshipId}/memories/{memoryId}',
  region: cfg.region,
  secrets: CLOUDINARY_SECRETS,
  retry: true,
  maxInstances: 10,
}, async (event) => {
  const before = event.data?.before;
  const after = event.data?.after;
  const deleted = !after?.exists;
  const photoRemoved = !!after?.exists && !!before?.exists
    && !!before.get('cloudinaryPublicId') && !after.get('cloudinaryPublicId');
  if (!deleted && !photoRemoved) return;
  const { relationshipId, memoryId } = event.params;
  await media.releaseAssetForMemory({ db: db(), cloud: cloud(), relationshipId, memoryId, nowMs: Date.now() });
});

// Dọn mỗi giờ (xem docs/architecture/MEDIA_CLOUDINARY.md). Tệp upload dở được dọn sớm hơn nhờ lịch hàng giờ.
// timeoutSeconds 540: mỗi bước xử lý tuần tự tối đa sweepBatchSize mục, nên cần thời gian hơn mặc định 60 giây.
exports.sweepMediaAssets = onSchedule({
  schedule: '0 * * * *',
  timeZone: 'Etc/UTC',
  region: cfg.region,
  secrets: CLOUDINARY_SECRETS,
  retryCount: 2,
  timeoutSeconds: 540,
}, async () => {
  const summary = await media.sweep({ db: db(), cloud: cloud(), nowMs: Date.now() });
  logger.info('sweepMediaAssets', summary);
});
