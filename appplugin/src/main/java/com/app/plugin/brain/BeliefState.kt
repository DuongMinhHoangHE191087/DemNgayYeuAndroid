package com.app.plugin.brain

/**
 * Belief state tối giản cho doc 24 mục B (POMDP): posterior RỜI RẠC trên latent player type.
 * Đây là điểm khởi đầu thực dụng — discrete, không phải particle filter đầy đủ — đủ cho
 * Phase 1-3 của roadmap (doc 23). Nâng cấp lên continuous/particle filter chỉ nên làm khi
 * đã đủ volume dữ liệu (doc 24 mục G: độ phức tạp model phải khớp độ trưởng thành dữ liệu).
 */
public enum class LatentPlayerType {
    HIGH_SKILL_LOW_TOLERANCE,   // giỏi, ít chịu được ad -> ưu tiên IAP nhẹ nhàng
    HIGH_SKILL_HIGH_TOLERANCE,  // giỏi, chịu ad tốt -> IAA-heavy an toàn
    LOW_SKILL_HIGH_CHURN_RISK,  // yếu, dễ rời bỏ -> bảo vệ trải nghiệm, free rescue
    CASUAL_STEADY                // ổn định, chưa rõ tín hiệu mạnh -> fallback trung tính
}

public data class BeliefState(
    val playerId: String,
    val posterior: MutableMap<LatentPlayerType, Double> = mutableMapOf(
        LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE to 0.25,
        LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE to 0.25,
        LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK to 0.25,
        LatentPlayerType.CASUAL_STEADY to 0.25
    )
) {
    /**
     * Cập nhật Bayes rời rạc: b_t+1(z) ∝ P(obs|z) * b_t(z).
     * likelihood: P(quan sát vừa xảy ra | mỗi loại z) — ví dụ tính từ tần suất fail/ad-accept
     * quan sát được so với baseline kỳ vọng của mỗi type. Việc ước lượng likelihood là
     * trách nhiệm của feature pipeline bên ngoài, không phải của class này.
     */
    public fun update(likelihood: Map<LatentPlayerType, Double>) {
        var normalizer = 0.0
        val updated = mutableMapOf<LatentPlayerType, Double>()
        for (type in LatentPlayerType.values()) {
            val prior = posterior[type] ?: 0.0
            val lik = likelihood[type] ?: 1.0
            val unnorm = prior * lik
            updated[type] = unnorm
            normalizer += unnorm
        }
        if (normalizer > 0.0) {
            for (type in LatentPlayerType.values()) {
                posterior[type] = (updated[type] ?: 0.0) / normalizer
            }
        }
    }

    public fun mostLikelyType(): LatentPlayerType =
        posterior.maxByOrNull { it.value }?.key ?: LatentPlayerType.CASUAL_STEADY

    /** Độ tin cậy vào mostLikelyType(); thấp -> nên fallback về policy trung tính/an toàn hơn. */
    public fun confidence(): Double = posterior.values.maxOrNull() ?: 0.0

    /** Đã học được gì chưa, hay vẫn đứng nguyên ở prior đồng đều? */
    public fun isStillPrior(): Boolean =
        posterior.values.all { kotlin.math.abs(it - 0.25) < 1e-6 }

    /** Dạng chuỗi gọn để lưu vào SharedPreferences: "TYPE:0.31|TYPE:0.19|..." */
    public fun serialize(): String =
        LatentPlayerType.values().joinToString("|") { "${it.name}:${posterior[it] ?: 0.0}" }

    public companion object {
        /**
         * Khôi phục belief từ chuỗi đã lưu. Hỏng/thiếu dữ liệu → trả về prior đồng đều thay vì
         * ném exception: mất belief chỉ làm cá nhân hóa kém đi tạm thời, còn crash thì làm hỏng
         * cả phiên chơi của user (nguyên tắc fail-safe, doc 27).
         */
        public fun deserialize(playerId: String, raw: String?): BeliefState {
            val state = BeliefState(playerId)
            if (raw.isNullOrBlank()) return state
            return try {
                val parsed = mutableMapOf<LatentPlayerType, Double>()
                var total = 0.0
                for (part in raw.split("|")) {
                    val kv = part.split(":")
                    if (kv.size != 2) continue
                    val type = LatentPlayerType.values().firstOrNull { it.name == kv[0] } ?: continue
                    val value = kv[1].toDoubleOrNull() ?: continue
                    if (value < 0.0) continue
                    parsed[type] = value
                    total += value
                }
                if (parsed.size != LatentPlayerType.values().size || total <= 0.0) return state
                for (type in LatentPlayerType.values()) {
                    state.posterior[type] = (parsed[type] ?: 0.0) / total
                }
                state
            } catch (e: Exception) {
                BeliefState(playerId)
            }
        }
    }
}
