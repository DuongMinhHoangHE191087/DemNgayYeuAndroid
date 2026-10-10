package com.example.data.repository

import android.app.Activity
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.FacebookAuthGateway
import com.example.data.auth.SocialAuthException
import com.example.data.auth.SocialProfile
import com.example.data.auth.UnlockAttemptStore
import com.example.data.db.AppDatabase
import com.example.ui.util.AuthSecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeFacebook : FacebookAuthGateway {
  var profile = SocialProfile("fb-uid-1", "fan@example.com", "Fan", null)
  var failWith: SocialAuthException.Reason? = null
  var linked = false
  override suspend fun signIn(activity: Activity): SocialProfile =
    failWith?.let { throw SocialAuthException(it) } ?: profile
  override suspend fun reauthenticate(activity: Activity, expectedUid: String) {
    failWith?.let { throw SocialAuthException(it) }
    if (expectedUid != profile.uid) throw SocialAuthException(SocialAuthException.Reason.MISMATCH)
  }
  override suspend fun link(activity: Activity): SocialProfile { linked = true; return profile }
  override fun isLinked() = linked
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PinAndFacebookAuthTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var repo: AuthRepository
  private lateinit var scope: CoroutineScope
  private lateinit var activity: Activity
  private val fb = FakeFacebook()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    listOf("inlove_unlock_attempts", "inlove_auth_prefs").forEach {
      context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
    }
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val online =OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false)
    repo = AuthRepository(db.inLoveDao(), online, context, scope, isTestMode = true, facebook = fb)
    activity = Robolectric.buildActivity(Activity::class.java).setup().get()
  }

  @After
  fun tearDown() {
    runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    db.close()
  }

  private suspend fun registerWithPin(email: String = "pin@inlove.app", pin: String = "4321") {
    repo.register("Tester", email, "SecurePassword@123", "SecurePassword@123")
    repo.login(email, "SecurePassword@123", rememberMe = false)
    assertTrue(repo.setAppPin(pin).first)
    repo.lockApp()
  }

  @Test
  fun wrongPinsLockOutEvenTheCorrectPin() = runBlocking {
    registerWithPin()
    repeat(4) { assertTrue(repo.unlockWithPin("0000") is UnlockResult.Wrong) }
    assertTrue(repo.unlockWithPin("0000") is UnlockResult.Locked)
    assertTrue("correct PIN must be refused while locked", repo.unlockWithPin("4321") is UnlockResult.Locked)
    assertTrue(repo.authState.value is AuthState.PinLocked)
    assertTrue(repo.lockedUntilMillis() > System.currentTimeMillis())
  }

  @Test
  fun lockoutSurvivesANewRepositoryInstance() = runBlocking {
    registerWithPin()
    repeat(5) { repo.unlockWithPin("0000") }
    val again =AuthRepository(db.inLoveDao(), OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false), context, scope, isTestMode = true, facebook = fb)
    again.login("pin@inlove.app", "SecurePassword@123", false)
    again.lockApp()
    assertTrue(again.unlockWithPin("4321") is UnlockResult.Locked)
  }

  @Test
  fun pinIsStoredWithSlowV2Hash() = runBlocking {
    registerWithPin()
    val stored = db.inLoveDao().getUserAccountByEmail("pin@inlove.app")!!.appPin
    assertTrue(AuthSecurityManager.isPinV2(stored))
    assertTrue(repo.unlockWithPin("4321") is UnlockResult.Success)
  }

  @Test
  fun legacyPlaintextAndSha256PinsAreUpgradedOnUnlock() = runBlocking {
    registerWithPin()
    val dao = db.inLoveDao()
    val acc = dao.getUserAccountByEmail("pin@inlove.app")!!
    dao.updateUserAccount(acc.copy(appPin = "4321")) // legacy plaintext
    repo.login("pin@inlove.app", "SecurePassword@123", false); repo.lockApp()
    assertTrue(repo.unlockWithPin("4321") is UnlockResult.Success)
    assertTrue(AuthSecurityManager.isPinV2(dao.getUserAccountByEmail("pin@inlove.app")!!.appPin))

    dao.updateUserAccount(dao.getUserAccountByEmail("pin@inlove.app")!!.let { it.copy(appPin = AuthSecurityManager.hashPin("4321", it.salt)) })
    repo.login("pin@inlove.app", "SecurePassword@123", false); repo.lockApp()
    assertTrue(repo.unlockWithPin("4321") is UnlockResult.Success)
    assertTrue(AuthSecurityManager.isPinV2(dao.getUserAccountByEmail("pin@inlove.app")!!.appPin))
  }

  @Test
  fun nonNumericOrShortPinNeverUnlocks() = runBlocking {
    registerWithPin()
    assertTrue(repo.unlockWithPin("43") is UnlockResult.Wrong)
    assertTrue(repo.unlockWithPin("43a1") is UnlockResult.Wrong)
    assertTrue(repo.authState.value is AuthState.PinLocked)
  }

  @Test
  fun facebookLoginCreatesProviderOnlyAccountThatPasswordCannotUnlock() = runBlocking {
    val (ok, _) = repo.loginWithFacebook(activity, rememberMe = false)
    assertTrue(ok)
    val acc = db.inLoveDao().getUserAccountByUid("fb-uid-1")!!
    assertEquals("fan@example.com", acc.email)
    assertTrue(repo.isProviderOnly(acc))
    assertTrue(repo.setAppPin("1357").first)
    repo.lockApp()
    assertTrue(repo.unlockWithAccountPassword("oauth:facebook") is UnlockResult.Wrong)
    assertTrue(repo.authState.value is AuthState.PinLocked)
  }

  @Test
  fun facebookWithoutEmailGetsStableSyntheticEmail() = runBlocking {
    fb.profile = SocialProfile("fb-uid-2", null, null, null)
    assertTrue(repo.loginWithFacebook(activity, false).first)
    assertEquals("fb_fb-uid-2@facebook.inlove.local", db.inLoveDao().getUserAccountByUid("fb-uid-2")!!.email)
  }

  @Test
  fun facebookUnlockClearsLockoutAndUnlocks() = runBlocking {
    repo.loginWithFacebook(activity, false)
    repo.setAppPin("1357"); repo.lockApp()
    repeat(5) { repo.unlockWithPin("0000") }
    assertTrue(repo.unlockWithPin("1357") is UnlockResult.Locked)
    assertTrue(repo.unlockWithFacebook(activity) is UnlockResult.Success)
    assertTrue(repo.authState.value is AuthState.Authenticated)
    assertEquals(0L, repo.lockedUntilMillis())
  }

  @Test
  fun facebookUnlockRejectsAnotherFacebookUser() = runBlocking {
    repo.loginWithFacebook(activity, false)
    repo.setAppPin("1357"); repo.lockApp()
    fb.profile = SocialProfile("someone-else", null, null, null)
    val r = repo.unlockWithFacebook(activity)
    assertEquals(UnlockResult.Error(SocialAuthException.Reason.MISMATCH), r)
    assertTrue(repo.authState.value is AuthState.PinLocked)
  }

  @Test
  fun cancelledFacebookLoginReturnsMessageWithoutAccount() = runBlocking {
    fb.failWith = SocialAuthException.Reason.CANCELLED
    val (ok, msg) = repo.loginWithFacebook(activity, false)
    assertFalse(ok)
    assertTrue(msg.isNotBlank())
    assertTrue(repo.authState.value is AuthState.Unauthenticated)
  }

  @Test
  fun attemptStoreEscalatesAndGuardsAgainstClockRollback() {
    var now = 1_000_000L
    val prefs = context.getSharedPreferences("attempts_unit", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
    val store = UnlockAttemptStore(prefs) { now }
    repeat(4) { assertTrue(store.recordFailure("u") is UnlockAttemptStore.Gate.Open) }
    assertEquals(UnlockAttemptStore.Gate.Locked(now + 30_000), store.recordFailure("u"))
    now += 30_001
    assertTrue(store.gate("u") is UnlockAttemptStore.Gate.Open)
    assertEquals(UnlockAttemptStore.Gate.Locked(now + 60_000), store.recordFailure("u"))
    // user rolls the clock back to dodge the lock
    val lockedAt = now
    now = 0L
    assertTrue(store.gate("u") is UnlockAttemptStore.Gate.Locked)
    now = lockedAt + 60_001
    assertTrue(store.gate("u") is UnlockAttemptStore.Gate.Open)
    store.reset("u")
    assertEquals(0, store.failures("u"))
    assertEquals(3_600_000L, UnlockAttemptStore.lockoutMillis(20))
  }
}
