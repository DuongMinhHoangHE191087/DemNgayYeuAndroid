package com.app.plugin.segment

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTierResolverTest {

    private val tier1 = setOf("US", "GB")
    private val tier2 = setOf("ES", "IT")

    @Test
    fun countryInTier1ListResolvesToOne() {
        assertEquals(1, GeoTierResolver.resolveTier("US", tier1, tier2))
    }

    @Test
    fun countryInTier2ListResolvesToTwo() {
        assertEquals(2, GeoTierResolver.resolveTier("IT", tier1, tier2))
    }

    @Test
    fun countryInNeitherListResolvesToFour() {
        assertEquals(4, GeoTierResolver.resolveTier("VN", tier1, tier2))
    }

    @Test
    fun resolutionIsCaseInsensitive() {
        assertEquals(1, GeoTierResolver.resolveTier("us", tier1, tier2))
    }

    @Test
    fun defaultTier1ContainsUnitedStates() {
        assertEquals(1, GeoTierResolver.resolveTier("US", GeoTierResolver.DEFAULT_TIER1, GeoTierResolver.DEFAULT_TIER2))
    }

    @Test
    fun defaultTier1ContainsRepresentativeWesternEuropeanMarkets() {
        // Spot-check countries that AdMob consistently classifies as top eCPM —
        // catches accidental removal during future tuning passes.
        listOf("GB", "DE", "FR", "NL", "CH", "IE").forEach { country ->
            assertEquals(
                "expected $country in DEFAULT_TIER1",
                1,
                GeoTierResolver.resolveTier(country, GeoTierResolver.DEFAULT_TIER1, GeoTierResolver.DEFAULT_TIER2)
            )
        }
    }

    @Test
    fun defaultTier1ContainsAsiaPacificDeveloped() {
        listOf("JP", "KR", "AU", "NZ", "SG", "TW", "HK").forEach { country ->
            assertEquals(
                "expected $country in DEFAULT_TIER1",
                1,
                GeoTierResolver.resolveTier(country, GeoTierResolver.DEFAULT_TIER1, GeoTierResolver.DEFAULT_TIER2)
            )
        }
    }

    @Test
    fun defaultTier2ContainsRepresentativeMidTierMarkets() {
        listOf("ES", "IT", "PT", "PL", "CZ", "IL", "AE", "SA").forEach { country ->
            assertEquals(
                "expected $country in DEFAULT_TIER2",
                2,
                GeoTierResolver.resolveTier(country, GeoTierResolver.DEFAULT_TIER1, GeoTierResolver.DEFAULT_TIER2)
            )
        }
    }

    @Test
    fun defaultTiersDoNotOverlap() {
        val overlap = GeoTierResolver.DEFAULT_TIER1.intersect(GeoTierResolver.DEFAULT_TIER2)
        assertEquals("Tier 1 and Tier 2 default lists overlap: $overlap", emptySet<String>(), overlap)
    }
}
