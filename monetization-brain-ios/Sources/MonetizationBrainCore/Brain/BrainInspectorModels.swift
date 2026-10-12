import Foundation

/// A belief, read as VALUES.
///
/// `DecisionEngine.beliefFor` returns the live `BeliefState` object that `decide` and `observe`
/// mutate, and `BeliefState.update` is public — so anything holding that reference could move a
/// player's belief without going through `observe`, skipping its gates and its persistence. The
/// inspector must not be the thing that does that, so it reads this instead: four numbers taken
/// once, with no way back to the object.
public struct BeliefReading: Sendable, Equatable {
    public let posterior: [LatentPlayerType: Double]
    public let mostLikelyType: LatentPlayerType
    public let confidence: Double
    public let isStillPrior: Bool

    public init(
        posterior: [LatentPlayerType: Double],
        mostLikelyType: LatentPlayerType,
        confidence: Double,
        isStillPrior: Bool
    ) {
        self.posterior = posterior
        self.mostLikelyType = mostLikelyType
        self.confidence = confidence
        self.isStillPrior = isStillPrior
    }
}

/// One decision as the inspector remembers it — a flattened `DecisionResponse` plus the moment it
/// happened, kept small because thirty of these live in memory for the whole session.
public struct BrainDecisionRecord: Sendable, Equatable {
    public let timestampMs: Int64
    public let decisionId: String
    public let decisionPoint: DecisionPoint
    public let chosenActionId: String
    public let chosenMode: ActionMode
    public let probability: Double
    public let segment: LatentPlayerType?
    public let segmentConfidence: Double
    public let scenario: PersonalizationScenario?
    public let guardrailsApplied: [String]
    /// Candidates that survived the guardrails — the arms the bandit actually got to choose from.
    public let eligibleCount: Int
    /// Candidates the caller supplied, before guardrails.
    ///
    /// ═══ WHY THIS IS PASSED IN RATHER THAN DERIVED THE WAY ANDROID DERIVES IT ═══
    ///
    /// Android computes it as `eligibleActionIds.size + guardrailsApplied.size`. That looks wrong
    /// — `guardrailsApplied` holds rule CODES, not candidates — but it is not: both engines append
    /// exactly one code per blocked candidate (first violation only, then the candidate is
    /// dropped), so the sum equals the true count on every path reachable today. This is
    /// therefore NOT a bug fix, and calling it one would be a better story than the truth.
    ///
    /// It is a robustness choice. That derivation stays correct only while two unrelated facts
    /// hold: one code per blocked candidate, and `.noAction` never being blocked by any
    /// guardrail — the second is what keeps `decide`'s "guardrails removed everything" fallback
    /// from double-counting, since that path re-adds a candidate the codes already counted.
    /// `decide` has the real number in hand, so the record takes it and depends on neither.
    public let totalCandidates: Int

    public init(
        timestampMs: Int64,
        decisionId: String,
        decisionPoint: DecisionPoint,
        chosenActionId: String,
        chosenMode: ActionMode,
        probability: Double,
        segment: LatentPlayerType?,
        segmentConfidence: Double,
        scenario: PersonalizationScenario?,
        guardrailsApplied: [String],
        eligibleCount: Int,
        totalCandidates: Int
    ) {
        self.timestampMs = timestampMs
        self.decisionId = decisionId
        self.decisionPoint = decisionPoint
        self.chosenActionId = chosenActionId
        self.chosenMode = chosenMode
        self.probability = probability
        self.segment = segment
        self.segmentConfidence = segmentConfidence
        self.scenario = scenario
        self.guardrailsApplied = guardrailsApplied
        self.eligibleCount = eligibleCount
        self.totalCandidates = totalCandidates
    }
}

/// Everything the brain believes and is enforcing for ONE player, at the moment of the call.
public struct BrainSnapshot: Sendable, Equatable {
    public let playerId: String
    /// Every `LatentPlayerType`, including the ones at zero — a missing key and a zero posterior
    /// mean different things to someone reading a debug dump, and the dump should not make them
    /// guess which one they are looking at.
    public let posterior: [LatentPlayerType: Double]
    public let segment: LatentPlayerType
    public let confidence: Double
    public let isStillPrior: Bool
    public let scenario: PersonalizationScenario
    /// Whether `scenario` is a real conclusion or just the fallback.
    ///
    /// This flag is the difference between "this player IS balanced/hybrid" and "we don't know
    /// enough to say, so they get the neutral treatment". Both show the same scenario name, and
    /// reading the first when it is the second is how someone concludes the brain is doing
    /// nothing when it is in fact still learning.
    public let scenarioIsFallback: Bool
    public let activeSignals: [String]
    public let effectiveInterstitialCap: Int
    public let guardrailsPrevented: [String: Int]
    public let recentDecisions: [BrainDecisionRecord]
    public let policyVersion: String
    public let policyEnabled: Bool
    public let policyReport: String
    public let isHoldout: Bool
    public let holdoutBucket: Int
    public let explorationFloor: Double

    public init(
        playerId: String,
        posterior: [LatentPlayerType: Double],
        segment: LatentPlayerType,
        confidence: Double,
        isStillPrior: Bool,
        scenario: PersonalizationScenario,
        scenarioIsFallback: Bool,
        activeSignals: [String],
        effectiveInterstitialCap: Int,
        guardrailsPrevented: [String: Int],
        recentDecisions: [BrainDecisionRecord],
        policyVersion: String,
        policyEnabled: Bool,
        policyReport: String,
        isHoldout: Bool,
        holdoutBucket: Int,
        explorationFloor: Double
    ) {
        self.playerId = playerId
        self.posterior = posterior
        self.segment = segment
        self.confidence = confidence
        self.isStillPrior = isStillPrior
        self.scenario = scenario
        self.scenarioIsFallback = scenarioIsFallback
        self.activeSignals = activeSignals
        self.effectiveInterstitialCap = effectiveInterstitialCap
        self.guardrailsPrevented = guardrailsPrevented
        self.recentDecisions = recentDecisions
        self.policyVersion = policyVersion
        self.policyEnabled = policyEnabled
        self.policyReport = policyReport
        self.isHoldout = isHoldout
        self.holdoutBucket = holdoutBucket
        self.explorationFloor = explorationFloor
    }
}
