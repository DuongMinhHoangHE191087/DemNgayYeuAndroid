package com.app.plugin.ads.adsfb

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.app.plugin.R

import com.app.plugin.until.LogUtil
import com.facebook.ads.AdOptionsView
import com.facebook.ads.MediaView
import com.facebook.ads.NativeAd
import com.facebook.ads.NativeAdBase
import com.facebook.ads.NativeAdLayout


internal class AdsFbNtFullView : Dialog {

    private var mNativeFullAd: NativeAd? = null
    private var mTShowBtClose = 3
    private var mCountTimeBtClose = 0
    private var mNativeAdLayout: NativeAdLayout? = null
    private var adOptionsView: AdOptionsView? = null
    private var adChoicesContainer: LinearLayout? = null
    private var btClose: Button? = null
    private var imgClose: ImageView? = null
    private var tvCount: TextView? = null
    private var mIsDelay = false
    private var mIsShowIc = true

    private var mDensity = -1f

    private var countTimeHandler: Handler? = null

    private var adfbmy: AdsFbMy
    var accc:Context? = null

    constructor(
        context: Context,
        nativeAd: NativeAd,
        timeBtClose: Int,
        isDelay: Boolean,
        showIc: Boolean
    ) : super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
        mNativeFullAd = nativeAd
        mTShowBtClose = timeBtClose
        mIsDelay = isDelay
        adfbmy = AdsFbMy.instance
        accc = context
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
        setContentView(R.layout.appplugin_fb_native_full)
        mNativeAdLayout = findViewById(R.id.fb_native_ad_view)
        adChoicesContainer = findViewById(R.id.fb_ad_choices_container)
        populateNativeAdView(mNativeFullAd!!, mNativeAdLayout!!)
        setCancelable(false)
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdLayout) {
        nativeAd.unregisterView()
        if (adChoicesContainer != null) {
            adOptionsView = AdOptionsView(context, nativeAd, adView)
            adChoicesContainer?.removeAllViews()
            adChoicesContainer?.addView(adOptionsView, 0)
        }
        val nativeAdIcon = adView.findViewById<MediaView>(R.id.fb_native_ad_icon)
        val nativeAdTitle = adView.findViewById<TextView>(R.id.fb_native_ad_title)
        val nativeAdBody = adView.findViewById<TextView>(R.id.fb_native_ad_body)
        val sponsoredLabel = adView.findViewById<TextView>(R.id.fb_native_ad_sponsored_label)
        val nativeAdSocialContext = adView.findViewById<TextView>(R.id.fb_native_ad_social_context)
        val nativeAdCallToAction = adView.findViewById<Button>(R.id.fb_native_ad_call_to_action)
        val nativeAdMedia = adView.findViewById<MediaView>(R.id.fb_native_ad_media)

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

        btClose = findViewById(R.id.fb_ad_close)
        tvCount = findViewById(R.id.fb_tv_count)
        imgClose = findViewById(R.id.fb_ad_imgclose)
        if (AdsFbMy.isShowGrid > 0) {
            btClose?.setBackgroundColor(Color.GREEN)
        }
        btClose?.setOnClickListener(View.OnClickListener { dismiss() })
        if (mIsDelay) {
            tvCount?.visibility = View.INVISIBLE
            imgClose?.visibility = View.VISIBLE
            btClose?.visibility = View.VISIBLE
            btClose?.isEnabled = true
            tvCount?.text = ""
        } else {
            // Hidden until onStart starts the countdown.
            imgClose?.visibility = View.INVISIBLE
            btClose?.visibility = View.INVISIBLE
            btClose?.isEnabled = false
            tvCount?.visibility = View.VISIBLE
            tvCount?.text = context.getString(R.string.appplugin_ad_close_countdown, mTShowBtClose)
        }
    }

    override fun onStart() {
        super.onStart()
        // Start at the moment the dialog owns a visible window, so a dialog pre-built behind the
        // parent ad (AdsFbPlNtFull.prepareChainedShow) keeps its full countdown. registerViewForInteraction
        // stays in onCreate: Meta counts an impression only once the ad is displayed.
        if (!mIsDelay) reCount()
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