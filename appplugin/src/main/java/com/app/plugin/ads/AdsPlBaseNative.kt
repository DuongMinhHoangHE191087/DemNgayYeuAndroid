package com.app.plugin.ads

import android.content.Context
import android.view.View

internal abstract class AdsPlBaseNative(context: Context, adBase: AdsBase, type: Int, pl: String) :
    AdsPlBase(context, adBase, type, pl) {
    override fun setTickAdHasShow(ecmp: AdsEcmp?): Boolean {
        if (ecmp != null && ecmp.isLoaded == true) {
            ecmp.isLoaded = false
            ecmp.adObject = null
            val ecmploaded = adsPlacementEcmp.adEcmps?.getAdLoaded()
            if (ecmploaded == null) {
                adsPlacementEcmp.isloaded = false
            }
            return true
        }
        return false
    }

    public open fun populateNativeAdView(ecmp: AdsEcmp): View? {
        return null
    }

    public open fun hideAd() {}
    public open fun destroyNative() {}
}