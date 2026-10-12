package com.app.plugin.segment

import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.segment.SegmentTestSupport.HOUR_MS
import com.app.plugin.segment.SegmentTestSupport.newEngine
import com.app.plugin.segment.SegmentTestSupport.newStore
import com.app.plugin.segment.SegmentTestSupport.prefsContext
import com.app.plugin.segment.SegmentTestSupport.properties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2.2.0 SEG H3 (R-P1, D18-220): user property `seg_*` của engine đi qua `SegmentHooks.setUserProperty`; tầng DENIED
 * và UNKNOWN (không bao giờ là đồng ý) → 0 lời gọi ghi, trong khi prefs cục bộ vẫn ghi. GRANTED → ghi (chứng minh seam
 * đếm được).
 */
class SegmentConsentTest {
    @After fun tearDown() = SegmentTestSupport.reset()

    @Test fun `H3 - DENIED va UNKNOWN khong ghi user property seg_, prefs van ghi, GRANTED ghi ca ba`() {
        for (tier in listOf(ConsentTier.DENIED, ConsentTier.UNKNOWN, ConsentTier.GRANTED)) {
            SegmentTestSupport.reset(tier)
            val store = newStore().apply { put("seg_first_seen_millis", System.currentTimeMillis() - 72 * HOUR_MS) }
            val engine = newEngine().also { it.init(prefsContext(store)) } // COLD_START → LIA: segment đổi ngay ở init

            engine.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED)
            engine.setRevenueTierUserProperty("STARVED")

            assertEquals("$tier: prefs cục bộ vẫn ghi", 1, store["seg_ever_tapped_plan"])
            val names = properties.map { it.first }.toSet()
            if (tier == ConsentTier.GRANTED) {
                assertEquals(setOf("seg_segment", "seg_score", "seg_revenue_tier"), names)
            } else {
                assertEquals("$tier: không gì rời máy", emptyList<Pair<String, String>>(), properties.toList())
            }
        }
    }

    @Test fun `I3 - giu luc UNKNOWN, GRANTED phat lai mot lan segment diem tang doanh thu, DENIED va PARTIAL khong`() {
        SegmentTestSupport.reset(ConsentTier.UNKNOWN)
        val store = newStore().apply { put("seg_first_seen_millis", System.currentTimeMillis() - 72 * HOUR_MS) }
        SegmentHooks.init(prefsContext(store)) // COLD_START → LIA lúc nạp: bị giữ (UNKNOWN)
        UserSegmentEngine.instance.setRevenueTierUserProperty("STARVED")
        assertEquals(emptyList<Pair<String, String>>(), properties.toList())

        ConsentState.setTier(ConsentTier.GRANTED)
        assertEquals(listOf("seg_segment" to "LIA", "seg_score" to "0", "seg_revenue_tier" to "STARVED"), properties.toList())

        properties.clear()
        ConsentState.setTier(ConsentTier.DENIED)
        ConsentState.setTier(ConsentTier.PARTIAL)
        assertEquals("DENIED/PARTIAL: không phát lại", emptyList<Pair<String, String>>(), properties.toList())
    }
}
