package com.example.data.auth.fakes

import com.example.data.auth.AuthGateway
import com.example.data.auth.AuthUser

/**
 * Records every call by name and replays queued results (a failure is a Result.failure
 * holding an AuthException). With an empty queue, calls succeed with a default value.
 *
 * Two clocks model the delete-account `auth_time` check: [cachedAuthTimeSec] is what the
 * cached ID token carries, [serverAuthTimeSec] is what the server would issue now.
 */
class FakeAuthGateway : AuthGateway {
  override var currentUser: AuthUser? = null

  val calls = mutableListOf<String>()
  val queued = ArrayDeque<Result<Any?>>()

  var cachedAuthTimeSec: Long = 0
  var serverAuthTimeSec: Long = 0

  @Suppress("UNCHECKED_CAST")
  private fun <T> record(call: String, default: T): T {
    calls += call
    return if (queued.isEmpty()) default else queued.removeFirst().getOrThrow() as T
  }

  private fun fallbackUser() = currentUser ?: AuthUser("fake-uid", "fake@example.com", null, false, listOf("password"))

  override suspend fun signInWithEmail(email: String, password: String): AuthUser =
    record("signInWithEmail", fallbackUser()).also { currentUser = it }

  override suspend fun createUserWithEmail(email: String, password: String): AuthUser =
    record("createUserWithEmail", fallbackUser()).also { currentUser = it }

  override suspend fun sendEmailVerification() = record("sendEmailVerification", Unit)

  override suspend fun sendPasswordResetEmail(email: String) = record("sendPasswordResetEmail", Unit)

  override suspend fun reauthenticateWithPassword(password: String) =
    record("reauthenticateWithPassword", Unit)

  override suspend fun updatePassword(newPassword: String) = record("updatePassword", Unit)

  override suspend fun reloadCurrentUser(): AuthUser? =
    record("reloadCurrentUser", currentUser).also { currentUser = it }

  override suspend fun refreshIdToken() {
    record("refreshIdToken", Unit)
    cachedAuthTimeSec = serverAuthTimeSec
  }

  override suspend fun signOut() {
    record("signOut", Unit)
    currentUser = null
  }
}
