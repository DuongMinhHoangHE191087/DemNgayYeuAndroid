package com.example.data.auth.fakes

import com.example.data.auth.SocialAuthGateway
import com.example.data.auth.SocialProfile
import com.example.data.auth.SocialProvider

/**
 * Records every call as "method:PROVIDER". All calls succeed unless a failure is queued in
 * [failures] under "method:PROVIDER" (consumed once); [linked] drives isLinked and is updated by link().
 */
class FakeSocialAuthGateway : SocialAuthGateway {
  val calls = mutableListOf<String>()
  val failures = mutableMapOf<String, Throwable>()
  val linked = mutableSetOf<SocialProvider>()
  var profile = SocialProfile("fake-uid", "fake@example.com", "Fake", null)

  private fun record(method: String, provider: SocialProvider) {
    val key = "$method:${provider.name}"
    calls += key
    failures.remove(key)?.let { throw it }
  }

  override suspend fun signIn(provider: SocialProvider): SocialProfile {
    record("signIn", provider)
    return profile
  }

  override suspend fun reauthenticate(provider: SocialProvider, expectedUid: String) =
    record("reauthenticate", provider)

  override suspend fun link(provider: SocialProvider): SocialProfile {
    record("link", provider)
    linked += provider
    return profile
  }

  override fun isLinked(provider: SocialProvider): Boolean = provider in linked

  override suspend fun revokeProviderAccess(provider: SocialProvider) = record("revokeProviderAccess", provider)
}
