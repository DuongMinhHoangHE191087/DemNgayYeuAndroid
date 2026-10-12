package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsfb.AdsFbPlBanner
import com.app.plugin.ads.adsfb.AdsFbPlRect
import com.app.plugin.ads.adsmax.AdsMaxMy
import com.app.plugin.ads.adsmax.AdsMaxPlBanner
import com.app.plugin.ads.adsmax.AdsMaxPlRect
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlBanner
import com.app.plugin.ads.adsmob.AdsMobPlRect
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.sdk.AppLovinSdkUtils
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task J1: 6 listener banner/rect (GMA, MAX, Meta) tách "response của request đang chờ" (callback đầu tiên của listener)
 * khỏi "auto-refresh của view" (các callback sau). Response đầu tiên chụp [AdsPlacementEcmpBase.loadGeneration] lúc gửi:
 * generation đã đổi (watchdog slot 60 s đã bỏ lượt, lượt mới đang chạy) thì không tới `adsLoadCb` của lượt mới — view
 * muộn giữ làm quảng cáo đã load nếu mục rảnh và trống ([AdsPlBase.keepLateAd]), không thì destroy. Refresh của view
 * đang hiện vẫn `onLoaded` mỗi lần như cũ. Một mạng mỗi loại: GMA banner, MAX rect, Meta banner.
 */
class LateBannerLoadCallbackTest {

    private val first = "ca-app-pub-3940256099942544/first"
    private val second = "ca-app-pub-3940256099942544/second"

    private class Callback : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "fail:$error"
        }
    }

    private lateinit var context: Context
    private val activity = mock(Activity::class.java)

    private fun admobQueue(): SdkReadyQueue =
        AdsMobMy::class.java.getDeclaredField("readyQueue").apply { isAccessible = true }.get(AdsMobMy.instance) as SdkReadyQueue

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        admobQueue().resetForTest(); admobQueue().markReady()
        AdsMaxMy.instance.readyQueue.resetForTest(); AdsMaxMy.instance.readyQueue.markReady()
        AdsFbMy.instance.readyQueue.resetForTest(); AdsFbMy.instance.readyQueue.markReady()
    }

    @After
    fun tearDown() {
        admobQueue().resetForTest()
        AdsMaxMy.instance.readyQueue.resetForTest()
        AdsFbMy.instance.readyQueue.resetForTest()
    }

    // ═══ GMA banner: AdView + AdListener, view mới mỗi request ═══

    private fun <T> withGmaBanner(block: (built: org.mockito.MockedConstruction<AdView>, listeners: MutableList<AdListener>) -> T): T {
        val listeners = mutableListOf<AdListener>()
        return mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }.use {
            mockConstruction(AdView::class.java) { view, _ ->
                doAnswer { listeners += it.arguments[0] as AdListener; null }.`when`(view).adListener = any()
                `when`(view.adUnitId).thenReturn(first)
            }.use { built -> block(built, listeners) }
        }
    }

    /**
     * Request 1 → slot hết hạn (cb1 `load timeout`) → request 2 đang chạy → response muộn của request 1 (thành công rồi
     * lỗi) không tới cb2, view muộn bị destroy (mục đang có request 2), không đổi slot/waterfall; request 2 trả lời →
     * cb2 một `loaded`; view 2 auto-refresh → cb2 thêm một `loaded` nữa (đường refresh như cũ).
     */
    @Test
    fun `GMA banner - response muon cua request da bo khong toi luot moi, refresh van onLoaded`() = MainHandlerTimers.run { t ->
        withGmaBanner { built, listeners ->
            val pl = AdsMobPlBanner(context, mock(AdsBase::class.java), AdsIDDef.AdmobBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms("$first;$second")
            val state = LoadedAdFixture.state(pl)
            val cb1 = Callback()
            pl.load("banner_home", cb1)
            assertEquals(1, built.constructed().size)

            t.advanceTo(60_000)
            assertEquals(listOf("fail:load timeout"), cb1.events)
            val cb2 = Callback()
            pl.load("banner_home", cb2)
            assertEquals("request 2 đã gửi", 2, built.constructed().size)
            assertTrue(state.isLoading)
            val late = built.constructed()[0]
            val fresh = built.constructed()[1]

            listeners[0].onAdLoaded()
            assertEquals("thành công muộn không tới lượt mới", emptyList<String>(), cb2.events)
            assertTrue("slot vẫn đang chờ request 2", state.isLoading)
            assertNotSame("view muộn không thay chỗ request 2", late, state.adEcmps!!.list[0].adObject)
            verify(late, times(1)).destroy()

            listeners[0].onAdFailedToLoad(mock(LoadAdError::class.java))
            assertEquals("lỗi muộn không tới lượt mới", emptyList<String>(), cb2.events)
            assertEquals("lỗi muộn không đẩy waterfall của lượt mới", 0, state.adEcmps!!.idxCurrEcpm)
            assertEquals("lỗi muộn không gửi request mới", 2, built.constructed().size)

            listeners[1].onAdLoaded()
            assertEquals(listOf("loaded"), cb2.events)
            assertSame(fresh, state.adEcmps!!.list[0].adObject)
            assertFalse(state.isLoading)
            verify(fresh, never()).destroy()

            listeners[1].onAdLoaded()
            assertEquals("auto-refresh của view đang hiện vẫn onLoaded", listOf("loaded", "loaded"), cb2.events)
            assertEquals(1, cb1.events.size)
        }
    }

    /** Slot rảnh, mục trống: response muộn giữ view làm quảng cáo đã load, không callback; lần load sau trả nó ngay. */
    @Test
    fun `GMA banner - response muon khi slot ranh duoc giu, lan load sau tra ngay khong request moi`() = MainHandlerTimers.run { t ->
        withGmaBanner { built, listeners ->
            val pl = AdsMobPlBanner(context, mock(AdsBase::class.java), AdsIDDef.AdmobBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms("$first;$second")
            val state = LoadedAdFixture.state(pl)
            val cb1 = Callback()
            pl.load("banner_home", cb1)
            t.advanceTo(60_000)
            assertEquals(listOf("fail:load timeout"), cb1.events)

            listeners[0].onAdLoaded()
            assertEquals("không callback thứ hai cho lượt đã bỏ", 1, cb1.events.size)
            assertSame("view muộn giữ làm quảng cáo đã load", built.constructed()[0], state.adEcmps!!.list[0].adObject)
            assertTrue(state.adEcmps!!.list[0].isLoaded)
            verify(built.constructed()[0], never()).destroy()

            val cb2 = Callback()
            pl.load("banner_home", cb2)
            assertEquals(listOf("loaded"), cb2.events)
            assertEquals("không request mới", 1, built.constructed().size)
        }
    }

    // ═══ MAX rect: MaxAdView + MaxAdViewAdListener, view mới mỗi request ═══

    @Test
    fun `MAX rect - response muon cua request da bo khong toi luot moi, refresh van onLoaded`() = MainHandlerTimers.run { t ->
        val listeners = mutableListOf<MaxAdViewAdListener>()
        mockStatic(AppLovinSdkUtils::class.java).use {
            mockConstruction(MaxAdView::class.java) { view, _ ->
                doAnswer { listeners += it.arguments[0] as MaxAdViewAdListener; null }.`when`(view).setListener(any())
                `when`(view.adUnitId).thenReturn("max-rect")
            }.use { built ->
                val pl = AdsMaxPlRect(context, mock(AdsBase::class.java), AdsIDDef.MaxRect, "rect_default")
                pl.setActivity(activity)
                pl.parserEcpms("max-rect-1;max-rect-2")
                val state = LoadedAdFixture.state(pl)
                val cb1 = Callback()
                pl.load("rect_home", cb1)
                assertEquals(1, built.constructed().size)

                t.advanceTo(60_000)
                assertEquals(listOf("fail:load timeout"), cb1.events)
                val cb2 = Callback()
                pl.load("rect_home", cb2)
                assertEquals(2, built.constructed().size)
                assertTrue(state.isLoading)
                val late = built.constructed()[0]
                val fresh = built.constructed()[1]
                val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-rect") }

                listeners[0].onAdLoaded(maxAd)
                assertEquals(emptyList<String>(), cb2.events)
                assertTrue(state.isLoading)
                assertNotSame(late, state.adEcmps!!.list[0].adObject)
                verify(late, times(1)).destroy()

                listeners[0].onAdLoadFailed("max-rect-1", mock(MaxError::class.java))
                assertEquals(emptyList<String>(), cb2.events)
                assertEquals(0, state.adEcmps!!.idxCurrEcpm)
                assertEquals(2, built.constructed().size)

                listeners[1].onAdLoaded(maxAd)
                assertEquals(listOf("loaded"), cb2.events)
                assertSame(fresh, state.adEcmps!!.list[0].adObject)
                assertFalse(state.isLoading)

                listeners[1].onAdLoaded(maxAd)
                assertEquals("auto-refresh vẫn onLoaded", listOf("loaded", "loaded"), cb2.events)
                assertEquals(1, cb1.events.size)
            }
        }
    }

    // ═══ Task J review IMP-1: MAX auto-refresh nạp lại trong cùng MaxAdView, nhánh refresh không được destroy chính nó ═══

    private fun <T> withMaxViews(block: (built: org.mockito.MockedConstruction<MaxAdView>, listeners: MutableList<MaxAdViewAdListener>) -> T): T {
        val listeners = mutableListOf<MaxAdViewAdListener>()
        // `MaxAdFormat.BANNER.getAdaptiveSize(context)` (AdsMaxPlBanner) đọc Display qua WindowManager của Context.
        val windowManager = mock(android.view.WindowManager::class.java)
        `when`(windowManager.defaultDisplay).thenReturn(mock(android.view.Display::class.java))
        `when`(context.getSystemService(Context.WINDOW_SERVICE)).thenReturn(windowManager)
        return mockStatic(AppLovinSdkUtils::class.java).use {
            mockConstruction(MaxAdView::class.java) { view, _ ->
                doAnswer { listeners += it.arguments[0] as MaxAdViewAdListener; null }.`when`(view).setListener(any())
                `when`(view.adUnitId).thenReturn("max-view")
            }.use { built -> block(built, listeners) }
        }
    }

    /**
     * MAX banner: `MaxAdViewAdListener.onAdLoaded` gọi lại trên cùng listener mỗi lần view tự refresh (view không đổi).
     * View đã load và đang hiện (`show`) refresh hai lần → không `destroy()` chính nó, `adObject` vẫn là view đó, app
     * vẫn `onLoaded` mỗi lần refresh. Trước đây nhánh refresh destroy `adObject` (chính view đang hiện) rồi gán lại nó.
     */
    @Test
    fun `MAX banner - auto-refresh cua view dang hien khong destroy chinh no`() = MainHandlerTimers.run { _ ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlBanner(context, mock(AdsBase::class.java), AdsIDDef.MaxBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-bn-1;max-bn-2")
            val state = LoadedAdFixture.state(pl)
            val cb = Callback()
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("banner_home", cb)
            assertEquals(1, built.constructed().size)
            val view = built.constructed()[0]

            listeners[0].onAdLoaded(maxAd)
            assertEquals(listOf("loaded"), cb.events)
            assertSame(view, state.adEcmps!!.list[0].adObject)
            assertTrue(pl.show("banner_home", null, 0))

            listeners[0].onAdLoaded(maxAd)
            listeners[0].onAdLoaded(maxAd)
            verify(view, never()).destroy()
            assertSame("view đang hiện vẫn là quảng cáo đã load", view, state.adEcmps!!.list[0].adObject)
            assertEquals("mỗi lần refresh vẫn onLoaded", listOf("loaded", "loaded", "loaded"), cb.events)
            assertEquals("không request mới", 1, built.constructed().size)
        }
    }

    /**
     * MAX rect: `getAdForShow` bỏ `adObject` khi lấy view ra hiện, nên lần refresh đầu đi nhánh `adObject == null` (gán
     * lại), lần refresh thứ hai mới gặp `adObject === adView` — trước đây destroy chính view đang hiện ở lần đó.
     */
    @Test
    fun `MAX rect - auto-refresh cua view da lay ra hien khong destroy chinh no`() = MainHandlerTimers.run { _ ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlRect(context, mock(AdsBase::class.java), AdsIDDef.MaxRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-rect-1;max-rect-2")
            val state = LoadedAdFixture.state(pl)
            val cb = Callback()
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("rect_home", cb)
            val view = built.constructed()[0]

            listeners[0].onAdLoaded(maxAd)
            assertEquals(listOf("loaded"), cb.events)
            val shown = pl.getAdForShow("rect_home", null) as ObjectForShowAdWithViewParent
            assertSame(view, shown.viewCurrAttach)
            assertEquals("getAdForShow bỏ adObject", null, state.adEcmps!!.list[0].adObject)

            listeners[0].onAdLoaded(maxAd)
            assertSame("refresh 1: gán lại view", view, state.adEcmps!!.list[0].adObject)
            listeners[0].onAdLoaded(maxAd)
            listeners[0].onAdLoaded(maxAd)
            verify(view, never()).destroy()
            assertSame(view, state.adEcmps!!.list[0].adObject)
            assertEquals(listOf("loaded", "loaded", "loaded", "loaded"), cb.events)
            assertEquals(1, built.constructed().size)
        }
    }

    /**
     * Task K mục 1 (re-review Task J Minor 1): view A đã lấy ra hiện (`getAdForShow` bỏ `adObject`), lần load sau nạp
     * view B mới vào cùng mục; A vẫn tự refresh và lần refresh kế đi nhánh "not turn" (mã lượt đã là của B). Như GMA:
     * chỉ gán khi mục trống, không `destroy()` view đang giữ — B (fill đã trả tiền, tươi hơn) vẫn là quảng cáo đã load.
     * Trước đây nhánh này destroy B rồi gán lại A.
     */
    @Test
    fun `MAX rect - refresh cua view da lay ra khong destroy view moi dang giu`() = MainHandlerTimers.run { _ ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlRect(context, mock(AdsBase::class.java), AdsIDDef.MaxRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-rect-1;max-rect-2")
            val state = LoadedAdFixture.state(pl)
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            val cb1 = Callback()
            pl.load("rect_home", cb1)
            val viewA = built.constructed()[0]
            listeners[0].onAdLoaded(maxAd)
            assertEquals(listOf("loaded"), cb1.events)
            val shown = pl.getAdForShow("rect_home", null) as ObjectForShowAdWithViewParent
            assertSame(viewA, shown.viewCurrAttach)

            // Task L mục 1: mã lượt là bộ đếm, request B (cùng khối đồng bộ) luôn có mã khác A — không cần đợi sang ms kế.
            val turnA = state.adEcmps!!.list[0].turnLoadId
            val cb2 = Callback()
            pl.load("rect_home", cb2)
            assertEquals("request B tạo view mới", 2, built.constructed().size)
            val viewB = built.constructed()[1]
            listeners[1].onAdLoaded(maxAd)
            assertEquals(listOf("loaded"), cb2.events)
            assertSame(viewB, state.adEcmps!!.list[0].adObject)
            assertTrue(state.adEcmps!!.list[0].turnLoadId != turnA)

            listeners[0].onAdLoaded(maxAd)   // A (đang hiện) tự refresh: nhánh "not turn"
            verify(viewB, never()).destroy()
            verify(viewA, never()).destroy()
            assertSame("B vẫn là quảng cáo đã load", viewB, state.adEcmps!!.list[0].adObject)
            assertTrue(state.adEcmps!!.list[0].isLoaded)
            assertEquals("không request mới", 2, built.constructed().size)
        }
    }

    /**
     * Task L mục 1 (review Task K Minor 4), dạng tất định (Task M mục 1): `turnLoadId` là bộ đếm đơn điệu của mục, không
     * phải `currentTimeMillis`. `showRect` (lấy view A qua `getAdForShow`) rồi `loadRect4Show` đồng bộ gửi request B:
     * mã lượt của B đúng bằng mã của A cộng một, nên hai request trong cùng một khối đồng bộ luôn có mã khác nhau. Không
     * đọc đồng hồ, không vòng lặp.
     */
    @Test
    fun `MAX rect - request ke tiep nhan turnLoadId bang turnA cong mot`() = MainHandlerTimers.run { _ ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlRect(context, mock(AdsBase::class.java), AdsIDDef.MaxRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-rect-1")
            val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("rect_home", Callback())                  // request A
            val turnA = item.turnLoadId
            listeners[0].onAdLoaded(maxAd)
            pl.getAdForShow("rect_home", null)                // showRect: lấy A ra
            pl.load("rect_home", Callback())                  // loadRect4Show: request B, cùng khối đồng bộ
            val turnB = item.turnLoadId
            assertEquals("request B tạo view mới", 2, built.constructed().size)
            assertEquals("mã lượt của B là mã của A cộng một", turnA + 1, turnB)
        }
    }

    /**
     * Task L mục 2 (review Task K Minor 1): nhánh refresh "cùng lượt" của MAX rect làm như GMA — chỉ gán khi mục trống,
     * không `destroy()` view khác đang giữ. A lấy ra hiện, B nạp và lấy ra hiện, A (view lượt trước) refresh → nhánh
     * "not turn" gán A vào mục trống; rồi B (lượt hiện tại, mục không loading) refresh → nhánh "cùng lượt" gặp mục đang
     * giữ A. Trước đây nhánh đó destroy A rồi gán B; nay giữ nguyên A, không destroy view nào, B vẫn onLoaded.
     */
    @Test
    fun `MAX rect - refresh cung luot khong destroy view khac dang giu`() = MainHandlerTimers.run { _ ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlRect(context, mock(AdsBase::class.java), AdsIDDef.MaxRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-rect-1")
            val state = LoadedAdFixture.state(pl)
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("rect_home", Callback())
            val viewA = built.constructed()[0]
            listeners[0].onAdLoaded(maxAd)
            pl.getAdForShow("rect_home", null)
            val cbB = Callback()
            pl.load("rect_home", cbB)
            val viewB = built.constructed()[1]
            listeners[1].onAdLoaded(maxAd)
            pl.getAdForShow("rect_home", null)
            assertEquals(null, state.adEcmps!!.list[0].adObject)

            listeners[0].onAdLoaded(maxAd)   // A refresh: "not turn", mục trống → gán A
            assertSame(viewA, state.adEcmps!!.list[0].adObject)
            listeners[1].onAdLoaded(maxAd)   // B refresh: "cùng lượt", mục đang giữ A
            verify(viewA, never()).destroy()
            verify(viewB, never()).destroy()
            assertSame("mục vẫn giữ A, không bị ghi đè", viewA, state.adEcmps!!.list[0].adObject)
            assertEquals("slot rảnh: response B, refresh A, refresh B đều onLoaded", listOf("loaded", "loaded", "loaded"), cbB.events)
            assertEquals("không request mới", 2, built.constructed().size)
        }
    }

    /**
     * Task L mục 2, kịch bản review: hai request trên cùng mục chung một mã lượt (trước Task L mục 1 là hai request trong
     * cùng ms; test đặt lại mã lượt bằng setter để mô phỏng). View A đang hiện refresh đi nhánh "cùng lượt" khi mục đang
     * giữ view B mới: không destroy B, B vẫn là quảng cáo đã load. Trước đây B bị destroy và A gán lại vào mục.
     */
    @Test
    fun `MAX banner - refresh cung luot khong destroy view moi dang giu`() = MainHandlerTimers.run { _ ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlBanner(context, mock(AdsBase::class.java), AdsIDDef.MaxBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-bn-1")
            val state = LoadedAdFixture.state(pl)
            val item = state.adEcmps!!.list[0]
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("banner_home", Callback())
            val viewA = built.constructed()[0]
            listeners[0].onAdLoaded(maxAd)
            val turnA = item.turnLoadId
            item.isLoaded = false; item.adObject = null      // A đã đưa lên màn hình, mục trống cho lượt sau
            val cbB = Callback()
            pl.load("banner_home", cbB)
            val viewB = built.constructed()[1]
            listeners[1].onAdLoaded(maxAd)
            assertEquals(listOf("loaded"), cbB.events)
            assertSame(viewB, item.adObject)
            item.turnLoadId = turnA                            // hai request chung mã lượt

            listeners[0].onAdLoaded(maxAd)                     // A refresh: nhánh "cùng lượt", mục đang giữ B
            verify(viewB, never()).destroy()
            verify(viewA, never()).destroy()
            assertSame("B vẫn là quảng cáo đã load", viewB, item.adObject)
            assertTrue(item.isLoaded)
            assertEquals("không request mới", 2, built.constructed().size)
        }
    }

    // ═══ Task L mục 3 (review Task K Minor 2): refresh của view đang hiện khi slot có request đang chờ ═══
    //
    // Rect A đã lấy ra hiện, `load(cb2)` gửi request B (slot loading). A tự refresh (thành công rồi lỗi) trong lúc B chờ:
    // không tới cb2 (trước đây cb2 nhận `loaded`/`fail` của A, helper coi đó là kết quả load và hạ cờ loading trước khi
    // B trả lời), slot vẫn loading. B trả lời → cb2 nhận đúng một callback kết thúc của chính B. Sau đó slot rảnh: refresh
    // của view đang hiện vẫn onLoaded như cũ. Một test mỗi mạng.

    private fun <T> withFbViews(block: (built: org.mockito.MockedConstruction<com.facebook.ads.AdView>, listeners: MutableList<com.facebook.ads.AdListener>) -> T): T {
        val listeners = mutableListOf<com.facebook.ads.AdListener>()
        return mockConstruction(com.facebook.ads.AdView::class.java) { view, _ ->
            val builder = mock(com.facebook.ads.AdView.AdViewLoadConfigBuilder::class.java)
            `when`(view.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listeners += it.arguments[0] as com.facebook.ads.AdListener; builder }
            `when`(builder.build()).thenReturn(mock(com.facebook.ads.AdView.AdViewLoadConfig::class.java))
        }.use { built -> block(built, listeners) }
    }

    @Test
    fun `GMA rect - refresh cua view dang hien khi request dang cho khong toi cb cua request`() = MainHandlerTimers.run { _ ->
        withGmaBanner { built, listeners ->
            val pl = AdsMobPlRect(context, mock(AdsBase::class.java), AdsIDDef.AdmobRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms(first)
            val state = LoadedAdFixture.state(pl)
            pl.load("rect_home", Callback())
            listeners[0].onAdLoaded()
            pl.getAdForShow("rect_home", null)                 // A lên màn hình
            val cb2 = Callback()
            pl.load("rect_home", cb2)                          // request B đang chờ
            assertEquals(2, built.constructed().size)
            assertTrue(state.isLoading)

            listeners[0].onAdLoaded()                          // A refresh
            listeners[0].onAdFailedToLoad(mock(LoadAdError::class.java))
            assertEquals("refresh của A không tới cb của request B", emptyList<String>(), cb2.events)
            assertTrue("slot vẫn chờ B", state.isLoading)

            listeners[1].onAdLoaded()                          // B trả lời
            assertEquals(listOf("loaded"), cb2.events)
            assertFalse(state.isLoading)
            listeners[0].onAdLoaded()                          // slot rảnh: refresh vẫn onLoaded
            assertEquals(listOf("loaded", "loaded"), cb2.events)
        }
    }

    @Test
    fun `MAX rect - refresh cua view dang hien khi request dang cho khong toi cb cua request`() = MainHandlerTimers.run { _ ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlRect(context, mock(AdsBase::class.java), AdsIDDef.MaxRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-rect-1")
            val state = LoadedAdFixture.state(pl)
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("rect_home", Callback())
            listeners[0].onAdLoaded(maxAd)
            pl.getAdForShow("rect_home", null)
            val cb2 = Callback()
            pl.load("rect_home", cb2)
            assertEquals(2, built.constructed().size)
            assertTrue(state.isLoading)

            listeners[0].onAdLoaded(maxAd)
            listeners[0].onAdLoadFailed("max-rect-1", mock(MaxError::class.java))
            assertEquals("refresh của A không tới cb của request B", emptyList<String>(), cb2.events)
            assertTrue("slot vẫn chờ B", state.isLoading)

            listeners[1].onAdLoaded(maxAd)
            assertEquals(listOf("loaded"), cb2.events)
            assertFalse(state.isLoading)
            listeners[0].onAdLoaded(maxAd)
            assertEquals(listOf("loaded", "loaded"), cb2.events)
        }
    }

    @Test
    fun `Meta rect - refresh cua view dang hien khi request dang cho khong toi cb, B loi thi dung mot fail`() = MainHandlerTimers.run { _ ->
        withFbViews { built, listeners ->
            val pl = AdsFbPlRect(context, mock(AdsBase::class.java), AdsIDDef.FBRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb-rect-1")
            val state = LoadedAdFixture.state(pl)
            pl.load("rect_home", Callback())
            val viewA = built.constructed()[0]
            listeners[0].onAdLoaded(viewA as Ad)
            pl.getAdForShow("rect_home", null)
            val cb2 = Callback()
            pl.load("rect_home", cb2)
            assertEquals(2, built.constructed().size)
            assertTrue(state.isLoading)
            val viewB = built.constructed()[1]

            listeners[0].onAdLoaded(viewA as Ad)
            listeners[0].onError(viewA as Ad, mock(AdError::class.java))
            assertEquals("refresh của A không tới cb của request B", emptyList<String>(), cb2.events)
            assertTrue("slot vẫn chờ B", state.isLoading)

            listeners[1].onError(viewB as Ad, mock(AdError::class.java))   // B lỗi, hết waterfall
            assertEquals("đúng một callback kết thúc của B", 1, cb2.events.size)
            assertTrue(cb2.events[0].startsWith("fail:"))
            assertFalse(state.isLoading)
        }
    }

    // ═══ Meta banner: AdView + AdListener qua builder ═══

    @Test
    fun `Meta banner - response muon cua request da bo khong toi luot moi, refresh van onLoaded`() = MainHandlerTimers.run { t ->
        val listeners = mutableListOf<com.facebook.ads.AdListener>()
        mockConstruction(com.facebook.ads.AdView::class.java) { view, _ ->
            val builder = mock(com.facebook.ads.AdView.AdViewLoadConfigBuilder::class.java)
            `when`(view.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listeners += it.arguments[0] as com.facebook.ads.AdListener; builder }
            `when`(builder.build()).thenReturn(mock(com.facebook.ads.AdView.AdViewLoadConfig::class.java))
        }.use { built ->
            val pl = AdsFbPlBanner(context, mock(AdsBase::class.java), AdsIDDef.FBBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb-1;fb-2")
            val state = LoadedAdFixture.state(pl)
            val cb1 = Callback()
            pl.load("banner_home", cb1)
            assertEquals(1, built.constructed().size)

            t.advanceTo(60_000)
            assertEquals(listOf("fail:load timeout"), cb1.events)
            val cb2 = Callback()
            pl.load("banner_home", cb2)
            assertEquals(2, built.constructed().size)
            assertTrue(state.isLoading)
            val late = built.constructed()[0]
            val fresh = built.constructed()[1]

            listeners[0].onAdLoaded(late as Ad)
            assertEquals(emptyList<String>(), cb2.events)
            assertTrue(state.isLoading)
            assertNotSame(late, state.adEcmps!!.list[0].adObject)
            verify(late, times(1)).destroy()

            listeners[0].onError(late as Ad, mock(AdError::class.java))
            assertEquals(emptyList<String>(), cb2.events)
            assertEquals(0, state.adEcmps!!.idxCurrEcpm)
            assertEquals(2, built.constructed().size)

            listeners[1].onAdLoaded(fresh as Ad)
            assertEquals(listOf("loaded"), cb2.events)
            assertSame(fresh, state.adEcmps!!.list[0].adObject)
            assertFalse(state.isLoading)
            verify(fresh, never()).destroy()

            listeners[1].onAdLoaded(fresh as Ad)
            assertEquals("auto-refresh vẫn onLoaded", listOf("loaded", "loaded"), cb2.events)
            assertEquals(1, cb1.events.size)
        }
    }

    // ═══ Task M mục 2 (review Task L Minor 2): request đang chờ giữ mục của nó từ đầu tới cuối ═══
    //
    // View A đang hiện (rect: `getAdForShow` bỏ `adObject`; banner: mục đặt lại trống như lúc A đã lên màn hình), request
    // B đang chờ trên cùng mục. A tự refresh (nhánh "not turn") không được gán A vào mục trống:
    // (a) mục phải trống trong lúc B chờ (`isLoaded = false` mà `adObject != null` thì `getAdLoaded` báo
    //     `LOAD_FAIL_BUT_ADS_NOT_NULL` giả);
    // (b) B không trả lời → watchdog của slot → thành công muộn của B qua `keepLateAd` gặp mục trống: B được giữ làm quảng
    //     cáo đã load, không bị destroy (trước đây mục đang giữ A nên B bị destroy, fill đã trả tiền bị bỏ).
    // Một test mỗi lớp (6 lớp, 12 chỗ gán). Helper gom vi phạm để log đỏ hiện cả (a) lẫn (b).

    private fun destroyCalls(view: Any) = mockingDetails(view).invocations.count { it.method.name == "destroy" }

    private fun pendingRequestViolations(
        t: MainHandlerTimers,
        pl: AdsPlBase,
        cbB: Callback,
        viewA: Any,
        viewB: Any,
        refreshA: () -> Unit,
        lateSuccessB: () -> Unit
    ): List<String> {
        val state = LoadedAdFixture.state(pl)
        val item = state.adEcmps!!.list[0]
        assertTrue("B đang chờ", state.isLoading)
        val violations = mutableListOf<String>()

        refreshA()
        if (item.adObject != null) {
            val who = if (item.adObject === viewA) "A" else "view khác"
            violations += "(a) refresh của A lúc B chờ gán $who vào mục (isLoaded=${item.isLoaded})"
        }
        assertEquals("refresh của A không tới cb của B", emptyList<String>(), cbB.events)

        t.advanceTo(60_000)                                   // B không trả lời: watchdog của slot
        assertEquals(listOf("fail:load timeout"), cbB.events)
        assertFalse(state.isLoading)

        lateSuccessB()                                        // thành công muộn của B → keepLateAd
        if (item.adObject !== viewB || !item.isLoaded) {
            val who = if (item.adObject === viewA) "A" else item.adObject?.let { "view khác" } ?: "null"
            violations += "(b) B tới muộn không được giữ: mục giữ $who (isLoaded=${item.isLoaded})"
        }
        if (destroyCalls(viewB) > 0) violations += "(b) B bị destroy ${destroyCalls(viewB)} lần"
        if (destroyCalls(viewA) > 0) violations += "A bị destroy ${destroyCalls(viewA)} lần"
        assertEquals("không callback thêm cho lượt đã bỏ", 1, cbB.events.size)
        return violations
    }

    @Test
    fun `GMA rect - request dang cho giu muc, refresh cua A khong chiem muc`() = MainHandlerTimers.run { t ->
        withGmaBanner { built, listeners ->
            val pl = AdsMobPlRect(context, mock(AdsBase::class.java), AdsIDDef.AdmobRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms(first)
            pl.load("rect_home", Callback())
            listeners[0].onAdLoaded()
            pl.getAdForShow("rect_home", null)                 // A lên màn hình
            val cbB = Callback()
            pl.load("rect_home", cbB)                          // request B đang chờ
            val (viewA, viewB) = built.constructed()
            assertEquals(emptyList<String>(), pendingRequestViolations(t, pl, cbB, viewA, viewB,
                refreshA = { listeners[0].onAdLoaded() }, lateSuccessB = { listeners[1].onAdLoaded() }))
        }
    }

    @Test
    fun `GMA banner - request dang cho giu muc, refresh cua A khong chiem muc`() = MainHandlerTimers.run { t ->
        withGmaBanner { built, listeners ->
            val pl = AdsMobPlBanner(context, mock(AdsBase::class.java), AdsIDDef.AdmobBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms(first)
            val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
            pl.load("banner_home", Callback())
            listeners[0].onAdLoaded()
            item.isLoaded = false; item.adObject = null        // A đã lên màn hình, mục trống cho lượt sau
            val cbB = Callback()
            pl.load("banner_home", cbB)
            val (viewA, viewB) = built.constructed()
            assertEquals(emptyList<String>(), pendingRequestViolations(t, pl, cbB, viewA, viewB,
                refreshA = { listeners[0].onAdLoaded() }, lateSuccessB = { listeners[1].onAdLoaded() }))
        }
    }

    @Test
    fun `MAX rect - request dang cho giu muc, refresh cua A khong chiem muc`() = MainHandlerTimers.run { t ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlRect(context, mock(AdsBase::class.java), AdsIDDef.MaxRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-rect-1")
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("rect_home", Callback())
            listeners[0].onAdLoaded(maxAd)
            pl.getAdForShow("rect_home", null)
            val cbB = Callback()
            pl.load("rect_home", cbB)
            val (viewA, viewB) = built.constructed()
            assertEquals(emptyList<String>(), pendingRequestViolations(t, pl, cbB, viewA, viewB,
                refreshA = { listeners[0].onAdLoaded(maxAd) }, lateSuccessB = { listeners[1].onAdLoaded(maxAd) }))
        }
    }

    @Test
    fun `MAX banner - request dang cho giu muc, refresh cua A khong chiem muc`() = MainHandlerTimers.run { t ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlBanner(context, mock(AdsBase::class.java), AdsIDDef.MaxBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-bn-1")
            val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("banner_home", Callback())
            listeners[0].onAdLoaded(maxAd)
            item.isLoaded = false; item.adObject = null
            val cbB = Callback()
            pl.load("banner_home", cbB)
            val (viewA, viewB) = built.constructed()
            assertEquals(emptyList<String>(), pendingRequestViolations(t, pl, cbB, viewA, viewB,
                refreshA = { listeners[0].onAdLoaded(maxAd) }, lateSuccessB = { listeners[1].onAdLoaded(maxAd) }))
        }
    }

    @Test
    fun `Meta rect - request dang cho giu muc, refresh cua A khong chiem muc`() = MainHandlerTimers.run { t ->
        withFbViews { built, listeners ->
            val pl = AdsFbPlRect(context, mock(AdsBase::class.java), AdsIDDef.FBRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb-rect-1")
            pl.load("rect_home", Callback())
            val viewA = built.constructed()[0]
            listeners[0].onAdLoaded(viewA as Ad)
            pl.getAdForShow("rect_home", null)
            val cbB = Callback()
            pl.load("rect_home", cbB)
            val viewB = built.constructed()[1]
            assertEquals(emptyList<String>(), pendingRequestViolations(t, pl, cbB, viewA, viewB,
                refreshA = { listeners[0].onAdLoaded(viewA as Ad) }, lateSuccessB = { listeners[1].onAdLoaded(viewB as Ad) }))
        }
    }

    @Test
    fun `Meta banner - request dang cho giu muc, refresh cua A khong chiem muc`() = MainHandlerTimers.run { t ->
        withFbViews { built, listeners ->
            val pl = AdsFbPlBanner(context, mock(AdsBase::class.java), AdsIDDef.FBBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb-bn-1")
            val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
            pl.load("banner_home", Callback())
            val viewA = built.constructed()[0]
            listeners[0].onAdLoaded(viewA as Ad)
            item.isLoaded = false; item.adObject = null
            val cbB = Callback()
            pl.load("banner_home", cbB)
            val viewB = built.constructed()[1]
            assertEquals(emptyList<String>(), pendingRequestViolations(t, pl, cbB, viewA, viewB,
                refreshA = { listeners[0].onAdLoaded(viewA as Ad) }, lateSuccessB = { listeners[1].onAdLoaded(viewB as Ad) }))
        }
    }

    // ═══ Final review Minor 2 (và Minor 6): refresh chiếm mục lúc slot rảnh, rồi mới có request ═══
    //
    // Thứ tự ngược với Task M mục 2: A đã lấy ra hiện, slot rảnh, A tự refresh → mục trống nên nhận A
    // (`adObject = A`, `isLoaded = false`; tham chiếu này không sở hữu A — helper đang giữ A trong container của app).
    // Sau đó app load → request B trên cùng mục. Trước đây mục vẫn giữ A: getAdLoaded báo `LOAD_FAIL_BUT_ADS_NOT_NULL`
    // giả, và B tới muộn sau watchdog bị `keepLateAd` destroy vì mục "đã có" A. Nay lúc app load (slot rảnh) mục bỏ tham
    // chiếu A (không destroy A: nó đang hiện), nên B muộn được giữ.

    private fun refreshThenRequestViolations(
        t: MainHandlerTimers,
        pl: AdsPlBase,
        placement: String,
        viewA: Any,
        viewB: () -> Any,
        refreshA: () -> Unit,
        lateSuccessB: () -> Unit
    ): List<String> {
        val state = LoadedAdFixture.state(pl)
        val item = state.adEcmps!!.list[0]
        assertFalse("slot rảnh", state.isLoading)
        refreshA()
        assertSame("tiền đề: refresh lúc slot rảnh gán A vào mục trống", viewA, item.adObject)
        assertFalse(item.isLoaded)

        val violations = mutableListOf<String>()
        val cbB = Callback()
        pl.load(placement, cbB)
        assertTrue("B đang chờ", state.isLoading)
        if (item.adObject != null) violations += "(a) request B bắt đầu mà mục vẫn giữ ${if (item.adObject === viewA) "A" else "view khác"}"

        t.advanceTo(60_000)                                   // B không trả lời: watchdog của slot
        assertEquals(listOf("fail:load timeout"), cbB.events)
        val b = viewB()
        lateSuccessB()                                        // thành công muộn của B → keepLateAd
        if (item.adObject !== b || !item.isLoaded) {
            val who = if (item.adObject === viewA) "A" else item.adObject?.let { "view khác" } ?: "null"
            violations += "(b) B tới muộn không được giữ: mục giữ $who (isLoaded=${item.isLoaded})"
        }
        if (destroyCalls(b) > 0) violations += "(b) B bị destroy ${destroyCalls(b)} lần"
        if (destroyCalls(viewA) > 0) violations += "A (đang hiện) bị destroy ${destroyCalls(viewA)} lần"
        assertEquals("không callback thêm cho lượt đã bỏ", 1, cbB.events.size)
        return violations
    }

    @Test
    fun `GMA rect - refresh chiem muc luc slot ranh roi request B, B muon van duoc giu`() = MainHandlerTimers.run { t ->
        withGmaBanner { built, listeners ->
            val pl = AdsMobPlRect(context, mock(AdsBase::class.java), AdsIDDef.AdmobRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms(first)
            pl.load("rect_home", Callback())
            listeners[0].onAdLoaded()
            pl.getAdForShow("rect_home", null)                 // A lên màn hình
            assertEquals(emptyList<String>(), refreshThenRequestViolations(t, pl, "rect_home", built.constructed()[0],
                viewB = { built.constructed()[1] },
                refreshA = { listeners[0].onAdLoaded() }, lateSuccessB = { listeners[1].onAdLoaded() }))
        }
    }

    @Test
    fun `GMA banner - refresh chiem muc luc slot ranh roi request B, B muon van duoc giu`() = MainHandlerTimers.run { t ->
        withGmaBanner { built, listeners ->
            val pl = AdsMobPlBanner(context, mock(AdsBase::class.java), AdsIDDef.AdmobBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms(first)
            val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
            pl.load("banner_home", Callback())
            listeners[0].onAdLoaded()
            item.isLoaded = false; item.adObject = null        // A đã lên màn hình, mục trống cho lượt sau
            assertEquals(emptyList<String>(), refreshThenRequestViolations(t, pl, "banner_home", built.constructed()[0],
                viewB = { built.constructed()[1] },
                refreshA = { listeners[0].onAdLoaded() }, lateSuccessB = { listeners[1].onAdLoaded() }))
        }
    }

    @Test
    fun `MAX rect - refresh chiem muc luc slot ranh roi request B, B muon van duoc giu`() = MainHandlerTimers.run { t ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlRect(context, mock(AdsBase::class.java), AdsIDDef.MaxRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-rect-1")
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("rect_home", Callback())
            listeners[0].onAdLoaded(maxAd)
            pl.getAdForShow("rect_home", null)
            assertEquals(emptyList<String>(), refreshThenRequestViolations(t, pl, "rect_home", built.constructed()[0],
                viewB = { built.constructed()[1] },
                refreshA = { listeners[0].onAdLoaded(maxAd) }, lateSuccessB = { listeners[1].onAdLoaded(maxAd) }))
        }
    }

    @Test
    fun `MAX banner - refresh chiem muc luc slot ranh roi request B, B muon van duoc giu`() = MainHandlerTimers.run { t ->
        withMaxViews { built, listeners ->
            val pl = AdsMaxPlBanner(context, mock(AdsBase::class.java), AdsIDDef.MaxBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-bn-1")
            val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-view") }
            pl.load("banner_home", Callback())
            listeners[0].onAdLoaded(maxAd)
            item.isLoaded = false; item.adObject = null
            assertEquals(emptyList<String>(), refreshThenRequestViolations(t, pl, "banner_home", built.constructed()[0],
                viewB = { built.constructed()[1] },
                refreshA = { listeners[0].onAdLoaded(maxAd) }, lateSuccessB = { listeners[1].onAdLoaded(maxAd) }))
        }
    }

    @Test
    fun `Meta rect - refresh chiem muc luc slot ranh roi request B, B muon van duoc giu`() = MainHandlerTimers.run { t ->
        withFbViews { built, listeners ->
            val pl = AdsFbPlRect(context, mock(AdsBase::class.java), AdsIDDef.FBRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb-rect-1")
            pl.load("rect_home", Callback())
            val viewA = built.constructed()[0]
            listeners[0].onAdLoaded(viewA as Ad)
            pl.getAdForShow("rect_home", null)
            assertEquals(emptyList<String>(), refreshThenRequestViolations(t, pl, "rect_home", viewA,
                viewB = { built.constructed()[1] },
                refreshA = { listeners[0].onAdLoaded(viewA as Ad) },
                lateSuccessB = { listeners[1].onAdLoaded(built.constructed()[1] as Ad) }))
        }
    }

    @Test
    fun `Meta banner - refresh chiem muc luc slot ranh roi request B, B muon van duoc giu`() = MainHandlerTimers.run { t ->
        withFbViews { built, listeners ->
            val pl = AdsFbPlBanner(context, mock(AdsBase::class.java), AdsIDDef.FBBanner, "banner_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb-bn-1")
            val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
            pl.load("banner_home", Callback())
            val viewA = built.constructed()[0]
            listeners[0].onAdLoaded(viewA as Ad)
            item.isLoaded = false; item.adObject = null
            assertEquals(emptyList<String>(), refreshThenRequestViolations(t, pl, "banner_home", viewA,
                viewB = { built.constructed()[1] },
                refreshA = { listeners[0].onAdLoaded(viewA as Ad) },
                lateSuccessB = { listeners[1].onAdLoaded(built.constructed()[1] as Ad) }))
        }
    }
}
