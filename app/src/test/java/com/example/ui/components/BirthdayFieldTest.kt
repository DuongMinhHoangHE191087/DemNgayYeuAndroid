package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class BirthdayFieldTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val changes = mutableListOf<String>()

  private fun show(value: String = "15/10/2004") {
    composeTestRule.setContent {
      MyApplicationTheme {
        Box {
          BirthdayField(
            label = "Ngày sinh",
            value = value,
            onValueChange = { changes += it },
            testTag = "bf",
          )
        }
      }
    }
  }

  private fun openViaCalendarButton() {
    composeTestRule.onNodeWithTag("bf_calendar_btn", useUnmergedTree = true).performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun showsTheCanonicalText() {
    show()
    composeTestRule.onNodeWithTag("bf").assertTextContains("15/10/2004")
  }

  @Test
  fun typingIsImpossible() {
    show()
    composeTestRule.onNodeWithTag("bf").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
    assertTrue(changes.isEmpty())
    composeTestRule.onNodeWithTag("bf").assertTextContains("15/10/2004")
  }

  @Test
  fun tappingTheFieldOpensThePicker() {
    show()
    composeTestRule.onNodeWithTag("bf").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("btn_select_year_month").assertExists()
  }

  @Test
  fun calendarButtonOpensThePicker() {
    show()
    openViaCalendarButton()
    composeTestRule.onNodeWithTag("btn_select_year_month").assertExists()
  }

  private fun clearButton() = composeTestRule.onNodeWithTag("bf_clear_btn", useUnmergedTree = true)

  @Test
  fun clearButtonIsHiddenWhenBlank() {
    show(value = "")
    clearButton().assertDoesNotExist()
  }

  @Test
  fun clearButtonIsShownWhenFilled() {
    show()
    clearButton().assertExists()
  }

  @Test
  fun clearButtonIsAtLeastATouchTarget() {
    show()
    clearButton().assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun clearingReportsAnEmptyValue() {
    show()
    clearButton().performTouchInput { click() }
    composeTestRule.waitForIdle()
    assertEquals(listOf(""), changes)
  }

  @Test
  fun clearingDoesNotOpenThePicker() {
    show()
    clearButton().performTouchInput { click() }
    composeTestRule.waitForIdle()
    assertEquals(listOf(""), changes)
    composeTestRule.onNodeWithTag("btn_select_year_month").assertDoesNotExist()
  }

  @Test
  fun helperShowsAgeAndZodiac() {
    show()
    composeTestRule.onNodeWithText("tuổi", substring = true, useUnmergedTree = true).assertTextContains("•", substring = true)
  }

  @Test
  fun yearGridIsBoundedToBirthYears() {
    show()
    openViaCalendarButton()
    composeTestRule.onNodeWithTag("btn_select_year_month").performClick()
    composeTestRule.waitForIdle()
    val now = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    // Index jumps (the grid starts at 1920): performScrollToNode would animate viewport by viewport and time out.
    composeTestRule.onNodeWithTag("year_grid").performScrollToIndex(0)
    composeTestRule.onNodeWithTag("year_item_1920").assertExists()
    composeTestRule.onNodeWithTag("year_item_1919").assertDoesNotExist()
    composeTestRule.onNodeWithTag("year_grid").performScrollToIndex(now - 1920)
    composeTestRule.onNodeWithTag("year_item_$now").assertExists()
    composeTestRule.onNodeWithTag("year_item_${now + 1}").assertDoesNotExist()
    assertEquals(0, changes.size)
  }
}
