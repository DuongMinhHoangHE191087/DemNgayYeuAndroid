package com.app.plugin.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.app.plugin.ads.consent.AdRequestFactory
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.GppConsent
import com.app.plugin.consent.GppSignals
import com.app.plugin.consent.TcfConsent
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Khởi tạo Meta SDK (app events, attribution cho chiến dịch Meta) sau consent. Lấy từ hai app fork.
 *
 * SDK chỉ khai `facebook-core` ở `compileOnly`: app nào đo chiến dịch Meta thì tự khai
 * `com.facebook.android:facebook-core` (hay `facebook-android-sdk`), app khác không phải mang
 * `FacebookInitProvider` theo. Không có lớp đó trong bản dựng thì hàm này không làm gì.
 *
 * Manifest của SDK đặt `com.facebook.sdk.AutoInitEnabled`, `AutoLogAppEventsEnabled` và
 * `AdvertiserIDCollectionEnabled` về `false`, theo cách Meta hướng dẫn để hoãn SDK tới khi có
 * consent. Nhưng facebook-core **lưu** ba cờ đó trên máy mỗi lần setter được gọi (`UserSettingsManager` →
 * prefs `com.facebook.sdk.USER_SETTINGS`, đọc bytecode 17.0.2), và giá trị đã lưu thắng manifest: `FacebookInitProvider`
 * đọc nó lúc mở app, trước `Application.onCreate`, rồi tự `fullyInitialize` và theo dõi app events nếu `true`. Máy
 * nào từng được khởi tạo (mọi máy nâng cấp từ ≤ 1.3.x, nơi SDK bật cho mọi tầng) giữ `true`. Nên khi tầng từ chối,
 * [start] và [followTier] ghi `false` đè cho cả ba cờ (review N1): lần mở kế im lặng. Lần mở này thì không gỡ được
 * (provider đã chạy), kể cả lần mở **đầu** sau nâng cấp. Tầng cho phép lại thì ghi `true` lại.
 *
 * Chỉ khởi tạo khi tầng cho phép ([ConsentRules.tierAllows], cùng cổng với Meta AN trong `AdSdkStartup` và với
 * AppsFlyer): `DENIED` và `UNKNOWN` trong vùng GDPR (vắng tính là trong) thì không (P1, FX; Điều 13: UNKNOWN không bao giờ
 * là đồng ý). Cổng UMP mở (`canRequestAds()`) không có nghĩa là họ đồng ý Meta. Tầng cho phép sau đó trong process thì
 * [followTier] khởi tạo, một lần.
 *
 * Khoá: [lock] giữ khi quyết định (đọc tầng, cờ) và xếp việc lên [executor] (chỉ xếp hàng), nên việc tới facebook-core
 * đúng thứ tự quyết định; không gọi ra ngoài khác trong khoá. [followTier] chạy trong khoá của `ConsentState` (listener
 * tầng), nên thứ tự là `ConsentState` → [lock], không bao giờ ngược lại.
 */
internal object MetaSdkStarter {
    private const val PLACEHOLDER_PREFIX = "FB_APP_ID"
    private const val THREAD_NAME = "appplugin-meta"

    /** Limited Data Use: tuỳ chọn xử lý dữ liệu của Meta cho luật bang Mỹ. */
    private const val LDU = "LDU"

    private val lock = Any()

    /** Application đã khởi tạo Meta SDK trong process này (một lần); `null` khi chưa. Khoá: [lock]. */
    private var launchedApp: Application? = null

    /** Việc cờ cuối cùng đã xếp lên [executor] là ghi `false` (tắt auto-init). Khoá: [lock]. */
    private var autoInitOff = false

    /**
     * Bị bỏ qua vì consent: Application của lần [start] bị từ chối, để [followTier] khởi tạo khi tầng cho phép;
     * `null` khi không giữ chờ. Khoá: [lock].
     */
    private var heldForConsent: Application? = null

    private val isFacebookCoreAvailable: Boolean by lazy {
        runCatching { Class.forName("com.facebook.FacebookSdk") }.isSuccess
    }

    /** App id rỗng hay còn là placeholder mà vẫn khởi tạo thì SDK gọi Graph API và nhận lỗi OAuth 190. */
    fun isConfiguredAppId(appId: String): Boolean {
        val id = appId.trim()
        return id.isNotEmpty() && !id.startsWith(PLACEHOLDER_PREFIX)
    }

    /** Có `facebook-core` trong bản dựng không; test thay (unit test không có lớp đó), [resetForTest] trả về bản thật. */
    @Volatile
    internal var facebookCoreAvailable: () -> Boolean = { isFacebookCoreAvailable }

    /** Luồng nền của facebook-core: một luồng daemon, tạo lần đầu có việc. */
    private val metaThread: Executor by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, THREAD_NAME).apply { isDaemon = true } }
    }

    /**
     * Hàng đợi của mọi lời gọi facebook-core (lần chạm đầu đọc đĩa, không chạy trên luồng phát callback consent): một
     * luồng, nên việc tới facebook-core đúng thứ tự được xếp. Test thay, [resetForTest] trả về bản thật.
     */
    @Volatile
    internal var executor: Executor = Executor { metaThread.execute(it) }

    /** Khởi tạo thật trên [executor]; test thay, [resetForTest] trả về bản thật. */
    @Volatile
    internal var initializer: (Application) -> Unit = { initializeInBackground(it) }

    /**
     * Chỉ được gọi sau cổng consent (task `meta sdk` của `AdSdkStartup`). Khởi tạo một lần mỗi process. Tầng không cho
     * phép ([ConsentRules.tierAllows]): không khởi tạo (không `activateApp`), ghi `false` cho ba cờ auto-init đã lưu
     * trên [executor], log lỗi, giữ chờ [followTier].
     */
    fun start(context: Context) {
        if (!facebookCoreAvailable()) {
            LogUtil.logD("meta sdk: facebook-core không có trong bản dựng, bỏ qua")
            return
        }
        val app = context.applicationContext as? Application ?: return
        if (!isConfiguredAppId(AppUtil.getResourceString(app, "facebook_app_id"))) {
            LogUtil.logD("meta sdk: facebook_app_id trống hoặc là placeholder, bỏ qua")
            return
        }
        // Quyết định và xếp hàng trong khoá, cùng với cờ giữ chờ: một followTier trên luồng khác chạy trước hoặc sau cả hai.
        val refusal = synchronized(lock) {
            val tier = ConsentState.tier()
            val gdprApplies = TcfConsent.gdprApplies(app)
            if (ConsentRules.tierAllows(tier, gdprApplies)) {
                heldForConsent = null
                launchLocked(app)
                return
            }
            disableAutoInitLocked(app, tier)
            if (launchedApp != null) return   // gọi lại sau khi đã khởi tạo: chỉ tắt cờ, không báo "not started"
            heldForConsent = app
            ConsentRules.scopeLabel(tier, gdprApplies)
        }
        LogUtil.logE("meta sdk: not started, $refusal")
    }

    /**
     * Theo tầng (`AdSdkStartup.applyTier`, mỗi lần tầng đổi). Chưa từng qua [start] thì không làm gì (app không gọi
     * `initMetaSdkAfterConsent`, hoặc cổng chưa mở). Lỗi thì log, không ném (chạy trong listener tầng).
     * - Bị bỏ qua vì consent mà tầng giờ cho phép ([ConsentRules.tierAllows]: `GRANTED`/`PARTIAL`, hoặc ngoài GDPR;
     *   `UNKNOWN` thì chưa) → khởi tạo lúc này, một lần.
     * - Đã khởi tạo mà tầng giờ từ chối → ghi `false` cho ba cờ auto-init (một lần mỗi lần chuyển): lần mở kế không tự
     *   khởi tạo; lần này facebook-core đã chạy, không gỡ được. Cho phép lại → ghi `true` lại.
     */
    fun followTier() {
        try {
            val startedTier = synchronized(lock) {
                val held = heldForConsent
                val app = held ?: launchedApp ?: return
                val tier = ConsentState.tier()
                if (!ConsentRules.tierAllows(tier, TcfConsent.gdprApplies(app))) {
                    disableAutoInitLocked(app, tier)
                    return
                }
                if (held == null) {
                    if (autoInitOff) enableAutoInitLocked(app)
                    return
                }
                heldForConsent = null
                launchLocked(held)
                tier
            }
            Log.d(LogUtil.TAG, "meta sdk: consent now allows it, starting (tier=$startedTier)")
        } catch (e: Exception) {
            LogUtil.logE("meta sdk: start after consent change failed: ${e.message}")
        }
    }

    /** Khởi tạo đúng một lần mỗi process. Trong [lock]: [initializer] chỉ xếp việc lên [executor]. */
    private fun launchLocked(app: Application) {
        if (launchedApp != null) return
        launchedApp = app
        autoInitOff = false
        initializer(app)
    }

    /** Trong [lock]: xếp việc ghi `false` cho ba cờ auto-init, trừ khi việc cờ cuối đã là `false`. */
    private fun disableAutoInitLocked(app: Application, tier: ConsentTier) {
        if (autoInitOff) return
        autoInitOff = true
        executor.execute { writeAutoInitOff(app, tier) }
    }

    /** Trong [lock]: Meta đã chạy, tầng cho phép lại sau một lần tắt → ghi `true` lại (không khởi tạo, không activate lại). */
    private fun enableAutoInitLocked(app: Application) {
        autoInitOff = false
        executor.execute {
            try {
                writeAutoInitFlags(app, enabled = true)
                Log.d(LogUtil.TAG, "meta sdk: auto-init enabled again (tier=${ConsentState.tier()})")
            } catch (e: Throwable) {
                LogUtil.logE("meta sdk: auto-init enable failed: ${e.message}")
            }
        }
    }

    /**
     * Trên [executor]. facebook-core 17.0.2 chỉ lưu cờ khi `FacebookSdk.isInitialized()` (provider thường đã chạy); chưa
     * thì giá trị chỉ nằm trong bộ nhớ, và được lưu nếu SDK khởi tạo trong process này — log rõ trường hợp đó.
     */
    private fun writeAutoInitOff(app: Application, tier: ConsentTier) {
        try {
            val persisted = FacebookSdk.isInitialized()
            writeAutoInitFlags(app, enabled = false)
            if (persisted) {
                Log.d(LogUtil.TAG, "meta sdk: auto-init disabled (tier=$tier)")
            } else {
                LogUtil.logE("meta sdk: auto-init disabled in memory only, facebook sdk not initialized (tier=$tier)")
            }
        } catch (e: Throwable) {
            LogUtil.logE("meta sdk: auto-init disable failed (tier=$tier): ${e.message}")
        }
    }

    /**
     * Ba cờ facebook-core lưu trên máy. Bật: advertising id canh cùng tín hiệu với npa=1 của AdMob (ở EEA mà purpose 1
     * không được đồng ý thì không thu).
     */
    private fun writeAutoInitFlags(app: Application, enabled: Boolean) {
        FacebookSdk.setAutoInitEnabled(enabled)
        FacebookSdk.setAutoLogAppEventsEnabled(enabled)
        FacebookSdk.setAdvertiserIDCollectionEnabled(enabled && AdRequestFactory.canRequestPersonalizedAds(app))
    }

    /**
     * Đánh giá lại GPP giữa phiên (`AdSdkStartup.applyTier`): Meta SDK đã khởi tạo trong process thì xếp
     * `setDataProcessingOptions` lên [executor] (sau mọi việc đã xếp, gồm cả `fullyInitialize`). Chưa khởi tạo (giữ chờ
     * consent, chưa qua [start], thiếu facebook-core) thì không làm gì — lúc khởi tạo [initializeInBackground] tự đọc GPP.
     */
    internal fun followGpp(gpp: GppSignals?) {
        if (gpp == null) return
        synchronized(lock) {
            if (launchedApp == null) return
            executor.execute { applyGpp(gpp) }
        }
    }

    /**
     * Trên [executor]. GPP (opt-out luật bang Mỹ) → Limited Data Use: [ConsentRules.usOptOut] thì
     * `FacebookSdk.setDataProcessingOptions(["LDU"], 0, 0)`, có GPP mà không opt-out thì `setDataProcessingOptions([])`.
     * Không có GPP thì không gọi. Lỗi thì log, không ném.
     */
    private fun applyGpp(gpp: GppSignals?) {
        if (gpp == null) return
        try {
            if (ConsentRules.usOptOut(gpp)) {
                FacebookSdk.setDataProcessingOptions(arrayOf(LDU), 0, 0)
                Log.d(LogUtil.TAG, "meta sdk: LDU on (${ConsentRules.gppLabel(gpp)})")
            } else {
                FacebookSdk.setDataProcessingOptions(arrayOf())
                Log.d(LogUtil.TAG, "meta sdk: LDU off (${ConsentRules.gppLabel(gpp)})")
            }
        } catch (e: Throwable) {
            LogUtil.logE("meta sdk: setDataProcessingOptions failed: ${e.message}")
        }
    }

    // Lần chạm đầu đọc đĩa; không để nó chặn luồng đang phát callback consent. GPP đọc ở đây (luồng nền), trước
    // `fullyInitialize`: Meta áp tuỳ chọn xử lý dữ liệu cho mọi event từ lần khởi tạo.
    private fun initializeInBackground(app: Application) {
        executor.execute {
            try {
                writeAutoInitFlags(app, enabled = true)
                applyGpp(GppConsent.read(app))
                FacebookSdk.fullyInitialize()
                AppEventsLogger.activateApp(app)
                LogUtil.logD("meta sdk: initialized after consent")
            } catch (e: Throwable) {
                LogUtil.logE("meta sdk: init failed: ${e.message}")
            }
        }
    }

    /** Như process mới: chưa khởi tạo, không giữ chờ consent, seam về bản thật. Chỉ cho test (`AdSdkStartup.resetForTest`). */
    @JvmSynthetic
    internal fun resetForTest() {
        synchronized(lock) {
            launchedApp = null
            heldForConsent = null
            autoInitOff = false
        }
        facebookCoreAvailable = { isFacebookCoreAvailable }
        initializer = { initializeInBackground(it) }
        executor = Executor { metaThread.execute(it) }
    }
}
