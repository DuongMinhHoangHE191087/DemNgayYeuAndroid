package com.app.plugin.brain

import com.app.plugin.license.LicenseGate

/**
 * Facade DECIDE/FEEDBACK (doc 06 mục 9-10) — entrypoint DUY NHẤT mà code ngoài package brain
 * nên gọi; không gọi trực tiếp BanditPolicy/GuardrailEngine/DecisionLogger từ bên ngoài.
 *
 * Vai trò trong roadmap (doc 23 mục D): xương sống của Phase 1-3. Ở Phase 0, có thể dùng
 * chính DecisionEngine này với đúng 1 candidate cố định (policy rule-based thuần túy) trước
 * khi truyền vào nhiều candidate để bandit thật sự ranking — nhờ vậy integration được chuẩn
 * hóa trước khi thêm ML, đúng nguyên tắc ở doc 07 ("không build ML trước khi API ổn định").
 *
 * QUAN TRỌNG: đây là bản skeleton kỹ thuật cho mục đích thiết kế/tham chiếu, chưa được wire
 * vào luồng ads hiện có (AdsPlacementConfig/AdsPlacementConfigManager) — xem doc 25 mục cho
 * ví dụ minh họa cách một call site tích hợp sẽ trông như thế nào.
 */
public class DecisionEngine(
    private val policyVersion: String = "brain_v0_skeleton"
) {
    internal val bandit = BanditPolicy()
    internal val guardrails = GuardrailEngine()
    private val beliefStates: MutableMap<String, BeliefState> = mutableMapOf()
    private val budgets: MutableMap<String, LagrangianBudget> = mutableMapOf()

    /**
     * Giá bóng tài nguyên của player. Mỗi player có ngân sách riêng vì áp lực quảng cáo
     * là trải nghiệm cá nhân — dùng chung một lambda toàn cục sẽ khiến hành vi của người
     * này ảnh hưởng tới người kia (đúng lỗi share-state đã tránh ở doc 27 mục B.2).
     */
    public fun budgetFor(playerId: String): LagrangianBudget =
        budgets.getOrPut(playerId) { LagrangianBudget() }

    /**
     * Ước lượng số decision point mỗi phiên, dùng để quy đổi trần ngân sách thành mức
     * cho phép trên mỗi quyết định (B/T trong công thức BwK). Giá trị thiết kế, chưa đo.
     */
    public var estimatedDecisionsPerSession: Int = 30

    init {
        // Khôi phục thống kê bandit đã học ở các phiên trước. Thiếu bước này thì bandit
        // quên sạch mỗi lần khởi động app, trong khi belief lại được lưu — lệch pha giữa
        // hai lớp khiến kết quả không tái lập được.
        bandit.restore(BrainStorage.loadBanditStats())

        // Nối decision log vào pipeline sẵn có ngay từ đầu. Không để tích hợp viên phải
        // tự nhớ đăng ký: quên một lần là mất vĩnh viễn dữ liệu của giai đoạn đó.
        DecisionLogger.installDefaultSink()
        // Artifact được chấp nhận lần cuối (RC hay harness) áp lại TRƯỚC quyết định đầu tiên: kill switch và rollout 0
        // giữ từ cold start tới khi RC nói khác (1.9.0 T5s, D7). Prefs đã nạp ở `AppPluginBase.onCreate`: đọc bộ nhớ.
        BrainPolicyStore.restore(::applyAndSync)
    }

    /**
     * Lấy belief, khôi phục từ storage nếu chưa có trong bộ nhớ. Không có bước khôi phục này,
     * mọi player sẽ reset về prior mỗi lần mở app — tức vĩnh viễn là "người mới" và cá nhân hóa
     * không bao giờ tích lũy được.
     */
    public fun beliefFor(playerId: String): BeliefState =
        beliefStates.getOrPut(playerId) {
            BeliefState.deserialize(playerId, BrainStorage.loadBelief(playerId))
        }

    /**
     * Nạp quan sát mới vào belief — ĐÂY là chỗ segment thực sự chuyển động.
     * Gọi mỗi khi player state có thay đổi đáng kể (kết thúc màn, xem/từ chối rewarded, mua hàng).
     *
     * @return true nếu belief đã được cập nhật; false nếu chưa đủ dữ liệu để kết luận gì.
     */
    public fun observe(state: PlayerState): Boolean {
        // GDPR coi personalization là profiling (doc 13 mục E), nên việc TÍCH LUỸ hồ sơ hành
        // vi cần sự đồng ý. Chặn ở đây chứ không ở nơi dùng hồ sơ: dữ liệu không được phép
        // thu thì không được ghi xuống, chứ không phải ghi rồi tránh đọc. Không có Context để đọc
        // IABTCF_gdprApplies: bản không tham số, UNKNOWN là chưa được cấp (#7, DOC/66).
        if (!com.app.plugin.consent.ConsentState.allowsProfiling()) return false
        val likelihood = LikelihoodEstimator.estimate(state)
        if (likelihood.isEmpty()) return false
        val belief = beliefFor(state.playerId)
        belief.update(likelihood)
        BrainStorage.saveBelief(state.playerId, belief.serialize())
        return true
    }

    /** Kịch bản personalization đang áp dụng cho player này. */
    public fun scenarioFor(playerId: String): PersonalizationScenario =
        PersonalizationScenario.resolve(beliefFor(playerId))

    /**
     * Nạp policy artifact (doc 35). Đây là API công khai để tích hợp viên tự nạp artifact
     * từ nguồn riêng; luồng Remote Config mặc định đã tự gọi trong `FirHelperConfig`. Bản được CHẤP NHẬN được lưu
     * ([BrainPolicyStore], `apply()`) cho lần mở sau; bản bị từ chối không bao giờ được lưu.
     */
    public fun applyPolicy(json: String?): BrainPolicy.ApplyResult {
        val result = applyAndSync(json)
        if (result.applied && json != null) BrainPolicyStore.save(json)
        return result
    }

    private fun applyAndSync(json: String?): BrainPolicy.ApplyResult =
        BrainPolicy.apply(json, guardrails).also { BrainPolicy.pendingExplorationFloor?.let { bandit.explorationFloor = it } }

    /**
     * Số quan sát bandit đã tích luỹ cho một (segment, arm). Dùng để chẩn đoán/hiển thị —
     * mở ra public thay vì để lộ cả `BanditPolicy`, tránh cho phép ghi từ bên ngoài.
     */
    public fun observationCount(decisionPoint: DecisionPoint, segment: LatentPlayerType, armId: String): Double =
        bandit.observationCount("${decisionPoint}_$segment", armId)

    /** Trần interstitial đang thực thi cho player này, sau khi hợp nhất guardrail và kịch bản. */
    public fun effectiveInterstitialCap(playerId: String): Int =
        guardrails.effectiveInterstitialCap(scenarioFor(playerId))

    /**
     * Xoá toàn bộ trạng thái đã học của một player: cả bộ nhớ lẫn storage.
     *
     * Phải xoá CẢ HAI. Chỉ xoá storage là lỗi tinh vi hay gặp khi test — engine là singleton
     * nên belief cũ vẫn nằm trong `beliefStates`, và người test tưởng đã reset trong khi
     * hệ thống vẫn dùng dữ liệu cũ.
     */
    public fun resetPlayer(playerId: String) {
        beliefStates.remove(playerId)
        budgets.remove(playerId)
        BrainStorage.clearBelief(playerId)
    }

    /** Bắt đầu phiên mới: giá bóng quay về mức khởi đầu, ngân sách phiên được cấp lại. */
    public fun startNewSession(playerId: String) {
        budgetFor(playerId).reset()
    }

    /**
     * DECIDE: nhận context + candidate hợp lệ, trả về action đã qua guardrail + bandit ranking.
     * candidates phải do CALLER cung cấp theo business rule (doc 06 mục 12: business
     * constraints TRƯỚC, ML ranking SAU) — DecisionEngine không tự sinh SKU/offer/creative.
     * candidates PHẢI luôn chứa ít nhất 1 action NO_ACTION (doc 24 mục M).
     * Giấy phép chưa VALID: NO_ACTION đầu tiên, `guardrailsApplied = [license_<nhãn>]`, không decision log (2.2.0 LIC).
     */
    public fun decide(
        request: DecisionRequest,
        state: PlayerState,
        candidates: List<ActionCandidate>,
        adsShownThisSession: Int = 0,
        minutesSinceLastInterstitial: Int = Int.MAX_VALUE
    ): DecisionResponse {
        require(candidates.isNotEmpty()) { "candidates không được rỗng" }
        require(candidates.any { it.mode == ActionMode.NO_ACTION }) {
            "candidates phải luôn chứa NO_ACTION (doc 24 mục M)"
        }
        // 2.2.0 LIC (D3/D16-220): chưa VALID (INVALID hay PENDING) → NO_ACTION đầu tiên, p = 1; 0 bandit/log/inspector.
        val lic = LicenseGate.verdict   // đọc MỘT lần (D28-220): nhãn luôn khớp quyết định, kể cả khi PENDING → VALID giữa chừng
        if (lic !is LicenseGate.Verdict.Valid) return candidates.first { it.mode == ActionMode.NO_ACTION }.let { no ->
            val label = if (lic is LicenseGate.Verdict.Invalid) "INVALID:${lic.reason}" else "PENDING"   // = LicenseGate.label
            DecisionResponse("d_${request.playerId}_unlicensed", no, 1.0, policyVersion, listOf("license_$label"), listOf(no.actionId))
        }

        // Nhóm đối chứng: KHÔNG personalize, nhận kịch bản trung tính. Guardrail vẫn áp
        // bình thường — holdout là để đo giá trị của personalization, không phải để bỏ
        // an toàn. Một nhóm control bị hại vì thiếu guardrail sẽ làm sai lệch phép so sánh
        // theo hướng có lợi cho brain một cách giả tạo.
        val isHoldout = HoldoutManager.isHoldout(request.playerId)

        // Kịch bản phải được xác định TRƯỚC guardrail, vì nó siết trần tài nguyên.
        val scenario = if (isHoldout) PersonalizationScenario.BALANCED_HYBRID
                       else scenarioFor(request.playerId)

        val guardrailsApplied = mutableListOf<String>()
        val safeCandidates = candidates.filter { candidate ->
            val violation = guardrails.evaluate(
                candidate, state, adsShownThisSession, minutesSinceLastInterstitial, scenario,
                // Bối cảnh cần cho ngưỡng riêng của nhánh THUA — cùng một cơ hội interstitial
                // nhưng sau khi thua thì chi phí retention cao hơn hẳn.
                decisionPoint = request.decisionPoint
            )
            if (violation != null) {
                guardrailsApplied.add(violation.code)
                false
            } else true
        }

        // Guardrail có thể loại hết mọi candidate thật; luôn còn lại NO_ACTION làm fallback an toàn.
        val finalCandidates = safeCandidates.ifEmpty {
            listOf(candidates.first { it.mode == ActionMode.NO_ACTION })
        }

        val belief = beliefFor(request.playerId)
        val armIds = finalCandidates.map { it.actionId }

        val chosen: ActionCandidate
        val probability: Double
        if (isHoldout) {
            // Control: hành vi tất định như trước khi có brain — không bandit, không khám phá.
            // Propensity = 1.0 vì không có lựa chọn ngẫu nhiên nào được thực hiện.
            chosen = finalCandidates.firstOrNull { it.isBaseline }
                ?: finalCandidates.first { it.mode == ActionMode.NO_ACTION }
            probability = 1.0
        } else {
            val segmentWeights = segmentWeightsFor(request.decisionPoint, belief)
            // Giá bóng Lagrangian: điều tiết nhịp BÊN TRONG ngân sách một cách trơn tru,
            // thay vì để bandit chọn tự do rồi đụng trần cứng và im bặt (doc 28 mục E.1).
            val budget = budgetFor(request.playerId)
            val penalties = finalCandidates.associate { it.actionId to budget.penaltyFor(it.mode) }
            val (armId, p) = bandit.selectArm(segmentWeights, armIds, penalties = penalties)
            chosen = finalCandidates.first { it.actionId == armId }
            probability = p

            // Cập nhật dual sau khi đã chọn: tiêu nhanh hơn ngân sách thì lambda tăng.
            val cap = guardrails.effectiveInterstitialCap(scenario)
            budget.observe(chosen.mode, cap.toDouble() / estimatedDecisionsPerSession.coerceAtLeast(1))
        }

        val response = DecisionResponse(
            decisionId = DecisionLogger.nextDecisionId(request.playerId),
            chosenAction = chosen,
            chosenActionProbability = probability,
            policyVersion = policyVersion,
            guardrailsApplied = guardrailsApplied,
            eligibleActionIds = armIds,
            segment = belief.mostLikelyType(),
            segmentConfidence = belief.confidence(),
            scenario = scenario,
            isHoldout = isHoldout
        )
        DecisionLogger.log(request, response)
        BrainInspector.recordDecision(request, response, state)
        return response
    }

    private fun licensed(): Boolean = LicenseGate.verdict is LicenseGate.Verdict.Valid   // private: dump Δ 0 (D16-220)

    /**
     * Ánh xạ belief posterior thành trọng số segment cho bandit (doc 33 mục 9).
     * Mỗi latent type là một segment riêng; trọng số chính là b_t(k).
     */
    private fun segmentWeightsFor(decisionPoint: DecisionPoint, belief: BeliefState): Map<String, Double> =
        LatentPlayerType.values().associate { type ->
            "${decisionPoint}_$type" to (belief.posterior[type] ?: 0.0)
        }.filterValues { it > 0.0 }

    /**
     * FEEDBACK: cập nhật bandit posterior (doc 06 mục 10), phân bổ CÓ TRỌNG SỐ theo belief
     * thay vì dồn hết vào segment khả dĩ nhất (doc 33 mục 9) — nhờ vậy bằng chứng không bị
     * mất khi belief của player dịch chuyển sang type khác.
     *
     * LƯU Ý: `outcomeReward01` phải do caller tính. Công thức reward đầy đủ (doc 11) cần
     * LTV/churn model chưa tồn tại — xem doc 25 mục C.
     */
    public fun feedback(
        request: DecisionRequest,
        response: DecisionResponse,
        outcomeReward01: Double
    ) {
        // 2.2.0 LIC (D28-220): quyết định bị chặn (`license_*`) không bao giờ được học hay ghi `brain_feedback`, kể cả khi đã VALID.
        if (!licensed() || response.guardrailsApplied.any { it.startsWith("license_") }) return
        // Outcome của nhóm đối chứng KHÔNG được đưa vào bandit. Quyết định của họ do
        // luật tất định sinh ra, không phải do bandit — nạp vào sẽ làm lệch posterior
        // bằng chính dữ liệu lẽ ra phải độc lập để so sánh. Vẫn ghi log đầy đủ để
        // phân tích incrementality ở phía backend.
        if (!response.isHoldout) {
            val belief = beliefFor(request.playerId)
            val segmentWeights = segmentWeightsFor(request.decisionPoint, belief)
            for ((segmentKey, weight) in segmentWeights) {
                bandit.update(segmentKey, response.chosenAction.actionId, outcomeReward01, weight)
            }
            BrainStorage.saveBanditStats(bandit.serialize())
        }
        DecisionLogger.feedback(
            response.decisionId, "outcome_reward", System.currentTimeMillis(), outcomeReward01
        )
    }

    public companion object {
        public val instance: DecisionEngine by lazy { DecisionEngine() }
    }
}
