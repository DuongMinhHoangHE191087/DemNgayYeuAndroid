package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMaxNative(type: Int, context: Context) : AdsBaseNative(type, context) {
    override val eventTag: String get() = "max_nt"

    override fun getKeyForResDf(): String {
        return ResIdDefault.MaxNativeDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_max${adsType}_native"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMaxPlNt(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
