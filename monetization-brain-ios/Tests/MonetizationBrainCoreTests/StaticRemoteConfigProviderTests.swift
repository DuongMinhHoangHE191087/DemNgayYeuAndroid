import XCTest
@testable import MonetizationBrainCore

final class StaticRemoteConfigProviderTests: XCTestCase {
    func test_returnsNilForMissingKey() {
        let provider = StaticRemoteConfigProvider()
        XCTAssertNil(provider.string(forKey: "missing"))
    }

    func test_returnsSeededValue() {
        let provider = StaticRemoteConfigProvider(values: ["flag": "on"])
        XCTAssertEqual(provider.string(forKey: "flag"), "on")
    }

    func test_updateReplacesValues() {
        let provider = StaticRemoteConfigProvider(values: ["flag": "on"])
        provider.update(["flag": "off", "other": "1"])
        XCTAssertEqual(provider.string(forKey: "flag"), "off")
        XCTAssertEqual(provider.string(forKey: "other"), "1")
    }

    func test_updateWithEmptyValuesKeepsCurrentValues() {
        let provider = StaticRemoteConfigProvider(values: ["flag": "on"])
        provider.update([:])
        XCTAssertEqual(provider.string(forKey: "flag"), "on")
    }

    func test_fetchAndActivateIsNoOp() async {
        let provider = StaticRemoteConfigProvider(values: ["flag": "on"])
        await provider.fetchAndActivate()
        XCTAssertEqual(provider.string(forKey: "flag"), "on")
    }
}
