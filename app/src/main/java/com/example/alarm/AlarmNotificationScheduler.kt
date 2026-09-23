package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.MilestoneEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmNotificationScheduler {

  private const val TAG = "AlarmScheduler"

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
  ): Long {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
      set(Calendar.MONTH, month - 1)
      set(Calendar.DAY_OF_MONTH, day)
      set(Calendar.HOUR_OF_DAY, hourOfDay)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    if (isAnnual) {
      target.set(Calendar.YEAR, now.get(Calendar.YEAR))
      if (target.before(now)) {
        // If already passed this year, advance to next year
        target.add(Calendar.YEAR, 1)
      }
    } else {
      if (year != null) {
        target.set(Calendar.YEAR, year)
      }
    }

    return target.timeInMillis
  }

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

    if (triggerAtMillis <= System.currentTimeMillis()) {
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
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      reminderId.toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
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
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      } else {
        alarmManager.setExact(
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

  /**
   * Schedules advance reminder and day-of reminder for a specific anniversary date.
   */
  fun scheduleAnniversaryNotification(
    context: Context,
    anniversary: AnniversaryDateEntity
  ): Boolean {
    val parsed = parseDateToMonthDayYear(anniversary.dateText) ?: return false
    val anniversaryMillis = calculateNextOccurrenceMillis(
      day = parsed.first,
      month = parsed.second,
      year = parsed.third,
      isAnnual = anniversary.isAnnual,
      hourOfDay = 9,
      minute = 0
    )

    var successAny = false

    // 1. Advance notification (e.g. 7 days, 3 days, 1 day before at 09:00 AM)
    if (anniversary.reminderDaysBefore > 0) {
      val advanceMillis = anniversaryMillis - (anniversary.reminderDaysBefore * 24 * 60 * 60 * 1000L)
      if (advanceMillis > System.currentTimeMillis()) {
        val advanceId = (100000L + anniversary.id * 10L + 1L)
        val advanceScheduled = scheduleAlarm(
          context = context,
          reminderId = advanceId,
          title = "🔔 Sắp Đến: ${anniversary.title}",
          message = "Còn ${anniversary.reminderDaysBefore} ngày nữa là đến ngày kỷ niệm '${anniversary.title}' (${anniversary.dateText}). Đừng quên chuẩn bị điều bất ngờ cho người ấy nhé! 💕",
          triggerAtMillis = advanceMillis,
          action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
          channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
          targetTab = "calendar"
        )
        if (advanceScheduled) successAny = true
      }
    }

    // 2. Day-of notification (at 09:00 AM)
    if (anniversaryMillis > System.currentTimeMillis()) {
      val dayOfId = (100000L + anniversary.id * 10L + 2L)
      val dayOfScheduled = scheduleAlarm(
        context = context,
        reminderId = dayOfId,
        title = "🎉 Hôm Nay: ${anniversary.title}!",
        message = "Hôm nay là ngày kỷ niệm đặc biệt '${anniversary.title}' (${anniversary.dateText})! Chúc hai bạn một ngày ngập tràn ngọt ngào và yêu thương! ❤️✨",
        triggerAtMillis = anniversaryMillis,
        action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
        channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
        targetTab = "calendar"
      )
      if (dayOfScheduled) successAny = true
    }

    return successAny
  }

  fun cancelAnniversaryNotification(context: Context, anniversaryId: Long) {
    cancelAlarm(context, 100000L + anniversaryId * 10L + 1L, ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
    cancelAlarm(context, 100000L + anniversaryId * 10L + 2L, ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
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

    if (milestoneMillis > System.currentTimeMillis()) {
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
   * Schedules a daily morning heartbeat check at 09:00 AM.
   */
  fun scheduleDailyMorningCheck(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val now = Calendar.getInstance()
    val next9Am = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 9)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (before(now)) {
        add(Calendar.DAY_OF_YEAR, 1)
      }
    }

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
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          next9Am.timeInMillis,
          pendingIntent
        )
      } else {
        alarmManager.set(
          AlarmManager.RTC_WAKEUP,
          next9Am.timeInMillis,
          pendingIntent
        )
      }
      Log.d(TAG, "Scheduled daily anniversary morning check at ${next9Am.time}")
    } catch (e: Exception) {
      Log.e(TAG, "Error scheduling daily morning check", e)
    }
  }

  /**
   * Queries Room DB and reschedules alarms for all enabled anniversaries, couple profile, and milestones.
   */
  suspend fun scheduleAllAnniversariesFromDb(context: Context): Int {
    var scheduledCount = 0
    try {
      val dao = AppDatabase.getDatabase(context).inLoveDao()
      val anniversaries = dao.getAnniversaryDatesList()
      for (ann in anniversaries) {
        if (ann.notificationEnabled) {
          if (scheduleAnniversaryNotification(context, ann)) {
            scheduledCount++
          }
        } else {
          cancelAnniversaryNotification(context, ann.id)
        }
      }

      val milestones = dao.getMilestonesList()
      for (ms in milestones) {
        if (ms.notificationEnabled && !ms.isPast) {
          if (scheduleMilestoneNotification(context, ms)) {
            scheduledCount++
          }
        }
      }

      // Schedule couple profile main anniversary
      val profile = dao.getCoupleProfileSync()
      if (profile != null) {
        val parsed = parseDateToMonthDayYear(profile.anniversaryDate)
        if (parsed != null) {
          val triggerMillis = calculateNextOccurrenceMillis(
            day = parsed.first,
            month = parsed.second,
            year = parsed.third,
            isAnnual = true,
            hourOfDay = 9,
            minute = 0
          )
          if (triggerMillis > System.currentTimeMillis()) {
            scheduleAlarm(
              context = context,
              reminderId = 400001L,
              title = "💑 Kỷ Niệm Ngày Yêu Nhau: ${profile.loveTitle} ❤️",
              message = "Chúc mừng ngày kỷ niệm chính thức yêu nhau của hai bạn! Chặng đường ${profile.loveDays} ngày yêu thương ngọt ngào! 🎉🌹",
              triggerAtMillis = triggerMillis,
              action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM,
              channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
              targetTab = "home"
            )
            scheduledCount++
          }
        }
      }

      // Schedule recurring daily check
      scheduleDailyMorningCheck(context)
      Log.d(TAG, "Successfully rescheduled $scheduledCount anniversary/milestone alarms from DB.")
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

  fun formatAlarmTime(millis: Long): String {
    val formatter = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault())
    return formatter.format(Date(millis))
  }
}
