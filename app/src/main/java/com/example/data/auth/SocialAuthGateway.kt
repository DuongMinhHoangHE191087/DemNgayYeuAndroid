package com.example.data.auth

/** Identity returned by a successful social sign-in (through Firebase Auth). */
data class SocialProfile(
  val uid: String,
  val email: String?,
  val displayName: String?,
  val photoUrl: String?
)

/** Thrown for failures the UI can show as-is; [reason] is a stable code for localisation. */
class SocialAuthException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
  enum class Reason { CANCELLED, NETWORK, ACCOUNT_EXISTS, NOT_CONFIGURED, MISMATCH, NO_SESSION, OTHER }
}

/** Social identity providers, keyed by their Firebase provider id. */
enum class SocialProvider(val firebaseProviderId: String) {
  FACEBOOK("facebook.com"),
  APPLE("apple.com")
}

/** Social sign-in/link/re-auth for any [SocialProvider]; implementations never log tokens. */
interface SocialAuthGateway {
  suspend fun signIn(provider: SocialProvider): SocialProfile
  /** Re-authenticates the CURRENT Firebase user; fails with MISMATCH if another account answers. */
  suspend fun reauthenticate(provider: SocialProvider, expectedUid: String)
  suspend fun link(provider: SocialProvider): SocialProfile
  fun isLinked(provider: SocialProvider): Boolean
  /** Best-effort removal of the app's grant at the provider (used when deleting the account). */
  suspend fun revokeProviderAccess(provider: SocialProvider)
}
