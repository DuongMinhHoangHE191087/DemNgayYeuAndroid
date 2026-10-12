import XCTest
@testable import MonetizationBrainCore

final class BanditPolicyTests: XCTestCase {
    /// Deterministic RNG test double — reproducible within Swift. No cross-platform parity
    /// requirement here (unlike HoldoutManager's hash): this algorithm is inherently stochastic,
    /// so all this needs is "same seed -> same result within Swift," not "matches Kotlin's RNG."
    private struct SeededLCG: RandomNumberGenerator {
        var state: UInt64
        mutating func next() -> UInt64 {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return state
        }
    }

    func test_singleCandidateShortCircuitsToPropensityOne() {
        let bandit = BanditPolicy()
        var rng = SeededLCG(state: 1)
        let result = bandit.selectArm(
            segmentWeights: ["seg": 1.0],
            candidateArmIds: ["only_arm"],
            randomSource: &rng
        )
        XCTAssertEqual(result.armId, "only_arm")
        XCTAssertEqual(result.propensity, 1.0)
    }

    func test_selectArmIsDeterministicForAFixedSeed() {
        let bandit1 = BanditPolicy()
        var rng1 = SeededLCG(state: 42)
        let result1 = bandit1.selectArm(
            segmentWeights: ["seg": 1.0],
            candidateArmIds: ["a", "b"],
            randomSource: &rng1
        )

        let bandit2 = BanditPolicy()
        var rng2 = SeededLCG(state: 42)
        let result2 = bandit2.selectArm(
            segmentWeights: ["seg": 1.0],
            candidateArmIds: ["a", "b"],
            randomSource: &rng2
        )

        XCTAssertEqual(result1.armId, result2.armId)
        XCTAssertEqual(result1.propensity, result2.propensity, accuracy: 1e-12)
    }

    func test_propensityIsAlwaysAtLeastExplorationFloorOverArmCount() {
        let bandit = BanditPolicy()
        bandit.explorationFloor = 0.03
        var rng = SeededLCG(state: 7)
        for _ in 0..<20 {
            let result = bandit.selectArm(
                segmentWeights: ["seg": 1.0],
                candidateArmIds: ["a", "b", "c"],
                randomSource: &rng
            )
            XCTAssertGreaterThanOrEqual(result.propensity, 0.03 / 3.0 - 1e-9)
            XCTAssertLessThanOrEqual(result.propensity, 1.0)
        }
    }

    func test_updateThenSelectArmConvergesTowardHigherRewardArm() {
        let bandit = BanditPolicy()
        bandit.explorationFloor = 0.03
        for _ in 0..<200 {
            bandit.update(segmentKey: "seg", armId: "good", reward01: 1.0)
            bandit.update(segmentKey: "seg", armId: "bad", reward01: 0.0)
        }

        var rng = SeededLCG(state: 99)
        var goodWins = 0
        let trials = 200
        for _ in 0..<trials {
            let result = bandit.selectArm(
                segmentWeights: ["seg": 1.0],
                candidateArmIds: ["good", "bad"],
                randomSource: &rng
            )
            if result.armId == "good" { goodWins += 1 }
        }
        // With 200 training rounds concentrating posterior sharply, "good" should win the
        // overwhelming majority of trials (bounded below only by the exploration floor).
        XCTAssertGreaterThan(goodWins, trials * 8 / 10)
    }

    func test_updateIgnoresNonPositiveWeight() {
        let bandit = BanditPolicy()
        bandit.update(segmentKey: "seg", armId: "a", reward01: 1.0, weight: 0.0)
        XCTAssertEqual(bandit.observationCount(segmentKey: "seg", armId: "a"), 0.0)
    }

    func test_observationCountReflectsAccumulatedWeight() {
        let bandit = BanditPolicy()
        bandit.update(segmentKey: "seg", armId: "a", reward01: 1.0, weight: 1.0)
        bandit.update(segmentKey: "seg", armId: "a", reward01: 0.0, weight: 0.5)
        XCTAssertEqual(bandit.observationCount(segmentKey: "seg", armId: "a"), 1.5, accuracy: 1e-9)
    }

    func test_serializeRestoreRoundTrip() {
        let bandit = BanditPolicy()
        bandit.update(segmentKey: "seg1", armId: "a", reward01: 0.8, weight: 1.0)
        bandit.update(segmentKey: "seg2", armId: "b", reward01: 0.2, weight: 1.0)
        let serialized = bandit.serialize()

        let restored = BanditPolicy()
        restored.restore(serialized)
        XCTAssertEqual(
            restored.observationCount(segmentKey: "seg1", armId: "a"),
            bandit.observationCount(segmentKey: "seg1", armId: "a"),
            accuracy: 1e-9
        )
        XCTAssertEqual(
            restored.observationCount(segmentKey: "seg2", armId: "b"),
            bandit.observationCount(segmentKey: "seg2", armId: "b"),
            accuracy: 1e-9
        )
    }

    func test_restoreIgnoresMalformedEntriesWithoutCrashing() {
        let bandit = BanditPolicy()
        bandit.restore("not;valid=entries,at,all;seg|arm=bad,data")
        XCTAssertEqual(bandit.observationCount(segmentKey: "seg", armId: "arm"), 0.0)
    }

    func test_restoreWithNilOrEmptyIsNoOp() {
        let bandit = BanditPolicy()
        bandit.restore(nil)
        bandit.restore("")
        XCTAssertEqual(bandit.serialize(), "")
    }

    func test_losingArmIsPickedAtApproximatelyExplorationFloorRate() {
        // Distinguishes "draw from the mixed distribution" (correct) from "draw from the raw
        // Thompson-Sampling distribution, only mix the logged propensity" (the exact bug class
        // this file's design doc warns about) — no other test in this file can tell the two
        // apart, since every other assertion is satisfied by both variants.
        let bandit = BanditPolicy()
        bandit.explorationFloor = 0.5 // high floor makes the two distributions clearly separable
        for _ in 0..<500 {
            bandit.update(segmentKey: "seg", armId: "good", reward01: 1.0)
            bandit.update(segmentKey: "seg", armId: "bad", reward01: 0.0)
        }
        // Posterior is now extremely lopsided (good ~= Beta(501,1), bad ~= Beta(1,501)), so raw
        // Thompson Sampling alone would pick "bad" almost never. Under the mixed distribution at
        // eps=0.5 with 2 arms, "bad" should be picked close to eps/n = 0.25 of the time instead.
        var rng = SeededLCG(state: 777)
        var badWins = 0
        let trials = 2000
        for _ in 0..<trials {
            let result = bandit.selectArm(
                segmentWeights: ["seg": 1.0],
                candidateArmIds: ["good", "bad"],
                randomSource: &rng
            )
            if result.armId == "bad" {
                badWins += 1
                // Also guards the REVERSE swap (draw from mixed, but log raw pts as propensity):
                // whenever "bad" wins, its logged propensity must itself be ~eps/n = 0.25, not
                // the raw (near-zero) Thompson-Sampling win rate.
                XCTAssertEqual(result.propensity, 0.25, accuracy: 0.05)
            }
        }
        let badRate = Double(badWins) / Double(trials)
        // Expected ~0.25 under correct mixing; a raw-distribution bug would land near 0.0.
        // Wide-ish tolerance band since this is still a finite-sample Monte Carlo estimate.
        XCTAssertGreaterThan(badRate, 0.15)
        XCTAssertLessThan(badRate, 0.35)
    }

    func test_defaultSelectArmOverloadUsesSystemRandomAndReturnsValidArm() {
        let bandit = BanditPolicy()
        let result = bandit.selectArm(
            segmentWeights: ["seg": 1.0],
            candidateArmIds: ["a", "b"]
        )
        XCTAssertTrue(["a", "b"].contains(result.armId))
        XCTAssertGreaterThan(result.propensity, 0.0)
        XCTAssertLessThanOrEqual(result.propensity, 1.0)
    }
    /// ═══ WHAT THE SEEDED-SEQUENCE TEST ABOVE IS ALSO FOR ═══
    ///
    /// `selectArm` was made 2.3× faster by hoisting the (segment, arm) table read out of the
    /// Monte Carlo loop and by making the generator a generic parameter instead of an existential.
    /// Neither change is supposed to alter a single draw — and the thing that proves it is that a
    /// fixed seed still produces the same arm and the same propensity, which
    /// `test_selectArmIsDeterministicForAFixedSeed` already pins.
    ///
    /// This adds the part that test cannot see: that the pairs missing from the table are still
    /// MATERIALISED. The old loop wrote a default `BetaParams` back on every lookup; the new code
    /// does it once up front. If that write were dropped as "dead", `serialize()` would silently
    /// stop persisting arms that had been offered but never rewarded, and the bandit would forget
    /// them across launches.
    func test_selectArmMaterialisesEveryOfferedArmSoItSurvivesSerialization() {
        let bandit = BanditPolicy()
        var rng = SeededLCG(state: 5)

        _ = bandit.selectArm(
            segmentWeights: ["SHOP_OPEN_CASUAL_STEADY": 1.0],
            candidateArmIds: ["a", "b", "c"],
            randomSource: &rng
        )

        let serialized = bandit.serialize()
        for armId in ["a", "b", "c"] {
            XCTAssertTrue(
                serialized.contains("SHOP_OPEN_CASUAL_STEADY|\(armId)="),
                "arm '\(armId)' was offered but is not in the persisted posterior:\n\(serialized)"
            )
        }
    }

    /// ═══ A CORRUPTED RECORD THAT KILLS THE BANDIT WITHOUT KILLING THE APP ═══
    ///
    /// `alpha > 0` is true for infinity. An infinite pair used to be accepted, and from there
    /// `sampleBeta` returns NaN (`inf / (inf + inf)`; its guard only catches a non-positive sum),
    /// every `sample > bestSample` comparison is false, and the first arm in the list wins every
    /// round — the bandit stops learning and logs a propensity near 1.0 for it. That number is
    /// what off-policy evaluation trusts, so the damage outlives the bad record.
    func test_restoreRejectsInfiniteParametersThatWouldSilentlyKillTheBandit() {
        let bandit = BanditPolicy()

        bandit.restore("seg|a=inf,inf;seg|b=2.0,3.0")

        let serialized = bandit.serialize()
        XCTAssertFalse(serialized.contains("seg|a="), "infinite record was accepted: \(serialized)")
        XCTAssertTrue(serialized.contains("seg|b=2.0,3.0"), "the good record must survive: \(serialized)")
    }

    /// The consequence, stated as behaviour rather than as storage: sampling still spreads across
    /// arms instead of collapsing onto the first one, and the propensity is a real number.
    func test_aPoisonedRecordDoesNotCollapseSelectionOntoTheFirstArm() {
        let bandit = BanditPolicy()
        bandit.restore("seg|a=inf,nan;seg|b=1e999,1.0")
        var rng = SeededLCG(state: 11)

        var chosen = Set<String>()
        for _ in 0..<40 {
            let result = bandit.selectArm(
                segmentWeights: ["seg": 1.0],
                candidateArmIds: ["a", "b"],
                randomSource: &rng
            )
            XCTAssertFalse(result.propensity.isNaN, "propensity went NaN")
            XCTAssertGreaterThan(result.propensity, 0.0)
            chosen.insert(result.armId)
        }
        XCTAssertEqual(chosen, ["a", "b"], "selection collapsed onto one arm")
    }

}
