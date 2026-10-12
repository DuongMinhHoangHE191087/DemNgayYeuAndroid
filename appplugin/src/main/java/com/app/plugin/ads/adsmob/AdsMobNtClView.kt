package com.app.plugin.ads.adsmob

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
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.RelativeLayout
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsfb.AdsFbMy
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoController.VideoLifecycleCallbacks
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView


internal class AdsMobNtClView : Dialog {

    private var mNativeClAd: NativeAd? = null
    private var mTShowBtClose = 1
    private var mNativeAdView: NativeAdView? = null
    private var loBtClose: RelativeLayout? = null
    private var btClose: Button? = null

    private var mDensity = -1f
    private var vcControlCurrShow: VideoController? = null


    private var admobmy: AdsMobMy

    constructor(context: Context, nativeAd: NativeAd, timeBtClose: Int) : super(
        context, android.R.style.Theme_Translucent
    ) {
        mNativeClAd = nativeAd
        mTShowBtClose = timeBtClose
        admobmy = AdsMobMy.instance
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
        setContentView(R.layout.appplugin_admob_native_cl)
        if (mDensity <= 0) {
            val dpmt: DisplayMetrics = context.getResources().getDisplayMetrics()
            mDensity = dpmt.density
        }
        mNativeAdView = findViewById(R.id.ntcl_ntadview)
        populateNativeAdView(mNativeClAd!!, mNativeAdView!!)
        setCancelable(false)
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
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
                videoController.videoLifecycleCallbacks = MainPost.video(object : VideoLifecycleCallbacks() {
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
