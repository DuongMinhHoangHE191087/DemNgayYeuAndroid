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
import com.google.android.gms.ads.appopen.AppOpenAd

internal class AdsMobPlOpen(context: Context, adsBase: AdsBase, type: Int, pl: String) : AdsPlBaseFull(context, adsBase, type, pl) {
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
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                if (ecmpLoad.adsId.startsWith("ca-app")) {
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
                    val loadGen = adsPlacementEcmp.loadGeneration
                    val adRequest = AdRequestFactory.build(mContext)
                    AppOpenAd.load(
                        GmaLoadContext.forLoad(mActivity!!, mContext),
                        ecmpLoad.adsId,
                        adRequest,
                        MainPost.appOpenLoad(object : AppOpenAd.AppOpenAdLoadCallback() {
                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                if (isStaleLoadCallback(loadGen)) return
                                LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.adObject = null
                                adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                                AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "opened", "admob", ecmpLoad.adsId, "${adError.code}")
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

                            override fun onAdLoaded(ad: AppOpenAd) {
                                // Final review IMP-1: paid listener gán trước guard stale, để quảng cáo tới muộn mà `keepLateAd` giữ (hiện sau
                                // đó) vẫn ghi doanh thu; quảng cáo bị bỏ/destroy thì không bao giờ hiện nên listener của nó không chạy.
                                ad.onPaidEventListener =
                                    MainPost.paid { adValue ->
                                        val netName = AdsMobMy.instance.getAdnetName(ad.responseInfo)
                                        PaidEvent.admob(
                                            turnPl(ad, showPl), adsPlacementEcmp.originPl,
                                            "opened",
                                            ad.adUnitId,
                                            netName,
                                            adValue
                                        )
                                    }
                                if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, ad); return }
                                LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                adsPlacementEcmp.isLoading = false
                                adsPlacementEcmp.isloaded = true
                                ecmpLoad.isLoading = false
                                ecmpLoad.isLoaded = true
                                ecmpLoad.adObject = ad
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    ad.adUnitId
                                )
                                AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "opened", "admob", ecmpLoad.adsId, ad.responseInfo.mediationAdapterClassName ?: "source_null")
                                onFinishLoad()
                            }
                        }))
                    AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "opened", "admob", ecmpLoad.adsId)
                } else {
                    LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad idload not correct")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        "",
                        "idload not correct"
                    )
                }
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl,
                adsPlacementEcmp.originPl,
                "",
                "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        // Quá 4 giờ thì null (quảng cáo đã bị bỏ): đi nhánh "show not loaded", không callback.
        val ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null) {
            LogUtil.logD("ads full open admob$adsType $placement ${adsPlacementEcmp.originPl} show")
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            val ad: AppOpenAd = ecmpShow.adObject as? AppOpenAd ?: run {
                LogUtil.logD("ads full open admob$adsType $placement ${adsPlacementEcmp.originPl} show ad not AppOpenAd")
                ecmpShow.isLoaded = false
                ecmpShow.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "ad null")
                return false
            }

            ad.fullScreenContentCallback = MainPost.fullScreen(object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} onclick")
                    adsPlacementEcmp.adsShowCb?.onAdClick(
                        turnPl(ad, showPl),
                        adsPlacementEcmp.originPl,
                        ad.adUnitId
                    )
                    AdsHelper.instance.onclickAd()
                }

                override fun onAdDismissedFullScreenContent() {
                    LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} ondissmiss")
                    ecmpShow.isLoaded = false
                    ecmpShow.adObject = null
                    adsPlacementEcmp.isShowing = false
                    adsPlacementEcmp.adsShowCb?.onAdDismissed(
                        turnPl(ad, showPl),
                        adsPlacementEcmp.originPl,
                        ad.adUnitId
                    )
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    LogEventServer.logAdShowFailWithReason(
                        placement = placement,
                        reason = "${Constants.AdShowFailReason.FAIL_TO_SHOW_FULL_CONTENT_OPEN}_${adError.message}"
                    )
                    LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} onshow fail")
                    ecmpShow.isLoaded = false
                    ecmpShow.adObject = null
                    adsPlacementEcmp.isShowing = false
                    adsPlacementEcmp.adsShowCb?.onAdShowFail(
                        showPl,
                        adsPlacementEcmp.originPl,
                        ad.adUnitId,
                        "${adError.code}-${adError.message}"
                    )
                }

                override fun onAdImpression() {
                    LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} onimpression id=${ecmpShow.adsId}")
                    adsPlacementEcmp.adsShowCb?.onAdImpression(
                        turnPl(ad, showPl),
                        adsPlacementEcmp.originPl,
                        ad.adUnitId
                    )
                }

                override fun onAdShowedFullScreenContent() {
                    LogUtil.logD("ads full open admob$adsType $loadPl ${adsPlacementEcmp.originPl} onshowed")
                    adsPlacementEcmp.adsShowCb?.onAdShowed(
                        showPl,
                        adsPlacementEcmp.originPl,
                        ad.adUnitId, 0
                    )
                }
            })
            if (mActivity == null) {
                LogUtil.logD("ads full open admob$adsType $placement ${adsPlacementEcmp.originPl} show activity null")
                // AppOpenAd/RewardedAd có không public destroy(), không cần gọi
                ecmpShow.isLoaded = false
                ecmpShow.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", Constants.AdShowFailReason.SHOW_WHEN_ACT_NULL)
                return false
            }
            ad.show(mActivity!!)
            return true
        } else {
            LogUtil.logD("ads full open admob$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
    }
}