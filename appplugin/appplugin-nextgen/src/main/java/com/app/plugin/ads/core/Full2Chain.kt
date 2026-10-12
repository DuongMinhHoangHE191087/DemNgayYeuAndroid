package com.app.plugin.ads.core

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.Full2DefaultCover
import com.app.plugin.ads.Full2TransitionListener
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.segment.SegmentHooks
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

/**
 * Chuỗi Full2 của [AdsHelper] (1.5.0 Task 12, G8): chuyển cảnh/cover (vùng 3) và show/load Full2 (vùng 17). Chỉ đổi chủ
 * sở hữu mã: thân hàm giữ nguyên văn, trạng thái dùng chung đọc qua [h] ở mỗi lần gọi (R2). Ở lại façade (R1, test
 * đọc/gán bằng reflection): `full2MainHandler` (qua `h.full2Handler()`), `pendingFull2` và `private data class
 * PendingFull2` (qua `h.queueFull2`/`h.pendingFull2Placement`/`h.pendingFull2Callback`/`h.clearPendingFull2`, chỉ nhận/
 * trả thành phần), `full2TransitionActive` (qua `h.isFull2TransitionActive`/`h.setFull2TransitionActive`); R3:
 * `full2TimeShow`, `full2IdxShow`, `full2Count4Circle`, `flagAdsFull*`, `suppressOpenAdUntilMillis`. Chuyển theo
 * controller: listener của app, hai Runnable hẹn giờ, cover mặc định. Giao thức một-dismiss: [showFull2Circle] dùng
 * chung `flagAdsFull4ShowFaild`/`flagAdsFull4Dissmiss` với quảng cáo cha, chỉ tăng và so, không đặt lại — `set(-10)`
 * của cha chặn dismiss thứ hai tới app. Controller khác gọi qua façade (`h.showFull2`, `h.beginFull2Transition`,
 * `h.finishFull2Transition`, `h.loadFull2ForThisTurn`), không tham chiếu lớp này (R5).
 */
internal class Full2Chain(private val h: AdsHelper) {
    private var full2TransitionListener: Full2TransitionListener? = null
    private val full2TransitionTimeout = Runnable { finishFull2Transition() }
    private val full2VisibleFallback = Runnable {
        LogUtil.logD("full2 transition fallback clear: impression callback missing")
        finishFull2Transition()
    }

    fun setFull2TransitionListener(listener: Full2TransitionListener?) {
        full2TransitionListener = listener
        // An app-provided cover replaces the default one; never show both.
        if (listener != null) defaultFull2Cover.hide()
        notifyFull2Transition(h.isFull2TransitionActive())
    }

    // Used only while the app has registered no Full2TransitionListener (see Full2DefaultCover).
    private val defaultFull2Cover = Full2DefaultCover()

    /** Main thread only. Routes the cover state to the app's listener, or to the default cover. */
    private fun notifyFull2Transition(active: Boolean) {
        val listener = full2TransitionListener
        if (listener != null) {
            listener.onTransitionChanged(active)
            return
        }
        if (active) {
            defaultFull2Cover.show(h.hostActivity(), h.fullscreenPolicy().isHostReadyForFullscreen())
        } else {
            defaultFull2Cover.hide()
        }
    }

    fun beginFull2Transition(parentPlacement: String) {
        if (full2SwitchedOff(full2Name(parentPlacement))) return   // 2.4.2 SWITCH: Full2 tắt thì không dựng lớp che
        full2Shows(full2Name(parentPlacement))
        h.full2Handler().removeCallbacks(full2TransitionTimeout)
        h.full2Handler().removeCallbacks(full2VisibleFallback)
        h.setFull2TransitionActive(true)
        h.full2Handler().post { notifyFull2Transition(true) }
        // The parent ad stays on screen for at least its close countdown: the one window in which
        // a Full2 that is not loaded yet can still load in time. A reload fired only at dismissal
        // filled 3.4s after the chain had given up (measured in speakerclean). When an ad is
        // already held the network layer answers "has loaded" and stops, so this costs nothing.
        loadFull2ForThisTurn(parentPlacement)
        // Native-full is rendered by this app, not by the network. Inflating it at show time ran
        // after the parent was gone, so the user saw their own screen for a layout pass - two
        // separate ads rather than one hand-off. Build it now, behind the parent.
        h.full2Handler().post { prepareChainedFull2() }
        // The timeout is not armed here: rewarded and interstitial creatives routinely stay open
        // far longer than three seconds. It starts when dismissal begins the actual hand-off.
    }

    /**
     * Asks every network that could serve the chained Full2 to pre-build what it can. Goes wide
     * rather than resolving the exact placement: showFull2 decides that later from config that
     * can change in between, and preparing is a no-op for formats the network renders itself.
     */
    private fun prepareChainedFull2() {
        try {
            for (type in h.currConfig.full2StepCicle.distinct()) {
                h.fullNetwork(type)?.prepareChainedShow()
            }
        } catch (e: Throwable) {
            LogUtil.logD("full2 prepare skipped: ${e.message}")
        }
    }

    private fun cancelChainedFull2() {
        try {
            for (type in h.currConfig.full2StepCicle.distinct()) {
                h.fullNetwork(type)?.cancelChainedShow()
            }
        } catch (e: Throwable) {
            LogUtil.logD("full2 cancel skipped: ${e.message}")
        }
    }

    private fun armFull2TransitionTimeout() {
        if (!h.isFull2TransitionActive()) return
        h.full2Handler().removeCallbacks(full2TransitionTimeout)
        h.full2Handler().removeCallbacks(full2VisibleFallback)
        h.full2Handler().postDelayed(full2TransitionTimeout, 3_000L)
    }

    fun finishFull2Transition() {
        full2Shows(null)
        h.full2Handler().removeCallbacks(full2TransitionTimeout)
        h.full2Handler().removeCallbacks(full2VisibleFallback)
        val wasActive = h.isFull2TransitionActive()
        h.setFull2TransitionActive(false)
        // clearActivity() đã báo false cho host đang huỷ ngay trước cửa sổ isReleasingHost; lời gọi trong cửa sổ (cha
        // đóng vì host huỷ: Full2 bị bỏ, hay chuỗi không nối) không báo lần hai (Điều 13: đúng một lần, #6).
        if (h.isReleasingHost() && !wasActive) return
        // Tell the host every time, including when this side was already inactive. The host may
        // raise its cover whenever a fullscreen ad starts, while this side arms only when the
        // chain is configured - and full2FlagShow can change while the parent is open. An early
        // return here left a black cover on screen after an interstitial until the user
        // backgrounded the app (speakerclean).
        h.full2Handler().post { notifyFull2Transition(false) }
        if (!wasActive) return
        // A dialog built for a chain that did not show holds its native ad; the next chain would
        // reuse it against an ad that may already be destroyed.
        h.full2Handler().post { cancelChainedFull2() }
    }

    private fun awaitFull2VisibleImpression() {
        h.full2Handler().removeCallbacks(full2TransitionTimeout)
        h.full2Handler().removeCallbacks(full2VisibleFallback)
        h.full2Handler().postDelayed(full2VisibleFallback, 1_500L)
    }

    /** [AdsHelper.onMainActivityResumed], sau `fullScreenAdPolicy.onHostActivityResumed()` (thứ tự cũ). */
    fun onHostResumed(activity: Activity) {
        defaultFull2Cover.onHostResumed()
        armFull2TransitionTimeout()
        val pendingPlacement = h.pendingFull2Placement() ?: return
        val pendingCallback = h.pendingFull2Callback()
        h.clearPendingFull2()
        h.full2Handler().post {
            if (h.hostActivity() === activity && !activity.isFinishing && !activity.isDestroyed) {
                showFull2(pendingPlacement, pendingCallback)
            }
        }
    }

    /** [AdsHelper.onMainActivityPaused] của đúng host, sau `fullScreenAdPolicy.onHostActivityPaused()`. */
    fun onHostPaused() {
        defaultFull2Cover.onHostPaused()
    }

    /** Đầu [AdsHelper.clearActivity], sau `fullScreenAdPolicy.onHostActivityPaused()`, trước `mActivity = null`. */
    fun onHostClearing() {
        h.full2Handler().removeCallbacksAndMessages(null)
        finishFull2Transition()
        // finishFull2Transition() posts its notification, and the queue was just cleared above;
        // drop the default cover directly so it cannot outlive the activity it was added to.
        defaultFull2Cover.hide()
    }

    /** Khối `finally` của [AdsHelper.clearActivity], sau cả hai lượt nhả/clear mạng (chú thích ở đó). */
    fun dropPending() {
        h.clearPendingFull2()
    }

    /** Móc Full2 của [AdsHelper.onAdLoaded], chạy trước telemetry (thứ tự cũ, R-6). */
    fun onNativeFullLoaded(adFormat: String) {
        // A native-full landing while a chain is armed is the reload beginFull2Transition() started
        // behind the parent. prepareChainedFull2() found nothing to build then; build it now so a
        // late fill still gets its dialog behind the parent. Idempotent per placement.
        if (h.isFull2TransitionActive() && adFormat == "native_full") {
            h.full2Handler().post { prepareChainedFull2() }
        }
    }

    /** 2.5.0 PRIO (Fable C1d): Full2 nối [key] (`full2Name(parent)`) có show đang chờ ⇒ mức SHOW ở cổng unit; `null` ⇒ bỏ. */
    private fun full2Shows(key: String?) {
        full2Demand?.let { UnitGate.demand(it, false) }
        full2Demand = key?.also { UnitGate.demand(it, true) }
    }
    private var full2Demand: String? = null

    /** Tên Full2 nối sau [placement] (`full_x`/`gift_x`/`x` → `full_2_x`; `full_2_*` giữ nguyên). */
    private fun full2Name(placement: String): String {
        var realPl = placement
        if (placement.startsWith("full_")) {
            if (!placement.startsWith("full_2_")) {
                realPl = placement.replace("full_", "full_2_")
            }
        } else if (placement.startsWith("gift_")) {
            realPl = placement.replace("gift_", "full_2_")
        }

        if (!realPl.startsWith("full_2_")) {
            realPl = "full_2_" + placement
        }
        return realPl
    }

    /** 2.4.2 SWITCH (Hợp đồng B): Full2 [realPl] tắt khi chính nó hoặc `full_2_default` có `flag < 0` trong `cf_ads_placement`. */
    private fun full2SwitchedOff(realPl: String): Boolean =
        !h.isPlacementEnabled(realPl) || !h.isPlacementEnabled(PlacementDefault.PLFull2Default)

    fun showFull2(placement: String, cbShow: AdsShowCb?) {
        armFull2TransitionTimeout()
        val realPl = full2Name(placement)
        if (full2SwitchedOff(realPl)) {
            LogUtil.logD("ads full helper $realPl showFull2 placement switched off")
            finishFull2Transition()
            return
        }

        // #6: trong cửa sổ clearActivity, cha đóng vì host bị huỷ (xoay, đổi ngôn ngữ, dark mode), không phải người dùng
        // đóng. Không nối Full2 lên màn hình tái tạo (Play: quảng cáo toàn màn hình "show unexpectedly"), không xếp
        // pendingFull2 (sẽ giữ cbShow của app, tức Activity cũ, tới lần resume kế).
        if (h.isReleasingHost()) {
            LogUtil.logD("ads full helper $realPl showFull2 skipped: parent closed by host destroy")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.HOST_DESTROYED
            )
            finishFull2Transition()
            return
        }

        // showFull2 is chained off the parent's dismissal and, unlike every other show entry point,
        // never checked entitlement: a user who had just watched a rewarded ad for ad-free time, or
        // who had bought remove-ads, still got a Full2 right after (fixed in speakerclean).
        if (h.isRemoveAds(4) || h.isDisableAds(4)) {
            LogUtil.logD("ads full helper $realPl showFull2 isremoveads or disable")
            finishFull2Transition()
            return
        }
        // Only the suppression windows apply to this same-turn chain: the app's own outbound
        // intents (suppressNextOpenAd) and the return from an ad click. Cooldowns keyed off the
        // parent ad must not, or the chain would be blocked by the very ad it follows.
        val now = System.currentTimeMillis()
        if (now < h.suppressOpenAdUntilMillis || h.fullscreenPolicy().shouldSuppressAutomaticFullscreen(now)) {
            LogUtil.logD("ads full helper $realPl showFull2 blocked by paywall suppress")
            finishFull2Transition()
            return
        }
        // Nothing loaded means nothing to hand off to: lower the cover now, while the parent ad's
        // activity is still on screen, instead of parking it on the host's first frame - that
        // frame was the black flash seen after closing an interstitial with no native behind it.
        h.getAdsPlacementConfig(realPl, 1)?.let { earlyPlcf ->
            val anyLoaded = getListFull2LoadShow(earlyPlcf).any { type ->
                h.fullNetwork(type)?.hasLoadedAd(earlyPlcf.placement) == true
            }
            if (!anyLoaded) {
                LogUtil.logD("ads full helper $realPl showFull2 nothing loaded; cover released before host resume")
                finishFull2Transition()
                loadFull2ForThisTurn(realPl)
                return
            }
        }
        // Dismiss callbacks arrive before the SDK's AdActivity has yielded the window back to the
        // host; a Dialog shown in that gap appears late and delays its countdown. Show the loaded
        // Full2 on the first resumed host frame instead.
        if (h.fullscreenPolicy().shouldDeferFull2UntilHostResumed()) {
            h.queueFull2(realPl, cbShow)
            LogUtil.logD("ads full helper $realPl queue Full2 until host resume")
            return
        }
        h.clearPendingFull2()

        // BUG-13 FIX: isFull2AllowShowAds() được định nghĩa nhưng không được gọi
        if (!h.isFull2AllowShowAds()) {
            LogUtil.logD("ads full helper $realPl showFull2 not allow show (delta time)")
            finishFull2Transition()
            return
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads full helper $realPl showFull2 cf not")
                finishFull2Transition()
            } else {
                if (!plcf.isShowing) {
                    LogUtil.logD("ads full helper ${plcf.placement} showFull2 call show")
                    plcf.cbShow = cbShow
                    h.full2Count4Circle = 0
                    val reShow = showFull2Circle(plcf, getListFull2LoadShow(plcf))
                    if (!reShow && realPl != PlacementDefault.PLFull2Splash && realPl != PlacementDefault.PLFullIntro3) { // để không bị load lại full 2 splash khi show full splash
                        finishFull2Transition()
                        loadFull2ForThisTurn(realPl)
                    } else if (!reShow) {
                        finishFull2Transition()
                    }
                } else {
                    LogUtil.logD("ads full helper $realPl showFull2 is ads showing")
                    finishFull2Transition()
                }
            }
        } else {
            LogUtil.logD("ads full helper $realPl showFull2 has not cf")
            finishFull2Transition()
        }
    }

    private fun showFull2Circle(
        plcf: AdsPlacementConfig, listShow: MutableList<Int>
    ): Boolean {
        LogUtil.logD("ads full helper ${plcf.placement} showFull2Circle")
        var reShow = false
        while (listShow.size > 0) {
            val typeShow = listShow[0]
            listShow.removeAt(0)
            h.full2Count4Circle++
            if (h.full2Count4Circle <= h.currConfig.full2StepCicle.size) {
                h.full2IdxShow++
                if (h.full2IdxShow >= h.currConfig.full2StepCicle.size) {
                    h.full2IdxShow = 0
                }
            }
            val adsShow = h.fullNetwork(typeShow)
            if (adsShow != null) {
                reShow = adsShow.showAd(plcf.placement, object : AdsShowCb {
                    override fun onAdShowFail(
                        placement: String?, originPl: String?, adId: String?, err: String?
                    ) {
                        plcf.isShowing = false
                        finishFull2Transition()
                        // BUG-FIX: decrement + clamp âm trong 1 thao tác atomic, tránh check-then-act race
                        h.flagAdsFullShowing.updateAndGet { (it - 1).coerceAtLeast(0) }
                        h.full2TimeShow = System.currentTimeMillis() / 1000
                        val newFlagShowFaild = h.flagAdsFull4ShowFaild.incrementAndGet()
                        if (newFlagShowFaild >= 2) {
                            h.flagAdsFull4ShowFaild.set(-10)
                            plcf.cbShow?.onAdShowFail(placement, originPl, adId, err)
                        }
                    }

                    // Forwarded as in speakerclean: the app sees placement full_2_* and can tell the
                    // hand-off has landed. Dismiss stays gated below, so an app that only knows the
                    // parent ad still gets exactly one onAdDismissed.
                    override fun onAdShowed(
                        placement: String?, originPl: String?, adId: String?, flag: Int
                    ) {
                        awaitFull2VisibleImpression()
                        plcf.cbShow?.onAdShowed(placement, originPl, adId, flag)
                    }

                    override fun onAdImpression(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                        finishFull2Transition()
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
                        h.full2TimeShow = System.currentTimeMillis() / 1000
                        val newFlagDissmiss = h.flagAdsFull4Dissmiss.incrementAndGet()
                        if (newFlagDissmiss >= 2) {
                            h.flagAdsFull4Dissmiss.set(-10)
                            plcf.cbShow?.onAdDismissed(placement, originPl, adId)
                        }
                        if (originPl == PlacementDefault.PLFull2Splash || originPl == PlacementDefault.PLFullIntro3) return  // dismiss full 2 splash thì không cho load
                        // Dismiss giữa clearActivity thì loadFull2ForThisTurn tự bỏ (skipLoadWithoutHost); lượt
                        // show cha kế tiếp load lại trên host mới. 2.3.0 PRELOAD (D17-230 a): nạp lại chính Full2 vừa
                        // hiện (`full_2_<placement cha>`, trước chỉ `full_2_default`; slot mặc định thì như cũ).
                        loadFull2ForThisTurn(plcf.placement)
                    }
                    // 0, as in speakerclean: the close countdown starts in onStart (doc 62), and the
                    // dialog pre-built behind the parent (isDelay = false) matches and is reused.
                    // 1 made every network rebuild it at show time.
                }, 0)
                if (reShow) {
                    plcf.isShowing = true
                    h.flagAdsFullShowing.incrementAndGet()
                    h.full2TimeShow = System.currentTimeMillis() / 1000
                    SegmentHooks.onFullscreenShown(System.currentTimeMillis()) // 2.2.0 SEG: Full2 đóng dấu như fork
                    h.flagAdsFull4Dissmiss.incrementAndGet()
                    h.flagAdsFull4ShowFaild.incrementAndGet()
                    break
                }
            }
        }
        if (!reShow) {
            plcf.isShowing = false
            finishFull2Transition()
        }
        return reShow
    }

    fun loadFull2ForThisTurn(placement: String) {
        val realPl = full2Name(placement)

        if (h.skipLoadWithoutHost("loadFull2ForThisTurn", realPl)) return
        if (full2SwitchedOff(realPl)) {
            LogUtil.logD("ads full helper $realPl loadFull2ForThisTurn placement switched off")
            return
        }
        // 2.3.0 PRELOAD (D17-230 b, R-IM2 m7): như show (`isRemoveAds(4)` ở showFull2), người đã gỡ toàn màn hình không
        // phát request Full2 — kể cả Full2 do rewarded (cờ 5 còn) kéo theo.
        if (h.isRemoveAds(4) || h.isDisableAds(4)) {
            LogUtil.logD("ads full helper $realPl loadFull2ForThisTurn isremoveads or disable")
            return
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads full helper $realPl loadFull2ForThisTurn cf not")
                return
            } else if (plcf.isShowing) {
                // 2.4.0 D26 (nextgen): dismiss cha tới qua MainPost SAU khi host resume → Full2 đã hiện (không xếp như Legacy) và
                // slot trống; nạp ở đây là request thừa. Dismiss của Full2 nạp lại (Full2Chain onAdDismissed).
                LogUtil.logD("ads full helper $realPl loadFull2ForThisTurn skipped: Full2 showing (reload on its dismiss)")
                return
            } else {
                if (!plcf.isLoading) {
                    LogUtil.logD("ads full helper ${plcf.placement} loadFull2ForThisTurn call load")
                    plcf.isLoading = true
                    plcf.cbLoad = null
                    loadFull2Circle(plcf, getListFull2LoadShow(plcf), plcf.loadCycle)
                } else {
                    LogUtil.logD("ads full helper $realPl loadFull2ForThisTurn loading")
                }
            }
        } else {
            LogUtil.logD("ads full helper $realPl loadFull2ForThisTurn not cf")
        }
    }

    private fun loadFull2Circle(plcf: AdsPlacementConfig, listLoad: MutableList<Int>, cycle: Int) {
        LogUtil.logD("ads full helper ${plcf.placement} loadFull2Circle")
        if (cycle != plcf.loadCycle) {
            LogUtil.logD("ads helper ${plcf.placement} loadFull2Circle stale cycle dropped")
            return
        }
        if (listLoad.size > 0) {
            val typeLoad = listLoad[0]
            listLoad.removeAt(0)
            val adsLoad = h.fullNetwork(typeLoad)
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
                    }

                    override fun onLoadFailed(
                        placement: String, originPl: String, adId: String, error: String
                    ) {
                        if (cycle != plcf.loadCycle) return
                        // BUG-01 FIX: phải dùng Handler.post như các circle khác để tránh stack overflow
                        // BUG-FIX: phải check isLoading trước khi retry (cùng pattern loadNtClCircle BUG-NEW-01)
                        if (plcf.isLoading) {
                            Handler(Looper.getMainLooper()).post {
                                loadFull2Circle(plcf, listLoad, cycle)
                            }
                        } else {
                            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "loadFull2Circle isLoading=false on fail")
                        }
                    }
                })
            } else {
                // BUG-01 FIX: wrap với Handler.post để tránh đệ quy đồng bộ → stack overflow
                Handler(Looper.getMainLooper()).post {
                    loadFull2Circle(plcf, listLoad, cycle)
                }
            }
        } else {
            plcf.isLoading = false
        }
    }

    private fun getListFull2LoadShow(plcf: AdsPlacementConfig): MutableList<Int> {
        var list: MutableList<Int> = mutableListOf()
        var idx = h.full2IdxShow
        if (idx >= h.currConfig.full2StepCicle.size) {
            idx = 0
        }
        for (i in 0 until h.currConfig.full2StepCicle.size) {
            list.add(h.currConfig.full2StepCicle[idx])
            idx++
            if (idx >= h.currConfig.full2StepCicle.size) {
                idx = 0
            }
        }
        for (i in 0 until h.currConfig.full2StepRe.size) {
            list.add(h.currConfig.full2StepRe[i])
        }

        return list
    }
}
