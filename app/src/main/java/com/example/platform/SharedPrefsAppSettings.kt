package com.example.platform

import android.content.Context
import com.example.ui.settings.AppSettings
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocaleManager

// Keeps the SharedPreferences keys the app already uses, so existing installs keep their settings.
class SharedPrefsAppSettings(context: Context) : AppSettings {
  private val appContext = context.applicationContext

  override fun language(): AppLanguage = LocaleManager.getInitialLanguage(appContext)

  override fun saveLanguage(language: AppLanguage) = LocaleManager.saveLanguage(appContext, language)

  override fun isFirstLaunch(): Boolean = LocaleManager.isFirstLaunch(appContext)

  override fun setFirstLaunchCompleted() = LocaleManager.setFirstLaunchCompleted(appContext)

  override fun savedWallpaperUrl(): String? = LocaleManager.getSavedWallpaperUrl(appContext)

  override fun saveWallpaperUrl(url: String) = LocaleManager.saveWallpaperUrl(appContext, url)

  override fun isSoundEnabled(): Boolean = LocaleManager.isSoundEnabled(appContext)

  override fun saveSoundEnabled(enabled: Boolean) = LocaleManager.saveSoundEnabled(appContext, enabled)

  // Same key and default (true) as AlarmNotificationScheduler.anniversaryNotificationsEnabled.
  override fun anniversaryNotificationsEnabled(): Boolean =
    notificationPrefs().getBoolean(KEY_ANNIVERSARY_ENABLED, true)

  override fun setAnniversaryNotificationsEnabled(enabled: Boolean) {
    notificationPrefs().edit().putBoolean(KEY_ANNIVERSARY_ENABLED, enabled).apply()
  }

  private fun notificationPrefs() = appContext.getSharedPreferences(NOTIFICATION_PREFS, Context.MODE_PRIVATE)

  private companion object {
    const val NOTIFICATION_PREFS = "inlove_notifications"
    const val KEY_ANNIVERSARY_ENABLED = "anniversary_enabled"
  }
}
