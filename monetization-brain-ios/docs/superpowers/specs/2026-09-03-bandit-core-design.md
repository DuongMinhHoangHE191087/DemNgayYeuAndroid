# Phase 2 — Bandit Core (Design)

**Date:** 2026-09-03
**Status:** Approved for planning (standing mandate: port Android 1:1, no per-phase approval needed — see memory `monetization-brain-ios-autonomous-mandate`)
**Depends on:** Phase 1 + 1.5 (merged to `main`). Source of truth: `appplugin` (Android),
`src/main/java/com/app/plugin/brain/{PlayerState,BeliefState,RewardModel,LikelihoodEstimator,BanditPolicy}.kt`.

## 1. Why this phase, and why this exact slice

Phase 1/1.5 built the plumbing and a facade whose decision points always return baseline — there
is no intelligence yet. The next Android file group in build order (commit `f710795`, the first
"Monetization Brain" commit) is the actual decision-making core. That group is large (~1700
Kotlin lines across `BanditPolicy`/`BeliefState`/`RewardModel`/`PlayerState`/
`LikelihoodEstimator`/`DecisionEngine`/`GuardrailEngine`/`LagrangianBudget`/
`PersonalizationScenario`/`BrainStorage`/`BrainInspector`) and DecisionEngine alone touches
nearly all of it — too large for one reviewable task, let alone one phase, matching the exact
lesson Phase 1 vs. 1.5 already established (small reviewable slices catch real bugs; one giant
slice hides them).

This phase ports the **standalone, independently-testable algorithmic primitives** — the pieces
with no dependency on anything not yet ported (no `GuardrailEngine` real rules, no
`LagrangianBudget`, no `BrainStorage` persistence, no `DecisionEngine` orchestration, no
`AdsBrain` wiring). Everything here is pure computation over explicit inputs, matching how
Phase 1 scoped itself to "plumbing with no intelligence" and Phase 1.5 scoped itself to "wiring
with no new intelligence." This phase is the mirror: **intelligence with no wiring yet.**

**Phase 3** (next, not this one) will port `DecisionEngine`, real `GuardrailEngine` rules,
`LagrangianBudget`, `PersonalizationScenario`, and `BrainStorage`, and wire `AdsBrain`'s
decision points to actually use all of it — the same "Phase N: build the piece" → "Phase N.5:
wire it" pattern already proven twice.

## 2. Scope

Port exactly these five Android files, field-for-field, formula-for-formula:

- `PlayerState` → `PlayerState` (Swift): pure data struct, the decision engine's input `S_t`.
- `LatentPlayerType` + `BeliefState` → same names: discrete Bayesian posterior over 4 latent
  player types.
- `RewardModel` → same name: the downstream-continuation reward formula (doc 39's fix — reward
  comes from what happens AFTER an action, not the action itself).
- `LikelihoodEstimator` → same name: naive-Bayes observation → likelihood, the piece that makes
  `BeliefState.update()` actually move (uncalibrated-but-documented Gaussian profile matching).
- `BanditPolicy` → same name: segment-keyed Thompson Sampling Beta-Bernoulli contextual bandit,
  with an exploration floor and Monte Carlo mixture sampling across latent segments.

**Not in scope** (Phase 3): `DecisionEngine`, real `GuardrailEngine` rules (`ClickFraudGuard`,
the interstitial-cap/frustration-exploit/velocity-breaker rules), `LagrangianBudget`,
`PersonalizationScenario`, `BrainStorage` (persistence — this phase's types have `serialize()`/
`deserialize()`/`restore()` methods, matching Android, but nothing calls them yet), `BrainPolicy`
(remote policy artifact — later, tied to `RemoteConfigProvider`), `BrainInspector`/
`BrainDebugView` (Android `View`-based debug UI, not portable 1:1 — a Swift-native debug surface,
if any, is a separate later decision, not assumed here). None of this phase's types are wired
into `AdsBrain` — that is explicitly Phase 3's job, mirroring the wire-facade precedent.

## 3. One deliberate deviation: RNG is injectable

Android's `BanditPolicy` calls `kotlin.random.Random` directly, with no seam for deterministic
testing — its own test suite (not reviewed here) presumably tests statistical properties over
many samples rather than exact draws. Swift's `RandomNumberGenerator` protocol makes dependency
injection nearly free, and this project's own testing discipline (TDD, deterministic assertions,
"pristine output, no flaky sleep/poll-based tests" — established explicitly in the Phase 1.5
spec) argues for taking it. `BanditPolicy`'s `selectArm(...)` and its private Gamma/Beta/Gaussian
sampling helpers take a `randomSource: RandomNumberGenerator` parameter (defaulting to
`SystemRandomNumberGenerator()` in production use), so tests can inject a seeded
`RandomNumberGenerator` (e.g. a small linear-congruential test double) and assert exact,
reproducible draws instead of only statistical trends.

This is the only structural deviation from the Android source in this phase. Every formula,
constant, and comment-documented rationale (damping value 0.25, exploration floor 0.03, reward
weights 0.4/0.6, Gaussian sigmas, the four latent-type profiles) is copied verbatim — this phase
is a port, not a redesign, exactly per the standing mandate.

## 4. Types, field-for-field

### 4.1 `PlayerState`

Direct struct port. All Kotlin `var` fields become Swift `var` (mutable — this is a per-decision
snapshot the game populates and the engine reads, not an immutable value like `SdkEvent`). Types:
`Int` → `Int`, `Long` → `Int64` (soft/hard currency balances — Kotlin `Long` is 64-bit), `Double`
→ `Double`, `String` → `String`. Defaults copied exactly (e.g. `daysSinceLastPurchase: Int = -1`,
sentinel for "never purchased" — NOT zero, which would mean "purchased today").

### 4.2 `LatentPlayerType` + `BeliefState`

`LatentPlayerType`: 4-case enum, `CaseIterable`, `Sendable`, `Equatable`. Raw values mirror the
Kotlin case names (`HIGH_SKILL_LOW_TOLERANCE`, `HIGH_SKILL_HIGH_TOLERANCE`,
`LOW_SKILL_HIGH_CHURN_RISK`, `CASUAL_STEADY`) — same precedent as `DecisionPoint`/`ActionMode`,
for future wire-format consistency once these values need to travel in an event.

`BeliefState`: a class (reference semantics, matching Kotlin `data class` used as mutable state
via `MutableMap` — `update()` mutates in place, matching every call site in `DecisionEngine`
that expects the SAME instance to reflect new evidence). Starts at uniform prior (0.25 each).
`update(likelihood:)` — unnormalized Bayes update then renormalize, skipping the write if the
normalizer is `<= 0` (guards a likelihood map that zeroes everything out). `mostLikelyType()`,
`confidence()` (max posterior value), `isStillPrior()` (all four within 1e-6 of 0.25),
`serialize()`/`static deserialize(playerId:raw:)` — string round-trip for future `BrainStorage`
persistence (Phase 3), ported now since it's part of the type's own contract and cheap to get
right alongside the rest, but not called by anything in this phase.

### 4.3 `RewardModel`

Same enum-as-namespace pattern as `HoldoutManager`: `public enum RewardModel` with mutable
`static var weightImmediateValue: Double = 0.4`, `static var weightContinuation: Double = 0.6`,
`static func estimate(actionHadValue:playerContinued:) -> Double` (the `Bool?` three-way branch:
`true` → full continuation weight, `false` → zero, `nil` → half weight, "unknown ≠ penalize"),
`static func reset()`. Formula, weights, and the reward-priority table in the Kotlin doc comment
are preserved verbatim in the Swift doc comment — this is exactly the file Phase 1's own `DOC/`
corpus calls out as fixing a real degenerate-reward bug (doc 39), so the rationale is load-bearing
context, not decoration.

### 4.4 `LikelihoodEstimator`

`public enum LikelihoodEstimator` namespace. Private `Profile` struct (4 Doubles). Default
profiles for all 4 types, copied exactly. `profileOf(_:)` — in Android, checks
`BrainPolicy.profileFor(type)` first (remote-calibrated override) before falling back to the
compiled default; `BrainPolicy` doesn't exist yet in this phase, so Swift's `profileOf(_:)`
returns the default profile only, with a doc comment noting this will gain the remote-override
check when `BrainPolicy` is ported (Phase 3+ territory) — this is a real, temporary behavioral
gap versus Android (remote recalibration doesn't work yet), stated honestly rather than silently
building a fake override hook now.

`gaussianLikelihood(observed:expected:sigma:)`, `observedPayerSignal(_:)`,
`observedChurnSignal(_:)` (both step functions over `PlayerState` fields), `estimate(_:) ->
[LatentPlayerType: Double]` (the support-gated naive-Bayes product, damped and floored),
`activeSignals(_:) -> [String]` (debug/diagnostic string list — kept since Android keeps it for
exactly that purpose, and it's cheap, pure, and testable). All four safety constants
(`MIN_REWARDED_SUPPORT = 3`, `MIN_SESSION_SUPPORT = 2`, `DAMPING = 0.25`,
`MIN_LIKELIHOOD = 0.02`) and all four Gaussian sigmas copied exactly.

### 4.5 `BanditPolicy`

`public final class BanditPolicy` (reference type — arms dictionary is genuinely mutable shared
state across calls, matching Kotlin's `class` with a `MutableMap` field). Private `BetaParams`
struct (`alpha`/`beta`, both default `1.0` — uniform Beta prior). `arms: [String: BetaParams]`
keyed by `"\(segmentKey)|\(armId)"`, protected by an `NSLock` (matching `ConsentState`/`AdsBrain`'s
established pattern for plain in-memory mutable state — not an actor, since every operation here
is synchronous CPU-bound math with no I/O, and forcing `async` on pure arithmetic would be the
wrong tradeoff this project has consistently avoided so far).

`public var explorationFloor: Double = 0.03`. `update(segmentKey:armId:reward01:weight:)` —
weight-clamped posterior update (`alpha += w*r`, `beta += w*(1-r)`), no-op if `weight <= 0`.

`selectArm(segmentWeights:candidateArmIds:mcSamples:penalties:randomSource:) -> (armId: String,
propensity: Double)` — the Monte Carlo mixture-sampling core (see §3 for the `randomSource`
addition): for each of `mcSamples` (default 200) rounds, draw one latent segment weighted by
`segmentWeights`, then for every candidate arm draw a Lagrangian-penalized Beta sample FROM THAT
SEGMENT and keep the argmax; tally win counts per arm; mix the resulting empirical distribution
with a uniform distribution at `explorationFloor`; draw the final chosen arm from the MIXED
distribution (not the raw Thompson-Sampling distribution — the mixed one is what gets logged as
propensity, so what's drawn and what's logged must be the same distribution, per the Kotlin
comment's explicit warning about exactly this bug class). Single-candidate short-circuit returns
`(candidateArmIds[0], 1.0)` without touching the RNG.

`serialize() -> String` / `restore(_:)` — `"segmentKey|armId=alpha,beta;..."` round-trip,
fail-safe on malformed input (clears `arms` rather than throwing, matching every other
`deserialize`/`restore` in this codebase's established fail-safe convention).
`observationCount(segmentKey:armId:) -> Double` (`alpha + beta - 2`, floored at 0 — subtracting
the Beta(1,1) prior's implicit two pseudo-observations).

Private Beta/Gamma/Gaussian sampling: `sampleBeta(alpha:beta:randomSource:)` (two Gamma draws,
ratio), `sampleGamma(shape:randomSource:)` (Marsaglia-Tsang, boost-for-shape-under-1 recursion
case included), `gaussian(randomSource:)` (Box-Muller). All three take `inout RandomNumberGenerator`
and are otherwise numerically identical to the Kotlin source, including the boundary handling
(`x + y <= 0` → return 0.5 in `sampleBeta`, matching Kotlin's fallback for a degenerate draw).

## 5. Testing

Deterministic pieces (`PlayerState`, `BeliefState`, `RewardModel`, `LikelihoodEstimator`) get
exact-value tests, same style as every prior phase. `BanditPolicy`'s sampling functions get two
kinds of coverage:

1. **Seeded-RNG exact-value tests** for the private Gamma/Beta/Gaussian helpers and for
   `selectArm` end-to-end, using a small deterministic `RandomNumberGenerator` test double (a
   linear congruential generator is enough — it doesn't need to match Kotlin's `Random` stream,
   since there is no cross-platform parity requirement here the way there was for
   `HoldoutManager`'s hash; it only needs to be *reproducible within Swift*, so the same seed
   always produces the same test assertion).
2. **Statistical/invariant tests** with the real `SystemRandomNumberGenerator`: `selectArm`
   always returns an arm from the candidate set; propensity is always in
   `[explorationFloor / n, 1.0]`; over many `update()` calls with a fixed high reward, the
   winning arm's empirical selection rate trends upward (a coarse convergence check, not an
   exact-value one — bandits are stochastic by design).

Given the size of this port (~625 Kotlin source lines across 5 files, likely 700-900 Swift lines
with doc comments preserved), this phase's plan will decompose into one task per file (5 tasks)
plus a final integration task, each following the same TDD/task-review cycle as every prior
phase — no change to process, only to what's being ported.

## 6. Non-goals carried forward

Everything Phase 1/1.5 already deferred stays deferred (`EventQueue` backoff/dedup and O(n) I/O,
`HoldoutManager` unsynchronized statics, `guardrailEngine` consultation, blocking network path
on decision calls, `AdsBrain.enabled` vs. observability). This phase adds no new deferred item —
everything it scopes, it ships in full, since these five files have no external dependency this
phase can't satisfy.
