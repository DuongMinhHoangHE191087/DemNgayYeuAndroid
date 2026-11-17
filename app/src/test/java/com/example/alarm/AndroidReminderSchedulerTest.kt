package com.example.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.plan.ReminderCoverage
import com.example.alarm.plan.ReminderKey
import com.example.alarm.plan.ReminderTestKind
import com.example.data.db.AccountDataVault
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.CustomReminderEntity
import com.example.data.model.MilestoneEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.RelationshipStatus
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocaleManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class AndroidReminderAlarm(val code: Int, val time: Long, val action: String, val message: String)

private data class AndroidReminderShown(val title: String, val text: String)

/** Giàn thử cho ReminderCommandRunner: ghi lại thứ tự chạy, có chốt chặn và lỗi giả. */
private class AndroidReminderRunnerRig(scope: CoroutineScope) {
  val events = mutableListOf<String>()
  var syncGate: CompletableDeferred<Unit>? = null
  var syncFailure: Throwable? = null
  var cancelFailure: Throwable? = null

  val runner = ReminderCommandRunner(
    scope = scope,
    onSync = {
      syncFailure?.let { syncFailure = null; throw it }
      events += "sync"
      val gate = syncGate
      syncGate = null
      gate?.await()
    },
    onCancelAll = {
      cancelFailure?.let { cancelFailure = null; throw it }
      events += "cancel"
    },
    onError = { events += "error:${it.message}" }
  )
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidReminderSchedulerTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private val originalZone: TimeZone = TimeZone.getDefault()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var milestoneProvider: () -> LoveMilestoneArgs? = { null }

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
    scope.cancel()
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

  private fun newScheduler(): AndroidReminderScheduler =
    AndroidReminderScheduler(context, db.inLoveDao(), { milestoneProvider() }, scope)

  private fun alarmManager(): AlarmManager = context.getSystemService(AlarmManager::class.java)

  private fun requestCodes(): List<Int> =
    shadowOf(alarmManager()).scheduledAlarms.map { shadowOf(it.operation).requestCode }.sorted()

  private fun snapshot(): List<AndroidReminderAlarm> =
    shadowOf(alarmManager()).scheduledAlarms.toList()
      .map {
        AndroidReminderAlarm(
          shadowOf(it.operation).requestCode,
          it.triggerAtTime,
          shadowOf(it.operation).savedIntent.action.orEmpty(),
          shadowOf(it.operation).savedIntent.getStringExtra(ReminderAlarmReceiver.EXTRA_MESSAGE).orEmpty()
        )
      }
      .sortedWith(compareBy({ it.code }, { it.time }, { it.action }))

  private fun clearAlarms() {
    shadowOf(alarmManager()).scheduledAlarms.toList().forEach { alarmManager().cancel(it.operation!!) }
  }

  private fun notificationManager(): NotificationManager =
    context.getSystemService(NotificationManager::class.java)

  private fun shown(id: Int): AndroidReminderShown {
    val extras = shadowOf(notificationManager()).getNotification(id).extras
    return AndroidReminderShown(
      extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
      extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    )
  }

  private fun clearNotifications() {
    notificationManager().cancelAll()
    assertTrue(shadowOf(notificationManager()).allNotifications.isEmpty())
  }

  private fun awaitRuns(scheduler: AndroidReminderScheduler, count: Int) {
    runBlocking { withTimeout(10_000) { scheduler.completedRuns.first { it >= count } } }
  }

  private fun loveStart(): Long = System.currentTimeMillis() - 50 * 86_400_000L

  /** Gieo mot bo du lieu day du; tra ve id cua loi nhac tuy chinh. */
  private fun seedAll(): Long = runBlocking {
    val dao = db.inLoveDao()
    dao.insertAnniversaryDate(
      AnniversaryDateEntity(id = 1, title = "Ngày yêu nhau", dateText = "20/10", reminderDaysBefore = 3, isAnnual = true)
    )
    dao.insertAnniversaryDate(
      AnniversaryDateEntity(id = 2, title = "Sinh nhật", dateText = "25/12", reminderDaysBefore = 7, isAnnual = true)
    )
    dao.insertMilestone(
      MilestoneEntity(
        title = "Hẹn hò",
        dateText = "30/10/2026",
        subtitle = "",
        categoryTag = "",
        secondaryTag = "",
        imageUrl = "",
        daysRemaining = 21,
        isUserCreated = true
      )
    )
    dao.insertCoupleProfile(
      CoupleProfileEntity(
        partner1Name = "An",
        partner1Birthday = "",
        partner1ProfilePicture = "",
        partner2Name = "Linh",
        partner2Birthday = "",
        partner2ProfilePicture = "",
        anniversaryDate = "14/02/2024"
      )
    )
    dao.insertCustomReminder(
      CustomReminderEntity(
        title = "Đặt bàn",
        dateText = "12/10/2026",
        details = "Tối nay",
        daysRemainingText = "",
        alarmTimeMillis = at(2026, 10, 12, 9)
      )
    )
  }

  private fun legacyArm(): Int = runBlocking {
    AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(context, db.inLoveDao())
  }

  // Runner (logic thuan, khong Android)

  private fun TestScope.rig(): AndroidReminderRunnerRig = AndroidReminderRunnerRig(backgroundScope)

  @Test
  fun runner_coalescesManySyncRequestsIntoOneRun() = runTest {
    val rig = rig()
    repeat(5) { rig.runner.requestSync() }
    runCurrent()
    assertEquals(listOf("sync"), rig.events)
  }

  @Test
  fun runner_requestDuringARun_queuesExactlyOneMoreRun() = runTest {
    val rig = rig()
    val gate = CompletableDeferred<Unit>()
    rig.syncGate = gate
    rig.runner.requestSync()
    runCurrent()
    assertEquals(listOf("sync"), rig.events)

    repeat(3) { rig.runner.requestSync() }
    gate.complete(Unit)
    runCurrent()
    assertEquals(listOf("sync", "sync"), rig.events)
    runCurrent()
    assertEquals(listOf("sync", "sync"), rig.events)
  }

  @Test
  fun runner_syncCancelSync_runsInThatOrder() = runTest {
    val rig = rig()
    rig.runner.requestSync()
    rig.runner.requestCancelAll()
    rig.runner.requestSync()
    runCurrent()
    assertEquals(listOf("sync", "cancel", "sync"), rig.events)
  }

  @Test
  fun runner_syncSyncCancel_runsSyncThenCancel() = runTest {
    val rig = rig()
    rig.runner.requestSync()
    rig.runner.requestSync()
    rig.runner.requestCancelAll()
    runCurrent()
    assertEquals(listOf("sync", "cancel"), rig.events)
  }

  @Test
  fun runner_cancelSyncSync_runsCancelThenSync() = runTest {
    val rig = rig()
    rig.runner.requestCancelAll()
    rig.runner.requestSync()
    rig.runner.requestSync()
    runCurrent()
    assertEquals(listOf("cancel", "sync"), rig.events)
  }

  @Test
  fun runner_cancelIsNeverCoalesced() = runTest {
    val rig = rig()
    repeat(3) { rig.runner.requestCancelAll() }
    runCurrent()
    assertEquals(listOf("cancel", "cancel", "cancel"), rig.events)
  }

  @Test
  fun runner_throwingBody_doesNotKillTheCollector() = runTest {
    val rig = rig()
    rig.syncFailure = IllegalStateException("boom")
    rig.runner.requestSync()
    runCurrent()
    assertEquals(listOf("error:boom"), rig.events)

    rig.runner.requestSync()
    runCurrent()
    assertEquals(listOf("error:boom", "sync"), rig.events)
    assertEquals(2, rig.runner.completed.value)
  }

  @Test
  fun runner_cancellationException_endsTheCollector_andIsNotReportedAsError() = runTest {
    val rig = rig()
    rig.syncFailure = CancellationException("stop")
    rig.runner.requestSync()
    runCurrent()
    assertTrue(rig.events.isEmpty())

    rig.runner.requestSync()
    runCurrent()
    assertTrue(rig.events.isEmpty())
  }

  @Test
  fun runner_scopeCancel_stopsTheCollector() = runTest {
    val job = Job()
    val rig = AndroidReminderRunnerRig(backgroundScope + job)
    rig.runner.requestSync()
    runCurrent()
    assertEquals(listOf("sync"), rig.events)

    job.cancel()
    rig.runner.requestSync()
    runCurrent()
    assertEquals(listOf("sync"), rig.events)
  }

  @Test
  fun runner_cancelAllAndWait_returnsOnlyAfterTheBodyRan() = runTest {
    val rig = rig()
    val waiter = launch { rig.runner.cancelAllAndWait() }
    assertTrue(rig.events.isEmpty())
    assertFalse(waiter.isCompleted)

    runCurrent()
    assertEquals(listOf("cancel"), rig.events)
    assertTrue(waiter.isCompleted)
  }

  @Test
  fun runner_cancelAllAndWait_returnsEvenWhenTheBodyThrows() = runTest {
    val rig = rig()
    rig.cancelFailure = IllegalStateException("bad cancel")
    val waiter = launch { rig.runner.cancelAllAndWait() }
    runCurrent()
    assertTrue(waiter.isCompleted)
    assertFalse(waiter.isCancelled)
    assertEquals(listOf("error:bad cancel"), rig.events)
  }

  @Test
  fun runner_cancelAllAndWait_resetsTheSyncFlag_soALaterSyncStillRuns() = runTest {
    val rig = rig()
    rig.runner.requestSync()
    // UNDISPATCHED: cancelAllAndWait chay den done.await() ngay, truoc khi requestSync thu hai.
    val waiter = launch(start = CoroutineStart.UNDISPATCHED) { rig.runner.cancelAllAndWait() }
    rig.runner.requestSync()
    runCurrent()
    assertEquals(listOf("sync", "cancel", "sync"), rig.events)
    assertTrue(waiter.isCompleted)
  }

  // Scheduler (Robolectric, ham that, Room that)

  @Test
  fun requestResyncSchedulesTheSameAlarmsAsTheLegacyEntryPoint() {
    seedAll()
    val legacyCount = legacyArm()
    val expected = snapshot()
    assertTrue(expected.isNotEmpty())
    assertTrue(expected.any { it.code == 500001 })

    clearAlarms()
    assertTrue(snapshot().isEmpty())

    val scheduler = newScheduler()
    scheduler.requestResync()
    awaitRuns(scheduler, 1)
    assertEquals(expected, snapshot())
    assertEquals(ReminderCoverage(legacyCount, 0, null), scheduler.coverage.value)
  }

  @Test
  fun resyncWithMilestoneArgs_alsoSchedulesTheLoveMilestones() {
    seedAll()
    val start = loveStart()
    val legacyDb = legacyArm()
    val legacyLove = LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(context, start, "", "Linh")
    val expected = snapshot()
    val codes = expected.map { it.code }
    assertTrue(300100 in codes && 400100 in codes && 500100 in codes)

    clearAlarms()
    milestoneProvider = { LoveMilestoneArgs(start, "", "Linh") }
    val scheduler = newScheduler()
    runBlocking { scheduler.resyncNow() }
    assertEquals(expected, snapshot())
    assertEquals(ReminderCoverage(legacyDb + legacyLove, 0, null), scheduler.coverage.value)
  }

  @Test
  fun resyncWithMilestoneArgs_forwardsTheDateTextWhenTheMillisAreUnset() {
    seedAll()
    val text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(loveStart()))
    legacyArm()
    LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(context, 0L, text, "Linh")
    val expected = snapshot()
    assertTrue(500100 in expected.map { it.code })

    clearAlarms()
    milestoneProvider = { LoveMilestoneArgs(0L, text, "Linh") }
    runBlocking { newScheduler().resyncNow() }
    assertEquals(expected, snapshot())
  }

  @Test
  fun resync_providerThrows_isLoggedNotCrashing_andCoverageUnchanged() {
    seedAll()
    val legacyCount = legacyArm()
    clearAlarms()

    milestoneProvider = { throw IllegalStateException("no args") }
    val scheduler = newScheduler()
    scheduler.requestResync()
    awaitRuns(scheduler, 1)
    assertEquals(ReminderCoverage(0, 0, null), scheduler.coverage.value)
    assertTrue(500001 in requestCodes())
    assertTrue(100011 in requestCodes())
    val errors = ShadowLog.getLogsForTag("AndroidReminderScheduler").filter { it.type == Log.ERROR }
    assertEquals(errors.toString(), 1, errors.size)
    assertEquals("no args", errors.single().throwable?.message)

    milestoneProvider = { null }
    scheduler.requestResync()
    awaitRuns(scheduler, 2)
    assertEquals(ReminderCoverage(legacyCount, 0, null), scheduler.coverage.value)
  }

  @Test
  fun cancelAllClearsEveryScheduledAlarm() {
    val customId = seedAll()
    legacyArm()
    LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(context, loveStart(), "", "Linh")
    // Moc ma dong ho that khong tao ra duoc: arm truc tiep voi ma co dinh.
    val future = at(2099, 1, 1)
    for (code in listOf(310007L, 410007L, 510007L, 302000L, 402000L, 502000L)) {
      AlarmNotificationScheduler.scheduleAlarm(
        context, code, "t", "m", future, ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM
      )
    }
    val armed = requestCodes()
    val expectedCodes = listOf(
      100011, 100012, 100021, 100022, 200001, 400001, 500001, customId.toInt(),
      300100, 400100, 500100, 310007, 410007, 510007, 302000, 402000, 502000
    )
    expectedCodes.forEach { assertTrue("missing $it in $armed", it in armed) }

    val scheduler = newScheduler()
    runBlocking { scheduler.resyncNow() }
    assertTrue(scheduler.coverage.value.scheduled > 0)

    runBlocking { scheduler.cancelEverything() }
    assertTrue(requestCodes().toString(), shadowOf(alarmManager()).scheduledAlarms.isEmpty())
    assertEquals(ReminderCoverage(0, 0, null), scheduler.coverage.value)
  }

  @Test
  fun cancelAll_coversTheFirstAndLastAnnualMilestoneYears() {
    // Nam yeu thu 1 (id 10001) va thu 100 (id 10100) la hai dau cua vong cancel; arm truc tiep voi ma co dinh.
    val future = at(2099, 1, 1)
    val codes = listOf(310001, 410001, 510001, 310100, 410100, 510100)
    for (code in codes) {
      AlarmNotificationScheduler.scheduleAlarm(
        context, code.toLong(), "t", "m", future, ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM
      )
    }
    assertEquals(codes.sorted(), requestCodes())

    runBlocking { newScheduler().cancelEverything() }
    assertTrue(requestCodes().toString(), shadowOf(alarmManager()).scheduledAlarms.isEmpty())
  }

  @Test
  fun cancelAllNow_hasCancelledBeforeItReturns() {
    seedAll()
    legacyArm()
    assertTrue(shadowOf(alarmManager()).scheduledAlarms.isNotEmpty())

    val scheduler = newScheduler()
    runBlocking { scheduler.cancelAllNow() }
    assertTrue(shadowOf(alarmManager()).scheduledAlarms.isEmpty())
  }

  @Test
  fun cancelAll_thenRequestResync_endsArmed() {
    seedAll()
    legacyArm()
    val expected = snapshot()
    assertTrue(expected.isNotEmpty())

    val scheduler = newScheduler()
    scheduler.cancelAll()
    scheduler.requestResync()
    awaitRuns(scheduler, 2)
    assertEquals(expected, snapshot())
  }

  @Test
  fun coupleAnniversary_messageCountsLoveDaysAtFireTime_andIsCancelledOnceTheCoupleEnds() {
    // The text follows the app language; the assertion below reads the Vietnamese wording.
    LocaleManager.saveLanguage(context, AppLanguage.VI)
    seedAll()
    legacyArm()
    assertTrue(400001 in requestCodes())
    // 14/02/2024 to the 14/02/2027 09:00 fire is 1096 full days; the count includes day 1.
    val message = snapshot().single { it.code == 400001 }.message
    assertTrue(message, message.contains("Chặng đường 1097 ngày"))

    runBlocking {
      db.inLoveDao().insertOnlineRelationship(
        OnlineRelationshipEntity(relationshipId = "R1", user1 = "A", user2 = "B", startDate = 0L, status = RelationshipStatus.TERMINATED)
      )
    }
    legacyArm()
    assertFalse(400001 in requestCodes())
  }

  @Test
  fun cancelByKeyRemovesOnlyThatPendingIntent() {
    val customId = runBlocking {
      val dao = db.inLoveDao()
      dao.insertAnniversaryDate(
        AnniversaryDateEntity(id = 1, title = "Ngày yêu nhau", dateText = "20/10", reminderDaysBefore = 3, isAnnual = true)
      )
      dao.insertCustomReminder(
        CustomReminderEntity(
          title = "Đặt bàn",
          dateText = "12/10/2026",
          details = "Tối nay",
          daysRemainingText = "",
          alarmTimeMillis = at(2026, 10, 12, 9)
        )
      )
    }
    legacyArm()
    val before = requestCodes()
    assertTrue(100011 in before && 100012 in before && customId.toInt() in before)

    val scheduler = newScheduler()
    scheduler.cancel(listOf(ReminderKey.Advance(1)))
    assertEquals(before - 100011, requestCodes())

    scheduler.cancel(listOf(ReminderKey.DayOf(1), ReminderKey.Custom(customId)))
    assertEquals(before - 100011 - 100012 - customId.toInt(), requestCodes())

    val rest = requestCodes()
    scheduler.cancel(emptyList())
    scheduler.cancel(listOf(ReminderKey.Advance(99), ReminderKey.LoveMilestoneDayOf(12345)))
    assertEquals(rest, requestCodes())
  }

  @Test
  fun cancel_usesTheKeysOwnAction() {
    val future = at(2026, 10, 12, 9)
    AlarmNotificationScheduler.scheduleAlarm(
      context, 7L, "t", "m", future, ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM
    )
    AlarmNotificationScheduler.scheduleAlarm(
      context, 7L, "t", "m", future, ReminderAlarmReceiver.ACTION_REMINDER_ALARM
    )
    assertEquals(listOf(7, 7), requestCodes())

    newScheduler().cancel(listOf(ReminderKey.Custom(7)))

    val left = shadowOf(alarmManager()).scheduledAlarms.toList()
    assertEquals(1, left.size)
    assertEquals(ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM, shadowOf(left[0].operation).savedIntent.action)
  }

  @Test
  fun sendTest_anniversary_matchesTheLegacyInstantTest() {
    AlarmNotificationScheduler.triggerInstantTest(context)
    val legacy = shown(9999)
    assertTrue(legacy.title != "null" && legacy.text != "null")
    clearNotifications()

    newScheduler().sendTest(ReminderTestKind.ANNIVERSARY)
    assertEquals(legacy, shown(9999))
  }

  @Test
  fun sendTest_milestone_matchesTheLegacyInstantMilestone() {
    LoveAnniversaryMilestoneScheduler.triggerInstantTestMilestone(context)
    val legacyDefault = shown(8888)
    clearNotifications()
    LoveAnniversaryMilestoneScheduler.triggerInstantTestMilestone(context, partnerName = "Linh")
    val legacyLinh = shown(8888)
    clearNotifications()
    assertNotEquals(legacyDefault.text, legacyLinh.text)

    val scheduler = newScheduler()
    milestoneProvider = { null }
    scheduler.sendTest(ReminderTestKind.MILESTONE)
    assertEquals(legacyDefault, shown(8888))
    clearNotifications()

    milestoneProvider = { LoveMilestoneArgs(loveStart(), "", "Linh") }
    scheduler.sendTest(ReminderTestKind.MILESTONE)
    assertEquals(legacyLinh, shown(8888))
  }

  @Test
  fun sendTest_milestone_blankPartnerShowsTheDefaultName() {
    LoveAnniversaryMilestoneScheduler.triggerInstantTestMilestone(context)
    val legacyDefault = shown(8888)
    clearNotifications()

    milestoneProvider = { LoveMilestoneArgs(loveStart(), "", "") }
    newScheduler().sendTest(ReminderTestKind.MILESTONE)
    assertEquals(legacyDefault, shown(8888))
  }

  @Test
  fun sendTest_custom_postsTheViewModelDefaultsOnId9999() {
    val scheduler = newScheduler()
    scheduler.sendTest(ReminderTestKind.ANNIVERSARY)
    val anniversary = shown(9999)
    clearNotifications()

    scheduler.sendTest(ReminderTestKind.CUSTOM)
    val custom = shown(9999)
    assertEquals("Thử nghiệm báo thức tình yêu ❤️", custom.title)
    assertEquals("Chuông và thông báo kỷ niệm hoạt động rất tốt!", custom.text)
    assertNotEquals(anniversary.title, custom.title)
  }

  @Test
  fun isExactDeliveryAvailable_followsTheAlarmManagerPermission() {
    val scheduler = newScheduler()
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    assertTrue(scheduler.isExactDeliveryAvailable())
    ShadowAlarmManager.setCanScheduleExactAlarms(false)
    assertFalse(scheduler.isExactDeliveryAvailable())
  }

  @Test
  fun coverage_startsEmpty() {
    assertEquals(ReminderCoverage(0, 0, null), newScheduler().coverage.value)
  }
}
