'use strict';

// Các giá trị hay thay đổi nằm ở một chỗ này. Đổi giới hạn, quota, định dạng hay region
// thì chỉ sửa file này rồi deploy lại functions.
const MB = 1024 * 1024;
const GB = 1024 * MB;
const DAY_MS = 24 * 60 * 60 * 1000;

module.exports = Object.freeze({
  region: 'asia-southeast1',

  limits: Object.freeze({
    imageMaxBytes: 10 * MB,
    videoMaxBytes: 50 * MB,
    videoMaxSeconds: 60,
    avatarMaxBytes: 1 * MB,
  }),

  // MIME mà app được phép khai báo -> định dạng nội bộ. Server ký đúng một content-type cho định dạng đó,
  // rồi confirm kiểm lại content-type, dung lượng và byte đầu tệp, nên tệp khác loại bị từ chối.
  mimeFormats: Object.freeze({
    IMAGE: Object.freeze({
      'image/jpeg': 'jpg',
      'image/jpg': 'jpg',
      'image/png': 'png',
      'image/webp': 'webp',
      'image/heic': 'heic',
      'image/heif': 'heic',
    }),
    VIDEO: Object.freeze({
      'video/mp4': 'mp4',
      'video/quicktime': 'mov',
    }),
  }),

  quotas: Object.freeze({
    userDailyUploads: 20,
    relationshipDailyUploads: 40,
    globalDailyUploads: 5000,
    relationshipStoredBytes: 2 * GB,
    // Trần dung lượng đã xác nhận của cả hệ thống: 8 GB, thấp hơn 10 GB-tháng miễn phí của Cloudflare R2 để còn đệm.
    // Khi chuyển sang gói trả phí thì nâng lên.
    globalStoredBytes: 8 * GB,
  }),

  timing: Object.freeze({
    // Thời hạn để app confirm sau khi đã ký upload. Quá hạn thì tài sản bị dọn bởi sweeper.
    reservationTtlMs: 2 * 60 * 60 * 1000,
    // Phiên đã hết hạn được giữ thêm khoảng này rồi mới xoá tệp, để tệp upload muộn (nếu có) cũng bị dọn.
    // 1 giờ vì sweeper chạy hàng giờ: tệp chưa xác nhận sống tối đa khoảng 3–4 giờ.
    lateUploadGraceMs: 60 * 60 * 1000,
    // Tài sản đã xác nhận mà không memory nào trỏ tới (app mất kết nối trước khi ghi memory, hoặc memory đã bỏ ảnh)
    // được kiểm tra lại sau khoảng này; vẫn không có memory thì xoá. Tăng lên nếu app có thể offline lâu hơn.
    orphanAfterMs: 30 * DAY_MS,
    // Thời hạn của URL xem ảnh/video. Hết hạn thì app xin URL mới.
    deliveryTtlSeconds: 15 * 60,
    // Bộ đếm theo ngày: TTL Firestore xoá sau khoảng này.
    dailyCounterTtlMs: 2 * DAY_MS,
    // Bản ghi asset đã xoá được giữ để đối soát rồi TTL xoá.
    deletedRecordTtlMs: 30 * DAY_MS,
    // Số mục xử lý tối đa cho mỗi bước của sweeper trong một lần chạy.
    sweepBatchSize: 300,
  }),

  // Rate limit theo uid cho từng callable (src/rateLimit.js): tối đa `max` lần trong mỗi cửa sổ `windowMs`.
  rateLimits: Object.freeze({
    acceptCoupleInvite: Object.freeze({ max: 10, windowMs: 60 * 60 * 1000 }),
    requestAccountDeletion: Object.freeze({ max: 5, windowMs: 60 * 60 * 1000 }),
    signMemoryUpload: Object.freeze({ max: 30, windowMs: 60 * 1000 }),
    confirmMemoryUpload: Object.freeze({ max: 60, windowMs: 60 * 1000 }),
    getMemoryMediaUrl: Object.freeze({ max: 300, windowMs: 60 * 1000 }),
    getAccountToken: Object.freeze({ max: 20, windowMs: 60 * 60 * 1000 }),
    claimPurchase: Object.freeze({ max: 30, windowMs: 60 * 60 * 1000 }),
    getEntitlement: Object.freeze({ max: 60, windowMs: 60 * 60 * 1000 }),
    signAvatarUpload: Object.freeze({ max: 10, windowMs: 60 * 60 * 1000 }),
  }),

  // Khoá đối tượng trên R2: inlove_mem_<memoryId>. Không có thư mục, không có tên người dùng.
  publicIdPrefix: 'inlove_mem_',
  // Avatar: một tệp cố định theo uid trong bucket công khai riêng (khoá inlove_avt_<uid>).
  avatarPublicIdPrefix: 'inlove_avt_',
});
