package com.example.data.seed

import com.example.data.db.InLoveDao
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dates for lunar and rule-based holidays. Each row holds one occurrence (isAnnual = false), so a
 * year-specific date is never repeated by day and month.
 */
object HolidayDates {

  private val rowDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  /** Earliest verified date on or after [today]; null when no verified year still lies ahead. */
  fun nextLunar(holiday: VietnameseHolidays.LunarHoliday, today: LocalDate): LocalDate? =
    holiday.datesByYear.values.filter { !it.isBefore(today) }.minByOrNull { it.toEpochDay() }

  /** This year's occurrence while it is still ahead, otherwise next year's. */
  fun nextRuleBased(holiday: WesternHolidays.RuleBasedHoliday, today: LocalDate): LocalDate {
    val thisYear = holiday.resolve(today.year)
    return if (thisYear.isBefore(today)) holiday.resolve(today.year + 1) else thisYear
  }

  /**
   * Moves stored lunar and rule-based rows to their next occurrence. Every live row is matched by its VI or EN
   * title. A lunar holiday with no verified date ahead keeps its text but stops repeating (isAnnual = false),
   * so an old date is never shown again as if it were new. Writes only when a value differs.
   */
  suspend fun refreshExisting(dao: InLoveDao, today: LocalDate) {
    for (row in dao.getAnniversaryDatesList()) {
      val lunar = VietnameseHolidays.lunarHolidays.firstOrNull {
        row.title == "${it.emoji} ${it.titleVi}" || row.title == "${it.emoji} ${it.titleEn}"
      }
      if (lunar != null) {
        val next = nextLunar(lunar, today)
        val updated = if (next == null) row.copy(isAnnual = false)
        else row.copy(dateText = next.format(rowDate), isAnnual = false)
        if (updated != row) dao.updateAnniversaryDate(updated)
        continue
      }
      val ruleBased = WesternHolidays.ruleBasedHolidays.firstOrNull {
        row.title == "${it.emoji} ${it.titleVi}" || row.title == "${it.emoji} ${it.titleEn}"
      } ?: continue
      val updated = row.copy(dateText = nextRuleBased(ruleBased, today).format(rowDate), isAnnual = false)
      if (updated != row) dao.updateAnniversaryDate(updated)
    }
  }
}
