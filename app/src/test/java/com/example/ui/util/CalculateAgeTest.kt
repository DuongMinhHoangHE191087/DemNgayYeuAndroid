package com.example.ui.util

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * calculateAge so (tháng, ngày) với ngày sinh thay vì ngày-trong-năm: từ 01/03 trở đi ngày thứ N của năm nhuận
 * và năm thường là hai ngày khác nhau, nên cách cũ lệch 1 tuổi ngay quanh sinh nhật.
 */
class CalculateAgeTest {
  private lateinit var previousLocale: Locale

  // Ghim tr-TR: kết quả không được phụ thuộc locale máy chạy.
  @Before fun pinLocale() {
    previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
  }

  @After fun restoreLocale() {
    Locale.setDefault(previousLocale)
  }

  // "Hôm nay" cố định lúc 12:00 để test tất định.
  private fun noon(year: Int, month: Int, day: Int): Long = GregorianCalendar(year, month, day, 12, 0, 0).timeInMillis

  // Ba ca này sai với cách so ngày-trong-năm cũ.
  @Test fun birthdayOnMarchFirst_afterALeapBirthYear_isCounted() {
    // Sinh 01/03/2000 (năm nhuận): đúng sinh nhật 01/03/2025 phải tròn 25 (cũ: 24).
    assertEquals(25, ProfileUtils.calculateAge("01/03/2000", noon(2025, Calendar.MARCH, 1)))
  }

  @Test fun leapDay_beforeAMarchFirstBirthday_isNotYetTheBirthday() {
    // Sinh 01/03/2001, hôm nay 29/02/2024: chưa tới sinh nhật nên 22 (cũ: 23).
    assertEquals(22, ProfileUtils.calculateAge("01/03/2001", noon(2024, Calendar.FEBRUARY, 29)))
  }

  @Test fun dayBeforeTheBirthday_inALeapYear_isStillYounger() {
    // Sinh 15/06/1990 (năm thường), hôm nay 14/06/2020 (năm nhuận): 29 (cũ: 30).
    assertEquals(29, ProfileUtils.calculateAge("15/06/1990", noon(2020, Calendar.JUNE, 14)))
  }

  // Các ca còn lại đúng với cả hai cách: chốt lại để lần sửa sau không làm hỏng.
  @Test fun leapDayBirthday_turnsOlderOnMarchFirstInANormalYear() {
    assertEquals(24, ProfileUtils.calculateAge("29/02/2000", noon(2025, Calendar.FEBRUARY, 28)))
    assertEquals(25, ProfileUtils.calculateAge("29/02/2000", noon(2025, Calendar.MARCH, 1)))
  }

  @Test fun onAndAfterTheBirthday_inALeapYear_isTheFullAge() {
    assertEquals(30, ProfileUtils.calculateAge("15/06/1990", noon(2020, Calendar.JUNE, 15)))
    assertEquals(30, ProfileUtils.calculateAge("15/06/1990", noon(2020, Calendar.JUNE, 16)))
  }

  @Test fun unparsableDate_fallsBackToTheYearOfTheGivenToday() {
    // Ngày không tồn tại thì rơi vào nhánh dự phòng: lấy năm của "hôm nay" truyền vào, không phải năm của máy.
    assertEquals(25, ProfileUtils.calculateAge("31/02/2000", noon(2025, Calendar.JUNE, 1)))
  }
}
