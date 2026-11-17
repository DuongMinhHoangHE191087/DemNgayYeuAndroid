package com.example.ui.components

// T6: the shared ProfileAvatar shows the Person placeholder for a blank, null or stock-photo url, and no placeholder for a real url.

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
class ProfileAvatarTest {

  @get:Rule val rule = createComposeRule()

  private fun show(url: String?) {
    rule.setContent {
      MyApplicationTheme {
        ProfileAvatar(url = url, contentDescription = "avatar", modifier = Modifier.size(40.dp))
      }
    }
  }

  @Test
  fun blankUrl_showsPlaceholder() {
    show("")
    rule.onNodeWithTag("profile_avatar_placeholder").assertExists()
  }

  @Test
  fun nullUrl_showsPlaceholder() {
    show(null)
    rule.onNodeWithTag("profile_avatar_placeholder").assertExists()
  }

  @Test
  fun stockUrl_showsPlaceholder() {
    show("https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200")
    rule.onNodeWithTag("profile_avatar_placeholder").assertExists()
  }

  @Test
  fun realUrl_hidesPlaceholder() {
    show("https://example.com/me.jpg")
    rule.onNodeWithTag("profile_avatar_placeholder").assertDoesNotExist()
  }

  @Test
  fun realUrl_keepsContentDescriptionWhileLoadingOrFailed() {
    show("https://example.com/me.jpg")
    rule.onNodeWithContentDescription("avatar").assertExists()
  }
}
