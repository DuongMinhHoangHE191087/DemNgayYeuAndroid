import XCTest
@testable import MonetizationBrainCore

final class PlayerStateTests: XCTestCase {
    func test_initSetsRequiredPlayerIdAndDefaults() {
        let state = PlayerState(playerId: "p1")
        XCTAssertEqual(state.playerId, "p1")
        XCTAssertEqual(state.sessionsLifetime, 0)
        XCTAssertEqual(state.daysSinceLastPurchase, -1)
        XCTAssertEqual(state.minutesSinceLastIapOffer, -1)
        XCTAssertEqual(state.minutesSinceLastRewarded, -1)
        XCTAssertEqual(state.minutesSinceLastAd, -1)
        XCTAssertEqual(state.adToleranceScore, 0.5)
        XCTAssertEqual(state.monetizationReadiness, 0.0)
    }

    func test_fieldsAreMutable() {
        var state = PlayerState(playerId: "p1")
        state.currentLevel = 12
        state.purchaseCountLifetime = 3
        state.softCurrencyBalance = 5_000
        XCTAssertEqual(state.currentLevel, 12)
        XCTAssertEqual(state.purchaseCountLifetime, 3)
        XCTAssertEqual(state.softCurrencyBalance, 5_000)
    }
}
