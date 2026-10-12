package com.app.plugin.ads.core

import android.os.Handler
import android.os.Looper
import android.view.View
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.LogUtil

/**
 * Vùng native full của [AdsHelper] (1.5.0 Task 7, G8). Chỉ đổi chủ sở hữu mã: thân hàm giữ nguyên, trạng thái dùng
 * chung đọc qua [h] ở mỗi lần gọi (không giữ bản sao — test gán `listAdsFull` của AdsHelper bằng reflection sau khi
 * dựng). Mạng native full nằm chung `listAdsFull` với interstitial nên đọc qua `h.fullNetwork(type)`; hạ cờ "đã load"
 * dùng chung `h.dropStaleLoadedFlag` với interstitial. `ntFullMapView` (view native full → quảng cáo, để huỷ) thuộc
 * vùng này; `clearActivity` gọi [onHostCleared].
 */
internal class NativeFullController(private val h: AdsHelper) {
    private var ntFullMapView: MutableMap<View, ObjectForShowAdWithViewParent> = mutableMapOf()
    private val cfNot = CfNotDispatcher("ads full nt helper")

    fun loadNativeFullForThisTurn(placement: String, loadCb: AdsLoadCb?) {
        var realPl = placement
        if (!placement.startsWith("full_")) {
            realPl = "full_" + placement
        }

        if (h.isRemoveAds(4) || h.isDisableAds(4)) {
            LogUtil.logD("ads full nt helper $realPl loadNativeFullForThisTurn isremoveads or disable")
            loadCb?.onLoadFailed(
                placement,
                realPl,
                "",
                h.disabledReason(4, "loadNativeFullForThisTurn isremoveads or disable")
            )
            return
        }

        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads full nt helper $realPl loadNativeFullForThisTurn cf not")
                cfNot.fail(placement, realPl, loadCb)
                return
            } else {
                if (!plcf.isLoading) {
                    LogUtil.logD("ads full nt helper ${plcf.placement} loadNativeFullForThisTurn call load")
                    plcf.isLoading = true
                    plcf.cbLoad = loadCb
                    loadNativeFullCircle(plcf, getListNativeFullLoadShow(plcf), plcf.loadCycle)
                } else {
                    LogUtil.logD("ads full nt helper $realPl loadNativeFullForThisTurn loading, attaching callback")
                    LoadCallbacks.attachLoadCallback(plcf, loadCb)
                }
            }
        } else {
            LogUtil.logD("ads full nt helper $realPl loadNativeFullForThisTurn not cf")
        }
    }

    private fun loadNativeFullCircle(plcf: AdsPlacementConfig, listLoad: MutableList<Int>, cycle: Int) {
        LogUtil.logD("ads full nt helper ${plcf.placement} loadNativeFullCircle")
        if (cycle != plcf.loadCycle) {
            LogUtil.logD("ads helper ${plcf.placement} loadNativeFullCircle stale cycle dropped")
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
                        plcf.cbLoad?.onLoaded(placement, originPl, adId)
                    }

                    override fun onLoadFailed(
                        placement: String, originPl: String, adId: String, error: String
                    ) {
                        if (cycle != plcf.loadCycle) return
                        // BUG-FIX: phải check isLoading trước khi retry (cùng pattern loadNtClCircle BUG-NEW-01)
                        if (plcf.isLoading) {
                            Handler(Looper.getMainLooper()).post {
                                loadNativeFullCircle(plcf, listLoad, cycle)
                            }
                        } else {
                            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "loadNativeFullCircle isLoading=false on fail")
                        }
                    }
                })
            } else {
                Handler(Looper.getMainLooper()).post {
                    loadNativeFullCircle(plcf, listLoad, cycle)
                }
            }
        } else {
            plcf.isLoading = false
            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "list empty")
        }
    }

    fun getNativeFullForShow(placement: String, adsShowCb: AdsShowCb?): View? {
        var realPl = placement
        if (!placement.startsWith("full_")) {
            realPl = "full_" + placement
        }
        // 1.7.0 Task 2: mỗi lượt gọi một `ad_show_attempt`, trước mọi cổng; ready = slot đã load lúc gọi.
        val ready = if (h.hasPlacementConfig() && h.getAdsPlacementConfig(realPl, 1)?.isLoaded == true) 1 else 0
        AdEventLog.showAttempt("native_full", realPl, ready, ScreenName.name)
        if (h.isRemoveAds(4) || h.isDisableAds(4)) {
            LogUtil.logD("ads full nt helper $realPl getNativeFullForShow isremoveads or disable")
            return null
        }
        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads full nt helper $realPl getNativeFullForShow cf not")
                return null
            } else {
                if (!plcf.isShowing) {
                    LogUtil.logD("ads full nt helper ${plcf.placement} getNativeFullForShow call show")
                    var listShow = getListNativeFullLoadShow(plcf)
                    var adRe: Any? = null
                    for (adg in listShow) {
                        val adsGet = h.fullNetwork(adg)
                        adRe = adsGet?.getAdForShow(plcf.placement, object : AdsShowCb {
                            override fun onAdShowFail(
                                placement: String?, originPl: String?, adId: String?, err: String?
                            ) {
                                adsShowCb?.onAdShowFail(placement, originPl, adId, err)
                            }

                            override fun onAdShowed(
                                placement: String?, originPl: String?, adId: String?, flag: Int
                            ) {
                                adsShowCb?.onAdShowed(placement, originPl, adId, flag)
                            }

                            override fun onAdImpression(
                                placement: String?, originPl: String?, adId: String?
                            ) {
                                adsShowCb?.onAdImpression(placement, originPl, adId)
                            }

                            override fun onAdClick(
                                placement: String?, originPl: String?, adId: String?
                            ) {
                                AdEventLog.click("native_full", placement?.takeIf { it.isNotEmpty() } ?: originPl ?: plcf.placement, adId ?: "")
                                adsShowCb?.onAdClick(placement, originPl, adId)
                            }

                            override fun onAdDismissed(
                                placement: String?, originPl: String?, adId: String?
                            ) {
                                adsShowCb?.onAdDismissed(placement, originPl, adId)
                            }

                        })
                        if (adRe != null) {
                            break
                        }
                    }
                    if (adRe != null) {
                        var obtmp = adRe as ObjectForShowAdWithViewParent
                        ntFullMapView[obtmp.viewCurrAttach!!] = obtmp
                        return obtmp.viewCurrAttach
                    } else {
                        h.dropStaleLoadedFlag(plcf, "getNativeFullForShow") {
                            loadNativeFullForThisTurn(realPl, null)
                        }
                        return null
                    }
                } else {
                    LogUtil.logD("ads full nt helper $realPl getNativeFullForShow is ads showing")
                    return null
                }
            }
        } else {
            LogUtil.logD("ads full nt helper $realPl getNativeFullForShow has not cf")
            return null
        }
    }

    fun destroyNativeFull(nativeAdFull: View?) {
        if (nativeAdFull != null) {
            if (ntFullMapView.containsKey(nativeAdFull)) {
                var ob = ntFullMapView[nativeAdFull]
                ob?.adsEcmp?.getAdsPl()?.destroyAd(ob.adLoad)
                ntFullMapView.remove(nativeAdFull)
            }
        }
    }

    private fun getListNativeFullLoadShow(plcf: AdsPlacementConfig): MutableList<Int> {
        var list: MutableList<Int> = mutableListOf()
        if (plcf.flagShow == 1) {
            list.add(AdsIDDef.AdmobFullNt)
            list.add(AdsIDDef.FBFullNt)
            list.add(AdsIDDef.MaxFullNt)
        } else {
            list.add(AdsIDDef.FBFullNt)
            list.add(AdsIDDef.AdmobFullNt)
            list.add(AdsIDDef.MaxFullNt)
        }
        return list
    }

    /** Gọi từ AdsHelper.clearActivity, đúng chỗ câu lệnh native full đầu tiên của hàm cũ (BUG-10: map giữ View → Activity). */
    fun onHostCleared() {
        ntFullMapView.clear()
    }
}
