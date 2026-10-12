package com.app.plugin.segment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdCadenceGateTest {

    private val testConfig = SegmentCadenceConfig(
        hipTabSwitchThreshold = 10,
        pspTabSwitchThreshold = 6,
        liaTabSwitchThreshold = 4
    )

    @Test
    fun coldStartTier1ResolvesToPsp() {
        assertEquals(UserSegment.PSP, AdCadenceGate.effectiveCadenceSegment(UserSegment.COLD_START, 1))
    }

    @Test
    fun coldStartTier2ResolvesToLia() {
        assertEquals(UserSegment.LIA, AdCadenceGate.effectiveCadenceSegment(UserSegment.COLD_START, 2))
    }

    @Test
    fun coldStartTier4ResolvesToLia() {
        assertEquals(UserSegment.LIA, AdCadenceGate.effectiveCadenceSegment(UserSegment.COLD_START, 4))
    }

    @Test
    fun nonColdStartSegmentsPassThroughRegardlessOfGeoTier() {
        assertEquals(UserSegment.HIP, AdCadenceGate.effectiveCadenceSegment(UserSegment.HIP, 4))
        assertEquals(UserSegment.PSP, AdCadenceGate.effectiveCadenceSegment(UserSegment.PSP, 2))
        assertEquals(UserSegment.LIA, AdCadenceGate.effectiveCadenceSegment(UserSegment.LIA, 1))
    }

    @Test
    fun hipDoesNotShowBelowThreshold() {
        assertFalse(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.HIP,
                geoTier = 4,
                tabSwitchCount = 9,
                config = testConfig
            )
        )
    }

    @Test
    fun hipShowsAtThreshold() {
        assertTrue(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.HIP,
                geoTier = 4,
                tabSwitchCount = 10,
                config = testConfig
            )
        )
    }

    @Test
    fun hipShowsAboveThreshold() {
        assertTrue(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.HIP,
                geoTier = 4,
                tabSwitchCount = 15,
                config = testConfig
            )
        )
    }

    @Test
    fun pspDoesNotShowBelowThreshold() {
        assertFalse(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.PSP,
                geoTier = 4,
                tabSwitchCount = 5,
                config = testConfig
            )
        )
    }

    @Test
    fun pspShowsAtThreshold() {
        assertTrue(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.PSP,
                geoTier = 4,
                tabSwitchCount = 6,
                config = testConfig
            )
        )
    }

    @Test
    fun pspShowsAboveThreshold() {
        assertTrue(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.PSP,
                geoTier = 4,
                tabSwitchCount = 8,
                config = testConfig
            )
        )
    }

    @Test
    fun liaDoesNotShowBelowThreshold() {
        assertFalse(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.LIA,
                geoTier = 4,
                tabSwitchCount = 3,
                config = testConfig
            )
        )
    }

    @Test
    fun liaShowsAtThreshold() {
        assertTrue(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.LIA,
                geoTier = 4,
                tabSwitchCount = 4,
                config = testConfig
            )
        )
    }

    @Test
    fun coldStartTier1UsesPspThreshold() {
        assertFalse(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.COLD_START,
                geoTier = 1,
                tabSwitchCount = 5,
                config = testConfig
            )
        )
        assertTrue(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.COLD_START,
                geoTier = 1,
                tabSwitchCount = 6,
                config = testConfig
            )
        )
    }

    @Test
    fun coldStartNonTier1UsesLiaThreshold() {
        assertFalse(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.COLD_START,
                geoTier = 4,
                tabSwitchCount = 3,
                config = testConfig
            )
        )
        assertTrue(
            AdCadenceGate.shouldShowInterstitial(
                segment = UserSegment.COLD_START,
                geoTier = 4,
                tabSwitchCount = 4,
                config = testConfig
            )
        )
    }

    @Test
    fun defaultsAreUpperEndOfPolicySafeRange() {
        // Locks in HIP=10, PSP=6, LIA=4 from policy-tightening pass.
        // Lower values risk AdMob "Disruptive Ads / presenting too many ads" flags.
        val defaults = SegmentCadenceConfig.DEFAULT
        assertEquals(10, defaults.hipTabSwitchThreshold)
        assertEquals(6, defaults.pspTabSwitchThreshold)
        assertEquals(4, defaults.liaTabSwitchThreshold)
    }

    // --------------------------------------------------------------------
    // POWER_USER + CHURN_RISK cadence
    // --------------------------------------------------------------------

    @Test
    fun powerUserGetsTheGentlestCadence() {
        // POWER_USER must be at least as patient as HIP — the user's LTV is
        // already proven, no point churning them with ad pressure.
        val defaults = SegmentCadenceConfig.DEFAULT
        assertTrue(
            "powerUser threshold (${defaults.powerUserTabSwitchThreshold}) must be >= hip (${defaults.hipTabSwitchThreshold})",
            defaults.powerUserTabSwitchThreshold >= defaults.hipTabSwitchThreshold,
        )
    }

    @Test
    fun powerUserDoesNotShowBelowThreshold() {
        val testConfig = SegmentCadenceConfig(powerUserTabSwitchThreshold = 14)
        assertFalse(
            AdCadenceGate.shouldShowInterstitial(
                segment        = UserSegment.POWER_USER,
                geoTier        = 4,
                tabSwitchCount = 13,
                config         = testConfig,
            )
        )
    }

    @Test
    fun powerUserShowsAtThreshold() {
        val testConfig = SegmentCadenceConfig(powerUserTabSwitchThreshold = 14)
        assertTrue(
            AdCadenceGate.shouldShowInterstitial(
                segment        = UserSegment.POWER_USER,
                geoTier        = 4,
                tabSwitchCount = 14,
                config         = testConfig,
            )
        )
    }

    @Test
    fun churnRiskUsesLiaLikeCadence() {
        // CHURN_RISK shouldn't be coddled — they were about to bounce anyway,
        // an interstitial does less damage there than to a power user.
        val testConfig = SegmentCadenceConfig(churnRiskTabSwitchThreshold = 4)
        assertFalse(
            AdCadenceGate.shouldShowInterstitial(
                segment        = UserSegment.CHURN_RISK,
                geoTier        = 4,
                tabSwitchCount = 3,
                config         = testConfig,
            )
        )
        assertTrue(
            AdCadenceGate.shouldShowInterstitial(
                segment        = UserSegment.CHURN_RISK,
                geoTier        = 4,
                tabSwitchCount = 4,
                config         = testConfig,
            )
        )
    }

    @Test
    fun nonColdStartNewSegmentsPassThroughGeoTier() {
        // The geo-tier bootstrap is COLD_START-only; new segments must not
        // be silently rewritten by effectiveCadenceSegment().
        assertEquals(UserSegment.POWER_USER, AdCadenceGate.effectiveCadenceSegment(UserSegment.POWER_USER, 1))
        assertEquals(UserSegment.CHURN_RISK, AdCadenceGate.effectiveCadenceSegment(UserSegment.CHURN_RISK, 1))
    }
}
