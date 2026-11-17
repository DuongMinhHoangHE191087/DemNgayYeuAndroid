package com.example.platform

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Simulates a ROM without the first `failFirst` settings screens: those launches throw.
private class ThrowingOnceContext(base: Context, private val failFirst: Int) : ContextWrapper(base) {
  val started = mutableListOf<Intent>()

  override fun getApplicationContext(): Context = this

  override fun startActivity(intent: Intent) {
    started += intent
    if (started.size <= failFirst) throw ActivityNotFoundException()
    super.startActivity(intent)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPermissionServiceFallbackTest {
  private val base = ApplicationProvider.getApplicationContext<Application>()

  @Test
  fun openSettingsFallsBackToAppDetailsWhenNotificationSettingsIsMissing() {
    val context = ThrowingOnceContext(base, failFirst = 1)
    AndroidPermissionService(context).openAppSettings()
    assertEquals(2, context.started.size)
    assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, context.started[0].action)
    val details = context.started[1]
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, details.action)
    assertEquals("package:" + base.packageName, details.dataString)
    assertTrue("fallback must still start in a new task", (details.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
  }

  @Test
  fun openSettingsDoesNotCrashWhenNoSettingsScreenExists() {
    val context = ThrowingOnceContext(base, failFirst = 2)
    AndroidPermissionService(context).openAppSettings()
    assertEquals(2, context.started.size)
  }
}
