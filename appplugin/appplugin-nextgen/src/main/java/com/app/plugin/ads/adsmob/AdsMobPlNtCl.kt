package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View
import com.app.plugin.R
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlNtClBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.AppResumeIF
import com.app.plugin.ads.MainPost
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoController
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * 2.4.0 NG4 (Hợp đồng B): native collapsible (NtCl) AdMob trên GMA Next-Gen. Nạp như `AdsMobPlNt` (`NativeAdLoader`,
 * `VideoOptions` không tắt tiếng như Legacy) qua [MainPost.nativeLoader]; sự kiện qua [MainPost.nativeEvents]; gắn view bằng
 * [AdsMobNtClView.bind] (`registerNativeAd` chỉ trên main). Host huỷ / người dùng đóng: gỡ view, huỷ quảng cáo, báo dismissed.
 */
internal class AdsMobPlNtCl(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlNtClBase(context, adBase, type, pl), AppResumeIF {
    override val eventTag: String get() = "mob_nt_cl"

    var adsEcmpShow: AdsEcmp? = null
    var nativeAdShow: NativeAd? = null

    private lateinit var admobmy: AdsMobMy
    private var vcControlCurrShow: VideoController? = null

    override fun onInit() {
        admobmy = AdsMobMy.instance
    }

    private fun tag(pl: String) = "ads bnntcl admob$adsType $pl ${adsPlacementEcmp.originPl}"

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
        val video = VideoOptions.Builder().setStartMuted(false).setCustomControlsRequested(true).build()
        AdsMobPlNt.loader(AdsMobPlNt.request(mContext, ecmpLoad.adsId, video, portrait), MainPost.nativeLoader(LoadCb(ecmpLoad, adsPlacementEcmp.loadGeneration)))
        AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId)
    }

    /** Kết quả nạp, trên main (qua [MainPost.nativeLoader]). */
    private inner class LoadCb(private val ecmpLoad: AdsEcmp, private val loadGen: Int) : NativeAdLoaderCallback {
        override fun onNativeAdLoaded(nativeAd: NativeAd) {
            nativeAd.adEventCallback = MainPost.nativeEvents(Events(nativeAd, ecmpLoad.adsId))
            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return }
            adsPlacementEcmp.isLoading = false
            adsPlacementEcmp.isloaded = true
            ecmpLoad.isLoading = false
            ecmpLoad.isLoaded = true
            AdsHelper.instance.onAdLoaded(
                loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId, nativeAd.getResponseInfo()?.adapterClassName ?: "source_null"
            )
            if (mActivity?.isDestroyed == true) {
                nativeAd.destroy()
                return
            }
            LogUtil.logD("${tag(loadPl)} tryLoad ok id=${ecmpLoad.adsId}")
            ecmpLoad.adObject = nativeAd
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
            if (!adsPlacementEcmp.flag4Load) {
                adsPlacementEcmp.isLoading = false
                adsPlacementEcmp.isloaded = false
                adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "$code ${adError.message}")
            }
            onFinishLoad()
        }
    }

    /** Sự kiện của quảng cáo đã nạp, trên main (qua [MainPost.nativeEvents]). */
    private inner class Events(private val ad: NativeAd, private val adsId: String) : NativeAdEventCallback {
        override fun onAdImpression() {
            LogUtil.logD("${tag(showPl)} onAdImpression id=$adsId")
            adsPlacementEcmp.adsShowCb?.onAdImpression(turnPl(ad, showPl), adsPlacementEcmp.originPl, adsId)
        }

        override fun onAdClicked() {
            LogUtil.logD("${tag(showPl)} onAdClicked")
            adsPlacementEcmp.adsShowCb?.onAdClick(turnPl(ad, showPl), adsPlacementEcmp.originPl, adsId)
            AdsHelper.instance.onclickAd()
        }

        override fun onAdSwipeGestureClicked() {
            LogUtil.logD("${tag(showPl)} onAdSwipeGestureClicked")
        }

        override fun onAdPaid(value: AdValue) {
            PaidEvent.admob(turnPl(ad, showPl), adsPlacementEcmp.originPl, FORMAT, adsId, PaidEvent.adNetwork(ad.getResponseInfo()), value)
        }
    }

    /** Quảng cáo không dùng nữa (hiện chỉ [keepLateAd] gọi): huỷ như lớp `Nt` cùng mạng. */
    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        if (ad !is NativeAd) return false
        ad.destroy()
        return true
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        val ecmpShow = adsPlacementEcmp.adEcmps?.getAdLoaded()
        if (ecmpShow == null) {
            LogUtil.logD("${tag(placement)} show not loaded")
            adsShowCb?.onAdShowFail(loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null")
            return false
        }
        LogUtil.logD("${tag(placement)} show")
        setShowPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = true
        ecmpShow.isLoaded = false
        adsPlacementEcmp.adsShowCb = adsShowCb
        nativeAdShow = ecmpShow.adObject as? NativeAd
        ecmpShow.adObject = null
        adsEcmpShow = ecmpShow
        ecmpShow.flagShow = 0
        if (nativeAdShow == null) {
            LogUtil.logD("${tag(placement)} show ad not NativeAd")
            adsPlacementEcmp.isShowing = false
            adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "nativeAd null")
            return false
        }
        showWithView()
        return true
    }

    override fun hideAd(flagHide: Int) {
        super.hideAd(flagHide)
        adsEcmpShow?.isLoaded = false
        adsEcmpShow?.adObject = null
        nativeAdShow?.destroy()
        nativeAdShow = null
    }

    private fun showWithView() {
        val ad = nativeAdShow ?: run {
            LogUtil.logD("${tag(showPl)} show faild ad null")
            hideAd(0)
            adsPlacementEcmp.adsShowCb?.onAdShowFail(showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "nativeAd null")
            return
        }
        AppPluginManager.instance.registerMyAppLifecycle(this)
        addToParentView(creatClView(ad))
        adsPlacementEcmp.adsShowCb?.onAdShowed(showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, 1)
    }

    /** Người dùng đóng: gỡ view (AdsHelper), huỷ quảng cáo, báo dismissed với placement của lượt này. */
    private fun hideView() {
        val ad = nativeAdShow // C1b: quảng cáo của lượt đang đóng, đọc trước khi hideNativeCl/hideAd bỏ nó
        AppPluginManager.instance.unRegisterMyAppLifecycle(this)
        AdsHelper.instance.hideNativeCl()
        hideAd(0)
        LogUtil.logD("${tag(showPl)} ondissmiss")
        adsEcmpShow?.isLoaded = false
        adsEcmpShow?.adObject = null
        adsPlacementEcmp.isShowing = false
        adsPlacementEcmp.adsShowCb?.onAdDismissed(turnPl(ad, showPl), adsPlacementEcmp.originPl, adsEcmpShow?.adsId)
    }

    // Inflate rời có chủ ý: view trả về, người gọi gắn vào container hiện quảng cáo (chưa có lúc này).
    @android.annotation.SuppressLint("InflateParams")
    private fun creatClView(ad: NativeAd): View {
        getInfoView()
        val loview = AdsMobPlNt.inflater(mContext).inflate(R.layout.appplugin_admob_native_cl, null, false)
        val adView: NativeAdView = loview.findViewById(R.id.ntcl_ntadview)
        val views = AdsMobNtClView.bind(loview, ad, adView, mDensity, vcControlCurrShow) { hideView() }
        vcControlCurrShow = views.video
        loBtClose = views.loClose
        btClose = views.btClose
        return loview
    }

    override fun onActivityResume(ac: Activity?) {
    }

    private companion object {
        const val FORMAT = "native_collapse"
    }
}
