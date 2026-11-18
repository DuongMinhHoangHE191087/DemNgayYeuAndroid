package com.example.ui.viewmodel.controllers

import com.example.data.model.OnlineUserEntity
import com.example.data.repository.AuthRepository
import com.example.data.repository.OnlineCoupleRepository
import com.example.shared.sync.CoupleCodeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SOLID controller handling 1-1 pairing search, invites, breakup lifecycle, PIN lock, and account deletion.
 */
class PairingAndAccountController(
  private val onlineRepo: OnlineCoupleRepository,
  private val authRepo: AuthRepository,
  private val scope: CoroutineScope,
  private val uiState: DialogUiStateController,
  private val isEnglishProvider: () -> Boolean,
) {
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchedUser = MutableStateFlow<OnlineUserEntity?>(null)
  val searchedUser: StateFlow<OnlineUserEntity?> = _searchedUser.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private fun tr(vi: String, en: String) = if (isEnglishProvider()) en else vi

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
    if (query.isBlank()) _searchedUser.value = null
  }

  fun performSearch() {
    val query = _searchQuery.value.trim()
    if (query.isBlank()) {
      uiState.showToast(tr("Vui lòng nhập mã ghép đôi, tên hoặc email!", "Enter a pairing code, name or email."))
      return
    }
    scope.launch {
      _isSearching.value = true
      val normalizedQuery = normalizeLinkOrKeepText(query)
      val user = onlineRepo.searchUserByCodeOrNameOrEmail(normalizedQuery)
      _searchedUser.value = user
      _isSearching.value = false
      if (user == null) {
        uiState.showToast(tr("Không tìm thấy người dùng với thông tin: $query", "No user found for: $query"))
      } else {
        uiState.showToast(tr("Đã tìm thấy thông tin của ${user.effectiveDisplayName}!", "Found ${user.effectiveDisplayName}!"))
      }
    }
  }

  private fun normalizeLinkOrKeepText(raw: String): String {
    val trimmed = raw.trim()
    val looksLikeLink = trimmed.contains("://") || trimmed.contains("code=", ignoreCase = true)
    return if (looksLikeLink) CoupleCodeParser.extractCode(trimmed).ifBlank { trimmed } else trimmed
  }

  suspend fun searchPartnerForSetLove(query: String): OnlineUserEntity? =
    onlineRepo.searchUserByCodeOrNameOrEmail(normalizeLinkOrKeepText(query))

  fun clearSearch() {
    _searchQuery.value = ""
    _searchedUser.value = null
  }

  fun sendSetLoveInvite(
    targetCodeOrLink: String,
    proposedStartDateMillis: Long,
    loveNote: String,
  ) {
    if (targetCodeOrLink.isBlank()) {
      uiState.showToast(tr("Vui lòng nhập mã ghép đôi hoặc dán link đối phương!", "Enter your partner's pairing code or paste their link."))
      return
    }
    val resolvedTarget = normalizeLinkOrKeepText(targetCodeOrLink)
    scope.launch {
      val (success, message) = onlineRepo.sendSetLoveInvite(resolvedTarget, proposedStartDateMillis, loveNote)
      uiState.showToast(message)
      if (success) {
        clearSearch()
        uiState.triggerFloatingHearts()
      }
    }
  }

  fun acceptSetLoveInvite(inviteId: String, confirmedStartDateMillis: Long?) {
    scope.launch {
      val (success, message) = onlineRepo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis)
      uiState.showToast(message)
      uiState.dismissInspectInvite()
      if (success) uiState.triggerFloatingHearts(isMilestone = true)
    }
  }

  fun rejectSetLoveInvite(inviteId: String) {
    scope.launch {
      val (_, message) = onlineRepo.rejectSetLoveInvite(inviteId)
      uiState.showToast(message)
      uiState.dismissInspectInvite()
    }
  }

  fun cancelSentInvite() {
    scope.launch {
      val (_, message) = onlineRepo.cancelSentInvite()
      uiState.showToast(message)
      clearSearch()
    }
  }

  fun requestBreakup() {
    scope.launch { uiState.showToast(onlineRepo.requestBreakup().second) }
  }

  fun confirmBreakup() {
    scope.launch { uiState.showToast(onlineRepo.confirmBreakup().second) }
  }

  fun rejectBreakup() {
    scope.launch { uiState.showToast(onlineRepo.rejectBreakup().second) }
  }

  fun forceBreakup() {
    scope.launch { uiState.showToast(onlineRepo.forceBreakup().second) }
  }

  fun toggleInterest(interestKey: String) {
    scope.launch {
      onlineRepo.toggleInterest(interestKey)
      uiState.showToast(tr("Đã cập nhật sở thích cá nhân", "Interests updated"))
    }
  }

  fun logout() {
    scope.launch {
      authRepo.logout()
      uiState.showToast(tr("Đã đăng xuất an toàn", "Signed out"))
    }
  }

  fun deleteAccountAndData(onComplete: (() -> Unit)?) {
    scope.launch {
      val result = authRepo.deleteCurrentAccount()
      if (result.isSuccess) {
        uiState.showToast(tr("Đã xóa toàn bộ tài khoản và dữ liệu thành công", "Account and data deleted"))
        onComplete?.invoke()
      } else {
        uiState.showToast(
          tr(
            "Không thể xóa tài khoản: ${result.exceptionOrNull()?.message ?: "Lỗi không xác định"}",
            "Couldn't delete account: ${result.exceptionOrNull()?.message ?: "unknown error"}",
          )
        )
      }
    }
  }

  fun lockApp() { authRepo.lockApp() }

  fun setAppPin(pin: String) {
    scope.launch { uiState.showToast(authRepo.setAppPin(pin).second) }
  }

  fun togglePinEnabled(enabled: Boolean) {
    scope.launch { uiState.showToast(authRepo.togglePinEnabled(enabled).second) }
  }
}
