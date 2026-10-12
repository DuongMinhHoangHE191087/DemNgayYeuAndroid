package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBaseBanner
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsBaseNative
import com.app.plugin.ads.MaxBridge
import com.app.plugin.consent.GppSignals

/**
 * Impl của [MaxBridge] (2.0.0 C1c): uỷ quyền nguyên văn cho [AdsMaxMy.instance]. Core không tham chiếu lớp này; nó
 * được dò bằng tên cố định [MaxBridge.IMPL_CLASS] (đổi tên/gói là phải đổi hằng đó và keep rule trong
 * `consumer-rules.pro`). Cần constructor không tham số. Lớp duy nhất mới trong `adsmax/` của C1c.
 */
internal class MaxBridgeImpl : MaxBridge, MaxBridge.Networks {
    private val max: AdsMaxMy get() = AdsMaxMy.instance

    override fun initAd(context: Context) = max.initAd(context)

    override fun startNetwork(context: Context) = max.startNetwork(context)

    override fun followGpp(context: Context, gpp: GppSignals?) = max.followGpp(context, gpp)

    override fun resetForTest() = max.resetForTest()

    override fun getAdnetName(adnet: String?): String = max.getAdnetName(adnet)

    override fun networks(): MaxBridge.Networks = this

    override fun getAdsBanner(type: Int): AdsBaseBanner? = max.getAdsBanner(type)

    override fun getAdsNtCl(type: Int): AdsBaseBanner? = max.getAdsNtCl(type)

    override fun getAdsRect(type: Int): AdsBaseBanner? = max.getAdsRect(type)

    override fun getAdsNative(type: Int): AdsBaseNative? = max.getAdsNative(type)

    override fun getAdsFull(type: Int): AdsBaseFull? = max.getAdsFull(type)

    override fun getAdsGift(type: Int): AdsBaseGift? = max.getAdsGift(type)
}
