package com.app.plugin.firbase

import com.app.plugin.ads.AdRevenueRouting
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.MaxBridge
import com.app.plugin.appsflyer.AppsflyerHelper
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import java.util.UUID
import com.google.android.libraries.ads.mobile.sdk.common.AdValue as NgAdValue

/**
 * Một paid callback của mạng quảng cáo → mọi đích doanh thu (1.7.0 Task 3, DOC/62 §2.3 "doanh thu nội bộ và AppsFlyer
 * lấy từ cùng một callback"). Mỗi lượt sinh **một** `imp_id` (khoá đối soát H6a) dùng chung cho, theo thứ tự:
 * Firebase `ad_impression` (param `imp_id`, `ad_placement`; AdMob chỉ khi [AdRevenueRouting] cho phép), tROAS, AppsFlyer (dòng
 * `rev af: imp=… started=0|1`), event hiển thị Firebase của [AdsHelper.onAdImpression] (`aad_imp_*`, `show_ads_*`),
 * rồi server `ad_show` và `ad_revenue` cùng `imp_id` (placement = placement gọi show, lùi về slot khi rỗng).
 * Server `ad_show` chỉ phát ở đây (không còn từ `AdsTelemetry`).
 *
 * `value_micros` là Long nguyên bản của mạng (AdMob `AdValue.valueMicros`, không qua double); `precision_type` đi thẳng
 * từ `AdValue.precisionType` (GMA 0–3), [PRECISION_NONE] khi mạng không cho biết (MAX, Meta direct, đường v1
 * `FirHelper.logAdRev*`). Meta direct không có doanh thu thật: `value_micros` 0, `currency` rỗng, không gửi Firebase
 * `ad_impression`, tROAS hay AppsFlyer (như v1) — không đếm đôi.
 *
 * Chạy trên luồng callback của mạng (main); event server dựng và gửi trên IO như mọi event.
 *
 * 2.4.0 NG3 (Hợp đồng B, [S24]–[S26]): GMA Next-Gen báo `onAdPaid(AdValue)` với `precisionType` là enum → Int 0–3 như Legacy
 * ([precision]); `ad_network` từ `ResponseInfo.getAdapterClassName()` qua cùng bộ ánh xạ với `AdsMobMy.getAdnetName`
 * ([adNetwork]); mã show-fail = mã của `mediationAdError` nếu có ([showFailCode]). NG5b: overload `AdValue` Legacy đã xoá
 * (banner/rect NG2 dùng overload Next-Gen).
 */
internal object PaidEvent {
    /** `precision_type` khi mạng không cho biết. */
    const val PRECISION_NONE = -1
    private const val MICROS = 1_000_000.0
    private const val ADMOB = "admob"
    private const val APPLOVIN = "applovin"
    private const val FACEBOOK = "facebook"
    private const val AF_ADMOB = 0
    private const val AF_MAX = 1
    private const val IMP_ID = "imp_id"
    private const val AD_PLACEMENT = "ad_placement"
    private const val PARAM_VALUE_MAX = 100

    /** Doanh thu tính bằng đơn vị tiền (v1) → micros, làm tròn (không cắt). */
    fun micros(value: Double): Long = Math.round(value * MICROS)

    /** `onAdPaid` của GMA Next-Gen [S24]: `valueMicros` Long nguyên bản, currency, precision enum → Int ([precision]). */
    fun admob(showPl: String, originPl: String, format: String, adUnit: String, netName: String, adValue: NgAdValue): String =
        admob(showPl, originPl, format, adUnit, netName, adValue.valueMicros, adValue.currencyCode, precision(adValue.precisionType))

    /** `precision_type` như GMA Legacy: UNKNOWN 0, ESTIMATED 1, PUBLISHER_PROVIDED 2, PRECISE 3 (không dựa vào `ordinal`). */
    fun precision(type: PrecisionType?): Int = when (type) {
        PrecisionType.UNKNOWN -> 0
        PrecisionType.ESTIMATED -> 1
        PrecisionType.PUBLISHER_PROVIDED -> 2
        PrecisionType.PRECISE -> 3
        null -> PRECISION_NONE
    }

    /** Nhãn mạng của adapter đã nạp (`getAdapterClassName()` [S25]); cùng mapper với `AdsMobMy.getAdnetName` (Legacy). */
    fun adNetwork(info: ResponseInfo?): String = AppUtil.extractAdNetwork(info?.adapterClassName ?: "source_null")

    /** Mã lỗi show của Next-Gen: mã của adapter mediation nếu có ([S26]), không thì mã lỗi SDK. */
    fun showFailCode(error: FullScreenContentError): String = (error.mediationAdError?.code ?: error.code.value).toString()

    fun admob(
        showPl: String, originPl: String, format: String, adUnit: String, netName: String,
        valueMicros: Long, currency: String, precisionType: Int
    ): String = emit(
        ADMOB, AF_ADMOB, showPl, originPl, format, adUnit, AppUtil.extractAdNetwork(netName), valueMicros, currency, precisionType
    )

    /** Revenue listener của MAX: [revenue] tính bằng [currency] (MAX báo USD); MAX không có `precisionType` kiểu GMA. */
    fun max(showPl: String, originPl: String, format: String, adUnit: String, netName: String, revenue: Double, currency: String): String =
        emit(
            APPLOVIN, AF_MAX, showPl, originPl, format, adUnit, (MaxBridge.get()?.getAdnetName(netName) ?: netName), micros(revenue), currency,
            PRECISION_NONE
        )

    /** Impression của Meta Audience Network gọi thẳng (không qua mediation): không có doanh thu. */
    fun metaDirect(showPl: String, originPl: String, format: String, adUnit: String, netName: String): String =
        emit(FACEBOOK, null, showPl, originPl, format, adUnit, AppUtil.extractAdNetwork(netName), 0L, "", PRECISION_NONE)

    /** [afMediation] `null` = không có doanh thu để gửi (Meta direct): bỏ Firebase `ad_impression`, tROAS, AppsFlyer. */
    private fun emit(
        platform: String, afMediation: Int?, showPl: String, originPl: String, format: String, adUnit: String, adSource: String,
        valueMicros: Long, currency: String, precisionType: Int
    ): String {
        val impId = UUID.randomUUID().toString()
        val value = valueMicros / MICROS
        // Placement gọi show (như `ad_show_attempt`/`ad_show_fail`), không phải slot mạng: slot dùng chung (full_home, full_2_*
        // → full_default) phải ra đúng placement; chưa có (paid event trước lượt show) thì lùi về slot (1.7.0 T3b). Một biến cho
        // mọi đích: Firebase `ad_placement` (2.5.1 PLACEMENT) = server `placement`.
        val placement = showPl.ifEmpty { originPl }
        if (afMediation != null) {
            if (AdRevenueRouting.shouldLogFirebaseAdImpression(platform)) {
                logFirebaseImpression(platform, adUnit, format, adSource, value, currency, impId, placement)
            } else if (PlacementPaidEvent.enabled) {
                PlacementPaidEvent.log(platform, adUnit, format, adSource, value, currency, impId, placement)
            }
            FirHelper.instance.logAdTroas(value)
            AppsflyerHelper.instance.logAdRevenue(afMediation, adSource, adUnit, format, value, currency, impId)
        }
        AdsHelper.instance.onAdImpression(showPl, originPl, format, platform, adUnit, adSource)
        val mediation = AdEventLog.mediationOf(platform)
        AdEventLog.show(format, mediation, adSource, placement, adUnit, impId)
        AdEventLog.revenue(format, mediation, adSource, adUnit, placement, valueMicros, currency, precisionType, impId, ScreenName.name)
        return impId
    }

    private fun logFirebaseImpression(
        platform: String, adUnit: String, format: String, adSource: String, value: Double, currency: String, impId: String,
        placement: String,
    ) {
        if (!FirebasePresence.available()) return
        try {
            Firebase.analytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION) {
                param(FirebaseAnalytics.Param.AD_PLATFORM, platform)
                param(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnit)
                param(FirebaseAnalytics.Param.AD_FORMAT, format)
                param(FirebaseAnalytics.Param.AD_SOURCE, adSource)
                param(FirebaseAnalytics.Param.VALUE, value)
                param(FirebaseAnalytics.Param.CURRENCY, currency)
                param(IMP_ID, impId)
                // Firebase cắt giá trị chuỗi > 100 ký tự; rỗng (cả showPl lẫn slot rỗng) thì không đặt.
                if (placement.isNotEmpty()) param(AD_PLACEMENT, placement.take(PARAM_VALUE_MAX))
            }
        } catch (e: Exception) {
            LogUtil.logE("PaidEvent ad_impression $platform failed: ${e.message}")
        }
    }
}
