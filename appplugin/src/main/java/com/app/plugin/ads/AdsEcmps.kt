package com.app.plugin.ads

import android.util.Log
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil

public class AdsEcmps(adPl: AdsPlBase, plEcmp: AdsPlacementEcmpBase) {
    public var adPl: AdsPlBase
    public var adPlEcmp: AdsPlacementEcmpBase
    public var idxCurrEcpm: Int = 0
    private var defaultList: MutableList<String> = mutableListOf()
    private var defaultString = ""
    public var list: MutableList<AdsEcmp> = mutableListOf()

    init {
        this.adPl = adPl
        this.adPlEcmp = plEcmp
    }

    public fun setDefaultIds(ids: String?) {
        defaultList = ids.orEmpty().split(";").map { it.trim() }
            .filter { it.length > 2 }.distinct().toMutableList()
        defaultString = defaultList.joinToString(";")
    }

    public fun getEcmpForLoad(): AdsEcmp? {
        // NEW-01 FIX: list là non-null MutableList, không cần !!
        if (idxCurrEcpm >= 0 && idxCurrEcpm < list.size) {
            return list[idxCurrEcpm]
        } else {
            return null
        }
    }

    public fun getEcmpWithObjectAd(ad: Any?): AdsEcmp? {
        // BUG-02 FIX: `list` là non-null MutableList, không cần check null
        if (ad != null) {
            for (item in list) {
                if (item.adObject != null && item.adObject?.equals(ad) == true) {
                    return item
                }
            }
        }
        return null
    }

    /**
     * Đồng hồ của waterfall (giờ hệ thống, ms): mốc [AdsEcmp.loadedAtMillis] và tuổi quảng cáo đều đọc
     * từ đây, nên test đẩy giờ thay vì sửa dữ liệu.
     */
    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    /**
     * Mục đầu tiên theo thứ tự waterfall vừa `isLoaded` vừa có `adObject`, hoặc `null`.
     *
     * @param maxAgeMillis tuổi tối đa của quảng cáo đã load; `null` là không giới hạn (mọi định dạng trừ
     *   app open, xem [AdsPlBase.loadedAdMaxAgeMs]). Mục có tuổi ngoài `[0, maxAgeMillis)` bị bỏ ngay tại
     *   đây rồi xét mục sau: đánh dấu chưa load, huỷ object ([AdsPlBase.destroyAd]), gửi lý do
     *   [expiredReason], để lần load kế tải mới. Tuổi âm (giờ hệ thống bị lùi sau khi load) là tuổi không
     *   biết, nên cũng bị bỏ.
     * @param nowMillis cùng gốc với [AdsEcmp.loadedAtMillis].
     * @param expiredReason nhãn `ad_show_fail_reason` (và log) của mục bị bỏ vì quá tuổi, do placement gọi quyết
     *   định ([AdsPlBase.loadedAdExpiredReason]; app open: [Constants.AdShowFailReason.APP_OPEN_EXPIRED]).
     */
    public fun getAdLoaded(
        maxAgeMillis: Long? = null,
        nowMillis: Long = nowMs(),
        expiredReason: String = Constants.AdShowFailReason.AD_EXPIRED
    ): AdsEcmp? {
        if (list.isEmpty()) {
            LogEventServer.logAdShowFailWithReason(
                placement = adPl.getLoadPlacement(),
                reason = "listAdsEcmp empty"
            )
        }
        for (item in list) {
            val plm = item.adEcmps.adPl.getLoadPlacement()
            if (item.isLoaded && item.adObject == null) {
                LogEventServer.logAdShowFailWithReason(
                    placement = plm,
                    reason = Constants.AdShowFailReason.LOADED_BUT_ADS_NULL
                )
            }
            if (!item.isLoaded && item.adObject != null) {
                LogEventServer.logAdShowFailWithReason(
                    placement = plm,
                    reason = Constants.AdShowFailReason.LOAD_FAIL_BUT_ADS_NOT_NULL
                )
            }
            if (item.isLoaded && item.adObject != null) {
                val ageMillis = nowMillis - item.loadedAtMillis
                if (maxAgeMillis != null && ageMillis !in 0 until maxAgeMillis) {
                    dropExpired(item, ageMillis, plm, expiredReason)
                    continue
                }
                return item
            }
        }
        return null
    }

    private fun dropExpired(item: AdsEcmp, ageMillis: Long, placement: String, reason: String) {
        val where = "ads ${adPl.logTag} ${adPlEcmp.originPl} id=${item.adsId}"
        LogUtil.logD("$where $reason after ${ageMillis / 60_000L} min, dropped; next load fetches a new one")
        LogEventServer.logAdShowFailWithReason(placement = placement, reason = reason)
        val stale = item.adObject
        item.isLoaded = false
        item.adObject = null
        try {
            adPl.destroyAd(stale)
        } catch (e: Exception) {
            LogUtil.logE("$where expired ad destroy failed: ${e.message}")
        }
    }

    public fun listFromData(data: String): Boolean {
        if (data.length <= 5) return false
        val ids = data.split(";").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) return false
        val previous = list.associateBy { it.adsId }
        val added = ids.any { it !in previous }
        val next = ids.map { id ->
            (previous[id] ?: AdsEcmp(this).apply { adsId = id }).also {
                it.isDefault = id in defaultList
            }
        }.toMutableList()
        // Preserve ownership of outstanding requests and unconsumed ads.
        previous.values.filter { it.adsId !in ids && (it.isLoading || it.isLoaded || it.isDefault) }
            .forEach { next.add(it) }
        defaultList.filter { id -> next.none { it.adsId == id } }.forEach { id ->
            next.add(AdsEcmp(this).apply { adsId = id; isDefault = true })
        }
        list.clear()
        list.addAll(next)
        return added
    }

    public fun listIdsToString(): String {
        var re = ""
        var f = false
        for (item in list) {  // NEW-01 FIX: list là non-null, không cần !!
            if (f) {
                re += ";"
            } else {
                f = true
            }
            re += item.adsId
        }
        return re
    }

    private fun checkIdExits(id: String, listCheck: MutableList<AdsEcmp>): AdsEcmp? {
        for (item in listCheck) {
            if (item.adsId.compareTo(id) == 0) {
                return item
            }
        }
        return null
    }

}