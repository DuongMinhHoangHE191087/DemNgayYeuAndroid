package com.app.plugin.ads.adsmob

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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsfb.AdsFbMy
import com.google.android.libraries.ads.mobile.sdk.common.VideoController
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * 2.4.0 NG5a: dialog Full2 (native full) trên GMA Next-Gen, layout `appplugin_admob_native_full` (+ `layout-land`) lớp gốc
 * Next-Gen [S14]. Khác Legacy: `registerNativeAd(ad, mediaView)` chỉ trên main qua [MainPost.run] [S14][S28]; video yêu cầu
 * điều khiển riêng ([videoOptions], [S15]) nên khi `isCustomControlsEnabled()` nút play/pause/mute của ta hiện và trạng thái
 * nút theo `VideoLifecycleCallbacks` đi qua [MainPost.video] [S16] (callback Next-Gen ở luồng nền [S5]).
 */
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

    constructor(
        context: Context,
        nativeAd: NativeAd,
        timeBtClose: Int,
        isDelay: Boolean,
        @Suppress("UNUSED_PARAMETER") showIc: Boolean
    ) : super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
        mNativeFullAd = nativeAd
        mTShowBtClose = timeBtClose
        mIsDelay = isDelay
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.TOP)
            setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
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
        val adView: NativeAdView = findViewById(R.id.native_ad_view)
        mNativeAdView = adView
        populateNativeAdView(requireNotNull(mNativeFullAd), adView)
        setCancelable(false)
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        bind(adView, nativeAd, mIsShowIc)
        vcControlCurrShow = videoControls(window?.decorView ?: adView, nativeAd)
        btClose = findViewById(R.id.ad_close)
        tvCount = findViewById(R.id.tv_count)
        imgClose = findViewById(R.id.ad_imgclose)
        if (AdsFbMy.isShowGrid > 0) {
            btClose?.setBackgroundColor(Color.GREEN)
        }
        btClose?.setOnClickListener { dismiss() }
        if (mIsDelay) {
            tvCount?.visibility = View.INVISIBLE
            imgClose?.visibility = View.VISIBLE
            btClose?.visibility = View.VISIBLE
            btClose?.isEnabled = true
            tvCount?.text = ""
            // BUG-FIX (Legacy): nút tắt về 24dp như nhánh sau countdown, nếu không giữ 22dp của XML, vùng bấm nhỏ.
            closeSize()
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
        val bt = btClose ?: return
        val count = tvCount ?: return
        imgClose?.visibility = View.INVISIBLE
        bt.visibility = View.INVISIBLE
        bt.isEnabled = false
        count.visibility = View.VISIBLE
        count.text = context.getString(R.string.appplugin_ad_close_countdown, mTShowBtClose)
        mCountTimeBtClose = mTShowBtClose
        countTimeBtClose(bt, count)
    }

    private fun countTimeBtClose(btClose: Button, tvCount: TextView) {
        val handler = countTimeHandler ?: Handler(Looper.getMainLooper()).also { countTimeHandler = it }
        handler.postDelayed({
            mCountTimeBtClose--
            if (mCountTimeBtClose > 0) {
                tvCount.text = context.getString(R.string.appplugin_ad_close_countdown, mCountTimeBtClose)
                countTimeBtClose(btClose, tvCount)
            } else {
                tvCount.visibility = View.INVISIBLE
                imgClose?.visibility = View.VISIBLE
                btClose.visibility = View.VISIBLE
                btClose.isEnabled = true
                tvCount.text = ""
                closeSize()
            }
        }, 1000)
    }

    private fun closeSize() {
        if (mDensity <= 0) mDensity = context.resources.displayMetrics.density
        afterClick()
    }

    fun afterClick() {
        val bt = btClose ?: return
        val lp = bt.layoutParams as? RelativeLayout.LayoutParams
        lp?.width = (24 * mDensity).toInt()
        lp?.height = (24 * mDensity).toInt()
        bt.layoutParams = lp
    }

    fun cancelCountdown() {
        countTimeHandler?.removeCallbacksAndMessages(null)
    }

    /**
     * Gỡ NativeAd khỏi view khi dialog bị bỏ mà quảng cáo được giữ: GMA giữ view (context Activity) từ NativeAd; Next-Gen
     * còn giữ callback video (nút của dialog) trên controller của quảng cáo → gỡ luôn. Không huỷ NativeAd. Gọi lần hai là no-op.
     */
    internal fun releaseNativeAdView() {
        vcControlCurrShow?.videoLifecycleCallbacks = null
        vcControlCurrShow = null
        mNativeAdView?.destroy()
        mNativeAdView = null
    }

    /** Trạng thái nút điều khiển của ta theo vòng đời video; chỉ chạy trên main (đăng ký qua [MainPost.video]). */
    private class VideoUi(private val play: View?, private val pause: View?, private val mute: ImageButton?) :
        VideoController.VideoLifecycleCallbacks {
        override fun onVideoStart() = playing(true)
        override fun onVideoPlay() = playing(true)
        override fun onVideoPause() = playing(false)
        override fun onVideoEnd() = playing(false)
        override fun onVideoMute(isMuted: Boolean) = muted(isMuted)

        fun playing(on: Boolean) {
            play?.visibility = if (on) View.GONE else View.VISIBLE
            pause?.visibility = if (on) View.VISIBLE else View.GONE
        }

        fun muted(on: Boolean) {
            mute?.setImageResource(if (on) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off)
        }
    }

    internal companion object {
        /** [S15] `VideoOptions` của Full2 (Legacy `AdsMobPlNtFull.kt:92`): có tiếng + yêu cầu điều khiển riêng; NG5b nạp với nó. */
        fun videoOptions(): VideoOptions = VideoOptions.Builder().setStartMuted(false).setCustomControlsRequested(true).build()

        /** Gắn asset theo id Legacy (headline, body, giá, CTA, icon nếu [showIc]) rồi `registerNativeAd` trên main [S14]. */
        fun bind(adView: NativeAdView, nativeAd: NativeAd, showIc: Boolean) {
            val mediaView: MediaView = adView.findViewById(R.id.ad_media)
            val headline = adView.findViewById<TextView>(R.id.ad_headline)
            val body = adView.findViewById<TextView>(R.id.ad_body)
            val price = adView.findViewById<TextView>(R.id.ad_price)
            val cta = adView.findViewById<Button>(R.id.ad_call_to_action)
            val icon = adView.findViewById<ImageView>(R.id.ad_app_icon)
            adView.headlineView = headline
            adView.bodyView = body
            adView.priceView = price
            adView.callToActionView = cta
            if (showIc) {
                icon?.clipToOutline = true
                icon?.visibility = View.VISIBLE
                adView.iconView = icon
            } else {
                icon?.visibility = View.INVISIBLE
            }
            headline?.text = nativeAd.headline
            mediaView.imageScaleType = ImageView.ScaleType.CENTER_INSIDE
            // GONE (not INVISIBLE) so the bottom card - a wrap_content ViewGroup - shrinks
            // to fit instead of keeping a blank reserved row for an asset the ad didn't send.
            text(body, nativeAd.body, View.GONE)
            text(price, nativeAd.price, View.GONE)
            text(cta, nativeAd.callToAction, View.INVISIBLE)
            if (showIc && icon != null) {
                val image = nativeAd.icon
                if (image == null) {
                    icon.visibility = View.INVISIBLE
                } else {
                    icon.setImageDrawable(image.drawable)
                    icon.visibility = View.VISIBLE
                }
            }
            MainPost.run { adView.registerNativeAd(nativeAd, mediaView) } // [S14][S28]: chỉ trên main
        }

        /**
         * Nút play/pause/mute của ta (`appplugin_ntfull_controls`) trong [root]: hiện + điều khiển [VideoController] khi quảng
         * cáo có video và `isCustomControlsEnabled()` [S15]; không thì ẩn. Trả controller khi có video.
         */
        fun videoControls(root: View, nativeAd: NativeAd): VideoController? {
            val media = nativeAd.mediaContent
            val vc = if (media.hasVideoContent) media.videoController else null
            val bar = root.findViewById<View>(R.id.appplugin_ntfull_controls)
            if (vc == null || !vc.isCustomControlsEnabled) {
                bar?.visibility = View.GONE
                return vc
            }
            val play = root.findViewById<View>(R.id.appplugin_ntfull_play)
            val pause = root.findViewById<View>(R.id.appplugin_ntfull_pause)
            val mute = root.findViewById<ImageButton>(R.id.appplugin_ntfull_mute)
            val ui = VideoUi(play, pause, mute)
            ui.playing(true)
            ui.muted(vc.isMuted)
            play?.setOnClickListener { vc.play() }
            pause?.setOnClickListener { vc.pause() }
            mute?.setOnClickListener { vc.mute(!vc.isMuted) }
            vc.videoLifecycleCallbacks = MainPost.video(ui) // [S16]: callback nền → main
            bar?.visibility = View.VISIBLE
            return vc
        }

        private fun text(view: TextView?, value: String?, hidden: Int) {
            if (value == null) {
                view?.visibility = hidden
            } else {
                view?.visibility = View.VISIBLE
                view?.text = value
            }
        }
    }
}
