package com.example.billing

/**
 * What the user will actually be charged for the selected product, derived from the eligible
 * Google Play offer rather than hard-coded text. [trialDays] is null when there is a trial but its
 * length cannot be expressed in whole days.
 */
data class PlanOffer(val price: String, val hasTrial: Boolean, val trialDays: Int?)

/**
 * ISO-8601 billing period (P3D, P1W) to whole days; months/years are not exact day counts.
 * At most 3 digits, so a malformed value can neither throw in toInt() nor overflow n * 7.
 */
fun isoPeriodToDays(period: String?): Int? {
  val m = Regex("""^P(\d{1,3})([DW])$""").matchEntire(period ?: return null) ?: return null
  val n = m.groupValues[1].toInt()
  return if (m.groupValues[2] == "W") n * 7 else n
}

/** Pure mapping so it can be tested without a Play Billing object. */
fun planOfferOf(recurringPrice: String?, trialPeriod: String?, hasTrial: Boolean): PlanOffer? =
  recurringPrice?.let { PlanOffer(it, hasTrial, if (hasTrial) isoPeriodToDays(trialPeriod) else null) }
