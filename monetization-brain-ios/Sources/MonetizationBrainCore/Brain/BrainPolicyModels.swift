import Foundation

// Brain-policy value types. Flat top-level declarations rather than types nested in
// `BrainPolicy`, matching this codebase's existing convention (`CrmModels.swift`,
// `OfferwallModels.swift`, `RecurringGrantModels.swift`).

/// Outcome of applying a `brain_policy_ios` artifact, matching `CrmApplyResult`/
/// `OfferwallApplyResult`/`RecurringGrantsApplyResult`.
///
/// `apply(_:guardrails:)` never throws: a malformed artifact means the last-good one keeps
/// running, and `report` says why in a form an operator can read. `applied == true` with notes in
/// the report is the normal degraded case — a field that could not be read is dropped and its
/// knob left alone, while every other field in the same artifact still takes effect.
public struct PolicyApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String

    public init(applied: Bool, report: String) {
        self.applied = applied
        self.report = report
    }
}

/// A remote-calibrated expected profile for one `LatentPlayerType` — the `mu` values
/// `LikelihoodEstimator` measures an observation against.
///
/// Every value in `LikelihoodEstimator.defaultProfiles` is a domain-knowledge assumption, NOT a
/// measurement; the whole point of shipping these in an artifact is to replace them with numbers
/// estimated from real decision logs without waiting for an SDK release.
///
/// ═══ WHY THE SET IS ALL-OR-NOTHING (design spec §3) ═══
///
/// These four numbers are only meaningful RELATIVE to the other segments' four. A posterior is an
/// argmax across the segments, so mixing two newly calibrated profiles with two compiled ones
/// does not give "partially improved" personalization — it gives a comparison between numbers
/// estimated on different scales, and the winner becomes an artefact of the mix. `BrainPolicy`
/// therefore drops an incomplete `likelihood_profiles` block entirely rather than half-applying
/// it.
///
/// ═══ NOT YET READ BY `LikelihoodEstimator` ═══
///
/// `LikelihoodEstimator.profileOf` still returns its compiled default unconditionally. Wiring the
/// override in changes what the decision path computes for every player, which is a separate
/// change with its own regression surface — the same Phase-N / Phase-N.5 split this port has used
/// throughout. `profileFor(_:)` is the accessor that wiring will call.
public struct LikelihoodProfile: Sendable, Equatable {
    public let failRate: Double
    public let rewardedAcceptRate: Double
    public let payerPropensity: Double
    public let churnTendency: Double

    public init(failRate: Double, rewardedAcceptRate: Double, payerPropensity: Double, churnTendency: Double) {
        self.failRate = failRate
        self.rewardedAcceptRate = rewardedAcceptRate
        self.payerPropensity = payerPropensity
        self.churnTendency = churnTendency
    }
}
