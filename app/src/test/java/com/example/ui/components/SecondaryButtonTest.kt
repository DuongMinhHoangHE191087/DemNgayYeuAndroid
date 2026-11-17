package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MinTouchBox
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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
class SecondaryButtonTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun secondaryButton_click_firesOnce() {
    var clicks = 0
    composeTestRule.setContent {
      MyApplicationTheme {
        SecondaryButton(text = "Hủy", onClick = { clicks++ }, testTag = "sb")
      }
    }
    composeTestRule.onNodeWithTag("sb").performClick()
    assertEquals(1, clicks)
  }

  @Test
  fun secondaryButton_defaultTag_isSecondaryBtn() {
    composeTestRule.setContent {
      MyApplicationTheme {
        SecondaryButton(text = "Hủy", onClick = {})
      }
    }
    composeTestRule.onNodeWithTag("secondary_btn").assertExists()
  }

  @Test
  fun secondaryButton_meetsTouchTarget() {
    composeTestRule.setContent {
      MyApplicationTheme {
        SecondaryButton(
          text = "Hủy",
          onClick = {},
          modifier = Modifier.heightIn(min = MinTouchBox),
          testTag = "sb_touch"
        )
      }
    }
    composeTestRule.onNodeWithTag("sb_touch").assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun disabledLook_false_marksNodeDisabled() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Column {
          SecondaryButton(
            text = "Disabled",
            onClick = {},
            modifier = Modifier.disabledLook(false),
            testTag = "dl_false"
          )
          SecondaryButton(
            text = "Enabled",
            onClick = {},
            modifier = Modifier.disabledLook(true),
            testTag = "dl_true"
          )
        }
      }
    }
    composeTestRule.onNodeWithTag("dl_false").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("dl_true").assertIsEnabled()
  }

  @Test
  fun secondaryButton_longLabelsInNarrowRow_bothVisible() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Row(modifier = Modifier.width(320.dp)) {
          SecondaryButton(
            text = "Đồng ý Set Love ❤️",
            onClick = {},
            modifier = Modifier.weight(1f).heightIn(min = MinTouchBox),
            testTag = "sb_long_1"
          )
          SecondaryButton(
            text = "Lưu & Tính Ngày",
            onClick = {},
            modifier = Modifier.weight(1f).heightIn(min = MinTouchBox),
            testTag = "sb_long_2"
          )
        }
      }
    }
    composeTestRule.onNodeWithTag("sb_long_1").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    composeTestRule.onNodeWithTag("sb_long_2").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun secondaryButton_states_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Column {
          PrimaryGradientButton(text = "Lưu", onClick = {})
          SecondaryButton(text = "Hủy", onClick = {})
          SecondaryButton(text = "Xóa", onClick = {}, isDestructive = true)
          SecondaryButton(text = "Xóa vĩnh viễn", onClick = {}, isDestructive = true, filled = true)
          SecondaryButton(text = "Filled no-op", onClick = {}, filled = true)
          SecondaryButton(text = "Có icon", onClick = {}, icon = Icons.Default.Favorite)
        }
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/secondary_button_states.png")
  }
}
