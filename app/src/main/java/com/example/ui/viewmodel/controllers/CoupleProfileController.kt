package com.example.ui.viewmodel.controllers

import android.app.Application
import com.example.data.db.InLoveDao
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.RelationshipStatus
import com.example.data.repository.InLoveRepository
import com.example.data.repository.OnlineCoupleRepository
import com.example.ui.util.Birthday
import com.example.ui.util.DEFAULT_LOVE_TITLE
import com.example.ui.util.ProfileUtils
import com.example.ui.util.cleanName
import com.example.ui.util.isProfileChanged
import com.example.ui.util.profileFieldAfter
import com.example.ui.util.profileUidOf
import com.example.ui.util.roomOwnsCard
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SOLID controller owning couple card state (`boy*`, `girl*`, `loveTitle`, `loveDays`,
 * `anniversaryDate`), Room/Online reconciliation, and local couple profile mutations.
 */
class CoupleProfileController(
  private val application: Application,
  private val repository: InLoveRepository,
  private val dao: InLoveDao,
  private val onlineRepo: OnlineCoupleRepository,
  private val scope: CoroutineScope,
  private val uiState: DialogUiStateController,
  private val isEnglishProvider: () -> Boolean,
) {
  private val _roomSeen = MutableStateFlow(false)
  val roomSeen: StateFlow<Boolean> = _roomSeen.asStateFlow()

  private val _boyName = MutableStateFlow("")
  val boyName: StateFlow<String> = _boyName.asStateFlow()

  private val _boyBirthDate = MutableStateFlow("")
  val boyBirthDate: StateFlow<String> = _boyBirthDate.asStateFlow()

  private val _boyAge = MutableStateFlow(0)
  val boyAge: StateFlow<Int> = _boyAge.asStateFlow()

  private val _boyZodiac = MutableStateFlow("")
  val boyZodiac: StateFlow<String> = _boyZodiac.asStateFlow()

  private val _boyAvatarUrl = MutableStateFlow("")
  val boyAvatarUrl: StateFlow<String> = _boyAvatarUrl.asStateFlow()

  private val _girlName = MutableStateFlow("")
  val girlName: StateFlow<String> = _girlName.asStateFlow()

  private val _girlBirthDate = MutableStateFlow("")
  val girlBirthDate: StateFlow<String> = _girlBirthDate.asStateFlow()

  private val _girlAge = MutableStateFlow(0)
  val girlAge: StateFlow<Int> = _girlAge.asStateFlow()

  private val _girlZodiac = MutableStateFlow("")
  val girlZodiac: StateFlow<String> = _girlZodiac.asStateFlow()

  private val _girlAvatarUrl = MutableStateFlow("")
  val girlAvatarUrl: StateFlow<String> = _girlAvatarUrl.asStateFlow()

  private val _loveTitle = MutableStateFlow(DEFAULT_LOVE_TITLE)
  val loveTitle: StateFlow<String> = _loveTitle.asStateFlow()

  private val _loveDays = MutableStateFlow(1)
  val loveDays: StateFlow<Int> = _loveDays.asStateFlow()

  private val _anniversaryDate = MutableStateFlow("Hôm nay")
  val anniversaryDate: StateFlow<String> = _anniversaryDate.asStateFlow()

  private var roomCouple: CoupleProfileEntity? = null

  private fun tr(vi: String, en: String) = if (isEnglishProvider()) en else vi

  private fun resetBoyCard() {
    val room = roomCouple
    _boyName.value = room?.partner1Name ?: ""
    _boyBirthDate.value = room?.partner1Birthday ?: ""
    _boyAvatarUrl.value = room?.partner1ProfilePicture ?: ""
    _boyAge.value = room?.partner1Age ?: 0
    _boyZodiac.value = room?.partner1Zodiac ?: ""
  }

  private fun resetGirlCard(
    room: CoupleProfileEntity? = roomCouple.takeIf { profileUidOf(onlineRepo.currentUser.value.uid) == null },
  ) {
    _girlName.value = room?.partner2Name ?: ""
    _girlBirthDate.value = room?.partner2Birthday ?: ""
    _girlAvatarUrl.value = room?.partner2ProfilePicture ?: ""
    _girlAge.value = room?.partner2Age ?: 0
    _girlZodiac.value = room?.partner2Zodiac ?: ""
  }

  fun startCollectors() {
    scope.launch {
      var hadProfile = false
      combine(repository.coupleProfile, dao.observeRelationships()) { p, rels -> p to rels }
        .collect { (profile, rels) ->
          _roomSeen.value = true
          val ended = rels.isNotEmpty() && rels.none { it.status != RelationshipStatus.TERMINATED }
          roomCouple = profile
          val ownsBoy = roomOwnsCard(onlineRepo.currentUser.value.uid)
          val signedIn = profileUidOf(onlineRepo.currentUser.value.uid) != null
          val ownsGirl = roomOwnsCard(onlineRepo.partnerUser.value?.uid) && !signedIn && !ended
          val ownsDate = onlineRepo.activeRelationship.value == null && rels.isEmpty()

          if (ended && onlineRepo.activeRelationship.value == null) {
            _loveDays.value = 1
            _anniversaryDate.value = "Hôm nay"
            if (onlineRepo.partnerUser.value == null) resetGirlCard(null)
          }
          if (profile == null) {
            if (!hadProfile) return@collect
            hadProfile = false
            if (ownsBoy) resetBoyCard()
            if (ownsGirl) resetGirlCard()
            _loveTitle.value = DEFAULT_LOVE_TITLE
            if (ownsDate) {
              _loveDays.value = 1
              _anniversaryDate.value = "Hôm nay"
            }
          } else {
            hadProfile = true
            if (ownsBoy) {
              _boyName.value = profile.partner1Name
              _boyBirthDate.value = profile.partner1Birthday
              _boyAvatarUrl.value = profile.partner1ProfilePicture
              _boyAge.value = profile.partner1Age
              _boyZodiac.value = profile.partner1Zodiac
            }
            if (ownsGirl) {
              _girlName.value = profile.partner2Name
              _girlBirthDate.value = profile.partner2Birthday
              _girlAvatarUrl.value = profile.partner2ProfilePicture
              _girlAge.value = profile.partner2Age
              _girlZodiac.value = profile.partner2Zodiac
            }
            _loveTitle.value = profile.loveTitle
            if (ownsDate) {
              _loveDays.value = ProfileUtils.loveDaysOn(profile.anniversaryDate) ?: profile.loveDays
              _anniversaryDate.value = profile.anniversaryDate
            }
          }
        }
    }

    scope.launch {
      var applied: String? = null
      onlineRepo.currentUser.collect { user ->
        val uid = profileUidOf(user.uid)
        if (isProfileChanged(applied, uid)) resetBoyCard()
        val sameProfile = applied == uid
        applied = uid
        if (uid == null) return@collect
        val name = user.effectiveDisplayName
        _boyName.value = profileFieldAfter(_boyName.value, name, name.isBlank(), sameProfile)
        _boyBirthDate.value = profileFieldAfter(_boyBirthDate.value, user.birthDate, user.birthDate.isBlank(), sameProfile)
        _boyAvatarUrl.value = profileFieldAfter(_boyAvatarUrl.value, user.avatarUrl, user.avatarUrl.isBlank(), sameProfile)
        _boyAge.value = profileFieldAfter(_boyAge.value, user.age, user.age <= 0, sameProfile)
        _boyZodiac.value = profileFieldAfter(_boyZodiac.value, user.zodiac, user.zodiac.isBlank(), sameProfile)
      }
    }

    scope.launch {
      var applied: String? = null
      onlineRepo.partnerUser.collect { partner ->
        val uid = profileUidOf(partner?.uid)
        if (isProfileChanged(applied, uid)) resetGirlCard()
        val sameProfile = applied == uid
        applied = uid
        if (partner == null || uid == null) return@collect
        val partnerName = partner.effectiveDisplayName
        _girlName.value = profileFieldAfter(_girlName.value, partnerName, partnerName.isBlank(), sameProfile)
        _girlBirthDate.value = profileFieldAfter(_girlBirthDate.value, partner.birthDate, partner.birthDate.isBlank(), sameProfile)
        _girlAvatarUrl.value = profileFieldAfter(_girlAvatarUrl.value, partner.avatarUrl, partner.avatarUrl.isBlank(), sameProfile)
        _girlAge.value = profileFieldAfter(_girlAge.value, partner.age, partner.age <= 0, sameProfile)
        _girlZodiac.value = profileFieldAfter(_girlZodiac.value, partner.zodiac, partner.zodiac.isBlank(), sameProfile)
      }
    }

    scope.launch {
      onlineRepo.activeRelationship.collect { rel ->
        if (rel != null) {
          if (rel.startDateText.isNotBlank()) _anniversaryDate.value = rel.startDateText
          relationshipLoveDays(rel)?.let { _loveDays.value = it }
        }
      }
    }
  }

  private fun isPairedNow(): Boolean =
    onlineRepo.activeRelationship.value != null ||
      onlineRepo.currentUser.value.status == com.example.data.model.OnlineStatus.COUPLED

  private fun relationshipLoveDays(rel: OnlineRelationshipEntity): Int? =
    ProfileUtils.calculateLoveDays(rel.startDate).takeIf { it > 0 }

  fun refreshLoveDays() {
    val rel = onlineRepo.activeRelationship.value
    val days = if (rel != null) relationshipLoveDays(rel) else ProfileUtils.loveDaysOn(_anniversaryDate.value)
    if (days != null) _loveDays.value = days
  }

  private suspend fun currentOrNewCoupleRow(): CoupleProfileEntity =
    repository.getCoupleProfileSync() ?: CoupleProfileEntity(
      id = 1,
      partner1Name = _boyName.value,
      partner1Birthday = _boyBirthDate.value,
      partner1ProfilePicture = _boyAvatarUrl.value,
      partner1Age = _boyAge.value,
      partner1Zodiac = _boyZodiac.value,
      partner2Name = _girlName.value,
      partner2Birthday = _girlBirthDate.value,
      partner2ProfilePicture = _girlAvatarUrl.value,
      partner2Age = _girlAge.value,
      partner2Zodiac = _girlZodiac.value,
      loveTitle = _loveTitle.value.ifBlank { DEFAULT_LOVE_TITLE },
      loveDays = 0,
      anniversaryDate = "",
    )

  fun saveCoupleProfile(
    girl: String,
    girlBirth: String,
    girlAvatar: String,
    girlA: Int,
    girlZod: String,
    title: String,
    days: Int,
    anniversary: String,
    loveEnded: Boolean,
  ) {
    if (isPairedNow()) {
      uiState.showToast(
        tr(
          "Bạn đang ghép đôi nên hồ sơ đôi đồng bộ theo ghép đôi, không sửa ở đây",
          "You're paired, so the couple profile syncs from pairing and can't be edited here",
        )
      )
      return
    }
    if (loveEnded) {
      updateLoveTitle(title)
      uiState.closeEditCoupleDialog()
      return
    }
    val cleanGirl = cleanName(girl)
    val cleanGirlAvatar = girlAvatar.trim()
    val cleanGirlZod = girlZod.trim()
    val cleanTitle = title.trim().ifEmpty { DEFAULT_LOVE_TITLE }
    val cleanAnniversary = if (anniversary.isBlank()) "" else {
      ProfileUtils.normalizeAnniversaryInput(anniversary) ?: run {
        uiState.showToast(tr("Ngày không hợp lệ (dd/MM/yyyy, không ở tương lai)", "Invalid date (dd/MM/yyyy, not in the future)"))
        return
      }
    }

    scope.launch {
      val existing = repository.getCoupleProfileSync()
      val parsed = Birthday.parse(girlBirth)
      val stored = existing?.takeIf { parsed == null && girlBirth.isNotBlank() && girlBirth.trim() == it.partner2Birthday.trim() }
      val cleanGirlBirth = parsed?.text ?: stored?.partner2Birthday ?: ""
      val finalGirlAge = stored?.partner2Age ?: girlA
      val finalGirlZod = stored?.partner2Zodiac ?: cleanGirlZod
      val entity = CoupleProfileEntity(
        id = 1,
        likesCsv = existing?.likesCsv ?: "",
        budgetMaxVnd = existing?.budgetMaxVnd ?: 0,
        occasionRegion = existing?.occasionRegion ?: "",
        partner1Name = existing?.partner1Name ?: _boyName.value,
        partner1Birthday = existing?.partner1Birthday ?: _boyBirthDate.value,
        partner1ProfilePicture = existing?.partner1ProfilePicture ?: _boyAvatarUrl.value,
        partner1Age = existing?.partner1Age ?: _boyAge.value,
        partner1Zodiac = existing?.partner1Zodiac ?: _boyZodiac.value,
        partner2Name = cleanGirl,
        partner2Birthday = cleanGirlBirth,
        partner2ProfilePicture = cleanGirlAvatar,
        partner2Age = finalGirlAge,
        partner2Zodiac = finalGirlZod,
        loveTitle = cleanTitle,
        loveDays = days,
        anniversaryDate = cleanAnniversary,
      )
      repository.saveCoupleProfile(entity)
      _girlName.value = cleanGirl
      _girlBirthDate.value = cleanGirlBirth
      _girlAvatarUrl.value = cleanGirlAvatar
      _girlAge.value = finalGirlAge
      _girlZodiac.value = finalGirlZod
      _loveTitle.value = cleanTitle
      _loveDays.value = days
      _anniversaryDate.value = cleanAnniversary
      uiState.closeEditCoupleDialog()
      uiState.triggerFloatingHearts()
      uiState.showToast(tr("Đã lưu hồ sơ 2 bạn thành công! ❤️", "Your couple profile was saved! ❤️"))
    }
  }

  fun completeOnboardingProfile(me: String, partner: String, title: String, anniversaryDate: String = "") {
    val cleanMe = cleanName(me)
    val cleanPartner = cleanName(partner)
    val cleanTitle = title.trim().ifEmpty { DEFAULT_LOVE_TITLE }
    val cleanDate = anniversaryDate.trim().takeIf { it.isNotEmpty() }
      ?.let { ProfileUtils.normalizeAnniversaryInput(it) }
    scope.launch {
      val existing = repository.getCoupleProfileSync()
      val resolvedDate = cleanDate ?: existing?.anniversaryDate.orEmpty()
      val resolvedDays = cleanDate?.let { calculateLoveDaysFromDate(it) } ?: (existing?.loveDays ?: 0)
      val row = existing?.copy(
        partner2Name = cleanPartner.ifEmpty { existing.partner2Name },
        loveTitle = cleanTitle,
        anniversaryDate = resolvedDate,
        loveDays = resolvedDays,
        updatedAt = System.currentTimeMillis()
      ) ?: CoupleProfileEntity(
        id = 1,
        partner1Name = _boyName.value,
        partner1Birthday = _boyBirthDate.value,
        partner1ProfilePicture = _boyAvatarUrl.value,
        partner1Age = _boyAge.value,
        partner1Zodiac = _boyZodiac.value,
        partner2Name = cleanPartner,
        partner2Birthday = "",
        partner2ProfilePicture = "",
        loveTitle = cleanTitle,
        loveDays = resolvedDays,
        anniversaryDate = resolvedDate,
      )
      repository.saveCoupleProfile(row)
      if (cleanDate != null) {
        _anniversaryDate.value = cleanDate
        _loveDays.value = resolvedDays
      }
      if (cleanMe.isNotBlank()) {
        val u = onlineRepo.currentUser.value
        val (ok, msg) = onlineRepo.updateMyProfile(cleanMe, u.birthDate, u.avatarUrl, u.gender, u.bio)
        if (!ok) uiState.showToast(msg)
        showMeOnCard()
      }
    }
  }

  fun updateLoveTitle(newTitle: String) {
    val cleanTitle = newTitle.trim().ifEmpty { DEFAULT_LOVE_TITLE }
    scope.launch {
      repository.saveCoupleProfile(currentOrNewCoupleRow().copy(loveTitle = cleanTitle, updatedAt = System.currentTimeMillis()))
      _loveTitle.value = cleanTitle
      uiState.showToast(
        tr("Đã đổi tên hành trình: \"$cleanTitle\" ❤️", "Love Journey title updated: \"$cleanTitle\" ❤️")
      )
    }
  }

  fun calculateLoveDaysFromDate(dateStr: String): Int =
    ProfileUtils.loveDaysOn(dateStr) ?: _loveDays.value

  fun setAnniversaryAndRecalculateDays(newDate: String, loveEnded: Boolean) {
    if (onlineRepo.activeRelationship.value != null) {
      uiState.showToast(tr("Ngày yêu đang đồng bộ theo ghép đôi, không đổi được ở đây", "Your love date syncs from pairing and can't be changed here"))
      return
    }
    if (loveEnded) {
      uiState.showToast(tr("Hiện chưa có mối quan hệ nào đang hoạt động.", "No active relationship right now."))
      return
    }
    val cleanDate = ProfileUtils.normalizeAnniversaryInput(newDate) ?: run {
      uiState.showToast(tr("Ngày không hợp lệ (dd/MM/yyyy, không ở tương lai)", "Invalid date (dd/MM/yyyy, not in the future)"))
      return
    }
    val calcDays = calculateLoveDaysFromDate(cleanDate)
    scope.launch {
      repository.saveCoupleProfile(currentOrNewCoupleRow().copy(anniversaryDate = cleanDate, loveDays = calcDays, updatedAt = System.currentTimeMillis()))
      _anniversaryDate.value = cleanDate
      _loveDays.value = calcDays
      uiState.triggerFloatingHearts()
      uiState.showToast(
        tr("Đã tính lại: $calcDays ngày yêu nhau từ ngày $cleanDate! 💕", "Recalculated: $calcDays days together since $cleanDate! 💕")
      )
    }
  }

  fun syncCalculatedDaysToHome(days: Int) {
    if (days <= 0) return
    _loveDays.value = days
    scope.launch {
      val current = repository.coupleProfile.first()
      if (current != null) {
        repository.saveCoupleProfile(current.copy(loveDays = days, updatedAt = System.currentTimeMillis()))
      }
      uiState.triggerFloatingHearts()
      uiState.showToast(
        tr("Đã đồng bộ $days ngày yêu lên trang chủ! ❤️", "Synced $days love days to main screen! ❤️")
      )
    }
  }

  fun updateMyProfile(
    name: String,
    birthDate: String,
    avatarUrl: String,
    gender: String,
    bio: String,
    clearBirthday: Boolean = false,
  ) {
    scope.launch {
      val oldAvatar = onlineRepo.currentUser.value.avatarUrl
      val (success, message) = onlineRepo.updateMyProfile(name, birthDate, avatarUrl, gender, bio, clearBirthday)
      uiState.showToast(message)
      if (success) {
        withContext(Dispatchers.IO) {
          ProfileUtils.staleLocalAvatar(
            oldAvatar,
            avatarUrl.trim(),
            File(application.filesDir, "avatars"),
          )?.delete()
        }
        showMeOnCard()
        uiState.closeEditProfileDialog()
        uiState.triggerFloatingHearts()
      }
    }
  }

  private fun showMeOnCard() {
    if (roomOwnsCard(onlineRepo.currentUser.value.uid)) {
      val me = onlineRepo.currentUser.value
      _boyName.value = me.effectiveDisplayName
      _boyBirthDate.value = me.birthDate
      _boyAge.value = me.age
      _boyZodiac.value = me.zodiac
      _boyAvatarUrl.value = me.avatarUrl
    }
  }
}
