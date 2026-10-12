package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.app.AppPluginManager
import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.LicenseTestRule
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.segment.SegmentHooks
import com.app.plugin.segment.SegmentTestSupport
import com.app.plugin.until.Constants.AdShowFailReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.4.2 FIXED (Hợp đồng A): thời điểm cố định `fixedMoment` bỏ qua guardrail segment, `interstitialRefusal`, cooldown N
 * màn và brain, vẫn chặn quảng cáo đang hiện, remove-ads/tắt định dạng, `flagShow < 0`, host chưa resume (ready);
 * `isInterstitialReadyToShow` cùng kết luận với `showInterstitial` cùng tham số; `full_intro_3` cố định nối Full2; mọi
 * lượt nối Full2 dưới placement được gọi, không dưới slot `originPl` của mạng.
 */
class FixedMomentTest {
    @get:Rule val license = LicenseTestRule()

    /** Mạng toàn màn hình có quảng cáo sẵn; [slot] = `originPl` mạng báo (null: chính placement được show). */
    private class Net(context: Context, type: Int, private val slot: String? = null) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "t242_fixed"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        val loads = mutableListOf<String>()
        val shows = mutableListOf<String>()
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "t242_fixed_${adsType}_data"
        override fun getKeyForResDf(): String = "t242_fixed_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += placement }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement
            showing = placement to adsShowCb
            return true
        }
        fun dismiss() = showing!!.let { (p, cb) -> showing = null; cb.onAdDismissed(p, slot ?: p, "t") }
    }

    /** Mạng rewarded hiện được; dismiss báo `originPl` = slot `gift_default` (placement không có slot riêng). */
    private class Gift(context: Context) : AdsBaseGift(AdsIDDef.AdmobRewarded, context) {
        override val eventTag: String get() = "t242_gift"
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "t242_gift_data"
        override fun getKeyForResDf(): String = "t242_gift_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = placement to adsShowCb
            return true
        }
        fun dismiss() = showing!!.let { (p, cb) -> cb.onAdDismissed(p, PlacementDefault.PLGiftDefault, "t") }
    }

    private class Cb : AdsGiftShowCb {
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) = Unit
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) = Unit
        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) = Unit
    }

    private class Fx(val helper: AdsHelper, val inter: Net, val full2: Net, val gift: Gift) {
        fun plcf(placement: String) = helper.getAdsPlacementConfig(placement, 1)!!
        fun transitionActive(): Boolean = get(helper, "full2TransitionActive") as Boolean
        fun policy() = get(helper, "fullScreenAdPolicy") as FullScreenAdPolicy
        fun show(pl: String, fixed: Boolean? = null, isDeltaTime: Boolean = true): Boolean =
            if (fixed == null) helper.showInterstitial(pl, 0, Cb(), isDeltaTime) else helper.showInterstitial(pl, 0, Cb(), isDeltaTime, fixed)
        fun ready(pl: String, fixed: Boolean? = null, isDeltaTime: Boolean = true): Boolean =
            if (fixed == null) helper.isInterstitialReadyToShow(pl) else helper.isInterstitialReadyToShow(pl, 0, isDeltaTime, fixed)
    }

    private val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
    private val appStartField = AppPluginManager::class.java.getDeclaredField("tAppStart").apply { isAccessible = true }
    private var savedSession = 0
    private var savedAppStart: Any? = null

    @Before fun setUp() {
        SegmentTestSupport.reset(tier = null)
        savedSession = sessionField.get(AppPluginManager.instance) as Int
        savedAppStart = appStartField.get(AppPluginManager.instance)
        sessionField.set(AppPluginManager.instance, 2)
    }

    @After fun tearDown() {
        sessionField.set(AppPluginManager.instance, savedSession)
        appStartField.set(AppPluginManager.instance, savedAppStart)
        SegmentTestSupport.reset(tier = null)
    }

    /** Phiên 2, host resume, Activity sống, interstitial AdMob [inter] (slot báo [slot]) + Full2 native full [full2]. */
    private fun withFx(full2FlagShow: Int = 0, slot: String? = null, check: Fx.() -> Unit) = MainHandlerTimers.run(honorRemoveAll = true) { _ ->
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            this.full2FlagShow = full2FlagShow
            fullStepCicle.clear(); fullStepCicle.add(AdsIDDef.AdmobFullInters); fullStepRe.clear()
            full2StepCicle.clear(); full2StepCicle.add(AdsIDDef.AdmobFullNt); full2StepRe.clear()
            giftStepCicle.clear(); giftStepCicle.add(AdsIDDef.AdmobRewarded); giftStepRe.clear()
        }
        val ctx = networkContext()
        val fx = Fx(helper, Net(ctx, AdsIDDef.AdmobFullInters, slot), Net(ctx, AdsIDDef.AdmobFullNt), Gift(ctx))
        set(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to fx.inter, AdsIDDef.AdmobFullNt to fx.full2))
        set(helper, "listAdsGift", mutableMapOf<Int, AdsBaseGift>(AdsIDDef.AdmobRewarded to fx.gift))
        set(helper, "listAdsAll", mutableListOf<AdsBase>())
        set(helper, "mActivity", mock(Activity::class.java))
        fx.policy().onHostActivityResumed()
        helper.getAdsPlacementConfig("full_home", 1)!!.isLoaded = true
        fx.check()
    }

    /** Guardrail segment 60 s (RC), fullscreen gần nhất 10 s trước. */
    private fun guardrailActive() {
        SegmentTestSupport.rc(longs = mapOf("seg_cadence_guardrail_seconds" to 60L))
        SegmentHooks.init(SegmentTestSupport.prefsContext())
        SegmentHooks.onFullscreenShown(System.currentTimeMillis() - 10_000L)
        assertTrue("guardrail đang chặn", SegmentHooks.blocksFullscreen(System.currentTimeMillis()))
    }

    /** Chỉ `interstitialRefusal` chặn: `fullStartTime` 300 s, app vừa mở. */
    private fun refusalActive(helper: AdsHelper) {
        helper.currConfig.fullStartTime = 300
        appStartField.set(AppPluginManager.instance, System.currentTimeMillis() / 1000)
        assertEquals(AdShowFailReason.NOT_ALLOW_SHOW, helper.interstitialRefusal())
    }

    @Test fun `FX1 fullStartTime chan - mac dinh false not_allow_show, fixedMoment hien, mang nhan show`() = withFx {
        refusalActive(helper)
        ServerEventCapture().use { server ->
            assertFalse(show("home"))
            assertEquals(listOf("full_home" to AdShowFailReason.NOT_ALLOW_SHOW), server.showFails(1))
        }
        assertEquals(emptyList<String>(), inter.shows)

        assertTrue("fixedMoment: hiện dù fullStartTime chưa qua", show("home", fixed = true))
        assertEquals(listOf("full_home"), inter.shows)
    }

    @Test fun `FX2 guardrail segment - ready mac dinh false nhu show, fixedMoment ready va show true`() = withFx {
        guardrailActive()
        assertFalse("ready mặc định khớp cổng guardrail của show", ready("home"))
        ServerEventCapture().use { server ->
            assertFalse(show("home"))
            assertEquals(listOf("full_home" to SegmentHooks.REASON_GUARDRAIL), server.showFails(1))
        }
        assertTrue("fixedMoment ready bỏ qua guardrail", ready("home", fixed = true))
        assertTrue("fixedMoment show bỏ qua guardrail", show("home", fixed = true))
        assertEquals(listOf("full_home"), inter.shows)
    }

    @Test fun `FX3 cooldown N man dang bat - mac dinh chan, fixedMoment hien`() = withFx {
        helper.setPostAdScreenCooldown(1)
        helper.markAutoAdShown()
        assertTrue(helper.isPostAdScreenCooldownActive())

        assertFalse(ready("home"))
        assertFalse(show("home"))
        assertTrue("fixedMoment ready bỏ qua cooldown", ready("home", fixed = true))
        assertTrue("fixedMoment show bỏ qua cooldown", show("home", fixed = true))
        assertEquals(listOf("full_home"), inter.shows)
    }

    @Test fun `FX3 brain tu choi - mac dinh chan, fixedMoment hien va khong hoi brain`() = withFx {
        AdsBrain.enabled = true
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.0
        BrainPolicy.clear()
        try {
            AdsBrain.onSessionStart("t242_fixed_${System.nanoTime()}")
            repeat(50) { AdsBrain.onInterstitialShown() }   // vượt trần phiên: brain chỉ còn NO_ACTION
            val shown = AdsBrain.adsShownThisSession()
            ServerEventCapture().use { server ->
                assertFalse(show("home"))
                assertEquals(listOf("full_home" to AdShowFailReason.SHOW_FULL_BRAIN_DECLINED), server.showFails(1))
            }
            assertTrue("fixedMoment: brain không phủ quyết", show("home", fixed = true))
            assertEquals(listOf("full_home"), inter.shows)
            assertEquals("brain không được hỏi: không đếm lượt", shown, AdsBrain.adsShownThisSession())
        } finally {
            AdsBrain.enabled = false
            HoldoutManager.holdoutFraction = 0.05
            BrainPolicy.clear()
        }
    }

    @Test fun `FX4 fixedMoment van chan - mask ALL, tat dinh dang, flagShow -1, dang hien, host pause`() {
        val blockers: List<Pair<String, (Fx) -> Unit>> = listOf(
            "mask ALL" to { fx: Fx -> fx.helper.setRemoveAdsMask(RemoveAdsMask.ALL) },
            "isDisableAds(4)" to { fx: Fx -> fx.helper.currConfig.maskAdsStatus = 0 },
            "flagShow -1" to { fx: Fx -> fx.plcf("full_home").flagShow = -1 },
            "đang hiện" to { fx: Fx -> fx.helper.flagAdsFullShowing.set(1) },
        )
        for ((name, block) in blockers) withFx {
            block(this)
            assertFalse("$name: ready", ready("home", fixed = true))
            assertFalse("$name: show", show("home", fixed = true))
            assertEquals(name, emptyList<String>(), inter.shows)
        }
        withFx {
            policy().onHostActivityPaused()
            assertFalse("host pause: ready", ready("home", fixed = true))
        }
    }

    @Test fun `FX5 ready isDeltaTime false - true khi chi interstitialRefusal chan, false khi guardrail chan`() = withFx {
        refusalActive(helper)
        assertFalse("isDeltaTime mặc định: luật tần suất chặn", ready("home"))
        assertTrue("isDeltaTime false: như show bỏ qua luật tần suất", ready("home", fixed = false, isDeltaTime = false))
        assertTrue("show cùng tham số cùng kết luận", show("home", isDeltaTime = false))
        inter.dismiss()

        guardrailActive()
        assertFalse("guardrail vẫn chặn khi isDeltaTime false", ready("home", fixed = false, isDeltaTime = false))
        assertFalse(show("home", isDeltaTime = false))
    }

    @Test fun `FX6 full_intro_3 fixedMoment noi Full2 full_2_intro_3, khong nap full_default`() = withFx(full2FlagShow = 1) {
        assertTrue(show("intro_3", fixed = true))
        assertTrue("beginFull2Transition: chuyển cảnh bật", transitionActive())
        assertEquals("Full2 nạp từ lúc cha hiện", listOf("full_2_intro_3"), full2.loads)

        inter.dismiss()

        assertEquals("đóng → showFull2(full_intro_3)", listOf("full_2_intro_3"), full2.shows)
        assertFalse("không nạp lại full_default sau intro3", PlacementDefault.PLFullDefault in inter.loads)
    }

    @Test fun `FX6 full_intro_3 khong fixedMoment - giu loai tru, khong noi Full2`() = withFx(full2FlagShow = 1) {
        assertTrue(show("intro_3", isDeltaTime = false))
        assertFalse(transitionActive())

        inter.dismiss()

        assertEquals(emptyList<String>(), full2.shows)
        assertFalse(PlacementDefault.PLFullDefault in inter.loads)
    }

    @Test fun `FX7 placement khong slot rieng, mang bao originPl full_default - Full2 ten full_2_clean_done`() =
        withFx(full2FlagShow = 1, slot = PlacementDefault.PLFullDefault) {
            assertTrue(show("clean_done", isDeltaTime = false))
            inter.dismiss()
            assertEquals("Full2 dưới placement được gọi", listOf("full_2_clean_done"), full2.shows)
        }

    @Test fun `FX8 rewarded gift_speaker_health, mang bao originPl gift_default - Full2 ten full_2_speaker_health`() =
        withFx(full2FlagShow = 2) {
            assertTrue(helper.showRewarded("speaker_health", Cb()))
            helper.giftTimeShow = 0
            gift.dismiss()
            assertEquals(listOf("full_2_speaker_health"), full2.shows)
        }

    @Test fun `FX6c intro3 va splash tu slot mac dinh - nap lai full_default, tu slot rieng - khong (nhu fork, R-A' m1)`() {
        for ((pl, slot, reload) in listOf(
            Triple("intro_3", PlacementDefault.PLFullDefault, true), Triple("splash", PlacementDefault.PLFullDefault, true),
            Triple("intro_3", null, false), Triple("splash", null, false),
        )) withFx(slot = slot) {
            assertTrue(show(pl, fixed = true))
            inter.dismiss()
            assertEquals("$pl từ slot ${slot ?: "riêng"}: nạp lại full_default", reload, PlacementDefault.PLFullDefault in inter.loads)
        }
    }

    private companion object {
        fun networkContext(): Context {
            val context = mock(Context::class.java)
            val resources = mock(Resources::class.java)
            `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
            `when`(context.resources).thenReturn(resources)
            `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
            return context
        }

        fun set(helper: AdsHelper, name: String, value: Any?) {
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
        }

        fun get(helper: AdsHelper, name: String): Any? =
            AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper)
    }
}
