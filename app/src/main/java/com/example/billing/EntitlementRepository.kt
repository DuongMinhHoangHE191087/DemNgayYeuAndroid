package com.example.billing

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Single source of truth for "is this user VIP" across the app. Wraps [BillingManager]'s own
 * StateFlows (still the only caller of com.app.plugin.iap.Entitlements.grant/revoke/sync - this
 * class does not duplicate that call) with one addition: a SharedPreferences-backed cache of the
 * last real answer, read synchronously at construction. Without this, a VIP user who opens the
 * app with no network sees [BillingManager.hasSyncedOnce] never become true this session, and
 * every direct reader of [BillingManager.isVipUser] sees `false` - not "unknown", `false` - for
 * the whole session. This class instead starts at the last known real answer and only overwrites
 * it once a fresh, real answer actually arrives.
 */
class EntitlementRepository(
  private val prefs: SharedPreferences,
  hasSyncedOnceFlow: StateFlow<Boolean>,
  isVipFlow: StateFlow<Boolean>,
  activeProductIdFlow: StateFlow<String?>,
  scope: CoroutineScope
) {
  private val _isVipUser = MutableStateFlow(prefs.getBoolean(KEY_LAST_KNOWN_VIP, false))
  val isVipUser: StateFlow<Boolean> = _isVipUser.asStateFlow()

  private val _activeProductId = MutableStateFlow(prefs.getString(KEY_LAST_KNOWN_PRODUCT_ID, null))
  val activeProductId: StateFlow<String?> = _activeProductId.asStateFlow()

  init {
    scope.launch {
      combine(hasSyncedOnceFlow, isVipFlow, activeProductIdFlow) { synced, vip, productId ->
        Triple(synced, vip, productId)
      }.collect { (synced, vip, productId) ->
        if (!synced) return@collect // keep showing the cached answer until a real one arrives
        _isVipUser.value = vip
        _activeProductId.value = productId
        prefs.edit()
          .putBoolean(KEY_LAST_KNOWN_VIP, vip)
          .putString(KEY_LAST_KNOWN_PRODUCT_ID, productId)
          .apply()
      }
    }
  }

  companion object {
    private const val KEY_LAST_KNOWN_VIP = "key_last_known_vip"
    private const val KEY_LAST_KNOWN_PRODUCT_ID = "key_last_known_product_id"
  }
}
