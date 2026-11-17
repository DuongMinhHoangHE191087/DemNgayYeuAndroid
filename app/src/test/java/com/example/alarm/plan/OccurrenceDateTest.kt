package com.example.alarm.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OccurrenceDateTest {

  private val zone = TimeZone.of("Asia/Ho_Chi_Minh")

  private fun now(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, second: Int = 0): Long =
    toEpochMillis(LocalDateTime(year, month, day, hour, minute, second), zone)

  private fun next(day: Int, month: Int, nowMillis: Long, year: Int? = null, annual: Boolean = true): LocalDate =
    nextOccurrence(day, month, year, annual, nowMillis, zone)

  @Test
  fun annualDateRollsForwardWhenItsDayHasPassed() {
    val at = now(2026, 10, 9, 8)
    assertEquals(LocalDate(2026, 10, 20), next(20, 10, at))
    assertEquals(LocalDate(2027, 10, 1), next(1, 10, at))
  }

  @Test
  fun sameDayBeforeNineStaysOnToday() {
    assertEquals(LocalDate(2026, 10, 9), next(9, 10, now(2026, 10, 9, 8)))
  }

  @Test
  fun sameDayAfterNineRollsToNextYear() {
    assertEquals(LocalDate(2027, 10, 9), next(9, 10, now(2026, 10, 9, 10)))
  }

  @Test
  fun exactlyNineSharpStaysOnToday() {
    // So sánh nghiêm ngặt: đúng 09:00:00.000 chưa tính là đã qua.
    assertEquals(LocalDate(2026, 10, 9), next(9, 10, now(2026, 10, 9, 9)))
    assertEquals(LocalDate(2027, 10, 9), next(9, 10, now(2026, 10, 9, 9, 0, 1)))
  }

  @Test
  fun leapDayFallsOnFebruary28InCommonYears() {
    assertEquals(LocalDate(2027, 2, 28), next(29, 2, now(2026, 10, 9, 8)))
  }

  @Test
  fun leapDayLandsOnFebruary29InLeapYears() {
    assertEquals(LocalDate(2028, 2, 29), next(29, 2, now(2027, 3, 1, 8)))
  }

  @Test
  fun dayThirtyOneClampsToThirtyInShorterMonths() {
    assertEquals(LocalDate(2027, 4, 30), next(31, 4, now(2026, 10, 9, 8)))
  }

  @Test
  fun oneTimeKeepsTheUsersYear() {
    val at = now(2026, 10, 9, 8)
    assertEquals(LocalDate(2024, 5, 1), next(1, 5, at, year = 2024, annual = false))
    assertEquals(LocalDate(2026, 5, 1), next(1, 5, at, year = null, annual = false))
  }

  @Test
  fun customHourMinuteDecidesTheRoll() {
    val at = now(2026, 10, 9, 20, 30)
    assertEquals(LocalDate(2026, 10, 9), nextOccurrence(9, 10, null, true, at, zone, 21, 0))
    assertEquals(LocalDate(2027, 10, 9), nextOccurrence(9, 10, null, true, at, zone, 20, 0))
  }

  @Test
  fun parsesVietnameseThangFormat() {
    assertEquals(ParsedDate(11, 9, 2026), parseDateText("11 Tháng 9, 2026"))
    assertEquals(ParsedDate(24, 10, 2026), parseDateText("24 Tháng 10, 2026"))
    assertEquals(ParsedDate(11, 9, null), parseDateText("11 Tháng 09"))
    assertEquals(ParsedDate(5, 1, null), parseDateText("5 thg 1"))
    assertEquals(ParsedDate(3, 2, 2027), parseDateText("3 tháng 2 2027"))
  }

  @Test
  fun parsesNumericFormats() {
    assertEquals(ParsedDate(11, 9, 2026), parseDateText("11/09/2026"))
    assertEquals(ParsedDate(11, 9, 2026), parseDateText("11-09-2026"))
    assertEquals(ParsedDate(11, 9, 2026), parseDateText("11.09.2026"))
    assertEquals(ParsedDate(11, 9, null), parseDateText("11/09"))
    assertEquals(ParsedDate(11, 9, 2026), parseDateText("2026-09-11"))
    assertEquals(ParsedDate(11, 9, 2026), parseDateText("  11/09/2026  "))
  }

  @Test
  fun isoDateIsCheckedAgainstTheRealCalendar() {
    assertEquals(ParsedDate(29, 2, 2028), parseDateText("2028-02-29"))
    assertNull(parseDateText("2027-02-29"))
    assertNull(parseDateText("2026-04-31"))
    assertEquals(ParsedDate(1, 9, 2026), parseDateText("2026-9-1"))
    // Đuôi sau ngày hợp lệ bị bỏ qua, như SimpleDateFormat.parse(String).
    assertEquals(ParsedDate(11, 9, 2026), parseDateText("2026-09-11T08:00"))
  }

  @Test
  fun rejectsGarbageDateText() {
    assertNull(parseDateText(""))
    assertNull(parseDateText("   "))
    assertNull(parseDateText("abc"))
    assertNull(parseDateText("32/01/2026"))
    assertNull(parseDateText("11/13/2026"))
    assertNull(parseDateText("00/05/2026"))
    assertNull(parseDateText("2026-02-30"))
  }

  @Test
  fun malformedTextThatOnlyTheOldSimpleDateFormatAcceptedIsRejected() {
    // Chủ đích chặt hơn bản cũ (đối chiếu 1,5 triệu chuỗi: mọi chênh lệch đều là "cũ đọc, mới null"):
    // app chỉ tạo dd/MM/yyyy nên các dạng dị dạng dưới đây không bao giờ xuất hiện từ bộ chọn ngày.
    assertNull(parseDateText("01/ 01/2026"))
    assertNull(parseDateText("011/1/2026"))
    assertNull(parseDateText("01/0001"))
    assertNull(parseDateText("32-1-26"))
  }

  @Test
  fun monthLengthsFollowTheCalendar() {
    assertEquals(LocalDate(2026, 1, 31), occurrenceDate(31, 1, 2026))
    assertEquals(LocalDate(2026, 9, 30), occurrenceDate(31, 9, 2026))
    assertEquals(LocalDate(2026, 11, 30), occurrenceDate(31, 11, 2026))
    assertEquals(LocalDate(2026, 12, 31), occurrenceDate(31, 12, 2026))
    assertEquals(LocalDate(2000, 2, 29), occurrenceDate(29, 2, 2000))
    assertEquals(LocalDate(1900, 2, 28), occurrenceDate(29, 2, 1900))
    assertEquals(ParsedDate(29, 2, 2000), parseDateText("2000-02-29"))
    assertNull(parseDateText("1900-02-29"))
  }

  @Test
  fun isoBoundsAreEnforced() {
    assertNull(parseDateText("2026-13-01"))
    assertNull(parseDateText("2026-00-10"))
    assertNull(parseDateText("2026-01-00"))
    assertNull(parseDateText("0000-01-01"))
    assertEquals(ParsedDate(31, 12, 2026), parseDateText("2026-12-31"))
  }

  @Test
  fun customMinuteDecidesTheRoll() {
    val at = now(2026, 10, 9, 20, 30)
    assertEquals(LocalDate(2026, 10, 9), nextOccurrence(9, 10, null, true, at, zone, 20, 45))
    assertEquals(LocalDate(2027, 10, 9), nextOccurrence(9, 10, null, true, at, zone, 20, 15))
  }

  @Test
  fun currentYearIsTakenInTheGivenZone() {
    // 2026-12-31 20:00 UTC đã là 2027-01-01 03:00 ở Hồ Chí Minh.
    assertEquals(LocalDate(2027, 6, 15), next(15, 6, now(2027, 1, 1, 3), year = null, annual = false))
  }
}