package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsfb.AdsFbPlInters
import com.app.plugin.ads.adsfb.AdsFbPlRect
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlInters
import com.app.plugin.ads.adsmob.AdsMobPlRwi
import com.app.plugin.ads.adsmob.AdsMobPlrewarded
import com.app.plugin.logevent.ServerEventCapture
import com.facebook.ads.InterstitialAdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/**
 * 1.9.0 Task 6 (D14, D48; plan 1.8.0 D21 / R-3b m2): paid và click của một quảng cáo mang placement của **lượt show/attach
 * của chính quảng cáo đó** ([AdsPlBase.turnPl]), không phải placement slot đang giữ lúc callback tới. Kịch bản: quảng cáo X
 * hiện ở `full_home`, đóng; slot load Y và hiện ở `full_settings`; callback muộn của X (paid, click) tới sau đó.
 * R4 quét nguồn: mọi chỗ paid/click trong `ads/adsmob` + `ads/adsfb` (kể cả thư mục con, R4b) đọc qua `turnPl` với khoá
 * là quảng cáo của chính callback (R4c); 3 chỗ của `AdsMobPlNtFull.kt` miễn ở 1.9.0 (D48) đã sửa ở 2.0.0 C1a.
 * R5 (2.0.0 C1b, T6 m1): 27 chỗ impression/dismiss gửi callback của app cũng đọc qua `turnPl`, cùng luật khoá R4c.
 * 2.1.0 P3 (M15): dismiss và thưởng muộn của rwi AdMob (C2, C3) và thưởng muộn của rewarded (C4) cũng mang placement lượt
 * của quảng cáo; R6 = 31 chỗ.
 */
class ShowTurnPlacementTest {

    private class RecordingShowCb : AdsShowCb {
        val clicks = mutableListOf<String?>()
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) {
            clicks += placement
        }
    }

    private class RecordingGiftCb : AdsGiftShowCb {
        val dismissals = mutableListOf<String?>()
        val rewards = mutableListOf<String?>()
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            dismissals += placement
        }
        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) {
            rewards += placement
        }
    }

    private class NoLoadCb : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }

    private lateinit var context: Context
    private val activity = mock(Activity::class.java)
    private val unit = "ca-app-pub-3940256099942544/t6"

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
        AdsFbMy.instance.readyQueue.resetForTest(); AdsFbMy.instance.readyQueue.markReady()
    }

    @After
    fun tearDown() {
        admobQueue().resetForTest()
        AdsFbMy.instance.readyQueue.resetForTest()
    }

    /** Hai lượt AdMob interstitial trên cùng slot: X hiện `full_home` rồi đóng, Y hiện `full_settings`; [check] nhận X. */
    private fun twoGmaTurns(cb: RecordingShowCb, check: (InterstitialAd) -> Unit) = MainHandlerTimers.run { _ ->
        val loads = mutableListOf<InterstitialAdLoadCallback>()
        mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }.use {
            mockStatic(InterstitialAd::class.java).use { statics ->
                statics.`when`<Unit> { InterstitialAd.load(any(Context::class.java), anyString(), any(AdRequest::class.java), any()) }
                    .thenAnswer { loads += it.arguments[3] as InterstitialAdLoadCallback; null }
                val pl = AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default")
                pl.setActivity(activity)
                pl.parserEcpms(unit)
                val x = gmaAd()
                pl.load("full_home", NoLoadCb())
                loads[0].onAdLoaded(x)
                assertTrue(pl.show("full_home", cb, 0))
                val screen = ArgumentCaptor.forClass(FullScreenContentCallback::class.java)
                verify(x).fullScreenContentCallback = screen.capture()
                screen.value.onAdDismissedFullScreenContent()

                pl.load("full_settings", NoLoadCb())
                assertEquals("lượt 2 gửi request mới", 2, loads.size)
                loads[1].onAdLoaded(gmaAd())
                assertTrue(pl.show("full_settings", cb, 0))
                check(x)
            }
        }
    }

    private fun gmaAd(): InterstitialAd = mock(InterstitialAd::class.java).also {
        `when`(it.adUnitId).thenReturn(unit)
        `when`(it.responseInfo).thenReturn(mock(ResponseInfo::class.java))
    }

    @Test
    fun `R1 paid muon cua quang cao luot truoc mang placement luot do, khong phai luot moi`() =
        twoGmaTurns(RecordingShowCb()) { x ->
            val paid = ArgumentCaptor.forClass(OnPaidEventListener::class.java)
            verify(x).onPaidEventListener = paid.capture()
            val adValue = mock(AdValue::class.java)
            `when`(adValue.valueMicros).thenReturn(1_000L)
            `when`(adValue.currencyCode).thenReturn("USD")
            ServerEventCapture().use { server ->
                mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                    analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(mock(FirebaseAnalytics::class.java))
                    paid.value.onPaidEvent(adValue)        // impression muộn của X, sau khi slot đã hiện Y
                }
                val rev = server.await("ad_revenue", 1).single()
                assertEquals(
                    "R1: paid muộn của X phải mang placement lượt X (full_home), không phải lượt mới: $rev",
                    "full_home", rev.get("placement").asString
                )
            }
        }

    @Test
    fun `R2 click muon cua quang cao luot truoc mang placement luot do`() {
        val cb = RecordingShowCb()
        twoGmaTurns(cb) { x ->
            val screen = ArgumentCaptor.forClass(FullScreenContentCallback::class.java)
            verify(x).fullScreenContentCallback = screen.capture()
            screen.value.onAdClicked()                     // click của X báo muộn, sau khi slot đã hiện Y
        }
        assertEquals("R2: click muộn của X phải mang placement lượt X", listOf<String?>("full_home"), cb.clicks)
    }

    @Test
    fun `R3 Meta interstitial - paid va click muon mang placement luot cua quang cao do`() = MainHandlerTimers.run { _ ->
        val listeners = mutableListOf<InterstitialAdListener>()
        mockConstruction(com.facebook.ads.InterstitialAd::class.java) { ad, _ ->
            val builder = mock(com.facebook.ads.InterstitialAd.InterstitialAdLoadConfigBuilder::class.java)
            `when`(ad.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listeners += it.arguments[0] as InterstitialAdListener; builder }
            `when`(ad.show()).thenReturn(true)
        }.use { built ->
            val pl = AdsFbPlInters(context, mock(AdsBase::class.java), AdsIDDef.FBFullInters, "full_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb_t6_unit")
            val cb = RecordingShowCb()
            pl.load("full_home", NoLoadCb())
            val x = built.constructed()[0]
            listeners[0].onAdLoaded(x)
            assertTrue(pl.show("full_home", cb, 0))
            listeners[0].onInterstitialDismissed(x)
            pl.load("full_settings", NoLoadCb())
            assertEquals("lượt 2 dựng quảng cáo mới", 2, built.constructed().size)
            listeners[1].onAdLoaded(built.constructed()[1])
            assertTrue(pl.show("full_settings", cb, 0))

            ServerEventCapture().use { server ->
                listeners[0].onAdClicked(x)                 // callback muộn của X
                listeners[0].onLoggingImpression(x)
                val rev = server.await("ad_revenue", 1).single()
                assertEquals("R3: paid muộn của X (Meta) mang placement lượt X: $rev", "full_home", rev.get("placement").asString)
            }
            assertEquals("R3: click muộn của X (Meta) mang placement lượt X", listOf<String?>("full_home"), cb.clicks)
        }
        assertMetaRectGetTurn()
    }

    /**
     * R-T6 I-1: đường get (`getAdForShow` → `setGetPlacement`, như `NativeController`/`RectController`). Rect Meta A lấy ra ở
     * `rect_home`; slot load B và get ở `rect_settings`; paid và click muộn của A mang `rect_home` (M8: bỏ ghi lượt ở
     * `setGetPlacement` → `rect_settings`).
     */
    private fun assertMetaRectGetTurn() {
        val listeners = mutableListOf<com.facebook.ads.AdListener>()
        mockConstruction(com.facebook.ads.AdView::class.java) { view, _ ->
            val builder = mock(com.facebook.ads.AdView.AdViewLoadConfigBuilder::class.java)
            `when`(view.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listeners += it.arguments[0] as com.facebook.ads.AdListener; builder }
            `when`(builder.build()).thenReturn(mock(com.facebook.ads.AdView.AdViewLoadConfig::class.java))
        }.use { built ->
            val pl = AdsFbPlRect(context, mock(AdsBase::class.java), AdsIDDef.FBRect, "rect_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb-rect-t6")
            val cb = RecordingShowCb()
            pl.load("rect_home", NoLoadCb())
            val a = built.constructed()[0]
            listeners[0].onAdLoaded(a as com.facebook.ads.Ad)
            assertTrue("get lượt 1", pl.getAdForShow("rect_home", cb) != null)
            pl.load("rect_settings", NoLoadCb())
            assertEquals("lượt get 2 dựng view mới", 2, built.constructed().size)
            listeners[1].onAdLoaded(built.constructed()[1] as com.facebook.ads.Ad)
            assertTrue("get lượt 2", pl.getAdForShow("rect_settings", cb) != null)

            ServerEventCapture().use { server ->
                listeners[0].onAdClicked(a as com.facebook.ads.Ad)        // callback muộn của view A
                listeners[0].onLoggingImpression(a as com.facebook.ads.Ad)
                val rev = server.await("ad_revenue", 1).single()
                assertEquals("R3/I-1: paid muộn của rect A (get) mang placement lượt get của A: $rev", "rect_home", rev.get("placement").asString)
            }
            assertEquals("R3/I-1: click muộn của rect A (get) mang placement lượt get của A", listOf<String?>("rect_home"), cb.clicks)
        }
    }

    /** 2.0.0 C1a (D1, R-T6 m1): không còn chỗ miễn — 3 site của `AdsMobPlNtFull.kt` (D48) đọc `turnPl` như mọi site khác. */
    private val exempt = emptySet<String>()

    private class Site(val where: String, val arg: String, val src: String, val at: Int)

    private val site = Regex("""(PaidEvent\.(?:admob|metaDirect)|onAdClick)\(\s*(turnPl\([^)]*\)|[^,]+),""")
    private val turn = Regex("""turnPl\(([A-Za-z]+), (?:showPl|getPl)\)""")
    private val networkRoots = listOf("adsmob", "adsfb").map { File("src/main/java/com/app/plugin/ads/$it") }

    /** 2.0.0 C1b (T6 m1): impression (show/get) và dismiss gửi cho callback của app; nhóm 2 = đối số placement. */
    private val callbackSite = Regex("""(?:adsShowCb|adsGetCb)\?\.(?:onAdImpression|onAdDismissed)\(\s*(turnPl\([^)]*\)|[^,]+),""")

    /** Mọi chỗ paid/click (hoặc [pattern]) trong các file `.kt` dưới [roots], **kể cả thư mục con** (R4b, R-T6 m2). */
    private fun sites(roots: List<File>, pattern: Regex = site, arg: Int = 2): List<Site> = roots.flatMap { root ->
        assertTrue("không thấy ${root.absolutePath}", root.isDirectory)
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.flatMap { f ->
            val src = f.readText()
            pattern.findAll(src).map { m ->
                Site("${f.name}:${src.substring(0, m.range.first).count { it == '\n' } + 1}", m.groupValues[arg].trim(), src, m.range.first)
            }
        }.toList()
    }

    /**
     * R4c (R-T6 m1): khoá của `turnPl` là quảng cáo của chính callback = tham số hoặc biến cục bộ của hàm thành viên bao
     * site (`{ nativeAd: NativeAd ->`, `onAdLoaded(ad: …)`, `val adView`, `var loadedNative`), không phải trường của lớp
     * (`nativeAdShow` = quảng cáo của lượt đang hiện) hay tên bất kỳ.
     */
    private fun ownAdKey(s: Site): Boolean {
        val key = turn.matchEntire(s.arg)?.groupValues?.get(1) ?: return false
        val before = s.src.substring(0, s.at)
        val member = Regex("""(?m)^    (?:[a-z]+ )*fun """).findAll(before).lastOrNull() ?: return false
        val k = Regex.escape(key)
        return Regex("""(?:\(|,\s*|\{\s*)$k\s*(?::|->)|\b(?:val|var)\s+$k\b""").containsMatchIn(before.substring(member.range.first))
    }

    @Test
    fun `R4 moi cho paid va click trong adsmob va adsfb doc placement cua luot qua turnPl`() {
        val sites = sites(networkRoots)
        assertEquals("số chỗ paid + click trong adsmob + adsfb (17 + 18): ${sites.map { it.where }}", 35, sites.size)
        assertEquals("không còn chỗ miễn (2.0.0 C1a)", exempt, sites.map { it.where }.filter { it in exempt }.toSet())
        val live = sites.filter { it.where !in exempt && !turn.matches(it.arg) }.map { it.where to it.arg }
        assertEquals("paid/click đọc placement hiện hành thay vì turnPl(<quảng cáo>, showPl|getPl): $live", emptyList<Pair<String, String>>(), live)
    }

    @Test
    fun `R4b quet ca thu muc con - site o goi con van bi bat`() {
        val root = kotlin.io.path.createTempDirectory("r4b").toFile()
        try {
            File(root, "sub/deep").mkdirs()
            File(root, "Top.kt").writeText("fun a() {\n    PaidEvent.admob(turnPl(ad, showPl), originPl, x)\n}\n")
            File(root, "sub/deep/Nested.kt").writeText("fun b() {\n    cb?.onAdClick(showPl, originPl, id)\n}\n")
            val found = sites(listOf(root)).map { it.where to it.arg }
            assertEquals("R4b: site trong thư mục con phải được quét", listOf("Top.kt:2" to "turnPl(ad, showPl)", "Nested.kt:2" to "showPl"), found)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `R4c khoa cua turnPl la quang cao cua chinh callback, khong phai truong hay ten bat ky`() {
        val sites = sites(networkRoots)
        val foreign = sites.filterNot(::ownAdKey).map { it.where to it.arg }
        assertEquals("turnPl phải khoá bằng quảng cáo của chính callback: $foreign", emptyList<Pair<String, String>>(), foreign)
        assertEquals("cả 35 site khoá bằng quảng cáo của callback", 35, sites.count(::ownAdKey))
        val field = "class P {\n    var nativeAdShow: Any? = null\n    fun load() {\n        x { cb.onAdClick(turnPl(nativeAdShow, showPl), o, i) }\n    }\n}\n"
        val local = field.replace("    fun load() {\n", "    fun load() {\n        var loadedNative: Any? = null\n").replace("turnPl(nativeAdShow", "turnPl(loadedNative")
        val probe = { src: String -> ownAdKey(Site("P.kt", site.find(src)!!.groupValues[2].trim(), src, site.find(src)!!.range.first)) }
        assertEquals("R4c: trường của lớp không phải quảng cáo của callback", false, probe(field))
        assertEquals("R4c: biến cục bộ của hàm bao là quảng cáo của callback", true, probe(local))
    }

    @Test
    fun `R5 moi impression va dismiss gui app trong adsmob va adsfb doc placement cua luot qua turnPl`() {
        val sites = sites(networkRoots, callbackSite, arg = 1)
        assertEquals("số chỗ impression show 14 + get 4 + dismiss 9: ${sites.map { it.where }}", 27, sites.size)
        val live = sites.filter { !turn.matches(it.arg) || !ownAdKey(it) }.map { it.where to it.arg }
        assertEquals("impression/dismiss phải đọc turnPl(<quảng cáo của lượt>, showPl|getPl): $live", emptyList<Pair<String, String>>(), live)
    }

    /** 2.1.0 P3 (M15, R-P3 I2): R5 cộng dismiss + thưởng của rwi AdMob và `cbrw?.onAdRewarded` của rewarded AdMob + Meta. */
    private val turnCallbackSite =
        Regex("""(?:adsShowCb|adsGetCb|\bcb|\bcbrw|\(cb as\? AdsGiftShowCb\))\?\.(?:onAdImpression|onAdDismissed|onAdRewarded)\(\s*(turnPl\([^)]*\)|[^,]+),""")

    @Test
    fun `R6 impression, dismiss va thuong gui app (rwi, rewarded) doc placement cua luot qua turnPl - 31 cho`() {
        val sites = sites(networkRoots, turnCallbackSite, arg = 1)
        assertEquals("R5 27 + rwi dismiss/thưởng 2 + thưởng rewarded 2: ${sites.map { it.where }}", 31, sites.size)
        val rewarded = sites.count { it.where.startsWith("AdsMobPlrewarded.kt:") || it.where.startsWith("AdsFbPlrewarded.kt:") }
        assertEquals("rewarded AdMob + Meta: impression, dismiss, thưởng × 2 file", 6, rewarded)
        assertEquals("3 chỗ rwi (impression của R5 + dismiss + thưởng)", 3, sites.count { it.where.startsWith("AdsMobPlRwi.kt:") })
        val live = sites.filter { !turn.matches(it.arg) || !ownAdKey(it) }.map { it.where to it.arg }
        assertEquals("impression/dismiss/thưởng phải đọc turnPl(<quảng cáo của lượt>, showPl|getPl): $live", emptyList<Pair<String, String>>(), live)
    }

    /**
     * Hai lượt rwi AdMob trên cùng slot (2.1.0 P3, M15): X hiện ở `rwi_home`; slot bỏ X khi X còn trên màn hình (lượt đã
     * chốt, như watchdog) và hiện Y ở `rwi_settings`; [check] nhận callback toàn màn hình và listener thưởng của X.
     */
    private fun twoRwiTurns(cb: RecordingGiftCb, check: (FullScreenContentCallback, OnUserEarnedRewardListener) -> Unit) {
        val pl = AdsMobPlRwi(context, mock(AdsBase::class.java), AdsIDDef.AdmobRwi, "rwi_default")
        pl.setActivity(activity)
        val clock = LoadedAdFixture.Clock()
        val x = mock(RewardedInterstitialAd::class.java).also { `when`(it.adUnitId).thenReturn(unit) }
        val heldX = LoadedAdFixture.giveLoadedAd(pl, x, clock)
        assertTrue("show lượt X", pl.show("rwi_home", cb, 0))
        val screen = ArgumentCaptor.forClass(FullScreenContentCallback::class.java)
        verify(x).fullScreenContentCallback = screen.capture()
        val earned = ArgumentCaptor.forClass(OnUserEarnedRewardListener::class.java)
        verify(x).show(any(Activity::class.java), earned.capture())
        heldX.adObject = null
        heldX.isLoaded = false
        LoadedAdFixture.giveLoadedAd(pl, mock(RewardedInterstitialAd::class.java), clock)
        assertTrue("show lượt Y", pl.show("rwi_settings", cb, 0))
        check(screen.value, earned.value)
    }

    @Test
    fun `C2 rwi dismiss muon cua luot truoc mang placement luot do`() {
        val cb = RecordingGiftCb()
        twoRwiTurns(cb) { screen, _ -> screen.onAdDismissedFullScreenContent() }   // X đóng muộn, sau khi Y đã hiện
        assertEquals("C2: dismiss muộn của X (rwi) mang placement lượt X", listOf<String?>("rwi_home"), cb.dismissals)
    }

    @Test
    fun `C3 rwi thuong muon cua luot truoc mang placement luot do`() {
        val cb = RecordingGiftCb()
        twoRwiTurns(cb) { screen, earned ->
            earned.onUserEarnedReward(mock(RewardItem::class.java))
            screen.onAdDismissedFullScreenContent()                                // thưởng trao lúc X đóng muộn
        }
        assertEquals("C3: thưởng muộn của X (rwi) mang placement lượt X", listOf<String?>("rwi_home"), cb.rewards)
        assertEquals("C3: dismiss của X sau thưởng", listOf<String?>("rwi_home"), cb.dismissals)
    }

    /**
     * R-P3 I2: rewarded AdMob — X hiện `gift_home` và người dùng nhận thưởng; slot bỏ X khi X còn trên màn hình và hiện Y ở
     * `gift_settings`; X đóng muộn → `onAdRewarded` mang placement lượt X (cũ: `showPl` hiện hành).
     */
    @Test
    fun `C4 rewarded thuong muon cua luot truoc mang placement luot do`() {
        val cb = RecordingGiftCb()
        val pl = AdsMobPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.AdmobRewarded, "gift_default")
        pl.setActivity(activity)
        val clock = LoadedAdFixture.Clock()
        val x = mock(RewardedAd::class.java).also { `when`(it.adUnitId).thenReturn(unit) }
        val heldX = LoadedAdFixture.giveLoadedAd(pl, x, clock)
        assertTrue("show lượt X", pl.show("gift_home", cb, 0))
        val screen = ArgumentCaptor.forClass(FullScreenContentCallback::class.java)
        verify(x).fullScreenContentCallback = screen.capture()
        val earned = ArgumentCaptor.forClass(OnUserEarnedRewardListener::class.java)
        verify(x).show(any(Activity::class.java), earned.capture())
        earned.value.onUserEarnedReward(mock(RewardItem::class.java))
        heldX.adObject = null
        heldX.isLoaded = false
        LoadedAdFixture.giveLoadedAd(pl, mock(RewardedAd::class.java), clock)
        assertTrue("show lượt Y", pl.show("gift_settings", cb, 0))
        screen.value.onAdDismissedFullScreenContent()                          // X đóng muộn, thưởng trao lúc đóng
        assertEquals("C4: thưởng muộn của X (rewarded) mang placement lượt X", listOf<String?>("gift_home"), cb.rewards)
        assertEquals("C4: dismiss của X (đã qua turnPl từ C1b)", listOf<String?>("gift_home"), cb.dismissals)
    }

    @Test // 2.2.0 PSH (N07, Q-A): click + impression của hai native full chọn callback theo lượt của quảng cáo (C4, C4b, C5)
    fun `R7 click va impression cua hai NtFull khong doc ecmpLoad flagShow song`() {
        val body = Regex("""(?ms)^( +)override fun (onAdClicked|onAdImpression|onLoggingImpression)\(.*?^\1\}$""")
        val bodies = listOf("adsmob/AdsMobPlNtFull.kt", "adsfb/AdsFbPlNtFull.kt").map { File(networkRoots[0].parentFile, it) }
            .flatMap { f -> body.findAll(f.readText()).map { "${f.name}:${it.groupValues[2]}" to it.value }.toList() }
        assertEquals("4 thân callback (AdMob + Meta, click + impression): ${bodies.map { it.first }}", 4, bodies.size)
        assertEquals("R7: thân callback đọc ecmpLoad.flagShow sống", emptyList<String>(), bodies.filter { "ecmpLoad.flagShow" in it.second }.map { it.first })
    }

    @Test // 2.3.0 MINR (PSH m1, Q12): census thân isGetTurn() của hai NtFull; âm: neo ở helper Meta hoặc neo mất đều đỏ
    fun `R7b isGetTurn chi doc flagShow qua mot du phong neo o AdsMobPlNtFull`() {
        fun bad(m: Map<String, String>) = m.filter { (n, t) -> val b = Regex("""(?ms)^( *)[^\n]*fun isGetTurn\(\)(?:[^\n]*\{[ \t]*\n.*?^\1\}|[^\n]*)""").findAll(t).map { it.value }.toList()
            b.isEmpty() || b.joinToString().split("ecmpLoad.flagShow").size - 1 != (if (n == "mob") 1 else 0) || (n == "mob" && b.none { "?: (ecmpLoad.flagShow != 0)" in it }) }.keys
        val m = mapOf("mob" to "adsmob/AdsMobPlNtFull.kt", "fb" to "adsfb/AdsFbPlNtFull.kt").mapValues { File(networkRoots[0].parentFile, it.value).readText() }
        assertEquals("R7b: census thật", emptySet<String>(), bad(m))
        assertEquals("R7b âm: neo chép sang helper Meta", setOf("fb"), bad(m + ("fb" to m.getValue("fb").replace("{ nativeAd in getTurns }", "{ nativeAd in getTurns } ?: (ecmpLoad.flagShow != 0)"))))
        assertEquals("R7b âm: neo ở Mob đổi", setOf("mob"), bad(m + ("mob" to m.getValue("mob").replace("?: (ecmpLoad.flagShow != 0)", "?: (ecmpLoad.flagShow == 1)"))))
        // R7b' (2.4.0 CARRY-T #3): thân BLOCK nhiều dòng của isGetTurn ở Meta cũng bị census (cũ: chỉ đọc dòng khai báo, thân block lọt qua).
        val blockFb = m.getValue("fb").replace(Regex("""( *)private fun isGetTurn\(\) = [^\n]*""")) { r -> "${r.groupValues[1]}private fun isGetTurn(): Boolean {\n${r.groupValues[1]}    return ecmpLoad.flagShow != 0\n${r.groupValues[1]}}" }
        assertEquals("R7b' âm: thân block Meta đọc flagShow", setOf("fb"), bad(m + ("fb" to blockFb)))
    }
}
