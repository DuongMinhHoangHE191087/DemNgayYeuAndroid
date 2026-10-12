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
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.AdStatus
import com.app.plugin.logevent.AdmobSDKInitState
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.segment.SegmentHooks
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

/**
 * Vùng interstitial của [AdsHelper]: load/ready và cờ "đã load" (1.5.0 Task 10, G8), show + cổng brain (Task 11).
 * Chỉ đổi chủ sở hữu mã: thân hàm giữ nguyên, trạng thái dùng chung đọc qua [h] ở mỗi lần gọi (không giữ bản sao — test
 * gán `listAdsFull` của AdsHelper bằng reflection sau khi dựng). `listAdsFull` (R1) đọc qua `h.fullNetworks()`;
 * `fullIdxShow`, `fullCount4Circle`, `fullTimeShow`, `flagAdsFullShowing`, `flagAdsFull4ShowFaild`/`flagAdsFull4Dissmiss`
 * (R3) ở lại façade. [fullNetwork] (trước là `getIntersWithType`) và [dropStaleLoadedFlag] dùng chung với native full,
 * app open, Full2 qua façade `h.fullNetwork`/`h.dropStaleLoadedFlag`. [readyCheckOffMainLogged] thuộc vùng này.
 * `loadInterstitialForThisTurn` giữ hai tham số mặc định như bản cũ: lambda nạp lại của [isInterstitialReadyToShow],
 * nhánh "không mạng nào hiện" của [showInterstitial] và hai lời gọi trong `object : AdsShowCb` của vòng show dựa vào
 * chúng, chuyển nguyên văn. Vòng show gọi chéo vùng qua façade (`h.showFull2`, `h.beginFull2Transition`,
 * `h.finishFull2Transition`, `h.markAutoAdShown`, `h.onAdClicked`). KDoc public của `isInterstitialLoaded`,
 * `isInterstitialReadyToShow` ở façade.
 */
internal class InterstitialController(private val h: AdsHelper) {
    fun loadInterstitialForThisTurn(
        placement: String,
        flagImg: Int,
        loadCb: AdsLoadCb? = null,
        isLoadFull2: Boolean = true
    ) {
        var realPl = placement
        if (!placement.startsWith("full_")) {
            realPl = "full_" + placement
        }

        if (h.skipLoadWithoutHost("loadInterstitialForThisTurn", realPl)) {
            loadCb?.onLoadFailed(placement, realPl, "", h.loadFailNoHostLabel())
            return
        }

        if (h.isRemoveAds(4) || h.isDisableAds(4)) {
            LogUtil.logD("ads full helper $realPl loadInterstitialForThisTurn isremoveads or disable")
            loadCb?.onLoadFailed(
                placement,
                realPl,
                "",
                h.disabledReason(4, "loadInterstitialForThisTurn isremoveads or disable")
            )
            return
        }

        if (!h.isIntersAllowLoadAds()) {
            LogUtil.logD("ads full helper $realPl loadInterstitialForThisTurn not met condition")
            loadCb?.onLoadFailed(
                placement,
                realPl,
                "",
                "loadInterstitialForThisTurn not met condition"
            )
            return
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads full helper $realPl loadInterstitialForThisTurn cf not")
                loadCb?.onLoadFailed(placement, realPl, "", "cf not")
                return
            } else {
                if (!plcf.isLoading) {
                    LogUtil.logD("ads full helper ${plcf.placement} loadInterstitialForThisTurn call load")
                    plcf.isLoading = true
                    plcf.cbLoad = loadCb
                    loadInterstitialCircle(plcf, getListIntersLoadShow(plcf, flagImg), plcf.loadCycle)
                    if (flagImg <= 0 && (h.currConfig.full2FlagShow == 1 || h.currConfig.full2FlagShow == 3) && isLoadFull2) {
                        h.loadFull2ForThisTurn(placement)
                    }
                } else if (loadCb != null && !LoadCallbacks.holds(plcf.cbLoad, loadCb)) {
                    // 2.4.0 CARRY-ADS #17 (D26-230 I1 mở rộng): lượt đang nạp (của SDK hay của app) → cb vào danh sách của lượt;
                    // mỗi cb nhận kết quả thật đúng một lần, theo thứ tự gắn (trước: cb thứ hai nhận "…loading").
                    LogUtil.logD("ads full helper $realPl loadInterstitialForThisTurn loading, attaching callback")
                    LoadCallbacks.attachLoadCallback(plcf, OneShotLoadCb(loadCb))
                }
            }
        } else {
            LogUtil.logD("ads full helper $realPl loadInterstitialForThisTurn not cf")
            loadCb?.onLoadFailed(placement, realPl, "", "loadInterstitialForThisTurn not cf")
        }
    }

    private fun loadInterstitialCircle(plcf: AdsPlacementConfig, listLoad: MutableList<Int>, cycle: Int) {
        LogUtil.logD("ads full helper ${plcf.placement} loadInterstitialCircle")
        if (cycle != plcf.loadCycle) {
            LogUtil.logD("ads helper ${plcf.placement} loadInterstitialCircle stale cycle dropped")
            return
        }
        if (listLoad.size > 0) {
            val typeLoad = listLoad[0]
            listLoad.removeAt(0)
            val adsLoad = fullNetwork(typeLoad)
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
                                loadInterstitialCircle(plcf, listLoad, cycle)
                            }
                        } else {
                            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "loadInterstitialCircle isLoading=false on fail")
                        }
                    }
                })
            } else {
                Handler(Looper.getMainLooper()).post {
                    loadInterstitialCircle(plcf, listLoad, cycle)
                }
            }
        } else {
            plcf.isLoading = false
            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "list empty")
        }
    }

    fun showInterstitial(
        placement: String,
        flagImage: Int,
        cbShow: AdsShowCb,
        isDeltaTime: Boolean
    ): Boolean = showInterstitial(placement, flagImage, cbShow, isDeltaTime, fixedMoment = false)

    /**
     * 2.4.2 FIXED (Hợp đồng A, D2-SC): [fixedMoment] `true` = thời điểm cố định app muốn có quảng cáo mỗi lần (hết
     * onboarding, dọn xong): bỏ qua guardrail segment ([SegmentHooks.blocksFullscreen]), `interstitialRefusal()`
     * (`fullStartTime`, luật phiên/khoảng cách), cooldown N màn và brain; [isDeltaTime] khi đó vô nghĩa. Vẫn chặn: quảng
     * cáo đang hiện, `isRemoveAds(4)`/`isDisableAds(4)` (kill, giấy phép, mask), `flagShow < 0`. Lượt cố định của
     * `full_intro_3` nối Full2 như fork (`FullScreenAdPolicy.shouldChainFull2AfterInterstitial` ba tham số).
     */
    fun showInterstitial(
        placement: String,
        flagImage: Int,
        cbShow: AdsShowCb,
        isDeltaTime: Boolean,
        fixedMoment: Boolean
    ): Boolean {
        // Cổng tần suất (luật tần suất, cooldown N màn, brain) chỉ áp cho lượt thường có isDeltaTime.
        val capped = isDeltaTime && !fixedMoment
        var realPl = placement
        if (!placement.startsWith("full_")) {
            realPl = "full_" + placement
        }

        // BUG-FIX: clamp âm và check trong 1 thao tác atomic, tránh check-then-act race
        if (h.flagAdsFullShowing.updateAndGet { if (it < 0) 0 else it } > 0) {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showInterstitial isAdsFullShowing")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.SHOW_FULL_WHEN_FLAG_SHOWING
            )
            return false
        }

        if (h.isRemoveAds(4) || h.isDisableAds(4)) {
            reportBlockedAttempt(realPl)
            h.reportDisabledShow(4, realPl, Constants.AdShowFailReason.SHOW_FULL_WHEN_REMOVE_ADS)
            return false
        }

        // 2.2.0 SEG: guardrail chéo loại của segment (mặc định tắt; RC seg_cadence_guardrail_seconds), mọi placement như fork.
        if (!fixedMoment && SegmentHooks.blocksFullscreen(System.currentTimeMillis())) {
            reportBlockedAttempt(realPl)
            LogEventServer.logAdShowFailWithReason(placement = realPl, reason = SegmentHooks.REASON_GUARDRAIL)
            return false
        }

        if (capped) {
            h.interstitialRefusal()?.let { reason ->
                reportBlockedAttempt(realPl)
                LogUtil.logD("ads full helper $realPl showInterstitial refused: $reason")
                LogEventServer.logAdShowFailWithReason(placement = realPl, reason = reason)
                return false
            }
        }

        // Cùng điều kiện isDeltaTime với brain bên dưới: splash/intro là luồng call site tự quyết.
        if (capped && h.screenCooldown().isActive) {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showInterstitial within post-ad screen cooldown")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.POST_AD_SCREEN_COOLDOWN
            )
            return false
        }

        // ═══ Monetization Brain (mặc định TẮT) ═══
        //
        // Đặt SAU mọi cổng tĩnh phía trên là có chủ đích: brain chỉ được SIẾT CHẶT hơn, không
        // bao giờ nới lỏng. Lượt nào luật cũ đã chặn thì vẫn bị chặn y như trước; brain chỉ
        // thêm một câu hỏi nữa cho những lượt lẽ ra đã được hiện.
        //
        // Chỉ áp khi `isDeltaTime` — `isDeltaTime = false` là cách call site nói "lượt này do
        // tôi quyết định" (splash/intro). Để brain phủ quyết ở đó sẽ phá vỡ những luồng bắt
        // buộc mà nó không có đủ ngữ cảnh để đánh giá.
        // Dùng `enabled` chứ không phải cổng rollout: người ngoài rollout vẫn được cập nhật
        // bộ đếm (các hàm `should*` tự trả true cho họ, và `observe()` tự bỏ qua). Nhờ vậy
        // khi nâng rollout từ xa giữa phiên, những người vừa vào nhóm không bắt đầu từ trạng
        // thái rỗng — nếu không, trần "mỗi phiên" của họ sẽ tính lại từ 0 và cho phép một
        // đợt quảng cáo dày bất thường ngay sau mỗi lần mở rộng.
        val brainConsulted = capped && com.app.plugin.brain.AdsBrain.enabled
        if (brainConsulted && !com.app.plugin.brain.AdsBrain.shouldShowInterstitial()) {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showInterstitial brain declined")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.SHOW_FULL_BRAIN_DECLINED
            )
            return false
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                reportBlockedAttempt(realPl)
                LogUtil.logD("ads full helper $realPl showInterstitial cf not")
                LogEventServer.logAdShowFailWithReason(placement = realPl, reason = Constants.AdShowFailReason.CONFIG_NOT)
                if (brainConsulted) com.app.plugin.brain.AdsBrain.onAdSkipped()
                return false
            } else {
                if (!plcf.isShowing) {
                    LogUtil.logD("ads full helper ${plcf.placement} showInterstitial call show")
                    plcf.cbShow = cbShow
                    h.fullCount4Circle = 0
                    h.flagAdsFull4ShowFaild.set(-2)
                    h.flagAdsFull4Dissmiss.set(-2)

                    LogEventServer.logAdStartAction(
                        adFormat = "interstitial",
                        placement = realPl,
                        isAction = if (plcf.isLoaded) AdStatus.Loaded.code else AdStatus.NotLoaded.code,
                        screenName = ScreenName.name,
                        isSDKInit = AdmobSDKInitState.isInitComplete
                    )

                    val reShow =
                        showInterstitialCircle(plcf, getListIntersLoadShow(plcf, flagImage), fixedMoment)
                    if (brainConsulted) {
                        // Brain đồng ý nhưng ad không hiện được (hết fill, SDK lỗi) là
                        // "không giao được giá trị". Phải báo lại: bỏ qua thì quyết định
                        // treo sẽ bị chốt nhầm thành có giá trị, và bandit học rằng
                        // hiện quảng cáo luôn thành công.
                        if (reShow) com.app.plugin.brain.AdsBrain.onInterstitialShown()
                        else com.app.plugin.brain.AdsBrain.onAdSkipped()
                    }
                    // Không mạng nào hiện được: cờ đã load (nếu còn bật) là cũ, hạ trước khi nạp lại.
                    if (!reShow) plcf.isLoaded = false
                    if (realPl != PlacementDefault.PLFullIntro3) {
                        if (!reShow) {
                            loadInterstitialForThisTurn(realPl, flagImage)
                        }
                    }
                    return reShow
                } else {
                    reportBlockedAttempt(realPl)
                    LogUtil.logD("ads full helper $realPl showInterstitial is ads showing")
                    LogEventServer.logAdShowFailWithReason(placement = realPl, reason = Constants.AdShowFailReason.AD_SHOWING)
                    if (brainConsulted) com.app.plugin.brain.AdsBrain.onAdSkipped()
                    return false
                }
            }
        } else {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads full helper $realPl showInterstitial has not cf")
            LogEventServer.logAdShowFailWithReason(placement = realPl, reason = Constants.AdShowFailReason.NOT_CONFIG)
            if (brainConsulted) com.app.plugin.brain.AdsBrain.onAdSkipped()
            return false
        }
    }

    /**
     * `ad_show_attempt` của lượt [showInterstitial] bị chặn trước `logAdStartAction` (lượt đi tiếp phát ở đó), ready = slot
     * đã load lúc gọi (1.7.0 Task 2: mỗi lượt gọi đúng một event, diff chỉ thêm dòng).
     */
    private fun reportBlockedAttempt(realPl: String) = AdEventLog.showAttempt(
        "interstitial", realPl, if (h.hasPlacementConfig() && h.getAdsPlacementConfig(realPl, 1)?.isLoaded == true) 1 else 0,
        ScreenName.name
    )

    private fun showInterstitialCircle(
        plcf: AdsPlacementConfig, listShow: MutableList<Int>, fixedMoment: Boolean
    ): Boolean {
        LogUtil.logD("ads full helper ${plcf.placement} showInterstitialCircle")
        var reShow = false
        val showFails = WaterfallShowFailHold(plcf.placement)
        while (listShow.size > 0) {
            val typeShow = listShow[0]
            listShow.removeAt(0)
            h.fullCount4Circle++
            if (h.fullCount4Circle <= h.currConfig.fullStepCicle.size) {
                h.fullIdxShow++
                if (h.fullIdxShow >= h.currConfig.fullStepCicle.size) {
                    h.fullIdxShow = 0
                }
            }
            val adsShow = fullNetwork(typeShow)
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
                        h.fullTimeShow = System.currentTimeMillis() / 1000
                        val newFlagShowFaild = h.flagAdsFull4ShowFaild.incrementAndGet()
                        if (newFlagShowFaild >= 2 || (newFlagShowFaild <= 0 && newFlagShowFaild >= -1)) {
                            h.flagAdsFull4ShowFaild.set(-10)
                            plcf.cbShow?.onAdShowFail(placement, originPl, adId, err)
                        }
                        if (originPl == PlacementDefault.PLFullSplash || originPl == PlacementDefault.PLFull2Splash || originPl == PlacementDefault.PLFullIntro3) return
                        loadInterstitialForThisTurn(PlacementDefault.PLFullDefault, 0, null)
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
                        AdEventLog.click("interstitial", placement?.takeIf { it.isNotEmpty() } ?: originPl ?: plcf.placement, adId ?: "")
                        h.onAdClicked()
                        plcf.cbShow?.onAdClick(placement, originPl, adId)
                    }

                    override fun onAdDismissed(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                        plcf.isShowing = false
                        // BUG-FIX: decrement + clamp âm trong 1 thao tác atomic, tránh check-then-act race
                        h.flagAdsFullShowing.updateAndGet { (it - 1).coerceAtLeast(0) }
                        h.fullTimeShow = System.currentTimeMillis() / 1000
                        val newFlagDissmiss = h.flagAdsFull4Dissmiss.incrementAndGet()
                        if (newFlagDissmiss >= 2 || (newFlagDissmiss <= 0 && newFlagDissmiss >= -1)) {
                            h.flagAdsFull4Dissmiss.set(-10)
                            plcf.cbShow?.onAdDismissed(placement, originPl, adId)
                        }

                        // Same policy as the show side below, so a chain is never started without
                        // the cover (the old check here still chained after full_splash). 2.4.2 FIXED: chain under
                        // the placement asked to show, not the network slot `originPl` ("full_default" for every
                        // placement without its own ids, which named each Full2 full_2_default, as in the fork).
                        if (h.fullscreenPolicy().shouldChainFull2AfterInterstitial(
                                full2FlagShow = h.currConfig.full2FlagShow,
                                originPlacement = plcf.placement,
                                fixedMoment = fixedMoment,
                            )
                        ) {
                            h.showFull2(plcf.placement, plcf.cbShow)
                        } else {
                            // Config can change while the parent ad is open. Balance a
                            // transition which may have been armed under the previous value.
                            h.finishFull2Transition()
                        }
                        if (originPl == PlacementDefault.PLFullSplash || originPl == PlacementDefault.PLFull2Splash || originPl == PlacementDefault.PLFullIntro3) return
                        loadInterstitialForThisTurn(PlacementDefault.PLFullDefault, 0, null)
                    }
                }) }
                if (reShow) {
                    plcf.isShowing = true
                    h.flagAdsFullShowing.incrementAndGet()
                    h.fullTimeShow = System.currentTimeMillis() / 1000
                    h.markAutoAdShown()
                    SegmentHooks.onFullscreenShown(System.currentTimeMillis())
                    if (h.fullscreenPolicy().shouldChainFull2AfterInterstitial(
                            h.currConfig.full2FlagShow,
                            plcf.placement,
                            fixedMoment,
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

    fun fullNetwork(type: Int): AdsBaseFull? {
        if (h.fullNetworks().containsKey(type)) {
            return h.fullNetworks()[type]
        }
        return null
    }

    fun isInterstitialLoaded(placement: String): Boolean {
        if (!h.hasPlacementConfig()) return false
        val realPl = if (placement.startsWith("full_")) placement else "full_$placement"
        return h.getAdsPlacementConfig(realPl, 1)?.isLoaded == true
    }

    /** 2.4.2: cùng kết luận với `showInterstitial(placement, flagImage, cb)` mặc định, kể cả guardrail segment. */
    fun isInterstitialReadyToShow(placement: String, flagImage: Int): Boolean =
        isInterstitialReadyToShow(placement, flagImage, isDeltaTime = true, fixedMoment = false)

    /** Cùng kết luận với [showInterstitial] cùng [isDeltaTime]/[fixedMoment] (trừ brain), cộng host đã resume. */
    fun isInterstitialReadyToShow(placement: String, flagImage: Int, isDeltaTime: Boolean, fixedMoment: Boolean): Boolean {
        warnIfReadyCheckOffMain()
        if (!h.hasPlacementConfig()) return false
        val realPl = if (placement.startsWith("full_")) placement else "full_$placement"
        val activity = h.hostActivity()
        if (!h.fullscreenPolicy().isHostReadyForFullscreen() ||
            activity == null || activity.isFinishing || activity.isDestroyed
        ) {
            return false
        }
        if (h.flagAdsFullShowing.get() > 0 || h.isRemoveAds(4) || h.isDisableAds(4)) return false
        if (!fixedMoment && SegmentHooks.blocksFullscreen(System.currentTimeMillis())) return false
        if (isDeltaTime && !fixedMoment && (h.interstitialRefusal() != null || h.screenCooldown().isActive)) return false
        val plcf = h.getAdsPlacementConfig(realPl, 1) ?: return false
        if (plcf.flagShow < 0 || plcf.isShowing || !plcf.isLoaded) return false
        val types = getListIntersLoadShow(plcf, flagImage)
        val ready = types.any { type -> fullNetwork(type)?.hasLoadedAd(plcf.placement) == true }
        if (!ready) {
            dropStaleLoadedFlag(plcf, "isInterstitialReadyToShow") {
                loadInterstitialForThisTurn(realPl, flagImage)
            }
        }
        return ready
    }

    /** Cảnh báo [isInterstitialReadyToShow] ngoài main thread chỉ ghi một lần mỗi process. */
    private val readyCheckOffMainLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun warnIfReadyCheckOffMain() {
        if (Looper.myLooper() == Looper.getMainLooper()) return
        if (readyCheckOffMainLogged.compareAndSet(false, true)) {
            LogUtil.logE("ads full helper isInterstitialReadyToShow called off the main thread; call it on main")
        }
    }

    /**
     * Cờ đã load của [plcf] bật mà không mạng toàn màn hình nào còn giữ quảng cáo: quảng cáo vừa bị bỏ vì quá tuổi
     * (`ad_expired`, gửi ở `AdsEcmps.dropExpired`, không gửi ở đây) hay đã được dùng. Hạ cờ ([isInterstitialLoaded]
     * `false` ngay) và nạp lại placement qua [reload] (cùng đường "show not loaded"). Đúng một lần: lời gọi sau thấy
     * cờ đã hạ nên không nạp nữa, và hàm load tự bỏ khi placement đang load (review E I2).
     *
     * Slot của một mạng đang tải thì không làm gì (giữ cờ, không nạp): nhiều placement dùng chung slot
     * (`AdsBase.getAdsForPl`: `full_home`, `full_tab_switch` → `full_default`), slot đó đang nạp lại sau lần show của
     * placement khác và sẽ có quảng cáo cho placement này. Nạp lúc đó gặp `"isloading"` rồi tải thừa ở mạng sau
     * (review EF Q1). Xét mọi mạng toàn màn hình của placement ([allFullTypes]), không chỉ danh sách của lời gọi: cờ
     * là của placement, không của `flagImage`, native full đã load vẫn giữ cờ khi app hỏi với danh sách
     * interstitial (review EF Q3). Mạng còn giữ đối tượng nhưng chưa sẵn sàng (MAX đang tự nạp lại trong cùng đối
     * tượng, `isReady() == false`, Task J4) cũng là bận (`holdsLoadedAd`, không phải `hasLoadedAd`): giữ cờ, không nạp
     * lại — nạp lúc đó chỉ được slot trả lại chính quảng cáo chưa sẵn sàng ("has loaded") rồi hạ cờ/nạp lại vòng vòng.
     */
    fun dropStaleLoadedFlag(plcf: AdsPlacementConfig, function: String, reload: () -> Unit) {
        if (!plcf.isLoaded) return
        val pl = plcf.placement
        val busy = allFullTypes(plcf).any { type ->
            fullNetwork(type)?.let { ad -> ad.isLoading(pl) || ad.holdsLoadedAd(pl) } == true
        }
        if (busy) {
            LogUtil.logD("ads helper $pl $function: a full-screen network holds or loads an ad; keep flag")
            return
        }
        plcf.isLoaded = false
        LogUtil.logD("ads helper ${plcf.placement} $function: loaded flag set but no network holds an ad, reload")
        reload()
    }

    /** Mọi mạng toàn màn hình một placement có thể được load (mọi `flagImage`: waterfall interstitial, native full, low). */
    private fun allFullTypes(plcf: AdsPlacementConfig): List<Int> =
        (getListIntersLoadShow(plcf, 0) + getListIntersLoadShow(plcf, 3)).distinct()

    private fun getListIntersLoadShow(plcf: AdsPlacementConfig, flagImage: Int): MutableList<Int> {
        var list: MutableList<Int> = mutableListOf()
        if (flagImage == 1) {
            list.add(AdsIDDef.AdmobFullNt)
            list.add(AdsIDDef.FBFullNt)
            list.add(AdsIDDef.MaxFullNt)
            return list
        } else if (flagImage == 2) {
            list.add(AdsIDDef.AdmobFullLow)
            return list
        } else if (flagImage == 3) {
            list.add(AdsIDDef.AdmobFullNt)
            list.add(AdsIDDef.FBFullNt)
            list.add(AdsIDDef.MaxFullNt)
            list.add(AdsIDDef.AdmobFullLow)
            return list
        } else if (flagImage == 4) {
            list.add(AdsIDDef.AdmobFullLow)
            list.add(AdsIDDef.AdmobFullNt)
            list.add(AdsIDDef.FBFullNt)
            list.add(AdsIDDef.MaxFullNt)
            return list
        } else if (flagImage == 5) {
            list.add(AdsIDDef.FBFullNt)
            list.add(AdsIDDef.AdmobFullNt)
            list.add(AdsIDDef.MaxFullNt)
            list.add(AdsIDDef.AdmobFullLow)
            return list
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

        return list
    }
}
