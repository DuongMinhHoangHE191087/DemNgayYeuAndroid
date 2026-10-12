package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBaseRect
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMaxRect(type: Int, context: Context) : AdsBaseRect(type, context) {
    override val eventTag: String get() = "max_rect"

    override fun getKeyForResDf(): String {
        return ResIdDefault.MaxRectDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_max${adsType}_rect"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMaxPlRect(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
