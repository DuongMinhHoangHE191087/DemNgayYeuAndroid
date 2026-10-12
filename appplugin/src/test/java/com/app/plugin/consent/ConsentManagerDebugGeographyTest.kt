package com.app.plugin.consent

import android.app.Activity
import android.content.res.Resources
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.lang.reflect.Method

/**
 * Task 8-0 (1.6.0): system property `appplugin.debug_geography` ép geography UMP debug (US-state
 * hoặc "other") để thăm dò sớm form US-state của Google mà không cần bản release. Chỉ có tác dụng
 * khi `testIds.isNotEmpty()` — cùng điều kiện với `debugForceEea` hôm nay. Áp dụng SAU
 * `debugForceEea` trong `buildParams`: nếu cả hai cùng đặt, property thắng (gọi
 * `setDebugGeography` sau cùng ghi đè giá trị của lần gọi trước trên cùng builder).
 */
class ConsentManagerDebugGeographyTest {

    @Before
    fun setUp() {
        ConsentManager.debugTestDeviceHashedIds = emptyList()
        ConsentManager.debugForceEea = false
        System.clearProperty("appplugin.debug_geography")
    }

    @After
    fun tearDown() {
        ConsentManager.debugTestDeviceHashedIds = emptyList()
        ConsentManager.debugForceEea = false
        System.clearProperty("appplugin.debug_geography")
    }

    private fun buildParams(activity: Activity): ConsentRequestParameters {
        val method: Method = ConsentManager::class.java.getDeclaredMethod("buildParams", Activity::class.java)
        method.isAccessible = true
        return method.invoke(ConsentManager, activity) as ConsentRequestParameters
    }

    /** Activity không bật `flagTest`: `AppUtil.isTestFlagOn` trả `false` (resource không tồn tại). */
    private fun activityWithNoTestFlag(): Activity {
        val resources = mock(Resources::class.java)
        val activity = mock(Activity::class.java)
        `when`(activity.resources).thenReturn(resources)
        return activity
    }

    // ═══ Map chuỗi → hằng số UMP (hàm thuần, không phụ thuộc testIds) ═══

    @Test
    fun `us map sang DEBUG_GEOGRAPHY_REGULATED_US_STATE`() {
        assertEquals(
            ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE,
            ConsentManager.debugGeographyFor("us"),
        )
    }

    @Test
    fun `other map sang DEBUG_GEOGRAPHY_OTHER`() {
        assertEquals(
            ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER,
            ConsentManager.debugGeographyFor("other"),
        )
    }

    @Test
    fun `gia tri khong nhan dien thi khong map`() {
        assertNull(ConsentManager.debugGeographyFor("eu"))
        assertNull(ConsentManager.debugGeographyFor(""))
        assertNull(ConsentManager.debugGeographyFor("US"))
    }

    @Test
    fun `null thi khong map`() {
        assertNull(ConsentManager.debugGeographyFor(null))
    }

    // ═══ testIds rỗng: không đặt debug geography dù có property ═══

    @Test
    fun `testIds rong thi khong tao ConsentDebugSettings du co property us`() {
        System.setProperty("appplugin.debug_geography", "us")

        mockConstruction(ConsentDebugSettings.Builder::class.java).use { mocked ->
            buildParams(activityWithNoTestFlag())
            assertTrue(mocked.constructed().isEmpty())
        }
    }

    // ═══ testIds không rỗng: property áp dụng ═══

    @Test
    fun `testIds khong rong va property us thi setDebugGeography US`() {
        System.setProperty("appplugin.debug_geography", "us")
        ConsentManager.debugTestDeviceHashedIds = listOf("ABCDEF0123456789")

        mockConstruction(ConsentDebugSettings.Builder::class.java).use { mocked ->
            buildParams(activityWithNoTestFlag())
            val builder = mocked.constructed().single()
            verify(builder).setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE)
        }
    }

    @Test
    fun `testIds khong rong va property other thi setDebugGeography OTHER`() {
        System.setProperty("appplugin.debug_geography", "other")
        ConsentManager.debugTestDeviceHashedIds = listOf("ABCDEF0123456789")

        mockConstruction(ConsentDebugSettings.Builder::class.java).use { mocked ->
            buildParams(activityWithNoTestFlag())
            val builder = mocked.constructed().single()
            verify(builder).setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER)
        }
    }

    @Test
    fun `khong co property thi khong goi setDebugGeography` () {
        ConsentManager.debugTestDeviceHashedIds = listOf("ABCDEF0123456789")

        mockConstruction(ConsentDebugSettings.Builder::class.java).use { mocked ->
            buildParams(activityWithNoTestFlag())
            val builder = mocked.constructed().single()
            verify(builder, org.mockito.Mockito.never()).setDebugGeography(org.mockito.ArgumentMatchers.anyInt())
        }
    }

    // ═══ Thứ tự khi cả debugForceEea và property cùng đặt: property áp dụng SAU, nên thắng ═══

    @Test
    fun `ca debugForceEea va property us cung dat thi property thang vi ap dung sau`() {
        System.setProperty("appplugin.debug_geography", "us")
        ConsentManager.debugForceEea = true
        ConsentManager.debugTestDeviceHashedIds = listOf("ABCDEF0123456789")

        mockConstruction(ConsentDebugSettings.Builder::class.java).use { mocked ->
            buildParams(activityWithNoTestFlag())
            val builder = mocked.constructed().single()
            val order = inOrder(builder)
            order.verify(builder).setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            order.verify(builder).setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE)
        }
    }
}
