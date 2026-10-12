package com.app.plugin.until

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import com.app.plugin.MonetizationSdk
import com.app.plugin.ads.AdsHelper
import com.app.plugin.app.AppPluginBase
import com.app.plugin.brain.BrainStorage
import com.app.plugin.brain.DecisionLogQueue
import com.app.plugin.iap.Entitlements
import com.app.plugin.iap.PurchaseLedger
import com.app.plugin.license.LicenseCache
import com.app.plugin.logevent.DurableEvents
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.segment.SegmentEvent
import com.app.plugin.segment.SegmentTestSupport
import com.app.plugin.segment.UserSegmentEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.util.concurrent.Executor

/**
 * 2.4.2 CLEAR (Hợp đồng D, quyết định (v); gap hàng 38): `MonetizationSdk.clearLocalData` trả về ngay, xoá trên luồng nền
 * mọi khoá của `App_prefer_file` và `XgameHCM-BaseAndroid` trừ danh sách giữ, làm rỗng `files/appplugin_events`, rồi nạp
 * lại `UserSegmentEngine`. Prefs trong bộ nhớ (`logevent/InMemoryPrefs.kt` + `getAll`), mỗi file một kho; executor đồng bộ.
 */
class LocalDataResetTest {
    @get:Rule val tmp = TemporaryFolder()

    private val savedExecutor = LocalDataReset.executor
    private val savedIo = DurableEvents.ioExecutor
    private val appStore = SegmentTestSupport.newStore()      // App_prefer_file (PreUtil)
    private val helperStore = SegmentTestSupport.newStore()   // XgameHCM-BaseAndroid (PreferenceHelper)
    private val licenseStore = SegmentTestSupport.newStore()  // appplugin_license (LicenseCache, 2.4.3)
    private val held = mutableListOf<Runnable>()
    private val savedHelperPrefs = PreferenceHelper.getInstance().preferences
    /** Chạy một lần ngay sau `commit` đầu tiên trên `App_prefer_file` (bước xoá prefs): event segment chen vào giữa. */
    private var afterFirstAppCommit: (() -> Unit)? = null

    @Before fun setUp() {
        SegmentTestSupport.reset()
        DurableEvents.resetForTest()
        DurableEvents.ioExecutor = Executor { it.run() }
        LocalDataReset.executor = Executor { held += it }
        appStore.putAll(
            mapOf(
                "seg_first_seen_millis" to 1000L, "seg_session_timestamps" to "1,2", "mem_app_session_count" to 5,
                "install_referrer_url" to "utm_source=x", "install_referrer_captured" to 1,
                "mem_cf_ads_placement" to "{\"full_home\":{\"flag\":-1}}", "ads_hp_remove_ad_mask" to 7, "ads_hp_remove_ad" to 1,
                "cfcurr_ads_fullstep" to "1,2", "plmem_admob0_full" to "full_default,id", "mem_sdk_kill_android" to "{}",
            )
        )
        helperStore.putAll(
            mapOf(
                "brain_belief_p1" to "b", "brain_bandit_stats" to "s", "brain_player_id" to "p1", "SEQUENCE_ID" to 9,
                "iap_active_products" to "a", "iap_remove_ads_products" to "r", "entitlements_remove_ads_masks" to "m",
                "iap_entitlements_v1" to "v", "analytics_collection_disabled_by_sdk" to true, "consent_tier" to "GRANTED",
                "mem_brain_policy_android" to "pol", "mem_iap_ledger" to "[]",
            )
        )
    }

    @After fun tearDown() {
        LocalDataReset.executor = savedExecutor
        DurableEvents.ioExecutor = savedIo
        DurableEvents.resetForTest()
        SegmentTestSupport.reset()
        PreferenceHelper.getInstance().preferences = savedHelperPrefs
        PurchaseLedger.newProcessForTest()
        forgetBrainCache()
    }

    /** `BrainStorage` là object của process: bỏ id đệm (và tập id đã quên, nếu có) để không rò sang test khác. */
    private fun forgetBrainCache() = BrainStorage::class.java.declaredFields
        .filter { it.name == "cachedPlayerId" || it.name == "forgottenIds" }
        .forEach { f -> f.isAccessible = true; (f.get(BrainStorage) as? MutableSet<*>)?.clear() ?: f.set(BrainStorage, null) }

    /** [inMemoryPrefs] cộng `getAll` (bản sao của kho). */
    private fun prefs(store: MutableMap<String, Any?>, onCommit: () -> Unit = {}): SharedPreferences {
        val base = inMemoryPrefs(store)
        val editor = mock(SharedPreferences.Editor::class.java) { inv ->
            val r = inv.method.invoke(base.edit(), *inv.arguments)
            if (inv.method.name == "commit") onCommit()
            if (r is SharedPreferences.Editor) inv.mock else r
        }
        return mock(SharedPreferences::class.java) { inv ->
            when (inv.method.name) {
                "getAll" -> HashMap(store)
                "edit" -> editor
                else -> inv.method.invoke(base, *inv.arguments)
            }
        }
    }

    private fun context(): Context = mock(Context::class.java).also { c ->
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(c.resources).thenReturn(res)
        `when`(c.applicationContext).thenReturn(c)
        `when`(c.applicationInfo).thenReturn(ApplicationInfo().apply { dataDir = tmp.root.path })
        `when`(c.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs(SegmentTestSupport.newStore()))
        val hook = { afterFirstAppCommit?.also { afterFirstAppCommit = null }?.invoke(); Unit }
        `when`(c.getSharedPreferences(eq(PreUtil.PREFF_FILE), anyInt())).thenReturn(prefs(appStore, hook))
        `when`(c.getSharedPreferences(eq("XgameHCM-BaseAndroid"), anyInt())).thenReturn(prefs(helperStore))
        `when`(c.getSharedPreferences(eq(LicenseCache.FILE), anyInt())).thenReturn(prefs(licenseStore))
    }

    private fun clearNow(context: Context) {
        MonetizationSdk.clearLocalData(context)
        while (held.isNotEmpty()) held.removeAt(0).run()
    }

    private fun eventsDir() = File(tmp.root, "files/appplugin_events")

    @Test fun `CL1 tra ve ngay, roi xoa du lieu nguoi dung cua ca hai file`() {
        val context = context()
        MonetizationSdk.clearLocalData(context)
        assertEquals("trả về ngay: chưa xoá gì", "1,2", appStore["seg_session_timestamps"])
        assertEquals("đúng một việc trên luồng nền", 1, held.size)

        clearNow(context)

        for (k in listOf("seg_session_timestamps", "mem_app_session_count", "install_referrer_url")) {
            assertFalse("App_prefer_file: $k vắng", appStore.containsKey(k))
        }
        for (k in listOf("brain_belief_p1", "brain_bandit_stats", "brain_player_id", "SEQUENCE_ID", "mem_iap_ledger")) {
            assertFalse("XgameHCM-BaseAndroid: $k vắng", helperStore.containsKey(k))
        }
        assertNotEquals("seg_first_seen_millis cũ không còn", 1000L, appStore["seg_first_seen_millis"])
    }

    @Test fun `CL2 khoa giu con nguyen - quyen da mua, consent, cau hinh tu xa, co mot-lan`() {
        val before = HashMap(appStore) to HashMap(helperStore)
        clearNow(context())

        val keptApp = listOf(
            "ads_hp_remove_ad_mask", "ads_hp_remove_ad", "install_referrer_captured", "mem_cf_ads_placement",
            "cfcurr_ads_fullstep", "plmem_admob0_full", "mem_sdk_kill_android",
        )
        keptApp.forEach { assertEquals("App_prefer_file giữ $it", before.first[it], appStore[it]) }
        val keptHelper = listOf(
            "iap_active_products", "iap_remove_ads_products", "entitlements_remove_ads_masks", "iap_entitlements_v1",
            "analytics_collection_disabled_by_sdk", "consent_tier", "mem_brain_policy_android",
        )
        keptHelper.forEach { assertEquals("XgameHCM-BaseAndroid giữ $it", before.second[it], helperStore[it]) }
    }

    @Test fun `CL2 khoa chep chu khop hang private o nguon`() {
        fun const(c: Class<*>, name: String) = c.getDeclaredField(name).apply { isAccessible = true }.get(null) as String
        val sources = listOf(
            const(Entitlements::class.java, "KEY_ACTIVE"), const(Entitlements::class.java, "KEY_REMOVE_ADS_IDS"),
            const(Entitlements::class.java, "KEY_REMOVE_ADS_MASKS"), const(Entitlements::class.java, "KEY_SIGNED"),
            const(AdsHelper::class.java, "KEY_REMOVE_ADS_MASK"), const(AdsHelper::class.java, "KEY_REMOVE_ADS_LEGACY"),
            const(AppPluginBase::class.java, "KEY_CONSENT_TIER"),
        )
        sources.forEach { assertTrue("giữ $it", LocalDataReset.keep(it)) }
    }

    @Test fun `CL3 hang doi appplugin_events rong - da gan DurableEvents`() {
        DurableEvents.attachDir(File(tmp.root, "files"), null)
        DecisionLogQueue(eventsDir(), DurableEvents.MAX_RECORDS).enqueue("ad_revenue\u001F{}")

        clearNow(context())

        assertEquals("hàng đợi rỗng", emptyList<String>(), DecisionLogQueue(eventsDir(), DurableEvents.MAX_RECORDS).peekAll())
    }

    @Test fun `CL3 hang doi appplugin_events rong - chua gan DurableEvents`() {
        DecisionLogQueue(eventsDir(), DurableEvents.MAX_RECORDS).enqueue("ad_revenue\u001F{}")

        clearNow(context())

        assertEquals("hàng đợi rỗng", emptyList<String>(), DecisionLogQueue(eventsDir(), DurableEvents.MAX_RECORDS).peekAll())
    }

    @Test fun `CL4 UserSegmentEngine nap lai tu prefs da xoa - firstSeenMillis moi`() {
        val context = context()
        UserSegmentEngine.instance.init(context)
        val field = UserSegmentEngine::class.java.getDeclaredField("firstSeenMillis").apply { isAccessible = true }
        assertEquals(1000L, field.get(UserSegmentEngine.instance))

        clearNow(context)

        val fresh = field.get(UserSegmentEngine.instance) as Long
        assertTrue("firstSeenMillis mới ($fresh)", fresh > 1000L)
        assertEquals("engine ghi mốc mới vào prefs đã xoá", fresh, appStore["seg_first_seen_millis"])
    }

    @Test fun `CL5 I2 so mua trong bo nho khong ghi lai token cu sau khi xoa`() {
        PreferenceHelper.getInstance().preferences = prefs(helperStore)
        helperStore["mem_iap_ledger"] = """[{"t":"old-token","p":"pro","o":"GPA.old","r":false}]"""
        PurchaseLedger.newProcessForTest()
        assertTrue("sổ cũ đã nạp vào bộ nhớ", PurchaseLedger.isGranted("old-token"))

        clearNow(context())
        PurchaseLedger.markReported("old-token")   // server trả 2xx muộn cho iap_purchase cũ

        assertFalse("token cũ không sống lại", PurchaseLedger.isGranted("old-token"))
        assertNull("sổ trên đĩa không có order id cũ", helperStore["mem_iap_ledger"])
    }

    @Test fun `CL6 I2 player id moi sau khi xoa, belief cua id cu khong mo coi`() {
        PreferenceHelper.getInstance().preferences = prefs(helperStore)
        forgetBrainCache()
        assertEquals("p1", BrainStorage.playerId())

        clearNow(context())
        BrainStorage.saveBelief("p1", "late")   // brain còn giữ state của id cũ trong phiên

        assertNotEquals("id mới sau khi xoá", "p1", BrainStorage.playerId())
        assertFalse("brain_belief_<id cũ> không được tạo lại", helperStore.containsKey("brain_belief_p1"))
    }

    @Test fun `CL7 I3 recordEventNow chen giua buoc xoa prefs va nap lai segment khong lam song lai lich su`() {
        val now = System.currentTimeMillis()
        appStore["seg_session_timestamps"] = "${now - 2000},${now - 1000}"
        val context = context()
        UserSegmentEngine.instance.init(context)
        afterFirstAppCommit = { UserSegmentEngine.instance.recordEventNow(SegmentEvent.SESSION_START, null, now) }

        clearNow(context)

        val stamps = UserSegmentEngine.instance.scorerSnapshot().sessionTimestamps
        assertFalse("RAM: lịch sử cũ không sống lại — $stamps", stamps.contains(now - 2000) || stamps.contains(now - 1000))
        val disk = appStore["seg_session_timestamps"]?.toString().orEmpty()
        assertFalse("đĩa: lịch sử cũ không sống lại — $disk", disk.contains("${now - 2000}") || disk.contains("${now - 1000}"))
    }

    @Test fun `CL9 xoa file appplugin_license - lan mo sau xac minh day du`() {
        licenseStore["v1"] = "${"a".repeat(64)}|t1|2000000000"

        clearNow(context())

        assertEquals("appplugin_license rỗng sau clear", emptyMap<String, Any?>(), HashMap(licenseStore))
        assertEquals("App_prefer_file vẫn giữ khoá giữ", 7, appStore["ads_hp_remove_ad_mask"])
    }
}
