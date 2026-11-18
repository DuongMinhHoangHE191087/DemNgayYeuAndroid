package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.example.alarm.AlarmNotificationScheduler
import com.example.alarm.AndroidReminderScheduler
import com.example.alarm.LoveAnniversaryMilestoneScheduler
import com.example.alarm.LoveMilestoneArgs
import com.example.alarm.LoveMilestoneInfo
import com.example.alarm.ReminderAlarmReceiver
import com.example.alarm.plan.ReminderKey
import com.example.alarm.plan.ReminderScheduler
import com.example.data.auth.AndroidAuthGateway
import com.example.data.db.AccountDataVault
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.CustomReminderEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.data.model.LoveBadgeEntity
import com.example.data.model.MilestoneEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.PRIVACY_PRIVATE
import com.example.data.model.RelationshipStatus
import com.example.data.model.ReminderCadenceEntity
import com.example.data.model.SharedMemoryEntity
import com.example.data.model.SubscriptionTier
import com.example.data.model.UserRole
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthState
import com.example.data.repository.InLoveRepository
import com.example.data.repository.OnlineCoupleRepository
import com.example.di.AppServiceLocator
import com.example.domain.media.MediaKind
import com.example.domain.media.MemoryMediaFile
import com.example.domain.media.ResolveMemoryMediaUrlUseCase
import com.example.domain.media.SaveSharedMemoryOutcome
import com.example.domain.media.SaveSharedMemoryUseCase
import com.example.domain.media.UploadMemoryMediaUseCase
import com.example.domain.media.formatMediaSize
import com.example.domain.usecase.GetPersonalizedGiftSuggestionsUseCase
import com.example.domain.usecase.PersonalizedGiftSuggestion
import com.example.shared.ai.AiGiftAdviceBundle
import com.example.shared.ai.RelationshipTone
import com.example.shared.calendar.MonthiversaryOccurrence
import com.example.shared.calendar.NextOccasionReadiness
import com.example.shared.dossier.PartnerDossier
import com.example.shared.monetization.ProTierCatalog
import com.example.shared.monetization.SacredZone
import com.example.shared.monetization.SoftPaywallPrompt
import com.example.shared.sync.OnlineSyncPhase
import com.example.shared.sync.OnlineSyncPhaseResolver
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocaleManager
import com.example.ui.util.ProfileUtils
import com.example.ui.viewmodel.controllers.AiGiftConciergeController
import com.example.ui.viewmodel.controllers.AnniversaryReadinessController
import com.example.ui.viewmodel.controllers.CoupleProfileController
import com.example.ui.viewmodel.controllers.DialogUiStateController
import com.example.ui.viewmodel.controllers.PairingAndAccountController
import com.example.ui.viewmodel.controllers.PartnerDossierController
import com.example.ui.viewmodel.controllers.SecretNoteOutcome
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val COUPLE_LOADED_WAIT_MS = 3_000L

private data class OnlineSyncSignals(
  val auth: AuthState,
  val user: OnlineUserEntity,
  val rel: OnlineRelationshipEntity?,
  val relStatus: String,
  val online: Boolean,
)

class InLoveViewModel @JvmOverloads constructor(
  application: Application,
  reminderScheduler: ReminderScheduler? = null
) : AndroidViewModel(application) {

  private val repository: InLoveRepository
  val onlineRepo: OnlineCoupleRepository
  val authRepo: AuthRepository
  val authState: StateFlow<AuthState>

  private val uiState = DialogUiStateController()
  private val coupleController: CoupleProfileController
  private val pairingController: PairingAndAccountController
  private val readinessController = AnniversaryReadinessController()
  private val dossierController: PartnerDossierController
  private val conciergeController: AiGiftConciergeController

  private val saveSharedMemoryUseCase by lazy {
    SaveSharedMemoryUseCase(UploadMemoryMediaUseCase(AppServiceLocator.memoryMediaRepository))
  }
  private val resolveMemoryMediaUrlUseCase by lazy {
    ResolveMemoryMediaUrlUseCase(AppServiceLocator.memoryMediaRepository)
  }

  private val _upcomingMilestones = MutableStateFlow<List<LoveMilestoneInfo>>(emptyList())
  val upcomingMilestones: StateFlow<List<LoveMilestoneInfo>> = _upcomingMilestones.asStateFlow()

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

  val currentOnlineUser: StateFlow<OnlineUserEntity>
  val partnerOnlineUser: StateFlow<OnlineUserEntity?>
  val activeRelationship: StateFlow<OnlineRelationshipEntity?>
  val relationshipStatus: StateFlow<String>
  val mutualInterests: StateFlow<Set<String>>
  val incomingInvite: StateFlow<OnlineInviteEntity?>
  val outgoingInvite: StateFlow<OnlineInviteEntity?>

  val userRole: StateFlow<UserRole>
  val subscriptionTier: StateFlow<SubscriptionTier>
  val isVip: StateFlow<Boolean>
  val isOnline: StateFlow<Boolean>
  val onlineSyncPhase: StateFlow<OnlineSyncPhase>

  val showVipDialog: StateFlow<Boolean> = uiState.showVipDialog
  val showPairingScreen: StateFlow<Boolean> = uiState.showPairingScreen
  val selectedTab: StateFlow<Int> = uiState.selectedTab
  val calendarFilter: StateFlow<String> = uiState.calendarFilter
  val giftCategory: StateFlow<String> = uiState.giftCategory
  val toastMessage: StateFlow<String?> = uiState.toastMessage
  val isAllNotificationsRead: StateFlow<Boolean> = uiState.isAllNotificationsRead
  val showNotifications: StateFlow<Boolean> = uiState.showNotifications
  val showAddReminderDialog: StateFlow<Boolean> = uiState.showAddReminderDialog
  val showAddMilestoneDialog: StateFlow<Boolean> = uiState.showAddMilestoneDialog
  val showAddChecklistDialog: StateFlow<Boolean> = uiState.showAddChecklistDialog
  val showAddAnniversaryDialog: StateFlow<Boolean> = uiState.showAddAnniversaryDialog
  val prefilledAnniversaryDate: StateFlow<String?> = uiState.prefilledAnniversaryDate
  val showAddGiftReminderDialog: StateFlow<Boolean> = uiState.showAddGiftReminderDialog
  val showSetAlarmDialog: StateFlow<Boolean> = uiState.showSetAlarmDialog
  val alarmDialogPresetTitle: StateFlow<String> = uiState.alarmDialogPresetTitle
  val alarmDialogPresetMessage: StateFlow<String> = uiState.alarmDialogPresetMessage
  val alarmDialogReminderId: StateFlow<Long?> = uiState.alarmDialogReminderId
  val selectedGiftDetail: StateFlow<GiftIdeaEntity?> = uiState.selectedGiftDetail
  val showVipProposalDetail: StateFlow<Boolean> = uiState.showVipProposalDetail
  val showLanguageDialog: StateFlow<Boolean> = uiState.showLanguageDialog
  val showGuideDialog: StateFlow<Boolean> = uiState.showGuideDialog
  val showWallpaperDialog: StateFlow<Boolean> = uiState.showWallpaperDialog
  val showMemoryDialog: StateFlow<Boolean> = uiState.showMemoryDialog
  val floatingHeartsTrigger: StateFlow<Long> = uiState.floatingHeartsTrigger
  val isMilestoneCelebration: StateFlow<Boolean> = uiState.isMilestoneCelebration
  val selectedMemoryDetail: StateFlow<SharedMemoryEntity?> = uiState.selectedMemoryDetail
  val selectedAnniversaryFilter: StateFlow<String> = uiState.selectedAnniversaryFilter
  val selectedBadge: StateFlow<LoveBadgeEntity?> = uiState.selectedBadge
  val showBadgeShowcaseDialog: StateFlow<Boolean> = uiState.showBadgeShowcaseDialog
  val showEditCoupleDialog: StateFlow<Boolean> = uiState.showEditCoupleDialog
  val showAuthScreen: StateFlow<Boolean> = uiState.showAuthScreen
  val selectedInviteForVerification: StateFlow<OnlineInviteEntity?> = uiState.selectedInviteForVerification
  val showEditProfileDialog: StateFlow<Boolean> = uiState.showEditProfileDialog

  private val _appLanguage = MutableStateFlow(LocaleManager.getInitialLanguage(application))
  val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

  private val _isFirstLaunch = MutableStateFlow(LocaleManager.isFirstLaunch(application))
  val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch.asStateFlow()

  private val _notificationsEnabled = MutableStateFlow(AlarmNotificationScheduler.anniversaryNotificationsEnabled(application))
  val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

  private val _soundEnabled = MutableStateFlow(LocaleManager.isSoundEnabled(application))
  val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

  private val _selectedWallpaperUrl = MutableStateFlow(
    LocaleManager.getSavedWallpaperUrl(application)
      ?: "https://images.unsplash.com/photo-1522383225653-ed111181a951?q=80&w=1080&auto=format&fit=crop"
  )
  val selectedWallpaperUrl: StateFlow<String> = _selectedWallpaperUrl.asStateFlow()

  val boyName: StateFlow<String>
  val boyBirthDate: StateFlow<String>
  val boyAge: StateFlow<Int>
  val boyZodiac: StateFlow<String>
  val boyAvatarUrl: StateFlow<String>
  val girlName: StateFlow<String>
  val girlBirthDate: StateFlow<String>
  val girlAge: StateFlow<Int>
  val girlZodiac: StateFlow<String>
  val girlAvatarUrl: StateFlow<String>
  val loveTitle: StateFlow<String>
  val loveDays: StateFlow<Int>
  val anniversaryDate: StateFlow<String>

  val searchQuery: StateFlow<String>
  val searchedUser: StateFlow<OnlineUserEntity?>
  val isSearching: StateFlow<Boolean>

  val partnerDossier: StateFlow<PartnerDossier>
  val showPartnerDossierSheet: StateFlow<Boolean>
  val dossierSoftPaywallPrompt: StateFlow<SoftPaywallPrompt?>

  val nextOccasionReadiness: StateFlow<NextOccasionReadiness?> = readinessController.nextReadiness
  val upcomingOccasionReadiness: StateFlow<List<NextOccasionReadiness>> = readinessController.upcomingReadiness
  val nextMonthiversary: StateFlow<MonthiversaryOccurrence?> = readinessController.nextMonthiversary
  val isSacredCelebrationToday: StateFlow<Boolean> = readinessController.isSacredCelebrationToday
  val nextThatTichIso: StateFlow<String?> = readinessController.nextThatTichIso

  val selectedConciergeTone: StateFlow<RelationshipTone>
  val isAiConciergeLoading: StateFlow<Boolean>
  val aiAdviceBundle: StateFlow<AiGiftAdviceBundle?>
  val aiRemainingAllowance: StateFlow<Int>
  val aiSoftPaywallPrompt: StateFlow<SoftPaywallPrompt?>

  private val dao: InLoveDao get() = AppDatabase.getDatabase(getApplication()).inLoveDao()

  // Android keeps its AlarmManager path behind ReminderScheduler; tests inject a fake via constructor.
  private val scheduler: ReminderScheduler by lazy {
    reminderScheduler ?: AndroidReminderScheduler(application, dao, { loveMilestoneArgsIfFresh() }, viewModelScope)
  }

  @Volatile private var loveStateEpoch = AccountDataVault.epoch.get()
  @Volatile private var loveEnded = false

  internal fun loveMilestoneArgs(): LoveMilestoneArgs {
    val rel = activeRelationship.value
    val startDateText = if (rel?.startDateText.isNullOrBlank()) anniversaryDate.value else rel.startDateText
    return LoveMilestoneArgs(rel?.startDate ?: 0L, startDateText, partnerOnlineUser.value?.effectiveDisplayName.orEmpty())
  }

  internal fun loveMilestoneArgsIfFresh(): LoveMilestoneArgs? =
    if (loveStateEpoch == AccountDataVault.epoch.get() && !loveEnded) loveMilestoneArgs() else null

  private fun anniversaryKeys(id: Long) = listOf(ReminderKey.Advance(id), ReminderKey.DayOf(id))

  init {
    val database = AppDatabase.getDatabase(application)
    repository = InLoveRepository(database.inLoveDao(), application)
    onlineRepo = OnlineCoupleRepository(database.inLoveDao(), application, viewModelScope)
    AppServiceLocator.initialize(application)

    authRepo = AuthRepository(
      database.inLoveDao(), onlineRepo, application, viewModelScope,
      auth = AndroidAuthGateway(),
      hooks = AppServiceLocator.sessionHooks(
        { onlineRepo.refreshState() },
        { uid, p, key -> onlineRepo.applyRemoteProfile(uid, p, key) }
      ),
      vault = AccountDataVault(
        application, database,
        beforeSwitch = {
          val s = scheduler
          if (s is AndroidReminderScheduler) s.cancelAllNow()
        },
        afterSwitch = {
          val s = scheduler
          if (s !is AndroidReminderScheduler) s.cancelAll()
          s.requestResync()
          viewModelScope.launch {
            runCatching {
              AppServiceLocator.partnerDossierRepository.loadCurrentScope()
              AppServiceLocator.cloudPartnerDossierSync.syncNow()
              AppServiceLocator.aiGiftAdvisorRepository.refreshQuotaState()
              conciergeController.syncAllowance(isVip.value)
            }
          }
        },
        afterAdopt = { before, after ->
          if (loveStateEpoch == before) loveStateEpoch = after
          scheduler.requestResync()
        }
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

    val isEngProvider = { _appLanguage.value == AppLanguage.EN }
    coupleController = CoupleProfileController(application, repository, database.inLoveDao(), onlineRepo, viewModelScope, uiState, isEngProvider)
    boyName = coupleController.boyName
    boyBirthDate = coupleController.boyBirthDate
    boyAge = coupleController.boyAge
    boyZodiac = coupleController.boyZodiac
    boyAvatarUrl = coupleController.boyAvatarUrl
    girlName = coupleController.girlName
    girlBirthDate = coupleController.girlBirthDate
    girlAge = coupleController.girlAge
    girlZodiac = coupleController.girlZodiac
    girlAvatarUrl = coupleController.girlAvatarUrl
    loveTitle = coupleController.loveTitle
    loveDays = coupleController.loveDays
    anniversaryDate = coupleController.anniversaryDate

    pairingController = PairingAndAccountController(onlineRepo, authRepo, viewModelScope, uiState, isEngProvider)
    searchQuery = pairingController.searchQuery
    searchedUser = pairingController.searchedUser
    isSearching = pairingController.isSearching

    dossierController = PartnerDossierController(
      store = AppServiceLocator.partnerDossierRepository,
      onDossierSaved = { updated -> AppServiceLocator.cloudPartnerDossierSync.schedulePush(updated) },
    )
    partnerDossier = dossierController.dossier
    showPartnerDossierSheet = dossierController.showDossierSheet
    dossierSoftPaywallPrompt = dossierController.softPaywallPrompt

    conciergeController = AiGiftConciergeController(
      gateway = AppServiceLocator.aiGiftAdvisorRepository,
      remainingAllowanceProvider = { vip ->
        if (vip) ProTierCatalog.VIP_AI_GENERATIONS_PER_MONTH
        else AppServiceLocator.monetizationFacade.remainingFreeAiQuota(
          AppServiceLocator.aiGiftAdvisorRepository.readCurrentMonthUsedCount()
        )
      }
    )
    selectedConciergeTone = conciergeController.selectedTone
    isAiConciergeLoading = conciergeController.isLoading
    aiAdviceBundle = conciergeController.adviceBundle
    aiRemainingAllowance = conciergeController.remainingAllowance
    aiSoftPaywallPrompt = conciergeController.softPaywallPrompt

    userRole = combine(currentOnlineUser, authState) { user, auth ->
      if (auth is AuthState.Authenticated) auth.account.userRole else user.userRole
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserRole.USER_FREE)

    subscriptionTier = combine(currentOnlineUser, authState) { user, auth ->
      if (auth is AuthState.Authenticated) auth.account.tier else user.tier
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubscriptionTier.FREE)

    val billingManager = AppServiceLocator.billingManager
    val adsManager = AppServiceLocator.adsManager
    isOnline = AppServiceLocator.networkMonitor.isOnline

    onlineSyncPhase = combine(
      combine(authState, currentOnlineUser, activeRelationship, relationshipStatus, isOnline) { auth, user, rel, relStatus, online ->
        OnlineSyncSignals(auth, user, rel, relStatus, online)
      },
      incomingInvite,
      outgoingInvite,
    ) { signals, inInvite, outInvite ->
      val signedIn = signals.auth is AuthState.Authenticated ||
        (signals.user.uid.isNotBlank() && signals.user.uid != OnlineCoupleRepository.GUEST_UID)
      val breakupPending = signals.rel?.status == RelationshipStatus.PENDING_BREAKUP
      val coupled = signals.relStatus == OnlineStatus.COUPLED || breakupPending
      OnlineSyncPhaseResolver.resolve(
        isSignedIn = signedIn,
        isCoupled = coupled,
        isBreakupPending = breakupPending,
        hasOutgoingInvite = outInvite != null,
        hasIncomingInvite = inInvite != null,
        isOnline = signals.online,
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnlineSyncPhase.GUEST_LOCAL)

    viewModelScope.launch {
      var wasOnline = isOnline.value
      isOnline.collect { nowOnline ->
        val reconnected = !wasOnline && nowOnline
        wasOnline = nowOnline
        if (reconnected) {
          runCatching { onlineRepo.refreshState() }
          runCatching { AppServiceLocator.cloudPartnerDossierSync.syncNow() }
        }
      }
    }

    isVip = combine(currentOnlineUser, authState, AppServiceLocator.entitlementRepository.isVipUser) { _, auth, billingVip ->
      val isAdmin = auth is AuthState.Authenticated && auth.account.userRole == UserRole.ADMIN
      billingVip || isAdmin
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    viewModelScope.launch {
      isVip.collect { vip ->
        adsManager.setVipStatus(vip)
        conciergeController.syncAllowance(vip)
      }
    }

    viewModelScope.launch {
      combine(billingManager.hasSyncedOnce, billingManager.isVipUser) { synced, vip -> synced to vip }
        .collect { (synced, isBillingVip) ->
          if (!synced) return@collect
          val currentAuth = authState.value
          val uid = if (currentAuth is AuthState.Authenticated) currentAuth.account.uid else currentOnlineUser.value.uid
          if (uid.isNotBlank()) {
            val isAdmin = currentAuth is AuthState.Authenticated && currentAuth.account.userRole == UserRole.ADMIN
            if (isBillingVip) {
              val mappedTier = when (billingManager.activeProductId.value) {
                com.example.billing.BillingManager.PRODUCT_VIP_MONTHLY -> SubscriptionTier.VIP_MONTHLY
                com.example.billing.BillingManager.PRODUCT_VIP_LIFETIME -> SubscriptionTier.LIFETIME
                else -> SubscriptionTier.VIP_YEARLY
              }
              authRepo.updateUserSubscription(uid, UserRole.USER_VIP, mappedTier)
              onlineRepo.setCurrentUserId(uid)
            } else if (!isAdmin) {
              authRepo.updateUserSubscription(uid, UserRole.USER_FREE, SubscriptionTier.FREE)
            }
          }
        }
    }

    milestones = repository.milestones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    giftIdeas = repository.giftIdeas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    checklistItems = repository.checklistItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    customReminders = repository.customReminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    reminderCadences = repository.reminderCadences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    coupleProfile = repository.coupleProfile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    sharedMemories = repository.sharedMemories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    loveBadges = repository.loveBadges.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    anniversaryDates = repository.anniversaryDates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    giftReminders = repository.giftReminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
      AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(application, dao)
      runCatching {
        AppServiceLocator.partnerDossierRepository.loadCurrentScope()
        AppServiceLocator.cloudPartnerDossierSync.syncNow()
      }
    }

    coupleController.startCollectors()

    viewModelScope.launch {
      combine(anniversaryDate, girlBirthDate, partnerDossier, coupleProfile) { annDate, partnerBirth, dossier, profile ->
        val effectiveRegion = dossier.occasionRegion.ifBlank { profile?.occasionRegion.orEmpty() }.ifBlank { "VN" }
        Triple(annDate, partnerBirth, effectiveRegion)
      }.collect { (annDate, partnerBirth, region) ->
        readinessController.refresh(
          anniversaryDateText = annDate,
          partnerBirthDateText = partnerBirth,
          region = region,
        )
        adsManager.setCelebrationDay(readinessController.isTodayCelebration())
      }
    }

    viewModelScope.launch {
      var milestonesCancelled = false
      combine(
        activeRelationship,
        anniversaryDate,
        partnerOnlineUser,
        dao.observeRelationships().map { r -> r.isNotEmpty() && r.none { it.status != RelationshipStatus.TERMINATED } }
      ) { rel, annDate, partner, ended ->
        Pair(Triple(rel, annDate, partner), ended && rel == null)
      }.collect { (state, ended) ->
        val (rel, annDate, partner) = state
        loveStateEpoch = AccountDataVault.epoch.get()
        loveEnded = ended
        if (ended) {
          _upcomingMilestones.value = emptyList()
          if (!milestonesCancelled) {
            milestonesCancelled = true
            LoveAnniversaryMilestoneScheduler.cancelMilestones(application)
          }
          return@collect
        }
        milestonesCancelled = false
        val startMillis = rel?.startDate ?: 0L
        val startDateText = if (rel?.startDateText.isNullOrBlank()) annDate else rel.startDateText
        val partnerName = partner?.effectiveDisplayName.orEmpty()
        _upcomingMilestones.value = LoveAnniversaryMilestoneScheduler.getUpcomingMilestones(startMillis, startDateText)
        LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(
          context = application,
          startDateMillis = startMillis,
          startDateText = startDateText,
          partnerName = partnerName
        )
      }
    }
  }

  val coupleLoaded: StateFlow<Boolean> = flow {
    withTimeoutOrNull(COUPLE_LOADED_WAIT_MS) { authRepo.restoreJob.join() }
    emit(true)
  }.combine(coupleController.roomSeen) { restored, seen -> restored && seen }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private fun tr(vi: String, en: String) = if (_appLanguage.value == AppLanguage.VI) vi else en

  private fun parseLikes(csv: String?): Set<String> =
    csv.orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

  fun setVipDialogVisible(visible: Boolean) = uiState.setVipDialogVisible(visible)
  fun openPairingScreen() = uiState.openPairingScreen()
  fun closePairingScreen() = uiState.closePairingScreen()
  fun setTab(index: Int) = uiState.setTab(index)
  fun setCalendarFilter(filter: String) = uiState.setCalendarFilter(filter)
  fun setGiftCategory(cat: String) = uiState.setGiftCategory(cat)
  fun showToast(msg: String) = uiState.showToast(msg)
  fun clearToast() = uiState.clearToast()
  fun setShowNotifications(show: Boolean) = uiState.setShowNotifications(show)
  fun selectAnniversaryFilter(filter: String) = uiState.selectAnniversaryFilter(filter)
  fun openAuthScreen() = uiState.openAuthScreen()
  fun closeAuthScreen() = uiState.closeAuthScreen()
  fun openEditCoupleDialog() = uiState.openEditCoupleDialog()
  fun closeEditCoupleDialog() = uiState.closeEditCoupleDialog()
  fun openAddReminderDialog() = uiState.openAddReminderDialog()
  fun closeAddReminderDialog() = uiState.closeAddReminderDialog()
  fun openAddMilestoneDialog() = uiState.openAddMilestoneDialog()
  fun closeAddMilestoneDialog() = uiState.closeAddMilestoneDialog()
  fun openAddChecklistDialog() = uiState.openAddChecklistDialog()
  fun closeAddChecklistDialog() = uiState.closeAddChecklistDialog()
  fun openAddAnniversaryDialog(initialDate: String? = null) = uiState.openAddAnniversaryDialog(initialDate)
  fun closeAddAnniversaryDialog() = uiState.closeAddAnniversaryDialog()
  fun openAddGiftReminderDialog() = uiState.openAddGiftReminderDialog()
  fun closeAddGiftReminderDialog() = uiState.closeAddGiftReminderDialog()
  fun openSetAlarmDialog(title: String = "", message: String = "", reminderId: Long? = null) =
    uiState.openSetAlarmDialog(title, message, reminderId)
  fun closeSetAlarmDialog() = uiState.closeSetAlarmDialog()
  fun openGiftDetail(item: GiftIdeaEntity) = uiState.openGiftDetail(item)
  fun closeGiftDetail() = uiState.closeGiftDetail()
  fun openVipProposal() = uiState.openVipProposal()
  fun closeVipProposal() = uiState.closeVipProposal()
  fun openLanguageDialog() = uiState.openLanguageDialog()
  fun closeLanguageDialog() = uiState.closeLanguageDialog()
  fun openGuideDialog() = uiState.openGuideDialog()
  fun closeGuideDialog() = uiState.closeGuideDialog()
  fun openWallpaperDialog() = uiState.openWallpaperDialog()
  fun closeWallpaperDialog() = uiState.closeWallpaperDialog()
  fun openMemoryDialog() = uiState.openMemoryDialog()
  fun closeMemoryDialog() = uiState.closeMemoryDialog()
  fun openMemoryDetail(memory: SharedMemoryEntity) = uiState.openMemoryDetail(memory)
  fun closeMemoryDetail() = uiState.closeMemoryDetail()
  fun selectBadge(badge: LoveBadgeEntity?) = uiState.selectBadge(badge)
  fun openBadgeShowcase() = uiState.openBadgeShowcase()
  fun closeBadgeShowcase() = uiState.closeBadgeShowcase()
  fun inspectInvite(invite: OnlineInviteEntity) = uiState.inspectInvite(invite)
  fun dismissInspectInvite() = uiState.dismissInspectInvite()
  fun openEditProfileDialog() = uiState.openEditProfileDialog()
  fun closeEditProfileDialog() = uiState.closeEditProfileDialog()

  fun triggerFloatingHearts(isMilestone: Boolean = false) = uiState.triggerFloatingHearts(isMilestone)

  fun completeFirstLaunch() {
    _isFirstLaunch.value = false
    LocaleManager.setFirstLaunchCompleted(getApplication())
  }

  fun setNotificationsEnabled(enabled: Boolean) {
    _notificationsEnabled.value = enabled
    AlarmNotificationScheduler.setAnniversaryNotificationsEnabled(getApplication(), enabled)
    scheduler.requestResync()
  }

  fun setSoundEnabled(enabled: Boolean) {
    _soundEnabled.value = enabled
    LocaleManager.saveSoundEnabled(getApplication(), enabled)
  }

  fun markAllNotificationsRead() {
    uiState.markAllNotificationsRead()
    showToast(tr("Đã đánh dấu đã đọc tất cả thông báo!", "All notifications marked as read"))
  }

  fun celebrateMilestoneAnniversary(days: Int) {
    triggerFloatingHearts(isMilestone = true)
    showToast(
      tr(
        "🎉 Chúc Mừng Cột Mốc $days Ngày Yêu! Mưa tim ngập tràn chúc phúc đôi bạn! 💕✨",
        "🎉 Milestone Reached: $days Days Together! Showering love hearts! 💕✨"
      )
    )
  }

  fun saveLocalAvatar(uri: Uri, isPartner: Boolean): String = try {
    val context = getApplication<Application>()
    val avatarsDir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
    val prefix = if (isPartner) "partner_avatar_" else "user_avatar_"
    val destFile = File(avatarsDir, "$prefix${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
      destFile.outputStream().use { output -> input.copyTo(output) }
    }
    destFile.toURI().toString()
  } catch (e: Exception) {
    android.util.Log.e("InLoveViewModel", "Error saving local avatar: ${e.message}")
    ""
  }

  fun deleteUnsavedAvatar(url: String) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        ProfileUtils.staleLocalAvatar(url, "", File(getApplication<Application>().filesDir, "avatars"))?.delete()
      }
    }
  }

  fun savePartnerPreferences(likesCsv: String, budgetMaxVnd: Long, occasionRegion: String) {
    viewModelScope.launch {
      repository.savePartnerPreferences(likesCsv.trim(), budgetMaxVnd, occasionRegion)
      if (repository.getCoupleProfileSync() != null) {
        runCatching { dossierController.syncLegacyPreferences(likesCsv, budgetMaxVnd, occasionRegion) }
      }
      showToast(tr("Đã lưu sở thích của đối tác ❤️", "Partner preferences saved ❤️"))
    }
  }

  // Partner Dossier & Secret Notes
  fun openPartnerDossierSheet() = dossierController.openDossierSheet()
  fun closePartnerDossierSheet() = dossierController.closeDossierSheet()
  fun dismissDossierSoftPaywall() = dossierController.dismissPaywallPrompt()

  fun savePartnerDossier(updated: PartnerDossier) {
    viewModelScope.launch {
      dossierController.saveDossier(updated)
      closePartnerDossierSheet()
      triggerFloatingHearts()
      showToast(tr("Đã lưu Hồ Sơ Người Thương", "Partner dossier saved"))
    }
  }

  fun addSecretSurpriseNote(
    noteText: String,
    occasionHint: String = "",
    estimatedPriceVnd: Long = 0L,
    targetUrlOrShop: String = "",
  ) {
    viewModelScope.launch {
      when (
        dossierController.addSecretNote(
          noteText = noteText,
          occasionHint = occasionHint,
          isVip = isVip.value,
          estimatedPriceVnd = estimatedPriceVnd,
          targetUrlOrShop = targetUrlOrShop,
        )
      ) {
        is SecretNoteOutcome.Added -> showToast(tr("Đã lưu gợi ý bí mật", "Secret hint saved"))
        is SecretNoteOutcome.QuotaReached -> showToast(
          tr("Đã đạt giới hạn ghi chú bí mật miễn phí", "Free secret notes limit reached")
        )
        SecretNoteOutcome.BlankIgnored -> Unit
      }
    }
  }

  fun toggleSecretNotePurchased(noteId: String, purchased: Boolean) {
    viewModelScope.launch { dossierController.toggleSecretNotePurchased(noteId, purchased) }
  }

  fun removeSecretNote(noteId: String) {
    viewModelScope.launch { dossierController.removeSecretNote(noteId) }
  }

  // AI Gift & Love-Note Concierge
  fun selectConciergeTone(tone: RelationshipTone) = conciergeController.selectTone(tone)
  fun dismissAiSoftPaywall() = conciergeController.dismissSoftPaywall()

  fun generateAiGiftAdvice(customOccasionTitle: String? = null) {
    viewModelScope.launch {
      conciergeController.generateAdvice(
        dossier = partnerDossier.value,
        readiness = nextOccasionReadiness.value,
        loveDays = loveDays.value,
        isVip = isVip.value,
        isEnglish = _appLanguage.value == AppLanguage.EN,
        customOccasionTitle = customOccasionTitle,
      )
    }
  }

  fun enterSacredZone(zone: SacredZone) = runCatching { AppServiceLocator.adsManager.enterSacredZone(zone) }
  fun exitSacredZone(zone: SacredZone) = runCatching { AppServiceLocator.adsManager.exitSacredZone(zone) }

  fun toggleChecklist(item: ChecklistItemEntity) {
    viewModelScope.launch { repository.toggleChecklistItem(item) }
  }

  fun renameChecklist(item: ChecklistItemEntity, text: String) {
    if (text.isBlank()) return
    viewModelScope.launch { repository.renameChecklistItem(item, text.trim()) }
  }

  fun addChecklist(text: String) {
    if (text.isBlank()) return
    viewModelScope.launch {
      repository.addChecklistItem(text.trim())
      showToast(tr("Đã thêm vào checklist chuẩn bị!", "Added to your checklist!"))
    }
  }

  fun toggleGiftFavorite(item: GiftIdeaEntity) {
    viewModelScope.launch {
      repository.toggleGiftFavorite(item)
      uiState.syncGiftDetailFavorite(item)
      showToast(if (!item.isFavorited) tr("Đã lưu ý tưởng quà tặng này!", "Gift idea saved!") else tr("Đã bỏ lưu ý tưởng", "Gift idea removed"))
    }
  }

  fun toggleCadence(item: ReminderCadenceEntity) {
    viewModelScope.launch {
      repository.toggleCadence(item)
      scheduler.requestResync()
      showToast(if (!item.isEnabled) tr("Đã bật: ${item.label}", "On: ${item.label}") else tr("Đã tắt: ${item.label}", "Off: ${item.label}"))
    }
  }

  fun addCustomReminder(title: String, dateText: String, note: String) {
    if (title.isBlank()) {
      showToast(tr("Vui lòng nhập tên lời nhắc nhé!", "Please enter a reminder name"))
      return
    }
    viewModelScope.launch {
      repository.addCustomReminder(title, dateText, note)
      uiState.closeAddReminderDialog()
      triggerFloatingHearts()
      showToast(tr("Đã thêm thành công lời nhắc hẹn hò mới!", "Reminder added!"))
    }
  }

  fun scheduleReminderAlarm(title: String, message: String, triggerAtMillis: Long, reminderId: Long?) {
    viewModelScope.launch {
      val formatted = AlarmNotificationScheduler.formatAlarmTime(triggerAtMillis)
      val existing = reminderId?.takeIf { repository.hasCustomReminder(it) }
      val id = existing ?: repository.addCustomReminder(title, formatted.substringAfter(" - "), message)
      if (triggerAtMillis > AlarmNotificationScheduler.nowMillis()) {
        repository.setCustomReminderAlarm(id, triggerAtMillis, formatted)
        scheduler.requestResync()
        val exactWarning = if (scheduler.isExactDeliveryAvailable()) "" else tr(" Quyền báo thức chính xác đang tắt, báo thức có thể trễ.", " Exact alarms are off, so the alarm may be late.")
        showToast(tr("⏰ Đã hẹn giờ báo thức lúc $formatted!", "⏰ Alarm set for $formatted!") + exactWarning)
        triggerFloatingHearts()
      } else {
        if (existing == null) repository.deleteCustomReminder(id) else repository.setCustomReminderAlarm(id, null, "")
        showToast(tr("Không thể đặt lịch báo thức. Vui lòng kiểm tra quyền hệ thống.", "Couldn't set the alarm. Check system permissions."))
      }
      uiState.closeSetAlarmDialog()
    }
  }

  fun testReminderAlarm(title: String, message: String) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val now = System.currentTimeMillis()
      val success = AlarmNotificationScheduler.scheduleAlarm(
        context = context,
        reminderId = now,
        title = title,
        message = message,
        triggerAtMillis = now + 1000L
      )
      showToast(
        if (success) tr("⏰ Báo thức thử sẽ kêu sau 1 giây!", "⏰ Test alarm rings in 1 second!")
        else tr("Không thể đặt lịch báo thức. Vui lòng kiểm tra quyền hệ thống.", "Couldn't set the alarm. Check system permissions.")
      )
      uiState.closeSetAlarmDialog()
    }
  }

  fun cancelReminderAlarm(reminderId: Long) {
    scheduler.cancel(listOf(ReminderKey.Custom(reminderId)))
  }

  fun triggerInstantTestAlarm(
    title: String = "Thử nghiệm báo thức tình yêu ❤️",
    message: String = "Chuông và thông báo kỷ niệm hoạt động rất tốt!"
  ) {
    AlarmNotificationScheduler.triggerInstantTest(getApplication(), title, message)
    showToast(tr("🔔 Đã phát thử chuông thông báo kỷ niệm!", "🔔 Test notification sent!"))
  }

  fun deleteCustomReminder(id: Long) {
    viewModelScope.launch {
      repository.deleteCustomReminder(id)
      cancelReminderAlarm(id)
      showToast(tr("Đã xóa lời nhắc hẹn thành công.", "Reminder deleted."))
    }
  }

  fun deleteMilestone(id: Long) {
    viewModelScope.launch {
      repository.deleteMilestone(id)
      scheduler.cancel(listOf(ReminderKey.UserMilestone(id)))
      showToast(tr("Đã xóa ngày kỷ niệm khỏi lịch.", "Milestone deleted."))
    }
  }

  fun deleteChecklistItem(id: Long) {
    viewModelScope.launch {
      repository.deleteChecklistItem(id)
      showToast(tr("Đã xóa công việc chuẩn bị quà.", "Checklist item deleted."))
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
      showToast(tr("Vui lòng nhập tiêu đề kỷ niệm!", "Please enter a title"))
      return
    }
    val defaultFallback = presetPhotos.value.firstOrNull() ?: ""
    viewModelScope.launch {
      repository.addMilestone(
        MilestoneEntity(
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
      )
      scheduler.requestResync()
      uiState.closeAddMilestoneDialog()
      triggerFloatingHearts()
      showToast(tr("Đã thêm kỷ niệm mới & hẹn giờ thông báo! 🔔", "Milestone added & notification set! 🔔"))
    }
  }

  fun toggleMilestoneNotification(item: MilestoneEntity) {
    viewModelScope.launch {
      val willBeEnabled = !item.notificationEnabled
      repository.toggleMilestoneNotification(item)
      if (willBeEnabled) {
        scheduler.requestResync()
        showToast(tr("🔔 Đã bật thông báo cho cột mốc '${item.title}'", "🔔 Notifications on for '${item.title}'"))
      } else {
        scheduler.cancel(listOf(ReminderKey.UserMilestone(item.id)))
        showToast(tr("🔕 Đã tắt thông báo cho '${item.title}'", "🔕 Notifications off for '${item.title}'"))
      }
    }
  }

  fun refreshLoveDays() = coupleController.refreshLoveDays()

  fun saveCoupleProfile(
    girl: String,
    girlBirth: String,
    girlAvatar: String,
    girlA: Int,
    girlZod: String,
    title: String,
    days: Int,
    anniversary: String = ""
  ) = coupleController.saveCoupleProfile(girl, girlBirth, girlAvatar, girlA, girlZod, title, days, anniversary, loveEnded)

  fun completeOnboardingProfile(me: String, partner: String, title: String, anniversaryDate: String = "") =
    coupleController.completeOnboardingProfile(me, partner, title, anniversaryDate)

  fun updateLoveTitle(newTitle: String) = coupleController.updateLoveTitle(newTitle)

  fun calculateLoveDaysFromDate(dateStr: String): Int = coupleController.calculateLoveDaysFromDate(dateStr)

  fun setAnniversaryAndRecalculateDays(newDate: String) =
    coupleController.setAnniversaryAndRecalculateDays(newDate, loveEnded)

  fun syncCalculatedDaysToHome(days: Int) = coupleController.syncCalculatedDaysToHome(days)

  fun upgradeWithBilling(activity: Activity, productDetails: ProductDetails, offerToken: String = "") {
    AppServiceLocator.billingManager.launchPurchaseFlow(activity, productDetails, offerToken)
  }

  fun restorePurchases(onComplete: ((Boolean) -> Unit)? = null) {
    AppServiceLocator.billingManager.queryExistingPurchases { success ->
      showToast(
        if (success) tr("Đã khôi phục thành công gói VIP", "VIP restored")
        else tr("Không tìm thấy giao dịch VIP nào cho tài khoản này.", "No VIP purchase found for this account.")
      )
      onComplete?.invoke(success)
    }
  }

  fun showInterstitialAd(activity: Activity, onDismissed: () -> Unit = {}) {
    AppServiceLocator.adsManager.showInterstitial(activity, onDismissed)
  }

  fun triggerAiGiftSuggestions(occasion: String = "Kỷ niệm ngày yêu") {
    viewModelScope.launch {
      val partner = partnerOnlineUser.value
      val partnerName = partner?.effectiveDisplayName.orEmpty().ifBlank { girlName.value }.ifBlank { "người ấy" }
      val interests = mutualInterests.value + parseLikes(coupleProfile.value?.likesCsv) +
        partnerDossier.value.interestCategories
      showToast(tr("Đang thêm gợi ý quà theo sở thích chung...", "Adding gift ideas..."))
      val result = repository.generateAiGiftSuggestions(
        partnerName = partnerName,
        mutualInterests = interests,
        occasion = occasion,
        isEnglish = _appLanguage.value == AppLanguage.EN
      )
      if (result.isSuccess) {
        val n = result.getOrNull()?.size ?: 0
        showToast(tr("Đã thêm $n gợi ý quà tặng theo sở thích", "Added $n gift idea${if (n == 1) "" else "s"}"))
      } else {
        showToast(tr("Chưa thêm được gợi ý mới. Bạn vẫn có thể xem toàn bộ danh mục quà tặng.", "Couldn't add new ideas. You can still browse the full catalog."))
      }
    }
  }

  fun setLanguage(lang: AppLanguage) {
    _appLanguage.value = lang
    LocaleManager.saveLanguage(getApplication(), lang)
    scheduler.requestResync()
    val resId = if (lang == AppLanguage.VI) com.example.R.string.toast_switched_vi else com.example.R.string.toast_switched_en
    val config = android.content.res.Configuration(getApplication<Application>().resources.configuration).apply {
      setLocale(Locale.forLanguageTag(if (lang == AppLanguage.VI) "vi" else "en"))
    }
    showToast(getApplication<Application>().createConfigurationContext(config).getString(resId))
  }

  fun setWallpaper(url: String) {
    if (url.isNotBlank()) {
      val trimmed = url.trim()
      _selectedWallpaperUrl.value = trimmed
      LocaleManager.saveWallpaperUrl(getApplication(), trimmed)
      triggerFloatingHearts()
      showToast(tr("Đã đổi hình nền lãng mạn mới! 🌸", "Romantic wallpaper updated! 🌸"))
    }
  }

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
        videoUri = media?.takeIf { isVideo }?.let { Uri.fromFile(File(it.path)).toString() },
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
      is SaveSharedMemoryOutcome.InvalidMedia -> { showToast(outcome.message); false }
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

  suspend fun resolveMemoryMediaUrl(memory: SharedMemoryEntity): String? =
    resolveMemoryMediaUrlUseCase(currentOnlineUser.value.relationshipId, memory.relationshipId.orEmpty(), memory.syncId).getOrNull()

  fun updateSharedMemory(memory: SharedMemoryEntity) {
    viewModelScope.launch {
      repository.updateSharedMemory(memory)
      uiState.syncMemoryDetail(memory)
      showToast(tr("Đã cập nhật kỷ niệm", "Memory updated"))
    }
  }

  fun isCurrentUserAuthor(memory: SharedMemoryEntity): Boolean {
    val myUid = currentOnlineUser.value.uid
    return memory.authorId.isBlank() || memory.authorId == myUid
  }

  fun deleteSharedMemory(id: Long) {
    viewModelScope.launch {
      repository.deleteSharedMemory(id)
      uiState.clearMemoryDetailIfId(id)
      showToast(tr("Đã xóa ảnh kỷ niệm", "Memory deleted"))
    }
  }

  fun toggleMemoryFavorite(memory: SharedMemoryEntity) {
    viewModelScope.launch {
      repository.toggleMemoryFavorite(memory)
      if (uiState.selectedMemoryDetail.value?.id == memory.id) {
        uiState.openMemoryDetail(memory.copy(isFavorite = !memory.isFavorite))
      }
      triggerFloatingHearts()
    }
  }

  fun claimBadge(badgeId: String, customNote: String = "") {
    viewModelScope.launch {
      repository.claimLoveBadge(badgeId, customNote)
      triggerFloatingHearts(isMilestone = true)
      showToast(tr("🏆 Đã vinh danh và lưu kỷ niệm vào Huy Hiệu Trái Tim!", "🏆 Badge honored and memory saved to Heart Trophy!"))
    }
  }

  fun celebrateBadge(badge: LoveBadgeEntity) {
    triggerFloatingHearts(isMilestone = true)
    showToast(tr("🎉 Chúc mừng cột mốc ${badge.targetDays} ngày bên nhau! 💕✨", "🎉 Celebrating ${badge.targetDays} days together! 💕✨"))
  }

  fun addAnniversaryDate(
    title: String,
    dateText: String,
    type: String = "LOVE",
    description: String = "",
    isAnnual: Boolean = true,
    notificationEnabled: Boolean = true,
    reminderDaysBefore: Int = 3
  ) {
    if (title.isBlank()) {
      showToast(tr("Vui lòng nhập tên ngày kỷ niệm!", "Please enter a name"))
      return
    }
    viewModelScope.launch {
      repository.addAnniversaryDate(
        title = title.trim(),
        dateText = dateText.trim(),
        type = type,
        description = description.trim(),
        isAnnual = isAnnual,
        notificationEnabled = notificationEnabled,
        reminderDaysBefore = reminderDaysBefore,
        relationshipId = currentOnlineUser.value.relationshipId
      )
      scheduler.requestResync()
      uiState.closeAddAnniversaryDialog()
      triggerFloatingHearts()
      showToast(
        if (notificationEnabled) tr("Đã lưu ngày kỷ niệm & kích hoạt thông báo tự động! 🔔", "Saved & notification set! 🔔")
        else tr("Đã lưu ngày kỷ niệm! 💕", "Saved! 💕")
      )
    }
  }

  fun updateAnniversaryDate(item: AnniversaryDateEntity) {
    viewModelScope.launch {
      repository.updateAnniversaryDate(item)
      if (item.notificationEnabled) scheduler.requestResync() else scheduler.cancel(anniversaryKeys(item.id))
      showToast(tr("Đã cập nhật ngày kỷ niệm!", "Updated!"))
    }
  }

  fun deleteAnniversaryDate(id: Long) {
    viewModelScope.launch {
      repository.deleteAnniversaryDate(id)
      scheduler.cancel(anniversaryKeys(id))
      showToast(tr("Đã xóa ngày kỷ niệm khỏi thiết bị.", "Date deleted."))
    }
  }

  fun toggleAnniversaryNotification(item: AnniversaryDateEntity) {
    viewModelScope.launch {
      val willBeEnabled = !item.notificationEnabled
      repository.updateAnniversaryDate(item.copy(notificationEnabled = willBeEnabled))
      if (willBeEnabled) {
        scheduler.requestResync()
        showToast(tr("🔔 Đã bật thông báo kỷ niệm '${item.title}'", "🔔 Notifications on for '${item.title}'"))
      } else {
        scheduler.cancel(anniversaryKeys(item.id))
        showToast(tr("🔕 Đã tắt thông báo kỷ niệm '${item.title}'", "🔕 Notifications off for '${item.title}'"))
      }
    }
  }

  fun triggerTestAnniversaryNotification() {
    val context = getApplication<Application>()
    viewModelScope.launch {
      val anniversaries = repository.getAnniversaryDatesList()
      val firstAnn = anniversaries.firstOrNull { it.notificationEnabled } ?: anniversaries.firstOrNull()
      if (firstAnn != null) {
        ReminderAlarmReceiver.showNotification(
          context = context,
          title = "🎉 Thử nghiệm Kỷ Niệm: ${firstAnn.title} ❤️",
          message = "Còn ít ngày nữa là đến '${firstAnn.title}' (${firstAnn.dateText}). Đừng quên chuẩn bị món quà bất ngờ và một buổi tối lãng mạn cho người ấy nhé! 🎁✨",
          notificationId = 8888,
          channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
          targetTab = "calendar"
        )
      } else {
        AlarmNotificationScheduler.triggerInstantTest(context)
      }
      showToast(tr("🔔 Đã gửi thông báo kỷ niệm thử nghiệm lên thanh trạng thái!", "🔔 Test notification sent!"))
    }
  }

  fun resyncAllAnniversaryAlarms() = scheduler.requestResync()

  fun syncUpcomingMilestonesFromFirestore() {
    if (loveEnded) {
      showToast(tr("Hiện chưa có mối quan hệ nào đang hoạt động.", "No active relationship right now."))
      return
    }
    val rel = activeRelationship.value
    val startMillis = rel?.startDate ?: 0L
    val startDateText = if (rel?.startDateText.isNullOrBlank()) anniversaryDate.value else rel.startDateText
    val partnerName = partnerOnlineUser.value?.effectiveDisplayName.orEmpty()
    val count = LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(getApplication(), startMillis, startDateText, partnerName)
    _upcomingMilestones.value = LoveAnniversaryMilestoneScheduler.getUpcomingMilestones(startMillis, startDateText)
    showToast(tr("Đã đồng bộ và đặt $count thông báo cột mốc", "Synced and scheduled $count milestone notifications"))
  }

  fun syncCloudData() {
    viewModelScope.launch {
      val success = repository.syncAllCloudPresets()
      showToast(
        if (success) tr("Đã đồng bộ dữ liệu mới nhất", "Synced the latest updates")
        else tr("Chưa thể kết nối đồng bộ, đang dùng dữ liệu đã lưu trên máy", "Sync unavailable, using saved data")
      )
    }
  }

  fun triggerTestMilestoneNotification() {
    val partnerName = partnerOnlineUser.value?.effectiveDisplayName.orEmpty().ifBlank { "người ấy" }
    val sample = _upcomingMilestones.value.firstOrNull()?.title ?: "💎 Bách Nhật Yêu (100 Ngày)"
    LoveAnniversaryMilestoneScheduler.triggerInstantTestMilestone(getApplication(), partnerName, sample)
    showToast(tr("🔔 Đã kích hoạt thông báo cột mốc thử nghiệm lên thanh trạng thái!", "🔔 Test notification sent!"))
  }

  fun addGiftReminder(
    title: String,
    recipient: String = "Người ấy",
    occasion: String = "Kỷ niệm ngày yêu",
    dueDateText: String = "",
    estimatedBudget: String = "",
    notes: String = ""
  ) {
    if (title.isBlank()) {
      showToast(tr("Vui lòng nhập món quà cần nhắc nhở!", "Please enter a gift"))
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
      uiState.closeAddGiftReminderDialog()
      triggerFloatingHearts()
      showToast(tr("Đã lưu lời nhắc quà tặng", "Gift reminder saved"))
    }
  }

  fun updateGiftReminder(item: GiftReminderEntity) {
    viewModelScope.launch {
      repository.updateGiftReminder(item)
      showToast(tr("Đã cập nhật lời nhắc quà tặng!", "Gift reminder updated!"))
    }
  }

  fun toggleGiftReminderCompleted(item: GiftReminderEntity) {
    viewModelScope.launch {
      repository.toggleGiftReminderCompleted(item)
      showToast(if (!item.isCompleted) tr("Đã hoàn thành chuẩn bị món quà", "Gift ready") else tr("Đã chuyển về danh sách chuẩn bị quà.", "Moved back to your to-do list"))
    }
  }

  fun deleteGiftReminder(id: Long) {
    viewModelScope.launch {
      repository.deleteGiftReminder(id)
      showToast(tr("Đã xóa lời nhắc quà tặng.", "Gift reminder deleted."))
    }
  }

  fun updateSearchQuery(query: String) = pairingController.updateSearchQuery(query)
  fun performSearch() = pairingController.performSearch()
  suspend fun searchPartnerForSetLove(query: String): OnlineUserEntity? = pairingController.searchPartnerForSetLove(query)
  fun clearSearch() = pairingController.clearSearch()

  fun updateMyProfile(
    name: String,
    birthDate: String,
    avatarUrl: String,
    gender: String,
    bio: String,
    clearBirthday: Boolean = false
  ) = coupleController.updateMyProfile(name, birthDate, avatarUrl, gender, bio, clearBirthday)

  fun sendSetLoveInvite(
    targetCodeOrLink: String,
    proposedStartDateMillis: Long = System.currentTimeMillis(),
    loveNote: String = ""
  ) = pairingController.sendSetLoveInvite(targetCodeOrLink, proposedStartDateMillis, loveNote)

  fun acceptSetLoveInvite(inviteId: String = "", confirmedStartDateMillis: Long? = null) =
    pairingController.acceptSetLoveInvite(inviteId, confirmedStartDateMillis)

  fun rejectSetLoveInvite(inviteId: String = "") = pairingController.rejectSetLoveInvite(inviteId)
  fun cancelSentInvite() = pairingController.cancelSentInvite()
  fun requestBreakup() = pairingController.requestBreakup()
  fun confirmBreakup() = pairingController.confirmBreakup()
  fun rejectBreakup() = pairingController.rejectBreakup()
  fun forceBreakup() = pairingController.forceBreakup()
  fun toggleInterest(interestKey: String) = pairingController.toggleInterest(interestKey)
  fun logout() = pairingController.logout()
  fun deleteAccountAndData(onComplete: (() -> Unit)? = null) = pairingController.deleteAccountAndData(onComplete)
  fun lockApp() = pairingController.lockApp()
  fun setAppPin(pin: String) = pairingController.setAppPin(pin)
  fun togglePinEnabled(enabled: Boolean) = pairingController.togglePinEnabled(enabled)

  override fun onCleared() {
    try { AppServiceLocator.syncCoordinator.stop() } catch (_: Exception) {}
    try { AppServiceLocator.profileSync?.stop() } catch (_: Exception) {}
    try { AppServiceLocator.cloudPartnerDossierSync.stop() } catch (_: Exception) {}
    super.onCleared()
  }
}
