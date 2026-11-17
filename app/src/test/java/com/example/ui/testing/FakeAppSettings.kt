package com.example.ui.testing

import com.example.ui.settings.AppSettings
import com.example.ui.util.AppLanguage

// In-memory AppSettings for tests that do not need Android.
class FakeAppSettings : AppSettings {
  private var savedLanguage = AppLanguage.EN
  private var firstLaunch = true
  private var wallpaperUrl: String? = null
  private var soundEnabled = true
  private var anniversaryEnabled = true

  override fun language(): AppLanguage = savedLanguage

  override fun saveLanguage(language: AppLanguage) {
    savedLanguage = language
  }

  override fun isFirstLaunch(): Boolean = firstLaunch

  override fun setFirstLaunchCompleted() {
    firstLaunch = false
  }

  override fun savedWallpaperUrl(): String? = wallpaperUrl

  override fun saveWallpaperUrl(url: String) {
    wallpaperUrl = url
  }

  override fun isSoundEnabled(): Boolean = soundEnabled

  override fun saveSoundEnabled(enabled: Boolean) {
    soundEnabled = enabled
  }

  override fun anniversaryNotificationsEnabled(): Boolean = anniversaryEnabled

  override fun setAnniversaryNotificationsEnabled(enabled: Boolean) {
    anniversaryEnabled = enabled
  }
}
