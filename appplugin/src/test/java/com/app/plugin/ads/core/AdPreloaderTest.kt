package com.app.plugin.ads.core

import android.content.Context
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.ads.AdsKillSwitchTest
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.ads.RemoveAdsMask.FLAG_FULLSCREEN
import com.app.plugin.ads.RemoveAdsMask.FLAG_REWARDED
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.license.LicenseGate
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.LogUtil
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedStatic
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * 2.3.0 PRELOAD (D15-230, D17-230 c, Hợp đồng E): [AdPreloader] qua cổng thật của `AdSdkStartup.startAdNetworks` (PL1, PL2)
 * và qua [FakePort] (PL3–PL6, PL8, PL10, chờ host). Seam đếm load = [FakePort.load] (thay lời gọi `AdsHelper.load*ForThisTurn`).
 * Dòng log cổng thiết bị đọc phải là `Log.i` (luật 26): kiểm bằng `mockStatic(Log)`.
 */
class AdPreloaderTest {
    private val all = listOf("interstitial full_default", "app_open full_splash", "rewarded gift_default", "rewarded_interstitial rwi_default")
    private lateinit var port: FakePort

    /** Mạng sẵn sàng ngay ([sdkReady]), đồng hồ ảo cho `post`, host có, cấu hình demo (open `full_splash`, có rwi). */
    private class FakePort : AdPreloader.Port {
        var now = 0L
        private val queue = mutableListOf<Pair<Long, () -> Unit>>()
        var sdkReady = true
        private val held = mutableListOf<() -> Unit>()
        var host = true
        var opens = listOf("full_splash")
        var rwi = true
        val removedFlags = mutableSetOf<Int>()
        val disabledFlags = mutableSetOf<Int>()
        var interWaitMs = 0L
        val loads = mutableListOf<String>()
        val loading = mutableSetOf<String>()   // plcf.isLoading theo placement
        var startsRequest = true               // false = controller từ chối / mạng trả "has loaded"
        var full2 = 0                          // full2FlagShow
        override fun whenSdkReady(action: () -> Unit) { if (sdkReady) action() else held += action }
        fun markReady() { sdkReady = true; held.toList().also { held.clear() }.forEach { it() } }
        override fun post(delayMs: Long, action: () -> Unit) { queue += (now + delayMs) to action }
        fun advance(to: Long) {
            while (true) {
                val next = queue.filter { it.first <= to }.minByOrNull { it.first } ?: break
                queue.remove(next)
                now = maxOf(now, next.first)
                next.second()
            }
            now = to
        }
        fun runPosted() = advance(now)
        override fun hostReady(): Boolean = host
        override fun openPlacements(): List<String> = opens
        override fun rwiConfigured(): Boolean = rwi
        override fun removed(flag: Int): Boolean = flag in removedFlags
        override fun disabled(flag: Int): Boolean = flag in disabledFlags
        override fun interstitialWaitMs(): Long = interWaitMs
        override fun isLoading(placement: String): Boolean = placement in loading
        override fun full2FlagShow(): Int = full2
        override fun load(slot: PreloadSlot) {
            loads += "${slot.format.rc} ${slot.placement}"
            if (!startsRequest) return
            loading += slot.placement
            val f = slot.format
            if ((f == PreloadFormat.INTERSTITIAL && full2 in setOf(1, 3)) || (f == PreloadFormat.REWARDED && full2 >= 2)) {
                if (!removed(FLAG_FULLSCREEN) && !disabled(FLAG_FULLSCREEN)) loading += "full_2_default"
            }
        }
    }

    @Before fun setUp() {
        ConsentState.resetForTest()
        AdSdkStartup.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdEventLog.resetPreloadForTest()
        AdSdkStartup.networkStarters = AdSdkStartup.NetworkStarters(admob = {}, meta = {}, max = {})
        port = FakePort()
        AdPreloader.port = port
    }

    @After fun tearDown() {
        AdSdkStartup.resetForTest()
        ConsentState.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdEventLog.resetPreloadForTest()
    }

    /** UMP trả [canRequest]; Firebase Analytics mock (`sdk_health`); `android.util.Log` ghi lại. */
    private fun withStartup(canRequest: Boolean = true, block: (Context, MockedStatic<Log>) -> Unit) {
        ConsentState.setTier(ConsentTier.GRANTED)
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.canRequestAds()).thenReturn(canRequest)
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }.thenReturn(ci)
            mockStatic(Class.forName("com.google.firebase.analytics.AnalyticsKt")).use { analyticsKt ->
                analyticsKt.`when`<FirebaseAnalytics> { Firebase.analytics }.thenReturn(mock(FirebaseAnalytics::class.java))
                mockStatic(Log::class.java).use { log ->
                    try {
                        block(context, log)
                    } finally {
                        ConsentState.resetForTest()
                        ConsentManager.forgetActivityForTest()
                    }
                }
            }
        }
    }

    private fun withLog(block: (MockedStatic<Log>) -> Unit) = mockStatic(Log::class.java).use(block)

    private fun MockedStatic<Log>.info(msg: String) = verify({ Log.i(LogUtil.TAG, msg) }, atLeastOnce())

    private fun freshPort(): FakePort {
        AdPreloader.resetForTest()
        return FakePort().also { port = it; AdPreloader.port = it }
    }

    @Test fun `PL1 cong VALID - mot luot moi process, dung 1 load moi slot cau hinh, dong log Log_i`() = withStartup { context, log ->
        AdSdkStartup.requestAdNetworks(context)
        AdSdkStartup.requestAdNetworks(context)
        port.advance(1_500)
        assertEquals("đúng một lời gọi load mỗi slot (demo 4)", all, port.loads)

        AdPreloader.schedule()   // lần gọi thứ hai trong process (M5: bỏ AtomicBoolean)
        port.runPosted()
        assertEquals("lần schedule thứ hai không mở lượt mới", all, port.loads)
        all.forEach { log.info("preload: $it load") }
    }

    @Test fun `PL2 kill switch ads - startAdNetworks tra som, 0 load`() {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        withStartup { context, _ -> AdSdkStartup.requestAdNetworks(context); port.advance(1_500) }
        assertEquals(emptyList<String>(), port.loads)
    }

    @Test fun `PL2 giay phep INVALID - 0 load`() {
        LicenseGate.resetForTest(LicenseGate.Verdict.Invalid("sig"))
        withStartup { context, _ -> AdSdkStartup.requestAdNetworks(context); port.advance(1_500) }
        assertEquals(emptyList<String>(), port.loads)
    }

    @Test fun `PL2 consent chua cho phep - cong dong, 0 load`() {
        withStartup(canRequest = false) { context, _ -> AdSdkStartup.requestAdNetworks(context); port.advance(1_500) }
        assertEquals(emptyList<String>(), port.loads)
    }

    @Test fun `PL3 mask FLAG_FULLSCREEN bo interstitial, app open, rwi - FLAG_REWARDED chi bo rewarded, moi slot mot dong skip`() = withLog { log ->
        port.removedFlags += FLAG_FULLSCREEN
        AdPreloader.schedule(); port.advance(1_500)
        assertEquals(listOf("rewarded gift_default"), port.loads)
        listOf("interstitial full_default", "app_open full_splash", "rewarded_interstitial rwi_default").forEach { log.info("preload: skip $it (remove_ads)") }

        val p = freshPort().apply { removedFlags += FLAG_REWARDED }   // 2.4.0 #27: rwi chỉ bỏ theo FLAG_FULLSCREEN (như controller)
        AdPreloader.schedule(); p.advance(1_500)
        assertEquals(listOf("interstitial full_default", "app_open full_splash", "rewarded_interstitial rwi_default"), p.loads)
        log.info("preload: skip rewarded gift_default (remove_ads)")

        val k = freshPort().apply { disabledFlags += FLAG_FULLSCREEN }   // kill / giấy phép / maskAdsStatus
        AdPreloader.schedule(); k.advance(1_500)
        assertEquals(listOf("rewarded gift_default", "rewarded_interstitial rwi_default"), k.loads)
        log.info("preload: skip interstitial full_default (disabled)")
    }

    @Test fun `PL4 RC enabled false - 0 load va preload off, formats thu hep, JSON hong va schema 2 ve mac dinh`() = withLog { log ->
        AdPreloader.applyRemote("""{"schema_version":1,"enabled":false}""")
        AdPreloader.schedule(); port.runPosted()
        assertEquals(emptyList<String>(), port.loads)
        log.info("preload: off (rc)")

        val p = freshPort()
        AdPreloader.applyRemote("""{"schema_version":1,"formats":["rewarded"]}""")
        AdPreloader.schedule(); p.advance(1_500)
        assertEquals(listOf("rewarded gift_default"), p.loads)

        for (bad in listOf("{", """{"schema_version":2,"enabled":false}""", """{"formats":["banner"]}""", """{"enabled":"no"}""")) {
            AdPreloader.applyRemote("""{"enabled":false}""")
            AdPreloader.applyRemote(bad)
            assertEquals("hỏng → mặc định cả khối: $bad", AdPreloadPlan.DEFAULT, AdPreloader.config)
        }
        AdPreloader.applyRemote("""{"schema_version":1,"delay_ms":999999}""")
        assertEquals("delay_ms kẹp 0..60 000", 60_000L, AdPreloader.config.delayMs)
        assertEquals(AdPreloadPlan.DEFAULT to null, AdPreloadPlan.parse(""))
    }

    @Test fun `PL5 delay_ms - load sau dung delay, mang chua san sang thi cho`() {
        AdPreloader.applyRemote("""{"schema_version":1,"delay_ms":1500}""")
        port.sdkReady = false
        AdPreloader.schedule()
        port.advance(5_000)
        assertEquals("chờ MobileAds init", emptyList<String>(), port.loads)

        port.markReady()
        port.advance(port.now + 1_499)
        assertEquals(emptyList<String>(), port.loads)
        port.advance(port.now + 1)
        assertEquals(all, port.loads)
    }

    @Test fun `PL6 khong co rwi_ hay app open cau hinh - bo slot do, mot dong skip`() = withLog { log ->
        port.rwi = false
        port.opens = emptyList()
        AdPreloader.schedule(); port.advance(1_500)
        assertEquals(listOf("interstitial full_default", "rewarded gift_default"), port.loads)
        log.info("preload: skip rewarded_interstitial rwi_default (not configured)")
        log.info("preload: skip app_open - (not configured)")
    }

    @Test fun `PL8 ad_request cua luot preload mang trigger=preload, luot thuong khong, ad_fill dong luot`() {
        val r = AdEventLog.LoadRequest("interstitial", "admob", "full_default", "u", "id", "s", 1)
        port.full2 = 1
        AdPreloader.schedule(); port.advance(1_500)
        assertEquals("preload", AdEventLog.requestEventData(r, "full_default")["trigger"])
        assertEquals("Full2 kéo theo cũng là lượt preload", "preload", AdEventLog.requestEventData(r, "full_2_default")["trigger"])
        assertEquals(null, AdEventLog.requestEventData(r, "nt_default")["trigger"])
        assertEquals(AdEventLog.requestData(r, "nt_default"), AdEventLog.requestEventData(r, "nt_default"))
        ServerEventCapture().use { AdEventLog.fill(r, "full_default", 10L, "admob") }
        assertEquals("fill đóng lượt", null, AdEventLog.requestEventData(r, "full_default")["trigger"])
    }

    @Test fun `PL10 fullStartTime ge 120 - interstitial hoan toi khi cua so dong, khong bi bo im lang`() = withLog { log ->
        port.interWaitMs = 40_000L
        AdPreloader.schedule(); port.advance(1_500)
        assertEquals(all - "interstitial full_default", port.loads)
        log.info("preload: defer interstitial full_default 40000 ms (fullStartTime)")
        port.advance(41_500)
        assertEquals("cửa sổ đóng: interstitial được nạp", all - "interstitial full_default" + "interstitial full_default", port.loads)
    }

    @Test fun `PL10 cua so fullStartTime va fullSessionStart nhu AdsFrequencyGate`() {
        assertEquals("120 → t/3 = 40 s, đang ở giây 3", 38_000L, AdPreloadPlan.startWindowWaitMs(1003, 1000, 120, 1000, 0))
        assertEquals("< 120 không chặn", 0L, AdPreloadPlan.startWindowWaitMs(1003, 1000, 119, 1000, 0))
        assertEquals("qua cửa sổ", 0L, AdPreloadPlan.startWindowWaitMs(1041, 1000, 120, 1000, 0))
        assertEquals("lấy cửa sổ dài hơn (phiên)", 98_000L, AdPreloadPlan.startWindowWaitMs(1003, 1000, 120, 1000, 300))
    }

    @Test fun `chua co host Activity - cho setMainActivity roi nap, chi mot lan`() = withLog { log ->
        port.host = false
        AdPreloader.schedule(); port.runPosted()
        assertEquals(emptyList<String>(), port.loads)
        log.info("preload: waiting for host activity")
        port.host = true
        AdPreloader.onHostSet(); port.advance(1_500)
        AdPreloader.onHostSet(); port.runPosted()
        assertEquals(all, port.loads)
    }

    private val req = AdEventLog.LoadRequest("interstitial", "admob", "x", "u", "id", "s", 1)
    private fun tagged(slot: String) = AdEventLog.requestEventData(req, slot)["trigger"] == "preload"

    @Test fun `PL8b preload tu choi hoac khong mo luot - ad_request ke tiep cua app khong mang trigger, log skip`() = withLog { log ->
        port.loading += "full_default"     // app đã nạp full_default (vd. Invoice Maker sau consent)
        port.full2 = 1
        AdPreloader.schedule(); port.advance(1_500)
        assertEquals("slot đang nạp: không đánh dấu, cả Full2", listOf(false, false), listOf(tagged("full_default"), tagged("full_2_default")))
        log.info("preload: skip interstitial full_default (loading)")

        val p = freshPort().apply { startsRequest = false }   // controller từ chối / "has loaded": không request
        AdPreloader.schedule(); p.advance(1_500)
        listOf("full_default", "full_splash", "gift_default", "rwi_default").forEach { assertEquals(it, false, tagged(it)) }
        log.info("preload: skip rewarded gift_default (held or refused)")

        val q = freshPort().apply { full2 = 2; removedFlags += FLAG_FULLSCREEN }   // rewarded còn, Full2 bị mask
        AdPreloader.schedule(); q.advance(1_500)
        assertEquals(true, tagged("gift_default"))
        assertEquals("Full2 không request dưới mask: không đánh dấu", false, tagged("full_2_default"))

        AdEventLog.resetPreloadForTest(); val z = freshPort()   // full2FlagShow 0: interstitial mở lượt, Full2 không
        AdPreloader.schedule(); z.advance(1_500)
        assertEquals(listOf(true, false), listOf(tagged("full_default"), tagged("full_2_default")))
    }

    @Test fun `CA7 dong skip mang placement - preload skip rewarded_interstitial rwi_default (loading), Log_i`() = withLog { log ->
        port.loading += "rwi_default"
        AdPreloader.schedule(); port.advance(1_500)
        log.info("preload: skip rewarded_interstitial rwi_default (loading)")
    }

    // 2.4.3 PREORDER (Hợp đồng C): delay_ms 0 ⇒ chờ full_splash/nt_splash đang nạp, ân hạn 1 500 ms, trần 10 000 ms, hỏi lại 250 ms.
    @Test fun `PO1 nt_splash dang nap - preload nhuong, splash xong thi nap du slot o luot hoi ke`() = withLog { log ->
        port.loading += "nt_splash"
        AdPreloader.schedule(); port.runPosted()
        assertEquals("preload phải nhường splash đang nạp", emptyList<String>(), port.loads)
        port.advance(5_000)
        assertEquals("còn nạp: vẫn chờ", emptyList<String>(), port.loads)
        port.loading -= "nt_splash"
        port.advance(5_249)
        assertEquals("chưa tới lượt hỏi kế", emptyList<String>(), port.loads)
        port.advance(5_250)
        assertEquals("lượt hỏi kế (≤ 250 ms): đủ 4 slot", all, port.loads)
        log.info("preload: splash done after 5250 ms (settled)")
    }

    @Test fun `PO2 RC delay_ms 1500 thang - splash dang nap van nap dung luc 1500, khong cho splash`() = withLog { log ->
        AdPreloader.applyRemote("""{"schema_version":1,"delay_ms":1500}""")
        port.loading += "nt_splash"
        AdPreloader.schedule()
        port.advance(1_499)
        assertEquals(emptyList<String>(), port.loads)
        port.advance(1_500)
        assertEquals("RC delay_ms thắng (hành vi 2.4.2)", all, port.loads)
        log.verify({ Log.i(LogUtil.TAG, "preload: wait splash") }, never())
    }

    @Test fun `PO3 splash nap mai - tran 10 000 ms roi nap, dong cap`() = withLog { log ->
        port.loading += "nt_splash"
        AdPreloader.schedule()
        port.advance(9_999)
        assertEquals("trước trần: chờ", emptyList<String>(), port.loads)
        port.advance(10_000)
        assertEquals("trần 10 s: chạy như cũ", all, port.loads)
        log.info("preload: splash done after 10000 ms (cap)")
    }

    @Test fun `PO4 khong splash nao - an han 1 500 ms, dong none - splash hien o 500 xong o 3 000 thi nap sau 3 000`() = withLog { log ->
        AdPreloader.schedule()
        port.advance(1_499)
        assertEquals("ân hạn: chờ splash có thể bắt đầu", emptyList<String>(), port.loads)
        port.advance(1_500)
        assertEquals(all, port.loads)
        log.info("preload: splash done after 1500 ms (none)")

        val p = freshPort()
        AdPreloader.schedule()
        p.advance(500)
        p.loading += "full_splash"
        p.advance(2_999)
        assertEquals("splash thấy trong ân hạn: chờ qua 1 500 ms", emptyList<String>(), p.loads)
        p.loading -= "full_splash"
        p.advance(3_000)
        assertEquals(all, p.loads)
        log.info("preload: splash done after 3000 ms (settled)")
    }

    @Test fun `PO5 preload wait splash dung mot dong moi luot, Log_i`() = withLog { log ->
        port.loading += "full_splash"
        AdPreloader.schedule()
        port.advance(2_000)
        port.loading -= "full_splash"
        port.advance(20_000)
        log.verify({ Log.i(LogUtil.TAG, "preload: wait splash") }, times(1))
        log.verify({ Log.i(LogUtil.TAG, "preload: splash done after 2250 ms (settled)") }, times(1))
        assertEquals(all, port.loads)
    }

    // Fable C2 (plan-signoff 2.4.3): hỏi isLoading mỗi 250 ms không được tạo entry placement ở app không có nó (vd. IM).
    @Test fun `PO6 HelperPort isLoading khong tao config cho placement vang - nt_splash o app khong co`() {
        val helper = AdsKillSwitchTest.newAdsHelper()
        val port = AdPreloader.HelperPort { helper }
        repeat(3) { assertFalse(port.isLoading("nt_splash")) }
        assertNull("hỏi isLoading không tạo entry nt_splash", helper.placementsConfigManager.peekConfig("nt_splash"))
        helper.placementsConfigManager.getConfig(PlacementDefault.PLFullSplash, 1)!!.isLoading = true
        assertTrue("entry có sẵn: đọc đúng isLoading", port.isLoading(PlacementDefault.PLFullSplash))
    }

    @Test fun `m1 hai dinh dang chung mot placement - chi mot dong load, dinh dang sau skip loading`() = withLog { log ->
        port.opens = listOf("full_default")   // Speaker Clean: admob_openad = full_default, cùng AdsPlacementConfig với interstitial
        AdPreloader.schedule(); port.advance(1_500)
        assertEquals(listOf("interstitial full_default", "rewarded gift_default", "rewarded_interstitial rwi_default"), port.loads)
        log.info("preload: skip app_open full_default (loading)")
        log.verify({ Log.i(LogUtil.TAG, "preload: app_open full_default load") }, never())
    }
}
