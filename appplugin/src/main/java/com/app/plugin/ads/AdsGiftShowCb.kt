package com.app.plugin.ads

public interface AdsGiftShowCb: AdsShowCb {
    public fun onAdRewarded(placement: String?, originPl:String?, adId: String?) {}
}