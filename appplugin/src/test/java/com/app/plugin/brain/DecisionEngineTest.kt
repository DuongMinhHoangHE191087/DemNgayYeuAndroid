package com.app.plugin.brain

import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test tích hợp cho toàn bộ vòng DECIDE → guardrail → bandit → FEEDBACK.
 *
 * Chạy được trên JVM thuần vì `BrainStorage` đi qua `PreferenceHelper`, và khi chưa gọi
 * `init(context)` thì `preferences` là null nên mọi thao tác lưu trữ tự thoái hoá thành no-op
 * (đúng thiết kế fail-safe). Nghĩa là các test này kiểm tra logic quyết định, KHÔNG kiểm tra
 * persistence — phần đó cần instrumented test trên thiết bị thật.
 */
class DecisionEngineTest {
    @get:Rule val license = LicenseTestRule()

    @Before
    fun setUp() {
        // #7 (DOC/66): UNKNOWN không còn được profiling/cá nhân hoá; test logic brain như người đã đồng ý.
        ConsentState.resetForTest()
        ConsentState.setTier(ConsentTier.GRANTED)
    }

    @After
    fun tearDown() = ConsentState.resetForTest()

    private fun engine() = DecisionEngine(policyVersion = "test_v1")

    private fun candidates() = listOf(
        ActionCandidate(ActionMode.NO_ACTION, "no_action"),
        ActionCandidate(ActionMode.INTERSTITIAL, "inter")
    )

    private fun request(id: String, dp: DecisionPoint = DecisionPoint.INTERSTITIAL_OPPORTUNITY) =
        DecisionRequest(id, dp)

    // ---------- Bất biến cốt lõi ----------

    @Test
    fun `bat buoc phai co NO_ACTION trong candidate set`() {
        val e = engine()
        var threw = false
        try {
            e.decide(
                request("p1"),
                PlayerState("p1"),
                listOf(ActionCandidate(ActionMode.INTERSTITIAL, "inter"))
            )
        } catch (ex: IllegalArgumentException) {
            threw = true
        }
        assertTrue("NO_ACTION phai luon la lua chon hop le (doc 24 muc M)", threw)
    }

    @Test
    fun `candidate rong thi nem loi`() {
        val e = engine()
        var threw = false
        try {
            e.decide(request("p1"), PlayerState("p1"), emptyList())
        } catch (ex: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `propensity luon hop le va action duoc chon nam trong tap du dieu kien`() {
        val e = engine()
        repeat(15) { i ->
            val d = e.decide(request("p_prop$i"), PlayerState("p_prop$i"), candidates())
            assertTrue("propensity=${d.chosenActionProbability}", d.chosenActionProbability in 0.0..1.0)
            assertTrue(
                "action duoc chon phai nam trong eligibleActionIds",
                d.chosenAction.actionId in d.eligibleActionIds
            )
        }
    }

    // ---------- Guardrail lọc đúng và log đủ cho OPE ----------

    @Test
    fun `action bi guardrail chan khong xuat hien trong tap du dieu kien`() {
        val e = engine()
        // failStreak = 0 -> rewarded bị chặn bởi ETH_MANUFACTURED_FAIL.
        val cands = listOf(
            ActionCandidate(ActionMode.NO_ACTION, "no_action"),
            ActionCandidate(ActionMode.REWARDED_AD, "rew")
        )
        val d = e.decide(request("p_block"), PlayerState("p_block", failStreakCurrent = 0), cands)

        assertTrue("phai ghi lai ma guardrail", d.guardrailsApplied.contains("ETH_MANUFACTURED_FAIL"))
        assertFalse("arm bi chan khong duoc nam trong tap du dieu kien", d.eligibleActionIds.contains("rew"))
        assertEquals(ActionMode.NO_ACTION, d.chosenAction.mode)
    }

    @Test
    fun `khi moi action that deu bi chan thi rot ve NO_ACTION`() {
        val e = engine()
        e.guardrails.maxInterstitialPerSession = 0
        val d = e.decide(
            request("p_all_blocked"), PlayerState("p_all_blocked"), candidates(),
            adsShownThisSession = 99
        )
        assertEquals(ActionMode.NO_ACTION, d.chosenAction.mode)
        assertEquals(listOf("no_action"), d.eligibleActionIds)
    }

    @Test
    fun `decision log ghi day du truong can cho OPE`() {
        val e = engine()
        val d = e.decide(request("p_log"), PlayerState("p_log"), candidates())
        assertNotNull(d.decisionId)
        assertTrue(d.decisionId.isNotBlank())
        assertEquals("test_v1", d.policyVersion)
        assertTrue("propensity la truong bat buoc cho OPE", d.chosenActionProbability > 0.0)
        assertTrue("phai ghi tap du dieu kien de tai lap quyet dinh", d.eligibleActionIds.isNotEmpty())
        assertNotNull("phai ghi segment tai thoi diem quyet dinh", d.segment)
        assertNotNull("phai ghi kich ban dang ap dung", d.scenario)
    }

    // ---------- observe(): belief chuyển động ----------

    @Test
    fun `observe tra ve false khi chua du du lieu`() {
        val e = engine()
        assertFalse(
            "user moi tinh khong the suy dien segment",
            e.observe(PlayerState("p_new"))
        )
        assertTrue(e.beliefFor("p_new").isStillPrior())
    }

    @Test
    fun `observe tra ve true va lam belief roi khoi prior khi du du lieu`() {
        val e = engine()
        val s = PlayerState(
            playerId = "p_obs",
            sessionsLifetime = 6,
            failRateLast30m = 0.8,
            daysSinceLastSession = 8
        )
        assertTrue(e.observe(s))
        assertFalse("belief phai roi khoi prior", e.beliefFor("p_obs").isStillPrior())
    }

    @Test
    fun `quan sat lap lai lam kich ban chuyen tu trung tinh sang chuyen biet`() {
        val e = engine()
        val id = "p_scenario"
        // Ban đầu chưa biết gì -> kịch bản trung tính.
        assertEquals(PersonalizationScenario.BALANCED_HYBRID, e.scenarioFor(id))

        val struggling = PlayerState(
            playerId = id,
            sessionsLifetime = 10,
            failRateLast30m = 0.85,
            daysSinceLastSession = 9
        )
        repeat(6) { e.observe(struggling) }

        assertEquals(
            "sau nhieu quan sat that bai, phai chuyen sang giu chan",
            PersonalizationScenario.RETENTION_FIRST, e.scenarioFor(id)
        )
    }

    // ---------- Tính chất an toàn end-to-end ----------

    @Test
    fun `kich ban giu chan that su siet tran quang cao trong luong quyet dinh`() {
        val e = engine()
        e.guardrails.maxInterstitialPerSession = 6
        val id = "p_cap"
        val struggling = PlayerState(
            playerId = id,
            sessionsLifetime = 10,
            failRateLast30m = 0.85,
            daysSinceLastSession = 9
        )
        repeat(6) { e.observe(struggling) }
        assertEquals(PersonalizationScenario.RETENTION_FIRST, e.scenarioFor(id))

        // RETENTION_FIRST có trần 1: đã chiếu 1 quảng cáo thì phải bị chặn,
        // dù trần tuyệt đối là 6.
        val d = e.decide(request(id), struggling, candidates(), adsShownThisSession = 1)
        assertTrue(
            "kich ban phai siet tran xuong 1",
            d.guardrailsApplied.contains("AD_PRESSURE_SESSION_CAP")
        )
        assertEquals(ActionMode.NO_ACTION, d.chosenAction.mode)
    }

    @Test
    fun `kich ban khong bao gio noi long vuot tran tuyet doi`() {
        val e = engine()
        e.guardrails.maxInterstitialPerSession = 2   // trần tuyệt đối chặt
        val id = "p_notloosen"
        val adTolerant = PlayerState(
            playerId = id,
            sessionsLifetime = 10,
            failRateLast30m = 0.12,
            rewardedOpportunitiesRecent = 12,
            rewardedAcceptRateRecent = 0.85
        )
        repeat(6) { e.observe(adTolerant) }
        assertEquals(PersonalizationScenario.AD_FORWARD, e.scenarioFor(id))

        // AD_FORWARD muốn trần 6, nhưng trần tuyệt đối là 2 -> vẫn bị chặn ở lần thứ 3.
        val d = e.decide(request(id), adTolerant, candidates(), adsShownThisSession = 2)
        assertTrue(
            "tran tuyet doi phai thang tran cua kich ban",
            d.guardrailsApplied.contains("AD_PRESSURE_SESSION_CAP")
        )
    }

    // ---------- FEEDBACK ----------

    @Test
    fun `feedback phan bo bang chung cho moi segment theo trong so belief`() {
        val e = engine()
        val id = "p_fb"
        val s = PlayerState(playerId = id)
        val d = e.decide(request(id), s, candidates())
        e.feedback(request(id), d, outcomeReward01 = 1.0)

        // Belief còn ở prior đồng đều 0.25 -> cả 4 segment cùng nhận 0.25 quan sát.
        var total = 0.0
        LatentPlayerType.values().forEach { type ->
            val key = "${DecisionPoint.INTERSTITIAL_OPPORTUNITY}_$type"
            total += e.bandit.observationCount(key, d.chosenAction.actionId)
        }
        assertEquals("tong bang chung phai bang dung 1 quan sat", 1.0, total, 1e-9)
    }

    @Test
    fun `resetPlayer xoa ca belief trong bo nho`() {
        val e = engine()
        val id = "p_reset"
        val s = PlayerState(playerId = id, sessionsLifetime = 8, failRateLast30m = 0.9, daysSinceLastSession = 9)
        repeat(4) { e.observe(s) }
        assertFalse(e.beliefFor(id).isStillPrior())

        e.resetPlayer(id)
        assertTrue("sau reset phai quay ve prior", e.beliefFor(id).isStillPrior())
        assertEquals(PersonalizationScenario.BALANCED_HYBRID, e.scenarioFor(id))
    }
}
