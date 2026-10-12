package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMaxGift(type: Int, context: Context) : AdsBaseGift(type, context) {
    override val eventTag: String get() = "max_reward"

    override fun getKeyForResDf(): String {
        return ResIdDefault.MaxGiftDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_max${adsType}_gift"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMaxPlrewarded(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
