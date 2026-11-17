package com.example.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Maps a Firebase failure to the platform-neutral [AuthErrorCode]. Pure, no Firebase init needed. */
internal fun toAuthErrorCode(e: Throwable): AuthErrorCode = when (e) {
  is FirebaseNetworkException -> AuthErrorCode.NETWORK
  is FirebaseTooManyRequestsException -> AuthErrorCode.TOO_MANY_REQUESTS
  is FirebaseAuthException -> when (e.errorCode) {
    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> AuthErrorCode.INVALID_CREDENTIALS
    "ERROR_USER_NOT_FOUND" -> AuthErrorCode.USER_NOT_FOUND
    "ERROR_EMAIL_ALREADY_IN_USE" -> AuthErrorCode.EMAIL_IN_USE
    "ERROR_WEAK_PASSWORD" -> AuthErrorCode.WEAK_PASSWORD
    "ERROR_INVALID_EMAIL" -> AuthErrorCode.INVALID_EMAIL
    "ERROR_TOO_MANY_REQUESTS" -> AuthErrorCode.TOO_MANY_REQUESTS
    "ERROR_REQUIRES_RECENT_LOGIN" -> AuthErrorCode.REQUIRES_RECENT_LOGIN
    "ERROR_USER_DISABLED" -> AuthErrorCode.USER_DISABLED
    else -> AuthErrorCode.UNKNOWN
  }
  else -> AuthErrorCode.UNKNOWN
}

private fun FirebaseUser.toAuthUser() = AuthUser(
  uid = uid,
  email = email,
  displayName = displayName,
  emailVerified = isEmailVerified,
  providerIds = providerData.map { it.providerId },
)

class AndroidAuthGateway : AuthGateway {
  private val auth get() = FirebaseAuth.getInstance()

  override val currentUser: AuthUser? get() = auth.currentUser?.toAuthUser()

  override suspend fun signInWithEmail(email: String, password: String): AuthUser = guardAuth {
    requireUser(auth.signInWithEmailAndPassword(email, password).await().user)
  }

  override suspend fun createUserWithEmail(email: String, password: String): AuthUser = guardAuth {
    requireUser(auth.createUserWithEmailAndPassword(email, password).await().user)
  }

  override suspend fun sendEmailVerification() = guardAuth {
    signedIn().sendEmailVerification().await()
    Unit
  }

  override suspend fun sendPasswordResetEmail(email: String) = guardAuth {
    auth.sendPasswordResetEmail(email).await()
    Unit
  }

  override suspend fun reauthenticateWithPassword(password: String) = guardAuth {
    val user = signedIn()
    val email = user.email ?: throw AuthException(AuthErrorCode.INVALID_EMAIL)
    user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    Unit
  }

  override suspend fun updatePassword(newPassword: String) = guardAuth {
    signedIn().updatePassword(newPassword).await()
    Unit
  }

  override suspend fun reloadCurrentUser(): AuthUser? = guardAuth {
    auth.currentUser?.let {
      it.reload().await()
      // Re-read: reload() mutates the cached user in place.
      auth.currentUser?.toAuthUser()
    }
  }

  override suspend fun refreshIdToken() = guardAuth {
    signedIn().getIdToken(true).await()
    Unit
  }

  override suspend fun signOut() {
    auth.signOut()
  }

  private fun signedIn(): FirebaseUser =
    auth.currentUser ?: throw AuthException(AuthErrorCode.USER_NOT_FOUND)

  private fun requireUser(user: FirebaseUser?): AuthUser =
    (user ?: throw AuthException(AuthErrorCode.UNKNOWN)).toAuthUser()
}

/** Runs [block], turning any Firebase failure into [AuthException]; cancellation and [AuthException] pass through. */
internal suspend fun <T> guardAuth(block: suspend () -> T): T = try {
  block()
} catch (e: CancellationException) {
  throw e
} catch (e: AuthException) {
  throw e
} catch (e: Exception) {
  throw AuthException(toAuthErrorCode(e), e.message, e)
}
