package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var onlineRepo: OnlineCoupleRepository
  private lateinit var authRepo: AuthRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    onlineRepo = OnlineCoupleRepository(db.inLoveDao(), context, scope)
    authRepo = AuthRepository(db.inLoveDao(), onlineRepo, context, scope, isTestMode = true)
  }

  @After
  fun tearDown() {
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

  @Test
  fun testLogin_wrongPassword_incrementsFailedAttempts() = runBlocking {
    val email = "wrong_pwd@inlove.app"
    val password = "CorrectPassword@123"
    authRepo.register("Tester", email, password, password)

    // Wrong password attempt
    val (loginSuccess, loginMsg) = authRepo.login(email, "WrongPassword@999", rememberMe = false)
    assertFalse("Đăng nhập sai mật khẩu phải thất bại", loginSuccess)
    assertTrue(loginMsg.contains("không chính xác") || loginMsg.contains("Mật khẩu"))

    val account = db.inLoveDao().getUserAccountByEmail(email)
    assertEquals(1, account?.failedAttempts)
  }

  @Test
  fun testPasswordReset_withOtp() = runBlocking {
    val email = "reset_test@inlove.app"
    val oldPassword = "OldPassword@123"
    val newPassword = "NewStrongPassword@2026"
    authRepo.register("Tester", email, oldPassword, oldPassword)

    // Request reset OTP
    val (reqSuccess, reqMsg) = authRepo.requestPasswordResetOtp(email)
    assertTrue("Yêu cầu gửi OTP phải thành công: $reqMsg", reqSuccess)

    // Retrieve active OTP securely via test helper
    val otp = authRepo.emailQueueService.getActiveOtpForTesting(email)
    assertNotNull("OTP phải được tạo trong queue bảo mật", otp)
    assertEquals("OTP phải có độ dài 6 ký tự số", 6, otp!!.length)

    // Reset password with OTP (no expectedOtp argument allowed from client)
    val (resetSuccess, resetMsg) = authRepo.resetPasswordWithOtp(
      emailInput = email,
      enteredOtp = otp,
      newPasswordInput = newPassword,
      confirmPasswordInput = newPassword
    )
    assertTrue("Reset mật khẩu thành công: $resetMsg", resetSuccess)

    // Verify login with new password works
    val (newLoginSuccess, _) = authRepo.login(email, newPassword, rememberMe = false)
    assertTrue("Phải đăng nhập được với mật khẩu mới", newLoginSuccess)

    // Verify old password no longer works
    authRepo.logout()
    val (oldLoginSuccess, _) = authRepo.login(email, oldPassword, rememberMe = false)
    assertFalse("Mật khẩu cũ không thể đăng nhập", oldLoginSuccess)
  }

  @Test
  fun testPasswordReset_withSecurityAnswer_unconfiguredAccount_fails() = runBlocking {
    val email = "no_sec_answer@inlove.app"
    val password = "SecurePassword@123"
    authRepo.register(
      displayNameInput = "No Sec Tester",
      emailInput = email,
      passwordInput = password,
      confirmPasswordInput = password,
      securityQuestionInput = "",
      securityAnswerInput = ""
    )

    // Attempt password reset with security answer on unconfigured account
    val (success, msg) = authRepo.resetPasswordWithSecurityAnswer(
      emailInput = email,
      securityAnswerInput = "anything",
      newPasswordInput = "NewPassword@2026",
      confirmPasswordInput = "NewPassword@2026"
    )
    assertFalse("Không được phép bypass đặt lại mật khẩu khi chưa thiết lập câu hỏi bảo mật", success)
    assertTrue(msg.contains("chưa thiết lập câu hỏi") || msg.contains("bảo mật"))
  }

  @Test
  fun testChangePassword_verifiesOldPassword() = runBlocking {
    val email = "change_pwd@inlove.app"
    val oldPassword = "OldSecurePassword@123"
    val newPassword = "BrandNewStrongPassword@2026"
    authRepo.register("Tester", email, oldPassword, oldPassword)
    authRepo.login(email, oldPassword, rememberMe = false)

    // Attempt with incorrect old password
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
    assertFalse("Mở khóa bằng mã PIN sai phải thất bại", wrongPinResult)
    assertTrue("Vẫn ở trạng thái PinLocked", authRepo.authState.value is AuthState.PinLocked)

    // Unlock with correct PIN
    val correctPinResult = authRepo.unlockWithPin("8888")
    assertTrue("Mở khóa bằng mã PIN đúng phải thành công", correctPinResult)
    assertTrue("Trạng thái phải trở lại Authenticated", authRepo.authState.value is AuthState.Authenticated)
  }
}
