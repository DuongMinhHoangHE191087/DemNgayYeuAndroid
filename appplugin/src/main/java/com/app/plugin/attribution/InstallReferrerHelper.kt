package com.app.plugin.attribution

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.app.plugin.consent.AnalyticsConsent
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.GppConsent
import com.app.plugin.consent.TcfConsent
import com.app.plugin.firbase.FirHelper
import com.app.plugin.logevent.EventNames
import com.app.plugin.until.PreUtil
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import java.net.URLDecoder
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Đọc Google Play Install Referrer một lần cho mỗi lượt cài, rồi đưa tham số UTM vào Firebase
 * Analytics: user property `utm_source`/`utm_medium`/`utm_campaign` và một event
 * `app_install_referrer`. Thiếu bước này, mọi lượt cài đều hiện là organic trong Firebase: Google
 * Ads conversion import vẫn chạy (nó lấy attribution phía server), nhưng không chia cohort theo
 * nguồn được.
 *
 * 2.4.2 REFERRER: SDK tự gọi [captureAfterConsent] từ `AdSdkStartup.applyTier` (cổng `analytics` mở sau câu trả lời
 * UMP, và mỗi lần tầng đổi), không như Speaker Clean/Invoice Maker (gọi trong `Application.onCreate`, trước consent).
 * Referrer có thể chứa `gclid` (định danh lượt bấm quảng cáo): chỉ đọc khi Consent Mode cho `analytics_storage`, và
 * `gclid` + `raw_referrer` chỉ gửi khi `ad_user_data` được cho. Tầng không cho: không IPC, thử lại ở lần áp tầng sau
 * hoặc lần mở sau. Consent, IPC và gửi chạy trên [executor]: ngoài main, ngoài khoá `ConsentState`. Chuỗi referrer thô
 * không lưu xuống prefs (khoá `install_referrer_url` của bản cũ không ai đọc; `clearLocalData` xoá giá trị còn sót).
 */
internal object InstallReferrerHelper {
    private const val TAG = "InstallReferrer"
    internal const val KEY_CAPTURED = "install_referrer_captured"

    /** Giới hạn độ dài giá trị user property của Firebase Analytics. */
    private const val USER_PROPERTY_MAX_LENGTH = 36
    private const val RAW_REFERRER_MAX_LENGTH = 100
    private const val RAW_REFERRER = "raw_referrer"

    private val REFERRER_KEYS = listOf("utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term", "gclid")

    /** Chỉ gửi khi Consent Mode cho `ad_user_data`: định danh lượt bấm quảng cáo, và chuỗi thô có thể chứa nó. */
    private val AD_USER_DATA_KEYS = setOf("gclid", RAW_REFERRER)

    private val worker: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "appplugin-referrer").apply { isDaemon = true } }
    }

    /** Luồng nền của mọi bước (một luồng daemon, tạo lần đầu có việc). Test thay bằng executor đồng bộ hoặc giữ Runnable. */
    @Volatile
    internal var executor: Executor = Executor { worker.execute(it) }

    /** Bước mở IPC ([captureOnce]); test thay để đếm số lần bắt đầu đọc. */
    @Volatile
    internal var startRead: (Context) -> Unit = { captureOnce(it) }

    /** Đã bắt đầu đọc trong process này: một lần mỗi process (lần mở sau thử lại nếu chưa ghi [KEY_CAPTURED]). */
    private val startedInProcess = AtomicBoolean(false)

    /**
     * Gọi từ `AdSdkStartup.applyTier` (trong khoá `ConsentState`): chỉ xếp việc lên [executor], không đọc gì tại chỗ.
     * Việc nền đọc tầng hiện tại; Consent Mode không cho `analytics_storage` (DENIED, UNKNOWN trong GDPR hoặc không biết
     * GDPR) thì không mở IPC. Lỗi thì log, không ném.
     */
    fun captureAfterConsent(context: Context) {
        try {
            val app = context.applicationContext ?: context
            executor.execute { captureIfAllowed(app) }
        } catch (e: Throwable) {
            Log.e(TAG, "InstallReferrer capture not scheduled", e)
        }
    }

    private fun captureIfAllowed(app: Context) {
        try {
            if (!consent(app).analyticsStorage) return
            if (PreUtil.getInt(app, KEY_CAPTURED, 0) == 1 || !startedInProcess.compareAndSet(false, true)) return
            startRead(app)
        } catch (e: Throwable) {
            Log.e(TAG, "InstallReferrer capture failed", e)
        }
    }

    /** Consent Mode lúc này, cùng quy tắc với `AnalyticsConsent.apply` (tầng, `IABTCF_gdprApplies`, GPP). */
    private fun consent(app: Context): AnalyticsConsent.Settings = AnalyticsConsent.settingsFor(
        ConsentState.tier(), TcfConsent.gdprApplies(app), ConsentRules.usOptOut(GppConsent.read(app)),
    )

    /** Mở kết nối Play Install Referrer; gọi qua [captureAfterConsent] (đã xét consent), không gọi thẳng. */
    public fun captureOnce(context: Context) {
        val appContext = context.applicationContext ?: context
        if (PreUtil.getInt(appContext, KEY_CAPTURED, 0) == 1) return

        val client = try {
            InstallReferrerClient.newBuilder(appContext).build()
        } catch (e: Throwable) {
            Log.e(TAG, "InstallReferrerClient build failed", e)
            return
        }

        client.startConnection(object : InstallReferrerStateListener {
            // Callback của Play chạy trên main: đọc (IPC), xét lại consent và gửi trên [executor].
            override fun onInstallReferrerSetupFinished(responseCode: Int) = executor.execute { finish(responseCode) }

            /** Hàm của lớp nghe (không lambda): thứ tự accessor `report` rồi `endQuietly` của object giữ như dump (IG). */
            fun finish(responseCode: Int) {
                try {
                    if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        val referrer = client.installReferrer.installReferrer.orEmpty()
                        val allowed = consent(appContext)
                        // Tầng thu hẹp từ lúc mở IPC: không gửi, không đánh dấu; lần mở sau đọc lại.
                        if (!allowed.analyticsStorage) return
                        // Chỉ ghi có hay không: chuỗi referrer có thể mang gclid.
                        Log.i(TAG, "InstallReferrer captured, empty=${referrer.isEmpty()}")   // Log.i: Task R grep (luật 26)
                        // `report(String)` = gửi đủ; giữ nó vì accessor `access$report` có trong dump (IG 2.4.2).
                        if (allowed.adUserData) report(referrer) else report(referrer, adUserData = false)
                    } else {
                        // FEATURE_NOT_SUPPORTED, SERVICE_UNAVAILABLE (cài ngoài Play, store khác),
                        // DEVELOPER_ERROR...: không bao giờ tự khỏi cho lượt cài này, nên đánh dấu
                        // đã xong để không mở IPC lại ở mỗi lần khởi động.
                        Log.w(TAG, "Install Referrer unavailable, code=$responseCode")
                    }
                    PreUtil.setInt(appContext, KEY_CAPTURED, 1)
                } catch (e: Throwable) {
                    Log.e(TAG, "InstallReferrer read failed", e)
                } finally {
                    endQuietly(client)
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                // Service rớt trước onInstallReferrerSetupFinished thì khối finally ở trên không
                // chạy, client và ServiceConnection rò suốt process. Không kết nối lại: lần khởi
                // động sau chạy lại (KEY_CAPTURED vẫn là 0), cách thử lại rẻ nhất.
                endQuietly(client)
            }
        })
    }

    private fun report(referrer: String) = report(referrer, adUserData = true)

    private fun report(referrer: String, adUserData: Boolean) {
        if (referrer.isEmpty()) return
        val params = eventParams(referrer, adUserData)
        try {
            val analytics = Firebase.analytics
            listOf("utm_source", "utm_medium", "utm_campaign").forEach { key ->
                params[key]?.takeIf { it.isNotEmpty() }?.let {
                    analytics.setUserProperty(key, it.take(USER_PROPERTY_MAX_LENGTH))
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "setUserProperty failed", e)
        }
        val event = Bundle().apply { params.forEach { (k, v) -> putString(k, v) } }
        FirHelper.instance.logEvent(EventNames.APP_INSTALL_REFERRER, event)
    }

    /**
     * Tham số event `app_install_referrer`: mọi khoá UTM + `gclid` (rỗng khi vắng) và `raw_referrer` (≤ 100 ký tự);
     * không [adUserData] thì bỏ `gclid` và `raw_referrer`.
     */
    internal fun eventParams(referrer: String, adUserData: Boolean): Map<String, String> {
        val parsed = parseReferrer(referrer)
        val all = REFERRER_KEYS.associateWith { parsed[it].orEmpty() } + (RAW_REFERRER to referrer.take(RAW_REFERRER_MAX_LENGTH))
        return if (adUserData) all else all - AD_USER_DATA_KEYS
    }

    /** Referrer là query string đã URL-encode; lấy giá trị đầu của mỗi khoá đã biết (`URLDecoder`, chạy được trên JVM). */
    internal fun parseReferrer(referrer: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (pair in referrer.split('&')) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val key = decode(pair.substring(0, eq))
            if (key in REFERRER_KEYS) out.putIfAbsent(key, decode(pair.substring(eq + 1)))
        }
        return out
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: IllegalArgumentException) {
        s
    }

    private fun endQuietly(client: InstallReferrerClient) {
        try {
            client.endConnection()
        } catch (_: Throwable) {
            // Đã đóng hoặc chưa từng kết nối.
        }
    }

    /** Process mới: chưa bắt đầu đọc. Chỉ cho test. */
    internal fun resetForTest() {
        startedInProcess.set(false)
    }
}
