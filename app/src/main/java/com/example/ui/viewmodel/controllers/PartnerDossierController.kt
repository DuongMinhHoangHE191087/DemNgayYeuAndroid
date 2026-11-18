package com.example.ui.viewmodel.controllers

import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerDossierStore
import com.example.shared.dossier.PartnerInterestCatalog
import com.example.shared.dossier.SecretHintNote
import com.example.shared.monetization.AdPlacementPolicy
import com.example.shared.monetization.ProFeature
import com.example.shared.monetization.ProTierCatalog
import com.example.shared.monetization.SoftPaywallPrompt
import com.example.shared.monetization.UpsellTrigger
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SecretNoteOutcome {
  data class Added(val dossier: PartnerDossier) : SecretNoteOutcome
  data class QuotaReached(val prompt: SoftPaywallPrompt) : SecretNoteOutcome
  data object BlankIgnored : SecretNoteOutcome
}

/**
 * SOLID controller orchestrating [PartnerDossierStore], secret-note Pro gating, and dossier modal state.
 */
class PartnerDossierController(
  private val store: PartnerDossierStore,
  private val clock: () -> Long = { System.currentTimeMillis() },
  private val onDossierSaved: (PartnerDossier) -> Unit = {},
) {
  val dossier: StateFlow<PartnerDossier> = store.dossier

  private val _showDossierSheet = MutableStateFlow(false)
  val showDossierSheet: StateFlow<Boolean> = _showDossierSheet.asStateFlow()

  private val _softPaywallPrompt = MutableStateFlow<SoftPaywallPrompt?>(null)
  val softPaywallPrompt: StateFlow<SoftPaywallPrompt?> = _softPaywallPrompt.asStateFlow()

  fun openDossierSheet() { _showDossierSheet.value = true }
  fun closeDossierSheet() { _showDossierSheet.value = false }
  fun dismissPaywallPrompt() { _softPaywallPrompt.value = null }

  suspend fun loadScope(scopeId: String): PartnerDossier = store.load(scopeId)

  suspend fun saveDossier(updated: PartnerDossier): PartnerDossier {
    val stamped = updated.copy(updatedAtEpochMs = maxOf(clock(), updated.updatedAtEpochMs))
    return store.save(stamped).also(onDossierSaved)
  }

  suspend fun syncLegacyPreferences(
    likesCsv: String,
    budgetMaxVnd: Long,
    occasionRegion: String,
  ): PartnerDossier {
    val current = store.dossier.value
    val normalizedCategories = PartnerInterestCatalog.normalizeInterestsCsv(likesCsv)
    val merged = current.copy(
      interestCategories = normalizedCategories.ifEmpty { current.interestCategories },
      freeformLikes = likesCsv.trim(),
      budgetMaxVnd = budgetMaxVnd.coerceAtLeast(0L),
      occasionRegion = occasionRegion.trim().uppercase().ifBlank { "VN" },
      updatedAtEpochMs = clock(),
    )
    return store.save(merged).also(onDossierSaved)
  }

  suspend fun addSecretNote(
    noteText: String,
    occasionHint: String,
    isVip: Boolean,
    estimatedPriceVnd: Long = 0L,
    targetUrlOrShop: String = "",
  ): SecretNoteOutcome {
    val cleanNote = noteText.trim()
    if (cleanNote.isEmpty()) return SecretNoteOutcome.BlankIgnored

    val currentNotes = store.dossier.value.secretSurpriseNotes
    if (!ProFeature.DEEP_PARTNER_DOSSIER_UNLIMITED.isUnlocked(isVip, currentNotes.size)) {
      val prompt = AdPlacementPolicy.softPaywallUpsell(
        trigger = UpsellTrigger.SECRET_NOTES_LIMIT,
        isVip = isVip,
        currentCount = currentNotes.size,
      ) ?: SoftPaywallPrompt(
        trigger = UpsellTrigger.SECRET_NOTES_LIMIT,
        feature = ProFeature.DEEP_PARTNER_DOSSIER_UNLIMITED,
        titleVi = "Đạt giới hạn ${ProTierCatalog.FREE_SECRET_NOTES_LIMIT} Ghi chú bí mật",
        titleEn = "Reached ${ProTierCatalog.FREE_SECRET_NOTES_LIMIT} Secret Notes limit",
        messageVi = "Nâng cấp InLove Pro để lưu không giới hạn gợi ý bí mật.",
        messageEn = "Upgrade to InLove Pro for unlimited surprise hints.",
        ctaVi = "Mở khóa InLove Pro",
        ctaEn = "Unlock InLove Pro",
        recommendedSku = ProTierCatalog.YEARLY,
      )
      _softPaywallPrompt.value = prompt
      return SecretNoteOutcome.QuotaReached(prompt)
    }

    val note = SecretHintNote(
      id = UUID.randomUUID().toString(),
      note = cleanNote,
      occasionHint = occasionHint.trim(),
      estimatedPriceVnd = estimatedPriceVnd.coerceAtLeast(0L),
      targetUrlOrShop = targetUrlOrShop.trim(),
      isPurchased = false,
      createdAtEpochMs = clock(),
    )
    val updated = store.addSecretNote(note).also(onDossierSaved)
    return SecretNoteOutcome.Added(updated)
  }

  suspend fun toggleSecretNotePurchased(noteId: String, purchased: Boolean): PartnerDossier =
    store.markSecretNotePurchased(noteId, purchased).also(onDossierSaved)

  suspend fun removeSecretNote(noteId: String): PartnerDossier =
    store.removeSecretNote(noteId).also(onDossierSaved)

  suspend fun addGivenGift(record: GivenGiftRecord): PartnerDossier =
    store.addGivenGift(record).also(onDossierSaved)
}
