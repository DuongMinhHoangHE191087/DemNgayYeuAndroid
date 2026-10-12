package com.app.plugin.ads.adsmax

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.app.plugin.R
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlNtClBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.AdsUtil
import com.app.plugin.ads.AppResumeIF
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean

internal class AdsMaxPlNtCl(context: Context, adBase: AdsBase, type: Int, pl: String) : AdsPlNtClBase(context, adBase, type, pl),
    AppResumeIF {
    override val eventTag: String get() = "max_nt_cl"

    private val timeShowBtClose = 1
    var adsEcmpShow: AdsEcmp? = null
    var nativeAdShow: MaxAd? = null

    private var nativeAdLoader: MaxNativeAdLoader? = null

    /**
     * Loader theo unit id của placement này: đổi mục thì dùng lại loader của unit đó, không dựng/destroy lại. Số loader
     * không vượt số id của danh sách (danh sách MAX chỉ đổi lúc dựng); trước Task 14 loader cũng không bao giờ bị destroy.
     */
    private val loaderByUnit = HashMap<String, MaxNativeAdLoader>()

    private lateinit var admaxmy: AdsMaxMy

    override fun onInit() {
        admaxmy = AdsMaxMy.instance
    }

    /** Mục waterfall mà đối tượng create-once đang gắn (unit id + instance AdsEcmp); listener chụp nó lúc dựng. */
    private var boundEcmp: AdsEcmp? = null

    /**
     * Cờ của listener đang gắn: đổi mục thì hạ cờ cũ, callback load của listener cũ (loader cũ vẫn giữ nó để click/doanh
     * thu tới được) bị bỏ. Listener đọc cờ chụp theo listener, không đọc field (không thêm accessor vào dump).
     */
    private var boundLive: AtomicBoolean? = null

    /** [AdsPlacementEcmpBase.loadGeneration] lúc gửi request gần nhất; listener của đối tượng đang gắn đọc từ field. */
    private var loadGen = 0

    override fun tryLoad() {
        if (holdUntilReady(AdsMaxMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads bnntcl max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false

                if (nativeAdLoader == null || boundEcmp !== ecmpLoad) {
                    // Mục khác (bước waterfall sau của danh sách nhiều id, hoặc parserEcpms đổi unit): trỏ sang loader của unit này
                    // (một loader mỗi unit) và gắn listener mới. Không gỡ listener, không destroy loader cũ: MAX đọc listener của loader
                    // lúc click/trả doanh thu, quảng cáo nó đã render có thể còn trên màn hình (review Task 14 I1). Callback load đã
                    // xếp hàng của listener cũ bị bỏ bằng cờ `live`.
                    boundLive?.set(false)
                    val live = AtomicBoolean(true).also { boundLive = it }
                    val lateTag = "ads bnntcl max$adsType ${adsPlacementEcmp.originPl}"
                    boundEcmp = ecmpLoad
                    nativeAdLoader = loaderByUnit.getOrPut(ecmpLoad.adsId) { MaxNativeAdLoader(ecmpLoad.adsId, mContext) }
                    nativeAdLoader?.setNativeAdListener(object : MaxNativeAdListener() {
                        override fun onNativeAdLoaded(
                            nativeAdView: MaxNativeAdView?,
                            nativeAd: MaxAd
                        ) {
                            // Callback load của listener đã thay (loader cũ giữ listener, không gỡ): không thuộc request đang chờ; bỏ ad.
                            if (!live.get()) { LogUtil.logD("$lateTag late load callback of replaced loader dropped"); destroyAd(nativeAd); return }
                            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return }
                            adsPlacementEcmp.isLoading = false
                            adsPlacementEcmp.isloaded = true
                            ecmpLoad.isLoading = false
                            ecmpLoad.isLoaded = true
                            AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "native_collapse", "applovin", ecmpLoad.adsId, nativeAd.networkName ?: "source_null")
                            if (mActivity != null && mActivity!!.isDestroyed) {
                                nativeAdLoader?.destroy(nativeAd)
                            } else {
                                LogUtil.logD("ads bnntcl max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                ecmpLoad.adObject = nativeAd
                                val adidloaded = ecmpLoad.adsId
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, adidloaded
                                )
                                onFinishLoad()
                            }
                        }

                        override fun onNativeAdLoadFailed(adUnitId: String, adError: MaxError) {
                            if (!live.get()) { LogUtil.logD("$lateTag late load callback of replaced loader dropped"); return }
                            if (isStaleLoadCallback(loadGen)) return
                            LogUtil.logD("ads bnntcl max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                            ecmpLoad.isLoading = false
                            ecmpLoad.adObject = null
                            adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                            AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "native_collapse", "applovin", ecmpLoad.adsId, "${adError.code}")
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
                        }

                        override fun onNativeAdClicked(nativeAd: MaxAd) {
                            LogUtil.logD("ads bnntcl max$adsType $showPl ${adsPlacementEcmp.originPl} onAdClicked")
                            adsPlacementEcmp.adsShowCb?.onAdClick(
                                showPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                            )
                            AdsHelper.instance.onclickAd()
                        }
                    })
                    nativeAdLoader?.setRevenueListener {
                        PaidEvent.max(
                            showPl, adsPlacementEcmp.originPl, "native_collapse", it.adUnitId, it.networkName, it.revenue, "USD"
                        )
                    }
                }
                loadGen = adsPlacementEcmp.loadGeneration
                nativeAdLoader?.loadAd()
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "native_collapse", "applovin", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads bnntcl max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads bnntcl max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads bnntcl max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    /**
     * Final review Minor 1: native ad mà lớp này không dùng nữa — hiện chỉ có [keepLateAd] gọi, khi quảng cáo tới muộn
     * sau watchdog slot không được giữ (mục đang có request mới hoặc đã giữ quảng cáo khác). Trước đây lớp NtCl không
     * ghi đè nên quảng cáo đó không được giữ mà cũng không destroy. Destroy như lớp `Nt` cùng mạng.
     */
    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        val loader = nativeAdLoader ?: return false
        if (ad is MaxAd) {
            loader.destroy(ad)
            return true
        }
        return false
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        val ecmpShow = adsPlacementEcmp.adEcmps?.getAdLoaded()
        if (ecmpShow != null) {
            LogUtil.logD("ads bnntcl max$adsType $placement ${adsPlacementEcmp.originPl} show")
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            nativeAdShow = ecmpShow.adObject as? MaxAd
            adsEcmpShow = ecmpShow
            adsEcmpShow?.flagShow = 0
            if (nativeAdShow == null) {
                LogUtil.logD("ads bnntcl max$adsType $placement ${adsPlacementEcmp.originPl} show ad not MaxAd")
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "nativeAd null")
                return false
            }
            showWithView()
            return true
        } else {
            LogUtil.logD("ads bnntcl max$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
    }

    override fun hideAd(flagHide:Int) {
        super.hideAd(flagHide)
        adsEcmpShow?.isLoaded = false
        adsEcmpShow?.adObject = null
        if (nativeAdLoader != null && nativeAdShow != null) {
            nativeAdLoader?.destroy(nativeAdShow)
            nativeAdShow = null
        }
    }
    //--------------------------------------------------
    private fun showWithView() {
        if (nativeAdShow != null) {
            AppPluginManager.instance.registerMyAppLifecycle(this)
            var adView = creatClView()
            addToParentView(adView)
            adsPlacementEcmp.adsShowCb?.onAdShowed(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, 1
            )
        } else {
            LogUtil.logD("ads bnntcl max$adsType $showPl ${adsPlacementEcmp.originPl} show faild ad null")
            hideAd(0)
            adsPlacementEcmp.adsShowCb?.onAdShowFail(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "nativeAd null"
            )
        }
    }

    private fun hideView() {
        AppPluginManager.instance.unRegisterMyAppLifecycle(this)
        AdsHelper.instance.hideNativeCl()
        hideAd(0)
        LogUtil.logD("ads bnntcl max$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
        adsPlacementEcmp.isShowing = false
        adsPlacementEcmp.adsShowCb?.onAdDismissed(
            showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId
        )
    }

    private fun creatClView(): View {
        getInfoView()
        val loview = LayoutInflater.from(mContext).inflate(R.layout.appplugin_max_native_cl_dialog, null, false)
        var nativeAdView: FrameLayout = loview.findViewById(R.id.ntcl_lo)
        populateNativeAdView(loview, nativeAdShow!!, nativeAdView)
        return loview
    }

    private fun createNativeAdView(): MaxNativeAdView {
        val binder =
            MaxNativeAdViewBinder.Builder(R.layout.appplugin_max_native_cl)
                .setTitleTextViewId(R.id.ntcl_headline)
                .setBodyTextViewId(R.id.ntcl_body)
                .setStarRatingContentViewGroupId(R.id.ntcl_star_rating_view)
                .setAdvertiserTextViewId(R.id.ntcl_advertiser)
                .setIconImageViewId(R.id.ntcl_app_icon)
                .setMediaContentViewGroupId(R.id.ntcl_media)
                .setCallToActionButtonId(R.id.ntcl_call_to_action)
                //.setOptionsContentViewGroupId( R.id.options_view )
                .build()
        return MaxNativeAdView(binder, mContext)
    }

    private fun populateNativeAdView(loAdVIew: View, nativeAd: MaxAd, loAdView: FrameLayout) {
        val adView = createNativeAdView()
        nativeAdLoader?.render(adView, nativeAd)
        loAdView.addView(
            adView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loBtClose = loAdVIew.findViewById<RelativeLayout>(R.id.ntlo_close)
        btClose = loBtClose?.findViewById<Button>(R.id.ntcl_close)
        if (AdsFbMy.isShowGrid > 0) {
            btClose?.setBackgroundColor(Color.GREEN)
        }
        btClose?.setOnClickListener(View.OnClickListener { hideView() })
        loBtClose?.visibility = View.VISIBLE
        val lp = btClose?.layoutParams as? RelativeLayout.LayoutParams
        lp?.width = (24 * mDensity).toInt()
        lp?.height = (24 * mDensity).toInt()
        btClose?.layoutParams = lp
    }

    override fun onActivityResume(ac: Activity?) {
    }
}
