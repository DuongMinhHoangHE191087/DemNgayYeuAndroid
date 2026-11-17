package com.example.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/** Bộ đếm ngày yêu tính cả ngày bắt đầu (ngày 1), nên mốc ngày N rơi vào start + (N - 1) ngày. */
class LoveMilestoneDateTest {

  private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
  private lateinit var savedZone: TimeZone
  private lateinit var savedLocale: Locale

  @Before
  fun setUp() {
    savedZone = TimeZone.getDefault()
    savedLocale = Locale.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
    Locale.setDefault(Locale.US)
  }

  @After
  fun tearDown() {
    TimeZone.setDefault(savedZone)
    Locale.setDefault(savedLocale)
  }

  @Test
  fun hundredDayMilestoneFallsOnStartPlus99Days() {
    // Không có hook đồng hồ: lấy start = hôm nay - 10 ngày để mốc 30 và 100 ngày luôn ở tương lai.
    val start = LocalDate.now(zone).minusDays(10)
    val text = start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))

    val m100 = LoveAnniversaryMilestoneScheduler.getUpcomingMilestones(0L, text)
      .firstOrNull { !it.isAnnual && it.milestoneDays == 100 }
    assertNotNull("mốc 100 ngày phải có trong danh sách", m100)

    val actual = Instant.ofEpochMilli(m100!!.targetDateMillis).atZone(zone).toLocalDate()
    assertEquals(start.plusDays(99), actual)
  }
}
