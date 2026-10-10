package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Thứ tự xoá tài khoản: đám mây → Firebase Auth → local (xem AuthRepository.deleteCurrentAccount). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountDeletionOrderTest {

  /** Ghi lại thứ tự từng bước. Có thể ném lỗi ở bước đám mây, hoặc đòi đăng nhập lại ở lần gọi Auth đầu. */
  private class RecordingCleanup(
    private val cloudError: Exception? = null,
    private val authNeedsRecentLoginOnce: Boolean = false
  ) : RemoteAccountCleanup {
    val events = mutableListOf<String>()
    private var authCalls = 0

    override suspend fun deleteCloudData(uid: String, coupleCode: String) {
      events += "cloud:$uid"
      cloudError?.let { throw it }
    }

    override suspend fun deleteAuthIdentity() {
      events += "auth"
      authCalls++
      if (authNeedsRecentLoginOnce && authCalls == 1) throw RecentLoginRequiredException()
    }
  }

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var scope: CoroutineScope

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    db.close()
  }

  private fun repoWith(cleanup: RemoteAccountCleanup): AuthRepository {
    val onlineRepo = OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false)
    return AuthRepository(db.inLoveDao(), onlineRepo, context, scope, isTestMode = true, remoteCleanup = cleanup)
  }

  /** Đăng ký một tài khoản đang đăng nhập; trả về (email, uid). */
  private suspend fun AuthRepository.registerTestAccount(): Pair<String, String> {
    val email = "delete-test@inlove.app"
    val (success, message) = register(
      displayNameInput = "Hoang & Linh",
      emailInput = email,
      passwordInput = PASSWORD,
      confirmPasswordInput = PASSWORD,
      securityQuestionInput = "Kỷ niệm đầu tiên ở đâu?",
      securityAnswerInput = "Hồ Tây"
    )
    assertTrue("Đăng ký phải thành công: $message", success)
    return email to (authState.value as AuthState.Authenticated).account.uid
  }

  @Test
  fun deleteAccount_runsCloudThenAuth_thenRemovesLocalAccount() = runBlocking {
    val cleanup = RecordingCleanup()
    val repo = repoWith(cleanup)
    val (email, uid) = repo.registerTestAccount()

    val result = repo.deleteCurrentAccount()

    assertTrue("Xoá phải thành công: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertEquals(listOf("cloud:$uid", "auth"), cleanup.events)
    assertNull(db.inLoveDao().getUserAccountByEmail(email))
    assertEquals(AuthState.Unauthenticated, repo.authState.value)
  }

  @Test
  fun deleteAccount_cloudFailure_skipsAuth_andKeepsAccount() = runBlocking {
    val cleanup = RecordingCleanup(cloudError = IllegalStateException("PERMISSION_DENIED"))
    val repo = repoWith(cleanup)
    val (email, uid) = repo.registerTestAccount()

    val result = repo.deleteCurrentAccount()

    assertTrue(result.isFailure)
    assertEquals(listOf("cloud:$uid"), cleanup.events)
    assertNotNull(db.inLoveDao().getUserAccountByEmail(email))
    assertTrue(repo.authState.value is AuthState.Authenticated)
  }

  @Test
  fun deleteAccount_recentLoginRequired_keepsLocalAccount_thenRetrySucceeds() = runBlocking {
    val cleanup = RecordingCleanup(authNeedsRecentLoginOnce = true)
    val repo = repoWith(cleanup)
    val (email, uid) = repo.registerTestAccount()

    val first = repo.deleteCurrentAccount()

    assertTrue(first.isFailure)
    assertNotNull("Dữ liệu local phải còn để thử lại", db.inLoveDao().getUserAccountByEmail(email))
    assertTrue(repo.authState.value is AuthState.Authenticated)

    val second = repo.deleteCurrentAccount()

    assertTrue("Lần thử lại phải thành công: ${second.exceptionOrNull()?.message}", second.isSuccess)
    assertEquals(listOf("cloud:$uid", "auth", "cloud:$uid", "auth"), cleanup.events)
    assertNull(db.inLoveDao().getUserAccountByEmail(email))
  }

  private companion object {
    const val PASSWORD = "SecurePassword@123"
  }
}
