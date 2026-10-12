package com.app.plugin.segment

/**
 * Geo tier resolver for the propensity-to-pay score model. Tiers correspond to
 * typical AdMob eCPM bands and broadly overlap with World Bank "high income"
 * classifications:
 *
 *   Tier 1 — top eCPM markets (mature economies, high mobile-app spend).
 *   Tier 2 — mid eCPM (developed markets a notch below Tier 1).
 *   Tier 3/4 — everything else; resolved as `4` since the score model only
 *              differentiates Tier 1 (+40) / Tier 2 (+20) / other (0).
 *
 * Both lists are Remote-Config overridable per app via the `seg_geo_tier1_countries`
 * / `seg_geo_tier2_countries` keys consumed in `UserSegmentEngine.kt:159-166`,
 * so apps that want a different default can ship overrides without code changes.
 */
internal object GeoTierResolver {
    val DEFAULT_TIER1 = setOf(
        // North America
        "US", "CA",
        // Western & Northern Europe
        "GB", "IE", "DE", "FR", "NL", "BE", "AT", "LU", "CH",
        "SE", "NO", "DK", "FI", "IS",
        // APAC developed
        "AU", "NZ", "JP", "KR", "SG", "TW", "HK"
    )
    val DEFAULT_TIER2 = setOf(
        // Southern & Eastern Europe
        "ES", "IT", "PT", "GR", "CY", "MT",
        "PL", "CZ", "SK", "HU", "SI", "EE", "LT", "LV", "HR",
        // Middle East developed
        "IL", "AE", "SA", "QA", "KW", "BH", "OM",
        // Latin America's wealthier markets
        "CL", "UY", "CR",
        // Other developed APAC
        "MO"
    )

    fun resolveTier(countryCode: String, tier1: Set<String>, tier2: Set<String>): Int {
        val normalized = countryCode.uppercase()
        return when {
            normalized in tier1 -> 1
            normalized in tier2 -> 2
            else -> 4
        }
    }
}

