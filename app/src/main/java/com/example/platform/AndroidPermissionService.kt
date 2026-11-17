package com.example.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

class AndroidPermissionService(context: Context) : PermissionService {
  private val appContext = context.applicationContext
  private var launcher: (((Boolean) -> Unit) -> Unit)? = null

  // MainActivity owns the ActivityResultLauncher; it passes a function that launches the system
  // dialog and calls back with the granted flag. Pass null on destroy so the Activity is not leaked.
  fun attachLauncher(launcher: (((Boolean) -> Unit) -> Unit)?) {
    this.launcher = launcher
  }

  override fun notificationStatus(): NotificationPermission = when {
    NotificationManagerCompat.from(appContext).areNotificationsEnabled() -> NotificationPermission.Granted
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !prefs().getBoolean(KEY_REQUESTED, false) ->
      NotificationPermission.NotDetermined
    else -> NotificationPermission.Denied
  }

  override fun requestNotifications(onResult: (NotificationPermission) -> Unit) {
    val launch = launcher
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || launch == null) {
      onResult(notificationStatus())
      return
    }
    prefs().edit().putBoolean(KEY_REQUESTED, true).apply()
    launch { granted -> onResult(if (granted) NotificationPermission.Granted else NotificationPermission.Denied) }
  }

  override fun openAppSettings() {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", appContext.packageName, null))
    // ACTION_APP_NOTIFICATION_SETTINGS only exists from API 26, and some ROMs lack it: fall back to the app-details page.
    val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName), details)
    } else {
      listOf(details)
    }
    for (candidate in candidates) {
      try {
        appContext.startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
      } catch (_: ActivityNotFoundException) {
        // try the next screen; a device without any settings app must not crash the caller
      }
    }
  }

  private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private companion object {
    const val PREFS = "inlove_notifications"
    const val KEY_REQUESTED = "post_notifications_requested"
  }
}
