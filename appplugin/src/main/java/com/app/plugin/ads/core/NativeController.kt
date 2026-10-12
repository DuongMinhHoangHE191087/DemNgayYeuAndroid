package com.app.plugin.ads.core

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.app.plugin.R
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlBaseNative
import com.app.plugin.ads.AdsPlacementConfig
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.ObjectForCustomeNativeAd
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.AdStatus
import com.app.plugin.logevent.AdmobSDKInitState
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ScreenName
import com.app.plugin.until.AppUtil
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

/**
 * Vùng native của [AdsHelper] (1.5.0 Task 5, G8). Chỉ đổi chủ sở hữu mã: thân hàm giữ nguyên, trạng thái dùng chung
 * đọc qua [h] ở mỗi lần gọi (không giữ bản sao — test gán `listAdsNative` của AdsHelper bằng reflection sau khi dựng).
 * `nativeMapView` (view native theo container), layout và màu tuỳ biến theo placement thuộc vùng này;
 * `clearActivity` gọi [onHostCleared].
 */
internal class NativeController(private val h: AdsHelper) {
    private var nativeMapView: MutableMap<ViewGroup, ObjectForShowAdWithViewParent> = mutableMapOf()
    private var nativeResIdCustomeLayout: MutableMap<Int, Int> = mutableMapOf()
    private var nativeCustomeView: MutableMap<String, ObjectForCustomeNativeAd> = mutableMapOf()

    /** 2.5.0 PRIO (Fable C1b): SHOW = placement còn view chờ gắn (điều kiện như chỗ gắn ở loadNativeCircle, kể cả parent còn trong cửa sổ);
     *  vị từ đọc map lúc cổng unit quyết (main), đăng ký ở [showNative]. */
    private val showPending: (String) -> Boolean =
        { pl -> nativeMapView.values.any { it.placement == pl && it.isShow && !it.isAttach && it.parentView?.isAttachedToWindow == true } }

    fun setLayoutCustome4Native(resId: Int, adType: Int) {
        nativeResIdCustomeLayout[adType] = resId
    }

    fun getLayoutCustome4Native(adType: Int): Int {
        if (adType == -1001) {
            return R.layout.appplugin_layout_native_full_pager
        }
        var resId = -1
        if (nativeResIdCustomeLayout.containsKey(adType)) {
            resId = nativeResIdCustomeLayout[adType]!!
        }
        if (resId <= 0) {
            if (adType == AdsIDDef.AdmobNative) {
                resId = R.layout.appplugin_admob_native_layout_small
            } else if (adType == AdsIDDef.FBNative) {
                resId = R.layout.appplugin_fb_native_layout_small
            } else {
                resId = R.layout.appplugin_max_native_layout_small
            }
        }
        return resId
    }

    fun setCustomeView4Native(placement: String, bgColor: Int, btActionColor: Int) {
        var realPl = placement
        if (!placement.startsWith("nt_")) {
            realPl = "nt_" + placement
        }
        var cf: ObjectForCustomeNativeAd?
        if (nativeCustomeView.containsKey(realPl)) {
            cf = nativeCustomeView[realPl]
            cf?.bgColor = bgColor
            cf?.actionColor = btActionColor
        } else {
            cf = ObjectForCustomeNativeAd(bgColor, btActionColor)
            nativeCustomeView[realPl] = cf
        }
    }

    fun removeCustomeView4Native(placement: String) {
        var realPl = placement
        if (!placement.startsWith("nt_")) {
            realPl = "nt_" + placement
        }
        if (nativeCustomeView.containsKey(realPl)) {
            nativeCustomeView.remove(realPl)
        }
    }

    fun getNativeCustom(placement: String): ObjectForCustomeNativeAd? {
        if (nativeCustomeView.containsKey(placement)) {
            return nativeCustomeView[placement]
        } else {
            return null
        }
    }

    fun showNative(
        placement: String,
        viewParent: ViewGroup?,
        cbShow: AdsShowCb?,
        isLoadNShow: Boolean
    ) {
        UnitGate.showSource(showPending) // view chờ gắn chỉ sinh ở đây
        var realPl = placement
        if (!placement.startsWith("nt_")) {
            realPl = "nt_" + placement
        }
        if (viewParent == null) {
            reportBlockedAttempt(realPl)
            LogUtil.logD("ads native helper $realPl showNative viewParent null")
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.VIEW_NULL
            )
            cbShow?.onAdShowFail(placement, realPl, "", "showNative viewParent null")
            return
        }
        if (h.isRemoveAds(3) || h.isDisableAds(3)) {
            reportBlockedAttempt(realPl)
            val reason = h.reportDisabledShow(3, realPl, Constants.AdShowFailReason.REMOVE_AD)
            val err = if (reason == Constants.AdShowFailReason.SHOW_BLOCKED_KILL_SWITCH) reason
                else "showNative isremoveads or disable"
            cbShow?.onAdShowFail(placement, realPl, "", err)
            return
        }
        val plcf = h.getAdsPlacementConfig(realPl, 1)

        if (plcf != null) {
            if (plcf.flagShow < 0) {
                reportBlockedAttempt(realPl)
                LogUtil.logD("ads native helper $realPl showNative cf not")
                cbShow?.onAdShowFail(placement, realPl, "", "showNative cf not")
                return
            }
            LogUtil.logD("ads native helper ${plcf.placement} showNative call show")
            plcf.cbShow = cbShow
            var itemShow = nativeMapView[viewParent]
            if (itemShow != null && itemShow.isAttach) {
                itemShow.isShow = true
                if (itemShow.placement == plcf.placement) {
                    reportBlockedAttempt(realPl)
                    itemShow.showCallback = cbShow
                    return
                }
                itemShow.placement = plcf.placement
                itemShow.isAttach = false
            } else {
                itemShow = ObjectForShowAdWithViewParent()
                nativeMapView[viewParent] = itemShow
                itemShow.placement = plcf.placement
                itemShow.parentView = viewParent
                itemShow.isShow = true
            }

            itemShow.showCallback = cbShow

            LogEventServer.logAdStartAction(
                adFormat = "native_custom",
                placement = realPl,
                isAction = if (plcf.isLoaded) AdStatus.Loaded.code else AdStatus.NotLoaded.code,
                screenName = ScreenName.name,
                isSDKInit = AdmobSDKInitState.isInitComplete
            )

            var adget = showNativeCircle(plcf)
            if (adget != null) {
                var obAdShow = adget as ObjectForShowAdWithViewParent
                itemShow.isAttach = true
                //refreshNative(itemShow, nativeTimeRefresh().toLong())
                if (obAdShow.type4Time != itemShow.type4Time || (obAdShow.adsEcmp?.timeShow ?: 0L) <= 100) {
                    itemShow.type4Time = obAdShow.type4Time
                    obAdShow.adsEcmp?.timeShow = System.currentTimeMillis()
                }

                if (itemShow.parentView == null) {
                    LogEventServer.logAdShowFailWithReason(
                        placement = realPl,
                        reason = "parent_view_of_item_null"
                    )
                }

                itemShow.viewCurrAttach = obAdShow.viewCurrAttach

                if (itemShow.viewCurrAttach != null) {
                    itemShow.parentView?.removeAllViews()
                    itemShow.adsEcmp?.getAdsPl()?.destroyAd(itemShow.adLoad)
                } else {
                    LogEventServer.logAdShowFailWithReason(
                        placement = realPl,
                        reason = Constants.AdShowFailReason.VIEW_ATTACH_NULL_IN_CALL_BACK
                    )
                    // addView(null) ném IllegalArgumentException: báo lỗi cho app rồi dừng.
                    cbShow?.onAdShowFail(placement, realPl, "", Constants.AdShowFailReason.VIEW_ATTACH_NULL_IN_CALL_BACK)
                    // Không có gì được gắn: lần showNative sau cùng parent/placement không được bỏ qua.
                    itemShow.isAttach = false
                    return
                }

                itemShow.parentView?.addView(obAdShow.viewCurrAttach)
                itemShow.adsEcmp = obAdShow.adsEcmp
                itemShow.adLoad = obAdShow.adLoad
                plcf.cbShow?.onAdShowed(
                    placement,
                    obAdShow.placement,
                    obAdShow.adsEcmp?.adsId,
                    0
                )
            } else {
                if (isLoadNShow) {
                    if (!plcf.isLoading) {
                        plcf.isLoading = true
                        loadNativeCircle(plcf, itemShow, getListNativeLoadShow(plcf), plcf.loadCycle)
                    }
                } else {
                    if (realPl !in noReload()) {
                        if (!plcf.isLoading) {
                            plcf.isLoading = true
                            loadNativeCircle(plcf, itemShow, getListNativeLoadShow(plcf), plcf.loadCycle)
                        }
                    }
                }
            }
        } else {
            reportBlockedAttempt(realPl)
            LogEventServer.logAdShowFailWithReason(
                placement = realPl,
                reason = Constants.AdShowFailReason.NOT_CONFIG
            )
            cbShow?.onAdShowFail(placement, realPl, "", "showNative not cf")
        }
    }

    /**
     * 2.5.0 PLMN: placement không tự nạp lại khi [showNative] chưa có quảng cáo, `@string/ads_cf_native_no_reload` (CSV):
     * vắng = `nt_language,nt_language_2,nt_intro` (2.4.4), có mặt (kể cả rỗng) = đúng danh sách đó. Đọc một lần.
     * 2.5.1 PLMNORM: mục thiếu `nt_` ⇒ `nt_<mục>` (như [showNative]) + một `logE`; lỗi đọc ⇒ `logE` + 3 placement cũ.
     */
    private fun noReload(): List<String> = noReloadList ?: run {
        val legacy = "nt_language,nt_language_2,nt_intro"
        val ctx = h.appContext() ?: return@run AppUtil.splitIds(legacy)
        val raw = runCatching { AppUtil.getResourceString(ctx, "ads_cf_native_no_reload", legacy) }.getOrElse {
            LogUtil.logE("ads_cf_native_no_reload read failed: ${it.message}"); legacy
        }
        AppUtil.splitIds(raw).map { pl ->
            if (pl.startsWith("nt_")) pl else "nt_$pl".also { LogUtil.logE("ads_cf_native_no_reload: mục '$pl' thiếu tiền tố nt_, dùng '$it'") }
        }.also { noReloadList = it }
    }
    private var noReloadList: List<String>? = null

    /**
     * `ad_show_attempt` của lượt [showNative] dừng trước `logAdStartAction` (lượt đi tiếp phát ở đó), ready = slot đã load
     * lúc gọi (1.7.0 Task 2: mỗi lượt gọi đúng một event, diff chỉ thêm dòng).
     */
    private fun reportBlockedAttempt(realPl: String) = AdEventLog.showAttempt(
        "native_custom", realPl, if (h.hasPlacementConfig() && h.getAdsPlacementConfig(realPl, 1)?.isLoaded == true) 1 else 0,
        ScreenName.name
    )

    private fun showNativeCircle(plcf: AdsPlacementConfig): Any? {
        if (!plcf.isShowing) {
            LogUtil.logD("ads native helper ${plcf.placement} showNativeCircle call show")
            var listShow = getListNativeLoadShow(plcf)
            if (listShow.isEmpty()) {
                LogEventServer.logAdShowFailWithReason(
                    placement = plcf.placement,
                    reason = Constants.AdShowFailReason.LIST_SHOW_EMPTY
                )
            }
            var adRe: Any? = null
            for (adg in listShow) {
                val adsGet = getNativeWithType(adg)
                if (adsGet == null) {
                    LogEventServer.logAdShowFailWithReason(
                        placement = plcf.placement,
                        reason = "adsGet null in $adg"
                    )
                }
                val callback = object : AdsShowCb {
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
                        AdEventLog.click("native_custom", placement?.takeIf { it.isNotEmpty() } ?: originPl ?: plcf.placement, adId ?: "")
                    }

                    override fun onAdDismissed(
                        placement: String?, originPl: String?, adId: String?
                    ) {
                    }

                }
                adRe = adsGet?.getAdForShow(plcf.placement, callback)
                if (adRe == null && adg == AdsIDDef.AdmobNative) {
                    for (source in h.optimizationPolicy.nativeReuseSources(plcf.placement)) {
                        val sourceEnabled = h.getAdsPlacementConfig(source, 1)?.flagShow?.let { it >= 0 } == true
                        val sourceVisible = nativeMapView.values.any { it.placement == source && it.isShow }
                        if (!sourceEnabled || sourceVisible) continue
                        adRe = adsGet?.getReusableAd(source, plcf.placement, callback)
                        if (adRe != null) break
                    }
                }
                if (adRe != null) {
                    break
                }
            }
            return adRe
        } else {
            LogUtil.logD("ads native helper ${plcf.placement} showNativeCircle is ads showing")
            LogEventServer.logAdShowFailWithReason(
                placement = plcf.placement,
                reason = Constants.AdShowFailReason.AD_SHOWING
            )
            return null
        }
    }

    fun loadNative4Show(placement: String, adloadCb: AdsLoadCb?) {
        var realPl = placement
        if (!placement.startsWith("nt_")) {
            realPl = "nt_" + placement
        }
        if (h.isRemoveAds(3) || h.isDisableAds(3)) {
            LogUtil.logD("ads native helper $realPl loadNative4Show isremoveads or disable")
            adloadCb?.onLoadFailed(placement, realPl, "", h.disabledReason(3, "loadNative4Show isremoveads or disable"))
            return
        }
        val plcf = h.getAdsPlacementConfig(realPl, 1)
        if (plcf != null) {
            if (plcf.flagShow < 0) {
                LogUtil.logD("ads native helper $realPl loadNative4Show cf not")
                adloadCb?.onLoadFailed(placement, realPl, "", "loadNative4Show cf not")
                return
            } else {
                if (!plcf.isLoading) {
                    LogUtil.logD("ads native helper ${plcf.placement} loadNative4Show call load")
                    plcf.isLoading = true
                    plcf.cbLoad = adloadCb
                    loadNativeCircle(plcf, null, getListNativeLoadShow(plcf), plcf.loadCycle)
                } else {
                    LogUtil.logD("ads native helper $realPl loadNative4Show loading")
                    adloadCb?.onLoadFailed(placement, realPl, "", "loadNative4Show loading")
                }
            }
        } else {
            LogUtil.logD("ads native helper $realPl loadNative4Show has not cf")
            adloadCb?.onLoadFailed(placement, realPl, "", "loadNative4Show has not cf")
            return
        }
    }

    private fun loadNativeCircle(
        plcf: AdsPlacementConfig,
        itemShow: ObjectForShowAdWithViewParent?,
        listLoad: MutableList<Int>,
        cycle: Int
    ) {
        LogUtil.logD("ads native helper ${plcf.placement} loadNativeCircle")
        if (cycle != plcf.loadCycle) {
            LogUtil.logD("ads helper ${plcf.placement} loadNativeCircle stale cycle dropped")
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
            val adsLoad = getNativeWithType(typeLoad)
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
                        // Resolve pending views at completion: the request may have begun as preload.
                        // Check ownership again after cbLoad, which may synchronously show/hide an ad.
                        val target = nativeMapView.values.firstOrNull {
                            it.placement == plcf.placement && it.isShow && !it.isAttach &&
                                it.parentView?.isAttachedToWindow == true
                        }
                        if (target != null && adsEcmp?.isLoaded == true && adsEcmp.adObject != null &&
                            adPl is AdsPlBaseNative && !h.isRemoveAds(3) && !h.isDisableAds(3) && plcf.flagShow >= 0) {
                            adPl.setGetPlacement(placement)
                            val adView = adPl.populateNativeAdView(adsEcmp)
                            if (adView != null) {
                                val ownedAd = adsEcmp.adObject
                                adPl.setTickAdHasShow(adsEcmp)
                                adsEcmp.adObject = null
                                adsEcmp.isLoaded = false
                                target.parentView?.removeAllViews()
                                target.adsEcmp?.getAdsPl()?.destroyAd(target.adLoad)
                                target.parentView?.addView(adView)
                                target.isAttach = true
                                target.isLoaded = true
                                target.isLoading = false
                                target.viewCurrAttach = adView
                                target.adsEcmp = adsEcmp
                                target.adLoad = ownedAd
                                target.type4Time = typeLoad
                                adsEcmp.timeShow = System.currentTimeMillis()
                                target.showCallback?.onAdShowed(placement, originPl, adId, 0)
                            }
                        }
                    }

                    override fun onLoadFailed(
                        placement: String, originPl: String, adId: String, error: String
                    ) {
                        if (cycle != plcf.loadCycle) return
                        if (plcf.isLoading) {
                            Handler(Looper.getMainLooper()).post {
                                loadNativeCircle(plcf, itemShow, listLoad, cycle)
                            }
                        } else {
                            LogUtil.logD("ads native helper ${plcf.placement} loadNativeCircle onLoadFailed refresh")
                        }
                    }
                })
            } else {
                Handler(Looper.getMainLooper()).post {
                    loadNativeCircle(plcf, itemShow, listLoad, cycle)
                }
            }
        } else {
            plcf.isLoading = false
            plcf.cbLoad?.onLoadFailed(plcf.placement, plcf.placement, "", "list empty")
            plcf.cbShow?.onAdShowFail(plcf.placement, plcf.placement, "", "list empty")
        }
    }

    private fun getListNativeLoadShow(plcf: AdsPlacementConfig): MutableList<Int> {
        var list: MutableList<Int> = mutableListOf()
        list.addAll(h.currConfig.nativeStepList)
        return list
    }

    private fun getNativeWithType(type: Int): AdsBaseNative? {
        if (h.nativeNetworks().containsKey(type)) {
            return h.nativeNetworks()[type]
        }
        return null
    }

    fun hideNative(viewParent: ViewGroup, isRemove: Boolean) {
        if (nativeMapView.containsKey(viewParent)) {
            nativeMapView[viewParent]?.isShow = false
            if (isRemove) {
                if (nativeMapView[viewParent]?.viewCurrAttach != null) {
                    nativeMapView[viewParent]?.parentView?.removeView(nativeMapView[viewParent]?.viewCurrAttach)
                }
                nativeMapView[viewParent]?.let { it.adsEcmp?.getAdsPl()?.destroyAd(it.adLoad) }
                nativeMapView.remove(viewParent)
            }
        }
    }

    /** Gọi từ AdsHelper.clearActivity, đúng chỗ câu lệnh native đầu tiên của hàm cũ (huỷ quảng cáo từng view rồi xoá map). */
    fun onHostCleared() {
        nativeMapView.values.forEach { it.isShow = false; it.adsEcmp?.getAdsPl()?.destroyAd(it.adLoad) }
        nativeMapView.clear()
    }
}
