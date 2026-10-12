package com.app.plugin.ads.adsmax

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementEcmpBase
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.until.LogUtil
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.isNull
import org.mockito.MockedStatic
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 1.5.0 Task 14 (final review I–M Minor 7): năm lớp MAX tạo đối tượng quảng cáo một lần (`MaxInterstitialAd`,
 * `MaxRewardedAd`, `MaxNativeAdLoader` của Nt/NtCl/NtFull) và listener chụp `ecmpLoad` của lần `tryLoad` đầu. Khi
 * `parserEcpms` đổi danh sách ad unit, đối tượng dùng lại vẫn request unit cũ và ghi kết quả vào mục đã rời danh sách.
 * Nay đối tượng gắn với mục waterfall (`boundEcmp`): mục khác thì dựng/lấy đối tượng cho unit của mục đó; cùng mục
 * thì dùng lại (create-once giữ). Đường production là bước waterfall sau của placement nhiều id MAX (danh sách MAX
 * chỉ `parserEcpms` lúc dựng). Đổi mục (Task 14 bước B, review Task 14 I1–I2): interstitial gỡ listener + `destroy()`
 * đối tượng cũ, đang hiện thì hoãn (log "rebind deferred"); native giữ một `MaxNativeAdLoader` mỗi unit, không gỡ
 * listener, không destroy loader cũ (MAX đọc listener của loader lúc click/trả doanh thu — quảng cáo nó đã render còn
 * trên màn hình); rewarded lấy `MaxRewardedAd.getInstance` của unit mới, không gỡ/destroy instance cũ (singleton theo
 * unit, placement khác có thể đang giữ nó), đang hiện thì hoãn. Callback load đã xếp hàng của đối tượng cũ bị bỏ (cờ
 * `live`; native kèm `destroyAd`).
 */
class AdsMaxCreateOnceRebindTest {

    private val unitA = "max-unit-A"
    private val unitB = "max-unit-B"

    private class LoadCb : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded:$adId"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "fail"
        }
    }

    private lateinit var context: Context
    private val activity = mock(Activity::class.java)
    private val maxError = mock(MaxError::class.java)

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        AdsMaxMy.instance.readyQueue.resetForTest(); AdsMaxMy.instance.readyQueue.markReady()
        AdsHelper.instance.setFlagAdClick(0)
    }

    @After
    fun tearDown() {
        AdsMaxMy.instance.readyQueue.resetForTest()
        AdsHelper.instance.setFlagAdClick(0)
        LogUtil.setFlagLog(1)
    }

    private fun maxAd(unit: String): MaxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn(unit) }

    /** Mục trong danh sách hiện tại theo unit id (null nếu đã rời danh sách). */
    private fun entry(state: AdsPlacementEcmpBase, unit: String): AdsEcmp? = state.adEcmps!!.list.firstOrNull { it.adsId == unit }

    /** Bắt log `LogUtil.logD` (qua `android.util.Log.d`) trong [block]. */
    private fun <T> captureLogs(block: (MutableList<String>) -> T): T {
        val logs = mutableListOf<String>()
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java).use { log: MockedStatic<Log> ->
            log.`when`<Int> { Log.d(anyString(), anyString()) }.thenAnswer { logs += it.arguments[1] as String; 0 }
            return block(logs)
        }
    }

    // ---------------------------------------------------------------- interstitial
    // Listener chỉ ghi khi khác null (lần gỡ `setListener(null)` không phải listener): chỉ số listener = chỉ số đối tượng.

    private class IntersHarness(val pl: AdsMaxPlInters) {
        val listeners = mutableListOf<MaxAdListener>()
        val units = mutableListOf<String>()
    }

    private fun <T> inters(block: (IntersHarness, List<MaxInterstitialAd>) -> T): T {
        var harness: IntersHarness? = null
        return mockConstruction(MaxInterstitialAd::class.java) { ad, ctx ->
            harness!!.units += ctx.arguments()[0] as String
            doAnswer { (it.arguments[0] as? MaxAdListener)?.let { l -> harness!!.listeners += l }; null }.`when`(ad).setListener(any())
            `when`(ad.isReady).thenReturn(true)
        }.use { built ->
            val pl = AdsMaxPlInters(context, mock(AdsBase::class.java), AdsIDDef.MaxFullInters, "full_default")
            pl.setActivity(activity)
            harness = IntersHarness(pl)
            block(harness!!, built.constructed())
        }
    }

    /** Lượt 1 trên [first] lỗi hết waterfall; rồi Remote Config đổi danh sách thành [next]. Trả mục của [first]. */
    private fun failThenReconfigure(pl: AdsPlBase, first: String, next: String, fail: () -> Unit): AdsEcmp {
        pl.parserEcpms(first)
        val firstEntry = entry(LoadedAdFixture.state(pl), first)!!
        val cb1 = LoadCb()
        pl.load("pl_home", cb1)
        fail()
        assertEquals("lượt 1 báo fail đúng một lần", listOf("fail"), cb1.events)
        assertFalse(LoadedAdFixture.state(pl).isLoading)
        pl.parserEcpms(next)
        return firstEntry
    }

    @Test
    fun `interstitial - parserEcpms doi unit thi luot load ke dung doi tuong moi cho unit moi, doi tuong cu go listener va destroy`() =
        inters { h, built ->
            val state = LoadedAdFixture.state(h.pl)
            val entryA = failThenReconfigure(h.pl, unitA, unitB) { h.listeners[0].onAdLoadFailed(unitA, maxError) }
            assertEquals(listOf(unitA), h.units)
            assertNull("A đã rời danh sách", entry(state, unitA))

            val cb2 = LoadCb()
            h.pl.load("pl_home", cb2)

            assertEquals("dựng đối tượng mới cho unit B", listOf(unitA, unitB), h.units)
            val old = built[0]
            verify(old).setListener(isNull())
            verify(old).setRevenueListener(isNull())
            verify(old, times(1)).destroy()
            verify(built[1], times(1)).loadAd()
            verify(built[1], never()).destroy()

            h.listeners[1].onAdLoaded(maxAd(unitB))
            val b = entry(state, unitB)!!
            assertTrue("mục B nhận kết quả", b.isLoaded)
            assertSame(built[1], b.adObject)
            assertEquals(listOf("loaded:$unitB"), cb2.events)
            assertFalse("mục A đã rời danh sách không nhận kết quả", entryA.isLoaded)
            assertNull(entryA.adObject)
        }

    @Test
    fun `interstitial - callback muon da xep hang cua doi tuong cu khong toi muc moi va app`() = inters { h, built ->
        val state = LoadedAdFixture.state(h.pl)
        failThenReconfigure(h.pl, unitA, unitB) { h.listeners[0].onAdLoadFailed(unitA, maxError) }
        val cb2 = LoadCb()
        h.pl.load("pl_home", cb2)
        assertEquals(2, built.size)
        assertTrue("request B đang chờ", state.isLoading)
        val b = entry(state, unitB)!!

        // MAX có thể giao callback đã xếp hàng trên main trước setListener(null): gọi thẳng listener đã chụp.
        h.listeners[0].onAdLoaded(maxAd(unitA))
        h.listeners[0].onAdLoadFailed(unitA, maxError)
        assertEquals("app không nhận gì từ đối tượng cũ", emptyList<String>(), cb2.events)
        assertTrue("request B vẫn chờ", state.isLoading)
        assertFalse(b.isLoaded)
        assertNull(b.adObject)
        assertTrue(b.isLoading)
        assertEquals(0, state.adEcmps!!.idxCurrEcpm)

        h.listeners[1].onAdLoaded(maxAd(unitB))
        assertEquals(listOf("loaded:$unitB"), cb2.events)
        assertSame(built[1], b.adObject)

        h.listeners[0].onAdLoaded(maxAd(unitA))
        assertEquals("slot rảnh: vẫn không tới app", listOf("loaded:$unitB"), cb2.events)
        assertSame(built[1], b.adObject)
    }

    @Test
    fun `interstitial - cung muc hai lan thi dung lai doi tuong, khong destroy`() = inters { h, built ->
        h.pl.parserEcpms(unitA)
        val cb1 = LoadCb()
        h.pl.load("pl_home", cb1)
        h.listeners[0].onAdLoadFailed(unitA, maxError)
        assertEquals(listOf("fail"), cb1.events)
        val cb2 = LoadCb()
        h.pl.load("pl_home", cb2)
        assertEquals("create-once giữ cho cùng mục", 1, built.size)
        verify(built[0], times(2)).loadAd()
        verify(built[0], never()).destroy()
        h.listeners[0].onAdLoaded(maxAd(unitA))
        assertEquals(listOf("loaded:$unitA"), cb2.events)
    }

    @Test
    fun `interstitial - dang hien thi hoan dung lai, luot load di voi doi tuong cu`() = inters { h, built ->
        captureLogs { logs ->
            val state = LoadedAdFixture.state(h.pl)
            failThenReconfigure(h.pl, unitA, unitB) { h.listeners[0].onAdLoadFailed(unitA, maxError) }
            state.isShowing = true
            h.pl.load("pl_home", LoadCb())
            assertEquals("không dựng đối tượng mới khi đang hiện", 1, built.size)
            verify(built[0], never()).destroy()
            verify(built[0], never()).setListener(isNull())
            verify(built[0], times(2)).loadAd()
            assertTrue(logs.any { it.contains("rebind deferred") && it.contains("$unitA → $unitB") })
        }
    }

    /** Mục đã được lấy ra hiện (app tiêu quảng cáo): mục và slot không còn giữ quảng cáo. */
    private fun consume(state: AdsPlacementEcmpBase) {
        state.adEcmps!!.list.forEach { it.isLoaded = false; it.adObject = null }
        state.isloaded = false
    }

    @Test
    fun `interstitial - danh sach A-B khong parserEcpms giua chung - moi buoc request unit cua muc do, ket qua vao dung muc`() =
        inters { h, built ->
            h.pl.parserEcpms("$unitA;$unitB")
            val state = LoadedAdFixture.state(h.pl)
            val a = entry(state, unitA)!!
            val b = entry(state, unitB)!!
            val cb1 = LoadCb()
            h.pl.load("pl_home", cb1)
            h.listeners[0].onAdLoadFailed(unitA, maxError)
            assertEquals("bước 2 dựng đối tượng cho unit B", listOf(unitA, unitB), h.units)
            verify(built[1], times(1)).loadAd()

            h.listeners[1].onAdLoaded(maxAd(unitB))
            assertTrue("mục B nhận kết quả", b.isLoaded)
            assertSame(built[1], b.adObject)
            assertFalse("mục A trống", a.isLoaded)
            assertNull(a.adObject)
            assertFalse(a.isLoading)
            assertFalse("mục B không kẹt đang load", b.isLoading)
            assertEquals("app đúng một loaded", listOf("loaded:$unitB"), cb1.events)

            // Lượt kế: A lỗi lần nữa → bước B request thật (không "isloading"), kết quả vào B.
            consume(state)
            val cb2 = LoadCb()
            h.pl.load("pl_home", cb2)
            h.listeners.last().onAdLoadFailed(unitA, maxError)
            assertEquals("B không kẹt: không báo isloading", emptyList<String>(), cb2.events)
            assertTrue("request B đang chờ", b.isLoading)
            assertEquals(listOf(unitA, unitB, unitA, unitB), h.units)
            h.listeners.last().onAdLoaded(maxAd(unitB))
            assertEquals(listOf("loaded:$unitB"), cb2.events)
            assertSame(built.last(), b.adObject)
            assertFalse(state.isLoading)
        }

    // ---------------------------------------------------------------- rewarded

    private class RewardedHarness(val pl: AdsMaxPlrewarded) {
        val listeners = mutableListOf<MaxRewardedAdListener>()
        val units = mutableListOf<String>()
        val built = mutableListOf<MaxRewardedAd>()
    }

    private fun <T> rewarded(block: (RewardedHarness) -> T): T =
        mockStatic(MaxRewardedAd::class.java).use { statics ->
            val pl = AdsMaxPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.MaxRewarded, "gift_default")
            pl.setActivity(activity)
            val h = RewardedHarness(pl)
            statics.`when`<MaxRewardedAd> { MaxRewardedAd.getInstance(anyString(), any(Context::class.java)) }.thenAnswer { inv ->
                val ad = mock(MaxRewardedAd::class.java)
                doAnswer { (it.arguments[0] as? MaxRewardedAdListener)?.let { l -> h.listeners += l }; null }.`when`(ad).setListener(any())
                `when`(ad.isReady).thenReturn(true)
                h.units += inv.arguments[0] as String
                h.built += ad
                ad
            }
            block(h)
        }

    @Test
    fun `rewarded - parserEcpms doi unit thi luot load ke lay instance cho unit moi, khong go khong destroy instance cu`() =
        rewarded { h ->
            val state = LoadedAdFixture.state(h.pl)
            failThenReconfigure(h.pl, unitA, unitB) { h.listeners[0].onAdLoadFailed(unitA, maxError) }
            val cb2 = LoadCb()
            h.pl.load("pl_home", cb2)

            assertEquals(listOf(unitA, unitB), h.units)
            verify(h.built[0], never()).setListener(isNull())
            verify(h.built[0], never()).setRevenueListener(isNull())
            verify(h.built[0], never()).destroy()
            verify(h.built[1], times(1)).loadAd()

            h.listeners[0].onAdLoaded(maxAd(unitA))
            assertEquals("callback muộn của instance cũ không tới app", emptyList<String>(), cb2.events)
            h.listeners[1].onAdLoaded(maxAd(unitB))
            val b = entry(state, unitB)!!
            assertTrue(b.isLoaded)
            assertSame(h.built[1], b.adObject)
            assertEquals(listOf("loaded:$unitB"), cb2.events)
        }

    @Test
    fun `rewarded - cung muc hai lan thi dung lai instance, khong destroy`() = rewarded { h ->
        h.pl.parserEcpms(unitA)
        h.pl.load("pl_home", LoadCb())
        h.listeners[0].onAdLoadFailed(unitA, maxError)
        h.pl.load("pl_home", LoadCb())
        assertEquals(listOf(unitA), h.units)
        verify(h.built[0], times(2)).loadAd()
        verify(h.built[0], never()).destroy()
    }

    @Test
    fun `rewarded - dang hien thi hoan dung lai`() = rewarded { h ->
        captureLogs { logs ->
            failThenReconfigure(h.pl, unitA, unitB) { h.listeners[0].onAdLoadFailed(unitA, maxError) }
            LoadedAdFixture.state(h.pl).isShowing = true
            h.pl.load("pl_home", LoadCb())
            assertEquals(listOf(unitA), h.units)
            verify(h.built[0], never()).destroy()
            verify(h.built[0], times(2)).loadAd()
            assertTrue(logs.any { it.contains("rebind deferred") })
        }
    }

    @Test
    fun `rewarded - hai placement dung chung unit U, placement 2 doi muc khoi U thi khong go khong destroy instance U`() =
        mockStatic(MaxRewardedAd::class.java).use { statics ->
            val unitU = "max-unit-U"
            val unitX = "max-unit-X"
            // Singleton theo unit như AppLovin: cùng unit → cùng đối tượng.
            val byUnit = HashMap<String, MaxRewardedAd>()
            val listenersOf = HashMap<MaxRewardedAd, MutableList<MaxRewardedAdListener>>()
            statics.`when`<MaxRewardedAd> { MaxRewardedAd.getInstance(anyString(), any(Context::class.java)) }.thenAnswer { inv ->
                byUnit.getOrPut(inv.arguments[0] as String) {
                    mock(MaxRewardedAd::class.java).also { ad ->
                        val ls = mutableListOf<MaxRewardedAdListener>().also { listenersOf[ad] = it }
                        doAnswer { (it.arguments[0] as? MaxRewardedAdListener)?.let { l -> ls += l }; null }.`when`(ad).setListener(any())
                        `when`(ad.isReady).thenReturn(true)
                    }
                }
            }
            val p1 = AdsMaxPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.MaxRewarded, "gift_default").also { it.setActivity(activity) }
            val p2 = AdsMaxPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.MaxRewarded, "gift_premium").also { it.setActivity(activity) }
            p1.parserEcpms(unitU)
            p2.parserEcpms("$unitX;$unitU")

            // Placement 1 giữ quảng cáo đã load của U.
            val cbP1 = LoadCb()
            p1.load("pl_home", cbP1)
            val u = byUnit.getValue(unitU)
            listenersOf.getValue(u).last().onAdLoaded(maxAd(unitU))
            assertEquals(listOf("loaded:$unitU"), cbP1.events)
            val p1Entry = entry(LoadedAdFixture.state(p1), unitU)!!
            assertSame(u, p1Entry.adObject)

            // Placement 2: X lỗi → U; U lỗi → hết lượt; lượt kế về X (đổi mục khỏi U).
            val cb1 = LoadCb()
            p2.load("pl_home", cb1)
            val x = byUnit.getValue(unitX)
            listenersOf.getValue(x).last().onAdLoadFailed(unitX, maxError)
            listenersOf.getValue(u).last().onAdLoadFailed(unitU, maxError)
            assertEquals(listOf("fail"), cb1.events)
            p2.load("pl_home", LoadCb())
            verify(x, times(2)).loadAd()

            verify(u, never()).destroy()
            verify(u, never()).setListener(isNull())
            verify(u, never()).setRevenueListener(isNull())
            verify(x, never()).destroy()
            assertTrue("mục U của placement 1 vẫn giữ quảng cáo", p1Entry.isLoaded)
            assertSame(u, p1Entry.adObject)
        }

    // ---------------------------------------------------------------- native (Nt, NtCl, NtFull)

    private class NativeHarness(val pl: AdsPlBase) {
        val listeners = mutableListOf<MaxNativeAdListener>()
        val units = mutableListOf<String>()
    }

    private fun <T> native(make: () -> AdsPlBase, block: (NativeHarness, List<MaxNativeAdLoader>) -> T): T {
        var harness: NativeHarness? = null
        return mockConstruction(MaxNativeAdLoader::class.java) { loader, ctx ->
            harness!!.units += ctx.arguments()[0] as String
            doAnswer { (it.arguments[0] as? MaxNativeAdListener)?.let { l -> harness!!.listeners += l }; null }.`when`(loader).setNativeAdListener(any())
        }.use { built ->
            val pl = make()
            pl.setActivity(activity)
            harness = NativeHarness(pl)
            block(harness!!, built.constructed())
        }
    }

    private fun nt() = AdsMaxPlNt(context, mock(AdsBase::class.java), AdsIDDef.MaxNative, "native_default")
    private fun ntCl() = AdsMaxPlNtCl(context, mock(AdsBase::class.java), AdsIDDef.MaxNtCl, "ntcl_default")
    private fun ntFull() = AdsMaxPlNtFull(context, mock(AdsBase::class.java), AdsIDDef.MaxFullNt, "full_2_default")

    /**
     * Kịch bản (1) cho một lớp native: loader mới cho unit mới, loader cũ không gỡ listener, không destroy (review Task 14
     * I1); callback muộn của loader cũ bị bỏ và ad của nó destroy qua loader hiện tại (review Task 14 m2).
     */
    private fun nativeRebind(make: () -> AdsPlBase) = native(make) { h, built ->
        val state = LoadedAdFixture.state(h.pl)
        failThenReconfigure(h.pl, unitA, unitB) { h.listeners[0].onNativeAdLoadFailed(unitA, maxError) }
        val cb2 = LoadCb()
        h.pl.load("pl_home", cb2)

        assertEquals("dựng loader mới cho unit B", listOf(unitA, unitB), h.units)
        verify(built[0], never()).setNativeAdListener(isNull())
        verify(built[0], never()).setRevenueListener(isNull())
        verify(built[0], never()).destroy()
        verify(built[1], times(1)).loadAd()

        val late = maxAd(unitA)
        h.listeners[0].onNativeAdLoaded(null, late)
        assertEquals("callback muộn của loader cũ không tới app", emptyList<String>(), cb2.events)
        assertTrue("request B vẫn chờ", state.isLoading)
        verify(built[1]).destroy(late)

        val ad = maxAd(unitB)
        h.listeners[1].onNativeAdLoaded(null, ad)
        val b = entry(state, unitB)!!
        assertTrue("mục B nhận kết quả", b.isLoaded)
        assertSame(ad, b.adObject)
        assertEquals(listOf("loaded:$unitB"), cb2.events)
        verify(built[1], never()).destroy()
    }

    @Test
    fun `native Nt - parserEcpms doi unit thi dung loader moi, loader cu khong go listener, khong destroy`() = nativeRebind(::nt)

    @Test
    fun `native NtCl - parserEcpms doi unit thi dung loader moi, loader cu khong go listener, khong destroy`() = nativeRebind(::ntCl)

    @Test
    fun `native NtFull - parserEcpms doi unit thi dung loader moi, loader cu khong go listener, khong destroy`() = nativeRebind(::ntFull)

    /** Kịch bản (2) cho một lớp native: cùng mục hai lần → dùng lại loader (create-once giữ). */
    private fun nativeSameEntry(make: () -> AdsPlBase) = native(make) { h, built ->
        h.pl.parserEcpms(unitA)
        h.pl.load("pl_home", LoadCb())
        h.listeners[0].onNativeAdLoadFailed(unitA, maxError)
        h.pl.load("pl_home", LoadCb())
        assertEquals(1, built.size)
        verify(built[0], times(2)).loadAd()
        verify(built[0], never()).destroy()
    }

    /**
     * Kịch bản (3) cho một lớp native: đang hiện vẫn đổi loader (đổi mục không còn chạm quảng cáo đang hiện, bước B bỏ
     * nhánh "rebind deferred"); loader cũ không bị gỡ listener, không destroy.
     */
    private fun nativeDeferred(make: () -> AdsPlBase) = native(make) { h, built ->
        captureLogs { logs ->
            failThenReconfigure(h.pl, unitA, unitB) { h.listeners[0].onNativeAdLoadFailed(unitA, maxError) }
            LoadedAdFixture.state(h.pl).isShowing = true
            h.pl.load("pl_home", LoadCb())
            assertEquals("dựng loader cho unit B dù đang hiện", listOf(unitA, unitB), h.units)
            verify(built[0], never()).destroy()
            verify(built[0], never()).setNativeAdListener(isNull())
            verify(built[0], never()).setRevenueListener(isNull())
            verify(built[0], times(1)).loadAd()
            verify(built[1], times(1)).loadAd()
            assertFalse(logs.any { it.contains("rebind deferred") })
        }
    }

    /**
     * Test (a) bước B cho một lớp native, đường production (danh sách `"A;B"`, không `parserEcpms` giữa chừng): ad1 của
     * loader A đã lấy ra hiện; lượt 2 A no-fill → bước B dựng loader B, loader A giữ nguyên (không gỡ listener, không
     * destroy) nên click trên ad1 vẫn tới SDK; lượt 3 về A dùng lại loader A (một loader mỗi unit).
     */
    private fun nativeWaterfall(make: () -> AdsPlBase) = native(make) { h, built ->
        h.pl.parserEcpms("$unitA;$unitB")
        val state = LoadedAdFixture.state(h.pl)
        val a = entry(state, unitA)!!
        val b = entry(state, unitB)!!

        val cb1 = LoadCb()
        h.pl.load("pl_home", cb1)
        val ad1 = maxAd(unitA)
        h.listeners[0].onNativeAdLoaded(null, ad1)
        assertEquals(listOf("loaded:$unitA"), cb1.events)
        val loaderA = built[0]
        consume(state) // ad1 đã lấy ra hiện, giữ ở test (như trên màn hình)

        val cb2 = LoadCb()
        h.pl.load("pl_home", cb2)
        h.listeners[0].onNativeAdLoadFailed(unitA, maxError)
        assertEquals("bước B dựng loader B", listOf(unitA, unitB), h.units)
        val loaderB = built[1]
        verify(loaderB, times(1)).loadAd()
        verify(loaderA, never()).destroy()
        verify(loaderA, never()).setNativeAdListener(isNull())
        verify(loaderA, never()).setRevenueListener(isNull())
        assertTrue(b.isLoading)

        // Click trên ad1 (loader A đã render) vẫn qua listener của loader A.
        h.listeners[0].onNativeAdClicked(ad1)
        assertTrue("click đặt cờ chặn app open", AdsHelper.instance.isClickAds())

        h.listeners[1].onNativeAdLoadFailed(unitB, maxError)
        assertEquals(listOf("fail"), cb2.events)

        // Lượt 3 về A: dùng lại loader A, không dựng loader thứ ba.
        val cb3 = LoadCb()
        h.pl.load("pl_home", cb3)
        assertEquals("một loader mỗi unit", 2, built.size)
        assertEquals(listOf(unitA, unitB), h.units)
        verify(loaderA, times(3)).loadAd()
        verify(loaderB, never()).destroy()
        val lateB = maxAd(unitB)
        h.listeners[1].onNativeAdLoaded(null, lateB)
        verify(loaderA).destroy(lateB)
        assertEquals("callback muộn của loader B không tới app", emptyList<String>(), cb3.events)
        val ad3 = maxAd(unitA)
        h.listeners.last().onNativeAdLoaded(null, ad3)
        assertEquals(listOf("loaded:$unitA"), cb3.events)
        assertSame(ad3, a.adObject)
        assertFalse(b.isLoaded)
    }

    @Test
    fun `native Nt - cung muc hai lan thi dung lai loader, khong destroy`() = nativeSameEntry(::nt)

    @Test
    fun `native NtCl - cung muc hai lan thi dung lai loader, khong destroy`() = nativeSameEntry(::ntCl)

    @Test
    fun `native NtFull - cung muc hai lan thi dung lai loader, khong destroy`() = nativeSameEntry(::ntFull)

    @Test
    fun `native Nt - dang hien thi van doi loader, loader cu khong bi go`() = nativeDeferred(::nt)

    @Test
    fun `native NtCl - dang hien thi van doi loader, loader cu khong bi go`() = nativeDeferred(::ntCl)

    @Test
    fun `native NtFull - dang hien thi van doi loader, loader cu khong bi go`() = nativeDeferred(::ntFull)

    @Test
    fun `native Nt - danh sach A-B, buoc B khong destroy loader A dang co ad hien, luot sau dung lai loader A`() = nativeWaterfall(::nt)

    @Test
    fun `native NtCl - danh sach A-B, buoc B khong destroy loader A dang co ad hien, luot sau dung lai loader A`() = nativeWaterfall(::ntCl)

    @Test
    fun `native NtFull - danh sach A-B, buoc B khong destroy loader A dang co ad hien, luot sau dung lai loader A`() = nativeWaterfall(::ntFull)
}
