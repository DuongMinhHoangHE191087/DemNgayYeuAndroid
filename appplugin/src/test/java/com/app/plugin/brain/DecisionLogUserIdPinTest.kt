package com.app.plugin.brain

import com.app.plugin.SdkKillSwitch
import com.app.plugin.logevent.HeldCall
import com.app.plugin.logevent.LogEventServerHelper
import com.app.plugin.logevent.LogEventTransport
import com.app.plugin.logevent.LogService
import com.app.plugin.logevent.ServerEventCapture
import com.google.gson.JsonObject
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import retrofit2.Call
import retrofit2.Response
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * O1 (b), plan 1.6.0 Task 2 (N3 của DOC/65 §5): event `brain_decision` sống mang `user_id` lúc ghi — cùng giá trị
 * với trường 13 của dòng decision log (và với replay, `DecisionLogReplayTest`) — không phải id hiện hành lúc dựng body
 * trên `Dispatchers.IO`. Hai nguồn id được làm lệch tất định thay vì đua thời gian: [PlayerIdSource.override] là nguồn
 * của dòng đĩa, [LogEventServerHelper.userIdSource] là nguồn của body lúc dựng.
 */
class DecisionLogUserIdPinTest {
    @get:Rule val license = LicenseTestRule()
    @get:Rule
    val tmp = TemporaryFolder()

    private val realExecutor = DecisionLogger.ioExecutor
    private val realUserIdSource = LogEventServerHelper.userIdSource
    private val io = HeldExecutor()
    private lateinit var capture: ServerEventCapture

    @Before fun setUp() {
        PlayerIdSource.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        capture = ServerEventCapture()
        DecisionLogger.ioExecutor = io
        DecisionLogger.clearForwardersForTest()   // forwarder rò từ test khác không chen vào
        DecisionLogger.installDefaultSink()
    }

    @After fun tearDown() {
        capture.close()
        PlayerIdSource.resetForTest()
        LogEventServerHelper.userIdSource = realUserIdSource
        DecisionLogger.ioExecutor = realExecutor
        DecisionLogger.detachQueueForTest()
        DecisionLogger.clearForwardersForTest()
    }

    private fun queueFile() = File(tmp.root, "brain_decision_queue.log")   // DecisionLogQueue.FILE_NAME

    private fun attach(q: DecisionLogQueue = DecisionLogQueue(tmp.root)) {
        DecisionLogger.attachQueue(q)
        io.runAll()
    }

    /** Một quyết định qua `DecisionEngine` → sink mặc định (ghi đĩa rồi gửi sống, trên luồng nền từ 1.8.0 F-P1). */
    private fun decideOnce(): String {
        val id = "p_pin_${System.nanoTime()}"
        val cands = listOf(ActionCandidate(ActionMode.NO_ACTION, "none"), ActionCandidate(ActionMode.INTERSTITIAL, "inter"))
        return DecisionEngine().decide(DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), PlayerState(id), cands)
            .decisionId.also { io.runAll() }
    }

    private fun diskFields(): List<String> = queueFile().readLines().single { it.isNotBlank() }.split("\u001F")

    private fun liveDecision(): JsonObject = capture.awaitRequests("brain_decision", 1).single()

    private fun userIdOf(body: JsonObject): String = body.getAsJsonObject("userProperty").get("user_id").asString

    @Test fun `event song mang user_id luc ghi, khong phai id hien hanh luc dung body`() {
        attach()
        PlayerIdSource.override = "pin-A"
        LogEventServerHelper.userIdSource = { "live-B" }

        val before = System.currentTimeMillis()
        val decisionId = decideOnce()

        val fields = diskFields()
        assertEquals("1.7.0 T6: 13 truong + ts_ms luc ghi (truong 14)", 14, fields.size)
        assertEquals(decisionId, fields[0])
        assertEquals("pin-A", fields[12])
        assertTrue("truong 14 = ts luc ghi", fields[13].toLong() in before..System.currentTimeMillis())
        val body = liveDecision()
        assertEquals("event song mang cung ts voi dong dia (truong 14)", fields[13].toLong(), body.get("ts").asLong)
        assertEquals(decisionId, body.getAsJsonObject("eventData").get("decision_id").asString)
        val ev = body.getAsJsonObject("eventData")
        assertEquals(listOf(fields[1], fields[2], fields[3], fields[11]),
            listOf("decision_point", "chosen_action_id", "chosen_action_mode", "policy_version").map { ev.get(it).asString })
        assertEquals("event song phai mang user_id da ghi vao dong decision log (truong 13)", "pin-A", userIdOf(body))
    }

    /**
     * `configure { playerId }` chạy giữa lúc dòng xuống đĩa và lúc body dựng trên IO: dựng lại bằng spy của hàng đợi
     * đổi id ngay sau khi ghi. Event sống vẫn mang id đã ghi, không đọc `PlayerIdSource` lần hai.
     */
    @Test fun `id doi ngay sau khi ghi dia thi event song van mang id da ghi`() {
        val q = spy(DecisionLogQueue(tmp.root))
        doAnswer { inv -> (inv.callRealMethod() as Boolean).also { PlayerIdSource.override = "pin-C" } }
            .`when`(q).enqueue(anyString())
        attach(q)
        PlayerIdSource.override = "pin-A"

        decideOnce()

        assertEquals("pin-A", diskFields()[12])
        assertEquals("pin-C", PlayerIdSource.current())
        assertEquals("event song lech id voi dong decision log", "pin-A", userIdOf(liveDecision()))
    }

    /** Giữ (xanh trước và sau): id không lưu được lúc ghi (trường 13 rỗng) thì event sống mang id hiện hành, như replay. */
    @Test fun `user_id khong luu duoc thi truong 13 rong va event song mang id hien hanh`() {
        attach()
        PlayerIdSource.override = "acct\n7\u001Fx"
        LogEventServerHelper.userIdSource = { "live-B" }

        decideOnce()

        val lines = queueFile().readLines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
        val fields = lines.single().split("\u001F")
        assertEquals(14, fields.size)
        assertEquals("", fields[12])
        assertEquals("truong 13 rong thi body dung id hien hanh", "live-B", userIdOf(liveDecision()))
    }

    /** Bản mới vẫn nối `onResult`: event sống được server nhận (ACCEPTED) thì dòng của nó rời đĩa. */
    @Test fun `event song duoc server nhan thi ban ghi roi dia`() {
        val calls = CopyOnWriteArrayList<HeldCall>()
        LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }.set(null, object : LogService {
            override fun logActionsToServer(requestBody: RequestBody): Call<ResponseBody> =
                HeldCall(Buffer().also { requestBody.writeTo(it) }.readUtf8()).also { calls += it }
        })
        attach()
        PlayerIdSource.override = "pin-A"

        decideOnce()

        assertEquals(1, queueFile().readLines().count { it.isNotBlank() })
        val deadline = System.currentTimeMillis() + 5_000
        while (calls.singleOrNull()?.isExecuted() != true) {
            assertTrue("het 5 s ma event song chua toi server", System.currentTimeMillis() < deadline)
            Thread.sleep(5)
        }
        calls.single().respond(Response.success("ok".toResponseBody()))
        io.runAll()   // xoá trên luồng nền của DecisionLogger
        assertEquals("ban ghi da toi server phai roi dia", 0, queueFile().readLines().count { it.isNotBlank() })
    }
}
