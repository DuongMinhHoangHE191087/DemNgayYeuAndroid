package com.app.plugin.nextgen

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlInters
import com.app.plugin.ads.adsmob.AdsMobPlOpen
import com.app.plugin.ads.adsmob.AdsMobPlRwi
import com.app.plugin.ads.adsmob.AdsMobPlrewarded
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.MediationAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/**
 * 2.4.0 NG3 (Hợp đồng B "NG3", luật 36) — interstitial / app open / rewarded / rwi trên GMA Next-Gen, và phần GMA của
 * `AdsMobPlIntersExpiryTest`, `AdsMobPlOpenExpiryTest`, `WaterfallSyncShowFailTest`, `TurnCallbackPlacementTest`,
 * `RewardedInterstitialControllerTest`, `LateLoadCallbackTest`: NF1 load không Context, NF4 lý do show-fail, NF5 app open 4 h,
 * NF6 dismiss từ luồng nền về main qua `poster` (dismiss → nạp lại giữ thứ tự), đăng ký callback chỉ qua `MainPost`.
 */
class NgFullScreenTest {
    private lateinit var context: Context
    private val activity = mock(Activity::class.java)
    private val clock = LoadedAdFixture.Clock()
    private val requests = ArrayList<Pair<AdRequest, AdLoadCallback<*>>>()
    private val posted = ArrayDeque<Runnable>()
    private val savedInters = AdsMobPlInters.loader
    private val savedOpen = AdsMobPlOpen.loader
    private val savedRw = AdsMobPlrewarded.loader
    private val savedRwi = AdsMobPlRwi.loader

    private class Cb : AdsGiftShowCb, AdsLoadInnerCb {
        val events = mutableListOf<String>()
        var onDismiss: () -> Unit = {}
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) { events += "loaded:$adId" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "loadFail:$error" }
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "showFail:$err" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed:$placement" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed:$placement"; onDismiss() }
        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) { events += "rewarded" }
    }

    @Before fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        sdkState(READY)
        AdsMobPlInters.loader = { r, cb -> requests += r to cb }
        AdsMobPlOpen.loader = { r, cb -> requests += r to cb }
        AdsMobPlrewarded.loader = { r, cb -> requests += r to cb }
        AdsMobPlRwi.loader = { r, cb -> requests += r to cb }
        MainPost.isMain = { true }
    }

    @After fun tearDown() {
        AdsMobPlInters.loader = savedInters; AdsMobPlOpen.loader = savedOpen; AdsMobPlrewarded.loader = savedRw; AdsMobPlRwi.loader = savedRwi
        sdkState(PENDING)
        MainPost.resetForTest()
        AdsPolicy.current = AdsPolicy.DEFAULT
    }

    /** Trạng thái init của `AdsMobMy.instance` (NG1, `private`): test coi SDK đã sẵn sàng, rồi trả về như cũ. */
    private fun sdkState(s: Int) {
        val my = AdsMobMy.instance
        AdsMobMy::class.java.getDeclaredField("state").apply { isAccessible = true }.setInt(my, s)
        (AdsMobMy::class.java.getDeclaredField("queue").apply { isAccessible = true }.get(my) as MutableCollection<*>).clear()
    }

    private fun <T : AdsPlBase> placed(pl: T, ad: Any?): Pair<T, AdsEcmp?> {
        pl.setActivity(activity)
        return pl to ad?.let { LoadedAdFixture.giveLoadedAd(pl, it, clock) }
    }

    private fun inters(ad: Any? = null) = placed(AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default"), ad)
    private fun open(ad: Any? = null) = placed(AdsMobPlOpen(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullOpen, "full_open"), ad)

    @Test fun `NF1 bon dinh dang nap qua X load(AdRequest, AdLoadCallback) khong Context, ket qua ve adsLoadCb`() {
        val pls = listOf<AdsPlBase>(
            inters().first, open().first,
            AdsMobPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.AdmobRewarded, "gift_default").also { it.setActivity(activity) },
            AdsMobPlRwi(context, mock(AdsBase::class.java), AdsIDDef.AdmobRwi, "rwi_default").also { it.setActivity(activity) },
        )
        val ads = listOf(mock(InterstitialAd::class.java), mock(AppOpenAd::class.java), mock(RewardedAd::class.java), mock(RewardedInterstitialAd::class.java))
        pls.forEachIndexed { i, pl ->
            pl.parserEcpms("$UNIT$i")
            val cb = Cb()
            pl.load("pl$i", cb)
            assertEquals("một request mỗi định dạng", i + 1, requests.size)
            assertEquals("$UNIT$i", requests[i].first.adUnitId)
            `when`((ads[i] as com.google.android.libraries.ads.mobile.sdk.common.Ad).adUnitId).thenReturn("$UNIT$i")
            @Suppress("UNCHECKED_CAST") (requests[i].second as AdLoadCallback<Any>).onAdLoaded(ads[i])
            assertEquals(listOf("loaded:$UNIT$i"), cb.events)
        }
    }

    private fun showAndCapture(pl: AdsMobPlInters, ad: InterstitialAd, cb: Cb): InterstitialAdEventCallback {
        assertTrue(pl.show("full_home", cb, 0))
        val captor = ArgumentCaptor.forClass(InterstitialAdEventCallback::class.java)
        verify(ad).adEventCallback = captor.capture()
        verify(ad).show(activity)
        return captor.value
    }

    @Test fun `NF4 show-fail interstitial - ly do ad_show_fail mang ma mediationAdError, khong co thi ma SDK`() {
        val ad = mock(InterstitialAd::class.java)
        val (pl, _) = inters(ad)
        val cb = Cb()
        val events = showAndCapture(pl, ad, cb)
        ServerEventCapture().use { server ->
            events.onAdFailedToShowFullScreenContent(FullScreenContentError(FullScreenContentError.ErrorCode.MEDIATION_SHOW_ERROR, "m", MediationAdError(1001, "x", "d")))
            assertEquals(listOf("full_home" to "${Constants.AdShowFailReason.FAIL_TO_SHOW_FULL_CONTENT}_1001"), server.showFails(1))
        }
        assertEquals(listOf("showFail:1001-m"), cb.events)
        val ad2 = mock(InterstitialAd::class.java)
        val (pl2, _) = inters(ad2)
        val cb2 = Cb()
        val ev2 = showAndCapture(pl2, ad2, cb2)
        ev2.onAdFailedToShowFullScreenContent(FullScreenContentError(FullScreenContentError.ErrorCode.APP_NOT_FOREGROUND, "bg", null))
        assertEquals(listOf("showFail:${FullScreenContentError.ErrorCode.APP_NOT_FOREGROUND.value}-bg"), cb2.events)
    }

    @Test fun `NF5 app open con han toi 4 gio, het han bo voi app_open_expired, show tra false khong callback`() {
        val ad = mock(AppOpenAd::class.java)
        val (pl, item) = open(ad)
        val fourH = 4 * 60 * 60_000L
        clock.nowMs = item!!.loadedAtMillis + fourH - 60_000L
        assertTrue("3 h 59 vẫn còn hạn (Legacy: app open 4 h)", pl.hasLoadedAd())
        clock.nowMs = item.loadedAtMillis + fourH
        val cb = Cb()
        ServerEventCapture().use { server ->
            assertFalse(pl.show("full_open", cb, 0))
            assertTrue(Constants.AdShowFailReason.APP_OPEN_EXPIRED in server.showFails(1).map { it.second })
        }
        assertEquals(emptyList<String>(), cb.events)
        assertFalse(item.isLoaded)
    }

    @Test fun `interstitial het han 60 phut voi ad_expired (phan GMA AdsMobPlIntersExpiryTest)`() {
        val (pl, item) = inters(mock(InterstitialAd::class.java))
        clock.nowMs = item!!.loadedAtMillis + 59 * 60_000L
        assertTrue(pl.hasLoadedAd())
        clock.nowMs = item.loadedAtMillis + 60 * 60_000L
        ServerEventCapture().use { server ->
            assertFalse(pl.hasLoadedAd())
            assertEquals(listOf(Constants.AdShowFailReason.AD_EXPIRED), server.showFails(1).map { it.second })
        }
    }

    @Test fun `NF6 dismiss tu luong nen toi controller tren main sau poster, dismiss truoc roi moi nap lai`() {
        val ad = mock(InterstitialAd::class.java)
        `when`(ad.adUnitId).thenReturn("$UNIT-a")
        val pl = AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default").also { it.setActivity(activity) }
        pl.parserEcpms("$UNIT-next")
        LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        val order = ArrayList<String>()
        val cb = Cb().apply { onDismiss = { order += "dismissed"; pl.load("full_home", this); order += "reload:${requests.size}" } }
        val events = showAndCapture(pl, ad, cb)
        MainPost.isMain = { false }
        MainPost.poster = { r -> posted.addLast(r) }
        events.onAdDismissedFullScreenContent()                     // luồng nền của SDK
        assertEquals("chưa tới controller trước khi main chạy", emptyList<String>(), order)
        assertTrue("slot vẫn đang hiện tới khi main xử lý", LoadedAdFixture.state(pl).isShowing)
        MainPost.isMain = { true }
        while (posted.isNotEmpty()) posted.removeFirst().run()
        assertEquals(listOf("dismissed", "reload:1"), order)
        assertFalse(LoadedAdFixture.state(pl).isShowing)
    }

    @Test fun `rewarded - thuong trao mot lan luc dong, onAdRewarded truoc onAdDismissed (turn placement)`() {
        val ad = mock(RewardedAd::class.java)
        `when`(ad.adUnitId).thenReturn("$UNIT-rw")
        val pl = AdsMobPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.AdmobRewarded, "gift_default").also { it.setActivity(activity) }
        LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        val cb = Cb()
        assertTrue(pl.show("gift_home", cb, 0))
        val calls = mockingDetails(ad).invocations
        val ev = calls.single { it.method.name == "setAdEventCallback" }.arguments[0] as RewardedAdEventCallback
        val show = calls.single { it.method.name == "show" }
        assertEquals(activity, show.arguments[0])
        (show.arguments[1] as OnUserEarnedRewardListener).onUserEarnedReward(mock(RewardItem::class.java))
        (ev as AdEventCallback).onAdDismissedFullScreenContent()
        ev.onAdDismissedFullScreenContent()
        assertEquals(listOf("rewarded", "dismissed:gift_home", "dismissed:gift_home"), cb.events)
    }

    @Test fun `dang ky callback trong override NG3 chi qua MainPost (luat 36)`() {
        val files = listOf("AdsMobPlInters", "AdsMobPlOpen", "AdsMobPlrewarded", "AdsMobPlRwi").map { File("$OVERRIDES/$it.kt") }
        assertTrue("sai cwd: ${files.first().absoluteFile}", files.all { it.isFile })
        val sites = Regex("""adEventCallback\s*=\s*(.*)|loader\((.*)|\.show\(activity,\s*(.*)""")
        val direct = Regex("""\b(InterstitialAd|AppOpenAd|RewardedAd|RewardedInterstitialAd)\.load\(""")
        val bad = files.flatMap { f ->
            val code = f.readLines().map { it.trim() }.filterNot { it.startsWith("*") || it.startsWith("//") }
            val found = code.filter { sites.containsMatchIn(it) }
            // R-NG3 m2: ≥ 1 site mỗi loại mỗi file (không pass rỗng), và cấm `XAd.load(` trực tiếp ngoài seam `loader`.
            listOfNotNull("${f.name}: không có adEventCallback =".takeIf { found.none { "adEventCallback" in it } },
                "${f.name}: không có loader(".takeIf { found.none { "loader(" in it } }) +
                found.filter { "MainPost." !in it }.map { "${f.name}: $it" } +
                code.filter { direct.containsMatchIn(it) && "internal var loader" !in it }.map { "${f.name}: load trực tiếp: $it" }
        }
        assertEquals(emptyList<String>(), bad)
    }

    private companion object {
        const val READY = 1
        const val PENDING = 0
        const val UNIT = "ca-app-pub-3940256099942544/"
        const val OVERRIDES = "src/main/java/com/app/plugin/ads/adsmob"
    }
}
