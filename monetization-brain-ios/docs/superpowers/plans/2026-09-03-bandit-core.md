# Phase 2: Bandit Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port Android's standalone decision-making primitives — `PlayerState`, `LatentPlayerType`/`BeliefState`, `RewardModel`, `LikelihoodEstimator`, `BanditPolicy` — field-for-field, formula-for-formula, with no wiring into `AdsBrain` yet (that's Phase 3).

**Architecture:** Five new files under `Sources/MonetizationBrainCore/Brain/`, ported directly from `appplugin`'s `src/main/java/com/app/plugin/brain/{PlayerState,BeliefState,RewardModel,LikelihoodEstimator,BanditPolicy}.kt`. One structural deviation: `BanditPolicy`'s sampling takes an injectable `RandomNumberGenerator` (defaulted to the system RNG via an overload) instead of a hardcoded global source, so tests can be deterministic instead of purely statistical.

**Tech Stack:** Same as Phase 1/1.5 — Swift Package Manager, Swift tools 5.10, iOS 15+/macOS 12+, XCTest.

**Source spec:** `docs/superpowers/specs/2026-09-03-bandit-core-design.md`.

**Local repo:** `/Users/dungdm/Documents/SDK/monetization-brain-ios`, branch `phase2/bandit-core` (branched from `main`, which already has Phase 1 + 1.5 merged).

---

## Task 1: `PlayerState`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/PlayerState.swift`
- Test: `Tests/MonetizationBrainCoreTests/PlayerStateTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class PlayerStateTests: XCTestCase {
    func test_initSetsRequiredPlayerIdAndDefaults() {
        let state = PlayerState(playerId: "p1")
        XCTAssertEqual(state.playerId, "p1")
        XCTAssertEqual(state.sessionsLifetime, 0)
        XCTAssertEqual(state.daysSinceLastPurchase, -1)
        XCTAssertEqual(state.minutesSinceLastIapOffer, -1)
        XCTAssertEqual(state.minutesSinceLastRewarded, -1)
        XCTAssertEqual(state.minutesSinceLastAd, -1)
        XCTAssertEqual(state.adToleranceScore, 0.5)
        XCTAssertEqual(state.monetizationReadiness, 0.0)
    }

    func test_fieldsAreMutable() {
        var state = PlayerState(playerId: "p1")
        state.currentLevel = 12
        state.purchaseCountLifetime = 3
        state.softCurrencyBalance = 5_000
        XCTAssertEqual(state.currentLevel, 12)
        XCTAssertEqual(state.purchaseCountLifetime, 3)
        XCTAssertEqual(state.softCurrencyBalance, 5_000)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter PlayerStateTests`
Expected: FAIL — `cannot find 'PlayerState' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Brain/PlayerState.swift`**

```swift
/// S_t (doc 04 / doc 24 §I): observable state + rolling latent-trait estimates — the input to
/// the decision engine (Phase 3). The game populates the observable fields each call; the
/// derived/latent fields (bottom of the struct) are filled by `BeliefState` and the feature
/// pipeline, not supplied by hand.
///
/// This is the minimal starting shape (Phase 0-1, doc 23 §D) — extended per the full feature
/// dictionary in doc 04 only when an actual decision point needs a new signal. Only `playerId`
/// is required at construction; every other field is a `var` set after construction, matching
/// how this type is used as a snapshot the caller assembles piece by piece.
public struct PlayerState: Sendable {
    public let playerId: String

    // MARK: Observable: lifecycle & session
    public var sessionsLifetime: Int = 0
    public var daysSinceLastSession: Int = 0
    public var sessionMinutesCurrent: Int = 0

    // MARK: Observable: progression / difficulty response
    public var currentLevel: Int = 0
    public var failStreakCurrent: Int = 0
    public var failRateLast30m: Double = 0.0

    // MARK: Observable: economy
    public var softCurrencyBalance: Int64 = 0
    public var hardCurrencyBalance: Int64 = 0
    public var burnRate24h: Double = 0.0

    // MARK: Observable: IAP behavior
    public var purchaseCountLifetime: Int = 0
    public var purchaseValueLifetimeUsd: Double = 0.0
    public var daysSinceLastPurchase: Int = -1

    /// Purchases in the CURRENT session. The strongest, easiest-to-measure at-risk marker in IAP
    /// per doc 29 §5.2 ("deposit frequency spike"), and an input to the guardrail layer's circuit
    /// breaker (Phase 3). Kept separate from `purchaseCountLifetime` — the two answer different
    /// questions: "has this player ever paid" vs. "are they spending in a rapid burst right now."
    public var purchasesThisSession: Int = 0
    /// Number of times an IAP offer has been shown this session — the frequency cap input.
    public var iapOffersThisSession: Int = 0
    /// -1 = no IAP offer shown this session yet.
    public var minutesSinceLastIapOffer: Int = -1
    /// Offers shown in the last 24h. A session cap alone isn't enough for someone who opens the
    /// app many times a day.
    public var iapOffers24h: Int = 0
    /// -1 = no rewarded ad seen yet. Used to defer an interstitial right after a voluntary
    /// rewarded view.
    public var minutesSinceLastRewarded: Int = -1
    /// Interstitial opportunities that have PASSED this session — including ones that were
    /// blocked. Used for the grace-period-after-reopen guardrail.
    public var interstitialOpportunitiesThisSession: Int = 0
    /// Gameplay assists (free gift / difficulty tuning) shown this session. Assists are FREE for
    /// the studio but the goodwill cost is real: stacking them creates learned helplessness and
    /// breaks the game economy — so this needs the same kind of cap as every other pressure
    /// action.
    public var assistsThisSession: Int = 0

    // MARK: Observable: IAA behavior
    public var adImpressions24h: Int = 0
    public var rewardedAcceptRateRecent: Double = 0.0
    /// Rewarded opportunities recently OFFERED. Must accompany `rewardedAcceptRateRecent` — a
    /// 0.0 rate for someone never offered means something completely different than someone
    /// offered 10 times who declined all 10. Without this field, `LikelihoodEstimator` would
    /// draw the wrong conclusion.
    public var rewardedOpportunitiesRecent: Int = 0
    public var minutesSinceLastAd: Int = -1

    // MARK: Derived / latent (doc 24 §I — filled by BeliefState or the feature pipeline)
    public var purchasePropensity: Double = 0.0
    public var churnRisk: Double = 0.0
    public var adToleranceScore: Double = 0.5
    public var adFatigueScore: Double = 0.0
    public var monetizationReadiness: Double = 0.0

    public init(playerId: String) {
        self.playerId = playerId
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter PlayerStateTests`
Expected: PASS — both tests green

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/PlayerState.swift Tests/MonetizationBrainCoreTests/PlayerStateTests.swift
git commit -m "feat: port PlayerState from Android"
```

---

## Task 2: `LatentPlayerType` + `BeliefState`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/LatentPlayerType.swift`
- Create: `Sources/MonetizationBrainCore/Brain/BeliefState.swift`
- Test: `Tests/MonetizationBrainCoreTests/BeliefStateTests.swift`

**A real cross-platform trap to avoid:** Swift's `Dictionary` has NO guaranteed iteration order (hash-randomized per process). Kotlin's `mutableMapOf(...)` is a `LinkedHashMap`, which preserves insertion order — so `maxByOrNull` on a tie deterministically returns the FIRST type as written in the literal (`HIGH_SKILL_LOW_TOLERANCE`). `mostLikelyType()` below MUST iterate `LatentPlayerType.allCases` (whose order is guaranteed to match declaration order) and track a running max with strict `>` comparison — NOT `Dictionary.max(by:)`, which would pick a non-deterministic winner among ties.

- [ ] **Step 1: Write the failing test**

```swift
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
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter BeliefStateTests`
Expected: FAIL — `cannot find 'BeliefState' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Brain/LatentPlayerType.swift`**

```swift
/// Minimal belief state for doc 24 §B (POMDP): a discrete posterior over latent player type.
/// A deliberately practical starting point — discrete, not a full particle filter — sufficient
/// for roadmap Phases 1-3 (doc 23). Upgrading to a continuous/particle filter should only happen
/// once there's enough data volume (doc 24 §G: model complexity must match data maturity).
public enum LatentPlayerType: String, Sendable, CaseIterable {
    /// Skilled, low ad tolerance -> favor gentle IAP.
    case highSkillLowTolerance = "HIGH_SKILL_LOW_TOLERANCE"
    /// Skilled, high ad tolerance -> safe to lean into IAA.
    case highSkillHighTolerance = "HIGH_SKILL_HIGH_TOLERANCE"
    /// Weak, high churn risk -> protect the experience, free rescue.
    case lowSkillHighChurnRisk = "LOW_SKILL_HIGH_CHURN_RISK"
    /// Stable, no strong signal yet -> neutral fallback.
    case casualSteady = "CASUAL_STEADY"
}
```

- [ ] **Step 4: Write `Sources/MonetizationBrainCore/Brain/BeliefState.swift`**

```swift
import Foundation

/// Discrete Bayesian posterior over `LatentPlayerType`: b_t+1(z) ∝ P(obs|z) * b_t(z).
///
/// A reference type (class), matching every future call site's expectation that
/// `update(likelihood:)` mutates the SAME instance other holders already have a reference to —
/// Kotlin's `data class` is used here as mutable state via a `MutableMap` field, not as an
/// immutable value.
public final class BeliefState: @unchecked Sendable {
    public let playerId: String
    public private(set) var posterior: [LatentPlayerType: Double]

    public init(playerId: String) {
        self.playerId = playerId
        self.posterior = Dictionary(uniqueKeysWithValues: LatentPlayerType.allCases.map { ($0, 0.25) })
    }

    private init(playerId: String, posterior: [LatentPlayerType: Double]) {
        self.playerId = playerId
        self.posterior = posterior
    }

    /// Updates the posterior in place. `likelihood` is P(observation just happened | each type)
    /// — estimating that likelihood is the feature pipeline's (`LikelihoodEstimator`'s)
    /// responsibility, not this type's.
    public func update(likelihood: [LatentPlayerType: Double]) {
        var normalizer = 0.0
        var updated: [LatentPlayerType: Double] = [:]
        for type in LatentPlayerType.allCases {
            let prior = posterior[type] ?? 0.0
            let lik = likelihood[type] ?? 1.0
            let unnorm = prior * lik
            updated[type] = unnorm
            normalizer += unnorm
        }
        guard normalizer > 0.0 else { return }
        for type in LatentPlayerType.allCases {
            posterior[type] = (updated[type] ?? 0.0) / normalizer
        }
    }

    /// The type with the highest posterior. Iterates `LatentPlayerType.allCases` in declared
    /// order with a strict `>` comparison — NOT `Dictionary.max(by:)`, whose tie-breaking is
    /// non-deterministic (Swift dictionaries have no guaranteed iteration order). This matches
    /// Kotlin's `maxByOrNull` on a `LinkedHashMap`, which deterministically favors the
    /// first-inserted key on a tie.
    public func mostLikelyType() -> LatentPlayerType {
        var best = LatentPlayerType.allCases[0]
        var bestValue = posterior[best] ?? 0.0
        for type in LatentPlayerType.allCases.dropFirst() {
            let value = posterior[type] ?? 0.0
            if value > bestValue {
                bestValue = value
                best = type
            }
        }
        return best
    }

    /// Confidence in `mostLikelyType()`; low means fall back to a more neutral/safe policy.
    public func confidence() -> Double {
        posterior.values.max() ?? 0.0
    }

    /// Has anything been learned yet, or is this still sitting at the uniform prior?
    public func isStillPrior() -> Bool {
        posterior.values.allSatisfy { abs($0 - 0.25) < 1e-6 }
    }

    /// Compact string for persistence: "TYPE:0.31|TYPE:0.19|..."
    public func serialize() -> String {
        LatentPlayerType.allCases
            .map { "\($0.rawValue):\(posterior[$0] ?? 0.0)" }
            .joined(separator: "|")
    }

    /// Restores belief from a saved string. Malformed/missing data returns a uniform prior
    /// instead of throwing — losing belief only degrades personalization temporarily; crashing
    /// would break the whole play session (fail-safe principle, doc 27).
    public static func deserialize(playerId: String, raw: String?) -> BeliefState {
        let fallback = BeliefState(playerId: playerId)
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return fallback
        }
        var parsed: [LatentPlayerType: Double] = [:]
        var total = 0.0
        for part in raw.split(separator: "|") {
            let kv = part.split(separator: ":")
            guard kv.count == 2,
                  let type = LatentPlayerType(rawValue: String(kv[0])),
                  let value = Double(kv[1]),
                  value >= 0.0 else { continue }
            parsed[type] = value
            total += value
        }
        guard parsed.count == LatentPlayerType.allCases.count, total > 0.0 else {
            return fallback
        }
        var normalized: [LatentPlayerType: Double] = [:]
        for type in LatentPlayerType.allCases {
            normalized[type] = (parsed[type] ?? 0.0) / total
        }
        return BeliefState(playerId: playerId, posterior: normalized)
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `swift test --filter BeliefStateTests`
Expected: PASS — all 9 tests green

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/LatentPlayerType.swift Sources/MonetizationBrainCore/Brain/BeliefState.swift Tests/MonetizationBrainCoreTests/BeliefStateTests.swift
git commit -m "feat: port LatentPlayerType/BeliefState from Android"
```

---

## Task 3: `RewardModel`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/RewardModel.swift`
- Test: `Tests/MonetizationBrainCoreTests/RewardModelTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class RewardModelTests: XCTestCase {
    override func tearDown() {
        RewardModel.reset()
        super.tearDown()
    }

    func test_adShownPlayerStayed_isHighestReward() {
        XCTAssertEqual(RewardModel.estimate(actionHadValue: true, playerContinued: true), 1.0, accuracy: 1e-9)
    }

    func test_notShownPlayerStayed_isSafeButLowerThanShown() {
        XCTAssertEqual(RewardModel.estimate(actionHadValue: false, playerContinued: true), 0.6, accuracy: 1e-9)
    }

    func test_adShownPlayerLeft_isWorseThanNotShownPlayerStayed() {
        let shownLeft = RewardModel.estimate(actionHadValue: true, playerContinued: false)
        let notShownStayed = RewardModel.estimate(actionHadValue: false, playerContinued: true)
        XCTAssertEqual(shownLeft, 0.4, accuracy: 1e-9)
        XCTAssertLessThan(shownLeft, notShownStayed)
    }

    func test_notShownPlayerLeft_isLowestReward() {
        XCTAssertEqual(RewardModel.estimate(actionHadValue: false, playerContinued: false), 0.0, accuracy: 1e-9)
    }

    func test_unknownContinuationIsNeutralNotGuessed() {
        let unknown = RewardModel.estimate(actionHadValue: false, playerContinued: nil)
        XCTAssertEqual(unknown, 0.3, accuracy: 1e-9) // weightContinuation / 2 = 0.3
    }

    func test_resetRestoresDefaultWeights() {
        RewardModel.weightImmediateValue = 0.9
        RewardModel.weightContinuation = 0.9
        RewardModel.reset()
        XCTAssertEqual(RewardModel.weightImmediateValue, 0.4)
        XCTAssertEqual(RewardModel.weightContinuation, 0.6)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter RewardModelTests`
Expected: FAIL — `cannot find 'RewardModel' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Brain/RewardModel.swift`**

```swift
/// Reward model — decides what "a good decision" means.
///
/// ═══ THE BUG THIS FIXES ═══
///
/// An earlier version assigned reward at the moment of the action itself: `INTERSTITIAL → 1.0`,
/// `NO_ACTION → 0.0`. That's tautological — the action gets rewarded because it happened, not
/// because it created value. The inevitable result: the bandit converges on "always show an ad,"
/// and the only thing holding that back is the guardrail layer — the entire learning component
/// becomes pointless, since it's only relearning a rule that was already hardcoded.
///
/// ═══ PRINCIPLE: DOWNSTREAM REWARD ═══
///
/// Reward must come from what happens AFTER an action, not from the action itself. This is the
/// pattern Pinterest productionized (doc 03 §8, doc 05 §9): retention is a sparse, delayed
/// signal, so an early-but-predictive session-behavior proxy is used instead.
///
/// The proxy used here is **whether the player kept playing after the action**:
///
/// ```
/// R = w_value · immediate_value  +  w_continue · continued_playing
/// ```
///
/// Priority order this produces (default weights):
///
/// | Situation                      | Reward | Meaning |
/// |---------------------------------|--------|---------|
/// | Ad shown, player stayed         | 1.00   | best — revenue with no one lost |
/// | Not shown, player stayed        | 0.60   | safe but revenue left on the table |
/// | Ad shown, player left           | 0.40   | **worse than doing nothing** |
/// | Not shown, player left anyway   | 0.00   | lost the player and got nothing |
///
/// The crucial line is the third: showing an ad and then losing the player must score LOWER
/// than staying silent. Without that property the bandit never learns restraint.
///
/// ═══ THIS IS STILL NOT THE DESTINATION ═══
///
/// The full formula (doc 11) is:
///
/// ```
/// R = r_immediate + γ·[LTV(s') − LTV(s)] − λ_churn·P(churn|action) − λ_fair·FairnessViolation
/// ```
///
/// which needs an LTV/churn model that doesn't exist yet. What's here is the **right-direction
/// intermediate step**: no longer tautological, and there's now a real tradeoff between revenue
/// and retention. When an LTV model exists, `estimate()` changes — the decision engine and
/// `AdsBrain` don't need to.
public enum RewardModel {
    /// Weight for immediate value (was the ad actually shown/completed).
    public static var weightImmediateValue: Double = 0.4

    /// Weight for the continuation signal — the retention proxy.
    public static var weightContinuation: Double = 0.6

    /// - Parameters:
    ///   - actionHadValue: did the action create immediate value (ad shown, rewarded watched
    ///     fully, IAP succeeded)?
    ///   - playerContinued: did the player keep playing afterward? `nil` = not known yet, treated
    ///     as neutral rather than guessed.
    public static func estimate(actionHadValue: Bool, playerContinued: Bool?) -> Double {
        let value = actionHadValue ? weightImmediateValue : 0.0
        let cont: Double
        switch playerContinued {
        case .some(true): cont = weightContinuation
        case .some(false): cont = 0.0
        case .none: cont = weightContinuation / 2.0
        }
        return min(max(value + cont, 0.0), 1.0)
    }

    /// Resets weights to their defaults.
    public static func reset() {
        weightImmediateValue = 0.4
        weightContinuation = 0.6
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter RewardModelTests`
Expected: PASS — all 6 tests green

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/RewardModel.swift Tests/MonetizationBrainCoreTests/RewardModelTests.swift
git commit -m "feat: port RewardModel from Android"
```

---

## Task 4: `LikelihoodEstimator`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/LikelihoodEstimator.swift`
- Test: `Tests/MonetizationBrainCoreTests/LikelihoodEstimatorTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import XCTest
@testable import MonetizationBrainCore

final class LikelihoodEstimatorTests: XCTestCase {
    func test_returnsEmptyMapWhenNoSignalHasSupport() {
        let state = PlayerState(playerId: "p1") // sessionsLifetime=0, rewardedOpportunitiesRecent=0
        XCTAssertTrue(LikelihoodEstimator.estimate(state).isEmpty)
    }

    func test_returnsAllFourTypesWhenSessionSupportPresent() {
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 5
        let result = LikelihoodEstimator.estimate(state)
        XCTAssertEqual(Set(result.keys), Set(LatentPlayerType.allCases))
    }

    func test_everyLikelihoodIsFlooredAndDamped() {
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 5
        state.failRateLast30m = 1.0 // maximally mismatched with every profile's expected failRate
        let result = LikelihoodEstimator.estimate(state)
        for value in result.values {
            XCTAssertGreaterThanOrEqual(value, 0.02)
            XCTAssertLessThanOrEqual(value, 1.0)
        }
    }

    func test_matchingProfileScoresHigherThanMismatchedProfile() {
        // CASUAL_STEADY profile: failRate 0.35. LOW_SKILL_HIGH_CHURN_RISK: failRate 0.60.
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 5
        state.failRateLast30m = 0.35
        let result = LikelihoodEstimator.estimate(state)
        XCTAssertGreaterThan(result[.casualSteady]!, result[.lowSkillHighChurnRisk]!)
    }

    func test_rewardedSignalOnlyUsedWithEnoughOpportunities() {
        var withoutSupport = PlayerState(playerId: "p1")
        withoutSupport.sessionsLifetime = 5
        withoutSupport.rewardedOpportunitiesRecent = 2 // below minRewardedSupport=3
        withoutSupport.rewardedAcceptRateRecent = 1.0 // extreme value, should be IGNORED

        var withSupport = withoutSupport
        withSupport.rewardedOpportunitiesRecent = 3

        let resultWithout = LikelihoodEstimator.estimate(withoutSupport)
        let resultWith = LikelihoodEstimator.estimate(withSupport)
        // Same failRate/payer/churn inputs, only rewarded signal differs in support -> results
        // must differ once the (extreme) rewarded signal is actually included.
        XCTAssertNotEqual(resultWithout[.highSkillLowTolerance], resultWith[.highSkillLowTolerance])
    }

    func test_activeSignalsReportsNoDataMessageWhenNothingHasSupport() {
        let state = PlayerState(playerId: "p1")
        XCTAssertEqual(LikelihoodEstimator.activeSignals(state), ["not enough data yet — belief holds at prior"])
    }

    func test_activeSignalsListsSessionSignalsWhenSupported() {
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 5
        state.failRateLast30m = 0.4
        let signals = LikelihoodEstimator.activeSignals(state)
        XCTAssertTrue(signals.contains("failRate=0.40"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter LikelihoodEstimatorTests`
Expected: FAIL — `cannot find 'LikelihoodEstimator' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Brain/LikelihoodEstimator.swift`**

```swift
import Foundation

/// Computes P(observation | latent type) — the link that actually makes `BeliefState` move.
///
/// Before this type existed, `BeliefState.update()` existed but nothing called it, so every
/// player's posterior sat at the uniform 0.25 prior forever — the system degenerated into one
/// global bandit with no personalization. This is the fix.
///
/// MODEL: naive Bayes over a few conditionally-independent signals. Each latent type has an
/// "expected profile"; the likelihood of an observation is its Gaussian match to that profile.
///
/// THREE SAFETY PRINCIPLES are baked in — this is the easiest part to get wrong:
///
///  1. **Support gating**: a signal is only used if there's ENOUGH data. `rewardedAcceptRateRecent
///     == 0.0` for someone never offered a rewarded ad does NOT mean "hates ads" — it means
///     "unknown." Conflating the two is a classic mistake.
///  2. **Damping**: likelihood is raised to a `damping < 1` power so a single observation can't
///     flip the posterior. Belief should drift gradually, not jump.
///  3. **Floor**: every likelihood is floored at `minLikelihood` so no segment is ever pushed to
///     probability 0 permanently — from 0, Bayes never recovers.
///
/// HONESTY WARNING: every profile constant below is a **domain-knowledge assumption, NOT YET
/// calibrated on real data**. Good enough to make the mechanism run and observable, but not a
/// conclusion. The correct calibration path: collect decision logs, label clusters with the
/// method in doc 33 (HMM/clustering with stability validation), then re-estimate these `mu`
/// values from real data.
public enum LikelihoodEstimator {
    /// Minimum rewarded opportunities before the acceptance rate is statistically meaningful.
    private static let minRewardedSupport = 3

    /// Minimum sessions before skill/progression signals are meaningful.
    private static let minSessionSupport = 2

    /// Update softening: 1.0 = trust a single observation completely; smaller drifts slower.
    ///
    /// 0.25 was chosen by working backward: for one EXTREME observation (95% fail rate, 10 days
    /// away), the posterior moves from prior 0.25 to ~0.59 — sensitive enough to react, not
    /// enough to conclude from a single look. At 0.35 that number is ~0.71, nearly an immediate
    /// flip, contradicting the point of damping.
    private static let damping = 0.25

    private static let minLikelihood = 0.02

    /// Expected profile for a latent type. These `mu` values need recalibration from data.
    private struct Profile {
        let failRate: Double
        let rewardedAcceptRate: Double
        let payerPropensity: Double
        let churnTendency: Double
    }

    /// The profile in effect: Android prefers a calibrated value from the policy artifact
    /// (`BrainPolicy.profileFor`) before falling back to the compiled default. `BrainPolicy` is
    /// not ported yet, so this always returns the default for now — the remote-override check
    /// will be added when `BrainPolicy` lands (Phase 3+); this is the one place that changes,
    /// nothing else in this file does.
    private static func profileOf(_ type: LatentPlayerType) -> Profile {
        defaultProfiles[type]!
    }

    private static let defaultProfiles: [LatentPlayerType: Profile] = [
        .highSkillLowTolerance: Profile(failRate: 0.15, rewardedAcceptRate: 0.20, payerPropensity: 0.70, churnTendency: 0.20),
        .highSkillHighTolerance: Profile(failRate: 0.15, rewardedAcceptRate: 0.75, payerPropensity: 0.20, churnTendency: 0.25),
        .lowSkillHighChurnRisk: Profile(failRate: 0.60, rewardedAcceptRate: 0.45, payerPropensity: 0.15, churnTendency: 0.80),
        .casualSteady: Profile(failRate: 0.35, rewardedAcceptRate: 0.50, payerPropensity: 0.35, churnTendency: 0.35)
    ]

    /// Gaussian width per signal — larger means the signal is less decisive.
    private static let sigmaFail = 0.25
    private static let sigmaRewarded = 0.25
    private static let sigmaPayer = 0.30
    private static let sigmaChurn = 0.30

    private static func gaussianLikelihood(observed: Double, expected: Double, sigma: Double) -> Double {
        let d = observed - expected
        return exp(-(d * d) / (2.0 * sigma * sigma))
    }

    /// Infers an observed "is a payer" probability from actual purchase behavior. Doesn't use
    /// `PlayerState.purchasePropensity` (that field is filled by a different model) — using it
    /// here would create a feedback loop (the model trusting its own output).
    private static func observedPayerSignal(_ state: PlayerState) -> Double {
        switch state.purchaseCountLifetime {
        case 3...: return 0.90
        case 2: return 0.75
        case 1: return 0.60
        default: return 0.10
        }
    }

    private static func observedChurnSignal(_ state: PlayerState) -> Double {
        switch state.daysSinceLastSession {
        case 7...: return 0.90
        case 3...: return 0.65
        case 1...: return 0.40
        default: return 0.20
        }
    }

    /// Returns a likelihood map to pass straight into `BeliefState.update(likelihood:)`. If NO
    /// signal has enough support, returns an empty map — the caller should skip that update
    /// rather than update with meaningless data.
    public static func estimate(_ state: PlayerState) -> [LatentPlayerType: Double] {
        let useRewarded = state.rewardedOpportunitiesRecent >= minRewardedSupport
        let useSkill = state.sessionsLifetime >= minSessionSupport
        let usePayer = state.sessionsLifetime >= minSessionSupport
        let useChurn = state.sessionsLifetime >= minSessionSupport

        guard useRewarded || useSkill || usePayer || useChurn else { return [:] }

        let payerSignal = observedPayerSignal(state)
        let churnSignal = observedChurnSignal(state)

        var result: [LatentPlayerType: Double] = [:]
        for type in LatentPlayerType.allCases {
            let p = profileOf(type)
            var likelihood = 1.0

            if useSkill {
                likelihood *= gaussianLikelihood(
                    observed: min(max(state.failRateLast30m, 0.0), 1.0), expected: p.failRate, sigma: sigmaFail
                )
            }
            if useRewarded {
                likelihood *= gaussianLikelihood(
                    observed: min(max(state.rewardedAcceptRateRecent, 0.0), 1.0), expected: p.rewardedAcceptRate, sigma: sigmaRewarded
                )
            }
            if usePayer {
                likelihood *= gaussianLikelihood(observed: payerSignal, expected: p.payerPropensity, sigma: sigmaPayer)
            }
            if useChurn {
                likelihood *= gaussianLikelihood(observed: churnSignal, expected: p.churnTendency, sigma: sigmaChurn)
            }

            result[type] = max(pow(likelihood, damping), minLikelihood)
        }
        return result
    }

    /// Lists which signals are actually being used — for a debug view, to see why belief is
    /// standing still.
    public static func activeSignals(_ state: PlayerState) -> [String] {
        var signals: [String] = []
        if state.sessionsLifetime >= minSessionSupport {
            signals.append(String(format: "failRate=%.2f", state.failRateLast30m))
            signals.append(String(format: "payer=%.2f", observedPayerSignal(state)))
            signals.append(String(format: "churn=%.2f", observedChurnSignal(state)))
        }
        if state.rewardedOpportunitiesRecent >= minRewardedSupport {
            signals.append(String(format: "rewardedAccept=%.2f", state.rewardedAcceptRateRecent))
        }
        if signals.isEmpty { signals.append("not enough data yet — belief holds at prior") }
        return signals
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter LikelihoodEstimatorTests`
Expected: PASS — all 7 tests green

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/LikelihoodEstimator.swift Tests/MonetizationBrainCoreTests/LikelihoodEstimatorTests.swift
git commit -m "feat: port LikelihoodEstimator from Android"
```

---

## Task 5: `BanditPolicy`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/BanditPolicy.swift`
- Test: `Tests/MonetizationBrainCoreTests/BanditPolicyTests.swift`

**Deliberate deviation from Android (see design spec §3):** `selectArm` and its private sampling
helpers take an injected `randomSource: inout RandomNumberGenerator`. A second, defaulted-source
overload (`selectArm` without `randomSource`) is the production entry point — Swift does not
allow default values for `inout` parameters, so this must be two overloads, not one method with
a default.

- [ ] **Step 1: Write the failing test**

```swift
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
        var rng: RandomNumberGenerator = SeededLCG(state: 1)
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
        var rng1: RandomNumberGenerator = SeededLCG(state: 42)
        let result1 = bandit1.selectArm(
            segmentWeights: ["seg": 1.0],
            candidateArmIds: ["a", "b"],
            randomSource: &rng1
        )

        let bandit2 = BanditPolicy()
        var rng2: RandomNumberGenerator = SeededLCG(state: 42)
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
        var rng: RandomNumberGenerator = SeededLCG(state: 7)
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

        var rng: RandomNumberGenerator = SeededLCG(state: 99)
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
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter BanditPolicyTests`
Expected: FAIL — `cannot find 'BanditPolicy' in scope`

- [ ] **Step 3: Write `Sources/MonetizationBrainCore/Brain/BanditPolicy.swift`**

```swift
import Foundation

/// Minimal contextual bandit: Thompson Sampling Beta-Bernoulli, keyed by (segmentKey, armId).
/// "Contextual" at the segment-key level — built from decision point + belief-state type — NOT
/// full contextual regression. A deliberate choice per doc 24 §G: start with a segment-keyed
/// MAB, only upgrade to a linear/tree contextual bandit once there's enough traffic per segment.
///
/// No external probability library — a Beta sample is derived from two Gamma draws
/// (Marsaglia-Tsang), Gamma from a Gaussian (Box-Muller), all through an injected
/// `RandomNumberGenerator` rather than a hardcoded global source (a deliberate departure from
/// the Android source, taken for deterministic testability — see the Phase 2 design spec §3).
public final class BanditPolicy: @unchecked Sendable {
    private struct BetaParams {
        var alpha: Double = 1.0
        var beta: Double = 1.0
    }

    private let lock = NSLock()
    private var arms: [String: BetaParams] = [:]

    /// Exploration floor (epsilon floor) — REQUIRED infrastructure, not a tuning knob.
    ///
    /// Reasoning: once Thompson Sampling has converged, it picks the same arm nearly 100% of the
    /// time and the logged propensity approaches 1.0. At that point **every off-policy
    /// evaluation becomes useless** — with no overlap, the importance weight of any other policy
    /// is either 0 or infinite (doc 31 §5, doc 28 §H).
    ///
    /// With probability `explorationFloor`, pick uniformly at random among the valid arms. The
    /// final propensity is therefore always ≥ `explorationFloor / n_arms` > 0, keeping OPE always
    /// feasible.
    ///
    /// 0.02–0.05 is the usual range. Setting 0.0 disables OPE — only do that knowing the
    /// consequence.
    public var explorationFloor: Double = 0.03

    public init() {}

    private func key(_ segmentKey: String, _ armId: String) -> String {
        "\(segmentKey)|\(armId)"
    }

    /// WEIGHTED posterior update by belief (doc 33 §9).
    ///
    /// `weight` is b_t(k) — the posterior probability the player belongs to latent type k. Hard-
    /// assigning to `mostLikelyType()` and only updating that segment is wrong: when belief
    /// shifts, history learned in the old segment is wasted, and the new segment restarts from
    /// prior. A soft (EM-style) update distributes evidence across every segment in proportion to
    /// how much it's trusted.
    ///
    /// Beta-Bernoulli naturally accepts fractional updates — alpha/beta are sufficient
    /// statistics, and adding a real number preserves the correct functional form.
    public func update(segmentKey: String, armId: String, reward01: Double, weight: Double = 1.0) {
        guard weight > 0.0 else { return }
        lock.lock()
        defer { lock.unlock() }
        var p = arms[key(segmentKey, armId)] ?? BetaParams()
        let r = min(max(reward01, 0.0), 1.0)
        let w = min(max(weight, 0.0), 1.0)
        p.alpha += w * r
        p.beta += w * (1.0 - r)
        arms[key(segmentKey, armId)] = p
    }

    /// Thompson Sampling over the MIXED belief — samples both sources of uncertainty at once:
    /// (1) which latent type the player is, (2) what each arm's true value is.
    ///
    /// Each Monte Carlo round: draw one segment weighted by belief, then draw a Beta for every
    /// arm FROM THAT SAME segment (one segment per round — a player really is only one type, we
    /// just don't know which). Win frequency across rounds gives the selection probability under
    /// the mixture policy — exactly the propensity that belongs in the decision log for OPE
    /// (doc 24 §K, doc 28 §H).
    ///
    /// - Parameter segmentWeights: segmentKey → belief weight; should sum to roughly 1.0.
    public func selectArm(
        segmentWeights: [String: Double],
        candidateArmIds: [String],
        mcSamples: Int = 200,
        penalties: [String: Double] = [:],
        randomSource: inout RandomNumberGenerator
    ) -> (armId: String, propensity: Double) {
        precondition(!candidateArmIds.isEmpty, "candidateArmIds must not be empty")
        precondition(!segmentWeights.isEmpty, "segmentWeights must not be empty")
        if candidateArmIds.count == 1 {
            return (candidateArmIds[0], 1.0)
        }

        let segments = Array(segmentWeights.keys)
        var cumulative = [Double](repeating: 0, count: segments.count)
        var running = 0.0
        for i in segments.indices {
            running += max(segmentWeights[segments[i]] ?? 0.0, 0.0)
            cumulative[i] = running
        }
        precondition(running > 0.0, "total segment weight must be positive")

        lock.lock()
        var winCounts: [String: Int] = Dictionary(uniqueKeysWithValues: candidateArmIds.map { ($0, 0) })
        for _ in 0..<mcSamples {
            let u = Double.random(in: 0..<1, using: &randomSource) * running
            var segIdx = cumulative.firstIndex { $0 >= u } ?? (segments.count - 1)
            if segIdx < 0 { segIdx = segments.count - 1 }
            let segmentKey = segments[segIdx]

            var bestArm = candidateArmIds[0]
            var bestSample = -Double.greatestFiniteMagnitude
            for armId in candidateArmIds {
                let p = arms[key(segmentKey, armId)] ?? BetaParams()
                arms[key(segmentKey, armId)] = p
                let sample = Self.sampleBeta(alpha: p.alpha, beta: p.beta, randomSource: &randomSource) - (penalties[armId] ?? 0.0)
                if sample > bestSample {
                    bestSample = sample
                    bestArm = armId
                }
            }
            winCounts[bestArm] = (winCounts[bestArm] ?? 0) + 1
        }
        lock.unlock()

        let eps = min(max(explorationFloor, 0.0), 1.0)
        let n = Double(candidateArmIds.count)

        // Mix with the uniform distribution: p_final(a) = (1-eps)*p_TS(a) + eps/n.
        // Draw from the MIXED distribution — if the arm were chosen by raw TS and only the
        // logged probability were mixed afterward, the log would misrepresent what was drawn.
        let mixed: [String: Double] = Dictionary(uniqueKeysWithValues: candidateArmIds.map { armId in
            let pts = Double(winCounts[armId] ?? 0) / Double(mcSamples)
            return (armId, (1.0 - eps) * pts + eps / n)
        })

        var draw = Double.random(in: 0..<1, using: &randomSource)
        var chosenArm = candidateArmIds.last!
        for armId in candidateArmIds {
            draw -= mixed[armId] ?? 0.0
            if draw <= 0.0 {
                chosenArm = armId
                break
            }
        }
        return (chosenArm, mixed[chosenArm] ?? (eps / n))
    }

    /// Same as above but using the system's default random source — the production entry point.
    /// A separate overload rather than a default value because Swift doesn't allow default
    /// values for `inout` parameters.
    public func selectArm(
        segmentWeights: [String: Double],
        candidateArmIds: [String],
        mcSamples: Int = 200,
        penalties: [String: Double] = [:]
    ) -> (armId: String, propensity: Double) {
        var rng: RandomNumberGenerator = SystemRandomNumberGenerator()
        return selectArm(
            segmentWeights: segmentWeights,
            candidateArmIds: candidateArmIds,
            mcSamples: mcSamples,
            penalties: penalties,
            randomSource: &rng
        )
    }

    /// Serializes the full posterior for persistence: "segmentKey|armId=alpha,beta;..."
    ///
    /// Without persistence the bandit forgets everything on every app restart while belief is
    /// saved — that phase mismatch makes results non-reproducible and hard to diagnose.
    public func serialize() -> String {
        lock.lock()
        defer { lock.unlock() }
        return arms.map { "\($0.key)=\($0.value.alpha),\($0.value.beta)" }.joined(separator: ";")
    }

    /// Restores the posterior. Corrupt data drops that record rather than throwing (fail-safe).
    public func restore(_ raw: String?) {
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        lock.lock()
        defer { lock.unlock() }
        for entry in raw.split(separator: ";") {
            guard let eqIndex = entry.lastIndex(of: "=") else { continue }
            let armKey = String(entry[entry.startIndex..<eqIndex])
            let numsPart = entry[entry.index(after: eqIndex)...]
            let nums = numsPart.split(separator: ",")
            guard nums.count == 2,
                  let a = Double(nums[0]), let b = Double(nums[1]),
                  a > 0.0, b > 0.0 else { continue }
            arms[armKey] = BetaParams(alpha: a, beta: b)
        }
    }

    /// Observations accumulated for one arm (alpha+beta-2, subtracting the Beta(1,1) prior).
    public func observationCount(segmentKey: String, armId: String) -> Double {
        lock.lock()
        defer { lock.unlock() }
        guard let p = arms[key(segmentKey, armId)] else { return 0.0 }
        return max(p.alpha + p.beta - 2.0, 0.0)
    }

    // MARK: - Beta sampling via two Gamma variables

    private static func sampleBeta(alpha: Double, beta: Double, randomSource: inout RandomNumberGenerator) -> Double {
        let x = sampleGamma(shape: alpha, randomSource: &randomSource)
        let y = sampleGamma(shape: beta, randomSource: &randomSource)
        return (x + y) <= 0.0 ? 0.5 : x / (x + y)
    }

    /// Marsaglia-Tsang method, valid for shape > 0.
    private static func sampleGamma(shape: Double, randomSource: inout RandomNumberGenerator) -> Double {
        if shape < 1.0 {
            let u = Double.random(in: 0..<1, using: &randomSource)
            return sampleGamma(shape: 1.0 + shape, randomSource: &randomSource) * pow(u, 1.0 / shape)
        }
        let d = shape - 1.0 / 3.0
        let c = 1.0 / (9.0 * d).squareRoot()
        while true {
            var x: Double
            var v: Double
            repeat {
                x = gaussian(randomSource: &randomSource)
                v = 1.0 + c * x
            } while v <= 0.0
            v = v * v * v
            let u = Double.random(in: 0..<1, using: &randomSource)
            if u < 1.0 - 0.0331 * x * x * x * x { return d * v }
            if log(u) < 0.5 * x * x + d * (1.0 - v + log(v)) { return d * v }
        }
    }

    private static func gaussian(randomSource: inout RandomNumberGenerator) -> Double {
        // Box-Muller
        let u1 = max(Double.random(in: 0..<1, using: &randomSource), 1e-12)
        let u2 = Double.random(in: 0..<1, using: &randomSource)
        return (-2.0 * log(u1)).squareRoot() * cos(2.0 * Double.pi * u2)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter BanditPolicyTests`
Expected: PASS — all 11 tests green

- [ ] **Step 5: Run the full suite to confirm no regressions elsewhere**

Run: `swift test`
Expected: PASS — every test across every file green (58 from before + this phase's new tests)

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/BanditPolicy.swift Tests/MonetizationBrainCoreTests/BanditPolicyTests.swift
git commit -m "feat: port BanditPolicy (Thompson Sampling) from Android, with injectable RNG"
```

---

## Task 6: Final integration pass

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run the entire test suite and a clean-warning check**

Run: `rm -rf .build && swift build 2>&1 | grep -i warning`
Expected: no output (zero warnings)

Run: `swift test`
Expected: every test passes, 0 failures.

- [ ] **Step 2: Update `README.md`**

Find the `**Current phase:**` paragraph near the top:
```markdown
**Current phase:** Phase 1 — Core Foundation. See
[`docs/superpowers/specs/2026-09-03-core-foundation-design.md`](docs/superpowers/specs/2026-09-03-core-foundation-design.md)
for scope and architecture. No decisioning intelligence yet — this phase ships the plumbing
(consent, event pipeline, remote config abstraction, holdout bucketing, and an `AdsBrain` facade
whose decision points return safe baselines) that every later phase builds on.
```
Replace with:
```markdown
**Current phase:** Phase 2 — Bandit Core. See
[`docs/superpowers/specs/2026-09-03-bandit-core-design.md`](docs/superpowers/specs/2026-09-03-bandit-core-design.md)
for scope and architecture. Phase 1 shipped the plumbing (consent, event pipeline, remote config
abstraction, holdout bucketing, and an `AdsBrain` facade whose decision points return safe
baselines); Phase 1.5 wired the facade to that plumbing. Phase 2 ports the standalone
decision-making primitives (`PlayerState`, `BeliefState`, `RewardModel`, `LikelihoodEstimator`,
`BanditPolicy`) — still not wired into `AdsBrain` (that's Phase 3).
```

Find the `## Status` section's opening sentence:
```markdown
Phase 1 ("Core Foundation") implemented — consent, event pipeline, remote-config abstraction,
holdout bucketing, and the `AdsBrain` facade with baseline-only decision points. See
```
Replace with:
```markdown
Phase 1 ("Core Foundation") and Phase 1.5 ("Wire Up the Facade") implemented and merged.
Phase 2 ("Bandit Core") adds the standalone Thompson Sampling bandit, belief state, reward
model, and likelihood estimator — ported from Android but not yet wired into `AdsBrain`. See
```

- [ ] **Step 3: Commit and push**

```bash
git add README.md
git commit -m "docs: mark Phase 2 bandit-core complete"
git push -u origin phase2/bandit-core
```

- [ ] **Step 4: Verify**

Run: `git log --oneline -8 && git status`
Expected: `git status` reports `nothing to commit, working tree clean`.
