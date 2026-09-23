package com.example.billing

/**
 * Nguồn sự thật DUY NHẤT cho Product ID của 3 gói VIP — phải khớp CHÍNH XÁC với Product ID
 * đã khai báo trên Google Play Console.
 *
 * Trước khi có object này, [BillingManager] (billing client THẬT duy nhất chạy purchase flow
 * qua Google Play) dùng `vip_monthly/vip_yearly/vip_lifetime`, còn `InLoveApplication` cấu hình
 * `MonetizationSdk` (appplugin) với `inlove_vip_monthly/inlove_vip_yearly/inlove_vip_lifetime` —
 * hai bộ ID khác nhau cho cùng 3 sản phẩm. Hậu quả: `com.app.plugin.iap.Entitlements` (dùng để
 * tắt quảng cáo cho VIP ở tầng appplugin) không bao giờ nhận diện đúng sản phẩm mà người dùng
 * thực sự mua qua [BillingManager].
 *
 * Quy ước từ nay: mọi nơi cần Product ID VIP (billing client, cấu hình appplugin, UI Paywall)
 * đều tham chiếu object này. Giá trị giữ nguyên `vip_monthly/vip_yearly/vip_lifetime` vì đó là
 * ID đã cấu hình thật trên Play Console (đừng đổi giá trị nếu chưa xác nhận với Play Console).
 */
object VipProductIds {
    const val MONTHLY = "vip_monthly"
    const val YEARLY = "vip_yearly"     // Có ưu đãi 3 ngày Free Trial
    const val LIFETIME = "vip_lifetime" // In-App Purchase (mua đứt)

    /** Tất cả Product ID VIP — dùng để kiểm tra một purchase có thuộc nhóm VIP hay không. */
    val ALL: Set<String> = setOf(MONTHLY, YEARLY, LIFETIME)
}
