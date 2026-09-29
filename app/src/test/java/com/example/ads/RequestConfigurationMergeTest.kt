package com.example.ads

import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RequestConfigurationMergeTest {

  @Test
  fun applyingTestDeviceIds_preservesAnExistingContentRatingAlreadySet() {
    // Simulate appplugin's AdsMobMy.startNetwork() having already run and set a content rating.
    MobileAds.setRequestConfiguration(
      RequestConfiguration.Builder()
        .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_MA)
        .build()
    )

    AdsManagerImpl.applyTestDeviceIds(listOf("TEST-DEVICE-ID"))

    val current = MobileAds.getRequestConfiguration()
    assert(current.maxAdContentRating == RequestConfiguration.MAX_AD_CONTENT_RATING_MA) {
      "a prior setRequestConfiguration()'s fields must survive — this is the exact bug appplugin's own AdsMobMy.kt history already hit"
    }
    assert(current.testDeviceIds.contains("TEST-DEVICE-ID"))
  }
}
