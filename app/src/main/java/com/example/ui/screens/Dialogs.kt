@file:Suppress("FunctionName")
package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.model.GiftIdeaEntity
import com.example.ui.designsystem.inLoveOutlinedTextFieldColors
import com.example.ui.viewmodel.InLoveViewModel

@Composable
fun dialogTextFieldColors() = inLoveOutlinedTextFieldColors()

@Composable
fun AddReminderDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, dateText: String, note: String) -> Unit,
  isEnglish: Boolean = false
) = com.example.ui.dialogs.AddReminderDialog(
  onDismiss = onDismiss,
  onConfirm = onConfirm,
  isEnglish = isEnglish
)

@Composable
fun AddMilestoneDialog(
  presetPhotos: List<String> = emptyList(),
  isEnglish: Boolean = false,
  onDismiss: () -> Unit,
  onConfirm: (
    title: String,
    dateText: String,
    subtitle: String,
    categoryTag: String,
    secondaryTag: String,
    imageUrl: String,
    daysRemaining: Int,
    isImportant: Boolean
  ) -> Unit
) = com.example.ui.dialogs.AddMilestoneDialog(
  presetPhotos = presetPhotos,
  isEnglish = isEnglish,
  onDismiss = onDismiss,
  onConfirm = onConfirm
)

internal typealias MilestoneFormState = com.example.ui.dialogs.MilestoneFormState

@Composable
internal fun AddMilestoneFormBody(
  form: MilestoneFormState,
  presetPhotos: List<String>,
  isEnglish: Boolean
) = com.example.ui.dialogs.AddMilestoneFormBody(
  form = form,
  presetPhotos = presetPhotos,
  isEnglish = isEnglish
)

@Composable
fun AddChecklistDialog(
  onDismiss: () -> Unit,
  onConfirm: (text: String) -> Unit,
  isEnglish: Boolean = false
) = com.example.ui.dialogs.AddChecklistDialog(
  onDismiss = onDismiss,
  onConfirm = onConfirm,
  isEnglish = isEnglish
)

@Composable
fun GiftDetailDialog(
  gift: GiftIdeaEntity,
  onDismiss: () -> Unit,
  onToggleFavorite: () -> Unit,
  isEnglish: Boolean = false
) = com.example.ui.dialogs.GiftDetailDialog(
  gift = gift,
  onDismiss = onDismiss,
  onToggleFavorite = onToggleFavorite,
  isEnglish = isEnglish
)

@Composable
fun VipProposalDialog(
  onDismiss: () -> Unit,
  isEnglish: Boolean = false
) = com.example.ui.dialogs.VipProposalDialog(
  onDismiss = onDismiss,
  isEnglish = isEnglish
)

@Composable
fun EditCoupleDialog(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) = com.example.ui.dialogs.EditCoupleDialog(
  viewModel = viewModel,
  onDismiss = onDismiss
)

@Composable
internal fun CoupledProfileSummary(
  partnerName: String,
  partnerBirthday: String,
  partnerAvatarUrl: String,
  anniversary: String,
  isEnglish: Boolean
) = com.example.ui.dialogs.CoupledProfileSummary(
  partnerName = partnerName,
  partnerBirthday = partnerBirthday,
  partnerAvatarUrl = partnerAvatarUrl,
  anniversary = anniversary,
  isEnglish = isEnglish
)

@Composable
internal fun EditCoupleDialogContent(
  viewModel: InLoveViewModel,
  onDismiss: () -> Unit
) = com.example.ui.dialogs.EditCoupleDialogContent(
  viewModel = viewModel,
  onDismiss = onDismiss
)

@Composable
fun DeleteConfirmationDialog(
  title: String,
  message: String,
  itemName: String? = null,
  isEnglish: Boolean = false,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) = com.example.ui.dialogs.DeleteConfirmationDialog(
  title = title,
  message = message,
  itemName = itemName,
  isEnglish = isEnglish,
  onConfirm = onConfirm,
  onDismiss = onDismiss
)

@Composable
fun SetAlarmReminderDialog(
  initialTitle: String = "",
  initialMessage: String = "",
  reminderId: Long? = null,
  onDismiss: () -> Unit,
  onSchedule: (title: String, message: String, triggerMillis: Long, reminderId: Long?) -> Unit,
  onTestNow: (title: String, message: String) -> Unit,
  isEnglish: Boolean = false
) = com.example.ui.dialogs.SetAlarmReminderDialog(
  initialTitle = initialTitle,
  initialMessage = initialMessage,
  reminderId = reminderId,
  onDismiss = onDismiss,
  onSchedule = onSchedule,
  onTestNow = onTestNow,
  isEnglish = isEnglish
)

@Composable
fun EditLoveTitleDialog(
  currentTitle: String,
  isEnglish: Boolean = false,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) = com.example.ui.dialogs.EditLoveTitleDialog(
  currentTitle = currentTitle,
  isEnglish = isEnglish,
  onDismiss = onDismiss,
  onConfirm = onConfirm
)

internal val REMINDER_DAYS_BEFORE_OPTIONS: List<Int>
  get() = com.example.ui.dialogs.REMINDER_DAYS_BEFORE_OPTIONS

@Composable
fun AddAnniversaryDateDialog(
  isEnglish: Boolean = false,
  initialDateText: String? = null,
  onDismiss: () -> Unit,
  onConfirm: (
    title: String,
    dateText: String,
    type: String,
    description: String,
    isAnnual: Boolean,
    notificationEnabled: Boolean,
    reminderDaysBefore: Int
  ) -> Unit
) = com.example.ui.dialogs.AddAnniversaryDateDialog(
  isEnglish = isEnglish,
  initialDateText = initialDateText,
  onDismiss = onDismiss,
  onConfirm = onConfirm
)

@Composable
fun AddGiftReminderDialog(
  onDismiss: () -> Unit,
  onConfirm: (
    title: String,
    recipient: String,
    occasion: String,
    dueDateText: String,
    estimatedBudget: String,
    notes: String
  ) -> Unit,
  isEnglish: Boolean = false
) = com.example.ui.dialogs.AddGiftReminderDialog(
  onDismiss = onDismiss,
  onConfirm = onConfirm,
  isEnglish = isEnglish
)
