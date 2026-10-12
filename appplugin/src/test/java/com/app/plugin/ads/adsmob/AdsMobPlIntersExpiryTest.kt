package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.SdkReadyQueue
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.google.android.gms.ads.interstitial.InterstitialAd
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * D5 (DOC/66): interstitial AdMob đã load quá 60 phút ([AdsPolicy.fullscreenMaxAgeMillis], khoá
 * `fullscreen_max_age_minutes`) là chưa load, như app open quá 4 giờ (mẫu: `AdsMobPlOpenExpiryTest`). GMA: *"ads
 * expire after an hour"* (developers.google.com/admob/android/interstitial). Quảng cáo bị bỏ với
 * `ad_show_fail_reason` = `ad_expired`; `show` đi nhánh "show not loaded" (trả `false`, không callback).
 */
class AdsMobPlIntersExpiryTest {

    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
    }

    private class RecordingLoadCb : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded:$adId"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "failed:$error"
        }
    }

    private lateinit var context: Context
    private val clock = LoadedAdFixture.Clock()
    private val activity = mock(Activity::class.java)
    private val ad = mock(InterstitialAd::class.java)

    /** Hàng chờ của AdMob là `private`: load chờ SDK trong test phải được bỏ, không để lại cho test khác. */
    private fun admobQueue(): SdkReadyQueue =
        AdsMobMy::class.java.getDeclaredField("readyQueue").apply { isAccessible = true }.get(AdsMobMy.instance) as SdkReadyQueue

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        admobQueue().resetForTest()
    }

    @After
    fun tearDown() {
        AdsPolicy.current = AdsPolicy.DEFAULT
        admobQueue().resetForTest()
    }

    private val sixtyMin = 60 * 60_000L

    private fun intersWithAd(): Pair<AdsMobPlInters, AdsEcmp> {
        val pl = AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default")
        pl.setActivity(activity)
        return pl to LoadedAdFixture.giveLoadedAd(pl, ad, clock)
    }

    @Test
    fun `show - qua 60 phut thi khong hien, tra false, khong callback, gui ad_expired, quang cao bi bo`() {
        val (pl, item) = intersWithAd()
        clock.nowMs = item.loadedAtMillis + sixtyMin + 1
        val cb = RecordingShowCb()

        ServerEventCapture().use { server ->
            assertFalse(pl.show("full_home", cb, 0))
            val reasons = server.showFails(2).map { it.second }
            assertTrue("phải có ad_expired: $reasons", Constants.AdShowFailReason.AD_EXPIRED in reasons)
        }

        assertEquals(emptyList<String>(), cb.events)
        verify(ad, never()).show(any(Activity::class.java))
        assertFalse(item.isLoaded)
        assertNull(item.adObject)
    }

    @Test
    fun `show - 59 phut thi hien nhu cu`() {
        val (pl, item) = intersWithAd()
        clock.nowMs = item.loadedAtMillis + 59 * 60_000L

        assertTrue(pl.show("full_home", RecordingShowCb(), 0))

        verify(ad).show(activity)
    }

    @Test
    fun `hasLoadedAd - 59 phut true, qua 60 phut false`() {
        val (pl, item) = intersWithAd()
        clock.nowMs = item.loadedAtMillis + sixtyMin - 1
        assertTrue(pl.hasLoadedAd())
        clock.nowMs = item.loadedAtMillis + sixtyMin
        assertFalse("đúng 60 phút là hết hạn", pl.hasLoadedAd())
    }

    @Test
    fun `load - qua 60 phut thi tai moi, khong bao onLoaded quang cao cu`() {
        val (pl, item) = intersWithAd()
        clock.nowMs = item.loadedAtMillis + sixtyMin + 1
        val cb = RecordingLoadCb()

        pl.load("full_home", cb)

        assertEquals("quảng cáo hết hạn không được báo là đã load", emptyList<String>(), cb.events)
        assertTrue("phải vào tryLoad (chờ SDK AdMob)", LoadedAdFixture.state(pl).isLoading)
        assertFalse(item.isLoaded)
        assertNull(item.adObject)
    }

    @Test
    fun `load - 59 phut bao onLoaded, khong tai lai`() {
        val (pl, item) = intersWithAd()
        clock.nowMs = item.loadedAtMillis + 59 * 60_000L
        val cb = RecordingLoadCb()

        pl.load("full_home", cb)

        assertEquals(listOf("loaded:${item.adsId}"), cb.events)
        assertFalse(LoadedAdFixture.state(pl).isLoading)
    }

    @Test
    fun `tuoi toi da doc tu fullscreen_max_age_minutes cua ads_policy_android`() {
        AdsPolicy.current = AdsPolicy.parse("""{"fullscreen_max_age_minutes":30}""")
        val (pl, item) = intersWithAd()

        clock.nowMs = item.loadedAtMillis + 30 * 60_000L - 1
        assertTrue(pl.hasLoadedAd())

        clock.nowMs = item.loadedAtMillis + 30 * 60_000L + 1
        assertFalse(pl.hasLoadedAd())
    }

    /** Nhãn chung `ad_expired` (mặc định của `AdsPlBase`), không phải `app_open_expired` của app open. */
    @Test
    fun `het han gui ly do ad_expired`() {
        val (pl, item) = intersWithAd()
        clock.nowMs = item.loadedAtMillis + sixtyMin + 1

        ServerEventCapture().use { server ->
            assertFalse(pl.hasLoadedAd())
            assertEquals(listOf(Constants.AdShowFailReason.AD_EXPIRED), server.showFails(1).map { it.second })
        }
    }
}
