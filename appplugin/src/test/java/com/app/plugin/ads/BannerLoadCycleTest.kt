package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 1.5.0 Task 15: vòng banner chụp `plcf.loadCycle` lúc bắt đầu (như 8 vòng khác) và chỉ gọi callback của app khi lượt
 * còn hiện hành; trước đó cái chặn là getter-null của `AdsPlacementConfig.cbLoad` (đọc ra `null` sau `load timeout`).
 * Sau watchdog 90 s của placement (app đã nhận `onLoadFailed("load timeout")`), vòng cũ vẫn chạy hết để gắn quảng cáo
 * tới muộn và xếp retry — chỉ 2 lời gọi app bị chặn: `onLoaded` trong inner cb và `list empty` ở cuối vòng.
 *
 * Mạng banner giả giữ inner cb của `loadAd` (mẫu `BannerControllerTest`). Mạng coi như chưa khởi tạo
 * (`AdSdkStartup.adNetworksStartedAtMs = 0`): bước im lặng không bị watchdog stall 30 s bỏ, nên vòng sống qua 90 s như
 * lúc chờ consent/khởi tạo.
 *
 * Callback của app gán lại SAU `load timeout` (qua `showBanner` không bắt đầu vòng mới, hoặc app gán thẳng `cbLoad`)
 * vẫn nhận kết quả muộn của vòng đang chạy — đúng như getter-null cũ; setter của `cbLoad` ghi lượt lúc gán
 * (`AdsPlacementConfig.isLoadCallbackCurrent`, 1.5.0 Task 15 bước B).
 */
class BannerLoadCycleTest {

    private class BannerNetwork(type: Int, context: Context) : AdsBaseBanner(type, context) {
        override val eventTag: String get() = "test_banner"
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_banner_data"
        override fun getKeyForResDf(): String = "test_banner_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
    }

    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private class Fixture(val helper: AdsHelper, val nets: List<BannerNetwork>, val timers: MainHandlerTimers) {
        fun plcf() = helper.getAdsPlacementConfig("bn_home", 1)!!

        /** Watchdog 90 s của placement chạy đúng lúc này (vd giữa `Handler.post` của bước waterfall và lúc bước đó chạy). */
        fun firePlacementWatchdogNow() {
            (AdsPlacementConfig::class.java.getDeclaredField("loadTimeoutRunnable").apply { isAccessible = true }
                .get(plcf()) as Runnable).run()
        }
    }

    private fun withBanner(types: List<Int>, check: Fixture.() -> Unit) = networksNotStarted {
        MainHandlerTimers.run(honorRemoveAll = true) { timers ->
            val helper = AdsKillSwitchTest.newAdsHelper()
            helper.currConfig.bannerStepCicle.addAll(types)
            val nets = types.map { BannerNetwork(it, networkContext()) }
            setField(helper, "listAdsBn", nets.associateBy { it.adsType }.toMutableMap<Int, AdsBaseBanner>())
            setField(helper, "listAdsAll", mutableListOf<AdsBase>())
            Fixture(helper, nets, timers).check()
        }
    }

    private fun <T> networksNotStarted(block: () -> T): T {
        val field = AdSdkStartup::class.java.getDeclaredField("adNetworksStartedAtMs").apply { isAccessible = true }
        val old = field.getLong(null)
        field.setLong(null, 0L)
        try {
            return block()
        } finally {
            field.setLong(null, old)
        }
    }

    private fun networkContext(): Context {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        return context
    }

    private fun setField(helper: AdsHelper, name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    private fun shownParent(): ViewGroup = mock(ViewGroup::class.java).also {
        `when`(it.isShown).thenReturn(true)
        `when`(it.isAttachedToWindow).thenReturn(true)
    }

    private fun ecmpWith(adObject: Any?): AdsEcmp = mock(AdsEcmp::class.java).also { `when`(it.adObject).thenReturn(adObject) }

    /**
     * Lời gọi app ở cuối vòng (`list empty`): mạng 1 lỗi khi lượt còn chạy → bước cuối xếp qua `Handler.post`; watchdog
     * 90 s của placement chạy trước khi bước đó chạy → app nhận `load timeout` (1); bước chạy → hết danh sách → app
     * KHÔNG nhận `list empty` (lượt đã báo kết thúc), vòng vẫn kết thúc như thường: xếp retry 5 s (màn hình trống).
     */
    @Test fun `sau load timeout, vong cu het danh sach - app khong nhan list empty, van xep retry 5 s`() =
        withBanner(listOf(AdsIDDef.AdmobBanner)) {
            val (net1) = nets
            val appCb = AppLoadCb()
            helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = appCb, cbShow = null)
            assertEquals(1, net1.loads.size)

            net1.loads[0].onLoadFailed("bn_home", "bn_home", "id-bn", "no fill") // bước kế đã post, chưa chạy
            firePlacementWatchdogNow()
            assertEquals(listOf("fail:load timeout"), appCb.events)
            assertFalse(plcf().isLoading)

            timers.runPosted()                                                  // vòng cũ chạy tới hết danh sách

            assertEquals("đúng một callback kết thúc", 1, appCb.events.size)
            assertEquals("vòng vẫn kết thúc: retry 5 s", listOf(5_000L), timers.pendingDelays)
            timers.advanceTo(5_000L)
            assertEquals("retry chạy vòng mới", 2, net1.loads.size)
        }

    /**
     * Lời gọi app trong inner `onLoaded`: watchdog 90 s báo `load timeout` (1); mạng 1 trả view muộn → app KHÔNG nhận
     * `onLoaded` thứ hai, nhưng view vẫn được gắn và `isLoaded` bật (banner không trống tới lần retry/show kế).
     */
    @Test fun `sau load timeout, view toi muon - app khong nhan onLoaded, view van duoc gan, isLoaded`() =
        withBanner(listOf(AdsIDDef.AdmobBanner, AdsIDDef.MaxBanner)) {
            val (net1, net2) = nets
            val parent = shownParent()
            val appCb = AppLoadCb()
            helper.showBanner("home", parent, isCollapse = false, cbLoad = appCb, cbShow = null)
            assertEquals(1, net1.loads.size)

            timers.advanceTo(AdsPlacementConfig.LOAD_TIMEOUT_MS)
            assertEquals(listOf("fail:load timeout"), appCb.events)
            assertEquals("stall không bỏ vòng khi mạng chưa khởi tạo", 1, net1.loads.size)

            val view = mock(View::class.java)
            net1.loads[0].onLoaded("bn_home", "bn_home", "id-bn", ecmpWith(view), null)

            assertEquals("đúng một callback kết thúc", 1, appCb.events.size)
            verify(parent, times(1)).addView(view)
            assertTrue(plcf().isLoaded)
            assertFalse(plcf().isLoading)
            assertEquals(0, net2.loads.size)
            assertEquals("vòng kết thúc như thường: hẹn refresh", listOf(helper.bannerTimeRefresh() * 1_000L), timers.pendingDelays)
        }

    /**
     * Ngữ nghĩa cũ của getter-null giữ nguyên (1.5.0 Task 15, phương án B): sau `load timeout` của placement, app gọi
     * `showBanner(cb2)` mà cổng delta chặn load mới (vòng A vẫn là vòng hiện hành của `BannerLoadGuard`); view của vòng A
     * tới muộn → `cb2` nhận `onLoaded` (trước đây setter của `cbLoad` hạ cờ timeout), `cb1` không nhận thêm gì. Không có
     * nhánh này, `cb2` chỉ nhận ở vòng refresh kế — hoặc không bao giờ khi `bannerTimeRefresh() == 0`.
     */
    @Test fun `sau load timeout, showBanner moi bi cong delta chan - cb2 nhan onLoaded muon cua vong cu, cb1 khong nhan them`() =
        withBanner(listOf(AdsIDDef.AdmobBanner)) {
            val (net1) = nets
            helper.currConfig.bannerDeltaTime = 1_000
            val parent = shownParent()
            val cb1 = AppLoadCb()
            helper.showBanner("home", parent, isCollapse = false, cbLoad = cb1, cbShow = null)
            assertEquals(1, net1.loads.size)

            timers.advanceTo(AdsPlacementConfig.LOAD_TIMEOUT_MS)
            assertEquals(listOf("fail:load timeout"), cb1.events)

            helper.bnTimeShow = System.currentTimeMillis() / 1000 // vòng trước vừa kết thúc: delta 1000 s chặn load mới
            val cb2 = AppLoadCb()
            helper.showBanner("home", parent, isCollapse = false, cbLoad = cb2, cbShow = null)
            assertEquals("cổng delta chặn loadBanner", 1, net1.loads.size)
            assertFalse(plcf().isLoading)

            val view = mock(View::class.java)
            net1.loads[0].onLoaded("bn_home", "bn_home", "id-bn", ecmpWith(view), null)

            assertEquals("cb2 nhận onLoaded của vòng tới muộn", listOf("loaded"), cb2.events)
            assertEquals("cb1 chỉ có một callback kết thúc", listOf("fail:load timeout"), cb1.events)
            verify(parent, times(1)).addView(view)
            assertTrue(plcf().isLoaded)
        }

    /**
     * Ngữ nghĩa getter cũ khi app gán thẳng `cbLoad` qua API public (review Task 15 I1): sau `load timeout`, app gán
     * `AdsHelper.getAdsPlacementConfig("bn_home", 1)!!.cbLoad = cb3` (không qua `showBanner`); view của vòng đang chạy
     * tới muộn → `cb3` nhận `onLoaded` (setter của `cbLoad` ghi lượt, như setter cũ hạ cờ timeout), `cb1` không nhận
     * thêm.
     */
    @Test fun `sau load timeout, app gan thang cbLoad - cb3 nhan onLoaded muon, cb1 khong nhan them`() =
        withBanner(listOf(AdsIDDef.AdmobBanner)) {
            val (net1) = nets
            val parent = shownParent()
            val cb1 = AppLoadCb()
            helper.showBanner("home", parent, isCollapse = false, cbLoad = cb1, cbShow = null)
            assertEquals(1, net1.loads.size)

            timers.advanceTo(AdsPlacementConfig.LOAD_TIMEOUT_MS)
            assertEquals(listOf("fail:load timeout"), cb1.events)

            val cb3 = AppLoadCb()
            helper.getAdsPlacementConfig("bn_home", 1)!!.cbLoad = cb3
            val view = mock(View::class.java)
            net1.loads[0].onLoaded("bn_home", "bn_home", "id-bn", ecmpWith(view), null)

            assertEquals("cb3 nhận onLoaded của vòng tới muộn", listOf("loaded"), cb3.events)
            assertEquals("cb1 chỉ có một callback kết thúc", listOf("fail:load timeout"), cb1.events)
            verify(parent, times(1)).addView(view)
        }

    /**
     * Gán `cbLoad` lúc vòng đang chạy rồi mới `load timeout` (review Task 15 m1): `showBanner(cb2)` khi vòng của `cb1`
     * còn chạy (không `loadAd` thêm) → watchdog 90 s báo `load timeout` tới `cb2`; view tới muộn → `cb2` KHÔNG nhận
     * `onLoaded` (đúng một callback kết thúc), `cb1` không nhận gì. Lần gán trước timeout không "hồi" callback.
     */
    @Test fun `gan cbLoad luc vong dang chay roi load timeout - cb2 chi nhan load timeout`() =
        withBanner(listOf(AdsIDDef.AdmobBanner)) {
            val (net1) = nets
            val parent = shownParent()
            val cb1 = AppLoadCb()
            helper.showBanner("home", parent, isCollapse = false, cbLoad = cb1, cbShow = null)
            val cb2 = AppLoadCb()
            helper.showBanner("home", parent, isCollapse = false, cbLoad = cb2, cbShow = null)
            assertEquals("vòng đang chạy: không loadAd thêm", 1, net1.loads.size)

            timers.advanceTo(AdsPlacementConfig.LOAD_TIMEOUT_MS)
            assertEquals(listOf("fail:load timeout"), cb2.events)

            net1.loads[0].onLoaded("bn_home", "bn_home", "id-bn", ecmpWith(mock(View::class.java)), null)

            assertEquals("cb2 đúng một callback kết thúc", listOf("fail:load timeout"), cb2.events)
            assertEquals(emptyList<String>(), cb1.events)
        }
}
