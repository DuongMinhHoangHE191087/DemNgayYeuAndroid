import XCTest
@testable import MonetizationBrainCore

final class ConsentStateTests: XCTestCase {
    override func setUp() {
        super.setUp()
        ConsentState.shared.resetForTest()
    }

    override func tearDown() {
        ConsentState.shared.resetForTest()
        super.tearDown()
    }

    func test_startsAtUnknown() {
        XCTAssertEqual(ConsentState.shared.tier(), .unknown)
    }

    func test_unknownAllowsProfilingAndPersonalization() {
        XCTAssertTrue(ConsentState.shared.allowsProfiling())
        XCTAssertTrue(ConsentState.shared.allowsPersonalization())
    }

    func test_deniedBlocksProfilingAndPersonalization() {
        ConsentState.shared.setTier(.denied)
        XCTAssertFalse(ConsentState.shared.allowsProfiling())
        XCTAssertFalse(ConsentState.shared.allowsPersonalization())
    }

    func test_partialBlocksProfilingButAllowsPersonalization() {
        ConsentState.shared.setTier(.partial)
        XCTAssertFalse(ConsentState.shared.allowsProfiling())
        XCTAssertTrue(ConsentState.shared.allowsPersonalization())
    }

    func test_grantedAllowsProfilingAndPersonalization() {
        ConsentState.shared.setTier(.granted)
        XCTAssertTrue(ConsentState.shared.allowsProfiling())
        XCTAssertTrue(ConsentState.shared.allowsPersonalization())
    }

    func test_guardrailsAndDecisionLoggingAlwaysAllowedRegardlessOfTier() {
        for tier: ConsentTier in [.unknown, .denied, .partial, .granted] {
            ConsentState.shared.setTier(tier)
            XCTAssertTrue(ConsentState.shared.allowsGuardrails())
            XCTAssertTrue(ConsentState.shared.allowsDecisionLogging())
        }
    }

    func test_settingSameTierDoesNotNotifyListeners() {
        var notifications: [ConsentTier] = []
        ConsentState.shared.onChange { notifications.append($0) }
        ConsentState.shared.setTier(.unknown) // already .unknown
        XCTAssertTrue(notifications.isEmpty)
    }

    func test_settingDifferentTierNotifiesAllListenersInOrder() {
        var firstSeen: [ConsentTier] = []
        var secondSeen: [ConsentTier] = []
        ConsentState.shared.onChange { firstSeen.append($0) }
        ConsentState.shared.onChange { secondSeen.append($0) }

        ConsentState.shared.setTier(.granted)

        XCTAssertEqual(firstSeen, [.granted])
        XCTAssertEqual(secondSeen, [.granted])
    }

    func test_childDirectedAndUnderAgeDefaultToNil() {
        XCTAssertNil(ConsentState.shared.childDirected)
        XCTAssertNil(ConsentState.shared.underAgeOfConsent)
    }

    func test_settersUpdateChildDirectedAndUnderAge() {
        ConsentState.shared.setChildDirected(true)
        ConsentState.shared.setUnderAgeOfConsent(false)
        XCTAssertEqual(ConsentState.shared.childDirected, true)
        XCTAssertEqual(ConsentState.shared.underAgeOfConsent, false)
    }
}
