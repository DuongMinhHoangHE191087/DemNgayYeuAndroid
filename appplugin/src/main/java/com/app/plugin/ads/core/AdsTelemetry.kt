package com.app.plugin.ads.core

import com.app.plugin.ads.AdsHelper
import com.app.plugin.firbase.FirHelper
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.AdStatus
import com.app.plugin.logevent.AdTracker
import com.app.plugin.logevent.AdmobSDKInitState
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil

/**
 * Vùng telemetry load/impression của [AdsHelper] (1.5.0 Task 1, G8): chuỗi load lên server, event `aad_*`/`show_ads_*`
 * lên Firebase, [AdTracker]. Móc Full2 của `onAdLoaded` (`native_full` đến khi đang chuyển cảnh → `prepareChainedFull2`)
 * ở lại façade, chạy trước hàm ở đây.
 *
 * Chuỗi load v2 (1.7.0 Task 1): mỗi request mở một mục ([OpenLoad]: mốc bắt đầu + `request_id`), `ad_fill`/`ad_no_fill`
 * đóng nó với cùng `request_id`; response không có mốc bắt đầu phát với `latency_ms = -1`, `request_id` rỗng. Watchdog
 * slot đóng request bằng `ad_no_fill(error_code = timeout)` ([onLoadTimeout]) và giữ dấu để response tới muộn của request
 * đó không phát thêm. Mỗi request đóng đúng một lần, kể cả khi callback tới từ hai luồng (1.8.0 Task 15, R-1): mục của
 * khoá chuyển sang dấu `timedOut = true` bằng CAS của `ConcurrentHashMap`, luồng thua không phát. Khoá chưa từng
 * mở (response không có start) phát một lần rồi mang dấu `timedOut`. Firebase `aad_*` giữ nguyên.
 *
 * [h] chưa dùng: giữ cho đồng dạng với các controller khác (plan 1.5.0, mẫu controller).
 */
internal class AdsTelemetry(private val h: AdsHelper) {
    private val maxEventName = 40
    private class OpenLoad(val startMs: Long, val request: AdEventLog.LoadRequest, val timedOut: Boolean = false)

    private val map4CheckLoadAd: java.util.concurrent.ConcurrentHashMap<String, OpenLoad> = java.util.concurrent.ConcurrentHashMap()

    /** Gắn vào [AdEventLog.loadTimeoutSink] mỗi lần mở request (không `init {}`: luật R-2 của controller). */
    private val timeoutSink: (String, List<String>) -> Unit = ::onLoadTimeout

    private fun loadRequest(originPl: String, adFormat: String, adPlatform: String, adId: String, requestId: String) =
        AdEventLog.LoadRequest(
            adFormat = adFormat,
            mediation = AdEventLog.mediationOf(adPlatform),
            placement = originPl,
            adUnit = adId,
            requestId = requestId,
            screenName = ScreenName.name,
            isSdkInit = AdmobSDKInitState.isInitComplete
        )

    fun onAdLoadStart(
        placement: String, originPl: String, adFormat: String, adPlatform: String, adId: String
    ) {
        LogUtil.logD("AdsHelper onAdLoadStart: pl=$placement opl=$originPl format=$adFormat platform=$adPlatform id=$adId")
        val key = "${placement}_${originPl}_${adFormat}_${adPlatform}_${adId}"
        val request = loadRequest(originPl, adFormat, adPlatform, adId, AdEventLog.newRequestId())
        map4CheckLoadAd[key] = OpenLoad(System.currentTimeMillis(), request)
        AdEventLog.loadTimeoutSink = timeoutSink
        val eventName = adEventName("aad_lst_", placement, adPlatform, adFormat)
        FirHelper.instance.logEvent(eventName)

        AdEventLog.request(request, originPl)

        AdTracker.updateStatus(originPl, AdStatus.Loading)
        UnitGate.onRequest(originPl, adId) // 2.4.4 INFLIGHT: đặt chỗ theo id thật đang bay
    }

    /**
     * Đóng request của [key] đúng một lần: `null` khi đã có luồng khác đóng nó hoặc watchdog đã đóng (response muộn; dấu ở lại
     * tới request kế cùng khoá), không thì request đã mở và `latency_ms`, hoặc request không mốc bắt đầu (`request_id` rỗng,
     * `latency_ms` `-1`; response tới mà không có [onAdLoadStart]). Thắng CAS mới được phát; mục đã đóng ở lại làm dấu (chỉ
     * `timedOut = true` mang nghĩa "đã đóng, không phát nữa": watchdog hoặc response), số dấu bị chặn bởi số khoá.
     */
    private fun close(key: String, originPl: String, adFormat: String, adPlatform: String, adId: String): Pair<AdEventLog.LoadRequest, Long>? {
        val open = map4CheckLoadAd[key]
        if (open == null) {
            val never = OpenLoad(0L, loadRequest(originPl, adFormat, adPlatform, adId, ""), timedOut = true)
            return if (map4CheckLoadAd.putIfAbsent(key, never) == null) never.request to -1L else null
        }
        if (open.timedOut || !map4CheckLoadAd.replace(key, open, OpenLoad(open.startMs, open.request, timedOut = true))) return null
        return open.request to (System.currentTimeMillis() - open.startMs)
    }

    fun onAdLoaded(
        placement: String,
        originPl: String,
        adFormat: String,
        adPlatform: String,
        adId: String,
        adNet: String
    ) {
        val key: String = "${placement}_${originPl}_${adFormat}_${adPlatform}_${adId}"
        val closed = close(key, originPl, adFormat, adPlatform, adId)
        LogUtil.logD("AdsHelper onAdLoaded: pl=${placement} opl=${originPl} format=${adFormat} platform=${adPlatform} id=${adId} net=${adNet} t=${closed?.second}")
        val eventName = adEventName("aad_led_", placement, adPlatform, adFormat)
        FirHelper.instance.logEvent(eventName)

        if (closed != null) AdEventLog.fill(closed.first, closed.first.placement, closed.second, AppUtil.extractAdNetwork(adNet))

        AdTracker.updateStatus(originPl, AdStatus.Loaded)
        UnitGate.onResult(originPl, placement, adId, ok = true) // n = 0
    }

    fun onAdLoadFail(
        placement: String,
        originPl: String,
        adFormat: String,
        adPlatform: String,
        adId: String,
        error: String
    ) {
        val key: String = "${placement}_${originPl}_${adFormat}_${adPlatform}_${adId}"
        val closed = close(key, originPl, adFormat, adPlatform, adId)
        LogUtil.logD("AdsHelper onAdLoadFail: pl=${placement} opl=${originPl} format=${adFormat} platform=${adPlatform} id=${adId} err=${error} t=${closed?.second}")
        val eventName = adEventName("aad_lfa_", placement, adPlatform, adFormat)
        FirHelper.instance.logEvent(eventName)

        if (closed != null) AdEventLog.noFill(closed.first, closed.first.placement, closed.second, error, error)

        AdTracker.updateStatus(originPl, AdStatus.Failed)
        UnitGate.onResult(originPl, placement, adId, ok = false) // n++, backoff 2–16 s
    }

    /**
     * Watchdog slot (`AdsPlacementEcmpBase.onLoadTimeout`, qua [AdEventLog.slotLoadTimeout]): đóng mọi request đang mở
     * của [originPl] tới [adIds] bằng `ad_no_fill(error_code = timeout)`, đúng một lần; dấu `timedOut` ở lại tới response
     * muộn (nếu có) để nó không phát `ad_fill`/`ad_no_fill` thứ hai. Không thêm event Firebase.
     */
    internal fun onLoadTimeout(originPl: String, adIds: List<String>) {
        map4CheckLoadAd.forEach { (key, open) ->
            if (open.timedOut || open.request.placement != originPl || open.request.adUnit !in adIds) return@forEach
            if (!map4CheckLoadAd.replace(key, open, OpenLoad(open.startMs, open.request, timedOut = true))) return@forEach
            AdEventLog.noFill(open.request, open.request.placement, System.currentTimeMillis() - open.startMs, AdEventLog.TIMEOUT, "load timeout")
            AdTracker.updateStatus(originPl, AdStatus.Failed)
        }
    }

    /**
     * Event hiển thị lên Firebase (`aad_imp_*`, `show_ads_*`). Server `ad_show` không phát ở đây mà ở `PaidEvent` (1.7.0
     * Task 3), cùng `imp_id` với `ad_revenue` của cùng paid callback.
     */
    fun onAdImpression(
        placement: String,
        originPl: String,
        adFormat: String,
        adPlatform: String,
        adId: String,
        adNet: String
    ) {
        LogUtil.logD("AdsHelper onAdImpression: pl=${placement} opl=${originPl} format=${adFormat} platform=${adPlatform} id=${adId} net=${adNet}")
        val eventName = adEventName("aad_imp_", placement, adPlatform, adFormat)
        FirHelper.instance.logEvent(eventName)

        val eventAds = "show_ads_${getAdFormat(adFormat)}"
        FirHelper.instance.logEvent(eventAds)
        FirHelper.instance.logEvent(EventNames.SHOW_ADS_TOTAL)
    }

    /** Tên rút gọn của `show_ads_*`; `rewarded_interstitial` (1.9.0 Task 2) xét trước `rewarded`/`interstitial` → `rwi`. */
    internal fun getAdFormat(adFormat: String): String {
        return if (adFormat == "rewarded_interstitial") {
            "rwi"
        } else if (adFormat.contains("banner") || adFormat.contains("rect")) {
            "banner"
        } else if (adFormat.contains("native")) {
            "native"
        } else if (adFormat.contains("opened")) {
            "open"
        } else if (adFormat.contains("rewarded")) {
            "reward"
        } else if (adFormat.contains("interstitial")) {
            "full"
        } else {
            "unknown_$adFormat"
        }
    }

    /** ROB-4 (2.3.0): tên event Firebase luôn <= 40 ký tự (FA bỏ tên dài hơn): cắt phần placement, giữ [prefix] và hậu tố platform/format. */
    private fun adEventName(prefix: String, placement: String, adPlatform: String, adFormat: String): String {
        val full = shortenNameEventAd("$prefix${placement}_${adPlatform}_${adFormat}")
        if (full.length <= maxEventName) return full
        val tail = shortenNameEventAd("_${adPlatform}_${adFormat}")
        val budget = (maxEventName - prefix.length - tail.length).coerceAtLeast(0)
        return prefix + shortenNameEventAd(placement).take(budget).trimEnd('_') + tail
    }

    private fun shortenNameEventAd(name: String): String {
        var nameShort = name.replace("_interstitial", "_int")
        nameShort = nameShort.replace("_native_full", "_ntfu")
        nameShort = nameShort.replace("_native_collapse", "_ntcl")
        nameShort = nameShort.replace("_native_custom", "_ntct")
        nameShort = nameShort.replace("_banner", "_bn")
        nameShort = nameShort.replace("_admob", "_gg")
        nameShort = nameShort.replace("_facebook", "_fb")
        nameShort = nameShort.replace("_applovin", "_max")

        return nameShort
    }
}
