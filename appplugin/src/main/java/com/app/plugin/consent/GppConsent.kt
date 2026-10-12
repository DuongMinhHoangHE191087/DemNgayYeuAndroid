package com.app.plugin.consent

import android.content.Context
import android.content.SharedPreferences
import com.app.plugin.until.LogUtil

/**
 * Đọc và giải mã tín hiệu GPP 1.1 mà UMP ghi vào default SharedPreferences (`IABGPP_HDR_GppString`,
 * `IABGPP_GppSID`), để trả lời một câu: người dùng có opt-out bán/chia sẻ dữ liệu hoặc quảng cáo nhắm mục tiêu
 * theo luật bang Mỹ không ([GppSignals.usOptOut]).
 *
 * Chuỗi GPP: các section cách nhau bằng `~`. Section đầu là header (base64url, 6 bit/ký tự): `Type` 6 bit = 3,
 * `Version` 6 bit, danh sách id section mã hoá Fibonacci range (delta). Mỗi section sau ứng với một id theo thứ tự
 * header; section có sub-section cách bằng `.` (core rồi GPC). Chỉ giải mã **core segment** của US National (7) và
 * CA/VA/CO/UT/CT (8–12); id khác bỏ qua. Mỗi trường 2 bit: 0 = không áp dụng, 1 = opt-out, 2 = không opt-out.
 * Các trường quyết định nằm trước mọi mảng độ dài thay đổi, nên tiền tố layout ổn định giữa các `Version` của
 * section; `Version` chỉ để log.
 *
 * `IABGPP_GppSID` (id section áp dụng, `"7"`, `"7_8"`; nhận cả `,`) lọc section xét opt-out; vắng hoặc sai dạng thì
 * xét mọi section trong header. Như [TcfConsent.read]: mọi lỗi đọc/giải mã → "không có GPP" (`null`), một dòng logE.
 *
 * Chưa nối vào consumer nào (1.6.0 Task 7); Task 8 nối và chốt layout bằng vector UMP đo trên emulator.
 */
internal object GppConsent {
    private const val KEY_GPP_STRING = "IABGPP_HDR_GppString"
    private const val KEY_GPP_SID = "IABGPP_GppSID"

    private const val VERSION_BITS = 6
    private const val FIELD_BITS = 2
    private const val OPT_OUT = 1
    private const val GPC_SUBSECTION_TYPE = 1
    private const val SUBSECTION_TYPE_BITS = 2

    private const val SALE = "SaleOptOut"
    private const val SHARING = "SharingOptOut"
    private const val TARGETED = "TargetedAdvertisingOptOut"

    private val VA_CO_CT = listOf("SharingNotice", "SaleOptOutNotice", "TargetedAdvertisingOptOutNotice", SALE, TARGETED)

    /** Tiền tố core segment sau `Version` (6 bit), mỗi trường 2 bit, theo đặc tả IAB GPP Sections/US*. */
    private val LAYOUTS: Map<Int, List<String>> = mapOf(
        7 to listOf(
            "SharingNotice", "SaleOptOutNotice", "SharingOptOutNotice", "TargetedAdvertisingOptOutNotice",
            "SensitiveDataProcessingOptOutNotice", "SensitiveDataLimitUseNotice", SALE, SHARING, TARGETED,
        ),
        8 to listOf("SaleOptOutNotice", "SharingOptOutNotice", "SensitiveDataLimitUseNotice", SALE, SHARING),
        9 to VA_CO_CT,
        10 to VA_CO_CT,
        11 to listOf(
            "SharingNotice", "SaleOptOutNotice", "TargetedAdvertisingOptOutNotice",
            "SensitiveDataProcessingOptOutNotice", SALE, TARGETED,
        ),
        12 to VA_CO_CT,
    )

    fun read(context: Context): GppSignals? = try {
        read(context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE))
    } catch (e: Exception) {
        LogUtil.logE("GppConsent: cannot open default preferences, GPP treated as missing: ${e.message}")
        null
    }

    fun read(prefs: SharedPreferences): GppSignals? {
        val gpp: String?
        val sid: String?
        try {
            gpp = prefs.getString(KEY_GPP_STRING, null)
            sid = prefs.getString(KEY_GPP_SID, null)
        } catch (e: Exception) {
            // Sai kiểu (CMP khác ghi GppSID dạng số) thì coi như chưa có GPP, không crash.
            LogUtil.logE("GppConsent: unreadable IABGPP signals, GPP treated as missing: ${e.message}")
            return null
        }
        return decode(gpp, sid)
    }

    /**
     * Giải mã [gpp] (`IABGPP_HDR_GppString`) với [sid] (`IABGPP_GppSID`). `null` khi chuỗi vắng/rỗng, không có
     * section 7–12 áp dụng, hoặc chuỗi hỏng (ký tự ngoài base64url, header không phải type 3, thiếu section,
     * core segment cụt) — chuỗi hỏng có đúng một dòng logE.
     */
    fun decode(gpp: String?, sid: String?): GppSignals? {
        if (gpp.isNullOrBlank()) return null
        return try {
            decodeOrThrow(gpp, sid)
        } catch (e: Exception) {
            // Không để lỗi nào của chuỗi GPP thoát ra luồng consent: mọi lỗi → "không có GPP" (như TcfConsent).
            LogUtil.logE("GppConsent: unreadable IABGPP_HDR_GppString, GPP treated as missing: ${e.message}")
            null
        }
    }

    /**
     * 1.6.3 Task 6 (quyết định chủ dự án C + GRANTED, review cuối 1.6.2 §4.1): header `IABGPP_HDR_GppString` còn liệt kê
     * section US 7–12 không. Không xét `IABGPP_GppSID`. `false` chỉ khi header đọc được và không có id US; `true` khi có id
     * US và section US giải mã được; `null` khi không có chuỗi, header hỏng hoặc section US hỏng — người gọi không gỡ gì
     * (fail-closed). Gọi lại `decode` (SID bỏ qua): đường start lại có thể log thêm một dòng logD/logE như `read`.
     */
    internal fun headerHasUsSection(prefs: SharedPreferences): Boolean? {
        val gpp = try { prefs.getString(KEY_GPP_STRING, null) } catch (e: Exception) { return null }
        if (gpp.isNullOrBlank()) return null
        return try {
            val parts = gpp.split('~')
            val ids = GppBits.decodeHeader(GppBits.toBits(parts[0]), maxIds = parts.size - 1)
            if (ids.none { it in LAYOUTS }) false else if (decode(gpp, null) != null) true else null
        } catch (e: Exception) { null }
    }

    internal fun headerHasUsSection(context: Context): Boolean? = try {
        headerHasUsSection(context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE))
    } catch (e: Exception) { null }

    private class UsSection(val id: Int, val version: Int, val fields: Map<String, Int>, val gpc: Boolean?)

    private fun decodeOrThrow(gpp: String, sid: String?): GppSignals? {
        val parts = gpp.split('~')
        val ids = GppBits.decodeHeader(GppBits.toBits(parts[0]), maxIds = parts.size - 1)
        require(parts.size == ids.size + 1) { "header lists ${ids.size} sections, string has ${parts.size - 1}" }
        val sidIds = parseSid(sid)
        val applicable = sidIds ?: ids.toSet()
        val us = ids.withIndex()
            .filter { (_, id) -> id in LAYOUTS && id in applicable }
            .map { (i, id) -> decodeUsSection(id, parts[i + 1]) }
        // Chỉ log GppSID sai dạng khi chuỗi đã giải mã xong: một chuỗi hỏng chỉ có một dòng logE.
        if (sidIds == null && !sid.isNullOrBlank()) {
            LogUtil.logE("GppConsent: IABGPP_GppSID '$sid' unreadable, using header sections $ids")
        }
        if (us.isEmpty()) {
            LogUtil.logD("GppConsent: sections=$ids sid=$sid, no US section 7–12 applies")
            return null
        }
        val gpcFlags = us.mapNotNull { it.gpc }
        val signals = GppSignals(
            sectionIds = ids,
            saleOptOut = us.any { it.fields[SALE] == OPT_OUT },
            sharingOptOut = us.any { it.fields[SHARING] == OPT_OUT },
            targetedAdsOptOut = us.any { it.fields[TARGETED] == OPT_OUT },
            gpc = if (gpcFlags.isEmpty()) null else gpcFlags.any { it },
            raw = gpp,
        )
        LogUtil.logD(
            "GppConsent: sections=$ids sid=$sid versions=${us.map { "${it.id}:v${it.version}" }} " +
                "sale=${signals.saleOptOut} sharing=${signals.sharingOptOut} " +
                "targeted=${signals.targetedAdsOptOut} gpc=${signals.gpc}"
        )
        return signals
    }

    /** `null` khi vắng, rỗng hoặc sai dạng (khi đó xét mọi section trong header). */
    private fun parseSid(sid: String?): Set<Int>? {
        if (sid.isNullOrBlank()) return null
        val ids = sid.split('_', ',').map { it.trim().toIntOrNull() }
        return if (ids.any { it == null }) null else ids.filterNotNull().toSet()
    }

    private fun decodeUsSection(id: Int, section: String): UsSection {
        val segments = section.split('.')
        val core = GppBits.toBits(segments[0])
        val layout = LAYOUTS.getValue(id)
        val fields = layout.withIndex().associate { (i, name) ->
            name to GppBits.readInt(core, VERSION_BITS + i * FIELD_BITS, FIELD_BITS)
        }
        var gpc: Boolean? = null
        for (segment in segments.drop(1)) {
            val bits = GppBits.toBits(segment)
            if (GppBits.readInt(bits, 0, SUBSECTION_TYPE_BITS) == GPC_SUBSECTION_TYPE) {
                gpc = GppBits.readInt(bits, SUBSECTION_TYPE_BITS, 1) == 1
            }
        }
        return UsSection(id, GppBits.readInt(core, 0, VERSION_BITS), fields, gpc)
    }
}
