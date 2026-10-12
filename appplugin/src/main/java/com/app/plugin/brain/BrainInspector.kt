package com.app.plugin.brain

/**
 * Cửa sổ quan sát trạng thái brain — phục vụ debug/QA và về sau là Control Plane (doc 26).
 *
 * Nguyên tắc: CHỈ ĐỌC. Inspector không bao giờ được tác động ngược vào quyết định, nếu không
 * hành vi khi bật debug sẽ khác hành vi production — đúng loại lỗi khiến QA mất niềm tin vào
 * công cụ. Toàn bộ ghi chép ở đây là hiệu ứng phụ, được bao try/catch, và có giới hạn bộ nhớ.
 */
public object BrainInspector {

    /** Số quyết định gần nhất được giữ lại. Cố định để không rò rỉ bộ nhớ trong phiên dài. */
    private const val MAX_HISTORY = 30

    public data class DecisionRecord(
        val timestampMs: Long,
        val decisionId: String,
        val decisionPoint: DecisionPoint,
        val chosenActionId: String,
        val chosenMode: ActionMode,
        val probability: Double,
        val segment: LatentPlayerType?,
        val segmentConfidence: Double,
        val scenario: PersonalizationScenario?,
        val guardrailsApplied: List<String>,
        val eligibleCount: Int,
        val totalCandidates: Int
    )

    private val history = ArrayDeque<DecisionRecord>()

    @Synchronized
    internal fun recordDecision(
        request: DecisionRequest,
        response: DecisionResponse,
        state: PlayerState
    ) {
        try {
            history.addLast(
                DecisionRecord(
                    timestampMs = System.currentTimeMillis(),
                    decisionId = response.decisionId,
                    decisionPoint = request.decisionPoint,
                    chosenActionId = response.chosenAction.actionId,
                    chosenMode = response.chosenAction.mode,
                    probability = response.chosenActionProbability,
                    segment = response.segment,
                    segmentConfidence = response.segmentConfidence,
                    scenario = response.scenario,
                    guardrailsApplied = response.guardrailsApplied,
                    eligibleCount = response.eligibleActionIds.size,
                    totalCandidates = response.eligibleActionIds.size + response.guardrailsApplied.size
                )
            )
            while (history.size > MAX_HISTORY) history.removeFirst()
        } catch (e: Exception) {
            // Debug không bao giờ được làm hỏng luồng quyết định.
        }
    }

    @Synchronized
    public fun recentDecisions(): List<DecisionRecord> = history.toList().asReversed()

    @Synchronized
    public fun clearHistory(): Unit = history.clear()

    /** Ảnh chụp toàn bộ trạng thái brain của một player tại thời điểm gọi. */
    public data class Snapshot(
        val playerId: String,
        val posterior: Map<LatentPlayerType, Double>,
        val segment: LatentPlayerType,
        val confidence: Double,
        val isStillPrior: Boolean,
        val scenario: PersonalizationScenario,
        val scenarioIsFallback: Boolean,
        val activeSignals: List<String>,
        val effectiveInterstitialCap: Int,
        val guardrailsPrevented: Map<String, Int>,
        val recentDecisions: List<DecisionRecord>,
        val policyVersion: String,
        val policyEnabled: Boolean,
        val policyReport: String,
        val isHoldout: Boolean,
        val holdoutBucket: Int,
        val explorationFloor: Double
    )

    public fun snapshot(engine: DecisionEngine, state: PlayerState): Snapshot {
        val belief = engine.beliefFor(state.playerId)
        val scenario = engine.scenarioFor(state.playerId)
        return Snapshot(
            playerId = state.playerId,
            posterior = LatentPlayerType.values().associateWith { belief.posterior[it] ?: 0.0 },
            segment = belief.mostLikelyType(),
            confidence = belief.confidence(),
            isStillPrior = belief.isStillPrior(),
            scenario = scenario,
            // Cờ này quan trọng khi đọc kết quả: BALANCED_HYBRID có thể là kết luận thật,
            // cũng có thể chỉ là fallback vì độ tin cậy chưa đủ. Hai trường hợp rất khác nhau.
            scenarioIsFallback = belief.confidence() < PersonalizationScenario.MIN_CONFIDENCE,
            activeSignals = LikelihoodEstimator.activeSignals(state),
            effectiveInterstitialCap = engine.guardrails.effectiveInterstitialCap(scenario),
            guardrailsPrevented = engine.guardrails.allPreventedCounts(),
            recentDecisions = recentDecisions(),
            policyVersion = BrainPolicy.activeVersion(),
            policyEnabled = BrainPolicy.isEnabled(),
            policyReport = BrainPolicy.lastApplyReport,
            isHoldout = HoldoutManager.isHoldout(state.playerId),
            holdoutBucket = HoldoutManager.bucket(state.playerId),
            explorationFloor = engine.bandit.explorationFloor
        )
    }

    /** Kết xuất dạng text — dùng cho Logcat, bug report, hoặc debug view. */
    public fun format(snapshot: Snapshot): String = buildString {
        appendLine("╔══ MONETIZATION BRAIN ══")
        appendLine("║ player: ${snapshot.playerId}")
        appendLine("║ policy: ${snapshot.policyVersion}" + if (!snapshot.policyEnabled) "  ⛔ KILL SWITCH BẬT" else "")
        appendLine(
            "║ nhóm  : " + (if (snapshot.isHoldout) "HOLDOUT (đối chứng — KHÔNG personalize)" else "treatment") +
                "  [bucket ${snapshot.holdoutBucket}/10000, explore ${"%.0f%%".format(snapshot.explorationFloor * 100)}]"
        )
        appendLine("╠══ SEGMENT ══")
        if (snapshot.isStillPrior) {
            appendLine("║ ⚠ CHƯA HỌC — belief vẫn ở prior đồng đều")
        }
        for ((type, p) in snapshot.posterior.entries.sortedByDescending { it.value }) {
            val bar = "█".repeat((p * 20).toInt().coerceIn(0, 20))
            val mark = if (type == snapshot.segment) "►" else " "
            appendLine("║$mark ${type.name.padEnd(26)} ${"%.3f".format(p)} $bar")
        }
        appendLine("║ tin cậy: ${"%.1f%%".format(snapshot.confidence * 100)}")
        appendLine("╠══ KỊCH BẢN ĐANG CHẠY ══")
        appendLine("║ ${snapshot.scenario.displayName}" + if (snapshot.scenarioIsFallback) "  (fallback: chưa đủ tin cậy)" else "")
        appendLine("║ lý do: ${snapshot.scenario.rationale}")
        appendLine("║ trần interstitial/phiên : ${snapshot.effectiveInterstitialCap}")
        appendLine("║ chủ động mời rewarded   : ${"%.1f".format(snapshot.scenario.rewardedAggressiveness)}")
        appendLine("║ ưu tiên IAP offer       : ${"%.1f".format(snapshot.scenario.iapOfferPriority)}")
        appendLine("║ hào phóng giá trị free  : ${"%.1f".format(snapshot.scenario.freeValueGenerosity)}")
        appendLine("╠══ TÍN HIỆU ĐANG DÙNG ══")
        snapshot.activeSignals.forEach { appendLine("║ • $it") }
        if (snapshot.guardrailsPrevented.isNotEmpty()) {
            appendLine("╠══ GUARDRAIL ĐÃ CHẶN ══")
            snapshot.guardrailsPrevented.entries.sortedByDescending { it.value }.forEach {
                appendLine("║ ${it.key}: ${it.value} lần")
            }
        }
        if (snapshot.recentDecisions.isNotEmpty()) {
            appendLine("╠══ QUYẾT ĐỊNH GẦN NHẤT ══")
            snapshot.recentDecisions.take(8).forEach { d ->
                appendLine(
                    "║ ${d.decisionPoint} → ${d.chosenMode} (${d.chosenActionId}) " +
                        "p=${"%.2f".format(d.probability)} " +
                        "[${d.eligibleCount}/${d.totalCandidates} arm]" +
                        if (d.guardrailsApplied.isEmpty()) "" else " ⛔${d.guardrailsApplied.joinToString(",")}"
                )
            }
        }
        appendLine("╚════════════════════════")
    }
}
