package com.example.di

import android.content.Context
import com.example.ads.AdsManager
import com.example.ads.AdsManagerImpl
import com.example.billing.BillingManager

/**
 * Singleton registry cung cấp [AdsManager] và [BillingManager] cho toàn bộ app.
 *
 * Lý do chọn Service Locator thay vì Hilt:
 *  - Project hiện tại chưa có `@HiltAndroidApp` Application class.
 *  - [AdsManagerImpl] cần được đăng ký làm [Application.ActivityLifecycleCallbacks]
 *    ở tầng Application — không phải ở ViewModel scope.
 *  - Thêm Hilt sẽ yêu cầu refactor toàn bộ ViewModel hierarchy → out of scope hiện tại.
 *
 * Thread-safety: Sử dụng `@Volatile` + double-checked locking để đảm bảo thread-safe
 * trong môi trường multi-thread (Coroutines + Main thread).
 *
 * Sử dụng:
 * ```kotlin
 * // Trong Application.onCreate() hoặc MainActivity.onCreate():
 * AppServiceLocator.initialize(applicationContext)
 *
 * // Lấy instance ở bất kỳ đâu:
 * val adsManager = AppServiceLocator.adsManager
 * val billingManager = AppServiceLocator.billingManager
 * ```
 */
object AppServiceLocator {

    @Volatile private var _adsManager: AdsManager? = null
    @Volatile private var _billingManager: BillingManager? = null

    /** Singleton instance của [AdsManager] — throw [IllegalStateException] nếu chưa initialize */
    val adsManager: AdsManager
        get() = _adsManager ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")

    /** Singleton instance của [BillingManager] — throw [IllegalStateException] nếu chưa initialize */
    val billingManager: BillingManager
        get() = _billingManager ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")

    /**
     * Khởi tạo tất cả services. Phải gọi sớm nhất có thể — trong `Application.onCreate()`
     * hoặc `MainActivity.onCreate()` trước khi `setContent {}`.
     *
     * Idempotent: Gọi nhiều lần an toàn, chỉ tạo instance một lần duy nhất.
     *
     * @param context Application Context — dùng `applicationContext` để tránh leak.
     */
    fun initialize(context: Context) {
        val appContext = context.applicationContext

        // Double-checked locking để thread-safe
        if (_billingManager == null) {
            synchronized(this) {
                if (_billingManager == null) {
                    _billingManager = BillingManager(appContext)
                }
            }
        }

        if (_adsManager == null) {
            synchronized(this) {
                if (_adsManager == null) {
                    _adsManager = AdsManagerImpl()
                }
            }
        }
    }
}
