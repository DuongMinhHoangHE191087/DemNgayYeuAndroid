package com.example.ui.screens

// V-09 run B: pins the four filter chips of MemoriesGridScreen as they behave today (FP-213: All, Couple, Video, Favorites; the empty state
// of a filter with no match; the selection is a plain remember, so it is not kept when the screen is left and opened again).
// Real view model + in-memory Room, English UI. The rows have no media (empty photoUri, no cloud id), so no image is loaded.
import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.SharedMemoryEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class MemoryFilterTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()
  private val screenVisible = mutableStateOf(true)

  // m1 image/couple, m2 video/couple/favorite, m3 image/private/favorite, m4 video/private: every filter gives a different non-empty list.
  private val m1 = memory(201, "v09-m1", "COUPLE_ONLY", video = false, favorite = false)
  private val m2 = memory(202, "v09-m2", "COUPLE_ONLY", video = true, favorite = true)
  private val m3 = memory(203, "v09-m3", "PRIVATE", video = false, favorite = true)
  private val m4 = memory(204, "v09-m4", "PRIVATE", video = true, favorite = false)
  private val all = listOf(m1, m2, m3, m4)

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

  private fun memory(id: Long, title: String, privacy: String, video: Boolean, favorite: Boolean) = SharedMemoryEntity(
    id = id,
    title = title,
    dateText = "01/01/2026",
    photoUri = "",
    isFavorite = favorite,
    mediaType = if (video) "VIDEO" else "IMAGE",
    privacyLevel = privacy,
    syncId = "v09-sync-$id"
  )

  private fun launch(rows: List<SharedMemoryEntity>) {
    runBlocking { db.inLoveDao().insertSharedMemories(rows) }
    val viewModel = newViewModel()
    viewModel.setLanguage(AppLanguage.EN)
    composeTestRule.setContent {
      MyApplicationTheme {
        if (screenVisible.value) MemoriesGridScreen(viewModel)
      }
    }
    // Room flows arrive some frames after setContent: wait for the first row before any assertion.
    eventually { composeTestRule.onNodeWithTag("memory_card_${rows.first().id}").assertExists() }
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

  // The cards of `shown` are on screen and every other seeded card is not composed at all.
  private fun assertCards(shown: List<SharedMemoryEntity>) {
    eventually {
      all.forEach { m ->
        val card = composeTestRule.onNodeWithTag("memory_card_${m.id}")
        if (m in shown) card.assertIsDisplayed() else card.assertDoesNotExist()
      }
    }
  }

  private fun assertSelectedChip(selected: String) {
    eventually {
      listOf("All", "Couple", "Video", "Favorites").forEach { label ->
        val chip = composeTestRule.onNodeWithText(label)
        if (label == selected) chip.assertIsSelected() else chip.assertIsNotSelected()
      }
    }
  }

  private fun tapChip(label: String) {
    composeTestRule.onNodeWithText(label).performClick()
  }

  @Test
  fun `FP-213 memory filter All is selected at the start and shows every card`() {
    launch(all)

    assertSelectedChip("All")
    assertCards(all)
  }

  @Test
  fun `FP-213 memory filter chips Couple Video Favorites and All each show exactly their cards`() {
    launch(all)
    assertCards(all)

    tapChip("Couple")
    assertSelectedChip("Couple")
    assertCards(listOf(m1, m2))

    tapChip("Video")
    assertSelectedChip("Video")
    assertCards(listOf(m2, m4))

    tapChip("Favorites")
    assertSelectedChip("Favorites")
    assertCards(listOf(m2, m3))

    tapChip("All")
    assertSelectedChip("All")
    assertCards(all)
  }

  // Only m1 (image, couple, not a favorite) exists: Video and Favorites match nothing and show their own empty text, and the chips still work.
  @Test
  fun `FP-213 memory filter with no match shows the empty state of that filter and the chips stay usable`() {
    launch(listOf(m1))
    assertCards(listOf(m1))

    tapChip("Video")
    eventually { composeTestRule.onNodeWithText("No memory videos yet").assertIsDisplayed() }
    composeTestRule.onNodeWithTag("memory_card_${m1.id}").assertDoesNotExist()

    tapChip("Favorites")
    eventually { composeTestRule.onNodeWithText("No favorite memories yet").assertIsDisplayed() }
    composeTestRule.onNodeWithText("No memory videos yet").assertDoesNotExist()
    composeTestRule.onNodeWithTag("memory_card_${m1.id}").assertDoesNotExist()

    tapChip("Couple")
    assertCards(listOf(m1))
    composeTestRule.onNodeWithText("No favorite memories yet").assertDoesNotExist()
  }

  @Test
  fun `FP-213 memory filter selection is not kept when the screen is left and opened again`() {
    launch(all)

    tapChip("Video")
    assertSelectedChip("Video")
    assertCards(listOf(m2, m4))

    composeTestRule.runOnIdle { screenVisible.value = false }
    eventually { composeTestRule.onNodeWithTag("memories_grid").assertDoesNotExist() }
    composeTestRule.runOnIdle { screenVisible.value = true }

    eventually { composeTestRule.onNodeWithTag("memories_grid").assertExists() }
    assertSelectedChip("All")
    assertCards(all)
  }
}
