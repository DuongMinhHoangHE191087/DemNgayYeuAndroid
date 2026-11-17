package com.example.ui.screens

// T7: the Home screen once a partner is set up. The "pair with your partner" banner is gone, the partner card carries no static
// OFFLINE chip, and a tap on the card still opens the edit dialog. With no partner the banner stays.
// Real view model + in-memory Room. Texts are matched exactly (substring = false): the connectivity banner says "You're offline",
// which a substring match would pick up.
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.CoupleProfileEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
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
class HomePartnerBannerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()
  private lateinit var viewModel: InLoveViewModel

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    injectDatabase(db)
  }

  @After
  fun tearDown() {
    viewModelStore.clear() // cancels viewModelScope BEFORE the database is closed
    injectDatabase(null)
    db.close()
  }

  // InLoveViewModel reaches Room through AppDatabase.getDatabase(context); setting INSTANCE hands it the in-memory database.
  private fun injectDatabase(value: AppDatabase?) {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
    field.isAccessible = true
    field.set(null, value)
  }

  private fun newViewModel(): InLoveViewModel =
    ViewModelProvider(viewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(ApplicationProvider.getApplicationContext<Application>()))[InLoveViewModel::class.java]

  // A guest whose couple row holds [partnerName] (blank = no partner set up yet); the Home screen is composed once the name reached the view model.
  private fun launch(partnerName: String, language: AppLanguage = AppLanguage.EN) {
    runBlocking {
      db.inLoveDao().insertCoupleProfile(
        CoupleProfileEntity(
          partner1Name = "Me Real", partner1Birthday = "", partner1ProfilePicture = "",
          partner2Name = partnerName, partner2Birthday = "", partner2ProfilePicture = ""
        )
      )
    }
    viewModel = newViewModel()
    viewModel.setLanguage(language)
    viewModel.clearToast() // setLanguage toasts "Switched to ..."
    await("the session restore") { viewModel.authRepo.restoreJob.isCompleted }
    shadowOf(Looper.getMainLooper()).idle()
    await("girlName") { viewModel.girlName.value == partnerName }
    composeTestRule.setContent {
      MyApplicationTheme {
        LoveHomeScreen(viewModel, onNavigateToCalendar = {}, onNavigateToGifts = {})
      }
    }
  }

  private fun await(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (true) {
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      if (System.nanoTime() > deadline) fail("timed out waiting for $what")
      LockSupport.parkNanos(1_000_000L)
    }
  }

  // Retries the check until it passes; on a timeout the last run throws the real assertion message.
  private fun eventually(check: () -> Unit) {
    try {
      composeTestRule.waitUntil(timeoutMillis = 5_000) { runCatching { check() }.isSuccess }
    } catch (_: Throwable) {
      // fall through: the final check below reports what is actually wrong
    }
    check()
  }

  private fun assertPartnerShownWithoutBannerOrChip(partnerName: String, vararg absentTexts: String) {
    eventually {
      composeTestRule.onNodeWithTag("partner_offline_profile_col").assertTextContains(partnerName)
      composeTestRule.onAllNodesWithTag("home_prompt_pair_banner").assertCountEquals(0)
      absentTexts.forEach { composeTestRule.onAllNodesWithText(it).assertCountEquals(0) }
    }
  }

  @Test
  fun `a set up partner hides the pair banner and the OFFLINE chip`() {
    launch("Lan")

    assertPartnerShownWithoutBannerOrChip("Lan", "OFFLINE", "MÁY", "Pair with your partner", "Pair >")
  }

  @Test
  fun `a set up partner hides the pair banner and the Ngoai tuyen chip in Vietnamese`() {
    launch("Lan", AppLanguage.VI)

    assertPartnerShownWithoutBannerOrChip("Lan", "Ngoại tuyến", "MÁY", "Ghép đôi cùng người ấy", "Ghép >")
  }

  @Test
  fun `no partner yet keeps the pair banner`() {
    launch("")

    eventually { composeTestRule.onNodeWithTag("home_prompt_pair_banner").assertExists() }
  }

  // P14: the pair banner is gated by coupleLoaded (restoreJob done AND the first Room emission), so a cold start never flashes it.
  @Test
  fun `coupleLoaded is false right after the view model is built and true once the restore and Room emission are done`() {
    viewModel = newViewModel()
    assertFalse(viewModel.coupleLoaded.value)

    await("the session restore") { viewModel.authRepo.restoreJob.isCompleted }
    await("coupleLoaded") { viewModel.coupleLoaded.value }
  }

  // The pair banner is absent while the gate is closed: composed straight after the view model is built (no partner, banner would show once loaded).
  @Test
  fun `no pair banner is composed while coupleLoaded is still false`() {
    viewModel = newViewModel()
    assertFalse(viewModel.coupleLoaded.value)

    composeTestRule.setContent {
      MyApplicationTheme {
        LoveHomeScreen(viewModel, onNavigateToCalendar = {}, onNavigateToGifts = {})
      }
    }
    // ponytail: the gate may open during setContent's first idle; only assert while it is still closed.
    if (!viewModel.coupleLoaded.value) {
      composeTestRule.onAllNodesWithTag("home_prompt_pair_banner").assertCountEquals(0)
    }
  }

  @Test
  fun `tapping the partner card opens the edit dialog`() {
    launch("Lan")
    assertFalse(viewModel.showEditCoupleDialog.value)

    eventually { composeTestRule.onNodeWithTag("partner_offline_profile_col").assertExists() }
    composeTestRule.onNodeWithTag("partner_offline_profile_col").performScrollTo().performClick()

    eventually { assertTrue(viewModel.showEditCoupleDialog.value) }
  }
}
