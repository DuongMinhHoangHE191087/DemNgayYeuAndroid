package com.example.alarm.plan

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

// Chỗ duy nhất dùng Instant: mọi đổi giờ địa phương <-> epoch đi qua đây.
// Giờ không tồn tại (nhảy mùa xuân) dịch tới trước đúng độ dài khoảng trống, giờ lặp (lùi mùa thu)
// lấy offset sớm hơn; kotlinx-datetime theo cùng quy tắc với ZonedDateTime.of của Java.

fun toEpochMillis(local: LocalDateTime, zone: TimeZone): Long =
  local.toInstant(zone).toEpochMilliseconds()

fun toLocalDateTime(epochMillis: Long, zone: TimeZone): LocalDateTime =
  Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
