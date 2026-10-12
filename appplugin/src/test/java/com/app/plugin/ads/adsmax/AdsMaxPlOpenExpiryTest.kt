package com.app.plugin.ads.adsmax

import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.logevent.ServerEventCapture
import com.applovin.mediation.ads.MaxAppOpenAd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task I1 (thay Task 11 cho MAX): app open MAX **không** áp hạn tuổi 4 giờ của SDK. `MaxAppOpenAd` bọc
 * `MaxFullscreenAdImpl` (định dạng `APP_OPEN`), tự hết hạn và nạp lại trong cùng đối tượng (`onAdExpired` →
 * `MediationServiceImpl.loadAd`, applovin-sdk 13.6.2), nên mốc load của SDK ([AdsEcmp.loadedAtMillis]) không bao giờ
 * đặt lại và hạn 4 giờ chỉ từ chối nhầm quảng cáo MAX còn tươi (cùng lập luận FE1). AdMob app open vẫn hết hạn 4 giờ
 * (`AdsMobPlOpenExpiryTest`).
 */
class AdsMaxPlOpenExpiryTest {

    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
    }

    private val clock = LoadedAdFixture.Clock()
    private val ad = mock(MaxAppOpenAd::class.java).also { `when`(it.isReady).thenReturn(true) }

    private fun openWithAd(): Pair<AdsMaxPlOpen, AdsEcmp> {
        val pl = AdsMaxPlOpen(mock(Context::class.java), mock(AdsBase::class.java), AdsIDDef.MaxFullOpen, "full_default")
        return pl to LoadedAdFixture.giveLoadedAd(pl, ad, clock)
    }

    @After
    fun tearDown() {
        AdsPolicy.current = AdsPolicy.DEFAULT
    }

    @Test
    fun `phut 241 van hien, khong destroy, khong app_open_expired`() {
        val (pl, item) = openWithAd()
        clock.nowMs = item.loadedAtMillis + 241 * 60_000L
        val cb = RecordingShowCb()

        val reasons = ServerEventCapture().use { server ->
            assertTrue(pl.hasLoadedAd())
            assertTrue(pl.show(PlacementDefault.PLFullSplash, cb, 0))
            server.await("ad_show_fail", 0).map { it.get("reason").asString }
        }

        assertEquals(emptyList<String>(), reasons)
        verify(ad).showAd()
        verify(ad, never()).destroy()
        assertTrue(item.isLoaded)
    }

    /** `app_open_max_age_minutes` của `ads_policy_android` chỉ còn áp cho AdMob; MAX bỏ qua. */
    @Test
    fun `app_open_max_age_minutes khong ap cho MAX`() {
        AdsPolicy.current = AdsPolicy.parse("""{"app_open_max_age_minutes":30}""")
        val (pl, item) = openWithAd()
        clock.nowMs = item.loadedAtMillis + 31 * 60_000L

        assertTrue(pl.hasLoadedAd())
        verify(ad, never()).destroy()
    }
}
