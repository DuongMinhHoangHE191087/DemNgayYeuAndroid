package com.app.plugin.ads.core

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.DisplayMetrics
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsKillSwitchTest
import com.app.plugin.ads.AdsPolicy
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.MainHandlerTimers
import com.app.plugin.ads.RemoveAdsMask
import com.app.plugin.ads.RwiIntroDialog
import com.app.plugin.ads.SdkReadyQueue
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlRwi
import com.app.plugin.ads.adsmob.AdsMobRwi
import com.app.plugin.app.AppPluginManager
import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.segment.SegmentHooks
import com.app.plugin.segment.SegmentTestSupport
import com.app.plugin.segment.UserSegmentEngine
import com.app.plugin.logevent.TelemetryConsentGate
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.Constants.AdShowFailReason
import com.app.plugin.until.PreferenceHelper
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 1.9.0 Task 2 (hợp đồng §C): rewarded interstitial qua tiền tố `rwi_`. Mạng và placement AdMob thật ([AdsMobRwi],
 * [AdsMobPlRwi]) với `RewardedInterstitialAd` giả; intro thay bằng bản ghi listener ([RwiIntroPresenter]), trừ phần
 * vòng đời của W11 chạy [RwiIntroDialog] thật trên stub Android. W1 ở `ShowChainTelemetryTest`, W15 ở
 * `AdsNetworkRegistryTest`.
 */
class RewardedInterstitialControllerTest {

    private class RecordingCb : AdsGiftShowCb {
        val events = CopyOnWriteArrayList<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "fail:$err" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed" }
        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) { events += "rewarded" }
    }

    private lateinit var helper: AdsHelper
    private lateinit var net: AdsMobRwi
    private lateinit var pl: AdsMobPlRwi
    private lateinit var ad: RewardedInterstitialAd
    private lateinit var loaded: com.app.plugin.ads.AdsEcmp
    private val activity = mock(Activity::class.java)
    private val clock = LoadedAdFixture.Clock()
    private val cb = RecordingCb()
    private val intros = mutableListOf<RwiIntroDialog.Listener>()
    private val defaultPresenter = RewardedInterstitialController.introPresenter

    private fun context(): Context = mock(Context::class.java).also { c ->
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(c.resources).thenReturn(resources)
        `when`(c.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
    }

    private fun setField(name: String, value: Any?) =
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)

    private fun mobQueue() = AdsMobMy::class.java.getDeclaredField("readyQueue").apply { isAccessible = true }
        .get(AdsMobMy.instance) as SdkReadyQueue

    @Before
    fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdSdkStartup.resetForTest()
        mobQueue().resetForTest()
        helper = AdsKillSwitchTest.newAdsHelper()
        val ctx = context()
        net = AdsMobRwi(AdsIDDef.AdmobRwi, ctx)
        pl = AdsMobPlRwi(ctx, net, AdsIDDef.AdmobRwi, AdsMobRwi.PLACEMENT_DEFAULT)
        net.mapAdsPl[AdsMobRwi.PLACEMENT_DEFAULT] = pl
        net.setActivity(activity)
        setField("listAdsGift", mutableMapOf(AdsIDDef.AdmobRwi to net))
        setField("listAdsAll", mutableListOf<AdsBase>(net))
        ad = mock(RewardedInterstitialAd::class.java)
        loaded = LoadedAdFixture.giveLoadedAd(pl, ad, clock)
        plcf().isLoaded = true
        RewardedInterstitialController.introPresenter = RwiIntroPresenter { _, l -> intros += l; true }
    }

    @After
    fun tearDown() {
        RewardedInterstitialController.introPresenter = defaultPresenter
        RwiIntroDialog.dismissActive()
        AdsPolicy.current = AdsPolicy.DEFAULT
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdSdkStartup.resetForTest()
        mobQueue().resetForTest()
    }

    private fun plcf(placement: String = "rwi_home") = helper.getAdsPlacementConfig(placement, 1)!!

    /** Lý do `ad_show_fail` của [action] (đúng [count] cái). */
    private fun reasonsOf(count: Int, action: () -> Unit): List<String> = ServerEventCapture().use { server ->
        action()
        server.await("ad_show_fail", count).map { it.get("reason").asString }
    }

    /**
     * Lượt bị cổng chặn (§C bước 1): đúng một `ad_show_attempt(rewarded_interstitial, rwi_home, ready=1)` TRƯỚC cổng, và
     * lý do `ad_show_fail` là [reason].
     */
    private fun assertRefusedTurn(reason: String) = ServerEventCapture().use { server ->
        assertFalse(helper.showRewarded("rwi_home", cb))
        val attempts = server.await("ad_show_attempt", 1).map {
            "${it.get("ad_format").asString}|${it.get("placement").asString}|ready=${it.get("ready").asInt}"
        }
        assertEquals("attempt trước mọi cổng", listOf("rewarded_interstitial|rwi_home|ready=1"), attempts)
        assertEquals(listOf(reason), server.await("ad_show_fail", 1).map { it.get("reason").asString })
        assertTrue(intros.isEmpty())
    }

    /** show → intro tiếp tục → quảng cáo GMA được gọi `show`; trả callback toàn màn hình và listener thưởng của nó. */
    private fun showThroughIntro(): Pair<FullScreenContentCallback, OnUserEarnedRewardListener> {
        assertTrue(helper.showRewarded("rwi_home", cb))
        assertEquals(1, intros.size)
        intros[0].onContinue()
        val fsc = ArgumentCaptor.forClass(FullScreenContentCallback::class.java)
        verify(ad).fullScreenContentCallback = fsc.capture()
        val earned = ArgumentCaptor.forClass(OnUserEarnedRewardListener::class.java)
        verify(ad).show(any(Activity::class.java), earned.capture())
        return fsc.value to earned.value
    }

    @Test fun `SEG rwi - hien dong dau dong ho segment, thuong ghi REWARDED_AD_COMPLETED`() {
        val store = SegmentTestSupport.newStore()
        SegmentTestSupport.reset(null)
        try {
            SegmentHooks.init(SegmentTestSupport.prefsContext(store))
            val (fsc, earned) = showThroughIntro()
            assertTrue("hiện được → đồng hồ bền", (store[SegmentHooks.KEY_LAST_FULLSCREEN] as? Long ?: 0L) > 0L)
            earned.onUserEarnedReward(mock(RewardItem::class.java))
            fsc.onAdShowedFullScreenContent()
            fsc.onAdDismissedFullScreenContent() // thưởng giao lúc đóng (RewardDeliveryGate)
            assertEquals(1, UserSegmentEngine.instance.scorerSnapshot().rewardedAdCompletions)
        } finally {
            SegmentTestSupport.reset(null)
        }
    }

    @Test fun `W2 formatOfPlacement rwi_ la rewarded_interstitial`() {
        assertEquals("rewarded_interstitial", AdEventLog.formatOfPlacement("rwi_x"))
    }

    @Test fun `W3 remove-ads FLAG_FULLSCREEN (goi re INTERRUPTIVE) chan rwi voi remove_ad, khong intro`() {
        helper.setRemoveAdsMask(RemoveAdsMask.INTERRUPTIVE)
        assertRefusedTurn(AdShowFailReason.REMOVE_AD)
    }

    @Test fun `W4 gap chung voi interstitial - interstitial vua hien thi rwi bi policy_min_gap, rwi dong thi interstitial cung bi`() {
        helper.fullTimeShow = System.currentTimeMillis() / 1000
        assertRefusedTurn(AdShowFailReason.POLICY_MIN_GAP)

        helper.fullTimeShow = -1
        val (fsc, _) = showThroughIntro()
        fsc.onAdDismissedFullScreenContent()
        assertEquals(AdShowFailReason.POLICY_MIN_GAP, helper.interstitialRefusal())
    }

    @Test fun `W5 phien dau bi chan khi no_interstitial_first_session`() {
        val field = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
        val saved = field.getInt(AppPluginManager.instance)
        field.setInt(AppPluginManager.instance, 1)
        try {
            AdsPolicy.current = AdsPolicy(noInterstitialFirstSession = true)
            assertEquals(
                listOf(AdShowFailReason.POLICY_FIRST_SESSION), reasonsOf(1) { assertFalse(helper.showRewarded("rwi_home", cb)) }
            )
            assertTrue(intros.isEmpty())
        } finally {
            field.setInt(AppPluginManager.instance, saved)
        }
    }

    @Test fun `W6 qua 60 phut - ad_expired, khong intro, nap lai`() {
        clock.nowMs = loaded.loadedAtMillis + 61 * 60_000L
        assertEquals(listOf(AdShowFailReason.AD_EXPIRED), reasonsOf(1) { assertFalse(helper.showRewarded("rwi_home", cb)) })
        assertTrue(intros.isEmpty())
        assertTrue("nạp lại", plcf().isLoading)
        assertTrue("slot mạng đang tải", net.isLoading("rwi_home"))
    }

    @Test fun `W7 bo qua intro - rwi_intro_skipped, giu quang cao, khong goi show GMA`() {
        assertTrue(helper.showRewarded("rwi_home", cb))
        assertEquals(1, intros.size)
        assertEquals(listOf(AdShowFailReason.RWI_INTRO_SKIPPED), reasonsOf(1) { intros[0].onSkip() })
        assertEquals(listOf("fail:${AdShowFailReason.RWI_INTRO_SKIPPED}"), cb.events.toList())
        verify(ad, never()).show(any(Activity::class.java), any(OnUserEarnedRewardListener::class.java))
        assertTrue("quảng cáo giữ cho lượt sau", net.hasLoadedAd("rwi_home"))
        assertTrue(plcf().isLoaded)
        assertEquals(0, helper.flagAdsFullShowing.get())
    }

    @Test fun `W8 thuong 1 lan va dismiss 1 lan ke ca khi mang bao lap`() {
        val (fsc, earned) = showThroughIntro()
        earned.onUserEarnedReward(mock(RewardItem::class.java))
        earned.onUserEarnedReward(mock(RewardItem::class.java))
        fsc.onAdShowedFullScreenContent()
        fsc.onAdDismissedFullScreenContent()
        fsc.onAdDismissedFullScreenContent()
        assertEquals(listOf("showed", "rewarded", "dismissed"), cb.events.toList())
    }

    @Test fun `W9 khong chuoi Full2 sau rwi`() {
        helper.currConfig.full2FlagShow = 2
        val (fsc, _) = showThroughIntro()
        assertFalse(helper.isFull2TransitionActive())
        fsc.onAdDismissedFullScreenContent()
        assertFalse(helper.isFull2TransitionActive())
        assertEquals(listOf("dismissed"), cb.events.toList())
    }

    @Test fun `W10 brain khong duoc hoi - brain se tu choi interstitial nhung rwi van hien, khong dem luot`() {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.0
        BrainPolicy.clear()
        try {
            AdsBrain.onSessionStart("t190_rwi_${System.nanoTime()}")
            repeat(50) { AdsBrain.onInterstitialShown() }
            val shownBefore = AdsBrain.adsShownThisSession()
            showThroughIntro()
            assertEquals(shownBefore, AdsBrain.adsShownThisSession())
        } finally {
            AdsBrain.enabled = false
            AdsBrain.rolloutFraction = 1.0
            HoldoutManager.holdoutFraction = 0.05
            BrainPolicy.clear()
        }
    }

    @Test fun `W11 flagAdsFullShowing giu tu intro toi dismiss, intro that dong khi host clearActivity`() {
        assertTrue(helper.showRewarded("rwi_home", cb))
        assertEquals("intro đang mở", 1, helper.flagAdsFullShowing.get())
        intros[0].onContinue()
        val fsc = ArgumentCaptor.forClass(FullScreenContentCallback::class.java)
        verify(ad).fullScreenContentCallback = fsc.capture()
        fsc.value.onAdShowedFullScreenContent()
        assertEquals("quảng cáo đang hiện", 1, helper.flagAdsFullShowing.get())
        assertFalse("lượt toàn màn hình khác bị chặn", helper.showRewarded("rwi_home", cb))
        fsc.value.onAdDismissedFullScreenContent()
        assertEquals(0, helper.flagAdsFullShowing.get())

        // Intro thật: host clearActivity đóng nó (onHostGone), trả cờ, không giữ tham chiếu.
        RewardedInterstitialController.introPresenter = defaultPresenter
        val resources = mock(Resources::class.java)
        `when`(resources.displayMetrics).thenReturn(DisplayMetrics())
        `when`(activity.resources).thenReturn(resources)
        loaded = LoadedAdFixture.giveLoadedAd(pl, mock(RewardedInterstitialAd::class.java), clock)
        plcf().isLoaded = true
        helper.fullTimeShow = -1
        val cb2 = RecordingCb()
        assertTrue(helper.showRewarded("rwi_home", cb2))
        assertTrue(RwiIntroDialog.isShowing)
        assertEquals(1, helper.flagAdsFullShowing.get())
        net.clearActivity()
        assertFalse(RwiIntroDialog.isShowing)
        assertEquals(0, helper.flagAdsFullShowing.get())
        assertEquals(listOf("fail:${AdShowFailReason.HOST_DESTROYED}"), cb2.events.toList())
    }

    @Test fun `W12 getAdFormat rewarded_interstitial la rwi (show_ads_rwi), xet truoc rewarded`() {
        val telemetry = AdsTelemetry(mock(AdsHelper::class.java))
        assertEquals("rwi", telemetry.getAdFormat("rewarded_interstitial"))
        assertEquals("reward", telemetry.getAdFormat("rewarded"))
        assertEquals("full", telemetry.getAdFormat("interstitial"))
    }

    @Test fun `W13 slot gift_ khong dung mang AdmobRwi ke ca khi cau hinh tro toi no`() {
        helper.currConfig.giftStepCicle.clear()
        helper.currConfig.giftStepCicle.add(AdsIDDef.AdmobRwi)
        helper.currConfig.giftStepRe.clear()
        plcf("gift_home").isLoaded = true
        assertFalse(helper.showRewarded("home", cb))
        helper.loadRewardedForThisTurn("home")
        assertTrue(intros.isEmpty())
        verify(ad, never()).show(any(Activity::class.java), any(OnUserEarnedRewardListener::class.java))
        assertFalse("mạng rwi không nhận load của gift_", net.isLoading("gift_home"))
    }

    /** D33: mặc định `values/` tiếng Anh, `values-vi/` tiếng Việt; mỗi file đúng 3 khoá của intro. */
    @Test fun `W16 chuoi intro - values (tieng Anh) va values-vi co dung 3 khoa`() {
        val keys = setOf("appplugin_rwi_title", "appplugin_rwi_countdown", "appplugin_rwi_skip")
        val nameAndText = Regex("""<string\s+name="([^"]+)"[^>]*>([^<]*)</string>""")
        val texts = listOf("values", "values-vi").associateWith { dir ->
            val f = File("src/main/res/$dir/appplugin_rwi_strings.xml")
            assertTrue("thiếu ${f.path} (chạy từ gốc module)", f.isFile)
            val found = nameAndText.findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
            assertEquals("$dir: đúng 3 khoá", keys, found.keys)
            found
        }
        assertEquals("mặc định tiếng Anh", "Skip", texts.getValue("values")["appplugin_rwi_skip"])
        assertEquals("Bỏ qua", texts.getValue("values-vi")["appplugin_rwi_skip"])
    }

    /** Host là [LifecycleOwner] với [LifecycleRegistry] ở RESUMED; resources đủ để [RwiIntroDialog] dựng view trên stub. */
    private fun lifecycleHost(): Pair<Activity, LifecycleRegistry> {
        val host = mock(Activity::class.java, withSettings().extraInterfaces(LifecycleOwner::class.java))
        val registry = LifecycleRegistry.createUnsafe(host as LifecycleOwner)
        registry.currentState = Lifecycle.State.RESUMED
        `when`((host as LifecycleOwner).lifecycle).thenReturn(registry)
        val resources = mock(Resources::class.java)
        `when`(resources.displayMetrics).thenReturn(DisplayMetrics())
        `when`(host.resources).thenReturn(resources)
        return host to registry
    }

    /** m2 (R-T2): ON_STOP dừng đếm ngược, ON_START chạy tiếp từ chỗ dừng; ON_DESTROY đóng intro (onHostGone), gỡ observer. */
    @Test fun `W17 intro theo vong doi host - ON_STOP dung dem nguoc, ON_START chay tiep, ON_DESTROY dong`() =
        MainHandlerTimers.run(honorRemoveAll = true) { timers ->
            val events = mutableListOf<String>()
            val listener = object : RwiIntroDialog.Listener {
                override fun onContinue() { events += "continue" }
                override fun onSkip() { events += "skip" }
                override fun onHostGone() { events += "hostGone" }
            }
            val (host, registry) = lifecycleHost()
            assertTrue(RwiIntroDialog.present(host, listener))
            timers.advanceTo(2_000) // còn 3 s
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            timers.advanceTo(60_000)
            assertEquals("dừng khi host ON_STOP", emptyList<String>(), events)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            timers.advanceTo(62_999)
            assertEquals("còn 1 s sau ON_START", emptyList<String>(), events)
            timers.advanceTo(63_000)
            assertEquals(listOf("continue"), events)
            assertFalse(RwiIntroDialog.isShowing)
            assertEquals("observer gỡ khi kết thúc", 0, registry.observerCount)

            val (host2, registry2) = lifecycleHost()
            assertTrue(RwiIntroDialog.present(host2, listener))
            registry2.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            assertEquals(listOf("continue", "hostGone"), events)
            assertFalse(RwiIntroDialog.isShowing)
            assertEquals(0, registry2.observerCount)
            timers.advanceTo(120_000)
            assertEquals("không tick nào sau khi đóng", listOf("continue", "hostGone"), events)
        }

    @Test fun `W14 consent DENIED - 0 request cua luot rwi`() {
        val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
        val prefs = PreferenceHelper.getInstance()
        val previous = prefs.preferences
        prefs.preferences = inMemoryPrefs(store)
        ConsentState.resetForTest()
        TelemetryConsentGate.resetForTest()
        try {
            TelemetryConsentGate.install(contextWithTcf(gdpr = null, purposes = ""))
            ConsentState.setTier(ConsentTier.DENIED)
            ServerEventCapture().use { server ->
                assertTrue(helper.showRewarded("rwi_home", cb))
                intros[0].onSkip()
                assertEquals(0, server.awaitRequests("ad_show_attempt", 0, 1_000).size)
                assertEquals("R-P1: không hàng nào sau DENIED", 0, server.allRequests().size)
            }
        } finally {
            TelemetryConsentGate.resetForTest()
            ConsentState.resetForTest()
            prefs.preferences = previous
        }
    }
}
