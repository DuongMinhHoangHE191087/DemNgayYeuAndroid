package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseFull
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.GmaLoadContext
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.RewardDeliveryGate
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

/**
 * Placement rewarded interstitial AdMob (1.9.0 Task 2). Cùng khung với `AdsMobPlrewarded` (mẫu, không sửa): chờ SDK
 * sẵn sàng, waterfall theo id, giữ quảng cáo tới muộn, tuổi tối đa `fullscreen_max_age_minutes` (60 phút), doanh thu qua
 * [PaidEvent] với định dạng `rewarded_interstitial`; thưởng trao tối đa một lần lúc đóng ([RewardDeliveryGate]).
 */
internal class AdsMobPlRwi(context: Context, adsBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseFull(context, adsBase, type, pl) {
    override val eventTag: String get() = "mob_rwi"

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
            LogUtil.logD("${tag(loadPl)} tryLoad ${if (ecmpLoad.isLoaded) "has loaded" else "is loading"}")
            if (ecmpLoad.isLoaded) {
                adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId)
            } else {
                adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "isloading")
            }
            return
        }
        if (!ecmpLoad.adsId.startsWith("ca-app")) {
            LogUtil.logD("${tag(loadPl)} tryLoad id incorrect")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "id incorrect")
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
        val loadGen = adsPlacementEcmp.loadGeneration
        RewardedInterstitialAd.load(
            GmaLoadContext.forLoad(activity, mContext),
            ecmpLoad.adsId,
            AdRequestFactory.build(mContext),
            MainPost.rwiLoad(object : RewardedInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    if (isStaleLoadCallback(loadGen)) return
                    LogUtil.logD("${tag(loadPl)} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                    ecmpLoad.isLoading = false
                    ecmpLoad.adObject = null
                    adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                    AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId, "${adError.code}")
                    val idx = adsPlacementEcmp.adEcmps?.idxCurrEcpm ?: 0
                    if (!adsPlacementEcmp.flag4Load && idx < (adsPlacementEcmp.adEcmps?.list?.size ?: 0)) {
                        tryLoad()
                        return
                    }
                    if (!adsPlacementEcmp.flag4Load) {
                        adsPlacementEcmp.isLoading = false
                        adsPlacementEcmp.isloaded = false
                        adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                            loadPl, adsPlacementEcmp.originPl, "", "${adError.code} ${adError.message}"
                        )
                    }
                    onFinishLoad()
                }

                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    // Như mẫu rewarded: paid listener gán trước guard stale (quảng cáo tới muộn được giữ vẫn ghi doanh thu).
                    ad.onPaidEventListener = MainPost.paid { adValue ->
                        val netName = AdsMobMy.instance.getAdnetName(ad.responseInfo)
                        PaidEvent.admob(turnPl(ad, showPl), adsPlacementEcmp.originPl, FORMAT, ad.adUnitId, netName, adValue)
                    }
                    if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, ad); return }
                    LogUtil.logD("${tag(loadPl)} tryLoad ok id=${ecmpLoad.adsId}")
                    adsPlacementEcmp.isLoading = false
                    adsPlacementEcmp.isloaded = true
                    ecmpLoad.isLoading = false
                    ecmpLoad.isLoaded = true
                    ecmpLoad.adObject = ad
                    adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ad.adUnitId)
                    AdsHelper.instance.onAdLoaded(
                        loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId,
                        ad.responseInfo.mediationAdapterClassName ?: "source_null"
                    )
                    onFinishLoad()
                }
            }))
        AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, FORMAT, "admob", ecmpLoad.adsId)
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        val ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow == null) {
            LogUtil.logD("${tag(placement)} show not loaded")
            return false
        }
        val ad = ecmpShow.adObject as? RewardedInterstitialAd
        val activity = mActivity
        if (ad == null || activity == null) {
            LogUtil.logD("${tag(placement)} show ${if (ad == null) "ad not RewardedInterstitialAd" else "activity null"}")
            ecmpShow.isLoaded = false
            ecmpShow.adObject = null
            adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", if (ad == null) "ad null" else "activity null")
            return false
        }
        LogUtil.logD("${tag(placement)} show")
        setShowPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = true
        adsPlacementEcmp.adsShowCb = adsShowCb
        ecmpShow.flagShow = 0
        val rewardGate = RewardDeliveryGate()
        ad.fullScreenContentCallback = MainPost.fullScreen(object : FullScreenContentCallback() {
            override fun onAdClicked() {
                adsPlacementEcmp.adsShowCb?.onAdClick(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId)
                AdsHelper.instance.onclickAd()
            }

            override fun onAdDismissedFullScreenContent() {
                LogUtil.logD("${tag(showPl)} ondissmiss")
                release(ecmpShow)
                val cb = adsPlacementEcmp.adsShowCb
                if (rewardGate.takeReward()) {
                    (cb as? AdsGiftShowCb)?.onAdRewarded(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId)
                }
                cb?.onAdDismissed(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId) // 2.1.0 P3 (M15): placement lượt X
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                LogUtil.logD("${tag(showPl)} onshow fail")
                release(ecmpShow)
                adsPlacementEcmp.adsShowCb?.onAdShowFail(
                    showPl, adsPlacementEcmp.originPl, ad.adUnitId, "${adError.code}-${adError.message}"
                )
            }

            override fun onAdImpression() {
                adsPlacementEcmp.adsShowCb?.onAdImpression(turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId)
            }

            override fun onAdShowedFullScreenContent() {
                adsPlacementEcmp.adsShowCb?.onAdShowed(showPl, adsPlacementEcmp.originPl, ad.adUnitId, 0)
            }
        })
        ad.show(activity, MainPost.earned {
            LogUtil.logD("${tag(placement)} OnUserEarnedRewardListener")
            ecmpShow.flagShow = 1
            rewardGate.markEarned()
        })
        return true
    }

    private fun release(ecmp: com.app.plugin.ads.AdsEcmp) {
        ecmp.isLoaded = false
        ecmp.adObject = null
        adsPlacementEcmp.isShowing = false
    }

    private companion object {
        const val FORMAT = "rewarded_interstitial"
    }
}
