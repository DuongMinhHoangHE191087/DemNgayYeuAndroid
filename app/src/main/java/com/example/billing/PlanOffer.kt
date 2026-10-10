package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.android.billingclient.api.ProductDetails

/**
 * What the user will actually be charged for the selected product, derived from the eligible
 * Google Play offer rather than hard-coded text. [trialDays] is null when there is a trial but its
 * length cannot be expressed in whole days.
 */
data class PlanOffer(val price: String, val hasTrial: Boolean, val trialDays: Int?)

/** ISO-8601 billing period (P3D, P1W) to whole days; months/years are not exact day counts. */
internal fun isoPeriodToDays(period: String?): Int? {
  val m = Regex("""^P(\d+)([DW])$""").matchEntire(period ?: return null) ?: return null
  val n = m.groupValues[1].toInt()
  return if (m.groupValues[2] == "W") n * 7 else n
}

/** Pure mapping so it can be tested without a Play Billing object. */
internal fun planOfferOf(recurringPrice: String?, trialPeriod: String?, hasTrial: Boolean): PlanOffer? =
  recurringPrice?.let { PlanOffer(it, hasTrial, if (hasTrial) isoPeriodToDays(trialPeriod) else null) }

/** The offer the paywall both shows and purchases (free-trial offer first when Play offers one). */
fun ProductDetails.toPlanOffer(): PlanOffer? {
  oneTimePurchaseOfferDetails?.let { return PlanOffer(it.formattedPrice, false, null) }
  val phases = findBestOffer(preferFreeTrial = true)?.pricingPhases?.pricingPhaseList ?: return null
  val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
  val recurring = phases.lastOrNull { it.priceAmountMicros > 0 }
  return planOfferOf(recurring?.formattedPrice, trial?.billingPeriod, trial != null)
}

/**
 * The paywall's real Activity: an explicit one wins; otherwise unwrap the context chain.
 * (A context made with `createConfigurationContext` does NOT wrap the Activity, so unwrapping
 * alone returns null there and the purchase button silently does nothing.)
 */
fun resolveHostActivity(explicit: Activity?, context: Context): Activity? {
  if (explicit != null) return explicit
  var c: Context? = context
  while (c is ContextWrapper) {
    if (c is Activity) return c
    c = c.baseContext
  }
  return c as? Activity
}
