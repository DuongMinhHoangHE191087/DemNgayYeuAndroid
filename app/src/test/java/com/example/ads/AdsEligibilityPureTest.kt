package com.example.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Quy tac hien quang cao: khong VIP, co consent, co ad unit, khong bi kill switch.
class AdsEligibilityPureTest {

    private val unit = "ca-app-pub-test/1"

    @Test
    fun vip_notEligible() {
        assertFalse(isAdsEligible(isVip = true, canRequestAds = true, adUnitId = unit, killed = false))
    }

    @Test
    fun cannotRequestAds_notEligible() {
        assertFalse(isAdsEligible(isVip = false, canRequestAds = false, adUnitId = unit, killed = false))
    }

    @Test
    fun blankAdUnit_notEligible() {
        assertFalse(isAdsEligible(isVip = false, canRequestAds = true, adUnitId = "", killed = false))
        assertFalse(isAdsEligible(isVip = false, canRequestAds = true, adUnitId = "   ", killed = false))
    }

    @Test
    fun killed_notEligible() {
        assertFalse(isAdsEligible(isVip = false, canRequestAds = true, adUnitId = unit, killed = true))
    }

    @Test
    fun freeConsentedWithUnit_eligible() {
        assertTrue(isAdsEligible(isVip = false, canRequestAds = true, adUnitId = unit, killed = false))
    }

    @Test
    fun allFalseCombos_matchTruthTable() {
        for (bits in 0 until 16) {
            val isVip = (bits and 1) != 0
            val canRequestAds = (bits and 2) != 0
            val hasUnit = (bits and 4) != 0
            val killed = (bits and 8) != 0
            val adUnitId = if (hasUnit) unit else ""
            val expected = !isVip && canRequestAds && hasUnit && !killed
            assertEquals(expected, isAdsEligible(isVip, canRequestAds, adUnitId, killed), "bits=$bits")
        }
    }
}
