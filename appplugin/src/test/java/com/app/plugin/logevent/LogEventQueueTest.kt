package com.app.plugin.logevent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Item gửi được ngay, hoặc `null` khi phải chờ ([LogEventQueue.nextOrWait] không lấy item khi trả Wait). */
private fun LogEventQueue.next(): LogEventQueue.Item? = (nextOrWait() as? LogEventQueue.Next.Send)?.item

/** Thời gian chờ; chỉ gọi khi chắc chắn không có item gửi được ngay (Send thì test hỏng ở phép ép kiểu). */
private fun LogEventQueue.waitMs(): Long = (nextOrWait() as LogEventQueue.Next.Wait).ms

/**
 * Cổng G6 (doc 65): trần 500, tối đa 4 request đang bay, backoff 1→60 s, bỏ bản ghi cũ nhất khi đầy.
 * Đồng hồ tiêm vào ([now]) để kiểm backoff mà không chờ thật.
 */
class LogEventQueueTest {
    private var now = 0L

    private fun queue(maxPending: Int = 500, maxInFlight: Int = 4) =
        LogEventQueue(maxPending = maxPending, maxInFlight = maxInFlight, nowMs = { now })

    private fun item(i: Int) = LogEventQueue.Item("ev", "body$i")

    @Test fun `day thi bo ban ghi cu nhat`() {
        val q = queue(maxPending = 3)
        val evicted = (1..4).mapNotNull { q.offer(item(it)) }
        assertEquals(3, q.pendingCount)
        assertEquals(1L, q.dropped)
        assertEquals("offer tra lai ban ghi bi bo de nguoi goi bao onResult(false)", listOf("body1"), evicted.map { it.body })
        assertEquals("body2", q.next()?.body)
    }

    @Test fun `tran mac dinh la 500`() {
        val q = queue()
        (1..501).forEach { q.offer(item(it)) }
        assertEquals(500, q.pendingCount)
        assertEquals(1L, q.dropped)
    }

    @Test fun `khong qua maxInFlight request dang bay`() {
        val q = queue(maxInFlight = 2)
        (1..3).forEach { q.offer(item(it)) }
        assertNotNull(q.next()); assertNotNull(q.next())
        assertNull(q.next())
        assertEquals(2, q.inFlight)
        q.onSuccess()
        assertNotNull(q.next())
    }

    @Test fun `mac dinh toi da 4 request dang bay`() {
        val q = queue()
        (1..6).forEach { q.offer(item(it)) }
        val sent = generateSequence { q.next() }.toList()
        assertEquals(4, sent.size)
        assertEquals(2, q.pendingCount)
    }

    @Test fun `backoff tang gap doi tu 1 s toi tran 60 s`() {
        val q = queue()
        assertEquals(1_000L, q.backoffFor(1))
        assertEquals(2_000L, q.backoffFor(2))
        assertEquals(32_000L, q.backoffFor(6))
        assertEquals(60_000L, q.backoffFor(7))
        assertEquals(60_000L, q.backoffFor(20))
        assertEquals(60_000L, q.backoffFor(Int.MAX_VALUE))
    }

    @Test fun `that bai thi tra lai dau hang, cho het backoff moi gui lai`() {
        val q = queue()
        q.offer(item(1)); q.offer(item(2))
        val first = q.next()!!
        assertEquals(LogEventQueue.FailureOutcome.Retry(1_000L, attempt = 1), q.onFailure(first))
        assertEquals(0, q.inFlight)
        assertNull("chua het 1 s backoff thi khong gui", q.next())
        assertEquals(1_000L, q.waitMs())
        now += 999
        assertNull(q.next())
        now += 1
        assertEquals("item loi o dau hang", "body1", q.next()?.body)
    }

    @Test fun `sau loi, offer moi khong lam item loi duoc gui lai truoc han`() {
        // Phụ lục 1: brief gọi pump() ở mỗi offer, item lỗi nằm đầu hàng nên bị gửi lại ngay, bỏ qua backoff.
        val q = queue()
        q.offer(item(1))
        q.onFailure(q.next()!!)
        q.offer(item(2))
        assertNull("offer moi khong mo cong backoff", q.next())
        now += 1_000
        assertEquals("body1", q.next()?.body)
        assertEquals("body2", q.next()?.body)
    }

    @Test fun `dong ho lui 1 gio sau loi thi van gui lai dung sau backoff`() {
        // Fix wave 1 (I1): người chơi chỉnh giờ lùi — cổng không được đóng thêm đúng bằng khoảng lùi.
        val q = queue()
        q.offer(item(1))
        q.onFailure(q.next()!!)
        now -= 3_600_000
        assertNull(q.next())
        now += 999
        assertNull(q.next())
        now += 1
        assertEquals("body1", q.next()?.body)
    }

    @Test fun `dong ho lui giua hai lan loi thi cong khong dong lau hon backoff`() {
        // Fix wave 2 (M1): onFailure đọc hạn cổng cũ trước khi now() dời nó theo đồng hồ lùi.
        val q = queue()
        (1..2).forEach { q.offer(item(it)) }
        val a = q.next()!!
        val b = q.next()!!
        q.onFailure(a)          // cổng tới 1000
        now -= 3_600_000        // lùi 1 giờ, chưa có lần đọc đồng hồ nào
        q.onFailure(b)          // chuỗi 2 lỗi: 2 s tính từ bây giờ
        assertEquals(2_000L, q.waitMs())
    }

    @Test fun `dong ho mac dinh la dong ho don dieu tinh bang ms`() {
        val before = System.nanoTime() / 1_000_000
        val t = monotonicNowMs()
        val after = System.nanoTime() / 1_000_000
        assertTrue("monotonicNowMs theo System.nanoTime, khong theo gio tuong", t in before..after)
    }

    @Test fun `cho trong la tran tru so item dang cho`() {
        val q = queue(maxPending = 5, maxInFlight = 1)
        (1..3).forEach { q.offer(item(it)) }
        q.next()
        assertEquals(3, q.headroom)
    }

    @Test fun `thanh cong thi mo cong backoff`() {
        val q = queue()
        (1..2).forEach { q.offer(item(it)) }
        val a = q.next()!!
        val b = q.next()!!
        q.onFailure(a)
        assertNull(q.next())
        q.onSuccess()   // b tới được server: mạng đã về
        assertEquals("body1", q.next()?.body)
        assertEquals(0L, q.waitMs())
        assertEquals("body2", b.body)   // b không quay lại hàng: nó đã thành công
        assertNull(q.next())
    }

    @Test fun `mat mang lau thi backoff tang toi 60 s ke ca khi moi item chi loi vai lan`() {
        val q = queue()
        (1..20).forEach { q.offer(item(it)) }
        val waits = mutableListOf<Long>()
        repeat(12) {
            val it2 = q.next()!!
            when (val r = q.onFailure(it2)) {
                is LogEventQueue.FailureOutcome.Retry -> waits += r.delayMs
                is LogEventQueue.FailureOutcome.Dropped -> waits += q.waitMs()
            }
            now += q.waitMs()
        }
        assertEquals(60_000L, waits.last())
        assertTrue("backoff khong giam khi loi lien tiep", waits.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun `qua 5 lan that bai thi bo`() {
        val q = queue()
        q.offer(item(1))
        val outcomes = (1..6).map {
            val it2 = q.next()!!
            q.onFailure(it2).also { now += q.waitMs() }
        }
        assertEquals(
            (1..5).map { LogEventQueue.FailureOutcome.Retry(q.backoffFor(it), attempt = it) },
            outcomes.take(5)
        )
        assertEquals(LogEventQueue.FailureOutcome.Dropped(attempts = 6), outcomes.last())
        assertEquals(0, q.pendingCount)
        assertEquals(1L, q.dropped)
    }

    @Test fun `bi tu choi thi nha cho va dem bo`() {
        val q = queue(maxInFlight = 1)
        q.offer(item(1)); q.offer(item(2))
        q.next()
        assertNull(q.next())
        q.onRejected()
        assertEquals(1L, q.dropped)
        assertEquals("4xx khong dong cong backoff", "body2", q.next()?.body)
    }

    @Test fun `hang day luc item loi quay lai thi item loi la cu nhat va bi bo`() {
        val q = queue(maxPending = 2)
        q.offer(item(1))
        val first = q.next()!!
        q.offer(item(2)); q.offer(item(3))
        assertEquals(LogEventQueue.FailureOutcome.Dropped(attempts = 1, queueFull = true), q.onFailure(first))
        assertEquals(2, q.pendingCount)
        now += q.waitMs()
        assertEquals("body2", q.next()?.body)
    }
}
