package com.example.ui.util

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** T22: Birthday là cổng chuẩn hoá duy nhất; age/zodiac gọi thẳng hàm golden của ProfileUtils. */
class BirthdayTest {
  private lateinit var previousLocale: Locale

  // Ghim tr-TR: kết quả không được phụ thuộc locale máy chạy.
  @Before fun pinLocale() {
    previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
  }

  @After fun restoreLocale() {
    Locale.setDefault(previousLocale)
  }

  // Cố định "hôm nay" = 2026-10-22 12:00 để test tất định.
  private fun now(): Long = GregorianCalendar(2026, Calendar.OCTOBER, 22, 12, 0, 0).timeInMillis

  private fun textOf(raw: String): String? = Birthday.parse(raw, now())?.text

  @Test fun acceptedFormsNormalizeToDdMmYyyy() {
    assertEquals("15/08/2000", textOf("15/08/2000"))
    assertEquals("05/08/2000", textOf("5/8/2000"))
    assertEquals("05/08/2000", textOf("2000-8-5"))
    assertEquals("15/08/2000", textOf("15-08-2000"))
  }

  @Test fun nonexistentDateIsRejected() {
    assertNull(Birthday.parse("31/02/2000", now()))
    assertEquals("", Birthday.normalize("31/02/2000", now()))
  }

  @Test fun futureDateIsRejected() {
    assertNull(Birthday.parse("23/10/2026", now()))
    assertNull(Birthday.parse("01/01/2030", now()))
    assertEquals("", Birthday.normalize("01/01/2030", now()))
    // Đúng hôm nay vẫn hợp lệ (so theo ngày).
    assertEquals("22/10/2026", textOf("22/10/2026"))
  }

  @Test fun savedYear1910IsKept() {
    assertEquals("05/05/1910", textOf("05/05/1910"))
    assertEquals("05/05/1910", Birthday.normalize("05/05/1910", now()))
  }

  @Test fun blankGivesNullAndEmpty() {
    assertNull(Birthday.parse("", now()))
    assertNull(Birthday.parse("   ", now()))
    assertEquals("", Birthday.normalize("", now()))
    assertEquals("", Birthday.normalize("   ", now()))
  }

  @Test fun surroundingWhitespaceIsTrimmed() {
    assertEquals("15/08/2000", textOf(" 15/08/2000 "))
    assertEquals("15/08/2000", Birthday.normalize("\t15/08/2000\n", now()))
  }

  @Test fun ageAndZodiacEqualGoldenFunctions() {
    for (raw in listOf("15/08/2000", "5/8/2000", "05/05/1910", "29/02/2000")) {
      val b = Birthday.parse(raw, now())
      assertNotNull(raw, b)
      assertEquals(raw, ProfileUtils.calculateAge(b!!.text), b.age)
      assertEquals(raw, ProfileUtils.calculateZodiac(b.text).first, b.zodiac)
    }
  }

  @Test fun minYearIs1920() {
    assertEquals(1920, Birthday.MIN_YEAR)
  }
}
