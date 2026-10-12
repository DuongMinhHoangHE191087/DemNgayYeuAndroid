package com.app.plugin.ads.adsfb

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsShowCb
import com.facebook.ads.InterstitialAd
import com.facebook.ads.InterstitialAdListener
import com.facebook.ads.RewardedVideoAd
import com.facebook.ads.RewardedVideoAdListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task I3 (FE3 concern): interstitial và rewarded Meta bị dismiss được `destroy()` như nhánh hết tuổi, không chỉ bỏ
 * tham chiếu; lúc đang hiện thì không. Native full Meta đã `destroy()` khi đóng (`hideNativeFull`).
 */
class AdsFbDismissDestroyTest {

    private lateinit var context: Context
    private val activity = mock(Activity::class.java)

    private class NoLoadCb : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }

    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            events += "dismissed"
        }
    }

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        AdsFbMy.instance.readyQueue.resetForTest()
        AdsFbMy.instance.readyQueue.markReady()
    }

    @After
    fun tearDown() {
        AdsFbMy.instance.readyQueue.resetForTest()
    }

    @Test
    fun `interstitial Meta dismiss thi destroy dung mot lan, dang hien thi khong`() {
        var listener: InterstitialAdListener? = null
        mockConstruction(InterstitialAd::class.java) { ad, _ ->
            val builder = mock(InterstitialAd.InterstitialAdLoadConfigBuilder::class.java)
            `when`(ad.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listener = it.arguments[0] as InterstitialAdListener; builder }
            `when`(ad.show()).thenReturn(true)
        }.use { built ->
            val pl = AdsFbPlInters(context, mock(AdsBase::class.java), AdsIDDef.FBFullInters, "full_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb_id_1;fb_id_2")
            pl.load("full_default", NoLoadCb())
            val ad = built.constructed().single()
            assertNotNull(listener)
            val l = listener!!
            l.onAdLoaded(ad)

            val cb = RecordingShowCb()
            assertTrue(pl.show("full_home", cb, 0))
            l.onInterstitialDisplayed(ad)
            verify(ad, never()).destroy()

            l.onInterstitialDismissed(ad)
            verify(ad, times(1)).destroy()
            assertEquals(listOf("showed", "dismissed"), cb.events)
        }
    }

    @Test
    fun `rewarded Meta dong thi destroy dung mot lan, dang hien thi khong`() {
        var listener: RewardedVideoAdListener? = null
        mockConstruction(RewardedVideoAd::class.java) { ad, _ ->
            val builder = mock(RewardedVideoAd.RewardedVideoAdLoadConfigBuilder::class.java)
            `when`(ad.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listener = it.arguments[0] as RewardedVideoAdListener; builder }
            `when`(ad.show()).thenReturn(true)
        }.use { built ->
            val pl = AdsFbPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.FBRewarded, "gift_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb_id_1;fb_id_2")
            pl.load("gift_default", NoLoadCb())
            val ad = built.constructed().single()
            val l = listener!!
            l.onAdLoaded(ad)

            val cb = RecordingShowCb()
            assertTrue(pl.show("gift_home", cb, 0))
            l.onLoggingImpression(ad)
            verify(ad, never()).destroy()

            l.onRewardedVideoClosed()
            verify(ad, times(1)).destroy()
            assertEquals(listOf("dismissed"), cb.events.filter { it == "dismissed" })
        }
    }
}
