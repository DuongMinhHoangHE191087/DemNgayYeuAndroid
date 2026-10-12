/// The candidate action space for one `DecisionEngine.decide(...)` call, and the request/response
/// shapes that carry it. Mirrors Android's `DecisionModels.kt` (`ActionCandidate`/
/// `DecisionRequest`/`DecisionResponse`) — `DecisionPoint`/`ActionMode` are already split into
/// their own files from Phase 1.

/// One candidate action `DecisionEngine.decide(...)` may choose. The caller (host app / business
/// rules) supplies the candidate set; `DecisionEngine` never synthesizes SKUs, offers, or
/// creatives on its own.
public struct ActionCandidate: Sendable, Equatable {
    public let mode: ActionMode
    public let actionId: String

    /// Marks this as the deterministic pre-brain behavior ("just show the ad, static rule"). The
    /// holdout (control) group always receives this candidate — that's what makes a
    /// treatment-vs-control comparison meaningful: control is exactly what the app did before
    /// this SDK existed. If no candidate is marked, holdout falls back to `.noAction`.
    public let isBaseline: Bool

    public init(mode: ActionMode, actionId: String, isBaseline: Bool = false) {
        self.mode = mode
        self.actionId = actionId
        self.isBaseline = isBaseline
    }
}

/// One `decide(...)` call's inputs. No `context: [String: Any]` field — Android's is never read
/// by `DecisionEngine.decide()`, and an unconstrained `Any` map can't satisfy Swift's `Sendable`
/// checking. Add a concrete, `Sendable` field here if a real use appears in a later phase.
public struct DecisionRequest: Sendable, Equatable {
    public let playerId: String
    public let decisionPoint: DecisionPoint

    public init(playerId: String, decisionPoint: DecisionPoint) {
        self.playerId = playerId
        self.decisionPoint = decisionPoint
    }
}

/// The result of one `decide(...)` call — everything needed both to act on the decision and to
/// reconstruct it later for off-policy evaluation.
public struct DecisionResponse: Sendable, Equatable {
    public let decisionId: String
    public let chosenAction: ActionCandidate

    /// Probability of choosing `chosenAction` under the CURRENT policy. Required for OPE.
    /// Conditioned on `eligibleActionIds` (the post-guardrail set), NOT the original candidate
    /// list — any OPE estimator reading the log must condition on the same set, or importance
    /// weights are wrong.
    public let chosenActionProbability: Double
    public let policyVersion: String

    /// Guardrail codes that fired (one per candidate removed).
    public let guardrailsApplied: [String]

    /// The candidate set AFTER guardrails — what the bandit actually chose among. Without this,
    /// the decision can't be reconstructed for OPE.
    public let eligibleActionIds: [String]

    public let segment: LatentPlayerType?
    public let segmentConfidence: Double
    public let scenario: PersonalizationScenario?

    /// Whether this player is in the control group (not personalized). Required in the log so
    /// treatment can be separated from control during analysis.
    public let isHoldout: Bool

    public init(
        decisionId: String,
        chosenAction: ActionCandidate,
        chosenActionProbability: Double,
        policyVersion: String,
        guardrailsApplied: [String] = [],
        eligibleActionIds: [String] = [],
        segment: LatentPlayerType? = nil,
        segmentConfidence: Double = 0.0,
        scenario: PersonalizationScenario? = nil,
        isHoldout: Bool = false
    ) {
        self.decisionId = decisionId
        self.chosenAction = chosenAction
        self.chosenActionProbability = chosenActionProbability
        self.policyVersion = policyVersion
        self.guardrailsApplied = guardrailsApplied
        self.eligibleActionIds = eligibleActionIds
        self.segment = segment
        self.segmentConfidence = segmentConfidence
        self.scenario = scenario
        self.isHoldout = isHoldout
    }
}
