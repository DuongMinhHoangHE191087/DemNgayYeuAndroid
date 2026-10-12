package com.app.plugin.logevent

import com.app.plugin.segment.SegmentHooks
import java.util.Locale
import java.util.UUID

/**
 * Event schema v2 lên server của SDK (1.7.0 Task 1, plan "Hợp đồng event v2"): một chỗ dựng `eventData` của 8 event
 * quảng cáo (`ad_request`, `ad_fill`, `ad_no_fill`, `ad_show_attempt`, `ad_show`, `ad_show_fail`, `ad_click`,
 * `ad_revenue`) và `consent_form_shown`, `consent_choice`, `session_end`. Mỗi event có builder map thuần `…Data` (test
 * hợp đồng đọc) và hàm gửi cùng tên qua [LogEventTransport.sendEvent] (dựng body và envelope trên IO, như mọi event).
 *
 * Key là snake_case ≤ 40 ký tự (`AdEventContractTest`). Hàm `LogEventServer.logAd*` (public, chữ ký v1) uỷ quyền tới đây.
 */
internal object AdEventLog {
    /** `error_code` của `ad_no_fill` khi watchdog slot đóng request (mạng không callback). */
    const val TIMEOUT = "timeout"
    private const val REASON_MAX = 100

    /** Trường chung của chuỗi load: `ad_fill`/`ad_no_fill` mang lại đúng các trường của `ad_request` cùng request. */
    internal data class LoadRequest(
        val adFormat: String,
        val mediation: String,
        val placement: String,
        val adUnit: String,
        val requestId: String,
        val screenName: String,
        val isSdkInit: Int
    )

    fun newRequestId(): String = UUID.randomUUID().toString()

    /** Tên mạng trung gian của SDK (`admob`/`applovin`/`facebook`) → `mediation` v2 (`admob`\|`max`\|`meta_direct`). */
    fun mediationOf(adPlatform: String): String = when (val p = adPlatform.lowercase(Locale.ROOT)) {
        "admob" -> "admob"
        "applovin", "max" -> "max"
        "facebook", "meta", "meta_direct" -> "meta_direct"
        else -> p
    }

    /**
     * `ad_format` của `ad_show_fail`, suy từ tiền tố placement (chỗ gọi chỉ có placement). `full_` dùng chung cho
     * interstitial, app open và native full nên là `fullscreen`; `rwi_` (1.9.0) là `rewarded_interstitial`; tiền tố lạ = `unknown`.
     */
    fun formatOfPlacement(placement: String): String = when {
        placement.startsWith("full_") -> "fullscreen"
        placement.startsWith("gift_") -> "rewarded"
        placement.startsWith("rwi_") -> "rewarded_interstitial" // 1.9.0 Task 2
        placement.startsWith("open_") -> "opened"
        placement.startsWith("bn_") -> "banner"
        placement.startsWith("cl_") -> "native_collapse"
        placement.startsWith("nt_") -> "native_custom"
        placement.startsWith("rect_") -> "rect"
        else -> "unknown"
    }

    // ── Builder thuần ────────────────────────────────────────────────────────────────────────────

    /**
     * [slot] = khoá slot mạng của request (`originPl` của slot), tách khỏi `placement` để nối load → show theo slot (1.8.0
     * Task 15, carry T3b). Nằm ở param map, không ở [LoadRequest] (ABI D1).
     */
    fun requestData(r: LoadRequest, slot: String): Map<String, Any> = mapOf(
        "ad_format" to r.adFormat,
        "mediation" to r.mediation,
        "placement" to r.placement,
        "ad_unit" to r.adUnit,
        "request_id" to r.requestId,
        "screen_name" to r.screenName,
        "is_sdk_init" to r.isSdkInit,
        "slot" to slot
    )

    /** Không có slot riêng (đường v1 `LogEventServer.logAd*`): slot = `placement` của request (slot mạng), như chuỗi load. */
    fun requestData(r: LoadRequest): Map<String, Any> = requestData(r, r.placement)

    fun fillData(r: LoadRequest, latencyMs: Long, adSource: String): Map<String, Any> = fillData(r, r.placement, latencyMs, adSource)

    fun noFillData(r: LoadRequest, latencyMs: Long, errorCode: String, reason: String): Map<String, Any> =
        noFillData(r, r.placement, latencyMs, errorCode, reason)

    /** [latencyMs] `-1` = không có mốc bắt đầu; [adSource] = mạng thắng (`AppUtil.extractAdNetwork`). */
    fun fillData(r: LoadRequest, slot: String, latencyMs: Long, adSource: String): Map<String, Any> =
        requestData(r, slot) + mapOf("latency_ms" to latencyMs, "ad_source" to adSource)

    /** [errorCode] = mã mạng dạng chuỗi hoặc [TIMEOUT]; [reason] cắt ≤ 100 ký tự. */
    fun noFillData(r: LoadRequest, slot: String, latencyMs: Long, errorCode: String, reason: String): Map<String, Any> =
        requestData(r, slot) + mapOf("latency_ms" to latencyMs, "error_code" to errorCode, "reason" to reason.take(REASON_MAX))

    /** [ready] 1 nếu placement đã load lúc gọi show, 0 nếu không. */
    fun showAttemptData(adFormat: String, placement: String, ready: Int, screenName: String): Map<String, Any> = mapOf(
        "ad_format" to adFormat,
        "placement" to placement,
        "ready" to ready,
        "screen_name" to screenName
    )

    fun showData(
        adFormat: String, mediation: String, adSource: String, placement: String, adUnit: String, impId: String
    ): Map<String, Any> = mapOf(
        "ad_format" to adFormat,
        "mediation" to mediation,
        "ad_source" to adSource,
        "placement" to placement,
        "ad_unit" to adUnit,
        "imp_id" to impId
    )

    fun showFailData(adFormat: String, placement: String, reason: String, isAction: Int, screenName: String): Map<String, Any> =
        mapOf(
            "ad_format" to adFormat,
            "placement" to placement,
            "reason" to reason,
            "is_action" to isAction,
            "screen_name" to screenName
        )

    fun clickData(adFormat: String, placement: String, adUnit: String): Map<String, Any> =
        mapOf("ad_format" to adFormat, "placement" to placement, "ad_unit" to adUnit)

    /** [valueMicros] nguyên bản của mạng (không quy đổi); [precisionType] GMA 0–3, `-1` = không có. */
    fun revenueData(
        adFormat: String, mediation: String, adSource: String, adUnit: String, placement: String, valueMicros: Long,
        currency: String, precisionType: Int, impId: String, screenName: String
    ): Map<String, Any> = mapOf(
        "ad_format" to adFormat,
        "mediation" to mediation,
        "ad_source" to adSource,
        "ad_unit" to adUnit,
        "placement" to placement,
        "value_micros" to valueMicros,
        "currency" to currency,
        "precision_type" to precisionType,
        "imp_id" to impId,
        "screen_name" to screenName
    )

    /** [gdprApplies] 0/1/-1 (không rõ); [formKind] `initial`\|`privacy_options`. */
    fun consentFormShownData(gdprApplies: Int, formKind: String): Map<String, Any> =
        mapOf("gdpr_applies" to gdprApplies, "form_kind" to formKind)

    /** [gppSections] sid nối `_`, rỗng nếu không; [latencyMs] tính từ `consent_form_shown`. */
    fun consentChoiceData(tier: String, gdprApplies: Int, gppSections: String, latencyMs: Long, formKind: String): Map<String, Any> =
        mapOf(
            "tier" to tier,
            "gdpr_applies" to gdprApplies,
            "gpp_sections" to gppSections,
            "latency_ms" to latencyMs,
            "form_kind" to formKind
        )

    /** [adsShownJson] chuỗi JSON `{"interstitial":n,…}`; [screens] số Activity start; [fgIndex] lần foreground trong process. */
    fun sessionEndData(durationMs: Long, adsShownJson: String, screens: Int, fgIndex: Int): Map<String, Any> = mapOf(
        "duration_ms" to durationMs,
        "ads_shown_json" to adsShownJson,
        "screens" to screens,
        "fg_index" to fgIndex
    )

    // ── Gửi ──────────────────────────────────────────────────────────────────────────────────────

    private fun send(eventName: String, eventData: Map<String, Any>) = LogEventTransport.sendEvent(eventName, eventData, null)

    fun request(r: LoadRequest, slot: String) = send(EventNames.AD_REQUEST, requestEventData(r, slot))

    /** 2.3.0 PRELOAD: slot → hạn (ms) của lượt nạp sẵn (`AdPreloader`); `ad_fill` của slot đóng lượt, quá 60 s thì tự hết. */
    private val preloadSlots = java.util.concurrent.ConcurrentHashMap<String, Long>()
    internal fun markPreload(slot: String) { preloadSlots[slot] = System.currentTimeMillis() + 60_000L }
    internal fun clearPreload(slot: String) { preloadSlots.remove(slot) }
    @JvmSynthetic internal fun resetPreloadForTest() = preloadSlots.clear()

    /** `ad_request` của lượt nạp sẵn mang thêm `trigger: "preload"` (param map như `slot`, không đổi [LoadRequest]). */
    internal fun requestEventData(r: LoadRequest, slot: String): Map<String, Any> =
        requestData(r, slot) + if ((preloadSlots[slot] ?: 0L) > System.currentTimeMillis()) mapOf("trigger" to "preload") else emptyMap()

    fun fill(r: LoadRequest, slot: String, latencyMs: Long, adSource: String) =
        send(EventNames.AD_FILL, fillData(r, slot, latencyMs, adSource)).also { preloadSlots.remove(slot) }

    fun noFill(r: LoadRequest, slot: String, latencyMs: Long, errorCode: String, reason: String) =
        send(EventNames.AD_NO_FILL, noFillData(r, slot, latencyMs, errorCode, reason))

    fun request(r: LoadRequest) = request(r, r.placement)

    fun fill(r: LoadRequest, latencyMs: Long, adSource: String) = fill(r, r.placement, latencyMs, adSource)

    fun noFill(r: LoadRequest, latencyMs: Long, errorCode: String, reason: String) = noFill(r, r.placement, latencyMs, errorCode, reason)

    fun showAttempt(adFormat: String, placement: String, ready: Int, screenName: String) =
        send(EventNames.AD_SHOW_ATTEMPT, showAttemptData(adFormat, placement, ready, screenName))

    fun show(adFormat: String, mediation: String, adSource: String, placement: String, adUnit: String, impId: String) {
        SessionTracker.onAdShown(adFormat)
        send(EventNames.AD_SHOW, showData(adFormat, mediation, adSource, placement, adUnit, impId))
    }

    fun showFail(adFormat: String, placement: String, reason: String, isAction: Int, screenName: String) =
        send(EventNames.AD_SHOW_FAIL, showFailData(adFormat, placement, reason, isAction, screenName))

    fun click(adFormat: String, placement: String, adUnit: String) = send(EventNames.AD_CLICK, clickData(adFormat, placement, adUnit))

    /** Cũng là impression có giá trị của segment (2.2.0 SEG `SegmentHooks.onAdImpression`; fork: paid event AdMob). */
    fun revenue(
        adFormat: String, mediation: String, adSource: String, adUnit: String, placement: String, valueMicros: Long,
        currency: String, precisionType: Int, impId: String, screenName: String
    ) {
        send(
            EventNames.AD_REVENUE,
            revenueData(adFormat, mediation, adSource, adUnit, placement, valueMicros, currency, precisionType, impId, screenName)
        )
        SegmentHooks.onAdImpression(adFormat, valueMicros)
    }

    fun consentFormShown(gdprApplies: Int, formKind: String) =
        send(EventNames.CONSENT_FORM_SHOWN, consentFormShownData(gdprApplies, formKind))

    fun consentChoice(tier: String, gdprApplies: Int, gppSections: String, latencyMs: Long, formKind: String) =
        send(EventNames.CONSENT_CHOICE, consentChoiceData(tier, gdprApplies, gppSections, latencyMs, formKind))

    fun sessionEnd(durationMs: Long, adsShownJson: String, screens: Int, fgIndex: Int) =
        send(EventNames.SESSION_END, sessionEndData(durationMs, adsShownJson, screens, fgIndex))

    // ── Timeout của watchdog slot ────────────────────────────────────────────────────────────────

    /**
     * Nơi nhận timeout của watchdog slot: `AdsTelemetry.onLoadTimeout` của helper đang mở request (gắn ở `onAdLoadStart`; trường
     * `telemetry` của `AdsHelper` là private và `AdsHelper.kt` không được thêm dòng — plan 1.7.0 Task 1).
     */
    @Volatile
    internal var loadTimeoutSink: ((originPl: String, adIds: List<String>) -> Unit)? = null

    /** `AdsPlacementEcmpBase.onLoadTimeout`: slot [originPl] bỏ các request tới [adIds] vì mạng không callback. */
    fun slotLoadTimeout(originPl: String, adIds: List<String>) {
        if (adIds.isNotEmpty()) loadTimeoutSink?.invoke(originPl, adIds)
    }
}
