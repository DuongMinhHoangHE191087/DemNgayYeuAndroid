package com.app.plugin.logevent

import android.content.SharedPreferences
import com.app.plugin.SdkKillSwitch
import com.app.plugin.brain.HeldExecutor
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.PreferenceHelper
import com.google.gson.JsonParser
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
import retrofit2.Call
import retrofit2.Response
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 1.7.0 P-1 (R-TF3 §2): event đã nằm trong hàng đợi của sender (đang chờ backoff sau 5xx) phải bị kiểm lại lúc GỬI — kill
 * switch `logevent`, `logevent_control.enabled=false` và consent `DENIED` (N1: sau DENIED không byte nào rời máy) — không chỉ
 * lúc `submitNow`. Bị chặn: DROPPED + đếm, 0 POST; bản bền (DurableEvents) giữ trên đĩa và replay khi bật lại; DENIED xoá.
 * Sender thật, lượt thức dậy thật (`delay` trên `requestScope`, backoff 1–2 s); `logService` giả qua reflection.
 */
class SendTimeCheckTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: SharedPreferences? = null
    private val realIo = DurableEvents.ioExecutor
    private val logServiceField = LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }
    private val calls = CopyOnWriteArrayList<HeldCall>()
    private val io = HeldExecutor()

    private val heldService = object : LogService {
        override fun logActionsToServer(requestBody: RequestBody): Call<ResponseBody> =
            HeldCall(Buffer().also { requestBody.writeTo(it) }.readUtf8()).also { calls += it }
    }

    @Before fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        ConsentState.resetForTest()
        TelemetryConsentGate.resetForTest()
        DurableEvents.resetForTest()
        previousPrefs = helper.preferences
        helper.preferences = inMemoryPrefs(store)
        LogEventServer.resetSenderForTest()
        logServiceField.set(null, heldService)
        DurableEvents.ioExecutor = io
    }

    @After fun tearDown() {
        LogEventTestIsolation.cancelScheduledWork()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        DurableEvents.resetForTest()
        DurableEvents.ioExecutor = realIo
        TelemetryConsentGate.resetForTest()
        ConsentState.resetForTest()
        logServiceField.set(null, null)
        LogEventServer.resetSenderForTest()
        helper.preferences = previousPrefs
    }

    private fun durableFile() = File(File(tmp.root, "appplugin_events"), "brain_decision_queue.log")

    private fun diskTags(): List<String> = (durableFile().takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } ?: emptyList())
        .map { tagOf(it.substringAfter('\u001F')) }

    private fun tagOf(body: String): String =
        JsonParser.parseString(body).asJsonObject.getAsJsonObject("eventData").get("k").asString

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            assertTrue("het 5 s ma chua: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(5)
        }
    }

    /**
     * `ad_revenue` (bền) + `ad_request` (không bền) qua cổng lúc GRANTED, gửi, server trả 500 → cả hai về hàng đợi chờ
     * backoff (≤ 2 s). Trả lời xong thì [flip]; rồi chờ quá lượt thức dậy.
     */
    private fun queueTwoThen(flip: () -> Unit) {
        DurableEvents.attachDir(tmp.root, null)
        io.runAll()
        LogEventTransport.sendEvent(EventNames.AD_REVENUE, mapOf("k" to "rev"), null)
        LogEventTransport.sendEvent(EventNames.AD_REQUEST, mapOf("k" to "req"), null)
        awaitTrue("2 event qua cong") { calls.size + io.size == 2 }
        io.runAll()
        awaitTrue("2 event gui") { calls.size == 2 }
        calls.forEach { it.respond(Response.error(500, "err".toResponseBody())) }
        flip()
        Thread.sleep(3_500)   // backoff 1 s rồi 2 s: lượt thức dậy đã chạy
        io.runAll()
    }

    @Test fun `P1 kill switch giua hang doi thi 0 POST sau do, DROPPED va dem`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        queueTwoThen { SdkKillSwitch.current = SdkKillSwitch(logevent = true) }

        assertEquals("kill switch: 0 POST sau khi bat (chi 2 lan dau)", 2, calls.size)
        assertEquals("2 event bi bo va dem", 2L, LogEventServer.droppedEventsForTest())
        assertEquals("ban ben (tien) giu tren dia", listOf("rev"), diskTags())
        val stored = calls.first { tagOf(it.body) == "rev" }.body

        // Gỡ kill giữa phiên: bản bền replay đúng body đã lưu.
        SdkKillSwitch.applyRemote(null, "")
        io.runAll()
        awaitTrue("replay sau khi go kill") { calls.size >= 3 }
        assertEquals("replay dung body da luu", listOf(stored), calls.drop(2).map { it.body })
    }

    @Test fun `P2 enabled=false giua hang doi thi 0 POST, ban ben giu tren dia`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        queueTwoThen { LogEventControl.applyRemote(null, """{"version":1,"enabled":false}""") }

        assertEquals("enabled=false: 0 POST sau khi tat", 2, calls.size)
        assertEquals(2L, LogEventServer.droppedEventsForTest())
        assertEquals("ban ben giu tren dia", listOf("rev"), diskTags())
    }

    @Test fun `P3 bat lai enabled thi ban ben replay`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        queueTwoThen { LogEventControl.applyRemote(null, """{"version":1,"enabled":false}""") }
        val stored = calls.first { tagOf(it.body) == "rev" }.body

        LogEventControl.applyRemote(null, """{"version":1,"enabled":true}""")
        io.runAll()
        awaitTrue("replay sau khi bat lai") { calls.size >= 3 }

        assertEquals("replay dung body da luu (mot lan)", listOf(stored), calls.drop(2).map { it.body })
        assertEquals("ban khong ben khong quay lai", 1, calls.count { tagOf(it.body) == "req" })
    }

    @Test fun `P4 DENIED giua hang doi thi 0 POST (N1), ban ben bi xoa`() {
        TelemetryConsentGate.install(contextWithTcf(gdpr = null, purposes = ""))
        ConsentState.setTier(ConsentTier.GRANTED)
        queueTwoThen { ConsentState.setTier(ConsentTier.DENIED) }

        assertEquals("DENIED: 0 POST sau DENIED (event da xep hang truoc DENIED)", 2, calls.size)
        assertEquals(2L, LogEventServer.droppedEventsForTest())
        assertEquals("DENIED xoa ban ben", emptyList<String>(), diskTags())
    }
}
