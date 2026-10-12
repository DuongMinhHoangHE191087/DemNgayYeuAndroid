package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.app.AppPluginManager
import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.DecisionLogger
import com.app.plugin.brain.DecisionPoint
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.LicenseTestRule
import com.app.plugin.brain.RewardModel
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants.AdShowFailReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Test đặc tả phần load/ready của interstitial trong [AdsHelper] (plan 1.5.0 Task 10): viết và chạy xanh trên mã CŨ
 * trước khi chuyển sang `ads.core.InterstitialController`, làm lưới cho những dòng mà full suite 1264 test không chặn
 * (đột biến sống, `DOC/testing/t150-t10-mutation.log`): callback kết thúc của cổng `cf not`, "đang load" và "not met
 * condition"; ba guard `loadCycle` của vòng load (đầu vòng, inner `onLoaded`, inner `onLoadFailed`); cờ `isLoadFull2`;
 * hai cổng tức thời của `isInterstitialReadyToShow` (host resume, cooldown N màn). Mọi lời gọi đi qua hàm public của
 * façade; field R1 (`listAdsFull`, `listAdsAll`, `mActivity`, `fullScreenAdPolicy`) gán bằng reflection như các test
 * sẵn có. Bước kế của waterfall đi qua `Handler.post` → handler ảo của [MainHandlerTimers].
 *
 * Task 11 bước A (review Task 10 I1: 17 đột biến sống qua 1273 test) thêm: đuôi vòng load (mọi mạng thất bại → đúng một
 * `list empty`, `isLoading` hạ; nhánh `isLoading=false on fail`), thứ tự mạng (`fullIdxShow`, `fullStepRe`,
 * `flagImage` 4), nạp kèm Full2 với `full2FlagShow` 3, sáu cổng còn lại của `isInterstitialReadyToShow` và `flagImage`
 * của lần nạp lại khi cờ đã load là cũ.
 *
 * Task 12 bước A (review Task 11 I1–I3: 10 đột biến sống qua 1298 test) thêm, trước khi Task 12 chuyển nửa Full2 của giao
 * thức một-dismiss: sentinel `set(-10)` của dismiss/show fail (chuỗi interstitial → Full2: Full2 đóng không tới app lần
 * hai; dismiss/show fail lặp từ mạng), `isShowing` hạ ở show fail bất đồng bộ, thứ tự dismiss cha → `showFull2`,
 * chuyển cảnh giữ tới impression của Full2, cổng policy của `beginFull2Transition`, thứ tự brain sau cổng
 * `flagAdsFullShowing`/cooldown, `isDeltaTime` của cooldown, và nhánh brain từ chối không gọi `onAdSkipped`.
 */
class InterstitialControllerTest {
    @get:Rule val license = LicenseTestRule()

    /** Mạng interstitial ghi mỗi lần `loadAd` (placement + callback), không tự báo gì. */
    private class LoadNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_inters"
        val loads = mutableListOf<Pair<String, AdsLoadInnerCb>>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_inters_${adsType}_data"
        override fun getKeyForResDf(): String = "test_inters_${adsType}_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement to adsLoadCb }
        fun cb(i: Int) = loads[i].second
        fun loadPlacements() = loads.map { it.first }
    }

    /** Mạng có quảng cáo sẵn ([hasLoadedAd] đúng), không đang tải. */
    private class ReadyNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_ready"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        val loads = mutableListOf<String>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_ready_${adsType}_data"
        override fun getKeyForResDf(): String = "test_ready_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement }
        val shows = mutableListOf<String>()
        /** `true`: `showAd` báo `onAdShowFail` đồng bộ rồi trả `false` (như SDK lỗi ngay). */
        var failSync = false
        private var showing: Pair<String, AdsShowCb>? = null
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            if (failSync) {
                adsShowCb.onAdShowFail(placement, placement, "test", "sync fail")
                return false
            }
            shows += placement
            showing = placement to adsShowCb
            return true
        }
        fun click() = showing!!.let { (pl, cb) -> cb.onAdClick(pl, pl, "test") }
        fun dismiss() = showing!!.let { (pl, cb) -> showing = null; cb.onAdDismissed(pl, pl, "test") }
    }

    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded:$originPl" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private class Fixture(
        val helper: AdsHelper,
        val net1: LoadNetwork,
        val net2: LoadNetwork,
        val full2: LoadNetwork,
        val low: LoadNetwork,
        val timers: MainHandlerTimers,
    ) {
        fun plcf(placement: String) = helper.getAdsPlacementConfig(placement, 1)!!
    }

    /**
     * `AdsHelper` với waterfall interstitial theo [fullStepCicle] (mặc định 2 mạng AdMob → MAX), `fullStepRe` theo [fullStepRe],
     * Full2 = native full AdMob (`full2StepCicle`), `full2FlagShow` theo [full2FlagShow]; thêm mạng AdMob low
     * ([Fixture.low], chỉ vào danh sách khi `flagImage` 2–5 hoặc có trong [fullStepRe]). Không mạng nào tự báo: test
     * gọi callback của từng lần `loadAd`.
     */
    private fun withInters(
        full2FlagShow: Int = 0,
        fullStepCicle: List<Int> = listOf(AdsIDDef.AdmobFullInters, AdsIDDef.MaxFullInters),
        fullStepRe: List<Int> = emptyList(),
        check: Fixture.() -> Unit,
    ) = MainHandlerTimers.run(honorRemoveAll = true) { timers ->
        val context = networkContext()
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            this.full2FlagShow = full2FlagShow
            this.fullStepCicle.clear()
            this.fullStepCicle.addAll(fullStepCicle)
            this.fullStepRe.clear()
            this.fullStepRe.addAll(fullStepRe)
            full2StepCicle.clear()
            full2StepCicle.add(AdsIDDef.AdmobFullNt)
            full2StepRe.clear()
        }
        val net1 = LoadNetwork(context, AdsIDDef.AdmobFullInters)
        val net2 = LoadNetwork(context, AdsIDDef.MaxFullInters)
        val full2 = LoadNetwork(context, AdsIDDef.AdmobFullNt)
        val low = LoadNetwork(context, AdsIDDef.AdmobFullLow)
        setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(
            AdsIDDef.AdmobFullInters to net1, AdsIDDef.MaxFullInters to net2, AdsIDDef.AdmobFullNt to full2,
            AdsIDDef.AdmobFullLow to low,
        ))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>())
        Fixture(helper, net1, net2, full2, low, timers).check()
    }

    private fun networkContext(): Context {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        return context
    }

    // ═══ Cổng load: mỗi request đúng một callback kết thúc ═══

    @Test fun `load - placement tat tu xa flagShow -1 - onLoadFailed cf not dung mot lan, khong goi mang`() = withInters {
        plcf("full_x").flagShow = -1
        val cb = AppLoadCb()

        helper.loadInterstitialForThisTurn("x", 0, cb)

        assertEquals(listOf("fail:cf not"), cb.events)
        assertEquals(emptyList<String>(), net1.loadPlacements() + net2.loadPlacements())
        assertFalse(plcf("full_x").isLoading)
    }

    @Test fun `load - dang load - lan goi thu hai gan vao luot (2_4_0 CARRY-ADS), khong goi mang them, chua ai nhan gi`() = withInters {
        val cb1 = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb1)
        assertEquals(listOf("full_x"), net1.loadPlacements())

        val cb2 = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb2)

        assertEquals("gắn vào lượt: chưa callback", emptyList<String>(), cb2.events)
        assertEquals("đang load: không gọi mạng thêm", 1, net1.loads.size)
        assertEquals(emptyList<String>(), cb1.events)
        assertTrue(plcf("full_x").isLoading)
    }

    @Test fun `load - chua qua mot phan ba fullStartTime tu luc cai app - onLoadFailed not met condition, khong goi mang`() = withInters {
        // isIntersAllowLoadAds: (bây giờ − tAppStart) ≤ fullStartTime / 3 và fullStartTime ≥ 120 → chặn load.
        val appStart = AppPluginManager::class.java.getDeclaredField("tAppStart").apply { isAccessible = true }
        val saved = appStart.get(AppPluginManager.instance)
        appStart.set(AppPluginManager.instance, System.currentTimeMillis() / 1000)
        try {
            helper.currConfig.fullStartTime = 300
            val cb = AppLoadCb()

            helper.loadInterstitialForThisTurn("x", 0, cb)

            assertEquals(listOf("fail:loadInterstitialForThisTurn not met condition"), cb.events)
            assertEquals(emptyList<String>(), net1.loadPlacements() + net2.loadPlacements())
            assertFalse(plcf("full_x").isLoading)
        } finally {
            appStart.set(AppPluginManager.instance, saved)
        }
    }

    // ═══ Guard loadCycle của vòng load: lượt cũ không chạm lượt mới ═══

    /** Hạ `isLoading` không tăng `loadCycle` (setter chỉ tăng khi bật): lượt 1 "kết thúc" mà vòng của nó vẫn còn. */
    private fun Fixture.endTurnWithoutCycleBump(placement: String) {
        val cycle = plcf(placement).loadCycle
        plcf(placement).isLoading = false
        assertEquals(cycle, plcf(placement).loadCycle)
    }

    @Test fun `vong load - buoc ke cua luot cu chay sau khi luot moi bat dau - bi bo o dau vong, khong goi mang sau`() = withInters {
        val cb1 = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb1)
        net1.cb(0).onLoadFailed("full_x", "full_x", "id", "no fill")
        assertEquals("bước kế của lượt 1 đi qua Handler.post, chưa chạy", 0, net2.loads.size)

        endTurnWithoutCycleBump("full_x")
        val cb2 = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb2)
        assertEquals("lượt 2 bắt đầu ở mạng 1", 2, net1.loads.size)

        timers.runPosted()

        assertEquals("bước kế của lượt 1 bị bỏ (guard đầu loadInterstitialCircle)", 0, net2.loads.size)
        assertEquals(emptyList<String>(), cb1.events)
        assertEquals(emptyList<String>(), cb2.events)
        assertTrue(plcf("full_x").isLoading)
    }

    @Test fun `vong load - onLoaded muon cua luot cu khi luot moi dang load - khong toi app, khong ha isLoading, khong bat isLoaded`() = withInters {
        val cb1 = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb1)
        endTurnWithoutCycleBump("full_x")
        val cb2 = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb2)

        net1.cb(0).onLoaded("full_x", "full_x", "id", null, null)

        assertEquals(emptyList<String>(), cb1.events)
        assertEquals(emptyList<String>(), cb2.events)
        assertTrue("lượt 2 vẫn đang load", plcf("full_x").isLoading)
        assertFalse(plcf("full_x").isLoaded)

        net1.cb(1).onLoaded("full_x", "full_x", "id", null, null)
        assertEquals(listOf("loaded:full_x"), cb2.events)
        assertEquals(emptyList<String>(), cb1.events)
    }

    @Test fun `vong load - onLoadFailed muon cua luot cu sau khi luot moi da xong - khong toi app lan hai, khong xep buoc ke`() = withInters {
        val cb1 = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb1)
        endTurnWithoutCycleBump("full_x")
        val cb2 = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb2)
        net1.cb(1).onLoaded("full_x", "full_x", "id", null, null)
        assertEquals(listOf("loaded:full_x"), cb2.events)
        assertFalse(plcf("full_x").isLoading)

        net1.cb(0).onLoadFailed("full_x", "full_x", "id", "late")
        timers.runPosted()

        assertEquals("lượt 2 đúng một callback kết thúc", listOf("loaded:full_x"), cb2.events)
        assertEquals(emptyList<String>(), cb1.events)
        assertEquals(0, net2.loads.size)
        assertTrue(plcf("full_x").isLoaded)
    }

    // ═══ Nạp kèm Full2 ═══

    @Test fun `load voi full2FlagShow 1 - nap kem Full2 cua placement, isLoadFull2 false hoac flagImg duong thi khong`() {
        withInters(full2FlagShow = 1) {
            helper.loadInterstitialForThisTurn("x", 0)
            assertEquals(listOf("full_2_x"), full2.loadPlacements())
        }
        withInters(full2FlagShow = 1) {
            helper.loadInterstitialForThisTurn("x", 0, null, false)
            assertEquals("isLoadFull2 = false", emptyList<String>(), full2.loadPlacements())
            assertEquals(listOf("full_x"), net1.loadPlacements())
        }
        withInters(full2FlagShow = 3) {
            helper.loadInterstitialForThisTurn("x", 1)
            assertEquals("flagImg 1: waterfall native full nạp full_x, không nạp Full2", listOf("full_x"), full2.loadPlacements())
        }
    }

    // ═══ Đuôi vòng load, danh sách mạng (1.5.0 Task 11 bước A, review Task 10 I1) ═══

    @Test fun `vong load - moi mang bao that bai - app nhan dung mot fail list empty, ha isLoading, lan load ke goi lai mang 1`() = withInters {
        val cb = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb)
        net1.cb(0).onLoadFailed("full_x", "full_x", "id", "no fill")
        timers.runPosted()
        assertEquals("mạng 2 là bước kế", 1, net2.loads.size)
        net2.cb(0).onLoadFailed("full_x", "full_x", "id", "no fill")
        timers.runPosted()

        assertEquals(listOf("fail:list empty"), cb.events)
        assertFalse(plcf("full_x").isLoading)
        assertFalse(plcf("full_x").isLoaded)

        val cb2 = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb2)
        assertEquals("lượt kế bắt đầu lại ở mạng 1, không bị chặn 'loading'", 2, net1.loads.size)
        assertEquals(emptyList<String>(), cb2.events)
        assertEquals("lượt 1 không nhận thêm", listOf("fail:list empty"), cb.events)
    }

    @Test fun `vong load - mang bao that bai khi isLoading da ha cung chu ky - app nhan fail isLoading=false on fail, khong xep buoc ke`() = withInters {
        val cb = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb)
        endTurnWithoutCycleBump("full_x")

        net1.cb(0).onLoadFailed("full_x", "full_x", "id", "no fill")
        timers.runPosted()

        assertEquals(listOf("fail:loadInterstitialCircle isLoading=false on fail"), cb.events)
        assertEquals(0, net2.loads.size)
    }

    @Test fun `danh sach mang - fullIdxShow 1 - mang 2 cua fullStepCicle duoc load truoc mang 1`() = withInters {
        helper.fullIdxShow = 1
        helper.loadInterstitialForThisTurn("x", 0)
        assertEquals(listOf("full_x"), net2.loadPlacements())
        assertEquals(emptyList<String>(), net1.loadPlacements())

        net2.cb(0).onLoadFailed("full_x", "full_x", "id", "no fill")
        timers.runPosted()
        assertEquals(listOf("full_x"), net1.loadPlacements())
    }

    @Test fun `danh sach mang - fullStepRe 1 mang - duoc load sau het fullStepCicle`() = withInters(fullStepRe = listOf(AdsIDDef.AdmobFullLow)) {
        val cb = AppLoadCb()
        helper.loadInterstitialForThisTurn("x", 0, cb)
        net1.cb(0).onLoadFailed("full_x", "full_x", "id", "no fill")
        timers.runPosted()
        assertEquals("chưa hết fullStepCicle", emptyList<String>(), low.loadPlacements())
        net2.cb(0).onLoadFailed("full_x", "full_x", "id", "no fill")
        timers.runPosted()

        assertEquals(listOf("full_x"), low.loadPlacements())
        assertEquals(emptyList<String>(), cb.events)
        low.cb(0).onLoaded("full_x", "full_x", "id", null, null)
        assertEquals(listOf("loaded:full_x"), cb.events)
    }

    @Test fun `danh sach mang - flagImg 4 - AdmobFullLow load truoc native full`() = withInters {
        helper.loadInterstitialForThisTurn("x", 4)
        assertEquals(listOf("full_x"), low.loadPlacements())
        assertEquals(emptyList<String>(), full2.loadPlacements())

        low.cb(0).onLoadFailed("full_x", "full_x", "id", "no fill")
        timers.runPosted()
        assertEquals(listOf("full_x"), full2.loadPlacements())
        assertEquals("flagImg 4 không dùng waterfall interstitial", emptyList<String>(), net1.loadPlacements() + net2.loadPlacements())
    }

    @Test fun `danh sach mang - type khong co mang - buoc ke di qua Handler post, khong goi dong bo`() =
        withInters(fullStepCicle = listOf(AdsIDDef.FBFullInters, AdsIDDef.AdmobFullInters)) {
            helper.loadInterstitialForThisTurn("x", 0)
            assertEquals("FB không có trong listAdsFull: bước kế chưa chạy", emptyList<String>(), net1.loadPlacements())

            timers.runPosted()
            assertEquals(listOf("full_x"), net1.loadPlacements())
        }

    @Test fun `load - nguoi dung da go quang cao toan man hinh - onLoadFailed dung mot lan, khong goi mang`() = withInters {
        helper.setRemoveAdsMask(1 shl RemoveAdsMask.FLAG_FULLSCREEN)
        val cb = AppLoadCb()

        helper.loadInterstitialForThisTurn("x", 0, cb)

        assertEquals(listOf("fail:loadInterstitialForThisTurn isremoveads or disable"), cb.events)
        assertEquals(emptyList<String>(), net1.loadPlacements() + net2.loadPlacements())
        assertFalse(plcf("full_x").isLoading)
    }

    @Test fun `isInterstitialLoaded - nhan ca placement co va khong co tien to full_`() = withInters {
        assertFalse(helper.isInterstitialLoaded("x"))
        plcf("full_x").isLoaded = true

        assertTrue(helper.isInterstitialLoaded("x"))
        assertTrue(helper.isInterstitialLoaded("full_x"))
    }

    @Test fun `load voi full2FlagShow 3 va flagImg 0 - nap kem Full2 cua placement`() = withInters(full2FlagShow = 3) {
        helper.loadInterstitialForThisTurn("x", 0)
        assertEquals(listOf("full_2_x"), full2.loadPlacements())
        assertEquals(listOf("full_x"), net1.loadPlacements())
    }

    // ═══ isInterstitialReadyToShow: cổng tức thời ═══

    /**
     * Host resume, Activity sống, `full_home` đã load, mạng AdMob interstitial có quảng cáo. Phiên 2 như
     * `AdsPolicyWiringTest` (luật tần suất mặc định không chặn); khôi phục phiên sau khi chạy.
     */
    private fun withReady(check: (AdsHelper, ReadyNetwork) -> Unit) {
        val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
        val savedSession = sessionField.get(AppPluginManager.instance) as Int
        sessionField.set(AppPluginManager.instance, 2)
        try {
            MainHandlerTimers.run(honorRemoveAll = true) { _ ->
                val helper = AdsKillSwitchTest.newAdsHelper()
                helper.currConfig.apply {
                    full2FlagShow = 0
                    fullStepCicle.clear()
                    fullStepCicle.add(AdsIDDef.AdmobFullInters)
                    fullStepRe.clear()
                }
                val ready = ReadyNetwork(networkContext(), AdsIDDef.AdmobFullInters)
                setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to ready))
                setField(helper, "listAdsAll", mutableListOf<AdsBase>())
                setField(helper, "mActivity", mock(Activity::class.java))
                field<FullScreenAdPolicy>(helper, "fullScreenAdPolicy").onHostActivityResumed()
                helper.getAdsPlacementConfig("full_home", 1)!!.isLoaded = true
                assertTrue("mọi cổng qua thì sẵn sàng", helper.isInterstitialReadyToShow("home"))
                check(helper, ready)
            }
        } finally {
            sessionField.set(AppPluginManager.instance, savedSession)
        }
    }

    @Test fun `ready - host chua resume - false, giu co da load, khong nap lai`() = withReady { helper, ready ->
        field<FullScreenAdPolicy>(helper, "fullScreenAdPolicy").onHostActivityPaused()

        assertFalse(helper.isInterstitialReadyToShow("home"))
        assertTrue(helper.isInterstitialLoaded("home"))
        assertEquals(emptyList<String>(), ready.loads)
    }

    @Test fun `ready - dang trong cooldown N man sau quang cao - false, giu co da load, khong nap lai`() = withReady { helper, ready ->
        helper.setPostAdScreenCooldown(1)
        helper.markAutoAdShown()
        assertTrue(helper.isPostAdScreenCooldownActive())

        assertFalse(helper.isInterstitialReadyToShow("home"))
        assertTrue(helper.isInterstitialLoaded("home"))
        assertEquals(emptyList<String>(), ready.loads)

        helper.recordScreenChange()
        assertTrue("đổi màn: hết cooldown", helper.isInterstitialReadyToShow("home"))
    }

    // ═══ isInterstitialReadyToShow: cổng tức thời còn lại (1.5.0 Task 11 bước A, review Task 10 I1) ═══

    /** Ready trả `false`, không mạng nào được nạp, cờ đã load giữ đúng [loaded]. */
    private fun assertNotReadyNoReload(helper: AdsHelper, ready: ReadyNetwork, loaded: Boolean = true) {
        assertFalse(helper.isInterstitialReadyToShow("home"))
        assertEquals(loaded, helper.isInterstitialLoaded("home"))
        assertEquals(emptyList<String>(), ready.loads)
    }

    @Test fun `ready - placement dang hien - false, giu co da load, khong nap lai`() = withReady { helper, ready ->
        helper.getAdsPlacementConfig("full_home", 1)!!.isShowing = true
        assertNotReadyNoReload(helper, ready)
    }

    @Test fun `ready - placement tat tu xa flagShow -1 - false, giu co da load, khong nap lai`() = withReady { helper, ready ->
        helper.getAdsPlacementConfig("full_home", 1)!!.flagShow = -1
        assertNotReadyNoReload(helper, ready)
    }

    @Test fun `ready - co da load tat du mang con quang cao - false, khong nap lai`() = withReady { helper, ready ->
        helper.getAdsPlacementConfig("full_home", 1)!!.isLoaded = false
        assertNotReadyNoReload(helper, ready, loaded = false)
    }

    @Test fun `ready - dang co quang cao toan man hinh khac hien - false, giu co da load, khong nap lai`() = withReady { helper, ready ->
        helper.flagAdsFullShowing.set(1)
        assertNotReadyNoReload(helper, ready)
    }

    @Test fun `ready - nguoi dung da go quang cao toan man hinh - false, giu co da load, khong nap lai`() = withReady { helper, ready ->
        helper.setRemoveAdsMask(1 shl RemoveAdsMask.FLAG_FULLSCREEN)
        assertTrue(helper.isRemoveAds(4))
        assertNotReadyNoReload(helper, ready)
    }

    @Test fun `ready - Activity dang finish hoac da destroy - false, giu co da load, khong nap lai`() = withReady { helper, ready ->
        val finishing = mock(Activity::class.java).also { `when`(it.isFinishing).thenReturn(true) }
        setField(helper, "mActivity", finishing)
        assertNotReadyNoReload(helper, ready)

        val destroyed = mock(Activity::class.java).also { `when`(it.isDestroyed).thenReturn(true) }
        setField(helper, "mActivity", destroyed)
        assertNotReadyNoReload(helper, ready)
    }

    @Test fun `ready - co da load ma khong mang nao giu quang cao - lan nap lai dung danh sach flagImage cua loi goi`() {
        val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
        val savedSession = sessionField.get(AppPluginManager.instance) as Int
        sessionField.set(AppPluginManager.instance, 2)
        try {
            withInters {
                setField(helper, "mActivity", mock(Activity::class.java))
                field<FullScreenAdPolicy>(helper, "fullScreenAdPolicy").onHostActivityResumed()
                plcf("full_home").isLoaded = true

                assertFalse(helper.isInterstitialReadyToShow("home", 1))

                assertFalse("cờ cũ đã hạ", helper.isInterstitialLoaded("home"))
                assertEquals("nạp lại theo flagImage 1: native full AdMob đầu danh sách", listOf("full_home"), full2.loadPlacements())
                assertEquals(emptyList<String>(), net1.loadPlacements() + net2.loadPlacements())
            }
        } finally {
            sessionField.set(AppPluginManager.instance, savedSession)
        }
    }

    // ═══ showInterstitial: cổng brain (1.5.0 Task 11, test đặc tả viết và chạy trên mã cũ trước khi chuyển) ═══

    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "fail:$err" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed" }
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) { events += "click" }
    }

    /** Bật brain cho [check] (player mới, không holdout, [rollout]); tắt và trả mặc định sau khi chạy. */
    private fun withBrain(rollout: Double, check: () -> Unit) {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = rollout
        HoldoutManager.holdoutFraction = 0.0
        BrainPolicy.clear()
        try {
            AdsBrain.onSessionStart("t11_brain_${System.nanoTime()}")
            check()
        } finally {
            AdsBrain.enabled = false
            AdsBrain.rolloutFraction = 1.0
            HoldoutManager.holdoutFraction = 0.05
            BrainPolicy.clear()
        }
    }

    @Test fun `show - brain bat va tu choi - false, dung mot SHOW_FULL_BRAIN_DECLINED, khong goi mang, khong nap lai, khong dem luot hien`() =
        withReady { helper, ready ->
            withBrain(rollout = 1.0) {
                // Vượt trần interstitial/phiên và/24 h của guardrail: brain chỉ còn NO_ACTION → shouldShowInterstitial false.
                repeat(50) { AdsBrain.onInterstitialShown() }
                val shownBefore = AdsBrain.adsShownThisSession()
                val plcf = helper.getAdsPlacementConfig("full_home", 1)!!
                val cb = RecordingShowCb()

                ServerEventCapture().use { server ->
                    assertFalse(helper.showInterstitial("home", 0, cb))
                    assertEquals(listOf("full_home" to AdShowFailReason.SHOW_FULL_BRAIN_DECLINED), server.showFails(1))
                }

                assertEquals(emptyList<String>(), cb.events)
                assertEquals("không mạng nào showAd", emptyList<String>(), ready.shows)
                assertEquals("không nạp lại", emptyList<String>(), ready.loads)
                assertTrue("cờ đã load giữ nguyên", helper.isInterstitialLoaded("home"))
                assertEquals("cbShow chưa gán", null, plcf.cbShow)
                assertFalse(plcf.isShowing)
                assertEquals("giao thức show chưa khởi động", 0, helper.flagAdsFull4ShowFaild.get())
                assertEquals(0, helper.flagAdsFull4Dissmiss.get())
                assertEquals("không onInterstitialShown", shownBefore, AdsBrain.adsShownThisSession())

                // isDeltaTime = false (splash/intro): brain không được hỏi, lượt hiện.
                assertTrue(helper.showInterstitial("home", 0, cb, isDeltaTime = false))
                assertEquals(listOf("full_home"), ready.shows)
                assertEquals(shownBefore, AdsBrain.adsShownThisSession())
            }
        }

    @Test fun `show - brain bat, dong y va mang hien duoc - true, onInterstitialShown dem mot luot`() = withReady { helper, ready ->
        // Ngoài rollout: shouldShowInterstitial trả true tất định, nhưng brain vẫn được hỏi và vẫn đếm lượt hiện.
        withBrain(rollout = 0.0) {
            val shownBefore = AdsBrain.adsShownThisSession()

            assertTrue(helper.showInterstitial("home", 0, RecordingShowCb()))

            assertEquals(listOf("full_home"), ready.shows)
            assertEquals(shownBefore + 1, AdsBrain.adsShownThisSession())
        }
    }

    // ═══ Vòng show: lời gọi chéo vùng qua façade, giao thức đếm, cổng "đang hiện" (1.5.0 Task 11; 7 đột biến sống qua
    // 1291 test sau khi chuyển, `DOC/testing/t150-t11-mutation.log`; xanh trên mã cũ) ═══

    private fun transitionActive(helper: AdsHelper): Boolean = field<Boolean>(helper, "full2TransitionActive")

    @Test fun `show - app bam quang cao - ghi click vao FullScreenAdPolicy (chan quang cao tu dong khi quay lai) va bao app`() =
        withReady { helper, ready ->
            val policy = field<FullScreenAdPolicy>(helper, "fullScreenAdPolicy")
            val cb = RecordingShowCb()
            assertTrue(helper.showInterstitial("home", 0, cb))
            assertFalse(policy.shouldSuppressAutomaticFullscreen())

            ready.click()

            assertTrue("onAdClicked của façade chạy", policy.shouldSuppressAutomaticFullscreen())
            assertEquals(listOf("click"), cb.events)
        }

    @Test fun `show - chuyen canh Full2 dang bat, full2FlagShow 0 - dismiss ket thuc chuyen canh ngay, khong noi Full2`() =
        withReady { helper, ready ->
            val cb = RecordingShowCb()
            assertTrue(helper.showInterstitial("home", 0, cb))
            // Cấu hình đổi khi quảng cáo đang mở: chuyển cảnh bật dưới giá trị cũ phải được cân lại ở dismiss.
            setField(helper, "full2TransitionActive", true)

            ready.dismiss()

            assertFalse(transitionActive(helper))
            assertEquals(listOf("dismissed"), cb.events)
            assertEquals(0, helper.flagAdsFullShowing.get())
        }

    @Test fun `show - chuyen canh Full2 dang bat, moi mang loi dong bo - onAdShowFail cuoi vong ket thuc chuyen canh ngay`() =
        withReady { helper, ready ->
            setField(helper, "full2TransitionActive", true)
            ready.failSync = true
            val cb = RecordingShowCb()

            assertFalse(helper.showInterstitial("home", 0, cb))

            assertFalse("onAdShowFail (chạy ở settle) kết thúc chuyển cảnh", transitionActive(helper))
            assertEquals(listOf("fail:sync fail"), cb.events)
        }

    @Test fun `show - mang hien duoc - giao thuc dem show fail va dismiss o -1, fullIdxShow xoay, flagAdsFullShowing 1`() =
        withReady { helper, ready ->
            helper.currConfig.fullStepCicle.add(AdsIDDef.MaxFullInters)
            helper.flagAdsFull4ShowFaild.set(5)
            helper.flagAdsFull4Dissmiss.set(5)

            assertTrue(helper.showInterstitial("home", 0, RecordingShowCb()))

            assertEquals(listOf("full_home"), ready.shows)
            assertEquals("set(-2) rồi +1 khi mạng hiện", -1, helper.flagAdsFull4ShowFaild.get())
            assertEquals(-1, helper.flagAdsFull4Dissmiss.get())
            assertEquals("mạng 1/2 của fullStepCicle đã dùng: lượt sau bắt đầu ở mạng 2", 1, helper.fullIdxShow)
            assertEquals(1, helper.fullCount4Circle)
            assertEquals(1, helper.flagAdsFullShowing.get())
        }

    @Test fun `show - dang co quang cao toan man hinh hien - false, dung mot SHOW_FULL_WHEN_FLAG_SHOWING, khong goi mang`() =
        withReady { helper, ready ->
            helper.flagAdsFullShowing.set(1)
            val cb = RecordingShowCb()

            ServerEventCapture().use { server ->
                assertFalse(helper.showInterstitial("home", 0, cb))
                assertEquals(listOf("full_home" to AdShowFailReason.SHOW_FULL_WHEN_FLAG_SHOWING), server.showFails(1))
            }
            assertEquals(emptyList<String>(), ready.shows)
            assertEquals(emptyList<String>(), cb.events)
            assertEquals(1, helper.flagAdsFullShowing.get())
        }

    @Test fun `show - flagAdsFullShowing am - kep ve 0 roi hien, sau show bang 1`() = withReady { helper, ready ->
        helper.flagAdsFullShowing.set(-3)

        assertTrue(helper.showInterstitial("home", 0, RecordingShowCb()))

        assertEquals(listOf("full_home"), ready.shows)
        assertEquals(1, helper.flagAdsFullShowing.get())
    }

    @Test fun `show - moi mang loi dong bo - mot fail toi app, nap lai full_default roi placement cua luot`() =
        withReady { helper, ready ->
            ready.failSync = true
            val cb = RecordingShowCb()

            assertFalse(helper.showInterstitial("home", 0, cb))

            assertEquals(listOf("fail:sync fail"), cb.events)
            assertEquals(
                "onAdShowFail nạp full_default, rồi nhánh không hiện được nạp lại placement",
                listOf(PlacementDefault.PLFullDefault, "full_home"), ready.loads,
            )
            assertFalse("cờ đã load hạ", helper.isInterstitialLoaded("home"))
            assertEquals(0, helper.flagAdsFullShowing.get())
        }

    // ═══ Giao thức một-dismiss, chuỗi interstitial → Full2, thứ tự brain/cooldown (1.5.0 Task 12 bước A, review Task 11
    // I1–I3: 10 đột biến sống qua 1298 test; xanh trên mã cũ, trước khi Task 12 chuyển nửa Full2) ═══

    /**
     * Mạng toàn màn hình có quảng cáo sẵn: `onAdShowed` đồng bộ trong `showAd`, giữ `AdsShowCb` để test tự phát
     * impression/dismiss/show fail, lặp được (SDK phát trùng) — khác [ReadyNetwork.dismiss] (chỉ một lần).
     */
    private class ChainNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_chain"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        val shows = mutableListOf<Pair<String, Int>>()
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_chain_${adsType}_data"
        override fun getKeyForResDf(): String = "test_chain_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) {}
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement to flagShow
            showing = placement to adsShowCb
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
        fun impression() = showing!!.let { (p, cb) -> cb.onAdImpression(p, p, "test") }
        fun dismiss() = showing!!.let { (p, cb) -> cb.onAdDismissed(p, p, "test") }
        fun failAsync() = showing!!.let { (p, cb) -> cb.onAdShowFail(p, p, "test", "async fail") }
        /** Click như mạng thật: `placement` = placement gọi show, `originPl` = slot mạng [slot] (T3b). */
        fun click(slot: String) = showing!!.let { (p, cb) -> cb.onAdClick(p, slot, "test") }
    }

    /** Callback app ghi sự kiện kèm placement: `showed:full_home`, `dismissed:full_home`, `fail:full_home`. */
    private class PlacementShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "fail:$placement" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed:$placement" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed:$placement" }
    }

    private class ChainFixture(val helper: AdsHelper, val inter: ChainNetwork, val full2: ChainNetwork)

    /**
     * Phiên 2, host đã resume (Full2 hiện ngay, không xếp `pendingFull2`), Activity sống; interstitial = [ChainFixture.inter]
     * (AdMob), Full2 = [ChainFixture.full2] (native full AdMob), cả hai có quảng cáo; `full2FlagShow` theo [full2FlagShow].
     */
    private fun withChain(full2FlagShow: Int = 1, check: ChainFixture.() -> Unit) {
        val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
        val savedSession = sessionField.get(AppPluginManager.instance) as Int
        sessionField.set(AppPluginManager.instance, 2)
        try {
            MainHandlerTimers.run(honorRemoveAll = true) { _ ->
                val helper = AdsKillSwitchTest.newAdsHelper()
                helper.currConfig.apply {
                    this.full2FlagShow = full2FlagShow
                    fullStepCicle.clear()
                    fullStepCicle.add(AdsIDDef.AdmobFullInters)
                    fullStepRe.clear()
                    full2StepCicle.clear()
                    full2StepCicle.add(AdsIDDef.AdmobFullNt)
                    full2StepRe.clear()
                }
                val inter = ChainNetwork(networkContext(), AdsIDDef.AdmobFullInters)
                val full2 = ChainNetwork(networkContext(), AdsIDDef.AdmobFullNt)
                setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(
                    AdsIDDef.AdmobFullInters to inter, AdsIDDef.AdmobFullNt to full2,
                ))
                setField(helper, "listAdsAll", mutableListOf<AdsBase>())
                setField(helper, "mActivity", mock(Activity::class.java))
                field<FullScreenAdPolicy>(helper, "fullScreenAdPolicy").onHostActivityResumed()
                ChainFixture(helper, inter, full2).check()
            }
        } finally {
            sessionField.set(AppPluginManager.instance, savedSession)
        }
    }

    @Test fun `click tren slot dung chung - ad_click mang placement goi show full_home va full_2_home, khong phai slot full_default`() =
        withChain(full2FlagShow = 1) {
            // 1.7.0 T3b: cùng khái niệm placement với `ad_show_attempt`/`ad_show`; slot mạng chỉ là chỗ lùi khi rỗng.
            ServerEventCapture().use { server ->
                assertTrue(helper.showInterstitial("home", 0, PlacementShowCb(), isDeltaTime = false))
                inter.dismiss()                        // Full2 hiện `full_2_home` sau khi cha đóng
                full2.click(slot = "full_default")
                inter.click(slot = "full_default")     // callback của cha còn giữ; click trước dismiss thì harness không hiện Full2
                val clicks = server.await("ad_click", 2).map { it.get("placement").asString }.sorted()
                assertEquals("ad_click.placement", listOf("full_2_home", "full_home"), clicks)
            }
        }

    @Test fun `chuoi inter - Full2 - app nhan showed cha, dismissed cha, showed Full2 theo thu tu, Full2 dong khong toi app lan hai`() =
        withChain(full2FlagShow = 1) {
            val cb = PlacementShowCb()
            assertTrue(helper.showInterstitial("home", 0, cb, isDeltaTime = false))
            assertTrue("chuyển cảnh bật khi cha hiện", transitionActive(helper))

            inter.dismiss()

            assertEquals(listOf("showed:full_home", "dismissed:full_home", "showed:full_2_home"), cb.events)
            assertEquals("Full2 hiện với flagShow 0", listOf("full_2_home" to 0), full2.shows)
            assertEquals("cha set(-10) rồi Full2 +1", -9, helper.flagAdsFull4Dissmiss.get())
            assertTrue("chuyển cảnh còn bật sau dismiss của cha, tới impression của Full2", transitionActive(helper))

            full2.impression()
            assertFalse("impression của Full2 kết thúc chuyển cảnh", transitionActive(helper))

            full2.dismiss()

            assertEquals(-8, helper.flagAdsFull4Dissmiss.get())
            assertEquals("Full2 đóng: không dismiss thứ hai", 1, cb.events.count { it.startsWith("dismissed") })
            assertEquals(0, helper.flagAdsFullShowing.get())
        }

    @Test fun `khong noi Full2 - mang goi onAdDismissed 3 lan - app nhan dung mot`() = withChain(full2FlagShow = 0) {
        val cb = PlacementShowCb()
        assertTrue(helper.showInterstitial("home", 0, cb, isDeltaTime = false))

        inter.dismiss(); inter.dismiss(); inter.dismiss()

        assertEquals(listOf("showed:full_home", "dismissed:full_home"), cb.events)
        assertEquals(emptyList<Pair<String, Int>>(), full2.shows)
    }

    @Test fun `khong noi Full2 - mang bao onAdShowFail bat dong bo 3 lan sau khi hien - app nhan dung mot, lan show ke hien duoc`() =
        withChain(full2FlagShow = 0) {
            val cb = PlacementShowCb()
            assertTrue(helper.showInterstitial("home", 0, cb, isDeltaTime = false))

            inter.failAsync(); inter.failAsync(); inter.failAsync()

            assertEquals(listOf("showed:full_home", "fail:full_home"), cb.events)
            assertFalse("isShowing đã hạ", helper.getAdsPlacementConfig("full_home", 1)!!.isShowing)
            assertTrue("placement không kẹt: lượt kế hiện được", helper.showInterstitial("home", 0, PlacementShowCb(), isDeltaTime = false))
            assertEquals(listOf("full_home", "full_home"), inter.shows.map { it.first })
        }

    @Test fun `full2FlagShow 0 - hien interstitial khong bat chuyen canh Full2`() = withChain(full2FlagShow = 0) {
        assertTrue(helper.showInterstitial("home", 0, PlacementShowCb(), isDeltaTime = false))

        assertFalse(transitionActive(helper))
    }

    /** Một lượt đã hiện và đóng với cooldown 1 màn: cooldown đang bật; `fullTimeShow = 0` để cổng luật tần suất không chặn trước. */
    private fun ChainFixture.oneTurnShownUnderCooldown() {
        helper.setPostAdScreenCooldown(1)
        assertTrue(helper.showInterstitial("home", 0, PlacementShowCb(), isDeltaTime = false))
        inter.dismiss()
        helper.fullTimeShow = 0
        assertTrue("cooldown N màn đang bật", helper.isPostAdScreenCooldownActive())
    }

    @Test fun `cooldown N man dang bat - showInterstitial isDeltaTime false (splash, intro) van hien`() = withChain(full2FlagShow = 0) {
        oneTurnShownUnderCooldown()

        assertTrue(helper.showInterstitial("home", 0, PlacementShowCb(), isDeltaTime = false))

        assertEquals(listOf("full_home", "full_home"), inter.shows.map { it.first })
    }

    /** Bật brain (rollout 1, không holdout, player mới) quanh [block]; trả các quyết định brain đã ghi. */
    private fun brainDecisions(block: () -> Unit): List<DecisionPoint> {
        val decisions = mutableListOf<DecisionPoint>()
        withBrain(rollout = 1.0) {
            DecisionLogger.registerForwarder { req, _ -> decisions += req.decisionPoint }
            try {
                block()
            } finally {
                DecisionLogger.clearForwardersForTest()
            }
        }
        return decisions
    }

    @Test fun `brain bat, moi cong qua - dung mot quyet dinh INTERSTITIAL_OPPORTUNITY (doi chung)`() = withChain(full2FlagShow = 0) {
        val decisions = brainDecisions { helper.showInterstitial("home", 0, PlacementShowCb()) }

        assertEquals(listOf(DecisionPoint.INTERSTITIAL_OPPORTUNITY), decisions)
    }

    @Test fun `brain bat, cooldown N man chan - brain khong duoc hoi, khong quyet dinh nao`() = withChain(full2FlagShow = 0) {
        oneTurnShownUnderCooldown()

        val decisions = ServerEventCapture().use { server ->
            val d = brainDecisions { assertFalse(helper.showInterstitial("home", 0, PlacementShowCb())) }
            assertEquals("cổng chặn là cooldown", listOf("full_home" to AdShowFailReason.POST_AD_SCREEN_COOLDOWN), server.showFails(1))
            d
        }

        assertEquals(emptyList<DecisionPoint>(), decisions)
    }

    @Test fun `brain bat, dang co quang cao toan man hinh hien - brain khong duoc hoi, khong quyet dinh nao`() = withChain(full2FlagShow = 0) {
        helper.flagAdsFullShowing.set(1)

        val decisions = brainDecisions { assertFalse(helper.showInterstitial("home", 0, PlacementShowCb())) }

        assertEquals(emptyList<DecisionPoint>(), decisions)
    }

    @Test fun `brain tu choi sau onInterstitialShown khong co quyet dinh treo - quyet dinh tu choi chot reward estimate(false, true) (O9 da sua)`() =
        withReady { helper, ready ->
            withBrain(rollout = 1.0) {
                // Không có quyết định treo: onInterstitialShown đặt pendingHadValue = true (vd người ngoài rollout đã hiện;
                // review Task 11). Vượt trần phiên: lượt kế brain từ chối (NO_ACTION). O9 đã sửa (plan 1.6.0 Task 1): quyết
                // định mới bắt đầu với pendingHadValue = false nên cờ cũ không còn gán cho quyết định từ chối.
                repeat(50) { AdsBrain.onInterstitialShown() }
                val rewards = mutableListOf<Double?>()
                DecisionLogger.registerFeedbackForwarder { _, _, _, r -> rewards += r }
                try {
                    assertFalse(helper.showInterstitial("home", 0, RecordingShowCb()))
                    assertEquals(emptyList<String>(), ready.shows)

                    AdsBrain.shouldShowInterstitial() // chốt quyết định từ chối đang treo

                    assertEquals("đúng một feedback: quyết định từ chối", 1, rewards.size)
                    assertEquals(
                        "quyết định từ chối không giao giá trị — không thừa hưởng lượt hiện cũ (O9 đã sửa)",
                        RewardModel.estimate(false, true), rewards.single()!!, 1e-9,
                    )
                } finally {
                    DecisionLogger.clearForwardersForTest()
                }
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
