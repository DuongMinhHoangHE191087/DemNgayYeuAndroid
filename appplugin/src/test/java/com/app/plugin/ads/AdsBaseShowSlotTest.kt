package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.app.AppPluginManager
import com.app.plugin.brain.LicenseTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 2.4.2 SLOT (Hợp đồng C, đk 2; gap hàng 28, fork `ShowSlotChooser.kt:12-17`): show/kiểm-đã-nạp đọc slot riêng nếu nó
 * có quảng cáo, không thì slot mặc định nếu nó có, không thì `riêng ?: mặc định`; load giữ `getAdsForPl`. Với tên
 * `full_2_*` có thêm bậc `full_2_default` trước `full_default`, cho cả load lẫn show. Slot là mock [AdsPlBase] đặt vào
 * `mapAdsPl` qua setter public; mạng là [AdsBaseFull] thật (không ghi đè `getAdsForPl`/`showAd`).
 */
class AdsBaseShowSlotTest {
    @get:Rule val license = LicenseTestRule()

    private class SlotNetwork(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullInters, context) {
        override val eventTag: String get() = "test_slot"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_slot_data"
        override fun getKeyForResDf(): String = "test_slot_res"
    }

    private object Cb : AdsShowCb
    private object LoadCb : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {}
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {}
    }

    private fun context(): Context = mock(Context::class.java).also { c ->
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(c.resources).thenReturn(res)
        `when`(c.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
    }

    /** Slot giả: [loaded] = có quảng cáo sẵn sàng (và đang giữ); `show` trả [loaded], như slot thật. */
    private fun slot(loaded: Boolean, placement: String): AdsPlBase = mock(AdsPlBase::class.java).also {
        `when`(it.hasLoadedAd()).thenReturn(loaded)
        `when`(it.holdsLoadedAd()).thenReturn(loaded)
        `when`(it.show(placement, Cb, 0)).thenReturn(loaded)
        `when`(it.getAdForShow(placement, Cb)).thenReturn("ad-of-$loaded")
    }

    private fun network(vararg slots: Pair<String, AdsPlBase>) = SlotNetwork(context()).apply { mapAdsPl = mutableMapOf(*slots) }

    @Test fun `SL1 slot rieng rong, slot mac dinh co quang cao - showAd goi show cua slot mac dinh, tra true`() {
        val own = slot(false, "full_home"); val def = slot(true, "full_home")
        val net = network("full_home" to own, "full_default" to def)

        assertTrue("showAd đọc slot mặc định có quảng cáo", net.showAd("full_home", Cb, 0))
        verify(def).show("full_home", Cb, 0)
        verify(own, never()).show("full_home", Cb, 0)
    }

    @Test fun `SL2 cung dieu kien - hasLoadedAd va holdsLoadedAd true`() {
        val net = network("full_home" to slot(false, "full_home"), "full_default" to slot(true, "full_home"))

        assertTrue("hasLoadedAd(full_home) thấy quảng cáo ở slot mặc định", net.hasLoadedAd("full_home"))
        assertTrue("holdsLoadedAd(full_home) thấy quảng cáo ở slot mặc định", net.holdsLoadedAd("full_home"))
    }

    @Test fun `SL2 cung dieu kien - isInterstitialReadyToShow true`() {
        val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
        val savedSession = sessionField.get(AppPluginManager.instance) as Int
        sessionField.set(AppPluginManager.instance, 2)
        try {
            MainHandlerTimers.run(honorRemoveAll = true) { _ ->
                val helper = AdsKillSwitchTest.newAdsHelper()
                helper.currConfig.apply { full2FlagShow = 0; fullStepCicle.clear(); fullStepCicle.add(AdsIDDef.AdmobFullInters); fullStepRe.clear() }
                val net = network("full_home" to slot(false, "full_home"), "full_default" to slot(true, "full_home"))
                setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to net))
                setField(helper, "listAdsAll", mutableListOf<AdsBase>())
                setField(helper, "mActivity", mock(Activity::class.java))
                (getField(helper, "fullScreenAdPolicy") as FullScreenAdPolicy).onHostActivityResumed()
                helper.getAdsPlacementConfig("full_home", 1)!!.isLoaded = true

                assertTrue("ready đọc slot mặc định có quảng cáo", helper.isInterstitialReadyToShow("home"))
            }
        } finally {
            sessionField.set(AppPluginManager.instance, savedSession)
        }
    }

    @Test fun `SL2 holdsLoadedAd chon slot theo holdsLoadedAd - slot rieng MAX dang tu nap lai thang slot mac dinh san sang`() {
        val own = slot(false, "full_home").also { `when`(it.holdsLoadedAd()).thenReturn(true) }   // isReady() == false
        val def = slot(true, "full_home")
        val net = network("full_home" to own, "full_default" to def)

        assertTrue(net.holdsLoadedAd("full_home"))
        verify(def, never()).holdsLoadedAd()   // vị từ hasLoadedAd sẽ chọn slot mặc định (review m1, MUT-R1)
    }

    @Test fun `SL3 getAdForShow tra quang cao cua slot mac dinh`() {
        val net = network("full_home" to slot(false, "full_home"), "full_default" to slot(true, "full_home"))

        assertEquals("getAdForShow đọc slot mặc định có quảng cáo", "ad-of-true", net.getAdForShow("full_home", Cb))
    }

    @Test fun `SL4 slot rieng co quang cao - doc rieng, ca hai rong - doc rieng, loadAd va isLoading luon slot rieng`() {
        val own = slot(true, "full_home"); val def = slot(true, "full_home")
        val net = network("full_home" to own, "full_default" to def)
        assertTrue(net.showAd("full_home", Cb, 0))
        verify(own).show("full_home", Cb, 0)
        verify(def, never()).show("full_home", Cb, 0)

        val ownEmpty = slot(false, "full_home"); val defEmpty = slot(false, "full_home")
        val empty = network("full_home" to ownEmpty, "full_default" to defEmpty)
        assertFalse(empty.hasLoadedAd("full_home"))
        empty.showAd("full_home", Cb, 0)
        verify(ownEmpty).show("full_home", Cb, 0)
        verify(defEmpty, never()).show("full_home", Cb, 0)

        val loadOwn = slot(false, "full_home"); val loadDef = slot(true, "full_home")
        `when`(loadOwn.isLoadingAd).thenReturn(true)
        val load = network("full_home" to loadOwn, "full_default" to loadDef)
        load.loadAd("full_home", LoadCb)
        verify(loadOwn).load("full_home", LoadCb)
        verify(loadDef, never()).load("full_home", LoadCb)
        assertTrue("isLoading đọc slot riêng", load.isLoading("full_home"))
    }

    @Test fun `SL5 full_2_home khong slot rieng - load va show dung full_2_default, mang khong full_2_default - full_default`() {
        val f2 = slot(true, "full_2_home"); val def = slot(true, "full_2_home")
        val net = network("full_2_default" to f2, "full_default" to def)
        net.loadAd("full_2_home", LoadCb)
        assertTrue(net.showAd("full_2_home", Cb, 0))
        verify(f2).load("full_2_home", LoadCb)
        verify(f2).show("full_2_home", Cb, 0)
        verify(def, never()).load("full_2_home", LoadCb)
        verify(def, never()).show("full_2_home", Cb, 0)

        val def2 = slot(true, "full_2_home")
        val noF2 = network("full_default" to def2)
        noF2.loadAd("full_2_home", LoadCb)
        assertTrue(noF2.showAd("full_2_home", Cb, 0))
        verify(def2).load("full_2_home", LoadCb)
        verify(def2).show("full_2_home", Cb, 0)
    }

    @Test fun `SL5 ba bac khi show - rieng rong, full_2_default rong, full_default co - doc full_default, full_2_default co - doc no`() {
        val own = slot(false, "full_2_home"); val f2Empty = slot(false, "full_2_home"); val def = slot(true, "full_2_home")
        val net = network("full_2_home" to own, "full_2_default" to f2Empty, "full_default" to def)
        assertEquals("getAdForShow bậc 3", "ad-of-true", net.getAdForShow("full_2_home", Cb))
        net.loadAd("full_2_home", LoadCb)
        verify(own).load("full_2_home", LoadCb)

        val f2 = slot(true, "full_2_home"); val def2 = slot(true, "full_2_home")
        val net2 = network("full_2_home" to slot(false, "full_2_home"), "full_2_default" to f2, "full_default" to def2)
        assertTrue(net2.showAd("full_2_home", Cb, 0))
        verify(f2).show("full_2_home", Cb, 0)
        verify(def2, never()).show("full_2_home", Cb, 0)
    }

    private fun setField(helper: AdsHelper, name: String, value: Any?) =
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)

    private fun getField(helper: AdsHelper, name: String): Any? =
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper)
}
