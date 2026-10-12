package com.app.plugin.iap

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 1.8.0 Task 11 — `BillingProxyCrashGuard` (D8/F9): quyết định thuần `shouldSwallow(isMain, throwable, count)` (K1–K4,
 * đặc tả: seam mới) — chỉ NPE có khung `ProxyBillingActivity`, trên main, trần 5 lượt/process; handler cài bọc handler
 * trước đó và chuyển nguyên mọi thứ khác (K4).
 */
class BillingProxyCrashGuardTest {
    private var saved: Thread.UncaughtExceptionHandler? = null

    @Before fun setUp() {
        saved = Thread.getDefaultUncaughtExceptionHandler()
        BillingProxyCrashGuard.resetForTest()
    }

    @After fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(saved)
        BillingProxyCrashGuard.resetForTest()
    }

    private fun frame(cls: String) = StackTraceElement(cls, "onCreate", "X.java", 1)

    /** NPE như crash thật: khung `ProxyBillingActivity.onCreate` dưới `ActivityThread.performLaunchActivity`. */
    private fun proxyNpe(): NullPointerException = NullPointerException("PendingIntent.getIntentSender() on null").apply {
        stackTrace = arrayOf(frame(BillingProxyCrashGuard.PROXY_ACTIVITY), frame("android.app.ActivityThread"))
    }

    private fun swallow(t: Throwable, isMain: Boolean = true, count: Int = 0) = BillingProxyCrashGuard.shouldSwallow(isMain, t, count)

    @Test fun `K1 NPE co khung ProxyBillingActivity tren main duoi tran thi nuot, ke ca khi la cause`() {
        assertTrue(swallow(proxyNpe()))
        assertTrue("bọc trong RuntimeException", swallow(RuntimeException("wrap", proxyNpe())))
        assertTrue("lượt thứ 5 (count = 4) vẫn nuốt", swallow(proxyNpe(), count = 4))
    }

    @Test fun `K2 NPE khong co khung ProxyBillingActivity thi khong nuot`() {
        assertFalse("NPE của app", swallow(NullPointerException("app").apply { stackTrace = arrayOf(frame("com.example.MainActivity")) }))
        assertFalse("NPE ở lớp billing khác", swallow(NullPointerException("x").apply {
            stackTrace = arrayOf(frame("com.android.billingclient.api.BillingClientImpl"))
        }))
    }

    @Test fun `K3 tran 5 luot moi process`() {
        assertEquals(5, BillingProxyCrashGuard.MAX_PER_PROCESS)
        assertFalse("count = 5 → không nuốt nữa", swallow(proxyNpe(), count = 5))
        assertFalse(swallow(proxyNpe(), count = 6))
    }

    @Test fun `K4 ngoai main hoac khong phai NPE thi khong nuot, handler cai mot lan va chuyen cho handler truoc`() {
        assertFalse("không phải main", swallow(proxyNpe(), isMain = false))
        assertFalse("IllegalStateException có khung ProxyBillingActivity", swallow(IllegalStateException("x").apply {
            stackTrace = arrayOf(frame(BillingProxyCrashGuard.PROXY_ACTIVITY))
        }))

        val seen = CopyOnWriteArrayList<Throwable>()
        val previous = Thread.UncaughtExceptionHandler { _, e -> seen += e }
        Thread.setDefaultUncaughtExceptionHandler(previous)
        BillingProxyCrashGuard.install()
        val guard = Thread.getDefaultUncaughtExceptionHandler()
        BillingProxyCrashGuard.install()
        assertSame("cài một lần mỗi process", guard, Thread.getDefaultUncaughtExceptionHandler())
        val other = IllegalStateException("app")
        guard.uncaughtException(Thread.currentThread(), other)
        // JVM test: không có main looper → luồng test không phải main → NPE billing cũng chuyển nguyên.
        val npe = proxyNpe()
        guard.uncaughtException(Thread.currentThread(), npe)
        assertEquals(listOf<Throwable>(other, npe), seen.toList())
    }
}
