package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Handler
import com.app.plugin.ads.adsmax.AdsMaxMy
import com.app.plugin.ads.adsmax.AdsMaxPlInters
import com.app.plugin.ads.adsmax.AdsMaxPlOpen
import com.app.plugin.ads.adsmax.AdsMaxPlrewarded
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task J4 (review Task I concern 4): `AdsPlBase.hasLoadedAd` nhất quán với `isReady()` của MAX. MAX interstitial,
 * rewarded, app open đang tự nạp lại trong cùng đối tượng (sau hết hạn) có `isReady == false`: `hasLoadedAd` trả
 * `false` (hook internal `isLoadedAdReady`), nên `isInterstitialReadyToShow` trả `false` thay vì `true` rồi `show` trả
 * `false` (I4). Không bỏ quảng cáo, không request mới: `dropStaleLoadedFlag` coi mạng còn giữ đối tượng (đang nạp lại)
 * là bận, giữ cờ đã load (`isInterstitialLoaded` vẫn `true`, app "chờ" theo mẫu ready → show / !loaded → load / chờ).
 */
class AdsHelperMaxReadyTest {

    private class FullNetwork(context: Context, type: Int, pl: AdsPlBase) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        init { mapAdsPl[PlacementDefault.PLFullDefault] = pl }
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_max_ready_full_data"
        override fun getKeyForResDf(): String = "test_max_ready_full_res"
    }

    private lateinit var context: Context
    private lateinit var helper: AdsHelper
    private val activity = mock(Activity::class.java)
    private val clock = LoadedAdFixture.Clock()

    private fun setField(name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    private fun field(name: String): Any? =
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper)

    @Before
    fun setUp() {
        AdsPolicy.current = AdsPolicy.DEFAULT
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        AdsMaxMy.instance.readyQueue.resetForTest(); AdsMaxMy.instance.readyQueue.markReady()
        helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.full2FlagShow = 0
        setField("listAdsFull", mutableMapOf<Int, AdsBaseFull>())
        setField("listAdsGift", mutableMapOf<Int, AdsBaseGift>())
        setField("listAdsAll", mutableListOf<AdsBase>())
    }

    @After
    fun tearDown() {
        AdsMaxMy.instance.readyQueue.resetForTest()
        AdsPolicy.current = AdsPolicy.DEFAULT
    }

    private class LoadEvents : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "fail:$error"
        }
    }

    /** MAX interstitial thật, giữ [ad] như sau `onAdLoaded`, đứng một mình trong waterfall; host đang resume. */
    private fun withMaxInters(ad: MaxInterstitialAd): AdsMaxPlInters {
        val pl = AdsMaxPlInters(context, mock(AdsBase::class.java), AdsIDDef.MaxFullInters, PlacementDefault.PLFullDefault)
        pl.setActivity(activity)
        pl.interstitialAd = ad
        LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        registerMaxInters(pl)
        return pl
    }

    /** [pl] là mạng interstitial duy nhất của helper; host đang resume. */
    @Suppress("UNCHECKED_CAST")
    private fun registerMaxInters(pl: AdsMaxPlInters) {
        val network = FullNetwork(context, AdsIDDef.MaxFullInters, pl)
        helper.currConfig.fullStepCicle.add(AdsIDDef.MaxFullInters)
        (field("listAdsFull") as MutableMap<Int, AdsBaseFull>)[AdsIDDef.MaxFullInters] = network
        (field("listAdsAll") as MutableList<AdsBase>).add(network)
        helper.setMainActivity(activity)
        (field("fullScreenAdPolicy") as FullScreenAdPolicy).onHostActivityResumed()
    }

    private fun <T> withInlineMainHandler(block: () -> T): T =
        mockConstruction(Handler::class.java) { handler, _ ->
            `when`(handler.post(any())).thenAnswer { (it.arguments[0] as Runnable).run(); true }
        }.use { block() }

    @Test
    fun `MAX interstitial isReady false - isInterstitialReadyToShow false, co da load giu, 0 request, isReady true - true`() {
        val ad = mock(MaxInterstitialAd::class.java).also { `when`(it.isReady).thenReturn(false) }
        val pl = withMaxInters(ad)
        val state = LoadedAdFixture.state(pl)

        withInlineMainHandler { helper.loadInterstitialForThisTurn("tab_switch", 0) }
        assertTrue("slot trả quảng cáo đang giữ, cờ đã load bật", helper.isInterstitialLoaded("tab_switch"))
        verify(ad, never()).loadAd()

        withInlineMainHandler {
            assertFalse("MAX chưa sẵn sàng thì không ready", helper.isInterstitialReadyToShow("tab_switch"))
            assertFalse("gọi lại vẫn không ready", helper.isInterstitialReadyToShow("tab_switch"))
        }
        verify(ad, never()).loadAd()
        assertFalse("không request mới (MAX đang tự nạp lại trong cùng đối tượng)", state.isLoading)
        assertTrue("cờ đã load giữ: app chờ MAX nạp lại, không load thừa", helper.isInterstitialLoaded("tab_switch"))
        assertTrue("quảng cáo không bị bỏ", state.adEcmps!!.list.single().isLoaded)
        assertEquals(ad, state.adEcmps!!.list.single().adObject)

        `when`(ad.isReady).thenReturn(true)
        assertTrue("MAX nạp lại xong thì ready", helper.isInterstitialReadyToShow("tab_switch"))
        verify(ad, never()).loadAd()
    }

    /** Mức placement: `hasLoadedAd` của 3 lớp MAX theo `isReady()`; AdMob (không có `isReady`) không đổi. */
    @Test
    fun `hasLoadedAd theo isReady cua MAX interstitial, rewarded, app open`() {
        val inters = mock(MaxInterstitialAd::class.java).also { `when`(it.isReady).thenReturn(false) }
        val i = AdsMaxPlInters(context, mock(AdsBase::class.java), AdsIDDef.MaxFullInters, "full_default")
        i.interstitialAd = inters
        LoadedAdFixture.giveLoadedAd(i, inters, clock)

        val rewarded = mock(MaxRewardedAd::class.java).also { `when`(it.isReady).thenReturn(false) }
        val r = AdsMaxPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.MaxRewarded, "gift_default")
        AdsMaxPlrewarded::class.java.getDeclaredField("rewardedAd").apply { isAccessible = true }.set(r, rewarded)
        LoadedAdFixture.giveLoadedAd(r, rewarded, clock)

        val open = mock(MaxAppOpenAd::class.java).also { `when`(it.isReady).thenReturn(false) }
        val o = AdsMaxPlOpen(context, mock(AdsBase::class.java), AdsIDDef.MaxFullOpen, "full_default")
        LoadedAdFixture.giveLoadedAd(o, open, clock)

        assertFalse(i.hasLoadedAd()); assertFalse(r.hasLoadedAd()); assertFalse(o.hasLoadedAd())
        assertTrue("quảng cáo vẫn giữ", LoadedAdFixture.ecmps(i).list.single().isLoaded)
        assertTrue(LoadedAdFixture.ecmps(r).list.single().isLoaded)
        assertTrue(LoadedAdFixture.ecmps(o).list.single().isLoaded)
        verify(inters, never()).destroy(); verify(rewarded, never()).destroy(); verify(open, never()).destroy()

        `when`(inters.isReady).thenReturn(true); `when`(rewarded.isReady).thenReturn(true); `when`(open.isReady).thenReturn(true)
        assertTrue(i.hasLoadedAd()); assertTrue(r.hasLoadedAd()); assertTrue(o.hasLoadedAd())
    }

    // ═══ Task J review IMP-2: MAX tự nạp lại lỗi trên đối tượng đang giữ → bỏ đối tượng, placement nạp lại thật ═══

    /**
     * MAX interstitial đã load rồi hết hạn → MAX tự nạp lại trong cùng đối tượng → lỗi (no fill/offline): `onAdLoadFailed`
     * tới khi slot rảnh. Trước đây callback đó bị bỏ hẳn (M1), đối tượng ở lại với `isReady == false`: `holdsLoadedAd` true
     * → `dropStaleLoadedFlag` "bận" → `isInterstitialReadyToShow` false và `isInterstitialLoaded` true cả phiên, không ai
     * gọi `loadAd()` nữa. Nay: đối tượng hỏng bị bỏ khỏi mục (không callback, không đẩy waterfall, không request ngay);
     * lần `isInterstitialReadyToShow` kế hạ cờ và nạp lại → `loadAd()` thật đúng một lần trên cùng đối tượng create-once.
     */
    @Test
    fun `MAX interstitial tu nap lai loi luc ranh - bo doi tuong, isInterstitialReadyToShow false roi loadAd dung mot lan`() =
        MainHandlerTimers.run { t ->
            var listener: MaxAdListener? = null
            mockConstruction(MaxInterstitialAd::class.java) { ad, _ ->
                doAnswer { listener = it.arguments[0] as MaxAdListener; null }.`when`(ad).setListener(any())
                `when`(ad.adUnitId).thenReturn("max-inters")
            }.use { built ->
                val pl = AdsMaxPlInters(context, mock(AdsBase::class.java), AdsIDDef.MaxFullInters, PlacementDefault.PLFullDefault)
                pl.setActivity(activity)
                pl.parserEcpms("max-inters-1;max-inters-2")
                registerMaxInters(pl)
                val state = LoadedAdFixture.state(pl)
                val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max-inters") }

                helper.loadInterstitialForThisTurn("tab_switch", 0)
                t.runPosted()
                assertEquals(1, built.constructed().size)
                val ad = built.constructed()[0]
                verify(ad, times(1)).loadAd()
                `when`(ad.isReady).thenReturn(true)
                listener!!.onAdLoaded(maxAd)
                assertTrue(helper.isInterstitialLoaded("tab_switch"))
                assertTrue(helper.isInterstitialReadyToShow("tab_switch"))
                assertFalse(state.isLoading)

                // Hết hạn → MAX tự nạp lại → lỗi, không request nào đang chờ.
                `when`(ad.isReady).thenReturn(false)
                clearInvocations(ad)
                listener!!.onAdLoadFailed("max-inters-1", mock(MaxError::class.java))
                verify(ad, never()).loadAd()
                assertEquals("không đẩy waterfall", 0, state.adEcmps!!.idxCurrEcpm)
                assertFalse("không request ngay trong callback", state.isLoading)
                assertNull("đối tượng hỏng bị bỏ khỏi mục", state.adEcmps!!.list[0].adObject)
                assertFalse(state.adEcmps!!.list[0].isLoaded)
                assertFalse(pl.holdsLoadedAd())

                assertFalse("không ready", helper.isInterstitialReadyToShow("tab_switch"))
                t.runPosted()
                verify(ad, times(1)).loadAd()
                assertEquals("create-once: không tạo MaxInterstitialAd mới", 1, built.constructed().size)
                assertTrue("placement đang nạp lại thật", state.isLoading)
                assertFalse("gọi lại lúc đang nạp: không request thứ hai", helper.isInterstitialReadyToShow("tab_switch"))
                t.runPosted()
                verify(ad, times(1)).loadAd()

                `when`(ad.isReady).thenReturn(true)
                listener!!.onAdLoaded(maxAd)
                assertTrue("nạp lại xong thì ready", helper.isInterstitialReadyToShow("tab_switch"))
                assertFalse(state.isLoading)
            }
        }

    /**
     * Mức placement cho hai lớp còn lại. Rewarded (create-once): bỏ đối tượng khỏi mục, không `destroy()`, không callback,
     * `load()` kế `loadAd()` lại trên cùng đối tượng. App open (đối tượng mới mỗi lần load): bỏ và `destroy()` đối tượng
     * hỏng, `load()` kế tạo đối tượng mới. Cả hai: không đẩy waterfall, không request ngay trong callback.
     */
    @Test
    fun `MAX rewarded va app open tu nap lai loi luc ranh - bo doi tuong, app open destroy, khong callback, load ke request that`() =
        MainHandlerTimers.run { _ ->
            val maxAd = mock(MaxAd::class.java).also { `when`(it.adUnitId).thenReturn("max") }
            val error = mock(MaxError::class.java)

            var rwListener: MaxRewardedAdListener? = null
            val rewarded = mock(MaxRewardedAd::class.java)
            doAnswer { rwListener = it.arguments[0] as MaxRewardedAdListener; null }.`when`(rewarded).setListener(any())
            mockStatic(MaxRewardedAd::class.java).use { statics ->
                statics.`when`<MaxRewardedAd> { MaxRewardedAd.getInstance(anyString(), any(Context::class.java)) }.thenReturn(rewarded)
                val r = AdsMaxPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.MaxRewarded, "gift_default")
                r.setActivity(activity)
                r.parserEcpms("max-rw-1;max-rw-2")
                val state = LoadedAdFixture.state(r)
                val cb = LoadEvents()
                r.load("gift_home", cb)
                verify(rewarded, times(1)).loadAd()
                `when`(rewarded.isReady).thenReturn(true)
                rwListener!!.onAdLoaded(maxAd)
                assertEquals(listOf("loaded"), cb.events)

                `when`(rewarded.isReady).thenReturn(false)
                rwListener!!.onAdLoadFailed("max-rw-1", error)
                assertEquals("không callback", listOf("loaded"), cb.events)
                assertNull(state.adEcmps!!.list[0].adObject)
                assertFalse(state.adEcmps!!.list[0].isLoaded)
                assertEquals(0, state.adEcmps!!.idxCurrEcpm)
                assertFalse(state.isLoading)
                verify(rewarded, times(1)).loadAd()
                verify(rewarded, never()).destroy()
                assertFalse(r.holdsLoadedAd())

                val cb2 = LoadEvents()
                r.load("gift_home", cb2)
                verify(rewarded, times(2)).loadAd()
                assertTrue(state.isLoading)
                `when`(rewarded.isReady).thenReturn(true)
                rwListener!!.onAdLoaded(maxAd)
                assertEquals(listOf("loaded"), cb2.events)
                assertTrue(r.hasLoadedAd())
            }

            val listeners = mutableListOf<MaxAdListener>()
            mockConstruction(MaxAppOpenAd::class.java) { ad, _ ->
                doAnswer { listeners += it.arguments[0] as MaxAdListener; null }.`when`(ad).setListener(any())
                `when`(ad.adUnitId).thenReturn("max-open")
            }.use { built ->
                val o = AdsMaxPlOpen(context, mock(AdsBase::class.java), AdsIDDef.MaxFullOpen, "full_default")
                o.setActivity(activity)
                o.parserEcpms("max-open-1;max-open-2")
                val state = LoadedAdFixture.state(o)
                val cb = LoadEvents()
                o.load(PlacementDefault.PLFullSplash, cb)
                val first = built.constructed()[0]
                `when`(first.isReady).thenReturn(true)
                listeners[0].onAdLoaded(maxAd)
                assertEquals(listOf("loaded"), cb.events)

                `when`(first.isReady).thenReturn(false)
                listeners[0].onAdLoadFailed("max-open-1", error)
                assertEquals("không callback", listOf("loaded"), cb.events)
                assertNull(state.adEcmps!!.list[0].adObject)
                verify(first, times(1)).destroy()
                assertEquals("không request ngay", 1, built.constructed().size)
                assertEquals(0, state.adEcmps!!.idxCurrEcpm)
                assertFalse(state.isLoading)
                assertFalse(o.holdsLoadedAd())

                val cb2 = LoadEvents()
                o.load(PlacementDefault.PLFullSplash, cb2)
                assertEquals("load kế tạo đối tượng mới", 2, built.constructed().size)
                verify(built.constructed()[1], times(1)).loadAd()
                assertTrue(state.isLoading)
            }
        }
}
