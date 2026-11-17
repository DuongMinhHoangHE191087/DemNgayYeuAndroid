package com.example.ui.components

// V-09 run C: pins NotificationsSheet as it behaves today (FP-409: the list of the user's reminders, the Mark all read button, a tap on a row).
// Real view model + in-memory Room, English UI. The sheet also lists the upcoming love milestones, so rows are found by scrolling to their unique v09- text.
import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.CustomReminderEntity
import com.example.ui.testing.TALL_PIXEL8
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
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
@Config(qualifiers = TALL_PIXEL8, sdk = [34])
class NotificationListMarkReadTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()

  // r1 has no alarm time (the row shows its dateText), r2 has one (the row shows the formatted alarm time instead).
  private val r1 = CustomReminderEntity(title = "v09-reminder-a", dateText = "v09-date-a", details = "", daysRemainingText = "")
  private val r2 = CustomReminderEntity(
    title = "v09-reminder-b",
    dateText = "v09-date-b",
    details = "",
    daysRemainingText = "",
    alarmTimeMillis = 1_900_000_000_000L,
    alarmTimeFormatted = "v09-alarm-b"
  )
  private val allMarked = "All notifications marked as read"

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

  // Seeds the reminders before the view model exists, opens the sheet and waits until the view model has the reminders.
  private fun launch(rows: List<CustomReminderEntity>): InLoveViewModel {
    runBlocking { rows.forEach { db.inLoveDao().insertCustomReminder(it) } }
    val viewModel = newViewModel()
    viewModel.setLanguage(AppLanguage.EN)
    viewModel.clearToast() // setLanguage toasts "Switched to English"
    composeTestRule.setContent { MyApplicationTheme { NotificationsSheet(viewModel, onDismiss = {}) } }
    composeTestRule.waitForIdle() // ModalBottomSheet settles after the first frames
    eventually { assertEquals(rows.map { it.title }.toSet(), viewModel.customReminders.value.map { it.title }.toSet()) }
    return viewModel
  }

  // Retries the check until it passes; on a timeout the last run throws the real assertion message.
  // The main looper is idled by hand: the view model's flows resume on it and the compose rule alone does not run them.
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

  // The list is a LazyColumn that also holds the milestone rows: scroll the row into view before asserting it.
  // In the tall window (see TALL_PIXEL8) the rows are already placed, so this costs a millisecond or two.
  // It would really scroll, which is very slow under Robolectric, only if the sheet outgrew the window.
  private fun scrollTo(text: String) {
    eventually {
      composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
      composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }
  }

  @Test
  fun `FP-409 notification list shows the reminders with their date or alarm time and the sheet header`() {
    launch(listOf(r1, r2))

    composeTestRule.onNodeWithTag("notifications_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithText("Notifications").assertIsDisplayed()
    composeTestRule.onNodeWithText("All quiet").assertDoesNotExist()
    scrollTo(r1.title)
    scrollTo(r1.dateText)
    scrollTo(r2.title)
    scrollTo(r2.alarmTimeFormatted)
    composeTestRule.onNodeWithText(r2.dateText).assertDoesNotExist()
  }

  @Test
  fun `FP-409 Mark all read button disables itself marks everything read and toasts`() {
    val viewModel = launch(listOf(r1))
    assertFalse(viewModel.isAllNotificationsRead.value)
    assertNull(viewModel.toastMessage.value)
    composeTestRule.onNodeWithTag("btn_mark_all_read").assertIsEnabled()

    composeTestRule.onNodeWithTag("btn_mark_all_read").performClick()

    eventually { assertTrue("all notifications read", viewModel.isAllNotificationsRead.value) }
    eventually { assertEquals(allMarked, viewModel.toastMessage.value) }
    eventually { composeTestRule.onNodeWithTag("btn_mark_all_read").assertIsNotEnabled() }
    scrollTo(r1.title) // read notifications stay in the list
  }

  @Test
  fun `FP-409 tapping a notification row marks all notifications read and toasts`() {
    val viewModel = launch(listOf(r1))
    assertFalse(viewModel.isAllNotificationsRead.value)
    assertNull(viewModel.toastMessage.value)
    composeTestRule.onNodeWithTag("btn_mark_all_read").assertIsEnabled()
    scrollTo(r1.title)

    composeTestRule.onNodeWithText(r1.title).performClick()

    eventually { assertTrue("all notifications read", viewModel.isAllNotificationsRead.value) }
    eventually { assertEquals(allMarked, viewModel.toastMessage.value) }
    eventually { composeTestRule.onNodeWithTag("btn_mark_all_read").assertIsNotEnabled() }
  }
}
