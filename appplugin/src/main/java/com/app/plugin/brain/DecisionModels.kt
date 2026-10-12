package com.app.plugin.brain

/**
 * Decision points tối thiểu, theo doc 06 mục 2. Mở rộng dần theo nhu cầu thực tế —
 * KHÔNG thêm decision point mà chưa có instrumentation event tương ứng trong package logevent.
 * (Lưu ý: không viết đường dẫn dạng `logevent/` kèm dấu sao trong KDoc — Kotlin cho phép
 * comment lồng nhau nên chuỗi đó mở một comment con và làm hỏng cả file.)
 */
public enum class DecisionPoint {
    SESSION_START,
    LEVEL_FAIL,
    LEVEL_FAIL_STREAK,
    LEVEL_COMPLETE,
    CURRENCY_LOW,
    SHOP_OPEN,
    REWARDED_OPPORTUNITY,
    INTERSTITIAL_OPPORTUNITY,
    RETURN_AFTER_INACTIVITY
}

/** Action space tối thiểu (doc 01 mục 1). GAMEPLAY_CONFIG dự phòng cho doc 24 mục C. */
public enum class ActionMode {
    NO_ACTION,
    FREE_ACTION,
    REWARDED_AD,
    INTERSTITIAL,
    IAP_OFFER,
    GAMEPLAY_CONFIG
}

public data class ActionCandidate(
    val mode: ActionMode,
    val actionId: String,
    // Điểm nội bộ của BanditPolicy trước guardrail — KHÔNG phải giá trị cuối cùng,
    // không nên đọc trường này từ bên ngoài package brain.
    var rawScore: Double = 0.0,
    /**
     * Đánh dấu đây là hành vi MẶC ĐỊNH khi chưa có brain (thường là "cứ hiện quảng cáo
     * theo rule tĩnh"). Nhóm holdout sẽ luôn nhận action này — nhờ đó phép so sánh
     * treatment vs control mới có nghĩa: control = đúng thứ hệ thống làm trước khi có brain.
     *
     * Nếu không candidate nào được đánh dấu, holdout rơi về NO_ACTION.
     */
    val isBaseline: Boolean = false
)

public data class DecisionRequest(
    val playerId: String,
    val decisionPoint: DecisionPoint,
    val context: Map<String, Any> = emptyMap()
)

public data class DecisionResponse(
    val decisionId: String,
    val chosenAction: ActionCandidate,
    // Xác suất chọn action này dưới policy hiện tại — BẮT BUỘC cho OPE (doc 05 mục 10,
    // doc 24 mục K). Đây là "chosen_action_probability" trong decision log schema (doc 06 mục 8).
    //
    // QUAN TRỌNG: xác suất này có điều kiện trên `eligibleActionIds`, KHÔNG phải trên toàn bộ
    // candidate ban đầu. Mọi estimator OPE đọc log phải điều kiện hoá theo cùng tập đó, nếu
    // không importance weight sẽ sai (doc 28 mục E.4).
    val chosenActionProbability: Double,
    val policyVersion: String,
    // Mã guardrail đã kích hoạt (mỗi lần loại một candidate ghi một mã).
    val guardrailsApplied: List<String> = emptyList(),
    // Tập action CÒN LẠI sau guardrail — tập mà bandit thực sự chọn trong đó.
    // Không có trường này thì log không tái lập được quyết định, và OPE mất hiệu lực.
    val eligibleActionIds: List<String> = emptyList(),
    // Segment và kịch bản đang áp dụng tại thời điểm quyết định. Ghi vào log để sau này
    // phân tích được hiệu quả theo từng segment, và để debug view hiển thị trực tiếp.
    val segment: LatentPlayerType? = null,
    val segmentConfidence: Double = 0.0,
    val scenario: PersonalizationScenario? = null,
    /**
     * Player này thuộc nhóm đối chứng (không được personalize). BẮT BUỘC ghi vào log —
     * phân tích sau này phải tách được treatment khỏi control, và không có cờ này thì
     * hai nhóm lẫn vào nhau, toàn bộ phép đo incrementality mất hiệu lực.
     */
    val isHoldout: Boolean = false
)
