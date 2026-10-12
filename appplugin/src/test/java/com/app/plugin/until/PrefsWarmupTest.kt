package com.app.plugin.until

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito.after
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [PrefsWarmup] (DOC/66 T1, Task B; 1.6.1 Task 1 thêm AppsFlyer/AppLovin): 4–6 file SharedPreferences nạp trên luồng nền từ `attachBaseContext`, main chờ
 * tối đa 250 ms trước lần đọc đầu, để cổng G2 đòi vi phạm StrictMode do SDK = 0 mà không đổi thứ tự khởi tạo.
 *
 * Luồng nền là luồng thật (không phải `HeldExecutor`): điều cần kiểm chính là lần nạp chạy trên luồng khác luồng gọi.
 * Context giả ghi (tên file, tên luồng) mỗi lần `getSharedPreferences`. `mockStatic(Log)` chỉ có hiệu lực trên luồng
 * test, nên chỉ kiểm dòng log của [PrefsWarmup.await] (chạy trên luồng gọi).
 */
class PrefsWarmupTest {
    private val pkg = "com.example.game"
    /** 4 file gốc: SDK và UMP đọc trên main lúc cold start. */
    private val baseNames = listOf(
        "XgameHCM-BaseAndroid",
        "App_prefer_file",
        "com.example.game_preferences",
        UMP_FILE
    )
    /** Host có `af_app_id` và SDK AppLovin (classpath test có AppLovin): 4 file gốc + AppsFlyer + AppLovin. */
    private val allNames = baseNames + APPSFLYER_FILE + APPLOVIN_FILE
    private val expectedNames = allNames

    /** (tên file, tên luồng) của mỗi lần `getSharedPreferences`, theo thứ tự gọi. */
    private val calls = CopyOnWriteArrayList<Pair<String, String>>()
    /** Sự kiện chung theo thứ tự: `prefs:<tên>` mỗi lần `getSharedPreferences`, `probe:af_app_id` mỗi lần dò (1.6.2 Task 1). */
    private val events = CopyOnWriteArrayList<String>()
    private val prefsByName = expectedNames.associateWith { mock(SharedPreferences::class.java) }

    /** Giữ luồng nền treo trong ca (d); mở ở [tearDown] để luồng đó kết thúc. */
    private val hang = CountDownLatch(1)

    @Before fun setUp() = PrefsWarmup.resetForTest()

    @After fun tearDown() {
        hang.countDown()
        PrefsWarmup.resetForTest()
    }

    /**
     * [afResId] = id của string `af_app_id` (0 = host không khai báo AppsFlyer). [onProbe] chạy trong lần dò
     * `af_app_id` (vd treo); đứng trước [prefsFor] để các test gọi `context { name -> … }` bằng trailing lambda.
     */
    private fun context(
        afResId: Int = AF_RES_ID,
        onProbe: () -> Unit = {},
        prefsFor: (String) -> SharedPreferences = { prefsByName[it] ?: mock(SharedPreferences::class.java) }
    ): Context {
        val ctx = mock(Context::class.java)
        val res = mock(Resources::class.java)
        `when`(ctx.packageName).thenReturn(pkg)
        `when`(ctx.resources).thenReturn(res)
        `when`(res.getIdentifier("af_app_id", "string", pkg)).thenAnswer {
            events.add("probe:af_app_id")
            onProbe()
            afResId
        }
        `when`(res.getString(AF_RES_ID)).thenReturn("debugkey")
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenAnswer { inv ->
            val name = inv.getArgument<String>(0)
            calls.add(name to Thread.currentThread().name)
            events.add("prefs:$name")
            prefsFor(name)
        }
        return ctx
    }

    private fun elapsedMs(since: Long) = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - since)

    // (a)
    @Test fun `names la 4 file SDK va UMP roi AppsFlyer va AppLovin, dung thu tu`() =
        assertEquals(expectedNames, PrefsWarmup.names(context()))

    // C1 (KB-2): host không có `af_app_id` thì AppsFlyer không init, không nạp file của nó; 4 file gốc giữ thứ tự.
    @Test fun `names khi host khong co af_app_id thi khong co file AppsFlyer, 4 file goc dung thu tu`() {
        val names = PrefsWarmup.names(context(afResId = 0))
        assertFalse("không có af_app_id mà vẫn nạp file AppsFlyer: $names", names.contains(APPSFLYER_FILE))
        assertEquals(baseNames, names.take(baseNames.size))
    }

    // R1 (KB-3): `af.init` đọc file prefs AppsFlyer trên main (t161-t1-emu37-strictmode-red.log) → phải nạp sẵn.
    @Test fun `names khi host co af_app_id thi co file prefs AppsFlyer`() {
        val names = PrefsWarmup.names(context())
        assertTrue("thiếu file prefs AppsFlyer $APPSFLYER_FILE: $names", names.contains(APPSFLYER_FILE))
    }

    // R2 (KB-3): AppLovin có trên classpath → `setDoNotSell` trên main đọc file này → phải nạp sẵn (T8a m3).
    @Test fun `names khi co SDK AppLovin thi co file prefs AppLovin`() {
        val names = PrefsWarmup.names(context())
        assertTrue("thiếu file prefs AppLovin $APPLOVIN_FILE: $names", names.contains(APPLOVIN_FILE))
    }

    // R3 (KB-3): AppsFlyer/AppLovin đọc sau UMP (sau khi cổng mở) → hai file mới đứng sau file UMP.
    @Test fun `hai file AppsFlyer va AppLovin dung sau file UMP`() {
        val names = PrefsWarmup.names(context())
        val ump = names.indexOf(UMP_FILE)
        assertTrue("file UMP phải có: $names", ump >= 0)
        assertTrue("file AppsFlyer phải đứng sau file UMP: $names", names.indexOf(APPSFLYER_FILE) > ump)
        assertTrue("file AppLovin phải đứng sau file UMP: $names", names.indexOf(APPLOVIN_FILE) > ump)
    }

    // R4 (KB-3): start nạp đủ 6 file, đúng thứ tự, chỉ trên luồng warm-up.
    @Test fun `start nap 6 file dung thu tu tren luong warm-up, 0 tren luong goi`() {
        PrefsWarmup.start(context())
        PrefsWarmup.await(LONG_TIMEOUT_MS)

        assertEquals("tên file nạp trên luồng warm-up", allNames, calls.filter { it.second == WARMUP_THREAD }.map { it.first })
        assertEquals("số lần nạp trên luồng gọi", 0, calls.count { it.second == Thread.currentThread().name })
    }

    // m2 (review Task 1): overload 3 tham số — nhánh không MAX không có file AppLovin; chỉ AppsFlyer khi có af_app_id.
    @Test fun `names 3 tham so khong MAX thi khong co file AppLovin`() {
        val ctx = context()
        assertEquals(baseNames, PrefsWarmup.names(ctx, hasAppsFlyerId = false, hasMax = false))
        assertEquals(baseNames + APPSFLYER_FILE, PrefsWarmup.names(ctx, hasAppsFlyerId = true, hasMax = false))
        assertEquals(baseNames + APPLOVIN_FILE, PrefsWarmup.names(ctx, hasAppsFlyerId = false, hasMax = true))
    }

    // m3 (review Task 1): đọc `af_app_id` ném thì coi như không có AppsFlyer; 4 file gốc vẫn được nạp.
    @Test fun `doc af_app_id nem thi van nap 4 file goc, khong co file AppsFlyer`() {
        val ctx = context()
        val broken = mock(Resources::class.java)
        `when`(broken.getIdentifier(anyString(), anyString(), anyString())).thenThrow(RuntimeException("resources gone"))
        `when`(ctx.resources).thenReturn(broken)

        assertEquals(baseNames + APPLOVIN_FILE, PrefsWarmup.names(ctx))
        calls.clear()
        PrefsWarmup.start(ctx)
        PrefsWarmup.await(LONG_TIMEOUT_MS)
        assertEquals(baseNames + APPLOVIN_FILE, calls.filter { it.second == WARMUP_THREAD }.map { it.first })
    }

    // m6 (review Task 1): latch chỉ mở sau khi file AppsFlyer nạp xong — chỉ file đó treo thì await hết hạn.
    @Test fun `chi file AppsFlyer treo thi await het han, latch phu ca file AppsFlyer`() {
        val ctx = context { name ->
            if (name == APPSFLYER_FILE) hang.await(HANG_LIMIT_S, TimeUnit.SECONDS)
            prefsByName.getValue(name)
        }
        mockStatic(Log::class.java).use { log ->
            PrefsWarmup.start(ctx)
            val t0 = System.nanoTime()
            val waited = PrefsWarmup.await()
            val elapsed = elapsedMs(t0)

            assertTrue("latch mở trước khi file AppsFlyer nạp xong: elapsed=$elapsed", elapsed >= PrefsWarmup.TIMEOUT_MS)
            assertTrue("waited=$waited", waited >= PrefsWarmup.TIMEOUT_MS)
            assertEquals(baseNames + APPSFLYER_FILE, calls.map { it.first })
            log.verify { Log.w(LogUtil.TAG, "prefs warmup timeout after 250 ms, main reads as before") }
            log.verify({ Log.d(eq(LogUtil.TAG), startsWith("prefs warmup waited=")) }, never())
        }
    }

    // R1 (1.6.2 Task 1, m1): dò af_app_id chỉ chạy sau khi 4 file gốc đã nạp.
    @Test fun `4 file goc nap truoc khi do af_app_id`() {
        PrefsWarmup.start(context())
        PrefsWarmup.await(LONG_TIMEOUT_MS)
        val probe = events.indexOf("probe:af_app_id")
        val baseAt = baseNames.map { events.indexOf("prefs:$it") }
        assertTrue("thiếu file gốc (chưa nạp): $events", baseAt.none { it < 0 })
        assertTrue("dò af_app_id chạy trước khi 4 file gốc nạp xong: $events", probe > baseAt.max())
    }

    // R2 (1.6.2 Task 1, m1): dò treo thì await hết hạn (một latch) nhưng 4 file gốc ĐÃ nạp — main đọc từ cache.
    @Test fun `do af_app_id treo thi await het han nhung 4 file goc da nap`() {
        val ctx = context(onProbe = { hang.await(HANG_LIMIT_S, TimeUnit.SECONDS) })
        mockStatic(Log::class.java).use { log ->
            PrefsWarmup.start(ctx)
            val waited = PrefsWarmup.await()
            assertTrue("waited=$waited", waited >= PrefsWarmup.TIMEOUT_MS)
            assertEquals("file đã nạp khi await hết hạn", baseNames, calls.map { it.first })
            log.verify { Log.w(LogUtil.TAG, "prefs warmup timeout after 250 ms, main reads as before") }
        }
    }

    /**
     * m1 (review Task 1): dò AppLovin (`Class.forName`, không có seam để ghi sự kiện trên luồng warm-up) cũng phải nằm
     * sau vòng 4 file gốc. Quét thân `warmAll` như test nối `AppPluginBase` bên dưới; bỏ dòng chú thích.
     */
    @Test fun `warmAll khong do gi truoc vong 4 file goc`() {
        val source = File("src/main/java/com/app/plugin/until/PrefsWarmup.kt")
        assertTrue("Không thấy ${source.absolutePath} (test phải chạy từ gốc module)", source.isFile)
        val lines = source.readLines()
        val start = lines.indexOfFirst { it.contains("private fun warmAll(") }
        assertTrue("không thấy warmAll", start >= 0)
        val end = (start + 1 until lines.size).first { lines[it].startsWith("    }") }
        val body = lines.subList(start + 1, end).map { it.trim() }.filter { !it.startsWith("//") }
        val baseLoop = body.indexOfFirst { it.startsWith("baseNames(base).") }   // P1-2 (D16): `.map { mở }.forEach { chờ }`
        val probes = listOf("Class.forName", "MAX_SDK_CLASS", "getResourceString", "getIdentifier", "optionalNames(", "names(")
        val probeLines = body.indices.filter { i -> probes.any { body[i].contains(it) } && !body[i].startsWith("baseNames(") }
        assertTrue("warmAll phải nạp 4 file gốc bằng một vòng baseNames(base).…: $body", baseLoop >= 0)
        assertTrue("warmAll phải dò file tuỳ chọn sau vòng gốc: $body", probeLines.isNotEmpty())
        assertTrue("warmAll dò trước vòng 4 file gốc: ${probeLines.map { body[it] }}", probeLines.all { it > baseLoop })
    }

    // m2 (review Task 1): warmAll ném ngoài warmOne (packageName ném khi dựng tên file) thì latch vẫn mở ở finally —
    // await trả ngay (log waited=), không phải chờ hết hạn.
    @Test fun `warmAll nem ngoai warmOne thi latch van mo, await tra ngay`() {
        val ctx = context()
        doThrow(IllegalStateException("packageName gone")).`when`(ctx).packageName
        mockStatic(Log::class.java).use { log ->
            PrefsWarmup.start(ctx)
            val waited = PrefsWarmup.await(LONG_TIMEOUT_MS)

            assertTrue("latch không mở khi warmAll ném: waited=$waited", waited < LONG_TIMEOUT_MS)
            assertTrue("không file nào được nạp: $calls", calls.isEmpty())
            log.verify { Log.d(LogUtil.TAG, "prefs warmup waited=$waited ms (timeout=$LONG_TIMEOUT_MS ms)") }
            log.verify({ Log.w(anyString(), anyString()) }, never())
        }
    }

    // (b)
    @Test fun `start nap ca 6 file tren luong appplugin-prefs-warmup, khong tren luong goi, await tra ve khi xong`() {
        mockStatic(Log::class.java).use { log ->
            PrefsWarmup.start(context())
            val waited = PrefsWarmup.await(LONG_TIMEOUT_MS)

            assertTrue("waited=$waited", waited >= 0)
            assertEquals(expectedNames, calls.map { it.first })
            assertEquals(setOf("appplugin-prefs-warmup"), calls.map { it.second }.toSet())
            assertTrue(calls.none { it.second == Thread.currentThread().name })
            // contains() ép SharedPreferencesImpl nạp xong trên luồng nền (awaitLoadedLocked chạy ở đó, không ở main).
            expectedNames.forEach { verify(prefsByName.getValue(it)).contains("") }
            log.verify { Log.d(LogUtil.TAG, "prefs warmup waited=$waited ms (timeout=$LONG_TIMEOUT_MS ms)") }
            log.verify({ Log.w(anyString(), anyString()) }, never())
        }
    }

    // (c)
    @Test fun `mot file nem IllegalStateException (Direct Boot) thi 5 file con lai van nap va latch van mo`() {
        val locked = expectedNames[1]
        val ctx = context { name ->
            if (name == locked) throw IllegalStateException("credential storage locked")
            prefsByName.getValue(name)
        }
        mockStatic(Log::class.java).use { log ->
            PrefsWarmup.start(ctx)
            PrefsWarmup.await(LONG_TIMEOUT_MS)

            assertEquals(expectedNames, calls.map { it.first })
            expectedNames.filter { it != locked }.forEach { verify(prefsByName.getValue(it)).contains("") }
            verify(prefsByName.getValue(locked), never()).contains(anyString())
            log.verify { Log.d(eq(LogUtil.TAG), startsWith("prefs warmup waited=")) }
            log.verify({ Log.w(anyString(), anyString()) }, never())
        }
    }

    // (d)
    @Test fun `luong nen treo thi await tra ve sau khoang TIMEOUT_MS, log timeout, khong nem`() {
        val ctx = context { name ->
            hang.await(HANG_LIMIT_S, TimeUnit.SECONDS)
            prefsByName.getValue(name)
        }
        mockStatic(Log::class.java).use { log ->
            PrefsWarmup.start(ctx)
            val t0 = System.nanoTime()
            val waited = PrefsWarmup.await()
            val elapsed = elapsedMs(t0)

            assertEquals(250L, PrefsWarmup.TIMEOUT_MS)
            assertTrue("elapsed=$elapsed", elapsed >= PrefsWarmup.TIMEOUT_MS && elapsed < PrefsWarmup.TIMEOUT_MS + SLACK_MS)
            assertTrue("waited=$waited", waited >= PrefsWarmup.TIMEOUT_MS)
            log.verify { Log.w(LogUtil.TAG, "prefs warmup timeout after 250 ms, main reads as before") }
            log.verify({ Log.d(eq(LogUtil.TAG), startsWith("prefs warmup waited=")) }, never())
        }
    }

    // (e)
    @Test fun `start hai lan thi mot luong, moi file nap mot lan`() {
        val ctx = context()
        PrefsWarmup.start(ctx)
        PrefsWarmup.start(ctx)
        PrefsWarmup.await(LONG_TIMEOUT_MS)

        // Chờ thêm một nhịp: luồng thứ hai (nếu có) không được nạp lại.
        verify(ctx, after(SETTLE_MS).times(expectedNames.size)).getSharedPreferences(anyString(), anyInt())
        assertEquals(expectedNames, calls.map { it.first })
        assertEquals(1, calls.map { it.second }.toSet().size)
    }

    @Test fun `await khi chua start thi tra ngay va log ly do`() {
        mockStatic(Log::class.java).use { log ->
            val t0 = System.nanoTime()
            assertEquals(0L, PrefsWarmup.await())
            assertTrue(elapsedMs(t0) < PrefsWarmup.TIMEOUT_MS)
            log.verify { Log.d(eq(LogUtil.TAG), startsWith("prefs warmup not started")) }
        }
    }

    /**
     * Nối vào `AppPluginBase`: `attachBaseContext` gọi `super` rồi [PrefsWarmup.start]; `onCreate` gọi
     * [PrefsWarmup.await] sau khi loại process phụ và trước lần đọc prefs đầu (`PreferenceHelper.init`). Quét mã nguồn
     * như `LogEventInitOrderContractTest`; bỏ qua dòng chú thích.
     */
    @Test fun `AppPluginBase start o attachBaseContext, await truoc PreferenceHelper init`() {
        val source = File("src/main/java/com/app/plugin/app/AppPluginBase.kt")
        assertTrue("Không thấy ${source.absolutePath} (test phải chạy từ gốc module)", source.isFile)
        val lines = source.readLines()
        fun first(call: String): Int = lines.indexOfFirst { line ->
            val code = line.trim()
            !code.startsWith("//") && !code.startsWith("*") && code.contains(call)
        }

        val attach = first("override fun attachBaseContext(base: Context)")
        val superAttach = first("super.attachBaseContext(base)")
        val start = first("PrefsWarmup.start(base)")
        val mainOnly = first("if (!isMainProcess()) return")
        val await = first("PrefsWarmup.await()")
        val firstRead = first("PreferenceHelper.getInstance().init(this)")

        assertTrue("attachBaseContext=$attach super=$superAttach start=$start", attach in 0 until superAttach && superAttach < start)
        assertTrue(
            "PrefsWarmup.start phải nằm trong attachBaseContext",
            lines.subList(attach + 1, start).none { it.contains(" fun ") }
        )
        assertTrue(
            "isMainProcess=$mainOnly await=$await PreferenceHelper.init=$firstRead",
            mainOnly in 0 until await && await < firstRead
        )
    }

    private companion object {
        const val AF_RES_ID = 7
        const val WARMUP_THREAD = "appplugin-prefs-warmup"
        const val UMP_FILE = "__GOOGLE_FUNDING_CHOICE_SDK_INTERNAL__"
        /** Tên đo trên máy (`run-as test.sdk.zd ls shared_prefs` sau `af.init`, t161-t1-emu37-prefs.log). */
        const val APPSFLYER_FILE = "appsflyer-data"
        const val APPLOVIN_FILE = "com.applovin.sdk.shared"
        /** Ca (b), (c), (e): hạn rộng để máy CI chậm không biến lần nạp thành timeout. */
        const val LONG_TIMEOUT_MS = 5_000L
        const val SLACK_MS = 2_000L
        const val SETTLE_MS = 150L
        const val HANG_LIMIT_S = 10L
    }
}
