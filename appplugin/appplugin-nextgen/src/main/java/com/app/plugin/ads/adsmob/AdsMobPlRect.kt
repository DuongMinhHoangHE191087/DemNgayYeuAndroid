package com.app.plugin.ads.adsmob

import android.content.Context
import android.view.View
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlBaseBn
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.ads.adsmob.AdsMobPlBanner.Companion.adapter
import com.app.plugin.ads.adsmob.AdsMobPlBanner.Companion.destroyView
import com.app.plugin.ads.adsmob.AdsMobPlBanner.Companion.loader
import com.app.plugin.ads.adsmob.AdsMobPlBanner.Companion.track
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * 2.4.0 NG2: rect (MREC) AdMob trên GMA Next-Gen — `BannerAdRequest` `AdSize.MEDIUM_RECTANGLE` [S11] qua
 * [AdRequestFactory.banner] (`npa`), `BannerAd.load` qua seam chung [AdsMobPlBanner.loader]. Response ([LoadCb]) tách khỏi
 * auto-refresh ([RefreshCb]) như banner; mọi callback về main qua [MainPost] (luật 36); huỷ qua [AdsMobPlBanner.destroyView].
 */
internal class AdsMobPlRect(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseBn(context, adBase, type, pl) {
    override val eventTag: String get() = "mob_rect"

    var adsEcmpGet: AdsEcmp? = null
    var adsGetCb: AdsShowCb? = null

    override fun onInit() {
    }

    override fun tryLoad() {
        if (!AdsMobMy.instance.isSdkInitialized) {
            adsPlacementEcmp.isLoading = true
            AdsMobMy.instance.runWhenSdkReady(unlessLoadAbandoned { tryLoad() })
            return
        }
        loadPl = adsPlacementEcmp.getPl
        val tag = "ads rect admob$adsType $loadPl ${adsPlacementEcmp.originPl}"
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad == null) {
            LogUtil.logD("$tag tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null")
            return
        }
        expireShown(ecmpLoad, tag)
        if (ecmpLoad.isLoaded) {
            LogUtil.logD("$tag tryLoad has loaded")
            adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, ecmpLoad, this)
            return
        }
        if (ecmpLoad.isLoading) {
            LogUtil.logD("$tag tryLoad is loading")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "isloading")
            return
        }
        if (!ecmpLoad.adsId.startsWith("ca-app")) {
            LogUtil.logD("$tag tryLoad idload not correct")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "idload not correct")
            return
        }
        LogUtil.logD("$tag tryLoad id=${ecmpLoad.adsId}")
        adsPlacementEcmp.isLoading = true
        adsPlacementEcmp.isHasLoad = true
        ecmpLoad.isLoading = true
        ecmpLoad.isLoaded = false
        ecmpLoad.turnLoadId++ // Task L mục 1: bộ đếm của mục, không phải giờ
        val request = AdRequestFactory.banner(mContext, ecmpLoad.adsId, AdSize.MEDIUM_RECTANGLE)
        loader(request, MainPost.load(LoadCb(ecmpLoad, ecmpLoad.turnLoadId, adsPlacementEcmp.loadGeneration)))
        AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "rect", "admob", ecmpLoad.adsId)
    }

    /** Rect đã lấy ra hiện quá `rectTimeRefresh` giây: bỏ mục đã load để nạp mới (Legacy); view còn gắn thì hoãn huỷ. */
    private fun expireShown(ecmp: AdsEcmp, tag: String) {
        if (!ecmp.isLoaded || ecmp.isLoading || ecmp.timeShow <= 100000) return
        if ((System.currentTimeMillis() - ecmp.timeShow) / 1000 <= AdsHelper.instance.rectTimeRefresh()) return
        ecmp.isLoaded = false
        val old = ecmp.adObject as? View
        if (old != null) {
            if (old.parent == null) {
                destroyView(old)
            } else {
                ecmp.checkDestroyAd()
                ecmp.adsMem.add(old)
            }
        }
        ecmp.adObject = null
        ecmp.timeShow = 0
        LogUtil.logD("$tag tryLoad id=${ecmp.adsId} clear old load new")
    }

    /** Response của request đang chờ (đã về main); lượt đã bỏ: thành công qua [orphanLateView], lỗi bỏ (Task J1). */
    private inner class LoadCb(private val ecmp: AdsEcmp, private val turn: Long, private val loadGen: Int) : AdLoadCallback<BannerAd> {
        override fun onAdLoaded(ad: BannerAd) {
            val view = mActivity?.let { ad.getView(it) }
            if (view == null) {
                ad.destroy()
                if (!isStaleLoadCallback(loadGen)) failed(ecmp, turn, -1, "activity unavailable")
                return
            }
            track(view, ad)
            ad.adEventCallback = MainPost.bannerEvents(Events(ecmp, view, ad)) // trước guard stale (IMP-1)
            val stale = isStaleLoadCallback(loadGen)
            if (stale && orphanLateView(ecmp, view, loaded = true)) return
            ad.bannerAdRefreshCallback = MainPost.bannerRefresh(RefreshCb(ecmp, turn, view, ad))
            if (!stale) loaded(ecmp, turn, view, ad)
        }

        override fun onAdFailedToLoad(adError: LoadAdError) {
            if (isStaleLoadCallback(loadGen)) return
            failed(ecmp, turn, adError.code.value, adError.message)
        }
    }

    private inner class RefreshCb(private val ecmp: AdsEcmp, private val turn: Long, private val view: View, private val ad: BannerAd) :
        BannerAdRefreshCallback {
        override fun onAdRefreshed() = loaded(ecmp, turn, view, ad)
        override fun onAdFailedToRefresh(adError: LoadAdError) = failed(ecmp, turn, adError.code.value, adError.message)
    }

    private inner class Events(private val ecmp: AdsEcmp, private val view: View, private val ad: BannerAd) : BannerAdEventCallback {
        override fun onAdClicked() {
            if (ecmp.flagShow == 1) {
                LogUtil.logD("ads rect admob$adsType $getPl ${adsPlacementEcmp.originPl} onclick get")
                adsGetCb?.onAdClick(turnPl(view, getPl), adsPlacementEcmp.originPl, ecmp.adsId)
            } else {
                LogUtil.logD("ads rect admob$adsType $getPl ${adsPlacementEcmp.originPl} onclick show")
            }
            AdsHelper.instance.onclickAd()
        }

        override fun onAdImpression() {
            if (ecmp.flagShow == 1) {
                LogUtil.logD("ads rect admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression get id=${ecmp.adsId}")
                adsGetCb?.onAdImpression(turnPl(view, getPl), adsPlacementEcmp.originPl, ecmp.adsId)
            } else {
                LogUtil.logD("ads rect admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression show id=${ecmp.adsId}")
            }
        }

        override fun onAdPaid(value: AdValue) {
            PaidEvent.admob(turnPl(view, getPl), adsPlacementEcmp.originPl, "rect", ecmp.adsId, adapter(ad), value)
        }
    }

    private fun loaded(ecmp: AdsEcmp, turn: Long, view: View, ad: BannerAd) {
        AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "rect", "admob", ecmp.adsId, adapter(ad))
        val tag = "ads rect admob$adsType $loadPl ${adsPlacementEcmp.originPl}"
        if (turn == ecmp.turnLoadId && ecmp.isLoading) {
            LogUtil.logD("$tag tryLoad ok loading id=${ecmp.adsId}")
            ecmp.isLoading = false
            ecmp.isLoaded = true
            ecmp.adObject = view
            adsPlacementEcmp.isLoading = false
            adsPlacementEcmp.isloaded = true
            adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ecmp.adsId, ecmp, this)
            ecmp.checkDestroyAd()
            onFinishLoad()
            return
        }
        LogUtil.logD("$tag tryLoad ok refresh${if (turn == ecmp.turnLoadId) "" else " not turn"} id=${ecmp.adsId}")
        // Task L mục 3 / Task M mục 2: request đang chờ giữ mục của nó và là callback duy nhất tới adsLoadCb.
        if (ecmp.adObject == null && !adsPlacementEcmp.isLoading) ecmp.adObject = view
        if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, ecmp.adsId, ecmp, this)
        ecmp.checkDestroyAd()
    }

    private fun failed(ecmp: AdsEcmp, turn: Long, code: Int, message: String) {
        AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "rect", "admob", ecmp.adsId, "$code")
        val tag = "ads rect admob$adsType $loadPl ${adsPlacementEcmp.originPl}"
        if (turn == ecmp.turnLoadId && ecmp.isLoading) {
            LogUtil.logD("$tag tryLoad loading fail id=${ecmp.adsId} err=$code")
            ecmp.isLoading = false
            ecmp.adObject = null
            adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
            if (!adsPlacementEcmp.flag4Load && (adsPlacementEcmp.adEcmps?.idxCurrEcpm ?: 0) < (adsPlacementEcmp.adEcmps?.list?.size ?: 0)) {
                tryLoad()
                return
            }
            if (!adsPlacementEcmp.flag4Load) {
                adsPlacementEcmp.isLoading = false
                adsPlacementEcmp.isloaded = false
                adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "$code $message")
            }
            onFinishLoad()
            return
        }
        LogUtil.logD("$tag tryLoad refresh fail${if (turn == ecmp.turnLoadId) "" else " not turn"} id=${ecmp.adsId} err=$code")
        if (!adsPlacementEcmp.isLoading) adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "$code $message")
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("ads rect admob$adsType $placement ${adsPlacementEcmp.originPl} show not use")
        return false
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdForShow()
        if (ecmpShow == null) {
            LogUtil.logD("ads rect admob$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not loaded")
            return null
        }
        LogUtil.logD("ads rect admob$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow")
        setGetPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = false
        adsGetCb = adsShowCb
        adsEcmpGet = ecmpShow
        ecmpShow.flagShow = 1
        ecmpShow.isLoaded = false
        val ad = ecmpShow.adObject
        ecmpShow.adObject = null
        val adView = ad as? View
        if (adView == null) {
            LogUtil.logD("ads rect admob$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow ad not View")
            return null
        }
        val obRe = ObjectForShowAdWithViewParent()
        obRe.viewCurrAttach = adView
        obRe.adsEcmp = ecmpShow
        obRe.adLoad = ad
        obRe.type4Time = adsType
        return obRe
    }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        val view = ad as? View ?: return false
        return view.parent == null && destroyView(view)
    }
}
