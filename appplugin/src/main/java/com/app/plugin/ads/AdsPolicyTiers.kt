package com.app.plugin.ads

import com.app.plugin.logevent.LogEventServerHelper
import com.app.plugin.until.LogUtil
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Khối `by_tier` của `ads_policy_android` (1.9.0 Task 4, hợp đồng B, D6): cùng schema_version 1, khoá tuỳ chọn;
 * mỗi tier `T1`/`T2`/`VN`/`OTHER` chỉ có 7 trường v1, trường vắng trong tier giữ giá trị top-level. Nhóm của một
 * quốc gia là [COUNTRY_TIER] = `backend/sql/seed/country_tier.csv` (`CountryTierParityTest` giữ hai nguồn bằng
 * nhau); quốc gia khác hay chưa biết là `OTHER`. Khối sai (tier lạ, trường lạ kể cả `gma_load_app_context`,
 * `by_tier` hay `schema_version` lồng trong tier, `by_tier` hay một tier không phải object) bị bỏ **cả khối**: top-level giữ, đúng một logE.
 * Holder này `internal` và chỉ trả một [AdsPolicy] có sẵn (ABI không đổi).
 */
internal object AdsPolicyTiers {
    const val KEY = "by_tier"
    private const val OTHER = "OTHER"
    private val TIERS = setOf("T1", "T2", "VN", OTHER)

    private const val T1_COUNTRIES = "US CA GB AU DE FR NL SE NO DK FI IS CH"
    private const val T2_COUNTRIES = "AT BE BG HR CY CZ EE GR HU IE IT LV LT LU MT PL PT RO SK SI ES JP KR"

    /** Mã quốc gia (ISO-2, hoa) → tier; nước ngoài bảng là [OTHER]. */
    internal val COUNTRY_TIER: Map<String, String> =
        T1_COUNTRIES.split(' ').associateWith { "T1" } + T2_COUNTRIES.split(' ').associateWith { "T2" } + ("VN" to "VN")

    /** Country của thiết bị (cùng nguồn cột `country` của event); test thay được. */
    @Volatile
    internal var countryProvider: () -> String = { LogEventServerHelper.currentCountry() }

    /** Áp một policy đã resolve lại (main thread). Mặc định là `AdsHelper.applyPolicy`; test thay được. */
    @Volatile
    internal var applier: (AdsPolicy) -> Unit = { AdsHelper.instance.applyPolicy(it) }

    /** Bản thô `ads_policy_android` đã khôi phục hay vừa áp gần nhất, để resolve lại khi country mới biết. */
    @Volatile
    private var lastRaw: String? = null

    @Volatile
    private var lastRejected: String? = null

    /** Top-level đã parse (và khoá JSON của nó) của bản thô gần nhất: áp lại không parse lần hai, nên không lặp logE cấp trường (R-T4-2 a). */
    @Volatile
    private var lastTop: Pair<String, AdsPolicy>? = null

    @Volatile
    private var reapplying = false

    internal fun tierOf(country: String): String = COUNTRY_TIER[country.trim().uppercase()] ?: OTHER

    internal fun remember(raw: String) {
        lastRaw = raw
    }

    /** [top] đã parse từ [o]; trả policy của tier thiết bị nếu [o] có khối `by_tier` hợp lệ chứa tier đó, không thì [top]. */
    internal fun resolve(o: JsonObject, top: AdsPolicy): AdsPolicy {
        lastTop = o.toString() to top
        val e = o.get(KEY) ?: return top
        val reason = invalidReason(e)
        if (reason != null) {
            // Áp lại lúc biết country không lặp logE của chính khối sai đã log ở lần parse trước.
            val sig = e.toString()
            val repeat = reapplying && sig == lastRejected
            lastRejected = sig
            if (repeat) return top
            LogUtil.logE("ads policy: ${AdsPolicy.REMOTE_KEY} $KEY sai ($reason), bỏ cả khối, dùng top-level")
            return top
        }
        val tier = tierOf(countryProvider())
        val own = e.asJsonObject.get(tier)?.asJsonObject
        LogUtil.logD("ads policy schema=${AdsPolicy.SCHEMA_VERSION} tier=$tier source=${if (own != null) "by_tier" else "top"}")
        return if (own == null) top else AdsPolicy.fieldsOf(own, top)
    }

    private fun invalidReason(e: com.google.gson.JsonElement): String? {
        if (!e.isJsonObject) return "không phải object"
        for ((tier, v) in e.asJsonObject.entrySet()) {
            if (tier !in TIERS) return "tier lạ '$tier'"
            if (!v.isJsonObject) return "tier '$tier' không phải object"
            val bad = v.asJsonObject.keySet().firstOrNull { it !in AdsPolicy.FIELD_KEYS }
            if (bad != null) return "tier '$tier' có khoá '$bad' ngoài 7 trường v1"
        }
        return null
    }

    /**
     * Country vừa biết (`LogEventServerHelper.init`, sau `restorePolicy` nếu app khởi tạo ads trước): resolve lại
     * bản thô đã nhớ và áp, chỉ khi nó có `by_tier` (R7). Không có bản thô thì không đụng `AdsHelper`.
     */
    internal fun onCountryKnown() {
        val raw = lastRaw ?: return
        val o = try {
            JsonParser.parseString(raw).asJsonObject
        } catch (e: Exception) {
            return   // JSON hỏng: AdsPolicy.parse đã logE lúc áp, không có by_tier để áp lại
        }
        if (!o.has(KEY)) return
        reapplying = true
        try {
            val top = lastTop?.takeIf { it.first == o.toString() }?.second
            applier(if (top != null) resolve(o, top) else AdsPolicy.parse(raw))
        } finally {
            reapplying = false
        }
    }
}
