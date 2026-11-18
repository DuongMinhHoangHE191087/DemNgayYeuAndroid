package com.example.ui.screens

import android.app.Application
import android.content.res.Configuration
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.InLoveApp
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.MilestoneEntity
import com.example.data.repository.OnlineCoupleRepository
import com.example.di.AppServiceLocator
import com.example.shared.guide.GuideChapterId
import com.example.shared.guide.GuideQuickAction
import com.example.shared.monetization.ProTierCatalog
import com.example.ui.testing.NoNetworkImagesRule
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File
import java.util.Locale
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class MainTabsScrollTest {

  @get:Rule val composeTestRule = createComposeRule()
  @get:Rule val noNetwork = NoNetworkImagesRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()
  private var firstMilestoneId: Long = 1L

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder().setApplicationId("1:1:android:1").setApiKey("fake").setProjectId("fake").build()
      )
    }
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    injectDatabase(db)
    AppServiceLocator.initialize(context)
    seedData()
  }

  @After
  fun tearDown() {
    viewModelStore.clear()
    injectDatabase(null)
    db.close()
  }

  private fun injectDatabase(value: AppDatabase?) {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
    field.isAccessible = true
    field.set(null, value)
  }

  private fun seedData() = runBlocking {
    val dao = db.inLoveDao()
    dao.insertCoupleProfile(
      CoupleProfileEntity(
        id = 1,
        partner1Name = "Minh",
        partner1Birthday = "01/01/1999",
        partner1ProfilePicture = "",
        partner2Name = "Linh",
        partner2Birthday = "02/02/2000",
        partner2ProfilePicture = "",
        loveTitle = "Minh & Linh",
        loveDays = 367,
        anniversaryDate = "17/11/2025",
      )
    )
    (1..5).forEach { idx ->
      val insertedId = dao.insertMilestone(
        MilestoneEntity(
          title = if (idx == 1) "100 Ngày Bên Nhau" else "Cột mốc $idx",
          dateText = "17/11/2026",
          subtitle = "Kỷ niệm đặc biệt $idx",
          categoryTag = "LOVE",
          secondaryTag = "MILESTONE",
          imageUrl = "",
          daysRemaining = 20 + idx * 10,
        )
      )
      if (idx == 1) firstMilestoneId = insertedId
      dao.insertAnniversaryDate(
        AnniversaryDateEntity(
          title = if (idx == 1) "💖 Lễ Tình Nhân (14/2)" else "Ngày kỷ niệm $idx",
          dateText = "1${idx}/11/2025",
          daysRemaining = 15 + idx * 7,
        )
      )
    }
  }

  private fun newViewModel(): InLoveViewModel {
    val app = ApplicationProvider.getApplicationContext<Application>()
    return ViewModelProvider(
      viewModelStore,
      ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    )[InLoveViewModel::class.java]
  }

  private fun hasVerticalScrollAction(): SemanticsMatcher =
    SemanticsMatcher("Has vertical scroll action") { node ->
      SemanticsProperties.VerticalScrollAxisRange in node.config
    }

  private fun verticalScrollValue(node: SemanticsNodeInteraction): Float =
    node.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

  private fun eventually(check: () -> Unit) {
    try {
      composeTestRule.waitUntil(timeoutMillis = 5_000) { runCatching { check() }.isSuccess }
    } catch (_: Throwable) {
    }
    check()
  }

  private fun maybeCapture(name: String) {
    val dir = System.getenv("POSTFIX_ARTIFACT_DIR")?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val outDir = File(dir).apply { mkdirs() }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = File(outDir, name).absolutePath)
  }

  @Test
  fun inLoveApp_scaffold_scrollsVertically_acrossAllFourMainTabs() {
    val vm = newViewModel()
    vm.completeFirstLaunch()
    vm.setLanguage(AppLanguage.VI)

    composeTestRule.setContent {
      MyApplicationTheme {
        InLoveApp(viewModel = vm)
      }
    }

    if (composeTestRule.onAllNodes(hasTestTag("app_opening_splash")).fetchSemanticsNodes().isNotEmpty()) {
      composeTestRule.onNodeWithTag("app_opening_splash").performClick()
      composeTestRule.mainClock.advanceTimeBy(1_000)
    }
    eventually {
      assertTrue(composeTestRule.onAllNodes(hasTestTag("app_opening_splash")).fetchSemanticsNodes().isEmpty())
    }

    val tabAnchors = listOf(
      0 to "love_circle_days_counter",
      2 to "interactive_calendar_card",
      3 to "mutual_interests_gift_card",
      4 to "language_selection_toggle",
    )

    tabAnchors.forEach { (tabIndex, anchorTag) ->
      vm.setTab(tabIndex)
      composeTestRule.mainClock.advanceTimeBy(800)
      eventually {
        composeTestRule.onNodeWithTag(anchorTag).assertExists()
        assertTrue(composeTestRule.onAllNodes(hasVerticalScrollAction()).fetchSemanticsNodes().size == 1)
      }
      when (tabIndex) {
        0 -> maybeCapture("wave3_01_home_top.png")
      }
      val scrollNode = composeTestRule.onNode(hasVerticalScrollAction())
      val before = verticalScrollValue(scrollNode)
      composeTestRule.onRoot().performTouchInput {
        swipeUp(startY = centerY + 260f, endY = centerY - 260f)
      }
      composeTestRule.waitForIdle()
      val after = verticalScrollValue(scrollNode)
      assertTrue(
        "Tab $tabIndex failed to scroll vertically inside InLoveApp Scaffold (before=$before, after=$after)",
        after > before
      )
      when (tabIndex) {
        0 -> {
          composeTestRule.onNodeWithTag("btn_send_quote").performScrollTo()
          maybeCapture("wave3_02_home_readiness_quotes.png")
        }
        2 -> {
          scrollNode.performScrollToNode(hasTestTag("anniversary_dates_section"))
          maybeCapture("wave3_03_calendar.png")
        }
        3 -> {
          scrollNode.performScrollToNode(hasTestTag("ai_gift_concierge_section"))
          maybeCapture("wave3_04_gift_dossier_concierge.png")
        }
        4 -> {
          scrollNode.performScrollToNode(hasText("Tài khoản", ignoreCase = true))
          maybeCapture("wave3_05_settings_compact.png")
        }
      }
    }
  }

  @Test
  fun homeTab_scrollsToReadinessCardAndBottomQuoteAction() {
    val vm = newViewModel()
    vm.setLanguage(AppLanguage.EN)
    composeTestRule.setContent {
      MyApplicationTheme {
        LoveHomeScreen(viewModel = vm, onNavigateToCalendar = {}, onNavigateToGifts = {})
      }
    }

    eventually {
      composeTestRule.onNodeWithTag("home_next_occasion_readiness_card").performScrollTo().assertIsDisplayed()
    }
    composeTestRule.onNodeWithTag("btn_send_quote").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun calendarTab_scrollsToAnniversaryDatesSection() {
    val vm = newViewModel()
    vm.setLanguage(AppLanguage.EN)
    composeTestRule.setContent {
      MyApplicationTheme {
        CalendarScreen(viewModel = vm, onNavigateToGifts = {})
      }
    }

    eventually { composeTestRule.onNode(hasVerticalScrollAction()).assertExists() }
    composeTestRule.onNode(hasVerticalScrollAction())
      .performScrollToNode(hasTestTag("anniversary_dates_section"))
    composeTestRule.onNodeWithTag("anniversary_dates_section").assertIsDisplayed()
  }

  @Test
  fun giftTab_scrollsToPartnerDossierAndAiConcierge() {
    val vm = newViewModel()
    vm.setLanguage(AppLanguage.EN)
    composeTestRule.setContent {
      MyApplicationTheme {
        GiftScreen(viewModel = vm)
      }
    }

    eventually { composeTestRule.onNode(hasVerticalScrollAction()).assertExists() }
    val list = composeTestRule.onNode(hasVerticalScrollAction())
    list.performScrollToNode(hasTestTag("partner_dossier_card"))
    composeTestRule.onNodeWithTag("partner_dossier_card").assertIsDisplayed()
    list.performScrollToNode(hasTestTag("ai_gift_concierge_section"))
    composeTestRule.onNodeWithTag("ai_gift_concierge_section").assertIsDisplayed()
  }

  @Test
  fun settingsTab_scrollsToAccountSection() {
    val vm = newViewModel()
    vm.setLanguage(AppLanguage.EN)
    composeTestRule.setContent {
      MyApplicationTheme {
        SettingsScreen(viewModel = vm)
      }
    }

    eventually { composeTestRule.onNode(hasVerticalScrollAction()).assertExists() }
    val scrollNode = composeTestRule.onNode(hasVerticalScrollAction())
    scrollNode.performScrollToNode(hasTestTag("settings_privacy_notice"))
    composeTestRule.onNodeWithTag("btn_toggle_privacy_details").assertIsDisplayed()
    composeTestRule.onNodeWithText("Show details").assertIsDisplayed()
    composeTestRule.onNodeWithTag("btn_toggle_privacy_details").performClick()
    composeTestRule.onNodeWithText("Hide").assertIsDisplayed()
    scrollNode.performScrollToNode(hasText("Account"))
    composeTestRule.onNodeWithText("Account").assertIsDisplayed()
  }

  @Test
  fun pairingScreen_rendersSyncPhaseBannerAndEmojiFreeInterests() {
    val vm = newViewModel()
    vm.setLanguage(AppLanguage.VI)
    composeTestRule.setContent {
      MyApplicationTheme {
        PairingScreen(viewModel = vm)
      }
    }

    eventually {
      composeTestRule.onNodeWithTag("pairing_sync_phase_banner").assertIsDisplayed()
    }
    val firstInterestLabel = OnlineCoupleRepository.AVAILABLE_INTERESTS.first().second
    composeTestRule.onNode(hasVerticalScrollAction())
      .performScrollToNode(hasTestTag("pairing_interests_card"))
    composeTestRule.onNodeWithText(firstInterestLabel).assertIsDisplayed()
    maybeCapture("wave3_06_pairing_online.png")
  }

  @Test
  fun userGuideDialog_rendersFourChaptersAndQuickAction() {
    var selectedAction: GuideQuickAction? = null
    composeTestRule.setContent {
      MyApplicationTheme {
        UserGuideDialog(
          language = AppLanguage.VI,
          onDismiss = {},
          onQuickAction = { selectedAction = it }
        )
      }
    }

    eventually {
      composeTestRule.onNodeWithTag("inlove_user_guide_dialog").assertIsDisplayed()
    }
    GuideChapterId.entries.forEach { chapterId ->
      composeTestRule.onNodeWithTag("guide_chapter_${chapterId.name}").assertIsDisplayed()
    }
    composeTestRule.onNodeWithText("01").assertIsDisplayed()
    composeTestRule.onNodeWithTag("guide_quick_action").assertIsDisplayed()
    maybeCapture("wave3_07_user_guide.png")

    composeTestRule.onNodeWithTag("guide_chapter_${GuideChapterId.ONLINE_COUPLE_PAIRING.name}").performClick()
    composeTestRule.onNodeWithTag("guide_quick_action").performClick()
    assertEquals(GuideQuickAction.OPEN_PAIRING, selectedAction)
  }

  private fun awaitCondition(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (true) {
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      if (System.nanoTime() > deadline) fail("timed out waiting for $what")
      LockSupport.parkNanos(1_000_000L)
    }
  }

  @Test
  fun onboardingStep5_showsStartDateField_withoutSparkle() {
    val vm = newViewModel()
    vm.setLanguage(AppLanguage.VI)
    composeTestRule.setContent {
      MyApplicationTheme {
        OnboardingScreen(viewModel = vm, onFinishOnboarding = {})
      }
    }

    repeat(4) {
      composeTestRule.onNodeWithText("Tiếp tục").performClick()
      composeTestRule.mainClock.advanceTimeBy(400)
      composeTestRule.waitForIdle()
    }
    eventually {
      composeTestRule.onNodeWithTag("onboarding_start_date_field").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithText("Bắt Đầu Ngay").assertIsDisplayed()
    }
  }

  @Test
  fun paywallScreen_displaysCompactBenefitsAndYearlyPlan() {
    val yearlySpec = ProTierCatalog.planFor(ProTierCatalog.YEARLY)
    composeTestRule.setContent {
      val baseContext = LocalContext.current
      val viConfig = Configuration(LocalConfiguration.current).apply {
        setLocale(Locale.forLanguageTag("vi"))
      }
      val localizedResources = baseContext.createConfigurationContext(viConfig).resources
      val viContext = object : android.content.ContextWrapper(baseContext) {
        override fun getResources() = localizedResources
      }
      CompositionLocalProvider(
        LocalConfiguration provides viConfig,
        LocalContext provides viContext
      ) {
        MyApplicationTheme {
          PaywallScreen(
            billingManager = AppServiceLocator.billingManager,
            adsManager = AppServiceLocator.adsManager,
            onDismiss = {}
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("paywall_pro_benefits_matrix").assertIsDisplayed()
    composeTestRule.onNodeWithTag("paywall_cta").assertIsDisplayed()
    val expectedSavings = yearlySpec?.savingsPercent ?: 57
    composeTestRule.onNodeWithText("Tiết kiệm $expectedSavings%", substring = true).assertIsDisplayed()
    maybeCapture("wave3_08_paywall.png")
  }

  @Test
  fun onboardingProfile_withStartDate_setsAnniversaryAndDays() {
    val vm = newViewModel()
    awaitCondition("session restore") { vm.authRepo.restoreJob.isCompleted }
    vm.completeOnboardingProfile("Minh", "Linh", "", "01/01/2024")
    awaitCondition("onboarding anniversaryDate and loveDays") {
      vm.anniversaryDate.value == "01/01/2024" && vm.loveDays.value > 0
    }
  }
}
