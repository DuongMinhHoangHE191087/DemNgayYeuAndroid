package com.app.plugin.ads.adsfb

import android.content.Context
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsFbBanner(type: Int, context: Context) : AdsBaseBanner(type, context) {
    override val eventTag: String get() = "fb_bn"

    override fun getKeyForResDf(): String {
        return ResIdDefault.FBBannerDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_fb${adsType}_banner"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsFbPlBanner(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
