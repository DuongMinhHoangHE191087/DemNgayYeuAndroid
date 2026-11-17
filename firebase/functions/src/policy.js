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

const CONTENT_TYPES = Object.freeze({
  jpg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
  heic: 'image/heic',
  mp4: 'video/mp4',
  mov: 'video/quicktime',
});

// Định dạng lạ là lỗi lập trình (đã qua formatForMime), không phải input người dùng: ném Error thường.
function contentTypeOf(format) {
  if (!Object.hasOwn(CONTENT_TYPES, format)) throw new Error(`Định dạng không có content-type: ${format}`);
  return CONTENT_TYPES[format];
}

const HEIC_BRANDS = new Set(['heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'mif1', 'msf1']);
const VIDEO_BRANDS = new Set([
  'isom', 'iso2', 'iso4', 'iso5', 'iso6', 'mp41', 'mp42', 'avc1', 'M4V ', '3gp4', '3gp5', '3gp6', 'mmp4', 'MSNV', 'dash', 'qt  ',
]);
const startsWith = (head, bytes, at = 0) => bytes.every((b, i) => head[at + i] === b);

// Nhận dạng họ tệp (IMAGE/VIDEO) từ tối đa 32 byte đầu; không đoán được thì null, không bao giờ ném.
// ponytail: kiểm theo HỌ, không theo định dạng (jpg khai báo nhưng gửi png vẫn qua; Coil tự nhận dạng).
// Mục tiêu là chặn tệp không phải media. MOV QuickTime rất cũ không có ftyp bị từ chối. Nâng cấp: so định dạng chính xác nếu thấy lạm dụng.
function sniffFamily(head) {
  if (!Buffer.isBuffer(head)) return null;
  if (startsWith(head, [0xff, 0xd8, 0xff])) return 'IMAGE';
  if (startsWith(head, [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) return 'IMAGE';
  if (head.toString('latin1', 0, 4) === 'RIFF' && head.toString('latin1', 8, 12) === 'WEBP') return 'IMAGE';
  if (head.toString('latin1', 4, 8) !== 'ftyp') return null;
  const brands = [8, 16, 20, 24, 28].filter((at) => at + 4 <= head.length).map((at) => head.toString('latin1', at, at + 4));
  if (brands.some((b) => b === 'avif' || b === 'avis')) return null;
  if (brands.some((b) => HEIC_BRANDS.has(b))) return 'IMAGE';
  if (brands.some((b) => VIDEO_BRANDS.has(b))) return 'VIDEO';
  return null;
}

// So khớp đối tượng R2 (dung lượng, content-type, 32 byte đầu) với những gì đã khai báo lúc reserve.
// Trả về lý do lỗi, hoặc null nếu đạt. expected = { kind, format, declaredBytes }.
function resourceProblem(resource, expected) {
  if (!resource) return 'Không tìm thấy tệp đã tải lên';
  if (resource.bytes !== expected.declaredBytes) return 'Dung lượng tệp không khớp khai báo';
  if (!(resource.bytes > 0 && resource.bytes <= maxBytesOf(expected.kind))) return 'Dung lượng tệp vượt giới hạn';
  if (resource.contentType !== contentTypeOf(expected.format)) return 'Định dạng tệp không khớp';
  if (sniffFamily(resource.head) !== expected.kind) return 'Định dạng tệp không khớp';
  return null;
}

// Hạn mức theo bậc (DEC-169). Ở v1 free = vip = cfg.quotas; chủ sở hữu nới vip ở P6.
const { userDailyUploads, relationshipDailyUploads, relationshipStoredBytes } = cfg.quotas;
const BASE_LIMITS = Object.freeze({ userDailyUploads, relationshipDailyUploads, relationshipStoredBytes });
const TIER_LIMITS = Object.freeze({ free: BASE_LIMITS, vip: BASE_LIMITS });

// Bậc lạ, rỗng hoặc khoá prototype luôn rơi về free: không bao giờ được hạn mức vip.
function limitsFor(tier, table = TIER_LIMITS) {
  return typeof tier === 'string' && Object.hasOwn(table, tier) ? table[tier] : table.free;
}

// usage: số lượt đã dùng trong ngày + tổng dung lượng đã lưu của cặp đôi. Trả HttpsError hoặc null.
// limits: kết quả của limitsFor; mặc định free. Trần toàn hệ thống (globalDailyUploads) không theo bậc.
function quotaProblem(usage, sizeBytes, limits = limitsFor('free')) {
  const q = { ...limits, globalDailyUploads: cfg.quotas.globalDailyUploads };
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
  contentTypeOf,
  sniffFamily,
  resourceProblem,
  TIER_LIMITS,
  limitsFor,
  quotaProblem,
  dayKey,
};
