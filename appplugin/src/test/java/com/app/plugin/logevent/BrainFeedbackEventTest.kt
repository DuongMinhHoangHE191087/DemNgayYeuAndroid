package com.app.plugin.logevent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Khoá payload của event `brain_feedback` (doc 46 mục B.1, doc 28 mục H.1).
 *
 * Hai trường propensity và tập ứng viên của decision đã log là "không bao giờ tái tạo
 * được" — nhưng thiếu reward thì mọi estimator IPS/DR phía server đều vô hiệu. Test này
 * khoá hợp đồng của trường `reward_value`:
 *   - có mặt, nguyên bản, khi DecisionEngine chốt được reward;
 *   - VẮNG MẶT (không phải 0.0) khi chưa có reward — backend phải phân biệt được
 *     "thiếu dữ liệu" với "reward bằng không", vì 0.0 là một reward hợp lệ
 *     (quảng cáo hiện xong người chơi bỏ game).
 */
class BrainFeedbackEventTest {

    @Test
    fun `feedback co reward mang truong reward_value nguyen ban`() {
        val d = LogEventServer.brainFeedbackEventData(
            decisionId = "d_p1_run1_7",
            event = "outcome_reward",
            timestampMs = 1_724_500_000_000L,
            rewardValue = 0.4
        )
        assertEquals("d_p1_run1_7", d["decision_id"])
        assertEquals("outcome_reward", d["outcome_event"])
        assertEquals(1_724_500_000_000L, d["outcome_timestamp_ms"])
        assertEquals(0.4, d["reward_value"] as Double, 1e-9)
    }

    @Test
    fun `feedback khong co reward thi truong vang mat chu khong phai 0`() {
        val d = LogEventServer.brainFeedbackEventData(
            decisionId = "d_p1_run1_8",
            event = "outcome_reward",
            timestampMs = 1_724_500_000_000L,
            rewardValue = null
        )
        assertFalse(
            "reward_value vang mat de backend phan biet 'thieu' voi 0.0",
            d.containsKey("reward_value")
        )
        // Các trường bắt buộc khác không vì thế mà mất.
        assertEquals("d_p1_run1_8", d["decision_id"])
        assertEquals("outcome_reward", d["outcome_event"])
    }

    @Test
    fun `reward bang 0 la gia tri hop le phai duoc gui di`() {
        // Quảng cáo hiện xong người chơi rời game — reward 0.00 theo RewardModel.
        // Nếu nhầm "0.0" với "không có" hàng này sẽ bị loại khỏi IPS một cách im lặng.
        val d = LogEventServer.brainFeedbackEventData(
            decisionId = "d_p1_run1_9",
            event = "outcome_reward",
            timestampMs = 1_724_500_000_000L,
            rewardValue = 0.0
        )
        assertEquals(0.0, d["reward_value"] as Double, 1e-9)
    }
}
