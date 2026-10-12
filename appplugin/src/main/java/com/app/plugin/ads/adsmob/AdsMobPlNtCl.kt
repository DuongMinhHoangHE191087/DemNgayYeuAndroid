package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.RelativeLayout
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsPlNtClBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.AdsUtil
import com.app.plugin.ads.AppResumeIF
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.NativeMediaRatio
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.app.plugin.ads.GmaLoadContext
import com.app.plugin.ads.consent.AdRequestFactory
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.Random

internal class AdsMobPlNtCl(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlNtClBase(context, adBase, type, pl), AppResumeIF {
    override val eventTag: String get() = "mob_nt_cl"

    private val timeShowBtClose = 1
    var adsEcmpShow: AdsEcmp? = null
    var nativeAdShow: NativeAd? = null

    private lateinit var admobmy: AdsMobMy

    private var mNativeAdView: NativeAdView? = null
    private var vcControlCurrShow: VideoController? = null

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
                        LogUtil.logD("ads bnntcl admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad activity null")
                        adsPlacementEcmp.isLoading = false
                        adsPlacementEcmp.adsLoadCb?.onLoadFailed(loadPl, adsPlacementEcmp.originPl, "", "activity null")
                        return
                    }
                    LogUtil.logD("ads bnntcl admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                    adsPlacementEcmp.isLoading = true
                    adsPlacementEcmp.isHasLoad = true
                    ecmpLoad.isLoading = true
                    ecmpLoad.isLoaded = false
                    val loadGen = adsPlacementEcmp.loadGeneration
                    val adOptionsBuilder: NativeAdOptions.Builder
                    val videoOptions =
                        VideoOptions.Builder().setStartMuted(false).setCustomControlsRequested(true)
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
                                    turnPl(nativeAd, showPl), adsPlacementEcmp.originPl,
                                    "native_collapse",
                                    adidloaded,
                                    netName,
                                    adValue
                                )
                            })
                            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return@nativeLoaded }
                            adsPlacementEcmp.isLoading = false
                            adsPlacementEcmp.isloaded = true
                            ecmpLoad.isLoading = false
                            ecmpLoad.isLoaded = true
                            AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "native_collapse", "admob", ecmpLoad.adsId, nativeAd.responseInfo?.mediationAdapterClassName ?: "source_null")
                            if (mActivity != null && mActivity!!.isDestroyed) {
                                nativeAd.destroy()
                            } else {
                                LogUtil.logD("ads bnntcl admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                ecmpLoad.adObject = nativeAd
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, adidloaded
                                )
                                onFinishLoad()
                            }
                        }).withAdListener(MainPost.adListener(object : AdListener() {
                            override fun onAdLoaded() {
                                //LogUtil.logD("ads bnntcl admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok2 id=${ecmpLoad.adsId}")
                            }

                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                if (isStaleLoadCallback(loadGen)) return
                                LogUtil.logD("ads bnntcl admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError.code}")
                                ecmpLoad.isLoading = false
                                ecmpLoad.adObject = null
                                adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                                AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "native_collapse", "admob", ecmpLoad.adsId, "${adError.code}")
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
                                LogUtil.logD("ads bnntcl admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdOpened")
                            }

                            override fun onAdImpression() {
                                LogUtil.logD("ads bnntcl admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdImpression id=${ecmpLoad.adsId}")
                                adsPlacementEcmp.adsShowCb?.onAdImpression(
                                    turnPl(loadedNative, showPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                            }

                            override fun onAdSwipeGestureClicked() {
                                LogUtil.logD("ads bnntcl admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdSwipeGestureClicked")
                            }

                            override fun onAdClicked() {
                                LogUtil.logD("ads bnntcl admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdClicked")
                                adsPlacementEcmp.adsShowCb?.onAdClick(
                                    turnPl(loadedNative, showPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                                )
                                AdsHelper.instance.onclickAd()
                            }

                            override fun onAdClosed() {
                                LogUtil.logD("ads bnntcl admob$adsType $showPl ${adsPlacementEcmp.originPl} onAdClosed")
                            }
                        })).withNativeAdOptions(adOptionsBuilder.build()).build()
                    adLoader.loadAd(AdRequestFactory.build(mContext))
                    AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "native_collapse", "admob", ecmpLoad.adsId)
                } else {
                    LogUtil.logD("ads bnntcl admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad idload not correct")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "idload not correct"
                    )
                }
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads bnntcl admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads bnntcl admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads bnntcl admob$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
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
        if (ad is NativeAd) {
            ad.destroy()
            return true
        }
        return false
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        val ecmpShow = adsPlacementEcmp.adEcmps?.getAdLoaded()
        if (ecmpShow != null) {
            LogUtil.logD("ads bnntcl admob$adsType $placement ${adsPlacementEcmp.originPl} show")
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            ecmpShow.isLoaded = false
            adsPlacementEcmp.adsShowCb = adsShowCb
            nativeAdShow = ecmpShow.adObject as? NativeAd
            ecmpShow.adObject = null
            adsEcmpShow = ecmpShow
            adsEcmpShow?.flagShow = 0
            if (nativeAdShow == null) {
                LogUtil.logD("ads bnntcl admob$adsType $placement ${adsPlacementEcmp.originPl} show ad not NativeAd")
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(placement, adsPlacementEcmp.originPl, "", "nativeAd null")
                return false
            }
            showWithView()
            return true
        } else {
            LogUtil.logD("ads bnntcl admob$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            adsShowCb?.onAdShowFail(loadPl,adsPlacementEcmp.originPl, "", "ecmpLoad null")
            return false
        }
    }

    override fun hideAd(flagHide: Int) {
        super.hideAd(flagHide)
        adsEcmpShow?.isLoaded = false
        adsEcmpShow?.adObject = null
        if (nativeAdShow != null) {
            nativeAdShow?.destroy()
            nativeAdShow = null
        }
    }

    //------------------------------
    private fun showWithView() {
        if (nativeAdShow != null) {
            AppPluginManager.instance.registerMyAppLifecycle(this)
            var adView = creatClView()
            addToParentView(adView)
            adsPlacementEcmp.adsShowCb?.onAdShowed(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, 1
            )
        } else {
            LogUtil.logD("ads bnntcl admob$adsType $showPl ${adsPlacementEcmp.originPl} show faild ad null")
            hideAd(0)
            adsPlacementEcmp.adsShowCb?.onAdShowFail(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "nativeAd null"
            )
        }
    }

    private fun hideView() {
        val ad = nativeAdShow // C1b: quảng cáo của lượt đang đóng, đọc trước khi hideNativeCl/hideAd bỏ nó
        AppPluginManager.instance.unRegisterMyAppLifecycle(this)
        AdsHelper.instance.hideNativeCl()
        hideAd(0)
        LogUtil.logD("ads bnntcl admob$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
        adsEcmpShow?.isLoaded = false
        adsEcmpShow?.adObject = null
        adsPlacementEcmp.isShowing = false
        adsPlacementEcmp.adsShowCb?.onAdDismissed(
            turnPl(ad, showPl), adsPlacementEcmp.originPl, adsEcmpShow?.adsId
        )
    }

    // Inflated detached on purpose: the view is returned and the caller adds it to whichever
    // container shows the ad, which does not exist yet at this point.
    @android.annotation.SuppressLint("InflateParams")
    private fun creatClView(): View {
        getInfoView()
        val loview = LayoutInflater.from(mContext).inflate(R.layout.appplugin_admob_native_cl, null, false)
        mNativeAdView = loview.findViewById(R.id.ntcl_ntadview)
        populateNativeAdView(loview, nativeAdShow!!, mNativeAdView!!)
        return loview
    }

    private fun populateNativeAdView(loAdVIew: View, nativeAd: NativeAd, adView: NativeAdView) {
        // Set the media view.
        adView.mediaView = adView.findViewById<View>(R.id.ntcl_media) as MediaView
        // Set other ad assets.
        adView.headlineView = adView.findViewById(R.id.ntcl_headline)
        adView.bodyView = adView.findViewById(R.id.ntcl_body)
        adView.callToActionView = adView.findViewById(R.id.ntcl_call_to_action)
        adView.iconView = adView.findViewById(R.id.ntcl_app_icon)
        adView.priceView = adView.findViewById(R.id.ntcl_price)
        adView.starRatingView = adView.findViewById(R.id.ntcl_stars)
        adView.storeView = adView.findViewById(R.id.ntcl_store)
        adView.advertiserView = adView.findViewById(R.id.ntcl_advertiser)

        // The headline and mediaContent are guaranteed to be in every NativeAd.
        (adView.headlineView as TextView?)!!.text = nativeAd.headline
        adView.mediaView!!.mediaContent = nativeAd.mediaContent

        // These assets aren't guaranteed to be in every NativeAd, so it's important to
        // check before trying to display them.

        // These assets aren't guaranteed to be in every NativeAd, so it's important to
        // check before trying to display them.
        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView?)!!.text = nativeAd.headline
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView?)!!.text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView!!.visibility = View.INVISIBLE
        } else {
            adView.callToActionView!!.visibility = View.VISIBLE
            (adView.callToActionView as Button?)!!.text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView!!.visibility = View.GONE
        } else {
            (adView.iconView as ImageView?)!!.setImageDrawable(nativeAd.icon!!.drawable)
            adView.iconView!!.visibility = View.VISIBLE
        }

        if (nativeAd.price == null) {
            adView.priceView!!.visibility = View.INVISIBLE
        } else {
            adView.priceView!!.visibility = View.VISIBLE
            (adView.priceView as TextView?)!!.text = nativeAd.price
        }

        if (nativeAd.store == null) {
            adView.storeView!!.visibility = View.INVISIBLE
        } else {
            adView.storeView!!.visibility = View.VISIBLE
            (adView.storeView as TextView?)!!.text = nativeAd.store
        }

        if (nativeAd.starRating == null) {
            adView.starRatingView!!.visibility = View.INVISIBLE
        } else {
            (adView.starRatingView as RatingBar?)!!.rating = nativeAd.starRating!!.toFloat()
            adView.starRatingView!!.visibility = View.VISIBLE
        }

        if (nativeAd.advertiser == null) {
            adView.advertiserView!!.visibility = View.INVISIBLE
        } else {
            (adView.advertiserView as TextView?)!!.text = nativeAd.advertiser
            adView.advertiserView!!.visibility = View.VISIBLE
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad.

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad.
        adView.setNativeAd(nativeAd)

        // Get the video controller for the ad. One will always be provided, even if the ad doesn't
        // have a video asset.

        // Get the video controller for the ad. One will always be provided, even if the ad doesn't
        // have a video asset.
        val mediaContent = nativeAd.mediaContent

        // Updates the UI to say whether or not this ad has a video asset.

        // Updates the UI to say whether or not this ad has a video asset.
        if (vcControlCurrShow != null) {
            vcControlCurrShow!!.stop()
        }
        if (mediaContent != null) {
            val videoController = mediaContent.videoController
            if (mediaContent.hasVideoContent()) {
                vcControlCurrShow = videoController
                videoController.videoLifecycleCallbacks =
                    MainPost.video(object : VideoController.VideoLifecycleCallbacks() {
                        override fun onVideoEnd() {
                            // Publishers should allow native ads to complete video playback before.
                            super.onVideoEnd()
                        }
                    })
            } else {
            }
        } else {
            vcControlCurrShow = null
        }
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
