package com.app.plugin.ads.adsmax

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.until.LogUtil
import com.applovin.mediation.MaxAd
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder


internal class AdsMaxNtFullView : Dialog {
    private var mNativeAdLoader: MaxNativeAdLoader? = null
    private var mNativeFullAd: MaxAd? = null
    private var mTShowBtClose = 3
    private var mCountTimeBtClose = 0
    private var btClose: Button? = null
    private var imgClose: ImageView? = null
    private var tvCount: TextView? = null
    private var mIsDelay = false
    private var mIsShowIc = true

    private var mDensity = -1f

    private var countTimeHandler: Handler? = null

    // Inflated in onCreate, rendered in onStart (see onStart).
    private var pendingAdView: MaxNativeAdView? = null
    private var pendingContainer: FrameLayout? = null
    private var isRendered = false

    private var admaxmy: AdsMaxMy

    constructor(
        context: Context,
        nativeAdLoader: MaxNativeAdLoader,
        nativeAd: MaxAd,
        timeBtClose: Int,
        isDelay: Boolean,
        showIc: Boolean
    ) : super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
        mNativeAdLoader = nativeAdLoader
        mNativeFullAd = nativeAd
        mTShowBtClose = timeBtClose
        mIsDelay = isDelay
        admaxmy = AdsMaxMy.instance
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
            )
            setGravity(Gravity.TOP)
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) // Allow interaction with the main screen
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.appplugin_max_native_full_dialog)
        var nativeLo: FrameLayout = findViewById(R.id.native_ad_lo)
        populateNativeAdView(nativeLo)
        setCancelable(false)
    }

    override fun onStart() {
        super.onStart()
        // AppLovin does not document whether render() already counts toward the impression, so it
        // runs only once this dialog owns a visible window - never for a dialog pre-built behind the
        // parent ad (AdsMaxPlNtFull.prepareChainedShow), which does just the inflation in onCreate.
        renderIfNeeded()
        // The countdown starts here, when the ad is visible, for the same reason as AdMob's.
        if (!mIsDelay) reCount()
    }

    private fun renderIfNeeded() {
        if (isRendered) return
        val adView = pendingAdView ?: return
        val container = pendingContainer ?: return
        val ad = mNativeFullAd ?: return
        mNativeAdLoader?.render(adView, ad)
        container.addView(
            adView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        isRendered = true
        pendingAdView = null
        pendingContainer = null
    }

    private fun createNativeAdView(): MaxNativeAdView {
        val binder =
            MaxNativeAdViewBinder.Builder(R.layout.appplugin_max_native_full)
                .setTitleTextViewId(R.id.ad_headline)
                .setBodyTextViewId(R.id.ad_body)
                //.setStarRatingContentViewGroupId(R.id.ntcl_star_rating_view)
                //.setAdvertiserTextViewId(R.id.ntcl_advertiser)
                .setIconImageViewId(R.id.ad_app_icon)
                .setMediaContentViewGroupId(R.id.ad_media)
                .setCallToActionButtonId(R.id.ad_call_to_action)
                //.setOptionsContentViewGroupId( R.id.options_view )
                .build()
        return MaxNativeAdView(binder, context)
    }

    private fun populateNativeAdView(loAdView: FrameLayout) {
        // Inflate now (the expensive half); render() and attaching wait for onStart.
        pendingAdView = createNativeAdView()
        pendingContainer = loAdView

        btClose = findViewById(R.id.ad_close)
        tvCount = findViewById(R.id.tv_count)
        imgClose = findViewById(R.id.ad_imgclose)
        if (AdsFbMy.isShowGrid > 0) {
            btClose?.setBackgroundColor(Color.GREEN)
        }
        btClose?.setOnClickListener(View.OnClickListener { dismiss() })
        if (mIsDelay) {
            tvCount?.setVisibility(View.INVISIBLE)
            imgClose?.setVisibility(View.VISIBLE)
            btClose?.setVisibility(View.VISIBLE)
            btClose?.setEnabled(true)
            tvCount?.setText("")
        } else {
            // Hidden until onStart starts the countdown.
            imgClose?.visibility = View.INVISIBLE
            btClose?.visibility = View.INVISIBLE
            btClose?.isEnabled = false
            tvCount?.visibility = View.VISIBLE
            tvCount?.text = context.getString(R.string.appplugin_ad_close_countdown, mTShowBtClose)
        }
    }

    fun reCount() {
        if (btClose != null) {
            imgClose!!.visibility = View.INVISIBLE
            btClose!!.visibility = View.INVISIBLE
            btClose!!.isEnabled = false
            tvCount!!.visibility = View.VISIBLE
            tvCount!!.text = context.getString(R.string.appplugin_ad_close_countdown, mTShowBtClose)
            mCountTimeBtClose = mTShowBtClose
            countTimeBtClose(btClose!!, tvCount!!)
        }
    }

    private fun countTimeBtClose(btClose: Button, tvCount: TextView) {
        if (countTimeHandler == null) {
            countTimeHandler = Handler(Looper.getMainLooper())
        }
        countTimeHandler?.postDelayed({
            mCountTimeBtClose--
            if (mCountTimeBtClose > 0) {
                tvCount.text = context.getString(R.string.appplugin_ad_close_countdown, mCountTimeBtClose)
                countTimeBtClose(btClose, tvCount)
            } else {
                tvCount.visibility = View.INVISIBLE
                imgClose!!.visibility = View.VISIBLE
                btClose.visibility = View.VISIBLE
                btClose.isEnabled = true
                tvCount.text = ""
                if (mDensity <= 0) {
                    mDensity = context.resources.displayMetrics.density
                }
                val lp = btClose.layoutParams as? RelativeLayout.LayoutParams
                lp?.width = (24 * mDensity).toInt()
                lp?.height = (24 * mDensity).toInt()
                btClose.layoutParams = lp
            }
        }, 1000)
    }

    fun afterClick() {
        if (btClose != null) {
            val lp = btClose!!.layoutParams as? RelativeLayout.LayoutParams
            lp?.width = (24 * mDensity).toInt()
            lp?.height = (24 * mDensity).toInt()
            btClose!!.layoutParams = lp
        }
    }

    fun cancelCountdown() {
        countTimeHandler?.removeCallbacksAndMessages(null)
    }
}