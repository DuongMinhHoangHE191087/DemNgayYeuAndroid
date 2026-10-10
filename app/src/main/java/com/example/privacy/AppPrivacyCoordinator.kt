package com.example.privacy

import android.app.Activity
import android.content.Context
import com.app.plugin.consent.ConsentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single publisher of the UMP consent answer for the whole app.
 *
 * Ads, banners and the Settings "privacy options" row all read these flows instead of taking a
 * one-time snapshot, so a consent withdrawal or a new answer reaches them immediately. The app has
 * no first-party analytics; the appplugin gates its own SDK startup with the same UMP answer.
 */
object AppPrivacyCoordinator {
  private val _canRequestAds = MutableStateFlow(false)
  /** True only when UMP has a stored answer that allows ad requests (false while unresolved). */
  val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

  private val _privacyOptionsRequired = MutableStateFlow(false)
  val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

  @Volatile private var appContext: Context? = null
  private val onConsentUpdated: () -> Unit = { refresh() }

  /** Idempotent; subscribes once to consent updates and publishes the stored state. */
  fun attach(context: Context) {
    synchronized(this) {
      if (appContext == null) {
        appContext = context.applicationContext
        runCatching { ConsentManager.addConsentUpdatedListener(onConsentUpdated) }
      }
    }
    refresh()
  }

  fun refresh() {
    val ctx = appContext ?: return
    _canRequestAds.value = ConsentManager.canStartAdSdks(ctx)
    _privacyOptionsRequired.value = runCatching { ConsentManager.isPrivacyOptionsRequired(ctx) }.getOrDefault(false)
  }

  /** Opens the UMP privacy options form and republishes the state when it closes. */
  fun showPrivacyOptions(activity: Activity) {
    runCatching { ConsentManager.showPrivacyOptions(activity) { refresh() } }
      .onFailure { android.util.Log.d("AppPrivacy", "showPrivacyOptions failed: ${it.message}") }
  }
}
