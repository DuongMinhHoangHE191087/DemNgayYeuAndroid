package com.app.plugin.ads.adsfb

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.app.plugin.R

import com.facebook.ads.AdOptionsView
import com.facebook.ads.MediaView
import com.facebook.ads.NativeAd
import com.facebook.ads.NativeAdBase
import com.facebook.ads.NativeAdLayout


internal class AdsFbNtClView : Dialog {

    private var mNativeClAd: NativeAd? = null
    private var mNativeAdLayout: NativeAdLayout? = null
    private var adOptionsView: AdOptionsView? = null
    private var adChoicesContainer: LinearLayout? = null
    private var mTShowBtClose = 1
    private var loBtClose: RelativeLayout? = null
    private var btClose: Button? = null

    private var mDensity = -1f


    private var adfbmy: AdsFbMy

    constructor(context: Context, nativeAd: NativeAd, timeBtClose: Int) : super(
        context, android.R.style.Theme_Translucent
    ) {
        mNativeClAd = nativeAd
        mTShowBtClose = timeBtClose
        adfbmy = AdsFbMy.instance
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.BOTTOM)
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) // Allow interaction with the main screen
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.appplugin_fb_native_cl)
        if (mDensity <= 0) {
            val dpmt: DisplayMetrics = context.resources.displayMetrics
            mDensity = dpmt.density
        }
        mNativeAdLayout = findViewById(R.id.fb_ntcl_ad_view)
        adChoicesContainer = findViewById(R.id.fb_ntcl_ad_choices_container)
        mNativeClAd?.let { nativeAd ->
            mNativeAdLayout?.let { adLayout ->
                populateNativeAdView(nativeAd, adLayout)
            }
        }
        setCancelable(false)
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdLayout) {

        nativeAd.unregisterView()
        if (adChoicesContainer != null) {
            adOptionsView = AdOptionsView(context, nativeAd, adView)
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
        clickableViews.add(nativeAdMedia)
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

        loBtClose = findViewById(R.id.fb_ntlo_close)
        btClose = loBtClose?.findViewById(R.id.fb_ntcl_close)
        if (AdsFbMy.isShowGrid > 0) {
            btClose?.setBackgroundColor(Color.GREEN)
        }
        btClose?.setOnClickListener { dismiss() }
        loBtClose?.visibility = View.VISIBLE
        val lp = btClose?.layoutParams as? RelativeLayout.LayoutParams
        lp?.width = (24 * mDensity).toInt()
        lp?.height = (24 * mDensity).toInt()
        btClose?.layoutParams = lp
    }



    fun afterClick() {
        if (btClose != null) {
            val lp = btClose!!.layoutParams as? RelativeLayout.LayoutParams
            lp?.width = (24 * mDensity).toInt()
            lp?.height = (24 * mDensity).toInt()
            btClose!!.layoutParams = lp
        }
    }

}
