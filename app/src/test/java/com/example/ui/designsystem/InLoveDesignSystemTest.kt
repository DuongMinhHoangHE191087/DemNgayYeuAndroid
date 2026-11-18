package com.example.ui.designsystem

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextDarkPlum
import com.example.ui.theme.TextSubtlePlum
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
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
class InLoveDesignSystemTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun choiceChip_meetsMinimum48dpTouchTarget_andToggles() {
    var clicks = 0
    composeTestRule.setContent {
      MyApplicationTheme {
        InLoveChoiceChip(
          label = "Coffee",
          selected = false,
          onClick = { clicks++ },
          testTag = "ds_chip_coffee"
        )
      }
    }

    composeTestRule.onNodeWithTag("ds_chip_coffee")
      .assertWidthIsAtLeast(48.dp)
      .assertHeightIsAtLeast(48.dp)
      .performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun progressBar_andStatusPill_renderLabelsAndPercentage() {
    composeTestRule.setContent {
      MyApplicationTheme {
        InLoveSurfaceCard {
          InLoveStatusPill(text = "Pro Ready")
          InLoveProgressBar(label = "Dossier", progressFraction = 0.65f)
        }
      }
    }

    composeTestRule.onNodeWithText("Pro Ready").assertExists()
    composeTestRule.onNodeWithText("Dossier").assertExists()
    composeTestRule.onNodeWithText("65%").assertExists()
  }

  @Test
  fun tokens_useHighContrastPlumValues() {
    assertEquals(TextDarkPlum, InLoveColors.TextPrimary)
    assertEquals(TextSubtlePlum, InLoveColors.TextSecondary)
    assertTrue(InLoveRadii.Card >= 24.dp)
    assertTrue(InLoveSpacing.Xs >= 8.dp)
  }
}
