'use strict';

// Dữ liệu cũ: memory từng lưu link public https://res.cloudinary.com/... (ảnh/video không ký) và public_id
// không theo quy ước. Module này chỉ đọc URL và quyết định. scripts/migrate-legacy-media.js làm phần I/O.
const policy = require('./policy');

const LEGACY_HOST = 'res.cloudinary.com';
const RESOURCE_TYPES = new Set(['image', 'video']);
// Đoạn biến đổi (w_800, q_auto, c_fill,w_200) và đoạn phiên bản (v1700000000) không thuộc public_id.
// Heuristic: một thư mục tên dạng "ab_..." sẽ bị bỏ sót. Script kiểm tra tệp tồn tại trước khi đổi tên nên an toàn.
const TRANSFORM_SEGMENT = /^[a-z]{1,3}_|,/;
const VERSION_SEGMENT = /^v\d+$/;
const EXTENSION = /\.[A-Za-z0-9]+$/;

const isHttps = (value) => typeof value === 'string' && value.startsWith('https://');

// Link public cũ -> { cloudName, resourceType, kind, publicId }, hoặc null nếu không phải dạng cần chuyển.
function parseLegacyUrl(url) {
  if (!isHttps(url)) return null;
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return null;
  }
  if (parsed.hostname !== LEGACY_HOST) return null;
  const [cloudName, resourceType, deliveryType, ...rest] = parsed.pathname.split('/').filter(Boolean);
  if (!RESOURCE_TYPES.has(resourceType) || deliveryType !== 'upload') return null;

  const kept = rest.filter((segment) => !TRANSFORM_SEGMENT.test(segment) && !VERSION_SEGMENT.test(segment));
  if (kept.length === 0) return null;
  try {
    const last = kept.length - 1;
    const publicId = kept
      .map((segment, index) => decodeURIComponent(index === last ? segment.replace(EXTENSION, '') : segment))
      .join('/');
    if (!publicId) return null;
    return { cloudName, resourceType, kind: resourceType === 'video' ? 'VIDEO' : 'IMAGE', publicId };
  } catch {
    return null;
  }
}

// Các field cần đổi trên memory (giá trị null nghĩa là xoá field). Ảnh preset của app được giữ nguyên.
// publicId undefined nghĩa là không đổi cloudinaryPublicId.
function memoryPatch({ memory, presetUrls, publicId }) {
  const patch = {};
  if (publicId !== undefined && (memory.cloudinaryPublicId ?? '') !== publicId) patch.cloudinaryPublicId = publicId;
  if (memory.photoUri && !presetUrls.has(memory.photoUri)) patch.photoUri = '';
  if (memory.videoUri) patch.videoUri = '';
  if ('cloudinaryUrl' in memory) patch.cloudinaryUrl = null;
  if ('isCloudinaryStored' in memory) patch.isCloudinaryStored = null;
  return patch;
}

// Quyết định cho một memory. action:
//   migrate: đổi tệp public cũ sang authenticated dưới public_id mới, rồi ghi mediaAssets active.
//   clear:   không còn tệp hợp lệ; bỏ link và tham chiếu cũ.
//   keep:    đã theo mô hình mới; chỉ dọn các field link cũ nếu còn.
//   skip:    link trỏ tới cloud khác; không đụng tới, báo cáo để xử lý tay.
function planLegacyMemory({ memoryId, memory, cloudName, presetUrls = new Set() }) {
  const target = policy.publicIdFor(memoryId);
  // Ảnh preset (kể cả khi nằm trên chính cloud này) là tài nguyên công khai dùng chung: không bao giờ chuyển.
  const urls = [memory.cloudinaryUrl, memory.photoUri, memory.videoUri]
    .filter((url) => isHttps(url) && !presetUrls.has(url));
  const parsed = urls.map(parseLegacyUrl).filter(Boolean);
  const own = parsed.find((p) => p.cloudName === cloudName);
  const current = memory.cloudinaryPublicId ?? '';

  if (own) return migrate({ from: own, target, memory, presetUrls });
  if (parsed.length > 0) return { action: 'skip', reason: 'link Cloudinary thuộc cloud khác' };
  if (current === target) return { action: 'keep', patch: memoryPatch({ memory, presetUrls }) };
  if (current) {
    // public_id cũ không còn link đi kèm (link đã bị xoá trước đó): vẫn cần chuyển tệp đó.
    const kind = memory.mediaType === 'VIDEO' ? 'VIDEO' : 'IMAGE';
    const from = { cloudName, resourceType: kind === 'VIDEO' ? 'video' : 'image', kind, publicId: current };
    return migrate({ from, target, memory, presetUrls });
  }
  return { action: 'clear', patch: memoryPatch({ memory, presetUrls, publicId: '' }) };
}

function migrate({ from, target, memory, presetUrls }) {
  return {
    action: 'migrate',
    from,
    to: target,
    kind: from.kind,
    patch: memoryPatch({ memory, presetUrls, publicId: target }),
  };
}

module.exports = { parseLegacyUrl, planLegacyMemory, memoryPatch };
