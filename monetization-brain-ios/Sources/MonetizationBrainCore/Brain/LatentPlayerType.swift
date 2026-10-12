/// Minimal belief state for doc 24 §B (POMDP): a discrete posterior over latent player type.
/// A deliberately practical starting point — discrete, not a full particle filter — sufficient
/// for roadmap Phases 1-3 (doc 23). Upgrading to a continuous/particle filter should only happen
/// once there's enough data volume (doc 24 §G: model complexity must match data maturity).
public enum LatentPlayerType: String, Sendable, CaseIterable {
    /// Skilled, low ad tolerance -> favor gentle IAP.
    case highSkillLowTolerance = "HIGH_SKILL_LOW_TOLERANCE"
    /// Skilled, high ad tolerance -> safe to lean into IAA.
    case highSkillHighTolerance = "HIGH_SKILL_HIGH_TOLERANCE"
    /// Weak, high churn risk -> protect the experience, free rescue.
    case lowSkillHighChurnRisk = "LOW_SKILL_HIGH_CHURN_RISK"
    /// Stable, no strong signal yet -> neutral fallback.
    case casualSteady = "CASUAL_STEADY"
}
