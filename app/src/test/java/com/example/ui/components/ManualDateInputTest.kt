package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class ManualDateInputTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun shortYear_showsErrorAndDoesNotConfirm() {
    var confirmed: String? = null
    composeTestRule.setContent {
      MyApplicationTheme {
        ManualDateInputContent(
          currentDate = "",
          isVietnamese = false,
          onDismiss = {},
          onConfirm = { confirmed = it }
        )
      }
    }

    composeTestRule.onNodeWithTag("input_anniversary_text").performTextInput("14/02/23")
    composeTestRule.onNodeWithText("Save & Calculate").performClick()

    composeTestRule.onNodeWithTag("text_anniversary_error").assertExists()
    assertNull(confirmed)
  }

  @Test
  fun looseDate_isNormalizedBeforeConfirm() {
    var confirmed: String? = null
    composeTestRule.setContent {
      MyApplicationTheme {
        ManualDateInputContent(
          currentDate = "",
          isVietnamese = false,
          onDismiss = {},
          onConfirm = { confirmed = it }
        )
      }
    }

    composeTestRule.onNodeWithTag("input_anniversary_text").performTextInput("1/2/2020")
    composeTestRule.onNodeWithText("Save & Calculate").performClick()

    assertEquals("01/02/2020", confirmed)
    composeTestRule.onNodeWithTag("text_anniversary_error").assertDoesNotExist()
  }
}
