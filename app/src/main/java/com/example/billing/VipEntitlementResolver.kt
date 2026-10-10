package com.example.billing

import com.android.billingclient.api.Purchase

/** Outcome of combining the SUBS and INAPP purchase queries into one VIP answer. */
data class VipEntitlement(
  /** Purchases that currently grant VIP: PURCHASED, not suspended, VIP product only. */
  val active: List<Purchase>,
  val productIds: Set<String>,
  /** Highest-value owned product (lifetime > yearly > monthly), null when none. */
  val best: String?,
  /** True only when BOTH queries succeeded; a failed query must never revoke a stored VIP. */
  val definitive: Boolean
) {
  val isVip: Boolean get() = active.isNotEmpty()
  /** Both queries answered and nothing grants VIP: safe to revoke. */
  val shouldRevoke: Boolean get() = definitive && active.isEmpty()
}

/** Pure aggregation, kept free of BillingClient so it can be unit-tested. `null` = query failed. */
fun resolveVipEntitlement(subs: List<Purchase>?, inApps: List<Purchase>?): VipEntitlement {
  val active = ((subs ?: emptyList()) + (inApps ?: emptyList())).filter {
    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
      !it.isSuspended &&
      it.products.any { id -> id in VipProductIds.ALL }
  }
  val ids = active.flatMap { it.products }.filter { it in VipProductIds.ALL }.toSet()
  val best = when {
    VipProductIds.LIFETIME in ids -> VipProductIds.LIFETIME
    VipProductIds.YEARLY in ids -> VipProductIds.YEARLY
    VipProductIds.MONTHLY in ids -> VipProductIds.MONTHLY
    else -> null
  }
  return VipEntitlement(active, ids, best, definitive = subs != null && inApps != null)
}
