package com.app.plugin.ads

import android.app.Activity
import android.content.Context

public abstract class AdsBaseBanner(type: Int, context: Context) : AdsBase(type, context) {
    override fun setActivity(activity: Activity) {
        super.setActivity(activity)

        for (ad in mapAdsPl) {
            ad.value.setActivity(activity)
        }
    }

    override fun getPlacementDef(): String {
        return PlacementDefault.PLBnDefault
    }

    public open fun hideAd() {
        for (ad in mapAdsPl) {
            (ad.value as AdsPlBaseBn).hideAd(1)
        }
    }
}