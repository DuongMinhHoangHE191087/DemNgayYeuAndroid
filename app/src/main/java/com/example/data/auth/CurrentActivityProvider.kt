package com.example.data.auth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks the resumed Activity so non-UI code (the OAuth web flow) can get one without the
 * ViewModel holding a reference. Weakly held: never keeps an Activity alive.
 */
object CurrentActivityProvider {
  @Volatile private var registeredApp: Application? = null
  @Volatile private var callbacks: Application.ActivityLifecycleCallbacks? = null
  @Volatile private var current: WeakReference<Activity>? = null

  val isRegistered: Boolean get() = registeredApp != null

  fun current(): Activity? = current?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

  @Synchronized
  fun register(app: Application) {
    if (registeredApp === app) return
    reset()
    val cb = object : Application.ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) { current = WeakReference(activity) }
      override fun onActivityPaused(activity: Activity) { clear(activity) }
      override fun onActivityDestroyed(activity: Activity) { clear(activity) }
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
    app.registerActivityLifecycleCallbacks(cb)
    callbacks = cb
    registeredApp = app
  }

  private fun clear(activity: Activity) {
    if (current?.get() === activity) current = null
  }

  @Synchronized
  private fun reset() {
    callbacks?.let { registeredApp?.unregisterActivityLifecycleCallbacks(it) }
    callbacks = null
    registeredApp = null
    current = null
  }
}
