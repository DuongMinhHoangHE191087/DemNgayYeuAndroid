package com.app.plugin.ads.adsmob

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.MainPost
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * Dựng view container cho native full AdMob (đường get của [AdsMobPlNtFull.getAdForShow]); `mContext` là context của
 * placement. 2.4.0 NG5a: GMA Next-Gen — layout `appplugin_admob_native_layout_full` lớp gốc Next-Gen, cùng id Legacy;
 * Next-Gen không có setter `mediaView`/`setNativeAd`: `registerNativeAd(ad, mediaView)` chỉ trên main qua [MainPost.run] [S14][S28].
 */
internal object AdsMobNtFullBinder {
    /** Seam test: inflater của view (JVM test không có `LayoutInflater.from`). */
    @Volatile internal var inflater: (Context) -> LayoutInflater = { LayoutInflater.from(it) }

    // Inflated detached on purpose: the view is returned and the caller adds it to whichever
    // container shows the ad, which does not exist yet at this point.
    @android.annotation.SuppressLint("InflateParams")
    fun populateNativeAdView(mContext: Context, nativeAd: NativeAd): View? {
        val loview = inflater(mContext).inflate(R.layout.appplugin_admob_native_layout_full, null, false)
        val adView: NativeAdView = loview.findViewById(R.id.ad_native_view_lo)
        val mediaView: MediaView = adView.findViewById(R.id.ad_media)
        val headline = adView.findViewById<TextView>(R.id.ad_headline)
        val body = adView.findViewById<TextView>(R.id.ad_body)
        val cta = adView.findViewById<Button>(R.id.ad_call_to_action)
        val icon = adView.findViewById<ImageView>(R.id.ad_app_icon)
        val price = adView.findViewById<TextView>(R.id.ad_price)
        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = cta
        adView.iconView = icon
        adView.priceView = price

        headline?.text = nativeAd.headline
        body?.visibility = View.VISIBLE
        body?.text = nativeAd.body ?: nativeAd.headline

        if (nativeAd.callToAction == null) {
            cta?.visibility = View.INVISIBLE
        } else {
            cta?.visibility = View.VISIBLE
            cta?.text = nativeAd.callToAction
        }

        val image = nativeAd.icon
        if (image == null) {
            icon?.visibility = View.GONE
        } else {
            icon?.setImageDrawable(image.drawable)
            icon?.visibility = View.VISIBLE
        }

        if (nativeAd.price == null) {
            price?.visibility = View.INVISIBLE
        } else {
            price?.visibility = View.VISIBLE
            price?.text = nativeAd.price
        }

        MainPost.run { adView.registerNativeAd(nativeAd, mediaView) } // [S14][S28]: chỉ trên main
        return loview
    }
}
