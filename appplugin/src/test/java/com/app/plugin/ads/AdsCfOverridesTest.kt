package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import com.app.plugin.ads.core.UnitGate
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import java.lang.reflect.Modifier

/**
 * 2.5.0 RCSW (Hợp đồng B, quyết định (c), Fable C3): khoá `ads_cf_overrides` (object) trong `ads_policy_android` đè
 * `@integer/ads_cf_*` của app cho đúng 4 tên allowlist, qua `AppUtil.getResourceInt`; áp ở cùng hai chỗ với
 * [GmaLoadContext] (`applyRemotePolicy`, `restorePolicy`). Tên lạ / kiểu sai / ngoài khoảng ⇒ bỏ + logE; RC vắng = resource.
 */
class AdsCfOverridesTest {
    private val savedCountry = AdsPolicyTiers.countryProvider

    @Before fun setUp() = reset()

    @After fun tearDown() {
        reset()
        AdsPolicyTiers.countryProvider = savedCountry
    }

    private fun reset() {
        AdsCfOverrides.resetForTest()
        UnitGate.resetForTest()
        GmaLoadContext.apply(null)
        AdsPolicy.current = AdsPolicy.DEFAULT
        AdsPolicyTiers.remember("")
        LogUtil.setFlagLog(1)
    }

    private fun policy(overrides: String) = """{"schema_version":1,"ads_cf_overrides":$overrides}"""

    /** Context có resource `@integer/<tên>` = giá trị; tên vắng = resource vắng. */
    private fun ctx(resources: Map<String, Int> = emptyMap()): Context {
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        resources.entries.forEachIndexed { i, (name, value) ->
            `when`(res.getIdentifier(eq(name), eq("integer"), eq(PKG))).thenReturn(100 + i)
            `when`(res.getInteger(100 + i)).thenReturn(value)
        }
        return mock(Context::class.java).also {
            `when`(it.resources).thenReturn(res)
            `when`(it.packageName).thenReturn(PKG)
        }
    }

    /** Context có prefs chứa bản thô đã lưu của `ads_policy_android` (đường `restorePolicy`). */
    private fun prefsContext(saved: String): Context {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.getString(AdsPolicy.PREF_KEY, "")).thenReturn(saved)
        return mock(Context::class.java).also { `when`(it.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs) }
    }

    private fun read(c: Context, vararg names: String) = names.map { AppUtil.getResourceInt(c, it) }

    @Test fun `RC1 applyRemotePolicy ads_cf_overrides de len resource vang`() {
        val h = AdsKillSwitchTest.newAdsHelper()
        h.applyRemotePolicy(policy("""{"ads_cf_unit_gate_off":1,"ads_cf_native_media_ratio":1}"""))
        assertEquals("RC thắng resource vắng", listOf(1, 1), read(ctx(), GATE_OFF, RATIO))
    }

    @Test fun `RC1b khoa RC co mat thang resource co mat, RC khong co khoa thi ve resource`() {
        val h = AdsKillSwitchTest.newAdsHelper()
        val c = ctx(mapOf(GATE_OFF to 1))
        h.applyRemotePolicy(policy("""{"ads_cf_unit_gate_off":0}"""))
        assertEquals("RC 0 có khoá thắng resource 1", listOf(0), read(c, GATE_OFF))
        h.applyRemotePolicy("""{"schema_version":1}""")
        assertEquals("RC không có khoá ⇒ resource", listOf(1), read(c, GATE_OFF))
        h.applyRemotePolicy(policy("""{"ads_cf_native_media_ratio":2}"""))
        assertEquals("khoá khác có mặt không chạm tên vắng trong RC", listOf(1, 2), read(c, GATE_OFF, RATIO))
    }

    @Test fun `RC2 restorePolicy ban luu cung ket qua, UnitGate isOff theo RC`() {
        val h = AdsKillSwitchTest.newAdsHelper()
        h.restorePolicy(prefsContext(policy("""{"ads_cf_unit_gate_off":1,"ads_cf_native_media_ratio":1}""")))
        val c = ctx()
        assertEquals(listOf(1, 1), read(c, GATE_OFF, RATIO))
        assertTrue("UnitGate chốt isOff từ RC (lần đọc đầu của process)", UnitGate.isOff(c))
    }

    @Test fun `RC2b RC ads_cf_unit_gate_prio_off 1 thi UnitGate tat muc (PRIO lui 2_4_4)`() {
        val c = ctx()
        UnitGate.isOff(c)
        assertEquals("RC vắng: full_2_* là PRELOAD", UnitGate.PRELOAD, UnitGate.level("full_2_default"))
        UnitGate.resetForTest()
        AdsKillSwitchTest.newAdsHelper().applyRemotePolicy(policy("""{"ads_cf_unit_gate_prio_off":1}"""))
        UnitGate.isOff(c)   // chốt công tắc một lần mỗi process, như lần mở app sau khi RC về
        assertEquals("RC prio_off = 1: không mức (mọi lượt APP, FIFO như 2.4.4)", UnitGate.APP, UnitGate.level("full_2_default"))
    }

    @Test fun `RC3 ten ngoai allowlist, kieu sai, ngoai khoang thi bo va logE, ten hop le giu`() {
        val bad = listOf(
            "ads_cf_admob_init_wait_ms" to "500",
            GATE_OFF to "true", GATE_OFF to "\"x\"", GATE_OFF to "1.5", GATE_OFF to "{}", GATE_OFF to "-1", GATE_OFF to "2", GAP_OWN to "2",
            RATIO to "4", PRIO_OFF to "2", GAP_OWN to "[1]",
        )
        val h = AdsKillSwitchTest.newAdsHelper()
        val c = ctx(mapOf(GATE_OFF to 0, RATIO to 3, "ads_cf_admob_init_wait_ms" to 0))
        bad.forEach { (name, value) ->
            AdsCfOverrides.resetForTest()
            val ok = if (name == PRIO_OFF) GAP_OWN else PRIO_OFF   // một tên hợp lệ khác trong cùng object
            mockStatic(Log::class.java).use { log ->
                h.applyRemotePolicy(policy("""{"$name":$value,"$ok":1}"""))
                assertEquals("$name=$value bị bỏ ⇒ resource", listOf(if (name == RATIO) 3 else 0), read(c, name))
                assertEquals("tên hợp lệ cùng object vẫn áp ($name=$value)", listOf(1), read(c, ok))
                log.verify({ Log.e(eq(LogUtil.TAG), startsWith("ads cf overrides")) }, times(1))
                log.verify({ Log.e(eq(LogUtil.TAG), contains(name)) }, times(1))
            }
        }
    }

    @Test fun `RC3b ads_cf_overrides khong phai object, JSON hong, schema la thi rong`() {
        val h = AdsKillSwitchTest.newAdsHelper()
        val c = ctx()
        mockStatic(Log::class.java).use { log ->
            h.applyRemotePolicy(policy("[1]"))
            assertEquals(listOf(0), read(c, GATE_OFF))
            log.verify({ Log.e(eq(LogUtil.TAG), startsWith("ads cf overrides")) }, times(1))
        }
        listOf("""{"schema_version":2,"ads_cf_overrides":{"ads_cf_unit_gate_off":1}}""",
            """{"ads_cf_overrides":{"ads_cf_unit_gate_off":1""", "[1]").forEach { raw ->
            h.applyRemotePolicy(policy("""{"ads_cf_unit_gate_off":1}"""))
            h.applyRemotePolicy(raw)
            assertEquals("$raw ⇒ rỗng (resource)", listOf(0), read(c, GATE_OFF))
        }
    }

    @Test fun `RC3c ads_cf_overrides trong mot tier by_tier bi bo kem logE`() {
        AdsPolicyTiers.countryProvider = { "US" }
        val h = AdsKillSwitchTest.newAdsHelper()
        mockStatic(Log::class.java).use { log ->
            h.applyRemotePolicy("""{"schema_version":1,"by_tier":{"T1":{"ads_cf_overrides":{"ads_cf_unit_gate_off":1}}}}""")
            assertEquals("khoá trong tier không áp", listOf(0), read(ctx(), GATE_OFF))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("'ads_cf_overrides'")) }, times(1))
        }
    }

    @Test fun `RC4 ads_cf_overrides khong bi AdsPolicy logE khoa la`() {
        val h = AdsKillSwitchTest.newAdsHelper()
        mockStatic(Log::class.java).use { log ->
            h.applyRemotePolicy(policy("""{"ads_cf_unit_gate_off":1}"""))
            log.verify({ Log.e(anyString(), contains("khoá lạ")) }, never())
        }
    }

    @Test fun `RC5 mot dong Log i khi ap khac rong, RC vang thi 0 dong`() {
        val h = AdsKillSwitchTest.newAdsHelper()
        mockStatic(Log::class.java).use { log ->
            h.applyRemotePolicy(policy("""{"ads_cf_unit_gate_off":1,"ads_cf_native_media_ratio":1}"""))
            log.verify({ Log.i(LogUtil.TAG, "ads cf overrides: ads_cf_unit_gate_off=1, ads_cf_native_media_ratio=1") }, times(1))
            log.verify({ Log.i(eq(LogUtil.TAG), startsWith("ads cf overrides")) }, times(1))
        }
        mockStatic(Log::class.java).use { log ->
            h.applyRemotePolicy("""{"schema_version":1}""")
            h.applyRemotePolicy(policy("{}"))
            h.applyRemotePolicy("")
            log.verify({ Log.i(eq(LogUtil.TAG), startsWith("ads cf overrides")) }, never())
        }
    }

    @Test fun `RC6 map bat bien trong field Volatile, apply thay ca map`() {
        val field = AdsCfOverrides::class.java.declaredFields
            .firstOrNull { Map::class.java.isAssignableFrom(it.type) && !Modifier.isFinal(it.modifiers) }
        assertNotNull("AdsCfOverrides giữ giá trị trong một field Map (var)", field)
        field!!.isAccessible = true
        assertTrue("field @Volatile (RC callback ≠ main)", Modifier.isVolatile(field.modifiers))
        AdsCfOverrides.apply(policy("""{"ads_cf_unit_gate_off":1}"""))
        val before = field.get(AdsCfOverrides)
        @Suppress("UNCHECKED_CAST")
        try {
            (before as MutableMap<String, Int>)["ads_cf_unit_gate_off"] = 0
            fail("map phải bất biến")
        } catch (expected: UnsupportedOperationException) {
        }
        AdsCfOverrides.apply(policy("""{"ads_cf_native_media_ratio":2}"""))
        assertTrue("apply thay cả map, không sửa map cũ", field.get(AdsCfOverrides) !== before)
        assertEquals(mapOf("ads_cf_unit_gate_off" to 1), before)
    }

    private companion object {
        const val PKG = "app"
        const val GATE_OFF = "ads_cf_unit_gate_off"
        const val RATIO = "ads_cf_native_media_ratio"
        const val PRIO_OFF = "ads_cf_unit_gate_prio_off"
        const val GAP_OWN = "ads_cf_open_gap_own_only"
    }
}
