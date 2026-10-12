package com.app.plugin.brain.policy

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.DecisionEngine
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.PlayerIdSource
import com.app.plugin.license.LicenseGate
import com.app.plugin.until.LogUtil
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Policy brain từ server `/policy` có Play Integrity (2.3.0 SECBC, hợp đồng C; D2–D5-230). Mặc định TẮT: cần brain bật ([brainOn], 2.4.0)
 * và đủ meta-data [META_URL] ([validUrl]), [META_API_KEY], [META_PROJECT] (Long > 0); thiếu một → `policy: off (<khoá>)`, 0 mạng.
 * [install] chỉ đăng ký `LicenseGate.onVerdict` (0 I/O trên main); verdict đầu `Valid` → luồng [THREAD_NAME] đọc meta-data,
 * `POST policy/nonce` → token Integrity → `GET policy`; không chờ consent (Q4). Một lượt mạng mỗi process, 0 thử lại (Q5), ≤ 1 lượt
 * Integrity / 24 h / cài đặt ([backedOff]). Fail-closed: chỉ áp khi 200 + JSON + `source == "app"` + `policy` là object; còn lại giữ
 * policy hiện có (RC/store/built-in). Áp thành công → RC `brain_policy_android` bị bỏ qua tới hết process (Q3, [remoteConfigAllowed]).
 * Mọi dòng `policy: …` qua `Log.i` (luật 26); không bao giờ log nonce, token hay khoá API.
 */
internal object PolicyClient {
    const val META_URL: String = "com.app.plugin.BRAIN_POLICY_URL"
    const val META_PROJECT: String = "com.app.plugin.INTEGRITY_CLOUD_PROJECT"
    const val META_API_KEY: String = "DATABUCKETS_API_KEY"
    const val THREAD_NAME: String = "appplugin-policy"
    const val TOKEN_TIMEOUT_MS: Long = 10_000L
    const val EXTRA_FAKE_TOKEN: String = "policy_fake_token"   // extra harness (`BrainPolicyHarness`, SECBH)
    private const val H_API_KEY = "X-Api-Key"
    private const val H_TOKEN = "X-Integrity-Token"
    private val NONCE = Regex("[A-Za-z0-9_=-]{16,500}")
    private val REASON = Regex("[a-z0-9_]{1,40}")
    private val VERSION = Regex("[A-Za-z0-9._-]{1,64}")   // #12: version server trước khi log
    private const val PREFS = "appplugin_policy"
    private const val KEY_LAST_ATTEMPT = "policy_last_attempt_ms"
    private const val HOUR_MS = 3_600_000L

    // Seam test (@JvmSynthetic: javac của app không gọi được).
    @get:JvmSynthetic @set:JvmSynthetic @Volatile
    internal var transport: PolicyTransport = OkHttpPolicyTransport()

    @get:JvmSynthetic @set:JvmSynthetic @Volatile
    internal var tokenSourceFactory: (Context, Long) -> IntegrityTokenSource = { c, p -> PlayIntegrityTokenSource(c, p) }

    /** Meta-data của app (chỉ đọc trên [THREAD_NAME]). */
    @get:JvmSynthetic @set:JvmSynthetic @Volatile
    internal var metaReader: (Context) -> Map<String, Any?> = { readMeta(it) }

    @get:JvmSynthetic @set:JvmSynthetic @Volatile
    internal var logSink: (String) -> Unit = { Log.i(LogUtil.TAG, it) }

    /** Version server đã áp trong process này (`null` = chưa): RC brain policy bị bỏ qua khi khác null (Q3). */
    @get:JvmSynthetic @Volatile
    internal var serverVersion: String? = null
        private set

    private val started = AtomicBoolean(false)       // Q5: một lượt mạng mỗi process
    private val harnessQueued = AtomicBoolean(false)
    private val offLogged = AtomicBoolean(false)
    private val rcSkipLogged = AtomicBoolean(false)
    @Volatile private var app: Context? = null
    @Volatile private var pool: ExecutorService? = null

    private val productHook: (LicenseGate.Verdict) -> Unit = { v -> if (v is LicenseGate.Verdict.Valid) submit(null) }

    /** Gọi ở `AppPluginManager.initPlugin`: chỉ đăng ký nghe verdict giấy phép; không đọc gì, không mạng. */
    fun install(context: Context) {
        try {
            app = context.applicationContext ?: context
            LicenseGate.onVerdict(productHook)
        } catch (e: Throwable) {
            log("policy: error (${e.javaClass.simpleName.ifEmpty { "Unknown" }})")
        }
    }

    /** Harness (3 khoá của `BrainPolicyHarness` + extra `policy_fake_token`): token giả, bỏ yêu cầu [META_PROJECT]. */
    fun runForHarness(context: Context, source: IntegrityTokenSource) {
        if (!harnessQueued.compareAndSet(false, true)) return
        app = app ?: context.applicationContext ?: context
        LicenseGate.onVerdict { v -> if (v is LicenseGate.Verdict.Valid) submit(source) }
    }

    /** `FirHelperConfig` (gọi trong `synchronized(PolicyClient)`): `false` sau khi policy server đã áp (Q3), một dòng log/process. */
    fun remoteConfigAllowed(): Boolean {
        val v = serverVersion ?: return true
        if (rcSkipLogged.compareAndSet(false, true)) log("policy: rc_skipped version=$v source=app")
        return false
    }

    private fun submit(harness: IntegrityTokenSource?) {
        val ctx = app ?: return
        pool().execute { runFlow(ctx, harness) }
    }

    private fun pool(): ExecutorService = synchronized(this) {
        pool ?: ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
            Thread(r, THREAD_NAME).apply { isDaemon = true }
        }.also { pool = it }
    }

    private fun runFlow(ctx: Context, harness: IntegrityTokenSource?) {
        try {
            if (started.get()) return
            if (!brainOn()) return off("brain")
            val meta = metaReader(ctx)
            val url = (meta[META_URL] as? String)?.trim()?.takeIf { validUrl(it) } ?: return off(META_URL)
            val key = (meta[META_API_KEY] as? String)?.takeIf { it.isNotBlank() } ?: return off(META_API_KEY)
            val source = harness ?: projectNumber(meta[META_PROJECT])?.let { tokenSourceFactory(ctx, it) } ?: return off(META_PROJECT)
            if (harness == null && backedOff(ctx)) return
            if (!started.compareAndSet(false, true)) return
            if (harness == null) prefs(ctx).edit().putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis()).commit()
            log(fetch(url, key, source))
        } catch (e: Throwable) {
            log("policy: error (${e.javaClass.simpleName.ifEmpty { "Unknown" }})")
        }
    }

    private fun fetch(url: String, key: String, source: IntegrityTokenSource): String {
        val n = transport.execute("POST", "${url}policy/nonce", mapOf(H_API_KEY to key))
        if (n.code != 200) return "policy: default (nonce_${n.code})"
        val nonce = (parseObject(n.body)?.get("nonce")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString })?.asString
        if (nonce == null || !NONCE.matches(nonce)) return "policy: default (nonce_parse)"
        val token = source.request(nonce, TOKEN_TIMEOUT_MS)
        if (token.isNullOrEmpty()) return "policy: default (no_token)"
        val g = transport.execute("GET", "${url}policy", mapOf(H_API_KEY to key, H_TOKEN to token))
        if (g.code != 200) return "policy: default (http_${g.code})"
        val body = parseObject(g.body) ?: return "policy: default (parse)"
        val src = body.get("source")?.takeIf { it.isJsonPrimitive }?.asString
        if (src != "app") return "policy: default (${reasonOf(body)})"
        val policy = body.get("policy")?.takeIf { it.isJsonObject } ?: return "policy: default (parse)"
        // I-1 (D24-230): cùng khoá với nhánh RC của FirHelperConfig — RC không chen giữa apply và đọc version.
        val (result, version) = synchronized(this) {
            val r = DecisionEngine.instance.applyPolicy(policy.toString())
            r to (if (!r.applied) null else (BrainPolicy.activeVersion().takeIf { VERSION.matches(it) } ?: "?").also { serverVersion = it })
        }
        if (version == null) return "policy: rejected (${result.report.lineSequence().first()})"
        return "policy: applied version=$version source=app"
    }

    /** `reason` của server (`integrity_off|no_token|not_accepted|no_policy|…`) — chỉ chữ thường/số/`_`, còn lại `source`. */
    private fun reasonOf(body: JsonObject): String =
        body.get("reason")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { REASON.matches(it) } ?: "source"

    /** JSON object chặt (không lenient, không dữ liệu thừa), thân ≤ 64 KB; khác → `null`. */
    private fun parseObject(body: String?): JsonObject? = try {
        if (body == null || body.length > PolicyTransport.MAX_BODY_BYTES) null else {
            val reader = JsonReader(StringReader(body))
            val element = Gson().getAdapter(JsonElement::class.java).read(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT) null else element as? JsonObject
        }
    } catch (e: Exception) { null }

    /** #11: `https://<host>/…` hoặc đúng `http://127.0.0.1[:port]/…` — so host đã parse (`URI`), không `startsWith`. */
    private fun validUrl(url: String): Boolean = try {
        val u = java.net.URI(url); val scheme = u.scheme?.lowercase()
        url.endsWith("/") && u.rawUserInfo == null && u.rawQuery == null && u.rawFragment == null && !u.host.isNullOrEmpty() &&
            (scheme == "https" || (scheme == "http" && u.host == "127.0.0.1"))
    } catch (e: Exception) { false }

    /** #28 (D3-240, D15-240): `configure { brainEnabled }` + player trong rollout PHÍA CLIENT — không theo rollout artifact đã áp. */
    private fun brainOn(): Boolean = AdsBrain.enabled && HoldoutManager.isInRollout(PlayerIdSource.current(), AdsBrain.rolloutFraction)

    /**
     * D3-240: lượt trước < 24 h (prefs, liên process) → `policy: skip (backoff <giờ còn lại>h)`; mốc ở tương lai (đồng hồ lùi) không
     * chặn. Mốc ghi (`commit`) trên [THREAD_NAME] TRƯỚC lượt mạng, lỗi giữa chừng vẫn tính; harness (token giả) không xét/ghi mốc.
     */
    private fun backedOff(ctx: Context): Boolean {
        val left = prefs(ctx).getLong(KEY_LAST_ATTEMPT, 0L) + 24 * HOUR_MS - System.currentTimeMillis()
        return (left in 1..24 * HOUR_MS).also { if (it) log("policy: skip (backoff ${(left + HOUR_MS - 1) / HOUR_MS}h)") }
    }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Cloud project number: aapt có thể ghi số dài thành chuỗi hoặc số. > 0, ngược lại `null`. */
    private fun projectNumber(raw: Any?): Long? = when (raw) {
        is Int -> raw.toLong()
        is Long -> raw
        is String -> raw.trim().toLongOrNull()
        else -> null
    }?.takeIf { it > 0 }

    private fun off(key: String) {
        if (offLogged.compareAndSet(false, true)) log("policy: off ($key)")
    }

    private fun log(line: String) {
        try { logSink(line) } catch (e: Throwable) { /* log không được làm hỏng luồng */ }
    }

    private fun readMeta(ctx: Context): Map<String, Any?> {
        val bundle = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA).metaData
        @Suppress("DEPRECATION")
        return listOf(META_URL, META_API_KEY, META_PROJECT).associateWith { bundle?.get(it) }
    }

    /** Test: như process mới (nhả luồng, trả seam mặc định). */
    @JvmSynthetic
    internal fun resetForTest() {
        synchronized(this) { pool?.shutdownNow(); pool = null }
        started.set(false); harnessQueued.set(false); offLogged.set(false); rcSkipLogged.set(false)
        app = null; serverVersion = null
        transport = OkHttpPolicyTransport()
        tokenSourceFactory = { c, p -> PlayIntegrityTokenSource(c, p) }
        metaReader = { readMeta(it) }
        logSink = { Log.i(LogUtil.TAG, it) }
    }

    /** Test: chờ mọi việc đã xếp trên [THREAD_NAME] xong. */
    @JvmSynthetic
    internal fun drainForTest() {
        pool().submit(Runnable {}).get(5, TimeUnit.SECONDS)
    }
}
