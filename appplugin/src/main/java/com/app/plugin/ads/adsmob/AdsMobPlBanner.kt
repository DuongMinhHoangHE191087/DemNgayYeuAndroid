package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import androidx.annotation.RequiresApi
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBaseBn
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.CollapsibleBanner
import com.app.plugin.ads.MainPost
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

internal class AdsMobPlBanner(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseBn(context, adBase, type, pl) {
    override val eventTag: String get() = "mob_bn"

    override fun onInit() {
    }

    /** 1.9.0 Task 3 (D37): slot đã nạp thành công lần đầu; trước đó mọi request mang extras collapsible ([CollapsibleBanner]). */
    private var firstLoadSucceeded = false

    // refreshBn() in AdsHelper re-calls load() every bannerTRefresh seconds to fetch a
    // fresh banner impression. AdsPlBase.load() short-circuits to whichever ecmp still has
    // isLoaded=true though, and a banner's ecmp never flips isLoaded back to false the way
    // native/interstitial do on "consumption" - so without this override every refresh
    // cycle just re-served the same stale AdView via the ecmp=null fast path, which
    // AdsHelper's onLoaded handler reads as "no ad to show" and tears down the
    // currently-displayed banner without putting anything back. Expire the loaded ecmp
    // once it's older than the configured refresh interval so load() falls through to
    // tryLoad() and actually requests a new impression.
    override fun load(placement: String, adsLoadCb: AdsLoadInnerCb?) {
        invalidateStaleLoadedEcmp()
        super.load(placement, adsLoadCb)
    }

    private fun invalidateStaleLoadedEcmp() {
        val ecmp = adsPlacementEcmp.adEcmps?.list?.firstOrNull { it.isLoaded } ?: return
        if (ecmp.timeShow <= 0) return
        val ageSeconds = (System.currentTimeMillis() - ecmp.timeShow) / 1000
        if (ageSeconds < AdsHelper.instance.bannerTimeRefresh()) return
        LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} invalidate stale ecmp id=${ecmp.adsId} age=${ageSeconds}s")
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

    override fun tryLoad() {
        if (!AdsMobMy.instance.isSdkInitialized) {
            adsPlacementEcmp.isLoading = true
            AdsMobMy.instance.runWhenSdkReady(unlessLoadAbandoned { tryLoad() })
            return
        }
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                if (ecmpLoad.adsId.startsWith("ca-app")) {
                    LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
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

                    val adView = AdView(mContext)
                    adView.adUnitId = ecmpLoad.adsId
                    adView.setAdSize(
                        if (AdsHelper.instance.optimizationPolicy.adaptiveBanner && mActivity != null)
                            getAdSize(mActivity!!) else AdSize.BANNER
                    )
                    val adRequest = CollapsibleBanner.request(mContext, adsPlacementEcmp.originPl, !firstLoadSucceeded)
                    adView.adListener = MainPost.adListener(object : AdListener() {
                        override fun onAdLoaded() {
                            if (orphaned) return
                            if (awaitingResponse) {
                                awaitingResponse = false
                                if (isStaleLoadCallback(loadGen)) { orphaned = orphanLateView(ecmpLoad, adView, loaded = true); return }
                            }
                            firstLoadSucceeded = true
                            AdsHelper.instance.onAdLoaded(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                "banner",
                                "admob",
                                ecmpLoad.adsId,
                                adView.responseInfo?.mediationAdapterClassName ?: "source_null"
                            )
                            if (memTurnId == ecmpLoad.turnLoadId) {
                                if (ecmpLoad.isLoading) {
                                    LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok loading id=${ecmpLoad.adsId} collapsible=${adView.isCollapsible}")
                                    ecmpLoad.isLoading = false
                                    ecmpLoad.isLoaded = true
                                    ecmpLoad.adObject = adView
                                    adsPlacementEcmp.isLoading = false
                                    adsPlacementEcmp.isloaded = true
                                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        adView.adUnitId,
                                        ecmpLoad,
                                        this@AdsMobPlBanner
                                    )
                                    ecmpLoad.checkDestroyAd()
                                    onFinishLoad()
                                } else {
                                    LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh id=${ecmpLoad.adsId}")
                                    if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                        LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh id=${ecmpLoad.adsId} ad null")
                                        ecmpLoad.adObject = adView
                                    }
                                    if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        adView.adUnitId,
                                        ecmpLoad,
                                        this@AdsMobPlBanner
                                    )
                                    ecmpLoad.checkDestroyAd()
                                }
                            } else {
                                LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh not turn id=${ecmpLoad.adsId}")
                                if (ecmpLoad.adObject == null && !adsPlacementEcmp.isLoading) {
                                    LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok refresh not turn id=${ecmpLoad.adsId} ad null")
                                    ecmpLoad.adObject = adView
                                }
                                if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    adView.adUnitId,
                                    ecmpLoad,
                                    this@AdsMobPlBanner
                                )
                                ecmpLoad.checkDestroyAd()
                            }
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            if (orphaned) return
                            if (awaitingResponse) {
                                awaitingResponse = false
                                if (isStaleLoadCallback(loadGen)) { orphaned = orphanLateView(ecmpLoad, adView, loaded = false); return }
                            }
                            AdsHelper.instance.onAdLoadFail(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                "banner",
                                "admob",
                                ecmpLoad.adsId,
                                "${adError.code}"
                            )
                            if (memTurnId == ecmpLoad.turnLoadId) {
                                if (ecmpLoad.isLoading) {
                                    LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad loading fail id=${ecmpLoad.adsId} err=${adError.message}")
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
                                    LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail id=${ecmpLoad.adsId} err=${adError.code}")
                                    if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        "",
                                        "" + adError.code + " " + adError.message
                                    )
                                }
                            } else {
                                LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad refresh fail not turn id=${ecmpLoad.adsId} err=${adError.code}")
                                if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    "",
                                    "" + adError.code + " " + adError.message
                                )
                            }
                        }

                        override fun onAdClicked() {
                            LogUtil.logD("ads bn admob$adsType $showPl ${adsPlacementEcmp.originPl} onclick")
                            adsPlacementEcmp.adsShowCb?.onAdClick(
                                turnPl(adView, showPl), adsPlacementEcmp.originPl, adView.adUnitId
                            )
                            AdsHelper.instance.onclickAd()
                        }

                        override fun onAdClosed() {
                        }

                        override fun onAdImpression() {
                            LogUtil.logD("ads bn admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdImpression id=${ecmpLoad.adsId}")
                            adsPlacementEcmp.adsShowCb?.onAdImpression(
                                turnPl(adView, showPl), adsPlacementEcmp.originPl, adView.adUnitId
                            )
                        }

                        override fun onAdOpened() {
                        }
                    })
                    adView.onPaidEventListener = MainPost.paid { adValue ->
                        val netName = AdsMobMy.instance.getAdnetName(adView.responseInfo)
                        PaidEvent.admob(
                            turnPl(adView, showPl),
                            adsPlacementEcmp.originPl,
                            "banner",
                            adView.adUnitId,
                            netName,
                            adValue
                        )
                    }
                    adView.loadAd(adRequest)
                    AdsHelper.instance.onAdLoadStart(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        "banner",
                        "admob",
                        ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad idload not correct")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "idload not correct"
                    )
                }
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        ecmpLoad.adsId,
                        ecmpLoad,
                        this@AdsMobPlBanner
                    )
                } else {
                    LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("ads bn admob$adsType $placement ${adsPlacementEcmp.originPl} show")
        setShowPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = true
        adsPlacementEcmp.adsShowCb = adsShowCb
        return true
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

    private fun getAdSize(context: Activity): AdSize {
        return try {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                context,
                getScreenWidth(context)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            AdSize.BANNER
        }
    }

    private fun getScreenWidth(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) getWidthR(activity)
        else getWidthRLower(activity)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getWidthR(activity: Activity): Int {
        val windowMetrics = activity.windowManager.currentWindowMetrics
        val bounds = windowMetrics.bounds
        val screenWidthPixels = bounds.width().toFloat()
        val density = activity.resources.displayMetrics.density
        return (screenWidthPixels / density).toInt()
    }

    private fun getWidthRLower(activity: Activity): Int {
        val display = activity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display?.getMetrics(outMetrics)
        val screenWidthPixels = outMetrics.widthPixels.toFloat()
        val density = activity.resources.displayMetrics.density
        return (screenWidthPixels / density).toInt()
    }
}