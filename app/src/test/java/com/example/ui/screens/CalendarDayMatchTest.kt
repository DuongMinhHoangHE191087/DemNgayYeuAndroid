package com.example.ui.screens

import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.MilestoneEntity
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kiểm tra ô lịch chỉ hiện dấu mốc đúng năm, trừ ngày kỷ niệm lặp lại hằng năm. */
class CalendarDayMatchTest {

  private fun cell(year: Int, month: Int, day: Int): Calendar = GregorianCalendar(year, month - 1, day)

  private fun milestone(date: String) = MilestoneEntity(
    title = "t",
    dateText = date,
    subtitle = "",
    categoryTag = "",
    secondaryTag = "",
    imageUrl = "",
    daysRemaining = 0
  )

  private fun anniversary(date: String, annual: Boolean) =
    AnniversaryDateEntity(title = "t", dateText = date, isAnnual = annual)

  // Cột mốc ghi dd/MM/yyyy chỉ hiện ở đúng năm của nó.
  @Test
  fun milestone_slashDate_fallsOnItsOwnYearOnly() {
    val m = milestone("10/03/2025")
    assertTrue(m.fallsOn(cell(2025, 3, 10)))
    assertFalse(m.fallsOn(cell(2026, 3, 10)))
  }

  // Cột mốc ghi yyyy-MM-dd chỉ hiện ở đúng năm của nó.
  @Test
  fun milestone_isoDate_fallsOnItsOwnYearOnly() {
    val m = milestone("2025-03-10")
    assertTrue(m.fallsOn(cell(2025, 3, 10)))
    assertFalse(m.fallsOn(cell(2026, 3, 10)))
  }

  // Ngày 1/1 chỉ hiện ở năm của cột mốc, không hiện ở ô 1/1 của năm trước.
  @Test
  fun milestone_firstOfJanuary_fallsOnItsYearNotThePreviousOne() {
    val m = milestone("01/01/2026")
    assertTrue(m.fallsOn(cell(2026, 1, 1)))
    assertFalse(m.fallsOn(cell(2025, 1, 1)))
  }

  // Ngày kỷ niệm lặp lại hằng năm hiện ở mọi năm, nhưng không sai ngày.
  @Test
  fun anniversary_annual_fallsOnEveryYear() {
    val a = anniversary("10/03/2025", annual = true)
    assertTrue(a.fallsOn(cell(2025, 3, 10)))
    assertTrue(a.fallsOn(cell(2026, 3, 10)))
    assertFalse(a.fallsOn(cell(2026, 3, 11)))
  }

  // Ngày kỷ niệm không lặp lại chỉ hiện ở đúng năm của nó.
  @Test
  fun anniversary_notAnnual_fallsOnItsOwnYearOnly() {
    val a = anniversary("10/03/2025", annual = false)
    assertTrue(a.fallsOn(cell(2025, 3, 10)))
    assertFalse(a.fallsOn(cell(2026, 3, 10)))
  }

  // Ngày không có năm thì vẫn khớp theo ngày và tháng như trước.
  @Test
  fun dateWithoutYear_keepsMonthDayMatch() {
    val m = milestone("10/03")
    val a = anniversary("10/03", annual = false)
    assertTrue(m.fallsOn(cell(2025, 3, 10)))
    assertTrue(m.fallsOn(cell(2026, 3, 10)))
    assertFalse(m.fallsOn(cell(2025, 3, 11)))
    assertTrue(a.fallsOn(cell(2025, 3, 10)))
    assertTrue(a.fallsOn(cell(2026, 3, 10)))
    assertFalse(a.fallsOn(cell(2025, 3, 11)))
  }
}
