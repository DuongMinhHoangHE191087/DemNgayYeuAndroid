package com.app.plugin.ads.core

import android.content.Context
import android.content.res.Resources
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.MainHandlerTimers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** 2.4.4 INFLIGHT: [UnitGate] ở mức object (công tắc đọc một lần, vỏ callback, hàng FIFO bỏ người chờ chết, timeout). */
class UnitGateTest {
    private val unit = "ca-app-pub-3940256099942544/u"

    @After fun tearDown() = UnitGate.resetForTest()

    private fun ctx(value: Int): Context {
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(7)
        `when`(res.getInteger(7)).thenReturn(value)
        return mock(Context::class.java).also { `when`(it.resources).thenReturn(res); `when`(it.packageName).thenReturn("p") }
    }

    private class Cb : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    @Test fun `cong tac doc mot lan moi process, reset doc lai, loi doc thi cong bat`() {
        assertEquals(true, UnitGate.isOff(ctx(1)))
        assertEquals("đọc một lần: giá trị đầu giữ", true, UnitGate.isOff(ctx(0)))
        UnitGate.resetForTest()
        assertEquals(false, UnitGate.isOff(ctx(0)))
        UnitGate.resetForTest()
        assertEquals("Context không có resources ⇒ cổng bật (không ném)", false, UnitGate.isOff(mock(Context::class.java)))
    }

    @Test fun `vo callback nha dat cho mot lan, giu cb goc de khu trung`() = MainHandlerTimers.run { timers ->
        val cb = Cb()
        val wrapped = UnitGate.hold("slotA", "full_splash", "full_splash", listOf(unit), 0, { true }, cb)
        assertSame(cb, UnitGate.original(wrapped))
        assertSame("cb không bọc trả chính nó", cb, UnitGate.original(cb))
        val runs = mutableListOf<Boolean>()
        assertEquals(UnitGate.PARKED, UnitGate.admit("slotB", "full_default", "full_default", listOf(unit), { true }) { runs += it })
        wrapped.onLoadFailed("full_splash", "full_splash", "", "x")
        wrapped.onLoadFailed("full_splash", "full_splash", "", "y")
        assertEquals(listOf("fail:x", "fail:y"), cb.events)
        timers.runPosted()
        assertEquals("người chờ chạy lại đúng một lần", listOf(false), runs)
    }

    @Test fun `hang FIFO bo nguoi cho da chet va xet nguoi ke`() = MainHandlerTimers.run { timers ->
        val wrapped = UnitGate.hold("slotA", "full_splash", "full_splash", listOf(unit), 0, { true }, null)
        val runs = mutableListOf<String>()
        UnitGate.admit("slotB", "full_default", "full_default", listOf(unit), { false }) { runs += "B" }
        UnitGate.admit("slotC", "full_home", "full_home", listOf(unit), { true }) { runs += "C" }
        wrapped.onLoaded("full_splash", "full_splash", unit)
        timers.runPosted()
        assertEquals(listOf("C"), runs)
    }

    @Test fun `watchdog dong request qua telemetry khong tinh backoff, lan sau cung slot xin lai ngay`() = MainHandlerTimers.run { _ ->
        val tel = AdsTelemetry(mock(AdsHelper::class.java))
        val cb = UnitGate.hold("slotA", "full_splash", "full_splash", listOf(unit), 0, { true }, null)
        tel.onAdLoadStart("full_splash", "full_splash", "interstitial", "admob", unit)
        tel.onLoadTimeout("full_splash", listOf(unit))
        cb.onLoadFailed("full_splash", "full_splash", "", "load timeout")
        assertEquals("timeout không phải lỗi load thật ⇒ không backoff", 0, UnitGate.admit("slotA", "full_splash", "full_splash", listOf(unit), { true }) { })
    }
}
