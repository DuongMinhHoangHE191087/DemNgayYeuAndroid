package com.app.plugin.ads.adsmax

import android.content.Context
import android.util.Log
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.SdkReadyQueue
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.GppConsent
import com.app.plugin.consent.GppSignals
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinPrivacySettings
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration

internal class AdsMaxMy {
    var mContext: Context? = null
    var bannerAds: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
    var ntClAds: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
    var rectAds: MutableMap<Int, AdsBaseBanner> = mutableMapOf()
    var nativeAds: MutableMap<Int, AdsBaseNative> = mutableMapOf()
    var fullAds: MutableMap<Int, AdsBaseFull> = mutableMapOf()
    var giftAds: MutableMap<Int, AdsBaseGift> = mutableMapOf()

    companion object {
        val instance: AdsMaxMy by lazy { AdsMaxMy() }

        /** Như AdMob: callback khởi tạo không về trong khoảng này thì vẫn cho load đang chờ chạy. */
        private const val SDK_INIT_TIMEOUT_MS = 8000L
    }

    internal val readyQueue = SdkReadyQueue()

    /** Dựng các đối tượng quảng cáo. Không khởi tạo AppLovin: việc đó là của [startNetwork]. */
    fun initAd(context: Context) {
        mContext = context
        var adbn = AdsMaxBanner(AdsIDDef.MaxBanner, context)
        bannerAds[AdsIDDef.MaxBanner] = adbn

        var ntcl = AdsMaxNtCl(AdsIDDef.MaxNtCl, context)
        ntClAds[AdsIDDef.MaxNtCl] = ntcl

        var rect = AdsMaxRect(AdsIDDef.MaxRect, context)
        rectAds[AdsIDDef.MaxRect] = rect

        var adnt = AdsMaxNative(AdsIDDef.MaxNative, context)
        nativeAds[AdsIDDef.MaxNative] = adnt

        var adInters = AdsMaxFull(AdsIDDef.MaxFullInters, context)
        fullAds[AdsIDDef.MaxFullInters] = adInters
        var adOpen = AdsMaxFull(AdsIDDef.MaxFullOpen, context)
        fullAds[AdsIDDef.MaxFullOpen] = adOpen
        var adNtFull = AdsMaxFull(AdsIDDef.MaxFullNt, context)
        fullAds[AdsIDDef.MaxFullNt] = adNtFull

        var adRw = AdsMaxGift(AdsIDDef.MaxRewarded, context)
        giftAds[AdsIDDef.MaxRewarded] = adRw
    }

    /**
     * Chỉ `AdSdkStartup` gọi, đúng một lần, khi consent đã cho phép. Load đang chờ chạy trên main
     * thread: chúng dựng view quảng cáo.
     */
    internal fun startNetwork(context: Context) {
        val appContext = context.applicationContext ?: context
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val sdkDevKey = AppUtil.getResourceString(appContext, "admax_devKey")
        if (sdkDevKey.isBlank()) {
            // App không dùng MAX (app tiện ích chỉ chạy AdMob + Meta). Khởi tạo với key rỗng vẫn
            // mở phiên AppLovin, ghi lỗi kèm stack trace và gửi postback mà không phục vụ được gì.
            // Không khởi tạo; load MAX nào lỡ có (config trỏ tới MAX) chạy tiếp và báo lỗi như cũ.
            LogUtil.logD("ads max: admax_devKey trống, không khởi tạo AppLovin (doNotSell skipped, no devKey)")
            main.post { readyQueue.markReady() }
            return
        }
        val initConfigBuilder = AppLovinSdkInitializationConfiguration.builder(sdkDevKey, appContext)
            .setMediationProvider(AppLovinMediationProvider.MAX)

        if (AppUtil.isTestFlagOn(appContext)) {
            initConfigBuilder.setTestDeviceAdvertisingIds(AppUtil.getResourceIds(appContext, "max_test_device"))
        }

        applyGpp(appContext, GppConsent.read(appContext))   // trước initialize: AppLovin đọc cờ lúc khởi tạo
        initializeRequested = true
        AppLovinSdk.getInstance(appContext).initialize(initConfigBuilder.build()) {
            LogUtil.logD("ads max initialize:${it}")
            main.post { readyQueue.markReady() }
        }
        main.postDelayed({
            if (!readyQueue.isReady) {
                LogUtil.logD("ads max initialize: not complete after $SDK_INIT_TIMEOUT_MS ms, releasing held loads")
            }
            readyQueue.markReady()
        }, SDK_INIT_TIMEOUT_MS)
    }

    /** [startNetwork] đã gọi `initialize` của AppLovin: từ đây [followGpp] mới có việc. */
    @Volatile
    private var initializeRequested = false

    /** [initializeRequested] về như process mới (review Task 8a m2, nửa MAX): test đã start MAX không làm test sau
     * `followGpp` gọi setDoNotSell. Chỉ cho test. */
    @JvmSynthetic
    internal fun resetForTest() {
        initializeRequested = false
    }

    /**
     * GPP (opt-out luật bang Mỹ) → `AppLovinPrivacySettings.setDoNotSell`: có GPP section US ([gpp] khác `null`) thì
     * đặt `doNotSell = `[ConsentRules.usOptOut] (cả `false` khi người dùng không opt-out, để không còn giá trị cũ).
     * Không có GPP thì **không gọi**: AppLovin tự đọc GPP, SDK không ghi cờ giả. Không xét tầng consent (`tierAllows`):
     * opt-out bang Mỹ không phụ thuộc GDPR. Lỗi thì log, không ném.
     */
    private fun applyGpp(context: Context, gpp: GppSignals?) {
        if (gpp == null) return
        val optOut = ConsentRules.usOptOut(gpp)
        try {
            AppLovinPrivacySettings.setDoNotSell(optOut, context)
            Log.d(LogUtil.TAG, "ads max: doNotSell=$optOut (${ConsentRules.gppLabel(gpp)})")
        } catch (e: Throwable) {
            LogUtil.logE("ads max: setDoNotSell failed: ${e.message}")
        }
    }

    /**
     * Đánh giá lại GPP giữa phiên (`AdSdkStartup.applyTier`: tầng đổi, hoặc UMP cập nhật mà tầng không đổi):
     * `setDoNotSell` gọi lại được sau khởi tạo. Chưa khởi tạo thì không làm gì — [startNetwork] tự đọc GPP trước
     * `initialize`.
     */
    internal fun followGpp(context: Context, gpp: GppSignals?) {
        if (initializeRequested) applyGpp(context, gpp)
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

    fun getAdnetName(adnet: String?): String {
        var adnetlow = adnet?.lowercase() ?: "source_null"
        if (adnetlow.contains("pangle")) {
            adnetlow = "pangle"
        } else if (adnetlow.contains("applovin") || adnetlow.contains("applovin_exchange")) {
            adnetlow = "applovin"
        } else if (adnetlow.contains("chartboost")) {
            adnetlow = "chartboost"
        } else if (adnetlow.contains("facebook")) {
            adnetlow = "facebook"
        } else if (adnetlow.contains("fyber") || adnetlow.contains("dt exchange")) {
            adnetlow = "fyber"
        } else if (adnetlow.contains("mytarget") || adnetlow.contains("vk ad network")) {
            adnetlow = "mytarget"
        } else if (adnetlow.contains("unity")) {
            adnetlow = "unity"
        } else if (adnetlow.contains("vungle") || adnetlow.contains("liftoff")) {
            adnetlow = "vungle"
        } else if (adnetlow.contains("ironsource")) {
            adnetlow = "ironsource"
        } else if (adnetlow.contains("mintegral")) {
            adnetlow = "mintegral"
        } else if (adnetlow.contains("admob")) {
            adnetlow = "admob"
        } else if (adnetlow.contains("google ad manager")) {
            adnetlow = "gam"
        } else if (adnetlow.contains("bigo") || adnetlow.contains("bıgo")) {
            adnetlow = "bigo"
        }

        return adnetlow
    }
}
