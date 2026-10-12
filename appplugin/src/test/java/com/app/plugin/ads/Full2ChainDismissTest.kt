package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * App điều hướng trong `onAdDismissed` của quảng cáo cha, nên callback này phải luôn tới, đúng một
 * lần, kể cả khi config cho nối Full2. Full2 có nhiều lý do để không hiện (chưa load, remove-ads,
 * vừa click, delta time...), và không nhánh nào trong số đó báo gì cho app. Giữ dismiss của quảng
 * cáo cha lại chờ Full2 thì màn hình đứng yên sau quảng cáo.
 */
class Full2ChainDismissTest {

    private class RecordingShowCb : AdsShowCb {
        val dismissed = mutableListOf<String?>()
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            dismissed += placement
        }
    }

    /** Interstitial có sẵn quảng cáo; giữ callback để test tự đóng nó. Không có Full2 nào load. */
    private class InterstitialOnlyNetwork(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "test_net"
        var showCb: AdsShowCb? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_inters_data"
        override fun getKeyForResDf(): String = "test_inters_res"
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showCb = adsShowCb
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
    }

    private lateinit var helper: AdsHelper
    private lateinit var network: InterstitialOnlyNetwork

    private fun setField(name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    @Before
    fun setUp() {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt()))
            .thenReturn(mock(SharedPreferences::class.java))
        network = InterstitialOnlyNetwork(context)

        helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply {
            full2FlagShow = 1 // nối Full2 sau interstitial
            full2StepCicle.add(AdsIDDef.AdmobFullNt)
        }
        helper.placementsConfigManager = AdsPlacementConfigManager()
        setField("listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to network))
    }

    @Test
    fun `Full2 khong hien duoc thi app van nhan dung mot onAdDismissed cua quang cao cha`() {
        val cb = RecordingShowCb()
        // flagImage 1: AdmobFullNt đứng đầu waterfall.
        assertTrue(helper.showInterstitial("home", 1, cb, isDeltaTime = false))

        network.showCb?.onAdDismissed("full_home", "full_home", "test")

        assertEquals(listOf<String?>("full_home"), cb.dismissed)
    }
}
