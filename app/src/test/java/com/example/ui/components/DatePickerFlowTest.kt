package com.example.ui.components

// T10: the date dialog's year -> month -> day flow and its bounds, composed directly (no text field underneath).
// Only day numbers 8..22 are queried: the grid is Monday-first with 5 or 6 weeks, so the filler cells repeat the other numbers.
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.time.LocalDate
import java.time.ZoneOffset
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
class DatePickerFlowTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private var picked: String? = null

  private val maxDay: Long = LocalDate.of(2026, 10, 21).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

  private fun launchBounded(initialDateStr: String?) {
    composeTestRule.setContent {
      MyApplicationTheme {
        InLoveDatePickerDialog(
          initialDateStr = initialDateStr,
          yearRange = 1920..2026,
          minMillis = DatePickerRules.birthMinDay(),
          maxDateMillis = maxDay,
          onDateSelected = { _, formatted -> picked = formatted },
          onDismiss = {}
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun launchUnbounded(initialDateStr: String?) {
    composeTestRule.setContent {
      MyApplicationTheme {
        InLoveDatePickerDialog(
          initialDateStr = initialDateStr,
          onDateSelected = { _, formatted -> picked = formatted },
          onDismiss = {}
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun openYearGrid() {
    composeTestRule.onNodeWithTag("btn_select_year_month").performClick()
  }

  // performScrollToNode walks the lazy grid one animated viewport at a time (about a minute for a far year under
  // Robolectric NATIVE graphics, past Espresso's 60 s idle limit); performScrollToIndex jumps straight to the item.
  private fun scrollYearGridTo(year: Int, firstYear: Int = 1920) {
    composeTestRule.onNodeWithTag("year_grid").performScrollToIndex(year - firstYear)
  }

  @Test
  fun `year then month then day hands the picked date to the callback`() {
    launchBounded("15/10/2004")

    openYearGrid()
    scrollYearGridTo(2001)
    composeTestRule.onNodeWithTag("year_item_2001").performClick()
    composeTestRule.onNodeWithTag("month_item_0").assertExists()
    composeTestRule.onNodeWithTag("month_item_5").performClick()
    composeTestRule.onNodeWithTag("cell_day_9").performClick()
    composeTestRule.onNodeWithTag("date_picker_confirm_btn").performScrollTo().performClick()

    assertEquals("09/06/2001", picked)
  }

  @Test
  fun `year grid stops at the range ends`() {
    launchBounded("15/10/2004")

    openYearGrid()
    scrollYearGridTo(1920)
    composeTestRule.onNodeWithTag("year_item_1920").assertExists()
    composeTestRule.onNodeWithTag("year_item_1919").assertDoesNotExist()
    scrollYearGridTo(2026)
    composeTestRule.onNodeWithTag("year_item_2026").assertExists()
    composeTestRule.onNodeWithTag("year_item_2027").assertDoesNotExist()
  }

  @Test
  fun `day after the max date is not selectable`() {
    launchBounded("21/10/2026")

    composeTestRule.onNodeWithTag("cell_day_21").assertIsEnabled()
    composeTestRule.onNodeWithTag("cell_day_22").assertIsNotEnabled()
  }

  @Test
  fun `previous month arrow is disabled before the min date`() {
    launchBounded("15/01/1920")

    composeTestRule.onNodeWithTag("btn_calendar_prev").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("btn_calendar_next").assertIsEnabled()
  }

  @Test
  fun `blank initial date opens on a selectable day with Confirm enabled`() {
    launchBounded("")

    composeTestRule.onNodeWithTag("date_picker_confirm_btn").assertIsEnabled()
  }

  @Test
  fun `month after the max date is not selectable in the month grid`() {
    launchBounded("21/10/2026")

    openYearGrid()
    scrollYearGridTo(2026)
    composeTestRule.onNodeWithTag("year_item_2026").performClick()
    composeTestRule.onNodeWithTag("month_item_9").assertIsEnabled()
    composeTestRule.onNodeWithTag("month_item_10").assertIsNotEnabled()
  }

  @Test
  fun `unbounded dialog keeps the 1950 to 2040 year grid and an enabled Confirm`() {
    launchUnbounded("15/10/2004")

    composeTestRule.onNodeWithTag("date_picker_confirm_btn").assertIsEnabled()
    openYearGrid()
    scrollYearGridTo(1950, firstYear = 1950)
    composeTestRule.onNodeWithTag("year_item_1950").assertExists()
    scrollYearGridTo(2040, firstYear = 1950)
    composeTestRule.onNodeWithTag("year_item_2040").assertExists()
  }
}
