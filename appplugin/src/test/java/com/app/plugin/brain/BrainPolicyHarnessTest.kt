package com.app.plugin.brain

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 1.9.0 Task 5s (D28) — harness brain: 3 khoá như `IapHarness` (`flagTest`, meta-data `BRAIN_HARNESS`, extra
 * `brain_policy`), JSON đi đúng đường RC (`DecisionEngine.applyPolicy` + `BrainPolicyStore`). "Đĩa" = prefs trong bộ nhớ.
 */
class BrainPolicyHarnessTest {
    private val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    private var previousPrefs: SharedPreferences? = null
    private val logs = CopyOnWriteArrayList<String>()

    @Before fun setUp() {
        previousPrefs = PreferenceHelper.getInstance().preferences
        PreferenceHelper.getInstance().preferences = inMemoryPrefs(store)
        BrainPolicy.clear()
        BrainPolicyHarness.resetForTest()
        BrainPolicyHarness.logSink = { logs += it }
    }

    @After fun tearDown() {
        BrainPolicyHarness.resetForTest()
        BrainPolicy.clear()
        AdsBrain.rolloutFraction = 1.0
        PreferenceHelper.getInstance().preferences = previousPrefs
    }

    private fun activity(flag: String? = "1", meta: Boolean = true, json: String? = null): Activity {
        val pkg = "test.sdk.zd"
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier("flagTest", "string", pkg)).thenReturn(if (flag == null) 0 else 42)
        `when`(res.getString(42)).thenReturn(flag)
        val bundle = mock(Bundle::class.java)
        `when`(bundle.getBoolean(BrainPolicyHarness.META_KEY, false)).thenReturn(meta)
        val pm = mock(PackageManager::class.java)
        `when`(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).thenReturn(ApplicationInfo().apply { metaData = bundle })
        val intent = mock(Intent::class.java)
        `when`(intent.getStringExtra(BrainPolicyHarness.EXTRA_POLICY)).thenReturn(json)
        return mock(Activity::class.java).also {
            `when`(it.packageName).thenReturn(pkg)
            `when`(it.resources).thenReturn(res)
            `when`(it.packageManager).thenReturn(pm)
            `when`(it.intent).thenReturn(intent)
        }
    }

    @Test fun `K9 du 3 khoa thi arm, activeVersion la version cua extra va artifact duoc luu nhu RC`() {
        BrainPolicyHarness.onActivityCreated(activity(json = """{"enabled":false}"""))   // thiếu version → bị từ chối
        assertNull("JSON bi tu choi khong arm", BrainPolicyHarness.armedVersion)
        assertEquals("JSON bi tu choi: dung 1 dong log: $logs", 1, logs.size)
        assertTrue(logs.single(), logs.single().startsWith("brain harness: artifact bị từ chối"))
        assertNull("ban bi tu choi khong duoc luu", store[BrainPolicyStore.PREF_KEY])

        val json = """{"version":"harness-k9","enabled":false,"experiment":{"rollout_fraction":0}}"""
        BrainPolicyHarness.onActivityCreated(activity(json = json))
        assertEquals("harness-k9", BrainPolicyHarness.armedVersion)
        assertEquals("harness-k9", BrainPolicy.activeVersion())
        assertEquals("brain harness: armed version=harness-k9", logs.last())
        assertEquals(2, logs.size)
        assertEquals("artifact cua harness duoc luu nhu RC", json, store[BrainPolicyStore.PREF_KEY])

        BrainPolicy.clear()   // cold start không extra: bản đã lưu được khôi phục
        DecisionEngine()
        assertEquals("harness-k9", BrainPolicy.activeVersion())
        assertEquals(false, BrainPolicy.isEnabled())
    }

    @Test fun `K10 thieu meta-data BRAIN_HARNESS thi khong arm va khong doc extra`() {
        val a = activity(meta = false, json = """{"version":"k10"}""")
        BrainPolicyHarness.onActivityCreated(a)
        BrainPolicyHarness.onActivityCreated(a)
        assertNull(BrainPolicyHarness.armedVersion)
        assertEquals("built-in-default", BrainPolicy.activeVersion())
        verify(a, never()).intent
        assertEquals("khong arm thi im lang: $logs", 0, logs.size)
        assertNull(store[BrainPolicyStore.PREF_KEY])
    }
}
