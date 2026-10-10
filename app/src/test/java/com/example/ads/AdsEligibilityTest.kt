package com.example.ads

import androidx.test.core.app.ApplicationProvider
import com.example.privacy.AppPrivacyCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdsEligibilityTest {

  @Test
  fun vipNeverGetsAds() {
    assertFalse(AdsManagerImpl.isEligible(isVip = true, canRequestAds = true))
  }

  @Test
  fun noConsentNeverGetsAds() {
    assertFalse(AdsManagerImpl.isEligible(isVip = false, canRequestAds = false))
  }

  @Test
  fun blankAdUnitNeverGetsAds() {
    assertFalse(AdsManagerImpl.isEligible(isVip = false, canRequestAds = true, adUnitId = " "))
  }

  @Test
  fun freeUserWithConsentAndUnitGetsAds() {
    assertTrue(AdsManagerImpl.isEligible(isVip = false, canRequestAds = true, adUnitId = "ca-app-pub-x/1"))
  }

  @Test
  fun v1ShipsBannerOnly() {
    assertFalse(AdsManagerImpl.INTERSTITIAL_ENABLED)
    assertFalse(AdsManagerImpl.APP_OPEN_ENABLED)
  }

  @Test
  fun unresolvedConsentIsPublishedAsNotAllowedAndPrivacyRowHidden() {
    AppPrivacyCoordinator.attach(ApplicationProvider.getApplicationContext())
    assertFalse("no stored UMP answer in a fresh process must not allow ads", AppPrivacyCoordinator.canRequestAds.value)
    assertEquals(false, AppPrivacyCoordinator.privacyOptionsRequired.value)
  }
}
