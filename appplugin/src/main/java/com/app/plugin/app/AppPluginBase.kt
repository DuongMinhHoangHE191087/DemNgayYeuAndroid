package com.app.plugin.app

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.consent.ConsentState
import com.app.plugin.license.LicenseGate
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.LogEventServerHelper
import com.app.plugin.logevent.TelemetryConsentGate
import com.app.plugin.until.Constants
import com.app.plugin.until.PreferenceHelper
import com.app.plugin.until.PrefsWarmup

public open class AppPluginBase : MultiDexApplication(), Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {
    private var currentActivity: Activity? = null
    private var mainAppActivity: Activity? = null
    private var isSStart: Boolean = true
    private var launcherClass: String? = null   // 2.4.1 HOSTFIX: launcher + "<gói lớp Application>." phân giải một lần ([resolveHostRules])
    private var appPkgDot: String? = null
    private var hostRulesResolved = false
    private val adSdkPrefixes = arrayOf(
        "com.google.android.gms.ads.", "com.facebook.ads.", "com.applovin.", "com.unity3d.", "com.google.android.ump.",
        "com.bytedance.", "com.pangle.", "com.mbridge.", "com.vungle.", "com.ironsource.", "com.inmobi.",
    )

    /** Thời điểm app vào nền lần gần nhất; 0 nghĩa là chưa từng vào nền trong process này. */
    private var lastBackgroundAtMs: Long = 0

    public companion object {
        /** Khoảng nghỉ tính là phiên mới — trùng ngưỡng phiên của `LogEventServer`. */
        private const val SESSION_GAP_MS = 30 * 60 * 1000L

        /** Khoá lưu tên `ConsentTier` giữa các process (xem `ConsentState.restore`). */
        private const val KEY_CONSENT_TIER = "consent_tier"

        /**
         * Prefs lúc mở app (2.1.0 P1, D10): `TIME_OPEN` + 1, `EXPERIMENT_GROUP` ghi trên luồng gọi (`initPlugin` đọc ngay); warm-up kịp →
         * một edit 11 khoá như cũ, hết hạn → 9 cờ (0 nơi đọc: SDK, demo, app tiêu thụ) qua [PrefsWarmup.onLoaded], luồng warm-up sau khi nạp.
         */
        internal fun resetOnOpen(prefs: PreferenceHelper) {
            val sync = mapOf(Constants.Common.TIME_OPEN to prefs.get(Constants.Common.TIME_OPEN, 0) + 1, Constants.EXPERIMENT_GROUP to 4)
            val flags = listOf(
                Constants.Common.IS_FETCH_FIREBASE, Constants.InitAds.NT_INTRO_LOADED, Constants.InitAds.NT_LANGUAGE_2_LOADED,
                Constants.InitAds.NT_LANGUAGE_LOADED, Constants.InitAds.FULL_SPLASH_LOADED, Constants.InitAds.FULL_2_SPLASH_LOADED,
                Constants.InitAds.RECT_SPLASH_LOADED, Constants.InitAds.FULL_SPLASH_SHOWED, Constants.InitAds.IS_LOAD_FULL,
            ).associateWith { false }
            if (PrefsWarmup.isLoaded()) return prefs.setValues(sync + flags)
            prefs.setValues(sync)
            PrefsWarmup.onLoaded { prefs.setValues(flags) }
        }
    }

    /**
     * Bắt đầu nạp sẵn các file SharedPreferences trên luồng nền (`PrefsWarmup`), trước `onCreate`: `onCreate` chờ
     * tối đa 250 ms trước lần đọc đầu, nên main không đọc đĩa lúc cold start (DOC/65 G2). Process phụ không chạy SDK
     * (`onCreate` trả về sớm), nên bỏ qua khi biết tên process (API 28+); dưới 28 vẫn start.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Application.getProcessName() != base.packageName) return
        PrefsWarmup.start(base)
    }

    override fun onCreate() {
        super<MultiDexApplication>.onCreate()
        if (!isMainProcess()) return
        com.app.plugin.iap.BillingProxyCrashGuard.install() // F9/D29: trước mọi ProxyBillingActivity dựng lại (idempotent)
        val startedAtMs = SystemClock.elapsedRealtime()
        // Chờ lần nạp prefs của attachBaseContext (≤ 250 ms, tính vào ngân sách main thread ở cuối hàm).
        PrefsWarmup.await()
        PreferenceHelper.getInstance().init(this)
        restoreConsentTier()
        resetOnOpen(PreferenceHelper.getInstance())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getProcessName()
            if (packageName != processName) {
                WebView.setDataDirectorySuffix(processName)
                Log.d("AppPlugin", "onCreat AppPluginBase 1 name=$processName")
            } else {
                Log.d("AppPlugin", "onCreat AppPluginBase 2 name=$processName")
                initPlugin()
            }
        } else {
            Log.d("AppPlugin", "onCreat AppPluginBase 3")
            initPlugin()
        }
        // Ngân sách main thread của SDK lúc mở app (DOC/65 G2): gồm cả initPlugin.
        val mainThreadMs = SystemClock.elapsedRealtime() - startedAtMs
        com.app.plugin.ads.SdkHealth.initMs = mainThreadMs
        Log.d("AppPlugin", "AppPluginBase.onCreate main-thread ms=${mainThreadMs}")
    }

    /**
     * Tầng consent của phiên trước có hiệu lực ngay khi mở process, trước khi UMP trả lời: người đã
     * bấm "Do not consent" không được chạy brain/CRM/offerwall trong khoảng chờ đó.
     */
    private fun restoreConsentTier() {
        ConsentState.installStore(object : ConsentState.TierStore {
            override fun save(name: String) = PreferenceHelper.getInstance().setValue(KEY_CONSENT_TIER, name)
            override fun load(): String? = PreferenceHelper.getInstance().get(KEY_CONSENT_TIER, "").ifEmpty { null }
        })
        ConsentState.restore()
        // Log thẳng như các dòng khác của onCreate: cờ LogUtil chỉ bật sau, ở AdsMobMy.initAd.
        Log.d("AppPlugin", "consent tier restored=${ConsentState.tier()}")
    }

    private fun isMainProcess(): Boolean {
        return try {
            val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getProcessName()
            } else {
                val pid = android.os.Process.myPid()
                val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
                manager?.runningAppProcesses?.find { it.pid == pid }?.processName
            }
            processName == packageName
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun initPlugin() {
        isSStart = true
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val t0 = SystemClock.elapsedRealtime()
        AppPluginManager.instance.initPlugin(this, isDebug)
        val t1 = SystemClock.elapsedRealtime()
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        val (baseUrl, apiKey) = dataBucketConfig()
        val t2 = SystemClock.elapsedRealtime()
        // Helper trước: LogEventServer.init replay decision log, body đọc app_name/country/session_id… (LogEventInitOrderContractTest).
        LogEventServerHelper.init(this)
        // Trước init: replay decision log và first_open/session_start chờ tầng consent (O3 (a), LogEventInitOrderContractTest).
        TelemetryConsentGate.install(this)
        val t3 = SystemClock.elapsedRealtime()
        LogEventServer.init(baseUrl, apiKey, isDebug)
        LogEventServer.sessionStart()
        val t4 = SystemClock.elapsedRealtime()
        // 1.6.3 Task 1 (review cuối 1.6.2 §4.2): main-thread ms tách theo bước, cổng đọc thành `initPlugin ms=`. Log thẳng.
        Log.d("AppPlugin", "initPlugin ms: manager=${t1 - t0} meta=${t2 - t1} helper+gate=${t3 - t2} logevent=${t4 - t3} total=${t4 - t0}")
    }

    /** `DATABUCKETS_*` từ meta-data (IPC PackageManager, bước `meta`; lỗi → `Constants.DataBucket`); cùng bundle khởi động [LicenseGate] (SA1). */
    private fun dataBucketConfig(): Pair<String, String> {
        var baseUrl = Constants.DataBucket.BASE_URL
        var apiKey = Constants.DataBucket.API_KEY
        var license: String? = LicenseGate.READ_FAILED   // đọc bundle lỗi → INVALID (error), không phải missing
        try {
            val bundle = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA).metaData
            bundle?.getString("DATABUCKETS_BASE_URL")?.takeIf { it.isNotEmpty() }?.let { baseUrl = it }
            bundle?.getString("DATABUCKETS_API_KEY")?.takeIf { it.isNotEmpty() }?.let { apiKey = it }
            license = bundle?.getString(LicenseGate.META_KEY)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        LicenseGate.start(this, license)   // 0 IPC thêm trên main: xác minh + đọc cert trên luồng appplugin-license
        return baseUrl to apiKey
    }

    /**
     * Khởi tạo Meta SDK (app events, attribution cho chiến dịch Meta) sau consent. Gọi lúc nào
     * cũng được, gọi lại là no-op: SDK tự hoãn tới khi UMP cho phép, như `AdsMobMy.startSdkInit`.
     * Không làm gì khi app không khai `facebook-core` hoặc `facebook_app_id` còn là placeholder.
     * Xem `MetaSdkStarter`.
     */
    public fun initMetaSdkAfterConsent() {
        AdSdkStartup.requestMetaSdk(this)
    }

    /** 2.5.0 LAM1: `AdsHelper.setAutoShowOpenAdOnForeground(false)` ⇒ bỏ lượt tự hiện open ad (kiểm lúc hẹn và lúc chạy). */
    private fun autoShowOpenAd(): Boolean = AdsHelper.instance.autoShowOpenAdOnForeground()
        .also { if (!it) Log.i(com.app.plugin.until.LogUtil.TAG, "ads open ad auto-show on foreground skipped (app)") }

    override fun onStart(owner: LifecycleOwner) {
        com.app.plugin.logevent.SessionTracker.onForeground()
        // Quay lại foreground sau khi rời đi đủ lâu = phiên mới với brain. Dùng đúng ngưỡng
        // 30 phút mà LogEventServer đang dùng để định nghĩa phiên, để hai hệ thống không đếm
        // phiên lệch nhau khi đối soát log. Quay lại nhanh (chuyển app, nhận cuộc gọi) KHÔNG
        // phải phiên mới — cấp lại ngân sách ở đó sẽ biến trần mỗi phiên thành vô nghĩa.
        val bg = lastBackgroundAtMs
        if (bg > 0 && System.currentTimeMillis() - bg >= SESSION_GAP_MS) {
            try {
                com.app.plugin.brain.AdsBrain.onSessionStart()
            } catch (e: Exception) {
                Log.e("AppPlugin", "brain session start failed: ${e.message}")
            }
        }

        // Đồng bộ lại quyền lợi mỗi lần vào foreground. Người dùng có thể vừa huỷ sub trong
        // Play Store, vừa mua ở máy khác, hoặc được hoàn tiền — tất cả đều xảy ra NGOÀI app,
        // nên không có event nào báo về. Không đồng bộ ở đây thì một sub đã huỷ vẫn tắt
        // quảng cáo cho tới lần khởi động lạnh kế tiếp.
        if (com.app.plugin.iap.IapHelper.isReady()) {
            try {
                com.app.plugin.iap.IapHelper.restorePurchases()
            } catch (e: Exception) {
                Log.e("AppPlugin", "iap restore failed: ${e.message}")
            }
        }
        com.app.plugin.iap.SubscriptionMessaging.onForeground(currentActivity) // D2: hộp thư Play khi grace/hold (T11)

        // Show the ad (if available) when the app moves to foreground.
        currentActivity?.let {
            if (!isSStart) {
                try {
                    if (autoShowOpenAd()) Handler(Looper.getMainLooper()).postDelayed({
                        if (autoShowOpenAd()) AdsHelper.instance.showOpenAd(PlacementDefault.PLFullResume, null)
                    }, 1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                isSStart = false
                if (!AppPluginManager.instance.checkMainActivity(currentActivity!!)) {
                    AdsHelper.instance.setMainActivity(currentActivity!!)
                } else {
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        lastBackgroundAtMs = System.currentTimeMillis()
        com.app.plugin.logevent.SessionTracker.onBackground()
        try {
            // App vào nền ngay sau một quảng cáo CHÍNH LÀ tín hiệu tiêu cực mà reward model
            // cần (doc 39). Không có móc này thì quyết định treo chỉ được chốt ở lần hỏi kế
            // tiếp — tức luôn được chấm là "người chơi ở lại", và bandit không bao giờ học
            // được rằng hiện quảng cáo có thể đuổi người chơi đi.
            com.app.plugin.brain.AdsBrain.onSessionEnd()
        } catch (e: Exception) {
            Log.e("AppPlugin", "brain session end failed: ${e.message}")
        }
    }

    /** ActivityLifecycleCallback methods. */
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        com.app.plugin.iap.IapHarness.onActivityCreated(activity) // harness G12: chỉ khi đủ 3 khoá (1.8.0 Task 8)
        com.app.plugin.brain.BrainPolicyHarness.onActivityCreated(activity) // harness brain: chỉ khi đủ 3 khoá (1.9.0 Task 5s, D28)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        com.app.plugin.logevent.SessionTracker.onActivityStarted()
        if (AppPluginManager.instance.checkMainActivity(activity)) {
            mainAppActivity = activity
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!AppPluginManager.instance.checkMainActivity(activity) && isHostCandidate(activity)) {
            AppPluginManager.instance.setMainActivity(activity)
        }
        if (AppPluginManager.instance.checkMainActivity(activity)) {
            AdsHelper.instance.setMainActivity(activity)
            AdsHelper.instance.onMainActivityResumed(activity)
            AdsHelper.instance.checkResumeAfterClickAd()
            for (cb in AppPluginManager.instance.getlistAppLifeCB()) {
                cb.onActivityResume(activity)
            }
        }
    }

    /**
     * 2.4.1 HOSTFIX (D36): host = Activity của app resume gần nhất (như ≤ 2.4.0, kể cả khi app đã tự `setMainActivity`). "Của app" =
     * tên lớp bắt đầu bằng applicationId (như cũ), hoặc là launcher của [getPackageName], hoặc nằm trong gói của lớp Application
     * — bản debug có `applicationIdSuffix` (`x.y.debug`, lớp `x.y.MainActivity`) không còn bị bỏ sót (Full2 xếp hàng rồi bị bỏ,
     * `sdd/im/full2-redmi-analysis.md`). Launcher khai bằng `activity-alias` có component = tên alias nên luật launcher không khớp;
     * luật tiền tố/gói app thường vẫn phủ. Không bao giờ Activity của SDK quảng cáo/mediation ([adSdkPrefixes]).
     */
    private fun isHostCandidate(activity: Activity): Boolean {
        val name = activity.javaClass.name
        if (adSdkPrefixes.any { name.startsWith(it) }) return false
        if (packageName?.let { name.startsWith(it) } == true) return true   // app không hậu tố: 0 lời gọi PackageManager
        resolveHostRules()
        return name == launcherClass || appPkgDot?.let { name.startsWith(it) } == true
    }

    /** Launcher của applicationId (null-safe: không launcher / lỗi → null) và "<gói lớp Application>.", một lần mỗi process. */
    private fun resolveHostRules() {
        if (hostRulesResolved) return
        hostRulesResolved = true
        launcherClass = try { packageManager.getLaunchIntentForPackage(packageName)?.component?.className } catch (e: Exception) { null }
        appPkgDot = javaClass.`package`?.name?.takeIf { it.isNotEmpty() }?.let { "$it." }
    }

    override fun onActivityPaused(activity: Activity) {
        if (AppPluginManager.instance.checkMainActivity(activity)) {
            AdsHelper.instance.onMainActivityPaused(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
        if (mainAppActivity == activity) mainAppActivity = null
        if (AppPluginManager.instance.checkMainActivity(activity)) {
            AppPluginManager.instance.clearMainActivity()
            AdsHelper.instance.clearActivity()
        }
    }
}
