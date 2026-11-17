package com.example.alarm.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

data class ParsedDate(val day: Int, val month: Int, val year: Int?)

private val slashRegex = Regex("""^(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{4}))?""")
private val vietnameseRegex = Regex("""(\d{1,2})\s+(?:Tháng|tháng|Thg|thg)\s+(\d{1,2})(?:[,\s]+(\d{4}))?""")

// Bản cũ còn nhánh SimpleDateFormat; các định dạng dùng "/" đã bị slashRegex bắt trước,
// nên chỉ còn "yyyy-MM-dd" tới được (parse theo tiền tố, ngày không có thật thì bị loại).
private val isoRegex = Regex("""^(\d{3,})-(\d+)-(\d+)""")

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
  2 -> if (isLeapYear(year)) 29 else 28
  4, 6, 9, 11 -> 30
  else -> 31
}

private fun MatchResult.toParsedOrNull(): ParsedDate? {
  val day = groupValues[1].toIntOrNull()
  val month = groupValues[2].toIntOrNull()
  val year = groupValues[3].toIntOrNull()
  return if (day != null && month != null && day in 1..31 && month in 1..12) ParsedDate(day, month, year) else null
}

/** Phân tích "dd/MM/yyyy", "dd-MM", "11 Tháng 9, 2026", "yyyy-MM-dd"... thành ngày, tháng (1-12), năm?. */
fun parseDateText(text: String): ParsedDate? {
  val clean = text.trim()

  slashRegex.find(clean)?.toParsedOrNull()?.let { return it }
  vietnameseRegex.find(clean)?.toParsedOrNull()?.let { return it }

  val iso = isoRegex.find(clean) ?: return null
  val year = iso.groupValues[1].toIntOrNull()
  val month = iso.groupValues[2].toIntOrNull()
  val day = iso.groupValues[3].toIntOrNull()
  if (year == null || month == null || day == null || year < 1 || month !in 1..12) return null
  return if (day in 1..lengthOfMonth(year, month)) ParsedDate(day, month, year) else null
}

/** Ngày không có trong tháng (29/2 năm thường, 31/4) rơi vào ngày cuối tháng. */
fun occurrenceDate(day: Int, month: Int, year: Int): LocalDate =
  LocalDate(year, month, minOf(day, lengthOfMonth(year, month)))

/**
 * Ngày của lần nhắc kế tiếp. Ngày hằng năm sang năm sau khi mốc giờ hôm nay đã qua
 * (so nghiêm ngặt: đúng hourOfDay:minute vẫn ở lại hôm nay); ngày một lần giữ năm của người dùng.
 */
fun nextOccurrence(
  day: Int,
  month: Int,
  year: Int?,
  isAnnual: Boolean,
  nowEpochMillis: Long,
  zone: TimeZone,
  hourOfDay: Int = 9,
  minute: Int = 0
): LocalDate {
  val currentYear = toLocalDateTime(nowEpochMillis, zone).year
  if (!isAnnual) return occurrenceDate(day, month, year ?: currentYear)
  val thisYear = occurrenceDate(day, month, currentYear)
  val fireAt = toEpochMillis(LocalDateTime(thisYear, LocalTime(hourOfDay, minute)), zone)
  return if (fireAt < nowEpochMillis) occurrenceDate(day, month, currentYear + 1) else thisYear
}
