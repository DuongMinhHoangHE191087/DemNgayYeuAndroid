package com.example.ui.settings

import com.example.ui.util.AppLanguage

// User settings the UI layer depends on. No Android types here, so view models stay testable on the JVM.
interface AppSettings {
  fun language(): AppLanguage
  fun saveLanguage(language: AppLanguage)
  fun isFirstLaunch(): Boolean
  fun setFirstLaunchCompleted()
  fun savedWallpaperUrl(): String?
  fun saveWallpaperUrl(url: String)
  fun isSoundEnabled(): Boolean
  fun saveSoundEnabled(enabled: Boolean)
  fun anniversaryNotificationsEnabled(): Boolean
  fun setAnniversaryNotificationsEnabled(enabled: Boolean)
}
