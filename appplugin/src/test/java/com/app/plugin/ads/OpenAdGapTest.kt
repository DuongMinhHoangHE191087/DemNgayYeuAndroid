package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants.AdShowFailReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.5.0 LAM2 (Hợp đồng B, quyết định (e), D5-250): open ad (trừ `full_splash`) không hiện trong `openAdDeltaTime` sau mọi
 * quảng cáo toàn màn hình: mốc = max(`openTimeShow`, `fullTimeShow`, `full2TimeShow`, `giftTimeShow`). Lùi bằng
 * `@integer/ads_cf_open_gap_own_only` = 1 (hoặc RC `ads_cf_overrides`, đọc mỗi lần) ⇒ chỉ tính open ad như 2.4.4.
 */
class OpenAdGapTest {
    /** Mạng app open giả: `showAd` hiện được, ghi placement. */
    private class OpenNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_open_gap"
        val shows = mutableListOf<String>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_open_gap_${adsType}_data"
        override fun getKeyForResDf(): String = "test_open_gap_${adsType}_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement
            adsShowCb.onAdShowed(placement, placement, "t", flagShow)
            return true
        }
    }

    @Before fun setUp() = AdsCfOverrides.resetForTest()

    @After fun tearDown() = AdsCfOverrides.resetForTest()

    private fun nowSec() = System.currentTimeMillis() / 1000

    /** Context có `@integer/ads_cf_open_gap_own_only` = [ownOnly]; `null` = resource vắng. */
    private fun ctx(ownOnly: Int?): Context {
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        if (ownOnly != null) {
            `when`(res.getIdentifier(eq(OWN_ONLY), eq("integer"), anyString())).thenReturn(7)
            `when`(res.getInteger(7)).thenReturn(ownOnly)
        }
        return mock(Context::class.java).also {
            `when`(it.resources).thenReturn(res)
            `when`(it.packageName).thenReturn("app")
            `when`(it.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        }
    }

    private fun helper(context: Context? = null): AdsHelper = AdsKillSwitchTest.newAdsHelper().also { h ->
        h.currConfig.openAdDeltaTime = 15
        h.openTimeShow = 0
        if (context != null) AdsHelper::class.java.getDeclaredField("mContext").apply { isAccessible = true }.set(h, context)
    }

    /** Lần lượt Full2, rewarded, interstitial hiện/đóng [ago] giây trước; trả `isOpenAllowShowAds` từng ca. */
    private fun afterEachFullscreen(ago: Long, context: Context? = null): List<Boolean> =
        listOf<(AdsHelper) -> Unit>({ it.full2TimeShow = nowSec() - ago }, { it.giftTimeShow = nowSec() - ago },
            { it.fullTimeShow = nowSec() - ago })
            .map { set -> helper(context).also(set).isOpenAllowShowAds() }

    @Test fun `O1 open ad resume bi chan trong openAdDeltaTime sau Full2, rewarded, interstitial`() {
        assertEquals("5 s sau Full2 / rewarded / interstitial, openAdDeltaTime 15 s", listOf(false, false, false), afterEachFullscreen(5))
        assertEquals("resource vắng (context có) = mặc định mới", listOf(false, false, false), afterEachFullscreen(5, ctx(null)))
        assertEquals("qua 20 s thì cho", listOf(true, true, true), afterEachFullscreen(20))
        assertFalse("open ad của chính nó vẫn tính", helper().also { it.openTimeShow = nowSec() - 5 }.isOpenAllowShowAds())
        assertTrue("không quảng cáo nào trước đó", helper().isOpenAllowShowAds())
    }

    @Test fun `O2 ads_cf_open_gap_own_only 1 thi chi tinh open ad (2_4_4), resource hoac RC`() {
        assertEquals("resource = 1", listOf(true, true, true), afterEachFullscreen(5, ctx(1)))
        assertEquals("resource = 0", listOf(false, false, false), afterEachFullscreen(5, ctx(0)))
        assertFalse("own-only vẫn chặn theo openTimeShow", helper(ctx(1)).also { it.openTimeShow = nowSec() - 5 }.isOpenAllowShowAds())
        AdsCfOverrides.apply("""{"schema_version":1,"ads_cf_overrides":{"ads_cf_open_gap_own_only":1}}""")
        assertEquals("RC = 1 có hiệu lực ngay", listOf(true, true, true), afterEachFullscreen(5, ctx(0)))
    }

    @Test fun `O3 full_splash van mien, full_resume bi NOT_ALLOW_SHOW sau Full2`() = MainHandlerTimers.run(honorRemoveAll = true) {
        val h = helper(ctx(null))
        h.currConfig.apply { openAdFirstType = 0; openAdtype = 0; fullStepCicle.clear(); fullStepRe.clear() }
        val open = OpenNetwork(ctx(null), AdsIDDef.AdmobFullOpen)
        AdsHelper::class.java.getDeclaredField("listAdsFull").apply { isAccessible = true }
            .set(h, mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullOpen to open))
        AdsHelper::class.java.getDeclaredField("listAdsAll").apply { isAccessible = true }.set(h, mutableListOf<AdsBase>())
        h.setFlag4ShowResume(true)
        h.full2TimeShow = nowSec() - 5
        ServerEventCapture().use { server ->
            assertFalse(h.showOpenAd("resume", null))
            assertEquals(listOf(PlacementDefault.PLFullResume to AdShowFailReason.NOT_ALLOW_SHOW), server.showFails(1))
        }
        assertEquals("resume không gọi mạng", emptyList<String>(), open.shows)
        assertTrue("full_splash miễn khoảng cách", h.showOpenAd(PlacementDefault.PLFullSplash, null))
        assertEquals(listOf(PlacementDefault.PLFullSplash), open.shows)
    }

    private companion object {
        const val OWN_ONLY = "ads_cf_open_gap_own_only"
    }
}
