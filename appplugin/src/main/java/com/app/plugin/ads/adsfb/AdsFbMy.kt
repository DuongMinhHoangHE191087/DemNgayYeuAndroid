package com.app.plugin.ads.adsfb

import android.content.Context
import android.util.Log
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.SdkReadyQueue
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.GppConsent
import com.app.plugin.consent.GppSignals
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.facebook.ads.AdSettings
import com.facebook.ads.AudienceNetworkAds

internal class AdsFbMy {
    var mContext: Context? = null
    private var bannerAds: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
    private var ntClAds: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
    private var rectAds: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
    private var nativeAds: MutableMap<Int, AdsBaseNative> = mutableMapOf()
    private var fullAds: MutableMap<Int, AdsBaseFull> = mutableMapOf()
    private var giftAds: MutableMap<Int, AdsBaseGift> = mutableMapOf()

    var flagPp: String? = ""

    companion object {
        val instance: AdsFbMy by lazy { AdsFbMy() }
        var isShowGrid = 0

        /** Như AdMob: listener khởi tạo không về trong khoảng này thì vẫn cho load đang chờ chạy. */
        private const val SDK_INIT_TIMEOUT_MS = 8000L

        /** Limited Data Use: tuỳ chọn xử lý dữ liệu của Meta cho luật bang Mỹ. */
        private const val LDU = "LDU"
    }

    internal val readyQueue = SdkReadyQueue()

    /** Dựng các đối tượng quảng cáo. Không khởi tạo Audience Network: việc đó là của [startNetwork]. */
    fun initAd(context: Context) {
        mContext = context
        flagPp = AppUtil.getResourceString(context, "flagTest")
        isShowGrid = 0
        if (flagPp != null && (flagPp?.compareTo("1") == 0 || flagPp?.compareTo("90351") == 0)) {
            // Chế độ lỗi tích hợp làm crash app khi tích hợp sai: chỉ để lộ lỗi ở bản test.
            // Trước 1.3.0 nó chạy ở mọi bản build, kể cả bản phát hành (lấy từ Invoice Maker).
            AdSettings.setIntegrationErrorMode(AdSettings.IntegrationErrorMode.INTEGRATION_ERROR_CRASH_DEBUG_MODE)
            isShowGrid = 1
            if (flagPp?.compareTo("90351") == 0) {
                AdSettings.setDebugBuild(true)
                AdSettings.setTestMode(true)
                isShowGrid = 2
            }
            AppUtil.getResourceIds(context, "fb_test_device").forEach { AdSettings.addTestDevice(it) }
        }

        var adbn = AdsFbBanner(AdsIDDef.FBBanner, context)
        bannerAds[AdsIDDef.FBBanner] = adbn

        var ntcl = AdsFbNtCl(AdsIDDef.FBNtCl, context)
        ntClAds[AdsIDDef.FBNtCl] = ntcl

        var rect = AdsFbRect(AdsIDDef.FBRect, context)
        rectAds[AdsIDDef.FBRect] = rect

        var adnt = AdsFbNative(AdsIDDef.FBNative, context)
        nativeAds[AdsIDDef.FBNative] = adnt

        var adInters = AdsFbFull(AdsIDDef.FBFullInters, context)
        fullAds[AdsIDDef.FBFullInters] = adInters
        var adNtFull = AdsFbFull(AdsIDDef.FBFullNt, context)
        fullAds[AdsIDDef.FBFullNt] = adNtFull

        var adRw = AdsFbGift(AdsIDDef.FBRewarded, context)
        giftAds[AdsIDDef.FBRewarded] = adRw
    }

    /**
     * Chỉ `AdSdkStartup` gọi, đúng một lần, khi consent đã cho phép. Load đang chờ chạy trên main
     * thread: chúng dựng view quảng cáo.
     */
    internal fun startNetwork(context: Context) {
        val appContext = context.applicationContext ?: context
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        applyGpp(GppConsent.read(appContext))   // trước khởi tạo: Audience Network đọc tuỳ chọn lúc init
        networkStarted = true
        if (AudienceNetworkAds.isInitialized(appContext)) {
            main.post { readyQueue.markReady() }
            return
        }
        AudienceNetworkAds
            .buildInitSettings(appContext)
            .withInitListener { initResult ->
                LogUtil.logD("ads fb initialize: ${initResult.message}")
                main.post { readyQueue.markReady() }
            }
            .initialize()
        main.postDelayed({
            if (!readyQueue.isReady) {
                LogUtil.logD("ads fb initialize: not complete after $SDK_INIT_TIMEOUT_MS ms, releasing held loads")
            }
            readyQueue.markReady()
        }, SDK_INIT_TIMEOUT_MS)
    }

    /** [startNetwork] đã chạy: từ đây [followGpp] mới có việc. */
    @Volatile
    private var networkStarted = false

    /**
     * GPP (opt-out luật bang Mỹ) → Limited Data Use của Meta: [ConsentRules.usOptOut] thì
     * `AdSettings.setDataProcessingOptions(["LDU"], 0, 0)` (quốc gia/bang 0 = Meta tự định vị), có GPP mà không opt-out
     * thì `setDataProcessingOptions([])` (tắt LDU rõ ràng). Không có GPP ([gpp] `null`) thì **không gọi**. Không xét tầng
     * consent. Lỗi thì log, không ném.
     */
    private fun applyGpp(gpp: GppSignals?) {
        if (gpp == null) return
        try {
            if (ConsentRules.usOptOut(gpp)) {
                AdSettings.setDataProcessingOptions(arrayOf(LDU), 0, 0)
                Log.d(LogUtil.TAG, "ads fb: LDU on (${ConsentRules.gppLabel(gpp)})")
            } else {
                AdSettings.setDataProcessingOptions(arrayOf())
                Log.d(LogUtil.TAG, "ads fb: LDU off (${ConsentRules.gppLabel(gpp)})")
            }
        } catch (e: Throwable) {
            LogUtil.logE("ads fb: setDataProcessingOptions failed: ${e.message}")
        }
    }

    /**
     * Đánh giá lại GPP giữa phiên (`AdSdkStartup.applyTier`): `setDataProcessingOptions` gọi lại được sau khởi tạo.
     * Chưa [startNetwork] (bị gác vì consent, hoặc chưa tới cổng) thì không làm gì — lúc khởi tạo nó tự đọc GPP.
     */
    internal fun followGpp(gpp: GppSignals?) {
        if (networkStarted) applyGpp(gpp)
    }

    /** [networkStarted] về như process mới (review Task 8a m2): test đã start AN không làm test sau gọi LDU. Chỉ cho test. */
    @JvmSynthetic
    internal fun resetForTest() {
        networkStarted = false
    }

    fun getAdsBanner(type: Int): AdsBaseBanner? {
        if (bannerAds.containsKey(type)) {
            return bannerAds[type]
        }
        return null
    }

    fun getAdsNtCl(type: Int): AdsBaseBanner? {
        if (ntClAds.containsKey(type)) {
            return ntClAds[type]
        }
        return null
    }

    fun getAdsRect(type: Int): AdsBaseBanner? {
        if (rectAds.containsKey(type)) {
            return rectAds[type]
        }
        return null
    }

    fun getAdsNative(type: Int): AdsBaseNative? {
        if (nativeAds.containsKey(type)) {
            return nativeAds[type]
        }
        return null
    }

    fun getAdsFull(type: Int): AdsBaseFull? {
        if (fullAds.containsKey(type)) {
            return fullAds[type]
        }
        return null
    }

    fun getAdsGift(type: Int): AdsBaseGift? {
        if (giftAds.containsKey(type)) {
            return giftAds[type]
        }
        return null
    }

}
