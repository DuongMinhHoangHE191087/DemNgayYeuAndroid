package com.app.plugin.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkReadyQueueTest {
    @Test
    fun `loads wait for the network and then run once in arrival order`() {
        val queue = SdkReadyQueue()
        val runs = mutableListOf<String>()
        queue.runWhenReady { runs += "banner" }
        queue.runWhenReady { runs += "interstitial" }

        assertEquals(emptyList<String>(), runs)
        assertFalse(queue.isReady)

        queue.markReady()
        queue.markReady()

        assertEquals(listOf("banner", "interstitial"), runs)
        assertTrue(queue.isReady)
    }

    @Test
    fun `a load after the network is ready runs at once`() {
        val queue = SdkReadyQueue()
        queue.markReady()
        var ran = false

        queue.runWhenReady { ran = true }

        assertTrue(ran)
    }

    // ═══ P1 (DOC/66): mạng sẽ không khởi tạo (consent) thì load báo fail ngay, không chờ mãi ═══

    @Test
    fun `close fails each held load exactly once and later loads at once`() {
        val queue = SdkReadyQueue()
        val events = mutableListOf<String>()
        queue.runWhenReady({ events += "banner ran" }, { events += "banner failed" })
        queue.runWhenReady({ events += "inters ran" }, { events += "inters failed" })

        queue.close()
        queue.close()
        queue.runWhenReady({ events += "rewarded ran" }, { events += "rewarded failed" })

        assertEquals(listOf("banner failed", "inters failed", "rewarded failed"), events)
        assertTrue(queue.isClosed)
        assertFalse(queue.isReady)
    }

    @Test
    fun `reopen holds loads again until the network is ready`() {
        val queue = SdkReadyQueue()
        queue.close()
        val events = mutableListOf<String>()

        queue.reopen()
        queue.runWhenReady({ events += "ran" }, { events += "failed" })
        assertEquals(emptyList<String>(), events)

        queue.markReady()
        assertEquals(listOf("ran"), events)
        assertFalse(queue.isClosed)
    }

    @Test
    fun `close after the network is ready changes nothing`() {
        val queue = SdkReadyQueue()
        queue.markReady()

        queue.close()
        var ran = false
        queue.runWhenReady({ ran = true }, { throw AssertionError("network is ready, load must run") })

        assertTrue(ran)
        assertFalse(queue.isClosed)
    }

    /** Review C Minor 4: đóng trong khoá của người gọi, báo sau (ngoài khoá); mỗi load vẫn nhận đúng một callback. */
    @Test
    fun `closeDeferred closes at once but notifies held loads only when run, exactly once`() {
        val queue = SdkReadyQueue()
        val events = mutableListOf<String>()
        queue.runWhenReady({ events += "banner ran" }, { events += "banner failed" })

        val notify = queue.closeDeferred()
        assertTrue(queue.isClosed)
        assertEquals(emptyList<String>(), events)
        queue.runWhenReady({ events += "later ran" }, { events += "later failed" })   // đến sau khi đóng: báo ngay

        notify()
        notify()
        queue.closeDeferred().invoke()   // đã đóng: không có gì để báo
        assertEquals(listOf("later failed", "banner failed"), events)
    }

    /** Review C Minor 9: load đã báo fail lúc đóng không chạy khi mạng mở lại rồi sẵn sàng (không callback thứ hai). */
    @Test
    fun `a load failed at close never runs after reopen and markReady`() {
        val queue = SdkReadyQueue()
        val events = mutableListOf<String>()
        queue.runWhenReady({ events += "old ran" }, { events += "old failed" })
        queue.close()

        queue.reopen()
        queue.runWhenReady({ events += "new ran" }, { events += "new failed" })
        queue.markReady()

        assertEquals(listOf("old failed", "new ran"), events)
    }

    @Test
    fun `a load queued by a flushed load still runs`() {
        val queue = SdkReadyQueue()
        var nested = false
        queue.runWhenReady { queue.runWhenReady { nested = true } }

        queue.markReady()

        assertTrue(nested)
    }
}
