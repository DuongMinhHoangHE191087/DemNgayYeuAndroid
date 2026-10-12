package com.app.plugin.ads.adsmax

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.PlacementDefault
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.ads.MaxAppOpenAd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task J3 (như Meta I3): `AdsMaxPlOpen` tạo `MaxAppOpenAd` mới mỗi lần load; đối tượng đã hiện xong phải `destroy()`
 * khi đóng (`onAdHidden`, sau khi bỏ tham chiếu và hạ `isShowing`, trước callback dismiss của app), không khi đang hiện.
 * `MaxAppOpenAd.destroy()` có trong applovin-sdk 13.6.2 (javap). Đối tượng đã đóng không dùng lại: lần load sau tạo
 * đối tượng mới.
 */
class AdsMaxPlOpenDismissDestroyTest {

    private class LoadCb : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "fail:$error"
        }
    }

    private class ShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            events += "dismissed"
        }
    }

    private lateinit var context: Context
    private val activity = mock(Activity::class.java)

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        AdsMaxMy.instance.readyQueue.resetForTest(); AdsMaxMy.instance.readyQueue.markReady()
    }

    @After
    fun tearDown() {
        AdsMaxMy.instance.readyQueue.resetForTest()
    }

    @Test
    fun `dong app open MAX thi destroy doi tuong da hien, khong destroy luc dang hien, lan load sau tao doi tuong moi`() {
        val listeners = mutableListOf<MaxAdListener>()
        mockConstruction(MaxAppOpenAd::class.java) { ad, _ ->
            doAnswer { listeners += it.arguments[0] as MaxAdListener; null }.`when`(ad).setListener(any())
            `when`(ad.adUnitId).thenReturn("max-open")
            `when`(ad.isReady).thenReturn(true)
        }.use { built ->
            val pl = AdsMaxPlOpen(context, mock(AdsBase::class.java), AdsIDDef.MaxFullOpen, "full_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-open-1;max-open-2")
            val state = LoadedAdFixture.state(pl)
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-open") }

            val loadCb = LoadCb()
            pl.load(PlacementDefault.PLFullSplash, loadCb)
            listeners[0].onAdLoaded(maxAd)
            assertEquals(listOf("loaded"), loadCb.events)
            val shown = built.constructed()[0]

            val showCb = ShowCb()
            assertTrue(pl.show(PlacementDefault.PLFullSplash, showCb, 0))
            verify(shown).showAd()
            listeners[0].onAdDisplayed(maxAd)
            assertEquals(listOf("showed"), showCb.events)
            assertTrue(state.isShowing)
            verify(shown, never()).destroy()

            listeners[0].onAdHidden(maxAd)
            verify(shown, times(1)).destroy()
            assertEquals("dismiss tới app đúng một lần, sau destroy", listOf("showed", "dismissed"), showCb.events)
            assertFalse(state.isShowing)
            assertNull("đối tượng đã đóng không còn là quảng cáo đã load", state.adEcmps!!.list[0].adObject)
            assertFalse(state.adEcmps!!.list[0].isLoaded)

            val loadCb2 = LoadCb()
            pl.load(PlacementDefault.PLFullSplash, loadCb2)
            assertEquals("lần load sau tạo MaxAppOpenAd mới", 2, built.constructed().size)
            listeners[1].onAdLoaded(maxAd)
            assertEquals(listOf("loaded"), loadCb2.events)
            verify(built.constructed()[1], never()).destroy()
        }
    }
}
