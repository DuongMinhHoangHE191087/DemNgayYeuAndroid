package com.app.plugin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Doc 60 G: kill switch cho từng module và cho một bản SDK hỏng, qua Remote Config `sdk_kill_android`. */
class SdkKillSwitchTest {
    @Before fun setUp() { SdkKillSwitch.current = SdkKillSwitch.NONE }
    @After fun tearDown() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        LogUtil.setFlagLog(1)
    }

    @Test fun `khong co config thi khong tat gi`() {
        val k = SdkKillSwitch.parse(null)
        assertFalse(k.killsAds("1.4.0")); assertFalse(k.killsIap("1.4.0")); assertFalse(k.killsLogevent("1.4.0"))
    }

    @Test fun `tat theo module`() {
        val k = SdkKillSwitch.parse("""{"ads":true}""")
        assertTrue(k.killsAds("1.4.0")); assertFalse(k.killsIap("1.4.0"))
    }

    @Test fun `tat moi thu cho ban SDK cu hon nguong`() {
        val k = SdkKillSwitch.parse("""{"below_version":"1.4.1"}""")
        assertTrue(k.killsAds("1.4.0")); assertTrue(k.killsIap("1.3.9")); assertFalse(k.killsAds("1.4.1")); assertFalse(k.killsAds("2.0.0"))
    }

    @Test fun `so sanh semver`() {
        assertTrue(SdkKillSwitch.isBelow("1.3.10", "1.4.0"))
        assertFalse(SdkKillSwitch.isBelow("1.10.0", "1.4.0"))
        assertFalse(SdkKillSwitch.isBelow("x", "1.4.0"))
    }

    @Test fun `json hong thi khong tat gi`() = assertEquals(SdkKillSwitch.NONE, SdkKillSwitch.parse("{"))

    /** Fix wave 1, Minor 1: bản pre-release đứng dưới bản chính cùng số; hậu tố sau khoảng trắng thì không. */
    @Test fun `ban pre-release duoi ban chinh cung so`() {
        assertTrue(SdkKillSwitch.isBelow("1.4.0-rc1", "1.4.0"))
        assertFalse(SdkKillSwitch.isBelow("1.4.0-rc1", "1.3.9"))
        assertFalse(SdkKillSwitch.isBelow("1.4.1-rc1", "1.4.0"))
        assertFalse(SdkKillSwitch.isBelow("1.4.0 debug", "1.4.0"))
        assertTrue(SdkKillSwitch.isBelow("1.3.9 debug", "1.4.0"))
        assertFalse("ngưỡng không phải số thì không so", SdkKillSwitch.isBelow("1.3.0", "1.4.0-rc1"))
        assertTrue(SdkKillSwitch.parse("""{"below_version":"1.4.0"}""").killsAds("1.4.0-rc1"))
    }

    /** Fix wave 1, Minor 1: `below_version` sai dạng bị bỏ và có logE, không lẳng lặng thành "không tắt". */
    @Test fun `below_version sai dang thi bo va logE, cac module khac van ap`() {
        mockStatic(Log::class.java).use { log ->
            val k = SdkKillSwitch.parse("""{"ads":true,"below_version":"1.4.x"}""")

            assertEquals(SdkKillSwitch(ads = true), k)
            log.verify { Log.e(eq(LogUtil.TAG), contains("below_version '1.4.x'")) }
        }
    }

    /** Fix wave 1, Minor 2: khoá không tắt gì của bản này thì chỉ logD, không logE. */
    @Test fun `khoa khong ap cho ban nay thi chi logD`() {
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java).use { log ->
            SdkKillSwitch.applyRemote(null, """{"below_version":"1.0.0"}""")

            assertEquals("none", SdkKillSwitch.current.label(BuildConfig.SDK_VERSION))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("sdk_kill_android từ")) }, never())
            log.verify { Log.d(eq(LogUtil.TAG), contains("sdk_kill_android từ Remote Config")) }

            SdkKillSwitch.applyRemote(null, """{"iap":true}""")
            log.verify { Log.e(eq(LogUtil.TAG), contains("→ tắt iap")) }
        }
    }

    /**
     * Đợt sửa cuối Phase B, mục 8: đọc từng trường riêng. `below_version` null hay object từng làm cả khoá
     * thành [SdkKillSwitch.NONE] — mất một `ads:true` hợp lệ, đúng lúc ops cần tắt khẩn.
     */
    @Test fun `below_version null hay object thi chi bo truong do, ads true van ap, log tung truong`() {
        mockStatic(Log::class.java).use { log ->
            assertEquals(SdkKillSwitch(ads = true), SdkKillSwitch.parse("""{"ads":true,"below_version":null}"""))
            assertEquals(SdkKillSwitch(ads = true), SdkKillSwitch.parse("""{"ads":true,"below_version":{}}"""))
            assertEquals(SdkKillSwitch(iap = true), SdkKillSwitch.parse("""{"iap":true,"below_version":["1.4.1"]}"""))

            log.verify({ Log.e(eq(LogUtil.TAG), contains("'below_version'")) }, times(3))
        }
    }

    /** Mục 8: `asBoolean` trên giá trị không phải true/false từng lẳng lặng ra `false`. Giờ bỏ trường đó, có log. */
    @Test fun `ads khong phai true false thi bo truong do va logE, truong khac van ap`() {
        mockStatic(Log::class.java).use { log ->
            assertEquals(SdkKillSwitch(iap = true), SdkKillSwitch.parse("""{"ads":1,"iap":true}"""))
            assertEquals(SdkKillSwitch(logevent = true), SdkKillSwitch.parse("""{"ads":"yes","logevent":true}"""))
            assertEquals(SdkKillSwitch(ads = true), SdkKillSwitch.parse("""{"ads":true,"iap":{"on":true}}"""))

            log.verify { Log.e(eq(LogUtil.TAG), contains("'ads' = 1")) }
            log.verify { Log.e(eq(LogUtil.TAG), contains("'ads' = \"yes\"")) }
            log.verify { Log.e(eq(LogUtil.TAG), contains("'iap' = {\"on\":true}")) }
        }
        // Chuỗi "true"/"false" vẫn đọc như trước (Gson asBoolean), không log.
        assertEquals(SdkKillSwitch(ads = true), SdkKillSwitch.parse("""{"ads":"true","iap":"FALSE"}"""))
    }

    @Test fun `json khong phai object, nguong hong thi khong tat gi`() {
        assertEquals(SdkKillSwitch.NONE, SdkKillSwitch.parse("""["ads"]"""))
        assertEquals(SdkKillSwitch.NONE, SdkKillSwitch.parse("""{"ads":null}"""))
        assertFalse(SdkKillSwitch.parse("""{"below_version":"abc"}""").killsAds("1.3.1"))
    }

    /** Giá trị `kill` của `sdk_health` (addendum A7). */
    @Test fun `nhan kill cho sdk_health`() {
        assertEquals("none", SdkKillSwitch.NONE.label("1.4.0"))
        assertEquals("ads", SdkKillSwitch(ads = true).label("1.4.0"))
        assertEquals("ads,iap,logevent", SdkKillSwitch(ads = true, iap = true, logevent = true).label("1.4.0"))
        assertEquals("version", SdkKillSwitch(iap = true, belowVersion = "1.4.1").label("1.4.0"))
        assertEquals("none", SdkKillSwitch(belowVersion = "1.4.0").label("1.4.0"))
    }

    /** Addendum A5: kill của phiên trước có hiệu lực ngay lúc mở app, trước khi mạng quảng cáo khởi tạo. */
    @Test fun `ap tu Remote Config thi luu, lan mo ke khoi phuc tu ban luu`() {
        val (context, editor) = prefsContext(saved = null)

        SdkKillSwitch.applyRemote(context, """{"ads":true}""")

        assertTrue(SdkKillSwitch.current.killsAds(BuildConfig.SDK_VERSION))
        verify(editor).putString(SdkKillSwitch.PREF_KEY, """{"ads":true}""")

        SdkKillSwitch.current = SdkKillSwitch.NONE   // process mới
        SdkKillSwitch.restore(prefsContext(saved = """{"ads":true}""").first)
        assertTrue(SdkKillSwitch.current.killsAds(BuildConfig.SDK_VERSION))
    }

    @Test fun `khoa bi go tren console thi het tat va luu chuoi rong`() {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        val (context, editor) = prefsContext(saved = """{"ads":true}""")

        SdkKillSwitch.applyRemote(context, "")

        assertEquals(SdkKillSwitch.NONE, SdkKillSwitch.current)
        verify(editor).putString(SdkKillSwitch.PREF_KEY, "")
    }

    /** Điều 13: đọc hay lưu lỗi thì không tắt gì thêm (không bao giờ cấp gì) và không ném; Điều 7: có log. */
    @Test fun `ban luu doc loi thi khong tat gi, khong nem`() {
        SdkKillSwitch.current = SdkKillSwitch(iap = true)
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenThrow(IllegalStateException("disk"))

        SdkKillSwitch.restore(context)
        assertEquals(SdkKillSwitch.NONE, SdkKillSwitch.current)

        SdkKillSwitch.applyRemote(context, """{"logevent":true}""")   // lưu lỗi: vẫn áp cho phiên này
        assertTrue(SdkKillSwitch.current.killsLogevent(BuildConfig.SDK_VERSION))

        SdkKillSwitch.applyRemote(null, """{"iap":true}""")   // chưa có context: vẫn áp
        assertTrue(SdkKillSwitch.current.killsIap(BuildConfig.SDK_VERSION))
    }

    private fun prefsContext(saved: String?): Pair<Context, SharedPreferences.Editor> {
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(SdkKillSwitch.PREF_KEY, "")).thenReturn(saved ?: "")
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context to editor
    }
}
