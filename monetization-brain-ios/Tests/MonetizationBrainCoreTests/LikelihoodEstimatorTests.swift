import XCTest
@testable import MonetizationBrainCore

final class LikelihoodEstimatorTests: XCTestCase {
    func test_returnsEmptyMapWhenNoSignalHasSupport() {
        let state = PlayerState(playerId: "p1") // sessionsLifetime=0, rewardedOpportunitiesRecent=0
        XCTAssertTrue(LikelihoodEstimator.estimate(state).isEmpty)
    }

    func test_returnsAllFourTypesWhenSessionSupportPresent() {
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 5
        let result = LikelihoodEstimator.estimate(state)
        XCTAssertEqual(Set(result.keys), Set(LatentPlayerType.allCases))
    }

    func test_everyLikelihoodIsFlooredAndDamped() {
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 5
        state.failRateLast30m = 1.0 // maximally mismatched with every profile's expected failRate
        let result = LikelihoodEstimator.estimate(state)
        for value in result.values {
            XCTAssertGreaterThanOrEqual(value, 0.02)
            XCTAssertLessThanOrEqual(value, 1.0)
        }
    }

    func test_matchingProfileScoresHigherThanMismatchedProfile() {
        // CASUAL_STEADY profile: failRate 0.35. LOW_SKILL_HIGH_CHURN_RISK: failRate 0.60.
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 5
        state.failRateLast30m = 0.35
        let result = LikelihoodEstimator.estimate(state)
        XCTAssertGreaterThan(result[.casualSteady]!, result[.lowSkillHighChurnRisk]!)
    }

    func test_rewardedSignalOnlyUsedWithEnoughOpportunities() {
        var withoutSupport = PlayerState(playerId: "p1")
        withoutSupport.sessionsLifetime = 5
        withoutSupport.rewardedOpportunitiesRecent = 2 // below minRewardedSupport=3
        withoutSupport.rewardedAcceptRateRecent = 1.0 // extreme value, should be IGNORED

        var withSupport = withoutSupport
        withSupport.rewardedOpportunitiesRecent = 3

        let resultWithout = LikelihoodEstimator.estimate(withoutSupport)
        let resultWith = LikelihoodEstimator.estimate(withSupport)
        // Same failRate/payer/churn inputs, only rewarded signal differs in support -> results
        // must differ once the (extreme) rewarded signal is actually included.
        XCTAssertNotEqual(resultWithout[.highSkillLowTolerance], resultWith[.highSkillLowTolerance])
    }

    func test_activeSignalsReportsNoDataMessageWhenNothingHasSupport() {
        let state = PlayerState(playerId: "p1")
        XCTAssertEqual(LikelihoodEstimator.activeSignals(state), ["not enough data yet — belief holds at prior"])
    }

    func test_activeSignalsListsSessionSignalsWhenSupported() {
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 5
        state.failRateLast30m = 0.4
        let signals = LikelihoodEstimator.activeSignals(state)
        XCTAssertTrue(signals.contains("failRate=0.40"))
    }
}
