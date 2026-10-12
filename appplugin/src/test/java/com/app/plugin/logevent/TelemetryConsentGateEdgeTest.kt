package com.app.plugin.logevent

import android.content.Context
import android.content.SharedPreferences
import com.app.plugin.SdkKillSwitch
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.Constants
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Review Task 6 (1.6.0): race giữa lần đọc tầng và khoá (I1, R1/R8), trần không đẩy `first_open`/`session_start` và số
 * `pre_consent_lost` không mất (I2, R2/R3/R5/R7), thứ tự bộ đệm trước event tới (R4), xả không chạy trên luồng gọi
 * `setTier` (R6). Cùng khung với `TelemetryConsentGateTest`.
 */
class TelemetryConsentGateEdgeTest {
    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: SharedPreferences? = null
    private val inGdprUnknown = contextWithTcf(gdpr = null, purposes = "")

    @Before fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        ConsentState.resetForTest()
        TelemetryConsentGate.resetForTest()
        previousPrefs = helper.preferences
        helper.preferences = inMemoryPrefs(store)
        resetSessionClock()
    }

    @After fun tearDown() {
        TelemetryConsentGate.resetForTest()
        ConsentState.resetForTest()
        helper.preferences = previousPrefs
        SdkKillSwitch.current = SdkKillSwitch.NONE
        resetSessionClock()
    }

    private fun resetSessionClock() =
        LogEventServer::class.java.getDeclaredField("lastTimestamp").apply { isAccessible = true }.setLong(null, 0L)

    private fun awaitTrue(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            // Thông báo (kể cả gateState(): dump luồng = safepoint toàn JVM) chỉ dựng khi hết hạn, không mỗi lượt thăm.
            if (System.currentTimeMillis() >= deadline) org.junit.Assert.fail("hết ${timeoutMs / 1000} s mà chưa: $what [${gateState()}]")
            Thread.sleep(5)
        }
    }

    private fun gateState() = "held=${TelemetryConsentGate.heldCount} flushed=${TelemetryConsentGate.flushedCount} " +
        "evicted=${TelemetryConsentGate.evictedCount} denied=${deniedRingSize()} pending=${pending()} " +
        "blockedOnGate=${someThreadBlockedOnGate()} gateIoIdle=${gateIoIdle()}"

    /**
     * `reevaluate()` chạy trên `ioScope` của cổng và là việc DUY NHẤT nó giao đó; `launch` đăng ký job con ngay trong lời gọi
     * `setTier`, nên "không còn job con" = mọi lượt đánh giá lại đã xong (thay cho sleep chờ nó chạy).
     */
    private fun gateIoIdle(): Boolean {
        val scope = TelemetryConsentGate::class.java.getDeclaredField("ioScope").apply { isAccessible = true }.get(null) as kotlinx.coroutines.CoroutineScope
        return scope.coroutineContext[kotlinx.coroutines.Job]!!.children.none()
    }

    /** Có luồng nào đang BLOCKED trong `TelemetryConsentGate` (chờ khoá của cổng): dấu hiệu quan sát được rằng event đã TỚI cổng. */
    private fun someThreadBlockedOnGate() = Thread.getAllStackTraces().any { (t, frames) ->
        t.state == Thread.State.BLOCKED && frames.any { it.className.startsWith("com.app.plugin.logevent.TelemetryConsentGate") }
    }

    /** Qua rào khoá cổng: khối `evaluate` đang chạy (kể cả `writePendingLocked` ở cuối) đã xong khi hàm này về. */
    private fun gateBarrier(timeoutMs: Long = 30_000) {
        val passed = CountDownLatch(1)
        Thread { synchronized(TelemetryConsentGate) { }; passed.countDown() }.apply { isDaemon = true; name = "gate-barrier"; start() }
        if (!passed.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            // Không gọi gateState(): nó đọc dưới chính khoá này và sẽ treo theo. Chỉ liệt kê luồng đang ở trong cổng.
            org.junit.Assert.fail("khoá cổng không nhả sau ${timeoutMs / 1000} s; luồng trong cổng: ${threadsInGate()}")
        }
    }

    private fun threadsInGate() = Thread.getAllStackTraces().entries
        .filter { e -> e.value.any { fr -> fr.className.startsWith("com.app.plugin.logevent.TelemetryConsentGate") } }
        .joinToString("; ") { e -> "${e.key.name} ${e.key.state} " + e.value.take(4).joinToString(" < ") { fr -> fr.className.substringAfterLast('.') + "." + fr.methodName } }

    /** Số event trong vòng `DENIED` (RAM) của cổng, đọc dưới khoá cổng: chỉ để test biết event đã TỚI cổng. */
    private fun deniedRingSize(): Int {
        val ring = TelemetryConsentGate::class.java.getDeclaredField("deniedHeld").apply { isAccessible = true }.get(null) as Collection<*>
        return synchronized(TelemetryConsentGate) { ring.size }
    }

    private val passedGate get() = TelemetryConsentGate.heldCount + TelemetryConsentGate.evictedCount

    /** Phát `behavior` b[from]…b[to] lần lượt, mỗi cái chờ qua cổng (giữ hoặc đẩy) rồi mới phát cái kế. */
    private fun emitBehaviors(from: Int, to: Int) = (from..to).forEach { i ->
        val before = passedGate
        LogEventServer.logEvent("b$i", "s")
        awaitTrue("b$i qua cổng") { passedGate == before + 1 }
    }

    private fun pending(): Int = store[TelemetryConsentGate.KEY_PENDING] as? Int ?: 0

    private fun itemOf(o: com.google.gson.JsonObject) = o.getAsJsonObject("eventData").get("id_item")?.asString

    /**
     * Context mà lần đọc `IABTCF_gdprApplies` thứ [blockAt] trên luồng IO (1 = lần đầu) báo [entered] rồi chặn tới khi test
     * mở [release]; khoá vắng (EEA chưa trả lời).
     */
    private fun contextBlockingOnRead(blockAt: Int, entered: CountDownLatch, release: CountDownLatch): Context {
        val reads = java.util.concurrent.atomic.AtomicInteger(0)
        val tcf = mock(SharedPreferences::class.java) { inv ->
            when (inv.method.name) {
                "contains" -> {
                    if (Thread.currentThread().name.contains("DefaultDispatcher") && reads.incrementAndGet() == blockAt) {
                        entered.countDown()
                        release.await(5, TimeUnit.SECONDS)
                    }
                    false
                }
                "getString", "getInt" -> inv.getArgument<Any?>(1)
                else -> null
            }
        }
        val context = mock(Context::class.java)
        `when`(context.packageName).thenReturn("com.example.app")
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(tcf)
        return context
    }

    @Test fun `R1 event doc tang UNKNOWN truoc khi GRANTED xa xong van duoc gui`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        TelemetryConsentGate.install(contextBlockingOnRead(1, entered, release))
        ServerEventCapture().use { server ->
            LogEventServer.logEvent("b1", "s")
            assertTrue("b1 đã tới lần đọc IABTCF", entered.await(5, TimeUnit.SECONDS))
            ConsentState.setTier(ConsentTier.GRANTED)
            awaitTrue("lượt xả của GRANTED chạy xong khi bộ đệm còn rỗng") { gateIoIdle() }
            release.countDown()    // b1 đi tiếp với lần đọc cũ (UNKNOWN)
            val sent = server.awaitRequests(EventNames.BEHAVIOR, 1, 500)
            assertEquals("b1 tới server sau GRANTED (held=${TelemetryConsentGate.heldCount})", 1, sent.size)
        }
    }

    @Test fun `R2 session_start mang 5 cua process truoc khong bi day ra khi tran tran, toi server voi pre_consent_lost 5`() {
        store[TelemetryConsentGate.KEY_PENDING] = 5
        store[Constants.Common.TIME_OPEN] = 2
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            LogEventServer.sessionStart()
            awaitTrue("session_start trong bộ đệm") { TelemetryConsentGate.heldCount == 1 }
            emitBehaviors(1, 50)
            assertEquals(1L, TelemetryConsentGate.evictedCount)

            ConsentState.setTier(ConsentTier.GRANTED)

            val starts = server.await(EventNames.SESSION_START, 1)
            assertEquals("session_start tới server (prefs=${pending()})", 1, starts.size)
            assertEquals(5, starts.single().get("pre_consent_lost")?.asInt)
        }
    }

    @Test fun `R2b xa luc session_start mang 5 chua toi cong - KEY_PENDING giu 5 toi khi chinh session_start duoc gui`() {
        store[TelemetryConsentGate.KEY_PENDING] = 5
        store[Constants.Common.TIME_OPEN] = 2
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        TelemetryConsentGate.install(contextBlockingOnRead(2, entered, release))
        ServerEventCapture().use { server ->
            LogEventServer.logEvent("b1", "s")
            awaitTrue("b1 trong bộ đệm") { TelemetryConsentGate.heldCount == 1 }
            LogEventServer.sessionStart()            // mang 5; lần đọc IABTCF của nó bị chặn trước cổng
            assertTrue("session_start tới lần đọc IABTCF", entered.await(5, TimeUnit.SECONDS))

            ConsentState.setTier(ConsentTier.GRANTED)

            assertEquals(1, server.awaitRequests(EventNames.BEHAVIOR, 1).size)
            gateBarrier()          // khối evaluate của lượt xả (gồm writePendingLocked) xong: pending() đã là giá trị sau xả
            if (pending() != 5) org.junit.Assert.fail("process chết lúc này vẫn còn 5 để báo, đang ${pending()} [${gateState()}]")
            release.countDown()
            assertEquals(5, server.await(EventNames.SESSION_START, 1).single().get("pre_consent_lost")?.asInt)
            awaitTrue("prefs = 0 sau khi session_start gửi, đang ${pending()}") { pending() == 0 }
        }
    }

    @Test fun `R3 tran tran khong day first_open, session_start - b1 bi day, KEY_PENDING sau xa = 1`() {
        store[Constants.Common.TIME_OPEN] = 1
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            LogEventServer.sessionStart()
            awaitTrue("first_open, session_start trong bộ đệm") { TelemetryConsentGate.heldCount == 2 }
            emitBehaviors(1, 49)
            assertEquals(1L, TelemetryConsentGate.evictedCount)
            assertEquals(TelemetryConsentGate.HOLD_CAP, TelemetryConsentGate.heldCount)

            ConsentState.setTier(ConsentTier.GRANTED)

            awaitTrue("50 event đã xả") { TelemetryConsentGate.flushedCount == 50L }
            val sentNames = server.allRequests().map { it.get("eventName").asString to itemOf(it) }
            assertTrue("first_open tới server: $sentNames", sentNames.any { it.first == EventNames.FIRST_OPEN })
            assertTrue("session_start tới server: $sentNames", sentNames.any { it.first == EventNames.SESSION_START })
            assertFalse("b1 là cái bị đẩy: $sentNames", sentNames.any { it.second == "b1" })
            awaitTrue("KEY_PENDING = 1 (b1 mất, báo ở lần mở kế), đang ${pending()}") { pending() == 1 }
        }
    }

    @Test fun `R4 bo dem xa truoc event dang toi - thu tu b1, b2, b3`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            emitBehaviors(1, 2)
            ConsentState.resetForTest()             // gỡ người nghe: chỉ event kế tiếp mới xả bộ đệm
            ConsentState.setTier(ConsentTier.GRANTED)
            LogEventServer.logEvent("b3", "s")

            val sent = server.awaitRequests(EventNames.BEHAVIOR, 3)
            assertEquals(listOf("b1", "b2", "b3"), sent.map { itemOf(it) })
        }
    }

    @Test fun `R5 DENIED bo session_start mang so cua process truoc - KEY_PENDING ve 0, 0 request`() {
        store[TelemetryConsentGate.KEY_PENDING] = 3
        store[Constants.Common.TIME_OPEN] = 2
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            LogEventServer.sessionStart()
            awaitTrue("session_start trong bộ đệm") { TelemetryConsentGate.heldCount == 1 }
            awaitTrue("prefs = 4 (3 + session_start)") { pending() == 4 }

            ConsentState.setTier(ConsentTier.DENIED)

            awaitTrue("prefs = 0 sau DENIED, đang ${pending()}") { pending() == 0 }
            assertEquals(0, server.awaitRequests(EventNames.SESSION_START, 0, 200).size)
        }
    }

    @Test fun `R6 xa khong chay tren luong goi setTier`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            emitBehaviors(1, 2)
            ConsentState.setTier(ConsentTier.GRANTED)
            assertEquals(2, server.awaitRequests(EventNames.BEHAVIOR, 2).size)
            val caller = Thread.currentThread().name
            assertFalse("xả chạy trên luồng gọi setTier ($caller): ${server.threads}", server.threads.contains(caller))
        }
    }

    @Test fun `R7 brain_decision dang giu khong tinh vao KEY_PENDING`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use {
            (1..3).forEach { i ->
                LogEventServer.logBrainDecision(
                    decisionId = "d_$i", decisionPoint = "interstitial", chosenActionId = "show",
                    chosenActionMode = "explore", chosenActionProbability = 0.5, eligibleActions = "show,skip",
                    guardrailsApplied = "", segment = "s", segmentConfidence = 1.0, scenario = "x",
                    isHoldout = 0, policyVersion = "p1", onResult = {}
                )
                awaitTrue("d_$i trong bộ đệm") { TelemetryConsentGate.heldCount == i }
            }
            // heldCount đọc dưới khoá cổng và tăng cùng khối với writePendingLocked: thấy 3 nghĩa là lần ghi pending đã xong.
            gateBarrier()
            assertEquals("brain_decision còn bản trên đĩa", 0, pending())
        }
    }

    @Test fun `R8 event toi trong luc xa dang gui - moi event gui dung mot lan, dung thu tu`() {
        TelemetryConsentGate.install(inGdprUnknown)
        val sending = CountDownLatch(1)
        val release = CountDownLatch(1)
        ServerEventCapture().use { server ->
            emitBehaviors(1, 2)
            server.beforeSend = { json ->
                if (json.contains("\"b1\"") && sending.count > 0) {
                    sending.countDown()
                    release.await(30, TimeUnit.SECONDS)
                }
            }
            ConsentState.setTier(ConsentTier.GRANTED)
            assertTrue("lượt xả đang gửi b1", sending.await(30, TimeUnit.SECONDS))
            val built = LogEventTransport.builtCount.get()
            LogEventServer.logEvent("b3", "s")
            // b3 dựng xong (builtCount tăng ngay trước khi vào cổng) thì đang tới khoá cổng mà lượt xả còn giữ: dấu hiệu quan sát
            // được, rẻ (không dump luồng). Khe còn lại giữa "dựng xong" và "vào khoá" chỉ làm kịch bản yếu đi, kết quả đúng như nhau
            // (b3 tới sau lượt xả cũng ra b1, b2, b3), nên không cần ngủ chờ thêm.
            awaitTrue("b3 dựng xong, đang tới cổng", timeoutMs = 30_000) { LogEventTransport.builtCount.get() > built }
            release.countDown()

            awaitTrue("b1, b2, b3 tới server", timeoutMs = 30_000) { server.allRequests().size >= 3 }
            server.awaitRequests(EventNames.BEHAVIOR, 3)   // + khoảng lặng ngắn để bắt event thừa
            assertEquals(listOf("b1", "b2", "b3"), server.allRequests().map { itemOf(it) })
        }
    }

    @Test fun `N1-d vong DENIED tran 500 bo cu nhat, DROPPED dung mot lan`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ConsentState.setTier(ConsentTier.DENIED)
        val results = java.util.concurrent.CopyOnWriteArrayList<Pair<Int, Boolean>>()
        fun comfort(i: Int) = LogEventServer.logBrainComfort(i.toDouble(), 1.0, "", "p") { results += i to it }
        ServerEventCapture().use { server ->
            comfort(1)
            // Thứ tự vào vòng là thứ tự tới cổng, không phải thứ tự gọi: mỗi event dựng trên Dispatchers.IO. Chờ c1 THẬT SỰ
            // trong vòng (đọc `deniedHeld` dưới khoá cổng) rồi mới phát các cái sau; sleep không bảo đảm gì dưới tải.
            awaitTrue("c1 vào vòng DENIED") { deniedRingSize() == 1 }
            (2..501).forEach(::comfort)

            // 501 event = 500 trong vòng + 1 bị bỏ: hai số đó cùng đúng nghĩa là cả 501 đã tới cổng, không cần ngủ chờ thêm.
            awaitTrue("500 trong vòng và một event bị bỏ, đang ${deniedRingSize()}/${results.size}") { deniedRingSize() == 500 && results.size == 1 }
            assertEquals("tràn 500: đúng một DROPPED, là cái cũ nhất: $results", listOf(1 to false), results.toList())
            assertEquals("0 request dưới DENIED", 0, server.allRequests().size)

            ConsentState.setTier(ConsentTier.GRANTED)

            awaitTrue("vòng xả 500, đang ${TelemetryConsentGate.flushedCount}") { TelemetryConsentGate.flushedCount == 500L }
        }
    }

    @Test fun `N1-e KEY_PENDING khong tinh event giu sau DENIED`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use { server ->
            emitBehaviors(1, 2)
            awaitTrue("prefs = 2") { pending() == 2 }
            ConsentState.setTier(ConsentTier.DENIED)
            awaitTrue("prefs = 0 sau DENIED, đang ${pending()}") { pending() == 0 }

            (3..5).forEach { LogEventServer.logEvent("b$it", "s") }
            awaitTrue("b3..b5 trong vòng DENIED") { deniedRingSize() == 3 }
            gateBarrier()          // khối evaluate của cái cuối (gồm writePendingLocked) xong trước khi đọc pending
            assertEquals("vòng DENIED chỉ trong RAM: KEY_PENDING không tính", 0, pending())

            ConsentState.setTier(ConsentTier.GRANTED)

            val sent = server.awaitRequests(EventNames.BEHAVIOR, 3).mapNotNull { itemOf(it) }
            assertEquals("b3..b5 gửi khi tầng đổi: $sent", listOf("b3", "b4", "b5"), sent.sorted())
            assertEquals(0, pending())
        }
    }

    @Test fun `R9 DENIED xoa ca so da bi day ra - nguoi tu choi khong nam trong funnel`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ServerEventCapture().use {
            emitBehaviors(1, 51)
            awaitTrue("prefs = 51 (50 giữ + 1 đẩy ra)") { pending() == 51 }

            ConsentState.setTier(ConsentTier.DENIED)

            awaitTrue("prefs = 0 sau DENIED, đang ${pending()}") { pending() == 0 }
        }
    }


    @Test fun `R6 C1 DENIED roi UNKNOWN roi GRANTED - vong DENIED xa truoc held`() {
        TelemetryConsentGate.install(inGdprUnknown)
        ConsentState.setTier(ConsentTier.DENIED)
        ServerEventCapture().use { server ->
            LogEventServer.logEvent("d1", "s")
            awaitTrue("d1 trong vòng DENIED") { deniedRingSize() == 1 }
            ConsentState.setTier(ConsentTier.UNKNOWN)
            awaitTrue("lượt đánh giá lại UNKNOWN xong") { gateIoIdle() }
            emitBehaviors(1, 1)
            assertEquals("UNKNOWN: 0 request, vòng DENIED giữ nguyên", 0, server.awaitRequests(EventNames.BEHAVIOR, 0, 300).size)
            ConsentState.setTier(ConsentTier.GRANTED)
            val sent = server.awaitRequests(EventNames.BEHAVIOR, 2)
            assertEquals("thứ tự phát: vòng DENIED (d1) trước held (b1)", listOf("d1", "b1"), sent.map { it.getAsJsonObject("eventData").get("id_item").asString })
            val ids = sent.map { it.get("sequenceId").asInt }
            assertEquals("sequenceId tăng dần: $ids", ids.sorted(), ids)
        }
    }
}
