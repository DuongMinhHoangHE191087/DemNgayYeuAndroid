package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.annotation.RequiresApi
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseFull
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.AppResumeIF
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.NativeMediaRatio
import com.app.plugin.ads.NtFullDialogHost
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest

/**
 * 2.4.0 NG5b (Hợp đồng B "NG5b", luật 36, R3): Full2 (native full) AdMob trên GMA Next-Gen. Nạp bằng
 * `NativeAdLoader.load(NativeAdRequest, NativeAdLoaderCallback)` [S13] (không Context; `VideoOptions` điều khiển riêng của
 * [AdsMobNtFullView.videoOptions], tỉ lệ media theo hướng màn hình); mọi callback load/sự kiện (gồm `onAdPaid`) về main qua
 * [MainPost] ([LoadCb], [Events]). Thứ tự chuỗi: [prepareChainedShow] dựng dialog bằng MỘT [MainPost.post] nên chạy SAU mọi
 * callback nền đã xếp hàng trước nó (vd. dismiss của quảng cáo cha) và xét lại tại lúc dựng: chuỗi còn hiệu lực
 * ([prepareGen]), host còn dùng được, quảng cáo còn trong mục. Phần còn lại (show/get/dismiss/host huỷ) giữ Legacy.
 */
internal class AdsMobPlNtFull(context: Context, adsBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseFull(context, adsBase, type, pl), AppResumeIF {
    override val eventTag: String get() = "mob_nt_full"

    /** `fullscreen_max_age_minutes` của `ads_policy_android` (mặc định 60 phút), đọc mỗi lần xét tuổi. */
    override val loadedAdMaxAgeMs: Long get() = AdsPolicy.current.fullscreenMaxAgeMillis()

    private val timeShowBtClose = 3
    var mDialogNativeFull: AdsMobNtFullView? = null
    var adsEcmpShow: AdsEcmp? = null
    var nativeAdShow: NativeAd? = null
    var mIsDelay = false

    // Dialog dựng trước lúc show, khi quảng cáo cha còn che màn hình; bỏ ngay khi dùng hoặc mất hiệu lực, để không bao giờ
    // hiện nhầm với quảng cáo khác quảng cáo đã populate nó.
    private var preparedDialog: AdsMobNtFullView? = null
    private var preparedNativeAd: NativeAd? = null
    private var preparedIsDelay = false
    private var preparedActivity: Activity? = null

    /** Mỗi lần xin dựng / bỏ dialog tăng một: lần dựng đã xếp hàng chỉ chạy khi chưa có bỏ/xin mới sau nó (NG5b). */
    private var prepareGen = 0

    var adsEcmpGet: AdsEcmp? = null
    var adsGetCb: AdsShowCb? = null
    private val getTurnAds = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>()) // P3: native lượt get

    private lateinit var admobmy: AdsMobMy
    private val background = AdsMobNtFullBackground() // lớp phủ safe-area (C1a: tách file)

    override fun onInit() {
        admobmy = AdsMobMy.instance
    }

    private fun tag(pl: String) = "ads full nt admob$adsType $pl ${adsPlacementEcmp.originPl}"

    override fun tryLoad() {
        if (!admobmy.isSdkInitialized) {
            adsPlacementEcmp.isLoading = true
            admobmy.runWhenSdkReady(unlessLoadAbandoned { tryLoad() })
            return
        }
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad == null) {
            LogUtil.logD("${tag(loadPl)} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null")
            return
        }
        if (ecmpLoad.isLoaded || ecmpLoad.isLoading) {
            if (ecmpLoad.isLoaded) {
                LogUtil.logD("${tag(loadPl)} tryLoad has loaded")
                adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId)
            } else {
                LogUtil.logD("${tag(loadPl)} tryLoad is loading")
                adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "isloading")
            }
            return
        }
        if (!ecmpLoad.adsId.startsWith("ca-app")) {
            LogUtil.logD("${tag(loadPl)} tryLoad idload not correct")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "idload not correct")
            return
        }
        val activity = mActivity
        if (activity == null) {
            LogUtil.logD("${tag(loadPl)} tryLoad activity null")
            adsPlacementEcmp.isLoading = false
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "activity null")
            return
        }
        LogUtil.logD("${tag(loadPl)} tryLoad id=${ecmpLoad.adsId}")
        adsPlacementEcmp.isLoading = true
        adsPlacementEcmp.isHasLoad = true
        ecmpLoad.isLoading = true
        ecmpLoad.isLoaded = false
        val portrait = activity.resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT
        val ratio = NativeMediaRatio.pick(NativeMediaRatio.forRequest(mContext, portrait), NativeAd.NativeMediaAspectRatio.ANY,   // 2.4.4 ASPECT
            NativeAd.NativeMediaAspectRatio.LANDSCAPE, NativeAd.NativeMediaAspectRatio.PORTRAIT)                         // mã 1/2/3 → enum Next-Gen
        val request = AdRequestFactory.native(mContext, ecmpLoad.adsId, AdsMobNtFullView.videoOptions(), ratio)
        loader(request, MainPost.nativeLoader(LoadCb(ecmpLoad, adsPlacementEcmp.loadGeneration)))
        AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId)
    }

    /** Kết quả nạp, trên main (qua [MainPost.nativeLoader]). */
    private inner class LoadCb(private val ecmpLoad: AdsEcmp, private val loadGen: Int) : NativeAdLoaderCallback {
        override fun onNativeAdLoaded(nativeAd: NativeAd) {
            // IMP-1: sự kiện (gồm onAdPaid) gắn trước guard stale: quảng cáo muộn mà keepLateAd giữ vẫn tính doanh thu.
            nativeAd.adEventCallback = MainPost.nativeEvents(Events(nativeAd, ecmpLoad))
            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return }
            adsPlacementEcmp.isLoading = false
            ecmpLoad.isLoading = false
            if (!NtFullDialogHost.isUsable(mActivity)) {
                nativeAd.destroy() // host chết lúc nạp: không giữ quảng cáo không ai hiện được (NG4 như vậy)
                ecmpLoad.isLoaded = false
                ecmpLoad.adObject = null
                onFinishLoad()
                adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, "activity unavailable")
                return
            }
            adsPlacementEcmp.isloaded = true
            ecmpLoad.isLoaded = true
            ecmpLoad.adObject = nativeAd
            AdsHelper.instance.onAdLoaded(
                loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId, nativeAd.getResponseInfo()?.adapterClassName ?: "source_null"
            )
            LogUtil.logD("${tag(loadPl)} tryLoad ok id=${ecmpLoad.adsId}")
            adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId)
            onFinishLoad()
        }

        override fun onAdFailedToLoad(adError: LoadAdError) {
            if (isStaleLoadCallback(loadGen)) return
            val code = adError.code.value
            LogUtil.logD("${tag(loadPl)} tryLoad fail id=${ecmpLoad.adsId} err=$code")
            ecmpLoad.isLoading = false
            ecmpLoad.adObject = null
            adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
            AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId, "$code")
            if (!adsPlacementEcmp.flag4Load && (adsPlacementEcmp.adEcmps?.idxCurrEcpm ?: 0) < (adsPlacementEcmp.adEcmps?.list?.size ?: 0)) {
                tryLoad()
                return
            }
            // Vô điều kiện (Legacy): để cờ loading kẹt thì Full2 sau quảng cáo cha kế không bao giờ nạp lại (speakerclean).
            adsPlacementEcmp.isLoading = false
            if (!adsPlacementEcmp.flag4Load) {
                adsPlacementEcmp.isloaded = false
                adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "$code ${adError.message}")
            }
            onFinishLoad()
        }
    }

    /** Sự kiện của quảng cáo đã nạp, trên main (qua [MainPost.nativeEvents]); `onAdPaid` thay `OnPaidEventListener` Legacy. */
    private inner class Events(private val ad: NativeAd, private val ecmp: AdsEcmp) : NativeAdEventCallback {
        /** 2.1.0 P3 + 2.2.0 PSH: đường (show/get) của lượt chính quảng cáo này; flagShow hiện hành chỉ là dự phòng. */
        private fun isGetTurn() = synchronized(getTurnAds) { ad in getTurnAds }

        override fun onAdImpression() {
            if (!isGetTurn()) {
                LogUtil.logD("${tag(showPl)} onAdImpression id=${ecmp.adsId}")
                adsPlacementEcmp.adsShowCb?.onAdImpression(turnPl(ad, showPl), adsPlacementEcmp.originPl, ecmp.adsId)
            } else {
                LogUtil.logD("${tag(getPl)} onAdImpression get id=${ecmp.adsId}")
                adsGetCb?.onAdImpression(turnPl(ad, getPl), adsPlacementEcmp.originPl, ecmp.adsId)
            }
        }

        override fun onAdClicked() {
            if (!isGetTurn()) {
                LogUtil.logD("${tag(showPl)} onAdClicked")
                adsPlacementEcmp.adsShowCb?.onAdClick(turnPl(ad, showPl), adsPlacementEcmp.originPl, ecmp.adsId)
                mDialogNativeFull?.afterClick()
            } else {
                LogUtil.logD("${tag(getPl)} onAdClicked get")
                adsGetCb?.onAdClick(turnPl(ad, getPl), adsPlacementEcmp.originPl, ecmp.adsId)
            }
            AdsHelper.instance.onclickAd()
        }

        override fun onAdSwipeGestureClicked() {
            LogUtil.logD("${tag(showPl)} onAdSwipeGestureClicked")
        }

        override fun onAdPaid(value: AdValue) {
            // Lượt show/get ghi placement của quảng cáo trước khi nó hiện (setShowPlacement/setGetPlacement): showPl là dự phòng.
            PaidEvent.admob(turnPl(ad, showPl), adsPlacementEcmp.originPl, FORMAT, ecmp.adsId, PaidEvent.adNetwork(ad.getResponseInfo()), value)
        }
    }

    /**
     * Dựng sẵn dialog Full2 sau quảng cáo cha (Legacy: `create()` chạy onCreate không gắn window; show() chỉ còn onStart,
     * nơi đếm ngược bắt đầu). Next-Gen gọi callback ở luồng nền [S5] và [MainPost] xếp chúng vào hàng main, nên lần dựng
     * được xếp bằng [MainPost.post] (FIFO): mọi callback đã xếp trước — dismiss của cha, nạp xong Full2 — chạy trước, và
     * lần dựng xét lại trạng thái lúc chạy. Không lấy quảng cáo khỏi mục: show() tự lấy và chỉ dùng lại dialog cùng quảng cáo.
     */
    override fun prepareChainedShow() {
        val gen = ++prepareGen
        MainPost.post { if (gen == prepareGen) buildPreparedDialog() }
    }

    /** Trên main, đúng lượt [prepareGen]: dựng khi chưa có dialog dựng sẵn, host dùng được (NC3) và quảng cáo còn trong mục. */
    private fun buildPreparedDialog() {
        if (preparedDialog != null) return
        val activity = mActivity ?: return
        if (!NtFullDialogHost.isUsable(activity)) {
            LogUtil.logD("ads full nt admob$adsType prepare skipped: host not usable")
            return
        }
        val ecmp = loadedAdWithinMaxAge() ?: return
        val ad = ecmp.adObject as? NativeAd ?: return
        var dialog: AdsMobNtFullView? = null
        try {
            dialog = AdsMobNtFullView(activity, ad, timeShowBtClose, false, true)
            dialog.create()
            preparedDialog = dialog
            preparedNativeAd = ad
            preparedIsDelay = false
            preparedActivity = activity
            LogUtil.logD("ads full nt admob$adsType prepared chained dialog")
        } catch (e: Throwable) {
            // Dựng sẵn là tối ưu; lỗi không được làm mất impression.
            LogUtil.logD("ads full nt admob$adsType prepare failed: ${e.message}")
            runCatching { dialog?.releaseNativeAdView() } // create() có thể ném sau registerNativeAd: nhả view dở dang
                .onFailure { t -> LogUtil.logE("ads full nt admob$adsType release half-built view failed: ${t.message}") }
            discardPreparedDialog()
        }
    }

    override fun cancelChainedShow() {
        if (preparedDialog != null) {
            LogUtil.logD("ads full nt admob$adsType discarding unused prepared dialog")
        }
        discardPreparedDialog()
    }

    private fun discardPreparedDialog() {
        prepareGen++ // lần dựng còn xếp hàng (nếu có) thành vô hiệu
        preparedDialog?.let {
            runCatching { it.releaseNativeAdView() }
                .onFailure { e -> LogUtil.logE("ads full nt admob$adsType releaseNativeAdView failed: ${e.message}") }
            runCatching { it.cancelCountdown() }
                .onFailure { e -> LogUtil.logE("ads full nt admob$adsType discardPreparedDialog failed: ${e.message}") }
        }
        preparedDialog = null
        preparedNativeAd = null
        preparedActivity = null
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        val ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow == null) {
            LogUtil.logD("${tag(placement)} show not loaded")
            LogEventServer.logAdShowFailWithReason(placement = placement, reason = Constants.AdShowFailReason.SHOW_NOT_LOADED_ON_SHOW)
            return false
        }
        LogUtil.logD("${tag(placement)} show")
        mIsDelay = (flagShow and 1) == 1
        setShowPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = true
        adsPlacementEcmp.adsShowCb = adsShowCb
        nativeAdShow = ecmpShow.adObject as? NativeAd
        adsEcmpShow = ecmpShow
        adsEcmpShow?.flagShow = 0
        if (nativeAdShow == null) {
            LogUtil.logD("${tag(placement)} show ad not NativeAd")
            ecmpShow.isLoaded = false
            ecmpShow.adObject = null
            adsPlacementEcmp.isShowing = false
            adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "nativeAd null")
            return false
        }
        // mActivity có thể null/chết nếu host huỷ giữa lúc nạp và show (showWidthDialog cần host sống).
        if (!NtFullDialogHost.isUsable(mActivity)) {
            LogUtil.logD("${tag(placement)} show activity null")
            // Trong vòng show, AdsHelper giữ onAdShowFail này tới cuối vòng (WaterfallShowFailHold): lý do phải tới server ở đây.
            LogEventServer.logAdShowFailWithReason(placement = placement, reason = Constants.AdShowFailReason.SHOW_WHEN_ACT_NULL)
            destroyAd(nativeAdShow)
            nativeAdShow = null
            ecmpShow.isLoaded = false
            ecmpShow.adObject = null
            adsPlacementEcmp.isShowing = false
            adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", Constants.AdShowFailReason.SHOW_WHEN_ACT_NULL)
            return false
        }
        val shown = showWidthDialog()
        ecmpShow.isLoaded = false
        ecmpShow.adObject = null
        return shown
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdWithinMaxAge()
        val ad = ecmpShow?.adObject
        if (ecmpShow == null || ad == null) {
            LogUtil.logD("${tag(placement)} getAdForShow not loaded")
            LogEventServer.logAdShowFailWithReason(placement = placement, reason = Constants.AdShowFailReason.SHOW_NOT_LOADED)
            return null
        }
        if (preparedNativeAd === ad) discardPreparedDialog() // huỷ view dựng sẵn TRƯỚC khi quảng cáo gắn view mới
        setGetPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = false
        adsGetCb = adsShowCb
        adsEcmpGet = ecmpShow
        adsEcmpGet?.flagShow = 1
        synchronized(getTurnAds) { getTurnAds += ad }
        ecmpShow.isLoaded = false
        ecmpShow.adObject = null
        val adNative = ad as? NativeAd
        if (adNative == null) {
            LogUtil.logD("${tag(placement)} getAdForShow ad not NativeAd")
            return null
        }
        val adView: View? = AdsMobNtFullBinder.populateNativeAdView(mContext, adNative)
        if (adView == null) {
            LogUtil.logD("${tag(placement)} getAdForShow not created view")
            return null
        }
        LogUtil.logD("${tag(placement)} getAdForShow")
        val obRe = ObjectForShowAdWithViewParent()
        obRe.viewCurrAttach = adView
        obRe.adsEcmp = ecmpShow
        obRe.adLoad = ad
        return obRe
    }

    private fun showWidthDialog(): Boolean {
        val ad = nativeAdShow
        if (ad == null) {
            LogUtil.logD("${tag(showPl)} show faild ad null")
            hideNativeFull()
            adsPlacementEcmp.adsShowCb?.onAdShowFail(showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "nativeAd null")
            return false
        }
        val host = mActivity
        if (host == null || !NtFullDialogHost.isUsable(host)) {
            LogUtil.logD("${tag(showPl)} show activity dead")
            LogEventServer.logAdShowFailWithReason(placement = showPl, reason = Constants.AdShowFailReason.ACT_DESTROY)
            hideNativeFull()
            adsPlacementEcmp.isShowing = false
            adsPlacementEcmp.adsShowCb?.onAdShowFail(showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, Constants.AdShowFailReason.ACT_DESTROY)
            return false
        }
        // Dùng lại dialog dựng sẵn chỉ khi cùng quảng cáo, cùng cờ delay VÀ cùng Activity (window token của Activity tạo nó).
        val reusable = preparedDialog?.takeIf { preparedNativeAd === ad && preparedIsDelay == mIsDelay && preparedActivity === host }
        // Huỷ view cũ TRƯỚC khi dialog mới gắn cùng quảng cáo.
        if (reusable == null) discardPreparedDialog()
        prepareGen++ // dialog dựng sẵn đã dùng/bỏ: lần dựng còn xếp hàng không dựng thêm
        preparedDialog = null
        preparedNativeAd = null
        preparedActivity = null
        val dialog = reusable ?: AdsMobNtFullView(host, ad, timeShowBtClose, mIsDelay, true)
        mDialogNativeFull = dialog
        AppPluginManager.instance.registerMyAppLifecycle(this)
        dialog.setOnDismissListener {
            LogUtil.logD("${tag(showPl)} ondissmiss")
            dispatchDismissOnce(dialog)
        }
        val shown = NtFullDialogHost.show(dialog) { e ->
            LogUtil.logE("ads full nt admob$adsType $showPl show failed: ${e.message}")
            LogEventServer.logAdShowFailWithReason(placement = showPl, reason = "${Constants.AdShowFailReason.ACT_DESTROY}_${e.javaClass.simpleName}")
            AppPluginManager.instance.unRegisterMyAppLifecycle(this)
            hideNativeFull()
            adsPlacementEcmp.isShowing = false
            adsPlacementEcmp.adsShowCb?.onAdShowFail(showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, Constants.AdShowFailReason.ACT_DESTROY)
        }
        if (!shown) return false
        adsPlacementEcmp.adsShowCb?.onAdShowed(showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addBg4Ad(mActivity)
        }
        return true
    }

    fun hideNativeFull() {
        discardPreparedDialog()
        adsEcmpShow?.isLoaded = false
        adsEcmpShow?.adObject = null
        nativeAdShow?.destroy()
        nativeAdShow = null
        // Huỷ đếm ngược để không giữ View/Context khi dialog bị bỏ sớm.
        mDialogNativeFull?.cancelCountdown()
        mDialogNativeFull = null
    }

    /**
     * Dọn và phát onAdDismissed cho [dialog] đúng một lần (listener dismiss và [clearActivity] cùng vào đây; message DISMISS
     * đã gửi vẫn tới sau `setOnDismissListener(null)`). Bỏ mDialogNativeFull TRƯỚC mọi bước khác (Legacy).
     */
    private fun dispatchDismissOnce(dialog: AdsMobNtFullView) {
        if (mDialogNativeFull !== dialog) {
            LogUtil.logD("${tag(showPl)} dismiss already dispatched, skip")
            return
        }
        mDialogNativeFull = null
        dialog.cancelCountdown()
        AppPluginManager.instance.unRegisterMyAppLifecycle(this)
        val ad = nativeAdShow // C1b: quảng cáo của lượt đang đóng, đọc trước khi hideNativeFull bỏ nó
        hideNativeFull()
        hideBlackSafeAreaView()
        adsPlacementEcmp.isShowing = false
        adsPlacementEcmp.adsShowCb?.onAdDismissed(turnPl(ad, showPl), adsPlacementEcmp.originPl, adsEcmpShow?.adsId)
    }

    /**
     * Activity chủ bị huỷ khi dialog đang hiện: window bị gỡ mà dismiss không chạy → đóng ở đây, đúng một onAdDismissed qua
     * [dispatchDismissOnce]. super.clearActivity() trước (mActivity null trước khi phát dismiss). Lớp phủ và dialog dựng sẵn
     * (kể cả lần dựng còn xếp hàng: [discardPreparedDialog] tăng [prepareGen]) luôn được nhả (Legacy, NtFullHostDestroyTest).
     */
    override fun clearActivity() {
        super.clearActivity()
        hideBlackSafeAreaView()
        discardPreparedDialog()
        val dialog = mDialogNativeFull ?: return
        LogUtil.logD("${tag(showPl)} host destroyed while showing, dismissing")
        dialog.setOnDismissListener(null)
        runCatching { dialog.dismiss() }
            .onFailure { LogUtil.logE("ads full nt admob$adsType $showPl host-destroy dismiss failed: ${it.message}") }
        dispatchDismissOnce(dialog)
    }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        val native = ad as? NativeAd ?: return false
        native.destroy()
        return true
    }

    var lastClickTime: Long = 0
    override fun onActivityResume(ac: Activity?) {
        if (SystemClock.elapsedRealtime() - lastClickTime < 1000L) return
        lastClickTime = SystemClock.elapsedRealtime()
        if (mIsDelay) {
            mIsDelay = false
            mDialogNativeFull?.reCount()
        }
    }

    // Lớp phủ safe-area ở AdsMobNtFullBackground (2.0.0 C1a); giữ hai hàm này để chữ ký JVM (api dump) không đổi.
    @RequiresApi(api = Build.VERSION_CODES.P)
    fun addBg4Ad(ac: Activity?) = background.addBg4Ad(ac)

    fun hideBlackSafeAreaView() = background.hideBlackSafeAreaView()

    internal companion object {
        private const val FORMAT = "native_full"

        /** Seam test: `NativeAdLoader.load` tĩnh của Next-Gen (không Context) [S13]. */
        @Volatile internal var loader: (NativeAdRequest, NativeAdLoaderCallback) -> Unit = { r, cb -> NativeAdLoader.load(r, cb) }
    }
}
