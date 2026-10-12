package com.app.plugin.logevent

import android.content.SharedPreferences
import com.app.plugin.SdkKillSwitch
import com.app.plugin.brain.DecisionLogQueue
import com.app.plugin.brain.HeldExecutor
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.PreferenceHelper
import com.google.gson.JsonParser
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 1.7.0 Task 6: `DurableEvents` — tập bền (tiền + phễu) ghi đĩa khi cổng consent cho gửi, replay body gốc ở process sau,
 * xoá khi 2xx hoặc 400/413 (R-9; 401 tạm thời từ 2.0.0 B1), xoá sạch khi `DENIED`; sign-off R1 (a) không I/O dưới monitor cổng, (b) replay chỉ khi
 * tầng qua cổng, (c) mỗi lượt ≤ chỗ trống hàng đợi. `logService` giả qua reflection (như `DecisionLogReplayTest`); luồng
 * đĩa là [HeldExecutor] chạy trên luồng test, trừ D1 (luồng thật để chặn bằng latch).
 */
class DurableEventsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: SharedPreferences? = null
    private val realIo = DurableEvents.ioExecutor
    private val logServiceField = LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }
    private val calls = CopyOnWriteArrayList<HeldCall>()
    private val io = HeldExecutor()
    private val inGdprUnknown = contextWithTcf(gdpr = null, purposes = "")

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
        LogEventTestIsolation.cancelScheduledWork()   // backoff của test này không được bơm vào service giả của test sau
        DurableEvents.resetForTest()
        DurableEvents.ioExecutor = realIo
        TelemetryConsentGate.resetForTest()
        ConsentState.resetForTest()
        logServiceField.set(null, null)
        LogEventServer.resetSenderForTest()
        helper.preferences = previousPrefs
    }

    private fun durableFile() = File(File(tmp.root, "appplugin_events"), "brain_decision_queue.log")   // DecisionLogQueue.FILE_NAME

    private fun diskLines(): List<String> = durableFile().takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } ?: emptyList()

    private fun writeRecords(vararg records: String) {
        durableFile().parentFile!!.mkdirs()
        durableFile().writeText(records.joinToString("") { it + "\n" })
    }

    /** Dòng đĩa như bản ghi của process trước: tên + U+001F + body đã dựng. */
    private fun record(name: String, body: String) = "$name\u001F$body"

    private fun storedBody(i: Int) =
        """{"sequenceId":$i,"eventName":"ad_revenue","eventData":{"value_micros":1234567,"k":"r$i"},"ts":179070300000$i,""" +
            """"userProperty":{"user_id":"u"},"event_id":"e-$i","schema_version":2,"consent_tier":"GRANTED"}"""

    private fun send(name: String, tag: String) = LogEventTransport.sendEvent(name, mapOf("k" to tag), null)

    private fun tagOf(body: String): String =
        JsonParser.parseString(body).asJsonObject.getAsJsonObject("eventData").get("k").asString

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            assertTrue("het 5 s ma chua: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(5)
        }
    }

    private fun ok(): Response<ResponseBody> = Response.success("ok".toResponseBody())
    private fun http(code: Int): Response<ResponseBody> = Response.error(code, "err".toResponseBody())

    /** Trả lời mỗi call chưa trả lời theo `eventData.k` của nó (một lần mỗi call). */
    private fun answerBy(answered: MutableSet<HeldCall>, respond: (HeldCall, String) -> Unit) {
        for (c in calls) if (answered.add(c)) respond(c, tagOf(c.body))
    }

    @Test fun `D1 ad_revenue da qua cong duoc luu dia truoc khi gui`() {
        val thread = Executors.newSingleThreadExecutor()
        DurableEvents.ioExecutor = thread
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val q = spy(DecisionLogQueue(File(tmp.root, "appplugin_events"), DurableEvents.MAX_RECORDS))
        doAnswer { inv -> entered.countDown(); release.await(5, TimeUnit.SECONDS); inv.callRealMethod() }
            .`when`(q).enqueue(anyString())
        ConsentState.setTier(ConsentTier.GRANTED)
        TelemetryConsentGate.install(inGdprUnknown)
        DurableEvents.attach(q, inGdprUnknown)

        send(EventNames.AD_REVENUE, "rev")

        assertTrue("ad_revenue qua cong phai duoc ghi dia (store chua chay)", entered.await(2, TimeUnit.SECONDS))
        // R1 (a): đang ghi đĩa (bị latch chặn) mà monitor của cổng vẫn rảnh — main lấy được ngay.
        val main = Executors.newSingleThreadExecutor()
        try {
            main.submit<Int> { TelemetryConsentGate.consumePendingLostForSessionStart() }.get(1, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            throw AssertionError("consumePendingLostForSessionStart bi chan boi store cham (I/O duoi monitor cong)")
        } finally {
            main.shutdownNow()
        }
        assertEquals("chua gui truoc khi luu dia xong", 0, calls.size)

        release.countDown()
        awaitTrue("gui sau khi luu") { calls.size == 1 }
        assertEquals("dong dia = ten + body da gui", listOf(record("ad_revenue", calls[0].body)), diskLines())
        thread.shutdownNow()
    }

    @Test fun `D2 process moi replay dung body goc (ts, sequenceId, event_id giong het)`() {
        writeRecords(record("ad_revenue", storedBody(1)))
        ConsentState.setTier(ConsentTier.GRANTED)
        logServiceField.set(null, null)
        DurableEvents.attachDir(tmp.root, null)
        io.runAll()

        // init dựng service thật; thay bằng service giả trước khi luồng đĩa (HeldExecutor) chạy lượt replay.
        LogEventServer.init("http://127.0.0.1:9/", "test-key")
        logServiceField.set(null, heldService)
        io.runAll()
        awaitTrue("1 ban replay toi server") { calls.size == 1 }

        assertEquals("body replay phai giong het body da luu", storedBody(1), calls[0].body)
        val sent = JsonParser.parseString(calls[0].body).asJsonObject
        assertEquals(listOf("1", "1790703000001", "e-1"), listOf("sequenceId", "ts", "event_id").map { sent.get(it).asString })
        calls[0].respond(ok())
        io.runAll()
        assertEquals("2xx thi roi dia", emptyList<String>(), diskLines())
    }

    @Test fun `D3 2xx xoa, 4xx vinh vien xoa, loi mang giu`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        DurableEvents.attachDir(tmp.root, null)
        io.runAll()   // lượt replay lúc gắn (transport đã init): file rỗng, không gửi gì
        val answered = mutableSetOf<HeldCall>()

        listOf("ok", "400", "401", "413").forEach { send(EventNames.AD_REVENUE, it) }
        awaitTrue("4 event qua cong") { calls.size + io.size == 4 }
        io.runAll()
        awaitTrue("4 event gui") { calls.size == 4 }
        assertEquals("luu truoc khi co ket qua", 4, diskLines().size)
        answerBy(answered) { c, tag -> c.respond(if (tag == "ok") ok() else http(tag.toInt())) }
        io.runAll()
        assertEquals("2xx va 400/413 roi dia; 401 tam thoi o lai (2.0.0 B1)", listOf("401"), diskLines().map { tagOf(it.substringAfter('\u001F')) })
        assertEquals("tu choi vinh vien duoc dem", 2L, DurableEvents.permanentRejectedCount.get())

        // Phán quyết R-9: 429, 5xx thử lại; 4xx khác và lỗi mạng cũng không xoá bản đĩa.
        listOf("403", "429", "500", "offline").forEach { send(EventNames.AD_REVENUE, it) }
        awaitTrue("4 event nua qua cong") { calls.size - 4 + io.size == 4 }
        io.runAll()
        awaitTrue("4 event nua gui") { calls.size == 8 }
        answerBy(answered) { c, tag -> if (tag == "offline") c.fail() else c.respond(http(tag.toInt())) }
        io.runAll()

        // DROPPED (lý do tạm thời: kill switch bật giữa lúc qua cổng và lúc ghi/gửi) cũng giữ bản đĩa.
        send(EventNames.AD_REVENUE, "dropped")
        awaitTrue("event qua cong, cho luong dia") { io.size == 1 }
        SdkKillSwitch.current = SdkKillSwitch(logevent = true)
        io.runAll()
        SdkKillSwitch.current = SdkKillSwitch.NONE

        assertEquals(
            "401/403/429/500/mang/DROPPED: ban dia o lai",
            listOf("401", "403", "429", "500", "dropped", "offline"),
            diskLines().map { tagOf(it.substringAfter('\u001F')) }.sorted()
        )
        assertEquals(2L, DurableEvents.permanentRejectedCount.get())
    }

    @Test fun `D4 event giu truoc consent khong cham dia, DENIED xoa file`() {
        writeRecords(record("ad_revenue", storedBody(7)))
        TelemetryConsentGate.install(inGdprUnknown)
        DurableEvents.attachDir(tmp.root, inGdprUnknown)

        send(EventNames.AD_REVENUE, "held")
        awaitTrue("ad_revenue vao bo dem cua cong") { TelemetryConsentGate.heldCount == 1 }
        io.runAll()
        assertEquals("event dang giu khong cham dia", listOf(record("ad_revenue", storedBody(7))), diskLines())

        ConsentState.setTier(ConsentTier.DENIED)
        awaitTrue("cong bo event giu") { TelemetryConsentGate.heldCount == 0 }
        io.runAll()
        assertEquals("DENIED xoa file ben", emptyList<String>(), diskLines())

        send(EventNames.AD_REVENUE, "after-denied")
        Thread.sleep(200)
        io.runAll()
        assertEquals("sau DENIED: khong ghi dia", emptyList<String>(), diskLines())
        assertEquals("sau DENIED: khong gui (Q2)", 0, calls.size)

        // Q2: vòng RAM xả khi GRANTED trong cùng process — gửi, nhưng không bao giờ xuống đĩa.
        ConsentState.setTier(ConsentTier.GRANTED)
        awaitTrue("event cua vong duoc gui") { io.runAll(); calls.size == 1 }
        assertEquals("after-denied", tagOf(calls[0].body))
        assertEquals("event phat sau DENIED khong xuong dia ca khi GRANTED sau do", emptyList<String>(), diskLines())
    }

    @Test fun `D5 ad_request (khong ben) khong luu`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        DurableEvents.attachDir(tmp.root, null)
        io.runAll()   // lượt replay lúc gắn: file rỗng

        send(EventNames.AD_REQUEST, "req")
        send(EventNames.AD_REVENUE, "rev")
        awaitTrue("2 event qua cong") { calls.size + io.size == 2 }
        io.runAll()
        awaitTrue("2 event gui") { calls.size == 2 }

        assertEquals("chi ad_revenue (tap ben) nam tren dia", listOf("ad_revenue"), diskLines().map { it.substringBefore('\u001F') })
    }

    @Test fun `D6 tran 2000 bo cu nhat`() {
        assertEquals(2000, DurableEvents.MAX_RECORDS)
        writeRecords(*Array(2000) { record("ad_revenue", """{"n":${it + 1}}""") })
        ConsentState.setTier(ConsentTier.GRANTED)
        // Gắn khi transport chưa init: không có lượt replay lúc gắn, test chỉ đo trần của lần ghi.
        logServiceField.set(null, null)
        DurableEvents.attachDir(tmp.root, null)
        logServiceField.set(null, heldService)
        assertEquals(0, io.size)

        send(EventNames.AD_REVENUE, "new")
        awaitTrue("event qua cong") { calls.size + io.size == 1 }
        io.runAll()

        val lines = diskLines()
        assertTrue("ban moi o cuoi: ${lines.last()}", lines.last().contains("\"k\":\"new\""))
        assertEquals("tran 2000", 2000, lines.size)
        assertFalse("ban cu nhat bi bo", lines.first().endsWith("""{"n":1}"""))
    }

    @Test fun `D7 replay khi tier UNKNOWN khong cham held-KEY_PENDING, sau GRANTED replay du`() {
        writeRecords(*Array(3) { record("ad_revenue", storedBody(it + 1)) })
        TelemetryConsentGate.install(inGdprUnknown)
        DurableEvents.attachDir(tmp.root, inGdprUnknown)
        DurableEvents.onTransportReady()
        io.runAll()
        Thread.sleep(100)
        assertEquals("UNKNOWN: khong replay", 0, calls.size)
        assertEquals("UNKNOWN: khong day vao held", 0, TelemetryConsentGate.heldCount)
        assertNull("UNKNOWN: KEY_PENDING khong doi", store[TelemetryConsentGate.KEY_PENDING])

        // R1 (c): hàng đợi event còn 2 chỗ (trần 2, bay 1) → lượt đầu 2 bản, bản 3 ở lại đĩa.
        LogEventTransport::class.java.getDeclaredField("sender").apply { isAccessible = true }.set(null, LogEventSender(
            queue = LogEventQueue(maxPending = 2, maxInFlight = 1),
            transport = { json -> heldService.logActionsToServer(json.toRequestBody()) },
            schedule = { _, _ -> }
        ))
        ConsentState.setTier(ConsentTier.GRANTED)
        io.runAll()
        assertEquals("sau GRANTED: replay luot dau = cho trong hang", 2L, DurableEvents.replayedCount.get())
        assertEquals("ban 3 o lai dia", 3, diskLines().size)

        awaitTrue("ban 1 dang bay") { calls.size == 1 }
        calls[0].respond(ok())
        awaitTrue("ban 2 dang bay") { calls.size == 2 }
        calls[1].respond(ok())
        io.runAll()
        awaitTrue("luot 2: ban 3") { calls.size == 3 }
        calls[2].respond(ok())
        io.runAll()

        assertEquals("sau GRANTED replay du 3 ban, dung body", (1..3).map(::storedBody), calls.map { it.body })
        assertEquals("roi dia het", emptyList<String>(), diskLines())
        assertEquals(0, TelemetryConsentGate.heldCount)
    }
}
