package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.MainHandlerTimers
import com.app.plugin.ads.NativeMediaRatio
import com.app.plugin.ads.SdkReadyQueue
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MediaAspectRatio
import com.google.android.gms.ads.nativead.NativeAdOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings

/**
 * 2.4.4 ASPECT (Hợp đồng B, quyết định (b)) — ba chỗ dựng request native Legacy (Nt, NtCl, NtFull) xin tỉ lệ media
 * của [NativeMediaRatio]: mặc định `MediaAspectRatio.ANY`, `ads_cf_native_media_ratio` lùi được. Bắt
 * `NativeAdOptions.Builder.setMediaAspectRatio` qua `mockConstruction` (GMA Legacy ⇒ loại khỏi nextgen).
 */
class NativeRatioRequestTest {
    private class NoopCb : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }

    private fun queue() = AdsMobMy::class.java.getDeclaredField("readyQueue").apply { isAccessible = true }.get(AdsMobMy.instance) as SdkReadyQueue

    @Before fun setUp() { NativeMediaRatio.resetForTest(); queue().resetForTest(); queue().markReady() }

    @After fun tearDown() { queue().resetForTest(); NativeMediaRatio.resetForTest() }

    /** Context app có `@integer/ads_cf_native_media_ratio` = [raw] (`null` = vắng). */
    private fun context(raw: Int?): Context {
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        if (raw != null) {
            `when`(res.getIdentifier(eq(NativeMediaRatio.RES_KEY), eq("integer"), eq(PKG))).thenReturn(RES_ID)
            `when`(res.getInteger(RES_ID)).thenReturn(raw)
        }
        return mock(Context::class.java).also {
            `when`(it.resources).thenReturn(res)
            `when`(it.packageName).thenReturn(PKG)
            `when`(it.applicationContext).thenReturn(it)
            `when`(it.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        }
    }

    private fun nt(c: Context): AdsPlBase = AdsMobPlNt(c, mock(AdsBase::class.java), AdsIDDef.AdmobNative, "nt_default")
    private fun ntCl(c: Context): AdsPlBase = AdsMobPlNtCl(c, mock(AdsBase::class.java), AdsIDDef.AdmobNtCl, "ntcl_default")
    private fun ntFull(c: Context): AdsPlBase = AdsMobPlNtFull(c, mock(AdsBase::class.java), AdsIDDef.AdmobFullNt, "full_2_default")

    /** Một lượt nạp thật (`load` → `tryLoad`) trên Activity [orientation]; trả các giá trị `setMediaAspectRatio` đã gọi. */
    private fun ratios(raw: Int?, orientation: Int = Configuration.ORIENTATION_PORTRAIT, make: (Context) -> AdsPlBase): List<Int> =
        MainHandlerTimers.run {
            val seen = mutableListOf<Int>()
            val activity = mock(Activity::class.java)
            val res = mock(Resources::class.java)
            `when`(activity.resources).thenReturn(res)
            `when`(res.configuration).thenReturn(Configuration().apply { this.orientation = orientation })
            mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }.use {
                mockConstruction(NativeAdOptions.Builder::class.java, withSettings().defaultAnswer(Answers.RETURNS_SELF)) { b, _ ->
                    `when`(b.setMediaAspectRatio(anyInt())).thenAnswer { seen += it.arguments[0] as Int; b }
                }.use {
                    mockConstruction(AdLoader.Builder::class.java, withSettings().defaultAnswer(Answers.RETURNS_SELF)) { b, _ ->
                        `when`(b.build()).thenReturn(mock(AdLoader::class.java))
                    }.use {
                        val pl = make(context(raw)); pl.setActivity(activity); pl.parserEcpms(UNIT)
                        pl.load("nt_home", NoopCb())
                    }
                }
            }
            seen
        }

    @Test fun `AS1 native nt may doc resource vang thi ANY`() {
        val got = mapOf("nt" to ratios(null, make = ::nt), "ntcl" to ratios(null, make = ::ntCl))
        assertEquals("native nt_/NtCl máy dọc, ads_cf_native_media_ratio vắng ⇒ ANY (${MediaAspectRatio.ANY})",
            mapOf("nt" to listOf(MediaAspectRatio.ANY), "ntcl" to listOf(MediaAspectRatio.ANY)), got)
    }

    @Test fun `AS2 NtFull Legacy may doc resource vang thi ANY, raw 1 thi PORTRAIT nhu 2_4_3`() {
        assertEquals("Full2 (NtFull) máy dọc, resource vắng ⇒ ANY", listOf(MediaAspectRatio.ANY), ratios(null, make = ::ntFull))
        assertEquals("Full2 máy dọc, raw 1 (theo hướng) ⇒ PORTRAIT", listOf(MediaAspectRatio.PORTRAIT), ratios(1, make = ::ntFull))
    }

    @Test fun `AS3 ma tra ve trung MediaAspectRatio Legacy, bang raw x huong qua request Nt`() {
        val land = Configuration.ORIENTATION_LANDSCAPE
        val got = listOf(null, 1, 2, 3).associateWith { raw -> ratios(raw, make = ::nt) + ratios(raw, land, ::nt) }
        val want = mapOf(
            null to listOf(MediaAspectRatio.ANY, MediaAspectRatio.ANY),
            1 to listOf(MediaAspectRatio.PORTRAIT, MediaAspectRatio.LANDSCAPE),
            2 to listOf(MediaAspectRatio.LANDSCAPE, MediaAspectRatio.LANDSCAPE),
            3 to listOf(MediaAspectRatio.PORTRAIT, MediaAspectRatio.PORTRAIT),
        )
        assertEquals("[dọc, ngang] theo raw, mã = hằng MediaAspectRatio Legacy", want, got)
    }

    @Test fun `AS3b raw 1 may ngang thi LANDSCAPE cho Nt NtCl NtFull`() {
        val land = Configuration.ORIENTATION_LANDSCAPE
        assertEquals("raw 1 (theo hướng, núm lùi 2.4.3) + máy ngang ⇒ LANDSCAPE ở cả ba chỗ Legacy",
            List(3) { MediaAspectRatio.LANDSCAPE }, listOf(::nt, ::ntCl, ::ntFull).flatMap { ratios(1, land, it) })
    }

    @Test fun `AS4 ba request Nt NtCl NtFull chi mot dong Log i ads native media ratio`() {
        mockStatic(Log::class.java).use { log ->
            ratios(null, make = ::nt); ratios(null, make = ::ntCl); ratios(null, make = ::ntFull)
            log.verify({ Log.i(eq(LogUtil.TAG), startsWith("ads native media ratio:")) }, times(1))
            log.verify({ Log.i(LogUtil.TAG, "ads native media ratio: ANY (ads_cf_native_media_ratio=0)") }, times(1))
        }
    }

    private companion object {
        const val PKG = "com.example.app"
        const val RES_ID = 0x7f0b0042
        const val UNIT = "ca-app-pub-3940256099942544/2247696110"
    }
}
