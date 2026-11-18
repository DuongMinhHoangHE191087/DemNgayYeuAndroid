'use strict';

// Điểm vào Cloud Functions. Logic nằm trong src/; file này chỉ nối callable, trigger, lịch dọn và secret.
const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { onDocumentWritten } = require('firebase-functions/v2/firestore');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { defineSecret, defineString } = require('firebase-functions/params');
const logger = require('firebase-functions/logger');
const cfg = require('./src/config');
const policy = require('./src/policy');
const media = require('./src/media');
const pairing = require('./src/pairing');
const accountDeletion = require('./src/accountDeletion');
const avatar = require('./src/avatar');
const aiAdvisor = require('./src/aiAdvisor');
const rateLimit = require('./src/rateLimit');
const { getAuth } = require('firebase-admin/auth');
const { createObjectStore } = require('./src/objectStore');
const { STORE_SECRETS } = require('./src/entitlement/options');
const { gateFor } = require('./src/entitlement/runtime');

if (getApps().length === 0) initializeApp();

// Cấu hình không bí mật đặt bằng defineString (firebase functions:config không dùng nữa); secret ở Secret Manager.
const R2_ACCOUNT_ID = defineString('R2_ACCOUNT_ID');
const R2_BUCKET = defineString('R2_BUCKET');
const R2_ACCESS_KEY_ID = defineSecret('R2_ACCESS_KEY_ID');
const R2_SECRET_ACCESS_KEY = defineSecret('R2_SECRET_ACCESS_KEY');
const R2_SECRETS = [R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY];
const GEMINI_API_KEY = defineSecret('GEMINI_API_KEY');

function readGeminiApiKey() {
  try {
    return String(GEMINI_API_KEY.value() ?? '').trim();
  } catch {
    return String(process.env.GEMINI_API_KEY ?? '').trim();
  }
}

// Một client cho mỗi instance. Secret không đổi trong vòng đời instance; đổi secret thì deploy lại.
let objectStore = null;
function cloud() {
  if (!objectStore) {
    objectStore = createObjectStore({
      accountId: R2_ACCOUNT_ID.value(),
      bucket: R2_BUCKET.value(),
      accessKeyId: R2_ACCESS_KEY_ID.value(),
      secretAccessKey: R2_SECRET_ACCESS_KEY.value(),
    });
  }
  return objectStore;
}

// Bucket avatar CÔNG KHAI riêng, cùng khoá truy cập R2. Mặc định '' = tắt (chủ dự án bật bằng cách đặt hai tham số rồi deploy).
const R2_AVATAR_BUCKET = defineString('R2_AVATAR_BUCKET', { default: '' });
const R2_AVATAR_PUBLIC_BASE_URL = defineString('R2_AVATAR_PUBLIC_BASE_URL', { default: '' });
const avatarBaseUrl = () => R2_AVATAR_PUBLIC_BASE_URL.value();

// Trả null khi chưa cấu hình: kiểm TRƯỚC createObjectStore vì nó ném Error thường (client thấy `internal`).
let avatarObjectStore = null;
function avatarCloud() {
  const bucket = R2_AVATAR_BUCKET.value();
  if (!avatar.isConfigured({ bucket, baseUrl: avatarBaseUrl() })) return null;
  if (!avatarObjectStore) {
    avatarObjectStore = createObjectStore({
      accountId: R2_ACCOUNT_ID.value(),
      bucket,
      accessKeyId: R2_ACCESS_KEY_ID.value(),
      secretAccessKey: R2_SECRET_ACCESS_KEY.value(),
    });
  }
  return avatarObjectStore;
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
// Ba callable media còn qua cổng quyền (gateFor tra store) nên cần cả secret của store.
const callableOptions = {
  region: cfg.region,
  enforceAppCheck: true,
  secrets: [...R2_SECRETS, ...STORE_SECRETS],
  maxInstances: 10,
};

const aiCallableOptions = {
  region: cfg.region,
  enforceAppCheck: true,
  secrets: [...STORE_SECRETS, GEMINI_API_KEY],
  maxInstances: 10,
};

// Ghép đôi: nhận lời mời + tạo relationship trong một transaction; client không còn tự ghi relationships.
// Không cần secret R2 nên không khai báo secrets.
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
  secrets: R2_SECRETS,
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
    purgeAvatar: (uid) => avatar.purge({ store: avatarCloud(), uid }),
    uid: event.params.uid,
    nowMs: Date.now(),
  });
});

// Avatar: không qua cổng quyền (hàng `free` trong GATES). Chỉ cần secret R2, không cần secret store.
exports.signAvatarUpload = onCall({ region: cfg.region, enforceAppCheck: true, secrets: R2_SECRETS, maxInstances: 10 }, async (request) => {
  const uid = requireUid(request);
  await limit('signAvatarUpload', uid);
  const input = avatar.parseSignInput(request.data);
  return avatar.sign({ store: avatarCloud(), uid, input, baseUrl: avatarBaseUrl(), nowMs: Date.now() });
});

exports.signMemoryUpload = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('signMemoryUpload', uid);
  const { tier } = await gateFor('signMemoryUpload', uid);
  const input = policy.parseReserveInput(request.data);
  return media.reserveUpload({ db: db(), cloud: cloud(), uid, input, tier, nowMs: Date.now() });
});

exports.confirmMemoryUpload = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('confirmMemoryUpload', uid);
  const { tier } = await gateFor('confirmMemoryUpload', uid);
  const input = policy.parseIds(request.data ?? {});
  return media.confirmUpload({ db: db(), cloud: cloud(), uid, input, tier, nowMs: Date.now() });
});

exports.getMemoryMediaUrl = onCall(callableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('getMemoryMediaUrl', uid);
  const { tier } = await gateFor('getMemoryMediaUrl', uid);
  const input = policy.parseIds(request.data ?? {});
  return media.getDeliveryUrl({ db: db(), cloud: cloud(), uid, input, tier, nowMs: Date.now() });
});

// Trợ lý gợi ý quà tặng & lời chúc AI: kiểm tra quyền (vip 60 lượt/tháng, free 2 lượt/tháng), rơi về engine thông minh nếu thiếu Gemini key.
exports.generateGiftAdvice = onCall(aiCallableOptions, async (request) => {
  const uid = requireUid(request);
  await limit('generateGiftAdvice', uid);
  const { tier } = await gateFor('generateGiftAdvice', uid);
  const input = aiAdvisor.parseAiGiftInput(request.data ?? {});
  return aiAdvisor.generateGiftAdvice({
    db: db(),
    uid,
    tier,
    input,
    geminiApiKey: readGeminiApiKey(),
    nowMs: Date.now(),
  });
});

// Memory bị xoá (bởi bất kỳ thành viên nào), hoặc bị bỏ ảnh (cloudinaryPublicId về rỗng), thì giải phóng tệp của nó.
// Thay đổi khác không liên quan tới tệp nên bỏ qua. retry: thao tác idempotent nên thử lại an toàn.
exports.onMemoryChanged = onDocumentWritten({
  document: 'relationships/{relationshipId}/memories/{memoryId}',
  region: cfg.region,
  secrets: R2_SECRETS,
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

// Dọn mỗi giờ. Tệp upload dở được dọn sớm hơn nhờ lịch hàng giờ.
// timeoutSeconds 540: mỗi bước xử lý tuần tự tối đa sweepBatchSize mục, nên cần thời gian hơn mặc định 60 giây.
exports.sweepMediaAssets = onSchedule({
  schedule: '0 * * * *',
  timeZone: 'Etc/UTC',
  region: cfg.region,
  secrets: R2_SECRETS,
  retryCount: 2,
  timeoutSeconds: 540,
}, async () => {
  const summary = await media.sweep({ db: db(), cloud: cloud(), nowMs: Date.now() });
  logger.info('sweepMediaAssets', summary);
});
Object.assign(exports, require('./src/entitlement'));
