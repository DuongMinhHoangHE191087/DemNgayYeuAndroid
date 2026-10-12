package com.app.plugin.ads.adsfb

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdOptionsView
import com.facebook.ads.MediaView
import com.facebook.ads.NativeAd
import com.facebook.ads.NativeAdBase
import com.facebook.ads.NativeAdLayout
import com.facebook.ads.NativeAdListener
import java.util.Random

internal class AdsFbPlNtCl(context: Context, adBase: AdsBase, type: Int, pl: String) : AdsPlNtClBase(context, adBase, type, pl),
    AppResumeIF {
    override val eventTag: String get() = "fb_nt_cl"

    private val timeShowBtClose = 1
    var adsEcmpShow: AdsEcmp? = null
    var nativeAdShow: NativeAd? = null

    private lateinit var adfbmy: AdsFbMy

    private var mNativeAdLayout: NativeAdLayout? = null
    private var adOptionsView: AdOptionsView? = null
    private var adChoicesContainer: LinearLayout? = null

    override fun onInit() {
        adfbmy = AdsFbMy.instance
    }

    override fun tryLoad() {
        if (holdUntilReady(AdsFbMy.instance.readyQueue)) return
        loadPl = adsPlacementEcmp.getPl
        val ecmpLoad = adsPlacementEcmp.adEcmps?.getEcmpForLoad()
        if (ecmpLoad != null) {
            if (!ecmpLoad.isLoaded && !ecmpLoad.isLoading) {
                LogUtil.logD("ads bnntcl fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false
                val loadGen = adsPlacementEcmp.loadGeneration

                var idLoad = ecmpLoad.adsId
                if (AdsFbMy.instance.flagPp?.compareTo("90351") == 0) {
                    idLoad = "VID_HD_9_16_39S_APP_INSTALL#$idLoad"
                }
                var nativeAd = com.facebook.ads.NativeAd(mActivity, idLoad)
                nativeAd.loadAd(
                    nativeAd.buildLoadAdConfig().withAdListener(object : NativeAdListener {
                        override fun onAdLoaded(p0: Ad?) {
                            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return }
                            adsPlacementEcmp.isLoading = false
                            adsPlacementEcmp.isloaded = true
                            ecmpLoad.isLoading = false
                            ecmpLoad.isLoaded = true
                            AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "native_collapse", "facebook", ecmpLoad.adsId, "facebook")
                            if (mActivity != null && mActivity!!.isDestroyed) {
                                nativeAd.destroy()
                            } else {
                                LogUtil.logD("ads bnntcl fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                ecmpLoad.adObject = nativeAd
                                val adidloaded = ecmpLoad.adsId
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl, adsPlacementEcmp.originPl, adidloaded
                                )
                                onFinishLoad()
                            }
                        }

                        override fun onError(ad: Ad?, adError: AdError?) {
                            if (isStaleLoadCallback(loadGen)) return
                            LogUtil.logD("ads bnntcl fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError?.errorCode}")
                            ecmpLoad.isLoading = false
                            ecmpLoad.adObject = null
                            adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                            AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "native_collapse", "facebook", ecmpLoad.adsId, "${adError?.errorCode}")
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
                        }

                        override fun onAdClicked(p0: Ad?) {
                            LogUtil.logD("ads bnntcl fb$adsType $showPl ${adsPlacementEcmp.originPl} onAdClicked")
                            adsPlacementEcmp.adsShowCb?.onAdClick(
                                turnPl(nativeAd, showPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                            )
                            AdsHelper.instance.onclickAd()
                        }

                        override fun onLoggingImpression(p0: Ad?) {
                            LogUtil.logD("ads bnntcl fb$adsType $showPl ${adsPlacementEcmp.originPl} onAdImpression")
                            adsPlacementEcmp.adsShowCb?.onAdImpression(
                                turnPl(nativeAd, showPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                            )
                            PaidEvent.metaDirect(turnPl(nativeAd, showPl), adsPlacementEcmp.originPl, "native_collapse", idLoad, "facebook")
                        }

                        override fun onMediaDownloaded(p0: Ad?) {
                        }

                    }).build()
                )
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "native_collapse", "facebook", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads bnntcl fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl, adsPlacementEcmp.originPl, ecmpLoad.adsId
                    )
                } else {
                    LogUtil.logD("ads bnntcl fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads bnntcl fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
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
            LogUtil.logD("ads bnntcl fb$adsType $placement ${adsPlacementEcmp.originPl} show")
            setShowPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = true
            adsPlacementEcmp.adsShowCb = adsShowCb
            nativeAdShow = ecmpShow.adObject as? NativeAd
            adsEcmpShow = ecmpShow
            adsEcmpShow?.flagShow = 0
            if (nativeAdShow == null) {
                LogUtil.logD("ads bnntcl fb$adsType $placement ${adsPlacementEcmp.originPl} show ad not NativeAd")
                hideAd(0)
                adsPlacementEcmp.adsShowCb?.onAdShowFail(
                    showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "nativeAd null"
                )
                return false
            }
            showWithView()
            return true
        } else {
            LogUtil.logD("ads bnntcl fb$adsType $placement ${adsPlacementEcmp.originPl} show not loaded")
            return false
        }
    }

    override fun hideAd(flagHide:Int) {
        super.hideAd(flagHide)
        adsEcmpShow?.isLoaded = false
        adsEcmpShow?.adObject = null
        if (nativeAdShow != null) {
            nativeAdShow?.destroy()
            nativeAdShow = null
        }
    }
    //----------------------------------------
    private fun showWithView() {
        if (nativeAdShow != null) {
            AppPluginManager.instance.registerMyAppLifecycle(this)
            var adView = creatClView()
            addToParentView(adView)
            adsPlacementEcmp.adsShowCb?.onAdShowed(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, 1
            )
        } else {
            LogUtil.logD("ads bnntcl fb$adsType $showPl ${adsPlacementEcmp.originPl} show faild ad null")
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
        LogUtil.logD("ads bnntcl fb$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
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
        val loview = LayoutInflater.from(mContext).inflate(R.layout.appplugin_fb_native_cl, null, false)
        mNativeAdLayout = loview.findViewById(R.id.fb_ntcl_ad_view)
        adChoicesContainer = loview.findViewById(R.id.fb_ntcl_ad_choices_container)
        populateNativeAdView(loview, nativeAdShow!!, mNativeAdLayout!!)
        return loview
    }

    private fun populateNativeAdView(loAdVIew: View, nativeAd: NativeAd, adView: NativeAdLayout) {
        nativeAd.unregisterView()
        if (adChoicesContainer != null) {
            adOptionsView = AdOptionsView(mContext, nativeAd, adView)
            adChoicesContainer?.removeAllViews()
            adChoicesContainer?.addView(adOptionsView, 0)
        }
        val nativeAdIcon = adView.findViewById<MediaView>(R.id.fb_ntcl_ad_icon)
        val nativeAdTitle = adView.findViewById<TextView>(R.id.fb_ntcl_ad_title)
        val nativeAdBody = adView.findViewById<TextView>(R.id.fb_ntcl_ad_body)
        val sponsoredLabel = adView.findViewById<TextView>(R.id.fb_ntcl_ad_sponsored_label)
        val nativeAdSocialContext = adView.findViewById<TextView>(R.id.fb_ntcl_ad_social_context)
        val nativeAdCallToAction = adView.findViewById<Button>(R.id.fb_ntcl_ad_call_to_action)
        val nativeAdMedia = adView.findViewById<MediaView>(R.id.fb_ntcl_ad_media)

        // Setting the Text
        nativeAdSocialContext.text = nativeAd.adSocialContext
        if (nativeAd.hasCallToAction()) {
            nativeAdCallToAction.visibility = View.VISIBLE
            nativeAdCallToAction.text = nativeAd.adCallToAction
        } else {
            nativeAdCallToAction.visibility = View.GONE
        }
        nativeAdTitle.text = nativeAd.advertiserName
        nativeAdBody.text = nativeAd.adBodyText
        sponsoredLabel.setText(R.string.appplugin_ad_sponsored)

        // You can use the following to specify the clickable areas.
        val clickableViews: MutableList<View> = ArrayList()
        clickableViews.add(nativeAdIcon)
        clickableViews.add(nativeAdMedia!!)
        clickableViews.add(nativeAdCallToAction)
        nativeAd.registerViewForInteraction(
            adView,
            nativeAdMedia,
            nativeAdIcon,
            clickableViews
        )

        // Optional: tag views
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdIcon,
            NativeAdBase.NativeComponentTag.AD_ICON
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdTitle,
            NativeAdBase.NativeComponentTag.AD_TITLE
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdBody,
            NativeAdBase.NativeComponentTag.AD_BODY
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdSocialContext,
            NativeAdBase.NativeComponentTag.AD_SOCIAL_CONTEXT
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdCallToAction,
            NativeAdBase.NativeComponentTag.AD_CALL_TO_ACTION
        )

        loBtClose = loAdVIew.findViewById<RelativeLayout>(R.id.fb_ntlo_close)
        btClose = loBtClose?.findViewById<Button>(R.id.fb_ntcl_close)
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
