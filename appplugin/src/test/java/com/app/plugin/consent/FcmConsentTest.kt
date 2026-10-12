package com.app.plugin.consent

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.brain.HeldExecutor
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

/**
 * FCM (auto-init và token) chỉ chạy khi tầng cho phép: PARTIAL/GRANTED, hoặc ngoài GDPR
 * (`IABTCF_gdprApplies = 0`). Task 9 fix F1, F2.
 *
 * Mọi lời gọi FirebaseMessaging và lấy token chạy trên luồng nền của [FcmConsent] (DOC/66 D1: đặt cờ auto-init
 * trên main làm Firebase ghi đĩa). Test thay luồng đó bằng [HeldExecutor]: lời gọi chỉ xảy ra khi test gọi
 * `runAll()`, trên luồng test, nơi `mockStatic` có hiệu lực.
 */
class FcmConsentTest {
    private val realExecutor = FcmConsent.executor
    private val realDelayed = FcmConsent.delayed
    private val fcmThread = HeldExecutor()

    /** Hẹn giờ của [FcmConsent.delayed] (hạn việc token, Task J2): test tự chạy khi muốn "hết hạn". */
    private val timers = mutableListOf<Pair<Runnable, Long>>()

    @Before fun setUp() {
        ConsentState.resetForTest()
        FcmConsent.resetForTest()
        FcmConsent.executor = fcmThread
        FcmConsent.delayed = { r, ms -> timers += r to ms }
    }

    @After fun tearDown() {
        FcmConsent.executor = realExecutor
        FcmConsent.delayed = realDelayed
        FcmConsent.resetForTest()
        ConsentState.resetForTest()
    }

    // ═══ Luật ═══

    @Test fun `ngoai GDPR thi cho phep moi tang tru DENIED`() {
        for (tier in ConsentTier.values().filter { it != ConsentTier.DENIED }) {
            assertTrue("$tier", FcmConsent.allows(tier, gdprApplies = false))
        }
    }

    /** Fix wave 2, Minor 6: `IABTCF_gdprApplies = 0` (có thể là giá trị cũ) không thắng `DENIED`. */
    @Test fun `DENIED thang IABTCF_gdprApplies = 0`() =
        assertFalse(FcmConsent.allows(ConsentTier.DENIED, gdprApplies = false))

    @Test fun `trong GDPR hoac khong biet thi chi PARTIAL va GRANTED`() {
        for (gdpr in listOf(true, null)) {
            assertTrue(FcmConsent.allows(ConsentTier.GRANTED, gdpr))
            assertTrue(FcmConsent.allows(ConsentTier.PARTIAL, gdpr))
            assertFalse(FcmConsent.allows(ConsentTier.DENIED, gdpr))
            assertFalse(FcmConsent.allows(ConsentTier.UNKNOWN, gdpr))
        }
    }

    // ═══ F2: lúc mở app tắt auto-init tới khi có consent (máy cũ đã lưu auto-init = on) ═══

    /** Task của `deleteToken()` trong [withMessaging]; test đọc listener đã gắn để giả lập kết quả xoá. */
    private lateinit var deleteTask: Task<Void>

    private fun withMessaging(block: (FirebaseMessaging, MockedStatic<FirebaseMessaging>, MockedStatic<Log>) -> Unit) {
        val fcm = mock(FirebaseMessaging::class.java)
        @Suppress("UNCHECKED_CAST")
        val task = mock(Task::class.java) as Task<Void>
        `when`(fcm.deleteToken()).thenReturn(task)
        // Task thật trả chính nó (`@NonNull`); mock trả null làm Kotlin ném NPE sau khi gắn listener (Task I6: lỗi đó
        // từng bị nuốt thành "delete failed" giả trong test).
        `when`(task.addOnCompleteListener(any<OnCompleteListener<Void>>())).thenReturn(task)
        deleteTask = task
        mockStatic(FirebaseMessaging::class.java).use { messaging ->
            messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }.thenReturn(fcm)
            mockStatic(Log::class.java).use { log -> block(fcm, messaging, log) }
        }
    }

    /** D1 (b): trên luồng gọi (main) không chạm FirebaseMessaging; cờ `false` chỉ được gán khi luồng nền chạy. */
    @Test fun `tang chua cho phep thi tat auto-init luc mo app chi tren luong nen`() = withMessaging { fcm, messaging, _ ->
        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = null, purposes = ""))

        assertEquals(1, fcmThread.size)
        messaging.verify({ FirebaseMessaging.getInstance() }, never())
        verifyNoInteractions(fcm)

        fcmThread.runAll()

        verify(fcm).isAutoInitEnabled = false
    }

    @Test fun `UNKNOWN va khong biet GDPR thi tat auto-init luc mo app`() = withMessaging { fcm, _, _ ->
        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = null, purposes = ""))
        fcmThread.runAll()

        verify(fcm).isAutoInitEnabled = false
    }

    @Test fun `DENIED trong GDPR thi tat auto-init luc mo app`() = withMessaging { fcm, _, _ ->
        ConsentState.setTier(ConsentTier.DENIED)

        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = 1, purposes = "0000000000"))
        fcmThread.runAll()

        verify(fcm).isAutoInitEnabled = false
    }

    @Test fun `DENIED ma IABTCF_gdprApplies = 0 thi van tat auto-init luc mo app`() = withMessaging { fcm, _, _ ->
        ConsentState.setTier(ConsentTier.DENIED)

        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = 0, purposes = ""))
        fcmThread.runAll()

        verify(fcm).isAutoInitEnabled = false
    }

    @Test fun `GRANTED hoac PARTIAL hoac ngoai GDPR thi khong dung vao FCM luc mo app`() =
        withMessaging { fcm, messaging, _ ->
            ConsentState.setTier(ConsentTier.GRANTED)
            FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = 1, purposes = "1111111111"))
            ConsentState.setTier(ConsentTier.PARTIAL)
            FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = null, purposes = ""))
            ConsentState.setTier(ConsentTier.UNKNOWN)
            FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = 0, purposes = ""))

            assertEquals("không xếp việc nào lên luồng nền", 0, fcmThread.size)
            messaging.verify({ FirebaseMessaging.getInstance() }, never())
            verifyNoInteractions(fcm)
        }

    /** D1 (d): lỗi trong việc trên luồng nền được logE ở đó, không ném (luồng nền ném là crash process). */
    @Test fun `tat auto-init loi thi logE, khong nem ra ngoai`() {
        mockStatic(FirebaseMessaging::class.java).use { messaging ->
            messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }
                .thenThrow(IllegalStateException("FirebaseApp not initialized"))
            mockStatic(Log::class.java).use { log ->
                FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = null, purposes = ""))
                log.verify({ Log.e(eq(LogUtil.TAG), anyString()) }, never())

                fcmThread.runAll()

                log.verify { Log.e(eq(LogUtil.TAG), contains("fcm auto-init hold failed")) }
            }
        }
    }

    // ═══ F1: sau khi UMP trả lời, theo tầng; token đúng một lần ═══

    private class Counter { var calls = 0 }

    /** Lần lấy token xong ngay (gọi `onDone` luôn), như `getToken()` trả Task đã hoàn tất. */
    private fun follower(counter: Counter) = FcmConsent.Follower { done -> counter.calls++; done() }

    private val granted: Context get() = contextWithTcf(gdpr = 1, purposes = "1111111111")

    /** D1 (a): trên luồng gọi (main) không chạm FirebaseMessaging, không lấy token; trên luồng nền: cờ rồi token. */
    @Test fun `tang cho phep thi dat auto-init roi lay token, ca hai tren luong nen`() = withMessaging { fcm, messaging, _ ->
        val fetchToken = mock(Runnable::class.java)
        ConsentState.setTier(ConsentTier.GRANTED)

        FcmConsent.Follower { done -> fetchToken.run(); done() }.follow(granted)

        assertEquals(1, fcmThread.size)
        messaging.verify({ FirebaseMessaging.getInstance() }, never())
        verifyNoInteractions(fcm, fetchToken)

        fcmThread.runAll()

        val order = inOrder(fcm, fetchToken)
        order.verify(fcm).isAutoInitEnabled = true
        order.verify(fetchToken).run()
    }

    /** D1 (c): quyết định "đã lấy token" đặt trước khi xếp hàng, nên hai việc đang chờ không lấy hai token. */
    @Test fun `hai lan follow cho phep truoc khi luong nen chay thi lay token mot lan`() = withMessaging { fcm, _, _ ->
        val counter = Counter()
        val f = follower(counter)
        ConsentState.setTier(ConsentTier.GRANTED)

        f.follow(granted)
        f.follow(granted)
        assertEquals(2, fcmThread.size)
        fcmThread.runAll()

        verify(fcm, times(2)).isAutoInitEnabled = true
        assertEquals(1, counter.calls)
    }

    /** D1 + 2.4.0 R-CARRY-POL m3: cùng một hàng đợi; việc giữ đọc lại tầng lúc chạy — consent tới trước thì không ghi `false`. */
    @Test fun `tat luc mo app roi cho phep sau consent thi viec giu bo qua, chi true`() = withMessaging { fcm, _, _ ->
        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = null, purposes = ""))
        ConsentState.setTier(ConsentTier.GRANTED)
        follower(Counter()).follow(granted)

        assertEquals(2, fcmThread.size)
        fcmThread.runAll()

        verify(fcm, never()).isAutoInitEnabled = false
        verify(fcm).isAutoInitEnabled = true
        verify(fcm, never()).deleteToken()
    }

    @Test fun `GRANTED thi bat auto-init va lay token dung mot lan`() = withMessaging { fcm, _, _ ->
        val counter = Counter()
        val f = follower(counter)
        ConsentState.setTier(ConsentTier.GRANTED)

        f.follow(granted)
        fcmThread.runAll()
        ConsentState.setTier(ConsentTier.PARTIAL)
        f.follow(granted)
        fcmThread.runAll()

        verify(fcm, times(2)).isAutoInitEnabled = true
        assertEquals(1, counter.calls)
    }

    @Test fun `DENIED trong GDPR thi auto-init tat va khong lay token`() = withMessaging { fcm, _, _ ->
        val counter = Counter()
        ConsentState.setTier(ConsentTier.DENIED)

        follower(counter).follow(contextWithTcf(gdpr = 1, purposes = "0000000000"))
        fcmThread.runAll()

        verify(fcm).isAutoInitEnabled = false
        verify(fcm, never()).isAutoInitEnabled = true
        assertEquals(0, counter.calls)
    }

    @Test fun `UNKNOWN khi khong biet GDPR thi khong lay token`() = withMessaging { fcm, _, _ ->
        val counter = Counter()

        follower(counter).follow(contextWithTcf(gdpr = null, purposes = ""))
        fcmThread.runAll()

        verify(fcm).isAutoInitEnabled = false
        assertEquals(0, counter.calls)
    }

    @Test fun `DENIED roi GRANTED thi lay token mot lan`() = withMessaging { fcm, _, _ ->
        val counter = Counter()
        val f = follower(counter)
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(granted)
        fcmThread.runAll()
        assertEquals(0, counter.calls)

        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()

        val order = inOrder(fcm)
        order.verify(fcm).isAutoInitEnabled = false
        order.verify(fcm).isAutoInitEnabled = true
        assertEquals(1, counter.calls)
    }

    @Test fun `rut lai tu GRANTED ve DENIED thi tat auto-init, khong lay token them`() = withMessaging { fcm, _, _ ->
        val counter = Counter()
        val f = follower(counter)
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()

        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(granted)
        fcmThread.runAll()

        val order = inOrder(fcm)
        order.verify(fcm).isAutoInitEnabled = true
        order.verify(fcm).isAutoInitEnabled = false
        assertEquals(1, counter.calls)
    }

    @Test fun `ngoai GDPR thi lay token ngay ca khi tang UNKNOWN`() = withMessaging { fcm, _, _ ->
        val counter = Counter()

        follower(counter).follow(contextWithTcf(gdpr = 0, purposes = ""))
        fcmThread.runAll()

        verify(fcm).isAutoInitEnabled = true
        assertEquals(1, counter.calls)
    }

    @Test fun `dat auto-init loi thi logE va van lay token khi duoc phep`() {
        val counter = Counter()
        ConsentState.setTier(ConsentTier.GRANTED)
        mockStatic(FirebaseMessaging::class.java).use { messaging ->
            messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }
                .thenThrow(IllegalStateException("FirebaseApp not initialized"))
            mockStatic(Log::class.java).use { log ->
                follower(counter).follow(granted)
                fcmThread.runAll()

                log.verify { Log.e(eq(LogUtil.TAG), contains("fcm auto-init update failed")) }
            }
        }
        assertEquals(1, counter.calls)
    }

    // ═══ #8 (DOC/66): rút consent thì xoá token FCM ═══

    private val denied: Context get() = contextWithTcf(gdpr = 1, purposes = "0000000000")

    /** Listener kết quả mà `deleteToken()` được gắn (một lần). */
    private fun deleteListener(): OnCompleteListener<Void> {
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(OnCompleteListener::class.java) as ArgumentCaptor<OnCompleteListener<Void>>
        verify(deleteTask).addOnCompleteListener(captor.capture())
        return captor.value
    }

    @Test fun `GRANTED roi DENIED thi xoa token dung mot lan, tren luong nen, sau khi tat auto-init`() =
        withMessaging { fcm, _, log ->
            val f = follower(Counter())
            ConsentState.setTier(ConsentTier.GRANTED)
            f.follow(granted)
            fcmThread.runAll()

            ConsentState.setTier(ConsentTier.DENIED)
            f.follow(denied)
            verify(fcm, never()).deleteToken()   // không trên luồng gọi (main)
            fcmThread.runAll()

            val order = inOrder(fcm)
            order.verify(fcm).isAutoInitEnabled = true
            order.verify(fcm).isAutoInitEnabled = false
            order.verify(fcm).deleteToken()
            verify(fcm, times(1)).deleteToken()

            `when`(deleteTask.isSuccessful).thenReturn(true)
            deleteListener().onComplete(deleteTask)
            log.verify { Log.d(LogUtil.TAG, "fcm token deleted after consent withdrawn") }
        }

    @Test fun `DENIED roi DENIED thi khong xoa token`() = withMessaging { fcm, _, _ ->
        val f = follower(Counter())
        ConsentState.setTier(ConsentTier.DENIED)

        f.follow(denied)
        f.follow(denied)
        fcmThread.runAll()

        verify(fcm, never()).deleteToken()
    }

    /** Lần follow đầu không tính: chưa biết tầng trước đó có cho phép không, nên không có gì để gọi là "rút". */
    @Test fun `lan follow dau voi DENIED thi khong xoa token`() = withMessaging { fcm, _, _ ->
        ConsentState.setTier(ConsentTier.DENIED)

        follower(Counter()).follow(denied)
        fcmThread.runAll()

        verify(fcm).isAutoInitEnabled = false
        verify(fcm, never()).deleteToken()
    }

    @Test fun `van cho phep (GRANTED sang PARTIAL) thi khong xoa token`() = withMessaging { fcm, _, _ ->
        val f = follower(Counter())
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        ConsentState.setTier(ConsentTier.PARTIAL)
        f.follow(granted)
        fcmThread.runAll()

        verify(fcm, never()).deleteToken()
    }

    @Test fun `xoa token roi cap lai thi lay token moi mot lan`() = withMessaging { fcm, _, _ ->
        val counter = Counter()
        val f = follower(counter)
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)
        fcmThread.runAll()
        assertEquals(1, counter.calls)
        `when`(deleteTask.isSuccessful).thenReturn(true)
        deleteListener().onComplete(deleteTask)

        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        f.follow(granted)
        fcmThread.runAll()

        assertEquals("token cũ đã xoá: lần cấp lại lấy token mới, đúng một lần", 2, counter.calls)
        verify(fcm, times(1)).deleteToken()
    }

    // ═══ Task I6 (review C Minor 3): xoá token và lấy token không chạy đua ═══

    /** Lần lấy token giả: ghi lại, test tự báo xong ([finish]) như Task của `getToken()` hoàn tất sau. */
    private class HeldFetch {
        var calls = 0
        private val pending = mutableListOf<() -> Unit>()
        val inFlight: Int get() = pending.size
        fun start(done: () -> Unit) {
            calls++
            pending += done
        }
        fun finish() {
            val done = pending.toList()
            pending.clear()
            done.forEach { it() }
        }
    }

    @Test fun `dang xoa token ma cap lai thi chi lay token sau khi xoa xong`() = withMessaging { fcm, _, _ ->
        val fetch = HeldFetch()
        val f = FcmConsent.Follower { done -> fetch.start(done) }
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()
        fetch.finish()
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)
        fcmThread.runAll()
        verify(fcm, times(1)).deleteToken()

        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()
        assertEquals("delete chưa xong: chưa lấy token", 1, fetch.calls)
        verify(fcm, times(2)).isAutoInitEnabled = true

        `when`(deleteTask.isSuccessful).thenReturn(true)
        deleteListener().onComplete(deleteTask)
        fcmThread.runAll()

        assertEquals("lấy token trong completion của delete, đúng một lần", 2, fetch.calls)
    }

    @Test fun `xoa token that bai van lay token cap lai sau khi xoa xong`() = withMessaging { fcm, _, _ ->
        val fetch = HeldFetch()
        val f = FcmConsent.Follower { done -> fetch.start(done) }
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()
        fetch.finish()
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()
        assertEquals(1, fetch.calls)

        `when`(deleteTask.isSuccessful).thenReturn(false)
        `when`(deleteTask.exception).thenReturn(IllegalStateException("SERVICE_NOT_AVAILABLE"))
        deleteListener().onComplete(deleteTask)
        fcmThread.runAll()

        assertEquals(2, fetch.calls)
        verify(fcm, times(1)).deleteToken()
    }

    @Test fun `dang lay token ma rut consent thi xoa sau khi lay xong`() = withMessaging { fcm, _, _ ->
        val fetch = HeldFetch()
        val f = FcmConsent.Follower { done -> fetch.start(done) }
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()
        assertEquals(1, fetch.inFlight)

        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)
        fcmThread.runAll()
        verify(fcm).isAutoInitEnabled = false
        verify(fcm, never()).deleteToken()

        fetch.finish()
        fcmThread.runAll()

        verify(fcm, times(1)).deleteToken()
        assertEquals(1, fetch.calls)
    }

    @Test fun `dang lay token, rut roi cap lai truoc khi lay xong thi khong xoa, khong lay them`() =
        withMessaging { fcm, _, _ ->
            val fetch = HeldFetch()
            val f = FcmConsent.Follower { done -> fetch.start(done) }
            ConsentState.setTier(ConsentTier.GRANTED)
            f.follow(granted)
            fcmThread.runAll()
            ConsentState.setTier(ConsentTier.DENIED)
            f.follow(denied)
            ConsentState.setTier(ConsentTier.GRANTED)
            f.follow(granted)
            fcmThread.runAll()

            fetch.finish()
            fcmThread.runAll()

            verify(fcm, never()).deleteToken()
            assertEquals("token đang lấy dùng được cho tầng cho phép hiện tại", 1, fetch.calls)
        }

    @Test fun `dang xoa token, cap roi rut lai truoc khi xoa xong thi khong lay token`() = withMessaging { fcm, _, _ ->
        val fetch = HeldFetch()
        val f = FcmConsent.Follower { done -> fetch.start(done) }
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()
        fetch.finish()
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)
        fcmThread.runAll()

        `when`(deleteTask.isSuccessful).thenReturn(true)
        deleteListener().onComplete(deleteTask)
        fcmThread.runAll()

        assertEquals("tầng cuối là từ chối: không lấy token", 1, fetch.calls)
        verify(fcm, times(1)).deleteToken()
    }

    @Test fun `lay token nem loi van mo hang cho xoa token`() = withMessaging { fcm, _, _ ->
        val f = FcmConsent.Follower { throw IllegalStateException("token boom") }
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        fcmThread.runAll()
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)
        fcmThread.runAll()

        verify(fcm, times(1)).deleteToken()
    }

    @Test fun `xoa token that bai thi logE`() = withMessaging { _, _, log ->
        val f = follower(Counter())
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)
        fcmThread.runAll()

        `when`(deleteTask.isSuccessful).thenReturn(false)
        `when`(deleteTask.exception).thenReturn(IllegalStateException("SERVICE_NOT_AVAILABLE"))
        deleteListener().onComplete(deleteTask)

        log.verify { Log.e(eq(LogUtil.TAG), contains("fcm token delete after consent withdrawn failed: SERVICE_NOT_AVAILABLE")) }
    }

    @Test fun `deleteToken nem loi thi logE, khong nem ra ngoai`() = withMessaging { fcm, _, log ->
        `when`(fcm.deleteToken()).thenThrow(IllegalStateException("delete boom"))
        val f = follower(Counter())
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)

        fcmThread.runAll()

        log.verify { Log.e(eq(LogUtil.TAG), contains("fcm token delete after consent withdrawn failed: delete boom")) }
    }

    // ═══ Review C Minor 2 (F3): quyết định và xếp hàng cùng một khoá ═══

    /**
     * `executor.execute` chạy trong `synchronized(follower)`, cùng khoá với quyết định (`lastAllowed`, cờ token), nên hai
     * lần `follow` trên hai luồng không xếp hàng ngược thứ tự quyết định (xoá token sau lần lấy mới, hoặc `true` tới
     * Firebase cuối cùng khi tầng là DENIED). Kiểm bằng `Thread.holdsLock` lúc xếp hàng: xác định, không phụ thuộc lịch.
     */
    @Test fun `follow xep hang trong cung khoa voi quyet dinh`() = withMessaging { _, _, _ ->
        val heldAtEnqueue = mutableListOf<Boolean>()
        var f: FcmConsent.Follower? = null
        FcmConsent.executor = java.util.concurrent.Executor { r ->
            heldAtEnqueue += Thread.holdsLock(checkNotNull(f))
            fcmThread.execute(r)
        }
        f = follower(Counter())
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)

        assertEquals(listOf(true, true), heldAtEnqueue)
    }

    /**
     * Hợp đồng nguồn (brief F3): trong `follow`, tầng được đọc và việc được xếp hàng bên trong `synchronized(this)`, nên
     * lần `follow` xếp hàng sau cùng mang tầng mới nhất lúc nó giữ khoá (tầng đổi giữa hai lần gọi không để một quyết
     * định cũ xếp sau quyết định mới).
     */
    @Test fun `follow doc tang va xep hang trong khoa (hop dong nguon)`() {
        val source = java.io.File("src/main/java/com/app/plugin/consent/FcmConsent.kt").readText()
        val follow = source.substringAfter("fun follow(context: Context) {").substringBefore("private fun applyOnFcmThread")
        val lockAt = follow.indexOf("synchronized(this)")
        assertTrue("follow có khối synchronized(this)", lockAt >= 0)
        assertTrue("tầng đọc trong khoá", follow.indexOf("ConsentState.tier()") > lockAt)
        assertTrue("xếp hàng trong khoá", follow.indexOf("executor.execute") > lockAt)
    }

    /** D1 (d): token ném trên luồng nền thì logE ở đó, không ném ra khỏi việc. */
    @Test fun `lay token nem loi thi logE, khong nem ra ngoai`() = withMessaging { _, _, log ->
        ConsentState.setTier(ConsentTier.GRANTED)

        FcmConsent.Follower { throw IllegalStateException("token boom") }.follow(granted)
        log.verify({ Log.e(eq(LogUtil.TAG), anyString()) }, never())

        fcmThread.runAll()

        log.verify { Log.e(eq(LogUtil.TAG), contains("fcm token fetch after consent failed")) }
    }

    // ═══ Review C Minor 6: xoá token lỗi thì thử lại ở lần mở kế ═══

    /** Cờ "xoá token còn nợ" trong prefs của SDK (khoá → giá trị), trong [block]; trả lại prefs cũ sau đó. */
    private fun withPrefs(block: (MutableMap<String, Boolean>) -> Unit) {
        val helper = PreferenceHelper.getInstance()
        val previous = helper.preferences
        val store = mutableMapOf<String, Boolean>()
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getBoolean(anyString(), anyBoolean())).thenAnswer {
            store[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1)
        }
        `when`(prefs.contains(anyString())).thenAnswer { store.containsKey(it.getArgument<String>(0)) }
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenAnswer {
            store[it.getArgument<String>(0)] = it.getArgument<Boolean>(1)
            editor
        }
        `when`(editor.remove(anyString())).thenAnswer {
            store.remove(it.getArgument<String>(0))
            editor
        }
        helper.preferences = prefs
        try {
            block(store)
        } finally {
            helper.preferences = previous
        }
    }

    private fun withdraw(f: FcmConsent.Follower) {
        ConsentState.setTier(ConsentTier.GRANTED)
        f.follow(granted)
        ConsentState.setTier(ConsentTier.DENIED)
        f.follow(denied)
        fcmThread.runAll()
    }

    @Test fun `xoa token that bai thi luu co de thu lai lan mo ke, thanh cong thi xoa co`() = withPrefs { store ->
        withMessaging { _, _, _ ->
            withdraw(follower(Counter()))
            `when`(deleteTask.isSuccessful).thenReturn(false)
            `when`(deleteTask.exception).thenReturn(IllegalStateException("SERVICE_NOT_AVAILABLE"))
            deleteListener().onComplete(deleteTask)

            assertEquals(true, store[FcmConsent.KEY_DELETE_PENDING])
        }
    }

    /**
     * Final review Minor 5: `deleteToken()` mà Task không bao giờ hoàn tất thì sau hạn 30 s việc xoá coi là
     * xong-thất-bại cho làn; cờ thử lại lúc mở app cũng phải được đặt như khi xoá lỗi, không thì lần mở kế không xoá lại.
     * Completion thật tới muộn vẫn sửa cờ (thành công thì xoá cờ).
     */
    @Test fun `xoa token het han 30 s thi cung luu co, completion muon thanh cong thi xoa co`() = withPrefs { store ->
        withMessaging { fcm, _, log ->
            withdraw(follower(Counter()))
            verify(fcm, times(1)).deleteToken()
            assertEquals("xoá đang bay: chưa có cờ", null, store[FcmConsent.KEY_DELETE_PENDING])

            timers.last().first.run()   // hạn của việc xoá
            log.verify { Log.e(eq(LogUtil.TAG), contains("fcm token delete did not complete within 30000 ms")) }
            assertEquals(true, store[FcmConsent.KEY_DELETE_PENDING])

            `when`(deleteTask.isSuccessful).thenReturn(true)
            deleteListener().onComplete(deleteTask)   // completion thật tới muộn
            assertEquals(null, store[FcmConsent.KEY_DELETE_PENDING])
        }
    }

    @Test fun `deleteToken nem loi thi cung luu co`() = withPrefs { store ->
        withMessaging { fcm, _, _ ->
            `when`(fcm.deleteToken()).thenThrow(IllegalStateException("delete boom"))
            withdraw(follower(Counter()))

            assertEquals(true, store[FcmConsent.KEY_DELETE_PENDING])
        }
    }

    @Test fun `mo lai voi DENIED va co con thi xoa token lai mot lan tren luong nen, thanh cong thi xoa co`() =
        withPrefs { store ->
            store[FcmConsent.KEY_DELETE_PENDING] = true
            withMessaging { fcm, messaging, log ->
                ConsentState.setTier(ConsentTier.DENIED)

                FcmConsent.holdAutoInitUntilConsent(denied)
                messaging.verify({ FirebaseMessaging.getInstance() }, never())   // không trên luồng gọi (main)
                fcmThread.runAll()

                val order = inOrder(fcm)
                order.verify(fcm).isAutoInitEnabled = false
                order.verify(fcm).deleteToken()
                verify(fcm, times(1)).deleteToken()
                log.verify { Log.d(eq(LogUtil.TAG), contains("fcm token delete retried")) }

                `when`(deleteTask.isSuccessful).thenReturn(true)
                deleteListener().onComplete(deleteTask)
                assertFalse(store.containsKey(FcmConsent.KEY_DELETE_PENDING))
            }
        }

    @Test fun `mo lai voi DENIED khong co co thi khong xoa token`() = withPrefs { _ ->
        withMessaging { fcm, _, _ ->
            ConsentState.setTier(ConsentTier.DENIED)

            FcmConsent.holdAutoInitUntilConsent(denied)
            fcmThread.runAll()

            verify(fcm).isAutoInitEnabled = false
            verify(fcm, never()).deleteToken()
        }
    }

    /** Tầng cho phép trở lại: không xoá nữa (token mới sẽ lấy), cờ nợ được xoá. */
    @Test fun `co con ma tang cho phep lai thi khong xoa token va xoa co`() = withPrefs { store ->
        store[FcmConsent.KEY_DELETE_PENDING] = true
        withMessaging { fcm, _, _ ->
            ConsentState.setTier(ConsentTier.GRANTED)

            FcmConsent.holdAutoInitUntilConsent(granted)
            follower(Counter()).follow(granted)
            fcmThread.runAll()

            verify(fcm, never()).deleteToken()
            assertFalse(store.containsKey(FcmConsent.KEY_DELETE_PENDING))
        }
    }

    // ═══ Task J2: xoá lại lúc mở app xếp chung với Follower; việc token đang bay có hạn ═══

    /**
     * (a) Cờ `mem_fcm_delete_pending` còn, mở app với DENIED: lần xoá lại đi qua làn token dùng chung. Người dùng cấp
     * consent trong cùng phiên khi delete chưa xong → lần lấy token chờ; delete xong (thành công) → lấy token đúng một
     * lần, cờ nợ được xoá. Trước đây lần xoá lại không xếp chung: token có thể được lấy rồi bị xoá ngay sau.
     */
    @Test fun `xoa lai luc mo app dang bay thi lan lay token sau consent chi chay sau khi xoa xong`() = withPrefs { store ->
        store[FcmConsent.KEY_DELETE_PENDING] = true
        withMessaging { fcm, _, _ ->
            ConsentState.setTier(ConsentTier.DENIED)
            FcmConsent.holdAutoInitUntilConsent(denied)
            fcmThread.runAll()
            verify(fcm, times(1)).deleteToken()

            val fetch = HeldFetch()
            ConsentState.setTier(ConsentTier.GRANTED)
            FcmConsent.Follower { done -> fetch.start(done) }.follow(granted)
            fcmThread.runAll()
            verify(fcm).isAutoInitEnabled = true
            assertEquals("delete lúc mở app chưa xong: chưa lấy token", 0, fetch.calls)

            `when`(deleteTask.isSuccessful).thenReturn(true)
            deleteListener().onComplete(deleteTask)
            fcmThread.runAll()

            assertEquals("lấy token sau khi delete xong, đúng một lần", 1, fetch.calls)
            assertFalse(store.containsKey(FcmConsent.KEY_DELETE_PENDING))
        }
    }

    /**
     * Review Task J Minor 2: Follower nhận việc lấy token (tầng cho phép lại) trước khi việc xoá lại lúc mở app kịp chạy
     * trên luồng nền → delete xếp sau fetch. Tới lượt, delete kiểm lại tầng: đã cho phép thì không xoá token vừa lấy,
     * chỉ mở làn (cờ nợ đã được Follower xoá). Trước đây delete chạy mù, xoá token mới và `tokenFetched` vẫn true.
     */
    @Test fun `xoa lai luc mo app xep sau lan lay token thi kiem lai tang, da cho phep thi khong xoa`() = withPrefs { store ->
        store[FcmConsent.KEY_DELETE_PENDING] = true
        withMessaging { fcm, _, log ->
            ConsentState.setTier(ConsentTier.DENIED)
            FcmConsent.holdAutoInitUntilConsent(denied)   // việc xoá lại xếp hàng trên luồng nền, chưa chạy

            val fetch = HeldFetch()
            ConsentState.setTier(ConsentTier.GRANTED)
            FcmConsent.Follower { done -> fetch.start(done) }.follow(granted)   // nhận làn (FETCH) trước trên luồng gọi
            fcmThread.runAll()
            assertEquals("fetch chạy, delete xếp sau", 1, fetch.calls)
            verify(fcm, never()).deleteToken()
            assertFalse(store.containsKey(FcmConsent.KEY_DELETE_PENDING))

            fetch.finish()   // tới lượt việc xoá lại
            fcmThread.runAll()
            verify(fcm, never()).deleteToken()
            log.verify { Log.d(eq(LogUtil.TAG), contains("fcm token delete retry at launch skipped")) }
        }
    }

    /**
     * Context có `IABTCF_gdprApplies` đổi được giữa chừng (như UMP ghi khoá sau khi app đã gọi
     * [FcmConsent.holdAutoInitUntilConsent]): [gdpr] `null` = khoá vắng.
     */
    private class MutableTcf(var gdpr: Int?) {
        val context: Context = mock(Context::class.java).also { ctx ->
            val prefs = mock(SharedPreferences::class.java)
            `when`(prefs.contains("IABTCF_gdprApplies")).thenAnswer { gdpr != null }
            `when`(prefs.getInt("IABTCF_gdprApplies", 0)).thenAnswer { gdpr ?: 0 }
            `when`(prefs.getString(anyString(), anyString())).thenReturn("")
            `when`(ctx.packageName).thenReturn("com.example.app")
            `when`(ctx.getSharedPreferences(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(prefs)
        }
    }

    /**
     * Task K mục 3 (re-review Task J Minor 3): lúc mở app tầng UNKNOWN và chưa biết GDPR (khoá vắng) → việc xoá lại
     * xếp hàng. UMP rồi ghi `IABTCF_gdprApplies = 0` (ngoài GDPR), Follower (tầng vẫn UNKNOWN) được phép và nhận làn lấy
     * token trước khi việc xoá chạy → việc xoá xếp sau. Tới lượt, việc xoá kiểm lại bằng `gdprApplies` đọc lại trên
     * luồng nền (ngoài GDPR + UNKNOWN = cho phép) → không xoá token vừa lấy. Trước đây nó dùng `gdprApplies` chụp lúc
     * gọi (`null` = không biết → không cho phép) và xoá token mới.
     */
    @Test fun `xoa lai luc mo app doc lai gdprApplies khi toi luot, ngoai GDPR voi UNKNOWN thi khong xoa`() =
        withPrefs { store ->
            store[FcmConsent.KEY_DELETE_PENDING] = true
            withMessaging { fcm, _, log ->
                val tcf = MutableTcf(gdpr = null)
                assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
                FcmConsent.holdAutoInitUntilConsent(tcf.context)   // việc xoá lại xếp hàng trên luồng nền, chưa chạy

                tcf.gdpr = 0   // UMP ghi: ngoài GDPR
                val fetch = HeldFetch()
                FcmConsent.Follower { done -> fetch.start(done) }.follow(tcf.context)   // nhận làn (FETCH) trước
                fcmThread.runAll()
                assertEquals("fetch chạy, delete xếp sau", 1, fetch.calls)
                verify(fcm, never()).deleteToken()

                fetch.finish()   // tới lượt việc xoá lại
                fcmThread.runAll()
                verify(fcm, never()).deleteToken()
                log.verify { Log.d(eq(LogUtil.TAG), contains("fcm token delete retry at launch skipped")) }
                assertFalse(store.containsKey(FcmConsent.KEY_DELETE_PENDING))
            }
        }

    /**
     * Task L mục 4 (review Task K Minor 3): việc xoá lại lúc mở app có thể nằm trong làn tới 30 s+; nó chỉ giữ
     * `context.applicationContext`, không giữ context người gọi truyền vào (vd. một Activity). Context gọi chỉ được đọc
     * trên luồng gọi (quyết định tắt auto-init); việc trên luồng nền đọc `IABTCF_gdprApplies` qua application context.
     */
    @Test fun `xoa lai luc mo app chi giu applicationContext, khong giu context nguoi goi`() =
        withPrefs { store ->
            store[FcmConsent.KEY_DELETE_PENDING] = true
            withMessaging { fcm, _, log ->
                val app = MutableTcf(gdpr = null)
                val caller = MutableTcf(gdpr = null).context      // như một Activity
                `when`(caller.applicationContext).thenReturn(app.context)
                FcmConsent.holdAutoInitUntilConsent(caller)
                org.mockito.Mockito.clearInvocations(caller)

                app.gdpr = 0   // UMP ghi vào prefs (đọc qua application context): ngoài GDPR, UNKNOWN cho phép
                fcmThread.runAll()

                verifyNoInteractions(caller)
                verify(fcm, never()).deleteToken()
                log.verify { Log.d(eq(LogUtil.TAG), contains("fcm token delete retry at launch skipped")) }
            }
        }

    /**
     * (b) Task của việc token không bao giờ hoàn tất (review Task I M2): sau hạn 30 s việc đó coi là xong-thất-bại
     * (một dòng logE), việc đang chờ chạy tiếp; completion tới muộn sau đó bị bỏ (không mở làn lần hai, không đụng việc
     * đang bay). Trước đây làn kẹt mãi: rút consent khi lần lấy token treo thì không bao giờ xoá token.
     */
    @Test fun `viec token khong bao gio xong thi het han 30 s, viec cho chay tiep, completion muon bi bo`() =
        withMessaging { fcm, _, log ->
            val fetch = HeldFetch()
            val f = FcmConsent.Follower { done -> fetch.start(done) }
            ConsentState.setTier(ConsentTier.GRANTED)
            f.follow(granted)
            fcmThread.runAll()
            assertEquals(1, fetch.inFlight)
            assertEquals("hẹn hạn 30 s cho việc lấy token", listOf(30_000L), timers.map { it.second })

            ConsentState.setTier(ConsentTier.DENIED)
            f.follow(denied)
            fcmThread.runAll()
            verify(fcm, never()).deleteToken()

            timers.removeAt(0).first.run()   // hết hạn
            log.verify { Log.e(eq(LogUtil.TAG), contains("fcm token fetch did not complete within 30000 ms")) }
            fcmThread.runAll()
            verify(fcm, times(1)).deleteToken()
            assertEquals("delete cũng có hạn", listOf(30_000L), timers.map { it.second })

            fetch.finish()   // completion muộn của lần lấy đã hết hạn
            fcmThread.runAll()
            ConsentState.setTier(ConsentTier.GRANTED)
            f.follow(granted)
            fcmThread.runAll()
            assertEquals("delete vẫn đang bay: lần lấy token mới phải chờ, completion muộn không mở làn", 1, fetch.calls)

            `when`(deleteTask.isSuccessful).thenReturn(true)
            deleteListener().onComplete(deleteTask)
            fcmThread.runAll()
            assertEquals(2, fetch.calls)
        }
}
