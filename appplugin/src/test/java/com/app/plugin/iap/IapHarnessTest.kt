package com.app.plugin.iap

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.RemoveAdsMask
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.logevent.TelemetryConsentGate
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.paywall.PaywallManager
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 1.8.0 Task 8 — harness G12 (§Hợp đồng B): 3 khoá (A1–A4, A6), 12 script chạy qua state machine thật của `IapHelper`
 * (A5, một hàng mỗi script), nối dây nguồn (A7, đỏ trên mã cũ). Luồng billing = executor trực tiếp, trễ của port kịch
 * bản = đồng hồ giả; "đĩa" = prefs trong bộ nhớ (sống qua "process mới" của script 5).
 */
class IapHarnessTest {
    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: android.content.SharedPreferences? = null
    private val logs = CopyOnWriteArrayList<String>()
    private val results = CopyOnWriteArrayList<String>()
    private val timer = FakeTimer()
    private fun field(name: String) = IapHelper::class.java.declaredFields.firstOrNull { it.name == name }?.apply { isAccessible = true }
    private fun helperPort(): BillingPort? = field("port")?.get(null) as BillingPort?

    private class FakeTimer : BillingTimer {
        private var now = 0L
        private val tasks = ArrayDeque<Pair<Long, Runnable>>()
        override fun nowMs(): Long = now
        override fun schedule(delayMs: Long, r: Runnable) { tasks.addLast(now + delayMs to r) }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.sortedBy { it.first }.first().let { t -> tasks.remove(t); now = t.first; t.second.run() }
        }
    }

    @Before fun setUp() {
        previousPrefs = helper.preferences
        helper.preferences = inMemoryPrefs(store)
        freshInstall()
    }

    @After fun tearDown() {
        newProcess()
        IapHarness.resetForTest()
        BillingPorts.active = null
        field("billingExecutor")?.set(null, Executors.newSingleThreadExecutor { r -> Thread(r, "appplugin-billing").apply { isDaemon = true } })
        BillingThread.timer = ScheduledBillingTimer(BillingThread.executor)
        PurchaseLedger.resetForTest()
        TelemetryConsentGate.resetForTest()
        ConsentState.resetForTest()
        PaywallManager.clear()
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        helper.preferences = previousPrefs
    }

    /** Cài mới: prefs rỗng, sổ token rỗng, không quyền lợi, không cấu hình. */
    private fun freshInstall() {
        store.clear()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        ConsentState.resetForTest()
        TelemetryConsentGate.resetForTest()
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        PaywallManager.clear()
        PurchaseLedger.resetForTest()
        newProcess()
    }

    /** Process mới (prefs giữ): IapHelper chưa có port/giá/luồng mua, harness chưa arm, "Play" là port giả [BillingPorts.active]. */
    private fun newProcess(): FakeBillingPort {
        IapHelper.replacePort(null)
        IapHelper.setProducts()
        @Suppress("UNCHECKED_CAST")
        (field("productCache")?.get(null) as MutableMap<String, IapProduct>?)?.clear()
        PurchaseFlow.resetForTest()
        (field("connectScheduled")?.get(null) as AtomicBoolean?)?.set(false)
        useDirectBillingExecutor()
        PurchaseLedger.newProcessForTest()
        IapHarness.resetForTest()
        IapHarness.executor = Executor { it.run() }
        IapHarness.logSink = { logs += it }
        BillingThread.timer = timer
        IapHelper.purchaseListener = { results += label(it) }
        return FakeBillingPort().also { IapHelper.replacePort(it); BillingPorts.active = it }
    }

    private fun label(r: IapResult): String = when (r) {
        is IapResult.Success -> "success:${r.purchase.productId}"
        is IapResult.Pending -> "pending:active=${Entitlements.activeProductIds()}:grace=" +
            (SubscriptionLifecycle.stateFor(IapScripts.SUB_ID) == SubState.IN_GRACE_PERIOD)
        IapResult.UserCanceled -> "cancel"
        else -> r.toString()
    }

    private fun activity(flag: String? = "1", meta: Boolean = true, script: String? = IapScripts.BUY_SUB): Activity {
        val pkg = "test.sdk.zd"
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier("flagTest", "string", pkg)).thenReturn(if (flag == null) 0 else 42)
        `when`(res.getString(42)).thenReturn(flag)
        val bundle = mock(Bundle::class.java)
        `when`(bundle.getBoolean(IapHarness.META_KEY, false)).thenReturn(meta)
        val pm = mock(PackageManager::class.java)
        `when`(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).thenReturn(ApplicationInfo().apply { metaData = bundle })
        val intent = mock(Intent::class.java)
        `when`(intent.getStringExtra(IapHarness.EXTRA_SCRIPT)).thenReturn(script)
        return mock(Activity::class.java).also {
            `when`(it.packageName).thenReturn(pkg)
            `when`(it.resources).thenReturn(res)
            `when`(it.packageManager).thenReturn(pm)
            `when`(it.intent).thenReturn(intent)
        }
    }

    /** Không arm: port của IapHelper vẫn là "Play", "Play" không bị `end()`, harness im lặng. */
    private fun assertNotArmed(play: FakeBillingPort, logLines: Int = 0) {
        assertNull("armed", IapHarness.armed)
        assertSame("port của IapHelper giữ nguyên", play, helperPort())
        assertFalse("port Play không bị end()", "end" in play.calls)
        assertEquals("dòng log harness: $logs", logLines, logs.size)
    }

    @Test fun `A1 thieu flagTest khong arm, khong doc tiep`() {
        val play = newProcess()
        val a = activity(flag = null)
        IapHarness.onActivityCreated(a)
        IapHarness.onActivityCreated(a)
        assertNotArmed(play)
        verify(a, never()).packageManager
        verify(a, never()).intent
        IapHarness.onActivityCreated(activity(flag = "0"))
        assertNotArmed(play)
    }

    @Test fun `A2 thieu meta-data IAP_HARNESS khong arm, khong doc intent`() {
        val play = newProcess()
        val a = activity(meta = false)
        IapHarness.onActivityCreated(a)
        assertNotArmed(play)
        verify(a, never()).intent
    }

    @Test fun `A3 ten script la khong arm`() {
        val play = newProcess()
        IapHarness.onActivityCreated(activity(script = "buy_everything"))
        assertNotArmed(play, logLines = 1)
        assertTrue(logs.single(), logs.single().contains("script lạ 'buy_everything'"))
        IapHarness.onActivityCreated(activity(script = null))
        assertNotArmed(play, logLines = 1)
    }

    @Test fun `A4 du 3 khoa thay port bang ScriptedBillingPort tren luong billing roi end port cu`() {
        val play = newProcess()
        val threads = CopyOnWriteArrayList<String>()
        IapHarness.executor = Executor { r -> threads += "billing-sim"; r.run() }
        IapHarness.onActivityCreated(activity(script = IapScripts.RESTORE_OWNED))
        assertTrue("port của IapHelper: ${helperPort()}", helperPort() is ScriptedBillingPort)
        assertSame(helperPort(), BillingPorts.active)
        assertSame(helperPort(), IapHarness.port)
        assertEquals("port Play: end() đúng 1 lần", listOf("end"), play.calls)
        assertEquals("swap chạy qua executor billing", listOf("billing-sim"), threads)
        assertEquals("iap harness: armed script=restore_owned", logs.first { "armed" in it })
        assertTrue(IapHelper.isReady())
    }

    @Test fun `A6 kill switch iap khong arm`() {
        val play = newProcess()
        SdkKillSwitch.current = SdkKillSwitch(iap = true)
        IapHarness.onActivityCreated(activity())
        assertNotArmed(play, logLines = 1)
        assertTrue(logs.single(), logs.single().contains("kill switch iap"))
    }

    /** Một hàng của §B: kết quả app nhận (theo thứ tự), quyền lợi + mask cuối, số ack thật, kiểm thêm. */
    private data class Row(
        val name: String,
        val results: List<String>,
        val active: Set<String>,
        val mask: Int,
        val acks: Int,
        val swapped: Boolean = true,
        val messages: Boolean = false,
        val pre: () -> Unit = {},
        val check: () -> Unit = {},
    )

    private val sub = setOf(IapScripts.SUB_ID)
    private val owned = { Entitlements.setRemoveAdsMasks(IapScripts.MASKS); Entitlements.grant(IapScripts.SUB_ID) }

    private fun rows(): List<Row> = listOf(
        Row(IapScripts.BUY_SUB, listOf("success:g12_sub"), sub, RemoveAdsMask.ALL, 1) {
            assertEquals(IapScripts.PAYWALL_PLACEMENT, PaywallSession.current()?.placement)
            assertTrue(PurchaseLedger.isGranted("g12-buy_sub"))
        },
        Row(IapScripts.BUY_CHEAP, listOf("success:g12_lite"), setOf(IapScripts.INAPP_ID), RemoveAdsMask.INTERRUPTIVE, 1),
        Row(IapScripts.CANCEL, listOf("cancel"), emptySet(), RemoveAdsMask.NONE, 0) { assertFalse(PurchaseLedger.isGranted("g12-cancel")) },
        Row(IapScripts.PENDING_THEN_PAID, listOf("pending:active=[]:grace=false", "success:g12_sub"), sub, RemoveAdsMask.ALL, 1),
        Row(IapScripts.KILL_MID_PURCHASE, emptyList(), sub, RemoveAdsMask.ALL, 1) {
            assertTrue(logs.any { it.endsWith("launch g12_sub no-callback") })
            assertTrue(logs.contains("iap harness: restore_unacked done"))
            assertTrue(PurchaseLedger.isGranted("g12-kill_mid_purchase"))
        },
        Row(IapScripts.RESTORE_OWNED, emptyList(), sub, RemoveAdsMask.ALL, 0) { assertFalse(logs.any { "launch" in it }) },
        Row(IapScripts.REOPEN, emptyList(), sub, RemoveAdsMask.ALL, 0, swapped = false, pre = owned) {
            assertTrue(logs.any { it.contains("mask=${RemoveAdsMask.ALL}") })
        },
        Row(IapScripts.RESTORE_STORE_ERROR, emptyList(), sub, RemoveAdsMask.ALL, 0, pre = owned),
        Row(IapScripts.REVOKE, emptyList(), emptySet(), RemoveAdsMask.NONE, 0, pre = {
            owned()
            SubscriptionLifecycle.observe(listOf(SubscriptionLifecycle.SubSnapshot(IapScripts.SUB_ID, IapPurchaseState.PURCHASED, true)))
        }) { assertEquals(SubState.LAPSED, SubscriptionLifecycle.stateFor(IapScripts.SUB_ID)) },
        Row(IapScripts.GRACE, listOf("success:g12_sub"), sub, RemoveAdsMask.ALL, 1, messages = true) {
            assertTrue(PurchaseLedger.isGranted(IapScripts.RTDN_FIXTURE_TOKEN))
        },
        Row(IapScripts.DENIED, listOf("success:g12_sub"), sub, RemoveAdsMask.ALL, 1, pre = { ConsentState.setTier(ConsentTier.DENIED) }) {
            assertTrue(logs.any { it.endsWith("consent tier=DENIED") })
        },
        Row(IapScripts.MESSAGES_OFF, listOf("success:g12_sub"), sub, RemoveAdsMask.ALL, 1, messages = true) {
            assertEquals("g12_messages_off", SubscriptionLifecycle.activeVersion())
            assertTrue(PurchaseLedger.isGranted(IapScripts.RTDN_FIXTURE_TOKEN))
        },
    )

    private fun runRow(row: Row) {
        freshInstall()
        logs.clear()
        results.clear()
        row.pre()
        val play = newProcess()
        IapHarness.onActivityCreated(activity(script = row.name))
        timer.runAll()
        IapScripts.find(row.name)?.resume?.let { resume ->
            newProcess() // am force-stop rồi mở lại: prefs + sổ giữ, bộ nhớ mất
            IapHarness.onActivityCreated(activity(script = resume.name))
            timer.runAll()
        }
        val at = "[${row.name}] logs=$logs"
        assertTrue(at, logs.contains("iap harness: armed script=${row.name}"))
        assertTrue(at, logs.contains("iap harness: ${row.name} done"))
        assertEquals(at, row.swapped, helperPort() is ScriptedBillingPort)
        assertEquals(at, row.swapped, "end" in play.calls)
        assertEquals(at, row.results, results.toList())
        assertEquals(at, row.active, Entitlements.activeProductIds())
        assertEquals(at, row.mask, Entitlements.removeAdsMask())
        assertEquals(at, row.acks, logs.count { it.contains("acknowledge") && it.endsWith("first=true") })
        assertEquals(at, row.messages, IapHarness.inAppMessagesAllowed())
        row.check()
    }

    @Test fun `A5 12 script chay het, chuoi callback dung bang B`() {
        val table = rows()
        assertEquals("một hàng mỗi script của §B", IapScripts.ALL.map { it.name }, table.map { it.name })
        assertEquals(12, table.size)
        for (row in table) runRow(row)
    }

    private val base = "src/main/java/com/app/plugin/"
    private fun text(rel: String): String = File(base + rel).let { if (it.exists()) it.readText() else "" }
    private fun count(s: String, needle: String) = Regex(Regex.escape(needle)).findAll(s).count()

    @Test fun `A7 onActivityCreated goi IapHarness 1 lan, BillingPorts la cho duy nhat tao ScriptedBillingPort`() {
        val t = text("app/AppPluginBase.kt")
        val body = t.substringAfter("override fun onActivityCreated", "").substringBefore("override fun ", "")
        assertEquals("AppPluginBase.onActivityCreated goi IapHarness", 1, count(body, "IapHarness."))
        val n = File(base).walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "ScriptedBillingPort.kt" }
            .sumOf { count(it.readText(), "ScriptedBillingPort(") }
        assertEquals("so cho tao ScriptedBillingPort trong src/main", 1, n)
        assertEquals("cho tao do la BillingPorts", 1, count(text("iap/BillingPorts.kt"), "ScriptedBillingPort("))
    }
}
