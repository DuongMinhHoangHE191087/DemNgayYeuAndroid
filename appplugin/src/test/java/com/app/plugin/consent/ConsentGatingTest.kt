package com.app.plugin.consent

import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.DecisionEngine
import com.app.plugin.brain.HoldoutManager
import com.app.plugin.brain.PersonalizationScenario
import com.app.plugin.brain.PlayerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Consent chi phối brain như thế nào — doc 13 mục E và F.
 *
 * Ranh giới cần đúng chính xác: GDPR coi personalization là **profiling**, nên việc TÍCH LUỸ
 * hồ sơ hành vi cần sự đồng ý. Guardrail thì ngược lại — chúng chỉ bảo vệ người chơi, và
 * không có diễn giải nào khiến việc bảo vệ người dùng cần sự cho phép của chính họ.
 */
class ConsentGatingTest {

    @Before
    fun setUp() {
        ConsentState.resetForTest()
        BrainPolicy.clear()
        HoldoutManager.holdoutFraction = 0.0
        AdsBrain.enabled = false
        AdsBrain.rolloutFraction = 1.0
    }

    @After
    fun tearDown() {
        ConsentState.resetForTest()
        BrainPolicy.clear()
        HoldoutManager.holdoutFraction = 0.05
        AdsBrain.enabled = false
    }

    private fun learner(id: String) = PlayerState(
        id, sessionsLifetime = 3, failStreakCurrent = 9, failRateLast30m = 0.9,
        rewardedOpportunitiesRecent = 8, rewardedAcceptRateRecent = 0.9
    )

    @Test
    fun `tu choi consent thi KHONG tich luy ho so hanh vi`() {
        val e = DecisionEngine()
        val id = "p_denied_${System.nanoTime()}"
        ConsentState.setTier(ConsentTier.DENIED)
        repeat(10) { e.observe(learner(id)) }
        assertTrue(
            "profiling can consent (doc 13 muc E) — belief phai dung yen",
            e.beliefFor(id).isStillPrior()
        )
    }

    @Test
    fun `dong y consent thi ho so dich chuyen binh thuong`() {
        val e = DecisionEngine()
        val id = "p_granted_${System.nanoTime()}"
        ConsentState.setTier(ConsentTier.GRANTED)
        repeat(10) { e.observe(learner(id)) }
        assertFalse(e.beliefFor(id).isStillPrior())
    }

    @Test
    fun `chua co cau tra loi consent thi KHONG tich luy ho so hanh vi`() {
        // #7 (DOC/66): UNKNOWN không bao giờ là đồng ý (GDPR Recital 32). Người ngoài EEA gần như không mất gì:
        // UMP trả NOT_REQUIRED → GRANTED sau vài trăm ms lần mở đầu, và tầng được lưu qua phiên.
        val e = DecisionEngine()
        val id = "p_unknown_${System.nanoTime()}"
        assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
        repeat(10) { e.observe(learner(id)) }
        assertTrue(e.beliefFor(id).isStillPrior())
    }

    @Test
    fun `tu choi consent thi kich ban ve trung tinh`() {
        val e = DecisionEngine()
        val id = "p_scen_${System.nanoTime()}"
        ConsentState.setTier(ConsentTier.GRANTED)
        repeat(10) { e.observe(learner(id)) }
        val personalized = e.scenarioFor(id)

        ConsentState.setTier(ConsentTier.DENIED)
        assertEquals(
            "bi tu choi thi khong duoc ca nhan hoa nua",
            PersonalizationScenario.BALANCED_HYBRID, e.scenarioFor(id)
        )
        // Hồ sơ cũ vẫn còn trên đĩa; điều bị chặn là VIỆC DÙNG nó, và đó mới là điểm đúng.
        ConsentState.setTier(ConsentTier.GRANTED)
        assertEquals("cho phep lai thi khoi phuc", personalized, e.scenarioFor(id))
    }

    @Test
    fun `guardrail van chan quang cao qua day ke ca khi bi tu choi consent`() {
        // Đây là tính chất quan trọng nhất của cả file. Nếu consent tắt luôn guardrail thì
        // người từ chối theo dõi lại là người bị dội quảng cáo nhiều nhất — kết quả ngược
        // hoàn toàn với mục đích của quy định.
        ConsentState.setTier(ConsentTier.DENIED)
        val e = DecisionEngine()
        e.guardrails.maxInterstitialPerSession = 2
        val v = e.guardrails.evaluate(
            com.app.plugin.brain.ActionCandidate(
                com.app.plugin.brain.ActionMode.INTERSTITIAL, "inter"
            ),
            PlayerState("p_g_${System.nanoTime()}"), adsShownThisSession = 5,
            minutesSinceLastInterstitial = 999
        )
        assertEquals("AD_PRESSURE_SESSION_CAP", v?.code)
    }

    @Test
    fun `doi consent lam ho so ngung dich chuyen ngay lap tuc`() {
        val e = DecisionEngine()
        val id = "p_switch_${System.nanoTime()}"
        ConsentState.setTier(ConsentTier.GRANTED)
        repeat(6) { e.observe(learner(id)) }
        val after = e.beliefFor(id).posterior.toMap()

        ConsentState.setTier(ConsentTier.DENIED)
        repeat(20) { e.observe(learner(id)) }
        assertEquals("rut lai dong y thi ho so phai dong bang", after, e.beliefFor(id).posterior.toMap())
    }
}
