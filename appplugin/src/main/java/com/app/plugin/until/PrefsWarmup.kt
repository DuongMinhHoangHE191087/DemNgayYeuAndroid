package com.app.plugin.until

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Nạp sẵn 4–6 file SharedPreferences mà SDK (và UMP, AppsFlyer, AppLovin) đọc trên main lúc cold start, trên một
 * luồng nền riêng (`appplugin-prefs-warmup`), bắt đầu từ `AppPluginBase.attachBaseContext`. `AppPluginBase.onCreate` gọi [await]
 * trước lần đọc đầu (`PreferenceHelper.init`): main chờ tối đa [TIMEOUT_MS]. Sau đó các lần đọc trên main không
 * chạm đĩa: đường dẫn file được cache theo ContextImpl (chính `base` mà Application và UMP dùng), còn
 * `SharedPreferencesImpl` được cache cho cả process và đã nạp xong. Cổng G2 (DOC/65) đòi vi phạm StrictMode do SDK
 * = 0 (`.superpowers/sdd/research-startup-telemetry.md` A2, A5 phương án 1). Thứ tự các lần đọc không đổi.
 *
 * Không đọc đĩa trên luồng gọi ở nhánh nào, kể cả trong `attachBaseContext`: StrictMode của app demo chỉ bật ở
 * `onCreate`, nên đọc trên main ở đó chỉ giấu vi phạm khỏi cổng, việc đọc vẫn nằm trên main (A1).
 * Hết hạn chờ thì main đọc như trước (chặn trong `awaitLoadedLocked` tới khi file nạp xong, việc nạp đã chạy từ
 * trước), nên main không chờ lâu hơn khi không có warm-up; việc không gấp thì để [onLoaded] chạy sau khi nạp (2.1.0 P1).
 * 4 file gốc nạp trước, rồi mới dò `af_app_id`/AppLovin và nạp file của chúng (1.6.2 Task 1); latch mở sau cả hai
 * phần; luồng nền log `prefs warmup loaded: base=… ms, optional=… ms`.
 */
internal object PrefsWarmup {

    /** Main chờ tối đa bao lâu trong [await]. */
    const val TIMEOUT_MS = 250L

    private const val THREAD_NAME = "appplugin-prefs-warmup"

    /** File của `PreferenceHelper.init`. */
    private const val PREFERENCE_HELPER_FILE = "XgameHCM-BaseAndroid"

    /**
     * File nội bộ của UMP 4.0.0 (bytecode `zzaq.<init>`: `Application.getSharedPreferences` rồi
     * `getStringSet("written_values")`), đọc trên main qua `ConsentManager.canStartAdSdks`. Không phải API của UMP:
     * nâng bản UMP mà tên đổi thì warm-up nạp một file thừa (vô hại) và cổng G2 sẽ bắt lại hai vi phạm của UMP.
     */
    private const val UMP_INTERNAL_FILE = "__GOOGLE_FUNDING_CHOICE_SDK_INTERNAL__"

    /** Lần [start] đầu tiên; `null` khi chưa start trong process này. */
    private val warmed = AtomicReference<Load?>(null)

    /** Latch của một lần [start] + các block [onLoaded] chờ nạp xong; khoá = chính đối tượng này. */
    private class Load : CountDownLatch(1) { val after = ArrayList<() -> Unit>() }

    /**
     * File prefs AppsFlyer 6.16.2 đọc trên main trong `AppsFlyerLib.init` (stack `t160-t8b-strictmode.log`:
     * SharedPreferencesImpl.getString ← AFc1qSDK ← AFa1zSDK.init ← AppsflyerHelper.startAfterConsent). Chuỗi trong
     * bytecode bị mã hoá; tên lấy từ `shared_prefs/` trên máy sau init (1.6.1 Task 1, log t161-t1-emu37-prefs.log).
     * Chỉ nạp khi host có `af_app_id` (không có thì AppsFlyer không init). Đổi bản mà tên đổi: nạp một file thừa,
     * G2 bắt lại.
     */
    private const val APPSFLYER_FILE = "appsflyer-data"

    /**
     * AppLovin 13.6.2: `AppLovinPrivacySettings.setDoNotSell` (AdsMaxMy.applyGpp, main, trước initialize) ghi file
     * này (review Task 8a m3). Không đo được trên demo (không devKey, O10).
     */
    private const val APPLOVIN_FILE = "com.applovin.sdk.shared"
    private const val MAX_SDK_CLASS = "com.applovin.sdk.AppLovinSdk"

    /** 4 file gốc rồi file tuỳ chọn (đọc resource, dò lớp: chỉ trên luồng warm-up); cho test và dump, `warmAll` gọi thẳng. */
    fun names(context: Context): List<String> = baseNames(context) + optionalNames(context)

    /** Thứ tự = thứ tự SDK đọc lúc cold start; AppsFlyer/AppLovin đọc sau UMP (sau khi cổng analytics/mạng mở). */
    internal fun names(context: Context, hasAppsFlyerId: Boolean, hasMax: Boolean): List<String> =
        baseNames(context) + optionalNames(hasAppsFlyerId, hasMax)

    /** 4 file main đọc trong `AppPluginBase.onCreate` (PreferenceHelper, PreUtil, IABTCF của CMP, UMP). Không dò gì. */
    private fun baseNames(context: Context): List<String> = listOf(
        PREFERENCE_HELPER_FILE, PreUtil.PREFF_FILE, "${context.packageName}_preferences", UMP_INTERNAL_FILE,
    )

    /** File tuỳ chọn: dò `af_app_id` và lớp AppLovin — gọi SAU khi 4 file gốc đã nạp (review cuối 1.6.1 m1). */
    private fun optionalNames(context: Context): List<String> = optionalNames(
        hasAppsFlyerId = runCatching { AppUtil.getResourceString(context, "af_app_id").isNotEmpty() }.getOrDefault(false),
        // Như AdsHelper.isMaxSdkAvailable, không nạp lớp AdsHelper sớm; initialize=false: không chạy static init AppLovin.
        hasMax = runCatching { Class.forName(MAX_SDK_CLASS, false, PrefsWarmup::class.java.classLoader) }.isSuccess,
    )

    private fun optionalNames(hasAppsFlyerId: Boolean, hasMax: Boolean): List<String> = buildList {
        if (hasAppsFlyerId) add(APPSFLYER_FILE)
        if (hasMax) add(APPLOVIN_FILE)
    }

    /** Nạp [names] trên luồng daemon [THREAD_NAME]; gọi lại là no-op; 0 đĩa trên luồng gọi; lỗi từng file log, latch luôn mở. */
    fun start(base: Context) {
        val latch = Load()
        if (!warmed.compareAndSet(null, latch)) return
        try {
            Thread({ warmAll(base, latch) }, THREAD_NAME).apply { isDaemon = true }.start()
        } catch (t: Throwable) {
            finish(latch)
            LogUtil.logE("prefs warmup thread not started, main reads as before: $t")
        }
    }

    private fun warmAll(base: Context, latch: Load) {
        try {
            // m1 (1.6.1): 4 file gốc nạp trước mọi việc dò. P1-2 (D16): mở cả 4 trước (mỗi file tự nạp trên luồng riêng → song song), rồi chờ.
            val t0 = System.nanoTime()
            baseNames(base).map { it to runCatching { base.getSharedPreferences(it, Context.MODE_PRIVATE) } }.forEach { (n, p) -> warmOne(base, n, p) }
            val baseMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
            optionalNames(base).forEach { warmOne(base, it) }
            val allMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
            // Số của #12 (quyết Task 1b): tách thời gian file gốc khỏi file tuỳ chọn. Log thẳng như await() (trước cờ LogUtil).
            Log.d(LogUtil.TAG, "prefs warmup loaded: base=$baseMs ms, optional=${allMs - baseMs} ms")
        } catch (t: Throwable) {
            LogUtil.logE("prefs warmup failed, main reads as before: $t")
        } finally {
            finish(latch)
        }
    }

    /** Mở latch rồi chạy các block [onLoaded] đã đăng ký, trên luồng gọi (luồng warm-up); lỗi của block được log, không ném. */
    private fun finish(load: Load) = synchronized(load) { load.countDown(); load.after.toList() }
        .forEach { block -> runCatching(block).onFailure { LogUtil.logE("prefs onLoaded block failed: $it") } }

    private fun warmOne(base: Context, name: String, opened: Result<SharedPreferences> =
        runCatching { base.getSharedPreferences(name, Context.MODE_PRIVATE) }) {
        try {
            opened.getOrThrow().contains("") // chờ SharedPreferencesImpl nạp xong (awaitLoadedLocked) trên luồng này
        } catch (t: Throwable) {
            // Direct Boot: prefs trên credential storage ném IllegalStateException khi máy chưa mở khoá.
            LogUtil.logE("prefs warmup $name failed, main reads it as before: $t")
        }
    }

    /**
     * Chờ lần nạp của [start] xong, tối đa [timeoutMs]; trả số ms đã chờ. Không ném. Log một dòng thẳng bằng
     * `Log`, không qua cờ LogUtil: chạy từ `Application.onCreate`, trước khi cờ log bật (như `FcmConsent`).
     */
    fun await(timeoutMs: Long = TIMEOUT_MS): Long {
        val latch = warmed.get()
        if (latch == null) {
            Log.d(LogUtil.TAG, "prefs warmup not started in this process, main reads as before")
            return 0
        }
        val startedAt = System.nanoTime()
        val done = try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            Log.w(LogUtil.TAG, "prefs warmup wait interrupted after $waitedMs ms, main reads as before")
            return waitedMs
        }
        val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        if (done) {
            Log.d(LogUtil.TAG, "prefs warmup waited=$waitedMs ms (timeout=$timeoutMs ms)")
        } else {
            Log.w(LogUtil.TAG, "prefs warmup timeout after $timeoutMs ms, main reads as before")
        }
        return waitedMs
    }

    /** Lần nạp đã xong, hoặc không start trong process này (main đọc như trước). */
    internal fun isLoaded(): Boolean = (warmed.get()?.count ?: 0L) == 0L

    /** 2.1.0 P1: [block] chạy đúng một lần — ngay trên luồng gọi khi [isLoaded], không thì trên luồng warm-up ngay sau khi nạp xong. */
    internal fun onLoaded(block: () -> Unit) {
        val load = warmed.get()
        if (load == null || !synchronized(load) { load.count > 0 && load.after.add(block) }) block()
    }

    /** Chỉ cho test: quên lần [start] trước. Luồng cũ (nếu còn chạy) chỉ mở latch của riêng nó. */
    internal fun resetForTest() = warmed.set(null)
}
