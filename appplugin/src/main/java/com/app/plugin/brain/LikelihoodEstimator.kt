package com.app.plugin.brain

import kotlin.math.exp
import kotlin.math.pow

/**
 * Tính P(quan sát | latent type) — mắt xích làm cho `BeliefState` thực sự chuyển động.
 *
 * Trước khi có class này, `BeliefState.update()` tồn tại nhưng không ai gọi, nên posterior của
 * mọi player vĩnh viễn đứng ở prior đồng đều 0.25 — hệ thống suy biến thành một bandit toàn
 * cục không có cá nhân hóa. Đây là phần sửa lỗi đó.
 *
 * MÔ HÌNH: naive Bayes trên vài tín hiệu độc lập có điều kiện. Mỗi latent type có một "hồ sơ
 * kỳ vọng" (expected profile); likelihood của một quan sát là độ khớp Gaussian với hồ sơ đó.
 *
 * BA NGUYÊN TẮC AN TOÀN được cài sẵn — đây là phần dễ làm sai nhất:
 *
 *  1. **Support gating**: một tín hiệu chỉ được tính nếu có ĐỦ dữ liệu. Ví dụ
 *     `rewardedAcceptRateRecent = 0.0` ở người chưa từng được mời rewarded KHÔNG có nghĩa là
 *     "ghét quảng cáo" — nó có nghĩa là "chưa biết". Gộp nhầm hai thứ này là lỗi kinh điển.
 *  2. **Damping**: likelihood được nâng lũy thừa `DAMPING < 1` để một quan sát đơn lẻ không
 *     thể lật posterior. Belief nên trôi dần, không nhảy giật.
 *  3. **Floor**: mọi likelihood bị chặn dưới bởi `MIN_LIKELIHOOD` để không segment nào bị đẩy
 *     về xác suất 0 vĩnh viễn — từ 0 thì Bayes không bao giờ hồi phục được.
 *
 * CẢNH BÁO TRUNG THỰC: toàn bộ hằng số hồ sơ dưới đây là **giá trị giả định từ domain
 * knowledge, CHƯA calibrate bằng dữ liệu thật**. Chúng đủ tốt để cơ chế chạy và quan sát được,
 * nhưng không được coi là kết luận. Cách calibrate đúng: gom decision log, gán nhãn cụm bằng
 * phương pháp ở doc 33 (HMM/clustering có kiểm định ổn định), rồi ước lượng lại các `mu` này
 * từ dữ liệu thực.
 */
internal object LikelihoodEstimator {

    /** Số cơ hội rewarded tối thiểu để tỉ lệ chấp nhận có ý nghĩa thống kê. */
    private const val MIN_REWARDED_SUPPORT = 3

    /** Số session tối thiểu để tín hiệu kỹ năng/tiến trình có ý nghĩa. */
    private const val MIN_SESSION_SUPPORT = 2

    /**
     * Làm mềm cập nhật: 1.0 = tin tuyệt đối một quan sát; càng nhỏ càng trôi chậm.
     *
     * Giá trị 0.25 được chọn bằng cách tính ngược: với một quan sát CỰC ĐOAN (fail rate 95%,
     * nghỉ 10 ngày), posterior đi từ prior 0.25 lên ~0.59 — đủ nhạy để phản ứng, nhưng chưa
     * đủ để kết luận chỉ từ một lần nhìn. Ở mức 0.35 con số này là ~0.71, tức gần như lật
     * ngược ngay lập tức, mâu thuẫn với mục đích damping.
     */
    private const val DAMPING = 0.25

    private const val MIN_LIKELIHOOD = 0.02

    /** Hồ sơ kỳ vọng của mỗi latent type. Đây là các `mu` cần được calibrate lại từ dữ liệu. */
    private data class Profile(
        val failRate: Double,
        val rewardedAcceptRate: Double,
        val payerPropensity: Double,
        val churnTendency: Double
    )

    /**
     * Hồ sơ đang có hiệu lực: ưu tiên giá trị calibrate từ policy artifact, không có thì dùng
     * mặc định biên dịch trong code. Nhờ đó có thể hiệu chỉnh lại segment từ xa khi đã thu
     * được dữ liệu thật, mà không cần release app (doc 33: hằng số hiện tại chưa calibrate).
     */
    private fun profileOf(type: LatentPlayerType): Profile {
        BrainPolicy.profileFor(type)?.let {
            return Profile(it.failRate, it.rewardedAcceptRate, it.payerPropensity, it.churnTendency)
        }
        return defaultProfiles[type]!!
    }

    private val defaultProfiles: Map<LatentPlayerType, Profile> = mapOf(
        LatentPlayerType.HIGH_SKILL_LOW_TOLERANCE to Profile(
            failRate = 0.15, rewardedAcceptRate = 0.20, payerPropensity = 0.70, churnTendency = 0.20
        ),
        LatentPlayerType.HIGH_SKILL_HIGH_TOLERANCE to Profile(
            failRate = 0.15, rewardedAcceptRate = 0.75, payerPropensity = 0.20, churnTendency = 0.25
        ),
        LatentPlayerType.LOW_SKILL_HIGH_CHURN_RISK to Profile(
            failRate = 0.60, rewardedAcceptRate = 0.45, payerPropensity = 0.15, churnTendency = 0.80
        ),
        LatentPlayerType.CASUAL_STEADY to Profile(
            failRate = 0.35, rewardedAcceptRate = 0.50, payerPropensity = 0.35, churnTendency = 0.35
        )
    )

    /** Độ rộng Gaussian cho từng tín hiệu — càng lớn thì tín hiệu càng ít quyết đoán. */
    private const val SIGMA_FAIL = 0.25
    private const val SIGMA_REWARDED = 0.25
    private const val SIGMA_PAYER = 0.30
    private const val SIGMA_CHURN = 0.30

    private fun gaussianLikelihood(observed: Double, expected: Double, sigma: Double): Double {
        val d = observed - expected
        return exp(-(d * d) / (2.0 * sigma * sigma))
    }

    /**
     * Suy ra "xác suất là payer" quan sát được từ hành vi mua thực tế.
     * Không dùng `purchasePropensity` trong PlayerState vì trường đó do model khác điền —
     * dùng nó ở đây sẽ tạo vòng lặp phản hồi (model tin vào chính output của mình).
     */
    private fun observedPayerSignal(state: PlayerState): Double = when {
        state.purchaseCountLifetime >= 3 -> 0.90
        state.purchaseCountLifetime == 2 -> 0.75
        state.purchaseCountLifetime == 1 -> 0.60
        else -> 0.10
    }

    private fun observedChurnSignal(state: PlayerState): Double = when {
        state.daysSinceLastSession >= 7 -> 0.90
        state.daysSinceLastSession >= 3 -> 0.65
        state.daysSinceLastSession >= 1 -> 0.40
        else -> 0.20
    }

    /**
     * Trả về map likelihood để truyền thẳng vào `BeliefState.update()`.
     * Nếu KHÔNG tín hiệu nào đủ support, trả về map rỗng — caller nên bỏ qua lần cập nhật đó
     * thay vì cập nhật bằng dữ liệu vô nghĩa.
     */
    fun estimate(state: PlayerState): Map<LatentPlayerType, Double> {
        val useRewarded = state.rewardedOpportunitiesRecent >= MIN_REWARDED_SUPPORT
        val useSkill = state.sessionsLifetime >= MIN_SESSION_SUPPORT
        val usePayer = state.sessionsLifetime >= MIN_SESSION_SUPPORT
        val useChurn = state.sessionsLifetime >= MIN_SESSION_SUPPORT

        if (!useRewarded && !useSkill && !usePayer && !useChurn) return emptyMap()

        val payerSignal = observedPayerSignal(state)
        val churnSignal = observedChurnSignal(state)

        return LatentPlayerType.values().associateWith { type ->
            val p = profileOf(type)
            var likelihood = 1.0

            if (useSkill) {
                likelihood *= gaussianLikelihood(
                    state.failRateLast30m.coerceIn(0.0, 1.0), p.failRate, SIGMA_FAIL
                )
            }
            if (useRewarded) {
                likelihood *= gaussianLikelihood(
                    state.rewardedAcceptRateRecent.coerceIn(0.0, 1.0), p.rewardedAcceptRate, SIGMA_REWARDED
                )
            }
            if (usePayer) {
                likelihood *= gaussianLikelihood(payerSignal, p.payerPropensity, SIGMA_PAYER)
            }
            if (useChurn) {
                likelihood *= gaussianLikelihood(churnSignal, p.churnTendency, SIGMA_CHURN)
            }

            likelihood.pow(DAMPING).coerceAtLeast(MIN_LIKELIHOOD)
        }
    }

    /** Liệt kê tín hiệu nào đang thực sự được dùng — phục vụ debug view, để biết vì sao belief đứng yên. */
    fun activeSignals(state: PlayerState): List<String> {
        val signals = mutableListOf<String>()
        if (state.sessionsLifetime >= MIN_SESSION_SUPPORT) {
            signals.add("failRate=%.2f".format(state.failRateLast30m))
            signals.add("payer=%.2f".format(observedPayerSignal(state)))
            signals.add("churn=%.2f".format(observedChurnSignal(state)))
        }
        if (state.rewardedOpportunitiesRecent >= MIN_REWARDED_SUPPORT) {
            signals.add("rewardedAccept=%.2f".format(state.rewardedAcceptRateRecent))
        }
        if (signals.isEmpty()) signals.add("chưa đủ dữ liệu — belief giữ nguyên prior")
        return signals
    }
}
