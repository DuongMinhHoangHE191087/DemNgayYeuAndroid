package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: ACTION_REMINDER_ALARM
    Log.d(TAG, "onReceive triggered with action: $action")

    when (action) {
      ACTION_DAILY_ANNIVERSARY_CHECK -> {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            Log.d(TAG, "Executing daily morning anniversary check...")
            checkAndNotifyTodayAnniversaries(context)
            // Reschedule tomorrow's check
            AlarmNotificationScheduler.scheduleDailyMorningCheck(context)
          } catch (e: Exception) {
            Log.e(TAG, "Error in daily anniversary check: ${e.message}", e)
          } finally {
            pendingResult.finish()
          }
        }
      }

      ACTION_SNOOZE -> {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Nhắc nhở tình yêu ❤️"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Đã đến thời gian kỷ niệm đặc biệt của hai bạn!"
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, System.currentTimeMillis())
        val snoozeMillis = System.currentTimeMillis() + 60 * 60 * 1000L // 1 hour later
        AlarmNotificationScheduler.scheduleAlarm(
          context = context,
          reminderId = reminderId,
          title = title,
          message = message,
          triggerAtMillis = snoozeMillis
        )
        // Dismiss current notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId.toInt())
      }

      ACTION_ANNIVERSARY_ALARM, ACTION_REMINDER_ALARM -> {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Nhắc nhở tình yêu ❤️"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Đã đến thời gian kỷ niệm đặc biệt của hai bạn!"
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, System.currentTimeMillis())
        val targetTab = intent.getStringExtra(EXTRA_TARGET_TAB) ?: "calendar"
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: CHANNEL_ANNIVERSARIES_ID

        showNotification(
          context = context,
          title = title,
          message = message,
          notificationId = reminderId.toInt(),
          channelId = channelId,
          targetTab = targetTab
        )
      }

      else -> {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Nhắc nhở tình yêu ❤️"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Đã đến thời gian kỷ niệm đặc biệt của hai bạn!"
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, System.currentTimeMillis())
        showNotification(context, title, message, reminderId.toInt())
      }
    }
  }

  private suspend fun checkAndNotifyTodayAnniversaries(context: Context) {
    try {
      val dao = AppDatabase.getDatabase(context).inLoveDao()
      val anniversaries = dao.getAnniversaryDatesList().filter { it.notificationEnabled }
      val today = java.util.Calendar.getInstance()
      val currentDay = today.get(java.util.Calendar.DAY_OF_MONTH)
      val currentMonth = today.get(java.util.Calendar.MONTH) + 1 // 1-12

      for (ann in anniversaries) {
        val parsed = AlarmNotificationScheduler.parseDateToMonthDayYear(ann.dateText) ?: continue
        if (parsed.first == currentDay && parsed.second == currentMonth) {
          // It's today!
          showNotification(
            context = context,
            title = "🎉 Hôm Nay: ${ann.title}!",
            message = "Hôm nay là ngày kỷ niệm '${ann.title}' (${ann.dateText})! Chúc hai bạn một ngày ngập tràn ngọt ngào và hạnh phúc! ❤️",
            notificationId = (100000 + ann.id * 10).toInt(),
            channelId = CHANNEL_ANNIVERSARIES_ID,
            targetTab = "calendar"
          )
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed checkAndNotifyTodayAnniversaries", e)
    }
  }

  companion object {
    private const val TAG = "ReminderAlarmReceiver"

    const val ACTION_REMINDER_ALARM = "com.example.inlove.ACTION_REMINDER_ALARM"
    const val ACTION_ANNIVERSARY_ALARM = "com.example.inlove.ACTION_ANNIVERSARY_ALARM"
    const val ACTION_DAILY_ANNIVERSARY_CHECK = "com.example.inlove.ACTION_DAILY_ANNIVERSARY_CHECK"
    const val ACTION_SNOOZE = "com.example.inlove.ACTION_SNOOZE"

    const val CHANNEL_ANNIVERSARIES_ID = "love_anniversaries_channel"
    const val CHANNEL_ANNIVERSARIES_NAME = "Kỷ niệm & Ngày đặc biệt ❤️"
    const val CHANNEL_REMINDERS_ID = "love_reminders_channel"
    const val CHANNEL_REMINDERS_NAME = "InLove Reminders & Alarms"

    const val EXTRA_TITLE = "extra_reminder_title"
    const val EXTRA_MESSAGE = "extra_reminder_message"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_TARGET_TAB = "extra_target_tab"
    const val EXTRA_CHANNEL_ID = "extra_channel_id"

    fun showNotification(
      context: Context,
      title: String,
      message: String,
      notificationId: Int = 1001,
      channelId: String = CHANNEL_ANNIVERSARIES_ID,
      targetTab: String = "calendar"
    ) {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      // Create Notification Channels for Android O+
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val anniversaryChannel = NotificationChannel(
          CHANNEL_ANNIVERSARIES_ID,
          CHANNEL_ANNIVERSARIES_NAME,
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Thông báo nhắc nhở ngày kỷ niệm tình yêu và các cột mốc đặc biệt"
          enableLights(true)
          lightColor = Color.rgb(255, 64, 129) // Pink accent
          enableVibration(true)
          vibrationPattern = longArrayOf(0, 300, 150, 300) // Romantic double heartbeat rhythm
        }
        notificationManager.createNotificationChannel(anniversaryChannel)

        val remindersChannel = NotificationChannel(
          CHANNEL_REMINDERS_ID,
          CHANNEL_REMINDERS_NAME,
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Chuông báo thức hẹn hò và chuẩn bị quà tặng"
          enableLights(true)
          lightColor = Color.rgb(255, 138, 101)
          enableVibration(true)
        }
        notificationManager.createNotificationChannel(remindersChannel)
      }

      val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("from_notification", true)
        putExtra("target_tab", targetTab)
        putExtra("notification_id", notificationId)
      }

      val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      // Snooze PendingIntent for 1 hour later
      val snoozeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
        action = ACTION_SNOOZE
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_MESSAGE, message)
        putExtra(EXTRA_REMINDER_ID, notificationId.toLong())
      }
      val snoozePendingIntent = PendingIntent.getBroadcast(
        context,
        notificationId + 500000,
        snoozeIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

      val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setSound(defaultSoundUri)
        .setVibrate(longArrayOf(0, 300, 150, 300))
        .setColor(0xFFFF4081.toInt())
        .setContentIntent(pendingIntent)
        .addAction(
          R.drawable.ic_launcher_foreground,
          "Mở InLove ❤️",
          pendingIntent
        )
        .addAction(
          R.drawable.ic_launcher_foreground,
          "Nhắc lại sau 1h ⏰",
          snoozePendingIntent
        )
        .build()

      notificationManager.notify(notificationId, notification)
    }
  }
}
