package com.app.plugin.ads

public interface AdsShowCb {
    public fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {}
    public fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {}
    public fun onAdImpression(placement: String?, originPl: String?, adId: String?) {}
    public fun onAdClick(placement: String?, originPl: String?, adId: String?) {}
    public fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {}
    //fun onAdPaidEvent( placement: String?, originPl:String?, adId: String?, adNet: String?, precisionType: Int, currencyCode: String?, valueMicros: Long )
    //fun onAdDesTroy(placement: String?, originPl:String?, adId: String?)
}