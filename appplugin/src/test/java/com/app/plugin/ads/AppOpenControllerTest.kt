package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.segment.SegmentHooks
import com.app.plugin.segment.SegmentTestSupport
import com.app.plugin.until.Constants.AdShowFailReason
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import java.lang.reflect.Field

/**
 * Test đặc tả vùng app open của [AdsHelper] (plan 1.5.0 Task 8, KB-2): viết và chạy xanh trên mã CŨ trước khi chuyển
 * sang `ads.core.AppOpenController`, làm lưới cho bước chuyển. Mọi lời gọi đi qua hàm public của façade
 * (`showOpenAd`, `showFirstOpen`, `loadOpenAdForThisTurn`, `setFlag4ShowResume`, `suppressNextOpenAd`, `onclickAd`,
 * `setMainActivity`, `checkAfterLoadIdsConfig`, `clearActivity`); field R1 (`listAdsFull`, `listAdsAll`) gán bằng
 * reflection như các test sẵn có. Cờ `isWaitReleadOpenAd` (không public, rời façade trong task này) tìm ở `AdsHelper`
 * rồi ở controller `open`, để cùng một test chạy trên mã cũ và mã mới.
 *
 * Cấu hình: lượt đầu (`openFlagFirst`) dùng `openAdFirstType = 0` → mạng app open (AdMob, MAX); các lượt sau dùng
 * `openAdtype = 1`, `openAdFlagImg = 1` → interstitial AdMob đứng đầu. Nên mạng nhận `showAd` cho biết
 * `openFlagFirst` đang bật hay tắt. `clearActivity` gọi `removeCallbacksAndMessages(null)` nên handler ảo chạy với
 * `honorRemoveAll = true` (review Task 6 m3).
 *
 * Bước A của Task 9 (review Task 8 m3/m4) thêm lưới cho ba lời gọi chéo vùng của vòng show mà trước đó không test nào
 * bắt: `h.markAutoAdShown()` (bật cooldown N màn + ẩn banner), cổng `h.screenCooldown().isActive`
 * (`POST_AD_SCREEN_COOLDOWN`) và `h.finishFull2Transition()` ở `onAdDismissed`/`onAdShowFail` (chuyển cảnh Full2 đang
 * bật kết thúc ngay, không chờ timeout 3 s). `full2TransitionActive` là field R1, đọc/gán bằng reflection.
 */
class AppOpenControllerTest {

    /** Mạng full giả: giữ inner callback của `loadAd`; `showAd` hiện được (hoặc báo lỗi đồng bộ nếu [failSync]). */
    private class OpenNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_open"
        val loads = mutableListOf<AdsLoadInnerCb>()
        val shows = mutableListOf<String>()
        var failSync = false
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_open_${adsType}_data"
        override fun getKeyForResDf(): String = "test_open_${adsType}_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement
            if (failSync) {
                adsShowCb.onAdShowFail(placement, placement, "t", "sync fail")
                return false
            }
            showing = placement to adsShowCb
            adsShowCb.onAdShowed(placement, placement, "t", flagShow)
            return true
        }
        fun dismiss() {
            val (placement, cb) = showing!!
            cb.onAdDismissed(placement, placement, "t")
        }
    }

    /** Mạng banner giả (như `BannerControllerTest.BannerNetwork`): giữ inner callback của `loadAd` để test gọi tay. */
    private class BannerNetwork(type: Int, context: Context) : AdsBaseBanner(type, context) {
        override val eventTag: String get() = "test_banner"
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_banner_data"
        override fun getKeyForResDf(): String = "test_banner_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
    }

    private class AppShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "fail:$err" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed" }
    }

    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded:$placement" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private class Fixture(
        val helper: AdsHelper,
        val open: OpenNetwork,
        val maxOpen: OpenNetwork,
        val inters: OpenNetwork,
        val timers: MainHandlerTimers,
    ) {
        fun plcf(placement: String) = helper.getAdsPlacementConfig(placement, 1)!!
    }

    private fun withOpen(check: Fixture.() -> Unit) = MainHandlerTimers.run(honorRemoveAll = true) { timers ->
        val context = networkContext()
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            openAdFirstType = 0
            openAdtype = 1
            openAdFlagImg = 1
            fullStepCicle.clear()
            fullStepRe.clear()
        }
        val open = OpenNetwork(context, AdsIDDef.AdmobFullOpen)
        val maxOpen = OpenNetwork(context, AdsIDDef.MaxFullOpen)
        val inters = OpenNetwork(context, AdsIDDef.AdmobFullInters)
        setField(
            helper, "listAdsFull",
            mutableMapOf<Int, AdsBaseFull>(
                AdsIDDef.AdmobFullOpen to open,
                AdsIDDef.MaxFullOpen to maxOpen,
                AdsIDDef.AdmobFullInters to inters,
            )
        )
        setField(helper, "listAdsAll", mutableListOf<AdsBase>())
        Fixture(helper, open, maxOpen, inters, timers).check()
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

    private fun transitionActive(helper: AdsHelper): Boolean =
        AdsHelper::class.java.getDeclaredField("full2TransitionActive").apply { isAccessible = true }.getBoolean(helper)

    /** Banner `bn_home` đang hiện: `showBanner` + inner `onLoaded` của mạng banner giả (như `BannerControllerTest`). */
    private fun Fixture.bannerShowing(): BannerNetwork {
        helper.currConfig.bannerStepCicle.add(AdsIDDef.AdmobBanner)
        val banner = BannerNetwork(AdsIDDef.AdmobBanner, networkContext())
        setField(helper, "listAdsBn", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobBanner to banner))
        val parent = mock(ViewGroup::class.java).also {
            `when`(it.isShown).thenReturn(true)
            `when`(it.isAttachedToWindow).thenReturn(true)
        }
        helper.showBanner("home", parent, isCollapse = false, cbLoad = null, cbShow = null)
        val ecmp = mock(AdsEcmp::class.java).also { `when`(it.adObject).thenReturn(mock(View::class.java)) }
        banner.loads[0].onLoaded("bn_home", "bn_home", "id-bn", ecmp, null)
        return banner
    }

    /** `isWaitReleadOpenAd`: ở `AdsHelper` (mã cũ) hoặc ở controller `open` (sau Task 8). */
    private fun waitReload(helper: AdsHelper): Pair<Any, Field> {
        runCatching { AdsHelper::class.java.getDeclaredField("isWaitReleadOpenAd") }.getOrNull()?.let {
            return helper to it.apply { isAccessible = true }
        }
        val controller = AdsHelper::class.java.getDeclaredField("open").apply { isAccessible = true }.get(helper)!!
        return controller to controller.javaClass.getDeclaredField("isWaitReleadOpenAd").apply { isAccessible = true }
    }

    /** Chạy [action] với Firebase giả, trả tên các event Firebase đã ghi theo thứ tự (như `AdsTelemetryTest`). */
    private fun firebaseEvents(action: () -> Unit): List<String> {
        val firebase = mock(FirebaseAnalytics::class.java)
        mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
            analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
            action()
        }
        return mockingDetails(firebase).invocations
            .filter { it.method.name == "logEvent" }
            .map { it.arguments[0] as String }
    }

    private fun nowSec() = System.currentTimeMillis() / 1000

    // ═══ Brief (1): cờ resume ═══

    @Test fun `showOpenAd resume khi openFlag4Resume false - false, dung mot OPEN_FLAG_RESUME, khong goi mang - bat co thi hien`() = withOpen {
        val cb = AppShowCb()
        assertFalse("mặc định cờ resume tắt", helper.getFlag4ShowResume())

        ServerEventCapture().use { server ->
            assertFalse(helper.showOpenAd("resume", cb))
            assertEquals(listOf(PlacementDefault.PLFullResume to AdShowFailReason.OPEN_FLAG_RESUME), server.showFails(1))
        }
        assertEquals("không gọi mạng", emptyList<String>(), open.shows + maxOpen.shows + inters.shows)
        assertEquals(emptyList<String>(), cb.events)
        assertFalse(helper.isFullShowing())

        helper.setFlag4ShowResume(true)
        assertTrue(helper.getFlag4ShowResume())
        assertTrue(helper.showOpenAd("resume", cb))
        assertEquals(listOf(PlacementDefault.PLFullResume), open.shows)
        assertEquals(listOf("showed"), cb.events)
        assertTrue("đang hiện: cờ full-screen bật", helper.isFullShowing())
        assertTrue(plcf(PlacementDefault.PLFullResume).isShowing)
        assertTrue("openTimeShow đặt lúc hiện", nowSec() - helper.openTimeShow <= 1)

        helper.openTimeShow = 0
        open.dismiss()
        assertEquals(listOf("showed", "dismissed"), cb.events)
        assertFalse(helper.isFullShowing())
        assertFalse(plcf(PlacementDefault.PLFullResume).isShowing)
        assertTrue("openTimeShow đặt lúc dismiss", nowSec() - helper.openTimeShow <= 1)
    }

    // ═══ Brief (2): suppressNextOpenAd ═══

    @Test fun `suppressNextOpenAd - resume bi chan NOT_ALLOW_SHOW, full_splash van toi vong show`() = withOpen {
        helper.setFlag4ShowResume(true)
        helper.suppressNextOpenAd(5000)
        assertTrue(helper.suppressOpenAdUntilMillis > System.currentTimeMillis())
        val cb = AppShowCb()

        ServerEventCapture().use { server ->
            assertFalse(helper.showOpenAd("resume", cb))
            assertEquals(listOf(PlacementDefault.PLFullResume to AdShowFailReason.NOT_ALLOW_SHOW), server.showFails(1))
        }
        assertEquals(emptyList<String>(), open.shows)
        assertEquals(emptyList<String>(), cb.events)

        assertTrue("full_splash miễn suppress", helper.showOpenAd(PlacementDefault.PLFullSplash, cb))
        assertEquals(listOf(PlacementDefault.PLFullSplash), open.shows)
        assertEquals(listOf("showed"), cb.events)
    }

    // ═══ Brief (3): load khi đang load ═══

    @Test fun `loadOpenAdForThisTurn khi dang load - cb2 gan vao luot (2_4_0 CARRY-ADS), mang khong goi them - ca hai nhan loaded`() = withOpen {
        val cb1 = AppLoadCb()
        val cb2 = AppLoadCb()
        assertFalse(helper.isFirstOpenReady())

        helper.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash, cb1)
        timers.runPosted()
        assertEquals("lượt đầu gọi mạng app open AdMob đúng một lần", 1, open.loads.size)
        assertTrue(plcf(PlacementDefault.PLFullSplash).isLoading)

        helper.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash, cb2)
        timers.runPosted()
        assertEquals(emptyList<String>(), cb2.events)
        assertEquals("mạng không gọi thêm", 1, open.loads.size)
        assertEquals(0, maxOpen.loads.size)

        open.loads[0].onLoaded(PlacementDefault.PLFullSplash, PlacementDefault.PLFullSplash, "id", null, null)
        timers.runPosted()
        assertEquals(listOf("loaded:${PlacementDefault.PLFullSplash}"), cb1.events)
        assertEquals("cb2 nhận kết quả thật đúng một lần", listOf("loaded:${PlacementDefault.PLFullSplash}"), cb2.events)
        assertTrue(helper.isFirstOpenReady())
        assertFalse(plcf(PlacementDefault.PLFullSplash).isLoading)
    }

    @Test fun `loadOpenCircle - mang loi het danh sach thi onLoadFailed list empty mot lan - luot cu khong toi app`() = withOpen {
        val cb = AppLoadCb()
        helper.loadOpenAdForThisTurn("resume", cb)
        timers.runPosted()
        assertEquals(1, open.loads.size)
        open.loads[0].onLoadFailed("full_resume", "full_resume", "id", "no fill")
        timers.runPosted()
        assertEquals("sang mạng kế (MAX app open)", 1, maxOpen.loads.size)
        maxOpen.loads[0].onLoadFailed("full_resume", "full_resume", "id", "no fill")
        timers.runPosted()
        assertEquals(listOf("fail:list empty"), cb.events)
        assertFalse(plcf("full_resume").isLoading)

        // Lượt mới; callback muộn của lượt cũ (loadCycle đã tăng) không tới app, không bật isLoaded.
        val cb2 = AppLoadCb()
        helper.loadOpenAdForThisTurn("resume", cb2)
        timers.runPosted()
        open.loads[0].onLoaded("full_resume", "full_resume", "id", null, null)
        timers.runPosted()
        assertEquals(emptyList<String>(), cb2.events)
        assertFalse(plcf("full_resume").isLoaded)
        assertEquals(listOf("fail:list empty"), cb.events)
    }

    // ═══ openFlagFirst, click, giao thức một callback ═══

    @Test fun `showFirstOpen tat openFlagFirst - lan resume sau dung danh sach thuong (interstitial dau)`() = withOpen {
        helper.setFlag4ShowResume(true)
        val cb = AppShowCb()
        assertTrue(helper.showFirstOpen(cb))
        assertEquals("lượt đầu: mạng app open", listOf(PlacementDefault.PLFullSplash), open.shows)
        open.dismiss()
        assertEquals(listOf("showed", "dismissed"), cb.events)

        helper.openTimeShow = 0
        assertTrue(helper.showOpenAd("resume", AppShowCb()))
        assertEquals("sau showFirstOpen: danh sách thường", listOf(PlacementDefault.PLFullResume), inters.shows)
        assertEquals(listOf(PlacementDefault.PLFullSplash), open.shows)
    }

    @Test fun `setAfterFirstOpenShow tat openFlagFirst - splash dung danh sach thuong`() = withOpen {
        helper.setAfterFirstOpenShow()
        assertTrue(helper.showOpenAd(PlacementDefault.PLFullSplash, AppShowCb()))
        assertEquals(listOf(PlacementDefault.PLFullSplash), inters.shows)
        assertEquals(emptyList<String>(), open.shows)
    }

    @Test fun `sau click quang cao - resume bi chan SHOW_AFTER_CLICK_AD mot lan va co click ha, lan sau hien`() = withOpen {
        helper.setFlag4ShowResume(true)
        helper.onclickAd()
        assertTrue(helper.isClickAds())
        val cb = AppShowCb()

        ServerEventCapture().use { server ->
            assertFalse(helper.showOpenAd("resume", cb))
            assertEquals(listOf(PlacementDefault.PLFullResume to AdShowFailReason.SHOW_AFTER_CLICK_AD), server.showFails(1))
        }
        assertFalse("cờ click hạ sau lượt bị chặn", helper.isClickAds())
        assertEquals(emptyList<String>(), open.shows)

        assertTrue(helper.showOpenAd("resume", cb))
        assertEquals(listOf("showed"), cb.events)
    }

    @Test fun `dang hien full - showOpenAd AD_FULL_SHOWING - dismiss ba lan chi mot onAdDismissed`() = withOpen {
        helper.setFlag4ShowResume(true)
        val cb = AppShowCb()
        assertTrue(helper.showOpenAd(PlacementDefault.PLFullSplash, cb))

        ServerEventCapture().use { server ->
            assertFalse(helper.showOpenAd("resume", AppShowCb()))
            assertEquals(listOf(PlacementDefault.PLFullResume to AdShowFailReason.AD_FULL_SHOWING), server.showFails(1))
        }

        // Mạng báo dismiss lặp: giao thức flagAdsFull4Dissmiss (set(-2) → +1 khi hiện → +1 mỗi dismiss, set(-10) khi báo)
        // chỉ cho một onAdDismissed tới app.
        open.dismiss()
        open.dismiss()
        open.dismiss()
        assertEquals(listOf("showed", "dismissed"), cb.events)
        assertFalse(helper.isFullShowing())
        assertEquals(0, helper.flagAdsFullShowing.get())
    }

    @Test fun `mang dau loi dong bo, mang sau hien - app chi nhan showed roi dismissed (WaterfallShowFailHold)`() = withOpen {
        open.failSync = true
        val cb = AppShowCb()

        assertTrue(helper.showOpenAd(PlacementDefault.PLFullSplash, cb))
        assertEquals(listOf(PlacementDefault.PLFullSplash), open.shows)
        assertEquals(listOf(PlacementDefault.PLFullSplash), maxOpen.shows)
        assertEquals("lỗi của mạng đầu không tới app giữa vòng", listOf("showed"), cb.events)
        assertTrue(helper.isFullShowing())

        maxOpen.dismiss()
        assertEquals(listOf("showed", "dismissed"), cb.events)
        assertFalse(helper.isFullShowing())
    }

    @Test fun `moi mang loi dong bo - app nhan dung mot onAdShowFail cuoi vong, co full-screen 0`() = withOpen {
        open.failSync = true
        maxOpen.failSync = true
        val cb = AppShowCb()

        assertFalse(helper.showOpenAd(PlacementDefault.PLFullSplash, cb))

        assertEquals(listOf(PlacementDefault.PLFullSplash), open.shows)
        assertEquals(listOf(PlacementDefault.PLFullSplash), maxOpen.shows)
        assertEquals(listOf("fail:sync fail"), cb.events)
        assertFalse(helper.isFullShowing())
        assertFalse(plcf(PlacementDefault.PLFullSplash).isShowing)
    }

    // ═══ Hook host: setMainActivity (onHostSet), checkAfterLoadIdsConfig (reloadSplash), clearActivity ═══

    @Test fun `setMainActivity khi dang cho reload splash - log SET_AC_WAIT_RELOAD_SPLASH mot lan, ha co, khong load`() = withOpen {
        val activity = mock(Activity::class.java)
        // Không chờ: không event.
        assertEquals(emptyList<String>(), firebaseEvents { helper.setMainActivity(activity) })

        val (owner, field) = waitReload(helper)
        field.setBoolean(owner, true)
        val fb = firebaseEvents {
            helper.setMainActivity(activity)
            helper.setMainActivity(activity)
        }
        assertEquals("một event, lần hai cờ đã hạ", listOf(EventNames.SET_AC_WAIT_RELOAD_SPLASH), fb)
        assertFalse(field.getBoolean(owner))
        timers.runPosted()
        assertEquals("reloadSplash không nạp lại (thiết kế SDK)", 0, open.loads.size + maxOpen.loads.size + inters.loads.size)
    }

    @Test fun `checkAfterLoadIdsConfig openFirstCfChange - ha co, khong nap lai splash, khong event, ca khi chua co Activity`() = withOpen {
        helper.currConfig.openFirstCfChange = true
        val fb = firebaseEvents { helper.checkAfterLoadIdsConfig() }
        timers.runPosted()

        assertFalse(helper.currConfig.openFirstCfChange)
        assertEquals(emptyList<String>(), fb)
        assertEquals(0, open.loads.size + maxOpen.loads.size + inters.loads.size)
        val (owner, field) = waitReload(helper)
        assertFalse("reloadSplash không đặt cờ chờ", field.getBoolean(owner))
    }

    /** App open không có câu lệnh nào trong `clearActivity`: trạng thái vùng (cờ resume, openFlagFirst, splash đã load) giữ nguyên. */
    @Test fun `clearActivity khong cham trang thai app open`() = withOpen {
        helper.setFlag4ShowResume(true)
        helper.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash)
        timers.runPosted()
        open.loads[0].onLoaded(PlacementDefault.PLFullSplash, PlacementDefault.PLFullSplash, "id", null, null)
        assertTrue(helper.isFirstOpenReady())

        helper.clearActivity()

        assertTrue(helper.getFlag4ShowResume())
        assertTrue(helper.isFirstOpenReady())
        assertTrue("openFlagFirst vẫn bật: splash dùng mạng app open", helper.showOpenAd(PlacementDefault.PLFullSplash, AppShowCb()))
        assertEquals(listOf(PlacementDefault.PLFullSplash), open.shows)
    }

    // ═══ Task 9 bước A (review Task 8 m3): cooldown N màn sau quảng cáo ═══

    @Test fun `cooldown man hinh - app open hien bat cooldown va an banner, lan sau POST_AD_SCREEN_COOLDOWN khong goi mang, doi man thi hien lai`() = withOpen {
        bannerShowing()
        assertTrue("banner đang hiện", helper.bnIsShow)
        helper.setPostAdScreenCooldown(1)
        helper.setFlag4ShowResume(true)
        assertFalse(helper.isPostAdScreenCooldownActive())
        val cb = AppShowCb()

        assertTrue(helper.showOpenAd("resume", cb))
        assertTrue("markAutoAdShown: cooldown bật", helper.isPostAdScreenCooldownActive())
        assertFalse("markAutoAdShown: banner đang hiện bị ẩn", helper.bnIsShow)
        open.dismiss()
        assertEquals(listOf("showed", "dismissed"), cb.events)

        helper.openTimeShow = 0 // chỉ còn cooldown chặn (không phải openDeltaTime)
        val blocked = AppShowCb()
        ServerEventCapture().use { server ->
            assertFalse(helper.showOpenAd("resume", blocked))
            assertEquals(
                listOf(PlacementDefault.PLFullResume to AdShowFailReason.POST_AD_SCREEN_COOLDOWN),
                server.showFails(1)
            )
        }
        assertEquals("lượt bị chặn không gọi mạng", listOf(PlacementDefault.PLFullResume), open.shows)
        assertEquals(emptyList<String>(), maxOpen.shows + inters.shows)
        assertEquals(emptyList<String>(), blocked.events)

        helper.recordScreenChange()
        assertFalse("đủ 1 màn: cooldown hết", helper.isPostAdScreenCooldownActive())
        val again = AppShowCb()
        assertTrue(helper.showOpenAd("resume", again))
        assertEquals(listOf(PlacementDefault.PLFullResume, PlacementDefault.PLFullResume), open.shows)
        assertEquals(listOf("showed"), again.events)
    }

    // ═══ Task 9 bước A (review Task 8 m4): chuyển cảnh Full2 đang bật kết thúc ngay ở dismiss / show fail ═══

    @Test fun `chuyen canh Full2 dang bat - app open dismiss ket thuc ngay, khong cho timeout 3 s`() = withOpen {
        setField(helper, "full2TransitionActive", true)
        val cb = AppShowCb()

        assertTrue(helper.showOpenAd(PlacementDefault.PLFullSplash, cb))
        assertTrue("lúc hiện chưa kết thúc", transitionActive(helper))
        open.dismiss()

        assertFalse("dismiss của app open kết thúc chuyển cảnh ngay", transitionActive(helper))
        assertEquals(listOf("showed", "dismissed"), cb.events)
        assertEquals("không có hẹn giờ nào phải chạy", emptyList<Long>(), timers.pendingDelays)
    }

    @Test fun `chuyen canh Full2 dang bat - moi mang loi dong bo, onAdShowFail cuoi vong ket thuc ngay`() = withOpen {
        setField(helper, "full2TransitionActive", true)
        open.failSync = true
        maxOpen.failSync = true
        val cb = AppShowCb()

        assertFalse(helper.showOpenAd(PlacementDefault.PLFullSplash, cb))

        assertFalse("onAdShowFail (chạy ở settle) kết thúc chuyển cảnh ngay", transitionActive(helper))
        assertEquals(listOf("fail:sync fail"), cb.events)
        assertEquals(emptyList<Long>(), timers.pendingDelays)
    }

    // ═══ 2.4.2 SPLASH: full_splash miễn guardrail segment (như fork), placement khác vẫn chặn ═══

    /** Guardrail RC 60 s, fullscreen cuối cách 10 s (đồng hồ bền), segment nạp xong; dọn seam sau [check]. */
    private fun withGuardrailActive(check: () -> Unit) {
        SegmentTestSupport.reset()
        try {
            SegmentTestSupport.rc(longs = mapOf("seg_cadence_guardrail_seconds" to 60L))
            val store = SegmentTestSupport.newStore().apply { put(SegmentHooks.KEY_LAST_FULLSCREEN, System.currentTimeMillis() - 10_000L) }
            SegmentHooks.init(SegmentTestSupport.prefsContext(store))
            assertTrue("guardrail đang chặn", SegmentHooks.blocksFullscreen(System.currentTimeMillis()))
            check()
        } finally {
            SegmentTestSupport.reset()
        }
    }

    @Test fun `SP1 guardrail 60 s, fullscreen 10 s truoc - showOpenAd full_splash toi vong show, khong seg_guardrail`() = withOpen {
        withGuardrailActive {
            val cb = AppShowCb()
            assertTrue("full_splash miễn guardrail", helper.showOpenAd(PlacementDefault.PLFullSplash, cb))
            assertEquals(listOf(PlacementDefault.PLFullSplash), open.shows)
            assertEquals(listOf("showed"), cb.events)
        }
    }

    @Test fun `SP2 guardrail 60 s - showOpenAd full_resume van bi seg_guardrail, khong goi mang`() = withOpen {
        withGuardrailActive {
            val cb = AppShowCb()
            helper.setFlag4ShowResume(true)
            ServerEventCapture().use { server ->
                assertFalse(helper.showOpenAd("resume", cb))
                assertEquals(listOf(PlacementDefault.PLFullResume to SegmentHooks.REASON_GUARDRAIL), server.showFails(1))
            }
            assertEquals(emptyList<String>(), open.shows + maxOpen.shows + inters.shows)
        }
    }
}
