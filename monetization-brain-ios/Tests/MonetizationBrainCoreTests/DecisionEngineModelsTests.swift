import XCTest
@testable import MonetizationBrainCore

final class DecisionEngineModelsTests: XCTestCase {
    func test_actionCandidateStoresCoreFields() {
        let candidate = ActionCandidate(mode: .interstitial, actionId: "int_1", isBaseline: true)
        XCTAssertEqual(candidate.mode, .interstitial)
        XCTAssertEqual(candidate.actionId, "int_1")
        XCTAssertTrue(candidate.isBaseline)
    }

    func test_actionCandidateDefaultsToNotBaseline() {
        let candidate = ActionCandidate(mode: .noAction, actionId: "no_action")
        XCTAssertFalse(candidate.isBaseline)
    }

    func test_actionCandidateEquatableByPublicFields() {
        let a = ActionCandidate(mode: .rewardedAd, actionId: "r1")
        let b = ActionCandidate(mode: .rewardedAd, actionId: "r1")
        let c = ActionCandidate(mode: .rewardedAd, actionId: "r2")
        XCTAssertEqual(a, b)
        XCTAssertNotEqual(a, c)
    }

    func test_decisionRequestStoresPlayerIdAndDecisionPoint() {
        let request = DecisionRequest(playerId: "p1", decisionPoint: .levelFail)
        XCTAssertEqual(request.playerId, "p1")
        XCTAssertEqual(request.decisionPoint, .levelFail)
    }

    func test_decisionResponseDefaultsAreEmptyAndNeutral() {
        let response = DecisionResponse(
            decisionId: "d1",
            chosenAction: ActionCandidate(mode: .noAction, actionId: "no_action"),
            chosenActionProbability: 1.0,
            policyVersion: "v1"
        )
        XCTAssertEqual(response.guardrailsApplied, [])
        XCTAssertEqual(response.eligibleActionIds, [])
        XCTAssertNil(response.segment)
        XCTAssertEqual(response.segmentConfidence, 0.0)
        XCTAssertNil(response.scenario)
        XCTAssertFalse(response.isHoldout)
    }

    func test_decisionResponseStoresAllFields() {
        let candidate = ActionCandidate(mode: .interstitial, actionId: "int_1")
        let response = DecisionResponse(
            decisionId: "d1",
            chosenAction: candidate,
            chosenActionProbability: 0.42,
            policyVersion: "brain_v0_skeleton",
            guardrailsApplied: ["AD_PRESSURE_COOLDOWN"],
            eligibleActionIds: ["int_1", "no_action"],
            segment: .highSkillHighTolerance,
            segmentConfidence: 0.7,
            scenario: .adForward,
            isHoldout: true
        )
        XCTAssertEqual(response.decisionId, "d1")
        XCTAssertEqual(response.chosenAction, candidate)
        XCTAssertEqual(response.chosenActionProbability, 0.42)
        XCTAssertEqual(response.guardrailsApplied, ["AD_PRESSURE_COOLDOWN"])
        XCTAssertEqual(response.eligibleActionIds, ["int_1", "no_action"])
        XCTAssertEqual(response.segment, .highSkillHighTolerance)
        XCTAssertEqual(response.segmentConfidence, 0.7)
        XCTAssertEqual(response.scenario, .adForward)
        XCTAssertTrue(response.isHoldout)
    }
}
