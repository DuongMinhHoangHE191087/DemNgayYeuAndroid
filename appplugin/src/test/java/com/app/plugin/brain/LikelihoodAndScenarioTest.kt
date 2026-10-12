package com.app.plugin.brain

import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LikelihoodAndScenarioTest {

    @Before
    fun setUp() {
        // #7 (DOC/66): UNKNOWN không còn được profiling/cá nhân hoá; test logic brain như người đã đồng ý.
        ConsentState.resetForTest()
        ConsentState.setTier(ConsentTier.GRANTED)
    }

    @After
    fun tearDown() = ConsentState.resetForTest()

    // ================= Support gating — tính chất dễ làm sai nhất =================

    @Test
    fun `chua co du lieu thi khong sinh likelihood`() {
        val fresh = PlayerState(playerId = "new_user")
        assertTrue(
            "user hoan toan moi khong duoc suy dien gi",
            LikelihoodEstimator.estimate(fresh).isEmpty()
        )
    }

    @Test
    fun `chua tung duoc moi rewarded thi KHONG ket luan la ghet quang cao`() {
        // Đây là lỗi kinh điển: rewardedAcceptRateRecent = 0.0 ở người chưa từng được mời
        // KHÔNG có nghĩa "ghét quảng cáo" — nó có nghĩa "chưa biết".
        val neverOffered = PlayerState(
            playerId = "u1",
            sessionsLifetime = 10,
            rewardedAcceptRateRecent = 0.0,
            rewardedOpportunitiesRecent = 0
        )
        val offeredAndRefused = neverOffered.copy(
            playerId = "u2",
            rewardedOpportunitiesRecent = 10
        )

        val l1 = LikelihoodEstimator.estimate(neverOffered)
        val l2 = LikelihoodEstimator.estimate(offeredAndRefused)

        assertTrue("ca hai deu co du lieu session nen khong rong", l1.isNotEmpty() && l2.isNotEmpty())
        assertNotEquals(
            "hai truong hop nay PHAI cho likelihood khac nhau",
            l1[LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE],
            l2[LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE]
        )
    }

    @Test
    fun `activeSignals bao ro khi chua du du lieu`() {
        val fresh = PlayerState(playerId = "u")
        val signals = LikelihoodEstimator.activeSignals(fresh)
        assertTrue(
            "phai noi ro la chua du du lieu, thay vi im lang",
            signals.any { it.contains("chưa đủ dữ liệu") }
        )
    }

    @Test
    fun `activeSignals liet ke rewarded khi da du support`() {
        val s = PlayerState(
            playerId = "u",
            sessionsLifetime = 5,
            rewardedOpportunitiesRecent = 5,
            rewardedAcceptRateRecent = 0.8
        )
        assertTrue(LikelihoodEstimator.activeSignals(s).any { it.startsWith("rewardedAccept") })
    }

    // ================= Likelihood luôn hợp lệ =================

    @Test
    fun `likelihood luon duong va co san cho moi type`() {
        val s = PlayerState(playerId = "u", sessionsLifetime = 8, failRateLast30m = 0.5)
        val l = LikelihoodEstimator.estimate(s)
        assertEquals(LatentPlayerType.values().size, l.size)
        l.values.forEach {
            assertTrue("likelihood phai duong (floor), thuc te $it", it > 0.0)
            assertFalse("khong duoc NaN", it.isNaN())
        }
    }

    // ================= Khả năng phân biệt =================

    @Test
    fun `nguoi thua nhieu va nghi lau nghieng ve nhom churn cao`() {
        val struggling = PlayerState(
            playerId = "u",
            sessionsLifetime = 10,
            failRateLast30m = 0.85,
            daysSinceLastSession = 9,
            purchaseCountLifetime = 0
        )
        val belief = BeliefState("u")
        repeat(5) { belief.update(LikelihoodEstimator.estimate(struggling)) }
        assertEquals(LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK, belief.mostLikelyType())
    }

    @Test
    fun `nguoi gioi nhan rewarded nhieu nghieng ve nhom chiu quang cao`() {
        val adTolerant = PlayerState(
            playerId = "u",
            sessionsLifetime = 10,
            failRateLast30m = 0.12,
            rewardedOpportunitiesRecent = 12,
            rewardedAcceptRateRecent = 0.85,
            daysSinceLastSession = 0,
            purchaseCountLifetime = 0
        )
        val belief = BeliefState("u")
        repeat(5) { belief.update(LikelihoodEstimator.estimate(adTolerant)) }
        assertEquals(LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE, belief.mostLikelyType())
    }

    @Test
    fun `nguoi gioi tu choi rewarded nhung co mua nghieng ve nhom can bao ve`() {
        val payer = PlayerState(
            playerId = "u",
            sessionsLifetime = 10,
            failRateLast30m = 0.12,
            rewardedOpportunitiesRecent = 12,
            rewardedAcceptRateRecent = 0.10,
            daysSinceLastSession = 0,
            purchaseCountLifetime = 4
        )
        val belief = BeliefState("u")
        repeat(5) { belief.update(LikelihoodEstimator.estimate(payer)) }
        assertEquals(LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE, belief.mostLikelyType())
    }

    @Test
    fun `damping ngan mot quan sat don le lat nguoc posterior`() {
        val extreme = PlayerState(
            playerId = "u",
            sessionsLifetime = 10,
            failRateLast30m = 0.95,
            daysSinceLastSession = 10
        )
        val belief = BeliefState("u")
        belief.update(LikelihoodEstimator.estimate(extreme))
        val afterOne = belief.confidence()
        assertTrue(
            "mot quan sat khong duoc dua do tin cay len qua cao, thuc te $afterOne",
            afterOne < 0.70
        )
        // Nhưng cũng phải THỰC SỰ phản ứng — damping không có nghĩa là bất động.
        assertTrue("van phai dich chuyen khoi prior 0.25, thuc te $afterOne", afterOne > 0.40)
    }

    // ================= PersonalizationScenario =================

    @Test
    fun `do tin cay thap thi dung kich ban trung tinh thay vi doan bua`() {
        val belief = BeliefState("u") // prior đồng đều -> confidence = 0.25
        assertTrue(belief.confidence() < PersonalizationScenario.MIN_CONFIDENCE)
        assertEquals(PersonalizationScenario.BALANCED_HYBRID, PersonalizationScenario.resolve(belief))
    }

    @Test
    fun `do tin cay du cao thi ap dung kich ban chuyen biet`() {
        val belief = BeliefState("u")
        belief.posterior[LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK] = 0.70
        belief.posterior[LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE] = 0.10
        belief.posterior[LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE] = 0.10
        belief.posterior[LatentPlayerType.CASUAL_STEADY] = 0.10
        assertEquals(PersonalizationScenario.RETENTION_FIRST, PersonalizationScenario.resolve(belief))
    }

    @Test
    fun `moi segment anh xa sang dung mot kich ban`() {
        val mapped = LatentPlayerType.values().map { PersonalizationScenario.forType(it) }
        assertEquals("anh xa phai la 1-1", mapped.size, mapped.toSet().size)
    }

    @Test
    fun `kich ban giu chan co ap luc quang cao thap nhat`() {
        val retention = PersonalizationScenario.RETENTION_FIRST
        val adForward = PersonalizationScenario.AD_FORWARD
        assertTrue(retention.interstitialCapPerSession < adForward.interstitialCapPerSession)
        assertTrue(retention.freeValueGenerosity > adForward.freeValueGenerosity)
    }

    @Test
    fun `kich ban bao ve va chuyen doi uu tien IAP hon quang cao`() {
        val protect = PersonalizationScenario.PROTECT_AND_CONVERT
        assertTrue(protect.iapOfferPriority > protect.rewardedAggressiveness)
        assertTrue(
            protect.interstitialCapPerSession < PersonalizationScenario.AD_FORWARD.interstitialCapPerSession
        )
    }
}
