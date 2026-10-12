package com.app.plugin.ads.adsmax

import android.app.Activity
import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task I4 (review EF Q9): MAX interstitial, rewarded và app open kiểm `isReady()` trước `showAd`. MAX đang tự nạp lại
 * sau khi hết hạn (hoặc nạp lại lỗi) thì `isReady()` là `false`: không gọi `showAd` (MAX sẽ báo display fail), trả
 * `false` như nhánh "show not loaded" để waterfall đi tiếp, không callback, gửi `ad_show_fail_reason` =
 * `show_not_loaded`, log. Quảng cáo đã load giữ nguyên (MAX báo `onAdLoaded` lại khi nạp xong).
 */
class AdsMaxShowReadyTest {

    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
    }

    private val context = mock(Context::class.java)
    private val activity = mock(Activity::class.java)
    private val clock = LoadedAdFixture.Clock()

    private fun inters(ready: Boolean): Pair<AdsMaxPlInters, MaxInterstitialAd> {
        val ad = mock(MaxInterstitialAd::class.java).also { `when`(it.isReady).thenReturn(ready) }
        val pl = AdsMaxPlInters(context, mock(AdsBase::class.java), AdsIDDef.MaxFullInters, "full_default")
        pl.setActivity(activity)
        pl.interstitialAd = ad
        LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        return pl to ad
    }

    private fun rewarded(ready: Boolean): Pair<AdsMaxPlrewarded, MaxRewardedAd> {
        val ad = mock(MaxRewardedAd::class.java).also { `when`(it.isReady).thenReturn(ready) }
        val pl = AdsMaxPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.MaxRewarded, "gift_default")
        pl.setActivity(activity)
        AdsMaxPlrewarded::class.java.getDeclaredField("rewardedAd").apply { isAccessible = true }.set(pl, ad)
        LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        return pl to ad
    }

    private fun open(ready: Boolean): Pair<AdsMaxPlOpen, MaxAppOpenAd> {
        val ad = mock(MaxAppOpenAd::class.java).also { `when`(it.isReady).thenReturn(ready) }
        val pl = AdsMaxPlOpen(context, mock(AdsBase::class.java), AdsIDDef.MaxFullOpen, "full_default")
        pl.setActivity(activity)
        LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        return pl to ad
    }

    /** Show khi chưa sẵn sàng: trả `false`, không callback, không đang hiện, quảng cáo vẫn đã load; lý do gửi server. */
    private fun assertRefused(pl: AdsPlBase, placement: String): List<String> {
        val cb = RecordingShowCb()
        val reasons = ServerEventCapture().use { server ->
            assertFalse(pl.show(placement, cb, 0))
            server.await("ad_show_fail", 1).map { it.get("reason").asString }
        }
        assertEquals(emptyList<String>(), cb.events)
        assertFalse(LoadedAdFixture.state(pl).isShowing)
        assertTrue("quảng cáo đã load giữ nguyên", LoadedAdFixture.ecmps(pl).list.single().isLoaded)
        return reasons
    }

    @Test
    fun `MAX interstitial chua san sang - khong showAd, tra false, show_not_loaded`() {
        val (pl, ad) = inters(ready = false)
        assertEquals(listOf(Constants.AdShowFailReason.SHOW_NOT_LOADED), assertRefused(pl, "full_home"))
        verify(ad, never()).showAd(any(Activity::class.java))
    }

    @Test
    fun `MAX rewarded chua san sang - khong showAd, tra false, show_not_loaded`() {
        val (pl, ad) = rewarded(ready = false)
        assertEquals(listOf(Constants.AdShowFailReason.SHOW_NOT_LOADED), assertRefused(pl, "gift_home"))
        verify(ad, never()).showAd(any(Activity::class.java))
    }

    @Test
    fun `MAX app open chua san sang - khong showAd, tra false, show_not_loaded`() {
        val (pl, ad) = open(ready = false)
        assertEquals(listOf(Constants.AdShowFailReason.SHOW_NOT_LOADED), assertRefused(pl, PlacementDefault.PLFullSplash))
        verify(ad, never()).showAd()
    }

    @Test
    fun `MAX san sang - van hien nhu cu`() {
        val (i, iAd) = inters(ready = true)
        val (r, rAd) = rewarded(ready = true)
        val (o, oAd) = open(ready = true)

        assertTrue(i.show("full_home", RecordingShowCb(), 0))
        assertTrue(r.show("gift_home", RecordingShowCb(), 0))
        assertTrue(o.show(PlacementDefault.PLFullSplash, RecordingShowCb(), 0))

        verify(iAd).showAd(activity)
        verify(rAd).showAd(activity)
        verify(oAd).showAd()
    }
}
