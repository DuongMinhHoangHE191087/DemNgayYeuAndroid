package com.example.ui.components

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatePickerRulesTest {
  private val utc = TimeZone.getTimeZone("UTC")
  private val utcPlus7 = TimeZone.getTimeZone("GMT+07:00")

  /** month0 là 0..11; mọi mốc đều theo UTC để khớp cách ô ngày được dựng. */
  private fun utcMillis(year: Int, month0: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
    Calendar.getInstance(utc).apply {
      clear()
      set(year, month0, day, hour, minute, 0)
    }.timeInMillis

  private val dayMs = 24L * 60 * 60 * 1000

  // --- chuyển chế độ ---

  @Test
  fun headerTap_fromDay_goesToYear() {
    assertEquals(PickerMode.YEAR, DatePickerRules.onHeaderTap(PickerMode.DAY))
  }

  @Test
  fun headerTap_fromMonthAndYear_goesBackToDay() {
    assertEquals(PickerMode.DAY, DatePickerRules.onHeaderTap(PickerMode.MONTH))
    assertEquals(PickerMode.DAY, DatePickerRules.onHeaderTap(PickerMode.YEAR))
  }

  @Test
  fun afterYearPicked_goesToMonth_afterMonthPicked_goesToDay() {
    assertEquals(PickerMode.MONTH, DatePickerRules.afterYearPicked())
    assertEquals(PickerMode.DAY, DatePickerRules.afterMonthPicked())
  }

  // --- khoảng năm sinh ---

  @Test
  fun birthYearRange_startsAt1920_endsAtCurrentLocalYear() {
    val now = utcMillis(2026, 9, 21, 12)
    assertEquals(1920..2026, DatePickerRules.birthYearRange(now, utc))
  }

  @Test
  fun birthYearRange_usesLocalYear_atUtcPlus7YearEdge() {
    // 2026-12-31 18:00 UTC đã là 01/01/2027 ở UTC+7.
    val now = utcMillis(2026, 11, 31, 18)
    assertEquals(1920..2026, DatePickerRules.birthYearRange(now, utc))
    assertEquals(1920..2027, DatePickerRules.birthYearRange(now, utcPlus7))
  }

  @Test
  fun birthMinDay_is1920_01_01_utc() {
    assertEquals(utcMillis(1920, 0, 1), DatePickerRules.birthMinDay())
  }

  // --- hôm nay theo ngày UTC ---

  @Test
  fun todayAsUtcDay_at2330UtcPlus7_isThatLocalDayAtUtcMidnight() {
    // 23:30 ngày 21/10 ở UTC+7 = 16:30 UTC cùng ngày.
    val now = utcMillis(2026, 9, 21, 16, 30)
    assertEquals(utcMillis(2026, 9, 21), DatePickerRules.todayAsUtcDay(now, utcPlus7))
  }

  @Test
  fun todayAsUtcDay_localDayAheadOfUtc_usesLocalDay() {
    // 18:00 UTC ngày 21/10 = 01:00 ngày 22/10 ở UTC+7.
    val now = utcMillis(2026, 9, 21, 18)
    assertEquals(utcMillis(2026, 9, 22), DatePickerRules.todayAsUtcDay(now, utcPlus7))
    assertEquals(utcMillis(2026, 9, 21), DatePickerRules.todayAsUtcDay(now, utc))
  }

  // --- isSelectable ---

  private val min = utcMillis(1920, 0, 1)
  private val max = utcMillis(2026, 9, 21)

  @Test
  fun isSelectable_inclusiveAtMinAndMax() {
    assertTrue(DatePickerRules.isSelectable(min, min, max))
    assertTrue(DatePickerRules.isSelectable(max, min, max))
  }

  @Test
  fun isSelectable_rejectsOneDayOutside() {
    assertFalse(DatePickerRules.isSelectable(min - dayMs, min, max))
    assertFalse(DatePickerRules.isSelectable(max + dayMs, min, max))
  }

  @Test
  fun isSelectable_ignoresTimeOfDay() {
    // Giờ trong ngày không làm lệch kết quả ở cả hai đầu.
    assertTrue(DatePickerRules.isSelectable(min + 5 * 60 * 60 * 1000L, min + 13 * 60 * 60 * 1000L, max))
    assertTrue(DatePickerRules.isSelectable(max, min, max + 23 * 60 * 60 * 1000L))
    assertTrue(DatePickerRules.isSelectable(max + 23 * 60 * 60 * 1000L, min, max))
    assertFalse(DatePickerRules.isSelectable(min - 1L, min, max))
    assertFalse(DatePickerRules.isSelectable(max + dayMs + 1L, min, max))
  }

  @Test
  fun isSelectable_nullBoundsAreUnbounded() {
    assertTrue(DatePickerRules.isSelectable(min - 1000 * dayMs, null, null))
    assertTrue(DatePickerRules.isSelectable(max + 1000 * dayMs, null, null))
    assertTrue(DatePickerRules.isSelectable(min, min, null))
    assertFalse(DatePickerRules.isSelectable(min - dayMs, min, null))
    assertTrue(DatePickerRules.isSelectable(max, null, max))
    assertFalse(DatePickerRules.isSelectable(max + dayMs, null, max))
  }

  // --- clampInitial ---

  @Test
  fun clampInitial_belowMin_returnsMin() {
    assertEquals(min, DatePickerRules.clampInitial(min - 400 * dayMs, min, max))
  }

  @Test
  fun clampInitial_aboveMax_returnsMax() {
    assertEquals(max, DatePickerRules.clampInitial(max + 400 * dayMs + 5000L, min, max))
  }

  @Test
  fun clampInitial_inside_returnsStartOfUtcDay() {
    val inside = utcMillis(2004, 9, 15, 17, 45)
    assertEquals(utcMillis(2004, 9, 15), DatePickerRules.clampInitial(inside, min, max))
  }

  @Test
  fun clampInitial_noBounds_onlyTruncatesToDay() {
    val t = utcMillis(2004, 9, 15, 17, 45)
    assertEquals(utcMillis(2004, 9, 15), DatePickerRules.clampInitial(t, null, null))
  }

  // --- monthHasSelectableDay ---

  @Test
  fun monthHasSelectableDay_december1919_isFalse() {
    assertFalse(DatePickerRules.monthHasSelectableDay(1919, 11, min, max))
  }

  @Test
  fun monthHasSelectableDay_january1920_isTrue() {
    assertTrue(DatePickerRules.monthHasSelectableDay(1920, 0, min, max))
  }

  @Test
  fun monthHasSelectableDay_maxMonthTrue_nextMonthFalse() {
    assertTrue(DatePickerRules.monthHasSelectableDay(2026, 9, min, max))
    assertFalse(DatePickerRules.monthHasSelectableDay(2026, 10, min, max))
    assertFalse(DatePickerRules.monthHasSelectableDay(2027, 0, min, max))
  }

  @Test
  fun monthHasSelectableDay_maxOnFirstOfMonth_stillTrue() {
    val firstOfMonth = utcMillis(2026, 9, 1)
    assertTrue(DatePickerRules.monthHasSelectableDay(2026, 9, null, firstOfMonth))
    assertFalse(DatePickerRules.monthHasSelectableDay(2026, 8, firstOfMonth, null))
    assertTrue(DatePickerRules.monthHasSelectableDay(2026, 8, null, firstOfMonth))
  }

  @Test
  fun monthHasSelectableDay_noBounds_isTrue() {
    assertTrue(DatePickerRules.monthHasSelectableDay(1800, 0, null, null))
    assertTrue(DatePickerRules.monthHasSelectableDay(2100, 11, null, null))
  }
}
