package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsfb.AdsFbPlNtCl
import com.app.plugin.ads.adsfb.AdsFbPlrewarded
import com.app.plugin.ads.adsmax.AdsMaxMy
import com.app.plugin.ads.adsmax.AdsMaxPlInters
import com.app.plugin.ads.adsmax.AdsMaxPlNtCl
import com.app.plugin.ads.adsmax.AdsMaxPlOpen
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlInters
import com.app.plugin.ads.adsmob.AdsMobPlNtCl
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.facebook.ads.RewardedVideoAd
import com.facebook.ads.RewardedVideoAdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task I review I-2 (và M1): callback muộn của một request đã bị watchdog slot bỏ, tới đúng lúc slot đang chạy lượt
 * load mới, không được chạm trạng thái slot/waterfall và không gọi `adsLoadCb` của lượt mới. Mỗi listener mạng chụp
 * [AdsPlacementEcmpBase.loadGeneration] lúc gửi request; generation đã đổi (hoặc slot đang rảnh) thì bỏ callback.
 * Ba mạng đại diện, mỗi mạng một kiểu listener: GMA interstitial (callback tạo mỗi request), MAX app open (listener
 * trên đối tượng tạo mỗi request), Meta rewarded (listener qua builder).
 */
class LateLoadCallbackTest {

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

    private fun gmaAd(unit: String): InterstitialAd {
        val ad = mock(InterstitialAd::class.java)
        `when`(ad.adUnitId).thenReturn(unit)
        `when`(ad.responseInfo).thenReturn(mock(ResponseInfo::class.java))
        return ad
    }

    /**
     * Request 1 → slot hết hạn (cb1 `load timeout`) → request 2 đang chạy → callback muộn của request 1 (thành công rồi
     * lỗi) không tới cb2, không đổi slot/waterfall; rồi request 2 callback → cb2 nhận đúng một `loaded`.
     */
    @Test
    fun `GMA interstitial - callback muon cua request da bo khong toi luot moi`() = MainHandlerTimers.run { t ->
        val callbacks = mutableListOf<InterstitialAdLoadCallback>()
        mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }.use {
            mockStatic(InterstitialAd::class.java).use { statics ->
                statics.`when`<Unit> { InterstitialAd.load(any(Context::class.java), anyString(), any(AdRequest::class.java), any()) }
                    .thenAnswer { callbacks += it.arguments[3] as InterstitialAdLoadCallback; null }
                val pl = AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default")
                pl.setActivity(activity)
                pl.parserEcpms("$first;$second")
                val state = LoadedAdFixture.state(pl)
                val cb1 = Callback()
                pl.load("full_home", cb1)
                assertEquals(1, callbacks.size)

                t.advanceTo(60_000)
                assertEquals(listOf("fail:load timeout"), cb1.events)
                val cb2 = Callback()
                pl.load("full_home", cb2)
                assertEquals("request 2 đã gửi", 2, callbacks.size)
                assertTrue(state.isLoading)

                val late = gmaAd(first)
                callbacks[0].onAdLoaded(late)
                assertEquals("thành công muộn không tới lượt mới", emptyList<String>(), cb2.events)
                assertTrue("slot vẫn đang chờ request 2", state.isLoading)
                assertNotSame("quảng cáo muộn không thay chỗ request 2 đang chạy", late, state.adEcmps!!.list[0].adObject)

                callbacks[0].onAdFailedToLoad(mock(LoadAdError::class.java))
                assertEquals("lỗi muộn không tới lượt mới", emptyList<String>(), cb2.events)
                assertEquals("lỗi muộn không đẩy waterfall của lượt mới", 0, state.adEcmps!!.idxCurrEcpm)
                assertEquals("lỗi muộn không gửi request mới", 2, callbacks.size)

                val fresh = gmaAd(first)
                callbacks[1].onAdLoaded(fresh)
                assertEquals(listOf("loaded"), cb2.events)
                assertSame(fresh, state.adEcmps!!.list[0].adObject)
                assertFalse(state.isLoading)
                assertEquals(1, cb1.events.size)
                // Final review IMP-1: paid listener gán đúng một lần trên đường loaded thường (không gán hai lần).
                verify(fresh, times(1)).onPaidEventListener = any()
            }
        }
    }

    /**
     * Final review IMP-1: quảng cáo GMA tới muộn sau watchdog slot, lúc slot rảnh và mục trống, được [AdsPlBase.keepLateAd]
     * giữ làm quảng cáo đã load; lần load sau trả nó (không request mới) và app hiện nó. Paid event của lần hiện đó phải
     * tới [com.app.plugin.firbase.FirHelper.logAdRevAdmob] đúng một lần (Firebase `ad_impression`, tROAS, AppsFlyer). Trước
     * đây listener chỉ gán ở nhánh loaded thường, sau guard stale: quảng cáo giữ muộn hiện mà không ghi doanh thu.
     */
    @Test
    fun `GMA interstitial - quang cao muon duoc giu van co paid listener, doanh thu ghi dung mot lan`() = MainHandlerTimers.run { t ->
        val callbacks = mutableListOf<InterstitialAdLoadCallback>()
        mockConstruction(AdRequest.Builder::class.java) { b, _ -> `when`(b.build()).thenReturn(mock(AdRequest::class.java)) }.use {
            mockStatic(InterstitialAd::class.java).use { statics ->
                statics.`when`<Unit> { InterstitialAd.load(any(Context::class.java), anyString(), any(AdRequest::class.java), any()) }
                    .thenAnswer { callbacks += it.arguments[3] as InterstitialAdLoadCallback; null }
                val pl = AdsMobPlInters(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullInters, "full_default")
                pl.setActivity(activity)
                pl.parserEcpms(first)
                val item = LoadedAdFixture.ecmps(pl).list[0]
                val cb1 = Callback()
                pl.load("full_home", cb1)
                t.advanceTo(60_000)
                assertEquals(listOf("fail:load timeout"), cb1.events)

                val late = gmaAd(first)
                callbacks[0].onAdLoaded(late)
                assertSame("slot rảnh, mục trống: quảng cáo muộn được giữ", late, item.adObject)
                assertTrue(item.isLoaded)
                val cb2 = Callback()
                pl.load("full_home", cb2)
                assertEquals("lần load sau trả quảng cáo đã giữ", listOf("loaded"), cb2.events)
                assertEquals("không request mới", 1, callbacks.size)

                val paid = ArgumentCaptor.forClass(OnPaidEventListener::class.java)
                verify(late, times(1)).onPaidEventListener = paid.capture()
                val adValue = mock(AdValue::class.java)
                `when`(adValue.valueMicros).thenReturn(12_300L)
                `when`(adValue.currencyCode).thenReturn("USD")
                val firebase = mock(FirebaseAnalytics::class.java)
                mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                    analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(firebase)
                    paid.value.onPaidEvent(adValue)                 // GMA báo paid event khi quảng cáo giữ muộn hiện
                }
                verify(firebase, times(1)).logEvent(eq(FirebaseAnalytics.Event.AD_IMPRESSION), any())
            }
        }
    }

    /**
     * Task I review Minor 2: kiểu listener thứ tư, `create-once` (khác ba kiểu trên: callback/listener mới mỗi
     * request) — `AdsMaxPlInters` tạo `MaxInterstitialAd` một lần, `loadGen` giữ trong field làm mới trước mỗi
     * `loadAd()` (không có danh sách callback theo request). Request 1 hết hạn (slot watchdog 60 s) → request 2 dùng
     * lại đối tượng cũ, không tạo `MaxInterstitialAd`/listener mới → `onAdLoaded` của listener đó báo đúng một `loaded`
     * cho cb2. Test hành vi hiện tại: `onAdLoaded` thứ hai tới lúc slot đang rảnh (generation không đổi nhưng
     * `isLoading=false`) bị coi là muộn — không callback, quảng cáo đã có (`adObject === interstitialAd`) giữ nguyên
     * qua [AdsPlBase.keepLateAd] (no-op vì `adObject` đã là chính đối tượng đó).
     */
    @Test
    fun `MAX interstitial create-once - callback muon luc slot ranh khong callback, giu quang cao`() = MainHandlerTimers.run { t ->
        var listener: MaxAdListener? = null
        mockConstruction(MaxInterstitialAd::class.java) { ad, _ ->
            doAnswer { listener = it.arguments[0] as MaxAdListener; null }.`when`(ad).setListener(any())
            `when`(ad.adUnitId).thenReturn("max-inters")
        }.use { built ->
            val pl = AdsMaxPlInters(context, mock(AdsBase::class.java), AdsIDDef.MaxFullInters, "full_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-inters-1;max-inters-2")
            val state = LoadedAdFixture.state(pl)
            val cb1 = Callback()
            pl.load("full_home", cb1)
            assertEquals("request 1 tạo MaxInterstitialAd", 1, built.constructed().size)

            t.advanceTo(60_000)
            assertEquals(listOf("fail:load timeout"), cb1.events)
            val cb2 = Callback()
            pl.load("full_home", cb2)
            assertEquals("request 2 dùng lại đối tượng cũ, không tạo mới", 1, built.constructed().size)
            assertTrue(state.isLoading)

            val maxAd = mock(MaxAd::class.java)
            `when`(maxAd.adUnitId).thenReturn("max-inters")
            listener!!.onAdLoaded(maxAd)
            assertEquals(listOf("loaded"), cb2.events)
            assertFalse(state.isLoading)
            assertSame(built.constructed()[0], state.adEcmps!!.list[0].adObject)
            assertEquals("cb1 không nhận thêm", 1, cb1.events.size)

            listener!!.onAdLoaded(maxAd)
            assertEquals("callback muộn lúc slot rảnh không tới cb2", listOf("loaded"), cb2.events)
            assertSame("quảng cáo giữ nguyên", built.constructed()[0], state.adEcmps!!.list[0].adObject)
            assertTrue(state.adEcmps!!.list[0].isLoaded)
        }
    }

    @Test
    fun `MAX app open - callback muon cua request da bo khong toi luot moi`() = MainHandlerTimers.run { t ->
        val listeners = mutableListOf<MaxAdListener>()
        mockConstruction(MaxAppOpenAd::class.java) { ad, _ ->
            doAnswer { listeners += it.arguments[0] as MaxAdListener; null }.`when`(ad).setListener(any())
            `when`(ad.adUnitId).thenReturn("max-open")
        }.use { built ->
            val pl = AdsMaxPlOpen(context, mock(AdsBase::class.java), AdsIDDef.MaxFullOpen, "full_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-open-1;max-open-2")
            val state = LoadedAdFixture.state(pl)
            val cb1 = Callback()
            pl.load(PlacementDefault.PLFullSplash, cb1)
            t.advanceTo(60_000)
            assertEquals(listOf("fail:load timeout"), cb1.events)
            val cb2 = Callback()
            pl.load(PlacementDefault.PLFullSplash, cb2)
            assertEquals(2, built.constructed().size)

            listeners[0].onAdLoaded(mock(MaxAd::class.java))
            assertEquals(emptyList<String>(), cb2.events)
            assertTrue(state.isLoading)
            assertNotSame(built.constructed()[0], state.adEcmps!!.list[0].adObject)

            listeners[0].onAdLoadFailed("max-open-1", mock(MaxError::class.java))
            assertEquals(emptyList<String>(), cb2.events)
            assertEquals(0, state.adEcmps!!.idxCurrEcpm)
            assertEquals(2, built.constructed().size)

            listeners[1].onAdLoaded(mock(MaxAd::class.java))
            assertEquals(listOf("loaded"), cb2.events)
            assertSame(built.constructed()[1], state.adEcmps!!.list[0].adObject)
        }
    }

    /**
     * M1: app open MAX hết hạn khi app ở nền → SDK tự nạp lại; không có Activity thì listener nhận
     * `onAdLoadFailed(MISSING_ACTIVITY)` dù không request nào đang chờ. Không được đẩy waterfall, không gửi request
     * mới, không callback. Task J review IMP-2: đối tượng đang giữ bị bỏ và `destroy()` (sẽ không bao giờ `isReady`).
     * Task K mục 2: `onAdLoaded` tới sau đó trên chính đối tượng đã destroy (`isReady == false`) không callback và không
     * được giữ lại ([AdsPlBase.keepLateAd] chỉ giữ quảng cáo `isLoadedAdReady`); trước đây nó được giữ lại làm quảng cáo
     * đã load dù đã destroy.
     */
    @Test
    fun `MAX app open - SDK tu nap lai khi khong co request thi khong callback, khong request moi`() = MainHandlerTimers.run { t ->
        val listeners = mutableListOf<MaxAdListener>()
        mockConstruction(MaxAppOpenAd::class.java) { ad, _ ->
            doAnswer { listeners += it.arguments[0] as MaxAdListener; null }.`when`(ad).setListener(any())
            `when`(ad.adUnitId).thenReturn("max-open")
        }.use { built ->
            val pl = AdsMaxPlOpen(context, mock(AdsBase::class.java), AdsIDDef.MaxFullOpen, "full_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-open-1;max-open-2")
            val state = LoadedAdFixture.state(pl)
            val cb1 = Callback()
            pl.load(PlacementDefault.PLFullSplash, cb1)
            listeners[0].onAdLoaded(mock(MaxAd::class.java))
            assertEquals(listOf("loaded"), cb1.events)
            assertFalse(state.isLoading)

            listeners[0].onAdLoadFailed("max-open-1", mock(MaxError::class.java))
            assertEquals("không callback khi không có request", listOf("loaded"), cb1.events)
            assertEquals("không gửi request mới", 1, built.constructed().size)
            assertEquals(0, state.adEcmps!!.idxCurrEcpm)
            assertFalse(state.isLoading)

            assertNull("IMP-2: đối tượng tự nạp lại lỗi bị bỏ", state.adEcmps!!.list[0].adObject)
            verify(built.constructed()[0], times(1)).destroy()

            `when`(built.constructed()[0].isReady).thenReturn(false)
            listeners[0].onAdLoaded(mock(MaxAd::class.java))
            assertEquals(listOf("loaded"), cb1.events)
            assertNull("đối tượng đã destroy không được giữ lại", state.adEcmps!!.list[0].adObject)
            assertFalse(state.adEcmps!!.list[0].isLoaded)
            assertFalse(pl.holdsLoadedAd())
            assertEquals("không gửi request mới", 1, built.constructed().size)
        }
    }

    /**
     * Task K mục 2 (re-review Task J Minor 2): [AdsPlBase.keepLateAd] chỉ giữ quảng cáo tới muộn khi nó sẵn sàng hiện
     * ([AdsPlBase.isLoadedAdReady]). Request A hết hạn (slot watchdog 60 s), slot rảnh, mục trống: `onAdLoaded` muộn của
     * A với `isReady == false` → không giữ, `destroy()`, không callback. Request B hết hạn rồi `onAdLoaded` muộn với
     * `isReady == true` → giữ làm quảng cáo đã load như trước (Task I2).
     */
    @Test
    fun `MAX app open - quang cao muon chua san sang khong duoc giu, san sang thi giu`() = MainHandlerTimers.run { t ->
        val listeners = mutableListOf<MaxAdListener>()
        mockConstruction(MaxAppOpenAd::class.java) { ad, _ ->
            doAnswer { listeners += it.arguments[0] as MaxAdListener; null }.`when`(ad).setListener(any())
            `when`(ad.adUnitId).thenReturn("max-open")
        }.use { built ->
            val pl = AdsMaxPlOpen(context, mock(AdsBase::class.java), AdsIDDef.MaxFullOpen, "full_default")
            pl.setActivity(activity)
            pl.parserEcpms("max-open-1;max-open-2")
            val state = LoadedAdFixture.state(pl)
            val cb1 = Callback()
            pl.load(PlacementDefault.PLFullSplash, cb1)
            t.advanceTo(60_000)
            assertEquals(listOf("fail:load timeout"), cb1.events)
            assertFalse(state.isLoading)
            val a = built.constructed()[0]

            `when`(a.isReady).thenReturn(false)
            listeners[0].onAdLoaded(mock(MaxAd::class.java))
            assertEquals("không callback", listOf("fail:load timeout"), cb1.events)
            assertNull("không giữ đối tượng chưa sẵn sàng", state.adEcmps!!.list[0].adObject)
            assertFalse(state.adEcmps!!.list[0].isLoaded)
            verify(a, times(1)).destroy()
            assertEquals(1, built.constructed().size)

            val cb2 = Callback()
            pl.load(PlacementDefault.PLFullSplash, cb2)
            assertEquals("mục trống: request B thật", 2, built.constructed().size)
            t.advanceTo(120_000)
            assertEquals(listOf("fail:load timeout"), cb2.events)
            val b = built.constructed()[1]
            `when`(b.isReady).thenReturn(true)
            listeners[1].onAdLoaded(mock(MaxAd::class.java))
            assertEquals(listOf("fail:load timeout"), cb2.events)
            assertSame("sẵn sàng thì giữ", b, state.adEcmps!!.list[0].adObject)
            assertTrue(state.adEcmps!!.list[0].isLoaded)
            verify(b, never()).destroy()
        }
    }

    @Test
    fun `Meta rewarded - callback muon cua request da bo khong toi luot moi, quang cao muon bi destroy`() = MainHandlerTimers.run { t ->
        val listeners = mutableListOf<RewardedVideoAdListener>()
        mockConstruction(RewardedVideoAd::class.java) { ad, _ ->
            val builder = mock(RewardedVideoAd.RewardedVideoAdLoadConfigBuilder::class.java)
            `when`(ad.buildLoadAdConfig()).thenReturn(builder)
            `when`(builder.withAdListener(any())).thenAnswer { listeners += it.arguments[0] as RewardedVideoAdListener; builder }
        }.use { built ->
            val pl = AdsFbPlrewarded(context, mock(AdsBase::class.java), AdsIDDef.FBRewarded, "gift_default")
            pl.setActivity(activity)
            pl.parserEcpms("fb-1;fb-2")
            val state = LoadedAdFixture.state(pl)
            val cb1 = Callback()
            pl.load("gift_home", cb1)
            t.advanceTo(60_000)
            assertEquals(listOf("fail:load timeout"), cb1.events)
            val cb2 = Callback()
            pl.load("gift_home", cb2)
            assertEquals(2, built.constructed().size)
            val late = built.constructed()[0]
            val fresh = built.constructed()[1]

            listeners[0].onAdLoaded(late)
            assertEquals(emptyList<String>(), cb2.events)
            assertTrue(state.isLoading)
            verify(late, times(1)).destroy()
            assertNotSame(late, state.adEcmps!!.list[0].adObject)

            listeners[0].onError(late, mock(com.facebook.ads.AdError::class.java))
            assertEquals(emptyList<String>(), cb2.events)
            assertEquals(0, state.adEcmps!!.idxCurrEcpm)
            assertEquals(2, built.constructed().size)

            listeners[1].onAdLoaded(fresh)
            assertEquals(listOf("loaded"), cb2.events)
            assertSame(fresh, state.adEcmps!!.list[0].adObject)
            verify(fresh, never()).destroy()
        }
    }

    // ═══ Final review Minor 1: NtCl — quảng cáo native tới muộn không được giữ thì bị destroy ═══
    //
    // `keepLateAd` gặp mục bận (đang có request mới, hoặc đã giữ quảng cáo khác) thì `destroyAd(ad)`. Ba lớp NtCl trước
    // đây không ghi đè `destroyAd` (về `AdsPlBase.destroyAd`, không làm gì): native ad tới muộn không được giữ mà cũng
    // không destroy. Nay mỗi lớp destroy như lớp `Nt` cùng mạng.

    /** Mục đang giữ [held] đã load: [late] tới muộn không được giữ, phải qua `destroyAd` của lớp. */
    private fun dropLateNative(pl: AdsPlBase, held: Any, late: Any) {
        pl.parserEcpms("ntcl-unit")
        val item = LoadedAdFixture.ecmps(pl).list[0]
        item.adObject = held
        item.isLoaded = true
        pl.keepLateAd(item, late)
        assertSame("quảng cáo đang giữ không bị thay", held, item.adObject)
    }

    @Test
    fun `GMA NtCl - native muon khong duoc giu thi bi destroy`() {
        val pl = AdsMobPlNtCl(context, mock(AdsBase::class.java), AdsIDDef.AdmobNtCl, "ntcl_default")
        val held = mock(com.google.android.gms.ads.nativead.NativeAd::class.java)
        val late = mock(com.google.android.gms.ads.nativead.NativeAd::class.java)
        dropLateNative(pl, held, late)
        verify(late, times(1)).destroy()
        verify(held, never()).destroy()
    }

    @Test
    fun `MAX NtCl - native muon khong duoc giu thi bi destroy qua loader`() {
        val pl = AdsMaxPlNtCl(context, mock(AdsBase::class.java), AdsIDDef.MaxNtCl, "ntcl_default")
        val loader = mock(MaxNativeAdLoader::class.java)
        AdsMaxPlNtCl::class.java.getDeclaredField("nativeAdLoader").apply { isAccessible = true }.set(pl, loader)
        val held = mock(MaxAd::class.java)
        val late = mock(MaxAd::class.java)
        dropLateNative(pl, held, late)
        verify(loader, times(1)).destroy(late)
        verify(loader, never()).destroy(held)
    }

    @Test
    fun `Meta NtCl - native muon khong duoc giu thi bi destroy`() {
        val pl = AdsFbPlNtCl(context, mock(AdsBase::class.java), AdsIDDef.FBNtCl, "ntcl_default")
        val held = mock(com.facebook.ads.NativeAd::class.java)
        val late = mock(com.facebook.ads.NativeAd::class.java)
        dropLateNative(pl, held, late)
        verify(late, times(1)).destroy()
        verify(held, never()).destroy()
    }
}
