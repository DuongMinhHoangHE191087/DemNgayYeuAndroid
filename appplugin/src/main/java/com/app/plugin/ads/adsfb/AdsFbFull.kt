package com.app.plugin.ads.adsfb

import android.content.Context
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsFbFull(type: Int, context: Context) : AdsBaseFull(type, context) {
    override val eventTag: String get() = "fb_full"

    override fun getKeyForResDf(): String {
        if (adsType == AdsIDDef.FBFullInters) {
            return ResIdDefault.FBFullDf
        } else if (adsType == AdsIDDef.FBFullNt) {
            return ResIdDefault.FBNtFullDf
        } else {
            return ""
        }
    }

    override fun getKeyForDataSave(): String {
        return "plmem_fb${adsType}_full"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        var ads: AdsPlBase? = null
        ads = when (adsType) {
            AdsIDDef.FBFullInters -> {
                AdsFbPlInters(mContext, this, adsType, placement)
            }

            AdsIDDef.FBFullNt -> {
                AdsFbPlNtFull(mContext, this, adsType, placement)
            }

            else -> {
                null
            }
        }
        if (ads != null && mActivity != null) {
            ads.setActivity(mActivity!!)
        }
        return ads
    }
}
