package com.app.plugin.crm

import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.LicenseTestRule
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Cầu nối CRM — cổng gate consent, cấu hình, allowlist sink và hai tín hiệu có dữ liệu
 * thật (SEGMENT_CHANGE, FIRST_PURCHASE) phát từ brain (doc 23 mục E.3, Phase 4).
 *
 * ═══ NGUYÊN TẮC KIỂM TRA ═══
 *
 * 1. Mặc định TẮT: chưa nạp cấu hình → không gì chảy ra ngoài.
 * 2. Gate consent: DENIED chặn, UNKNOWN cũng chặn (#7, DOC/66), childDirected chặn kể cả GRANTED.
 * 3. Hai tín hiệu chuẩn đến từ đường ống thật của AdsBrain — không có tín hiệu bịa.
 */
class CrmBridgeTest {
    @get:Rule val license = LicenseTestRule()

    /** Sink ghi lại mọi tín hiệu — thay cho vendor CRM thật trong test. */
    private class RecordingSink(override val sinkId: String) : CrmBridge.CrmSink {
        val received = mutableListOf<CrmBridge.EngagementSignal>()
        var throwOnDeliver = false
        override fun deliver(signal: CrmBridge.EngagementSignal): Boolean {
            if (throwOnDeliver) throw IllegalStateException("vendor SDK hỏng")
            received += signal
            return true
        }
    }

    private lateinit var sink: RecordingSink

    @Before
    fun setUp() {
        CrmBridge.clear()
        ConsentState.resetForTest()
        // #7 (DOC/66): UNKNOWN chặn tín hiệu; các test cầu nối chạy như người đã đồng ý.
        ConsentState.setTier(ConsentTier.GRANTED)
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.0
        BrainPolicy.clear()
        sink = RecordingSink("test_sink")
    }

    @After
    fun tearDown() {
        CrmBridge.clear()
        ConsentState.resetForTest()
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
        HoldoutManager.holdoutFraction = 0.05
        BrainPolicy.clear()
    }

    private fun enableBridge(sinks: String = "") {
        val json = if (sinks.isEmpty()) {
            """{"version":"crm_v1","enabled":true}"""
        } else {
            """{"version":"crm_v1","enabled":true,"sinks":[$sinks]}"""
        }
        val r = CrmBridge.apply(json)
        assertTrue("cau hinh mau phai ap duoc: ${r.report}", r.applied)
    }

    // ═══ Cấu hình ═══

    @Test
    fun `chua nap cau hinh thi khong phat tin hieu`() {
        CrmBridge.register(sink)
        val report = CrmBridge.emit(
            CrmBridge.EngagementSignal(CrmBridge.SignalKind.FIRST_PURCHASE, "p1", "usd=1.0", false)
        )
        assertTrue("phai bao ly do: $report", report.contains("chưa nạp cấu hình"))
        assertTrue(sink.received.isEmpty())
    }

    @Test
    fun `apply thieu version thi tu choi`() {
        val r = CrmBridge.apply("""{"enabled":true}""")
        assertFalse(r.applied)
        assertTrue("phai noi thieu version: ${r.report}", r.report.contains("version"))
        assertEquals("chưa nạp", CrmBridge.activeVersion())
    }

    @Test
    fun `JSON hong giu cau hinh dang chay`() {
        enableBridge()
        val r = CrmBridge.apply("{rác không phải json")
        assertFalse(r.applied)
        assertEquals("crm_v1", CrmBridge.activeVersion())
    }

    @Test
    fun `enabled false chan moi tin hieu`() {
        CrmBridge.apply("""{"version":"crm_v2","enabled":false}""")
        CrmBridge.register(sink)
        val report = CrmBridge.emit(
            CrmBridge.EngagementSignal(CrmBridge.SignalKind.FIRST_PURCHASE, "p1", "usd=1.0", false)
        )
        assertTrue("phai bao dang tat: $report", report.contains("đang tắt"))
        assertTrue(sink.received.isEmpty())
    }

    // ═══ Gate consent ═══

    @Test
    fun `consent DENIED chan phat tin hieu`() {
        enableBridge()
        CrmBridge.register(sink)
        ConsentState.setTier(ConsentTier.DENIED)
        val report = CrmBridge.emit(
            CrmBridge.EngagementSignal(CrmBridge.SignalKind.SEGMENT_CHANGE, "p1", "A->B", false)
        )
        assertTrue("phai bao consent: $report", report.contains("consent"))
        assertTrue(sink.received.isEmpty())
    }

    @Test
    fun `consent UNKNOWN chan tin hieu`() {
        // #7 (DOC/66): chưa có câu trả lời thì chưa được cấp. CrmBridge không có Context để đọc
        // IABTCF_gdprApplies, nên UNKNOWN chặn cả ngoài GDPR, tới câu trả lời đầu của UMP.
        enableBridge()
        CrmBridge.register(sink)
        ConsentState.resetForTest()
        assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
        val report = CrmBridge.emit(
            CrmBridge.EngagementSignal(CrmBridge.SignalKind.FIRST_PURCHASE, "p1", "usd=1.0", false)
        )
        assertEquals("consent chưa cho phép (tầng UNKNOWN) — không phát tín hiệu CRM", report)
        assertTrue(sink.received.isEmpty())
    }

    @Test
    fun `childDirected chan ke ca khi GRANTED`() {
        enableBridge()
        CrmBridge.register(sink)
        ConsentState.setTier(ConsentTier.GRANTED)
        ConsentState.childDirected = true
        val report = CrmBridge.emit(
            CrmBridge.EngagementSignal(CrmBridge.SignalKind.FIRST_PURCHASE, "p1", "usd=1.0", false)
        )
        assertTrue("phai bao COPPA: $report", report.contains("trẻ em"))
        assertTrue(sink.received.isEmpty())
    }

    // ═══ Allowlist và đăng ký sink ═══

    @Test
    fun `allowlist chi cho sink duoc ke ten`() {
        enableBridge(sinks = "\"braze\"")
        val braze = RecordingSink("braze")
        val oneSignal = RecordingSink("onesignal")
        CrmBridge.register(braze)
        CrmBridge.register(oneSignal)
        CrmBridge.emit(
            CrmBridge.EngagementSignal(CrmBridge.SignalKind.FIRST_PURCHASE, "p1", "usd=1.0", false)
        )
        assertEquals(1, braze.received.size)
        assertTrue("onesignal khong nam trong allowlist", oneSignal.received.isEmpty())
    }

    @Test
    fun `allowlist rong cho phep moi sink da dang ky`() {
        enableBridge()
        val other = RecordingSink("khac")
        CrmBridge.register(sink)
        CrmBridge.register(other)
        CrmBridge.emit(
            CrmBridge.EngagementSignal(CrmBridge.SignalKind.FIRST_PURCHASE, "p1", "usd=1.0", false)
        )
        assertEquals(1, sink.received.size)
        assertEquals(1, other.received.size)
    }

    @Test
    fun `dang ky trung id thay adapter cu`() {
        enableBridge()
        val old = RecordingSink("braze")
        val new = RecordingSink("braze")
        CrmBridge.register(old)
        CrmBridge.register(new)
        CrmBridge.emit(
            CrmBridge.EngagementSignal(CrmBridge.SignalKind.FIRST_PURCHASE, "p1", "usd=1.0", false)
        )
        assertTrue("adapter cu phai duoc thay", old.received.isEmpty())
        assertEquals(1, new.received.size)
    }

    @Test
    fun `sink id rong bi tu choi dang ky`() {
        assertFalse(CrmBridge.register(RecordingSink("")))
        assertFalse(CrmBridge.register(RecordingSink("   ")))
    }

    @Test
    fun `sink nem loi khong chan sink khac va khong lam hong cau noi`() {
        enableBridge()
        sink.throwOnDeliver = true
        val healthy = RecordingSink("healthy")
        CrmBridge.register(sink)
        CrmBridge.register(healthy)
        val report = CrmBridge.emit(
            CrmBridge.EngagementSignal(CrmBridge.SignalKind.FIRST_PURCHASE, "p1", "usd=1.0", false)
        )
        assertEquals(1, healthy.received.size)
        assertTrue("phai bao 1/2 da giao: $report", report.contains("1/2"))
    }

    // ═══ Tín hiệu từ đường ống thật của AdsBrain ═══

    @Test
    fun `onPurchase phat FIRST_PURCHASE mot lan duy nhat`() {
        enableBridge()
        CrmBridge.register(sink)
        AdsBrain.enabled = true
        AdsBrain.onSessionStart("crm_fp_${System.nanoTime()}")

        AdsBrain.onPurchase(4.99)
        assertEquals("luot mua dau phai phat tin hieu", 1, sink.received.size)
        val s = sink.received[0]
        assertEquals(CrmBridge.SignalKind.FIRST_PURCHASE, s.kind)
        assertEquals("usd=4.99", s.detail)
        assertFalse(s.isHoldout)

        AdsBrain.onPurchase(9.99)
        assertEquals("luot mua thu hai khong duoc phat nua", 1, sink.received.size)
    }

    @Test
    fun `SEGMENT_CHANGE phat khi belief thuc su dich chuyen`() {
        enableBridge()
        CrmBridge.register(sink)
        AdsBrain.enabled = true
        val id = "crm_seg_${System.nanoTime()}"
        // Hai phiên để sessionsLifetime đạt ngưỡng hỗ trợ tối thiểu của likelihood.
        AdsBrain.onSessionStart(id)
        AdsBrain.onSessionStart(id)
        // Chuỗi thua dài đẩy posterior ra khỏi prior — belief dịch chuyển thật.
        repeat(30) { AdsBrain.onLevelFail() }

        val seg = sink.received.filter { it.kind == CrmBridge.SignalKind.SEGMENT_CHANGE }
        assertTrue("30 lan thua phai lam segment dich chuyen it nhat mot lan", seg.isNotEmpty())
        val first = seg.first()
        val parts = first.detail.split("->")
        assertEquals("detail phai dang FROM->TO: ${first.detail}", 2, parts.size)
        assertTrue("FROM va TO phai khac nhau: ${first.detail}", parts[0] != parts[1])
    }

    @Test
    fun `tin hieu mang co isHoldout khi nguoi choi thuoc nhom doi chung`() {
        enableBridge()
        CrmBridge.register(sink)
        AdsBrain.enabled = true
        HoldoutManager.holdoutFraction = 1.0
        AdsBrain.onSessionStart("crm_hold_${System.nanoTime()}")

        AdsBrain.onPurchase(1.99)
        assertEquals(1, sink.received.size)
        assertTrue("nhom doi chung phai duoc danh co de loai khoi chien dich CRM",
            sink.received[0].isHoldout)
    }

    @Test
    fun `brain tat van phat FIRST_PURCHASE vi day la su kien that`() {
        // Lượt mua là sự thật, không phải profiling — CRM bật thì phát, độc lập với brain.
        enableBridge()
        CrmBridge.register(sink)
        AdsBrain.enabled = false
        AdsBrain.onSessionStart("crm_nobrain_${System.nanoTime()}")
        AdsBrain.onPurchase(2.99)
        assertEquals(1, sink.received.size)
    }
}
