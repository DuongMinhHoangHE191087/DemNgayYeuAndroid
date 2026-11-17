package com.example.data.auth

import android.app.Activity
import com.example.InLoveApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSocialAuthGatewayTest {

  // Firebase is never touched: every lambda that would reach it fails the test.
  private fun gateway() = AndroidSocialAuthGateway(
    auth = { error("FirebaseAuth must not be used") },
    activity = { null },
    currentUser = { null }
  )

  private fun reasonOf(block: suspend () -> Unit): SocialAuthException.Reason =
    (runCatching { runBlocking { block() } }.exceptionOrNull() as SocialAuthException).reason

  @Test fun apple_signIn_throwsNotConfigured() {
    assertEquals(SocialAuthException.Reason.NOT_CONFIGURED, reasonOf { gateway().signIn(SocialProvider.APPLE) })
  }

  @Test fun apple_link_reauthenticate_revoke_throwNotConfigured() {
    val g = gateway()
    assertEquals(SocialAuthException.Reason.NOT_CONFIGURED, reasonOf { g.link(SocialProvider.APPLE) })
    assertEquals(SocialAuthException.Reason.NOT_CONFIGURED, reasonOf { g.reauthenticate(SocialProvider.APPLE, "uid") })
    assertEquals(SocialAuthException.Reason.NOT_CONFIGURED, reasonOf { g.revokeProviderAccess(SocialProvider.APPLE) })
  }

  @Test fun facebook_signIn_withoutActivity_throwsNoSession() {
    assertEquals(SocialAuthException.Reason.NO_SESSION, reasonOf { gateway().signIn(SocialProvider.FACEBOOK) })
  }

  // Có Activity nhưng chưa đăng nhập: reauthenticate / link không được chạm Firebase mà phải báo NO_SESSION.
  @Test fun facebook_reauthenticate_and_link_withoutUser_throwNoSession() {
    val activity = Robolectric.buildActivity(Activity::class.java).get()
    val g = AndroidSocialAuthGateway(auth = { error("FirebaseAuth must not be used") }, activity = { activity }, currentUser = { null })
    assertEquals(SocialAuthException.Reason.NO_SESSION, reasonOf { g.reauthenticate(SocialProvider.FACEBOOK, "uid") })
    assertEquals(SocialAuthException.Reason.NO_SESSION, reasonOf { g.link(SocialProvider.FACEBOOK) })
  }

  @Test fun isLinked_apple_falseWhenNoUser() {
    assertFalse(gateway().isLinked(SocialProvider.APPLE))
    assertFalse(gateway().isLinked(SocialProvider.FACEBOOK))
  }

  @Test fun providerIds() {
    assertEquals("facebook.com", SocialProvider.FACEBOOK.firebaseProviderId)
    assertEquals("apple.com", SocialProvider.APPLE.firebaseProviderId)
  }

  // Robolectric builds InLoveApplication (which takes the isRunningInTest early exit) and runs onCreate.
  @Config(application = InLoveApplication::class)
  @Test fun application_registersCurrentActivityProvider_evenInTestMode() {
    assertTrue(RuntimeEnvironment.getApplication() is InLoveApplication)
    assertTrue(CurrentActivityProvider.isRegistered)
  }
}
