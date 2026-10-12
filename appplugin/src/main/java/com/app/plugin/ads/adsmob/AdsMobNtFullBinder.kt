package com.app.plugin.ads.adsmob

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.app.plugin.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Dựng view container cho native full AdMob (đường get của [AdsMobPlNtFull.getAdForShow]). 2.0.0 C1a (D1): chuyển nguyên
 * văn từ `AdsMobPlNtFull.populateNativeAdView` để file placement dưới 700 dòng; `mContext` là context của placement.
 */
internal object AdsMobNtFullBinder {
    // Inflated detached on purpose: the view is returned and the caller adds it to whichever
    // container shows the ad, which does not exist yet at this point.
    @android.annotation.SuppressLint("InflateParams")
    fun populateNativeAdView(mContext: Context, nativeAd: NativeAd): View? {
        val loview =
            LayoutInflater.from(mContext).inflate(R.layout.appplugin_admob_native_layout_full, null, false)
        var adView: NativeAdView = loview.findViewById(R.id.ad_native_view_lo)
        // Set the media view.
        adView.mediaView = adView.findViewById<View>(R.id.ad_media) as MediaView
        // Set other ad assets.
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        adView.priceView = adView.findViewById(R.id.ad_price)
//        adView.starRatingView = adView.findViewById(R.id.ntcl_stars)
//        adView.storeView = adView.findViewById(R.id.ntcl_store)
//        adView.advertiserView = adView.findViewById(R.id.ntcl_advertiser)

        (adView.headlineView as TextView?)?.text = nativeAd.headline
        adView.mediaView?.mediaContent = nativeAd.mediaContent

        if (nativeAd.body == null) {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView?)?.text = nativeAd.headline
        } else {
            adView.bodyView?.visibility = View.VISIBLE
            (adView.bodyView as TextView?)?.text = nativeAd.body
        }

        if (nativeAd.callToAction == null) {
            adView.callToActionView?.visibility = View.INVISIBLE
        } else {
            adView.callToActionView?.visibility = View.VISIBLE
            (adView.callToActionView as Button?)?.text = nativeAd.callToAction
        }

        if (nativeAd.icon == null) {
            adView.iconView?.visibility = View.GONE
        } else {
            (adView.iconView as ImageView?)?.setImageDrawable(nativeAd.icon!!.drawable)
            adView.iconView?.visibility = View.VISIBLE
        }

        if (nativeAd.price == null) {
            adView.priceView?.visibility = View.INVISIBLE
        } else {
            adView.priceView?.visibility = View.VISIBLE
            (adView.priceView as TextView?)?.text = nativeAd.price
        }

//        if (nativeAd.store == null) {
//            adView.storeView?.visibility = View.INVISIBLE
//        } else {
//            adView.storeView?.visibility = View.VISIBLE
//            (adView.storeView as TextView?)?.text = nativeAd.store
//        }

//        if (nativeAd.starRating == null) {
//            adView.starRatingView?.visibility = View.INVISIBLE
//        } else {
//            (adView.starRatingView as RatingBar?)?.rating = nativeAd.starRating!!.toFloat()
//            adView.starRatingView?.visibility = View.VISIBLE
//        }

//        if (nativeAd.advertiser == null) {
//            adView.advertiserView?.visibility = View.INVISIBLE
//        } else {
//            (adView.advertiserView as TextView?)!!.text = nativeAd.advertiser
//            adView.advertiserView?.visibility = View.VISIBLE
//        }

        adView.setNativeAd(nativeAd)
        return loview
    }
}
