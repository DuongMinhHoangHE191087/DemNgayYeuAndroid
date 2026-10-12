package com.app.plugin.ads

/**
 * Đặt một quảng cáo đã load vào placement thật, như sau `onAdLoaded` của mạng, với đồng hồ giả của
 * placement ([AdsEcmps.nowMs]): mốc load ([AdsEcmp.loadedAtMillis]) lấy từ đồng hồ đó, và test đẩy
 * [Clock.nowMs] để quảng cáo già đi thay vì sửa dữ liệu.
 */
internal object LoadedAdFixture {

    class Clock(var nowMs: Long = 1_000_000_000L)

    /** Trạng thái placement của [pl] (field `adsPlacementEcmp` là `protected`, đọc qua reflection như các test khác). */
    fun state(pl: AdsPlBase): AdsPlacementEcmpBase =
        AdsPlBase::class.java.getDeclaredField("adsPlacementEcmp").apply { isAccessible = true }.get(pl)
            as AdsPlacementEcmpBase

    /** Waterfall của [pl]. */
    fun ecmps(pl: AdsPlBase): AdsEcmps = state(pl).adEcmps!!

    fun giveLoadedAd(pl: AdsPlBase, ad: Any, clock: Clock): AdsEcmp {
        val ecmps = ecmps(pl)
        ecmps.nowMs = { clock.nowMs }
        return AdsEcmp(ecmps).apply { adsId = "unit-${pl.javaClass.simpleName}"; isLoaded = true; adObject = ad }
            .also { ecmps.list.add(it) }
    }
}
