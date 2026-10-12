package com.app.plugin.consent

/**
 * Tín hiệu GPP (Global Privacy Platform) mà UMP ghi vào `IABGPP_*`, đã giải mã cho các section luật bang Mỹ mà
 * SDK đọc: US National (7) và CA/VA/CO/UT/CT (8–12). Do [GppConsent] dựng; `null` ở chỗ gọi nghĩa là không có
 * GPP hoặc không có section 7–12 nào áp dụng (không phải người Mỹ theo UMP, hoặc thông điệp US chưa bật).
 *
 * @property sectionIds id section theo thứ tự trong header chuỗi GPP (kể cả id ngoài 7–12, vd 2 TCF EU).
 * @property saleOptOut một section áp dụng có `SaleOptOut = 1`.
 * @property sharingOptOut một section áp dụng có `SharingOptOut = 1` (chỉ 7 và 8 có trường này).
 * @property targetedAdsOptOut một section áp dụng có `TargetedAdvertisingOptOut = 1` (7, 9–12).
 * @property gpc cờ Global Privacy Control từ sub-section GPC; `null` khi không section nào có sub-section đó. Chỉ để
 *   log, không quyết định opt-out.
 * @property raw chuỗi `IABGPP_HDR_GppString` gốc.
 */
internal data class GppSignals(
    val sectionIds: List<Int>,
    val saleOptOut: Boolean,
    val sharingOptOut: Boolean,
    val targetedAdsOptOut: Boolean,
    val gpc: Boolean?,
    val raw: String,
) {
    /** Người dùng đã opt-out bán, chia sẻ hoặc quảng cáo nhắm mục tiêu theo luật bang Mỹ. GPC không tính. */
    val usOptOut: Boolean get() = saleOptOut || sharingOptOut || targetedAdsOptOut
}
