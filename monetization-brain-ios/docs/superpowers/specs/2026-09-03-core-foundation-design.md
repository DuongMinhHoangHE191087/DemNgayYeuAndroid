# MonetizationBrainIOS — Phase 1: Core Foundation (Design)

**Date:** 2026-09-03
**Status:** Approved for planning
**Source of truth (Android):** `appplugin` repo, branch `feat/monetization-brain`, `DOC/` (esp. docs 06, 13, 25, 26, 38, 46), `src/main/java/com/app/plugin/{consent,brain}`.

## 1. Why a separate repo, and why this scope

`appplugin` (Android) is not a small SDK — it is a Player Decision Platform with ~59 design
docs and roughly 20 files under `com.app.plugin.brain` alone (bandit engine, guardrails,
holdout, reward model, decision logging), plus IAP, paywall, subscription lifecycle, battle
pass, offerwall, CRM bridge and server-side OPE built on top. Porting all of that to iOS in one
pass is not a single implementation unit — it is a multi-phase program, mirroring the order the
Android roadmap itself went through (doc 07/23: rules → segmentation → propensity → bandit →
guardrails → paywall → IAP → subscription → battle pass → offerwall → cross-modal → CRM → OPE).

This repo (`monetization-brain-ios`) exists specifically so iOS work never gets mixed into the
Android repo's history, branches, or build. This document specs **only the first slice**: the
foundation every later phase depends on. Everything else is deliberately out of scope here and
will get its own spec when its turn comes.

## 2. Goals

- Stand up a Swift Package that can be added to an iOS app today, with a real (if inert) public
  API surface: `AdsBrain.configure(...)`, decision-point stubs, a self-check report.
- Port the pieces of Android's foundation layer that are pure logic and therefore portable
  1:1 without waiting on a real bandit implementation: `ConsentState`, `HoldoutManager`
  (bucket hashing).
- Establish the event pipeline shape (queue → durable store → HTTP send) that every future
  phase (IAP, paywall, brain decisions) will publish through, matching the backend contract
  that is explicitly stack-independent (Android doc 46).
- Keep the core package dependency-free. Anything that pulls in a third-party SDK (Google UMP,
  Firebase) is a separate, optional SPM product.
- Lock cross-platform parity early where it's cheap to get wrong forever: the holdout bucket
  hash (salt + user id → bucket) must produce byte-identical results to the Android/Python
  implementations, verified by a shared vector fixture.

## 3. Non-goals (future phases, each gets its own spec)

- Real bandit / decision engine (`BanditPolicy`, `BeliefState`, `DecisionEngine`, `RewardModel`)
  — Phase 2.
- IAP / StoreKit 2, entitlements, FX rates — Phase 3.
- Paywall (`paywallFor`), Purchase Router — Phase 4.
- Subscription lifecycle, battle pass, recurring grants, offerwall, cross-modal wiring, CRM
  bridge, experiment exposure, server-side OPE — later phases, order to be decided when Phase 1
  ships.
- A real Firebase Remote Config adapter and a real Google UMP consent flow — stubbed behind
  protocols in Phase 1, concrete adapters ship as optional targets later.
- A dedicated iOS backend — none needed. The backend contract (doc 46) is explicitly
  stack-independent; this SDK talks to the same event contract the Android SDK does.

## 4. Architecture

Two SPM products in one package:

```
monetization-brain-ios/
  Package.swift
  Sources/
    MonetizationBrainCore/
      Consent/
        ConsentTier.swift          // enum: unknown, denied, partial, granted
        ConsentState.swift         // pure, thread-safe, listener-based — no platform import
      Config/
        RemoteConfigProvider.swift // protocol
        StaticRemoteConfigProvider.swift // in-memory/JSON impl, only concrete impl in Phase 1
      Events/
        SdkEvent.swift             // Codable event envelope matching doc 46 contract
        EventQueue.swift           // actor: durable on-disk queue, batching, dedup key
        EventSink.swift            // protocol future producers (IAP, paywall, brain) publish through
        HTTPEventSender.swift      // URLSession-based sender: retry + backoff
      Brain/
        AdsBrain.swift             // public facade — single entry point
        GuardrailEngine.swift      // protocol + pass-through default (always allows)
        HoldoutManager.swift       // real FNV-1a bucket hash, ported now
        DecisionPoint.swift        // enum mirroring Android's decision points
        SelfCheckReport.swift      // diagnostics, mirrors Android's self-check habit
    MonetizationBrainUMP/          // optional target
      ConsentManager.swift         // wraps Google UMP iOS SDK, depends on GoogleUserMessagingPlatform
  Tests/
    MonetizationBrainCoreTests/
      ConsentStateTests.swift
      HoldoutManagerParityTests.swift   // vectors shared with Android's HoldoutParityVectorsTest
      EventQueueTests.swift
      AdsBrainTests.swift
  docs/superpowers/specs/            // this file and future phase specs
  README.md
  .gitignore
```

`MonetizationBrainCore` has zero third-party dependencies. `MonetizationBrainUMP` is an
independent product an app opts into only if it wants this package's UMP wrapper instead of
wiring UMP itself. Firebase Remote Config gets the same treatment in a later phase
(`MonetizationBrainFirebaseConfig` or similar) rather than being added to Core.

## 5. Components

### 5.1 Consent (`ConsentState`, `ConsentTier`)

Direct port of `com.app.plugin.consent.ConsentState`. Four tiers — `unknown`, `denied`,
`partial`, `granted` — with `unknown` carrying the same meaning it does on Android: nobody has
asked yet, which is not the same thing as a refusal, and must not be conflated into either
`granted` or `denied`. Thread-safe (Swift actor or lock-protected class), listener-based so
tier changes are a first-class event (cached profiles must react immediately when consent is
withdrawn). Same permission surface as Android: `allowsProfiling()`, `allowsPersonalization()`,
`allowsGuardrails()` (always true), `allowsDecisionLogging()` (always true, no PII in the log).
`childDirected` / `underAgeOfConsent` as optional Bools, `nil` meaning "the host app hasn't
declared this" — the SDK never asserts a legal claim on the app's behalf.

`ConsentManager` (in the `MonetizationBrainUMP` product) is a thin wrapper translating Google
UMP iOS SDK results into `ConsentTier` — same boundary Android draws: policy logic lives in
`ConsentState` and is unit-testable without any platform dependency; `ConsentManager` only runs
the UMP flow and translates its result. Not auto-invoked by `AdsBrain.configure()` — the host
app calls it from a view controller at a time of its choosing, for the same reason Android
documents: UMP can show a full-screen dialog and must not ambush the app's own splash screen.

### 5.2 Remote config (`RemoteConfigProvider`)

A protocol (`func string(forKey: String) -> String?`, `func fetchAndActivate() async`) with a
single concrete implementation in Phase 1: `StaticRemoteConfigProvider`, backed by an in-memory
dictionary an app can seed from its own JSON/plist. No Firebase dependency in Core — this
matches the Android finding that `com.app.plugin.brain` never imports Firebase directly; only a
separate `firbase` adapter package does. A real Firebase-backed provider is a future optional
target, not part of this phase.

### 5.3 Event pipeline (`SdkEvent`, `EventQueue`, `EventSink`, `HTTPEventSender`)

Mirrors `DecisionLogQueue` + `DecisionLogger`. `SdkEvent` is a `Codable` envelope (event name,
timestamp, payload dictionary, optional dedup key) shaped to match the backend contract that
Android doc 46 specifies as independent of client stack — so events this SDK sends land in the
same ingestion pipeline the Android SDK already uses. `EventQueue` is an actor that persists
events to disk (FileManager-backed JSON) before attempting to send, so a killed app doesn't lose
queued events; it batches and retries with backoff through `HTTPEventSender` (URLSession).
`EventSink` is the protocol future producers (IAP, paywall, brain decisions) will publish
through — defined now, even though Phase 1 only emits lifecycle/debug events, so later phases
don't need to touch this layer's shape.

### 5.4 `AdsBrain` facade

The single public entry point, mirroring the Android facade pattern. Phase 1 surface:
`configure(config:)`, an `enabled` flag defaulting to **false** (matches Android doc 38's
"mặc định TẮT" principle — brain features never turn themselves on), `selfCheckReport()` for
diagnostics, and decision-point stub methods (`shouldOfferIap`, `chooseCrossModal`,
`chooseAssist`) that always return the safe baseline / `.noAction` result. This lets a host app
wire up the integration shape today — call sites, guardrail checks, holdout membership — without
the SDK pretending to have real intelligence before Phase 2 lands.

### 5.5 Guardrail skeleton (`GuardrailEngine`)

A protocol plus a pass-through default implementation that always allows. Shaped like Android's
`GuardrailEngine` (kill-switch check, rollout/holdout check) but with no real rules yet — those
arrive with the bandit engine in Phase 2.

### 5.6 `HoldoutManager`

Ported for real in Phase 1, not stubbed — it's pure deterministic hashing (FNV-1a over salt +
user id → bucket), cheap to get right now and expensive to get inconsistent later, since every
future phase (paywall holdout, brain rollout, CRM `isHoldout` flag, experiment exposure) shares
one namespace (`brain_holdout_v1` on Android). Ships with a vector-based parity test using the
same constant test vectors as Android's `HoldoutParityVectorsTest` and Python's
`test_reconcile.py`, so "same salt + same user id → same bucket" is verified across all three
implementations from day one, not discovered as a bug after three platforms disagree.

## 6. Data flow (Phase 1)

```
App calls AdsBrain.configure(config:)
    ↓
RemoteConfigProvider seeded (static, Phase 1) — no network fetch yet
    ↓
ConsentState starts at .unknown; app later calls ConsentManager.request(...) from its own VC
    ↓
App calls a decision-point stub (e.g. shouldOfferIap) →
    GuardrailEngine.allows() (always true, Phase 1) →
    HoldoutManager bucket lookup (real, deterministic) →
    stub returns baseline / .noAction
    ↓
AdsBrain logs an SdkEvent through EventQueue →
    persisted to disk →
    HTTPEventSender attempts send, retries with backoff on failure
```

## 7. Error handling

Same philosophy Android holds throughout: broken/missing config keeps the last-known-good state
rather than crashing or silently defaulting to something unsafe; listener callbacks are
try/caught so one bad listener can't break the notification chain; the event pipeline degrades
to local retry, never silent data loss, and never blocks the calling app; everything defaults to
the safe/off state (`enabled = false`, guardrails allow, decisions return baseline) rather than
throwing across the SDK/app boundary.

## 8. Testing

XCTest, one test file per component, mirroring the Android test-per-file convention under
`src/test/java/com/app/plugin/{consent,brain}`. Required coverage for Phase 1 sign-off:

- `ConsentStateTests` — tier transitions, permission surface, listener notification and
  listener-failure isolation.
- `HoldoutManagerParityTests` — the shared cross-platform vector fixture; this is the one test
  in this phase that must never be "close enough."
- `EventQueueTests` — persistence survives process restart (simulated), retry/backoff behavior,
  dedup key handling.
- `AdsBrainTests` — `enabled` defaults false, stub decision points always return baseline,
  `selfCheckReport()` reflects unconfigured/misconfigured state honestly.

## 9. Repo / project setup

- GitHub: `dungduong2899/monetization-brain-ios` (private, created, separate from `appplugin`).
- Local: `/Users/dungdm/Documents/SDK/monetization-brain-ios` — sibling directory to
  `appplugin`, not nested inside it.
- Swift Package Manager only, iOS 15+ deployment target, Swift tools 5.9+.
- `.gitignore`: standard Swift/Xcode (`.build/`, `.swiftpm/`, `xcuserdata/`, `DerivedData/`).

## 10. Open items carried to later phases

- Which order Phase 2+ ships in (bandit engine vs IAP vs paywall) is not decided here —
  Android's own history did bandit-core before IAP, but the value-fastest path for iOS may
  differ; revisit when Phase 1 is done.
- Firebase Remote Config and Google UMP concrete adapters are named but not built in this
  phase.
- No decision yet on whether iOS event schema versions independently from Android's or shares
  one version number — deferred until the first real event beyond lifecycle/debug ships.
