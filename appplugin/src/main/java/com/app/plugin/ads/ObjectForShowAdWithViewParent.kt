package com.app.plugin.ads

import android.view.View
import android.view.ViewGroup

internal class ObjectForShowAdWithViewParent {
    var showCallback: AdsShowCb? = null
    var parentView: ViewGroup? = null
    var viewCurrAttach: View? = null
    var adLoad: Any? = null
    var adExten: Any? = null
    var adViewContainer: View? = null
    var adTypeCurr: Int = 0
    var placement: String = ""
    var isAttach: Boolean = false
    var isLoading: Boolean = false
    var isLoaded: Boolean = false
    var isShow: Boolean = false
    var flagRefresh: Int = 0
    var type4Time: Int = 1000000000
    var adsEcmp:AdsEcmp? = null
}