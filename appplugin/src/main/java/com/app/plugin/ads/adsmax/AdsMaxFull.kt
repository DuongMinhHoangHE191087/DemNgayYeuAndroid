package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMaxFull(type: Int, context: Context) : AdsBaseFull(type, context) {
    override val eventTag: String get() = "max_full"

    override fun getKeyForResDf(): String {
        if (adsType == AdsIDDef.MaxFullInters) {
            return ResIdDefault.MaxFullDf
        } else if (adsType == AdsIDDef.MaxFullOpen) {
            return ResIdDefault.MaxOpenAdDf
        } else if (adsType == AdsIDDef.MaxFullNt) {
            return ResIdDefault.MaxNtFullDf
        } else {
            return ""
        }
    }

    override fun getKeyForDataSave(): String {
        return "plmem_max${adsType}_full"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        var ads: AdsPlBase? = null
        ads = when (adsType) {
            AdsIDDef.MaxFullInters -> {
                AdsMaxPlInters(mContext, this, adsType, placement)
            }

            AdsIDDef.MaxFullOpen -> {
                AdsMaxPlOpen(mContext, this, adsType, placement)
            }

            AdsIDDef.MaxFullNt -> {
                AdsMaxPlNtFull(mContext, this, adsType, placement)
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
