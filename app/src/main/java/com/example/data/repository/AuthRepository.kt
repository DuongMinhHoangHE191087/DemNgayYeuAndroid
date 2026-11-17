package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.example.BuildConfig
import com.example.data.db.InLoveDao
import com.example.data.media.MediaPath
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

import com.example.data.email.EmailQueueService
import com.example.data.email.OtpPurpose

sealed class AuthState {
  data object Unauthenticated : AuthState()
  data class Authenticated(val account: UserAccountEntity) : AuthState()
  data class PinLocked(val account: UserAccountEntity) : AuthState()
}

/** Outcome of an app-lock unlock attempt; the UI localises it (no raw SDK/domain strings). */
sealed interface UnlockResult {
  data object Success : UnlockResult
  data class Wrong(val attemptsBeforeLock: Int) : UnlockResult
  data class Locked(val untilMillis: Long) : UnlockResult
  data object NoSession : UnlockResult
  data class Error(val reason: com.example.data.auth.SocialAuthException.Reason) : UnlockResult
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
  private val auth: com.example.data.auth.AuthGateway,
  private val hooks: com.example.data.auth.SessionHooks,
  private val vault: com.example.data.db.AccountDataVault? = null,
  private val facebook: com.example.data.auth.FacebookAuthGateway = com.example.data.auth.FirebaseFacebookGateway(),
  // Test truyền bản giả để kiểm thứ tự xoá (AccountDeletionOrderTest) và không đụng Firebase.
  private val remoteCleanup: RemoteAccountCleanup = FirebaseRemoteAccountCleanup()
) {
  private val mediaFilesDir: java.io.File = context.filesDir
  private val prefs: SharedPreferences =
    context.getSharedPreferences("inlove_auth_prefs", Context.MODE_PRIVATE)
  // Prefs của ProfileSync: xoá tài khoản chỉ gỡ key `synced_<uid>` của uid đó.
  private val profileSyncPrefs: SharedPreferences =
    context.getSharedPreferences(com.example.data.sync.ProfileSync.PREFS, Context.MODE_PRIVATE)
  private val attempts = com.example.data.auth.UnlockAttemptStore(
    context.getSharedPreferences("inlove_unlock_attempts", Context.MODE_PRIVATE)
  )

  private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
  val authState: StateFlow<AuthState> = _authState.asStateFlow()

  val emailQueueService: EmailQueueService = EmailQueueService.getInstance(context)

  companion object {
    private const val KEY_SESSION_TOKEN = "key_session_token"
    private const val KEY_REMEMBER_ME = "key_remember_me"
    private const val KEY_SAVED_EMAIL = "key_saved_email"
    /** passwordHash marker for accounts created through a sign-in provider (no local password). */
    const val PROVIDER_HASH_PREFIX = "oauth:"
    /** Quá thời gian này khi xoá dữ liệu đám mây thì dừng và báo thử lại; Auth chưa bị xoá nên không mất gì. */
    private const val CLOUD_CLEANUP_TIMEOUT_MS = 20_000L
    /** reload() failures that mean the cached session is no longer valid (fail-closed); anything else keeps offline access. */
    private val SESSION_INVALID_CODES = setOf(
      com.example.data.auth.AuthErrorCode.USER_NOT_FOUND,
      com.example.data.auth.AuthErrorCode.USER_DISABLED,
      com.example.data.auth.AuthErrorCode.INVALID_CREDENTIALS
    )
  }

  /** Swaps the personal-data scope BEFORE the auth state flips, so sync never writes into the wrong account. */
  private suspend fun enterScope(email: String?) {
    vault?.switchTo(com.example.data.db.AccountDataVault.scopeOf(email))
    if (email == null) onlineRepo.setCurrentUserId("") // drop the previous account's online identity/relationship
  }

  /** Đang ở chế độ khách và có dữ liệu tự tạo: đăng ký phải hỏi người dùng giữ hay bỏ. */
  suspend fun guestHasData(): Boolean = withContext(Dispatchers.IO) {
    vault != null && vault.currentScope == com.example.data.db.AccountDataVault.GUEST && vault.hasPersonalData()
  }

  // Tests join it to wait for the restore path (see AuthRepositoryTest).
  internal val restoreJob = scope.launch {
    restoreSession()
  }

  /**
   * Fires [com.example.data.auth.SessionHooks.onSessionStarted] for [uid] (the production hook starts the
   * SyncCoordinator with `onlineRepo::refreshState`, so a remote pairing/breakup change refreshes this
   * device's online-couple state). Wrapped in try/catch: a hook that cannot run (e.g. the service
   * locator is not initialized) degrades to "sync doesn't start this session" instead of breaking
   * login/register/restore/unlock.
   */
  private fun startSyncCoordinatorSafely(uid: String) {
    // Cloud actions need a live Firebase session for this very uid; a cached local account alone
    // (expired/revoked session, or offline restore) stays fully usable but local-only.
    if (auth.currentUser?.uid != uid) {
      Log.d("AuthRepo", "No matching Firebase session; cloud sync stays off")
      return
    }
    try {
      hooks.onSessionStarted(uid)
    } catch (e: Exception) {
      Log.d("AuthRepo", "Session start hook failed (e.g. locator not initialized): ${e.message}")
    }
  }

  /** Vietnamese text for a gateway failure; codes the UI does not special-case fall back to [fallback]. */
  private fun authErrorMessage(e: com.example.data.auth.AuthException, fallback: String): String =
    when (e.code) {
      com.example.data.auth.AuthErrorCode.INVALID_CREDENTIALS -> "Email hoặc mật khẩu không chính xác!"
      com.example.data.auth.AuthErrorCode.USER_NOT_FOUND -> "Tài khoản không tồn tại. Bạn có thể bấm Đăng Ký ngay bên cạnh!"
      com.example.data.auth.AuthErrorCode.EMAIL_IN_USE -> "Email này đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác!"
      com.example.data.auth.AuthErrorCode.NETWORK -> "Không có kết nối mạng. Vui lòng thử lại."
      else -> e.cause?.localizedMessage ?: fallback
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
   * Checks the cached account against Firebase Auth in the background. A deleted/disabled user or a
   * revoked token signs the cached session out; any network problem keeps offline local access.
   */
  private fun reconcileFirebaseSession(account: UserAccountEntity) {
    scope.launch(Dispatchers.IO) {
      val user = auth.currentUser ?: return@launch
      if (user.uid != account.uid) return@launch
      try {
        auth.reloadCurrentUser()
      } catch (e: com.example.data.auth.AuthException) {
        // ponytail: `cause` checks cover FirebaseAuthInvalidUserException codes the gateway maps to UNKNOWN
        // (e.g. ERROR_USER_TOKEN_EXPIRED); drop them once AndroidAuthGateway maps those codes.
        if (e.code in SESSION_INVALID_CODES ||
          e.cause is com.google.firebase.auth.FirebaseAuthInvalidUserException ||
          e.cause is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
        ) {
          Log.w("AuthRepo", "Firebase user no longer valid (${e.code}); signing out cached session")
          logout()
        } else {
          Log.d("AuthRepo", "Firebase reconcile skipped (offline?): ${e.message}")
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.d("AuthRepo", "Firebase reconcile skipped (offline?): ${e.message}")
      }
    }
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
        enterScope(account.email)
        // If PIN is enabled, lock upon relaunch for security
        val unlocked = !(account.isPinEnabled && account.appPin.isNotEmpty())
        _authState.value = if (unlocked) AuthState.Authenticated(account) else AuthState.PinLocked(account)
        syncOnlineUserWithAccount(account)
        // Hook chạy SAU khi dòng online_users đã khớp tài khoản (ProfileSync lấy key khởi đầu từ dòng đó), như login/register.
        if (unlocked) startSyncCoordinatorSafely(account.uid)
        reconcileFirebaseSession(account)
        return@withContext
      }
    }
    enterScope(null)
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
    val firebaseUid = try {
      auth.signInWithEmail(email, passwordInput).uid
    } catch (e: com.example.data.auth.AuthException) {
      Log.w("AuthRepo", "Firebase Auth sign-in failed: ${e.message}")
      val message = authErrorMessage(e, "Email hoặc mật khẩu không chính xác!")
      dao.insertSecurityLog(
        SecurityAuditLogEntity(
          accountEmail = email,
          action = "LOGIN_FAILED",
          detail = "Đăng nhập thất bại: $message"
        )
      )
      return@withContext false to message
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
      // no-salt-rotation notes in changePassword: appPin/securityAnswerHash share this salt and
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

    enterScope(account.email)
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
    otpCodeInput: String = "",
    keepGuestData: Boolean = true
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val name = com.example.ui.util.cleanName(displayNameInput)
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
    val firebaseUid = try {
      auth.createUserWithEmail(email, passwordInput).uid
    } catch (e: com.example.data.auth.AuthException) {
      Log.w("AuthRepo", "Firebase Auth registration error: ${e.message}")
      return@withContext false to authErrorMessage(e, "Đăng ký tài khoản thất bại qua Firebase Auth!")
    }
    // Send real Firebase email verification
    try {
      auth.sendEmailVerification()
    } catch (e: com.example.data.auth.AuthException) {
      Log.w("AuthRepo", "Send email verification warning: ${e.message}")
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
      avatarUrl = "",
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
      bio = com.example.ui.util.WELCOME_BIO,
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

    // Đăng ký từ chế độ khách: người dùng chọn giữ dữ liệu khách cho tài khoản mới (mặc định) hoặc bắt đầu mới.
    // Bắt đầu mới: enterScope cất dữ liệu khách vào ngăn của khách, tài khoản mới trống, không gán ngầm.
    if (keepGuestData && vault != null && vault.currentScope == com.example.data.db.AccountDataVault.GUEST) {
      vault.adoptCurrentInto(com.example.data.db.AccountDataVault.scopeOf(email))
    } else {
      enterScope(email)
    }
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
   * Sends a Firebase Auth password-reset link email (native Firebase flow, not a custom OTP).
   * Does not reveal whether the address has an account.
   */
  suspend fun requestPasswordResetOtp(emailInput: String): Pair<Boolean, String> =
    withContext(Dispatchers.IO) {
      val email = emailInput.trim().lowercase()
      if (!AuthSecurityManager.isValidEmail(email)) {
        return@withContext false to "Địa chỉ Email không đúng định dạng!"
      }
      return@withContext try {
        auth.sendPasswordResetEmail(email)
        dao.insertSecurityLog(
          SecurityAuditLogEntity(
            accountEmail = email,
            action = "PASSWORD_RESET_REQUEST",
            detail = "Gửi email đặt lại mật khẩu qua Firebase Auth"
          )
        )
        true to "Liên kết đặt lại mật khẩu đã được gửi đến email $email. Vui lòng kiểm tra hộp thư!"
      } catch (e: com.example.data.auth.AuthException) {
        when (e.code) {
          // Do not reveal whether the address has an account.
          com.example.data.auth.AuthErrorCode.USER_NOT_FOUND ->
            true to "Nếu email này có tài khoản, liên kết đặt lại mật khẩu đã được gửi."
          com.example.data.auth.AuthErrorCode.NETWORK -> false to "Không có kết nối mạng. Vui lòng thử lại."
          else -> {
            Log.w("AuthRepo", "Firebase password reset error: ${e.message}")
            false to "Gửi email đặt lại mật khẩu thất bại. Vui lòng thử lại sau!"
          }
        }
      }
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

    val strength = AuthSecurityManager.evaluatePasswordStrength(newPasswordInput)
    if (strength.level == PasswordStrengthLevel.VERY_WEAK || strength.level == PasswordStrengthLevel.WEAK) {
      return@withContext false to "Mật khẩu mới chưa đủ mạnh. ${strength.missingRequirements.joinToString(", ")}"
    }

    if (newPasswordInput != confirmPasswordInput) {
      return@withContext false to "Mật khẩu mới xác nhận không khớp!"
    }

    // Hash cục bộ chỉ là cache và có thể cũ (mật khẩu đổi ở máy khác). Firebase mới là nguồn thật:
    // hash không khớp KHÔNG chặn nữa, chỉ quyết định thông báo khi Firebase cũng từ chối.
    val wrongOldMessage = "Mật khẩu hiện tại không chính xác!"
    val localMatches = AuthSecurityManager.hashPassword(oldPasswordInput, currentAccount.salt) == currentAccount.passwordHash

    // Mật khẩu Firebase Auth THẬT phải đổi trước khi cập nhật cache cục bộ — nếu không, người
    // dùng nghĩ đã đổi mật khẩu nhưng lần đăng nhập kế tiếp (luôn xác thực qua Firebase, xem
    // [login]) vẫn đòi mật khẩu CŨ, trong khi PIN-fallback ([unlockWithAccountPassword]) lại
    // chấp nhận mật khẩu MỚI — gây lệch trạng thái khó hiểu và có thể tự khoá tài khoản thật.
    // reauthenticate() bằng mật khẩu cũ vừa xác minh đúng là mật khẩu Firebase hiện tại, vừa
    // thoả điều kiện "recent login" mà updatePassword() bắt buộc.
    if (auth.currentUser == null) {
      return@withContext false to if (!localMatches) wrongOldMessage
      else "Phiên đăng nhập Firebase đã hết hạn. Vui lòng đăng nhập lại rồi thử lại."
    }
    try {
      auth.reauthenticateWithPassword(oldPasswordInput)
      auth.updatePassword(newPasswordInput)
    } catch (e: com.example.data.auth.AuthException) {
      Log.w("AuthRepo", "Firebase changePassword failed: ${e.message}")
      // Chỉ INVALID_CREDENTIALS mới có nghĩa "mật khẩu cũ sai"; mất mạng hay bị giới hạn lượt thử
      // không được báo thành sai mật khẩu.
      return@withContext false to if (!localMatches && e.code == com.example.data.auth.AuthErrorCode.INVALID_CREDENTIALS) wrongOldMessage
      else authErrorMessage(e, "Đổi mật khẩu trên Firebase thất bại. Vui lòng thử lại.")
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

    val hashedPin = AuthSecurityManager.hashPinV2(pin, currentAccount.salt)
    val updated = currentAccount.copy(appPin = hashedPin, isPinEnabled = true)
    dao.updateUserAccount(updated)
    attempts.reset(currentAccount.uid)

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

  private fun lockedAccount(): UserAccountEntity? = when (val s = _authState.value) {
    is AuthState.PinLocked -> s.account
    is AuthState.Authenticated -> s.account
    else -> null
  }

  private fun unlockSucceeded(account: UserAccountEntity) {
    attempts.reset(account.uid)
    _authState.value = AuthState.Authenticated(account)
    startSyncCoordinatorSafely(account.uid)
  }

  /** Shared gate + counter for every guessable secret (PIN, account password). */
  private suspend fun guessUnlock(account: UserAccountEntity, verify: suspend (UserAccountEntity) -> UserAccountEntity?): UnlockResult {
    (attempts.gate(account.uid) as? com.example.data.auth.UnlockAttemptStore.Gate.Locked)?.let {
      return UnlockResult.Locked(it.untilMillis)
    }
    val verified = verify(account)
    if (verified != null) {
      unlockSucceeded(verified)
      return UnlockResult.Success
    }
    dao.insertSecurityLog(
      SecurityAuditLogEntity(accountEmail = account.email, action = "UNLOCK_FAILED", detail = "Sai mã mở khóa lần ${attempts.failures(account.uid) + 1}")
    )
    return when (val gate = attempts.recordFailure(account.uid)) {
      is com.example.data.auth.UnlockAttemptStore.Gate.Locked -> UnlockResult.Locked(gate.untilMillis)
      else -> UnlockResult.Wrong(attempts.remainingBeforeLock(account.uid))
    }
  }

  /**
   * Unlocks the app with the PIN. Rate-limited (persisted, escalating lock-out); old PIN formats
   * (plaintext, salted SHA-256) are upgraded to the slow v2 hash on the first successful unlock.
   */
  suspend fun unlockWithPin(pinInput: String): UnlockResult = withContext(Dispatchers.Default) {
    val account = lockedAccount() ?: return@withContext UnlockResult.NoSession
    guessUnlock(account) { acc ->
      if (pinInput.length != 4 || !pinInput.all { it.isDigit() } ||
        !AuthSecurityManager.verifyPin(pinInput, acc.salt, acc.appPin)
      ) return@guessUnlock null
      if (AuthSecurityManager.isPinV2(acc.appPin)) acc
      else acc.copy(appPin = AuthSecurityManager.hashPinV2(pinInput, acc.salt)).also { dao.updateUserAccount(it) }
    }
  }

  /** Fallback unlock with the account password (never matches provider-only accounts). Shares the PIN lock-out. */
  suspend fun unlockWithAccountPassword(passwordInput: String): UnlockResult = withContext(Dispatchers.Default) {
    val account = lockedAccount() ?: return@withContext UnlockResult.NoSession
    guessUnlock(account) { acc ->
      if (isProviderOnly(acc)) return@guessUnlock null
      val hash = AuthSecurityManager.hashPassword(passwordInput, acc.salt)
      if (AuthSecurityManager.secureEquals(hash, acc.passwordHash)) acc else null
    }
  }

  /**
   * Unlock by re-authenticating with Facebook. Not rate-limited (the secret is not guessable
   * here) and it clears the PIN lock-out, so it is also the "forgot PIN" recovery path.
   */
  suspend fun unlockWithFacebook(activity: android.app.Activity): UnlockResult {
    val account = lockedAccount() ?: return UnlockResult.NoSession
    return try {
      facebook.reauthenticate(activity, account.uid)
      dao.insertSecurityLog(SecurityAuditLogEntity(accountEmail = account.email, action = "UNLOCK_FACEBOOK", detail = "Mở khóa bằng Facebook"))
      unlockSucceeded(account)
      UnlockResult.Success
    } catch (e: com.example.data.auth.SocialAuthException) {
      UnlockResult.Error(e.reason)
    }
  }

  val isFacebookLinked: Boolean get() = facebook.isLinked()

  /** Accounts created through Facebook have no password the user knows. */
  fun isProviderOnly(account: UserAccountEntity) = account.passwordHash.startsWith(PROVIDER_HASH_PREFIX)

  /** Links Facebook to the signed-in account so it can be used to sign in / unlock / recover the PIN. */
  suspend fun linkFacebook(activity: android.app.Activity): Pair<Boolean, String> {
    val account = lockedAccount() ?: return false to "Bạn chưa đăng nhập!"
    return try {
      facebook.link(activity)
      dao.insertSecurityLog(SecurityAuditLogEntity(accountEmail = account.email, action = "FACEBOOK_LINKED", detail = "Liên kết Facebook"))
      true to "Đã liên kết Facebook với tài khoản!"
    } catch (e: com.example.data.auth.SocialAuthException) {
      false to socialErrorMessage(e.reason)
    }
  }

  /** Signs in (or creates the local cache account) through Facebook, honouring the PIN lock afterwards. */
  suspend fun loginWithFacebook(activity: android.app.Activity, rememberMe: Boolean): Pair<Boolean, String> {
    val profile = try {
      facebook.signIn(activity)
    } catch (e: com.example.data.auth.SocialAuthException) {
      return false to socialErrorMessage(e.reason)
    }
    return withContext(Dispatchers.IO) {
      val email = (profile.email ?: "fb_${profile.uid}@facebook.inlove.local").trim().lowercase()
      val token = AuthSecurityManager.generateSessionToken()
      val existing = dao.getUserAccountByUid(profile.uid) ?: dao.getUserAccountByEmail(email)
      val account = if (existing == null) {
        UserAccountEntity(
          uid = profile.uid,
          email = email,
          passwordHash = PROVIDER_HASH_PREFIX + "facebook",
          salt = AuthSecurityManager.generateSalt(),
          displayName = profile.displayName ?: email.substringBefore('@'),
          coupleCode = ProfileUtils.generateRandomCoupleCode(),
          avatarUrl = profile.photoUrl.orEmpty(),
          securityQuestion = AuthSecurityManager.SECURITY_QUESTIONS[0],
          lastLoginAt = System.currentTimeMillis(),
          sessionToken = token
        ).also { dao.insertUserAccount(it) }
      } else {
        existing.copy(uid = profile.uid, lastLoginAt = System.currentTimeMillis(), sessionToken = token).also {
          if (existing.uid != profile.uid) { dao.deleteUserAccount(existing); dao.insertUserAccount(it) } else dao.updateUserAccount(it)
        }
      }
      prefs.edit()
        .putBoolean(KEY_REMEMBER_ME, rememberMe)
        .putString(KEY_SESSION_TOKEN, if (rememberMe) token else null)
        .putString(KEY_SAVED_EMAIL, account.email)
        .apply()
      dao.insertSecurityLog(SecurityAuditLogEntity(accountEmail = account.email, action = "LOGIN_SUCCESS", detail = "Đăng nhập Facebook, UID: ${account.uid}"))
      enterScope(account.email)
      syncOnlineUserWithAccount(account)
      if (account.isPinEnabled && account.appPin.isNotEmpty()) {
        _authState.value = AuthState.PinLocked(account)
      } else {
        _authState.value = AuthState.Authenticated(account)
        startSyncCoordinatorSafely(account.uid)
      }
      true to "Đăng nhập thành công! Chào mừng ${account.displayName} 💕"
    }
  }

  fun socialErrorMessage(reason: com.example.data.auth.SocialAuthException.Reason): String =
    com.example.ui.util.socialErrorText(reason, english = false)

  /** Epoch millis until which unlock attempts are refused for the current account (0 = not locked). */
  fun lockedUntilMillis(): Long {
    val account = lockedAccount() ?: return 0L
    return (attempts.gate(account.uid) as? com.example.data.auth.UnlockAttemptStore.Gate.Locked)?.untilMillis ?: 0L
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
      auth.signOut()
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (_: Exception) {}

    try {
      hooks.onSessionEnded()
    } catch (e: Exception) {
      Log.d("AuthRepo", "Session end hook skipped (locator not initialized): ${e.message}")
    }

    enterScope(null)
    _authState.value = AuthState.Unauthenticated
  }

  /**
   * Xoá tài khoản vĩnh viễn: gửi yêu cầu cho máy chủ (callable `requestAccountDeletion`) → đăng xuất → xoá dữ liệu local.
   * Máy chủ xoá dữ liệu đám mây, tệp và danh tính Auth, tự thử lại nếu lỗi giữa chừng (xem accountDeletion.js).
   * Nếu máy chủ chưa nhận (mất mạng, cần đăng nhập lại) thì dữ liệu local còn nguyên để thử lại; gọi lại là an toàn.
   * Đăng nhập lại mà máy chủ vẫn xoá dở thì tài khoản có thể biến mất; người dùng chỉ cần đợi.
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

      // 1. Dữ liệu đám mây, khi còn đăng nhập. Có timeout để mạng chậm không treo màn hình xoá.
      val coupleCode = dao.getOnlineUserByUidSync(uid)?.coupleCode.orEmpty()
      // ponytail: dừng đồng bộ hồ sơ TRƯỚC khi xoá đám mây để không có lần ghi hồ sơ mới nào được xếp hàng sau đó.
      // Trần: một lần ghi đã giao cho SDK Firestore trước mốc này vẫn có thể tới server muộn và tạo lại users/{uid}
      // (plan §8, F16). Nếu xoá thất bại (timeout / cần đăng nhập lại) sync KHÔNG được bật lại, nó dừng tới phiên
      // kế tiếp. Nâng cấp = bật lại khi thất bại, nếu sau này cần.
      try { hooks.onAccountDeleting() } catch (e: Exception) {
        Log.w("AuthRepo", "Account-deleting hook failed: ${e.javaClass.simpleName}")
      }
      // Máy chủ chỉ nhận yêu cầu khi đăng nhập gần đây; nếu không thì dừng ở đây, local còn nguyên.
      try {
        withTimeout(CLOUD_CLEANUP_TIMEOUT_MS) { remoteCleanup.deleteCloudData(uid, coupleCode) }
      } catch (_: TimeoutCancellationException) {
        return@withContext Result.failure(
          IllegalStateException("Mạng đang chậm, chưa gửi được yêu cầu xoá lên máy chủ. Tài khoản vẫn còn nguyên, vui lòng thử lại.")
        )
      } catch (_: RecentLoginRequiredException) {
        return@withContext Result.failure(
          IllegalStateException("Vì lý do bảo mật, vui lòng đăng xuất và đăng nhập lại gần đây trước khi xoá tài khoản vĩnh viễn.")
        )
      }

      // 2. Máy chủ đã nhận việc (tự thử lại tới khi xong); client chỉ kết thúc phiên Auth.
      remoteCleanup.deleteAuthIdentity()

      // 3. Dừng đồng bộ trước khi xoá local, để listener không ghi lại dữ liệu vừa xoá.
      try { hooks.onSessionEnded() } catch (_: Exception) {}

      // 4. Delete Local Room records, online cache, and memories.
      // Đọc ảnh đại diện local trước khi xoá hàng online_users (xoá file ở bước 5).
      val avatarUrl = dao.getOnlineUserByUidSync(uid)?.avatarUrl.orEmpty()
      // Clear the in-memory current user first, or a concurrent writer can re-insert the online-user row.
      onlineRepo.setCurrentUserId("")
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
      // Ứng viên xoá file: gồm cả hàng đang ẩn do huỷ ghép đôi, lấy trước khi xoá hàng.
      val mediaValues = dao.getAllSharedMemories().first().flatMap { listOf(it.photoUri, it.videoUri) }
      dao.clearAllSharedMemories()
      dao.clearCoupleProfile()

      // 5. Clear all preferences and reset session
      prefs.edit().clear().apply()
      profileSyncPrefs.edit { remove(com.example.data.sync.ProfileSync.syncedKey(uid)) }
      vault?.switchTo(com.example.data.db.AccountDataVault.GUEST, discardCurrent = true)
      // Xoá file sau khi vault khôi phục dữ liệu khách: file mà hàng khách còn dùng phải giữ lại.
      MediaPath.deleteUnreferenced(
        mediaFilesDir, mediaValues,
        dao.getAllSharedMemories().first().flatMap { listOf(it.photoUri, it.videoUri) }
      )
      // deleteUnreferenced chỉ xoá file của ứng viên nên file manifest sao lưu và avatar xoá riêng.
      runCatching { java.io.File(java.io.File(mediaFilesDir, MediaPath.DIR), MediaPath.BACKUP_MANIFEST).delete() }
      runCatching { ProfileUtils.staleLocalAvatar(avatarUrl, "", java.io.File(mediaFilesDir, "avatars"))?.delete() }
      _authState.value = AuthState.Unauthenticated
      Result.success(Unit)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
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
        bio = com.example.ui.util.WELCOME_BIO,
        isProfileSetup = true,
        isCurrentUser = true,
        role = account.role,
        subscriptionTier = account.subscriptionTier,
        isVip = account.isVip
      )
      dao.insertOnlineUser(newOnlineUser)
    } else {
      // ponytail: account photo seeds new rows only, so a picked avatar survives sign-in.
      // Ceiling: a row created with a blank photo never gets one later. Upgrade: a user-picked flag.
      dao.updateOnlineUser(
        existingOnlineUser.copy(
          // A name the user set in the profile dialog wins over the login account's name.
          displayName = if (existingOnlineUser.effectiveDisplayName.isNotBlank()) existingOnlineUser.displayName else account.displayName,
          email = account.email,
          coupleCode = account.coupleCode,
          isCurrentUser = true,
          role = account.role,
          subscriptionTier = account.subscriptionTier,
          isVip = account.isVip
        )
      )
    }
    onlineRepo.setCurrentUserId(account.uid)
    publishCoupleCodeInBackground(account.uid)
  }

  /**
   * Đăng ký mã ghép đôi lên Firestore để người khác tìm được bằng mã (lời mời theo mã cần bản ghi này).
   * Chạy nền, không chặn đăng nhập; lỗi chỉ ghi log.
   */
  private fun publishCoupleCodeInBackground(uid: String) {
    scope.launch {
      try {
        // ponytail: một lần get mỗi lần đăng nhập; mã đã có thì không ghi lại. Chưa retry khi offline.
        if (auth.currentUser?.uid != uid) return@launch
        val code = dao.getOnlineUserByUidSync(uid)?.coupleCode?.takeIf { it.isNotBlank() } ?: return@launch
        onlineRepo.publishCoupleCode(uid, code).onFailure { Log.w("AuthRepo", "publish couple code failed: ${it.message}") }
      } catch (e: Exception) {
        Log.w("AuthRepo", "publish couple code skipped: ${e.message}")
      }
    }
  }
}
