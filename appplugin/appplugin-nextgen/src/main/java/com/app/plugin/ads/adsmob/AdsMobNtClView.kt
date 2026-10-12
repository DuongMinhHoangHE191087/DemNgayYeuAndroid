package com.app.plugin.ads.adsmob

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
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
import com.google.android.libraries.ads.mobile.sdk.common.VideoController
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * 2.4.0 NG4: dialog native collapsible AdMob trên GMA Next-Gen (layout `appplugin_admob_native_cl`, lớp gốc Next-Gen [S14]).
 * [bind] là phần gắn asset dùng chung với `AdsMobPlNtCl` (Legacy chép hai lần): `registerNativeAd(ad, mediaView)` chỉ trên
 * main qua [MainPost.run] [S14][S28].
 */
internal class AdsMobNtClView(context: Context, private val nativeAd: NativeAd, @Suppress("unused") timeBtClose: Int) :
    Dialog(context, android.R.style.Theme_Translucent) {
    private var views: Views? = null
    private var density = -1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) // cho phép thao tác màn hình chính
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.appplugin_admob_native_cl)
        if (density <= 0) density = context.resources.displayMetrics.density
        views = bind(window!!.decorView, nativeAd, findViewById(R.id.ntcl_ntadview), density, previous = views?.video) { dismiss() }
        setCancelable(false)
    }

    fun afterClick() {
        views?.btClose?.let { closeSize(it, density) }
    }

    /** Nút đóng và video controller của lượt đang hiện (để dừng khi lượt sau thay). */
    internal class Views(val loClose: RelativeLayout?, val btClose: Button?, val video: VideoController?)

    internal companion object {
        /** Gắn asset của [nativeAd] vào [adView] (id `ntcl_*`), nút đóng gọi [onClose]; dừng video của lượt trước [previous]. */
        fun bind(root: View, nativeAd: NativeAd, adView: NativeAdView, density: Float, previous: VideoController?, onClose: () -> Unit): Views {
            val mediaView: MediaView = adView.findViewById(R.id.ntcl_media)
            adView.headlineView = adView.findViewById(R.id.ntcl_headline)
            adView.bodyView = adView.findViewById(R.id.ntcl_body)
            adView.callToActionView = adView.findViewById(R.id.ntcl_call_to_action)
            adView.iconView = adView.findViewById(R.id.ntcl_app_icon)
            adView.priceView = adView.findViewById(R.id.ntcl_price)
            adView.starRatingView = adView.findViewById(R.id.ntcl_stars)
            adView.storeView = adView.findViewById(R.id.ntcl_store)
            adView.advertiserView = adView.findViewById(R.id.ntcl_advertiser)
            (adView.headlineView as TextView?)?.text = nativeAd.headline
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView?)?.text = nativeAd.body ?: nativeAd.headline
            text(adView.callToActionView, nativeAd.callToAction, View.INVISIBLE)
            val icon = nativeAd.icon
            if (icon == null) {
                adView.iconView?.visibility = View.GONE
            } else {
                (adView.iconView as ImageView?)?.setImageDrawable(icon.drawable)
                adView.iconView?.visibility = View.VISIBLE
            }
            text(adView.priceView, nativeAd.price, View.INVISIBLE)
            text(adView.storeView, nativeAd.store, View.INVISIBLE)
            text(adView.advertiserView, nativeAd.advertiser, View.INVISIBLE)
            val stars = nativeAd.starRating
            if (stars == null) {
                adView.starRatingView?.visibility = View.INVISIBLE
            } else {
                (adView.starRatingView as RatingBar?)?.rating = stars.toFloat()
                adView.starRatingView?.visibility = View.VISIBLE
            }
            MainPost.run { adView.registerNativeAd(nativeAd, mediaView) } // [S14][S28]: chỉ trên main
            previous?.stop()
            val media = nativeAd.mediaContent
            val video = if (media != null && media.hasVideoContent) media.videoController else null
            val loClose = root.findViewById<RelativeLayout>(R.id.ntlo_close)
            val btClose = loClose?.findViewById<Button>(R.id.ntcl_close)
            if (AdsFbMy.isShowGrid > 0) btClose?.setBackgroundColor(Color.GREEN)
            btClose?.setOnClickListener { onClose() }
            loClose?.visibility = View.VISIBLE
            btClose?.let { closeSize(it, density) }
            return Views(loClose, btClose, video)
        }

        private fun text(view: View?, value: String?, hidden: Int) {
            if (value == null) {
                view?.visibility = hidden
            } else {
                view?.visibility = View.VISIBLE
                (view as TextView?)?.text = value
            }
        }

        private fun closeSize(bt: Button, density: Float) {
            val lp = bt.layoutParams as? RelativeLayout.LayoutParams
            lp?.width = (24 * density).toInt()
            lp?.height = (24 * density).toInt()
            bt.layoutParams = lp
        }
    }
}
