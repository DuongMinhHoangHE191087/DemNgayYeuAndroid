# Decision Engine (Phase 3) — Design Spec

**Status:** approved (standing autonomous-port mandate — see `monetization-brain-ios-autonomous-mandate`
memory; no per-phase design checkpoint).

## 1. Scope

Port Android's orchestration layer — the code that sits between the standalone primitives
Phase 2 shipped (`BeliefState`, `RewardModel`, `LikelihoodEstimator`, `BanditPolicy`) and a real
decision. Source: `com.app.plugin.brain.{DecisionEngine,GuardrailEngine,ClickFraudGuard,
LagrangianBudget,PersonalizationScenario,BrainStorage,DecisionModels}` (Kotlin, ~1400 lines
combined).

**In scope:**
- `GuardrailEngine`: real HARD + RESOURCE rules (currently `AllowAllGuardrailEngine` skeleton).
- `ClickFraudGuard`: click-window lockout.
- `LagrangianBudget`: shadow-price resource shaping.
- `PersonalizationScenario`: segment → policy-knob mapping.
- `BrainStorage`: belief/bandit persistence, backed by an injectable key-value store.
- `ActionCandidate` / `DecisionRequest` / `DecisionResponse`: the `decide`/`feedback` data shapes.
- `DecisionEngine`: the `decide`/`feedback`/`observe` facade tying all of the above together,
  emitting real `brain_decision`/`brain_feedback` events on the existing `EventSink` pipeline.

**Out of scope, deferred to later phases (each already flagged in the Android source itself):**
- **`BrainPolicy`** (doc 35, remote policy artifact application — `applyPolicy()`,
  `BrainPolicy.scenarioCap()`, `BrainPolicy.isEnabled()`/`minConfidence()`). This is a distinct
  remote-config-driven tuning subsystem, not part of the decision loop's own logic. iOS's
  `PersonalizationScenario.resolve` and `GuardrailEngine.effectiveInterstitialCap` use fixed
  local defaults instead of a remote override, exactly as `LikelihoodEstimator`'s profiles were
  left uncalibrated in Phase 2 — a documented gap, not a silent omission.
- **`BrainInspector`** — pure debug/diagnostic snapshot tool. `SelfCheckReport` (Phase 1) already
  covers the "is this SDK healthy" question; extending it is a future, separable task.
- **`DecisionLogQueue`** (the file-append-only durability layer inside `DecisionLogger.kt`) — iOS
  already has this exact guarantee generically in `EventQueue` (persist-before-send,
  remove-by-content-match, atomic replace), built in Phase 1 for precisely this reason. Porting a
  second, decision-log-specific queue would duplicate it. `brain_decision`/`brain_feedback`
  events go through the same `EventSink` every other event uses.
- **Wiring into `AdsBrain`** (`chooseCrossModal`/`chooseAssist`/`shouldOfferIap` becoming real
  bandit-backed calls). This mirrors the Phase 1 → Phase 1.5 split: Phase 3 ships a complete,
  independently testable orchestration layer; Phase 3.5 wires it into the public facade. Wiring
  needs its own design pass (how do simple `allowRewarded`/`allowIapOffer` booleans become a real
  `[ActionCandidate]` list?) that would otherwise balloon this phase past reviewable size — the
  same reasoning already applied once to split Phase 1/1.5.
- **`BrainStorage.playerId()`** (Android's self-generated stable device player id). Not needed:
  iOS's `AdsBrain.Configuration.playerId` is already a *required* field (a stricter contract than
  Android's optional self-generated fallback), so the host app always supplies one.
- **`BrainStorage.loadOfferCount24h`/`bumpOfferCount24h`** — not called anywhere in
  `DecisionEngine.kt`; it belongs to a different call site (paywall/IAP offer-frequency tracking)
  that hasn't been ported yet.
- **`DecisionRequest.context: Map<String, Any>`** — never read by `DecisionEngine.decide()` in
  the Android source. Omitted rather than ported as dead weight, and because an unconstrained
  `[String: Any]` cannot satisfy Swift's `Sendable` checking. If a real use appears in a later
  phase, add it then with a concrete, `Sendable` value type.

## 2. Architecture

```
DecisionEngine (final class, NSLock-protected dictionaries)
├── BanditPolicy            (Phase 2, unchanged)
├── GuardrailEngine          (new: real HARD/RESOURCE rules)
│     └── uses ClickFraudGuard (new, static lock-protected state)
├── BeliefState per playerId (Phase 2, unchanged) ── restored via BrainStorage
├── LagrangianBudget per playerId (new)
├── PersonalizationScenario (new: enum, resolve(_:) reads BeliefState + ConsentState)
└── BrainStorage             (new: belief/bandit persistence via injected BrainKeyValueStore)
```

`DecisionEngine` is the only type from this phase that talks to `EventSink` — everything else is
pure computation, exactly like Phase 2's primitives, and testable without any I/O double.

## 3. Key deviations from the Kotlin source (all deliberate, all documented in code)

1. **`GuardrailEngine` becomes a concrete class, not a protocol.** Phase 1 introduced a
   `GuardrailEngine` *protocol* with a narrow `allows(action:playerId:) -> Bool` shape, explicitly
   documented as a stand-in ("no real rules yet — those arrive with the bandit engine in a later
   phase"). That shape cannot express Android's real `evaluate(candidate:state:...) ->
   GuardrailViolation?` contract (per-violation code/reason/kind, resource-cap queries,
   prevented-count telemetry). Android's own `GuardrailEngine` was never a protocol either — it's
   one concrete class owned by `DecisionEngine`. Phase 3 replaces the file wholesale: the protocol
   and `AllowAllGuardrailEngine` are deleted, `GuardrailEngine` becomes the real
   `public final class`. This is safe: `AdsBrain.Configuration.guardrailEngine` is stored but
   never read by any Phase 1/1.5 decision-point method (verified against the current source), so
   no runtime behavior depends on the old protocol shape. `AdsBrain.Configuration.guardrailEngine`
   changes type from `GuardrailEngine` (protocol) to `GuardrailEngine` (class) with a fresh
   instance as its default, and `GuardrailEngineTests.swift` is replaced with real-rule coverage.
2. **`BrainStorage` takes an injected `BrainKeyValueStore` protocol instead of wrapping a global
   singleton.** Android's `BrainStorage` is an `object` wrapping `PreferenceHelper.getInstance()`
   directly. iOS already has one precedent for this exact swap — `RemoteConfigProvider` — so
   `BrainKeyValueStore` (get/set/remove string) + a `UserDefaultsBrainStore` default
   implementation follows the same shape, keeping `DecisionEngine` unit-testable against an
   in-memory fake instead of polluting real `UserDefaults` across test runs.
3. **`PersonalizationScenario`'s per-case constants become a raw-value enum + computed
   properties**, matching the style already established by `LatentPlayerType` (raw value =
   Kotlin's enum name, `CaseIterable`, `Sendable`), rather than Kotlin's constructor-argument enum
   syntax which Swift doesn't have. The raw value doubles as the exact string Android logs via
   `.name` in `brain_decision`'s `scenario` field.
4. **`ClickFraudGuard` becomes a Swift `enum` namespace with `NSLock`-protected static state**,
   mirroring `HoldoutManager`'s shape (a namespace, not a class instance) but *with* a lock —
   Android's version uses `ConcurrentHashMap` + a `synchronized` block specifically because it's
   real concurrent mutable state (ad clicks can arrive from multiple call sites), so — unlike
   `HoldoutManager`'s known-and-documented unsynchronized limitation — this new code gets a lock
   from day one.
5. **Guardrail violation `reason` strings are ported to English**, matching every other file in
   this codebase (the Android source's comments and end-user-adjacent strings are Vietnamese; the
   iOS port has translated all of them so far). The `code` identifiers
   (`CLICK_FRAUD_LOCKOUT`, `ETH_MANUFACTURED_FAIL`, `AD_PRESSURE_SESSION_CAP`, etc.) stay
   byte-for-byte identical to Android's — those are the strings that land in the `brain_decision`
   event's `guardrails_applied` field and must match across platforms for backend analysis.
6. **`brain_decision`/`brain_feedback` wire shape is taken from the REAL backend contract**
   (`backend/app/normalize.py` lines 158–184), not reconstructed from `DecisionResponse`'s Kotlin
   field names — this is the exact lesson from Phase 1 and Phase 1.5's final reviews, both of
   which found an invented-instead-of-verified wire shape as a Critical bug. Confirmed fields —
   all inside `eventData`, snake_case, matching this package's already-locked convention:

   `brain_decision`: `decision_id`, `decision_point`, `chosen_action_id`, `chosen_action_mode`,
   `chosen_action_probability` (float), `eligible_actions` (comma-joined, order preserved),
   `guardrails_applied` (comma-joined), `segment`, `segment_confidence` (float), `scenario`,
   `is_holdout` (int 0/1 — backend's `_i` helper, not a JSON bool), `policy_version`.

   `brain_feedback`: `decision_id`, `outcome_event`, `outcome_timestamp_ms` (int ms), and
   `reward_value` — **omitted from `eventData` entirely when `nil`**, never sent as `0.0`. The
   backend's own `_fo` helper comment is explicit: "absence and 0.0 mean different things — a
   reward of exactly 0.0 is a valid outcome (ad shown, player quit anyway); a fabricated 0.0 for
   'not yet known' poisons the OPE estimator." `SdkEvent.eventData` is `[String: SdkEventValue]`,
   so omitting the key is a natural `nil`-guard, not a special case.
7. **`is_holdout` is sent as `SdkEventValue.int(0/1)`**, not `.bool`, matching the backend's `_i`
   parsing of that field (confirmed in `normalize.py`) — sending a JSON `true`/`false` there would
   parse to `0` via `_i`'s `int(v)` coercion on non-numeric input in the Python backend's weaker
   path, which is exactly the kind of silent-corruption risk Phase 1.5's final review flagged
   once already for a different field. Use `.int`, not `.bool`.
8. **`DecisionEngine.decide`/`feedback` are `async`**, matching every other event-emitting call in
   this package (`AdsBrain.configure`/`chooseCrossModal`/`chooseAssist`) — for the same reason:
   deterministic testability of the emitted event, not fire-and-forget. `observe`/`beliefFor`/
   `budgetFor`/`scenarioFor`/`resetPlayer`/`startNewSession`/`observationCount`/
   `effectiveInterstitialCap` stay synchronous — none of them touch `EventSink`.
9. **`nextDecisionId` drops the storage-counter bug class by construction.** Android's own doc
   comment on `DecisionLogger.nextDecisionId` describes a real bug an earlier version had
   (deriving the counter from `PreferenceHelper`, which silently returns a constant default when
   uninitialized — collapsing every decision id to the same value). The current Kotlin is already
   process-local (`runId` + `AtomicLong`), so there is no bug to avoid porting — just port the
   same process-local shape: a `String` generated once per process (`ProcessInfo` uptime +
   `UUID().uuidString.prefix(4)`, or equivalent) plus a lock-protected counter.

## 4. Types (field-for-field)

### `GuardrailKind` (enum)
`case hard, resource` — `Sendable, Equatable`.

### `GuardrailViolation` (struct)
`let code: String`, `let reason: String`, `let kind: GuardrailKind` — `Sendable, Equatable`.

### `GuardrailEngine` (final class, `@unchecked Sendable`)
Public `var` thresholds (all with Android's exact default values — these are conservative design
choices per the Kotlin doc comments, not calibrated from data, and inherit that caveat verbatim):

```
maxInterstitialPerSession = 6
minMinutesBetweenInterstitial = 3
maxAds24h = 40
churnRiskProtectionThreshold = 0.75
maxIapOffersPerSession = 3
minMinutesBetweenIapOffers = 10
maxPurchasesPerSessionBeforeBreak = 3
frustrationStreakThreshold = 5
minLevelForInterstitial = 0
minLevelForInterstitialAfterLoss = 0
minMinutesAfterRewarded = 0
skipFirstInterstitialsOnReopen = 0
maxIapOffers24h = 0
maxAssistsPerSession = 3
```

Private `NSLock`-protected `preventedCounts: [String: Int]`.

Methods:
- `preventedCount(_ code: String) -> Int`
- `allPreventedCounts() -> [String: Int]`
- `effectiveInterstitialCap(_ scenario: PersonalizationScenario?) -> Int` — `min(maxInterstitialPerSession, scenario?.interstitialCapPerSession ?? maxInterstitialPerSession)`. This `min` direction is a safety invariant, not an implementation detail: a scenario can only tighten the cap, never loosen it beyond the absolute ceiling — call this out in the doc comment exactly as the Kotlin does.
- `evaluate(candidate:state:adsShownThisSession:minutesSinceLastInterstitial:scenario:decisionPoint:) -> GuardrailViolation?` — the full rule chain from `GuardrailEngine.kt` lines 164–387, same order:
  1. HARD: click-fraud lockout (exempts `.noAction`, `.gameplayConfig`, `.freeAction`)
  2. HARD: rewarded requires a real fail streak (`ETH_MANUFACTURED_FAIL`)
  3. HARD: no IAP offer during a frustration streak (`ETH_FRUSTRATION_EXPLOIT`)
  4. HARD: IAP velocity breaker (`IAP_VELOCITY_BREAKER`)
  5. RESOURCE (interstitial only): new-player level gate (loss-path variant), reopen grace,
     post-rewarded cooldown, session cap (via `effectiveInterstitialCap`), cooldown, 24h cap
  6. RESOURCE: recent-payer ad-pressure protection (ad-like candidates only)
  7. RESOURCE (IAP offer only): 24h cap, session cap, cooldown
  8. RESOURCE (assist-like only): session cap
  9. RESOURCE (interstitial only): churn-risk protection
  Every violation branch calls a private `record(_:)` that increments `preventedCounts` before
  returning — matching Android's `record()` helper exactly (this feeds a future ComfortIndex,
  per the Kotlin doc comment, so the counter must be right even though nothing reads it yet).

### `ClickFraudGuard` (enum namespace)
`static var maxClicksPerWindow = 6`, `windowMs: Int64 = 300_000`, `lockoutMs: Int64 = 1_800_000`.
Private `NSLock` + two `static var` dictionaries (`clickState: [String: (windowStart: Int64, count: Int)]`, `lockedUntil: [String: Int64]`).
- `registerClick(playerId:nowMs:)` — default `nowMs` = current epoch ms.
- `isLocked(playerId:nowMs:) -> Bool`.

### `LagrangianBudget` (final class, `@unchecked Sendable`)
`var learningRate = 0.15`, `var maxLambda = 5.0`. `NSLock`-protected `private(set) var lambdaAdPressure = 0.1` (getter goes through the lock too, matching `BeliefState`'s pattern from Phase 2's final review).
- `costOf(_ mode: ActionMode) -> Double` — `interstitial: 1.0, rewardedAd: 0.3, iapOffer: 0.2, noAction/freeAction/gameplayConfig: 0.0`.
- `penaltyFor(_ mode: ActionMode) -> Double` — `lambdaAdPressure * costOf(mode)`.
- `observe(chosenMode:budgetPerDecision:)` — multiplicative-weights update, clamped to `[0.001, maxLambda]`.
- `reset()` — back to `0.1`.

### `PersonalizationScenario` (raw-value enum, `String, Sendable, CaseIterable`)
Four cases, raw values matching Kotlin's names exactly: `PROTECT_AND_CONVERT`, `AD_FORWARD`,
`RETENTION_FIRST`, `BALANCED_HYBRID`. Computed properties per case: `displayName`, `rationale`,
`interstitialCapPerSession`, `rewardedAggressiveness`, `iapOfferPriority`, `freeValueGenerosity`
— exact values from the Kotlin source (§ PersonalizationScenario.kt above).
- `static func forType(_ type: LatentPlayerType) -> PersonalizationScenario` — the fixed 1:1 map.
- `static var minConfidence: Double = 0.40` (was `BrainPolicy`-overridable in Android; fixed here, see §1).
- `static func resolve(_ belief: BeliefState) -> PersonalizationScenario` — `!ConsentState.shared.allowsPersonalization() → .balancedHybrid`; else `belief.confidence() >= minConfidence ? forType(belief.mostLikelyType()) : .balancedHybrid`. (Drops the `BrainPolicy.isEnabled()` check — see §1; no equivalent kill-switch exists yet in this phase.)

### `BrainKeyValueStore` (protocol, `Sendable`)
`func string(forKey:) -> String?`, `func setString(_:forKey:)`, `func removeObject(forKey:)`.

### `UserDefaultsBrainStore` (struct, conforms to `BrainKeyValueStore`)
Wraps an injected `UserDefaults` (default `.standard`).

### `BrainStorage` (final class, `@unchecked Sendable`)
Holds a `BrainKeyValueStore`. Key prefixes: `"brain_belief_"`, `"brain_bandit_stats"`.
- `loadBelief(playerId:) -> String?` — `nil` when blank/absent (matches Kotlin's blank→nil).
- `saveBelief(playerId:serialized:)`
- `clearBelief(playerId:)`
- `loadBanditStats() -> String?`
- `saveBanditStats(_:)`

### `ActionCandidate` (struct, `Sendable`)
`public let mode: ActionMode`, `public let actionId: String`, `internal var rawScore: Double = 0.0` (module-internal — mirrors Kotlin's "don't read this from outside the brain package"), `public let isBaseline: Bool = false`.

### `DecisionRequest` (struct, `Sendable`)
`public let playerId: String`, `public let decisionPoint: DecisionPoint`. (No `context` — see §1.)

### `DecisionResponse` (struct, `Sendable`, `Equatable`)
`decisionId: String`, `chosenAction: ActionCandidate`, `chosenActionProbability: Double`, `policyVersion: String`, `guardrailsApplied: [String] = []`, `eligibleActionIds: [String] = []`, `segment: LatentPlayerType? = nil`, `segmentConfidence: Double = 0.0`, `scenario: PersonalizationScenario? = nil`, `isHoldout: Bool = false`. (`ActionCandidate` needs `Equatable` too for this to compile — add it.)

### `DecisionEngine` (final class, `@unchecked Sendable`)
```
public static let shared = DecisionEngine()

public init(
    policyVersion: String = "brain_v0_skeleton",
    bandit: BanditPolicy = BanditPolicy(),
    guardrails: GuardrailEngine = GuardrailEngine(),
    store: BrainKeyValueStore = UserDefaultsBrainStore(),
    eventSink: EventSink? = nil
)
```
`eventSink` is optional and `nil` by default — a `DecisionEngine` constructed standalone (e.g. in
a test, or before `AdsBrain.configure` has run) can still `decide`/`feedback` correctly; a `nil`
sink just means no event is emitted, exactly like `AdsBrain`'s own unconfigured-state behavior in
Phase 1. Phase 3.5's wiring will pass `AdsBrain`'s configured sink in.

Internal `NSLock`-protected `[String: BeliefState]` and `[String: LagrangianBudget]` dictionaries,
plus `BrainStorage`, `bandit`, `guardrails`, `policyVersion`, `eventSink`.
`public var estimatedDecisionsPerSession: Int = 30`.

At `init`: `bandit.restore(store.loadBanditStats())` — no `DecisionLogger.installDefaultSink()`
equivalent needed (see §1, `EventQueue` already handles durable delivery generically).

Methods (field-for-field from `DecisionEngine.kt`, English doc comments translated from the
Vietnamese rationale already read from source):
- `budgetFor(_ playerId: String) -> LagrangianBudget` — get-or-create, lock-protected.
- `beliefFor(_ playerId: String) -> BeliefState` — get-or-create via `BeliefState.deserialize(playerId:raw: storage.loadBelief(playerId))`, lock-protected.
- `observe(_ state: PlayerState) -> Bool` — gated on `ConsentState.shared.allowsProfiling()`; `false` if consent denied or `LikelihoodEstimator.estimate(state)` is empty; else updates belief, persists via `storage.saveBelief`, returns `true`. **Synchronous** (see §3.8).
- `scenarioFor(_ playerId: String) -> PersonalizationScenario` — `.resolve(beliefFor(playerId))`.
- `observationCount(decisionPoint:segment:armId:) -> Double` — `bandit.observationCount("\(decisionPoint.rawValue)_\(segment.rawValue)", armId)`.
- `effectiveInterstitialCap(_ playerId: String) -> Int` — `guardrails.effectiveInterstitialCap(scenarioFor(playerId))`.
- `resetPlayer(_ playerId: String)` — removes from both dictionaries AND calls `storage.clearBelief(playerId)` (both, per the Kotlin doc comment's explicit warning about the "only cleared storage, in-memory copy still stale" bug class).
- `startNewSession(_ playerId: String)` — `budgetFor(playerId).reset()`.
- `decide(request:state:candidates:adsShownThisSession:minutesSinceLastInterstitial:) async -> DecisionResponse`:
  1. `precondition(!candidates.isEmpty)`, `precondition(candidates.contains { $0.mode == .noAction })` — matching Android's `require`.
  2. `isHoldout = HoldoutManager.isHoldout(request.playerId)`.
  3. `scenario = isHoldout ? .balancedHybrid : scenarioFor(request.playerId)`.
  4. Filter candidates through `guardrails.evaluate(...)`, collecting violation codes into `guardrailsApplied`; if that empties the set, fall back to the first `.noAction` candidate from the ORIGINAL list (never crash on an over-aggressive guardrail).
  5. If holdout: `chosen = finalCandidates.first(where: { $0.isBaseline }) ?? finalCandidates.first(where: { $0.mode == .noAction }) ?? finalCandidates[0]`, `probability = 1.0`. (Android's `firstOrNull { isBaseline } ?: first { NO_ACTION }` — step 4's fallback guarantees a `.noAction` candidate always survives, so the third branch is unreachable in practice; it exists so this method has no force-unwrap and cannot crash even if a future refactor weakens that invariant.)
  6. Else: build `segmentWeights` from `LatentPlayerType.allCases.map { ("\(request.decisionPoint.rawValue)_\($0.rawValue)", belief.posterior[$0] ?? 0.0) }.filter { $0.1 > 0 }`; `penalties` from `budget.penaltyFor(mode)` per candidate; call `bandit.selectArm(segmentWeights:candidateArmIds:penalties:)`; look up the chosen `ActionCandidate` by `actionId`; then `budget.observe(chosen.mode, cap / max(estimatedDecisionsPerSession, 1))` where `cap = guardrails.effectiveInterstitialCap(scenario)`.
  7. Build `DecisionResponse` with a freshly generated `decisionId`, `belief.mostLikelyType()`/`belief.confidence()` for `segment`/`segmentConfidence`.
  8. If `eventSink` is set and `ConsentState.shared.allowsDecisionLogging()`, emit `brain_decision` per §3.6/§3.7, `await`ed.
  9. Return the response.
- `feedback(request:response:outcomeReward01:) async`:
  1. If `!response.isHoldout`: recompute `segmentWeights` from `beliefFor(request.playerId)` the same way as step 6 above, call `bandit.update(segmentKey:armId: response.chosenAction.actionId, reward01: outcomeReward01, weight:)` for each, then `storage.saveBanditStats(bandit.serialize())`.
  2. If `eventSink` is set and `ConsentState.shared.allowsDecisionLogging()`, emit `brain_feedback` per §3.6 (`outcome_event: "outcome_reward"`, current time for `outcome_timestamp_ms`, `reward_value: outcomeReward01`), `await`ed.

`nextDecisionId(playerId:)` — private, per §3.9: `"d_\(playerId)_\(Self.runId)_\(counter)"`, `runId` a `let` computed once (process-lifetime), counter an `NSLock`-protected `Int`.

## 5. Testing

Every new type gets the same treatment Phase 2 established: pure-logic types (`GuardrailEngine`,
`LagrangianBudget`, `PersonalizationScenario`, `ClickFraudGuard`) get deterministic unit tests with
no I/O double needed. `BrainStorage` gets an in-memory `BrainKeyValueStore` fake. `DecisionEngine`
gets a recording fake `EventSink` (already exists from Phase 1/1.5 tests — reuse it) to assert on
the exact `brain_decision`/`brain_feedback` field names and values, plus:
- A composability test exercising the full `decide` → `feedback` round trip with a real
  `BanditPolicy`/`BeliefState`, matching the composability-test discipline the Phase 2 final
  review established (the exact gap class that review caught: per-piece-correct code whose
  composition was never exercised).
- A guardrail-filters-everything-down-to-NO_ACTION test (the documented fallback path).
- A holdout-path test asserting `probability == 1.0` and that `bandit.observationCount` does NOT
  change after `feedback` on a holdout response (Android's explicit "holdout outcomes never feed
  the bandit" rule).
- A `resetPlayer` test asserting BOTH the in-memory dictionary AND `storage.loadBelief` are
  cleared (the exact bug class the Kotlin doc comment warns about).
- A `reward_value` omission test: `feedback` with a `nil`-equivalent... actually `outcomeReward01`
  is non-optional `Double` in this signature (Android's is too — `Double`, not `Double?`), so
  `reward_value` is ALWAYS present in practice via this path; note in the test that the
  `SdkEventValue` chosen is `.double`, not `.int`, and that the omission behavior described in
  §3.6 is what `SdkEvent.eventData` gives for free, not something `DecisionEngine` has to special-
  case here.

## 6. Migration note for `AdsBrain.swift` / `GuardrailEngineTests.swift`

- `AdsBrain.Configuration.guardrailEngine: GuardrailEngine = AllowAllGuardrailEngine()` becomes
  `guardrailEngine: GuardrailEngine = GuardrailEngine()`. The property is still unread by any
  Phase 1/1.5 method — Phase 3.5 is where it starts mattering.
- `GuardrailEngineTests.swift` is replaced with real-rule tests (see §5); nothing in it tested
  behavior other than "always allows," which no longer exists.
