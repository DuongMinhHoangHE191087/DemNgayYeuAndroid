package com.app.plugin.ads

/** Host hook used to cover the short hand-off from an interstitial or rewarded ad to Full2. */
public fun interface Full2TransitionListener {
    public fun onTransitionChanged(active: Boolean)
}
