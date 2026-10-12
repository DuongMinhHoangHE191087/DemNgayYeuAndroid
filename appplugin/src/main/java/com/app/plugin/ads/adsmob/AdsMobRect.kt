package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBaseRect
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMobRect(type: Int, context: Context) : AdsBaseRect(type, context) {
    override val eventTag: String get() = "mob_rect"

    override fun getKeyForResDf(): String {
        return ResIdDefault.AdmobRectDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_admob${adsType}_rect"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMobPlRect(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
