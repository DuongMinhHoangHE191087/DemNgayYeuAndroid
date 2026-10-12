package com.app.plugin.ads.adsmax

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.app.plugin.R
import com.app.plugin.ads.adsfb.AdsFbMy
import com.applovin.mediation.MaxAd
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder


internal class AdsMaxNtClView : Dialog {
    private var mNativeAdLoader: MaxNativeAdLoader? = null
    private var mNativeClAd: MaxAd? = null
    private var mTShowBtClose = 1
    private var loBtClose: RelativeLayout? = null
    private var btClose: Button? = null

    private var mDensity = -1f


    private var admaxmy: AdsMaxMy

    constructor(
        context: Context, nativeAdLoader: MaxNativeAdLoader, nativeAd: MaxAd, timeBtClose: Int
    ) : super(
        context, android.R.style.Theme_Translucent
    ) {
        mNativeAdLoader = nativeAdLoader
        mNativeClAd = nativeAd
        mTShowBtClose = timeBtClose
        admaxmy = AdsMaxMy.instance
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
        setContentView(R.layout.appplugin_max_native_cl_dialog)
        if (mDensity <= 0) {
            val dpmt: DisplayMetrics = context.getResources().getDisplayMetrics()
            mDensity = dpmt.density
        }
        var nativeAdView: FrameLayout = findViewById(R.id.ntcl_lo)
        populateNativeAdView(mNativeClAd!!, nativeAdView)
        setCancelable(false)
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
        return MaxNativeAdView(binder, context)
    }

    private fun populateNativeAdView(nativeAd: MaxAd, loAdView: FrameLayout) {
        val adView = createNativeAdView()
        mNativeAdLoader?.render(adView, nativeAd)
        loAdView.addView(
            adView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        loBtClose = findViewById<RelativeLayout>(R.id.ntlo_close)
        btClose = loBtClose?.findViewById<Button>(R.id.ntcl_close)
        if (AdsFbMy.isShowGrid > 0) {
            btClose?.setBackgroundColor(Color.GREEN)
        }
        btClose?.setOnClickListener(View.OnClickListener { dismiss() })
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
