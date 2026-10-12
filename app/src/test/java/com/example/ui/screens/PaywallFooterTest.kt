package com.example.ui.screens

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
class PaywallFooterTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setFooter(
    canPurchase: Boolean = true,
    priceUnavailable: Boolean = false,
    onRetryPrices: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      MyApplicationTheme {
        PaywallFooter(
          ctaText = "Start free trial",
          canPurchase = canPurchase,
          priceUnavailable = priceUnavailable,
          disclosure = "Free for 7 days, then 99.000 d per year. Renews automatically. Cancel anytime in Google Play.",
          onPurchase = {},
          onRetryPrices = onRetryPrices,
          onRestore = {},
          onManage = {},
          onTerms = {},
          onPrivacy = {},
        )
      }
    }
  }

  @Test
  fun footer_hasCtaAndDisclosure() {
    setFooter()
    composeTestRule.onNodeWithTag("paywall_cta").assertExists().assertIsEnabled()
    composeTestRule.onNodeWithTag("paywall_disclosure").assertExists()
    composeTestRule.onNodeWithTag("paywall_retry_prices").assertDoesNotExist()
  }

  @Test
  fun footer_priceUnavailable_showsRetry_disablesCta() {
    var retries = 0
    setFooter(canPurchase = false, priceUnavailable = true, onRetryPrices = { retries++ })
    composeTestRule.onNodeWithTag("paywall_retry_prices").assertExists().performClick()
    assertEquals(1, retries)
    composeTestRule.onNodeWithTag("paywall_cta").assertIsNotEnabled()
  }

  @Test
  fun footer_screenshot() {
    setFooter()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/paywall_footer.png")
  }
}
