package com.app.plugin.brain

import kotlin.math.exp

/**
 * Giá bóng (shadow price) cho ràng buộc tài nguyên — hiện thực hoá Bandits-with-Knapsacks
 * ở mức thực dụng (doc 28 mục E.1, doc 25 mục B.4).
 *
 * ═══ VẤN ĐỀ NÓ GIẢI ═══
 *
 * Lọc cứng ("đủ 6 quảng cáo thì chặn") tạo ra hành vi giật cục: bandit cứ chọn arm tốt nhất
 * cho tới khi đụng trần rồi im bặt. Với ngân sách 6 quảng cáo/phiên, người chơi có thể nhận
 * cả 6 trong 5 phút đầu rồi không còn gì — trải nghiệm tệ mà doanh thu cũng không tối ưu.
 *
 * Lý thuyết BwK chỉ ra lời giải tối ưu khi ràng buộc là *ngân sách* không phải "luôn chọn arm
 * tốt nhất", mà là một **phân phối trộn** — ví dụ 70% rewarded nhẹ, 20% interstitial, 10% im
 * lặng. Lagrangian là cách đạt được điều đó mà không phải giải LP mỗi lần quyết định:
 *
 * ```text
 * reward hiệu chỉnh:  r~(a) = r(a) − Σ_j lambda_j · c_j(a)
 * cập nhật dual:      lambda_j ← lambda_j · exp( eta · (c_quan_sát − B_j/T) )
 * ```
 *
 * Tiêu nhanh hơn ngân sách → `lambda` tăng → mọi action tốn tài nguyên bị phạt nặng dần,
 * một cách **trơn tru**. Tiêu chậm hơn → `lambda` giảm → hệ thống tự nới. Đây cũng là chỉ số
 * chẩn đoán rất tốt: nhìn `lambda` theo thời gian biết ngay áp lực quảng cáo đang căng hay chùng.
 *
 * ═══ VẪN GIỮ TRẦN CỨNG ═══
 *
 * Lagrangian KHÔNG thay thế trần tuyệt đối trong `GuardrailEngine`. Phạt là mềm — về nguyên
 * tắc một arm có reward đủ lớn vẫn vượt qua được. Với an toàn người dùng thì không chấp nhận
 * "đủ tiền thì mua được quyền spam". Vì vậy hai lớp bổ sung cho nhau:
 *
 *  - Lagrangian: điều tiết nhịp **bên trong** ngân sách (mượt, tối ưu)
 *  - Trần cứng : chặn tuyệt đối **ở biên** (an toàn, không thương lượng)
 *
 * ═══ GHI CHÚ TRUNG THỰC ═══
 *
 * Bảng chi phí bên dưới là **giá trị thiết kế do tôi chọn**, chưa hiệu chỉnh từ dữ liệu thật.
 * Ý nghĩa của chúng là "mức khó chịu tương đối" chứ không phải đơn vị đo được. Khi có decision
 * log đủ lớn, nên ước lượng lại từ tác động thực tế lên retention.
 */
public class LagrangianBudget {

    /** Tốc độ học của biến dual. Lớn quá thì lambda dao động; nhỏ quá thì phản ứng chậm. */
    public var learningRate: Double = 0.15

    /** Chặn trên để một chuỗi bất thường không đẩy lambda lên vô cực rồi khoá mọi action. */
    public var maxLambda: Double = 5.0

    /**
     * Khởi tạo dương. Cập nhật là phép nhân nên nếu bắt đầu từ 0 thì lambda mãi mãi bằng 0
     * và cơ chế không bao giờ hoạt động — lỗi kinh điển khi triển khai multiplicative weights.
     */
    public var lambdaAdPressure: Double = 0.1
        private set

    /**
     * "Mức khó chịu" mà mỗi loại action tiêu tốn. Rewarded thấp hơn interstitial nhiều vì
     * người chơi CHỦ ĐỘNG chọn xem — đây là khác biệt về bản chất, không phải mức độ
     * (doc 14: rewarded chỉ hợp lệ khi là lựa chọn thật).
     */
    public fun costOf(mode: ActionMode): Double = when (mode) {
        ActionMode.INTERSTITIAL -> 1.0
        ActionMode.REWARDED_AD -> 0.3
        ActionMode.IAP_OFFER -> 0.2
        ActionMode.NO_ACTION -> 0.0
        ActionMode.FREE_ACTION -> 0.0
        ActionMode.GAMEPLAY_CONFIG -> 0.0
    }

    /** Lượng phạt trừ vào reward của một action. */
    public fun penaltyFor(mode: ActionMode): Double = lambdaAdPressure * costOf(mode)

    /**
     * Cập nhật giá bóng sau khi đã chọn xong action.
     *
     * @param chosenMode action vừa thực hiện.
     * @param budgetPerDecision ngân sách cho phép trên mỗi decision point = B/T. Ví dụ trần
     *   6 interstitial/phiên và ước lượng ~30 decision point mỗi phiên thì B/T = 0.2.
     */
    public fun observe(chosenMode: ActionMode, budgetPerDecision: Double) {
        val consumed = costOf(chosenMode)
        val target = budgetPerDecision.coerceAtLeast(0.0)
        val factor = exp(learningRate * (consumed - target))
        lambdaAdPressure = (lambdaAdPressure * factor).coerceIn(0.001, maxLambda)
    }

    /** Trả lambda về mức khởi đầu — gọi khi bắt đầu phiên mới. */
    public fun reset() {
        lambdaAdPressure = 0.1
    }
}
