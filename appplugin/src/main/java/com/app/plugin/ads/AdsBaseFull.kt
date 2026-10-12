package com.app.plugin.ads

import android.app.Activity
import android.content.Context

public abstract class AdsBaseFull(type: Int, context: Context) : AdsBase(type, context) {
    override fun setActivity(activity: Activity) {
        super.setActivity(activity)

        for (ad in mapAdsPl) {
            ad.value.setActivity(activity)
        }
    }

    /** Forwards the pre-build request to every placement this network holds. */
    public open fun prepareChainedShow() {
        for (ad in mapAdsPl) {
            ad.value.prepareChainedShow()
        }
    }

    /** Forwards the discard request to every placement this network holds. */
    public open fun cancelChainedShow() {
        for (ad in mapAdsPl) {
            ad.value.cancelChainedShow()
        }
    }

    override fun getPlacementDef(): String {
        return PlacementDefault.PLFullDefault
    }
}