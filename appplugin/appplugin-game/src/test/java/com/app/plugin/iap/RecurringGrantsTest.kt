package com.app.plugin.iap

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cau noi VIP ↔ virtual currency: phat dinh ky cho nguoi so huu (doc 19, doc 23 muc C.3).
 *
 * ═══ TINH CHAT TRUNG TAM ═══
 *
 * 1. Khong truy linh: vang 3 ngay chi nhan MOT chu ky khi quay lai.
 * 2. Chu ky tinh tu lan claim truoc, khong theo ngay duong lich.
 * 3. So huu = Entitlements HOAC SubscriptionLifecycle ACTIVE — ca hai tat la het quyen.
 */
class RecurringGrantsTest {

    companion object {
        private const val T0 = 1_000_000_000L
        private const val H = 3_600_000L

        fun json(version: String = "rg_v1", extra: String = ""): String = """
            {
              "version": "$version",
              "enabled": true,
              $extra
              "grants": [
                {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500},
                {"product_id": "vip_pass", "period_hours": 168, "reward_type": "ticket", "reward_amount": 1},
                {"product_id": "vip_monthly", "period_hours": 24, "reward_type": "gem", "reward_amount": 100}
              ]
            }
        """.trimIndent()
    }

    @Before
    fun setUp() {
        RecurringGrants.clear()
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        RecurringGrants.clock = { T0 }
    }

    @After
    fun tearDown() {
        RecurringGrants.clear()
        Entitlements.clearAll()
        SubscriptionLifecycle.clear()
        SubscriptionServerState.clock = System::currentTimeMillis
    }

    private fun purchased() = listOf(SubscriptionLifecycle.SubSnapshot("vip_monthly", IapPurchaseState.PURCHASED, true))
    private fun at(ms: Long) { SubscriptionServerState.clock = { ms } }

    // ═══ Cấu hình ═══

    @Test
    fun `chua nap cau hinh van an toan`() {
        assertEquals("chưa nạp", RecurringGrants.activeVersion())
        assertTrue(RecurringGrants.dueGrants().isEmpty())
        assertFalse(RecurringGrants.claim("vip_pass", "gem").claimed)
    }

    @Test
    fun `cau hinh hong bi tu choi va giu ban dang chay`() {
        assertTrue(RecurringGrants.apply(json()).applied)
        assertFalse(RecurringGrants.apply(null).applied)
        assertFalse(RecurringGrants.apply("{hong").applied)
        assertFalse(RecurringGrants.apply("""{"grants": []}""").applied) // thiếu version
        val r = RecurringGrants.apply("{hong")
        assertTrue("report phai noi ro ly do", r.report.contains("từ chối"))
        assertEquals("rg_v1", RecurringGrants.activeVersion())
    }

    @Test
    fun `grant thieu hoac sai truong bi tu choi`() {
        assertFalse(RecurringGrants.apply(
            """{"version": "v", "grants": [{"product_id": "p"}]}"""
        ).applied) // thiếu period_hours
        assertFalse(RecurringGrants.apply(
            """{"version": "v", "grants": [{"product_id": "p", "period_hours": 0, "reward_type": "gem", "reward_amount": 1}]}"""
        ).applied) // period_hours <= 0
        assertFalse(RecurringGrants.apply(
            """{"version": "v", "grants": [{"product_id": "p", "period_hours": 24, "reward_type": "gem", "reward_amount": 0}]}"""
        ).applied) // amount <= 0
    }

    @Test
    fun `dong trung duoc loai bo`() {
        val r = RecurringGrants.apply("""
            {"version": "v", "grants": [
              {"product_id": "p", "period_hours": 24, "reward_type": "gem", "reward_amount": 5},
              {"product_id": "p", "period_hours": 24, "reward_type": "gem", "reward_amount": 9}
            ]}
        """.trimIndent())
        assertTrue(r.applied)
        assertTrue(r.report.contains("bỏ 1 dòng trùng"))
    }

    @Test
    fun `tat enabled thi ngung het`() {
        Entitlements.grant("vip_pass")
        assertTrue(RecurringGrants.apply(json()).applied)
        assertTrue(RecurringGrants.apply(
            """{"version": "rg_v2", "enabled": false, "grants": [
              {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500}]}"""
        ).applied)
        assertTrue(RecurringGrants.dueGrants().isEmpty())
        assertFalse(RecurringGrants.claim("vip_pass", "gem").claimed)
    }

    // ═══ Sở hữu ═══

    @Test
    fun `khong so huu thi khong co gi den luot`() {
        RecurringGrants.apply(json())
        assertTrue(RecurringGrants.dueGrants().isEmpty())
        val r = RecurringGrants.claim("vip_pass", "gem")
        assertFalse(r.claimed)
        assertTrue(r.report.contains("không sở hữu"))
    }

    @Test
    fun `entitlements la mot nguon so huu`() {
        RecurringGrants.apply(json())
        Entitlements.grant("vip_pass")
        val due = RecurringGrants.dueGrants()
        assertEquals("vip_pass có 2 dòng phát", 2, due.size)
        assertTrue(due.none { it.grant.productId == "vip_monthly" })
    }

    @Test
    fun `subscription ACTIVE la nguon so huu thu hai`() {
        RecurringGrants.apply(json())
        // Không có Entitlements — chỉ SubscriptionLifecycle biết sub đang active.
        SubscriptionLifecycle.observe(listOf(
            SubscriptionLifecycle.SubSnapshot("vip_monthly", IapPurchaseState.PURCHASED, true)
        ))
        val due = RecurringGrants.dueGrants()
        assertEquals(1, due.size)
        assertEquals("vip_monthly", due[0].grant.productId)
    }

    @Test
    fun `nhan server RENEWED khong phai nguon so huu (D28)`() {
        RecurringGrants.apply(json())
        assertTrue(SubscriptionServerState.applyServer("vip_monthly", "RENEWED", System.currentTimeMillis()))
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertTrue(RecurringGrants.dueGrants().none { it.grant.productId == "vip_monthly" })
    }

    @Test
    fun `client ACTIVE roi nhan server GRACE van so huu (D28)`() {
        RecurringGrants.apply(json())
        SubscriptionLifecycle.observe(listOf(
            SubscriptionLifecycle.SubSnapshot("vip_monthly", IapPurchaseState.PURCHASED, true)
        ))
        assertTrue(SubscriptionServerState.applyServer("vip_monthly", "GRACE", System.currentTimeMillis() + 1))
        assertEquals(SubState.IN_GRACE_PERIOD, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertEquals(listOf("vip_monthly"), RecurringGrants.dueGrants().map { it.grant.productId })
    }

    // ═══ Carry 1.8.0 (R-10 n1/n2) — đặc tả + giết đột biến ═══

    /**
     * R10n1 — đặc tả + giết đột biến Z2 (bỏ so sánh `eventMs > lần đổi cuối của client` trong `serverLabelWins`):
     * nhãn server GRACE cũ hơn lần đổi cuối của client thì thua; người đăng ký lại vẫn sở hữu.
     */
    @Test
    fun `R10n1 nhan server cu hon lan doi cuoi cua client thi thua`() {
        RecurringGrants.apply(json())
        val t0 = 1_800_000_000_000L
        at(t0); SubscriptionLifecycle.observe(purchased()) // client ACTIVE
        assertTrue(SubscriptionServerState.applyServer("vip_monthly", "GRACE", t0 + 500))
        at(t0 + 2_000); SubscriptionLifecycle.observe(emptyList()) // store bỏ sub → LAPSED (client mới hơn nhãn)
        at(t0 + 3_000); SubscriptionLifecycle.observe(purchased()) // đăng ký lại
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.stateFor("vip_monthly"))
        assertEquals(listOf("vip_monthly"), RecurringGrants.dueGrants().map { it.grant.productId })
    }

    /**
     * R10n2 — đặc tả + giết đột biến Z3 (bỏ `label != clientLabel`): client LAPSED → server RECOVERED → client ACTIVE.
     * Nhãn trùng thì không bỏ qua, nên `clientStateFor` được làm mới thành ACTIVE và `owned()` đúng.
     */
    @Test
    fun `R10n2 nhan trung thi clientStateFor lam moi thanh ACTIVE`() {
        RecurringGrants.apply(json())
        val t0 = 1_800_000_000_000L
        at(t0); SubscriptionLifecycle.observe(purchased())
        at(t0 + 100); SubscriptionLifecycle.observe(emptyList()) // LAPSED
        assertEquals(SubState.LAPSED, SubscriptionLifecycle.clientStateFor("vip_monthly"))
        assertTrue(SubscriptionServerState.applyServer("vip_monthly", "RECOVERED", t0 + 200))
        at(t0 + 300); SubscriptionLifecycle.observe(purchased())
        assertEquals(SubState.ACTIVE, SubscriptionLifecycle.clientStateFor("vip_monthly"))
        assertEquals(listOf("vip_monthly"), RecurringGrants.dueGrants().map { it.grant.productId })
    }

    @Test
    fun `sub LAPSED thi het quyen nhan`() {
        RecurringGrants.apply(json())
        SubscriptionLifecycle.observe(listOf(
            SubscriptionLifecycle.SubSnapshot("vip_monthly", IapPurchaseState.PURCHASED, true)
        ))
        SubscriptionLifecycle.observe(emptyList()) // → LAPSED
        assertTrue("churn rồi thì không còn đặc quyền", RecurringGrants.dueGrants().isEmpty())
    }

    // ═══ Chu kỳ ═══

    @Test
    fun `lan dau nhan duoc ngay lap tuc`() {
        RecurringGrants.apply(json())
        Entitlements.grant("vip_pass")
        val r = RecurringGrants.claim("vip_pass", "gem")
        assertTrue(r.claimed)
        assertEquals("gem", r.rewardType)
        assertEquals(500L, r.rewardAmount)
    }

    @Test
    fun `hai lan lien tiep chi nhan mot`() {
        RecurringGrants.apply(json())
        Entitlements.grant("vip_pass")
        assertTrue(RecurringGrants.claim("vip_pass", "gem").claimed)
        val r = RecurringGrants.claim("vip_pass", "gem")
        assertFalse(r.claimed)
        assertTrue(r.report.contains("chưa đến lượt"))
    }

    @Test
    fun `du chu ky thi nhan tiep — tinh tu lan truoc khong theo lich`() {
        RecurringGrants.apply(json())
        Entitlements.grant("vip_pass")
        var now = T0
        RecurringGrants.clock = { now }
        assertTrue(RecurringGrants.claim("vip_pass", "gem").claimed) // nhận lúc T0
        now = T0 + 23 * H
        assertFalse("23h chưa đủ chu kỳ 24h", RecurringGrants.claim("vip_pass", "gem").claimed)
        now = T0 + 24 * H
        assertTrue("đúng 24h từ lần trước", RecurringGrants.claim("vip_pass", "gem").claimed)
    }

    @Test
    fun `khong truy linh — vang lau chi nhan mot chu ky`() {
        RecurringGrants.apply(json())
        Entitlements.grant("vip_pass")
        var now = T0
        RecurringGrants.clock = { now }
        assertTrue(RecurringGrants.claim("vip_pass", "gem").claimed)
        now = T0 + 10 * 24 * H // vắng 10 ngày
        val r = RecurringGrants.claim("vip_pass", "gem")
        assertTrue(r.claimed)
        assertEquals("chỉ MỘT chu kỳ, không cộng dồn 10 ngày", 500L, r.rewardAmount)
    }

    @Test
    fun `hai dong khac chu ky doc lap nhau`() {
        RecurringGrants.apply(json())
        Entitlements.grant("vip_pass")
        var now = T0
        RecurringGrants.clock = { now }
        assertTrue(RecurringGrants.claim("vip_pass", "gem").claimed)
        assertTrue(RecurringGrants.claim("vip_pass", "ticket").claimed)
        now = T0 + 25 * H
        assertTrue("gem đến lượt sau 24h", RecurringGrants.claim("vip_pass", "gem").claimed)
        assertFalse("ticket chu kỳ 168h chưa đến", RecurringGrants.claim("vip_pass", "ticket").claimed)
    }

    @Test
    fun `claim dong khong co trong cau hinh bi tu choi`() {
        RecurringGrants.apply(json())
        Entitlements.grant("vip_pass")
        assertFalse(RecurringGrants.claim("vip_pass", "kim_cuong").claimed)
    }

    // ═══ Reset ═══

    @Test
    fun `clear xoa trang thai`() {
        RecurringGrants.apply(json())
        Entitlements.grant("vip_pass")
        RecurringGrants.claim("vip_pass", "gem")
        RecurringGrants.clear()
        assertEquals("chưa nạp", RecurringGrants.activeVersion())
        assertTrue(RecurringGrants.dueGrants().isEmpty())
    }
}
