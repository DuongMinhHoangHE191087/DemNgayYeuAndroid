import Foundation

/// Guardrail engine — runs BEFORE the bandit ever sees the action space.
///
/// TWO-TIER CLASSIFICATION:
///
///  - `.hard`: an ABSOLUTE constraint (ethics, store policy, anti-fraud, child protection).
///    Removes the action from the candidate set outright, never expressed as a soft penalty in
///    reward — because a penalty can always be "bought" with enough revenue.
///  - `.resource`: a BUDGETED resource constraint (ad-load/session, daily fatigue, churn risk).
///    Bandits-with-Knapsacks theory says the optimal solution is a MIXED distribution over arms,
///    not "pick the best arm, then block." A hard filter on this tier alone biases the system.
///    `LagrangianBudget` provides the correct shadow-price treatment; the thresholds in this
///    class are the ABSOLUTE SAFETY CEILING at the edge, not the sole regulating mechanism —
///    the two layers complement each other.
///
/// Two design principles (distilled from a competitor teardown):
/// 1. "Fail-safe, not fail-aggressive" — every default in this class must be the SAFEST edge of
///    the system. A competitor app got this backwards: a config parse error made it silently
///    sell its most expensive bundle and 12x its ad density.
/// 2. "Isolate state by action kind" — every `ActionMode` gets its OWN cap state, never shared
///    with an unrelated mode (the same competitor shared one cooldown slot between pip/banner
///    and squeeze/native ads).
public enum GuardrailKind: Sendable, Equatable {
    /// Absolute constraint — always a hard filter, correctly so.
    case hard
    /// Resource constraint — smoothed by `LagrangianBudget`; the threshold here is the edge cap.
    case resource
}

public struct GuardrailViolation: Sendable, Equatable {
    public let code: String
    public let reason: String
    public let kind: GuardrailKind

    public init(code: String, reason: String, kind: GuardrailKind) {
        self.code = code
        self.reason = reason
        self.kind = kind
    }
}

public final class GuardrailEngine: @unchecked Sendable {
    private let lock = NSLock()

    // Resource budget. Production loads these from a policy artifact; the values here are the
    // SAFEST edge — if the artifact fetch fails, the system falls back to these and must be
    // MORE conservative, never looser.
    private var _maxInterstitialPerSession: Int = 6
    public var maxInterstitialPerSession: Int {
        get { lock.lock(); defer { lock.unlock() }; return _maxInterstitialPerSession }
        set { lock.lock(); defer { lock.unlock() }; _maxInterstitialPerSession = newValue }
    }

    private var _minMinutesBetweenInterstitial: Int = 3
    public var minMinutesBetweenInterstitial: Int {
        get { lock.lock(); defer { lock.unlock() }; return _minMinutesBetweenInterstitial }
        set { lock.lock(); defer { lock.unlock() }; _minMinutesBetweenInterstitial = newValue }
    }

    private var _maxAds24h: Int = 40
    public var maxAds24h: Int {
        get { lock.lock(); defer { lock.unlock() }; return _maxAds24h }
        set { lock.lock(); defer { lock.unlock() }; _maxAds24h = newValue }
    }

    private var _churnRiskProtectionThreshold: Double = 0.75
    public var churnRiskProtectionThreshold: Double {
        get { lock.lock(); defer { lock.unlock() }; return _churnRiskProtectionThreshold }
        set { lock.lock(); defer { lock.unlock() }; _churnRiskProtectionThreshold = newValue }
    }

    // --- IAP_OFFER thresholds ---
    private var _maxIapOffersPerSession: Int = 3
    public var maxIapOffersPerSession: Int {
        get { lock.lock(); defer { lock.unlock() }; return _maxIapOffersPerSession }
        set { lock.lock(); defer { lock.unlock() }; _maxIapOffersPerSession = newValue }
    }

    private var _minMinutesBetweenIapOffers: Int = 10
    public var minMinutesBetweenIapOffers: Int {
        get { lock.lock(); defer { lock.unlock() }; return _minMinutesBetweenIapOffers }
        set { lock.lock(); defer { lock.unlock() }; _minMinutesBetweenIapOffers = newValue }
    }

    private var _maxPurchasesPerSessionBeforeBreak: Int = 3

    /// Purchases in one session enough to CUT OFF offers — the spend-velocity circuit breaker.
    public var maxPurchasesPerSessionBeforeBreak: Int {
        get { lock.lock(); defer { lock.unlock() }; return _maxPurchasesPerSessionBeforeBreak }
        set { lock.lock(); defer { lock.unlock() }; _maxPurchasesPerSessionBeforeBreak = newValue }
    }

    private var _frustrationStreakThreshold: Int = 5

    /// A losing streak deep enough to count as "stuck" — no offers at this point.
    ///
    /// The line isn't whether there's a loss at all (offering after a few losses is a normal
    /// game-design pattern) — it's that willingness-to-pay rises with the LENGTH of the losing
    /// streak. The more stuck someone is, the more "effective" an offer becomes — which is
    /// exactly why it isn't allowed.
    public var frustrationStreakThreshold: Int {
        get { lock.lock(); defer { lock.unlock() }; return _frustrationStreakThreshold }
        set { lock.lock(); defer { lock.unlock() }; _frustrationStreakThreshold = newValue }
    }

    // --- Progression-based guardrails (distilled from a production puzzle-game teardown) ---
    // Common thread: protect the FRAGILE MOMENT. A brand-new player not yet attached, someone
    // who just lost and is frustrated, someone who just reopened the app and hasn't found their
    // rhythm yet — three moments where an ad's immediate value is about the same but the
    // retention cost differs enormously.
    // Default 0/-1 = OFF, preserving old behavior. Turning these on is the studio's call.

    private var _minLevelForInterstitial: Int = 0

    /// Don't show an interstitial before this level. The reference game uses 10.
    public var minLevelForInterstitial: Int {
        get { lock.lock(); defer { lock.unlock() }; return _minLevelForInterstitial }
        set { lock.lock(); defer { lock.unlock() }; _minLevelForInterstitial = newValue }
    }

    private var _minLevelForInterstitialAfterLoss: Int = 0

    /// Separate, usually higher threshold for the LOSS branch (reference game: 22 vs 10). The
    /// reason is psychological, not technical: someone who just lost is already frustrated;
    /// stacking an ad on top pushes them to leave — the same logic `RewardModel` relies on.
    /// 0 = share the winning branch's threshold.
    public var minLevelForInterstitialAfterLoss: Int {
        get { lock.lock(); defer { lock.unlock() }; return _minLevelForInterstitialAfterLoss }
        set { lock.lock(); defer { lock.unlock() }; _minLevelForInterstitialAfterLoss = newValue }
    }

    private var _minMinutesAfterRewarded: Int = 0

    /// Defer an interstitial after the player just watched a rewarded ad. Kept separate from the
    /// normal cooldown: someone who just VOLUNTARILY watched a long ad shouldn't immediately get
    /// a forced one stacked on top.
    public var minMinutesAfterRewarded: Int {
        get { lock.lock(); defer { lock.unlock() }; return _minMinutesAfterRewarded }
        set { lock.lock(); defer { lock.unlock() }; _minMinutesAfterRewarded = newValue }
    }

    private var _skipFirstInterstitialsOnReopen: Int = 0

    /// Skip the first N interstitial opportunities after reopening the app — the moment the
    /// player is deciding whether to stay.
    public var skipFirstInterstitialsOnReopen: Int {
        get { lock.lock(); defer { lock.unlock() }; return _skipFirstInterstitialsOnReopen }
        set { lock.lock(); defer { lock.unlock() }; _skipFirstInterstitialsOnReopen = newValue }
    }

    private var _maxIapOffers24h: Int = 0

    /// Daily offer cap. A session cap alone can't stop someone who opens the app 10x a day.
    public var maxIapOffers24h: Int {
        get { lock.lock(); defer { lock.unlock() }; return _maxIapOffers24h }
        set { lock.lock(); defer { lock.unlock() }; _maxIapOffers24h = newValue }
    }

    private var _maxAssistsPerSession: Int = 3

    /// Cap on gameplay assists (FREE_ACTION gift / GAMEPLAY_CONFIG difficulty tuning) per
    /// session.
    ///
    /// Assists are FREE for the studio but not free for the experience: stacking them creates
    /// learned helplessness (the player stops trying because help is coming), breaks the
    /// progression signal the game depends on, and skews the game economy. This is the edge cap
    /// for both assist action kinds — the rhythm inside it self-regulates (a gift only has value
    /// when the player is actually stuck).
    public var maxAssistsPerSession: Int {
        get { lock.lock(); defer { lock.unlock() }; return _maxAssistsPerSession }
        set { lock.lock(); defer { lock.unlock() }; _maxAssistsPerSession = newValue }
    }

    /// Count of successful blocks per guardrail — feeds a future ComfortIndex.
    private var preventedCounts: [String: Int] = [:]

    public init() {}

    public func preventedCount(_ code: String) -> Int {
        lock.lock()
        defer { lock.unlock() }
        return preventedCounts[code] ?? 0
    }

    public func allPreventedCounts() -> [String: Int] {
        lock.lock()
        defer { lock.unlock() }
        return preventedCounts
    }

    /// Effective interstitial cap = min(absolute cap, scenario cap).
    ///
    /// The `min` direction is deliberate and a real safety invariant: a personalization scenario
    /// may only TIGHTEN the cap, never loosen it past the shared ceiling. If loosening were
    /// allowed, a player misclassified into an ad-forward segment would get MORE ads than the
    /// global policy permits at all — exactly the kind of failure the fail-safe principle above
    /// forbids.
    public func effectiveInterstitialCap(_ scenario: PersonalizationScenario?) -> Int {
        guard let scenario else { return maxInterstitialPerSession }
        return min(maxInterstitialPerSession, scenario.interstitialCapPerSession)
    }

    private func record(_ violation: GuardrailViolation) -> GuardrailViolation {
        lock.lock()
        preventedCounts[violation.code, default: 0] += 1
        lock.unlock()
        return violation
    }

    /// - Parameters:
    ///   - scenario: the personalization scenario in effect. Its cap can only make the system
    ///     MORE conservative — see `effectiveInterstitialCap`.
    ///   - decisionPoint: needed to distinguish the WIN branch from the LOSS branch — they have
    ///     different thresholds. `nil` = context unknown, treated as the win branch.
    public func evaluate(
        candidate: ActionCandidate,
        state: PlayerState,
        adsShownThisSession: Int,
        minutesSinceLastInterstitial: Int,
        scenario: PersonalizationScenario? = nil,
        decisionPoint: DecisionPoint? = nil
    ) -> GuardrailViolation? {
        let isAdLike = candidate.mode == .interstitial || candidate.mode == .rewardedAd

        // ================= HARD — absolute constraints =================

        // Anti-click-fraud: protects the mediation account. Exempts NO_ACTION, GAMEPLAY_CONFIG,
        // and FREE_ACTION — this lock protects the ad inventory from fraudulent clicks, not
        // gifts or difficulty tuning.
        if candidate.mode != .noAction,
           candidate.mode != .gameplayConfig,
           candidate.mode != .freeAction,
           ClickFraudGuard.isLocked(playerId: state.playerId) {
            return record(GuardrailViolation(
                code: "CLICK_FRAUD_LOCKOUT",
                reason: "exceeded click threshold in window, ads locked",
                kind: .hard
            ))
        }

        // Ethics: rewarded may only be offered after a REAL fail — never a "manufactured fail."
        // An ethical constraint is never turned into a reward penalty.
        if candidate.mode == .rewardedAd, state.failStreakCurrent <= 0 {
            return record(GuardrailViolation(
                code: "ETH_MANUFACTURED_FAIL",
                reason: "rewarded should only be offered after a real fail",
                kind: .hard
            ))
        }

        // Ethics: don't monetize being stuck.
        if candidate.mode == .iapOffer, state.failStreakCurrent >= frustrationStreakThreshold {
            return record(GuardrailViolation(
                code: "ETH_FRUSTRATION_EXPLOIT",
                reason: "stuck (fail streak \(state.failStreakCurrent)) — no offer",
                kind: .hard
            ))
        }

        // Spend-velocity circuit breaker.
        //
        // HARD, not RESOURCE — this is the crux: filing it as RESOURCE would turn it into a soft
        // penalty the bandit can "buy back" with enough revenue — and revenue from exactly the
        // group spending in a rapid burst is always big enough to buy it. Protecting vulnerable
        // people is never allowed to have a price.
        if candidate.mode == .iapOffer, state.purchasesThisSession >= maxPurchasesPerSessionBeforeBreak {
            return record(GuardrailViolation(
                code: "IAP_VELOCITY_BREAKER",
                reason: "purchased \(state.purchasesThisSession)x this session — pausing offers",
                kind: .hard
            ))
        }

        // ================= RESOURCE — edge-of-budget caps =================
        // The rhythm INSIDE the budget is regulated by `LagrangianBudget` (shadow price
        // subtracted from reward before the bandit chooses). The thresholds below are the final
        // stop: reaching them means the shadow price didn't restrain enough, and there's no
        // further negotiation.

        if candidate.mode == .interstitial {
            let isLossPath = decisionPoint == .levelFail || decisionPoint == .levelFailStreak
            let levelGate = (isLossPath && minLevelForInterstitialAfterLoss > 0)
                ? minLevelForInterstitialAfterLoss
                : minLevelForInterstitial
            if levelGate > 0, state.currentLevel < levelGate {
                return record(GuardrailViolation(
                    code: "NEW_PLAYER_GRACE",
                    reason: "level \(state.currentLevel) < threshold \(levelGate)"
                        + (isLossPath ? " (loss branch)" : ""),
                    kind: .resource
                ))
            }

            if skipFirstInterstitialsOnReopen > 0,
               state.interstitialOpportunitiesThisSession < skipFirstInterstitialsOnReopen {
                return record(GuardrailViolation(
                    code: "REOPEN_GRACE",
                    reason: "opportunity #\(state.interstitialOpportunitiesThisSession + 1) after reopening",
                    kind: .resource
                ))
            }

            // -1 = never, meant as "infinitely long ago" — getting this wrong would block
            // interstitials for everyone who has never watched a rewarded ad, i.e. most players.
            if minMinutesAfterRewarded > 0,
               (0..<minMinutesAfterRewarded).contains(state.minutesSinceLastRewarded) {
                return record(GuardrailViolation(
                    code: "INTER_AFTER_REWARDED_COOLDOWN",
                    reason: "watched rewarded \(state.minutesSinceLastRewarded) min ago",
                    kind: .resource
                ))
            }

            let cap = effectiveInterstitialCap(scenario)
            if adsShownThisSession >= cap {
                let scenarioNote = scenario.map { ", per scenario \($0.displayName)" } ?? ""
                return record(GuardrailViolation(
                    code: "AD_PRESSURE_SESSION_CAP",
                    reason: "exceeded interstitial/session cap (effective=\(cap)\(scenarioNote))",
                    kind: .resource
                ))
            }
            if minutesSinceLastInterstitial < minMinutesBetweenInterstitial {
                return record(GuardrailViolation(
                    code: "AD_PRESSURE_COOLDOWN",
                    reason: "not enough cooldown between interstitials",
                    kind: .resource
                ))
            }
            if state.adImpressions24h >= maxAds24h {
                return record(GuardrailViolation(
                    code: "AD_PRESSURE_24H_CAP",
                    reason: "exceeded max_ads_24h",
                    kind: .resource
                ))
            }
        }

        // Protect a recent payer: reduce ad pressure right after a transaction.
        //
        // Identified by PURCHASE COUNT, not total spend — with real billing, prices come back in
        // local currency and only sum to USD once the exchange rate is known; someone who paid
        // 9.99 EUR on a device with no cached EUR rate has a USD total of 0. Using total spend as
        // the "have they ever paid" test would silently drop exactly those payers, and only in
        // some markets.
        if isAdLike, state.purchaseCountLifetime > 0, (0...2).contains(state.daysSinceLastPurchase) {
            return record(GuardrailViolation(
                code: "PAYER_PROTECTION_RECENT",
                reason: "recent payer needs reduced ad pressure",
                kind: .resource
            ))
        }

        // Offer frequency cap. Fully isolated from ad state — sharing a counter between two
        // unrelated action kinds is exactly the bug seen in the competitor teardown.
        if candidate.mode == .iapOffer {
            if maxIapOffers24h > 0, state.iapOffers24h >= maxIapOffers24h {
                return record(GuardrailViolation(
                    code: "IAP_OFFER_24H_CAP",
                    reason: "exceeded offer/24h cap (\(maxIapOffers24h))",
                    kind: .resource
                ))
            }
            if state.iapOffersThisSession >= maxIapOffersPerSession {
                return record(GuardrailViolation(
                    code: "IAP_OFFER_SESSION_CAP",
                    reason: "exceeded offer/session cap (\(maxIapOffersPerSession))",
                    kind: .resource
                ))
            }
            // -1 = no offer shown yet this session, must be read as "infinitely long ago," not
            // "0 minutes ago" — getting this wrong blocks exactly the very FIRST offer.
            if (0..<minMinutesBetweenIapOffers).contains(state.minutesSinceLastIapOffer) {
                return record(GuardrailViolation(
                    code: "IAP_OFFER_COOLDOWN",
                    reason: "not enough cooldown between offers",
                    kind: .resource
                ))
            }
        }

        // Gameplay-assist frequency cap (FREE_ACTION / GAMEPLAY_CONFIG). Isolated from both ad
        // and offer counters — three unrelated action kinds, no shared state (state-isolation
        // principle above).
        if (candidate.mode == .freeAction || candidate.mode == .gameplayConfig),
           state.assistsThisSession >= maxAssistsPerSession {
            return record(GuardrailViolation(
                code: "ASSIST_SESSION_CAP",
                reason: "exceeded assist/session cap (\(maxAssistsPerSession))",
                kind: .resource
            ))
        }

        // Churn-risk budget.
        if candidate.mode == .interstitial, state.churnRisk > churnRiskProtectionThreshold {
            return record(GuardrailViolation(
                code: "CHURN_PROTECTION",
                reason: "high churn_risk, avoid adding ad pressure",
                kind: .resource
            ))
        }

        return nil
    }
}
