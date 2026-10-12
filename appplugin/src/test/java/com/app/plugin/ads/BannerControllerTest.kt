package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Test đặc tả vùng banner của [AdsHelper] (plan 1.5.0 Task 6, KB-2): viết và chạy xanh trên mã CŨ trước khi chuyển
 * sang `ads.core.BannerController`, làm lưới cho bước chuyển (vùng doanh thu banner trước đó gần như không có test ở mức
 * `AdsHelper`, research-g8 §2.3). Mọi lời gọi đi qua hàm public của façade (`showBanner`, `hideBanner`,
 * `grantBannerHideReward`, `retryBannerNow`, `clearActivity`) và `bnIsShow` (R3, ở lại façade); state riêng của vùng
 * (`bnViewParent`, `bnCurrAdd`, `bnShowPl`…) chỉ quan sát qua hành vi, không đọc bằng reflection vì nó rời façade.
 * Mạng banner giả giữ inner callback của `loadAd` để test gọi tay; mọi `Handler` là handler ảo của [MainHandlerTimers]
 * (kể cả `removeCallbacksAndMessages(null)`), nên `pendingDelays` là đúng các hẹn giờ banner còn sống.
 */
class BannerControllerTest {

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

    private class AppShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "showFail:$err" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed" }
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { events += "impression" }
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) { events += "click" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed" }
    }

    private class Fixture(val helper: AdsHelper, val nets: List<BannerNetwork>, val timers: MainHandlerTimers) {
        val first get() = nets[0]
        fun plcf() = helper.getAdsPlacementConfig("bn_home", 1)!!
    }

    /**
     * `AdsHelper` với mạng banner theo [types] (thứ tự waterfall `bannerStepCicle`), dựng trong [MainHandlerTimers.run]
     * (handler ảo của vùng banner là field eager, R-4).
     */
    private fun withBanner(types: List<Int> = listOf(AdsIDDef.AdmobBanner), check: Fixture.() -> Unit) =
        MainHandlerTimers.run(honorRemoveAll = true) { timers ->
            val helper = AdsKillSwitchTest.newAdsHelper()
            helper.currConfig.bannerStepCicle.addAll(types)
            val nets = types.map { BannerNetwork(it, networkContext()) }
            setField(helper, "listAdsBn", nets.associateBy { it.adsType }.toMutableMap<Int, AdsBaseBanner>())
            setField(helper, "listAdsAll", mutableListOf<AdsBase>())
            Fixture(helper, nets, timers).check()
        }

    /** Context đủ cho `AdsBase` dựng (resources + SharedPreferences), như `LoadCycleGuardTest.setUp`. */
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

    /** Container đang hiện và gắn vào cửa sổ (điều kiện `refreshBn` load lại thay vì chờ tiếp). */
    private fun shownParent(): ViewGroup = mock(ViewGroup::class.java).also {
        `when`(it.isShown).thenReturn(true)
        `when`(it.isAttachedToWindow).thenReturn(true)
    }

    private fun ecmpWith(adObject: Any?): AdsEcmp = mock(AdsEcmp::class.java).also { `when`(it.adObject).thenReturn(adObject) }

    private fun AdsLoadInnerCb.loaded(adObject: Any?) = onLoaded("bn_home", "bn_home", "id-bn", ecmpWith(adObject), null)
    private fun AdsLoadInnerCb.failed() = onLoadFailed("bn_home", "bn_home", "id-bn", "no fill")

    /** Mạng đã khởi tạo từ lâu: bước im lặng được tính là kẹt ngay khi watchdog 30 s chạy. Trả lại giá trị cũ sau khối. */
    private fun <T> networksStartedLongAgo(block: () -> T): T {
        val field = AdSdkStartup::class.java.getDeclaredField("adNetworksStartedAtMs").apply { isAccessible = true }
        val old = field.getLong(null)
        field.setLong(null, System.currentTimeMillis() - 10 * BannerLoadGuard.STALL_TIMEOUT_MS)
        try {
            return block()
        } finally {
            field.setLong(null, old)
        }
    }

    @Test fun `showBanner - mang dau loadAd mot lan, onLoaded gan view, cbLoad onLoaded mot lan, isLoaded, bnIsShow`() = withBanner {
        val parent = shownParent()
        val view = mock(View::class.java)
        val load = AppLoadCb()
        val show = AppShowCb()

        helper.showBanner("home", parent, isCollapse = false, cbLoad = load, cbShow = show)
        assertEquals("mạng đầu loadAd một lần", 1, first.loads.size)
        first.loads[0].loaded(view)

        verify(parent, times(1)).addView(view)
        assertEquals(listOf("loaded"), load.events)
        assertEquals("banner không báo show", emptyList<String>(), show.events)
        assertTrue(plcf().isLoaded)
        assertFalse(plcf().isLoading)
        assertTrue(helper.bnIsShow)
        assertEquals("không load thêm", 1, first.loads.size)
    }

    @Test fun `waterfall 2 mang loi het - cbLoad onLoadFailed list empty mot lan, retry 5 s load lai mang 1`() =
        withBanner(listOf(AdsIDDef.AdmobBanner, AdsIDDef.MaxBanner)) {
            val (net1, net2) = nets
            val load = AppLoadCb()
            helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = load, cbShow = null)
            assertEquals(1, net1.loads.size)

            net1.loads[0].failed()
            assertEquals("bước kế đi qua Handler.post", 0, net2.loads.size)
            timers.runPosted()
            assertEquals("mạng 2 loadAd", 1, net2.loads.size)

            net2.loads[0].failed()
            timers.runPosted()
            assertEquals(listOf("fail:list empty"), load.events)
            assertFalse(plcf().isLoading)
            assertEquals("chỉ còn hẹn retry 5 s (watchdog đã huỷ)", listOf(5_000L), timers.pendingDelays)

            timers.advanceTo(5_000L)
            assertEquals("retry: vòng mới từ mạng 1", 2, net1.loads.size)
            assertEquals(1, net2.loads.size)
            assertEquals("retry không báo app lần nữa", listOf("fail:list empty"), load.events)
        }

    @Test fun `stall 30 s - bo vong, xep retry, callback muon cua vong cu bi bo`() = withBanner {
        networksStartedLongAgo {
            val parent = shownParent()
            val load = AppLoadCb()
            helper.showBanner("home", parent, isCollapse = false, cbLoad = load, cbShow = null)
            // watchdog bước banner 30 s, và watchdog 90 s của placement (AdsPlacementConfig.LOAD_TIMEOUT_MS) khi isLoading.
            assertEquals(listOf(BannerLoadGuard.STALL_TIMEOUT_MS, AdsPlacementConfig.LOAD_TIMEOUT_MS), timers.pendingDelays.sorted())

            timers.advanceTo(BannerLoadGuard.STALL_TIMEOUT_MS)
            assertFalse("vòng kẹt bị bỏ: hạ isLoading", plcf().isLoading)
            assertEquals("retry 5 s đã xếp", listOf(5_000L), timers.pendingDelays)

            // Callback muộn của vòng đã bỏ: không gắn view, không báo app, không đổi cờ, không xếp bước kế.
            first.loads[0].loaded(mock(View::class.java))
            first.loads[0].failed()
            verify(parent, never()).addView(any(View::class.java))
            assertEquals(emptyList<String>(), load.events)
            assertFalse(plcf().isLoaded)
            assertEquals(listOf(5_000L), timers.pendingDelays)

            timers.advanceTo(BannerLoadGuard.STALL_TIMEOUT_MS + 5_000L)
            assertEquals("retry: vòng mới", 2, first.loads.size)
        }
    }

    @Test fun `hideBanner - bnIsShow false, parent INVISIBLE, bo hen refresh va hen retry`() {
        withBanner {
            val parent = shownParent()
            helper.showBanner("home", parent, isCollapse = false, cbLoad = null, cbShow = null)
            first.loads[0].loaded(mock(View::class.java))
            assertEquals("hẹn refresh 60 s", listOf(60_000L), timers.pendingDelays)

            helper.hideBanner()

            assertFalse(helper.bnIsShow)
            verify(parent, times(1)).visibility = View.INVISIBLE
            assertEquals("bannerHandler bỏ hẹn refresh", emptyList<Long>(), timers.pendingDelays)
            timers.advanceTo(120_000L)
            assertEquals(1, first.loads.size)
        }
        withBanner {
            helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = null, cbShow = null)
            first.loads[0].failed()
            timers.runPosted()
            assertEquals("hẹn retry 5 s", listOf(5_000L), timers.pendingDelays)

            helper.hideBanner()

            assertEquals("bannerRetryHandler bỏ hẹn retry", emptyList<Long>(), timers.pendingDelays)
            timers.advanceTo(60_000L)
            assertEquals(1, first.loads.size)
        }
    }

    @Test fun `grantBannerHideReward - an banner, het han thi resumeParkedBanner showBanner lai voi cbLoad null`() = withBanner {
        val parent = shownParent()
        val load = AppLoadCb()
        helper.showBanner("home", parent, isCollapse = false, cbLoad = load, cbShow = null)
        first.loads[0].loaded(mock(View::class.java))

        helper.grantBannerHideReward(1_000L)
        assertFalse(helper.bnIsShow)
        verify(parent, times(1)).visibility = View.INVISIBLE
        assertEquals("chỉ còn hẹn hiện lại (refresh đã bỏ)", listOf(1_000L), timers.pendingDelays)

        helper.currConfig.bannerDeltaTime = -1 // cổng delta banner cho qua để thấy vòng load của lần hiện lại
        timers.advanceTo(1_000L)

        assertTrue("hiện lại", helper.bnIsShow)
        verify(parent, times(2)).visibility = View.VISIBLE
        assertNull("lần hiện lại không mang cbLoad của app", plcf().cbLoad)
        assertEquals("showBanner lại chạy vòng load", 2, first.loads.size)
        first.loads[1].loaded(mock(View::class.java))
        assertEquals("app không nhận onLoaded của vòng hiện lại", listOf("loaded"), load.events)
    }

    @Test fun `refreshBn - sau onLoaded khi dang hien hen bannerTimeRefresh giay roi load lai`() = withBanner {
        val parent = shownParent()
        helper.showBanner("home", parent, isCollapse = false, cbLoad = null, cbShow = null)
        first.loads[0].loaded(mock(View::class.java))
        assertEquals(60, helper.bannerTimeRefresh())
        assertEquals(listOf(helper.bannerTimeRefresh() * 1_000L), timers.pendingDelays)

        timers.advanceTo(59_999L)
        assertEquals(1, first.loads.size)
        timers.advanceTo(60_000L)

        assertEquals("refresh: vòng load thứ hai", 2, first.loads.size)
        assertTrue(plcf().isLoading)
    }

    @Test fun `retryBannerNow - no-op khi plcf isLoaded, load ngay khi vong truoc loi`() {
        withBanner {
            helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = null, cbShow = null)
            first.loads[0].loaded(Any()) // có quảng cáo nhưng không phải View: isLoaded, chưa gắn gì
            assertTrue(plcf().isLoaded)

            helper.retryBannerNow("x")

            assertEquals("isLoaded: không load thêm", 1, first.loads.size)
        }
        withBanner {
            helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = null, cbShow = null)
            first.loads[0].failed()
            timers.runPosted()
            assertEquals(listOf(5_000L), timers.pendingDelays)

            helper.retryBannerNow("consent updated")

            assertEquals("load ngay, không chờ retry", 2, first.loads.size)
            assertEquals(
                "hẹn retry cũ bị bỏ, chỉ còn watchdog vòng mới (bước 30 s, placement 90 s)",
                listOf(BannerLoadGuard.STALL_TIMEOUT_MS, AdsPlacementConfig.LOAD_TIMEOUT_MS),
                timers.pendingDelays.sorted(),
            )
        }
    }

    @Test fun `clearActivity - go view banner, removeAllViews, bo tham chieu parent va view`() = withBanner {
        val parent = shownParent()
        val view = mock(View::class.java)
        helper.showBanner("home", parent, isCollapse = false, cbLoad = null, cbShow = null)
        first.loads[0].loaded(view)
        verify(parent, times(1)).addView(view)
        `when`(view.parent).thenReturn(parent)

        helper.clearActivity()

        verify(parent, times(1)).removeView(view) // BannerAttach.detach(bnCurrAdd)
        verify(parent, times(1)).removeAllViews()
        verify(parent, times(1)).visibility = View.INVISIBLE // hideBanner
        assertFalse(helper.bnIsShow)
        assertEquals("hẹn refresh bị bỏ", emptyList<Long>(), timers.pendingDelays)

        // bnViewParent = null: hideBanner lần nữa không chạm parent cũ; hiện lại theo phần thưởng không có chỗ để hiện.
        helper.hideBanner()
        verify(parent, times(1)).visibility = View.INVISIBLE
        helper.retryBannerNow("x")
        helper.grantBannerHideReward(1_000L)
        timers.advanceTo(1_000L)
        assertEquals("không load lại trên host đã huỷ", 1, first.loads.size)

        // bnCurrAdd = null: banner mới trên container mới không removeAllViews (không còn "banner đang gắn").
        helper.currConfig.bannerDeltaTime = -1
        val parent2 = shownParent()
        val view2 = mock(View::class.java)
        helper.showBanner("home", parent2, isCollapse = false, cbLoad = null, cbShow = null)
        first.loads[1].loaded(view2)
        verify(parent2, never()).removeAllViews()
        verify(parent2, times(1)).addView(view2)
    }

    // ═══ 1.5.0 Task 15 bước 0: đặc tả vòng banner trước khi đổi cơ chế callback (review Task 6, P1) ═══

    /**
     * (a) Lưới cho nhánh `bnCurrAdd != null` của `onBannerCycleFailed`: banner đang gắn quảng cáo, vòng refresh lỗi hết
     * danh sách → giữ nhịp refresh (`bannerTimeRefresh()` giây), không xếp retry 5 s (lỗi 1.2.0: refresh dừng hẳn sau một
     * vòng refresh lỗi).
     */
    @Test fun `vong refresh loi het khi dang gan quang cao - xep refresh bannerTimeRefresh, khong xep retry 5 s`() = withBanner {
        val parent = shownParent()
        val load = AppLoadCb()
        helper.showBanner("home", parent, isCollapse = false, cbLoad = load, cbShow = null)
        first.loads[0].loaded(mock(View::class.java))
        assertEquals("hẹn refresh", listOf(helper.bannerTimeRefresh() * 1_000L), timers.pendingDelays)

        timers.advanceTo(helper.bannerTimeRefresh() * 1_000L)
        assertEquals("refresh: vòng thứ hai", 2, first.loads.size)
        first.loads[1].failed()
        timers.runPosted()

        assertFalse(plcf().isLoading)
        assertEquals("vòng refresh lỗi hết: báo app list empty", listOf("loaded", "fail:list empty"), load.events)
        assertEquals(
            "banner đang gắn: giữ nhịp refresh, không retry 5 s",
            listOf(helper.bannerTimeRefresh() * 1_000L),
            timers.pendingDelays,
        )
        timers.advanceTo(2 * helper.bannerTimeRefresh() * 1_000L)
        assertEquals("refresh kế tiếp chạy", 3, first.loads.size)
    }

    /**
     * (b) Lưới cho `resetRetries()` trong inner `onLoaded`: vòng 1 lỗi (retry 5 s), vòng 2 load được (quảng cáo không phải
     * View: màn hình vẫn trống), vòng refresh kế tiếp lỗi → retry lại bắt đầu từ 5 s, không phải bậc 15 s.
     */
    @Test fun `vong loi, vong sau onLoaded, vong loi ke tiep tren man hinh trong - retry lai tu 5 s`() = withBanner {
        helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = null, cbShow = null)
        first.loads[0].failed()
        timers.runPosted()
        assertEquals("vòng 1 lỗi: retry bậc đầu", listOf(5_000L), timers.pendingDelays)

        timers.advanceTo(5_000L)
        assertEquals("retry: vòng 2", 2, first.loads.size)
        first.loads[1].loaded(Any()) // isLoaded, không gắn gì (không phải View): màn hình vẫn trống
        assertTrue(plcf().isLoaded)
        assertEquals("sau onLoaded: hẹn refresh", listOf(helper.bannerTimeRefresh() * 1_000L), timers.pendingDelays)

        timers.advanceTo(5_000L + helper.bannerTimeRefresh() * 1_000L)
        assertEquals("refresh: vòng 3", 3, first.loads.size)
        first.loads[2].failed()
        timers.runPosted()

        assertEquals("onLoaded đã đặt lại bậc retry: 5 s, không phải 15 s", listOf(5_000L), timers.pendingDelays)
    }

    /**
     * (c) Lưới cho `h.bnTimeShow = …` trong inner `onLoaded`: `bannerDeltaTime > 0`, `showBanner` lần hai trong khoảng
     * delta sau khi vòng đầu load xong → cổng delta chặn, không `loadAd` thêm.
     */
    @Test fun `onLoaded dat bnTimeShow - showBanner lan hai trong khoang delta khong loadAd them`() = withBanner {
        helper.currConfig.bannerDeltaTime = 50
        val parent = shownParent()
        helper.showBanner("home", parent, isCollapse = false, cbLoad = null, cbShow = null)
        assertEquals(1, first.loads.size)
        first.loads[0].loaded(mock(View::class.java))
        assertFalse(plcf().isLoading)

        helper.showBanner("home", parent, isCollapse = false, cbLoad = null, cbShow = null)

        assertEquals("trong khoảng delta: không load thêm", 1, first.loads.size)
        assertFalse(plcf().isLoading)
    }

    /**
     * (d) `grantBannerHideReward` đã ghi `hideUntil` (SharedPreferences giả trả `hideUntil > now`): `showBanner` mới chỉ
     * park container (`INVISIBLE`, không `loadAd`) và hẹn hiện lại đúng `hideUntil - now`.
     */
    @Test fun `hideUntil trong tuong lai - showBanner chi park, khong loadAd, hen hien lai dung hideUntil - now`() = withBanner {
        val hideMs = 10_000L
        val hideUntil = System.currentTimeMillis() + hideMs
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getLong(eq("ads_bn_hide_until_millis"), anyLong())).thenReturn(hideUntil)
        val appContext = mock(Context::class.java)
        `when`(appContext.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        setField(helper, "mContext", appContext)
        val parent = shownParent()
        val load = AppLoadCb()
        val show = AppShowCb()

        val before = System.currentTimeMillis()
        helper.showBanner("home", parent, isCollapse = false, cbLoad = load, cbShow = show)
        val after = System.currentTimeMillis()

        assertEquals("park: không loadAd", 0, first.loads.size)
        verify(parent, times(1)).visibility = View.INVISIBLE
        verify(parent, never()).visibility = View.VISIBLE
        assertFalse("park không bật bnIsShow", helper.bnIsShow)
        assertEquals("park không callback", emptyList<String>(), load.events + show.events)
        val delay = timers.pendingDelays.single()
        assertTrue("hẹn hiện lại = hideUntil - now ($delay)", delay in (hideUntil - after)..(hideUntil - before))

        // Hết hạn (prefs không còn hideUntil): hiện lại đúng container đã park, chạy vòng load.
        `when`(prefs.getLong(eq("ads_bn_hide_until_millis"), anyLong())).thenReturn(-1L)
        timers.advanceTo(delay)
        assertTrue(helper.bnIsShow)
        verify(parent, times(1)).visibility = View.VISIBLE
        assertEquals("hiện lại chạy vòng load", 1, first.loads.size)
    }
}
