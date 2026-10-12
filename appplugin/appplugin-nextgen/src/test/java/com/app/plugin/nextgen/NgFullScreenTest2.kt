package com.app.plugin.nextgen

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsConfigOb
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfigManager
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.MainHandlerTimers
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlInters
import com.app.plugin.ads.adsmob.AdsMobPlOpen
import com.app.plugin.ads.adsmob.AdsMobPlRwi
import com.app.plugin.ads.adsmob.AdsMobPlrewarded
import com.app.plugin.logevent.ServerEventCapture
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`

/**
 * 2.4.0 NG3 fix round (R-NG3 I1/m1/m4, D21-240): phần GMA còn thiếu của `LateLoadCallbackTest`, `AdsMobPlIntersExpiryTest`,
 * `AdsMobPlOpenExpiryTest`, `RewardedInterstitialControllerTest` W8, `PaidEventTest` R2 và `WaterfallSyncShowFailTest` (hành vi
 * mới) trên GMA Next-Gen; thưởng tới SAU dismiss (callback nền) vẫn được trao đúng một lần.
 */
class NgFullScreenTest2 {
    private lateinit var context: Context
    private val activity = mock(Activity::class.java)
    private val clock = LoadedAdFixture.Clock()
    private val requests = ArrayList<Pair<AdRequest, AdLoadCallback<*>>>()
    private val savedInters = AdsMobPlInters.loader
    private val savedRw = AdsMobPlrewarded.loader

    private class Cb : AdsGiftShowCb, AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) { events += "loaded:$adId" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "loadFail:$error" }
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "showFail:$err" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed:$placement" }
        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) { events += "rewarded:$placement" }
    }

    @Before fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        sdkState(1)
        AdsMobPlInters.loader = { r, cb -> requests += r to cb }
        AdsMobPlrewarded.loader = { r, cb -> requests += r to cb }
        MainPost.isMain = { true }
    }

    @After fun tearDown() {
        AdsMobPlInters.loader = savedInters; AdsMobPlrewarded.loader = savedRw
        sdkState(0)
        MainPost.resetForTest()
        AdsPolicy.current = AdsPolicy.DEFAULT
    }

    private fun sdkState(s: Int) {
        val my = AdsMobMy.instance
        AdsMobMy::class.java.getDeclaredField("state").apply { isAccessible = true }.setInt(my, s)
        (AdsMobMy::class.java.getDeclaredField("queue").apply { isAccessible = true }.get(my) as MutableCollection<*>).clear()
    }

    private fun inters() = AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default").also { it.setActivity(activity) }
    private fun gmaInters(unit: String) = mock(InterstitialAd::class.java).also { `when`(it.adUnitId).thenReturn(unit) }

    /** Callback sự kiện (đặt lúc show) và listener thưởng của [ad] mock, đọc từ lời gọi đã ghi. */
    private fun events(ad: Any): AdEventCallback =
        mockingDetails(ad).invocations.last { it.method.name == "setAdEventCallback" }.arguments[0] as AdEventCallback
    private fun earned(ad: Any): OnUserEarnedRewardListener =
        mockingDetails(ad).invocations.last { it.method.name == "show" }.arguments[1] as OnUserEarnedRewardListener

    @Test fun `GMA interstitial - callback muon cua request da bo khong toi luot moi (LateLoadCallbackTest)`() = MainHandlerTimers.run { t ->
        val pl = inters()
        pl.parserEcpms("${UNIT}first;${UNIT}second")
        val state = LoadedAdFixture.state(pl)
        val cb1 = Cb()
        pl.load("full_home", cb1)
        t.advanceTo(60_000)
        assertEquals(listOf("loadFail:load timeout"), cb1.events)
        val cb2 = Cb()
        pl.load("full_home", cb2)
        assertEquals(2, requests.size)
        @Suppress("UNCHECKED_CAST") val first = requests[0].second as AdLoadCallback<InterstitialAd>
        @Suppress("UNCHECKED_CAST") val second = requests[1].second as AdLoadCallback<InterstitialAd>
        val late = gmaInters("${UNIT}first")
        first.onAdLoaded(late)
        first.onAdFailedToLoad(mock(LoadAdError::class.java))
        assertEquals("muộn không tới lượt mới", emptyList<String>(), cb2.events)
        assertNotSame(late, state.adEcmps!!.list[0].adObject)
        assertEquals("lỗi muộn không đẩy waterfall", 0, state.adEcmps!!.idxCurrEcpm)
        val fresh = gmaInters("${UNIT}first")
        second.onAdLoaded(fresh)
        assertEquals(listOf("loaded:${UNIT}first"), cb2.events)
        assertSame(fresh, state.adEcmps!!.list[0].adObject)
    }

    @Test fun `quang cao muon duoc keepLateAd giu, hien thi doanh thu dung mot lan, imp_id chung ad_show va ad_revenue`() = MainHandlerTimers.run { t ->
        val pl = inters()
        pl.parserEcpms("${UNIT}only")
        pl.load("full_home", Cb())
        t.advanceTo(60_000)                                         // watchdog: slot rảnh, mục trống
        val late = gmaInters("${UNIT}only")
        @Suppress("UNCHECKED_CAST") (requests[0].second as AdLoadCallback<InterstitialAd>).onAdLoaded(late)
        val cb = Cb()
        pl.load("full_home", cb)
        assertEquals("lần load sau trả quảng cáo giữ muộn, không request mới", listOf("loaded:${UNIT}only"), cb.events)
        assertEquals(1, requests.size)
        assertTrue(pl.show("full_home", cb, 0))
        ServerEventCapture().use { server ->
            events(late).onAdPaid(AdValue(PrecisionType.PRECISE, 1_234L, "USD"))
            val rev = server.await("ad_revenue", 1)
            val show = server.await("ad_show", 1)
            assertEquals("doanh thu đúng 1 lần", 1, rev.size)
            assertEquals("1234", rev.single().get("value_micros").asString)
            assertEquals("imp_id chung (PaidEventTest R2)", rev.single().get("imp_id").asString, show.single().get("imp_id").asString)
        }
    }

    @Test fun `interstitial qua 60 phut - load lai, khong bao onLoaded quang cao cu (AdsMobPlIntersExpiryTest)`() {
        val pl = inters()
        val item = LoadedAdFixture.giveLoadedAd(pl, gmaInters("${UNIT}old"), clock).apply { adsId = "${UNIT}old" }
        clock.nowMs = item.loadedAtMillis + 60 * 60_000L + 1
        val cb = Cb()
        pl.load("full_home", cb)
        assertEquals("quảng cáo hết hạn không báo onLoaded", emptyList<String>(), cb.events)
        assertEquals("nạp lại: một request mới", listOf("${UNIT}old"), requests.map { it.first.adUnitId })
        assertTrue(LoadedAdFixture.state(pl).isLoading)
        assertFalse(item.isLoaded)
        AdsPolicy.current = AdsPolicy.parse("""{"fullscreen_max_age_minutes":30}""")
        val pl2 = inters()
        val item2 = LoadedAdFixture.giveLoadedAd(pl2, gmaInters("unit-2"), clock)
        clock.nowMs = item2.loadedAtMillis + 30 * 60_000L - 1
        assertTrue("tuổi tối đa đọc từ fullscreen_max_age_minutes", pl2.hasLoadedAd())
        clock.nowMs = item2.loadedAtMillis + 30 * 60_000L + 1
        assertFalse(pl2.hasLoadedAd())
    }

    @Test fun `rwi - thuong va dismiss mot lan ke ca khi SDK bao lap (W8)`() {
        val ad = mock(RewardedInterstitialAd::class.java).also { `when`(it.adUnitId).thenReturn("${UNIT}rwi") }
        val pl = AdsMobPlRwi(context, mock(AdsBase::class.java), AdsIDDef.AdmobRwi, "rwi_default").also { it.setActivity(activity) }
        LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        val cb = Cb()
        assertTrue(pl.show("rwi_home", cb, 0))
        earned(ad).onUserEarnedReward(mock(RewardItem::class.java)); earned(ad).onUserEarnedReward(mock(RewardItem::class.java))
        events(ad).onAdDismissedFullScreenContent(); events(ad).onAdDismissedFullScreenContent()
        assertEquals(1, cb.events.count { it.startsWith("rewarded") })
        assertEquals("rewarded:rwi_home", cb.events.first())
    }

    @Test fun `rewarded - dismiss toi truoc earned (callback nen) van trao thuong dung mot lan (R-NG3 m4)`() {
        val ad = mock(RewardedAd::class.java).also { `when`(it.adUnitId).thenReturn("${UNIT}rw") }
        val pl = AdsMobPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.AdmobRewarded, "gift_default").also { it.setActivity(activity) }
        LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        val cb = Cb()
        assertTrue(pl.show("gift_home", cb, 0))
        events(ad).onAdDismissedFullScreenContent()
        earned(ad).onUserEarnedReward(mock(RewardItem::class.java))
        earned(ad).onUserEarnedReward(mock(RewardItem::class.java))
        assertEquals(listOf("dismissed:gift_home", "rewarded:gift_home"), cb.events)
    }

    @Test fun `show-fail cua Next-Gen toi sau show (nen) - show tra true, khong roi sang mang sau trong luot (ng-gioi-han)`() {
        val ad = gmaInters("${UNIT}sf")
        val pl = inters()
        LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        val cb = Cb()
        assertTrue("Next-Gen không báo lỗi đồng bộ trong show()", pl.show("full_home", cb, 0))
        assertEquals(emptyList<String>(), cb.events)
        events(ad).onAdFailedToShowFullScreenContent(FullScreenContentError(FullScreenContentError.ErrorCode.INTERNAL_ERROR, "x", null))
        assertEquals(listOf("showFail:${FullScreenContentError.ErrorCode.INTERNAL_ERROR.value}-x"), cb.events)
    }

    // ═══ Vòng app open của AdsHelper (AdsMobPlOpenExpiryTest) ═══

    private class PlNetwork(context: Context, type: Int, private val pl: AdsPlBase) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "ng_open_pl_${adsType}_data"
        override fun getKeyForResDf(): String = "ng_open_pl_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
    }

    private class ShowingNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        private var showing: AdsShowCb? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "ng_open_showing_${adsType}_data"
        override fun getKeyForResDf(): String = "ng_open_showing_${adsType}_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = adsShowCb; adsShowCb.onAdShowed(placement, placement, "test", flagShow); return true
        }
        fun dismiss() { showing?.onAdDismissed(PlacementDefault.PLFullSplash, PlacementDefault.PLFullSplash, "test") }
    }

    private fun helperWith(vararg networks: Pair<Int, AdsBaseFull>): AdsHelper =
        AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance().apply {
            currConfig = AdsConfigOb(mock(Context::class.java)).apply { openAdFirstType = 0; openAdtype = 0 }
            placementsConfigManager = AdsPlacementConfigManager()
            AdsHelper::class.java.getDeclaredField("listAdsFull").apply { isAccessible = true }.set(this, mutableMapOf(*networks))
        }

    private fun expiredOpen(): AdsMobPlOpen {
        val pl = AdsMobPlOpen(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullOpen, "full_default").also { it.setActivity(activity) }
        val item = LoadedAdFixture.giveLoadedAd(pl, mock(AppOpenAd::class.java), clock)
        clock.nowMs = item.loadedAtMillis + 4 * 60 * 60_000L + 1
        return pl
    }

    @Test fun `vong app open - AdMob het han bi bo qua, mang sau hien`() {
        val showing = ShowingNetwork(context, AdsIDDef.MaxFullOpen)
        val helper = helperWith(AdsIDDef.AdmobFullOpen to PlNetwork(context, AdsIDDef.AdmobFullOpen, expiredOpen()), AdsIDDef.MaxFullOpen to showing)
        val cb = Cb()
        assertTrue(helper.showOpenAd(PlacementDefault.PLFullSplash, cb))
        showing.dismiss()
        assertEquals(listOf("showed", "dismissed:${PlacementDefault.PLFullSplash}"), cb.events)
        assertFalse(helper.isFullShowing())
    }

    @Test fun `vong app open - chi co AdMob het han - tra false, khong callback`() {
        val helper = helperWith(AdsIDDef.AdmobFullOpen to PlNetwork(context, AdsIDDef.AdmobFullOpen, expiredOpen()))
        val cb = Cb()
        assertFalse(helper.showOpenAd(PlacementDefault.PLFullSplash, cb))
        assertEquals(emptyList<String>(), cb.events)
    }

    private companion object {
        const val UNIT = "ca-app-pub-3940256099942544/"
    }
}
