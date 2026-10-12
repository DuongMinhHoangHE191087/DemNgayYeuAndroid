package com.app.plugin.nextgen

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.NativeMediaRatio
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlNt
import com.app.plugin.ads.adsmob.AdsMobPlNtCl
import com.app.plugin.ads.adsmob.AdsMobPlNtFull
import com.app.plugin.ads.core.UnitGate
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd.NativeMediaAspectRatio
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.4.4 ASPECT (Hợp đồng B, quyết định (b)) — Next-Gen: `AdsMobPlNt.request` (Nt + NtCl) và `AdsMobPlNtFull` ánh xạ mã
 * [NativeMediaRatio] sang `NativeAd.NativeMediaAspectRatio`: vắng ⇒ ANY; `ads_cf_native_media_ratio` = 1 + máy dọc ⇒
 * PORTRAIT (2.4.3); 2 ⇒ LANDSCAPE kể cả máy dọc. Bản Legacy: `adsmob/NativeRatioRequestTest`.
 */
class NgNativeRatioTest {
    private val requests = ArrayList<NativeAdRequest>()
    private val savedNt = AdsMobPlNt.loader
    private val savedFull = AdsMobPlNtFull.loader

    private class Cb : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }

    @Before fun setUp() {
        UnitGate.resetForTest() // 2.4.4 INFLIGHT: cổng unit là trạng thái tĩnh của process
        NativeMediaRatio.resetForTest()
        sdkReady(true)
        AdsMobPlNt.loader = { r: NativeAdRequest, _: NativeAdLoaderCallback -> requests += r }
        AdsMobPlNtFull.loader = { r: NativeAdRequest, _: NativeAdLoaderCallback -> requests += r }
        MainPost.isMain = { true }
    }

    @After fun tearDown() {
        AdsMobPlNt.loader = savedNt
        AdsMobPlNtFull.loader = savedFull
        sdkReady(false)
        MainPost.resetForTest()
        NativeMediaRatio.resetForTest()
        UnitGate.resetForTest() // slot không callback ở đây: không để đặt chỗ sống sang test sau
    }

    private fun sdkReady(ready: Boolean) {
        val my = AdsMobMy.instance
        AdsMobMy::class.java.getDeclaredField("state").apply { isAccessible = true }.setInt(my, if (ready) 1 else 0)
        (AdsMobMy::class.java.getDeclaredField("queue").apply { isAccessible = true }.get(my) as MutableCollection<*>).clear()
    }

    /** Context app có `@integer/ads_cf_native_media_ratio` = [raw] (`null` = vắng). */
    private fun context(raw: Int?): Context {
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(res.displayMetrics).thenReturn(DisplayMetrics())
        if (raw != null) {
            `when`(res.getIdentifier(eq(NativeMediaRatio.RES_KEY), eq("integer"), eq(PKG))).thenReturn(RES_ID)
            `when`(res.getInteger(RES_ID)).thenReturn(raw)
        }
        return mock(Context::class.java).also {
            `when`(it.resources).thenReturn(res)
            `when`(it.packageName).thenReturn(PKG)
            `when`(it.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        }
    }

    private fun activity(o: Int): Activity {
        val res = mock(Resources::class.java)
        `when`(res.configuration).thenReturn(Configuration().apply { orientation = o })
        return mock(Activity::class.java).also { `when`(it.resources).thenReturn(res) }
    }

    /** Tỉ lệ của request Nt, NtCl, NtFull (theo thứ tự) trên Activity hướng [o] (mặc định dọc) với resource [raw]. */
    private fun ratios(raw: Int?, o: Int = Configuration.ORIENTATION_PORTRAIT): List<NativeMediaAspectRatio?> {
        requests.clear()
        val c = context(raw)
        listOf<Pair<AdsPlBase, String>>(
            AdsMobPlNt(c, mock(AdsBase::class.java), AdsIDDef.AdmobNative, "nt_default") to "nt_home",
            AdsMobPlNtCl(c, mock(AdsBase::class.java), AdsIDDef.AdmobNtCl, "ntcl_default") to "ntcl_home",
            AdsMobPlNtFull(c, mock(AdsBase::class.java), AdsIDDef.AdmobFullNt, "full_2_default") to "full_2_default",
        ).forEach { (pl, placement) ->
            UnitGate.resetForTest() // test về tỉ lệ, không về cổng: ba slot cùng UNIT không callback, mỗi slot tự gửi
            pl.setActivity(activity(o)); pl.parserEcpms(UNIT); pl.load(placement, Cb())
        }
        return requests.map { it.mediaAspectRatio }
    }

    @Test fun `AS5 Next-Gen may doc resource vang thi ANY cho Nt NtCl NtFull`() =
        assertEquals(List(3) { NativeMediaAspectRatio.ANY }, ratios(null))

    @Test fun `AS5b Next-Gen raw 1 may doc thi PORTRAIT, raw 2 thi LANDSCAPE cho Nt NtCl NtFull`() {
        assertEquals("raw 1 (theo hướng, 2.4.3) + máy dọc", List(3) { NativeMediaAspectRatio.PORTRAIT }, ratios(1))
        assertEquals("raw 2 ép LANDSCAPE dù máy dọc", List(3) { NativeMediaAspectRatio.LANDSCAPE }, ratios(2))
    }

    @Test fun `AS5c Next-Gen raw 1 may ngang thi LANDSCAPE cho Nt NtCl NtFull`() =
        assertEquals("raw 1 (theo hướng, núm lùi 2.4.3) + máy ngang", List(3) { NativeMediaAspectRatio.LANDSCAPE },
            ratios(1, Configuration.ORIENTATION_LANDSCAPE))

    private companion object {
        const val PKG = "com.example.app"
        const val RES_ID = 0x7f0b0042
        const val UNIT = "ca-app-pub-3940256099942544/2247696110"
    }
}
