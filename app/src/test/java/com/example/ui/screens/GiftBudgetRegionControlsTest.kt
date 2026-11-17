package com.example.ui.screens

// V-09 run A: pins the partner-preferences editor of GiftScreen as it behaves today (FP-504 gift budget, FP-505 holiday region).
// Deviation from the plan row: the pickers do not pass the values "to the use case"; Save calls viewModel.savePartnerPreferences(likes, budget, region),
// which updates the couple_profile row with id 1 through the repository and the DAO. So a profile row must exist, and the tests assert that row.
import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class GiftBudgetRegionControlsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()

  private val noProfileHint = "Create your couple profile first to save."
  private val savedToast = "Partner preferences saved ❤️"

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    injectDatabase(db)
  }

  @After
  fun tearDown() {
    viewModelStore.clear() // onCleared(): cancels viewModelScope and stops the sync coordinator BEFORE the database is closed
    injectDatabase(null)
    db.close()
  }

  // InLoveViewModel reaches Room through AppDatabase.getDatabase(context); setting the INSTANCE field is how it gets the in-memory test database.
  // D-42 changes how the DAO is obtained: this is the ONE place to adapt (nothing else here may mention INSTANCE).
  private fun injectDatabase(value: AppDatabase?) {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
    field.isAccessible = true
    field.set(null, value)
  }

  private fun newViewModel(): InLoveViewModel =
    ViewModelProvider(viewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(ApplicationProvider.getApplicationContext<Application>()))[InLoveViewModel::class.java]

  private fun profile(): CoupleProfileEntity? = runBlocking { db.inLoveDao().getCoupleProfileSync() }

  private fun seedProfile(likes: String = "", budget: Long = 0L, region: String = "") {
    runBlocking {
      db.inLoveDao().insertCoupleProfile(
        CoupleProfileEntity(
          id = 1,
          partner1Name = "V09 One",
          partner1Birthday = "01/01/1999",
          partner1ProfilePicture = "",
          partner2Name = "V09 Two",
          partner2Birthday = "02/02/1999",
          partner2ProfilePicture = "",
          likesCsv = likes,
          budgetMaxVnd = budget,
          occasionRegion = region
        )
      )
    }
  }

  // The profile (when any) is inserted BEFORE the view model exists. The editor sits in the first lazy item of GiftScreen, so no scrolling is needed.
  private fun launch(): InLoveViewModel {
    val viewModel = newViewModel()
    viewModel.setLanguage(AppLanguage.EN)
    composeTestRule.setContent {
      MyApplicationTheme {
        GiftScreen(viewModel)
      }
    }
    composeTestRule.onNodeWithTag("btn_toggle_partner_preferences").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      exists("btn_save_partner_preferences")
    }
    return viewModel
  }

  private fun exists(tag: String): Boolean =
    composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

  // What the text field really holds (its editable text), not the label or the placeholder around it.
  private fun fieldText(tag: String): String =
    composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

  // A chip prints its label only while it is selected, so the label doubles as the visible "selected" state.
  private fun chipShowsLabel(tag: String, label: String): Boolean =
    composeTestRule.onAllNodes(hasTestTag(tag) and hasText(label)).fetchSemanticsNodes().isNotEmpty()

  // Save is enabled only once the profile flow has delivered the row, so the wait doubles as the "profile loaded" signal.
  private fun awaitProfileLoaded() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      runCatching { composeTestRule.onNodeWithTag("btn_save_partner_preferences").assertIsEnabled() }.isSuccess
    }
  }

  private fun typeAndSave(likes: String, budget: String, regionTag: String) {
    awaitProfileLoaded()
    composeTestRule.onNodeWithTag("partner_likes_field").performTextInput(likes)
    composeTestRule.onNodeWithTag("partner_budget_field").performTextInput(budget)
    composeTestRule.onNodeWithTag(regionTag).performClick()
    composeTestRule.onNodeWithTag("btn_save_partner_preferences").performScrollTo().performClick()
  }

  // Retries the check until it passes; on a timeout the last run throws the real assertion message.
  // The main looper is idled by hand: the view model's writes (viewModelScope) resume on it and the compose rule alone does not run them.
  private fun eventually(check: () -> Unit) {
    try {
      composeTestRule.waitUntil(timeoutMillis = 5_000) {
        shadowOf(Looper.getMainLooper()).idle()
        runCatching { check() }.isSuccess
      }
    } catch (_: Throwable) {
      // fall through: the final check below reports what is actually wrong
    }
    check()
  }

  @Test
  fun `FP-504 FP-505 saving likes budget and Vietnam region stores them in the couple profile`() {
    seedProfile()
    val before = profile()!!
    assertEquals("precondition: no likes", "", before.likesCsv)
    assertEquals("precondition: no budget", 0L, before.budgetMaxVnd)
    assertEquals("precondition: no region", "", before.occasionRegion)
    val viewModel = launch()
    awaitProfileLoaded()
    composeTestRule.onNodeWithText(noProfileHint).assertDoesNotExist()

    typeAndSave("  coffee, books  ", "500000", "partner_region_vn") // the blanks around the likes are trimmed

    composeTestRule.waitUntil(timeoutMillis = 5_000) { profile()?.occasionRegion == "VN" }
    val after = profile()!!
    assertEquals("coffee, books", after.likesCsv)
    assertEquals(500_000L, after.budgetMaxVnd)
    assertEquals("VN", after.occasionRegion)
    assertEquals("the rest of the profile is untouched", before.partner1Name, after.partner1Name)
    assertFalse("Save collapses the editor", exists("btn_save_partner_preferences"))
    eventually { assertEquals(savedToast, viewModel.toastMessage.value) }
  }

  @Test
  fun `FP-505 international region is stored and tapping the selected chip again clears it`() {
    seedProfile()
    launch()

    typeAndSave("travel", "1200", "partner_region_intl")
    composeTestRule.waitUntil(timeoutMillis = 5_000) { profile()?.occasionRegion == "INTL" }
    assertEquals("INTL", profile()!!.occasionRegion)
    assertEquals(1_200L, profile()!!.budgetMaxVnd)

    // Save collapses the editor: reopen it; the fields show what was stored and the chip shows the stored region as selected, so one tap clears it.
    composeTestRule.onNodeWithTag("btn_toggle_partner_preferences").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      exists("btn_save_partner_preferences")
    }
    assertEquals("travel", fieldText("partner_likes_field"))
    assertEquals("1200", fieldText("partner_budget_field"))
    assertTrue("the stored region shows its label", chipShowsLabel("partner_region_intl", "International"))
    assertFalse("the other chip stays plain", chipShowsLabel("partner_region_vn", "Vietnam"))
    composeTestRule.onNodeWithTag("partner_region_intl").performClick()
    assertFalse("tapping the selected chip hides its label", chipShowsLabel("partner_region_intl", "International"))
    composeTestRule.onNodeWithTag("btn_save_partner_preferences").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) { profile()?.occasionRegion == "" }
    assertEquals("region cleared", "", profile()!!.occasionRegion)
    assertEquals("budget kept", 1_200L, profile()!!.budgetMaxVnd)
  }

  @Test
  fun `FP-504 save is disabled and stores nothing while there is no couple profile`() {
    assertNull("precondition: no profile row", profile())
    launch()

    composeTestRule.onNodeWithTag("btn_save_partner_preferences").assertIsNotEnabled()
    composeTestRule.onNodeWithText(noProfileHint).assertExists()
    composeTestRule.onNodeWithTag("partner_budget_field").performTextInput("900")
    composeTestRule.onNodeWithTag("btn_save_partner_preferences").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("btn_save_partner_preferences").assertIsNotEnabled()
    assertNull("a disabled Save must not create a profile row", profile())

    composeTestRule.onNodeWithTag("btn_toggle_partner_preferences").performScrollTo().performClick()
    assertFalse("the toggle closes the editor again", exists("btn_save_partner_preferences"))
  }

  // The budget field filters its input to digits, so letters never reach the stored number.
  @Test
  fun `FP-504 budget field keeps only digits before saving`() {
    seedProfile()
    launch()

    typeAndSave("", "12ab3", "partner_region_vn")

    composeTestRule.waitUntil(timeoutMillis = 5_000) { profile()?.occasionRegion == "VN" }
    assertEquals(123L, profile()!!.budgetMaxVnd)
  }

  @Test
  fun `FP-504 FP-505 the editor opens with the stored values and an emptied budget field stores 0 meaning no limit`() {
    seedProfile(likes = "tea", budget = 777L, region = "VN")
    launch()
    awaitProfileLoaded()

    assertEquals("tea", fieldText("partner_likes_field"))
    assertEquals("777", fieldText("partner_budget_field"))
    assertTrue("the stored region shows its label", chipShowsLabel("partner_region_vn", "Vietnam"))
    assertFalse("the other chip stays plain", chipShowsLabel("partner_region_intl", "International"))

    composeTestRule.onNodeWithTag("partner_budget_field").performTextReplacement("")
    composeTestRule.onNodeWithTag("btn_save_partner_preferences").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) { profile()?.budgetMaxVnd == 0L }
    assertEquals("an empty budget field means no limit", 0L, profile()!!.budgetMaxVnd)
    assertEquals("likes kept", "tea", profile()!!.likesCsv)
    assertEquals("region kept", "VN", profile()!!.occasionRegion)
  }

  @Test
  fun `FP-504 likes field holds at most 200 characters and the budget field at 12 digits`() {
    seedProfile()
    launch()
    awaitProfileLoaded()

    composeTestRule.onNodeWithTag("partner_likes_field").performTextInput("a".repeat(250))
    composeTestRule.onNodeWithTag("partner_budget_field").performTextInput("1234567890123")

    assertEquals(200, fieldText("partner_likes_field").length)
    assertEquals("123456789012", fieldText("partner_budget_field"))
  }

  // The digit filter keeps a minus sign out of the editor, so the repository's clamp at 0 is only reachable through the view model.
  @Test
  fun `FP-504 a negative budget is stored as 0`() {
    seedProfile()
    composeTestRule.setContent { } // a compose hierarchy has to exist for the wait below
    newViewModel().savePartnerPreferences("tea", -5L, "VN")

    eventually { assertEquals("VN", profile()!!.occasionRegion) }
    assertEquals(0L, profile()!!.budgetMaxVnd)
  }
}
