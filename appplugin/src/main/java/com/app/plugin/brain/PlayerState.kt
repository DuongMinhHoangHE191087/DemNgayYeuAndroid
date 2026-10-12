package com.app.plugin.brain

/**
 * S_t (doc 04 / doc 24 mục I): observable state + rolling latent-trait estimates.
 * Input cho DecisionEngine.decide(). Phần observable do caller (game) cung cấp mỗi lần gọi;
 * phần latent/derived (cuối file) do BeliefState và pipeline feature tính, KHÔNG phải input tay.
 *
 * Đây là bản tối giản để bắt đầu (Phase 0-1, doc 23 mục D) — mở rộng field theo feature
 * dictionary đầy đủ ở doc 04 khi decision point thực tế cần thêm tín hiệu.
 */
public data class PlayerState(
    val playerId: String,

    // --- Observable: lifecycle & session ---
    var sessionsLifetime: Int = 0,
    var daysSinceLastSession: Int = 0,
    var sessionMinutesCurrent: Int = 0,

    // --- Observable: progression / difficulty response ---
    var currentLevel: Int = 0,
    var failStreakCurrent: Int = 0,
    var failRateLast30m: Double = 0.0,

    // --- Observable: economy ---
    var softCurrencyBalance: Long = 0L,
    var hardCurrencyBalance: Long = 0L,
    var burnRate24h: Double = 0.0,

    // --- Observable: IAP behavior ---
    var purchaseCountLifetime: Int = 0,
    var purchaseValueLifetimeUsd: Double = 0.0,
    var daysSinceLastPurchase: Int = -1,
    /**
     * Số lượt mua TRONG PHIÊN HIỆN TẠI. Đây là marker at-risk mạnh nhất và dễ đo nhất trong
     * IAP theo doc 29 mục 5.2 ("deposit frequency spike"), và là đầu vào của circuit breaker
     * ở `GuardrailEngine`. Tách khỏi `purchaseCountLifetime` vì hai thứ trả lời hai câu hỏi
     * khác hẳn nhau: "đã từng trả tiền chưa" so với "có đang chi dồn dập ngay lúc này không".
     */
    var purchasesThisSession: Int = 0,
    /** Số lần đã chào bán trong phiên — trần tần suất chào bán. */
    var iapOffersThisSession: Int = 0,
    /** -1 = chưa từng chào bán trong phiên này. */
    var minutesSinceLastIapOffer: Int = -1,
    /** Số lần chào bán trong 24h. Trần theo phiên là chưa đủ với người mở app nhiều lần/ngày. */
    var iapOffers24h: Int = 0,
    /** -1 = chưa từng xem rewarded. Dùng để hoãn interstitial ngay sau một rewarded tự nguyện. */
    var minutesSinceLastRewarded: Int = -1,
    /** Số cơ hội interstitial đã ĐI QUA trong phiên — kể cả lần bị chặn. Dùng cho ân hạn mở lại app. */
    var interstitialOpportunitiesThisSession: Int = 0,
    /**
     * Số lần hỗ trợ gameplay (quà tặng / chỉnh độ khó) đã đưa ra trong phiên. Trợ giúp là MIỄN
     * PHÍ cho studio nhưng chi phí goodwill thì thật: dồn dập quà tặng tạo helplessness học
     * được và làm hỏng kinh tế game — nên vẫn cần trần như mọi action gây áp lực khác.
     */
    var assistsThisSession: Int = 0,

    // --- Observable: IAA behavior ---
    var adImpressions24h: Int = 0,
    var rewardedAcceptRateRecent: Double = 0.0,
    // Số cơ hội rewarded đã ĐƯỢC MỜI gần đây. Bắt buộc phải có cùng với rewardedAcceptRateRecent:
    // tỉ lệ 0.0 ở người chưa từng được mời có ý nghĩa hoàn toàn khác với người được mời 10 lần
    // và từ chối cả 10. Thiếu trường này, LikelihoodEstimator sẽ kết luận sai.
    var rewardedOpportunitiesRecent: Int = 0,
    var minutesSinceLastAd: Int = -1,

    // --- Derived / latent (doc 24 mục I — filled bởi BeliefState hoặc feature pipeline) ---
    var purchasePropensity: Double = 0.0,     // P(purchase | 24h)
    var churnRisk: Double = 0.0,              // P(churn | 7d)
    var adToleranceScore: Double = 0.5,       // 0..1
    var adFatigueScore: Double = 0.0,         // 0..1
    var monetizationReadiness: Double = 0.0   // doc 01 mục 5, composite có trọng số
)
