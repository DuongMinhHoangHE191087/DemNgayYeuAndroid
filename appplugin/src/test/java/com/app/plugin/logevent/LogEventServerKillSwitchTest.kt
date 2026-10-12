package com.app.plugin.logevent

import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.verifyNoInteractions

/**
 * Task 16: kill switch `logevent` chặn mọi event gửi lên server của SDK, ngay cả khi `LogEventServer`
 * đã init. `onResult(false)` báo đúng một lần, ngay, để hàng đợi decision log giữ bản ghi (chỉ xoá khi
 * server nhận); không event nào vào hàng đợi của Task 14a. Dòng log kill chỉ một lần mỗi process;
 * `resetSenderForTest` đặt lại cờ đó, nên hai test dưới cùng thấy đúng một dòng dù chạy theo thứ tự nào.
 */
class LogEventServerKillSwitchTest {
    private val logServiceField = LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }
    private val service = mock(LogService::class.java)

    @Before fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        LogEventServer.resetSenderForTest()
        logServiceField.set(null, service)
        SdkKillSwitch.current = SdkKillSwitch(logevent = true)
    }

    @After fun tearDown() {
        LogEventTestIsolation.cancelScheduledWork()
        logServiceField.set(null, null)   // lateinit trở về chưa init, như các test khác mong đợi
        LogEventServer.resetSenderForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    private fun decision(i: Int, onResult: ((Boolean) -> Unit)?) = LogEventServer.logBrainDecision(
        decisionId = "d_$i", decisionPoint = "interstitial", chosenActionId = "show",
        chosenActionMode = "explore", chosenActionProbability = 0.5, eligibleActions = "show,skip",
        guardrailsApplied = "", segment = "s", segmentConfidence = 1.0, scenario = "x",
        isHoldout = 0, policyVersion = "p1", onResult = onResult
    )

    @Test fun `kill logevent thi khong gui, moi event onResult false dung mot lan, log mot lan`() {
        val results = mutableListOf<Boolean>()
        mockStatic(Log::class.java).use { log ->
            decision(1) { results += it }
            decision(2) { results += it }

            assertEquals("mỗi event đúng một onResult(false), ngay", listOf(false, false), results)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("kill switch: logevent")) }, times(1))
        }
        Thread.sleep(100)   // event thường dựng trên IO: cho nó thời gian lộ ra nếu lỡ lọt qua
        assertEquals(listOf(false, false), results)
        assertEquals(0, LogEventServer.pendingEventsForTest())
        verifyNoInteractions(service)
    }

    @Test fun `event khong co onResult cung bi chan, dong log kill co lai sau reset`() {
        mockStatic(Log::class.java).use { log ->
            LogEventServer.logAdShowFailWithReason(placement = "full_home", reason = "x")

            log.verify({ Log.e(eq(LogUtil.TAG), contains("kill switch: logevent")) }, times(1))
        }
        Thread.sleep(100)
        assertEquals(0, LogEventServer.pendingEventsForTest())
        verifyNoInteractions(service)
    }
}
