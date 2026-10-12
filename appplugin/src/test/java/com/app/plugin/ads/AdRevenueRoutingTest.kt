package com.app.plugin.ads

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Doc 62 mục 2.3: AdMob đã liên kết Firebase thì Firebase tự ghi ad_impression; SDK ghi thêm là đếm đôi. */
class AdRevenueRoutingTest {
    @After fun tearDown() { AdRevenueRouting.admobLinkedToFirebase = false }

    @Test fun `mac dinh SDK van ghi ad_impression cho admob`() =
        assertTrue(AdRevenueRouting.shouldLogFirebaseAdImpression("admob"))

    @Test fun `da lien ket thi khong ghi cho admob, van ghi cho mang khac`() {
        AdRevenueRouting.admobLinkedToFirebase = true
        assertFalse(AdRevenueRouting.shouldLogFirebaseAdImpression("admob"))
        assertTrue(AdRevenueRouting.shouldLogFirebaseAdImpression("applovin"))
    }

    // ═══ Đọc integer resource của app: thiếu, khác 1, hay đọc lỗi đều là "chưa liên kết" ═══
    // Mặc định phải là chưa liên kết: đếm đôi còn lọc lại được, còn SDK im lặng khi app chưa
    // liên kết thì doanh thu AdMob biến mất khỏi Firebase.

    private class AppRes(resId: Int, value: () -> Int) {
        val res: Resources = mock(Resources::class.java)
        val context: Context = mock(Context::class.java)

        init {
            `when`(context.packageName).thenReturn(PKG)
            `when`(context.resources).thenReturn(res)
            `when`(res.getIdentifier("ads_admob_firebase_linked", "integer", PKG)).thenReturn(resId)
            `when`(res.getInteger(resId)).thenAnswer { value() }
        }
    }

    @Test fun `app khong khai resource thi chua lien ket va khong doc gia tri`() {
        val app = AppRes(0) { 1 }
        assertFalse(AdRevenueRouting.readAdmobLinked(app.context))
        verify(app.res, never()).getInteger(anyInt())
    }

    @Test fun `chi gia tri 1 la da lien ket`() {
        assertTrue(AdRevenueRouting.readAdmobLinked(AppRes(RES_ID) { 1 }.context))
        assertFalse(AdRevenueRouting.readAdmobLinked(AppRes(RES_ID) { 0 }.context))
        assertFalse(AdRevenueRouting.readAdmobLinked(AppRes(RES_ID) { 2 }.context))
    }

    @Test fun `doc resource loi thi log va coi la chua lien ket`() {
        mockStatic(Log::class.java).use { log ->
            val app = AppRes(RES_ID) { throw Resources.NotFoundException("ads_admob_firebase_linked") }
            assertFalse(AdRevenueRouting.readAdmobLinked(app.context))
            log.verify { Log.e(eq(LogUtil.TAG), contains("ads_admob_firebase_linked")) }
        }
    }

    private companion object {
        const val PKG = "com.example.game"
        const val RES_ID = 0x7f0b0001
    }
}
