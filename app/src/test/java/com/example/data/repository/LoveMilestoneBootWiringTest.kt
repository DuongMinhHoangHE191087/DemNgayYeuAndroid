package com.example.data.repository

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.AndroidReminderScheduler
import com.example.alarm.LoveAnniversaryMilestoneScheduler
import com.example.alarm.LoveMilestoneArgs
import com.example.alarm.ReminderAlarmReceiver
import com.example.data.auth.fakes.FakeAuthGateway
import com.example.data.db.AccountDataVault
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.RelationshipStatus
import com.example.data.model.UserAccountEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Bản sao cột mốc yêu dùng cho lúc khởi động lại máy đi theo đúng tài khoản qua vault và AuthRepository thật. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoveMilestoneBootWiringTest {

  private class Cleanup : RemoteAccountCleanup {
    override suspend fun deleteCloudData(uid: String, coupleCode: String) {}

    override suspend fun deleteAuthIdentity() {}
  }

  // Context mà lần lấy AlarmManager đầu tiên bị giữ tới khi `release` được nhả: lúc đó lần cài đang giữ armLock.
  private class ParkingContext(private val base: Context) : ContextWrapper(base) {
    val reached = CountDownLatch(1)
    val release = CountDownLatch(1)
    val seenTokens = CopyOnWriteArraySet<String>()

    override fun getSystemService(name: String): Any? {
      if (name == Context.ALARM_SERVICE) {
        if (reached.count > 0L) {
          reached.countDown()
          release.await(10, TimeUnit.SECONDS)
        }
        seenTokens.add(AccountDataVault.currentToken(base))
      }
      return super.getSystemService(name)
    }
  }

  // Context mà lần lấy AlarmManager thứ hai chạy `switchNow`: tức là giữa hai lần đặt báo thức của cùng một lần cài đang giữ armLock.
  // Báo thức đầu mang token cũ, các báo thức sau mang token mới. Lần thứ tư chạy thêm `switchBackNow` (nếu có) để token quay về như cũ.
  private class SwitchingContext(
    private val base: Context,
    private val switchNow: () -> Unit,
    private val switchBackNow: () -> Unit = {}
  ) : ContextWrapper(base) {
    val seenTokens = CopyOnWriteArraySet<String>()
    private var calls = 0

    override fun getSystemService(name: String): Any? {
      if (name == Context.ALARM_SERVICE) {
        when (++calls) {
          2 -> switchNow()
          4 -> switchBackNow()
        }
        seenTokens.add(AccountDataVault.currentToken(base))
      }
      return super.getSystemService(name)
    }
  }

  // Context chạy `hooks[n]` ở lần lấy AlarmManager thứ n (lần lấy nằm ngay trước khi một báo thức được đóng dấu chủ) và đếm số lần lấy.
  // Một lần cài cùng tham số luôn lấy cùng số lần, nên đếm thử một lần cài rồi đặt hook vào lần lấy thứ hai của lượt sau là biết chắc nó rơi vào lượt nào.
  private class HookedContext(private val base: Context, private val hooks: Map<Int, () -> Unit> = emptyMap()) : ContextWrapper(base) {
    val seenTokens = CopyOnWriteArraySet<String>()
    var calls = 0
      private set

    override fun getSystemService(name: String): Any? {
      if (name == Context.ALARM_SERVICE) {
        hooks[++calls]?.invoke()
        seenTokens.add(AccountDataVault.currentToken(base))
      }
      return super.getSystemService(name)
    }
  }

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var repoScope: CoroutineScope
  private lateinit var schedulerScope: CoroutineScope
  private lateinit var scheduler: AndroidReminderScheduler
  private lateinit var vault: AccountDataVault
  private val originalZone: TimeZone = TimeZone.getDefault()
  private val dao get() = db.inLoveDao()

  // Phải là null lúc đổi tài khoản, nếu không lần đồng bộ sau đổi sẽ tự cài lại cột mốc dưới token mới.
  private var args: LoveMilestoneArgs? = null

  private val dayMillis = 86_400_000L
  private val startA = System.currentTimeMillis() - 10 * dayMillis
  private val startB = System.currentTimeMillis() - 40 * dayMillis
  private val scopeA = AccountDataVault.scopeOf("a@x.com")
  private val scopeB = AccountDataVault.scopeOf("b@x.com")

  private fun textOf(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(millis))

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    listOf("inlove_account_scope", "inlove_auth_prefs", "inlove_unlock_attempts", "love_milestone_boot").forEach {
      context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
    }
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    repoScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scheduler = AndroidReminderScheduler(context, dao, { args }, schedulerScope)
    vault = AccountDataVault(
      context,
      db,
      beforeSwitch = { scheduler.cancelAllNow() },
      afterSwitch = { scheduler.requestResync() }
    )
    runBlocking { vault.switchTo(AccountDataVault.GUEST) }
  }

  @After
  fun tearDown() {
    runBlocking { repoScope.coroutineContext.job.cancelAndJoin() }
    schedulerScope.cancel()
    db.close()
    TimeZone.setDefault(originalZone)
  }

  private fun waitRuns(count: Int) {
    runBlocking { withTimeout(10_000) { scheduler.completedRuns.first { it >= count } } }
  }

  // Một lần đổi tài khoản của vault chạy hai lệnh: hủy rồi đồng bộ lại.
  private fun switchTo(scope: String) {
    val before = scheduler.completedRuns.value
    runBlocking { vault.switchTo(scope) }
    waitRuns(before + 2)
  }

  private fun addAccount(uid: String, email: String) = runBlocking {
    dao.insertUserAccount(
      UserAccountEntity(uid = uid, email = email, passwordHash = "h", salt = "s", displayName = uid, coupleCode = "C-$uid", sessionToken = "s-$uid")
    )
  }

  private fun addOwner(uid: String, email: String, start: Long) {
    addAccount(uid, email)
    runBlocking {
      dao.insertOnlineRelationship(
        OnlineRelationshipEntity(relationshipId = "r-$uid", user1 = uid, user2 = "p-$uid", startDate = start, startDateText = textOf(start))
      )
    }
  }

  private fun milestoneAlarms() =
    shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
      .filter { shadowOf(it.operation).requestCode.let { code -> code in 300000..599999 && code != 500001 } }

  private fun milestoneCodes(): List<Int> = milestoneAlarms().map { shadowOf(it.operation).requestCode }.sorted()

  // Chủ (token) mà mỗi báo thức cột mốc đang mang, đọc đúng chỗ ReminderAlarmReceiver đọc khi báo thức nổ.
  private fun milestoneOwners(): Set<String?> =
    milestoneAlarms().map { shadowOf(it.operation).savedIntent.getStringExtra(ReminderAlarmReceiver.EXTRA_OWNER) }.toSet()

  private fun dropAlarms() {
    val manager = context.getSystemService(AlarmManager::class.java)
    shadowOf(manager).scheduledAlarms.toList().forEach { it.operation?.let { op -> manager.cancel(op) } }
  }

  private fun bootCodes(): List<Int> {
    dropAlarms()
    runBlocking { LoveAnniversaryMilestoneScheduler.rearmFromBootCopy(context, dao) }
    return milestoneCodes()
  }

  private fun armDirect(start: Long, partner: String): List<Int> {
    LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(context, start, textOf(start), partner)
    return milestoneCodes()
  }

  private val day30Codes = listOf(300030, 400030, 500030)

  // Một lần khởi động lại bị giữ ở giữa lúc đọc DB của cặp: `meanwhile` chạy lúc đó, rồi lần khởi động lại được nhả và trả kết quả.
  private fun pausedReplayResult(meanwhile: () -> Unit): Int {
    val reached = CompletableDeferred<Unit>()
    val gate = CompletableDeferred<Unit>()
    val realDao = dao
    val pausing = object : InLoveDao by realDao {
      override suspend fun getAllRelationshipsSync(): List<OnlineRelationshipEntity> {
        reached.complete(Unit)
        gate.await()
        return realDao.getAllRelationshipsSync()
      }
    }
    val replay = schedulerScope.async { LoveAnniversaryMilestoneScheduler.rearmFromBootCopy(context, pausing) }
    runBlocking { withTimeout(10_000) { reached.await() } }
    meanwhile()
    gate.complete(Unit)
    return runBlocking { withTimeout(10_000) { replay.await() } }
  }

  @Test
  fun bootCopyFollowsTheAccountThroughSwitches() {
    addOwner("uA", "a@x.com", startA)
    addOwner("uB", "b@x.com", startB)

    switchTo(scopeA)
    args = LoveMilestoneArgs(startA, textOf(startA), "An")
    val beforeArm = scheduler.completedRuns.value
    scheduler.requestResync()
    waitRuns(beforeArm + 1)
    args = null
    val codesA = milestoneCodes()
    assertTrue("the resync must arm A's love milestones", codesA.containsAll(day30Codes))
    assertEquals(AccountDataVault.tokenOf(scopeA), LoveAnniversaryMilestoneScheduler.readBootCopy(context)?.token)

    switchTo(scopeB)
    assertEquals(emptyList<Int>(), bootCodes())
    assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))

    val codesB = armDirect(startB, "Binh")
    assertTrue(codesB.isNotEmpty())
    assertFalse("B is past its day 30", codesB.any { it in day30Codes })
    assertNotEquals(codesA, codesB)
    assertEquals(
      LoveAnniversaryMilestoneScheduler.BootCopy(startB, textOf(startB), "Binh", AccountDataVault.tokenOf(scopeB)),
      LoveAnniversaryMilestoneScheduler.readBootCopy(context)
    )
    assertEquals(codesB, bootCodes())

    switchTo(scopeA)
    assertEquals(emptyList<Int>(), bootCodes())
    assertEquals(codesA, armDirect(startA, "An"))
    val copy = LoveAnniversaryMilestoneScheduler.readBootCopy(context)
    assertEquals(AccountDataVault.currentToken(context), copy?.token)
    assertEquals(AccountDataVault.tokenOf(scopeA), copy?.token)
    assertEquals(codesA, bootCodes())
  }

  private fun repo(): AuthRepository {
    val online = OnlineCoupleRepository(dao, context, repoScope, useFirestore = false)
    return AuthRepository(dao, online, context, repoScope, FakeAuthGateway(), RecordingSessionHooks(), remoteCleanup = Cleanup(), vault = vault)
  }

  private val email = "boot-test@inlove.app"

  // Đăng ký thật vào tài khoản mới, gắn một cặp ACTIVE với chính tài khoản đó, cài cột mốc và kiểm tra bản sao cùng lần khởi động lại.
  private fun registeredWithArmedMilestones(repo: AuthRepository): List<Int> {
    val before = scheduler.completedRuns.value
    val (ok, msg) = runBlocking { repo.register("Tester", email, "Passw0rd!x", "Passw0rd!x", keepGuestData = false) }
    assertTrue("register must succeed: $msg", ok)
    waitRuns(before + 2)
    val uid = runBlocking { dao.getAllUserAccounts().first().single { it.email == email }.uid }
    runBlocking {
      dao.insertOnlineRelationship(
        OnlineRelationshipEntity(relationshipId = "r1", user1 = uid, user2 = "partner", startDate = startA, startDateText = textOf(startA))
      )
    }
    val armed = armDirect(startA, "An")
    assertTrue(armed.containsAll(day30Codes))
    assertEquals(AccountDataVault.tokenOf(AccountDataVault.scopeOf(email)), LoveAnniversaryMilestoneScheduler.readBootCopy(context)?.token)
    assertEquals("positive control: a reboot brings the signed-in account's milestones back", armed, bootCodes())
    return armed
  }

  @Test
  fun logoutForgetsTheBootCopyRightAway() {
    val repo = repo()
    registeredWithArmedMilestones(repo)

    val before = scheduler.completedRuns.value
    runBlocking { repo.logout() }

    assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))
    waitRuns(before + 2)
    assertEquals(emptyList<Int>(), bootCodes())
  }

  @Test
  fun deletingTheAccountForgetsTheBootCopyRightAway() {
    val repo = repo()
    registeredWithArmedMilestones(repo)

    val before = scheduler.completedRuns.value
    val result = runBlocking { repo.deleteCurrentAccount() }

    assertTrue("delete must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))
    waitRuns(before + 2)
    assertEquals(emptyList<Int>(), bootCodes())
  }

  // Lần khởi động lại bị giữ ở giữa lúc đọc DB: sau khi đổi tài khoản xong, nó không được cài lại cột mốc của tài khoản cũ.
  @Test
  fun replayPausedInsideADatabaseReadDoesNotRearmTheOldAccountAfterASwitch() {
    addOwner("uA", "a@x.com", startA)
    addOwner("uB", "b@x.com", startB)
    switchTo(scopeA)
    assertTrue(armDirect(startA, "An").containsAll(day30Codes))
    assertEquals(AccountDataVault.tokenOf(scopeA), LoveAnniversaryMilestoneScheduler.readBootCopy(context)?.token)
    dropAlarms()

    val armed = pausedReplayResult { switchTo(scopeB) }

    assertEquals("the replay must give up once the account it started for is gone", 0, armed)
    assertEquals(emptyList<Int>(), milestoneCodes())
    assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))
  }

  // Hủy rồi cài lại đúng bản sao cũ trong lúc một lần khởi động lại đang bị giữ: lần đó đã cũ, không được cài lại gì.
  @Test
  fun replayDoesNotRearmAfterACancelEvenWhenTheSameCopyIsWrittenAgain() {
    addOwner("uA", "a@x.com", startA)
    switchTo(scopeA)
    assertTrue(armDirect(startA, "An").containsAll(day30Codes))
    dropAlarms()

    val armed = pausedReplayResult {
      runBlocking { scheduler.cancelAllNow() }
      assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))
      assertTrue(armDirect(startA, "An").containsAll(day30Codes))
      dropAlarms()
    }

    assertEquals("a replay that was overtaken by a cancel must not arm, even if an identical copy came back", 0, armed)
    assertEquals(emptyList<Int>(), milestoneCodes())
    assertTrue("positive control: a fresh reboot still brings the milestones back", bootCodes().containsAll(day30Codes))
  }

  // Hủy mà không đổi tài khoản và không ghi lại bản sao nào trong lúc một lần khởi động lại đang bị giữ: lần đó đã cũ, không được cài lại gì.
  @Test
  fun replayDoesNotRearmAfterACancelWithoutAnAccountSwitch() {
    addOwner("uA", "a@x.com", startA)
    switchTo(scopeA)
    assertTrue(armDirect(startA, "An").containsAll(day30Codes))
    dropAlarms()

    val armed = pausedReplayResult { runBlocking { scheduler.cancelAllNow() } }

    assertEquals("a replay that was overtaken by a cancel must not arm while the same account is in force", 0, armed)
    assertEquals(emptyList<Int>(), milestoneCodes())
    assertNull("the cancel's forget must stand", LoveAnniversaryMilestoneScheduler.readBootCopy(context))
    assertEquals("positive control: the account was not switched", AccountDataVault.tokenOf(scopeA), AccountDataVault.currentToken(context))
  }

  // Một bản sao mới hơn được ghi (không có lệnh hủy nào) trong lúc một lần khởi động lại đang bị giữ: lần đó đã cũ, không được cài hay đè bản sao.
  @Test
  fun replayDoesNotOverwriteANewerCopyWrittenWhileItWasPaused() {
    addOwner("uA", "a@x.com", startA)
    switchTo(scopeA)
    assertTrue(armDirect(startA, "An").containsAll(day30Codes))
    dropAlarms()

    val newerStart = System.currentTimeMillis() - 20 * dayMillis
    val armed = pausedReplayResult {
      assertTrue("positive control: the newer copy arms its own milestones", armDirect(newerStart, "An").isNotEmpty())
      dropAlarms()
    }

    assertEquals("a replay that was overtaken by a newer copy must not arm", 0, armed)
    assertEquals(emptyList<Int>(), milestoneCodes())
    assertEquals(
      "the replay must leave the newer copy as it was",
      LoveAnniversaryMilestoneScheduler.BootCopy(newerStart, textOf(newerStart), "An", AccountDataVault.tokenOf(scopeA)),
      LoveAnniversaryMilestoneScheduler.readBootCopy(context)
    )
  }

  // Chờ một lần đổi đã chạy tới hook hủy: epoch tăng ngay trước hook, sau khi rào (nếu có) đã dựng.
  private fun awaitSwitchStarted(epochBefore: Long) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (AccountDataVault.epoch.get() == epochBefore && System.nanoTime() < deadline) Thread.sleep(10)
    assertNotEquals("the switch must have started", epochBefore, AccountDataVault.epoch.get())
  }

  // Lần khởi động lại đang cài cột mốc (giữ armLock) bị giữ ở lần lấy AlarmManager đầu tiên, rồi một lần đổi sang B bắt đầu. `prepare` chạy ngay trước lần khởi động lại.
  // Lần đổi phải chờ lần cài xong, token không đổi suốt lúc cài. Lần đổi đã vào vùng rào từ trước khi lần cài xong nên lần cài tự thu hồi hết và báo 0;
  // lệnh hủy của lần đổi chạy sau và chỉ còn quét lại.
  private fun assertTheSwitchWaitsForAReplayThatIsArming(prepare: () -> Unit = {}) {
    addOwner("uA", "a@x.com", startA)
    switchTo(scopeA)
    assertTrue(armDirect(startA, "An").containsAll(day30Codes))
    dropAlarms()
    prepare()

    val tokenA = AccountDataVault.tokenOf(scopeA)
    val parking = ParkingContext(context)
    val replay = schedulerScope.async { LoveAnniversaryMilestoneScheduler.rearmFromBootCopy(parking, dao) }

    val before = scheduler.completedRuns.value
    // Nhả chốt từ luồng test, không từ coroutine: pool Default có thể đã kẹt hết luồng (một luồng chờ chốt, một luồng chờ khóa).
    val (switch, switchedWhileArming, tokenWhileArming) = try {
      assertTrue("the replay must reach its final arm", parking.reached.await(10, TimeUnit.SECONDS))
      val epochBefore = AccountDataVault.epoch.get()
      val started = schedulerScope.async { vault.switchTo(scopeB) }
      awaitSwitchStarted(epochBefore)
      val finished = runBlocking { withTimeoutOrNull(1_000) { started.await() } } != null
      Triple(started, finished, AccountDataVault.currentToken(context))
    } finally {
      parking.release.countDown()
    }
    val armed = runBlocking { withTimeout(10_000) { replay.await() } }
    runBlocking { withTimeout(10_000) { switch.await() } }

    assertFalse("the switch must wait for a replay that is inside its final arm", switchedWhileArming)
    assertEquals("the account must not change while the replay is arming", tokenA, tokenWhileArming)
    // Sau hai assert đầu: một lần chạy RED phải fail ở assert định thử, không phải ở lần chờ hết giờ.
    waitRuns(before + 2)
    assertEquals("a replay that a switch began under must take its own alarms back", 0, armed)
    assertEquals("every alarm the replay armed carries A's token", setOf(tokenA), parking.seenTokens.toSet())
    assertEquals(AccountDataVault.tokenOf(scopeB), AccountDataVault.currentToken(context))
    assertEquals("nothing the replay armed may survive the switch", emptyList<Int>(), milestoneCodes())
    assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))
  }

  @Test
  fun accountSwitchWaitsForAReplayThatIsArmingItsMilestones() {
    assertTheSwitchWaitsForAReplayThatIsArming()
  }

  // Cùng tình huống nhưng lần đổi bắt đầu bằng việc khôi phục một lần đổi dở (dấu pending): lần khôi phục cũng phải chạy hook hủy và chờ lần cài, không được ghi phạm vi ngay.
  @Test
  fun pendingSwitchRecoveryWaitsForAReplayThatIsArmingItsMilestones() {
    val prefs = context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE)
    assertTheSwitchWaitsForAReplayThatIsArming {
      assertTrue(prefs.edit().putString("pending", "$scopeA|$scopeB|false").commit())
    }

    assertFalse("positive control: the recovery consumed the pending marker", prefs.contains("pending"))
  }

  // Một lần đổi sang B bị giữ ngay sau lệnh hủy của nó (phạm vi vẫn là A). Trong lúc đó A ghi lại bản sao rồi một lần khởi động lại MỚI bắt đầu.
  // Lần đó không được cài gì: lần đổi ghi phạm vi B ngay sau, nên báo thức của nó sẽ mang token B với dữ liệu của A mà không còn lệnh hủy nào chạy sau.
  @Test
  fun aFreshReplayInsideAnAccountSwitchDoesNotArmTheOutgoingAccountsMilestones() {
    addOwner("uA", "a@x.com", startA)
    switchTo(scopeA)
    val parked = CountDownLatch(1)
    val resume = CountDownLatch(1)
    val parkingVault = AccountDataVault(
      context, db,
      beforeSwitch = {
        scheduler.cancelAllNow()
        parked.countDown()
        resume.await(10, TimeUnit.SECONDS)
      },
      afterSwitch = { scheduler.requestResync() }
    )

    val before = scheduler.completedRuns.value
    val switch = schedulerScope.async { parkingVault.switchTo(scopeB) }
    val armed = try {
      assertTrue("the switch must park right after its cancel", parked.await(10, TimeUnit.SECONDS))
      assertEquals("positive control: the scope is still A", AccountDataVault.tokenOf(scopeA), AccountDataVault.currentToken(context))
      assertTrue("positive control: A writes its copy after the cancel", armDirect(startA, "An").containsAll(day30Codes))
      dropAlarms()
      runBlocking { LoveAnniversaryMilestoneScheduler.rearmFromBootCopy(context, dao) }
    } finally {
      resume.countDown()
    }
    runBlocking { withTimeout(10_000) { switch.await() } }
    waitRuns(before + 2)

    assertEquals("a replay that starts inside an account switch must not arm anything", 0, armed)
    assertEquals(AccountDataVault.tokenOf(scopeB), AccountDataVault.currentToken(context))
    assertEquals("nothing of A's may survive the switch", emptyList<Int>(), milestoneCodes())
  }

  // Hook hủy của một lần đổi ném lỗi: rào phải hạ xuống, nếu không mọi lần khởi động lại sau đó (và mọi test chạy sau trong cùng JVM) bị từ chối mãi.
  @Test
  fun aSwitchWhoseCancelHookFailsDoesNotLockTheReplayOut() {
    addOwner("uA", "a@x.com", startA)
    switchTo(scopeA)
    val failing = AccountDataVault(context, db, beforeSwitch = { error("cancel failed") })

    val failure = runCatching { runBlocking { failing.switchTo(scopeB) } }.exceptionOrNull()

    assertEquals("cancel failed", failure?.message)
    assertEquals(AccountDataVault.tokenOf(scopeA), AccountDataVault.currentToken(context))
    assertTrue(armDirect(startA, "An").containsAll(day30Codes))
    assertTrue("the replay must still arm after the failed switch", bootCodes().containsAll(day30Codes))
  }

  // Lần khởi động lại đang cài cột mốc (giữ armLock), giữa hai báo thức có một lần ghi phạm vi sang B không qua armLock và không qua hook nào.
  // Với `switchBack`, lần lấy AlarmManager thứ tư đưa phạm vi về lại A: token cuối giống token đầu nhưng các báo thức ở giữa mang token của B.
  // `beforeReplay` chạy ngay trước lần khởi động lại. Vault ở đây không có hook: vault có hook sẽ chờ lần cài đang giữ armLock và kẹt.
  private fun replayThatIsSwitchedMidArm(
    switchBack: Boolean = false,
    beforeReplay: () -> Unit = {}
  ): Pair<Int, SwitchingContext> {
    addOwner("uA", "a@x.com", startA)
    switchTo(scopeA)
    assertTrue(armDirect(startA, "An").containsAll(day30Codes))
    dropAlarms()
    beforeReplay()

    val switching = SwitchingContext(
      context,
      switchNow = { runBlocking { AccountDataVault(context, db).switchTo(scopeB) } },
      switchBackNow = { if (switchBack) runBlocking { AccountDataVault(context, db).switchTo(scopeA) } }
    )
    val replay = schedulerScope.async { LoveAnniversaryMilestoneScheduler.rearmFromBootCopy(switching, dao) }
    return runBlocking { withTimeout(10_000) { replay.await() } } to switching
  }

  // Báo thức đặt sau lần đổi mang token của B nhưng dữ liệu của A, và không còn lệnh hủy nào chạy sau: lần cài phải tự thu hồi hết, kể cả bản sao.
  private fun assertTheReplayTookEverythingBack(armed: Int, switching: SwitchingContext) {
    assertEquals(
      "positive control: the scope changed between two of the replay's alarms",
      setOf(AccountDataVault.tokenOf(scopeA), AccountDataVault.tokenOf(scopeB)),
      switching.seenTokens.toSet()
    )
    assertEquals(AccountDataVault.tokenOf(scopeB), AccountDataVault.currentToken(context))
    assertEquals("a replay that lost its account must report nothing armed", 0, armed)
    assertEquals("nothing of A's may survive under B's token", emptyList<Int>(), milestoneCodes())
    assertNull("A's boot copy must not outlive the switch", LoveAnniversaryMilestoneScheduler.readBootCopy(context))
  }

  @Test
  fun replayTakesBackItsAlarmsWhenTheScopeChangesWhileItIsArming() {
    val (armed, switching) = replayThatIsSwitchedMidArm()

    assertTheReplayTookEverythingBack(armed, switching)
  }

  // Cùng tình huống nhưng phạm vi đổi nhờ khôi phục một lần đổi dở (dấu pending) ở đầu `switchTo`: nhánh này không chạy hook nào.
  @Test
  fun replayTakesBackItsAlarmsWhenAPendingSwitchIsRecoveredWhileItIsArming() {
    val prefs = context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE)
    val (armed, switching) = replayThatIsSwitchedMidArm {
      assertTrue(prefs.edit().putString("pending", "$scopeA|$scopeB|false").commit())
    }

    assertFalse("positive control: the recovery consumed the pending marker", prefs.contains("pending"))
    assertTheReplayTookEverythingBack(armed, switching)
  }

  // Phạm vi đi sang B rồi về lại A trong lúc lần cài còn chạy: token cuối giống token đầu nên kiểm tra token không thấy gì,
  // nhưng những báo thức đặt lúc phạm vi là B mang token B với dữ liệu của A.
  @Test
  fun replayTakesBackItsAlarmsWhenTheScopeGoesToAnotherAccountAndBackWhileItIsArming() {
    val (armed, switching) = replayThatIsSwitchedMidArm(switchBack = true)

    assertEquals(
      "positive control: the scope was B between two of the replay's alarms",
      setOf(AccountDataVault.tokenOf(scopeA), AccountDataVault.tokenOf(scopeB)),
      switching.seenTokens.toSet()
    )
    assertEquals(AccountDataVault.tokenOf(scopeA), AccountDataVault.currentToken(context))
    assertEquals("a replay that the scope left and came back to must take its alarms back", 0, armed)
    assertEquals("nothing armed under B's token may survive", emptyList<Int>(), milestoneCodes())
    assertNull(LoveAnniversaryMilestoneScheduler.readBootCopy(context))
  }

  // Khách có ngày yêu chỉ nằm trong hồ sơ cặp (chưa có cặp online): bản sao khởi động ghi ngày bằng chữ, `startMillis = 0`, token của khách.
  // Gọi đúng một lần mỗi test: hồ sơ cặp chỉ có một dòng (id = 1). `armContext` cho phép đếm số lần lấy AlarmManager của một lượt cài.
  private fun armGuestFromProfileText(armContext: Context = context) {
    runBlocking {
      dao.insertCoupleProfile(
        CoupleProfileEntity(
          partner1Name = "A", partner1Birthday = "", partner1ProfilePicture = "",
          partner2Name = "B", partner2Birthday = "", partner2ProfilePicture = "",
          anniversaryDate = textOf(startA)
        )
      )
    }
    LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore(armContext, 0L, textOf(startA), "An")
    assertEquals(
      "positive control: the copy is the guest's",
      AccountDataVault.tokenOf(AccountDataVault.GUEST),
      LoveAnniversaryMilestoneScheduler.readBootCopy(context)?.token
    )
  }

  // Số lần lấy AlarmManager của đúng một lượt cài bản sao khách: lượt thứ hai trong cùng lần khởi động lại sẽ lấy thêm đúng chừng đó lần.
  // Đo bằng một lượt cài thử (không hook) thay vì ghi cứng một con số, vì số cột mốc sắp tới phụ thuộc ngày chạy test.
  private fun armGuestAndCountLookupsPerPass(): Int {
    val counting = HookedContext(context)
    armGuestFromProfileText(counting)
    assertTrue("positive control: a pass looks the alarm service up at least twice", counting.calls >= 2)
    return counting.calls
  }

  // Vault KHÔNG có hook: vault có hook sẽ chờ armLock mà lần khởi động lại đang giữ, rồi kẹt.
  private fun adoptWithoutHooks(scope: String) {
    runBlocking { AccountDataVault(context, db).adoptCurrentInto(scope) }
  }

  private fun switchWithoutHooks(scope: String) {
    runBlocking { AccountDataVault(context, db).switchTo(scope) }
  }

  // Hook của lượt hai phải rơi vào lúc lượt cài đang đặt báo thức, không phải lúc nó đang thu hồi hết: lượt thu hồi cũng lấy AlarmManager
  // cho từng mã yêu cầu (hàng trăm lần) nên chỉ đếm số lần lấy thì không phân biệt được hai chỗ. Ngăn xếp gọi thì phân biệt được.
  private class Landing {
    @Volatile
    var whileArming: Boolean? = null
  }

  private fun landingWhileArming(landing: Landing, action: () -> Unit): () -> Unit = {
    landing.whileArming = Thread.currentThread().stackTrace.any { it.methodName.startsWith("scheduleMilestonesFromFirestore") }
    action()
  }

  // Lần khởi động lại của bản sao khách, với `hooks[n]` chạy ở lần lấy AlarmManager thứ n (lần lấy nằm ngay trước khi một báo thức được đóng dấu).
  private fun guestReplayWithHooks(hooks: Map<Int, () -> Unit>): Pair<Int, HookedContext> {
    dropAlarms()
    val hooked = HookedContext(context, hooks)
    val replay = schedulerScope.async { LoveAnniversaryMilestoneScheduler.rearmFromBootCopy(hooked, dao) }
    return runBlocking { withTimeout(10_000) { replay.await() } } to hooked
  }

  // Đăng ký giữ dữ liệu khách chỉ đổi chủ, dữ liệu nằm nguyên chỗ: báo thức và bản sao đã kiểm chứng vẫn đúng, chỉ phải thuộc về tài khoản mới.
  private fun assertTheAdoptedAccountKeptItsAlarms(armed: Int, hooked: HookedContext) {
    val adopted = AccountDataVault.tokenOf(scopeA)
    assertEquals(
      "positive control: the scope was adopted between two of the replay's alarms",
      setOf(AccountDataVault.tokenOf(AccountDataVault.GUEST), adopted),
      hooked.seenTokens.toSet()
    )
    assertEquals(adopted, AccountDataVault.currentToken(context))
    assertTrue("the replay must report what it armed for the adopted account", armed > 0)
    assertTrue("the adopted account must keep its milestone alarms", milestoneCodes().containsAll(day30Codes))
    assertEquals("every alarm must be stamped with the adopted account", setOf<String?>(adopted), milestoneOwners())
    assertEquals(
      "the adopted account must keep a boot copy under its own token",
      LoveAnniversaryMilestoneScheduler.BootCopy(0L, textOf(startA), "An", adopted),
      LoveAnniversaryMilestoneScheduler.readBootCopy(context)
    )
    addAccount("uA", "a@x.com")
    assertTrue("the kept copy must still replay for the adopted account", bootCodes().containsAll(day30Codes))
  }

  private fun assertTheGuestReplayTookEverythingBack(armed: Int, scope: String) {
    assertEquals(AccountDataVault.tokenOf(scope), AccountDataVault.currentToken(context))
    assertEquals("a replay that lost its account must report nothing armed", 0, armed)
    assertEquals("nothing may survive under the new account's token", emptyList<Int>(), milestoneCodes())
    assertNull("the old copy must not outlive the switch", LoveAnniversaryMilestoneScheduler.readBootCopy(context))
  }

  // Đăng ký giữ dữ liệu khách xảy ra giữa hai báo thức của lần khởi động lại: vault chỉ ghi phạm vi, không qua hook, không qua armLock.
  // Lần cài phải cài lại dưới token mới thay vì thu hồi hết (không còn lệnh nào dựng lại báo thức sau khi đăng ký).
  @Test
  fun replayKeepsItsAlarmsAndCopyWhenGuestDataIsAdoptedWhileItIsArming() {
    armGuestFromProfileText()

    val (armed, hooked) = guestReplayWithHooks(mapOf(2 to { adoptWithoutHooks(scopeA) }))

    assertTheAdoptedAccountKeptItsAlarms(armed, hooked)
  }

  // Cùng tình huống nhưng chưa từng có phạm vi nào được ghi: lần ghi đầu tiên của `switchTo` cũng không có hook và không có hàng rào.
  @Test
  fun replayKeepsItsAlarmsAndCopyWhenTheFirstEverScopeIsAdoptedWhileItIsArming() {
    context.getSharedPreferences("inlove_account_scope", Context.MODE_PRIVATE).edit().clear().commit()
    assertEquals(
      "positive control: with no scope written the token falls back to the guest's",
      AccountDataVault.tokenOf(AccountDataVault.GUEST),
      AccountDataVault.currentToken(context)
    )
    armGuestFromProfileText()

    val (armed, hooked) = guestReplayWithHooks(mapOf(2 to { switchWithoutHooks(scopeA) }))

    assertTheAdoptedAccountKeptItsAlarms(armed, hooked)
  }

  // Sau khi nhận dữ liệu khách, một lần đổi tài khoản thật (có hàng rào) đến ngay trong cùng lần cài: lần này vẫn phải thu hồi hết.
  @Test
  fun replayStillTakesEverythingBackWhenAnAdoptionIsFollowedByARealSwitch() {
    armGuestFromProfileText()

    val (armed, hooked) = guestReplayWithHooks(mapOf(2 to { adoptWithoutHooks(scopeA) }, 4 to { switchWithoutHooks(scopeB) }))

    assertEquals(
      "positive control: the scope went guest, A, B while the replay armed",
      setOf(AccountDataVault.tokenOf(AccountDataVault.GUEST), AccountDataVault.tokenOf(scopeA), AccountDataVault.tokenOf(scopeB)),
      hooked.seenTokens.toSet()
    )
    assertTheGuestReplayTookEverythingBack(armed, scopeB)
  }

  // Lượt cài lại dưới token mới cũng chạy ngoài hàng rào của vault: một lần đổi tài khoản thật rơi vào lượt thứ hai vẫn phải thu hồi hết.
  @Test
  fun replayTakesEverythingBackWhenARealSwitchLandsInTheSecondPass() {
    val perPass = armGuestAndCountLookupsPerPass()
    val landing = Landing()

    val (armed, hooked) = guestReplayWithHooks(
      mapOf(2 to { adoptWithoutHooks(scopeA) }, (perPass + 2) to landingWhileArming(landing) { switchWithoutHooks(scopeB) })
    )

    assertTrue("positive control: the real switch landed while the replay was arming a second pass", landing.whileArming == true)
    assertEquals(
      "positive control: the scope went guest, A, B while the replay armed",
      setOf(AccountDataVault.tokenOf(AccountDataVault.GUEST), AccountDataVault.tokenOf(scopeA), AccountDataVault.tokenOf(scopeB)),
      hooked.seenTokens.toSet()
    )
    assertTheGuestReplayTookEverythingBack(armed, scopeB)
  }

  // Lượt cài lại cũng có thể bị nhận dữ liệu lần nữa (không có hàng rào, chỉ token đổi): lượt hai không được tin token cũ, cuối cùng vẫn thu hồi hết.
  @Test
  fun replayTakesEverythingBackWhenTheScopeIsAdoptedAgainWhileItRearms() {
    val perPass = armGuestAndCountLookupsPerPass()
    val landing = Landing()

    val (armed, hooked) = guestReplayWithHooks(
      mapOf(2 to { adoptWithoutHooks(scopeA) }, (perPass + 2) to landingWhileArming(landing) { adoptWithoutHooks(scopeB) })
    )

    assertTrue("positive control: the second adoption landed while the replay was arming a second pass", landing.whileArming == true)
    assertEquals(
      "positive control: the scope went guest, A, B while the replay armed",
      setOf(AccountDataVault.tokenOf(AccountDataVault.GUEST), AccountDataVault.tokenOf(scopeA), AccountDataVault.tokenOf(scopeB)),
      hooked.seenTokens.toSet()
    )
    assertTheGuestReplayTookEverythingBack(armed, scopeB)
  }

  // Lần cài không bị ai chen vào chỉ đặt báo thức đúng một lượt: lượt thứ hai chỉ dành cho trường hợp phạm vi đổi chủ giữa chừng.
  // Cũng là phép đối chứng cho cách đo `perPass` của các test trên: lượt cài thử và lượt khởi động lại lấy AlarmManager cùng số lần.
  @Test
  fun anUninterruptedReplayArmsItsMilestonesOnlyOnce() {
    val perPass = armGuestAndCountLookupsPerPass()

    val (armed, hooked) = guestReplayWithHooks(emptyMap())

    assertTrue("the replay must arm the copy it replays", armed > 0)
    assertEquals("an uninterrupted replay arms once, in a single pass", perPass, hooked.calls)
    assertEquals(setOf<String?>(AccountDataVault.tokenOf(AccountDataVault.GUEST)), milestoneOwners())
  }

  // Một lần đổi tài khoản thật (có hàng rào) rơi vào lượt đầu thì lượt hai không được chạy: nó sẽ đặt báo thức dưới token của lần chuyển đang diễn ra
  // rồi lại bị thu hồi. Hook thứ hai nằm đúng chỗ lượt hai bắt đầu: có lượt hai thì nó rơi vào lúc đặt báo thức, không có thì rơi vào lúc thu hồi.
  @Test
  fun replayDoesNotArmASecondPassWhenARealSwitchLandsInTheFirst() {
    val perPass = armGuestAndCountLookupsPerPass()
    val landing = Landing()

    val (armed, hooked) = guestReplayWithHooks(
      mapOf(2 to { switchWithoutHooks(scopeB) }, (perPass + 2) to landingWhileArming(landing) {})
    )

    assertEquals("positive control: the second hook was reached, and while sweeping", false, landing.whileArming)
    assertEquals(
      "positive control: the scope went guest, B while the replay armed",
      setOf(AccountDataVault.tokenOf(AccountDataVault.GUEST), AccountDataVault.tokenOf(scopeB)),
      hooked.seenTokens.toSet()
    )
    assertTheGuestReplayTookEverythingBack(armed, scopeB)
  }

  // Lần khởi động lại của tài khoản cũ bị giữ ở giữa một lần đọc DB. Trong lúc bị giữ: đổi sang B và cài cột mốc của B.
  // Lần cũ phải dừng, không được xóa hay đè bản sao của B.
  private fun staleReplayLeavesTheNextAccountsCopyAlone(
    pausingDao: (CompletableDeferred<Unit>, CompletableDeferred<Unit>) -> InLoveDao
  ) {
    val reached = CompletableDeferred<Unit>()
    val gate = CompletableDeferred<Unit>()
    val replay = schedulerScope.async { LoveAnniversaryMilestoneScheduler.rearmFromBootCopy(context, pausingDao(reached, gate)) }
    runBlocking { withTimeout(10_000) { reached.await() } }

    addOwner("uB", "b@x.com", startB)
    switchTo(scopeB)
    val codesB = armDirect(startB, "Binh")
    assertTrue(codesB.isNotEmpty())
    gate.complete(Unit)
    val armed = runBlocking { withTimeout(10_000) { replay.await() } }

    assertEquals("the stale replay must stop without arming anything", 0, armed)
    assertEquals(
      "the stale replay must leave the new account's copy as it was",
      LoveAnniversaryMilestoneScheduler.BootCopy(startB, textOf(startB), "Binh", AccountDataVault.tokenOf(scopeB)),
      LoveAnniversaryMilestoneScheduler.readBootCopy(context)
    )
    assertEquals(codesB, bootCodes())
  }

  // Bản sao của khách: lần đối chiếu hồ sơ bị giữ, trong lúc đó đã đổi sang B nên hồ sơ của khách biến mất khỏi DB.
  @Test
  fun staleGuestReplayDoesNotWipeTheNextAccountsCopy() {
    armGuestFromProfileText()
    val realDao = dao
    staleReplayLeavesTheNextAccountsCopyAlone { reached, gate ->
      object : InLoveDao by realDao {
        override suspend fun getCoupleProfileSync(): CoupleProfileEntity? {
          reached.complete(Unit)
          gate.await()
          return realDao.getCoupleProfileSync()
        }
      }
    }
  }

  // Cặp của A bị đọc là đã kết thúc sau khi đã đổi sang B.
  @Test
  fun staleReplayThatSeesTheOldCoupleEndedDoesNotWipeTheNextAccountsCopy() {
    addOwner("uA", "a@x.com", startA)
    switchTo(scopeA)
    assertTrue(armDirect(startA, "An").containsAll(day30Codes))
    val realDao = dao
    staleReplayLeavesTheNextAccountsCopyAlone { reached, gate ->
      object : InLoveDao by realDao {
        override suspend fun getAllRelationshipsSync(): List<OnlineRelationshipEntity> {
          reached.complete(Unit)
          gate.await()
          return realDao.getAllRelationshipsSync().map { it.copy(status = RelationshipStatus.TERMINATED) }
        }
      }
    }
  }

  // Phiên đăng nhập của A bị đọc là đã mất sau khi đã đổi sang B.
  @Test
  fun staleReplayThatSeesTheOldSessionGoneDoesNotWipeTheNextAccountsCopy() {
    addOwner("uA", "a@x.com", startA)
    switchTo(scopeA)
    assertTrue(armDirect(startA, "An").containsAll(day30Codes))
    val realDao = dao
    staleReplayLeavesTheNextAccountsCopyAlone { reached, gate ->
      object : InLoveDao by realDao {
        override fun getAllUserAccounts(): Flow<List<UserAccountEntity>> = flow {
          reached.complete(Unit)
          gate.await()
          emitAll(realDao.getAllUserAccounts().map { accounts -> accounts.map { it.copy(sessionToken = "") } })
        }
      }
    }
  }
}
