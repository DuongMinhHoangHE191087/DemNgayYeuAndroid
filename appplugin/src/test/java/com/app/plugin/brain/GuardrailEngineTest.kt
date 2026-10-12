package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardrailEngineTest {

    private fun state(
        id: String,
        failStreak: Int = 0,
        churn: Double = 0.0,
        ads24h: Int = 0,
        purchaseUsd: Double = 0.0,
        daysSincePurchase: Int = -1,
        // Suy ra từ purchaseUsd theo mặc định: "đã trả 4.99 qua 0 lượt mua" là trạng thái
        // không thể tồn tại, và guardrail nhận diện payer bằng SỐ LƯỢT (tổng USD có thể
        // bằng 0 khi chưa biết tỉ giá tiền tệ địa phương — xem PayerProtectionTest).
        purchaseCount: Int = if (purchaseUsd > 0.0) 1 else 0
    ) = PlayerState(
        playerId = id,
        failStreakCurrent = failStreak,
        churnRisk = churn,
        adImpressions24h = ads24h,
        purchaseCountLifetime = purchaseCount,
        purchaseValueLifetimeUsd = purchaseUsd,
        daysSinceLastPurchase = daysSincePurchase
    )

    private val interstitial = ActionCandidate(ActionMode.INTERSTITIAL, "inter")
    private val rewarded = ActionCandidate(ActionMode.REWARDED_AD, "rew")
    private val noAction = ActionCandidate(ActionMode.NO_ACTION, "none")

    // ---------- Tính chất an toàn quan trọng nhất ----------

    @Test
    fun `tran kich ban chi duoc siet chat hon khong bao gio noi long`() {
        val g = GuardrailEngine()
        g.maxInterstitialPerSession = 3

        // AD_FORWARD cho phép 6 — nhưng trần tuyệt đối là 3, nên hiệu lực phải là 3.
        assertEquals(
            "kich ban KHONG duoc vuot tran tuyet doi",
            3, g.effectiveInterstitialCap(PersonalizationScenario.AD_FORWARD)
        )
        // RETENTION_FIRST cho phép 1 — chặt hơn trần, nên hiệu lực là 1.
        assertEquals(
            "kich ban chat hon thi duoc ap dung",
            1, g.effectiveInterstitialCap(PersonalizationScenario.RETENTION_FIRST)
        )
        // Không có kịch bản -> dùng trần tuyệt đối.
        assertEquals(3, g.effectiveInterstitialCap(null))
    }

    @Test
    fun `kich ban siet chat lam interstitial bi chan som hon`() {
        val g = GuardrailEngine()
        g.maxInterstitialPerSession = 6
        val s = state("p_scen")

        // Với RETENTION_FIRST (trần 1), đã chiếu 1 quảng cáo -> phải bị chặn.
        val blocked = g.evaluate(interstitial, s, 1, 999, PersonalizationScenario.RETENTION_FIRST)
        assertNotNull("RETENTION_FIRST phai chan o quang cao thu 2", blocked)
        assertEquals("AD_PRESSURE_SESSION_CAP", blocked!!.code)

        // Cùng tình huống nhưng AD_FORWARD (trần min(6,6)=6) -> chưa chặn.
        assertNull(g.evaluate(interstitial, s, 1, 999, PersonalizationScenario.AD_FORWARD))
    }

    // ---------- Phân loại HARD vs RESOURCE ----------

    @Test
    fun `rang buoc dao duc duoc phan loai HARD`() {
        val g = GuardrailEngine()
        // failStreak = 0 nghĩa là chưa thua thật -> không được mời rewarded.
        val v = g.evaluate(rewarded, state("p_eth", failStreak = 0), 0, 999)
        assertNotNull(v)
        assertEquals("ETH_MANUFACTURED_FAIL", v!!.code)
        assertEquals(GuardrailKind.HARD, v.kind)
    }

    @Test
    fun `rang buoc ngan sach duoc phan loai RESOURCE`() {
        val g = GuardrailEngine()
        g.maxInterstitialPerSession = 2
        val v = g.evaluate(interstitial, state("p_res"), 2, 999)
        assertNotNull(v)
        assertEquals(GuardrailKind.RESOURCE, v!!.kind)
    }

    @Test
    fun `rewarded duoc phep sau khi thua that`() {
        val g = GuardrailEngine()
        assertNull(
            "thua that (streak>0) thi rewarded la hop le",
            g.evaluate(rewarded, state("p_ok", failStreak = 2), 0, 999)
        )
    }

    // ---------- Từng luật tài nguyên ----------

    @Test
    fun `cooldown chan interstitial qua gan nhau`() {
        val g = GuardrailEngine()
        g.minMinutesBetweenInterstitial = 5
        val v = g.evaluate(interstitial, state("p_cd"), 0, minutesSinceLastInterstitial = 1)
        assertEquals("AD_PRESSURE_COOLDOWN", v?.code)
    }

    @Test
    fun `tran 24h chan khi vuot nguong`() {
        val g = GuardrailEngine()
        g.maxAds24h = 10
        val v = g.evaluate(interstitial, state("p_24h", ads24h = 10), 0, 999)
        assertEquals("AD_PRESSURE_24H_CAP", v?.code)
    }

    @Test
    fun `payer vua mua duoc bao ve khoi ad pressure`() {
        val g = GuardrailEngine()
        val s = state("p_payer", purchaseUsd = 4.99, daysSincePurchase = 1)
        assertEquals("PAYER_PROTECTION_RECENT", g.evaluate(interstitial, s, 0, 999)?.code)
        // Bảo vệ áp dụng cho cả rewarded, không chỉ interstitial.
        val sr = state("p_payer2", failStreak = 3, purchaseUsd = 4.99, daysSincePurchase = 0)
        assertEquals("PAYER_PROTECTION_RECENT", g.evaluate(rewarded, sr, 0, 999)?.code)
    }

    @Test
    fun `payer mua lau roi thi khong con duoc bao ve`() {
        val g = GuardrailEngine()
        val s = state("p_old", purchaseUsd = 4.99, daysSincePurchase = 30)
        assertNull(g.evaluate(interstitial, s, 0, 999))
    }

    @Test
    fun `churn risk cao chan interstitial`() {
        val g = GuardrailEngine()
        g.churnRiskProtectionThreshold = 0.7
        assertEquals(
            "CHURN_PROTECTION",
            g.evaluate(interstitial, state("p_churn", churn = 0.95), 0, 999)?.code
        )
    }

    // ---------- NO_ACTION không bao giờ bị chặn ----------

    @Test
    fun `NO_ACTION luon di qua moi guardrail`() {
        val g = GuardrailEngine()
        g.maxInterstitialPerSession = 0
        g.maxAds24h = 0
        val s = state("p_noact", churn = 1.0, ads24h = 999, purchaseUsd = 100.0, daysSincePurchase = 0)
        assertNull("NO_ACTION la fallback an toan, khong duoc chan", g.evaluate(noAction, s, 999, 0))
    }

    // ---------- Đếm số lần đã ngăn chặn (đầu vào ComfortIndex) ----------

    @Test
    fun `preventedCount tang moi lan chan thanh cong`() {
        val g = GuardrailEngine()
        g.maxInterstitialPerSession = 1
        val s = state("p_count")
        assertEquals(0, g.preventedCount("AD_PRESSURE_SESSION_CAP"))

        repeat(3) { g.evaluate(interstitial, s, 5, 999) }
        assertEquals(3, g.preventedCount("AD_PRESSURE_SESSION_CAP"))
        assertTrue(g.allPreventedCounts().containsKey("AD_PRESSURE_SESSION_CAP"))
    }

    @Test
    fun `khong chan thi khong tang bo dem`() {
        val g = GuardrailEngine()
        g.evaluate(noAction, state("p_nocount"), 0, 999)
        assertTrue("khong duoc dem khi khong co vi pham", g.allPreventedCounts().isEmpty())
    }

    // ---------- Chống click-fraud ----------

    @Test
    fun `click fraud khoa moi loai ad nhung khong khoa NO_ACTION`() {
        val playerId = "p_fraud_${System.nanoTime()}"
        ClickFraudGuard.maxClicksPerWindow = 2
        repeat(5) { ClickFraudGuard.registerClick(playerId) }

        val g = GuardrailEngine()
        val s = state(playerId, failStreak = 3)
        assertEquals("CLICK_FRAUD_LOCKOUT", g.evaluate(interstitial, s, 0, 999)?.code)
        assertEquals("CLICK_FRAUD_LOCKOUT", g.evaluate(rewarded, s, 0, 999)?.code)
        assertNull("NO_ACTION khong bi khoa boi click-fraud", g.evaluate(noAction, s, 0, 999))
    }

    @Test
    fun `click duoi nguong thi khong khoa`() {
        val playerId = "p_fraud_ok_${System.nanoTime()}"
        ClickFraudGuard.maxClicksPerWindow = 6
        repeat(2) { ClickFraudGuard.registerClick(playerId) }
        val g = GuardrailEngine()
        assertNull(g.evaluate(interstitial, state(playerId), 0, 999))
    }
}
