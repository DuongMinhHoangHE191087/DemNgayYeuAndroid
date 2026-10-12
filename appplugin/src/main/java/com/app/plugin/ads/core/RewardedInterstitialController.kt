package com.app.plugin.ads.core

import android.app.Activity
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.ads.RemoveAdsMask
import com.app.plugin.ads.RwiIntroDialog
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.segment.SegmentHooks
import com.app.plugin.until.Constants.AdShowFailReason
import com.app.plugin.until.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

/** Mở màn intro trên host (mặc định [RwiIntroDialog.present]); `false` = không mở được, không callback nào tới. */
internal fun interface RwiIntroPresenter {
    fun present(activity: Activity, listener: RwiIntroDialog.Listener): Boolean
}

/**
 * Rewarded interstitial (1.9.0 Task 2, hợp đồng §C). App gọi `loadRewardedForThisTurn("rwi_<x>")` /
 * `showRewarded("rwi_<x>", cb)`; [RewardedController] chuyển sang đây TRƯỚC tiền tố `gift_` ([owns]). Chỉ AdMob
 * (mạng `AdsIDDef.AdmobRwi` trong map gift của façade, đọc qua `h.rewardedNetworks()` mỗi lần gọi). Không state riêng
 * ngoài [cfNot] (luật R-2: không đọc `h` lúc dựng).
 *
 * Thứ tự cổng của [show]: `ad_show_attempt(rewarded_interstitial)` → kill/tắt định dạng → remove-ads
 * `FLAG_FULLSCREEN` (`remove_ad`) → [SharedFullscreenGap.verdict] (phiên đầu + gap chung `fullTimeShow`
 * với interstitial) → (cấu hình placement, quảng cáo toàn màn hình khác đang hiện) → tuổi > `fullscreen_max_age_minutes`
 * (`ad_expired` do placement gửi + nạp lại) → intro ([RwiIntroDialog]; bỏ qua = `rwi_intro_skipped`, giữ quảng cáo) →
 * show. Một lượt giữ `flagAdsFullShowing` từ lúc intro mở tới lối ra duy nhất ([Turn.end]); `onAdRewarded` ≤ 1,
 * `onAdDismissed` ≤ 1; không chuỗi Full2; brain không được hỏi.
 */
internal class RewardedInterstitialController(private val h: AdsHelper) {
    private val cfNot = CfNotDispatcher("ads rwi helper")

    private fun network(): AdsBaseGift? = h.rewardedNetworks()[AdsIDDef.AdmobRwi]

    /** Kill switch hoặc định dạng rewarded bị tắt bằng `maskAdsStatus` (cùng cờ với rewarded: lượt do người dùng chọn). */
    private fun isKilledOrDisabled(): Boolean = h.isDisableAds(RemoveAdsMask.FLAG_REWARDED)

    /** Người dùng đã trả tiền gỡ quảng cáo toàn màn hình (gói rẻ `INTERRUPTIVE` gỡ cả rwi). */
    private fun isRemoved(): Boolean = h.isRemoveAds(RemoveAdsMask.FLAG_FULLSCREEN)

    fun load(placement: String, loadCb: AdsLoadCb?) {
        if (h.skipLoadWithoutHost("loadRwi", placement)) {
            loadCb?.onLoadFailed(placement, placement, "", h.loadFailNoHostLabel())
            return
        }
        if (isKilledOrDisabled() || isRemoved()) {
            LogUtil.logD("ads rwi helper $placement load isremoveads or disable")
            loadCb?.onLoadFailed(
                placement, placement, "", h.disabledReason(RemoveAdsMask.FLAG_FULLSCREEN, "loadRwi isremoveads or disable")
            )
            return
        }
        val plcf = h.getAdsPlacementConfig(placement, 1)
        if (plcf == null) {
            LogUtil.logD("ads rwi helper $placement load not cf")
            return
        }
        if (plcf.flagShow < 0) {
            cfNot.fail(placement, placement, loadCb)
            return
        }
        if (plcf.isLoading) {
            LoadCallbacks.attachLoadCallback(plcf, loadCb)
            return
        }
        val net = network()
        if (net == null) {
            LogUtil.logD("ads rwi helper $placement load no network")
            loadCb?.onLoadFailed(placement, placement, "", NO_NETWORK)
            return
        }
        LogUtil.logD("ads rwi helper $placement loadRwi call load")
        plcf.isLoading = true
        plcf.cbLoad = loadCb
        val cycle = plcf.loadCycle
        net.loadAd(placement, object : AdsLoadInnerCb {
            override fun onLoaded(placement: String, originPl: String, adId: String, adsEcmp: AdsEcmp?, adPl: AdsPlBase?) {
                if (cycle != plcf.loadCycle) return
                plcf.isLoading = false
                plcf.isLoaded = true
                plcf.cbLoad?.onLoaded(placement, originPl, adId)
            }

            override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
                if (cycle != plcf.loadCycle) return
                plcf.isLoading = false
                plcf.isLoaded = false
                plcf.cbLoad?.onLoadFailed(placement, originPl, adId, error)
            }
        })
    }

    private fun refuse(placement: String, reason: String): Boolean {
        LogUtil.logD("ads rwi helper $placement show refused: $reason")
        LogEventServer.logAdShowFailWithReason(placement = placement, reason = reason)
        return false
    }

    fun show(placement: String, cbShow: AdsGiftShowCb): Boolean {
        val ready = if (h.hasPlacementConfig() && h.getAdsPlacementConfig(placement, 1)?.isLoaded == true) 1 else 0
        AdEventLog.showAttempt(FORMAT, placement, ready, ScreenName.name)

        if (isKilledOrDisabled()) {
            h.reportDisabledShow(RemoveAdsMask.FLAG_FULLSCREEN, placement, AdShowFailReason.REMOVE_AD)
            return false
        }
        if (isRemoved()) {
            h.reportDisabledShow(RemoveAdsMask.FLAG_FULLSCREEN, placement, AdShowFailReason.REMOVE_AD)
            return false
        }
        SharedFullscreenGap.verdict(h)?.let { return refuse(placement, it) }

        val plcf = h.getAdsPlacementConfig(placement, 1) ?: return refuse(placement, AdShowFailReason.NOT_CONFIG)
        if (plcf.flagShow < 0) return refuse(placement, AdShowFailReason.CONFIG_NOT)
        if (h.flagAdsFullShowing.updateAndGet { if (it < 0) 0 else it } > 0) {
            return refuse(placement, AdShowFailReason.AD_FULL_SHOWING)
        }
        if (plcf.isShowing) return refuse(placement, AdShowFailReason.AD_SHOWING)
        val net = network() ?: return refuse(placement, AdShowFailReason.LIST_SHOW_EMPTY)

        // Tuổi: quảng cáo quá `fullscreen_max_age_minutes` bị placement bỏ ngay trong `hasLoadedAd` (gửi `ad_expired`).
        if (!net.hasLoadedAd(placement)) {
            if (!plcf.isLoaded) refuse(placement, AdShowFailReason.SHOW_NOT_LOADED)
            else LogUtil.logD("ads rwi helper $placement show: loaded ad expired or lost, reload")
            plcf.isLoaded = false
            load(placement, null)
            return false
        }
        val activity = net.mActivity ?: return refuse(placement, AdShowFailReason.SHOW_WHEN_ACT_NULL)

        LogUtil.logD("ads rwi helper $placement showRwi call show")
        plcf.isShowing = true
        plcf.cbShow = cbShow
        h.flagAdsFullShowing.incrementAndGet()
        val turn = Turn(placement, plcf, net)
        if (!introPresenter.present(activity, turn)) {
            turn.fail(AdShowFailReason.SHOW_WHEN_ACT_NULL)
            return false
        }
        return true
    }

    /**
     * Một lượt show (intro + quảng cáo). Callback của app đọc qua `plcf.cbShow` mỗi lần (host bị huỷ thì façade nhả nó,
     * Task I5). Lối ra duy nhất [end]: hạ `isShowing`, trả `flagAdsFullShowing` đúng một lần.
     */
    private inner class Turn(
        private val placement: String,
        private val plcf: AdsPlacementConfig,
        private val net: AdsBaseGift,
    ) : RwiIntroDialog.Listener, AdsGiftShowCb {
        private val ended = AtomicBoolean(false)
        private val rewarded = AtomicBoolean(false)

        private fun app(): AdsGiftShowCb? = plcf.cbShow as? AdsGiftShowCb

        private fun end(): Boolean {
            if (!ended.compareAndSet(false, true)) return false
            plcf.isShowing = false
            h.flagAdsFullShowing.updateAndGet { (it - 1).coerceAtLeast(0) }
            return true
        }

        /** Lượt kết thúc trước khi quảng cáo hiện: gửi [reason], báo app một `onAdShowFail`. Quảng cáo vẫn giữ. */
        fun fail(reason: String) {
            if (!end()) return
            refuse(placement, reason)
            app()?.onAdShowFail(placement, placement, "", reason)
        }

        override fun onSkip() = fail(AdShowFailReason.RWI_INTRO_SKIPPED)

        override fun onHostGone() = fail(AdShowFailReason.HOST_DESTROYED)

        override fun onContinue() {
            if (ended.get()) return
            if (!net.showAd(placement, this)) {
                plcf.isLoaded = false
                fail(AdShowFailReason.SHOW_NOT_LOADED_ON_SHOW)
                load(placement, null)
                return
            }
            plcf.isLoaded = false
            h.fullTimeShow = System.currentTimeMillis() / 1000
            h.markAutoAdShown()
            SegmentHooks.onFullscreenShown(System.currentTimeMillis()) // 2.2.0 SEG
        }

        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            if (!end()) return
            h.fullTimeShow = System.currentTimeMillis() / 1000
            app()?.onAdShowFail(placement, originPl, adId, err)
            load(this.placement, null)
        }

        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            app()?.onAdShowed(placement, originPl, adId, flag)
        }

        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) {
            app()?.onAdImpression(placement, originPl, adId)
        }

        override fun onAdClick(placement: String?, originPl: String?, adId: String?) {
            AdEventLog.click("rewarded_interstitial", placement?.takeIf { it.isNotEmpty() } ?: originPl ?: this.placement, adId ?: "")
            h.onAdClicked()
            app()?.onAdClick(placement, originPl, adId)
        }

        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) {
            if (!rewarded.compareAndSet(false, true)) return
            SegmentHooks.onRewardedCompleted() // 2.2.0 SEG: REWARDED_AD_COMPLETED như rewarded
            app()?.onAdRewarded(placement, originPl, adId)
        }

        /** Không chuỗi Full2 (hợp đồng §C); gap chung tính từ lúc đóng như interstitial. Lượt đã kết thúc (fail) thì bỏ. */
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            if (!end()) return
            h.fullTimeShow = System.currentTimeMillis() / 1000
            app()?.onAdDismissed(placement, originPl, adId)
            load(this.placement, null)
        }
    }

    companion object {
        private const val PREFIX = "rwi_"
        const val FORMAT = "rewarded_interstitial"
        private const val NO_NETWORK = "no rwi network"

        fun owns(placement: String): Boolean = placement.startsWith(PREFIX)

        /** Chỗ mở intro; mặc định [RwiIntroDialog]. Test JVM thay bằng bản giả (không có cửa sổ thật). */
        @Volatile
        var introPresenter: RwiIntroPresenter = RwiIntroPresenter { activity, listener -> RwiIntroDialog.present(activity, listener) }
    }
}
