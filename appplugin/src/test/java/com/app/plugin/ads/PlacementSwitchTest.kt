package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import com.app.plugin.app.AppPluginManager
import com.app.plugin.brain.LicenseTestRule
import com.app.plugin.firbase.FirHelperConfig
import com.google.firebase.firestore.DocumentSnapshot
import com.app.plugin.segment.SegmentTestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.4.2 SWITCH (Hợp đồng B, D1-242): công tắc placement qua `cf_ads_placement` tương đương `PlacementSwitches` của fork —
 * bật lại khi gỡ khoá (theo từng nguồn RC/Firestore), bí danh `full_x` ↔ `x`, `isPlacementEnabled`, Full2 tắt theo
 * `full_2_X` hoặc `full_2_default`, cold start từ bản gộp, JSON hỏng giữ nguyên.
 */
class PlacementSwitchTest {
    @get:Rule val license = LicenseTestRule()

    /** Mạng toàn màn hình có quảng cáo sẵn; ghi load/show, dismiss bằng tay. */
    private class Net(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "t242_switch"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        val loads = mutableListOf<String>()
        val shows = mutableListOf<String>()
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "t242_switch_${adsType}_data"
        override fun getKeyForResDf(): String = "t242_switch_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement
            showing = placement to adsShowCb
            return true
        }
        fun dismiss() = showing!!.let { (p, cb) -> showing = null; cb.onAdDismissed(p, p, "t") }
    }

    private class Cb : AdsShowCb {
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) = Unit
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) = Unit
    }

    private class LoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private val store = SegmentTestSupport.newStore()
    private val ctx = SegmentTestSupport.prefsContext(store)
    private val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
    private var savedSession = 0

    @Before fun setUp() {
        savedSession = sessionField.get(AppPluginManager.instance) as Int
        sessionField.set(AppPluginManager.instance, 2)
    }

    @After fun tearDown() { sessionField.set(AppPluginManager.instance, savedSession) }

    /** `AdsHelper` mới có `mContext` = prefs trong bộ nhớ [store] (cùng store = cùng "đĩa" qua cold start). */
    private fun newHelper(): AdsHelper = AdsKillSwitchTest.newAdsHelper().also { set(it, "mContext", ctx) }

    private fun AdsHelper.flag(pl: String) = getAdsPlacementConfig(pl, 1)!!.flagShow
    private fun AdsHelper.firestore(json: String) = placementsConfigManager.switches.apply(ctx, PlacementSource.FIRESTORE, json)
    private fun mem() = store[PlacementSwitchState.KEY_MERGED]

    @Test fun `SW1 khoa RC lan truoc dat ma nay vang - bat lai flagShow 1`() {
        val h = newHelper()
        h.parserAdsConfig("""{"full_clean_done":{"flag":-1}}""")
        assertEquals(-1, h.flag("full_clean_done"))

        h.parserAdsConfig("""{"full_home":{"flag":1}}""")

        assertEquals("gỡ khoá trên RC = bật lại", 1, h.flag("full_clean_done"))
        assertTrue(h.isPlacementEnabled("full_clean_done"))
    }

    @Test fun `SW2 RC rong sau khi tat - bat lai va mem bang chuoi rong`() {
        val h = newHelper()
        h.parserAdsConfig("""{"full_clean_done":{"flag":-1}}""")
        assertTrue(h.placementsConfigManager.switches.hasRecord(PlacementSource.REMOTE_CONFIG))

        h.parserAdsConfig("")

        assertEquals(1, h.flag("full_clean_done"))
        assertEquals("", mem())
        assertFalse(h.placementsConfigManager.switches.hasRecord(PlacementSource.REMOTE_CONFIG))
    }

    @Test fun `SW3 bi danh - khoa clean_done tat full_clean_done (load cf not), full_gift_x tat gift_x, khoa dung ten thang`() {
        val h = newHelper()
        h.parserAdsConfig("""{"clean_done":{"flag":-1},"full_gift_x":{"flag":-1},"home":{"flag":-1},"full_home":{"flag":1}}""")

        assertFalse(h.isPlacementEnabled("full_clean_done"))
        assertFalse(h.isPlacementEnabled("gift_x"))
        assertTrue("JSON có cả hai: khoá đúng tên thắng", h.isPlacementEnabled("full_home"))
        val cb = LoadCb()
        h.loadInterstitialForThisTurn("clean_done", 0, cb)
        assertEquals(listOf("fail:cf not"), cb.events)
    }

    /** Phiên 2, host resume, interstitial + Full2 (native full) có quảng cáo, `full2FlagShow` 1. */
    private fun withChain(check: (AdsHelper, Net, Net) -> Unit) = MainHandlerTimers.run(honorRemoveAll = true) { _ ->
        val h = newHelper()
        h.currConfig.apply {
            full2FlagShow = 1
            fullStepCicle.clear(); fullStepCicle.add(AdsIDDef.AdmobFullInters); fullStepRe.clear()
            full2StepCicle.clear(); full2StepCicle.add(AdsIDDef.AdmobFullNt); full2StepRe.clear()
        }
        val inter = Net(ctx, AdsIDDef.AdmobFullInters)
        val full2 = Net(ctx, AdsIDDef.AdmobFullNt)
        set(h, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to inter, AdsIDDef.AdmobFullNt to full2))
        set(h, "listAdsAll", mutableListOf<AdsBase>())
        set(h, "mActivity", mock(Activity::class.java))
        (get(h, "fullScreenAdPolicy") as FullScreenAdPolicy).onHostActivityResumed()
        check(h, inter, full2)
    }

    private fun assertFull2Off(h: AdsHelper, inter: Net, full2: Net, why: String, off: String) {
        assertTrue(h.showInterstitial("clean_done", 0, Cb(), isDeltaTime = false))
        assertFalse("$why: không dựng lớp che", get(h, "full2TransitionActive") as Boolean)
        (get(h, "fullScreenAdPolicy") as FullScreenAdPolicy).onHostActivityPaused()   // Full2 bật thì sẽ xếp hàng
        inter.dismiss()
        assertNull("$why: không queue Full2", h.pendingFull2Placement())
        assertFalse("$why: lớp che hạ ngay", get(h, "full2TransitionActive") as Boolean)
        assertEquals("$why: không hiện", emptyList<String>(), full2.shows)
        assertEquals("$why: không nạp Full2 tắt", emptyList<String>(), full2.loads.filter { it == "full_2_clean_done" || it == off })
    }

    @Test fun `SW4 full_2_clean_done tat - sau interstitial full_clean_done khong lop che, khong queue, khong nap`() =
        withChain { h, inter, full2 ->
            h.parserAdsConfig("""{"full_2_clean_done":{"flag":-1}}""")
            assertFull2Off(h, inter, full2, "full_2_clean_done -1", "full_2_clean_done")
        }

    @Test fun `SW4 full_2_default tat - Full2 cua full_clean_done cung tat`() = withChain { h, inter, full2 ->
        h.parserAdsConfig("""{"full_2_default":{"flag":-1}}""")
        assertFull2Off(h, inter, full2, "full_2_default -1", PlacementDefault.PLFull2Default)
        // R-A' m5: cửa load riêng — full_2_clean_done bật (flagShow 1) nhưng full_2_default tắt: load kèm của interstitial không nạp Full2.
        h.getAdsPlacementConfig("full_clean_done", 1)!!.isLoading = false
        h.loadInterstitialForThisTurn("clean_done", 0)
        assertFalse("load kèm Full2 bị công tắc full_2_default chặn", "full_2_clean_done" in full2.loads)
    }

    @Test fun `SW5 cold start tu ban gop van tat, JSON hong giu nguyen, isPlacementEnabled khong tao entry, truoc init true`() {
        val h = newHelper()
        h.parserAdsConfig("""{"full_clean_done":{"flag":-1}}""")
        h.parserAdsConfig("""{ khong phai json""")
        assertEquals("JSON hỏng: giữ nguyên", -1, h.flag("full_clean_done"))

        val cold = newHelper()   // process mới, cùng prefs: initAdsHelper dựng manager rồi restore
        cold.placementsConfigManager = AdsPlacementConfigManager()
        cold.placementsConfigManager.switches.restore(ctx)
        assertFalse("cold start vẫn tắt", cold.isPlacementEnabled("full_clean_done"))
        cold.parserAdsConfig("")
        assertTrue("bản ghi RC sống qua cold start: gỡ khoá vẫn bật lại", cold.isPlacementEnabled("full_clean_done"))

        assertTrue(cold.isPlacementEnabled("full_never_seen"))
        assertNull("gọi không tạo entry", cold.placementsConfigManager.peekConfig("full_never_seen"))
        val uninit = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        assertTrue("trước initAdsHelper: true, không ném", uninit.isPlacementEnabled("full_x"))
    }

    @Test fun `SW6 Firestore tat full_a, RC rong khi RC chua dat gi - full_a van -1`() {
        val h = newHelper()
        h.firestore("""{"full_a":{"flag":-1}}""")
        assertFalse(h.placementsConfigManager.switches.hasRecord(PlacementSource.REMOTE_CONFIG))

        h.parserAdsConfig("")   // kể cả khi RC rỗng vẫn được áp

        assertEquals(-1, h.flag("full_a"))
        assertTrue("bản gộp còn công tắc Firestore", (mem() as String).contains("full_a"))
    }

    @Test fun `SW7 nguon nay khong bat lai cong tac cua nguon kia`() {
        val h = newHelper()
        h.parserAdsConfig("""{"full_a":{"flag":-1},"full_c":{"flag":-1}}""")
        h.firestore("""{"full_b":{"flag":-1},"full_c":{"flag":-1}}""")
        assertEquals("Firestore không bật lại full_a của RC", -1, h.flag("full_a"))

        h.firestore("""{"full_b":{"flag":-1}}""")
        assertEquals("Firestore gỡ full_c nhưng RC còn tắt", -1, h.flag("full_c"))

        h.parserAdsConfig("")
        assertEquals("RC gỡ: full_a bật lại", 1, h.flag("full_a"))
        assertEquals(1, h.flag("full_c"))
        assertEquals("full_b của Firestore giữ", -1, h.flag("full_b"))
    }

    @Test fun `SW8 nang tu 2_4_1 - chi co ban gop, chua co ban ghi nguon - coi la RC, RC rong bat lai`() {
        store[PlacementSwitchState.KEY_MERGED] = """{"full_clean_done":{"flag":-1}}"""   // prefs của 2.4.1
        val h = newHelper()
        h.placementsConfigManager.switches.restore(ctx)
        assertFalse(h.isPlacementEnabled("full_clean_done"))
        assertTrue(h.placementsConfigManager.switches.hasRecord(PlacementSource.REMOTE_CONFIG))

        h.parserAdsConfig("")

        assertTrue("khoá RC 2.4.1 bị gỡ: bật lại ở lần fetch đầu của 2.4.2", h.isPlacementEnabled("full_clean_done"))
    }

    @Test fun `SW9 Firestore rong sau khi tat - chi bat lai khoa cua Firestore, rong khi chua co ban ghi - khong ghi prefs (R-A' m4)`() {
        val ads = AdsHelper.instance
        val names = listOf("mContext", "currConfig", "placementsConfigManager", "listAdsAll")
        val saved = names.associateWith { get(ads, it) }
        try {
            set(ads, "mContext", ctx)
            set(ads, "currConfig", AdsConfigOb(ctx))
            set(ads, "placementsConfigManager", AdsPlacementConfigManager())
            set(ads, "listAdsAll", mutableListOf<AdsBase>())
            ads.parserAdsConfig("""{"full_b":{"flag":-1}}""")
            firestoreFetch("""{"full_a":{"flag":-1}}""")
            assertEquals(-1, ads.flag("full_a"))

            firestoreFetch(null)   // khoá cf_ads_placement gỡ khỏi document

            assertEquals("Firestore gỡ: full_a bật lại", 1, ads.flag("full_a"))
            assertEquals("full_b của RC giữ", -1, ads.flag("full_b"))
            store.remove(PlacementSource.FIRESTORE.prefKey)
            firestoreFetch("")
            assertFalse("rỗng khi chưa có bản ghi: không ghi prefs", store.containsKey(PlacementSource.FIRESTORE.prefKey))
        } finally {
            saved.forEach { (n, v) -> set(ads, n, v) }
        }
    }

    /** `FirHelperConfig.parserCfWithFireStore` thật với document chỉ có `cf_ads_placement` = [value]. */
    private fun firestoreFetch(value: String?) {
        val doc = mock(DocumentSnapshot::class.java)
        `when`(doc.getString("cf_ads_placement")).thenReturn(value)
        FirHelperConfig::class.java.getDeclaredMethod("parserCfWithFireStore", DocumentSnapshot::class.java)
            .apply { isAccessible = true }.invoke(FirHelperConfig(), doc)
    }

    private companion object {
        fun set(helper: AdsHelper, name: String, value: Any?) {
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
        }

        fun get(helper: AdsHelper, name: String): Any? =
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper)
    }
}
