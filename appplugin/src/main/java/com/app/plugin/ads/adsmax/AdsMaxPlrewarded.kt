package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseFull
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.RewardDeliveryGate
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxRewardedAd
import java.util.concurrent.atomic.AtomicBoolean

internal class AdsMaxPlrewarded(context: Context, adsBase: AdsBase, type: Int, pl: String) : AdsPlBaseFull(context, adsBase, type, pl) {
    override val eventTag: String get() = "max_reward"

    // Không ghi đè loadedAdMaxAgeMs: miễn hạn `fullscreen_max_age_minutes` (review E I1). MAX tự hết hạn và nạp lại
    // trong cùng đối tượng `MaxRewardedAd` (`MaxFullscreenAdImpl.onAdExpired`, applovin-sdk 13.6.2), nên mốc load
    // của SDK (`AdsEcmp.loadedAtMillis`, chỉ đặt lại khi `adObject` đổi) không bao giờ mới lại; hạn của SDK chỉ
    // từ chối nhầm quảng cáo MAX còn tươi. MAX native full vẫn áp hạn (MAX chỉ báo `onNativeAdExpired`, không nạp lại).

    private var rewardedAd: MaxRewardedAd? = null
    var ecmpShow: AdsEcmp? = null
    private var rewardDeliveryGate = RewardDeliveryGate()
    override fun onInit() {
    }

    /** Mục waterfall mà đối tượng create-once đang gắn (unit id + instance AdsEcmp); listener chụp nó lúc dựng. */
    private var boundEcmp: AdsEcmp? = null

    /**
     * Cờ của listener đang gắn: đổi mục thì hạ cờ cũ, callback load của listener cũ (instance cũ không bị gỡ listener) bị
     * bỏ. Listener đọc cờ chụp theo listener, không đọc field (không thêm accessor vào dump).
     */
    private var boundLive: AtomicBoolean? = null

    /** [AdsPlacementEcmpBase.loadGeneration] lúc gửi request gần nhất; listener của đối tượng đang gắn đọc từ field. */
    private var loadGen = 0

    override fun tryLoad() {
        if (holdUntilReady(AdsMaxMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads gift max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false

                if (rewardedAd == null || boundEcmp !== ecmpLoad) {
                    if (rewardedAd != null && adsPlacementEcmp.isShowing) {
                        // Đang hiện bằng đối tượng cũ: không dựng lại giữa lúc hiện (mất callback đóng). Lượt load này vẫn đi với
                        // đối tượng cũ; lượt sau (sau dismiss) dựng lại. Log để thấy trên máy.
                        LogUtil.logD("ads gift max$adsType $loadPl ${adsPlacementEcmp.originPl} rebind deferred: showing id=${boundEcmp?.adsId} → ${ecmpLoad.adsId}")
                    } else {
                        // Mục khác (bước waterfall sau của danh sách nhiều id, hoặc parserEcpms đổi unit): lấy instance của unit này
                        // và gắn listener mới. Không gỡ listener, không destroy instance cũ: `MaxRewardedAd` là singleton theo unit,
                        // placement rewarded khác dùng chung unit có thể đang giữ nó đã load (review Task 14 I2). Callback load đã
                        // xếp hàng của listener cũ bị bỏ bằng cờ `live`.
                        boundLive?.set(false)
                        val live = AtomicBoolean(true).also { boundLive = it }
                        val lateTag = "ads gift max$adsType ${adsPlacementEcmp.originPl}"
                        boundEcmp = ecmpLoad
                        rewardedAd = MaxRewardedAd.getInstance(ecmpLoad.adsId, mContext)
                        rewardedAd?.setListener(object : MaxRewardedAdListener {
                            override fun onAdLoaded(ad: MaxAd) {
                                // Callback load của listener đã thay (instance cũ không bị gỡ listener): không thuộc request đang chờ.
                                if (!live.get()) { LogUtil.logD("$lateTag late load callback of replaced object dropped"); return }
                                if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, rewardedAd); return }
                                LogUtil.logD("ads gift max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                adsPlacementEcmp.isLoading = false
                                adsPlacementEcmp.isloaded = true
                                ecmpLoad.isLoading = false
                                ecmpLoad.isLoaded = true
                                ecmpLoad.adObject = rewardedAd
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, ad.adUnitId
                                )
                                AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "rewarded", "applovin", ecmpLoad.adsId, ad.networkName ?: "source_null")
                                onFinishLoad()
                            }

                            override fun onAdLoadFailed(p0: String, adError: MaxError) {
                                if (!live.get()) { LogUtil.logD("$lateTag late load callback of replaced object dropped"); return }
                                // Slot rảnh: MAX tự nạp lại lỗi trên đối tượng đang giữ → bỏ nó (Task J review IMP-2), không callback.
                                if (isStaleLoadCallback(loadGen)) { dropHeldAdOnIdleLoadFailure(ecmpLoad, rewardedAd); return }
                                LogUtil.logD("ads gift max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.adObject = null
                                adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                                AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "rewarded", "applovin", ecmpLoad.adsId, "${adError.code}")
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

                            override fun onAdDisplayed(ad: MaxAd) {
                                LogUtil.logD("ads gift max$adsType $showPl ${adsPlacementEcmp.originPl} onshowed")
                                adsPlacementEcmp.adsShowCb?.onAdShowed(
                                    showPl, adsPlacementEcmp.originPl, ad.adUnitId, 0
                                )
                            }

                            override fun onAdHidden(ad: MaxAd) {
                                LogUtil.logD("ads gift max$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
                                ecmpShow?.isLoaded = false
                                ecmpShow?.adObject = null
                                adsPlacementEcmp.isShowing = false
                                if (rewardDeliveryGate.takeReward() && adsPlacementEcmp.adsShowCb != null) {
                                    val cbrw: AdsGiftShowCb? =
                                        adsPlacementEcmp.adsShowCb as AdsGiftShowCb?
                                    cbrw?.onAdRewarded(
                                        showPl, adsPlacementEcmp.originPl, ad.adUnitId
                                    )
                                }
                                adsPlacementEcmp.adsShowCb?.onAdDismissed(
                                    showPl, adsPlacementEcmp.originPl, ad.adUnitId
                                )
                            }

                            override fun onAdClicked(ad: MaxAd) {
                                LogUtil.logD("ads gift max$adsType $showPl ${adsPlacementEcmp.originPl} onclick")
                                adsPlacementEcmp.adsShowCb?.onAdClick(
                                    showPl, adsPlacementEcmp.originPl, ad.adUnitId
                                )
                                AdsHelper.instance.onclickAd()
                            }

                            override fun onAdDisplayFailed(ad: MaxAd, adError: MaxError) {
                                LogUtil.logD("ads gift max$adsType $showPl ${adsPlacementEcmp.originPl} onshow fail")
                                ecmpShow?.isLoaded = false
                                ecmpShow?.adObject = null
                                adsPlacementEcmp.isShowing = false
                                adsPlacementEcmp.adsShowCb?.onAdShowFail(
                                    showPl,
                                    adsPlacementEcmp.originPl,
                                    ad.adUnitId,
                                    "${adError.code}-${adError.message}"
                                )
                            }

                            override fun onUserRewarded(p0: MaxAd, p1: MaxReward) {
                                LogUtil.logD("ads gift max$adsType $showPl ${adsPlacementEcmp.originPl} OnUserEarnedRewardListener")
                                ecmpShow?.flagShow = 1
                                rewardDeliveryGate.markEarned()
                            }

                        })
                        rewardedAd?.setRevenueListener {
                            PaidEvent.max(
                                showPl, adsPlacementEcmp.originPl, "rewarded", it.adUnitId, it.networkName, it.revenue, "USD"
                            )
                        }
                    }
                }
                loadGen = adsPlacementEcmp.loadGeneration
                rewardedAd?.loadAd()
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "rewarded", "applovin", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads gift max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads gift max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads gift max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    /** Task J4: `hasLoadedAd` theo `isReady()` như `show` (Task I4); MAX đang tự nạp lại thì chưa sẵn sàng, không bỏ quảng cáo. */
    override fun isLoadedAdReady(ad: Any): Boolean = (ad as? MaxRewardedAd)?.isReady ?: true

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        ecmpShow = loadedAdWithinMaxAge()
        if (ecmpShow != null && rewardedAd != null) {
            if (mActivity == null) {
                LogUtil.logD("ads gift max$adsType $placement ${adsPlacementEcmp.originPl} show activity null")
                return false
            }
            if (rewardedAd?.isReady != true) {
                // Task I4: MAX đang tự nạp lại (sau hết hạn) hoặc nạp lại lỗi; showAd lúc này chỉ ra display fail.
                LogUtil.logD("ads gift max$adsType $placement ${adsPlacementEcmp.originPl} show not ready")
                LogEventServer.logAdShowFailWithReason(placement, Constants.AdShowFailReason.SHOW_NOT_LOADED)
                return false
            }
            LogUtil.logD("ads gift max$adsType $placement ${adsPlacementEcmp.originPl} show")
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            ecmpShow?.flagShow = 0
            rewardDeliveryGate = RewardDeliveryGate()
            rewardedAd?.showAd(mActivity!!)
            return true
        } else {
            LogUtil.logD("ads gift max$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
    }
}