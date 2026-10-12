package com.app.plugin.brain

/**
 * Kịch bản personalization — ánh xạ mỗi latent segment sang một BỘ NÚM CHÍNH SÁCH cụ thể.
 *
 * Đây là hiện thực hóa bảng chiến lược ở doc 01 mục 2: hai non-payer có thể cần chiến lược
 * hoàn toàn khác nhau. Segment trả lời "player này là ai"; scenario trả lời "vậy thì làm gì".
 *
 * Các hệ số dưới đây là **prior từ domain knowledge, chưa được calibrate bằng dữ liệu thật**
 * (doc 30 gọi đây là [Rule of thumb]). Chúng là điểm khởi đầu để bandit học tiếp, KHÔNG phải
 * kết luận đã đo. Khi có decision log đủ lớn, bandit sẽ tự điều chỉnh và các số này chỉ còn
 * vai trò warm-start prior (doc 28 mục D: `kappa` ∈ 0.1–0.3 cho trust coefficient).
 */
public enum class PersonalizationScenario(
    public val displayName: String,
    public val rationale: String,
    /** Trần interstitial mỗi phiên cho segment này. */
    public val interstitialCapPerSession: Int,
    /** Mức chủ động mời rewarded (0 = chỉ khi user tự tìm, 1 = mời mọi cơ hội hợp lệ). */
    public val rewardedAggressiveness: Double,
    /** Ưu tiên đẩy IAP offer so với ad (0 = không bao giờ, 1 = ưu tiên tuyệt đối). */
    public val iapOfferPriority: Double,
    /** Mức hào phóng cho FREE_ACTION — giá trị tặng không kèm điều kiện. */
    public val freeValueGenerosity: Double
) {
    /** Payer tiềm năng, nhạy cảm với quảng cáo → bảo vệ trải nghiệm, dồn lực vào IAP. */
    PROTECT_AND_CONVERT(
        displayName = "Bảo vệ & chuyển đổi",
        rationale = "Kỹ năng tốt, chịu ad kém, có tín hiệu chi trả → giảm ad, ưu tiên IAP",
        interstitialCapPerSession = 2,
        rewardedAggressiveness = 0.2,
        iapOfferPriority = 0.9,
        freeValueGenerosity = 0.3
    ),

    /** Chịu ad tốt, ít khả năng mua → khai thác phía IAA là hợp lý và không gây hại. */
    AD_FORWARD(
        displayName = "Thiên về quảng cáo",
        rationale = "Chịu ad tốt, ít tín hiệu chi trả → IAA là nguồn giá trị chính",
        interstitialCapPerSession = 6,
        rewardedAggressiveness = 0.8,
        iapOfferPriority = 0.2,
        freeValueGenerosity = 0.2
    ),

    /** Nguy cơ rời bỏ cao → ưu tiên giữ chân trước, kiếm tiền sau (doc 14: hai vòng tách biệt). */
    RETENTION_FIRST(
        displayName = "Giữ chân trước hết",
        rationale = "Fail nhiều, rủi ro churn cao → giảm mọi áp lực, tăng giá trị miễn phí",
        interstitialCapPerSession = 1,
        rewardedAggressiveness = 0.3,
        iapOfferPriority = 0.1,
        freeValueGenerosity = 0.9
    ),

    /** Chưa đủ tín hiệu để phân biệt → chính sách cân bằng, an toàn. */
    BALANCED_HYBRID(
        displayName = "Cân bằng",
        rationale = "Chưa đủ tín hiệu phân biệt → chính sách trung tính, tiếp tục quan sát",
        interstitialCapPerSession = 3,
        rewardedAggressiveness = 0.5,
        iapOfferPriority = 0.5,
        freeValueGenerosity = 0.5
    );

    public companion object {
        /** Ánh xạ segment → kịch bản. Quan hệ 1-1 có chủ đích: mỗi segment có đúng một chiến lược. */
        public fun forType(type: LatentPlayerType): PersonalizationScenario = when (type) {
            LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE -> PROTECT_AND_CONVERT
            LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE -> AD_FORWARD
            LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK -> RETENTION_FIRST
            LatentPlayerType.CASUAL_STEADY -> BALANCED_HYBRID
        }

        /**
         * Ngưỡng tin cậy tối thiểu để áp kịch bản chuyên biệt. Dưới ngưỡng này, belief còn quá
         * phân tán để kết luận — dùng BALANCED_HYBRID thay vì đoán bừa (doc 33: segmentation
         * chỉ có giá trị khi phân biệt được thật, không phải khi gán nhãn cho có).
         */
        public const val MIN_CONFIDENCE: Double = 0.40

        /**
         * Ngưỡng tin cậy lấy từ policy artifact nếu có (cho phép chỉnh từ xa), mặc định
         * `MIN_CONFIDENCE`. Kill switch trong artifact buộc mọi player về kịch bản trung tính —
         * đây là đường thoát khi phát hiện personalization đang gây hại và cần tắt ngay
         * mà không kịp release app.
         */
        public fun resolve(belief: BeliefState): PersonalizationScenario {
            // Bị từ chối consent thì không được dùng hồ sơ để cá nhân hoá. Hồ sơ cũ vẫn nằm
            // trên đĩa và sẽ dùng lại được nếu người dùng cho phép trở lại — thứ bị chặn là
            // VIỆC DÙNG, đúng ranh giới mà quy định đặt ra. Chưa có câu trả lời (UNKNOWN) cũng chặn: không có
            // Context để đọc IABTCF_gdprApplies, nên dùng bản không tham số (#7, DOC/66).
            if (!com.app.plugin.consent.ConsentState.allowsPersonalization()) return BALANCED_HYBRID
            if (!BrainPolicy.isEnabled()) return BALANCED_HYBRID
            return if (belief.confidence() >= BrainPolicy.minConfidence()) forType(belief.mostLikelyType())
            else BALANCED_HYBRID
        }
    }
}
