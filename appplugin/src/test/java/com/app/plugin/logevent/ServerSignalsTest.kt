package com.app.plugin.logevent

import android.util.Log
import com.app.plugin.iap.Entitlements
import com.app.plugin.iap.IapPurchaseState
import com.app.plugin.iap.SubState
import com.app.plugin.iap.SubscriptionLifecycle
import com.app.plugin.iap.SubscriptionServerState
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import retrofit2.Response
import java.io.File
import java.util.concurrent.Executor

/**
 * 1.8.0 Task 10 (plan "Hợp đồng C", D9): `signals` trong phản hồi 200 của `/push` → nhãn vòng đời subscription, không
 * bao giờ quyền lợi. G1–G6 đặc tả (`ServerSignals` mới), G7 hồi quy (body luôn đóng), G8 DENIED → 0 signal, G9 test
 * nguồn (đỏ trên mã cũ): body giao cho [ServerSignals] trước khi đóng.
 */
class ServerSignalsTest {
    private val calls = mutableListOf<HeldCall>()
    private val outcomes = mutableListOf<EventOutcome>()
    private val defaultExecutor = ServerSignals.executor

    @Before fun setUp() {
        SubscriptionLifecycle.clear()
        SubscriptionServerState.clear()
        Entitlements.clearAll()
        ServerSignals.executor = Executor { it.run() }
    }

    @After fun tearDown() {
        ServerSignals.executor = defaultExecutor
        SubscriptionLifecycle.clear()
        SubscriptionServerState.clear()
        Entitlements.clearAll()
    }

    private fun sender() = LogEventSender(
        queue = LogEventQueue(nowMs = { 0L }),
        transport = { json -> HeldCall(json).also { calls += it } },
        schedule = { _, _ -> },
        nowMs = { 0L }
    ).also { it.submit(LogEventQueue.Item("ev", "body", onOutcome = { o -> outcomes += o })) }

    private fun sig(pid: String, state: String, ms: Long, v: String = "1", type: String = "\"sub_state\"") =
        """{"v":$v,"type":$type,"product_id":"$pid","state":"$state","event_ms":$ms}"""

    private fun body(vararg s: String) = """{"ok":true,"accepted":1,"rejected":0,"signals":[${s.joinToString(",")}]}"""

    private fun push(json: String): Int = ServerSignals.apply(json.toByteArray())

    /** Body không rõ độ dài; đếm số byte thật sự bị kéo lên từ nguồn và biết đã đóng chưa. */
    private class CountingBody(private val bytes: ByteArray) : ResponseBody() {
        var consumed = 0L
        var closed = false
        private val src: BufferedSource = object : Source {
            private var pos = 0
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (pos >= bytes.size) return -1L
                val n = minOf(byteCount, (bytes.size - pos).toLong()).toInt()
                sink.write(bytes, pos, n)
                pos += n
                consumed += n
                return n.toLong()
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() { closed = true }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1L
        override fun source(): BufferedSource = src
    }

    @Test fun `G1 2xx co GRACE dat nhan IN_GRACE_PERIOD va subscription_state mang source server`() {
        SubscriptionLifecycle.observe(listOf(SubscriptionLifecycle.SubSnapshot("vip", IapPurchaseState.PURCHASED, true)))
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.stateFor("vip"))

        val events = ServerEventCapture().use { server ->
            sender()
            calls[0].respond(Response.success(CountingBody(body(sig("vip", "GRACE", 1_000L)).toByteArray())))
            server.await("subscription_state", 1)
        }

        assertEquals(listOf(EventOutcome.ACCEPTED), outcomes)
        assertEquals(SubState.IN_GRACE_PERIOD, SubscriptionLifecycle.stateFor("vip"))
        assertEquals("GRACE", SubscriptionServerState.stateFor("vip"))
        assertEquals(1, events.size)
        val e = events.single()
        assertEquals(listOf("vip", "ACTIVE", "IN_GRACE_PERIOD", "server"), listOf("product_id", "from_state", "to_state", "source").map { e.get(it).asString })
    }

    @Test fun `G2 muoi trang thai server anh xa dung nhan cong khai, trang thai goc giu noi bo`() {
        val expected = linkedMapOf(
            "GRACE" to SubState.IN_GRACE_PERIOD, "ON_HOLD" to SubState.LAPSED, "PAUSED" to SubState.LAPSED,
            "REVOKED" to SubState.LAPSED, "EXPIRED" to SubState.LAPSED, "CANCELED" to SubState.CANCELLED,
            "RECOVERED" to SubState.ACTIVE, "RENEWED" to SubState.ACTIVE, "RESTARTED" to SubState.ACTIVE, "PURCHASED" to SubState.ACTIVE
        )
        val all = expected.keys.map { state -> sig("p_$state", state, 5_000L) }
        assertEquals(5, push(body(*all.take(5).toTypedArray())))
        assertEquals(5, push(body(*all.drop(5).toTypedArray())))

        for ((state, label) in expected) {
            assertEquals("nhãn của $state", label, SubscriptionLifecycle.stateFor("p_$state"))
            assertEquals("trạng thái gốc của $state", state, SubscriptionServerState.stateFor("p_$state"))
            assertEquals(5_000L, SubscriptionServerState.lastAppliedMs("p_$state"))
        }
    }

    @Test fun `G3 body qua 4096 byte bi bo ca khoi, chi doc co gioi han, log mot dong`() {
        val signal = body(sig("vip", "GRACE", 1_000L))
        val huge = CountingBody((signal.dropLast(1) + ",\"pad\":\"" + "x".repeat(1 shl 20) + "\"}").toByteArray())
        val exact = signal.dropLast(1) + ",\"pad\":\"\"}"
        val exactBytes = (exact.dropLast(2) + "x".repeat(ServerSignals.MAX_BYTES - exact.length) + "\"}").toByteArray()
        assertEquals(ServerSignals.MAX_BYTES, exactBytes.size)

        mockStatic(Log::class.java).use { log ->
            sender()
            calls[0].respond(Response.success(huge))
            assertTrue("chỉ kéo lên ≤ 16 KiB của body 1 MiB: ${huge.consumed}", huge.consumed <= 16_384L)
            assertTrue(huge.closed)
            assertEquals(SubState.NONE, SubscriptionLifecycle.stateFor("vip"))
            assertNull(SubscriptionServerState.stateFor("vip"))
            log.verify({ Log.e(eq("AppPlugin"), contains("signals bỏ")) }, times(1))
        }

        // Đúng 4096 byte vẫn nhận.
        ServerSignals.onPushResponse(ServerSignals.read(CountingBody(exactBytes)))
        assertEquals(SubState.IN_GRACE_PERIOD, SubscriptionLifecycle.stateFor("vip"))
    }

    @Test fun `G4 signal khong bao gio grant hay revoke quyen loi`() {
        Entitlements.grant("vip")
        assertEquals(3, push(body(sig("vip", "REVOKED", 1_000L), sig("gold", "PURCHASED", 1_000L), sig("vip2", "EXPIRED", 1_000L))))

        assertEquals(SubState.LAPSED, SubscriptionLifecycle.stateFor("vip"))
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.stateFor("gold"))
        assertTrue("REVOKED từ server không thu hồi", Entitlements.isActive("vip"))
        assertFalse("PURCHASED từ server không cấp", Entitlements.isActive("gold"))
        assertEquals(setOf("vip"), Entitlements.activeProductIds())
    }

    @Test fun `G5 sai dang thi bo, khong nem, phan tu hop le cung khoi van ap`() {
        val bad = listOf(
            sig("a", "GRACE", 1_000L, v = "2"), sig("b", "GRACE", 1_000L, v = "\"1\""), sig("c", "GRACE", 1_000L, v = "1.5"),
            sig("d", "GRACE", 1_000L, type = "\"other\""), sig("e", "WEIRD", 1_000L), sig("", "GRACE", 1_000L),
            """{"v":1,"type":"sub_state","product_id":"f","state":"GRACE","event_ms":"1000"}""",
            """{"v":1,"type":"sub_state","product_id":"g","state":"GRACE","event_ms":1.5}""",
            """{"v":1,"type":"sub_state","product_id":"h","state":"GRACE"}""", sig("i", "GRACE", 0L), "7", "null"
        )
        for (b in bad) assertEquals(b, 0, push(body(b)))
        for (raw in listOf("not json", "[]", "{\"ok\":true}", "{\"signals\":{}}", "{\"signals\":null}")) assertEquals(raw, 0, push(raw))
        val six = (1..6).map { sig("s$it", "GRACE", 1_000L) }
        assertEquals("> 5 phần tử: bỏ cả khối", 0, push(body(*six.toTypedArray())))
        ServerSignals.onPushResponse(null)
        ServerSignals.onPushResponse(ByteArray(0))

        assertEquals(1, push(body(sig("z", "WEIRD", 1_000L), sig("ok", "CANCELED", 1_000L))))
        assertEquals(SubState.CANCELLED, SubscriptionLifecycle.stateFor("ok"))
        for (pid in listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "s1", "s6", "z")) {
            assertEquals(pid, SubState.NONE, SubscriptionLifecycle.stateFor(pid))
            assertNull(pid, SubscriptionServerState.stateFor(pid))
        }
    }

    @Test fun `G6 event_ms khong moi hon lan ap cuoi thi bo`() {
        assertEquals(1, push(body(sig("vip", "GRACE", 2_000L))))
        assertEquals("cùng tín hiệu ở /push kế", 0, push(body(sig("vip", "GRACE", 2_000L))))
        assertEquals(0, push(body(sig("vip", "RECOVERED", 2_000L))))
        assertEquals(0, push(body(sig("vip", "RECOVERED", 1_999L))))
        assertEquals(SubState.IN_GRACE_PERIOD, SubscriptionLifecycle.stateFor("vip"))
        assertEquals("GRACE", SubscriptionServerState.stateFor("vip"))

        assertEquals(1, push(body(sig("vip", "RECOVERED", 2_001L))))
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.stateFor("vip"))
        assertEquals(2_001L, SubscriptionServerState.lastAppliedMs("vip"))
        assertEquals("mốc theo sản phẩm", 1, push(body(sig("other", "GRACE", 1_000L))))
    }

    @Test fun `G7 2xx va loi - body van dong, loi khong ap signal`() {
        val okBody = CountingBody(body(sig("vip", "GRACE", 1_000L)).toByteArray())
        val errBody = CountingBody(body(sig("vip2", "GRACE", 1_000L)).toByteArray())
        sender()
        calls[0].respond(Response.success(okBody))
        sender()
        calls[1].respond(Response.error(500, errBody))

        assertTrue("2xx: body đóng", okBody.closed)
        assertTrue("lỗi: body đóng", errBody.closed)
        assertEquals(SubState.IN_GRACE_PERIOD, SubscriptionLifecycle.stateFor("vip"))
        assertEquals("không phải 2xx: không áp", SubState.NONE, SubscriptionLifecycle.stateFor("vip2"))
    }

    @Test fun `G8 DENIED thi khong gui nen khong co signal nao`() {
        val s = LogEventSender(
            queue = LogEventQueue(nowMs = { 0L }),
            transport = { json -> HeldCall(json).also { calls += it } },
            schedule = { _, _ -> },
            nowMs = { 0L }
        )
        s.allowed = { false }
        s.submit(LogEventQueue.Item("ev", "body", onOutcome = { o -> outcomes += o }))

        assertTrue("0 request", calls.isEmpty())
        assertEquals(listOf(EventOutcome.DROPPED), outcomes)
        assertEquals(SubState.NONE, SubscriptionLifecycle.stateFor("vip"))
        assertNull(SubscriptionServerState.stateFor("vip"))
    }

    @Test fun `G9 LogEventSender onResponse giao body cho ServerSignals truoc closeBodies`() {
        val t = File("src/main/java/com/app/plugin/logevent/LogEventSender.kt").readText()
        val onResp = t.substringAfter("override fun onResponse", "").substringBefore("override fun onFailure", "")
        val i = onResp.indexOf("ServerSignals")
        val j = onResp.indexOf("closeBodies")
        assertEquals(1, if (i in 0 until j) 1 else 0)
    }
}
