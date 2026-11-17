package com.example.alarm.plan

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

object UserItemRules {
  private val NINE_AM = LocalTime(9, 0)
  private const val ROUTE_CALENDAR = "calendar"
  private const val ROUTE_HOME = "home"
  private const val EXACT_DAY = "exact_day"

  /** Luật I1, I2, I3, I4, I8 của kế hoạch p5 mục 4.1.5; không sắp xếp, không cắt, không che chữ. */
  fun plan(input: ReminderPlanInput, nowMillis: Long, zone: TimeZone): List<PlannedNotification> {
    val out = mutableListOf<PlannedNotification>()
    if (input.anniversaryNotificationsEnabled) {
      for (item in input.anniversaries) anniversary(item, input, nowMillis, zone, out)
      for (item in input.milestones) milestone(item, input, nowMillis, zone, out)
      input.couple?.let { couple(it, input, nowMillis, zone, out) }
    }
    for (item in input.customReminders) custom(item, input, nowMillis, zone, out)
    return out
  }

  // I1 và I2: nhắc trước N ngày, rồi đúng ngày.
  private fun anniversary(
    item: AnniversaryItem,
    input: ReminderPlanInput,
    nowMillis: Long,
    zone: TimeZone,
    out: MutableList<PlannedNotification>
  ) {
    if (!item.notificationEnabled) return
    val parsed = parseDateText(item.dateText) ?: return
    val date = nextOccurrence(parsed.day, parsed.month, parsed.year, item.isAnnual, nowMillis, zone)
    val n = item.reminderDaysBefore

    if (n > 0 && !isGatedOff(n, input.disabledCadenceKeys)) {
      // N rất lớn có thể làm phép trừ ngày ném lỗi: bỏ nhắc trước đó thay vì làm hỏng cả kế hoạch.
      val advance = try {
        at9(
          ReminderKey.Advance(item.id), ReminderKind.ADVANCE, date.minus(n, DateTimeUnit.DAY),
          ReminderTexts.of(ReminderKind.ADVANCE, input.isEnglish, ReminderTextArgs(item.title, item.dateText, n)),
          ROUTE_CALENDAR, false, nowMillis, zone
        )
      } catch (e: RuntimeException) {
        null
      }
      if (advance != null) out += advance
    }

    if (EXACT_DAY !in input.disabledCadenceKeys) {
      // 29/2, 30/2, 31/2 bị kẹp sang ngày khác tùy năm nên không lặp được.
      val repeats = item.isAnnual && !(parsed.month == 2 && parsed.day >= 29)
      at9(
        ReminderKey.DayOf(item.id), ReminderKind.DAY_OF, date,
        ReminderTexts.of(ReminderKind.DAY_OF, input.isEnglish, ReminderTextArgs(item.title, item.dateText)),
        ROUTE_CALENDAR, repeats, nowMillis, zone
      )?.let { out += it }
    }
  }

  // I3: cột mốc người dùng, một lần.
  private fun milestone(
    item: MilestoneItem,
    input: ReminderPlanInput,
    nowMillis: Long,
    zone: TimeZone,
    out: MutableList<PlannedNotification>
  ) {
    if (!item.notificationEnabled || item.isPast) return
    val parsed = parseDateText(item.dateText) ?: return
    val date = nextOccurrence(parsed.day, parsed.month, parsed.year, false, nowMillis, zone)
    at9(
      ReminderKey.UserMilestone(item.id), ReminderKind.USER_MILESTONE, date,
      ReminderTexts.of(ReminderKind.USER_MILESTONE, input.isEnglish, ReminderTextArgs(item.title, item.dateText)),
      ROUTE_CALENDAR, false, nowMillis, zone
    )?.let { out += it }
  }

  // I4: ngày yêu nhau, luôn sau ngày bắt đầu; số ngày tính cho đúng ngày kích hoạt (bộ đếm tính cả ngày bắt đầu).
  private fun couple(
    couple: CoupleItem,
    input: ReminderPlanInput,
    nowMillis: Long,
    zone: TimeZone,
    out: MutableList<PlannedNotification>
  ) {
    val parsed = parseDateText(couple.startDate) ?: return
    val year = parsed.year ?: return
    val start = occurrenceDate(parsed.day, parsed.month, year)
    val date = maxOf(
      nextOccurrence(parsed.day, parsed.month, year, true, nowMillis, zone),
      occurrenceDate(parsed.day, parsed.month, year + 1)
    )
    val loveDays = start.daysUntil(date) + 1
    at9(
      ReminderKey.CoupleAnniversary, ReminderKind.COUPLE_ANNIVERSARY, date,
      ReminderTexts.of(ReminderKind.COUPLE_ANNIVERSARY, input.isEnglish, ReminderTextArgs(title = couple.title, loveDays = loveDays)),
      ROUTE_HOME, false, nowMillis, zone
    )?.let { out += it }
  }

  // I8: báo thức cá nhân, thời điểm tuyệt đối, không chịu công tắc kỷ niệm.
  private fun custom(
    item: CustomReminderItem,
    input: ReminderPlanInput,
    nowMillis: Long,
    zone: TimeZone,
    out: MutableList<PlannedNotification>
  ) {
    if (item.alarmEpochMillis <= nowMillis) return
    val text = ReminderTexts.of(ReminderKind.CUSTOM, input.isEnglish, ReminderTextArgs(title = item.title, details = item.details))
    out += PlannedNotification(
      key = ReminderKey.Custom(item.id),
      kind = ReminderKind.CUSTOM,
      fireAt = toLocalDateTime(item.alarmEpochMillis, zone),
      fireAtEpochMillis = item.alarmEpochMillis,
      title = text.title,
      body = text.body,
      route = ROUTE_CALENDAR,
      repeatsYearly = false,
      isAbsolute = true
    )
  }

  // Hàng công tắc chỉ có cho N = 7, 3, 1; N khác không có hàng nên không bị chặn.
  private fun isGatedOff(n: Int, disabled: Set<String>): Boolean = when (n) {
    7 -> "7_days" in disabled
    3 -> "3_days" in disabled
    1 -> "1_day" in disabled
    else -> false
  }

  /** Thông báo lúc 09:00 của [date] theo [zone]; null khi giờ đó không còn ở tương lai (so nghiêm ngặt). */
  private fun at9(
    key: ReminderKey,
    kind: ReminderKind,
    date: LocalDate,
    text: ReminderText,
    route: String,
    repeatsYearly: Boolean,
    nowMillis: Long,
    zone: TimeZone
  ): PlannedNotification? {
    val fireAt = LocalDateTime(date, NINE_AM)
    val epoch = toEpochMillis(fireAt, zone)
    if (epoch <= nowMillis) return null
    return PlannedNotification(key, kind, fireAt, epoch, text.title, text.body, route, repeatsYearly)
  }
}
