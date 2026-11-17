package com.example.ui.screens

// V-09 run A: pins the saved gift reminder controls of GiftScreen as they behave today (FP-411: Room CRUD never sets an alarm).
// Covered through the screen: complete toggle, confirmed delete, cancelled delete, and the add button up to the view model flag that opens the dialog.
// The text-entry dialogs cannot be driven here: add is hosted by MainActivity, edit is the private EditFieldsDialog of the row, and a text field
// inside a Dialog never reaches Compose idle under Robolectric (the same limit is noted in AddMilestoneExamplesTest). So the add and edit calls the
// dialogs make are pinned at the view model, with their Room result (DEC-265). The trim and fallback rules of the edit form itself live in the
// private dialog composable and stay unpinned. The optional AlarmManager check is left out; the entity-level alarm assertions stay.
import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.GiftReminderEntity
import com.example.ui.testing.NoNetworkImagesRule
import com.example.ui.testing.TALL_PIXEL8
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
@Config(qualifiers = TALL_PIXEL8, sdk = [34])
class GiftReminderCrudTest {

  @get:Rule val composeTestRule = createComposeRule()
  @get:Rule val noNetworkImages = NoNetworkImagesRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()
  private lateinit var viewModel: InLoveViewModel
  private val giftIdeaCard = SemanticsMatcher("is a gift idea card") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("gift_idea_") == true
  }

  private val idA = 201L
  private val idB = 202L
  private val titleA = "v09-gift-a"
  private val titleB = "v09-gift-b"

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

  private fun rows(): List<GiftReminderEntity> = runBlocking { db.inLoveDao().getGiftRemindersList() }

  private fun row(id: Long): GiftReminderEntity? = rows().firstOrNull { it.id == id }

  // FP-411: plain CRUD must never arm an alarm on the reminder.
  private fun assertNoAlarm(id: Long) {
    val entity = row(id)
    assertNotNull("reminder $id exists", entity)
    assertNull("no alarm time on reminder $id", entity!!.alarmTimeMillis)
    assertEquals("no alarm text on reminder $id", "", entity.alarmTimeFormatted)
  }

  // Gift reminders are not seeded, but the rows are still inserted BEFORE the view model exists so its first Room emission already holds them.
  private fun seed() {
    runBlocking {
      db.inLoveDao().insertGiftReminder(
        GiftReminderEntity(id = idA, title = titleA, recipient = "v09-recipient-a", dueDateText = "01/01/2030", estimatedBudget = "100k", notes = "v09-notes-a")
      )
      db.inLoveDao().insertGiftReminder(
        GiftReminderEntity(id = idB, title = titleB, recipient = "v09-recipient-b", dueDateText = "02/02/2030")
      )
    }
  }

  // The view model tests below compose nothing, so the paused Robolectric main looper is pumped by hand (same helper as ReminderWiringSettingsTest).
  private fun await(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (true) {
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      if (System.nanoTime() > deadline) fail("timed out waiting for $what")
      LockSupport.parkNanos(1_000_000L)
    }
  }

  private fun launch() {
    seed()
    val viewModel = newViewModel()
    this.viewModel = viewModel
    viewModel.setLanguage(AppLanguage.EN)
    composeTestRule.setContent {
      MyApplicationTheme {
        GiftScreen(viewModel)
      }
    }
    // The section is an unkeyed lazy item below the gift ideas: when the ideas arrive it moves and its row state (expanded) is lost,
    // so every test waits until the ideas are composed before it touches a row. They arrive within a second or two; 30 s is margin for a loaded machine.
    composeTestRule.waitUntil(timeoutMillis = 30_000) {
      runCatching { composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(giftIdeaCard) }.isSuccess
    }
  }

  // GiftScreen is a LazyColumn, but in the tall window (see TALL_PIXEL8) the reminders section is already placed, so this costs a millisecond or two.
  // It would really scroll, which is very slow under Robolectric, only if the screen outgrew the window.
  private fun scrollToSection() {
    composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("gift_reminders_section"))
  }

  private fun tap(tag: String) {
    scrollToSection()
    composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
  }

  // Retries the check until it passes (Room flows arrive some frames late); on a timeout the last run throws the real assertion message.
  private fun eventually(check: () -> Unit) {
    try {
      composeTestRule.waitUntil(timeoutMillis = 15_000) {
        runCatching { scrollToSection(); check() }.isSuccess
      }
    } catch (_: Throwable) {
      // fall through: the final check below reports what is actually wrong
    }
    scrollToSection()
    check()
  }

  private fun assertRowShows(id: Long, title: String) {
    composeTestRule.onNodeWithTag("gift_reminder_item_$id").assertTextContains(title)
  }

  private fun expandAndOpenDelete(id: Long) {
    eventually { assertRowShows(id, if (id == idA) titleA else titleB) }
    tap("gift_reminder_item_$id")
    eventually { composeTestRule.onNodeWithTag("btn_delete_gift_reminder_$id").assertExists() }
    tap("btn_delete_gift_reminder_$id")
    eventually { composeTestRule.onNodeWithTag("dialog_confirm_delete").assertIsDisplayed() }
  }

  // The section header counts "completed/total" over the saved reminders, which is the observable completed state.
  @Test
  fun `FP-411 gift reminder complete toggle flips the flag and the counter and sets no alarm`() {
    launch()
    eventually { assertRowShows(idA, titleA) }
    eventually { composeTestRule.onNodeWithText("0/2").assertExists() }
    assertFalse("precondition: not completed in DB", row(idA)!!.isCompleted)
    assertNoAlarm(idA)

    tap("btn_toggle_gift_reminder_$idA")
    composeTestRule.waitUntil(timeoutMillis = 5_000) { row(idA)?.isCompleted == true }
    assertTrue("DB flag flipped to completed", row(idA)!!.isCompleted)
    eventually { composeTestRule.onNodeWithText("1/2").assertExists() }
    assertFalse("the other reminder is unchanged", row(idB)!!.isCompleted)
    assertNoAlarm(idA)
    assertNoAlarm(idB)

    tap("btn_toggle_gift_reminder_$idA")
    composeTestRule.waitUntil(timeoutMillis = 5_000) { row(idA)?.isCompleted == false }
    assertFalse("DB flag flipped back", row(idA)!!.isCompleted)
    eventually { composeTestRule.onNodeWithText("0/2").assertExists() }
    assertNoAlarm(idA)
  }

  @Test
  fun `FP-411 gift reminder delete confirmed removes the row from UI and DB and sets no alarm`() {
    launch()
    expandAndOpenDelete(idA)
    assertNotNull("precondition: row still in DB while the dialog is open", row(idA))
    composeTestRule.onNodeWithTag("btn_confirm_delete").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) { row(idA) == null }
    assertNull("DB row deleted", row(idA))
    eventually { composeTestRule.onNodeWithTag("gift_reminder_item_$idA").assertDoesNotExist() }
    eventually { composeTestRule.onNodeWithTag("dialog_confirm_delete").assertDoesNotExist() }
    assertEquals("the other reminder is still in the DB", titleB, row(idB)?.title)
    assertNoAlarm(idB)
    eventually { assertRowShows(idB, titleB) }
  }

  @Test
  fun `FP-411 gift reminder delete cancelled keeps the row in UI and DB and sets no alarm`() {
    launch()
    expandAndOpenDelete(idA)
    val before = row(idA)
    assertNotNull("precondition: row in DB", before)
    composeTestRule.onNodeWithTag("btn_cancel_delete").performClick()

    eventually { composeTestRule.onNodeWithTag("dialog_confirm_delete").assertDoesNotExist() }
    composeTestRule.waitForIdle()
    eventually { assertRowShows(idA, titleA) }
    assertEquals("DB row unchanged after cancel", before, row(idA))
    assertNoAlarm(idA)
    assertEquals("the other reminder is unchanged too", titleB, row(idB)?.title)
  }

  // GiftScreen only raises the flag; MainActivity hosts the dialog that reads the text (see the header).
  @Test
  fun `FP-411 gift reminder add button only raises the add dialog flag and adds nothing`() {
    launch()
    assertFalse("precondition: the add dialog flag is down", viewModel.showAddGiftReminderDialog.value)

    tap("btn_add_gift_reminder_header")

    composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.showAddGiftReminderDialog.value }
    assertTrue("the add dialog flag is up", viewModel.showAddGiftReminderDialog.value)
    assertEquals("the tap adds no reminder", setOf(titleA, titleB), rows().map { it.title }.toSet())
  }

  // The add dialog ends in viewModel.addGiftReminder(...): a blank title is refused and the dialog stays open; anything else is stored trimmed,
  // empty fields fall back to their defaults, the dialog flag goes down and no alarm is armed.
  @Test
  fun `FP-411 gift reminder add through the view model trims fills defaults closes the dialog and sets no alarm`() {
    seed()
    viewModel = newViewModel()
    viewModel.openAddGiftReminderDialog()

    viewModel.addGiftReminder("   ")
    assertTrue("a blank title keeps the dialog open", viewModel.showAddGiftReminderDialog.value)

    viewModel.addGiftReminder("  v09-added  ", recipient = "   ", occasion = "", dueDateText = " ", estimatedBudget = "", notes = "  v09-added-notes  ")

    await("the added gift reminder") { rows().any { it.title == "v09-added" } }
    await("the add dialog flag to go down") { !viewModel.showAddGiftReminderDialog.value }
    val added = rows().single { it.title == "v09-added" }
    assertEquals("Người ấy", added.recipient)
    assertEquals("Kỷ niệm ngày yêu", added.occasion)
    assertEquals("Sớm nhất", added.dueDateText)
    assertEquals("Tùy chọn", added.estimatedBudget)
    assertEquals("v09-added-notes", added.notes)
    assertFalse("a new reminder is not completed", added.isCompleted)
    assertNoAlarm(added.id)
    assertEquals("the blank call added nothing", setOf(titleA, titleB, "v09-added"), rows().map { it.title }.toSet())
  }

  // The edit dialog ends in viewModel.updateGiftReminder(item) with an edited copy of the row: the row is replaced as given and no alarm is armed.
  @Test
  fun `FP-411 gift reminder edit through the view model replaces the row and sets no alarm`() {
    seed()
    viewModel = newViewModel()
    val a = row(idA)!!
    val b = row(idB)!!
    val edited = a.copy(title = "v09-gift-a-edited", notes = "v09-notes-a-edited")

    viewModel.updateGiftReminder(edited)

    await("the edited gift reminder") { row(idA)?.title == "v09-gift-a-edited" }
    assertEquals("the row is replaced as given", edited, row(idA))
    assertEquals("the other reminder is untouched", b, row(idB))
    assertNoAlarm(idA)
    assertNoAlarm(idB)
  }
}
