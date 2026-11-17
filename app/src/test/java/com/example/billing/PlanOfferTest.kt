package com.example.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
  fun multiDigitPeriodsAreWholeDays() {
    assertEquals(10, isoPeriodToDays("P10D"))
    assertEquals(84, isoPeriodToDays("P12W"))
    assertEquals(6993, isoPeriodToDays("P999W"))
  }

  @Test
  fun malformedPeriodsAreNullAndNeverThrow() {
    listOf("", "P", "PD", "PW", "P1000D", "P99999999999D", "p3d", " P3D", "P3D ", "P1W2D", "PT3D", "P-1D", "3D")
      .forEach { assertNull(isoPeriodToDays(it), "should reject: '$it'") }
  }

  @Test
  fun offerWithoutPriceIsNull() {
    assertNull(planOfferOf(null, "P7D", true))
  }

  @Test
  fun trialOfferKeepsLengthAndPaidOfferDropsIt() {
    assertEquals(PlanOffer("99.000đ", true, 7), planOfferOf("99.000đ", "P7D", true))
    assertEquals(PlanOffer("99.000đ", false, null), planOfferOf("99.000đ", "P7D", false))
    // a trial whose length is not whole days still counts as a trial, just without a day count
    assertEquals(PlanOffer("99.000đ", true, null), planOfferOf("99.000đ", "P1M", true))
    assertEquals(PlanOffer("99.000đ", true, null), planOfferOf("99.000đ", null, true))
  }
}
