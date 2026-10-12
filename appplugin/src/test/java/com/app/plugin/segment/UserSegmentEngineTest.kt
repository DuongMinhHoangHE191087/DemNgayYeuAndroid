package com.app.plugin.segment

import com.app.plugin.segment.SegmentTestSupport.HOUR_MS
import com.app.plugin.segment.SegmentTestSupport.newEngine
import com.app.plugin.segment.SegmentTestSupport.newStore
import com.app.plugin.segment.SegmentTestSupport.prefsContext
import com.app.plugin.segment.SegmentTestSupport.rc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import java.util.Locale

/**
 * 2.2.0 SEG: engine port từ fork IM (0 test engine trong cả hai fork — plan-inputs F5). RC qua seam `SegmentHooks`,
 * prefs trong bộ nhớ; engine mới mỗi test (constructor private), không dùng singleton.
 */
class UserSegmentEngineTest {
    private val savedLocale = Locale.getDefault()

    @Before fun setUp() {
        SegmentTestSupport.reset()
        Locale.setDefault(Locale("vi", "VN")) // VN ngoài tier 1/2 mặc định: điểm không phụ thuộc locale của máy chạy test
    }

    @After fun tearDown() {
        Locale.setDefault(savedLocale)
        SegmentTestSupport.reset()
    }

    @Test fun `init doc RC - guardrail, nguong cadence, diem va danh sach nuoc tier 1 tu khoa seg_ cua fork`() {
        rc(
            longs = mapOf("seg_cadence_guardrail_seconds" to 60L, "seg_cadence_hip_tab_switch" to 12L, "seg_threshold_hip" to 80L),
            strings = mapOf("seg_geo_tier1_countries" to "vn, us"),
        )
        val store = newStore()
        val engine = newEngine()

        engine.init(prefsContext(store))

        assertEquals(60_000L, engine.cadenceConfig().globalGuardrailMillis)
        assertEquals(12, engine.cadenceConfig().hipTabSwitchThreshold)
        assertEquals("khoá không có trên RC giữ mặc định", 6, engine.cadenceConfig().pspTabSwitchThreshold)
        assertEquals("VN nằm trong danh sách tier 1 từ RC", 1, engine.geoTier)
        assertEquals(1, store["seg_geo_tier"])

        rc() // không RC: mặc định SDK, guardrail tắt (D10-220)
        val plain = newEngine().also { it.init(prefsContext(newStore())) }
        assertEquals(0L, plain.cadenceConfig().globalGuardrailMillis)
        assertEquals("VN không thuộc tier 1/2 mặc định", 4, plain.geoTier)
    }

    @Test fun `ScorerState luu xuong prefs va khoi phuc o init cua engine moi (qua kill)`() {
        val store = newStore()
        val first = newEngine().also { it.init(prefsContext(store)) }
        first.recordEvent(SegmentEvent.PAYWALL_SHOWN)
        first.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED)
        first.recordEvent(SegmentEvent.FEATURE_USED, mapOf("featureId" to "scan"))
        first.recordEvent(SegmentEvent.REWARDED_AD_COMPLETED)
        first.recordEvent(SegmentEvent.SESSION_START)
        val saved = first.scorerSnapshot()

        val second = newEngine().also { it.init(prefsContext(store)) }

        assertEquals(saved, second.scorerSnapshot())
        assertEquals(setOf("scan"), second.scorerSnapshot().featuresUsed)
        assertEquals("mốc tín hiệu doanh thu sống qua kill", first.lastSegmentEventMillis, second.lastSegmentEventMillis)
        assertNotEquals(0L, second.lastSegmentEventMillis)
    }

    @Test fun `forceSegment ghi de phan loai, null tra ve phan loai tinh`() {
        val engine = newEngine().also { it.init(prefsContext()) }
        assertEquals("cài mới: trong 48 h", UserSegment.COLD_START, engine.segment.value)

        engine.forceSegment(UserSegment.POWER_USER)
        assertEquals(UserSegment.POWER_USER, engine.segment.value)

        engine.forceSegment(null)
        assertEquals(UserSegment.COLD_START, engine.segment.value)
    }

    @Test fun `StateFlow phat khi diem va segment doi, khong phat khi khong doi`() {
        val store = newStore().apply { put("seg_first_seen_millis", System.currentTimeMillis() - 72 * HOUR_MS) }
        val engine = newEngine().also { it.init(prefsContext(store)) }
        val scores = mutableListOf<Int>()
        val segments = mutableListOf<UserSegment>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { engine.score.collect { scores += it } }
        scope.launch { engine.segment.collect { segments += it } }
        try {
            engine.recordEvent(SegmentEvent.PAYWALL_SHOWN) // không đổi điểm
            engine.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED) // +20
            repeat(3) { engine.recordEvent(SegmentEvent.FEATURE_USED, mapOf("featureId" to "f$it")) } // +5 ×3

            assertEquals(listOf(0, 20, 25, 30, 35), scores)
            assertEquals("ngoài 48 h, điểm < 40: LIA suốt", listOf(UserSegment.LIA), segments)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `COLD_START trong 48 h dau tien, het 48 h thi theo diem`() {
        val now = System.currentTimeMillis()
        val young = newEngine().also {
            it.init(prefsContext(newStore().apply { put("seg_first_seen_millis", now - 47 * HOUR_MS) }))
        }
        val old = newEngine().also {
            it.init(prefsContext(newStore().apply { put("seg_first_seen_millis", now - 49 * HOUR_MS) }))
        }
        val fresh = newStore()
        newEngine().init(prefsContext(fresh))

        assertEquals(UserSegment.COLD_START, young.segment.value)
        assertEquals(UserSegment.LIA, old.segment.value)
        val firstSeen = fresh["seg_first_seen_millis"] as Long
        assertEquals("lần đầu: ghi mốc cài đặt", true, firstSeen in now..System.currentTimeMillis())
    }

    /** 2.4.2 SPLASH (quyết định iv): resource app là mặc định cục bộ; RC thắng; không có thì tắt. */
    private fun contextWithGuardrailResource(seconds: Int?): android.content.Context {
        val ctx = prefsContext(newStore())
        `when`(ctx.packageName).thenReturn("app.test")
        if (seconds != null) {
            `when`(ctx.resources.getIdentifier("ads_cf_seg_cadence_guardrail_seconds", "integer", "app.test")).thenReturn(77)
            `when`(ctx.resources.getInteger(77)).thenReturn(seconds)
        }
        return ctx
    }

    private fun guardrailMillisFor(ctx: android.content.Context): Long {
        SegmentHooks.init(ctx) // nạp context cho hook đọc resource (executor đồng bộ của test)
        return newEngine().also { it.init(ctx) }.cadenceConfig().globalGuardrailMillis
    }

    @Test fun `SP3 resource ads_cf_seg_cadence_guardrail_seconds 60, RC vang - guardrail 60 s`() {
        rc()
        assertEquals(60_000L, guardrailMillisFor(contextWithGuardrailResource(60)))
    }

    @Test fun `SP3 RC 0 thang resource - guardrail 0`() {
        rc(longs = mapOf("seg_cadence_guardrail_seconds" to 0L))
        assertEquals(0L, guardrailMillisFor(contextWithGuardrailResource(60)))
    }

    @Test fun `SP3 RC 90 thang resource 60`() {
        rc(longs = mapOf("seg_cadence_guardrail_seconds" to 90L))
        assertEquals(90_000L, guardrailMillisFor(contextWithGuardrailResource(60)))
    }

    @Test fun `SP3 khong resource, khong RC - guardrail 0`() {
        rc()
        assertEquals(0L, guardrailMillisFor(contextWithGuardrailResource(null)))
    }
}
