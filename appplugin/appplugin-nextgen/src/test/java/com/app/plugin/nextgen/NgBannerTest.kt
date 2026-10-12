package com.app.plugin.nextgen

import android.os.Bundle
import android.util.Log
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.CollapsibleBanner
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.MainHandlerTimers
import com.app.plugin.ads.adsmob.AdsMobPlBanner
import com.app.plugin.ads.adsmob.AdsMobPlRect
import com.app.plugin.nextgen.NgBannerKit.Companion.UNIT
import com.app.plugin.nextgen.NgBannerKit.Companion.activity
import com.app.plugin.nextgen.NgBannerKit.Companion.context
import com.app.plugin.nextgen.NgBannerKit.Companion.extras
import com.app.plugin.firbase.PaidEvent
import com.app.plugin.until.LogUtil
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.times
import java.io.File

/**
 * 2.4.0 NG2 (Hợp đồng B "NG2", luật 36): banner/rect/collapsible trên GMA Next-Gen. NB1 `BannerAdRequest` large anchored
 * adaptive [S10][S11] + `npa`; NB2 `collapsible=bottom` tới lần nạp thành công đầu (bản nextgen của `CollapsibleBannerTest`)
 * [S12]; NB3 rect `MEDIUM_RECTANGLE`; NB4 callback load/refresh nền chỉ gắn view sau `poster`; NB5 lỗi muộn của lượt đã bỏ
 * bị bỏ (bản nextgen của `LateBannerLoadCallbackTest` GMA banner); kiểm điểm đăng ký (D19).
 */
class NgBannerTest {
    private val kit = NgBannerKit()

    @Before fun setUp() { kit.install(); CollapsibleBanner.resetForTest() }

    @After fun tearDown() { kit.uninstall(); CollapsibleBanner.resetForTest() }

    private fun banner(ctx: android.content.Context = context(), slot: String = "bn_home") =
        AdsMobPlBanner(ctx, mock(AdsBase::class.java), AdsIDDef.AdmobBanner, slot).also { it.setActivity(activity()); it.parserEcpms(UNIT) }

    private fun rect() = AdsMobPlRect(context(), mock(AdsBase::class.java), AdsIDDef.AdmobRect, "rect_default")
        .also { it.setActivity(activity()); it.parserEcpms(UNIT) }

    /** Request đang chờ thành công, quảng cáo được dùng (mục hết `isLoaded`), rồi slot load lại. */
    private fun succeedThenReload(pl: AdsMobPlBanner) {
        kit.loaded(kit.requests.lastIndex)
        LoadedAdFixture.state(pl).adEcmps!!.list[0].apply { isLoaded = false; adObject = null }
        pl.load("bn_home", null)
    }

    private fun failThenReload(pl: AdsMobPlBanner) { kit.failed(kit.requests.lastIndex); pl.load("bn_home", null) }

    private fun requestExtras() = kit.requests.map { extras(it.first.googleExtrasBundle) }

    @Test fun `NB1 banner nap qua BannerAd load voi BannerAdRequest large anchored adaptive va npa, khong collapsible`() =
        mockConstruction(Bundle::class.java).use {
            banner(context(denied = true), slot = "bn_settings").load("bn_settings", null)
            assertEquals(1, kit.requests.size)
            val req = kit.requests[0].first
            assertEquals(UNIT, req.adUnitId)
            assertTrue("large anchored adaptive [S11]: ${req.adSize}", req.adSize.isLargeAnchoredAdaptiveBanner)
            assertEquals(listOf(mapOf("npa" to "1")), requestExtras())
        }

    @Test fun `NB2 collapsible=bottom toi lan nap thanh cong dau, giu npa, nap dau loi van collapsible`() =
        mockConstruction(Bundle::class.java).use {
            val pl = banner(context(collapsible = arrayOf("bn_home"), denied = true))
            pl.load("bn_home", null)
            failThenReload(pl)
            succeedThenReload(pl)
            assertEquals(listOf(
                mapOf("collapsible" to "bottom", "npa" to "1"), mapOf("collapsible" to "bottom", "npa" to "1"), mapOf("npa" to "1"),
            ), requestExtras())
        }

    @Test fun `NB2 muc sai trong ads_collapsible_placements bo ca danh sach, mot logE`() = mockStatic(Log::class.java).use { log ->
        mockConstruction(Bundle::class.java).use { banner(context(collapsible = arrayOf("bn_home", "bn_missing"))).load("bn_home", null) }
        assertEquals("bn_home hợp lệ nhưng cả danh sách bị bỏ", listOf(emptyMap<String, String>()), requestExtras())
        log.verify({ Log.e(eq(LogUtil.TAG), startsWith("ads collapsible: resource ads_collapsible_placements ignored")) }, times(1))
        assertEquals(setOf("bn_home"), CollapsibleBanner.parse(arrayOf("bn_home"), setOf("bn_home")))
    }

    @Test fun `NB3 rect nap MEDIUM_RECTANGLE, khong extras collapsible`() = mockConstruction(Bundle::class.java).use {
        rect().load("rect_home", null)
        assertEquals(1, kit.requests.size)
        assertEquals(AdSize.MEDIUM_RECTANGLE, kit.requests[0].first.adSize)
        assertEquals(listOf(emptyMap<String, String>()), requestExtras())
    }

    @Test fun `NB4 callback load va refresh tu luong nen chi gan view sau poster`() {
        val pl = banner()
        val cb = NgBannerKit.Cb()
        pl.load("bn_home", cb)
        val item = LoadedAdFixture.state(pl).adEcmps!!.list[0]
        kit.background()
        val ad = kit.loaded(0)
        assertEquals("0 getView trước khi main chạy", 0, mockingDetails(ad).invocations.count { it.method.name == "getView" })
        assertEquals(null, item.adObject)
        assertEquals(emptyList<String>(), cb.events)
        kit.drain()
        assertEquals(listOf("loaded"), cb.events)
        assertSame(kit.viewOf(ad), item.adObject)
        kit.refresh(ad).onAdRefreshed()
        assertEquals("refresh chưa tới trước khi main chạy", listOf("loaded"), cb.events)
        kit.drain()
        assertEquals(listOf("loaded", "loaded"), cb.events)
    }

    @Test fun `NB5 loi va thanh cong muon cua luot da bo khong toi luot moi, view muon bi huy, refresh van onLoaded`() =
        MainHandlerTimers.run { t ->
            val pl = banner()
            val state = LoadedAdFixture.state(pl)
            val cb1 = NgBannerKit.Cb(); pl.load("bn_home", cb1)
            t.advanceTo(60_000)
            assertEquals(listOf("fail:load timeout"), cb1.events)
            val cb2 = NgBannerKit.Cb(); pl.load("bn_home", cb2)
            assertEquals("request 2 đã gửi", 2, kit.requests.size)
            kit.requests[0].second.onAdFailedToLoad(NgBannerKit.error())
            assertEquals("lỗi muộn không tới lượt mới", emptyList<String>(), cb2.events)
            assertEquals("lỗi muộn không đẩy waterfall", 0, state.adEcmps!!.idxCurrEcpm)
            assertEquals("lỗi muộn không gửi request mới", 2, kit.requests.size)
            assertTrue(state.isLoading)
            val late = kit.loaded(0)
            assertEquals("thành công muộn không tới lượt mới", emptyList<String>(), cb2.events)
            assertEquals("view muộn bị huỷ (mục đang có request 2)", 1, kit.destroyed(late))
            val fresh = kit.loaded(1)
            assertEquals(listOf("loaded"), cb2.events)
            assertSame(kit.viewOf(fresh), state.adEcmps!!.list[0].adObject)
            assertFalse(state.isLoading)
            kit.refresh(fresh).onAdRefreshed()
            assertEquals("auto-refresh vẫn onLoaded", listOf("loaded", "loaded"), cb2.events)
            assertEquals(0, kit.destroyed(fresh))
            assertEquals(1, cb1.events.size)
        }

    @Test fun `doanh thu onAdPaid precision enum sang Int theo ten`() {
        assertEquals(listOf(0, 1, 2, 3, -1), listOf(PrecisionType.UNKNOWN, PrecisionType.ESTIMATED, PrecisionType.PUBLISHER_PROVIDED,
            PrecisionType.PRECISE, null).map { PaidEvent.precision(it) }) // NG5b: helper NG2 gộp vào PaidEvent
    }

    @Test fun `dang ky callback trong override NG2 chi qua MainPost, moi file co diem dang ky (D19)`() {
        val sites = Regex("""loader\(|adEventCallback\s*=|bannerAdRefreshCallback\s*=|BannerAd\.load\(""")
        val need = mapOf(
            "adsmob/AdsMobPlBanner" to listOf("loader(", "adEventCallback", "bannerAdRefreshCallback"),
            "adsmob/AdsMobPlRect" to listOf("loader(", "adEventCallback", "bannerAdRefreshCallback"),
        )
        for ((name, kinds) in need) {
            val f = File("$OVERRIDES/$name.kt")
            assertTrue("sai cwd: ${f.absoluteFile}", f.isFile)
            val code = f.readLines().map { it.substringBefore("//").trim() }
                .filter { !it.startsWith("*") && sites.containsMatchIn(it) && !it.startsWith("@Volatile internal var loader") }
            assertEquals("$name: điểm đăng ký không qua MainPost", emptyList<String>(), code.filter { "MainPost." !in it })
            for (kind in kinds) assertTrue("$name: thiếu điểm đăng ký $kind", code.any { kind in it })
        }
    }

    private companion object {
        const val OVERRIDES = "src/main/java/com/app/plugin/ads"
    }
}
