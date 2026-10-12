package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import androidx.annotation.RequiresApi
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBaseBn
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.CollapsibleBanner
import com.app.plugin.ads.MainPost
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import java.util.WeakHashMap

/**
 * 2.4.0 NG2: banner AdMob trên GMA Next-Gen (Hợp đồng B "NG2"). `BannerAd.load(BannerAdRequest, AdLoadCallback)` [S10] không
 * nhận Context, size `getLargeAnchoredAdaptiveBannerAdSize` (current-orientation deprecated [S11]; chiều cao có thể khác
 * Legacy — A/B ghi), extras collapsible qua [CollapsibleBanner] [S12]. Legacy có MỘT listener cho response + auto-refresh
 * của `AdView`; Next-Gen tách: [LoadCb] là response (một lần), [RefreshCb] là refresh — cùng nhánh "loading"/"refresh"/
 * "not turn" như Legacy (Task J1/L/M). Mọi callback (load, refresh, sự kiện gồm `onAdPaid`) về main qua [MainPost] (luật 36);
 * view lấy bằng `getView(activity)` chỉ trong callback đã bọc. Huỷ đi qua `BannerAd.destroy()` ([destroyView]).
 */
internal class AdsMobPlBanner(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseBn(context, adBase, type, pl) {
    override val eventTag: String get() = "mob_bn"

    override fun onInit() {
    }

    /** 1.9.0 Task 3 (D37): slot đã nạp thành công lần đầu; trước đó mọi request mang extras collapsible ([CollapsibleBanner]). */
    private var firstLoadSucceeded = false

    // refreshBn() của AdsHelper gọi lại load() mỗi bannerTRefresh giây; mục đã load của banner không tự hạ isLoaded như
    // native/interstitial, nên hết hạn mục cũ hơn chu kỳ refresh để load() đi tới tryLoad() và xin impression mới (Legacy).
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
        val old = ecmp.adObject as? View
        ecmp.adObject = null
        if (old != null) {
            if (old.parent == null) {
                destroyView(old)
            } else {
                // Còn gắn (banner đang hiện): hoãn huỷ tới khi AdsHelper thay banner mới và gỡ view này.
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
        val tag = "ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl}"
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad == null) {
            LogUtil.logD("$tag tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null")
            return
        }
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
        val host = mActivity
        val size = if (AdsHelper.instance.optimizationPolicy.adaptiveBanner && host != null) getAdSize(host) else AdSize.BANNER
        val request = CollapsibleBanner.request(mContext, adsPlacementEcmp.originPl, !firstLoadSucceeded, ecmpLoad.adsId, size)
        loader(request, MainPost.load(LoadCb(ecmpLoad, ecmpLoad.turnLoadId, adsPlacementEcmp.loadGeneration)))
        AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "banner", "admob", ecmpLoad.adsId)
    }

    /**
     * Response của request đang chờ [S10] (đã về main). Lượt đã bỏ ([isStaleLoadCallback], Task J1): thành công đi
     * [orphanLateView] (mục rảnh và trống thì giữ, không thì huỷ), lỗi thì bỏ — không tới `adsLoadCb` của lượt mới.
     */
    private inner class LoadCb(private val ecmp: AdsEcmp, private val turn: Long, private val loadGen: Int) : AdLoadCallback<BannerAd> {
        override fun onAdLoaded(ad: BannerAd) {
            val view = mActivity?.let { ad.getView(it) }
            if (view == null) {
                ad.destroy()
                if (!isStaleLoadCallback(loadGen)) failed(ecmp, turn, -1, "activity unavailable")
                return
            }
            track(view, ad)
            // Trước guard stale (IMP-1): quảng cáo tới muộn mà được giữ rồi hiện sau vẫn tính doanh thu.
            ad.adEventCallback = MainPost.bannerEvents(Events(ecmp, view, ad))
            val stale = isStaleLoadCallback(loadGen)
            if (stale && orphanLateView(ecmp, view, loaded = true)) return
            ad.bannerAdRefreshCallback = MainPost.bannerRefresh(RefreshCb(ecmp, turn, view, ad))
            if (!stale) loaded(ecmp, turn, view, ad)
        }

        override fun onAdFailedToLoad(adError: LoadAdError) {
            if (isStaleLoadCallback(loadGen)) return // lượt đã bỏ: không có view để huỷ, không đẩy waterfall
            failed(ecmp, turn, adError.code.value, adError.message)
        }
    }

    /** Auto-refresh của banner đã nạp (Next-Gen tách khỏi response): nhánh "refresh"/"not turn" của Legacy. */
    private inner class RefreshCb(private val ecmp: AdsEcmp, private val turn: Long, private val view: View, private val ad: BannerAd) :
        BannerAdRefreshCallback {
        override fun onAdRefreshed() = loaded(ecmp, turn, view, ad)
        override fun onAdFailedToRefresh(adError: LoadAdError) = failed(ecmp, turn, adError.code.value, adError.message)
    }

    private inner class Events(private val ecmp: AdsEcmp, private val view: View, private val ad: BannerAd) : BannerAdEventCallback {
        override fun onAdClicked() {
            LogUtil.logD("ads bn admob$adsType $showPl ${adsPlacementEcmp.originPl} onclick")
            adsPlacementEcmp.adsShowCb?.onAdClick(turnPl(view, showPl), adsPlacementEcmp.originPl, ecmp.adsId)
            AdsHelper.instance.onclickAd()
        }

        override fun onAdImpression() {
            LogUtil.logD("ads bn admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdImpression id=${ecmp.adsId}")
            adsPlacementEcmp.adsShowCb?.onAdImpression(turnPl(view, showPl), adsPlacementEcmp.originPl, ecmp.adsId)
        }

        override fun onAdPaid(value: AdValue) {
            PaidEvent.admob(turnPl(view, showPl), adsPlacementEcmp.originPl, "banner", ecmp.adsId, adapter(ad), value) // NG5b: entry Next-Gen của PaidEvent (NG3)
        }
    }

    /** Response hoặc refresh thành công của [view]: nhánh "loading" (lượt [turn] đang chờ) hay "refresh"/"not turn". */
    private fun loaded(ecmp: AdsEcmp, turn: Long, view: View, ad: BannerAd) {
        firstLoadSucceeded = true
        AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "banner", "admob", ecmp.adsId, adapter(ad))
        val tag = "ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl}"
        if (turn == ecmp.turnLoadId && ecmp.isLoading) {
            LogUtil.logD("$tag tryLoad ok loading id=${ecmp.adsId} collapsible=${ad.isCollapsible()}")
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

    /** Lỗi response (lượt [turn] đang chờ: sang mạng kế hoặc báo lỗi) hoặc lỗi refresh. */
    private fun failed(ecmp: AdsEcmp, turn: Long, code: Int, message: String) {
        AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "banner", "admob", ecmp.adsId, "$code")
        val tag = "ads bn admob$adsType $loadPl ${adsPlacementEcmp.originPl}"
        if (turn == ecmp.turnLoadId && ecmp.isLoading) {
            LogUtil.logD("$tag tryLoad loading fail id=${ecmp.adsId} err=$message")
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
        LogUtil.logD("ads bn admob$adsType $placement ${adsPlacementEcmp.originPl} show")
        setShowPlacement(placement)
        adsPlacementEcmp.isloaded = false
        adsPlacementEcmp.isShowing = true
        adsPlacementEcmp.adsShowCb = adsShowCb
        return true
    }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        val view = ad as? View ?: return false
        return view.parent == null && destroyView(view)
    }

    private fun getAdSize(activity: Activity): AdSize = try {
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, getScreenWidth(activity))
    } catch (e: Exception) {
        LogUtil.logE("ads bn admob$adsType adaptive size failed, BANNER: ${e.message}")
        AdSize.BANNER
    }

    private fun getScreenWidth(activity: Activity): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) getWidthR(activity) else getWidthRLower(activity)

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getWidthR(activity: Activity): Int {
        val bounds = activity.windowManager.currentWindowMetrics.bounds
        return (bounds.width().toFloat() / activity.resources.displayMetrics.density).toInt()
    }

    private fun getWidthRLower(activity: Activity): Int {
        val outMetrics = DisplayMetrics()
        @Suppress("DEPRECATION") activity.windowManager.defaultDisplay?.getMetrics(outMetrics)
        return (outMetrics.widthPixels.toFloat() / activity.resources.displayMetrics.density).toInt()
    }

    internal companion object {
        /** Seam test: `BannerAd.load` tĩnh của Next-Gen (không Context) [S10]; banner và rect dùng chung. */
        @Volatile internal var loader: (BannerAdRequest, AdLoadCallback<BannerAd>) -> Unit = { r, cb -> BannerAd.load(r, cb) }

        /** View → BannerAd của nó: Next-Gen huỷ qua quảng cáo, còn slot giữ view (khoá yếu theo view). */
        private val views = WeakHashMap<View, BannerAd>()

        fun track(view: View, ad: BannerAd) {
            synchronized(views) { views[view] = ad }
        }

        /** Huỷ `BannerAd` của [view] (một lần); `false` khi view không phải banner Next-Gen của ta. */
        fun destroyView(view: View): Boolean {
            val ad = synchronized(views) { views.remove(view) } ?: return false
            ad.destroy()
            return true
        }

        fun adapter(ad: BannerAd): String = ad.getResponseInfo()?.adapterClassName ?: "source_null"
    }
}
