package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsfb.AdsFbPlRect
import com.app.plugin.ads.adsmax.AdsMaxMy
import com.app.plugin.ads.adsmax.AdsMaxPlRect
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlRect
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.sdk.AppLovinSdkUtils
import com.facebook.ads.Ad
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

/**
 * Final review Minor 6 (1.5.0 Task 15): rect A đã lấy ra hiện (`getAdForShow` bỏ `adObject`), slot rảnh, A tự refresh
 * (nhánh "not turn"/"refresh" của listener thật) → mục trống nhận A mà không đặt `isLoaded` (`adObject = A`,
 * `isLoaded = false`). Đường `load` bỏ tham chiếu đó từ 1.4.0 (`releaseUnloadedItemRefs`, `LateBannerLoadCallbackTest`);
 * đường `getAdForShow` thì chưa: lần lấy ra kế tiếp đi `AdsEcmps.getAdLoaded` gặp mục đó và phát
 * `ad_show_fail_reason = load_fail_but_ads_not_null` giả. Kỳ vọng: không phát, trả `null` (không có quảng cáo đã load),
 * mục trống (không destroy A — đang hiện). Một test mỗi lớp rect (GMA, MAX, Meta).
 */
class RectGetAdForShowSquatTest {

    private val gmaUnit = "ca-app-pub-3940256099942544/first"

    private class Callback : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "fail:$error"
        }
    }

    private lateinit var context: Context
    private val activity = mock(Activity::class.java)

    private fun admobQueue(): SdkReadyQueue =
        AdsMobMy::class.java.getDeclaredField("readyQueue").apply { isAccessible = true }.get(AdsMobMy.instance) as SdkReadyQueue

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        admobQueue().resetForTest(); admobQueue().markReady()
        AdsMaxMy.instance.readyQueue.resetForTest(); AdsMaxMy.instance.readyQueue.markReady()
        AdsFbMy.instance.readyQueue.resetForTest(); AdsFbMy.instance.readyQueue.markReady()
    }

    @After
    fun tearDown() {
        admobQueue().resetForTest()
        AdsMaxMy.instance.readyQueue.resetForTest()
        AdsFbMy.instance.readyQueue.resetForTest()
    }

    /**
     * Tiền đề: A đã lấy ra, refresh lúc slot rảnh cho mục `adObject = A`, `isLoaded = false`. Rồi `getAdForShow` lần hai
     * trong khi bắt event server. Trả danh sách vi phạm (rỗng = đúng).
     */
    private fun squatThenGetAgainViolations(pl: AdsPlBaseBn, viewA: Any, refreshA: () -> Unit): List<String> {
        val state = LoadedAdFixture.state(pl)
        val item = state.adEcmps!!.list[0]
        assertFalse("slot rảnh", state.isLoading)
        refreshA()
        assertSame("tiền đề: refresh lúc slot rảnh gán A vào mục trống", viewA, item.adObject)
        assertFalse(item.isLoaded)

        val violations = mutableListOf<String>()
        ServerEventCapture().use { server ->
            val got = pl.getAdForShow("rect_home", null)
            if (got != null) violations += "getAdForShow trả $got thay vì null"
            LogEventServer.logAdShowFailWithReason(placement = "sentinel", reason = "sentinel")
            val squat = server.await("ad_show_fail", 1)
                .map { it.get("reason").asString }
                .filter { it == Constants.AdShowFailReason.LOAD_FAIL_BUT_ADS_NOT_NULL }
            if (squat.isNotEmpty()) violations += "phát ${squat.size} event ${Constants.AdShowFailReason.LOAD_FAIL_BUT_ADS_NOT_NULL}"
        }
        if (item.adObject != null) violations += "mục vẫn giữ ${if (item.adObject === viewA) "A" else "view khác"}"
        return violations
    }

    private fun <T> withGmaViews(block: (built: org.mockito.MockedConstruction<AdView>, listeners: MutableList<AdListener>) -> T): T {
        val listeners = mutableListOf<AdListener>()
        return mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }.use {
            mockConstruction(AdView::class.java) { view, _ ->
                doAnswer { listeners += it.arguments[0] as AdListener; null }.`when`(view).adListener = any()
                `when`(view.adUnitId).thenReturn(gmaUnit)
            }.use { built -> block(built, listeners) }
        }
    }

    private fun <T> withMaxViews(block: (built: org.mockito.MockedConstruction<com.applovin.mediation.ads.MaxAdView>, listeners: MutableList<MaxAdViewAdListener>) -> T): T {
        val listeners = mutableListOf<MaxAdViewAdListener>()
        return mockStatic(AppLovinSdkUtils::class.java).use {
            mockConstruction(com.applovin.mediation.ads.MaxAdView::class.java) { view, _ ->
                doAnswer { listeners += it.arguments[0] as MaxAdViewAdListener; null }.`when`(view).setListener(any())
                `when`(view.adUnitId).thenReturn("max-view")
            }.use { built -> block(built, listeners) }
        }
    }

    private fun <T> withFbViews(block: (built: org.mockito.MockedConstruction<com.facebook.ads.AdView>, listeners: MutableList<com.facebook.ads.AdListener>) -> T): T {
        val listeners = mutableListOf<com.facebook.ads.AdListener>()
        return mockConstruction(com.facebook.ads.AdView::class.java) { view, _ ->
            val builder = mock(com.facebook.ads.AdView.AdViewLoadConfigBuilder::class.java)
            `when`(view.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listeners += it.arguments[0] as com.facebook.ads.AdListener; builder }
            `when`(builder.build()).thenReturn(mock(com.facebook.ads.AdView.AdViewLoadConfig::class.java))
        }.use { built -> block(built, listeners) }
    }

    @Test
    fun `GMA rect - getAdForShow lan hai khi refresh chiem muc - khong LOAD_FAIL_BUT_ADS_NOT_NULL, tra null, muc trong`() = MainHandlerTimers.run { _ ->
        withGmaViews { built, listeners ->
            val pl = AdsMobPlRect(context, mock(AdsBase::class.java), AdsIDDef.AdmobRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms(gmaUnit)
            pl.load("rect_home", Callback())
            listeners[0].onAdLoaded()
            pl.getAdForShow("rect_home", null)                 // A lên màn hình
            assertEquals(emptyList<String>(), squatThenGetAgainViolations(pl, built.constructed()[0]) { listeners[0].onAdLoaded() })
        }
    }

    @Test
    fun `MAX rect - getAdForShow lan hai khi refresh chiem muc - khong LOAD_FAIL_BUT_ADS_NOT_NULL, tra null, muc trong`() = MainHandlerTimers.run { _ ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlRect(context, mock(AdsBase::class.java), AdsIDDef.MaxRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-rect-1")
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("rect_home", Callback())
            listeners[0].onAdLoaded(maxAd)
            pl.getAdForShow("rect_home", null)
            assertEquals(emptyList<String>(), squatThenGetAgainViolations(pl, built.constructed()[0]) { listeners[0].onAdLoaded(maxAd) })
        }
    }

    @Test
    fun `Meta rect - getAdForShow lan hai khi refresh chiem muc - khong LOAD_FAIL_BUT_ADS_NOT_NULL, tra null, muc trong`() = MainHandlerTimers.run { _ ->
        withFbViews { built, listeners ->
            val pl = AdsFbPlRect(context, mock(AdsBase::class.java), AdsIDDef.FBRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb-rect-1")
            pl.load("rect_home", Callback())
            val viewA = built.constructed()[0]
            listeners[0].onAdLoaded(viewA as Ad)
            pl.getAdForShow("rect_home", null)
            assertEquals(emptyList<String>(), squatThenGetAgainViolations(pl, viewA) { listeners[0].onAdLoaded(viewA as Ad) })
        }
    }
}
