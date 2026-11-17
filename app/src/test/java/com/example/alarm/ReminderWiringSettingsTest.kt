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
import com.example.alarm.plan.LoveMilestoneRules
import com.example.alarm.plan.ReminderKind
import com.example.alarm.plan.ReminderScheduler
import com.example.alarm.plan.ReminderText
import com.example.alarm.plan.ReminderTextArgs
import com.example.alarm.plan.ReminderTexts
import com.example.alarm.plan.fakes.FakeReminderScheduler
import com.example.data.db.AccountDataVault
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.CustomReminderEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.ReminderCadenceEntity
import com.example.data.model.UserAccountEntity
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocaleManager
import com.example.ui.viewmodel.InLoveViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

// Pins which scheduler calls the ViewModel's settings triggers make (N-07b): cadence toggle, global notification switch,
// notification permission grant and account switch. A fake scheduler sees "write first, then ONE resync" (and "cancelAll, then resync"
// after an account switch); the real Android scheduler still arms and cancels the same AlarmManager request codes as before (rule K2).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderWiringSettingsTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private val originalZone: TimeZone = TimeZone.getDefault()
  private val stores = mutableListOf<ViewModelStore>()

  private val scopeOfBob = AccountDataVault.scopeOf("bob@x.com")
  private val guestScope = AccountDataVault.scopeOf(null)

  // One scheduler call seen by the Spy: its name, the account token in force and the custom reminder titles in Room at that moment.
  private class Event(val name: String, val token: String, val titles: List<String>)

  private val events = mutableListOf<Event>()

  // Counts through [inner]; the hooks run BEFORE delegating so they see the state the VM left behind.
  private class Spy(val inner: FakeReminderScheduler = FakeReminderScheduler()) : ReminderScheduler by inner {
    var onResync: () -> Unit = {}
    var onCancelAll: () -> Unit = {}

    override fun requestResync() {
      onResync()
      inner.requestResync()
    }

    override fun cancelAll() {
      onCancelAll()
      inner.cancelAll()
    }
  }

  // A vault switch running on a background thread, so the test thread can keep the main looper going.
  private class Switching {
    lateinit var thread: Thread

    @Volatile
    var failure: Throwable? = null
  }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    // No stored scope: the first vault switch (the VM's session restore) then only stores the guest scope and moves nothing.
    forgetScope()
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
    it.onCancelAll = { events += event("cancelAll") }
    it.onResync = { events += event("resync") }
  }

  private fun event(name: String) = Event(name, AccountDataVault.currentToken(context), reminderTitles())

  private fun reminderTitles() = runBlocking { dao.getCustomRemindersList() }.map { it.title }

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

  // Runs one VM action and waits for its toast, the VM's "done" signal.
  private fun act(vm: InLoveViewModel, toastFragment: String, action: () -> Unit) {
    vm.clearToast()
    action()
    await("toast containing '$toastFragment'") { vm.toastMessage.value?.contains(toastFragment) == true }
  }

  // Waits WITHOUT idling the main looper, so main-looper work stays queued.
  private fun parkFor(nanos: Long) {
    val end = System.nanoTime() + nanos
    while (System.nanoTime() < end) LockSupport.parkNanos(1_000_000L)
  }

  private fun assertCalls(spy: Spy, resyncs: Int) {
    assertEquals("resync requests", resyncs, spy.inner.resyncRequests)
    assertEquals("cancelAll calls", 0, spy.inner.cancelAllCalls)
    assertTrue("cancel(keys) calls", spy.inner.cancelCalls.isEmpty())
  }

  // ---- account scope and vault helpers ----

  private fun scopePrefs() = context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE)

  private fun forgetScope() {
    scopePrefs().edit().remove("scope").commit()
  }

  // The VM's session restore runs on IO and enters the guest scope; switching before it stored the scope could be overtaken by it.
  private fun awaitSessionRestored() =
    await("the session restore to store the guest scope") { scopePrefs().getString("scope", null) == guestScope }

  private fun vaultOf(vm: InLoveViewModel): AccountDataVault {
    val field = vm.authRepo.javaClass.getDeclaredField("vault")
    field.isAccessible = true
    return field.get(vm.authRepo) as AccountDataVault
  }

  // Reads the lazy scheduler of a VM built with null; this also creates it on the test thread.
  private fun androidScheduler(vm: InLoveViewModel): AndroidReminderScheduler {
    val field = InLoveViewModel::class.java.getDeclaredField("scheduler\$delegate")
    field.isAccessible = true
    return (field.get(vm) as Lazy<*>).value as AndroidReminderScheduler
  }

  private fun androidVm(): InLoveViewModel = newVm(null).also {
    androidScheduler(it)
    awaitSessionRestored()
  }

  // With the real Android scheduler the switch waits for a command that only the main looper can run: never block the test thread on it.
  private fun startSwitch(vault: AccountDataVault, scope: String): Switching {
    val switching = Switching()
    switching.thread = thread {
      try {
        runBlocking { vault.switchTo(scope) }
      } catch (t: Throwable) {
        switching.failure = t
      }
    }
    return switching
  }

  private fun finishSwitch(switching: Switching) {
    await("the account switch to finish") { !switching.thread.isAlive }
    switching.thread.join()
    switching.failure?.let { throw it }
  }

  private fun switchAccount(vault: AccountDataVault, scope: String) = finishSwitch(startSwitch(vault, scope))

  // ---- rows and alarms ----

  private fun insertAnniversary(title: String) = runBlocking {
    dao.insertAnniversaryDate(AnniversaryDateEntity(title = title, dateText = "25/12/2026", reminderDaysBefore = 3))
  }

  private fun insertReminder(title: String, alarm: Long?) = runBlocking {
    dao.insertCustomReminder(
      CustomReminderEntity(title = title, dateText = "d", details = "msg", daysRemainingText = "x", alarmTimeMillis = alarm)
    )
  }

  private fun insertCadences() = runBlocking {
    dao.insertReminderCadences(
      listOf(
        ReminderCadenceEntity(key = "7_days", label = "n07b 7d", isEnabled = true),
        ReminderCadenceEntity(key = "3_days", label = "n07b 3d", isEnabled = true),
        ReminderCadenceEntity(key = "1_day", label = "n07b 1d", isEnabled = true),
        ReminderCadenceEntity(key = "exact_day", label = "n07b day", isEnabled = true)
      )
    )
  }

  private fun cadenceOf(key: String) = runBlocking { dao.getReminderCadencesList() }.single { it.key == key }

  private fun alarms(): List<Pair<Int, Long>> =
    shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .map { shadowOf(it.operation).requestCode to it.triggerAtTime }
      .sortedBy { it.first }

  private fun alarmsWithCodes(vararg codes: Int) = alarms().filter { it.first in codes.toSet() }

  private fun awaitArmed(what: String, vararg codes: Int) =
    await(what) { alarmsWithCodes(*codes).size == codes.size }

  private fun advanceCode(id: Long) = (100000 + id * 10 + 1).toInt()

  private fun dayOfCode(id: Long) = (100000 + id * 10 + 2).toInt()

  // The three codes of the 30-day love milestone, armed by the VM's own relationship collector right after construction.
  private val loveCodes = intArrayOf(300030, 400030, 500030)

  private fun assertSwitchCalls(token: String, present: List<String>, absent: List<String>) {
    assertEquals("order of scheduler calls", listOf("cancelAll", "resync"), events.map { it.name })
    events.forEach {
      assertEquals("account token at ${it.name}", token, it.token)
      assertTrue("${it.name} must see $present in Room, saw ${it.titles}", it.titles.containsAll(present))
      assertTrue("${it.name} must not see $absent in Room, saw ${it.titles}", absent.none { title -> title in it.titles })
    }
  }

  // ---- A. fake scheduler: which calls, in which order ----

  @Test
  fun cadenceToggleRequestsResync() {
    runBlocking { dao.insertReminderCadences(listOf(ReminderCadenceEntity(key = "3_days", label = "n07b 3d", isEnabled = true))) }
    val enabledAtResync = mutableListOf<Boolean>()
    val spy = Spy()
    spy.onResync = { enabledAtResync += cadenceOf("3_days").isEnabled }
    val vm = newVm(spy)

    act(vm, "Off: n07b 3d") { vm.toggleCadence(cadenceOf("3_days")) }
    assertCalls(spy, 1)
    assertEquals("the row must already be DISABLED when the resync is requested", listOf(false), enabledAtResync)

    act(vm, "On: n07b 3d") { vm.toggleCadence(cadenceOf("3_days")) }
    assertCalls(spy, 2)
    assertEquals("the row must already be ENABLED again when the second resync is requested", listOf(false, true), enabledAtResync)
  }

  @Test
  fun notificationSwitchRequestsResync() {
    val enabledAtResync = mutableListOf<Boolean>()
    val spy = Spy()
    spy.onResync = { enabledAtResync += AlarmNotificationScheduler.anniversaryNotificationsEnabled(context) }
    val vm = newVm(spy)

    vm.setNotificationsEnabled(false)
    assertCalls(spy, 1)
    assertEquals("the preference must be written before the resync is requested", listOf(false), enabledAtResync)
    assertFalse(vm.notificationsEnabled.value)

    vm.setNotificationsEnabled(true)
    assertCalls(spy, 2)
    assertEquals(listOf(false, true), enabledAtResync)
    assertTrue(vm.notificationsEnabled.value)
  }

  @Test
  fun grantingNotificationPermissionRequestsResync() {
    val spy = Spy()
    val vm = newVm(spy)

    // No wait on purpose: the new code requests the resync synchronously, the old code launched a coroutine.
    vm.resyncAllAnniversaryAlarms()

    assertCalls(spy, 1)
  }

  @Test
  fun accountSwitchCancelsAllThenResyncs() {
    val guestTitle = "n07b guest"
    val bobTitle = "n07b bob"
    insertReminder(guestTitle, atSec(2026, 10, 9, 10, 0, 0))
    val spy = newSpy()
    val vm = newVm(spy)
    awaitSessionRestored()
    val vault = vaultOf(vm)

    runBlocking { vault.switchTo(scopeOfBob) }
    assertSwitchCalls(AccountDataVault.tokenOf(scopeOfBob), emptyList(), listOf(guestTitle))

    insertReminder(bobTitle, atSec(2026, 10, 9, 11, 0, 0))
    events.clear()
    runBlocking { vault.switchTo(guestScope) }
    assertSwitchCalls(AccountDataVault.tokenOf(guestScope), listOf(guestTitle), listOf(bobTitle))

    events.clear()
    runBlocking { vault.switchTo(scopeOfBob) }
    assertSwitchCalls(AccountDataVault.tokenOf(scopeOfBob), listOf(bobTitle), listOf(guestTitle))

    assertEquals("cancelAll calls", 3, spy.inner.cancelAllCalls)
    assertEquals("resync requests", 3, spy.inner.resyncRequests)
    assertTrue("cancel(keys) calls", spy.inner.cancelCalls.isEmpty())
  }

  // ---- B. epoch gate: love milestone input goes stale after an account switch ----

  @Test
  fun loveArgsGoStaleAfterAnAccountSwitch() {
    val vm = newVm(newSpy())
    await("the love milestone collector") { alarmsWithCodes(*loveCodes).size == loveCodes.size }
    awaitSessionRestored()
    val fresh = vm.loveMilestoneArgsIfFresh()
    assertNotNull("the relationship state is fresh before any switch", fresh)
    assertEquals(vm.loveMilestoneArgs(), fresh)

    runBlocking { vaultOf(vm).switchTo(scopeOfBob) }

    // Asserted straight after the switch: nothing may idle the looper in between.
    assertNull("the relationship state belongs to the previous account after a switch", vm.loveMilestoneArgsIfFresh())
  }

  @Test
  fun loveArgsAreFreshAgainOnceTheCollectorSeesTheNewState() {
    val vm = newVm(newSpy())
    await("the love milestone collector") { alarmsWithCodes(*loveCodes).size == loveCodes.size }
    awaitSessionRestored()
    runBlocking { vaultOf(vm).switchTo(scopeOfBob) }
    assertNull(vm.loveMilestoneArgsIfFresh())

    vm.setAnniversaryAndRecalculateDays("01/01/2025")

    await("the collector to re-stamp") { vm.loveMilestoneArgsIfFresh() != null }
    assertEquals("01/01/2025", vm.loveMilestoneArgsIfFresh()!!.startDateText)
  }

  // ---- C. real Android scheduler: the AlarmManager outcome must not change (K2) ----

  @Test
  fun defaultSchedulerCadenceToggleChangesWhichAlarmsStayArmed() {
    val id = insertAnniversary("n07b cad")
    insertCadences()
    val reminderId = insertReminder("n07b cad alarm", atSec(2026, 10, 9, 10, 0, 0)).toInt()
    val vm = newVm(null)
    awaitArmed("the start-up alarms", advanceCode(id), dayOfCode(id), reminderId)

    act(vm, "Off: n07b 3d") { vm.toggleCadence(cadenceOf("3_days")) }

    await("the advance alarm to be cancelled") { alarmsWithCodes(advanceCode(id)).isEmpty() }
    assertEquals(
      listOf(dayOfCode(id) to at9(2026, 12, 25), reminderId to atSec(2026, 10, 9, 10, 0, 0)).sortedBy { it.first },
      alarmsWithCodes(dayOfCode(id), reminderId)
    )

    act(vm, "On: n07b 3d") { vm.toggleCadence(cadenceOf("3_days")) }

    await("the advance alarm to be armed again") { alarmsWithCodes(advanceCode(id)).isNotEmpty() }
    assertEquals(listOf(advanceCode(id) to at9(2026, 12, 22)), alarmsWithCodes(advanceCode(id)))
  }

  @Test
  fun defaultSchedulerPermissionGrantRearmsAlarms() {
    val id = insertAnniversary("n07b grant")
    val reminderId = insertReminder("n07b grant alarm", atSec(2026, 10, 9, 10, 0, 0)).toInt()
    val vm = newVm(null)
    awaitArmed("the start-up alarms", advanceCode(id), dayOfCode(id), reminderId)
    AlarmNotificationScheduler.cancelAlarm(context, advanceCode(id).toLong(), ReminderAlarmReceiver.ACTION_ANNIVERSARY_ALARM)
    assertEquals(emptyList<Pair<Int, Long>>(), alarmsWithCodes(advanceCode(id)))

    vm.resyncAllAnniversaryAlarms()

    await("the advance alarm to be armed again") { alarmsWithCodes(advanceCode(id)).isNotEmpty() }
    assertEquals(listOf(advanceCode(id) to at9(2026, 12, 22)), alarmsWithCodes(advanceCode(id)))
  }

  // The global switch only filters at delivery: the resync it requests must leave the armed alarms alone.
  @Test
  fun defaultSchedulerNotificationSwitchKeepsAlarmsArmed() {
    val id = insertAnniversary("n07b switch")
    val vm = newVm(null)
    awaitArmed("the start-up alarms", advanceCode(id), dayOfCode(id))
    val before = androidScheduler(vm).completedRuns.value

    vm.setNotificationsEnabled(false)

    await("the resync command to finish") { androidScheduler(vm).completedRuns.value > before }
    assertEquals(listOf(advanceCode(id) to at9(2026, 12, 22)), alarmsWithCodes(advanceCode(id)))
  }

  // ---- D. Android account switch: real scheduler, switch on a background thread ----

  @Test
  fun androidSwitchWaitsForTheCancelBeforeTheDataMoves() {
    val guestTitle = "n07b wait"
    insertReminder(guestTitle, atSec(2026, 10, 9, 10, 0, 0))
    val vm = androidVm()
    val vault = vaultOf(vm)

    val switching = startSwitch(vault, scopeOfBob)
    parkFor(1_500_000_000L)

    assertTrue("the switch must wait for the cancel command, which only the main looper can run", switching.thread.isAlive)
    assertEquals(AccountDataVault.tokenOf(guestScope), AccountDataVault.currentToken(context))
    assertTrue("the outgoing rows must still be in Room while the cancel is pending", reminderTitles().contains(guestTitle))

    finishSwitch(switching)

    assertEquals(AccountDataVault.tokenOf(scopeOfBob), AccountDataVault.currentToken(context))
    assertFalse("the outgoing rows must be gone once the switch finished", reminderTitles().contains(guestTitle))
  }

  @Test
  fun androidSwitchCancelsTheOutgoingAccountsAlarms() {
    val guestId = insertReminder("n07b out", atSec(2026, 10, 9, 10, 0, 0)).toInt()
    val vm = androidVm()
    awaitArmed("the guest alarm", guestId)

    switchAccount(vaultOf(vm), scopeOfBob)

    assertEquals(emptyList<Pair<Int, Long>>(), alarmsWithCodes(guestId))
  }

  @Test
  fun androidRoundTripRearmsTheReturningAccount() {
    val guestAt = atSec(2026, 10, 9, 10, 0, 0)
    val guestId = insertReminder("n07b round guest", guestAt).toInt()
    val vm = androidVm()
    val vault = vaultOf(vm)
    awaitArmed("the guest alarm", guestId)

    switchAccount(vault, scopeOfBob)
    assertEquals(emptyList<Pair<Int, Long>>(), alarmsWithCodes(guestId))
    val bobAt = atSec(2026, 10, 9, 11, 0, 0)
    val bobId = insertReminder("n07b round bob", bobAt).toInt()
    runBlocking { AlarmNotificationScheduler.scheduleAllAnniversariesFromDb(context, dao) }
    assertEquals(listOf(bobId to bobAt), alarmsWithCodes(bobId))

    switchAccount(vault, guestScope)

    assertEquals(emptyList<Pair<Int, Long>>(), alarmsWithCodes(bobId))
    await("the guest alarm to be armed again") { alarmsWithCodes(guestId).isNotEmpty() }
    assertEquals(listOf(guestId to guestAt), alarmsWithCodes(guestId))
  }

  @Test
  fun androidSwitchDoesNotRearmLoveMilestonesFromStaleState() {
    val vm = androidVm()
    awaitArmed("the love milestone collector", *loveCodes)
    val before = androidScheduler(vm).completedRuns.value

    switchAccount(vaultOf(vm), scopeOfBob)

    assertNull("precondition: the relationship state is stale after the switch", vm.loveMilestoneArgsIfFresh())
    await("the cancel-all and the resync commands to finish") { androidScheduler(vm).completedRuns.value >= before + 2 }
    assertEquals(emptyList<Pair<Int, Long>>(), alarmsWithCodes(*loveCodes))
  }

  // Registering keeps the guest data in place (adoptCurrentInto): nothing in Room changes, so the collector does not emit again,
  // yet the alarms and the boot copy armed under the guest token would be dropped by the receiver once the owner is the new account.
  @Test
  fun androidAdoptionRearmsTheLoveMilestonesUnderTheNewOwner() {
    val vm = androidVm()
    awaitArmed("the love milestone collector", *loveCodes)
    val guestToken = AccountDataVault.tokenOf(guestScope)
    val bobToken = AccountDataVault.tokenOf(scopeOfBob)
    assertEquals("positive control: armed under the guest token", guestToken, ownerOfAlarm(loveCodes.first()))
    val before = androidScheduler(vm).completedRuns.value

    runBlocking { vaultOf(vm).adoptCurrentInto(scopeOfBob) }

    assertNotNull("the adopted data is still the current account's relationship state", vm.loveMilestoneArgsIfFresh())
    await("the resync command to finish") { androidScheduler(vm).completedRuns.value >= before + 1 }
    await("every love alarm and the boot copy to carry the new owner") {
      loveCodes.all { ownerOfAlarm(it) == bobToken } && LoveAnniversaryMilestoneScheduler.readBootCopy(context)?.token == bobToken
    }
    assertEquals(loveCodes.size, alarmsWithCodes(*loveCodes).size)
  }

  // An adoption that follows a switch must not vouch for the state of the account before it: only a collector emission does.
  @Test
  fun androidAdoptionKeepsStaleLoveStateStale() {
    val vm = androidVm()
    awaitArmed("the love milestone collector", *loveCodes)
    val vault = vaultOf(vm)
    switchAccount(vault, scopeOfBob)
    assertNull("precondition: the relationship state is stale after the switch", vm.loveMilestoneArgsIfFresh())

    runBlocking { vault.adoptCurrentInto(AccountDataVault.scopeOf("carol@x.com")) }

    assertNull(vm.loveMilestoneArgsIfFresh())
  }

  @Test
  fun androidSwitchClearsTheBootCopyThroughTheProductionWiring() {
    val vm = androidVm()
    awaitArmed("the love milestone collector", *loveCodes)
    val guestToken = AccountDataVault.tokenOf(guestScope)
    assertEquals(
      "positive control: the collector's arm leaves a boot copy under the guest token",
      guestToken,
      LoveAnniversaryMilestoneScheduler.readBootCopy(context)?.token
    )
    val vault = vaultOf(vm)

    val switching = startSwitch(vault, scopeOfBob)
    parkFor(1_500_000_000L)

    assertTrue("the switch must wait for the cancel command, which only the main looper can run", switching.thread.isAlive)
    assertEquals(guestToken, LoveAnniversaryMilestoneScheduler.readBootCopy(context)?.token)
    assertEquals(guestToken, AccountDataVault.currentToken(context))

    finishSwitch(switching)

    assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))
    assertEquals(AccountDataVault.tokenOf(scopeOfBob), AccountDataVault.currentToken(context))
  }

  // The outgoing couple's date survives a logout in the ViewModel and its collector arms the guest scope with it again.
  // A reboot afterwards must not bring those alarms back: the guest has no couple profile and no relationship that backs the date.
  @Test
  fun androidLogoutDoesNotReplayTheOutgoingCouplesBootCopy() {
    val email = "w4@x.com"
    val uid = "uid_w4"
    val start = System.currentTimeMillis() - 10 * 86_400_000L
    val startText = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(start))
    val vm = androidVm()
    runBlocking {
      dao.insertUserAccount(
        UserAccountEntity(uid = uid, email = email, passwordHash = "h", salt = "s", displayName = "Me", coupleCode = "MMMM-1111", sessionToken = "s-w4")
      )
      dao.insertOnlineUser(
        OnlineUserEntity(
          uid = uid, displayName = "Me", coupleCode = "MMMM-1111", partnerId = "uid_p", relationshipId = "rel1",
          status = OnlineStatus.COUPLED, isProfileSetup = true
        )
      )
      dao.insertOnlineUser(OnlineUserEntity(uid = "uid_p", displayName = "Pat", coupleCode = "PPPP-2222", isProfileSetup = true))
      dao.insertOnlineRelationship(
        OnlineRelationshipEntity(relationshipId = "rel1", user1 = uid, user2 = "uid_p", startDate = start, startDateText = startText)
      )
    }
    val accountToken = AccountDataVault.tokenOf(AccountDataVault.scopeOf(email))
    switchAccount(vaultOf(vm), AccountDataVault.scopeOf(email))
    runBlocking { vm.onlineRepo.setCurrentUserId(uid) }
    awaitArmed("the paired account's love milestones", *loveCodes)
    await("the boot copy of the paired account") {
      LoveAnniversaryMilestoneScheduler.readBootCopy(context)?.let { it.token == accountToken && it.startMillis == start } == true
    }
    val before = androidScheduler(vm).completedRuns.value

    act(vm, "Signed out") { vm.logout() }
    await("the cancel-all and the resync commands to finish") { androidScheduler(vm).completedRuns.value >= before + 2 }
    repeat(20) {
      shadowOf(Looper.getMainLooper()).idle()
      parkFor(25_000_000L)
    }

    val copy = LoveAnniversaryMilestoneScheduler.readBootCopy(context)
    assertNotNull("premise (w4): the collector replays the outgoing date under the guest scope", copy)
    assertEquals(AccountDataVault.tokenOf(guestScope), copy!!.token)
    assertEquals(startText, copy.startText)

    val manager = context.getSystemService(AlarmManager::class.java)
    shadowOf(manager).scheduledAlarms.toList().forEach { it.operation?.let { op -> manager.cancel(op) } }
    runBlocking { LoveAnniversaryMilestoneScheduler.rearmFromBootCopy(context, dao) }

    assertEquals(
      emptyList<Int>(),
      alarms().map { it.first }.filter { it in 300000..599999 && it != 500001 }
    )
    assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))
  }

  // ---- E. language of the armed love milestone texts (real scheduler, extras read from the PendingIntent) ----

  private val loveKinds = listOf(
    300030 to ReminderKind.LOVE_MILESTONE_THREE_DAYS,
    400030 to ReminderKind.LOVE_MILESTONE_ONE_DAY,
    500030 to ReminderKind.LOVE_MILESTONE_DAY_OF
  )

  private fun armedAlarm(code: Int) =
    shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .firstOrNull { shadowOf(it.operation).requestCode == code }

  // Account token stored in the PendingIntent of the alarm with this request code.
  private fun ownerOfAlarm(code: Int): String? =
    armedAlarm(code)?.let { shadowOf(it.operation).savedIntent.getStringExtra(ReminderAlarmReceiver.EXTRA_OWNER) }

  // Title and message stored in the PendingIntent of the alarm with this request code.
  private fun extrasOf(code: Int): Pair<String, String>? = armedAlarm(code)?.let {
    val intent = shadowOf(it.operation).savedIntent
    intent.getStringExtra(ReminderAlarmReceiver.EXTRA_TITLE).orEmpty() to
      intent.getStringExtra(ReminderAlarmReceiver.EXTRA_MESSAGE).orEmpty()
  }

  // The date the day-of alarm with this request code fires on, as the notification text writes it.
  private fun dateOfAlarm(code: Int): String? = armedAlarm(code)?.let {
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it.triggerAtTime))
  }

  // No partner: the blank name reaches the shared text table, which writes the default word of the language.
  private fun expectedLoveText(kind: ReminderKind, english: Boolean, title: String, date: String): ReminderText =
    ReminderTexts.of(kind, english, ReminderTextArgs(title = title, dateText = date, partnerName = ""))

  private fun dayName30(english: Boolean): String =
    LoveMilestoneRules.DAY_MILESTONES.first { it.days == 30 }.let { if (english) it.en else it.vi }

  private fun dayOfReads(english: Boolean): Boolean {
    val date = dateOfAlarm(500030) ?: return false
    val text = expectedLoveText(ReminderKind.LOVE_MILESTONE_DAY_OF, english, dayName30(english), date)
    return extrasOf(500030) == (text.title to text.body)
  }

  private fun allLoveTextsRead(english: Boolean): Boolean {
    val date = dateOfAlarm(500030) ?: return false
    return loveKinds.all { (code, kind) ->
      val text = expectedLoveText(kind, english, dayName30(english), date)
      extrasOf(code) == (text.title to text.body)
    }
  }

  @Test
  fun androidEnglishDayAndAnnualMilestonesCarryEnglishNames() {
    androidVm()
    // 510001 is the day-of alarm of the 1-year anniversary, armed because the default start date is today.
    awaitArmed("the love milestone collector", 500030, 510001)

    val day = expectedLoveText(ReminderKind.LOVE_MILESTONE_DAY_OF, true, dayName30(true), dateOfAlarm(500030)!!)
    assertEquals(day.title to day.body, extrasOf(500030))
    val annual = expectedLoveText(ReminderKind.LOVE_MILESTONE_DAY_OF, true, LoveMilestoneRules.annualName(1, true), dateOfAlarm(510001)!!)
    assertEquals(annual.title to annual.body, extrasOf(510001))
  }

  @Test
  fun androidLanguageSwitchRearmsTheLoveMilestonesInTheNewLanguage() {
    val vm = androidVm()
    vm.setLanguage(AppLanguage.VI)
    await("the love milestones to be armed with a Vietnamese day-of text") {
      alarmsWithCodes(*loveCodes).size == loveCodes.size && dayOfReads(false)
    }
    val vietnamese = alarmsWithCodes(*loveCodes)
    val before = androidScheduler(vm).completedRuns.value

    vm.setLanguage(AppLanguage.EN)

    await("the resync after the switch to English to re-arm the texts") {
      androidScheduler(vm).completedRuns.value > before && allLoveTextsRead(true)
    }
    assertEquals("same request codes and trigger times", vietnamese, alarmsWithCodes(*loveCodes))
    val date = dateOfAlarm(500030)!!
    val expected = expectedLoveText(ReminderKind.LOVE_MILESTONE_DAY_OF, true, dayName30(true), date)
    assertEquals(expected.title, extrasOf(500030)!!.first)
    assertEquals(expected.body, extrasOf(500030)!!.second)
  }

  @Test
  fun androidEnglishLoveMilestoneWithoutPartnerSaysYourPartner() {
    androidVm()
    awaitArmed("the love milestone collector", *loveCodes)

    val message = extrasOf(500030)!!.second
    assertTrue(message, message.contains("your partner"))
    assertFalse(message, message.contains("người ấy"))
  }
}
