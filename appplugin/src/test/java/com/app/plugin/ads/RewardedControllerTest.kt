package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Test đặc tả vùng rewarded của [AdsHelper] (plan 1.5.0 Task 9, KB-2): viết và chạy xanh trên mã CŨ trước khi chuyển
 * sang `ads.core.RewardedController`, làm lưới cho bước chuyển. Rewarded mang phần thưởng và doanh thu: phần thưởng tới
 * app đúng một lần, mỗi lượt show đúng một callback kết thúc (`onAdShowFail` hoặc `onAdDismissed`), chuỗi Full2 sau
 * rewarded (`full2FlagShow` 2/3) giữ nguyên. Mọi lời gọi đi qua hàm public của façade (`showRewarded`,
 * `loadRewardedForThisTurn`, `setPostAdScreenCooldown`, `isPostAdScreenCooldownActive`) và `var` public R3
 * (`giftTimeShow`, `giftIdxShow`, `giftCount4Circle`, `flagAdsFull*`); field R1 (`listAdsGift`, `listAdsFull`,
 * `listAdsAll`, `fullScreenAdPolicy`, `full2TransitionActive`) đọc/gán bằng reflection như các test sẵn có.
 *
 * Mạng rewarded giả phát callback theo kịch bản như `Full2CallbackParityTest.Full2Network`: `showAd` hiện được (hoặc báo
 * `onAdShowFail` ĐỒNG BỘ trong lời gọi rồi trả `false` nếu [GiftNetwork.failSync]); test tự gọi reward/click/dismiss.
 * Mạng Full2 giả (native full AdMob, `full2StepCicle`) có sẵn quảng cáo và ghi lại lời gọi `showAd` cùng giá trị
 * `flagAdsFullShowing` lúc đó. Bước load kế của waterfall đi qua `Handler.post` → handler ảo của [MainHandlerTimers].
 */
class RewardedControllerTest {

    private class GiftNetwork(context: Context, type: Int) : AdsBaseGift(type, context) {
        override val eventTag: String get() = "test_gift"
        val loads = mutableListOf<Pair<String, AdsLoadInnerCb>>()
        val shows = mutableListOf<String>()
        var failSync = false
        /** Báo `onLoaded` ĐỒNG BỘ ngay trong `loadAd`, như lối tắt "has loaded" của `AdsPlBase.load`. */
        var loadSync = false
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_gift_${adsType}_data"
        override fun getKeyForResDf(): String = "test_gift_${adsType}_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) {
            loads += placement to adsLoadCb
            if (loadSync) adsLoadCb.onLoaded(placement, placement, "t", null, null)
        }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement
            if (failSync) {
                adsShowCb.onAdShowFail(placement, placement, "t", "sync fail")
                return false
            }
            showing = placement to adsShowCb
            adsShowCb.onAdShowed(placement, placement, "t", flagShow)
            return true
        }
        private val cb get() = showing!!
        fun reward() = (cb.second as AdsGiftShowCb).onAdRewarded(cb.first, cb.first, "t")
        fun click() = cb.second.onAdClick(cb.first, cb.first, "t")
        fun impression() = cb.second.onAdImpression(cb.first, cb.first, "t")
        fun dismiss() = cb.second.onAdDismissed(cb.first, cb.first, "t")
        fun failAfterShown() = cb.second.onAdShowFail(cb.first, cb.first, "t", "present fail")
        fun loadPlacements() = loads.map { it.first }
    }

    /** Mạng Full2 có sẵn quảng cáo; ghi placement và `flagAdsFullShowing` lúc `showAd`, hiện được. */
    private class Full2Network(context: Context, private val helper: AdsHelper) : AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "test_full2"
        private val loaded = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        val loads = mutableListOf<String>()
        val shows = mutableListOf<Pair<String, Int>>()
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_full2_data"
        override fun getKeyForResDf(): String = "test_full2_res"
        override fun getAdsForPl(placement: String): AdsPlBase = loaded
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement to helper.flagAdsFullShowing.get()
            showing = placement to adsShowCb
            adsShowCb.onAdShowed(placement, placement, "t", flagShow)
            return true
        }
        fun dismiss() = showing!!.let { (pl, cb) -> cb.onAdDismissed(pl, pl, "t") }
    }

    private class AppGiftCb : AdsGiftShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "fail:$err" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed:$placement" }
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { events += "impression" }
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) { events += "click" }
        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) { events += "rewarded" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed" }
        fun count(prefix: String) = events.count { it.startsWith(prefix) }
    }

    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded:$placement" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private class Fixture(
        val helper: AdsHelper,
        val net1: GiftNetwork,
        val net2: GiftNetwork,
        val full2: Full2Network,
        val timers: MainHandlerTimers,
    ) {
        fun plcf(placement: String) = helper.getAdsPlacementConfig(placement, 1)!!
        /** Placement mọi lời gọi `loadAd` rewarded (hai mạng): mạng đầu của lượt load kế theo `giftIdxShow` đã xoay. */
        fun giftLoads() = net1.loadPlacements() + net2.loadPlacements()
        fun transitionActive(): Boolean = field<Boolean>(helper, "full2TransitionActive")
        fun policy(): FullScreenAdPolicy = field(helper, "fullScreenAdPolicy")
    }

    /**
     * `AdsHelper` với waterfall rewarded 2 mạng (AdMob → MAX, `giftStepCicle`), Full2 = native full AdMob, host đã
     * resume (Full2 hiện ngay, không xếp chờ), `full2FlagShow` theo [full2FlagShow]. `clearActivity` không được gọi ở
     * đây, nhưng handler ảo vẫn chạy `honorRemoveAll = true` như các test đặc tả khác của 1.5.0.
     */
    private fun withGift(full2FlagShow: Int = 0, check: Fixture.() -> Unit) = MainHandlerTimers.run(honorRemoveAll = true) { timers ->
        val context = networkContext()
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            this.full2FlagShow = full2FlagShow
            giftStepCicle.clear()
            giftStepCicle.addAll(listOf(AdsIDDef.AdmobRewarded, AdsIDDef.MaxRewarded))
            giftStepRe.clear()
            full2StepCicle.clear()
            full2StepCicle.add(AdsIDDef.AdmobFullNt)
            full2StepRe.clear()
        }
        val net1 = GiftNetwork(context, AdsIDDef.AdmobRewarded)
        val net2 = GiftNetwork(context, AdsIDDef.MaxRewarded)
        val full2 = Full2Network(context, helper)
        setField(helper, "listAdsGift", mutableMapOf<Int, AdsBaseGift>(AdsIDDef.AdmobRewarded to net1, AdsIDDef.MaxRewarded to net2))
        setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to full2))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>())
        field<FullScreenAdPolicy>(helper, "fullScreenAdPolicy").onHostActivityResumed()
        Fixture(helper, net1, net2, full2, timers).check()
    }

    private fun networkContext(): Context {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        return context
    }

    private fun nowSec() = System.currentTimeMillis() / 1000

    // ═══ Brief (1): waterfall — mạng 1 lỗi đồng bộ, mạng 2 hiện ═══

    @Test fun `mang 1 loi dong bo tra false, mang 2 hien - app 0 onAdShowFail, 1 onAdShowed, dismiss mot lan`() = withGift {
        net1.failSync = true
        val cb = AppGiftCb()

        assertTrue(helper.showRewarded("x", cb))

        assertEquals(listOf("gift_x"), net1.shows)
        assertEquals(listOf("gift_x"), net2.shows)
        assertEquals("lỗi giữa waterfall không tới app", listOf("showed:gift_x"), cb.events)
        assertEquals(1, helper.flagAdsFullShowing.get())
        assertTrue(plcf("gift_x").isShowing)
        assertEquals("lỗi giữ lại không nạp lại gift_default", emptyList<String>(), net1.loadPlacements())

        net2.dismiss()
        assertEquals(listOf("showed:gift_x", "dismissed"), cb.events)
        assertEquals(0, cb.count("fail"))
        assertEquals(0, helper.flagAdsFullShowing.get())
        assertFalse(plcf("gift_x").isShowing)
    }

    // ═══ Brief (2): không mạng nào hiện ═══

    @Test fun `khong mang nao hien - dung mot onAdShowFail cuoi vong, isLoaded false, nap lai goi mang 1 lan`() = withGift {
        net1.failSync = true
        net2.failSync = true
        val cb = AppGiftCb()
        plcf(PlacementDefault.PLGiftDefault).isLoaded = true // cờ đã load cũ phải được hạ

        assertFalse(helper.showRewarded("default", cb))

        assertEquals(listOf(PlacementDefault.PLGiftDefault), net1.shows)
        assertEquals(listOf(PlacementDefault.PLGiftDefault), net2.shows)
        assertEquals("đúng một fail, lỗi đầu tiên (WaterfallShowFailHold)", listOf("fail:sync fail"), cb.events)
        assertFalse(plcf(PlacementDefault.PLGiftDefault).isLoaded)
        assertFalse(plcf(PlacementDefault.PLGiftDefault).isShowing)
        assertEquals(0, helper.flagAdsFullShowing.get())
        timers.runPosted()
        assertEquals("nạp lại: mạng 1 đúng một lần", listOf(PlacementDefault.PLGiftDefault), net1.loadPlacements())
        assertEquals(0, net2.loads.size)
    }

    @Test fun `khong mang nao hien, placement rieng - fail mot lan, nap lai gift_x mot lan (callback), lan goi cua showRewarded gan vao luot`() = withGift {
        net1.failSync = true
        net2.failSync = true
        val cb = AppGiftCb()

        assertFalse(helper.showRewarded("x", cb))

        assertEquals(listOf("fail:sync fail"), cb.events)
        assertEquals("2.4.0 CARRY-ADS #8: nạp lại placement vừa hiện", listOf("gift_x"), net1.loadPlacements())
        assertTrue(plcf("gift_x").isLoading)
        assertFalse(plcf(PlacementDefault.PLGiftDefault).isLoading)
    }

    // ═══ Brief (3): phần thưởng, dismiss, chuỗi Full2 ═══

    @Test fun `onAdRewarded toi app dung mot lan - dismiss Full2 flag 2 goi showFull2 gift_x voi cb cua app, co full-screen ve 0, nap lai gift_x`() = withGift(full2FlagShow = 2) {
        val cb = AppGiftCb()
        assertTrue(helper.showRewarded("x", cb))
        assertTrue("full2FlagShow 2: hiện rewarded bật chuyển cảnh", transitionActive())
        assertEquals("beginFull2Transition nạp Full2 từ lúc cha hiện", listOf("full_2_x"), full2.loads)

        net1.reward()
        assertEquals(listOf("showed:gift_x", "rewarded"), cb.events)

        helper.giftTimeShow = 0
        net1.dismiss()

        assertEquals("showFull2(\"gift_x\") → full_2_x, khi cờ full-screen của rewarded đã về 0", listOf("full_2_x" to 0), full2.shows)
        assertEquals(
            "app nhận dismiss của rewarded, rồi showed của Full2 qua chính cb đó",
            listOf("showed:gift_x", "rewarded", "dismissed", "showed:full_2_x"),
            cb.events
        )
        assertEquals(1, cb.count("rewarded"))
        assertTrue("giftTimeShow đặt lúc dismiss", nowSec() - helper.giftTimeShow <= 1)
        assertFalse(plcf("gift_x").isShowing)
        assertEquals("nạp lại gift_x (placement vừa hiện) sau dismiss", listOf("gift_x"), giftLoads())

        full2.dismiss()
        assertEquals("một lượt show: đúng một onAdDismissed", 1, cb.count("dismissed"))
        assertEquals(0, helper.flagAdsFullShowing.get())
    }

    @Test fun `dismiss Full2 flag 0 - khong showFull2, chuyen canh ket thuc (dang tat, khong nem), nap lai gift_x`() = withGift(full2FlagShow = 0) {
        val cb = AppGiftCb()
        assertTrue(helper.showRewarded("x", cb))
        assertFalse("full2FlagShow 0: không bật chuyển cảnh", transitionActive())
        assertEquals(emptyList<String>(), full2.loads)

        net1.reward()
        net1.dismiss()

        assertEquals(emptyList<Pair<String, Int>>(), full2.shows)
        assertEquals(listOf("showed:gift_x", "rewarded", "dismissed"), cb.events)
        assertFalse(transitionActive())
        assertEquals(0, helper.flagAdsFullShowing.get())
        assertEquals(listOf("gift_x"), giftLoads())
    }

    @Test fun `chuyen canh Full2 dang bat, full2FlagShow 0 - dismiss rewarded ket thuc chuyen canh ngay`() = withGift(full2FlagShow = 0) {
        setField(helper, "full2TransitionActive", true)
        assertTrue(helper.showRewarded("x", AppGiftCb()))
        net1.dismiss()
        assertFalse("finishFull2Transition ở nhánh không nối", transitionActive())
        assertEquals(emptyList<Pair<String, Int>>(), full2.shows)
    }

    @Test fun `full2FlagShow 3 cung noi Full2 sau rewarded`() = withGift(full2FlagShow = 3) {
        val cb = AppGiftCb()
        assertTrue(helper.showRewarded("x", cb))
        assertTrue(transitionActive())
        net1.dismiss()
        assertEquals(listOf("full_2_x" to 0), full2.shows)
        assertEquals(1, cb.count("dismissed"))
    }

    // ═══ Giao thức một callback kết thúc ═══

    @Test fun `dismiss ba lan - chi mot onAdDismissed, co full-screen 0, reward khong lap`() = withGift {
        val cb = AppGiftCb()
        assertTrue(helper.showRewarded("x", cb))
        assertTrue(helper.isFullShowing())
        assertTrue("giftTimeShow đặt lúc hiện", nowSec() - helper.giftTimeShow <= 1)

        net1.reward()
        net1.dismiss()
        net1.dismiss()
        net1.dismiss()

        assertEquals(listOf("showed:gift_x", "rewarded", "dismissed"), cb.events)
        assertFalse(helper.isFullShowing())
        assertEquals(0, helper.flagAdsFullShowing.get())
    }

    @Test fun `loi bat dong bo sau khi da hien - app nhan dung mot onAdShowFail, co full-screen 0, nap lai gift_x`() = withGift {
        val cb = AppGiftCb()
        assertTrue(helper.showRewarded("x", cb))

        // Mạng báo lỗi lặp: giao thức flagAdsFull4ShowFaild (set(-2) → +1 khi hiện → +1 mỗi lỗi, set(-10) khi báo)
        // chỉ cho một onAdShowFail tới app.
        net1.failAfterShown()
        net1.failAfterShown()
        net1.failAfterShown()

        assertEquals(listOf("showed:gift_x", "fail:present fail"), cb.events)
        assertEquals(0, helper.flagAdsFullShowing.get())
        assertFalse(plcf("gift_x").isShowing)
        assertEquals("mỗi lần báo lỗi nạp lại gift_x (lần sau đang load)", listOf("gift_x"), giftLoads())
    }

    @Test fun `click - app nhan onAdClick, onAdClicked chan quang cao tu dong khi quay lai, impression chuyen tiep`() = withGift {
        val cb = AppGiftCb()
        assertTrue(helper.showRewarded("x", cb))
        assertFalse(policy().shouldSuppressAutomaticFullscreen())

        net1.impression()
        net1.click()

        assertEquals(listOf("showed:gift_x", "impression", "click"), cb.events)
        assertTrue("h.onAdClicked(): cửa sổ chặn sau click", policy().shouldSuppressAutomaticFullscreen())
    }

    // ═══ Cổng show ═══

    @Test fun `cong show - dang co full-screen, giftDeltaTime, dang hien - false, khong goi mang, khong callback`() = withGift {
        val cb = AppGiftCb()

        helper.flagAdsFullShowing.set(1)
        assertFalse(helper.showRewarded("x", cb))
        helper.flagAdsFullShowing.set(-3)
        helper.giftTimeShow = nowSec()
        helper.currConfig.giftDeltaTime = 30
        assertFalse("giftDeltaTime chặn", helper.showRewarded("x", cb))
        assertEquals("cờ âm được kẹp về 0", 0, helper.flagAdsFullShowing.get())

        helper.giftTimeShow = 0
        plcf("gift_x").isShowing = true
        assertFalse("placement đang hiện", helper.showRewarded("x", cb))

        assertEquals(emptyList<String>(), net1.shows + net2.shows)
        assertEquals(emptyList<String>(), cb.events)
        assertEquals(emptyList<String>(), net1.loadPlacements())
    }

    @Test fun `placement tat tu xa flagShow -1 - showRewarded false khong goi mang khong callback, load bao cf not ngay khong goi mang (O7)`() = withGift {
        plcf("gift_x").flagShow = -1
        val cb = AppGiftCb()

        assertFalse(helper.showRewarded("x", cb))
        assertEquals("cổng cf not của showRewarded: không mạng nào showAd", emptyList<String>(), net1.shows + net2.shows)
        assertEquals(emptyList<String>(), cb.events)
        assertEquals(0, helper.flagAdsFullShowing.get())

        val loadCb = AppLoadCb()
        helper.loadRewardedForThisTurn("x", loadCb)
        timers.runPosted()
        assertEquals("cổng cf not của loadRewardedForThisTurn: không gọi mạng", emptyList<String>(), giftLoads())
        assertEquals("cổng cf not báo onLoadFailed ngay, đúng một lần (O7 (a), 1.6.0 Task 5)", listOf("fail:cf not"), loadCb.events)
        assertFalse(plcf("gift_x").isLoading)
    }

    @Test fun `cooldown man hinh - rewarded khong bi chan nhung bat cooldown cho quang cao tu dong ke tiep`() = withGift {
        helper.setPostAdScreenCooldown(1)
        assertTrue(helper.showRewarded("x", AppGiftCb()))
        assertTrue("markAutoAdShown", helper.isPostAdScreenCooldownActive())
        net1.dismiss()

        helper.giftTimeShow = 0
        assertTrue("rewarded (người dùng bấm) không bị cooldown chặn", helper.showRewarded("x", AppGiftCb()))
    }

    @Test fun `vong show xoay giftIdxShow - lan sau bat dau tu mang ke, giftCount4Circle dem so mang da thu`() = withGift {
        helper.fullCount4Circle = 5
        assertTrue(helper.showRewarded("x", AppGiftCb()))
        assertEquals(listOf("gift_x"), net1.shows)
        assertEquals(1, helper.giftIdxShow)
        assertEquals(1, helper.giftCount4Circle)
        assertEquals("fullCount4Circle đặt 0 ở mỗi lượt", 0, helper.fullCount4Circle)
        net1.dismiss()

        helper.giftTimeShow = 0
        assertTrue(helper.showRewarded("x", AppGiftCb()))
        assertEquals("lượt 2 bắt đầu ở mạng 2", listOf("gift_x"), net2.shows)
        assertEquals(0, helper.giftIdxShow)
        assertEquals(2, helper.giftCount4Circle)
    }

    // ═══ Vòng load ═══

    @Test fun `load - mang 1 loi, buoc ke qua Handler post, mang 2 load duoc - cb onLoaded mot lan, dang load thi cb2 gan vao luot`() = withGift {
        val cb = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cb)
        assertEquals(listOf("gift_x"), net1.loadPlacements())
        assertTrue(plcf("gift_x").isLoading)

        val cb2 = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cb2)
        assertEquals("đang load: không gọi mạng thêm", 1, net1.loads.size)
        assertEquals("đang load: chưa callback (gắn vào lượt đang chạy, O7 (a))", emptyList<String>(), cb2.events)

        net1.loads[0].second.onLoadFailed("gift_x", "gift_x", "id", "no fill")
        assertEquals("bước kế đi qua Handler.post", 0, net2.loads.size)
        timers.runPosted()
        assertEquals(listOf("gift_x"), net2.loadPlacements())
        net2.loads[0].second.onLoaded("gift_x", "gift_x", "id", null, null)

        assertEquals(listOf("loaded:gift_x"), cb.events)
        assertEquals("cb2 (gọi lúc đang load) nhận kết quả của lượt đúng một lần", listOf("loaded:gift_x"), cb2.events)
        assertTrue(plcf("gift_x").isLoaded)
        assertFalse(plcf("gift_x").isLoading)
        assertEquals(emptyList<String>(), full2.loads)
    }

    /**
     * Review Task 9 m2 (1.5.0 Task 10 bước A): `cbLoad` gán TRƯỚC khi chạy vòng. Lối tắt "has loaded" của `AdsPlBase.load`
     * báo `onLoaded` đồng bộ ngay trong `loadAd`; gán sau vòng thì callback của lần gọi này không bao giờ tới, còn
     * callback của lần gọi trước nhận `onLoaded` lần hai.
     */
    @Test fun `load - mang bao onLoaded dong bo trong loadAd - callback cua lan goi nay nhan dung mot lan, lan truoc khong nhan them`() = withGift {
        val cb1 = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cb1)
        net1.loads.single().second.onLoaded("gift_x", "gift_x", "id", null, null)
        assertEquals(listOf("loaded:gift_x"), cb1.events)
        assertFalse(plcf("gift_x").isLoading)

        val sync = GiftNetwork(networkContext(), AdsIDDef.AdmobRewarded).apply { loadSync = true }
        setField(helper, "listAdsGift", mutableMapOf<Int, AdsBaseGift>(AdsIDDef.AdmobRewarded to sync))
        val cb2 = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cb2)

        assertEquals(listOf("gift_x"), sync.loadPlacements())
        assertEquals(listOf("loaded:gift_x"), cb2.events)
        assertEquals("lần gọi trước không nhận thêm", listOf("loaded:gift_x"), cb1.events)
        assertTrue(plcf("gift_x").isLoaded)
        assertFalse(plcf("gift_x").isLoading)
    }

    @Test fun `load - moi mang loi thi onLoadFailed list empty mot lan, luot cu khong toi app ca khi luot moi da xong`() = withGift {
        val cb = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cb)
        net1.loads[0].second.onLoadFailed("gift_x", "gift_x", "id", "no fill")
        timers.runPosted()
        net2.loads[0].second.onLoadFailed("gift_x", "gift_x", "id", "no fill")
        timers.runPosted()
        assertEquals(listOf("fail:list empty"), cb.events)
        assertFalse(plcf("gift_x").isLoading)

        // Lượt mới đang load: callback muộn của lượt cũ (loadCycle đã tăng) không tới app, không bật isLoaded.
        val cb2 = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cb2)
        net1.loads[0].second.onLoaded("gift_x", "gift_x", "id", null, null)
        net1.loads[0].second.onLoadFailed("gift_x", "gift_x", "id", "late")
        timers.runPosted()
        assertEquals("lượt cũ (loadCycle) không tới app", emptyList<String>(), cb2.events)
        assertFalse(plcf("gift_x").isLoaded)
        assertEquals("lượt cũ không xếp bước kế", 1, net2.loads.size)
        assertEquals(listOf("fail:list empty"), cb.events)

        // Lượt mới đã kết thúc (isLoading hạ): lỗi muộn của lượt cũ vẫn không tới app. Guard cycle của inner
        // onLoadFailed chỉ lộ ra ở đây — khi isLoading còn bật, guard đầu loadRewardedCircle che nó.
        net1.loads[1].second.onLoaded("gift_x", "gift_x", "id", null, null)
        assertEquals(listOf("loaded:gift_x"), cb2.events)
        net1.loads[0].second.onLoadFailed("gift_x", "gift_x", "id", "late")
        net2.loads[0].second.onLoadFailed("gift_x", "gift_x", "id", "late")
        timers.runPosted()
        assertEquals(listOf("loaded:gift_x"), cb2.events)
        assertTrue(plcf("gift_x").isLoaded)
        assertEquals(listOf("fail:list empty"), cb.events)
    }

    @Test fun `load - loi sau khi luot da ha isLoading thi bao isLoading=false on fail`() = withGift {
        val cb = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cb)
        val cycle = plcf("gift_x").loadCycle
        // Hạ cờ không tăng loadCycle (setter chỉ tăng khi bật).
        plcf("gift_x").isLoading = false
        assertEquals(cycle, plcf("gift_x").loadCycle)
        net1.loads[0].second.onLoadFailed("gift_x", "gift_x", "id", "no fill")
        timers.runPosted()
        assertEquals(listOf("fail:loadRewardedCircle isLoading=false on fail"), cb.events)
        assertEquals(0, net2.loads.size)
    }

    @Test fun `load voi full2FlagShow 2 hoac 3 - nap kem Full2 cua placement, flag 0 thi khong`() {
        withGift(full2FlagShow = 2) {
            helper.loadRewardedForThisTurn("x")
            assertEquals(listOf("full_2_x"), full2.loads)
        }
        withGift(full2FlagShow = 3) {
            helper.loadRewardedForThisTurn("x")
            assertEquals(listOf("full_2_x"), full2.loads)
        }
        withGift(full2FlagShow = 1) {
            helper.loadRewardedForThisTurn("x")
            assertEquals(emptyList<String>(), full2.loads)
        }
    }

    private companion object {
        fun setField(helper: AdsHelper, name: String, value: Any?) {
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> field(helper: AdsHelper, name: String): T =
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper) as T
    }
}
