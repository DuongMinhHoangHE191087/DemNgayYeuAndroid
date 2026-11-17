package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthErrorCode
import com.example.data.auth.AuthException
import com.example.data.auth.AuthUser
import com.example.data.auth.SessionHooks
import com.example.data.auth.fakes.FakeAuthGateway
import com.example.data.db.AppDatabase
import com.example.data.sync.ProfileSync
import com.example.ui.util.AuthSecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ghi lại vòng đời phiên để test kiểm thứ tự/uid; dùng chung cho các test cùng package. */
internal class RecordingSessionHooks : SessionHooks {
  val started = mutableListOf<String>()
  var endedCount = 0
  override fun onSessionStarted(uid: String) { started += uid }
  override fun onSessionEnded() { endedCount++ }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var onlineRepo: OnlineCoupleRepository
  private lateinit var authRepo: AuthRepository
  private lateinit var scope: CoroutineScope
  private val gateway = FakeAuthGateway()
  private val hooks = RecordingSessionHooks()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    onlineRepo = OnlineCoupleRepository(db.inLoveDao(), context, scope, useFirestore = false)
    authRepo = AuthRepository(db.inLoveDao(), onlineRepo, context, scope, auth = gateway, hooks = hooks)
  }

  @After
  fun tearDown() {
    runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    db.close()
  }

  @Test
  fun testRegister_success() = runBlocking {
    val email = "lover@inlove.app"
    val password = "SecurePassword@123"
    val displayName = "Hoang & Linh"

    val (success, message) = authRepo.register(
      displayNameInput = displayName,
      emailInput = email,
      passwordInput = password,
      confirmPasswordInput = password,
      securityQuestionInput = "Kỷ niệm đầu tiên ở đâu?",
      securityAnswerInput = "Hồ Tây"
    )

    assertTrue("Đăng ký phải thành công: $message", success)

    // Verify user is in database with hashed password
    val savedAccount = db.inLoveDao().getUserAccountByEmail(email)
    assertNotNull("Tài khoản phải được lưu trong Room", savedAccount)
    assertEquals(email, savedAccount?.email)
    assertEquals(displayName, savedAccount?.displayName)
    assertNotEquals("Mật khẩu không được lưu dưới dạng plain text", password, savedAccount?.passwordHash)
    assertTrue("Salt phải được tạo", savedAccount?.salt?.isNotEmpty() == true)

    // Verify security audit log exists
    val logs = db.inLoveDao().getSecurityLogsForAccount(email).first()
    assertTrue("Phải ghi audit log khi đăng ký", logs.any { it.action == "REGISTER" })
  }

  @Test
  fun testRegister_newAccountPersistsBlankAvatar() = runBlocking {
    val email = "blank_avatar@inlove.app"
    val password = "SecurePassword@123"

    val (success, message) = authRepo.register(
      displayNameInput = "Hoang & Linh",
      emailInput = email,
      passwordInput = password,
      confirmPasswordInput = password
    )

    assertTrue("Đăng ký phải thành công: $message", success)
    val account = checkNotNull(db.inLoveDao().getUserAccountByEmail(email))
    assertEquals("Tài khoản mới không được lưu ảnh stock", "", account.avatarUrl)
    val online = checkNotNull(db.inLoveDao().getOnlineUserByUidSync(account.uid))
    assertEquals("Online user mới cũng không được có ảnh stock", "", online.avatarUrl)
  }

  @Test
  fun testRegister_duplicateEmail_fails() = runBlocking {
    val email = "duplicate@inlove.app"
    val password = "SecurePassword@123"

    // First registration
    val (firstSuccess, _) = authRepo.register(
      displayNameInput = "User 1",
      emailInput = email,
      passwordInput = password,
      confirmPasswordInput = password
    )
    assertTrue(firstSuccess)

    // Second registration with same email
    val (secondSuccess, secondMessage) = authRepo.register(
      displayNameInput = "User 2",
      emailInput = email,
      passwordInput = password,
      confirmPasswordInput = password
    )
    assertFalse("Không được phép đăng ký trùng email", secondSuccess)
    assertTrue(secondMessage.contains("đã được sử dụng") || secondMessage.contains("Email"))
  }

  @Test
  fun testLogin_success_and_logout() = runBlocking {
    val email = "login_test@inlove.app"
    val password = "MySecretPassword@2026"
    authRepo.register("Tester", email, password, password)

    // Login with correct credentials
    val (loginSuccess, loginMsg) = authRepo.login(email, password, rememberMe = true)
    assertTrue("Đăng nhập phải thành công: $loginMsg", loginSuccess)

    // Check AuthState
    val currentAuth = authRepo.authState.value
    assertTrue("AuthState phải là Authenticated", currentAuth is AuthState.Authenticated)
    val account = (currentAuth as AuthState.Authenticated).account
    assertEquals(email, account.email)
    assertTrue("Session token phải được tạo", account.sessionToken.isNotEmpty())

    // Check logout
    authRepo.logout()
    assertTrue("AuthState sau khi logout phải là Unauthenticated", authRepo.authState.value is AuthState.Unauthenticated)
  }

  // Firebase là nguồn xác thực duy nhất: lỗi đăng nhập đến từ AuthGateway dưới dạng mã ổn định.
  @Test
  fun login_wrongPassword_messageContainsKhongChinhXac() = runBlocking {
    val email = "wrong_pwd@inlove.app"
    gateway.queued += Result.failure(AuthException(AuthErrorCode.INVALID_CREDENTIALS))

    val (ok, msg) = authRepo.login(email, "WrongPassword@999", rememberMe = false)

    assertFalse("Đăng nhập sai mật khẩu phải thất bại", ok)
    assertTrue("Thông báo phải chứa 'không chính xác': $msg", msg.contains("không chính xác"))
    assertTrue(authRepo.authState.value is AuthState.Unauthenticated)
    assertEquals(emptyList<String>(), hooks.started)
    val logs = db.inLoveDao().getSecurityLogsForAccount(email).first()
    assertTrue("Phải ghi audit log đăng nhập thất bại", logs.any { it.action == "LOGIN_FAILED" })
  }

  @Test
  fun login_unknownEmail_messageIsTaiKhoanKhongTonTai() = runBlocking {
    gateway.queued += Result.failure(AuthException(AuthErrorCode.USER_NOT_FOUND))

    val (ok, msg) = authRepo.login("nobody@inlove.app", "Whatever@123", rememberMe = false)

    assertFalse(ok)
    assertEquals("Tài khoản không tồn tại. Bạn có thể bấm Đăng Ký ngay bên cạnh!", msg)
  }

  @Test
  fun register_duplicateEmail_returnsEmailInUseMessage() = runBlocking {
    val email = "dup_remote@inlove.app"
    gateway.queued += Result.failure(AuthException(AuthErrorCode.EMAIL_IN_USE))

    val (ok, msg) = authRepo.register("User", email, "SecurePassword@123", "SecurePassword@123")

    assertFalse(ok)
    assertTrue("Phải báo email đã được dùng: $msg", msg.contains("đã được sử dụng"))
    assertNull("Không được tạo tài khoản local khi Firebase từ chối", db.inLoveDao().getUserAccountByEmail(email))
  }

  @Test
  fun login_success_callsOnSessionStartedWithUid() = runBlocking {
    val email = "session_start@inlove.app"
    gateway.queued += Result.success(AuthUser("uid-123", email, null, true, listOf("password")))

    val (ok, msg) = authRepo.login(email, "SecurePassword@123", rememberMe = false)

    assertTrue("Đăng nhập phải thành công: $msg", ok)
    assertEquals(listOf("uid-123"), hooks.started)
  }

  @Test
  fun logout_callsOnSessionEnded() = runBlocking {
    val email = "session_end@inlove.app"
    authRepo.login(email, "SecurePassword@123", rememberMe = false)
    assertEquals(0, hooks.endedCount)

    authRepo.logout()

    assertEquals(1, hooks.endedCount)
    assertTrue(gateway.calls.contains("signOut"))
    assertTrue(authRepo.authState.value is AuthState.Unauthenticated)
  }

  @Test
  fun requestPasswordResetOtp_callsSendPasswordResetEmail() = runBlocking {
    val (ok, msg) = authRepo.requestPasswordResetOtp("  Reset@Inlove.App ")

    assertTrue("Phải báo đã gửi: $msg", ok)
    assertTrue(gateway.calls.contains("sendPasswordResetEmail"))
  }

  @Test
  fun testPasswordRecovery_hasOnlyTheFirebaseEmailRoute() {
    // Local OTP / security-answer resets changed only the local hash, so the real Firebase password stayed old. Keep both gone.
    val obsolete = setOf("resetPasswordWithOtp", "resetPasswordWithSecurityAnswer")
    val leftover = AuthRepository::class.java.declaredMethods.map { it.name }.filter { it in obsolete }
    assertTrue("Đường khôi phục cũ vẫn còn trong AuthRepository: $leftover", leftover.isEmpty())
  }

  @Test
  fun testChangePassword_verifiesOldPassword() = runBlocking {
    val email = "change_pwd@inlove.app"
    val oldPassword = "OldSecurePassword@123"
    val newPassword = "BrandNewStrongPassword@2026"
    authRepo.register("Tester", email, oldPassword, oldPassword)
    authRepo.login(email, oldPassword, rememberMe = false)

    // Attempt with incorrect old password (Firebase is the source of truth and rejects it)
    gateway.queued += Result.failure(AuthException(AuthErrorCode.INVALID_CREDENTIALS))
    val (failChange, _) = authRepo.changePassword("IncorrectOldPwd@123", newPassword, newPassword)
    assertFalse("Không thể đổi mật khẩu nếu nhập sai mật khẩu cũ", failChange)

    // Attempt with correct old password
    val (successChange, changeMsg) = authRepo.changePassword(oldPassword, newPassword, newPassword)
    assertTrue("Đổi mật khẩu thành công: $changeMsg", successChange)
  }

  @Test
  fun testAppPin_and_lockUnlockFlow() = runBlocking {
    val email = "pin_test@inlove.app"
    val password = "SecurePassword@123"
    authRepo.register("Tester", email, password, password)
    authRepo.login(email, password, rememberMe = false)

    // Set 4-digit PIN
    val (pinSetSuccess, _) = authRepo.setAppPin("8888")
    assertTrue("Đặt mã PIN thành công", pinSetSuccess)

    // Verify PIN is hashed in database, not stored in plaintext
    val savedAccount = db.inLoveDao().getUserAccountByEmail(email)
    assertNotEquals("Mã PIN tuyệt đối không được lưu plain text trong Room", "8888", savedAccount?.appPin)
    assertTrue("Mã PIN phải được lưu dưới dạng hash an toàn", savedAccount?.appPin?.isNotEmpty() == true)

    // Lock app
    authRepo.lockApp()
    assertTrue("Trạng thái phải chuyển thành PinLocked", authRepo.authState.value is AuthState.PinLocked)

    // Unlock with wrong PIN
    val wrongPinResult = authRepo.unlockWithPin("1234")
    assertTrue("Mở khóa bằng mã PIN sai phải thất bại", wrongPinResult is UnlockResult.Wrong)
    assertTrue("Vẫn ở trạng thái PinLocked", authRepo.authState.value is AuthState.PinLocked)

    // Unlock with correct PIN
    val correctPinResult = authRepo.unlockWithPin("8888")
    assertTrue("Mở khóa bằng mã PIN đúng phải thành công", correctPinResult is UnlockResult.Success)
    assertTrue("Trạng thái phải trở lại Authenticated", authRepo.authState.value is AuthState.Authenticated)
  }

  @Test
  fun login_keepsEditedOnlineAvatar_whenAccountPhotoIsSet() = runBlocking {
    val email = "avatar_keep@inlove.app"
    val password = "SecurePassword@123"
    authRepo.register("Tester", email, password, password)
    authRepo.login(email, password, rememberMe = false)
    val uid = (authRepo.authState.value as AuthState.Authenticated).account.uid

    // The user picked a local photo, then the account gets a provider photo
    val online = checkNotNull(db.inLoveDao().getOnlineUserByUidSync(uid))
    db.inLoveDao().updateOnlineUser(online.copy(avatarUrl = "file:/custom.jpg"))
    val account = checkNotNull(db.inLoveDao().getUserAccountByEmail(email))
    db.inLoveDao().updateUserAccount(account.copy(avatarUrl = "https://provider.example/photo.jpg"))

    authRepo.logout()
    authRepo.login(email, password, rememberMe = false)

    assertEquals(
      "Ảnh đã chọn không được bị ghi đè bằng ảnh tài khoản khi đăng nhập lại",
      "file:/custom.jpg",
      db.inLoveDao().getOnlineUserByUidSync(uid)?.avatarUrl
    )
  }

  @Test
  fun login_keepsEditedProfileName_butStillSyncsOtherAccountFields() = runBlocking {
    val email = "name_keep@inlove.app"
    val password = "SecurePassword@123"
    authRepo.register("Tester", email, password, password)
    authRepo.login(email, password, rememberMe = false)
    val uid = (authRepo.authState.value as AuthState.Authenticated).account.uid

    val online = checkNotNull(db.inLoveDao().getOnlineUserByUidSync(uid))
    db.inLoveDao().updateOnlineUser(
      online.copy(displayName = "Biệt danh", isProfileSetup = true, coupleCode = "STALE", isCurrentUser = false)
    )

    authRepo.logout()
    authRepo.login(email, password, rememberMe = false)

    val account = checkNotNull(db.inLoveDao().getUserAccountByEmail(email))
    val after = checkNotNull(db.inLoveDao().getOnlineUserByUidSync(uid))
    assertEquals("Tên đã sửa trong hồ sơ không được bị ghi đè bằng tên tài khoản", "Biệt danh", after.displayName)
    assertEquals(account.coupleCode, after.coupleCode)
    assertEquals(email, after.email)
    assertTrue(after.isCurrentUser)
  }

  @Test
  fun login_takesAccountName_whenOnlineProfileIsNotSetUp() = runBlocking {
    val email = "name_seed@inlove.app"
    val password = "SecurePassword@123"
    authRepo.register("Tester", email, password, password)
    authRepo.login(email, password, rememberMe = false)
    val uid = (authRepo.authState.value as AuthState.Authenticated).account.uid

    for (placeholder in listOf("", "Vô danh")) {
      val online = checkNotNull(db.inLoveDao().getOnlineUserByUidSync(uid))
      db.inLoveDao().updateOnlineUser(online.copy(displayName = placeholder, isProfileSetup = false))

      authRepo.logout()
      authRepo.login(email, password, rememberMe = false)

      val account = checkNotNull(db.inLoveDao().getUserAccountByEmail(email))
      assertEquals(account.displayName, db.inLoveDao().getOnlineUserByUidSync(uid)?.displayName)
    }
  }

  @Test
  fun deleteCurrentAccount_removesOnlineUserRow() = runBlocking {
    val email = "delete_online@inlove.app"
    val password = "SecurePassword@123"
    val repo = AuthRepository(
      db.inLoveDao(), onlineRepo, context, scope, auth = gateway, hooks = hooks,
      remoteCleanup = NoopRemoteAccountCleanup
    )
    repo.register("Tester", email, password, password)
    repo.login(email, password, rememberMe = false)
    val uid = (repo.authState.value as AuthState.Authenticated).account.uid
    assertNotNull(db.inLoveDao().getOnlineUserByUidSync(uid))

    val result = repo.deleteCurrentAccount()

    assertTrue("Xoá phải thành công: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertNull(db.inLoveDao().getOnlineUserByUidSync(uid))
  }

  @Test
  fun deleteCurrentAccount_removesOnlyDeletedUidsProfileSyncKey() = runBlocking {
    val email = "delete_sync_key@inlove.app"
    val password = "SecurePassword@123"
    val repo = AuthRepository(
      db.inLoveDao(), onlineRepo, context, scope, auth = gateway, hooks = hooks,
      remoteCleanup = NoopRemoteAccountCleanup
    )
    repo.register("Tester", email, password, password)
    val uid = (repo.authState.value as AuthState.Authenticated).account.uid
    val syncPrefs = context.getSharedPreferences(ProfileSync.PREFS, Context.MODE_PRIVATE)
    syncPrefs.edit()
      .putString(ProfileSync.syncedKey(uid), "key-of-deleted-uid")
      .putString(ProfileSync.syncedKey("other"), "key-of-other-uid")
      .commit()

    val result = repo.deleteCurrentAccount()

    assertTrue("Xoá phải thành công: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertFalse("Key đồng bộ của uid đã xoá phải mất", syncPrefs.contains(ProfileSync.syncedKey(uid)))
    assertEquals("key-of-other-uid", syncPrefs.getString(ProfileSync.syncedKey("other"), null))
  }

  @Test
  fun restoreSession_startsHookOnlyAfterOnlineRowMatchesAccount() = runBlocking {
    val email = "relaunch_hook@inlove.app"
    val password = "SecurePassword@123"
    authRepo.register("Tester", email, password, password)
    val accountUid = (authRepo.authState.value as AuthState.Authenticated).account.uid
    // Mô phỏng khởi động lại: dòng online_users biến mất, restoreSession phải tạo lại nó TRƯỚC khi gọi hook.
    db.inLoveDao().deleteOnlineUser(accountUid)
    assertNull(db.inLoveDao().getOnlineUserByUidSync(accountUid))
    val rowSeenByHook = mutableListOf<Boolean>()
    val relaunchHooks = object : SessionHooks {
      override fun onSessionStarted(uid: String) {
        rowSeenByHook += (runBlocking { db.inLoveDao().getOnlineUserByUidSync(uid) } != null)
      }
      override fun onSessionEnded() {}
    }

    // Cùng gateway nên auth.currentUser?.uid khớp uid của tài khoản đã lưu phiên (rememberMe).
    val repo2 = AuthRepository(db.inLoveDao(), onlineRepo, context, scope, auth = gateway, hooks = relaunchHooks)
    repo2.restoreJob.join()

    assertTrue(repo2.authState.value is AuthState.Authenticated)
    assertEquals("Hook phải chạy đúng một lần", listOf(true), rowSeenByHook)
  }

  @Test
  fun changePassword_success_reauthenticatesBeforeUpdatingFirebasePassword() = runBlocking {
    val email = "change_order@inlove.app"
    val oldPassword = "OldSecurePassword@123"
    val newPassword = "BrandNewStrongPassword@2026"
    authRepo.register("Tester", email, oldPassword, oldPassword)
    authRepo.login(email, oldPassword, rememberMe = false)
    gateway.calls.clear()

    val (ok, msg) = authRepo.changePassword(oldPassword, newPassword, newPassword)

    assertTrue("Đổi mật khẩu phải thành công: $msg", ok)
    assertEquals(listOf("reauthenticateWithPassword", "updatePassword"), gateway.calls)
  }

  @Test
  fun changePassword_firebaseReauthFails_keepsLocalHashAndSkipsUpdate() = runBlocking {
    val email = "change_reauth_fail@inlove.app"
    val oldPassword = "OldSecurePassword@123"
    val newPassword = "BrandNewStrongPassword@2026"
    authRepo.register("Tester", email, oldPassword, oldPassword)
    authRepo.login(email, oldPassword, rememberMe = false)
    val hashBefore = checkNotNull(db.inLoveDao().getUserAccountByEmail(email)).passwordHash
    gateway.calls.clear()
    gateway.queued += Result.failure(AuthException(AuthErrorCode.INVALID_CREDENTIALS))

    val (ok, _) = authRepo.changePassword(oldPassword, newPassword, newPassword)

    assertFalse("Firebase từ chối thì không được báo đã đổi mật khẩu", ok)
    assertEquals(listOf("reauthenticateWithPassword"), gateway.calls)
    assertEquals(
      "Hash cục bộ không được đổi khi Firebase chưa đổi",
      hashBefore,
      checkNotNull(db.inLoveDao().getUserAccountByEmail(email)).passwordHash
    )
  }

  @Test
  fun changePassword_staleLocalHash_firebaseAcceptsOld_succeedsAndRefreshesHash() = runBlocking {
    val email = "change_stale@inlove.app"
    val oldPassword = "OldSecurePassword@123"
    val newPassword = "BrandNewStrongPassword@2026"
    authRepo.register("Tester", email, oldPassword, oldPassword)
    authRepo.login(email, oldPassword, rememberMe = true)
    // Mật khẩu đã đổi ở máy khác: hash cục bộ cũ không còn khớp mật khẩu Firebase hiện tại.
    val account = checkNotNull(db.inLoveDao().getUserAccountByEmail(email))
    db.inLoveDao().updateUserAccount(
      account.copy(passwordHash = AuthSecurityManager.hashPassword("SomeOtherPassword@999", account.salt))
    )
    // Repo mới khôi phục phiên từ Room nên giữ đúng tài khoản có hash cũ trong authState.
    val gateway2 = FakeAuthGateway().apply {
      currentUser = AuthUser("other-uid", email, null, true, listOf("password"))
    }
    val repo2 = AuthRepository(db.inLoveDao(), onlineRepo, context, scope, auth = gateway2, hooks = hooks)
    repo2.restoreJob.join()
    assertTrue(repo2.authState.value is AuthState.Authenticated)

    val (ok, msg) = repo2.changePassword(oldPassword, newPassword, newPassword)

    assertTrue("Firebase chấp nhận mật khẩu cũ thì phải đổi được: $msg", ok)
    assertEquals(listOf("reauthenticateWithPassword", "updatePassword"), gateway2.calls)
    assertEquals(
      AuthSecurityManager.hashPassword(newPassword, account.salt),
      checkNotNull(db.inLoveDao().getUserAccountByEmail(email)).passwordHash
    )
  }

  @Test
  fun changePassword_weakNewPassword_doesNotCallFirebase() = runBlocking {
    val email = "change_weak@inlove.app"
    val oldPassword = "OldSecurePassword@123"
    authRepo.register("Tester", email, oldPassword, oldPassword)
    authRepo.login(email, oldPassword, rememberMe = false)
    gateway.calls.clear()

    val (ok, _) = authRepo.changePassword(oldPassword, "123", "123")

    assertFalse(ok)
    assertTrue("Mật khẩu yếu không được chạm Firebase: ${gateway.calls}", gateway.calls.isEmpty())
  }

  @Test
  fun changePassword_wrongOldPassword_invalidCredentials_saysWrongPassword() = runBlocking {
    val email = "change_wrong_old@inlove.app"
    val oldPassword = "OldSecurePassword@123"
    val newPassword = "BrandNewStrongPassword@2026"
    authRepo.register("Tester", email, oldPassword, oldPassword)
    authRepo.login(email, oldPassword, rememberMe = false)
    gateway.queued += Result.failure(AuthException(AuthErrorCode.INVALID_CREDENTIALS))

    val (ok, msg) = authRepo.changePassword("WrongOldPassword@999", newPassword, newPassword)

    assertFalse(ok)
    assertEquals("Mật khẩu hiện tại không chính xác!", msg)
  }

  @Test
  fun changePassword_wrongOldPassword_networkError_isNotReportedAsWrongPassword() = runBlocking {
    val email = "change_wrong_old_net@inlove.app"
    val oldPassword = "OldSecurePassword@123"
    val newPassword = "BrandNewStrongPassword@2026"
    authRepo.register("Tester", email, oldPassword, oldPassword)
    authRepo.login(email, oldPassword, rememberMe = false)
    gateway.queued += Result.failure(AuthException(AuthErrorCode.NETWORK))

    val (ok, msg) = authRepo.changePassword("WrongOldPassword@999", newPassword, newPassword)

    assertFalse(ok)
    assertEquals("Không có kết nối mạng. Vui lòng thử lại.", msg)
  }

  @Test
  fun changePassword_wrongOldPassword_tooManyRequests_isNotReportedAsWrongPassword() = runBlocking {
    val email = "change_wrong_old_429@inlove.app"
    val oldPassword = "OldSecurePassword@123"
    val newPassword = "BrandNewStrongPassword@2026"
    authRepo.register("Tester", email, oldPassword, oldPassword)
    authRepo.login(email, oldPassword, rememberMe = false)
    gateway.queued += Result.failure(AuthException(AuthErrorCode.TOO_MANY_REQUESTS))

    val (ok, msg) = authRepo.changePassword("WrongOldPassword@999", newPassword, newPassword)

    assertFalse(ok)
    assertEquals("Đổi mật khẩu trên Firebase thất bại. Vui lòng thử lại.", msg)
  }

  @Test
  fun unlockWithPin_withoutMatchingFirebaseSession_doesNotStartSync() = runBlocking {
    val email = "pin_nosession@inlove.app"
    val password = "SecurePassword@123"
    authRepo.register("Tester", email, password, password)
    authRepo.login(email, password, rememberMe = false)
    authRepo.setAppPin("8888")
    val uid = (authRepo.authState.value as AuthState.Authenticated).account.uid
    val startsAfterLogin = hooks.started.size

    // Còn phiên Firebase khớp uid: mở khoá PIN phải bật lại đồng bộ.
    authRepo.lockApp()
    assertTrue(authRepo.unlockWithPin("8888") is UnlockResult.Success)
    assertEquals(startsAfterLogin + 1, hooks.started.size)
    assertEquals(uid, hooks.started.last())

    // Phiên Firebase hết hạn/bị thu hồi: tài khoản cục bộ vẫn dùng được nhưng không được bật đồng bộ đám mây.
    authRepo.lockApp()
    gateway.currentUser = null
    assertTrue(authRepo.unlockWithPin("8888") is UnlockResult.Success)
    assertEquals(startsAfterLogin + 1, hooks.started.size)
  }
}
