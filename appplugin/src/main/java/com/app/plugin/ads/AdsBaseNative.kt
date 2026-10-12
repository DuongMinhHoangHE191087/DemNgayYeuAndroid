package com.app.plugin.ads

import android.app.Activity
import android.content.Context

public abstract class AdsBaseNative(type: Int, context: Context) : AdsBase(type, context) {
    override fun setActivity(activity: Activity) {
        super.setActivity(activity)

        for (ad in mapAdsPl) {
            ad.value.setActivity(activity)
        }
    }

    internal fun getReusableAd(source: String, target: String, callback: AdsShowCb): Any? {
        val donor = mapAdsPl[source] ?: return null
        if (!donor.hasFreshAdForReuse()) return null
        return donor.getAdForShow(target, callback)
    }

    override fun getPlacementDef(): String {
        return PlacementDefault.PLNtDefault
    }
}