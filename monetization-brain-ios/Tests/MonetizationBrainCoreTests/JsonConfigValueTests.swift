import XCTest
@testable import MonetizationBrainCore

/// Direct coverage for the shared config primitives. Until now every one of these was exercised
/// only through a parser, which is fine for the paths those parsers actually take — and is exactly
/// how `clamped`'s `Int64.max` trap stayed alive: no caller passes it, deliberately, so nothing
/// ever reached the line that traps. The header of `JsonConfigValue` invites future callers in, so
/// the primitives get tested as primitives.
final class JsonConfigValueTests: XCTestCase {
    // MARK: - clamped: the bound itself must be `Double`-representable (M1)

    /// ⚠️ WITHOUT THE FIX THIS DOES NOT FAIL AN ASSERTION — IT TRAPS.
    ///
    /// `Double(Int64.max)` rounds UP to 2^63, which `Int64` cannot represent, so the old
    /// `Int64(min(value, Double(upperBound)))` crashed the process on any input at or above the
    /// bound. A fatal trap in a host app's config load is the outcome the whole clamp-before-convert
    /// rule exists to prevent; a crashed test process is the honest shape of that red, and this
    /// file's neighbours already use that framing
    /// (`BattlePassTests.test_aConfigSwapCannotInterleaveWithClaimsOrAddXpUnderForcedContention`).
    func test_clampedSurvivesAnInt64MaxUpperBound() {
        XCTAssertEqual(JsonConfigValue.clamped(1e300, lowerBound: 0, upperBound: Int64.max), Int64.max)
        XCTAssertEqual(JsonConfigValue.clamped(.infinity, lowerBound: 0, upperBound: Int64.max), Int64.max)
        // The bound itself, and the `Double` it rounds to, both land ON the bound rather than past it.
        XCTAssertEqual(JsonConfigValue.clamped(Double(Int64.max), lowerBound: 0, upperBound: Int64.max), Int64.max)
        XCTAssertEqual(JsonConfigValue.clamped(9.223372036854776e18, lowerBound: 0, upperBound: Int64.max), Int64.max)
    }

    /// Same trap through the `Int` overload, which on a 64-bit platform has the identical ceiling.
    func test_clampedSurvivesAnIntMaxUpperBound() {
        XCTAssertEqual(JsonConfigValue.clamped(1e300, lowerBound: 0, upperBound: Int.max), Int.max)
        XCTAssertEqual(JsonConfigValue.clamped(Double(Int.max), lowerBound: 0, upperBound: Int.max), Int.max)
        XCTAssertEqual(JsonConfigValue.clamped(-1e300, lowerBound: Int.min, upperBound: Int.max), Int.min)
    }

    /// The lower end has the mirrored shape, and `Double(Int64.min)` happens to be exact — so this
    /// asserts the outcome rather than assuming the asymmetry stays true.
    func test_clampedSurvivesAnInt64MinLowerBound() {
        XCTAssertEqual(JsonConfigValue.clamped(-1e300, lowerBound: Int64.min, upperBound: 0), Int64.min)
        XCTAssertEqual(JsonConfigValue.clamped(-.infinity, lowerBound: Int64.min, upperBound: 0), Int64.min)
        XCTAssertEqual(JsonConfigValue.clamped(Double(Int64.min), lowerBound: Int64.min, upperBound: 0), Int64.min)
    }

    /// `NaN` also trapped: `min`/`max` propagate it, and `Int64(NaN)` is fatal. It carries no
    /// magnitude, so it clamps to `lowerBound` — the fail-safe direction.
    func test_clampedTreatsNaNAsTheLowerBoundRatherThanTrapping() {
        XCTAssertEqual(JsonConfigValue.clamped(.nan, lowerBound: 7, upperBound: 99), 7)
        XCTAssertEqual(JsonConfigValue.clamped(.nan, lowerBound: Int64.min, upperBound: Int64.max), Int64.min)
    }

    /// The behavior every existing caller depends on, unchanged by the fix: in-range values pass
    /// through, out-of-range values land exactly on the caller's bound, truncation is toward zero.
    func test_clampedKeepsItsExistingInRangeBehavior() {
        XCTAssertEqual(JsonConfigValue.clamped(42, lowerBound: 0, upperBound: 100), 42)
        XCTAssertEqual(JsonConfigValue.clamped(3.9, lowerBound: 0, upperBound: 100), 3, "truncates toward zero")
        XCTAssertEqual(JsonConfigValue.clamped(-3.9, lowerBound: -100, upperBound: 100), -3, "toward zero, not down")
        XCTAssertEqual(JsonConfigValue.clamped(100.0, lowerBound: 0, upperBound: 100), 100, "the bound is inclusive")
        XCTAssertEqual(JsonConfigValue.clamped(0.0, lowerBound: 0, upperBound: 100), 0)
        XCTAssertEqual(JsonConfigValue.clamped(1e300, lowerBound: 0, upperBound: 100), 100)
        XCTAssertEqual(JsonConfigValue.clamped(-1e300, lowerBound: 0, upperBound: 100), 0)
    }

    /// The bound this package actually uses today (`BattlePass.maxXp`/`RecurringGrants
    /// .maxRewardAmount`), whose `Double` rounds UP by one — the case the old second integer clamp
    /// was written for, and which must still land exactly on the bound.
    func test_clampedLandsExactlyOnAHalfInt64MaxBound() {
        let half = Int64.max / 2
        XCTAssertEqual(JsonConfigValue.clamped(1e300, lowerBound: 0, upperBound: half), half)
        XCTAssertEqual(JsonConfigValue.clamped(Double(half), lowerBound: 0, upperBound: half), half)
    }

    // MARK: - identifier: length ceiling (L1)

    func test_identifierAcceptsAnIdAtTheLengthLimitAndRejectsOneOverIt() {
        var notes: [String] = []
        let atLimit = String(repeating: "a", count: JsonConfigValue.maxIdentifierLength)
        XCTAssertEqual(
            JsonConfigValue.identifier(atLimit, field: "product_id", label: "a grant",
                                       mustNotContain: ["|"], notes: &notes),
            atLimit
        )
        XCTAssertTrue(notes.isEmpty, "an id at the limit is legal and carries no note")

        let overLimit = String(repeating: "a", count: JsonConfigValue.maxIdentifierLength + 1)
        XCTAssertNil(
            JsonConfigValue.identifier(overLimit, field: "product_id", label: "a grant",
                                       mustNotContain: ["|"], notes: &notes)
        )
        XCTAssertEqual(notes.count, 1)
        XCTAssertTrue(notes[0].contains("over the \(JsonConfigValue.maxIdentifierLength)-character limit"),
                      "note was: \(notes[0])")
    }

    /// The diagnostic must NOT reproduce the offending id: the note ends up in an `apply` report
    /// and in host logs, which is the same availability problem in a different coat.
    func test_theOverLengthDiagnosticDoesNotEchoTheIdBack() {
        var notes: [String] = []
        let huge = String(repeating: "z", count: 5_000_000)
        XCTAssertNil(
            JsonConfigValue.identifier(huge, field: "season_id", label: "the battle pass season",
                                       mustNotContain: ["|"], notes: &notes)
        )
        XCTAssertEqual(notes.count, 1)
        XCTAssertLessThan(notes[0].count, 200, "the note must be a size report, not a copy of the value")
        XCTAssertTrue(notes[0].contains("5000000 characters"), "note was: \(notes[0])")
    }

    /// Trimming runs BEFORE the length check, so padding cannot push a legal id over the line.
    func test_surroundingWhitespaceIsNotCountedAgainstTheLimit() {
        var notes: [String] = []
        let padded = "  " + String(repeating: "a", count: JsonConfigValue.maxIdentifierLength) + "  \n"
        XCTAssertEqual(
            JsonConfigValue.identifier(padded, field: "product_id", label: "a grant",
                                       mustNotContain: [], notes: &notes)?.count,
            JsonConfigValue.maxIdentifierLength
        )
        XCTAssertTrue(notes.isEmpty, "notes were: \(notes)")
    }
}
