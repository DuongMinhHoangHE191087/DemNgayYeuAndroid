package com.app.plugin.segment

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentEnumsTest {

    @Test
    fun segmentEventHasExactlyTheNineSpecEvents() {
        assertEquals(
            listOf(
                "SESSION_START",
                "FEATURE_USED",
                "PAYWALL_SHOWN",
                "PAYWALL_DISMISSED",
                "PAYWALL_PLAN_TAPPED",
                "REWARDED_AD_COMPLETED",
                "AD_IMPRESSION",
                "PURCHASE_COMPLETED",
                "NON_PAYER_DISMISS"
            ),
            SegmentEvent.values().map { it.name }
        )
    }

    @Test
    fun userSegmentHasExactlyTheSixSpecSegments() {
        // Order matters — host apps may persist segment by ordinal in older
        // versions; appending new values at the end preserves backward
        // compatibility with stored values.
        assertEquals(
            listOf("HIP", "PSP", "LIA", "COLD_START", "POWER_USER", "CHURN_RISK"),
            UserSegment.values().map { it.name }
        )
    }

    @Test
    fun ecpmBucketHasExactlyHighAndLow() {
        assertEquals(
            listOf("HIGH", "LOW"),
            EcpmBucket.values().map { it.name }
        )
    }
}
