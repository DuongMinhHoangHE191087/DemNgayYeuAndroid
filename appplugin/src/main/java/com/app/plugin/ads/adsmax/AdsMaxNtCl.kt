package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBaseBannerCl
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMaxNtCl(type: Int, context: Context) : AdsBaseBannerCl(type, context) {
    override val eventTag: String get() = "max_nt_cl"

    override fun getKeyForResDf(): String {
        return ResIdDefault.MaxNtClDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_max${adsType}_ntcl"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMaxPlNtCl(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
