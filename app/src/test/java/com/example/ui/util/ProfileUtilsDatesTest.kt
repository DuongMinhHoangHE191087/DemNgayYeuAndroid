package com.example.ui.util

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileUtilsDatesTest {

  /** 15/06/2024 12:00 giờ máy, cố định để test không phụ thuộc ngày chạy. */
  private val now: Long = Calendar.getInstance().apply {
    clear()
    set(2024, Calendar.JUNE, 15, 12, 0, 0)
  }.timeInMillis

  @Test
  fun `four patterns give the same value as calculateLoveDays`() {
    val expected = ProfileUtils.calculateLoveDays(ProfileUtils.parseDateToMillis("14/02/2023"))
    listOf("14/02/2023", "14-02-2023", "2023-02-14", "14/2/2023").forEach {
      assertEquals(it, expected, ProfileUtils.loveDaysOn(it))
    }
  }

  @Test
  fun `fixed now counts the start day as day 1`() {
    assertEquals(1, ProfileUtils.loveDaysOn("15/06/2024", now))
    assertEquals(2, ProfileUtils.loveDaysOn("14/06/2024", now))
    assertEquals(367, ProfileUtils.loveDaysOn("15/06/2023", now))
  }

  @Test
  fun `loveDaysOn gives null for bad or future input`() {
    listOf("", "   ", "abc", "31/02/2024", "14/02/23", "01/01/1899", "16/06/2024").forEach {
      assertNull(it, ProfileUtils.loveDaysOn(it, now))
    }
  }

  @Test
  fun `normalize returns dd-MM-yyyy`() {
    assertEquals("01/02/2020", ProfileUtils.normalizeAnniversaryInput("1/2/2020", now))
    assertEquals("01/02/2020", ProfileUtils.normalizeAnniversaryInput("2020-02-01", now))
    assertEquals("01/02/2020", ProfileUtils.normalizeAnniversaryInput(" 01-02-2020 ", now))
    assertEquals("15/06/2024", ProfileUtils.normalizeAnniversaryInput("15/06/2024", now))
    assertEquals("01/01/1900", ProfileUtils.normalizeAnniversaryInput("1/1/1900", now))
  }

  @Test
  fun `normalize gives null for bad or future input`() {
    listOf("", "abc", "31/02/2024", "14/02/23", "01/01/1899", "16/06/2024", "2024-06-16", "1/2/2020x").forEach {
      assertNull(it, ProfileUtils.normalizeAnniversaryInput(it, now))
    }
  }
}
