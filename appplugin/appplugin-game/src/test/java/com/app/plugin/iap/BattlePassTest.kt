package com.app.plugin.iap

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Battle pass engine: cau hinh mua, XP/tier, catch-up, claim hai track (doc 23 muc C.3).
 *
 * ═══ TINH CHAT TRUNG TAM ═══
 *
 * 1. Premium la non-renewing IAP theo mua — entitlement mua cu khong mo track premium mua moi.
 * 2. Game so huu XP, SDK so huu luat: ngoai mua khong co XP, het mua khong duoc claim.
 * 3. Catch-up chi bat dau sau start_fraction cua mua — nguoi vao muon duoi kip duoc.
 */
class BattlePassTest {

    companion object {
        private const val START = 1_000_000L
        private const val END = 2_000_000L // mùa dài 1_000_000ms
        private const val MID = 1_500_000L // đúng 50% mùa

        /** Cấu hình chuẩn: 3 bậc, premium product bp_s1_premium, catch-up x2 sau 50%. */
        fun seasonJson(
            season: String = "season_1",
            version: String = "bp_v1",
            premium: String? = "bp_s1_premium",
            catchUp: String = """"catch_up": {"enabled": true, "start_fraction": 0.5, "multiplier": 2.0}"""
        ): String {
            val premiumPart = if (premium != null) """"premium_product_id": "$premium",""" else ""
            return """
                {
                  "version": "$version",
                  "season_id": "$season",
                  "start_ts_ms": $START,
                  "end_ts_ms": $END,
                  $premiumPart
                  "xp_cap": 1000,
                  "tiers": [
                    {"tier": 1, "xp_required": 0,
                     "rewards_free": [{"id": "f1", "type": "gem", "amount": 10}],
                     "rewards_premium": [{"id": "p1", "type": "skin", "amount": 1}]},
                    {"tier": 2, "xp_required": 100,
                     "rewards_free": [{"id": "f2", "type": "gem", "amount": 20}],
                     "rewards_premium": []},
                    {"tier": 3, "xp_required": 300,
                     "rewards_free": [],
                     "rewards_premium": [{"id": "p3", "type": "emote", "amount": 1}]}
                  ],
                  $catchUp
                }
            """.trimIndent()
        }
    }

    @Before
    fun setUp() {
        BattlePass.clear()
        Entitlements.clearAll()
        BattlePass.clock = { MID }
    }

    @After
    fun tearDown() {
        BattlePass.clear()
        Entitlements.clearAll()
    }

    // ═══ Cấu hình ═══

    @Test
    fun `chua nap cau hinh van tra loi an toan`() {
        assertEquals("chưa nạp", BattlePass.activeVersion())
        assertEquals(BattlePass.SeasonPhase.NO_SEASON, BattlePass.phase())
        assertEquals(0L, BattlePass.addXp(100))
        assertEquals(0, BattlePass.currentTier())
        assertFalse(BattlePass.isPremiumActive())
        assertTrue(BattlePass.claimable().isEmpty())
    }

    @Test
    fun `cau hinh rong hong hoac thieu truong bi tu choi`() {
        assertFalse(BattlePass.apply(null).applied)
        assertFalse(BattlePass.apply("   ").applied)
        assertFalse(BattlePass.apply("{json hong").applied)
        assertFalse(BattlePass.apply("""{"season_id": "s1"}""").applied) // thiếu version
        assertFalse(BattlePass.apply("""{"version": "v1"}""").applied) // thiếu season_id
        assertFalse(BattlePass.apply(
            """{"version": "v1", "season_id": "s1", "start_ts_ms": 100, "end_ts_ms": 50,
                "tiers": [{"tier": 1, "xp_required": 0}]}"""
        ).applied) // end trước start
    }

    @Test
    fun `tiers sai cau truc bi tu choi`() {
        val base = """{"version": "v1", "season_id": "s1", "start_ts_ms": $START, "end_ts_ms": $END, "tiers": %s}"""
        // xp_required không tăng dần
        assertFalse(BattlePass.apply(base.format(
            """[{"tier":1,"xp_required":10},{"tier":2,"xp_required":5}]"""
        )).applied)
        // trùng số bậc
        assertFalse(BattlePass.apply(base.format(
            """[{"tier":1,"xp_required":0},{"tier":1,"xp_required":10}]"""
        )).applied)
        // xp_required âm
        assertFalse(BattlePass.apply(base.format(
            """[{"tier":1,"xp_required":-1}]"""
        )).applied)
    }

    @Test
    fun `catch up sai tham so bi tu choi`() {
        assertFalse(BattlePass.apply(
            seasonJson(catchUp = """"catch_up": {"enabled": true, "start_fraction": 1.5}""")
        ).applied)
        assertFalse(BattlePass.apply(
            seasonJson(catchUp = """"catch_up": {"enabled": true, "multiplier": 0.5}""")
        ).applied)
    }

    @Test
    fun `cau hinh hong giu nguyen mua dang chay`() {
        assertTrue(BattlePass.apply(seasonJson()).applied)
        val r = BattlePass.apply("{hong")
        assertFalse(r.applied)
        assertTrue("report phai noi ro ly do tu choi", r.report.contains("từ chối"))
        assertEquals("bp_v1", BattlePass.activeVersion())
    }

    @Test
    fun `cau hinh hop le duoc ap dung`() {
        val r = BattlePass.apply(seasonJson())
        assertTrue(r.applied)
        assertTrue(r.report.contains("season_1"))
        assertTrue(r.report.contains("3 bậc"))
    }

    @Test
    fun `pass chi co track free van hop le`() {
        assertTrue(BattlePass.apply(seasonJson(premium = null)).applied)
        assertFalse("khong co san pham premium thi khong the active", BattlePass.isPremiumActive())
    }

    // ═══ Thời gian mùa ═══

    @Test
    fun `phase theo dung moc thoi gian`() {
        BattlePass.apply(seasonJson())
        assertEquals(BattlePass.SeasonPhase.NOT_STARTED, BattlePass.phase(START - 1))
        assertEquals(BattlePass.SeasonPhase.ACTIVE, BattlePass.phase(START))
        assertEquals(BattlePass.SeasonPhase.ACTIVE, BattlePass.phase(END))
        assertEquals(BattlePass.SeasonPhase.ENDED, BattlePass.phase(END + 1))
    }

    // ═══ XP và tier ═══

    @Test
    fun `ngoai mua khong co xp`() {
        BattlePass.apply(seasonJson())
        BattlePass.clock = { START - 1 }
        assertEquals("chưa mở mùa", 0L, BattlePass.addXp(100))
        BattlePass.clock = { END + 1 }
        assertEquals("đã hết mùa", 0L, BattlePass.addXp(100))
        assertEquals(0L, BattlePass.xpTotal())
        // Bậc 1 ngưỡng 0 XP luôn đạt ngay khi có cấu hình — đó là thiết kế, không phải XP.
        assertEquals(1, BattlePass.currentTier())
    }

    @Test
    fun `xp cong don va vuot nguong tier`() {
        BattlePass.apply(seasonJson(catchUp = """"catch_up": {"enabled": false}"""))
        assertEquals(40L, BattlePass.addXp(40))
        assertEquals(1, BattlePass.currentTier())
        assertEquals(60L, BattlePass.addXp(60)) // tổng 100 → bậc 2
        assertEquals(2, BattlePass.currentTier())
        BattlePass.addXp(200) // tổng 300 → bậc 3
        assertEquals(3, BattlePass.currentTier())
    }

    @Test
    fun `tran xp duoc ton trong`() {
        BattlePass.apply(seasonJson(catchUp = """"catch_up": {"enabled": false}"""))
        assertEquals("xp_cap = 1000", 1000L, BattlePass.addXp(5000))
        assertEquals(0L, BattlePass.addXp(50))
        assertEquals(1000L, BattlePass.xpTotal())
    }

    @Test
    fun `so am hoac zero khong duoc tinh`() {
        BattlePass.apply(seasonJson())
        assertEquals(0L, BattlePass.addXp(-5))
        assertEquals(0L, BattlePass.addXp(0))
    }

    @Test
    fun `catch up nhan doi xp sau nua mua`() {
        BattlePass.apply(seasonJson()) // x2 sau 50% mùa, đồng hồ mặc định ở MID (đúng 50%)
        assertEquals("đúng mốc 50% đã vào catch-up", 200L, BattlePass.addXp(100))
        BattlePass.clock = { START + 1 }
        BattlePass.clear(); BattlePass.apply(seasonJson()); BattlePass.clock = { START + 1 }
        assertEquals("đầu mùa chưa catch-up", 100L, BattlePass.addXp(100))
    }

    @Test
    fun `catch up tat thi xp nguyen ban`() {
        BattlePass.apply(seasonJson(catchUp = """"catch_up": {"enabled": false}"""))
        assertEquals(100L, BattlePass.addXp(100))
    }

    // ═══ Premium ═══

    @Test
    fun `premium gan voi entitlement cua dung mua nay`() {
        BattlePass.apply(seasonJson())
        assertFalse(BattlePass.isPremiumActive())
        Entitlements.grant("bp_s1_premium")
        assertTrue(BattlePass.isPremiumActive())
        // Mùa mới với product id khác — entitlement mùa cũ không còn giá trị.
        BattlePass.apply(seasonJson(season = "season_2", premium = "bp_s2_premium"))
        assertFalse("entitlement mùa cũ không mở premium mùa mới", BattlePass.isPremiumActive())
    }

    // ═══ Claim ═══

    @Test
    fun `claim free thanh cong tra danh sach reward`() {
        BattlePass.apply(seasonJson(catchUp = """"catch_up": {"enabled": false}"""))
        val r = BattlePass.claim(1, BattlePass.Track.FREE)
        assertTrue(r.claimed)
        assertEquals(1, r.rewards.size)
        assertEquals("f1", r.rewards[0].id)
        assertEquals(10L, r.rewards[0].amount)
    }

    @Test
    fun `claim bac chua dat bi tu choi`() {
        BattlePass.apply(seasonJson())
        val r = BattlePass.claim(3, BattlePass.Track.FREE) // cần 300 XP
        assertFalse(r.claimed)
        assertTrue(r.report.contains("chưa đạt"))
    }

    @Test
    fun `claim hai lan bi chan`() {
        BattlePass.apply(seasonJson())
        assertTrue(BattlePass.claim(1, BattlePass.Track.FREE).claimed)
        val r = BattlePass.claim(1, BattlePass.Track.FREE)
        assertFalse(r.claimed)
        assertTrue(r.report.contains("đã claim"))
    }

    @Test
    fun `claim premium can entitlement nhung free thi khong`() {
        BattlePass.apply(seasonJson())
        val denied = BattlePass.claim(1, BattlePass.Track.PREMIUM)
        assertFalse(denied.claimed)
        assertTrue(denied.report.contains("chưa sở hữu premium"))
        Entitlements.grant("bp_s1_premium")
        assertTrue(BattlePass.claim(1, BattlePass.Track.PREMIUM).claimed)
        // Track free của cùng bậc vẫn claim được độc lập.
        assertTrue(BattlePass.claim(1, BattlePass.Track.FREE).claimed)
    }

    @Test
    fun `het mua khong con claim duoc`() {
        BattlePass.apply(seasonJson())
        BattlePass.clock = { END + 1 }
        val r = BattlePass.claim(1, BattlePass.Track.FREE)
        assertFalse(r.claimed)
        assertTrue(r.report.contains("không đang chạy"))
    }

    @Test
    fun `claim bac khong co trong cau hinh bi tu choi`() {
        BattlePass.apply(seasonJson())
        assertFalse(BattlePass.claim(99, BattlePass.Track.FREE).claimed)
    }

    // ═══ Claimable ═══

    @Test
    fun `claimable chi liet ke bac da dat chua claim co thuong`() {
        BattlePass.apply(seasonJson(catchUp = """"catch_up": {"enabled": false}"""))
        BattlePass.addXp(150) // đạt bậc 1 và 2
        // Bậc 1: free có thưởng, premium có thưởng nhưng chưa mua pass → chỉ FREE.
        // Bậc 2: free có thưởng; premium rỗng → không liệt kê.
        val list = BattlePass.claimable()
        assertTrue(1 to BattlePass.Track.FREE in list)
        assertTrue(2 to BattlePass.Track.FREE in list)
        assertTrue(1 to BattlePass.Track.PREMIUM !in list)
        // Mua pass → premium bậc 1 xuất hiện.
        Entitlements.grant("bp_s1_premium")
        assertTrue(1 to BattlePass.Track.PREMIUM in BattlePass.claimable())
        // Claim xong thì biến mất.
        BattlePass.claim(1, BattlePass.Track.FREE)
        assertTrue(1 to BattlePass.Track.FREE !in BattlePass.claimable())
    }

    // ═══ Đổi mùa ═══

    @Test
    fun `mua moi dat lai tien do`() {
        BattlePass.apply(seasonJson(catchUp = """"catch_up": {"enabled": false}"""))
        BattlePass.addXp(150)
        assertTrue(BattlePass.claim(1, BattlePass.Track.FREE).claimed)
        BattlePass.apply(seasonJson(season = "season_2"))
        assertEquals("mùa mới là cuộc đua mới", 0L, BattlePass.xpTotal())
        assertEquals(1, BattlePass.currentTier()) // bậc 1 ngưỡng 0 XP luôn đạt
        val r = BattlePass.claim(1, BattlePass.Track.FREE)
        assertTrue("lịch sử claim mùa cũ không chặn mùa mới", r.claimed)
    }

    @Test
    fun `nap lai cung mua giu nguyen tien do`() {
        BattlePass.apply(seasonJson(catchUp = """"catch_up": {"enabled": false}"""))
        BattlePass.addXp(150)
        BattlePass.apply(seasonJson(catchUp = """"catch_up": {"enabled": false}"""))
        assertEquals(150L, BattlePass.xpTotal())
    }

    // ═══ Reset ═══

    @Test
    fun `clear xoa het trang thai`() {
        BattlePass.apply(seasonJson())
        BattlePass.addXp(50)
        BattlePass.clear()
        assertEquals("chưa nạp", BattlePass.activeVersion())
        assertEquals(0L, BattlePass.xpTotal())
        assertEquals(BattlePass.SeasonPhase.NO_SEASON, BattlePass.phase())
    }
}
