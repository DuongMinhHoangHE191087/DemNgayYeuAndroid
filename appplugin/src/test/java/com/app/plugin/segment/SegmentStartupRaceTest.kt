package com.app.plugin.segment

import android.content.Context
import com.app.plugin.segment.SegmentTestSupport.newStore
import com.app.plugin.segment.SegmentTestSupport.prefsContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * 2.2.0 SEG fix 2 (R-SEG delta I7, m9, m10; probe của reviewer `RsegRaceTest`): app gọi `recordEvent` giữa lúc engine
 * đang nạp trên luồng nền không được ghi đè lịch sử đã lưu và không chạy trước event hook đã xếp hàng; nạp hỏng hay một
 * action phát lại ném `Error` không làm luồng nạp quay mãi hay kẹt ở STARTING. Singleton reset ở mỗi test
 * (`SegmentTestSupport.reset` → `freshSingleton`).
 */
class SegmentStartupRaceTest {
    @Before fun setUp() = SegmentTestSupport.reset()

    @After fun tearDown() = SegmentTestSupport.reset()

    @Test fun `I7 - recordEvent cua app giua luc nap giu lich su da luu va giu thu tu sau event hook`() {
        val store = newStore().apply { put("seg_ever_tapped_plan", 1) }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val order = Collections.synchronizedList(mutableListOf<String>())
        SegmentHooks.rcLong = { entered.countDown(); release.await(5, TimeUnit.SECONDS); null }
        SegmentStartup.executor = null // luồng nạp thật
        SegmentHooks.init(prefsContext(store))
        assertTrue("luồng nạp đã vào RC", entered.await(5, TimeUnit.SECONDS))

        SegmentStartup.runOrQueue { order += "hook" } // event hook xếp trước
        UserSegmentEngine.instance.recordEvent(SegmentEvent.FEATURE_USED, mapOf("featureId" to "x")) // app, giữa lúc nạp
        // Ghi trực tiếp giữa lúc nạp (không qua hàng đợi): engine chưa nhận ghi tới khi init xong → bỏ, không lưu mặc định.
        UserSegmentEngine.instance.recordEventNow(SegmentEvent.FEATURE_USED, mapOf("featureId" to "direct"), System.currentTimeMillis())
        SegmentStartup.runOrQueue { order += "after-app:${"x" in UserSegmentEngine.instance.scorerSnapshot().featuresUsed}" }
        release.countDown()
        val t0 = System.currentTimeMillis()
        while (!SegmentStartup.isLoaded && System.currentTimeMillis() - t0 < 5_000) Thread.sleep(10)

        assertTrue(SegmentStartup.isLoaded)
        val s = UserSegmentEngine.instance.scorerSnapshot()
        assertTrue("lịch sử đã lưu còn (store=${store["seg_ever_tapped_plan"]})", s.everTappedPlan)
        assertEquals(1, store["seg_ever_tapped_plan"])
        assertTrue("event của app còn", "x" in s.featuresUsed)
        assertFalse("ghi trực tiếp lúc chưa init xong bị bỏ", "direct" in s.featuresUsed)
        assertEquals("thứ tự: hook → app → sau", listOf("hook", "after-app:true"), order.toList())
    }

    @Test fun `m13 - event app xep hang giu thoi diem goi, SHOWN roi DISMISSED cach 100 ms (cua so 50 ms) khong thanh bounce sau phat lai`() {
        val tasks = mutableListOf<Runnable>()
        SegmentStartup.executor = Executor { tasks += it }
        SegmentTestSupport.rc(longs = mapOf("seg_bounce_window_millis" to 50L)) // cửa sổ bounce 50 ms qua seam RC
        SegmentHooks.init(prefsContext(newStore()))
        UserSegmentEngine.instance.recordEvent(SegmentEvent.PAYWALL_SHOWN)
        Thread.sleep(100) // > cửa sổ 50 ms; phát lại liền nhau (< 50 ms) sẽ thành bounce giả
        UserSegmentEngine.instance.recordEvent(SegmentEvent.PAYWALL_DISMISSED)

        tasks.single().run() // phát lại liền nhau

        val s = UserSegmentEngine.instance.scorerSnapshot()
        assertEquals("dismiss không chạm vẫn đếm", 1, s.dismissWithoutTapCount)
        assertEquals("không bounce giả do giờ phát lại", 0, s.bounceCount)
    }

    @Test fun `m15 - setRevenueTierUserProperty luc dang nap toi Firebase dung mot lan sau khi nap`() {
        val tasks = mutableListOf<Runnable>()
        SegmentStartup.executor = Executor { tasks += it }
        SegmentHooks.init(prefsContext(newStore()))
        UserSegmentEngine.instance.setRevenueTierUserProperty("STARVED")
        assertEquals(0, SegmentTestSupport.properties.count { it.first == "seg_revenue_tier" })

        tasks.single().run()

        assertEquals(listOf("seg_revenue_tier" to "STARVED"), SegmentTestSupport.properties.filter { it.first == "seg_revenue_tier" })
    }

    @Test fun `m9 - nap hong truoc khi engine init, event app xep hang khong lam luong nap quay mai`() {
        val tasks = mutableListOf<Runnable>()
        SegmentStartup.executor = Executor { tasks += it }
        val bad = mock(Context::class.java)
        `when`(bad.applicationContext).thenReturn(bad)
        `when`(bad.getSharedPreferences(anyString(), anyInt())).thenThrow(IllegalStateException("locked"))
        SegmentHooks.init(bad)
        UserSegmentEngine.instance.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED) // xếp hàng (đang nạp)
        val th = Thread { tasks.single().run() }.apply { isDaemon = true; start(); join(3_000) }
        assertFalse("luồng nạp còn quay sau 3 s", th.isAlive)
        assertTrue("không kẹt STARTING", SegmentStartup.isLoaded)
    }

    @Test fun `m10 - action phat lai nem Error, luong nap van mo cong va phat cac action con lai`() {
        val tasks = mutableListOf<Runnable>()
        SegmentStartup.executor = Executor { tasks += it }
        SegmentHooks.init(prefsContext(newStore()))
        val seen = mutableListOf<Int>()
        SegmentStartup.runOrQueue { seen += 1 }
        SegmentStartup.runOrQueue { throw AssertionError("boom") }
        SegmentStartup.runOrQueue { seen += 3 }

        tasks.single().run()

        assertTrue("không kẹt STARTING", SegmentStartup.isLoaded)
        assertEquals(listOf(1, 3), seen)
    }
}
