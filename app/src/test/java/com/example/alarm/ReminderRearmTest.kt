package com.example.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.plan.LoveMilestoneRules
import com.example.alarm.plan.ReminderKind
import com.example.alarm.plan.ReminderTextArgs
import com.example.alarm.plan.ReminderTexts
import com.example.data.db.AccountDataVault
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.RelationshipStatus
import com.example.data.model.UserAccountEntity
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Already-armed alarms carry this exact string across app updates, so it is spelled out instead of read from the receiver.
private const val DAILY_ACTION = "com.example.inlove.ACTION_DAILY_ANNIVERSARY_CHECK"

// Drives the REAL receivers against an in-memory Room database and locks the order of the daily check:
// 1 re-arm tomorrow, 2 refresh stale holiday rows, 3 notify today's anniversaries, 4 re-arm everything.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderRearmTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private val originalZone: TimeZone = TimeZone.getDefault()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    // The receivers write their texts in the app language; the assertions below read the Vietnamese wording.
    LocaleManager.saveLanguage(context, AppLanguage.VI)
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit()
      .putString("scope", AccountDataVault.scopeOf("alice@x.com")).commit()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    injectDatabase(db)
  }

  @After
  fun tearDown() {
    AlarmNotificationScheduler.nowMillis = { System.currentTimeMillis() }
    LoveAnniversaryMilestoneScheduler.nowMillis = { System.currentTimeMillis() }
    schedulerScope.cancel()
    injectDatabase(null)
    TimeZone.setDefault(originalZone)
    db.close()
  }

  // The receivers call AppDatabase.getDatabase(context); setting its INSTANCE field is how they get the in-memory test database.
  // D-42 changes how the receivers obtain the DAO: this is the ONE place to adapt (nothing else here may mention INSTANCE).
  private fun injectDatabase(value: AppDatabase?) {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
    field.isAccessible = true
    field.set(null, value)
  }

  private fun atSec(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
    Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute, second) }.timeInMillis

  // A real BroadcastReceiver.PendingResult, so goAsync() works and we can wait for finish() (the deterministic "the coroutine is done" signal).
  private fun newPendingResult(): BroadcastReceiver.PendingResult {
    val create = Class.forName("org.robolectric.shadows.ShadowBroadcastPendingResult").getDeclaredMethod(
      "create", Int::class.javaPrimitiveType, String::class.java, Bundle::class.java, Boolean::class.javaPrimitiveType
    )
    create.isAccessible = true
    return create.invoke(null, 0, null, null, false) as BroadcastReceiver.PendingResult
  }

  private fun attach(receiver: BroadcastReceiver, pending: BroadcastReceiver.PendingResult) {
    BroadcastReceiver::class.java.getMethod("setPendingResult", BroadcastReceiver.PendingResult::class.java)
      .invoke(receiver, pending)
  }

  // Guava is not on the compile classpath, so the future is read by reflection.
  private fun futureOf(pending: BroadcastReceiver.PendingResult): Future<*> {
    val shadow: Any = shadowOf(pending)
    return shadow.javaClass.getMethod("getFuture").invoke(shadow) as Future<*>
  }

  // Delivers the broadcast to the REAL receiver, waits until it has finished (bounded), returns whether it went async (goAsync()).
  private fun deliver(receiver: BroadcastReceiver, action: String, on: Context = context): Boolean {
    val pending = newPendingResult()
    attach(receiver, pending)
    shadowOf(receiver).onReceive(on, Intent(action), AtomicBoolean())
    val wentAsync = shadowOf(receiver).wentAsync()
    futureOf(pending).get(20, TimeUnit.SECONDS)
    return wentAsync
  }

  // Sorted by request code, so a duplicated alarm or a missing one makes assertEquals fail.
  private fun alarms(): List<Pair<Int, Long>> =
    shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .map { shadowOf(it.operation).requestCode to it.triggerAtTime }
      .sortedBy { it.first }

  private fun notifications(): List<Pair<Int, String?>> =
    context.getSystemService(NotificationManager::class.java).activeNotifications
      .map { it.id to it.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() }
      .sortedBy { it.first }

  // A failure the receiver caught leaves an ERROR log carrying its throwable; an uncaught one would crash the process instead.
  private fun loggedAnError(): Boolean = ShadowLog.getLogs().any { it.type == Log.ERROR && it.throwable != null }

  // Request codes are persisted by AlarmManager across app updates, so the tests spell them out as literals.
  private fun at9(year: Int, month: Int, day: Int): Long = atSec(year, month, day, 9, 0, 0)

  private val nowDaily get() = atSec(2027, 5, 10, 9, 0, 5)
  private val nowBoot get() = atSec(2026, 10, 9, 8, 0, 0)

  // A legacy holiday row whose date went stale (it still says today's day/month), plus a real anniversary that falls today.
  private fun seedStaleScenario() = runBlocking {
    db.inLoveDao().insertAnniversaryDate(
      AnniversaryDateEntity(title = "💐 Mother's Day", dateText = "10/05/2026", type = "CUSTOM", isAnnual = true)
    )
    db.inLoveDao().insertAnniversaryDate(
      AnniversaryDateEntity(title = "Ngày yêu nhau", dateText = "10/05/2024", isAnnual = true, reminderDaysBefore = 3)
    )
  }

  private fun runDailyCheck(): Boolean {
    AlarmNotificationScheduler.nowMillis = { nowDaily }
    return deliver(ReminderAlarmReceiver(), DAILY_ACTION)
  }

  private fun seedBootScenario() = runBlocking {
    db.inLoveDao().insertAnniversaryDate(
      AnniversaryDateEntity(title = "Ngày yêu nhau", dateText = "25/12/2026", isAnnual = true, reminderDaysBefore = 3)
    )
  }

  @Test
  fun dailyCheckKeepsTomorrowsDailyAlarmArmed() {
    seedStaleScenario()

    assertTrue(runDailyCheck())

    val daily = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .filter { shadowOf(it.operation).requestCode == 500001 }
    assertEquals(1, daily.size)
    assertEquals(at9(2027, 5, 11), daily.single().triggerAtTime)
    assertEquals(DAILY_ACTION, shadowOf(daily.single().operation).savedIntent.action)
  }

  @Test
  fun dailyCheckRefreshesStaleHolidayDates() {
    seedStaleScenario()

    assertTrue(runDailyCheck())

    val rows = runBlocking { db.inLoveDao().getAnniversaryDatesList() }.sortedBy { it.id }
    assertEquals("💐 Mother's Day", rows[0].title)
    assertEquals("14/05/2028", rows[0].dateText)
    assertFalse(rows[0].isAnnual)
    assertEquals("10/05/2024", rows[1].dateText)
    assertTrue(rows[1].isAnnual)
    assertFalse(
      "the refresh must run BEFORE the notify step: otherwise the stale annual row still says 10/05 and posts 100012",
      notifications().any { it.first == 100012 }
    )
  }

  @Test
  fun dailyCheckPostsTodaysAnniversaryNotification() {
    seedStaleScenario()

    assertTrue(runDailyCheck())

    assertEquals(listOf(100022 to "🎉 Hôm Nay: Ngày yêu nhau!"), notifications())
  }

  @Test
  fun dailyCheckReschedulesAllAnniversaries() {
    seedStaleScenario()

    assertTrue(runDailyCheck())

    assertEquals(
      listOf(
        100011 to at9(2028, 5, 11),
        100012 to at9(2028, 5, 14),
        100021 to at9(2028, 5, 7),
        100022 to at9(2028, 5, 10),
        500001 to at9(2027, 5, 11)
      ),
      alarms()
    )
  }

  @Test
  fun rebootReschedulesAllAnniversaries() {
    seedBootScenario()
    AlarmNotificationScheduler.nowMillis = { nowBoot }

    assertTrue(deliver(BootCompletedReceiver(), Intent.ACTION_BOOT_COMPLETED))

    assertEquals(
      listOf(
        100011 to at9(2026, 12, 22),
        100012 to at9(2026, 12, 25),
        500001 to at9(2026, 10, 9)
      ),
      alarms()
    )
    assertEquals(emptyList<Pair<Int, String?>>(), notifications())
  }

  @Test
  fun nonRescheduleActionDoesNothing() {
    seedBootScenario()
    AlarmNotificationScheduler.nowMillis = { nowBoot }
    val before = runBlocking { db.inLoveDao().getAnniversaryDatesList() }

    assertFalse(deliver(BootCompletedReceiver(), Intent.ACTION_SCREEN_ON))

    assertEquals(emptyList<Pair<Int, Long>>(), alarms())
    assertEquals(emptyList<Pair<Int, String?>>(), notifications())
    assertEquals(before, runBlocking { db.inLoveDao().getAnniversaryDatesList() })
  }

  // Locks "re-arm tomorrow FIRST": if step 1 moved after the DAO work, nothing would arm 500001.
  // The failing step must also be caught and logged, and the PendingResult still finished (deliver waits for it).
  @Test
  fun dailyCheckStillArmsTomorrowWhenALaterStepFails() {
    seedStaleScenario()
    db.close()

    assertTrue(runDailyCheck())

    assertEquals(listOf(500001 to at9(2027, 5, 11)), alarms())
    assertEquals(emptyList<Pair<Int, String?>>(), notifications())
    assertTrue("a failed step must be caught and logged, not left to crash the app", loggedAnError())
  }

  // The scheduler guards its own DAO calls, so AppDatabase.getDatabase is the one boot step that can reach the receiver's catch.
  @Test
  fun rebootStillFinishesAndLogsWhenTheDatabaseCannotBeBuilt() {
    injectDatabase(null)
    val noApplicationContext = object : ContextWrapper(context) {
      override fun getApplicationContext(): Context = throw IllegalStateException("no application context")
    }

    assertTrue(deliver(BootCompletedReceiver(), Intent.ACTION_BOOT_COMPLETED, noApplicationContext))

    assertTrue("a failed boot must be caught and logged, not left to crash the app", loggedAnError())
  }

  // ---- U13a: the love milestones come back after a reboot (the boot copy kept by LoveAnniversaryMilestoneScheduler) ----

  private val dayMillis = 86_400_000L

  // LoveAnniversaryMilestoneScheduler reads the real clock, so the start date is 10 days before the real now.
  private val start = System.currentTimeMillis() - 10 * dayMillis

  private fun textOf(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(millis))

  private val startText get() = textOf(start)

  private val schedulerScope = CoroutineScope(SupervisorJob())
  private var milestoneArgs: LoveMilestoneArgs? = null
  private val scheduler by lazy { AndroidReminderScheduler(context, db.inLoveDao(), { milestoneArgs }, schedulerScope) }

  private fun ownerAccount(uid: String = "u1", email: String = "alice@x.com", session: String = "s1") = UserAccountEntity(
    uid = uid, email = email, passwordHash = "h", salt = "s", displayName = "Alice", coupleCode = "CODE-$uid", sessionToken = session
  )

  private fun seedOwner() = runBlocking { db.inLoveDao().insertUserAccount(ownerAccount()) }

  private fun seedPair(id: String, other: String, startMillis: Long, status: String = RelationshipStatus.ACTIVE, text: String = "") = runBlocking {
    db.inLoveDao().insertOnlineRelationship(
      OnlineRelationshipEntity(relationshipId = id, user1 = "u1", user2 = other, startDate = startMillis, startDateText = text, status = status)
    )
  }

  private fun seedProfile(text: String) = runBlocking {
    db.inLoveDao().insertCoupleProfile(
      CoupleProfileEntity(
        partner1Name = "An", partner1Birthday = "", partner1ProfilePicture = "",
        partner2Name = "Binh", partner2Birthday = "", partner2ProfilePicture = "",
        anniversaryDate = text
      )
    )
  }

  private fun setScope(scope: String) {
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit().putString("scope", scope).commit()
  }

  // Love milestone request codes: 300000..599999 except the daily check 500001 and the couple anniversary 400001.
  private fun milestoneCodes(): List<Int> = alarms().map { it.first }.filter { it in 300000..599999 && it != 500001 && it != 400001 }

  // The reboot dropping every scheduled alarm.
  private fun dropAlarms() {
    val manager = context.getSystemService(AlarmManager::class.java)
    shadowOf(manager).scheduledAlarms.toList().forEach { it.operation?.let { op -> manager.cancel(op) } }
  }

  private fun boot() {
    assertTrue(deliver(BootCompletedReceiver(), Intent.ACTION_BOOT_COMPLETED))
  }

  private fun armMilestones(millis: Long = start, text: String = startText, partner: String = "An") {
    LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(context, millis, text, partner)
  }

  // The positive control: arm, drop the alarms, boot, and the milestone codes armed before are back.
  private fun controlAfter(arm: () -> Unit): List<Int> {
    arm()
    val armed = milestoneCodes()
    assertTrue("the arm must schedule love milestone alarms", armed.isNotEmpty())
    dropAlarms()
    boot()
    assertEquals("positive control: a reboot must bring the love milestone alarms back", armed, milestoneCodes())
    return armed
  }

  private fun assertNoMilestonesAfterBoot() {
    dropAlarms()
    boot()
    assertEquals("a reboot must not arm love milestones here", emptyList<Int>(), milestoneCodes())
  }

  private fun assertCopyForgotten() {
    assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))
  }

  private fun seedControlled(): List<Int> {
    seedOwner()
    seedPair("r1", "u2", start, text = startText)
    return controlAfter { armMilestones() }
  }

  @Test
  fun bootRearmsLoveMilestonesArmedByTheViewModelPath() {
    seedControlled()
  }

  @Test
  fun bootRearmsLoveMilestonesArmedByTheResync() {
    seedOwner()
    seedPair("r1", "u2", start, text = startText)
    milestoneArgs = LoveMilestoneArgs(start, startText, "An")

    controlAfter { runBlocking { scheduler.resyncNow() } }
  }

  @Test
  fun bootDoesNotArmMilestonesUnderAnotherAccountScope() {
    seedControlled()
    setScope(AccountDataVault.scopeOf("bob@x.com"))

    assertNoMilestonesAfterBoot()
  }

  @Test
  fun bootDoesNotRearmAfterCancelEverything() {
    seedControlled()
    runBlocking { scheduler.cancelEverything() }

    assertNoMilestonesAfterBoot()
  }

  @Test
  fun bootDoesNotRearmAfterCancelMilestones() {
    seedControlled()
    LoveAnniversaryMilestoneScheduler.cancelMilestones(context)

    boot()

    assertEquals(emptyList<Int>(), milestoneCodes())
  }

  @Test
  fun bootDoesNotRearmAfterAResyncWithoutMilestoneArgs() {
    seedControlled()
    milestoneArgs = null
    runBlocking { scheduler.resyncNow() }

    assertNoMilestonesAfterBoot()
  }

  @Test
  fun bootDoesNotRearmAnEndedCouple() {
    seedControlled()
    seedPair("r1", "u2", start, RelationshipStatus.TERMINATED)

    assertNoMilestonesAfterBoot()
    assertCopyForgotten()
  }

  @Test
  fun bootRearmsWhenTheOwnerIsRepairedAfterABreakup() {
    val armed = seedControlled()
    seedPair("r1", "u2", start, RelationshipStatus.TERMINATED)
    seedPair("r2", "u3", start, text = startText)

    dropAlarms()
    boot()

    assertEquals(armed, milestoneCodes())
  }

  @Test
  fun bootIgnoresAnotherAccountsActiveRowWhenTheOwnerIsEnded() {
    seedControlled()
    seedPair("r1", "u2", start, RelationshipStatus.TERMINATED)
    runBlocking {
      db.inLoveDao().insertOnlineRelationship(OnlineRelationshipEntity(relationshipId = "r9", user1 = "u8", user2 = "u9", startDate = start))
    }

    assertNoMilestonesAfterBoot()
    assertCopyForgotten()
  }

  @Test
  fun bootStillRearmsAPendingBreakup() {
    val armed = seedControlled()
    seedPair("r1", "u2", start, RelationshipStatus.PENDING_BREAKUP, startText)

    dropAlarms()
    boot()

    assertEquals(armed, milestoneCodes())
  }

  @Test
  fun bootDropsTheCopyOfADeletedAccount() {
    seedControlled()
    runBlocking {
      db.inLoveDao().deleteUserAccount(ownerAccount())
      db.inLoveDao().deleteOnlineRelationshipsForUser("u1")
    }

    assertNoMilestonesAfterBoot()
    assertCopyForgotten()
  }

  @Test
  fun bootDropsTheCopyOfASignedOutAccount() {
    seedControlled()
    runBlocking { db.inLoveDao().insertUserAccount(ownerAccount(session = "")) }

    assertNoMilestonesAfterBoot()
    assertCopyForgotten()
  }

  @Test
  fun bootIgnoresAnotherAccountsBlankSession() {
    seedOwner()
    runBlocking { db.inLoveDao().insertUserAccount(ownerAccount("u7", "bob@x.com", "")) }
    seedPair("r1", "u2", start, text = startText)

    controlAfter { armMilestones() }
  }

  @Test
  fun bootRearmsAGuestCopyThatRoomStillBacks() {
    setScope(AccountDataVault.scopeOf(null))
    seedProfile(startText)

    controlAfter { armMilestones(0L, startText, "An") }
  }

  @Test
  fun bootForgetsTheGuestCopyOnceTheProfileIsGone() {
    setScope(AccountDataVault.scopeOf(null))
    seedProfile(startText)
    controlAfter { armMilestones(0L, startText, "An") }
    runBlocking { db.inLoveDao().clearCoupleProfile() }

    assertNoMilestonesAfterBoot()
    assertCopyForgotten()
  }

  @Test
  fun bootForgetsAnotherAccountsCopyWrittenUnderThisToken() {
    setScope(AccountDataVault.scopeOf("bob@x.com"))
    runBlocking { db.inLoveDao().insertUserAccount(ownerAccount("u7", "bob@x.com", "s7")) }
    seedProfile(startText)
    controlAfter { armMilestones(0L, startText, "") }
    armMilestones(0L, textOf(start - 10 * dayMillis), "")

    assertNoMilestonesAfterBoot()
    assertCopyForgotten()
  }

  @Test
  fun bootForgetsThePlaceholderDate() {
    setScope(AccountDataVault.scopeOf(null))
    seedProfile(startText)
    controlAfter { armMilestones(0L, startText, "An") }
    armMilestones(0L, "Hôm nay", "An")

    assertNoMilestonesAfterBoot()
    assertCopyForgotten()
  }

  @Test
  fun bootForgetsACopyWhoseStartDateWasEditedElsewhere() {
    seedControlled()
    seedPair("r1", "u2", start + dayMillis, text = startText)

    assertNoMilestonesAfterBoot()
    assertCopyForgotten()
  }

  @Test
  fun bootRearmsAPairedOwnerWithoutAStartMillis() {
    seedOwner()
    seedPair("r1", "u2", 0L, text = startText)

    controlAfter { armMilestones(0L, startText, "An") }
  }

  @Test
  fun bootRearmsASignedInUnpairedOwnerWithAProfile() {
    seedOwner()
    seedProfile(startText)

    controlAfter { armMilestones(0L, startText, "An") }
  }

  // ---- A day-count milestone stays alive until its own day is over (a text-parsed start is local midnight, the day-of alert is 08:30) ----

  // 07:00 on the day the 30-day milestone of startText falls.
  private fun dayThirtyAt0700(): Long = Calendar.getInstance().apply {
    timeInMillis = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).parse(startText)!!.time
    add(Calendar.DAY_OF_YEAR, 29)
    set(Calendar.HOUR_OF_DAY, 7)
  }.timeInMillis

  // Title and message stored in the PendingIntent of the alarm with this request code.
  private fun extrasOf(code: Int): Pair<String, String>? =
    shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .firstOrNull { shadowOf(it.operation).requestCode == code }
      ?.let {
        val intent = shadowOf(it.operation).savedIntent
        intent.getStringExtra(ReminderAlarmReceiver.EXTRA_TITLE).orEmpty() to
          intent.getStringExtra(ReminderAlarmReceiver.EXTRA_MESSAGE).orEmpty()
      }

  private fun dayOfTextOfDay30(english: Boolean, date: String): Pair<String, String> {
    val name = LoveMilestoneRules.DAY_MILESTONES.first { it.days == 30 }.let { if (english) it.en else it.vi }
    val text = ReminderTexts.of(ReminderKind.LOVE_MILESTONE_DAY_OF, english, ReminderTextArgs(title = name, dateText = date, partnerName = "An"))
    return text.title to text.body
  }

  @Test
  fun bootOnTheMorningOfADayCountMilestoneKeepsItsDayOfAlert() {
    seedOwner()
    seedPair("r1", "u2", 0L, text = startText)
    armMilestones(0L, startText, "An")
    assertTrue("positive control: the arm must schedule the 30-day alerts", milestoneCodes().containsAll(listOf(300030, 400030, 500030)))
    dropAlarms()

    LoveAnniversaryMilestoneScheduler.nowMillis = { dayThirtyAt0700() }
    boot()

    val codes = milestoneCodes()
    assertTrue("08:30 of the milestone day is still ahead at 07:00: $codes", 500030 in codes)
    assertFalse("the 3-day and 1-day alerts are behind: $codes", 300030 in codes || 400030 in codes)
  }

  @Test
  fun resyncOnTheMorningOfADayCountMilestoneRewritesItsDayOfAlertInTheNewLanguage() {
    seedOwner()
    seedPair("r1", "u2", 0L, text = startText)
    armMilestones(0L, startText, "An")
    val date = textOf(dayThirtyAt0700())
    assertEquals("positive control: armed in Vietnamese", dayOfTextOfDay30(false, date), extrasOf(500030))

    LocaleManager.saveLanguage(context, AppLanguage.EN)
    LoveAnniversaryMilestoneScheduler.nowMillis = { dayThirtyAt0700() }
    milestoneArgs = LoveMilestoneArgs(0L, startText, "An")
    runBlocking { scheduler.resyncNow() }

    assertEquals(dayOfTextOfDay30(true, date), extrasOf(500030))
  }
}
