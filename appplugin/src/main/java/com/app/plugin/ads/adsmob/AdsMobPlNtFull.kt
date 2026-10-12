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
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.app.plugin.ads.GmaLoadContext
import com.app.plugin.ads.consent.AdRequestFactory
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

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

    // Dialog built ahead of the show, while the parent fullscreen ad still covers the screen.
    // Cleared the moment it is consumed or invalidated, so a stale one can never be shown
    // against a different native ad than the one it was populated with.
    private var preparedDialog: AdsMobNtFullView? = null
    private var preparedNativeAd: NativeAd? = null
    private var preparedIsDelay = false
    private var preparedActivity: Activity? = null

    var adsEcmpGet: AdsEcmp? = null
    var adsGetCb: AdsShowCb? = null
    private val getTurnAds = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>()) // P3: native lượt get

    private lateinit var admobmy: AdsMobMy
    private val background = AdsMobNtFullBackground() // lớp phủ safe-area (C1a: tách file)

    override fun onInit() {
        admobmy = AdsMobMy.instance
    }

    override fun tryLoad() {
        if (!admobmy.isSdkInitialized) {
            adsPlacementEcmp.isLoading = true
            admobmy.runWhenSdkReady(unlessLoadAbandoned { tryLoad() })
            return
        }
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                if (ecmpLoad.adsId.startsWith("ca-app")) {
                    if (mActivity == null) {
                        LogUtil.logD("ads full nt admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad activity null")
                        adsPlacementEcmp.isLoading = false
                        adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "activity null")
                        return
                    }
                    LogUtil.logD("ads full nt admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                    adsPlacementEcmp.isLoading = true
                    adsPlacementEcmp.isHasLoad = true
                    ecmpLoad.isLoading = true
                    ecmpLoad.isLoaded = false
                    val loadGen = adsPlacementEcmp.loadGeneration
                    val adOptionsBuilder: NativeAdOptions.Builder
                    val videoOptions =
                        VideoOptions.Builder().setStartMuted(false).setCustomControlsRequested(true)
                            .build()
                    // 2.4.4 ASPECT: mặc định ANY, `ads_cf_native_media_ratio` lùi được (NativeMediaRatio, Hợp đồng B).
                    val portrait = mActivity?.resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT
                    adOptionsBuilder = NativeAdOptions.Builder().setMediaAspectRatio(NativeMediaRatio.forRequest(mContext, portrait))
                    adOptionsBuilder.setVideoOptions(videoOptions)
                    var loadedNative: NativeAd? = null // C1a: click của AdLoader đọc lượt của native này qua turnPl
                    val getTurns = getTurnAds // P3: listener đọc qua biến cục bộ (không sinh access$ mới)
                    val adLoader = AdLoader.Builder(GmaLoadContext.forLoad(mActivity!!, mContext), ecmpLoad.adsId)
                        .forNativeAd(MainPost.nativeLoaded { nativeAd: NativeAd ->
                            loadedNative = nativeAd
                            // Final review IMP-1: paid listener gán trước guard stale, để quảng cáo tới muộn mà `keepLateAd` giữ (hiện sau
                            // đó) vẫn ghi doanh thu; quảng cáo bị bỏ/destroy thì không bao giờ hiện nên listener của nó không chạy.
                            val adidloaded = ecmpLoad.adsId
                            nativeAd.setOnPaidEventListener(MainPost.paid { adValue ->
                                val netName =
                                    AdsMobMy.instance.getAdnetName(nativeAd.responseInfo)
                                // Lượt show/get ghi placement của X trước khi X hiện (setShowPlacement/setGetPlacement): showPl chỉ là dự phòng.
                                PaidEvent.admob(
                                    turnPl(nativeAd, showPl), adsPlacementEcmp.originPl,
                                    "native_full",
                                    adidloaded,
                                    netName,
                                    adValue
                                )
                            })
                            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return@nativeLoaded }
                            adsPlacementEcmp.isLoading = false
                            adsPlacementEcmp.isloaded = true
                            ecmpLoad.isLoading = false
                            ecmpLoad.isLoaded = true
                            AdsHelper.instance.onAdLoaded(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                "native_full",
                                "admob",
                                ecmpLoad.adsId,
                                nativeAd.responseInfo?.mediationAdapterClassName ?: "source_null"
                            )
                            if (mActivity != null && mActivity!!.isDestroyed) {
                                nativeAd.destroy()
                            } else {
                                LogUtil.logD("ads full nt admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                ecmpLoad.adObject = nativeAd
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, adidloaded
                                )
                                onFinishLoad()
                            }
                        }).withAdListener(MainPost.adListener(object : AdListener() {
                            /** 2.1.0 P3 (M11) + 2.2.0 PSH (N07): đường (show/get) của lượt chính native này; flagShow hiện hành chỉ là dự phòng. */
                            private fun isGetTurn() = loadedNative?.let { synchronized(getTurns) { it in getTurns } } ?: (ecmpLoad.flagShow != 0)

                            override fun onAdLoaded() {
                                //LogUtil.logD("ads full nt admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok2 id=${ecmpLoad.adsId}")
                            }

                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                if (isStaleLoadCallback(loadGen)) return
                                LogUtil.logD("ads full nt admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.adObject = null
                                adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                                AdsHelper.instance.onAdLoadFail(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    "native_full",
                                    "admob",
                                    ecmpLoad.adsId,
                                    "${adError.code}"
                                )
                                if (!adsPlacementEcmp.flag4Load && adsPlacementEcmp.adEcmps?.idxCurrEcpm ?: 0 < adsPlacementEcmp.adEcmps?.list?.size ?: 0) {
                                    tryLoad()
                                } else {
                                    // Unconditionally, not only when flag4Load is clear: leaving it
                                    // set wedges the placement for the session, so the Full2 behind
                                    // the next parent ad is never reloaded (speakerclean).
                                    adsPlacementEcmp.isLoading = false
                                    if (!adsPlacementEcmp.flag4Load) {
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

                            override fun onAdOpened() {
                                if (ecmpLoad.flagShow == 0) {
                                    LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdOpened")
                                } else {
                                    LogUtil.logD("ads full nt admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdOpened")
                                }
                            }

                            override fun onAdImpression() {
                                if (!isGetTurn()) {
                                    LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdImpression id=${ecmpLoad.adsId}")
                                    adsPlacementEcmp.adsShowCb?.onAdImpression(
                                        turnPl(loadedNative, showPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                    )
                                } else {
                                    LogUtil.logD("ads full nt admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression show id=${ecmpLoad.adsId}")
                                    adsGetCb?.onAdImpression(
                                        turnPl(loadedNative, getPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                    )
                                }
                            }

                            override fun onAdSwipeGestureClicked() {
                                if (ecmpLoad.flagShow == 0) {
                                    LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdSwipeGestureClicked")
                                } else {
                                    LogUtil.logD("ads full nt admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdSwipeGestureClicked")
                                }
                            }

                            override fun onAdClicked() {
                                if (!isGetTurn()) {
                                    LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdClicked")
                                    adsPlacementEcmp.adsShowCb?.onAdClick(
                                        turnPl(loadedNative, showPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                    )
                                    mDialogNativeFull?.afterClick()
                                } else {
                                    LogUtil.logD("ads full nt admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdClicked")
                                    adsGetCb?.onAdClick(
                                        turnPl(loadedNative, getPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                    )
                                }
                                AdsHelper.instance.onclickAd()
                            }

                            override fun onAdClosed() {
                                if (ecmpLoad.flagShow == 0) {
                                    LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdClosed")
                                } else {
                                    LogUtil.logD("ads full nt admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdClosed")
                                }
                            }
                        })).withNativeAdOptions(adOptionsBuilder.build()).build()
                    adLoader.loadAd(AdRequestFactory.build(mContext))
                    AdsHelper.instance.onAdLoadStart(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        "native_full",
                        "admob",
                        ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads full nt admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad idload not correct")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "idload not correct"
                    )
                }
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads full nt admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads full nt admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads full nt admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    /**
     * Inflates and populates the Full2 dialog before it is asked to show.
     *
     * This format is rendered by the app, not the network, so doing it at show time ran after the
     * interstitial was already dismissed: the user watched the app for one layout pass before the
     * ad appeared. Dialog.create() runs onCreate without attaching a window, so the expensive half
     * happens behind the parent ad and show() is left with onStart - where the close-button
     * countdown starts, so the countdown still begins when the ad becomes visible.
     *
     * Peeks at the loaded ad rather than consuming it: show() still resolves its own ad and reuses
     * this dialog only when it resolves to the same object. A dialog dropped unused or failing to build has
     * its NativeAdView destroyed (the view holds the Activity through the ad); the ad itself is kept.
     */
    override fun prepareChainedShow() {
        if (preparedDialog != null) return
        val activity = mActivity ?: return
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
            // Pre-building is an optimisation; failing it must never cost the impression.
            LogUtil.logD("ads full nt admob$adsType prepare failed: ${e.message}")
            runCatching { dialog?.releaseNativeAdView() } // create() có thể ném sau setNativeAd: nhả view dở dang
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
        if (ecmpShow != null) {
            LogUtil.logD("ads full nt admob$adsType $placement ${adsPlacementEcmp.originPl} show")
            val fd = (flagShow and 1)
            mIsDelay = (fd == 1)
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            nativeAdShow = ecmpShow.adObject as? NativeAd
            adsEcmpShow = ecmpShow
            adsEcmpShow?.flagShow = 0
            if (nativeAdShow == null) {
                LogUtil.logD("ads full nt admob$adsType $placement ${adsPlacementEcmp.originPl} show ad not NativeAd")
                ecmpShow.isLoaded = false
                ecmpShow.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "nativeAd null")
                return false
            }
            // BUG-FIX: mActivity có thể null nếu activity đã destroy giữa lúc load và show (showWidthDialog dùng mActivity!!)
            if (!NtFullDialogHost.isUsable(mActivity)) {
                LogUtil.logD("ads full nt admob$adsType $placement ${adsPlacementEcmp.originPl} show activity null")
                // Trong vòng show, AdsHelper giữ onAdShowFail này tới cuối vòng (WaterfallShowFailHold):
                // lý do phải tới server ở đây.
                LogEventServer.logAdShowFailWithReason(
                    placement = placement,
                    reason = Constants.AdShowFailReason.SHOW_WHEN_ACT_NULL
                )
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
        } else {
            LogUtil.logD("ads full nt admob$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            LogEventServer.logAdShowFailWithReason(
                placement = placement,
                reason = Constants.AdShowFailReason.SHOW_NOT_LOADED_ON_SHOW
            )
            return false
        }
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null && ecmpShow.adObject != null) {
            if (preparedNativeAd === ecmpShow.adObject) discardPreparedDialog() // huỷ view dựng sẵn trước khi gắn lại
            setGetPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = false
            adsGetCb = adsShowCb
            adsEcmpGet = ecmpShow
            adsEcmpGet?.flagShow = 1
            ecmpShow.adObject?.let { synchronized(getTurnAds) { getTurnAds += it } }
            ecmpShow.isLoaded = false
            var ad = ecmpShow.adObject
            ecmpShow.adObject = null
            val adNative = ad as? NativeAd
            if (adNative == null) {
                LogUtil.logD("ads full nt admob$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow ad not NativeAd")
                return null
            }
            var adView: View? = AdsMobNtFullBinder.populateNativeAdView(mContext, adNative)
            if (adView != null) {
                LogUtil.logD("ads full nt admob$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow")
                var obRe = ObjectForShowAdWithViewParent()
                obRe.viewCurrAttach = adView
                obRe.adsEcmp = ecmpShow
                obRe.adLoad = ad
                return obRe
            } else {
                LogUtil.logD("ads full nt admob$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not created view")
                return null
            }
        } else {
            LogUtil.logD("ads full nt admob$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not loaded")
            LogEventServer.logAdShowFailWithReason(
                placement = placement,
                reason = Constants.AdShowFailReason.SHOW_NOT_LOADED
            )
            return null
        }
    }

    private fun showWidthDialog(): Boolean {
        if (nativeAdShow != null) {
            val host = mActivity
            if (!NtFullDialogHost.isUsable(host)) {
                LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} show activity dead")
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
            // Dùng lại dialog dựng sẵn chỉ khi cùng quảng cáo, cùng cờ delay VÀ cùng Activity: dialog
            // gắn window token của Activity tạo ra nó, Activity chết thì show() ném BadTokenException.
            val reusable = preparedDialog?.takeIf {
                preparedNativeAd === nativeAdShow && preparedIsDelay == mIsDelay && preparedActivity === host
            }
            // Huỷ view cũ TRƯỚC khi dialog mới gắn cùng ad: destroy() bỏ view hiện hành của engine GMA.
            if (reusable == null) discardPreparedDialog()
            preparedDialog = null
            preparedNativeAd = null
            preparedActivity = null
            val dialog = reusable ?: AdsMobNtFullView(host!!, nativeAdShow!!, timeShowBtClose, mIsDelay, true)
            mDialogNativeFull = dialog
            AppPluginManager.instance.registerMyAppLifecycle(this)
            dialog.setOnDismissListener {
                LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
                dispatchDismissOnce(dialog)
            }
            val shown = NtFullDialogHost.show(dialog) { e ->
                LogUtil.logE("ads full nt admob$adsType $showPl show failed: ${e.message}")
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
            LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} show faild ad null")
            hideNativeFull()
            adsPlacementEcmp.adsShowCb?.onAdShowFail(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "nativeAd null"
            )
            return false
        }
    }

    private fun reCount() {
        if (mDialogNativeFull != null) {
            mDialogNativeFull?.reCount()
        }
    }

    fun hideNativeFull() {
        discardPreparedDialog()
        adsEcmpShow?.isLoaded = false
        adsEcmpShow?.adObject = null
        if (nativeAdShow != null) {
            nativeAdShow?.destroy()
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
    private fun dispatchDismissOnce(dialog: AdsMobNtFullView) {
        if (mDialogNativeFull !== dialog) {
            LogUtil.logD("ads full nt admob$adsType $showPl dismiss already dispatched, skip")
            return
        }
        mDialogNativeFull = null
        // hideNativeFull chỉ huỷ đếm ngược qua mDialogNativeFull, đã bỏ ở trên: huỷ trực tiếp.
        dialog.cancelCountdown()
        AppPluginManager.instance.unRegisterMyAppLifecycle(this)
        val ad = nativeAdShow // C1b: quảng cáo của lượt đang đóng, đọc trước khi hideNativeFull bỏ nó
        hideNativeFull()
        hideBlackSafeAreaView()
        adsPlacementEcmp.isShowing = false
        adsPlacementEcmp.adsShowCb?.onAdDismissed(turnPl(ad, showPl), adsPlacementEcmp.originPl, adsEcmpShow?.adsId)
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
        LogUtil.logD("ads full nt admob$adsType $showPl host destroyed while showing, dismissing")
        dialog.setOnDismissListener(null)
        runCatching { dialog.dismiss() }
            .onFailure { LogUtil.logE("ads full nt admob$adsType $showPl host-destroy dismiss failed: ${it.message}") }
        dispatchDismissOnce(dialog)
    }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        if (ad != null) {
            if (ad is NativeAd) {
                var adNative: NativeAd = ad
                adNative.destroy()
                return true
            }
        }
        return false
    }

    //

    var lastClickTime: Long = 0
    override fun onActivityResume(ac: Activity?) {
        if (SystemClock.elapsedRealtime() - lastClickTime < 1000L) {
            return
        }
        lastClickTime = SystemClock.elapsedRealtime()
        if (mIsDelay) {
            mIsDelay = false
            reCount()
        }
    }

    // Lớp phủ safe-area ở AdsMobNtFullBackground (2.0.0 C1a); giữ hai hàm này để chữ ký JVM (api dump) không đổi.
    @RequiresApi(api = Build.VERSION_CODES.P)
    fun addBg4Ad(ac: Activity?) = background.addBg4Ad(ac)

    fun hideBlackSafeAreaView() = background.hideBlackSafeAreaView()
}
