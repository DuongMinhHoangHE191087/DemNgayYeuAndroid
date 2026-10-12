package com.app.plugin.ads.core

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsKillSwitchTest
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainHandlerTimers
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.ads.RemoveAdsMask.FLAG_FULLSCREEN
import com.app.plugin.ads.RemoveAdsMask.FLAG_REWARDED
import com.app.plugin.brain.LicenseTestRule
import com.app.plugin.logevent.AdEventLog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.4.0 CARRY-ADS (Hợp đồng D; minors-230 #8, #17, #21): rewarded nạp lại placement vừa hiện (CA1, CA2); lời gọi load
 * khi slot đang nạp GẮN callback vào lượt — mọi callback nhận kết quả đúng một lần theo thứ tự gắn (CA3 interstitial,
 * CA3b tầng mạng `AdsPlBase`, CA4 app open, CA4b rewarded); lượt preload kết thúc không fill bỏ dấu `trigger=preload`
 * ngay (CA5). Mạng giả ghi callback của mỗi `loadAd`; test tự báo kết quả. Bước kế của waterfall qua handler ảo.
 */
class LoadCallbackChainTest {
    @get:Rule val license = LicenseTestRule()

    private val events = mutableListOf<String>()

    private inner class Cb(private val name: String) : AdsLoadCb {
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "$name:loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "$name:fail:$error" }
    }

    private class FullNet(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_chain_full"
        val loads = mutableListOf<Pair<String, AdsLoadInnerCb>>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_chain_${adsType}_data"
        override fun getKeyForResDf(): String = "test_chain_${adsType}_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement to adsLoadCb }
    }

    private class GiftNet(context: Context) : AdsBaseGift(AdsIDDef.AdmobRewarded, context) {
        override val eventTag: String get() = "test_chain_gift"
        val loads = mutableListOf<Pair<String, AdsLoadInnerCb>>()
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_chain_gift_data"
        override fun getKeyForResDf(): String = "test_chain_gift_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement to adsLoadCb }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = placement to adsShowCb
            adsShowCb.onAdShowed(placement, placement, "t", flagShow)
            return true
        }
        fun dismiss() = showing!!.let { (pl, cb) -> cb.onAdDismissed(pl, pl, "t") }
        fun failAfterShown() = showing!!.let { (pl, cb) -> cb.onAdShowFail(pl, pl, "t", "present fail") }
    }

    private class Fx(val helper: AdsHelper, val inters: FullNet, val open: FullNet, val gift: GiftNet, val timers: MainHandlerTimers) {
        fun plcf(pl: String) = helper.getAdsPlacementConfig(pl, 1)!!
    }

    private fun withNets(check: Fx.() -> Unit) = MainHandlerTimers.run(honorRemoveAll = true) { timers ->
        val context = mock(Context::class.java)
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(res)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            full2FlagShow = 0
            openAdFirstType = 0; openAdtype = 1; openAdFlagImg = 1
            fullStepCicle.clear(); fullStepCicle.add(AdsIDDef.AdmobFullInters); fullStepRe.clear()
            giftStepCicle.clear(); giftStepCicle.add(AdsIDDef.AdmobRewarded); giftStepRe.clear()
        }
        val inters = FullNet(context, AdsIDDef.AdmobFullInters)
        val open = FullNet(context, AdsIDDef.AdmobFullOpen)
        val gift = GiftNet(context)
        set(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to inters, AdsIDDef.AdmobFullOpen to open))
        set(helper, "listAdsGift", mutableMapOf<Int, AdsBaseGift>(AdsIDDef.AdmobRewarded to gift))
        set(helper, "listAdsAll", mutableListOf<AdsBase>())
        Fx(helper, inters, open, gift, timers).check()
    }

    // ═══ #8: rewarded nạp lại placement vừa hiện ═══

    @Test fun `CA1 rewarded gift_premium dismiss thi nap lai gift_premium`() = withNets {
        assertEquals(true, helper.showRewarded("premium", object : AdsGiftShowCb {}))
        gift.dismiss()
        assertEquals(listOf("gift_premium"), gift.loads.map { it.first })
    }

    @Test fun `CA2 rewarded gift_premium show fail thi nap lai gift_premium`() = withNets {
        assertEquals(true, helper.showRewarded("premium", object : AdsGiftShowCb {}))
        gift.failAfterShown()
        assertEquals(listOf("gift_premium"), gift.loads.map { it.first })
    }

    // ═══ #17: nối callback khi slot đang nạp ═══

    @Test fun `CA3 interstitial hai lan load khi dang nap - cb1 roi cb2 onLoaded dung mot lan`() = withNets {
        helper.loadInterstitialForThisTurn("x", 0, Cb("cb1"))
        helper.loadInterstitialForThisTurn("x", 0, Cb("cb2"))
        assertEquals("đang nạp: không gọi mạng thêm", 1, inters.loads.size)
        inters.loads[0].second.onLoaded("full_x", "full_x", "id", null, null)
        assertEquals(listOf("cb1:loaded", "cb2:loaded"), events)
    }

    @Test fun `CA3b AdsPlBase dang load - callback thu hai gan vao luot, ca hai nhan ket qua that`() {
        val p = Probe().apply { setActivity(mock(Activity::class.java)); parserEcpms("ca-app-pub-1/a") }
        p.load("full_test", Inner("i1"))
        p.load("full_test", Inner("i2"))
        assertEquals("một request", 1, p.requests)
        p.done()
        assertEquals(listOf("i1:loaded", "i2:loaded"), events)
    }

    @Test fun `CA4 app open hai lan load khi dang nap - cb1 roi cb2 onLoaded dung mot lan`() = withNets {
        helper.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash, Cb("cb1")); timers.runPosted()
        helper.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash, Cb("cb2")); timers.runPosted()
        assertEquals(1, open.loads.size)
        open.loads[0].second.onLoaded(PlacementDefault.PLFullSplash, PlacementDefault.PLFullSplash, "id", null, null)
        timers.runPosted()
        assertEquals(listOf("cb1:loaded", "cb2:loaded"), events)
    }

    @Test fun `CA4b rewarded cb1 khong bi cb2 ghi de, loi nap thi ca hai onLoadFailed mot lan`() = withNets {
        helper.loadRewardedForThisTurn("x", Cb("cb1"))
        helper.loadRewardedForThisTurn("x", Cb("cb2"))
        assertEquals(1, gift.loads.size)
        gift.loads[0].second.onLoadFailed("gift_x", "gift_x", "id", "no fill")
        timers.runPosted()
        assertEquals(listOf("cb1:fail:list empty", "cb2:fail:list empty"), events)
    }

    // ═══ #21: preload kết thúc không fill bỏ dấu trigger=preload ═══

    @Test fun `CA5 preload no-fill cuoi thi trigger=preload xoa, request app ke tiep khong mang`() = withNets {
        AdEventLog.resetPreloadForTest()
        val r = AdEventLog.LoadRequest("interstitial", "admob", "full_x", "u", "id", "s", 1)
        AdEventLog.markPreload("full_x")
        AdPreloader.HelperPort { helper }.load(PreloadSlot(PreloadFormat.INTERSTITIAL, "full_x"))
        assertEquals("preload", AdEventLog.requestEventData(r, "full_x")["trigger"])
        inters.loads[0].second.onLoadFailed("full_x", "full_x", "id", "no fill")
        timers.runPosted()
        assertEquals("lượt preload kết thúc không fill", false, plcf("full_x").isLoading)
        assertEquals(null, AdEventLog.requestEventData(r, "full_x")["trigger"])
        AdEventLog.resetPreloadForTest()
    }

    // ═══ #27: cổng rwi của preload = cổng của controller ═══

    @Test fun `CA6 mask FLAG_FULLSCREEN bo rwi, FLAG_REWARDED mot minh khong bo rwi, kill FLAG_REWARDED thi disabled`() {
        val rwi = PreloadFormat.REWARDED_INTERSTITIAL
        assertEquals("remove_ads", AdPreloadPlan.blockReason(rwi, { it == FLAG_FULLSCREEN }, { false }))
        assertEquals(null, AdPreloadPlan.blockReason(rwi, { it == FLAG_REWARDED }, { false }))
        assertEquals("disabled", AdPreloadPlan.blockReason(rwi, { false }, { it == FLAG_REWARDED }))
    }

    private inner class Inner(private val name: String) : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) { events += "$name:loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "$name:fail:$error" }
    }

    /** Slot mạng: `tryLoad` mở request; [done] báo `onLoaded` qua callback hiện có của slot, như listener của mạng. */
    private class Probe : AdsPlBase(mock(Context::class.java), mock(AdsBase::class.java), 0, "full_test") {
        var requests = 0
        override val eventTag: String get() = "test_chain_probe"
        override fun onInit() = Unit
        override fun tryLoad() { requests++; adsPlacementEcmp.isLoading = true }
        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int) = false
        fun done() {
            adsPlacementEcmp.isLoading = false
            adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, "id", null, this)
        }
    }

    private fun set(helper: AdsHelper, name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }
}
