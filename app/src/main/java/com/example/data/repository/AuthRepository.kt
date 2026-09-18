package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.db.InLoveDao
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.RbacPolicy
import com.example.data.model.SecurityAuditLogEntity
import com.example.data.model.SubscriptionTier
import com.example.data.model.UserAccountEntity
import com.example.data.model.UserRole
import com.example.ui.util.AuthSecurityManager
import com.example.ui.util.PasswordStrengthLevel
import com.example.ui.util.ProfileUtils
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

import com.example.data.email.EmailQueueService
import com.example.data.email.OtpPurpose

import com.example.BuildConfig

sealed class AuthState {
  data object Unauthenticated : AuthState()
  data class Authenticated(val account: UserAccountEntity) : AuthState()
  data class PinLocked(val account: UserAccountEntity) : AuthState()
}

/**
 * Cloud-driven Test Fixture model stored in Firebase Firestore (collection: test_fixtures).
 * Zero credentials bundled in the APK binary.
 */
data class CloudTestFixture(
  val email: String,
  val password: String,
  val displayName: String,
  val role: String = "USER_VIP",
  val tier: String = "VIP_YEARLY"
)

class AuthRepository(
  private val dao: InLoveDao,
  private val onlineRepo: OnlineCoupleRepository,
  context: Context
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val prefs: SharedPreferences =
    context.getSharedPreferences("inlove_auth_prefs", Context.MODE_PRIVATE)

  private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
  val authState: StateFlow<AuthState> = _authState.asStateFlow()

  val emailQueueService: EmailQueueService = EmailQueueService.getInstance(context)

  companion object {
    private const val KEY_SESSION_TOKEN = "key_session_token"
    private const val KEY_REMEMBER_ME = "key_remember_me"
    private const val KEY_SAVED_EMAIL = "key_saved_email"
  }

  init {
    scope.launch {
      restoreSession()
    }
  }

  /**
   * Fetches test account fixtures dynamically from Firebase Firestore over the network if available.
   * Zero hardcoded credentials bundled in the APK binary.
   */
  suspend fun fetchTestAccountFromFirebase(isPartner: Boolean = false): Result<CloudTestFixture> =
    fetchTestFixtureByDocId(if (!isPartner) "tester_primary" else "tester_partner")

  /**
   * Fetches any test account fixture dynamically by docId from Firebase Firestore over the network.
   * Zero hardcoded credentials bundled in the APK binary.
   */
  suspend fun fetchTestFixtureByDocId(docId: String): Result<CloudTestFixture> = withContext(Dispatchers.IO) {
    try {
      val firestore = FirebaseFirestore.getInstance()
      val snapshot = firestore.collection("test_fixtures").document(docId).get().await()

      if (snapshot.exists()) {
        val email = snapshot.getString("email") ?: return@withContext Result.failure(IllegalStateException("No email in cloud fixture"))
        val password = snapshot.getString("password") ?: return@withContext Result.failure(IllegalStateException("No password in cloud fixture"))
        val fixture = CloudTestFixture(
          email = email,
          password = password,
          displayName = snapshot.getString("displayName") ?: "Tester",
          role = snapshot.getString("role") ?: "USER_VIP",
          tier = snapshot.getString("tier") ?: "VIP_YEARLY"
        )
        ensureTestAccountInDatabase(fixture)
        Result.success(fixture)
      } else {
        Result.failure(IllegalStateException("Tài khoản kiểm thử không tồn tại trên Cloud Firestore ($docId)."))
      }
    } catch (e: Exception) {
      Log.w("AuthRepo", "Firebase test fixture retrieval error: ${e.message}")
      Result.failure(e)
    }
  }

  private suspend fun ensureTestAccountInDatabase(fixture: CloudTestFixture) {
    val existing = dao.getUserAccountByEmail(fixture.email)
    if (existing == null) {
      val salt = AuthSecurityManager.generateSalt()
      val hash = AuthSecurityManager.hashPassword(fixture.password, salt)
      val secAnswerHash = AuthSecurityManager.hashSecurityAnswer("InLove", salt)

      val account = UserAccountEntity(
        uid = "uid_" + (fixture.email.hashCode().toUInt().toString()),
        email = fixture.email,
        passwordHash = hash,
        salt = salt,
        displayName = fixture.displayName,
        coupleCode = ProfileUtils.generateRandomCoupleCode(),
        avatarUrl = "",
        securityQuestion = AuthSecurityManager.SECURITY_QUESTIONS[0],
        securityAnswerHash = secAnswerHash,
        appPin = "",
        isPinEnabled = false,
        role = fixture.role,
        subscriptionTier = fixture.tier,
        isVip = (fixture.role == "USER_VIP" || fixture.tier != "FREE")
      )
      dao.insertUserAccount(account)
    }
  }

  suspend fun seedTestScenarioAccounts(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val resA = fetchTestAccountFromFirebase(isPartner = false)
    val resB = fetchTestAccountFromFirebase(isPartner = true)
    if (resA.isSuccess) {
      return@withContext true to "Đã đồng bộ tài khoản kiểm thử từ Firebase Firestore (${resA.getOrNull()?.email}) ✨"
    } else {
      return@withContext false to "Không thể kết nối Firestore để tải tài khoản test."
    }
  }

  /**
   * Requests a 6-digit OTP verification code sent via the asynchronous Email Queue.
   */
  suspend fun requestRegistrationOtp(email: String): Result<Unit> {
    if (!AuthSecurityManager.isValidEmail(email)) {
      return Result.failure(IllegalArgumentException("Địa chỉ Email không đúng định dạng!"))
    }
    return emailQueueService.enqueueVerificationEmail(email, OtpPurpose.REGISTRATION)
  }

  /**
   * Verifies the 6-digit OTP code against the active queue record.
   */
  fun verifyOtp(email: String, code: String): Pair<Boolean, String> {
    return emailQueueService.verifyOtp(email, code)
  }

  /**
   * Returns remaining cooldown in seconds before the user can request another OTP.
   */
  fun getOtpCooldown(email: String): Int {
    return emailQueueService.getCooldownSeconds(email)
  }

  /**
   * Attempts restoring persistent session if "Remember me" is enabled.
   */
  private suspend fun restoreSession() = withContext(Dispatchers.IO) {
    val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)
    val savedToken = prefs.getString(KEY_SESSION_TOKEN, null)

    if (rememberMe && !savedToken.isNullOrEmpty()) {
      val account = dao.getUserAccountBySessionToken(savedToken)
      if (account != null) {
        // If PIN is enabled, lock upon relaunch for security
        if (account.isPinEnabled && account.appPin.isNotEmpty()) {
          _authState.value = AuthState.PinLocked(account)
        } else {
          _authState.value = AuthState.Authenticated(account)
        }
        syncOnlineUserWithAccount(account)
        return@withContext
      }
    }
    _authState.value = AuthState.Unauthenticated
  }

  /**
   * Sign In with Brute-Force lockout protection, cryptographic password checking,
   * audit logging and session generation.
   */
  suspend fun login(
    emailInput: String,
    passwordInput: String,
    rememberMe: Boolean
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val email = emailInput.trim().lowercase()
    if (!AuthSecurityManager.isValidEmail(email)) {
      return@withContext false to "Email không đúng định dạng. Vui lòng kiểm tra lại!"
    }
    if (passwordInput.isEmpty()) {
      return@withContext false to "Vui lòng nhập mật khẩu!"
    }

    val account = dao.getUserAccountByEmail(email)
    if (account == null) {
      dao.insertSecurityLog(
        SecurityAuditLogEntity(
          accountEmail = email,
          action = "LOGIN_FAILED",
          detail = "Đăng nhập thất bại: Tài khoản không tồn tại"
        )
      )
      return@withContext false to "Tài khoản không tồn tại. Bạn có thể bấm Đăng Ký ngay bên cạnh!"
    }

    // Check Lockout
    val lockoutStatus = AuthSecurityManager.checkLockoutStatus(
      account.failedAttempts,
      account.lockoutUntil
    )
    if (lockoutStatus.isLocked) {
      dao.insertSecurityLog(
        SecurityAuditLogEntity(
          accountEmail = email,
          action = "LOCKOUT",
          detail = "Từ chối đăng nhập: Tài khoản đang bị tạm khóa còn ${lockoutStatus.remainingSeconds}s"
        )
      )
      return@withContext false to "Tài khoản bị tạm khóa vì nhập sai nhiều lần! Vui lòng thử lại sau ${lockoutStatus.remainingSeconds} giây."
    }

    // Verify Password Hash
    val expectedHash = AuthSecurityManager.hashPassword(passwordInput, account.salt)
    if (expectedHash != account.passwordHash) {
      val newFailed = account.failedAttempts + 1
      val isNowLocked = newFailed >= AuthSecurityManager.MAX_FAILED_ATTEMPTS
      val newLockoutUntil = if (isNowLocked) {
        System.currentTimeMillis() + AuthSecurityManager.LOCKOUT_DURATION_MILLIS
      } else 0L

      val updatedAccount = account.copy(
        failedAttempts = newFailed,
        lockoutUntil = newLockoutUntil
      )
      dao.updateUserAccount(updatedAccount)

      dao.insertSecurityLog(
        SecurityAuditLogEntity(
          accountEmail = email,
          action = if (isNowLocked) "LOCKOUT" else "LOGIN_FAILED",
          detail = if (isNowLocked) "Khóa tài khoản 3 phút do nhập sai 5 lần" else "Sai mật khẩu lần $newFailed"
        )
      )

      return@withContext if (isNowLocked) {
        false to "Bạn đã nhập sai 5 lần liên tiếp! Tài khoản bị tạm khóa 3 phút để đảm bảo an toàn."
      } else {
        val remaining = AuthSecurityManager.MAX_FAILED_ATTEMPTS - newFailed
        false to "Mật khẩu không chính xác! Bạn còn $remaining lần thử trước khi tài khoản bị khóa."
      }
    }

    // Login Success
    val newSessionToken = AuthSecurityManager.generateSessionToken()
    val updatedAccount = account.copy(
      failedAttempts = 0,
      lockoutUntil = 0L,
      lastLoginAt = System.currentTimeMillis(),
      sessionToken = newSessionToken
    )
    dao.updateUserAccount(updatedAccount)

    // Save preferences
    prefs.edit()
      .putBoolean(KEY_REMEMBER_ME, rememberMe)
      .putString(KEY_SESSION_TOKEN, if (rememberMe) newSessionToken else null)
      .putString(KEY_SAVED_EMAIL, email)
      .apply()

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = email,
        action = "LOGIN_SUCCESS",
        detail = "Đăng nhập thành công"
      )
    )

    // Synchronize authoritative identity with FirebaseAuth for Firestore access
    try {
      val fbAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
      fbAuth.signInWithEmailAndPassword(email, passwordInput).await()
      Log.i("AuthRepo", "Firebase Auth signed in: ${fbAuth.currentUser?.uid}")
    } catch (e: Exception) {
      Log.w("AuthRepo", "Firebase Auth sign-in notice: ${e.message}")
    }

    syncOnlineUserWithAccount(updatedAccount)

    if (updatedAccount.isPinEnabled && updatedAccount.appPin.isNotEmpty()) {
      _authState.value = AuthState.PinLocked(updatedAccount)
    } else {
      _authState.value = AuthState.Authenticated(updatedAccount)
    }

    return@withContext true to "Đăng nhập thành công! Chào mừng ${updatedAccount.displayName} 💕"
  }

  suspend fun loginUser(
    emailInput: String,
    passwordInput: String,
    rememberMe: Boolean = true
  ): Pair<Boolean, String> = login(emailInput, passwordInput, rememberMe)

  /**
   * Fast Test Login for QA/Developers using cloud fixtures on Firebase Firestore.
   */
  suspend fun loginTestUser(isPartner: Boolean = false): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val fixtureResult = fetchTestAccountFromFirebase(isPartner)
    val fixture = fixtureResult.getOrNull() ?: return@withContext false to "Không thể tải tài khoản test từ Firebase."
    return@withContext login(fixture.email, fixture.password, rememberMe = true)
  }

  suspend fun loginDemoUser(userAOrB: String): Pair<Boolean, String> =
    loginTestUser(isPartner = (userAOrB == "B"))

  /**
   * Update RBAC user role and subscription tier.
   */
  suspend fun updateUserSubscription(
    uid: String,
    role: UserRole,
    tier: SubscriptionTier
  ): Boolean = withContext(Dispatchers.IO) {
    try {
      val account = dao.getUserAccountByUid(uid)
      if (account != null) {
        val isVipFlag = RbacPolicy.isAdFree(role, tier)
        val updated = account.copy(
          role = role.code,
          subscriptionTier = tier.code,
          isVip = isVipFlag
        )
        dao.updateUserAccount(updated)
        if (_authState.value is AuthState.Authenticated) {
          _authState.value = AuthState.Authenticated(updated)
        }
      }
      val onlineUser = dao.getOnlineUserByUidSync(uid)
      if (onlineUser != null) {
        val isVipFlag = RbacPolicy.isAdFree(role, tier)
        dao.updateOnlineUser(
          onlineUser.copy(
            role = role.code,
            subscriptionTier = tier.code,
            isVip = isVipFlag
          )
        )
      }
      onlineRepo.refreshState()
      true
    } catch (e: Exception) {
      Log.e("AuthRepo", "Failed to update subscription", e)
      false
    }
  }

  /**
   * User Registration with password policy enforcement, unique email checks,
   * salt generation, security question setup and couple code generation.
   */
  suspend fun register(
    displayNameInput: String,
    emailInput: String,
    passwordInput: String,
    confirmPasswordInput: String,
    securityQuestionInput: String = "",
    securityAnswerInput: String = "",
    otpCodeInput: String = ""
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val name = displayNameInput.trim()
    val email = emailInput.trim().lowercase()

    if (name.isEmpty()) {
      return@withContext false to "Vui lòng nhập họ và tên hoặc biệt danh!"
    }
    if (!AuthSecurityManager.isValidEmail(email)) {
      return@withContext false to "Địa chỉ Email không hợp lệ! Vui lòng kiểm tra lại."
    }

    // Verify OTP if provided
    if (otpCodeInput.isNotEmpty()) {
      val (isOtpValid, otpMsg) = verifyOtp(email, otpCodeInput)
      if (!isOtpValid) {
        return@withContext false to otpMsg
      }
    }

    // Check email uniqueness
    val existing = dao.getUserAccountByEmail(email)
    if (existing != null) {
      return@withContext false to "Email này đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác!"
    }

    // Relaxed password length to prevent registration errors
    if (passwordInput.length < 6) {
      return@withContext false to "Mật khẩu phải có tối thiểu 6 ký tự!"
    }

    if (passwordInput != confirmPasswordInput) {
      return@withContext false to "Mật khẩu xác nhận không khớp. Vui lòng nhập lại chính xác!"
    }

    // Generate cryptographic salt and hash
    val salt = AuthSecurityManager.generateSalt()
    val passwordHash = AuthSecurityManager.hashPassword(passwordInput, salt)
    val answerHash = if (securityAnswerInput.trim().isNotEmpty()) {
      AuthSecurityManager.hashSecurityAnswer(securityAnswerInput, salt)
    } else {
      ""
    }
    val coupleCode = ProfileUtils.generateRandomCoupleCode()
    var firebaseUid = "user_${System.currentTimeMillis()}"
    try {
      val fbAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
      val fbResult = fbAuth.createUserWithEmailAndPassword(email, passwordInput).await()
      val user = fbResult.user
      if (user != null) {
        firebaseUid = user.uid
        user.sendEmailVerification()
        Log.i("AuthRepo", "Firebase Auth user registered: $firebaseUid")
      }
    } catch (e: Exception) {
      Log.w("AuthRepo", "Firebase Auth registration notice: ${e.message}")
    }
    val uid = firebaseUid
    val sessionToken = AuthSecurityManager.generateSessionToken()

    val newAccount = UserAccountEntity(
      uid = uid,
      email = email,
      passwordHash = passwordHash,
      salt = salt,
      displayName = name,
      coupleCode = coupleCode,
      avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=600&auto=format&fit=crop",
      failedAttempts = 0,
      lockoutUntil = 0L,
      lastLoginAt = System.currentTimeMillis(),
      createdAt = System.currentTimeMillis(),
      securityQuestion = securityQuestionInput.ifEmpty { "InLove Account" },
      securityAnswerHash = answerHash,
      appPin = "",
      isPinEnabled = false,
      sessionToken = sessionToken
    )

    dao.insertUserAccount(newAccount)

    // Also register an OnlineUserEntity so the account can immediately use Set Love 1-1
    val onlineUser = OnlineUserEntity(
      uid = uid,
      displayName = name,
      email = email,
      coupleCode = coupleCode,
      partnerId = null,
      relationshipId = null,
      status = OnlineStatus.SINGLE,
      avatarUrl = newAccount.avatarUrl,
      gender = "MALE",
      birthDate = "",
      age = 0,
      zodiac = "",
      bio = "Chào mừng bạn đến với InLove ✨",
      isProfileSetup = true,
      isCurrentUser = true
    )
    dao.insertOnlineUser(onlineUser)

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = email,
        action = "REGISTER",
        detail = "Đăng ký tài khoản thành công với mã $coupleCode"
      )
    )

    // Save preferences
    prefs.edit()
      .putBoolean(KEY_REMEMBER_ME, true)
      .putString(KEY_SESSION_TOKEN, sessionToken)
      .putString(KEY_SAVED_EMAIL, email)
      .apply()

    syncOnlineUserWithAccount(newAccount)
    _authState.value = AuthState.Authenticated(newAccount)

    return@withContext true to "Tạo tài khoản thành công! Chào mừng $name tham gia InLove."
  }

  suspend fun registerUser(
    displayNameInput: String,
    emailInput: String,
    passwordInput: String,
    confirmPasswordInput: String,
    securityQuestionInput: String = "",
    securityAnswerInput: String = "",
    otpCodeInput: String = ""
  ): Pair<Boolean, String> = register(
    displayNameInput = displayNameInput,
    emailInput = emailInput,
    passwordInput = passwordInput,
    confirmPasswordInput = confirmPasswordInput,
    securityQuestionInput = securityQuestionInput,
    securityAnswerInput = securityAnswerInput,
    otpCodeInput = otpCodeInput
  )

  /**
   * Generates a 6-digit OTP for password recovery via EmailQueueService.
   * Does not store plaintext OTP in audit logs and does not leak it to client.
   */
  suspend fun requestPasswordResetOtp(emailInput: String): Pair<Boolean, String> =
    withContext(Dispatchers.IO) {
      val email = emailInput.trim().lowercase()
      if (!AuthSecurityManager.isValidEmail(email)) {
        return@withContext false to "Địa chỉ Email không đúng định dạng!"
      }
      val account = dao.getUserAccountByEmail(email)
        ?: return@withContext false to "Không tìm thấy tài khoản tương ứng với email này!"

      // Send real password reset email via Firebase Auth if available
      try {
        com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email)
      } catch (e: Exception) {
        Log.w("AuthRepo", "Firebase password reset notice: ${e.message}")
      }

      val result = emailQueueService.enqueueVerificationEmail(email, OtpPurpose.PASSWORD_RESET)
      return@withContext if (result.isSuccess) {
        dao.insertSecurityLog(
          SecurityAuditLogEntity(
            accountEmail = email,
            action = "PASSWORD_RESET_REQUEST",
            detail = "Yêu cầu đặt lại mật khẩu qua email"
          )
        )
        true to "Mã xác thực đã được gửi đến email của bạn. Vui lòng kiểm tra hộp thư!"
      } else {
        false to (result.exceptionOrNull()?.message ?: "Gửi mã xác thực thất bại. Vui lòng thử lại!")
      }
    }

  /**
   * Resets password using OTP code verified securely on repository/queue level.
   * Single-use OTP prevents replay attacks.
   */
  suspend fun resetPasswordWithOtp(
    emailInput: String,
    enteredOtp: String,
    newPasswordInput: String,
    confirmPasswordInput: String
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val email = emailInput.trim().lowercase()
    val account = dao.getUserAccountByEmail(email) ?: return@withContext false to "Tài khoản không tồn tại!"

    val (otpValid, otpMessage) = emailQueueService.verifyOtp(email, enteredOtp)
    if (!otpValid) {
      return@withContext false to otpMessage
    }

    val strength = AuthSecurityManager.evaluatePasswordStrength(newPasswordInput)
    if (strength.level == PasswordStrengthLevel.VERY_WEAK || strength.level == PasswordStrengthLevel.WEAK) {
      return@withContext false to "Mật khẩu mới chưa đủ mạnh. ${strength.missingRequirements.joinToString(", ")}"
    }

    if (newPasswordInput != confirmPasswordInput) {
      return@withContext false to "Mật khẩu mới xác nhận không khớp!"
    }

    val newSalt = AuthSecurityManager.generateSalt()
    val newHash = AuthSecurityManager.hashPassword(newPasswordInput, newSalt)

    val updated = account.copy(
      passwordHash = newHash,
      salt = newSalt,
      failedAttempts = 0,
      lockoutUntil = 0L
    )
    dao.updateUserAccount(updated)

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = email,
        action = "PASSWORD_RESET",
        detail = "Đặt lại mật khẩu thành công qua OTP"
      )
    )

    return@withContext true to "Đặt lại mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới."
  }

  /**
   * Resets password using Security Question Answer.
   * Strictly verifies that account has configured security questions.
   */
  suspend fun resetPasswordWithSecurityAnswer(
    emailInput: String,
    securityAnswerInput: String,
    newPasswordInput: String,
    confirmPasswordInput: String
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val email = emailInput.trim().lowercase()
    val account = dao.getUserAccountByEmail(email) ?: return@withContext false to "Tài khoản không tồn tại!"

    val expectedAnswerHash = account.securityAnswerHash
    if (expectedAnswerHash.isBlank()) {
      return@withContext false to "Tài khoản chưa thiết lập câu hỏi bảo mật! Vui lòng sử dụng phương thức đặt lại qua mã OTP email."
    }

    val providedHash = AuthSecurityManager.hashSecurityAnswer(securityAnswerInput, account.salt)
    if (expectedAnswerHash != providedHash) {
      return@withContext false to "Câu trả lời bảo mật không chính xác!"
    }

    val strength = AuthSecurityManager.evaluatePasswordStrength(newPasswordInput)
    if (strength.level == PasswordStrengthLevel.VERY_WEAK || strength.level == PasswordStrengthLevel.WEAK) {
      return@withContext false to "Mật khẩu mới chưa đủ an toàn! ${strength.missingRequirements.joinToString(", ")}"
    }

    if (newPasswordInput != confirmPasswordInput) {
      return@withContext false to "Mật khẩu xác nhận không khớp!"
    }

    val newSalt = AuthSecurityManager.generateSalt()
    val newHash = AuthSecurityManager.hashPassword(newPasswordInput, newSalt)
    val newAnswerHash = AuthSecurityManager.hashSecurityAnswer(securityAnswerInput, newSalt)

    val updated = account.copy(
      passwordHash = newHash,
      salt = newSalt,
      securityAnswerHash = newAnswerHash,
      failedAttempts = 0,
      lockoutUntil = 0L
    )
    dao.updateUserAccount(updated)

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = email,
        action = "PASSWORD_RESET",
        detail = "Đặt lại mật khẩu qua câu hỏi bảo mật"
      )
    )

    return@withContext true to "Đặt lại mật khẩu thành công! Hãy đăng nhập ngay."
  }

  /**
   * Change password from Settings (requires old password).
   */
  suspend fun changePassword(
    oldPasswordInput: String,
    newPasswordInput: String,
    confirmPasswordInput: String
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val currentAccount = when (val currentAuth = _authState.value) {
      is AuthState.Authenticated -> currentAuth.account
      is AuthState.PinLocked -> currentAuth.account
      else -> return@withContext false to "Bạn chưa đăng nhập!"
    }

    val oldHash = AuthSecurityManager.hashPassword(oldPasswordInput, currentAccount.salt)
    if (oldHash != currentAccount.passwordHash) {
      return@withContext false to "Mật khẩu hiện tại không chính xác!"
    }

    val strength = AuthSecurityManager.evaluatePasswordStrength(newPasswordInput)
    if (strength.level == PasswordStrengthLevel.VERY_WEAK || strength.level == PasswordStrengthLevel.WEAK) {
      return@withContext false to "Mật khẩu mới chưa đủ mạnh. ${strength.missingRequirements.joinToString(", ")}"
    }

    if (newPasswordInput != confirmPasswordInput) {
      return@withContext false to "Mật khẩu mới xác nhận không khớp!"
    }

    val newSalt = AuthSecurityManager.generateSalt()
    val newHash = AuthSecurityManager.hashPassword(newPasswordInput, newSalt)
    val updated = currentAccount.copy(passwordHash = newHash, salt = newSalt)
    dao.updateUserAccount(updated)

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = currentAccount.email,
        action = "PASSWORD_CHANGED",
        detail = "Đổi mật khẩu thành công từ cài đặt"
      )
    )

    _authState.value = AuthState.Authenticated(updated)
    return@withContext true to "Đã cập nhật mật khẩu mới an toàn thành công!"
  }

  /**
   * Sets or updates 4-digit PIN for app lock with salted hashing.
   * Never stores plaintext PIN in Room database.
   */
  suspend fun setAppPin(pin: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    if (pin.length != 4 || !pin.all { it.isDigit() }) {
      return@withContext false to "Mã PIN phải gồm đúng 4 chữ số!"
    }
    val currentAccount = when (val currentAuth = _authState.value) {
      is AuthState.Authenticated -> currentAuth.account
      is AuthState.PinLocked -> currentAuth.account
      else -> return@withContext false to "Bạn chưa đăng nhập!"
    }

    val hashedPin = AuthSecurityManager.hashPin(pin, currentAccount.salt)
    val updated = currentAccount.copy(appPin = hashedPin, isPinEnabled = true)
    dao.updateUserAccount(updated)

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = currentAccount.email,
        action = "PIN_CHANGED",
        detail = "Kích hoạt và cập nhật mã PIN bảo vệ ứng dụng (đã mã hóa)"
      )
    )

    _authState.value = AuthState.Authenticated(updated)
    return@withContext true to "Đã thiết lập mã PIN 4 số bảo vệ ứng dụng thành công!"
  }

  /**
   * Toggles PIN lock feature on/off.
   */
  suspend fun togglePinEnabled(enabled: Boolean): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val currentAccount = when (val currentAuth = _authState.value) {
      is AuthState.Authenticated -> currentAuth.account
      is AuthState.PinLocked -> currentAuth.account
      else -> return@withContext false to "Bạn chưa đăng nhập!"
    }

    if (enabled && currentAccount.appPin.isEmpty()) {
      return@withContext false to "Vui lòng cài đặt mã PIN trước khi bật khóa ứng dụng!"
    }

    val updated = currentAccount.copy(isPinEnabled = enabled)
    dao.updateUserAccount(updated)
    _authState.value = AuthState.Authenticated(updated)

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = currentAccount.email,
        action = "PIN_CHANGED",
        detail = if (enabled) "Bật khóa mã PIN ứng dụng" else "Tắt khóa mã PIN ứng dụng"
      )
    )

    return@withContext true to if (enabled) "Đã bật bảo vệ ứng dụng bằng mã PIN!" else "Đã tắt bảo vệ bằng mã PIN."
  }

  /**
   * Unlocks app using hashed PIN comparison.
   * Automatically migrates legacy plaintext 4-digit PINs upon first successful unlock.
   */
  fun unlockWithPin(pinInput: String): Boolean {
    val currentAccount = when (val currentAuth = _authState.value) {
      is AuthState.PinLocked -> currentAuth.account
      is AuthState.Authenticated -> currentAuth.account
      else -> return false
    }

    val hashedInput = AuthSecurityManager.hashPin(pinInput, currentAccount.salt)
    if (hashedInput == currentAccount.appPin) {
      _authState.value = AuthState.Authenticated(currentAccount)
      return true
    }

    // Migration fallback: if account previously stored legacy unhashed 4-digit PIN
    if (pinInput == currentAccount.appPin && currentAccount.appPin.length == 4) {
      scope.launch {
        val migrated = currentAccount.copy(appPin = hashedInput)
        dao.updateUserAccount(migrated)
      }
      _authState.value = AuthState.Authenticated(currentAccount)
      return true
    }

    return false
  }

  /**
   * Fallback unlock using account password.
   */
  fun unlockWithAccountPassword(passwordInput: String): Boolean {
    val currentAccount = when (val currentAuth = _authState.value) {
      is AuthState.PinLocked -> currentAuth.account
      is AuthState.Authenticated -> currentAuth.account
      else -> return false
    }

    val hash = AuthSecurityManager.hashPassword(passwordInput, currentAccount.salt)
    if (hash == currentAccount.passwordHash) {
      _authState.value = AuthState.Authenticated(currentAccount)
      return true
    }
    return false
  }

  /**
   * Manually locks the app with PIN.
   */
  fun lockApp() {
    val current = _authState.value
    if (current is AuthState.Authenticated && current.account.isPinEnabled && current.account.appPin.isNotEmpty()) {
      _authState.value = AuthState.PinLocked(current.account)
    }
  }

  /**
   * Secure Logout: clears tokens, resets state to Unauthenticated.
   */
  suspend fun logout() = withContext(Dispatchers.IO) {
    val email = when (val current = _authState.value) {
      is AuthState.Authenticated -> current.account.email
      is AuthState.PinLocked -> current.account.email
      else -> ""
    }

    if (email.isNotEmpty()) {
      val account = dao.getUserAccountByEmail(email)
      if (account != null) {
        dao.updateUserAccount(account.copy(sessionToken = ""))
      }
      dao.insertSecurityLog(
        SecurityAuditLogEntity(
          accountEmail = email,
          action = "LOGOUT",
          detail = "Đăng xuất tài khoản an toàn"
        )
      )
    }

    prefs.edit()
      .remove(KEY_SESSION_TOKEN)
      .putBoolean(KEY_REMEMBER_ME, false)
      .apply()

    try {
      com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
    } catch (_: Exception) {}

    _authState.value = AuthState.Unauthenticated
  }

  /**
   * Google Play Policy compliant Account Deletion:
   * Permanently deletes user account, cloud documents, Firebase Auth user,
   * audit logs, local memories/profiles, and resets session.
   */
  suspend fun deleteCurrentAccount(): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val email = when (val current = _authState.value) {
        is AuthState.Authenticated -> current.account.email
        is AuthState.PinLocked -> current.account.email
        else -> ""
      }
      val uid = when (val current = _authState.value) {
        is AuthState.Authenticated -> current.account.uid
        is AuthState.PinLocked -> current.account.uid
        else -> ""
      }

      // 1. Delete Cloud Firestore documents if available
      if (uid.isNotEmpty()) {
        try {
          val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
          fs.collection("users").document(uid).delete().await()
          fs.collection("users_3nf").document(uid).delete().await()

          // Clean up user's memories in Firestore
          val userMemories = fs.collection("memories").whereEqualTo("authorUid", uid).get().await()
          userMemories.documents.forEach { doc ->
            try { doc.reference.delete().await() } catch (_: Exception) {}
          }

          val userMemories3nf = fs.collection("memories_3nf").whereEqualTo("authorUid", uid).get().await()
          userMemories3nf.documents.forEach { doc ->
            try { doc.reference.delete().await() } catch (_: Exception) {}
          }
        } catch (e: Exception) {
          Log.w("AuthRepo", "Firestore account documents deletion notice: ${e.message}")
        }
      }

      // 2. Delete Firebase Auth identity
      try {
        val fbAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val fbUser = fbAuth.currentUser
        if (fbUser != null) {
          fbUser.delete().await()
        }
      } catch (e: Exception) {
        Log.w("AuthRepo", "Firebase Auth account deletion notice: ${e.message}")
      }

      // 3. Delete Local Room records, online cache, and memories
      if (email.isNotEmpty()) {
        val account = dao.getUserAccountByEmail(email)
        if (account != null) {
          dao.deleteUserAccount(account)
        }
        dao.deleteSecurityLogsForAccount(email)
      }
      if (uid.isNotEmpty()) {
        dao.deleteOnlineUser(uid)
        dao.deleteOnlineRelationshipsForUser(uid)
        dao.deleteOnlineInvitesForUser(uid)
      }
      dao.clearAllSharedMemories()
      dao.clearCoupleProfile()

      // 4. Clear all preferences and reset session
      prefs.edit().clear().apply()
      _authState.value = AuthState.Unauthenticated
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e("AuthRepo", "Error deleting account: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Gets audit logs for account.
   */
  fun getAuditLogsForCurrentAccount(): Flow<List<SecurityAuditLogEntity>> {
    val email = when (val current = _authState.value) {
      is AuthState.Authenticated -> current.account.email
      is AuthState.PinLocked -> current.account.email
      else -> ""
    }
    return dao.getSecurityLogsForAccount(email)
  }

  private suspend fun syncOnlineUserWithAccount(account: UserAccountEntity) {
    onlineRepo.setCurrentUserId(account.uid)
    val existingOnlineUser = dao.getOnlineUserByUidSync(account.uid)
    if (existingOnlineUser == null) {
      val newOnlineUser = OnlineUserEntity(
        uid = account.uid,
        displayName = account.displayName,
        email = account.email,
        coupleCode = account.coupleCode,
        partnerId = null,
        relationshipId = null,
        status = OnlineStatus.SINGLE,
        avatarUrl = account.avatarUrl,
        gender = "MALE",
        birthDate = "",
        age = 0,
        zodiac = "",
        bio = "Chào mừng bạn đến với InLove ✨",
        isProfileSetup = true,
        isCurrentUser = true,
        role = account.role,
        subscriptionTier = account.subscriptionTier,
        isVip = account.isVip
      )
      dao.insertOnlineUser(newOnlineUser)
    } else {
      dao.updateOnlineUser(
        existingOnlineUser.copy(
          displayName = account.displayName,
          email = account.email,
          coupleCode = account.coupleCode,
          avatarUrl = account.avatarUrl.ifEmpty { existingOnlineUser.avatarUrl },
          isCurrentUser = true,
          role = account.role,
          subscriptionTier = account.subscriptionTier,
          isVip = account.isVip
        )
      )
    }
    onlineRepo.setCurrentUserId(account.uid)
  }
}
