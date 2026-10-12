package com.app.plugin.ads.core

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

/**
 * Vùng native collapse (NtCl) của [AdsHelper] (1.5.0 Task 4, G8). Chỉ đổi chủ sở hữu mã: thân hàm giữ nguyên, trạng
 * thái dùng chung đọc qua [h] ở mỗi lần gọi (không giữ bản sao — test gán `listAdsNtCl`/`mActivity` của AdsHelper
 * bằng reflection sau khi dựng). `ntClIsShow` và container `ntclParentView` thuộc vùng này; mốc `clTimeShow` ở lại
 * façade (R3: cổng thời gian `isNtClAllowShowAds` cũng đọc). `clearActivity` gọi [onHostCleared].
 */
internal class NativeCollapseController(private val h: AdsHelper) {
    private var ntClIsShow = false
    private var ntclParentView: FrameLayout? = null
    private val cfNot = CfNotDispatcher("ads bnntcl helper")

    fun createAndAddNtClParentView(): FrameLayout? {
        if (ntclParentView == null && h.hostActivity() != null) {
            ntclParentView = FrameLayout(h.hostActivity()!!)
            val bpl = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            //ntclParentView?.setBackgroundColor(Color.BLACK)
            h.hostActivity()?.addContentView(ntclParentView, bpl)
        }
        return ntclParentView
    }

    fun bringLayoutCl() {
        if (h.hostActivity() != null) {
            if (ntclParentView != null) {
                ntclParentView?.bringToFront()
            }
        }
    }

    fun showNtCl(placement: String, cbShow: AdsShowCb?) {
        var realPl = placement
        if (placement.startsWith("bn_")) {
            realPl = placement.replace("bn_", "cl_")
        }
        if (!realPl.startsWith("cl_")) {
            realPl = "cl_" + realPl
        }
        // 1.7.0 Task 2: mỗi lượt gọi một `ad_show_attempt`, trước mọi cổng; ready = slot đã load lúc gọi.
        val ready = if (h.hasPlacementConfig() && h.getAdsPlacementConfig(realPl, 1)?.isLoaded == true) 1 else 0
        AdEventLog.showAttempt("native_collapse", realPl, ready, ScreenName.name)
        LogUtil.logD("ads bnntcl helper $realPl showNtCl")
        // Cùng cổng với loadNtClForThisTurn: NtCl load trước khi gói gỡ quảng cáo được cấp không được hiện (review E M3).
        if (h.isRemoveAds(1) || h.isDisableAds(1)) {
            h.reportDisabledShow(1, realPl, Constants.AdShowFailReason.REMOVE_AD)
            return
        }
        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads bn helper $realPl showNtCl cf not")
                return
            } else {
                bringLayoutCl()
                ntClIsShow = true
                plcf.cbShow = cbShow
                val isShow = showNtClCircle(plcf, getListNtClLoadShow(placement))
                if (!isShow) {
                    loadNtClForThisTurn(placement, null)
                }
            }
        }
    }

    private fun showNtClCircle(plcf: AdsPlacementConfig, listShow: MutableList<Int>): Boolean {
        LogUtil.logD("ads bnntcl helper ${plcf.placement} showNtClCircle")
        var reShow = false
        while (listShow.size > 0) {
            val typeShow = listShow[0]
            listShow.removeAt(0)
            val adsShow = getNtClWithType(typeShow)
            if (adsShow != null) {
                reShow = adsShow.showAd(plcf.placement, object : AdsShowCb {
                    override fun onAdShowFail(
                        placement: String?, originPl: String?, adId: String?, err: String?
                    ) {
                        // BUG-FIX: decrement + clamp âm trong 1 thao tác atomic, tránh check-then-act race
                        h.flagAdsFullShowing.updateAndGet { (it - 1).coerceAtLeast(0) }
                    }

                    override fun onAdShowed(
                        placement: String?, originPl: String?, adId: String?, flag: Int
                    ) {
                        plcf.cbShow?.onAdShowed(placement, originPl, adId, 1)
                    }

                    override fun onAdImpression(
                        placement: String?, originPl: String?, adId: String?
                    ) {

                    }

                    override fun onAdClick(placement: String?, originPl: String?, adId: String?) {
                        AdEventLog.click("native_collapse", placement?.takeIf { it.isNotEmpty() } ?: originPl ?: plcf.placement, adId ?: "")

                    }

                    override fun onAdDismissed(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                        plcf.cbShow?.onAdDismissed(placement, originPl, adId)
                    }
                })
                if (reShow) {
                    ntclParentView?.visibility = View.VISIBLE
                    break
                }
            }
        }
        h.clTimeShow = System.currentTimeMillis() / 1000
        return reShow
    }

    fun loadNtClForThisTurn(placement: String, loadCb: AdsLoadCb?) {
        var realPl = placement
        if (placement.startsWith("bn_")) {
            realPl = placement.replace("bn_", "cl_")
        }
        if (!realPl.startsWith("cl_")) {
            realPl = "cl_" + realPl
        }
        LogUtil.logD("ads bnntcl helper $realPl showNtCl")

        if (h.isRemoveAds(1) || h.isDisableAds(1)) {
            LogUtil.logD("ads bnntcl helper $realPl loadNtClForThisTurn isremoveads or disable")
            loadCb?.onLoadFailed(placement, realPl, "", h.disabledReason(1, "loadNtClForThisTurn isremoveads or disable"))
            return
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads bnntcl helper $realPl loadNtClForThisTurn cf not")
                cfNot.fail(placement, realPl, loadCb)
                return
            } else {
                if (!plcf.isLoading) {
                    LogUtil.logD("ads bnntcl helper ${plcf.placement} loadNtClForThisTurn call load")
                    plcf.isLoading = true
                    plcf.cbLoad = loadCb
                    loadNtClCircle(plcf, getListNtClLoadShow(realPl), plcf.loadCycle)
                } else {
                    LogUtil.logD("ads bnntcl helper $realPl loadNtClForThisTurn loading, attaching callback")
                    LoadCallbacks.attachLoadCallback(plcf, loadCb)
                }
            }
        } else {
            LogUtil.logD("ads bnntcl helper $realPl loadNtClForThisTurn not cf")
        }
    }

    private fun loadNtClCircle(plcf: AdsPlacementConfig, listLoad: MutableList<Int>, cycle: Int) {
        LogUtil.logD("ads bnntcl helper ${plcf.placement} loadNtClCircle")
        if (cycle != plcf.loadCycle) {
            LogUtil.logD("ads helper ${plcf.placement} loadNtClCircle stale cycle dropped")
            return
        }
        if (listLoad.size > 0) {
            val typeLoad = listLoad[0]
            listLoad.removeAt(0)
            val adsLoad = getNtClWithType(typeLoad)
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
                        if (ntClIsShow) {
                            showNtCl(placement, plcf.cbShow)
                        }
                    }

                    override fun onLoadFailed(
                        placement: String, originPl: String, adId: String, error: String
                    ) {
                        if (cycle != plcf.loadCycle) return
                        // BUG-NEW-01 FIX: phải check isLoading trước khi retry
                        // để tránh infinite retry khi concurrent call set isLoading=false
                        // (đồng bộ pattern với loadBannerCircle, loadRectCircle)
                        if (plcf.isLoading) {
                            Handler(Looper.getMainLooper()).post {
                                loadNtClCircle(plcf, listLoad, cycle)
                            }
                        } else {
                            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "loadNtClCircle isLoading=false on fail")
                        }
                    }
                })
            } else {
                Handler(Looper.getMainLooper()).post {
                    loadNtClCircle(plcf, listLoad, cycle)
                }
            }
        } else {
            plcf.isLoading = false
            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "list empty")
        }
    }

    private fun getListNtClLoadShow(placement: String): MutableList<Int> {
        var list: MutableList<Int> = mutableListOf()
        list.addAll(h.currConfig.clStepList)
        return list
    }

    private fun getNtClWithType(type: Int): AdsBaseBanner? {
        if (h.ntClNetworks().containsKey(type)) {
            return h.ntClNetworks()[type]
        }
        return null
    }

    fun hideNativeCl() {
        ntClIsShow = false
        // 1.9.0 Task 3: gỡ view của lượt vừa đóng (trước chỉ INVISIBLE: mỗi lượt hiện cộng một view vào host).
        ntclParentView?.removeAllViews()
        ntclParentView?.visibility = View.INVISIBLE
        for (ad in h.ntClNetworks()) {
            ad.value.hideAd()
        }
    }

    /** Gọi từ AdsHelper.clearActivity, đúng chỗ câu lệnh ntcl đầu tiên của hàm cũ (`ntclParentView?.removeAllViews()`). */
    fun onHostCleared() {
        ntclParentView?.removeAllViews()
        ntclParentView = null
    }
}
