package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Hợp đồng của show: giá trị trả về `false` là "không hiện". `onAdShowFail` chỉ dành cho quảng
 * cáo đã được giao cho một mạng mà mạng đó không hiện được.
 *
 * Trước 1.3.1, mạng không có cấu hình cho placement gọi `onAdShowFail("not pl config")` ngay giữa
 * vòng waterfall. Mạng sau vẫn hiện được, nên app nhận "lỗi" rồi quảng cáo vẫn lên (doc 64 mục D).
 */
class ShowWaterfallCallbackTest {

    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
    }

    /** Mạng thật (logic của [AdsBase]) nhưng không có placement nào được cấu hình. */
    private class UnconfiguredNetwork(context: Context) : AdsBaseFull(AdsIDDef.FBFullNt, context) {
        override val eventTag: String get() = "test_net"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_unconfigured_data"
        override fun getKeyForResDf(): String = "test_unconfigured_res"
    }

    /** Mạng có quảng cáo sẵn: hiện được và báo onAdShowed. */
    private class ShowingNetwork(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "test_net"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_showing_data"
        override fun getKeyForResDf(): String = "test_showing_res"
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
    }

    private lateinit var context: Context
    private lateinit var helper: AdsHelper

    private fun field(name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt()))
            .thenReturn(mock(SharedPreferences::class.java))

        helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply { full2FlagShow = 0 }
        helper.placementsConfigManager = AdsPlacementConfigManager()
    }

    @Test
    fun `mang chua cau hinh placement tra false va khong goi onAdShowFail`() {
        val cb = RecordingShowCb()

        val shown = UnconfiguredNetwork(context).showAd("full_home", cb)

        assertFalse(shown)
        assertTrue(cb.events.isEmpty())
    }

    @Test
    fun `mang chua cau hinh dung truoc mang hien duoc thi app chi nhan onAdShowed`() {
        field(
            "listAdsFull",
            mutableMapOf<Int, AdsBaseFull>(
                AdsIDDef.FBFullNt to UnconfiguredNetwork(context),
                AdsIDDef.AdmobFullNt to ShowingNetwork(context),
            )
        )
        val cb = RecordingShowCb()

        // flagImage 5: FBFullNt đứng trước AdmobFullNt trong waterfall.
        val shown = helper.showInterstitial("home", 5, cb, isDeltaTime = false)

        assertTrue(shown)
        assertEquals(listOf("showed"), cb.events)
    }
}
