package com.app.plugin.consent

import android.util.Log
import com.app.plugin.brain.HeldExecutor
import com.app.plugin.firbase.FirebasePresence
import com.google.firebase.messaging.FirebaseMessaging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

/**
 * 2.4.0 CARRY-POL (minors-230 #31, F1a m11): Firebase vắng lúc `initPlugin` thì `holdAutoInitUntilConsent` không bỏ hẳn — hẹn
 * hỏi lại trên luồng FCM (giãn đôi, có trần); FirebaseApp có muộn (app tự khởi tạo) → giữ auto-init lại đúng một lần, nên cờ
 * auto-init `true` đã lưu từ bản ≤ 1.3.x không chạy trước consent. Hẹn giờ và luồng nền là bản tay của test.
 */
class FcmLateFirebaseTest {
    private val realExecutor = FcmConsent.executor
    private val realDelayed = FcmConsent.delayed
    private val fcmThread = HeldExecutor()
    private val timers = mutableListOf<Pair<Runnable, Long>>()
    @Volatile private var hasFirebase = false

    @Before fun setUp() {
        FirebasePresence.resetForTest()
        ConsentState.resetForTest()
        FcmConsent.resetForTest()
        FcmConsent.executor = fcmThread
        FcmConsent.delayed = { r, ms -> timers += r to ms }
        FirebasePresence.probe = { hasFirebase }
        FirebasePresence.warn = {}
    }

    @After fun tearDown() {
        FcmConsent.executor = realExecutor
        FcmConsent.delayed = realDelayed
        FcmConsent.resetForTest()
        ConsentState.resetForTest()
        FirebasePresence.resetForTest()
    }

    /** Chạy các hẹn giờ đang chờ (hẹn mới do chúng tạo chờ lượt sau), rồi luồng FCM; trả độ trễ đã chạy. */
    private fun fire(): List<Long> {
        val due = timers.toList().also { timers.clear() }
        due.forEach { it.first.run() }
        fcmThread.runAll()
        return due.map { it.second }
    }

    private fun withMessaging(block: (FirebaseMessaging) -> Unit) {
        val fcm = mock(FirebaseMessaging::class.java)
        mockStatic(FirebaseMessaging::class.java).use { messaging ->
            messaging.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance() }.thenReturn(fcm)
            mockStatic(Log::class.java).use { block(fcm) }
        }
    }

    @Test fun `CP7 Firebase vang luc init roi co thi giu auto-init lai dung 1 lan, auto-init tat toi consent`() = withMessaging { fcm ->
        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = 1, purposes = ""))
        fcmThread.runAll()
        verifyNoInteractions(fcm)
        assertEquals("hen hoi lai khi Firebase vang", 1, timers.size)
        assertEquals("van vang: hen tiep, gian doi", listOf(500L), fire())
        verifyNoInteractions(fcm)
        hasFirebase = true
        assertEquals(listOf(1_000L), fire())
        verify(fcm, times(1)).isAutoInitEnabled = false
        verify(fcm, never()).isAutoInitEnabled = true
        assertEquals("co Firebase: khong hen them", 0, timers.size)
        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = 1, purposes = ""))   // lần gọi sau (Firebase có): như cũ
        fcmThread.runAll()
        assertEquals("khong hen lai", 0, timers.size)
    }

    @Test fun `CP7b Firebase khong bao gio co thi hoi lai co tran roi dung, 0 loi goi FCM`() = withMessaging { fcm ->
        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = 1, purposes = ""))
        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = 1, purposes = ""))
        assertEquals("hai lan goi: mot chuoi hoi lai", 1, timers.size)
        val delays = mutableListOf<Long>()
        repeat(20) { delays += fire() }
        assertEquals((0 until 8).map { 500L shl it }, delays)
        verifyNoInteractions(fcm)
    }

    @Test fun `CP7c Firebase co muon ma tang da cho phep thi khong dung vao FCM`() = withMessaging { fcm ->
        val ctx = contextWithTcf(gdpr = 1, purposes = "")
        FcmConsent.holdAutoInitUntilConsent(ctx)
        ConsentState.setTier(ConsentTier.GRANTED)
        hasFirebase = true
        fire()
        verifyNoInteractions(fcm)
        assertEquals(0, timers.size)
    }

    @Test fun `CP7d resetForTest huy chuoi hoi lai dang hen`() = withMessaging { fcm ->
        FcmConsent.holdAutoInitUntilConsent(contextWithTcf(gdpr = 1, purposes = ""))
        FcmConsent.resetForTest()
        hasFirebase = true
        fire()
        verifyNoInteractions(fcm)
    }
}
