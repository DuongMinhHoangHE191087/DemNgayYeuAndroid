package com.app.plugin.brain

/**
 * Player Comfort & Willingness-to-Pay Index — North Star Metric thứ tư (doc 26 mục C.4).
 *
 * ═══ VÌ SAO TỒN TẠI ═══
 *
 * Ba chỉ số kia (ARPDAU, Revenue, Subscription) đều **dễ tối ưu bằng cách hy sinh người chơi**.
 * Chỉ số này là đối trọng. Doc 26 đặt ràng buộc: Control Plane phải hiển thị cả bốn cùng lúc,
 * không cho phép xem riêng ARPDAU mà ẩn chỉ số này.
 *
 * ═══ VẤN ĐỀ TRUNG THỰC: PHẦN LỚN TÍN HIỆU CHƯA CÓ ═══
 *
 * Công thức đầy đủ ở doc 26 cần 5 thành phần. Hiện tại chỉ **2** tính được:
 *
 * | Thành phần | Trạng thái |
 * |---|---|
 * | `ad_fatigue` | ✅ suy từ `adImpressions24h` so với trần |
 * | `churn_proxy` | ✅ suy từ `daysSinceLastSession` (thô) |
 * | `organic_purchase_probability` | ❌ cần causal model chưa tồn tại |
 * | `refund_rate` | ❌ SDK chưa theo dõi refund |
 * | `post_purchase_retention_delta` | ❌ SDK chưa theo dõi retention sau mua |
 *
 * Vì vậy `compute()` **luôn trả về kèm `coverage`** — tỉ lệ thành phần có dữ liệu. Một chỉ số
 * độ phủ 40% mà trình bày như con số chắc chắn còn nguy hiểm hơn không có chỉ số nào: nó tạo
 * cảm giác đã đo được thứ chưa đo được. Caller **phải** hiển thị `coverage` cạnh giá trị.
 *
 * ═══ ĐÍNH CHÍNH CÔNG THỨC DOC 26 ═══
 *
 * Doc 26 viết `− w5·guardrail_violation_rate_prevented` (dấu âm) nhưng lại mô tả nó là "proxy
 * cho việc hệ thống đang bảo vệ user" (tức tốt). Hai điều đó mâu thuẫn.
 *
 * Xử lý: **bỏ hẳn khỏi ComfortIndex.** Guardrail nổ nhiều nghĩa là *chính sách bên dưới đang
 * muốn hung hăng* và bị chặn lại — đó là tín hiệu về **sức khoẻ chính sách**, không phải về
 * trải nghiệm người chơi (người chơi đã được bảo vệ, nên comfort của họ không đổi). Gộp hai
 * thứ vào một chỉ số làm cả hai mất ý nghĩa. Tần suất guardrail nổ được báo cáo riêng qua
 * `policyAggressiveness()`.
 */
internal object ComfortIndex {

    /** Kết quả kèm thông tin về mức độ đáng tin. */
    public data class Result(
        /** Giá trị 0..1; càng cao càng thoải mái. */
        val value: Double,
        /** Tỉ lệ thành phần có dữ liệu (0..1). Thấp = KHÔNG được coi là kết luận. */
        val coverage: Double,
        /** Tên các thành phần đã tính được. */
        val componentsAvailable: List<String>,
        /** Tên các thành phần thiếu dữ liệu, kèm lý do. */
        val componentsMissing: List<String>
    ) {
        /** Ngưỡng tối thiểu để con số này đáng đưa vào quyết định kinh doanh. */
        public fun isTrustworthy(): Boolean = coverage >= 0.6

        public fun describe(): String = buildString {
            append("ComfortIndex %.2f (độ phủ %.0f%%".format(value, coverage * 100))
            if (!isTrustworthy()) append(" — CHƯA ĐỦ TIN CẬY")
            append(")")
        }
    }

    /** Tổng số thành phần trong công thức đầy đủ (doc 26), dùng làm mẫu số của độ phủ. */
    private const val TOTAL_COMPONENTS = 5

    /**
     * @param adCapPerDay trần quảng cáo/ngày dùng để chuẩn hoá mức mệt mỏi.
     */
    public fun compute(state: PlayerState, adCapPerDay: Int = 40): Result {
        val available = mutableListOf<String>()
        val missing = mutableListOf<String>()
        var sum = 0.0
        var weightUsed = 0.0

        // --- Có dữ liệu: mức mệt mỏi vì quảng cáo ---
        val cap = adCapPerDay.coerceAtLeast(1)
        val fatigue = (state.adImpressions24h.toDouble() / cap).coerceIn(0.0, 1.0)
        sum += (1.0 - fatigue); weightUsed += 1.0
        available.add("ad_fatigue=%.2f".format(fatigue))

        // --- Có dữ liệu: proxy churn thô từ khoảng nghỉ ---
        val churnProxy = when {
            state.daysSinceLastSession >= 7 -> 0.9
            state.daysSinceLastSession >= 3 -> 0.6
            state.daysSinceLastSession >= 1 -> 0.3
            else -> 0.1
        }
        sum += (1.0 - churnProxy); weightUsed += 1.0
        available.add("churn_proxy=%.2f".format(churnProxy))

        // --- Chưa có dữ liệu ---
        missing.add("organic_purchase_probability (cần causal model — doc 05)")
        missing.add("refund_rate (SDK chưa theo dõi refund)")
        missing.add("post_purchase_retention_delta (SDK chưa theo dõi retention sau mua)")

        val value = if (weightUsed > 0.0) (sum / weightUsed).coerceIn(0.0, 1.0) else 0.0
        return Result(
            value = value,
            coverage = available.size.toDouble() / TOTAL_COMPONENTS,
            componentsAvailable = available,
            componentsMissing = missing
        )
    }

    /**
     * Mức hung hăng của chính sách — tách khỏi ComfortIndex có chủ đích (xem phần đính chính
     * ở đầu file). Guardrail nổ càng nhiều nghĩa là policy bên dưới càng muốn vượt rào.
     *
     * @return số lần guardrail đã chặn, nhóm theo mã. Diễn giải: tăng đột biến ở một mã nào đó
     *   là dấu hiệu policy hoặc artifact vừa đổi theo hướng cần rà lại.
     */
    public fun policyAggressiveness(engine: DecisionEngine): Map<String, Int> =
        engine.guardrails.allPreventedCounts()
}
