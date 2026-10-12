package com.app.plugin.consent

import android.content.Context
import android.content.SharedPreferences
import com.app.plugin.until.LogUtil

/** Các khoá IABTCF_* mà UMP ghi vào default SharedPreferences của app (TCF v2.2 CMP API). */
internal data class TcfSignals(
    val gdprApplies: Int?,
    val purposeConsents: String,
    val purposeLegitimateInterests: String,
    val vendorConsents: String
)

/**
 * Đọc tầng consent từ tín hiệu TCF v2.2 mà UMP ghi, theo từng purpose.
 *
 * Trạng thái `OBTAINED` của UMP chỉ nói luồng consent đã xong, không nói người dùng đồng ý gì:
 * bấm "Do not consent" cũng là `OBTAINED` (đo 2026-09-24: `IABTCF_PurposeConsents = 0000000000`).
 * Hiến pháp Điều 13: tín hiệu thiếu hay hỏng chỉ cho UNKNOWN hoặc DENIED, không bao giờ GRANTED.
 */
internal object TcfConsent {
    const val GOOGLE_VENDOR_ID = 755
    private const val P_STORAGE = 1
    private const val P_BASIC_ADS = 2
    private const val P_PROFILE = 3
    private const val P_PERSONALISED_ADS = 4
    private const val P_MEASURE_ADS = 7
    private const val P_MARKET_RESEARCH = 9
    private const val P_IMPROVE = 10

    private const val KEY_GDPR_APPLIES = "IABTCF_gdprApplies"
    private const val KEY_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"
    private const val KEY_PURPOSE_LI = "IABTCF_PurposeLegitimateInterests"
    private const val KEY_VENDOR_CONSENTS = "IABTCF_VendorConsents"

    private val EMPTY = TcfSignals(null, "", "", "")

    fun read(context: Context): TcfSignals = try {
        read(context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE))
    } catch (e: Exception) {
        LogUtil.logE("TcfConsent: cannot open default preferences, TCF treated as missing: ${e.message}")
        EMPTY
    }

    fun read(prefs: SharedPreferences): TcfSignals = try {
        TcfSignals(
            gdprApplies = if (prefs.contains(KEY_GDPR_APPLIES)) prefs.getInt(KEY_GDPR_APPLIES, 0) else null,
            purposeConsents = prefs.getString(KEY_PURPOSE_CONSENTS, "") ?: "",
            purposeLegitimateInterests = prefs.getString(KEY_PURPOSE_LI, "") ?: "",
            vendorConsents = prefs.getString(KEY_VENDOR_CONSENTS, "") ?: ""
        )
    } catch (e: Exception) {
        // Sai kiểu (CMP khác ghi gdprApplies dạng chuỗi) thì coi như chưa có TCF: UNKNOWN, không GRANTED.
        LogUtil.logE("TcfConsent: unreadable IABTCF signals, TCF treated as missing: ${e.message}")
        EMPTY
    }

    /**
     * `IABTCF_gdprApplies` có áp dụng không: `true` khi khác 0, `false` khi bằng 0, `null` khi khoá
     * vắng hoặc không đọc được (chưa có CMP ghi, sai kiểu, không mở được preferences).
     *
     * `null` không phải "không áp dụng". Consumer không được lấy `null` làm lý do cấp quyền; cần
     * Boolean thì phải chọn "không biết → chưa được cấp" ngay tại chỗ gọi, kèm comment.
     */
    fun gdprApplies(context: Context): Boolean? = read(context).gdprApplies?.let { it != 0 }

    /**
     * GRANTED: đủ điều kiện quảng cáo cá nhân hoá. PARTIAL: chỉ đủ cho quảng cáo không cá nhân
     * hoá → brain không tích luỹ hồ sơ (ConsentState.allowsProfiling). DENIED: thiếu purpose 1,
     * vendor Google, hoặc một trong purpose 2, 7, 9, 10. UNKNOWN: `IABTCF_gdprApplies` vắng hoặc
     * không đọc được, tức TCF không cho câu trả lời nào.
     */
    fun tierOf(s: TcfSignals): ConsentTier {
        val gdpr = s.gdprApplies ?: return ConsentTier.UNKNOWN
        if (gdpr == 0) return ConsentTier.GRANTED
        val storage = has(s.purposeConsents, P_STORAGE)
        val google = has(s.vendorConsents, GOOGLE_VENDOR_ID)
        val basic = listOf(P_BASIC_ADS, P_MEASURE_ADS, P_MARKET_RESEARCH, P_IMPROVE)
            .all { has(s.purposeConsents, it) || has(s.purposeLegitimateInterests, it) }
        if (!storage || !google || !basic) return ConsentTier.DENIED
        val personalised = has(s.purposeConsents, P_PROFILE) && has(s.purposeConsents, P_PERSONALISED_ADS)
        return if (personalised) ConsentTier.GRANTED else ConsentTier.PARTIAL
    }

    /**
     * Ghép trạng thái UMP với TCF. Chỉ khi UMP là `OBTAINED` thì TCF mới là câu trả lời của người
     * dùng; ngoài đó (`NOT_REQUIRED`, `REQUIRED`) giữ tầng UMP, để chuỗi TCF cũ còn sót không mở
     * rộng quyền. `OBTAINED` mà không đọc được TCF thì hạ GRANTED xuống UNKNOWN (Điều 13).
     *
     * UNKNOWN mà hàm này trả nghĩa là **chưa có câu trả lời dùng được**, và không bao giờ là đồng ý:
     * 1. Nghĩa: chưa chạy luồng consent, hoặc UMP `OBTAINED` mà không đọc được IABTCF.
     * 2. Mọi consumer về quảng cáo, analytics, FCM, AppsFlyer, brain và CRM phải coi UNKNOWN là chưa được cấp.
     * 3. Task 9 siết cho Analytics/FCM/AppsFlyer; quyết định #7 (DOC/66) siết `ConsentState.allowsProfiling`/
     *    `allowsPersonalization`: UNKNOWN chỉ được cấp khi `IABTCF_gdprApplies = 0` đọc được rõ ràng.
     */
    fun resolve(umpObtained: Boolean, umpTier: ConsentTier, tcfTier: ConsentTier): ConsentTier = when {
        !umpObtained -> umpTier
        tcfTier != ConsentTier.UNKNOWN -> tcfTier
        umpTier == ConsentTier.GRANTED -> ConsentTier.UNKNOWN
        else -> umpTier
    }

    private fun has(bits: String, index1: Int): Boolean =
        index1 >= 1 && bits.length >= index1 && bits[index1 - 1] == '1'
}
