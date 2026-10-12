package com.app.plugin.logevent

import android.content.SharedPreferences
import com.app.plugin.SdkKillSwitch
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.logevent.TelemetryConsentGate.Decision
import com.app.plugin.until.Constants
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * O3 (a), 1.6.0 Task 6: `TelemetryConsentGate` giữ event server (JSON đã dựng, `ts` lúc phát) khi chưa có tầng consent
 * dùng được, xả theo thứ tự khi tầng cho phép, bỏ (onResult false) khi `DENIED`, trần 50 bỏ cũ nhất, và đếm số event
 * process trước giữ mà không kịp gửi (`KEY_PENDING` → `session_start.pre_consent_lost`). Chưa `install` = PASS (hành vi
 * cũ, mọi test khác). Request bắt bằng `ServerEventCapture` (`HeldCall`: tối đa 4 request đang bay, còn lại chờ trong
 * hàng đợi của sender); prefs của `PreferenceHelper` là bản trong bộ nhớ ([store]).
 */
class TelemetryConsentGateTest {
    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: SharedPreferences? = null

    /** EEA chưa trả lời: `IABTCF_gdprApplies` vắng. */
    private val inGdprUnknown = contextWithTcf(gdpr = null, purposes = "")

    @Before fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        ConsentState.resetForTest()
        TelemetryConsentGate.resetForTest()
        previousPrefs = helper.preferences
        helper.preferences = inMemoryPrefs(store)
        LogEventServer::class.java.getDeclaredField("lastTimestamp").apply { isAccessible = true }.setLong(null, 0L)
    }

    @After fun tearDown() {
        TelemetryConsentGate.resetForTest()
        ConsentState.resetForTest()
        helper.preferences = previousPrefs
        SdkKillSwitch.current = SdkKillSwitch.NONE
        LogEventServer::class.java.getDeclaredField("lastTimestamp").apply { isAccessible = true }.setLong(null, 0L)
    }

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            assertTrue("hết 5 s mà chưa: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(5)
        }
    }

    private fun decision(
        i: Int,
        results: MutableList<Pair<Int, Boolean>>,
        onResult: (Boolean) -> Unit = { results += i to it }
    ) = LogEventServer.logBrainDecision(
        decisionId = "d_$i", decisionPoint = "interstitial", chosenActionId = "show",
        chosenActionMode = "explore", chosenActionProbability = 0.5, eligibleActions = "show,skip",
        guardrailsApplied = "", segment = "s", segmentConfidence = 1.0, scenario = "x",
        isHoldout = 0, policyVersion = "p1", onResult = onResult
    )

    /** `onResult` của app ném (lỗi app): ghi lần gọi vào [calls] rồi ném. */
    private fun throwingDecision(i: Int, calls: MutableList<Boolean>) =
        decision(i, mutableListOf()) { calls += it; throw IllegalStateException("app bug") }

    /** Phát [n] event `behavior` (`id_item` = b1…bn) lần lượt, mỗi cái chờ vào bộ đệm rồi mới phát cái kế. */
    private fun holdBehaviors(n: Int) = (1..n).forEach { i ->
        LogEventServer.logEvent("b$i", "s")
        awaitTrue("b$i vào bộ đệm") { TelemetryConsentGate.heldCount == i }
    }

    // ═══ decide: cùng luật ConsentRules.tierAllows ═══

    @Test fun `decide - GRANTED PARTIAL cho qua, DENIED bo ke ca ngoai GDPR, UNKNOWN giu tru khi gdprApplies = 0`() {
        for (gdpr in listOf(true, false, null)) {
            assertEquals("GRANTED gdpr=$gdpr", Decision.PASS, TelemetryConsentGate.decide(ConsentTier.GRANTED, gdpr))
            assertEquals("PARTIAL gdpr=$gdpr", Decision.PASS, TelemetryConsentGate.decide(ConsentTier.PARTIAL, gdpr))
            assertEquals("DENIED thắng mọi gdpr=$gdpr", Decision.DROP, TelemetryConsentGate.decide(ConsentTier.DENIED, gdpr))
        }
        assertEquals("UNKNOWN trong GDPR", Decision.HOLD, TelemetryConsentGate.decide(ConsentTier.UNKNOWN, true))
        assertEquals("UNKNOWN, gdprApplies vắng", Decision.HOLD, TelemetryConsentGate.decide(ConsentTier.UNKNOWN, null))
        assertEquals("UNKNOWN ngoài GDPR (=0)", Decision.PASS, TelemetryConsentGate.decide(ConsentTier.UNKNOWN, false))
    }

    // ═══ 1. Giữ tới khi có tầng, xả khi GRANTED ═══

    @Test fun `UNKNOWN trong GDPR - first_open, session_start khong toi server, GRANTED thi gui ca hai, ts luc phat`() {
        TelemetryConsentGate.install(inGdprUnknown)
        store[Constants.Common.TIME_OPEN] = 1
        ServerEventCapture().use { server ->
            LogEventServer.sessionStart()

            assertEquals("trước tầng: 0 session_start tới server", 0, server.awaitRequests(EventNames.SESSION_START, 0, 300).size)
            assertEquals("trước tầng: 0 first_open tới server", 0, server.awaitRequests(EventNames.FIRST_OPEN, 0, 0).size)
            awaitTrue("2 event trong bộ đệm") { TelemetryConsentGate.heldCount == 2 }
            val beforeFlush = System.currentTimeMillis()
            Thread.sleep(20)

            ConsentState.setTier(ConsentTier.GRANTED)

            val start = server.awaitRequests(EventNames.SESSION_START, 1).single()
            val first = server.awaitRequests(EventNames.FIRST_OPEN, 1).single()
            assertTrue("ts của session_start là lúc phát, không lúc xả", start.get("ts").asLong <= beforeFlush)
            assertTrue("ts của first_open là lúc phát, không lúc xả", first.get("ts").asLong <= beforeFlush)
            assertFalse("không có event mất trước consent", start.getAsJsonObject("eventData").has("pre_consent_lost"))
            assertEquals(0, TelemetryConsentGate.heldCount)
            assertEquals(2L, TelemetryConsentGate.flushedCount)
        }
    }

    @Test fun `PARTIAL cung xa bo dem`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            holdBehaviors(2)
            ConsentState.setTier(ConsentTier.PARTIAL)
            assertEquals(2, server.awaitRequests(EventNames.BEHAVIOR, 2).size)
        }
    }

    // ═══ 2. DENIED bỏ event đang giữ ═══

    @Test fun `UNKNOWN roi DENIED - 3 event giu bi bo, 0 request, onResult false moi cai dung mot lan`() {
        TelemetryConsentGate.install(inGdprUnknown)
        val results = CopyOnWriteArrayList<Pair<Int, Boolean>>()
        ServerEventCapture().use { server ->
            (1..3).forEach { decision(it, results) }
            awaitTrue("3 event trong bộ đệm") { TelemetryConsentGate.heldCount == 3 }
            assertTrue("chưa trả lời thì chưa có onResult", results.isEmpty())

            ConsentState.setTier(ConsentTier.DENIED)

            awaitTrue("3 onResult") { results.size == 3 }
            assertEquals(0, server.awaitRequests(EventNames.BRAIN_DECISION, 0).size)
            assertEquals(setOf(1 to false, 2 to false, 3 to false), results.toSet())
            assertEquals("mỗi event đúng một onResult", 3, results.size)
            assertEquals(3L, TelemetryConsentGate.droppedCount)
            assertEquals(0, TelemetryConsentGate.heldCount)
        }
    }

    // ═══ 2b. Sau DENIED không gửi gì (N1, quyết định Q2 1.6.4) ═══

    @Test fun `event phat sau DENIED khong gui, giu trong RAM (Q2)`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ConsentState.setTier(ConsentTier.DENIED)
        ServerEventCapture().use { server ->
            LogEventServer.logEvent("after", "s")
            assertEquals("Q2: 0 request sau DENIED", 0, server.awaitRequests(EventNames.BEHAVIOR, 0, 1_000).size)
            assertEquals("vòng DENIED không phải bộ đệm trước câu trả lời", 0, TelemetryConsentGate.heldCount)
        }
    }

    @Test fun `DENIED roi GRANTED trong process thi xa vong DENIED`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ConsentState.setTier(ConsentTier.DENIED)
        ServerEventCapture().use { server ->
            LogEventServer.logEvent("after", "s")
            assertEquals("DENIED: 0 request trước khi tầng đổi", 0, server.awaitRequests(EventNames.BEHAVIOR, 0, 1_000).size)

            ConsentState.setTier(ConsentTier.GRANTED)

            val sent = server.awaitRequests(EventNames.BEHAVIOR, 1)
            assertEquals("GRANTED cùng process: đúng 1 request", listOf("after"), sent.map { it.getAsJsonObject("eventData").get("id_item").asString })
            assertEquals(1L, TelemetryConsentGate.flushedCount)
        }
    }

    @Test fun `brain_decision sau DENIED bao DROPPED ngay, ca khi replay, 0 request`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ConsentState.setTier(ConsentTier.DENIED)
        val live = CopyOnWriteArrayList<Boolean>()
        val replay = CopyOnWriteArrayList<EventOutcome>()
        ServerEventCapture().use { server ->
            LogEventServer.logBrainDecisionForUser(mapOf("decision_id" to "live1"), null) { live += it }
            LogEventServer.replayBrainDecision(mapOf("decision_id" to "r1")) { replay += it }

            val deadline = System.currentTimeMillis() + 1_000
            while ((live.isEmpty() || replay.isEmpty()) && System.currentTimeMillis() < deadline) Thread.sleep(5)
            assertTrue("cả hai callback về trong 1 s (lô replay không treo): live=$live replay=$replay, " +
                "request=${server.allRequests().size}", live.isNotEmpty() && replay.isNotEmpty())
            assertEquals("sống: onResult(false)", listOf(false), live.toList())
            assertEquals("replay: DROPPED (bản đĩa giữ), không REJECTED", listOf(EventOutcome.DROPPED), replay.toList())
            assertEquals("0 request brain_decision", 0, server.awaitRequests(EventNames.BRAIN_DECISION, 0, 300).size)
            assertEquals("brain_decision không vào bộ đệm", 0, TelemetryConsentGate.heldCount)
        }
    }

    // ═══ 3. Ngoài GDPR ═══

    @Test fun `UNKNOWN ngoai GDPR (IABTCF_gdprApplies = 0) - gui ngay, khong giu`() {
        TelemetryConsentGate.install(contextWithTcf(gdpr = 0, purposes = ""))
        ServerEventCapture().use { server ->
            LogEventServer.logEvent("b1", "s")
            assertEquals(1, server.awaitRequests(EventNames.BEHAVIOR, 1).size)
            assertEquals(0, TelemetryConsentGate.heldCount)
        }
    }

    // ═══ 4. Trần ═══

    @Test fun `tran 50 - event thu 51 day event cu nhat ra, onResult false cua no, con 50 duoc xa`() {
        TelemetryConsentGate.install(inGdprUnknown)
        val results = CopyOnWriteArrayList<Pair<Int, Boolean>>()
        ServerEventCapture().use { server ->
            (1..51).forEach { i ->
                decision(i, results)
                awaitTrue("d_$i đã qua cổng") { TelemetryConsentGate.heldCount + TelemetryConsentGate.evictedCount == i.toLong() }
            }
            assertEquals(TelemetryConsentGate.HOLD_CAP, TelemetryConsentGate.heldCount)
            assertEquals(1L, TelemetryConsentGate.evictedCount)
            awaitTrue("onResult của cái bị bỏ") { results.isNotEmpty() }
            assertEquals("cái bị bỏ là cái đầu", listOf(1 to false), results.toList())

            ConsentState.setTier(ConsentTier.GRANTED)

            val sent = server.awaitRequests(EventNames.BRAIN_DECISION, 4)
            assertEquals("d_2 đi đầu", "d_2", sent.first().getAsJsonObject("eventData").get("decision_id").asString)
            awaitTrue("50 event đã xả") { TelemetryConsentGate.flushedCount == 50L }
            assertEquals("4 đang bay + 46 chờ trong hàng của sender", 46, LogEventServer.pendingEventsForTest())
        }
    }

    @Test fun `tran 50 - brain_decision bi day ra khong tinh vao KEY_PENDING, chi b1 (X2)`() {
        TelemetryConsentGate.install(inGdprUnknown)
        val results = CopyOnWriteArrayList<Pair<Int, Boolean>>()
        ServerEventCapture().use {
            (1..51).forEach { i ->
                decision(i, results)
                awaitTrue("d_$i đã qua cổng") { TelemetryConsentGate.heldCount + TelemetryConsentGate.evictedCount == i.toLong() }
            }
            assertEquals(1L, TelemetryConsentGate.evictedCount)

            LogEventServer.logEvent("b1", "s")

            awaitTrue("b1 vào, brain_decision cũ nhất bị đẩy") { TelemetryConsentGate.evictedCount == 2L }
            assertEquals(TelemetryConsentGate.HOLD_CAP, TelemetryConsentGate.heldCount)
            awaitTrue("prefs ghi sau b1") { store[TelemetryConsentGate.KEY_PENDING] != null }
            assertEquals("KEY_PENDING chỉ đếm b1: brain_decision (giữ hay bị đẩy) còn bản đĩa", 1, store[TelemetryConsentGate.KEY_PENDING])
        }
    }

    // ═══ 5. Thứ tự xả ═══

    @Test fun `xa theo thu tu phat - sequenceId tang dan`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            holdBehaviors(4)
            ConsentState.setTier(ConsentTier.GRANTED)

            val sent = server.awaitRequests(EventNames.BEHAVIOR, 4)
            assertEquals(listOf("b1", "b2", "b3", "b4"), sent.map { it.getAsJsonObject("eventData").get("id_item").asString })
            val ids = sent.map { it.get("sequenceId").asInt }
            assertEquals("sequenceId tăng dần: $ids", ids.sorted(), ids)
            assertEquals(4, ids.toSet().size)
        }
    }

    // ═══ 6. KEY_PENDING và pre_consent_lost ═══

    @Test fun `KEY_PENDING = so event dang giu, ve 0 sau khi xa`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            holdBehaviors(2)
            awaitTrue("prefs = 2") { store[TelemetryConsentGate.KEY_PENDING] == 2 }

            ConsentState.setTier(ConsentTier.GRANTED)

            assertEquals(2, server.awaitRequests(EventNames.BEHAVIOR, 2).size)
            awaitTrue("prefs = 0 sau xả") { store[TelemetryConsentGate.KEY_PENDING] == 0 }
        }
    }

    @Test fun `KEY_PENDING ve 0 sau khi DENIED bo`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use {
            holdBehaviors(2)
            awaitTrue("prefs = 2") { store[TelemetryConsentGate.KEY_PENDING] == 2 }
            ConsentState.setTier(ConsentTier.DENIED)
            awaitTrue("prefs = 0 sau bỏ") { store[TelemetryConsentGate.KEY_PENDING] == 0 }
        }
    }

    @Test fun `process truoc giu 3 event - session_start mang pre_consent_lost = 3, prefs ve 0`() {
        store[TelemetryConsentGate.KEY_PENDING] = 3
        store[Constants.Common.TIME_OPEN] = 2
        ConsentState.setTier(ConsentTier.GRANTED)
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            LogEventServer.sessionStart()

            val data = server.await(EventNames.SESSION_START, 1).single()
            assertEquals("session_start mang pre_consent_lost của process trước", 3, data.get("pre_consent_lost")?.asInt)
            awaitTrue("prefs = 0 sau khi session_start gửi") { store[TelemetryConsentGate.KEY_PENDING] == 0 }
        }
    }

    @Test fun `khong co event mat - session_start khong co khoa pre_consent_lost`() {
        store[Constants.Common.TIME_OPEN] = 2
        ConsentState.setTier(ConsentTier.GRANTED)
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            LogEventServer.sessionStart()
            assertFalse(server.await(EventNames.SESSION_START, 1).single().has("pre_consent_lost"))
        }
    }

    @Test fun `process truoc mat 2, process nay lai thoat khi giu session_start - prefs = 2 + 1, khong mat so dem`() {
        store[TelemetryConsentGate.KEY_PENDING] = 2
        store[Constants.Common.TIME_OPEN] = 2
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            LogEventServer.sessionStart()
            awaitTrue("session_start trong bộ đệm") { TelemetryConsentGate.heldCount == 1 }
            awaitTrue("prefs = 3 (2 của process trước chưa báo + 1 đang giữ)") { store[TelemetryConsentGate.KEY_PENDING] == 3 }

            ConsentState.setTier(ConsentTier.GRANTED)

            val data = server.await(EventNames.SESSION_START, 1).single()
            assertEquals("session_start mang 2 của process trước", 2, data.get("pre_consent_lost")?.asInt)
            awaitTrue("prefs = 0 sau khi gửi") { store[TelemetryConsentGate.KEY_PENDING] == 0 }
        }
    }

    // ═══ 7. Kill switch đứng trước gate ═══

    @Test fun `kill switch logevent va UNKNOWN - DROPPED ngay, khong vao bo dem`() {
        TelemetryConsentGate.install(inGdprUnknown)
        SdkKillSwitch.current = SdkKillSwitch(logevent = true)
        val results = CopyOnWriteArrayList<Pair<Int, Boolean>>()
        ServerEventCapture().use {
            decision(1, results)
            assertEquals("onResult(false) ngay", listOf(1 to false), results.toList())
            Thread.sleep(100)
            assertEquals(0, TelemetryConsentGate.heldCount)
        }
    }

    @Test fun `kill switch logevent toi luc dang giu - GRANTED xa, 0 request, onResult false moi cai dung mot lan (m4)`() {
        TelemetryConsentGate.install(inGdprUnknown)
        val results = CopyOnWriteArrayList<Pair<Int, Boolean>>()
        ServerEventCapture().use { server ->
            (1..3).forEach { i -> decision(i, results); awaitTrue("d_$i trong bộ đệm") { TelemetryConsentGate.heldCount == i } }
            SdkKillSwitch.current = SdkKillSwitch(logevent = true)

            ConsentState.setTier(ConsentTier.GRANTED)

            awaitTrue("3 event đã xả") { TelemetryConsentGate.flushedCount == 3L }
            Thread.sleep(300)
            assertEquals("kill switch tới lúc đang giữ: 0 POST", 0, server.allRequests().size)
            assertEquals("đúng một onResult(false) mỗi cái", listOf(1 to false, 2 to false, 3 to false), results.toList())
            assertEquals(0, LogEventServer.pendingEventsForTest())
        }
    }

    @Test fun `kill switch below_version toi luc dang giu - replay brain_decision nhan DROPPED, khong REJECTED`() {
        TelemetryConsentGate.install(inGdprUnknown)
        val outcomes = CopyOnWriteArrayList<EventOutcome>()
        ServerEventCapture().use { server ->
            LogEventServer.replayBrainDecision(mapOf("decision_id" to "r1")) { outcomes += it }
            awaitTrue("replay trong bộ đệm") { TelemetryConsentGate.heldCount == 1 }
            SdkKillSwitch.current = SdkKillSwitch(belowVersion = "99.0")

            ConsentState.setTier(ConsentTier.GRANTED)

            awaitTrue("đã xả") { TelemetryConsentGate.flushedCount == 1L }
            Thread.sleep(300)
            assertEquals("kill theo phiên bản lúc xả: 0 POST", 0, server.allRequests().size)
            assertEquals("DROPPED (tạm) để decision log không xếp bản ghi cuối như REJECTED", listOf(EventOutcome.DROPPED), outcomes.toList())
        }
    }

    /** Chạy [block] (+300 ms) với handler uncaught mặc định tạm: trả các throwable thoát khỏi luồng (Android: chết process). */
    private fun uncaughtDuring(block: () -> Unit): List<Throwable> {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        try { block(); Thread.sleep(300) } finally { Thread.setDefaultUncaughtExceptionHandler(previous) }
        return uncaught.toList()
    }

    @Test fun `kill switch luc xa, onResult nem - DROPPED dung mot lan, khong exception thoat ra`() {
        TelemetryConsentGate.install(inGdprUnknown)
        val calls = CopyOnWriteArrayList<Boolean>()
        val uncaught = uncaughtDuring {
            ServerEventCapture().use { server ->
                throwingDecision(1, calls)
                awaitTrue("d_1 trong bộ đệm") { TelemetryConsentGate.heldCount == 1 }
                SdkKillSwitch.current = SdkKillSwitch(logevent = true)

                ConsentState.setTier(ConsentTier.GRANTED)

                awaitTrue("đã xả") { TelemetryConsentGate.flushedCount == 1L }
                Thread.sleep(300)
                assertEquals(0, server.allRequests().size)
            }
        }
        assertEquals("onResult ném: vẫn đúng một lần", listOf(false), calls.toList())
        assertEquals("không exception thoát ra: ${uncaught.map { it.message }}", 0, uncaught.size)
    }

    @Test fun `kill switch bat luc dung body, tang GRANTED, onResult nem - DROPPED mot lan, khong thoat coroutine IO`() {
        val base = inMemoryPrefs(store)
        // Kill switch bật sau lần kiểm ở sendEvent, trước submit của gate: lúc đọc SEQUENCE_ID khi dựng body.
        helper.preferences = java.lang.reflect.Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, m, args ->
            if (m.name == "getInt" && args?.get(0) == "SEQUENCE_ID") SdkKillSwitch.current = SdkKillSwitch(logevent = true)
            m.invoke(base, *(args ?: emptyArray()))
        } as SharedPreferences
        TelemetryConsentGate.install(inGdprUnknown)
        ConsentState.setTier(ConsentTier.GRANTED)
        val calls = CopyOnWriteArrayList<Boolean>()
        val uncaught = uncaughtDuring {
            ServerEventCapture().use { server ->
                throwingDecision(1, calls)
                awaitTrue("onResult đã gọi") { calls.isNotEmpty() }
                Thread.sleep(300)
                assertEquals(0, server.allRequests().size)
            }
        }
        assertEquals("onResult ném: đúng một lần", listOf(false), calls.toList())
        assertEquals("không exception thoát coroutine IO: ${uncaught.map { it.message }}", 0, uncaught.size)
    }

    // ═══ 8. Chưa install = PASS (hành vi cũ) ═══

    @Test fun `chua install - UNKNOWN van gui ngay nhu cac ban truoc`() {
        ServerEventCapture().use { server ->
            LogEventServer.logEvent("b1", "s")
            assertEquals(1, server.awaitRequests(EventNames.BEHAVIOR, 1).size)
            assertEquals(0, TelemetryConsentGate.heldCount)
        }
    }
}
