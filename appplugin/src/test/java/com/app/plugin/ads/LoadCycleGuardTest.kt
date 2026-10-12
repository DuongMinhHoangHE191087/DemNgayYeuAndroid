package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Task I review C1/M6: vòng waterfall của `AdsHelper` (circle) phải thuộc về một lượt load của placement. Sau khi
 * watchdog 90 s của placement đã báo `load timeout` và app load lại, vòng cũ (còn giữ slot mạng đang chờ khởi tạo)
 * tỉnh dậy khi watchdog 60 s của slot đó hết hạn: nó không được chạm `isLoading`/`cbLoad` của lượt mới. Mỗi request
 * của app nhận đúng một callback kết thúc.
 */
class LoadCycleGuardTest {

    /** Slot mạng giữ lượt load tới khi mạng khởi tạo; mạng không bao giờ sẵn sàng trong test. */
    private class HeldPl(context: Context, pl: String) :
        AdsPlBase(context, mock(AdsBase::class.java), 0, pl) {
        private val queue = SdkReadyQueue()
        override val eventTag: String get() = "test_held"
        override fun onInit() = Unit
        override fun tryLoad() {
            holdUntilReady(queue)
        }
        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int) = false
    }

    private class HeldNetwork(context: Context, type: Int, private val pl: AdsPlBase) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_held_${adsType}_data"
        override fun getKeyForResDf(): String = "test_held_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
    }

    /** Mạng rect: load treo (không callback); [getAdForShow] trả rect đã có sẵn trong cache của mạng. */
    private class RectNetwork(context: Context) : AdsBaseRect(AdsIDDef.AdmobRect, context) {
        override val eventTag: String get() = "test_rect"
        var cached: Any? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_rect_data"
        override fun getKeyForResDf(): String = "test_rect_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun getAdForShow(placement: String, adsShowCb: AdsShowCb): Any? = cached
    }

    /**
     * Mạng interstitial ghi callback mỗi lần `loadAd`; [sync] thì báo `onLoaded` ĐỒNG BỘ ngay trong `loadAd`, như lối tắt
     * "has loaded" của `AdsPlBase.load`.
     */
    private class LoadNetwork(context: Context, type: Int, private val sync: Boolean) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_load"
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_load_${adsType}_data"
        override fun getKeyForResDf(): String = "test_load_${adsType}_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) {
            loads += adsLoadCb
            if (sync) adsLoadCb.onLoaded(placement, placement, "t", null, null)
        }
    }

    private class AppCb(private val onFail: (() -> Unit)? = null) : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) {
            events += "loaded"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "fail:$error"
            if (events.size == 1) onFail?.invoke()
        }
    }

    private lateinit var context: Context

    private fun setField(helper: AdsHelper, name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
    }

    /** Helper với waterfall interstitial AdMob → MAX, cả hai slot đều giữ chờ mạng khởi tạo. Dựng trong khối timer. */
    private fun helperWithHeldInters(): Triple<AdsHelper, HeldPl, HeldPl> {
        val admob = HeldPl(context, "full_default").apply { setActivity(mock(Activity::class.java)) }
        val max = HeldPl(context, "full_default").apply { setActivity(mock(Activity::class.java)) }
        val helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply {
            full2FlagShow = 0
            fullStepCicle.addAll(listOf(AdsIDDef.AdmobFullInters, AdsIDDef.MaxFullInters))
        }
        helper.placementsConfigManager = AdsPlacementConfigManager()
        val networks = mutableMapOf<Int, AdsBaseFull>(
            AdsIDDef.AdmobFullInters to HeldNetwork(context, AdsIDDef.AdmobFullInters, admob),
            AdsIDDef.MaxFullInters to HeldNetwork(context, AdsIDDef.MaxFullInters, max),
        )
        setField(helper, "listAdsFull", networks)
        setField(helper, "listAdsAll", networks.values.toMutableList<AdsBase>())
        return Triple(helper, admob, max)
    }

    /**
     * t=0 load (cb1): AdMob giữ, watchdog slot A@60 s, watchdog placement@90 s. t=60 slot A hết hạn → vòng 1 sang MAX
     * (giữ, watchdog M@120 s). t=90 placement hết hạn → cb1 nhận `load timeout` và app load lại ngay (cb2): vòng 2 →
     * AdMob giữ lại (A'@150 s). t=120 slot M hết hạn → vòng 1 tỉnh dậy trong lúc lượt 2 đang chạy. Trước đây vòng 1
     * hạ `isLoading` và báo cb2 `list empty`, rồi vòng 2 báo cb2 lần nữa. Nay lượt 2 nhận đúng một callback.
     */
    @Test
    fun `vong cu tinh day khi luot moi dang chay - luot moi nhan dung mot callback`() = MainHandlerTimers.run { t ->
        val (helper, admob, max) = helperWithHeldInters()
        val cb2 = AppCb()
        val cb1 = AppCb(onFail = { helper.loadInterstitialForThisTurn("home", 0, cb2) })

        helper.loadInterstitialForThisTurn("home", 0, cb1)
        assertTrue("slot AdMob giữ lượt 1", admob.isLoadingAd)
        assertEquals(listOf(60_000L, 90_000L), t.pendingDelays.sorted())

        t.advanceTo(60_000)
        assertTrue("vòng 1 đi tiếp sang MAX sau khi slot AdMob hết hạn", max.isLoadingAd)
        assertEquals(emptyList<String>(), cb1.events)

        t.advanceTo(90_000)
        assertEquals(listOf("fail:load timeout"), cb1.events)
        assertTrue("app load lại: slot AdMob giữ lượt 2", admob.isLoadingAd)
        assertEquals(emptyList<String>(), cb2.events)

        t.advanceTo(120_000)
        assertEquals("vòng 1 tỉnh dậy không được báo cho lượt 2", emptyList<String>(), cb2.events)

        t.advanceTo(600_000)
        assertEquals("lượt 1 chỉ một callback", 1, cb1.events.size)
        assertEquals("lượt 2 đúng một callback", listOf("fail:load timeout"), cb2.events)
    }

    /**
     * Review I-1: `showRect` gán `cbLoad` rồi lấy rect từ cache của mạng và báo `onLoaded` ngay, không bật `isLoading`.
     * Sau một lần placement hết hạn, callback đó phải tới app (trước đây getter `cbLoad` trả `null` tới lượt kế).
     */
    @Test
    fun `placement het han mot lan - showRect lay rect tu cache van bao onLoaded dung mot lan`() = MainHandlerTimers.run { t ->
        val helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply { rectStepList.add(AdsIDDef.AdmobRect) }
        helper.placementsConfigManager = AdsPlacementConfigManager()
        val rect = RectNetwork(context)
        setField(helper, "listAdsRect", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobRect to rect))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>(rect))
        val cb1 = AppCb()
        helper.loadRect4Show("home", cb1)
        t.advanceTo(90_000)
        assertEquals(listOf("fail:load timeout"), cb1.events)

        rect.cached = ObjectForShowAdWithViewParent().apply { adLoad = mock(View::class.java) }
        val cb2 = AppCb()
        helper.showRect("home", mock(ViewGroup::class.java), cb2)

        assertEquals("rect từ cache phải báo onLoaded cho request này", listOf("loaded"), cb2.events)
        assertEquals(1, cb1.events.size)
    }

    /**
     * Review Task 9 m2 (1.5.0 Task 10 bước A): `loadInterstitialForThisTurn` gán `cbLoad` TRƯỚC khi chạy vòng. Lối tắt
     * "has loaded" của `AdsPlBase.load` báo `onLoaded` đồng bộ ngay trong `loadAd`; gán sau vòng thì callback của lần gọi
     * này không bao giờ tới, còn callback của lần gọi trước nhận `onLoaded` lần hai.
     */
    @Test
    fun `interstitial mang bao onLoaded dong bo trong loadAd - callback cua lan goi nay nhan dung mot lan, lan truoc khong nhan them`() = MainHandlerTimers.run { _ ->
        val helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply {
            full2FlagShow = 0
            fullStepCicle.add(AdsIDDef.AdmobFullInters)
        }
        helper.placementsConfigManager = AdsPlacementConfigManager()
        val async = LoadNetwork(context, AdsIDDef.AdmobFullInters, sync = false)
        setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to async))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>(async))
        val cb1 = AppCb()
        helper.loadInterstitialForThisTurn("home", 0, cb1)
        async.loads.single().onLoaded("full_home", "full_home", "t", null, null)
        assertEquals(listOf("loaded"), cb1.events)

        val sync = LoadNetwork(context, AdsIDDef.AdmobFullInters, sync = true)
        setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to sync))
        val cb2 = AppCb()
        helper.loadInterstitialForThisTurn("home", 0, cb2)

        assertEquals("mạng đồng bộ được gọi đúng một lần", 1, sync.loads.size)
        assertEquals(listOf("loaded"), cb2.events)
        assertEquals("lần gọi trước không nhận thêm", listOf("loaded"), cb1.events)
        assertTrue(helper.isInterstitialLoaded("home"))
    }
}
