package com.example.data.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.tasks.await

/** Identity returned by a successful Facebook sign-in (through Firebase Auth). */
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

/**
 * Facebook login without the Facebook SDK: Firebase's generic OAuth provider runs the web flow in
 * a Custom Tab, so the Facebook App ID/secret live only in the Firebase console (never in the APK).
 */
interface FacebookAuthGateway {
  suspend fun signIn(activity: Activity): SocialProfile
  /** Re-authenticates the CURRENT Firebase user; fails with MISMATCH if another Facebook user answers. */
  suspend fun reauthenticate(activity: Activity, expectedUid: String)
  suspend fun link(activity: Activity): SocialProfile
  fun isLinked(): Boolean
}

class FirebaseFacebookGateway(
  private val auth: () -> FirebaseAuth = { FirebaseAuth.getInstance() }
) : FacebookAuthGateway {

  private fun provider() = OAuthProvider.newBuilder(PROVIDER_ID)
    .setScopes(listOf("email", "public_profile"))
    .build()

  override suspend fun signIn(activity: Activity): SocialProfile = guard {
    val user = auth().startActivityForSignInWithProvider(activity, provider()).await().user
      ?: throw SocialAuthException(SocialAuthException.Reason.OTHER)
    user.toProfile()
  }

  override suspend fun reauthenticate(activity: Activity, expectedUid: String) = guard {
    val current = auth().currentUser ?: throw SocialAuthException(SocialAuthException.Reason.NO_SESSION)
    if (current.uid != expectedUid) throw SocialAuthException(SocialAuthException.Reason.MISMATCH)
    val result = current.startActivityForReauthenticateWithProvider(activity, provider()).await()
    if (result.user?.uid != expectedUid) throw SocialAuthException(SocialAuthException.Reason.MISMATCH)
  }

  override suspend fun link(activity: Activity): SocialProfile = guard {
    val current = auth().currentUser ?: throw SocialAuthException(SocialAuthException.Reason.NO_SESSION)
    current.startActivityForLinkWithProvider(activity, provider()).await().user?.toProfile()
      ?: throw SocialAuthException(SocialAuthException.Reason.OTHER)
  }

  override fun isLinked(): Boolean =
    auth().currentUser?.providerData?.any { it.providerId == PROVIDER_ID } == true

  private fun com.google.firebase.auth.FirebaseUser.toProfile(): SocialProfile {
    val fb = providerData.firstOrNull { it.providerId == PROVIDER_ID }
    return SocialProfile(
      uid = uid,
      email = (email ?: fb?.email)?.takeIf { it.isNotBlank() },
      displayName = (displayName ?: fb?.displayName)?.takeIf { it.isNotBlank() },
      photoUrl = (photoUrl ?: fb?.photoUrl)?.toString()
    )
  }

  private suspend fun <T> guard(block: suspend () -> T): T = try {
    block()
  } catch (e: SocialAuthException) {
    throw e
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
    throw SocialAuthException(SocialAuthException.Reason.ACCOUNT_EXISTS, e)
  } catch (e: com.google.firebase.FirebaseNetworkException) {
    throw SocialAuthException(SocialAuthException.Reason.NETWORK, e)
  } catch (e: com.google.firebase.auth.FirebaseAuthException) {
    throw SocialAuthException(
      when (e.errorCode) {
        "ERROR_WEB_CONTEXT_CANCELED" -> SocialAuthException.Reason.CANCELLED
        "ERROR_OPERATION_NOT_ALLOWED", "ERROR_INVALID_CREDENTIAL", "ERROR_APP_NOT_AUTHORIZED" -> SocialAuthException.Reason.NOT_CONFIGURED
        else -> SocialAuthException.Reason.OTHER
      },
      e
    )
  } catch (e: Exception) {
    throw SocialAuthException(SocialAuthException.Reason.OTHER, e)
  }

  companion object {
    const val PROVIDER_ID = "facebook.com"
  }
}
