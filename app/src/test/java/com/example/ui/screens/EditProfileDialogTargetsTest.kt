package com.example.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.data.model.OnlineUserEntity
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class EditProfileDialogTargetsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun closeAndPresets_meetMinTouchBox() {
    composeTestRule.setContent {
      MyApplicationTheme {
        EditMyProfileDialogContent(
          currentUser = OnlineUserEntity(uid = "me", coupleCode = ""),
          presetAvatars = listOf("a", "b"),
          isEnglish = false,
          onDismiss = {},
          onSave = { _, _, _, _, _ -> }
        )
      }
    }

    // The test root runs at the unscaled density, while the app content is scaled by
    // AppDensity (0.85). MinTouchBox (57.dp) therefore renders at about 48.45.dp here.
    listOf("btn_close_edit_profile", "preset_avatar_0", "preset_avatar_1").forEach { tag ->
      composeTestRule.onNodeWithTag(tag)
        .assertWidthIsAtLeast(48.dp)
        .assertHeightIsAtLeast(48.dp)
    }
    composeTestRule.onNodeWithContentDescription("Ảnh đại diện").assertExists()
  }
}
