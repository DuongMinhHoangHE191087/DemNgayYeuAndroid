package com.app.plugin.segment

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentScoringConfigTest {

    @Test
    fun defaultMatchesSpecWeightTable() {
        val config = SegmentScoringConfig.DEFAULT

        assertEquals(40, config.geoTier1Weight)
        assertEquals(20, config.geoTier2Weight)
        assertEquals(20, config.planTappedWeight)
        assertEquals(-10, config.bounceWeight)
        assertEquals(2000L, config.bounceWindowMillis)
        assertEquals(-10, config.dismissNoTapWeight)
        assertEquals(-30, config.dismissNoTapCap)
        assertEquals(5, config.featureUsedWeight)
        assertEquals(25, config.featureUsedCap)
        assertEquals(10, config.sessions3to6Weight)
        assertEquals(15, config.sessions7PlusWeight)
        assertEquals(5, config.rewardedAdWeight)
        assertEquals(15, config.rewardedAdCap)
        assertEquals(10, config.ecpmHighWeight)
        assertEquals(-10, config.ecpmLowWeight)
        assertEquals(5, config.ecpmMinImpressions)
        assertEquals(70, config.hipThreshold)
        assertEquals(40, config.pspThreshold)
        assertEquals(48L * 60 * 60 * 1000, config.coldStartWindowMillis)
    }
}
