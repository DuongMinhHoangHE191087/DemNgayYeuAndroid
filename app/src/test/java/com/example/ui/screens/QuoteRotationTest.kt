package com.example.ui.screens

// V-09 run B: pins the quote card of LoveHomeScreen as it behaves today (FP-112: the shuffle button moves to the next quote and wraps
// around, and the position is a plain remember, so it is not kept when the screen is left and opened again; the send button toasts and starts the hearts).
// Real view model + in-memory Room, English UI (one test in Vietnamese). The list of quotes depends on the language (6 English and 6 Vietnamese).
// The quotes are matched by their full text: quote 2 and quote 6 carry the number of love days, which is read from the view model.
import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.viewmodel.InLoveViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
class QuoteRotationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var db: AppDatabase
  private val viewModelStore = ViewModelStore()
  private val screenVisible = mutableStateOf(true)
  private lateinit var viewModel: InLoveViewModel
  private var language = AppLanguage.EN

  // The six quotes of LoveHomeScreen in the language of the test, in order, exactly as shown (quotes 2 and 6 carry the number of love days).
  private fun quotes(): List<String> {
    val days = viewModel.loveDays.value
    return if (language == AppLanguage.VI) {
      listOf(
        "Dù mai này cuộc sống có bận rộn đến đâu, anh vẫn luôn chọn yêu em như những ngày đầu tiên 💕",
        "Cảm ơn vì đã cùng anh đi qua $days ngày ngọt ngào, đầy ắp tiếng cười và những cái ôm ấm áp ✨",
        "Tình yêu không phải là tìm một người hoàn hảo, mà là cùng nhau hoàn thiện và bao dung mỗi ngày 🌸",
        "Bên cạnh em, mỗi phút giây bình dị nhất cũng trở thành những kỷ niệm vô giá của cuộc đời anh 💖",
        "Cảm ơn em vì đã là mảnh ghép dịu dàng nhất bước vào thanh xuân của anh 🌷",
        "$days ngày bên nhau không chỉ là một con số, mà là minh chứng cho tình yêu bền chặt của đôi ta 💍"
      )
    } else {
      listOf(
        "No matter how busy life gets, I will always choose to love you like day one 💕",
        "Thank you for walking with me through $days sweet days filled with warm smiles and hugs ✨",
        "Love isn't about finding someone perfect, but growing and cherishing each other every day 🌸",
        "Beside you, even the simplest moments turn into priceless treasures 💖",
        "Thank you for being the sweetest part of my youth 🌷",
        "$days days together is not just a number, but a testament to our everlasting love 💍"
      )
    }
  }

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

  private fun launch() {
    viewModel = newViewModel()
    viewModel.setLanguage(language)
    viewModel.clearToast() // setLanguage toasts "Switched to English"
    composeTestRule.setContent {
      MyApplicationTheme {
        if (screenVisible.value) LoveHomeScreen(viewModel, onNavigateToCalendar = {}, onNavigateToGifts = {})
      }
    }
    eventually { assertQuoteShown(0) }
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

  // The counter reads "<n>/6", the quote of position n is on screen and no other quote is.
  private fun assertQuoteShown(index: Int) {
    composeTestRule.onNodeWithText("${index + 1}/6").assertExists()
    quotes().forEachIndexed { i, text ->
      val node = composeTestRule.onNode(hasText(text))
      if (i == index) node.assertExists() else node.assertDoesNotExist()
    }
  }

  private fun shuffle() {
    composeTestRule.onNodeWithTag("btn_shuffle_quote").performScrollTo().performClick()
  }

  @Test
  fun `FP-112 quote counter starts at the first quote`() {
    launch()

    eventually { assertQuoteShown(0) }
  }

  @Test
  fun `FP-112 quote shuffle button advances the counter and changes the quote`() {
    launch()
    eventually { assertQuoteShown(0) }
    assertNull(viewModel.toastMessage.value)

    shuffle()
    eventually { assertQuoteShown(1) }
    assertEquals("New love reflection loaded ✨", viewModel.toastMessage.value)

    shuffle()
    eventually { assertQuoteShown(2) }
  }

  // Six quotes: the sixth tap comes back to the first one.
  @Test
  fun `FP-112 quote shuffle button wraps around to the first quote after the last`() {
    launch()
    for (next in 1..5) {
      shuffle()
      eventually { assertQuoteShown(next) }
    }
    composeTestRule.onNodeWithText("6/6").assertExists()

    shuffle()

    eventually { assertQuoteShown(0) }
    composeTestRule.onNodeWithText("6/6").assertDoesNotExist()
  }

  // The Vietnamese list has its own six quotes and its own shuffle toast.
  @Test
  fun `FP-112 quote texts and shuffle toast in Vietnamese`() {
    language = AppLanguage.VI
    launch()

    for (next in 1..5) {
      shuffle()
      eventually { assertQuoteShown(next) }
    }
    assertEquals("Đã đổi sang thông điệp yêu thương mới ✨", viewModel.toastMessage.value)

    shuffle()
    eventually { assertQuoteShown(0) }
  }

  @Test
  fun `FP-112 quote position is not kept when the screen is left and opened again`() {
    launch()
    shuffle()
    shuffle()
    eventually { assertQuoteShown(2) }

    composeTestRule.runOnIdle { screenVisible.value = false }
    eventually { composeTestRule.onNodeWithTag("btn_shuffle_quote").assertDoesNotExist() }
    composeTestRule.runOnIdle { screenVisible.value = true }

    eventually { assertQuoteShown(0) }
  }

  // The toast says "copied", but the code puts nothing on the clipboard today: the button only toasts for the partner and starts the floating hearts.
  @Test
  fun `FP-112 quote send button toasts for the partner and starts the floating hearts`() {
    launch()
    val heartsBefore = viewModel.floatingHeartsTrigger.value
    assertNull(viewModel.toastMessage.value)

    composeTestRule.onNodeWithTag("btn_send_quote").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

    eventually { assertEquals("Sweet love quote copied to send to Partner! 💌", viewModel.toastMessage.value) }
    assertNotEquals("floating hearts started", heartsBefore, viewModel.floatingHeartsTrigger.value)
    assertFalse("a plain shower, not a milestone one", viewModel.isMilestoneCelebration.value)
    assertQuoteShown(0)
  }
}
