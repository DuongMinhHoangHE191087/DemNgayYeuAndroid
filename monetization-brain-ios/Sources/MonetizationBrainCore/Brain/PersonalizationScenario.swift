import Foundation

/// Personalization scenario — maps each latent segment to a concrete BUNDLE OF POLICY KNOBS.
///
/// Segment answers "who is this player"; scenario answers "so what do we do about it."
///
/// The numbers below are a PRIOR from domain knowledge, not yet calibrated with real data — a
/// rule of thumb. They're a starting point for the bandit to learn from, NOT a measured
/// conclusion. Once there's enough decision log volume, the bandit adjusts on its own and these
/// numbers become warm-start priors only.
public enum PersonalizationScenario: String, Sendable, CaseIterable {
    case protectAndConvert = "PROTECT_AND_CONVERT"
    case adForward = "AD_FORWARD"
    case retentionFirst = "RETENTION_FIRST"
    case balancedHybrid = "BALANCED_HYBRID"

    public var displayName: String {
        switch self {
        case .protectAndConvert: return "Protect & convert"
        case .adForward: return "Ad-forward"
        case .retentionFirst: return "Retention first"
        case .balancedHybrid: return "Balanced"
        }
    }

    public var rationale: String {
        switch self {
        case .protectAndConvert:
            return "High skill, low ad tolerance, has a payer signal -> fewer ads, prioritize IAP"
        case .adForward:
            return "High ad tolerance, low payer signal -> IAA is the main value source"
        case .retentionFirst:
            return "Many fails, high churn risk -> reduce all pressure, raise free value"
        case .balancedHybrid:
            return "Not enough signal to distinguish -> neutral policy, keep observing"
        }
    }

    /// Interstitial cap per session for this segment.
    public var interstitialCapPerSession: Int {
        switch self {
        case .protectAndConvert: return 2
        case .adForward: return 6
        case .retentionFirst: return 1
        case .balancedHybrid: return 3
        }
    }

    /// How proactively rewarded ads are offered (0 = only when the user seeks it out, 1 = every
    /// valid opportunity).
    public var rewardedAggressiveness: Double {
        switch self {
        case .protectAndConvert: return 0.2
        case .adForward: return 0.8
        case .retentionFirst: return 0.3
        case .balancedHybrid: return 0.5
        }
    }

    /// Priority given to IAP offers over ads (0 = never, 1 = absolute priority).
    public var iapOfferPriority: Double {
        switch self {
        case .protectAndConvert: return 0.9
        case .adForward: return 0.2
        case .retentionFirst: return 0.1
        case .balancedHybrid: return 0.5
        }
    }

    /// Generosity for FREE_ACTION — no-strings-attached value given to the player.
    public var freeValueGenerosity: Double {
        switch self {
        case .protectAndConvert: return 0.3
        case .adForward: return 0.2
        case .retentionFirst: return 0.9
        case .balancedHybrid: return 0.5
        }
    }

    /// Segment -> scenario. A deliberate 1:1 relationship: every segment has exactly one
    /// strategy.
    public static func forType(_ type: LatentPlayerType) -> PersonalizationScenario {
        switch type {
        case .highSkillLowTolerance: return .protectAndConvert
        case .highSkillHighTolerance: return .adForward
        case .lowSkillHighChurnRisk: return .retentionFirst
        case .casualSteady: return .balancedHybrid
        }
    }

    private static let _minConfidence = LockedValue(0.40)

    /// Minimum confidence required to apply a specialized scenario. Below this, belief is still
    /// too spread out to conclude anything — fall back to `.balancedHybrid` instead of guessing.
    /// Was remote-config-overridable via `BrainPolicy` on Android; fixed here (no remote policy
    /// artifact subsystem exists yet — a documented, deferred gap, see the Phase 3 design spec).
    public static var minConfidence: Double {
        get { _minConfidence.wrapped }
        set { _minConfidence.wrapped = newValue }
    }

    /// Resolves the scenario currently in effect for `belief`.
    public static func resolve(_ belief: BeliefState) -> PersonalizationScenario {
        // Personalization requires consent to USE an existing profile — the profile itself may
        // still be on disk, unaffected; only its use is blocked, matching the exact boundary the
        // regulation draws.
        guard ConsentState.shared.allowsPersonalization() else { return .balancedHybrid }
        guard belief.confidence() >= minConfidence else { return .balancedHybrid }
        return forType(belief.mostLikelyType())
    }
}
