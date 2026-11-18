@file:Suppress("FunctionName")
package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.domain.content.GiftCatalog
import com.example.domain.usecase.PersonalizedGiftSuggestion
import com.example.ui.components.AppAlertDialog
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.animateDialogEntrance
import com.example.ui.designsystem.InLoveColors
import com.example.ui.screens.gift.AiGiftConciergeSection
import com.example.ui.screens.gift.ChecklistRowContent
import com.example.ui.screens.gift.GiftAmbientBannerCard
import com.example.ui.screens.gift.GiftBottomActions
import com.example.ui.screens.gift.GiftCategoryFilterRow
import com.example.ui.screens.gift.GiftChecklistCard
import com.example.ui.screens.gift.GiftIdeaCardContent
import com.example.ui.screens.gift.GiftIllustrationCaption
import com.example.ui.screens.gift.GiftReminderRowContent
import com.example.ui.screens.gift.GiftRemindersCard
import com.example.ui.screens.gift.GiftVipProposalBanner
import com.example.ui.screens.gift.PartnerDossierCard
import com.example.ui.screens.gift.PartnerDossierSheet
import com.example.ui.screens.gift.PartnerPreferencesEditorContent
import com.example.ui.screens.gift.PersonalizedGiftCardContent
import com.example.ui.screens.gift.PersonalizedGiftListContent
import com.example.ui.screens.gift.WishlistNotebookDialogContent
import com.example.ui.theme.InLoveFonts
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.OnSurface
import com.example.ui.theme.Primary
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel

@Composable
fun GiftScreen(
  viewModel: InLoveViewModel
) {
  val giftIdeas by viewModel.giftIdeas.collectAsState()
  val personalizedGifts by viewModel.personalizedGifts.collectAsState()
  val checklistItems by viewModel.checklistItems.collectAsState()
  val giftReminders by viewModel.giftReminders.collectAsState()
  val selectedCategory by viewModel.giftCategory.collectAsState()
  val mutualInterests by viewModel.mutualInterests.collectAsState()
  val partnerUser by viewModel.partnerOnlineUser.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val isEnglish = appLanguage == AppLanguage.EN
  val coupleProfile by viewModel.coupleProfile.collectAsState()
  val isVip by viewModel.isVip.collectAsState()
  val partnerDossier by viewModel.partnerDossier.collectAsState()
  val showDossierSheet by viewModel.showPartnerDossierSheet.collectAsState()
  val dossierSoftPaywall by viewModel.dossierSoftPaywallPrompt.collectAsState()
  val nextReadiness by viewModel.nextOccasionReadiness.collectAsState()
  val selectedTone by viewModel.selectedConciergeTone.collectAsState()
  val isAiLoading by viewModel.isAiConciergeLoading.collectAsState()
  val aiBundle by viewModel.aiAdviceBundle.collectAsState()
  val aiAllowance by viewModel.aiRemainingAllowance.collectAsState()
  val aiSoftPaywall by viewModel.aiSoftPaywallPrompt.collectAsState()
  val context = LocalContext.current

  var giftItemToDelete by remember { mutableStateOf<ChecklistItemEntity?>(null) }
  var reminderToDelete by remember { mutableStateOf<GiftReminderEntity?>(null) }
  var showWishlistDialog by remember { mutableStateOf(false) }
  var sharePreview by remember { mutableStateOf<String?>(null) }

  sharePreview?.let { text ->
    AppAlertDialog(
      onDismissRequest = { sharePreview = null },
      scrollableText = true,
      modifier = Modifier.animateDialogEntrance(),
      containerColor = Color.White,
      title = { Text(if (isEnglish) "Share this suggestion?" else "Chia sẻ gợi ý này?", fontWeight = FontWeight.Bold, color = TextDarkPlum) },
      text = { Text(text = text, fontSize = 14.sp, color = TextDarkPlum) },
      confirmButton = {
        PrimaryGradientButton(
          text = if (isEnglish) "Share" else "Chia sẻ",
          onClick = {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(shareIntent, if (isEnglish) "Share suggestion via" else "Chia sẻ gợi ý qua"))
            sharePreview = null
          },
          modifier = Modifier.heightIn(min = MinTouchBox),
          testTag = "btn_confirm_share_gift"
        )
      },
      dismissButton = {
        SecondaryButton(
          text = if (isEnglish) "Cancel" else "Hủy",
          onClick = { sharePreview = null },
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
      }
    )
  }

  dossierSoftPaywall?.let { prompt ->
    AppAlertDialog(
      onDismissRequest = { viewModel.dismissDossierSoftPaywall() },
      scrollableText = true,
      modifier = Modifier.animateDialogEntrance(),
      containerColor = Color.White,
      title = { Text(if (isEnglish) prompt.titleEn else prompt.titleVi, fontWeight = FontWeight.Bold, color = TextDarkPlum) },
      text = { Text(if (isEnglish) prompt.messageEn else prompt.messageVi, fontSize = 14.sp, color = TextSubtlePlum) },
      confirmButton = {
        PrimaryGradientButton(
          text = if (isEnglish) prompt.ctaEn else prompt.ctaVi,
          onClick = {
            viewModel.dismissDossierSoftPaywall()
            viewModel.closePartnerDossierSheet()
            viewModel.setVipDialogVisible(true)
          },
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
      },
      dismissButton = {
        SecondaryButton(
          text = if (isEnglish) "Later" else "Để sau",
          onClick = { viewModel.dismissDossierSoftPaywall() },
          modifier = Modifier.heightIn(min = MinTouchBox)
        )
      }
    )
  }

  reminderToDelete?.let { reminder ->
    DeleteConfirmationDialog(
      title = if (isEnglish) "Delete Gift Reminder?" else "Xóa lời nhắc quà tặng?",
      message = if (isEnglish) "Remove reminder '${reminder.title}' from your gift list?" else "Bạn có muốn xóa lời nhắc '${reminder.title}' khỏi danh sách quà không?",
      itemName = reminder.title,
      isEnglish = isEnglish,
      onConfirm = {
        viewModel.deleteGiftReminder(reminder.id)
        reminderToDelete = null
      },
      onDismiss = { reminderToDelete = null }
    )
  }

  giftItemToDelete?.let { item ->
    DeleteConfirmationDialog(
      title = if (isEnglish) "Delete Checklist Item?" else "Xóa việc chuẩn bị quà?",
      message = if (isEnglish) "Are you sure you want to delete this item from the gift checklist?" else "Bạn có chắc chắn muốn xóa mục này khỏi checklist quà tặng không?",
      itemName = item.text,
      isEnglish = isEnglish,
      onConfirm = {
        viewModel.deleteChecklistItem(item.id)
        giftItemToDelete = null
      },
      onDismiss = { giftItemToDelete = null }
    )
  }

  val completedCount = checklistItems.count { it.isCompleted }
  val totalCount = checklistItems.size
  val progressPercent = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()) * 100f else 0f
  val filteredIdeas = remember(giftIdeas, selectedCategory) {
    GiftCatalog.filterByChip(giftIdeas, selectedCategory)
  }
  val upcomingOccasionTitle = remember(nextReadiness, isEnglish) {
    nextReadiness?.let { if (isEnglish) it.titleEn else it.titleVi }
      ?: if (isEnglish) "Love Anniversary" else "Kỷ niệm ngày yêu"
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Ambient Banner Card
    item(contentType = "gift_ambient_banner") {
      GiftAmbientBannerCard(progressPercent = progressPercent, isEnglish = isEnglish)
    }

    // 1b. Personalized suggestions + legacy partner preferences editor
    item(contentType = "gift_personalized") {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = InLoveColors.SoftBlushCard),
        border = BorderStroke(1.dp, Color(0xFFFF80AB).copy(alpha = 0.5f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("mutual_interests_gift_card")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          if (mutualInterests.isNotEmpty()) {
            Text(
              text = if (isEnglish) "You both love: ${mutualInterests.joinToString(" • ")}" else "Hai bạn cùng yêu thích: ${mutualInterests.joinToString(" • ")}",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextDarkPlum
            )
            Spacer(modifier = Modifier.height(8.dp))
          }

          if (personalizedGifts.isNotEmpty()) {
            PersonalizedGiftList(
              suggestions = personalizedGifts,
              shareLabel = if (isEnglish) "Share this suggestion" else "Chia sẻ gợi ý này",
              onShare = { sharePreview = it },
              onScheduleReminder = { s ->
                viewModel.openSetAlarmDialog(
                  title = s.gift.title,
                  message = listOf(s.reason, s.preparation).filter { it.isNotBlank() }.joinToString(" • ")
                )
              },
              reminderLabel = if (isEnglish) "Remind to prepare gift" else "Nhắc chuẩn bị quà"
            )
          } else {
            Text(
              text = if (isEnglish) "Pick interests below for tailored ideas." else "Chọn sở thích bên dưới để nhận gợi ý riêng.",
              fontSize = 12.sp,
              color = TextSubtlePlum
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          PrimaryGradientButton(
            text = if (isEnglish) "More ideas" else "Gợi ý thêm",
            onClick = { viewModel.triggerAiGiftSuggestions() },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = MinTouchBox),
            testTag = "btn_trigger_ai_gift_suggestions"
          )

          Spacer(modifier = Modifier.height(4.dp))
          PartnerPreferencesEditor(
            profileExists = coupleProfile != null,
            savedLikes = coupleProfile?.likesCsv.orEmpty(),
            savedBudgetVnd = coupleProfile?.budgetMaxVnd ?: 0L,
            savedRegion = coupleProfile?.occasionRegion.orEmpty(),
            isEnglish = isEnglish,
            onSave = viewModel::savePartnerPreferences
          )
        }
      }
    }

    // 1c. Deep Partner Dossier Summary Card
    item(contentType = "gift_partner_dossier") {
      PartnerDossierCard(
        dossier = partnerDossier,
        isVip = isVip,
        isEnglish = isEnglish,
        onOpenEditor = { viewModel.openPartnerDossierSheet() }
      )
    }

    // 1d. Interactive Gift & Love-Note Concierge
    item(contentType = "gift_ai_concierge") {
      AiGiftConciergeSection(
        selectedTone = selectedTone,
        occasionTitle = upcomingOccasionTitle,
        adviceBundle = aiBundle,
        isLoading = isAiLoading,
        isVip = isVip,
        remainingAllowance = aiAllowance,
        softPaywallPrompt = aiSoftPaywall,
        isEnglish = isEnglish,
        onSelectTone = viewModel::selectConciergeTone,
        onGenerateAdvice = { viewModel.generateAiGiftAdvice(upcomingOccasionTitle) },
        onOpenPaywall = { viewModel.setVipDialogVisible(true) },
        onCopyLoveNote = { note ->
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
          clipboard?.setPrimaryClip(ClipData.newPlainText("inlove_note", note))
          viewModel.showToast(if (isEnglish) "Love note copied" else "Đã sao chép lời chúc")
        },
        onShareText = { sharePreview = it },
        onSaveGiftReminder = { rec ->
          viewModel.addGiftReminder(
            title = rec.title,
            recipient = partnerUser?.effectiveDisplayName?.ifBlank { null } ?: if (isEnglish) "Partner" else "Người ấy",
            occasion = upcomingOccasionTitle,
            estimatedBudget = rec.priceRangeLabel,
            notes = listOf(rec.reason, rec.preparationPlan).filter { it.isNotBlank() }.joinToString(" • ")
          )
        }
      )
    }

    // 2. Category Filter Tabs
    item(contentType = "gift_category_tabs") {
      GiftCategoryFilterRow(
        selectedCategory = selectedCategory,
        isEnglish = isEnglish,
        onSelectCategory = viewModel::setGiftCategory
      )
    }

    // 3. VIP Featured Proposal Banner
    item(contentType = "gift_vip_proposal") {
      GiftVipProposalBanner(
        isEnglish = isEnglish,
        onOpenVipProposal = { viewModel.openVipProposal() }
      )
    }

    // 4. Section Title
    item(contentType = "gift_ideas_header") {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isEnglish) "Gift Preparation Ideas" else "Ý Tưởng Chuẩn Bị Quà",
          fontFamily = InLoveFonts.DisplaySerif,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = OnSurface,
        )
        Text("${filteredIdeas.size}", fontSize = 12.sp, color = TextSubtlePlum)
      }
    }

    // 5. Ideas Stream
    items(filteredIdeas, key = { it.id }, contentType = { "gift_idea_card" }) { idea ->
      GiftIdeaCard(
        idea = idea,
        isEnglish = isEnglish,
        onFavoriteToggle = { viewModel.toggleGiftFavorite(idea) },
        onActionClick = { viewModel.openGiftDetail(idea) }
      )
    }

    // 6. Interactive Checklist Section
    item(contentType = "gift_checklist") {
      GiftChecklistCard(
        checklistItems = checklistItems,
        completedCount = completedCount,
        totalCount = totalCount,
        isEnglish = isEnglish,
        onOpenAddDialog = { viewModel.openAddChecklistDialog() },
        onToggleItem = viewModel::toggleChecklist,
        onRenameItem = viewModel::renameChecklist,
        onRequestDeleteItem = { giftItemToDelete = it }
      )
    }

    // 6b. Saved Gift Reminders Section
    item(contentType = "gift_reminders") {
      GiftRemindersCard(
        giftReminders = giftReminders,
        isEnglish = isEnglish,
        onOpenAddDialog = { viewModel.openAddGiftReminderDialog() },
        onToggleReminder = viewModel::toggleGiftReminderCompleted,
        onUpdateReminder = viewModel::updateGiftReminder,
        onRequestDeleteReminder = { reminderToDelete = it }
      )
    }

    // 7. Dual Bottom Action Buttons
    item(contentType = "gift_bottom_actions") {
      GiftBottomActions(
        isEnglish = isEnglish,
        onOpenAlarm = {
          viewModel.openSetAlarmDialog(
            title = if (isEnglish) "Gift preparation reminder" else "Nhắc chuẩn bị quà tặng",
            message = if (isEnglish) "Prepare a thoughtful gift in time for your partner" else "Chuẩn bị sẵn món quà ý nghĩa cho người ấy đúng hẹn"
          )
        },
        onOpenWishlist = { showWishlistDialog = true }
      )
    }
  }

  if (showWishlistDialog) {
    WishlistNotebookDialog(
      giftIdeas = giftIdeas,
      isEnglish = isEnglish,
      onFavoriteToggle = { viewModel.toggleGiftFavorite(it) },
      onItemClick = { viewModel.openGiftDetail(it) },
      onDismiss = { showWishlistDialog = false }
    )
  }

  if (showDossierSheet) {
    PartnerDossierSheet(
      dossier = partnerDossier,
      isVip = isVip,
      isEnglish = isEnglish,
      onDismiss = { viewModel.closePartnerDossierSheet() },
      onSaveDossier = { viewModel.savePartnerDossier(it) },
      onAddSecretNote = { note, hint -> viewModel.addSecretSurpriseNote(note, hint) },
      onToggleSecretNotePurchased = { id, purchased -> viewModel.toggleSecretNotePurchased(id, purchased) },
      onRemoveSecretNote = { id -> viewModel.removeSecretNote(id) },
      onOpenPaywall = {
        viewModel.closePartnerDossierSheet()
        viewModel.setVipDialogVisible(true)
      }
    )
  }
}

@Composable
fun WishlistNotebookDialog(
  giftIdeas: List<GiftIdeaEntity>,
  isEnglish: Boolean,
  onFavoriteToggle: (GiftIdeaEntity) -> Unit,
  onItemClick: (GiftIdeaEntity) -> Unit,
  onDismiss: () -> Unit
) = WishlistNotebookDialogContent(giftIdeas, isEnglish, onFavoriteToggle, onItemClick, onDismiss)

@Composable
fun GiftIdeaCard(
  idea: GiftIdeaEntity,
  isEnglish: Boolean = false,
  onFavoriteToggle: () -> Unit,
  onActionClick: () -> Unit
) = GiftIdeaCardContent(idea, isEnglish, onFavoriteToggle, onActionClick)

@Composable
internal fun PersonalizedGiftCard(
  title: String,
  reason: String,
  preparation: String,
  priceLabel: String?,
  modifier: Modifier = Modifier,
  onShare: (() -> Unit)? = null,
  shareLabel: String = "",
  onScheduleReminder: (() -> Unit)? = null,
  reminderLabel: String = ""
) = PersonalizedGiftCardContent(
  title = title,
  reason = reason,
  preparation = preparation,
  priceLabel = priceLabel,
  modifier = modifier,
  onShare = onShare,
  shareLabel = shareLabel,
  onScheduleReminder = onScheduleReminder,
  reminderLabel = reminderLabel
)

@Composable
internal fun PersonalizedGiftList(
  suggestions: List<PersonalizedGiftSuggestion>,
  shareLabel: String,
  onShare: (String) -> Unit,
  onScheduleReminder: ((PersonalizedGiftSuggestion) -> Unit)? = null,
  reminderLabel: String = ""
) = PersonalizedGiftListContent(
  suggestions = suggestions,
  shareLabel = shareLabel,
  onShare = onShare,
  onScheduleReminder = onScheduleReminder,
  reminderLabel = reminderLabel
)

@Preview(showBackground = true)
@Composable
private fun PersonalizedGiftCardPreview() {
  PersonalizedGiftCard(
    title = "Bó hoa hồng bất ngờ",
    reason = "Hợp sở thích chung của hai bạn và dịp sắp tới.",
    preparation = "Còn khoảng hai tuần, đủ thời gian đặt và gói quà.",
    priceLabel = "Giá tham khảo: 200.000–500.000 ₫"
  )
}

@Composable
internal fun PartnerPreferencesEditor(
  profileExists: Boolean,
  savedLikes: String,
  savedBudgetVnd: Long,
  savedRegion: String,
  isEnglish: Boolean,
  onSave: (String, Long, String) -> Unit
) = PartnerPreferencesEditorContent(profileExists, savedLikes, savedBudgetVnd, savedRegion, isEnglish, onSave)

@Composable
internal fun IllustrationCaption(isEnglish: Boolean, modifier: Modifier = Modifier) =
  GiftIllustrationCaption(isEnglish, modifier)

@Composable
fun ChecklistRow(
  item: ChecklistItemEntity,
  isEnglish: Boolean = false,
  onToggle: () -> Unit,
  onEdit: (String) -> Unit,
  onDelete: () -> Unit
) = ChecklistRowContent(item, isEnglish, onToggle, onEdit, onDelete)

@Composable
fun GiftReminderRow(
  item: GiftReminderEntity,
  isEnglish: Boolean = false,
  onToggle: () -> Unit,
  onEdit: (GiftReminderEntity) -> Unit,
  onDelete: () -> Unit
) = GiftReminderRowContent(item, isEnglish, onToggle, onEdit, onDelete)
