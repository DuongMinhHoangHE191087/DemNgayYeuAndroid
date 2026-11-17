package com.example.golden

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Bản sao nguyên văn các hàm ngày của Android cũ (D-05a), làm oracle cho GV-001.
 * Dùng bản sao thay vì gọi mã thật để chế độ VERIFY không đổi khi P4 đổi hàm thật sang `CalendarMath`. Xoá ở D-35.
 * Nguồn: `ProfileUtils` (dateFormat, calculateLoveDays, formatDate, parseDateToMillis) và `DatePickerUtils`
 * (formatUtcMillisToDate, parseDateToUtcMillis) trong `Material3DatePicker.kt`.
 * Khác mã gốc ở hai chỗ: `nowMillis` tường minh (mã thật gọi `System.currentTimeMillis()`), và chế độ `CALENDAR`
 * (đếm ngày lịch bằng `java.time`, chỉ trong test) để so với `ELAPSED` (sao chép cũ).
 */
object LegacyDateOracle {
  enum class Mode { ELAPSED, CALENDAR }

  // ponytail: bản sao của ProfileUtils.dateFormat, giữ nguyên "new instance per access".
  private val dateFormat
    get() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
      isLenient = false
    }

  /** `ProfileUtils.calculateLoveDays(Long)`. CALENDAR: số ngày lịch (múi giờ máy) từ ngày bắt đầu đến hôm nay, tính cả hai đầu. */
  fun calculateLoveDays(startDateMillis: Long, nowMillis: Long, mode: Mode): Int {
    val now = nowMillis
    if (startDateMillis > now) return 0
    if (mode == Mode.CALENDAR) {
      val zone = TimeZone.getDefault().toZoneId()
      val start = Instant.ofEpochMilli(startDateMillis).atZone(zone).toLocalDate()
      val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
      return (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceAtLeast(1)
    }
    val diff = now - startDateMillis
    val days = TimeUnit.MILLISECONDS.toDays(diff).toInt()
    return (days + 1).coerceAtLeast(1)
  }

  /** `ProfileUtils.calculateLoveDays(String)`. */
  fun calculateLoveDays(startDateStr: String, nowMillis: Long, mode: Mode): Int {
    if (startDateStr.isBlank()) return 1
    return try {
      val date = dateFormat.parse(startDateStr.trim()) ?: return 1
      calculateLoveDays(date.time, nowMillis, mode)
    } catch (e: Exception) {
      1
    }
  }

  /** `ProfileUtils.formatDate`. */
  fun formatDate(millis: Long): String {
    return dateFormat.format(Date(millis))
  }

  /** `ProfileUtils.parseDateToMillis`; mã thật trả `System.currentTimeMillis()` khi lỗi, ở đây là `nowMillis`. */
  fun parseDateToMillis(dateStr: String, nowMillis: Long): Long {
    return try {
      dateFormat.parse(dateStr.trim())?.time ?: nowMillis
    } catch (e: Exception) {
      nowMillis
    }
  }

  /** `DatePickerUtils.formatUtcMillisToDate`. */
  fun formatUtcMillisToDate(utcMillis: Long, pattern: String = "dd/MM/yyyy"): String {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault()).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(utcMillis))
  }

  /** Ban sao `LoveTimeBreakdown` (LoveDaysCalculatorWidget.kt), giu rieng de khong trung ten voi lop that. */
  data class Breakdown(
    val totalDays: Long,
    val years: Int,
    val months: Int,
    val days: Int,
    val totalWeeks: Long,
    val remainingDaysInWeek: Long,
    val totalHours: Long,
    val totalMinutes: Long,
    val totalSeconds: Long,
    val nextMilestoneDays: Int,
    val daysUntilMilestone: Int,
    val milestoneProgress: Float
  )

  /**
   * Ban sao nguyen van `calculateDetailedLoveTime` (LoveDaysCalculatorWidget.kt), D-05b, oracle cho GV-003.
   * Chi khac goc: `nowMillis` tuong minh va tham so `mode`. ELAPSED = hanh vi cu (totalDays tu mili giay troi qua);
   * CALENDAR = totalDays la so ngay lich (gio may) giua ngay bat dau va ngay hien tai, cong mot. Cac khoa suy ra tu
   * totalDays duoc tinh lai bang cung cong thuc; years/months/days/totalSeconds giong nhau o hai che do.
   */
  fun calculateDetailedLoveTime(startDateStr: String, nowMillis: Long, mode: Mode): Breakdown {
    val formats = listOf("dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
    var startCal: Calendar? = null
    for (pattern in formats) {
      try {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.isLenient = false
        val date = sdf.parse(startDateStr.trim())
        if (date != null) {
          val cal = Calendar.getInstance()
          cal.time = date
          cal.set(Calendar.HOUR_OF_DAY, 0)
          cal.set(Calendar.MINUTE, 0)
          cal.set(Calendar.SECOND, 0)
          cal.set(Calendar.MILLISECOND, 0)
          startCal = cal
          break
        }
      } catch (_: Exception) {}
    }

    val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    if (startCal == null || startCal.after(nowCal)) {
      return Breakdown(
        totalDays = 0,
        years = 0,
        months = 0,
        days = 0,
        totalWeeks = 0,
        remainingDaysInWeek = 0,
        totalHours = 0,
        totalMinutes = 0,
        totalSeconds = 0,
        nextMilestoneDays = 100,
        daysUntilMilestone = 100,
        milestoneProgress = 0f
      )
    }

    val diffMillis = nowCal.timeInMillis - startCal.timeInMillis
    val totalDays = if (mode == Mode.CALENDAR) {
      val zone = TimeZone.getDefault().toZoneId()
      val start = Instant.ofEpochMilli(startCal.timeInMillis).atZone(zone).toLocalDate()
      val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
      ChronoUnit.DAYS.between(start, today) + 1
    } else {
      TimeUnit.MILLISECONDS.toDays(diffMillis) + 1
    }
    val totalWeeks = totalDays / 7
    val remainingDaysInWeek = totalDays % 7
    val totalHours = totalDays * 24
    val totalMinutes = totalHours * 60
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)

    var years = nowCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)
    var months = nowCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH)
    var days = nowCal.get(Calendar.DAY_OF_MONTH) - startCal.get(Calendar.DAY_OF_MONTH)

    if (days < 0) {
      months -= 1
      val prevMonthCal = nowCal.clone() as Calendar
      prevMonthCal.add(Calendar.MONTH, -1)
      days += prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    if (months < 0) {
      years -= 1
      months += 12
    }

    val nextMilestone = STANDARD_MILESTONES.firstOrNull { it > totalDays } ?: ((totalDays / 500 + 1) * 500).toInt()
    val daysUntilMilestone = (nextMilestone - totalDays).toInt().coerceAtLeast(0)
    val prevMilestone = STANDARD_MILESTONES.lastOrNull { it <= totalDays } ?: 0
    val milestoneProgress = if (nextMilestone > prevMilestone) {
      ((totalDays - prevMilestone).toFloat() / (nextMilestone - prevMilestone).toFloat()).coerceIn(0f, 1f)
    } else 1f

    return Breakdown(
      totalDays = totalDays,
      years = years.coerceAtLeast(0),
      months = months.coerceAtLeast(0),
      days = days.coerceAtLeast(0),
      totalWeeks = totalWeeks,
      remainingDaysInWeek = remainingDaysInWeek,
      totalHours = totalHours,
      totalMinutes = totalMinutes,
      totalSeconds = totalSeconds,
      nextMilestoneDays = nextMilestone,
      daysUntilMilestone = daysUntilMilestone,
      milestoneProgress = milestoneProgress
    )
  }

  /** Danh sach cot moc cua ban that (`standardMilestones`, khai bao cuc bo trong ham goc). */
  val STANDARD_MILESTONES = listOf(100, 200, 365, 500, 730, 1000, 1349, 1500, 1825, 2000, 2500, 3000, 3650, 5000)

  /** `DatePickerUtils.parseDateToUtcMillis`. */
  fun parseDateToUtcMillis(dateStr: String): Long? {
    val formats = listOf("dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
    for (fmt in formats) {
      try {
        val sdf = SimpleDateFormat(fmt, Locale.getDefault()).apply {
          timeZone = TimeZone.getTimeZone("UTC")
          isLenient = false
        }
        val parsed = sdf.parse(dateStr.trim())
        if (parsed != null) return parsed.time
      } catch (_: Exception) {}
    }
    return null
  }
}
