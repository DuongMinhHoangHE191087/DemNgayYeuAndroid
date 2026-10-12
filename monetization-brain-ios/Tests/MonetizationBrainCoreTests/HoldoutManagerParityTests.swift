import XCTest
@testable import MonetizationBrainCore

/// Locks `HoldoutManager.bucketWith` to the exact FNV-1a vectors Android's
/// `HoldoutParityVectorsTest` and the backend's `test_reconcile.py` also assert. The server-side
/// reconciliation job reimplements this hash in Python to re-derive bucket membership from
/// (salt, user_id) — changing this algorithm without changing all three implementations makes
/// every already-collected exposure row look "corrupt" the moment the server reconciles it.
final class HoldoutManagerParityTests: XCTestCase {
    override func tearDown() {
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
        super.tearDown()
    }

    func test_crossLanguageHashVectors() {
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v1", "user_1"), 6924)
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v1", "user_2"), 9781)
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v1", "player-abc-123"), 5350)
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v2", "user_1"), 735)
        XCTAssertEqual(HoldoutManager.bucketWith("brain_holdout_v1|rollout", "user_1"), 7683)
        XCTAssertEqual(HoldoutManager.bucketWith("paywall_variant", "user_1"), 7074)
    }

    func test_bucketIsStableAcrossCalls() {
        HoldoutManager.salt = "brain_holdout_v1"
        let first = HoldoutManager.bucket("stable_user")
        let second = HoldoutManager.bucket("stable_user")
        XCTAssertEqual(first, second)
    }

    func test_isHoldoutDisabledWhenFractionIsZero() {
        HoldoutManager.holdoutFraction = 0.0
        XCTAssertFalse(HoldoutManager.isHoldout("user_1"))
    }

    func test_isHoldoutAlwaysTrueWhenFractionIsOne() {
        HoldoutManager.holdoutFraction = 1.0
        XCTAssertTrue(HoldoutManager.isHoldout("user_1"))
    }

    func test_isInRolloutIndependentNamespaceNotPhaseShiftOfHoldoutBucket() {
        // Known vectors (see test_crossLanguageHashVectors): bucketWith("brain_holdout_v1", "user_1") = 6924,
        // bucketWith("brain_holdout_v1|rollout", "user_1") = 7683. A phase-shift bug — deriving the rollout
        // bucket from the holdout bucket instead of hashing a separate namespace — would make isInRollout
        // agree with isHoldout at any threshold. At fraction 0.70 (threshold 7000) the two buckets fall on
        // OPPOSITE sides: holdout's 6924 < 7000 (in), rollout's 7683 is not < 7000 (out). A phase-shift
        // implementation would incorrectly put this player in both or neither.
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.70
        XCTAssertTrue(HoldoutManager.isHoldout("user_1"))
        XCTAssertFalse(HoldoutManager.isInRollout("user_1", fraction: 0.70))
    }

    func test_isHoldoutReturnsFalseForNaNFraction() {
        HoldoutManager.holdoutFraction = Double.nan
        XCTAssertFalse(HoldoutManager.isHoldout("user_1"))
    }

    func test_isInRolloutReturnsFalseForNaNFraction() {
        XCTAssertFalse(HoldoutManager.isInRollout("user_1", fraction: Double.nan))
    }
    /// ═══ WHAT CAN ACTUALLY BE TESTED ABOUT THE CUT-OFF ═══
    ///
    /// The fraction→bucket conversion now goes through `JsonConfigValue.clamped`, so it cannot
    /// trap on an extreme value. That defence is NOT test-covered and cannot be: `isHoldout`
    /// short-circuits on non-finite, `<= 0` and `>= 1` before the conversion, so no public call
    /// can reach it with an extreme input today. The point of the change is that the conversion
    /// stops depending on those three guards staying in that order.
    ///
    /// What IS testable is the property the conversion has to have: raising the fraction only
    /// ever ADDS players to the holdout. A conversion that is off by a rounding step, or that
    /// clamps to the wrong bound, breaks this — and this is the behaviour an experiment's
    /// comparability actually rests on, since a player must not leave the control group because
    /// someone widened the holdout.
    func test_raisingTheFractionOnlyEverAddsPlayersToTheHoldout() {
        let players = (0..<2000).map { "player_\($0)" }

        HoldoutManager.holdoutFraction = 0.3
        let atThirty = Set(players.filter { HoldoutManager.isHoldout($0) })
        HoldoutManager.holdoutFraction = 0.6
        let atSixty = Set(players.filter { HoldoutManager.isHoldout($0) })

        XCTAssertTrue(atThirty.isSubset(of: atSixty), "someone left the control group when the holdout widened")
        XCTAssertGreaterThan(atSixty.count, atThirty.count)
        // Sanity on the magnitude, loose enough not to be a hash-distribution test.
        XCTAssertEqual(Double(atThirty.count) / 2000.0, 0.3, accuracy: 0.05)
        XCTAssertEqual(Double(atSixty.count) / 2000.0, 0.6, accuracy: 0.05)
    }

}
