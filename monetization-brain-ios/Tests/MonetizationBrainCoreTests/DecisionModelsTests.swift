import XCTest
@testable import MonetizationBrainCore

final class DecisionModelsTests: XCTestCase {
    func test_decisionPointRawValuesMatchAndroidEventContract() {
        XCTAssertEqual(DecisionPoint.sessionStart.rawValue, "SESSION_START")
        XCTAssertEqual(DecisionPoint.levelFail.rawValue, "LEVEL_FAIL")
        XCTAssertEqual(DecisionPoint.levelFailStreak.rawValue, "LEVEL_FAIL_STREAK")
        XCTAssertEqual(DecisionPoint.levelComplete.rawValue, "LEVEL_COMPLETE")
        XCTAssertEqual(DecisionPoint.currencyLow.rawValue, "CURRENCY_LOW")
        XCTAssertEqual(DecisionPoint.shopOpen.rawValue, "SHOP_OPEN")
        XCTAssertEqual(DecisionPoint.rewardedOpportunity.rawValue, "REWARDED_OPPORTUNITY")
        XCTAssertEqual(DecisionPoint.interstitialOpportunity.rawValue, "INTERSTITIAL_OPPORTUNITY")
        XCTAssertEqual(DecisionPoint.returnAfterInactivity.rawValue, "RETURN_AFTER_INACTIVITY")
    }

    func test_actionModeRawValuesMatchAndroidEventContract() {
        XCTAssertEqual(ActionMode.noAction.rawValue, "NO_ACTION")
        XCTAssertEqual(ActionMode.freeAction.rawValue, "FREE_ACTION")
        XCTAssertEqual(ActionMode.rewardedAd.rawValue, "REWARDED_AD")
        XCTAssertEqual(ActionMode.interstitial.rawValue, "INTERSTITIAL")
        XCTAssertEqual(ActionMode.iapOffer.rawValue, "IAP_OFFER")
        XCTAssertEqual(ActionMode.gameplayConfig.rawValue, "GAMEPLAY_CONFIG")
    }

    func test_crossModalDecisionEquality() {
        let a = CrossModalDecision(action: .noAction, isHoldout: false)
        let b = CrossModalDecision(action: .noAction, isHoldout: false)
        XCTAssertEqual(a, b)
    }

    func test_assistDecisionEquality() {
        let a = AssistDecision(action: .freeAction, isHoldout: true)
        let b = AssistDecision(action: .freeAction, isHoldout: true)
        XCTAssertEqual(a, b)
    }
}
