package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBaseBannerCl
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMobNtCl(type: Int, context: Context) : AdsBaseBannerCl(type, context) {
    override val eventTag: String get() = "mob_nt_cl"

    override fun getKeyForResDf(): String {
        return ResIdDefault.AdmobNtClDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_admob${adsType}_ntcl"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMobPlNtCl(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
