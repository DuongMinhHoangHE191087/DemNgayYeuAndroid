package com.app.plugin.until

internal typealias AdCallBack<T> = (state: AdState, adView: T?) -> Unit

internal object AdNetwork {
    val ADMOB = 15
    val MYTARGET = 12
    val BIGO = 13
}

internal enum class AdPlatform(val txt: String) {
    ADMOB("Admob"), MY_TARGET("MyTarget"), BIGO("Bigo"), MAX("max")
}

internal enum class AdState {
    AD_LOAD_FAIL,
    AD_LOAD_OK,
    AD_SHOW_FAIL,
    AD_SHOW,
    AD_REWARD_FAIL,
    AD_REWARD_OK,
    AD_CLOSE,
    AD_IMPRESSION,
    AD_CLICK,
    AD_OPENED,
    AD_REWARDED,
    AD_SWIPE_GESTURE_CLICK
}


internal enum class AdFormat(val txt: String) {
    BANNER("banner"),
    BANNER_RECT("banner_rect"),
    INTERSTITIAL("interstitial"),
    REWARDED("reward"),
    NATIVE("native"),
    NATIVE_CL("native_cl"),
    OPEN("open"),
    NATIVE_FULL("native_full")
}