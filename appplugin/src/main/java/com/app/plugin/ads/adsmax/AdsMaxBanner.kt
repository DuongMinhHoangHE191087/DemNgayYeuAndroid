package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMaxBanner(type: Int, context: Context) : AdsBaseBanner(type, context) {
    override val eventTag: String get() = "max_bn"

    override fun getKeyForResDf(): String {
        return ResIdDefault.MaxBannerDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_max${adsType}_banner"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMaxPlBanner(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
