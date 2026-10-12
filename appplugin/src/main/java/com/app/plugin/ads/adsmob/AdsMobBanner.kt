package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMobBanner(type: Int, context: Context) : AdsBaseBanner(type, context) {
    override val eventTag: String get() = "mob_bn"

    override fun getKeyForResDf(): String {
        return ResIdDefault.AdmobBannerDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_admob${adsType}_banner"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMobPlBanner(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
