package com.app.plugin.ads.adsmax

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.annotation.RequiresApi
import com.app.plugin.R
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseFull
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.AppResumeIF
import com.app.plugin.ads.BannerAttach
import com.app.plugin.ads.NtFullDialogHost
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import java.util.concurrent.atomic.AtomicBoolean

internal class AdsMaxPlNtFull(context: Context, adsBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseFull(context, adsBase, type, pl), AppResumeIF {
    override val eventTag: String get() = "max_nt_full"

    /** `fullscreen_max_age_minutes` của `ads_policy_android` (mặc định 60 phút), đọc mỗi lần xét tuổi. */
    override val loadedAdMaxAgeMs: Long get() = AdsPolicy.current.fullscreenMaxAgeMillis()

    private val timeShowBtClose = 3
    var mDialogNativeFull: AdsMaxNtFullView? = null
    var adsEcmpShow: AdsEcmp? = null
    var nativeAdShow: MaxAd? = null
    var mIsDelay = false

    // Dialog inflated ahead of the show, behind the parent fullscreen ad; see AdsMobPlNtFull.
    private var preparedDialog: AdsMaxNtFullView? = null
    private var preparedNativeAd: MaxAd? = null
    private var preparedActivity: Activity? = null

    var adsEcmpGet: AdsEcmp? = null
    var adsGetCb: AdsShowCb? = null

    private lateinit var admaxmy: AdsMaxMy
    private var nativeAdLoader: MaxNativeAdLoader? = null

    /**
     * Loader theo unit id của placement này: đổi mục thì dùng lại loader của unit đó, không dựng/destroy lại. Số loader
     * không vượt số id của danh sách (danh sách MAX chỉ đổi lúc dựng); trước Task 14 loader cũng không bao giờ bị destroy.
     */
    private val loaderByUnit = HashMap<String, MaxNativeAdLoader>()

    override fun onInit() {
        admaxmy = AdsMaxMy.instance
    }

    /** Mục waterfall mà đối tượng create-once đang gắn (unit id + instance AdsEcmp); listener chụp nó lúc dựng. */
    private var boundEcmp: AdsEcmp? = null

    /**
     * Cờ của listener đang gắn: đổi mục thì hạ cờ cũ, callback load của listener cũ (loader cũ vẫn giữ nó để click/doanh
     * thu tới được) bị bỏ. Listener đọc cờ chụp theo listener, không đọc field (không thêm accessor vào dump).
     */
    private var boundLive: AtomicBoolean? = null

    /** [AdsPlacementEcmpBase.loadGeneration] lúc gửi request gần nhất; listener của đối tượng đang gắn đọc từ field. */
    private var loadGen = 0

    override fun tryLoad() {
        if (holdUntilReady(AdsMaxMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads full nt max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false

                if (nativeAdLoader == null || boundEcmp !== ecmpLoad) {
                    // Mục khác (bước waterfall sau của danh sách nhiều id, hoặc parserEcpms đổi unit): trỏ sang loader của unit này
                    // (một loader mỗi unit) và gắn listener mới. Không gỡ listener, không destroy loader cũ: MAX đọc listener của loader
                    // lúc click/trả doanh thu, quảng cáo nó đã render có thể còn trên màn hình (review Task 14 I1). Callback load đã
                    // xếp hàng của listener cũ bị bỏ bằng cờ `live`.
                    boundLive?.set(false)
                    val live = AtomicBoolean(true).also { boundLive = it }
                    val lateTag = "ads full nt max$adsType ${adsPlacementEcmp.originPl}"
                    boundEcmp = ecmpLoad
                    nativeAdLoader = loaderByUnit.getOrPut(ecmpLoad.adsId) { MaxNativeAdLoader(ecmpLoad.adsId, mContext) }
                    nativeAdLoader?.setNativeAdListener(object : MaxNativeAdListener() {
                        override fun onNativeAdLoaded(
                            nativeAdView: MaxNativeAdView?, nativeAd: MaxAd
                        ) {
                            // Callback load của listener đã thay (loader cũ giữ listener, không gỡ): không thuộc request đang chờ; bỏ ad.
                            if (!live.get()) { LogUtil.logD("$lateTag late load callback of replaced loader dropped"); destroyAd(nativeAd); return }
                            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return }
                            adsPlacementEcmp.isLoading = false
                            adsPlacementEcmp.isloaded = true
                            ecmpLoad.isLoading = false
                            ecmpLoad.isLoaded = true
                            AdsHelper.instance.onAdLoaded(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                "native_full",
                                "applovin",
                                ecmpLoad.adsId,
                                nativeAd.networkName ?: "source_null"
                            )
                            if (mActivity != null && mActivity!!.isDestroyed) {
                                nativeAdLoader?.destroy(nativeAd)
                            } else {
                                LogUtil.logD("ads full nt max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                ecmpLoad.adObject = nativeAd
                                val adidloaded = ecmpLoad.adsId
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, adidloaded
                                )
                                onFinishLoad()
                            }
                        }

                        override fun onNativeAdLoadFailed(adUnitId: String, adError: MaxError) {
                            if (!live.get()) { LogUtil.logD("$lateTag late load callback of replaced loader dropped"); return }
                            if (isStaleLoadCallback(loadGen)) return
                            LogUtil.logD("ads full nt max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                            ecmpLoad.isLoading = false
                            ecmpLoad.adObject = null
                            adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                            AdsHelper.instance.onAdLoadFail(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                "native_full",
                                "applovin",
                                ecmpLoad.adsId,
                                "${adError.code}"
                            )
                            if (!adsPlacementEcmp.flag4Load && adsPlacementEcmp.adEcmps?.idxCurrEcpm ?: 0 < adsPlacementEcmp.adEcmps?.list?.size ?: 0) {
                                tryLoad()
                            } else {
                                if (!adsPlacementEcmp.flag4Load) {
                                    adsPlacementEcmp.isLoading = false
                                    adsPlacementEcmp.isloaded = false
                                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        "",
                                        "" + adError.code + " " + adError.message
                                    )
                                }
                                onFinishLoad()
                            }
                        }

                        override fun onNativeAdClicked(nativeAd: MaxAd) {
                            if (ecmpLoad.flagShow == 0) {
                                LogUtil.logD("ads full nt max$adsType $showPl ${adsPlacementEcmp.originPl} onAdClicked")
                                adsPlacementEcmp.adsShowCb?.onAdClick(
                                    showPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                                mDialogNativeFull?.afterClick()
                            } else {
                                LogUtil.logD("ads full nt max$adsType $getPl ${adsPlacementEcmp.originPl} onAdClicked")
                                adsGetCb?.onAdClick(
                                    getPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                            }
                            AdsHelper.instance.onclickAd()
                        }
                    })
                    nativeAdLoader?.setRevenueListener {
                        var pl = showPl
                        if (ecmpLoad.flagShow != 0) {
                            pl = getPl
                        }
                        PaidEvent.max(
                            pl, adsPlacementEcmp.originPl, "native_full", it.adUnitId, it.networkName, it.revenue, "USD"
                        )
                    }
                }
                loadGen = adsPlacementEcmp.loadGeneration
                nativeAdLoader?.loadAd()
                AdsHelper.instance.onAdLoadStart(
                    loadPl, adsPlacementEcmp.originPl, "native_full", "applovin", ecmpLoad.adsId
                )
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads full nt max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads full nt max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads full nt max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    /**
     * Inflates the Full2 dialog behind the parent ad, as AdsMobPlNtFull does. Only the inflation
     * happens here: render() waits for the dialog's onStart (AdsMaxNtFullView), because AppLovin
     * does not document whether render() already counts toward the impression.
     */
    override fun prepareChainedShow() {
        if (preparedDialog != null) return
        val activity = mActivity ?: return
        val loader = nativeAdLoader ?: return
        val ecmp = loadedAdWithinMaxAge() ?: return
        val ad = ecmp.adObject as? MaxAd ?: return
        try {
            val dialog = AdsMaxNtFullView(activity, loader, ad, timeShowBtClose, false, true)
            dialog.create()
            preparedDialog = dialog
            preparedNativeAd = ad
            preparedActivity = activity
            LogUtil.logD("ads full nt max$adsType prepared chained dialog")
        } catch (e: Throwable) {
            // Pre-building is an optimisation; failing it must never cost the impression.
            LogUtil.logD("ads full nt max$adsType prepare failed: ${e.message}")
            discardPreparedDialog()
        }
    }

    override fun cancelChainedShow() {
        if (preparedDialog != null) {
            LogUtil.logD("ads full nt max$adsType discarding unused prepared dialog")
        }
        discardPreparedDialog()
    }

    private fun discardPreparedDialog() {
        preparedDialog?.let {
            runCatching { it.cancelCountdown() }
                .onFailure { e -> LogUtil.logE("ads full nt max$adsType discardPreparedDialog failed: ${e.message}") }
        }
        preparedDialog = null
        preparedNativeAd = null
        preparedActivity = null
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        val ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null) {
            LogUtil.logD("ads full nt max$adsType $placement ${adsPlacementEcmp.originPl} show")
            val fd = (flagShow and 1)
            mIsDelay = (fd == 1)
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            nativeAdShow = ecmpShow.adObject as? MaxAd
            adsEcmpShow = ecmpShow
            adsEcmpShow?.flagShow = 0
            if (nativeAdShow == null) {
                LogUtil.logD("ads full nt max$adsType $placement ${adsPlacementEcmp.originPl} show ad not MaxAd")
                ecmpShow.isLoaded = false
                ecmpShow.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "nativeAd null")
                return false
            }
            // BUG-FIX: mActivity có thể null nếu activity đã destroy giữa lúc load và show (showWidthDialog dùng mActivity!!)
            if (!NtFullDialogHost.isUsable(mActivity)) {
                LogUtil.logD("ads full nt max$adsType $placement ${adsPlacementEcmp.originPl} show activity null")
                // Trong vòng show, AdsHelper giữ onAdShowFail này tới cuối vòng (WaterfallShowFailHold):
                // lý do phải tới server ở đây.
                LogEventServer.logAdShowFailWithReason(
                    placement = placement,
                    reason = Constants.AdShowFailReason.SHOW_WHEN_ACT_NULL
                )
                nativeAdLoader?.destroy(nativeAdShow)
                nativeAdShow = null
                ecmpShow.isLoaded = false
                ecmpShow.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "activity null")
                return false
            }
            return showWidthDialog()
        } else {
            LogUtil.logD("ads full nt max$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null && ecmpShow.adObject != null) {
            setGetPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = false
            adsGetCb = adsShowCb
            adsEcmpGet = ecmpShow
            adsEcmpGet?.flagShow = 1
            ecmpShow.isLoaded = false
            var ad = ecmpShow.adObject
            ecmpShow.adObject = null
            val maxAdCast = ad as? MaxAd
            if (maxAdCast == null) {
                LogUtil.logD("ads full nt max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow ad not MaxAd")
                return null
            }
            var adView: View? = populateNativeAdView(maxAdCast)
            if (adView != null) {
                LogUtil.logD("ads full nt max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow")
                var obRe = ObjectForShowAdWithViewParent()
                obRe.viewCurrAttach = adView
                obRe.adsEcmp = ecmpShow
                obRe.adLoad = ad
                return obRe
            } else {
                LogUtil.logD("ads full nt max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not created view")
                return null
            }
        } else {
            LogUtil.logD("ads full nt max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not loaded")
            return null
        }
    }

    private fun populateNativeAdView(nativeAd: MaxAd): View {
        val adView = createNativeAdView()
        nativeAdLoader?.render(adView, nativeAd)
        return adView
    }

    private fun createNativeAdView(): MaxNativeAdView {
        val binder = MaxNativeAdViewBinder.Builder(R.layout.appplugin_max_native_full)
            .setTitleTextViewId(R.id.ad_headline).setBodyTextViewId(R.id.ad_body)
            //.setStarRatingContentViewGroupId(R.id.ntcl_star_rating_view)
            //.setAdvertiserTextViewId(R.id.ntcl_advertiser)
            .setIconImageViewId(R.id.ad_app_icon).setMediaContentViewGroupId(R.id.ad_media)
            .setCallToActionButtonId(R.id.ad_call_to_action)
            //.setOptionsContentViewGroupId( R.id.options_view )
            .build()
        return MaxNativeAdView(binder, mContext)
    }

    private fun showWidthDialog(): Boolean {
        if (nativeAdShow != null) {
            val host = mActivity
            if (!NtFullDialogHost.isUsable(host)) {
                LogUtil.logD("ads full nt max$adsType $showPl ${adsPlacementEcmp.originPl} show activity dead")
                LogEventServer.logAdShowFailWithReason(
                    placement = showPl,
                    reason = Constants.AdShowFailReason.ACT_DESTROY
                )
                hideNativeFull()
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(
                    showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, Constants.AdShowFailReason.ACT_DESTROY
                )
                return false
            }
            // Dùng lại dialog dựng sẵn chỉ khi cùng quảng cáo VÀ cùng Activity: dialog gắn window
            // token của Activity tạo ra nó, Activity chết thì show() ném BadTokenException.
            val reusable = preparedDialog?.takeIf {
                preparedNativeAd === nativeAdShow && preparedActivity === host
            }
            if (reusable == null) discardPreparedDialog()
            preparedDialog = null
            preparedNativeAd = null
            preparedActivity = null
            val dialog = reusable
                ?: AdsMaxNtFullView(host!!, nativeAdLoader!!, nativeAdShow!!, timeShowBtClose, false, true)
            mDialogNativeFull = dialog
            AppPluginManager.instance.registerMyAppLifecycle(this)
            dialog.setOnDismissListener {
                LogUtil.logD("ads full nt max$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
                dispatchDismissOnce(dialog)
            }
            val shown = NtFullDialogHost.show(dialog) { e ->
                LogUtil.logE("ads full nt max$adsType $showPl show failed: ${e.message}")
                LogEventServer.logAdShowFailWithReason(
                    placement = showPl,
                    reason = "${Constants.AdShowFailReason.ACT_DESTROY}_${e.javaClass.simpleName}"
                )
                AppPluginManager.instance.unRegisterMyAppLifecycle(this)
                hideNativeFull()
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(
                    showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, Constants.AdShowFailReason.ACT_DESTROY
                )
            }
            if (!shown) return false
            adsPlacementEcmp.adsShowCb?.onAdShowed(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, 0
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addBg4Ad(mActivity)
            }
            return true
        } else {
            LogUtil.logD("ads full nt max$adsType $showPl ${adsPlacementEcmp.originPl} show faild ad null")
            hideNativeFull()
            adsPlacementEcmp.adsShowCb?.onAdShowFail(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "nativeAd null"
            )
            return false
        }
    }

    private fun reCount() {
        if (mDialogNativeFull != null) {
            mDialogNativeFull!!.reCount()
        }
    }

    fun hideNativeFull() {
        discardPreparedDialog()
        adsEcmpShow?.isLoaded = false
        adsEcmpShow?.adObject = null
        if (nativeAdLoader != null && nativeAdShow != null) {
            nativeAdLoader?.destroy(nativeAdShow)
            nativeAdShow = null
        }
        // BUG-FIX: cancel countdown handler để tránh leak View/Context khi dialog bị dismiss sớm
        mDialogNativeFull?.cancelCountdown()
        mDialogNativeFull = null
    }

    /**
     * Dọn dẹp và phát onAdDismissed cho [dialog], đúng một lần. Hai đường cùng gọi vào đây: listener
     * dismiss và [clearActivity]. Dialog.dismiss() gửi message DISMISS mang listener đang gắn, và
     * setOnDismissListener(null) sau đó không thu hồi message đã gửi: người dùng bấm đóng rồi host bị
     * huỷ trước khi message chạy thì listener cũ vẫn tới sau clearActivity. Bỏ mDialogNativeFull
     * TRƯỚC mọi bước khác nên lần gọi thứ hai (hoặc lần gọi cho một dialog cũ) dừng ở dòng đầu.
     */
    private fun dispatchDismissOnce(dialog: AdsMaxNtFullView) {
        if (mDialogNativeFull !== dialog) {
            LogUtil.logD("ads full nt max$adsType $showPl dismiss already dispatched, skip")
            return
        }
        mDialogNativeFull = null
        // hideNativeFull chỉ huỷ đếm ngược qua mDialogNativeFull, đã bỏ ở trên: huỷ trực tiếp.
        dialog.cancelCountdown()
        AppPluginManager.instance.unRegisterMyAppLifecycle(this)
        hideNativeFull()
        hideBlackSafeAreaView()
        adsPlacementEcmp.isShowing = false
        adsPlacementEcmp.adsShowCb?.onAdDismissed(showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId)
    }

    /**
     * Activity chủ bị huỷ (xoay màn hình, đổi theme, hệ thống thu hồi) trong lúc dialog đang hiện:
     * window bị hệ thống gỡ (WindowLeaked) mà Dialog.dismiss() không chạy, nên listener dismiss
     * không bao giờ tới và cờ flagAdsFullShowing của AdsHelper kẹt ở 1 (đo 2026-09-24). Đóng dialog
     * ở đây và phát đúng một onAdDismissed qua [dispatchDismissOnce], cùng đường với listener.
     *
     * AdsHelper nhả Activity của mọi mạng (lượt 1, [com.app.plugin.ads.AdsBase.releaseActivity])
     * trước khi lớp này dismiss (lượt 2, hàm này), nên reload từ bất kỳ callback nào cũng thất bại an
     * toàn ở nhánh mActivity == null của [com.app.plugin.ads.AdsPlBase.load]. super.clearActivity()
     * ở đầu giữ cùng bảo đảm cho placement này khi hàm được gọi riêng. Lớp phủ safe-area và dialog
     * dựng sẵn luôn được nhả, kể cả khi không có dialog đang hiện: cả hai giữ Activity này tới khi
     * process chết.
     */
    override fun clearActivity() {
        super.clearActivity()
        hideBlackSafeAreaView()
        discardPreparedDialog()
        val dialog = mDialogNativeFull ?: return
        LogUtil.logD("ads full nt max$adsType $showPl host destroyed while showing, dismissing")
        dialog.setOnDismissListener(null)
        runCatching { dialog.dismiss() }
            .onFailure { LogUtil.logE("ads full nt max$adsType $showPl host-destroy dismiss failed: ${it.message}") }
        dispatchDismissOnce(dialog)
    }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        if (ad != null && nativeAdLoader != null) {
            if (ad is MaxAd) {
                var adNative: MaxAd = ad
                nativeAdLoader?.destroy(adNative)
                return true
            }
        }
        return false
    }

    //
    override fun onActivityResume(ac: Activity?) {
        if (mIsDelay) {
            mIsDelay = false
            reCount()
        }
    }

    //======================================================
    private var parentBlackSafeAreaView: ViewGroup? = null
    private var blackSafeTop: View? = null
    private var blackSafeBottom: View? = null
    private var blackSafeLeft: View? = null
    private var blackSafeRight: View? = null
    private var sSafeTop = -1
    private var sSafeBottom: Int = -1
    private var sSafeLeft: Int = -1
    private var sSafeRight: Int = -1

    @RequiresApi(api = Build.VERSION_CODES.P)
    fun addBg4Ad(ac: Activity?) {
        if (sSafeTop < 0) {
            val lp = ac!!.window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            val windowInsets = ac.window.decorView.rootView.rootWindowInsets
            if (windowInsets != null) {
                val displayCutout = windowInsets.displayCutout
                if (displayCutout != null) {
                    sSafeTop = displayCutout.safeInsetTop
                    sSafeBottom = displayCutout.safeInsetBottom
                    sSafeLeft = displayCutout.safeInsetLeft
                    sSafeRight = displayCutout.safeInsetRight
                    Log.d("mysdk", "top=$sSafeTop")
                    Log.d("mysdk", "bottom=$sSafeBottom")
                    Log.d("mysdk", "left=$sSafeLeft")
                    Log.d("mysdk", "right=$sSafeRight")
                }
            }
        }
        if (parentBlackSafeAreaView == null && ac != null) {
            parentBlackSafeAreaView = RelativeLayout(ac)
            val bpl = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            ac.addContentView(parentBlackSafeAreaView, bpl)
        }
        if (parentBlackSafeAreaView != null) {
            parentBlackSafeAreaView!!.visibility = View.VISIBLE
            if (sSafeTop > 0) {
                if (blackSafeTop == null) {
                    blackSafeTop = View(ac)
                    val cpr =
                        RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sSafeTop)
                    cpr.addRule(
                        RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE
                    )
                    parentBlackSafeAreaView!!.addView(blackSafeTop, cpr)
                    blackSafeTop!!.setBackgroundColor(-0x1000000)
                }
                blackSafeTop!!.visibility = View.VISIBLE
            } else {
                if (blackSafeTop != null) {
                    blackSafeTop!!.visibility = View.INVISIBLE
                }
            }
            if (sSafeBottom > 0) {
                if (blackSafeBottom == null) {
                    blackSafeBottom = View(ac)
                    val cpr = RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, sSafeBottom
                    )
                    cpr.addRule(
                        RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE
                    )
                    parentBlackSafeAreaView!!.addView(blackSafeBottom, cpr)
                    blackSafeBottom!!.setBackgroundColor(-0x1000000)
                }
                blackSafeBottom!!.visibility = View.VISIBLE
            } else {
                if (blackSafeBottom != null) {
                    blackSafeBottom!!.visibility = View.INVISIBLE
                }
            }
            if (sSafeLeft > 0) {
                if (blackSafeLeft == null) {
                    blackSafeLeft = View(ac)
                    val cpr =
                        RelativeLayout.LayoutParams(sSafeLeft, ViewGroup.LayoutParams.MATCH_PARENT)
                    cpr.addRule(
                        RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE
                    )
                    parentBlackSafeAreaView!!.addView(blackSafeLeft, cpr)
                    blackSafeLeft!!.setBackgroundColor(-0x1000000)
                }
                blackSafeLeft!!.visibility = View.VISIBLE
            } else {
                if (blackSafeLeft != null) {
                    blackSafeLeft!!.visibility = View.INVISIBLE
                }
            }
            if (sSafeRight > 0) {
                if (blackSafeRight == null) {
                    blackSafeRight = View(ac)
                    val cpr =
                        RelativeLayout.LayoutParams(sSafeRight, ViewGroup.LayoutParams.MATCH_PARENT)
                    cpr.addRule(
                        RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE
                    )
                    parentBlackSafeAreaView!!.addView(blackSafeRight, cpr)
                    blackSafeRight!!.setBackgroundColor(-0x1000000)
                }
                blackSafeRight!!.visibility = View.VISIBLE
            } else {
                if (blackSafeRight != null) {
                    blackSafeRight!!.visibility = View.INVISIBLE
                }
            }
        }
    }

    /**
     * Gỡ lớp phủ safe-area khỏi content của host và bỏ mọi tham chiếu tới nó: lớp phủ được dựng
     * bằng context của Activity, giữ lại qua nhiều lần hiện thì giữ luôn Activity đầu tiên (heap
     * 2026-09-24) và sau khi xoay màn hình addBg4Ad dùng lại view của Activity đã chết. Lần hiện
     * sau addBg4Ad dựng lại trên host hiện tại.
     */
    fun hideBlackSafeAreaView() {
        val overlay = parentBlackSafeAreaView ?: return
        overlay.visibility = View.INVISIBLE
        BannerAttach.detach(overlay)
        parentBlackSafeAreaView = null
        blackSafeTop = null
        blackSafeBottom = null
        blackSafeLeft = null
        blackSafeRight = null
    }
}
