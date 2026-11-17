package com.example.ui.screens

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.data.model.OnlineUserEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.withCardFallback
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
          onSave = { _, _, _, _, _, _ -> }
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

  @Test
  fun avatarActions_existAndMeetMinRequirements() {
    composeTestRule.setContent {
      MyApplicationTheme {
        EditMyProfileDialogContent(
          currentUser = OnlineUserEntity(uid = "me", coupleCode = "", avatarUrl = "https://example.com/avatar.jpg"),
          presetAvatars = emptyList(),
          isEnglish = false,
          canEditPhoto = true,
          onDismiss = {},
          onSave = { _, _, _, _, _, _ -> }
        )
      }
    }

    // MinTouchBox (57.dp) renders at about 48.45.dp here (see the first test).
    composeTestRule.onNodeWithTag("btn_photo_pencil")
      .assertWidthIsAtLeast(48.dp)
      .assertHeightIsAtLeast(48.dp)
    composeTestRule.onNodeWithTag("btn_photo_pencil").performClick()
    composeTestRule.onNodeWithTag("btn_photo_library").assertExists()
    composeTestRule.onNodeWithTag("btn_photo_camera").assertExists()
    composeTestRule.onNodeWithTag("btn_photo_remove").assertExists()
    composeTestRule.onNodeWithTag("btn_photo_cancel")
      .assertWidthIsAtLeast(48.dp)
      .assertHeightIsAtLeast(48.dp)
    listOf("btn_photo_library", "btn_photo_camera", "btn_photo_remove").forEach { tag ->
      composeTestRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
    }
  }

  @Test
  fun birthdayField_usesDatePickerWithMinTouchTarget() {
    composeTestRule.setContent {
      MyApplicationTheme {
        EditMyProfileDialogContent(
          currentUser = OnlineUserEntity(uid = "me", coupleCode = ""),
          isEnglish = true,
          onDismiss = {},
          onSave = { _, _, _, _, _, _ -> }
        )
      }
    }

    composeTestRule.onNodeWithTag("input_my_profile_birth").assertExists()
    // Unmerged tree: the text field merges its trailing icon into its own node.
    // Only the size is checked here; MyProfileBirthdayTest clicks it (the picker window does open under Robolectric).
    composeTestRule.onNodeWithTag("input_my_profile_birth_calendar_btn", useUnmergedTree = true)
      .assertWidthIsAtLeast(48.dp)
      .assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun blankBirthDate_staysBlankWhenSaved() {
    var savedName: String? = null
    var savedBirth: String? = null
    composeTestRule.setContent {
      MyApplicationTheme {
        EditMyProfileDialogContent(
          currentUser = OnlineUserEntity(uid = "me", coupleCode = "", displayName = "Smoke A", isProfileSetup = true),
          isEnglish = true,
          onDismiss = {},
          onSave = { name, birth, _, _, _, _ -> savedName = name; savedBirth = birth }
        )
      }
    }

    composeTestRule.onNodeWithTag("btn_save_my_profile").performScrollTo().performClick()
    assertEquals("", savedBirth)
    assertEquals("Smoke A", savedName)
  }

  @Test
  fun cardFallbackName_isPrefilledAndSaved() {
    var savedName: String? = null
    composeTestRule.setContent {
      MyApplicationTheme {
        EditMyProfileDialogContent(
          currentUser = OnlineUserEntity(uid = "me", coupleCode = "").withCardFallback("An", "", ""),
          isEnglish = true,
          onDismiss = {},
          onSave = { name, _, _, _, _, _ -> savedName = name }
        )
      }
    }

    composeTestRule.onNodeWithTag("btn_save_my_profile").performScrollTo().performClick()
    assertEquals("An", savedName)
  }

  @Test
  fun placeholderName_isNotPrefilled() {
    var savedName: String? = null
    composeTestRule.setContent {
      MyApplicationTheme {
        EditMyProfileDialogContent(
          currentUser = OnlineUserEntity(uid = "me", coupleCode = "", displayName = "Vô danh", isProfileSetup = true),
          isEnglish = true,
          onDismiss = {},
          onSave = { name, _, _, _, _, _ -> savedName = name }
        )
      }
    }

    composeTestRule.onNodeWithTag("btn_save_my_profile").performScrollTo().performClick()
    assertEquals("", savedName)
  }
}
