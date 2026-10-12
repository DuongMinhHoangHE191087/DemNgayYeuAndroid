package com.app.plugin.segment

import android.app.Activity
import android.content.Context
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsBaseGift
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsKillSwitchTest
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.FullScreenAdPolicy
import com.app.plugin.ads.MainHandlerTimers
import com.app.plugin.app.AppPluginManager
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.logevent.SessionTracker
import com.app.plugin.segment.SegmentTestSupport.newStore
import com.app.plugin.segment.SegmentTestSupport.prefsContext
import com.app.plugin.segment.SegmentTestSupport.rc
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.util.Collections
import java.util.concurrent.Executor

/**
 * 2.2.0 SEG: năm hook của `SegmentHooks` chạy qua đường thật của SDK (controller show, `AdEventLog`), thay cho các
 * dòng fork ở `AdsHelper`/`AppPluginBase`/`FirHelper` (H1, H2, H4 của plan; consent H3 ở `SegmentConsentTest`).
 */
class SegmentHooksTest {
    /** Mạng interstitial có quảng cáo sẵn; `showAd` hiện được và ghi placement. */
    private class ReadyInters(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullInters, context) {
        override val eventTag: String get() = "seg_inters"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        val shows = mutableListOf<String>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "seg_inters_data"
        override fun getKeyForResDf(): String = "seg_inters_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean { shows += placement; return true }
    }

    /** Mạng rewarded hiện được; [reward] bắn `onAdRewarded` của lượt đang hiện. */
    private class Gift(context: Context) : AdsBaseGift(AdsIDDef.AdmobRewarded, context) {
        override val eventTag: String get() = "seg_gift"
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "seg_gift_data"
        override fun getKeyForResDf(): String = "seg_gift_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = placement to adsShowCb
            return true
        }
        fun reward() = showing!!.let { (pl, cb) -> (cb as AdsGiftShowCb).onAdRewarded(pl, pl, "t") }
    }

    private class NoopCb : AdsGiftShowCb {
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) = Unit
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) = Unit
        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) = Unit
    }

    private val store = newStore()
    private val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
    private var savedSession = 0

    @Before fun setUp() {
        SegmentTestSupport.reset()
        savedSession = sessionField.get(AppPluginManager.instance) as Int
        sessionField.set(AppPluginManager.instance, 2)
    }

    @After fun tearDown() {
        SessionTracker.resetForTest()
        sessionField.set(AppPluginManager.instance, savedSession)
        SegmentTestSupport.reset()
    }

    /** `AdsHelper` mới, interstitial `full_home` sẵn sàng, rewarded một mạng, host đã resume. */
    private fun withHelper(check: (AdsHelper, ReadyInters, Gift) -> Unit) = MainHandlerTimers.run(honorRemoveAll = true) { _ ->
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            full2FlagShow = 0
            fullStepCicle.clear(); fullStepCicle.add(AdsIDDef.AdmobFullInters); fullStepRe.clear()
            giftStepCicle.clear(); giftStepCicle.add(AdsIDDef.AdmobRewarded); giftStepRe.clear()
        }
        val context = prefsContext(newStore())
        val inters = ReadyInters(context)
        val gift = Gift(context)
        set(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to inters))
        set(helper, "listAdsGift", mutableMapOf<Int, AdsBaseGift>(AdsIDDef.AdmobRewarded to gift))
        set(helper, "listAdsAll", mutableListOf<AdsBase>())
        set(helper, "mActivity", mock(Activity::class.java))
        (get(helper, "fullScreenAdPolicy") as FullScreenAdPolicy).onHostActivityResumed()
        helper.getAdsPlacementConfig("full_home", 1)!!.isLoaded = true
        check(helper, inters, gift)
    }

    @Test fun `H1 H4 - guardrail tat khi khong RC, RC 60 s chan interstitial sau fullscreen, dau ben song qua khoi dong lai`() {
        SegmentHooks.init(prefsContext(store))
        SegmentHooks.onFullscreenShown(System.currentTimeMillis() - 10_000L)
        withHelper { helper, inters, _ ->
            assertTrue("H4: không RC → guardrail tắt, hiện như 2.1.0", helper.showInterstitial("home", 0, NoopCb()))
            assertEquals(listOf("full_home"), inters.shows)
        }
        val stamp = store[SegmentHooks.KEY_LAST_FULLSCREEN] as Long
        assertTrue("nhánh hiện được đóng dấu bền (khoá fork)", System.currentTimeMillis() - stamp in 0..5_000L)

        SegmentHooks.resetForTest() // khởi động lại process: đồng hồ trong bộ nhớ mất
        rc(longs = mapOf("seg_cadence_guardrail_seconds" to 60L))
        SegmentHooks.init(prefsContext(store))
        withHelper { helper, inters, _ ->
            ServerEventCapture().use { server ->
                assertFalse("H1: fullscreen cách < 60 s", helper.showInterstitial("home", 0, NoopCb()))
                assertFalse("như fork: full_splash không miễn", helper.showInterstitial("splash", 0, NoopCb(), isDeltaTime = false))
                assertEquals(
                    listOf("full_home" to SegmentHooks.REASON_GUARDRAIL, "full_splash" to SegmentHooks.REASON_GUARDRAIL),
                    server.showFails(2),
                )
            }
            assertEquals(emptyList<String>(), inters.shows)
        }
    }

    @Test fun `H2 - rewarded hoan tat ghi REWARDED_AD_COMPLETED`() {
        SegmentHooks.init(prefsContext(store))
        withHelper { helper, _, gift ->
            assertTrue(helper.showRewarded("x", NoopCb()))
            gift.reward()
        }
        assertEquals(1, UserSegmentEngine.instance.scorerSnapshot().rewardedAdCompletions)
        assertEquals(1, store["seg_rewarded_ad_completions"])
    }

    @Test fun `AD_IMPRESSION tu AdEventLog - moi impression fullscreen dem, gia tri 0 khong nhom eCPM, banner khong dem`() {
        SegmentHooks.init(prefsContext(store))
        val before = UserSegmentEngine.instance.scorerSnapshot().adImpressionCount
        ServerEventCapture().use {
            AdEventLog.revenue("interstitial", "admob", "admob", "u", "full_home", 600_000L, "USD", 3, "i1", "s")
            AdEventLog.revenue("banner", "admob", "admob", "u", "bn_home", 900_000L, "USD", 3, "i2", "s")
            AdEventLog.revenue("rewarded", "meta_direct", "facebook", "u", "gift_x", 0L, "", -1, "i3", "s")
            AdEventLog.revenue("rewarded_interstitial", "admob", "admob", "u", "rwi_home", 0L, "USD", 0, "i4", "s")
        }
        assertEquals("interstitial + Meta 0 + rwi 0 (D27-220)", before + 3, UserSegmentEngine.instance.scorerSnapshot().adImpressionCount)
        assertEquals("chỉ giá trị > 0 vào cửa sổ eCPM", "600000", store["seg_ecpm_window"])
    }

    @Test fun `SESSION_START moi lan foreground qua SessionTracker, initPlugin khong ghi them`() {
        SegmentHooks.init(prefsContext(store))
        val before = UserSegmentEngine.instance.scorerSnapshot().sessionTimestamps.size
        SessionTracker.onForeground()
        SessionTracker.onForeground()
        assertEquals("hai lần foreground = hai SESSION_START (fork: onStart)", before + 2, UserSegmentEngine.instance.scorerSnapshot().sessionTimestamps.size)

        val src = File("src/main/java/com/app/plugin/app/AppPluginManager.kt").readText()
        val body = src.substringAfter("public fun initPlugin(").substringBefore("\n    }\n")
        assertTrue("initPlugin gọi SegmentHooks.init", body.contains("SegmentHooks.init(context)"))
        assertFalse("initPlugin không ghi SESSION_START (không đếm đôi lúc cold start)", body.contains("SegmentHooks.onSessionStart"))
    }

    @Test fun `D27 - init chi hen, 0 lan doc RC tren luong init, doc het tren luong nap`() {
        val threads = Collections.synchronizedList(mutableListOf<Thread>())
        SegmentHooks.rcLong = { threads += Thread.currentThread(); null }
        SegmentHooks.rcString = { threads += Thread.currentThread(); null }
        val tasks = mutableListOf<Runnable>()
        SegmentStartup.executor = Executor { tasks += it }

        SegmentHooks.init(prefsContext(store))

        assertEquals("0 lần đọc RC trên luồng gọi init (main)", 0, threads.size)
        assertFalse(SegmentStartup.isLoaded)
        val loader = Thread { tasks.single().run() }.apply { start(); join(5_000) }
        assertTrue("nạp xong", SegmentStartup.isLoaded)
        assertTrue("≥ 30 khoá seg_* đọc", threads.size >= 30)
        assertEquals("mọi lần đọc trên luồng nạp", setOf(loader), threads.toSet())
    }

    @Test fun `D27 - event toi som xep hang, phat lai dung thu tu, tran 100 bo cu nhat`() {
        val tasks = mutableListOf<Runnable>()
        SegmentStartup.executor = Executor { tasks += it }
        SegmentHooks.init(prefsContext(store))
        val seen = mutableListOf<Int>()
        SessionTracker.onForeground() // hook thật: xếp hàng
        UserSegmentEngine.instance.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED) // app gọi thẳng lúc đang nạp: xếp hàng
        repeat(SegmentStartup.QUEUE_CAP) { i -> SegmentStartup.runOrQueue { seen += i } }
        assertEquals(SegmentStartup.QUEUE_CAP, SegmentStartup.queued)

        tasks.single().run()

        assertEquals("2 cái cũ nhất bị bỏ, còn lại đúng thứ tự", (0 until SegmentStartup.QUEUE_CAP).toList(), seen)
        assertEquals(0, SegmentStartup.queued)
        assertEquals("SESSION_START sớm bị bỏ khi tràn", 0, UserSegmentEngine.instance.scorerSnapshot().sessionTimestamps.size)
        SegmentStartup.runOrQueue { seen += -1 }
        assertEquals("đã nạp: chạy ngay", -1, seen.last())

        SegmentHooks.resetForTest()
        SegmentStartup.executor = Executor { tasks += it }
        SegmentHooks.init(prefsContext(store))
        SessionTracker.onForeground()
        UserSegmentEngine.instance.recordEvent(SegmentEvent.PAYWALL_PLAN_TAPPED)
        tasks.last().run()
        assertEquals("phát lại SESSION_START sau nạp", 1, UserSegmentEngine.instance.scorerSnapshot().sessionTimestamps.size)
        assertTrue("event app tới sớm được phát lại", UserSegmentEngine.instance.scorerSnapshot().everTappedPlan)
    }

    @Test fun `D27 - blocksFullscreen false truoc khi nap, true sau khi nap voi dong ho khoi phuc`() {
        val now = System.currentTimeMillis()
        store[SegmentHooks.KEY_LAST_FULLSCREEN] = now - 10_000L
        rc(longs = mapOf("seg_cadence_guardrail_seconds" to 60L))
        val tasks = mutableListOf<Runnable>()
        SegmentStartup.executor = Executor { tasks += it }
        SegmentHooks.init(prefsContext(store))

        assertFalse("chưa nạp: không chặn", SegmentHooks.blocksFullscreen(now))
        tasks.single().run()
        assertTrue("nạp xong: đồng hồ khôi phục 10 s trước, guardrail 60 s", SegmentHooks.blocksFullscreen(now))
    }

    private fun set(helper: AdsHelper, name: String, value: Any?) =
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)

    private fun get(helper: AdsHelper, name: String): Any? =
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper)
}
