package com.example.alarm.plan

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

object LoveMilestoneRules {
  private val NINE_AM = LocalTime(9, 0)
  private val EIGHT_THIRTY = LocalTime(8, 30)
  private const val ROUTE_CALENDAR = "calendar"
  private const val ROUTE_HOME = "home"

  // Mã cột mốc hằng năm là ANNUAL_ID_BASE + số năm yêu nhau, không đụng mã cột mốc theo ngày (30..2000).
  private const val ANNUAL_ID_BASE = 10000L
  private const val ANNUAL_YEARS_AHEAD = 4

  // Bảng mốc theo số ngày dùng chung: LoveAnniversaryMilestoneScheduler.STANDARD_DAY_MILESTONES lấy tên tiếng Việt từ đây; tiếng Anh dịch mới.
  internal class DayMilestone(val days: Int, val vi: String, val en: String)

  internal val DAY_MILESTONES = listOf(
    DayMilestone(30, "🌱 1 Tháng Yêu Nhau (30 Ngày)", "🌱 1 Month in Love (30 Days)"),
    DayMilestone(50, "🌸 50 Ngày Gắn Kết", "🌸 50 Days Together"),
    DayMilestone(100, "💎 Bách Nhật Yêu (100 Ngày)", "💎 100 Days in Love"),
    DayMilestone(150, "🎀 150 Ngày Đồng Điệu", "🎀 150 Days in Harmony"),
    DayMilestone(200, "🌹 200 Ngày Nồng Nàn", "🌹 200 Days of Passion"),
    DayMilestone(300, "✨ 300 Ngày Chung Đôi", "✨ 300 Days as a Couple"),
    DayMilestone(365, "🎂 Tròn 1 Năm Yêu (365 Ngày)", "🎂 1 Year in Love (365 Days)"),
    DayMilestone(500, "🌿 500 Ngày Tri Kỷ", "🌿 500 Days of Soulmates"),
    DayMilestone(730, "💍 Tròn 2 Năm Yêu (730 Ngày)", "💍 2 Years in Love (730 Days)"),
    DayMilestone(1000, "👑 1000 Ngày Trọn Vẹn", "👑 1000 Days of Fulfillment"),
    DayMilestone(1500, "🏆 1500 Ngày Vững Bền", "🏆 1500 Days Strong"),
    DayMilestone(2000, "🌟 2000 Ngày Hạnh Phúc", "🌟 2000 Days of Happiness")
  )

  /** Luật I5, I6, I7 của kế hoạch p5 mục 4.1.5; không sắp xếp, không cắt, không che chữ. */
  fun plan(input: ReminderPlanInput, nowMillis: Long, zone: TimeZone): List<PlannedNotification> {
    val couple = input.couple ?: return emptyList()
    if (!input.anniversaryNotificationsEnabled) return emptyList()
    val parsed = parseDateText(couple.startDate) ?: return emptyList()
    val startYear = parsed.year ?: return emptyList()
    // Ngày không có thật (31/04) rơi vào cuối tháng, như mọi chỗ khác của kế hoạch.
    val start = occurrenceDate(parsed.day, parsed.month, startYear)
    val currentYear = toLocalDateTime(nowMillis, zone).year
    val out = mutableListOf<PlannedNotification>()

    // Bộ đếm ngày yêu tính cả ngày bắt đầu (ngày 1), nên ngày thứ N rơi vào start + (N - 1) ngày.
    for (m in DAY_MILESTONES) {
      val name = if (input.isEnglish) m.en else m.vi
      alerts(m.days.toLong(), start.plus(m.days - 1, DateTimeUnit.DAY), name, input, couple, nowMillis, zone, out)
    }

    for (year in currentYear..currentYear + ANNUAL_YEARS_AHEAD) {
      val yearsInLove = year - startYear
      if (yearsInLove <= 0) continue
      val name = annualName(yearsInLove, input.isEnglish)
      val date = occurrenceDate(parsed.day, parsed.month, year)
      alerts(ANNUAL_ID_BASE + yearsInLove, date, name, input, couple, nowMillis, zone, out)
    }
    return out
  }

  internal fun annualName(yearsInLove: Int, isEnglish: Boolean): String =
    if (isEnglish) "🎉 $yearsInLove-Year Love Anniversary" else "🎉 Kỷ Niệm $yearsInLove Năm Yêu Nhau"

  // Ba thông báo của một cột mốc: trước 3 ngày, trước 1 ngày (09:00) và đúng ngày (08:30); mỗi cái tự lọc theo giờ của nó.
  private fun alerts(
    msId: Long,
    date: LocalDate,
    name: String,
    input: ReminderPlanInput,
    couple: CoupleItem,
    nowMillis: Long,
    zone: TimeZone,
    out: MutableList<PlannedNotification>
  ) {
    val args = ReminderTextArgs(title = name, dateText = dateText(date), partnerName = couple.partnerName)
    fun add(key: ReminderKey, kind: ReminderKind, day: LocalDate, time: LocalTime, route: String) {
      val fireAt = LocalDateTime(day, time)
      val epoch = toEpochMillis(fireAt, zone)
      if (epoch <= nowMillis) return
      val text = ReminderTexts.of(kind, input.isEnglish, args)
      out += PlannedNotification(key, kind, fireAt, epoch, text.title, text.body, route)
    }
    add(
      ReminderKey.LoveMilestoneThreeDays(msId), ReminderKind.LOVE_MILESTONE_THREE_DAYS,
      date.minus(3, DateTimeUnit.DAY), NINE_AM, ROUTE_CALENDAR
    )
    add(
      ReminderKey.LoveMilestoneOneDay(msId), ReminderKind.LOVE_MILESTONE_ONE_DAY,
      date.minus(1, DateTimeUnit.DAY), NINE_AM, ROUTE_CALENDAR
    )
    add(ReminderKey.LoveMilestoneDayOf(msId), ReminderKind.LOVE_MILESTONE_DAY_OF, date, EIGHT_THIRTY, ROUTE_HOME)
  }

  /** "dd/MM/yyyy" có đệm số 0; không dùng String.format để chạy được ngoài JVM. */
  private fun dateText(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}/${date.month.number.toString().padStart(2, '0')}/${date.year.toString().padStart(4, '0')}"
}
