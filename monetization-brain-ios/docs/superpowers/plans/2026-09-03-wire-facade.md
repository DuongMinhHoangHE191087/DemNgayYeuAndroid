# Phase 1.5: Wire Up the Facade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `AdsBrain` actually use the `Configuration` it stores — real event emission through `eventSink`, real `HoldoutManager`-backed `isHoldout` on `chooseCrossModal`/`chooseAssist`, and the `playerId` on `Configuration` that both require.

**Architecture:** Add `playerId: String` to `AdsBrain.Configuration`. Convert `configure(_:)`, `chooseCrossModal(...)`, and `chooseAssist(...)` to `async` so event emission can be awaited deterministically in tests instead of fire-and-forget. `configure(_:)` emits one `sdk_configured` event; `chooseCrossModal`/`chooseAssist` compute real holdout membership via `HoldoutManager.isHoldout(playerId)` and emit one `sdk_baseline_decision` event each, when configured. `shouldOfferIap(...)` and `guardrailEngine` consultation are explicitly untouched — see the design spec §4 for why.

**Tech Stack:** Same as Phase 1 — Swift Package Manager, Swift tools 5.10, iOS 15+/macOS 12+, XCTest.

**Source spec:** `docs/superpowers/specs/2026-09-03-wire-facade-design.md`.

> **Historical note:** the `eventData` field names shown below as `decisionPoint`/`chosenAction`/`isHoldout`
> (camelCase, no `userProperty`) are what was originally planned. Commit `3f7f83b` (found during
> this phase's own final whole-branch review) corrected them to the shipped shape:
> `decision_point`/`chosen_action_mode`/`is_holdout` (snake_case) plus a real
> `userProperty.user_id`. This plan is left as an execution record, not re-edited — see the spec
> (§3, §6) for the corrected, current contract. Do not copy the blocks below into a future plan.

**Local repo:** `/Users/dungdm/Documents/SDK/monetization-brain-ios`, branch `phase1.5/wire-facade` (stacked on `phase1/core-foundation`, which is PR #1 and not yet merged to `main`).

---

## Task 1: `Configuration.playerId` + async `configure()` + `sdk_configured` event

**Files:**
- Modify: `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`
- Modify: `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`

- [ ] **Step 1: Write the failing test**

Replace `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift` with exactly this (adds `FakeEventSink`, gives `makeConfiguration` a `playerId` parameter, converts the two tests that call `configure(_:)` to `async`, adds `test_configureSendsSdkConfiguredEvent`; `chooseCrossModal`/`chooseAssist` tests are UNCHANGED/still synchronous in this task — they get converted in Tasks 2 and 3):

```swift
import XCTest
@testable import MonetizationBrainCore

final class AdsBrainTests: XCTestCase {
    override func setUp() {
        super.setUp()
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
    }

    override func tearDown() {
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        super.tearDown()
    }

    private func makeConfiguration(playerId: String = "user_1") -> AdsBrain.Configuration {
        AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: EventQueue(
                directory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),
                sender: NoOpEventSender()
            ),
            playerId: playerId
        )
    }

    func test_enabledDefaultsToFalse() {
        XCTAssertFalse(AdsBrain.shared.enabled)
    }

    func test_isConfiguredFalseBeforeConfigure() {
        XCTAssertFalse(AdsBrain.shared.isConfigured())
    }

    func test_isConfiguredTrueAfterConfigure() async {
        await AdsBrain.shared.configure(makeConfiguration())
        XCTAssertTrue(AdsBrain.shared.isConfigured())
    }

    func test_configureSendsSdkConfiguredEvent() async {
        let sink = FakeEventSink()
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: sink,
            playerId: "user_1"
        )

        await AdsBrain.shared.configure(configuration)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"])
    }

    /// In Phase 1 there is no guardrail/bandit to justify withholding an IAP offer, so the gate
    /// returns the same value the Android SDK returns when its brain is inactive: `true`.
    func test_shouldOfferIapReturnsTrueWhenBrainInactive() {
        XCTAssertTrue(AdsBrain.shared.shouldOfferIap())
        XCTAssertTrue(AdsBrain.shared.shouldOfferIap(decisionPoint: .currencyLow))
    }

    func test_chooseCrossModalAlwaysReturnsBaselineNotHoldout() {
        let decision = AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)
        XCTAssertEqual(decision.action, .interstitial)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseAssistAlwaysReturnsBaselineNotHoldout() {
        let decision = AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)
        XCTAssertEqual(decision.action, .freeAction)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_selfCheckReportListsIssuesWhenUnconfiguredAndDisabled() {
        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertFalse(report.isConfigured)
        XCTAssertFalse(report.isEnabled)
        XCTAssertEqual(report.consentTier, .unknown)
        XCTAssertTrue(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertTrue(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }

    func test_selfCheckReportHasNoConfigurationIssueOnceConfiguredAndEnabled() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        ConsentState.shared.setTier(.granted)

        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertTrue(report.isConfigured)
        XCTAssertTrue(report.isEnabled)
        XCTAssertFalse(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertFalse(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }
}

private struct NoOpEventSender: EventSender {
    func send(_ event: SdkEvent) async throws {}
}

private actor FakeEventSink: EventSink {
    private(set) var sentEvents: [SdkEvent] = []

    func send(_ event: SdkEvent) async {
        sentEvents.append(event)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter AdsBrainTests`
Expected: FAIL to compile — `Configuration` has no `playerId` parameter yet, and `configure(_:)` is not `async` yet (so `await AdsBrain.shared.configure(...)` is invalid).

- [ ] **Step 3: Modify `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`**

Find:
```swift
    public struct Configuration: Sendable {
        public let remoteConfig: RemoteConfigProvider
        public let eventSink: EventSink
        public let guardrailEngine: GuardrailEngine

        public init(
            remoteConfig: RemoteConfigProvider,
            eventSink: EventSink,
            guardrailEngine: GuardrailEngine = AllowAllGuardrailEngine()
        ) {
            self.remoteConfig = remoteConfig
            self.eventSink = eventSink
            self.guardrailEngine = guardrailEngine
        }
    }
```
Replace with:
```swift
    public struct Configuration: Sendable {
        public let remoteConfig: RemoteConfigProvider
        public let eventSink: EventSink
        public let playerId: String
        public let guardrailEngine: GuardrailEngine

        public init(
            remoteConfig: RemoteConfigProvider,
            eventSink: EventSink,
            playerId: String,
            guardrailEngine: GuardrailEngine = AllowAllGuardrailEngine()
        ) {
            self.remoteConfig = remoteConfig
            self.eventSink = eventSink
            self.playerId = playerId
            self.guardrailEngine = guardrailEngine
        }
    }
```

Find:
```swift
    /// Wires the facade up. Does not itself turn decisioning on — `enabled` stays `false` until
    /// the host app opts in, matching the Android SDK's "off by default" rule: brain features
    /// never turn themselves on.
    public func configure(_ configuration: Configuration) {
        lock.lock()
        self.configuration = configuration
        lock.unlock()
    }
```
Replace with:
```swift
    /// Wires the facade up. Does not itself turn decisioning on — `enabled` stays `false` until
    /// the host app opts in, matching the Android SDK's "off by default" rule: brain features
    /// never turn themselves on.
    ///
    /// `async` because it emits one `sdk_configured` lifecycle event through the configured
    /// `eventSink` and awaits it, rather than firing it off in an untracked `Task` — an awaited
    /// call is what lets a test assert on the event deterministically instead of polling for a
    /// background task to finish.
    public func configure(_ configuration: Configuration) async {
        lock.lock()
        self.configuration = configuration
        lock.unlock()

        await configuration.eventSink.send(
            SdkEvent(
                eventName: "sdk_configured",
                eventData: ["sdk_version": .string(MonetizationBrainSDK.version)]
            )
        )
    }

    /// Thread-safe read of the current configuration, used by decision-point methods below.
    private func currentConfiguration() -> Configuration? {
        lock.lock()
        defer { lock.unlock() }
        return configuration
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter AdsBrainTests`
Expected: PASS — all tests in this file green (the `chooseCrossModal`/`chooseAssist` tests are unchanged from Phase 1 and still pass as-is, since those methods are untouched in this task)

- [ ] **Step 5: Run the full suite to confirm no regressions elsewhere**

Run: `swift test`
Expected: PASS — all tests across every test file green

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/AdsBrain.swift Tests/MonetizationBrainCoreTests/AdsBrainTests.swift
git commit -m "feat: add playerId to Configuration, make configure() async, emit sdk_configured"
```

---

## Task 2: Wire `chooseCrossModal` — real holdout + `sdk_baseline_decision` event

**Files:**
- Modify: `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`
- Modify: `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`

- [ ] **Step 1: Write the failing test**

Replace `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift` with exactly this (everything from Task 1 unchanged except: the `chooseCrossModal` test is converted to `async`/renamed, and three new tests are added for it; `chooseAssist`'s test stays synchronous/unchanged — that's Task 3's job):

```swift
import XCTest
@testable import MonetizationBrainCore

final class AdsBrainTests: XCTestCase {
    override func setUp() {
        super.setUp()
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
    }

    override func tearDown() {
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
        super.tearDown()
    }

    private func makeConfiguration(playerId: String = "user_1") -> AdsBrain.Configuration {
        AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: EventQueue(
                directory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),
                sender: NoOpEventSender()
            ),
            playerId: playerId
        )
    }

    func test_enabledDefaultsToFalse() {
        XCTAssertFalse(AdsBrain.shared.enabled)
    }

    func test_isConfiguredFalseBeforeConfigure() {
        XCTAssertFalse(AdsBrain.shared.isConfigured())
    }

    func test_isConfiguredTrueAfterConfigure() async {
        await AdsBrain.shared.configure(makeConfiguration())
        XCTAssertTrue(AdsBrain.shared.isConfigured())
    }

    func test_configureSendsSdkConfiguredEvent() async {
        let sink = FakeEventSink()
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: sink,
            playerId: "user_1"
        )

        await AdsBrain.shared.configure(configuration)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"])
    }

    /// In Phase 1 there is no guardrail/bandit to justify withholding an IAP offer, so the gate
    /// returns the same value the Android SDK returns when its brain is inactive: `true`.
    func test_shouldOfferIapReturnsTrueWhenBrainInactive() {
        XCTAssertTrue(AdsBrain.shared.shouldOfferIap())
        XCTAssertTrue(AdsBrain.shared.shouldOfferIap(decisionPoint: .currencyLow))
    }

    func test_chooseCrossModalReturnsBaselineNotHoldoutWhenUnconfigured() async {
        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)
        XCTAssertEqual(decision.action, .interstitial)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseCrossModalReportsRealHoldoutMembershipWhenConfigured() async {
        // Known vectors (HoldoutManagerParityTests): bucketWith("brain_holdout_v1", "user_1") = 6924.
        // At fraction 0.70 (threshold 7000), user_1 is in holdout: 6924 < 7000.
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_1"))

        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)

        XCTAssertEqual(decision.action, .interstitial)
        XCTAssertTrue(decision.isHoldout)
    }

    func test_chooseCrossModalReportsNotHoldoutForPlayerOutsideBucket() async {
        // Known vector: bucketWith("brain_holdout_v1", "user_2") = 9781, not < 7000.
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_2"))

        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)

        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseCrossModalSendsSdkBaselineDecisionEventWhenConfigured() async {
        let sink = FakeEventSink()
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: sink,
            playerId: "user_1"
        )
        await AdsBrain.shared.configure(configuration)

        _ = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.count, 2) // sdk_configured, then sdk_baseline_decision
        XCTAssertEqual(sent.last?.eventName, "sdk_baseline_decision")
        XCTAssertEqual(sent.last?.eventData["decisionPoint"], .string("LEVEL_FAIL"))
        XCTAssertEqual(sent.last?.eventData["chosenAction"], .string("INTERSTITIAL"))
    }

    func test_chooseAssistAlwaysReturnsBaselineNotHoldout() {
        let decision = AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)
        XCTAssertEqual(decision.action, .freeAction)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_selfCheckReportListsIssuesWhenUnconfiguredAndDisabled() {
        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertFalse(report.isConfigured)
        XCTAssertFalse(report.isEnabled)
        XCTAssertEqual(report.consentTier, .unknown)
        XCTAssertTrue(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertTrue(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }

    func test_selfCheckReportHasNoConfigurationIssueOnceConfiguredAndEnabled() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        ConsentState.shared.setTier(.granted)

        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertTrue(report.isConfigured)
        XCTAssertTrue(report.isEnabled)
        XCTAssertFalse(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertFalse(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }
}

private struct NoOpEventSender: EventSender {
    func send(_ event: SdkEvent) async throws {}
}

private actor FakeEventSink: EventSink {
    private(set) var sentEvents: [SdkEvent] = []

    func send(_ event: SdkEvent) async {
        sentEvents.append(event)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter AdsBrainTests`
Expected: FAIL to compile — `chooseCrossModal` is not `async` yet, so `await AdsBrain.shared.chooseCrossModal(...)` is invalid.

- [ ] **Step 3: Modify `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`**

Find:
```swift
    /// Always returns `baseline` with `isHoldout` always `false` in Phase 1 — no decision is
    /// actually being made yet, so nothing is being held out from.
    public func chooseCrossModal(
        decisionPoint: DecisionPoint,
        allowRewarded: Bool = true,
        allowIapOffer: Bool = true,
        baseline: ActionMode = .noAction
    ) -> CrossModalDecision {
        CrossModalDecision(action: baseline, isHoldout: false)
    }
```
Replace with:
```swift
    /// `isHoldout` reflects a REAL `HoldoutManager` bucket lookup for the configured
    /// `playerId` when `AdsBrain` has been configured — no longer hardcoded `false`. The
    /// returned `action` is still always `baseline`: this only wires observability and holdout
    /// bookkeeping, not decisioning. `allowRewarded`/`allowIapOffer` remain unused — there is
    /// still no bandit to apply them to. When unconfigured, behavior is unchanged from Phase 1:
    /// baseline, `isHoldout: false`, no event (there is no `playerId` to attribute one to).
    ///
    /// `async` — see `configure(_:)`'s doc comment for why.
    public func chooseCrossModal(
        decisionPoint: DecisionPoint,
        allowRewarded: Bool = true,
        allowIapOffer: Bool = true,
        baseline: ActionMode = .noAction
    ) async -> CrossModalDecision {
        guard let configuration = currentConfiguration() else {
            return CrossModalDecision(action: baseline, isHoldout: false)
        }

        let isHoldout = HoldoutManager.isHoldout(configuration.playerId)
        await configuration.eventSink.send(
            SdkEvent(
                eventName: "sdk_baseline_decision",
                eventData: [
                    "decisionPoint": .string(decisionPoint.rawValue),
                    "chosenAction": .string(baseline.rawValue),
                    "isHoldout": .bool(isHoldout)
                ]
            )
        )
        return CrossModalDecision(action: baseline, isHoldout: isHoldout)
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter AdsBrainTests`
Expected: PASS — all tests in this file green

- [ ] **Step 5: Run the full suite to confirm no regressions elsewhere**

Run: `swift test`
Expected: PASS — all tests across every test file green

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/AdsBrain.swift Tests/MonetizationBrainCoreTests/AdsBrainTests.swift
git commit -m "feat: wire chooseCrossModal to real HoldoutManager membership and sdk_baseline_decision event"
```

---

## Task 3: Wire `chooseAssist` — real holdout + `sdk_baseline_decision` event

**Files:**
- Modify: `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`
- Modify: `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`

- [ ] **Step 1: Write the failing test**

Replace `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift` with exactly this (everything from Task 2 unchanged except: the `chooseAssist` test is converted to `async`/renamed, and three new tests are added for it — mirroring what Task 2 did for `chooseCrossModal`):

```swift
import XCTest
@testable import MonetizationBrainCore

final class AdsBrainTests: XCTestCase {
    override func setUp() {
        super.setUp()
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
    }

    override func tearDown() {
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
        super.tearDown()
    }

    private func makeConfiguration(playerId: String = "user_1") -> AdsBrain.Configuration {
        AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: EventQueue(
                directory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),
                sender: NoOpEventSender()
            ),
            playerId: playerId
        )
    }

    func test_enabledDefaultsToFalse() {
        XCTAssertFalse(AdsBrain.shared.enabled)
    }

    func test_isConfiguredFalseBeforeConfigure() {
        XCTAssertFalse(AdsBrain.shared.isConfigured())
    }

    func test_isConfiguredTrueAfterConfigure() async {
        await AdsBrain.shared.configure(makeConfiguration())
        XCTAssertTrue(AdsBrain.shared.isConfigured())
    }

    func test_configureSendsSdkConfiguredEvent() async {
        let sink = FakeEventSink()
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: sink,
            playerId: "user_1"
        )

        await AdsBrain.shared.configure(configuration)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"])
    }

    /// In Phase 1 there is no guardrail/bandit to justify withholding an IAP offer, so the gate
    /// returns the same value the Android SDK returns when its brain is inactive: `true`.
    func test_shouldOfferIapReturnsTrueWhenBrainInactive() {
        XCTAssertTrue(AdsBrain.shared.shouldOfferIap())
        XCTAssertTrue(AdsBrain.shared.shouldOfferIap(decisionPoint: .currencyLow))
    }

    func test_chooseCrossModalReturnsBaselineNotHoldoutWhenUnconfigured() async {
        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)
        XCTAssertEqual(decision.action, .interstitial)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseCrossModalReportsRealHoldoutMembershipWhenConfigured() async {
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_1"))

        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)

        XCTAssertEqual(decision.action, .interstitial)
        XCTAssertTrue(decision.isHoldout)
    }

    func test_chooseCrossModalReportsNotHoldoutForPlayerOutsideBucket() async {
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_2"))

        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)

        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseCrossModalSendsSdkBaselineDecisionEventWhenConfigured() async {
        let sink = FakeEventSink()
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: sink,
            playerId: "user_1"
        )
        await AdsBrain.shared.configure(configuration)

        _ = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.count, 2)
        XCTAssertEqual(sent.last?.eventName, "sdk_baseline_decision")
        XCTAssertEqual(sent.last?.eventData["decisionPoint"], .string("LEVEL_FAIL"))
        XCTAssertEqual(sent.last?.eventData["chosenAction"], .string("INTERSTITIAL"))
    }

    func test_chooseAssistReturnsBaselineNotHoldoutWhenUnconfigured() async {
        let decision = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)
        XCTAssertEqual(decision.action, .freeAction)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseAssistReportsRealHoldoutMembershipWhenConfigured() async {
        // Known vector: bucketWith("brain_holdout_v1", "user_1") = 6924 < 7000.
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_1"))

        let decision = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)

        XCTAssertEqual(decision.action, .freeAction)
        XCTAssertTrue(decision.isHoldout)
    }

    func test_chooseAssistReportsNotHoldoutForPlayerOutsideBucket() async {
        // Known vector: bucketWith("brain_holdout_v1", "user_2") = 9781, not < 7000.
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_2"))

        let decision = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)

        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseAssistSendsSdkBaselineDecisionEventWhenConfigured() async {
        let sink = FakeEventSink()
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: sink,
            playerId: "user_1"
        )
        await AdsBrain.shared.configure(configuration)

        _ = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.count, 2)
        XCTAssertEqual(sent.last?.eventName, "sdk_baseline_decision")
        XCTAssertEqual(sent.last?.eventData["decisionPoint"], .string("LEVEL_FAIL_STREAK"))
        XCTAssertEqual(sent.last?.eventData["chosenAction"], .string("FREE_ACTION"))
    }

    func test_selfCheckReportListsIssuesWhenUnconfiguredAndDisabled() {
        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertFalse(report.isConfigured)
        XCTAssertFalse(report.isEnabled)
        XCTAssertEqual(report.consentTier, .unknown)
        XCTAssertTrue(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertTrue(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }

    func test_selfCheckReportHasNoConfigurationIssueOnceConfiguredAndEnabled() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        ConsentState.shared.setTier(.granted)

        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertTrue(report.isConfigured)
        XCTAssertTrue(report.isEnabled)
        XCTAssertFalse(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertFalse(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }
}

private struct NoOpEventSender: EventSender {
    func send(_ event: SdkEvent) async throws {}
}

private actor FakeEventSink: EventSink {
    private(set) var sentEvents: [SdkEvent] = []

    func send(_ event: SdkEvent) async {
        sentEvents.append(event)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `swift test --filter AdsBrainTests`
Expected: FAIL to compile — `chooseAssist` is not `async` yet.

- [ ] **Step 3: Modify `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`**

Find:
```swift
    /// Always returns `baseline` with `isHoldout` always `false` in Phase 1.
    public func chooseAssist(
        decisionPoint: DecisionPoint,
        allowFreeGift: Bool = true,
        allowDifficultyTuning: Bool = true,
        baseline: ActionMode = .noAction
    ) -> AssistDecision {
        AssistDecision(action: baseline, isHoldout: false)
    }
```
Replace with:
```swift
    /// `isHoldout` reflects a REAL `HoldoutManager` bucket lookup for the configured
    /// `playerId` when `AdsBrain` has been configured — no longer hardcoded `false`. The
    /// returned `action` is still always `baseline` — see `chooseCrossModal`'s doc comment,
    /// the same reasoning applies here.
    ///
    /// `async` — see `configure(_:)`'s doc comment for why.
    public func chooseAssist(
        decisionPoint: DecisionPoint,
        allowFreeGift: Bool = true,
        allowDifficultyTuning: Bool = true,
        baseline: ActionMode = .noAction
    ) async -> AssistDecision {
        guard let configuration = currentConfiguration() else {
            return AssistDecision(action: baseline, isHoldout: false)
        }

        let isHoldout = HoldoutManager.isHoldout(configuration.playerId)
        await configuration.eventSink.send(
            SdkEvent(
                eventName: "sdk_baseline_decision",
                eventData: [
                    "decisionPoint": .string(decisionPoint.rawValue),
                    "chosenAction": .string(baseline.rawValue),
                    "isHoldout": .bool(isHoldout)
                ]
            )
        )
        return AssistDecision(action: baseline, isHoldout: isHoldout)
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `swift test --filter AdsBrainTests`
Expected: PASS — all tests in this file green

- [ ] **Step 5: Run the full suite to confirm no regressions elsewhere**

Run: `swift test`
Expected: PASS — all tests across every test file green

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/AdsBrain.swift Tests/MonetizationBrainCoreTests/AdsBrainTests.swift
git commit -m "feat: wire chooseAssist to real HoldoutManager membership and sdk_baseline_decision event"
```

---

## Task 4: Final integration pass

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run the entire test suite**

Run: `swift test`
Expected: every test across every test file passes, 0 failures.

- [ ] **Step 2: Update `README.md`'s "Known Phase 1 limitations" section**

Find:
```markdown
- **`AdsBrain` doesn't wire anything to anything yet.** `configure(_:)` stores a
  `Configuration` (`remoteConfig`/`eventSink`/`guardrailEngine`), but no code path reads it —
  no event is ever actually emitted through `eventSink`, `guardrailEngine.allows(...)` is never
  consulted, and `HoldoutManager` isn't called from `AdsBrain` at all, so every returned
  decision's `isHoldout` is hardcoded `false` rather than a real bucket lookup.
```
Replace with:
```markdown
- **`AdsBrain` now wires event emission and holdout membership for real (Phase 1.5).**
  `configure(_:)` emits `sdk_configured`; `chooseCrossModal`/`chooseAssist` emit
  `sdk_baseline_decision` and report a REAL `HoldoutManager` bucket lookup for `isHoldout` — see
  [`docs/superpowers/specs/2026-09-03-wire-facade-design.md`](docs/superpowers/specs/2026-09-03-wire-facade-design.md).
  Two things stay deliberately unwired: `guardrailEngine.allows(...)` is still never consulted
  (guardrails filter a bandit's candidate set, and there is no candidate set until a real bandit
  exists), and no Android-shaped `brain_decision` event is emitted (its
  `chosen_action_probability`/`eligible_action_ids` fields only exist once a real bandit produces
  them — faking them would repeat the exact wire-format mistake the Phase 1 final review caught).
  `shouldOfferIap(...)` stays fully unwired too — it has no `isHoldout` field to make real.
```

Find:
```markdown
- **`AdsBrain.Configuration` has no player-id concept.** Every holdout/rollout/decision API is
  keyed on a player id; the facade currently has nowhere to source one from. Needs to be added
  before Phase 2's decision engine can be wired in, ideally before any app takes a dependency
  on `Configuration`'s current initializer shape.
```
Delete this bullet entirely (resolved — `Configuration.playerId` now exists).

- [ ] **Step 3: Commit and push**

```bash
git add README.md
git commit -m "docs: mark Phase 1.5 wire-facade complete"
git push -u origin phase1.5/wire-facade
```

- [ ] **Step 4: Verify**

Run: `git log --oneline -6 && git status`
Expected: `git status` reports `nothing to commit, working tree clean` and the last 6 commits show this phase's 4 commits on top of `phase1/core-foundation`'s tip.
