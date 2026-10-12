package com.app.plugin.ads.adsfb

import android.content.Context
import com.app.plugin.ads.AdsBaseRect
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsFbRect(type: Int, context: Context) : AdsBaseRect(type, context) {
    override val eventTag: String get() = "fb_rect"

    override fun getKeyForResDf(): String {
        return ResIdDefault.FBRectDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_fb${adsType}_rect"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsFbPlRect(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
