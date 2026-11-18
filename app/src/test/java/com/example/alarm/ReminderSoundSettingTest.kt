package com.example.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AccountDataVault
import com.example.ui.util.LocaleManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSoundSettingTest {

  private lateinit var context: Context
  private lateinit var notificationManager: NotificationManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    LocaleManager.saveSoundEnabled(context, true)
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit()
      .putString("scope", AccountDataVault.scopeOf("tester@inlove.app")).commit()
  }

  @After
  fun tearDown() {
    LocaleManager.saveSoundEnabled(context, true)
  }

  @Test
  fun soundOn_usesHighImportanceAnniversaryChannel() {
    LocaleManager.saveSoundEnabled(context, true)

    ReminderAlarmReceiver.showNotification(
      context = context,
      title = "Kỷ niệm 100 ngày",
      message = "Chúc mừng hai bạn!",
      notificationId = 2001,
      channelId = ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID,
    )

    val posted = shadowOf(notificationManager).getNotification(2001)
    assertNotNull(posted)
    assertEquals(ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID, posted.channelId)
    assertEquals(NotificationCompat.PRIORITY_HIGH, posted.priority)

    val channel = notificationManager.getNotificationChannel(ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID)
    assertNotNull(channel)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    assertTrue(channel.shouldVibrate())
  }

  @Test
  fun soundOff_routesToSilentChannelWithNoSoundOrVibration() {
    LocaleManager.saveSoundEnabled(context, false)

    ReminderAlarmReceiver.showNotification(
      context = context,
      title = "Nhắc hẹn tối nay",
      message = "Đừng quên bó hoa nhé!",
      notificationId = 2002,
      channelId = ReminderAlarmReceiver.CHANNEL_REMINDERS_ID,
    )

    val posted = shadowOf(notificationManager).getNotification(2002)
    assertNotNull(posted)
    assertEquals(ReminderAlarmReceiver.CHANNEL_REMINDERS_SILENT_ID, posted.channelId)
    assertEquals(NotificationCompat.PRIORITY_DEFAULT, posted.priority)
    assertNull(posted.sound)
    assertNull(posted.vibrate)
    assertEquals(0, posted.defaults)

    val channel = notificationManager.getNotificationChannel(ReminderAlarmReceiver.CHANNEL_REMINDERS_SILENT_ID)
    assertNotNull(channel)
    assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    assertNull(channel.sound)
    assertTrue(!channel.shouldVibrate())
  }

  @Test
  fun onReceiveReminderBroadcast_respectsSoundToggleOffForAnniversaryChannel() {
    LocaleManager.saveSoundEnabled(context, false)
    val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
      action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
      putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "Lễ Thất Tịch")
      putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, "Ngắm sao cùng nhau")
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, 2003L)
      putExtra(ReminderAlarmReceiver.EXTRA_CHANNEL_ID, ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_ID)
      putExtra(ReminderAlarmReceiver.EXTRA_OWNER, AccountDataVault.currentToken(context))
    }

    ReminderAlarmReceiver().onReceive(context, intent)

    val posted = shadowOf(notificationManager).getNotification(2003)
    assertNotNull(posted)
    assertEquals(ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_SILENT_ID, posted.channelId)
    assertNull(posted.sound)
    val silentChannel = notificationManager.getNotificationChannel(ReminderAlarmReceiver.CHANNEL_ANNIVERSARIES_SILENT_ID)
    assertNotNull(silentChannel)
    assertNull(silentChannel.sound)
    assertTrue(!silentChannel.shouldVibrate())
  }
}
