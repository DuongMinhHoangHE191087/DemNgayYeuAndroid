package com.example.ui.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.golden.LocaleStringsSupport
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ba chuỗi onboarding nhắc hẹn (title, sub, ai_tip) không được chứa token AI đứng riêng, phân biệt hoa thường.
 * Kiểm tra cả hai ngôn ngữ, cả trong resource (strings.xml) lẫn trong bảng tĩnh LocalizedStrings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoAiClaimStringsTest {
  private val S = LocaleStringsSupport
  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val aiToken = Regex("\\bAI\\b")
  private val reminderKeys = listOf("onboardingReminderTitle", "onboardingReminderSub", "onboardingReminderAiTip")

  @Test
  fun reminderOnboardingStringsDoNotClaimAi() {
    val sources = linkedMapOf(
      "enResource" to S.fromResources(context, AppLanguage.EN),
      "enStatic" to S.asMap(LocalizedStrings.get(AppLanguage.EN)),
      "viResource" to S.fromResources(context, AppLanguage.VI),
      "viStatic" to S.asMap(LocalizedStrings.get(AppLanguage.VI)),
    )
    val hits = sources.flatMap { (source, map) ->
      reminderKeys.filter { aiToken.containsMatchIn(map.getValue(it)) }.map { "$it@$source" }
    }
    assertTrue("Chuỗi vẫn có token AI: $hits", hits.isEmpty())
  }
}
