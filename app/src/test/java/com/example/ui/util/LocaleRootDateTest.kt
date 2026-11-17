package com.example.ui.util

import com.example.alarm.LoveAnniversaryMilestoneScheduler
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocaleRootDateTest {
  private lateinit var previousLocale: Locale
  private lateinit var previousZone: TimeZone

  @Before fun pinThaiLocale() {
    previousLocale = Locale.getDefault()
    previousZone = TimeZone.getDefault()
    Locale.setDefault(Locale.forLanguageTag("th-TH"))
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
  }

  @After fun restore() {
    Locale.setDefault(previousLocale)
    TimeZone.setDefault(previousZone)
  }

  private fun localMillis(): Long =
    LocalDate.of(2026, 3, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

  @Test fun formatDateIsGregorianUnderThai() {
    assertEquals("15/03/2026", ProfileUtils.formatDate(localMillis()))
  }

  @Test fun parseDateIsGregorianUnderThai() {
    assertEquals(localMillis(), ProfileUtils.parseDateToMillis("15/03/2026"))
  }

  @Test fun milestoneStartDateIsGregorianUnderThai() {
    assertEquals(localMillis(), LoveAnniversaryMilestoneScheduler.parseStartDateMillis(0, "15/03/2026"))
  }
}
