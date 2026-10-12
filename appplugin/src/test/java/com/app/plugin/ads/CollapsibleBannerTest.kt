package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlBanner
import com.app.plugin.until.LogUtil
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings

/**
 * 1.9.0 Task 3 (Hợp đồng D, D4/D23/D26/D37): banner GMA collapsible theo resource `ads_collapsible_placements`.
 * Request của slot có tên mang extras `collapsible=bottom` tới lần nạp thành công đầu tiên (khoá/giá trị theo tài liệu Google
 * https://developers.google.com/admob/android/banner/collapsible — `extras.putString("collapsible", "bottom")`; trang
 * không có `collapsible_request_id` nên SDK không gửi); sau đó không có. Đi qua `AdsMobPlBanner.tryLoad` thật,
 * `AdRequest.Builder`/`Bundle`/`AdView` giả để đọc extras từng request.
 */
class CollapsibleBannerTest {
    private val unit = "ca-app-pub-3940256099942544/6300978111"
    private val pkg = "com.example.app"
    private val activity = mock(Activity::class.java)

    private fun admobQueue(): SdkReadyQueue =
        AdsMobMy::class.java.getDeclaredField("readyQueue").apply { isAccessible = true }.get(AdsMobMy.instance) as SdkReadyQueue

    @Before
    fun setUp() {
        admobQueue().resetForTest(); admobQueue().markReady()
        LogUtil.setFlagLog(3)
        CollapsibleBanner.resetForTest()
    }

    @After
    fun tearDown() {
        admobQueue().resetForTest()
        LogUtil.setFlagLog(1)
        CollapsibleBanner.resetForTest()
    }

    /** Context có resource `ads_collapsible_placements` = [collapsible] (`null` = không khai) và `admob_banner` = [banners]. */
    /** [gdprNoConsent]: prefs TCF `IABTCF_gdprApplies=1`, purpose 1 không đồng ý → `AdRequestFactory` thêm `npa=1`. */
    private fun context(collapsible: Array<String>?, banners: String = "bn_home,$unit#bn_settings,$unit",
                        gdprNoConsent: Boolean = false): Context {
        val ctx = mock(Context::class.java)
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        if (collapsible != null) {
            `when`(res.getIdentifier("ads_collapsible_placements", "array", pkg)).thenReturn(11)
            `when`(res.getStringArray(11)).thenReturn(collapsible)
        }
        `when`(res.getIdentifier("admob_banner", "string", pkg)).thenReturn(12)
        `when`(res.getString(12)).thenReturn(banners)
        `when`(ctx.resources).thenReturn(res)
        `when`(ctx.packageName).thenReturn(pkg)
        val prefs = mock(SharedPreferences::class.java)
        if (gdprNoConsent) {
            `when`(prefs.contains("IABTCF_gdprApplies")).thenReturn(true)
            `when`(prefs.getInt("IABTCF_gdprApplies", 0)).thenReturn(1)
            `when`(prefs.getString("IABTCF_PurposeConsents", null)).thenReturn("0111111111")
        }
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return ctx
    }

    private class Run(val requests: List<Map<String, String>>, val listeners: List<AdListener>)

    /** Request đang chờ lỗi, rồi slot load lại (request mới). */
    private fun failThenReload(pl: AdsMobPlBanner, listeners: List<AdListener>) {
        listeners.last().onAdFailedToLoad(mock(LoadAdError::class.java))
        pl.load(pl.slot(), null)
    }

    /** Request đang chờ thành công, quảng cáo được dùng (mục hết `isLoaded`), rồi slot load lại (request mới). */
    private fun succeedThenReload(pl: AdsMobPlBanner, listeners: List<AdListener>) {
        listeners.last().onAdLoaded()
        LoadedAdFixture.state(pl).adEcmps!!.list[0].apply { isLoaded = false; adObject = null }
        pl.load(pl.slot(), null)
    }

    private fun AdsMobPlBanner.slot() = LoadedAdFixture.state(this).originPl

    /**
     * Slot [slot] của [ctx]: request 1 rồi [script] (placement + listener từng request). Trả extras AdMob của từng
     * request (theo thứ tự `AdRequest.Builder` dựng).
     */
    private fun run(ctx: Context, slot: String = "bn_home", collapsibleView: Boolean = false,
                    script: (AdsMobPlBanner, List<AdListener>) -> Unit = { _, _ -> }): Run = MainHandlerTimers.run {
        val puts = mutableMapOf<Any, MutableMap<String, String>>()
        val requests = mutableListOf<MutableMap<String, String>>()
        val listeners = mutableListOf<AdListener>()
        mockConstruction(Bundle::class.java) { b, _ ->
            puts[b] = mutableMapOf()
            doAnswer { puts.getValue(b)[it.getArgument(0)] = it.getArgument(1); null }.`when`(b).putString(anyString(), anyString())
        }.use {
            mockConstruction(AdRequest.Builder::class.java, withSettings().defaultAnswer(Answers.RETURNS_SELF)) { b, _ ->
                val extras = mutableMapOf<String, String>().also { requests += it }
                `when`(b.build()).thenReturn(mock(AdRequest::class.java))
                doAnswer { extras.putAll(puts[it.getArgument(1)] ?: emptyMap()); b }.`when`(b).addNetworkExtrasBundle(any(), any())
            }.use {
                mockConstruction(AdView::class.java) { view, _ ->
                    doAnswer { listeners += it.arguments[0] as AdListener; null }.`when`(view).adListener = any()
                    `when`(view.adUnitId).thenReturn(unit)
                    `when`(view.isCollapsible).thenReturn(collapsibleView)
                }.use {
                    val pl = AdsMobPlBanner(ctx, mock(AdsBase::class.java), AdsIDDef.AdmobBanner, slot)
                    pl.setActivity(activity)
                    pl.parserEcpms(unit)
                    pl.load(slot, null)
                    script(pl, listeners)
                }
            }
        }
        Run(requests, listeners)
    }

    // C1
    @Test
    fun `slot co trong ads_collapsible_placements - request dau co collapsible=bottom`() {
        val r = run(context(arrayOf("bn_home")))
        assertEquals(1, r.requests.size)
        assertEquals("bottom", r.requests[0]["collapsible"])
        assertEquals("trang Google không có collapsible_request_id: không gửi", null, r.requests[0]["collapsible_request_id"])
    }

    // C2
    @Test
    fun `request sau lan nap thanh cong dau (load lai) khong co extras collapsible`() {
        val r = run(context(arrayOf("bn_home"))) { pl, listeners -> succeedThenReload(pl, listeners) }
        assertEquals("hai request của slot", 2, r.requests.size)
        assertEquals("bottom", r.requests[0]["collapsible"])
        assertEquals("request 2 không có collapsible", null, r.requests[1]["collapsible"])
    }

    // C6 (D37)
    @Test
    fun `nap dau that bai - request ke van collapsible, sau nap thanh cong thi khong`() {
        val r = run(context(arrayOf("bn_home"))) { pl, listeners ->
            failThenReload(pl, listeners)
            succeedThenReload(pl, listeners)
        }
        assertEquals("ba request của slot", 3, r.requests.size)
        assertEquals("bottom", r.requests[0]["collapsible"])
        assertEquals("nạp đầu thất bại không dùng hết extras", "bottom", r.requests[1]["collapsible"])
        assertEquals("sau onAdLoaded đầu tiên không còn extras", null, r.requests[2]["collapsible"])
    }

    // C7 (R-T3 I2): consent giữ nguyên — request collapsible mang cả npa=1 như đường thường (AdRequestFactory).
    @Test
    fun `GDPR khong dong y - request collapsible mang ca collapsible=bottom va npa=1`() {
        val r = run(context(arrayOf("bn_home"), gdprNoConsent = true)) { pl, listeners -> succeedThenReload(pl, listeners) }
        assertEquals(2, r.requests.size)
        assertEquals(mapOf("collapsible" to "bottom", "npa" to "1"), r.requests[0].toMap())
        assertEquals("đường thường sau lần nạp đầu: chỉ npa=1", mapOf("npa" to "1"), r.requests[1].toMap())
    }

    // C3
    @Test
    fun `slot khong co trong danh sach - khong extras collapsible`() {
        val r = run(context(arrayOf("bn_settings")))
        assertEquals(1, r.requests.size)
        assertEquals(null, r.requests[0]["collapsible"])
    }

    // C4
    @Test
    fun `mot muc sai (khong bn_ hoac khong co trong ads_ids) - bo ca danh sach, mot logE`() = mockStatic(Log::class.java).use { log ->
        val r = run(context(arrayOf("bn_home", "bn_missing")))
        assertEquals("bn_home hợp lệ nhưng cả danh sách bị bỏ", null, r.requests[0]["collapsible"])
        log.verify({ Log.e(eq(LogUtil.TAG), startsWith("ads collapsible: resource ads_collapsible_placements ignored")) }, times(1))

        CollapsibleBanner.resetForTest()
        assertEquals(emptySet<String>(), CollapsibleBanner.parse(arrayOf("bn_home", "home"), setOf("bn_home", "home")))
        log.verify({ Log.e(eq(LogUtil.TAG), startsWith("ads collapsible: resource ads_collapsible_placements ignored")) }, times(2))
        assertEquals(setOf("bn_home"), CollapsibleBanner.parse(arrayOf("bn_home"), setOf("bn_home")))
        log.verify({ Log.e(eq(LogUtil.TAG), startsWith("ads collapsible:")) }, times(2))
    }

    // C5
    @Test
    fun `load xong log collapsible=isCollapsible`() = mockStatic(Log::class.java).use { log ->
        run(context(arrayOf("bn_home")), collapsibleView = true) { _, listeners -> listeners[0].onAdLoaded() }
        log.verify({ Log.d(eq(LogUtil.TAG), contains("collapsible=true")) }, times(1))
        log.verify({ Log.d(eq(LogUtil.TAG), contains("collapsible=false")) }, never())
    }
}
