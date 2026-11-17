package com.example.di

import android.content.Context
import com.example.ads.AdsManager
import com.example.ads.AdsManagerImpl
import com.example.billing.BillingManager
import kotlinx.coroutines.tasks.await

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

    // ponytail: identity of the Application context last initialized against. In production there is
    // exactly one Application for the process, so this never changes and initialize() stays a true
    // no-op after the first call. In Robolectric unit tests, each test class gets its OWN fresh
    // Application/AppDatabase, but this `object` is one JVM-wide singleton shared by every test class
    // in the same Gradle-forked JVM — so without this check, whichever test ran first permanently wins:
    // syncCoordinator/profileSync keep a stale InLoveDao bound to a database another test already closed.
    // Root cause of "GiftReminderCrudTest passes alone, fails in the full suite".
    @Volatile private var _initializedContextId: Int? = null
    @Volatile private var _adsManager: AdsManager? = null
    @Volatile private var _billingManager: BillingManager? = null
    @Volatile private var _syncCoordinator: com.example.data.sync.SyncCoordinator? = null
    @Volatile private var _profileSync: com.example.data.sync.ProfileSync? = null
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

    /** [com.example.data.sync.ProfileSync]; null khi Firebase chưa khởi tạo hoặc [com.example.domain.DataResidencyPolicy.profileOnline] tắt. */
    val profileSync: com.example.data.sync.ProfileSync?
        get() = _profileSync

    /**
     * Nối vòng đời phiên đăng nhập với [syncCoordinator] và [profileSync]; [onRemoteChange] làm mới trạng thái ghép đôi
     * khi bên kia đổi, [applyRemoteProfile] ghi hồ sơ kéo từ server vào Room. Chưa initialize thì getter ném lỗi và AuthRepository bắt lại (sync không chạy).
     */
    fun sessionHooks(
        onRemoteChange: suspend () -> Unit,
        applyRemoteProfile: suspend (String, com.example.data.sync.ProfileProjection, String) -> Boolean
    ): com.example.data.auth.SessionHooks =
        // ponytail: `start` lần đầu thắng (`start` cùng uid là no-op), nên lambda của VM đã bắt đầu phiên được dùng tới hết phiên;
        // VM đó bị huỷ thì repository của nó vẫn ghi đúng Room dùng chung.
        object : com.example.data.auth.SessionHooks {
            override fun onSessionStarted(uid: String) {
                syncCoordinator.start(uid, onRemoteChange)
                profileSync?.start(uid, applyRemoteProfile)
            }
            override fun onSessionEnded() {
                syncCoordinator.stop()
                profileSync?.stop()
            }
            override fun onAccountDeleting() {
                profileSync?.stop()
            }
        }

    /** Social sign-in (Facebook qua OAuth web flow của Firebase); Activity lấy từ CurrentActivityProvider. */
    val socialAuthGateway: com.example.data.auth.SocialAuthGateway by lazy {
        com.example.data.auth.AndroidSocialAuthGateway()
    }

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
        val appContextId = System.identityHashCode(appContext)

        // Context đổi (chỉ xảy ra khi test tạo Application mới mỗi lớp test) -> rebuild toàn bộ,
        // tránh syncCoordinator/profileSync giữ InLoveDao trỏ vào DB cũ đã bị đóng ở test khác.
        if (_initializedContextId != appContextId) {
            synchronized(this) {
                if (_initializedContextId != appContextId) {
                    _syncCoordinator?.stop()
                    _profileSync?.stop()
                    _billingManager = null
                    _entitlementRepository = null
                    _adsManager = null
                    _networkMonitor = null
                    _memoryMediaRepository = null
                    _syncCoordinator = null
                    _profileSync = null
                    _initializedContextId = appContextId
                }
            }
        }

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
                    _memoryMediaRepository = com.example.data.media.CloudMemoryMediaRepository()
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
                    val syncScope = kotlinx.coroutines.CoroutineScope(
                        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO +
                            // Lỗi lọt ra ngoài chỉ ghi log, không làm sập app; CancellationException không bao giờ tới đây.
                            kotlinx.coroutines.CoroutineExceptionHandler { _, t -> android.util.Log.e("InLove", "uncaught in app scope", t) }
                    )
                    _profileSync = if (fs != null && com.example.domain.DataResidencyPolicy.profileOnline) {
                        com.example.data.sync.ProfileSync(
                            observeRow = db.inLoveDao()::getOnlineUserByUid,
                            prefs = appContext.getSharedPreferences(com.example.data.sync.ProfileSync.PREFS, Context.MODE_PRIVATE),
                            scope = syncScope,
                            fetch = { uid ->
                                val snap = fs.document("users/$uid").get(com.google.firebase.firestore.Source.SERVER).await()
                                if (snap.exists()) snap.data else null
                            },
                            // Ghi đè toàn bộ (không merge); `data` chỉ đến từ toRemoteMap().
                            push = { uid, data, onDone ->
                                fs.document("users/$uid")
                                    .set(data + ("updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                                    .addOnSuccessListener { onDone(com.example.data.sync.ProfileSync.PushOutcome.ACKED) }
                                    .addOnFailureListener { e ->
                                        android.util.Log.w("ProfileSync", "profile push failed: ${e.javaClass.simpleName}")
                                        val denied = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                                            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                                        onDone(
                                            if (denied) com.example.data.sync.ProfileSync.PushOutcome.DENIED
                                            else com.example.data.sync.ProfileSync.PushOutcome.FAILED
                                        )
                                    }
                            },
                            signedInUid = { runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() }
                        )
                    } else null
                    _syncCoordinator = com.example.data.sync.SyncCoordinator(db.inLoveDao(), fs, syncScope)
                }
            }
        }
    }
}
