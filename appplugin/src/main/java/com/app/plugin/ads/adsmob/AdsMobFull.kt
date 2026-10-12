package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMobFull(type: Int, context: Context) : AdsBaseFull(type, context) {
    override val eventTag: String get() = "mob_full"

    override fun getKeyForResDf(): String {
        if (adsType == AdsIDDef.AdmobFullInters) {
            return ResIdDefault.AdmobFullDf
        } else if (adsType == AdsIDDef.AdmobFullLow) {
            return ResIdDefault.AdmobFullImgDf
        } else if (adsType == AdsIDDef.AdmobFullOpen) {
            return ResIdDefault.AdmobOpenAdDf
        } else if (adsType == AdsIDDef.AdmobFullNt) {
            return ResIdDefault.AdmobNtFullDf
        } else {
            return ""
        }
    }

    override fun getKeyForDataSave(): String {
        return "plmem_admob${adsType}_full"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        var ads: AdsPlBase? = null
        ads = when (adsType) {
            AdsIDDef.AdmobFullInters -> {
                AdsMobPlInters(mContext, this, adsType, placement)
            }
            AdsIDDef.AdmobFullLow -> {
                AdsMobPlInters(mContext, this, adsType, placement)
            }
            AdsIDDef.AdmobFullOpen -> {
                AdsMobPlOpen(mContext, this, adsType, placement)
            }
            AdsIDDef.AdmobFullNt -> {
                AdsMobPlNtFull(mContext, this, adsType, placement)
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
