package com.example.monetization

import android.app.Activity
import android.content.Context
import com.example.shared.monetization.AdDecision
import com.example.shared.monetization.AdPlacementPolicy
import com.example.shared.monetization.AdPlacementType
import com.example.shared.monetization.AdSessionState
import com.example.shared.monetization.ProFeature
import com.example.shared.monetization.ProPlanSpec
import com.example.shared.monetization.SacredZone
import com.example.shared.monetization.SoftPaywallPrompt
import com.example.shared.monetization.UpsellTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure policy gate governing when ads are permitted (VIP, UMP consent, Sacred Zones, warmup, cooldown, session cap).
 */
interface AdPolicyGate {
    val sessionState: StateFlow<AdSessionState>

    fun updateVip(isVip: Boolean)

    fun updateConsent(hasConsent: Boolean)

    fun enterSacredZone(zone: SacredZone)

    fun exitSacredZone(zone: SacredZone)

    fun setCelebrationDay(isCelebrationDay: Boolean)

    fun isInSacredZone(): Boolean

    fun evaluate(type: AdPlacementType, nowMs: Long): AdDecision

    fun canShowBanner(nowMs: Long = 0L): Boolean

    fun canShowInterstitial(nowMs: Long): Boolean

    fun canShowAppOpen(nowMs: Long): Boolean

    fun recordInterstitialShown(shownAtMs: Long)

    fun resetSession(startedAtMs: Long)
}

/**
 * Feature & quota gate for InLove Pro subscriptions and contextual soft-paywall prompts.
 */
interface MonetizationFeatureGate {
    fun isVipUser(): Boolean

    fun isFeatureUnlocked(feature: ProFeature, currentUsageCount: Int = 0): Boolean

    fun remainingFreeAiQuota(usedThisMonth: Int): Int

    fun remainingFreeSecretNotes(currentNotesCount: Int): Int

    fun softPaywallUpsell(trigger: UpsellTrigger, currentCount: Int = 0): SoftPaywallPrompt?

    fun catalogSpecs(): List<ProPlanSpec>

    fun isVipSku(productId: String): Boolean
}

/**
 * Dependency-inversion seam over `:appplugin` static singletons (`MonetizationSdk`, `AdsHelper`, `ConsentManager`),
 * allowing JVM unit tests without live Play Services or static Android SDK initialization.
 */
interface AppPluginBridge {
    fun configureSdk(context: Context, playerId: String? = null)

    fun syncRemoveAds(isVip: Boolean)

    fun isAdsSdkDisabled(): Boolean

    fun isBannerHiddenByReward(): Boolean

    fun grantBannerHideReward(durationMillis: Long)

    fun suppressNextOpenAd(durationMillis: Long = 5_000L)

    fun recordScreenChange()

    fun requestConsent(activity: Activity, onConsentCompleted: (canRequestAds: Boolean) -> Unit)

    fun showPrivacyOptions(activity: Activity, onCompleted: (canRequestAds: Boolean) -> Unit)

    fun clearLocalData(context: Context)
}

/**
 * Thread-safe [AdPolicyGate] backed by `:shared`'s pure [AdPlacementPolicy].
 */
class AdPlacementPolicyGate(
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AdPolicyGate {

    private val _sessionState = MutableStateFlow(AdSessionState(sessionStartedAtMs = clock()))
    override val sessionState: StateFlow<AdSessionState> = _sessionState.asStateFlow()

    override fun updateVip(isVip: Boolean) {
        _sessionState.update { it.copy(isVip = isVip) }
    }

    override fun updateConsent(hasConsent: Boolean) {
        _sessionState.update { it.copy(hasConsent = hasConsent) }
    }

    override fun enterSacredZone(zone: SacredZone) {
        _sessionState.update { it.copy(activeSacredZones = it.activeSacredZones + zone) }
    }

    override fun exitSacredZone(zone: SacredZone) {
        _sessionState.update { it.copy(activeSacredZones = it.activeSacredZones - zone) }
    }

    override fun setCelebrationDay(isCelebrationDay: Boolean) {
        _sessionState.update { it.copy(isCelebrationDay = isCelebrationDay) }
    }

    override fun isInSacredZone(): Boolean = AdPlacementPolicy.isInSacredZone(_sessionState.value)

    override fun evaluate(type: AdPlacementType, nowMs: Long): AdDecision =
        AdPlacementPolicy.evaluate(type, _sessionState.value, nowMs)

    override fun canShowBanner(nowMs: Long): Boolean =
        AdPlacementPolicy.canShowBanner(_sessionState.value, nowMs)

    override fun canShowInterstitial(nowMs: Long): Boolean =
        AdPlacementPolicy.canShowInterstitial(_sessionState.value, nowMs)

    override fun canShowAppOpen(nowMs: Long): Boolean =
        AdPlacementPolicy.canShowAppOpen(_sessionState.value, nowMs)

    override fun recordInterstitialShown(shownAtMs: Long) {
        _sessionState.update { AdPlacementPolicy.recordInterstitialShown(it, shownAtMs) }
    }

    override fun resetSession(startedAtMs: Long) {
        _sessionState.update {
            it.copy(
                sessionStartedAtMs = startedAtMs,
                lastInterstitialShownAtMs = 0L,
                interstitialsShownThisSession = 0,
            )
        }
    }
}
