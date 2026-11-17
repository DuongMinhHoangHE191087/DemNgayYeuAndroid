package com.example.data.auth

import com.example.data.auth.fakes.FakeAuthGateway
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric only because FirebaseException's constructor calls android.text.TextUtils;
// the mapping function itself needs no Android/Firebase init.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAuthGatewayMappingTest {
  private fun codeOf(firebaseCode: String) =
    toAuthErrorCode(FirebaseAuthException(firebaseCode, "msg"))

  @Test fun mapsWrongPasswordToInvalidCredentials() =
    assertEquals(AuthErrorCode.INVALID_CREDENTIALS, codeOf("ERROR_WRONG_PASSWORD"))

  @Test fun mapsInvalidCredentialToInvalidCredentials() =
    assertEquals(AuthErrorCode.INVALID_CREDENTIALS, codeOf("ERROR_INVALID_CREDENTIAL"))

  @Test fun mapsUserNotFound() =
    assertEquals(AuthErrorCode.USER_NOT_FOUND, codeOf("ERROR_USER_NOT_FOUND"))

  @Test fun mapsEmailInUse() =
    assertEquals(AuthErrorCode.EMAIL_IN_USE, codeOf("ERROR_EMAIL_ALREADY_IN_USE"))

  @Test fun mapsWeakPassword() =
    assertEquals(AuthErrorCode.WEAK_PASSWORD, codeOf("ERROR_WEAK_PASSWORD"))

  @Test fun mapsInvalidEmail() =
    assertEquals(AuthErrorCode.INVALID_EMAIL, codeOf("ERROR_INVALID_EMAIL"))

  @Test fun mapsTooManyRequests() =
    assertEquals(AuthErrorCode.TOO_MANY_REQUESTS, codeOf("ERROR_TOO_MANY_REQUESTS"))

  @Test fun mapsRequiresRecentLogin() =
    assertEquals(AuthErrorCode.REQUIRES_RECENT_LOGIN, codeOf("ERROR_REQUIRES_RECENT_LOGIN"))

  @Test fun mapsUserDisabled() =
    assertEquals(AuthErrorCode.USER_DISABLED, codeOf("ERROR_USER_DISABLED"))

  @Test fun mapsNetworkException() =
    assertEquals(AuthErrorCode.NETWORK, toAuthErrorCode(FirebaseNetworkException("offline")))

  @Test fun mapsTooManyRequestsException() =
    assertEquals(AuthErrorCode.TOO_MANY_REQUESTS, toAuthErrorCode(FirebaseTooManyRequestsException("slow down")))

  @Test fun unknownCodeMapsToUnknown() {
    assertEquals(AuthErrorCode.UNKNOWN, codeOf("ERROR_SOMETHING_NEW"))
    assertEquals(AuthErrorCode.UNKNOWN, toAuthErrorCode(IllegalStateException("boom")))
  }

  @Test fun guardRethrowsCancellationUntouched() {
    val cancel = CancellationException("cancelled")
    val thrown = runCatching { runBlocking { guardAuth<Unit> { throw cancel } } }.exceptionOrNull()
    assertSame(cancel, thrown)
  }

  @Test fun guardPassesAuthExceptionThrough() {
    val original = AuthException(AuthErrorCode.EMAIL_IN_USE)
    val thrown = runCatching { runBlocking { guardAuth<Unit> { throw original } } }.exceptionOrNull()
    assertSame(original, thrown)
  }

  @Test fun guardWrapsOtherFailuresWithMappedCodeMessageAndCause() {
    val cause = FirebaseNetworkException("offline")
    val thrown = runCatching { runBlocking { guardAuth<Unit> { throw cause } } }.exceptionOrNull()
    assertTrue(thrown is AuthException)
    thrown as AuthException
    assertEquals(AuthErrorCode.NETWORK, thrown.code)
    assertEquals("offline", thrown.message)
    assertSame(cause, thrown.cause)
  }

  @Test fun guardReturnsBlockResult() = runBlocking {
    assertEquals(42, guardAuth { 42 })
  }

  @Test fun fakeRefreshIdTokenCopiesServerAuthTimeIntoCache() = runBlocking {
    val fake = FakeAuthGateway().apply {
      cachedAuthTimeSec = 100
      serverAuthTimeSec = 500
    }
    fake.refreshIdToken()
    assertEquals(500L, fake.cachedAuthTimeSec)
    assertEquals(1, fake.calls.count { it == "refreshIdToken" })
  }
}
