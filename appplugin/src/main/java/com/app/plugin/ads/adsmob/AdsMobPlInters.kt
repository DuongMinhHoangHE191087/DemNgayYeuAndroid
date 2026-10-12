package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseFull
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainPost
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdError
import com.app.plugin.ads.GmaLoadContext
import com.app.plugin.ads.consent.AdRequestFactory
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

internal class AdsMobPlInters(context: Context, adsBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseFull(context, adsBase, type, pl) {
    override val eventTag: String get() = "mob_full"

    /** `fullscreen_max_age_minutes` của `ads_policy_android` (mặc định 60 phút), đọc mỗi lần xét tuổi. */
    override val loadedAdMaxAgeMs: Long get() = AdsPolicy.current.fullscreenMaxAgeMillis()

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
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                if (ecmpLoad.adsId.startsWith("ca-app")) {
                    if (mActivity == null) {
                        LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad activity null")
                        adsPlacementEcmp.isLoading = false
                        adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "activity null")
                        return
                    }
                    LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                    adsPlacementEcmp.isLoading = true
                    adsPlacementEcmp.isHasLoad = true
                    ecmpLoad.isLoading = true
                    ecmpLoad.isLoaded = false
                    val loadGen = adsPlacementEcmp.loadGeneration
                    val adRequest = AdRequestFactory.build(mContext)
                    InterstitialAd.load(GmaLoadContext.forLoad(mActivity!!, mContext),
                        ecmpLoad.adsId,
                        adRequest,
                        MainPost.interstitialLoad(object : InterstitialAdLoadCallback() {
                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                if (isStaleLoadCallback(loadGen)) return
                                LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.adObject = null
                                adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                                AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "interstitial", "admob", ecmpLoad.adsId, "${adError.code}")
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

                            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                                // Final review IMP-1: paid listener gán trước guard stale, để quảng cáo tới muộn mà `keepLateAd` giữ (hiện sau
                                // đó) vẫn ghi doanh thu; quảng cáo bị bỏ/destroy thì không bao giờ hiện nên listener của nó không chạy.
                                interstitialAd.onPaidEventListener =
                                    MainPost.paid { adValue ->
                                        val netName = AdsMobMy.instance.getAdnetName(interstitialAd.responseInfo)
                                        PaidEvent.admob(
                                            turnPl(interstitialAd, showPl), adsPlacementEcmp.originPl,
                                            "interstitial",
                                            interstitialAd.adUnitId,
                                            netName,
                                            adValue
                                        )
                                    }
                                if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, interstitialAd); return }
                                LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                adsPlacementEcmp.isLoading = false
                                adsPlacementEcmp.isloaded = true
                                ecmpLoad.isLoading = false
                                ecmpLoad.isLoaded = true
                                ecmpLoad.adObject = interstitialAd
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, interstitialAd.adUnitId
                                )
                                AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "interstitial", "admob", ecmpLoad.adsId, interstitialAd.responseInfo.mediationAdapterClassName ?: "source_null")
                                onFinishLoad()
                            }
                        }))
                    AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "interstitial", "admob", ecmpLoad.adsId)
                } else {
                    LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad idload not correct")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "idload not correct"
                    )
                }
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        val ecmpLoaded = loadedAdWithinMaxAge()
        if (ecmpLoaded != null) {
            LogUtil.logD("ads full admob$adsType $placement ${adsPlacementEcmp.originPl} show")
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            val ad: InterstitialAd = ecmpLoaded.adObject as? InterstitialAd ?: run {
                LogUtil.logD("ads full admob$adsType $placement ${adsPlacementEcmp.originPl} show ad not InterstitialAd")
                ecmpLoaded.isLoaded = false
                ecmpLoaded.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "ad null")
                return false
            }

            ad.fullScreenContentCallback = MainPost.fullScreen(object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    LogUtil.logD("ads full admob$adsType $showPl ${adsPlacementEcmp.originPl} onclick")
                    adsPlacementEcmp.adsShowCb?.onAdClick(
                        turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId
                    )
                    AdsHelper.instance.onclickAd()
                }

                override fun onAdDismissedFullScreenContent() {
                    LogUtil.logD("ads full admob$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
                    ecmpLoaded.isLoaded = false
                    ecmpLoaded.adObject = null
                    adsPlacementEcmp.isShowing = false
                    adsPlacementEcmp.adsShowCb?.onAdDismissed(
                        turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId
                    )
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    LogEventServer.logAdShowFailWithReason(
                        placement = placement,
                        reason = "${Constants.AdShowFailReason.FAIL_TO_SHOW_FULL_CONTENT}_${adError.message}"
                    )
                    LogUtil.logD("ads full admob$adsType $showPl ${adsPlacementEcmp.originPl} onshow fail")
                    ecmpLoaded.isLoaded = false
                    ecmpLoaded.adObject = null
                    adsPlacementEcmp.isShowing = false
                    adsPlacementEcmp.adsShowCb?.onAdShowFail(
                        showPl,
                        adsPlacementEcmp.originPl,
                        ad.adUnitId,
                        "${adError.code}-${adError.message}"
                    )
                }

                override fun onAdImpression() {
                    LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} onimpression id=${ecmpLoaded.adsId}")
                    adsPlacementEcmp.adsShowCb?.onAdImpression(
                        turnPl(ad, showPl), adsPlacementEcmp.originPl, ad.adUnitId
                    )
                }

                override fun onAdShowedFullScreenContent() {
                    LogUtil.logD("ads full admob$adsType $loadPl ${adsPlacementEcmp.originPl} onshowed")
                    adsPlacementEcmp.adsShowCb?.onAdShowed(
                        showPl, adsPlacementEcmp.originPl, ad.adUnitId, 0
                    )
                }
            })
            if (mActivity != null) {
                ad.show(mActivity!!)
                return true
            } else {
                LogEventServer.logAdShowFailWithReason(
                    placement = placement,
                    reason = Constants.AdShowFailReason.SHOW_WHEN_ACT_NULL
                )
                // BUG-NEW-02 FIX: InterstitialAd has no destroy(); clearing adObject below
                // drops the only reference so it can be garbage collected.
                // Reset state: ad was not shown, clear flags set above
                ecmpLoaded.isLoaded = false
                ecmpLoaded.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(
                    placement, adsPlacementEcmp.originPl, "",
                    Constants.AdShowFailReason.SHOW_WHEN_ACT_NULL
                )
                return false
            }
        } else {

            LogUtil.logD("ads full admob$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            LogEventServer.logAdShowFailWithReason(
                placement = placement,
                reason = Constants.AdShowFailReason.SHOW_NOT_LOADED
            )
            return false
        }
    }
}