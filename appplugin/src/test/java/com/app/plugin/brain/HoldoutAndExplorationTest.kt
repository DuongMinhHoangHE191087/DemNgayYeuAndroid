package com.app.plugin.brain

import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class HoldoutAndExplorationTest {
    @get:Rule val license = LicenseTestRule()

    private val defaultFraction = HoldoutManager.holdoutFraction
    private val defaultSalt = HoldoutManager.salt

    @Before
    fun setUp() {
        HoldoutManager.holdoutFraction = defaultFraction
        HoldoutManager.salt = defaultSalt
        BrainPolicy.clear()
        // #7 (DOC/66): UNKNOWN không còn được profiling/cá nhân hoá; test logic brain như người đã đồng ý.
        ConsentState.resetForTest()
        ConsentState.setTier(ConsentTier.GRANTED)
    }

    @After
    fun tearDown() {
        ConsentState.resetForTest()
        HoldoutManager.holdoutFraction = 0.05
        HoldoutManager.salt = "brain_holdout_v1"
        BrainPolicy.clear()
    }

    // ═══ Holdout: tính ổn định là yêu cầu số một ═══

    @Test
    fun `phan nhom on dinh tuyet doi qua nhieu lan goi`() {
        val ids = (1..200).map { "player_$it" }
        val first = ids.associateWith { HoldoutManager.isHoldout(it) }
        repeat(5) {
            for (id in ids) {
                assertEquals(
                    "player $id doi nhom giua cac lan goi -> pha huy phep do",
                    first[id], HoldoutManager.isHoldout(id)
                )
            }
        }
    }

    @Test
    fun `bucket luon nam trong khoang hop le`() {
        for (i in 1..2000) {
            val b = HoldoutManager.bucket("user_$i")
            assertTrue("bucket=$b ngoai khoang [0,10000)", b in 0..9999)
        }
    }

    @Test
    fun `ti le holdout thuc te bam sat ti le cau hinh`() {
        HoldoutManager.holdoutFraction = 0.10
        val n = 20_000
        val count = (1..n).count { HoldoutManager.isHoldout("u$it") }
        val actual = count.toDouble() / n
        assertTrue(
            "ti le thuc te $actual lech qua xa muc tieu 0.10",
            abs(actual - 0.10) < 0.02
        )
    }

    @Test
    fun `fraction 0 tat hoan toan holdout`() {
        HoldoutManager.holdoutFraction = 0.0
        assertTrue((1..500).none { HoldoutManager.isHoldout("u$it") })
    }

    @Test
    fun `fraction 1 dua tat ca vao holdout`() {
        HoldoutManager.holdoutFraction = 1.0
        assertTrue((1..500).all { HoldoutManager.isHoldout("u$it") })
    }

    @Test
    fun `doi salt lam phan nhom lai dan so`() {
        HoldoutManager.holdoutFraction = 0.20
        HoldoutManager.salt = "salt_A"
        val a = (1..1000).map { HoldoutManager.isHoldout("u$it") }
        HoldoutManager.salt = "salt_B"
        val b = (1..1000).map { HoldoutManager.isHoldout("u$it") }
        val same = a.zip(b).count { it.first == it.second }
        assertTrue("doi salt phai xao tron dang ke, giong nhau $same/1000", same < 950)
    }

    @Test
    fun `rollout va holdout khong tuong quan voi nhau`() {
        HoldoutManager.holdoutFraction = 0.10
        val ids = (1..5000).map { "u$it" }
        val holdout = ids.filter { HoldoutManager.isHoldout(it) }
        // Trong nhóm holdout, tỉ lệ nằm trong rollout 50% phải vẫn xấp xỉ 50%.
        // Nếu hai cơ chế dùng chung bucket thì tỉ lệ này sẽ là 0% hoặc 100%.
        val inRollout = holdout.count { HoldoutManager.isInRollout(it, 0.5) }
        val ratio = inRollout.toDouble() / holdout.size
        assertTrue(
            "hai co che bi tuong quan (ratio=$ratio) -> khong the chay dong thoi",
            ratio in 0.2..0.8
        )
    }

    // ═══ Holdout trong luồng quyết định ═══

    private fun candidates() = listOf(
        ActionCandidate(ActionMode.NO_ACTION, "no_action"),
        ActionCandidate(ActionMode.INTERSTITIAL, "inter", isBaseline = true)
    )

    /** Tìm một playerId chắc chắn thuộc/không thuộc holdout để test tất định. */
    private fun findPlayer(wantHoldout: Boolean): String =
        (1..5000).map { "p$it" }.first { HoldoutManager.isHoldout(it) == wantHoldout }

    @Test
    fun `holdout luon nhan action baseline va propensity bang 1`() {
        HoldoutManager.holdoutFraction = 0.5
        val e = DecisionEngine()
        val id = findPlayer(true)
        repeat(5) {
            val d = e.decide(DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), PlayerState(id), candidates())
            assertTrue(d.isHoldout)
            assertEquals("holdout phai nhan action baseline", "inter", d.chosenAction.actionId)
            assertEquals("khong co lua chon ngau nhien -> propensity = 1", 1.0, d.chosenActionProbability, 1e-9)
        }
    }

    @Test
    fun `holdout khong duoc personalize du belief da ro rang`() {
        HoldoutManager.holdoutFraction = 0.5
        val e = DecisionEngine()
        val id = findPlayer(true)
        val struggling = PlayerState(
            playerId = id, sessionsLifetime = 10,
            failRateLast30m = 0.85, daysSinceLastSession = 9
        )
        repeat(6) { e.observe(struggling) }
        // Belief vẫn học bình thường (để so sánh sau này), nhưng KHÔNG được áp kịch bản.
        assertEquals(LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK, e.beliefFor(id).mostLikelyType())

        val d = e.decide(DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), struggling, candidates())
        assertEquals(
            "holdout phai giu kich ban trung tinh",
            PersonalizationScenario.BALANCED_HYBRID, d.scenario
        )
    }

    @Test
    fun `outcome cua holdout khong duoc nap vao bandit`() {
        HoldoutManager.holdoutFraction = 0.5
        val e = DecisionEngine()
        val id = findPlayer(true)
        val req = DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        val d = e.decide(req, PlayerState(id), candidates())
        e.feedback(req, d, 1.0)

        var total = 0.0
        LatentPlayerType.values().forEach {
            total += e.observationCount(DecisionPoint.INTERSTITIAL_OPPORTUNITY, it, d.chosenAction.actionId)
        }
        assertEquals("bandit khong duoc hoc tu nhom doi chung", 0.0, total, 1e-9)
    }

    @Test
    fun `treatment van hoc binh thuong`() {
        HoldoutManager.holdoutFraction = 0.5
        val e = DecisionEngine()
        val id = findPlayer(false)
        val req = DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        val d = e.decide(req, PlayerState(id), candidates())
        assertFalse(d.isHoldout)
        e.feedback(req, d, 1.0)

        var total = 0.0
        LatentPlayerType.values().forEach {
            total += e.observationCount(DecisionPoint.INTERSTITIAL_OPPORTUNITY, it, d.chosenAction.actionId)
        }
        assertEquals(1.0, total, 1e-9)
    }

    @Test
    fun `guardrail van ap dung cho nhom holdout`() {
        HoldoutManager.holdoutFraction = 0.5
        val e = DecisionEngine()
        e.guardrails.maxInterstitialPerSession = 1
        val id = findPlayer(true)
        val d = e.decide(
            DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), PlayerState(id), candidates(),
            adsShownThisSession = 5
        )
        assertTrue(
            "holdout la de do personalization, KHONG phai de bo an toan",
            d.guardrailsApplied.contains("AD_PRESSURE_SESSION_CAP")
        )
        assertEquals(ActionMode.NO_ACTION, d.chosenAction.mode)
    }

    // ═══ Sàn khám phá ═══

    @Test
    fun `propensity khong bao gio bang 0 nho san kham pha`() {
        val b = BanditPolicy()
        b.explorationFloor = 0.05
        val seg = mapOf("s" to 1.0)
        // Dồn bằng chứng cực mạnh về "good".
        repeat(300) { b.update("s", "good", 1.0); b.update("s", "bad", 0.0) }

        var sawBad = false
        var minProb = 1.0
        repeat(400) {
            val (arm, p) = b.selectArm(seg, listOf("good", "bad"))
            minProb = minOf(minProb, p)
            if (arm == "bad") sawBad = true
            assertTrue("propensity phai duong de OPE dung duoc", p > 0.0)
        }
        assertTrue("phai thi thoang van kham pha arm kem hon", sawBad)
        assertTrue("propensity nho nhat ($minProb) phai >= eps/n", minProb >= 0.05 / 2 - 1e-9)
    }

    @Test
    fun `khong co san kham pha thi propensity co the tien ve 1`() {
        val b = BanditPolicy()
        b.explorationFloor = 0.0
        val seg = mapOf("s" to 1.0)
        repeat(300) { b.update("s", "good", 1.0); b.update("s", "bad", 0.0) }
        val (arm, p) = b.selectArm(seg, listOf("good", "bad"))
        assertEquals("good", arm)
        assertTrue("day chinh la tinh huong lam OPE vo dung, p=$p", p > 0.95)
    }

    @Test
    fun `san kham pha van cho phep khai thac arm tot`() {
        val b = BanditPolicy()
        b.explorationFloor = 0.03
        val seg = mapOf("s" to 1.0)
        repeat(200) { b.update("s", "good", 1.0); b.update("s", "bad", 0.0) }
        val goodCount = (1..300).count { b.selectArm(seg, listOf("good", "bad")).first == "good" }
        assertTrue("van phai chon 'good' ap dao, thuc te $goodCount/300", goodCount > 250)
    }

    // ═══ Cấu hình từ policy artifact ═══

    @Test
    fun `artifact chinh duoc holdout va san kham pha`() {
        val e = DecisionEngine()
        val r = e.applyPolicy(
            """{"version":"exp_v1","experiment":{"holdout_fraction":0.2,"exploration_floor":0.08,"holdout_salt":"cycle_2"}}"""
        )
        assertTrue(r.applied)
        assertEquals(0.2, HoldoutManager.holdoutFraction, 1e-9)
        assertEquals("cycle_2", HoldoutManager.salt)
        assertEquals(0.08, e.bandit.explorationFloor, 1e-9)
    }

    @Test
    fun `holdout fraction bi kep khong cho vuot nua dan so`() {
        val e = DecisionEngine()
        e.applyPolicy("""{"version":"v","experiment":{"holdout_fraction":0.95}}""")
        assertEquals("holdout > 50% gan nhu chac chan la loi cau hinh", 0.5, HoldoutManager.holdoutFraction, 1e-9)
    }

    @Test
    fun `artifact tat san kham pha thi phai canh bao`() {
        val e = DecisionEngine()
        val r = e.applyPolicy("""{"version":"v","experiment":{"exploration_floor":0}}""")
        assertTrue(r.applied)
        assertTrue("phai canh bao OPE se hong", r.report.contains("OPE"))
    }
}
