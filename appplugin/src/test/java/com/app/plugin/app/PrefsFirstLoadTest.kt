package com.app.plugin.app

import android.content.Context
import android.content.SharedPreferences
import com.app.plugin.until.Constants
import com.app.plugin.until.PreferenceHelper
import com.app.plugin.until.PrefsWarmup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 2.1.0 P1 (M47, DRY-210 §3g, D10): lần nạp prefs đầu chậm (Samsung sau `pm clear` ≈ 313 ms > 250 ms) không chặn main ngoài
 * consent. `consent_tier`, `TIME_OPEN`, `EXPERIMENT_GROUP` vẫn đồng bộ trên main trước `initPlugin`; 9 cờ đặt lại lúc mở app
 * (`IS_FETCH_FIREBASE` + 8 `InitAds.*`, 0 nơi đọc) chạy qua [PrefsWarmup.onLoaded] trên luồng warm-up chỉ khi `await()` hết hạn.
 *
 * Mỗi `apply()` của editor giả ghi (tên luồng, khoá → giá trị). Warm-up chạy luồng thật; [hang] giữ file đầu tiên để `await` hết hạn.
 */
class PrefsFirstLoadTest {
    private data class Edit(val thread: String, val values: Map<String, Any?>)

    private val edits = CopyOnWriteArrayList<Edit>()
    private val hang = CountDownLatch(1)
    private val helper = PreferenceHelper().apply { preferences = recordingPrefs(timeOpen = 6) }

    @Before fun setUp() = PrefsWarmup.resetForTest()

    @After fun tearDown() {
        hang.countDown()
        PrefsWarmup.resetForTest()
    }

    /** SharedPreferences giả: `getInt(TIME_OPEN)` = [timeOpen]; mỗi `edit()` một editor mới, `apply()` ghi vào [edits]. */
    private fun recordingPrefs(timeOpen: Int): SharedPreferences {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getInt(Constants.Common.TIME_OPEN, 0)).thenReturn(timeOpen)
        `when`(prefs.edit()).thenAnswer { RecordingEditor() }
        return prefs
    }

    private inner class RecordingEditor : SharedPreferences.Editor {
        private val values = LinkedHashMap<String, Any?>()
        private fun put(k: String?, v: Any?): SharedPreferences.Editor { values[requireNotNull(k)] = v; return this }
        override fun putString(k: String?, v: String?) = put(k, v)
        override fun putStringSet(k: String?, v: MutableSet<String>?) = put(k, v)
        override fun putInt(k: String?, v: Int) = put(k, v)
        override fun putLong(k: String?, v: Long) = put(k, v)
        override fun putFloat(k: String?, v: Float) = put(k, v)
        override fun putBoolean(k: String?, v: Boolean) = put(k, v)
        override fun remove(k: String?) = put(k, null)
        override fun clear(): SharedPreferences.Editor { values.clear(); return this }
        override fun commit(): Boolean = true.also { apply() }
        override fun apply() { edits.add(Edit(Thread.currentThread().name, LinkedHashMap(values))) }
    }

    /** Context cho `PrefsWarmup.start`: [hold] = file đầu tiên treo tới khi [hang] mở (warm-up hết hạn). */
    private fun warmupContext(hold: Boolean): Context {
        val ctx = mock(Context::class.java)
        `when`(ctx.packageName).thenReturn("com.example.app")
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenAnswer {
            if (hold) hang.await(HANG_LIMIT_S, TimeUnit.SECONDS)
            mock(SharedPreferences::class.java)
        }
        return ctx
    }

    private fun startExpired() {
        PrefsWarmup.start(warmupContext(hold = true))
        PrefsWarmup.await(SHORT_TIMEOUT_MS)
        assertTrue("warm-up phải đang treo (nhánh hết hạn)", !PrefsWarmup.isLoaded())
    }

    private fun startInTime() {
        PrefsWarmup.start(warmupContext(hold = false))
        PrefsWarmup.await(LONG_TIMEOUT_MS)
        assertTrue("warm-up phải xong (nhánh kịp)", PrefsWarmup.isLoaded())
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(HANG_LIMIT_S)
        while (!cond() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("hết hạn chờ: $what; edits=$edits", cond())
    }

    private val caller get() = Thread.currentThread().name
    private val syncValues = mapOf(Constants.Common.TIME_OPEN to 7, Constants.EXPERIMENT_GROUP to 4)

    // F1 (KB-3): hết hạn → 9 cờ ghi trên luồng warm-up sau khi nạp xong, KHÔNG trên luồng gọi (cũ: một edit 11 khoá trên main).
    @Test fun `F1 warm-up het han thi 9 co dat lai ghi tren luong warm-up, khong tren luong goi`() {
        startExpired()
        AppPluginBase.resetOnOpen(helper)

        assertEquals("luồng gọi chỉ ghi TIME_OPEN + EXPERIMENT_GROUP, một edit", listOf(Edit(caller, syncValues)), edits.toList())
        hang.countDown()
        waitFor("edit 9 cờ trên luồng warm-up") { edits.size >= 2 }
        assertEquals(Edit(WARMUP_THREAD, FLAGS.associateWith { false }), edits[1])
        assertEquals("đúng 2 edit", 2, edits.size)
    }

    // F2 (đặc tả): warm-up kịp (hoặc không start) → như cũ: một edit 11 khoá, trên luồng gọi, xong trước khi hàm trả về.
    @Test fun `F2 warm-up kip thi mot edit 11 khoa tren luong goi nhu cu`() {
        val old = syncValues + FLAGS.associateWith { false }
        AppPluginBase.resetOnOpen(helper)   // không start trong process
        startInTime()
        AppPluginBase.resetOnOpen(helper)

        assertEquals("mỗi lần mở: một edit 11 khoá trên luồng gọi", listOf(Edit(caller, old), Edit(caller, old)), edits.toList())
        assertEquals("11 khoá", 11, edits[1].values.size)
    }

    // F3: consent restore rồi TIME_OPEN/EXPERIMENT_GROUP (đọc + ghi đồng bộ) trước initPlugin ở cả hai nhánh (R-P1, D10).
    @Test fun `F3 consent va TIME_OPEN EXPERIMENT_GROUP truoc initPlugin o ca hai nhanh`() {
        val l = File("src/main/java/com/app/plugin/app/AppPluginBase.kt").readLines().map { it.trim() }
        val onCreate = l.indexOfFirst { it.startsWith("override fun onCreate()") }
        fun at(line: String) = l.drop(onCreate).indexOf(line).let { if (it < 0) -1 else it + onCreate }
        val order = listOf("PrefsWarmup.await()", "PreferenceHelper.getInstance().init(this)", "restoreConsentTier()",
            "resetOnOpen(PreferenceHelper.getInstance())", "initPlugin()").map { it to at(it) }
        assertTrue("thứ tự onCreate sai (dòng nguyên, không bọc luồng): $order",
            onCreate >= 0 && order.all { it.second > onCreate } && order.zipWithNext().all { (a, b) -> a.second < b.second })
        for ((name, start) in listOf("hết hạn" to ::startExpired, "kịp" to ::startInTime)) {
            edits.clear(); PrefsWarmup.resetForTest(); start()
            AppPluginBase.resetOnOpen(helper)
            val first = edits.firstOrNull()
            assertTrue("$name: TIME_OPEN=7 + EXPERIMENT_GROUP=4 phải ghi trên luồng gọi trước khi trả về: $edits",
                first != null && first.thread == caller && syncValues.all { (k, v) -> first.values[k] == v })
        }
    }

    // F4: block của onLoaded chạy đúng một lần — gọi trước khi nạp xong (luồng warm-up, sau khi nạp), sau khi nạp, hoặc không start.
    @Test fun `F4 onLoaded chay block dung mot lan du goi truoc hay sau khi nap`() {
        val runs = CopyOnWriteArrayList<String>()
        PrefsWarmup.onLoaded { runs.add("none:$caller") }
        assertEquals("không start → chạy ngay", listOf("none:$caller"), runs.toList())

        startExpired()
        val before = AtomicInteger()
        PrefsWarmup.onLoaded { runs.add("before:${Thread.currentThread().name}"); before.incrementAndGet() }
        assertEquals("chưa nạp xong thì chưa chạy", 0, before.get())
        hang.countDown()
        waitFor("block đăng ký trước khi nạp") { before.get() >= 1 }
        PrefsWarmup.onLoaded { runs.add("after:$caller") }
        Thread.sleep(SETTLE_MS)
        assertEquals(listOf("none:$caller", "before:$WARMUP_THREAD", "after:$caller"), runs.toList())
    }

    // F5 (P1-2, D16): 4 file gốc mở hết (mỗi SharedPreferencesImpl tự nạp trên luồng riêng → song song) rồi mới chờ file đầu.
    @Test fun `F5 warm-up mo ca 4 file goc truoc lan cho dau tien`() {
        val events = CopyOnWriteArrayList<String>()
        val ctx = mock(Context::class.java)
        `when`(ctx.packageName).thenReturn("com.example.app")
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenAnswer { inv ->
            val name = inv.getArgument<String>(0)
            events.add("open:$name")
            mock(SharedPreferences::class.java).also { prefs ->
                `when`(prefs.contains("")).thenAnswer { Thread.sleep(LOAD_DELAY_MS); events.add("await:$name"); false }
            }
        }
        PrefsWarmup.start(ctx)
        PrefsWarmup.await(LONG_TIMEOUT_MS)

        val base = listOf("XgameHCM-BaseAndroid", "App_prefer_file", "com.example.app_preferences", "__GOOGLE_FUNDING_CHOICE_SDK_INTERNAL__")
        assertEquals("4 lần mở trước lần chờ đầu: $events", base.map { "open:$it" } + base.map { "await:$it" }, events.take(8))
    }

    private companion object {
        const val LOAD_DELAY_MS = 20L
        const val WARMUP_THREAD = "appplugin-prefs-warmup"
        const val SHORT_TIMEOUT_MS = 30L
        const val LONG_TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 150L
        const val HANG_LIMIT_S = 10L
        val FLAGS = listOf(
            Constants.Common.IS_FETCH_FIREBASE, Constants.InitAds.NT_INTRO_LOADED, Constants.InitAds.NT_LANGUAGE_2_LOADED,
            Constants.InitAds.NT_LANGUAGE_LOADED, Constants.InitAds.FULL_SPLASH_LOADED, Constants.InitAds.FULL_2_SPLASH_LOADED,
            Constants.InitAds.RECT_SPLASH_LOADED, Constants.InitAds.FULL_SPLASH_SHOWED, Constants.InitAds.IS_LOAD_FULL,
        )
    }
}
