package com.app.plugin.ads

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * 2.4.4 ASPECT (Hợp đồng B, quyết định (b)): bảng `ads_cf_native_media_ratio` × hướng màn hình → mã 1 ANY / 2 LANDSCAPE /
 * 3 PORTRAIT, đọc qua resource app; một dòng `Log.i` mỗi process. Dùng chung hai module (không lớp GMA).
 */
class NativeMediaRatioTest {
    @Before fun setUp() = NativeMediaRatio.resetForTest()

    @After fun tearDown() = NativeMediaRatio.resetForTest()

    /** Context có `@integer/ads_cf_native_media_ratio` = [raw]; `null` = resource vắng. */
    private fun ctx(raw: Int?): Context {
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        if (raw != null) {
            `when`(res.getIdentifier(eq(NativeMediaRatio.RES_KEY), eq("integer"), eq(PKG))).thenReturn(RES_ID)
            `when`(res.getInteger(RES_ID)).thenReturn(raw)
        }
        return mock(Context::class.java).also {
            `when`(it.resources).thenReturn(res)
            `when`(it.packageName).thenReturn(PKG)
        }
    }

    @Test fun `NM1 pick ma 1 2 3 va khac sang gia tri tuong ung (2_5_0 NGMAP)`() {
        val got = listOf(1, 2, 3, 0, 4, -1).map { NativeMediaRatio.pick(it, "any", "landscape", "portrait") }
        assertEquals("1 any, 2 landscape, 3 portrait, khác any", listOf("any", "landscape", "portrait", "any", "any", "any"), got)
    }

    @Test fun `AS3 bang raw x huong man hinh theo quyet dinh b`() {
        val raws = listOf(null, 0, 1, 2, 3, -1, 4, 99)
        val got = raws.associateWith { raw -> listOf(true, false).map { NativeMediaRatio.forRequest(ctx(raw), it) } }
        val want = mapOf(
            null to listOf(1, 1), 0 to listOf(1, 1),
            1 to listOf(3, 2),                       // theo hướng (2.4.3): dọc PORTRAIT, ngang LANDSCAPE
            2 to listOf(2, 2), 3 to listOf(3, 3),
            -1 to listOf(1, 1), 4 to listOf(1, 1), 99 to listOf(1, 1),
        )
        assertEquals("[dọc, ngang] theo raw (vắng/0/ngoài 1–3 = ANY)", want, got)
    }

    @Test fun `AS4b dong log mot lan moi process, Log i, nhan theo raw`() {
        val labels = mapOf(null to "ANY (${NativeMediaRatio.RES_KEY}=0)", 1 to "BY_ORIENTATION (${NativeMediaRatio.RES_KEY}=1)",
            2 to "LANDSCAPE (${NativeMediaRatio.RES_KEY}=2)", 3 to "PORTRAIT (${NativeMediaRatio.RES_KEY}=3)",
            99 to "ANY (${NativeMediaRatio.RES_KEY}=99)")
        mockStatic(Log::class.java).use { log ->
            labels.forEach { (raw, label) ->
                NativeMediaRatio.resetForTest()
                repeat(3) { NativeMediaRatio.forRequest(ctx(raw), it % 2 == 0) }
                log.verify({ Log.i(LogUtil.TAG, "ads native media ratio: $label") }, times(1))
            }
            log.verify({ Log.d(anyString(), anyString()) }, never())
        }
    }

    private companion object {
        const val PKG = "com.example.app"
        const val RES_ID = 0x7f0b0042
    }
}
