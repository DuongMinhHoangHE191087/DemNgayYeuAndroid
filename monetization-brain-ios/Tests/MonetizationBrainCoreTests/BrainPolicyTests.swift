import XCTest
@testable import MonetizationBrainCore

/// Design spec §5 (BrainPolicy half).
///
/// The three deviations this file exists to hold in place: §2.1 (Kotlin's `toInt()` saturates,
/// Swift's `Int(Double)` TRAPS — so a hostile artifact would crash every instance that fetched
/// it), §2.5 (`resetForTest()` must revert the DOWNSTREAM knobs, not just its own fields) and
/// §2.6 (the `HARD_*` ceilings are the security property, and are tested from the ATTACKING side
/// — an artifact asking for `max_ads_24h: 9999` must come out at 60).
final class BrainPolicyTests: XCTestCase {
    // MARK: - Helpers

    /// The two literals §2.1 is about. `1e300` overflows `Int` by ~280 orders of magnitude, and a
    /// 20-digit literal decodes as an `NSDecimalNumber` — `as? Int` returns `nil` for BOTH, so a
    /// clamp written after the cast never runs, and the cast itself traps.
    private static let hostileLiterals = ["1e300", "99999999999999999999", "-1e300", "1e-300"]

    private func makePolicy(
        _ json: String? = nil,
        guardrails: GuardrailEngine = GuardrailEngine()
    ) -> (BrainPolicy, GuardrailEngine) {
        let policy = BrainPolicy()
        if let json { _ = policy.apply(json, guardrails: guardrails) }
        return (policy, guardrails)
    }

    /// An artifact carrying EVERY numeric field this parser reads, each set to `literal`. Used to
    /// prove no field anywhere reaches a bare `Int(...)`.
    private func artifactWithEveryNumericField(_ literal: String) -> String {
        """
        {
          "version": "hostile_v1",
          "enabled": true,
          "guardrails": {
            "max_interstitial_per_session": \(literal),
            "min_minutes_between_interstitial": \(literal),
            "max_ads_24h": \(literal),
            "churn_risk_threshold": \(literal)
          },
          "iap": {
            "max_offers_per_session": \(literal),
            "min_minutes_between_offers": \(literal),
            "max_purchases_per_session_before_break": \(literal),
            "frustration_streak_threshold": \(literal)
          },
          "gameplay": { "max_assists_per_session": \(literal) },
          "progression": {
            "min_level_for_interstitial": \(literal),
            "min_level_for_interstitial_after_loss": \(literal),
            "min_minutes_after_rewarded": \(literal),
            "skip_first_interstitials_on_reopen": \(literal),
            "max_iap_offers_24h": \(literal)
          },
          "click_fraud": {
            "max_clicks_per_window": \(literal),
            "window_seconds": \(literal),
            "lockout_seconds": \(literal)
          },
          "experiment": {
            "holdout_fraction": \(literal),
            "rollout_fraction": \(literal),
            "exploration_floor": \(literal),
            "holdout_salt": "hostile_salt"
          },
          "fx_rates": { "VND": \(literal), "EUR": \(literal) },
          "remove_ads_products": ["com.x.remove_ads"],
          "reward": {
            "weight_immediate_value": \(literal),
            "weight_continuation": \(literal)
          },
          "scenarios": { "AD_FORWARD": { "interstitial_cap": \(literal) } },
          "likelihood_profiles": {
            "HIGH_SKILL_LOW_TOLERANCE": { "fail_rate": \(literal), "rewarded_accept": \(literal), "payer_propensity": \(literal), "churn_tendency": \(literal) },
            "HIGH_SKILL_HIGH_TOLERANCE": { "fail_rate": \(literal), "rewarded_accept": \(literal), "payer_propensity": \(literal), "churn_tendency": \(literal) },
            "LOW_SKILL_HIGH_CHURN_RISK": { "fail_rate": \(literal), "rewarded_accept": \(literal), "payer_propensity": \(literal), "churn_tendency": \(literal) },
            "CASUAL_STEADY": { "fail_rate": \(literal), "rewarded_accept": \(literal), "payer_propensity": \(literal), "churn_tendency": \(literal) }
          },
          "min_confidence": \(literal)
        }
        """
    }

    override func setUp() {
        super.setUp()
        BrainPolicy.shared.resetForTest()
        ConsentState.shared.resetForTest()
    }

    override func tearDown() {
        BrainPolicy.shared.resetForTest()
        ConsentState.shared.resetForTest()
        super.tearDown()
    }

    // MARK: - Artifact envelope

    func test_beforeAnyApplyTheBuiltInDefaultsAreInForce() {
        let (policy, _) = makePolicy()
        XCTAssertEqual(policy.activeVersion(), "not loaded")
        XCTAssertTrue(policy.isEnabled(), "an unfetched policy is live, not dead")
        XCTAssertNil(policy.profileFor(.casualSteady))
        XCTAssertEqual(policy.scenarioCap(.adForward), PersonalizationScenario.adForward.interstitialCapPerSession)
    }

    func test_emptyMalformedOrVersionlessArtifactIsRejected() {
        let (policy, _) = makePolicy()
        for json in [nil, "   ", "{not valid json", #"{"enabled": true}"#, #"{"version": "   "}"#] as [String?] {
            XCTAssertFalse(policy.apply(json, guardrails: GuardrailEngine()).applied, String(describing: json))
        }
        XCTAssertEqual(policy.activeVersion(), "not loaded")
    }

    /// §5: a malformed artifact leaves the PREVIOUS one running — a failed fetch must never
    /// silently revert the SDK to its compiled defaults mid-session.
    func test_aMalformedArtifactLeavesThePreviousOneRunning() {
        let engine = GuardrailEngine()
        let (policy, _) = makePolicy(#"{"version": "p1", "guardrails": {"max_ads_24h": 12}}"#, guardrails: engine)
        XCTAssertEqual(engine.maxAds24h, 12)

        let result = policy.apply("{not valid json", guardrails: engine)
        XCTAssertFalse(result.applied)
        XCTAssertTrue(result.report.contains("p1"), result.report)
        XCTAssertEqual(policy.activeVersion(), "p1")
        XCTAssertEqual(engine.maxAds24h, 12, "the last-good artifact must still be in force")
    }

    // MARK: - §2.2 `enabled` defaults the OTHER way from Offerwall, on purpose

    /// A kill switch whose ABSENCE means "policy is live" — matching Android, and deliberately
    /// NOT unified with `OfferwallManager.enabled`, which defaults to `false`.
    func test_enabledAbsentMeansThePolicyIsLive() {
        let (policy, _) = makePolicy(#"{"version": "p1"}"#)
        XCTAssertTrue(policy.isEnabled())
    }

    func test_explicitFalseDisablesThePolicyAndWrongTypesDegradeToLiveWithANote() {
        let (policy, _) = makePolicy(#"{"version": "p1", "enabled": false}"#)
        XCTAssertFalse(policy.isEnabled())

        for raw in ["\"false\"", "\"true\"", "0", "1", "[]"] {
            let result = policy.apply(#"{"version": "p2", "enabled": \#(raw)}"#, guardrails: GuardrailEngine())
            XCTAssertTrue(result.report.contains("'enabled' is not a boolean"), "\(raw) -> \(result.report)")
            XCTAssertTrue(result.report.contains("defaulting to true"), "\(raw) -> \(result.report)")
            XCTAssertTrue(policy.isEnabled(), raw)
        }
    }

    /// `enabled: false` is a kill switch over the TUNING, not a reason to reject the artifact:
    /// the version still loads so an operator can see which artifact turned the policy off.
    func test_aDisabledArtifactStillLoadsItsVersion() {
        let (policy, _) = makePolicy(#"{"version": "p1", "enabled": false}"#)
        XCTAssertEqual(policy.activeVersion(), "p1")
    }

    // MARK: - The wire contract with Android

    /// ═══ THE TEST THAT WOULD HAVE CAUGHT THE FIELD NAMES ═══
    ///
    /// Every other test in this file writes the artifact it is about to read, so a parser looking
    /// for `churn_risk_protection_threshold` and a test supplying `churn_risk_protection_threshold`
    /// agreed with each other perfectly and with the backend not at all. Nine field names had
    /// drifted from `com.app.plugin.brain.BrainPolicy` that way — `churn_risk_threshold`,
    /// `exploration_floor`, `holdout_salt`, `interstitial_cap`, `max_offers_per_session`,
    /// `min_minutes_between_offers`, `rewarded_accept`, `window_seconds`, `lockout_seconds` — plus
    /// one field read out of the wrong BLOCK, and none of it produced a single failure: a name the
    /// parser does not recognize is not an error, it is silence.
    ///
    /// So this one is written from the OTHER side. The artifact below is
    /// `appplugin/DOC/policy_artifact_example.json`, the file Android's own `PolicyArtifactContract`
    /// test loads — the shape ops actually authors — with the handful of values that happen to
    /// equal an iOS compiled default nudged, so that "the knob moved" is a real assertion rather
    /// than a coincidence. Every knob is captured before and asserted after: a typo in any name
    /// leaves its knob at the captured value and fails here instead of going quiet in production.
    func test_androidsReferenceArtifactMovesEveryKnobItNames() throws {
        let engine = GuardrailEngine()
        let policy = BrainPolicy()

        let beforeInterstitial = engine.maxInterstitialPerSession
        let beforeCooldown = engine.minMinutesBetweenInterstitial
        let beforeAds24h = engine.maxAds24h
        let beforeChurn = engine.churnRiskProtectionThreshold
        let beforeOffers = engine.maxIapOffersPerSession
        let beforeOfferCooldown = engine.minMinutesBetweenIapOffers
        let beforeBreak = engine.maxPurchasesPerSessionBeforeBreak
        let beforeStreak = engine.frustrationStreakThreshold
        let beforeAssists = engine.maxAssistsPerSession
        let beforeLevelGate = engine.minLevelForInterstitial
        let beforeLossGate = engine.minLevelForInterstitialAfterLoss
        let beforeAfterRewarded = engine.minMinutesAfterRewarded
        let beforeSkipOnReopen = engine.skipFirstInterstitialsOnReopen
        let beforeOffers24h = engine.maxIapOffers24h
        let beforeClicks = ClickFraudGuard.maxClicksPerWindow
        let beforeWindowMs = ClickFraudGuard.windowMs
        let beforeLockoutMs = ClickFraudGuard.lockoutMs
        let beforeHoldout = HoldoutManager.holdoutFraction
        let beforeSalt = HoldoutManager.salt
        let beforeRollout = AdsBrain.shared.rolloutFraction
        let beforeFloor = policy.pendingExplorationFloor
        let beforeImmediate = RewardModel.weightImmediateValue
        let beforeContinuation = RewardModel.weightContinuation
        let beforeConfidence = PersonalizationScenario.minConfidence
        let beforeRemoveAds = Entitlements.shared.removeAdsProductIds

        let result = policy.apply(Self.androidReferenceArtifact, guardrails: engine)
        XCTAssertTrue(result.applied, result.report)
        XCTAssertEqual(policy.activeVersion(), "policy_2026_08_24_reference")
        XCTAssertTrue(policy.isEnabled())

        // guardrails
        XCTAssertEqual(engine.maxInterstitialPerSession, 4)
        XCTAssertEqual(engine.minMinutesBetweenInterstitial, 5)
        XCTAssertEqual(engine.maxAds24h, 30)
        XCTAssertEqual(engine.churnRiskProtectionThreshold, 0.7, accuracy: 1e-9)
        // iap
        XCTAssertEqual(engine.maxIapOffersPerSession, 2)
        XCTAssertEqual(engine.minMinutesBetweenIapOffers, 15)
        XCTAssertEqual(engine.maxPurchasesPerSessionBeforeBreak, 2)
        XCTAssertEqual(engine.frustrationStreakThreshold, 4)
        // gameplay
        XCTAssertEqual(engine.maxAssistsPerSession, 4)
        // progression — including the daily offer cap, which Android reads HERE and not under iap
        XCTAssertEqual(engine.minLevelForInterstitial, 10)
        XCTAssertEqual(engine.minLevelForInterstitialAfterLoss, 22)
        XCTAssertEqual(engine.minMinutesAfterRewarded, 2)
        XCTAssertEqual(engine.skipFirstInterstitialsOnReopen, 1)
        XCTAssertEqual(engine.maxIapOffers24h, 2)
        // click_fraud — SECONDS on the wire, milliseconds in the knob
        XCTAssertEqual(ClickFraudGuard.maxClicksPerWindow, 5)
        XCTAssertEqual(ClickFraudGuard.windowMs, 120_000)
        XCTAssertEqual(ClickFraudGuard.lockoutMs, 900_000)
        // experiment
        XCTAssertEqual(HoldoutManager.holdoutFraction, 0.1, accuracy: 1e-9)
        XCTAssertEqual(HoldoutManager.salt, "brain_holdout_v2")
        XCTAssertEqual(AdsBrain.shared.rolloutFraction, 0.1, accuracy: 1e-9)
        XCTAssertEqual(try XCTUnwrap(policy.pendingExplorationFloor), 0.03, accuracy: 1e-9)
        // reward
        XCTAssertEqual(RewardModel.weightImmediateValue, 0.3, accuracy: 1e-9)
        XCTAssertEqual(RewardModel.weightContinuation, 0.7, accuracy: 1e-9)
        // scenarios
        XCTAssertEqual(policy.scenarioCap(.protectAndConvert), 1)
        XCTAssertEqual(policy.scenarioCap(.adForward), 4)
        XCTAssertEqual(policy.scenarioCap(.retentionFirst), 5)
        XCTAssertEqual(policy.scenarioCap(.balancedHybrid), 2)
        // likelihood_profiles
        guard let profile = policy.profileFor(.lowSkillHighChurnRisk) else {
            return XCTFail("the complete profile set must apply: \(result.report)")
        }
        XCTAssertEqual(profile.failRate, 0.60, accuracy: 1e-9)
        XCTAssertEqual(profile.rewardedAcceptRate, 0.45, accuracy: 1e-9)
        XCTAssertEqual(profile.payerPropensity, 0.15, accuracy: 1e-9)
        XCTAssertEqual(profile.churnTendency, 0.80, accuracy: 1e-9)
        XCTAssertNotNil(policy.profileFor(.highSkillLowTolerance))
        XCTAssertNotNil(policy.profileFor(.highSkillHighTolerance))
        XCTAssertNotNil(policy.profileFor(.casualSteady))
        // fx_rates / remove_ads_products / min_confidence
        XCTAssertEqual(FxRates.knownCurrencyCount(), 4)
        XCTAssertEqual(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "EUR") ?? 0, 1.08, accuracy: 1e-9)
        XCTAssertEqual(Entitlements.shared.removeAdsProductIds, ["premium_monthly", "premium_yearly", "no_ads_forever"])
        XCTAssertEqual(PersonalizationScenario.minConfidence, 0.55, accuracy: 1e-9)
        XCTAssertEqual(policy.minConfidence(), 0.55, accuracy: 1e-9)

        // ...and every one of those is a CHANGE. Without this half, a knob that was already
        // sitting on the artifact's value would let a mistyped field name pass unnoticed.
        XCTAssertNotEqual(engine.maxInterstitialPerSession, beforeInterstitial)
        XCTAssertNotEqual(engine.minMinutesBetweenInterstitial, beforeCooldown)
        XCTAssertNotEqual(engine.maxAds24h, beforeAds24h)
        XCTAssertNotEqual(engine.churnRiskProtectionThreshold, beforeChurn)
        XCTAssertNotEqual(engine.maxIapOffersPerSession, beforeOffers)
        XCTAssertNotEqual(engine.minMinutesBetweenIapOffers, beforeOfferCooldown)
        XCTAssertNotEqual(engine.maxPurchasesPerSessionBeforeBreak, beforeBreak)
        XCTAssertNotEqual(engine.frustrationStreakThreshold, beforeStreak)
        XCTAssertNotEqual(engine.maxAssistsPerSession, beforeAssists)
        XCTAssertNotEqual(engine.minLevelForInterstitial, beforeLevelGate)
        XCTAssertNotEqual(engine.minLevelForInterstitialAfterLoss, beforeLossGate)
        XCTAssertNotEqual(engine.minMinutesAfterRewarded, beforeAfterRewarded)
        XCTAssertNotEqual(engine.skipFirstInterstitialsOnReopen, beforeSkipOnReopen)
        XCTAssertNotEqual(engine.maxIapOffers24h, beforeOffers24h)
        XCTAssertNotEqual(ClickFraudGuard.maxClicksPerWindow, beforeClicks)
        XCTAssertNotEqual(ClickFraudGuard.windowMs, beforeWindowMs)
        XCTAssertNotEqual(ClickFraudGuard.lockoutMs, beforeLockoutMs)
        XCTAssertNotEqual(HoldoutManager.holdoutFraction, beforeHoldout)
        XCTAssertNotEqual(HoldoutManager.salt, beforeSalt)
        XCTAssertNotEqual(AdsBrain.shared.rolloutFraction, beforeRollout)
        XCTAssertNotEqual(policy.pendingExplorationFloor, beforeFloor)
        XCTAssertNotEqual(RewardModel.weightImmediateValue, beforeImmediate)
        XCTAssertNotEqual(RewardModel.weightContinuation, beforeContinuation)
        XCTAssertNotEqual(PersonalizationScenario.minConfidence, beforeConfidence)
        XCTAssertNotEqual(Entitlements.shared.removeAdsProductIds, beforeRemoveAds)
        XCTAssertNotEqual(policy.scenarioCap(.adForward), PersonalizationScenario.adForward.interstitialCapPerSession)

        // A well-formed reference artifact earns a clean report: no note at all.
        XCTAssertFalse(result.report.contains("\u{26A0}"), result.report)
        policy.resetForTest()
    }

    /// `appplugin/DOC/policy_artifact_example.json`, kept as a literal rather than read off disk —
    /// the Android repo is a sibling checkout this test target cannot depend on. The values marked
    /// below differ from the published example ONLY because the published value coincides with an
    /// iOS compiled default, which would make the "it moved" assertions vacuous.
    private static let androidReferenceArtifact = """
    {
      "version": "policy_2026_08_24_reference",
      "enabled": true,
      "guardrails": {
        "max_interstitial_per_session": 4,
        "min_minutes_between_interstitial": 5,
        "max_ads_24h": 30,
        "churn_risk_threshold": 0.7
      },
      "iap": {
        "max_offers_per_session": 2,
        "min_minutes_between_offers": 15,
        "max_purchases_per_session_before_break": 2,
        "frustration_streak_threshold": 4
      },
      "gameplay": { "max_assists_per_session": 4 },
      "progression": {
        "min_level_for_interstitial": 10,
        "min_level_for_interstitial_after_loss": 22,
        "min_minutes_after_rewarded": 2,
        "skip_first_interstitials_on_reopen": 1,
        "max_iap_offers_24h": 2
      },
      "click_fraud": {
        "max_clicks_per_window": 5,
        "window_seconds": 120,
        "lockout_seconds": 900
      },
      "experiment": {
        "holdout_fraction": 0.1,
        "holdout_salt": "brain_holdout_v2",
        "exploration_floor": 0.03,
        "rollout_fraction": 0.1
      },
      "reward": {
        "weight_immediate_value": 0.3,
        "weight_continuation": 0.7
      },
      "scenarios": {
        "PROTECT_AND_CONVERT": { "interstitial_cap": 1 },
        "AD_FORWARD":          { "interstitial_cap": 4 },
        "RETENTION_FIRST":     { "interstitial_cap": 5 },
        "BALANCED_HYBRID":     { "interstitial_cap": 2 }
      },
      "likelihood_profiles": {
        "HIGH_SKILL_LOW_TOLERANCE":  {"fail_rate":0.15,"rewarded_accept":0.20,"payer_propensity":0.70,"churn_tendency":0.20},
        "HIGH_SKILL_HIGH_TOLERANCE": {"fail_rate":0.15,"rewarded_accept":0.75,"payer_propensity":0.20,"churn_tendency":0.25},
        "LOW_SKILL_HIGH_CHURN_RISK": {"fail_rate":0.60,"rewarded_accept":0.45,"payer_propensity":0.15,"churn_tendency":0.80},
        "CASUAL_STEADY":             {"fail_rate":0.35,"rewarded_accept":0.50,"payer_propensity":0.35,"churn_tendency":0.35}
      },
      "fx_rates": { "EUR": 1.08, "GBP": 1.27, "JPY": 0.0064, "VND": 0.000039 },
      "remove_ads_products": ["premium_monthly", "premium_yearly", "no_ads_forever"],
      "min_confidence": 0.55
    }
    """

    // MARK: - §2.6 the nine HARD_* ceilings, attacked from above

    /// The example the spec names outright: an artifact asking for 9999 must come out at 60.
    func test_maxAds24hCannotExceedItsHardCeiling() {
        let (_, engine) = makePolicy(#"{"version": "p1", "guardrails": {"max_ads_24h": 9999}}"#)
        XCTAssertEqual(engine.maxAds24h, 60)
        XCTAssertEqual(engine.maxAds24h, BrainPolicy.hardMaxAds24h)
    }

    func test_everyFrequencyCapIsClampedToItsHardCeiling() {
        let json = """
        {
          "version": "p1",
          "guardrails": { "max_interstitial_per_session": 9999, "max_ads_24h": 9999 },
          "iap": {
            "max_offers_per_session": 9999,
            "max_purchases_per_session_before_break": 9999,
            "frustration_streak_threshold": 9999
          },
          "gameplay": { "max_assists_per_session": 9999 },
          "click_fraud": { "max_clicks_per_window": 9999 }
        }
        """
        let (policy, engine) = makePolicy(json)
        XCTAssertEqual(engine.maxInterstitialPerSession, BrainPolicy.hardMaxInterstitialPerSession)
        XCTAssertEqual(engine.maxAds24h, BrainPolicy.hardMaxAds24h)
        XCTAssertEqual(engine.maxIapOffersPerSession, BrainPolicy.hardMaxIapOffersPerSession)
        XCTAssertEqual(engine.maxPurchasesPerSessionBeforeBreak, BrainPolicy.hardMaxPurchasesPerSessionBeforeBreak)
        XCTAssertEqual(engine.frustrationStreakThreshold, BrainPolicy.hardMaxFrustrationStreakThreshold)
        XCTAssertEqual(engine.maxAssistsPerSession, BrainPolicy.hardMaxAssistsPerSession)
        XCTAssertEqual(ClickFraudGuard.maxClicksPerWindow, BrainPolicy.hardMaxClicksPerWindow)
        policy.resetForTest()
    }

    /// Cooldowns clamp on the LOWER bound: a SHORTER cooldown is the more aggressive direction,
    /// so that is the side an artifact must not be able to cross.
    func test_cooldownsAreClampedFromBelowNotAbove() {
        let json = """
        {
          "version": "p1",
          "guardrails": { "min_minutes_between_interstitial": 0 },
          "iap": { "min_minutes_between_offers": 0 }
        }
        """
        let (_, engine) = makePolicy(json)
        XCTAssertEqual(engine.minMinutesBetweenInterstitial, BrainPolicy.hardMinMinutesBetweenInterstitial)
        XCTAssertEqual(engine.minMinutesBetweenIapOffers, BrainPolicy.hardMinMinutesBetweenIapOffers)
    }

    /// ...and an artifact making them LONGER is honoured: the caps only forbid loosening.
    func test_anArtifactMayAlwaysMakeTheSdkMoreConservative() {
        let json = """
        {
          "version": "p1",
          "guardrails": { "max_ads_24h": 3, "max_interstitial_per_session": 1, "min_minutes_between_interstitial": 90 },
          "iap": { "min_minutes_between_offers": 240, "frustration_streak_threshold": 2 }
        }
        """
        let (_, engine) = makePolicy(json)
        XCTAssertEqual(engine.maxAds24h, 3)
        XCTAssertEqual(engine.maxInterstitialPerSession, 1)
        XCTAssertEqual(engine.minMinutesBetweenInterstitial, 90)
        XCTAssertEqual(engine.minMinutesBetweenIapOffers, 240)
        XCTAssertEqual(engine.frustrationStreakThreshold, 2)
    }

    /// §2.6, the subtle one: `frustrationStreakThreshold` clamps against the CONSTANT, not the
    /// value currently in force. Clamping against the current value would ratchet monotonically
    /// tighter on every fetch and an operator could never relax it again.
    func test_frustrationStreakThresholdClampsAgainstTheConstantNotTheCurrentValue() {
        let engine = GuardrailEngine()
        let (policy, _) = makePolicy(#"{"version": "p1", "iap": {"frustration_streak_threshold": 2}}"#, guardrails: engine)
        XCTAssertEqual(engine.frustrationStreakThreshold, 2)

        _ = policy.apply(#"{"version": "p2", "iap": {"frustration_streak_threshold": 4}}"#, guardrails: engine)
        XCTAssertEqual(engine.frustrationStreakThreshold, 4, "a later artifact must be able to relax it again")

        _ = policy.apply(#"{"version": "p3", "iap": {"frustration_streak_threshold": 9999}}"#, guardrails: engine)
        XCTAssertEqual(engine.frustrationStreakThreshold, BrainPolicy.hardMaxFrustrationStreakThreshold)
    }

    /// ═══ THE VALUES, NOT MERELY "SOMETHING POSITIVE" ═══
    ///
    /// These nine numbers are a contract with the Android build: the same artifact, authored once
    /// by ops against `BrainPolicy.kt`, is clamped on both platforms and must be clamped the same
    /// way. Asserting `> 0` let every one of them drift — `hardMaxInterstitialPerSession` shipped
    /// at 12 against Android's 10, and `hardMaxFrustrationStreakThreshold` at 10 against 5, which
    /// is a ceiling that lets the SDK monetize a player twice as deep into a losing streak as the
    /// safety review signed off on. So each is pinned to the literal in
    /// `com.app.plugin.brain.BrainPolicy`.
    func test_theNineHardCeilingsCarryAndroidsExactValues() {
        XCTAssertEqual(BrainPolicy.hardMaxInterstitialPerSession, 10)
        XCTAssertEqual(BrainPolicy.hardMaxAds24h, 60)
        XCTAssertEqual(BrainPolicy.hardMinMinutesBetweenInterstitial, 1)
        XCTAssertEqual(BrainPolicy.hardMaxClicksPerWindow, 12)
        XCTAssertEqual(BrainPolicy.hardMaxIapOffersPerSession, 6)
        XCTAssertEqual(BrainPolicy.hardMinMinutesBetweenIapOffers, 3)
        XCTAssertEqual(BrainPolicy.hardMaxPurchasesPerSessionBeforeBreak, 5)
        XCTAssertEqual(BrainPolicy.hardMaxFrustrationStreakThreshold, 5)
        XCTAssertEqual(BrainPolicy.hardMaxAssistsPerSession, 8)
    }

    // MARK: - §2.1 the deviation the whole port turns on

    /// ═══ THE TEST THAT WOULD CRASH A NAIVE PORT ═══
    ///
    /// Kotlin's `Double.toInt()` SATURATES (`1e300.toInt()` is `Int.MAX_VALUE`, no exception), so
    /// Android's `num()`-then-`toInt()` parsing is crash-proof by language accident. Swift's
    /// `Int(someDouble)` TRAPS. A policy artifact is operator-controlled remote config:
    /// `{"max_ads_24h": 1e300}` would take down every app instance that fetched it — a crash that
    /// does not exist in the original.
    ///
    /// If any field regresses to a bare `Int(...)`, this does not fail an assertion; it kills the
    /// test process.
    func test_everyNumericFieldSurvivesAHostileLiteralWithoutTrapping() {
        for literal in Self.hostileLiterals {
            let engine = GuardrailEngine()
            let policy = BrainPolicy()
            let result = policy.apply(artifactWithEveryNumericField(literal), guardrails: engine)
            XCTAssertTrue(result.applied, "\(literal): \(result.report)")
            XCTAssertEqual(policy.activeVersion(), "hostile_v1", literal)
            policy.resetForTest()
        }
    }

    /// ...and the clamp is not merely "did not crash": an overflowing value lands exactly on the
    /// bound, which for a capped field is the HARD ceiling — so the trap-guard and the safety cap
    /// are one operation rather than two chances to forget.
    func test_anOverflowingLiteralLandsExactlyOnTheHardCeiling() throws {
        let engine = GuardrailEngine()
        let policy = BrainPolicy()
        _ = policy.apply(artifactWithEveryNumericField("1e300"), guardrails: engine)

        XCTAssertEqual(engine.maxAds24h, BrainPolicy.hardMaxAds24h)
        XCTAssertEqual(engine.maxInterstitialPerSession, BrainPolicy.hardMaxInterstitialPerSession)
        XCTAssertEqual(engine.maxIapOffersPerSession, BrainPolicy.hardMaxIapOffersPerSession)
        XCTAssertEqual(ClickFraudGuard.maxClicksPerWindow, BrainPolicy.hardMaxClicksPerWindow)
        XCTAssertEqual(engine.maxPurchasesPerSessionBeforeBreak, BrainPolicy.hardMaxPurchasesPerSessionBeforeBreak)
        XCTAssertEqual(engine.frustrationStreakThreshold, BrainPolicy.hardMaxFrustrationStreakThreshold)
        XCTAssertEqual(engine.maxAssistsPerSession, BrainPolicy.hardMaxAssistsPerSession)
        XCTAssertLessThanOrEqual(HoldoutManager.holdoutFraction, 0.5)
        XCTAssertLessThanOrEqual(PersonalizationScenario.minConfidence, 1.0)
        XCTAssertLessThanOrEqual(try XCTUnwrap(policy.pendingExplorationFloor), 0.5)
        policy.resetForTest()
    }

    /// A 20-digit literal is the OTHER half of §2.1 and it is not the same bug: it decodes as an
    /// `NSDecimalNumber`, so `as? Int` returns `nil` and `as? Double` is what reads it.
    func test_aTwentyDigitLiteralIsReadAndClampedRatherThanIgnored() {
        let engine = GuardrailEngine()
        let policy = BrainPolicy()
        _ = policy.apply(#"{"version": "p1", "guardrails": {"max_ads_24h": 99999999999999999999}}"#, guardrails: engine)
        XCTAssertEqual(engine.maxAds24h, BrainPolicy.hardMaxAds24h, "not silently ignored, not trapped — clamped")
        policy.resetForTest()
    }

    /// A NEGATIVE overflow lands on the LOWER bound, which for a cooldown is the hard floor.
    func test_aNegativeOverflowLandsOnTheLowerBound() {
        let json = """
        {
          "version": "p1",
          "guardrails": { "min_minutes_between_interstitial": -1e300, "max_ads_24h": -1e300 },
          "iap": { "min_minutes_between_offers": -1e300 }
        }
        """
        let (_, engine) = makePolicy(json)
        XCTAssertEqual(engine.minMinutesBetweenInterstitial, BrainPolicy.hardMinMinutesBetweenInterstitial)
        XCTAssertEqual(engine.minMinutesBetweenIapOffers, BrainPolicy.hardMinMinutesBetweenIapOffers)
        XCTAssertEqual(engine.maxAds24h, 0, "a frequency cap floors at 0 — Android's clampUpper ends in coerceAtLeast(0)")
    }

    func test_aNonNumericFieldIsDroppedWithANoteAndLeavesItsKnobAlone() {
        let engine = GuardrailEngine()
        let before = engine.maxAds24h
        let (_, _) = makePolicy(#"{"version": "p1", "guardrails": {"max_ads_24h": "lots"}}"#, guardrails: engine)
        XCTAssertEqual(engine.maxAds24h, before)
    }

    /// `true as? Int` succeeds and returns `1` in Swift, so without `JsonConfigValue`'s boolean
    /// discriminator `{"max_ads_24h": true}` would become a real one-ad-per-day cap.
    func test_aBooleanWhereANumberBelongsIsRefused() {
        let engine = GuardrailEngine()
        let before = engine.maxAds24h
        let (policy, _) = makePolicy(#"{"version": "p1", "guardrails": {"max_ads_24h": true}}"#, guardrails: engine)
        XCTAssertEqual(engine.maxAds24h, before)
        XCTAssertEqual(policy.activeVersion(), "p1")
    }

    // MARK: - Blocks

    func test_theGuardrailBlockAppliesEveryFieldItCarries() {
        let json = """
        {
          "version": "p1",
          "guardrails": {
            "max_interstitial_per_session": 4,
            "min_minutes_between_interstitial": 7,
            "max_ads_24h": 25,
            "churn_risk_threshold": 0.6
          }
        }
        """
        let (_, engine) = makePolicy(json)
        XCTAssertEqual(engine.maxInterstitialPerSession, 4)
        XCTAssertEqual(engine.minMinutesBetweenInterstitial, 7)
        XCTAssertEqual(engine.maxAds24h, 25)
        XCTAssertEqual(engine.churnRiskProtectionThreshold, 0.6, accuracy: 1e-9)
    }

    func test_theIapBlockAppliesEveryFieldItCarries() {
        let json = """
        {
          "version": "p1",
          "iap": {
            "max_offers_per_session": 2,
            "min_minutes_between_offers": 20,
            "max_purchases_per_session_before_break": 2,
            "frustration_streak_threshold": 3
          }
        }
        """
        let (_, engine) = makePolicy(json)
        XCTAssertEqual(engine.maxIapOffersPerSession, 2)
        XCTAssertEqual(engine.minMinutesBetweenIapOffers, 20)
        XCTAssertEqual(engine.maxPurchasesPerSessionBeforeBreak, 2)
        XCTAssertEqual(engine.frustrationStreakThreshold, 3)
    }

    func test_theGameplayAndProgressionBlocksApply() {
        let json = """
        {
          "version": "p1",
          "gameplay": { "max_assists_per_session": 1 },
          "progression": {
            "min_level_for_interstitial": 10,
            "min_level_for_interstitial_after_loss": 22,
            "min_minutes_after_rewarded": 4,
            "skip_first_interstitials_on_reopen": 2,
            "max_iap_offers_24h": 5
          }
        }
        """
        let (_, engine) = makePolicy(json)
        XCTAssertEqual(engine.maxAssistsPerSession, 1)
        XCTAssertEqual(engine.minLevelForInterstitial, 10)
        XCTAssertEqual(engine.minLevelForInterstitialAfterLoss, 22)
        XCTAssertEqual(engine.minMinutesAfterRewarded, 4)
        XCTAssertEqual(engine.skipFirstInterstitialsOnReopen, 2)
        XCTAssertEqual(engine.maxIapOffers24h, 5, "Android reads the daily offer cap under 'progression', not 'iap'")
    }

    /// Android's one cross-field sanity check: a loss-branch gate that opens EARLIER than the
    /// win-branch gate means the player who just lost gets hit before the player who just won.
    /// Both values still apply — the note is what tells the operator they transposed them.
    func test_aLossGateBelowTheWinGateIsNotedButStillApplied() {
        let engine = GuardrailEngine()
        let policy = BrainPolicy()
        let result = policy.apply(
            #"{"version": "p1", "progression": {"min_level_for_interstitial": 22, "min_level_for_interstitial_after_loss": 10}}"#,
            guardrails: engine
        )
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("loss-branch gate"), result.report)
        XCTAssertEqual(engine.minLevelForInterstitialAfterLoss, 10)
        policy.resetForTest()
    }

    /// `0` is a LEGITIMATE value for the fields documented as "0 = OFF", so it must not be
    /// dropped as "not a positive number". This is the acceptance rule `JsonConfigValue.numeric`
    /// gained an explicit flag for.
    /// The spec names `max_ads_24h: 0` specifically as the way an operator switches ads off from
    /// the artifact. Before the correction pass that field was floored at 1, so `numeric` DROPPED
    /// it and the knob silently kept its compiled default of 40 a day — an operator turning ads off
    /// and getting 40. Proven here with the literal, not inferred from a sibling field sharing the
    /// same helper.
    func test_maxAds24hOfZeroReachesTheKnobRatherThanBeingDropped() {
        let engine = GuardrailEngine()
        engine.maxAds24h = 40
        _ = makePolicy(#"{"version": "p_zero_ads", "guardrails": {"max_ads_24h": 0}}"#, guardrails: engine)
        XCTAssertEqual(
            engine.maxAds24h, 0,
            "0 is a value an operator means, not a missing field — dropping it leaves ads running"
        )
    }

    /// `max_iap_offers_24h` is the ONE guardrail knob with no hard ceiling: it defaults to 0 (OFF),
    /// so every value an artifact can set is a tightening and there is nothing for a ceiling to
    /// protect. That "no ceiling" property was asserted only by a doc comment; this pins it, and
    /// distinguishes the field from its eight capped siblings.
    ///
    /// The 10_000 bound it does carry is a trap-guard for `Int(Double)`, not a safety cap — so a
    /// large-but-plausible value must pass through UNCLAMPED.
    func test_maxIapOffers24hHasNoHardCeilingUnlikeItsCappedSiblings() {
        let engine = GuardrailEngine()
        _ = makePolicy(
            #"{"version": "p_no_ceiling", "progression": {"max_iap_offers_24h": 9999}}"#,
            guardrails: engine
        )
        XCTAssertEqual(
            engine.maxIapOffers24h, 9999,
            "this knob has no HARD_* ceiling; 9999 must survive intact, unlike a capped sibling"
        )

        // The trap-guard still holds against a value that would crash the Int conversion.
        _ = makePolicy(
            #"{"version": "p_no_ceiling_2", "progression": {"max_iap_offers_24h": 1e300}}"#,
            guardrails: engine
        )
        XCTAssertLessThanOrEqual(engine.maxIapOffers24h, 10_000, "the trap-guard bounds it")
        XCTAssertGreaterThan(engine.maxIapOffers24h, 0, "and it does not trap or collapse to zero")
    }

    func test_zeroIsAcceptedWhereZeroMeansOff() {
        let engine = GuardrailEngine()
        engine.minLevelForInterstitial = 10
        engine.maxIapOffers24h = 6
        engine.maxAssistsPerSession = 3
        let json = """
        {
          "version": "p1",
          "gameplay": { "max_assists_per_session": 0 },
          "progression": { "min_level_for_interstitial": 0, "skip_first_interstitials_on_reopen": 0, "max_iap_offers_24h": 0 }
        }
        """
        _ = makePolicy(json, guardrails: engine)
        XCTAssertEqual(engine.maxIapOffers24h, 0)
        XCTAssertEqual(engine.maxAssistsPerSession, 0)
        XCTAssertEqual(engine.minLevelForInterstitial, 0)
        XCTAssertEqual(engine.skipFirstInterstitialsOnReopen, 0)
    }

    /// ═══ THE ONE BLOCK WHOSE WIRE UNIT IS NOT THE KNOB'S UNIT ═══
    ///
    /// Android reads SECONDS and multiplies by 1000 on the way into two millisecond-valued knobs.
    /// Reading them as milliseconds made the schema's own reference artifact — `window_seconds:
    /// 300` — a 300-MILLISECOND window: a fraud guard that never fires, configured by an ops team
    /// who had every reason to believe they had set five minutes.
    func test_theClickFraudBlockReadsSecondsAndStoresMilliseconds() {
        let json = """
        {"version": "p1", "click_fraud": {"max_clicks_per_window": 4, "window_seconds": 300, "lockout_seconds": 1800}}
        """
        let (policy, _) = makePolicy(json)
        XCTAssertEqual(ClickFraudGuard.maxClicksPerWindow, 4)
        XCTAssertEqual(ClickFraudGuard.windowMs, 300_000, "300 seconds is five minutes, not a third of a second")
        XCTAssertEqual(ClickFraudGuard.lockoutMs, 1_800_000)
        policy.resetForTest()
    }

    /// The floors differ, and Android's are not symmetric: a window floors at one SECOND
    /// (`coerceAtLeast(1000L)`), a lockout at ZERO (`coerceAtLeast(0L)`) — because a zero lockout
    /// is how an operator turns the lockout off, and rounding it up to a second refuses that.
    func test_theClickFraudFloorsAreAndroidsAsymmetricOnes() {
        let json = """
        {"version": "p1", "click_fraud": {"window_seconds": 0, "lockout_seconds": 0}}
        """
        let (policy, _) = makePolicy(json)
        XCTAssertEqual(ClickFraudGuard.windowMs, 1_000)
        XCTAssertEqual(ClickFraudGuard.lockoutMs, 0)
        policy.resetForTest()
    }

    func test_theExperimentBlockApplies() throws {
        let json = """
        {
          "version": "p1",
          "experiment": {
            "holdout_fraction": 0.2,
            "holdout_salt": "brain_holdout_v9",
            "rollout_fraction": 0.5,
            "exploration_floor": 0.11
          }
        }
        """
        let (policy, _) = makePolicy(json)
        XCTAssertEqual(HoldoutManager.holdoutFraction, 0.2, accuracy: 1e-9)
        XCTAssertEqual(HoldoutManager.salt, "brain_holdout_v9")
        XCTAssertEqual(AdsBrain.shared.rolloutFraction, 0.5, accuracy: 1e-9)
        XCTAssertEqual(try XCTUnwrap(policy.pendingExplorationFloor), 0.11, accuracy: 1e-9)
        policy.resetForTest()
    }

    /// Fractions clamp in `Double` space, and the three bounds are NOT the same. `holdout_fraction`
    /// and `exploration_floor` stop at 0.5 (Android's `coerceIn(0.0, 0.5)`): holding back — or
    /// exploring on — more than half the population is a configuration error, and clamping either
    /// to 1.0 would put every player in the control group, the experiment silently measuring
    /// nothing. `rollout_fraction` goes to 1.0, because 100% is where a staged rollout ends up.
    func test_fractionsAreClampedToTheirOwnBoundsNotAllToOne() throws {
        let json = """
        {"version": "p1", "experiment": {"holdout_fraction": 42, "rollout_fraction": 9, "exploration_floor": 9}}
        """
        let (policy, _) = makePolicy(json)
        XCTAssertEqual(HoldoutManager.holdoutFraction, 0.5, accuracy: 1e-9)
        XCTAssertEqual(AdsBrain.shared.rolloutFraction, 1.0, accuracy: 1e-9)
        XCTAssertEqual(try XCTUnwrap(policy.pendingExplorationFloor), 0.5, accuracy: 1e-9)
        policy.resetForTest()
    }

    /// The two settings that switch a subsystem off carry a note when they do, so an operator who
    /// zeroed one by accident reads it in the report instead of wondering why nothing happens.
    func test_azeroRolloutAndAzeroExplorationFloorAreBothNoted() {
        let engine = GuardrailEngine()
        let policy = BrainPolicy()
        let result = policy.apply(
            #"{"version": "p1", "experiment": {"rollout_fraction": 0, "exploration_floor": 0}}"#,
            guardrails: engine
        )
        XCTAssertTrue(result.report.contains("rollout_fraction=0"), result.report)
        XCTAssertTrue(result.report.contains("exploration_floor=0"), result.report)
        policy.resetForTest()
    }

    func test_anUnusableSaltIsDroppedAndTheRunningSaltSurvives() {
        let before = HoldoutManager.salt
        let huge = String(repeating: "s", count: JsonConfigValue.maxIdentifierLength + 1)
        let (policy, _) = makePolicy(#"{"version": "p1", "experiment": {"holdout_salt": "\#(huge)"}}"#)
        XCTAssertEqual(HoldoutManager.salt, before, "re-salting the whole population on a typo is not recoverable")
        policy.resetForTest()
    }

    func test_theFxRatesBlockAppliesAndDropsUnusableEntries() {
        let json = #"{"version": "p1", "fx_rates": {"vnd": 0.00004, "EUR": 1.09, "XXX": "free", "YYY": 0}}"#
        let (policy, _) = makePolicy(json)
        XCTAssertEqual(FxRates.knownCurrencyCount(), 2)
        XCTAssertEqual(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "VND") ?? 0, 0.00004, accuracy: 1e-12)
        XCTAssertNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "XXX"))
        policy.resetForTest()
    }

    /// `setRates` REPLACES the whole table, so Android applies it only when at least one rate
    /// survived parsing. Blanking the USD column for every non-USD market on the strength of one
    /// mistyped rate is the failure this avoids.
    func test_anFxBlockWithNoUsableRateKeepsTheRunningTable() {
        let (policy, engine) = makePolicy(#"{"version": "p1", "fx_rates": {"EUR": 1.09}}"#)
        XCTAssertEqual(FxRates.knownCurrencyCount(), 1)
        _ = policy.apply(#"{"version": "p2", "fx_rates": {"EUR": "one point oh nine"}}"#, guardrails: engine)
        XCTAssertEqual(FxRates.knownCurrencyCount(), 1, "one mistyped rate must not blank the table")
        policy.resetForTest()
    }

    func test_theRemoveAdsProductsBlockApplies() {
        let json = #"{"version": "p1", "remove_ads_products": ["com.x.noads", "com.x.vip"]}"#
        let (policy, _) = makePolicy(json)
        XCTAssertEqual(Entitlements.shared.removeAdsProductIds, ["com.x.noads", "com.x.vip"])
        policy.resetForTest()
    }

    /// `Entitlements` persists this set joined with `|`, so a product id containing one would not
    /// survive its own round trip — the `RecurringGrants`-class defect, in a different subsystem.
    func test_aRemoveAdsProductIdContainingThePersistenceDelimiterIsDropped() {
        let json = #"{"version": "p1", "remove_ads_products": ["com.x|noads", "com.x.ok", 7]}"#
        let (policy, _) = makePolicy(json)
        XCTAssertEqual(Entitlements.shared.removeAdsProductIds, ["com.x.ok"])
        policy.resetForTest()
    }

    func test_theRewardBlockAppliesAndClamps() {
        let (policy, _) = makePolicy(#"{"version": "p1", "reward": {"weight_immediate_value": 0.25, "weight_continuation": 5}}"#)
        XCTAssertEqual(RewardModel.weightImmediateValue, 0.25, accuracy: 1e-9)
        XCTAssertEqual(RewardModel.weightContinuation, 1.0, accuracy: 1e-9)
        policy.resetForTest()
    }

    /// Android warns when the weights invert the ordering the reward model exists to encode:
    /// "showed an ad and lost the player" must always score BELOW "stayed quiet and kept them".
    /// The weights still apply — this is a governance parameter, and the report is the audit trail.
    func test_rewardWeightsThatLoseTheAntiDegeneracyPropertyAreNoted() {
        let engine = GuardrailEngine()
        let policy = BrainPolicy()
        let result = policy.apply(
            #"{"version": "p1", "reward": {"weight_immediate_value": 0.9, "weight_continuation": 0.1}}"#,
            guardrails: engine
        )
        XCTAssertTrue(result.report.contains("anti-degeneracy"), result.report)
        XCTAssertEqual(RewardModel.weightImmediateValue, 0.9, accuracy: 1e-9)

        let healthy = policy.apply(
            #"{"version": "p2", "reward": {"weight_immediate_value": 0.3, "weight_continuation": 0.7}}"#,
            guardrails: engine
        )
        XCTAssertFalse(healthy.report.contains("anti-degeneracy"), healthy.report)
        policy.resetForTest()
    }

    func test_minConfidenceAppliesToTheScenarioResolverAndIsReadableBack() {
        let (policy, _) = makePolicy(#"{"version": "p1", "min_confidence": 0.75}"#)
        XCTAssertEqual(policy.minConfidence(), 0.75, accuracy: 1e-9)
        XCTAssertEqual(PersonalizationScenario.minConfidence, 0.75, accuracy: 1e-9)
        policy.resetForTest()
    }

    // MARK: - Scenarios

    func test_aScenarioCapOverridesTheCompiledDefault() {
        let json = #"{"version": "p1", "scenarios": {"AD_FORWARD": {"interstitial_cap": 2}}}"#
        let (policy, _) = makePolicy(json)
        XCTAssertEqual(policy.scenarioCap(.adForward), 2)
        XCTAssertEqual(
            policy.scenarioCap(.retentionFirst),
            PersonalizationScenario.retentionFirst.interstitialCapPerSession,
            "an unmentioned scenario keeps its compiled default"
        )
    }

    func test_aScenarioCapIsClampedToTheSessionHardCeiling() {
        let json = #"{"version": "p1", "scenarios": {"AD_FORWARD": {"interstitial_cap": 9999}}}"#
        let (policy, _) = makePolicy(json)
        XCTAssertEqual(policy.scenarioCap(.adForward), BrainPolicy.hardMaxInterstitialPerSession)
    }

    /// §5: an unknown scenario name is SKIPPED with a note, never a reason to reject the whole
    /// artifact — a newer backend naming a segment this SDK build does not have yet must not
    /// switch off every other tuning knob in the same payload.
    func test_anUnknownScenarioNameIsSkippedWithoutKillingTheArtifact() {
        let json = """
        {
          "version": "p1",
          "guardrails": {"max_ads_24h": 11},
          "scenarios": {"NOT_A_SCENARIO": {"interstitial_cap": 2}, "AD_FORWARD": {"interstitial_cap": 3}}
        }
        """
        let (policy, engine) = makePolicy(json)
        let result = policy.apply(json, guardrails: engine)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("NOT_A_SCENARIO"), result.report)
        XCTAssertEqual(policy.scenarioCap(.adForward), 3)
        XCTAssertEqual(engine.maxAds24h, 11, "the rest of the artifact still applied")
    }

    // MARK: - Likelihood profiles: the one all-or-nothing block

    private static let fullProfiles = """
    {
      "HIGH_SKILL_LOW_TOLERANCE": { "fail_rate": 0.11, "rewarded_accept": 0.22, "payer_propensity": 0.33, "churn_tendency": 0.44 },
      "HIGH_SKILL_HIGH_TOLERANCE": { "fail_rate": 0.12, "rewarded_accept": 0.23, "payer_propensity": 0.34, "churn_tendency": 0.45 },
      "LOW_SKILL_HIGH_CHURN_RISK": { "fail_rate": 0.13, "rewarded_accept": 0.24, "payer_propensity": 0.35, "churn_tendency": 0.46 },
      "CASUAL_STEADY": { "fail_rate": 0.14, "rewarded_accept": 0.25, "payer_propensity": 0.36, "churn_tendency": 0.47 }
    }
    """

    func test_aCompleteLikelihoodProfileSetIsApplied() {
        let (policy, _) = makePolicy(#"{"version": "p1", "likelihood_profiles": \#(Self.fullProfiles)}"#)
        guard let profile = policy.profileFor(.highSkillLowTolerance) else {
            return XCTFail("the complete set must apply")
        }
        XCTAssertEqual(profile.failRate, 0.11, accuracy: 1e-9)
        XCTAssertEqual(profile.rewardedAcceptRate, 0.22, accuracy: 1e-9)
        XCTAssertEqual(profile.payerPropensity, 0.33, accuracy: 1e-9)
        XCTAssertEqual(profile.churnTendency, 0.44, accuracy: 1e-9)
        XCTAssertNotNil(policy.profileFor(.casualSteady))
    }

    /// §3: the ONE block that is all-or-nothing. Mixing newly calibrated profiles with compiled
    /// ones makes cross-segment comparison meaningless — the numbers would no longer be on the
    /// same scale, and the argmax between them would be an artefact of the mix.
    func test_aPartialLikelihoodProfileSetIsDroppedWholesale() {
        let partial = """
        {
          "HIGH_SKILL_LOW_TOLERANCE": { "fail_rate": 0.11, "rewarded_accept": 0.22, "payer_propensity": 0.33, "churn_tendency": 0.44 },
          "CASUAL_STEADY": { "fail_rate": 0.14, "rewarded_accept": 0.25, "payer_propensity": 0.36, "churn_tendency": 0.47 }
        }
        """
        let json = #"{"version": "p1", "guardrails": {"max_ads_24h": 9}, "likelihood_profiles": \#(partial)}"#
        let (policy, engine) = makePolicy(json)
        XCTAssertNil(policy.profileFor(.highSkillLowTolerance), "a partial set names nobody")
        XCTAssertNil(policy.profileFor(.casualSteady))
        XCTAssertEqual(engine.maxAds24h, 9, "...but the rest of the artifact still applies")
    }

    /// One unreadable FIELD inside one profile is the same situation as a missing profile: the
    /// set is incomplete, so it is dropped wholesale rather than half-applied.
    func test_oneUnreadableFieldDropsTheWholeProfileSet() {
        let broken = Self.fullProfiles.replacingOccurrences(of: "\"fail_rate\": 0.13", with: "\"fail_rate\": \"high\"")
        let (policy, _) = makePolicy(#"{"version": "p1", "likelihood_profiles": \#(broken)}"#)
        XCTAssertNil(policy.profileFor(.highSkillLowTolerance))
    }

    func test_anUnknownLatentTypeNameIsNotedAndDropsTheSet() {
        let extra = Self.fullProfiles.replacingOccurrences(of: "\"CASUAL_STEADY\"", with: "\"NOT_A_TYPE\"")
        let (policy, engine) = makePolicy(#"{"version": "p1", "likelihood_profiles": \#(extra)}"#)
        let result = policy.apply(#"{"version": "p2", "likelihood_profiles": \#(extra)}"#, guardrails: engine)
        XCTAssertTrue(result.applied, "an unknown type name never rejects the artifact")
        XCTAssertTrue(result.report.contains("NOT_A_TYPE"), result.report)
        XCTAssertNil(policy.profileFor(.casualSteady))
    }

    /// A later artifact that drops the block entirely reverts to the compiled profiles — the
    /// remote set is an override, never an accumulation.
    func test_aLaterArtifactWithoutProfilesClearsThePreviousOnes() {
        let engine = GuardrailEngine()
        let (policy, _) = makePolicy(#"{"version": "p1", "likelihood_profiles": \#(Self.fullProfiles)}"#, guardrails: engine)
        XCTAssertNotNil(policy.profileFor(.casualSteady))
        _ = policy.apply(#"{"version": "p2"}"#, guardrails: engine)
        XCTAssertNil(policy.profileFor(.casualSteady))
    }

    // MARK: - Degradation

    func test_aBlockThatIsNotAnObjectIsNotedAndTheRestOfTheArtifactApplies() {
        let json = #"{"version": "p1", "guardrails": "tighter", "iap": 7, "max_ads_24h": 5, "click_fraud": [], "gameplay": {"max_assists_per_session": 1}}"#
        let engine = GuardrailEngine()
        let policy = BrainPolicy()
        let result = policy.apply(json, guardrails: engine)
        XCTAssertTrue(result.applied, result.report)
        XCTAssertTrue(result.report.contains("guardrails"), result.report)
        XCTAssertEqual(engine.maxAssistsPerSession, 1)
        policy.resetForTest()
    }

    func test_anArtifactWithNoBlocksAtAllAppliesAndChangesNothing() {
        let engine = GuardrailEngine()
        let before = (engine.maxAds24h, engine.maxInterstitialPerSession, engine.frustrationStreakThreshold)
        let (policy, _) = makePolicy(#"{"version": "p1"}"#, guardrails: engine)
        XCTAssertEqual(policy.activeVersion(), "p1")
        XCTAssertEqual(engine.maxAds24h, before.0)
        XCTAssertEqual(engine.maxInterstitialPerSession, before.1)
        XCTAssertEqual(engine.frustrationStreakThreshold, before.2)
    }

    func test_theReportNamesTheVersionAndEveryNote() {
        let engine = GuardrailEngine()
        let policy = BrainPolicy()
        let result = policy.apply(#"{"version": "p1", "enabled": "yes", "guardrails": {"max_ads_24h": "lots"}}"#, guardrails: engine)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("p1"), result.report)
        XCTAssertTrue(result.report.contains("enabled"), result.report)
        XCTAssertTrue(result.report.contains("max_ads_24h"), result.report)
        policy.resetForTest()
    }

    // MARK: - §2.5 `resetForTest()` reverts the DOWNSTREAM knobs, not just its own fields

    /// ═══ THE GAP ANDROID KNOWS ABOUT AND NEVER ASSERTS ═══
    ///
    /// Android's `clear()` resets only `BrainPolicy`'s own three fields; every value it wrote
    /// into `GuardrailEngine`, `ClickFraudGuard`, `HoldoutManager`, `AdsBrain.rolloutFraction`,
    /// `RewardModel`, `FxRates` and `Entitlements` survives. Both Android test files work around
    /// it by manually resetting those singletons in `tearDown`. A reset that leaves half the
    /// system configured is a test-isolation bug waiting to be blamed on something else.
    func test_resetForTestRevertsEveryDownstreamKnobItWrote() {
        let engine = GuardrailEngine()
        let before = (
            maxInterstitialPerSession: engine.maxInterstitialPerSession,
            minMinutesBetweenInterstitial: engine.minMinutesBetweenInterstitial,
            maxAds24h: engine.maxAds24h,
            churnRiskProtectionThreshold: engine.churnRiskProtectionThreshold,
            maxIapOffersPerSession: engine.maxIapOffersPerSession,
            minMinutesBetweenIapOffers: engine.minMinutesBetweenIapOffers,
            maxIapOffers24h: engine.maxIapOffers24h,
            maxPurchasesPerSessionBeforeBreak: engine.maxPurchasesPerSessionBeforeBreak,
            frustrationStreakThreshold: engine.frustrationStreakThreshold,
            maxAssistsPerSession: engine.maxAssistsPerSession,
            minLevelForInterstitial: engine.minLevelForInterstitial,
            minLevelForInterstitialAfterLoss: engine.minLevelForInterstitialAfterLoss,
            minMinutesAfterRewarded: engine.minMinutesAfterRewarded,
            skipFirstInterstitialsOnReopen: engine.skipFirstInterstitialsOnReopen
        )
        let beforeClicks = ClickFraudGuard.maxClicksPerWindow
        let beforeWindow = ClickFraudGuard.windowMs
        let beforeLockout = ClickFraudGuard.lockoutMs
        let beforeHoldout = HoldoutManager.holdoutFraction
        let beforeSalt = HoldoutManager.salt
        let beforeRollout = AdsBrain.shared.rolloutFraction
        let beforeImmediate = RewardModel.weightImmediateValue
        let beforeContinuation = RewardModel.weightContinuation
        let beforeConfidence = PersonalizationScenario.minConfidence
        let beforeRemoveAds = Entitlements.shared.removeAdsProductIds

        let json = """
        {
          "version": "p1",
          "guardrails": {"max_interstitial_per_session": 2, "min_minutes_between_interstitial": 30, "max_ads_24h": 7, "churn_risk_threshold": 0.3},
          "iap": {"max_offers_per_session": 1, "min_minutes_between_offers": 60, "max_purchases_per_session_before_break": 1, "frustration_streak_threshold": 2},
          "gameplay": {"max_assists_per_session": 1},
          "progression": {"min_level_for_interstitial": 10, "min_level_for_interstitial_after_loss": 22, "min_minutes_after_rewarded": 4, "skip_first_interstitials_on_reopen": 3, "max_iap_offers_24h": 2},
          "click_fraud": {"max_clicks_per_window": 2, "window_seconds": 60, "lockout_seconds": 60},
          "experiment": {"holdout_fraction": 0.4, "holdout_salt": "changed_salt", "rollout_fraction": 0.2, "exploration_floor": 0.3},
          "fx_rates": {"EUR": 1.1},
          "remove_ads_products": ["com.x.noads"],
          "reward": {"weight_immediate_value": 0.9, "weight_continuation": 0.1},
          "scenarios": {"AD_FORWARD": {"interstitial_cap": 1}},
          "likelihood_profiles": \(Self.fullProfiles),
          "min_confidence": 0.9
        }
        """
        let policy = BrainPolicy()
        XCTAssertTrue(policy.apply(json, guardrails: engine).applied)

        // Everything really did move — otherwise the reset assertions below prove nothing.
        XCTAssertNotEqual(engine.maxAds24h, before.maxAds24h)
        XCTAssertNotEqual(ClickFraudGuard.maxClicksPerWindow, beforeClicks)
        XCTAssertNotEqual(HoldoutManager.salt, beforeSalt)
        XCTAssertNotEqual(AdsBrain.shared.rolloutFraction, beforeRollout)
        XCTAssertNotEqual(RewardModel.weightImmediateValue, beforeImmediate)
        XCTAssertNotEqual(PersonalizationScenario.minConfidence, beforeConfidence)
        XCTAssertNotEqual(Entitlements.shared.removeAdsProductIds, beforeRemoveAds)
        XCTAssertEqual(FxRates.knownCurrencyCount(), 1)

        policy.resetForTest()

        XCTAssertEqual(policy.activeVersion(), "not loaded")
        XCTAssertTrue(policy.isEnabled())
        XCTAssertNil(policy.profileFor(.casualSteady))
        XCTAssertEqual(policy.scenarioCap(.adForward), PersonalizationScenario.adForward.interstitialCapPerSession)

        XCTAssertEqual(engine.maxInterstitialPerSession, before.maxInterstitialPerSession)
        XCTAssertEqual(engine.minMinutesBetweenInterstitial, before.minMinutesBetweenInterstitial)
        XCTAssertEqual(engine.maxAds24h, before.maxAds24h)
        XCTAssertEqual(engine.churnRiskProtectionThreshold, before.churnRiskProtectionThreshold, accuracy: 1e-9)
        XCTAssertEqual(engine.maxIapOffersPerSession, before.maxIapOffersPerSession)
        XCTAssertEqual(engine.minMinutesBetweenIapOffers, before.minMinutesBetweenIapOffers)
        XCTAssertEqual(engine.maxIapOffers24h, before.maxIapOffers24h)
        XCTAssertEqual(engine.maxPurchasesPerSessionBeforeBreak, before.maxPurchasesPerSessionBeforeBreak)
        XCTAssertEqual(engine.frustrationStreakThreshold, before.frustrationStreakThreshold)
        XCTAssertEqual(engine.maxAssistsPerSession, before.maxAssistsPerSession)
        XCTAssertEqual(engine.minLevelForInterstitial, before.minLevelForInterstitial)
        XCTAssertEqual(engine.minLevelForInterstitialAfterLoss, before.minLevelForInterstitialAfterLoss)
        XCTAssertEqual(engine.minMinutesAfterRewarded, before.minMinutesAfterRewarded)
        XCTAssertEqual(engine.skipFirstInterstitialsOnReopen, before.skipFirstInterstitialsOnReopen)

        XCTAssertEqual(ClickFraudGuard.maxClicksPerWindow, beforeClicks)
        XCTAssertEqual(ClickFraudGuard.windowMs, beforeWindow)
        XCTAssertEqual(ClickFraudGuard.lockoutMs, beforeLockout)
        XCTAssertEqual(HoldoutManager.holdoutFraction, beforeHoldout, accuracy: 1e-9)
        XCTAssertEqual(HoldoutManager.salt, beforeSalt)
        XCTAssertEqual(AdsBrain.shared.rolloutFraction, beforeRollout, accuracy: 1e-9)
        XCTAssertEqual(RewardModel.weightImmediateValue, beforeImmediate, accuracy: 1e-9)
        XCTAssertEqual(RewardModel.weightContinuation, beforeContinuation, accuracy: 1e-9)
        XCTAssertEqual(PersonalizationScenario.minConfidence, beforeConfidence, accuracy: 1e-9)
        XCTAssertEqual(Entitlements.shared.removeAdsProductIds, beforeRemoveAds)
        XCTAssertEqual(FxRates.knownCurrencyCount(), 0)
    }

    /// The knobs are reverted to what was in force BEFORE the first artifact, not to a
    /// hard-coded copy of each downstream type's defaults — so a default changing in
    /// `GuardrailEngine` can never drift out of sync with this reset.
    func test_resetRevertsToWhatWasInForceBeforeTheFirstArtifactNotToACompiledCopy() {
        let engine = GuardrailEngine()
        engine.maxAds24h = 17
        engine.frustrationStreakThreshold = 8
        let policy = BrainPolicy()
        _ = policy.apply(#"{"version": "p1", "guardrails": {"max_ads_24h": 3}, "iap": {"frustration_streak_threshold": 2}}"#, guardrails: engine)
        XCTAssertEqual(engine.maxAds24h, 3)
        policy.resetForTest()
        XCTAssertEqual(engine.maxAds24h, 17, "the host's own value, not GuardrailEngine's compiled default")
        XCTAssertEqual(engine.frustrationStreakThreshold, 8)
    }

    func test_resetForTestIsIdempotentAndSafeBeforeAnyApply() {
        let policy = BrainPolicy()
        policy.resetForTest()
        policy.resetForTest()
        XCTAssertEqual(policy.activeVersion(), "not loaded")
        XCTAssertTrue(policy.isEnabled())
    }

    // MARK: - §2.7/§4 the limitation that is stated rather than implied

    /// Whole-artifact atomicity is NOT attempted (matching Android): each knob is written
    /// individually behind its own lock, so a concurrent reader can see some updated and others
    /// not. What this asserts is the part that IS guaranteed — every individual write is
    /// race-free, so hammering `apply` from several threads never corrupts a value or traps.
    func test_concurrentAppliesLeaveEveryKnobAtOneOfTheAppliedValues() {
        let engine = GuardrailEngine()
        let policy = BrainPolicy()
        let group = DispatchGroup()
        for index in 0..<20 {
            DispatchQueue.global().async(group: group) {
                _ = policy.apply(
                    #"{"version": "p\#(index)", "guardrails": {"max_ads_24h": \#(10 + index)}}"#,
                    guardrails: engine
                )
            }
        }
        XCTAssertEqual(group.wait(timeout: .now() + 10.0), .success)
        XCTAssertTrue((10...29).contains(engine.maxAds24h), "\(engine.maxAds24h)")
        XCTAssertTrue(policy.activeVersion().hasPrefix("p"))
        policy.resetForTest()
    }
}
