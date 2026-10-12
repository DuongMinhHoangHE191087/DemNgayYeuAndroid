package com.app.plugin.ads.core

import android.os.Handler
import android.os.Looper
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.ads.WaterfallShowFailHold
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.FirHelper
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.AdStatus
import com.app.plugin.logevent.AdmobSDKInitState
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.segment.SegmentHooks
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

/**
 * Vùng app open của [AdsHelper] (1.5.0 Task 8, G8). Chỉ đổi chủ sở hữu mã: thân hàm giữ nguyên, trạng thái dùng chung
 * đọc qua [h] ở mỗi lần gọi (không giữ bản sao — test gán `listAdsFull` của AdsHelper bằng reflection sau khi dựng).
 * Ở lại façade: `openFlag4Resume` (R1, đọc qua `h.openResumeFlag()`), `openTimeShow`, `suppressOpenAdUntilMillis`,
 * `fullCount4Circle`, `fullIdxShow`, `flagAdsFull*` (R3), `flagAdClick` (cổng click, `h.adClickFlag()`/`h.setAdClickFlag`).
 * `openFlagFirst` và `isWaitReleadOpenAd` thuộc vùng này. `reloadSplash` giữ rỗng: thân sau `return` vô điều kiện là mã
 * chết (R7), xoá ở Task 8; `setMainActivity` gọi [onHostSet], `checkAfterLoadIdsConfig` gọi [reloadSplash].
 */
internal class AppOpenController(private val h: AdsHelper) {
    private var openFlagFirst: Boolean = true
    private var isWaitReleadOpenAd: Boolean = false

    /** Gọi từ AdsHelper.setMainActivity, đúng chỗ khối `isWaitReleadOpenAd` cũ (sau vòng `setActivity` của mạng). */
    fun onHostSet() {
        if (isWaitReleadOpenAd) {
            isWaitReleadOpenAd = false
            FirHelper.instance.logEvent(EventNames.SET_AC_WAIT_RELOAD_SPLASH)
            reloadSplash()
        }
        AdPreloader.onHostSet()   // 2.3.0 PRELOAD: lượt nạp sẵn đang chờ host Activity
    }

    fun reloadSplash() { /* splash không tự nạp lại (thiết kế SDK); thân chết sau return xoá 1.5.0 Task 8 */ }

    fun loadOpenAdForThisTurn(placement: String, adloadCb: AdsLoadCb?) {
        var realPl = placement
        if (!placement.startsWith("full_")) {
            realPl = "full_" + placement
        }
        if (h.isRemoveAds(4) || h.isDisableAds(4)) {
            LogUtil.logD("ads full helper $realPl loadOpenAdForThisTurn isremoveads or disable")
            adloadCb?.onLoadFailed(placement, realPl, "", h.disabledReason(4, "loadOpenAdForThisTurn isremoveads or disable"))
            return
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads full helper $realPl loadOpenAdForThisTurn cf not")
                adloadCb?.onLoadFailed(placement, realPl, "", "loadOpenAdForThisTurn cf not")
                return
            } else {
                if (!plcf.isLoading) {
                    LogUtil.logD("ads full helper ${plcf.placement} loadOpenAdForThisTurn call load")
                    plcf.isLoading = true
                    plcf.cbLoad = adloadCb
                    loadOpenCircle(plcf, getListOpenLoadShow(plcf), plcf.loadCycle)
                } else if (adloadCb != null && !LoadCallbacks.holds(plcf.cbLoad, adloadCb)) {
                    // 2.4.0 CARRY-ADS #17: lượt đang nạp → cb vào danh sách của lượt, kết quả thật đúng một lần theo thứ tự gắn.
                    LogUtil.logD("ads full helper $realPl loadOpenAdForThisTurn loading, attaching callback")
                    LoadCallbacks.attachLoadCallback(plcf, OneShotLoadCb(adloadCb))
                }
            }
        } else {
            LogUtil.logD("ads full helper $realPl loadOpenAdForThisTurn not cf")
            adloadCb?.onLoadFailed(placement, realPl, "", "loadOpenAdForThisTurn not cf")
        }
    }

    private fun loadOpenCircle(plcf: AdsPlacementConfig, listLoad: MutableList<Int>, cycle: Int) {
        LogUtil.logD("ads full helper ${plcf.placement} loadOpenCircle")
        if (cycle != plcf.loadCycle) {
            LogUtil.logD("ads helper ${plcf.placement} loadOpenCircle stale cycle dropped")
            return
        }
        if (listLoad.size > 0) {
            val typeLoad = listLoad[0]
            listLoad.removeAt(0)
            val adsLoad = getOpenWithType(typeLoad)
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
                                loadOpenCircle(plcf, listLoad, cycle)
                            }
                        } else {
                            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "loadOpenCircle isLoading=false on fail")
                        }
                    }
                })
            } else {
                Handler(Looper.getMainLooper()).post {
                    loadOpenCircle(plcf, listLoad, cycle)
                }
            }
        } else {
            plcf.isLoading = false
            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "list empty")
        }
    }

    fun setAfterFirstOpenShow() {
        openFlagFirst = false
    }

    fun isFirstOpenReady(): Boolean {
        val plcf = h.getAdsPlacementConfig(PlacementDefault.PLFullSplash, 1)
        if (plcf != null) {
            return plcf.isLoaded
        }
        return false
    }

    fun showFirstOpen(cbShow: AdsShowCb?): Boolean {
        LogUtil.logD("ads full helper ${PlacementDefault.PLFullSplash} showFirstOpen")
        val re = showOpenAd(PlacementDefault.PLFullSplash, cbShow)
        openFlagFirst = false
        return re
    }

    fun showOpenAd(placement: String, cbShow: AdsShowCb?): Boolean {
        var realPl = placement
        if (!placement.startsWith("full_")) {
            realPl = "full_" + placement
        }

        if (!realPl.contains(PlacementDefault.PLFullSplash) && !h.openResumeFlag()) {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showOpenAd openFlag4Resume=false")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.OPEN_FLAG_RESUME
            )
            return false
        }

        // BUG-FIX: clamp âm và check trong 1 thao tác atomic, tránh check-then-act race
        // (get() rồi set(0) riêng biệt có thể clobber 1 incrementAndGet() chạy giữa 2 lệnh)
        if (h.flagAdsFullShowing.updateAndGet { if (it < 0) 0 else it } > 0) {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showOpenAd isAdsFullShowing")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.AD_FULL_SHOWING
            )
            return false
        }

        if (h.adClickFlag() > 0 && realPl != PlacementDefault.PLFullSplash) {
            reportBlockedAttempt(realPl)
            h.setAdClickFlag(0)
            LogUtil.logD("ads full helper $realPl showOpenAd not show after clickAd")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.SHOW_AFTER_CLICK_AD
            )
            return false
        }

        if (realPl != PlacementDefault.PLFullSplash && System.currentTimeMillis() < h.suppressOpenAdUntilMillis) {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showOpenAd suppressed after app-initiated intent")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.NOT_ALLOW_SHOW
            )
            return false
        }

        if (h.isRemoveAds(4) || h.isDisableAds(4)) {
            reportBlockedAttempt(realPl)
            h.reportDisabledShow(4, realPl, Constants.AdShowFailReason.REMOVE_AD)
            return false
        }

        // 2.2.0 SEG: guardrail chéo loại của segment (mặc định tắt; RC seg_cadence_guardrail_seconds hoặc resource app).
        // 2.4.2 SPLASH: full_splash miễn như fork (AdsHelper.kt:2213) và như các cổng khác ở trên: dấu bền sống qua process.
        if (realPl != PlacementDefault.PLFullSplash && SegmentHooks.blocksFullscreen(System.currentTimeMillis())) {
            reportBlockedAttempt(realPl)
            LogEventServer.logAdShowFailWithReason(placement = realPl, reason = SegmentHooks.REASON_GUARDRAIL)
            return false
        }

        // Luật phiên của ads_policy_android (mặc định 1: không chặn). full_splash miễn, như khối suppress ở trên.
        if (realPl != PlacementDefault.PLFullSplash) {
            h.adsPolicy.appOpenVerdict(AppPluginManager.instance.getSessionApp())?.let { reason ->
                reportBlockedAttempt(realPl)
                LogUtil.logD("ads full helper $realPl showOpenAd refused by policy: $reason")
                LogEventServer.logAdShowFailWithReason(placement = realPl, reason = reason)
                return false
            }
        }

        if (h.screenCooldown().isActive && realPl != PlacementDefault.PLFullSplash) {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showOpenAd within post-ad screen cooldown")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.POST_AD_SCREEN_COOLDOWN
            )
            return false
        }

        if (!h.isOpenAllowShowAds() && realPl != PlacementDefault.PLFullSplash) {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showOpenAd not allow show")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.NOT_ALLOW_SHOW
            )
            return false
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                reportBlockedAttempt(realPl)
                LogUtil.logD("ads full helper $realPl showOpenAd cf not")
                LogEventServer.logAdShowFailWithReason(
                    placement = realPl,
                    reason = Constants.AdShowFailReason.CONFIG_NOT
                )
                return false
            } else {
                if (!plcf.isShowing) {
                    LogUtil.logD("ads full helper ${plcf.placement} showOpenAd call show")
                    plcf.cbShow = cbShow
                    h.fullCount4Circle = 0
                    h.flagAdsFull4ShowFaild.set(-2)
                    h.flagAdsFull4Dissmiss.set(-2)

                    LogEventServer.logAdStartAction(
                        adFormat = "opened",
                        placement = realPl,
                        isAction = if (plcf.isLoaded) AdStatus.Loaded.code else AdStatus.NotLoaded.code,
                        screenName = ScreenName.name,
                        isSDKInit = AdmobSDKInitState.isInitComplete
                    )

                    val reShow = showOpenAdCircle(plcf, getListOpenLoadShow(plcf))

                    if (!reShow) {

                    }
                    return reShow
                } else {
                    reportBlockedAttempt(realPl)
                    LogUtil.logD("ads full helper $realPl showOpenAd is ads showing")
                    LogEventServer.logAdShowFailWithReason(
                        placement = realPl,
                        reason = Constants.AdShowFailReason.AD_SHOWING
                    )
                    return false
                }
            }
        } else {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showOpenAd has not cf")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.NOT_CONFIG
            )
            return false
        }
    }

    /**
     * `ad_show_attempt` của lượt [showOpenAd] bị chặn trước `logAdStartAction` (lượt đi tiếp phát ở đó), ready = slot đã
     * load lúc gọi (1.7.0 Task 2: mỗi lượt gọi đúng một event, diff chỉ thêm dòng).
     */
    private fun reportBlockedAttempt(realPl: String) = AdEventLog.showAttempt(
        "opened", realPl, if (h.hasPlacementConfig() && h.getAdsPlacementConfig(realPl, 1)?.isLoaded == true) 1 else 0,
        ScreenName.name
    )

    private fun showOpenAdCircle(
        plcf: AdsPlacementConfig, listShow: MutableList<Int>
    ): Boolean {
        LogUtil.logD("ads full helper ${plcf.placement} showOpenAdCircle")
        var reShow = false
        val showFails = WaterfallShowFailHold(plcf.placement)
        if (listShow.isEmpty()) {
            LogEventServer.logAdShowFailWithReason(
                placement = plcf.placement,
                reason = Constants.AdShowFailReason.LIST_SHOW_EMPTY
            )
        }
        while (listShow.size > 0) {
            val typeShow = listShow[0]
            listShow.removeAt(0)
            val adsShow = h.fullNetwork(typeShow)
            if (adsShow != null) {
                reShow = showFails.during { adsShow.showAd(plcf.placement, object : AdsShowCb {
                    override fun onAdShowFail(
                        placement: String?, originPl: String?, adId: String?, err: String?
                    ) {
                        // Báo trong lúc showAd(): giữ tới cuối vòng, không tới app giữa waterfall.
                        if (showFails.hold { onAdShowFail(placement, originPl, adId, err) }) return
                        h.finishFull2Transition()
                        plcf.isShowing = false
                        // BUG-FIX: decrement + clamp âm trong 1 thao tác atomic, tránh check-then-act race
                        h.flagAdsFullShowing.updateAndGet { (it - 1).coerceAtLeast(0) }
                        h.openTimeShow = System.currentTimeMillis() / 1000
                        val newFlagShowFaild = h.flagAdsFull4ShowFaild.incrementAndGet()
                        if (newFlagShowFaild >= 2 || (newFlagShowFaild <= 0 && newFlagShowFaild >= -1)) {
                            h.flagAdsFull4ShowFaild.set(-10)
                            plcf.cbShow?.onAdShowFail(placement, originPl, adId, err)
                        }
                        reloadAfterShow(plcf)
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
                        AdEventLog.click("opened", placement?.takeIf { it.isNotEmpty() } ?: originPl ?: plcf.placement, adId ?: "")
                        plcf.cbShow?.onAdClick(placement, originPl, adId)
                    }

                    override fun onAdDismissed(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                        plcf.isShowing = false
                        // BUG-FIX: decrement + clamp âm trong 1 thao tác atomic, tránh check-then-act race
                        h.flagAdsFullShowing.updateAndGet { (it - 1).coerceAtLeast(0) }
                        h.openTimeShow = System.currentTimeMillis() / 1000
                        val newFlagDissmiss = h.flagAdsFull4Dissmiss.incrementAndGet()
                        if (newFlagDissmiss >= 2 || (newFlagDissmiss <= 0 && newFlagDissmiss >= -1)) {
                            h.flagAdsFull4Dissmiss.set(-10)
                            plcf.cbShow?.onAdDismissed(placement, originPl, adId)
                        }
                        // One fullscreen ad per user action: Full2 chains only off interstitial and
                        // rewarded (full2FlagShow), never off an app open ad (speakerclean standard).
                        h.finishFull2Transition()
                        reloadAfterShow(plcf)
                    }
                }) }
                if (reShow) {
                    plcf.isShowing = true
                    h.flagAdsFullShowing.incrementAndGet()
                    h.openTimeShow = System.currentTimeMillis() / 1000
                    h.markAutoAdShown()
                    SegmentHooks.onFullscreenShown(System.currentTimeMillis())
                    h.flagAdsFull4ShowFaild.incrementAndGet()
                    h.flagAdsFull4Dissmiss.incrementAndGet()
                    break
                }
            } else {
                LogEventServer.logAdShowFailWithReason(
                    placement = plcf.placement,
                    reason = "${Constants.AdShowFailReason.AD_SHOW_NULL}_type_$typeShow"
                )
            }
        }
        showFails.settle(reShow)
        if (!reShow) {
            plcf.isShowing = false
        }
        return reShow
    }

    /** 2.3.0 PRELOAD (D15-230): nạp lại placement vừa hiện (dismiss/lỗi hiện) như interstitial; `full_splash` không tự nạp lại. */
    private fun reloadAfterShow(plcf: AdsPlacementConfig) {
        if (plcf.placement == PlacementDefault.PLFullSplash || h.skipLoadWithoutHost("reloadAfterShow", plcf.placement)) return
        loadOpenAdForThisTurn(plcf.placement, null)
    }

    private fun getOpenWithType(type: Int): AdsBaseFull? {
        if (h.fullNetworks().containsKey(type)) {
            return h.fullNetworks()[type]
        }
        return null
    }

    private fun getListOpenLoadShow(plcf: AdsPlacementConfig): MutableList<Int> {
        var list: MutableList<Int> = mutableListOf()
        var openType = h.currConfig.openAdtype
        var openFlagImg = h.currConfig.openAdFlagImg
        if (openFlagFirst) {
            openType = h.currConfig.openAdFirstType
            openFlagImg = h.currConfig.openAdFirstImg
        }
        if (openType == 0 || openType == 2 || (openFlagFirst && openType == 4)) {
            list.add(AdsIDDef.AdmobFullOpen)
            list.add(AdsIDDef.MaxFullOpen)
        }
        if (openType == 1 || openType == 3 || openType == 2 || (openType == 4 && !openFlagFirst)) {
            if (openFlagImg == 1) {
                list.add(AdsIDDef.AdmobFullInters)
            } else if (openFlagImg == 2) {
                list.add(AdsIDDef.AdmobFullNt)
                list.add(AdsIDDef.FBFullNt)
                list.add(AdsIDDef.MaxFullNt)
            } else if (openFlagImg == 3) {
                list.add(AdsIDDef.FBFullNt)
                list.add(AdsIDDef.AdmobFullNt)
                list.add(AdsIDDef.MaxFullNt)
            }
            var idx = h.fullIdxShow
            if (idx >= h.currConfig.fullStepCicle.size) {
                idx = 0
            }
            for (i in 0 until h.currConfig.fullStepCicle.size) {
                list.add(h.currConfig.fullStepCicle[idx])
                idx++
                if (idx >= h.currConfig.fullStepCicle.size) {
                    idx = 0
                }
            }
            for (i in 0 until h.currConfig.fullStepRe.size) {
                list.add(h.currConfig.fullStepRe[i])
            }
            if (h.currConfig.openAdtype == 3) {
                list.add(AdsIDDef.AdmobFullOpen)
                list.add(AdsIDDef.MaxFullOpen)
            }
        }
        return list
    }
}
