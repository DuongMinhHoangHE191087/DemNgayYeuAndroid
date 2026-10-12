package com.app.plugin.logevent

import android.util.Log
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.endsWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import retrofit2.Response

/**
 * Phụ lục Task 14a: [LogEventSender] nối [LogEventQueue] với Retrofit. Mỗi event báo `onOutcome` đúng
 * một lần (gửi được / bị bỏ), mọi nhánh bỏ đều log (Điều 7), 4xx cố định không thử lại, body luôn đóng.
 * Đồng hồ, lịch hẹn gửi lại và `Call` đều giả: không mạng, không chờ thật.
 */
class LogEventSenderTest {
    private var now = 0L
    private val calls = mutableListOf<HeldCall>()
    private val wakeups = mutableListOf<Pair<Long, () -> Unit>>()   // (hạn, việc)
    private val results = mutableListOf<Pair<String, Boolean>>()   // (body, server nhận?)
    private val outcomes = mutableListOf<Pair<String, EventOutcome>>()

    private fun sender(
        queue: LogEventQueue = LogEventQueue(nowMs = { now }),
        transport: (String) -> HeldCall = { json -> HeldCall(json) }
    ) = LogEventSender(
        queue = queue,
        transport = { json -> transport(json).also { calls += it } },
        schedule = { delayMs, block -> wakeups += (now + delayMs) to block },
        nowMs = { now }
    )

    private fun LogEventSender.submit(i: Int) =
        submit(LogEventQueue.Item("ev$i", "body$i", onOutcome = { o ->
            results += "body$i" to (o == EventOutcome.ACCEPTED)
            outcomes += "body$i" to o
        }))

    /** Tiến đồng hồ rồi chạy các lần hẹn gửi lại đã tới hạn, như coroutine `delay` sẽ làm. */
    private fun advance(ms: Long) {
        now += ms
        val due = wakeups.filter { it.first <= now }
        wakeups.removeAll(due)
        due.forEach { it.second() }
    }

    private fun ok(): Response<ResponseBody> = Response.success("ok".toResponseBody())
    private fun http(code: Int): Response<ResponseBody> = Response.error(code, "err".toResponseBody())

    @Test fun `6 event chi tao 4 request toi khi 1 request tra loi`() {
        val s = sender()
        (1..6).forEach { s.submit(it) }
        assertEquals(4, calls.size)
        assertEquals(2, s.pendingCount)

        calls[0].respond(ok())

        assertEquals(5, calls.size)
        assertEquals("body5", calls[4].body)
        assertEquals(listOf("body1" to true), results)
    }

    @Test fun `thanh cong thi onResult true dung mot lan va dong body`() {
        val s = sender()
        val body = mock(ResponseBody::class.java)
        s.submit(1)

        calls[0].respond(Response.success(body))

        verify(body).close()
        assertEquals(listOf("body1" to true), results)
        assertTrue("khong hen gui lai", wakeups.isEmpty())
    }

    @Test fun `http loi thi dong errorBody ca khi thu lai lan khi bo`() {
        val s = sender()
        s.submit(1); s.submit(2)
        val retried = mock(ResponseBody::class.java)
        val rejected = mock(ResponseBody::class.java)

        calls[0].respond(Response.error(500, retried))
        calls[1].respond(Response.error(400, rejected))

        verify(retried).close()
        verify(rejected).close()
    }

    @Test fun `http 400 thi bo, onResult false, log event dropped`() {
        val s = sender()
        mockStatic(Log::class.java).use { log ->
            s.submit(1)
            calls[0].respond(http(400))
            log.verify { Log.e(eq("AppPlugin"), contains("event dropped: http 400")) }
        }
        assertEquals(listOf("body1" to false), results)
        assertEquals("4xx la bi tu choi, khong phai bi bo tam thoi", listOf("body1" to EventOutcome.REJECTED), outcomes)
        assertTrue("4xx khong hen gui lai", wakeups.isEmpty())
        advance(60_000)
        assertEquals(1, calls.size)
    }

    private fun assertRetriedAfterBackoff(code: Int) {
        val s = sender()
        s.submit(1)

        calls[0].respond(http(code))

        assertTrue("chua bao ket qua khi con thu lai", results.isEmpty())
        advance(999)
        assertEquals("chua het 1 s backoff", 1, calls.size)
        advance(1)
        assertEquals(listOf("body1", "body1"), calls.map { it.body })
        calls[1].respond(ok())
        assertEquals(listOf("body1" to true), results)
    }

    @Test fun `http 429 thi thu lai sau backoff`() = assertRetriedAfterBackoff(429)

    /** 1.7.0 Task 6, phán quyết R-9: 429 thử lại theo `Retry-After` của server (backend T9 gửi giây, ≥ 1), không sớm hơn. */
    @Test fun `RA http 429 co Retry-After 7 thi gui lai sau 7 s, khong som hon`() {
        val s = sender()
        s.submit(1)
        val raw = okhttp3.Response.Builder().code(429).message("Too Many Requests").protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("http://localhost/").build()).header("Retry-After", "7").build()

        calls[0].respond(Response.error("err".toResponseBody(), raw))

        assertTrue("429 chua phai ket qua cuoi", results.isEmpty())
        advance(6_999)
        assertEquals("chua het Retry-After 7 s thi chua gui lai", 1, calls.size)
        advance(1)
        assertEquals(listOf("body1", "body1"), calls.map { it.body })
    }

    @Test fun `http 408 thi thu lai sau backoff`() = assertRetriedAfterBackoff(408)

    @Test fun `http 500 thi thu lai sau backoff`() = assertRetriedAfterBackoff(500)

    @Test fun `loi mang thi thu lai, event moi khong lam gui lai truoc han`() {
        val s = sender()
        s.submit(1)
        calls[0].fail()

        s.submit(2)
        assertEquals("event moi khong mo cong backoff", 1, calls.size)

        advance(1_000)
        assertEquals(listOf("body1", "body1", "body2"), calls.map { it.body })
    }

    @Test fun `het luot thu thi bo, onResult false mot lan, log`() {
        val s = sender()
        mockStatic(Log::class.java).use { log ->
            s.submit(1)
            repeat(6) {
                calls.last().fail()
                advance(60_000)
            }
            log.verify { Log.e(eq("AppPlugin"), contains("event dropped: ev1 after 6 attempts")) }
        }
        assertEquals("1 lan dau + 5 lan thu lai", 6, calls.size)
        assertEquals(listOf("body1" to false), results)
        assertEquals(listOf("body1" to EventOutcome.DROPPED), outcomes)
    }

    @Test fun `day hang thi bo ban ghi cu nhat, onResult false, log`() {
        val s = sender(queue = LogEventQueue(maxPending = 2, maxInFlight = 1, nowMs = { now }))
        mockStatic(Log::class.java).use { log ->
            (1..4).forEach { s.submit(it) }   // 1 đang bay; 2, 3 chờ; 4 đẩy 2 ra
            log.verify { Log.e(eq("AppPlugin"), contains("event queue full (2): dropped oldest")) }
        }
        assertEquals(listOf("body2" to false), results)
        assertEquals(listOf("body2" to EventOutcome.DROPPED), outcomes)
        calls[0].respond(ok())
        assertEquals(listOf("body1", "body3"), calls.map { it.body })
    }

    @Test fun `bo vi day hang log lan dau roi moi 100 lan`() {
        val s = sender(queue = LogEventQueue(maxPending = 1, maxInFlight = 1, nowMs = { now }))
        mockStatic(Log::class.java).use { log ->
            (1..202).forEach { s.submit(it) }   // 1 đang bay, 1 chờ, 200 lần đẩy bản cũ nhất ra
            log.verify({ Log.e(eq("AppPlugin"), contains("event queue full")) }, times(3))
            log.verify { Log.e(eq("AppPlugin"), endsWith("total 1")) }
            log.verify { Log.e(eq("AppPlugin"), endsWith("total 100")) }
            log.verify { Log.e(eq("AppPlugin"), endsWith("total 200")) }
        }
        assertEquals("moi ban bi bo deu co onResult(false)", 200, results.count { !it.second })
    }

    @Test fun `cong mo dung giua hai lan doc dong ho thi van gui hoac hen gui lai`() {
        // Fix wave 1 (M1): next() đọc 999 (cổng đóng), retryWaitMs() đọc 1998 (cổng đã mở) → 0 → không hẹn,
        // item kẹt tới event kế. Đồng hồ ở đây nhảy `step` ms sau mỗi lần đọc.
        var step = 0L
        val s = sender(queue = LogEventQueue(nowMs = { now.also { now += step } }))
        s.submit(1)

        step = 999
        calls[0].fail()   // onFailure đọc 0 → cổng tới 1000; lần đọc kế là 999
        step = 0

        assertTrue("phai gui lai ngay hoac hen gui lai", calls.size > 1 || wakeups.isNotEmpty())
    }

    @Test fun `hang day luc item loi quay lai thi bo item loi, onResult false`() {
        val s = sender(queue = LogEventQueue(maxPending = 1, maxInFlight = 1, nowMs = { now }))
        s.submit(1); s.submit(2)
        calls[0].fail()
        assertEquals(listOf("body1" to false), results)
        advance(1_000)
        assertEquals(listOf("body1", "body2"), calls.map { it.body })
    }

    @Test fun `transport nem loi thi thu lai sau backoff, khong nem ra ngoai`() {
        var failNext = true
        val s = sender(transport = { json ->
            if (failNext) { failNext = false; throw IllegalStateException("chua init") }
            HeldCall(json)
        })

        s.submit(1)
        assertTrue(calls.isEmpty())
        advance(1_000)
        assertEquals(listOf("body1"), calls.map { it.body })
    }

    @Test fun `onResult nem loi thi hang van chay`() {
        val s = sender()
        s.submit(LogEventQueue.Item("ev1", "body1", onOutcome = { throw IllegalStateException("boom") }))
        (2..5).forEach { s.submit(it) }

        calls[0].respond(ok())

        assertEquals("event thu 5 duoc gui sau khi slot nha ra", 5, calls.size)
    }

    @Test fun `nhieu request cung loi chi hen mot lan gui lai`() {
        val s = sender()
        (1..4).forEach { s.submit(it) }
        calls.toList().forEach { it.fail() }
        assertEquals(1, wakeups.size)

        advance(8_000)   // chuỗi 4 lỗi liên tiếp: backoff 8 s
        assertEquals(listOf("body4", "body3", "body2", "body1"), calls.drop(4).map { it.body })
    }
}
