package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault

internal class AdsMobGift(type: Int, context: Context) : AdsBaseGift(type, context) {
    override val eventTag: String get() = "mob_reward"

    override fun getKeyForResDf(): String {
        return ResIdDefault.AdmobGiftDf
    }

    override fun getKeyForDataSave(): String {
        return "plmem_admob${adsType}_gift"
    }

    override fun getNewAdsPl(placement: String): AdsPlBase? {
        val ads = AdsMobPlrewarded(mContext, this, adsType, placement)
        mActivity?.let {
            ads.setActivity(it)
        }
        return ads
    }
}
