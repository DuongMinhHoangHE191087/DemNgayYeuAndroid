import XCTest
@testable import MonetizationBrainCore

final class MonetizationBrainCoreTests: XCTestCase {
    func test_versionIsSet() {
        XCTAssertFalse(MonetizationBrainSDK.version.isEmpty)
    }
}
