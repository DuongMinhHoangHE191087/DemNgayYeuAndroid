import XCTest
@testable import MonetizationBrainCore

final class LagrangianBudgetTests: XCTestCase {
    func test_startsAtPointOne() {
        let budget = LagrangianBudget()
        XCTAssertEqual(budget.lambdaAdPressure, 0.1, accuracy: 1e-12)
    }

    func test_costOfMatchesAndroidTable() {
        let budget = LagrangianBudget()
        XCTAssertEqual(budget.costOf(.interstitial), 1.0, accuracy: 1e-12)
        XCTAssertEqual(budget.costOf(.rewardedAd), 0.3, accuracy: 1e-12)
        XCTAssertEqual(budget.costOf(.iapOffer), 0.2, accuracy: 1e-12)
        XCTAssertEqual(budget.costOf(.noAction), 0.0)
        XCTAssertEqual(budget.costOf(.freeAction), 0.0)
        XCTAssertEqual(budget.costOf(.gameplayConfig), 0.0)
    }

    func test_penaltyForIsLambdaTimesCost() {
        let budget = LagrangianBudget()
        XCTAssertEqual(budget.penaltyFor(.interstitial), budget.lambdaAdPressure * 1.0, accuracy: 1e-12)
        XCTAssertEqual(budget.penaltyFor(.noAction), 0.0)
    }

    func test_observeRaisesLambdaWhenSpendingFasterThanBudget() {
        let budget = LagrangianBudget()
        let before = budget.lambdaAdPressure
        budget.observe(chosenMode: .interstitial, budgetPerDecision: 0.1)
        XCTAssertGreaterThan(budget.lambdaAdPressure, before)
    }

    func test_observeLowersLambdaWhenSpendingSlowerThanBudget() {
        let budget = LagrangianBudget()
        budget.observe(chosenMode: .interstitial, budgetPerDecision: 5.0)
        let before = budget.lambdaAdPressure
        budget.observe(chosenMode: .noAction, budgetPerDecision: 5.0)
        XCTAssertLessThan(budget.lambdaAdPressure, before)
    }

    func test_observeClampsToMaxLambda() {
        let budget = LagrangianBudget()
        budget.maxLambda = 1.0
        for _ in 0..<200 {
            budget.observe(chosenMode: .interstitial, budgetPerDecision: 0.0)
        }
        XCTAssertLessThanOrEqual(budget.lambdaAdPressure, 1.0)
    }

    func test_observeClampsToMinimumFloor() {
        let budget = LagrangianBudget()
        for _ in 0..<200 {
            budget.observe(chosenMode: .noAction, budgetPerDecision: 5.0)
        }
        XCTAssertGreaterThanOrEqual(budget.lambdaAdPressure, 0.001)
    }

    func test_resetReturnsToStartingLambda() {
        let budget = LagrangianBudget()
        budget.observe(chosenMode: .interstitial, budgetPerDecision: 0.0)
        XCTAssertNotEqual(budget.lambdaAdPressure, 0.1)
        budget.reset()
        XCTAssertEqual(budget.lambdaAdPressure, 0.1, accuracy: 1e-12)
    }
}
