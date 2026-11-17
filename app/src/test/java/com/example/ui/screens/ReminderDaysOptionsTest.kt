package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** Các mốc nhắc trước của hộp thoại thêm ngày kỷ niệm. */
class ReminderDaysOptionsTest {

  // Có thêm mốc 14 ngày; bốn mốc cũ giữ nguyên thứ tự.
  @Test
  fun reminderDaysOptions_includeFourteenDays() {
    assertEquals(listOf(0, 1, 3, 7, 14), REMINDER_DAYS_BEFORE_OPTIONS)
  }
}
