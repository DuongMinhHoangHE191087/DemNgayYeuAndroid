package com.example.monetization

import android.app.Activity
import android.content.Context
import com.app.plugin.MonetizationSdk
import com.app.plugin.ads.AdsHelper
import com.app.plugin.consent.ConsentManager
import com.example.billing.VipProductIds
import com.example.shared.monetization.AdDecision
import com.example.shared.monetization.AdPlacementPolicy
import com.example.shared.monetization.AdPlacementType
import com.example.shared.monetization.AdSessionState
import com.example.shared.monetization.ProFeature
import com.example.shared.monetization.ProPlanSpec
import com.example.shared.monetization.ProTierCatalog
import com.example.shared.monetization.SacredZone
import com.example.shared.monetization.SoftPaywallPrompt
import com.example.shared.monetization.UpsellTrigger
import kotlinx.coroutines.flow.StateFlow

/**
 * Production implementation of [AppPluginBridge] wrapping `:appplugin`'s static APIs safely.
 */
object DefaultAppPluginBridge : AppPluginBridge {

    override fun configureSdk(context: Context, playerId: String?) {
        runCatching {
            MonetizationSdk.configure(context) {
                brainEnabled = false
                brainRolloutFraction = 0.05
                childDirected = false
                if (!playerId.isNullOrBlank()) {
                    this.playerId = playerId
                }
                inappProducts = listOf(VipProductIds.LIFETIME)
                subsProducts = listOf(VipProductIds.MONTHLY, VipProductIds.YEARLY)
                removeAdsProducts = VipProductIds.ALL
            }
        }
    }

    override fun syncRemoveAds(isVip: Boolean) {
        runCatching { AdsHelper.instance.setRemoveAds(if (isVip) 1 else 0) }
    }

    override fun isAdsSdkDisabled(): Boolean =
        runCatching { AdsHelper.instance.isDisableAds(0) }.getOrDefault(true)

    override fun isBannerHiddenByReward(): Boolean =
        runCatching { AdsHelper.instance.isBannerHiddenByReward() }.getOrDefault(false)

    override fun grantBannerHideReward(durationMillis: Long) {
        runCatching { AdsHelper.instance.grantBannerHideReward(durationMillis) }
    }

    override fun suppressNextOpenAd(durationMillis: Long) {
        runCatching { AdsHelper.instance.suppressNextOpenAd(durationMillis) }
    }

    override fun recordScreenChange() {
        runCatching { AdsHelper.instance.recordScreenChange() }
    }

    override fun requestConsent(activity: Activity, onConsentCompleted: (canRequestAds: Boolean) -> Unit) {
        val appContext = activity.applicationContext
        runCatching {
            ConsentManager.request(activity) {
                val allowed = runCatching { ConsentManager.canStartAdSdks(appContext) }.getOrDefault(false)
                onConsentCompleted(allowed)
            }
        }.onFailure {
            onConsentCompleted(false)
        }
    }

    override fun showPrivacyOptions(activity: Activity, onCompleted: (canRequestAds: Boolean) -> Unit) {
        val appContext = activity.applicationContext
        runCatching {
            ConsentManager.showPrivacyOptions(activity) {
                val allowed = runCatching { ConsentManager.canStartAdSdks(appContext) }.getOrDefault(false)
                onCompleted(allowed)
            }
        }.onFailure {
            onCompleted(false)
        }
    }

    override fun clearLocalData(context: Context) {
        runCatching { MonetizationSdk.clearLocalData(context) }
    }
}

/**
 * Clean facade unifying `:appplugin` (`MonetizationSdk`, `AdsHelper`, `ConsentManager`)
 * with `:shared`'s pure [AdPlacementPolicy] and [ProTierCatalog].
 *
 * Enforces win-win monetization:
 * - Zero ads for VIP, missing UMP consent, celebration days, or any [SacredZone].
 * - 60s session warmup, 120s interstitial cooldown, and max 3 interstitials/session.
 * - Contextual soft-paywall prompts and free starter quota tracking for AI Concierge & Secret Notes.
 */
class AppPluginMonetizationFacade(
    private val adPolicyGate: AdPolicyGate = AdPlacementPolicyGate(),
    private val pluginBridge: AppPluginBridge = DefaultAppPluginBridge,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AdPolicyGate by adPolicyGate, MonetizationFeatureGate {

    override fun updateVip(isVip: Boolean) {
        adPolicyGate.updateVip(isVip)
        pluginBridge.syncRemoveAds(isVip)
    }

    override fun isVipUser(): Boolean = adPolicyGate.sessionState.value.isVip

    override fun isFeatureUnlocked(feature: ProFeature, currentUsageCount: Int): Boolean =
        feature.isUnlocked(isVip = isVipUser(), currentUsageCount = currentUsageCount)

    override fun remainingFreeAiQuota(usedThisMonth: Int): Int =
        if (isVipUser()) {
            (ProTierCatalog.VIP_AI_GENERATIONS_PER_MONTH - usedThisMonth).coerceAtLeast(0)
        } else {
            (ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH - usedThisMonth).coerceAtLeast(0)
        }

    override fun remainingFreeSecretNotes(currentNotesCount: Int): Int =
        if (isVipUser()) Int.MAX_VALUE
        else (ProTierCatalog.FREE_SECRET_NOTES_LIMIT - currentNotesCount).coerceAtLeast(0)

    override fun softPaywallUpsell(trigger: UpsellTrigger, currentCount: Int): SoftPaywallPrompt? =
        AdPlacementPolicy.softPaywallUpsell(trigger = trigger, isVip = isVipUser(), currentCount = currentCount)

    override fun catalogSpecs(): List<ProPlanSpec> = ProTierCatalog.PLANS

    override fun isVipSku(productId: String): Boolean = ProTierCatalog.isVipSku(productId)

    /**
     * Checks banner eligibility combining pure [AdPlacementPolicy] (VIP, consent, SacredZones)
     * with `:appplugin` kill switch and reward hide windows.
     */
    fun shouldShowBanner(adUnitId: String, nowMs: Long = clock()): Boolean {
        if (adUnitId.isBlank()) return false
        if (!adPolicyGate.canShowBanner(nowMs)) return false
        if (pluginBridge.isBannerHiddenByReward()) return false
        return !pluginBridge.isAdsSdkDisabled()
    }

    /** Records a screen transition and suppresses accidental App Open Ads when leaving/returning from external flows. */
    fun onScreenTransition(suppressNextAoaMs: Long = 0L) {
        pluginBridge.recordScreenChange()
        if (suppressNextAoaMs > 0L) {
            pluginBridge.suppressNextOpenAd(suppressNextAoaMs)
        }
    }

    fun evaluatePlacement(type: AdPlacementType, nowMs: Long = clock()): AdDecision =
        adPolicyGate.evaluate(type, nowMs)

    fun currentState(): AdSessionState = adPolicyGate.sessionState.value

    fun configureSdk(context: Context, playerId: String? = null) =
        pluginBridge.configureSdk(context, playerId)

    fun requestConsent(activity: Activity, onConsentCompleted: (Boolean) -> Unit) {
        pluginBridge.requestConsent(activity) { allowed ->
            adPolicyGate.updateConsent(allowed)
            onConsentCompleted(allowed)
        }
    }

    fun showPrivacyOptions(activity: Activity, onCompleted: (Boolean) -> Unit) {
        pluginBridge.showPrivacyOptions(activity) { allowed ->
            adPolicyGate.updateConsent(allowed)
            onCompleted(allowed)
        }
    }

    fun grantAdFreeHourReward(durationMillis: Long = AdsHelper.DEFAULT_BANNER_HIDE_MILLIS) =
        pluginBridge.grantBannerHideReward(durationMillis)

    fun clearLocalData(context: Context) = pluginBridge.clearLocalData(context)
}
