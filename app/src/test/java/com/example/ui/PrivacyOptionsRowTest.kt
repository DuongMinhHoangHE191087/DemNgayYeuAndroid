package com.example.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.app.plugin.consent.ConsentManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyOptionsRowTest {

  @Test
  fun isPrivacyOptionsRequired_returnsFalseSafely_whenNoConsentFlowHasEverRunInThisProcess() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // No UMP flow has run in this test process at all — this is exactly the "Settings opened
    // cold, e.g. via deep link, before MainActivity's own consent flow ever ran" case (Review
    // Focus item 5). ConsentManager.kt:151-157 wraps this in try/catch and returns false; this
    // test pins that so a future SDK change can't silently make it throw instead.
    val required = ConsentManager.isPrivacyOptionsRequired(context)

    assert(!required) { "must degrade to false, never throw, when UMP has no saved consent state" }
  }

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun adPrivacyOptionsRow_rendersOnlyWhenVisibleIsTrue() {
    composeRule.setContent {
      com.example.ui.screens.AdPrivacyOptionsRow(visible = false, isEnglish = true, onClick = {})
    }
    composeRule.onNodeWithTag("settings_privacy_options_row").assertDoesNotExist()

    composeRule.setContent {
      com.example.ui.screens.AdPrivacyOptionsRow(visible = true, isEnglish = true, onClick = {})
    }
    composeRule.onNodeWithTag("settings_privacy_options_row").assertExists()
  }

  @Test
  fun adPrivacyOptionsRow_clickInvokesCallback() {
    var clicked = false
    composeRule.setContent {
      com.example.ui.screens.AdPrivacyOptionsRow(visible = true, isEnglish = true, onClick = { clicked = true })
    }
    composeRule.onNodeWithTag("settings_privacy_options_row").performClick()
    assert(clicked) { "tapping the row must invoke onClick — this is what wires to ConsentManager.showPrivacyOptions(activity) at the real call site" }
  }
}
