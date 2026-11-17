'use strict';

// Tham số và tuỳ chọn triển khai của các hàm entitlement (p6 M-23). Chỉ KHAI BÁO ở đây; không bao giờ gọi .value() lúc nạp module
// (đọc ở runtime.js, lười, vì secret chỉ có khi hàm bind đúng secret đó).
const { defineSecret, defineString } = require('firebase-functions/params');
const cfg = require('../config');

const APPLE_IAP_PRIVATE_KEY = defineSecret('APPLE_IAP_PRIVATE_KEY');
const ENTITLEMENT_SIGNING_KEY = defineSecret('ENTITLEMENT_SIGNING_KEY');
// Chỉ bốn hàm gọi store hoặc ký token mới bind; getAccountToken và releaseEntitlementBindings không cần nên không được thấy khoá.
const STORE_SECRETS = [APPLE_IAP_PRIVATE_KEY, ENTITLEMENT_SIGNING_KEY];

const APPLE_IAP_KEY_ID = defineString('APPLE_IAP_KEY_ID');
const APPLE_IAP_ISSUER_ID = defineString('APPLE_IAP_ISSUER_ID');
const APPLE_BUNDLE_ID = defineString('APPLE_BUNDLE_ID');
const APPLE_APP_APPLE_ID = defineString('APPLE_APP_APPLE_ID');
const GOOGLE_PACKAGE_NAME = defineString('GOOGLE_PACKAGE_NAME');
// Mặc định rỗng = null = đường legacy ĐÓNG (mặc định từ chối); chủ dự án phải đặt mốc rõ ràng mới mở.
const LEGACY_CLAIM_UNTIL_MS = defineString('LEGACY_CLAIM_UNTIL_MS', { default: '' });
const ACCOUNT_TOKEN_CUTOVER_MS = defineString('ACCOUNT_TOKEN_CUTOVER_MS', { default: '' });

// App Check bắt buộc: chặn client không phải app chính thức. Cùng mẫu acceptCoupleInvite (index.js).
const callableOptions = { region: cfg.region, enforceAppCheck: true, maxInstances: 10 };
const storeCallableOptions = { ...callableOptions, secrets: STORE_SECRETS };

// Webhook Apple (onRequest không có App Check; chữ ký JWS của Apple là xác thực) và RTDN Google (Pub/Sub, topic play-rtdn).
// retry: mọi bước idempotent (dedupe ghi cùng transaction), nên gửi lại an toàn.
const appleHttpOptions = { region: cfg.region, secrets: STORE_SECRETS, maxInstances: 10 };
const rtdnOptions = { topic: 'play-rtdn', region: cfg.region, secrets: STORE_SECRETS, retry: true, maxInstances: 10 };
const releaseOptions = { document: 'accountDeletions/{uid}', region: cfg.region, retry: true, maxInstances: 10 };

module.exports = {
  STORE_SECRETS,
  APPLE_IAP_PRIVATE_KEY,
  ENTITLEMENT_SIGNING_KEY,
  APPLE_IAP_KEY_ID,
  APPLE_IAP_ISSUER_ID,
  APPLE_BUNDLE_ID,
  APPLE_APP_APPLE_ID,
  GOOGLE_PACKAGE_NAME,
  LEGACY_CLAIM_UNTIL_MS,
  ACCOUNT_TOKEN_CUTOVER_MS,
  callableOptions,
  storeCallableOptions,
  appleHttpOptions,
  rtdnOptions,
  releaseOptions,
};
