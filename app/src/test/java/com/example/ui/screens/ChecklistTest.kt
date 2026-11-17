package com.example.ui.screens

// V-09 run A: pins the gift checklist controls of GiftScreen as they behave today (FP-506: toggle, confirmed delete, delete cancel,
// the add button, and the add and rename calls). Real view model + in-memory Room, English UI.
// The text-entry dialogs cannot be driven here: add is hosted by MainActivity, rename is the private EditFieldsDialog of the row, and a text
// field inside a Dialog never reaches Compose idle under Robolectric (the same limit is noted in AddMilestoneExamplesTest). So the add button is
// pinned up to the view model flag that opens the dialog, and the add and rename calls the dialogs make are pinned at the view model, with
// their Room result (DEC-265).
import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ChecklistItemEntity
import com.example.ui.testing.NoNetworkImagesRule
import com.example.ui.testing.TALL_PIXEL8
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.flow.first
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
class ChecklistTest {

  @get:Rule val composeTestRule = createComposeRule()
  @get:Rule val noNetworkImages = NoNetworkImagesRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()
  private lateinit var viewModel: InLoveViewModel
  private val giftIdeaCard = SemanticsMatcher("is a gift idea card") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("gift_idea_") == true
  }

  private val idA = 101L
  private val idB = 102L
  private val textA = "v09-task-a"
  private val textB = "v09-task-b"

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

  private fun rows(): List<ChecklistItemEntity> = runBlocking { db.inLoveDao().getAllChecklistItems().first() }

  private fun row(id: Long): ChecklistItemEntity? = rows().firstOrNull { it.id == id }

  // The rows are inserted BEFORE the view model exists: the seeder only writes its 3 default rows when the table is empty.
  private fun seed() {
    runBlocking {
      db.inLoveDao().insertChecklistItems(
        listOf(ChecklistItemEntity(id = idA, text = textA), ChecklistItemEntity(id = idB, text = textB))
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

  // GiftScreen is a LazyColumn, but in the tall window (see TALL_PIXEL8) the checklist section is already placed, so this costs a millisecond or two.
  // It would really scroll, which is very slow under Robolectric, only if the screen outgrew the window.
  private fun scrollToSection() {
    composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("checklist_section"))
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

  private fun assertRowShows(id: Long, text: String) {
    composeTestRule.onNodeWithTag("checklist_item_$id").assertTextContains(text)
  }

  private fun toggleShowsCompleted(id: Long): Boolean =
    composeTestRule.onAllNodes(
      hasContentDescription("Completed") and hasAnyAncestor(hasTestTag("btn_toggle_checklist_$id")),
      useUnmergedTree = true
    ).fetchSemanticsNodes().isNotEmpty()

  private fun expandAndOpenDelete(id: Long) {
    eventually { assertRowShows(id, if (id == idA) textA else textB) }
    tap("checklist_item_$id")
    eventually { composeTestRule.onNodeWithTag("btn_delete_checklist_$id").assertExists() }
    tap("btn_delete_checklist_$id")
    eventually { composeTestRule.onNodeWithTag("dialog_confirm_delete").assertIsDisplayed() }
  }

  @Test
  fun `FP-506 checklist toggle button flips the completed flag and flips it back`() {
    launch()
    eventually { assertRowShows(idA, textA) }
    assertFalse("precondition: not completed in DB", row(idA)!!.isCompleted)
    assertFalse("precondition: no check mark in UI", toggleShowsCompleted(idA))

    tap("btn_toggle_checklist_$idA")
    composeTestRule.waitUntil(timeoutMillis = 5_000) { row(idA)?.isCompleted == true }
    assertTrue("DB flag flipped to completed", row(idA)!!.isCompleted)
    eventually { assertTrue("UI shows the check mark", toggleShowsCompleted(idA)) }
    assertFalse("the other row is unchanged", row(idB)!!.isCompleted)

    tap("btn_toggle_checklist_$idA")
    composeTestRule.waitUntil(timeoutMillis = 5_000) { row(idA)?.isCompleted == false }
    assertFalse("DB flag flipped back", row(idA)!!.isCompleted)
    eventually { assertFalse("UI check mark is gone", toggleShowsCompleted(idA)) }
  }

  @Test
  fun `FP-506 checklist delete confirmed removes the row from UI and DB`() {
    launch()
    expandAndOpenDelete(idA)
    assertNotNull("precondition: row still in DB while the dialog is open", row(idA))
    composeTestRule.onNodeWithTag("btn_confirm_delete").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) { row(idA) == null }
    assertNull("DB row deleted", row(idA))
    eventually { composeTestRule.onNodeWithTag("checklist_item_$idA").assertDoesNotExist() }
    eventually { composeTestRule.onNodeWithTag("dialog_confirm_delete").assertDoesNotExist() }
    assertEquals("the other row is still in the DB", textB, row(idB)?.text)
    eventually { assertRowShows(idB, textB) }
  }

  @Test
  fun `FP-506 checklist delete cancelled keeps the row in UI and DB`() {
    launch()
    expandAndOpenDelete(idA)
    val before = row(idA)
    assertNotNull("precondition: row in DB", before)
    composeTestRule.onNodeWithTag("btn_cancel_delete").performClick()

    eventually { composeTestRule.onNodeWithTag("dialog_confirm_delete").assertDoesNotExist() }
    composeTestRule.waitForIdle()
    eventually { assertRowShows(idA, textA) }
    assertEquals("DB row unchanged after cancel", before, row(idA))
    assertEquals("the other row is unchanged too", textB, row(idB)?.text)
  }

  // GiftScreen only raises the flag; MainActivity hosts the dialog that reads the text (see the header).
  @Test
  fun `FP-506 checklist add button only raises the add dialog flag and adds nothing`() {
    launch()
    assertFalse("precondition: the add dialog flag is down", viewModel.showAddChecklistDialog.value)

    tap("btn_add_checklist_task")

    composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.showAddChecklistDialog.value }
    assertTrue("the add dialog flag is up", viewModel.showAddChecklistDialog.value)
    assertEquals("the tap adds no row", setOf(textA, textB), rows().map { it.text }.toSet())
  }

  // The add dialog ends in viewModel.addChecklist(text): blank text is ignored, anything else is stored trimmed, with the default icon, not completed.
  @Test
  fun `FP-506 checklist add through the view model stores trimmed text and ignores blank text`() {
    seed()
    viewModel = newViewModel()

    viewModel.addChecklist("   ")
    viewModel.addChecklist("  v09-added  ")

    await("the added checklist row") { rows().any { it.text == "v09-added" } }
    val added = rows().single { it.text == "v09-added" }
    assertFalse("a new row is not completed", added.isCompleted)
    assertEquals("a new row gets the default icon", "check", added.iconName)
    assertEquals("the blank call added nothing", setOf(textA, textB, "v09-added"), rows().map { it.text }.toSet())
  }

  // The rename dialog ends in viewModel.renameChecklist(item, text): blank text is ignored, anything else replaces only the text, trimmed.
  // Row B gets a non-default icon and the completed flag first, so "only the text changed" can fail.
  @Test
  fun `FP-506 checklist rename through the view model changes only the text and ignores blank text`() {
    seed()
    runBlocking { db.inLoveDao().updateChecklistItem(row(idB)!!.copy(iconName = "gift", isCompleted = true)) }
    viewModel = newViewModel()
    val a = row(idA)!!
    val b = row(idB)!!

    viewModel.renameChecklist(a, "   ")
    viewModel.renameChecklist(b, "  v09-renamed-b  ")

    await("the renamed checklist row") { row(idB)?.text == "v09-renamed-b" }
    assertEquals("only the text changed", b.copy(text = "v09-renamed-b"), row(idB))
    assertEquals("the blank rename changed nothing", a, row(idA))
  }
}
