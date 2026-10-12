package com.app.plugin.ads.adsfb

import android.content.Context
import com.app.plugin.ads.AdsBaseBannerCl
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsFbNtCl(type: Int, context: Context) : AdsBaseBannerCl(type, context) {
    override val eventTag: String get() = "fb_nt_cl"

    override fun getKeyForResDf(): String {
        return ResIdDefault.FBNtClDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_fb${adsType}_ntcl"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsFbPlNtCl(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
