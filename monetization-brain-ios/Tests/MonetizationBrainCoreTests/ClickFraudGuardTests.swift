import XCTest
@testable import MonetizationBrainCore

final class ClickFraudGuardTests: XCTestCase {
    override func setUp() {
        super.setUp()
        ClickFraudGuard.maxClicksPerWindow = 6
        ClickFraudGuard.windowMs = 300_000
        ClickFraudGuard.lockoutMs = 1_800_000
        ClickFraudGuard.resetForTest()
    }

    func test_notLockedAtExactlyThreshold() {
        for _ in 0..<6 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 0))
    }

    func test_locksAfterExceedingThresholdInWindow() {
        for _ in 0..<7 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        XCTAssertTrue(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 0))
    }

    func test_lockoutExpiresAfterLockoutMs() {
        for _ in 0..<7 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        XCTAssertTrue(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 1_799_999))
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 1_800_001))
    }

    func test_windowResetsAfterWindowMsElapses() {
        for _ in 0..<6 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        for _ in 0..<6 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 300_001)
        }
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 300_001))
    }

    func test_lockoutIsIndependentPerPlayer() {
        for _ in 0..<7 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        XCTAssertTrue(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 0))
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "p2", nowMs: 0))
    }

    func test_isLockedFalseForUnknownPlayer() {
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "never_seen", nowMs: 0))
    }
}
