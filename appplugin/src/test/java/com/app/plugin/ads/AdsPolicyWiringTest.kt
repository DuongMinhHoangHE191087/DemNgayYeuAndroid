package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import com.app.plugin.app.AppPluginManager
import com.app.plugin.logevent.LogEventServerHelper
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants.AdShowFailReason
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task 10: [AdsPolicy] nối vào [AdsHelper] ở đúng một chỗ mỗi luật.
 * - Một luật khoảng cách (không chồng `fullDeltatime` với policy), và [AdsHelper.isInterstitialReadyToShow]
 *   trả cùng kết luận với [AdsHelper.showInterstitial] (addendum B2).
 * - Lượt bị policy chặn đi đường của mọi lượt bị chặn đồng bộ ở cổng: trả `false`, không callback, đúng một
 *   `ad_show_fail_reason` mang nhãn riêng (Điều 13 đúng một lần, G6; addendum B4).
 * - App open miễn `full_splash` như khối suppress (B3); không hoán instance [FullScreenAdPolicy] (B1);
 *   cooldown N màn hình của policy không bật khi app chưa báo màn nào (B7); lưu cho lần mở kế (B5).
 */
class AdsPolicyWiringTest {

    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            events += "dismissed"
        }
    }

    /** Mạng có quảng cáo sẵn ([hasLoadedAd] đúng), hiện được; [dismiss] như người dùng bấm đóng. */
    private class ReadyNetwork(context: Context, type: Int) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        private var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_policy_${adsType}_data"
        override fun getKeyForResDf(): String = "test_policy_${adsType}_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = placement to adsShowCb
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
        fun dismiss() {
            val (placement, cb) = showing ?: return
            showing = null
            cb.onAdDismissed(placement, placement, "test")
        }
    }

    private val doc62 = """{"schema_version":1,"no_interstitial_first_session":true,"app_open_from_session":2,""" +
        """"interstitial_min_gap_seconds":240}"""

    private lateinit var context: Context
    private lateinit var helper: AdsHelper
    private lateinit var inters: ReadyNetwork
    private lateinit var open: ReadyNetwork
    private val sessionField = AppPluginManager::class.java.getDeclaredField("sessionCount").apply { isAccessible = true }
    private var savedSession = 0

    private fun field(name: String): Any? =
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper)

    private fun setField(name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    private fun session(n: Int) = sessionField.set(AppPluginManager.instance, n)

    private fun nowSec() = System.currentTimeMillis() / 1000

    private fun fullScreenAdPolicy() = field("fullScreenAdPolicy") as FullScreenAdPolicy

    private fun requiredScreens() = (field("postAdCooldown") as PostAdScreenCooldown).requiredScreens

    @Before
    fun setUp() {
        AdsPolicy.current = AdsPolicy.DEFAULT
        savedSession = sessionField.get(AppPluginManager.instance) as Int
        session(2)
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        inters = ReadyNetwork(context, AdsIDDef.AdmobFullNt)
        open = ReadyNetwork(context, AdsIDDef.AdmobFullOpen)
        helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            full2FlagShow = 0
            openAdFirstType = 0
            openAdtype = 0
            fullDeltaTime = 30
        }
        setField("listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to inters, AdsIDDef.AdmobFullOpen to open))
        // Host đang resume, placement đã load: mọi cổng tức thời khác của isInterstitialReadyToShow đều qua.
        setField("mActivity", mock(Activity::class.java))
        fullScreenAdPolicy().onHostActivityResumed()
        helper.getAdsPlacementConfig("full_home", 1)!!.isLoaded = true
    }

    @After
    fun tearDown() {
        AdsPolicy.current = AdsPolicy.DEFAULT
        session(savedSession)
        LogUtil.setFlagLog(1)
    }

    /** Lượt bị chặn: show trả `false`, ready cũng `false`, app không nhận callback, đúng một event [reason]. */
    private fun assertInterstitialRefused(reason: String) {
        val cb = RecordingShowCb()
        ServerEventCapture().use { server ->
            assertFalse("ready phải cùng kết luận với show", helper.isInterstitialReadyToShow("home", 1))
            assertFalse(helper.showInterstitial("home", 1, cb))
            assertEquals(emptyList<String>(), cb.events)
            assertEquals(listOf("full_home" to reason), server.showFails(1))
        }
    }

    private fun assertInterstitialShows() {
        val cb = RecordingShowCb()
        assertTrue(helper.isInterstitialReadyToShow("home", 1))
        assertTrue(helper.showInterstitial("home", 1, cb))
        assertEquals(listOf("showed"), cb.events)
        inters.dismiss()
    }

    // ═══ Interstitial: một luật khoảng cách, ready = show ═══

    @Test fun `mac dinh - khoang cach la fullDeltatime cu, chan khi chua qua 30 s, gui policy_min_gap`() {
        helper.fullTimeShow = nowSec() - 10
        assertInterstitialRefused(AdShowFailReason.POLICY_MIN_GAP)

        helper.fullTimeShow = nowSec() - 31
        assertInterstitialShows()
    }

    @Test fun `policy dat khoang cach thi thay fullDeltatime, khong chong hai luat`() {
        helper.applyPolicy(AdsPolicy(interstitialMinGapSeconds = 0))
        helper.fullTimeShow = nowSec() - 10   // fullDeltatime 30 s sẽ chặn nếu còn chạy song song
        assertInterstitialShows()
    }

    @Test fun `policy dat khoang cach 240 s thi chan du fullDeltatime da qua`() {
        helper.applyPolicy(AdsPolicy(interstitialMinGapSeconds = 240))
        helper.fullTimeShow = nowSec() - 100
        assertInterstitialRefused(AdShowFailReason.POLICY_MIN_GAP)
    }

    @Test fun `policy phien dau - phien 1 bi chan, gui policy_first_session, phien 2 hien`() {
        helper.applyPolicy(AdsPolicy(noInterstitialFirstSession = true))
        session(1)
        assertInterstitialRefused(AdShowFailReason.POLICY_FIRST_SESSION)

        session(2)
        assertInterstitialShows()
    }

    @Test fun `policy phien dau khong chan luot isDeltaTime false (splash, intro tu quyet)`() {
        helper.applyPolicy(AdsPolicy(noInterstitialFirstSession = true, interstitialMinGapSeconds = 3600))
        session(1)
        helper.fullTimeShow = nowSec()
        assertTrue(helper.showInterstitial("home", 1, RecordingShowCb(), isDeltaTime = false))
    }

    /** G6: nhánh `fullTimeStart` trước đây trả `false` không một event nào. */
    @Test fun `fullTimeStart cu chan thi gui not_allow_show`() {
        helper.currConfig.fullStartTime = Int.MAX_VALUE
        assertInterstitialRefused(AdShowFailReason.NOT_ALLOW_SHOW)
    }

    /** Review E M6: log áp policy ghi cả tuổi tối đa toàn màn hình, để lần chạy máy đọc được giá trị đang áp. */
    @Test fun `log ads policy ghi fullscreenMaxAge`() {
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java).use { log ->
            helper.applyPolicy(AdsPolicy(fullscreenMaxAgeMinutes = 45))
            log.verify({ Log.d(eq(LogUtil.TAG), contains("fullscreenMaxAge=45min")) }, times(1))
        }
    }

    // ═══ App open ═══

    @Test fun `mac dinh app open khong xet phien`() {
        setField("openFlag4Resume", true)
        session(0)
        assertTrue(helper.showOpenAd(PlacementDefault.PLFullResume, RecordingShowCb()))
    }

    @Test fun `policy app open tu phien 2 - resume o phien 1 bi chan, khong callback, dung mot event`() {
        setField("openFlag4Resume", true)
        helper.applyPolicy(AdsPolicy(appOpenFromSession = 2))
        session(1)
        val cb = RecordingShowCb()
        ServerEventCapture().use { server ->
            assertFalse(helper.showOpenAd(PlacementDefault.PLFullResume, cb))
            assertEquals(emptyList<String>(), cb.events)
            assertEquals(listOf(PlacementDefault.PLFullResume to AdShowFailReason.POLICY_APP_OPEN_SESSION), server.showFails(1))
        }

        session(2)
        assertTrue(helper.showOpenAd(PlacementDefault.PLFullResume, cb))
        assertEquals(listOf("showed"), cb.events)
    }

    @Test fun `policy app open - full_splash duoc mien nhu khoi suppress`() {
        helper.applyPolicy(AdsPolicy(appOpenFromSession = 2))
        session(1)
        assertTrue(helper.showOpenAd(PlacementDefault.PLFullSplash, RecordingShowCb()))
    }

    // ═══ Cooldown sau click, cooldown N màn hình ═══

    /** B1: hoán instance làm mất `isHostActivityResumed` tới lần resume kế (fullscreen "not ready", Full2 bị giữ). */
    @Test fun `applyPolicy giu instance FullScreenAdPolicy va trang thai host, doi cooldown sau click`() {
        val before = fullScreenAdPolicy()

        helper.applyPolicy(AdsPolicy(postClickCooldownSeconds = 20))

        assertSame(before, fullScreenAdPolicy())
        assertTrue(before.isHostReadyForFullscreen())
        assertTrue(helper.isInterstitialReadyToShow("home", 1))
        before.recordAdClick(1_000L)
        assertTrue(before.shouldSuppressAutomaticFullscreen(1_000L + 19_999))
        assertFalse(before.shouldSuppressAutomaticFullscreen(1_000L + 20_000))

        helper.applyPolicy(AdsPolicy.DEFAULT)   // khoá vắng: về 5 s như cũ
        before.recordAdClick(100_000L)
        assertTrue(before.shouldSuppressAutomaticFullscreen(100_000L + 4_999))
        assertFalse(before.shouldSuppressAutomaticFullscreen(100_000L + 5_000))
    }

    @Test fun `screens - policy khong dat thi giu gia tri app, co dat thi policy thang, go khoa thi ve gia tri app`() {
        helper.setPostAdScreenCooldown(2)
        helper.applyPolicy(AdsPolicy.DEFAULT)
        assertEquals("applyPolicy không được reset setPostAdScreenCooldown", 2, requiredScreens())
        helper.applyPolicy(AdsPolicy(interstitialMinGapSeconds = 60))
        assertEquals(2, requiredScreens())

        helper.applyPolicy(AdsPolicy(screensBeforeInterstitial = 3))
        assertEquals(3, requiredScreens())
        helper.setPostAdScreenCooldown(1)
        assertEquals("khoá Remote Config thắng lời gọi của app", 3, requiredScreens())

        helper.applyPolicy(AdsPolicy.DEFAULT)
        assertEquals("gỡ khoá: về giá trị app đặt gần nhất", 1, requiredScreens())
    }

    /** B7, Điều 7.4: policy bật N màn hình cho app không báo màn thì không được chặn interstitial vĩnh viễn. */
    @Test fun `screens tu policy ma app chua bao man nao - khong bat cooldown, canh bao mot lan`() {
        helper.applyPolicy(AdsPolicy(screensBeforeInterstitial = 2))
        mockStatic(Log::class.java).use { log ->
            repeat(2) {
                assertTrue(helper.showInterstitial("home", 1, RecordingShowCb(), isDeltaTime = false))
                inters.dismiss()
                assertFalse(helper.isPostAdScreenCooldownActive())
            }
            log.verify({ Log.e(eq(LogUtil.TAG), contains("recordScreenChange")) }, times(1))
        }

        helper.recordScreenChange()
        assertTrue(helper.showInterstitial("home", 1, RecordingShowCb(), isDeltaTime = false))
        inters.dismiss()
        assertTrue("app đã báo màn: cooldown của policy bật", helper.isPostAdScreenCooldownActive())
    }

    @Test fun `cooldown app tu dat van bat nhu cu du app chua bao man`() {
        helper.setPostAdScreenCooldown(2)
        assertTrue(helper.showInterstitial("home", 1, RecordingShowCb(), isDeltaTime = false))
        inters.dismiss()
        assertTrue(helper.isPostAdScreenCooldownActive())
    }

    // ═══ Remote Config và lần mở kế (B5) ═══

    @Test fun `Remote Config ap ngay va luu ban tho, lan mo ke khoi phuc, go khoa thi ve mac dinh`() {
        val (prefsCtx, editor) = prefsContext(saved = null)
        setField("mContext", prefsCtx)

        helper.applyRemotePolicy(doc62)
        assertEquals(AdsPolicy.parse(doc62), helper.adsPolicy)
        verify(editor).putString(AdsPolicy.PREF_KEY, doc62)

        AdsPolicy.current = AdsPolicy.DEFAULT   // process mới
        val saved = """{"screens_before_interstitial":3,"post_click_cooldown_seconds":20}"""
        helper.restorePolicy(prefsContext(saved = saved).first)
        assertEquals(AdsPolicy.parse(saved), helper.adsPolicy)
        assertEquals("khôi phục đi qua applyPolicy", 3, requiredScreens())

        helper.applyRemotePolicy("")
        assertEquals(AdsPolicy.DEFAULT, helper.adsPolicy)
        assertEquals(0, requiredScreens())
        verify(editor).putString(AdsPolicy.PREF_KEY, "")
    }

    /** P10 (R7): `restorePolicy` chạy trước khi biết country; khi `LogEventServerHelper` biết country, policy resolve lại. */
    @Test fun `P10 ap lai khi biet country sau restorePolicy`() {
        val defaultApplier = AdsPolicyTiers.applier
        val raw = """{"schema_version":1,"interstitial_min_gap_seconds":240,"by_tier":{"T1":{"interstitial_min_gap_seconds":300}}}"""
        try {
            AdsPolicyTiers.applier = helper::applyPolicy
            LogEventServerHelper.onCountryKnown("DEFAULT")
            helper.restorePolicy(prefsContext(saved = raw).first)
            assertEquals("chưa biết country: top-level", 240, helper.adsPolicy.minGapSeconds(30))

            LogEventServerHelper.onCountryKnown("US")
            assertEquals("biết country: tier T1", 300, helper.adsPolicy.minGapSeconds(30))
        } finally {
            AdsPolicyTiers.applier = defaultApplier
            AdsPolicyTiers.remember("")
            LogEventServerHelper.onCountryKnown("")
        }
    }

    /** D30-3: raw không có `by_tier` thì biết country không áp lại (không đè policy app tự đặt). */
    @Test fun `ap lai chi khi raw co by_tier`() {
        val defaultApplier = AdsPolicyTiers.applier
        var calls = 0
        try {
            AdsPolicyTiers.applier = { calls++ }
            AdsPolicyTiers.remember(doc62)
            LogEventServerHelper.onCountryKnown("US")
            assertEquals("raw v1 không by_tier", 0, calls)
            AdsPolicyTiers.remember("{ rac")
            LogEventServerHelper.onCountryKnown("US")
            assertEquals("raw hỏng", 0, calls)
            AdsPolicyTiers.remember("""{"by_tier":{"T1":{"app_open_from_session":2}}}""")
            LogEventServerHelper.onCountryKnown("US")
            assertEquals("raw có by_tier", 1, calls)
        } finally {
            AdsPolicyTiers.applier = defaultApplier
            AdsPolicyTiers.remember("")
            LogEventServerHelper.onCountryKnown("")
        }
    }

    /** m4: khối `by_tier` sai chỉ logE một lần dù áp lại khi biết country. */
    @Test fun `ap lai khong lap logE cua khoi by_tier sai`() {
        val defaultApplier = AdsPolicyTiers.applier
        val raw = """{"schema_version":1,"by_tier":{"T9":{}}}"""
        try {
            AdsPolicyTiers.applier = helper::applyPolicy
            LogEventServerHelper.onCountryKnown("DEFAULT")
            org.mockito.Mockito.mockStatic(Log::class.java).use { log ->
                helper.restorePolicy(prefsContext(saved = raw).first)
                LogEventServerHelper.onCountryKnown("US")
                log.verify({ Log.e(eq(LogUtil.TAG), contains("by_tier sai")) }, times(1))
            }
        } finally {
            AdsPolicyTiers.applier = defaultApplier
            AdsPolicyTiers.remember("")
            LogEventServerHelper.onCountryKnown("")
        }
    }

    private fun prefsContext(saved: String?): Pair<Context, SharedPreferences.Editor> {
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(AdsPolicy.PREF_KEY, "")).thenReturn(saved ?: "")
        val ctx = mock(Context::class.java)
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return ctx to editor
    }
}
