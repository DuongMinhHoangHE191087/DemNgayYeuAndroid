import XCTest
@testable import MonetizationBrainCore

final class GuardrailEngineTests: XCTestCase {
    override func tearDown() {
        ClickFraudGuard.resetForTest()
        super.tearDown()
    }

    private func candidate(_ mode: ActionMode, id: String = "a1") -> ActionCandidate {
        ActionCandidate(mode: mode, actionId: id)
    }

    private func state(playerId: String = "p1") -> PlayerState {
        PlayerState(playerId: playerId)
    }

    // MARK: - Default (all-off) behavior

    func test_defaultThresholdsAllowInterstitialWithNoPressure() {
        let engine = GuardrailEngine()
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: state(),
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    // MARK: - HARD: click fraud

    func test_clickFraudLockoutBlocksInterstitial() {
        let engine = GuardrailEngine()
        for _ in 0..<7 { ClickFraudGuard.registerClick(playerId: "p1") }
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: state(),
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "CLICK_FRAUD_LOCKOUT")
        XCTAssertEqual(violation?.kind, .hard)
    }

    func test_clickFraudLockoutExemptsNoActionGameplayConfigAndFreeAction() {
        let engine = GuardrailEngine()
        for _ in 0..<7 { ClickFraudGuard.registerClick(playerId: "p1") }
        for mode in [ActionMode.noAction, .gameplayConfig, .freeAction] {
            let violation = engine.evaluate(
                candidate: candidate(mode),
                state: state(),
                adsShownThisSession: 0,
                minutesSinceLastInterstitial: 999
            )
            XCTAssertNil(violation, "\(mode) should be exempt from click-fraud lockout")
        }
    }

    // MARK: - HARD: ethics

    func test_rewardedRequiresRealFailStreak() {
        let engine = GuardrailEngine()
        var s = state()
        s.failStreakCurrent = 0
        let violation = engine.evaluate(
            candidate: candidate(.rewardedAd),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "ETH_MANUFACTURED_FAIL")
    }

    func test_rewardedAllowedAfterRealFailStreak() {
        let engine = GuardrailEngine()
        var s = state()
        s.failStreakCurrent = 1
        let violation = engine.evaluate(
            candidate: candidate(.rewardedAd),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    func test_iapOfferBlockedDuringFrustrationStreak() {
        let engine = GuardrailEngine()
        var s = state()
        s.failStreakCurrent = 5
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "ETH_FRUSTRATION_EXPLOIT")
    }

    func test_iapVelocityBreakerBlocksAfterMaxPurchasesInSession() {
        let engine = GuardrailEngine()
        var s = state()
        s.purchasesThisSession = 3
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "IAP_VELOCITY_BREAKER")
        XCTAssertEqual(violation?.kind, .hard)
    }

    // MARK: - RESOURCE: interstitial-specific

    func test_newPlayerGraceBlocksBelowMinLevel() {
        let engine = GuardrailEngine()
        engine.minLevelForInterstitial = 10
        var s = state()
        s.currentLevel = 5
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "NEW_PLAYER_GRACE")
        XCTAssertEqual(violation?.kind, .resource)
    }

    func test_newPlayerGraceUsesLossThresholdOnLossBranch() {
        let engine = GuardrailEngine()
        engine.minLevelForInterstitial = 5
        engine.minLevelForInterstitialAfterLoss = 20
        var s = state()
        s.currentLevel = 10 // clears the win threshold, not the loss threshold
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999,
            decisionPoint: .levelFail
        )
        XCTAssertEqual(violation?.code, "NEW_PLAYER_GRACE")
    }

    func test_reopenGraceSkipsFirstNOpportunities() {
        let engine = GuardrailEngine()
        engine.skipFirstInterstitialsOnReopen = 2
        var s = state()
        s.interstitialOpportunitiesThisSession = 1
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "REOPEN_GRACE")
    }

    func test_postRewardedCooldownBlocksShortlyAfterRewarded() {
        let engine = GuardrailEngine()
        engine.minMinutesAfterRewarded = 5
        var s = state()
        s.minutesSinceLastRewarded = 2
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "INTER_AFTER_REWARDED_COOLDOWN")
    }

    func test_postRewardedCooldownIgnoresNeverWatchedSentinel() {
        let engine = GuardrailEngine()
        engine.minMinutesAfterRewarded = 5
        var s = state()
        s.minutesSinceLastRewarded = -1
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    func test_sessionCapUsesEffectiveInterstitialCap() {
        let engine = GuardrailEngine()
        engine.maxInterstitialPerSession = 6
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: state(),
            adsShownThisSession: 2,
            minutesSinceLastInterstitial: 999,
            scenario: .protectAndConvert // cap 2
        )
        XCTAssertEqual(violation?.code, "AD_PRESSURE_SESSION_CAP")
    }

    func test_cooldownBetweenInterstitialsBlocksTooSoon() {
        let engine = GuardrailEngine()
        engine.minMinutesBetweenInterstitial = 3
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: state(),
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 1
        )
        XCTAssertEqual(violation?.code, "AD_PRESSURE_COOLDOWN")
    }

    func test_dailyAdCapBlocksAt24hLimit() {
        let engine = GuardrailEngine()
        var s = state()
        s.adImpressions24h = 40
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "AD_PRESSURE_24H_CAP")
    }

    // MARK: - RESOURCE: payer protection

    func test_payerProtectionBlocksAdLikeShortlyAfterPurchase() {
        let engine = GuardrailEngine()
        var s = state()
        s.purchaseCountLifetime = 1
        s.daysSinceLastPurchase = 1
        s.failStreakCurrent = 1 // bypass ETH_MANUFACTURED_FAIL for rewardedAd
        let violation = engine.evaluate(
            candidate: candidate(.rewardedAd),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "PAYER_PROTECTION_RECENT")
    }

    func test_payerProtectionDoesNotBlockNonAdLike() {
        let engine = GuardrailEngine()
        var s = state()
        s.purchaseCountLifetime = 1
        s.daysSinceLastPurchase = 1
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    // MARK: - RESOURCE: IAP offer caps

    func test_iapOffer24hCapBlocksAtLimit() {
        let engine = GuardrailEngine()
        engine.maxIapOffers24h = 5
        var s = state()
        s.iapOffers24h = 5
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "IAP_OFFER_24H_CAP")
    }

    func test_iapOfferSessionCapBlocksAtLimit() {
        let engine = GuardrailEngine()
        var s = state()
        s.iapOffersThisSession = 3
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "IAP_OFFER_SESSION_CAP")
    }

    func test_iapOfferCooldownBlocksTooSoon() {
        let engine = GuardrailEngine()
        var s = state()
        s.minutesSinceLastIapOffer = 2
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "IAP_OFFER_COOLDOWN")
    }

    func test_iapOfferCooldownIgnoresNeverOfferedSentinel() {
        let engine = GuardrailEngine()
        var s = state()
        s.minutesSinceLastIapOffer = -1
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    // MARK: - RESOURCE: assists

    func test_assistSessionCapBlocksFreeActionAtLimit() {
        let engine = GuardrailEngine()
        var s = state()
        s.assistsThisSession = 3
        let violation = engine.evaluate(
            candidate: candidate(.freeAction),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "ASSIST_SESSION_CAP")
    }

    func test_assistSessionCapBlocksGameplayConfigAtLimit() {
        let engine = GuardrailEngine()
        var s = state()
        s.assistsThisSession = 3
        let violation = engine.evaluate(
            candidate: candidate(.gameplayConfig),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "ASSIST_SESSION_CAP")
    }

    func test_assistCounterIsolatedFromAdAndOfferCounters() {
        let engine = GuardrailEngine()
        var s = state()
        s.assistsThisSession = 0
        s.adImpressions24h = 999
        s.iapOffersThisSession = 999
        let violation = engine.evaluate(
            candidate: candidate(.freeAction),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    // MARK: - RESOURCE: churn protection

    func test_churnProtectionBlocksInterstitialAboveThreshold() {
        let engine = GuardrailEngine()
        var s = state()
        s.churnRisk = 0.9
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "CHURN_PROTECTION")
    }

    // MARK: - effectiveInterstitialCap

    func test_effectiveInterstitialCapReturnsAbsoluteCapWhenScenarioIsNil() {
        let engine = GuardrailEngine()
        engine.maxInterstitialPerSession = 6
        XCTAssertEqual(engine.effectiveInterstitialCap(nil), 6)
    }

    func test_effectiveInterstitialCapNeverExceedsAbsoluteCap() {
        let engine = GuardrailEngine()
        engine.maxInterstitialPerSession = 4
        XCTAssertEqual(engine.effectiveInterstitialCap(.adForward), 4) // adForward's own cap is 6
    }

    func test_effectiveInterstitialCapUsesScenarioWhenTighter() {
        let engine = GuardrailEngine()
        engine.maxInterstitialPerSession = 6
        XCTAssertEqual(engine.effectiveInterstitialCap(.protectAndConvert), 2)
    }

    // MARK: - prevented counts

    func test_preventedCountsIncrementOnEachBlock() {
        let engine = GuardrailEngine()
        var s = state()
        s.adImpressions24h = 40
        _ = engine.evaluate(candidate: candidate(.interstitial), state: s, adsShownThisSession: 0, minutesSinceLastInterstitial: 999)
        _ = engine.evaluate(candidate: candidate(.interstitial), state: s, adsShownThisSession: 0, minutesSinceLastInterstitial: 999)
        XCTAssertEqual(engine.preventedCount("AD_PRESSURE_24H_CAP"), 2)
        XCTAssertEqual(engine.allPreventedCounts()["AD_PRESSURE_24H_CAP"], 2)
    }

    func test_preventedCountIsZeroForNeverTriggeredCode() {
        let engine = GuardrailEngine()
        XCTAssertEqual(engine.preventedCount("NEVER_TRIGGERED"), 0)
    }
}
