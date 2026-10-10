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
    @Volatile private var _syncCoordinator: com.example.data.sync.SyncCoordinator? = null
    @Volatile private var _networkMonitor: com.example.data.sync.NetworkMonitor? = null
    @Volatile private var _entitlementRepository: com.example.billing.EntitlementRepository? = null
    @Volatile private var _memoryMediaRepository: com.example.domain.media.MemoryMediaRepository? = null

    /** Singleton instance của [AdsManager] — throw [IllegalStateException] nếu chưa initialize */
    val adsManager: AdsManager
        get() = _adsManager ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")

    /** Singleton instance của [BillingManager] — throw [IllegalStateException] nếu chưa initialize */
    val billingManager: BillingManager
        get() = _billingManager ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")

    /** Singleton instance của [com.example.data.sync.SyncCoordinator] — throw [IllegalStateException] nếu chưa initialize */
    val syncCoordinator: com.example.data.sync.SyncCoordinator
        get() = _syncCoordinator ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")

    /** Singleton instance của [com.example.data.sync.NetworkMonitor] — throw [IllegalStateException] nếu chưa initialize */
    val networkMonitor: com.example.data.sync.NetworkMonitor
        get() = _networkMonitor ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")

    /** Singleton instance của [com.example.billing.EntitlementRepository] — throw [IllegalStateException] nếu chưa initialize */
    val entitlementRepository: com.example.billing.EntitlementRepository
        get() = _entitlementRepository ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")

    /** Singleton instance của [com.example.domain.media.MemoryMediaRepository] — throw [IllegalStateException] nếu chưa initialize */
    val memoryMediaRepository: com.example.domain.media.MemoryMediaRepository
        get() = _memoryMediaRepository ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")

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

        if (_entitlementRepository == null) {
            synchronized(this) {
                if (_entitlementRepository == null) {
                    val bm = _billingManager!!
                    _entitlementRepository = com.example.billing.EntitlementRepository(
                        appContext.getSharedPreferences("inlove_entitlement_prefs", Context.MODE_PRIVATE),
                        bm.hasSyncedOnce, bm.isVipUser, bm.activeProductId,
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
                    )
                }
            }
        }

        if (_adsManager == null) {
            synchronized(this) {
                if (_adsManager == null) {
                    _adsManager = AdsManagerImpl().also {
                        // Cached VIP is known synchronously; apply it before consent/SDK start can load anything.
                        _entitlementRepository?.let { repo -> it.setVipStatus(repo.isVipUser.value) }
                    }
                }
            }
        }

        if (_networkMonitor == null) {
            synchronized(this) {
                if (_networkMonitor == null) {
                    _networkMonitor = com.example.data.sync.NetworkMonitor(appContext)
                }
            }
        }

        if (_memoryMediaRepository == null) {
            synchronized(this) {
                if (_memoryMediaRepository == null) {
                    _memoryMediaRepository = com.example.data.media.FirebaseMemoryMediaRepository()
                }
            }
        }

        if (_syncCoordinator == null) {
            synchronized(this) {
                if (_syncCoordinator == null) {
                    val db = com.example.data.db.AppDatabase.getDatabase(appContext)
                    val fs = try {
                        if (com.google.firebase.FirebaseApp.getApps(appContext).isNotEmpty()) {
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        } else null
                    } catch (e: Exception) { null }
                    _syncCoordinator = com.example.data.sync.SyncCoordinator(
                        db.inLoveDao(), fs, kotlinx.coroutines.CoroutineScope(
                            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
                        )
                    )
                }
            }
        }
    }
}
