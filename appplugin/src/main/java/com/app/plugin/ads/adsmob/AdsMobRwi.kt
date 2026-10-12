package com.app.plugin.ads.adsmob

import android.content.Context
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.ResIdDefault
import com.app.plugin.ads.RwiIntroDialog

/**
 * Mạng rewarded interstitial AdMob (1.9.0 Task 2, type `AdsIDDef.AdmobRwi`): id từ resource `admob_rwi`
 * (`rwi_default,<ad unit>#…`), slot mặc định `rwi_default`. Nằm trong map gift của registry để nhận
 * `setActivity`/`releaseActivity`/`clearActivity`; chỉ `RewardedInterstitialController` gọi load/show.
 */
internal class AdsMobRwi(type: Int, context: Context) : AdsBaseGift(type, context) {
    override val eventTag: String get() = "mob_rwi"

    override fun getKeyForResDf(): String = ResIdDefault.AdmobRwiDf

    override fun getKeyForDataSave(): String = "plmem_admob${adsType}_rwi"

    override fun getPlacementDef(): String = PLACEMENT_DEFAULT

    override fun getNewAdsPl(placement: String): AdsPlBase {
        val ads = AdsMobPlRwi(mContext, this, adsType, placement)
        mActivity?.let { ads.setActivity(it) }
        return ads
    }

    /** Host bị huỷ: màn intro đang mở (giữ Activity) đóng trước khi nhả Activity của các placement. */
    override fun clearActivity() {
        RwiIntroDialog.dismissActive()
        super.clearActivity()
    }

    companion object {
        const val PLACEMENT_DEFAULT = "rwi_default"
    }
}
