package com.app.plugin.consent

/**
 * Luật consent dùng chung cho các consumer (Consent Mode của Firebase Analytics, FCM, AppsFlyer), để chúng
 * không lệch nhau khi một nơi được sửa.
 */
internal object ConsentRules {

    /**
     * Người dùng ngoài GDPR được cấp hết mà không xét tầng: chỉ khi `IABTCF_gdprApplies = 0`
     * (`gdprApplies == false`) và tầng không phải [ConsentTier.DENIED].
     *
     * - `null` (khoá vắng hoặc đọc lỗi) là không biết, không bao giờ là ngoài GDPR (Điều 13).
     * - `DENIED` thắng `0`: `0` có thể là giá trị cũ còn lại khi người dùng đã vào EEA, còn `DENIED` là
     *   câu trả lời thật.
     */
    fun outsideGdprGrantsAll(tier: ConsentTier, gdprApplies: Boolean?): Boolean =
        gdprApplies == false && tier != ConsentTier.DENIED

    /**
     * Tầng cho phép một SDK chỉ chạy khi có consent (FCM, AppsFlyer, Meta AN và Meta app events ở cổng và khi start muộn): `GRANTED`
     * hoặc `PARTIAL`, hoặc ngoài GDPR ([outsideGdprGrantsAll]). `UNKNOWN` trong GDPR hoặc không biết thì không: UNKNOWN
     * không bao giờ là đồng ý (Điều 13). `DENIED` không bao giờ.
     */
    fun tierAllows(tier: ConsentTier, gdprApplies: Boolean?): Boolean =
        tier == ConsentTier.GRANTED || tier == ConsentTier.PARTIAL || outsideGdprGrantsAll(tier, gdprApplies)

    /** Tầng trong log của cổng consent (từ chối, start): `consent tier=<tầng> in|outside GDPR scope` (vắng hay lỗi tính là trong). */
    fun scopeLabel(tier: ConsentTier, gdprApplies: Boolean?): String =
        "consent tier=$tier ${if (gdprApplies == false) "outside" else "in"} GDPR scope"

    /**
     * Người dùng đã opt-out bán/chia sẻ dữ liệu hoặc quảng cáo nhắm mục tiêu theo luật bang Mỹ (GPP section 7–12,
     * [GppConsent]). `null` (không có GPP, không phải người Mỹ theo UMP) là không opt-out. Không đổi [tierAllows]
     * hay [outsideGdprGrantsAll]; chưa consumer nào gọi (1.6.0 Task 7, nối ở Task 8).
     */
    internal fun usOptOut(gpp: GppSignals?): Boolean = gpp?.usOptOut == true

    /** Nhãn GPP trong log của cổng consent: `gpp=optOut`, `gpp=ok` hoặc `gpp=none` (không có GPP áp dụng). */
    internal fun gppLabel(gpp: GppSignals?): String = when {
        gpp == null -> "gpp=none"
        gpp.usOptOut -> "gpp=optOut"
        else -> "gpp=ok"
    }
}
