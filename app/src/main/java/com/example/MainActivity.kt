@file:Suppress("FunctionName")
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.AuthState
import com.example.ui.components.DigitalTrophyRewardDialog
import com.example.ui.components.FloatingHeartsOverlay
import com.example.ui.components.InLoveBottomNav
import com.example.ui.components.InLoveTopBar
import com.example.ui.components.MilestoneBadgeShowcaseDialog
import com.example.ui.screens.AddAnniversaryDateDialog
import com.example.ui.screens.AddChecklistDialog
import com.example.ui.screens.AddGiftReminderDialog
import com.example.ui.screens.AddMilestoneDialog
import com.example.ui.screens.AddReminderDialog
import com.example.ui.screens.AppPinLockScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.CaptureMemoryDialog
import com.example.ui.screens.EditCoupleDialog
import com.example.ui.screens.EditMyProfileDialog
import com.example.ui.screens.GiftDetailDialog
import com.example.ui.screens.GiftScreen
import com.example.ui.screens.LanguageSelectionDialog
import com.example.ui.screens.LoveHomeScreen
import com.example.ui.screens.MemoriesGridScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.ReminderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UserGuideDialog
import com.example.ui.screens.VipProposalDialog
import com.example.ui.screens.WallpaperPickerDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.util.AppLanguage
import com.example.ui.components.ComposeBannerAd
import com.example.ui.screens.PaywallScreen
import com.example.ui.util.LocalizedStrings
import com.example.ui.viewmodel.InLoveViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Khởi tạo AppServiceLocator & Ads / Billing Services
    com.example.di.AppServiceLocator.initialize(applicationContext)
    val adsManager = com.example.di.AppServiceLocator.adsManager
    adsManager.requestConsentAndInitialize(this)
    adsManager.registerAppOpenAdLifecycle(application)
    adsManager.preloadInterstitial(this, BuildConfig.ADMOB_INTERSTITIAL_ID)
    adsManager.preloadAppOpenAd(this, BuildConfig.ADMOB_AOA_ID)
    com.example.di.AppServiceLocator.billingManager.startBillingConnection()

    setContent {
      androidx.compose.runtime.CompositionLocalProvider(
        androidx.activity.compose.LocalActivityResultRegistryOwner provides this
      ) {
        MyApplicationTheme {
          InLoveApp()
        }
      }
    }
  }
}

@Composable
fun InLoveApp(viewModel: InLoveViewModel = viewModel()) {
  val selectedTab by viewModel.selectedTab.collectAsState()
  val toastMessage by viewModel.toastMessage.collectAsState()
  val floatingHeartsTrigger by viewModel.floatingHeartsTrigger.collectAsState()
  val isMilestoneCelebration by viewModel.isMilestoneCelebration.collectAsState()

  val showAddReminderDialog by viewModel.showAddReminderDialog.collectAsState()
  val showAddMilestoneDialog by viewModel.showAddMilestoneDialog.collectAsState()
  val showAddChecklistDialog by viewModel.showAddChecklistDialog.collectAsState()
  val showAddAnniversaryDialog by viewModel.showAddAnniversaryDialog.collectAsState()
  val showAddGiftReminderDialog by viewModel.showAddGiftReminderDialog.collectAsState()
  val showEditCoupleDialog by viewModel.showEditCoupleDialog.collectAsState()
  val showAuthScreen by viewModel.showAuthScreen.collectAsState()
  val selectedGiftDetail by viewModel.selectedGiftDetail.collectAsState()
  val showVipProposalDetail by viewModel.showVipProposalDetail.collectAsState()

  val showLanguageDialog by viewModel.showLanguageDialog.collectAsState()
  val showWallpaperDialog by viewModel.showWallpaperDialog.collectAsState()
  val showMemoryDialog by viewModel.showMemoryDialog.collectAsState()
  val showGuideDialog by viewModel.showGuideDialog.collectAsState()
  val showBadgeShowcaseDialog by viewModel.showBadgeShowcaseDialog.collectAsState()
  val selectedBadge by viewModel.selectedBadge.collectAsState()
  val loveDays by viewModel.loveDays.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val selectedWallpaperUrl by viewModel.selectedWallpaperUrl.collectAsState()
  val presetWallpapers by viewModel.presetWallpapers.collectAsState()
  val presetPhotos by viewModel.presetPhotos.collectAsState()

  val locale = remember(appLanguage) {
    when (appLanguage) {
      AppLanguage.VI -> java.util.Locale.forLanguageTag("vi")
      AppLanguage.EN -> java.util.Locale.forLanguageTag("en")
    }
  }
  val currentConfig = androidx.compose.ui.platform.LocalConfiguration.current
  val configuration = remember(locale, currentConfig) {
    android.content.res.Configuration(currentConfig).apply {
      setLocale(locale)
    }
  }
  val rawContext = androidx.compose.ui.platform.LocalContext.current
  val currentRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    ?: (rawContext as? androidx.activity.result.ActivityResultRegistryOwner)

  val context = remember(rawContext, configuration) {
    rawContext.createConfigurationContext(configuration)
  }

  val compositionLocals = remember(configuration, context, currentRegistryOwner) {
    val locals = mutableListOf<androidx.compose.runtime.ProvidedValue<*>>(
      androidx.compose.ui.platform.LocalConfiguration provides configuration,
      androidx.compose.ui.platform.LocalContext provides context
    )
    if (currentRegistryOwner != null) {
      locals.add(androidx.activity.compose.LocalActivityResultRegistryOwner provides currentRegistryOwner)
    }
    locals.toTypedArray()
  }

  androidx.compose.runtime.CompositionLocalProvider(*compositionLocals) {
    val strings = LocalizedStrings.fromContext(context, appLanguage)

    val boyName by viewModel.boyName.collectAsState()
    val boyBirthDate by viewModel.boyBirthDate.collectAsState()
    val boyAvatarUrl by viewModel.boyAvatarUrl.collectAsState()
    val boyAge by viewModel.boyAge.collectAsState()
    val boyZodiac by viewModel.boyZodiac.collectAsState()

    val girlName by viewModel.girlName.collectAsState()
    val girlBirthDate by viewModel.girlBirthDate.collectAsState()
    val girlAvatarUrl by viewModel.girlAvatarUrl.collectAsState()
    val girlAge by viewModel.girlAge.collectAsState()
    val girlZodiac by viewModel.girlZodiac.collectAsState()

    val loveTitle by viewModel.loveTitle.collectAsState()
    val loveDays by viewModel.loveDays.collectAsState()
    val anniversaryDate by viewModel.anniversaryDate.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val authState by viewModel.authState.collectAsState()
    val isVip by viewModel.isVip.collectAsState()
    val showVipDialog by viewModel.showVipDialog.collectAsState()
    val currentTier by viewModel.subscriptionTier.collectAsState()

    LaunchedEffect(toastMessage) {
      toastMessage?.let { msg ->
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        snackbarHostState.showSnackbar(msg)
        viewModel.clearToast()
      }
    }

    // Request notification permission for Android 13+ (API 33+)
    val registryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    val permissionLauncher = if (registryOwner != null) {
      androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
      ) { isGranted ->
        if (isGranted) {
          viewModel.showToast(if (appLanguage == AppLanguage.VI) "🔔 Đã kích hoạt quyền thông báo kỷ niệm!" else "🔔 Anniversary notifications enabled!")
          viewModel.resyncAllAnniversaryAlarms()
        }
      }
    } else null

    LaunchedEffect(permissionLauncher) {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && permissionLauncher != null) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
          ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
          permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
      }
    }

    // Handle deep navigation when opened from an anniversary or reminder notification
    val activity = rawContext as? androidx.activity.ComponentActivity
    LaunchedEffect(activity?.intent) {
      val targetTab = activity?.intent?.getStringExtra("target_tab")
      if (targetTab == "calendar") {
        viewModel.setTab(2)
      } else if (targetTab == "home") {
        viewModel.setTab(0)
      }
    }

    // Trigger floating hearts on first open
    LaunchedEffect(Unit) {
      viewModel.triggerFloatingHearts()
    }

    val screenTitle = when (selectedTab) {
      0 -> strings.navHome
      1 -> strings.navMemories
      2 -> strings.navCalendar
      3 -> strings.navGifts
      else -> strings.navSettings
    }

    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState()

    if (isFirstLaunch) {
      OnboardingScreen(
        viewModel = viewModel,
        onFinishOnboarding = {
          viewModel.completeFirstLaunch()
        }
      )
    } else if (showAuthScreen) {
      AuthScreen(
        viewModel = viewModel,
        onBackToGuest = { viewModel.closeAuthScreen() }
      )
    } else if (authState is AuthState.PinLocked) {
      AppPinLockScreen(account = (authState as AuthState.PinLocked).account, viewModel = viewModel)
    } else {
      Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                listOf(
                  Surface,
                  SurfaceContainerLowest,
                  Color(0xFFFFF7F9)
                )
              )
            )
        ) {
    Scaffold(
      containerColor = Color.Transparent,
      topBar = {
        if (selectedTab == 0) {
          // Trang Chủ (Yêu Thích): Phần chữ yêu thích & trái tim được để trống trực tiếp để chèn quảng cáo (Banner Ad)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .padding(top = 4.dp, bottom = 2.dp)
          ) {
            ComposeBannerAd(
              isVip = isVip
            )
          }
        } else {
          Column {
            InLoveTopBar(
              title = screenTitle,
              onHeartClick = {
                viewModel.triggerFloatingHearts()
                viewModel.showToast(strings.countingDaysNotice.format(loveDays))
              },
              onProfileClick = {
                viewModel.setTab(4)
              }
            )

            // Khoảng trắng phía trên ứng dụng dành cho Banner Quảng Cáo AdMob
            ComposeBannerAd(
              isVip = isVip
            )
          }
        }
      },
      bottomBar = {
        InLoveBottomNav(
          selectedTab = selectedTab,
          onTabSelected = { tabIndex ->
            viewModel.setTab(tabIndex)
          },
          labels = listOf(
            strings.navHome,
            strings.navMemories,
            strings.navCalendar,
            strings.navGifts,
            strings.navSettings
          )
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        AnimatedContent(
          targetState = selectedTab,
          transitionSpec = {
            if (targetState > initialState) {
              (androidx.compose.animation.slideInHorizontally(
                animationSpec = androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> (fullWidth * 0.22f).toInt() }
              ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(320)) + androidx.compose.animation.scaleIn(initialScale = 0.97f, animationSpec = androidx.compose.animation.core.tween(320)))
                .togetherWith(
                  androidx.compose.animation.slideOutHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> (-fullWidth * 0.22f).toInt() }
                  ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(280)) + androidx.compose.animation.scaleOut(targetScale = 0.97f, animationSpec = androidx.compose.animation.core.tween(280))
                )
            } else {
              (androidx.compose.animation.slideInHorizontally(
                animationSpec = androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                initialOffsetX = { fullWidth -> (-fullWidth * 0.22f).toInt() }
              ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(320)) + androidx.compose.animation.scaleIn(initialScale = 0.97f, animationSpec = androidx.compose.animation.core.tween(320)))
                .togetherWith(
                  androidx.compose.animation.slideOutHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> (fullWidth * 0.22f).toInt() }
                  ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(280)) + androidx.compose.animation.scaleOut(targetScale = 0.97f, animationSpec = androidx.compose.animation.core.tween(280))
                )
            }
          },
          label = "screen_transition"
        ) { targetIndex ->
          when (targetIndex) {
            0 -> LoveHomeScreen(
              viewModel = viewModel,
              onNavigateToCalendar = { viewModel.setTab(2) },
              onNavigateToGifts = { viewModel.setTab(3) },
              onNavigateToMemories = { viewModel.setTab(1) }
            )
            1 -> MemoriesGridScreen(viewModel = viewModel)
            2 -> CalendarScreen(
              viewModel = viewModel,
              onNavigateToGifts = { viewModel.setTab(3) }
            )
            3 -> GiftScreen(viewModel = viewModel)
            4 -> SettingsScreen(viewModel = viewModel)
            else -> LoveHomeScreen(
              viewModel = viewModel,
              onNavigateToCalendar = { viewModel.setTab(2) },
              onNavigateToGifts = { viewModel.setTab(3) },
              onNavigateToMemories = { viewModel.setTab(1) }
            )
          }
        }
      }
    }

    // Romantic Floating Hearts Animation Overlay (Particle System with Milestone Celebration mode)
    FloatingHeartsOverlay(
      trigger = floatingHeartsTrigger,
      isMilestoneCelebration = isMilestoneCelebration
    )

    // Dialogs
    if (showEditCoupleDialog) {
      EditCoupleDialog(
        viewModel = viewModel,
        onDismiss = { viewModel.closeEditCoupleDialog() }
      )
    }

    if (showAddReminderDialog) {
      AddReminderDialog(
        onDismiss = { viewModel.closeAddReminderDialog() },
        onConfirm = { title, dateText, note ->
          viewModel.addCustomReminder(title, dateText, note)
        }
      )
    }

    if (showAddMilestoneDialog) {
      AddMilestoneDialog(
        onDismiss = { viewModel.closeAddMilestoneDialog() },
        onConfirm = { title, dateText, subtitle, catTag, secTag, img, days, important ->
          viewModel.addMilestone(title, dateText, subtitle, catTag, secTag, img, days, important)
        }
      )
    }

    if (showAddChecklistDialog) {
      AddChecklistDialog(
        onDismiss = { viewModel.closeAddChecklistDialog() },
        onConfirm = { text ->
          viewModel.addChecklist(text)
          viewModel.closeAddChecklistDialog()
        }
      )
    }

    if (showAddAnniversaryDialog) {
      AddAnniversaryDateDialog(
        onDismiss = { viewModel.closeAddAnniversaryDialog() },
        onConfirm = { title, dateText, type, description, isAnnual, reminderDaysBefore ->
          viewModel.addAnniversaryDate(title, dateText, type, description, isAnnual, reminderDaysBefore)
        }
      )
    }

    if (showAddGiftReminderDialog) {
      AddGiftReminderDialog(
        onDismiss = { viewModel.closeAddGiftReminderDialog() },
        onConfirm = { title, recipient, occasion, dueDateText, estimatedBudget, notes ->
          viewModel.addGiftReminder(title, recipient, occasion, dueDateText, estimatedBudget, notes)
        }
      )
    }

    selectedGiftDetail?.let { gift ->
      GiftDetailDialog(
        gift = gift,
        onDismiss = { viewModel.closeGiftDetail() },
        onToggleFavorite = { viewModel.toggleGiftFavorite(gift) }
      )
    }

    if (showVipProposalDetail) {
      VipProposalDialog(
        onDismiss = { viewModel.closeVipProposal() }
      )
    }

    if (showLanguageDialog) {
      LanguageSelectionDialog(
        currentLanguage = appLanguage,
        onLanguageSelected = { lang ->
          viewModel.setLanguage(lang)
        },
        onDismiss = { viewModel.closeLanguageDialog() }
      )
    }

    if (showWallpaperDialog) {
      WallpaperPickerDialog(
        language = appLanguage,
        currentWallpaperUrl = selectedWallpaperUrl,
        presetWallpapers = presetWallpapers,
        onApplyWallpaper = { url ->
          viewModel.setWallpaper(url)
        },
        onDismiss = { viewModel.closeWallpaperDialog() }
      )
    }

    if (showMemoryDialog) {
      CaptureMemoryDialog(
        language = appLanguage,
        presetPhotos = presetPhotos,
        onSaveMemory = { note, photoUrl ->
          viewModel.saveMemory(note, photoUrl)
        },
        onDismiss = { viewModel.closeMemoryDialog() }
      )
    }

    if (showGuideDialog) {
      UserGuideDialog(
        language = appLanguage,
        onDismiss = { viewModel.closeGuideDialog() }
      )
    }

    if (showBadgeShowcaseDialog) {
      MilestoneBadgeShowcaseDialog(
        viewModel = viewModel,
        onDismiss = { viewModel.closeBadgeShowcase() }
      )
    }

    selectedBadge?.let { badge ->
      DigitalTrophyRewardDialog(
        badge = badge,
        currentLoveDays = loveDays,
        viewModel = viewModel,
        onDismiss = { viewModel.selectBadge(null) }
      )
    }

    val showPairingScreen by viewModel.showPairingScreen.collectAsState()
    if (showPairingScreen) {
      androidx.compose.ui.window.Dialog(
        onDismissRequest = { viewModel.closePairingScreen() },
        properties = androidx.compose.ui.window.DialogProperties(
          usePlatformDefaultWidth = false,
          dismissOnBackPress = true
        )
      ) {
        PairingScreen(
          viewModel = viewModel,
          onNavigateBack = { viewModel.closePairingScreen() }
        )
      }
    }

    val showEditProfileDialog by viewModel.showEditProfileDialog.collectAsState()
    val currentOnlineUser by viewModel.currentOnlineUser.collectAsState()
    val dynamicPresetAvatars by viewModel.presetAvatars.collectAsState()
    if (showEditProfileDialog) {
      EditMyProfileDialog(
        currentUser = currentOnlineUser,
        presetAvatars = dynamicPresetAvatars,
        onDismiss = { viewModel.closeEditProfileDialog() },
        onSave = { name, birth, avatar, gender, bio ->
          viewModel.updateMyProfile(name, birth, avatar, gender, bio)
        }
      )
    }

    if (showVipDialog) {
      androidx.compose.ui.window.Dialog(
        onDismissRequest = { viewModel.setVipDialogVisible(false) },
        properties = androidx.compose.ui.window.DialogProperties(
          usePlatformDefaultWidth = false,
          dismissOnBackPress = true
        )
      ) {
        PaywallScreen(
          billingManager = com.example.di.AppServiceLocator.billingManager,
          adsManager = com.example.di.AppServiceLocator.adsManager,
          onDismiss = { viewModel.setVipDialogVisible(false) }
        )
      }
    }
  }
}
}
}
