import Foundation

/// Computes P(observation | latent type) — the link that actually makes `BeliefState` move.
///
/// Before this type existed, `BeliefState.update()` existed but nothing called it, so every
/// player's posterior sat at the uniform 0.25 prior forever — the system degenerated into one
/// global bandit with no personalization. This is the fix.
///
/// MODEL: naive Bayes over a few conditionally-independent signals. Each latent type has an
/// "expected profile"; the likelihood of an observation is its Gaussian match to that profile.
///
/// THREE SAFETY PRINCIPLES are baked in — this is the easiest part to get wrong:
///
///  1. **Support gating**: a signal is only used if there's ENOUGH data. `rewardedAcceptRateRecent
///     == 0.0` for someone never offered a rewarded ad does NOT mean "hates ads" — it means
///     "unknown." Conflating the two is a classic mistake.
///  2. **Damping**: likelihood is raised to a `damping < 1` power so a single observation can't
///     flip the posterior. Belief should drift gradually, not jump.
///  3. **Floor**: every likelihood is floored at `minLikelihood` so no segment is ever pushed to
///     probability 0 permanently — from 0, Bayes never recovers.
///
/// HONESTY WARNING: every profile constant below is a **domain-knowledge assumption, NOT YET
/// calibrated on real data**. Good enough to make the mechanism run and observable, but not a
/// conclusion. The correct calibration path: collect decision logs, label clusters with the
/// method in doc 33 (HMM/clustering with stability validation), then re-estimate these `mu`
/// values from real data.
public enum LikelihoodEstimator {
    /// Minimum rewarded opportunities before the acceptance rate is statistically meaningful.
    private static let minRewardedSupport = 3

    /// Minimum sessions before skill/progression signals are meaningful.
    private static let minSessionSupport = 2

    /// Update softening: 1.0 = trust a single observation completely; smaller drifts slower.
    ///
    /// 0.25 was chosen by working backward: for one EXTREME observation (95% fail rate, 10 days
    /// away), the posterior moves from prior 0.25 to ~0.59 — sensitive enough to react, not
    /// enough to conclude from a single look. At 0.35 that number is ~0.71, nearly an immediate
    /// flip, contradicting the point of damping.
    private static let damping = 0.25

    private static let minLikelihood = 0.02

    /// Expected profile for a latent type. These `mu` values need recalibration from data.
    private struct Profile {
        let failRate: Double
        let rewardedAcceptRate: Double
        let payerPropensity: Double
        let churnTendency: Double
    }

    /// The profile in effect: Android prefers a calibrated value from the policy artifact
    /// (`BrainPolicy.profileFor`) before falling back to the compiled default. `BrainPolicy` is
    /// not ported yet, so this always returns the default for now — the remote-override check
    /// will be added when `BrainPolicy` lands (Phase 3+); this is the one place that changes,
    /// nothing else in this file does.
    private static func profileOf(_ type: LatentPlayerType) -> Profile {
        defaultProfiles[type]!
    }

    private static let defaultProfiles: [LatentPlayerType: Profile] = [
        .highSkillLowTolerance: Profile(failRate: 0.15, rewardedAcceptRate: 0.20, payerPropensity: 0.70, churnTendency: 0.20),
        .highSkillHighTolerance: Profile(failRate: 0.15, rewardedAcceptRate: 0.75, payerPropensity: 0.20, churnTendency: 0.25),
        .lowSkillHighChurnRisk: Profile(failRate: 0.60, rewardedAcceptRate: 0.45, payerPropensity: 0.15, churnTendency: 0.80),
        .casualSteady: Profile(failRate: 0.35, rewardedAcceptRate: 0.50, payerPropensity: 0.35, churnTendency: 0.35)
    ]

    /// Gaussian width per signal — larger means the signal is less decisive.
    private static let sigmaFail = 0.25
    private static let sigmaRewarded = 0.25
    private static let sigmaPayer = 0.30
    private static let sigmaChurn = 0.30

    private static func gaussianLikelihood(observed: Double, expected: Double, sigma: Double) -> Double {
        let d = observed - expected
        return exp(-(d * d) / (2.0 * sigma * sigma))
    }

    /// Infers an observed "is a payer" probability from actual purchase behavior. Doesn't use
    /// `PlayerState.purchasePropensity` (that field is filled by a different model) — using it
    /// here would create a feedback loop (the model trusting its own output).
    private static func observedPayerSignal(_ state: PlayerState) -> Double {
        switch state.purchaseCountLifetime {
        case 3...: return 0.90
        case 2: return 0.75
        case 1: return 0.60
        default: return 0.10
        }
    }

    private static func observedChurnSignal(_ state: PlayerState) -> Double {
        switch state.daysSinceLastSession {
        case 7...: return 0.90
        case 3...: return 0.65
        case 1...: return 0.40
        default: return 0.20
        }
    }

    /// Returns a likelihood map to pass straight into `BeliefState.update(likelihood:)`. If NO
    /// signal has enough support, returns an empty map — the caller should skip that update
    /// rather than update with meaningless data.
    public static func estimate(_ state: PlayerState) -> [LatentPlayerType: Double] {
        let useRewarded = state.rewardedOpportunitiesRecent >= minRewardedSupport
        let useSkill = state.sessionsLifetime >= minSessionSupport
        let usePayer = state.sessionsLifetime >= minSessionSupport
        let useChurn = state.sessionsLifetime >= minSessionSupport

        guard useRewarded || useSkill || usePayer || useChurn else { return [:] }

        let payerSignal = observedPayerSignal(state)
        let churnSignal = observedChurnSignal(state)

        var result: [LatentPlayerType: Double] = [:]
        for type in LatentPlayerType.allCases {
            let p = profileOf(type)
            var likelihood = 1.0

            if useSkill {
                likelihood *= gaussianLikelihood(
                    observed: min(max(state.failRateLast30m, 0.0), 1.0), expected: p.failRate, sigma: sigmaFail
                )
            }
            if useRewarded {
                likelihood *= gaussianLikelihood(
                    observed: min(max(state.rewardedAcceptRateRecent, 0.0), 1.0), expected: p.rewardedAcceptRate, sigma: sigmaRewarded
                )
            }
            if usePayer {
                likelihood *= gaussianLikelihood(observed: payerSignal, expected: p.payerPropensity, sigma: sigmaPayer)
            }
            if useChurn {
                likelihood *= gaussianLikelihood(observed: churnSignal, expected: p.churnTendency, sigma: sigmaChurn)
            }

            result[type] = max(pow(likelihood, damping), minLikelihood)
        }
        return result
    }

    /// Lists which signals are actually being used — for a debug view, to see why belief is
    /// standing still.
    public static func activeSignals(_ state: PlayerState) -> [String] {
        var signals: [String] = []
        if state.sessionsLifetime >= minSessionSupport {
            signals.append(String(format: "failRate=%.2f", state.failRateLast30m))
            signals.append(String(format: "payer=%.2f", observedPayerSignal(state)))
            signals.append(String(format: "churn=%.2f", observedChurnSignal(state)))
        }
        if state.rewardedOpportunitiesRecent >= minRewardedSupport {
            signals.append(String(format: "rewardedAccept=%.2f", state.rewardedAcceptRateRecent))
        }
        if signals.isEmpty { signals.append("not enough data yet — belief holds at prior") }
        return signals
    }
}
