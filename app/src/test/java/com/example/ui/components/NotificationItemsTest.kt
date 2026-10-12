package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.alarm.LoveMilestoneInfo
import com.example.data.model.CustomReminderEntity
import com.example.ui.theme.AppDensity
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class NotificationItemsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun milestone(days: Int) = LoveMilestoneInfo(
    id = 1, title = "100 ngày", milestoneDays = 100, targetDateMillis = 0L,
    formattedDate = "01/01/2027", daysRemaining = days
  )

  private fun reminder(alarm: String) = CustomReminderEntity(
    title = "Hẹn hò", dateText = "02/02/2027", details = "", daysRemainingText = "", alarmTimeFormatted = alarm
  )

  @Test
  fun milestoneToday_isUrgent_withTodayText() {
    val item = buildNotificationItems(listOf(milestone(0)), emptyList(), isEnglish = false).single()
    assertEquals("Hôm nay", item.whenText)
    assertTrue(item.urgent)
  }

  @Test
  fun milestone5Days_notUrgent_textHasDate() {
    val item = buildNotificationItems(listOf(milestone(5)), emptyList(), isEnglish = false).single()
    assertEquals(false, item.urgent)
    assertEquals("5 ngày · 01/01/2027", item.whenText)
  }

  @Test
  fun reminder_blankTime_fallsBackToDateText() {
    val item = buildNotificationItems(emptyList(), listOf(reminder("")), isEnglish = false).single()
    assertEquals("02/02/2027", item.whenText)
  }

  @Test
  fun topNotification_prefersUrgentOverFirst() {
    val items = buildNotificationItems(listOf(milestone(2)), listOf(reminder("08:00")), isEnglish = false)
    val reordered = items.reversed() // reminder first, urgent milestone second
    assertEquals("Hẹn hò", reordered.first().title)
    assertEquals("100 ngày", topNotification(reordered)?.title)
  }

  @Test
  fun topNotification_empty_isNull() {
    assertNull(topNotification(emptyList()))
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Test
  fun sheetDensity_matchesApp() {
    var outer = 0f
    var raw = 0f
    var wrapped = 0f
    composeTestRule.setContent {
      MyApplicationTheme {
        outer = LocalDensity.current.density
        ModalBottomSheet(
          onDismissRequest = {},
          sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
          raw = LocalDensity.current.density
          AppDensity { wrapped = LocalDensity.current.density }
          Box {}
        }
      }
    }
    composeTestRule.waitForIdle()
    assertTrue("outer density not captured", outer > 0f && wrapped > 0f)
    // A12: the sheet window resets density to the unscaled value, so NotificationsSheet re-applies AppDensity.
    assertTrue("sheet no longer resets density; drop AppDensity wrap (raw=$raw outer=$outer)", raw != outer)
    assertEquals(outer, wrapped, 0.0001f)
  }
}
