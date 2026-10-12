package com.app.plugin.logevent

import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.until.LogUtil
import kotlinx.coroutines.sync.Mutex
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import retrofit2.Call
import retrofit2.Response
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Nối dây của Task 14a: `LogEventServer.logEventToServer` đi qua hàng đợi (tối đa 4 request đang bay),
 * body JSON được dựng trước rồi mới thành `RequestBody` lúc gửi. `logService` thay bằng bản giả qua
 * reflection như `LogEventServerKillSwitchTest`; event dựng trên `Dispatchers.IO` nên test chờ có hạn.
 */
class LogEventServerQueueTest {
    private val logServiceField = LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }
    private val endpointField = LogEventTransport::class.java.getDeclaredField("endpoint").apply { isAccessible = true }

    private class HeldService : LogService {
        val calls = CopyOnWriteArrayList<HeldCall>()
        override fun logActionsToServer(requestBody: RequestBody): Call<ResponseBody> {
            val json = Buffer().also { requestBody.writeTo(it) }.readUtf8()
            return HeldCall(json).also { calls += it }
        }
    }

    private val service = HeldService()

    @Before fun setUp() {
        LogEventTestIsolation.cancelScheduledWork()   // lưới phụ: việc hẹn còn sót của lớp test khác không được vào service giả này
        SdkKillSwitch.current = SdkKillSwitch.NONE
        LogEventServer.resetSenderForTest()
        logServiceField.set(null, service)
    }

    @After fun tearDown() {
        LogEventTestIsolation.cancelScheduledWork()
        logServiceField.set(null, null)   // lateinit trở về chưa init, như các test khác mong đợi
        endpointField.set(null, null)
        LogEventServer.resetSenderForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    private fun decision(i: Int, results: MutableList<Boolean>) = LogEventServer.logBrainDecision(
        decisionId = "d_$i", decisionPoint = "interstitial", chosenActionId = "show",
        chosenActionMode = "explore", chosenActionProbability = 0.5, eligibleActions = "show,skip",
        guardrailsApplied = "", segment = "s", segmentConfidence = 1.0, scenario = "x",
        isHoldout = 0, policyVersion = "p1", onResult = { results += it }
    )

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            assertTrue("het 5 s ma chua: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(5)
        }
    }

    @Test fun `6 event thi chi 4 request dang bay, 1 request tra loi thi gui tiep`() {
        val results = CopyOnWriteArrayList<Boolean>()
        (1..6).forEach { decision(it, results) }

        awaitTrue("4 request dang bay va 2 event cho") {
            service.calls.size == 4 && LogEventServer.pendingEventsForTest() == 2
        }
        assertEquals(4, service.calls.size)
        assertTrue("body la JSON cua event", service.calls.all { it.body.contains("\"eventName\":\"brain_decision\"") })

        service.calls[0].respond(Response.success("ok".toResponseBody()))

        assertEquals(listOf(true), results.toList())
        assertEquals("slot nha ra thi event cho duoc gui ngay", 5, service.calls.size)
        assertEquals(1, LogEventServer.pendingEventsForTest())
    }

    /**
     * Đợt sửa cuối Phase B, mục 8: chưa `init` thì event bị bỏ — trước đây chỉ `Logs.d` (câm ở bản release).
     * Giờ `logE` một lần mỗi phiên (không spam mỗi event), mọi event vẫn báo DROPPED.
     */
    @Test fun `chua init thi logE mot lan moi phien, moi event van bao DROPPED`() {
        endpointField.set(null, null)
        logServiceField.set(null, null)
        val outcomes = CopyOnWriteArrayList<EventOutcome>()
        mockStatic(Log::class.java).use { log ->
            repeat(3) { LogEventServer.replayBrainDecision(mapOf("decision_id" to "d_$it")) { o -> outcomes += o } }

            log.verify({ Log.e(eq(LogUtil.TAG), contains("LogEventServer not initialized")) }, times(1))
        }
        assertEquals(List(3) { EventOutcome.DROPPED }, outcomes.toList())
    }

    /** 1.6.3 Task 1b: init đã chạy với endpoint rỗng (Constants.DataBucket mặc định) = "tắt theo cấu hình": Log.i một lần, không logE, event vẫn DROPPED. */
    @Test fun `endpoint rong thi Log i telemetry off mot lan, khong logE not initialized, event DROPPED`() {
        logServiceField.set(null, null)
        endpointField.set(null, "")   // như init("") đã chạy, không dựng Retrofit/không flushQueue
        val outcomes = CopyOnWriteArrayList<EventOutcome>()
        mockStatic(Log::class.java).use { log ->
            repeat(2) { LogEventServer.replayBrainDecision(mapOf("decision_id" to "d_$it")) { o -> outcomes += o } }
            log.verify({ Log.i(eq(LogUtil.TAG), contains("telemetry off: DATABUCKETS_BASE_URL rỗng")) }, times(1))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("LogEventServer not initialized")) }, never())
        }
        assertEquals(List(2) { EventOutcome.DROPPED }, outcomes.toList())
    }

    /**
     * 1.6.3 Task 1b, review R-A I1: cờ "telemetry off" tách khỏi `notInitLogged` — event lọt trước `init` (kể cả khi
     * `logE` câm vì cờ log SDK tắt) không được nuốt dòng `Log.i` sau `init("")`; `resetSenderForTest` đặt lại cờ.
     */
    @Test fun `event truoc init khong nuot dong telemetry off sau init rong, reset thi log lai`() {
        logServiceField.set(null, null)
        endpointField.set(null, null)
        val outcomes = CopyOnWriteArrayList<EventOutcome>()
        mockStatic(Log::class.java).use { log ->
            LogEventServer.replayBrainDecision(mapOf("decision_id" to "d_0")) { o -> outcomes += o }
            endpointField.set(null, "")   // như init("") chạy sau event đầu
            LogEventServer.replayBrainDecision(mapOf("decision_id" to "d_1")) { o -> outcomes += o }
            log.verify({ Log.e(eq(LogUtil.TAG), contains("LogEventServer not initialized")) }, times(1))
            log.verify({ Log.i(eq(LogUtil.TAG), contains("telemetry off: DATABUCKETS_BASE_URL rỗng")) }, times(1))

            LogEventServer.resetSenderForTest()
            LogEventServer.replayBrainDecision(mapOf("decision_id" to "d_2")) { o -> outcomes += o }
            log.verify({ Log.i(eq(LogUtil.TAG), contains("telemetry off: DATABUCKETS_BASE_URL rỗng")) }, times(2))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("LogEventServer not initialized")) }, times(1))
        }
        assertEquals(List(3) { EventOutcome.DROPPED }, outcomes.toList())
    }

    /**
     * Đợt sửa cuối Phase B, mục 4: event đang dựng (chờ `sequenceIdMutex`) khi `resetSenderForTest` chạy
     * thuộc thế hệ cũ: dựng xong thì bị bỏ (DROPPED), không rò vào sender mới của test sau.
     */
    @Test fun `event dung xong sau resetSenderForTest thi bi bo, khong ro sang sender moi`() {
        val mutex = LogEventTransport::class.java.getDeclaredField("sequenceIdMutex")
            .apply { isAccessible = true }.get(null) as Mutex
        check(mutex.tryLock()) { "sequenceIdMutex dang bi giu" }
        val outcomes = CopyOnWriteArrayList<EventOutcome>()
        try {
            LogEventServer.replayBrainDecision(mapOf("decision_id" to "d_gen")) { outcomes += it }
            LogEventServer.resetSenderForTest()   // test trước kết thúc khi event còn đang dựng
        } finally {
            mutex.unlock()
        }

        awaitTrue("event the he cu co ket qua") { outcomes.isNotEmpty() }
        awaitTrue("requestScope yen: khong con event dang dung/hen") { LogEventTestIsolation.scheduledWork() == 0 }
        assertEquals(listOf(EventOutcome.DROPPED), outcomes.toList())
        assertEquals("khong request nao", 0, service.calls.size)
        assertEquals("sender moi khong nhan event cu", 0, LogEventServer.pendingEventsForTest())
    }

    /**
     * Task 17 (review đợt sửa cuối Phase B, Minor 1): không dựng được body (ở đây Gson từ chối `NaN`) lặp lại
     * mãi với cùng bản ghi. Báo [EventOutcome.REJECTED] (như KDoc của enum) để decision log xếp bản đó cuối
     * các lượt sau; báo DROPPED thì bản đó luôn là "mới" và cũ nhất, bị chọn đầu mọi lượt và chặn replay.
     * REJECTED không bao giờ xoá bản ghi khỏi đĩa.
     */
    @Test fun `khong dung duoc body thi bao REJECTED de decision log xep cuoi`() {
        val outcomes = CopyOnWriteArrayList<EventOutcome>()

        LogEventServer.replayBrainDecision(mapOf("chosen_action_probability" to Double.NaN)) { outcomes += it }

        awaitTrue("co ket qua") { outcomes.isNotEmpty() }
        awaitTrue("requestScope yen: khong con event dang dung/hen") { LogEventTestIsolation.scheduledWork() == 0 }
        assertEquals(listOf(EventOutcome.REJECTED), outcomes.toList())
        assertEquals("khong request nao", 0, service.calls.size)
    }

    /**
     * 1.6.4 Task 3b (T1b-m1): `endpoint` do main ghi ở `init`, luồng IO của replay đọc trong `sendEvent` — phải `@Volatile`
     * và đọc đúng một lần mỗi event (hợp đồng nguồn; JMM không quan sát được bằng test hành vi).
     */
    @Test fun `endpoint volatile va sendEvent doc mot lan`() {
        val src = java.io.File("src/main/java/com/app/plugin/logevent/LogEventTransport.kt").readText()
        val lines = src.lines()
        val decl = lines.indexOfFirst { it.trim() == "private var endpoint: String? = null" }
        assertTrue("endpoint phải là `@Volatile private var endpoint: String? = null` (decl=$decl)",
            decl > 0 && lines[decl - 1].trim() == "@Volatile")
        val start = src.indexOf("internal fun sendEvent(")
        val end = src.indexOf("\n    }\n", start)
        assertTrue("không thấy thân sendEvent", start >= 0 && end > start)
        val reads = Regex("\\bendpoint\\b").findAll(src.substring(start, end)).count()
        assertEquals("sendEvent phải đọc endpoint đúng 1 lần (ep = endpoint)", 1, reads)
    }
}
