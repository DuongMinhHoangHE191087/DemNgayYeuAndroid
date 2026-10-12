package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.core.AdPreloader
import com.app.plugin.ads.core.PreloadFormat
import com.app.plugin.ads.core.PreloadSlot
import com.app.plugin.app.AppPluginManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.3.0 PRELOAD — ma trận nạp lại sau khi hiện (PL7, PL7b, D17-230 a/b) và idempotence của lượt nạp sẵn trên controller thật
 * (PL9). Rewarded nạp lại `gift_default` sau dismiss/lỗi hiện đã có đặc tả ở `RewardedControllerTest` (`:234`, `:305`).
 */
class AdPreloadReloadTest {

    /** Mạng full có sẵn quảng cáo (`hasLoadedAd`), ghi mỗi `loadAd`, hiện được; test gọi dismiss/lỗi/onLoaded bằng tay. */
    private class RecFull(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_reload"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        val loads = mutableListOf<Pair<String, AdsLoadInnerCb>>()
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_reload_${adsType}_data"
        override fun getKeyForResDf(): String = "test_reload_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement to adsLoadCb }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = placement to adsShowCb
            adsShowCb.onAdShowed(placement, placement, "t", flagShow)
            return true
        }
        fun placements() = loads.map { it.first }
        fun fill(p: String) = loads.last { it.first == p }.second.onLoaded(p, p, "t", null, null)
        fun dismiss() = showing!!.let { (p, cb) -> cb.onAdDismissed(p, p, "t") }
        fun failAsync() = showing!!.let { (p, cb) -> cb.onAdShowFail(p, p, "t", "async fail") }
    }

    private class Cb : AdsShowCb

    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "failed:$error" }
    }

    private fun networkContext(): Context {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        return context
    }

    private fun setField(helper: AdsHelper, name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    /** Phiên 2, host đã resume; mạng [types] là [RecFull]; interstitial AdMob, Full2 native full AdMob, `full2FlagShow` [full2]. */
    private fun withHelper(full2: Int, vararg types: Int, check: (AdsHelper, Map<Int, RecFull>) -> Unit) {
        val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
        val savedSession = sessionField.get(AppPluginManager.instance) as Int
        sessionField.set(AppPluginManager.instance, 2)
        try {
            MainHandlerTimers.run(honorRemoveAll = true) { _ ->
                val helper = AdsKillSwitchTest.newAdsHelper()
                helper.currConfig.apply {
                    full2FlagShow = full2
                    fullStepCicle.clear(); fullStepCicle.add(AdsIDDef.AdmobFullInters); fullStepRe.clear()
                    full2StepCicle.clear(); full2StepCicle.add(AdsIDDef.AdmobFullNt); full2StepRe.clear()
                    openAdFirstType = 0; openAdtype = 0
                }
                val nets = types.associateWith { RecFull(networkContext(), it) }
                setField(helper, "listAdsFull", nets.toMutableMap<Int, AdsBaseFull>())
                setField(helper, "listAdsAll", mutableListOf<AdsBase>())
                setField(helper, "listAdsGift", mutableMapOf<Int, AdsBaseGift>())
                setField(helper, "mActivity", mock(Activity::class.java))
                helper.fullscreenPolicy().onHostActivityResumed()
                check(helper, nets)
            }
        } finally {
            sessionField.set(AppPluginManager.instance, savedSession)
        }
    }

    @Test fun `PL7 interstitial dismiss - nap lai full_default (dac ta InterstitialController 389)`() =
        withHelper(0, AdsIDDef.AdmobFullInters) { helper, nets ->
            val inter = nets.getValue(AdsIDDef.AdmobFullInters)
            assertTrue(helper.showInterstitial("home", 0, Cb(), isDeltaTime = false))
            inter.dismiss()
            assertEquals(listOf("full_default"), inter.placements())
        }

    @Test fun `PL7 D17a Full2 dong - nap lai full_2_cua placement cha, khong chi full_2_default`() =
        withHelper(1, AdsIDDef.AdmobFullInters, AdsIDDef.AdmobFullNt) { helper, nets ->
            val inter = nets.getValue(AdsIDDef.AdmobFullInters)
            val full2 = nets.getValue(AdsIDDef.AdmobFullNt)
            assertTrue(helper.showInterstitial("home", 0, Cb(), isDeltaTime = false))
            assertEquals("beginFull2Transition nạp full_2_home lúc cha hiện", listOf("full_2_home"), full2.placements())
            full2.fill("full_2_home")
            inter.dismiss()   // Full2 full_2_home hiện; cha nạp lại full_default (+ full_2_default)
            full2.dismiss()
            assertEquals(
                "Full2 đã hiện được nạp lại theo placement cha", 2, full2.placements().count { it == "full_2_home" }
            )
        }

    @Test fun `PL7 D17b mask go toan man hinh - loadFull2 khong phat request, ke ca qua rewarded`() =
        withHelper(2, AdsIDDef.AdmobFullNt) { helper, nets ->
            val full2 = nets.getValue(AdsIDDef.AdmobFullNt)
            helper.setRemoveAdsMask(RemoveAdsMask.INTERRUPTIVE)
            helper.loadFull2ForThisTurn("full_x")
            helper.loadRewardedForThisTurn("x")   // rewarded còn (INTERRUPTIVE), Full2 của nó thì không
            assertEquals(emptyList<String>(), full2.placements())
            helper.setRemoveAdsMask(RemoveAdsMask.NONE)
            helper.loadFull2ForThisTurn("full_x")
            assertEquals(listOf("full_2_x"), full2.placements())
        }

    @Test fun `PL7b app open dismiss - nap lai dung placement vua hien, mot lan`() =
        withHelper(0, AdsIDDef.AdmobFullOpen) { helper, nets ->
            val open = nets.getValue(AdsIDDef.AdmobFullOpen)
            helper.setFlag4ShowResume(true)
            assertTrue(helper.showOpenAd("resume", null))
            open.dismiss()
            assertEquals(listOf(PlacementDefault.PLFullResume), open.placements())
            assertTrue(helper.getAdsPlacementConfig(PlacementDefault.PLFullResume, 1)!!.isLoading)
            open.dismiss()   // mạng báo lặp: slot đang nạp, không mở vòng thứ hai
            assertEquals(1, open.placements().size)
        }

    @Test fun `PL7b app open loi hien sau khi hien - nap lai, full_splash khong tu nap lai`() =
        withHelper(0, AdsIDDef.AdmobFullOpen) { helper, nets ->
            val open = nets.getValue(AdsIDDef.AdmobFullOpen)
            helper.setFlag4ShowResume(true)
            assertTrue(helper.showOpenAd("resume", null))
            open.failAsync()
            assertEquals(listOf(PlacementDefault.PLFullResume), open.placements())

            assertTrue(helper.showOpenAd(PlacementDefault.PLFullSplash, null))
            open.dismiss()
            assertEquals("splash không tự nạp lại (thiết kế SDK)", listOf(PlacementDefault.PLFullResume), open.placements())
        }

    @Test fun `PL9 slot dang nap - luot preload khong mo vong nap thu hai, HelperPort doc cau hinh mang`() =
        withHelper(0, AdsIDDef.AdmobFullInters, AdsIDDef.AdmobFullOpen) { helper, nets ->
            val inter = nets.getValue(AdsIDDef.AdmobFullInters)
            val open = nets.getValue(AdsIDDef.AdmobFullOpen)
            open.mapAdsPl[PlacementDefault.PLFullSplash] = mock(AdsPlBase::class.java)
            val port = AdPreloader.HelperPort { helper }
            helper.loadInterstitialForThisTurn("default", 0)   // app đã gọi (vd. Invoice Maker sau consent)
            port.load(PreloadSlot(PreloadFormat.INTERSTITIAL, PlacementDefault.PLFullDefault))
            port.load(PreloadSlot(PreloadFormat.INTERSTITIAL, PlacementDefault.PLFullDefault))
            assertEquals(listOf("full_default"), inter.placements())

            assertTrue(port.hostReady())
            assertEquals(listOf(PlacementDefault.PLFullSplash), port.openPlacements())
            assertFalse("không có mạng rwi", port.rwiConfigured())
            helper.setRemoveAdsMask(RemoveAdsMask.INTERRUPTIVE)
            assertTrue(port.removed(RemoveAdsMask.FLAG_FULLSCREEN))
            assertFalse(port.removed(RemoveAdsMask.FLAG_REWARDED))
        }

    @Test fun `PL11 nap san dang chay - app load interstitial va app open kem cb nhan onLoaded mot lan, app thu hai cung gan vao luot (2_4_0 CARRY-ADS)`() =
        withHelper(0, AdsIDDef.AdmobFullInters, AdsIDDef.AdmobFullOpen) { helper, nets ->
            val port = AdPreloader.HelperPort { helper }
            for ((slot, net) in listOf(
                PreloadSlot(PreloadFormat.INTERSTITIAL, PlacementDefault.PLFullDefault) to nets.getValue(AdsIDDef.AdmobFullInters),
                PreloadSlot(PreloadFormat.APP_OPEN, PlacementDefault.PLFullSplash) to nets.getValue(AdsIDDef.AdmobFullOpen),
            )) {
                port.load(slot)   // lượt của SDK, không callback
                val cb = AppLoadCb()
                val cb2 = AppLoadCb()
                if (slot.format == PreloadFormat.INTERSTITIAL) {
                    helper.loadInterstitialForThisTurn("default", 0, cb)
                    helper.loadInterstitialForThisTurn("default", 0, cb2)
                } else {
                    helper.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash, cb)
                    helper.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash, cb2)
                }
                assertEquals("không mở vòng thứ hai", 1, net.placements().size)
                assertEquals(emptyList<String>(), cb.events)
                assertEquals("2.4.0 #17: cb thứ hai cũng gắn vào lượt, chưa callback", emptyList<String>(), cb2.events)
                net.fill(slot.placement)
                net.fill(slot.placement)   // mạng báo lặp: callback một lần mỗi cb (OneShotLoadCb)
                assertEquals(listOf("loaded"), cb.events)
                assertEquals(listOf("loaded"), cb2.events)
            }
        }
}
