package com.app.plugin.brain

import com.app.plugin.SdkKillSwitch
import com.app.plugin.logevent.ServerEventCapture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 1.8.0 F-P1 (D31, R-16 P1): `PaywallManager.paywallFor` → `AdsBrain.shouldOfferIap` → `DecisionEngine.decide` →
 * `DecisionLogger.log` chạy trên main; sink mặc định ghi `DecisionLogQueue` (enqueue, trim, đọc) ngay trên luồng gọi =
 * StrictMode DiskRead/DiskWrite by-sdk trên main (G12 #1/#10/#11/#12). Mọi truy cập hàng đợi chuyển sang MỘT luồng
 * nền "appplugin-brain-log"; ghi đĩa rồi mới gửi, cùng trên luồng đó, theo thứ tự FIFO.
 */
class DecisionLogOffMainTest {
    @get:Rule val license = LicenseTestRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val realExecutor = DecisionLogger.ioExecutor

    @Before
    fun setUp() {
        PlayerIdSource.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        DecisionLogger.clearForwardersForTest()   // forwarder rò từ test khác không chen vào
        DecisionLogger.installDefaultSink()
    }

    @After
    fun tearDown() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        PlayerIdSource.resetForTest()
        DecisionLogger.ioExecutor = realExecutor
        DecisionLogger.detachQueueForTest()
        DecisionLogger.clearForwardersForTest()
        DecisionLogger.installDefaultSink()
    }

    private fun queueFile() = File(tmp.root, "brain_decision_queue.log")   // DecisionLogQueue.FILE_NAME

    /** Số bản ghi đang có trên đĩa; `0` khi file chưa có (chưa bản nào được ghi). */
    private fun diskRecords(): Int = queueFile().takeIf { it.isFile }?.readLines()?.count { it.isNotBlank() } ?: 0

    private fun decideOnce(): String {
        val id = "p_offmain_${System.nanoTime()}"
        val cands = listOf(ActionCandidate(ActionMode.NO_ACTION, "none"), ActionCandidate(ActionMode.INTERSTITIAL, "inter"))
        return DecisionEngine().decide(DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), PlayerState(id), cands)
            .decisionId
    }

    /** Chờ luồng nền chạy hết việc đã hẹn (một luồng FIFO: việc chặn này chạy sau mọi việc trước nó). */
    private fun drainIo() {
        val done = CountDownLatch(1)
        DecisionLogger.ioExecutor.execute { done.countDown() }
        assertTrue("luong nen cua decision log phai chay het viec", done.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `log tu luong main-sim - ghi hang doi tren appplugin-brain-log`() {
        // Luồng nền thật của DecisionLogger (seam `ioExecutor` giữ nguyên bản production).
        val q = spy(DecisionLogQueue(tmp.root))
        val queueThreads = CopyOnWriteArrayList<String>()
        val written = CountDownLatch(1)
        doAnswer { inv ->
            queueThreads += "enqueue@" + Thread.currentThread().name
            inv.callRealMethod().also { written.countDown() }
        }.`when`(q).enqueue(anyString())
        doAnswer { inv -> queueThreads += "peekAll@" + Thread.currentThread().name; inv.callRealMethod() }.`when`(q).peekAll()
        doAnswer { inv -> queueThreads += "removeAll@" + Thread.currentThread().name; inv.callRealMethod() }
            .`when`(q).removeAll(anyCollection())
        DecisionLogger.attachQueue(q)
        drainIo()
        queueThreads.clear()

        var decisionId = ""
        val main = Thread({ decisionId = decideOnce() }, "main-sim")
        main.start()
        main.join(5_000)

        assertTrue("ban ghi phai duoc ghi xuong dia", written.await(5, TimeUnit.SECONDS))
        drainIo()
        assertTrue("khong thao tac hang doi nao tren main-sim: $queueThreads", queueThreads.none { it.endsWith("@main-sim") })
        assertEquals("enqueue@appplugin-brain-log", queueThreads.first { it.startsWith("enqueue@") })
        assertEquals(decisionId, queueFile().readLines().single { it.isNotBlank() }.substringBefore('\u001F'))
    }

    @Test
    fun `thu tu FIFO va persisted-truoc-send giu nguyen`() {
        val io = HeldExecutor()
        DecisionLogger.ioExecutor = io
        val steps = CopyOnWriteArrayList<String>()
        val q = spy(DecisionLogQueue(tmp.root))
        doAnswer { inv ->
            steps += "enqueue:" + (inv.arguments[0] as String).substringBefore('\u001F')
            inv.callRealMethod()
        }.`when`(q).enqueue(anyString())
        DecisionLogger.attachQueue(q)
        io.runAll()
        ServerEventCapture().use {
            // logService có (capture) → `sendEventAt` tới kill switch ĐỒNG BỘ trên luồng gửi: điểm quan sát lần gửi sống.
            // Mỗi lần gửi ghi lại số bản ghi đang có trên đĩa: bản ghi của chính nó phải đã nằm đó.
            val ks = spy(SdkKillSwitch.NONE)
            doAnswer { steps += "send@disk=" + diskRecords(); true }
                .`when`(ks).killsLogevent(anyString())
            SdkKillSwitch.current = ks

            val ids = List(3) { decideOnce() }
            io.runAll()

            val expected = ids.mapIndexed { i, id -> listOf("enqueue:$id", "send@disk=${i + 1}") }.flatten()
            assertEquals("ghi dia roi moi gui, tung ban, theo thu tu log()", expected, steps)
            assertEquals("hang doi giu dung thu tu log()", ids,
                queueFile().readLines().filter { it.isNotBlank() }.map { it.substringBefore('\u001F') })
        }
    }
}
