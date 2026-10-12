package com.app.plugin.ads.adsmax

import android.content.Context
import android.view.View
import com.app.plugin.R
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPlBaseNative
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import java.util.concurrent.atomic.AtomicBoolean

internal class AdsMaxPlNt(context: Context, adBase: AdsBase, type: Int, pl: String) : AdsPlBaseNative(context, adBase, type, pl) {
    override val eventTag: String get() = "max_nt"

    private lateinit var admaxmy: AdsMaxMy
    private var nativeAdLoader: MaxNativeAdLoader? = null

    /**
     * Loader theo unit id của placement này: đổi mục thì dùng lại loader của unit đó, không dựng/destroy lại. Số loader
     * không vượt số id của danh sách (danh sách MAX chỉ đổi lúc dựng); trước Task 14 loader cũng không bao giờ bị destroy.
     */
    private val loaderByUnit = HashMap<String, MaxNativeAdLoader>()

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
                LogUtil.logD("ads native max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
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
                    val lateTag = "ads native max$adsType ${adsPlacementEcmp.originPl}"
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
                            AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "native_custom", "applovin", ecmpLoad.adsId, nativeAd.networkName ?: "source_null")
                            if (mActivity != null && mActivity!!.isDestroyed) {
                                nativeAdLoader?.destroy(nativeAd)
                            } else {
                                LogUtil.logD("ads native max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                ecmpLoad.adObject = nativeAd
                                val adidloaded = ecmpLoad.adsId
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, adidloaded, ecmpLoad, this@AdsMaxPlNt
                                )
                                onFinishLoad()
                            }
                        }

                        override fun onNativeAdLoadFailed(adUnitId: String, adError: MaxError) {
                            if (!live.get()) { LogUtil.logD("$lateTag late load callback of replaced loader dropped"); return }
                            if (isStaleLoadCallback(loadGen)) return
                            LogUtil.logD("ads native max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.message}")
                            ecmpLoad.isLoading = false
                            ecmpLoad.adObject = null
                            adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                            AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "native_custom", "applovin", ecmpLoad.adsId, "${adError.code}")
                            if (!adsPlacementEcmp.flag4Load && adsPlacementEcmp.adEcmps?.idxCurrEcpm ?: 0 < adsPlacementEcmp.adEcmps?.list?.size ?: 0) {
                                tryLoad()
                            } else {
                                if (!adsPlacementEcmp.flag4Load) {
                                    adsPlacementEcmp.isLoading = false
                                    adsPlacementEcmp.isloaded = false
                                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                                        loadPl,
                                        adsPlacementEcmp.originPl,
                                        adUnitId,
                                        "" + adError.code + " " + adError.message
                                    )
                                }
                                onFinishLoad()
                            }
                        }

                        override fun onNativeAdClicked(nativeAd: MaxAd) {
                            LogUtil.logD("ads native max$adsType $getPl ${adsPlacementEcmp.originPl} onAdClicked")
                            adsPlacementEcmp.adsShowCb?.onAdClick(
                                getPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                            )
                            AdsHelper.instance.onclickAd()
                        }
                    })
                    nativeAdLoader?.setRevenueListener {
                        PaidEvent.max(
                            getPl, adsPlacementEcmp.originPl, "native_custom", it.adUnitId, it.networkName, it.revenue, "USD"
                        )
                    }
                }
                loadGen = adsPlacementEcmp.loadGeneration
                nativeAdLoader?.loadAd()
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "native_custom", "applovin", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads native max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, ecmpLoad, this@AdsMaxPlNt
                    )
                } else {
                    LogUtil.logD("ads native max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads native max$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("ads native max$adsType $placement ${adsPlacementEcmp.originPl} show not use")
        return false
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdWithinMaxAge()   // 2.4.3 NATAGE: quá fullscreen_max_age_minutes ⇒ bỏ + ad_expired
        if (ecmpShow != null && ecmpShow.adObject != null) {
            LogUtil.logD("ads native max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow")
            setGetPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = false
            ecmpShow.isLoaded = false
            var ad = ecmpShow.adObject
            ecmpShow.adObject = null

            val maxAdCast = ad as? MaxAd
            if (maxAdCast == null) {
                LogUtil.logD("ads native max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow ad not MaxAd")
                return null
            }
            var obRe = ObjectForShowAdWithViewParent()
            obRe.viewCurrAttach = populateNativeAdView(maxAdCast)
            obRe.adsEcmp = ecmpShow
            obRe.adLoad = ad
            obRe.type4Time = adsType
            obRe.placement = adsPlacementEcmp.originPl
            return obRe
        } else {
            LogUtil.logD("ads native max$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not loaded")
            return null
        }
    }

    override fun populateNativeAdView(ecmp: AdsEcmp): View? {
        if (ecmp.adObject == null) {
            return null
        } else {
            if (ecmp.adObject is MaxAd) {
                return populateNativeAdView(ecmp.adObject as MaxAd)
            } else {
                return null
            }
        }
    }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        if (ad != null && nativeAdLoader != null) {
            if (ad is MaxAd) {
                var adNative: MaxAd = ad
                nativeAdLoader?.destroy(adNative)
                return true
            }
        }
        return false
    }

    private fun populateNativeAdView(nativeAd: MaxAd) : View {
        val adView = createNativeAdView()
        nativeAdLoader?.render(adView, nativeAd)
        var custome = AdsHelper.instance.getNativeCustom(getPl)
        if (custome != null) {
            adView.setBackgroundColor(custome.bgColor)
            adView.callToActionButton?.setBackgroundColor(custome.actionColor)
        }
        return adView
    }

    private fun createNativeAdView(): MaxNativeAdView {
        val binder =
            MaxNativeAdViewBinder.Builder(AdsHelper.instance.getLayoutCustome4Native(AdsIDDef.MaxNative))
                .setTitleTextViewId(R.id.ad_headline)
                .setBodyTextViewId(R.id.ad_body)
                .setStarRatingContentViewGroupId(R.id.ad_star_rating_view)
                .setAdvertiserTextViewId(R.id.ad_advertiser)
                .setIconImageViewId(R.id.ad_app_icon)
                .setMediaContentViewGroupId(R.id.ad_media)
                .setCallToActionButtonId(R.id.ad_call_to_action)
                //.setOptionsContentViewGroupId( R.id.options_view )
                .build()
        return MaxNativeAdView(binder, mContext)
    }
}