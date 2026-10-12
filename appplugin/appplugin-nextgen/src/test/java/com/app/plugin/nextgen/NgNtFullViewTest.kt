package com.app.plugin.nextgen

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.app.plugin.R
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsmob.AdsMobNtFullBinder
import com.app.plugin.ads.adsmob.AdsMobNtFullView
import com.google.android.libraries.ads.mobile.sdk.common.VideoController
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/**
 * 2.4.0 NG5a (Hợp đồng B "NG5a", luật 36): Full2 trên GMA Next-Gen. NV1 `VideoOptions.setCustomControlsRequested()` +
 * `isCustomControlsEnabled()` [S15] quyết định nút play/pause/mute của ta; NV2 `VideoLifecycleCallbacks` [S16] chỉ chạm view
 * sau `MainPost` (seam nền, `poster` FIFO); NV3 binder/view gắn đủ asset vào `NativeAdView` Next-Gen theo id Legacy,
 * `registerNativeAd(ad, mediaView)` trên main; layout như NN3 của NG4; kiểm điểm đăng ký (D19).
 */
class NgNtFullViewTest {
    private val posted = ArrayDeque<Runnable>()
    private val savedInflater = AdsMobNtFullBinder.inflater
    private val bar = mock(View::class.java)
    private val play = mock(ImageButton::class.java)
    private val pause = mock(ImageButton::class.java)
    private val mute = mock(ImageButton::class.java)
    private val root = mock(View::class.java)
    private val vc = mock(VideoController::class.java)

    @Before fun setUp() {
        MainPost.isMain = { true }
        `when`(root.findViewById<View>(R.id.appplugin_ntfull_controls)).thenReturn(bar)
        `when`(root.findViewById<View>(R.id.appplugin_ntfull_play)).thenReturn(play)
        `when`(root.findViewById<View>(R.id.appplugin_ntfull_pause)).thenReturn(pause)
        `when`(root.findViewById<View>(R.id.appplugin_ntfull_mute)).thenReturn(mute)
    }

    @After fun tearDown() {
        AdsMobNtFullBinder.inflater = savedInflater
        MainPost.resetForTest()
    }

    private fun background() {
        MainPost.isMain = { false }
        MainPost.poster = { r -> posted.addLast(r) }
    }

    private fun drain() { while (posted.isNotEmpty()) posted.removeFirst().run() }

    private fun videoAd(hasVideo: Boolean, custom: Boolean): NativeAd {
        val media = mock(MediaContent::class.java)
        `when`(media.hasVideoContent).thenReturn(hasVideo)
        `when`(media.videoController).thenReturn(vc)
        `when`(vc.isCustomControlsEnabled).thenReturn(custom)
        return mock(NativeAd::class.java).also { `when`(it.mediaContent).thenReturn(media) }
    }

    private fun click(v: View) =
        (mockingDetails(v).invocations.single { it.method.name == "setOnClickListener" }.arguments[0] as View.OnClickListener).onClick(v)

    private fun touches(vararg views: View) = views.sumOf { mockingDetails(it).invocations.size }

    @Test fun `NV1 Full2 yeu cau dieu khien rieng, isCustomControlsEnabled true thi play pause mute cua ta hien va dieu khien video`() {
        val options = AdsMobNtFullView.videoOptions()
        assertTrue("Full2: yêu cầu điều khiển riêng [S15]", options.customControlsRequested)
        assertFalse("Full2: có tiếng như Legacy", options.startMuted)
        assertSame(vc, AdsMobNtFullView.videoControls(root, videoAd(hasVideo = true, custom = true)))
        verify(bar).visibility = View.VISIBLE
        click(play); verify(vc).play()
        click(pause); verify(vc).pause()
        click(mute); verify(vc).mute(true)
    }

    @Test fun `NV1 isCustomControlsEnabled false hoac khong video thi an, khong gan nut`() {
        for ((hasVideo, custom) in listOf(true to false, false to true)) {
            clearInvocations(bar, play, pause, mute, vc)
            AdsMobNtFullView.videoControls(root, videoAd(hasVideo, custom))
            verify(bar).visibility = View.GONE
            verify(bar, never()).visibility = View.VISIBLE
            assertEquals("video=$hasVideo custom=$custom: 0 listener", 0,
                listOf(play, pause, mute).sumOf { v -> mockingDetails(v).invocations.count { it.method.name == "setOnClickListener" } })
        }
    }

    @Test fun `NV2 VideoLifecycleCallbacks qua MainPost video, luong nen 0 cham view truoc khi main chay`() {
        AdsMobNtFullView.videoControls(root, videoAd(hasVideo = true, custom = true))
        val cb = mockingDetails(vc).invocations.single { it.method.name == "setVideoLifecycleCallbacks" }.arguments[0]
            as VideoController.VideoLifecycleCallbacks
        clearInvocations(play, pause, mute)
        background()
        cb.onVideoPause(); cb.onVideoMute(true)
        assertEquals("0 chạm view trước khi main chạy", 0, touches(play, pause, mute))
        drain()
        verify(play).visibility = View.VISIBLE
        verify(pause).visibility = View.GONE
        verify(mute).setImageResource(android.R.drawable.ic_lock_silent_mode)
        clearInvocations(play, pause)
        cb.onVideoPlay()
        assertEquals(0, touches(play, pause))
        drain()
        verify(play).visibility = View.GONE
        verify(pause).visibility = View.VISIBLE
    }

    /** `NativeAdView` có asset theo id Legacy; trả map id → view để kiểm. */
    private fun assetView(): Pair<NativeAdView, Map<Int, View>> {
        val adView = mock(NativeAdView::class.java)
        val views = mapOf(
            R.id.ad_media to mock(MediaView::class.java), R.id.ad_headline to mock(TextView::class.java),
            R.id.ad_body to mock(TextView::class.java), R.id.ad_call_to_action to mock(Button::class.java),
            R.id.ad_app_icon to mock(ImageView::class.java), R.id.ad_price to mock(TextView::class.java),
        )
        views.forEach { (id, v) -> `when`(adView.findViewById<View>(id)).thenReturn(v) }
        return adView to views
    }

    private fun assetAd(): NativeAd = mock(NativeAd::class.java).also {
        `when`(it.headline).thenReturn("H"); `when`(it.body).thenReturn("B")
        `when`(it.callToAction).thenReturn("C"); `when`(it.price).thenReturn("P")
    }

    private fun verifyAssets(adView: NativeAdView, v: Map<Int, View>) {
        verify(adView).headlineView = v[R.id.ad_headline]
        verify(adView).bodyView = v[R.id.ad_body]
        verify(adView).callToActionView = v[R.id.ad_call_to_action]
        verify(adView).priceView = v[R.id.ad_price]
        verify(v[R.id.ad_headline] as TextView).text = "H"
    }

    @Test fun `NV3 binder gan du asset vao NativeAdView Next-Gen theo id Legacy, registerNativeAd(ad, mediaView) chi tren main`() {
        val (adView, v) = assetView()
        val loview = mock(View::class.java).also { `when`(it.findViewById<View>(R.id.ad_native_view_lo)).thenReturn(adView) }
        val inflater = mock(LayoutInflater::class.java)
        `when`(inflater.inflate(anyInt(), isNull<ViewGroup>(), anyBoolean())).thenReturn(loview)
        AdsMobNtFullBinder.inflater = { inflater }
        val ad = assetAd()
        background()
        assertSame(loview, AdsMobNtFullBinder.populateNativeAdView(mock(Context::class.java), ad))
        verify(inflater).inflate(R.layout.appplugin_admob_native_layout_full, null, false)
        verifyAssets(adView, v)
        verify(adView).iconView = v[R.id.ad_app_icon]
        assertEquals("0 registerNativeAd trước khi main chạy", 0, mockingDetails(adView).invocations.count { it.method.name == "registerNativeAd" })
        drain()
        verify(adView).registerNativeAd(ad, v[R.id.ad_media] as MediaView)
    }

    @Test fun `NV3 dialog Full2 gan asset theo id Legacy, an icon khi showIc false, body null thi GONE`() {
        val (adView, v) = assetView()
        val ad = assetAd()
        AdsMobNtFullView.bind(adView, ad, showIc = true)
        verifyAssets(adView, v)
        verify(adView).iconView = v[R.id.ad_app_icon]
        verify(adView).registerNativeAd(ad, v[R.id.ad_media] as MediaView)
        val (adView2, v2) = assetView()
        `when`(adView2.bodyView).thenReturn(v2[R.id.ad_body])
        `when`(ad.body).thenReturn(null)
        AdsMobNtFullView.bind(adView2, ad, showIc = false)
        verify(adView2, never()).iconView = v2[R.id.ad_app_icon]
        verify(v2[R.id.ad_app_icon] as View).visibility = View.INVISIBLE
        verify(v2[R.id.ad_body] as View).visibility = View.GONE
        verify(adView2).registerNativeAd(ad, v2[R.id.ad_media] as MediaView)
    }

    @Test fun `NV5 releaseNativeAdView go callback video (khong giu nut cua dialog) va huy view, goi hai lan no-op (R-NG5a m1)`() {
        val dialog = AdsMobNtFullView(mock(Context::class.java), mock(NativeAd::class.java), 3, false, true)
        val adView = mock(NativeAdView::class.java)
        AdsMobNtFullView::class.java.getDeclaredField("vcControlCurrShow").apply { isAccessible = true }.set(dialog, vc)
        AdsMobNtFullView::class.java.getDeclaredField("mNativeAdView").apply { isAccessible = true }.set(dialog, adView)
        dialog.releaseNativeAdView()
        verify(vc).videoLifecycleCallbacks = null
        verify(adView).destroy()
        dialog.releaseNativeAdView()
        verify(adView).destroy()
    }

    @Test fun `NV4 ba layout Full2 lop goc Next-Gen, id Legacy giu, chi them khoi dieu khien video`() {
        for ((rel, controls) in LAYOUTS) {
            val ng = File("src/main/res/$rel")
            assertTrue("thiếu override $rel (cwd=${File(".").absoluteFile})", ng.isFile)
            val copy = if (rel.startsWith("layout-land/")) rel else rel.removePrefix("layout/")
            val legacy = requireNotNull(javaClass.classLoader?.getResource("legacy-layouts/$copy")) { "thiếu bản sao Legacy $rel" }.readText()
            val text = ng.readText()
            assertTrue("$rel: còn lớp native Legacy", LEGACY_PKG !in text)
            assertTrue("$rel: NativeAdView/MediaView Next-Gen", "<${NG_PKG}NativeAdView" in text && "<${NG_PKG}MediaView" in text)
            val block = Regex(""" *<!-- appplugin-nextgen: video controls -->[\s\S]*?<!-- /appplugin-nextgen: video controls -->\n\n""")
            assertEquals("$rel: khối điều khiển video", if (controls) 1 else 0, block.findAll(text).count())
            assertEquals("$rel: tập id", ids(legacy) + (if (controls) CONTROL_IDS else emptySet()), ids(text))
            assertEquals("$rel: ngoài khối chỉ đổi tên lớp", legacy.replace(LEGACY_PKG, NG_PKG), text.replace(block, ""))
        }
    }

    @Test fun `dang ky video callback va registerNativeAd trong override NG5a chi qua MainPost, moi file co diem dang ky (D19)`() {
        val sites = Regex("""videoLifecycleCallbacks\s*=(?!\s*null\b)|setVideoLifecycleCallbacks\((?!\s*null\b)|registerNativeAd\(|NativeAdLoader\.load\(""") // gỡ (= null) không phải đăng ký
        for ((name, need) in mapOf("AdsMobNtFullView" to listOf("videoLifecycleCallbacks", "registerNativeAd("), "AdsMobNtFullBinder" to listOf("registerNativeAd("))) {
            val f = File("$OVERRIDES/$name.kt")
            assertTrue("sai cwd: ${f.absoluteFile}", f.isFile)
            val code = f.readLines().map { it.substringBefore("//").trim() }.filter { !it.startsWith("*") && sites.containsMatchIn(it) }
            assertEquals("$name: điểm đăng ký không qua MainPost", emptyList<String>(), code.filter { "MainPost." !in it })
            for (kind in need) assertTrue("$name: thiếu điểm đăng ký $kind", code.any { kind in it && "MainPost." in it })
        }
    }

    private fun ids(xml: String) = Regex("""android:id="@\+id/([A-Za-z0-9_]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()

    private companion object {
        const val OVERRIDES = "src/main/java/com/app/plugin/ads/adsmob"
        const val LEGACY_PKG = "com.google.android.gms.ads.nativead."
        const val NG_PKG = "com.google.android.libraries.ads.mobile.sdk.nativead."
        val CONTROL_IDS = setOf("appplugin_ntfull_controls", "appplugin_ntfull_play", "appplugin_ntfull_pause", "appplugin_ntfull_mute")
        val LAYOUTS = listOf(
            "layout/appplugin_admob_native_full.xml" to true,
            "layout-land/appplugin_admob_native_full.xml" to true,
            "layout/appplugin_admob_native_layout_full.xml" to false,
        )
    }
}
