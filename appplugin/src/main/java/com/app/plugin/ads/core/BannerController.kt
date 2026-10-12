package com.app.plugin.ads.core

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.BannerAttach
import com.app.plugin.ads.BannerLoadGuard
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil

/**
 * Vùng banner của [AdsHelper] (1.5.0 Task 6, G8). Chỉ đổi chủ sở hữu mã: thân hàm giữ nguyên, trạng thái dùng chung
 * đọc qua [h] ở mỗi lần gọi (không giữ bản sao — test gán `listAdsBn` của AdsHelper bằng reflection sau khi dựng).
 * Chỗ đặt/view đang hiện (`bnShowPl`, `bnViewParent`, `bnCurrAdd`), vòng waterfall, watchdog, thử lại, refresh và hẹn
 * hiện lại sau phần thưởng "tắt banner" thuộc vùng này; `bnIsShow`, `bnFlagRefresh` (public) và mốc `bnTimeShow` (cổng
 * delta) ở lại façade (R3). `clearActivity` gọi [onHostCleared].
 */
internal class BannerController(private val h: AdsHelper) {
    private companion object {
        private const val KEY_BANNER_HIDE_UNTIL = "ads_bn_hide_until_millis"
    }

    private val bnHideRewardHandler = Handler(Looper.getMainLooper())

    private var bnShowPl: String = ""
    // BUG-04 FIX: bnIsAddView đã bị xóa (dead code, always false)
    private var bnTypeShow: Int = -1
    private var bnViewParent: ViewGroup? = null
    private var bnCurrAdd: View? = null
    private var bnIdxShow = 0
    private var bnCount4Circle = 0
    private var bnType4Time = 1000000

    fun showBanner(
        placement: String,
        viewParent: ViewGroup,
        isCollapse: Boolean,
        cbLoad: AdsLoadCb?,
        cbShow: AdsShowCb?,
        isDeltaTime: Boolean
    ) {
        var realPl = placement
        if (!placement.startsWith("bn_")) {
            realPl = "bn_" + placement
        }
        // 1.7.0 Task 2: mỗi lượt gọi một `ad_show_attempt`, trước mọi cổng; ready = slot đã load lúc gọi.
        val ready = if (h.hasPlacementConfig() && h.getAdsPlacementConfig(realPl, 1)?.isLoaded == true) 1 else 0
        AdEventLog.showAttempt("banner", realPl, ready, ScreenName.name)
        var flagcl = isCollapse
        if (flagcl) {
            if (h.isDisableAds(1)) {
                flagcl = false
            }
        }
        if (h.isRemoveAds(0) || h.isDisableAds(0)) {
            LogUtil.logD("ads bn helper $realPl showBanner isremoveads or disable")
            // Đúng một callback (Điều 13): kill switch làm mọi người dùng gặp cổng này mỗi phiên.
            cbShow?.onAdShowFail(placement, realPl, "", h.disabledReason(0, "showBanner isremoveads or disable"))
            return
        }
        val hideUntil = h.appContext()?.let { PreUtil.getLong(it, KEY_BANNER_HIDE_UNTIL, -1L) } ?: -1L
        val now = System.currentTimeMillis()
        if (hideUntil > now) {
            LogUtil.logD("ads bn helper $realPl showBanner hidden by reward until $hideUntil")
            parkBanner(realPl, viewParent)
            scheduleBannerHideResume(hideUntil - now)
            return
        }
        // Không cần hẹn giờ như nhánh trên: recordScreenChange() tự hiện lại banner khi
        // cooldown hết.
        if (h.screenCooldown().isActive) {
            LogUtil.logD("ads bn helper $realPl showBanner within post-ad screen cooldown")
            parkBanner(realPl, viewParent)
            return
        }
        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads bn helper $realPl showBanner cf not")
                return
            } else {
                // Set state AFTER config validation to avoid inconsistent state on early return
                bnViewParent = viewParent
                h.bnIsShow = true
                if (h.bnFlagRefresh < 0) h.bnFlagRefresh = 0
                bnShowPl = realPl
                bnViewParent?.visibility = View.VISIBLE
                LogUtil.logD("ads bn helper ${plcf.placement} showBanner call show")
                plcf.cbShow = cbShow
                plcf.cbLoad = cbLoad
                // BUG-04 FIX: bỏ bnIsAddView (always false = dead code)
                if (h.isBnAllowShowAds()) {
                    loadBanner(plcf)
                } else {
                    LogUtil.logD("ads bn helper $realPl showBanner not match delta time")
                }
                // BUG-05 FIX: isNtClAllowShowAds() phải được kiểm tra trong mọi trường hợp
                // !isDeltaTime chỉ bỏ qua banner delta time check, không bỏ qua NtCl delta time
                if (flagcl && h.isNtClAllowShowAds()) {
                    h.showNtCl(realPl, cbShow)
                } else {
                    LogUtil.logD("ads bn helper $realPl showNativeCL not match delta time")
                }
            }
        } else {
            LogUtil.logD("ads bn helper $realPl showBanner has not cf")
            return
        }
    }

    // Vòng load banner: một vòng mỗi chỗ đặt, có watchdog và thử lại. Xem BannerLoadGuard.
    private val bannerLoadGuard = BannerLoadGuard()
    private val bannerStallHandler = Handler(Looper.getMainLooper())
    private val bannerRetryHandler = Handler(Looper.getMainLooper())

    private fun loadBanner(plcf: AdsPlacementConfig) {
        if (plcf.isLoading) {
            // Gọi chồng là no-op: vòng đang chạy tự kết thúc, hoặc watchdog gỡ nó nếu kẹt. Bản
            // 1.2.0 hạ cờ ở đây và chạy vòng thứ hai song song.
            LogUtil.logD("ads bn helper ${plcf.placement} loadBanner is loading")
            return
        }
        plcf.isLoading = true
        // Lượt của placement (1.5.0 Task 15, như 8 vòng khác): chụp sau `isLoading = true` (setter tăng loadCycle).
        val loadCycle = plcf.loadCycle
        bnCount4Circle = 0
        val cycle = bannerLoadGuard.beginCycle(plcf.placement)
        loadBannerCircle(plcf, getListBannerLoadShow(plcf), cycle, loadCycle)
    }

    private val bannerStallChecks = java.util.concurrent.ConcurrentHashMap<String, Runnable>()

    /** Mỗi bước của vòng đặt lại hẹn giờ, nên chỉ một bước im lặng quá lâu mới bị coi là kẹt. */
    private fun armBannerStallWatchdog(plcf: AdsPlacementConfig, cycle: Int) {
        val check = Runnable { onBannerStallCheck(plcf, cycle) }
        bannerStallChecks.put(plcf.placement, check)?.let { bannerStallHandler.removeCallbacks(it) }
        bannerStallHandler.postDelayed(check, BannerLoadGuard.STALL_TIMEOUT_MS)
    }

    private fun cancelBannerStallWatchdog(placement: String) {
        bannerStallChecks.remove(placement)?.let { bannerStallHandler.removeCallbacks(it) }
    }

    private fun onBannerStallCheck(plcf: AdsPlacementConfig, cycle: Int) {
        if (!bannerLoadGuard.isCurrent(plcf.placement, cycle) || !plcf.isLoading) return
        if (!BannerLoadGuard.isStallCountable(AdSdkStartup.adNetworksStartedAtMs, System.currentTimeMillis())) {
            armBannerStallWatchdog(plcf, cycle)
            return
        }
        if (!bannerLoadGuard.abandon(plcf.placement, cycle)) return
        LogUtil.logD("ads bn helper ${plcf.placement} banner load stalled, abandoning the cycle")
        plcf.isLoading = false
        scheduleBannerRetry(plcf)
    }

    /**
     * Cả waterfall đã lỗi. Banner đang hiện quảng cáo cũ thì giữ nhịp refresh: bản 1.2.0 dừng
     * refresh hẳn sau một vòng refresh lỗi. Chưa có gì trên màn hình thì thử lại có giới hạn.
     */
    private fun onBannerCycleFailed(plcf: AdsPlacementConfig) {
        if (!h.bnIsShow || bnShowPl != plcf.placement) return
        if (bnCurrAdd != null) {
            refreshBn(bannerTimeRefresh().toLong())
        } else {
            scheduleBannerRetry(plcf)
        }
    }

    private fun scheduleBannerRetry(plcf: AdsPlacementConfig) {
        val delay = bannerLoadGuard.nextRetryDelayMs()
        if (delay == null) {
            LogUtil.logD("ads bn helper ${plcf.placement} retry after failure exhausted")
            return
        }
        bannerRetryHandler.removeCallbacksAndMessages(null)
        bannerRetryHandler.postDelayed({
            if (h.bnIsShow && bnCurrAdd == null && bnShowPl == plcf.placement && !plcf.isLoaded && !plcf.isLoading) {
                LogUtil.logD("ads bn helper ${plcf.placement} retry after failure (${delay / 1000}s)")
                loadBanner(plcf)
            }
        }, delay)
    }

    private val bannerHandler = Handler(Looper.getMainLooper())
    private fun refreshBn(timeWait: Long) {
        if (bannerTimeRefresh() == 0) return
        if (h.bnIsShow && h.bnFlagRefresh == 0) {
            h.bnFlagRefresh = 1
            if (timeWait <= 0) {
                val plcf = h.getAdsPlacementConfig(bnShowPl, 1)
                if (plcf != null) {
                    LogUtil.logD("ads bn helper ${plcf.placement} refreshBn1")
                    loadBanner(plcf)
                }
            } else {
                bannerHandler.removeCallbacksAndMessages(null)
                bannerHandler.postDelayed({
                    if (h.bnIsShow && h.bnFlagRefresh == 1) {
                        h.bnFlagRefresh = 0
                        val plcf = h.getAdsPlacementConfig(bnShowPl, 1)
                        if (plcf != null) {
                            LogUtil.logD("ads bn helper ${plcf.placement} refreshBn2")
                            if (bnViewParent?.isShown == true && bnViewParent?.isAttachedToWindow == true) {
                                loadBanner(plcf)
                            } else {
                                refreshBn(timeWait)
                            }
                        }
                    }
                }, timeWait * 1000)
            }
        }
    }

    fun hideBanner() {
        LogUtil.logD("ads bn helper hideBanner")
        bannerHandler.removeCallbacksAndMessages(null)
        bannerRetryHandler.removeCallbacksAndMessages(null)
        h.bnFlagRefresh = -1
        h.bnIsShow = false
        bnViewParent?.visibility = View.INVISIBLE
    }

    /** Nhớ chỗ đặt banner nhưng giữ nó ẩn, để lúc được hiện lại thì biết hiện ở đâu. */
    private fun parkBanner(realPl: String, viewParent: ViewGroup) {
        bnViewParent = viewParent
        bnShowPl = realPl
        bnViewParent?.visibility = View.INVISIBLE
    }

    fun isBannerHiddenByReward(): Boolean {
        val hideUntil = h.appContext()?.let { PreUtil.getLong(it, KEY_BANNER_HIDE_UNTIL, -1L) } ?: -1L
        return hideUntil > System.currentTimeMillis()
    }

    fun grantBannerHideReward(durationMillis: Long) {
        if (durationMillis <= 0) return
        val until = System.currentTimeMillis() + durationMillis
        h.appContext()?.let { PreUtil.setLong(it, KEY_BANNER_HIDE_UNTIL, until) }
        hideBanner()
        scheduleBannerHideResume(durationMillis)
    }

    private fun scheduleBannerHideResume(delayMillis: Long) {
        bnHideRewardHandler.removeCallbacksAndMessages(null)
        bnHideRewardHandler.postDelayed({ resumeParkedBanner() }, delayMillis)
    }

    fun resumeParkedBanner() {
        val parent = bnViewParent
        val pl = bnShowPl
        if (parent != null && pl.isNotEmpty()) {
            showBanner(pl, parent, isCollapse = false, cbLoad = null, cbShow = null, isDeltaTime = false)
        }
    }

    fun retryBannerNow(reason: String) {
        if (!h.bnIsShow || bnCurrAdd != null || bnShowPl.isEmpty()) return
        if (!h.hasPlacementConfig()) return
        val plcf = h.getAdsPlacementConfig(bnShowPl, 1) ?: return
        if (plcf.isLoaded || plcf.isLoading) return
        LogUtil.logD("ads bn helper ${plcf.placement} retry banner: $reason")
        bannerRetryHandler.removeCallbacksAndMessages(null)
        bannerLoadGuard.resetRetries()
        loadBanner(plcf)
    }

    /**
     * [cycle]: vòng của [bannerLoadGuard] (stall/abandon) — vòng không còn hiện hành thì bỏ hết. [loadCycle]: lượt của
     * placement lúc vòng bắt đầu ([AdsPlacementConfig.loadCycle]); watchdog 90 s của placement đã báo `load timeout` thì
     * lượt đó không còn hiện hành, nhưng vòng banner KHÔNG bỏ (khác 8 vòng kia): nó vẫn chạy hết waterfall để gắn quảng
     * cáo tới muộn và xếp retry/refresh; chỉ 2 lời gọi callback của app bị chặn theo
     * [AdsPlacementConfig.isLoadCallbackCurrent] (1.5.0 Task 15, thay getter-null cũ của `cbLoad`).
     */
    private fun loadBannerCircle(plcf: AdsPlacementConfig, listLoad: MutableList<Int>, cycle: Int, loadCycle: Int) {
        if (!bannerLoadGuard.isCurrent(plcf.placement, cycle)) {
            LogUtil.logD("ads bn helper ${plcf.placement} stale banner cycle dropped")
            return
        }
        LogUtil.logD("ads bn helper ${plcf.placement} loadBannerCircle")
        armBannerStallWatchdog(plcf, cycle)
        if (listLoad.size > 0) {
            val typeLoad = listLoad[0]
            listLoad.removeAt(0)
            bnCount4Circle++
            if (bnCount4Circle <= h.currConfig.bannerStepCicle.size) {
                bnIdxShow++
                if (bnIdxShow >= h.currConfig.bannerStepCicle.size) {
                    bnIdxShow = 0
                }
            }
            val adsLoad = getBannerWithType(typeLoad)
            bnTypeShow = typeLoad
            if (adsLoad != null) {
                adsLoad.loadAd(plcf.placement, object : AdsLoadInnerCb {
                    override fun onLoaded(
                        placement: String,
                        originPl: String,
                        adId: String,
                        adEcmp: AdsEcmp?,
                        adPl: AdsPlBase?
                    ) {
                        // Vòng đã bị watchdog bỏ: quảng cáo vẫn nằm trong cache của mạng, vòng
                        // kế tiếp lấy ra ngay mà không gửi request mới.
                        if (!bannerLoadGuard.isCurrent(plcf.placement, cycle)) return
                        cancelBannerStallWatchdog(plcf.placement)
                        bannerRetryHandler.removeCallbacksAndMessages(null)
                        bannerLoadGuard.resetRetries()
                        h.bnTimeShow = System.currentTimeMillis() / 1000
                        plcf.isLoading = false
                        plcf.isLoaded = true
                        if (plcf.isLoadCallbackCurrent(loadCycle)) {
                            plcf.cbLoad?.onLoaded(placement, originPl, adId)
                        } else {
                            LogUtil.logD("ads bn helper ${plcf.placement} app callback dropped: placement cycle $loadCycle now ${plcf.loadCycle}")
                        }
                        if (h.bnIsShow) {
                            refreshBn(bannerTimeRefresh().toLong())
                        }
                        if (typeLoad != bnType4Time || (adEcmp != null && adEcmp.timeShow <= 100)) {
                            bnType4Time = typeLoad
                            adEcmp?.timeShow = System.currentTimeMillis()
                        }
                        if (!h.bnIsShow || bnShowPl != plcf.placement || adEcmp?.adObject !is View) return
                        if (bnTypeShow == typeLoad) {
                            adPl?.setShowPlacement(placement)
                            val adView = adEcmp?.adObject as View?
                            if (bnCurrAdd != adView) {
                                if (bnCurrAdd != null) bnViewParent?.removeAllViews()
                                bnCurrAdd = adView
                            }
                            val parent = bnViewParent
                            if (adView != null && parent != null && !BannerAttach.attach(parent, adView)) {
                                LogEventServer.logAdShowFailWithReason(
                                    placement = plcf.placement,
                                    reason = Constants.AdShowFailReason.VIEW_ATTACH_NULL_IN_CALL_BACK
                                )
                            }
                            // The current banner remains owned by adObject. Any previous banner
                            // queued during stale invalidation is now detached and can be destroyed.
                            adEcmp?.checkDestroyAd()
                        } else {
                            LogUtil.logD("ads bn helper ${plcf.placement} loadBannerCircle onloaded but dif type tcurr=$bnTypeShow type=$typeLoad")
                        }
                    }

                    override fun onLoadFailed(
                        placement: String, originPl: String, adId: String, error: String
                    ) {
                        if (!bannerLoadGuard.isCurrent(plcf.placement, cycle)) return
                        if (plcf.isLoading) {
                            Handler(Looper.getMainLooper()).post {
                                loadBannerCircle(plcf, listLoad, cycle, loadCycle)
                            }
                        } else {
                            refreshBn(5)
                        }
                    }
                })
            } else {
                Handler(Looper.getMainLooper()).post {
                    loadBannerCircle(plcf, listLoad, cycle, loadCycle)
                }
            }
        } else {
            cancelBannerStallWatchdog(plcf.placement)
            h.bnTimeShow = System.currentTimeMillis() / 1000
            plcf.isLoading = false
            if (plcf.isLoadCallbackCurrent(loadCycle)) {
                plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "list empty")
            } else {
                LogUtil.logD("ads bn helper ${plcf.placement} app callback dropped: placement cycle $loadCycle now ${plcf.loadCycle}")
            }
            onBannerCycleFailed(plcf)
        }
    }

    private fun getBannerWithType(type: Int): AdsBaseBanner? {
        if (h.bannerNetworks().containsKey(type)) {
            return h.bannerNetworks()[type]
        }
        return null
    }

    private fun getListBannerLoadShow(plcf: AdsPlacementConfig): MutableList<Int> {
        var list: MutableList<Int> = mutableListOf()
        var idx = bnIdxShow
        if (idx >= h.currConfig.bannerStepCicle.size) {
            idx = 0
        }
        for (i in 0 until h.currConfig.bannerStepCicle.size) {
            list.add(h.currConfig.bannerStepCicle[idx])
            idx++
            if (idx >= h.currConfig.bannerStepCicle.size) {
                idx = 0
            }
        }
        for (i in 0 until h.currConfig.bannerStepRe.size) {
            list.add(h.currConfig.bannerStepRe[i])
        }
        return list
    }

    fun bannerTimeRefresh(): Int {
        val seconds = if (h.optimizationPolicy.refreshOverridden) h.optimizationPolicy.bannerRefreshSeconds
            else h.currConfig.bannerTRefresh
        return if (seconds == 0) 0 else seconds.coerceIn(30, 150)
    }

    /**
     * Gọi từ AdsHelper.clearActivity, đúng chỗ câu lệnh banner đầu tiên của hàm cũ (`hideBanner()`, ngay sau
     * `mActivity = null`): ẩn banner, bỏ hẹn hiện lại, gỡ view khỏi container rồi bỏ tham chiếu container/view.
     */
    fun onHostCleared() {
        hideBanner()
        bnHideRewardHandler.removeCallbacksAndMessages(null)
        // Gỡ view quảng cáo khỏi container của Activity đang chết: AdView sống lâu (context
        // Application) mà còn trỏ vào container cũ thì Activity cũ không bao giờ được GC (đo 2026-09-24:
        // 4 Activity sống sau xoay màn hình, GC-root AdView.mParent → container → MainActivity cũ).
        BannerAttach.detach(bnCurrAdd)
        bnViewParent?.removeAllViews()
        // Clear banner view references to prevent Activity memory leak
        bnViewParent = null
        bnCurrAdd = null
    }
}
