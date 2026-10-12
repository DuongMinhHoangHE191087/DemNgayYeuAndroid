package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsfb.AdsFbPlInters
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlBanner
import com.app.plugin.ads.adsmob.AdsMobPlInters
import com.app.plugin.logevent.ServerEventCapture
import com.facebook.ads.InterstitialAdListener
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 2.0.0 C1b (T6 m1, D48): impression và dismiss muộn của một quảng cáo mang placement của **lượt hiện quảng cáo đó**
 * ([AdsPlBase.turnPl]), không phải `showPl`/`getPl` của slot lúc callback tới. Kịch bản chung: X hiện ở lượt 1
 * (`*_home`), slot load Y và hiện ở lượt 2 (`*_settings`), callback muộn của X tới sau đó. Quét nguồn 27 site: R5 ở
 * [ShowTurnPlacementTest].
 */
class TurnCallbackPlacementTest {

    private class RecordingShowCb : AdsShowCb {
        val impressions = mutableListOf<String?>()
        val dismissals = mutableListOf<String?>()
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) {
            impressions += placement
        }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            dismissals += placement
        }
    }

    private class NoLoadCb : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }

    private lateinit var context: Context
    private val activity = mock(Activity::class.java)
    private val unit = "ca-app-pub-3940256099942544/c1b"

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
        AdsFbMy.instance.readyQueue.resetForTest(); AdsFbMy.instance.readyQueue.markReady()
    }

    @After
    fun tearDown() {
        admobQueue().resetForTest()
        AdsFbMy.instance.readyQueue.resetForTest()
    }

    @Test
    fun `I1 impression muon cua banner AdMob luot truoc mang placement luot do`() = MainHandlerTimers.run { _ ->
        val listeners = mutableListOf<AdListener>()
        mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }.use {
            mockConstruction(AdView::class.java) { view, _ ->
                doAnswer { listeners += it.arguments[0] as AdListener; null }.`when`(view).adListener = any()
                `when`(view.adUnitId).thenReturn(unit)
            }.use { built ->
                val pl = AdsMobPlBanner(context, mock(AdsBase::class.java), AdsIDDef.AdmobBanner, "banner_default")
                pl.setActivity(activity)
                pl.parserEcpms(unit)
                val cb = RecordingShowCb()
                pl.load("banner_home", NoLoadCb())
                listeners[0].onAdLoaded()
                assertTrue(pl.show("banner_home", cb, 0))
                releaseHeldAd(pl)
                pl.load("banner_settings", NoLoadCb())
                assertEquals("lượt 2 dựng view mới", 2, built.constructed().size)
                listeners[1].onAdLoaded()
                assertTrue(pl.show("banner_settings", cb, 0))

                listeners[0].onAdImpression()                       // impression muộn của view X, sau khi slot đã hiện Y
                assertEquals("I1: impression muộn của banner X mang placement lượt X", listOf<String?>("banner_home"), cb.impressions)
            }
        }
    }

    @Test
    fun `I2 impression muon cua interstitial Meta luot truoc mang placement luot do`() = MainHandlerTimers.run { _ ->
        val listeners = mutableListOf<InterstitialAdListener>()
        mockConstruction(com.facebook.ads.InterstitialAd::class.java) { ad, _ ->
            val builder = mock(com.facebook.ads.InterstitialAd.InterstitialAdLoadConfigBuilder::class.java)
            `when`(ad.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listeners += it.arguments[0] as InterstitialAdListener; builder }
            `when`(ad.show()).thenReturn(true)
        }.use { built ->
            val pl = AdsFbPlInters(context, mock(AdsBase::class.java), AdsIDDef.FBFullInters, "full_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb_c1b_unit")
            val cb = RecordingShowCb()
            pl.load("full_home", NoLoadCb())
            val x = built.constructed()[0]
            listeners[0].onAdLoaded(x)
            assertTrue(pl.show("full_home", cb, 0))
            listeners[0].onInterstitialDismissed(x)
            pl.load("full_settings", NoLoadCb())
            assertEquals("lượt 2 dựng quảng cáo mới", 2, built.constructed().size)
            listeners[1].onAdLoaded(built.constructed()[1])
            assertTrue(pl.show("full_settings", cb, 0))

            ServerEventCapture().use { server ->
                listeners[0].onLoggingImpression(x)                 // impression muộn của X (Meta)
                server.await("ad_revenue", 1)
            }
            assertEquals("I2: impression muộn của X (Meta) mang placement lượt X", listOf<String?>("full_home"), cb.impressions)
        }
    }

    /**
     * Slot thả X mà chưa nhận dismiss (controller hạ `isShowing` khi lượt show không thành, X vẫn có thể còn trên màn
     * hình); slot nạp Y và hiện ở lượt 2; dismiss của X tới sau đó.
     */
    @Test
    fun `I3 dismiss muon cua interstitial AdMob luot truoc mang placement luot do`() = MainHandlerTimers.run { _ ->
        val loads = mutableListOf<InterstitialAdLoadCallback>()
        mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }.use {
            mockStatic(InterstitialAd::class.java).use { statics ->
                statics.`when`<Unit> { InterstitialAd.load(any(Context::class.java), anyString(), any(AdRequest::class.java), any()) }
                    .thenAnswer { loads += it.arguments[3] as InterstitialAdLoadCallback; null }
                val pl = AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default")
                pl.setActivity(activity)
                pl.parserEcpms(unit)
                val cb = RecordingShowCb()
                val x = gmaAd()
                pl.load("full_home", NoLoadCb())
                loads[0].onAdLoaded(x)
                assertTrue(pl.show("full_home", cb, 0))
                val screen = ArgumentCaptor.forClass(FullScreenContentCallback::class.java)
                verify(x).fullScreenContentCallback = screen.capture()
                releaseHeldAd(pl)
                pl.load("full_settings", NoLoadCb())
                assertEquals("lượt 2 gửi request mới", 2, loads.size)
                loads[1].onAdLoaded(gmaAd())
                assertTrue(pl.show("full_settings", cb, 0))

                screen.value.onAdDismissedFullScreenContent()      // dismiss muộn của X, sau khi slot đã hiện Y
                assertEquals("I3: dismiss muộn của X mang placement lượt X", listOf<String?>("full_home"), cb.dismissals)
            }
        }
    }

    /** Slot thôi giữ quảng cáo của lượt 1 (mục trống, không đang hiện) để lượt 2 nạp quảng cáo mới. */
    private fun releaseHeldAd(pl: AdsPlBase) {
        val state = LoadedAdFixture.state(pl)
        state.adEcmps!!.list.forEach { it.isLoaded = false; it.adObject = null }
        state.isShowing = false
    }

    private fun gmaAd(): InterstitialAd = mock(InterstitialAd::class.java).also {
        `when`(it.adUnitId).thenReturn(unit)
        `when`(it.responseInfo).thenReturn(mock(ResponseInfo::class.java))
    }
}
