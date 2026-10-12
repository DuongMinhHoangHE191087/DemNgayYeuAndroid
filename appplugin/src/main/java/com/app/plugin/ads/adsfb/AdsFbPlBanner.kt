package com.app.plugin.ads.adsfb

import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBaseBn
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdListener
import com.facebook.ads.AdSize
import com.facebook.ads.AdView

internal class AdsFbPlBanner(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseBn(context, adBase, type, pl) {
    override val eventTag: String get() = "fb_bn"

    override fun onInit() {
    }

    // See AdsMobPlBanner.load() for why this override exists: without it, refreshBn()'s
    // periodic reload just replays the same already-loaded ecmp (ecmp=null fast path in
    // AdsPlBase.load()), which makes AdsHelper tear the banner down without showing a
    // replacement. Expire the ecmp once stale so load() actually falls through to
    // tryLoad() and fetches a new impression.
    override fun load(placement: String, adsLoadCb: AdsLoadInnerCb?) {
        invalidateStaleLoadedEcmp()
        super.load(placement, adsLoadCb)
    }

    private fun invalidateStaleLoadedEcmp() {
        val ecmp = adsPlacementEcmp.adEcmps?.list?.firstOrNull { it.isLoaded } ?: return
        if (ecmp.timeShow <= 0) return
        val ageSeconds = (System.currentTimeMillis() - ecmp.timeShow) / 1000
        if (ageSeconds < AdsHelper.instance.bannerTimeRefresh()) return
        LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} invalidate stale ecmp id=${ecmp.adsId} age=${ageSeconds}s")
        ecmp.isLoaded = false
        val old = ecmp.adObject as? AdView
        ecmp.adObject = null
        if (old != null) {
            if (old.parent == null) {
                old.destroy()
            } else {
                // Still attached as the currently-shown banner - defer destruction until
                // AdsHelper swaps in the freshly-loaded ad and detaches this one.
                ecmp.checkDestroyAd()
                ecmp.adsMem.add(old)
            }
        }
    }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        if (ad != null) {
            if (ad is AdView) {
                if (ad.parent == null) {
                    ad.destroy()
                    return true
                }
            }
        }
        return false
    }

    override fun tryLoad() {
        if (holdUntilReady(AdsFbMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        showPl = loadPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
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
                var adView = AdView(mContext, idLoad, AdSize.BANNER_HEIGHT_50)
                adView.loadAd(
                    adView.buildLoadAdConfig().withAdListener(object : AdListener {
                        override fun onError(ad: Ad?, adError: AdError?) {
                            if (orphaned) return
                            if (awaitingResponse) {
                                awaitingResponse = false
                                if (isStaleLoadCallback(loadGen)) { orphaned = orphanLateView(ecmpLoad, adView, loaded = false); return }
                            }
                            AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "banner", "facebook", ecmpLoad.adsId, "${adError?.errorCode}")
                            if (memTurnId == ecmpLoad.turnLoadId) {
                                if (ecmpLoad.isLoading) {
                                    LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad loading fail id=${ecmpLoad.adsId} err=${adError?.errorCode}")
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
                                    LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail id=${ecmpLoad.adsId} err=${adError?.errorCode}")
                                    if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        "",
                                        "" + adError?.errorCode + " " + adError?.errorMessage
                                    )
                                }
                            } else {
                                LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail not turn id=${ecmpLoad.adsId} err=${adError?.errorCode}")
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
                            AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "banner", "facebook", ecmpLoad.adsId, "facebook")
                            if (memTurnId == ecmpLoad.turnLoadId) {
                                LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                if (ecmpLoad.isLoading) {
                                    ecmpLoad.isLoading = false
                                    ecmpLoad.isLoaded = true
                                    ecmpLoad.adObject = adView
                                    adsPlacementEcmp.isLoading = false
                                    adsPlacementEcmp.isloaded = true
                                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        idLoad,
                                        ecmpLoad,
                                        this@AdsFbPlBanner
                                    )
                                    ecmpLoad.checkDestroyAd()
                                    onFinishLoad()
                                } else {
                                    if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                        LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId} ad null")
                                        ecmpLoad.adObject = adView
                                    }
                                    if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        idLoad,
                                        ecmpLoad,
                                        this@AdsFbPlBanner
                                    )
                                    ecmpLoad.checkDestroyAd()
                                }
                            } else {
                                LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok not turn id=${ecmpLoad.adsId}")
                                if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                    LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok not turn id=${ecmpLoad.adsId} ad null")
                                    ecmpLoad.adObject = adView
                                }
                                if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    idLoad,
                                    ecmpLoad,
                                    this@AdsFbPlBanner
                                )
                                ecmpLoad.checkDestroyAd()
                            }
                        }

                        override fun onAdClicked(p0: Ad?) {
                            LogUtil.logD("ads bn fb$adsType $showPl ${adsPlacementEcmp.originPl} onclick")
                            adsPlacementEcmp.adsShowCb?.onAdClick(
                                turnPl(adView, showPl), adsPlacementEcmp.originPl, idLoad
                            )
                            AdsHelper.instance.onclickAd()
                        }

                        override fun onLoggingImpression(p0: Ad?) {
                            LogUtil.logD("ads bn fb$adsType $showPl ${adsPlacementEcmp.originPl} onAdImpression")
                            adsPlacementEcmp.adsShowCb?.onAdImpression(
                                turnPl(adView, showPl), adsPlacementEcmp.originPl, idLoad
                            )
                            PaidEvent.metaDirect(turnPl(adView, showPl), adsPlacementEcmp.originPl, "banner", idLoad, "facebook")
                        }
                    }).build()
                )
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "banner", "facebook", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        ecmpLoad.adsId,
                        ecmpLoad,
                        this@AdsFbPlBanner
                    )
                } else {
                    LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads bn fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("ads bn fb$adsType $placement ${adsPlacementEcmp.originPl} show")
        setShowPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = true
        adsPlacementEcmp.adsShowCb = adsShowCb
        return true
    }
}