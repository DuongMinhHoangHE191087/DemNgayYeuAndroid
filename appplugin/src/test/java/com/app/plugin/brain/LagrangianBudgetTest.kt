package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.exp

class LagrangianBudgetTest {
    @get:Rule val license = LicenseTestRule()

    @Before
    fun setUp() {
        HoldoutManager.holdoutFraction = 0.0   // loại holdout khỏi các test này
        BrainPolicy.clear()
    }

    // ═══ Cơ chế dual ═══

    @Test
    fun `lambda tang khi tieu nhanh hon ngan sach`() {
        val b = LagrangianBudget()
        val before = b.lambdaAdPressure
        // Ngân sách 0.2/quyết định nhưng liên tục tiêu 1.0 (interstitial).
        repeat(5) { b.observe(ActionMode.INTERSTITIAL, budgetPerDecision = 0.2) }
        assertTrue("lambda phai tang, ${before} -> ${b.lambdaAdPressure}", b.lambdaAdPressure > before)
    }

    @Test
    fun `lambda giam khi tieu cham hon ngan sach`() {
        val b = LagrangianBudget()
        repeat(10) { b.observe(ActionMode.INTERSTITIAL, 0.2) }
        val peak = b.lambdaAdPressure
        repeat(30) { b.observe(ActionMode.NO_ACTION, 0.2) }
        assertTrue("lambda phai ha xuong khi ngung tieu, $peak -> ${b.lambdaAdPressure}",
            b.lambdaAdPressure < peak)
    }

    @Test
    fun `lambda luon nam trong bien an toan`() {
        val b = LagrangianBudget()
        repeat(500) { b.observe(ActionMode.INTERSTITIAL, 0.0) }
        assertTrue("khong duoc vuot maxLambda", b.lambdaAdPressure <= b.maxLambda + 1e-9)
        repeat(2000) { b.observe(ActionMode.NO_ACTION, 1.0) }
        assertTrue("khong duoc ve 0 (nhan voi 0 thi mai mai la 0)", b.lambdaAdPressure > 0.0)
    }

    @Test
    fun `khoi tao duong de phep nhan hoat dong duoc`() {
        assertTrue(
            "multiplicative weights bat dau tu 0 se mai mai bang 0 — loi kinh dien",
            LagrangianBudget().lambdaAdPressure > 0.0
        )
    }

    // ═══ Bảng chi phí ═══

    @Test
    fun `rewarded ton it tai nguyen hon interstitial`() {
        val b = LagrangianBudget()
        assertTrue(
            "rewarded la lua chon chu dong cua nguoi choi -> it kho chiu hon",
            b.costOf(ActionMode.REWARDED_AD) < b.costOf(ActionMode.INTERSTITIAL)
        )
    }

    @Test
    fun `hanh dong khong lam phien co chi phi bang 0`() {
        val b = LagrangianBudget()
        assertEquals(0.0, b.costOf(ActionMode.NO_ACTION), 1e-9)
        assertEquals(0.0, b.costOf(ActionMode.FREE_ACTION), 1e-9)
        assertEquals(0.0, b.costOf(ActionMode.GAMEPLAY_CONFIG), 1e-9)
    }

    @Test
    fun `phat ti le thuan voi lambda va chi phi`() {
        val b = LagrangianBudget()
        val p0 = b.penaltyFor(ActionMode.INTERSTITIAL)
        repeat(8) { b.observe(ActionMode.INTERSTITIAL, 0.1) }
        val p1 = b.penaltyFor(ActionMode.INTERSTITIAL)
        assertTrue("phat phai tang cung lambda, $p0 -> $p1", p1 > p0)
        assertEquals("NO_ACTION khong bao gio bi phat", 0.0, b.penaltyFor(ActionMode.NO_ACTION), 1e-9)
    }

    @Test
    fun `reset dua lambda ve muc khoi dau`() {
        val b = LagrangianBudget()
        val initial = b.lambdaAdPressure
        repeat(10) { b.observe(ActionMode.INTERSTITIAL, 0.1) }
        assertNotEquals(initial, b.lambdaAdPressure, 1e-9)
        b.reset()
        assertEquals(initial, b.lambdaAdPressure, 1e-9)
    }

    // ═══ Tác động thật lên lựa chọn của bandit ═══

    @Test
    fun `phat lam bandit bot uu tien arm ton tai nguyen`() {
        val bandit = BanditPolicy()
        bandit.explorationFloor = 0.0
        val seg = mapOf("s" to 1.0)
        // Hai arm có bằng chứng NGANG NHAU.
        repeat(50) { bandit.update("s", "inter", 1.0); bandit.update("s", "none", 1.0) }

        val noPenalty = (1..200).count { bandit.selectArm(seg, listOf("inter", "none")).first == "inter" }
        val withPenalty = (1..200).count {
            bandit.selectArm(seg, listOf("inter", "none"), penalties = mapOf("inter" to 0.5)).first == "inter"
        }
        assertTrue(
            "phat phai lam giam ro ret ti le chon 'inter': $noPenalty -> $withPenalty",
            withPenalty < noPenalty - 30
        )
    }

    @Test
    fun `phat khong lam mat hoan toan arm bi phat khi no vuot troi`() {
        val bandit = BanditPolicy()
        bandit.explorationFloor = 0.0
        val seg = mapOf("s" to 1.0)
        // "inter" tốt vượt trội, "none" kém hẳn.
        repeat(80) { bandit.update("s", "inter", 1.0); bandit.update("s", "none", 0.0) }
        val picked = (1..200).count {
            bandit.selectArm(seg, listOf("inter", "none"), penalties = mapOf("inter" to 0.15)).first == "inter"
        }
        assertTrue("phat nhe khong duoc xoa so mot arm thuc su tot, $picked/200", picked > 120)
    }

    // ═══ Tích hợp: giãn nhịp thay vì dồn cục ═══

    @Test
    fun `moi player co ngan sach rieng khong anh huong lan nhau`() {
        val e = DecisionEngine()
        val a = e.budgetFor("player_A")
        val b = e.budgetFor("player_B")
        repeat(10) { a.observe(ActionMode.INTERSTITIAL, 0.1) }
        assertTrue("lambda cua A phai tang", a.lambdaAdPressure > b.lambdaAdPressure)
        assertEquals("B khong duoc bi anh huong", LagrangianBudget().lambdaAdPressure, b.lambdaAdPressure, 1e-9)
    }

    @Test
    fun `lambda tang dan qua cac quyet dinh lien tiep trong luong that`() {
        val e = DecisionEngine()
        e.guardrails.maxInterstitialPerSession = 10   // trần cao để không bị chặn cứng
        val id = "p_pace_${System.nanoTime()}"
        val cands = listOf(
            ActionCandidate(ActionMode.NO_ACTION, "none"),
            ActionCandidate(ActionMode.INTERSTITIAL, "inter")
        )
        val req = DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY)

        // Khẳng định tiền đề thay vì giả định nó. `decide()` CHỈ cập nhật giá bóng ở nhánh
        // treatment — nhóm holdout đi đường tất định và không đụng tới ngân sách. Nếu một
        // test khác để lại `holdoutFraction` cao thì test này đỏ với thông báo vô nghĩa
        // ("giá bóng đứng yên") trong khi nguyên nhân thật là nhiễm chéo state toàn cục.
        HoldoutManager.holdoutFraction = 0.0
        assertTrue(
            "tien de: player nay phai thuoc nhom treatment, neu khong lambda khong bao gio doi",
            !HoldoutManager.isHoldout(id)
        )

        // B/T mà `decide()` dùng khi cập nhật dual. Nằm hẳn trong (0, 1) thì MỌI lựa chọn đều
        // dịch lambda: NO_ACTION (chi phí 0) kéo xuống, INTERSTITIAL (chi phí 1) đẩy lên.
        val budget = e.budgetFor(id)
        val target = e.guardrails.effectiveInterstitialCap(e.scenarioFor(id)).toDouble() /
            e.estimatedDecisionsPerSession
        assertTrue("tien de: 0 < B/T < 1, thuc te $target", target > 0.0 && target < 1.0)

        // Lựa chọn của bandit là ngẫu nhiên (RNG không seed), nên KHÔNG khẳng định "lambda cuối
        // khác lambda đầu": tiêu đúng bằng ngân sách (k = 20·B/T, ví dụ 2 INTERSTITIAL / 20 khi
        // B/T = 0.1) thì dual đúng là trở về điểm xuất phát — xem test "tieu dung bang ngan
        // sach...". Thay vào đó khẳng định điều đúng với MỌI chuỗi lựa chọn hợp lệ: giá bóng
        // sống ở từng quyết định, đúng chiều, và lambda cuối khớp đúng mức tiêu thụ thực tế.
        val start = budget.lambdaAdPressure
        var prev = start
        val chosen = (1..20).map { step ->
            val mode = e.decide(req, PlayerState(id), cands).chosenAction.mode
            val now = budget.lambdaAdPressure
            if (mode == ActionMode.INTERSTITIAL) {
                assertTrue("buoc $step: INTERSTITIAL phai day lambda len, $prev -> $now", now > prev)
            } else {
                assertTrue("buoc $step: $mode phai keo lambda xuong, $prev -> $now", now < prev)
            }
            prev = now
            mode
        }
        val end = budget.lambdaAdPressure
        val interstitials = chosen.count { it == ActionMode.INTERSTITIAL }
        val expected = start * exp(budget.learningRate * (interstitials - chosen.size * target))

        assertEquals(
            "lambda cuoi phai phan anh dung muc tieu thu thuc te (da chon: ${chosen.groupingBy { it }.eachCount()})",
            expected, end, 1e-12
        )
        assertTrue("lambda phai trong bien", end > 0.0 && end <= budget.maxLambda)
    }

    @Test
    fun `tieu dung bang ngan sach thi gia bong dung yen, lech thi dich dung chieu`() {
        // Đúng chuỗi RV-A bắt được (T8, 1.6.4): B/T = 3/30 = 0.1, 18 NO_ACTION + 2 INTERSTITIAL
        // → tổng số mũ 0.15·(2 − 20·0.1) = 0. Đây là điểm cân bằng BwK, không phải lỗi.
        fun lambdaAfter(interstitials: Int): Double {
            val b = LagrangianBudget()
            val modes = List(20 - interstitials) { ActionMode.NO_ACTION } +
                List(interstitials) { ActionMode.INTERSTITIAL }
            modes.forEach { b.observe(it, budgetPerDecision = 0.1) }
            return b.lambdaAdPressure
        }
        val start = LagrangianBudget().lambdaAdPressure
        assertEquals("tieu dung ngan sach -> lambda ve dung diem dau", start, lambdaAfter(2), 1e-9)
        assertTrue("tieu vuot ngan sach -> lambda tang", lambdaAfter(3) > start)
        assertTrue("tieu duoi ngan sach -> lambda giam", lambdaAfter(1) < start)
    }

    @Test
    fun `startNewSession cap lai ngan sach`() {
        val e = DecisionEngine()
        val id = "p_sess_${System.nanoTime()}"
        repeat(12) { e.budgetFor(id).observe(ActionMode.INTERSTITIAL, 0.1) }
        val high = e.budgetFor(id).lambdaAdPressure
        e.startNewSession(id)
        assertTrue("phien moi phai cap lai ngan sach, $high -> ${e.budgetFor(id).lambdaAdPressure}",
            e.budgetFor(id).lambdaAdPressure < high)
    }

    @Test
    fun `holdout khong dung Lagrangian`() {
        HoldoutManager.holdoutFraction = 1.0   // ép tất cả vào holdout
        val e = DecisionEngine()
        val id = "p_ho_${System.nanoTime()}"
        val before = e.budgetFor(id).lambdaAdPressure
        val cands = listOf(
            ActionCandidate(ActionMode.NO_ACTION, "none"),
            ActionCandidate(ActionMode.INTERSTITIAL, "inter", isBaseline = true)
        )
        repeat(10) { e.decide(DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), PlayerState(id), cands) }
        assertEquals(
            "nhom doi chung phai giu hanh vi cu, khong dieu tiet",
            before, e.budgetFor(id).lambdaAdPressure, 1e-9
        )
        HoldoutManager.holdoutFraction = 0.05
    }
}
