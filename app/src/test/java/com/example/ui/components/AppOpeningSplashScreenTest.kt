package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
class AppOpeningSplashScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun splashScreen_rendersLogoAndDismissesOnClick() {
    var isFinished = false

    composeTestRule.setContent {
      MyApplicationTheme {
        AppOpeningSplashScreen(
          isEnglish = false,
          onFinished = { isFinished = true }
        )
      }
    }

    composeTestRule.onNodeWithTag("app_opening_splash").assertExists()
    composeTestRule.onNodeWithText("InLove").assertExists()
    composeTestRule.onNodeWithText("Gắn kết từng khoảnh khắc yêu").assertExists()

    // Clicking anywhere on the splash screen triggers immediate skip
    composeTestRule.onNodeWithTag("app_opening_splash").performClick()
    assertTrue(isFinished)
  }
}
