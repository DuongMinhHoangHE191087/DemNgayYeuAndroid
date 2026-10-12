package com.app.plugin.brain

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Contextual bandit tối giản: Thompson Sampling Beta-Bernoulli, keyed theo (segmentKey, armId).
 * "Contextual" ở mức segment-key ghép từ decision point + belief-state type — KHÔNG phải
 * full contextual regression. Đây là lựa chọn cố ý theo doc 24 mục G: bắt đầu bằng
 * segment-keyed MAB, chỉ nâng cấp lên linear/tree contextual bandit khi đủ traffic per-segment.
 *
 * Không dùng thư viện xác suất ngoài — Beta sample được suy ra từ 2 mẫu Gamma
 * (Marsaglia-Tsang), Gamma suy từ Gaussian (Box-Muller), tất cả bằng kotlin.random thuần túy.
 */
internal class BanditPolicy {

    private data class BetaParams(var alpha: Double = 1.0, var beta: Double = 1.0)

    private val arms: MutableMap<String, BetaParams> = mutableMapOf()

    /**
     * Sàn khám phá (epsilon floor) — hạ tầng BẮT BUỘC, không phải tinh chỉnh thuật toán.
     *
     * Lý do: khi Thompson Sampling đã hội tụ, nó chọn cùng một arm gần như 100% số lần và
     * propensity log ra xấp xỉ 1.0. Lúc đó **mọi off-policy evaluation trở nên vô dụng** —
     * không có overlap thì importance weight của mọi policy khác đều bằng 0 hoặc vô cực
     * (doc 31 mục 5, doc 28 mục H).
     *
     * Với xác suất `explorationFloor`, chọn đều ngẫu nhiên trong các arm hợp lệ. Propensity
     * cuối cùng vì vậy luôn ≥ `explorationFloor / n_arms` > 0, đảm bảo OPE luôn khả thi.
     *
     * 0.02–0.05 là khoảng thông dụng. Đặt 0.0 sẽ tắt OPE — chỉ làm khi biết rõ hệ quả.
     */
    public var explorationFloor: Double = 0.03

    private fun key(segmentKey: String, armId: String) = "$segmentKey|$armId"

    /**
     * Cập nhật posterior CÓ TRỌNG SỐ theo belief (doc 33 mục 9).
     *
     * `weight` là b_t(k) — xác suất hậu nghiệm rằng player thuộc latent type k. Gán cứng vào
     * `mostLikelyType()` rồi chỉ cập nhật segment đó là sai: khi belief dịch chuyển, lịch sử
     * học được ở segment cũ bị bỏ phí, và segment mới khởi động lại từ prior. Cập nhật mềm
     * (soft/EM-style) phân bổ bằng chứng cho mọi segment theo đúng mức độ tin tưởng.
     *
     * Beta-Bernoulli chấp nhận cập nhật phân số một cách tự nhiên: alpha/beta là sufficient
     * statistics, cộng số thực vẫn giữ đúng dạng hàm.
     */
    public fun update(segmentKey: String, armId: String, reward01: Double, weight: Double = 1.0) {
        if (weight <= 0.0) return
        val p = arms.getOrPut(key(segmentKey, armId)) { BetaParams() }
        val r = reward01.coerceIn(0.0, 1.0)
        val w = weight.coerceIn(0.0, 1.0)
        p.alpha += w * r
        p.beta += w * (1.0 - r)
    }

    /**
     * Thompson Sampling trên HỖN HỢP belief — lấy mẫu đồng thời cả hai nguồn bất định:
     * (1) player thuộc latent type nào, (2) giá trị thật của mỗi arm là bao nhiêu.
     *
     * Mỗi vòng Monte Carlo: rút một segment theo trọng số belief, rồi rút Beta cho từng arm
     * TỪ CHÍNH segment đó (dùng chung một segment trong một vòng — vì player thực sự chỉ
     * thuộc một type, ta chỉ không biết type nào). Tần suất thắng cho ra xác suất chọn dưới
     * policy hỗn hợp — đây chính là propensity đúng để ghi vào decision log cho OPE
     * (doc 24 mục K, doc 28 mục H).
     *
     * @param segmentWeights map segmentKey → belief weight; tổng nên xấp xỉ 1.0.
     */
    public fun selectArm(
        segmentWeights: Map<String, Double>,
        candidateArmIds: List<String>,
        mcSamples: Int = 200,
        penalties: Map<String, Double> = emptyMap()
    ): Pair<String, Double> {
        require(candidateArmIds.isNotEmpty()) { "candidateArmIds không được rỗng" }
        require(segmentWeights.isNotEmpty()) { "segmentWeights không được rỗng" }
        if (candidateArmIds.size == 1) return candidateArmIds[0] to 1.0

        val segments = segmentWeights.keys.toList()
        val cumulative = DoubleArray(segments.size)
        var running = 0.0
        for (i in segments.indices) {
            running += (segmentWeights[segments[i]] ?: 0.0).coerceAtLeast(0.0)
            cumulative[i] = running
        }
        require(running > 0.0) { "tổng trọng số segment phải dương" }

        val winCounts = HashMap<String, Int>().apply { candidateArmIds.forEach { put(it, 0) } }
        repeat(mcSamples) {
            // Rút một latent segment theo belief.
            val u = Random.nextDouble() * running
            var segIdx = cumulative.indexOfFirst { it >= u }
            if (segIdx < 0) segIdx = segments.size - 1
            val segmentKey = segments[segIdx]

            var bestArm = candidateArmIds[0]
            var bestSample = -Double.MAX_VALUE
            for (armId in candidateArmIds) {
                val p = arms.getOrPut(key(segmentKey, armId)) { BetaParams() }
                // Reward hiệu chỉnh Lagrangian: r~(a) = r(a) − Σ lambda_j·c_j(a).
                // Trừ TRƯỚC khi so sánh để giá bóng thực sự tham gia vào lựa chọn,
                // thay vì chặn cứng sau khi đã chọn (doc 28 mục E.1).
                val sample = sampleBeta(p.alpha, p.beta) - (penalties[armId] ?: 0.0)
                if (sample > bestSample) {
                    bestSample = sample
                    bestArm = armId
                }
            }
            winCounts[bestArm] = (winCounts[bestArm] ?: 0) + 1
        }

        val eps = explorationFloor.coerceIn(0.0, 1.0)
        val n = candidateArmIds.size

        // Trộn với phân phối đều: p_final(a) = (1-eps)*p_TS(a) + eps/n.
        // Rút thăm THEO CHÍNH phân phối đã trộn, để action thực sự chọn khớp với
        // propensity đã log — nếu chọn theo TS rồi mới log xác suất trộn thì log sai.
        val mixed = candidateArmIds.associateWith { armId ->
            val pts = (winCounts[armId] ?: 0).toDouble() / mcSamples
            (1.0 - eps) * pts + eps / n
        }

        var draw = Random.nextDouble()
        var chosenArm = candidateArmIds.last()
        for (armId in candidateArmIds) {
            draw -= (mixed[armId] ?: 0.0)
            if (draw <= 0.0) {
                chosenArm = armId
                break
            }
        }
        return chosenArm to (mixed[chosenArm] ?: (eps / n))
    }

    /**
     * Kết xuất toàn bộ posterior để lưu: "segmentKey|armId=alpha,beta;..."
     *
     * Không có persistence, bandit quên sạch mỗi lần app khởi động lại — trong khi belief thì
     * được lưu. Sự lệch pha đó khiến kết quả test không tái lập được và rất khó chẩn đoán.
     */
    public fun serialize(): String =
        arms.entries.joinToString(";") { (k, v) -> "$k=${v.alpha},${v.beta}" }

    /** Khôi phục posterior. Dữ liệu hỏng → bỏ qua bản ghi đó, không ném exception (fail-safe). */
    public fun restore(raw: String?) {
        if (raw.isNullOrBlank()) return
        try {
            for (entry in raw.split(";")) {
                val eq = entry.lastIndexOf('=')
                if (eq <= 0) continue
                val armKey = entry.substring(0, eq)
                val nums = entry.substring(eq + 1).split(",")
                if (nums.size != 2) continue
                val a = nums[0].toDoubleOrNull() ?: continue
                val b = nums[1].toDoubleOrNull() ?: continue
                if (a <= 0.0 || b <= 0.0) continue
                arms[armKey] = BetaParams(a, b)
            }
        } catch (e: Exception) {
            arms.clear()
        }
    }

    /** Số lần quan sát đã tích luỹ cho một arm (alpha+beta-2, trừ đi prior Beta(1,1)). */
    public fun observationCount(segmentKey: String, armId: String): Double {
        val p = arms[key(segmentKey, armId)] ?: return 0.0
        return (p.alpha + p.beta - 2.0).coerceAtLeast(0.0)
    }

    // --- Beta sampling qua 2 biến Gamma ---

    private fun sampleBeta(alpha: Double, beta: Double): Double {
        val x = sampleGamma(alpha)
        val y = sampleGamma(beta)
        return if (x + y <= 0.0) 0.5 else x / (x + y)
    }

    /** Marsaglia-Tsang method, hợp lệ cho shape > 0. */
    private fun sampleGamma(shape: Double): Double {
        if (shape < 1.0) {
            val u = Random.nextDouble()
            return sampleGamma(1.0 + shape) * u.pow(1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            var x: Double
            var v: Double
            do {
                x = gaussian()
                v = 1.0 + c * x
            } while (v <= 0.0)
            v *= v * v
            val u = Random.nextDouble()
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v
            if (ln(u) < 0.5 * x * x + d * (1.0 - v + ln(v))) return d * v
        }
    }

    private fun gaussian(): Double {
        // Box-Muller
        val u1 = Random.nextDouble().coerceAtLeast(1e-12)
        val u2 = Random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }
}
