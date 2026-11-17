package com.example.platform

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPermissionServiceTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val service = AndroidPermissionService(context)

  private fun setNotificationsEnabled(enabled: Boolean) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    shadowOf(manager).setNotificationsEnabled(enabled)
  }

  @Test
  fun statusIsGrantedWhenNotificationsAreEnabled() {
    setNotificationsEnabled(true)
    assertEquals(NotificationPermission.Granted, service.notificationStatus())
  }

  @Test
  @Config(sdk = [32])
  fun statusIsDeniedBelowApi33WhenDisabled() {
    setNotificationsEnabled(false)
    assertEquals(NotificationPermission.Denied, service.notificationStatus())
  }

  @Test
  fun statusIsNotDeterminedOnApi33BeforeFirstRequest() {
    setNotificationsEnabled(false)
    assertEquals(NotificationPermission.NotDetermined, service.notificationStatus())
  }

  @Test
  fun statusIsDeniedOnApi33AfterARequestWasRefused() {
    setNotificationsEnabled(false)
    service.attachLauncher { onResult -> onResult(false) }
    service.requestNotifications {}
    assertEquals(NotificationPermission.Denied, service.notificationStatus())
  }

  @Test
  @Config(sdk = [32])
  fun requestBelowApi33ReturnsCurrentStatusWithoutLauncher() {
    setNotificationsEnabled(false)
    var launched = false
    service.attachLauncher { launched = true }
    var result: NotificationPermission? = null
    service.requestNotifications { result = it }
    assertEquals(NotificationPermission.Denied, result)
    assertTrue("launcher must not be used below API 33", !launched)
  }

  @Test
  fun requestOnApi33DelegatesToLauncherAndMapsResult() {
    setNotificationsEnabled(false)
    var deliver: ((Boolean) -> Unit)? = null
    service.attachLauncher { deliver = it }
    val results = mutableListOf<NotificationPermission>()
    service.requestNotifications { results += it }
    assertNotNull("launcher must be invoked on API 33+", deliver)
    assertTrue("no result before the system dialog answers", results.isEmpty())
    deliver!!(true)
    service.requestNotifications { results += it }
    deliver(false)
    assertEquals(listOf(NotificationPermission.Granted, NotificationPermission.Denied), results)
  }

  @Test
  @Config(sdk = [33])
  fun requestOnExactlyApi33UsesTheLauncher() {
    setNotificationsEnabled(false)
    var launched = false
    service.attachLauncher { launched = true }
    service.requestNotifications {}
    assertTrue("API 33 is the first version with the runtime permission", launched)
  }

  @Test
  fun requestOnApi33WithoutLauncherReturnsCurrentStatus() {
    setNotificationsEnabled(false)
    var result: NotificationPermission? = null
    service.requestNotifications { result = it }
    assertEquals(NotificationPermission.NotDetermined, result)
  }

  @Test
  fun openSettingsStartsAppNotificationSettingsIntent() {
    service.openAppSettings()
    val intent: Intent = shadowOf(context as android.app.Application).nextStartedActivity
    assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
    assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
  }

  @Test
  @Config(sdk = [26])
  fun openSettingsOnExactlyApi26UsesAppNotificationSettings() {
    service.openAppSettings()
    val intent: Intent = shadowOf(context as android.app.Application).nextStartedActivity
    assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
  }

  @Test
  @Config(sdk = [25])
  fun openSettingsBelowApi26FallsBackToApplicationDetails() {
    service.openAppSettings()
    val intent: Intent = shadowOf(context as android.app.Application).nextStartedActivity
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals("package:" + context.packageName, intent.dataString)
  }
}
