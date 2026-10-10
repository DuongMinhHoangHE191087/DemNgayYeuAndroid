package com.example.billing

import com.android.billingclient.api.Purchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VipEntitlementResolverTest {
  private fun purchase(product: String, state: Int = 1, suspended: Boolean = false, acked: Boolean = true) = Purchase(
    """{"orderId":"o-$product","packageName":"p","productIds":["$product"],"purchaseTime":1,"purchaseState":$state,
       "purchaseToken":"tok-$product","acknowledged":$acked,"autoRenewing":false,"suspended":$suspended}""",
    "sig"
  )

  @Test
  fun failedQueryIsNotDefinitiveAndNeverRevokes() {
    val e = resolveVipEntitlement(null, emptyList())
    assertFalse(e.definitive)
    assertFalse(e.shouldRevoke)
  }

  @Test
  fun bothQueriesEmptyRevokes() {
    val e = resolveVipEntitlement(emptyList(), emptyList())
    assertTrue(e.shouldRevoke)
    assertNull(e.best)
  }

  @Test
  fun unrelatedProductsAreIgnored() {
    val e = resolveVipEntitlement(listOf(purchase("some_other_sku")), emptyList())
    assertFalse(e.isVip)
    assertTrue(e.shouldRevoke)
  }

  @Test
  fun suspendedSubscriptionDoesNotGrantButLifetimeStillDoes() {
    val e = resolveVipEntitlement(
      listOf(purchase(VipProductIds.YEARLY, suspended = true)),
      listOf(purchase(VipProductIds.LIFETIME))
    )
    assertTrue(e.isVip)
    assertEquals(setOf(VipProductIds.LIFETIME), e.productIds)
    assertEquals(VipProductIds.LIFETIME, e.best)
  }

  @Test
  fun suspendedSubscriptionAloneRevokes() {
    val e = resolveVipEntitlement(listOf(purchase(VipProductIds.MONTHLY, suspended = true)), emptyList())
    assertFalse(e.isVip)
    assertTrue(e.shouldRevoke)
  }

  @Test
  fun pendingPurchaseDoesNotGrant() {
    // JSON purchaseState 4 == PENDING
    val e = resolveVipEntitlement(listOf(purchase(VipProductIds.MONTHLY, state = 4)), emptyList())
    assertFalse(e.isVip)
  }

  @Test
  fun yearlyBeatsMonthlyAndLifetimeBeatsBoth() {
    val subs = listOf(purchase(VipProductIds.MONTHLY), purchase(VipProductIds.YEARLY))
    assertEquals(VipProductIds.YEARLY, resolveVipEntitlement(subs, emptyList()).best)
    assertEquals(VipProductIds.LIFETIME, resolveVipEntitlement(subs, listOf(purchase(VipProductIds.LIFETIME))).best)
  }

  @Test
  fun oneFailedQueryStillGrantsFromTheOtherButCannotRevoke() {
    val granted = resolveVipEntitlement(listOf(purchase(VipProductIds.YEARLY)), null)
    assertTrue(granted.isVip)
    assertFalse(granted.definitive)
    assertFalse(resolveVipEntitlement(emptyList(), null).shouldRevoke)
  }
}
