package com.example.ui.screens

import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.example.ui.testing.NoNetworkImagesRule
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.example.ui.testing.TALL_PIXEL8
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.InLoveViewModelHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "vi-$TALL_PIXEL8", sdk = [34])
class ExactAlarmSettingsRowTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val noNetwork = NoNetworkImagesRule()

  private val title = "Cho phép báo thức đúng giờ"

  // Density of the real screen, read above the theme: MyApplicationTheme scales everything below it by 0.85.
  private var screenDensity = 0f

  private fun launch(canScheduleExact: Boolean, owner: LifecycleOwner? = null) {
    ShadowAlarmManager.setCanScheduleExactAlarms(canScheduleExact)
    // SettingsScreen asks FirebaseAuth whether Facebook is linked, which needs a default app (offline, fake options).
    val app = ApplicationProvider.getApplicationContext<Application>()
    if (FirebaseApp.getApps(app).isEmpty()) {
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder().setApplicationId("1:1:android:1").setApiKey("fake").setProjectId("fake").build()
      )
    }
    val viewModel = InLoveViewModelHarness.build()
    composeTestRule.setContent {
      screenDensity = LocalDensity.current.density
      CompositionLocalProvider(LocalLifecycleOwner provides (owner ?: LocalLifecycleOwner.current)) {
        MyApplicationTheme { SettingsScreen(viewModel = viewModel) }
      }
    }
  }

  @Test
  fun rowShown_whenExactAlarmsNotGranted() {
    launch(canScheduleExact = false)

    composeTestRule.onNodeWithText(title).assertIsDisplayed()
  }

  @Test
  fun click_opensExactAlarmPermissionScreenForThisApp() {
    launch(canScheduleExact = false)

    composeTestRule.onNodeWithText(title).performClick()

    val app = ApplicationProvider.getApplicationContext<Application>()
    val started = shadowOf(app).nextStartedActivity
    assertNotNull("no activity was started", started)
    assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, started.action)
    assertEquals("package:" + app.packageName, started.dataString)
  }

  @Test
  fun rowAbsent_whenExactAlarmsGranted() {
    launch(canScheduleExact = true)

    composeTestRule.onNodeWithText(title).assertDoesNotExist()
  }

  // assertHeightIsAtLeast reads a node in its own (scaled) density, so it cannot see AppDensity: compare real pixels with 48 dp of the screen.
  @Test
  fun row_meets48DpTouchTarget() {
    launch(canScheduleExact = false)

    val size = composeTestRule.onNodeWithText(title).fetchSemanticsNode().size
    val minPx = (48 * screenDensity).roundToInt()
    assertTrue("rendered ${size.width}x${size.height} px, need a height of at least $minPx px (48 dp)", size.height >= minPx)
  }

  // The permission is granted on the system screen, so the row has to notice it when the app comes back to the front.
  @Test
  fun rowDisappears_whenExactAlarmsAreGrantedWhileTheAppIsAway() {
    val owner = ControlledLifecycleOwner()
    owner.lifecycle.currentState = Lifecycle.State.RESUMED
    launch(canScheduleExact = false, owner = owner)
    composeTestRule.onNodeWithText(title).assertIsDisplayed()

    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    composeTestRule.runOnUiThread {
      owner.lifecycle.currentState = Lifecycle.State.STARTED
      owner.lifecycle.currentState = Lifecycle.State.RESUMED
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText(title).assertDoesNotExist()
  }

  private class ControlledLifecycleOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)
  }
}
