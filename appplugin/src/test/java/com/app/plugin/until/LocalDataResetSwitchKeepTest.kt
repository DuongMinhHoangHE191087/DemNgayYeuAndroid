package com.app.plugin.until

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import com.app.plugin.MonetizationSdk
import com.app.plugin.ads.AdsPlacementConfigManager
import com.app.plugin.ads.PlacementSource
import com.app.plugin.ads.PlacementSwitchState
import com.app.plugin.brain.BrainStorage
import com.app.plugin.iap.PurchaseLedger
import com.app.plugin.logevent.DurableEvents
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.segment.SegmentTestSupport
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.util.concurrent.Executor

/**
 * 2.4.2 SWITCH/CLEAR (ruling D8-SC; tách khỏi `LocalDataResetTest` ở review m13): trạng thái công tắc placement theo
 * nguồn (D1-242) là cache cấu hình vận hành, `clearLocalData` giữ cả ba khoá cùng nhau — bản gộp `mem_cf_ads_placement`
 * và bản ghi RC/Firestore của `PlacementSwitchState`. Prefs trong bộ nhớ + `getAll`; executor đồng bộ.
 */
class LocalDataResetSwitchKeepTest {
    @get:Rule val tmp = TemporaryFolder()

    private val savedExecutor = LocalDataReset.executor
    private val savedIo = DurableEvents.ioExecutor
    private val savedHelperPrefs = PreferenceHelper.getInstance().preferences
    private val appStore = SegmentTestSupport.newStore()      // App_prefer_file (PreUtil)
    private val held = mutableListOf<Runnable>()

    @Before fun setUp() {
        SegmentTestSupport.reset()
        DurableEvents.resetForTest()
        DurableEvents.ioExecutor = Executor { it.run() }
        LocalDataReset.executor = Executor { held += it }
    }

    @After fun tearDown() {
        LocalDataReset.executor = savedExecutor
        DurableEvents.ioExecutor = savedIo
        DurableEvents.resetForTest()
        SegmentTestSupport.reset()
        PreferenceHelper.getInstance().preferences = savedHelperPrefs
        PurchaseLedger.newProcessForTest()
        BrainStorage::class.java.declaredFields
            .filter { it.name == "cachedPlayerId" || it.name == "forgottenIds" }
            .forEach { f -> f.isAccessible = true; (f.get(BrainStorage) as? MutableSet<*>)?.clear() ?: f.set(BrainStorage, null) }
    }

    /** [inMemoryPrefs] cộng `getAll` (bản sao của kho). */
    private fun prefs(store: MutableMap<String, Any?>): SharedPreferences {
        val base = inMemoryPrefs(store)
        return mock(SharedPreferences::class.java) { inv ->
            if (inv.method.name == "getAll") HashMap(store) else inv.method.invoke(base, *inv.arguments)
        }
    }

    private fun context(): Context = mock(Context::class.java).also { c ->
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(c.resources).thenReturn(res)
        `when`(c.applicationContext).thenReturn(c)
        `when`(c.applicationInfo).thenReturn(ApplicationInfo().apply { dataDir = tmp.root.path })
        `when`(c.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs(SegmentTestSupport.newStore()))
        `when`(c.getSharedPreferences(eq(PreUtil.PREFF_FILE), anyInt())).thenReturn(prefs(appStore))
    }

    private fun clearNow(context: Context) {
        MonetizationSdk.clearLocalData(context)
        while (held.isNotEmpty()) held.removeAt(0).run()
    }

    @Test fun `CL8 khoa giu - ba khoa cong tac placement lay tu hang nguon`() {
        // D8-SC: trạng thái công tắc placement theo nguồn (SWITCH, D1-242) là cache cấu hình vận hành, ba khoá đi cùng nhau.
        val sources = listOf(PlacementSwitchState.KEY_MERGED, PlacementSource.REMOTE_CONFIG.prefKey, PlacementSource.FIRESTORE.prefKey)
        sources.forEach { assertTrue("giữ $it", LocalDataReset.keep(it)) }
    }

    @Test fun `CL8 D8-SC - ba khoa cong tac placement con sau clear, cong tac van tat, ban ghi theo nguon con`() {
        val context = context()
        val before = AdsPlacementConfigManager()
        before.switches.apply(context, PlacementSource.REMOTE_CONFIG, """{"full_b":{"flag":-1}}""")
        before.switches.apply(context, PlacementSource.FIRESTORE, """{"full_a":{"flag":-1}}""")
        val keys = listOf(PlacementSwitchState.KEY_MERGED, PlacementSource.REMOTE_CONFIG.prefKey, PlacementSource.FIRESTORE.prefKey)
        val saved = keys.associateWith { appStore[it] }

        clearNow(context)

        keys.forEach { assertEquals("App_prefer_file giữ $it", saved[it], appStore[it]) }
        val cold = AdsPlacementConfigManager().also { it.switches.restore(context) }   // cold start sau clear
        assertEquals("full_a vẫn tắt sau clear", -1, cold.getConfig("full_a", 1)!!.flagShow)
        assertEquals("full_b vẫn tắt sau clear", -1, cold.getConfig("full_b", 1)!!.flagShow)
        cold.switches.apply(context, PlacementSource.REMOTE_CONFIG, "")   // RC gỡ khoá
        assertEquals("bản ghi RC còn: full_b bật lại", 1, cold.getConfig("full_b", 1)!!.flagShow)
        assertEquals("bản ghi Firestore còn: RC rỗng không bật full_a", -1, cold.getConfig("full_a", 1)!!.flagShow)
    }
}
