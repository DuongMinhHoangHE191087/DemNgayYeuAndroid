/// Reward model — decides what "a good decision" means.
///
/// ═══ THE BUG THIS FIXES ═══
///
/// An earlier version assigned reward at the moment of the action itself: `INTERSTITIAL → 1.0`,
/// `NO_ACTION → 0.0`. That's tautological — the action gets rewarded because it happened, not
/// because it created value. The inevitable result: the bandit converges on "always show an ad,"
/// and the only thing holding that back is the guardrail layer — the entire learning component
/// becomes pointless, since it's only relearning a rule that was already hardcoded.
///
/// ═══ PRINCIPLE: DOWNSTREAM REWARD ═══
///
/// Reward must come from what happens AFTER an action, not from the action itself. This is the
/// pattern Pinterest productionized (doc 03 §8, doc 05 §9): retention is a sparse, delayed
/// signal, so an early-but-predictive session-behavior proxy is used instead.
///
/// The proxy used here is **whether the player kept playing after the action**:
///
/// ```
/// R = w_value · immediate_value  +  w_continue · continued_playing
/// ```
///
/// Priority order this produces (default weights):
///
/// | Situation                      | Reward | Meaning |
/// |---------------------------------|--------|---------|
/// | Ad shown, player stayed         | 1.00   | best — revenue with no one lost |
/// | Not shown, player stayed        | 0.60   | safe but revenue left on the table |
/// | Ad shown, player left           | 0.40   | **worse than doing nothing** |
/// | Not shown, player left anyway   | 0.00   | lost the player and got nothing |
///
/// The crucial line is the third: showing an ad and then losing the player must score LOWER
/// than staying silent. Without that property the bandit never learns restraint.
///
/// ═══ THIS IS STILL NOT THE DESTINATION ═══
///
/// The full formula (doc 11) is:
///
/// ```
/// R = r_immediate + γ·[LTV(s') − LTV(s)] − λ_churn·P(churn|action) − λ_fair·FairnessViolation
/// ```
///
/// which needs an LTV/churn model that doesn't exist yet. What's here is the **right-direction
/// intermediate step**: no longer tautological, and there's now a real tradeoff between revenue
/// and retention. When an LTV model exists, `estimate()` changes — the decision engine and
/// `AdsBrain` don't need to.
public enum RewardModel {
    /// Weight for immediate value (was the ad actually shown/completed).
    private static let _weightImmediateValue = LockedValue(0.4)
    public static var weightImmediateValue: Double {
        get { _weightImmediateValue.wrapped }
        set { _weightImmediateValue.wrapped = newValue }
    }

    /// Weight for the continuation signal — the retention proxy.
    private static let _weightContinuation = LockedValue(0.6)
    public static var weightContinuation: Double {
        get { _weightContinuation.wrapped }
        set { _weightContinuation.wrapped = newValue }
    }

    /// - Parameters:
    ///   - actionHadValue: did the action create immediate value (ad shown, rewarded watched
    ///     fully, IAP succeeded)?
    ///   - playerContinued: did the player keep playing afterward? `nil` = not known yet, treated
    ///     as neutral rather than guessed.
    public static func estimate(actionHadValue: Bool, playerContinued: Bool?) -> Double {
        let value = actionHadValue ? weightImmediateValue : 0.0
        let cont: Double
        switch playerContinued {
        case .some(true): cont = weightContinuation
        case .some(false): cont = 0.0
        case .none: cont = weightContinuation / 2.0
        }
        return min(max(value + cont, 0.0), 1.0)
    }

    /// Resets weights to their defaults.
    public static func reset() {
        weightImmediateValue = 0.4
        weightContinuation = 0.6
    }
}
