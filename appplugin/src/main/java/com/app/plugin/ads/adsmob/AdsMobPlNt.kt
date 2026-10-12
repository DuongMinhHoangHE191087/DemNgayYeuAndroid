package com.app.plugin.ads.adsmob

import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPlBaseNative
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.NativeMediaRatio
import com.app.plugin.ads.ObjectForShowAdWithViewParent
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.app.plugin.ads.GmaLoadContext
import com.app.plugin.ads.consent.AdRequestFactory
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

internal class AdsMobPlNt(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseNative(context, adBase, type, pl) {
    override val eventTag: String get() = "mob_nt"

    private lateinit var admobmy: AdsMobMy

    override fun onInit() {
        admobmy = AdsMobMy.instance
    }

    override fun tryLoad() {
        if (!admobmy.isSdkInitialized) {
            adsPlacementEcmp.isLoading = true
            admobmy.runWhenSdkReady(unlessLoadAbandoned { tryLoad() })
            return
        }
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                if (ecmpLoad.adsId.startsWith("ca-app")) {
                    if (mActivity == null) {
                        LogUtil.logD("ads native admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad activity null")
                        adsPlacementEcmp.isLoading = false
                        adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "activity null")
                        return
                    }
                    LogUtil.logD("ads native admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                    adsPlacementEcmp.isLoading = true
                    adsPlacementEcmp.isHasLoad = true
                    ecmpLoad.isLoading = true
                    ecmpLoad.isLoaded = false
                    val loadGen = adsPlacementEcmp.loadGeneration
                    val adOptionsBuilder: NativeAdOptions.Builder
                    val videoOptions =
                        VideoOptions.Builder().setStartMuted(true).setCustomControlsRequested(true)
                            .build()
                    // 2.4.4 ASPECT: mặc định ANY, `ads_cf_native_media_ratio` lùi được (NativeMediaRatio, Hợp đồng B).
                    val portrait = mActivity?.resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT
                    adOptionsBuilder = NativeAdOptions.Builder().setMediaAspectRatio(NativeMediaRatio.forRequest(mContext, portrait))
                    adOptionsBuilder.setVideoOptions(videoOptions)
                    var loadedNative: NativeAd? = null // T6: click của AdLoader đọc lượt của native này qua turnPl
                    val adLoader = AdLoader.Builder(GmaLoadContext.forLoad(mActivity!!, mContext), ecmpLoad.adsId)
                        .forNativeAd(MainPost.nativeLoaded { nativeAd: NativeAd ->
                            loadedNative = nativeAd
                            // Final review IMP-1: paid listener gán trước guard stale, để quảng cáo tới muộn mà `keepLateAd` giữ (hiện sau
                            // đó) vẫn ghi doanh thu; quảng cáo bị bỏ/destroy thì không bao giờ hiện nên listener của nó không chạy.
                            val adidloaded = ecmpLoad.adsId
                            nativeAd.setOnPaidEventListener(MainPost.paid { adValue ->
                                val netName = AdsMobMy.instance.getAdnetName(nativeAd.responseInfo)
                                PaidEvent.admob(
                                    turnPl(nativeAd, getPl), adsPlacementEcmp.originPl,
                                    "native_custom",
                                    adidloaded,
                                    netName,
                                    adValue
                                )
                            })
                            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return@nativeLoaded }
                            if (mActivity == null || mActivity?.isDestroyed == true || mActivity?.isFinishing == true) {
                                nativeAd.destroy()
                                adsPlacementEcmp.isLoading = false
                                ecmpLoad.isLoading = false
                                ecmpLoad.isLoaded = false
                                ecmpLoad.adObject = null
                                onFinishLoad()
                                adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId, "activity unavailable")
                                return@nativeLoaded
                            }
                            adsPlacementEcmp.isLoading = false
                            adsPlacementEcmp.isloaded = true
                            ecmpLoad.isLoading = false
                            ecmpLoad.isLoaded = true
                            ecmpLoad.adObject = nativeAd
                            AdsHelper.instance.onAdLoaded(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                "native_custom",
                                "admob",
                                ecmpLoad.adsId,
                                nativeAd.responseInfo?.mediationAdapterClassName ?: "source_null"
                            )
                            LogUtil.logD("ads native admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                            adsPlacementEcmp.adsLoadCb?.onLoaded(
                                loadPl,
                                adsPlacementEcmp.originPl,
                                adidloaded,
                                ecmpLoad,
                                this@AdsMobPlNt
                            )
                            onFinishLoad()

                        }).withAdListener(MainPost.adListener(object : AdListener() {
                            override fun onAdLoaded() {
                                //LogUtil.logD("ads native admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok2 id=${ecmpLoad.adsId}")
                            }

                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                if (isStaleLoadCallback(loadGen)) return
                                LogUtil.logD("ads native admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.message}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.adObject = null
                                adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                                AdsHelper.instance.onAdLoadFail(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    "native_custom",
                                    "admob",
                                    ecmpLoad.adsId,
                                    "${adError.code}"
                                )
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

                            override fun onAdOpened() {
                                LogUtil.logD("ads native admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdOpened")
                            }

                            override fun onAdImpression() {
                                LogUtil.logD("ads native admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression id=${ecmpLoad.adsId}")
                                adsPlacementEcmp.adsShowCb?.onAdImpression(
                                    turnPl(loadedNative, getPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                            }

                            override fun onAdSwipeGestureClicked() {
                                LogUtil.logD("ads native admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdSwipeGestureClicked")
                            }

                            override fun onAdClicked() {
                                LogUtil.logD("ads native admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdClicked")
                                adsPlacementEcmp.adsShowCb?.onAdClick(
                                    turnPl(loadedNative, getPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                                AdsHelper.instance.onclickAd()
                            }

                            override fun onAdClosed() {
                                LogUtil.logD("ads native admob$adsType $getPl ${adsPlacementEcmp.originPl} onAdClosed")
                            }
                        })).withNativeAdOptions(adOptionsBuilder.build()).build()
                    adLoader.loadAd(AdRequestFactory.build(mContext))
                    AdsHelper.instance.onAdLoadStart(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        "native_custom",
                        "admob",
                        ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads native admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad idload not correct")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "idload not correct"
                    )
                }
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads native admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        ecmpLoad.adsId,
                        ecmpLoad,
                        this@AdsMobPlNt
                    )
                } else {
                    LogUtil.logD("ads native admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads native admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("ads native admob$adsType $placement ${adsPlacementEcmp.originPl} show not use")
        return false
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdWithinMaxAge()   // 2.4.3 NATAGE: quá fullscreen_max_age_minutes ⇒ bỏ + ad_expired
        if (ecmpShow != null && ecmpShow.adObject != null) {
            LogUtil.logD("ads native admob$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow")
            setGetPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = false
            ecmpShow.isLoaded = false
            var ad = ecmpShow.adObject
            if (ad == null) {
                LogEventServer.logAdShowFailWithReason(
                    placement = placement,
                    reason = Constants.AdShowFailReason.AD_OBJECT_NULL
                )
                // BUG-A3 FIX: return null ngay để tránh NPE khi force-cast null as NativeAd
                return null
            }
            ecmpShow.adObject = null

            var obRe = ObjectForShowAdWithViewParent()
            obRe.viewCurrAttach = populateNativeAdView(ad as NativeAd)
            if (obRe.viewCurrAttach == null) {
                LogEventServer.logAdShowFailWithReason(
                    placement = placement,
                    reason = Constants.AdShowFailReason.VIEW_ATTACH_NULL
                )
            }
            obRe.adsEcmp = ecmpShow
            obRe.adLoad = ad
            obRe.type4Time = adsType
            obRe.placement = adsPlacementEcmp.originPl
            return obRe
        } else {
            LogUtil.logD("ads native admob$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not loaded")
            LogEventServer.logAdShowFailWithReason(
                placement = placement,
                reason = Constants.AdShowFailReason.SHOW_NOT_LOADED
            )
            return null
        }
    }

    override fun populateNativeAdView(ecmp: AdsEcmp): View? {
        if (ecmp.adObject == null) {
            return null
        } else {
            if (ecmp.adObject is NativeAd) {
                return populateNativeAdView(ecmp.adObject as NativeAd)
            } else {
                return null
            }
        }
    }

    override fun destroyAd(ad: Any?): Boolean {
        super.destroyAd(ad)
        if (ad != null) {
            if (ad is NativeAd) {
                var adNative: NativeAd = ad
                adNative.destroy()
                return true
            }
        }
        return false
    }

    private fun populateNativeAdView(nativeAd: NativeAd): View? {
        val layout = AdsHelper.instance.getLayoutCustome4Native(if (adsPlacementEcmp.originPl == "nt_full_intro") -1001 else AdsIDDef.AdmobNative)
        val loview = LayoutInflater.from(mContext).inflate(layout, null, false)
        var adView: NativeAdView = loview.findViewById(R.id.ad_native_view_lo)
        // Set the media view. 2.5.0 PLMM: layout app thiếu/khác kiểu `ad_media` ⇒ không gắn media (không crash), logE một lần.
        val media = adView.findViewById<View>(R.id.ad_media) as? MediaView
        if (media == null) NativeMediaRatio.warnNoMediaView(mContext, layout)
        adView.mediaView = media
        // Set other ad assets.
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.priceView = adView.findViewById(R.id.ad_price)
//        adView.starRatingView = adView.findViewById(R.id.ntcl_stars)
//        adView.storeView = adView.findViewById(R.id.ntcl_store)
//        adView.advertiserView = adView.findViewById(R.id.ntcl_advertiser)

        var custome = AdsHelper.instance.getNativeCustom(getPl)
        if (custome != null) {
            adView.setBackgroundColor(custome.bgColor)
        }

        (adView.headlineView as TextView?)?.text = nativeAd.headline
        adView.mediaView?.mediaContent = nativeAd.mediaContent

        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView?)?.text = nativeAd.headline
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView?)?.text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button?)?.text = nativeAd.callToAction
            if (custome != null) {
                adView.callToActionView?.setBackgroundColor(custome.actionColor)
            }
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as ImageView?)?.setImageDrawable(nativeAd.icon!!.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        if (nativeAd.price == null) {
            adView.priceView?.visibility = View.INVISIBLE
        } else {
            adView.priceView?.visibility = View.VISIBLE
            (adView.priceView as TextView?)?.text = nativeAd.price
        }

//        if (nativeAd.store == null) {
//            adView.storeView?.visibility = View.INVISIBLE
//        } else {
//            adView.storeView?.visibility = View.VISIBLE
//            (adView.storeView as TextView?)?.text = nativeAd.store
//        }

//        if (nativeAd.starRating == null) {
//            adView.starRatingView?.visibility = View.INVISIBLE
//        } else {
//            (adView.starRatingView as RatingBar?)?.rating = nativeAd.starRating!!.toFloat()
//            adView.starRatingView?.visibility = View.VISIBLE
//        }

//        if (nativeAd.advertiser == null) {
//            adView.advertiserView?.visibility = View.INVISIBLE
//        } else {
//            (adView.advertiserView as TextView?)!!.text = nativeAd.advertiser
//            adView.advertiserView?.visibility = View.VISIBLE
//        }

        adView.setNativeAd(nativeAd)
        return loview
    }
}
