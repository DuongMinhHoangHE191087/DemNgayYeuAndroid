package com.app.plugin.ads.adsmob

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.MainFront
import com.app.plugin.consent.ConsentState
import com.app.plugin.logevent.AdmobSDKInitState
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration.MaxAdContentRating
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration.TagForChildDirectedTreatment
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration.TagForUnderAgeOfConsent
import com.google.android.libraries.ads.mobile.sdk.initialization.AdapterStatus
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationStatus
import com.google.android.libraries.ads.mobile.sdk.initialization.OnAdapterInitializationCompleteListener
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 2.4.0 NG1 (Hợp đồng B "NG1 init", D4/D9-240): bản GMA Next-Gen của [AdsMobMy]. API công khai như Legacy. Khác Legacy:
 * `MobileAds.initialize(ctx, InitializationConfig, listener)` chạy trên luồng tạo mới [THREAD_NAME] ([S1][S22]); quá
 * [SDK_INIT_TIMEOUT_MS] thì GIỮ hàng đợi (load trước init ném), tối đa [QUEUE_CAP] lời gọi (tràn từ chối lời gọi mới); init xong
 * chạy hàng đợi đúng một lần trên main; init lỗi bỏ hàng đợi. Năm dòng `gma: …` qua `Log.i` (luật 26/28).
 */
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

        private const val SDK_INIT_TIMEOUT_MS = 8000L
        private const val QUEUE_CAP = 16
        private const val THREAD_NAME = "appplugin-gma-init"
        private const val META_APP_ID = "com.google.android.gms.ads.APPLICATION_ID"
        private const val PENDING = 0
        private const val READY = 1
        private const val FAILED = 2
    }

    // Seam test (đồng hồ, luồng, hẹn giờ, MobileAds, meta-data, log); mặc định là đường thật.
    @Volatile internal var initExecutor: Executor? = null
    @Volatile internal var scheduleTimeout: (Runnable, Long) -> Unit = { r, ms -> Handler(Looper.getMainLooper()).postDelayed(r, ms) }
    @Volatile internal var initializer: (Context, InitializationConfig, OnAdapterInitializationCompleteListener) -> Unit =
        { c, cfg, l -> MobileAds.initialize(c, cfg, l) }
    @Volatile internal var appIdReader: (Context) -> String? = { readAppId(it) }
    @Volatile internal var testDeviceIds: (Context) -> List<String> =
        { if (AppUtil.isTestFlagOn(it)) AppUtil.getResourceIds(it, "admob_test_device") else emptyList() }
    @Volatile internal var clock: () -> Long = { SystemClock.elapsedRealtime() }
    @Volatile internal var logSink: (String) -> Unit = { Log.i(LogUtil.TAG, it) }

    private val lock = Any()
    private var state = PENDING
    private val queue = ArrayDeque<() -> Unit>()
    private var dropped = 0
    private val initDoneOnce = java.util.concurrent.atomic.AtomicBoolean(false)

    /** MobileAds Next-Gen đã khởi tạo xong (khác Legacy: quá hạn KHÔNG tính là xong). */
    public val isSdkInitialized: Boolean
        get() = synchronized(lock) { state == READY }

    /**
     * Chạy [action] ngay nếu GMA Next-Gen đã khởi tạo xong; chưa thì xếp hàng (≤ [QUEUE_CAP], tràn thì từ chối lời gọi mới +
     * `gma: init queue full, drop <n>`), chạy theo thứ tự đúng một lần khi init xong. Init lỗi: bỏ.
     */
    public fun runWhenSdkReady(action: () -> Unit) {
        val now = synchronized(lock) {
            when (state) {
                READY -> true
                FAILED -> return
                else -> {
                    // R-NG1 I2: tràn thì TỪ CHỐI lời gọi mới — giữ preload/splash đã xếp trước (D4 "giữ hàng đợi").
                    if (queue.size >= QUEUE_CAP) { dropped++; logSink("gma: init queue full, drop $dropped"); return }
                    queue.addLast(action)
                    false
                }
            }
        }
        if (now) action()
    }

    /** Như Legacy: yêu cầu khởi tạo mọi mạng quảng cáo, SDK tự hoãn tới khi UMP cho phép (`AdSdkStartGate`). */
    public fun startSdkInit(context: Context) {
        AdSdkStartup.requestAdNetworks(context)
    }

    /** Dựng các đối tượng quảng cáo. Không khởi tạo MobileAds: việc đó là của [startNetwork]. */
    public fun initAd(context: Context) {
        mContext = context
        if (AppUtil.isTestFlagOn(context)) LogUtil.setFlagLog(3)
        bannerAds[AdsIDDef.AdmobBanner] = AdsMobBanner(AdsIDDef.AdmobBanner, context)
        ntClAds[AdsIDDef.AdmobNtCl] = AdsMobNtCl(AdsIDDef.AdmobNtCl, context)
        rectAds[AdsIDDef.AdmobRect] = AdsMobRect(AdsIDDef.AdmobRect, context)
        nativeAds[AdsIDDef.AdmobNative] = AdsMobNative(AdsIDDef.AdmobNative, context)
        fullAds[AdsIDDef.AdmobFullInters] = AdsMobFull(AdsIDDef.AdmobFullInters, context)
        fullAds[AdsIDDef.AdmobFullLow] = AdsMobFull(AdsIDDef.AdmobFullLow, context)
        fullAds[AdsIDDef.AdmobFullOpen] = AdsMobFull(AdsIDDef.AdmobFullOpen, context)
        fullAds[AdsIDDef.AdmobFullNt] = AdsMobFull(AdsIDDef.AdmobFullNt, context)
        giftAds[AdsIDDef.AdmobRewarded] = AdsMobGift(AdsIDDef.AdmobRewarded, context)
        giftAds[AdsIDDef.AdmobRwi] = AdsMobRwi(AdsIDDef.AdmobRwi, context)
    }

    /**
     * Chỉ `AdSdkStartup.startAdNetworks` gọi, một lần, sau consent (thứ tự #55 giữ). Main chỉ đọc cờ test và hẹn giờ quá
     * hạn; meta-data `APPLICATION_ID`, `RequestConfiguration` và `MobileAds.initialize` chạy trên [THREAD_NAME].
     */
    internal fun startNetwork(context: Context) {
        val app = context.applicationContext ?: context
        val testIds = testDeviceIds(app)
        scheduleTimeout(Runnable { onTimeout() }, SDK_INIT_TIMEOUT_MS)
        (initExecutor ?: Executors.newSingleThreadExecutor { r -> Thread(r, THREAD_NAME).apply { isDaemon = true } }
            .also { initExecutor = it }).execute {
            try {
                val appId = appIdReader(app) ?: throw IllegalStateException("APPLICATION_ID")
                val config = InitializationConfig.Builder(appId).setRequestConfiguration(requestConfiguration(testIds)).build()
                val startedAt = clock()
                initializer(app, config) { status -> onInitDone(status, clock() - startedAt) }
            } catch (e: Throwable) {
                onInitFailed(e.javaClass.simpleName.ifEmpty { "Unknown" })
            }
        }
    }

    /** MA + test device như Legacy; cờ trẻ em/tuổi đồng ý chỉ khi studio đã khai tường minh. */
    internal fun requestConfiguration(testIds: List<String>): RequestConfiguration {
        val builder = RequestConfiguration.Builder()
            .setMaxAdContentRating(MaxAdContentRating.MAX_AD_CONTENT_RATING_MA)
            .setTestDeviceIds(testIds)
        ConsentState.childDirected?.let {
            builder.setTagForChildDirectedTreatment(
                if (it) TagForChildDirectedTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                else TagForChildDirectedTreatment.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
            )
        }
        ConsentState.underAgeOfConsent?.let {
            builder.setTagForUnderAgeOfConsent(
                if (it) TagForUnderAgeOfConsent.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                else TagForUnderAgeOfConsent.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
            )
        }
        return builder.build()
    }

    private fun onTimeout() {
        if (synchronized(lock) { state == PENDING }) logSink("gma: init timeout, giữ hàng đợi")
    }

    /** Luồng nền của SDK (lần gọi đầu): telemetry adapter như Legacy, `gma: ready`, rồi chạy hàng đợi trên main đúng một lần. */
    private fun onInitDone(status: InitializationStatus, ms: Long) {
        if (!initDoneOnce.compareAndSet(false, true)) return   // R-NG1 m1: SDK có thể gọi lại; telemetry + `ready` một lần/process
        LogEventServer.loadSDKInitTime()
        for ((adapterClass, s) in status.adapterStatusMap) {
            val net = AppUtil.extractAdNetwork(adapterClass)
            LogUtil.logD("ads admob adapter $net: ${s.initializationState} ${s.latency}ms ${s.description}")
            LogEventServer.logAdAdapterInit(
                adPlatform = "admob",
                adapterClass = net,
                isReady = if (s.initializationState == AdapterStatus.InitializationState.COMPLETE) 1 else 0,
                latency = s.latency.toLong(),
                screenName = ScreenName.name
            )
        }
        logSink("gma: ready ${ms}ms")
        MainFront.run { drain() }   // 2.4.4 INITCB: message async đầu hàng main, không cuối hàng sau khung hình
    }

    private fun drain() {
        val due = synchronized(lock) {
            if (state != PENDING) return
            state = READY
            queue.toList().also { queue.clear() }
        }
        AdmobSDKInitState.isInitComplete = 1   // F1a m3: ghi trên main (đọc trên main), không trên luồng SDK
        logSink("gma: init done, run ${due.size}")
        due.forEach { it() }
    }

    private fun onInitFailed(code: String) {
        synchronized(lock) {
            if (state != PENDING) return
            state = FAILED
            queue.clear()
        }
        logSink("gma: init failed ($code)")
    }

    private fun readAppId(ctx: Context): String? =
        ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA).metaData?.getString(META_APP_ID)

    public fun getAdsBanner(type: Int): AdsBaseBanner? = bannerAds[type]

    public fun getAdsNtCl(type: Int): AdsBaseBanner? = ntClAds[type]

    public fun getAdsRect(type: Int): AdsBaseBanner? = rectAds[type]

    public fun getAdsNative(type: Int): AdsBaseNative? = nativeAds[type]

    public fun getAdsFull(type: Int): AdsBaseFull? = fullAds[type]

    public fun getAdsGift(type: Int): AdsBaseGift? = giftAds[type]
}
