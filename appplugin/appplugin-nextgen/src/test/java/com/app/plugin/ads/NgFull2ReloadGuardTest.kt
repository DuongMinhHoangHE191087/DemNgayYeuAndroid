package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.4.0 FIX-OOM / D26 (nextgen override `ads/core/Full2Chain.kt`): Next-Gen đưa dismiss của quảng cáo cha về main qua
 * `MainPost`, tới SAU khi host đã resume, nên `showFull2` hiện Full2 ngay (không xếp hàng như Legacy) rồi chính handler
 * dismiss đó gọi nạp lại Full2 (`loadInterstitialForThisTurn` → `loadFull2ForThisTurn`, rewarded: `loadRewardedForThisTurn`
 * → `loadFull2ForThisTurn`) trong lúc slot Full2 đang hiện → request `full_2_default` thứ hai (E-ab). Guard: slot đang hiện
 * thì không nạp; dismiss của Full2 nạp lại đúng một lần. Thứ tự Legacy (host chưa resume, Full2 xếp hàng) không đổi.
 */
class NgFull2ReloadGuardTest {

    /** Mạng toàn màn hình một bước (cha và Full2 cùng `AdmobFullNt`): ghi load/show, giữ callback show để test tự đóng. */
    private class FullNetwork(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "test_full2_guard"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        val loads = mutableListOf<String>()
        val shows = mutableListOf<Pair<String, AdsShowCb>>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_full2_guard_data"
        override fun getKeyForResDf(): String = "test_full2_guard_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement to adsShowCb
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
        fun full2Loads() = loads.filter { it.startsWith("full_2_") }
        fun dismiss(placement: String) = shows.last { it.first == placement }.second.onAdDismissed(placement, placement, "test")
    }

    private class Fixture(val helper: AdsHelper, val network: FullNetwork, val policy: FullScreenAdPolicy, val timers: MainHandlerTimers)

    private fun <T> field(target: Any, name: String): T =
        @Suppress("UNCHECKED_CAST") (target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T)

    private fun setField(target: Any, name: String, value: Any?) =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)

    /** Host đã resume (thứ tự Next-Gen), Full2 bật sau interstitial và rewarded (`full2FlagShow` 3). */
    private fun withHost(check: Fixture.() -> Unit) = MainHandlerTimers.run(honorRemoveAll = true) { timers ->
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            full2FlagShow = 3
            full2StepCicle.clear(); full2StepCicle.add(AdsIDDef.AdmobFullNt)
            full2StepRe.clear()
        }
        val network = FullNetwork(context)
        setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to network))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>(network))
        helper.setMainActivity(mock(Activity::class.java))
        val policy = field<FullScreenAdPolicy>(helper, "fullScreenAdPolicy")
        policy.onHostActivityResumed()
        Fixture(helper, network, policy, timers).check()
    }

    @Test fun `interstitial roi Full2 - dismiss cha sau khi host resume, mot request full_2_default toi khi Full2 dong`() = withHost {
        assertTrue(helper.showInterstitial("default", 1, object : AdsShowCb {}, isDeltaTime = false))
        assertEquals("Full2 nạp sau cha khi cha hiện (beginFull2Transition)", listOf("full_2_default"), network.full2Loads())
        helper.getAdsPlacementConfig("full_2_default", 1)!!.isLoading = false // lượt nạp đó đã fill (mạng giả không gọi lại)
        network.loads.clear()

        network.dismiss("full_default") // dismiss cha (Next-Gen: tới main sau khi host resume)
        timers.runPosted()

        assertEquals("Full2 hiện ngay (không xếp hàng)", listOf("full_default", "full_2_default"), network.shows.map { it.first })
        assertEquals("không nạp lại full_2_default khi Full2 đang hiện", emptyList<String>(), network.full2Loads())
        network.dismiss("full_2_default")
        timers.runPosted()
        assertEquals("dismiss Full2 nạp lại đúng một lần", listOf("full_2_default"), network.full2Loads())
    }

    @Test fun `rewarded roi Full2 - showFull2 roi nap lai cua rewarded khi Full2 dang hien, mot request toi khi Full2 dong`() = withHost {
        // Đúng hai lời gọi của dismiss rewarded (`RewardedController`): showFull2(gift_default) rồi loadRewardedForThisTurn
        // → loadFull2ForThisTurn(gift_default) (full2FlagShow 2/3).
        helper.showFull2("gift_default", object : AdsShowCb {})
        timers.runPosted()
        assertEquals(listOf("full_2_default"), network.shows.map { it.first })

        helper.loadFull2ForThisTurn("gift_default")

        assertEquals("không nạp lại full_2_default khi Full2 đang hiện", emptyList<String>(), network.full2Loads())
        network.dismiss("full_2_default")
        timers.runPosted()
        assertEquals("dismiss Full2 nạp lại đúng một lần", listOf("full_2_default"), network.full2Loads())
    }

    @Test fun `thu tu Legacy - host chua resume, Full2 xep hang, nap lai van di toi mang (guard tro)`() = withHost {
        policy.onHostActivityPaused()
        helper.showFull2("full_default", object : AdsShowCb {})
        assertNotNull("Full2 xếp chờ host resume", field<Any?>(helper, "pendingFull2"))
        assertEquals(emptyList<String>(), network.shows.map { it.first })

        helper.loadFull2ForThisTurn("full_default")

        assertEquals("slot không hiện: nạp đi tới mạng như trước (mạng tự trả has loaded)", listOf("full_2_default"), network.full2Loads())
    }
}
