package com.example.alarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.plan.ReminderKey
import com.example.alarm.plan.ReminderScheduler
import com.example.alarm.plan.fakes.FakeReminderScheduler
import com.example.data.db.AccountDataVault
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.CustomReminderEntity
import com.example.data.model.MilestoneEntity
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocaleManager
import com.example.ui.viewmodel.InLoveViewModel
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

// Pins which scheduler calls the ViewModel's personal-reminder entry points make (N-07a):
// a fake scheduler sees "database write first, then ONE resync or a cancel by key";
// the real Android scheduler still arms and cancels the same AlarmManager request codes as before (rule K2).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderWiringEntriesTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private val originalZone: TimeZone = TimeZone.getDefault()
  private val stores = mutableListOf<ViewModelStore>()

  // What Room held at the moment the scheduler was called.
  private class Snap(
    val anniversaries: List<AnniversaryDateEntity>,
    val milestones: List<MilestoneEntity>,
    val reminders: List<CustomReminderEntity>
  )

  private val atResync = mutableListOf<Snap>()
  private val atCancel = mutableListOf<Snap>()

  // Counts through [inner]; the hooks run BEFORE delegating so they see the state the VM left behind.
  private class Spy(val inner: FakeReminderScheduler = FakeReminderScheduler()) : ReminderScheduler by inner {
    var onResync: () -> Unit = {}
    var onCancel: (List<ReminderKey>) -> Unit = {}

    override fun requestResync() {
      onResync()
      inner.requestResync()
    }

    override fun cancel(keys: List<ReminderKey>) {
      onCancel(keys)
      inner.cancel(keys)
    }
  }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    // Guest scope: the VM's session restore enters the guest scope, and a different stored scope would wipe the rows under test.
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit()
      .putString("scope", AccountDataVault.scopeOf(null)).commit()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    injectDatabase(db)
    AlarmNotificationScheduler.nowMillis = { NOW }
  }

  @After
  fun tearDown() {
    stores.forEach { it.clear() }
    AlarmNotificationScheduler.nowMillis = { System.currentTimeMillis() }
    injectDatabase(null)
    TimeZone.setDefault(originalZone)
    db.close()
  }

  // The VM calls AppDatabase.getDatabase(context); setting its INSTANCE field is how it gets the in-memory test database.
  private fun injectDatabase(value: AppDatabase?) {
    val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
    field.isAccessible = true
    field.set(null, value)
  }

  private fun atSec(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
    Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute, second) }.timeInMillis

  private fun at9(year: Int, month: Int, day: Int): Long = atSec(year, month, day, 9, 0, 0)

  private val NOW get() = atSec(2026, 10, 9, 8, 0, 0)

  private val dao get() = db.inLoveDao()

  private fun newVm(scheduler: ReminderScheduler?): InLoveViewModel {
    // The VM reads the saved language when it is built, so it starts in English and no language-change resync is requested.
    LocaleManager.saveLanguage(context, AppLanguage.EN)
    val store = ViewModelStore().also { stores += it }
    val factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T =
        InLoveViewModel(context as Application, scheduler) as T
    }
    val vm = ViewModelProvider(store, factory)[InLoveViewModel::class.java]
    vm.clearToast()
    return vm
  }

  private fun newSpy(): Spy = Spy().also {
    it.onResync = { atResync += snap() }
    it.onCancel = { atCancel += snap() }
  }

  private fun snap() = runBlocking { Snap(dao.getAnniversaryDatesList(), dao.getMilestonesList(), dao.getCustomRemindersList()) }

  // The VM runs on the paused Robolectric main looper: poll with idle() until the condition holds (bounded, no sleep).
  private fun await(what: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (true) {
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      if (System.nanoTime() > deadline) fail("timed out waiting for $what")
      LockSupport.parkNanos(1_000_000L)
    }
  }

  // Runs one VM action and waits for its toast, the VM's "done" signal (shown after the scheduler call).
  private fun act(vm: InLoveViewModel, toastFragment: String, action: () -> Unit): String {
    vm.clearToast()
    action()
    await("toast containing '$toastFragment'") { vm.toastMessage.value?.contains(toastFragment) == true }
    return vm.toastMessage.value!!
  }

  private fun assertCalls(spy: Spy, resyncs: Int, cancels: List<List<ReminderKey>>) {
    assertEquals("resync requests", resyncs, spy.inner.resyncRequests)
    assertEquals("cancel calls", cancels, spy.inner.cancelCalls.toList())
  }

  private fun insertAnniversary(title: String) = runBlocking {
    dao.insertAnniversaryDate(AnniversaryDateEntity(title = title, dateText = "25/12/2026", reminderDaysBefore = 3))
  }

  private fun insertMilestone(title: String) = runBlocking {
    dao.insertMilestone(
      MilestoneEntity(
        title = title, dateText = "25/12/2026", subtitle = "s", categoryTag = "c", secondaryTag = "t",
        imageUrl = "", daysRemaining = 77, isUserCreated = true
      )
    )
  }

  private fun insertReminder(title: String, alarm: Long?) = runBlocking {
    dao.insertCustomReminder(
      CustomReminderEntity(title = title, dateText = "d", details = "msg", daysRemainingText = "x", alarmTimeMillis = alarm)
    )
  }

  private fun anniversaryOf(title: String) = runBlocking { dao.getAnniversaryDatesList() }.single { it.title == title }

  private fun milestoneOf(title: String) = runBlocking { dao.getMilestonesList() }.single { it.title == title }

  private fun remindersOf(title: String) = runBlocking { dao.getCustomRemindersList() }.filter { it.title == title }

  private fun alarms(): List<Pair<Int, Long>> =
    shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .map { shadowOf(it.operation).requestCode to it.triggerAtTime }
      .sortedBy { it.first }

  private fun alarmsWithCodes(vararg codes: Int) = alarms().filter { it.first in codes.toSet() }

  private fun advanceCode(id: Long) = (100000 + id * 10 + 1).toInt()

  private fun dayOfCode(id: Long) = (100000 + id * 10 + 2).toInt()

  private fun keys(id: Long) = listOf(ReminderKey.Advance(id), ReminderKey.DayOf(id))

  // ---- A. fake scheduler: which calls, in which order ----

  @Test
  fun addAnniversaryRequestsOneResync() {
    val spy = newSpy()
    val vm = newVm(spy)

    act(vm, "Saved") { vm.addAnniversaryDate(title = "n07a add", dateText = "25/12/2026", reminderDaysBefore = 3) }

    assertCalls(spy, 1, emptyList())
    assertTrue(
      "the row must already be saved, with notifications on, when the resync is requested",
      atResync.single().anniversaries.any { it.title == "n07a add" && it.notificationEnabled }
    )
  }

  @Test
  fun deleteAnniversaryCancelsItsKey() {
    val id = insertAnniversary("n07a del")
    val spy = newSpy()
    val vm = newVm(spy)

    act(vm, "Date deleted.") { vm.deleteAnniversaryDate(id) }

    assertCalls(spy, 0, listOf(keys(id)))
    assertTrue("the row must already be deleted when the cancel runs", atCancel.single().anniversaries.none { it.id == id })
  }

  @Test
  fun toggleAnniversaryNotificationCancelsOrResyncs() {
    val id = insertAnniversary("n07a tog")
    val spy = newSpy()
    val vm = newVm(spy)

    act(vm, "🔕 Notifications off for 'n07a tog'") { vm.toggleAnniversaryNotification(anniversaryOf("n07a tog")) }
    assertCalls(spy, 0, listOf(keys(id)))

    act(vm, "🔔 Notifications on for 'n07a tog'") { vm.toggleAnniversaryNotification(anniversaryOf("n07a tog")) }
    assertCalls(spy, 1, listOf(keys(id)))
    assertTrue("the row must be ON when the resync is requested", atResync.single().anniversaries.single { it.id == id }.notificationEnabled)
  }

  @Test
  fun updateAnniversaryDateResyncsWhenOnAndCancelsWhenOff() {
    val id = insertAnniversary("n07a upd")
    val spy = newSpy()
    val vm = newVm(spy)

    act(vm, "Updated!") { vm.updateAnniversaryDate(anniversaryOf("n07a upd").copy(notificationEnabled = true, title = "n07a upd2")) }
    assertCalls(spy, 1, emptyList())
    assertEquals("the new title must be saved when the resync is requested", "n07a upd2", atResync.single().anniversaries.single { it.id == id }.title)

    act(vm, "Updated!") { vm.updateAnniversaryDate(anniversaryOf("n07a upd2").copy(notificationEnabled = false)) }
    assertCalls(spy, 1, listOf(keys(id)))
  }

  @Test
  fun milestoneChangeRequestsResync() {
    insertMilestone("n07a guard") // a non-empty table stops the VM's offline seeder, which would overwrite id 1
    val spy = newSpy()
    val vm = newVm(spy)

    act(vm, "Milestone added") { vm.addMilestone("n07a ms", "25/12/2026", "sub", "", "", "", 77, false) }
    assertCalls(spy, 1, emptyList())
    assertTrue("the milestone must be saved when the resync is requested", atResync.single().milestones.any { it.title == "n07a ms" })
    val id = milestoneOf("n07a ms").id

    act(vm, "🔕 Notifications off for 'n07a ms'") { vm.toggleMilestoneNotification(milestoneOf("n07a ms")) }
    assertCalls(spy, 1, listOf(listOf(ReminderKey.UserMilestone(id))))

    act(vm, "🔔 Notifications on for 'n07a ms'") { vm.toggleMilestoneNotification(milestoneOf("n07a ms")) }
    assertCalls(spy, 2, listOf(listOf(ReminderKey.UserMilestone(id))))
  }

  @Test
  fun deleteMilestoneCancelsItsKey() {
    val id = insertMilestone("n07a msdel")
    val spy = newSpy()
    val vm = newVm(spy)

    act(vm, "Milestone deleted.") { vm.deleteMilestone(id) }

    assertCalls(spy, 0, listOf(listOf(ReminderKey.UserMilestone(id))))
    assertTrue("the row must already be gone when the cancel runs", atCancel.single().milestones.none { it.id == id })
  }

  @Test
  fun scheduleReminderAlarmWritesTheRowThenRequestsOneResync() {
    val spy = newSpy()
    val vm = newVm(spy)

    val toast = act(vm, "Alarm set") { vm.scheduleReminderAlarm("n07a alarm", "msg", atSec(2026, 10, 9, 10, 0, 0), null) }

    assertEquals("⏰ Alarm set for 10:00 - 09/10/2026!", toast)
    assertCalls(spy, 1, emptyList())
    assertEquals(
      "the alarm time must be on the row when the resync is requested",
      atSec(2026, 10, 9, 10, 0, 0),
      atResync.single().reminders.single { it.title == "n07a alarm" }.alarmTimeMillis
    )
  }

  @Test
  fun scheduleReminderAlarmWarnsWhenExactAlarmsAreOff() {
    val spy = newSpy()
    spy.inner.exactAvailable = false
    val vm = newVm(spy)

    val toast = act(vm, "Alarm set") { vm.scheduleReminderAlarm("n07a warn", "msg", atSec(2026, 10, 9, 10, 0, 0), null) }

    assertTrue("toast was: $toast", toast.endsWith("Exact alarms are off, so the alarm may be late."))
    assertCalls(spy, 1, emptyList())
  }

  @Test
  fun scheduleReminderAlarmInThePastRequestsNothing() {
    val spy = newSpy()
    val vm = newVm(spy)
    val past = atSec(2026, 10, 9, 7, 0, 0)

    val toast = act(vm, "Couldn't set the alarm") { vm.scheduleReminderAlarm("n07a past", "msg", past, null) }
    assertTrue("toast was: $toast", toast.startsWith("Couldn't set the alarm"))
    assertCalls(spy, 0, emptyList())
    assertEquals("a new reminder row must not stay", emptyList<CustomReminderEntity>(), remindersOf("n07a past"))

    val id = insertReminder("n07a past2", atSec(2026, 10, 10, 9, 0, 0))
    act(vm, "Couldn't set the alarm") { vm.scheduleReminderAlarm("n07a past2", "msg", past, id) }
    assertCalls(spy, 0, emptyList())
    assertNull("an existing row must not keep a time nothing will ring", remindersOf("n07a past2").single().alarmTimeMillis)
  }

  // "Now" itself is not in the future: the boundary of the past check.
  @Test
  fun scheduleReminderAlarmAtThisInstantRequestsNothing() {
    val spy = newSpy()
    val vm = newVm(spy)

    val toast = act(vm, "Couldn't set the alarm") { vm.scheduleReminderAlarm("n07a now", "msg", NOW, null) }

    assertTrue("toast was: $toast", toast.startsWith("Couldn't set the alarm"))
    assertCalls(spy, 0, emptyList())
    assertEquals(emptyList<CustomReminderEntity>(), remindersOf("n07a now"))
  }

  @Test
  fun editCustomAlarmRequestsResync() {
    val id = insertReminder("n07a edit", atSec(2026, 10, 9, 9, 0, 0))
    val spy = newSpy()
    val vm = newVm(spy)

    act(vm, "Alarm set") { vm.scheduleReminderAlarm("n07a edit", "msg", atSec(2026, 10, 9, 11, 0, 0), id) }

    assertCalls(spy, 1, emptyList())
    val row = atResync.single().reminders.filter { it.title == "n07a edit" }.single()
    assertEquals("the edit must reuse the row", id, row.id)
    assertEquals("the new time must be on the row when the resync is requested", atSec(2026, 10, 9, 11, 0, 0), row.alarmTimeMillis)
  }

  @Test
  fun deleteCustomReminderCancelsItsKey() {
    val id = insertReminder("n07a rdel", atSec(2026, 10, 9, 9, 0, 0))
    val spy = newSpy()
    val vm = newVm(spy)

    act(vm, "Reminder deleted.") { vm.deleteCustomReminder(id) }

    assertCalls(spy, 0, listOf(listOf(ReminderKey.Custom(id))))
    assertTrue("the row must already be gone when the cancel runs", atCancel.single().reminders.none { it.id == id })
  }

  // ponytail: the branch with a relationship is skipped, add it when the collector in init reuses loveMilestoneArgs().
  @Test
  fun loveMilestoneArgsFallsBackWithoutRelationship() {
    val vm = newVm(newSpy())

    val args = vm.loveMilestoneArgs()

    assertEquals(0L, args.startDateMillis)
    assertEquals("", args.partnerName)
    assertEquals(vm.anniversaryDate.value, args.startDateText)
  }

  // ---- B. real Android scheduler: the AlarmManager outcome must not change (K2) ----

  @Test
  fun defaultSchedulerArmsAnniversaryAlarmsAfterAdd() {
    val vm = newVm(null)

    act(vm, "Saved") { vm.addAnniversaryDate(title = "n07a dadd", dateText = "25/12/2026", reminderDaysBefore = 3) }
    val id = anniversaryOf("n07a dadd").id

    await("both anniversary alarms") { alarmsWithCodes(advanceCode(id), dayOfCode(id)).size == 2 }
    assertEquals(
      listOf(advanceCode(id) to at9(2026, 12, 22), dayOfCode(id) to at9(2026, 12, 25)),
      alarmsWithCodes(advanceCode(id), dayOfCode(id))
    )
  }

  @Test
  fun defaultSchedulerCancelsAnniversaryAlarmsOnDelete() {
    val id = insertAnniversary("n07a ddel")
    val vm = newVm(null)
    runBlocking { AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(context, dao) }
    assertEquals(
      listOf(advanceCode(id) to at9(2026, 12, 22), dayOfCode(id) to at9(2026, 12, 25)),
      alarmsWithCodes(advanceCode(id), dayOfCode(id))
    )

    act(vm, "Date deleted.") { vm.deleteAnniversaryDate(id) }

    assertEquals(emptyList<Pair<Int, Long>>(), alarmsWithCodes(advanceCode(id), dayOfCode(id)))
  }

  @Test
  fun defaultSchedulerArmsAndCancelsPersonalAlarm() {
    val vm = newVm(null)
    val at = atSec(2026, 10, 9, 10, 0, 0)

    act(vm, "Alarm set") { vm.scheduleReminderAlarm("n07a dalarm", "msg", at, null) }
    val id = remindersOf("n07a dalarm").single().id

    await("the personal alarm") { alarmsWithCodes(id.toInt()).isNotEmpty() }
    assertEquals(listOf(id.toInt() to at), alarmsWithCodes(id.toInt()))

    act(vm, "Reminder deleted.") { vm.deleteCustomReminder(id) }

    assertEquals(emptyList<Pair<Int, Long>>(), alarmsWithCodes(id.toInt()))
  }

  @Test
  fun defaultSchedulerRefusesPastPersonalAlarm() {
    val vm = newVm(null)
    val past = atSec(2026, 10, 9, 7, 0, 0)

    val toast = act(vm, "Couldn't set the alarm") { vm.scheduleReminderAlarm("n07a dpast", "msg", past, null) }

    assertTrue("toast was: $toast", toast.startsWith("Couldn't set the alarm"))
    assertTrue("no alarm may be armed in the past", alarms().none { it.second == past })
    assertEquals(emptyList<CustomReminderEntity>(), remindersOf("n07a dpast"))
  }

  @Test
  fun defaultSchedulerArmsAndCancelsMilestoneAlarm() {
    insertMilestone("n07a guard") // a non-empty table stops the VM's offline seeder, which would overwrite id 1
    val vm = newVm(null)

    act(vm, "Milestone added") { vm.addMilestone("n07a dms", "25/12/2026", "sub", "", "", "", 77, false) }
    val code = (200000 + milestoneOf("n07a dms").id).toInt()

    await("the milestone alarm") { alarmsWithCodes(code).isNotEmpty() }
    assertEquals(listOf(code to at9(2026, 12, 25)), alarmsWithCodes(code))

    act(vm, "🔕 Notifications off for 'n07a dms'") { vm.toggleMilestoneNotification(milestoneOf("n07a dms")) }
    assertEquals(emptyList<Pair<Int, Long>>(), alarmsWithCodes(code))

    act(vm, "🔔 Notifications on for 'n07a dms'") { vm.toggleMilestoneNotification(milestoneOf("n07a dms")) }
    await("the milestone alarm again") { alarmsWithCodes(code).isNotEmpty() }
    assertEquals(listOf(code to at9(2026, 12, 25)), alarmsWithCodes(code))
  }

  // The runner is handed loveMilestoneArgs(): a resync must also re-arm the love milestone alarms (30 days: 3 days before, 1 day before, day of).
  @Test
  fun defaultSchedulerResyncArmsLoveMilestoneAlarms() {
    val vm = newVm(null)
    val codes = intArrayOf(300030, 400030, 500030)
    // The VM's own relationship collector arms them right after construction; the default text is not a date, so the start is the real now.
    await("the collector to arm the love milestone alarms") { alarmsWithCodes(*codes).size == codes.size }
    codes.forEach { AlarmNotificationScheduler.cancelAlarm(context, it.toLong(), ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM) }
    assertEquals(emptyList<Pair<Int, Long>>(), alarmsWithCodes(*codes))

    act(vm, "Saved") { vm.addAnniversaryDate(title = "n07a dlove", dateText = "25/12/2026", reminderDaysBefore = 3) }

    await("the resync to arm the love milestone alarms again") { alarmsWithCodes(*codes).size == codes.size }
  }

  // The activity asks for the ViewModel through the platform factory, which looks up the one-argument constructor by reflection.
  @Test
  fun viewModelCanBeCreatedByTheDefaultFactory() {
    val store = ViewModelStore().also { stores += it }
    val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(context as Application)

    assertNotNull(ViewModelProvider(store, factory)[InLoveViewModel::class.java])
  }
}
