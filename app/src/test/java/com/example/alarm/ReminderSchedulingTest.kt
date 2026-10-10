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
import com.example.data.model.ReminderCadenceEntity
import com.example.data.seed.HolidayDates
import com.example.data.seed.VietnameseHolidays
import com.example.data.seed.WesternHolidays
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSchedulingTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private val originalZone: TimeZone = TimeZone.getDefault()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit()
      .putString("scope", AccountDataVault.scopeOf("alice@x.com")).commit()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
    pinNow(at(2026, 10, 9, 8))
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
  }

  @After
  fun tearDown() {
    AlarmNotificationScheduler.nowMillis = { System.currentTimeMillis() }
    AlarmNotificationScheduler.setAnniversaryNotificationsEnabled(context, true)
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    TimeZone.setDefault(originalZone)
    db.close()
  }

  private fun pinNow(millis: Long) {
    AlarmNotificationScheduler.nowMillis = { millis }
  }

  private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Long =
    Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute, 0) }.timeInMillis

  private fun calc(day: Int, month: Int, year: Int? = null, isAnnual: Boolean = true): Long =
    AlarmNotificationScheduler.calculateNextOccurrenceMillis(day, month, year, isAnnual)

  private fun ann(
    id: Long,
    dateText: String,
    daysBefore: Int = 3,
    enabled: Boolean = true,
    annual: Boolean = true
  ) = AnniversaryDateEntity(
    id = id,
    title = "Ngày yêu nhau",
    dateText = dateText,
    reminderDaysBefore = daysBefore,
    notificationEnabled = enabled,
    isAnnual = annual
  )

  private fun schedule(anniversary: AnniversaryDateEntity, disabledCadence: Set<String>): Boolean =
    AlarmNotificationScheduler.scheduleAnniversaryNotification(context, anniversary, disabledCadence)

  private fun requestCodes(): List<Int> =
    shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .map { shadowOf(it.operation).requestCode }
      .sorted()

  private fun shownCount(): Int =
    shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size

  private fun shownNotification(id: Int) =
    shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(id)

  // Date math

  @Test
  fun annualDateRollsForwardWhenItsDayHasPassed() {
    pinNow(at(2026, 10, 9, 8))
    assertEquals(at(2026, 10, 20), calc(20, 10))
    assertEquals(at(2027, 10, 1), calc(1, 10))
  }

  @Test
  fun sameDayBeforeNineStaysOnToday() {
    pinNow(at(2026, 10, 9, 8))
    assertEquals(at(2026, 10, 9), calc(9, 10))
  }

  @Test
  fun sameDayAfterNineRollsToNextYear() {
    pinNow(at(2026, 10, 9, 10))
    assertEquals(at(2027, 10, 9), calc(9, 10))
  }

  @Test
  fun leapDayFallsOnFebruary28InCommonYears() {
    pinNow(at(2026, 10, 9, 8))
    assertEquals(at(2027, 2, 28), calc(29, 2))
  }

  @Test
  fun leapDayLandsOnFebruary29InLeapYears() {
    pinNow(at(2027, 3, 1, 8))
    assertEquals(at(2028, 2, 29), calc(29, 2))
  }

  @Test
  fun dayThirtyOneClampsToThirtyInShorterMonths() {
    pinNow(at(2026, 10, 9, 8))
    assertEquals(at(2027, 4, 30), calc(31, 4))
  }

  @Test
  fun alarmIdsFollowTheAnniversaryScheme() {
    assertEquals(100071L, AlarmNotificationScheduler.advanceAlarmId(7))
    assertEquals(100072L, AlarmNotificationScheduler.dayOfAlarmId(7))
  }

  // Cadence and notification preferences

  @Test
  fun disabledNotificationSchedulesNothingAndClearsItsAlarms() {
    schedule(ann(7, "20/10"), emptySet())
    assertEquals(2, requestCodes().size)

    assertFalse(schedule(ann(7, "20/10", enabled = false), emptySet()))
    assertEquals(emptyList<Int>(), requestCodes())
  }

  @Test
  fun disabling7DaysRemovesOnlyTheAdvanceAlarm() {
    schedule(ann(7, "20/10", daysBefore = 7), emptySet())
    schedule(ann(7, "20/10", daysBefore = 7), setOf("7_days"))
    assertEquals(listOf(AlarmNotificationScheduler.dayOfAlarmId(7).toInt()), requestCodes())
  }

  @Test
  fun disablingExactDayRemovesOnlyTheDayOfAlarm() {
    schedule(ann(7, "20/10", daysBefore = 3), emptySet())
    schedule(ann(7, "20/10", daysBefore = 3), setOf("exact_day"))
    assertEquals(listOf(AlarmNotificationScheduler.advanceAlarmId(7).toInt()), requestCodes())
  }

  @Test
  fun onlyDisabledCadenceRowsCountAsOff() = runBlocking {
    db.inLoveDao().insertReminderCadences(
      listOf(
        ReminderCadenceEntity(key = "7_days", label = "Trước 7 ngày", isEnabled = true),
        ReminderCadenceEntity(key = "exact_day", label = "00:00 Ngày lễ", isEnabled = false)
      )
    )
    // "3_days" has no row at all, so it counts as enabled.
    assertEquals(setOf("exact_day"), AlarmNotificationScheduler.disabledCadenceKeys(db.inLoveDao()))
  }

  @Test
  fun twoDayAdvanceIsNotGatedByAnyCadenceRow() {
    schedule(ann(7, "20/10", daysBefore = 2), setOf("7_days", "3_days", "1_day", "exact_day"))
    assertEquals(listOf(AlarmNotificationScheduler.advanceAlarmId(7).toInt()), requestCodes())
  }

  @Test
  fun advanceAlarmIsClearedWhenDaysBeforeDropsToZero() {
    schedule(ann(7, "20/10", daysBefore = 3), emptySet())
    schedule(ann(7, "20/10", daysBefore = 0), emptySet())
    assertEquals(listOf(AlarmNotificationScheduler.dayOfAlarmId(7).toInt()), requestCodes())
  }

  // Daily morning check

  @Test
  fun morningCheckPostsTheDayOfNotificationForToday() = runBlocking {
    pinNow(at(2026, 10, 20, 8))
    val dao = db.inLoveDao()
    val id = dao.insertAnniversaryDate(ann(0, "20/10"))

    ReminderAlarmReceiver.checkAndNotifyTodayAnniversaries(context, dao)

    assertNotNull(shownNotification(AlarmNotificationScheduler.dayOfAlarmId(id).toInt()))
  }

  @Test
  fun morningCheckAndDayOfAlarmShareOneNotificationSlot() = runBlocking {
    pinNow(at(2026, 10, 20, 8))
    val dao = db.inLoveDao()
    val id = dao.insertAnniversaryDate(ann(0, "20/10"))
    val dayOfAlarm = Intent(context, ReminderAlarmReceiver::class.java).apply {
      action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM
      putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "Hôm nay")
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, AlarmNotificationScheduler.dayOfAlarmId(id))
    }

    ReminderAlarmReceiver().onReceive(context, dayOfAlarm)
    ReminderAlarmReceiver.checkAndNotifyTodayAnniversaries(context, dao)

    assertEquals(1, shownCount())
  }

  @Test
  fun morningCheckHonoursDisabledExactDay() = runBlocking {
    pinNow(at(2026, 10, 20, 8))
    val dao = db.inLoveDao()
    dao.insertAnniversaryDate(ann(0, "20/10"))
    dao.insertReminderCadences(listOf(ReminderCadenceEntity(key = "exact_day", label = "00:00 Ngày lễ", isEnabled = false)))

    ReminderAlarmReceiver.checkAndNotifyTodayAnniversaries(context, dao)

    assertEquals(0, shownCount())
  }

  @Test
  fun morningCheckSkipsAOneOffDateFromAnotherYearButShowsThisYear() = runBlocking {
    pinNow(at(2026, 10, 20, 8))
    val dao = db.inLoveDao()
    val oldYear = dao.insertAnniversaryDate(ann(0, "20/10/2025", annual = false))
    val thisYear = dao.insertAnniversaryDate(ann(0, "20/10/2026", annual = false))

    ReminderAlarmReceiver.checkAndNotifyTodayAnniversaries(context, dao)

    assertNull(shownNotification(AlarmNotificationScheduler.dayOfAlarmId(oldYear).toInt()))
    assertNotNull(shownNotification(AlarmNotificationScheduler.dayOfAlarmId(thisYear).toInt()))
  }

  @Test
  fun morningCheckSkipsDisabledNotifications() = runBlocking {
    pinNow(at(2026, 10, 20, 8))
    val dao = db.inLoveDao()
    dao.insertAnniversaryDate(ann(0, "20/10", enabled = false))

    ReminderAlarmReceiver.checkAndNotifyTodayAnniversaries(context, dao)

    assertEquals(0, shownCount())
  }

  // Reboot and timezone

  @Test
  fun rebootRestoresAFutureCustomReminderUnderItsRowId() = runBlocking {
    pinNow(at(2026, 10, 9, 8))
    val dao = db.inLoveDao()
    val rowId = dao.insertCustomReminder(
      CustomReminderEntity(
        title = "Hẹn hò",
        dateText = "20/10",
        details = "Tối nay",
        daysRemainingText = "",
        alarmTimeMillis = at(2026, 10, 20, 19),
        alarmTimeFormatted = "19:00 - 20/10/2026"
      )
    )

    AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(context, dao)

    val restored = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .single { shadowOf(it.operation).requestCode == rowId.toInt() }
    assertEquals(ReminderAlarmReceiver.ACTION_REMINDER_ALARM, shadowOf(restored.operation).savedIntent.action)
  }

  @Test
  fun rebootSkipsACustomReminderWhoseTimeHasPassed() = runBlocking {
    pinNow(at(2026, 10, 9, 8))
    val dao = db.inLoveDao()
    val rowId = dao.insertCustomReminder(
      CustomReminderEntity(
        title = "Hẹn hò",
        dateText = "01/10",
        details = "",
        daysRemainingText = "",
        alarmTimeMillis = at(2026, 10, 1, 19),
        alarmTimeFormatted = "19:00 - 01/10/2026"
      )
    )

    AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(context, dao)

    assertFalse(requestCodes().contains(rowId.toInt()))
  }

  @Test
  fun nineAmFollowsTheDeviceZoneWhenItChanges() {
    pinNow(at(2026, 10, 9, 8))
    assertEquals(Instant.parse("2026-10-20T02:00:00Z").toEpochMilli(), calc(20, 10))

    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

    assertEquals(Instant.parse("2026-10-20T13:00:00Z").toEpochMilli(), calc(20, 10))
  }

  @Test
  fun bootAndClockActionsTriggerARescheduleAndOthersDoNot() {
    listOf(
      Intent.ACTION_BOOT_COMPLETED,
      "android.intent.action.QUICKBOOT_POWERON",
      Intent.ACTION_TIMEZONE_CHANGED,
      "android.intent.action.TIME_SET"
    ).forEach { assertTrue(it, BootCompletedReceiver.isRescheduleAction(it)) }

    assertFalse(BootCompletedReceiver.isRescheduleAction(Intent.ACTION_SCREEN_ON))
    assertFalse(BootCompletedReceiver.isRescheduleAction(null))
  }

  // Exact-alarm access

  @Test
  fun exactAlarmAllowedFollowsTheSdkAndTheExactPermission() {
    assertFalse(AlarmNotificationScheduler.exactAlarmAllowed(34, false))
    assertTrue(AlarmNotificationScheduler.exactAlarmAllowed(34, true))
    assertTrue(AlarmNotificationScheduler.exactAlarmAllowed(30, false))
  }

  @Test
  fun schedulingStillSucceedsWithoutExactAlarmAccess() {
    ShadowAlarmManager.setCanScheduleExactAlarms(false)

    val scheduled = AlarmNotificationScheduler.scheduleAlarm(context, 42L, "Hẹn hò", "Tối nay", at(2026, 10, 20, 19))

    assertTrue(scheduled)
    assertEquals(listOf(42), requestCodes())
  }

  // Holidays

  @Test
  fun lunarHolidayMovesToItsNextVerifiedYearAndStopsWhenNoneIsVerified() {
    val tet = VietnameseHolidays.lunarHolidays.first { it.titleVi == "Tết Nguyên Đán" }
    val vuLan = VietnameseHolidays.lunarHolidays.first { it.titleVi == "Lễ Vu Lan" }
    assertEquals(LocalDate.of(2027, 2, 6), HolidayDates.nextLunar(tet, LocalDate.of(2026, 10, 9)))
    assertNull(HolidayDates.nextLunar(vuLan, LocalDate.of(2026, 10, 9)))
  }

  @Test
  fun ruleBasedHolidayRecomputesForTheNextYear() {
    val mothersDay = WesternHolidays.ruleBasedHolidays.first { it.titleEn == "Mother's Day" }
    assertEquals(LocalDate.of(2027, 5, 9), HolidayDates.nextRuleBased(mothersDay, LocalDate.of(2026, 10, 9)))
  }

  @Test
  fun refreshRewritesALegacyLunarRowAndNeverRevivesOrAddsRows() = runBlocking {
    val dao = db.inLoveDao()
    val legacyId = dao.insertAnniversaryDate(
      AnniversaryDateEntity(title = "🧧 Tết Nguyên Đán", dateText = "17/02/2026", type = "CUSTOM", isAnnual = true)
    )
    val tombstoneId = dao.insertAnniversaryDate(
      AnniversaryDateEntity(title = "🌕 Rằm Tháng Giêng", dateText = "03/03/2026", type = "CUSTOM", isAnnual = false, deleted = true)
    )

    HolidayDates.refreshExisting(dao, LocalDate.of(2026, 10, 9))

    val rows = dao.getAnniversaryDatesList()
    assertEquals(1, rows.size)
    val tet = rows.single { it.id == legacyId }
    assertEquals("06/02/2027", tet.dateText)
    assertFalse(tet.isAnnual)
    val tombstone = db.openHelper.readableDatabase
      .query("SELECT dateText, deleted FROM anniversary_dates WHERE id = $tombstoneId")
      .use { c -> c.moveToFirst(); c.getString(0) to c.getInt(1) }
    assertEquals("03/03/2026" to 1, tombstone)
  }

  @Test
  fun refreshMovesALegacyRuleBasedRowToItsNextYear() = runBlocking {
    val dao = db.inLoveDao()
    val legacyId = dao.insertAnniversaryDate(
      AnniversaryDateEntity(title = "💐 Mother's Day", dateText = "10/05/2026", type = "CUSTOM", isAnnual = true)
    )

    HolidayDates.refreshExisting(dao, LocalDate.of(2026, 10, 9))

    val mothersDay = dao.getAnniversaryDatesList().single { it.id == legacyId }
    assertEquals("09/05/2027", mothersDay.dateText)
    assertFalse(mothersDay.isAnnual)
  }

  // Custom reminder persistence

  @Test
  fun setCustomReminderAlarmPersistsMillisAndFormattedText() = runBlocking {
    val dao = db.inLoveDao()
    val id = dao.insertCustomReminder(
      CustomReminderEntity(title = "Hẹn hò", dateText = "20/10", details = "", daysRemainingText = "")
    )
    val millis = at(2026, 10, 20, 19)

    dao.setCustomReminderAlarm(id, millis, "19:00 - 20/10/2026")

    val saved = dao.getCustomRemindersList().single { it.id == id }
    assertEquals(millis, saved.alarmTimeMillis)
    assertEquals("19:00 - 20/10/2026", saved.alarmTimeFormatted)
  }

  // Device-wide anniversary switch

  @Test
  fun anniversaryNotificationsAreOnByDefaultAndTheSwitchPersists() {
    assertTrue(AlarmNotificationScheduler.anniversaryNotificationsEnabled(context))

    AlarmNotificationScheduler.setAnniversaryNotificationsEnabled(context, false)
    assertFalse(AlarmNotificationScheduler.anniversaryNotificationsEnabled(context))

    AlarmNotificationScheduler.setAnniversaryNotificationsEnabled(context, true)
    assertTrue(AlarmNotificationScheduler.anniversaryNotificationsEnabled(context))
  }

  @Test
  fun switchOffSilencesAnAnniversaryAlarmAtDeliveryButNotACustomReminder() {
    AlarmNotificationScheduler.setAnniversaryNotificationsEnabled(context, false)

    ReminderAlarmReceiver().onReceive(context, Intent(context, ReminderAlarmReceiver::class.java).apply {
      action = ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, 42L)
      putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "Ngày yêu nhau")
    })
    assertEquals(0, shownCount())

    ReminderAlarmReceiver().onReceive(context, Intent(context, ReminderAlarmReceiver::class.java).apply {
      action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, 43L)
      putExtra(ReminderAlarmReceiver.EXTRA_TITLE, "Hẹn hò")
    })
    assertEquals(1, shownCount())
  }

  @Test
  fun switchOffSilencesTheMorningNotice() = runBlocking {
    pinNow(at(2026, 10, 9, 8))
    AlarmNotificationScheduler.setAnniversaryNotificationsEnabled(context, false)
    val dao = db.inLoveDao()
    dao.insertAnniversaryDate(ann(0, "09/10"))

    ReminderAlarmReceiver.checkAndNotifyTodayAnniversaries(context, dao)

    assertEquals(0, shownCount())
  }
}
