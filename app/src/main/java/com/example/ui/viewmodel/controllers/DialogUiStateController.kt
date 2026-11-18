package com.example.ui.viewmodel.controllers

import com.example.data.model.GiftIdeaEntity
import com.example.data.model.LoveBadgeEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.SharedMemoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-responsibility controller managing modal visibility, tabs, filters, toasts, and overlay triggers
 * so [com.example.ui.viewmodel.InLoveViewModel] stays lean and modular.
 */
class DialogUiStateController {
  private val _showVipDialog = MutableStateFlow(false)
  val showVipDialog: StateFlow<Boolean> = _showVipDialog.asStateFlow()

  private val _showPairingScreen = MutableStateFlow(false)
  val showPairingScreen: StateFlow<Boolean> = _showPairingScreen.asStateFlow()

  private val _selectedTab = MutableStateFlow(0)
  val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

  private val _calendarFilter = MutableStateFlow("ALL")
  val calendarFilter: StateFlow<String> = _calendarFilter.asStateFlow()

  private val _giftCategory = MutableStateFlow("Tất cả")
  val giftCategory: StateFlow<String> = _giftCategory.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  private val _isAllNotificationsRead = MutableStateFlow(false)
  val isAllNotificationsRead: StateFlow<Boolean> = _isAllNotificationsRead.asStateFlow()

  private val _showNotifications = MutableStateFlow(false)
  val showNotifications: StateFlow<Boolean> = _showNotifications.asStateFlow()

  private val _showAddReminderDialog = MutableStateFlow(false)
  val showAddReminderDialog: StateFlow<Boolean> = _showAddReminderDialog.asStateFlow()

  private val _showAddMilestoneDialog = MutableStateFlow(false)
  val showAddMilestoneDialog: StateFlow<Boolean> = _showAddMilestoneDialog.asStateFlow()

  private val _showAddChecklistDialog = MutableStateFlow(false)
  val showAddChecklistDialog: StateFlow<Boolean> = _showAddChecklistDialog.asStateFlow()

  private val _showAddAnniversaryDialog = MutableStateFlow(false)
  val showAddAnniversaryDialog: StateFlow<Boolean> = _showAddAnniversaryDialog.asStateFlow()

  private val _prefilledAnniversaryDate = MutableStateFlow<String?>(null)
  val prefilledAnniversaryDate: StateFlow<String?> = _prefilledAnniversaryDate.asStateFlow()

  private val _showAddGiftReminderDialog = MutableStateFlow(false)
  val showAddGiftReminderDialog: StateFlow<Boolean> = _showAddGiftReminderDialog.asStateFlow()

  private val _showSetAlarmDialog = MutableStateFlow(false)
  val showSetAlarmDialog: StateFlow<Boolean> = _showSetAlarmDialog.asStateFlow()

  private val _alarmDialogPresetTitle = MutableStateFlow("")
  val alarmDialogPresetTitle: StateFlow<String> = _alarmDialogPresetTitle.asStateFlow()

  private val _alarmDialogPresetMessage = MutableStateFlow("")
  val alarmDialogPresetMessage: StateFlow<String> = _alarmDialogPresetMessage.asStateFlow()

  private val _alarmDialogReminderId = MutableStateFlow<Long?>(null)
  val alarmDialogReminderId: StateFlow<Long?> = _alarmDialogReminderId.asStateFlow()

  private val _selectedGiftDetail = MutableStateFlow<GiftIdeaEntity?>(null)
  val selectedGiftDetail: StateFlow<GiftIdeaEntity?> = _selectedGiftDetail.asStateFlow()

  private val _showVipProposalDetail = MutableStateFlow(false)
  val showVipProposalDetail: StateFlow<Boolean> = _showVipProposalDetail.asStateFlow()

  private val _showLanguageDialog = MutableStateFlow(false)
  val showLanguageDialog: StateFlow<Boolean> = _showLanguageDialog.asStateFlow()

  private val _showGuideDialog = MutableStateFlow(false)
  val showGuideDialog: StateFlow<Boolean> = _showGuideDialog.asStateFlow()

  private val _showWallpaperDialog = MutableStateFlow(false)
  val showWallpaperDialog: StateFlow<Boolean> = _showWallpaperDialog.asStateFlow()

  private val _showMemoryDialog = MutableStateFlow(false)
  val showMemoryDialog: StateFlow<Boolean> = _showMemoryDialog.asStateFlow()

  private val _floatingHeartsTrigger = MutableStateFlow(1L)
  val floatingHeartsTrigger: StateFlow<Long> = _floatingHeartsTrigger.asStateFlow()

  private val _isMilestoneCelebration = MutableStateFlow(false)
  val isMilestoneCelebration: StateFlow<Boolean> = _isMilestoneCelebration.asStateFlow()

  private val _selectedMemoryDetail = MutableStateFlow<SharedMemoryEntity?>(null)
  val selectedMemoryDetail: StateFlow<SharedMemoryEntity?> = _selectedMemoryDetail.asStateFlow()

  private val _selectedAnniversaryFilter = MutableStateFlow("Tất cả")
  val selectedAnniversaryFilter: StateFlow<String> = _selectedAnniversaryFilter.asStateFlow()

  private val _selectedBadge = MutableStateFlow<LoveBadgeEntity?>(null)
  val selectedBadge: StateFlow<LoveBadgeEntity?> = _selectedBadge.asStateFlow()

  private val _showBadgeShowcaseDialog = MutableStateFlow(false)
  val showBadgeShowcaseDialog: StateFlow<Boolean> = _showBadgeShowcaseDialog.asStateFlow()

  private val _showEditCoupleDialog = MutableStateFlow(false)
  val showEditCoupleDialog: StateFlow<Boolean> = _showEditCoupleDialog.asStateFlow()

  private val _showAuthScreen = MutableStateFlow(false)
  val showAuthScreen: StateFlow<Boolean> = _showAuthScreen.asStateFlow()

  private val _showEditProfileDialog = MutableStateFlow(false)
  val showEditProfileDialog: StateFlow<Boolean> = _showEditProfileDialog.asStateFlow()

  private val _selectedInviteForVerification = MutableStateFlow<OnlineInviteEntity?>(null)
  val selectedInviteForVerification: StateFlow<OnlineInviteEntity?> = _selectedInviteForVerification.asStateFlow()

  fun setVipDialogVisible(visible: Boolean) { _showVipDialog.value = visible }
  fun openPairingScreen() { _showPairingScreen.value = true }
  fun closePairingScreen() { _showPairingScreen.value = false }
  fun setTab(index: Int) { _selectedTab.value = index }
  fun setCalendarFilter(filter: String) { _calendarFilter.value = filter }
  fun setGiftCategory(cat: String) { _giftCategory.value = cat }
  fun showToast(msg: String) { _toastMessage.value = msg }
  fun clearToast() { _toastMessage.value = null }
  fun setShowNotifications(show: Boolean) { _showNotifications.value = show }
  fun markAllNotificationsRead() { _isAllNotificationsRead.value = true }
  fun selectAnniversaryFilter(filter: String) { _selectedAnniversaryFilter.value = filter }

  fun triggerFloatingHearts(isMilestone: Boolean = false, nowMs: Long = System.currentTimeMillis()) {
    _isMilestoneCelebration.value = isMilestone
    _floatingHeartsTrigger.value = nowMs
  }

  fun openAuthScreen() { _showAuthScreen.value = true }
  fun closeAuthScreen() { _showAuthScreen.value = false }
  fun openEditCoupleDialog() { _showEditCoupleDialog.value = true }
  fun closeEditCoupleDialog() { _showEditCoupleDialog.value = false }
  fun openEditProfileDialog() { _showEditProfileDialog.value = true }
  fun closeEditProfileDialog() { _showEditProfileDialog.value = false }

  fun openAddReminderDialog() { _showAddReminderDialog.value = true }
  fun closeAddReminderDialog() { _showAddReminderDialog.value = false }
  fun openAddMilestoneDialog() { _showAddMilestoneDialog.value = true }
  fun closeAddMilestoneDialog() { _showAddMilestoneDialog.value = false }
  fun openAddChecklistDialog() { _showAddChecklistDialog.value = true }
  fun closeAddChecklistDialog() { _showAddChecklistDialog.value = false }

  fun openAddAnniversaryDialog(initialDate: String? = null) {
    _prefilledAnniversaryDate.value = initialDate
    _showAddAnniversaryDialog.value = true
  }

  fun closeAddAnniversaryDialog() {
    _showAddAnniversaryDialog.value = false
    _prefilledAnniversaryDate.value = null
  }

  fun openAddGiftReminderDialog() { _showAddGiftReminderDialog.value = true }
  fun closeAddGiftReminderDialog() { _showAddGiftReminderDialog.value = false }

  fun openSetAlarmDialog(title: String = "", message: String = "", reminderId: Long? = null) {
    _alarmDialogPresetTitle.value = title
    _alarmDialogPresetMessage.value = message
    _alarmDialogReminderId.value = reminderId
    _showSetAlarmDialog.value = true
  }

  fun closeSetAlarmDialog() { _showSetAlarmDialog.value = false }

  fun openGiftDetail(item: GiftIdeaEntity) { _selectedGiftDetail.value = item }
  fun closeGiftDetail() { _selectedGiftDetail.value = null }
  fun syncGiftDetailFavorite(item: GiftIdeaEntity) {
    if (_selectedGiftDetail.value?.id == item.id) {
      _selectedGiftDetail.value = item.copy(isFavorited = !item.isFavorited)
    }
  }

  fun openVipProposal() { _showVipProposalDetail.value = true }
  fun closeVipProposal() { _showVipProposalDetail.value = false }
  fun openLanguageDialog() { _showLanguageDialog.value = true }
  fun closeLanguageDialog() { _showLanguageDialog.value = false }
  fun openGuideDialog() { _showGuideDialog.value = true }
  fun closeGuideDialog() { _showGuideDialog.value = false }
  fun openWallpaperDialog() { _showWallpaperDialog.value = true }
  fun closeWallpaperDialog() { _showWallpaperDialog.value = false }
  fun openMemoryDialog() { _showMemoryDialog.value = true }
  fun closeMemoryDialog() { _showMemoryDialog.value = false }

  fun openMemoryDetail(memory: SharedMemoryEntity) { _selectedMemoryDetail.value = memory }
  fun closeMemoryDetail() { _selectedMemoryDetail.value = null }
  fun syncMemoryDetail(memory: SharedMemoryEntity) {
    if (_selectedMemoryDetail.value?.id == memory.id) _selectedMemoryDetail.value = memory
  }
  fun clearMemoryDetailIfId(id: Long) {
    if (_selectedMemoryDetail.value?.id == id) _selectedMemoryDetail.value = null
  }

  fun selectBadge(badge: LoveBadgeEntity?) { _selectedBadge.value = badge }
  fun openBadgeShowcase() { _showBadgeShowcaseDialog.value = true }
  fun closeBadgeShowcase() { _showBadgeShowcaseDialog.value = false }
  fun inspectInvite(invite: OnlineInviteEntity) { _selectedInviteForVerification.value = invite }
  fun dismissInspectInvite() { _selectedInviteForVerification.value = null }
}
