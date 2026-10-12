package com.app.plugin.nextgen

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlNt
import com.app.plugin.ads.adsmob.AdsMobPlNtCl
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/**
 * 2.4.0 NG4 (Hợp đồng B "NG4", luật 36) — native `nt_` và NtCl trên GMA Next-Gen, cùng phần native của
 * `NtFullTurnPlacementTest` và `GmaLoadContextPlacementTest` (nextgen: load không nhận Context, cổng activity null giữ):
 * NN1 `NativeAdLoader` + `NativeAdRequest(NATIVE)` + VideoOptions; NN2 `registerNativeAd` chỉ trên main sau `poster`;
 * NN3 layout nextgen lớp gốc Next-Gen, cùng tập id với Legacy (bản sao Legacy trong test resources); NN4 NtCl gỡ/huỷ.
 */
class NgNativeTest {
    private lateinit var context: Context
    private val activity = mock(Activity::class.java)
    private val requests = ArrayList<Pair<NativeAdRequest, NativeAdLoaderCallback>>()
    private val posted = ArrayDeque<Runnable>()
    private val savedLoader = AdsMobPlNt.loader
    private val savedInflater = AdsMobPlNt.inflater
    private val adView = mock(NativeAdView::class.java)
    private val mediaView = mock(MediaView::class.java)
    private val btClose = mock(Button::class.java)

    private class Cb : AdsShowCb, AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "loadFail:$error" }
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { events += "impression:$placement" }
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) { events += "click:$placement" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed:$placement" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed:$placement" }
    }

    @Before fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(resources.displayMetrics).thenReturn(DisplayMetrics())
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        sdkReady(true)
        AdsMobPlNt.loader = { r, cb -> requests += r to cb }
        AdsMobPlNt.inflater = { inflater() }
        MainPost.isMain = { true }
    }

    @After fun tearDown() {
        AdsMobPlNt.loader = savedLoader
        AdsMobPlNt.inflater = savedInflater
        sdkReady(false)
        MainPost.resetForTest()
    }

    /** Trạng thái init của `AdsMobMy.instance` (NG1, private): coi như đã sẵn sàng trong test, rồi trả lại. */
    private fun sdkReady(ready: Boolean) {
        val my = AdsMobMy.instance
        AdsMobMy::class.java.getDeclaredField("state").apply { isAccessible = true }.setInt(my, if (ready) 1 else 0)
        (AdsMobMy::class.java.getDeclaredField("queue").apply { isAccessible = true }.get(my) as MutableCollection<*>).clear()
    }

    /** Inflate trên JVM: root có `NativeAdView` (id `ad_native_view_lo`/`ntcl_ntadview`), `MediaView` và nút đóng. */
    private fun inflater(): LayoutInflater {
        `when`(adView.findViewById<View>(anyInt())).thenReturn(mock(TextView::class.java))
        `when`(adView.findViewById<View>(R.id.ad_media)).thenReturn(mediaView)
        `when`(adView.findViewById<View>(R.id.ntcl_media)).thenReturn(mediaView)
        val close = mock(RelativeLayout::class.java)
        `when`(close.findViewById<View>(R.id.ntcl_close)).thenReturn(btClose)
        val root = mock(View::class.java)
        `when`(root.findViewById<View>(anyInt())).thenReturn(adView)
        `when`(root.findViewById<View>(R.id.ntlo_close)).thenReturn(close)
        return mock(LayoutInflater::class.java).also { `when`(it.inflate(anyInt(), any<ViewGroup>(), anyBoolean())).thenReturn(root) }
    }

    private fun nt() = AdsMobPlNt(context, mock(AdsBase::class.java), AdsIDDef.AdmobNative, "nt_default").also { it.setActivity(activity) }
    private fun ntCl() = AdsMobPlNtCl(context, mock(AdsBase::class.java), AdsIDDef.AdmobNtCl, "ntcl_default").also { it.setActivity(activity) }

    private fun loaded(pl: AdsPlBase, placement: String): NativeAd {
        pl.parserEcpms(UNIT)
        pl.load(placement, Cb())
        val ad = mock(NativeAd::class.java)
        requests.last().second.onNativeAdLoaded(ad)
        return ad
    }

    @Test fun `NN1 Nt va NtCl nap qua NativeAdLoader voi NativeAdRequest NATIVE + VideoOptions, khong Context`() {
        loaded(nt(), "nt_home"); loaded(ntCl(), "ntcl_home")
        assertEquals(2, requests.size)
        val (ntReq, clReq) = requests[0].first to requests[1].first
        assertEquals(listOf(NativeAd.NativeAdType.NATIVE), ntReq.nativeAdTypes)
        assertEquals(UNIT, ntReq.adUnitId)
        assertTrue("Nt: tắt tiếng + điều khiển riêng", ntReq.videoOptions?.startMuted == true && ntReq.videoOptions?.customControlsRequested == true)
        assertTrue("NtCl: có tiếng + điều khiển riêng", clReq.videoOptions?.startMuted == false && clReq.videoOptions?.customControlsRequested == true)
        assertEquals(NativeAd.NativeMediaAspectRatio.ANY, ntReq.mediaAspectRatio)
    }

    @Test fun `cong activity null giu (GmaLoadContextPlacementTest nextgen), 0 request`() {
        val pl = AdsMobPlNt(context, mock(AdsBase::class.java), AdsIDDef.AdmobNative, "nt_default")
        pl.parserEcpms(UNIT)
        val cb = Cb()
        pl.load("nt_home", cb)
        assertEquals(listOf("loadFail:isActivitynull"), cb.events)
        assertTrue(requests.isEmpty())
    }

    @Test fun `NN2 registerNativeAd chi chay tren main sau poster`() {
        val pl = nt()
        val ad = loaded(pl, "nt_home")
        MainPost.isMain = { false }
        MainPost.poster = { r -> posted.addLast(r) }
        assertNotNull(pl.getAdForShow("nt_home", Cb()))
        assertEquals("0 registerNativeAd trước khi main chạy", 0, registers())
        while (posted.isNotEmpty()) posted.removeFirst().run()
        verify(adView, times(1)).registerNativeAd(ad, mediaView)
    }

    @Test fun `paid va click muon cua luot truoc mang placement luot do (NtFullTurnPlacementTest phan native)`() {
        val pl = nt()
        val x = loaded(pl, "nt_home")
        val cb = Cb()
        LoadedAdFixture.state(pl).adsShowCb = cb   // NativeController gắn callback của lượt
        assertNotNull(pl.getAdForShow("nt_home", cb))
        loaded(pl, "nt_settings"); assertNotNull(pl.getAdForShow("nt_settings", cb))
        val events = mockingDetails(x).invocations.single { it.method.name == "setAdEventCallback" }.arguments[0] as NativeAdEventCallback
        events.onAdClicked(); events.onAdImpression()
        events.onAdPaid(AdValue(PrecisionType.PRECISE, 10L, "USD"))
        assertEquals(listOf("click:nt_home", "impression:nt_home"), cb.events)
    }

    @Test fun `NN4 NtCl - host an thi huy quang cao mot lan, nut dong bao dismissed luot do roi huy`() {
        val ntClField = AdsHelper::class.java.getDeclaredField("listAdsNtCl").apply { isAccessible = true }
        val savedNtCl = ntClField.get(AdsHelper.instance)
        ntClField.set(AdsHelper.instance, mutableMapOf<Int, Any>())   // AdsHelper.hideNativeCl duyệt map này (như NativeCollapseControllerTest)
        try { ntClCloseAndHostHide() } finally { ntClField.set(AdsHelper.instance, savedNtCl) }
    }

    private fun ntClCloseAndHostHide() {
        val pl = ntCl()
        val ad = loaded(pl, "ntcl_home")
        val cb = Cb()
        assertTrue(pl.show("ntcl_home", cb, 0))
        verify(adView).registerNativeAd(ad, mediaView)
        val close = mockingDetails(btClose).invocations.single { it.method.name == "setOnClickListener" }.arguments[0] as View.OnClickListener
        close.onClick(btClose)
        assertEquals(listOf("showed:ntcl_home", "dismissed:ntcl_home"), cb.events)
        verify(ad, times(1)).destroy()
        pl.hideAd(0)
        verify(ad, times(1)).destroy()
    }

    @Test fun `NN3 layout nextgen lop goc Next-Gen, cung tap id voi ban Legacy`() {
        for (name in LAYOUTS) {
            val ng = File("src/main/res/layout/$name.xml")
            assertTrue("thiếu override $name (cwd=${File(".").absoluteFile})", ng.isFile)
            val legacy = requireNotNull(javaClass.classLoader?.getResource("legacy-layouts/$name.xml")) { "thiếu bản sao Legacy $name" }.readText()
            val text = ng.readText()
            assertTrue("$name: còn lớp native Legacy", "com.google.android.gms.ads.nativead." !in text)
            assertTrue("$name: lớp gốc NativeAdView Next-Gen", "<com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView" in text)
            assertTrue("$name: MediaView Next-Gen", "<com.google.android.libraries.ads.mobile.sdk.nativead.MediaView" in text)
            assertEquals("$name: tập id", ids(legacy), ids(text))
            assertEquals("$name: chỉ đổi tên lớp", legacy.replace(LEGACY_PKG, NG_PKG), text)
        }
    }

    @Test fun `dang ky callback va registerNativeAd trong override NG4 chi qua MainPost (luat 36)`() {
        // R-NG4 m2: mỗi file phải có đủ loại site nó dùng (không pass rỗng), và cấm `NativeAdLoader.load(` trực tiếp ngoài seam.
        val kinds = mapOf(
            "AdsMobPlNt" to listOf("adEventCallback", "loader(", "registerNativeAd("),
            "AdsMobPlNtCl" to listOf("adEventCallback", "loader("),
            "AdsMobNtClView" to listOf("registerNativeAd("),
        )
        val sites = Regex("""adEventCallback\s*=|loader\(|registerNativeAd\(""")
        val bad = kinds.flatMap { (name, need) ->
            val f = File("$OVERRIDES/$name.kt")
            assertTrue("sai cwd: ${f.absoluteFile}", f.isFile)
            val code = f.readLines().map { it.trim() }.filterNot { it.startsWith("*") || it.startsWith("//") }.map { it.substringBefore("//") }
            val found = code.filter { sites.containsMatchIn(it) }
            need.filter { k -> found.none { k in it } }.map { "$name: thiếu site $it" } +
                found.filter { "MainPost." !in it }.map { "$name: $it" } +
                code.filter { "NativeAdLoader.load(" in it && "internal var loader" !in it }.map { "$name: load trực tiếp: $it" }
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test fun `Nt nap xong khi host da huy - huy quang cao, bao activity unavailable (R-NG4 m1)`() {
        val pl = nt()
        pl.parserEcpms(UNIT)
        val cb = Cb()
        pl.load("nt_home", cb)
        `when`(activity.isDestroyed).thenReturn(true)
        val ad = mock(NativeAd::class.java)
        requests.single().second.onNativeAdLoaded(ad)
        verify(ad, times(1)).destroy()
        assertEquals(listOf("loadFail:activity unavailable"), cb.events)
    }

    @Test fun `Nt va NtCl - native muon khong duoc giu thi bi destroy (LateLoadCallbackTest nextgen)`() {
        for (pl in listOf<AdsPlBase>(nt(), ntCl())) {
            pl.parserEcpms("ntcl-unit")
            val item = LoadedAdFixture.ecmps(pl).list[0]
            val held = mock(NativeAd::class.java)
            val late = mock(NativeAd::class.java)
            item.adObject = held
            item.isLoaded = true
            pl.keepLateAd(item, late)
            assertTrue("quảng cáo đang giữ không bị thay", item.adObject === held)
            verify(late, times(1)).destroy()
            verify(held, times(0)).destroy()
        }
    }

    private fun registers() = mockingDetails(adView).invocations.count { it.method.name == "registerNativeAd" }

    private fun ids(xml: String) = Regex("""android:id="@\+id/([A-Za-z0-9_]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()

    private companion object {
        const val UNIT = "ca-app-pub-3940256099942544/2247696110"
        const val OVERRIDES = "src/main/java/com/app/plugin/ads/adsmob"
        const val LEGACY_PKG = "com.google.android.gms.ads.nativead."
        const val NG_PKG = "com.google.android.libraries.ads.mobile.sdk.nativead."
        val LAYOUTS = listOf(
            "appplugin_admob_native_cl", "appplugin_admob_native_layout_small",
            "appplugin_admob_native_layout_language_2", "appplugin_layout_native_full_pager",
        )
    }
}
