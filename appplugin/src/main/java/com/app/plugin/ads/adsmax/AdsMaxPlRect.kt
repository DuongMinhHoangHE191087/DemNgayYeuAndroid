package com.app.plugin.ads.adsmax

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseBn
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.sdk.AppLovinSdkUtils

internal class AdsMaxPlRect(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseBn(context, adBase, type, pl) {
    override val eventTag: String get() = "max_rect"

    var adsEcmpGet: AdsEcmp? = null
    var adsGetCb: AdsShowCb? = null

    override fun onInit() {
    }

    override fun tryLoad() {
        if (holdUntilReady(AdsMaxMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
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
                // Task J review IMP-1: MAX auto-refresh nạp lại trong cùng MaxAdView và gọi `onAdLoaded` trên cùng listener;
                // `getAdForShow` bỏ `adObject` nên refresh đầu gán lại, refresh sau gặp `adObject === adView`: không destroy
                // chính nó (làm rect đang hiện trống và ngừng refresh).
                // Task K mục 1: nhánh "not turn" (view đã lấy ra hiện refresh sau khi mục đã nạp view mới của lượt sau) làm
                // như GMA: chỉ gán khi mục trống, không destroy/ghi đè view mới đang giữ. Task L mục 2: nhánh "refresh" cùng
                // lượt cũng vậy (mục có thể đang giữ view khác, vd. view lượt trước vừa gán ở nhánh "not turn").
                var awaitingResponse = true
                var orphaned = false

                val widthDp = 400
                val widthPx = AppLovinSdkUtils.dpToPx(mContext, widthDp)
                val heightDp = 300
                val heightPx = AppLovinSdkUtils.dpToPx(mContext, heightDp)

                var adView: MaxAdView? = MaxAdView(ecmpLoad.adsId, MaxAdFormat.MREC, mContext)
                adView?.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
                adView?.setBackgroundColor(Color.BLACK)
                adView?.setListener(object : MaxAdViewAdListener {
                    override fun onAdLoaded(ad: MaxAd) {
                        if (orphaned) return
                        if (awaitingResponse) {
                            awaitingResponse = false
                            if (isStaleLoadCallback(loadGen)) { orphaned = orphanLateView(ecmpLoad, adView, loaded = true); return }
                        }
                        AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "rect", "applovin", ecmpLoad.adsId, ad.networkName ?: "source_null")
                        if (memTurnId == ecmpLoad.turnLoadId) {
                            if (ecmpLoad.isLoading) {
                                LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.isLoaded = true
                                ecmpLoad.adObject = adView
                                adsPlacementEcmp.isLoading = false
                                adsPlacementEcmp.isloaded = true
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    ad.adUnitId,
                                    ecmpLoad,
                                    this@AdsMaxPlRect
                                )
                                ecmpLoad.checkDestroyAd()
                                onFinishLoad()
                            } else {
                                LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh id=${ecmpLoad.adsId}")
                                if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                    LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh id=${ecmpLoad.adsId} ad null")
                                    ecmpLoad.adObject = adView
                                }
                                if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    ad.adUnitId,
                                    ecmpLoad,
                                    this@AdsMaxPlRect
                                )
                                ecmpLoad.checkDestroyAd()
                            }
                        } else {
                            LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok not turn")
                            if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok not turn ad null")
                                ecmpLoad.adObject = adView
                            }
                            if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                ad.adUnitId,
                                ecmpLoad,
                                this@AdsMaxPlRect
                            )
                            ecmpLoad.checkDestroyAd()
                        }
                    }

                    override fun onAdLoadFailed(msg: String, adError: MaxError) {
                        if (orphaned) return
                        if (awaitingResponse) {
                            awaitingResponse = false
                            if (isStaleLoadCallback(loadGen)) { orphaned = orphanLateView(ecmpLoad, adView, loaded = false); return }
                        }
                        AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "rect", "applovin", ecmpLoad.adsId, "${adError.code}")
                        if (memTurnId == ecmpLoad.turnLoadId) {
                            if (ecmpLoad.isLoading) {
                                LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad loading fail id=${ecmpLoad.adsId} err=${adError.code}")
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
                                            "" + adError.code + " " + adError.message
                                        )
                                    }
                                    onFinishLoad()
                                }
                            } else {
                                LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail id=${ecmpLoad.adsId} err=${adError.code}")
                                if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    "",
                                    "" + adError.code + " " + adError.message
                                )
                            }
                        } else {
                            LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail not turn id=${ecmpLoad.adsId} err=${adError.code}")
                            if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                "",
                                "" + adError.code + " " + adError.message
                            )
                        }
                    }

                    override fun onAdDisplayed(p0: MaxAd) {
                        if (ecmpLoad.flagShow == 1) {
                            LogUtil.logD("ads rect max$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression get")
                            adsGetCb?.onAdImpression(
                                getPl, adsPlacementEcmp.originPl, adView.adUnitId
                            )
                        } else {
                            LogUtil.logD("ads rect max$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression show")
                        }
                    }

                    override fun onAdHidden(p0: MaxAd) {
                    }

                    override fun onAdClicked(p0: MaxAd) {
                        if (ecmpLoad.flagShow == 1) {
                            LogUtil.logD("ads rect max$adsType $getPl ${adsPlacementEcmp.originPl} onclick get")
                            adsGetCb?.onAdClick(
                                getPl, adsPlacementEcmp.originPl, adView.adUnitId
                            )
                        } else {
                            LogUtil.logD("ads rect max$adsType $getPl ${adsPlacementEcmp.originPl} onclick show")
                        }
                        AdsHelper.instance.onclickAd()
                    }

                    override fun onAdDisplayFailed(p0: MaxAd, p1: MaxError) {
                    }

                    override fun onAdExpanded(p0: MaxAd) {
                    }

                    override fun onAdCollapsed(p0: MaxAd) {
                    }
                })
                adView?.setRevenueListener {
                    PaidEvent.max(
                        getPl, adsPlacementEcmp.originPl, "rect", it.adUnitId, it.networkName, it.revenue, "USD"
                    )
                }
                adView?.loadAd()
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "rect", "applovin", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        ecmpLoad.adsId,
                        ecmpLoad,
                        this@AdsMaxPlRect
                    )
                } else {
                    LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads rect max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("ads rect max$adsType $placement ${adsPlacementEcmp.originPl} show not use")
        return false
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdForShow()
        if (ecmpShow != null) {
            LogUtil.logD("ads rect max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow")
            setGetPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = false
            adsGetCb = adsShowCb
            adsEcmpGet = ecmpShow
            ecmpShow.flagShow = 1
            ecmpShow.isLoaded = false
            var ad = ecmpShow.adObject
            ecmpShow.adObject = null

            val adView = ad as? View
            if (adView == null) {
                LogUtil.logD("ads rect max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow ad not View")
                return null
            }
            var obRe = ObjectForShowAdWithViewParent()
            obRe.viewCurrAttach = adView
            obRe.adsEcmp = ecmpShow
            obRe.adLoad = ad
            obRe.type4Time = adsType
            return obRe
        } else {
            LogUtil.logD("ads rect max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not loaded")
            return null
        }
    }

    override fun destroyAd(ad: Any?): Boolean {
        (ad as? MaxAdView)?.destroy()
        return true
    }
}