package com.example.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.UI_SCALE
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class AppWindowsTest {

  @get:Rule val rule = createComposeRule()

  // Windows (Dialog/Popup) may keep the test clock busy, so they advance frames by hand
  // instead of waitForIdle / onNode*.
  private fun advanceUntil(done: () -> Boolean) {
    var frames = 0
    while (!done() && frames < 30) {
      rule.mainClock.advanceTimeByFrame()
      frames++
    }
  }

  @Test
  fun windowLocals_reapplyDensityContextConfigurationAndTouchSize() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val cfg = Configuration(ctx.resources.configuration)
    var outer: Density? = null
    var inner: Density? = null
    var innerContext: Context? = null
    var innerConfig: Configuration? = null
    var minSize: Dp? = null
    rule.setContent {
      outer = LocalDensity.current
      AppWindowLocals(ctx, cfg) {
        inner = LocalDensity.current
        innerContext = LocalContext.current
        innerConfig = LocalConfiguration.current
        minSize = LocalMinimumInteractiveComponentSize.current
      }
    }
    rule.waitForIdle()
    assertEquals(outer!!.density * UI_SCALE, inner!!.density, 1e-4f)
    assertEquals(outer!!.fontScale, inner!!.fontScale, 1e-4f)
    assertSame(ctx, innerContext)
    assertSame(cfg, innerConfig)
    assertEquals(MinTouchBox, minSize)
  }

  @Test
  fun appDialog_exposesCallSiteContextAndScaledDensity() {
    var outerContext: Context? = null
    var outer: Density? = null
    var innerContext: Context? = null
    var inner: Density? = null
    rule.mainClock.autoAdvance = false
    rule.setContent {
      outerContext = LocalContext.current
      outer = LocalDensity.current
      AppDialog(onDismissRequest = {}) {
        innerContext = LocalContext.current
        inner = LocalDensity.current
      }
    }
    advanceUntil { inner != null }
    assertNotNull(inner)
    assertSame(outerContext, innerContext)
    assertEquals(outer!!.density * UI_SCALE, inner!!.density, 1e-4f)
  }

  @Test
  fun appAlertDialog_scrollableTextTrue_composesPlainText() {
    var composed = false
    rule.mainClock.autoAdvance = false
    rule.setContent {
      AppAlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        scrollableText = true,
        text = { composed = true },
      )
    }
    advanceUntil { composed }
    assertTrue(composed)
  }

  @Test
  fun appAlertDialog_scrollableTextFalse_composesOwnScroll() {
    var composed = false
    rule.mainClock.autoAdvance = false
    rule.setContent {
      AppAlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        scrollableText = false,
        text = {
          Column(Modifier.verticalScroll(rememberScrollState())) { composed = true }
        },
      )
    }
    advanceUntil { composed }
    assertTrue(composed)
  }

  @Test
  fun appDropdownMenu_wrapsItemsWithScaledDensity() {
    var outer: Density? = null
    var inner: Density? = null
    rule.mainClock.autoAdvance = false
    rule.setContent {
      outer = LocalDensity.current
      AppDropdownMenu(expanded = true, onDismissRequest = {}) {
        inner = LocalDensity.current
      }
    }
    advanceUntil { inner != null }
    assertNotNull(inner)
    assertEquals(outer!!.density * UI_SCALE, inner!!.density, 1e-4f)
  }
}
