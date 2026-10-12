package com.app.plugin.nextgen

import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlBaseBn
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.MainHandlerTimers
import com.app.plugin.ads.adsmob.AdsMobPlBanner
import com.app.plugin.ads.adsmob.AdsMobPlRect
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.nextgen.NgBannerKit.Companion.UNIT
import com.app.plugin.nextgen.NgBannerKit.Companion.activity
import com.app.plugin.nextgen.NgBannerKit.Companion.context
import com.app.plugin.until.Constants
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import com.app.plugin.ads.AdsShowCb
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import org.junit.Assert.assertNotNull
import org.mockito.Mockito.mockingDetails

/**
 * 2.4.0 NG2: bản nextgen của phần GMA trong `LateBannerLoadCallbackTest` (Task J1/L/M, final review Minor 2) và
 * `RectGetAdForShowSquatTest` (Minor 6). Next-Gen tách response (`AdLoadCallback`, một lần) khỏi auto-refresh
 * (`BannerAdRefreshCallback`), nên "callback đầu tiên của listener" = load callback, "refresh" = refresh callback.
 */
class NgBannerLateTest {
    private val kit = NgBannerKit()

    @Before fun setUp() = kit.install()

    @After fun tearDown() = kit.uninstall()

    private fun banner() = AdsMobPlBanner(context(), mock(AdsBase::class.java), AdsIDDef.AdmobBanner, "banner_default")
        .also { it.setActivity(activity()); it.parserEcpms(UNIT) }

    private fun rect() = AdsMobPlRect(context(), mock(AdsBase::class.java), AdsIDDef.AdmobRect, "rect_default")
        .also { it.setActivity(activity()); it.parserEcpms(UNIT) }

    /** Rect A nạp xong rồi lấy ra hiện (`getAdForShow` bỏ `adObject`). */
    private fun rectShown(pl: AdsMobPlRect): BannerAd {
        pl.load("rect_home", NgBannerKit.Cb())
        val a = kit.loaded(0)
        pl.getAdForShow("rect_home", null)
        return a
    }

    @Test fun `GMA banner - response muon khi slot ranh duoc giu, lan load sau tra ngay khong request moi`() = MainHandlerTimers.run { t ->
        val pl = banner()
        val state = LoadedAdFixture.state(pl)
        val cb1 = NgBannerKit.Cb(); pl.load("banner_home", cb1)
        t.advanceTo(60_000)
        assertEquals(listOf("fail:load timeout"), cb1.events)
        val late = kit.loaded(0)
        assertEquals("không callback thứ hai cho lượt đã bỏ", 1, cb1.events.size)
        assertSame("view muộn giữ làm quảng cáo đã load", kit.viewOf(late), state.adEcmps!!.list[0].adObject)
        assertTrue(state.adEcmps!!.list[0].isLoaded)
        assertEquals(0, kit.destroyed(late))
        val cb2 = NgBannerKit.Cb(); pl.load("banner_home", cb2)
        assertEquals(listOf("loaded"), cb2.events)
        assertEquals("không request mới", 1, kit.requests.size)
    }

    @Test fun `GMA rect - refresh cua view dang hien khi request dang cho khong toi cb cua request`() = MainHandlerTimers.run { _ ->
        val pl = rect()
        val state = LoadedAdFixture.state(pl)
        val a = rectShown(pl)
        val cb2 = NgBannerKit.Cb(); pl.load("rect_home", cb2)
        assertEquals(2, kit.requests.size)
        assertTrue(state.isLoading)
        kit.refresh(a).onAdRefreshed()
        kit.refresh(a).onAdFailedToRefresh(NgBannerKit.error())
        assertEquals("refresh của A không tới cb của request B", emptyList<String>(), cb2.events)
        assertTrue("slot vẫn chờ B", state.isLoading)
        kit.loaded(1)
        assertEquals(listOf("loaded"), cb2.events)
        assertFalse(state.isLoading)
        kit.refresh(a).onAdRefreshed()
        assertEquals("slot rảnh: refresh vẫn onLoaded", listOf("loaded", "loaded"), cb2.events)
    }

    @Test fun `GMA rect - request dang cho giu muc, refresh cua A khong chiem muc, B muon duoc giu`() = MainHandlerTimers.run { t ->
        val pl = rect()
        val a = rectShown(pl)
        val cbB = NgBannerKit.Cb(); pl.load("rect_home", cbB)
        val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
        kit.refresh(a).onAdRefreshed()
        assertEquals("(a) refresh của A lúc B chờ không gán vào mục", null, item.adObject)
        assertEquals(emptyList<String>(), cbB.events)
        t.advanceTo(60_000)
        assertEquals(listOf("fail:load timeout"), cbB.events)
        val b = kit.loaded(1)
        assertKept(pl, b, a, cbB)
    }

    @Test fun `GMA rect - refresh chiem muc luc slot ranh roi request B, B muon van duoc giu`() = MainHandlerTimers.run { t ->
        val pl = rect()
        val a = rectShown(pl)
        val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
        kit.refresh(a).onAdRefreshed()
        assertSame("tiền đề: refresh lúc slot rảnh gán A vào mục trống", kit.viewOf(a), item.adObject)
        assertFalse(item.isLoaded)
        val cbB = NgBannerKit.Cb(); pl.load("rect_home", cbB)
        assertEquals("(a) request B bắt đầu thì mục bỏ tham chiếu A", null, item.adObject)
        t.advanceTo(60_000)
        assertEquals(listOf("fail:load timeout"), cbB.events)
        assertKept(pl, kit.loaded(1), a, cbB)
    }

    @Test fun `GMA rect - getAdForShow lan hai khi refresh chiem muc - khong LOAD_FAIL_BUT_ADS_NOT_NULL, tra null, muc trong`() =
        MainHandlerTimers.run { _ ->
            val pl = rect()
            val a = rectShown(pl)
            val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
            kit.refresh(a).onAdRefreshed()
            assertSame("tiền đề: refresh lúc slot rảnh gán A vào mục trống", kit.viewOf(a), item.adObject)
            ServerEventCapture().use { server ->
                assertEquals(null, pl.getAdForShow("rect_home", null))
                LogEventServer.logAdShowFailWithReason(placement = "sentinel", reason = "sentinel")
                val squat = server.await("ad_show_fail", 1).map { it.get("reason").asString }
                    .filter { it == Constants.AdShowFailReason.LOAD_FAIL_BUT_ADS_NOT_NULL }
                assertEquals(emptyList<String>(), squat)
            }
            assertEquals("mục trống", null, item.adObject)
            assertEquals("A đang hiện: không huỷ", 0, kit.destroyed(a))
        }

    /** Banner A nạp xong rồi lên màn hình (mục đặt lại trống cho lượt sau, như Legacy :672/:823). */
    private fun bannerShown(pl: AdsMobPlBanner): BannerAd {
        pl.load("banner_home", NgBannerKit.Cb())
        val a = kit.loaded(0)
        LoadedAdFixture.state(pl).adEcmps!!.list[0].apply { isLoaded = false; adObject = null }
        return a
    }

    @Test fun `GMA banner - request dang cho giu muc, refresh cua A khong chiem muc, B muon duoc giu (R-NG2 m5)`() = MainHandlerTimers.run { t ->
        val pl = banner()
        val a = bannerShown(pl)
        val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
        val cbB = NgBannerKit.Cb(); pl.load("banner_home", cbB)
        kit.refresh(a).onAdRefreshed()
        assertEquals("(a) refresh của A lúc B chờ không gán vào mục", null, item.adObject)
        assertEquals(emptyList<String>(), cbB.events)
        t.advanceTo(60_000)
        assertEquals(listOf("fail:load timeout"), cbB.events)
        assertKept(pl, kit.loaded(1), a, cbB)
    }

    @Test fun `GMA banner - refresh chiem muc luc slot ranh roi request B, B muon van duoc giu (R-NG2 m5)`() = MainHandlerTimers.run { t ->
        val pl = banner()
        val a = bannerShown(pl)
        val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
        kit.refresh(a).onAdRefreshed()
        assertSame("tiền đề: refresh lúc slot rảnh gán A vào mục trống", kit.viewOf(a), item.adObject)
        assertFalse(item.isLoaded)
        val cbB = NgBannerKit.Cb(); pl.load("banner_home", cbB)
        assertEquals("(a) request B bắt đầu thì mục bỏ tham chiếu A", null, item.adObject)
        t.advanceTo(60_000)
        assertEquals(listOf("fail:load timeout"), cbB.events)
        assertKept(pl, kit.loaded(1), a, cbB)
    }

    @Test fun `GMA banner - quang cao muon duoc giu van bao onAdPaid voi placement cua luot (IMP-1, R-NG2 m1)`() = MainHandlerTimers.run { t ->
        val pl = banner()
        pl.load("banner_home", NgBannerKit.Cb())
        t.advanceTo(60_000)
        val late = kit.loaded(0)
        assertSame("tiền đề: quảng cáo muộn được giữ", kit.viewOf(late), LoadedAdFixture.state(pl).adEcmps!!.list[0].adObject)
        pl.show("bn_turn", null, 0)
        val events = mockingDetails(late).invocations.lastOrNull { it.method.name == "setAdEventCallback" }?.arguments?.get(0) as BannerAdEventCallback?
        assertNotNull("quảng cáo muộn được giữ phải có callback sự kiện (doanh thu)", events)
        ServerEventCapture().use { server ->
            requireNotNull(events).onAdPaid(AdValue(PrecisionType.PRECISE, 5_000L, "USD"))
            val rev = server.await("ad_revenue", 1).single()
            assertEquals("bn_turn", rev.get("placement").asString)
            assertEquals(3, rev.get("precision_type").asInt)
        }
    }

    @Test fun `GMA rect - click va impression luot get (flagShow 1) toi adsGetCb voi placement get (R-NG2 m1)`() = MainHandlerTimers.run { _ ->
        val pl = rect()
        pl.load("rect_home", NgBannerKit.Cb())
        val a = kit.loaded(0)
        val got = mutableListOf<String>()
        val cb = object : AdsShowCb {
            override fun onAdClick(placement: String?, originPl: String?, adId: String?) { got += "click:$placement" }
            override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { got += "impression:$placement" }
        }
        pl.getAdForShow("rect_settings", cb)
        val events = mockingDetails(a).invocations.last { it.method.name == "setAdEventCallback" }.arguments[0] as BannerAdEventCallback
        events.onAdImpression(); events.onAdClicked()
        assertEquals(listOf("impression:rect_settings", "click:rect_settings"), got)
    }

    /** B tới muộn sau watchdog: giữ làm quảng cáo đã load, không huỷ B lẫn A (đang hiện), không callback thêm. */
    private fun assertKept(pl: AdsPlBaseBn, b: BannerAd, a: BannerAd, cbB: NgBannerKit.Cb) {
        val item = LoadedAdFixture.state(pl as AdsPlBase).adEcmps!!.list[0]
        assertSame("(b) B muộn được giữ", kit.viewOf(b), item.adObject)
        assertTrue(item.isLoaded)
        assertEquals("B không bị huỷ", 0, kit.destroyed(b))
        assertEquals("A không bị huỷ", 0, kit.destroyed(a))
        assertEquals("không callback thêm cho lượt đã bỏ", 1, cbB.events.size)
    }
}
