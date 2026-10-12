package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.app.plugin.BuildConfig
import com.app.plugin.R
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.core.AdsFrequencyGate
import com.app.plugin.ads.core.AdsNetworkRegistry
import com.app.plugin.ads.core.AdsTelemetry
import com.app.plugin.ads.core.AppOpenController
import com.app.plugin.ads.core.BannerController
import com.app.plugin.ads.core.Full2Chain
import com.app.plugin.ads.core.InterstitialController
import com.app.plugin.ads.core.NativeCollapseController
import com.app.plugin.ads.core.NativeController
import com.app.plugin.ads.core.NativeFullController
import com.app.plugin.ads.core.RectController
import com.app.plugin.ads.core.RewardedController
import com.app.plugin.license.LicenseGate
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil
import com.applovin.mediation.ads.MaxAdView
import java.util.concurrent.atomic.AtomicInteger

public class AdsHelper private constructor() {
    public companion object {
        public val instance: AdsHelper by lazy { AdsHelper() }

        private const val KEY_REMOVE_ADS_LEGACY = "ads_hp_remove_ad"
        private const val KEY_REMOVE_ADS_MASK = "ads_hp_remove_ad_mask"
        /** Lỗi trả cho AdsLoadCb khi load bị bỏ vì host đang bị huỷ, trong clearActivity (xem skipLoadWithoutHost). */
        private const val LOAD_FAIL_NO_HOST = "host destroyed, load skipped"

        /** Thời gian ẩn banner mặc định khi người dùng xem rewarded "tắt banner". */
        public const val DEFAULT_BANNER_HIDE_MILLIS: Long = 60 * 60 * 1000L

        /** SDK AppLovin MAX có mặt trong bản dựng hay không. Tính một lần, không ném ngoại lệ. */
        internal val isMaxSdkAvailable: Boolean by lazy {
            runCatching { Class.forName("com.applovin.sdk.AppLovinSdk") }.isSuccess
        }
    }

    /** Mask gỡ quảng cáo đến trước [initAdsHelper]; ghi xuống ở đó. */
    @Volatile private var pendingRemoveAdsMask: Int? = null

    private val postAdCooldown = PostAdScreenCooldown()
    private val retryBannerOnConsent: () -> Unit = { banner.retryBannerNow("consent updated") }

    public val optimizationPolicy: AdsOptimizationPolicy = AdsOptimizationPolicy()

    private var mContext: Context? = null
    private var mActivity: Activity? = null
    /** true chỉ trong hai lượt nhả/clear mạng của [clearActivity] (xem [skipLoadWithoutHost]). */
    private var isReleasingHost = false
    private lateinit var listAdsBn: MutableMap<Int, AdsBaseBanner>
    private lateinit var listAdsNtCl: MutableMap<Int, AdsBaseBanner>
    private lateinit var listAdsRect: MutableMap<Int, AdsBaseBanner>
    private lateinit var listAdsNative: MutableMap<Int, AdsBaseNative>
    private lateinit var listAdsFull: MutableMap<Int, AdsBaseFull>
    private lateinit var listAdsGift: MutableMap<Int, AdsBaseGift>
    private lateinit var listAdsAll: MutableList<AdsBase>
    public lateinit var currConfig: AdsConfigOb
    public lateinit var placementsConfigManager: AdsPlacementConfigManager

    /** Telemetry load/impression (1.5.0 Task 1): map đo thời gian load `map4CheckLoadAd` chuyển theo controller. */
    private val telemetry = AdsTelemetry(this)
    /** Luật tần suất, cooldown và 7 cổng thời gian (1.5.0 Task 2). */
    private val gate = AdsFrequencyGate(this)
    /** Rect (1.5.0 Task 3): `rectMapView` chuyển theo controller. */
    private val rect = RectController(this)
    /** Native collapse (1.5.0 Task 4): `ntClIsShow`, `ntclParentView` chuyển theo controller. */
    private val ntcl = NativeCollapseController(this)
    /** Native (1.5.0 Task 5): `nativeMapView`, layout/màu tuỳ biến theo placement chuyển theo controller. */
    private val native = NativeController(this)
    /**
     * Banner (1.5.0 Task 6): chỗ đặt/view đang hiện, vòng load + watchdog + thử lại + refresh, hẹn hiện lại sau phần
     * thưởng "tắt banner" chuyển theo controller; `bnIsShow`, `bnFlagRefresh`, `bnTimeShow` ở lại dưới đây (R3).
     */
    private val banner = BannerController(this)
    /** Native full (1.5.0 Task 7): `ntFullMapView` (view native full → quảng cáo, để huỷ) chuyển theo controller. */
    private val ntFull = NativeFullController(this)
    /** App open (1.5.0 Task 8): `openFlagFirst`, `isWaitReleadOpenAd` chuyển theo controller; `openFlag4Resume` (R1), `openTimeShow` (R3) ở lại dưới đây. */
    private val open = AppOpenController(this)
    /**
     * Rewarded (1.5.0 Task 9): vùng không có state riêng; `giftTimeShow`, `giftIdxShow`, `giftCount4Circle` ở lại dưới
     * đây (R3), `listAdsGift` ở lại (R1).
     */
    private val rewarded = RewardedController(this)
    /**
     * Interstitial (1.5.0 Task 10: load, ready, cờ "đã load"; show ở Task 11): `readyCheckOffMainLogged` chuyển theo
     * controller; `fullIdxShow`, `fullCount4Circle`, `fullTimeShow`, `flagAdsFull*` ở lại dưới đây (R3), `listAdsFull` (R1).
     */
    private val inters = InterstitialController(this)
    /**
     * Chuỗi Full2 (1.5.0 Task 12): chuyển cảnh/cover và show/load Full2; listener của app, hai hẹn giờ và cover mặc định
     * chuyển theo controller; `full2MainHandler`, `pendingFull2`, `full2TransitionActive` (R1) ở lại dưới đây.
     */
    private val full2 = Full2Chain(this)

    private var flagAdClick: Int = 0
    public var timeStartFet: Long = 0

    //banner
    public var bnIsShow: Boolean = false
    @Volatile internal var bnTimeShow: Long = 0
    public var bnFlagRefresh: Int = 0

    //ntcl
    @Volatile internal var clTimeShow: Long = -1

    //openAds
    @Volatile public var openTimeShow: Long = 0
    private var openFlag4Resume: Boolean = false
    @Volatile private var autoShowOpenAdOnForeground: Boolean = true

    //full
    @Volatile public var fullTimeShow: Long = -1
    public var fullIdxShow: Int = 0
    public var fullCount4Circle: Int = 0
    private val fullScreenAdPolicy = FullScreenAdPolicy()
    private val full2MainHandler = Handler(Looper.getMainLooper())
    private data class PendingFull2(val placement: String, val callback: AdsShowCb?)
    private var pendingFull2: PendingFull2? = null
    @Volatile private var full2TransitionActive = false

    public fun setFull2TransitionListener(listener: Full2TransitionListener?): Unit = full2.setFull2TransitionListener(listener)

    /** Until this time, neither an app open ad nor an automatic Full2 may start. */
    @Volatile public var suppressOpenAdUntilMillis: Long = -1L

    /**
     * Call before the app itself sends the user out (share sheet, permission dialog, billing,
     * sign-in): the return trip resumes the app, and an app open ad or a Full2 landing on that
     * resume reads as an ad punishing the user for using the feature.
     */
    public fun suppressNextOpenAd(durationMillis: Long = 5000L) {
        suppressOpenAdUntilMillis = System.currentTimeMillis() + durationMillis
    }

    /** Prevent an automatic fullscreen ad from appearing as the user returns from an ad click. */
    public fun onAdClicked() {
        fullScreenAdPolicy.recordAdClick()
    }

    internal fun beginFull2Transition(parentPlacement: String) = full2.beginFull2Transition(parentPlacement)

    internal fun finishFull2Transition() = full2.finishFull2Transition()

    // Trạng thái R1 của chuỗi Full2 cho Full2Chain (đọc/ghi ở mỗi lần gọi, R2); PendingFull2 không lộ ra ngoài lớp này.
    internal fun full2Handler(): Handler = full2MainHandler
    internal fun isReleasingHost(): Boolean = isReleasingHost
    internal fun isFull2TransitionActive(): Boolean = full2TransitionActive
    internal fun setFull2TransitionActive(v: Boolean) { full2TransitionActive = v }
    internal fun queueFull2(placement: String, cb: AdsShowCb?) { pendingFull2 = PendingFull2(placement, cb) }
    internal fun pendingFull2Placement(): String? = pendingFull2?.placement
    internal fun pendingFull2Callback(): AdsShowCb? = pendingFull2?.callback
    internal fun clearPendingFull2() { pendingFull2 = null }

    //full 2
    @Volatile public var full2TimeShow: Long = -1
    public var full2IdxShow: Int = 0
    public var full2Count4Circle: Int = 0

    //gift
    @Volatile public var giftTimeShow: Long = -1
    public var giftIdxShow: Int = 0
    public var giftCount4Circle: Int = 0

    // AtomicInteger ensures thread-safe read/write from ad SDK callbacks
    // that may fire off the main thread (e.g. Facebook Audience Network)
    public var flagAdsFullShowing: AtomicInteger = AtomicInteger(0)
    // BUG-FIX: cùng lý do với flagAdsFullShowing - đọc/ghi từ ad callback có thể chạy background thread
    public var flagAdsFull4ShowFaild: AtomicInteger = AtomicInteger(0)
    public var flagAdsFull4Dissmiss: AtomicInteger = AtomicInteger(0)

    public fun initAdsHelper(context: Context) {
        mContext = context
        pendingRemoveAdsMask?.let { mask ->
            pendingRemoveAdsMask = null
            setRemoveAdsMask(mask)
        }
        com.app.plugin.consent.ConsentManager.addConsentUpdatedListener(retryBannerOnConsent)
        //init list ads
        // Chỉ dựng đối tượng quảng cáo. Mạng nào cũng chỉ khởi tạo khi app gọi
        // AdsMobMy.startSdkInit() và UMP cho phép (AdSdkStartGate); load đến trước đó được giữ lại.
        LogUtil.logD("ads full helper initAdsHelper")
        AdsMobMy.instance.initAd(mContext!!)
        AdsFbMy.instance.initAd(mContext!!)
        // Ứng dụng nào chỉ chạy AdMob mediation có thể loại hẳn SDK AppLovin MAX khỏi bản dựng
        // cho nhẹ. Khi đó lớp AppLovin không tồn tại, gọi thẳng initAd sẽ ném NoClassDefFoundError
        // và chết ngay lúc khởi tạo quảng cáo. Có SDK thì chạy như cũ; không có thì bỏ qua, mọi
        // getAds* của AdsMaxMy trả null nên phần còn lại vẫn hoạt động bình thường.
        if (isMaxSdkAvailable) {
            MaxBridge.get()?.initAd(mContext!!)
        } else {
            LogUtil.logD("ads max: không có SDK AppLovin trong bản dựng, bỏ qua")
        }

        val r = AdsNetworkRegistry.build()
        listAdsBn = r.banners
        listAdsNtCl = r.ntCls
        listAdsRect = r.rects
        listAdsNative = r.natives
        listAdsFull = r.fulls
        listAdsGift = r.gifts
        listAdsAll = r.all

        currConfig = AdsConfigOb(context)
        currConfig.loadFromMem()
        // Luật tần suất của lần Remote Config trước (ads_policy_android), trước mọi lượt show: prefs đã nạp.
        restorePolicy(context)
        placementsConfigManager = AdsPlacementConfigManager()
        placementsConfigManager.switches.restore(mContext!!)   // 2.4.2 SWITCH: bản gộp + bản ghi theo nguồn
        timeStartFet = System.currentTimeMillis()
        //if not load from mem not case
    }

    public fun setMainActivity(activity: Activity) {
        mActivity = activity
        for (ads in listAdsAll) {
            ads.setActivity(activity)
        }
        open.onHostSet()
    }

    public fun onMainActivityResumed(activity: Activity) {
        if (mActivity !== activity) setMainActivity(activity)
        fullScreenAdPolicy.onHostActivityResumed()
        full2.onHostResumed(activity)
    }

    public fun onMainActivityPaused(activity: Activity) {
        if (mActivity === activity) {
            fullScreenAdPolicy.onHostActivityPaused()
            full2.onHostPaused()
        }
    }

    public fun clearActivity() {
        fullScreenAdPolicy.onHostActivityPaused()
        full2.onHostClearing()
        mActivity = null
        banner.onHostCleared()
        rect.onHostCleared()
        ntcl.onHostCleared()
        // Clear ViewGroup-keyed maps to prevent Activity memory leaks
        // ViewGroup holds reference to Activity; must be released on destroy
        native.onHostCleared()
        // BUG-10 FIX: ntFullMapView cũng giữ View reference → phải clear để tránh Activity leak
        ntFull.onHostCleared()
        lop.removeCallbacksAndMessages(null)
        // Hai lượt. Lượt 2 là nơi native full dismiss dialog và phát onAdDismissed, và các callback
        // dismiss của helper load lại ngay (interstitial, Full2, rewarded). Một lượt thì mạng đứng sau
        // trong listAdsAll, chưa tới lượt clear, còn giữ Activity đang huỷ và dựng quảng cáo trên nó.
        // Lượt 1 nhả Activity của mọi mạng trước, nên load từ bất kỳ callback nào cũng thất bại an toàn.
        // isReleasingHost mở cửa sổ cho skipLoadWithoutHost; finally đóng lại kể cả khi một mạng ném.
        isReleasingHost = true
        try {
            for (ads in listAdsAll) {
                ads.releaseActivity()
            }
            for (ads in listAdsAll) {
                ads.clearActivity()
            }
        } finally {
            isReleasingHost = false
            // Sau cả hai lượt: dismiss phát ở lượt 2 chạy showFull2 sau dòng xoá ở đầu hàm cũ (đo trong Task F), nên
            // xoá ở đây. showFull2 trong cửa sổ đã không xếp (#6); dòng này giữ bất biến "host mới không nhận Full2 xếp
            // từ host cũ" kể cả khi một mạng ném giữa chừng, và nhả cbShow của app (Activity cũ).
            full2.dropPending()
            // Task I5 (G9): cbShow của quảng cáo cha (và mọi placement không đang hiện) cũng giữ Activity cũ; 1.6.0 Task 4:
            // cbLoad của placement không đang load cũng vậy (heap bước rect); đang load: bọc OneShotLoadCb (Task 5).
            if (::placementsConfigManager.isInitialized) {
                val show = placementsConfigManager.releaseIdleShowCallbacks()
                val load = placementsConfigManager.releaseIdleLoadCallbacks()
                LogUtil.logD("ads helper clearActivity: released $show idle show callbacks, $load idle load callbacks")
                val inFlight = placementsConfigManager.wrapInFlightLoadCallbacks()
                if (inFlight > 0) LogUtil.logD("ads helper clearActivity: $inFlight in-flight load callbacks released after terminal")
            }
        }
    }

    /**
     * Chỉ trong cửa sổ [clearActivity] ([isReleasingHost]): lượt 2 là nơi native full phát onAdDismissed
     * đồng bộ, và callback dismiss (của helper hay của app) load lại ngay. Lượt 1 đã nhả Activity của mọi
     * mạng nên load đó vốn thất bại an toàn ở tầng mạng; bỏ sớm ở đây chỉ để khỏi đi hết waterfall rồi
     * nhận "isActivitynull". Ngoài cửa sổ đó, kể cả khi chưa có host (onCreate trước onActivityResumed,
     * nơi SDK đăng ký host), load đi như cũ: mạng đầu fail "isActivitynull", các bước sau được post và
     * chạy sau [setMainActivity].
     */
    internal fun skipLoadWithoutHost(function: String, realPl: String): Boolean {
        if (!isReleasingHost) return false
        LogUtil.logD("ads helper $realPl $function: host destroyed, skip")
        return true
    }

    /** [LOAD_FAIL_NO_HOST] cho controller: hằng `private const` của companion ở lại, không đổi thành `internal` (dump). */
    internal fun loadFailNoHostLabel(): String = LOAD_FAIL_NO_HOST

    /** Công tắc cũ gỡ tất cả hoặc không gì: 1 = [RemoveAdsMask.ALL], 0 = [RemoveAdsMask.NONE]. */
    public fun setRemoveAds(isRemove: Int) {
        setRemoveAdsMask(if (isRemove == 1) RemoveAdsMask.ALL else RemoveAdsMask.NONE)
    }

    /** Người dùng đã gỡ MỌI định dạng (gói Pro). Muốn hỏi từng định dạng thì dùng [isRemoveAds] có cờ. */
    public fun isRemoveAds(): Boolean = getRemoveAdsMask() == RemoveAdsMask.ALL

    /**
     * Ghi những định dạng người dùng đã trả tiền để gỡ, xem [RemoveAdsMask].
     *
     * Entitlement từ billing có thể về trước [initAdsHelper]. Bỏ lệnh ghi lúc đó thì người đã mua
     * gỡ quảng cáo vẫn thấy quảng cáo tới khi entitlement tình cờ đổi lần nữa, mà với IAP một lần
     * thì có thể là không bao giờ. Nên giữ lại và ghi ở [initAdsHelper].
     */
    public fun setRemoveAdsMask(mask: Int) {
        val clean = RemoveAdsMask.sanitize(mask)
        val ctx = mContext
        if (ctx == null) {
            pendingRemoveAdsMask = clean
            return
        }
        PreUtil.setInt(ctx, KEY_REMOVE_ADS_MASK, clean)
        // Giữ khoá cũ đồng bộ, để hạ xuống bản cũ vẫn tắt quảng cáo cho người đã trả tiền.
        PreUtil.setInt(ctx, KEY_REMOVE_ADS_LEGACY, if (clean == RemoveAdsMask.ALL) 1 else 0)
    }

    public fun getRemoveAdsMask(): Int {
        pendingRemoveAdsMask?.let { return it }
        val ctx = mContext ?: return RemoveAdsMask.NONE
        val mask = PreUtil.getInt(ctx, KEY_REMOVE_ADS_MASK, -1)
        // -1 = bản này chưa từng ghi. Đọc khoá boolean cũ, để lượt cài đã mua gỡ quảng cáo
        // trước khi có mask vẫn không thấy quảng cáo.
        if (mask >= 0) return RemoveAdsMask.sanitize(mask)
        return if (PreUtil.getInt(ctx, KEY_REMOVE_ADS_LEGACY, 0) == 1) RemoveAdsMask.ALL else RemoveAdsMask.NONE
    }

    /** Người dùng đã trả tiền gỡ định dạng ứng với [flag] (cùng số hiệu với [isDisableAds]). */
    public fun isRemoveAds(flag: Int): Boolean = RemoveAdsMask.isRemoved(getRemoveAdsMask(), flag)

    public fun saveConfig(): Unit = currConfig.saveCurr()

    public fun onclickAd() { flagAdClick = 1 }

    public fun setFlagAdClick(flag: Int) { flagAdClick = flag }

    public fun checkResumeAfterClickAd() {
        if (flagAdClick > 0) {
            flagAdClick = 2
            lop.removeCallbacksAndMessages(null)
            lop.postDelayed({
                if (flagAdClick == 2) {
                    flagAdClick = 0
                }
            }, 3000)
        }
    }

    /** Dòng "kill removed mid-session" chỉ ghi một lần. */
    private val killLiftLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Kill switch `sdk_kill_android` tắt quảng cáo của bản SDK này (doc 60 G). Chỉ tắt, không cấp gì. Phiên
     * đã bỏ khởi tạo mạng vì kill ([AdSdkStartup.adNetworksKilled]) vẫn tắt tới lần mở kế dù khoá đã gỡ:
     * mạng chưa khởi tạo thì load lọt qua cổng sẽ bị giữ mãi trong hàng chờ của mạng, không callback.
     */
    private fun isAdsKilled(): Boolean {
        if (SdkKillSwitch.current.killsAds(BuildConfig.SDK_VERSION)) return true
        if (!AdSdkStartup.adNetworksKilled) return false
        if (killLiftLogged.compareAndSet(false, true)) {
            LogUtil.logE("kill switch: kill removed mid-session; ads stay disabled until next open")
        }
        return true
    }

    /**
     * Nhãn của một lượt bị cổng `isRemoveAds(flag) || isDisableAds(flag)` chặn: kill switch có nhãn riêng
     * [Constants.AdShowFailReason.SHOW_BLOCKED_KILL_SWITCH]; người dùng đã trả tiền gỡ định dạng này thì
     * giữ [otherwise] (lý do của họ, không phải của ops), cũng như khi kill không bật.
     */
    internal fun disabledReason(flag: Int, otherwise: String): String =
        if (!isRemoveAds(flag) && (isAdsKilled() || LicenseGate.blocksAds)) Constants.AdShowFailReason.SHOW_BLOCKED_KILL_SWITCH else otherwise

    /** Gửi lý do show bị cổng remove-ads/tắt định dạng chặn (nhãn theo [disabledReason]); trả về nhãn đã gửi. */
    internal fun reportDisabledShow(flag: Int, placement: String, otherwise: String): String {
        val reason = disabledReason(flag, otherwise)
        LogUtil.logD("ads helper $placement show blocked: $reason")
        LogEventServer.logAdShowFailWithReason(placement = placement, reason = reason)
        return reason
    }

    /*flag=0-bn, =1-cl, =2-rect =3-nt, =4-interstitial, =5-rewarded, =6-openad*/
    public fun isDisableAds(flag: Int): Boolean {
        if (isAdsKilled() || LicenseGate.blocksAds) return true   // SA1: giấy phép INVALID tắt như kill `ads`
        val check = arrayOf(1, 2, 4, 8, 16, 32, 64)
        val re = currConfig.maskAdsStatus and check[flag]
        return (re == 0)
    }

    public fun getAdsPlacementConfig(placement: String, flagDf: Int): AdsPlacementConfig? {
        return placementsConfigManager.getConfig(placement, flagDf)
    }

    /** `cf_ads_placement` từ Remote Config (hoặc app thay RC): công tắc theo nguồn, xem `PlacementSwitchState`. */
    public fun parserAdsConfig(data: String): Unit = placementsConfigManager.switches.apply(mContext!!, PlacementSource.REMOTE_CONFIG, data)

    /** Placement không bị `cf_ads_placement` tắt (`flag < 0`); trước `initAdsHelper` là `true`; không xét kill/remove-ads. */
    public fun isPlacementEnabled(placement: String): Boolean = !hasPlacementConfig() || (placementsConfigManager.peekConfig(placement)?.flagShow ?: 0) >= 0

    public fun checkAfterLoadIdsConfig() {
        for (ad in listAdsAll) {
            ad.checkLoadAfterGetCf()
        }
        if (currConfig.openFirstCfChange) {
            currConfig.openFirstCfChange = false
            open.reloadSplash()
        }
    }

    public fun onAdLoadStart(
        placement: String, originPl: String, adFormat: String, adPlatform: String, adId: String
    ): Unit = telemetry.onAdLoadStart(placement, originPl, adFormat, adPlatform, adId)

    public fun onAdLoaded(
        placement: String,
        originPl: String,
        adFormat: String,
        adPlatform: String,
        adId: String,
        adNet: String
    ) {
        full2.onNativeFullLoaded(adFormat)
        telemetry.onAdLoaded(placement, originPl, adFormat, adPlatform, adId, adNet)
    }

    public fun onAdLoadFail(
        placement: String,
        originPl: String,
        adFormat: String,
        adPlatform: String,
        adId: String,
        error: String
    ): Unit = telemetry.onAdLoadFail(placement, originPl, adFormat, adPlatform, adId, error)

    public fun onAdImpression(
        placement: String,
        originPl: String,
        adFormat: String,
        adPlatform: String,
        adId: String,
        adNet: String
    ): Unit = telemetry.onAdImpression(placement, originPl, adFormat, adPlatform, adId, adNet)

    //banner
    public fun showBanner(
        placement: String,
        viewParent: ViewGroup,
        isCollapse: Boolean,
        cbLoad: AdsLoadCb?,
        cbShow: AdsShowCb?,
        isDeltaTime: Boolean = true
    ): Unit = banner.showBanner(placement, viewParent, isCollapse, cbLoad, cbShow, isDeltaTime)

    private val lop = Handler(Looper.getMainLooper())

    public fun hideBanner(): Unit = banner.hideBanner()

    public fun isBannerHiddenByReward(): Boolean = banner.isBannerHiddenByReward()

    /**
     * Ẩn banner trong [durationMillis] làm phần thưởng (ví dụ "xem quảng cáo để tắt banner 1 giờ").
     * Mốc hết hạn được lưu, nên qua lần khởi động lại vẫn còn hiệu lực.
     */
    public fun grantBannerHideReward(durationMillis: Long = DEFAULT_BANNER_HIDE_MILLIS): Unit = banner.grantBannerHideReward(durationMillis)

    internal fun resumeParkedBanner() = banner.resumeParkedBanner()

    /**
     * Tải lại banner ngay, bỏ qua chờ refresh: dùng khi vừa có lý do để request lần trước thất
     * bại không còn đúng nữa, ví dụ form consent vừa đóng. Không làm gì khi banner đang không
     * được hiện, đã có quảng cáo, hoặc đang tải.
     */
    public fun retryBannerNow(reason: String): Unit = banner.retryBannerNow(reason)

    // ═══ Luật tần suất (AdsPolicy, doc 62 F, Remote Config `ads_policy_android`) ═══

    /** Luật tần suất đang áp ([AdsPolicy.current]); đổi bằng [applyPolicy]. */
    public val adsPolicy: AdsPolicy get() = gate.adsPolicy

    /**
     * Áp [policy] (main thread): [AdsPolicy.current], cooldown sau click (đặt tại chỗ trên [FullScreenAdPolicy]
     * đang dùng, không dựng instance mới) và cooldown N màn hình. Trường `null` trả luật đó về nguồn cũ: 5 s, và
     * giá trị app đặt bằng [setPostAdScreenCooldown]. SDK tự gọi lúc mở app (bản đã lưu) và khi Remote Config
     * về; app tự gọi thì lần Remote Config về sau ghi đè.
     */
    public fun applyPolicy(policy: AdsPolicy): Unit = gate.applyPolicy(policy)

    /**
     * Giá trị `ads_policy_android` vừa fetch (`FirHelperConfig.applyRemoteCf`, main thread): áp ngay và lưu
     * cho lần mở kế. Khoá vắng (chuỗi rỗng) là về mặc định; JSON hỏng cũng vậy, kèm logE.
     */
    internal fun applyRemotePolicy(raw: String) = gate.applyRemotePolicy(raw)

    /**
     * Lúc mở app ([initAdsHelper]): áp bản đã lưu ở lần Remote Config trước, trước mọi lượt show. Cùng chuỗi đặt
     * [GmaLoadContext] (DOC/65 §7 #18), giữ trong bộ nhớ: lúc nạp GMA không đọc prefs.
     */
    internal fun restorePolicy(context: Context) = gate.restorePolicy(context)

    /**
     * Số màn hình người dùng phải đi qua sau một quảng cáo toàn màn hình trước khi interstitial,
     * app open tự động và banner được hiện lại. 0 (mặc định) là tắt. Xem [PostAdScreenCooldown].
     * Khoá `screens_before_interstitial` của `ads_policy_android`, khi có, thắng giá trị này.
     */
    public fun setPostAdScreenCooldown(screens: Int): Unit = gate.setPostAdScreenCooldown(screens)

    public fun isPostAdScreenCooldownActive(): Boolean = gate.isPostAdScreenCooldownActive()

    /** App gọi mỗi lần người dùng chuyển màn hình (đổi tab, đổi route). */
    public fun recordScreenChange(): Unit = gate.recordScreenChange()

    // Cổng tần suất/thời gian cho controller (1.5.0 Task 2: thân ở AdsFrequencyGate) và state R1 nó đọc qua façade.
    internal fun appContext(): Context? = mContext
    internal fun hostActivity(): Activity? = mActivity
    internal fun screenCooldown(): PostAdScreenCooldown = postAdCooldown
    internal fun fullscreenPolicy(): FullScreenAdPolicy = fullScreenAdPolicy
    internal fun markAutoAdShown() = gate.markAutoAdShown()
    internal fun isOpenAllowShowAds(): Boolean = gate.isOpenAllowShowAds()
    internal fun isIntersAllowLoadAds(): Boolean = gate.isIntersAllowLoadAds()
    internal fun interstitialRefusal(): String? = gate.interstitialRefusal()
    internal fun isNtClAllowShowAds(): Boolean = gate.isNtClAllowShowAds()
    internal fun isBnAllowShowAds(): Boolean = gate.isBnAllowShowAds()
    internal fun isFull2AllowShowAds(): Boolean = gate.isFull2AllowShowAds()
    internal fun isRewardedAllowShowAds(): Boolean = gate.isRewardedAllowShowAds()

    public fun bannerTimeRefresh(): Int = banner.bannerTimeRefresh()

    /** Mạng banner theo type (R1: field `listAdsBn` ở lại đây vì test gán bằng reflection). */
    internal fun bannerNetworks(): MutableMap<Int, AdsBaseBanner> = listAdsBn

    /** `::placementsConfigManager.isInitialized` chỉ gọi được trong AdsHelper (R6). */
    internal fun hasPlacementConfig(): Boolean = ::placementsConfigManager.isInitialized

    //Rect
    public fun showRect(placement: String, viewParent: ViewGroup?, adloadCb: AdsLoadCb? = null): Unit = rect.showRect(placement, viewParent, adloadCb)
    public fun hideRect(viewParent: ViewGroup, isRemove: Boolean): Unit = rect.hideRect(viewParent, isRemove)
    public fun loadRect4Show(placement: String, cbLoad: AdsLoadCb?): Unit = rect.loadRect4Show(placement, cbLoad)
    public fun rectTimeRefresh(): Int = rect.rectTimeRefresh()

    /** Mạng rect theo type (R1: field `listAdsRect` ở lại đây vì test gán bằng reflection). */
    internal fun rectNetworks(): MutableMap<Int, AdsBaseBanner> = listAdsRect

    //native cl
    public fun createAndAddNtClParentView(): FrameLayout? = ntcl.createAndAddNtClParentView()
    public fun bringLayoutCl(): Unit = ntcl.bringLayoutCl()
    public fun showNtCl(placement: String, cbShow: AdsShowCb?): Unit = ntcl.showNtCl(placement, cbShow)
    public fun loadNtClForThisTurn(placement: String, loadCb: AdsLoadCb? = null): Unit = ntcl.loadNtClForThisTurn(placement, loadCb)
    public fun hideNativeCl(): Unit = ntcl.hideNativeCl()

    /** Mạng NtCl theo type (R1: field `listAdsNtCl` ở lại đây vì test gán bằng reflection). */
    internal fun ntClNetworks(): MutableMap<Int, AdsBaseBanner> = listAdsNtCl

    //native
    public fun setLayoutCustome4Native(resId: Int, adType: Int): Unit = native.setLayoutCustome4Native(resId, adType)
    public fun getLayoutCustome4Native(adType: Int): Int = native.getLayoutCustome4Native(adType)
    public fun setCustomeView4Native(placement: String, bgColor: Int, btActionColor: Int): Unit =
        native.setCustomeView4Native(placement, bgColor, btActionColor)
    public fun removeCustomeView4Native(placement: String): Unit = native.removeCustomeView4Native(placement)
    public fun getNativeCustom(placement: String): ObjectForCustomeNativeAd? = native.getNativeCustom(placement)
    public fun showNative(placement: String, viewParent: ViewGroup?, cbShow: AdsShowCb?, isLoadNShow: Boolean = false): Unit =
        native.showNative(placement, viewParent, cbShow, isLoadNShow)
    public fun loadNative4Show(placement: String, adloadCb: AdsLoadCb?): Unit = native.loadNative4Show(placement, adloadCb)
    public fun hideNative(viewParent: ViewGroup, isRemove: Boolean): Unit = native.hideNative(viewParent, isRemove)

    /** Mạng native theo type (R1: field `listAdsNative` ở lại đây vì test gán bằng reflection). */
    internal fun nativeNetworks(): MutableMap<Int, AdsBaseNative> = listAdsNative

    //openAds
    public fun loadOpenAdForThisTurn(placement: String, adloadCb: AdsLoadCb? = null): Unit = open.loadOpenAdForThisTurn(placement, adloadCb)

    public fun setAfterFirstOpenShow(): Unit = open.setAfterFirstOpenShow()

    public fun setFlag4ShowResume(flag: Boolean) { openFlag4Resume = flag }

    /**
     * Bật/tắt lượt SDK **tự** gọi `showOpenAd(full_resume)` 1 s sau khi app vào foreground (mặc định `true`, như 2.4.4). Tắt chỉ
     * bỏ lượt tự gọi đó, app vẫn gọi [showOpenAd] được; khác [setFlag4ShowResume] (`false` chặn mọi open ad trừ `full_splash`).
     */
    public fun setAutoShowOpenAdOnForeground(enabled: Boolean) { autoShowOpenAdOnForeground = enabled }
    internal fun autoShowOpenAdOnForeground(): Boolean = autoShowOpenAdOnForeground

    public fun getFlag4ShowResume(): Boolean = openFlag4Resume

    public fun isFullShowing(): Boolean = flagAdsFullShowing.get() > 0

    public fun isClickAds(): Boolean = flagAdClick > 0

    public fun isFirstOpenReady(): Boolean = open.isFirstOpenReady()

    public fun showFirstOpen(cbShow: AdsShowCb?): Boolean = open.showFirstOpen(cbShow)

    public fun showOpenAd(placement: String, cbShow: AdsShowCb?): Boolean = open.showOpenAd(placement, cbShow)

    /** Cờ resume của app open cho controller (R1: field `openFlag4Resume` ở lại đây vì test gán bằng reflection). */
    internal fun openResumeFlag(): Boolean = openFlag4Resume

    /** Cờ click quảng cáo (cổng "không hiện app open ngay sau click"); `setFlagAdClick` public giữ nguyên (R8: tên khác). */
    internal fun adClickFlag(): Int = flagAdClick
    internal fun setAdClickFlag(v: Int) { flagAdClick = v }

    //NativeFull
    public fun loadNativeFullForThisTurn(placement: String, loadCb: AdsLoadCb? = null): Unit = ntFull.loadNativeFullForThisTurn(placement, loadCb)
    public fun getNativeFullForShow(placement: String, adsShowCb: AdsShowCb?): View? = ntFull.getNativeFullForShow(placement, adsShowCb)
    public fun destroyNativeFull(nativeAdFull: View?): Unit = ntFull.destroyNativeFull(nativeAdFull)

    //interstitial====================================================
    public fun loadInterstitialForThisTurn(
        placement: String,
        flagImg: Int,
        loadCb: AdsLoadCb? = null,
        isLoadFull2: Boolean = true
    ): Unit = inters.loadInterstitialForThisTurn(placement, flagImg, loadCb, isLoadFull2)

    public fun showInterstitial(
        placement: String,
        flagImage: Int,
        cbShow: AdsShowCb,
        isDeltaTime: Boolean = true
    ): Boolean = inters.showInterstitial(placement, flagImage, cbShow, isDeltaTime)

    /** [fixedMoment] `true`: thời điểm cố định, hiện mỗi lần gọi (bỏ qua guardrail segment, luật tần suất, cooldown N màn, brain; KDoc ở `InterstitialController`). */
    public fun showInterstitial(placement: String, flagImage: Int, cbShow: AdsShowCb, isDeltaTime: Boolean = true, fixedMoment: Boolean): Boolean =
        inters.showInterstitial(placement, flagImage, cbShow, isDeltaTime, fixedMoment)

    /** Mạng toàn màn hình theo type (R1: `listAdsFull` ở lại đây; thân ở `InterstitialController.fullNetwork`, 1.5.0 Task 10). */
    internal fun fullNetwork(type: Int): AdsBaseFull? = inters.fullNetwork(type)

    /** Mọi mạng toàn màn hình (R1: `listAdsFull` ở lại đây; app open đọc qua đây ở mỗi lần gọi, R2). */
    internal fun fullNetworks(): MutableMap<Int, AdsBaseFull> = listAdsFull

    /** Placement interstitial đã có quảng cáo tải xong (chưa xét các cổng lúc show). */
    public fun isInterstitialLoaded(placement: String): Boolean = inters.isInterstitialLoaded(placement)

    /**
     * `true` chỉ khi có quảng cáo thật trong tay VÀ `showInterstitial(placement, flagImage, cb)`
     * (mặc định `isDeltaTime = true`) sẽ qua mọi cổng tức thời, kể cả luật tần suất `ads_policy_android`
     * ([interstitialRefusal]) và guardrail segment (2.4.2), trừ quyết định của brain.
     *
     * App dùng hàm này trước khi chạy màn chờ kiểu "ad break": chỉ nhìn [isInterstitialLoaded]
     * thì màn chờ chạy xong rồi lệnh show mới bị chặn, người dùng tưởng sắp có quảng cáo mà
     * không có gì.
     *
     * **Gọi trên main thread**: không phải phép đọc thuần. Quảng cáo quá tuổi bị bỏ ngay trong lời gọi
     * (`AdsPlBase.hasLoadedAd`: native full bị `destroy()`, gửi `ad_expired`), và khi cờ đã load bật mà không
     * mạng nào còn quảng cáo, cũng không slot mạng nào đang tải, thì cờ hạ ([isInterstitialLoaded] `false`) và
     * placement được nạp lại một lần. Gọi ngoài main thì `logE` một lần (mỗi process) rồi vẫn xử lý.
     *
     * **[flagImage] phải trùng giá trị đã dùng khi load** (`loadInterstitialForThisTurn(placement, flagImg)`) và khi
     * show: sẵn sàng xét các mạng theo `flagImage` (`getListIntersLoadShow`), còn cờ đã load và lần nạp lại xét theo
     * placement (mọi mạng toàn màn hình). Khác nhau thì hàm trả `false` dù [isInterstitialLoaded] `true` và không nạp
     * lại (placement vẫn có mạng giữ quảng cáo) — tới khi quảng cáo đó quá tuổi (60 phút; interstitial MAX không có
     * tuổi) hoặc được show.
     */
    public fun isInterstitialReadyToShow(placement: String, flagImage: Int = 0): Boolean = inters.isInterstitialReadyToShow(placement, flagImage)

    /** Cùng kết luận với `showInterstitial(placement, flagImage, cb, isDeltaTime, fixedMoment)`, trừ brain. */
    public fun isInterstitialReadyToShow(placement: String, flagImage: Int = 0, isDeltaTime: Boolean = true, fixedMoment: Boolean): Boolean =
        inters.isInterstitialReadyToShow(placement, flagImage, isDeltaTime, fixedMoment)

    /** Hạ cờ "đã load" cũ rồi nạp lại (thân + KDoc ở `InterstitialController.dropStaleLoadedFlag`); native full gọi qua đây. */
    internal fun dropStaleLoadedFlag(plcf: AdsPlacementConfig, function: String, reload: () -> Unit) =
        inters.dropStaleLoadedFlag(plcf, function, reload)

    // full2
    public fun showFull2(placement: String, cbShow: AdsShowCb?): Unit = full2.showFull2(placement, cbShow)

    internal fun loadFull2ForThisTurn(placement: String) = full2.loadFull2ForThisTurn(placement)

    //rewarded====================================================
    public fun loadRewardedForThisTurn(placement: String, loadCb: AdsLoadCb? = null): Unit = rewarded.loadRewardedForThisTurn(placement, loadCb)

    public fun showRewarded(placement: String, cbShow: AdsGiftShowCb): Boolean = rewarded.showRewarded(placement, cbShow)

    /** Mạng rewarded theo type (R1: field `listAdsGift` ở lại đây vì test gán bằng reflection; đọc ở mỗi lần gọi, R2). */
    internal fun rewardedNetworks(): MutableMap<Int, AdsBaseGift> = listAdsGift
}
