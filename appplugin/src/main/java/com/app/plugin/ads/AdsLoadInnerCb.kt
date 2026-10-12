package com.app.plugin.ads

public interface AdsLoadInnerCb {
    public fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp? = null, adspl: AdsPlBase? = null)
    public fun onLoadFailed(placement: String, originPl: String, adId: String, error: String)
}