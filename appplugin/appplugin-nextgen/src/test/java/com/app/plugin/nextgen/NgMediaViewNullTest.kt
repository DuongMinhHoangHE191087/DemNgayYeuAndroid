package com.app.plugin.nextgen

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.DisplayMetrics
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
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.NativeMediaRatio
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlNt
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 2.5.0 PLMM (Hợp đồng D, Fable C4) Next-Gen: layout native của app không có `ad_media` hoặc `ad_media` không phải
 * `MediaView` ⇒ vẫn dựng view, `registerNativeAd(ad, null)` (tham số 2 `@Nullable`, javap ads-mobile-sdk 1.5.0), không
 * ném; một `logE` mỗi process nêu layout id và "GMA yêu cầu MediaView". Harness như `NgNativeTest`.
 */
class NgMediaViewNullTest {
    private lateinit var context: Context
    private val activity = mock(Activity::class.java)
    private val requests = ArrayList<Pair<NativeAdRequest, NativeAdLoaderCallback>>()
    private val savedLoader = AdsMobPlNt.loader
    private val savedInflater = AdsMobPlNt.inflater

    private class Cb : AdsShowCb, AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }

    @Before fun setUp() {
        NativeMediaRatio.resetForTest()
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(resources.displayMetrics).thenReturn(DisplayMetrics())
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        sdkReady(true)
        AdsMobPlNt.loader = { r, cb -> requests += r to cb }
        MainPost.isMain = { true }
    }

    @After fun tearDown() {
        AdsMobPlNt.loader = savedLoader
        AdsMobPlNt.inflater = savedInflater
        sdkReady(false)
        MainPost.resetForTest()
        NativeMediaRatio.resetForTest()
        LogUtil.setFlagLog(1)
    }

    private fun sdkReady(ready: Boolean) {
        val my = AdsMobMy.instance
        AdsMobMy::class.java.getDeclaredField("state").apply { isAccessible = true }.setInt(my, if (ready) 1 else 0)
        (AdsMobMy::class.java.getDeclaredField("queue").apply { isAccessible = true }.get(my) as MutableCollection<*>).clear()
    }

    /** Inflater JVM: `NativeAdView` có `ad_media` = [media] (null = layout không có id đó). */
    private fun inflater(adView: NativeAdView, media: View?): LayoutInflater {
        `when`(adView.findViewById<View>(anyInt())).thenReturn(mock(TextView::class.java))
        `when`(adView.findViewById<View>(R.id.ad_media)).thenReturn(media)
        val root = mock(View::class.java)
        `when`(root.findViewById<View>(anyInt())).thenReturn(adView)
        return mock(LayoutInflater::class.java).also { `when`(it.inflate(anyInt(), any<ViewGroup>(), anyBoolean())).thenReturn(root) }
    }

    /** Một lượt `nt_home` nạp xong rồi `getAdForShow`; trả (view, quảng cáo, lỗi ném ra). */
    private fun showOnce(adView: NativeAdView, media: View?): Triple<Any?, NativeAd, Throwable?> {
        AdsMobPlNt.inflater = { inflater(adView, media) }
        val pl = AdsMobPlNt(context, mock(AdsBase::class.java), AdsIDDef.AdmobNative, "nt_default").also { it.setActivity(activity) }
        pl.parserEcpms(UNIT)
        pl.load("nt_home", Cb())
        val ad = mock(NativeAd::class.java)
        requests.last().second.onNativeAdLoaded(ad)
        val r = runCatching { pl.getAdForShow("nt_home", Cb()) }
        return Triple(r.getOrNull(), ad, r.exceptionOrNull())
    }

    @Test fun `PL3 Next-Gen ad_media vang hoac sai kieu - dung view, registerNativeAd voi null, mot logE moi process`() {
        val layout = AdsHelper.instance.getLayoutCustome4Native(AdsIDDef.AdmobNative)
        for ((label, media) in listOf("vắng" to null, "TextView" to mock(TextView::class.java))) {
            NativeMediaRatio.resetForTest()
            mockStatic(Log::class.java).use { log ->
                val adView = mock(NativeAdView::class.java)
                val (shown, ad, error) = showOnce(adView, media)
                assertNull("ad_media $label: không ném", error)
                assertNotNull("ad_media $label: vẫn có view để hiện", shown)
                verify(adView, times(1)).registerNativeAd(ad, null)
                showOnce(mock(NativeAdView::class.java), media)
                log.verify({ Log.e(eq(LogUtil.TAG), contains("GMA yêu cầu MediaView")) }, times(1))
                log.verify({ Log.e(eq(LogUtil.TAG), contains("0x" + Integer.toHexString(layout))) }, times(1))
            }
        }
    }

    @Test fun `PL3b Next-Gen ad_media la MediaView - registerNativeAd voi media, khong logE`() {
        mockStatic(Log::class.java).use { log ->
            val adView = mock(NativeAdView::class.java)
            val media = mock(MediaView::class.java)
            val (_, ad, error) = showOnce(adView, media)
            assertNull(error)
            verify(adView, times(1)).registerNativeAd(ad, media)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("GMA yêu cầu MediaView")) }, times(0))
        }
    }

    private companion object {
        const val UNIT = "ca-app-pub-3940256099942544/2247696110"
    }
}
