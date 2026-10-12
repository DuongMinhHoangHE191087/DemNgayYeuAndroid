package com.app.plugin.ads.adsmob

import android.content.Context
import android.util.Log
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.MainFront
import com.app.plugin.ads.SdkReadyQueue
import com.app.plugin.logevent.AdmobSDKInitState
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.initialization.AdapterStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

public class AdsMobMy {
    public var mContext: Context? = null
    public var bannerAds: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
    public var ntClAds: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
    public var rectAds: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
    public var nativeAds: MutableMap<Int, AdsBaseNative> = mutableMapOf()
    public var fullAds: MutableMap<Int, AdsBaseFull> = mutableMapOf()
    public var giftAds: MutableMap<Int, AdsBaseGift> = mutableMapOf()

    public companion object {
        public val instance: AdsMobMy by lazy { AdsMobMy() }

        // Quá thời gian này mà MobileAds.initialize() vẫn chưa xong (mất mạng, lỗi...)
        // thì vẫn cho các request đang chờ chạy tiếp, tránh treo quảng cáo vĩnh viễn.
        private const val SDK_INIT_TIMEOUT_MS = 8000L
    }

    private val readyQueue = SdkReadyQueue()

    /** MobileAds đã khởi tạo xong (hoặc đã quá [SDK_INIT_TIMEOUT_MS] kể từ lúc bắt đầu). */
    public val isSdkInitialized: Boolean
        get() = readyQueue.isReady

    /**
     * Chạy [action] ngay nếu AdMob SDK đã init xong (đảm bảo request có mediation),
     * nếu chưa thì xếp hàng để chạy ngay khi init xong (hoặc sau timeout).
     */
    public fun runWhenSdkReady(action: () -> Unit) {
        readyQueue.runWhenReady(action)
    }

    /**
     * Yêu cầu khởi tạo mọi mạng quảng cáo: MobileAds (kéo theo adapter mediation), Meta Audience
     * Network và AppLovin MAX. Gọi lúc nào cũng được, gọi lại là no-op: SDK tự hoãn tới khi UMP
     * cho phép (xem `AdSdkStartGate`). Người dùng đã trả lời consent ở phiên trước thì khởi tạo
     * ngay.
     *
     * Trước 1.3.0 `AdsHelper.initAdsHelper()` tự khởi tạo cả ba mạng trong
     * `AppPluginBase.onCreate`, tức trước consent. Nay không gọi hàm này thì không mạng nào khởi
     * tạo và không quảng cáo nào được tải.
     */
    public fun startSdkInit(context: Context) {
        AdSdkStartup.requestAdNetworks(context)
    }

    /** Dựng các đối tượng quảng cáo. Không khởi tạo MobileAds: việc đó là của [startNetwork]. */
    public fun initAd(context: Context) {
        mContext = context
        // Bật log ngay, để bản test thấy cả những dòng trước consent ("waiting for consent").
        if (AppUtil.isTestFlagOn(context)) LogUtil.setFlagLog(3)
        var adbn = AdsMobBanner(AdsIDDef.AdmobBanner, context)
        bannerAds[AdsIDDef.AdmobBanner] = adbn

        var ntcl = AdsMobNtCl(AdsIDDef.AdmobNtCl, context)
        ntClAds[AdsIDDef.AdmobNtCl] = ntcl

        var rect = AdsMobRect(AdsIDDef.AdmobRect, context)
        rectAds[AdsIDDef.AdmobRect] = rect

        var adnt = AdsMobNative(AdsIDDef.AdmobNative, context)
        nativeAds[AdsIDDef.AdmobNative] = adnt

        var adInters = AdsMobFull(AdsIDDef.AdmobFullInters, context)
        fullAds[AdsIDDef.AdmobFullInters] = adInters
        var adIntersLow = AdsMobFull(AdsIDDef.AdmobFullLow, context)
        fullAds[AdsIDDef.AdmobFullLow] = adIntersLow
        var adOpen = AdsMobFull(AdsIDDef.AdmobFullOpen, context)
        fullAds[AdsIDDef.AdmobFullOpen] = adOpen
        var adNtFull = AdsMobFull(AdsIDDef.AdmobFullNt, context)
        fullAds[AdsIDDef.AdmobFullNt] = adNtFull

        var adRw = AdsMobGift(AdsIDDef.AdmobRewarded, context)
        giftAds[AdsIDDef.AdmobRewarded] = adRw
        // 1.9.0 Task 2: rewarded interstitial (placement `rwi_`), chỉ RewardedInterstitialController dùng.
        giftAds[AdsIDDef.AdmobRwi] = AdsMobRwi(AdsIDDef.AdmobRwi, context)
    }

    /**
     * Chỉ `AdSdkStartup` gọi, đúng một lần, khi consent đã cho phép. Load đang chờ chạy trên main
     * thread, như Meta và MAX: chúng dựng view quảng cáo.
     */
    internal fun startNetwork(context: Context) {
        val appContext = context.applicationContext ?: context
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        // Đọc cờ test ngay trên luồng gọi để MỘT RequestConfiguration dưới đây mang đủ mọi
        // trường. Bản trước gọi setRequestConfiguration() hai lần trên hai luồng: lần trong
        // coroutine IO (rating, cờ trẻ em, cờ tuổi đồng ý) và lần trên luồng gọi (chỉ test
        // device, dựng từ Builder() mới) — lần nào chạy sau thì xoá trường của lần kia. Hai
        // app fork đã gặp cả hai chiều trên máy thật: phiên hiện "Test Ad", và phiên mà cả
        // 110 request mang gợi ý "use setTestDeviceIds", tức bản debug ăn quảng cáo thật.
        val testDeviceIds = if (AppUtil.isTestFlagOn(appContext)) {
            AppUtil.getResourceIds(appContext, "admob_test_device")
        } else {
            emptyList()
        }
        val startedAt = System.currentTimeMillis()
        // 2.4.3 FASTSTART-C: resource app `ads_cf_admob_init_wait_ms` (vắng/0 = tắt: chờ callback như cũ) thả hàng load đang
        // giữ N ms sau khi initialize() đã được gọi — sau setRequestConfiguration, nên test device áp trước mọi request.
        // Callback init và hẹn 8 s dưới đây giữ nguyên; markReady idempotent. Chạy trên luồng IO ở cuối coroutine; là
        // lambda thường (không nằm trong khối suspend) để gọi hàm private mà không sinh `access$` vào dump API.
        val earlyRelease = {
            earlyReleaseDelayMs(AppUtil.getResourceInt(appContext, "ads_cf_admob_init_wait_ms"))?.let { n ->
                main.postDelayed({
                    if (!readyQueue.isReady) {
                        Log.i(LogUtil.TAG, "ads admob initialize: early release after $n ms (ads_cf_admob_init_wait_ms)")
                    }
                    readyQueue.markReady()
                }, n)
            }
            Unit
        }
        val backgroundScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        backgroundScope.launch {
            // Đối tượng trẻ em (COPPA) và người dưới tuổi đồng ý (GDPR) là TUYÊN BỐ PHÁP LÝ
            // của studio, không phải giá trị mặc định kỹ thuật của một thư viện.
            //
            // Bản trước hardcode TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE và
            // TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE — tức SDK tự khẳng định thay studio rằng app
            // của họ không hướng tới trẻ em. Nay chỉ đặt khi studio đã khai báo tường minh;
            // chưa khai thì KHÔNG đụng vào, để AdMob dùng mặc định của chính nó.
            val builder = MobileAds.getRequestConfiguration().toBuilder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_MA)
                .setTestDeviceIds(testDeviceIds)
            com.app.plugin.consent.ConsentState.childDirected?.let {
                builder.setTagForChildDirectedTreatment(
                    if (it) RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                    else RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
                )
            }
            com.app.plugin.consent.ConsentState.underAgeOfConsent?.let {
                builder.setTagForUnderAgeOfConsent(
                    if (it) RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                    else RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
                )
            }
            MobileAds.setRequestConfiguration(builder.build())

            // Initialize the Google Mobile Ads SDK on a background thread.
            MobileAds.initialize(appContext) { statusInit ->
                AdmobSDKInitState.isInitComplete = 1
                LogEventServer.loadSDKInitTime()
                val statusMap = statusInit.adapterStatusMap
                for ((adapterClass, status) in statusMap) {
                    val isReady = if (status.initializationState == AdapterStatus.State.READY) 1 else 0
                    val latency = status.latency.toLong()
                    LogUtil.logD("ads admob adapter ${AppUtil.extractAdNetwork(adapterClass)}: " +
                        "${status.initializationState} ${latency}ms ${status.description}")
                    LogEventServer.logAdAdapterInit(
                        adPlatform = "admob",
                        adapterClass = AppUtil.extractAdNetwork(adapterClass),
                        isReady = isReady,
                        latency = latency,
                        screenName = ScreenName.name
                    )
                }
                LogUtil.logD("ads admob initialize: complete after ${System.currentTimeMillis() - startedAt} ms")
                // 2.4.4 INITCB: GMA gọi callback này trên main ⇒ nhả hàng tại chỗ, không xếp cuối hàng main (chờ khung hình).
                MainFront.run { readyQueue.markReady() }
            }
            earlyRelease()
        }
        // An toàn: nếu initialize() không kịp hoàn tất (mất mạng, lỗi SDK...) trong
        // SDK_INIT_TIMEOUT_MS thì vẫn giải phóng các request đang chờ, tránh treo quảng cáo.
        // Tính từ lúc bắt đầu khởi tạo, không từ initAd(): request đang chờ consent không được
        // chạy trước khi MobileAds khởi tạo.
        main.postDelayed({
            if (!readyQueue.isReady) {
                LogUtil.logD("ads admob initialize: not complete after $SDK_INIT_TIMEOUT_MS ms, releasing held loads")
            }
            readyQueue.markReady()
        }, SDK_INIT_TIMEOUT_MS)
    }

    /** FASTSTART-C: giá trị resource → ms thả sớm; ≤ 0 ⇒ `null` (tắt), kẹp 1..[SDK_INIT_TIMEOUT_MS]. */
    private fun earlyReleaseDelayMs(raw: Int): Long? = if (raw <= 0) null else raw.toLong().coerceAtMost(SDK_INIT_TIMEOUT_MS)


    public fun getAdsBanner(type: Int): AdsBaseBanner? {
        if (bannerAds.containsKey(type)) {
            return bannerAds[type]
        }
        return null
    }

    public fun getAdsNtCl(type: Int): AdsBaseBanner? {
        if (ntClAds.containsKey(type)) {
            return ntClAds[type]
        }
        return null
    }

    public fun getAdsRect(type: Int): AdsBaseBanner? {
        if (rectAds.containsKey(type)) {
            return rectAds[type]
        }
        return null
    }

    public fun getAdsNative(type: Int): AdsBaseNative? {
        if (nativeAds.containsKey(type)) {
            return nativeAds[type]
        }
        return null
    }

    public fun getAdsFull(type: Int): AdsBaseFull? {
        if (fullAds.containsKey(type)) {
            return fullAds[type]
        }
        return null
    }

    public fun getAdsGift(type: Int): AdsBaseGift? {
        if (giftAds.containsKey(type)) {
            return giftAds[type]
        }
        return null
    }

    /** Nhãn mạng của lớp adapter (`source_null` khi không có): một mapper, [AppUtil.extractAdNetwork] (1.9.0 T1, D11). */
    internal fun getAdnetName(responseInfo: ResponseInfo?): String =
        AppUtil.extractAdNetwork(responseInfo?.mediationAdapterClassName ?: "source_null")
}
