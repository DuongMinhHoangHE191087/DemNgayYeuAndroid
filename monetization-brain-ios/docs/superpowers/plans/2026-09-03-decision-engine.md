# Decision Engine (Phase 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port Android's orchestration layer (`GuardrailEngine` real rules, `ClickFraudGuard`,
`LagrangianBudget`, `PersonalizationScenario`, `BrainStorage`, `DecisionEngine`) so
`DecisionEngine.decide`/`feedback`/`observe` is a complete, independently testable
decision loop tying Phase 2's primitives together — not yet wired into `AdsBrain`'s public
methods (that's Phase 3.5).

**Architecture:** See `docs/superpowers/specs/2026-09-03-decision-engine-design.md` for the full
design, deviations from the Kotlin source, and rationale. This plan implements that spec
field-for-field.

**Tech Stack:** Swift Package Manager, `MonetizationBrainCore` target, XCTest.

**Global constraints (apply to every task below):**
- Every new type mirroring Android state gets exactly the lock discipline Phase 2 established:
  `NSLock` around any type's actual mutable state; plain `var` for read-only-after-construction
  config knobs. Never call `NSLock.lock()`/`unlock()` directly inside an `async` function body —
  route through a synchronous private helper.
- No force-unwraps (`!`) in new production code, even where an invariant makes one provably safe
  elsewhere in the same function — use `??` fallbacks instead (see the design spec §4's
  `decide()` walkthrough for the exact pattern).
- Guardrail violation `code` strings are byte-for-byte identical to the Kotlin source (they're
  logged cross-platform); `reason` strings and all doc comments are English, matching every other
  file already in this codebase.
- `brain_decision`/`brain_feedback` event field names come from `backend/app/normalize.py`
  (already verified in the design spec §3.6) — do not invent or rename a field.

**Execution order (NOT the order tasks are numbered/listed below):** Task 0
(`DecisionEngineModels`) references `PersonalizationScenario`, so it must be dispatched AFTER
Task 3. Dispatch in this order: **Task 1, Task 2, Task 3, Task 0, Task 4, Task 5, Task 6, Task
7.** Every task's code is written assuming this order — by the time Task 0 is dispatched,
`PersonalizationScenario` already exists and the build stays green after every task, with no
partial-build gaps.

---

### Task 0: Decision engine models — `ActionCandidate`, `DecisionRequest`, `DecisionResponse`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/DecisionEngineModels.swift`
- Test: `Tests/MonetizationBrainCoreTests/DecisionEngineModelsTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import XCTest
@testable import MonetizationBrainCore

final class DecisionEngineModelsTests: XCTestCase {
    func test_actionCandidateStoresCoreFields() {
        let candidate = ActionCandidate(mode: .interstitial, actionId: "int_1", isBaseline: true)
        XCTAssertEqual(candidate.mode, .interstitial)
        XCTAssertEqual(candidate.actionId, "int_1")
        XCTAssertTrue(candidate.isBaseline)
    }

    func test_actionCandidateDefaultsToNotBaseline() {
        let candidate = ActionCandidate(mode: .noAction, actionId: "no_action")
        XCTAssertFalse(candidate.isBaseline)
    }

    func test_actionCandidateEquatableByPublicFields() {
        let a = ActionCandidate(mode: .rewardedAd, actionId: "r1")
        let b = ActionCandidate(mode: .rewardedAd, actionId: "r1")
        let c = ActionCandidate(mode: .rewardedAd, actionId: "r2")
        XCTAssertEqual(a, b)
        XCTAssertNotEqual(a, c)
    }

    func test_decisionRequestStoresPlayerIdAndDecisionPoint() {
        let request = DecisionRequest(playerId: "p1", decisionPoint: .levelFail)
        XCTAssertEqual(request.playerId, "p1")
        XCTAssertEqual(request.decisionPoint, .levelFail)
    }

    func test_decisionResponseDefaultsAreEmptyAndNeutral() {
        let response = DecisionResponse(
            decisionId: "d1",
            chosenAction: ActionCandidate(mode: .noAction, actionId: "no_action"),
            chosenActionProbability: 1.0,
            policyVersion: "v1"
        )
        XCTAssertEqual(response.guardrailsApplied, [])
        XCTAssertEqual(response.eligibleActionIds, [])
        XCTAssertNil(response.segment)
        XCTAssertEqual(response.segmentConfidence, 0.0)
        XCTAssertNil(response.scenario)
        XCTAssertFalse(response.isHoldout)
    }

    func test_decisionResponseStoresAllFields() {
        let candidate = ActionCandidate(mode: .interstitial, actionId: "int_1")
        let response = DecisionResponse(
            decisionId: "d1",
            chosenAction: candidate,
            chosenActionProbability: 0.42,
            policyVersion: "brain_v0_skeleton",
            guardrailsApplied: ["AD_PRESSURE_COOLDOWN"],
            eligibleActionIds: ["int_1", "no_action"],
            segment: .highSkillHighTolerance,
            segmentConfidence: 0.7,
            scenario: .adForward,
            isHoldout: true
        )
        XCTAssertEqual(response.decisionId, "d1")
        XCTAssertEqual(response.chosenAction, candidate)
        XCTAssertEqual(response.chosenActionProbability, 0.42)
        XCTAssertEqual(response.guardrailsApplied, ["AD_PRESSURE_COOLDOWN"])
        XCTAssertEqual(response.eligibleActionIds, ["int_1", "no_action"])
        XCTAssertEqual(response.segment, .highSkillHighTolerance)
        XCTAssertEqual(response.segmentConfidence, 0.7)
        XCTAssertEqual(response.scenario, .adForward)
        XCTAssertTrue(response.isHoldout)
    }
}
```

- [ ] **Step 2: Run to verify it fails to compile** (types don't exist yet)

Run: `swift test --filter DecisionEngineModelsTests`
Expected: build failure — `ActionCandidate`/`DecisionRequest`/`DecisionResponse` not found.
(Dispatch this task AFTER Task 3 per the plan's "Execution order" note above —
`DecisionResponse` references `PersonalizationScenario`, which must already exist.)

- [ ] **Step 3: Write the implementation**

```swift
/// The candidate action space for one `DecisionEngine.decide(...)` call, and the request/response
/// shapes that carry it. Mirrors Android's `DecisionModels.kt` (`ActionCandidate`/
/// `DecisionRequest`/`DecisionResponse`) — `DecisionPoint`/`ActionMode` are already split into
/// their own files from Phase 1.

/// One candidate action `DecisionEngine.decide(...)` may choose. The caller (host app / business
/// rules) supplies the candidate set; `DecisionEngine` never synthesizes SKUs, offers, or
/// creatives on its own.
public struct ActionCandidate: Sendable, Equatable {
    public let mode: ActionMode
    public let actionId: String

    /// `BanditPolicy`'s internal score before guardrails run — NOT the final value.
    /// Module-internal on purpose: nothing outside `MonetizationBrainCore` should read this
    /// (mirrors the Kotlin comment "don't read this field from outside the brain package").
    var rawScore: Double = 0.0

    /// Marks this as the deterministic pre-brain behavior ("just show the ad, static rule"). The
    /// holdout (control) group always receives this candidate — that's what makes a
    /// treatment-vs-control comparison meaningful: control is exactly what the app did before
    /// this SDK existed. If no candidate is marked, holdout falls back to `.noAction`.
    public let isBaseline: Bool

    public init(mode: ActionMode, actionId: String, rawScore: Double = 0.0, isBaseline: Bool = false) {
        self.mode = mode
        self.actionId = actionId
        self.rawScore = rawScore
        self.isBaseline = isBaseline
    }
}

/// One `decide(...)` call's inputs. No `context: [String: Any]` field — Android's is never read
/// by `DecisionEngine.decide()`, and an unconstrained `Any` map can't satisfy Swift's `Sendable`
/// checking. Add a concrete, `Sendable` field here if a real use appears in a later phase.
public struct DecisionRequest: Sendable, Equatable {
    public let playerId: String
    public let decisionPoint: DecisionPoint

    public init(playerId: String, decisionPoint: DecisionPoint) {
        self.playerId = playerId
        self.decisionPoint = decisionPoint
    }
}

/// The result of one `decide(...)` call — everything needed both to act on the decision and to
/// reconstruct it later for off-policy evaluation.
public struct DecisionResponse: Sendable, Equatable {
    public let decisionId: String
    public let chosenAction: ActionCandidate

    /// Probability of choosing `chosenAction` under the CURRENT policy. Required for OPE.
    /// Conditioned on `eligibleActionIds` (the post-guardrail set), NOT the original candidate
    /// list — any OPE estimator reading the log must condition on the same set, or importance
    /// weights are wrong.
    public let chosenActionProbability: Double
    public let policyVersion: String

    /// Guardrail codes that fired (one per candidate removed).
    public let guardrailsApplied: [String]

    /// The candidate set AFTER guardrails — what the bandit actually chose among. Without this,
    /// the decision can't be reconstructed for OPE.
    public let eligibleActionIds: [String]

    public let segment: LatentPlayerType?
    public let segmentConfidence: Double
    public let scenario: PersonalizationScenario?

    /// Whether this player is in the control group (not personalized). Required in the log so
    /// treatment can be separated from control during analysis.
    public let isHoldout: Bool

    public init(
        decisionId: String,
        chosenAction: ActionCandidate,
        chosenActionProbability: Double,
        policyVersion: String,
        guardrailsApplied: [String] = [],
        eligibleActionIds: [String] = [],
        segment: LatentPlayerType? = nil,
        segmentConfidence: Double = 0.0,
        scenario: PersonalizationScenario? = nil,
        isHoldout: Bool = false
    ) {
        self.decisionId = decisionId
        self.chosenAction = chosenAction
        self.chosenActionProbability = chosenActionProbability
        self.policyVersion = policyVersion
        self.guardrailsApplied = guardrailsApplied
        self.eligibleActionIds = eligibleActionIds
        self.segment = segment
        self.segmentConfidence = segmentConfidence
        self.scenario = scenario
        self.isHoldout = isHoldout
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `swift build && swift test --filter DecisionEngineModelsTests`
Expected: PASS (6/6).

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/DecisionEngineModels.swift Tests/MonetizationBrainCoreTests/DecisionEngineModelsTests.swift
git commit -m "feat: add ActionCandidate/DecisionRequest/DecisionResponse models"
```

---

### Task 1: `ClickFraudGuard`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/ClickFraudGuard.swift`
- Test: `Tests/MonetizationBrainCoreTests/ClickFraudGuardTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import XCTest
@testable import MonetizationBrainCore

final class ClickFraudGuardTests: XCTestCase {
    override func setUp() {
        super.setUp()
        ClickFraudGuard.maxClicksPerWindow = 6
        ClickFraudGuard.windowMs = 300_000
        ClickFraudGuard.lockoutMs = 1_800_000
        ClickFraudGuard.resetForTest()
    }

    func test_notLockedAtExactlyThreshold() {
        for _ in 0..<6 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 0))
    }

    func test_locksAfterExceedingThresholdInWindow() {
        for _ in 0..<7 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        XCTAssertTrue(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 0))
    }

    func test_lockoutExpiresAfterLockoutMs() {
        for _ in 0..<7 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        XCTAssertTrue(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 1_799_999))
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 1_800_001))
    }

    func test_windowResetsAfterWindowMsElapses() {
        for _ in 0..<6 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        for _ in 0..<6 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 300_001)
        }
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 300_001))
    }

    func test_lockoutIsIndependentPerPlayer() {
        for _ in 0..<7 {
            ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0)
        }
        XCTAssertTrue(ClickFraudGuard.isLocked(playerId: "p1", nowMs: 0))
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "p2", nowMs: 0))
    }

    func test_isLockedFalseForUnknownPlayer() {
        XCTAssertFalse(ClickFraudGuard.isLocked(playerId: "never_seen", nowMs: 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `swift test --filter ClickFraudGuardTests`
Expected: FAIL — `ClickFraudGuard` not found.

- [ ] **Step 3: Write the implementation**

```swift
import Foundation

/// Automatic click-fraud protection, distilled from a production teardown pattern (a competing
/// app's `config_prevent_ad_click`): more than `maxClicksPerWindow` ad clicks inside `windowMs`
/// locks ALL ads for `lockoutMs`. Protects the mediation/AdMob account from invalid traffic by
/// blocking client-side, before the request is even sent.
///
/// Defaults (6 clicks / 300s / 1800s lockout) were observed running in real production — a
/// reasonable starting point, NOT a mandatory value; production should load these from a policy
/// artifact when one exists.
///
/// Unlike `HoldoutManager`'s static state (deliberately left unsynchronized — a known, documented
/// Phase 1 gap), this is real concurrent mutable state: ad clicks can arrive from multiple call
/// sites. Lock-protected from day one.
public enum ClickFraudGuard {
    public static var maxClicksPerWindow: Int = 6
    public static var windowMs: Int64 = 300_000
    public static var lockoutMs: Int64 = 1_800_000

    private struct ClickWindow {
        var windowStartMs: Int64
        var count: Int
    }

    private static let lock = NSLock()
    private static var clickState: [String: ClickWindow] = [:]
    private static var lockedUntilMs: [String: Int64] = [:]

    private static func defaultNowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }

    /// Call every time a user clicks an ad (not every impression).
    public static func registerClick(playerId: String, nowMs: Int64? = nil) {
        let now = nowMs ?? defaultNowMs()
        lock.lock()
        defer { lock.unlock() }
        var window = clickState[playerId] ?? ClickWindow(windowStartMs: now, count: 0)
        if now - window.windowStartMs > windowMs {
            window.windowStartMs = now
            window.count = 0
        }
        window.count += 1
        if window.count > maxClicksPerWindow {
            lockedUntilMs[playerId] = now + lockoutMs
        }
        clickState[playerId] = window
    }

    public static func isLocked(playerId: String, nowMs: Int64? = nil) -> Bool {
        let now = nowMs ?? defaultNowMs()
        lock.lock()
        defer { lock.unlock() }
        guard let until = lockedUntilMs[playerId] else { return false }
        return now < until
    }

    /// Test-only: clears all click/lockout state.
    static func resetForTest() {
        lock.lock()
        clickState.removeAll()
        lockedUntilMs.removeAll()
        lock.unlock()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `swift test --filter ClickFraudGuardTests`
Expected: PASS (6/6)

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/ClickFraudGuard.swift Tests/MonetizationBrainCoreTests/ClickFraudGuardTests.swift
git commit -m "feat: port ClickFraudGuard from Android"
```

---

### Task 2: `LagrangianBudget`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/LagrangianBudget.swift`
- Test: `Tests/MonetizationBrainCoreTests/LagrangianBudgetTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import XCTest
@testable import MonetizationBrainCore

final class LagrangianBudgetTests: XCTestCase {
    func test_startsAtPointOne() {
        let budget = LagrangianBudget()
        XCTAssertEqual(budget.lambdaAdPressure, 0.1, accuracy: 1e-12)
    }

    func test_costOfMatchesAndroidTable() {
        let budget = LagrangianBudget()
        XCTAssertEqual(budget.costOf(.interstitial), 1.0, accuracy: 1e-12)
        XCTAssertEqual(budget.costOf(.rewardedAd), 0.3, accuracy: 1e-12)
        XCTAssertEqual(budget.costOf(.iapOffer), 0.2, accuracy: 1e-12)
        XCTAssertEqual(budget.costOf(.noAction), 0.0)
        XCTAssertEqual(budget.costOf(.freeAction), 0.0)
        XCTAssertEqual(budget.costOf(.gameplayConfig), 0.0)
    }

    func test_penaltyForIsLambdaTimesCost() {
        let budget = LagrangianBudget()
        XCTAssertEqual(budget.penaltyFor(.interstitial), budget.lambdaAdPressure * 1.0, accuracy: 1e-12)
        XCTAssertEqual(budget.penaltyFor(.noAction), 0.0)
    }

    func test_observeRaisesLambdaWhenSpendingFasterThanBudget() {
        let budget = LagrangianBudget()
        let before = budget.lambdaAdPressure
        budget.observe(chosenMode: .interstitial, budgetPerDecision: 0.1)
        XCTAssertGreaterThan(budget.lambdaAdPressure, before)
    }

    func test_observeLowersLambdaWhenSpendingSlowerThanBudget() {
        let budget = LagrangianBudget()
        budget.observe(chosenMode: .interstitial, budgetPerDecision: 5.0)
        let before = budget.lambdaAdPressure
        budget.observe(chosenMode: .noAction, budgetPerDecision: 5.0)
        XCTAssertLessThan(budget.lambdaAdPressure, before)
    }

    func test_observeClampsToMaxLambda() {
        let budget = LagrangianBudget()
        budget.maxLambda = 1.0
        for _ in 0..<200 {
            budget.observe(chosenMode: .interstitial, budgetPerDecision: 0.0)
        }
        XCTAssertLessThanOrEqual(budget.lambdaAdPressure, 1.0)
    }

    func test_observeClampsToMinimumFloor() {
        let budget = LagrangianBudget()
        for _ in 0..<200 {
            budget.observe(chosenMode: .noAction, budgetPerDecision: 5.0)
        }
        XCTAssertGreaterThanOrEqual(budget.lambdaAdPressure, 0.001)
    }

    func test_resetReturnsToStartingLambda() {
        let budget = LagrangianBudget()
        budget.observe(chosenMode: .interstitial, budgetPerDecision: 0.0)
        XCTAssertNotEqual(budget.lambdaAdPressure, 0.1)
        budget.reset()
        XCTAssertEqual(budget.lambdaAdPressure, 0.1, accuracy: 1e-12)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `swift test --filter LagrangianBudgetTests`
Expected: FAIL — `LagrangianBudget` not found.

- [ ] **Step 3: Write the implementation**

```swift
import Foundation

/// Shadow price for a resource constraint — a practical Bandits-with-Knapsacks implementation.
///
/// Hard filtering ("6 ads and you're cut off") makes behavior jerky: the bandit keeps picking the
/// best arm until it hits the cap, then goes silent. With a 6-interstitial/session budget, a
/// player could get all 6 in the first 5 minutes and then nothing — a bad experience AND
/// suboptimal revenue.
///
/// BwK theory says the optimal solution, when the constraint is a *budget* rather than "always
/// pick the best arm," is a MIXED distribution — e.g. 70% light rewarded, 20% interstitial, 10%
/// silence. Lagrangian relaxation reaches that without solving an LP at every decision:
///
/// ```
/// adjusted reward:  r~(a) = r(a) - Σ_j lambda_j * c_j(a)
/// dual update:       lambda_j <- lambda_j * exp(eta * (observed_cost - B_j/T))
/// ```
///
/// Spend faster than budget -> lambda rises -> every resource-costly action gets penalized more,
/// smoothly. Spend slower -> lambda falls -> the system relaxes on its own.
///
/// This does NOT replace `GuardrailEngine`'s hard caps. The penalty is soft — in principle, an
/// arm with a big enough reward can still win. For user-safety constraints, "enough revenue buys
/// you the right to spam" is unacceptable, so the two layers complement each other: Lagrangian
/// shapes the rhythm INSIDE the budget (smooth, optimal); the hard cap blocks absolutely AT THE
/// EDGE (safe, non-negotiable).
///
/// Cost values below are DESIGN CHOICES, not calibrated from real data — same caveat as every
/// other threshold ported from Android in this phase.
public final class LagrangianBudget: @unchecked Sendable {
    /// Dual variable's learning rate. Too high: lambda oscillates. Too low: reacts too slowly.
    public var learningRate: Double = 0.15

    /// Ceiling so one abnormal streak can't push lambda to infinity and lock out every action.
    public var maxLambda: Double = 5.0

    private let lock = NSLock()
    /// Starts positive on purpose: the update is multiplicative, so starting at 0 would keep
    /// lambda at 0 forever — a classic bug when implementing multiplicative weights.
    private var _lambdaAdPressure: Double = 0.1

    public var lambdaAdPressure: Double {
        lock.lock()
        defer { lock.unlock() }
        return _lambdaAdPressure
    }

    public init() {}

    /// "Discomfort cost" each action type consumes. Rewarded is much lower than interstitial
    /// because the player CHOSE to watch it — a difference in kind, not degree (rewarded is only
    /// valid when it's a genuine choice).
    public func costOf(_ mode: ActionMode) -> Double {
        switch mode {
        case .interstitial: return 1.0
        case .rewardedAd: return 0.3
        case .iapOffer: return 0.2
        case .noAction, .freeAction, .gameplayConfig: return 0.0
        }
    }

    /// Amount subtracted from an action's reward.
    public func penaltyFor(_ mode: ActionMode) -> Double {
        lock.lock()
        let lambda = _lambdaAdPressure
        lock.unlock()
        return lambda * costOf(mode)
    }

    /// Updates the shadow price after an action has been chosen.
    ///
    /// - Parameters:
    ///   - chosenMode: the action just taken.
    ///   - budgetPerDecision: allowed budget per decision point = B/T. E.g. a 6-interstitial/
    ///     session cap with ~30 estimated decision points per session gives B/T = 0.2.
    public func observe(chosenMode: ActionMode, budgetPerDecision: Double) {
        let consumed = costOf(chosenMode)
        let target = max(budgetPerDecision, 0.0)
        lock.lock()
        defer { lock.unlock() }
        let factor = exp(learningRate * (consumed - target))
        _lambdaAdPressure = min(max(_lambdaAdPressure * factor, 0.001), maxLambda)
    }

    /// Resets lambda to its starting value — call at the start of a new session.
    public func reset() {
        lock.lock()
        _lambdaAdPressure = 0.1
        lock.unlock()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `swift test --filter LagrangianBudgetTests`
Expected: PASS (8/8)

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/LagrangianBudget.swift Tests/MonetizationBrainCoreTests/LagrangianBudgetTests.swift
git commit -m "feat: port LagrangianBudget from Android"
```

---

### Task 3: `PersonalizationScenario`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/PersonalizationScenario.swift`
- Test: `Tests/MonetizationBrainCoreTests/PersonalizationScenarioTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import XCTest
@testable import MonetizationBrainCore

final class PersonalizationScenarioTests: XCTestCase {
    override func tearDown() {
        ConsentState.shared.resetForTest()
        PersonalizationScenario.minConfidence = 0.40
        super.tearDown()
    }

    func test_forTypeMapsEachSegmentToItsScenario() {
        XCTAssertEqual(PersonalizationScenario.forType(.highSkillLowTolerance), .protectAndConvert)
        XCTAssertEqual(PersonalizationScenario.forType(.highSkillHighTolerance), .adForward)
        XCTAssertEqual(PersonalizationScenario.forType(.lowSkillHighChurnRisk), .retentionFirst)
        XCTAssertEqual(PersonalizationScenario.forType(.casualSteady), .balancedHybrid)
    }

    func test_interstitialCapPerSessionMatchesAndroid() {
        XCTAssertEqual(PersonalizationScenario.protectAndConvert.interstitialCapPerSession, 2)
        XCTAssertEqual(PersonalizationScenario.adForward.interstitialCapPerSession, 6)
        XCTAssertEqual(PersonalizationScenario.retentionFirst.interstitialCapPerSession, 1)
        XCTAssertEqual(PersonalizationScenario.balancedHybrid.interstitialCapPerSession, 3)
    }

    func test_policyKnobsMatchAndroidPerCase() {
        XCTAssertEqual(PersonalizationScenario.protectAndConvert.rewardedAggressiveness, 0.2, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.protectAndConvert.iapOfferPriority, 0.9, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.protectAndConvert.freeValueGenerosity, 0.3, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.adForward.rewardedAggressiveness, 0.8, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.retentionFirst.freeValueGenerosity, 0.9, accuracy: 1e-12)
        XCTAssertEqual(PersonalizationScenario.balancedHybrid.iapOfferPriority, 0.5, accuracy: 1e-12)
    }

    func test_resolveFallsBackToBalancedWhenPersonalizationNotConsented() {
        ConsentState.shared.setTier(.denied)
        let belief = BeliefState(playerId: "p1")
        belief.update(likelihood: [.highSkillHighTolerance: 10.0])
        XCTAssertGreaterThanOrEqual(belief.confidence(), PersonalizationScenario.minConfidence)
        XCTAssertEqual(PersonalizationScenario.resolve(belief), .balancedHybrid)
    }

    func test_resolveFallsBackToBalancedBelowMinConfidence() {
        let belief = BeliefState(playerId: "p1") // uniform prior: confidence 0.25
        XCTAssertLessThan(belief.confidence(), PersonalizationScenario.minConfidence)
        XCTAssertEqual(PersonalizationScenario.resolve(belief), .balancedHybrid)
    }

    func test_resolveAppliesSpecializedScenarioAboveMinConfidence() {
        let belief = BeliefState(playerId: "p1")
        belief.update(likelihood: [.highSkillHighTolerance: 10.0])
        XCTAssertGreaterThanOrEqual(belief.confidence(), PersonalizationScenario.minConfidence)
        XCTAssertEqual(PersonalizationScenario.resolve(belief), .adForward)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `swift test --filter PersonalizationScenarioTests`
Expected: FAIL — `PersonalizationScenario` not found.

- [ ] **Step 3: Write the implementation**

```swift
/// Personalization scenario — maps each latent segment to a concrete BUNDLE OF POLICY KNOBS.
///
/// Segment answers "who is this player"; scenario answers "so what do we do about it."
///
/// The numbers below are a PRIOR from domain knowledge, not yet calibrated with real data — a
/// rule of thumb. They're a starting point for the bandit to learn from, NOT a measured
/// conclusion. Once there's enough decision log volume, the bandit adjusts on its own and these
/// numbers become warm-start priors only.
public enum PersonalizationScenario: String, Sendable, CaseIterable {
    case protectAndConvert = "PROTECT_AND_CONVERT"
    case adForward = "AD_FORWARD"
    case retentionFirst = "RETENTION_FIRST"
    case balancedHybrid = "BALANCED_HYBRID"

    public var displayName: String {
        switch self {
        case .protectAndConvert: return "Protect & convert"
        case .adForward: return "Ad-forward"
        case .retentionFirst: return "Retention first"
        case .balancedHybrid: return "Balanced"
        }
    }

    public var rationale: String {
        switch self {
        case .protectAndConvert:
            return "High skill, low ad tolerance, has a payer signal -> fewer ads, prioritize IAP"
        case .adForward:
            return "High ad tolerance, low payer signal -> IAA is the main value source"
        case .retentionFirst:
            return "Many fails, high churn risk -> reduce all pressure, raise free value"
        case .balancedHybrid:
            return "Not enough signal to distinguish -> neutral policy, keep observing"
        }
    }

    /// Interstitial cap per session for this segment.
    public var interstitialCapPerSession: Int {
        switch self {
        case .protectAndConvert: return 2
        case .adForward: return 6
        case .retentionFirst: return 1
        case .balancedHybrid: return 3
        }
    }

    /// How proactively rewarded ads are offered (0 = only when the user seeks it out, 1 = every
    /// valid opportunity).
    public var rewardedAggressiveness: Double {
        switch self {
        case .protectAndConvert: return 0.2
        case .adForward: return 0.8
        case .retentionFirst: return 0.3
        case .balancedHybrid: return 0.5
        }
    }

    /// Priority given to IAP offers over ads (0 = never, 1 = absolute priority).
    public var iapOfferPriority: Double {
        switch self {
        case .protectAndConvert: return 0.9
        case .adForward: return 0.2
        case .retentionFirst: return 0.1
        case .balancedHybrid: return 0.5
        }
    }

    /// Generosity for FREE_ACTION — no-strings-attached value given to the player.
    public var freeValueGenerosity: Double {
        switch self {
        case .protectAndConvert: return 0.3
        case .adForward: return 0.2
        case .retentionFirst: return 0.9
        case .balancedHybrid: return 0.5
        }
    }

    /// Segment -> scenario. A deliberate 1:1 relationship: every segment has exactly one
    /// strategy.
    public static func forType(_ type: LatentPlayerType) -> PersonalizationScenario {
        switch type {
        case .highSkillLowTolerance: return .protectAndConvert
        case .highSkillHighTolerance: return .adForward
        case .lowSkillHighChurnRisk: return .retentionFirst
        case .casualSteady: return .balancedHybrid
        }
    }

    /// Minimum confidence required to apply a specialized scenario. Below this, belief is still
    /// too spread out to conclude anything — fall back to `.balancedHybrid` instead of guessing.
    /// Was remote-config-overridable via `BrainPolicy` on Android; fixed here (no remote policy
    /// artifact subsystem exists yet — a documented, deferred gap, see the Phase 3 design spec).
    public static var minConfidence: Double = 0.40

    /// Resolves the scenario currently in effect for `belief`.
    public static func resolve(_ belief: BeliefState) -> PersonalizationScenario {
        // Personalization requires consent to USE an existing profile — the profile itself may
        // still be on disk, unaffected; only its use is blocked, matching the exact boundary the
        // regulation draws.
        guard ConsentState.shared.allowsPersonalization() else { return .balancedHybrid }
        guard belief.confidence() >= minConfidence else { return .balancedHybrid }
        return forType(belief.mostLikelyType())
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `swift test --filter PersonalizationScenarioTests`
Expected: PASS (6/6).

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/PersonalizationScenario.swift Tests/MonetizationBrainCoreTests/PersonalizationScenarioTests.swift
git commit -m "feat: port PersonalizationScenario from Android"
```

---

### Task 4: `BrainKeyValueStore` / `UserDefaultsBrainStore` / `BrainStorage`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/BrainStorage.swift`
- Test: `Tests/MonetizationBrainCoreTests/BrainStorageTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import XCTest
@testable import MonetizationBrainCore

final class BrainStorageTests: XCTestCase {
    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? { values[key] }
        func setString(_ value: String, forKey key: String) { values[key] = value }
        func removeObject(forKey key: String) { values.removeValue(forKey: key) }
    }

    func test_loadBeliefReturnsNilWhenNeverSaved() {
        let storage = BrainStorage(store: InMemoryStore())
        XCTAssertNil(storage.loadBelief(playerId: "p1"))
    }

    func test_saveThenLoadBeliefRoundTrips() {
        let storage = BrainStorage(store: InMemoryStore())
        storage.saveBelief(playerId: "p1", serialized: "some-serialized-belief")
        XCTAssertEqual(storage.loadBelief(playerId: "p1"), "some-serialized-belief")
    }

    func test_beliefIsIsolatedPerPlayer() {
        let storage = BrainStorage(store: InMemoryStore())
        storage.saveBelief(playerId: "p1", serialized: "belief-p1")
        storage.saveBelief(playerId: "p2", serialized: "belief-p2")
        XCTAssertEqual(storage.loadBelief(playerId: "p1"), "belief-p1")
        XCTAssertEqual(storage.loadBelief(playerId: "p2"), "belief-p2")
    }

    func test_clearBeliefRemovesOnlyThatPlayer() {
        let storage = BrainStorage(store: InMemoryStore())
        storage.saveBelief(playerId: "p1", serialized: "belief-p1")
        storage.saveBelief(playerId: "p2", serialized: "belief-p2")
        storage.clearBelief(playerId: "p1")
        XCTAssertNil(storage.loadBelief(playerId: "p1"))
        XCTAssertEqual(storage.loadBelief(playerId: "p2"), "belief-p2")
    }

    func test_blankBeliefTreatedAsAbsent() {
        let store = InMemoryStore()
        store.setString("   ", forKey: "brain_belief_p1")
        let storage = BrainStorage(store: store)
        XCTAssertNil(storage.loadBelief(playerId: "p1"))
    }

    func test_loadBanditStatsReturnsNilWhenNeverSaved() {
        let storage = BrainStorage(store: InMemoryStore())
        XCTAssertNil(storage.loadBanditStats())
    }

    func test_saveThenLoadBanditStatsRoundTrips() {
        let storage = BrainStorage(store: InMemoryStore())
        storage.saveBanditStats("seg|arm=2.0,3.0")
        XCTAssertEqual(storage.loadBanditStats(), "seg|arm=2.0,3.0")
    }

    func test_userDefaultsBrainStoreRoundTripsThroughRealUserDefaults() {
        let suiteName = "BrainStorageTests.\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            return XCTFail("could not create isolated UserDefaults suite")
        }
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = UserDefaultsBrainStore(defaults: defaults)
        store.setString("value", forKey: "key")
        XCTAssertEqual(store.string(forKey: "key"), "value")
        store.removeObject(forKey: "key")
        XCTAssertNil(store.string(forKey: "key"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `swift test --filter BrainStorageTests`
Expected: FAIL — `BrainKeyValueStore`/`BrainStorage`/`UserDefaultsBrainStore` not found.

- [ ] **Step 3: Write the implementation**

```swift
import Foundation

/// Minimal key-value persistence `BrainStorage` needs. An injectable protocol — matching the
/// precedent `RemoteConfigProvider` already set — rather than wrapping `UserDefaults` directly,
/// so `DecisionEngine` stays unit-testable against an in-memory fake instead of polluting real
/// `UserDefaults` across test runs.
public protocol BrainKeyValueStore: Sendable {
    func string(forKey key: String) -> String?
    func setString(_ value: String, forKey key: String)
    func removeObject(forKey key: String)
}

/// Default `BrainKeyValueStore` backed by `UserDefaults`.
public struct UserDefaultsBrainStore: BrainKeyValueStore {
    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func string(forKey key: String) -> String? {
        defaults.string(forKey: key)
    }

    public func setString(_ value: String, forKey key: String) {
        defaults.set(value, forKey: key)
    }

    public func removeObject(forKey key: String) {
        defaults.removeObject(forKey: key)
    }
}

/// Durable storage for the brain, on top of an injected `BrainKeyValueStore`.
///
/// Why this exists: `DecisionEngine`'s belief/bandit state is otherwise in-memory only and is
/// lost the moment the process is killed. Without this, every app launch resets every player to
/// a uniform prior, and personalization never accumulates across sessions. This is a silent
/// failure that's easy to miss testing manually (everything looks correct within one session).
///
/// Not ported: `playerId()` (Android's self-generated stable device id) — unneeded, since
/// `AdsBrain.Configuration.playerId` is already a required field. `loadOfferCount24h`/
/// `bumpOfferCount24h` — not called by `DecisionEngine`; belongs to a call site not yet ported.
public final class BrainStorage: @unchecked Sendable {
    private static let beliefKeyPrefix = "brain_belief_"
    private static let banditStatsKey = "brain_bandit_stats"

    private let store: BrainKeyValueStore

    public init(store: BrainKeyValueStore) {
        self.store = store
    }

    public func loadBelief(playerId: String) -> String? {
        let raw = store.string(forKey: Self.beliefKeyPrefix + playerId)
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return raw
    }

    public func saveBelief(playerId: String, serialized: String) {
        store.setString(serialized, forKey: Self.beliefKeyPrefix + playerId)
    }

    public func clearBelief(playerId: String) {
        store.removeObject(forKey: Self.beliefKeyPrefix + playerId)
    }

    public func loadBanditStats() -> String? {
        let raw = store.string(forKey: Self.banditStatsKey)
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return raw
    }

    public func saveBanditStats(_ serialized: String) {
        store.setString(serialized, forKey: Self.banditStatsKey)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `swift test --filter BrainStorageTests`
Expected: PASS (8/8)

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/BrainStorage.swift Tests/MonetizationBrainCoreTests/BrainStorageTests.swift
git commit -m "feat: add BrainKeyValueStore/UserDefaultsBrainStore/BrainStorage"
```

---

### Task 5: `GuardrailEngine` real rules (replaces the Phase 1 skeleton) + `AdsBrain` migration

**Files:**
- Modify (full rewrite): `Sources/MonetizationBrainCore/Brain/GuardrailEngine.swift`
- Modify: `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`
- Modify (full rewrite): `Tests/MonetizationBrainCoreTests/GuardrailEngineTests.swift`

**Context:** Phase 1's `GuardrailEngine` is a *protocol* (`allows(action:playerId:) -> Bool`) with
one conformer, `AllowAllGuardrailEngine`, explicitly documented as a stand-in ("no real rules yet
— those arrive with the bandit engine in a later phase"). Android's `GuardrailEngine` was never a
protocol — one concrete class, owned by `DecisionEngine`. This task replaces the protocol
entirely with that real class. This is safe: `AdsBrain.Configuration.guardrailEngine` is stored
but never read by any Phase 1/1.5 method (verified against the current source) — no runtime
behavior of already-shipped code depends on the old protocol shape.

- [ ] **Step 1: Write the failing tests** (replaces the entire existing file)

```swift
import XCTest
@testable import MonetizationBrainCore

final class GuardrailEngineTests: XCTestCase {
    override func tearDown() {
        ClickFraudGuard.resetForTest()
        super.tearDown()
    }

    private func candidate(_ mode: ActionMode, id: String = "a1") -> ActionCandidate {
        ActionCandidate(mode: mode, actionId: id)
    }

    private func state(playerId: String = "p1") -> PlayerState {
        PlayerState(playerId: playerId)
    }

    // MARK: - Default (all-off) behavior

    func test_defaultThresholdsAllowInterstitialWithNoPressure() {
        let engine = GuardrailEngine()
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: state(),
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    // MARK: - HARD: click fraud

    func test_clickFraudLockoutBlocksInterstitial() {
        let engine = GuardrailEngine()
        for _ in 0..<7 { ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0) }
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: state(),
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "CLICK_FRAUD_LOCKOUT")
        XCTAssertEqual(violation?.kind, .hard)
    }

    func test_clickFraudLockoutExemptsNoActionGameplayConfigAndFreeAction() {
        let engine = GuardrailEngine()
        for _ in 0..<7 { ClickFraudGuard.registerClick(playerId: "p1", nowMs: 0) }
        for mode in [ActionMode.noAction, .gameplayConfig, .freeAction] {
            let violation = engine.evaluate(
                candidate: candidate(mode),
                state: state(),
                adsShownThisSession: 0,
                minutesSinceLastInterstitial: 999
            )
            XCTAssertNil(violation, "\(mode) should be exempt from click-fraud lockout")
        }
    }

    // MARK: - HARD: ethics

    func test_rewardedRequiresRealFailStreak() {
        let engine = GuardrailEngine()
        var s = state()
        s.failStreakCurrent = 0
        let violation = engine.evaluate(
            candidate: candidate(.rewardedAd),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "ETH_MANUFACTURED_FAIL")
    }

    func test_rewardedAllowedAfterRealFailStreak() {
        let engine = GuardrailEngine()
        var s = state()
        s.failStreakCurrent = 1
        let violation = engine.evaluate(
            candidate: candidate(.rewardedAd),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    func test_iapOfferBlockedDuringFrustrationStreak() {
        let engine = GuardrailEngine()
        var s = state()
        s.failStreakCurrent = 5
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "ETH_FRUSTRATION_EXPLOIT")
    }

    func test_iapVelocityBreakerBlocksAfterMaxPurchasesInSession() {
        let engine = GuardrailEngine()
        var s = state()
        s.purchasesThisSession = 3
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "IAP_VELOCITY_BREAKER")
        XCTAssertEqual(violation?.kind, .hard)
    }

    // MARK: - RESOURCE: interstitial-specific

    func test_newPlayerGraceBlocksBelowMinLevel() {
        let engine = GuardrailEngine()
        engine.minLevelForInterstitial = 10
        var s = state()
        s.currentLevel = 5
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "NEW_PLAYER_GRACE")
        XCTAssertEqual(violation?.kind, .resource)
    }

    func test_newPlayerGraceUsesLossThresholdOnLossBranch() {
        let engine = GuardrailEngine()
        engine.minLevelForInterstitial = 5
        engine.minLevelForInterstitialAfterLoss = 20
        var s = state()
        s.currentLevel = 10 // clears the win threshold, not the loss threshold
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999,
            decisionPoint: .levelFail
        )
        XCTAssertEqual(violation?.code, "NEW_PLAYER_GRACE")
    }

    func test_reopenGraceSkipsFirstNOpportunities() {
        let engine = GuardrailEngine()
        engine.skipFirstInterstitialsOnReopen = 2
        var s = state()
        s.interstitialOpportunitiesThisSession = 1
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "REOPEN_GRACE")
    }

    func test_postRewardedCooldownBlocksShortlyAfterRewarded() {
        let engine = GuardrailEngine()
        engine.minMinutesAfterRewarded = 5
        var s = state()
        s.minutesSinceLastRewarded = 2
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "INTER_AFTER_REWARDED_COOLDOWN")
    }

    func test_postRewardedCooldownIgnoresNeverWatchedSentinel() {
        let engine = GuardrailEngine()
        engine.minMinutesAfterRewarded = 5
        var s = state()
        s.minutesSinceLastRewarded = -1
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    func test_sessionCapUsesEffectiveInterstitialCap() {
        let engine = GuardrailEngine()
        engine.maxInterstitialPerSession = 6
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: state(),
            adsShownThisSession: 2,
            minutesSinceLastInterstitial: 999,
            scenario: .protectAndConvert // cap 2
        )
        XCTAssertEqual(violation?.code, "AD_PRESSURE_SESSION_CAP")
    }

    func test_cooldownBetweenInterstitialsBlocksTooSoon() {
        let engine = GuardrailEngine()
        engine.minMinutesBetweenInterstitial = 3
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: state(),
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 1
        )
        XCTAssertEqual(violation?.code, "AD_PRESSURE_COOLDOWN")
    }

    func test_dailyAdCapBlocksAt24hLimit() {
        let engine = GuardrailEngine()
        var s = state()
        s.adImpressions24h = 40
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "AD_PRESSURE_24H_CAP")
    }

    // MARK: - RESOURCE: payer protection

    func test_payerProtectionBlocksAdLikeShortlyAfterPurchase() {
        let engine = GuardrailEngine()
        var s = state()
        s.purchaseCountLifetime = 1
        s.daysSinceLastPurchase = 1
        let violation = engine.evaluate(
            candidate: candidate(.rewardedAd),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "PAYER_PROTECTION_RECENT")
    }

    func test_payerProtectionDoesNotBlockNonAdLike() {
        let engine = GuardrailEngine()
        var s = state()
        s.purchaseCountLifetime = 1
        s.daysSinceLastPurchase = 1
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNotEqual(violation?.code, "PAYER_PROTECTION_RECENT")
    }

    // MARK: - RESOURCE: IAP offer caps

    func test_iapOffer24hCapBlocksAtLimit() {
        let engine = GuardrailEngine()
        engine.maxIapOffers24h = 5
        var s = state()
        s.iapOffers24h = 5
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "IAP_OFFER_24H_CAP")
    }

    func test_iapOfferSessionCapBlocksAtLimit() {
        let engine = GuardrailEngine()
        var s = state()
        s.iapOffersThisSession = 3
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "IAP_OFFER_SESSION_CAP")
    }

    func test_iapOfferCooldownBlocksTooSoon() {
        let engine = GuardrailEngine()
        var s = state()
        s.minutesSinceLastIapOffer = 2
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "IAP_OFFER_COOLDOWN")
    }

    func test_iapOfferCooldownIgnoresNeverOfferedSentinel() {
        let engine = GuardrailEngine()
        var s = state()
        s.minutesSinceLastIapOffer = -1
        let violation = engine.evaluate(
            candidate: candidate(.iapOffer),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    // MARK: - RESOURCE: assists

    func test_assistSessionCapBlocksFreeActionAtLimit() {
        let engine = GuardrailEngine()
        var s = state()
        s.assistsThisSession = 3
        let violation = engine.evaluate(
            candidate: candidate(.freeAction),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "ASSIST_SESSION_CAP")
    }

    func test_assistSessionCapBlocksGameplayConfigAtLimit() {
        let engine = GuardrailEngine()
        var s = state()
        s.assistsThisSession = 3
        let violation = engine.evaluate(
            candidate: candidate(.gameplayConfig),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "ASSIST_SESSION_CAP")
    }

    func test_assistCounterIsolatedFromAdAndOfferCounters() {
        let engine = GuardrailEngine()
        var s = state()
        s.assistsThisSession = 0
        s.adImpressions24h = 999
        s.iapOffersThisSession = 999
        let violation = engine.evaluate(
            candidate: candidate(.freeAction),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertNil(violation)
    }

    // MARK: - RESOURCE: churn protection

    func test_churnProtectionBlocksInterstitialAboveThreshold() {
        let engine = GuardrailEngine()
        var s = state()
        s.churnRisk = 0.9
        let violation = engine.evaluate(
            candidate: candidate(.interstitial),
            state: s,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertEqual(violation?.code, "CHURN_PROTECTION")
    }

    // MARK: - effectiveInterstitialCap

    func test_effectiveInterstitialCapReturnsAbsoluteCapWhenScenarioIsNil() {
        let engine = GuardrailEngine()
        engine.maxInterstitialPerSession = 6
        XCTAssertEqual(engine.effectiveInterstitialCap(nil), 6)
    }

    func test_effectiveInterstitialCapNeverExceedsAbsoluteCap() {
        let engine = GuardrailEngine()
        engine.maxInterstitialPerSession = 4
        XCTAssertEqual(engine.effectiveInterstitialCap(.adForward), 4) // adForward's own cap is 6
    }

    func test_effectiveInterstitialCapUsesScenarioWhenTighter() {
        let engine = GuardrailEngine()
        engine.maxInterstitialPerSession = 6
        XCTAssertEqual(engine.effectiveInterstitialCap(.protectAndConvert), 2)
    }

    // MARK: - prevented counts

    func test_preventedCountsIncrementOnEachBlock() {
        let engine = GuardrailEngine()
        var s = state()
        s.adImpressions24h = 40
        _ = engine.evaluate(candidate: candidate(.interstitial), state: s, adsShownThisSession: 0, minutesSinceLastInterstitial: 999)
        _ = engine.evaluate(candidate: candidate(.interstitial), state: s, adsShownThisSession: 0, minutesSinceLastInterstitial: 999)
        XCTAssertEqual(engine.preventedCount("AD_PRESSURE_24H_CAP"), 2)
        XCTAssertEqual(engine.allPreventedCounts()["AD_PRESSURE_24H_CAP"], 2)
    }

    func test_preventedCountIsZeroForNeverTriggeredCode() {
        let engine = GuardrailEngine()
        XCTAssertEqual(engine.preventedCount("NEVER_TRIGGERED"), 0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `swift test --filter GuardrailEngineTests`
Expected: build FAIL — the old protocol has no `evaluate`/thresholds/`preventedCount`.

- [ ] **Step 3: Write the implementation** (replaces `GuardrailEngine.swift` entirely)

```swift
import Foundation

/// Guardrail engine — runs BEFORE the bandit ever sees the action space.
///
/// TWO-TIER CLASSIFICATION:
///
///  - `.hard`: an ABSOLUTE constraint (ethics, store policy, anti-fraud, child protection).
///    Removes the action from the candidate set outright, never expressed as a soft penalty in
///    reward — because a penalty can always be "bought" with enough revenue.
///  - `.resource`: a BUDGETED resource constraint (ad-load/session, daily fatigue, churn risk).
///    Bandits-with-Knapsacks theory says the optimal solution is a MIXED distribution over arms,
///    not "pick the best arm, then block." A hard filter on this tier alone biases the system.
///    `LagrangianBudget` provides the correct shadow-price treatment; the thresholds in this
///    class are the ABSOLUTE SAFETY CEILING at the edge, not the sole regulating mechanism —
///    the two layers complement each other.
///
/// Two design principles (distilled from a competitor teardown):
/// 1. "Fail-safe, not fail-aggressive" — every default in this class must be the SAFEST edge of
///    the system. A competitor app got this backwards: a config parse error made it silently
///    sell its most expensive bundle and 12x its ad density.
/// 2. "Isolate state by action kind" — every `ActionMode` gets its OWN cap state, never shared
///    with an unrelated mode (the same competitor shared one cooldown slot between pip/banner
///    and squeeze/native ads).
public enum GuardrailKind: Sendable, Equatable {
    /// Absolute constraint — always a hard filter, correctly so.
    case hard
    /// Resource constraint — smoothed by `LagrangianBudget`; the threshold here is the edge cap.
    case resource
}

public struct GuardrailViolation: Sendable, Equatable {
    public let code: String
    public let reason: String
    public let kind: GuardrailKind

    public init(code: String, reason: String, kind: GuardrailKind) {
        self.code = code
        self.reason = reason
        self.kind = kind
    }
}

public final class GuardrailEngine: @unchecked Sendable {
    // Resource budget. Production loads these from a policy artifact; the values here are the
    // SAFEST edge — if the artifact fetch fails, the system falls back to these and must be
    // MORE conservative, never looser.
    public var maxInterstitialPerSession: Int = 6
    public var minMinutesBetweenInterstitial: Int = 3
    public var maxAds24h: Int = 40
    public var churnRiskProtectionThreshold: Double = 0.75

    // --- IAP_OFFER thresholds ---
    public var maxIapOffersPerSession: Int = 3
    public var minMinutesBetweenIapOffers: Int = 10

    /// Purchases in one session enough to CUT OFF offers — the spend-velocity circuit breaker.
    public var maxPurchasesPerSessionBeforeBreak: Int = 3

    /// A losing streak deep enough to count as "stuck" — no offers at this point.
    ///
    /// The line isn't whether there's a loss at all (offering after a few losses is a normal
    /// game-design pattern) — it's that willingness-to-pay rises with the LENGTH of the losing
    /// streak. The more stuck someone is, the more "effective" an offer becomes — which is
    /// exactly why it isn't allowed.
    public var frustrationStreakThreshold: Int = 5

    // --- Progression-based guardrails (distilled from a production puzzle-game teardown) ---
    // Common thread: protect the FRAGILE MOMENT. A brand-new player not yet attached, someone
    // who just lost and is frustrated, someone who just reopened the app and hasn't found their
    // rhythm yet — three moments where an ad's immediate value is about the same but the
    // retention cost differs enormously.
    // Default 0/-1 = OFF, preserving old behavior. Turning these on is the studio's call.

    /// Don't show an interstitial before this level. The reference game uses 10.
    public var minLevelForInterstitial: Int = 0

    /// Separate, usually higher threshold for the LOSS branch (reference game: 22 vs 10). The
    /// reason is psychological, not technical: someone who just lost is already frustrated;
    /// stacking an ad on top pushes them to leave — the same logic `RewardModel` relies on.
    /// 0 = share the winning branch's threshold.
    public var minLevelForInterstitialAfterLoss: Int = 0

    /// Defer an interstitial after the player just watched a rewarded ad. Kept separate from the
    /// normal cooldown: someone who just VOLUNTARILY watched a long ad shouldn't immediately get
    /// a forced one stacked on top.
    public var minMinutesAfterRewarded: Int = 0

    /// Skip the first N interstitial opportunities after reopening the app — the moment the
    /// player is deciding whether to stay.
    public var skipFirstInterstitialsOnReopen: Int = 0

    /// Daily offer cap. A session cap alone can't stop someone who opens the app 10x a day.
    public var maxIapOffers24h: Int = 0

    /// Cap on gameplay assists (FREE_ACTION gift / GAMEPLAY_CONFIG difficulty tuning) per
    /// session.
    ///
    /// Assists are FREE for the studio but not free for the experience: stacking them creates
    /// learned helplessness (the player stops trying because help is coming), breaks the
    /// progression signal the game depends on, and skews the game economy. This is the edge cap
    /// for both assist action kinds — the rhythm inside it self-regulates (a gift only has value
    /// when the player is actually stuck).
    public var maxAssistsPerSession: Int = 3

    private let lock = NSLock()
    /// Count of successful blocks per guardrail — feeds a future ComfortIndex.
    private var preventedCounts: [String: Int] = [:]

    public init() {}

    public func preventedCount(_ code: String) -> Int {
        lock.lock()
        defer { lock.unlock() }
        return preventedCounts[code] ?? 0
    }

    public func allPreventedCounts() -> [String: Int] {
        lock.lock()
        defer { lock.unlock() }
        return preventedCounts
    }

    /// Effective interstitial cap = min(absolute cap, scenario cap).
    ///
    /// The `min` direction is deliberate and a real safety invariant: a personalization scenario
    /// may only TIGHTEN the cap, never loosen it past the shared ceiling. If loosening were
    /// allowed, a player misclassified into an ad-forward segment would get MORE ads than the
    /// global policy permits at all — exactly the kind of failure the fail-safe principle above
    /// forbids.
    public func effectiveInterstitialCap(_ scenario: PersonalizationScenario?) -> Int {
        guard let scenario else { return maxInterstitialPerSession }
        return min(maxInterstitialPerSession, scenario.interstitialCapPerSession)
    }

    private func record(_ violation: GuardrailViolation) -> GuardrailViolation {
        lock.lock()
        preventedCounts[violation.code, default: 0] += 1
        lock.unlock()
        return violation
    }

    /// - Parameters:
    ///   - scenario: the personalization scenario in effect. Its cap can only make the system
    ///     MORE conservative — see `effectiveInterstitialCap`.
    ///   - decisionPoint: needed to distinguish the WIN branch from the LOSS branch — they have
    ///     different thresholds. `nil` = context unknown, treated as the win branch.
    public func evaluate(
        candidate: ActionCandidate,
        state: PlayerState,
        adsShownThisSession: Int,
        minutesSinceLastInterstitial: Int,
        scenario: PersonalizationScenario? = nil,
        decisionPoint: DecisionPoint? = nil
    ) -> GuardrailViolation? {
        let isAdLike = candidate.mode == .interstitial || candidate.mode == .rewardedAd

        // ================= HARD — absolute constraints =================

        // Anti-click-fraud: protects the mediation account. Exempts NO_ACTION, GAMEPLAY_CONFIG,
        // and FREE_ACTION — this lock protects the ad inventory from fraudulent clicks, not
        // gifts or difficulty tuning.
        if candidate.mode != .noAction,
           candidate.mode != .gameplayConfig,
           candidate.mode != .freeAction,
           ClickFraudGuard.isLocked(playerId: state.playerId) {
            return record(GuardrailViolation(
                code: "CLICK_FRAUD_LOCKOUT",
                reason: "exceeded click threshold in window, ads locked",
                kind: .hard
            ))
        }

        // Ethics: rewarded may only be offered after a REAL fail — never a "manufactured fail."
        // An ethical constraint is never turned into a reward penalty.
        if candidate.mode == .rewardedAd, state.failStreakCurrent <= 0 {
            return record(GuardrailViolation(
                code: "ETH_MANUFACTURED_FAIL",
                reason: "rewarded should only be offered after a real fail",
                kind: .hard
            ))
        }

        // Ethics: don't monetize being stuck.
        if candidate.mode == .iapOffer, state.failStreakCurrent >= frustrationStreakThreshold {
            return record(GuardrailViolation(
                code: "ETH_FRUSTRATION_EXPLOIT",
                reason: "stuck (fail streak \(state.failStreakCurrent)) — no offer",
                kind: .hard
            ))
        }

        // Spend-velocity circuit breaker.
        //
        // HARD, not RESOURCE — this is the crux: filing it as RESOURCE would turn it into a soft
        // penalty the bandit can "buy back" with enough revenue — and revenue from exactly the
        // group spending in a rapid burst is always big enough to buy it. Protecting vulnerable
        // people is never allowed to have a price.
        if candidate.mode == .iapOffer, state.purchasesThisSession >= maxPurchasesPerSessionBeforeBreak {
            return record(GuardrailViolation(
                code: "IAP_VELOCITY_BREAKER",
                reason: "purchased \(state.purchasesThisSession)x this session — pausing offers",
                kind: .hard
            ))
        }

        // ================= RESOURCE — edge-of-budget caps =================
        // The rhythm INSIDE the budget is regulated by `LagrangianBudget` (shadow price
        // subtracted from reward before the bandit chooses). The thresholds below are the final
        // stop: reaching them means the shadow price didn't restrain enough, and there's no
        // further negotiation.

        if candidate.mode == .interstitial {
            let isLossPath = decisionPoint == .levelFail || decisionPoint == .levelFailStreak
            let levelGate = (isLossPath && minLevelForInterstitialAfterLoss > 0)
                ? minLevelForInterstitialAfterLoss
                : minLevelForInterstitial
            if levelGate > 0, state.currentLevel < levelGate {
                return record(GuardrailViolation(
                    code: "NEW_PLAYER_GRACE",
                    reason: "level \(state.currentLevel) < threshold \(levelGate)"
                        + (isLossPath ? " (loss branch)" : ""),
                    kind: .resource
                ))
            }

            if skipFirstInterstitialsOnReopen > 0,
               state.interstitialOpportunitiesThisSession < skipFirstInterstitialsOnReopen {
                return record(GuardrailViolation(
                    code: "REOPEN_GRACE",
                    reason: "opportunity #\(state.interstitialOpportunitiesThisSession + 1) after reopening",
                    kind: .resource
                ))
            }

            // -1 = never, meant as "infinitely long ago" — getting this wrong would block
            // interstitials for everyone who has never watched a rewarded ad, i.e. most players.
            if minMinutesAfterRewarded > 0,
               (0..<minMinutesAfterRewarded).contains(state.minutesSinceLastRewarded) {
                return record(GuardrailViolation(
                    code: "INTER_AFTER_REWARDED_COOLDOWN",
                    reason: "watched rewarded \(state.minutesSinceLastRewarded) min ago",
                    kind: .resource
                ))
            }

            let cap = effectiveInterstitialCap(scenario)
            if adsShownThisSession >= cap {
                let scenarioNote = scenario.map { ", per scenario \($0.displayName)" } ?? ""
                return record(GuardrailViolation(
                    code: "AD_PRESSURE_SESSION_CAP",
                    reason: "exceeded interstitial/session cap (effective=\(cap)\(scenarioNote))",
                    kind: .resource
                ))
            }
            if minutesSinceLastInterstitial < minMinutesBetweenInterstitial {
                return record(GuardrailViolation(
                    code: "AD_PRESSURE_COOLDOWN",
                    reason: "not enough cooldown between interstitials",
                    kind: .resource
                ))
            }
            if state.adImpressions24h >= maxAds24h {
                return record(GuardrailViolation(
                    code: "AD_PRESSURE_24H_CAP",
                    reason: "exceeded max_ads_24h",
                    kind: .resource
                ))
            }
        }

        // Protect a recent payer: reduce ad pressure right after a transaction.
        //
        // Identified by PURCHASE COUNT, not total spend — with real billing, prices come back in
        // local currency and only sum to USD once the exchange rate is known; someone who paid
        // 9.99 EUR on a device with no cached EUR rate has a USD total of 0. Using total spend as
        // the "have they ever paid" test would silently drop exactly those payers, and only in
        // some markets.
        if isAdLike, state.purchaseCountLifetime > 0, (0...2).contains(state.daysSinceLastPurchase) {
            return record(GuardrailViolation(
                code: "PAYER_PROTECTION_RECENT",
                reason: "recent payer needs reduced ad pressure",
                kind: .resource
            ))
        }

        // Offer frequency cap. Fully isolated from ad state — sharing a counter between two
        // unrelated action kinds is exactly the bug seen in the competitor teardown.
        if candidate.mode == .iapOffer {
            if maxIapOffers24h > 0, state.iapOffers24h >= maxIapOffers24h {
                return record(GuardrailViolation(
                    code: "IAP_OFFER_24H_CAP",
                    reason: "exceeded offer/24h cap (\(maxIapOffers24h))",
                    kind: .resource
                ))
            }
            if state.iapOffersThisSession >= maxIapOffersPerSession {
                return record(GuardrailViolation(
                    code: "IAP_OFFER_SESSION_CAP",
                    reason: "exceeded offer/session cap (\(maxIapOffersPerSession))",
                    kind: .resource
                ))
            }
            // -1 = no offer shown yet this session, must be read as "infinitely long ago," not
            // "0 minutes ago" — getting this wrong blocks exactly the very FIRST offer.
            if (0..<minMinutesBetweenIapOffers).contains(state.minutesSinceLastIapOffer) {
                return record(GuardrailViolation(
                    code: "IAP_OFFER_COOLDOWN",
                    reason: "not enough cooldown between offers",
                    kind: .resource
                ))
            }
        }

        // Gameplay-assist frequency cap (FREE_ACTION / GAMEPLAY_CONFIG). Isolated from both ad
        // and offer counters — three unrelated action kinds, no shared state (state-isolation
        // principle above).
        if (candidate.mode == .freeAction || candidate.mode == .gameplayConfig),
           state.assistsThisSession >= maxAssistsPerSession {
            return record(GuardrailViolation(
                code: "ASSIST_SESSION_CAP",
                reason: "exceeded assist/session cap (\(maxAssistsPerSession))",
                kind: .resource
            ))
        }

        // Churn-risk budget.
        if candidate.mode == .interstitial, state.churnRisk > churnRiskProtectionThreshold {
            return record(GuardrailViolation(
                code: "CHURN_PROTECTION",
                reason: "high churn_risk, avoid adding ad pressure",
                kind: .resource
            ))
        }

        return nil
    }
}
```

- [ ] **Step 4: Migrate `AdsBrain.Configuration`**

In `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`, change:

```swift
        public init(
            remoteConfig: RemoteConfigProvider,
            eventSink: EventSink,
            playerId: String,
            guardrailEngine: GuardrailEngine = AllowAllGuardrailEngine()
        ) {
```

to:

```swift
        public init(
            remoteConfig: RemoteConfigProvider,
            eventSink: EventSink,
            playerId: String,
            guardrailEngine: GuardrailEngine = GuardrailEngine()
        ) {
```

(The stored property type annotation `public let guardrailEngine: GuardrailEngine` above it needs
no change — it already names the type `GuardrailEngine`, which now resolves to the real class
instead of the old protocol.)

- [ ] **Step 5: Run test to verify it passes**

Run: `swift build && swift test --filter GuardrailEngineTests`
Expected: build succeeds, tests PASS (27/27). Then run the FULL suite once —
`swift test` — to confirm `AdsBrainTests` still passes with the migrated `Configuration` default.

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/GuardrailEngine.swift Sources/MonetizationBrainCore/Brain/AdsBrain.swift Tests/MonetizationBrainCoreTests/GuardrailEngineTests.swift
git commit -m "feat: port real GuardrailEngine rules from Android, replacing the Phase 1 skeleton"
```

---

### Task 6: `DecisionEngine`

**Files:**
- Create: `Sources/MonetizationBrainCore/Brain/DecisionEngine.swift`
- Test: `Tests/MonetizationBrainCoreTests/DecisionEngineTests.swift`

**Context:** This is the orchestrator tying every prior task (and Phase 2's `BeliefState`/
`RewardModel`/`LikelihoodEstimator`/`BanditPolicy`) together. Read the design spec §4's
`DecisionEngine` section before implementing if anything below is ambiguous — it walks through
`decide`'s 9 steps in prose.

- [ ] **Step 1: Write the failing tests**

```swift
import XCTest
@testable import MonetizationBrainCore

final class DecisionEngineTests: XCTestCase {
    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? { values[key] }
        func setString(_ value: String, forKey key: String) { values[key] = value }
        func removeObject(forKey key: String) { values.removeValue(forKey: key) }
    }

    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    private func noActionCandidate(id: String = "no_action", isBaseline: Bool = false) -> ActionCandidate {
        ActionCandidate(mode: .noAction, actionId: id, isBaseline: isBaseline)
    }

    override func setUp() {
        super.setUp()
        HoldoutManager.holdoutFraction = 0.0
        ConsentState.shared.resetForTest()
    }

    override func tearDown() {
        HoldoutManager.holdoutFraction = 0.05
        ConsentState.shared.resetForTest()
        super.tearDown()
    }

    private func makeEngine(sink: EventSink? = nil) -> DecisionEngine {
        DecisionEngine(guardrails: GuardrailEngine(), store: InMemoryStore(), eventSink: sink)
    }

    // MARK: - decide: basic contract

    func test_decideReturnsNoActionWhenOnlyCandidateAvailable() async {
        let engine = makeEngine()
        let response = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        XCTAssertEqual(response.chosenAction.actionId, "no_action")
        XCTAssertEqual(response.eligibleActionIds, ["no_action"])
    }

    func test_decideFallsBackToNoActionWhenGuardrailsRemoveEveryRealCandidate() async {
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.failStreakCurrent = 0 // manufactured-fail guardrail blocks rewarded
        let response = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .rewardedOpportunity),
            state: state,
            candidates: [
                noActionCandidate(),
                ActionCandidate(mode: .rewardedAd, actionId: "r1")
            ]
        )
        XCTAssertEqual(response.chosenAction.mode, .noAction)
        XCTAssertTrue(response.guardrailsApplied.contains("ETH_MANUFACTURED_FAIL"))
    }

    // MARK: - decide: holdout path

    func test_holdoutIsDeterministicAndUsesBaselineCandidate() async {
        HoldoutManager.holdoutFraction = 1.0
        let engine = makeEngine()
        let baseline = ActionCandidate(mode: .interstitial, actionId: "baseline_int", isBaseline: true)
        let response = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .interstitialOpportunity),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate(), baseline],
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertTrue(response.isHoldout)
        XCTAssertEqual(response.chosenAction.actionId, "baseline_int")
        XCTAssertEqual(response.chosenActionProbability, 1.0)
        XCTAssertEqual(response.scenario, .balancedHybrid)
    }

    func test_holdoutFallsBackToNoActionWhenNoBaselineMarked() async {
        HoldoutManager.holdoutFraction = 1.0
        let engine = makeEngine()
        let response = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .interstitialOpportunity),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate(), ActionCandidate(mode: .interstitial, actionId: "int1")]
        )
        XCTAssertEqual(response.chosenAction.mode, .noAction)
    }

    func test_holdoutFeedbackDoesNotUpdateBandit() async {
        HoldoutManager.holdoutFraction = 1.0
        let engine = makeEngine()
        let baseline = ActionCandidate(mode: .interstitial, actionId: "baseline_int", isBaseline: true)
        let request = DecisionRequest(playerId: "p1", decisionPoint: .interstitialOpportunity)
        let response = await engine.decide(
            request: request,
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate(), baseline]
        )
        await engine.feedback(request: request, response: response, outcomeReward01: 1.0)
        let count = engine.observationCount(
            decisionPoint: .interstitialOpportunity,
            segment: .casualSteady,
            armId: "baseline_int"
        )
        XCTAssertEqual(count, 0.0)
    }

    // MARK: - decide/feedback: treatment path composability

    func test_decideAndFeedbackRoundTripUpdatesBanditObservationCount() async {
        HoldoutManager.holdoutFraction = 0.0
        let engine = makeEngine()
        let request = DecisionRequest(playerId: "p1", decisionPoint: .interstitialOpportunity)
        let candidates = [noActionCandidate(), ActionCandidate(mode: .interstitial, actionId: "int1")]
        let response = await engine.decide(
            request: request,
            state: PlayerState(playerId: "p1"),
            candidates: candidates,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertFalse(response.isHoldout)
        await engine.feedback(request: request, response: response, outcomeReward01: 1.0)

        let segment = response.segment ?? .casualSteady
        let count = engine.observationCount(
            decisionPoint: .interstitialOpportunity,
            segment: segment,
            armId: response.chosenAction.actionId
        )
        XCTAssertGreaterThan(count, 0.0)
    }

    // MARK: - observe / beliefFor

    func test_observeUpdatesBeliefWhenConsentAllowsProfiling() {
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 10
        state.failRateLast30m = 0.65
        state.daysSinceLastSession = 9
        state.purchaseCountLifetime = 0
        let updated = engine.observe(state)
        XCTAssertTrue(updated)
        XCTAssertFalse(engine.beliefFor("p1").isStillPrior())
    }

    func test_observeReturnsFalseWhenProfilingConsentDenied() {
        ConsentState.shared.setTier(.denied)
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 10
        state.failRateLast30m = 0.65
        let updated = engine.observe(state)
        XCTAssertFalse(updated)
    }

    // MARK: - resetPlayer

    func test_resetPlayerClearsBothInMemoryAndStorage() {
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 10
        state.failRateLast30m = 0.65
        _ = engine.observe(state)
        XCTAssertFalse(engine.beliefFor("p1").isStillPrior())

        engine.resetPlayer("p1")

        XCTAssertTrue(engine.beliefFor("p1").isStillPrior())
    }

    // MARK: - startNewSession / budgetFor

    func test_startNewSessionResetsBudgetLambda() {
        let engine = makeEngine()
        let budget = engine.budgetFor("p1")
        budget.observe(chosenMode: .interstitial, budgetPerDecision: 0.0)
        XCTAssertNotEqual(budget.lambdaAdPressure, 0.1)
        engine.startNewSession("p1")
        XCTAssertEqual(engine.budgetFor("p1").lambdaAdPressure, 0.1, accuracy: 1e-12)
    }

    // MARK: - effectiveInterstitialCap

    func test_effectiveInterstitialCapReflectsScenario() {
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 20
        state.failRateLast30m = 0.05
        state.rewardedAcceptRateRecent = 0.9
        state.rewardedOpportunitiesRecent = 10
        state.purchaseCountLifetime = 2
        _ = engine.observe(state)
        let cap = engine.effectiveInterstitialCap("p1")
        XCTAssertLessThanOrEqual(cap, 6)
        XCTAssertGreaterThan(cap, 0)
    }

    // MARK: - event emission

    func test_decideEmitsBrainDecisionEventWithRealBackendFieldNames() async {
        let sink = FakeEventSink()
        let engine = makeEngine(sink: sink)
        _ = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1)
        let event = events[0]
        XCTAssertEqual(event.eventName, "brain_decision")
        XCTAssertEqual(event.eventData["decision_point"], .string("SHOP_OPEN"))
        XCTAssertEqual(event.eventData["chosen_action_mode"], .string("NO_ACTION"))
        XCTAssertNotNil(event.eventData["decision_id"])
        XCTAssertNotNil(event.eventData["chosen_action_probability"])
        XCTAssertEqual(event.eventData["is_holdout"], .int(0))
        XCTAssertEqual(event.userProperty.userId, "p1")
    }

    func test_feedbackEmitsBrainFeedbackEventWithRewardValue() async {
        let sink = FakeEventSink()
        let engine = makeEngine(sink: sink)
        let request = DecisionRequest(playerId: "p1", decisionPoint: .shopOpen)
        let response = await engine.decide(
            request: request,
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        await engine.feedback(request: request, response: response, outcomeReward01: 0.5)
        let events = await sink.sentEvents
        let feedbackEvent = events.first { $0.eventName == "brain_feedback" }
        XCTAssertEqual(feedbackEvent?.eventData["decision_id"], .string(response.decisionId))
        XCTAssertEqual(feedbackEvent?.eventData["reward_value"], .double(0.5))
        XCTAssertEqual(feedbackEvent?.eventData["outcome_event"], .string("outcome_reward"))
    }

    func test_decisionIdsAreUniqueAcrossCalls() async {
        let engine = makeEngine()
        let r1 = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        let r2 = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        XCTAssertNotEqual(r1.decisionId, r2.decisionId)
    }

    // MARK: - bandit restore at init

    func test_initRestoresBanditStatsFromStorage() {
        let store = InMemoryStore()
        let seedBandit = BanditPolicy()
        seedBandit.update(segmentKey: "SHOP_OPEN_CASUAL_STEADY", armId: "arm1", reward01: 1.0)
        store.setString(seedBandit.serialize(), forKey: "brain_bandit_stats")

        let engine = DecisionEngine(guardrails: GuardrailEngine(), store: store)
        let count = engine.observationCount(decisionPoint: .shopOpen, segment: .casualSteady, armId: "arm1")
        XCTAssertGreaterThan(count, 0.0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `swift test --filter DecisionEngineTests`
Expected: FAIL — `DecisionEngine` not found.

- [ ] **Step 3: Write the implementation**

```swift
import Foundation

/// Facade for DECIDE/FEEDBACK — the ONLY entry point code outside this package should call;
/// never call `BanditPolicy`/`GuardrailEngine`/event emission directly from outside.
///
/// Ties together every primitive from Phase 2 (`BeliefState`, `RewardModel`,
/// `LikelihoodEstimator`, `BanditPolicy`) with this phase's orchestration types
/// (`GuardrailEngine`, `LagrangianBudget`, `PersonalizationScenario`, `BrainStorage`) into one
/// `decide`/`feedback`/`observe` loop. Not yet wired into `AdsBrain`'s public decision-point
/// methods — that's Phase 3.5, mirroring the Phase 1 -> Phase 1.5 split (see the Phase 3 design
/// spec §1).
public final class DecisionEngine: @unchecked Sendable {
    public static let shared = DecisionEngine()

    private let policyVersion: String
    private let bandit: BanditPolicy
    private let guardrails: GuardrailEngine
    private let storage: BrainStorage
    private let eventSink: EventSink?

    private let lock = NSLock()
    private var beliefStates: [String: BeliefState] = [:]
    private var budgets: [String: LagrangianBudget] = [:]

    /// Estimated decision points per session, used to convert the session budget cap into a
    /// per-decision allowance (B/T in the BwK formula). A design value, not yet measured.
    public var estimatedDecisionsPerSession: Int = 30

    private let runId: String = {
        let millis = Int64(Date().timeIntervalSince1970 * 1000)
        let suffix = UUID().uuidString.prefix(6).lowercased()
        return String(millis, radix: 36) + "-" + suffix
    }()
    private let counterLock = NSLock()
    private var counter: Int64 = 0

    /// - Parameter eventSink: where `brain_decision`/`brain_feedback` events go. `nil` (the
    ///   default) means `decide`/`feedback` still work correctly, just without emitting an event
    ///   — the same "usable before configured" behavior `AdsBrain` itself has in Phase 1. Phase
    ///   3.5's wiring passes `AdsBrain`'s configured sink in.
    public init(
        policyVersion: String = "brain_v0_skeleton",
        bandit: BanditPolicy = BanditPolicy(),
        guardrails: GuardrailEngine = GuardrailEngine(),
        store: BrainKeyValueStore = UserDefaultsBrainStore(),
        eventSink: EventSink? = nil
    ) {
        self.policyVersion = policyVersion
        self.bandit = bandit
        self.guardrails = guardrails
        self.storage = BrainStorage(store: store)
        self.eventSink = eventSink
        // Restore bandit stats learned in previous sessions. Without this, the bandit forgets
        // everything on every app restart while belief is saved — that phase mismatch makes
        // results non-reproducible and hard to diagnose.
        self.bandit.restore(storage.loadBanditStats())
    }

    /// Player's resource budget. Each player has their own — a shared global lambda would let one
    /// player's behavior affect another's (the exact shared-state bug this port avoids elsewhere).
    public func budgetFor(_ playerId: String) -> LagrangianBudget {
        lock.lock()
        defer { lock.unlock() }
        if let existing = budgets[playerId] { return existing }
        let created = LagrangianBudget()
        budgets[playerId] = created
        return created
    }

    /// Belief, restored from storage if not already in memory. Without this restoration step,
    /// every player resets to a uniform prior on every app launch — permanently "new," and
    /// personalization never accumulates.
    public func beliefFor(_ playerId: String) -> BeliefState {
        lock.lock()
        defer { lock.unlock() }
        if let existing = beliefStates[playerId] { return existing }
        let restored = BeliefState.deserialize(playerId: playerId, raw: storage.loadBelief(playerId: playerId))
        beliefStates[playerId] = restored
        return restored
    }

    /// Feeds a new observation into belief — this is where a segment actually moves. Call
    /// whenever player state changes meaningfully (level end, rewarded seen/declined, purchase).
    ///
    /// - Returns: `true` if belief was updated; `false` if there wasn't enough data to conclude
    ///   anything, or consent doesn't allow it.
    @discardableResult
    public func observe(_ state: PlayerState) -> Bool {
        // GDPR treats personalization as profiling, so ACCUMULATING a behavioral profile needs
        // consent. Blocked here, not at the point of use: data that isn't allowed to be
        // collected must not be written down at all, not written-then-avoided-on-read.
        guard ConsentState.shared.allowsProfiling() else { return false }
        let likelihood = LikelihoodEstimator.estimate(state)
        guard !likelihood.isEmpty else { return false }
        let belief = beliefFor(state.playerId)
        belief.update(likelihood: likelihood)
        storage.saveBelief(playerId: state.playerId, serialized: belief.serialize())
        return true
    }

    /// The personalization scenario currently applying to this player.
    public func scenarioFor(_ playerId: String) -> PersonalizationScenario {
        PersonalizationScenario.resolve(beliefFor(playerId))
    }

    /// Accumulated bandit observations for one (segment, arm). Exposed for diagnostics/display —
    /// deliberately narrower than exposing `BanditPolicy` itself, which would allow writes from
    /// outside.
    public func observationCount(decisionPoint: DecisionPoint, segment: LatentPlayerType, armId: String) -> Double {
        bandit.observationCount(segmentKey: "\(decisionPoint.rawValue)_\(segment.rawValue)", armId: armId)
    }

    /// Effective interstitial cap in force for this player, after merging the guardrail and
    /// scenario caps.
    public func effectiveInterstitialCap(_ playerId: String) -> Int {
        guardrails.effectiveInterstitialCap(scenarioFor(playerId))
    }

    /// Clears everything learned about a player: both in-memory and storage.
    ///
    /// Both MUST be cleared. Clearing storage alone is a subtle bug seen often in testing: the
    /// engine is a long-lived instance, so the old belief is still sitting in `beliefStates`, and
    /// whoever reset thinks the state is clean while the system is still using stale data.
    public func resetPlayer(_ playerId: String) {
        lock.lock()
        beliefStates.removeValue(forKey: playerId)
        budgets.removeValue(forKey: playerId)
        lock.unlock()
        storage.clearBelief(playerId: playerId)
    }

    /// Starts a new session: shadow price returns to its starting level, session budget resets.
    public func startNewSession(_ playerId: String) {
        budgetFor(playerId).reset()
    }

    /// Maps belief posterior to segment weights for the bandit (each latent type is its own
    /// segment; the weight is exactly b_t(k)).
    private static func segmentWeights(decisionPoint: DecisionPoint, belief: BeliefState) -> [String: Double] {
        var result: [String: Double] = [:]
        let posterior = belief.posterior
        for type in LatentPlayerType.allCases {
            let weight = posterior[type] ?? 0.0
            if weight > 0.0 {
                result["\(decisionPoint.rawValue)_\(type.rawValue)"] = weight
            }
        }
        return result
    }

    /// DECIDE: takes context + valid candidates, returns an action that has passed guardrails and
    /// bandit ranking.
    ///
    /// `candidates` must be supplied by the CALLER per business rules (business constraints
    /// FIRST, ML ranking SECOND) — `DecisionEngine` never synthesizes SKUs/offers/creatives
    /// itself. `candidates` MUST always contain at least one `.noAction` action.
    public func decide(
        request: DecisionRequest,
        state: PlayerState,
        candidates: [ActionCandidate],
        adsShownThisSession: Int = 0,
        minutesSinceLastInterstitial: Int = Int.max
    ) async -> DecisionResponse {
        precondition(!candidates.isEmpty, "candidates must not be empty")
        precondition(candidates.contains { $0.mode == .noAction }, "candidates must always contain .noAction")

        // Control group: NOT personalized, gets the neutral scenario. Guardrails still apply
        // normally — holdout measures the value of personalization, not an excuse to drop safety.
        // A control group harmed by missing guardrails would bias the comparison artificially in
        // the brain's favor.
        let isHoldout = HoldoutManager.isHoldout(request.playerId)

        // Scenario must be resolved BEFORE guardrails, since it tightens the resource cap.
        let scenario: PersonalizationScenario = isHoldout ? .balancedHybrid : scenarioFor(request.playerId)

        var guardrailsApplied: [String] = []
        let safeCandidates = candidates.filter { candidate in
            let violation = guardrails.evaluate(
                candidate: candidate,
                state: state,
                adsShownThisSession: adsShownThisSession,
                minutesSinceLastInterstitial: minutesSinceLastInterstitial,
                scenario: scenario,
                decisionPoint: request.decisionPoint
            )
            if let violation {
                guardrailsApplied.append(violation.code)
                return false
            }
            return true
        }

        // Guardrails may remove every real candidate; .noAction always remains as a safe fallback.
        let noActionFallback = candidates.first(where: { $0.mode == .noAction })
        let finalCandidates = safeCandidates.isEmpty
            ? [noActionFallback].compactMap { $0 }
            : safeCandidates

        let belief = beliefFor(request.playerId)
        let armIds = finalCandidates.map(\.actionId)

        let chosen: ActionCandidate
        let probability: Double
        if isHoldout {
            // Control: deterministic behavior, as it was before the brain existed — no bandit, no
            // exploration. Propensity = 1.0 since no random choice was made.
            chosen = finalCandidates.first(where: { $0.isBaseline })
                ?? finalCandidates.first(where: { $0.mode == .noAction })
                ?? finalCandidates[0]
            probability = 1.0
        } else {
            let segmentWeights = Self.segmentWeights(decisionPoint: request.decisionPoint, belief: belief)
            // Lagrangian shadow price: shapes the rhythm INSIDE the budget smoothly, instead of
            // letting the bandit choose freely and then hit a hard wall and go silent.
            let budget = budgetFor(request.playerId)
            var penalties: [String: Double] = [:]
            for candidate in finalCandidates {
                penalties[candidate.actionId] = budget.penaltyFor(candidate.mode)
            }
            let (armId, p) = bandit.selectArm(
                segmentWeights: segmentWeights,
                candidateArmIds: armIds,
                penalties: penalties
            )
            chosen = finalCandidates.first(where: { $0.actionId == armId }) ?? finalCandidates[0]
            probability = p

            // Update the dual variable after choosing: spending faster than budget raises lambda.
            let cap = guardrails.effectiveInterstitialCap(scenario)
            budget.observe(
                chosenMode: chosen.mode,
                budgetPerDecision: Double(cap) / Double(max(estimatedDecisionsPerSession, 1))
            )
        }

        let response = DecisionResponse(
            decisionId: nextDecisionId(playerId: request.playerId),
            chosenAction: chosen,
            chosenActionProbability: probability,
            policyVersion: policyVersion,
            guardrailsApplied: guardrailsApplied,
            eligibleActionIds: armIds,
            segment: belief.mostLikelyType(),
            segmentConfidence: belief.confidence(),
            scenario: scenario,
            isHoldout: isHoldout
        )
        await logDecision(request: request, response: response)
        return response
    }

    /// FEEDBACK: updates the bandit posterior, WEIGHTED by belief rather than dumped entirely
    /// into the single most-likely segment — evidence isn't lost when a player's belief shifts
    /// to a different type mid-stream.
    ///
    /// `outcomeReward01` must be computed by the caller. The full reward formula needs an
    /// LTV/churn model this SDK doesn't have yet.
    public func feedback(
        request: DecisionRequest,
        response: DecisionResponse,
        outcomeReward01: Double
    ) async {
        // The control group's outcome is NEVER fed into the bandit. Their decision came from a
        // deterministic rule, not the bandit — feeding it in would bias the posterior with data
        // that's supposed to stay independent for comparison. Still logged in full for
        // incrementality analysis on the backend.
        if !response.isHoldout {
            let belief = beliefFor(request.playerId)
            let segmentWeights = Self.segmentWeights(decisionPoint: request.decisionPoint, belief: belief)
            for (segmentKey, weight) in segmentWeights {
                bandit.update(
                    segmentKey: segmentKey,
                    armId: response.chosenAction.actionId,
                    reward01: outcomeReward01,
                    weight: weight
                )
            }
            storage.saveBanditStats(bandit.serialize())
        }
        await logFeedback(playerId: request.playerId, decisionId: response.decisionId, outcomeReward01: outcomeReward01)
    }

    // MARK: - Event emission

    private func logDecision(request: DecisionRequest, response: DecisionResponse) async {
        guard let eventSink, ConsentState.shared.allowsDecisionLogging() else { return }
        await eventSink.send(SdkEvent(
            eventName: "brain_decision",
            eventData: [
                "decision_id": .string(response.decisionId),
                "decision_point": .string(request.decisionPoint.rawValue),
                "chosen_action_id": .string(response.chosenAction.actionId),
                "chosen_action_mode": .string(response.chosenAction.mode.rawValue),
                "chosen_action_probability": .double(response.chosenActionProbability),
                // Original order preserved (comma-joined, not sorted) — a set that changes order
                // between calls is still the same set, but order helps catch client-side bugs
                // during backend reconciliation.
                "eligible_actions": .string(response.eligibleActionIds.joined(separator: ",")),
                "guardrails_applied": .string(response.guardrailsApplied.joined(separator: ",")),
                "segment": .string(response.segment?.rawValue ?? "UNKNOWN"),
                "segment_confidence": .double(response.segmentConfidence),
                "scenario": .string(response.scenario?.rawValue ?? "UNKNOWN"),
                // Backend's normalize.py parses this with its integer helper, not a JSON bool.
                "is_holdout": .int(response.isHoldout ? 1 : 0),
                "policy_version": .string(response.policyVersion)
            ],
            userProperty: UserProperty(userId: request.playerId)
        ))
    }

    private func logFeedback(playerId: String, decisionId: String, outcomeReward01: Double) async {
        guard let eventSink, ConsentState.shared.allowsDecisionLogging() else { return }
        await eventSink.send(SdkEvent(
            eventName: "brain_feedback",
            eventData: [
                "decision_id": .string(decisionId),
                "outcome_event": .string("outcome_reward"),
                "outcome_timestamp_ms": .int(Int(Date().timeIntervalSince1970 * 1000)),
                "reward_value": .double(outcomeReward01)
            ],
            userProperty: UserProperty(userId: playerId)
        ))
    }

    // MARK: - Decision id

    /// Generates an ABSOLUTELY UNIQUE `decisionId`, with no dependency on storage: `runId`
    /// distinguishes app runs, a lock-protected counter guarantees uniqueness within the process
    /// even when called from multiple threads.
    private func nextDecisionId(playerId: String) -> String {
        counterLock.lock()
        counter += 1
        let n = counter
        counterLock.unlock()
        return "d_\(playerId)_\(runId)_\(n)"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `swift test --filter DecisionEngineTests`
Expected: PASS (16/16). Then run the FULL suite: `swift test`. Expected: all tests across every
target pass, 0 warnings (also try `swift build -Xswiftc -strict-concurrency=complete` to match the
zero-warnings bar Phase 2 finished at).

- [ ] **Step 5: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/DecisionEngine.swift Tests/MonetizationBrainCoreTests/DecisionEngineTests.swift
git commit -m "feat: port DecisionEngine — ties GuardrailEngine/LagrangianBudget/PersonalizationScenario/BrainStorage/BanditPolicy/BeliefState into one decide/feedback/observe loop"
```

---

### Task 7: README update

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the "Current phase" / "Status" sections**

Read the current `README.md` first (its exact structure was set by Phase 1/1.5/2's own Task-N
README edits — match that structure, don't restructure it). Update it to state:

- Phase 3 "Decision Engine" is complete and merged: real `GuardrailEngine` rules,
  `ClickFraudGuard`, `LagrangianBudget`, `PersonalizationScenario`, `BrainStorage`,
  `DecisionEngine` (`decide`/`feedback`/`observe`).
- Link the design spec: `docs/superpowers/specs/2026-09-03-decision-engine-design.md`.
- Explicitly list what's still NOT wired: `AdsBrain`'s public decision-point methods
  (`chooseCrossModal`/`chooseAssist`/`shouldOfferIap`) still return their Phase 1 baseline —
  `DecisionEngine` exists and is fully tested, but nothing calls it from the public facade yet.
  That's Phase 3.5.
- List the explicitly deferred subsystems from the design spec §1 (`BrainPolicy`/remote policy
  artifacts, `BrainInspector`) so a future reader doesn't rediscover the gap from scratch.
- Update the test count in whatever summary line the README already uses (check the actual final
  count from Task 6's full-suite run — do not guess a number).

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: update README for Phase 3 Decision Engine"
```
