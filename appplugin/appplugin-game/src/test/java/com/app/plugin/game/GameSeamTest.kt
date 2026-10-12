package com.app.plugin.game

import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.BrainSignalListener
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.LicenseTestRule
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.crm.CrmBridge
import com.app.plugin.firbase.RcSectionRegistry
import com.app.plugin.iap.BattlePass
import com.app.plugin.iap.PurchaseRouter
import com.app.plugin.iap.RecurringGrants
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.offerwall.OfferwallManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.0.0 C1d (D1, D16, plan §Hợp đồng C): core chỉ tới mã game (BattlePass, RecurringGrants, PurchaseRouter,
 * OfferwallManager, CrmBridge) qua seam: [RcSectionRegistry] (5 section RC) và [BrainSignalListener] (2 tín
 * hiệu brain). [GameModule] (tên cố định) tự đăng ký; core dò nó đúng một lần, 0 I/O, 0 gửi (R-P1).
 * 2.1.0 G1: nửa "có GameModule" (G3b, G4, G5) ở `appplugin-game`; G2 census + G3a "vắng" ở `GameSeamRootTest` của gốc.
 */
class GameSeamTest {
    @get:Rule val license = LicenseTestRule()

    /** Sink CRM ghi lại mọi tín hiệu (thay vendor thật). */
    private class RecordingSink : CrmBridge.CrmSink {
        override val sinkId = "g_sink"
        val received = CopyOnWriteArrayList<CrmBridge.EngagementSignal>()
        override fun deliver(signal: CrmBridge.EngagementSignal): Boolean = received.add(signal)
    }

    private val sink = RecordingSink()

    @Before fun setUp() {
        reset()
        ConsentState.setTier(ConsentTier.GRANTED)
        HoldoutManager.holdoutFraction = 0.0
    }

    @After fun tearDown() {
        reset()
        HoldoutManager.holdoutFraction = 0.05
    }

    private fun reset() {
        RcSectionRegistry.resetForTest()
        PurchaseRouter.clear(); BattlePass.clear(); RecurringGrants.clear(); OfferwallManager.clear(); CrmBridge.clear()
        ConsentState.resetForTest()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        BrainPolicy.clear()
    }

    private fun enableCrm() {
        assertTrue(CrmBridge.apply("""{"version":"crm_g","enabled":true}""").applied)
        assertTrue(CrmBridge.register(sink))
    }

    private fun kinds(kind: CrmBridge.SignalKind) = sink.received.filter { it.kind == kind }

    @Test
    fun `G3b GameModule co - do dung mot lan va keep rule`() {
        RcSectionRegistry.resetForTest()
        val found = mutableListOf<String>()
        RcSectionRegistry.classFor = { found += it; Class.forName(it) }
        repeat(3) { RcSectionRegistry.ensureGameModule() }
        assertEquals("có: dò đúng một lần", listOf(RcSectionRegistry.GAME_MODULE_CLASS), found)
        assertEquals(GAME_KEYS, RcSectionRegistry.sections().map { it.key })
        assertNotNull(BrainSignalListener.current())
        // R8 không thấy Class.forName(tên cố định) + reflection install(): thiếu keep rule thì bản release xoá/đổi tên, dò ra vắng.
        val keep = "-keep class ${RcSectionRegistry.GAME_MODULE_CLASS} { public static final " +
            "${RcSectionRegistry.GAME_MODULE_CLASS} INSTANCE; public void install(); }"
        assertTrue("consumer-rules.pro thiếu đúng dòng '$keep'", File("consumer-rules.pro").readLines().any { it.trim() == keep })
    }

    @Test
    fun `G4 first purchase va segment change toi CrmBridge y het truoc`() {
        enableCrm()
        AdsBrain.enabled = true
        val buyer = "g4_fp_${System.nanoTime()}"
        AdsBrain.onSessionStart(buyer)
        AdsBrain.onPurchase(4.99)
        AdsBrain.onPurchase(9.99)
        val fp = kinds(CrmBridge.SignalKind.FIRST_PURCHASE)
        assertEquals("lượt mua đầu phát đúng một lần", 1, fp.size)
        assertEquals(CrmBridge.EngagementSignal(CrmBridge.SignalKind.FIRST_PURCHASE, buyer, "usd=4.99", false, 0L), fp[0].copy(atMs = 0L))

        val player = "g4_seg_${System.nanoTime()}"
        AdsBrain.onSessionStart(player)
        AdsBrain.onSessionStart(player)
        repeat(30) { AdsBrain.onLevelFail() }
        val seg = kinds(CrmBridge.SignalKind.SEGMENT_CHANGE)
        assertTrue("30 lần thua phải làm segment dịch chuyển", seg.isNotEmpty())
        seg.forEachIndexed { i, s ->
            assertEquals(player, s.playerId)
            val (from, to) = s.detail.split("->").also { assertEquals(s.detail, 2, it.size) }
            assertNotEquals("FROM khác TO: ${s.detail}", from, to)
            // Mỗi dịch chuyển phát đúng một lần: tín hiệu sau nối tiếp tín hiệu trước (phát trùng làm đứt chuỗi).
            if (i > 0) assertEquals("chuỗi segment: ${seg.map { it.detail }}", seg[i - 1].detail.substringAfter("->"), from)
        }
    }

    @Test
    fun `G5 DENIED - dang ky va ap section game gui 0 (R-P1)`() {
        ConsentState.setTier(ConsentTier.DENIED)
        CrmBridge.register(sink)
        ServerEventCapture().use { cap ->
            cap.autoAck = true
            RcSectionRegistry.ensureGameModule()
            RcSectionRegistry.sections().forEach { it.apply(SAMPLE.getValue(it.key)) }
            val listener = requireNotNull(BrainSignalListener.current()) { "GameModule phải cài listener" }
            listener.onFirstPurchase("g5", 1.0, false)
            listener.onSegmentChange("g5", "A", "B", false)
            Thread.sleep(SETTLE_MS)
            assertEquals("DENIED: 0 request tới server", 0, cap.allRequests().size)
        }
        assertEquals(GAME_KEYS, RcSectionRegistry.sections().map { it.key })
        assertEquals("crm_g5", CrmBridge.activeVersion())
        assertEquals("DENIED: 0 tín hiệu CRM", 0, sink.received.size)
    }

    private companion object {
        const val SETTLE_MS = 300L
        val GAME_KEYS = listOf(
            "purchase_routing_android", "battle_pass_android", "recurring_grants_android",
            "offerwall_config_android", "crm_bridge_android"
        )
        val SAMPLE = mapOf(
            "purchase_routing_android" to """{"version":"pr_g5","rules":[]}""",
            "battle_pass_android" to """{"season_id":"s1"}""",
            "recurring_grants_android" to """{"version":"rg_g5","grants":[]}""",
            "offerwall_config_android" to """{"version":"ow_g5","enabled":true}""",
            "crm_bridge_android" to """{"version":"crm_g5","enabled":true}"""
        )
    }
}
