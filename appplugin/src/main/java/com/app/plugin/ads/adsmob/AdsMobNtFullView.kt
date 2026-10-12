package com.app.plugin.ads.adsmob

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoController.VideoLifecycleCallbacks
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView



internal class AdsMobNtFullView : Dialog {

    private var mNativeFullAd: NativeAd? = null
    private var mTShowBtClose = 3
    private var mCountTimeBtClose = 0
    private var mNativeAdView: NativeAdView? = null
    private var btClose: Button? = null
    private var imgClose: ImageView? = null
    private var tvCount: TextView? = null
    private var mIsDelay = false
    private var mIsShowIc = true

    private var mDensity = -1f
    private var vcControlCurrShow: VideoController? = null

    private var countTimeHandler: Handler? = null

    private var admobmy: AdsMobMy

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
        admobmy = AdsMobMy.instance
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

            // Cho content extend vào vùng display cutout (notch). Trong landscape cutout
            // là long-edge nên cần ALWAYS (API 30+) thay vì SHORT_EDGES để fill kín ngang.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes = attributes.apply {
                    layoutInDisplayCutoutMode =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        } else {
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                }
            }
        }
        // LAYOUT_HIDE_NAVIGATION + LAYOUT_FULLSCREEN bắt buộc kèm HIDE_NAVIGATION/FULLSCREEN
        // để content thực sự được layout dưới vùng nav/status bar đã ẩn — nếu thiếu, content
        // chỉ thu vào kích thước "đã trừ nav bar", lộ HomeScreen phía sau ở cạnh nav bar
        // (trong landscape: trái khi rotation_270, phải khi rotation_90).
        window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.appplugin_admob_native_full)
        mNativeAdView = findViewById(R.id.native_ad_view)
        populateNativeAdView(mNativeFullAd!!, mNativeAdView!!)
        setCancelable(false)
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
        // Set the media view.
        adView.mediaView = mediaView

        // Set other ad assets.
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.priceView = adView.findViewById(R.id.ad_price)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        if (mIsShowIc) {
            val imageView = adView.findViewById<ImageView>(R.id.ad_app_icon)
            imageView.clipToOutline = true
            imageView.visibility = View.VISIBLE
            adView.iconView = imageView
        } else {
            val imageView = adView.findViewById<ImageView>(R.id.ad_app_icon)
            imageView.visibility = View.INVISIBLE
        }

        // The headline and mediaContent are guaranteed to be in every NativeAd.
        (adView.headlineView as TextView?)?.text = nativeAd.headline
        mediaView.mediaContent = nativeAd.mediaContent
        mediaView.setImageScaleType(ImageView.ScaleType.CENTER_INSIDE)

        // These assets aren't guaranteed to be in every NativeAd, so it's important to
        // check before trying to display them.
        // GONE (not INVISIBLE) so the bottom card - a wrap_content ViewGroup - shrinks
        // to fit instead of keeping a blank reserved row for an asset the ad didn't send.
        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.GONE
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView?)?.text = nativeAd.body
        }
        if (nativeAd.price == null) {
            adView.priceView?.visibility = View.GONE
        } else {
            adView.priceView?.visibility = View.VISIBLE
            (adView.priceView as TextView?)?.text = nativeAd.price
        }
        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button?)?.text = nativeAd.callToAction
        }
        if (mIsShowIc && adView.iconView != null) {
            if (nativeAd.icon == null) {
                adView.iconView?.visibility = View.INVISIBLE
            } else {
                (adView.iconView as ImageView?)?.setImageDrawable(
                    nativeAd.icon!!.drawable
                )
                adView.iconView?.visibility = View.VISIBLE
            }
        }

        // This method tells the Google Mobile Ads SDK that you have finished populating your
        // native ad view with this native ad.
        adView.setNativeAd(nativeAd)

        // Get the video controller for the ad. One will always be provided,
        // even if the ad doesn't have a video asset.
        val videoController = nativeAd.mediaContent!!.videoController

        // Updates the UI to say whether or not this ad has a video asset.
        if (videoController.hasVideoContent()) {

            // Create a new VideoLifecycleCallbacks object and pass it to the VideoController.
            // The VideoController will call methods on this object when events occur in the
            // video lifecycle.
            vcControlCurrShow = videoController
            videoController.videoLifecycleCallbacks = MainPost.video(object : VideoLifecycleCallbacks() {
                override fun onVideoEnd() {
                    // Publishers should allow native ads to complete video playback before
                    // refreshing or replacing them with another ad in the same UI location.
                    super.onVideoEnd()
                }
            })
        } else {
            vcControlCurrShow = null
        }
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
            // BUG-FIX: thiếu resize nút tắt về đúng size 42dp (giống nhánh không bị "nham" sau countdown),
            // nếu không nút giữ nguyên size mặc định 22dp trong XML, vùng bấm nhỏ khó tắt
            if (mDensity <= 0) {
                mDensity = context.resources.displayMetrics.density
            }
            val lp = btClose?.layoutParams as? RelativeLayout.LayoutParams
            lp?.width = (24 * mDensity).toInt()
            lp?.height = (24 * mDensity).toInt()
            btClose?.layoutParams = lp
        } else {
            // Hidden until onStart: the countdown itself starts there (see onStart).
            imgClose?.visibility = View.INVISIBLE
            btClose?.visibility = View.INVISIBLE
            btClose?.isEnabled = false
            tvCount?.visibility = View.VISIBLE
            tvCount?.text = context.getString(R.string.appplugin_ad_close_countdown, mTShowBtClose)
        }
    }

    override fun onStart() {
        super.onStart()
        // Start at the moment the dialog owns a visible window. Starting in onCreate() consumed
        // part of the countdown while the outgoing interstitial was still fading, and would run it
        // entirely for a dialog pre-built behind the parent ad (AdsMobPlNtFull.prepareChainedShow).
        if (!mIsDelay) reCount()
    }

    fun reCount() {
        if (btClose != null) {
            imgClose?.visibility = View.INVISIBLE
            btClose?.visibility = View.INVISIBLE
            btClose?.isEnabled = false
            tvCount?.visibility = View.VISIBLE
            tvCount?.text = context.getString(R.string.appplugin_ad_close_countdown, mTShowBtClose)
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

    /**
     * Gỡ NativeAd khỏi view khi dialog bị bỏ mà quảng cáo được giữ: GMA giữ view (context Activity)
     * từ NativeAd. Không huỷ NativeAd. Gọi lần hai là no-op.
     */
    internal fun releaseNativeAdView() {
        mNativeAdView?.destroy()
        mNativeAdView = null
    }
}