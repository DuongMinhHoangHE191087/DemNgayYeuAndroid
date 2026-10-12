package com.app.plugin.license

import android.content.Context
import android.os.Handler
import android.os.Message
import com.app.plugin.license.Ed25519TestSigner.CERT
import com.app.plugin.license.Ed25519TestSigner.NOW_MS
import com.app.plugin.license.Ed25519TestSigner.PKG
import com.app.plugin.license.Ed25519TestSigner.token
import com.app.plugin.license.LicenseGate.Verdict
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.same
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 2.4.3 FASTSTART-A (Hợp đồng B, A): việc chờ verdict chạy lại trên main bằng `Message` **async** gửi
 * `sendMessageAtFrontOfQueue` trên `Handler(mainLooper)` — vượt hàng đợi và sync barrier của khung hình, vẫn trên main.
 */
class LicenseMainPostTest {
    @Before fun setUp() = LicenseGate.resetForTest()

    @After fun tearDown() = LicenseGate.resetForTest()

    /** Gọi [LicenseGate.mainPoster] với `Message`/`Handler` giả; trả handler đã tạo và message đã gửi. */
    private fun postThroughDefault(): Pair<Handler, Message> {
        val r = Runnable { }
        val msg = mock(Message::class.java)
        mockStatic(Message::class.java).use { m ->
            m.`when`<Message> { Message.obtain(any(Handler::class.java), same(r)) }.thenReturn(msg)
            mockConstruction(Handler::class.java).use { c ->
                LicenseGate.mainPoster(r)
                assertEquals("đúng một Handler(mainLooper)", 1, c.constructed().size)
                return c.constructed()[0] to msg
            }
        }
    }

    @Test fun `FA1 poster mac dinh gui message async dau hang doi main`() {
        val (h, msg) = postThroughDefault()
        verify(h).sendMessageAtFrontOfQueue(msg)
        verify(msg).setAsynchronous(true)
        verify(h, never()).post(any(Runnable::class.java))
    }

    @Test fun `FA2 sau resetForTest poster van la ban front-async`() {
        LicenseGate.mainPoster = { it.run() }
        LicenseGate.resetForTest()
        val (h, msg) = postThroughDefault()
        verify(h).sendMessageAtFrontOfQueue(msg)
        verify(msg).setAsynchronous(true)
    }

    @Test fun `FA3 main khong cho verdict, rerun chay qua poster seam sau verdict`() {
        val posted = LinkedBlockingQueue<Runnable>()
        LicenseGate.keys = { Ed25519TestSigner.KEYS }
        LicenseGate.clock = { NOW_MS }
        val hold = CountDownLatch(1)
        LicenseGate.certsProvider = { hold.await(5, TimeUnit.SECONDS); listOf(CERT) }
        LicenseGate.mainPoster = { posted += it }
        val ctx = mock(Context::class.java).also { `when`(it.packageName).thenReturn(PKG) }
        var reran = 0
        LicenseGate.start(ctx, token())
        assertFalse("PENDING: cổng trả false ngay, không chờ", LicenseGate.allowsAdNetworks(ctx) { reran++ })
        assertTrue("chưa gửi gì khi verdict chưa có", posted.isEmpty())
        hold.countDown()
        val job = posted.poll(5, TimeUnit.SECONDS)
        assertTrue("rerun phải được gửi qua poster", job != null)
        assertEquals(Verdict.Valid(Ed25519TestSigner.KID, Ed25519TestSigner.EXP), LicenseGate.verdict)
        job!!.run()
        assertEquals(1, reran)
    }
}
