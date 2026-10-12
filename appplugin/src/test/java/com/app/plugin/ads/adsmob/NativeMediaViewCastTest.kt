package com.app.plugin.ads.adsmob

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.NativeMediaRatio
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/**
 * 2.5.0 PLMM (Hợp đồng D, quyết định (f), Fable C4): view native của app thiếu `ad_media` hoặc `ad_media` không phải
 * `MediaView` ⇒ không gắn media, không crash, một `logE` mỗi process nêu layout id và "GMA yêu cầu MediaView". PL2 đọc
 * nguồn theo đường từ gốc repo (Legacy + Next-Gen) ⇒ exclude nextgen; PL2b chạy `populateNativeAdView` Legacy trên JVM.
 */
class NativeMediaViewCastTest {
    private lateinit var context: Context

    @Before fun setUp() {
        NativeMediaRatio.resetForTest()
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
    }

    @After fun tearDown() {
        NativeMediaRatio.resetForTest()
        LogUtil.setFlagLog(1)
    }

    @Test fun `PL2 khong ep kieu cung MediaView o native Legacy va Next-Gen`() {
        val legacy = File("src/main/java/com/app/plugin/ads/adsmob/AdsMobPlNt.kt").readText()
        val ng = File("appplugin-nextgen/src/main/java/com/app/plugin/ads/adsmob/AdsMobPlNt.kt").readText()
        val hard = listOf(Regex("""\)\s+as\s+MediaView\b""").containsMatchIn(legacy), Regex("""val\s+mediaView:\s*MediaView\s*=""").containsMatchIn(ng))
        assertEquals("ad_media vắng/không phải MediaView: `as? MediaView` (Legacy), `MediaView?` (Next-Gen)", listOf(false, false), hard)
    }

    /** `populateNativeAdView` Legacy với `ad_media` = [media] (null = layout không có id đó); trả (view, NativeAdView, lỗi). */
    private fun populate(media: View?): Triple<View?, NativeAdView, Throwable?> {
        val adView = mock(NativeAdView::class.java)
        `when`(adView.findViewById<View>(anyInt())).thenReturn(mock(TextView::class.java))
        `when`(adView.findViewById<View>(R.id.ad_media)).thenReturn(media)
        val root = mock(View::class.java)
        `when`(root.findViewById<View>(R.id.ad_native_view_lo)).thenReturn(adView)
        val inflater = mock(LayoutInflater::class.java)
        `when`(inflater.inflate(anyInt(), any<ViewGroup>(), anyBoolean())).thenReturn(root)
        val pl = AdsMobPlNt(context, mock(AdsBase::class.java), AdsIDDef.AdmobNative, "nt_default")
        val ecmp = mock(AdsEcmp::class.java).also { `when`(it.adObject).thenReturn(mock(NativeAd::class.java)) }
        return mockStatic(LayoutInflater::class.java).use { inflaters ->
            inflaters.`when`<LayoutInflater> { LayoutInflater.from(any()) }.thenReturn(inflater)
            val r = runCatching { pl.populateNativeAdView(ecmp) }
            Triple(r.getOrNull(), adView, r.exceptionOrNull())
        }
    }

    @Test fun `PL2b Legacy ad_media vang hoac sai kieu - dung view, khong gan media, mot logE moi process`() {
        val layout = AdsHelper.instance.getLayoutCustome4Native(AdsIDDef.AdmobNative)
        for ((label, media) in listOf("vắng" to null, "TextView" to mock(TextView::class.java))) {
            NativeMediaRatio.resetForTest()
            mockStatic(Log::class.java).use { log ->
                val (view, adView, error) = populate(media)
                assertNull("ad_media $label: không ném", error)
                assertNotNull("ad_media $label: vẫn dựng view", view)
                verify(adView).mediaView = null
                verify(adView).setNativeAd(any())
                populate(media)
                log.verify({ Log.e(eq(LogUtil.TAG), contains("GMA yêu cầu MediaView")) }, times(1))
                log.verify({ Log.e(eq(LogUtil.TAG), contains("0x" + Integer.toHexString(layout))) }, times(1))
            }
        }
    }

    @Test fun `PL2c Legacy ad_media la MediaView - gan nhu cu, khong logE`() {
        mockStatic(Log::class.java).use { log ->
            val media = mock(MediaView::class.java)
            val (view, adView, error) = populate(media)
            assertNull(error)
            assertNotNull(view)
            verify(adView).mediaView = media
            log.verify({ Log.e(eq(LogUtil.TAG), contains("GMA yêu cầu MediaView")) }, times(0))
        }
    }
}
