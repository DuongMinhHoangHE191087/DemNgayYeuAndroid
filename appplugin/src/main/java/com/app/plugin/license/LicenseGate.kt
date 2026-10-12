package com.app.plugin.license

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.app.plugin.until.LogUtil
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Khoá giấy phép offline (hợp đồng §C, plan 2.1.0 SA1). Xác minh một lần mỗi process trên luồng [THREAD_NAME]:
 * `AppPluginBase` bắt đầu ở bước `meta` với chuỗi meta-data [META_KEY] đọc từ cùng bundle của `dataBucketConfig`
 * (0 IPC thêm trên main); đọc cert ký = 1 IPC trên luồng license, rồi [LicenseCache] (2.4.3). App không kế thừa `AppPluginBase` thì lần
 * [allowsAdNetworks] đầu tự bắt đầu, đọc meta-data trên luồng license.
 *
 * Verdict: [Verdict.Pending] → [Verdict.Valid] | [Verdict.Invalid] (lý do cố định: `missing`, `malformed`, `kid`,
 * `sig`, `pkg`, `cert`, `expired`, `feat`; `timeout` khi chờ quá [WAIT_MS]; `error` khi xác minh ném — fail-closed).
 * Verdict đầu tiên thắng, không đổi trong process. INVALID: mạng quảng cáo không khởi tạo (`AdSdkStartup`),
 * `AdsHelper.isDisableAds` true, `AdsBrain` không quyết; đúng một `logE`. VALID: một dòng `Log.i` thẳng.
 * Giấy phép chỉ tắt, không mở gì consent đóng (R-P1). Main không bao giờ chờ: chỉ [await] chờ, và chỉ luồng riêng gọi.
 */
internal object LicenseGate {
    const val META_KEY: String = "com.app.plugin.LICENSE"
    const val THREAD_NAME: String = "appplugin-license"
    const val WAIT_MS: Long = 2_000L
    const val HEALTH_NETWORKS: String = "unlicensed"   // `networks` của `sdk_health` khi INVALID (khối mới: mặt SdkHealth giữ nguyên)

    /** Chuỗi canh của `AppPluginBase` khi đọc bundle meta-data lỗi → `INVALID (error)`, không phải `missing`. */
    const val READ_FAILED: String = "\u0000read-failed"
    private val REQUIRED_FEATURES = listOf("ads", "brain")

    internal sealed class Verdict {
        internal object Pending : Verdict()
        internal data class Valid(val kid: String, val expSec: Long) : Verdict()
        internal data class Invalid(val reason: String) : Verdict()
    }

    // Seam test: accessor @JvmSynthetic — AAR không minify, javac của app không gọi được setKeys/setClock… (R-SA1 I1).
    /** Cert ký của app: SHA-256 hex thường của DER (seam test). */
    @get:JvmSynthetic @set:JvmSynthetic
    @Volatile
    internal var certsProvider: (Context) -> List<String> = { readCerts(it) }

    @get:JvmSynthetic @set:JvmSynthetic
    @Volatile
    internal var clock: () -> Long = { System.currentTimeMillis() }

    @get:JvmSynthetic @set:JvmSynthetic
    @Volatile
    internal var keys: () -> Map<String, ByteArray> = { LicenseKeys.all() }

    /** 2.4.3 FASTSTART-A: `Message` async đầu hàng main (vượt hàng đợi và sync barrier khung hình), vẫn chạy trên main. */
    private val frontPoster: (Runnable) -> Unit = { r ->
        val h = Handler(Looper.getMainLooper())
        h.sendMessageAtFrontOfQueue(Message.obtain(h, r).apply { isAsynchronous = true })
    }

    /** Chạy lại việc đã chờ verdict trên main (seam test); mặc định [frontPoster]. */
    @get:JvmSynthetic @set:JvmSynthetic
    @Volatile
    internal var mainPoster: (Runnable) -> Unit = frontPoster

    /** Seam đếm (L14): tên luồng của mỗi lần [await]. */
    @get:JvmSynthetic
    internal val awaitThreads: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    @get:JvmSynthetic
    @Volatile
    internal var verdict: Verdict = Verdict.Pending
        private set

    private val started = AtomicBoolean(false)
    private val waiters = AtomicInteger(0)

    @Volatile
    private var decided = CountDownLatch(1)

    /** 2.2.0 LIC (D30-220): nghe verdict đầu tiên (trùng thể hiện = một lần), chạy ngoài khoá trên luồng quyết định. */
    private val listeners = LinkedHashSet<(Verdict) -> Unit>()

    @JvmSynthetic internal fun onVerdict(listener: (Verdict) -> Unit) {
        val v = synchronized(this) { verdict.also { if (it is Verdict.Pending) listeners += listener } }
        if (v !is Verdict.Pending) listener(v)   // đã có verdict: chạy ngay trên luồng gọi
    }

    /** INVALID: quảng cáo và brain tắt. PENDING không chặn quảng cáo (load còn được giữ); brain chỉ quyết khi VALID (2.2.0 LIC). */
    val blocksAds: Boolean get() = verdict is Verdict.Invalid

    /** Param `license` của `sdk_health`: `VALID`, `INVALID:<lý do>` hoặc `PENDING`. */
    val label: String get() = verdict.let { if (it is Verdict.Invalid) "INVALID:${it.reason}" else if (it is Verdict.Valid) "VALID" else "PENDING" }

    /** Bước `meta` của `initPlugin`: [token] = chuỗi meta-data (null = vắng). Gọi lại là no-op; không chờ. */
    fun start(context: Context, token: String?) {
        launch(context) { check(token != READ_FAILED) { "meta-data" }; token }
    }

    private fun launch(context: Context, token: (Context) -> String?) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext ?: context
        val launchNs = System.nanoTime()   // `since_start` của dòng `license: source=…` (2.4.3 F1 C1)
        try {
            Thread({ decide(verify(app, launchNs, token)) }, THREAD_NAME).apply { isDaemon = true }.start()
        } catch (e: Throwable) {
            decide(Verdict.Invalid("error"))
        }
    }

    /** Xác minh qua [LicenseCache] (2.4.3: cache VALID, trượt = [evaluate]); mọi ngoại lệ = `INVALID (error)` — fail-closed. */
    private fun verify(app: Context, launchNs: Long, token: (Context) -> String?): Verdict = try {
        LicenseCache.resolve(app, launchNs) { token(app) }
    } catch (e: Throwable) {
        Verdict.Invalid("error")
    }

    /** Luật verdict, thuần (test gọi thẳng); [certs] chỉ đọc khi chữ ký và gói đã đúng. */
    @JvmSynthetic
    internal fun evaluate(raw: String?, pkg: String, certs: () -> List<String>, nowMs: Long): Verdict {
        if (raw.isNullOrBlank()) return Verdict.Invalid("missing")
        val t = LicenseToken.parse(raw) ?: return Verdict.Invalid("malformed")
        val key = keys()[t.kid] ?: return Verdict.Invalid("kid")
        if (!Ed25519.verify(key, t.signedPart, t.signature)) return Verdict.Invalid("sig")
        if (t.pkg != pkg) return Verdict.Invalid("pkg")
        val mine = certs().map { it.lowercase() }.toSet()
        if (t.certs.none { it.lowercase() in mine }) return Verdict.Invalid("cert")
        if (t.expSec <= nowMs / 1000) return Verdict.Invalid("expired")
        if (!t.feat.containsAll(REQUIRED_FEATURES)) return Verdict.Invalid("feat")
        return Verdict.Valid(t.kid, t.expSec)
    }

    private fun decide(v: Verdict) {
        val due = synchronized(this) { if (verdict !is Verdict.Pending) return; verdict = v; listeners.toList().also { listeners.clear() } }
        decided.countDown()
        try {
            when (v) {
                is Verdict.Valid -> Log.i(LogUtil.TAG, "license: VALID kid=${v.kid} exp=${expDate(v.expSec)}")
                is Verdict.Invalid -> LogUtil.logE("license: INVALID (${v.reason}) — quảng cáo và brain tắt")
                Verdict.Pending -> Unit
            }
        } catch (e: Throwable) {
            // log không được làm hỏng verdict (Điều 7)
        }
        due.forEach { try { it(v) } catch (e: Throwable) { /* người nghe không được làm hỏng verdict */ } }
    }

    /** Ngày hết hạn `yyyy-MM-dd` theo UTC (2.2.0 MIN24: `SimpleDateFormat` API 1 thay `java.time.Instant` API 26). */
    @JvmSynthetic internal fun expDate(expSec: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(expSec * 1000))

    /**
     * Cổng khởi tạo mạng của `AdSdkStartup` (main): VALID → `true`; INVALID → `false`; PENDING → `false`, chờ verdict
     * trên một luồng riêng (≤ [WAIT_MS]) rồi chạy lại [rerun] trên main qua [mainPoster]. Main không chờ.
     */
    fun allowsAdNetworks(context: Context, rerun: () -> Unit): Boolean {
        when (verdict) {
            is Verdict.Valid -> return true
            is Verdict.Invalid -> return false
            Verdict.Pending -> Unit
        }
        launch(context) { readToken(it) }
        if (waiters.getAndIncrement() > 0) return false
        try {
            Thread({ await(); mainPoster(Runnable { rerun() }) }, "$THREAD_NAME-wait").apply { isDaemon = true }.start()
        } catch (e: Throwable) {
            decide(Verdict.Invalid("error"))
        }
        return false
    }

    /** Chờ verdict ≤ [WAIT_MS] trên luồng gọi — không bao giờ main; hết hạn → `INVALID (timeout)` (fail-closed). */
    @JvmSynthetic
    internal fun await(): Verdict {
        awaitThreads += Thread.currentThread().name
        if (!decided.await(WAIT_MS, TimeUnit.MILLISECONDS)) decide(Verdict.Invalid("timeout"))
        return verdict
    }

    private fun readToken(context: Context): String? =
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData?.getString(META_KEY)

    @Suppress("DEPRECATION")
    private fun readCerts(context: Context): List<String> {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            if (info == null) null else if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        return signatures.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    /** Về trạng thái process mới, rồi đặt [verdict] (mặc định PENDING). Chỉ cho test. */
    @JvmSynthetic
    internal fun resetForTest(verdict: Verdict = Verdict.Pending) {
        synchronized(this) { this.verdict = verdict; listeners.clear() }
        decided = CountDownLatch(if (verdict is Verdict.Pending) 1 else 0)
        started.set(verdict !is Verdict.Pending)
        waiters.set(0)
        awaitThreads.clear()
        certsProvider = { readCerts(it) }
        clock = { System.currentTimeMillis() }
        keys = { LicenseKeys.all() }
        mainPoster = frontPoster
    }
}
