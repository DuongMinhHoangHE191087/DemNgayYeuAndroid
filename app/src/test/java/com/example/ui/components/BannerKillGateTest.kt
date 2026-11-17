package com.example.ui.components

import android.view.View
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A fake banner (plain View) is injected so no Mobile Ads init happens; canRequestAds and
// sdkDisabled are always passed so the AppServiceLocator / AdsHelper defaults never run.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BannerKillGateTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun show(
    isVip: Boolean = false,
    canRequestAds: Boolean = true,
    adUnitId: String = "ca-app-pub-test/1",
    killed: Boolean = false
  ) {
    composeRule.setContent {
      ComposeBannerAd(
        isVip = isVip,
        adUnitId = adUnitId,
        canRequestAds = canRequestAds,
        sdkDisabled = { killed },
        createBanner = { ctx, _ -> View(ctx) }
      )
    }
  }

  @Test
  fun killedSdk_hidesComposeBanner() {
    show(killed = true)
    composeRule.onNodeWithTag("compose_banner").assertDoesNotExist()
  }

  @Test
  fun notKilled_showsComposeBanner() {
    show()
    composeRule.onNodeWithTag("compose_banner").assertExists()
  }

  @Test
  fun vip_hidesComposeBanner() {
    show(isVip = true)
    composeRule.onNodeWithTag("compose_banner").assertDoesNotExist()
  }

  @Test
  fun noConsent_hidesComposeBanner() {
    show(canRequestAds = false)
    composeRule.onNodeWithTag("compose_banner").assertDoesNotExist()
  }

  @Test
  fun blankAdUnit_hidesComposeBanner() {
    show(adUnitId = " ")
    composeRule.onNodeWithTag("compose_banner").assertDoesNotExist()
  }

  @Test
  fun createBanner_receivesTheAdUnitId() {
    val asked = mutableListOf<String>()
    composeRule.setContent {
      ComposeBannerAd(
        isVip = false,
        adUnitId = "ca-app-pub-test/42",
        canRequestAds = true,
        sdkDisabled = { false },
        createBanner = { ctx, unitId -> asked += unitId; View(ctx) }
      )
    }
    composeRule.onNodeWithTag("compose_banner").assertExists()
    assertEquals(listOf("ca-app-pub-test/42"), asked)
  }

  // Google Play policy: 12dp above and below keeps the banner away from navigation buttons.
  // The banner view fills the space the box leaves, so capping the box at 100dp leaves 76dp.
  @Test
  fun banner_keepsAccidentalClickPadding() {
    var banner: View? = null
    composeRule.setContent {
      ComposeBannerAd(
        isVip = false,
        adUnitId = "ca-app-pub-test/1",
        modifier = Modifier.heightIn(max = 100.dp),
        canRequestAds = true,
        sdkDisabled = { false },
        createBanner = { ctx, _ -> View(ctx).also { banner = it } }
      )
    }
    composeRule.waitForIdle()
    val expected = with(composeRule.density) { 76.dp.roundToPx() }
    assertEquals(expected, banner!!.height)
  }

  @Test
  fun killFlippedMidSession_hidesComposeBanner() {
    val killed = mutableStateOf(false)
    composeRule.setContent {
      ComposeBannerAd(
        isVip = false,
        adUnitId = "ca-app-pub-test/1",
        canRequestAds = true,
        sdkDisabled = { killed.value },
        createBanner = { ctx, _ -> View(ctx) }
      )
    }
    composeRule.onNodeWithTag("compose_banner").assertExists()
    composeRule.runOnIdle { killed.value = true }
    composeRule.onNodeWithTag("compose_banner").assertDoesNotExist()
  }

  // No test in this suite initialises AdsHelper, so its lateinit config throws: the default kill
  // switch must then fail closed (hide the banner) instead of crashing or showing ads.
  @Test
  fun defaultKillSwitch_failsClosed_whenSdkNotInitialised() {
    composeRule.setContent {
      ComposeBannerAd(
        isVip = false,
        adUnitId = "ca-app-pub-test/1",
        canRequestAds = true,
        createBanner = { ctx, _ -> View(ctx) }
      )
    }
    composeRule.onNodeWithTag("compose_banner").assertDoesNotExist()
  }
}
