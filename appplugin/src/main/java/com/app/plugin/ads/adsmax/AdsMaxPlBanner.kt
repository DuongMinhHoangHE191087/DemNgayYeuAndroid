package com.app.plugin.ads.adsmax

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseBn
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.sdk.AppLovinSdkUtils

internal class AdsMaxPlBanner(context: Context, adBase: AdsBase, type: Int, pl: String) : AdsPlBaseBn(context, adBase, type, pl) {
    override val eventTag: String get() = "max_bn"

    override fun onInit() {
    }

    override fun tryLoad() {
        if (holdUntilReady(AdsMaxMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        showPl = loadPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
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
                // Task J review IMP-1: MAX auto-refresh nạp lại trong cùng MaxAdView và gọi `onAdLoaded` trên cùng listener,
                // nên ở nhánh refresh `adObject` thường là chính `adView` (destroy nó làm banner đang hiện trống và ngừng
                // refresh). Task K mục 1 + Task L mục 2: cả nhánh "not turn" lẫn "refresh" cùng lượt làm như GMA: chỉ gán
                // khi mục trống, không destroy/ghi đè view khác đang giữ.
                var awaitingResponse = true
                var orphaned = false

                val width = ViewGroup.LayoutParams.MATCH_PARENT
                val heightDp = MaxAdFormat.BANNER.getAdaptiveSize(mContext).height
                val heightPx = AppLovinSdkUtils.dpToPx(mContext, heightDp)

                var adView: MaxAdView? = MaxAdView(ecmpLoad.adsId, mContext)
                adView?.layoutParams = FrameLayout.LayoutParams(width, heightPx)
                adView?.setBackgroundColor(Color.BLACK)
                adView?.setListener(object : MaxAdViewAdListener {
                    override fun onAdLoaded(p0: MaxAd) {
                        if (orphaned) return
                        if (awaitingResponse) {
                            awaitingResponse = false
                            if (isStaleLoadCallback(loadGen)) { orphaned = orphanLateView(ecmpLoad, adView, loaded = true); return }
                        }
                        AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "banner", "applovin", ecmpLoad.adsId, p0.networkName ?: "source_null")
                        if (memTurnId == ecmpLoad.turnLoadId) {
                            if (ecmpLoad.isLoading) {
                                LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok loading id=${ecmpLoad.adsId}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.isLoaded = true
                                ecmpLoad.adObject = adView
                                adsPlacementEcmp.isLoading = false
                                adsPlacementEcmp.isloaded = true
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, ecmpLoad, this@AdsMaxPlBanner
                                )
                                ecmpLoad.checkDestroyAd()
                                onFinishLoad()
                            } else {
                                LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh id=${ecmpLoad.adsId}")
                                if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                    LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh id=${ecmpLoad.adsId} ad null")
                                    ecmpLoad.adObject = adView
                                }
                                if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, ecmpLoad, this@AdsMaxPlBanner
                                )
                                ecmpLoad.checkDestroyAd()
                            }
                        } else {
                            LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh not turn id=${ecmpLoad.adsId}")
                            if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh not turn id=${ecmpLoad.adsId} ad null")
                                ecmpLoad.adObject = adView
                            }
                            if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, ecmpLoad, this@AdsMaxPlBanner
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
                        AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "banner", "applovin", ecmpLoad.adsId, "${adError.code}")
                        if (memTurnId == ecmpLoad.turnLoadId) {
                            if (ecmpLoad.isLoading) {
                                LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad loading fail id=${ecmpLoad.adsId} err=${adError.code}")
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
                                LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail id=${ecmpLoad.adsId} err=${adError.code}")
                                if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    "",
                                    "" + adError.code + " " + adError.message
                                )
                            }
                        } else {
                            LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail not turn id=${ecmpLoad.adsId} err=${adError.code}")
                            if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                "",
                                "" + adError.code + " " + adError.message
                            )
                        }
                    }

                    override fun onAdDisplayed(p0: MaxAd) {
                        LogUtil.logD("ads bn max$adsType $showPl ${adsPlacementEcmp.originPl} onAdImpression")
                        adsPlacementEcmp.adsShowCb?.onAdImpression(
                            showPl, adsPlacementEcmp.originPl, adView.adUnitId
                        )
                    }

                    override fun onAdHidden(p0: MaxAd) {
                    }

                    override fun onAdClicked(p0: MaxAd) {
                        LogUtil.logD("ads bn max$adsType $showPl ${adsPlacementEcmp.originPl} onclick")
                        adsPlacementEcmp.adsShowCb?.onAdClick(
                            showPl, adsPlacementEcmp.originPl, adView.adUnitId
                        )
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
                        showPl, adsPlacementEcmp.originPl, "banner", it.adUnitId, it.networkName, it.revenue, "USD"
                    )
                }
                adView?.loadAd()
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "banner", "applovin", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, ecmpLoad, this@AdsMaxPlBanner
                    )
                } else {
                    LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads bn max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("ads bn max$adsType $placement ${adsPlacementEcmp.originPl} show")
        setShowPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = true
        adsPlacementEcmp.adsShowCb = adsShowCb
        return true
    }

    override fun destroyAd(ad: Any?): Boolean {
        (ad as? MaxAdView)?.destroy()
        return true
    }
}
