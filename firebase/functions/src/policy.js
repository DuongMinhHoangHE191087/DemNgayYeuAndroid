'use strict';

// Luật thuần (không I/O): kiểm tra input, định dạng, tài sản sau upload, quota.
// Mọi lỗi trả về HttpsError để callable ném thẳng cho app.
const { HttpsError } = require('firebase-functions/v2/https');
const cfg = require('./config');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ID_RE = /^[A-Za-z0-9_-]{1,128}$/;
const KINDS = ['IMAGE', 'VIDEO'];
const MB = 1024 * 1024;

const invalid = (message) => new HttpsError('invalid-argument', message);

function publicIdFor(memoryId) {
  return `${cfg.publicIdPrefix}${memoryId}`;
}

function resourceTypeOf(kind) {
  return kind === 'VIDEO' ? 'video' : 'image';
}

function maxBytesOf(kind) {
  return kind === 'VIDEO' ? cfg.limits.videoMaxBytes : cfg.limits.imageMaxBytes;
}

function formatForMime(kind, mimeType) {
  const table = cfg.mimeFormats[kind];
  const key = String(mimeType ?? '').toLowerCase();
  if (!Object.hasOwn(table, key)) {
    throw invalid(`Định dạng tệp không được hỗ trợ: ${mimeType || '(trống)'}`);
  }
  return table[key];
}

function parseIds(input) {
  const { relationshipId, memoryId } = input;
  if (typeof relationshipId !== 'string' || !ID_RE.test(relationshipId)) {
    throw invalid('relationshipId không hợp lệ');
  }
  if (typeof memoryId !== 'string' || !UUID_RE.test(memoryId)) {
    throw invalid('memoryId phải là UUID viết thường');
  }
  return { relationshipId, memoryId };
}

// Input ký upload: app khai báo loại, MIME, dung lượng, thời lượng. Server kiểm tra trước khi cấp chữ ký.
function parseReserveInput(data) {
  const input = data ?? {};
  const { kind, sizeBytes, durationSeconds, mimeType } = input;
  if (!KINDS.includes(kind)) throw invalid('kind phải là IMAGE hoặc VIDEO');
  if (!Number.isInteger(sizeBytes) || sizeBytes <= 0 || sizeBytes > maxBytesOf(kind)) {
    throw invalid(`Dung lượng tệp phải từ 1 byte đến ${maxBytesOf(kind) / MB} MB`);
  }
  if (kind === 'VIDEO' && !(durationSeconds > 0 && durationSeconds <= cfg.limits.videoMaxSeconds)) {
    throw invalid(`Video phải dài từ 1 đến ${cfg.limits.videoMaxSeconds} giây`);
  }
  return {
    ...parseIds(input),
    kind,
    sizeBytes,
    durationSeconds: kind === 'VIDEO' ? durationSeconds : 0,
    format: formatForMime(kind, mimeType),
  };
}

// So khớp tài sản Cloudinary (đọc qua Admin API) với những gì đã ký. Trả về lý do lỗi, hoặc null nếu đạt.
function resourceProblem(resource, expected) {
  if (!resource) return 'Không tìm thấy tệp đã tải lên';
  if (resource.public_id !== expected.publicId) return 'public_id không khớp';
  if (resource.type !== 'authenticated') return 'Tệp không ở chế độ riêng tư';
  if (resource.resource_type !== resourceTypeOf(expected.kind)) return 'Loại tài nguyên không khớp';
  if (resource.format !== expected.format) return 'Định dạng tệp không khớp';
  if (!(resource.bytes > 0 && resource.bytes <= maxBytesOf(expected.kind))) return 'Dung lượng tệp vượt giới hạn';
  if (expected.kind === 'VIDEO') {
    const max = cfg.limits.videoMaxSeconds + 1; // dung sai 1 giây do làm tròn thời lượng
    if (typeof resource.duration !== 'number' || resource.duration > max) {
      return 'Không xác định được thời lượng video hoặc video quá dài';
    }
  }
  return null;
}

// usage: số lượt đã dùng trong ngày + tổng dung lượng đã lưu của cặp đôi. Trả HttpsError hoặc null.
function quotaProblem(usage, sizeBytes) {
  const q = cfg.quotas;
  const exhausted = (message) => new HttpsError('resource-exhausted', message);
  if (usage.userCount >= q.userDailyUploads) {
    return exhausted('Bạn đã dùng hết lượt tải ảnh hôm nay, thử lại vào ngày mai');
  }
  if (usage.relationshipCount >= q.relationshipDailyUploads) {
    return exhausted('Cặp đôi đã dùng hết lượt tải ảnh hôm nay');
  }
  if (usage.globalCount >= q.globalDailyUploads) {
    return exhausted('Hệ thống đang quá tải, thử lại sau');
  }
  if (usage.storedBytes + sizeBytes > q.relationshipStoredBytes) {
    return exhausted('Kho ảnh của cặp đôi đã đầy');
  }
  return null;
}

// Ngày theo UTC (YYYYMMDD) dùng làm khoá bộ đếm; cùng một múi giờ cho mọi instance.
function dayKey(nowMs) {
  return new Date(nowMs).toISOString().slice(0, 10).replaceAll('-', '');
}

module.exports = {
  UUID_RE,
  publicIdFor,
  resourceTypeOf,
  maxBytesOf,
  formatForMime,
  parseIds,
  parseReserveInput,
  resourceProblem,
  quotaProblem,
  dayKey,
};
