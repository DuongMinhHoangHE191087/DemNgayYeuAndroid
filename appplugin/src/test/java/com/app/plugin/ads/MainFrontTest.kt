package com.app.plugin.ads

import android.os.Handler
import android.os.Message
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 2.4.4 INITCB (Hợp đồng C): [MainFront] — ở main chạy tại chỗ (0 post); ngoài main gửi `Message` **async** bằng
 * `sendMessageAtFrontOfQueue` trên `Handler(mainLooper)` (mẫu FA1 2.4.3 `LicenseMainPostTest`), không `Handler.post`.
 */
class MainFrontTest {
    @After fun tearDown() = MainFront.resetForTest()

    /** Chạy [MainFront.run] với `Message`/`Handler` giả; trả số lần block chạy ngay, các Handler đã dựng, message giả. */
    private fun runThroughDefault(onMain: Boolean): Triple<Int, List<Handler>, Message> {
        MainFront.isMain = { onMain }
        var ran = 0
        val msg = mock(Message::class.java)
        mockStatic(Message::class.java).use { m ->
            m.`when`<Message> { Message.obtain(any(Handler::class.java), any(Runnable::class.java)) }.thenReturn(msg)
            mockConstruction(Handler::class.java).use { c ->
                MainFront.run { ran++ }
                return Triple(ran, c.constructed().toList(), msg)
            }
        }
    }

    @Test fun `IC2 o main chay tai cho, 0 Handler, 0 message`() {
        val (ran, handlers, _) = runThroughDefault(onMain = true)
        assertEquals("đang ở main (callback init GMA Legacy): chạy ngay", 1, ran)
        assertEquals("không dựng Handler, không post", 0, handlers.size)
    }

    @Test fun `IC2b ngoai main gui message async dau hang main, khong post cuoi hang`() {
        val (ran, handlers, msg) = runThroughDefault(onMain = false)
        assertEquals("ngoài main: chưa chạy tại chỗ", 0, ran)
        assertEquals("đúng một Handler(mainLooper)", 1, handlers.size)
        verify(handlers[0]).sendMessageAtFrontOfQueue(msg)
        verify(msg).setAsynchronous(true)
        verify(handlers[0], never()).post(any(Runnable::class.java))
    }

    @Test fun `IC2c seam poster nhan runnable chay dung block, resetForTest tra mac dinh`() {
        val posted = ArrayList<Runnable>()
        var ran = 0
        MainFront.isMain = { false }
        MainFront.poster = { posted += it }
        MainFront.run { ran++ }
        assertEquals(listOf(1, 0), listOf(posted.size, ran))
        posted.single().run()
        assertEquals(1, ran)
        MainFront.resetForTest()
        val (_, handlers, msg) = runThroughDefault(onMain = false)
        verify(handlers.single()).sendMessageAtFrontOfQueue(msg)
    }
}
