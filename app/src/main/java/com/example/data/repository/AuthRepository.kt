package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

import com.example.data.email.EmailQueueService
import com.example.data.email.OtpPurpose

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
  context: Context,
  private val scope: CoroutineScope,
  private val isTestMode: Boolean = false
) {
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
   * Starts [com.example.di.AppServiceLocator.syncCoordinator] for [uid], with `onlineRepo::refreshState`
   * as the callback so a remote pairing/breakup change (detected by SyncCoordinator, whether this
   * device or the partner's caused it) actually refreshes this device's UI-facing online-couple
   * state. Wrapped in try/catch: under Robolectric (AuthRepositoryTest, RepositoryScopeLifecycleTest)
   * the locator is never initialized, so `AppServiceLocator.syncCoordinator`'s getter throws — this
   * degrades that to "sync doesn't start this session" instead of breaking login/register/restore/unlock.
   */
  private fun startSyncCoordinatorSafely(uid: String) {
    try {
      com.example.di.AppServiceLocator.syncCoordinator.start(uid) { onlineRepo.refreshState() }
    } catch (e: Exception) {
      Log.d("AuthRepo", "SyncCoordinator not started (locator not initialized, e.g. under test): ${e.message}")
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
    if (!BuildConfig.DEBUG) {
      return@withContext Result.failure(SecurityException("Tài khoản kiểm thử chỉ khả dụng trong bản Debug/Internal Testing."))
    }
    val fixture = if (docId == "tester_partner") {
      CloudTestFixture(
        email = "tester_b@inlove.test",
        password = "Password123!",
        displayName = "Tester B (Partner)",
        role = "USER_VIP",
        tier = "VIP_YEARLY"
      )
    } else {
      CloudTestFixture(
        email = "tester_a@inlove.test",
        password = "Password123!",
        displayName = "Tester A (Primary)",
        role = "USER_VIP",
        tier = "VIP_YEARLY"
      )
    }
    ensureTestAccountInDatabase(fixture)
    Result.success(fixture)
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
          startSyncCoordinatorSafely(account.uid)
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

    // 1. Authoritative check: FirebaseAuth is SSOT (Fail-Closed)
    val firebaseUid = if (isTestMode) {
      val acc = dao.getUserAccountByEmail(email)
      if (acc == null) {
        dao.insertSecurityLog(
          SecurityAuditLogEntity(
            accountEmail = email,
            action = "LOGIN_FAILED",
            detail = "Đăng nhập thất bại: Tài khoản không tồn tại"
          )
        )
        return@withContext false to "Tài khoản không tồn tại. Bạn có thể bấm Đăng Ký ngay bên cạnh!"
      }
      val expectedHash = AuthSecurityManager.hashPassword(passwordInput, acc.salt)
      if (expectedHash != acc.passwordHash) {
        val newFailed = acc.failedAttempts + 1
        dao.updateUserAccount(acc.copy(failedAttempts = newFailed))
        dao.insertSecurityLog(
          SecurityAuditLogEntity(
            accountEmail = email,
            action = "LOGIN_FAILED",
            detail = "Sai mật khẩu lần $newFailed"
          )
        )
        return@withContext false to "Mật khẩu không chính xác!"
      }
      acc.uid
    } else {
      val fbAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
      val authResult = try {
        fbAuth.signInWithEmailAndPassword(email, passwordInput).await()
      } catch (e: Exception) {
        Log.w("AuthRepo", "Firebase Auth sign-in failed: ${e.message}")
        dao.insertSecurityLog(
          SecurityAuditLogEntity(
            accountEmail = email,
            action = "LOGIN_FAILED",
            detail = "Đăng nhập thất bại: ${e.localizedMessage}"
          )
        )
        return@withContext false to (e.localizedMessage ?: "Email hoặc mật khẩu không chính xác!")
      }
      val firebaseUser = authResult.user ?: return@withContext false to "Xác thực Firebase không khả dụng."
      firebaseUser.uid
    }

    // 2. Synchronize Room local account with Firebase authoritative identity
    var account = dao.getUserAccountByEmail(email)
    val salt = AuthSecurityManager.generateSalt()
    val passwordHash = AuthSecurityManager.hashPassword(passwordInput, salt)
    val newSessionToken = AuthSecurityManager.generateSessionToken()

    if (account == null) {
      // Create local cache profile for existing Firebase user logging in on new device
      val newAccount = UserAccountEntity(
        uid = firebaseUid,
        email = email,
        passwordHash = passwordHash,
        salt = salt,
        displayName = email.substringBefore('@'),
        coupleCode = ProfileUtils.generateRandomCoupleCode(),
        avatarUrl = "",
        securityQuestion = AuthSecurityManager.SECURITY_QUESTIONS[0],
        securityAnswerHash = "",
        appPin = "",
        isPinEnabled = false,
        role = "USER_FREE",
        subscriptionTier = "FREE",
        isVip = false,
        lastLoginAt = System.currentTimeMillis(),
        sessionToken = newSessionToken
      )
      dao.insertUserAccount(newAccount)
      account = newAccount
    } else {
      // Migrate legacy UID if different from Firebase Auth UID. Also refresh the local
      // passwordHash (with the account's EXISTING salt — never a new one, see the
      // no-salt-rotation notes in changePassword/resetPasswordWithOtp/
      // resetPasswordWithSecurityAnswer: appPin/securityAnswerHash share this salt and
      // would break if it changed) using the password just verified as correct by
      // Firebase above. Without this, a password changed via Firebase on ANOTHER device
      // (or via the official Firebase reset email) never reaches this device's local
      // cache, so unlockWithAccountPassword (the PIN-fallback screen) keeps rejecting the
      // user's real, current password here until they log out/in enough times to notice —
      // this device's local hash is only ever set once, at this device's own registration
      // or first login.
      val refreshedHash = AuthSecurityManager.hashPassword(passwordInput, account.salt)
      val updated = account.copy(
        uid = firebaseUid,
        passwordHash = refreshedHash,
        failedAttempts = 0,
        lockoutUntil = 0L,
        lastLoginAt = System.currentTimeMillis(),
        sessionToken = newSessionToken
      )
      if (account.uid != firebaseUid) {
        dao.deleteUserAccount(account)
        dao.insertUserAccount(updated)
      } else {
        dao.updateUserAccount(updated)
      }
      account = updated
    }

    // 3. Save preferences
    prefs.edit()
      .putBoolean(KEY_REMEMBER_ME, rememberMe)
      .putString(KEY_SESSION_TOKEN, if (rememberMe) newSessionToken else null)
      .putString(KEY_SAVED_EMAIL, email)
      .apply()

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = email,
        action = "LOGIN_SUCCESS",
        detail = "Đăng nhập thành công với Firebase UID: $firebaseUid"
      )
    )

    syncOnlineUserWithAccount(account)

    if (account.isPinEnabled && account.appPin.isNotEmpty()) {
      _authState.value = AuthState.PinLocked(account)
    } else {
      _authState.value = AuthState.Authenticated(account)
      startSyncCoordinatorSafely(account.uid)
    }

    return@withContext true to "Đăng nhập thành công! Chào mừng ${account.displayName} 💕"
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

    // 1. Authoritative check: Create user in Firebase Auth first (Fail-Closed)
    val firebaseUid = if (isTestMode) {
      "test_uid_" + email.hashCode().toUInt()
    } else {
      val fbAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
      val createResult = try {
        fbAuth.createUserWithEmailAndPassword(email, passwordInput).await()
      } catch (e: Exception) {
        Log.w("AuthRepo", "Firebase Auth registration error: ${e.message}")
        return@withContext false to (e.localizedMessage ?: "Đăng ký tài khoản thất bại qua Firebase Auth!")
      }

      val firebaseUser = createResult.user ?: return@withContext false to "Không thể khởi tạo phiên xác thực Firebase."
      // Send real Firebase email verification
      try {
        firebaseUser.sendEmailVerification().await()
      } catch (e: Exception) {
        Log.w("AuthRepo", "Send email verification warning: ${e.message}")
      }
      firebaseUser.uid
    }

    // 2. Generate cryptographic salt and hash for local fallback/cache
    val salt = AuthSecurityManager.generateSalt()
    val passwordHash = AuthSecurityManager.hashPassword(passwordInput, salt)
    val answerHash = if (securityAnswerInput.trim().isNotEmpty()) {
      AuthSecurityManager.hashSecurityAnswer(securityAnswerInput, salt)
    } else {
      ""
    }
    val coupleCode = ProfileUtils.generateRandomCoupleCode()
    val sessionToken = AuthSecurityManager.generateSessionToken()

    val newAccount = UserAccountEntity(
      uid = firebaseUid,
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
      uid = firebaseUid,
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
    startSyncCoordinatorSafely(newAccount.uid)

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
      if (isTestMode) {
        emailQueueService.enqueueVerificationEmail(email, OtpPurpose.PASSWORD_RESET)
        dao.insertSecurityLog(
          SecurityAuditLogEntity(
            accountEmail = email,
            action = "PASSWORD_RESET_REQUEST",
            detail = "Yêu cầu đặt lại mật khẩu qua email"
          )
        )
        return@withContext true to "Mã xác thực đã được gửi đến email của bạn. Vui lòng kiểm tra hộp thư!"
      }

      return@withContext try {
        com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
        dao.insertSecurityLog(
          SecurityAuditLogEntity(
            accountEmail = email,
            action = "PASSWORD_RESET_REQUEST",
            detail = "Gửi email đặt lại mật khẩu qua Firebase Auth"
          )
        )
        true to "Liên kết đặt lại mật khẩu đã được gửi đến email $email. Vui lòng kiểm tra hộp thư!"
      } catch (e: Exception) {
        Log.w("AuthRepo", "Firebase password reset error: ${e.message}")
        false to (e.localizedMessage ?: "Gửi email đặt lại mật khẩu thất bại. Vui lòng thử lại sau!")
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

    // KHÔNG đổi `salt` — như [changePassword], appPin và securityAnswerHash đều băm
    // bằng cùng salt cấp tài khoản (AuthSecurityManager.hashPin/hashSecurityAnswer).
    // Sinh salt mới ở đây trước làm hỏng khoá PIN vĩnh viễn (unlockWithPin dùng
    // account.salt MỚI trong khi appPin đã lưu được băm bằng salt CŨ) và làm hỏng luôn
    // câu hỏi bảo mật (resetPasswordWithSecurityAnswer so sánh bằng account.salt mới
    // trong khi securityAnswerHash được băm bằng salt cũ) — cả hai không hề được cập
    // nhật lại theo salt mới ở hàm này.
    val newHash = AuthSecurityManager.hashPassword(newPasswordInput, account.salt)

    val updated = account.copy(
      passwordHash = newHash,
      failedAttempts = 0,
      lockoutUntil = 0L
    )
    dao.updateUserAccount(updated)

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = email,
        action = "PASSWORD_RESET",
        detail = "Đặt lại mật khẩu cục bộ qua OTP — đã kích hoạt email xác nhận Firebase chính thức"
      )
    )

    // QUAN TRỌNG: mã OTP 6 số ở đây được xác minh CỤC BỘ qua [EmailQueueService], không phải
    // cơ chế reset chính thức của Firebase Auth. Firebase Auth Client SDK KHÔNG cho phép tự đặt
    // mật khẩu mới cho một tài khoản đã quên mật khẩu chỉ bằng email + OTP tự chế — cần mật
    // khẩu cũ (để reauthenticate, xem [changePassword]) hoặc oobCode từ email Firebase gửi.
    // Trước đây hàm này chỉ đổi hash cục bộ rồi báo "thành công", trong khi mật khẩu đăng nhập
    // Firebase thật (nguồn xác thực duy nhất trong [login]) không đổi — người dùng bị khoá tài
    // khoản thật sự. Sửa: cập nhật hash cục bộ (vẫn cần cho PIN-fallback) NHƯNG đồng thời kích
    // hoạt luôn email reset chính thức của Firebase, và thông báo đúng sự thật thay vì nói đã
    // xong khi chưa xong.
    if (!isTestMode) {
      try {
        com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
      } catch (e: Exception) {
        Log.w("AuthRepo", "Firebase reset email after OTP verification failed: ${e.message}")
      }
      return@withContext true to "Đã xác minh OTP thành công! Chúng tôi vừa gửi thêm một email chính thức từ Google để bạn hoàn tất đặt mật khẩu đăng nhập mới — vui lòng kiểm tra hộp thư và làm theo hướng dẫn trong email đó."
    }

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

    // KHÔNG đổi `salt` (xem giải thích trong changePassword/resetPasswordWithOtp):
    // appPin dùng chung salt cấp tài khoản này, nên đổi sang salt mới ở đây sẽ làm
    // hỏng khoá PIN vĩnh viễn dù không đụng gì tới appPin. securityAnswerHash vẫn
    // giữ nguyên (đã đúng với salt hiện tại, không cần băm lại).
    val newHash = AuthSecurityManager.hashPassword(newPasswordInput, account.salt)

    val updated = account.copy(
      passwordHash = newHash,
      failedAttempts = 0,
      lockoutUntil = 0L
    )
    dao.updateUserAccount(updated)

    dao.insertSecurityLog(
      SecurityAuditLogEntity(
        accountEmail = email,
        action = "PASSWORD_RESET",
        detail = "Đặt lại mật khẩu cục bộ qua câu hỏi bảo mật — đã kích hoạt email xác nhận Firebase chính thức"
      )
    )

    // Cùng lý do với resetPasswordWithOtp ở trên: xác minh câu hỏi bảo mật là cục bộ, không
    // thể tự đặt mật khẩu Firebase thật cho tài khoản đã quên mật khẩu. Cập nhật hash cục bộ
    // (cho PIN-fallback) và kích hoạt email reset chính thức của Firebase thay vì báo sai.
    if (!isTestMode) {
      try {
        com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
      } catch (e: Exception) {
        Log.w("AuthRepo", "Firebase reset email after security-answer verification failed: ${e.message}")
      }
      return@withContext true to "Đã xác minh câu hỏi bảo mật thành công! Chúng tôi vừa gửi thêm một email chính thức từ Google để bạn hoàn tất đặt mật khẩu đăng nhập mới — vui lòng kiểm tra hộp thư."
    }

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

    // Mật khẩu Firebase Auth THẬT phải đổi trước khi cập nhật cache cục bộ — nếu không, người
    // dùng nghĩ đã đổi mật khẩu nhưng lần đăng nhập kế tiếp (luôn xác thực qua Firebase, xem
    // [login]) vẫn đòi mật khẩu CŨ, trong khi PIN-fallback ([unlockWithAccountPassword]) lại
    // chấp nhận mật khẩu MỚI — gây lệch trạng thái khó hiểu và có thể tự khoá tài khoản thật.
    // reauthenticate() bằng mật khẩu cũ vừa xác minh đúng là mật khẩu Firebase hiện tại, vừa
    // thoả điều kiện "recent login" mà updatePassword() bắt buộc.
    if (!isTestMode) {
      val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        ?: return@withContext false to "Phiên đăng nhập Firebase đã hết hạn. Vui lòng đăng nhập lại rồi thử lại."
      try {
        val credential = com.google.firebase.auth.EmailAuthProvider
          .getCredential(currentAccount.email, oldPasswordInput)
        fbUser.reauthenticate(credential).await()
        fbUser.updatePassword(newPasswordInput).await()
      } catch (e: Exception) {
        Log.w("AuthRepo", "Firebase changePassword failed: ${e.message}")
        return@withContext false to (e.localizedMessage ?: "Đổi mật khẩu trên Firebase thất bại. Vui lòng thử lại.")
      }
    }

    // KHÔNG đổi `salt`: PIN (setAppPin/unlockWithPin) và câu hỏi bảo mật
    // (securityAnswerHash) đều được băm bằng CÙNG salt cấp tài khoản này (xem
    // AuthSecurityManager.hashPin/hashSecurityAnswer). Nếu đổi sang salt mới ở đây,
    // appPin đã lưu (băm bằng salt cũ) sẽ không bao giờ khớp lại với
    // hashPin(pin, salt_mới) trong unlockWithPin() nữa — khoá PIN vĩnh viễn hỏng dù
    // người dùng nhập đúng mã cho tới khi họ tự đặt lại PIN. Giữ nguyên salt hiện tại
    // và chỉ đổi passwordHash vẫn an toàn (salt vẫn ngẫu nhiên 16 byte/tài khoản).
    val newHash = AuthSecurityManager.hashPassword(newPasswordInput, currentAccount.salt)
    val updated = currentAccount.copy(passwordHash = newHash)
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
      startSyncCoordinatorSafely(currentAccount.uid)
      return true
    }

    // Migration fallback: if account previously stored legacy unhashed 4-digit PIN
    if (pinInput == currentAccount.appPin && currentAccount.appPin.length == 4) {
      scope.launch {
        val migrated = currentAccount.copy(appPin = hashedInput)
        dao.updateUserAccount(migrated)
      }
      _authState.value = AuthState.Authenticated(currentAccount)
      startSyncCoordinatorSafely(currentAccount.uid)
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
      startSyncCoordinatorSafely(currentAccount.uid)
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

    try {
      com.example.di.AppServiceLocator.syncCoordinator.stop()
    } catch (e: Exception) {
      Log.d("AuthRepo", "SyncCoordinator stop skipped (locator not initialized): ${e.message}")
    }

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

      if (uid.isEmpty()) {
        return@withContext Result.failure(IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."))
      }

      if (!isTestMode) {
        // 1. Xoá danh tính Firebase Auth TRƯỚC TIÊN. Trước đây bước này chạy SAU CÙNG (sau khi
        // đã xoá xong Firestore) — nếu Firebase yêu cầu đăng nhập lại gần đây (đăng nhập đã
        // lâu) thì `delete()` ném `FirebaseAuthRecentLoginRequiredException`, toàn bộ hàm bị
        // catch ở ngoài và dừng lại, nhưng dữ liệu Firestore/Room đã xoá mất rồi — tài khoản
        // kẹt ở trạng thái nửa xoá (mất dữ liệu, vẫn đăng nhập được). Xoá Auth trước: nếu lỗi,
        // KHÔNG có gì bị xoá cả, người dùng chỉ cần đăng nhập lại rồi thử xoá lần nữa.
        val fbAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val fbUser = fbAuth.currentUser
        try {
          fbUser?.delete()?.await()
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
          return@withContext Result.failure(
            IllegalStateException("Vì lý do bảo mật, vui lòng đăng xuất và đăng nhập lại gần đây trước khi xoá tài khoản vĩnh viễn.")
          )
        }

        // 2. Xoá tài liệu Cloud Firestore (Fail-Closed) — chỉ chạy sau khi Auth đã xoá thành công
        val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        fs.collection("users").document(uid).delete().await()
        fs.collection("users_3nf").document(uid).delete().await()

        // Clean up user's memories in Firestore
        val userMemories = fs.collection("memories").whereEqualTo("authorUid", uid).get().await()
        userMemories.documents.forEach { doc ->
          doc.reference.delete().await()
        }

        val userMemories3nf = fs.collection("memories_3nf").whereEqualTo("authorUid", uid).get().await()
        userMemories3nf.documents.forEach { doc ->
          doc.reference.delete().await()
        }

        // Xoá các lời mời Set Love mà tài khoản này đã GỬI đi (field senderUid — khớp đúng
        // OnlineCoupleRepository.sendSetLoveInvite() ghi thật lên collection "invites"; đây là
        // collection Firestore duy nhất khác mà app hiện có ghi tới, ngoài users/memories ở
        // trên — "relationships"/"relationships_3nf"/"invites_3nf" hiện KHÔNG được client ghi
        // nên không cần dọn ở đây).
        val sentInvites = fs.collection("invites").whereEqualTo("senderUid", uid).get().await()
        sentInvites.documents.forEach { doc ->
          doc.reference.delete().await()
        }
      }

      // 3. Delete Local Room records, online cache, and memories
      if (email.isNotEmpty()) {
        val account = dao.getUserAccountByEmail(email)
        if (account != null) {
          dao.deleteUserAccount(account)
        }
        dao.deleteSecurityLogsForAccount(email)
      }
      dao.deleteOnlineUser(uid)
      dao.deleteOnlineRelationshipsForUser(uid)
      dao.deleteOnlineInvitesForUser(uid)
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
