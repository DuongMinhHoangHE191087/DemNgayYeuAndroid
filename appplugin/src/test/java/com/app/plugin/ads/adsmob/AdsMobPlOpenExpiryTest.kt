package com.app.plugin.ads.adsmob

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.APP_OPEN_MAX_AGE_MS
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsConfigOb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsPlacementConfigManager
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.google.android.gms.ads.appopen.AppOpenAd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
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
 * Task 11: app open AdMob đã load quá 4 giờ ([APP_OPEN_MAX_AGE_MS]) không được hiện (Google không tính
 * impression của app open hết hạn). [AdsMobPlOpen.show] đi đúng đường "show not loaded" có sẵn: trả
 * `false`, không callback; vòng app open đi tiếp sang mạng sau, và không mạng nào hiện thì app không nhận
 * callback nào (hợp đồng 1.3.1, [com.app.plugin.ads.WaterfallShowFailHold]).
 */
class AdsMobPlOpenExpiryTest {

    private class RecordingShowCb : AdsShowCb {
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

    /** Mạng thật với đúng một placement thật; load không chạy vì SDK mạng không có trong JVM test. */
    private class PlNetwork(context: Context, type: Int, private val pl: AdsPlBase) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_open_pl_${adsType}_data"
        override fun getKeyForResDf(): String = "test_open_pl_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
    }

    /** Mạng có quảng cáo còn hạn: hiện được, báo onAdShowed; [dismiss] như người dùng bấm đóng. */
    private class ShowingNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        private var showing: AdsShowCb? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_open_showing_${adsType}_data"
        override fun getKeyForResDf(): String = "test_open_showing_${adsType}_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = adsShowCb
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
        fun dismiss() {
            showing?.onAdDismissed(PlacementDefault.PLFullSplash, PlacementDefault.PLFullSplash, "test")
        }
    }

    private lateinit var context: Context
    private val clock = LoadedAdFixture.Clock()
    private val activity = mock(Activity::class.java)
    private val ad = mock(AppOpenAd::class.java)

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
    }

    /** App open AdMob thật, có Activity, với quảng cáo đã load ở [LoadedAdFixture.Clock] hiện tại. */
    private fun openWithAd(): Pair<AdsMobPlOpen, com.app.plugin.ads.AdsEcmp> {
        val pl = AdsMobPlOpen(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullOpen, "full_default")
        pl.setActivity(activity)
        return pl to LoadedAdFixture.giveLoadedAd(pl, ad, clock)
    }

    @Test
    fun `show - qua 4 gio thi khong hien, tra false, khong callback, quang cao bi bo`() {
        val (pl, item) = openWithAd()
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1
        val cb = RecordingShowCb()

        assertFalse(pl.show(PlacementDefault.PLFullSplash, cb, 0))

        assertEquals(emptyList<String>(), cb.events)
        verify(ad, never()).show(any(Activity::class.java))
        assertFalse(item.isLoaded)
        assertNull(item.adObject)
        assertFalse(pl.hasLoadedAd())
    }

    @Test
    fun `show - con han thi hien nhu cu`() {
        val (pl, item) = openWithAd()
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS - 1

        assertTrue(pl.show(PlacementDefault.PLFullSplash, RecordingShowCb(), 0))

        verify(ad).show(activity)
    }

    @Test
    fun `hasLoadedAd - qua 4 gio la false`() {
        val (pl, item) = openWithAd()
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1
        assertFalse(pl.hasLoadedAd())
    }

    @After
    fun tearDown() {
        AdsPolicy.current = AdsPolicy.DEFAULT
    }

    /** Task 10 (addendum B6): tuổi tối đa đọc từ `app_open_max_age_minutes` của `ads_policy_android`. */
    @Test
    fun `tuoi toi da doc tu ads_policy_android`() {
        AdsPolicy.current = AdsPolicy.parse("""{"app_open_max_age_minutes":60}""")
        val (pl, item) = openWithAd()

        clock.nowMs = item.loadedAtMillis + 60 * 60_000L - 1
        assertTrue(pl.hasLoadedAd())

        clock.nowMs = item.loadedAtMillis + 60 * 60_000L + 1
        assertFalse(pl.hasLoadedAd())
    }

    /** Nhãn do placement quyết định (B6): app open giữ `app_open_expired` của Task 11. */
    @Test
    fun `het han gui ly do app_open_expired`() {
        val (pl, item) = openWithAd()
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1

        ServerEventCapture().use { server ->
            assertFalse(pl.hasLoadedAd())
            assertEquals(listOf(Constants.AdShowFailReason.APP_OPEN_EXPIRED), server.showFails(1).map { it.second })
        }
    }

    // ═══ Trong vòng app open của AdsHelper ═══

    private fun helperWithOpenNetworks(vararg networks: Pair<Int, AdsBaseFull>): AdsHelper =
        AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance().apply {
            currConfig = AdsConfigOb(mock(Context::class.java)).apply {
                openAdFirstType = 0 // AdmobFullOpen rồi MaxFullOpen
                openAdtype = 0
            }
            placementsConfigManager = AdsPlacementConfigManager()
            AdsHelper::class.java.getDeclaredField("listAdsFull").apply { isAccessible = true }
                .set(this, mutableMapOf(*networks))
        }

    @Test
    fun `vong app open - AdMob het han bi bo qua, mang sau hien - app chi nhan showed, dismissed`() {
        val (pl, item) = openWithAd()
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1
        val showing = ShowingNetwork(context, AdsIDDef.MaxFullOpen)
        val helper = helperWithOpenNetworks(
            AdsIDDef.AdmobFullOpen to PlNetwork(context, AdsIDDef.AdmobFullOpen, pl),
            AdsIDDef.MaxFullOpen to showing,
        )
        val cb = RecordingShowCb()

        val shown = helper.showOpenAd(PlacementDefault.PLFullSplash, cb)
        showing.dismiss()

        assertTrue("mạng sau phải hiện", shown)
        assertEquals(listOf("showed", "dismissed"), cb.events)
        verify(ad, never()).show(any(Activity::class.java))
        assertFalse(helper.isFullShowing())
    }

    @Test
    fun `vong app open - chi co AdMob het han - tra false, app khong nhan callback nao`() {
        val (pl, item) = openWithAd()
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1
        val helper = helperWithOpenNetworks(AdsIDDef.AdmobFullOpen to PlNetwork(context, AdsIDDef.AdmobFullOpen, pl))
        val cb = RecordingShowCb()

        assertFalse(helper.showOpenAd(PlacementDefault.PLFullSplash, cb))

        assertEquals("như mọi lượt \"show not loaded\": không onAdShowFail", emptyList<String>(), cb.events)
        assertFalse(helper.isFullShowing())
    }
}
