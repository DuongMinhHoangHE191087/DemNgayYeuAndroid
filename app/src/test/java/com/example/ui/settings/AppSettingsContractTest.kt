package com.example.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.platform.SharedPrefsAppSettings
import com.example.ui.testing.FakeAppSettings
import com.example.ui.util.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

// One contract, run against every AppSettings implementation.
abstract class AppSettingsContractTest {

  abstract fun createSettings(): AppSettings

  @Test
  fun saveLanguage_roundTrips() {
    val settings = createSettings()
    settings.saveLanguage(AppLanguage.VI)
    assertEquals(AppLanguage.VI, settings.language())
    settings.saveLanguage(AppLanguage.EN)
    assertEquals(AppLanguage.EN, settings.language())
  }

  @Test
  fun firstLaunch_trueUntilCompleted() {
    val settings = createSettings()
    assertTrue(settings.isFirstLaunch())
    settings.setFirstLaunchCompleted()
    assertFalse(settings.isFirstLaunch())
  }

  @Test
  fun wallpaper_nullUntilSaved_thenRoundTrips() {
    val settings = createSettings()
    assertNull(settings.savedWallpaperUrl())
    settings.saveWallpaperUrl("https://example.com/wallpaper.jpg")
    assertEquals("https://example.com/wallpaper.jpg", settings.savedWallpaperUrl())
  }

  @Test
  fun sound_defaultTrue_thenRoundTrips() {
    val settings = createSettings()
    assertTrue(settings.isSoundEnabled())
    settings.saveSoundEnabled(false)
    assertFalse(settings.isSoundEnabled())
    settings.saveSoundEnabled(true)
    assertTrue(settings.isSoundEnabled())
  }

  @Test
  fun anniversaryNotifications_defaultTrue_thenRoundTrips() {
    val settings = createSettings()
    assertTrue(settings.anniversaryNotificationsEnabled())
    settings.setAnniversaryNotificationsEnabled(false)
    assertFalse(settings.anniversaryNotificationsEnabled())
    settings.setAnniversaryNotificationsEnabled(true)
    assertTrue(settings.anniversaryNotificationsEnabled())
  }
}

class FakeAppSettingsContractTest : AppSettingsContractTest() {
  override fun createSettings(): AppSettings = FakeAppSettings()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPrefsAppSettingsContractTest : AppSettingsContractTest() {

  override fun createSettings(): AppSettings =
    SharedPrefsAppSettings(ApplicationProvider.getApplicationContext<Context>())

  @Test
  fun sharedPrefs_usesLegacyKeys() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("inlove_preferences", Context.MODE_PRIVATE)
    val notifPrefs = context.getSharedPreferences("inlove_notifications", Context.MODE_PRIVATE)
    // Values written by the pre-refactor app must be read back through the interface.
    prefs.edit()
      .putString("key_app_language", "EN")
      .putBoolean("key_first_launch", false)
      .putString("key_wallpaper_url", "https://example.com/legacy.jpg")
      .putBoolean("key_sound_enabled", false)
      .apply()
    notifPrefs.edit().putBoolean("anniversary_enabled", false).apply()

    val settings = SharedPrefsAppSettings(context)
    assertEquals(AppLanguage.EN, settings.language())
    assertFalse(settings.isFirstLaunch())
    assertEquals("https://example.com/legacy.jpg", settings.savedWallpaperUrl())
    assertFalse(settings.isSoundEnabled())
    assertFalse(settings.anniversaryNotificationsEnabled())

    // Writes must land on the same keys.
    settings.saveLanguage(AppLanguage.VI)
    settings.saveSoundEnabled(true)
    settings.setAnniversaryNotificationsEnabled(true)
    assertEquals("VI", prefs.getString("key_app_language", null))
    assertTrue(prefs.getBoolean("key_sound_enabled", false))
    assertTrue(notifPrefs.getBoolean("anniversary_enabled", false))
  }

  @Test
  fun sharedPrefs_defaultLanguageFollowsSystemLocale() {
    val original = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("vi-VN"))
      assertEquals(AppLanguage.VI, createSettings().language())
      Locale.setDefault(Locale.forLanguageTag("en-US"))
      assertEquals(AppLanguage.EN, createSettings().language())
      Locale.setDefault(Locale.forLanguageTag("fr-FR"))
      assertEquals(AppLanguage.EN, createSettings().language())
    } finally {
      Locale.setDefault(original)
    }
  }
}
