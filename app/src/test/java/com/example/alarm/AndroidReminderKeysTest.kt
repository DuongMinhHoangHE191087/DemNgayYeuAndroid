package com.example.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.plan.ReminderKey
import com.example.data.db.AccountDataVault
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidReminderKeysTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit()
      .putString("scope", AccountDataVault.scopeOf("alice@x.com")).commit()
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
  }

  @After
  fun tearDown() {
    AlarmNotificationScheduler.nowMillis = { System.currentTimeMillis() }
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
  }

  private fun armedCodes(): List<Int> =
    shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .map { shadowOf(it.operation).requestCode }

  @Test
  fun legacyCodesMatchAdvanceAndDayOfAlarmIds() {
    for (id in listOf(0L, 1L, 7L, 99_999L, 123_456_789L)) {
      assertEquals(AlarmNotificationScheduler.advanceAlarmId(id).toInt(), legacyRequestCode(ReminderKey.Advance(id)))
      assertEquals(AlarmNotificationScheduler.dayOfAlarmId(id).toInt(), legacyRequestCode(ReminderKey.DayOf(id)))
    }
    assertEquals(100011, legacyRequestCode(ReminderKey.Advance(1)))
    assertEquals(100012, legacyRequestCode(ReminderKey.DayOf(1)))
    assertEquals(100071, legacyRequestCode(ReminderKey.Advance(7)))
    assertEquals(100072, legacyRequestCode(ReminderKey.DayOf(7)))
  }

  @Test
  fun legacyCodesMatchMilestoneOffsets() {
    assertEquals(200005, legacyRequestCode(ReminderKey.UserMilestone(5)))
    assertEquals(400001, legacyRequestCode(ReminderKey.CoupleAnniversary))
    assertEquals(300100, legacyRequestCode(ReminderKey.LoveMilestoneThreeDays(100)))
    assertEquals(400100, legacyRequestCode(ReminderKey.LoveMilestoneOneDay(100)))
    assertEquals(500100, legacyRequestCode(ReminderKey.LoveMilestoneDayOf(100)))
    assertEquals(310007, legacyRequestCode(ReminderKey.LoveMilestoneThreeDays(10007)))
    assertEquals(410007, legacyRequestCode(ReminderKey.LoveMilestoneOneDay(10007)))
    assertEquals(510007, legacyRequestCode(ReminderKey.LoveMilestoneDayOf(10007)))
    assertEquals(42, legacyRequestCode(ReminderKey.Custom(42)))

    // Kiem tra that: ngay bat dau cach day 50 ngay nen moc 100 ngay con 50 ngay nua.
    val start = System.currentTimeMillis() - 50 * 86_400_000L
    LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(context, start, "", "Linh")
    val armed = armedCodes()
    assertTrue(legacyRequestCode(ReminderKey.LoveMilestoneThreeDays(100)) in armed)
    assertTrue(legacyRequestCode(ReminderKey.LoveMilestoneOneDay(100)) in armed)
    assertTrue(legacyRequestCode(ReminderKey.LoveMilestoneDayOf(100)) in armed)
  }

  @Test
  fun customCodeIsTheRowIdAndTruncatesLikeLegacy() {
    assertEquals(0, legacyRequestCode(ReminderKey.Custom(0)))
    assertEquals(Int.MAX_VALUE, legacyRequestCode(ReminderKey.Custom(Int.MAX_VALUE.toLong())))
    assertEquals(3_000_000_000L.toInt(), legacyRequestCode(ReminderKey.Custom(3_000_000_000L)))
  }

  @Test
  fun legacyAction_isReminderAlarmOnlyForCustom() {
    assertEquals(ReminderAlarmReceiver.ACTION_REMINDER_ALARM, legacyAction(ReminderKey.Custom(1)))
    val others = listOf(
      ReminderKey.Advance(1),
      ReminderKey.DayOf(1),
      ReminderKey.UserMilestone(1),
      ReminderKey.CoupleAnniversary,
      ReminderKey.LoveMilestoneThreeDays(1),
      ReminderKey.LoveMilestoneOneDay(1),
      ReminderKey.LoveMilestoneDayOf(1)
    )
    assertEquals(7, others.size)
    others.forEach { assertEquals(ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM, legacyAction(it)) }
  }

  @Test
  fun realisticIdsNeverCollide() {
    val loveIds = LoveAnniversaryMilestoneScheduler.STANDARD_DAY_MILESTONES.map { it.first.toLong() } +
      (10_001L..10_100L)
    assertEquals(12 + 100, loveIds.size)
    val keys = mutableListOf<ReminderKey>()
    for (id in 1L..999L) {
      keys += ReminderKey.Advance(id)
      keys += ReminderKey.DayOf(id)
      keys += ReminderKey.UserMilestone(id)
      keys += ReminderKey.Custom(id)
    }
    for (id in loveIds) {
      keys += ReminderKey.LoveMilestoneThreeDays(id)
      keys += ReminderKey.LoveMilestoneOneDay(id)
      keys += ReminderKey.LoveMilestoneDayOf(id)
    }
    keys += ReminderKey.CoupleAnniversary
    val codes = keys.map { legacyRequestCode(it) }
    assertEquals(keys.size, codes.toSet().size)
    assertFalse(500_001 in codes)
  }
}
