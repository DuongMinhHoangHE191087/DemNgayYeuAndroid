package com.example.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntitlementRepositoryTest {

  private fun prefs(context: Context) =
    context.getSharedPreferences("test_entitlement_prefs", Context.MODE_PRIVATE)

  @Test
  fun isVipUser_seedsFromPersistedCache_beforeAnyBillingResponseThisSession() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Simulate a prior session that ended VIP=true.
    prefs(context).edit().putBoolean("key_last_known_vip", true).putString("key_last_known_product_id", "vip_yearly").apply()

    val hasSyncedOnce = MutableStateFlow(false) // no network yet this session
    val isVip = MutableStateFlow(false)          // BillingManager's own un-synced default
    val activeProductId = MutableStateFlow<String?>(null)
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    val repo = EntitlementRepository(prefs(context), hasSyncedOnce, isVip, activeProductId, scope)

    assert(repo.isVipUser.value) { "must read the persisted cache immediately, not wait for hasSyncedOnce" }
    assert(repo.activeProductId.value == "vip_yearly")
  }

  @Test
  fun isVipUser_updatesAndPersists_onlyAfterHasSyncedOnceBecomesTrue() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val hasSyncedOnce = MutableStateFlow(false)
    val isVip = MutableStateFlow(false)
    val activeProductId = MutableStateFlow<String?>(null)
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    val repo = EntitlementRepository(prefs(context), hasSyncedOnce, isVip, activeProductId, scope)
    assert(!repo.isVipUser.value)

    // A real Play Billing answer arrives: VIP, then hasSyncedOnce flips.
    isVip.value = true
    activeProductId.value = "vip_lifetime"
    hasSyncedOnce.value = true

    assert(repo.isVipUser.value) { "must adopt the real answer once hasSyncedOnce is true" }
    assert(prefs(context).getBoolean("key_last_known_vip", false)) { "must persist the real answer for the next cold start" }
    assert(prefs(context).getString("key_last_known_product_id", null) == "vip_lifetime")
  }
}
