package com.app.plugin.ads.adsfb

import android.content.Context
import android.view.View
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseBn
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdListener
import com.facebook.ads.AdSize
import com.facebook.ads.AdView

internal class AdsFbPlRect(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseBn(context, adBase, type, pl) {
    override val eventTag: String get() = "fb_rect"

    var adsEcmpGet: AdsEcmp? = null
    var adsGetCb: AdsShowCb? = null

    override fun onInit() {
    }

    override fun tryLoad() {
        if (holdUntilReady(AdsFbMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                if (ecmpLoad.timeShow > 100000) {
                    val dt = (System.currentTimeMillis() - ecmpLoad.timeShow) / 1000
                    if (dt > AdsHelper.instance.rectTimeRefresh()) {
                        ecmpLoad.isLoaded = false
                        if (ecmpLoad.adObject != null) {
                            var adm: AdView = ecmpLoad.adObject as AdView
                            if (adm.parent == null) {
                                adm.destroy()
                            } else {
                                ecmpLoad.checkDestroyAd()
                                ecmpLoad.adsMem.add(adm)
                            }
                        }
                        ecmpLoad.adObject = null
                        ecmpLoad.timeShow = 0
                        LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId} clear old load new")
                    }
                }
            }
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false
                ecmpLoad.turnLoadId++ // Task L mục 1: bộ đếm của mục, không phải giờ (hai request trong cùng ms khác mã)
                val memTurnId = ecmpLoad.turnLoadId
                val loadGen = adsPlacementEcmp.loadGeneration
                // Task J1: callback đầu tiên của listener này là response của request đang chờ (guard theo loadGen như 17
                // lớp khác); các callback sau là auto-refresh của view, đi đường "refresh"/"not turn" như cũ. View của lượt
                // đã bỏ mà không được giữ ([AdsPlBaseBn.orphanLateView]) thì mọi callback sau của nó bị bỏ.
                // Task L mục 3: callback refresh (nhánh "refresh"/"not turn") chỉ báo `adsLoadCb` khi slot không có request đang
                // chờ; có thì callback kết thúc của chính request đó là callback duy nhất tới `adsLoadCb` (response của request
                // luôn đi nhánh loading: cùng mã lượt, mục đang loading).
                // Task M mục 2: hai nhánh đó cũng chỉ gán view refresh vào mục trống khi slot không có request đang chờ, để
                // request đang chờ giữ mục của nó tới cuối: không `LOAD_FAIL_BUT_ADS_NOT_NULL` giả trong lúc chờ, và view của
                // request tới muộn sau watchdog được `keepLateAd` giữ thay vì bị destroy.
                var awaitingResponse = true
                var orphaned = false

                var idLoad = ecmpLoad.adsId
                if (AdsFbMy.instance.flagPp?.compareTo("90351") == 0) {
                    idLoad = "IMG_16_9_APP_INSTALL#$idLoad"
                }
                var adView = AdView(mContext, idLoad, AdSize.RECTANGLE_HEIGHT_250)
                adView.loadAd(
                    adView.buildLoadAdConfig().withAdListener(object : AdListener {
                        override fun onError(ad: Ad?, adError: AdError?) {
                            if (orphaned) return
                            if (awaitingResponse) {
                                awaitingResponse = false
                                if (isStaleLoadCallback(loadGen)) { orphaned = orphanLateView(ecmpLoad, adView, loaded = false); return }
                            }
                            AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "rect", "facebook", ecmpLoad.adsId, "${adError?.errorCode}")
                            if (memTurnId == ecmpLoad.turnLoadId) {
                                if (ecmpLoad.isLoading) {
                                    LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad loading fail id=${ecmpLoad.adsId} err=${adError?.errorCode}")
                                    ecmpLoad.isLoading = false
                                    ecmpLoad.adObject = null
                                    adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                                    if (!adsPlacementEcmp.flag4Load && adsPlacementEcmp.adEcmps?.idxCurrEcpm ?: 0 < adsPlacementEcmp.adEcmps?.list?.size ?: 0) {
                                        tryLoad()
                                    } else {
                                        if (!adsPlacementEcmp.flag4Load) {
                                            adsPlacementEcmp.isLoading = false
                                            adsPlacementEcmp.isloaded = false
                                            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                                loadPl,
                                                adsPlacementEcmp.originPl,
                                                "",
                                                "" + adError?.errorCode + " " + adError?.errorMessage
                                            )
                                        }
                                        onFinishLoad()
                                    }
                                } else {
                                    LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail id=${ecmpLoad.adsId} err=${adError?.errorCode}")
                                    if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        "",
                                        "" + adError?.errorCode + " " + adError?.errorMessage
                                    )
                                }
                            } else {
                                LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail not turn id=${ecmpLoad.adsId} err=${adError?.errorCode}")
                                if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    "",
                                    "" + adError?.errorCode + " " + adError?.errorMessage
                                )
                            }
                        }

                        override fun onAdLoaded(ad: Ad?) {
                            if (orphaned) return
                            if (awaitingResponse) {
                                awaitingResponse = false
                                if (isStaleLoadCallback(loadGen)) { orphaned = orphanLateView(ecmpLoad, adView, loaded = true); return }
                            }
                            AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "rect", "facebook", ecmpLoad.adsId, "facebook")
                            if (memTurnId == ecmpLoad.turnLoadId) {
                                LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                if (ecmpLoad.isLoading) {
                                    ecmpLoad.isLoading = false
                                    ecmpLoad.isLoaded = true
                                    if (ecmpLoad.adObject != null) {
                                        LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok but adobject != null")
                                    }
                                    ecmpLoad.adObject = adView
                                    adsPlacementEcmp.isLoading = false
                                    adsPlacementEcmp.isloaded = true
                                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        idLoad,
                                        ecmpLoad,
                                        this@AdsFbPlRect
                                    )
                                    ecmpLoad.checkDestroyAd()
                                    onFinishLoad()
                                } else {
                                    LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok not turn")
                                    if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                        LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok not turn ad null")
                                        ecmpLoad.adObject = adView
                                    }
                                    if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        idLoad,
                                        ecmpLoad,
                                        this@AdsFbPlRect
                                    )
                                    ecmpLoad.checkDestroyAd()
                                }
                            } else {
                                LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok not turn")
                                if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                    LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok not turn ad null")
                                    ecmpLoad.adObject = adView
                                }
                                if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    idLoad,
                                    ecmpLoad,
                                    this@AdsFbPlRect
                                )
                                ecmpLoad.checkDestroyAd()
                            }
                        }

                        override fun onAdClicked(p0: Ad?) {
                            if (ecmpLoad.flagShow == 1) {
                                LogUtil.logD("ads rect fb$adsType $getPl ${adsPlacementEcmp.originPl} onclick get")
                                adsGetCb?.onAdClick(
                                    turnPl(adView, getPl), adsPlacementEcmp.originPl, idLoad
                                )
                            } else {
                                LogUtil.logD("ads rect fb$adsType $getPl ${adsPlacementEcmp.originPl} onclick show")
                            }
                            AdsHelper.instance.onclickAd()
                        }

                        override fun onLoggingImpression(p0: Ad?) {
                            PaidEvent.metaDirect(turnPl(adView, getPl), adsPlacementEcmp.originPl, "rect", idLoad, "facebook")
                            if (ecmpLoad.flagShow == 1) {
                                LogUtil.logD("ads rect fb$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression get")
                                adsGetCb?.onAdImpression(
                                    turnPl(adView, getPl), adsPlacementEcmp.originPl, idLoad
                                )
                            } else {
                                LogUtil.logD("ads rect fb$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression show")
                            }
                        }
                    }).build()
                )
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "rect", "facebook", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        ecmpLoad.adsId,
                        ecmpLoad,
                        this@AdsFbPlRect
                    )
                } else {
                    LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads rect fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("ads rect fb$adsType $placement ${adsPlacementEcmp.originPl} show not use")
        return false
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdForShow()
        if (ecmpShow != null) {
            LogUtil.logD("ads rect fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow")
            setGetPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = false
            adsGetCb = adsShowCb
            adsEcmpGet = ecmpShow
            ecmpShow.flagShow = 1
            ecmpShow.isLoaded = false
            var ad = ecmpShow.adObject
            ecmpShow.adObject = null

            val adViewCast = ad as? View
            if (adViewCast == null) {
                LogUtil.logD("ads rect fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow ad not View")
                return null
            }
            var obRe = ObjectForShowAdWithViewParent()
            obRe.viewCurrAttach = adViewCast
            obRe.adsEcmp = ecmpShow
            obRe.adLoad = ad
            obRe.type4Time = adsType
            return obRe
        } else {
            LogUtil.logD("ads rect fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not loaded")
            return null
        }
    }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        if (ad != null) {
            if (ad is AdView) {
                var adView: AdView = ad
                if (adView.parent == null) {
                    adView.destroy()
                    return true
                }
            }
        }
        return false
    }
}