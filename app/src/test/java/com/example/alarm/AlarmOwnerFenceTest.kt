package com.example.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AccountDataVault
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.CustomReminderEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmOwnerFenceTest {

  private lateinit var context: Context
  private val alice = AccountDataVault.scopeOf("alice@x.com")
  private val bob = AccountDataVault.scopeOf("bob@x.com")

  @Before
  fun signInAsAlice() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit()
      .putString("scope", alice).commit()
  }

  private fun alarmIntent(owner: String?) = Intent(context, ReminderAlarmReceiver::class.java).apply {
    action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
    putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "Hẹn hò tối nay")
    putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, 42L)
    owner?.let { putExtra(ReminderAlarmReceiver.EXTRA_OWNER, it) }
  }

  private fun shownNotifications() =
    shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

  private fun alarmsScheduled() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms

  @Test
  fun scheduledAlarmCarriesTheTokenOfTheAccountInForce() {
    AlarmNotificationScheduler.scheduleAlarm(context, 42L, "Hẹn hò", "Tối nay", System.currentTimeMillis() + 60_000)

    val alarm = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.single()
    assertEquals(
      AccountDataVault.tokenOf(alice),
      shadowOf(alarm.operation).savedIntent.getStringExtra(ReminderAlarmReceiver.EXTRA_OWNER)
    )
  }

  @Test
  fun receiverShowsAlarmSetByTheAccountInForce() {
    ReminderAlarmReceiver().onReceive(context, alarmIntent(AccountDataVault.currentToken(context)))
    assertEquals(1, shownNotifications().size)
  }

  @Test
  fun receiverDropsAlarmSetByAnotherAccount() {
    ReminderAlarmReceiver().onReceive(context, alarmIntent(AccountDataVault.tokenOf(bob)))
    assertEquals(0, shownNotifications().size)
  }

  @Test
  fun switchingAccountCancelsTheOutgoingAccountsAlarms() {
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    try {
      runBlocking {
        val dao = db.inLoveDao()
        val reminderId = dao.insertCustomReminder(
          CustomReminderEntity(title = "Hẹn hò", dateText = "14/02", details = "", daysRemainingText = "")
        )
        AlarmNotificationScheduler.scheduleAlarm(context, reminderId, "Hẹn hò", "Tối nay", System.currentTimeMillis() + 60_000)

        val anniversary = AnniversaryDateEntity(title = "Ngày yêu nhau", dateText = "20/10", reminderDaysBefore = 0)
        AlarmNotificationScheduler.scheduleAnniversaryNotification(
          context, anniversary.copy(id = dao.insertAnniversaryDate(anniversary)), emptySet()
        )
      }
      assertEquals(2, alarmsScheduled().size)

      runBlocking { AlarmNotificationScheduler.cancelOutgoingAccountAlarms(context, db.inLoveDao()) }

      assertEquals(0, alarmsScheduled().size)
    } finally {
      db.close()
    }
  }
}
