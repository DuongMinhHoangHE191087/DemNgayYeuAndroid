package com.example.ui.components

// V-09 run C: pins DigitalTrophyRewardDialog as it behaves today (FP-107: the custom milestone note and its Save / Done paths, the share and celebrate buttons,
// the locked state, the unlock boundary, the showcase filter counts).
// Real view model + in-memory Room, English UI. The dialog is rendered directly with a chosen currentLoveDays; its badge comes from seeded rows.
// Deviation from the plan row: the share button does not build an Intent. It copies a text to the CLIPBOARD and shows a toast; the copied text and the toast follow the UI language.
import android.app.Application
import android.content.ClipboardManager
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.LoveBadgeEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
class BadgeRewardNoteTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()
  private val shownBadge = mutableStateOf<LoveBadgeEntity?>(null)

  private val loveDays = 150
  private val unlocked = badge("v09_badge_100", 100)
  private val locked = badge("v09_badge_900", 900)
  private val exact = badge("v09_badge_150", loveDays) // reached exactly today
  private val savedToast = "🏆 Badge honored and memory saved to Heart Trophy!"
  private val emptyNote = "No personal note yet. Tap pencil to record your memory!"

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

  private fun badge(id: String, targetDays: Int) = LoveBadgeEntity(
    id = id,
    targetDays = targetDays,
    titleVi = "v09 vi $id",
    titleEn = "v09 title $id",
    descVi = "v09 mo ta $id",
    descEn = "v09 desc $id",
    tier = "GOLD",
    iconType = "trophy_heart",
    rewardQuoteVi = "v09 loi $id",
    rewardQuoteEn = "v09 quote $id"
  )

  private fun badgeRow(id: String): LoveBadgeEntity =
    runBlocking { db.inLoveDao().getAllLoveBadges().first().first { it.id == id } }

  private fun clipboardText(): String? {
    val clipboard = ApplicationProvider.getApplicationContext<Application>().getSystemService(ClipboardManager::class.java)
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
  }

  // Seeds the three badges before the view model exists (the seeder only fills empty tables), then renders the dialog for `shown`.
  private fun launch(shown: LoveBadgeEntity): InLoveViewModel {
    runBlocking { db.inLoveDao().insertLoveBadges(listOf(unlocked, locked, exact)) }
    val viewModel = newViewModel()
    viewModel.setLanguage(AppLanguage.EN)
    viewModel.clearToast() // setLanguage toasts "Switched to English"
    shownBadge.value = badgeRow(shown.id)
    composeTestRule.setContent {
      MyApplicationTheme {
        shownBadge.value?.let { DigitalTrophyRewardDialog(it, loveDays, viewModel, onDismiss = {}) }
      }
    }
    composeTestRule.waitForIdle()
    return viewModel
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
  fun `FP-107 unlocked badge note edit and Save Note writes the note to the badge row and shows it`() {
    val note = "v09 our first picnic"
    val viewModel = launch(unlocked)
    val heartsBefore = viewModel.floatingHeartsTrigger.value
    assertEquals("", badgeRow(unlocked.id).customNote)
    assertFalse(badgeRow(unlocked.id).isClaimed)
    assertNull(badgeRow(unlocked.id).claimedTimestamp)
    composeTestRule.onNodeWithText(emptyNote).assertIsDisplayed()
    composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()

    // The pencil toggles the editor: the first tap opens it, a second tap closes it again and saves nothing.
    composeTestRule.onNodeWithContentDescription("Edit note").performClick()
    composeTestRule.onNode(hasSetTextAction()).assertExists()
    composeTestRule.onNodeWithText(emptyNote).assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("Edit note").performClick()
    composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()
    composeTestRule.onNodeWithText(emptyNote).assertIsDisplayed()
    assertNull(viewModel.toastMessage.value)
    assertFalse(badgeRow(unlocked.id).isClaimed)

    composeTestRule.onNodeWithContentDescription("Edit note").performClick()
    composeTestRule.onNode(hasSetTextAction()).performTextReplacement(note)
    composeTestRule.onNodeWithText("Save Note").performClick()

    eventually { assertEquals("badge row customNote", note, badgeRow(unlocked.id).customNote) }
    assertTrue("saving marks the badge claimed", badgeRow(unlocked.id).isClaimed)
    assertNotNull("saving stamps the claim time", badgeRow(unlocked.id).claimedTimestamp)
    eventually { assertEquals(savedToast, viewModel.toastMessage.value) }
    assertNotEquals("floating hearts started", heartsBefore, viewModel.floatingHeartsTrigger.value)
    assertTrue("a milestone shower", viewModel.isMilestoneCelebration.value)
    composeTestRule.onNodeWithText(note).assertIsDisplayed()
    composeTestRule.onNodeWithText(emptyNote).assertDoesNotExist()
    composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()
  }

  // The note is saved through the same view model call the Save Note button uses; then the dialog is disposed and rendered again from a fresh DAO read.
  // The badge is the middle one of the three rows (150 days), so a claim that lands on the first row of the table would be seen.
  @Test
  fun `FP-107 saved note survives closing and re-opening the dialog for the same badge`() {
    val note = "v09 note that must survive"
    val viewModel = launch(exact)
    composeTestRule.onNodeWithText(emptyNote).assertIsDisplayed()

    viewModel.claimBadge(exact.id, note)
    eventually { assertEquals("badge row customNote", note, badgeRow(exact.id).customNote) }
    assertTrue(badgeRow(exact.id).isClaimed)
    assertEquals("", badgeRow(unlocked.id).customNote)
    assertFalse(badgeRow(unlocked.id).isClaimed)
    assertFalse(badgeRow(locked.id).isClaimed)

    composeTestRule.runOnIdle { shownBadge.value = null }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText(emptyNote).assertDoesNotExist()
    composeTestRule.onNodeWithTag("share_badge_button").assertDoesNotExist()

    composeTestRule.runOnIdle { shownBadge.value = badgeRow(exact.id) }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText(note).assertIsDisplayed()
    composeTestRule.onNodeWithText(emptyNote).assertDoesNotExist()
  }

  // What the dialog shows for a badge in the English UI: its title, the milestone line, the couple names, the inscription (the quote in quotation marks) and the description.
  @Test
  fun `FP-107 dialog shows the badge title the milestone line the couple names the quote and the description`() {
    launch(unlocked)

    composeTestRule.onNodeWithText("v09 title v09_badge_100").assertIsDisplayed()
    composeTestRule.onNodeWithText("Milestone of 100 days of love").assertIsDisplayed()
    composeTestRule.onNodeWithText("You & Partner").assertIsDisplayed()
    composeTestRule.onNodeWithText("\"v09 quote v09_badge_100\"").assertIsDisplayed()
    composeTestRule.onNodeWithText("v09 desc v09_badge_100").assertIsDisplayed()
  }

  // Saving with an empty field (the call Save Note makes) does not wipe the note saved before: the repository keeps the old note.
  @Test
  fun `FP-107 saving an empty note keeps the note saved before`() {
    runBlocking { db.inLoveDao().insertLoveBadges(listOf(unlocked, locked)) }
    val viewModel = newViewModel()
    viewModel.setLanguage(AppLanguage.EN)
    viewModel.claimBadge(unlocked.id, "v09 first note")
    eventually { assertEquals(savedToast, viewModel.toastMessage.value) } // the toast is the last step of a save: the first save is completely over here
    assertEquals("badge row customNote", "v09 first note", badgeRow(unlocked.id).customNote)
    viewModel.clearToast() // from here on only the second save can bring the toast back, so its toast is the signal that its write is done

    viewModel.claimBadge(unlocked.id, "")

    eventually { assertEquals(savedToast, viewModel.toastMessage.value) }
    assertEquals("v09 first note", badgeRow(unlocked.id).customNote)
    assertTrue(badgeRow(unlocked.id).isClaimed)
  }

  // The keyboard Done key runs the same save as the Save Note button.
  @Test
  fun `FP-107 keyboard Done in the note field saves the note and closes the editor`() {
    val note = "v09 saved with the Done key"
    val viewModel = launch(unlocked)

    composeTestRule.onNodeWithContentDescription("Edit note").performClick()
    composeTestRule.onNode(hasSetTextAction()).performTextReplacement(note)
    composeTestRule.onNode(hasSetTextAction()).performImeAction()

    eventually { assertEquals("badge row customNote", note, badgeRow(unlocked.id).customNote) }
    eventually { assertEquals(savedToast, viewModel.toastMessage.value) }
    composeTestRule.onNodeWithText(note).assertIsDisplayed()
    composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()
  }

  // The plan says "creates the right share Intent"; the code copies the milestone text (in the UI language) to the clipboard and shows a toast (no Intent).
  @Test
  fun `FP-107 share button on an unlocked badge copies the milestone text to the clipboard and toasts`() {
    val viewModel = launch(unlocked)
    assertNull("clipboard starts empty", clipboardText())
    assertNull(viewModel.toastMessage.value)

    composeTestRule.onNodeWithTag("share_badge_button").performClick()

    eventually {
      assertEquals(
        "🏆 You & Partner reached the \"v09 title v09_badge_100\" milestone (100 days together)! 💕 v09 quote v09_badge_100",
        clipboardText()
      )
    }
    assertEquals("Milestone achievement copied to clipboard! 📋💕", viewModel.toastMessage.value)
  }

  // Same click under a Vietnamese UI: the copied text and the toast switch language with it.
  @Test
  fun `FP-107 share button copies the Vietnamese milestone text and toast when the UI language is Vietnamese`() {
    val viewModel = launch(unlocked)
    viewModel.setLanguage(AppLanguage.VI)
    viewModel.clearToast() // setLanguage toasts the switch notice
    composeTestRule.waitForIdle() // the dialog recomposes with the Vietnamese wording before the click

    composeTestRule.onNodeWithTag("share_badge_button").performClick()

    eventually {
      assertEquals(
        "🏆 Bạn & Người ấy đã đạt cột mốc \"v09 vi v09_badge_100\" (100 ngày bên nhau)! 💕 v09 loi v09_badge_100",
        clipboardText()
      )
    }
    assertEquals("Đã sao chép vinh danh cột mốc vào bộ nhớ tạm! 📋💕", viewModel.toastMessage.value)
  }

  @Test
  fun `FP-107 celebrate button on an unlocked badge toasts the celebration and does not touch the clipboard`() {
    val viewModel = launch(unlocked)
    val heartsBefore = viewModel.floatingHeartsTrigger.value
    assertNull(viewModel.toastMessage.value)

    composeTestRule.onNodeWithTag("celebrate_badge_button").performClick()

    eventually { assertEquals("🎉 Celebrating 100 days together! 💕✨", viewModel.toastMessage.value) }
    assertNotEquals("floating hearts started", heartsBefore, viewModel.floatingHeartsTrigger.value)
    assertTrue("a milestone shower", viewModel.isMilestoneCelebration.value)
    assertNull("celebrate does not copy anything", clipboardText())
  }

  // A badge whose target equals today's love days is already unlocked: both action buttons show and there is no countdown.
  @Test
  fun `FP-107 badge whose target equals the love days counts as unlocked`() {
    launch(exact)

    composeTestRule.onNodeWithTag("share_badge_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("celebrate_badge_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("Countdown: 0 Days Left").assertDoesNotExist()
  }

  // A locked badge (900 days, 150 loved): no share and no celebrate button, one countdown button that only toasts.
  @Test
  fun `FP-107 locked badge has no share or celebrate button and the countdown button toasts the days left`() {
    val viewModel = launch(locked)
    val heartsBefore = viewModel.floatingHeartsTrigger.value
    composeTestRule.onNodeWithTag("share_badge_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("celebrate_badge_button").assertDoesNotExist()
    composeTestRule.onNodeWithText("Countdown: 750 Days Left").assertIsDisplayed()
    assertNull(viewModel.toastMessage.value)

    composeTestRule.onNodeWithText("Countdown: 750 Days Left").performClick()

    eventually { assertEquals("Keep going! Only 750 days left to unlock this heart badge! 💕", viewModel.toastMessage.value) }
    assertNotEquals("floating hearts started", heartsBefore, viewModel.floatingHeartsTrigger.value)
    assertFalse("a plain shower, not a milestone one", viewModel.isMilestoneCelebration.value)
    assertNull("a locked badge copies nothing", clipboardText())
    assertFalse(badgeRow(locked.id).isClaimed)
  }

  // The showcase dialog also shows the app's own badges if the seeder adds any: the expected counts come from the rows in the database.
  @Test
  fun `FP-107 showcase filter pills show the unlocked and upcoming counts of the badge rows`() {
    runBlocking { db.inLoveDao().insertLoveBadges(listOf(unlocked, locked)) }
    val viewModel = newViewModel()
    viewModel.setLanguage(AppLanguage.EN)
    composeTestRule.setContent { MyApplicationTheme { MilestoneBadgeShowcaseDialog(viewModel, onDismiss = {}) } }
    composeTestRule.waitForIdle()

    eventually {
      val rows = runBlocking { db.inLoveDao().getAllLoveBadges().first() }
      val days = viewModel.loveDays.value
      val unlockedCount = rows.count { it.targetDays <= days }
      assertTrue("both seeded rows are in the database", rows.size >= 2)
      composeTestRule.onNodeWithText("All (${rows.size})").assertIsDisplayed()
      composeTestRule.onNodeWithText("Unlocked ($unlockedCount)").assertIsDisplayed()
      composeTestRule.onNodeWithText("Upcoming (${rows.size - unlockedCount})").assertIsDisplayed()
    }
  }
}
