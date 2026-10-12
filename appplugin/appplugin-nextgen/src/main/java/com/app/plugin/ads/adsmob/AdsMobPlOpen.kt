package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseFull
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback

/**
 * 2.4.0 NG3 (Hợp đồng B): app open AdMob trên GMA Next-Gen. `AppOpenAd.load(AdRequest, AdLoadCallback)` không nhận
 * Context [S18] (cờ `GmaLoadContext` vô hiệu ở nextgen, `gma-gioi-han`); mọi callback tới trên luồng nền [S5] và về main qua
 * [MainPost] (`load`, `appOpenEvents`); doanh thu qua `onAdPaid` của event callback [S24]. Luồng/log như Legacy; hết hạn 4 h giữ.
 */
internal class AdsMobPlOpen(context: Context, adsBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseFull(context, adsBase, type, pl) {
    override val eventTag: String get() = "mob_open"

    /** `app_open_max_age_minutes` của `ads_policy_android` (mặc định 4 giờ), đọc mỗi lần xét tuổi. */
    override val loadedAdMaxAgeMs: Long get() = AdsPolicy.current.appOpenMaxAgeMillis()

    override val loadedAdExpiredReason: String get() = Constants.AdShowFailReason.APP_OPEN_EXPIRED

    override fun onInit() {
    }

    override fun tryLoad() {
        if (!AdsMobMy.instance.isSdkInitialized) {
            adsPlacementEcmp.isLoading = true
            AdsMobMy.instance.runWhenSdkReady(unlessLoadAbandoned { tryLoad() })
            return
        }
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad == null) {
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null")
            return
        }
        if (ecmpLoad.isLoaded || ecmpLoad.isLoading) {
            if (ecmpLoad.isLoaded) {
                LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId)
            } else {
                LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "isloading")
            }
            return
        }
        if (!ecmpLoad.adsId.startsWith("ca-app")) {
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad idload not correct")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "idload not correct")
            return
        }
        if (mActivity == null) {
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad activity null")
            adsPlacementEcmp.isLoading = false
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "activity null")
            return
        }
        LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
        adsPlacementEcmp.isLoading = true
        adsPlacementEcmp.isHasLoad = true
        ecmpLoad.isLoading = true
        ecmpLoad.isLoaded = false
        loader(AdRequestFactory.request(mContext, ecmpLoad.adsId), MainPost.load(LoadCb(ecmpLoad, adsPlacementEcmp.loadGeneration)))
        AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId)
    }

    /** Kết quả nạp, trên main (qua [MainPost.load]). */
    private inner class LoadCb(private val ecmpLoad: AdsEcmp, private val loadGen: Int) : AdLoadCallback<AppOpenAd> {
        override fun onAdFailedToLoad(adError: LoadAdError) {
            if (isStaleLoadCallback(loadGen)) return
            val code = adError.code.value
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=$code")
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

        override fun onAdLoaded(ad: AppOpenAd) {
            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, ad); return }
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
            adsPlacementEcmp.isLoading = false
            adsPlacementEcmp.isloaded = true
            ecmpLoad.isLoading = false
            ecmpLoad.isLoaded = true
            ecmpLoad.adObject = ad
            adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ad.adUnitId)
            AdsHelper.instance.onAdLoaded(
                loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId, ad.getResponseInfo()?.adapterClassName ?: "source_null"
            )
            onFinishLoad()
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        val ecmpLoaded = loadedAdWithinMaxAge()
        if (ecmpLoaded == null) {
            // Quá 4 giờ thì null (quảng cáo đã bị bỏ): đi nhánh "show not loaded", không callback.
            LogUtil.logD("ads full open admob$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
        LogUtil.logD("ads full open admob$adsType $placement ${adsPlacementEcmp.originPl} show")
        setShowPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = true
        adsPlacementEcmp.adsShowCb = adsShowCb
        val ad = ecmpLoaded.adObject as? AppOpenAd ?: run {
            LogUtil.logD("ads full open admob$adsType $placement ${adsPlacementEcmp.originPl} show ad not AppOpenAd")
            release(ecmpLoaded)
            adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "ad null")
            return false
        }
        ad.adEventCallback = MainPost.appOpenEvents(Events(ad, ecmpLoaded, placement))
        val activity = mActivity
        if (activity == null) {
            LogUtil.logD("ads full open admob$adsType $placement ${adsPlacementEcmp.originPl} show activity null")
            release(ecmpLoaded)
            adsPlacementEcmp.adsShowCb?.onAdShowFail(
                placement, adsPlacementEcmp.originPl, "", Constants.AdShowFailReason.SHOW_WHEN_ACT_NULL
            )
            return false
        }
        ad.show(activity)
        return true
    }

    /** Sự kiện của lượt hiện, trên main (qua [MainPost.appOpenEvents]); `onAdPaid` thay `OnPaidEventListener` Legacy. */
    private inner class Events(private val ad: AppOpenAd, private val ecmp: AdsEcmp, private val placement: String) :
        AppOpenAdEventCallback {
        override fun onAdClicked() {
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} onclick")
            adsPlacementEcmp.adsShowCb?.onAdClick(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId)
            AdsHelper.instance.onclickAd()
        }

        override fun onAdDismissedFullScreenContent() {
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} ondissmiss")
            release(ecmp)
            adsPlacementEcmp.adsShowCb?.onAdDismissed(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId)
        }

        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
            val code = PaidEvent.showFailCode(fullScreenContentError)
            LogEventServer.logAdShowFailWithReason(placement = placement, reason = "${Constants.AdShowFailReason.FAIL_TO_SHOW_FULL_CONTENT_OPEN}_$code")
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} onshow fail")
            release(ecmp)
            adsPlacementEcmp.adsShowCb?.onAdShowFail(
                showPl, adsPlacementEcmp.originPl, ad.adUnitId, "$code-${fullScreenContentError.message}"
            )
        }

        override fun onAdImpression() {
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} onimpression id=${ecmp.adsId}")
            adsPlacementEcmp.adsShowCb?.onAdImpression(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId)
        }

        override fun onAdShowedFullScreenContent() {
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} onshowed")
            adsPlacementEcmp.adsShowCb?.onAdShowed(showPl, adsPlacementEcmp.originPl, ad.adUnitId, 0)
        }

        override fun onAdPaid(value: AdValue) {
            PaidEvent.admob(turnPl(ad, showPl), adsPlacementEcmp.originPl, FORMAT, ad.adUnitId, PaidEvent.adNetwork(ad.getResponseInfo()), value)
        }
    }

    private fun release(ecmp: AdsEcmp) {
        ecmp.isLoaded = false
        ecmp.adObject = null
        adsPlacementEcmp.isShowing = false
    }

    internal companion object {
        private const val FORMAT = "opened"

        /** Seam test: `AppOpenAd.load` tĩnh của Next-Gen (không Context). */
        @Volatile internal var loader: (AdRequest, AdLoadCallback<AppOpenAd>) -> Unit = { r, cb -> AppOpenAd.load(r, cb) }
    }
}
