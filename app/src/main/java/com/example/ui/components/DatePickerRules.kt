package com.example.ui.components

import com.example.ui.util.Birthday
import java.util.Calendar
import java.util.TimeZone

internal enum class PickerMode { DAY, MONTH, YEAR }

/** Luật thuần của bộ chọn ngày (không Android/Compose) để test JVM được. Mọi so sánh theo NGÀY UTC, giờ trong ngày bị bỏ qua. */
internal object DatePickerRules {
  private const val DAY_MS = 24L * 60 * 60 * 1000
  private val utc: TimeZone = TimeZone.getTimeZone("UTC")

  fun onHeaderTap(m: PickerMode) = if (m == PickerMode.DAY) PickerMode.YEAR else PickerMode.DAY
  fun afterYearPicked() = PickerMode.MONTH
  fun afterMonthPicked() = PickerMode.DAY

  /** 1920..năm hiện tại theo múi giờ máy. */
  fun birthYearRange(
    nowMillis: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
  ): IntRange {
    val year = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }.get(Calendar.YEAR)
    return Birthday.MIN_YEAR..year
  }

  /** 1920-01-01 00:00 UTC. */
  fun birthMinDay(): Long = utcDay(Birthday.MIN_YEAR, 0, 1)

  /** Ngày/tháng/năm theo giờ máy, đặt lại về 00:00 UTC: cùng cách ô ngày được dựng. */
  fun todayAsUtcDay(nowMillis: Long, zone: TimeZone): Long {
    val local = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
    return utcDay(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
  }

  /** Hai đầu đều được chọn; null = không giới hạn. */
  fun isSelectable(dayUtcMillis: Long, minMillis: Long?, maxMillis: Long?): Boolean {
    val day = startOfDay(dayUtcMillis)
    return (minMillis == null || day >= startOfDay(minMillis)) &&
      (maxMillis == null || day <= startOfDay(maxMillis))
  }

  /** Đầu ngày UTC của [millis], kéo vào [min, max]. */
  fun clampInitial(millis: Long, minMillis: Long?, maxMillis: Long?): Long {
    var day = startOfDay(millis)
    if (maxMillis != null) day = minOf(day, startOfDay(maxMillis))
    if (minMillis != null) day = maxOf(day, startOfDay(minMillis))
    return day
  }

  /** [month] 0..11. True nếu tháng đó còn ít nhất một ngày chọn được. */
  fun monthHasSelectableDay(year: Int, month: Int, minMillis: Long?, maxMillis: Long?): Boolean {
    val first = utcDay(year, month, 1)
    val last = utcDay(year, month + 1, 1) - DAY_MS // Calendar tự cuộn tháng 12 sang năm sau
    return (minMillis == null || last >= startOfDay(minMillis)) &&
      (maxMillis == null || first <= startOfDay(maxMillis))
  }

  private fun startOfDay(millis: Long): Long = Math.floorDiv(millis, DAY_MS) * DAY_MS

  private fun utcDay(year: Int, month: Int, day: Int): Long =
    Calendar.getInstance(utc).apply {
      clear()
      set(year, month, day)
    }.timeInMillis
}
