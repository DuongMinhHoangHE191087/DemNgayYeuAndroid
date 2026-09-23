package com.example

import android.util.Log
import com.app.plugin.MonetizationSdk
import com.app.plugin.app.AppPluginBase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck

class InLoveApplication : AppPluginBase() {
    private val isRunningInTest: Boolean by lazy {
        try {
            Class.forName("org.robolectric.Robolectric") != null
        } catch (_: Throwable) {
            false
        }
    }

    override fun onCreate() {
        if (isRunningInTest) {
            try {
                FirebaseApp.initializeApp(this)
            } catch (_: Throwable) {}
            return
        }

        try {
            super.onCreate()
        } catch (t: Throwable) {
            Log.e("InLoveApp", "AppPluginBase onCreate failed: ${t.message}")
        }
        FirebaseApp.initializeApp(this)

        val report = MonetizationSdk.configure(this) {
            brainEnabled = true
            brainRolloutFraction = 1.0
            childDirected = false

            inappProducts = listOf("inlove_vip_lifetime")
            subsProducts = listOf("inlove_vip_monthly", "inlove_vip_yearly")
            removeAdsProducts = setOf("inlove_vip_monthly", "inlove_vip_yearly", "inlove_vip_lifetime")
        }
        Log.i("InLoveApp", "MonetizationSdk initialized:\n${report.describe()}")

        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            try {
                // Use reflection to load DebugAppCheckProviderFactory safely
                // because firebase-appcheck-debug is a debugImplementation dependency
                val debugFactoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                val getInstanceMethod = debugFactoryClass.getMethod("getInstance")
                val debugFactory = getInstanceMethod.invoke(null) as AppCheckProviderFactory
                appCheck.installAppCheckProviderFactory(debugFactory)
                Log.d("InLoveApp", "Firebase App Check initialized with DebugAppCheckProviderFactory")
            } catch (e: Exception) {
                Log.w("InLoveApp", "Could not initialize DebugAppCheckProviderFactory: ${e.message}")
            }
        } else {
            try {
                // Use reflection to load PlayIntegrityAppCheckProviderFactory safely
                val integrityFactoryClass = Class.forName("com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory")
                val getInstanceMethod = integrityFactoryClass.getMethod("getInstance")
                val integrityFactory = getInstanceMethod.invoke(null) as AppCheckProviderFactory
                appCheck.installAppCheckProviderFactory(integrityFactory)
                Log.i("InLoveApp", "Firebase App Check initialized with PlayIntegrityAppCheckProviderFactory")
            } catch (e: Exception) {
                Log.w("InLoveApp", "Could not initialize PlayIntegrityAppCheckProviderFactory: ${e.message}")
            }
        }
    }
}
