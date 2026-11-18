package com.example.ui.viewmodel

import com.example.shared.ai.AdviceSource
import com.example.shared.ai.AiGiftAdviceBundle
import com.example.shared.ai.AiGiftAdvisorGateway
import com.example.shared.ai.AiGiftRequest
import com.example.shared.ai.LoveNoteDraft
import com.example.shared.ai.RelationshipTone
import com.example.shared.ai.RichGiftRecommendation
import com.example.shared.calendar.PreparationStage
import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerDossierStore
import com.example.shared.dossier.SecretHintNote
import com.example.shared.monetization.ProTierCatalog
import com.example.shared.monetization.UpsellTrigger
import com.example.ui.viewmodel.controllers.AiGiftConciergeController
import com.example.ui.viewmodel.controllers.AnniversaryReadinessController
import com.example.ui.viewmodel.controllers.DialogUiStateController
import com.example.ui.viewmodel.controllers.PartnerDossierController
import com.example.ui.viewmodel.controllers.SecretNoteOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryPartnerDossierStore : PartnerDossierStore {
  private val _dossier = MutableStateFlow(PartnerDossier())
  override val dossier: StateFlow<PartnerDossier> = _dossier.asStateFlow()

  override suspend fun load(scopeId: String): PartnerDossier {
    _dossier.value = _dossier.value.copy(scopeId = scopeId)
    return _dossier.value
  }

  override suspend fun save(dossier: PartnerDossier): PartnerDossier {
    _dossier.value = dossier
    return dossier
  }

  override suspend fun addSecretNote(note: SecretHintNote): PartnerDossier {
    val updated = _dossier.value.copy(
      secretSurpriseNotes = listOf(note) + _dossier.value.secretSurpriseNotes
    )
    _dossier.value = updated
    return updated
  }

  override suspend fun markSecretNotePurchased(noteId: String, purchased: Boolean): PartnerDossier {
    val updated = _dossier.value.copy(
      secretSurpriseNotes = _dossier.value.secretSurpriseNotes.map {
        if (it.id == noteId) it.copy(isPurchased = purchased) else it
      }
    )
    _dossier.value = updated
    return updated
  }

  override suspend fun removeSecretNote(noteId: String): PartnerDossier {
    val updated = _dossier.value.copy(
      secretSurpriseNotes = _dossier.value.secretSurpriseNotes.filterNot { it.id == noteId }
    )
    _dossier.value = updated
    return updated
  }

  override suspend fun addGivenGift(record: GivenGiftRecord): PartnerDossier {
    val updated = _dossier.value.copy(giftHistory = listOf(record) + _dossier.value.giftHistory)
    _dossier.value = updated
    return updated
  }

  override suspend fun clearScope(scopeId: String) {
    _dossier.value = PartnerDossier(scopeId = scopeId)
  }
}

private class RecordingAiGateway(
  private val remainingAfterCall: Int = 1,
) : AiGiftAdvisorGateway {
  var callCount: Int = 0
  var lastRequest: AiGiftRequest? = null

  override suspend fun advise(request: AiGiftRequest): AiGiftAdviceBundle {
    callCount += 1
    lastRequest = request
    return AiGiftAdviceBundle(
      source = AdviceSource.SMART_LOCAL_ENGINE,
      occasionId = request.occasionId,
      occasionTitle = request.occasionTitle,
      summaryHeadline = "Gợi ý chuẩn gu ${request.occasionTitle}",
      recommendations = listOf(
        RichGiftRecommendation(
          id = "rec_1",
          title = "Bó hoa tulip phấn",
          category = "Lãng mạn",
          priceRangeLabel = "350.000–550.000 ₫",
          reason = "Hợp tone ${request.tone.name}",
          preparationPlan = "Đặt trước 2 ngày kèm thiệp tay"
        )
      ),
      loveNotes = listOf(
        LoveNoteDraft(
          id = "note_1",
          tone = request.tone,
          title = "Lời chúc ngọt ngào",
          message = "Cảm ơn em vì hành trình tuyệt đẹp bên nhau!"
        )
      ),
      usedFreeAllowance = true,
      remainingFreeAllowance = remainingAfterCall,
    )
  }
}

class ControllersUnitTest {

  @Test
  fun partnerDossierController_enforcesFreeSecretNotesLimitAndUnlocksForVip() = runBlocking {
    val store = InMemoryPartnerDossierStore()
    val controller = PartnerDossierController(store = store, clock = { 1000L })

    assertEquals(SecretNoteOutcome.BlankIgnored, controller.addSecretNote("   ", "8/3", isVip = false))

    repeat(ProTierCatalog.FREE_SECRET_NOTES_LIMIT) { index ->
      val outcome = controller.addSecretNote("Hint #$index", "Birthday", isVip = false)
      assertTrue(outcome is SecretNoteOutcome.Added)
    }
    assertEquals(ProTierCatalog.FREE_SECRET_NOTES_LIMIT, controller.dossier.value.secretSurpriseNotes.size)

    val blocked = controller.addSecretNote("Over limit hint", "Anniversary", isVip = false)
    assertTrue(blocked is SecretNoteOutcome.QuotaReached)
    assertEquals(UpsellTrigger.SECRET_NOTES_LIMIT, controller.softPaywallPrompt.value?.trigger)

    controller.dismissPaywallPrompt()
    assertNull(controller.softPaywallPrompt.value)

    val vipOutcome = controller.addSecretNote("VIP unlimited hint", "Birthday", isVip = true)
    assertTrue(vipOutcome is SecretNoteOutcome.Added)
    assertEquals(ProTierCatalog.FREE_SECRET_NOTES_LIMIT + 1, controller.dossier.value.secretSurpriseNotes.size)

    val firstNoteId = controller.dossier.value.secretSurpriseNotes.first().id
    controller.toggleSecretNotePurchased(firstNoteId, true)
    assertTrue(controller.dossier.value.secretSurpriseNotes.first().isPurchased)
    controller.removeSecretNote(firstNoteId)
    assertEquals(ProTierCatalog.FREE_SECRET_NOTES_LIMIT, controller.dossier.value.secretSurpriseNotes.size)
  }

  @Test
  fun partnerDossierController_syncLegacyPreferencesNormalizesRegionAndInterests() = runBlocking {
    val store = InMemoryPartnerDossierStore()
    val controller = PartnerDossierController(store = store, clock = { 2000L })

    val updated = controller.syncLegacyPreferences(
      likesCsv = "coffee, travel, photography",
      budgetMaxVnd = 750_000L,
      occasionRegion = " intl "
    )

    assertEquals(750_000L, updated.budgetMaxVnd)
    assertEquals("INTL", updated.occasionRegion)
    assertEquals("coffee, travel, photography", updated.freeformLikes)
    assertTrue(updated.interestCategories.isNotEmpty())
  }

  @Test
  fun aiGiftConciergeController_tracksQuotaAndTriggersSoftPaywall() = runBlocking {
    var available = 1
    val gateway = RecordingAiGateway(remainingAfterCall = 0)
    val controller = AiGiftConciergeController(
      gateway = gateway,
      remainingAllowanceProvider = { vip -> if (vip) ProTierCatalog.VIP_AI_GENERATIONS_PER_MONTH else available }
    )

    controller.selectTone(RelationshipTone.DEEPLY_ROMANTIC)
    assertEquals(RelationshipTone.DEEPLY_ROMANTIC, controller.selectedTone.value)

    val bundle = controller.generateAdvice(
      dossier = PartnerDossier(),
      readiness = null,
      loveDays = 100,
      isVip = false,
      isEnglish = false,
      customOccasionTitle = "100 Ngày Yêu"
    )

    assertNotNull(bundle)
    assertEquals(1, gateway.callCount)
    assertEquals(RelationshipTone.DEEPLY_ROMANTIC, gateway.lastRequest?.tone)
    assertEquals(0, controller.remainingAllowance.value)
    assertEquals(UpsellTrigger.AI_QUOTA_REACHED, controller.softPaywallPrompt.value?.trigger)

    available = 0
    controller.generateAdvice(
      dossier = PartnerDossier(),
      readiness = null,
      loveDays = 100,
      isVip = false,
      isEnglish = false
    )
    assertEquals(1, gateway.callCount)

    controller.syncAllowance(isVip = true)
    assertEquals(ProTierCatalog.VIP_AI_GENERATIONS_PER_MONTH, controller.remainingAllowance.value)
    assertNull(controller.softPaywallPrompt.value)
  }

  @Test
  fun anniversaryReadinessController_detectsSacredDayAndThatTich() {
    val controller = AnniversaryReadinessController()

    assertEquals(LocalDate(2025, 2, 14), controller.parseKmpDate("14/02/2025"))
    assertEquals(LocalDate(2027, 2, 20), controller.parseKmpDate("2027-02-20"))
    assertNull(controller.parseKmpDate("Hôm nay"))

    controller.refresh(
      anniversaryDateText = "14/02/2025",
      partnerBirthDateText = "20/08/2001",
      region = "VN",
      todayOverride = LocalDate(2027, 2, 14)
    )

    assertTrue(controller.isSacredCelebrationToday.value)
    assertTrue(controller.isTodayCelebration())
    assertEquals(PreparationStage.TODAY_CELEBRATION, controller.nextReadiness.value?.stage)
    assertEquals("2027-08-08", controller.nextThatTichIso.value)
    assertNotNull(controller.nextMonthiversary.value)

    controller.refresh(
      anniversaryDateText = "14/02/2025",
      partnerBirthDateText = "20/08/2001",
      region = "VN",
      todayOverride = LocalDate(2027, 3, 1)
    )
    assertFalse(controller.isTodayCelebration())
  }

  @Test
  fun dialogUiStateController_managesModalsAndPresetsCleanly() {
    val ui = DialogUiStateController()

    ui.openAddAnniversaryDialog("14/02/2027")
    assertTrue(ui.showAddAnniversaryDialog.value)
    assertEquals("14/02/2027", ui.prefilledAnniversaryDate.value)

    ui.closeAddAnniversaryDialog()
    assertFalse(ui.showAddAnniversaryDialog.value)
    assertNull(ui.prefilledAnniversaryDate.value)

    ui.openSetAlarmDialog(title = "Buy flowers", message = "Before 5 PM", reminderId = 42L)
    assertTrue(ui.showSetAlarmDialog.value)
    assertEquals("Buy flowers", ui.alarmDialogPresetTitle.value)
    assertEquals("Before 5 PM", ui.alarmDialogPresetMessage.value)
    assertEquals(42L, ui.alarmDialogReminderId.value)

    ui.closeSetAlarmDialog()
    assertFalse(ui.showSetAlarmDialog.value)

    ui.openVipProposal()
    assertTrue(ui.showVipProposalDetail.value)
    ui.closeVipProposal()
    assertFalse(ui.showVipProposalDetail.value)
  }
}
