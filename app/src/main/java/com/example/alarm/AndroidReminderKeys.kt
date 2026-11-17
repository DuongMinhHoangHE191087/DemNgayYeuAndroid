package com.example.alarm

import com.example.alarm.plan.ReminderKey

/** Mã request cũ của một khóa nhắc (công thức cũ, cắt về Int như reminderId.toInt()). */
fun legacyRequestCode(key: ReminderKey): Int = when (key) {
  is ReminderKey.Advance -> AlarmNotificationScheduler.advanceAlarmId(key.id)
  is ReminderKey.DayOf -> AlarmNotificationScheduler.dayOfAlarmId(key.id)
  is ReminderKey.UserMilestone -> 200000L + key.id
  ReminderKey.CoupleAnniversary -> 400001L
  is ReminderKey.LoveMilestoneThreeDays -> 300000L + key.msId
  is ReminderKey.LoveMilestoneOneDay -> 400000L + key.msId
  is ReminderKey.LoveMilestoneDayOf -> 500000L + key.msId
  is ReminderKey.Custom -> key.id
}.toInt()

/** Action cũ của PendingIntent: Custom dùng ACTION_REMINDER_ALARM, mọi khóa khác dùng ACTION_ANNIVERSARY_ALARM. */
fun legacyAction(key: ReminderKey): String = when (key) {
  is ReminderKey.Custom -> ReminderAlarmReceiver.ACTION_REMINDER_ALARM
  is ReminderKey.Advance,
  is ReminderKey.DayOf,
  is ReminderKey.UserMilestone,
  ReminderKey.CoupleAnniversary,
  is ReminderKey.LoveMilestoneThreeDays,
  is ReminderKey.LoveMilestoneOneDay,
  is ReminderKey.LoveMilestoneDayOf -> ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM
}
