package com.example.ui.screens

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

  private fun quotes(): List<String> {
    val days = viewModel.loveDays.value
    return if (language == AppLanguage.VI) {
      listOf(
        "Dù ngày mai có bận rộn đến đâu, anh vẫn chọn lắng nghe và yêu em như những ngày đầu tiên.",
        "Cảm ơn em đã cùng anh đi qua $days ngày bình yên, qua những bữa cơm tối và cái ôm cuối ngày.",
        "Tình yêu bền lâu không phải là không bao giờ giận hờn, mà là luôn tìm cách quay về bên nhau sau mỗi lần hiểu lầm.",
        "Bên cạnh em, những khoảnh khắc giản dị nhất như đi dạo cuối tuần cũng trở thành điều anh mong đợi.",
        "Cảm ơn em đã kiên nhẫn bước vào cuộc sống của anh bằng tất cả sự dịu dàng.",
        "$days ngày bên nhau không chỉ là một con số, mà là từng bước nhỏ mình cùng giữ lời hứa với nhau.",
        "Không cần dịp thật lớn mới nói lời thương, chỉ cần mỗi sáng thức dậy vẫn thấy hai đứa còn nhớ về nhau.",
        "Anh thích cách hai đứa có thể im lặng ngồi cạnh nhau hàng giờ mà vẫn thấy lòng nhẹ tênh.",
        "Mỗi mùa đi qua, điều anh trân trọng nhất vẫn là được nhìn em cười trong những câu chuyện nhỏ hằng ngày.",
        "Cảm ơn vì đã tin tưởng và nắm tay anh cả những lúc mọi thứ chưa thật sự dễ dàng.",
        "Chỉ cần biết tối nay vẫn có em chờ chia sẻ chuyện vui buồn, cả ngày dài bỗng trở nên dịu lại.",
        "Hành trình dài hay ngắn không quan trọng bằng việc mỗi chặng đường đều có dấu chân của cả hai."
      )
    } else {
      listOf(
        "No matter how busy tomorrow gets, I still choose to listen and care for you like day one.",
        "Thank you for walking with me through $days quiet days, shared dinners, and warm evening talks.",
        "Lasting love is not the absence of small misunderstandings, but always finding our way back to kindness.",
        "Beside you, even an ordinary weekend walk feels like something worth remembering.",
        "Thank you for stepping into my life with patience, warmth, and quiet steadiness.",
        "$days days together is not just a count on a screen, it is every promise we kept along the way.",
        "We do not need a grand occasion to say we care, just remembering the small things each morning.",
        "I love that we can sit together in quiet company for hours and still feel completely at home.",
        "With every passing season, my favorite moments are still hearing you laugh over everyday stories.",
        "Thank you for holding my hand through the uncertain days as faithfully as the bright ones.",
        "Knowing I can tell you about my day at the end of it makes every hard hour softer.",
        "The distance ahead matters less when every milestone carries both of our footprints."
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
    viewModelStore.clear()
    injectDatabase(null)
    db.close()
  }

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
    viewModel.clearToast()
    composeTestRule.setContent {
      MyApplicationTheme {
        if (screenVisible.value) LoveHomeScreen(viewModel, onNavigateToCalendar = {}, onNavigateToGifts = {})
      }
    }
    eventually { assertQuoteShown(0) }
  }

  private fun eventually(check: () -> Unit) {
    try {
      composeTestRule.waitUntil(timeoutMillis = 5_000) { runCatching { check() }.isSuccess }
    } catch (_: Throwable) {
    }
    check()
  }

  private fun assertQuoteShown(index: Int) {
    composeTestRule.onNodeWithText("${index + 1}/12").assertExists()
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
    assertEquals("Next reflection loaded", viewModel.toastMessage.value)

    shuffle()
    eventually { assertQuoteShown(2) }
  }

  @Test
  fun `FP-112 quote shuffle button wraps around to the first quote after the last`() {
    launch()
    for (next in 1..11) {
      shuffle()
      eventually { assertQuoteShown(next) }
    }
    composeTestRule.onNodeWithText("12/12").assertExists()

    shuffle()

    eventually { assertQuoteShown(0) }
    composeTestRule.onNodeWithText("12/12").assertDoesNotExist()
  }

  @Test
  fun `FP-112 quote texts and shuffle toast in Vietnamese`() {
    language = AppLanguage.VI
    launch()

    for (next in 1..11) {
      shuffle()
      eventually { assertQuoteShown(next) }
    }
    assertEquals("Đã chuyển sang lời nhắn tiếp theo", viewModel.toastMessage.value)

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

  @Test
  fun `FP-112 quote send button toasts for the partner and starts the floating hearts`() {
    launch()
    val heartsBefore = viewModel.floatingHeartsTrigger.value
    assertNull(viewModel.toastMessage.value)

    composeTestRule.onNodeWithTag("btn_send_quote").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

    eventually { assertEquals("Sweet love reflection ready for Partner", viewModel.toastMessage.value) }
    assertNotEquals("floating hearts started", heartsBefore, viewModel.floatingHeartsTrigger.value)
    assertFalse("a plain shower, not a milestone one", viewModel.isMilestoneCelebration.value)
    assertQuoteShown(0)
  }
}
