package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.`when`

/**
 * Task I2: watchdog cấp slot cho [AdsPlacementEcmpBase.isLoading], cùng mẫu với watchdog của [AdsPlacementConfig].
 * Mạng không bao giờ callback thì slot không kẹt "đang load" mãi: hết hạn thì báo `onLoadFailed` đúng một lần qua
 * `adsLoadCb` (waterfall của helper đi tiếp), slot nhận load mới; callback tới muộn của lượt đó không tới ai.
 */
class SlotLoadWatchdogTest {

    private val first = "ca-app-pub-3940256099942544/first"
    private val second = "ca-app-pub-3940256099942544/second"

    /** Hẹn giờ của mọi `Handler` dựng trong khối: giữ runnable, test tự chạy như lúc hết hạn. */
    private class Timers {
        val pending = mutableListOf<Pair<Runnable, Long>>()
        fun fireAll() {
            val due = pending.toList()
            pending.clear()
            due.forEach { it.first.run() }
        }
    }

    private fun <T> withTimers(block: (Timers) -> T): T {
        val timers = Timers()
        return mockConstruction(Handler::class.java) { handler, _ ->
            `when`(handler.postDelayed(any(Runnable::class.java), anyLong())).thenAnswer {
                timers.pending += (it.arguments[0] as Runnable) to (it.arguments[1] as Long)
                true
            }
            doAnswer { inv -> timers.pending.removeIf { it.first === inv.arguments[0] }; null }
                .`when`(handler).removeCallbacks(any(Runnable::class.java))
        }.use { block(timers) }
    }

    /** Slot mạng giả: `tryLoad` gửi request và không bao giờ tự callback; `late*` làm như listener của mạng thật. */
    private class Probe(private val queue: SdkReadyQueue? = null) :
        AdsPlBase(mock(Context::class.java), mock(AdsBase::class.java), 0, "full_test") {
        var requests = 0
        private var ecmpLoad: AdsEcmp? = null
        override val eventTag: String get() = "test_probe"
        val state get() = adsPlacementEcmp
        override fun onInit() = Unit
        override fun tryLoad() {
            if (queue != null && holdUntilReady(queue)) return
            val item = state.adEcmps!!.getEcmpForLoad()!!
            requests++
            ecmpLoad = item
            state.isLoading = true
            state.isHasLoad = true
            item.isLoading = true
        }
        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int) = false

        fun lateSuccess(ad: Any) {
            val item = ecmpLoad!!
            item.isLoading = false
            item.isLoaded = true
            item.adObject = ad
            state.isLoading = false
            state.isloaded = true
            state.adsLoadCb?.onLoaded(loadPl, state.originPl, item.adsId, item, this)
            onFinishLoad()
        }

        fun lateFail() {
            val item = ecmpLoad!!
            item.isLoading = false
            item.adObject = null
            state.adEcmps!!.idxCurrEcpm++
            if (!state.flag4Load && state.adEcmps!!.idxCurrEcpm < state.adEcmps!!.list.size) {
                tryLoad()
            } else {
                if (!state.flag4Load) {
                    state.isLoading = false
                    state.isloaded = false
                    state.adsLoadCb?.onLoadFailed(loadPl, state.originPl, "", "no fill")
                }
                onFinishLoad()
            }
        }
    }

    private class Callback : AdsLoadInnerCb {
        val loaded = mutableListOf<AdsEcmp?>()
        val failures = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            loaded += ecmp
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            failures += error
        }
    }

    private fun probe(queue: SdkReadyQueue? = null) =
        Probe(queue).apply { setActivity(mock(Activity::class.java)); parserEcpms("$first;$second") }

    @Test
    fun `slot khong callback - het han bao fail dung mot lan, het dang load, load lai duoc`() = withTimers { timers ->
        val p = probe()
        val cb = Callback()
        p.load("full_test", cb)
        assertTrue(p.state.isLoading)
        assertEquals(listOf(60_000L), timers.pending.map { it.second })

        timers.fireAll()

        assertEquals(listOf("load timeout"), cb.failures)
        assertFalse(p.state.isLoading)
        assertTrue(p.state.adEcmps!!.list.none { it.isLoading })
        timers.fireAll()
        assertEquals("watchdog không báo lần hai", 1, cb.failures.size)

        val next = Callback()
        p.load("full_test", next)
        assertEquals("load sau timeout được gửi, không bị từ chối isloading", 2, p.requests)
        assertEquals(emptyList<String>(), next.failures)
    }

    @Test
    fun `callback thanh cong toi muon - khong callback thu hai, quang cao giu lam ad tuoi`() = withTimers { timers ->
        val p = probe()
        val cb = Callback()
        p.load("full_test", cb)
        timers.fireAll()

        val ad = Any()
        p.lateSuccess(ad)

        assertEquals(emptyList<AdsEcmp?>(), cb.loaded)
        assertEquals(listOf("load timeout"), cb.failures)
        val next = Callback()
        p.load("full_test", next)
        assertEquals("quảng cáo muộn được dùng, không request mới", 1, p.requests)
        assertSame(ad, next.loaded.single()?.adObject)
    }

    @Test
    fun `callback fail toi muon - khong callback thu hai, khong request moi`() = withTimers { timers ->
        val p = probe()
        val cb = Callback()
        p.load("full_test", cb)
        timers.fireAll()

        p.lateFail()

        assertEquals(listOf("load timeout"), cb.failures)
        assertEquals("waterfall của lượt đã bỏ không đi tiếp sang id sau", 1, p.requests)
        assertFalse(p.state.isLoading)
    }

    @Test
    fun `load dang giu cho mang khoi tao - het han roi mang san sang thi khong tai, khong callback`() = withTimers { timers ->
        val queue = SdkReadyQueue()
        val p = probe(queue)
        val cb = Callback()
        p.load("full_test", cb)
        assertTrue(p.state.isLoading)
        timers.fireAll()
        assertEquals(listOf("load timeout"), cb.failures)

        queue.markReady()

        assertEquals("lượt giữ đã bỏ không gửi request", 0, p.requests)
        assertEquals(listOf("load timeout"), cb.failures)
        assertFalse(p.state.isLoading)
    }

    /**
     * 1.5.0 Task 15 (thay getter-null): hết hạn đặt `adsLoadCb` về `null` ngay lúc báo fail — callback của lượt đã bỏ
     * không còn ai giữ (refresh/callback muộn gọi `adsLoadCb?.…` không tới cb cũ); `load()` kế gán cb mới và cb mới
     * nhận kết quả.
     */
    @Test
    fun `het han dat adsLoadCb null - goi adsLoadCb khong toi cb cu, load ke gan cb moi va cb moi nhan`() = withTimers { timers ->
        val p = probe()
        val cb = Callback()
        p.load("full_test", cb)
        assertSame(cb, p.state.adsLoadCb)

        timers.fireAll()

        assertEquals(listOf("load timeout"), cb.failures)
        assertNull("hết hạn: adsLoadCb về null", p.state.adsLoadCb)
        p.state.adsLoadCb?.onLoaded("full_test", p.state.originPl, "id", null, p) // như nhánh refresh của listener
        assertEquals(emptyList<AdsEcmp?>(), cb.loaded)

        val next = Callback()
        p.load("full_test", next)
        assertSame("load kế gán cb mới", next, p.state.adsLoadCb)
        val ad = Any()
        p.lateSuccess(ad)
        assertSame(ad, next.loaded.single()?.adObject)
        assertEquals("cb cũ không nhận gì thêm", emptyList<AdsEcmp?>(), cb.loaded)
        assertEquals(listOf("load timeout"), cb.failures)
    }

    @Test
    fun `callback dung han huy watchdog`() = withTimers { timers ->
        val p = probe()
        val cb = Callback()
        p.load("full_test", cb)
        p.lateSuccess(Any())

        assertTrue(timers.pending.isEmpty())
        assertEquals(1, cb.loaded.size)
        assertEquals(emptyList<String>(), cb.failures)
    }

    private class AppCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) {
            events += "loaded"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "fail:$error"
        }
    }

    /**
     * Watchdog của placement (90 s) đã báo app `load timeout`: lượt đó không còn hiện hành (`loadCycle` đổi), nên
     * callback của lượt đó tới sau (slot hết hạn sau 90 s khi nhiều mạng cùng treo, hoặc mạng callback muộn) không tới
     * app lần hai — 9 vòng waterfall của `AdsHelper` chỉ gọi `cbLoad` khi lượt đã chụp còn là `loadCycle` hiện tại.
     * 1.5.0 Task 15: `cbLoad` không còn getter-null (trước đây đọc ra `null` tới lượt kế); lượt mới (`isLoading = true`)
     * là một `loadCycle` mới và dùng `cbLoad` như cũ.
     */
    @Test
    fun `placement het han - callback cua luot cu toi muon khong toi app`() = withTimers { timers ->
        val plcf = AdsPlacementConfig()
        val app = AppCb()
        plcf.isLoading = true
        val cycle = plcf.loadCycle // vòng waterfall chụp lượt lúc bắt đầu
        plcf.cbLoad = app

        timers.fireAll()

        assertEquals(listOf("fail:load timeout"), app.events)
        assertFalse(plcf.isLoading)
        assertNotEquals("watchdog bỏ lượt: loadCycle đổi", cycle, plcf.loadCycle)
        assertFalse("lượt đã báo timeout: vòng không được gọi app", plcf.isLoadCallbackCurrent(cycle))
        assertEquals(1, app.events.size)

        plcf.cbLoad = app // gán lại sau `load timeout` (showBanner / app gán thẳng): nhận kết quả muộn, như getter cũ
        assertTrue("cbLoad gán lại sau timeout", plcf.isLoadCallbackCurrent(cycle))

        val timedOut = plcf.loadCycle
        plcf.isLoading = true
        assertNotEquals("lượt mới là loadCycle mới", timedOut, plcf.loadCycle)
        assertTrue("lượt mới hiện hành", plcf.isLoadCallbackCurrent(plcf.loadCycle))
        assertFalse("lượt cũ không còn hiện hành sau lượt mới", plcf.isLoadCallbackCurrent(cycle))
        assertSame(app, plcf.cbLoad)
    }
}
