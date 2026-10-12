package com.app.plugin.ads.core

import android.os.Handler
import android.os.Looper
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.ads.WaterfallShowFailHold
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.segment.SegmentHooks
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

/**
 * Vùng rewarded của [AdsHelper] (1.5.0 Task 9, G8). Chỉ đổi chủ sở hữu mã: thân hàm giữ nguyên, trạng thái dùng chung
 * đọc qua [h] ở mỗi lần gọi (không giữ bản sao — test gán `listAdsGift` của AdsHelper bằng reflection sau khi dựng).
 * Vùng không có state riêng: `giftTimeShow`, `giftIdxShow`, `giftCount4Circle`, `fullCount4Circle`, `flagAdsFull*` (R3)
 * ở lại façade; `listAdsGift` (R1) đọc qua `h.rewardedNetworks()`. Chuỗi Full2 sau rewarded gọi façade
 * (`h.beginFull2Transition`, `h.loadFull2ForThisTurn`, `h.showFull2`, `h.finishFull2Transition`; thân ở façade tới Task 12).
 * `loadRewardedForThisTurn` giữ tham số mặc định `loadCb = null` như bản cũ: vòng show/dismiss tự nạp lại bằng lời gọi
 * một tham số, chuyển nguyên văn.
 */
internal class RewardedController(private val h: AdsHelper) {
    private val cfNot = CfNotDispatcher("ads gift helper")
    /** 1.9.0 Task 2: placement `rwi_` đi sang rewarded interstitial TRƯỚC tiền tố `gift_` (hợp đồng §C). */
    private val rwi = RewardedInterstitialController(h)

    fun loadRewardedForThisTurn(placement: String, loadCb: AdsLoadCb? = null) {
        if (RewardedInterstitialController.owns(placement)) return rwi.load(placement, loadCb)
        var realPl = placement
        if (!placement.startsWith("gift_")) {
            realPl = "gift_" + placement
        }

        if (h.skipLoadWithoutHost("loadRewardedForThisTurn", realPl)) {
            loadCb?.onLoadFailed(placement, realPl, "", h.loadFailNoHostLabel())
            return
        }

        if (h.isRemoveAds(5) || h.isDisableAds(5)) {
            LogUtil.logD("ads gift helper $realPl loadRewardedForThisTurn isremoveads or disable")
            loadCb?.onLoadFailed(
                placement,
                realPl,
                "",
                h.disabledReason(5, "loadRewardedForThisTurn isremoveads or disable")
            )
            return
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads gift helper $realPl loadRewardedForThisTurn cf not")
                cfNot.fail(placement, realPl, loadCb)
                return
            } else {
                if (!plcf.isLoading) {
                    LogUtil.logD("ads gift helper ${plcf.placement} loadRewardedForThisTurn call load")
                    plcf.isLoading = true
                    plcf.cbLoad = loadCb
                    loadRewardedCircle(plcf, getListRewardedLoadShow(plcf), plcf.loadCycle)
                    if (h.currConfig.full2FlagShow == 2 || h.currConfig.full2FlagShow == 3) {
                        h.loadFull2ForThisTurn(placement)
                    }
                } else {
                    LogUtil.logD("ads gift helper $realPl loadRewardedForThisTurn loading, attaching callback")
                    LoadCallbacks.attachLoadCallback(plcf, loadCb)
                }
            }
        } else {
            LogUtil.logD("ads gift helper $realPl loadRewardedForThisTurn not cf")
        }
    }

    private fun loadRewardedCircle(plcf: AdsPlacementConfig, listLoad: MutableList<Int>, cycle: Int) {
        LogUtil.logD("ads gift helper ${plcf.placement} loadRewardedCircle")
        if (cycle != plcf.loadCycle) {
            LogUtil.logD("ads helper ${plcf.placement} loadRewardedCircle stale cycle dropped")
            return
        }
        if (listLoad.size > 0) {
            val typeLoad = listLoad[0]
            listLoad.removeAt(0)
            val adsLoad = getRewardedWithType(typeLoad)
            if (adsLoad != null) {
                adsLoad.loadAd(plcf.placement, object : AdsLoadInnerCb {
                    override fun onLoaded(
                        placement: String,
                        originPl: String,
                        adId: String,
                        adsEcmp: AdsEcmp?,
                        adPl: AdsPlBase?
                    ) {
                        if (cycle != plcf.loadCycle) return
                        plcf.isLoading = false
                        plcf.isLoaded = true
                        plcf.cbLoad?.onLoaded(placement, originPl, adId)
                    }

                    override fun onLoadFailed(
                        placement: String, originPl: String, adId: String, error: String
                    ) {
                        if (cycle != plcf.loadCycle) return
                        // BUG-FIX: phải check isLoading trước khi retry (cùng pattern loadNtClCircle BUG-NEW-01)
                        if (plcf.isLoading) {
                            Handler(Looper.getMainLooper()).post {
                                loadRewardedCircle(plcf, listLoad, cycle)
                            }
                        } else {
                            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "loadRewardedCircle isLoading=false on fail")
                        }
                    }
                })
            } else {
                Handler(Looper.getMainLooper()).post {
                    loadRewardedCircle(plcf, listLoad, cycle)
                }
            }
        } else {
            plcf.isLoading = false
            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "list empty")
        }
    }

    fun showRewarded(placement: String, cbShow: AdsGiftShowCb): Boolean {
        if (RewardedInterstitialController.owns(placement)) return rwi.show(placement, cbShow)
        var realPl = placement
        if (!placement.startsWith("gift_")) {
            realPl = "gift_" + placement
        }
        // 1.7.0 Task 2: mỗi lượt gọi một `ad_show_attempt`, trước mọi cổng; ready = slot đã load lúc gọi.
        val ready = if (h.hasPlacementConfig() && h.getAdsPlacementConfig(realPl, 1)?.isLoaded == true) 1 else 0
        AdEventLog.showAttempt("rewarded", realPl, ready, ScreenName.name)

        // BUG-FIX: clamp âm và check trong 1 thao tác atomic, tránh check-then-act race
        if (h.flagAdsFullShowing.updateAndGet { if (it < 0) 0 else it } > 0) {
            LogUtil.logD("ads gift helper $realPl showRewarded isAdsFullShowing")
            LogEventServer.logAdShowFailWithReason(placement = realPl, reason = Constants.AdShowFailReason.AD_FULL_SHOWING)
            return false
        }

        if (h.isRemoveAds(5) || h.isDisableAds(5)) {
            h.reportDisabledShow(5, realPl, Constants.AdShowFailReason.REMOVE_AD)
            return false
        }

        // BUG-12 FIX: giftDeltaTime được config nhưng isRewardedAllowShowAds() chưa được gọi
        if (!h.isRewardedAllowShowAds()) {
            LogUtil.logD("ads gift helper $realPl showRewarded not allow show (delta time)")
            LogEventServer.logAdShowFailWithReason(placement = realPl, reason = Constants.AdShowFailReason.NOT_ALLOW_SHOW)
            return false
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads gift helper $realPl showRewarded cf not")
                LogEventServer.logAdShowFailWithReason(placement = realPl, reason = Constants.AdShowFailReason.CONFIG_NOT)
                return false
            } else {
                if (!plcf.isShowing) {
                    LogUtil.logD("ads gift helper ${plcf.placement} showRewarded call show")
                    plcf.cbShow = cbShow
                    h.fullCount4Circle = 0
                    h.flagAdsFull4ShowFaild.set(-2)
                    h.flagAdsFull4Dissmiss.set(-2)
                    val reshow = showRewardedCircle(plcf, getListRewardedLoadShow(plcf))
                    if (!reshow) {
                        // Cờ đã load (nếu còn bật) là cũ: hạ trước khi nạp lại.
                        plcf.isLoaded = false
                        loadRewardedForThisTurn(realPl)
                    }
                    return reshow
                } else {
                    LogUtil.logD("ads gift helper $realPl showRewarded is ads showing")
                    LogEventServer.logAdShowFailWithReason(placement = realPl, reason = Constants.AdShowFailReason.AD_SHOWING)
                    return false
                }
            }
        } else {
            LogUtil.logD("ads gift helper $realPl showRewarded has not cf")
            LogEventServer.logAdShowFailWithReason(placement = realPl, reason = Constants.AdShowFailReason.NOT_CONFIG)
            return false
        }
    }

    private fun showRewardedCircle(
        plcf: AdsPlacementConfig, listShow: MutableList<Int>
    ): Boolean {
        LogUtil.logD("ads gift helper ${plcf.placement} showRewardedCircle")
        var reShow = false
        val showFails = WaterfallShowFailHold(plcf.placement)
        while (listShow.size > 0) {
            val typeShow = listShow[0]
            listShow.removeAt(0)
            h.giftCount4Circle++
            if (h.giftCount4Circle <= h.currConfig.giftStepCicle.size) {
                h.giftIdxShow++
                if (h.giftIdxShow >= h.currConfig.giftStepCicle.size) {
                    h.giftIdxShow = 0
                }
            }
            val adsShow = getRewardedWithType(typeShow)
            if (adsShow != null) {
                reShow = showFails.during { adsShow.showAd(plcf.placement, object : AdsGiftShowCb {
                    override fun onAdShowFail(
                        placement: String?, originPl: String?, adId: String?, err: String?
                    ) {
                        // Báo trong lúc showAd(): giữ tới cuối vòng, không tới app giữa waterfall.
                        if (showFails.hold { onAdShowFail(placement, originPl, adId, err) }) return
                        plcf.isShowing = false
                        // BUG-FIX: decrement + clamp âm trong 1 thao tác atomic, tránh check-then-act race
                        h.flagAdsFullShowing.updateAndGet { (it - 1).coerceAtLeast(0) }
                        h.giftTimeShow = System.currentTimeMillis() / 1000
                        val newFlagShowFaild = h.flagAdsFull4ShowFaild.incrementAndGet()
                        if (newFlagShowFaild >= 2 || (newFlagShowFaild <= 0 && newFlagShowFaild >= -1)) {
                            h.flagAdsFull4ShowFaild.set(-10)
                            plcf.cbShow?.onAdShowFail(placement, originPl, adId, err)
                        }
                        loadRewardedForThisTurn(plcf.placement)   // 2.4.0 CARRY-ADS #8: placement vừa hiện, như rwi
                    }

                    override fun onAdShowed(
                        placement: String?, originPl: String?, adId: String?, flag: Int
                    ) {
                        plcf.cbShow?.onAdShowed(placement, originPl, adId, flag)
                    }

                    override fun onAdImpression(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                        plcf.cbShow?.onAdImpression(placement, originPl, adId)
                    }

                    override fun onAdClick(placement: String?, originPl: String?, adId: String?) {
                        AdEventLog.click("rewarded", placement?.takeIf { it.isNotEmpty() } ?: originPl ?: plcf.placement, adId ?: "")
                        h.onAdClicked()
                        plcf.cbShow?.onAdClick(placement, originPl, adId)
                    }

                    override fun onAdRewarded(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                        SegmentHooks.onRewardedCompleted()
                        if (plcf.cbShow != null) {
                            val rwcb: AdsGiftShowCb = plcf.cbShow as AdsGiftShowCb
                            rwcb.onAdRewarded(placement, originPl, adId)
                        }
                    }

                    override fun onAdDismissed(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                        plcf.isShowing = false
                        // BUG-FIX: decrement + clamp âm trong 1 thao tác atomic, tránh check-then-act race
                        h.flagAdsFullShowing.updateAndGet { (it - 1).coerceAtLeast(0) }
                        h.giftTimeShow = System.currentTimeMillis() / 1000
                        val newFlagDissmiss = h.flagAdsFull4Dissmiss.incrementAndGet()
                        if (newFlagDissmiss >= 2 || (newFlagDissmiss >= -1 && newFlagDissmiss <= 0)) {
                            h.flagAdsFull4Dissmiss.set(-10)
                            plcf.cbShow?.onAdDismissed(placement, originPl, adId)
                        }

                        if (h.fullscreenPolicy().shouldChainFull2AfterRewarded(
                                full2FlagShow = h.currConfig.full2FlagShow,
                            )
                        ) {
                            // The reward callback runs before dismissal, so a reward that grants an
                            // ad-free entitlement is already visible to showFull2() and is protected
                            // by its isRemoveAds() gate.
                            h.showFull2(plcf.placement, plcf.cbShow)   // 2.4.2 FIXED: placement được gọi, không slot originPl
                        } else {
                            h.finishFull2Transition()
                        }

                        loadRewardedForThisTurn(plcf.placement)   // 2.4.0 CARRY-ADS #8: placement vừa hiện, như rwi
                    }
                }) }
                if (reShow) {
                    plcf.isShowing = true
                    h.flagAdsFullShowing.incrementAndGet()
                    h.giftTimeShow = System.currentTimeMillis() / 1000
                    // Rewarded không bị cooldown chặn (người dùng tự bấm), nhưng vẫn bật cooldown
                    // cho quảng cáo tự động kế tiếp.
                    h.markAutoAdShown()
                    if (h.fullscreenPolicy().shouldChainFull2AfterRewarded(
                            h.currConfig.full2FlagShow,
                        )
                    ) h.beginFull2Transition(plcf.placement)
                    h.flagAdsFull4ShowFaild.incrementAndGet()
                    h.flagAdsFull4Dissmiss.incrementAndGet()
                    break
                }
            }
        }
        showFails.settle(reShow)
        if (!reShow) {
            plcf.isShowing = false
        }
        return reShow
    }

    private fun getRewardedWithType(type: Int): AdsBaseGift? {
        if (type == AdsIDDef.AdmobRwi) return null // chỉ RewardedInterstitialController dùng (1.9.0 Task 2)
        if (h.rewardedNetworks().containsKey(type)) {
            return h.rewardedNetworks()[type]
        }
        return null
    }

    private fun getListRewardedLoadShow(plcf: AdsPlacementConfig): MutableList<Int> {
        var list: MutableList<Int> = mutableListOf()

        var idx = h.giftIdxShow
        if (idx >= h.currConfig.giftStepCicle.size) {
            idx = 0
        }
        for (i in 0 until h.currConfig.giftStepCicle.size) {
            list.add(h.currConfig.giftStepCicle[idx])
            idx++
            if (idx >= h.currConfig.giftStepCicle.size) {
                idx = 0
            }
        }
        for (i in 0 until h.currConfig.giftStepRe.size) {
            list.add(h.currConfig.giftStepRe[i])
        }

        return list
    }
}
