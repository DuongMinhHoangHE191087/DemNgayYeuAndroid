package com.app.plugin.ads

internal class AdsOb4Native {
    var adObject: Any? = null
    var objectEx: Any? = null

    constructor(ad: Any?, onEx: Any?) {
        adObject = ad
        objectEx = onEx
    }
}