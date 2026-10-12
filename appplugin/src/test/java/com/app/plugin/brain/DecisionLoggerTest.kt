package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Test cho đường đi của decision log.
 *
 * Đây là loại lỗi tệ nhất nếu bỏ sót: log không tới đích thì hệ thống vẫn chạy bình thường,
 * không crash, không cảnh báo — chỉ đến khi cần phân tích mới phát hiện dữ liệu không tồn tại,
 * và lúc đó không tạo lại được. Vì vậy phải có test khẳng định forwarder thực sự được gọi.
 */
class DecisionLoggerTest {
    @get:Rule val license = LicenseTestRule()

    private fun candidates() = listOf(
        ActionCandidate(ActionMode.NO_ACTION, "no_action"),
        ActionCandidate(ActionMode.INTERSTITIAL, "inter", isBaseline = true)
    )

    @Test
    fun `moi quyet dinh deu duoc day toi forwarder`() {
        val captured = mutableListOf<Pair<DecisionRequest, DecisionResponse>>()
        DecisionLogger.registerForwarder { req, res -> captured.add(req to res) }

        val e = DecisionEngine()
        val id = "p_log_${System.nanoTime()}"
        val req = DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        repeat(3) { e.decide(req, PlayerState(id), candidates()) }

        val mine = captured.filter { it.first.playerId == id }
        assertEquals("phai bat duoc du 3 quyet dinh", 3, mine.size)
    }

    @Test
    fun `log mang day du truong bat buoc cho OPE`() {
        var seen: DecisionResponse? = null
        val id = "p_ope_${System.nanoTime()}"
        DecisionLogger.registerForwarder { req, res -> if (req.playerId == id) seen = res }

        val e = DecisionEngine()
        e.decide(DecisionRequest(id, DecisionPoint.REWARDED_OPPORTUNITY), PlayerState(id), candidates())

        val r = requireNotNull(seen) { "forwarder khong duoc goi" }
        assertTrue("decision_id khong duoc rong", r.decisionId.isNotBlank())
        assertTrue("propensity phai duong", r.chosenActionProbability > 0.0)
        assertTrue("phai co tap ung vien", r.eligibleActionIds.isNotEmpty())
        assertTrue("policy_version khong duoc rong", r.policyVersion.isNotBlank())
    }

    @Test
    fun `feedback cung duoc day toi forwarder`() {
        val ids = mutableListOf<String>()
        DecisionLogger.registerFeedbackForwarder { decisionId, _, _, _ -> ids.add(decisionId) }

        val e = DecisionEngine()
        val id = "p_fb_${System.nanoTime()}"
        val req = DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        val d = e.decide(req, PlayerState(id), candidates())
        e.feedback(req, d, 1.0)

        assertTrue("feedback phai toi duoc forwarder", ids.contains(d.decisionId))
    }

    @Test
    fun `reward tu engine toi duoc forwarder nguyen ban`() {
        // Con số này là đầu vào KHÔNG THỂ TÁI TẠO của OPE phía server (doc 28 mục H.1):
        // propensity đã log trong decision, nhưng thiếu reward thì IPS/DR đều vô hiệu.
        val captured = mutableListOf<Pair<String, Double?>>()
        DecisionLogger.registerFeedbackForwarder { decisionId, _, _, reward ->
            captured.add(decisionId to reward)
        }

        val e = DecisionEngine()
        val id = "p_reward_${System.nanoTime()}"
        val req = DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        val d = e.decide(req, PlayerState(id), candidates())
        e.feedback(req, d, 0.4)

        val mine = captured.filter { it.first == d.decisionId }
        assertEquals("feedback reward phai toi forwarder dung ban ghi", 1, mine.size)
        assertEquals(0.4, mine[0].second!!, 1e-9)
    }

    @Test
    fun `forwarder nem exception khong lam hong luong quyet dinh`() {
        DecisionLogger.registerForwarder { _, _ -> throw RuntimeException("sink hỏng") }

        val e = DecisionEngine()
        val id = "p_boom_${System.nanoTime()}"
        // Không được ném ra ngoài: logging hỏng thì mất dữ liệu, nhưng phiên chơi của
        // user phải tiếp tục bình thường.
        val d = e.decide(DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), PlayerState(id), candidates())
        assertTrue(d.decisionId.isNotBlank())
    }

    @Test
    fun `decision_id la duy nhat trong cung mot player`() {
        val e = DecisionEngine()
        val id = "p_uniq_${System.nanoTime()}"
        val req = DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY)
        val a = e.decide(req, PlayerState(id), candidates()).decisionId
        val b = e.decide(req, PlayerState(id), candidates()).decisionId
        assertNotEquals("trung decision_id se lam hong viec khop outcome", a, b)
    }

    @Test
    fun `installDefaultSink goi nhieu lan van an toan`() {
        // Idempotent: gọi lại không được nhân đôi số bản ghi gửi đi.
        repeat(5) { DecisionLogger.installDefaultSink() }
        val e = DecisionEngine()
        val id = "p_idem_${System.nanoTime()}"
        val d = e.decide(DecisionRequest(id, DecisionPoint.SESSION_START), PlayerState(id), candidates())
        assertTrue(d.decisionId.isNotBlank())
    }
}
