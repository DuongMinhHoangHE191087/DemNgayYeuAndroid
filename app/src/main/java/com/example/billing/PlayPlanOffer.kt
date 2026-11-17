package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.android.billingclient.api.ProductDetails

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

/** Helper extensions for selecting subscription offers and dynamic price formatting */
fun ProductDetails.findBestOffer(preferFreeTrial: Boolean = false): ProductDetails.SubscriptionOfferDetails? {
  val offers = subscriptionOfferDetails ?: return null
  if (offers.isEmpty()) return null

  if (preferFreeTrial) {
    // Locate offer containing a free trial phase (priceAmountMicros == 0)
    val trialOffer = offers.firstOrNull { offer ->
      offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
    }
    if (trialOffer != null) return trialOffer
  }

  // Default: base plan (without promotional offerId) or first available offer
  return offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
}

fun ProductDetails.getFormattedPrice(preferFreeTrial: Boolean = false): String? {
  oneTimePurchaseOfferDetails?.formattedPrice?.let { return it }
  val offer = findBestOffer(preferFreeTrial)
  val phases = offer?.pricingPhases?.pricingPhaseList
  return phases?.lastOrNull { it.priceAmountMicros > 0 }?.formattedPrice
    ?: phases?.firstOrNull()?.formattedPrice
}
