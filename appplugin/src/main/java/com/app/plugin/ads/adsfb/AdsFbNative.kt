package com.app.plugin.ads.adsfb

import android.content.Context
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsFbNative(type: Int, context: Context) : AdsBaseNative(type, context) {
    override val eventTag: String get() = "fb_nt"

    override fun getKeyForResDf(): String {
        return ResIdDefault.FBNativeDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_fb${adsType}_native"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsFbPlNt(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
