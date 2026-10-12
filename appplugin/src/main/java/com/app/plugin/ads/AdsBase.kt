package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.AppUtil
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil

public abstract class AdsBase(type: Int, context: Context) {
    public var adsType: Int = 0
    public var mContext: Context
    public var mActivity: Activity? = null
    public var mapAdsPl: MutableMap<String, AdsPlBase> = mutableMapOf()

    /**
     * Nhãn của lớp mạng trong log và nhãn `ad_show_fail_reason` `not_plm_cf_<eventTag>_<adsType>` (mạng không có
     * cấu hình cho placement): `<mạng>_<định dạng>`, snake_case, ví dụ `mob_full`, `fb_bn`, `max_reward` (cùng chuỗi
     * với `AdsPlBase.eventTag` của placement cùng mạng và định dạng). Hằng chữ, không lấy từ tên lớp: R8 của app đổi
     * tên lớp SDK, nhãn thành `not_plm_cf_z71`. Khai bằng getter (`get() = "…"`), không backing field, vì `init` của
     * lớp này chạy trước khi field của lớp con được gán. `EventNameContractTest` kiểm mọi lớp mạng khai nó, đúng
     * dạng, không trùng.
     */
    protected abstract val eventTag: String

    init {
        adsType = type
        mContext = context
        loadFromMem()
    }

    public open fun setActivity(activity: Activity) {
        mActivity = activity
    }

    public fun parSerConfig(data: Map<String, Any>, isSave: Boolean = false) {
        var adsDatamem: String = ""
        data.forEach { (key, value) ->
            val adsPl = value as Map<String, Any>
            parserPlacement(key, adsPl)
            if (isSave) {
                if (adsDatamem.length < 3) {
                    adsDatamem = "${key},${adsPl["list"].toString()}"
                } else {
                    adsDatamem += "#${key},${adsPl["list"].toString()}"
                }
            }
        }
        if (isSave) {
            PreUtil.setString(mContext, getKeyForDataSave(), adsDatamem)
        }
    }

    private fun parserPlacement(placement: String, data: Map<String, Any>) {
        var adsPl: AdsPlBase?
        if (mapAdsPl.containsKey(placement)) {
            adsPl = mapAdsPl[placement]
        } else {
            LogEventServer.logAdShowFailWithReason(
                placement = placement,
                reason = Constants.AdShowFailReason.NOT_CONTAIN_PLACEMENT
            )
            adsPl = getNewAdsPl(placement)
            if (adsPl != null) {
                mapAdsPl[placement] = adsPl
            }
        }
        adsPl?.parserEcpms(data["list"].toString())
    }

    public fun checkLoadAfterGetCf() {
        for (ad in mapAdsPl) {
            ad.value.checkLoadAfterGetCf()
        }
    }

    private fun loadFromMem() {
        val idDf = AppUtil.getResourceString(mContext, getKeyForResDf())
        var mapdf: MutableMap<String, String> = mutableMapOf()
        val pls = idDf.split("#")
        for (pl in pls) {
            val dpl = pl.split(",")
            if (dpl.size >= 2) {
                mapdf[dpl[0]] = dpl[1]
            }
        }
        var adsData = PreUtil.getString(mContext, getKeyForDataSave(), idDf)
        if (adsData.length < 10) {
            adsData = idDf
        }
        if (adsData.isNotEmpty()) {
            val pls = adsData.split("#")
            for (pl in pls) {
                val dpl = pl.split(",")
                if (dpl.size >= 2) {
                    var adsPl: AdsPlBase?
                    if (mapAdsPl.containsKey(dpl[0])) {
                        adsPl = mapAdsPl[dpl[0]]
                    } else {
                        adsPl = getNewAdsPl(dpl[0])
                        if (adsPl != null) {
                            // NEW-03 FIX: dùng direct key lookup thay vì iterate rồi remove bên trong
                            // → tránh ConcurrentModificationException
                            val dfValue = mapdf[dpl[0]]
                            if (dfValue != null) {
                                adsPl.setIdsDf(dfValue)
                                mapdf.remove(dpl[0])
                            }
                            mapAdsPl[dpl[0]] = adsPl
                        }
                    }
                    adsPl?.parserEcpms(dpl[1])
                }
            }
        }
        for (depl in mapdf) {
            var adsPl: AdsPlBase?
            if (mapAdsPl.containsKey(depl.key)) {
                adsPl = mapAdsPl[depl.key]
            } else {
                adsPl = getNewAdsPl(depl.key)
                if (adsPl != null) {
                    adsPl.setIdsDf(depl.value)
                    mapAdsPl[depl.key] = adsPl
                }
            }
            adsPl?.parserEcpms(depl.value)
        }
        mapdf.clear()
    }

//    fun saveToMem() {
//        val adsData = dataToString()
//        PreUtil.setString(mContext, getKeyForDataSave(), adsData)
//    }

    private fun dataToString(): String {
        var re = ""
        var f = false
        mapAdsPl.forEach {
            if (f) {
                re += "#"
            } else {
                f = true
            }
            re += it.value.adsPlEcmpToStringData()
        }
        return re
    }

    protected abstract fun getNewAdsPl(placement: String): AdsPlBase?
    protected abstract fun getKeyForDataSave(): String
    protected abstract fun getKeyForResDf(): String
    protected abstract fun getPlacementDef(): String

    /**
     * Slot của [placement] theo thứ tự (2.4.2 SLOT): slot riêng; với tên `full_2_*` thêm slot `full_2_default` (slot Full2
     * chuẩn của demo, INTEGRATION §9, IM ntfull không có `full_default`); rồi slot mặc định của mạng ([getPlacementDef]).
     */
    private fun slotsFor(placement: String): List<AdsPlBase> = listOfNotNull(
        mapAdsPl[placement],
        mapAdsPl[PlacementDefault.PLFull2Default]?.takeIf { placement.startsWith("full_2_") },
        mapAdsPl[getPlacementDef()],
    ).distinct()

    /** Slot để load ([loadAd], [isLoading]): slot đầu của [slotsFor]. */
    protected open fun getAdsForPl(placement: String): AdsPlBase? = slotsFor(placement).firstOrNull()

    /**
     * Slot để show/kiểm đã nạp (fork `ShowSlotChooser`): slot đầu của [slotsFor] đang có quảng cáo theo [has], không thì
     * [getAdsForPl]. RC `cf_ids_placement` tạo được slot riêng giữa load và show; quảng cáo đã nạp ở slot mặc định vẫn hiện.
     */
    private fun getAdsForShow(placement: String, has: (AdsPlBase) -> Boolean): AdsPlBase? =
        slotsFor(placement).firstOrNull(has) ?: getAdsForPl(placement)

    public open fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) {
        val ad = getAdsForPl(placement)
        if (ad != null) {
            ad.load(placement, adsLoadCb)
        } else {
            LogUtil.logD("ads ${eventTag}_$adsType loadAd $placement not pl config")
            adsLoadCb.onLoadFailed(placement, placement, "", "not pl config")
        }
    }

    public open fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int = 0): Boolean {
        val ad = getAdsForShow(placement, AdsPlBase::hasLoadedAd)
        if (ad != null) {
            return ad.show(placement, adsShowCb, flagShow)
        } else {
            // Same as "not loaded": return false and stay silent, so the waterfall moves on to the
            // next network. A callback here reached the app mid-waterfall, and a later network
            // could still show - the app saw a failure and then the ad (doc 64 D).
            LogUtil.logD("ads ${eventTag}_$adsType showAd $placement not pl config")
            LogEventServer.logAdShowFailWithReason(
                placement = placement,
                reason = "${Constants.AdShowFailReason.NOT_PLM_CF}_${eventTag}_$adsType"
            )
            return false
        }
    }

    public fun hasLoadedAd(placement: String): Boolean =
        getAdsForShow(placement, AdsPlBase::hasLoadedAd)?.hasLoadedAd() == true

    /** Như [hasLoadedAd] nhưng kể cả quảng cáo MAX đang tự nạp lại (`isReady() == false`); xem [AdsPlBase.holdsLoadedAd]. */
    internal fun holdsLoadedAd(placement: String): Boolean =
        getAdsForShow(placement) { it.holdsLoadedAd() }?.holdsLoadedAd() == true

    /**
     * Slot của [placement] ở mạng này đang tải. Nhiều placement dùng chung một slot ([getAdsForPl] rơi về placement
     * mặc định: `full_home`, `full_tab_switch` → `full_default`), nên đây là trạng thái của slot, không của placement.
     */
    internal fun isLoading(placement: String): Boolean = getAdsForPl(placement)?.isLoadingAd == true

    public open fun getAdForShow(placement: String, adsShowCb: AdsShowCb): Any? {
        val ad = getAdsForShow(placement, AdsPlBase::hasLoadedAd)
        if (ad != null) {
            return ad.getAdForShow(placement, adsShowCb)
        } else {
            LogUtil.logD("ads ${eventTag}_$adsType getAdForShow $placement not pl config")
            LogEventServer.logAdShowFailWithReason(
                placement = placement,
                reason = "${Constants.AdShowFailReason.NOT_PLM_CF}_${eventTag}_$adsType"
            )
            return null
        }
    }

    /**
     * Lượt 1 khi host chết: bỏ Activity của mạng này và mọi placement, không dismiss, không callback.
     * AdsHelper gọi hàm này trên MỌI mạng trước khi gọi [clearActivity] (lượt 2, native full phát
     * onAdDismissed ở đó) trên bất kỳ mạng nào. Nhờ vậy dù callback dismiss nào load lại, mọi
     * [AdsPlBase.load] đều thất bại an toàn thay vì dựng quảng cáo trên Activity đang huỷ.
     */
    public fun releaseActivity() {
        mActivity = null
        for (ad in mapAdsPl.values) {
            ad.releaseActivity()
        }
    }

    public open fun clearActivity() {
        mActivity = null
        for (ad in mapAdsPl) {
            ad.value.clearActivity()
        }
    }
}