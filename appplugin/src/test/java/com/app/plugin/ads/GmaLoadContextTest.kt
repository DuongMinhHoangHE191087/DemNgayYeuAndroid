package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`

/**
 * Task N (DOC/65 §7 #18 phương án b): khoá `gma_load_app_context` trong `ads_policy_android`. Chỉ JSON boolean `true`
 * bật; thiếu khoá, sai kiểu, JSON hỏng → `false` (hành vi cũ: nạp GMA bằng Activity). Giá trị đi cùng đường áp của
 * [AdsPolicy] (Remote Config về, và bản đã lưu lúc mở app), cache trong bộ nhớ, không đọc prefs lúc nạp.
 */
class GmaLoadContextTest {

    @After
    fun tearDown() {
        GmaLoadContext.apply(null)
        AdsPolicy.current = AdsPolicy.DEFAULT
        LogUtil.setFlagLog(1)
    }

    @Test fun `true la bat`() {
        assertTrue(GmaLoadContext.parse("""{"gma_load_app_context":true}"""))
        assertTrue(GmaLoadContext.parse("""{"schema_version":1,"interstitial_min_gap_seconds":60,"gma_load_app_context":true}"""))
    }

    @Test fun `thieu khoa, false, null, rong la tat`() {
        assertFalse(GmaLoadContext.parse(null))
        assertFalse(GmaLoadContext.parse(""))
        assertFalse(GmaLoadContext.parse("{}"))
        assertFalse(GmaLoadContext.parse("""{"schema_version":1,"app_open_from_session":2}"""))
        assertFalse(GmaLoadContext.parse("""{"gma_load_app_context":false}"""))
    }

    @Test fun `sai kieu la tat, co logE`() {
        mockStatic(Log::class.java).use { log ->
            assertFalse(GmaLoadContext.parse("""{"gma_load_app_context":"true"}"""))
            assertFalse(GmaLoadContext.parse("""{"gma_load_app_context":1}"""))
            assertFalse(GmaLoadContext.parse("""{"gma_load_app_context":null}"""))
            assertFalse(GmaLoadContext.parse("""{"gma_load_app_context":{}}"""))
            assertFalse(GmaLoadContext.parse("""{"gma_load_app_context":[true]}"""))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("gma_load_app_context")) }, org.mockito.Mockito.times(5))
        }
    }

    @Test fun `JSON hong, khong phai object, schema khac la tat`() {
        assertFalse(GmaLoadContext.parse("""{"gma_load_app_context":tru"""))
        assertFalse(GmaLoadContext.parse("[true]"))
        assertFalse(GmaLoadContext.parse("true"))
        assertFalse("schema khác: cả policy về mặc định, cờ cũng tắt",
            GmaLoadContext.parse("""{"schema_version":2,"gma_load_app_context":true}"""))
    }

    @Test fun `khoa moi khong bi AdsPolicy coi la khoa la`() {
        mockStatic(Log::class.java).use { log ->
            val p = AdsPolicy.parse("""{"schema_version":1,"app_open_from_session":2,"gma_load_app_context":true}""")
            assertEquals(2, p.appOpenFromSession)
            log.verify({ Log.e(anyString(), contains("khoá lạ")) }, never())
        }
    }

    @Test fun `apply cap nhat enabled, doi giua phien thi theo gia tri moi`() {
        GmaLoadContext.apply("""{"gma_load_app_context":true}""")
        assertTrue(GmaLoadContext.enabled)
        GmaLoadContext.apply("""{"gma_load_app_context":false}""")
        assertFalse(GmaLoadContext.enabled)
        GmaLoadContext.apply("""{"gma_load_app_context":true}""")
        GmaLoadContext.apply("")
        assertFalse("khoá bị gỡ là về mặc định", GmaLoadContext.enabled)
    }

    /**
     * `prefs.getString(AdsPolicy.PREF_KEY, "")` được mock trả cố định [on], không đổi theo `putString` thật sự đã
     * gọi — nên `restorePolicy(ctx)` bên dưới luôn thấy [on] bất kể `applyRemotePolicy` trước đó đã lưu gì. Test
     * này chứng minh đường nối `restorePolicy → GmaLoadContext.apply` (mục tiêu của brief), KHÔNG phải một vòng
     * lưu rồi đọc lại thật (save→restore round trip) — xem Task N review L1.
     */
    @Test fun `Remote Config ve va ban da luu luc mo app deu dat co`() {
        val helper = AdsKillSwitchTest.newAdsHelper()
        val on = """{"schema_version":1,"gma_load_app_context":true}"""
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(AdsPolicy.PREF_KEY, "")).thenReturn(on)
        val ctx = mock(Context::class.java)
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        AdsHelper::class.java.getDeclaredField("mContext").apply { isAccessible = true }.set(helper, ctx)

        helper.applyRemotePolicy(on)
        assertTrue(GmaLoadContext.enabled)
        helper.applyRemotePolicy("")
        assertFalse(GmaLoadContext.enabled)

        helper.restorePolicy(ctx)                      // prefs mock trả cố định "on" — không phải bản vừa lưu ở trên
        assertTrue(GmaLoadContext.enabled)
        helper.applyRemotePolicy("""{"gma_load_app_context":"yes"}""")
        assertFalse(GmaLoadContext.enabled)
    }
}
