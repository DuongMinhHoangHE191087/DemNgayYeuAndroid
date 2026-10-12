/// S_t (doc 04 / doc 24 §I): observable state + rolling latent-trait estimates — the input to
/// the decision engine (Phase 3). The game populates the observable fields each call; the
/// derived/latent fields (bottom of the struct) are filled by `BeliefState` and the feature
/// pipeline, not supplied by hand.
///
/// This is the minimal starting shape (Phase 0-1, doc 23 §D) — extended per the full feature
/// dictionary in doc 04 only when an actual decision point needs a new signal. Only `playerId`
/// is required at construction; every other field is a `var` set after construction, matching
/// how this type is used as a snapshot the caller assembles piece by piece.
public struct PlayerState: Sendable {
    public let playerId: String

    // MARK: Observable: lifecycle & session
    public var sessionsLifetime: Int = 0
    public var daysSinceLastSession: Int = 0
    public var sessionMinutesCurrent: Int = 0

    // MARK: Observable: progression / difficulty response
    public var currentLevel: Int = 0
    public var failStreakCurrent: Int = 0
    public var failRateLast30m: Double = 0.0

    // MARK: Observable: economy
    public var softCurrencyBalance: Int64 = 0
    public var hardCurrencyBalance: Int64 = 0
    public var burnRate24h: Double = 0.0

    // MARK: Observable: IAP behavior
    public var purchaseCountLifetime: Int = 0
    public var purchaseValueLifetimeUsd: Double = 0.0
    public var daysSinceLastPurchase: Int = -1

    /// Purchases in the CURRENT session. The strongest, easiest-to-measure at-risk marker in IAP
    /// per doc 29 §5.2 ("deposit frequency spike"), and an input to the guardrail layer's circuit
    /// breaker (Phase 3). Kept separate from `purchaseCountLifetime` — the two answer different
    /// questions: "has this player ever paid" vs. "are they spending in a rapid burst right now."
    public var purchasesThisSession: Int = 0
    /// Number of times an IAP offer has been shown this session — the frequency cap input.
    public var iapOffersThisSession: Int = 0
    /// -1 = no IAP offer shown this session yet.
    public var minutesSinceLastIapOffer: Int = -1
    /// Offers shown in the last 24h. A session cap alone isn't enough for someone who opens the
    /// app many times a day.
    public var iapOffers24h: Int = 0
    /// -1 = no rewarded ad seen yet. Used to defer an interstitial right after a voluntary
    /// rewarded view.
    public var minutesSinceLastRewarded: Int = -1
    /// Interstitial opportunities that have PASSED this session — including ones that were
    /// blocked. Used for the grace-period-after-reopen guardrail.
    public var interstitialOpportunitiesThisSession: Int = 0
    /// Gameplay assists (free gift / difficulty tuning) shown this session. Assists are FREE for
    /// the studio but the goodwill cost is real: stacking them creates learned helplessness and
    /// breaks the game economy — so this needs the same kind of cap as every other pressure
    /// action.
    public var assistsThisSession: Int = 0

    // MARK: Observable: IAA behavior
    public var adImpressions24h: Int = 0
    public var rewardedAcceptRateRecent: Double = 0.0
    /// Rewarded opportunities recently OFFERED. Must accompany `rewardedAcceptRateRecent` — a
    /// 0.0 rate for someone never offered means something completely different than someone
    /// offered 10 times who declined all 10. Without this field, `LikelihoodEstimator` would
    /// draw the wrong conclusion.
    public var rewardedOpportunitiesRecent: Int = 0
    public var minutesSinceLastAd: Int = -1

    // MARK: Derived / latent (doc 24 §I — filled by BeliefState or the feature pipeline)
    public var purchasePropensity: Double = 0.0
    public var churnRisk: Double = 0.0
    public var adToleranceScore: Double = 0.5
    public var adFatigueScore: Double = 0.0
    public var monetizationReadiness: Double = 0.0

    public init(playerId: String) {
        self.playerId = playerId
    }
}
