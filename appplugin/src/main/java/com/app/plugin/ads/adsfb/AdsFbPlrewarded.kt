package com.app.plugin.ads.adsfb

import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseFull
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.RewardDeliveryGate
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.RewardedVideoAd
import com.facebook.ads.RewardedVideoAdListener

internal class AdsFbPlrewarded(context: Context, adsBase: AdsBase, type: Int, pl: String) : AdsPlBaseFull(context, adsBase, type, pl) {
    override val eventTag: String get() = "fb_reward"

    /** `fullscreen_max_age_minutes` của `ads_policy_android` (mặc định 60 phút), đọc mỗi lần xét tuổi. */
    override val loadedAdMaxAgeMs: Long get() = AdsPolicy.current.fullscreenMaxAgeMillis()

    var ecmpShow: AdsEcmp? = null
    private var rewardDeliveryGate = RewardDeliveryGate()
    override fun onInit() {
    }

    override fun tryLoad() {
        if (holdUntilReady(AdsFbMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads gift fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false
                val loadGen = adsPlacementEcmp.loadGeneration

                var idLoad = ecmpLoad.adsId
                if (AdsFbMy.instance.flagPp?.compareTo("90351") == 0) {
                    idLoad = "VID_HD_9_16_39S_APP_INSTALL#$idLoad"
                }
                var rewardedVideoAd = RewardedVideoAd(mContext, idLoad)
                rewardedVideoAd.loadAd(
                    rewardedVideoAd.buildLoadAdConfig()
                        .withAdListener(object : RewardedVideoAdListener {
                            override fun onError(p0: Ad?, adError: AdError?) {
                                if (isStaleLoadCallback(loadGen)) return
                                LogUtil.logD("ads gift fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError?.errorCode}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.adObject = null
                                adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                                AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "rewarded", "facebook", ecmpLoad.adsId, "${adError?.errorCode}")
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

                            override fun onAdLoaded(ad: Ad?) {
                                if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, rewardedVideoAd); return }
                                LogUtil.logD("ads gift fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                adsPlacementEcmp.isLoading = false
                                adsPlacementEcmp.isloaded = true
                                ecmpLoad.isLoading = false
                                ecmpLoad.isLoaded = true
                                ecmpLoad.adObject = rewardedVideoAd
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, idLoad
                                )
                                AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "rewarded", "facebook", ecmpLoad.adsId, "facebook")
                                onFinishLoad()
                            }

                            override fun onAdClicked(ad: Ad?) {
                                LogUtil.logD("ads gift fb$adsType $showPl ${adsPlacementEcmp.originPl} onclick")
                                adsPlacementEcmp.adsShowCb?.onAdClick(
                                    turnPl(rewardedVideoAd, showPl), adsPlacementEcmp.originPl, idLoad
                                )
                                AdsHelper.instance.onclickAd()
                            }

                            override fun onLoggingImpression(ad: Ad?) {
                                LogUtil.logD("ads gift fb$adsType $showPl ${adsPlacementEcmp.originPl} onimpression")
                                adsPlacementEcmp.adsShowCb?.onAdImpression(
                                    turnPl(rewardedVideoAd, showPl), adsPlacementEcmp.originPl, idLoad
                                )
                                PaidEvent.metaDirect(turnPl(rewardedVideoAd, showPl), adsPlacementEcmp.originPl, "rewarded", idLoad, "facebook")
                            }

                            override fun onRewardedVideoCompleted() {
                                LogUtil.logD("ads gift fb$adsType $showPl ${adsPlacementEcmp.originPl} OnUserEarnedRewardListener")
                                ecmpShow?.flagShow = 1
                                rewardDeliveryGate.markEarned()
                            }

                            override fun onRewardedVideoClosed() {
                                LogUtil.logD("ads gift fb$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
                                ecmpShow?.isLoaded = false
                                ecmpShow?.adObject = null
                                adsPlacementEcmp.isShowing = false
                                // Task I3: quảng cáo đã đóng không dùng lại được; destroy() như nhánh hết tuổi (FE3)
                                // để Meta giải phóng nó, không chỉ bỏ tham chiếu. Chỉ ở đây, sau khi đã thôi hiện.
                                destroyAd(rewardedVideoAd)
                                if (rewardDeliveryGate.takeReward() && adsPlacementEcmp.adsShowCb != null) {
                                    val cbrw: AdsGiftShowCb? = adsPlacementEcmp.adsShowCb as AdsGiftShowCb?
                                    cbrw?.onAdRewarded(
                                        turnPl(rewardedVideoAd, showPl), adsPlacementEcmp.originPl, idLoad
                                    )
                                }
                                adsPlacementEcmp.adsShowCb?.onAdDismissed(
                                    turnPl(rewardedVideoAd, showPl), adsPlacementEcmp.originPl, idLoad
                                )
                            }

                        })
                        .build())
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "rewarded", "facebook", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads gift fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads gift fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads gift fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null) {
            LogUtil.logD("ads gift fb$adsType $placement ${adsPlacementEcmp.originPl} show")
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            ecmpShow?.flagShow = 0
            rewardDeliveryGate = RewardDeliveryGate()
            val ad = ecmpShow?.adObject as? RewardedVideoAd
            if (ad == null) {
                LogUtil.logD("ads gift fb$adsType $placement ${adsPlacementEcmp.originPl} show ad not RewardedVideoAd")
                // BUG-FIX: reset hết state + báo callback, đồng bộ với AdsMobPlInters.kt
                ecmpShow?.isLoaded = false
                ecmpShow?.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "ad null")
                return false
            }
            ad.show()
            return true
        } else {
            LogUtil.logD("ads gift fb$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
    }

    /**
     * Quảng cáo bị bỏ vì quá tuổi (`AdsEcmps.dropExpired` gọi hàm này sau khi bỏ tham chiếu, review E M2) hoặc đã đóng
     * (`onRewardedVideoClosed`, Task I3): `destroy()` để Meta giải phóng tài nguyên của `RewardedVideoAd`, không chỉ
     * bỏ tham chiếu. Đối tượng khác kiểu thì bỏ qua.
     */
    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        val metaAd = ad as? RewardedVideoAd ?: return false
        metaAd.destroy()
        return true
    }
}
