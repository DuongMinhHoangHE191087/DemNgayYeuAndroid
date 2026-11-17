package com.example.data.auth

// Platform-neutral email/password auth contract. Pure Kotlin: no android/firebase imports,
// so the iOS side can implement the same surface.

data class AuthUser(
  val uid: String,
  val email: String?,
  val displayName: String?,
  val emailVerified: Boolean,
  val providerIds: List<String>,
)

enum class AuthErrorCode {
  INVALID_CREDENTIALS,
  USER_NOT_FOUND,
  EMAIL_IN_USE,
  WEAK_PASSWORD,
  INVALID_EMAIL,
  TOO_MANY_REQUESTS,
  REQUIRES_RECENT_LOGIN,
  USER_DISABLED,
  NETWORK,
  UNKNOWN,
}

class AuthException(
  val code: AuthErrorCode,
  message: String? = null,
  cause: Throwable? = null,
) : Exception(message ?: code.name, cause)

interface AuthGateway {
  val currentUser: AuthUser?

  suspend fun signInWithEmail(email: String, password: String): AuthUser
  suspend fun createUserWithEmail(email: String, password: String): AuthUser
  suspend fun sendEmailVerification()
  suspend fun sendPasswordResetEmail(email: String)
  suspend fun reauthenticateWithPassword(password: String)
  suspend fun updatePassword(newPassword: String)

  /** Re-reads the signed-in user from the server; null when nobody is signed in. */
  suspend fun reloadCurrentUser(): AuthUser?

  /**
   * Forces a fresh ID token for the current user (no cache), so server-side `auth_time`
   * checks see a just-completed re-login. Throws USER_NOT_FOUND when signed out.
   */
  suspend fun refreshIdToken()

  suspend fun signOut()
}

/** Callbacks fired around the signed-in session lifecycle. */
interface SessionHooks {
  fun onSessionStarted(uid: String)
  fun onSessionEnded()

  /** Sắp xoá tài khoản: dừng mọi thứ có thể tạo lại dữ liệu trên server (mặc định không làm gì). */
  fun onAccountDeleting() {}
}
