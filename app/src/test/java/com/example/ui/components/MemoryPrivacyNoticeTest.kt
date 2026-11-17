package com.example.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.domain.DataResidency
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
class MemoryPrivacyNoticeTest {

  @get:Rule val rule = createComposeRule()

  @Test
  fun localOnly_showsCardWithAllThreeTexts() {
    rule.setContent {
      MyApplicationTheme {
        MemoryPrivacyNotice(residency = DataResidency.LOCAL_ONLY)
      }
    }
    rule.onNodeWithTag("settings_privacy_notice").assertExists()
    rule.onNodeWithText("Your private memories stay on your phone", substring = true).assertExists()
    rule.onNodeWithText(
      "Memory photos, videos and notes are stored only on this phone",
      substring = true
    ).assertExists()
    rule.onNodeWithText("Please do not clear the app data", substring = true).assertExists()
  }

  @Test
  fun cloudSync_rendersNothing() {
    rule.setContent {
      MyApplicationTheme {
        MemoryPrivacyNotice(residency = DataResidency.CLOUD_SYNC)
      }
    }
    rule.onNodeWithTag("settings_privacy_notice").assertDoesNotExist()
    rule.onNodeWithText("Your private memories stay on your phone", substring = true).assertDoesNotExist()
  }

  @Test
  @Config(sdk = [34], qualifiers = "vi")
  fun vietnameseLocale_showsVietnameseTitle() {
    rule.setContent {
      MyApplicationTheme {
        MemoryPrivacyNotice(residency = DataResidency.LOCAL_ONLY)
      }
    }
    rule.onNodeWithText("Kỷ niệm riêng tư ở lại trên máy bạn", substring = true).assertExists()
  }

  @Test
  fun localOnly_bodySaysEmailAndPasswordOnlySignIn() {
    rule.setContent {
      MyApplicationTheme {
        MemoryPrivacyNotice(residency = DataResidency.LOCAL_ONLY)
      }
    }
    rule.onNodeWithText(
      "Your email and password are used only to sign in",
      substring = true
    ).assertExists()
  }

  @Test
  @Config(sdk = [34], qualifiers = "vi")
  fun vietnameseLocale_bodySaysEmailAndPasswordOnlySignIn() {
    rule.setContent {
      MyApplicationTheme {
        MemoryPrivacyNotice(residency = DataResidency.LOCAL_ONLY)
      }
    }
    rule.onNodeWithText(
      "Email và mật khẩu chỉ dùng để đăng nhập",
      substring = true
    ).assertExists()
  }
}
