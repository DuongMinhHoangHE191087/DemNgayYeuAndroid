package com.app.plugin.brain

/**
 * Reward model — quyết định "một quyết định tốt" nghĩa là gì.
 *
 * ═══ LỖI ĐANG SỬA ═══
 *
 * Bản đầu gán reward ngay tại thời điểm hành động: `INTERSTITIAL → 1.0`, `NO_ACTION → 0.0`.
 * Đó là **tautological** — action được thưởng vì đã xảy ra, không phải vì tạo ra giá trị.
 * Hệ quả tất yếu: bandit hội tụ về "luôn hiện quảng cáo", và thứ duy nhất cản lại là guardrail.
 * Nói cách khác, toàn bộ phần học máy trở nên vô nghĩa — nó chỉ học lại chính luật đã cứng.
 *
 * ═══ NGUYÊN TẮC: DOWNSTREAM REWARD ═══
 *
 * Reward phải đến từ **điều xảy ra sau** hành động, không phải từ bản thân hành động. Đây là
 * pattern Pinterest đã production hoá (doc 03 mục 8, doc 05 mục 9): retention là tín hiệu
 * thưa và trễ, nên dùng hành vi phiên xuất hiện sớm nhưng dự báo được retention làm proxy.
 *
 * Tín hiệu proxy dùng ở đây là **người chơi có tiếp tục chơi sau hành động hay không**:
 *
 * ```text
 * R = w_value · giá_trị_tức_thì  +  w_continue · tiếp_tục_chơi
 * ```
 *
 * Thứ tự ưu tiên mà công thức tạo ra (với trọng số mặc định):
 *
 * | Tình huống                        | Reward | Diễn giải |
 * |-----------------------------------|--------|-----------|
 * | Hiện ad, người chơi ở lại         | 1.00   | tốt nhất — có doanh thu mà không mất ai |
 * | Không hiện, người chơi ở lại      | 0.60   | an toàn nhưng bỏ lỡ doanh thu |
 * | Hiện ad, người chơi bỏ đi         | 0.40   | **tệ hơn không làm gì** |
 * | Không hiện, người chơi vẫn bỏ đi  | 0.00   | mất người mà chẳng được gì |
 *
 * Điểm mấu chốt là dòng thứ ba: hiện quảng cáo rồi mất người chơi phải bị chấm **thấp hơn**
 * việc im lặng. Không có tính chất đó thì bandit không bao giờ học được cách kiềm chế.
 *
 * ═══ ĐÂY VẪN CHƯA PHẢI ĐÍCH ═══
 *
 * Công thức đầy đủ ở doc 11 là:
 *
 * ```text
 * R = r_tức_thì + γ·[LTV(s') − LTV(s)] − λ_churn·P(churn|action) − λ_fair·ViPhamCongBang
 * ```
 *
 * Cần LTV/churn model chưa tồn tại. Cái ở đây là **bậc thang trung gian đúng hướng**: không
 * còn tautological, đã có đánh đổi thật giữa doanh thu và giữ chân. Khi có LTV model, thay
 * `estimate()` chứ không cần sửa `DecisionEngine` hay `AdsBrain`.
 */
internal object RewardModel {

    /** Trọng số cho giá trị tức thì (có hiện được quảng cáo hay không). */
    public var weightImmediateValue: Double = 0.4

    /** Trọng số cho tín hiệu tiếp tục chơi — proxy cho retention. */
    public var weightContinuation: Double = 0.6

    /**
     * @param actionHadValue hành động có tạo ra giá trị tức thì không (quảng cáo hiện được,
     *   rewarded được xem hết, IAP thành công).
     * @param playerContinued người chơi có tiếp tục chơi sau đó không. `null` = chưa biết,
     *   coi như trung tính để không đoán bừa.
     */
    public fun estimate(actionHadValue: Boolean, playerContinued: Boolean?): Double {
        val value = if (actionHadValue) weightImmediateValue else 0.0
        val cont = when (playerContinued) {
            true -> weightContinuation
            false -> 0.0
            null -> weightContinuation / 2.0   // chưa biết: không thưởng cũng không phạt
        }
        return (value + cont).coerceIn(0.0, 1.0)
    }

    /** Đưa trọng số về mặc định. */
    public fun reset() {
        weightImmediateValue = 0.4
        weightContinuation = 0.6
    }
}
