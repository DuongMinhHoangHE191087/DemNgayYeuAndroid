package com.app.plugin.ads

import android.content.Context
import android.os.Bundle
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest

/**
 * Banner GMA collapsible theo cấu hình (1.9.0 Task 3, Hợp đồng D; D4, D23, D26).
 *
 * Resource app `ads_collapsible_placements` (string-array tên slot `bn_` của `admob_banner`): một mục không bắt đầu
 * `bn_` hoặc không có trong `admob_banner` → bỏ **cả** danh sách, một logE (D23). Request của slot có tên, tới lần nạp
 * thành công đầu tiên của slot (D37), mang extras `collapsible=bottom`. 2.4.0 NG2 (GMA Next-Gen): extras đi qua
 * `setGoogleExtrasBundle` [S12] của [AdRequestFactory.banner], cùng Bundle với `npa=1` (consent không đổi); "chỉ cho
 * Google demand, mediation hiện như banner thường" [S12].
 */
internal object CollapsibleBanner {
    private const val RESOURCE = "ads_collapsible_placements"
    private const val PREFIX = "bn_"

    /** Danh sách đã đọc trong process (`null` = chưa đọc); đọc một lần để danh sách sai chỉ logE một lần. */
    @Volatile
    private var placements: Set<String>? = null

    /** [names] của resource (`null` = app không khai) so với các slot [known] của `admob_banner`; sai → rỗng + logE. */
    fun parse(names: Array<String>?, known: Set<String>): Set<String> {
        if (names == null) return emptySet()
        val bad = names.filter { !it.startsWith(PREFIX) || it !in known }
        if (bad.isEmpty()) return names.toSet()
        LogUtil.logE("ads collapsible: resource $RESOURCE ignored (not a bn_ slot of admob_banner: $bad)")
        return emptySet()
    }

    private fun listed(context: Context): Set<String> = placements ?: try {
        val res = context.resources
        val id = res.getIdentifier("ads_collapsible_placements", "array", context.packageName) // chữ: KeepXmlTest quét
        val names = if (id == 0) null else res.getStringArray(id)
        val known = AppUtil.getResourceString(context, ResIdDefault.AdmobBannerDf)
            .split("#").map { it.substringBefore(",").trim() }.filter { it.isNotEmpty() }.toSet()
        parse(names, known)
    } catch (e: Exception) {
        LogUtil.logE("ads collapsible: resource $RESOURCE not readable: ${e.message}")
        emptySet()
    }.also { placements = it }

    /** `BannerAdRequest` [size] cho slot [placement]; extras collapsible chỉ khi slot có tên và [beforeFirstLoad]. */
    fun request(context: Context, placement: String, beforeFirstLoad: Boolean, adUnitId: String, size: AdSize): BannerAdRequest {
        if (!beforeFirstLoad || placement !in listed(context)) return AdRequestFactory.banner(context, adUnitId, size)
        val extras = Bundle()
        extras.putString("collapsible", "bottom")
        LogUtil.logD("ads collapsible: $placement request before first load collapsible=bottom")
        return AdRequestFactory.banner(context, adUnitId, size, extras)
    }

    @JvmSynthetic
    internal fun resetForTest() {
        placements = null
    }
}
