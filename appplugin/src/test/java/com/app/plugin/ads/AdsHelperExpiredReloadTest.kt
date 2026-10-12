package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * Review E I2 (FE2): quảng cáo quá 60 phút bị bỏ ở `hasLoadedAd` ([AdsHelper.isInterstitialReadyToShow]), ở
 * `getAdForShow` ([AdsHelper.getNativeFullForShow]) hay ở `show` thì cờ "đã load" của placement phải hạ ngay
 * ([AdsHelper.isInterstitialLoaded] `false`) và SDK nạp lại placement đó đúng một lần. Trước đây cờ kẹt ở `true`
 * không còn quảng cáo nào, nên mẫu "ad break" của Speaker Clean (`MainActivity.kt:432-545`: ready → show, else if
 * not loaded → load) không bao giờ load lại và interstitial của placement dừng hẳn.
 *
 * Placement thật tối thiểu ([ExpiringPl]): hạn 60 phút như 7 placement toàn màn hình, đếm `tryLoad`, load xong khi
 * test gọi [ExpiringPl.completeLoad] (như `onAdLoaded` của mạng).
 */
class AdsHelperExpiredReloadTest {

    private val sixtyMin = 60 * 60_000L
    private val clock = LoadedAdFixture.Clock()

    private class RecordingShowCb : AdsGiftShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            events += "dismissed"
        }
    }

    /** Placement toàn màn hình có hạn 60 phút; [tryLoads] đếm lần tải mới thật sự tới mạng. */
    private class ExpiringPl(context: Context, pl: String, private val clock: LoadedAdFixture.Clock) :
        AdsPlBase(context, mock(AdsBase::class.java), 0, pl) {
        override val eventTag: String get() = "test_expiring"
        override val loadedAdMaxAgeMs: Long get() = 60 * 60_000L
        var tryLoads = 0
        var loadSeq = 0
        private var shownCb: AdsShowCb? = null
        override fun onInit() = Unit
        override fun tryLoad() {
            tryLoads++
            adsPlacementEcmp.isLoading = true
        }

        /** Mạng trả quảng cáo mới cho lần tải đang chờ (không có lần tải nào đang chờ thì không làm gì). */
        fun completeLoad() {
            if (!adsPlacementEcmp.isLoading) return
            adsPlacementEcmp.isLoading = false
            val item = LoadedAdFixture.giveLoadedAd(this, Any(), clock).apply { adsId = "ad-${++loadSeq}" }
            adsPlacementEcmp.adsLoadCb?.onLoaded(loadPl, adsPlacementEcmp.originPl, item.adsId, item, this)
        }

        /** Người dùng đóng quảng cáo đang hiện: `onAdDismissed` của lần [show] trước. */
        fun dismiss() {
            shownCb?.onAdDismissed(PlacementDefault.PLFullDefault, PlacementDefault.PLFullDefault, "ad-$loadSeq")
            shownCb = null
        }

        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean {
            val item = loadedAdWithinMaxAge() ?: return false
            item.isLoaded = false
            item.adObject = null
            adsShowCb?.onAdShowed(placement, placement, item.adsId, flagShow)
            shownCb = adsShowCb
            return true
        }

        // Chỉ đi đường quảng cáo hết hạn (bị bỏ trong loadedAdWithinMaxAge, trả null như mạng thật).
        override fun getAdForShow(placement: String, adsShowCb: AdsShowCb?): Any? =
            loadedAdWithinMaxAge()?.let { error("test chỉ dùng quảng cáo hết hạn cho getAdForShow") }
    }

    private class FullNetwork(context: Context, type: Int, pl: AdsPlBase) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        init { mapAdsPl[PlacementDefault.PLFullDefault] = pl }
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_expired_reload_full_data"
        override fun getKeyForResDf(): String = "test_expired_reload_full_res"
    }

    private class GiftNetwork(context: Context, pl: AdsPlBase) : AdsBaseGift(AdsIDDef.AdmobRewarded, context) {
        override val eventTag: String get() = "test_net"
        init { mapAdsPl[PlacementDefault.PLGiftDefault] = pl }
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_expired_reload_gift_data"
        override fun getKeyForResDf(): String = "test_expired_reload_gift_res"
    }

    private lateinit var context: Context
    private lateinit var helper: AdsHelper
    private val activity = mock(Activity::class.java)

    private fun setField(name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    private fun field(name: String): Any? =
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper)

    @Before
    fun setUp() {
        AdsPolicy.current = AdsPolicy.DEFAULT
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.full2FlagShow = 0
        setField("listAdsFull", mutableMapOf<Int, AdsBaseFull>())
        setField("listAdsGift", mutableMapOf<Int, AdsBaseGift>())
        setField("listAdsAll", mutableListOf<AdsBase>())
    }

    @After
    fun tearDown() {
        AdsPolicy.current = AdsPolicy.DEFAULT
        LogUtil.setFlagLog(1)
    }

    /** Interstitial [type] đứng đầu waterfall, host đang resume; trả placement của mạng. */
    private fun withInterstitialNetwork(type: Int = AdsIDDef.AdmobFullInters): ExpiringPl =
        withInterstitialNetworks(type).single()

    /**
     * Các mạng [types] theo thứ tự waterfall, host đang resume. Mỗi mạng chỉ có slot `full_default`: mọi placement
     * `full_*` (home, tab_switch…) dùng chung slot đó của mạng, như `AdsBase.getAdsForPl` với cấu hình thật.
     */
    @Suppress("UNCHECKED_CAST")
    private fun withInterstitialNetworks(vararg types: Int): List<ExpiringPl> {
        val pls = types.map { type ->
            val pl = ExpiringPl(context, PlacementDefault.PLFullDefault, clock)
            val network = FullNetwork(context, type, pl)
            helper.currConfig.fullStepCicle.add(type)
            (field("listAdsFull") as MutableMap<Int, AdsBaseFull>)[type] = network
            (field("listAdsAll") as MutableList<AdsBase>).add(network)
            pl
        }
        helper.setMainActivity(activity)
        (field("fullScreenAdPolicy") as FullScreenAdPolicy).onHostActivityResumed()
        return pls
    }

    /** `Handler(Looper.getMainLooper()).post` chạy ngay: waterfall load đi tiếp sang mạng sau như trên máy. */
    private fun <T> withInlineMainHandler(block: () -> T): T =
        mockConstruction(Handler::class.java) { handler, _ ->
            `when`(handler.post(org.mockito.ArgumentMatchers.any())).thenAnswer {
                (it.arguments[0] as Runnable).run()
                true
            }
        }.use { block() }

    /** App load placement, mạng trả quảng cáo: cờ đã load bật, 1 `tryLoad`. */
    private fun loadedTabSwitch(pl: ExpiringPl) {
        helper.loadInterstitialForThisTurn("tab_switch", 0)
        pl.completeLoad()
        assertTrue("sau load cờ đã load phải bật", helper.isInterstitialLoaded("tab_switch"))
        assertEquals(1, pl.tryLoads)
    }

    private fun expiredReasons(capture: ServerEventCapture, count: Int): List<String> =
        capture.await("ad_show_fail", count).map { it.get("reason").asString }

    @Test
    fun `isInterstitialReadyToShow - qua 60 phut - false, ha co da load, nap lai dung mot lan, mot ad_expired`() {
        val pl = withInterstitialNetwork()
        loadedTabSwitch(pl)
        clock.nowMs += sixtyMin - 1
        assertTrue("59 phút còn hạn", helper.isInterstitialReadyToShow("tab_switch"))

        clock.nowMs += 2
        ServerEventCapture().use { server ->
            assertFalse("quá 60 phút không ready", helper.isInterstitialReadyToShow("tab_switch"))
            assertFalse("cờ đã load phải hạ ngay", helper.isInterstitialLoaded("tab_switch"))
            assertEquals("SDK nạp lại đúng một lần", 2, pl.tryLoads)

            assertFalse("gọi lại khi đang load vẫn không ready", helper.isInterstitialReadyToShow("tab_switch"))
            assertEquals("gọi lại khi đang load không nạp thêm", 2, pl.tryLoads)
            assertEquals(listOf(Constants.AdShowFailReason.AD_EXPIRED), expiredReasons(server, 1))
        }
        pl.completeLoad()
        assertTrue("nạp lại xong thì ready", helper.isInterstitialReadyToShow("tab_switch"))
    }

    /** Vòng "ad break" của Speaker Clean `MainActivity.kt:432-545`, mỗi nhịp một lần. */
    private fun adBreakTick(cb: AdsShowCb): String = when {
        helper.isInterstitialReadyToShow("tab_switch") ->
            if (helper.showInterstitial("tab_switch", 0, cb)) "show" else "show_false"
        !helper.isInterstitialLoaded("tab_switch") -> {
            helper.loadInterstitialForThisTurn("full_tab_switch", 0)
            "load"
        }
        else -> "wait"
    }

    @Test
    fun `mau ad break Speaker Clean chay tiep sau lan het han dau tien`() {
        val pl = withInterstitialNetwork()
        loadedTabSwitch(pl)
        clock.nowMs += sixtyMin + 1
        val cb = RecordingShowCb()

        val ticks = mutableListOf<String>()
        ticks += adBreakTick(cb)      // hết hạn: ready false, cờ đã hạ → app load (SDK đã nạp, lần này "loading")
        pl.completeLoad()             // mạng trả quảng cáo mới
        ticks += adBreakTick(cb)      // ready → show

        assertEquals(listOf("load", "show"), ticks)
        assertEquals(listOf("showed"), cb.events)
        assertEquals("1 lần load đầu + 1 lần SDK nạp lại; lần load của app gộp vào lần đang chạy", 2, pl.tryLoads)
    }

    @Test
    fun `showInterstitial - quang cao qua 60 phut - false, ha co da load, nap lai mot lan`() {
        val pl = withInterstitialNetwork()
        loadedTabSwitch(pl)
        clock.nowMs += sixtyMin + 1

        ServerEventCapture().use { server ->
            assertFalse(helper.showInterstitial("tab_switch", 0, RecordingShowCb()))
            assertFalse("cờ đã load phải hạ", helper.isInterstitialLoaded("tab_switch"))
            assertEquals(2, pl.tryLoads)
            assertTrue(Constants.AdShowFailReason.AD_EXPIRED in expiredReasons(server, 1))
        }
    }

    @Test
    fun `getNativeFullForShow - native full qua 60 phut - null, ha co da load, nap lai dung mot lan`() {
        // Native full đứng đầu danh sách của getNativeFullForShow (flagShow 1: AdMob native full trước).
        val pl = withInterstitialNetwork(AdsIDDef.AdmobFullNt)
        helper.loadNativeFullForThisTurn("home")
        pl.completeLoad()
        assertTrue(helper.isInterstitialLoaded("home"))
        clock.nowMs += sixtyMin + 1

        ServerEventCapture().use { server ->
            assertNull(helper.getNativeFullForShow("home", null))
            assertFalse("cờ đã load phải hạ ngay", helper.isInterstitialLoaded("home"))
            assertEquals("SDK nạp lại native full đúng một lần", 2, pl.tryLoads)

            assertNull(helper.getNativeFullForShow("home", null))
            assertEquals("gọi lại khi đang load không nạp thêm", 2, pl.tryLoads)
            assertEquals(listOf(Constants.AdShowFailReason.AD_EXPIRED), expiredReasons(server, 1))
        }
    }

    @Test
    fun `showRewarded - quang cao qua 60 phut - false, ha co da load cua placement, nap lai mot lan`() {
        val pl = ExpiringPl(context, PlacementDefault.PLGiftDefault, clock)
        val network = GiftNetwork(context, pl)
        helper.currConfig.giftStepCicle.add(AdsIDDef.AdmobRewarded)
        @Suppress("UNCHECKED_CAST")
        (field("listAdsGift") as MutableMap<Int, AdsBaseGift>)[AdsIDDef.AdmobRewarded] = network
        @Suppress("UNCHECKED_CAST")
        (field("listAdsAll") as MutableList<AdsBase>).add(network)
        helper.setMainActivity(activity)
        helper.loadRewardedForThisTurn("coins")
        pl.completeLoad()
        val plcf = helper.getAdsPlacementConfig("gift_coins", 1)!!
        assertTrue(plcf.isLoaded)
        clock.nowMs += sixtyMin + 1

        assertFalse(helper.showRewarded("coins", RecordingShowCb()))

        assertFalse("cờ đã load của placement rewarded phải hạ", plcf.isLoaded)
        assertEquals(2, pl.tryLoads)
    }

    /** isInterstitialReadyToShow có side effect (bỏ quảng cáo, destroy native full, gửi event): chỉ gọi trên main. */
    @Test
    fun `isInterstitialReadyToShow goi ngoai main - logE mot lan, van xu ly khong crash`() {
        val pl = withInterstitialNetwork()
        loadedTabSwitch(pl)
        clock.nowMs += sixtyMin + 1
        val main = mock(Looper::class.java)
        val worker = mock(Looper::class.java)

        mockStatic(Looper::class.java).use { looper ->
            looper.`when`<Looper> { Looper.getMainLooper() }.thenReturn(main)
            looper.`when`<Looper> { Looper.myLooper() }.thenReturn(worker)
            mockStatic(Log::class.java).use { log ->
                assertFalse(helper.isInterstitialReadyToShow("tab_switch"))
                assertFalse(helper.isInterstitialReadyToShow("tab_switch"))
                log.verify({ Log.e(eq(LogUtil.TAG), contains("main thread")) }, times(1))
            }
        }
        assertFalse("vẫn xử lý: cờ đã load hạ", helper.isInterstitialLoaded("tab_switch"))
        assertEquals("vẫn xử lý: nạp lại một lần", 2, pl.tryLoads)
    }

    /**
     * Review EF Q1: `full_home` và `full_tab_switch` dùng chung slot `full_default` của mỗi mạng. Show `home` xong,
     * SDK nạp lại `full_default` (slot của một mạng đang load, mạng nào tuỳ vòng xoay `fullIdxShow`); cờ đã load
     * của `tab_switch` còn bật từ lần load trước. Ready check của `tab_switch` lúc đó không được hạ cờ và nạp lại:
     * lần nạp đó gặp "isloading" ở slot đang load rồi tải thêm ở mạng kia, một request thừa mỗi lần show mà
     * quảng cáo của nó nằm chờ tới `ad_expired`.
     */
    @Test
    fun `slot chung dang nap lai sau show - ready check khong ha co, khong tai mang kia, khong ad_expired`() {
        val nets = withInterstitialNetworks(AdsIDDef.AdmobFullInters, AdsIDDef.MaxFullInters)
        val first = nets[0]
        fun totalLoads() = nets.sumOf { it.tryLoads }
        loadedTabSwitch(first)
        helper.loadInterstitialForThisTurn("home", 0)
        assertTrue("slot chung đã có quảng cáo: home bật cờ, không tải mới", helper.isInterstitialLoaded("home"))
        assertEquals(1, totalLoads())
        val cb = RecordingShowCb()
        assertTrue(helper.showInterstitial("home", 0, cb))
        first.dismiss()
        assertEquals(listOf("showed", "dismissed"), cb.events)
        assertEquals("dismiss nạp lại full_default: slot của một mạng đang load", 2, totalLoads())
        helper.fullTimeShow = 0L // qua khoảng cách tối thiểu giữa hai interstitial, slot vẫn đang load

        ServerEventCapture().use { server ->
            withInlineMainHandler {
                assertFalse("slot đang load: chưa ready", helper.isInterstitialReadyToShow("tab_switch"))
            }
            assertTrue("slot đang load: cờ đã load không hạ", helper.isInterstitialLoaded("tab_switch"))
            assertEquals("không tải thừa ở mạng nào", 2, totalLoads())
            assertFalse(Constants.AdShowFailReason.AD_EXPIRED in expiredReasons(server, 1))
        }
        nets.forEach { it.completeLoad() }
        assertTrue("slot load xong thì tab_switch ready", helper.isInterstitialReadyToShow("tab_switch"))
        assertEquals(2, totalLoads())
    }

    /**
     * Review EF Q3: cờ đã load là của placement, không của `flagImage`. App load `home` dạng native full rồi hỏi
     * ready với `flagImage` mặc định 0 (danh sách interstitial): native full vẫn còn đó nên không được hạ cờ và nạp
     * interstitial.
     */
    @Test
    fun `native full da load - ready check interstitial flagImage 0 - khong ha co, khong nap interstitial`() {
        val (inters, ntFull) = withInterstitialNetworks(AdsIDDef.AdmobFullInters, AdsIDDef.AdmobFullNt)
        helper.currConfig.fullStepCicle.remove(AdsIDDef.AdmobFullNt) // waterfall interstitial chỉ có AdMob inters
        helper.loadNativeFullForThisTurn("home")
        ntFull.completeLoad()
        assertTrue(helper.isInterstitialLoaded("home"))

        withInlineMainHandler {
            assertFalse("danh sách flagImage 0 không có quảng cáo", helper.isInterstitialReadyToShow("home"))
        }

        assertTrue("native full còn: cờ đã load không hạ", helper.isInterstitialLoaded("home"))
        assertEquals("không nạp interstitial", 0, inters.tryLoads)
        assertEquals(1, ntFull.tryLoads)
        assertTrue("đúng flagImage thì ready", helper.isInterstitialReadyToShow("home", 1))
    }
}
