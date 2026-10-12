package com.example.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class AddMilestoneExamplesTest {

  @get:Rule val composeTestRule = createComposeRule()

  // Tests the form body directly: a text field inside a Dialog window never goes idle under
  // Robolectric.
  private val form = MilestoneFormState("")

  private fun setBody(isEnglish: Boolean = false) {
    composeTestRule.setContent {
      MyApplicationTheme {
        AddMilestoneFormBody(form = form, presetPhotos = emptyList(), isEnglish = isEnglish)
      }
    }
  }

  @Test
  fun exampleChip_fillsTitle() {
    setBody()

    composeTestRule.onNodeWithText("Ngày tỏ tình").performClick()
    composeTestRule.onNodeWithTag("input_milestone_title").assertTextContains("Ngày tỏ tình")
  }

  @Test
  fun exampleChip_selectsCategory_andKeepsStoredVietnameseTag() {
    setBody(isEnglish = true)

    composeTestRule.onNodeWithText("First trip").performClick()
    composeTestRule.waitForIdle()

    assertEquals("First trip", form.title)
    assertEquals("Du Lịch", form.categoryTag)
  }

  @Test
  fun categoryCell_meetsMinTouchHeight() {
    setBody()

    // The test root is unscaled; app content renders at 0.85, so MinTouchBox (57.dp) is ~48.45.dp.
    composeTestRule.onNodeWithContentDescription("Du Lịch").assertHeightIsAtLeast(48.dp)
  }
}
