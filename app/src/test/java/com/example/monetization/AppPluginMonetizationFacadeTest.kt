package com.example.monetization

import android.app.Activity
import android.content.Context
import com.example.shared.monetization.AdBlockReason
import com.example.shared.monetization.AdPlacementPolicy
import com.example.shared.monetization.AdPlacementType
import com.example.shared.monetization.ProFeature
import com.example.shared.monetization.ProTierCatalog
import com.example.shared.monetization.SacredZone
import com.example.shared.monetization.UpsellTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPluginMonetizationFacadeTest {

    private class FakeBridge : AppPluginBridge {
        var lastRemoveAdsVip: Boolean? = null
        var adsSdkDisabled: Boolean = false
        var bannerHiddenByReward: Boolean = false
        var grantedRewardMs: Long? = null
        var suppressedAoaMs: Long? = null
        var screenChanges: Int = 0
        var consentToReturn: Boolean = true

        override fun configureSdk(context: Context, playerId: String?) = Unit
        override fun syncRemoveAds(isVip: Boolean) { lastRemoveAdsVip = isVip }
        override fun isAdsSdkDisabled(): Boolean = adsSdkDisabled
        override fun isBannerHiddenByReward(): Boolean = bannerHiddenByReward
        override fun grantBannerHideReward(durationMillis: Long) { grantedRewardMs = durationMillis }
        override fun suppressNextOpenAd(durationMillis: Long) { suppressedAoaMs = durationMillis }
        override fun recordScreenChange() { screenChanges++ }
        override fun requestConsent(activity: Activity, onConsentCompleted: (Boolean) -> Unit) =
            onConsentCompleted(consentToReturn)
        override fun showPrivacyOptions(activity: Activity, onCompleted: (Boolean) -> Unit) =
            onCompleted(consentToReturn)
        override fun clearLocalData(context: Context) = Unit
    }

    @Test
    fun vipUserDisablesAllAdsUnlocksAllFeaturesAndSuppressesUpsells() {
        var nowMs = 1_000_000L
        val bridge = FakeBridge()
        val gate = AdPlacementPolicyGate { nowMs }
        val facade = AppPluginMonetizationFacade(gate, bridge) { nowMs }
        facade.updateConsent(true)
        nowMs += AdPlacementPolicy.SESSION_WARMUP_MS + 1_000L

        assertTrue(facade.shouldShowBanner("ca-app-pub-1/2", nowMs))
        assertTrue(facade.canShowInterstitial(nowMs))

        facade.updateVip(true)
        assertEquals(true, bridge.lastRemoveAdsVip)
        assertTrue(facade.isVipUser())
        assertFalse(facade.shouldShowBanner("ca-app-pub-1/2", nowMs))
        assertFalse(facade.canShowInterstitial(nowMs))
        assertFalse(facade.canShowAppOpen(nowMs))
        assertEquals(AdBlockReason.VIP_USER, facade.evaluatePlacement(AdPlacementType.BANNER, nowMs).blockReason)

        for (feature in ProFeature.entries) {
            assertTrue("VIP should unlock $feature", facade.isFeatureUnlocked(feature, currentUsageCount = 999))
        }
        for (trigger in UpsellTrigger.entries) {
            assertNull("VIP should not see $trigger upsell", facade.softPaywallUpsell(trigger, currentCount = 999))
        }
    }

    @Test
    fun sacredZonesAndCelebrationDaySuppressAllAdFormats() {
        var nowMs = 10_000_000L
        val bridge = FakeBridge()
        val gate = AdPlacementPolicyGate { nowMs }
        val facade = AppPluginMonetizationFacade(gate, bridge) { nowMs }
        facade.updateConsent(true)
        nowMs += AdPlacementPolicy.SESSION_WARMUP_MS + 5_000L

        facade.enterSacredZone(SacredZone.ONBOARDING)
        facade.enterSacredZone(SacredZone.PAYWALL)
        assertTrue(facade.isInSacredZone())
        assertFalse(facade.shouldShowBanner("unit-1", nowMs))
        assertEquals(AdBlockReason.SACRED_ZONE, facade.evaluatePlacement(AdPlacementType.INTERSTITIAL, nowMs).blockReason)
        assertEquals(AdBlockReason.SACRED_ZONE, facade.evaluatePlacement(AdPlacementType.APP_OPEN, nowMs).blockReason)

        // Exiting one zone keeps suppression active until all zones exit.
        facade.exitSacredZone(SacredZone.ONBOARDING)
        assertTrue(facade.isInSacredZone())
        assertFalse(facade.shouldShowBanner("unit-1", nowMs))

        facade.exitSacredZone(SacredZone.PAYWALL)
        assertFalse(facade.isInSacredZone())
        assertTrue(facade.shouldShowBanner("unit-1", nowMs))

        facade.setCelebrationDay(true)
        assertTrue(facade.isInSacredZone())
        assertFalse(facade.shouldShowBanner("unit-1", nowMs))
        facade.setCelebrationDay(false)
        assertTrue(facade.shouldShowBanner("unit-1", nowMs))
    }

    @Test
    fun interstitialEnforces60sWarmup120sCooldownAndMaxThreePerSession() {
        var nowMs = 1_000_000L
        val gate = AdPlacementPolicyGate { nowMs }
        val facade = AppPluginMonetizationFacade(gate, FakeBridge()) { nowMs }
        facade.updateConsent(true)

        // 59s -> warmup blocks
        assertEquals(AdBlockReason.WARMUP_ACTIVE, facade.evaluatePlacement(AdPlacementType.INTERSTITIAL, nowMs + 59_999L).blockReason)
        nowMs += 60_000L
        assertTrue(facade.canShowInterstitial(nowMs))

        // Show #1
        facade.recordInterstitialShown(nowMs)
        assertEquals(AdBlockReason.COOLDOWN_ACTIVE, facade.evaluatePlacement(AdPlacementType.INTERSTITIAL, nowMs + 119_999L).blockReason)

        // Show #2 after 120s
        nowMs += 120_000L
        assertTrue(facade.canShowInterstitial(nowMs))
        facade.recordInterstitialShown(nowMs)

        // Show #3 after 120s
        nowMs += 120_000L
        assertTrue(facade.canShowInterstitial(nowMs))
        facade.recordInterstitialShown(nowMs)

        // Show #4 blocked by session cap even after waiting
        nowMs += 300_000L
        assertFalse(facade.canShowInterstitial(nowMs))
        assertEquals(AdBlockReason.SESSION_CAP_REACHED, facade.evaluatePlacement(AdPlacementType.INTERSTITIAL, nowMs).blockReason)

        // New session resets cap
        facade.resetSession(nowMs)
        assertFalse(facade.canShowInterstitial(nowMs)) // warmup active again
        assertTrue(facade.canShowInterstitial(nowMs + 60_000L))
    }

    @Test
    fun bannerRespectsKillSwitchRewardHideAndBlankUnit() {
        val bridge = FakeBridge()
        val facade = AppPluginMonetizationFacade(AdPlacementPolicyGate { 100_000L }, bridge) { 100_000L }
        facade.updateConsent(true)

        assertFalse(facade.shouldShowBanner("  "))
        assertTrue(facade.shouldShowBanner("ca-app-pub-1/2"))

        bridge.adsSdkDisabled = true
        assertFalse(facade.shouldShowBanner("ca-app-pub-1/2"))
        bridge.adsSdkDisabled = false

        bridge.bannerHiddenByReward = true
        assertFalse(facade.shouldShowBanner("ca-app-pub-1/2"))
    }

    @Test
    fun freeStarterQuotasCatalogParityAndSoftPaywallUpsells() {
        val facade = AppPluginMonetizationFacade(AdPlacementPolicyGate { 0L }, FakeBridge()) { 0L }

        assertEquals(setOf("vip_monthly", "vip_yearly", "vip_lifetime"), facade.catalogSpecs().map { it.sku }.toSet())
        assertTrue(facade.isVipSku(ProTierCatalog.YEARLY))
        assertFalse(facade.isVipSku("unknown_sku"))

        assertEquals(2, facade.remainingFreeAiQuota(0))
        assertEquals(1, facade.remainingFreeAiQuota(1))
        assertEquals(0, facade.remainingFreeAiQuota(2))
        assertTrue(facade.isFeatureUnlocked(ProFeature.AI_GIFT_CONCIERGE_UNLIMITED, 1))
        assertFalse(facade.isFeatureUnlocked(ProFeature.AI_GIFT_CONCIERGE_UNLIMITED, 2))

        assertEquals(5, facade.remainingFreeSecretNotes(0))
        assertEquals(0, facade.remainingFreeSecretNotes(5))
        assertNull(facade.softPaywallUpsell(UpsellTrigger.AI_QUOTA_REACHED, currentCount = 1))

        val aiUpsell = facade.softPaywallUpsell(UpsellTrigger.AI_QUOTA_REACHED, currentCount = 2)
        assertNotNull(aiUpsell)
        assertEquals(ProTierCatalog.YEARLY, aiUpsell!!.recommendedSku)

        val notesUpsell = facade.softPaywallUpsell(UpsellTrigger.SECRET_NOTES_LIMIT, currentCount = 5)
        assertNotNull(notesUpsell)
        assertEquals(ProFeature.DEEP_PARTNER_DOSSIER_UNLIMITED, notesUpsell!!.feature)
    }
}
