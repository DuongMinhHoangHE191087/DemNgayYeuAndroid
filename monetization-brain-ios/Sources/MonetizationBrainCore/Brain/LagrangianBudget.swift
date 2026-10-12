import Foundation

/// Shadow price for a resource constraint — a practical Bandits-with-Knapsacks implementation.
///
/// Hard filtering ("6 ads and you're cut off") makes behavior jerky: the bandit keeps picking the
/// best arm until it hits the cap, then goes silent. With a 6-interstitial/session budget, a
/// player could get all 6 in the first 5 minutes and then nothing — a bad experience AND
/// suboptimal revenue.
///
/// BwK theory says the optimal solution, when the constraint is a *budget* rather than "always
/// pick the best arm," is a MIXED distribution — e.g. 70% light rewarded, 20% interstitial, 10%
/// silence. Lagrangian relaxation reaches that without solving an LP at every decision:
///
/// ```
/// adjusted reward:  r~(a) = r(a) - Σ_j lambda_j * c_j(a)
/// dual update:       lambda_j <- lambda_j * exp(eta * (observed_cost - B_j/T))
/// ```
///
/// Spend faster than budget -> lambda rises -> every resource-costly action gets penalized more,
/// smoothly. Spend slower -> lambda falls -> the system relaxes on its own.
///
/// This does NOT replace `GuardrailEngine`'s hard caps. The penalty is soft — in principle, an
/// arm with a big enough reward can still win. For user-safety constraints, "enough revenue buys
/// you the right to spam" is unacceptable, so the two layers complement each other: Lagrangian
/// shapes the rhythm INSIDE the budget (smooth, optimal); the hard cap blocks absolutely AT THE
/// EDGE (safe, non-negotiable).
///
/// Cost values below are DESIGN CHOICES, not calibrated from real data — same caveat as every
/// other threshold ported from Android in this phase.
public final class LagrangianBudget: @unchecked Sendable {
    private let lock = NSLock()

    /// Dual variable's learning rate. Too high: lambda oscillates. Too low: reacts too slowly.
    private var _learningRate: Double = 0.15

    public var learningRate: Double {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _learningRate
        }
        set {
            lock.lock()
            defer { lock.unlock() }
            _learningRate = newValue
        }
    }

    /// Ceiling so one abnormal streak can't push lambda to infinity and lock out every action.
    private var _maxLambda: Double = 5.0

    public var maxLambda: Double {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _maxLambda
        }
        set {
            lock.lock()
            defer { lock.unlock() }
            _maxLambda = newValue
        }
    }

    /// Starts positive on purpose: the update is multiplicative, so starting at 0 would keep
    /// lambda at 0 forever — a classic bug when implementing multiplicative weights.
    private var _lambdaAdPressure: Double = 0.1

    public var lambdaAdPressure: Double {
        lock.lock()
        defer { lock.unlock() }
        return _lambdaAdPressure
    }

    public init() {}

    /// "Discomfort cost" each action type consumes. Rewarded is much lower than interstitial
    /// because the player CHOSE to watch it — a difference in kind, not degree (rewarded is only
    /// valid when it's a genuine choice).
    public func costOf(_ mode: ActionMode) -> Double {
        switch mode {
        case .interstitial: return 1.0
        case .rewardedAd: return 0.3
        case .iapOffer: return 0.2
        case .noAction, .freeAction, .gameplayConfig: return 0.0
        }
    }

    /// Amount subtracted from an action's reward.
    public func penaltyFor(_ mode: ActionMode) -> Double {
        lock.lock()
        defer { lock.unlock() }
        return _lambdaAdPressure * costOf(mode)
    }

    /// Updates the shadow price after an action has been chosen.
    ///
    /// - Parameters:
    ///   - chosenMode: the action just taken.
    ///   - budgetPerDecision: allowed budget per decision point = B/T. E.g. a 6-interstitial/
    ///     session cap with ~30 estimated decision points per session gives B/T = 0.2.
    public func observe(chosenMode: ActionMode, budgetPerDecision: Double) {
        let consumed = costOf(chosenMode)
        let target = max(budgetPerDecision, 0.0)
        lock.lock()
        defer { lock.unlock() }
        let factor = exp(_learningRate * (consumed - target))
        _lambdaAdPressure = min(max(_lambdaAdPressure * factor, 0.001), _maxLambda)
    }

    /// Resets lambda to its starting value — call at the start of a new session.
    public func reset() {
        lock.lock()
        _lambdaAdPressure = 0.1
        lock.unlock()
    }
}
