package com.app.plugin.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerLoadGuardTest {
    private val home = "bn_home"
    private val detail = "bn_detail"

    @Test
    fun `callbacks of a cycle stop counting once a newer cycle begins`() {
        val guard = BannerLoadGuard()
        val first = guard.beginCycle(home)

        val second = guard.beginCycle(home)

        assertFalse(guard.isCurrent(home, first))
        assertTrue(guard.isCurrent(home, second))
    }

    @Test
    fun `a cycle on another placement leaves this one running`() {
        val guard = BannerLoadGuard()
        val onHome = guard.beginCycle(home)

        guard.beginCycle(detail)

        assertTrue(guard.isCurrent(home, onHome))
    }

    @Test
    fun `abandoning a stalled cycle turns its late callbacks stale`() {
        val guard = BannerLoadGuard()
        val stalled = guard.beginCycle(home)

        assertTrue(guard.abandon(home, stalled))

        assertFalse(guard.isCurrent(home, stalled))
    }

    @Test
    fun `a watchdog left over from a replaced cycle abandons nothing`() {
        val guard = BannerLoadGuard()
        val old = guard.beginCycle(home)
        val current = guard.beginCycle(home)

        assertFalse(guard.abandon(home, old))

        assertTrue(guard.isCurrent(home, current))
    }

    @Test
    fun `a step waiting for the networks to start is never stalled`() {
        assertFalse(BannerLoadGuard.isStallCountable(networksStartedAtMs = 0L, nowMs = 1_000_000L))
    }

    @Test
    fun `a step gets the full stall budget after the networks start`() {
        val startedAt = 1_000_000L

        assertFalse(BannerLoadGuard.isStallCountable(startedAt, startedAt + BannerLoadGuard.STALL_TIMEOUT_MS - 1))
        assertTrue(BannerLoadGuard.isStallCountable(startedAt, startedAt + BannerLoadGuard.STALL_TIMEOUT_MS))
    }

    @Test
    fun `retries after a failed cycle back off 5 s, 15 s, 45 s and then stop`() {
        val guard = BannerLoadGuard()

        assertEquals(5_000L, guard.nextRetryDelayMs())
        assertEquals(15_000L, guard.nextRetryDelayMs())
        assertEquals(45_000L, guard.nextRetryDelayMs())
        assertNull(guard.nextRetryDelayMs())
    }

    @Test
    fun `a filled banner restores the whole retry budget`() {
        val guard = BannerLoadGuard()
        repeat(3) { guard.nextRetryDelayMs() }

        guard.resetRetries()

        assertEquals(5_000L, guard.nextRetryDelayMs())
    }
}
