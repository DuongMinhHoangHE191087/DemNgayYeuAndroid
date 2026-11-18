package com.example.ui.viewmodel.controllers

import com.example.shared.ai.AiGiftAdviceBundle
import com.example.shared.ai.AiGiftAdvisorGateway
import com.example.shared.ai.AiGiftRequest
import com.example.shared.ai.RelationshipTone
import com.example.shared.calendar.NextOccasionReadiness
import com.example.shared.dossier.PartnerDossier
import com.example.shared.monetization.AdPlacementPolicy
import com.example.shared.monetization.ProTierCatalog
import com.example.shared.monetization.SoftPaywallPrompt
import com.example.shared.monetization.UpsellTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SOLID controller managing AI Gift & Love-Note Concierge state, tone selection,
 * monthly allowance tracking, and contextual soft-paywall prompts.
 */
class AiGiftConciergeController(
  private val gateway: AiGiftAdvisorGateway,
  private val remainingAllowanceProvider: (isVip: Boolean) -> Int = { isVip ->
    if (isVip) ProTierCatalog.VIP_AI_GENERATIONS_PER_MONTH
    else ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH
  },
) {
  private val _selectedTone = MutableStateFlow(RelationshipTone.SWEET)
  val selectedTone: StateFlow<RelationshipTone> = _selectedTone.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _adviceBundle = MutableStateFlow<AiGiftAdviceBundle?>(null)
  val adviceBundle: StateFlow<AiGiftAdviceBundle?> = _adviceBundle.asStateFlow()

  private val _remainingAllowance = MutableStateFlow(ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH)
  val remainingAllowance: StateFlow<Int> = _remainingAllowance.asStateFlow()

  private val _softPaywallPrompt = MutableStateFlow<SoftPaywallPrompt?>(null)
  val softPaywallPrompt: StateFlow<SoftPaywallPrompt?> = _softPaywallPrompt.asStateFlow()

  fun selectTone(tone: RelationshipTone) {
    _selectedTone.value = tone
  }

  fun syncAllowance(isVip: Boolean) {
    _remainingAllowance.value = remainingAllowanceProvider(isVip).coerceAtLeast(0)
    if (isVip) {
      _softPaywallPrompt.value = null
    }
  }

  fun dismissSoftPaywall() {
    _softPaywallPrompt.value = null
  }

  suspend fun generateAdvice(
    dossier: PartnerDossier,
    readiness: NextOccasionReadiness?,
    loveDays: Int,
    isVip: Boolean,
    isEnglish: Boolean,
    customOccasionTitle: String? = null,
    excludedGiftTitles: Set<String> = emptySet(),
  ): AiGiftAdviceBundle? {
    val preRemaining = remainingAllowanceProvider(isVip).coerceAtLeast(0)
    _remainingAllowance.value = preRemaining
    if (!isVip && preRemaining <= 0) {
      _softPaywallPrompt.value = AdPlacementPolicy.softPaywallUpsell(
        trigger = UpsellTrigger.AI_QUOTA_REACHED,
        isVip = false,
        currentCount = ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH,
      )
      return _adviceBundle.value
    }

    val occasionId = readiness?.occasionId ?: "couple_anniversary"
    val occasionTitle = customOccasionTitle?.trim()?.takeIf { it.isNotEmpty() }
      ?: readiness?.let { if (isEnglish) it.titleEn else it.titleVi }
      ?: if (isEnglish) "Love Anniversary" else "Kỷ niệm ngày yêu"

    _isLoading.value = true
    return try {
      val request = AiGiftRequest(
        dossier = dossier,
        occasionId = occasionId,
        occasionTitle = occasionTitle,
        daysUntilOccasion = readiness?.daysRemaining,
        loveDays = loveDays.coerceAtLeast(0),
        tone = _selectedTone.value,
        isEnglish = isEnglish,
        maxRecommendations = 5,
        excludedGiftTitles = excludedGiftTitles,
      )
      val bundle = gateway.advise(request)
      _adviceBundle.value = bundle
      val updatedRemaining = bundle.remainingFreeAllowance
        ?: remainingAllowanceProvider(isVip).coerceAtLeast(0)
      _remainingAllowance.value = updatedRemaining.coerceAtLeast(0)

      if (!isVip && updatedRemaining <= 0 && bundle.usedFreeAllowance) {
        _softPaywallPrompt.value = AdPlacementPolicy.softPaywallUpsell(
          trigger = UpsellTrigger.AI_QUOTA_REACHED,
          isVip = false,
          currentCount = ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH,
        )
      } else {
        _softPaywallPrompt.value = null
      }
      bundle
    } finally {
      _isLoading.value = false
    }
  }
}
