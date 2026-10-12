package com.app.plugin.ads.adsfb

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
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
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdOptionsView
import com.facebook.ads.MediaView
import com.facebook.ads.NativeAd
import com.facebook.ads.NativeAdBase
import com.facebook.ads.NativeAdLayout
import com.facebook.ads.NativeAdListener


internal class AdsFbPlNtFull(context: Context, adsBase: AdsBase, type: Int, pl: String) : AdsPlBaseFull(context, adsBase, type, pl),
    AppResumeIF {
    override val eventTag: String get() = "fb_nt_full"

    /** `fullscreen_max_age_minutes` của `ads_policy_android` (mặc định 60 phút), đọc mỗi lần xét tuổi. */
    override val loadedAdMaxAgeMs: Long get() = AdsPolicy.current.fullscreenMaxAgeMillis()

    private val timeShowBtClose = 3
    var mDialogNativeFull: AdsFbNtFullView? = null
    var adsEcmpShow: AdsEcmp? = null
    var nativeAdShow: NativeAd? = null
    var mIsDelay = false

    // Dialog built ahead of the show, behind the parent fullscreen ad; see AdsMobPlNtFull.
    private var preparedDialog: AdsFbNtFullView? = null
    private var preparedNativeAd: NativeAd? = null
    private var preparedActivity: Activity? = null

    var adsEcmpGet: AdsEcmp? = null
    var adsGetCb: AdsShowCb? = null
    private val getTurnAds = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>()) // PSH: native lượt get

    private lateinit var adfbmy: AdsFbMy
    private var adOptionsView: AdOptionsView? = null
    private var adChoicesContainer: LinearLayout? = null

    override fun onInit() {
        adfbmy = AdsFbMy.instance
    }

    override fun tryLoad() {
        if (holdUntilReady(AdsFbMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads full nt fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false
                val loadGen = adsPlacementEcmp.loadGeneration

                var idLoad = ecmpLoad.adsId
                if (AdsFbMy.instance.flagPp?.compareTo("90351") == 0) {
                    idLoad = "VID_HD_9_16_39S_APP_INSTALL#$idLoad"
                }
                var nativeAd = NativeAd(mActivity, idLoad)
                val getTurns = getTurnAds // PSH: listener đọc qua biến cục bộ (không sinh access$ mới)
                nativeAd.loadAd(
                    nativeAd.buildLoadAdConfig().withAdListener(object : NativeAdListener {
                        /** 2.2.0 PSH (N07): đường (show/get) của lượt chính quảng cáo này, như AdMob 2.1.0 P3; không theo flagShow sống của slot. */
                        private fun isGetTurn() = synchronized(getTurns) { nativeAd in getTurns }

                        override fun onAdLoaded(p0: Ad?) {
                            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return }
                            adsPlacementEcmp.isLoading = false
                            adsPlacementEcmp.isloaded = true
                            ecmpLoad.isLoading = false
                            ecmpLoad.isLoaded = true
                            AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "native_full", "facebook", ecmpLoad.adsId, "facebook")
                            if (mActivity != null && mActivity!!.isDestroyed) {
                                nativeAd.destroy()
                            } else {
                                LogUtil.logD("ads full nt fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                ecmpLoad.adObject = nativeAd
                                val adidloaded = ecmpLoad.adsId
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, adidloaded
                                )
                                onFinishLoad()
                            }
                        }

                        override fun onError(ad: Ad?, adError: AdError?) {
                            if (isStaleLoadCallback(loadGen)) return
                            LogUtil.logD("ads full nt fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError?.errorCode}")
                            ecmpLoad.isLoading = false
                            ecmpLoad.adObject = null
                            adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                            AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "native_full", "facebook", ecmpLoad.adsId, "${adError?.errorCode}")
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
                                        "" + adError?.errorCode + " " + adError?.errorMessage
                                    )
                                }
                                onFinishLoad()
                            }
                        }

                        override fun onAdClicked(p0: Ad?) {
                            if (!isGetTurn()) {
                                LogUtil.logD("ads full nt fb$adsType $showPl ${adsPlacementEcmp.originPl} onAdClicked")
                                adsPlacementEcmp.adsShowCb?.onAdClick(
                                    turnPl(nativeAd, showPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                                mDialogNativeFull?.afterClick()
                            } else {
                                LogUtil.logD("ads full nt fb$adsType $getPl ${adsPlacementEcmp.originPl} onAdClicked")
                                adsGetCb?.onAdClick(
                                    turnPl(nativeAd, getPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                            }
                            AdsHelper.instance.onclickAd()
                        }

                        override fun onLoggingImpression(p0: Ad?) {
                            if (!isGetTurn()) {
                                LogUtil.logD("ads full nt fb$adsType $showPl ${adsPlacementEcmp.originPl} onAdImpression")
                                adsPlacementEcmp.adsShowCb?.onAdImpression(
                                    turnPl(nativeAd, showPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                                PaidEvent.metaDirect(turnPl(nativeAd, showPl), adsPlacementEcmp.originPl, "native_full", idLoad, "facebook")
                            } else {
                                LogUtil.logD("ads full nt fb$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression")
                                adsGetCb?.onAdImpression(
                                    turnPl(nativeAd, getPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                                PaidEvent.metaDirect(turnPl(nativeAd, getPl), adsPlacementEcmp.originPl, "native_full", idLoad, "facebook")
                            }
                        }

                        override fun onMediaDownloaded(p0: Ad?) {
                        }

                    }).build()
                )
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "native_full", "facebook", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads full nt fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads full nt fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads full nt fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    /**
     * Inflates and populates the Full2 dialog behind the parent ad, as AdsMobPlNtFull does.
     * registerViewForInteraction runs here without a window: Meta counts an impression only once
     * the ad is displayed, and registering early also starts its media download sooner.
     */
    override fun prepareChainedShow() {
        if (preparedDialog != null) return
        val activity = mActivity ?: return
        val ecmp = loadedAdWithinMaxAge() ?: return
        val ad = ecmp.adObject as? NativeAd ?: return
        try {
            val dialog = AdsFbNtFullView(activity, ad, timeShowBtClose, false, true)
            dialog.create()
            preparedDialog = dialog
            preparedNativeAd = ad
            preparedActivity = activity
            LogUtil.logD("ads full nt fb$adsType prepared chained dialog")
        } catch (e: Throwable) {
            // Pre-building is an optimisation; failing it must never cost the impression.
            LogUtil.logD("ads full nt fb$adsType prepare failed: ${e.message}")
            discardPreparedDialog()
        }
    }

    override fun cancelChainedShow() {
        if (preparedDialog != null) {
            LogUtil.logD("ads full nt fb$adsType discarding unused prepared dialog")
        }
        discardPreparedDialog()
    }

    private fun discardPreparedDialog() {
        preparedDialog?.let {
            runCatching { it.cancelCountdown() }
                .onFailure { e -> LogUtil.logE("ads full nt fb$adsType discardPreparedDialog failed: ${e.message}") }
        }
        // The ad stays loaded for a later show, which registers it against its own views.
        preparedNativeAd?.let {
            runCatching { it.unregisterView() }
                .onFailure { e -> LogUtil.logE("ads full nt fb$adsType discardPreparedDialog unregisterView failed: ${e.message}") }
        }
        preparedDialog = null
        preparedNativeAd = null
        preparedActivity = null
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        val ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null) {
            LogUtil.logD("ads full nt fb$adsType $placement ${adsPlacementEcmp.originPl} show")
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
                LogUtil.logD("ads full nt fb$adsType $placement ${adsPlacementEcmp.originPl} show ad not NativeAd")
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "nativeAd null")
                return false
            }
            // BUG-FIX: mActivity có thể null nếu activity đã destroy giữa lúc load và show (showWidthDialog dùng mActivity!!)
            if (!NtFullDialogHost.isUsable(mActivity)) {
                LogUtil.logD("ads full nt fb$adsType $placement ${adsPlacementEcmp.originPl} show activity null")
                // Trong vòng show, AdsHelper giữ onAdShowFail này tới cuối vòng (WaterfallShowFailHold):
                // lý do phải tới server ở đây.
                LogEventServer.logAdShowFailWithReason(
                    placement = placement,
                    reason = Constants.AdShowFailReason.SHOW_WHEN_ACT_NULL
                )
                nativeAdShow?.destroy()
                nativeAdShow = null
                ecmpShow.isLoaded = false
                ecmpShow.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "activity null")
                return false
            }
            return showWidthDialog()
        } else {
            LogUtil.logD("ads full nt fb$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null) {
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
            val nativeAdCast = ad as? NativeAd
            if (nativeAdCast == null) {
                LogUtil.logD("ads full nt fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow ad not NativeAd")
                return null
            }
            var adView:View? = populateNativeAdView(nativeAdCast)
            if (adView != null) {
                LogUtil.logD("ads full nt fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow")
                var obRe = ObjectForShowAdWithViewParent()
                obRe.viewCurrAttach = adView
                obRe.adsEcmp = ecmpShow
                obRe.adLoad = ad
                return obRe
            } else {
                LogUtil.logD("ads full nt fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not created view")
                return null
            }
        } else {

            LogUtil.logD("ads full nt fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not loaded")
            return null
        }
    }

    // Inflated detached on purpose: the view is returned and the caller adds it to whichever
    // container shows the ad, which does not exist yet at this point.
    @android.annotation.SuppressLint("InflateParams")
    private fun populateNativeAdView(nativeAd: NativeAd): View? {
        nativeAd.unregisterView()
        val nativeAdLayout =
            LayoutInflater.from(mContext).inflate(R.layout.appplugin_fb_native_layout_full, null, false)
        // BUG-FIX: gán adChoicesContainer cho layout MỚI trước khi addView, tránh add AdOptionsView
        // vào container CŨ (đã bị thay thế) khiến icon Ad Choices không hiện trên ad đang hiển thị
        adChoicesContainer = nativeAdLayout.findViewById<LinearLayout>(R.id.fb_ad_choices_container)
        if (adChoicesContainer != null) {
            adOptionsView = AdOptionsView(mContext, nativeAd, nativeAdLayout as NativeAdLayout)
            adChoicesContainer?.removeAllViews()
            adChoicesContainer?.addView(adOptionsView, 0)
        }
        val nativeAdIcon = nativeAdLayout.findViewById<MediaView>(R.id.fb_native_ad_icon)
        val nativeAdTitle = nativeAdLayout.findViewById<TextView>(R.id.fb_native_ad_title)
        val nativeAdBody = nativeAdLayout.findViewById<TextView>(R.id.fb_native_ad_body)
        val sponsoredLabel =
            nativeAdLayout.findViewById<TextView>(R.id.fb_native_ad_sponsored_label)
        val nativeAdSocialContext =
            nativeAdLayout.findViewById<TextView>(R.id.fb_native_ad_social_context)
        val nativeAdCallToAction =
            nativeAdLayout.findViewById<Button>(R.id.fb_native_ad_call_to_action)
        val nativeAdMedia = nativeAdLayout.findViewById<MediaView>(R.id.fb_native_ad_media)

        // Setting the Text
        nativeAdSocialContext.text = nativeAd.adSocialContext
        if (nativeAd.hasCallToAction()) {
            nativeAdCallToAction.visibility = View.VISIBLE
            nativeAdCallToAction.text = nativeAd.adCallToAction
        } else {
            nativeAdCallToAction.visibility = View.GONE
        }
        nativeAdTitle.text = nativeAd.advertiserName
        nativeAdBody.text = nativeAd.adBodyText
        sponsoredLabel.setText(R.string.appplugin_ad_sponsored)

        // You can use the following to specify the clickable areas.
        val clickableViews: MutableList<View> = ArrayList()
        clickableViews.add(nativeAdIcon)
        clickableViews.add(nativeAdMedia)
        clickableViews.add(nativeAdCallToAction)
        nativeAd.registerViewForInteraction(
            nativeAdLayout, nativeAdMedia, nativeAdIcon, clickableViews
        )

        // Optional: tag views
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdIcon, NativeAdBase.NativeComponentTag.AD_ICON
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdTitle, NativeAdBase.NativeComponentTag.AD_TITLE
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdBody, NativeAdBase.NativeComponentTag.AD_BODY
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdSocialContext, NativeAdBase.NativeComponentTag.AD_SOCIAL_CONTEXT
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdCallToAction, NativeAdBase.NativeComponentTag.AD_CALL_TO_ACTION
        )
        return nativeAdLayout
    }

    private fun showWidthDialog(): Boolean {
        if (nativeAdShow != null) {
            val host = mActivity
            if (!NtFullDialogHost.isUsable(host)) {
                LogUtil.logD("ads full nt fb$adsType $showPl ${adsPlacementEcmp.originPl} show activity dead")
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
            val dialog = reusable ?: AdsFbNtFullView(host!!, nativeAdShow!!, timeShowBtClose, false, true)
            mDialogNativeFull = dialog
            AppPluginManager.instance.registerMyAppLifecycle(this)
            dialog.setOnDismissListener {
                LogUtil.logD("ads full nt fb$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
                dispatchDismissOnce(dialog)
            }
            val shown = NtFullDialogHost.show(dialog) { e ->
                LogUtil.logE("ads full nt fb$adsType $showPl show failed: ${e.message}")
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
            LogUtil.logD("ads full nt fb$adsType $showPl ${adsPlacementEcmp.originPl} show faild ad null")
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
        if (nativeAdShow != null) {
            nativeAdShow?.destroy()
            nativeAdShow = null
        }
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
    private fun dispatchDismissOnce(dialog: AdsFbNtFullView) {
        if (mDialogNativeFull !== dialog) {
            LogUtil.logD("ads full nt fb$adsType $showPl dismiss already dispatched, skip")
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
        LogUtil.logD("ads full nt fb$adsType $showPl host destroyed while showing, dismissing")
        dialog.setOnDismissListener(null)
        runCatching { dialog.dismiss() }
            .onFailure { LogUtil.logE("ads full nt fb$adsType $showPl host-destroy dismiss failed: ${it.message}") }
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
