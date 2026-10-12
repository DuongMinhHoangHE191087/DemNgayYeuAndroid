package com.app.plugin.ads.core

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.BannerAttach
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

/**
 * Vùng rect của [AdsHelper] (1.5.0 Task 3, G8). Chỉ đổi chủ sở hữu mã: thân hàm giữ nguyên, trạng thái dùng chung
 * đọc qua [h] ở mỗi lần gọi (không giữ bản sao — test gán `listAdsRect` của AdsHelper bằng reflection sau khi dựng).
 * `rectMapView` (view rect theo container) thuộc vùng này; `clearActivity` gọi [onHostCleared].
 */
internal class RectController(private val h: AdsHelper) {
    private var rectMapView: MutableMap<ViewGroup, ObjectForShowAdWithViewParent> = mutableMapOf()
    private val cfNot = CfNotDispatcher("ads rect helper")

    fun showRect(placement: String, viewParent: ViewGroup?, adloadCb: AdsLoadCb?) {
        var realPl = placement
        if (!placement.startsWith("rect_")) {
            realPl = "rect_" + placement
        }
        // 1.7.0 Task 2: mỗi lượt gọi một `ad_show_attempt`, trước mọi cổng; ready = slot đã load lúc gọi.
        val ready = if (h.hasPlacementConfig() && h.getAdsPlacementConfig(realPl, 1)?.isLoaded == true) 1 else 0
        AdEventLog.showAttempt("rect", realPl, ready, ScreenName.name)
        if (viewParent == null) {
            LogUtil.logD("ads rect helper $realPl showRect viewParent null")
            adloadCb?.onLoadFailed(placement, realPl, "", "viewParent == null")
            return
        }
        if (h.isRemoveAds(2) || h.isDisableAds(2)) {
            LogUtil.logD("ads rect helper $realPl showRect isremoveads or disable")
            adloadCb?.onLoadFailed(placement, realPl, "", h.disabledReason(2, "showRect isremoveads or disable"))
            return
        }
        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            // O7 (a): đang load → callback gắn vào lượt đang chạy (hoặc nhận từ cache), không ghi đè callback của lượt đó.
            val attachToCycle = plcf.isLoading
            if (!attachToCycle) plcf.cbLoad = adloadCb
            LogUtil.logD("ads rect helper ${plcf.placement} showRect call show")
            var itemShow = rectMapView[viewParent]
            if (itemShow != null && itemShow.isAttach) {
                if (attachToCycle) LoadCallbacks.attachLoadCallback(plcf, adloadCb)
                itemShow.isShow = true
                refreshRect(itemShow, 0)
            } else {
                if (itemShow == null) {
                    itemShow = ObjectForShowAdWithViewParent()
                }
                rectMapView[viewParent] = itemShow
                itemShow.placement = plcf.placement
                itemShow.parentView = viewParent
                itemShow.isShow = true
                var adget = showRectCircle(plcf)
                if (adget != null) {
                    var obAdShow = adget as ObjectForShowAdWithViewParent
                    itemShow.isAttach = true
                    refreshRect(itemShow, rectTimeRefresh().toLong())
                    if (obAdShow.type4Time != itemShow.type4Time || (obAdShow.adsEcmp?.timeShow ?: 0L) <= 100) {
                        itemShow.type4Time = obAdShow.type4Time
                        obAdShow.adsEcmp?.timeShow = System.currentTimeMillis()
                    }
                    var adView = obAdShow.adLoad as View
                    if (itemShow.viewCurrAttach != null) {
                        if (itemShow.viewCurrAttach != adView) {
                            itemShow.parentView?.removeAllViews()
                            if (adView.parent != null) {
                                val ppv = adView.parent
                                if (ppv is ViewGroup) {
                                    ppv.removeView(adView)
                                }
                            }
                            itemShow.viewCurrAttach = adView
                            itemShow.parentView?.addView(adView)
                            itemShow.adsEcmp = obAdShow.adsEcmp
                        }
                    } else {
                        itemShow.parentView?.removeAllViews()
                        if (adView.parent != null) {
                            val ppv = adView.parent
                            if (ppv is ViewGroup) {
                                ppv.removeView(adView)
                            }
                        }
                        itemShow.viewCurrAttach = adView
                        itemShow.parentView?.addView(adView)
                        itemShow.adsEcmp = obAdShow.adsEcmp
                    }
                    // Đang load: báo thẳng callback của lần gọi này — callback của lượt đang chạy nhận kết thúc của lượt.
                    if (attachToCycle) adloadCb?.onLoaded(placement, realPl, "") else plcf.cbLoad?.onLoaded(placement, realPl, "")
                } else {
                    if (!plcf.isLoading) {
                        plcf.isLoading = true
                        loadRectCircle(plcf, itemShow, getListRectLoadShow(plcf), plcf.loadCycle)
                    } else {
                        LoadCallbacks.attachLoadCallback(plcf, adloadCb)
                    }
                }
            }
        } else {
            adloadCb?.onLoadFailed(placement, realPl, "", "plcf null")
        }
    }

    private fun showRectCircle(plcf: AdsPlacementConfig): Any? {
        if (!plcf.isShowing) {
            LogUtil.logD("ads rect helper ${plcf.placement} showRectCircle call show")
            var listShow = getListRectLoadShow(plcf)
            var adRe: Any? = null
            for (adg in listShow) {
                val adsGet = getRectWithType(adg)
                adRe = adsGet?.getAdForShow(plcf.placement, object : AdsShowCb {
                    override fun onAdShowFail(
                        placement: String?, originPl: String?, adId: String?, err: String?
                    ) {
                    }

                    override fun onAdShowed(
                        placement: String?, originPl: String?, adId: String?, flag: Int
                    ) {
                    }

                    override fun onAdImpression(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                    }

                    override fun onAdClick(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                        AdEventLog.click("rect", placement?.takeIf { it.isNotEmpty() } ?: originPl ?: plcf.placement, adId ?: "")
                    }

                    override fun onAdDismissed(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                    }
                })
                if (adRe != null) {
                    break
                }
            }
            return adRe
        } else {
            LogUtil.logD("ads rect helper ${plcf.placement} showRectCircle is ads showing")
        }
        return null
    }

    private fun refreshRect(itemShow: ObjectForShowAdWithViewParent, timeWait: Long) {
        // Rect không tự refresh (thiết kế SDK): thân cũ chết sau `return` vô điều kiện, xoá ở 1.5.0 Task 3 (R7).
    }

    fun hideRect(viewParent: ViewGroup, isRemove: Boolean) {
        if (rectMapView.containsKey(viewParent)) {
            rectMapView[viewParent]?.isShow = false
            if (isRemove) {
                if (rectMapView[viewParent]?.viewCurrAttach != null) {
                    rectMapView[viewParent]?.parentView?.removeView(rectMapView[viewParent]?.viewCurrAttach)
                }
                rectMapView[viewParent]?.adsEcmp?.checkDestroyAd()
                rectMapView.remove(viewParent)
            }
        }
    }

    fun loadRect4Show(placement: String, cbLoad: AdsLoadCb?) {
        var realPl = placement
        if (!placement.startsWith("rect_")) {
            realPl = "rect_" + placement
        }
        if (h.isRemoveAds(2) || h.isDisableAds(2)) {
            LogUtil.logD("ads rect helper $realPl loadRect4Show isremoveads or disable")
            cbLoad?.onLoadFailed(placement, realPl, "", h.disabledReason(2, "loadRect4Show isremoveads or disable"))
            return
        }
        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads rect helper $realPl loadRect4Show cf not")
                cfNot.fail(placement, realPl, cbLoad)
                return
            } else {
                if (!plcf.isLoading) {
                    LogUtil.logD("ads rect helper ${plcf.placement} loadRect4Show call load")
                    plcf.isLoading = true
                    plcf.cbLoad = cbLoad
                    loadRectCircle(plcf, null, getListRectLoadShow(plcf), plcf.loadCycle)
                } else {
                    LogUtil.logD("ads rect helper $realPl loadRect4Show loading, attaching callback")
                    LoadCallbacks.attachLoadCallback(plcf, cbLoad)
                }
            }
        } else {
            LogUtil.logD("ads rect helper $realPl loadRect4Show has not cf")
            return
        }
    }

    private fun loadRectCircle(
        plcf: AdsPlacementConfig,
        itemShow: ObjectForShowAdWithViewParent?,
        listLoad: MutableList<Int>,
        cycle: Int
    ) {
        LogUtil.logD("ads rect helper ${plcf.placement} loadRectCircle")
        if (cycle != plcf.loadCycle) {
            LogUtil.logD("ads helper ${plcf.placement} loadRectCircle stale cycle dropped")
            return
        }
        if (listLoad.size > 0) {
            val typeLoad = listLoad[0]
            listLoad.removeAt(0)
//            bnCount4Circle++
//            if (bnCount4Circle <= currConfig.bannerStepCicle.size) {
//                bnIdxShow++
//                if (bnIdxShow >= currConfig.bannerStepCicle.size) {
//                    bnIdxShow = 0
//                }
//            }
            itemShow?.adTypeCurr = typeLoad
            val adsLoad = getRectWithType(typeLoad)
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
                        if (itemShow?.parentView?.isAttachedToWindow == false) {
                            // BUG-FIX: view đã bị detach trong lúc load, ad vừa nhận không được dùng tới
                            // → phải destroy ngay, tránh leak ad/Context (cùng pattern loadNativeCircle)
                            adPl?.destroyAd(adsEcmp?.adObject)
                            adsEcmp?.adObject = null
                            adsEcmp?.isLoaded = false
                            return
                        }
                        itemShow?.isLoaded = true
                        itemShow?.isLoading = false
                        itemShow?.adLoad = adsEcmp?.adObject
                        if (itemShow?.isShow == true) {
                            if (itemShow.adTypeCurr == typeLoad) {
                                adPl?.setGetPlacement(placement)
                                itemShow.isAttach = true
                                refreshRect(itemShow, rectTimeRefresh().toLong())
                                if (typeLoad != itemShow.type4Time || (adsEcmp?.timeShow ?: 0L) <= 100) {
                                    itemShow.type4Time = typeLoad
                                    adsEcmp?.timeShow = System.currentTimeMillis()
                                }
                                val adView = adsEcmp?.adObject as View
                                val rectParent = itemShow.parentView
                                if (itemShow.viewCurrAttach == null || itemShow.viewCurrAttach != adView) {
                                    if (itemShow.viewCurrAttach != null) rectParent?.removeAllViews()
                                    if (rectParent != null && BannerAttach.attach(rectParent, adView)) {
                                        itemShow.viewCurrAttach = adView
                                        rectParent.visibility = View.VISIBLE
                                        itemShow.adsEcmp = adsEcmp
                                    } else {
                                        LogEventServer.logAdShowFailWithReason(
                                            placement = placement,
                                            reason = Constants.AdShowFailReason.VIEW_ATTACH_NULL_IN_CALL_BACK
                                        )
                                    }
                                }
                            } else {
                                LogUtil.logD("ads rect helper ${plcf.placement} loadRectCircle onloaded but dif type tcurr=${itemShow.adTypeCurr} type=$typeLoad")
                            }
                        } else {
                            LogUtil.logD("ads rect helper ${plcf.placement} loadRectCircle onloaded but show=${itemShow?.isShow}")
                        }
                    }

                    override fun onLoadFailed(
                        placement: String, originPl: String, adId: String, error: String
                    ) {
                        if (cycle != plcf.loadCycle) return
                        if (plcf.isLoading) {
                            Handler(Looper.getMainLooper()).post {
                                loadRectCircle(plcf, itemShow, listLoad, cycle)
                            }
                        } else {
                            LogUtil.logD("ads rect helper ${plcf.placement} loadRectCircle onLoadFailed refresh")
                            plcf.cbLoad?.onLoadFailed(
                                plcf.placement,
                                plcf.placement,
                                "",
                                "loadRectCircle onLoadFailed refresh"
                            )
                        }
                    }
                })
            } else {
                Handler(Looper.getMainLooper()).post {
                    loadRectCircle(plcf, itemShow, listLoad, cycle)
                }
            }
        } else {
            plcf.isLoading = false
            itemShow?.isLoading = false
            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "list empty")
        }
    }

    private fun getListRectLoadShow(plcf: AdsPlacementConfig): MutableList<Int> {
        var list: MutableList<Int> = mutableListOf()
        list.addAll(h.currConfig.rectStepList)
        return list
    }

    private fun getRectWithType(type: Int): AdsBaseBanner? {
        if (h.rectNetworks().containsKey(type)) {
            return h.rectNetworks()[type]
        }
        return null
    }

    fun rectTimeRefresh(): Int {
        return h.currConfig.bannerTRefresh
    }

    /** Gọi từ AdsHelper.clearActivity, đúng chỗ câu lệnh rect đầu tiên của hàm cũ. */
    fun onHostCleared() {
        for (item in rectMapView.values) BannerAttach.detach(item.viewCurrAttach)
        rectMapView.clear()
    }
}
