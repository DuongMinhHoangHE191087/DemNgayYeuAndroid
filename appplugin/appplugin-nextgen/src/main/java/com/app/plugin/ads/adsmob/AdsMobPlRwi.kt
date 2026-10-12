package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseFull
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback

/**
 * 2.4.0 NG3 (Hợp đồng B): rewarded interstitial AdMob (1.9.0 Task 2) trên GMA Next-Gen. `RewardedInterstitialAd.load(AdRequest,
 * AdLoadCallback)` không nhận Context [S20] (cờ `GmaLoadContext` vô hiệu ở nextgen, `gma-gioi-han`); mọi callback tới trên luồng nền [S5] và về main qua
 * [MainPost] (`load`, `rwiEvents`, `earned`); doanh thu qua `onAdPaid` của event callback [S24]. Luồng/log như Legacy; thưởng trao đúng một lần ([RewardTurn], cả khi earned tới sau dismiss).
 */
internal class AdsMobPlRwi(context: Context, adsBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseFull(context, adsBase, type, pl) {
    override val eventTag: String get() = "mob_rwi"

    /** `fullscreen_max_age_minutes` của `ads_policy_android` (mặc định 60 phút), đọc mỗi lần xét tuổi. */
    override val loadedAdMaxAgeMs: Long get() = AdsPolicy.current.fullscreenMaxAgeMillis()

    override fun onInit() {
    }

    private fun tag(pl: String) = "ads rwi admob$adsType $pl ${adsPlacementEcmp.originPl}"

    override fun tryLoad() {
        if (!AdsMobMy.instance.isSdkInitialized) {
            adsPlacementEcmp.isLoading = true
            AdsMobMy.instance.runWhenSdkReady(unlessLoadAbandoned { tryLoad() })
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
            LogUtil.logD("${tag(loadPl)} tryLoad id incorrect")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "id incorrect")
            return
        }
        if (mActivity == null) {
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
        loader(AdRequestFactory.request(mContext, ecmpLoad.adsId), MainPost.load(LoadCb(ecmpLoad, adsPlacementEcmp.loadGeneration)))
        AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId)
    }

    /** Kết quả nạp, trên main (qua [MainPost.load]). */
    private inner class LoadCb(private val ecmpLoad: AdsEcmp, private val loadGen: Int) : AdLoadCallback<RewardedInterstitialAd> {
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

        override fun onAdLoaded(ad: RewardedInterstitialAd) {
            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, ad); return }
            LogUtil.logD("${tag(loadPl)} tryLoad ok id=${ecmpLoad.adsId}")
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
            LogUtil.logD("${tag(placement)} show not loaded")
            return false
        }
        val ad = ecmpLoaded.adObject as? RewardedInterstitialAd
        val activity = mActivity
        if (ad == null || activity == null) {
            LogUtil.logD("${tag(placement)} show ${if (ad == null) "ad not RewardedInterstitialAd" else "activity null"}")
            ecmpLoaded.isLoaded = false
            ecmpLoaded.adObject = null
            adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", if (ad == null) "ad null" else "activity null")
            return false
        }
        LogUtil.logD("${tag(placement)} show")
        setShowPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = true
        adsPlacementEcmp.adsShowCb = adsShowCb
        ecmpLoaded.flagShow = 0
        val turn = RewardTurn { (adsShowCb as? AdsGiftShowCb)?.onAdRewarded(turnPl(ad, placement), adsPlacementEcmp.originPl, ad.adUnitId) }
        ad.adEventCallback = MainPost.rwiEvents(Events(ad, ecmpLoaded, turn))
        ad.show(activity, MainPost.earned(OnUserEarnedRewardListener {
            LogUtil.logD("${tag(placement)} OnUserEarnedRewardListener")
            ecmpLoaded.flagShow = 1
            turn.earned()
        }))
        return true
    }

    /** Sự kiện của lượt hiện, trên main (qua [MainPost.rwiEvents]); `onAdPaid` thay `OnPaidEventListener` Legacy. */
    private inner class Events(private val ad: RewardedInterstitialAd, private val ecmp: AdsEcmp, private val turn: RewardTurn) :
        RewardedInterstitialAdEventCallback {
        override fun onAdClicked() {
            LogUtil.logD("${tag(showPl)} onclick")
            adsPlacementEcmp.adsShowCb?.onAdClick(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId)
            AdsHelper.instance.onclickAd()
        }

        override fun onAdDismissedFullScreenContent() {
            LogUtil.logD("${tag(showPl)} ondissmiss")
            release(ecmp)
            turn.dismissed()
            adsPlacementEcmp.adsShowCb?.onAdDismissed(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId)
        }

        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
            val code = PaidEvent.showFailCode(fullScreenContentError)
            LogUtil.logD("${tag(showPl)} onshow fail")
            release(ecmp)
            adsPlacementEcmp.adsShowCb?.onAdShowFail(
                showPl, adsPlacementEcmp.originPl, ad.adUnitId, "$code-${fullScreenContentError.message}"
            )
        }

        override fun onAdImpression() {
            LogUtil.logD("${tag(showPl)} onimpression")
            adsPlacementEcmp.adsShowCb?.onAdImpression(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId)
        }

        override fun onAdShowedFullScreenContent() {
            LogUtil.logD("${tag(showPl)} onshowed")
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
        private const val FORMAT = "rewarded_interstitial"

        /** Seam test: `RewardedInterstitialAd.load` tĩnh của Next-Gen (không Context). */
        @Volatile internal var loader: (AdRequest, AdLoadCallback<RewardedInterstitialAd>) -> Unit = { r, cb -> RewardedInterstitialAd.load(r, cb) }
    }
}
