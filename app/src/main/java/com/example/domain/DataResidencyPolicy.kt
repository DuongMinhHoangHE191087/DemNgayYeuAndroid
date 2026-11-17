package com.example.domain

/** Nơi nội dung người dùng (kỷ niệm, ảnh/video) được lưu. Xác thực, bắt tay ghép đôi, preset, thanh toán và App Check luôn chạy online và KHÔNG bị cổng này chặn. */
enum class DataResidency { LOCAL_ONLY, CLOUD_SYNC }

object DataResidencyPolicy {
  // ponytail: công tắc lúc biên dịch; chuyển sang Remote Config chỉ khi chủ dự án cần rollback theo đợt.
  val current: DataResidency = DataResidency.LOCAL_ONLY
  val cloudContent: Boolean get() = current == DataResidency.CLOUD_SYNC

  /** Hồ sơ cá nhân lưu ở Firestore users/{uid}. Công tắc tắt khẩn cấp: tắt thì wiring không dựng ProfileSync. */
  val profileOnline: Boolean = true

  /** Online chỉ có hồ sơ cá nhân (Firestore) và ảnh đại diện khi avatarsOnR2 bật (R2, và chỉ khi đó avatarUrl mới được đồng bộ). TẮT cho tới khi chủ dự án tạo bucket avatar công khai. */
  val avatarsOnR2: Boolean = false
}
