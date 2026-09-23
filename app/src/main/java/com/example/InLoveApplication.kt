package com.example

import android.util.Log
import com.app.plugin.MonetizationSdk
import com.app.plugin.app.AppPluginBase
import com.example.billing.VipProductIds
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

        // Firebase + App Check PHẢI được cài đặt trước khi AppPluginBase.onCreate() chạy:
        // AppPluginBase -> AppPluginManager.initPlugin() tự gọi FirebaseApp.initializeApp()
        // rồi bắn ngay FirHelper.fetchRemoteCf{}/fetchFireStore{} — nếu App Check chưa có
        // provider factory tại thời điểm đó, những request đầu tiên đi không kèm token App
        // Check (mất tác dụng bảo vệ enforcement, nếu bật). Gọi FirebaseApp.initializeApp()
        // hai lần là an toàn (idempotent, và AppPluginManager tự bọc Throwable quanh lần gọi
        // thứ hai của nó).
        FirebaseApp.initializeApp(this)
        installAppCheckProviderFactory()

        try {
            super.onCreate()
        } catch (t: Throwable) {
            Log.e("InLoveApp", "AppPluginBase onCreate failed: ${t.message}")
        }

        // Nguồn sự thật duy nhất cho Product ID billing: VipProductIds (khớp BillingManager,
        // billing client THẬT duy nhất chạy purchase flow). MonetizationSdk/IapHelper phía
        // appplugin chỉ dùng các ID này để đồng bộ Entitlements (tắt quảng cáo cho VIP), không
        // tự chạy một luồng mua hàng song song — xem VipProductIds.kt để biết chi tiết.
        val report = MonetizationSdk.configure(this) {
            brainEnabled = true
            brainRolloutFraction = 1.0
            childDirected = false

            inappProducts = listOf(VipProductIds.LIFETIME)
            subsProducts = listOf(VipProductIds.MONTHLY, VipProductIds.YEARLY)
            removeAdsProducts = setOf(VipProductIds.MONTHLY, VipProductIds.YEARLY, VipProductIds.LIFETIME)
        }
        Log.i("InLoveApp", "MonetizationSdk initialized:\n${report.describe()}")
    }

    private fun installAppCheckProviderFactory() {
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
            } catch (e: Throwable) {
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
            } catch (e: Throwable) {
                Log.w("InLoveApp", "Could not initialize PlayIntegrityAppCheckProviderFactory: ${e.message}")
            }
        }
    }
}
