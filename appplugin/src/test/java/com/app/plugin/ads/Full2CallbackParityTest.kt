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
 * Full2 theo chuẩn Speaker Clean (doc 62 mục 3.M), phần 1.3.0 còn thiếu khi đưa về SDK:
 * - app nhận onAdShowed, onAdImpression, onAdClick của Full2 (placement `full_2_*`). Speaker Clean
 *   dựa vào onAdShowed của Full2 để biết chuyển cảnh đã xong;
 * - click vào Full2 cũng mở cửa sổ chặn quảng cáo tự động khi người dùng quay lại;
 * - Full2 hiện với flagShow 0: nút đóng đếm ngược từ `onStart`, và dialog dựng sẵn phía sau quảng
 *   cáo cha (dựng với isDelay = false) được dùng lại thay vì bị bỏ.
 */
class Full2CallbackParityTest {

    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed:$placement"
        }
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) {
            events += "impression:$placement"
        }
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) {
            events += "click:$placement"
        }
    }

    /** Mạng có sẵn Full2: hiện ngay và phát lần lượt showed, impression, click. */
    private class Full2Network(context: Context, private val loaded: AdsPlBase) :
        AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "test_net"
        var lastFlagShow: Int? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_full2_data"
        override fun getKeyForResDf(): String = "test_full2_res"
        override fun getAdsForPl(placement: String): AdsPlBase = loaded
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            lastFlagShow = flagShow
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            adsShowCb.onAdImpression(placement, placement, "test")
            adsShowCb.onAdClick(placement, placement, "test")
            return true
        }
    }

    private lateinit var helper: AdsHelper
    private lateinit var network: Full2Network

    private fun <T> field(name: String): T =
        @Suppress("UNCHECKED_CAST")
        (AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper) as T)

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

        val loaded = mock(AdsPlBase::class.java)
        `when`(loaded.hasLoadedAd()).thenReturn(true)
        network = Full2Network(context, loaded)

        helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply {
            full2StepCicle.add(AdsIDDef.AdmobFullNt)
        }
        helper.placementsConfigManager = AdsPlacementConfigManager()
        setField("listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to network))
        field<FullScreenAdPolicy>("fullScreenAdPolicy").onHostActivityResumed()
    }

    @Test
    fun `app nhan showed impression va click cua Full2`() {
        val cb = RecordingShowCb()

        helper.showFull2("full_home", cb)

        assertEquals(
            listOf("showed:full_2_home", "impression:full_2_home", "click:full_2_home"),
            cb.events
        )
    }

    @Test
    fun `Full2 hien voi flagShow 0 de dem nguoc tu onStart va dung lai dialog dung san`() {
        helper.showFull2("full_home", RecordingShowCb())

        assertEquals(0, network.lastFlagShow)
    }

    @Test
    fun `click vao Full2 chan quang cao tu dong khi nguoi dung quay lai`() {
        helper.showFull2("full_home", RecordingShowCb())

        assertTrue(field<FullScreenAdPolicy>("fullScreenAdPolicy").shouldSuppressAutomaticFullscreen())
    }
}
