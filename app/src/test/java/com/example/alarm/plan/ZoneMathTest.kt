package com.example.alarm.plan

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneMathTest {

  private val hcm = TimeZone.of("Asia/Ho_Chi_Minh")
  private val newYork = TimeZone.of("America/New_York")

  private fun utcMillis(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

  @Test
  fun nineAmFollowsTheZoneWhenItChanges() {
    val nineAm = LocalDateTime(2026, 10, 20, 9, 0)
    assertEquals(utcMillis("2026-10-20T02:00:00Z"), toEpochMillis(nineAm, hcm))
    assertEquals(utcMillis("2026-10-20T13:00:00Z"), toEpochMillis(nineAm, newYork))
  }

  @Test
  fun epochRoundTripsThroughLocalTime() {
    val epoch = utcMillis("2026-10-20T02:00:00.123Z")
    val local = toLocalDateTime(epoch, hcm)
    assertEquals(LocalDateTime(2026, 10, 20, 9, 0, 0, 123_000_000), local)
    assertEquals(epoch, toEpochMillis(local, hcm))
  }

  @Test
  fun springForwardGapShiftsByTheGapLength() {
    // 02:30 không tồn tại ở New York ngày 2026-03-08, dịch tới 03:30 EDT (07:30Z).
    val gap = LocalDateTime(2026, 3, 8, 2, 30)
    assertEquals(utcMillis("2026-03-08T07:30:00Z"), toEpochMillis(gap, newYork))
  }

  @Test
  fun fallBackOverlapTakesTheEarlierOffset() {
    // 01:30 lặp hai lần ngày 2026-11-01; lấy lần đầu (EDT, 05:30Z).
    val overlap = LocalDateTime(2026, 11, 1, 1, 30)
    assertEquals(utcMillis("2026-11-01T05:30:00Z"), toEpochMillis(overlap, newYork))
    assertEquals(overlap, toLocalDateTime(utcMillis("2026-11-01T06:30:00Z"), newYork))
  }
}
