import XCTest
@testable import MonetizationBrainCore

final class BeliefStateTests: XCTestCase {
    func test_startsAtUniformPrior() {
        let belief = BeliefState(playerId: "p1")
        for type in LatentPlayerType.allCases {
            XCTAssertEqual(belief.posterior[type], 0.25)
        }
        XCTAssertTrue(belief.isStillPrior())
    }

    func test_mostLikelyTypeBreaksTiesByDeclarationOrder() {
        let belief = BeliefState(playerId: "p1")
        // All four still tied at 0.25 -> first declared case wins, matching Kotlin's
        // LinkedHashMap insertion-order tie-break in maxByOrNull.
        XCTAssertEqual(belief.mostLikelyType(), .highSkillLowTolerance)
    }

    func test_updateShiftsPosteriorTowardHigherLikelihoodType() {
        let belief = BeliefState(playerId: "p1")
        belief.update(likelihood: [
            .highSkillLowTolerance: 1.0,
            .highSkillHighTolerance: 3.0,
            .lowSkillHighChurnRisk: 1.0,
            .casualSteady: 1.0
        ])
        XCTAssertEqual(belief.mostLikelyType(), .highSkillHighTolerance)
        XCTAssertFalse(belief.isStillPrior())
        let total = belief.posterior.values.reduce(0, +)
        XCTAssertEqual(total, 1.0, accuracy: 1e-9)
    }

    func test_updateAppliesDefaultLikelihoodOneToOmittedTypes() {
        let belief = BeliefState(playerId: "p1")
        // Only one type supplied; the other three default to likelihood 1.0 (unnorm = prior * 1).
        // unnorm: highSkillLowTolerance = 0.25*2.0 = 0.5, the other three = 0.25*1.0 = 0.25 each.
        // normalizer = 1.25 -> posterior: 0.4, 0.2, 0.2, 0.2.
        belief.update(likelihood: [.highSkillLowTolerance: 2.0])
        XCTAssertEqual(belief.posterior[.highSkillLowTolerance]!, 0.4, accuracy: 1e-9)
        XCTAssertEqual(belief.posterior[.highSkillHighTolerance]!, 0.2, accuracy: 1e-9)
        XCTAssertEqual(belief.posterior[.lowSkillHighChurnRisk]!, 0.2, accuracy: 1e-9)
        XCTAssertEqual(belief.posterior[.casualSteady]!, 0.2, accuracy: 1e-9)
    }

    func test_updateWithZeroNormalizerLeavesPosteriorUnchanged() {
        let belief = BeliefState(playerId: "p1")
        belief.update(likelihood: [
            .highSkillLowTolerance: 0.0,
            .highSkillHighTolerance: 0.0,
            .lowSkillHighChurnRisk: 0.0,
            .casualSteady: 0.0
        ])
        for type in LatentPlayerType.allCases {
            XCTAssertEqual(belief.posterior[type], 0.25)
        }
    }

    func test_confidenceIsMaxPosteriorValue() {
        let belief = BeliefState(playerId: "p1")
        belief.update(likelihood: [
            .highSkillLowTolerance: 1.0,
            .highSkillHighTolerance: 5.0,
            .lowSkillHighChurnRisk: 1.0,
            .casualSteady: 1.0
        ])
        XCTAssertEqual(belief.confidence(), belief.posterior[.highSkillHighTolerance]!)
    }

    func test_serializeDeserializeRoundTrip() {
        let belief = BeliefState(playerId: "p1")
        belief.update(likelihood: [
            .highSkillLowTolerance: 2.0,
            .highSkillHighTolerance: 1.0,
            .lowSkillHighChurnRisk: 1.0,
            .casualSteady: 1.0
        ])
        let serialized = belief.serialize()
        let restored = BeliefState.deserialize(playerId: "p1", raw: serialized)
        for type in LatentPlayerType.allCases {
            XCTAssertEqual(restored.posterior[type]!, belief.posterior[type]!, accuracy: 1e-9)
        }
    }

    func test_deserializeWithNilOrEmptyReturnsUniformPrior() {
        let a = BeliefState.deserialize(playerId: "p1", raw: nil)
        let b = BeliefState.deserialize(playerId: "p1", raw: "")
        for type in LatentPlayerType.allCases {
            XCTAssertEqual(a.posterior[type], 0.25)
            XCTAssertEqual(b.posterior[type], 0.25)
        }
    }

    func test_deserializeWithMalformedDataReturnsUniformPrior() {
        let restored = BeliefState.deserialize(playerId: "p1", raw: "garbage-not-matching-format")
        for type in LatentPlayerType.allCases {
            XCTAssertEqual(restored.posterior[type], 0.25)
        }
    }

    func test_deserializeWithIncompleteTypeSetReturnsUniformPrior() {
        // Only 2 of 4 types present -> reject and fall back, matching Kotlin's parsed.size check.
        let restored = BeliefState.deserialize(playerId: "p1", raw: "HIGH_SKILL_LOW_TOLERANCE:0.6|CASUAL_STEADY:0.4")
        for type in LatentPlayerType.allCases {
            XCTAssertEqual(restored.posterior[type], 0.25)
        }
    }

    /// Composability test: proves `LikelihoodEstimator.estimate(_:)`'s output actually works as
    /// the `likelihood:` argument to `BeliefState.update(likelihood:)`, not just that each type
    /// is individually correct in isolation — the exact class of gap Task 5's review caught in
    /// `BanditPolicy` (per-type-correct pieces whose composition was never exercised).
    func test_likelihoodEstimatorOutputComposesWithBeliefStateUpdate() {
        // A player who matches LOW_SKILL_HIGH_CHURN_RISK's profile (failRate 0.60, rewarded
        // accept 0.45, payer 0.15, churn 0.80) far more closely than any other type: heavy fail
        // rate, long absence, no purchases.
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 10
        state.failRateLast30m = 0.65
        state.daysSinceLastSession = 9
        state.purchaseCountLifetime = 0

        let likelihood = LikelihoodEstimator.estimate(state)
        XCTAssertFalse(likelihood.isEmpty)

        let belief = BeliefState(playerId: "p1")
        belief.update(likelihood: likelihood)

        XCTAssertEqual(belief.mostLikelyType(), .lowSkillHighChurnRisk)
        XCTAssertGreaterThan(belief.posterior[.lowSkillHighChurnRisk]!, 0.25)
        XCTAssertFalse(belief.isStillPrior())
        let total = belief.posterior.values.reduce(0, +)
        XCTAssertEqual(total, 1.0, accuracy: 1e-9)
    }
    // MARK: - Degenerate input

    /// ═══ THE CHAIN THIS GUARD BREAKS ═══
    ///
    /// `Double("inf")` is a successful parse, `inf >= 0` is true, and an infinite total passes
    /// `total > 0` — so a persisted "inf" used to normalise to `inf / inf` and hand back a posterior
    /// that is NaN in every slot. NaN then travels invisibly: `DecisionEngine.segmentWeights`
    /// filters on `weight > 0`, which is FALSE for NaN, so the weights come back empty and
    /// `BanditPolicy.selectArm`'s `precondition(!segmentWeights.isEmpty)` takes the host app down
    /// on the next decision.
    ///
    /// This function's own doc promises the opposite — malformed data degrades to the uniform
    /// prior, because crashing would break the whole play session.
    func test_anInfiniteValueInThePersistedBeliefFallsBackToTheUniformPrior() {
        let raw = LatentPlayerType.allCases.map { "\($0.rawValue):inf" }.joined(separator: "|")

        let belief = BeliefState.deserialize(playerId: "p1", raw: raw)

        for type in LatentPlayerType.allCases {
            XCTAssertEqual(belief.posterior[type] ?? -1, 0.25, accuracy: 1e-9)
        }
        XCTAssertTrue(belief.isStillPrior())
    }

    /// The same hole one level up: every individual value finite, their sum not.
    func test_aTotalThatOverflowsFallsBackToTheUniformPrior() {
        let raw = LatentPlayerType.allCases.map { "\($0.rawValue):1e308" }.joined(separator: "|")

        let belief = BeliefState.deserialize(playerId: "p1", raw: raw)

        XCTAssertTrue(belief.isStillPrior(), "posterior was \(belief.posterior)")
    }

    /// And the in-memory path: an infinite normalizer passes `> 0` and turns the whole posterior
    /// to NaN in one update. `LikelihoodEstimator` cannot produce one today — it returns values in
    /// (0, 1] — but a guard whose job is to reject a degenerate update has to reject this one.
    func test_anOverflowingLikelihoodLeavesThePosteriorUntouched() {
        let belief = BeliefState(playerId: "p1")
        let before = belief.posterior

        belief.update(likelihood: Dictionary(
            uniqueKeysWithValues: LatentPlayerType.allCases.map { ($0, Double.infinity) }
        ))

        XCTAssertEqual(belief.posterior, before)
        for type in LatentPlayerType.allCases {
            XCTAssertFalse((belief.posterior[type] ?? .nan).isNaN, "\(type) went NaN")
        }
    }

}
