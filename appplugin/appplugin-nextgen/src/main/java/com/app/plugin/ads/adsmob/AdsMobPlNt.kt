package com.app.plugin.ads.adsmob

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPlBaseNative
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.NativeMediaRatio
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * 2.4.0 NG4 (Hợp đồng B): native `nt_` AdMob trên GMA Next-Gen. `NativeAdLoader.load(NativeAdRequest, NativeAdLoaderCallback)`
 * [S13] (không Context) với `VideoOptions` + tỉ lệ media như Legacy; mọi callback load/sự kiện về main qua [MainPost]
 * (`nativeLoader`, `nativeEvents`); `NativeAdView.registerNativeAd(ad, mediaView)` chỉ chạy trên main qua [MainPost.run]
 * [S14] (Meta `registerViewForInteraction` là UiThread [S28]). Layout nextgen dùng lớp gốc Next-Gen, cùng id.
 */
internal class AdsMobPlNt(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseNative(context, adBase, type, pl) {
    override val eventTag: String get() = "mob_nt"

    private lateinit var admobmy: AdsMobMy

    override fun onInit() {
        admobmy = AdsMobMy.instance
    }

    private fun tag(pl: String) = "ads native admob$adsType $pl ${adsPlacementEcmp.originPl}"

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
                adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, ecmpLoad, this)
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
        val video = VideoOptions.Builder().setStartMuted(true).setCustomControlsRequested(true).build()
        loader(request(mContext, ecmpLoad.adsId, video, portrait), MainPost.nativeLoader(LoadCb(ecmpLoad, adsPlacementEcmp.loadGeneration)))
        AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId)
    }

    /** Kết quả nạp, trên main (qua [MainPost.nativeLoader]). */
    private inner class LoadCb(private val ecmpLoad: AdsEcmp, private val loadGen: Int) : NativeAdLoaderCallback {
        override fun onNativeAdLoaded(nativeAd: NativeAd) {
            nativeAd.adEventCallback = MainPost.nativeEvents(Events(nativeAd, ecmpLoad.adsId))
            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return }
            if (mActivity == null || mActivity?.isDestroyed == true || mActivity?.isFinishing == true) {
                nativeAd.destroy()
                adsPlacementEcmp.isLoading = false
                ecmpLoad.isLoading = false
                ecmpLoad.isLoaded = false
                ecmpLoad.adObject = null
                onFinishLoad()
                adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, "activity unavailable")
                return
            }
            adsPlacementEcmp.isLoading = false
            adsPlacementEcmp.isloaded = true
            ecmpLoad.isLoading = false
            ecmpLoad.isLoaded = true
            ecmpLoad.adObject = nativeAd
            AdsHelper.instance.onAdLoaded(
                loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId, nativeAd.getResponseInfo()?.adapterClassName ?: "source_null"
            )
            LogUtil.logD("${tag(loadPl)} tryLoad ok id=${ecmpLoad.adsId}")
            adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, ecmpLoad, this@AdsMobPlNt)
            onFinishLoad()
        }

        override fun onAdFailedToLoad(adError: LoadAdError) {
            if (isStaleLoadCallback(loadGen)) return
            val code = adError.code.value
            LogUtil.logD("${tag(loadPl)} tryLoad fail id=${ecmpLoad.adsId} err=${adError.message}")
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

    /** Sự kiện của quảng cáo đã nạp, trên main (qua [MainPost.nativeEvents]); `onAdPaid` thay `OnPaidEventListener` Legacy. */
    private inner class Events(private val ad: NativeAd, private val adsId: String) : NativeAdEventCallback {
        override fun onAdImpression() {
            LogUtil.logD("${tag(getPl)} onAdImpression id=$adsId")
            adsPlacementEcmp.adsShowCb?.onAdImpression(turnPl(ad, getPl), adsPlacementEcmp.originPl, adsId)
        }

        override fun onAdClicked() {
            LogUtil.logD("${tag(getPl)} onAdClicked")
            adsPlacementEcmp.adsShowCb?.onAdClick(turnPl(ad, getPl), adsPlacementEcmp.originPl, adsId)
            AdsHelper.instance.onclickAd()
        }

        override fun onAdSwipeGestureClicked() {
            LogUtil.logD("${tag(getPl)} onAdSwipeGestureClicked")
        }

        override fun onAdPaid(value: AdValue) {
            PaidEvent.admob(turnPl(ad, getPl), adsPlacementEcmp.originPl, FORMAT, adsId, PaidEvent.adNetwork(ad.getResponseInfo()), value)
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("${tag(placement)} show not use")
        return false
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdWithinMaxAge()   // 2.4.3 NATAGE: quá fullscreen_max_age_minutes ⇒ bỏ + ad_expired
        val ad = ecmpShow?.adObject
        if (ecmpShow == null || ad == null) {
            LogUtil.logD("${tag(placement)} getAdForShow not loaded")
            LogEventServer.logAdShowFailWithReason(placement = placement, reason = Constants.AdShowFailReason.SHOW_NOT_LOADED)
            return null
        }
        LogUtil.logD("${tag(placement)} getAdForShow")
        setGetPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = false
        ecmpShow.isLoaded = false
        ecmpShow.adObject = null
        val obRe = ObjectForShowAdWithViewParent()
        obRe.viewCurrAttach = (ad as? NativeAd)?.let { populateNativeAdView(it) }
        if (obRe.viewCurrAttach == null) {
            LogEventServer.logAdShowFailWithReason(placement = placement, reason = Constants.AdShowFailReason.VIEW_ATTACH_NULL)
        }
        obRe.adsEcmp = ecmpShow
        obRe.adLoad = ad
        obRe.type4Time = adsType
        obRe.placement = adsPlacementEcmp.originPl
        return obRe
    }

    override fun populateNativeAdView(ecmp: AdsEcmp): View? = (ecmp.adObject as? NativeAd)?.let { populateNativeAdView(it) }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        if (ad !is NativeAd) return false
        ad.destroy()
        return true
    }

    private fun populateNativeAdView(nativeAd: NativeAd): View? {
        val layout = AdsHelper.instance.getLayoutCustome4Native(if (adsPlacementEcmp.originPl == "nt_full_intro") -1001 else AdsIDDef.AdmobNative)
        val loview = inflater(mContext).inflate(layout, null, false)
        val adView: NativeAdView = loview.findViewById(R.id.ad_native_view_lo)
        val mediaView = adView.findViewById<View>(R.id.ad_media) as? MediaView   // 2.5.0 PLMM: vắng/khác kiểu ⇒ null, không crash
        if (mediaView == null) NativeMediaRatio.warnNoMediaView(mContext, layout)
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.priceView = adView.findViewById(R.id.ad_price)
        val custom = AdsHelper.instance.getNativeCustom(getPl)
        if (custom != null) adView.setBackgroundColor(custom.bgColor)
        (adView.headlineView as TextView?)?.text = nativeAd.headline
        adView.bodyView?.visibility = View.VISIBLE
        (adView.bodyView as TextView?)?.text = nativeAd.body ?: nativeAd.headline
        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button?)?.text = nativeAd.callToAction
            if (custom != null) adView.callToActionView?.setBackgroundColor(custom.actionColor)
        }
        val icon = nativeAd.icon
        if (icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as ImageView?)?.setImageDrawable(icon.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }
        if (nativeAd.price == null) {
            adView.priceView?.visibility = View.INVISIBLE
        } else {
            adView.priceView?.visibility = View.VISIBLE
            (adView.priceView as TextView?)?.text = nativeAd.price
        }
        MainPost.run { adView.registerNativeAd(nativeAd, mediaView) } // [S14][S28]: chỉ trên main
        return loview
    }

    internal companion object {
        private const val FORMAT = "native_custom"

        /** Seam test: `NativeAdLoader.load` tĩnh của Next-Gen (không Context). */
        @Volatile internal var loader: (NativeAdRequest, NativeAdLoaderCallback) -> Unit = { r, cb -> NativeAdLoader.load(r, cb) }

        /** Seam test: inflater của view native. */
        @Volatile internal var inflater: (Context) -> LayoutInflater = { LayoutInflater.from(it) }

        /**
         * `NativeAdRequest` (NATIVE) + `VideoOptions` + tỉ lệ media; `npa` lấy từ request của `AdRequestFactory.native` (một
         * nguồn quyết định consent, NG1) — extras Google của nó được chép sang.
         */
        internal fun request(context: Context, adUnitId: String, video: VideoOptions, portrait: Boolean): NativeAdRequest {
            val base = AdRequestFactory.native(context, adUnitId)
            val ratio = NativeMediaRatio.pick(NativeMediaRatio.forRequest(context, portrait), NativeAd.NativeMediaAspectRatio.ANY,   // 2.4.4 ASPECT
                NativeAd.NativeMediaAspectRatio.LANDSCAPE, NativeAd.NativeMediaAspectRatio.PORTRAIT)                       // mã 1/2/3 → enum Next-Gen
            val builder = NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE)).setVideoOptions(video)
                .setMediaAspectRatio(ratio)
            base.googleExtrasBundle?.let { builder.setGoogleExtrasBundle(it) }
            return builder.build()
        }
    }
}
