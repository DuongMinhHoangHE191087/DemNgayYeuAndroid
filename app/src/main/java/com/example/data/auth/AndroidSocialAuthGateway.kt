package com.example.data.auth

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Firebase's generic OAuth web flow (Custom Tab) per [SocialProvider.firebaseProviderId], so
 * provider app secrets live only in the Firebase console. Default deny: [SocialProvider.APPLE]
 * is rejected with NOT_CONFIGURED until it is set up. Tokens are never logged.
 */
class AndroidSocialAuthGateway(
  private val auth: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
  private val activity: () -> Activity? = CurrentActivityProvider::current,
  private val currentUser: () -> FirebaseUser? = { auth().currentUser }
) : SocialAuthGateway {

  override suspend fun signIn(provider: SocialProvider): SocialProfile = guard(provider) { act ->
    val user = auth().startActivityForSignInWithProvider(act, oauth(provider)).await().user
      ?: throw SocialAuthException(SocialAuthException.Reason.OTHER)
    user.toProfile(provider)
  }

  override suspend fun reauthenticate(provider: SocialProvider, expectedUid: String) = guard(provider) { act ->
    val current = currentUser() ?: throw SocialAuthException(SocialAuthException.Reason.NO_SESSION)
    if (current.uid != expectedUid) throw SocialAuthException(SocialAuthException.Reason.MISMATCH)
    val result = current.startActivityForReauthenticateWithProvider(act, oauth(provider)).await()
    if (result.user?.uid != expectedUid) throw SocialAuthException(SocialAuthException.Reason.MISMATCH)
  }

  override suspend fun link(provider: SocialProvider): SocialProfile = guard(provider) { act ->
    val current = currentUser() ?: throw SocialAuthException(SocialAuthException.Reason.NO_SESSION)
    current.startActivityForLinkWithProvider(act, oauth(provider)).await().user?.toProfile(provider)
      ?: throw SocialAuthException(SocialAuthException.Reason.OTHER)
  }

  override fun isLinked(provider: SocialProvider): Boolean =
    currentUser()?.providerData?.any { it.providerId == provider.firebaseProviderId } == true

  // ponytail: the Firebase web flow hands the app no provider token, so there is nothing to revoke
  // client-side for Facebook; the grant is removed by the user at the provider. Revisit with a real SDK.
  override suspend fun revokeProviderAccess(provider: SocialProvider) {
    requireConfigured(provider)
  }

  private fun requireConfigured(provider: SocialProvider) {
    if (provider == SocialProvider.APPLE) throw SocialAuthException(SocialAuthException.Reason.NOT_CONFIGURED)
  }

  private fun oauth(provider: SocialProvider): OAuthProvider =
    OAuthProvider.newBuilder(provider.firebaseProviderId)
      .setScopes(listOf("email", "public_profile"))
      .build()

  private fun FirebaseUser.toProfile(provider: SocialProvider): SocialProfile {
    val p = providerData.firstOrNull { it.providerId == provider.firebaseProviderId }
    return SocialProfile(
      uid = uid,
      email = (email ?: p?.email)?.takeIf { it.isNotBlank() },
      displayName = (displayName ?: p?.displayName)?.takeIf { it.isNotBlank() },
      photoUrl = (photoUrl ?: p?.photoUrl)?.toString()
    )
  }

  /** Gate (provider configured, Activity available) then run [block], mapping failures to [SocialAuthException]. */
  private suspend fun <T> guard(provider: SocialProvider, block: suspend (Activity) -> T): T = try {
    requireConfigured(provider)
    block(activity() ?: throw SocialAuthException(SocialAuthException.Reason.NO_SESSION))
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
}
