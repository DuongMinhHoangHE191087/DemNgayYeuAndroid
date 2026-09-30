package com.example.ui.util

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocaleManagerTest {

  @Test
  fun wallpaperUrl_survivesAcrossReads_onceSaved() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    assert(LocaleManager.getSavedWallpaperUrl(context) == null) {
      "no wallpaper saved yet must return null so the caller falls back to its own default"
    }

    LocaleManager.saveWallpaperUrl(context, "https://example.com/custom-wallpaper.jpg")

    assert(LocaleManager.getSavedWallpaperUrl(context) == "https://example.com/custom-wallpaper.jpg") {
      "a saved wallpaper URL must be readable back — this is the fix for wallpaper resetting on app restart"
    }
  }
}
