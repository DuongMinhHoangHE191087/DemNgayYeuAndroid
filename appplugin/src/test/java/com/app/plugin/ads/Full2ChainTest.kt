package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.segment.SegmentHooks
import com.app.plugin.segment.SegmentTestSupport
import com.app.plugin.until.Constants.AdShowFailReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Test đặc tả trình tự chuyển cảnh/cover của chuỗi Full2 (plan 1.5.0 Task 12, KB-2): viết và chạy xanh trên mã CŨ (thân
 * trong `AdsHelper`) trước khi chuyển sang `ads.core.Full2Chain`. Vùng này chỉ lộ lỗi trên máy ở 1.3.x (cover đen sau
 * interstitial, xoay, `WindowLeaked`), nên khoá thứ tự `onTransitionChanged` mà app nhận, hàng đợi `pendingFull2`,
 * cửa sổ `clearActivity` và nhánh "không có gì đã load". Đi qua hàm public/`internal` của façade; field R1
 * (`listAdsFull`, `listAdsAll`, `fullScreenAdPolicy`, `pendingFull2`, `full2TransitionActive`) đọc/gán bằng reflection
 * như các test sẵn có. `full2MainHandler` là Handler ảo của [MainHandlerTimers] (`honorRemoveAll`: `clearActivity`
 * gọi `removeCallbacksAndMessages(null)`), `post` chỉ chạy khi test gọi `runPosted()`.
 *
 * Test 6–14 (1.5.0 Task 13 bước A, review Task 12 I1–I2): đường xếp Full2 chờ host resume — đường thường trên API
 * 37 — (callback của app đi qua hàng đợi, lambda bỏ show khi host bị thay/đang đóng/đã huỷ, show trực tiếp xoá hàng
 * đợi cũ) và hẹn giờ hạ cover (begin/finish gỡ timeout 3 s và fallback 1,5 s; `onAdShowed` không kèm impression hạ
 * cover sau đúng 1,5 s). Lỗi hai vùng này chỉ lộ trên máy (`BadToken`/`WindowLeaked`, cover đen).
 */
class Full2ChainTest {

    /** Mạng Full2 (native full AdMob): có quảng cáo hay không theo [loaded]; ghi load/show/prepare/cancel. */
    private class Full2Network(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "test_full2_chain"
        var loaded = true
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenAnswer { loaded } }
        val loads = mutableListOf<String>()
        val shows = mutableListOf<Pair<String, Int>>()
        var prepares = 0
        var cancels = 0
        /** Chạy trong `clearActivity()` của mạng (lượt 2 của `AdsHelper.clearActivity`). */
        var onClearActivity: (() -> Unit)? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_full2_chain_data"
        override fun getKeyForResDf(): String = "test_full2_chain_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement to flagShow
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
        override fun prepareChainedShow() { prepares++ }
        override fun cancelChainedShow() { cancels++ }
        override fun clearActivity() {
            super.clearActivity()
            onClearActivity?.invoke()
        }
    }

    /** Ghi mọi `onTransitionChanged` app nhận (đã bỏ lần báo lúc đăng ký). */
    private class RecordingTransition : Full2TransitionListener {
        val events = mutableListOf<Boolean>()
        override fun onTransitionChanged(active: Boolean) { events += active }
    }

    /** Callback show của app: ghi `showed:<placement>`, `impression:`, `click:`, `dismissed:`, `fail:` theo thứ tự nhận. */
    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed:$placement" }
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { events += "impression:$placement" }
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) { events += "click:$placement" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed:$placement" }
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "fail:$placement" }
    }

    private class Fixture(
        val helper: AdsHelper,
        val network: Full2Network,
        val transition: RecordingTransition,
        val activity: Activity,
        val timers: MainHandlerTimers,
    ) {
        val policy: FullScreenAdPolicy get() = field(helper, "fullScreenAdPolicy")
        val pendingFull2: Any? get() = field(helper, "pendingFull2")
        val transitionActive: Boolean get() = field(helper, "full2TransitionActive")
    }

    /**
     * `AdsHelper` có host [Fixture.activity] (đã resume), Full2 một bước = [Fixture.network] (trong `listAdsFull` và
     * `listAdsAll`), app đăng ký [Fixture.transition]; `full2FlagShow` 1.
     */
    private fun withFull2(check: Fixture.() -> Unit) = MainHandlerTimers.run(honorRemoveAll = true) { timers ->
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            full2FlagShow = 1
            full2StepCicle.clear()
            full2StepCicle.add(AdsIDDef.AdmobFullNt)
            full2StepRe.clear()
        }
        val network = Full2Network(context)
        setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to network))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>(network))
        val activity = mock(Activity::class.java)
        helper.setMainActivity(activity)
        field<FullScreenAdPolicy>(helper, "fullScreenAdPolicy").onHostActivityResumed()
        val transition = RecordingTransition()
        helper.setFull2TransitionListener(transition)
        assertEquals("đăng ký báo trạng thái hiện tại (tắt) đồng bộ", listOf(false), transition.events)
        transition.events.clear()
        Fixture(helper, network, transition, activity, timers).check()
    }

    @Test fun `1 begin bao true mot lan, finish bao false mot lan, finish lan hai khi da tat van bao false (co y, cover den speakerclean)`() =
        withFull2 {
            helper.beginFull2Transition("full_x")

            assertTrue(transitionActive)
            assertEquals("báo qua full2MainHandler.post, chưa đồng bộ", emptyList<Boolean>(), transition.events)
            assertEquals("load Full2 sau cha ngay khi bắt đầu", listOf("full_2_x"), network.loads)
            timers.runPosted()
            assertEquals(listOf(true), transition.events)
            assertEquals("dựng sẵn Full2 sau cha", 1, network.prepares)
            assertEquals("chưa hẹn timeout 3 s: cha có thể mở lâu", emptyList<Long>(), full2Timers(timers))

            helper.finishFull2Transition()
            assertFalse(transitionActive)
            timers.runPosted()
            assertEquals(listOf(true, false), transition.events)
            assertEquals("chuỗi không nối: huỷ dialog dựng sẵn", 1, network.cancels)

            // Đã tắt mà vẫn báo false: host có thể tự bật cover khi quảng cáo toàn màn hình bắt đầu (return sớm từng để
            // lại cover đen sau interstitial). Không huỷ dựng sẵn lần hai.
            helper.finishFull2Transition()
            timers.runPosted()
            assertEquals(listOf(true, false, false), transition.events)
            assertEquals(1, network.cancels)
        }

    @Test fun `2 showFull2 trong cua so clearActivity - HOST_DESTROYED, khong pendingFull2, khong bao false lan hai`() =
        withFull2 {
            helper.beginFull2Transition("full_home")
            timers.runPosted()
            assertEquals(listOf(true), transition.events)
            val cb = object : AdsShowCb {}
            // Cha (native full) phát dismiss ở lượt 2 của clearActivity, callback dismiss nối Full2.
            network.onClearActivity = { helper.showFull2("home", cb) }

            val fails = ServerEventCapture().use { server ->
                helper.clearActivity()
                server.showFails(1)
            }
            timers.runPosted()

            assertEquals(listOf("full_2_home" to AdShowFailReason.HOST_DESTROYED), fails)
            assertNull("không xếp Full2 chờ host mới", pendingFull2)
            assertEquals("clearActivity báo false đúng một lần, showFull2 trong cửa sổ không báo thêm", listOf(true, false), transition.events)
            assertEquals("không Full2 nào hiện", emptyList<Pair<String, Int>>(), network.shows)
            assertFalse(transitionActive)
            assertEquals("không còn hẹn giờ Full2 (timeout 3 s, fallback 1,5 s)", emptyList<Long>(), full2Timers(timers))
        }

    @Test fun `3 host chua resume - showFull2 xep pendingFull2, onMainActivityResumed hien Full2 dung mot lan voi flagShow 0`() =
        withFull2 {
            policy.onHostActivityPaused()
            assertTrue(policy.shouldDeferFull2UntilHostResumed())

            helper.showFull2("home", object : AdsShowCb {})

            assertNotNull("xếp chờ host resume", pendingFull2)
            assertEquals(emptyList<Pair<String, Int>>(), network.shows)

            helper.onMainActivityResumed(activity)
            assertNull("lấy khỏi hàng đợi ngay khi resume", pendingFull2)
            assertEquals("hiện qua full2MainHandler.post, chưa đồng bộ", emptyList<Pair<String, Int>>(), network.shows)
            timers.runPosted()

            assertEquals(listOf("full_2_home" to 0), network.shows)
            assertNull(pendingFull2)
        }

    @Test fun `4 suppressNextOpenAd dang hieu luc - showFull2 khong hien, bao false, khong load`() = withFull2 {
        helper.suppressNextOpenAd(5000)

        helper.showFull2("home", object : AdsShowCb {})
        timers.runPosted()

        assertEquals(emptyList<Pair<String, Int>>(), network.shows)
        assertEquals(listOf(false), transition.events)
        assertEquals(emptyList<String>(), network.loads)
        assertNull(pendingFull2)
    }

    @Test fun `5 khong co gi da load - ket thuc chuyen canh va load Full2 ngay, khong xep pendingFull2 du host chua resume`() =
        withFull2 {
            network.loaded = false
            policy.onHostActivityPaused()

            helper.showFull2("home", object : AdsShowCb {})
            timers.runPosted()

            assertNull("hạ cover trước khi host resume, không xếp", pendingFull2)
            assertEquals(listOf(false), transition.events)
            assertEquals(listOf("full_2_home"), network.loads)
            assertEquals(emptyList<Pair<String, Int>>(), network.shows)
        }

    /** Host tạm dừng → `showFull2("home", cb)` xếp chờ → host resume: lambda hiện Full2 đã được post, chưa chạy. */
    private fun Fixture.queueFull2ThenResume(cb: AdsShowCb) {
        policy.onHostActivityPaused()
        helper.showFull2("home", cb)
        assertNotNull("xếp chờ host resume", pendingFull2)
        helper.onMainActivityResumed(activity)
        assertNull(pendingFull2)
        assertEquals("hiện qua full2MainHandler.post, chưa đồng bộ", emptyList<Pair<String, Int>>(), network.shows)
    }

    @Test fun `SEG Full2 hien dong dau dong ho guardrail cua segment nhu fork`() = withFull2 {
        val store = SegmentTestSupport.newStore()
        SegmentTestSupport.reset(null)
        try {
            SegmentHooks.init(SegmentTestSupport.prefsContext(store))
            helper.showFull2("home", RecordingShowCb())
            assertEquals(listOf("full_2_home" to 0), network.shows)
            assertTrue("Full2 hiện → đồng hồ bền", (store[SegmentHooks.KEY_LAST_FULLSCREEN] as? Long ?: 0L) > 0L)
        } finally {
            SegmentTestSupport.reset(null)
        }
    }

    @Test fun `6 Full2 xep cho host resume - callback cua app di qua hang doi, nhan showed full_2_home`() = withFull2 {
        val cb = RecordingShowCb()
        queueFull2ThenResume(cb)

        timers.runPosted()

        assertEquals(listOf("full_2_home" to 0), network.shows)
        // Đọc callback trước khi xoá hàng đợi: đọc sau (hay truyền null) làm mất callback duy nhất của app khi app gọi
        // showFull2 lúc host chưa resume, và mất showed/impression/click `full_2_*` của Full2 nối sau cha.
        assertEquals(listOf("showed:full_2_home"), cb.events)
    }

    @Test fun `7 Full2 xep cho host resume - host bi thay truoc khi post chay, khong hien`() = withFull2 {
        val cb = RecordingShowCb()
        queueFull2ThenResume(cb)
        helper.setMainActivity(mock(Activity::class.java))

        timers.runPosted()

        assertEquals("không hiện Full2 lên host cũ (BadToken/WindowLeaked)", emptyList<Pair<String, Int>>(), network.shows)
        assertEquals(emptyList<String>(), cb.events)
    }

    @Test fun `8 Full2 xep cho host resume - host dang dong khi post chay, khong hien`() = withFull2 {
        val cb = RecordingShowCb()
        queueFull2ThenResume(cb)
        `when`(activity.isFinishing).thenReturn(true)

        timers.runPosted()

        assertEquals(emptyList<Pair<String, Int>>(), network.shows)
        assertEquals(emptyList<String>(), cb.events)
    }

    @Test fun `9 Full2 xep cho host resume - host da huy khi post chay, khong hien`() = withFull2 {
        val cb = RecordingShowCb()
        queueFull2ThenResume(cb)
        `when`(activity.isDestroyed).thenReturn(true)

        timers.runPosted()

        assertEquals(emptyList<Pair<String, Int>>(), network.shows)
        assertEquals(emptyList<String>(), cb.events)
    }

    @Test fun `10 da xep roi host resume qua policy - showFull2 truc tiep hien va xoa hang doi cu`() = withFull2 {
        policy.onHostActivityPaused()
        helper.showFull2("home", RecordingShowCb())
        assertNotNull(pendingFull2)
        policy.onHostActivityResumed()
        val cb = RecordingShowCb()

        helper.showFull2("home", cb)

        assertEquals(listOf("full_2_home" to 0), network.shows)
        assertEquals(listOf("showed:full_2_home"), cb.events)
        assertNull("hàng đợi cũ bị bỏ: lần resume kế không hiện Full2 cũ", pendingFull2)
    }

    @Test fun `11 begin lan hai go timeout 3 s con treo cua chuoi truoc`() = withFull2 {
        helper.beginFull2Transition("full_home")
        policy.onHostActivityPaused()
        helper.showFull2("home", RecordingShowCb())
        assertEquals("showFull2 hẹn timeout 3 s khi đang chuyển cảnh", listOf(3_000L), full2Timers(timers))

        helper.beginFull2Transition("full_home")

        // Timeout cũ để lại sẽ kết thúc sớm chuyển cảnh của chuỗi sau (chớp đen).
        assertEquals(emptyList<Long>(), full2Timers(timers))
        assertTrue(transitionActive)
    }

    @Test fun `12 finish go timeout 3 s con treo`() = withFull2 {
        helper.beginFull2Transition("full_home")
        policy.onHostActivityPaused()
        helper.showFull2("home", RecordingShowCb())
        assertEquals(listOf(3_000L), full2Timers(timers))

        helper.finishFull2Transition()

        assertEquals(emptyList<Long>(), full2Timers(timers))
        assertFalse(transitionActive)
    }

    @Test fun `13 begin va finish go fallback 1,5 s con treo`() {
        withFull2 {
            helper.beginFull2Transition("full_home")
            helper.showFull2("home", RecordingShowCb())
            assertEquals(listOf(1_500L), full2Timers(timers))

            helper.beginFull2Transition("full_home")

            assertEquals("begin gỡ fallback của Full2 trước", emptyList<Long>(), full2Timers(timers))
        }
        withFull2 {
            helper.beginFull2Transition("full_home")
            helper.showFull2("home", RecordingShowCb())
            assertEquals(listOf(1_500L), full2Timers(timers))

            helper.finishFull2Transition()

            assertEquals("finish gỡ fallback", emptyList<Long>(), full2Timers(timers))
        }
    }

    @Test fun `14 Full2 showed khong kem impression - fallback ha cover sau dung 1,5 s`() = withFull2 {
        helper.beginFull2Transition("full_home")
        timers.runPosted()
        assertEquals(listOf(true), transition.events)
        transition.events.clear()
        val cb = RecordingShowCb()

        helper.showFull2("home", cb)

        assertEquals(listOf("full_2_home" to 0), network.shows)
        assertEquals(listOf("showed:full_2_home"), cb.events)
        assertEquals("onAdShowed thay timeout 3 s bằng fallback 1,5 s", listOf(1_500L), full2Timers(timers))
        assertTrue(transitionActive)

        timers.advanceTo(timers.nowMs + 1_499L)
        assertTrue("chưa tới 1,5 s", transitionActive)
        timers.advanceTo(timers.nowMs + 1L)

        // Mạng không bao giờ báo impression: không có fallback thì cover đứng tới timeout 3 s hoặc mãi (cover đen).
        assertFalse(transitionActive)
        assertEquals(listOf(false), transition.events)
        assertEquals(emptyList<Long>(), full2Timers(timers))
    }

    private companion object {
        /** Hẹn giờ của chuỗi Full2 còn chờ: timeout chuyển cảnh 3 s, fallback impression 1,5 s (watchdog load 90 s bỏ qua). */
        fun full2Timers(timers: MainHandlerTimers): List<Long> = timers.pendingDelays.filter { it == 3_000L || it == 1_500L }

        fun setField(helper: AdsHelper, name: String, value: Any?) {
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> field(helper: AdsHelper, name: String): T =
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper) as T
    }
}
