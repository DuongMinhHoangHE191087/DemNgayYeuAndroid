package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import com.android.billingclient.api.ProductDetails
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.CustomReminderEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.data.model.LoveBadgeEntity
import com.example.data.model.MilestoneEntity
import com.example.data.model.PRIVACY_PRIVATE
import com.example.data.model.ReminderCadenceEntity
import com.example.data.model.SharedMemoryEntity
import com.example.alarm.AlarmNotificationScheduler
import com.example.data.repository.InLoveRepository
import com.example.domain.media.MediaKind
import com.example.domain.media.MemoryMediaFile
import com.example.domain.media.ResolveMemoryMediaUrlUseCase
import com.example.domain.media.SaveSharedMemoryOutcome
import com.example.domain.media.SaveSharedMemoryUseCase
import com.example.domain.media.UploadMemoryMediaUseCase
import com.example.domain.media.formatMediaSize
import com.example.domain.usecase.GetPersonalizedGiftSuggestionsUseCase
import com.example.domain.usecase.PersonalizedGiftSuggestion
import com.example.ui.util.AppLanguage
import com.example.ui.util.isProfileChanged
import com.example.ui.util.profileFieldAfter
import com.example.ui.util.profileUidOf
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InLoveViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: InLoveRepository
  val onlineRepo: com.example.data.repository.OnlineCoupleRepository
  val authRepo: com.example.data.repository.AuthRepository
  val authState: StateFlow<com.example.data.repository.AuthState>

  // by lazy: AppServiceLocator.initialize() chạy trong init bên dưới, sau khi các property khai báo ở đây đã được tạo.
  private val saveSharedMemoryUseCase by lazy { SaveSharedMemoryUseCase(UploadMemoryMediaUseCase(com.example.di.AppServiceLocator.memoryMediaRepository)) }
  private val resolveMemoryMediaUrlUseCase by lazy { ResolveMemoryMediaUrlUseCase(com.example.di.AppServiceLocator.memoryMediaRepository) }

  private val _upcomingMilestones = MutableStateFlow<List<com.example.alarm.LoveMilestoneInfo>>(emptyList())
  val upcomingMilestones: StateFlow<List<com.example.alarm.LoveMilestoneInfo>> = _upcomingMilestones.asStateFlow()

  val milestones: StateFlow<List<MilestoneEntity>>
  val giftIdeas: StateFlow<List<GiftIdeaEntity>>
  val personalizedGifts: StateFlow<List<PersonalizedGiftSuggestion>>
  val checklistItems: StateFlow<List<ChecklistItemEntity>>
  val customReminders: StateFlow<List<CustomReminderEntity>>
  val reminderCadences: StateFlow<List<ReminderCadenceEntity>>
  val coupleProfile: StateFlow<CoupleProfileEntity?>
  val sharedMemories: StateFlow<List<SharedMemoryEntity>>
  val loveBadges: StateFlow<List<LoveBadgeEntity>>
  val anniversaryDates: StateFlow<List<AnniversaryDateEntity>>
  val giftReminders: StateFlow<List<GiftReminderEntity>>
  val presetPhotos: StateFlow<List<String>>
  val presetAvatars: StateFlow<List<String>>
  val presetWallpapers: StateFlow<List<String>>

  // Online 1-1 Set Love StateFlows
  val currentOnlineUser: StateFlow<com.example.data.model.OnlineUserEntity>
  val partnerOnlineUser: StateFlow<com.example.data.model.OnlineUserEntity?>
  val activeRelationship: StateFlow<com.example.data.model.OnlineRelationshipEntity?>
  val relationshipStatus: StateFlow<String>
  val mutualInterests: StateFlow<Set<String>>
  val incomingInvite: StateFlow<com.example.data.model.OnlineInviteEntity?>
  val outgoingInvite: StateFlow<com.example.data.model.OnlineInviteEntity?>

  // RBAC & VIP Subscription StateFlows
  val userRole: StateFlow<com.example.data.model.UserRole>
  val subscriptionTier: StateFlow<com.example.data.model.SubscriptionTier>
  val isVip: StateFlow<Boolean>

  // Live connectivity — backed by AppServiceLocator's single process-wide NetworkMonitor
  // (was already registered and running, just never read by any UI before now).
  val isOnline: StateFlow<Boolean>

  private val _showVipDialog = MutableStateFlow(false)
  val showVipDialog: StateFlow<Boolean> = _showVipDialog.asStateFlow()

  fun setVipDialogVisible(visible: Boolean) {
    _showVipDialog.value = visible
  }

  private val _showPairingScreen = MutableStateFlow(false)
  val showPairingScreen: StateFlow<Boolean> = _showPairingScreen.asStateFlow()

  fun openPairingScreen() { _showPairingScreen.value = true }
  fun closePairingScreen() { _showPairingScreen.value = false }

  private val _selectedTab = MutableStateFlow(0) // Default to Love Screen matching image.png!
  val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

  private val _calendarFilter = MutableStateFlow("Tất cả (4)")
  val calendarFilter: StateFlow<String> = _calendarFilter.asStateFlow()

  private val _giftCategory = MutableStateFlow("Tất cả")
  val giftCategory: StateFlow<String> = _giftCategory.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  private val _isAllNotificationsRead = MutableStateFlow(false)
  val isAllNotificationsRead: StateFlow<Boolean> = _isAllNotificationsRead.asStateFlow()

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

  // Set Alarm Reminder Dialog State
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

  private val _sweetNoteLiked = MutableStateFlow(false)
  val sweetNoteLiked: StateFlow<Boolean> = _sweetNoteLiked.asStateFlow()

  // App Language (VI or EN) - dynamically detected from device locale or saved preferences
  private val _appLanguage = MutableStateFlow(com.example.ui.util.LocaleManager.getInitialLanguage(application))
  val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

  // First-run / Onboarding state
  private val _isFirstLaunch = MutableStateFlow(com.example.ui.util.LocaleManager.isFirstLaunch(application))
  val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch.asStateFlow()

  fun completeFirstLaunch() {
    _isFirstLaunch.value = false
    com.example.ui.util.LocaleManager.setFirstLaunchCompleted(getApplication())
  }

  // Notification/sound settings toggles — previously plain UI-local `remember` state in
  // SettingsScreen that reset every time the user left the screen. Persisted the same way as
  // appLanguage/wallpaper; still doesn't gate actual notification delivery (a bigger feature
  // than "make this toggle stop resetting"), so the subtitle copy in Settings must not claim it does.
  private val _notificationsEnabled = MutableStateFlow(com.example.alarm.AlarmNotificationScheduler.anniversaryNotificationsEnabled(application))
  val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

  private val _soundEnabled = MutableStateFlow(com.example.ui.util.LocaleManager.isSoundEnabled(application))
  val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

  fun setNotificationsEnabled(enabled: Boolean) {
    _notificationsEnabled.value = enabled
    com.example.alarm.AlarmNotificationScheduler.setAnniversaryNotificationsEnabled(getApplication(), enabled)
  }

  fun setSoundEnabled(enabled: Boolean) {
    _soundEnabled.value = enabled
    com.example.ui.util.LocaleManager.saveSoundEnabled(getApplication(), enabled)
  }

  // Language Dialog State (Shown on entry or when tapped)
  private val _showLanguageDialog = MutableStateFlow(false)
  val showLanguageDialog: StateFlow<Boolean> = _showLanguageDialog.asStateFlow()

  // User Guide Dialog State
  private val _showGuideDialog = MutableStateFlow(false)
  val showGuideDialog: StateFlow<Boolean> = _showGuideDialog.asStateFlow()

  // Wallpaper Picker Dialog State & Selected Wallpaper
  private val _showWallpaperDialog = MutableStateFlow(false)
  val showWallpaperDialog: StateFlow<Boolean> = _showWallpaperDialog.asStateFlow()

  private val _selectedWallpaperUrl = MutableStateFlow(
    com.example.ui.util.LocaleManager.getSavedWallpaperUrl(application)
      ?: "https://images.unsplash.com/photo-1522383225653-ed111181a951?q=80&w=1080&auto=format&fit=crop"
  )
  val selectedWallpaperUrl: StateFlow<String> = _selectedWallpaperUrl.asStateFlow()

  // Memory Capture Dialog State
  private val _showMemoryDialog = MutableStateFlow(false)
  val showMemoryDialog: StateFlow<Boolean> = _showMemoryDialog.asStateFlow()

  // Floating Hearts Overlay Trigger
  private val _floatingHeartsTrigger = MutableStateFlow(1L)
  val floatingHeartsTrigger: StateFlow<Long> = _floatingHeartsTrigger.asStateFlow()

  private val _isMilestoneCelebration = MutableStateFlow(false)
  val isMilestoneCelebration: StateFlow<Boolean> = _isMilestoneCelebration.asStateFlow()

  private val _selectedMemoryDetail = MutableStateFlow<SharedMemoryEntity?>(null)
  val selectedMemoryDetail: StateFlow<SharedMemoryEntity?> = _selectedMemoryDetail.asStateFlow()

  // Anniversary Memories Carousel Filter
  private val _selectedAnniversaryFilter = MutableStateFlow("Tất cả")
  val selectedAnniversaryFilter: StateFlow<String> = _selectedAnniversaryFilter.asStateFlow()

  fun selectAnniversaryFilter(filter: String) {
    _selectedAnniversaryFilter.value = filter
  }

  // Milestone Badge Tracker State
  private val _selectedBadge = MutableStateFlow<LoveBadgeEntity?>(null)
  val selectedBadge: StateFlow<LoveBadgeEntity?> = _selectedBadge.asStateFlow()

  private val _showBadgeShowcaseDialog = MutableStateFlow(false)
  val showBadgeShowcaseDialog: StateFlow<Boolean> = _showBadgeShowcaseDialog.asStateFlow()

  // Couple Profile State (as shown in user's image.png: Mhoang & TLinh)
  // Couple Profile State (Default clean state for production)
  private val _boyName = MutableStateFlow("Bạn")
  val boyName: StateFlow<String> = _boyName.asStateFlow()

  private val _boyBirthDate = MutableStateFlow("")
  val boyBirthDate: StateFlow<String> = _boyBirthDate.asStateFlow()

  private val _boyAge = MutableStateFlow(0)
  val boyAge: StateFlow<Int> = _boyAge.asStateFlow()

  private val _boyZodiac = MutableStateFlow("")
  val boyZodiac: StateFlow<String> = _boyZodiac.asStateFlow()

  private val _boyAvatarUrl = MutableStateFlow("")
  val boyAvatarUrl: StateFlow<String> = _boyAvatarUrl.asStateFlow()

  private val _girlName = MutableStateFlow("Người Thương")
  val girlName: StateFlow<String> = _girlName.asStateFlow()

  private val _girlBirthDate = MutableStateFlow("")
  val girlBirthDate: StateFlow<String> = _girlBirthDate.asStateFlow()

  private val _girlAge = MutableStateFlow(0)
  val girlAge: StateFlow<Int> = _girlAge.asStateFlow()

  private val _girlZodiac = MutableStateFlow("")
  val girlZodiac: StateFlow<String> = _girlZodiac.asStateFlow()

  private val _girlAvatarUrl = MutableStateFlow("")
  val girlAvatarUrl: StateFlow<String> = _girlAvatarUrl.asStateFlow()

  private val _loveTitle = MutableStateFlow("Hành Trình Yêu Thương")
  val loveTitle: StateFlow<String> = _loveTitle.asStateFlow()

  private val _loveDays = MutableStateFlow(1)
  val loveDays: StateFlow<Int> = _loveDays.asStateFlow()

  private val _anniversaryDate = MutableStateFlow("Hôm nay")
  val anniversaryDate: StateFlow<String> = _anniversaryDate.asStateFlow()

  private val _showEditCoupleDialog = MutableStateFlow(false)
  val showEditCoupleDialog: StateFlow<Boolean> = _showEditCoupleDialog.asStateFlow()

  private val _showAuthScreen = MutableStateFlow(false)
  val showAuthScreen: StateFlow<Boolean> = _showAuthScreen.asStateFlow()

  fun openAuthScreen() {
    _showAuthScreen.value = true
  }

  fun closeAuthScreen() {
    _showAuthScreen.value = false
  }

  fun saveLocalAvatar(uri: android.net.Uri, isPartner: Boolean): String {
    return try {
      val context = getApplication<Application>()
      val avatarsDir = java.io.File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
      val fileName = if (isPartner) "partner_avatar_${System.currentTimeMillis()}.jpg" else "user_avatar_${System.currentTimeMillis()}.jpg"
      val destFile = java.io.File(avatarsDir, fileName)

      context.contentResolver.openInputStream(uri)?.use { input ->
        destFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }
      destFile.toURI().toString()
    } catch (e: Exception) {
      android.util.Log.e("InLoveViewModel", "Error saving local avatar: ${e.message}")
      uri.toString()
    }
  }

  /** Latest couple profile from Room; a card falls back to it when its online profile is replaced or vanishes. */
  private var roomCouple: CoupleProfileEntity? = null

  private fun resetBoyCard() {
    val room = roomCouple
    _boyName.value = room?.partner1Name ?: "Bạn"
    _boyBirthDate.value = room?.partner1Birthday ?: ""
    _boyAvatarUrl.value = room?.partner1ProfilePicture ?: ""
    _boyAge.value = room?.partner1Age ?: 0
    _boyZodiac.value = room?.partner1Zodiac ?: ""
  }

  private fun resetGirlCard() {
    val room = roomCouple
    _girlName.value = room?.partner2Name ?: "Người Thương"
    _girlBirthDate.value = room?.partner2Birthday ?: ""
    _girlAvatarUrl.value = room?.partner2ProfilePicture ?: ""
    _girlAge.value = room?.partner2Age ?: 0
    _girlZodiac.value = room?.partner2Zodiac ?: ""
  }

  private val dao: com.example.data.db.InLoveDao get() = AppDatabase.getDatabase(getApplication()).inLoveDao()

  init {
    val database = AppDatabase.getDatabase(application)
    repository = InLoveRepository(database.inLoveDao(), application)
    onlineRepo = com.example.data.repository.OnlineCoupleRepository(database.inLoveDao(), application, viewModelScope)
    authRepo = com.example.data.repository.AuthRepository(
      database.inLoveDao(), onlineRepo, application, viewModelScope,
      vault = com.example.data.db.AccountDataVault(
        application, database,
        // Alarms of the outgoing scope must not fire in the next one (the receiver shows their text).
        beforeSwitch = {
          com.example.alarm.AlarmNotificationScheduler.cancelOutgoingAccountAlarms(application, database.inLoveDao())
        },
        afterSwitch = { com.example.alarm.AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(application, database.inLoveDao()) }
      )
    )
    authState = authRepo.authState

    currentOnlineUser = onlineRepo.currentUser
    partnerOnlineUser = onlineRepo.partnerUser
    activeRelationship = onlineRepo.activeRelationship
    relationshipStatus = onlineRepo.relationshipStatus
    mutualInterests = onlineRepo.mutualInterests
    incomingInvite = onlineRepo.incomingInvite
    outgoingInvite = onlineRepo.outgoingInvite

    userRole = combine(currentOnlineUser, authState) { user, auth ->
      if (auth is com.example.data.repository.AuthState.Authenticated) {
        auth.account.userRole
      } else {
        user.userRole
      }
    }.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      com.example.data.model.UserRole.USER_FREE
    )

    subscriptionTier = combine(currentOnlineUser, authState) { user, auth ->
      if (auth is com.example.data.repository.AuthState.Authenticated) {
        auth.account.tier
      } else {
        user.tier
      }
    }.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      com.example.data.model.SubscriptionTier.FREE
    )

    com.example.di.AppServiceLocator.initialize(application)
    val billingManager = com.example.di.AppServiceLocator.billingManager
    val adsManager = com.example.di.AppServiceLocator.adsManager
    isOnline = com.example.di.AppServiceLocator.networkMonitor.isOnline

    // BillingManager is the authoritative Single Source of Truth for VIP entitlements.
    // Cached local DB flags cannot grant VIP if Google Play Billing reports inactive/expired subscription.
    isVip = combine(currentOnlineUser, authState, com.example.di.AppServiceLocator.entitlementRepository.isVipUser) { _, auth, billingVip ->
      val isAdmin = auth is com.example.data.repository.AuthState.Authenticated &&
          auth.account.userRole == com.example.data.model.UserRole.ADMIN
      billingVip || isAdmin
    }.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      false
    )

    // Tự động đồng bộ trạng thái VIP với AdsManager để dọn cache ads
    viewModelScope.launch {
      isVip.collect { vip ->
        adsManager.setVipStatus(vip)
      }
    }

    // Khi thanh toán qua Google Play thành công hoặc hết hạn, tự động đồng bộ Room DB.
    // Chờ billingManager.hasSyncedOnce = true trước khi hạ cấp bất kỳ ai xuống FREE:
    // isVipUser khởi tạo `false` trước khi queryExistingPurchases() kịp trả lời Google Play,
    // nếu không chờ thì một VIP thật bị hạ xuống FREE trong vài trăm ms đầu mỗi lần mở app.
    viewModelScope.launch {
      combine(billingManager.hasSyncedOnce, billingManager.isVipUser) { synced, vip -> synced to vip }
        .collect { (synced, isBillingVip) ->
        if (!synced) return@collect
        val currentAuth = authState.value
        val uid = if (currentAuth is com.example.data.repository.AuthState.Authenticated) {
          currentAuth.account.uid
        } else {
          currentOnlineUser.value.uid
        }

        if (uid.isNotBlank()) {
          val isAdmin = currentAuth is com.example.data.repository.AuthState.Authenticated &&
              currentAuth.account.userRole == com.example.data.model.UserRole.ADMIN
          if (isBillingVip) {
            val mappedTier = when (billingManager.activeProductId.value) {
              com.example.billing.BillingManager.PRODUCT_VIP_MONTHLY -> com.example.data.model.SubscriptionTier.VIP_MONTHLY
              com.example.billing.BillingManager.PRODUCT_VIP_LIFETIME -> com.example.data.model.SubscriptionTier.LIFETIME
              else -> com.example.data.model.SubscriptionTier.VIP_YEARLY
            }
            authRepo.updateUserSubscription(
              uid,
              com.example.data.model.UserRole.USER_VIP,
              mappedTier
            )
            onlineRepo.setCurrentUserId(uid)
          } else if (!isAdmin) {
            // Revoke local entitlement when Google Play subscription expires or is revoked
            authRepo.updateUserSubscription(
              uid,
              com.example.data.model.UserRole.USER_FREE,
              com.example.data.model.SubscriptionTier.FREE
            )
          }
        }
      }
    }

    milestones = repository.milestones.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    giftIdeas = repository.giftIdeas.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    checklistItems = repository.checklistItems.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    customReminders = repository.customReminders.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    reminderCadences = repository.reminderCadences.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    coupleProfile = repository.coupleProfile.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      null
    )

    sharedMemories = repository.sharedMemories.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    loveBadges = repository.loveBadges.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    anniversaryDates = repository.anniversaryDates.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    giftReminders = repository.giftReminders.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    // Ngày sinh/ngày yêu là chữ tự do: không đọc được thì bỏ qua dịp đó, không đoán. Năm sinh không quan trọng nên lấy 2000.
    // Sở thích nhập tay (likesCsv) hợp với sở thích chung của cặp đôi online; ngân sách 0 = không giới hạn.
    // ponytail: today lấy tại thời điểm phát, mở app qua đêm thì đổi khi dữ liệu đổi.
    val occasionDates = combine(boyBirthDate, girlBirthDate, anniversaryDate) { boy, girl, love ->
      val birthdays = listOf(boy, girl).mapNotNull { text ->
        AlarmNotificationScheduler.parseDateToMonthDayYear(text)?.let { (d, m, _) ->
          AlarmNotificationScheduler.occurrenceDate(d, m, 2000)
        }
      }
      val loveStart = AlarmNotificationScheduler.parseDateToMonthDayYear(love)?.let { (d, m, y) ->
        y?.let { AlarmNotificationScheduler.occurrenceDate(d, m, it) }
      }
      birthdays to loveStart
    }
    personalizedGifts = combine(giftIdeas, mutualInterests, appLanguage, occasionDates, coupleProfile) { ideas, interests, lang, (birthdays, loveStart), profile ->
      GetPersonalizedGiftSuggestionsUseCase(
        ideas = ideas,
        interests = interests + parseLikes(profile?.likesCsv),
        budgetMaxVnd = profile?.budgetMaxVnd?.takeIf { it > 0 },
        birthdays = birthdays,
        loveStart = loveStart,
        today = LocalDate.now(),
        isEnglish = lang == AppLanguage.EN,
        region = profile?.occasionRegion.orEmpty()
      )
    }.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )

    presetPhotos = repository.presetPhotos
    presetAvatars = repository.presetAvatars
    presetWallpapers = repository.presetWallpapers

    viewModelScope.launch {
      presetWallpapers.collect { wallpapers ->
        if (wallpapers.isNotEmpty() && _selectedWallpaperUrl.value.isBlank()) {
          _selectedWallpaperUrl.value = wallpapers.first()
        }
      }
    }

    viewModelScope.launch {
      repository.initializeDefaultDataIfEmpty(_appLanguage.value)
      // Automatically schedule all stored anniversaries & milestones in Room DB
      com.example.alarm.AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(application, dao)
    }

    viewModelScope.launch {
      var hadProfile = false
      repository.coupleProfile.collect { profile ->
        roomCouple = profile
        if (profile == null) {
          if (!hadProfile) return@collect
          hadProfile = false
          // Scope switched to one with no profile: don't inherit the previous couple's details.
          resetBoyCard(); resetGirlCard()
          _loveTitle.value = "Hành Trình Yêu Thương"; _loveDays.value = 1; _anniversaryDate.value = "Hôm nay"
        } else {
          hadProfile = true
          _boyName.value = profile.partner1Name
          _boyBirthDate.value = profile.partner1Birthday
          _boyAvatarUrl.value = profile.partner1ProfilePicture
          _boyAge.value = profile.partner1Age
          _boyZodiac.value = profile.partner1Zodiac
          _girlName.value = profile.partner2Name
          _girlBirthDate.value = profile.partner2Birthday
          _girlAvatarUrl.value = profile.partner2ProfilePicture
          _girlAge.value = profile.partner2Age
          _girlZodiac.value = profile.partner2Zodiac
          _loveTitle.value = profile.loveTitle
          _loveDays.value = profile.loveDays
          _anniversaryDate.value = profile.anniversaryDate
        }
      }
    }

    // Online profiles fill the couple cards; a card holds one profile at a time (see OnlineProfileRules.kt).
    viewModelScope.launch {
      var applied: String? = null
      currentOnlineUser.collect { user ->
        val uid = profileUidOf(user.uid)
        if (isProfileChanged(applied, uid)) resetBoyCard()
        val sameProfile = applied == uid
        applied = uid
        if (uid == null) return@collect
        val name = user.displayName
        _boyName.value = profileFieldAfter(_boyName.value, name, name.isBlank() || name == "Bạn", sameProfile)
        _boyBirthDate.value = profileFieldAfter(_boyBirthDate.value, user.birthDate, user.birthDate.isBlank(), sameProfile)
        _boyAvatarUrl.value = profileFieldAfter(_boyAvatarUrl.value, user.avatarUrl, user.avatarUrl.isBlank(), sameProfile)
        _boyAge.value = profileFieldAfter(_boyAge.value, user.age, user.age <= 0, sameProfile)
        _boyZodiac.value = profileFieldAfter(_boyZodiac.value, user.zodiac, user.zodiac.isBlank(), sameProfile)
      }
    }

    viewModelScope.launch {
      var applied: String? = null
      partnerOnlineUser.collect { partner ->
        val uid = profileUidOf(partner?.uid)
        if (isProfileChanged(applied, uid)) resetGirlCard()
        val sameProfile = applied == uid
        applied = uid
        if (partner == null || uid == null) return@collect
        _girlName.value = profileFieldAfter(_girlName.value, partner.displayName, partner.displayName.isBlank(), sameProfile)
        _girlBirthDate.value = profileFieldAfter(_girlBirthDate.value, partner.birthDate, partner.birthDate.isBlank(), sameProfile)
        _girlAvatarUrl.value = profileFieldAfter(_girlAvatarUrl.value, partner.avatarUrl, partner.avatarUrl.isBlank(), sameProfile)
        _girlAge.value = profileFieldAfter(_girlAge.value, partner.age, partner.age <= 0, sameProfile)
        _girlZodiac.value = profileFieldAfter(_girlZodiac.value, partner.zodiac, partner.zodiac.isBlank(), sameProfile)
      }
    }

    viewModelScope.launch {
      activeRelationship.collect { rel ->
        if (rel != null) {
          if (rel.startDateText.isNotBlank()) _anniversaryDate.value = rel.startDateText
          val days = com.example.ui.util.ProfileUtils.calculateLoveDays(rel.startDate)
          if (days > 0) _loveDays.value = days
        }
      }
    }

    // Automatically schedule upcoming love anniversary milestones based on Firestore start date
    viewModelScope.launch {
      combine(
        activeRelationship,
        _anniversaryDate,
        partnerOnlineUser
      ) { rel, annDate, partner ->
        Triple(rel, annDate, partner)
      }.collect { (rel, annDate, partner) ->
        val startMillis = rel?.startDate ?: 0L
        val startDateText = if (rel?.startDateText.isNullOrBlank()) annDate else rel.startDateText
        val partnerName = partner?.effectiveDisplayName ?: "người ấy"

        // Recalculate upcoming milestones list
        val milestonesList = com.example.alarm.LoveAnniversaryMilestoneScheduler.getUpcomingMilestones(
          startDateMillis = startMillis,
          startDateText = startDateText
        )
        _upcomingMilestones.value = milestonesList

        // Schedule local alarms based on Firestore start date
        com.example.alarm.LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(
          context = application,
          startDateMillis = startMillis,
          startDateText = startDateText,
          partnerName = partnerName
        )
      }
    }
  }

  fun triggerFloatingHearts(isMilestone: Boolean = false) {
    _isMilestoneCelebration.value = isMilestone
    _floatingHeartsTrigger.value = System.currentTimeMillis()
  }

  fun celebrateMilestoneAnniversary(days: Int) {
    triggerFloatingHearts(isMilestone = true)
    val celebrationMessage = if (_appLanguage.value == AppLanguage.VI) {
      "🎉 Chúc Mừng Cột Mốc $days Ngày Yêu! Mưa tim ngập tràn chúc phúc đôi bạn! 💕✨"
    } else {
      "🎉 Milestone Reached: $days Days Together! Showering love hearts! 💕✨"
    }
    showToast(celebrationMessage)
  }

  fun setTab(index: Int) {
    _selectedTab.value = index
  }

  fun setCalendarFilter(filter: String) {
    _calendarFilter.value = filter
  }

  fun setGiftCategory(cat: String) {
    _giftCategory.value = cat
  }

  fun showToast(msg: String) {
    _toastMessage.value = msg
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  /** "Cà phê, du lịch" -> {"cà phê", "du lịch"}; rỗng/null -> tập rỗng. */
  private fun parseLikes(csv: String?): Set<String> =
    csv.orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

  /** Lưu sở thích, ngân sách (VND, 0 = không giới hạn) và vùng dịp lễ của đối tác; chỉ có tác dụng khi đã có hồ sơ cặp đôi. */
  fun savePartnerPreferences(likesCsv: String, budgetMaxVnd: Long, occasionRegion: String) {
    viewModelScope.launch {
      repository.savePartnerPreferences(likesCsv.trim(), budgetMaxVnd, occasionRegion)
      showToast(
        if (_appLanguage.value == AppLanguage.VI) "Đã lưu sở thích của đối tác ❤️"
        else "Partner preferences saved ❤️"
      )
    }
  }

  fun toggleChecklist(item: ChecklistItemEntity) {
    viewModelScope.launch {
      repository.toggleChecklistItem(item)
    }
  }

  fun addChecklist(text: String) {
    if (text.isBlank()) return
    viewModelScope.launch {
      repository.addChecklistItem(text.trim())
      showToast("Đã thêm vào checklist chuẩn bị!")
    }
  }

  fun toggleGiftFavorite(item: GiftIdeaEntity) {
    viewModelScope.launch {
      repository.toggleGiftFavorite(item)
      showToast(if (!item.isFavorited) "Đã lưu ý tưởng quà tặng này!" else "Đã bỏ lưu ý tưởng")
    }
  }

  fun toggleCadence(item: ReminderCadenceEntity) {
    viewModelScope.launch {
      repository.toggleCadence(item)
      // A cadence toggle changes which advance and day-of alarms stay armed, so reschedule now.
      com.example.alarm.AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(getApplication(), dao)
      showToast(if (!item.isEnabled) "Đã bật: ${item.label}" else "Đã tắt: ${item.label}")
    }
  }

  fun addCustomReminder(title: String, dateText: String, note: String) {
    if (title.isBlank()) {
      showToast("Vui lòng nhập tên lời nhắc nhé!")
      return
    }
    viewModelScope.launch {
      repository.addCustomReminder(title, dateText, note)
      _showAddReminderDialog.value = false
      triggerFloatingHearts()
      showToast("Đã thêm thành công lời nhắc hẹn hò mới!")
    }
  }

  fun openSetAlarmDialog(title: String = "", message: String = "", reminderId: Long? = null) {
    _alarmDialogPresetTitle.value = title
    _alarmDialogPresetMessage.value = message
    _alarmDialogReminderId.value = reminderId
    _showSetAlarmDialog.value = true
  }

  fun closeSetAlarmDialog() {
    _showSetAlarmDialog.value = false
  }

  // Personal alarms live on their reminder row, so reboot, timezone change and account switch-in can restore them.
  fun scheduleReminderAlarm(title: String, message: String, triggerAtMillis: Long, reminderId: Long?) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val formatted = com.example.alarm.AlarmNotificationScheduler.formatAlarmTime(triggerAtMillis)
      // A missing or stale id gets a new reminder row; its id is the alarm's request code.
      val existing = reminderId?.takeIf { repository.hasCustomReminder(it) }
      val id = existing ?: repository.addCustomReminder(title, formatted.substringAfter(" - "), message)
      val success = com.example.alarm.AlarmNotificationScheduler.scheduleAlarm(
        context = context,
        reminderId = id,
        title = title,
        message = message,
        triggerAtMillis = triggerAtMillis
      )
      if (success) {
        repository.setCustomReminderAlarm(id, triggerAtMillis, formatted)
        val exactWarning = if (com.example.alarm.AlarmNotificationScheduler.exactAlarmsGranted(context)) "" else " Quyền báo thức chính xác đang tắt, báo thức có thể trễ."
        showToast("⏰ Đã hẹn giờ báo thức lúc $formatted!$exactWarning")
        triggerFloatingHearts()
      } else {
        // A new row is dropped; an existing row must not keep a time that nothing will ring.
        if (existing == null) repository.deleteCustomReminder(id) else repository.setCustomReminderAlarm(id, null, "")
        showToast("Không thể đặt lịch báo thức. Vui lòng kiểm tra quyền hệ thống.")
      }
      _showSetAlarmDialog.value = false
    }
  }

  // A test alarm fires one second from now and stores nothing.
  fun testReminderAlarm(title: String, message: String) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val now = System.currentTimeMillis()
      val success = com.example.alarm.AlarmNotificationScheduler.scheduleAlarm(
        context = context,
        reminderId = now,
        title = title,
        message = message,
        triggerAtMillis = now + 1000L
      )
      showToast(if (success) "⏰ Báo thức thử sẽ kêu sau 1 giây!" else "Không thể đặt lịch báo thức. Vui lòng kiểm tra quyền hệ thống.")
      _showSetAlarmDialog.value = false
    }
  }

  fun cancelReminderAlarm(reminderId: Long) {
    val context = getApplication<Application>()
    com.example.alarm.AlarmNotificationScheduler.cancelAlarm(context, reminderId)
  }

  fun triggerInstantTestAlarm(
    title: String = "Thử nghiệm báo thức tình yêu ❤️",
    message: String = "Chuông và thông báo kỷ niệm hoạt động rất tốt!"
  ) {
    val context = getApplication<Application>()
    com.example.alarm.AlarmNotificationScheduler.triggerInstantTest(context, title, message)
    showToast("🔔 Đã phát thử chuông thông báo kỷ niệm!")
  }

  fun deleteCustomReminder(id: Long) {
    viewModelScope.launch {
      cancelReminderAlarm(id)
      repository.deleteCustomReminder(id)
      showToast("Đã xóa lời nhắc hẹn thành công.")
    }
  }

  fun deleteMilestone(id: Long) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      com.example.alarm.AlarmNotificationScheduler.cancelMilestoneNotification(context, id)
      repository.deleteMilestone(id)
      showToast("Đã xóa ngày kỷ niệm khỏi lịch.")
    }
  }

  fun deleteChecklistItem(id: Long) {
    viewModelScope.launch {
      repository.deleteChecklistItem(id)
      showToast("Đã xóa công việc chuẩn bị quà.")
    }
  }

  fun addMilestone(
    title: String,
    dateText: String,
    subtitle: String,
    categoryTag: String,
    secondaryTag: String,
    imageUrl: String,
    daysRemaining: Int,
    isImportant: Boolean
  ) {
    if (title.isBlank()) {
      showToast("Vui lòng nhập tiêu đề kỷ niệm!")
      return
    }
    val defaultFallback = presetPhotos.value.firstOrNull() ?: ""
    viewModelScope.launch {
      val ms = MilestoneEntity(
        title = title,
        dateText = dateText,
        subtitle = subtitle,
        categoryTag = categoryTag.ifBlank { "Kỷ Niệm" },
        secondaryTag = secondaryTag.ifBlank { "Ý Nghĩa" },
        imageUrl = imageUrl.ifBlank { defaultFallback },
        daysRemaining = daysRemaining,
        isPast = daysRemaining < 0,
        isImportant = isImportant,
        isUserCreated = true,
        notificationEnabled = true
      )
      val newId = repository.addMilestone(ms)
      val context = getApplication<Application>()
      com.example.alarm.AlarmNotificationScheduler.scheduleMilestoneNotification(context, ms.copy(id = newId))
      _showAddMilestoneDialog.value = false
      triggerFloatingHearts()
      showToast("Đã thêm kỷ niệm mới & hẹn giờ thông báo! 🔔")
    }
  }

  fun toggleMilestoneNotification(item: MilestoneEntity) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val willBeEnabled = !item.notificationEnabled
      val updated = item.copy(notificationEnabled = willBeEnabled)
      repository.toggleMilestoneNotification(item)
      if (willBeEnabled) {
        com.example.alarm.AlarmNotificationScheduler.scheduleMilestoneNotification(context, updated)
        showToast("🔔 Đã bật thông báo cho cột mốc '${item.title}'")
      } else {
        com.example.alarm.AlarmNotificationScheduler.cancelMilestoneNotification(context, item.id)
        showToast("🔕 Đã tắt thông báo cho '${item.title}'")
      }
    }
  }

  fun markAllNotificationsRead() {
    _isAllNotificationsRead.value = true
    showToast("Đã đánh dấu đã đọc tất cả thông báo!")
  }

  fun toggleSweetNoteLike() {
    val newState = !_sweetNoteLiked.value
    _sweetNoteLiked.value = newState
    if (newState) {
      triggerFloatingHearts()
      showToast("Đã gửi phản hồi tim ngọt ngào ❤️!")
    }
  }

  fun saveCoupleProfile(
    boy: String,
    boyBirth: String,
    boyAvatar: String,
    boyA: Int,
    boyZod: String,
    girl: String,
    girlBirth: String,
    girlAvatar: String,
    girlA: Int,
    girlZod: String,
    title: String,
    days: Int,
    anniversary: String = ""
  ) {
    val cleanBoy = boy.trim().ifEmpty { _boyName.value.ifBlank { "Bạn" } }
    val cleanBoyBirth = boyBirth.trim().ifEmpty { _boyBirthDate.value }
    val cleanBoyAvatar = boyAvatar.trim().ifEmpty { _boyAvatarUrl.value }
    val cleanBoyZod = boyZod.trim().ifEmpty { _boyZodiac.value }

    val cleanGirl = girl.trim().ifEmpty { _girlName.value.ifBlank { "Người thương" } }
    val cleanGirlBirth = girlBirth.trim().ifEmpty { _girlBirthDate.value }
    val cleanGirlAvatar = girlAvatar.trim().ifEmpty { _girlAvatarUrl.value }
    val cleanGirlZod = girlZod.trim().ifEmpty { _girlZodiac.value }

    val cleanTitle = title.trim().ifEmpty { _loveTitle.value.ifBlank { "Hành Trình Yêu Thương" } }

    viewModelScope.launch {
      val existing = repository.getCoupleProfileSync() // REPLACE ghi đè hàng: giữ lại sở thích đã nhập
      val entity = CoupleProfileEntity(
        id = 1,
        likesCsv = existing?.likesCsv ?: "",
        budgetMaxVnd = existing?.budgetMaxVnd ?: 0,
        occasionRegion = existing?.occasionRegion ?: "",
        partner1Name = cleanBoy,
        partner1Birthday = cleanBoyBirth,
        partner1ProfilePicture = cleanBoyAvatar,
        partner1Age = boyA,
        partner1Zodiac = cleanBoyZod,
        partner2Name = cleanGirl,
        partner2Birthday = cleanGirlBirth,
        partner2ProfilePicture = cleanGirlAvatar,
        partner2Age = girlA,
        partner2Zodiac = cleanGirlZod,
        loveTitle = cleanTitle,
        loveDays = days,
        anniversaryDate = anniversary
      )
      repository.saveCoupleProfile(entity)

      // Also update local StateFlows immediately for reactive smoothness
      _boyName.value = cleanBoy
      _boyBirthDate.value = cleanBoyBirth
      _boyAvatarUrl.value = cleanBoyAvatar
      _boyAge.value = boyA
      _boyZodiac.value = cleanBoyZod
      _girlName.value = cleanGirl
      _girlBirthDate.value = cleanGirlBirth
      _girlAvatarUrl.value = cleanGirlAvatar
      _girlAge.value = girlA
      _girlZodiac.value = cleanGirlZod
      _loveTitle.value = cleanTitle
      _loveDays.value = days
      _anniversaryDate.value = anniversary

      _showEditCoupleDialog.value = false
      triggerFloatingHearts()
      showToast("Đã lưu hồ sơ 2 bạn vào cơ sở dữ liệu Room thành công! ❤️")
    }
  }

  /**
   * Update and persist custom Love Journey Title ("Hành Trình Yêu Thương")
   */
  fun updateLoveTitle(newTitle: String) {
    val cleanTitle = newTitle.trim().ifEmpty { "Hành Trình Yêu Thương" }
    viewModelScope.launch {
      _loveTitle.value = cleanTitle
      val current = repository.getCoupleProfileSync() ?: CoupleProfileEntity(
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
        loveTitle = cleanTitle,
        loveDays = _loveDays.value,
        anniversaryDate = _anniversaryDate.value
      )
      repository.saveCoupleProfile(current.copy(loveTitle = cleanTitle))
      val msg = if (_appLanguage.value == AppLanguage.EN) {
        "Love Journey title updated: \"$cleanTitle\" ❤️"
      } else {
        "Đã đổi tên hành trình: \"$cleanTitle\" ❤️"
      }
      showToast(msg)
    }
  }

  /**
   * Calculates the exact number of days a couple has been together given their
   * anniversary/start date string (e.g. "14/02/2023").
   * Counts the start day as day 1 so today is included in the streak.
   */
  fun calculateLoveDaysFromDate(dateStr: String): Int {
    val formats = listOf("dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
    for (pattern in formats) {
      try {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.isLenient = false
        val date = sdf.parse(dateStr.trim())
        if (date != null) {
          val startCal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
          }
          val nowCal = Calendar.getInstance()
          val diffMillis = nowCal.timeInMillis - startCal.timeInMillis
          if (diffMillis >= 0) {
            val days = TimeUnit.MILLISECONDS.toDays(diffMillis) + 1
            return days.toInt()
          }
        }
      } catch (_: Exception) {}
    }
    return _loveDays.value
  }

  /**
   * Sets the anniversary date, calculates the total days elapsed, updates the
   * Room Database and UI StateFlows, and gives celebratory romantic feedback.
   */
  fun setAnniversaryAndRecalculateDays(newDate: String) {
    val cleanDate = newDate.trim().ifEmpty { _anniversaryDate.value.ifBlank { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) } }
    val calcDays = calculateLoveDaysFromDate(cleanDate)
    _anniversaryDate.value = cleanDate
    _loveDays.value = calcDays

    viewModelScope.launch {
      val current = repository.coupleProfile.first()
      if (current != null) {
        val updated = current.copy(
          anniversaryDate = cleanDate,
          loveDays = calcDays,
          updatedAt = System.currentTimeMillis()
        )
        repository.saveCoupleProfile(updated)
      }
      triggerFloatingHearts()
      showToast(
        if (_appLanguage.value == AppLanguage.VI) {
          "Đã tính lại: $calcDays ngày yêu nhau từ ngày $cleanDate! 💕"
        } else {
          "Recalculated: $calcDays days together since $cleanDate! 💕"
        }
      )
    }
  }

  /**
   * Mở giao diện thanh toán Google Play Billing cho gói đăng ký VIP.
   */
  fun upgradeWithBilling(
    activity: Activity,
    productDetails: ProductDetails,
    offerToken: String = ""
  ) {
    com.example.di.AppServiceLocator.billingManager.launchPurchaseFlow(activity, productDetails, offerToken)
  }

  /**
   * Khôi phục giao dịch đã mua từ Google Play (Restore Purchases).
   */
  fun restorePurchases(onComplete: ((Boolean) -> Unit)? = null) {
    com.example.di.AppServiceLocator.billingManager.queryExistingPurchases { success ->
      if (success) {
        showToast("✅ Đã khôi phục thành công gói VIP!")
      } else {
        showToast("Không tìm thấy giao dịch VIP nào cho tài khoản này.")
      }
      onComplete?.invoke(success)
    }
  }

  /**
   * Hiển thị quảng cáo xen kẽ Interstitial Ad với cơ chế tự động capping 30 giây.
   */
  fun showInterstitialAd(activity: Activity, onDismissed: () -> Unit = {}) {
    com.example.di.AppServiceLocator.adsManager.showInterstitial(activity, onDismissed)
  }

  /**
   * Thêm quà gợi ý theo sở thích chung vào danh mục (mẫu có sẵn, không gọi AI).
   */
  fun triggerAiGiftSuggestions(occasion: String = "Kỷ niệm ngày yêu") {
    viewModelScope.launch {
      val partner = partnerOnlineUser.value
      val partnerName = partner?.effectiveDisplayName ?: girlName.value
      val interests = mutualInterests.value + parseLikes(coupleProfile.value?.likesCsv)
      showToast("✨ Đang thêm gợi ý quà theo sở thích chung...")
      val result = repository.generateAiGiftSuggestions(
        partnerName = partnerName,
        mutualInterests = interests,
        occasion = occasion
      )
      if (result.isSuccess) {
        showToast("✨ Đã thêm ${result.getOrNull()?.size ?: 0} gợi ý quà tặng theo sở thích!")
      } else {
        showToast("Chưa thêm được gợi ý mới. Bạn vẫn có thể xem toàn bộ danh mục quà tặng.")
      }
    }
  }



  /**
   * Direct sync of calculated days to the couple profile and database.
   */
  fun syncCalculatedDaysToHome(days: Int) {
    if (days <= 0) return
    _loveDays.value = days
    viewModelScope.launch {
      val current = repository.coupleProfile.first()
      if (current != null) {
        val updated = current.copy(
          loveDays = days,
          updatedAt = System.currentTimeMillis()
        )
        repository.saveCoupleProfile(updated)
      }
      triggerFloatingHearts()
      showToast(
        if (_appLanguage.value == AppLanguage.VI) {
          "Đã đồng bộ $days ngày yêu lên trang chủ! ❤️"
        } else {
          "Synced $days love days to main screen! ❤️"
        }
      )
    }
  }

  fun updateCoupleInfo(
    boy: String,
    boyBirth: String,
    boyA: Int,
    boyZod: String,
    girl: String,
    girlBirth: String,
    girlA: Int,
    girlZod: String,
    title: String,
    days: Int
  ) {
    saveCoupleProfile(
      boy = boy,
      boyBirth = boyBirth,
      boyAvatar = _boyAvatarUrl.value,
      boyA = boyA,
      boyZod = boyZod,
      girl = girl,
      girlBirth = girlBirth,
      girlAvatar = _girlAvatarUrl.value,
      girlA = girlA,
      girlZod = girlZod,
      title = title,
      days = days
    )
  }

  fun openEditCoupleDialog() {
    _showEditCoupleDialog.value = true
  }

  fun closeEditCoupleDialog() {
    _showEditCoupleDialog.value = false
  }

  fun openAddReminderDialog() {
    _showAddReminderDialog.value = true
  }

  fun closeAddReminderDialog() {
    _showAddReminderDialog.value = false
  }

  fun openAddMilestoneDialog() {
    _showAddMilestoneDialog.value = true
  }

  fun closeAddMilestoneDialog() {
    _showAddMilestoneDialog.value = false
  }

  fun openAddChecklistDialog() {
    _showAddChecklistDialog.value = true
  }

  fun closeAddChecklistDialog() {
    _showAddChecklistDialog.value = false
  }

  fun openAddAnniversaryDialog(initialDate: String? = null) {
    _prefilledAnniversaryDate.value = initialDate
    _showAddAnniversaryDialog.value = true
  }

  fun closeAddAnniversaryDialog() {
    _showAddAnniversaryDialog.value = false
    _prefilledAnniversaryDate.value = null
  }

  fun openAddGiftReminderDialog() {
    _showAddGiftReminderDialog.value = true
  }

  fun closeAddGiftReminderDialog() {
    _showAddGiftReminderDialog.value = false
  }

  fun openGiftDetail(item: GiftIdeaEntity) {
    _selectedGiftDetail.value = item
  }

  fun closeGiftDetail() {
    _selectedGiftDetail.value = null
  }

  fun openVipProposal() {
    _showVipProposalDetail.value = true
  }

  fun closeVipProposal() {
    _showVipProposalDetail.value = false
  }

  fun setLanguage(lang: AppLanguage) {
    _appLanguage.value = lang
    com.example.ui.util.LocaleManager.saveLanguage(getApplication(), lang)
    val resId = if (lang == AppLanguage.VI) com.example.R.string.toast_switched_vi else com.example.R.string.toast_switched_en
    val config = android.content.res.Configuration(getApplication<Application>().resources.configuration).apply {
      setLocale(java.util.Locale.forLanguageTag(if (lang == AppLanguage.VI) "vi" else "en"))
    }
    val localizedContext = getApplication<Application>().createConfigurationContext(config)
    val notice = localizedContext.getString(resId)
    showToast(notice)
  }

  fun openLanguageDialog() {
    _showLanguageDialog.value = true
  }

  fun closeLanguageDialog() {
    _showLanguageDialog.value = false
  }

  fun openGuideDialog() {
    _showGuideDialog.value = true
  }

  fun closeGuideDialog() {
    _showGuideDialog.value = false
  }

  fun openWallpaperDialog() {
    _showWallpaperDialog.value = true
  }

  fun closeWallpaperDialog() {
    _showWallpaperDialog.value = false
  }

  fun setWallpaper(url: String) {
    if (url.isNotBlank()) {
      val trimmed = url.trim()
      _selectedWallpaperUrl.value = trimmed
      com.example.ui.util.LocaleManager.saveWallpaperUrl(getApplication(), trimmed)
      triggerFloatingHearts()
      val msg = if (_appLanguage.value == AppLanguage.VI) "Đã đổi hình nền lãng mạn mới! 🌸" else "Romantic wallpaper updated! 🌸"
      showToast(msg)
    }
  }

  fun openMemoryDialog() {
    _showMemoryDialog.value = true
  }

  fun closeMemoryDialog() {
    _showMemoryDialog.value = false
  }

  /**
   * Lưu kỷ niệm. Ảnh/video được tải lên và máy chủ xác nhận trước, rồi mới ghi vào Room, nên kỷ niệm
   * không bao giờ trỏ tới một ảnh chưa được xác nhận. Trả về false khi chưa lưu để hộp thoại giữ nguyên.
   */
  suspend fun saveSharedMemory(
    title: String,
    dateText: String,
    photoUri: String,
    note: String,
    location: String,
    media: MemoryMediaFile?,
    coverUri: String?,
    privacyLevel: String,
    anniversaryTitle: String = "Kỷ Niệm Ngày Yêu",
  ): Boolean = viewModelScope.async {
    persistSharedMemory(title, dateText, photoUri, note, location, media, coverUri, privacyLevel, anniversaryTitle)
  }.await()

  // Chạy trong viewModelScope (không theo hộp thoại) nên việc lưu không bị huỷ khi người dùng đóng màn hình giữa chừng.
  private suspend fun persistSharedMemory(
    title: String,
    dateText: String,
    photoUri: String,
    note: String,
    location: String,
    media: MemoryMediaFile?,
    coverUri: String?,
    privacyLevel: String,
    anniversaryTitle: String,
  ): Boolean {
    val vi = _appLanguage.value == AppLanguage.VI
    val me = currentOnlineUser.value
    val isVideo = media?.kind == MediaKind.VIDEO
    val validTitle = title.trim().ifEmpty { "Khoảnh Khắc Ngọt Ngào" }
    val outcome = saveSharedMemoryUseCase(
      signedIn = me.uid.isNotBlank(),
      relationshipId = me.relationshipId,
      wantsShare = privacyLevel != PRIVACY_PRIVATE,
      media = media,
    ) { memoryId, shareWithCouple, publicId ->
      repository.addSharedMemory(
        title = validTitle,
        dateText = dateText.trim().ifEmpty { "Hôm nay" },
        photoUri = (coverUri ?: photoUri).trim().ifEmpty { presetPhotos.value.firstOrNull().orEmpty() },
        note = note.trim(),
        location = location.trim(),
        authorId = me.uid,
        authorName = me.effectiveDisplayName.ifBlank { "Bạn" },
        mediaType = if (isVideo) "VIDEO" else "IMAGE",
        videoUri = media?.takeIf { isVideo }?.let { Uri.fromFile(it.file).toString() },
        cloudinaryPublicId = publicId,
        fileSizeFormatted = media?.let { formatMediaSize(it.sizeBytes) } ?: "",
        durationSeconds = media?.durationSeconds ?: 0,
        privacyLevel = if (shareWithCouple) privacyLevel else PRIVACY_PRIVATE,
        relationshipId = me.relationshipId,
        syncId = memoryId,
        anniversaryTitle = anniversaryTitle.trim().ifEmpty { "Kỷ Niệm Ngày Yêu" },
      )
    }
    return when (outcome) {
      is SaveSharedMemoryOutcome.Saved -> {
        triggerFloatingHearts()
        showToast(
          when {
            outcome.unpaired -> if (vi) "Chưa ghép đôi nên \"$validTitle\" được lưu riêng trên máy." else "Not paired yet, so \"$validTitle\" is saved on this device only."
            isVideo -> if (vi) "Đã lưu video kỷ niệm! 🎬💕" else "Saved video memory! 🎬💕"
            else -> if (vi) "Đã lưu kỷ niệm \"$validTitle\"! 📸💕" else "Saved memory \"$validTitle\"! 📸💕"
          }
        )
        true
      }
      SaveSharedMemoryOutcome.NotSignedIn -> {
        showToast(if (vi) "Hãy đăng nhập để chia sẻ kỷ niệm với người ấy." else "Sign in to share memories with your partner.")
        false
      }
      is SaveSharedMemoryOutcome.InvalidMedia -> {
        showToast(outcome.message)
        false
      }
      SaveSharedMemoryOutcome.UploadFailed -> {
        showToast(
          if (vi) "Chưa tải được ảnh hoặc video lên, kỷ niệm chưa được lưu. Hãy kiểm tra mạng và thử lại."
          else "Couldn't upload the photo or video, so the memory wasn't saved. Check your connection and try again."
        )
        false
      }
      SaveSharedMemoryOutcome.SaveFailed -> {
        showToast(if (vi) "Không lưu được kỷ niệm, hãy thử lại." else "Couldn't save the memory. Please try again.")
        false
      }
    }
  }

  /** URL phân phối tạm (khoảng 15 phút) cho ảnh/video đã tải lên của kỷ niệm; null nếu không có hoặc lỗi. */
  suspend fun resolveMemoryMediaUrl(memory: SharedMemoryEntity): String? {
    // Use case từ chối kỷ niệm không thuộc cặp đôi đang đăng nhập, trước khi gọi máy chủ.
    return resolveMemoryMediaUrlUseCase(
      currentRelationshipId = currentOnlineUser.value.relationshipId,
      relationshipId = memory.relationshipId.orEmpty(),
      memoryId = memory.syncId,
    ).getOrNull()
  }

  fun updateSharedMemory(memory: SharedMemoryEntity) {
    viewModelScope.launch {
      repository.updateSharedMemory(memory)
      if (_selectedMemoryDetail.value?.id == memory.id) {
        _selectedMemoryDetail.value = memory
      }
      val msg = if (_appLanguage.value == AppLanguage.VI) "Đã cập nhật kỷ niệm! ✏️✨" else "Memory updated!"
      showToast(msg)
    }
  }

  fun isCurrentUserAuthor(memory: SharedMemoryEntity): Boolean {
    val myUid = currentOnlineUser.value.uid
    return memory.authorId.isBlank() || memory.authorId == myUid
  }

  fun deleteSharedMemory(id: Long) {
    viewModelScope.launch {
      repository.deleteSharedMemory(id)
      if (_selectedMemoryDetail.value?.id == id) {
        _selectedMemoryDetail.value = null
      }
      val msg = if (_appLanguage.value == AppLanguage.VI) "Đã xóa ảnh kỷ niệm" else "Memory deleted"
      showToast(msg)
    }
  }

  fun toggleMemoryFavorite(memory: SharedMemoryEntity) {
    viewModelScope.launch {
      repository.toggleMemoryFavorite(memory)
      if (_selectedMemoryDetail.value?.id == memory.id) {
        _selectedMemoryDetail.value = memory.copy(isFavorite = !memory.isFavorite)
      }
      triggerFloatingHearts()
    }
  }

  fun openMemoryDetail(memory: SharedMemoryEntity) {
    _selectedMemoryDetail.value = memory
  }

  fun closeMemoryDetail() {
    _selectedMemoryDetail.value = null
  }

  // Visual Milestone Tracker & Badges
  fun selectBadge(badge: LoveBadgeEntity?) {
    _selectedBadge.value = badge
  }

  fun openBadgeShowcase() {
    _showBadgeShowcaseDialog.value = true
  }

  fun closeBadgeShowcase() {
    _showBadgeShowcaseDialog.value = false
  }

  fun claimBadge(badgeId: String, customNote: String = "") {
    viewModelScope.launch {
      repository.claimLoveBadge(badgeId, customNote)
      triggerFloatingHearts(isMilestone = true)
      val msg = if (_appLanguage.value == AppLanguage.VI) {
        "🏆 Đã vinh danh và lưu kỷ niệm vào Huy Hiệu Trái Tim!"
      } else {
        "🏆 Badge honored and memory saved to Heart Trophy!"
      }
      showToast(msg)
    }
  }

  fun celebrateBadge(badge: LoveBadgeEntity) {
    triggerFloatingHearts(isMilestone = true)
    val msg = if (_appLanguage.value == AppLanguage.VI) {
      "🎉 Chúc mừng cột mốc ${badge.targetDays} ngày bên nhau! 💕✨"
    } else {
      "🎉 Celebrating ${badge.targetDays} days together! 💕✨"
    }
    showToast(msg)
  }

  // Room Persistence for Anniversary Dates
  fun addAnniversaryDate(
    title: String,
    dateText: String,
    type: String = "LOVE",
    description: String = "",
    isAnnual: Boolean = true,
    reminderDaysBefore: Int = 3
  ) {
    if (title.isBlank()) {
      showToast("Vui lòng nhập tên ngày kỷ niệm!")
      return
    }
    viewModelScope.launch {
      val newId = repository.addAnniversaryDate(
        title = title.trim(),
        dateText = dateText.trim(),
        type = type,
        description = description.trim(),
        isAnnual = isAnnual,
        reminderDaysBefore = reminderDaysBefore,
        relationshipId = currentOnlineUser.value.relationshipId
      )
      val context = getApplication<Application>()
      val ann = AnniversaryDateEntity(
        id = newId,
        title = title.trim(),
        dateText = dateText.trim(),
        type = type,
        description = description.trim(),
        isAnnual = isAnnual,
        notificationEnabled = true,
        reminderDaysBefore = reminderDaysBefore
      )
      val disabledCadence = com.example.alarm.AlarmNotificationScheduler.disabledCadenceKeys(dao)
      com.example.alarm.AlarmNotificationScheduler.scheduleAnniversaryNotification(context, ann, disabledCadence)
      _showAddAnniversaryDialog.value = false
      triggerFloatingHearts()
      showToast("Đã lưu ngày kỷ niệm & kích hoạt thông báo tự động! 🔔")
    }
  }

  fun updateAnniversaryDate(item: AnniversaryDateEntity) {
    viewModelScope.launch {
      repository.updateAnniversaryDate(item)
      val context = getApplication<Application>()
      if (item.notificationEnabled) {
        val disabledCadence = com.example.alarm.AlarmNotificationScheduler.disabledCadenceKeys(dao)
        com.example.alarm.AlarmNotificationScheduler.scheduleAnniversaryNotification(context, item, disabledCadence)
      } else {
        com.example.alarm.AlarmNotificationScheduler.cancelAnniversaryNotification(context, item.id)
      }
      showToast("Đã cập nhật ngày kỷ niệm!")
    }
  }

  fun deleteAnniversaryDate(id: Long) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      com.example.alarm.AlarmNotificationScheduler.cancelAnniversaryNotification(context, id)
      repository.deleteAnniversaryDate(id)
      showToast("Đã xóa ngày kỷ niệm khỏi thiết bị.")
    }
  }

  fun toggleAnniversaryNotification(item: AnniversaryDateEntity) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val willBeEnabled = !item.notificationEnabled
      val updated = item.copy(notificationEnabled = willBeEnabled)
      repository.updateAnniversaryDate(updated)
      if (willBeEnabled) {
        val disabledCadence = com.example.alarm.AlarmNotificationScheduler.disabledCadenceKeys(dao)
        val scheduled = com.example.alarm.AlarmNotificationScheduler.scheduleAnniversaryNotification(context, updated, disabledCadence)
        showToast(if (scheduled) "🔔 Đã bật thông báo kỷ niệm '${item.title}'" else "Đã bật thông báo '${item.title}'")
      } else {
        com.example.alarm.AlarmNotificationScheduler.cancelAnniversaryNotification(context, item.id)
        showToast("🔕 Đã tắt thông báo kỷ niệm '${item.title}'")
      }
    }
  }

  fun triggerTestAnniversaryNotification() {
    val context = getApplication<Application>()
    viewModelScope.launch {
      val anniversaries = repository.getAnniversaryDatesList()
      val firstAnn = anniversaries.firstOrNull { it.notificationEnabled } ?: anniversaries.firstOrNull()
      if (firstAnn != null) {
        com.example.alarm.ReminderAlarmReceiver.showNotification(
          context = context,
          title = "🎉 Thử nghiệm Kỷ Niệm: ${firstAnn.title} ❤️",
          message = "Còn ít ngày nữa là đến '${firstAnn.title}' (${firstAnn.dateText}). Đừng quên chuẩn bị món quà bất ngờ và một buổi tối lãng mạn cho người ấy nhé! 🎁✨",
          notificationId = 8888,
          channelId = com.example.alarm.ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
          targetTab = "calendar"
        )
      } else {
        com.example.alarm.AlarmNotificationScheduler.triggerInstantTest(context)
      }
      showToast("🔔 Đã gửi thông báo kỷ niệm thử nghiệm lên thanh trạng thái!")
    }
  }

  fun resyncAllAnniversaryAlarms() {
    val context = getApplication<Application>()
    viewModelScope.launch {
      val count = com.example.alarm.AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(context, dao)
      showToast("⏰ Đã quét & kích hoạt lại $count thông báo kỷ niệm từ cơ sở dữ liệu!")
    }
  }

  fun syncUpcomingMilestonesFromFirestore() {
    val rel = activeRelationship.value
    val startMillis = rel?.startDate ?: 0L
    val startDateText = if (rel?.startDateText.isNullOrBlank()) anniversaryDate.value else rel.startDateText
    val partnerName = partnerOnlineUser.value?.effectiveDisplayName ?: "người ấy"

    val count = com.example.alarm.LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(
      context = getApplication<Application>(),
      startDateMillis = startMillis,
      startDateText = startDateText,
      partnerName = partnerName
    )
    _upcomingMilestones.value = com.example.alarm.LoveAnniversaryMilestoneScheduler.getUpcomingMilestones(
      startDateMillis = startMillis,
      startDateText = startDateText
    )
    showToast("🔔 Đã đồng bộ & kích hoạt $count thông báo cột mốc từ Firestore!")
  }

  /**
   * Synchronize all enriched presets (milestones, gifts, badges, checklists, assets) from Cloud Firestore.
   */
  fun syncCloudData() {
    viewModelScope.launch {
      val success = repository.syncAllCloudPresets()
      if (success) {
        showToast("✨ Đã làm giàu & đồng bộ dữ liệu mới nhất từ Cloud Firestore!")
      } else {
        showToast("⚠️ Không thể kết nối Cloud Firestore, đang dùng dữ liệu lưu trữ.")
      }
    }
  }

  fun triggerTestMilestoneNotification() {
    val partnerName = partnerOnlineUser.value?.effectiveDisplayName ?: "người ấy"
    val sample = _upcomingMilestones.value.firstOrNull()?.title ?: "💎 Bách Nhật Yêu (100 Ngày)"
    com.example.alarm.LoveAnniversaryMilestoneScheduler.triggerInstantTestMilestone(
      context = getApplication<Application>(),
      partnerName = partnerName,
      sampleMilestoneTitle = sample
    )
    showToast("🔔 Đã kích hoạt thông báo cột mốc thử nghiệm lên thanh trạng thái!")
  }

  // Room Persistence for Gift Reminders
  fun addGiftReminder(
    title: String,
    recipient: String = "Người ấy",
    occasion: String = "Kỷ niệm ngày yêu",
    dueDateText: String = "",
    estimatedBudget: String = "",
    notes: String = ""
  ) {
    if (title.isBlank()) {
      showToast("Vui lòng nhập món quà cần nhắc nhở!")
      return
    }
    viewModelScope.launch {
      repository.addGiftReminder(
        title = title.trim(),
        recipient = recipient.trim().ifEmpty { "Người ấy" },
        occasion = occasion.trim().ifEmpty { "Kỷ niệm ngày yêu" },
        dueDateText = dueDateText.trim().ifEmpty { "Sớm nhất" },
        estimatedBudget = estimatedBudget.trim().ifEmpty { "Tùy chọn" },
        notes = notes.trim()
      )
      _showAddGiftReminderDialog.value = false
      triggerFloatingHearts()
      showToast("Đã lưu lời nhắc quà tặng vào cơ sở dữ liệu Room!")
    }
  }

  fun updateGiftReminder(item: GiftReminderEntity) {
    viewModelScope.launch {
      repository.updateGiftReminder(item)
      showToast("Đã cập nhật lời nhắc quà tặng!")
    }
  }

  fun toggleGiftReminderCompleted(item: GiftReminderEntity) {
    viewModelScope.launch {
      repository.toggleGiftReminderCompleted(item)
      val msg = if (!item.isCompleted) "🎁 Tuyệt vời! Đã hoàn thành chuẩn bị món quà!" else "Đã chuyển về danh sách chuẩn bị quà."
      showToast(msg)
    }
  }

  fun deleteGiftReminder(id: Long) {
    viewModelScope.launch {
      repository.deleteGiftReminder(id)
      showToast("Đã xóa lời nhắc quà tặng.")
    }
  }

  // Online 1-1 Set Love Search & Inspection State
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchedUser = MutableStateFlow<com.example.data.model.OnlineUserEntity?>(null)
  val searchedUser: StateFlow<com.example.data.model.OnlineUserEntity?> = _searchedUser.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
    if (query.isBlank()) {
      _searchedUser.value = null
    }
  }

  fun performSearch() {
    val query = _searchQuery.value.trim()
    if (query.isBlank()) {
      showToast("Vui lòng nhập mã ghép đôi, tên hoặc email!")
      return
    }
    viewModelScope.launch {
      _isSearching.value = true
      val user = onlineRepo.searchUserByCodeOrNameOrEmail(query)
      _searchedUser.value = user
      _isSearching.value = false
      if (user == null) {
        showToast("Không tìm thấy người dùng với thông tin: $query")
      } else {
        showToast("Đã tìm thấy thông tin của ${user.effectiveDisplayName}!")
      }
    }
  }

  suspend fun searchPartnerForSetLove(query: String): com.example.data.model.OnlineUserEntity? {
    return onlineRepo.searchUserByCodeOrNameOrEmail(query)
  }

  fun clearSearch() {
    _searchQuery.value = ""
    _searchedUser.value = null
  }

  // Identity & Date Verification before accepting invite
  private val _selectedInviteForVerification = MutableStateFlow<com.example.data.model.OnlineInviteEntity?>(null)
  val selectedInviteForVerification: StateFlow<com.example.data.model.OnlineInviteEntity?> = _selectedInviteForVerification.asStateFlow()

  fun inspectInvite(invite: com.example.data.model.OnlineInviteEntity) {
    _selectedInviteForVerification.value = invite
  }

  fun dismissInspectInvite() {
    _selectedInviteForVerification.value = null
  }

  // Edit My Profile Dialog & Flow
  private val _showEditProfileDialog = MutableStateFlow(false)
  val showEditProfileDialog: StateFlow<Boolean> = _showEditProfileDialog.asStateFlow()

  fun openEditProfileDialog() {
    _showEditProfileDialog.value = true
  }

  fun closeEditProfileDialog() {
    _showEditProfileDialog.value = false
  }

  fun updateMyProfile(
    name: String,
    birthDate: String,
    avatarUrl: String,
    gender: String,
    bio: String
  ) {
    viewModelScope.launch {
      val (success, message) = onlineRepo.updateMyProfile(name, birthDate, avatarUrl, gender, bio)
      showToast(message)
      if (success) {
        _showEditProfileDialog.value = false
        triggerFloatingHearts()
      }
    }
  }

  // Online 1-1 Set Love Actions
  fun sendSetLoveInvite(
    targetCodeOrLink: String,
    proposedStartDateMillis: Long = System.currentTimeMillis(),
    loveNote: String = ""
  ) {
    if (targetCodeOrLink.isBlank()) {
      showToast("Vui lòng nhập mã ghép đôi hoặc dán link đối phương!")
      return
    }
    viewModelScope.launch {
      val (success, message) = onlineRepo.sendSetLoveInvite(targetCodeOrLink, proposedStartDateMillis, loveNote)
      showToast(message)
      if (success) {
        _searchQuery.value = ""
        _searchedUser.value = null
        triggerFloatingHearts()
      }
    }
  }

  fun acceptSetLoveInvite(inviteId: String = "", confirmedStartDateMillis: Long? = null) {
    viewModelScope.launch {
      val (success, message) = onlineRepo.acceptSetLoveInvite(inviteId, confirmedStartDateMillis)
      showToast(message)
      _selectedInviteForVerification.value = null
      if (success) {
        triggerFloatingHearts(isMilestone = true)
      }
    }
  }

  fun rejectSetLoveInvite(inviteId: String = "") {
    viewModelScope.launch {
      val (success, message) = onlineRepo.rejectSetLoveInvite(inviteId)
      showToast(message)
      _selectedInviteForVerification.value = null
    }
  }

  fun cancelSentInvite() {
    viewModelScope.launch {
      val (success, message) = onlineRepo.cancelSentInvite()
      showToast(message)
      _searchQuery.value = ""
      _searchedUser.value = null
    }
  }

  fun requestBreakup() {
    viewModelScope.launch {
      val (success, message) = onlineRepo.requestBreakup()
      showToast(message)
    }
  }

  fun confirmBreakup() {
    viewModelScope.launch {
      val (success, message) = onlineRepo.confirmBreakup()
      showToast(message)
    }
  }

  fun rejectBreakup() {
    viewModelScope.launch {
      val (success, message) = onlineRepo.rejectBreakup()
      showToast(message)
    }
  }

  fun forceBreakup() {
    viewModelScope.launch {
      val (success, message) = onlineRepo.forceBreakup()
      showToast(message)
    }
  }

  fun toggleInterest(interestKey: String) {
    viewModelScope.launch {
      onlineRepo.toggleInterest(interestKey)
      showToast("Đã cập nhật sở thích cá nhân")
    }
  }

  fun switchDemoUser() {
    onlineRepo.switchDemoUser()
    showToast("Đã chuyển đổi tài khoản thử nghiệm 1-1")
  }

  fun logout() {
    viewModelScope.launch {
      authRepo.logout()
      showToast("Đã đăng xuất an toàn")
    }
  }

  fun deleteAccountAndData(onComplete: (() -> Unit)? = null) {
    viewModelScope.launch {
      val result = authRepo.deleteCurrentAccount()
      if (result.isSuccess) {
        showToast("Đã xóa toàn bộ tài khoản và dữ liệu thành công")
        onComplete?.invoke()
      } else {
        showToast("Không thể xóa tài khoản: ${result.exceptionOrNull()?.message ?: "Lỗi không xác định"}")
      }
    }
  }

  fun lockApp() {
    authRepo.lockApp()
  }

  fun setAppPin(pin: String) {
    viewModelScope.launch {
      val res = authRepo.setAppPin(pin)
      showToast(res.second)
    }
  }

  fun togglePinEnabled(enabled: Boolean) {
    viewModelScope.launch {
      val res = authRepo.togglePinEnabled(enabled)
      showToast(res.second)
    }
  }
  override fun onCleared() {
    // The app-lifetime SyncCoordinator holds a callback into this ViewModel's repository; drop it
    // (and its Firestore listeners) so the cleared ViewModel can be collected. restoreSession restarts it.
    try { com.example.di.AppServiceLocator.syncCoordinator.stop() } catch (_: Exception) {}
    super.onCleared()
  }
}
