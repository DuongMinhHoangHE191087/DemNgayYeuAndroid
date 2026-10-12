package com.app.plugin.ads.adsfb

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdOptionsView
import com.facebook.ads.MediaView
import com.facebook.ads.NativeAd
import com.facebook.ads.NativeAdBase
import com.facebook.ads.NativeAdLayout
import com.facebook.ads.NativeAdListener

internal class AdsFbPlNt(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBaseNative(context, adBase, type, pl) {
    override val eventTag: String get() = "fb_nt"

    private lateinit var adfbmy: AdsFbMy
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
                LogUtil.logD("ads native fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad id=${ecmpLoad.adsId}")
                adsPlacementEcmp.isLoading = true
                adsPlacementEcmp.isHasLoad = true
                ecmpLoad.isLoading = true
                ecmpLoad.isLoaded = false
                val loadGen = adsPlacementEcmp.loadGeneration
                var idLoad = ecmpLoad.adsId
                if (AdsFbMy.instance.flagPp?.compareTo("90351") == 0) {
                    idLoad = "VID_HD_9_16_39S_APP_INSTALL#$idLoad"
                }
                var nativeAd = NativeAd(mActivity, idLoad)
                nativeAd.loadAd(
                    nativeAd.buildLoadAdConfig().withAdListener(object : NativeAdListener {
                        override fun onAdLoaded(p0: Ad?) {
                            if (isStaleLoadCallback(loadGen)) { keepLateAd(ecmpLoad, nativeAd); return }
                            adsPlacementEcmp.isLoading = false
                            adsPlacementEcmp.isloaded = true
                            ecmpLoad.isLoading = false
                            ecmpLoad.isLoaded = true
                            AdsHelper.instance.onAdLoaded(loadPl, adsPlacementEcmp.originPl, "native_custom", "facebook", ecmpLoad.adsId, "facebook")
                            if (mActivity != null && mActivity!!.isDestroyed) {
                                nativeAd.destroy()
                            } else {
                                LogUtil.logD("ads native fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ok id=${ecmpLoad.adsId}")
                                ecmpLoad.adObject = nativeAd
                                val adidloaded = ecmpLoad.adsId
                                adsPlacementEcmp.adsLoadCb?.onLoaded(
                                    loadPl,
                                    adsPlacementEcmp.originPl,
                                    adidloaded,
                                    ecmpLoad,
                                    this@AdsFbPlNt
                                )
                                onFinishLoad()
                            }
                        }

                        override fun onError(ad: Ad?, adError: AdError?) {
                            if (isStaleLoadCallback(loadGen)) return
                            LogUtil.logD("ads native fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad fail id=${ecmpLoad.adsId} err=${adError?.errorCode}")
                            ecmpLoad.isLoading = false
                            ecmpLoad.adObject = null
                            adsPlacementEcmp.adEcmps?.let { it.idxCurrEcpm++ }
                            AdsHelper.instance.onAdLoadFail(loadPl, adsPlacementEcmp.originPl, "native_custom", "facebook", ecmpLoad.adsId, "${adError?.errorCode}")
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
                            LogUtil.logD("ads native fb$adsType $getPl ${adsPlacementEcmp.originPl} onAdClicked")
                            adsPlacementEcmp.adsShowCb?.onAdClick(
                                turnPl(nativeAd, getPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                            )
                            AdsHelper.instance.onclickAd()
                        }

                        override fun onLoggingImpression(p0: Ad?) {
                            LogUtil.logD("ads native fb$adsType $getPl ${adsPlacementEcmp.originPl} onAdImpression")
                            adsPlacementEcmp.adsShowCb?.onAdImpression(
                                turnPl(nativeAd, getPl), adsPlacementEcmp.originPl, ecmpLoad.adsId
                            )
                            PaidEvent.metaDirect(turnPl(nativeAd, getPl), adsPlacementEcmp.originPl, "native_custom", idLoad, "facebook")
                        }

                        override fun onMediaDownloaded(p0: Ad?) {
                        }

                    }).build()
                )
                AdsHelper.instance.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "native_custom", "facebook", ecmpLoad.adsId)
            } else {
                if (ecmpLoad.isLoaded) {
                    LogUtil.logD("ads native fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad has loaded")
                    adsPlacementEcmp.adsLoadCb?.onLoaded(
                        loadPl,
                        adsPlacementEcmp.originPl,
                        ecmpLoad.adsId,
                        ecmpLoad,
                        this@AdsFbPlNt
                    )
                } else {
                    LogUtil.logD("ads native fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad is loading")
                    adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                        loadPl, adsPlacementEcmp.originPl, "", "isloading"
                    )
                }
            }
        } else {
            LogUtil.logD("ads native fb$adsType $loadPl ${adsPlacementEcmp.originPl} tryLoad ecmpLoad null")
            adsPlacementEcmp.adsLoadCb?.onLoadFailed(
                loadPl, adsPlacementEcmp.originPl, "", "ecmpLoad null"
            )
        }
    }

    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
        LogUtil.logD("ads native fb$adsType $placement ${adsPlacementEcmp.originPl} show not use")
        return false
    }

    override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? {
        val ecmpShow = loadedAdWithinMaxAge()   // 2.4.3 NATAGE: quá fullscreen_max_age_minutes ⇒ bỏ + ad_expired
        if (ecmpShow != null) {
            LogUtil.logD("ads native fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow")
            setGetPlacement(placement)
            adsPlacementEcmp.isloaded = false
            adsPlacementEcmp.isShowing = false
            ecmpShow.isLoaded = false
            var ad = ecmpShow.adObject
            ecmpShow.adObject = null

            val nativeAdCast = ad as? NativeAd
            if (nativeAdCast == null) {
                LogUtil.logD("ads native fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow ad not NativeAd")
                return null
            }
            var obRe = ObjectForShowAdWithViewParent()
            obRe.viewCurrAttach = populateNativeAdView(nativeAdCast)
            obRe.adsEcmp = ecmpShow
            obRe.adLoad = ad
            obRe.type4Time = adsType
            obRe.placement = adsPlacementEcmp.originPl
            return obRe
        } else {
            LogUtil.logD("ads native fb$adsType $placement ${adsPlacementEcmp.originPl} getAdForShow not loaded")
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
        nativeAd.unregisterView()
        val nativeAdLayout =
            LayoutInflater.from(mContext).inflate(AdsHelper.instance.getLayoutCustome4Native(
                AdsIDDef.FBNative), null, false)
        // BUG-FIX: gán adChoicesContainer cho layout MỚI trước khi addView, tránh add AdOptionsView
        // vào container CŨ (đã bị thay thế) khiến icon Ad Choices không hiện trên ad đang hiển thị
        adChoicesContainer = nativeAdLayout.findViewById<LinearLayout>(R.id.fb_ad_choices_container)
        if (adChoicesContainer != null) {
            adOptionsView = AdOptionsView(mContext, nativeAd, nativeAdLayout as NativeAdLayout)
            adChoicesContainer?.removeAllViews()
            adChoicesContainer?.addView(adOptionsView, 0)
        }
        val nativeAdIcon = nativeAdLayout.findViewById<MediaView>(R.id.fb_native_ad_icon)
        val nativeAdTitle = nativeAdLayout.findViewById<TextView>(R.id.fb_native_ad_title)
        val nativeAdBody = nativeAdLayout.findViewById<TextView>(R.id.fb_native_ad_body)
        val sponsoredLabel =
            nativeAdLayout.findViewById<TextView>(R.id.fb_native_ad_sponsored_label)
        val nativeAdSocialContext =
            nativeAdLayout.findViewById<TextView>(R.id.fb_native_ad_social_context)
        val nativeAdCallToAction =
            nativeAdLayout.findViewById<Button>(R.id.fb_native_ad_call_to_action)
        val nativeAdMedia = nativeAdLayout.findViewById<MediaView>(R.id.fb_native_ad_media)

        var custome = AdsHelper.instance.getNativeCustom(getPl)
        if (custome != null) {
            nativeAdLayout.setBackgroundColor(custome.bgColor)
            nativeAdCallToAction?.setBackgroundColor(custome.actionColor)
        }

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
        clickableViews.add(nativeAdMedia)
        clickableViews.add(nativeAdCallToAction)
        nativeAd.registerViewForInteraction(
            nativeAdLayout, nativeAdMedia, nativeAdIcon, clickableViews
        )

        // Optional: tag views
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdIcon, NativeAdBase.NativeComponentTag.AD_ICON
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdTitle, NativeAdBase.NativeComponentTag.AD_TITLE
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdBody, NativeAdBase.NativeComponentTag.AD_BODY
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdSocialContext, NativeAdBase.NativeComponentTag.AD_SOCIAL_CONTEXT
        )
        NativeAdBase.NativeComponentTag.tagView(
            nativeAdCallToAction, NativeAdBase.NativeComponentTag.AD_CALL_TO_ACTION
        )
        return nativeAdLayout
    }
}