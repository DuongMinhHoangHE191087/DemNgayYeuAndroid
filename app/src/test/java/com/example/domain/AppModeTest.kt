package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModeTest {
  @Test fun resolvesModes() {
    assertEquals(AccountMode.GUEST, AppMode.resolve(signedIn = false, paired = true))
    assertEquals(AccountMode.SIGNED_IN, AppMode.resolve(true, false))
    assertEquals(AccountMode.PAIRED, AppMode.resolve(true, true))
  }

  @Test fun offlineMessageOnlyWhenSignedIn() {
    assertTrue(AppMode.describe(AccountMode.PAIRED, online = false, english = true).contains("Offline"))
    assertEquals("Guest — data stays on this device", AppMode.describe(AccountMode.GUEST, online = false, english = true))
  }
}
