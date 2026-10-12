import XCTest
@testable import MonetizationBrainCore

final class PersonalizationScenarioTests: XCTestCase {
    override func tearDown() {
        ConsentState.shared.resetForTest()
        PersonalizationScenario.minConfidence = 0.40
        super.tearDown()
    }

    func test_forTypeMapsEachSegmentToItsScenario() {
        XCTAssertEqual(PersonalizationScenario.forType(.highSkillLowTolerance), .protectAndConvert)
        XCTAssertEqual(PersonalizationScenario.forType(.highSkillHighTolerance), .adForward)
        XCTAssertEqual(PersonalizationScenario.forType(.lowSkillHighChurnRisk), .retentionFirst)
        XCTAssertEqual(PersonalizationScenario.forType(.casualSteady), .balancedHybrid)
    }

    func test_interstitialCapPerSessionMatchesAndroid() {
        XCTAssertEqual(PersonalizationScenario.protectAndConvert.interstitialCapPerSession, 2)
        XCTAssertEqual(PersonalizationScenario.adForward.interstitialCapPerSession, 6)
        XCTAssertEqual(PersonalizationScenario.retentionFirst.interstitialCapPerSession, 1)
        XCTAssertEqual(PersonalizationScenario.balancedHybrid.interstitialCapPerSession, 3)
    }

    func test_policyKnobsMatchAndroidPerCase() {
        XCTAssertEqual(PersonalizationScenario.protectAndConvert.rewardedAggressiveness, 0.2, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.protectAndConvert.iapOfferPriority, 0.9, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.protectAndConvert.freeValueGenerosity, 0.3, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.adForward.rewardedAggressiveness, 0.8, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.retentionFirst.freeValueGenerosity, 0.9, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.balancedHybrid.iapOfferPriority, 0.5, accuracy: 1e-12)
    }

    func test_resolveFallsBackToBalancedWhenPersonalizationNotConsented() {
        ConsentState.shared.setTier(.denied)
        let belief = BeliefState(playerId: "p1")
        belief.update(likelihood: [.highSkillHighTolerance: 10.0])
        XCTAssertGreaterThanOrEqual(belief.confidence(), PersonalizationScenario.minConfidence)
        XCTAssertEqual(PersonalizationScenario.resolve(belief), .balancedHybrid)
    }

    func test_resolveFallsBackToBalancedBelowMinConfidence() {
        let belief = BeliefState(playerId: "p1") // uniform prior: confidence 0.25
        XCTAssertLessThan(belief.confidence(), PersonalizationScenario.minConfidence)
        XCTAssertEqual(PersonalizationScenario.resolve(belief), .balancedHybrid)
    }

    func test_resolveAppliesSpecializedScenarioAboveMinConfidence() {
        let belief = BeliefState(playerId: "p1")
        belief.update(likelihood: [.highSkillHighTolerance: 10.0])
        XCTAssertGreaterThanOrEqual(belief.confidence(), PersonalizationScenario.minConfidence)
        XCTAssertEqual(PersonalizationScenario.resolve(belief), .adForward)
    }
}
