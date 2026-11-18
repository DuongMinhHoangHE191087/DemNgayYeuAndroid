package com.example.shared.monetization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonetizationPolicyTest {

    @Test
    fun proTierCatalog_matchesExactPlayAndStoreKitSkus_andQuotas() {
        assertEquals("vip_monthly", ProTierCatalog.MONTHLY)
        assertEquals("vip_yearly", ProTierCatalog.YEARLY)
        assertEquals("vip_lifetime", ProTierCatalog.LIFETIME)
        assertEquals(setOf("vip_monthly", "vip_yearly", "vip_lifetime"), ProTierCatalog.ALL_SKUS)
        assertEquals(2, ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH)
        assertEquals(5, ProTierCatalog.FREE_SECRET_NOTES_LIMIT)
        assertTrue(ProTierCatalog.isVipSku("vip_yearly"))
        assertFalse(ProTierCatalog.isVipSku("free_tier"))
    }

    @Test
    fun adPlacementPolicy_blocksVipNoConsentCelebrationDayAndAllSacredZones() {
        val readyState = AdSessionState(
            isVip = false,
            hasConsent = true,
            activeSacredZones = emptySet(),
            sessionStartedAtMs = 0L,
            lastInterstitialShownAtMs = 0L,
            interstitialsShownThisSession = 0,
            isCelebrationDay = false,
        )
        assertTrue(AdPlacementPolicy.canShowBanner(readyState, 120_000L))
        assertTrue(AdPlacementPolicy.canShowInterstitial(readyState, 120_000L))

        val vipDecision = AdPlacementPolicy.evaluate(AdPlacementType.BANNER, readyState.copy(isVip = true), 120_000L)
        assertFalse(vipDecision.allowed)
        assertEquals(AdBlockReason.VIP_USER, vipDecision.blockReason)

        val noConsentDecision = AdPlacementPolicy.evaluate(AdPlacementType.BANNER, readyState.copy(hasConsent = false), 120_000L)
        assertFalse(noConsentDecision.allowed)
        assertEquals(AdBlockReason.NO_CONSENT, noConsentDecision.blockReason)

        for (zone in SacredZone.entries) {
            val zDecision = AdPlacementPolicy.evaluate(
                AdPlacementType.INTERSTITIAL,
                readyState.copy(activeSacredZones = setOf(zone)),
                120_000L,
            )
            assertFalse(zDecision.allowed, "SacredZone $zone must block ads")
            assertEquals(AdBlockReason.SACRED_ZONE, zDecision.blockReason)
        }

        val celebrationDecision = AdPlacementPolicy.evaluate(
            AdPlacementType.BANNER,
            readyState.copy(isCelebrationDay = true),
            120_000L,
        )
        assertFalse(celebrationDecision.allowed)
        assertEquals(AdBlockReason.SACRED_ZONE, celebrationDecision.blockReason)
    }

    @Test
    fun adPlacementPolicy_enforcesWarmupCooldownAndSessionCap() {
        var state = AdSessionState(
            isVip = false,
            hasConsent = true,
            sessionStartedAtMs = 1_000L,
        )

        val duringWarmup = AdPlacementPolicy.evaluate(AdPlacementType.INTERSTITIAL, state, nowMs = 60_999L)
        assertFalse(duringWarmup.allowed)
        assertEquals(AdBlockReason.WARMUP_ACTIVE, duringWarmup.blockReason)

        assertTrue(AdPlacementPolicy.canShowInterstitial(state, nowMs = 61_000L))

        state = AdPlacementPolicy.recordInterstitialShown(state, shownAtMs = 61_000L)
        val duringCooldown = AdPlacementPolicy.evaluate(AdPlacementType.INTERSTITIAL, state, nowMs = 180_999L)
        assertFalse(duringCooldown.allowed)
        assertEquals(AdBlockReason.COOLDOWN_ACTIVE, duringCooldown.blockReason)

        assertTrue(AdPlacementPolicy.canShowInterstitial(state, nowMs = 181_000L))
        state = AdPlacementPolicy.recordInterstitialShown(state, shownAtMs = 181_000L)
        state = AdPlacementPolicy.recordInterstitialShown(state, shownAtMs = 310_000L)

        val capped = AdPlacementPolicy.evaluate(AdPlacementType.INTERSTITIAL, state, nowMs = 500_000L)
        assertFalse(capped.allowed)
        assertEquals(AdBlockReason.SESSION_CAP_REACHED, capped.blockReason)
    }

    @Test
    fun proFeatureGating_andContextualSoftPaywallPrompts() {
        assertTrue(ProFeature.AI_GIFT_CONCIERGE_UNLIMITED.isUnlocked(isVip = false, currentUsageCount = 1))
        assertFalse(ProFeature.AI_GIFT_CONCIERGE_UNLIMITED.isUnlocked(isVip = false, currentUsageCount = 2))
        assertTrue(ProFeature.DEEP_PARTNER_DOSSIER_UNLIMITED.isUnlocked(isVip = false, currentUsageCount = 4))
        assertFalse(ProFeature.DEEP_PARTNER_DOSSIER_UNLIMITED.isUnlocked(isVip = false, currentUsageCount = 5))
        assertFalse(ProFeature.EARLY_14D_30D_REMINDERS.isUnlocked(isVip = false))
        assertTrue(ProFeature.EARLY_14D_30D_REMINDERS.isUnlocked(isVip = true))

        assertNull(AdPlacementPolicy.softPaywallUpsell(UpsellTrigger.AI_QUOTA_REACHED, isVip = true, currentCount = 10))
        assertNull(AdPlacementPolicy.softPaywallUpsell(UpsellTrigger.AI_QUOTA_REACHED, isVip = false, currentCount = 1))
        val prompt = AdPlacementPolicy.softPaywallUpsell(UpsellTrigger.AI_QUOTA_REACHED, isVip = false, currentCount = 2)
        assertNotNull(prompt)
        assertEquals(ProTierCatalog.YEARLY, prompt.recommendedSku)
        assertEquals(ProFeature.AI_GIFT_CONCIERGE_UNLIMITED, prompt.feature)

        val aiDesc = ProFeature.AI_GIFT_CONCIERGE_UNLIMITED.descriptionVi.lowercase()
        assertFalse(aiDesc.contains("không giới hạn"), "Must not claim unlimited alongside 60/month cap")
        for (trigger in UpsellTrigger.entries) {
            val item = AdPlacementPolicy.softPaywallUpsell(trigger, isVip = false, currentCount = 99)
            assertNotNull(item)
            assertFalse(item.titleVi.contains('!'))
            assertFalse(item.titleEn.contains('!'))
            assertFalse(item.ctaVi.contains('!'))
            assertFalse(item.ctaEn.contains('!'))
        }
    }

    @Test
    fun proTierCatalog_containsReferencePricingAndTaglines_preservingExactSkus() {
        val monthly = ProTierCatalog.planFor(ProTierCatalog.MONTHLY)
        val yearly = ProTierCatalog.planFor(ProTierCatalog.YEARLY)
        val lifetime = ProTierCatalog.planFor(ProTierCatalog.LIFETIME)

        assertNotNull(monthly)
        assertNotNull(yearly)
        assertNotNull(lifetime)

        assertEquals(39_000L, monthly.referencePriceVnd)
        assertEquals("2.99", monthly.referencePriceUsd)
        assertEquals(0, monthly.savingsPercent)
        assertTrue(monthly.taglineVi.isNotBlank() && monthly.taglineEn.isNotBlank())

        assertEquals(199_000L, yearly.referencePriceVnd)
        assertEquals("17.99", yearly.referencePriceUsd)
        assertEquals(57, yearly.savingsPercent)
        assertEquals(3, yearly.freeTrialDays)
        assertTrue(yearly.isRecommended)
        assertTrue(yearly.taglineVi.contains("57%") && yearly.taglineEn.contains("57%"))

        assertEquals(399_000L, lifetime.referencePriceVnd)
        assertEquals("39.99", lifetime.referencePriceUsd)
        assertEquals(0, lifetime.savingsPercent)
        assertTrue(lifetime.taglineVi.isNotBlank() && lifetime.taglineEn.isNotBlank())
    }
}
