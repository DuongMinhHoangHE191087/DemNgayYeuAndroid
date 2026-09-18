package com.example.data.model

/**
 * Role-Based Access Control (RBAC) definitions for InLove.
 */
enum class UserRole(val code: String, val titleVi: String) {
  USER_FREE("USER_FREE", "Thành viên Thường"),
  USER_VIP("USER_VIP", "Thành viên VIP"),
  ADMIN("ADMIN", "Quản trị viên");

  companion object {
    fun fromCode(code: String?): UserRole {
      return entries.find { it.code.equals(code, ignoreCase = true) } ?: USER_FREE
    }
  }
}

/**
 * Subscription Tiers for InLove.
 */
enum class SubscriptionTier(
  val code: String,
  val titleVi: String,
  val priceVi: String,
  val descriptionVi: String
) {
  FREE(
    code = "FREE",
    titleVi = "Gói Miễn Phí",
    priceVi = "0đ",
    descriptionVi = "Trải nghiệm tính năng cơ bản, có kèm quảng cáo hỗ trợ phát triển."
  ),
  VIP_MONTHLY(
    code = "VIP_MONTHLY",
    titleVi = "Gói VIP Tháng",
    priceVi = "39.000đ / tháng",
    descriptionVi = "Tắt 100% quảng cáo, mở khóa gợi ý quà tặng AI và lưu trữ ảnh Cloudinary không giới hạn."
  ),
  VIP_YEARLY(
    code = "VIP_YEARLY",
    titleVi = "Gói VIP Năm (Tiết kiệm 35%)",
    priceVi = "299.000đ / năm",
    descriptionVi = "Mọi đặc quyền VIP suốt 365 ngày kèm huy hiệu Tình Yêu Vĩnh Cửu độc quyền."
  ),
  LIFETIME(
    code = "LIFETIME",
    titleVi = "Gói VIP Trọn Đời",
    priceVi = "599.000đ vĩnh viễn",
    descriptionVi = "Sở hữu toàn bộ tính năng cao cấp mãi mãi cho cả hai người thương."
  );

  companion object {
    fun fromCode(code: String?): SubscriptionTier {
      return entries.find { it.code.equals(code, ignoreCase = true) } ?: FREE
    }
  }
}

/**
 * Helper to determine feature permissions based on Role and Subscription Tier.
 */
object RbacPolicy {
  fun isAdFree(role: UserRole, tier: SubscriptionTier): Boolean {
    return role == UserRole.USER_VIP || role == UserRole.ADMIN || tier != SubscriptionTier.FREE
  }

  fun canUseAiGiftSuggestions(role: UserRole, tier: SubscriptionTier): Boolean {
    // Both VIP and users who have upgraded can use AI gift suggestions freely
    return isAdFree(role, tier)
  }

  fun canUploadUnlimitedMemories(role: UserRole, tier: SubscriptionTier): Boolean {
    return isAdFree(role, tier)
  }
}
