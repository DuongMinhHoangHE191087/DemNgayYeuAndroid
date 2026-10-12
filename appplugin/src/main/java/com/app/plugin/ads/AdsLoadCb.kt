package com.app.plugin.ads

public interface AdsLoadCb {
    public fun onLoaded(placement: String, originPl: String, adId: String)
    public fun onLoadFailed(placement: String, originPl: String, adId: String, error: String)
}