package com.example.ui.screens

// T11: the birthday of MY profile is picked from the calendar only (1920..today), always saved as dd/MM/yyyy.
// EditMyProfileDialogContent is composed directly (no Dialog window); the date picker opens as its own window, which does work under Robolectric.
// The year grid is jumped with performScrollToIndex (performScrollToNode would walk it one animated viewport at a time and time out).
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.data.model.OnlineUserEntity
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class MyProfileBirthdayTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private var savedBirth: String? = null
  private var savedClear: Boolean? = null

  private fun show(birthDate: String = "") {
    composeTestRule.setContent {
      MyApplicationTheme {
        EditMyProfileDialogContent(
          currentUser = OnlineUserEntity(uid = "me", coupleCode = "", displayName = "Smoke A", isProfileSetup = true, birthDate = birthDate),
          isEnglish = true,
          onDismiss = {},
          onSave = { _, birth, _, _, _, clear -> savedBirth = birth; savedClear = clear }
        )
      }
    }
  }

  private fun save() {
    composeTestRule.onNodeWithTag("btn_save_my_profile").performScrollTo().assertIsEnabled().performClick()
  }

  @Test
  fun `tapping the birthday field opens the calendar`() {
    show()

    composeTestRule.onNodeWithTag("input_my_profile_birth").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("btn_select_year_month").assertExists()
  }

  @Test
  fun `typing cannot change the birthday`() {
    show()

    // A picker-only field has no SetText action, so the input itself throws; either way the saved value must stay blank.
    runCatching { composeTestRule.onNodeWithTag("input_my_profile_birth").performScrollTo().performTextInput("12/12/2012") }
    save()

    assertEquals("", savedBirth)
  }

  @Test
  fun `year then month then day then Confirm is what Save hands over`() {
    show()

    composeTestRule.onNodeWithTag("input_my_profile_birth").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_select_year_month").performClick()
    composeTestRule.onNodeWithTag("year_grid").performScrollToIndex(2001 - 1920)
    composeTestRule.onNodeWithTag("year_item_2001").performClick()
    composeTestRule.onNodeWithTag("month_item_5").performClick()
    composeTestRule.onNodeWithTag("cell_day_9").performClick()
    composeTestRule.onNodeWithTag("date_picker_confirm_btn").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("input_my_profile_birth").assertTextContains("09/06/2001")
    save()

    assertEquals("09/06/2001", savedBirth)
  }

  @Test
  fun `the year grid offers 1920 up to this year only`() {
    show()

    composeTestRule.onNodeWithTag("input_my_profile_birth_calendar_btn", useUnmergedTree = true).performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_select_year_month").performClick()
    composeTestRule.waitForIdle()
    val now = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    composeTestRule.onNodeWithTag("year_grid").performScrollToIndex(0)
    composeTestRule.onNodeWithTag("year_item_1920").assertExists()
    composeTestRule.onNodeWithTag("year_item_1919").assertDoesNotExist()
    composeTestRule.onNodeWithTag("year_grid").performScrollToIndex(now - 1920)
    composeTestRule.onNodeWithTag("year_item_$now").assertExists()
    composeTestRule.onNodeWithTag("year_item_${now + 1}").assertDoesNotExist()
  }

  @Test
  fun `an unreadable stored birthday is shown empty and saved blank`() {
    show(birthDate = "garbage")

    save()

    assertEquals("", savedBirth)
  }

  @Test
  fun `clearing a stored birthday with the X saves blank and asks to clear`() {
    show(birthDate = "15/10/2004")

    composeTestRule.onNodeWithTag("input_my_profile_birth_clear_btn", useUnmergedTree = true)
      .performScrollTo()
      .performTouchInput { click() }
    composeTestRule.waitForIdle()
    save()

    assertEquals("", savedBirth)
    assertEquals(true, savedClear)
  }

  @Test
  fun `an unreadable stored birthday opens blank and does not ask to clear`() {
    show(birthDate = "garbage")

    save()

    assertEquals("", savedBirth)
    assertEquals(false, savedClear)
  }

  @Test
  fun `the Save button is a full-size touch target`() {
    show()

    // AppDensity scales the content by 0.85, so MinTouchBox (57.dp) renders at about 48.45.dp in the unscaled test root.
    composeTestRule.onNodeWithTag("btn_save_my_profile").performScrollTo().assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun `a short stored birthday is shown and saved as dd MM yyyy`() {
    show(birthDate = "5/5/2004")

    composeTestRule.onNodeWithTag("input_my_profile_birth").performScrollTo().assertTextContains("05/05/2004")
    save()

    assertEquals("05/05/2004", savedBirth)
  }

  @Test
  fun `a valid birthday before 1920 is kept as it is`() {
    show(birthDate = "05/05/1910")

    composeTestRule.onNodeWithTag("input_my_profile_birth").performScrollTo().assertTextContains("05/05/1910")
    save()

    assertEquals("05/05/1910", savedBirth)
  }
}
