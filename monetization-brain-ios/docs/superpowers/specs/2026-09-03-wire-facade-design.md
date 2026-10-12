# Phase 1.5 — Wire Up the Facade (Design)

**Date:** 2026-09-03
**Status:** Approved for planning
**Depends on:** Phase 1 Core Foundation (`docs/superpowers/specs/2026-09-03-core-foundation-design.md`,
PR #1, branch `phase1/core-foundation` — not yet merged to `main` at the time this branch,
`phase1.5/wire-facade`, was cut).

## 1. Why this exists

Phase 1 shipped `AdsBrain` holding a `Configuration` (`remoteConfig`/`eventSink`/`guardrailEngine`)
that nothing ever reads, and `HoldoutManager` fully implemented but called from nowhere — every
returned decision's `isHoldout` was hardcoded `false`. This was flagged explicitly in Phase 1's
final review and README ("Known Phase 1 limitations") rather than fixed then, because fixing it
requires a decision this document makes precise: what, exactly, is honest to wire up before a real
decision engine exists.

## 2. Scope

Exactly what was approved when this phase was chosen over starting Phase 2 (bandit engine) or
IAP: real event emission through `eventSink`, real `HoldoutManager` consultation so `isHoldout`
stops being hardcoded `false`, and a `playerId` on `Configuration` to make both possible.

**Not in scope** (deferred, unchanged from Phase 1's README limitations list): retry
backoff/dedup semantics on `EventQueue`, `EventQueue`'s O(n)-per-operation file I/O,
`HoldoutManager`'s unsynchronized `static var` thread-safety, and `guardrailEngine` consultation
(see §4 for why the last one specifically stays out).

## 3. What gets wired, precisely

- **`AdsBrain.Configuration` gains `playerId: String`** — required (no default; a fabricated
  default like `""` would be a silent wrong-identity bug, matching the reasoning Phase 1 already
  applied to `eventName`'s missing default).
- **`configure(_:)` becomes `async`.** After storing the configuration, it sends one
  `sdk_configured` event through `configuration.eventSink` (awaited, not fire-and-forget — see
  §5 for why everything here is `async` rather than fire-and-forget).
- **`chooseCrossModal(...)` and `chooseAssist(...)` become `async`.** When configured, each:
  1. Computes `isHoldout = HoldoutManager.isHoldout(configuration.playerId)` — a real bucket
     lookup, replacing the hardcoded `false`.
  2. Sends one `sdk_baseline_decision` event through `configuration.eventSink` with `eventData`
     fields `decision_point`, `chosen_action_mode` (= the baseline that's about to be returned),
     `is_holdout` (snake_case, and `userProperty.user_id = configuration.playerId` — see §6).
  3. Returns `CrossModalDecision`/`AssistDecision(action: baseline, isHoldout: isHoldout)` — the
     returned `action` is UNCHANGED from Phase 1 (still always the baseline; this phase does not
     touch decisioning, only observability and holdout bookkeeping).

  When not configured, behavior is byte-identical to Phase 1: `isHoldout: false`, no event (there
  is no `playerId` to attribute an event to, and no `eventSink` to send it through).

- **`shouldOfferIap(...)` is explicitly NOT touched.** It has no `isHoldout` field in its return
  type at all (`Bool`), so "isHoldout stops being hardcoded false" doesn't apply to it, and it
  stays fully synchronous, unwired, exactly as Phase 1 left it.

## 4. Two decisions this document is making explicit

**No fabricated `brain_decision` event.** Android's real `brain_decision` event carries
`chosen_action_probability` and `eligible_action_ids` — fields that only exist once a real bandit
picks among real candidates (Phase 1 non-goal, still true here). Emitting `brain_decision` with
those fields invented or omitted would repeat the exact mistake Phase 1's final review caught
(`SdkEvent`'s invented wire format). Instead, this phase emits a **distinctly-named**
`sdk_baseline_decision` — a Phase-1.5-specific signal that makes no claim to be the Android SDK's
`brain_decision` event, carrying only fields that are honestly known right now. It does not
collide with any name in the backend's `KNOWN_EVENTS` set (`backend/app/normalize.py`); rows land
in storage with `is_known_event: false`, which is correct — it isn't one of the Android SDK's
known event shapes.

**`guardrailEngine` stays unconsulted.** Guardrails exist to filter a bandit's candidate set.
There is no candidate set yet — `chooseCrossModal`/`chooseAssist` return the same baseline
regardless of any guardrail's answer. Calling `guardrailEngine.allows(...)` and then ignoring the
result would look like real wiring while doing nothing; that's a worse failure mode than not
calling it at all, because it's misleading to read. Real guardrail consultation is scoped to
whichever future phase introduces the bandit engine.

## 5. Why `async` instead of fire-and-forget

`EventSink.send(_:)` is `async`. Android's equivalent facade methods are synchronous
(`@Synchronized fun ...(): Boolean`), and its logger fires callbacks without blocking the caller.
A Swift-native equivalent would be `Task { await eventSink.send(event) }` inside a still-synchronous
method — but that makes the emitted-event guarantee untestable deterministically: a test would
have to poll or sleep to observe whether the fire-and-forget `Task` has completed, which is
exactly the kind of flaky test this project's testing discipline (TDD, pristine output, no
sleep-based tests anywhere in Phase 1) rules out.

Making `configure(_:)`, `chooseCrossModal(...)`, and `chooseAssist(...)` `async` instead means a
test can `await` the call and then deterministically assert on what a fake `EventSink` received —
no polling, no timing assumptions. This is a deliberate departure from Android's synchronous
facade, justified by: (a) it's the only way to keep this testable without flakiness, and (b) a
real bandit-backed decision in a future phase is plausibly async anyway (a model lookup, a
segment fetch), so this is forward-compatible rather than a throwaway API shape. `shouldOfferIap`,
`selfCheckReport()`, `enabled`, and `isConfigured()` are unaffected and remain synchronous — only
the three methods that now genuinely await I/O change signature.

## 6. `sdk_baseline_decision` payload shape

```
eventName: "sdk_baseline_decision"
eventData:
  decision_point:     <DecisionPoint.rawValue, e.g. "LEVEL_FAIL">
  chosen_action_mode: <ActionMode.rawValue of the baseline that was returned>
  is_holdout:         <Bool>
userProperty:
  user_id: <configuration.playerId>
  (every other UserProperty field left at its default)
```

`eventData` keys are snake_case, matching this package's own locked convention (`SdkEvent`'s
doc comment, `UserProperty`'s doc comment, and `SdkEventTests.test_wireFormatMatchesBackendContract`)
that everything inside `eventData`/`userProperty` is snake_case even though the envelope's own
top-level keys are camelCase. `decision_point`/`chosen_action_mode`/`is_holdout` specifically
reuse the backend's existing canonical field names for this kind of data (`backend/app/
normalize.py`'s `brain_decision` handling) — not because this event IS `brain_decision` (it
isn't, see §4), but because inventing parallel names for the same semantic fields would be its
own small wire-format fabrication.

`userProperty.user_id` is populated from `configuration.playerId` — the one field this entire
phase exists to make real, so a fabricated-default player id here would defeat the phase's own
purpose (§3 already forbids a default `playerId` on `Configuration` for exactly this reason).
No other `userProperty` field, and no `sequenceId`/`ts` override beyond `SdkEvent`'s own
defaults, is populated — this phase does not yet have a real session/sequence-number source.
`sdk_configured` (§3) uses the same `userProperty.user_id` population and an `eventData` of
just `{"sdk_version": <MonetizationBrainSDK.version>}` (already snake_case as a single key).

## 7. Error handling

Same philosophy Phase 1 established throughout: `eventSink.send(_:)` is non-throwing by protocol
(`EventQueue` already guarantees a failed network send never surfaces to the caller — it just
stays queued for the next flush), so `await configuration.eventSink.send(...)` cannot itself
introduce a new failure path into `AdsBrain`. Nothing in this phase changes that contract.

`emitBaselineDecision(...)` (the shared helper `chooseCrossModal`/`chooseAssist` route through)
is gated on `ConsentState.shared.allowsDecisionLogging()` — currently a no-op (that method always
returns `true` today), but it's the designated policy hook for exactly this kind of emission, and
the call site should exist even while the hook does nothing. `configure(_:)`'s `sdk_configured`
event is NOT gated on it — `allowsDecisionLogging()` is scoped to decision logs specifically
("the log contains a pseudonymous id, the chosen action, and propensity" — `ConsentState`'s own
doc comment), and an SDK-configuration lifecycle event isn't one. Neither `configure(_:)` nor
`emitBaselineDecision(...)` is gated on `AdsBrain.enabled` — "brain features never turn
themselves on" (§3) governs *decisioning*, and this phase adds none; whether a disabled brain
should also suppress observability is an open question this document does not resolve (see
README's "Known Phase 1.5 gaps").

## 8. Testing

- `AdsBrainTests`: extend `Configuration` construction in the existing test helper with a
  `playerId`; add tests asserting (a) `chooseCrossModal`/`chooseAssist` report real `isHoldout`
  for a player id whose bucket is deterministically known (reuse a `HoldoutManagerParityTests`
  vector so the expected `true`/`false` is derived, not guessed), (b) both methods send exactly
  one `sdk_baseline_decision` event with the correct fields when configured, (c) neither sends an
  event nor changes `isHoldout` from Phase 1's `false` when unconfigured, (d) `configure(_:)`
  sends exactly one `sdk_configured` event. A fake `EventSink` test double (actor, `Sendable`)
  captures sent events for inspection — `EventQueueTests`' `FakeEventSender` pattern is the model
  to follow, adapted for `EventSink`'s different (non-throwing) signature.

## 9. Non-goals carried forward unchanged

Everything Phase 1's README already lists as deferred stays deferred: `EventQueue` backoff/dedup
and O(n)-per-operation I/O, `HoldoutManager`'s unsynchronized statics, and (new to this document,
§4) guardrail consultation and any `brain_decision`-shaped event.
