# Phase 3.5 "Wire the Decision Engine" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `DecisionEngine` into `AdsBrain`'s public decision-point methods, turning the facade
from a baseline-only stub into Android's real stateful `AdsBrain.kt` equivalent — session state,
lifecycle hooks, automatic feedback resolution, five real decision-point methods.

**Architecture:** `AdsBrain` gains a private, `NSLock`-protected `SessionState` (a `PlayerState`
snapshot plus session counters and a pending-decision holder) mutated only through synchronous
private helpers, and calls the already-shipped `DecisionEngine.decide`/`feedback`/`observe`. See
`docs/superpowers/specs/2026-09-04-wire-decision-engine-design.md` for the full design and every
deviation's rationale — this plan implements that spec section by section.

**Tech Stack:** Swift Package Manager, `MonetizationBrainCore` target, XCTest, iOS 15+/macOS 12+.

---

## Global constraints (apply to every task below)

- **NSLock discipline:** lock only around actual mutable state. **Never call `lock.lock()`/
  `unlock()` directly inside an `async` function body** — route through a synchronous private
  helper (the existing `storeConfiguration`/`currentConfiguration` pair is the template). NSLock
  is non-reentrant — never call a locked helper from inside another locked helper's critical
  section.
- **No force-unwraps (`!`)** in new production code. Use `guard let`/`??`.
- **Line order for the `isActive()` gate matters and must match Android exactly**: every decision-
  point method checks `isActive()` (and, for `chooseCrossModal`/`chooseAssist`, the all-arms-
  disallowed short-circuit) BEFORE calling `resolvePending`. Do not resolve pending state before
  this check — see spec §4's decision-point section for why.
- **Literal `actionId` strings are exact and must match Android byte-for-byte**: `"no_action"`,
  `"interstitial"`, `"rewarded"`, `"iap_offer"`, `"free_assist"`, `"ease_difficulty"`. These flow
  into the bandit's per-arm keys and the decision log.
- **Do not assert an exact chosen action/mode for any non-holdout `decide` call with 2+ real
  candidates.** `BanditPolicy.selectArm` (called via `DecisionEngine.decide`) uses
  `SystemRandomNumberGenerator()` with no injectable seed reachable through `DecisionEngine` — Its
  real-number Thompson-sampling draw is genuinely non-deterministic in that case. Only assert exact
  chosen values when: the path is holdout (deterministic baseline selection), guardrails/allow-flags
  leave exactly one real candidate (the `count == 1` short-circuit in `selectArm` is deterministic),
  or the brain is inactive/unconfigured (no bandit call happens at all). For every other case, read
  `response.chosenAction` back reflectively (e.g. to build the next assertion) rather than asserting
  a fixed literal. This is the same pattern `DecisionEngineTests.swift` already uses in
  `test_decideAndFeedbackRoundTripUpdatesBanditObservationCount`.
- **`AdsBrain.shared.enabled` currently defaults to `false`, and prior to this plan `chooseCrossModal`/
  `chooseAssist` never checked it at all.** Once Task 3/4 add the real `isActive()` gate, every
  EXISTING test that calls those methods after `configure(...)` and expects a real (non-baseline)
  decision must add `AdsBrain.shared.enabled = true` before calling it, or it will silently start
  hitting the (still-correct) inactive-fallback path instead of what it was written to exercise.
  Task 4 is where these existing tests get fixed — do not fix them piecemeal in earlier tasks.
- Every task: run `swift build && swift test` before committing; report the full suite's pass count
  in the task report, not just the new file's tests.

---

### Task 0: `DecisionEngine` — gate `segment`/`segment_confidence` logging on personalization consent

**Files:**
- Modify: `Sources/MonetizationBrainCore/Brain/DecisionEngine.swift` (the `logDecision` method)
- Test: `Tests/MonetizationBrainCoreTests/DecisionEngineTests.swift`

Closes the first of the two Phase-3-final-review gate items the README already flags (design spec
§3.6): `brain_decision`'s `segment`/`segment_confidence` fields are currently emitted from
`response.segment`/`response.segmentConfidence` regardless of personalization consent, even though
`PersonalizationScenario.resolve` already refuses to USE a player's profile once consent is
withdrawn. This task closes the gap at the one place it logs, without touching
`DecisionEngine.decide`'s own computation (that still needs `belief`/`segment` internally for
non-personalization-gated purposes — only the LOGGED value changes).

Independent of every other task in this plan — no dependency on `AdsBrain`.

- [ ] **Step 1: Write the failing test**

Read the current `logDecision` test in `Tests/MonetizationBrainCoreTests/DecisionEngineTests.swift`
(`test_decideEmitsBrainDecisionEventWithRealBackendFieldNames`, around line 215) first — this new
test follows the same construction pattern (a `DecisionEngine` built with a real `BanditPolicy`,
a `GuardrailEngine`, an in-memory `BrainKeyValueStore` fake, and a recording fake `EventSink`).

`ConsentState`'s four tiers (`Sources/MonetizationBrainCore/Consent/ConsentTier.swift`) are
`.unknown`, `.denied`, `.partial`, `.granted`. `allowsPersonalization()` is `tier() != .denied` —
so `.denied` is the ONLY tier where personalization is withheld; `allowsProfiling()` is `tier ==
.unknown || tier == .granted`. `BeliefState.mostLikelyType()` returns a non-optional
`LatentPlayerType` (a deterministic argmax winner even over a perfectly uniform prior — there is no
"no evidence yet" nil case), so `decide()` ALWAYS sets `response.segment` to some concrete real
type, never `nil`, for every call regardless of how little evidence exists — meaning
`response.segment?.rawValue ?? "UNKNOWN"` in `logDecision` never actually falls through to the
`?? "UNKNOWN"` branch today; that branch exists only as defensive coding for a `DecisionResponse`
constructed by hand (e.g. a test fixture), not something `decide()` itself produces. This means a
single fresh-belief `decide()` call under `.granted` vs. `.denied` consent already exercises the
fix correctly — no need to force non-trivial belief evidence with extra `observe()` calls first:

```swift
    func test_brainDecisionOmitsSegmentWhenPersonalizationConsentDenied() async {
        HoldoutManager.holdoutFraction = 0.0
        ConsentState.shared.setTier(.denied)
        let sink = RecordingEventSink()
        let engine = DecisionEngine(
            bandit: BanditPolicy(),
            guardrails: GuardrailEngine(),
            store: BrainStorage(store: InMemoryBrainKeyValueStore()),
            eventSink: sink
        )
        let request = DecisionRequest(playerId: "p1", decisionPoint: .levelFail)
        let candidates = [ActionCandidate(mode: .noAction, actionId: "no_action")]

        _ = await engine.decide(request: request, state: PlayerState(playerId: "p1"), candidates: candidates)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.first?.eventData["segment"], .string("UNKNOWN"))
        XCTAssertEqual(sent.first?.eventData["segment_confidence"], .double(0.0))
    }

    func test_brainDecisionIncludesRealSegmentWhenPersonalizationConsentGranted() async {
        HoldoutManager.holdoutFraction = 0.0
        ConsentState.shared.setTier(.granted)
        let sink = RecordingEventSink()
        let engine = DecisionEngine(
            bandit: BanditPolicy(),
            guardrails: GuardrailEngine(),
            store: BrainStorage(store: InMemoryBrainKeyValueStore()),
            eventSink: sink
        )
        let request = DecisionRequest(playerId: "p1", decisionPoint: .levelFail)
        let candidates = [ActionCandidate(mode: .noAction, actionId: "no_action")]

        let response = await engine.decide(request: request, state: PlayerState(playerId: "p1"), candidates: candidates)

        // Confirms the fix's guard is genuinely conditional, not a hardcoded "UNKNOWN" — the
        // logged value must be `mostLikelyType()`'s real (always-non-nil) argmax, matching what
        // `decide()` itself computed for this response.
        let sent = await sink.sentEvents
        XCTAssertEqual(sent.first?.eventData["segment"], .string(response.segment?.rawValue ?? "UNKNOWN"))
        XCTAssertNotEqual(sent.first?.eventData["segment"], .string("UNKNOWN"))
        XCTAssertEqual(sent.first?.eventData["segment_confidence"], .double(response.segmentConfidence))
    }
```

`RecordingEventSink`/`InMemoryBrainKeyValueStore` — check whether Phase 3's existing test files
already declare equivalent fakes (likely `FakeEventSink`/`InMemoryStore` under different names in
`DecisionEngineTests.swift` or `BrainStorageTests.swift`) and reuse them instead of redeclaring.

- [ ] **Step 2: Run to verify it fails**

Run: `swift test --filter DecisionEngineTests`
Expected: `test_brainDecisionOmitsSegmentWhenPersonalizationConsentDenied` FAILS — today `logDecision`
logs `response.segment`'s real (always-non-nil) value unconditionally, so the emitted `segment`
field is some concrete `LatentPlayerType.rawValue`, not `"UNKNOWN"`, even under denied consent.
`test_brainDecisionIncludesRealSegmentWhenPersonalizationConsentGranted` should already PASS before
Step 3's fix (the granted path is unaffected by this change) — that's expected, not a problem;
it's here as regression coverage for the branch Step 3 must not break.

- [ ] **Step 3: Write the implementation**

In `DecisionEngine.swift`, `logDecision` currently builds `"segment"`/`"segment_confidence"`
directly from `response.segment`/`response.segmentConfidence`. Change those two lines only:

```swift
    private func logDecision(request: DecisionRequest, response: DecisionResponse) async {
        guard let eventSink, ConsentState.shared.allowsDecisionLogging() else { return }
        let allowsPersonalization = ConsentState.shared.allowsPersonalization()
        await eventSink.send(SdkEvent(
            eventName: "brain_decision",
            eventData: [
                "decision_id": .string(response.decisionId),
                "decision_point": .string(request.decisionPoint.rawValue),
                "chosen_action_id": .string(response.chosenAction.actionId),
                "chosen_action_mode": .string(response.chosenAction.mode.rawValue),
                "chosen_action_probability": .double(response.chosenActionProbability),
                "eligible_actions": .string(response.eligibleActionIds.joined(separator: ",")),
                "guardrails_applied": .string(response.guardrailsApplied.joined(separator: ",")),
                "segment": .string(allowsPersonalization ? (response.segment?.rawValue ?? "UNKNOWN") : "UNKNOWN"),
                "segment_confidence": .double(allowsPersonalization ? response.segmentConfidence : 0.0),
                "scenario": .string(response.scenario?.rawValue ?? "UNKNOWN"),
                "is_holdout": .int(response.isHoldout ? 1 : 0),
                "policy_version": .string(response.policyVersion)
            ],
            userProperty: UserProperty(userId: request.playerId)
        ))
    }
```

Note `scenario` is unchanged — `PersonalizationScenario.resolve` already forces `.balancedHybrid`
under withdrawn consent, so `response.scenario` is already the safe value by the time it reaches
here; only `segment`/`segment_confidence` needed the extra guard because they are read straight
from `belief`, which `decide` computes unconditionally (consent-gating that computation itself,
rather than just its logging, would break `DecisionEngine`'s other internal uses of `belief` — see
spec §3.6, this is an isolated one-method fix by design).

- [ ] **Step 4: Run to verify it passes**

Run: `swift build && swift test --filter DecisionEngineTests`
Expected: PASS, including the new tests.

- [ ] **Step 5: Run full suite regression**

Run: `swift test`
Expected: PASS, same count as before this task plus the new test(s) — no other suite affected.

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/DecisionEngine.swift Tests/MonetizationBrainCoreTests/DecisionEngineTests.swift
git commit -m "fix: gate brain_decision's segment/segment_confidence on personalization consent"
```

---

### Task 1: `AdsBrain` — `Configuration.decisionEngine`, `SessionState`, `isActive()`, session lifecycle

**Files:**
- Modify: `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`
- Test: `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`

Establishes the state-management skeleton every later task builds on: `Configuration.decisionEngine`
replaces the dead `guardrailEngine` field, `AdsBrain` gains a lock-protected `SessionState`, the
`isActive()`/`rolloutFraction` gate, and the `resolvePending`/`onSessionStart`/`onSessionEnd`
lifecycle. No decision-point method changes behavior yet in this task — `shouldOfferIap`/
`chooseCrossModal`/`chooseAssist` stay exactly as Phase 1.5 left them (still stubs); that migration
is Tasks 3–4. This task is fully testable in isolation via `onSessionStart`/`onSessionEnd`'s
observable effects (session field resets) and by injecting a `DecisionEngine` and asserting
`feedback` fires correctly through the new `resolvePending` machinery once a pending decision is
manually queued (a private-API seam is not available from the test target, so this task's tests
drive `resolvePending` indirectly through `onSessionEnd` after Task 1's own `SessionState` accepts
a pending decision via a small internal test hook — see Step 1 below for the exact approach).

- [ ] **Step 1: Write the failing tests**

Append to `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift` (keep the existing
`makeConfiguration` helper, but it must now be updated to compile against the new `Configuration`
shape — see Step 1a):

```swift
    // MARK: - Task 1: session lifecycle

    func test_onSessionStartResetsSessionScopedFields() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true

        await AdsBrain.shared.onSessionStart()
        let state = AdsBrain.shared.currentState()

        XCTAssertNotNil(state)
        XCTAssertEqual(state?.playerId, "user_1")
        XCTAssertEqual(state?.sessionsLifetime, 1)
        XCTAssertEqual(state?.daysSinceLastSession, 0)
        XCTAssertEqual(state?.purchasesThisSession, 0)
        XCTAssertEqual(state?.iapOffersThisSession, 0)
        XCTAssertEqual(state?.minutesSinceLastIapOffer, -1)
        XCTAssertEqual(state?.assistsThisSession, 0)
        XCTAssertEqual(state?.interstitialOpportunitiesThisSession, 0)
        XCTAssertEqual(AdsBrain.shared.adsShownThisSession(), 0)
    }

    func test_onSessionStartTwiceIncrementsSessionsLifetimeWithoutResettingLifetimeFields() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true

        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onSessionStart()

        XCTAssertEqual(AdsBrain.shared.currentState()?.sessionsLifetime, 2)
    }

    func test_currentStateIsNilBeforeAnySessionStarted() {
        XCTAssertNil(AdsBrain.shared.currentState())
    }

    func test_onSessionEndIsHarmlessWithNoPendingDecision() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onSessionEnd() // must not crash / hang with nothing pending
    }

    func test_rolloutFractionDefaultsToOne() {
        XCTAssertEqual(AdsBrain.shared.rolloutFraction, 1.0)
    }
```

- [ ] **Step 1a: Update `makeConfiguration` and every existing `Configuration(...)` call site**

`AdsBrain.Configuration`'s `guardrailEngine: GuardrailEngine = GuardrailEngine()` parameter is
being removed and replaced with `decisionEngine: DecisionEngine? = nil` (defaulted internally —
see Step 3). No test currently passes `guardrailEngine:` explicitly (confirmed: it was dead code
since Phase 1), so every existing `Configuration(...)` call site in
`Tests/MonetizationBrainCoreTests/AdsBrainTests.swift` compiles unchanged against the new
initializer — this step is a compile-and-confirm, not an edit, unless `grep -rn "guardrailEngine"
Sources Tests` finds a reference this plan didn't anticipate (if it does, update it and note the
deviation in the report).

- [ ] **Step 2: Run to verify the new tests fail to compile / fail**

Run: `swift test --filter AdsBrainTests`
Expected: build failure — `onSessionStart`/`onSessionEnd`/`currentState`/`adsShownThisSession`/
`rolloutFraction` not found on `AdsBrain`.

- [ ] **Step 3: Write the implementation**

Replace `AdsBrain.Configuration` in `AdsBrain.swift`:

```swift
    public struct Configuration: Sendable {
        public let remoteConfig: RemoteConfigProvider
        public let eventSink: EventSink
        public let playerId: String
        public let decisionEngine: DecisionEngine

        /// `decisionEngine` defaults to a freshly-constructed `DecisionEngine(eventSink: eventSink)`
        /// — NOT a bare `DecisionEngine()`, which would silently carry `eventSink: nil` and mean
        /// `brain_decision`/`brain_feedback` never reach this `Configuration`'s own event pipeline.
        /// Pass a pre-built `DecisionEngine` (e.g. one constructed with a fake `BrainKeyValueStore`
        /// and a fake `EventSink`) for deterministic tests.
        public init(
            remoteConfig: RemoteConfigProvider,
            eventSink: EventSink,
            playerId: String,
            decisionEngine: DecisionEngine? = nil
        ) {
            self.remoteConfig = remoteConfig
            self.eventSink = eventSink
            self.playerId = playerId
            self.decisionEngine = decisionEngine ?? DecisionEngine(eventSink: eventSink)
        }
    }
```

Add the `SessionState` struct and supporting private state, near the top of the class (after
`Configuration`, before `configuration`/`_enabled`):

```swift
    /// One player's in-flight session — the observable `PlayerState` snapshot plus the session
    /// counters and timestamps Android's `AdsBrain` tracks as bare private fields, plus the
    /// decision currently awaiting a downstream signal before its reward can be computed.
    ///
    /// A value type on purpose (unlike Android's in-place-mutated fields): every hook reads the
    /// current snapshot, computes the new value, and writes the WHOLE struct back under `lock` —
    /// no partial in-place mutation can race a `decide()` call reading it mid-update.
    private struct SessionState {
        var playerState: PlayerState
        var adsShownThisSession: Int = 0
        var lastInterstitialAtMs: Int64 = 0 // 0 = never
        var lastIapOfferAtMs: Int64 = 0
        var lastRewardedAtMs: Int64 = 0
        var levelAttempts: Int = 0
        var levelFails: Int = 0
        var rewardedOffered: Int = 0
        var rewardedAccepted: Int = 0
        var pendingRequest: DecisionRequest?
        var pendingResponse: DecisionResponse?
        var pendingActionHadValue: Bool = false
    }

    private var session: SessionState?
    private var _rolloutFraction: Double = 1.0
```

Add lock-protected `rolloutFraction`, right after the existing `enabled` computed property:

```swift
    /// Fraction of the population the brain actually controls, when `enabled == true`.
    /// Deterministic per-`playerId` bucketing via `HoldoutManager.isInRollout` — a player is
    /// always on the same side. Defaults to `1.0`: the safety switch is `enabled`, not this
    /// field — defaulting to `0` would make turning `enabled` on a silent no-op with nothing to
    /// diagnose. See design spec §1 for why the remote-config-driven staged-rollout use case
    /// (raising this gradually via a policy artifact) is deferred, not this field itself.
    public var rolloutFraction: Double {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _rolloutFraction
        }
        set {
            lock.lock()
            _rolloutFraction = newValue
            lock.unlock()
        }
    }

    /// Is the brain actually controlling the currently configured player? Combines both gates in
    /// one place — checking `enabled` alone and forgetting the rollout check (or vice versa) at
    /// a call site is exactly the kind of mistake that would leak an out-of-rollout player's data
    /// into `observe()`, corrupting the treatment/control comparison the rollout exists to make.
    private func isActive() -> Bool {
        guard let configuration = currentConfiguration() else { return false }
        return enabled && HoldoutManager.isInRollout(configuration.playerId, fraction: rolloutFraction)
    }
```

Add the session-management private helpers, plus `currentState()`/`adsShownThisSession()`, near
`currentConfiguration()`:

```swift
    /// Mutates `session` under `lock` and returns whatever `body` returns — `nil` if no session
    /// exists yet (before the first `onSessionStart()`). Every method that reads OR writes
    /// `session` after construction goes through this or `currentSession()`; never `lock.lock()`
    /// directly, and never from inside an `async` function body.
    private func withSession<T>(_ body: (inout SessionState) -> T) -> T? {
        lock.lock()
        defer { lock.unlock() }
        guard var current = session else { return nil }
        let result = body(&current)
        session = current
        return result
    }

    private func currentSession() -> SessionState? {
        lock.lock()
        defer { lock.unlock() }
        return session
    }

    /// Snapshot of the current session's observable state — for a debug screen or startup log,
    /// matching Android's `currentState()`. `nil` before the first `onSessionStart()`.
    public func currentState() -> PlayerState? {
        currentSession()?.playerState
    }

    /// Interstitials shown in the current session. Read-only; the facade owns the counter.
    public func adsShownThisSession() -> Int {
        currentSession()?.adsShownThisSession ?? 0
    }

    /// Starts (or restarts) a session for the currently configured player. Unlike Android, iOS's
    /// `playerId` is fixed for a `Configuration`'s whole lifetime (a required field, not a
    /// self-generated fallback — see Phase 3 spec §1) — a real player switch means a fresh
    /// `configure(_:)` call with a new `Configuration`, not a `playerId` argument here. So there
    /// is no "did the player change" branch to port: every call resets the SAME session-scoped
    /// fields on the ONE configured player's state.
    ///
    /// Resolves any decision left pending from a previous session as "player did not continue" —
    /// a session ending mid-decision (app killed, crashed) is itself the negative signal.
    public func onSessionStart() async {
        await resolvePending(playerContinued: false)
        guard let configuration = currentConfiguration() else { return }
        resetSessionForNewSession(playerId: configuration.playerId)
        configuration.decisionEngine.startNewSession(configuration.playerId)
    }

    /// Plain synchronous helper — see `storeConfiguration`'s doc comment for why this can't be
    /// inlined into `onSessionStart`'s `async` body: `NSLock.lock()`/`unlock()` are
    /// `@available(*, noasync)`, so the lock/unlock pair must live in a synchronous function
    /// `onSessionStart` merely calls.
    private func resetSessionForNewSession(playerId: String) {
        lock.lock()
        var current = session ?? SessionState(playerState: PlayerState(playerId: playerId))
        current.playerState.sessionsLifetime += 1
        current.playerState.daysSinceLastSession = 0
        current.adsShownThisSession = 0
        current.lastInterstitialAtMs = 0
        current.lastIapOfferAtMs = 0
        current.playerState.purchasesThisSession = 0
        current.playerState.iapOffersThisSession = 0
        current.playerState.minutesSinceLastIapOffer = -1
        current.playerState.assistsThisSession = 0
        current.playerState.interstitialOpportunitiesThisSession = 0
        session = current
        lock.unlock()
    }

    /// Ends the session: any decision left pending is resolved as "player did not continue" —
    /// if an interstitial JUST showed and the session ends right after, that is a real negative
    /// signal, not a missing one.
    public func onSessionEnd() async {
        await resolvePending(playerContinued: false)
    }

    /// Takes (and clears) the pending decision, if any, returning what `resolvePending` needs to
    /// compute the reward. `nil` when nothing is pending — calling `resolvePending` when nothing
    /// is pending must be harmless (every lifecycle hook calls it unconditionally).
    private func takePendingDecision() -> (request: DecisionRequest, response: DecisionResponse, hadValue: Bool)? {
        withSession { s -> (DecisionRequest, DecisionResponse, Bool)? in
            guard let request = s.pendingRequest, let response = s.pendingResponse else { return nil }
            let hadValue = s.pendingActionHadValue
            s.pendingRequest = nil
            s.pendingResponse = nil
            s.pendingActionHadValue = false
            return (request, response, hadValue)
        }.flatMap { $0 }
    }

    /// Resolves whatever decision is currently pending with a downstream signal — the engine room
    /// of the whole design. No-op when nothing is pending, so every caller can call this
    /// unconditionally without first checking whether a decision is outstanding.
    private func resolvePending(playerContinued: Bool) async {
        guard let (request, response, hadValue) = takePendingDecision() else { return }
        guard let configuration = currentConfiguration() else { return }
        let reward = RewardModel.estimate(actionHadValue: hadValue, playerContinued: playerContinued)
        await configuration.decisionEngine.feedback(request: request, response: response, outcomeReward01: reward)
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `swift build && swift test --filter AdsBrainTests`
Expected: PASS (existing tests + new ones). If `test_onSessionEndIsHarmlessWithNoPendingDecision`
or any `resolvePending`-touching test needs a `pendingRequest`/`pendingResponse` actually queued to
be meaningful beyond "doesn't crash," that requires a decision-point method that doesn't exist
until Task 3/4 — this task's tests are deliberately limited to what's observable without one; do
not add a test-only public seam to inject a pending decision (`session`/`SessionState` stay
private) — a fuller `resolvePending` round-trip test belongs in Task 3 once `shouldShowInterstitial`
exists to create a real pending decision.

- [ ] **Step 5: Run full suite regression**

Run: `swift test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/AdsBrain.swift Tests/MonetizationBrainCoreTests/AdsBrainTests.swift
git commit -m "feat: AdsBrain session-state skeleton — Configuration.decisionEngine, isActive, resolvePending, session lifecycle"
```

---

### Task 2: `AdsBrain` — behavior hooks (progression, economy, assist, rewarded/interstitial signals)

**Files:**
- Modify: `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`
- Test: `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`

Ports Android's remaining behavior hooks: `onLevelFail`/`onLevelWin`/`onPurchase`/
`onRewardedOffered`/`onInterstitialShown`/`onAdSkipped`/`onIapOfferShown`/`onAssistShown`/
`onAssistUsed`/`onPlayerContinued`, plus `observeIfActive` (the gated bridge to
`DecisionEngine.observe`). None of these are reachable from a real decision-point method yet
(Task 3/4) — they mutate `SessionState` and, where Android does, call `resolvePending`/`observeIfActive`.
This task is unit-testable purely through `currentState()`'s observable field changes.

- [ ] **Step 1: Write the failing tests**

```swift
    // MARK: - Task 2: behavior hooks

    func test_onLevelFailIncrementsFailStreakAndUpdatesFailRate() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onLevelFail()
        await AdsBrain.shared.onLevelFail()

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.failStreakCurrent, 2)
        XCTAssertEqual(state?.failRateLast30m, 1.0) // 2 attempts, 2 fails
    }

    func test_onLevelWinResetsFailStreakAndAdvancesLevel() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onLevelFail()

        await AdsBrain.shared.onLevelWin()

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.failStreakCurrent, 0)
        XCTAssertEqual(state?.currentLevel, 1)
        XCTAssertEqual(state?.failRateLast30m, 0.5) // 2 attempts (1 fail, 1 win), 1 fail
    }

    func test_onPurchaseUpdatesPurchaseFields() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onPurchase(usd: 4.99)

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.purchasesThisSession, 1)
        XCTAssertEqual(state?.purchaseCountLifetime, 1)
        XCTAssertEqual(state?.purchaseValueLifetimeUsd, 4.99)
        XCTAssertEqual(state?.daysSinceLastPurchase, 0)
    }

    func test_onRewardedOfferedAcceptedUpdatesFieldsAndAcceptRate() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onRewardedOffered(accepted: true)
        await AdsBrain.shared.onRewardedOffered(accepted: false)

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.rewardedOpportunitiesRecent, 2)
        XCTAssertEqual(state?.rewardedAcceptRateRecent, 0.5)
        XCTAssertEqual(state?.adImpressions24h, 1) // only the accepted one counts
    }

    func test_onInterstitialShownIncrementsSessionAdsAndImpressions() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        AdsBrain.shared.onInterstitialShown()

        XCTAssertEqual(AdsBrain.shared.adsShownThisSession(), 1)
        XCTAssertEqual(AdsBrain.shared.currentState()?.adImpressions24h, 1)
    }

    func test_onIapOfferShownIncrementsSessionAndInMemory24hCounter() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        AdsBrain.shared.onIapOfferShown()

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.iapOffersThisSession, 1)
        XCTAssertEqual(state?.iapOffers24h, 1)
    }

    func test_onIapOfferShownIsNoOpWhenInactive() async {
        await AdsBrain.shared.configure(makeConfiguration())
        await AdsBrain.shared.onSessionStart() // enabled is still false here

        AdsBrain.shared.onIapOfferShown()

        XCTAssertEqual(AdsBrain.shared.currentState()?.iapOffersThisSession, 0)
    }

    func test_onAssistShownIncrementsSessionCounter() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        AdsBrain.shared.onAssistShown()

        XCTAssertEqual(AdsBrain.shared.currentState()?.assistsThisSession, 1)
    }
```

- [ ] **Step 2: Run to verify they fail to compile**

Run: `swift test --filter AdsBrainTests`
Expected: build failure — the new hook methods don't exist yet.

- [ ] **Step 3: Write the implementation**

Add a private mutation helper for the `recompute()`-equivalent (fail rate / rewarded accept rate),
and all the new public hooks, in `AdsBrain.swift`:

```swift
    /// Recomputes the two rate fields from their raw counters — the client-side stand-in for a
    /// feature pipeline this SDK doesn't have yet (matches Android's `recompute()`; doc 31 §1).
    private func recompute(_ s: inout SessionState) {
        s.playerState.failRateLast30m = s.levelAttempts > 0
            ? Double(s.levelFails) / Double(s.levelAttempts) : 0.0
        s.playerState.rewardedOpportunitiesRecent = s.rewardedOffered
        s.playerState.rewardedAcceptRateRecent = s.rewardedOffered > 0
            ? Double(s.rewardedAccepted) / Double(s.rewardedOffered) : 0.0
    }

    /// Feeds the latest state into belief, gated on `isActive()` — mirrors Android's private
    /// `observe()`. `DecisionEngine.observe` has its own separate consent gate
    /// (`allowsProfiling()`); this gate is about rollout/holdout membership, a different axis.
    private func observeIfActive() {
        guard isActive(), let configuration = currentConfiguration(), let state = currentState() else { return }
        _ = configuration.decisionEngine.observe(state)
    }

    public func onLevelFail() async {
        await resolvePending(playerContinued: true)
        _ = withSession { s -> Void in
            s.levelAttempts += 1
            s.levelFails += 1
            s.playerState.failStreakCurrent += 1
            self.recompute(&s)
        }
        observeIfActive()
    }

    public func onLevelWin() async {
        await resolvePending(playerContinued: true)
        _ = withSession { s -> Void in
            s.levelAttempts += 1
            s.playerState.failStreakCurrent = 0
            s.playerState.currentLevel += 1
            self.recompute(&s)
        }
        observeIfActive()
    }

    public func onPurchase(usd: Double) async {
        // Credit the pending decision as having had value ONLY if it was actually an IAP offer —
        // a purchase landing while an unrelated decision (e.g. interstitial) is pending must not
        // credit that unrelated action, or the bandit learns the wrong lesson (Android's own
        // comment on `onPurchase` calls this out explicitly).
        _ = withSession { s -> Void in
            if s.pendingResponse?.chosenAction.mode == .iapOffer {
                s.pendingActionHadValue = true
            }
        }
        await resolvePending(playerContinued: true)
        _ = withSession { s -> Void in
            s.playerState.purchasesThisSession += 1
            s.playerState.purchaseCountLifetime += 1
            s.playerState.purchaseValueLifetimeUsd += usd
            s.playerState.daysSinceLastPurchase = 0
        }
        observeIfActive()
    }

    public func onRewardedOffered(accepted: Bool) async {
        _ = withSession { s -> Void in
            s.rewardedOffered += 1
            if accepted {
                s.rewardedAccepted += 1
                s.playerState.adImpressions24h += 1
                s.lastRewardedAtMs = Self.nowMs()
            }
            self.recompute(&s)
            s.pendingActionHadValue = accepted
        }
        await resolvePending(playerContinued: accepted)
        observeIfActive()
    }

    /// A rewarded/interstitial impression just happened. Marks the pending decision as having had
    /// value — whether the player then continued playing is a SEPARATE question the next signal
    /// answers, not decided here (see `RewardModel`).
    public func onInterstitialShown() {
        _ = withSession { s -> Void in
            s.adsShownThisSession += 1
            s.playerState.adImpressions24h += 1
            s.lastInterstitialAtMs = Self.nowMs()
            s.pendingActionHadValue = true
        }
    }

    /// Caller decided not to show (or the ad wasn't ready). Not a value signal.
    public func onAdSkipped() {
        _ = withSession { s -> Void in s.pendingActionHadValue = false }
    }

    /// Actually showed the IAP offer on screen. NOT a value signal by itself — a shown offer
    /// nobody buys created nothing; value is only credited when `onPurchase` resolves this same
    /// pending decision. Gated on `isActive()`: these counters are only read from inside
    /// `shouldOfferIap`, which already returns `true` immediately when inactive — bumping them
    /// while inactive would create dead data with nothing to consume it.
    public func onIapOfferShown() {
        guard isActive() else { return }
        _ = withSession { s -> Void in
            s.playerState.iapOffersThisSession += 1
            // In-memory-only approximation, reset every `onSessionStart` — no cross-session
            // persistence yet (`BrainStorage.loadOfferCount24h`/`bumpOfferCount24h` deferred, see
            // design spec §1/§3.9). Better than leaving this permanently `0`.
            s.playerState.iapOffers24h += 1
            s.lastIapOfferAtMs = Self.nowMs()
            s.pendingActionHadValue = false
        }
    }

    /// Actually handed the assist out (gift shown / difficulty applied). Same "not value yet"
    /// reasoning as `onIapOfferShown` — value is credited by `onAssistUsed(used: true)`.
    public func onAssistShown() {
        guard isActive() else { return }
        _ = withSession { s -> Void in
            s.playerState.assistsThisSession += 1
            s.pendingActionHadValue = false
        }
    }

    /// Outcome of the assist just shown. `used: true` is itself a continuation signal — resolve
    /// immediately, same as `onRewardedOffered(accepted: true)`. `used: false` (ignored) is NOT
    /// resolved as a negative here — the player may still self-recover and stay; wait for a real
    /// downstream signal (`onLevelWin`/`onLevelFail`/`onSessionEnd`), same as `onAdSkipped`.
    public func onAssistUsed(used: Bool) async {
        guard isActive() else { return }
        if used {
            _ = withSession { s -> Void in s.pendingActionHadValue = true }
            await resolvePending(playerContinued: true)
        } else {
            _ = withSession { s -> Void in s.pendingActionHadValue = false }
        }
    }

    /// Manual escape hatch: the host app has some OTHER activity signal worth treating as "still
    /// playing" that isn't covered by a more specific hook above.
    public func onPlayerContinued() async {
        await resolvePending(playerContinued: true)
    }

    private static func nowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
```

Note the `withSession { s -> Void in ... }` closures use `self.recompute(&s)` (an explicit `self`
because the closure captures `self` for calling another instance method — required since the
closure is not `@escaping` but does reference `self`'s method; if Swift accepts it without the
explicit `self` in context, that's fine too, this plan's snippet is illustrative of the required
logic, not a syntax mandate — resolve any compiler diagnostic by adjusting capture syntax, not by
changing the underlying mutation logic).

- [ ] **Step 4: Run to verify it passes**

Run: `swift build && swift test --filter AdsBrainTests`
Expected: PASS.

- [ ] **Step 5: Run full suite regression**

Run: `swift test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/AdsBrain.swift Tests/MonetizationBrainCoreTests/AdsBrainTests.swift
git commit -m "feat: AdsBrain behavior hooks — progression, economy, assist, rewarded/interstitial signals"
```

---

### Task 3: `AdsBrain` — real binary gates (`shouldShowInterstitial`, `shouldOfferRewarded`, `shouldOfferIap`)

**Files:**
- Modify: `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`
- Test: `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`

Wires the three binary decision-point methods to real `DecisionEngine.decide` calls. Two are brand
new (`shouldShowInterstitial`/`shouldOfferRewarded` — `DecisionPoint.interstitialOpportunity`/
`.rewardedOpportunity` already exist with no caller until now); `shouldOfferIap` changes from a
hardcoded-`true` sync stub to a real `async` call. `chooseCrossModal`/`chooseAssist` are Task 4 —
do not touch them here.

- [ ] **Step 1: Write the failing tests**

```swift
    // MARK: - Task 3: real binary gates

    func test_shouldShowInterstitialReturnsTrueWhenUnconfigured() async {
        let result = await AdsBrain.shared.shouldShowInterstitial()
        XCTAssertTrue(result)
    }

    func test_shouldShowInterstitialReturnsTrueWhenInactive() async {
        await AdsBrain.shared.configure(makeConfiguration())
        await AdsBrain.shared.onSessionStart() // enabled still false

        let result = await AdsBrain.shared.shouldShowInterstitial()
        XCTAssertTrue(result)
    }

    func test_shouldShowInterstitialMakesNoEngineCallWhenInactive() async {
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration) // enabled defaults false -> inactive
        await AdsBrain.shared.onSessionStart()

        _ = await AdsBrain.shared.shouldShowInterstitial()

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"]) // no brain_decision
    }

    func test_shouldShowInterstitialWhenActiveAndHoldoutReturnsBaselineDeterministically() async {
        HoldoutManager.holdoutFraction = 1.0 // everyone is holdout
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let result = await AdsBrain.shared.shouldShowInterstitial()

        // Holdout always gets the baseline candidate, which here is `.interstitial` (isBaseline
        // is set on the real-mode arm for every binary gate — see Task 3's implementation).
        XCTAssertTrue(result)
    }

    func test_shouldShowInterstitialIncrementsInterstitialOpportunityCounter() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        _ = await AdsBrain.shared.shouldShowInterstitial()

        XCTAssertEqual(AdsBrain.shared.currentState()?.interstitialOpportunitiesThisSession, 1)
    }

    func test_shouldOfferRewardedReturnsTrueWhenInactive() async {
        let result = await AdsBrain.shared.shouldOfferRewarded()
        XCTAssertTrue(result)
    }

    func test_shouldOfferRewardedWhenActiveAndHoldoutReturnsBaselineDeterministically() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        // A fresh player has failStreakCurrent == 0, which the ethical HARD guardrail
        // ETH_MANUFACTURED_FAIL (GuardrailEngine.evaluate) filters `.rewardedAd` out for —
        // "rewarded should only be offered after a real fail." Without a real fail streak, the
        // guardrail removes the only real candidate, .noAction survives as the sole finalCandidate,
        // and holdout picks THAT instead of the isBaseline candidate — an entirely different,
        // still-correct behavior this test is not the one meant to exercise. Establish a real fail
        // first so `.rewardedAd` actually reaches the holdout selection this test is testing.
        await AdsBrain.shared.onLevelFail()

        let result = await AdsBrain.shared.shouldOfferRewarded()

        XCTAssertTrue(result)
    }

    /// Phase 1's original contract, still true: brain off/inactive means "keep offering IAP as
    /// before" — never a silent revenue-surface shutdown.
    func test_shouldOfferIapReturnsTrueWhenBrainInactive() async {
        XCTAssertTrue(await AdsBrain.shared.shouldOfferIap())
        XCTAssertTrue(await AdsBrain.shared.shouldOfferIap(decisionPoint: .currencyLow))
    }

    func test_shouldOfferIapWhenActiveAndHoldoutReturnsBaselineDeterministically() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let result = await AdsBrain.shared.shouldOfferIap()

        XCTAssertTrue(result)
    }

    func test_shouldOfferIapRefreshesMinutesSinceLastIapOfferBeforeDeciding() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        AdsBrain.shared.onIapOfferShown()

        _ = await AdsBrain.shared.shouldOfferIap()

        // lastIapOfferAtMs was just set, so minutesSinceLastIapOffer must now read ~0, not the
        // -1 "never" sentinel `onSessionStart` reset it to.
        XCTAssertEqual(AdsBrain.shared.currentState()?.minutesSinceLastIapOffer, 0)
    }

    func test_shouldShowInterstitialThenResolvePendingOnNextOpportunityCallsFeedback() async {
        HoldoutManager.holdoutFraction = 0.0 // non-holdout, but only one REAL candidate after
                                              // guardrails is not guaranteed here — use holdout=0
                                              // only to reach the bandit path; this test doesn't
                                              // assert WHICH action was chosen, only that feedback
                                              // fires on the next call.
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        _ = await AdsBrain.shared.shouldShowInterstitial()
        AdsBrain.shared.onInterstitialShown()
        _ = await AdsBrain.shared.shouldShowInterstitial() // resolves the first pending decision

        let sent = await sink.sentEvents
        XCTAssertTrue(sent.contains { $0.eventName == "brain_feedback" })
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `swift test --filter AdsBrainTests`
Expected: build failure — `shouldShowInterstitial`/`shouldOfferRewarded` don't exist;
`shouldOfferIap` is not `async` yet.

- [ ] **Step 3: Write the implementation**

Add `minutesSince`/`refreshIapTiming` helpers and the three real gate methods, replacing the old
`shouldOfferIap` stub entirely:

```swift
    /// Minutes since `atMs`, or `never` if `atMs == 0` (the sentinel for "hasn't happened yet in
    /// this session"). Two different `never` values are used by callers on purpose — see each
    /// call site: `GuardrailEngine`'s cooldown rules read `-1`/`Int.max` differently depending on
    /// which guardrail is asking (Phase 3's `GuardrailEngine.evaluate` doc comments explain why).
    private func minutesSince(_ atMs: Int64, never: Int) -> Int {
        guard atMs != 0 else { return never }
        let elapsedMs = Self.nowMs() - atMs
        return Int(min(elapsedMs / 60_000, Int64(Int.max)))
    }

    /// Refreshes `minutesSinceLastIapOffer` right before a decision that needs it — `PlayerState`
    /// is a snapshot, it doesn't know the clock (same reasoning as every other timing field).
    private func refreshIapTiming() {
        _ = withSession { s -> Void in
            s.playerState.minutesSinceLastIapOffer = self.minutesSince(s.lastIapOfferAtMs, never: -1)
        }
    }

    /// @return `true` if an interstitial should show now. `true` unconditionally when
    /// unconfigured/inactive — the same "off = old behavior" contract every gate in this facade
    /// shares.
    public func shouldShowInterstitial() async -> Bool {
        guard let configuration = currentConfiguration() else { return true }
        guard isActive() else { return true }
        await resolvePending(playerContinued: true)
        // One locked call captures the opportunity-counter bump, the refreshed
        // minutesSinceLastRewarded, AND everything `decide` needs to read — avoids a window
        // between two separate locked calls where a concurrent hook could mutate `session` in
        // between (see the note after this method for why holding `lock` across the `await
        // decide(...)` below is not the fix).
        guard let snapshot = withSession({ s -> (state: PlayerState, adsShownThisSession: Int, minutesSinceLastInterstitial: Int) in
            s.playerState.interstitialOpportunitiesThisSession += 1
            s.playerState.minutesSinceLastRewarded = self.minutesSince(s.lastRewardedAtMs, never: -1)
            return (s.playerState, s.adsShownThisSession, self.minutesSince(s.lastInterstitialAtMs, never: Int.max))
        }) else { return true }
        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: .interstitialOpportunity)
        let candidates = [
            ActionCandidate(mode: .noAction, actionId: "no_action"),
            ActionCandidate(mode: .interstitial, actionId: "interstitial", isBaseline: true)
        ]
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: snapshot.state,
            candidates: candidates,
            adsShownThisSession: snapshot.adsShownThisSession,
            minutesSinceLastInterstitial: snapshot.minutesSinceLastInterstitial
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return response.chosenAction.mode == .interstitial
    }

    /// @return `true` if a rewarded ad should be offered now. The ethical "no manufactured fail"
    /// guardrail applies automatically inside `decide` — no caller-side check needed.
    public func shouldOfferRewarded() async -> Bool {
        guard let configuration = currentConfiguration() else { return true }
        guard isActive() else { return true }
        await resolvePending(playerContinued: true)
        guard let session = currentSession() else { return true }
        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: .rewardedOpportunity)
        let candidates = [
            ActionCandidate(mode: .noAction, actionId: "no_action"),
            ActionCandidate(mode: .rewardedAd, actionId: "rewarded", isBaseline: true)
        ]
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: session.playerState,
            candidates: candidates,
            adsShownThisSession: session.adsShownThisSession
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return response.chosenAction.mode == .rewardedAd
    }

    /// @return `true` if an IAP offer should be shown now. `decisionPoint` distinguishes contexts
    /// with different ethical weight (`.shopOpen` — player-initiated; `.currencyLow` — resource
    /// pressure) so the bandit learns them separately, never pooled into one arm. Frustration and
    /// spend-velocity guardrails apply automatically inside `decide`.
    ///
    /// `async` — was synchronous in Phase 1; every caller needs an `await` added.
    public func shouldOfferIap(decisionPoint: DecisionPoint = .shopOpen) async -> Bool {
        guard let configuration = currentConfiguration() else { return true }
        guard isActive() else { return true }
        await resolvePending(playerContinued: true)
        refreshIapTiming()
        guard let session = currentSession() else { return true }
        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: decisionPoint)
        let candidates = [
            ActionCandidate(mode: .noAction, actionId: "no_action"),
            ActionCandidate(mode: .iapOffer, actionId: "iap_offer", isBaseline: true)
        ]
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: session.playerState,
            candidates: candidates,
            adsShownThisSession: session.adsShownThisSession
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return response.chosenAction.mode == .iapOffer
    }
```

`shouldOfferRewarded`/`shouldOfferIap` above read `currentSession()` once (a single locked call, no
mutation needed before the `decide` call) rather than `withSession`'s mutate-and-return shape — use
whichever of the two helpers fits each method's needs, matching the pattern shown. None of these
methods should hold `lock` across the `await configuration.decisionEngine.decide(...)` call itself
— that would violate the no-lock-across-`async` rule — so there remains an unavoidable, brief
window between reading the snapshot and `decide` returning where a concurrent hook could mutate
`session`; this is an accepted tradeoff (Android's single `@Synchronized` method has no such
window, but iOS cannot fully replicate that across an `await` boundary without breaking the NSLock
discipline this project requires).

- [ ] **Step 4: Run to verify it passes**

Run: `swift build && swift test --filter AdsBrainTests`
Expected: PASS. `test_shouldOfferIapReturnsTrueWhenBrainInactive` requires `await` now — if the old
call sites elsewhere in the test file are not updated to `await`, the build fails; fix every call
site.

- [ ] **Step 5: Run full suite regression**

Run: `swift test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/AdsBrain.swift Tests/MonetizationBrainCoreTests/AdsBrainTests.swift
git commit -m "feat: AdsBrain real binary gates — shouldShowInterstitial, shouldOfferRewarded, shouldOfferIap now decide-backed"
```

---

### Task 4: `AdsBrain` — real `chooseCrossModal`/`chooseAssist`, retire `sdk_baseline_decision`

**Files:**
- Modify: `Sources/MonetizationBrainCore/Brain/AdsBrain.swift`
- Test: `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`

Wires the two cross-modal decision-point methods to real `decide` calls, and deletes
`emitBaselineDecision`/the `sdk_baseline_decision` event entirely (design spec §3.4) now that
`brain_decision` is the real signal. This is the task that breaks the SIX existing tests calling
`chooseCrossModal`/`chooseAssist` and asserting on `sdk_baseline_decision` or a deterministic
baseline action while configured — fix them here, not earlier (the global constraints section
above explains why `enabled = true` is newly required).

- [ ] **Step 1: Update the six now-stale existing tests, then write new ones**

In `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`, replace these six existing tests:

`test_chooseCrossModalReturnsBaselineNotHoldoutWhenUnconfigured` — **unchanged**, no edit needed
(unconfigured fallback is untouched by this task).

`test_chooseCrossModalReportsRealHoldoutMembershipWhenConfigured` — add `AdsBrain.shared.enabled =
true` right after `configure`, and add `await AdsBrain.shared.onSessionStart()` before the
`chooseCrossModal` call (a session must exist for the real path):

```swift
    func test_chooseCrossModalReportsRealHoldoutMembershipWhenConfigured() async {
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_1"))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        // `baseline` MUST be one of chooseCrossModal's real arms (.noAction/.rewardedAd/
        // .iapOffer) now that this is a real decision, not a stub that echoes back whatever was
        // passed — `.interstitial` (the old stub-era baseline) is not a candidate this method
        // can ever build, so under holdout no candidate would match `isBaseline` and the
        // fallback would silently pick `.noAction` instead, defeating this test's own point.
        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .iapOffer)

        // Holdout (user_1 is in-bucket at 0.70, per the known vector already used elsewhere in
        // this file) always gets exactly the `isBaseline` candidate — deterministic.
        XCTAssertEqual(decision.action, .iapOffer)
        XCTAssertTrue(decision.isHoldout)
    }
```

`test_chooseCrossModalReportsNotHoldoutForPlayerOutsideBucket` — same two additions
(`enabled = true`, `onSessionStart()`); do NOT assert `decision.action` (non-holdout, 2 real
candidates — non-deterministic per the global constraints section). `baseline` here doesn't affect
this test's assertion, but use `.iapOffer` anyway for consistency with the sibling test above:

```swift
    func test_chooseCrossModalReportsNotHoldoutForPlayerOutsideBucket() async {
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_2"))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .iapOffer)

        XCTAssertFalse(decision.isHoldout)
    }
```

`test_chooseCrossModalSendsSdkBaselineDecisionEventWhenConfigured` — **replace entirely** (the
event it asserts on no longer exists):

```swift
    func test_chooseCrossModalSendsBrainDecisionEventWhenConfigured() async {
        HoldoutManager.holdoutFraction = 0.70 // user_1 is holdout at this fraction (known vector)
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        // See the note on `test_chooseCrossModalReportsRealHoldoutMembershipWhenConfigured` above
        // for why `baseline` must be a real chooseCrossModal arm now.
        _ = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .iapOffer)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured", "brain_decision"])
        XCTAssertEqual(sent.last?.eventData["decision_point"], .string("LEVEL_FAIL"))
        XCTAssertEqual(sent.last?.eventData["chosen_action_mode"], .string("IAP_OFFER"))
        XCTAssertEqual(sent.last?.eventData["is_holdout"], .int(1))
        XCTAssertEqual(sent.last?.userProperty.userId, "user_1")
    }
```

Apply the identical three edits to `test_chooseAssistReportsRealHoldoutMembershipWhenConfigured`,
`test_chooseAssistReportsNotHoldoutForPlayerOutsideBucket`, and replace
`test_chooseAssistSendsSdkBaselineDecisionEventWhenConfigured` with
`test_chooseAssistSendsBrainDecisionEventWhenConfigured` — same shape as the cross-modal version
above but with `chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)` and
`chosen_action_mode: .string("FREE_ACTION")`.

Then add:

```swift
    // MARK: - Task 4: real cross-modal/assist

    func test_chooseCrossModalAllArmsDisallowedReturnsBaselineWithNoEngineCall() async {
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseCrossModal(
            decisionPoint: .levelFail, allowRewarded: false, allowIapOffer: false, baseline: .noAction
        )

        XCTAssertEqual(decision.action, .noAction)
        XCTAssertFalse(decision.isHoldout)
        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"]) // no brain_decision at all
    }

    func test_chooseCrossModalAllowRewardedFalseExcludesRewardedCandidate() async {
        HoldoutManager.holdoutFraction = 1.0 // holdout forces the baseline candidate deterministically
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseCrossModal(
            decisionPoint: .levelFail, allowRewarded: false, allowIapOffer: true, baseline: .iapOffer
        )

        XCTAssertEqual(decision.action, .iapOffer) // baseline is the only marked candidate; holdout takes it
    }

    func test_chooseAssistNeverOffersIapOffer() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)

        // chooseAssist's candidate set is structurally incapable of including .iapOffer — this
        // asserts the RESULT can never be it, guarding against a future edit accidentally adding
        // that arm (the core ethical constraint from design spec / Android's own doc comment).
        XCTAssertNotEqual(decision.action, .iapOffer)
    }
```

- [ ] **Step 2: Run to verify failures**

Run: `swift test --filter AdsBrainTests`
Expected: build/assertion failures — `chooseCrossModal`/`chooseAssist` still return the Phase 1.5
baseline stub and still emit `sdk_baseline_decision`.

- [ ] **Step 3: Write the implementation**

Delete `emitBaselineDecision` entirely from `AdsBrain.swift`. Replace `chooseCrossModal`/
`chooseAssist`:

```swift
    /// Real cross-modal decision: at ONE decision point, the brain chooses between `.noAction`,
    /// `.rewardedAd`, and `.iapOffer` by the player's pay-propensity — instead of two blind
    /// Boolean gates (`shouldOfferRewarded`/`shouldOfferIap`) asking separately. Ad and purchase
    /// compete as candidates IN THE SAME ROUND, so pressure from one substitutes for the other
    /// rather than stacking.
    ///
    /// `baseline` is what the game would do without the brain — holdout, and the inactive/
    /// unconfigured fallback, always return exactly it. `allowRewarded`/`allowIapOffer` let the
    /// caller drop an arm that doesn't exist at this point (e.g. no ad fill). Both disallowed
    /// means there is no decision to make at all — return `baseline` without calling `decide`, so
    /// a one-arm non-decision never pollutes the OPE log.
    public func chooseCrossModal(
        decisionPoint: DecisionPoint,
        allowRewarded: Bool = true,
        allowIapOffer: Bool = true,
        baseline: ActionMode = .noAction
    ) async -> CrossModalDecision {
        if !allowRewarded && !allowIapOffer {
            return CrossModalDecision(action: baseline, isHoldout: false)
        }
        guard let configuration = currentConfiguration() else {
            return CrossModalDecision(action: baseline, isHoldout: false)
        }
        guard isActive() else {
            return CrossModalDecision(action: baseline, isHoldout: false)
        }
        await resolvePending(playerContinued: true)
        if allowIapOffer { refreshIapTiming() }
        guard let session = currentSession() else {
            return CrossModalDecision(action: baseline, isHoldout: false)
        }

        var candidates = [ActionCandidate(mode: .noAction, actionId: "no_action")]
        if allowRewarded {
            candidates.append(ActionCandidate(mode: .rewardedAd, actionId: "rewarded", isBaseline: baseline == .rewardedAd))
        }
        if allowIapOffer {
            candidates.append(ActionCandidate(mode: .iapOffer, actionId: "iap_offer", isBaseline: baseline == .iapOffer))
        }

        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: decisionPoint)
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: session.playerState,
            candidates: candidates,
            adsShownThisSession: session.adsShownThisSession
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return CrossModalDecision(action: response.chosenAction.mode, isHoldout: response.isHoldout)
    }

    /// Real gameplay-assist decision: chooses between `.noAction`, `.freeAction` (free gift/hint),
    /// and `.gameplayConfig` (ease difficulty) — the two modes never offered anywhere else.
    ///
    /// DELIBERATELY has no `.iapOffer` arm. Rescuing a stuck player with something they must PAY
    /// for is exploiting frustration — exactly what `ETH_FRUSTRATION_EXPLOIT` blocks elsewhere.
    /// Paid rescue is a `chooseCrossModal`/`shouldOfferIap` decision, where the guardrail can
    /// refuse it; it is structurally absent here, not merely discouraged.
    public func chooseAssist(
        decisionPoint: DecisionPoint,
        allowFreeGift: Bool = true,
        allowDifficultyTuning: Bool = true,
        baseline: ActionMode = .noAction
    ) async -> AssistDecision {
        if !allowFreeGift && !allowDifficultyTuning {
            return AssistDecision(action: baseline, isHoldout: false)
        }
        guard let configuration = currentConfiguration() else {
            return AssistDecision(action: baseline, isHoldout: false)
        }
        guard isActive() else {
            return AssistDecision(action: baseline, isHoldout: false)
        }
        await resolvePending(playerContinued: true)
        guard let session = currentSession() else {
            return AssistDecision(action: baseline, isHoldout: false)
        }

        var candidates = [ActionCandidate(mode: .noAction, actionId: "no_action")]
        if allowFreeGift {
            candidates.append(ActionCandidate(mode: .freeAction, actionId: "free_assist", isBaseline: baseline == .freeAction))
        }
        if allowDifficultyTuning {
            candidates.append(ActionCandidate(mode: .gameplayConfig, actionId: "ease_difficulty", isBaseline: baseline == .gameplayConfig))
        }

        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: decisionPoint)
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: session.playerState,
            candidates: candidates,
            adsShownThisSession: session.adsShownThisSession
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return AssistDecision(action: response.chosenAction.mode, isHoldout: response.isHoldout)
    }
```

Also delete the now-inaccurate `// MARK: - Decision points (Phase 1: always return the baseline)`
comment above this section — replace with `// MARK: - Decision points (real, DecisionEngine-backed)`.

- [ ] **Step 4: Run to verify it passes**

Run: `swift build && swift test --filter AdsBrainTests`
Expected: PASS.

- [ ] **Step 5: Run full suite regression**

Run: `swift test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add Sources/MonetizationBrainCore/Brain/AdsBrain.swift Tests/MonetizationBrainCoreTests/AdsBrainTests.swift
git commit -m "feat: AdsBrain real chooseCrossModal/chooseAssist, retire sdk_baseline_decision"
```

---

### Task 5: Close remaining gaps — regression tests, migration sweep

**Files:**
- Test: `Tests/MonetizationBrainCoreTests/AdsBrainTests.swift`
- Possibly modify: any other file `grep` finds still referencing the old `Configuration`
  signature or `sdk_baseline_decision`.

Adds the structural tests the design spec §5 calls for that Tasks 1–4 didn't naturally produce,
and sweeps for any stale reference this plan's task-by-task edits might have missed.

- [ ] **Step 1: Write the failing/new tests**

```swift
    // MARK: - Task 5: closing gaps

    func test_shouldOfferIapAllInactivePathsMakeNoEngineCall() async {
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration) // enabled defaults false
        await AdsBrain.shared.onSessionStart()

        _ = await AdsBrain.shared.shouldOfferIap()

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"])
    }

    func test_rolloutFractionZeroMakesBrainInactiveEvenWhenEnabled() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        AdsBrain.shared.rolloutFraction = 0.0
        await AdsBrain.shared.onSessionStart()

        let result = await AdsBrain.shared.shouldOfferRewarded()

        XCTAssertTrue(result) // outside rollout -> same as inactive -> baseline `true`
    }

    func test_onPurchaseDoesNotCreditAnUnrelatedPendingInterstitialDecision() async {
        HoldoutManager.holdoutFraction = 1.0 // holdout: chosen action deterministic (.interstitial)
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        _ = await AdsBrain.shared.shouldShowInterstitial() // queues a pending .interstitial decision

        await AdsBrain.shared.onPurchase(usd: 0.99) // resolves it — must NOT credit it as valuable

        let sent = await sink.sentEvents
        let feedback = sent.first { $0.eventName == "brain_feedback" }
        XCTAssertEqual(feedback?.eventData["reward_value"], .double(RewardModel.estimate(actionHadValue: false, playerContinued: true)))
    }

    func test_onSessionStartDoesNotResetLifetimePurchaseFields() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onPurchase(usd: 9.99)

        await AdsBrain.shared.onSessionStart() // new session, same player

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.purchaseCountLifetime, 1)
        XCTAssertEqual(state?.purchaseValueLifetimeUsd, 9.99)
        XCTAssertEqual(state?.purchasesThisSession, 0) // session-scoped field DOES reset
    }
```

- [ ] **Step 2: Run to verify failures**

Run: `swift test --filter AdsBrainTests`
Expected: any genuine gap fails; if all pass immediately, that confirms Tasks 1–4 already covered
the behavior correctly — still keep the tests (regression coverage, per this project's established
"no test should be dropped just because it passed on the first try" practice).

- [ ] **Step 3: Fix any failure found; sweep for stale references**

```bash
grep -rn "sdk_baseline_decision\|guardrailEngine:" Sources Tests README.md docs/superpowers/specs docs/superpowers/plans
```

Any hit outside this plan's own historical spec/plan files (which correctly still document what
WAS true before this phase) needs updating. If the sweep is clean, note that in the report instead
of making a no-op commit.

- [ ] **Step 4: Run full suite regression**

Run: `swift test`
Expected: PASS. Report the exact final test count.

- [ ] **Step 5: Commit**

```bash
git add Tests/MonetizationBrainCoreTests/AdsBrainTests.swift
git commit -m "test: AdsBrain regression coverage — inactive-path no-engine-call, rollout gate, purchase-credit guard, session-reset scope"
```

(If Step 3 found and fixed a stale reference in a non-test file, add it to this commit or split
into a second commit — whichever keeps the commit's message accurate to its contents.)

---

### Task 6: README update

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the "Current phase" / "Status" sections**

Read the current `README.md` first (structure is locked by every prior phase's own Task-N edit —
match it, don't restructure it). Update to state:

- Phase 3.5 ("Wire the Decision Engine") is complete and merged: `AdsBrain` is now a real stateful
  facade — `DecisionEngine` wired into five decision-point methods
  (`shouldShowInterstitial`/`shouldOfferRewarded`/`shouldOfferIap`/`chooseCrossModal`/
  `chooseAssist`, all `async` now), full session-state ownership (`onSessionStart`/`onSessionEnd`
  plus ten behavior hooks), and automatic feedback resolution.
- Link the design spec: `docs/superpowers/specs/2026-09-04-wire-decision-engine-design.md`.
- `sdk_baseline_decision` no longer exists — `brain_decision`/`brain_feedback` (from
  `DecisionEngine`) are the real signal now.
- Update the test count from whatever `swift test`'s full-suite run reported in Task 5 — do not
  guess a number.
- List the explicitly deferred items from design spec §1 (remote-config-driven `rolloutFraction`,
  experiment-exposure logging, `ComfortIndex`, CRM bridge signals, `BrainStorage`'s 24h-offer-count
  persistence, `DecisionEngine`'s `precondition`-vs-fail-safe question) — the same "Known deferred
  subsystems" pattern Phase 3's README section already established; either extend that section or
  add a matching one scoped to this phase, whichever reads more naturally against the current file.

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: update README for Phase 3.5 Wire the Decision Engine"
```
