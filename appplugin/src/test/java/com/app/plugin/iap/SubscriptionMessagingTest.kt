package com.app.plugin.iap

import android.app.Activity
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * 1.8.0 Task 11 — D2 (+ D27): SDK tự gọi hộp thư Play (`showInAppMessages`) ở lần vào foreground khi sub grace/hold,
 * ≤ 1 lần/24 h/sản phẩm, tắt bằng `in_app_messages.enabled`, im dưới harness trừ khi script cho phép, kết quả
 * `SUBSCRIPTION_STATUS_UPDATED` → khôi phục. N1–N5 đặc tả (seam mới); N6 nguồn, đỏ trên mã cũ.
 *
 * Luồng billing = hàng đợi tự chạy ([runBilling]); main = `BillingThread.postMain` (JVM không có main looper: chạy ngay).
 */
class SubscriptionMessagingTest {
    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: android.content.SharedPreferences? = null
    private val billingQueue = ArrayDeque<Runnable>()
    private val base = File("src/main/java/com/app/plugin")
    private val t0 = 1_800_000_000_000L
    private val hour = 3_600_000L
    private var now = t0
    private val activity: Activity = mock(Activity::class.java)
    private lateinit var fake: FakeBillingPort
    private var updated = false

    /** Port giả: `showInAppMessages` ghi lời gọi rồi trả [updated] (Play: `SUBSCRIPTION_STATUS_UPDATED`). */
    private fun port(inner: FakeBillingPort): BillingPort = object : BillingPort by inner {
        override fun showInAppMessages(activity: Activity, onResult: (subscriptionUpdated: Boolean) -> Unit) {
            inner.calls += "showInAppMessages"
            onResult(updated)
        }
    }

    @Before fun setUp() {
        previousPrefs = helper.preferences
        helper.preferences = inMemoryPrefs(store)
        SubscriptionLifecycle.clear()
        SubscriptionMessaging.resetForTest()
        IapHarness.resetForTest()
        useDirectBillingExecutor()
        SubscriptionMessaging.executor = Executor { billingQueue.addLast(it) }
        SubscriptionMessaging.clock = { now }
        SubscriptionServerState.clock = { now }
        fake = FakeBillingPort()
        install(port(fake))
    }

    @After fun tearDown() {
        SubscriptionMessaging.resetForTest()
        IapHarness.resetForTest()
        SubscriptionLifecycle.clear()
        SubscriptionServerState.clock = System::currentTimeMillis
        BillingPorts.active = null
        IapHelper.replacePort(null)
        Entitlements.clearAll()
        PurchaseLedger.resetForTest()
        helper.preferences = previousPrefs
    }

    private fun install(p: BillingPort) {
        IapHelper.replacePort(p)
        BillingPorts.active = p
    }

    private fun runBilling() { while (billingQueue.isNotEmpty()) billingQueue.removeFirst().run() }

    private fun calls(): Int = fake.calls.count { it == "showInAppMessages" }

    /** Một lần vào foreground: `onStart` (main) chỉ xếp việc; quyết định chạy trên luồng billing. Trả số lời gọi mới. */
    private fun foreground(): Int {
        val before = calls()
        SubscriptionMessaging.onForeground(activity)
        assertEquals("main chỉ xếp việc lên luồng billing", before, calls())
        runBilling()
        return calls() - before
    }

    private fun grace(pid: String = "vip") = assertTrue(SubscriptionServerState.applyServer(pid, "GRACE", now - hour))

    private fun sub(pid: String, suspended: Boolean) =
        PortPurchase(listOf(pid), "GPA.$pid", "tok-$pid", t0, IapPurchaseState.PURCHASED, true, true, 1, suspended)

    @Test fun `N1 grace hoac hold hoac suspended va foreground thi goi Play dung 1 lan`() {
        assertEquals("không grace/hold/suspended → 0", 0, foreground())

        grace("vip")
        assertEquals(SubState.IN_GRACE_PERIOD, SubscriptionLifecycle.stateFor("vip"))
        assertEquals("grace → 1", 1, foreground())

        // ON_HOLD: Play không trả sub về (nhãn LAPSED), trạng thái server gốc còn tươi → vẫn nhắc (D27).
        assertTrue(SubscriptionServerState.applyServer("hold", "ON_HOLD", now - hour))
        assertEquals(SubState.LAPSED, SubscriptionLifecycle.stateFor("hold"))
        assertEquals("ON_HOLD tươi → 1", 1, foreground())

        // ON_HOLD quá cửa sổ 7 ngày → không còn tươi.
        assertTrue(SubscriptionServerState.applyServer("old", "ON_HOLD", now - 8 * 24 * hour))
        now += 25 * hour
        assertTrue(SubscriptionServerState.applyServer("vip", "RECOVERED", now - 1))
        assertTrue(SubscriptionServerState.applyServer("hold", "RECOVERED", now - 1))
        assertEquals("hồi phục + hold cũ → 0", 0, foreground())

        // `suspended` thấy ở lượt khôi phục gần nhất, qua port Play đã bọc của BillingPorts.
        val play = FakeBillingPort().apply { owned[IapProductType.SUBS] = listOf(sub("paused", suspended = true)) }
        fake = play
        install(BillingPorts.observed(port(play)))
        IapHelper.restorePurchases()
        assertEquals("suspended → 1", 1, foreground())
    }

    @Test fun `N2 lan foreground thu hai trong 24 h khong goi, du 24 h thi goi lai`() {
        grace()
        assertEquals(1, foreground())
        now += 23 * hour
        assertEquals("lần 2 trong 24 h → 0", 0, foreground())
        assertEquals("mốc lưu prefs: process mới vẫn biết", true, store.keys.any { it == "sub_inapp_msg_last" })
        now += hour
        assertEquals("đủ 24 h → 1", 1, foreground())
    }

    @Test fun `N3 in_app_messages enabled false thi 0 loi goi, mac dinh bat`() {
        grace()
        assertTrue(SubscriptionLifecycle.apply("""{"version":"v_off","in_app_messages":{"enabled":false}}""").applied)
        assertEquals("enabled=false → 0", 0, foreground())

        assertTrue(SubscriptionLifecycle.apply("""{"version":"v_default"}""").applied)
        assertEquals("không có khối → mặc định bật → 1", 1, foreground())

        now += 24 * hour
        assertTrue(SubscriptionLifecycle.apply("""{"version":"v_all_off","enabled":false}""").applied)
        assertEquals("cả tính năng tắt → 0", 0, foreground())
    }

    @Test fun `N4 harness da arm ma script khong cho phep thi 0 loi goi`() {
        grace()
        val armed = IapHarness::class.java.getDeclaredField("armed").apply { isAccessible = true }
        armed.set(null, IapScripts.find(IapScripts.BUY_SUB))
        assertEquals("buy_sub không cho phép → 0", 0, foreground())

        armed.set(null, IapScripts.find("grace"))
        assertTrue(IapHarness.inAppMessagesAllowed())
        assertEquals("grace cho phép → 1", 1, foreground())
    }

    @Test fun `N5 SUBSCRIPTION_STATUS_UPDATED thi khoi phuc, NO_ACTION_NEEDED thi khong`() {
        grace()
        val order = CopyOnWriteArrayList<String>()
        updated = false
        assertEquals(1, foreground())
        assertEquals("NO_ACTION_NEEDED → không restore", 0, fake.calls.count { it.startsWith("queryOwned") })

        now += 24 * hour
        updated = true
        assertEquals(1, foreground())
        fake.calls.filter { it == "showInAppMessages" || it.startsWith("queryOwned") }.forEach { order += it }
        assertEquals(listOf("showInAppMessages", "showInAppMessages", "queryOwned:INAPP", "queryOwned:SUBS"), order)
    }

    /** R-11 m1: app chưa dựng port billing → `onStart` không xếp việc nào (không khởi động luồng billing). */
    @Test fun `N7 port null thi onForeground khong xep viec nao va 0 loi goi`() {
        grace()
        BillingPorts.active = null
        SubscriptionMessaging.onForeground(activity)
        assertEquals("0 việc trên luồng billing", 0, billingQueue.size)
        runBilling()
        assertEquals(0, calls())
    }

    /** R-11 m2: port chưa gọi store được (`usable() = false`) → 0 lời gọi, mốc 24 h không bị tiêu. */
    @Test fun `N8 port khong usable thi 0 loi goi, lan sau usable thi goi`() {
        grace()
        fake.isUsable = false
        assertEquals(0, foreground())
        fake.isUsable = true
        assertEquals("mốc 24 h chưa bị tiêu", 1, foreground())
    }

    /** R-11 m3: khối `in_app_messages` sai dạng → tắt (fail-closed); vắng `enabled` trong khối → mặc định bật. */
    @Test fun `N9 in_app_messages sai dang thi tat, 0 loi goi`() {
        grace()
        for (bad in listOf("{\"enabled\":\"false\"}", "{\"enabled\":0}", "true")) {
            assertTrue(SubscriptionLifecycle.apply("{\"version\":\"v_bad\",\"in_app_messages\":$bad}").applied)
            assertEquals("$bad → 0", 0, foreground())
        }
        assertTrue(SubscriptionLifecycle.apply("{\"version\":\"v_empty\",\"in_app_messages\":{}}").applied)
        assertEquals("khối rỗng → mặc định bật → 1", 1, foreground())
    }

    private fun count(rel: String, needle: String): Int {
        val f = File(base, rel)
        assertTrue("không thấy ${f.path}: sai thư mục làm việc", f.isFile) // plan-signoff: file vắng là lỗi, không phải rỗng
        return Regex(Regex.escape(needle)).findAll(f.readText()).count()
    }

    /** N6 (nguồn): `onStart` gọi `SubscriptionMessaging.onForeground` đúng 1 lần; `BillingPorts` cài guard. */
    @Test fun `N6 AppPluginBase onStart goi SubscriptionMessaging onForeground va BillingPorts cai BillingProxyCrashGuard`() {
        val t = File(base, "app/AppPluginBase.kt").readText()
        val onStart = t.substringAfter("override fun onStart", "").substringBefore("override fun ", "")
        assertEquals(1, count("app/AppPluginBase.kt", "SubscriptionMessaging.onForeground"))
        assertEquals(1, Regex(Regex.escape("SubscriptionMessaging.onForeground")).findAll(onStart).count())
        assertEquals(1, count("iap/BillingPorts.kt", "BillingProxyCrashGuard"))
    }

    /** N10 (nguồn, R-11 I1 / D29): guard cài trên main ở `AppPluginBase.onCreate` đúng 1 lần (trước crash F9). */
    @Test fun `N10 AppPluginBase onCreate cai BillingProxyCrashGuard dung 1 lan`() {
        val t = File(base, "app/AppPluginBase.kt").also { assertTrue(it.isFile) }.readText()
        val onCreate = t.substringAfter("override fun onCreate()", "").substringBefore("override fun ", "")
        assertEquals(1, Regex(Regex.escape("BillingProxyCrashGuard.install()")).findAll(onCreate).count())
        val gate = onCreate.indexOf("isMainProcess()")
        assertTrue("sau isMainProcess", gate in 0 until onCreate.indexOf("BillingProxyCrashGuard.install()"))
    }
}
