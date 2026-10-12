package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMobNative(type: Int, context: Context) : AdsBaseNative(type, context) {
    override val eventTag: String get() = "mob_nt"

    override fun getKeyForResDf(): String {
        return ResIdDefault.AdmobNativeDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_admob${adsType}_native"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMobPlNt(mContext, this, adsType, placement)
        mActivity?.let { ads.setActivity(it) }
        return ads
    }
}
