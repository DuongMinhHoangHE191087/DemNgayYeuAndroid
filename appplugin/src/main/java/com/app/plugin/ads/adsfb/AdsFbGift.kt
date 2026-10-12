package com.app.plugin.ads.adsfb

import android.content.Context
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsFbGift(type: Int, context: Context) : AdsBaseGift(type, context) {
    override val eventTag: String get() = "fb_reward"

    override fun getKeyForResDf(): String {
        return ResIdDefault.FBGiftDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_fb${adsType}_gift"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsFbPlrewarded(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
