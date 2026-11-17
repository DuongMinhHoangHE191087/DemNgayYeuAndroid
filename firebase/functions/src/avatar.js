'use strict';

// Avatar người dùng: một tệp JPEG cố định theo uid (inlove_avt_<uid>) trong bucket R2 công khai riêng.
// Không xác nhận phía máy chủ (rủi ro đã chấp nhận): chặn bằng content-type đã ký, trần 1 MB, một đối tượng mỗi uid và rate limit.
const { HttpsError } = require('firebase-functions/v2/https');
const cfg = require('./config');

const UID_RE = /^[A-Za-z0-9_-]{1,128}$/;
const EXPIRES_SECONDS = 600; // hằng số, không phải đầu vào: presign ném RangeError nếu ngoài [1, 604800]
const CACHE_CONTROL = 'public, max-age=31536000, immutable'; // an toàn vì mỗi URL công khai mang ?v= mới

const invalid = (message) => new HttpsError('invalid-argument', message);

function parseSignInput(data) {
  const { contentType, contentLength } = data ?? {};
  if (contentType !== 'image/jpeg') throw invalid('contentType phải là image/jpeg');
  if (!Number.isInteger(contentLength) || contentLength < 1 || contentLength > cfg.limits.avatarMaxBytes) {
    throw invalid(`contentLength phải là số nguyên trong [1, ${cfg.limits.avatarMaxBytes}]`);
  }
  return { contentType, contentLength };
}

// "Đã cấu hình" = có bucket VÀ base URL https. Kiểm TRƯỚC createObjectStore (nó ném Error thường khi thiếu tham số).
// Base URL không được có ? hoặc #: `sign` ghép `${base}/${publicId}?v=...` nên chúng làm hỏng URL công khai.
const isConfigured = ({ bucket, baseUrl } = {}) => !!bucket && /^https:\/\/[^\s?#]+$/.test(baseUrl ?? '');

const keyFor = (uid) => `${cfg.avatarPublicIdPrefix}${uid}`;

// store rỗng = chưa cấu hình bucket avatar.
function sign({ store, uid, input, baseUrl, nowMs }) {
  // details.reason là thứ duy nhất client đọc được (CloudFunctionsException.reason), cùng kiểu với recent-login-required.
  if (!store) throw new HttpsError('failed-precondition', 'avatars-not-configured', { reason: 'avatars-not-configured' });
  if (!UID_RE.test(uid ?? '')) throw invalid('uid không hợp lệ');
  const publicId = keyFor(uid);
  const { uploadUrl, headers } = store.signUpload({
    publicId, ...input, expiresInSeconds: EXPIRES_SECONDS, overwrite: true, cacheControl: CACHE_CONTROL,
  });
  return { uploadUrl, headers, publicId, publicUrl: `${baseUrl.replace(/\/+$/, '')}/${publicId}?v=${nowMs}` };
}

// Chưa cấu hình, hoặc uid không thể đã ký avatar: không làm gì để không chặn việc xoá tài khoản.
async function purge({ store, uid }) {
  if (!store || !UID_RE.test(uid ?? '')) return;
  await store.destroy(keyFor(uid));
}

module.exports = { parseSignInput, isConfigured, keyFor, sign, purge };
