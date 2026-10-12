#if canImport(UIKit)
import XCTest
import UserMessagingPlatform
@testable import MonetizationBrainUMP
import MonetizationBrainCore

/// The UMP target's only decision: UMP's `ConsentStatus` -> this SDK's `ConsentTier`.
///
/// ═══ WHY THIS FILE EXISTS, AND WHY IT IS THE ONLY ONE IN THE TARGET ═══
///
/// Everything else in `ConsentManager` is a call into Google's SDK — presenting a form, reading a
/// singleton — and a unit test of that would only be a test of a mock. The mapping table is
/// different: it is the one place where a wrong line silently changes what the SDK is allowed to
/// do with a player's data, and it had NO coverage at all, because this target had no test target
/// and `translate` read `ConsentInformation.shared` internally.
///
/// These tests do not run under `swift test`: `UserMessagingPlatform` is iOS-only, so on macOS
/// this whole file compiles to nothing. `Scripts/verify.sh` runs them on an iOS Simulator, which
/// is the only place they mean anything.
final class ConsentManagerTranslateTests: XCTestCase {
    /// Outside the EEA there is no consent obligation at all. That is a definite "allowed", and
    /// collapsing it into `.unknown` would make every non-EEA player look unconfigured.
    func test_notRequiredIsADefiniteGrantRatherThanAnAbsentAnswer() {
        XCTAssertEqual(ConsentManager.translate(.notRequired, canRequestAds: true), .granted)
        // Not conditional on `canRequestAds`: there was no obligation to satisfy in the first
        // place, so there is nothing for that flag to withhold.
        XCTAssertEqual(ConsentManager.translate(.notRequired, canRequestAds: false), .granted)
    }

    /// The fail-closed branch, and the reason `canRequestAds` is a parameter at all. UMP reporting
    /// that the flow COMPLETED is not the same as the user having allowed anything; if UMP also
    /// says ads may not be requested, a `.granted` here would hand back a consent nobody gave.
    func test_aCompletedFlowIsOnlyAGrantIfUmpAlsoAllowsAds() {
        XCTAssertEqual(ConsentManager.translate(.obtained, canRequestAds: true), .granted)
        XCTAssertEqual(ConsentManager.translate(.obtained, canRequestAds: false), .denied)
    }

    /// Consent is required and has not been obtained — the user has not said yes, so the answer
    /// is no, regardless of what `canRequestAds` claims.
    func test_requiredButNotObtainedIsDeniedEvenIfUmpWouldAllowAds() {
        XCTAssertEqual(ConsentManager.translate(.required, canRequestAds: false), .denied)
        XCTAssertEqual(ConsentManager.translate(.required, canRequestAds: true), .denied)
    }

    /// "Nobody has asked yet" is its own tier and must not be flattened into `.denied`: the SDK
    /// never invents a refusal nobody gave. See `ConsentTier`'s own doc comment.
    func test_unknownStaysUnknownAndIsNotReadAsARefusal() {
        XCTAssertEqual(ConsentManager.translate(.unknown, canRequestAds: true), .unknown)
        XCTAssertEqual(ConsentManager.translate(.unknown, canRequestAds: false), .unknown)
    }

    /// A status added by a future UMP release must not land on `.granted` by accident. `.unknown`
    /// is the safe landing spot — it is the "we have not established anything" tier, and it is
    /// what `AdsBrain.selfCheckReport()` surfaces rather than silently acting on.
    func test_anUnrecognisedStatusLandsOnUnknownRatherThanOnAGrant() {
        // `ConsentStatus` is an ObjC enum, so a raw value outside the known set is representable
        // and is exactly what a newer UMP would deliver. Nothing else in this suite can reach the
        // `@unknown default` arm.
        let fromTheFuture = ConsentStatus(rawValue: 9999)
        XCTAssertNotNil(fromTheFuture, "an out-of-range raw value should still construct")
        if let fromTheFuture {
            XCTAssertEqual(ConsentManager.translate(fromTheFuture, canRequestAds: true), .unknown)
        }
    }

    /// The whole table in one place, so a reordered `switch` shows up as a table diff rather than
    /// as one failing assertion somewhere above.
    func test_theWholeTableIsWhatTheDocCommentSaysItIs() {
        let table: [(ConsentStatus, Bool, ConsentTier)] = [
            (.notRequired, true, .granted),
            (.notRequired, false, .granted),
            (.obtained, true, .granted),
            (.obtained, false, .denied),
            (.required, true, .denied),
            (.required, false, .denied),
            (.unknown, true, .unknown),
            (.unknown, false, .unknown),
        ]
        for (status, canRequestAds, expected) in table {
            XCTAssertEqual(
                ConsentManager.translate(status, canRequestAds: canRequestAds), expected,
                "status \(status.rawValue) with canRequestAds=\(canRequestAds)"
            )
        }
    }
}
#endif
