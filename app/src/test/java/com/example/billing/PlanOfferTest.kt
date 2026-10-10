package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanOfferTest {

  @Test
  fun isoPeriodsConvertToWholeDays() {
    assertEquals(3, isoPeriodToDays("P3D"))
    assertEquals(14, isoPeriodToDays("P2W"))
    assertNull(isoPeriodToDays("P1M"))
    assertNull(isoPeriodToDays("P1Y"))
    assertNull(isoPeriodToDays(null))
  }

  @Test
  fun offerWithoutPriceIsNull() {
    assertNull(planOfferOf(null, "P7D", true))
  }

  @Test
  fun trialOfferKeepsLengthAndPaidOfferDropsIt() {
    assertEquals(PlanOffer("99.000đ", true, 7), planOfferOf("99.000đ", "P7D", true))
    assertEquals(PlanOffer("99.000đ", false, null), planOfferOf("99.000đ", "P7D", false))
  }

  @Test
  fun explicitActivityWinsOverContext() {
    val activity = Robolectric.buildActivity(Activity::class.java).get()
    val other = ApplicationProvider.getApplicationContext<Context>()
    assertSame(activity, resolveHostActivity(activity, other))
  }

  @Test
  fun activityIsFoundThroughWrappersButNotThroughConfigurationContext() {
    val activity = Robolectric.buildActivity(Activity::class.java).get()
    assertSame(activity, resolveHostActivity(null, ContextWrapper(ContextWrapper(activity))))
    // createConfigurationContext does not wrap the Activity: only the explicit one can be used
    val cfg = activity.createConfigurationContext(activity.resources.configuration)
    assertNull(resolveHostActivity(null, cfg))
  }
}
