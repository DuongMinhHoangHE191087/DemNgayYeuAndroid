import XCTest
@testable import MonetizationBrainCore

final class FxRatesTests: XCTestCase {
    override func tearDown() {
        FxRates.reset()
        super.tearDown()
    }

    func test_toUsdReturnsNilWhenCurrencyUnknown() {
        XCTAssertNil(FxRates.toUsd(priceMicros: 99_000_000_000, currencyCode: "VND"))
    }

    func test_toUsdResolvesUsdWithoutAnyRateLoaded() throws {
        let usd = try XCTUnwrap(FxRates.toUsd(priceMicros: 4_990_000, currencyCode: "USD"))
        XCTAssertEqual(usd, 4.99, accuracy: 0.0001)
    }

    func test_toUsdReturnsNilForBlankCurrencyCode() {
        XCTAssertNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: ""))
    }

    func test_setRatesAppliesConversionForKnownCurrency() throws {
        FxRates.setRates(["VND": 0.00004])
        let usd = try XCTUnwrap(FxRates.toUsd(priceMicros: 99_000_000_000, currencyCode: "VND"))
        XCTAssertEqual(usd, 3.96, accuracy: 0.0001) // 99000 * 0.00004
    }

    func test_setRatesIsCaseInsensitiveOnCurrencyCode() {
        FxRates.setRates(["vnd": 0.00004])
        XCTAssertNotNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "VND"))
    }

    func test_setRatesReplacesNotMerges() {
        FxRates.setRates(["VND": 0.00004])
        FxRates.setRates(["EUR": 1.08])
        XCTAssertNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "VND"))
        XCTAssertNotNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "EUR"))
    }

    func test_setRatesFiltersOutNonPositiveAndNonFiniteEntries() {
        FxRates.setRates(["ZERO": 0.0, "NEG": -1.0, "NAN": .nan, "INF": .infinity, "OK": 1.5])
        XCTAssertNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "ZERO"))
        XCTAssertNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "NEG"))
        XCTAssertNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "NAN"))
        XCTAssertNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "INF"))
        XCTAssertNotNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "OK"))
    }

    func test_knownCurrencyCountReflectsLoadedRates() {
        XCTAssertEqual(FxRates.knownCurrencyCount(), 0)
        FxRates.setRates(["VND": 0.00004, "EUR": 1.08])
        XCTAssertEqual(FxRates.knownCurrencyCount(), 2)
    }

    func test_resetClearsLoadedRates() {
        FxRates.setRates(["VND": 0.00004])
        FxRates.reset()
        XCTAssertNil(FxRates.toUsd(priceMicros: 1_000_000, currencyCode: "VND"))
        XCTAssertEqual(FxRates.knownCurrencyCount(), 0)
    }
}
