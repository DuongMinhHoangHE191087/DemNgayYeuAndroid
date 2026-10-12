package com.app.plugin.ads

/**
 * Những định dạng quảng cáo người dùng đã *trả tiền* để gỡ, dạng bitmask trên cùng số hiệu cờ
 * mà `AdsHelper.isDisableAds(flag)` dùng (bit `1 shl flag`). Ngược với `maskAdsStatus` từ remote
 * config: ở đây bit bật nghĩa là "đã gỡ", không phải "được bật".
 *
 * Là mask chứ không phải boolean vì hai sản phẩm cần hai mask khác nhau: gói Pro gỡ hết
 * ([ALL]), còn IAP gỡ quảng cáo rẻ hơn chỉ gỡ các định dạng làm gián đoạn ([INTERRUPTIVE]):
 * native inline vẫn hiện, rewarded vẫn còn vì người dùng đó vẫn cần nó để mở khoá nội dung.
 * Lấy từ Invoice Maker. Public từ 2.3.0 (ROB-3): INTEGRATION.md §9 bước 6 và §14 bước 2 bảo app dùng các hằng này.
 */
public object RemoveAdsMask {
    public const val FLAG_BANNER: Int = 0
    public const val FLAG_NATIVE_COLLAPSIBLE: Int = 1
    public const val FLAG_RECT: Int = 2
    public const val FLAG_NATIVE: Int = 3

    /** App open, native full và interstitial: SDK chặn cả ba trên cùng một cờ. */
    public const val FLAG_FULLSCREEN: Int = 4
    public const val FLAG_REWARDED: Int = 5
    public const val BIT_COUNT: Int = 6

    /** Mọi định dạng — thứ gói Pro mua được. */
    public const val ALL: Int = (1 shl BIT_COUNT) - 1

    /**
     * Chỉ các định dạng gián đoạn hoặc chiếm màn hình: banner, native collapsible, rect và nhóm toàn màn hình.
     * Native collapsible (NtCl) nằm trong nhóm này vì nó là lớp phủ đáy che ~45 % màn hình (`AdsPlNtClBase`), gây
     * gián đoạn hơn banner 50dp mà gói rẻ đã gỡ (quyết định #15, DOC/66, 1.4.0; trước 1.4.0 gói rẻ giữ NtCl).
     * Native inline và rewarded không thuộc nhóm này.
     */
    public const val INTERRUPTIVE: Int = (1 shl FLAG_BANNER) or (1 shl FLAG_NATIVE_COLLAPSIBLE) or (1 shl FLAG_RECT) or
        (1 shl FLAG_FULLSCREEN)

    public const val NONE: Int = 0

    public fun isRemoved(mask: Int, flag: Int): Boolean {
        if (flag < 0 || flag >= BIT_COUNT) return false
        return (mask and (1 shl flag)) != 0
    }

    /** Chỉ giữ các bit hợp lệ, để một giá trị lạ từ app không bật nhầm cờ ngoài phạm vi. */
    public fun sanitize(mask: Int): Int = mask and ALL
}
