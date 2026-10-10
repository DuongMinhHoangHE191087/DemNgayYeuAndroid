package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.db.AccountDataVault
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.MilestoneEntity
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmNotificationScheduler {

  private const val TAG = "AlarmScheduler"
  private const val PREFS_NAME = "inlove_notifications"
  private const val KEY_ANNIVERSARY_ENABLED = "anniversary_enabled"

  // reminder_settings keys; a missing row counts as enabled.
  internal const val CADENCE_EXACT_DAY = "exact_day"

  // Clock seam: tests pin it so the date and time rules stay deterministic.
  internal var nowMillis: () -> Long = { System.currentTimeMillis() }

  /**
   * Parses various date formats into Triple(day, month, year?).
   * month is 1-12.
   */
  fun parseDateToMonthDayYear(dateText: String): Triple<Int, Int, Int?>? {
    val cleanText = dateText.trim()
    if (cleanText.isBlank()) return null

    // 1. Regex for: "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "dd/MM"
    val slashRegex = Regex("""^(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{4}))?""")
    val matchSlash = slashRegex.find(cleanText)
    if (matchSlash != null) {
      val day = matchSlash.groupValues[1].toIntOrNull()
      val month = matchSlash.groupValues[2].toIntOrNull()
      val year = matchSlash.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull()
      if (day != null && month != null && day in 1..31 && month in 1..12) {
        return Triple(day, month, year)
      }
    }

    // 2. Regex for Vietnamese textual format: "11 Tháng 9, 2026", "24 Tháng 10, 2026", "11 Tháng 09"
    val vnRegex = Regex("""(\d{1,2})\s+(?:Tháng|tháng|Thg|thg)\s+(\d{1,2})(?:[,\s]+(\d{4}))?""")
    val matchVn = vnRegex.find(cleanText)
    if (matchVn != null) {
      val day = matchVn.groupValues[1].toIntOrNull()
      val month = matchVn.groupValues[2].toIntOrNull()
      val year = matchVn.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull()
      if (day != null && month != null && day in 1..31 && month in 1..12) {
        return Triple(day, month, year)
      }
    }

    // 3. SimpleDateFormat fallbacks
    val formats = listOf(
      "dd/MM/yyyy",
      "dd/MM",
      "yyyy-MM-dd",
      "d/M/yyyy",
      "d/M"
    )
    for (fmt in formats) {
      try {
        val sdf = SimpleDateFormat(fmt, Locale.getDefault()).apply { isLenient = false }
        val d = sdf.parse(cleanText)
        if (d != null) {
          val cal = Calendar.getInstance().apply { time = d }
          val hasYear = fmt.contains("yyyy")
          return Triple(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            if (hasYear) cal.get(Calendar.YEAR) else null
          )
        }
      } catch (_: Exception) {
        // Try next format
      }
    }

    return null
  }

  internal fun today(): LocalDate =
    Instant.ofEpochMilli(nowMillis()).atZone(ZoneId.systemDefault()).toLocalDate()

  /** A day the month does not have (Feb 29 in common years, Apr 31) falls on the month's last day. */
  internal fun occurrenceDate(day: Int, month: Int, year: Int): LocalDate {
    val yearMonth = YearMonth.of(year, month)
    return yearMonth.atDay(minOf(day, yearMonth.lengthOfMonth()))
  }

  /**
   * Calendar day of the next occurrence. An annual date rolls to next year once its time has passed today;
   * a one-off date keeps its own year.
   */
  internal fun nextOccurrenceDate(
    day: Int,
    month: Int,
    year: Int?,
    isAnnual: Boolean,
    hourOfDay: Int = 9,
    minute: Int = 0
  ): LocalDate {
    val currentYear = today().year
    if (!isAnnual) return occurrenceDate(day, month, year ?: currentYear)
    val thisYear = occurrenceDate(day, month, currentYear)
    return if (millisOn(thisYear, hourOfDay, minute) < nowMillis()) {
      occurrenceDate(day, month, currentYear + 1)
    } else {
      thisYear
    }
  }

  private fun millisOn(date: LocalDate, hourOfDay: Int = 9, minute: Int = 0): Long =
    ZonedDateTime.of(date, LocalTime.of(hourOfDay, minute), ZoneId.systemDefault()).toInstant().toEpochMilli()

  /**
   * Calculates next upcoming timestamp in millis for an anniversary at specified hour:minute.
   */
  fun calculateNextOccurrenceMillis(
    day: Int,
    month: Int, // 1-12
    year: Int? = null,
    isAnnual: Boolean = true,
    hourOfDay: Int = 9,
    minute: Int = 0
  ): Long = millisOn(nextOccurrenceDate(day, month, year, isAnnual, hourOfDay, minute), hourOfDay, minute)

  /**
   * Schedules an alarm with AlarmManager.
   */
  fun scheduleAlarm(
    context: Context,
    reminderId: Long,
    title: String,
    message: String,
    triggerAtMillis: Long,
    action: String = ReminderAlarmReceiver.ACTION_REMINDER_ALARM,
    channelId: String = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
    targetTab: String = "calendar"
  ): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
      ?: return false

    if (triggerAtMillis <= nowMillis()) {
      Log.d(TAG, "Skipping past alarm trigger $triggerAtMillis for reminder $reminderId")
      return false
    }

    val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
      this.action = action
      putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
      putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, message)
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
      putExtra(ReminderAlarmReceiver.EXTRA_CHANNEL_ID, channelId)
      putExtra(ReminderAlarmReceiver.EXTRA_TARGET_TAB, targetTab)
      putExtra(ReminderAlarmReceiver.EXTRA_OWNER, AccountDataVault.currentToken(context))
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      reminderId.toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Cancel first: a re-set replaces the alarm whether or not the platform merges a repeated PendingIntent.
    alarmManager.cancel(pendingIntent)
    try {
      if (exactGranted(alarmManager)) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      } else {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      }
      Log.d(TAG, "Scheduled alarm $reminderId at $triggerAtMillis ($title)")
      return true
    } catch (e: SecurityException) {
      Log.e(TAG, "SecurityException scheduling exact alarm: ${e.message}, using windowed fallback", e)
      try {
        alarmManager.set(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
        return true
      } catch (ex: Throwable) {
        Log.e(TAG, "Failed fallback alarm", ex)
        return false
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to schedule alarm", e)
      return false
    }
  }

  /** Exact alarms need the user's grant from API 31; before that they are always allowed. */
  fun exactAlarmAllowed(sdkInt: Int, canScheduleExact: Boolean): Boolean =
    sdkInt < Build.VERSION_CODES.S || canScheduleExact

  /** False means alarms still fire, but inexactly: Doze can delay them by minutes. */
  fun exactAlarmsGranted(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return exactGranted(alarmManager)
  }

  private fun exactGranted(alarmManager: AlarmManager): Boolean = exactAlarmAllowed(
    Build.VERSION.SDK_INT,
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()
  )

  fun cancelAlarm(
    context: Context,
    reminderId: Long,
    action: String = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
  ) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
      this.action = action
    }
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      reminderId.toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
    Log.d(TAG, "Canceled alarm for reminder $reminderId")
  }

  /** Request code of an anniversary's advance reminder. */
  fun advanceAlarmId(anniversaryId: Long): Long = 100000L + anniversaryId * 10L + 1L

  /** Request code of an anniversary's day-of reminder; the morning check posts the same notification id. */
  fun dayOfAlarmId(anniversaryId: Long): Long = 100000L + anniversaryId * 10L + 2L

  /** Cadence keys the user switched off. A reminder_settings row is the only switch; no row means on. */
  suspend fun disabledCadenceKeys(dao: InLoveDao): Set<String> =
    dao.getReminderCadencesList().filterNot { it.isEnabled }.map { it.key }.toSet()

  /**
   * Schedules the advance reminder (reminderDaysBefore days before, 09:00) and the day-of reminder (09:00).
   * 7_days, 3_days and 1_day switch off the advance reminder of that day count; exact_day switches off the
   * day-of reminder. Any alarm that is switched off or can no longer fire is cancelled.
   */
  fun scheduleAnniversaryNotification(
    context: Context,
    anniversary: AnniversaryDateEntity,
    disabledCadence: Set<String>
  ): Boolean {
    val parsed = parseDateToMonthDayYear(anniversary.dateText)
    if (!anniversary.notificationEnabled || parsed == null) {
      cancelAnniversaryNotification(context, anniversary.id)
      return false
    }
    val date = nextOccurrenceDate(parsed.first, parsed.second, parsed.third, anniversary.isAnnual)
    val now = nowMillis()
    var successAny = false

    // 1. Advance notification, daysBefore days before the date at 09:00
    val advanceKey = when (anniversary.reminderDaysBefore) {
      7 -> "7_days"
      3 -> "3_days"
      1 -> "1_day"
      else -> null
    }
    val advanceOn = anniversary.reminderDaysBefore > 0 && (advanceKey == null || advanceKey !in disabledCadence)
    val advanceMillis = millisOn(date.minusDays(anniversary.reminderDaysBefore.toLong()))
    if (advanceOn && advanceMillis > now) {
      val advanceScheduled = scheduleAlarm(
        context = context,
        reminderId = advanceAlarmId(anniversary.id),
        title = "🔔 Sắp Đến: ${anniversary.title}",
        message = "Còn ${anniversary.reminderDaysBefore} ngày nữa là đến ngày kỷ niệm '${anniversary.title}' (${anniversary.dateText}). Đừng quên chuẩn bị điều bất ngờ cho người ấy nhé! 💕",
        triggerAtMillis = advanceMillis,
        action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
        channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
        targetTab = "calendar"
      )
      if (advanceScheduled) successAny = true
    } else {
      cancelAlarm(context, advanceAlarmId(anniversary.id), ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
    }

    // 2. Day-of notification (at 09:00 AM)
    val dayOfMillis = millisOn(date)
    if (CADENCE_EXACT_DAY !in disabledCadence && dayOfMillis > now) {
      val (dayOfTitle, dayOfMessage) = dayOfTexts(anniversary.title, anniversary.dateText)
      val dayOfScheduled = scheduleAlarm(
        context = context,
        reminderId = dayOfAlarmId(anniversary.id),
        title = dayOfTitle,
        message = dayOfMessage,
        triggerAtMillis = dayOfMillis,
        action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
        channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
        targetTab = "calendar"
      )
      if (dayOfScheduled) successAny = true
    } else {
      cancelAlarm(context, dayOfAlarmId(anniversary.id), ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
    }

    return successAny
  }

  /** Day-of text. The morning check posts into the same notification slot, so both paths share it. */
  internal fun dayOfTexts(title: String, dateText: String): Pair<String, String> =
    "🎉 Hôm Nay: $title!" to "Hôm nay là ngày kỷ niệm đặc biệt '$title' ($dateText)! Chúc hai bạn một ngày ngập tràn ngọt ngào và yêu thương! ❤️✨"

  fun cancelAnniversaryNotification(context: Context, anniversaryId: Long) {
    cancelAlarm(context, advanceAlarmId(anniversaryId), ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
    cancelAlarm(context, dayOfAlarmId(anniversaryId), ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
  }

  /**
   * Schedules reminder for a Milestone entity.
   */
  fun scheduleMilestoneNotification(context: Context, milestone: MilestoneEntity): Boolean {
    val parsed = parseDateToMonthDayYear(milestone.dateText) ?: return false
    val milestoneMillis = calculateNextOccurrenceMillis(
      day = parsed.first,
      month = parsed.second,
      year = parsed.third,
      isAnnual = false,
      hourOfDay = 9,
      minute = 0
    )

    if (milestoneMillis > nowMillis()) {
      val milestoneId = (200000L + milestone.id)
      return scheduleAlarm(
        context = context,
        reminderId = milestoneId,
        title = "⭐ Cột Mốc Tình Yêu: ${milestone.title}",
        message = "Cột mốc ý nghĩa '${milestone.title}' (${milestone.dateText}) đang đến rất gần! Cùng người ấy lưu lại khoảnh khắc này nhé! 💖",
        triggerAtMillis = milestoneMillis,
        action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
        channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
        targetTab = "calendar"
      )
    }
    return false
  }

  fun cancelMilestoneNotification(context: Context, milestoneId: Long) {
    cancelAlarm(context, 200000L + milestoneId, ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
  }

  /**
   * Cancels the alarms the outgoing account set from its own rows, so they cannot fire under the next account.
   */
  suspend fun cancelOutgoingAccountAlarms(context: Context, dao: InLoveDao) {
    // ponytail: Firestore milestone offsets and the 400001 summary id stay scheduled; the owner fence drops them when they fire.
    dao.getAnniversaryDatesList().forEach { cancelAnniversaryNotification(context, it.id) }
    dao.getMilestonesList().filter { it.isUserCreated }.forEach { cancelMilestoneNotification(context, it.id) }
    dao.getCustomRemindersList().forEach { cancelAlarm(context, it.id) }
  }

  /**
   * Schedules the daily morning check at the next 09:00. Each run re-arms the next one.
   */
  fun scheduleDailyMorningCheck(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val today = today()
    val todayAt9 = millisOn(today)
    val next9Am = if (todayAt9 > nowMillis()) todayAt9 else millisOn(today.plusDays(1))

    val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
      action = ReminderAlarmReceiver.ACTION_DAILY_ANNIVERSARY_CHECK
    }
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      500001,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
      alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        next9Am,
        pendingIntent
      )
      Log.d(TAG, "Scheduled daily anniversary morning check at ${Date(next9Am)}")
    } catch (e: Exception) {
      Log.e(TAG, "Error scheduling daily morning check", e)
    }
  }

  /**
   * Makes the alarms match the database: anniversaries, milestones, the couple anniversary, personal reminders,
   * and the daily check. Runs on boot, on a clock or timezone change, on account switch, and after edits.
   * Returns the number of alarms scheduled.
   */
  suspend fun scheduleAllAnniversariesFromDb(context: Context, dao: InLoveDao): Int {
    var scheduledCount = 0
    try {
      scheduleDailyMorningCheck(context)
      val disabledCadence = disabledCadenceKeys(dao)

      for (ann in dao.getAnniversaryDatesList()) {
        if (scheduleAnniversaryNotification(context, ann, disabledCadence)) {
          scheduledCount++
        }
      }

      for (ms in dao.getMilestonesList()) {
        if (ms.notificationEnabled && !ms.isPast && scheduleMilestoneNotification(context, ms)) {
          scheduledCount++
        } else {
          cancelMilestoneNotification(context, ms.id)
        }
      }

      // Couple anniversary: fixed id 400001. A missing or unreadable date cancels the alarm it left behind.
      val profile = dao.getCoupleProfileSync()
      val parsed = profile?.let { parseDateToMonthDayYear(it.anniversaryDate) }
      val coupleScheduled = profile != null && parsed != null && scheduleAlarm(
        context = context,
        reminderId = 400001L,
        title = "💑 Kỷ Niệm Ngày Yêu Nhau: ${profile.loveTitle} ❤️",
        message = "Chúc mừng ngày kỷ niệm chính thức yêu nhau của hai bạn! Chặng đường ${profile.loveDays} ngày yêu thương ngọt ngào! 🎉🌹",
        triggerAtMillis = calculateNextOccurrenceMillis(parsed.first, parsed.second, parsed.third, isAnnual = true),
        action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
        channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
        targetTab = "home"
      )
      if (coupleScheduled) {
        scheduledCount++
      } else {
        cancelAlarm(context, 400001L, ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
      }

      // Personal alarms keep their trigger time on the reminder row; a past trigger is skipped.
      for (reminder in dao.getCustomRemindersList()) {
        val triggerMillis = reminder.alarmTimeMillis ?: continue
        if (scheduleAlarm(context, reminder.id, reminder.title, reminder.details, triggerMillis)) {
          scheduledCount++
        }
      }
      Log.d(TAG, "Successfully rescheduled $scheduledCount alarms from DB.")
    } catch (e: Exception) {
      Log.e(TAG, "Failed scheduleAllAnniversariesFromDb: ${e.message}", e)
    }
    return scheduledCount
  }

  /**
   * Fires an immediate test anniversary notification so the user can verify sound, vibration, and layout.
   */
  fun triggerInstantTest(
    context: Context,
    title: String = "🔔 Thử nghiệm thông báo kỷ niệm ❤️",
    message: String = "Sắp đến ngày kỷ niệm đặc biệt của hai bạn! Hãy chuẩn bị những bất ngờ ngọt ngào nhé! ✨"
  ) {
    ReminderAlarmReceiver.showNotification(
      context = context,
      title = title,
      message = message,
      notificationId = 9999,
      channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
      targetTab = "calendar"
    )
  }

  // Device-wide switch for anniversary notices. Alarms stay armed while it is off; delivery drops them.
  fun anniversaryNotificationsEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ANNIVERSARY_ENABLED, true)

  fun setAnniversaryNotificationsEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ANNIVERSARY_ENABLED, enabled).apply()
  }

  fun formatAlarmTime(millis: Long): String {
    val formatter = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault())
    return formatter.format(Date(millis))
  }
}
