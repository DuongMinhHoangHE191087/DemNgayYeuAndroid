# Phase 3.5 "Wire the Decision Engine" — Design Spec

**Status:** approved (standing autonomous-port mandate — see `monetization-brain-ios-autonomous-mandate`
memory; no per-phase design checkpoint).

## 1. Scope

Phase 3 shipped a complete, independently-testable `DecisionEngine` — orchestration, guardrails,
budget, personalization, persistence — but nothing in `AdsBrain` calls it. Phase 3.5 is the wiring
step, mirroring the Phase 1 → 1.5 split: `AdsBrain` becomes a real stateful facade, exactly like
Android's `com.app.plugin.brain.AdsBrain` object (source: `AdsBrain.kt`, 625 lines, read in full).

**In scope — port `AdsBrain.kt` field-for-field and method-for-method:**
- `rolloutFraction: Double` + the `isActive()` gate (`enabled && HoldoutManager.isInRollout(...)`)
  — iOS's `HoldoutManager.isInRollout` already exists (ported ahead of need in Phase 1); this phase
  is the first caller.
- Facade-owned session state: a `PlayerState` per configured player, `adsShownThisSession`, last-
  action timestamps (`lastInterstitialAtMs`/`lastIapOfferAtMs`/`lastRewardedAtMs`), progression
  counters (`levelAttempts`/`levelFails`/`rewardedOffered`/`rewardedAccepted`), and a pending-
  decision holder (`pendingRequest`/`pendingResponse`/`pendingActionHadValue`) for automatic
  feedback resolution.
- **Five** decision-point methods, all real (`DecisionEngine.decide`-backed), replacing Phase 1.5's
  baseline-only stubs and adding the two Android has that iOS never had:
  - `shouldShowInterstitial() async -> Bool` — **new**.
  - `shouldOfferRewarded() async -> Bool` — **new**.
  - `shouldOfferIap(decisionPoint:) async -> Bool` — was sync in Phase 1, becomes `async`.
  - `chooseCrossModal(decisionPoint:allowRewarded:allowIapOffer:baseline:) async -> CrossModalDecision`
    — was baseline-only since Phase 1.5, now real.
  - `chooseAssist(decisionPoint:allowFreeGift:allowDifficultyTuning:baseline:) async -> AssistDecision`
    — was baseline-only since Phase 1.5, now real.
- Behavior/lifecycle hooks the host app calls to keep session state current — the exact Android set:
  `onSessionStart()`, `onSessionEnd()`, `onLevelFail()`, `onLevelWin()`, `onPurchase(usd:)`,
  `onInterstitialShown()`, `onAdSkipped()`, `onRewardedOffered(accepted:)`, `onIapOfferShown()`,
  `onAssistShown()`, `onAssistUsed(used:)`, `onPlayerContinued()`.
- Automatic feedback resolution (`resolvePending`) — the private engine-room of the whole design;
  see §3.1.
- Retiring `sdk_baseline_decision`/`emitBaselineDecision` (Phase 1.5's stopgap observability event
  for "no real decisioning yet") now that `brain_decision` (emitted by `DecisionEngine` itself) is
  the real signal — see §3.4.
- Closing one of the two Phase-3-final-review gate items the README already flags: consent scope
  on `segment`/`segment_confidence` logging (§3.6). The other flagged item (`precondition` on
  `decide`'s candidate shape) is resolved by construction, not by changing `DecisionEngine` — see
  §3.7.
- `Configuration.guardrailEngine` (dead since Phase 1 — stored, never read) is replaced by
  `Configuration.decisionEngine: DecisionEngine` (§4), per the migration note Phase 3's own spec
  §6 already flagged as "Phase 3.5 is where it starts mattering."

**Out of scope, deferred to later phases (each already flagged in the Android source itself, or a
direct consequence of a Phase 3 deferral):**
- **Remote-config-driven `rolloutFraction`** — the field and the `isActive()` gate ARE ported; what
  Android calls "raise it via policy artifact" is not, for the same reason `BrainPolicy` itself is
  deferred (Phase 3 spec §1). Fixed at whatever the host app sets directly.
- **Experiment-exposure logging** (`HoldoutManager.exposureRecord` + a `experiment_exposure` event
  on every `onSessionStart`) — no CRM/analytics event of that shape exists on iOS yet. This
  subsumes and formally retires the "no backend consumer yet" gap already on `sdk_baseline_decision`
  in the README (that event is deleted this phase anyway, see §3.4) rather than leaving two
  unconsumed events instead of one.
- **`ComfortIndex`** (the 4th North Star Metric — see `[[sdk-product-output-definition]]` memory)
  and `onSessionEnd`'s `reportComfort()` call — no `ComfortIndex` type exists on iOS. Explicitly
  flagged here (not silently dropped) because it maps to a real, named product goal.
- **CRM bridge signals** (`emitFirstPurchase` on `onPurchase`, `emitSegmentChange` on a belief-type
  argmax flip inside `observe`) — no CRM bridge exists on iOS yet.
- **`BrainStorage.loadOfferCount24h`/`bumpOfferCount24h` persistence** — already flagged deferred
  in Phase 3's own spec §1. `PlayerState.iapOffers24h` stays session-scoped only (bumped in-memory
  by `onIapOfferShown`, reset to `0` every `onSessionStart`, never loaded/persisted across
  sessions) — a smaller, honest approximation rather than silently wrong data.
- **`DecisionLogger.flushQueue()` equivalent** — no analog needed; `EventQueue` (Phase 1) already
  owns durable delivery generically, per Phase 3 spec §1's identical reasoning for
  `DecisionLogQueue`.
- **Replacing `DecisionEngine.decide`'s `precondition` with a fail-safe fallback** — see §3.7 for
  why this phase closes the practical risk without touching Phase 3's public contract.

## 2. Architecture

```
AdsBrain (final class, @unchecked Sendable)
├── Configuration (unchanged fields + decisionEngine: DecisionEngine replaces guardrailEngine)
├── SessionState (new: private struct, NSLock-protected, replaces the bare `configuration: Configuration?`
│     as the thing decision-point methods read/mutate)
│     ├── playerState: PlayerState
│     ├── adsShownThisSession, lastInterstitialAtMs, lastIapOfferAtMs, lastRewardedAtMs
│     ├── levelAttempts, levelFails, rewardedOffered, rewardedAccepted
│     └── pendingRequest, pendingResponse, pendingActionHadValue
├── resolvePending(playerContinued:) async — private, calls decisionEngine.feedback
├── 5 decision-point methods (all async) — each: isActive() gate → resolvePending → build
│     candidates/state snapshot → decisionEngine.decide → store new pending → map response back
└── 12 lifecycle/behavior hooks — mutate SessionState synchronously (never inside an async body
      — see §3.2), some also resolvePending/observe
```

`AdsBrain` still never talks to `EventSink` directly for decision events — that responsibility
moved entirely into `DecisionEngine` in Phase 3. `AdsBrain`'s only remaining direct `eventSink`
use is `configure(_:)`'s `sdk_configured` lifecycle event (unchanged).

## 3. Key deviations from the Kotlin source (all deliberate, all documented in code)

1. **`resolvePending` and every hook that calls it become `async`, where Android's are synchronous
   `@Synchronized fun`s.** Android's `engine.feedback(...)` is a blocking call under a monitor
   lock; iOS's `DecisionEngine.feedback` is `async` (a Phase 3 design choice, for the same
   deterministic-event-emission testability reason `configure`/`chooseCrossModal`/`chooseAssist`
   were already made `async` in Phase 1.5). This cascades: `onSessionStart`, `onSessionEnd`,
   `onLevelFail`, `onLevelWin`, `onPurchase`, `onRewardedOffered`, `onAssistUsed`, and
   `onPlayerContinued` all become `async` on iOS even though their Android counterparts are not —
   the async surface is strictly larger here than on Android, and that is a direct, consistent
   consequence of Phase 3's own async design, not a new deviation invented in this phase. Methods
   that never call `resolvePending` (`onInterstitialShown`, `onAdSkipped`, `onIapOfferShown`,
   `onAssistShown`) stay synchronous, matching Android exactly.
2. **No `lock.lock()`/`unlock()` inside any `async` function body — binding across every phase.**
   `AdsBrain` gains a `SessionState` struct (value type, matching this codebase's stated
   immutability preference over Android's in-place-mutated object fields) and a private
   *synchronous* helper — `mutateSession(_ body: (inout SessionState) -> SessionState-derived
   value)` shape, or equivalent — that every async method calls to read/mutate it. The pattern
   already established by `storeConfiguration`/`currentConfiguration` (Phase 1) is the template:
   plain synchronous helpers wrap every `lock.lock()`/`unlock()` pair, `async` bodies only ever
   call those helpers.
3. **`PlayerState` is a Swift `struct` (value type), unlike Android's in-place-mutated
   `PlayerState`.** Every hook reads the current snapshot from `SessionState`, computes the new
   value, and writes the whole struct back under the lock — never partial in-place field mutation
   racing a `decide()` call reading it mid-update. This is a correctness improvement the value-type
   choice gives for free, not extra design work.
4. **`sdk_baseline_decision`/`emitBaselineDecision` are deleted, not adapted.** Android's own
   `isActive() == false` path emits nothing (`return CrossModalDecision(baseline, isHoldout: false)`
   before any logging code runs) — the exact contract this phase restores. `sdk_baseline_decision`
   existed only because Phase 1.5 had no real decisioning to observe yet and needed *some* signal
   that decision points were being reached; now that `brain_decision` is real and emitted by
   `DecisionEngine` itself whenever `isActive()` lets a call through, keeping a second,
   permanently-baseline event around would mean the wire carries two competing "what did the brain
   decide" signals forever, one of which (`sdk_baseline_decision`) the README already noted "has no
   backend consumer yet." Deleting it now, while nothing external depends on it, is cheaper than
   deprecating it later.
5. **The "unconfigured" fallback (`currentConfiguration() == nil`) is unchanged and unaffected by
   this phase.** It returns `baseline`/`true` immediately, before the `isActive()` gate is even
   reached — Android has no equivalent concept (`AdsBrain` is a singleton `object` with no
   optional-configuration wrapper), but this fallback already existed on iOS since Phase 1 and
   stays exactly as-is.
6. **`brain_decision`'s `segment`/`segment_confidence` fields are now gated on
   `ConsentState.shared.allowsPersonalization()`, not only `allowsDecisionLogging()`.** This closes
   the gap the Phase 3 final review flagged and the README recorded as a Phase 3.5 gate item: a
   player who withdrew personalization consent could otherwise still have their derived segment
   logged, even though `PersonalizationScenario.resolve` already refuses to *use* that profile for
   the same player. `DecisionEngine.logDecision` (Phase 3, unchanged elsewhere) emits `"UNKNOWN"`/
   `0.0` for these two fields instead of the real values when personalization consent is absent —
   a one-guard change, isolated to that one private method, not a `DecisionEngine` API change.
7. **`DecisionEngine.decide`'s `precondition` stays exactly as Phase 3 shipped it — this phase
   closes the risk by construction instead of by loosening the contract.** The README's other
   flagged gate item worried this would "trap the host process in release builds ... once Phase 3.5
   makes the candidate list reachable from remote-config-driven business rules." `AdsBrain`'s own
   candidate-construction code (§3.8) always prepends `ActionCandidate(.noAction, "no_action")`
   unconditionally before any `allow*`-flag-driven append, exactly mirroring Android's own
   `mutableListOf(ActionCandidate(NO_ACTION, "no_action"))` starting point — so every call
   `AdsBrain` itself makes into `decide` is non-empty and contains `.noAction` by construction, not
   by hope. `DecisionEngine` remains a `public` type callable directly (its own doc comment already
   says so: "the ONLY entry point code outside this package should call"), so a caller who
   constructs `DecisionEngine` directly and violates the precondition is still trapped — that
   broader question (should `DecisionEngine` itself have a fail-safe instead of a precondition) is
   left open for a future phase if a real external-caller-misuse case ever surfaces; Phase 3.5 does
   not need to answer it to close the risk through `AdsBrain`, which is the only caller this port
   has today.
8. **Two new decision-point methods iOS never had — `shouldShowInterstitial`/`shouldOfferRewarded`.**
   Phase 1's `DecisionPoint` enum already includes `.interstitialOpportunity`/`.rewardedOpportunity`
   with no caller — evidence this gap was anticipated, not newly discovered. Building the session-
   state/pending-feedback machinery this phase requires for the other three methods and NOT adding
   these two (which reuse 100% of that machinery, differing only in their 2-arm candidate list)
   would be artificial scope-narrowing, not real discipline — deferring them would just mean
   re-deriving the same machinery in a near-identical future phase.
9. **`onIapOfferShown`'s `state.iapOffers24h` bump is in-memory-only, not `BrainStorage`-persisted**
   — see the "out of scope" note in §1. Kept as a documented, intentional gap rather than either
   silently wrong (never bumped at all) or requiring a paywall/IAP-tracking layer this phase does
   not build.
10. **`refreshIapTiming()`/`minutesSince(_:never:)` become private free functions or methods on
    `SessionState`**, not global mutable state — ports Android's exact two `Int`-conversion helpers
    (`(now - atMs) / 60_000`, clamped to `Int.max`, with the caller-chosen "never happened" sentinel
    — `Int.max` for interstitial, `-1` for rewarded/IAP, matching each guardrail's own reading of
    that sentinel per Phase 3's `GuardrailEngine.evaluate` doc comments).

## 4. Types

### `AdsBrain.Configuration` (struct, `Sendable`) — one field replaced
```swift
public struct Configuration: Sendable {
    public let remoteConfig: RemoteConfigProvider
    public let eventSink: EventSink
    public let playerId: String
    public let decisionEngine: DecisionEngine

    public init(
        remoteConfig: RemoteConfigProvider,
        eventSink: EventSink,
        playerId: String,
        decisionEngine: DecisionEngine? = nil
    ) {
        self.remoteConfig = remoteConfig
        self.eventSink = eventSink
        self.playerId = playerId
        // A caller-supplied engine is used as-is (tests inject one with a fake bandit/store for
        // determinism). The default is NOT `DecisionEngine()` — that would silently construct an
        // engine with `eventSink: nil` and brain_decision/brain_feedback would never be emitted
        // for a real host app that didn't think to pass one in. Building the default HERE, from
        // this Configuration's own `eventSink`, is what makes "just pass playerId/remoteConfig/
        // eventSink like every other phase" keep working correctly by default.
        self.decisionEngine = decisionEngine ?? DecisionEngine(eventSink: eventSink)
    }
}
```
`guardrailEngine` is removed — `DecisionEngine` already owns and constructs its own
`GuardrailEngine` internally (Phase 3); the field was dead since Phase 1 (Phase 3 spec §1 deviation
1 already confirmed nothing read it).

### `AdsBrain.SessionState` (private struct)
```swift
private struct SessionState {
    var playerState: PlayerState
    var adsShownThisSession: Int = 0
    var lastInterstitialAtMs: Int64 = 0   // 0 = never
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
```
`AdsBrain` holds `private var session: SessionState?` (`nil` until `onSessionStart()` has run at
least once) guarded by the existing `lock`, plus `public var enabled`/new `public var
rolloutFraction: Double = 1.0` following the exact same locked-computed-property shape `enabled`
already has.

### Decision-point method signatures (all `public`, all on `AdsBrain`)
```swift
public func shouldShowInterstitial() async -> Bool
public func shouldOfferRewarded() async -> Bool
public func shouldOfferIap(decisionPoint: DecisionPoint = .shopOpen) async -> Bool   // was sync
public func chooseCrossModal(
    decisionPoint: DecisionPoint,
    allowRewarded: Bool = true,
    allowIapOffer: Bool = true,
    baseline: ActionMode = .noAction
) async -> CrossModalDecision
public func chooseAssist(
    decisionPoint: DecisionPoint,
    allowFreeGift: Bool = true,
    allowDifficultyTuning: Bool = true,
    baseline: ActionMode = .noAction
) async -> AssistDecision
```
Unconfigured fallback (`currentConfiguration() == nil`) unchanged: baseline/`true`, no event.
`isActive()` false (configured but `enabled == false` or outside `rolloutFraction`): same
baseline/`true` return, still no event, no `decisionEngine.decide` call. **Line order matters and
must match Android exactly:** `isActive()` is checked and can return BEFORE `resolvePending` is
ever reached — every one of Android's five decision-point methods has `if (!isActive()) return ...`
as its literal first line, ahead of the `resolvePending(playerContinued = true)` call that follows
it when active. A decision left pending from a session where the brain was active stays pending
(unresolved, and therefore un-rewarded) until the brain becomes active again or `onSessionEnd`
force-resolves it — do not "fix" this by resolving pending state before the `isActive()` check;
that would diverge from the ported source without a documented reason.

Cross-modal/assist "all arms disallowed" short-circuit (`!allowRewarded && !allowIapOffer`, resp.
`!allowFreeGift && !allowDifficultyTuning`) returns `baseline` before even the `isActive()` check,
matching Android's exact line order — no engine call, no pending-decision mutation, at all.

`chooseCrossModal`/`chooseAssist` candidate construction (exact Android literal `actionId`
strings, verified against `AdsBrain.kt`):
```swift
var candidates = [ActionCandidate(mode: .noAction, actionId: "no_action")]
if allowRewarded {
    candidates.append(ActionCandidate(mode: .rewardedAd, actionId: "rewarded", isBaseline: baseline == .rewardedAd))
}
if allowIapOffer {
    candidates.append(ActionCandidate(mode: .iapOffer, actionId: "iap_offer", isBaseline: baseline == .iapOffer))
}
```
(assist: `"free_assist"`/`.freeAction` and `"ease_difficulty"`/`.gameplayConfig`, same shape).
Binary gates use a hardcoded 2-arm list (`"no_action"` + one fixed `actionId`, `isBaseline: true`
on the non-`.noAction` arm): `shouldShowInterstitial` → `"interstitial"`/`.interstitial`;
`shouldOfferRewarded` → `"rewarded"`/`.rewardedAd`; `shouldOfferIap` → `"iap_offer"`/`.iapOffer`.

Each method, after the gates above: `await resolvePending(playerContinued: true)` →
(`shouldShowInterstitial`/`shouldOfferIap` refresh their own timing field first, per §3.10) → build
`DecisionRequest`/candidates from the current session snapshot → `await decisionEngine.decide(...)`
→ store the new `pendingRequest`/`pendingResponse` → map `DecisionResponse.chosenAction.mode`
(binary gates: `== ` the one real mode; cross-modal/assist: pass `chosenAction.mode` and
`isHoldout` straight through into `CrossModalDecision`/`AssistDecision`) back to the caller.

### Lifecycle/behavior hooks (all `public`)
```swift
public func onSessionStart() async     // resolvePending(false); reset session-scoped PlayerState
                                        // fields + counters; decisionEngine.startNewSession(playerId)
public func onSessionEnd() async       // resolvePending(false)
public func onLevelFail() async        // always: failStreakCurrent += 1, levelFails/levelAttempts,
                                        // recompute failRateLast30m; resolvePending(true); observeIfActive()
public func onLevelWin() async         // always: failStreakCurrent = 0, currentLevel += 1,
                                        // levelAttempts; resolvePending(true); observeIfActive()
public func onPurchase(usd: Double) async
    // if pendingResponse?.chosenAction.mode == .iapOffer: pendingActionHadValue = true (BEFORE
    // resolving — crediting the wrong pending decision is the exact bug Android's own comment
    // warns about); always: purchasesThisSession/purchaseCountLifetime/purchaseValueLifetimeUsd/
    // daysSinceLastPurchase = 0; resolvePending(true); observeIfActive()
public func onInterstitialShown()      // sync: adsShownThisSession += 1, adImpressions24h += 1,
                                        // lastInterstitialAtMs = now, pendingActionHadValue = true
public func onAdSkipped()              // sync: pendingActionHadValue = false
public func onRewardedOffered(accepted: Bool) async
    // rewardedOffered += 1; if accepted: rewardedAccepted += 1, adImpressions24h += 1,
    // lastRewardedAtMs = now; recompute rewardedAcceptRateRecent/rewardedOpportunitiesRecent;
    // pendingActionHadValue = accepted; resolvePending(playerContinued: accepted); observeIfActive()
public func onIapOfferShown()          // sync, isActive()-gated: iapOffersThisSession += 1,
                                        // iapOffers24h += 1 (in-memory only, §3.9), lastIapOfferAtMs
                                        // = now, pendingActionHadValue = false
public func onAssistShown()            // sync, isActive()-gated: assistsThisSession += 1,
                                        // pendingActionHadValue = false
public func onAssistUsed(used: Bool) async
    // isActive()-gated: if used { pendingActionHadValue = true; await resolvePending(true) }
    // else { pendingActionHadValue = false }
public func onPlayerContinued() async  // resolvePending(true) — manual escape hatch for host
                                        // apps with an activity signal not covered above
```
`observeIfActive()` — private: `if isActive() { decisionEngine.observe(session.playerState) }`,
sync (`DecisionEngine.observe` is sync, Phase 3). Read-only debug accessors also added, matching
Android's `currentState()`/`adsShownThisSession()`: `public func currentState() -> PlayerState?`,
`public func adsShownThisSession() -> Int`.

### `resolvePending(playerContinued:) async` (private)
```swift
private func resolvePending(playerContinued: Bool) async {
    guard let (request, response, hadValue) = takePendingSnapshot() else { return }  // sync helper,
                                                                                       // clears session's
                                                                                       // pending fields
    let reward = RewardModel.estimate(actionHadValue: hadValue, playerContinued: playerContinued)
    await decisionEngine.feedback(request: request, response: response, outcomeReward01: reward)
}
```
No-op when nothing is pending (mirrors Android: `pendingRequest ?: return`).

## 5. Testing

`AdsBrainTests.swift` gets a substantial rewrite (expected, not a deviation — mirrors Phase 1.5's
own experience rewriting `GuardrailEngineTests` when `GuardrailEngine` stopped being a permissive
stub). Coverage, using a `DecisionEngine` constructed with a real `BanditPolicy`/`GuardrailEngine`
and an in-memory `BrainKeyValueStore` fake (Phase 3's own test pattern) plus a recording fake
`EventSink` (already exists, Phase 1):

- Unconfigured fallback unchanged for all 5 decision-point methods (baseline/`true`, no event).
- `isActive() == false` (both `enabled == false` and outside `rolloutFraction`) returns
  baseline/`true` for all 5, with **zero** calls into the injected `DecisionEngine` — assert this
  by using a spy/counting wrapper or by asserting no `brain_decision` event was emitted.
- `chooseCrossModal`/`chooseAssist` "all arms disallowed" short-circuit: baseline returned, no
  engine call, no pending-decision mutation (a subsequent unrelated hook that would otherwise
  resolve a pending decision must be a no-op).
- Candidate construction: each `allow*` flag independently toggles its own candidate's presence;
  `isBaseline` lands on exactly the candidate matching the `baseline` parameter.
- `resolvePending` composability: a `decide`-producing call followed by the *next* decision-point
  call correctly triggers `decisionEngine.feedback` with `playerContinued: true` and the reward
  `RewardModel.estimate` would produce for whatever `pendingActionHadValue` was set to by the
  hook(s) called in between — exercise at least one full `onInterstitialShown` →
  `shouldShowInterstitial` (next opportunity) round trip.
- `onPurchase`'s "credit the pending decision only if it was actually an IAP offer" guard:
  a purchase arriving while a *different* pending decision (e.g. interstitial) is outstanding must
  NOT mark that pending decision as having had value.
- `onSessionEnd`/a new `onSessionStart` resolve any still-pending decision with
  `playerContinued: false`.
- Session-scoped `PlayerState` fields reset correctly on `onSessionStart` (the exact list in §4);
  lifetime fields (`purchaseCountLifetime`, `sessionsLifetime`, etc.) do NOT reset.
- `segment`/`segment_confidence` consent gate (§3.6): a `brain_decision` event emitted while
  personalization consent is withdrawn carries `"UNKNOWN"`/`0.0` for those two fields — add this
  case directly to `DecisionEngineTests.swift` (it's a `DecisionEngine`-level change, not an
  `AdsBrain`-level one) alongside the existing full-key-set assertion Phase 3's final-review fix
  round already added.
- Async-hook NSLock discipline: no test-visible behavior here, but the whole-branch review will
  re-verify (per the binding rule) that no `lock.lock()`/`unlock()` appears directly inside an
  `async` function body anywhere in the new `AdsBrain.swift`.

## 6. Migration notes

- `AdsBrain.Configuration.guardrailEngine` → `decisionEngine` (§4) — every existing call site that
  constructs a `Configuration` (production and test) needs updating; there is no default-preserving
  overload possible since the field's TYPE changes, not just its default.
- `AdsBrainTests.swift`'s existing baseline-decision assertions (`sdk_baseline_decision` event
  shape) are deleted, not adapted — that event no longer exists (§3.4).
- `shouldOfferIap`'s signature changes from sync `-> Bool` to `async -> Bool` — every existing call
  site (production and test) needs an `await` added; there is no non-async-preserving overload,
  matching how Phase 1.5 changed `configure`/`chooseCrossModal`/`chooseAssist` the same way with no
  deprecated-sync-shim left behind.
