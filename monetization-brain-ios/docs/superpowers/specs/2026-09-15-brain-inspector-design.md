# BrainInspector — design

**Status:** implemented (773 tests).
**Closes:** the "`BrainInspector` (26-field snapshot) and `BrainDebugView` — deliberately not
ported" line in the parity audit.
**Android reference:** `src/main/java/com/app/plugin/brain/BrainInspector.kt` (161 lines) and
`BrainDebugView.kt`.

## §1 What it answers, and what already answered something else

`SelfCheckReport` answers *is the SDK wired up correctly* — a configuration audit, run once at
startup. `BrainInspector` answers *what is this brain deciding, for this player, right now*, and
the answer changes with every decision. The earlier audit treated the first as covering the
second; it does not. A QA engineer holding a device where "the brain isn't doing anything" needs
the kill switch, the holdout verdict, the belief posterior and the last few decisions — none of
which a config audit contains.

## §2 The one rule the whole type obeys

**The inspector never feeds back into a decision.** If it did, behaviour with debugging on would
differ from production, which is the precise failure that makes QA stop trusting the tool and
start distrusting every bug report written with it. Concretely:

- `recordDecision` runs after the response is final, at the end of `decide`.
- History is bounded at 30 records, so a long session cannot leak memory.
- Nothing it does can fail. Android wraps the record in `try/catch` for this reason; the Swift
  version has nothing that throws, which is stated in the comment so the absence reads as
  deliberate rather than forgotten.
- `recordDecision` is `internal`. A host that could write history could produce a dump of
  decisions that never happened, and a bug report nobody can trust is worse than none.

## §3 It does not reach into the engine

Android's inspector reads `engine.guardrails` and `engine.bandit` directly. On iOS both are
private, for the reason `adCapPerDay` already documents: handing those objects out hands out
their SETTERS, and a debug view that can raise a safety cap is not a debug view. So the port added
four read-throughs on `DecisionEngine` — `activePolicyVersion()`, `isPolicyEnabled()`,
`policyApplyReport()`, `explorationFloor()` — and the inspector goes through them.

Belief is the case that is easy to miss, and code review caught it: `beliefFor` returns the LIVE
`BeliefState` that `decide` and `observe` mutate, and `BeliefState.update` is public. An inspector
holding that reference is one line away from being able to move a player's belief without going
through `observe` — the same category of mistake as handing out `guardrails`, just less visible.
So the engine gained `beliefReading(_:)`, four values with no way back to the object, and the
inspector reads that. `beliefFor` stays public: this is about what the inspector holds, not a new
restriction on hosts.

That also fixes a subtler thing. The snapshot reads the policy **the engine was built with**, not
`BrainPolicy.shared`. A dump describing an artifact this engine never applied is the most
confusing possible output for someone debugging a config push, and
`test_snapshotReadsTheEnginesOwnPolicyNotTheSharedOne` fails if that regresses.

## §4 `BrainPolicy.lastApplyReport`

The snapshot needs the report text of the artifact in force, which iOS did not keep. Added as a
lock-guarded field, set only when an artifact is ACCEPTED — Android does the same, and the reason
is worth stating: the field answers "what is running", and a refused artifact is not running. The
rejection reason is not lost; it comes back in that call's `PolicyApplyResult` and in
`RemoteConfigApplyReport.describe()`, which is where someone debugging a failed push looks.

## §5 `totalCandidates`, and a correction I had to make to my own first draft

The first version of this field's doc claimed it fixed an Android bug: that Android derives
`totalCandidates` as `eligible + guardrailsApplied.size`, conflating rule codes with candidates.
**That claim was wrong**, and the test written to prove it proved nothing. Both engines append
exactly one code per blocked candidate (first violation only, then the candidate is dropped), so
the derivation equals the true count on every path reachable today.

What the field actually is: a robustness choice. The derivation stays correct only while two
unrelated facts hold — one code per blocked candidate, and `.noAction` never being blocked by any
guardrail, which is what keeps `decide`'s "guardrails removed everything" fallback from
double-counting. `decide` has the real number in hand, so the record takes it and depends on
neither. The doc and the test now say that instead of the better story.

## §5.5 The holdout contradiction — this one IS an Android bug

`decide` forces a holdout player onto `.balancedHybrid`: they are the control group and are never
personalized. Android's `snapshot` calls `engine.scenarioFor(...)` and
`guardrails.effectiveInterstitialCap(...)` unconditionally. Holdout bucketing is a stable
per-player hash, so a long-lived player with a confidently-learned segment can sit in the holdout
for their whole lifetime — and for exactly that player, Android's dump prints the PERSONALIZED
scenario and cap under a heading that reads "SCENARIO IN FORCE", while the decisions listed lower
in the same dump show `.balancedHybrid`. The dump contradicts itself, and the half a reader is
most likely to trust is the wrong half.

The iOS snapshot resolves the scenario the way `decide` resolves it, asks for the cap of THAT
scenario (`effectiveInterstitialCap(for:)`, a new read-through), reports `scenarioIsFallback` as
false for a holdout player — the scenario is imposed, not a low-confidence fallback — and
annotates the rendered line with "(imposed: holdout control, never personalized)".

`test_forAHoldoutPlayerTheSnapshotShowsTheScenarioTheDecisionUsed` drives the belief until the
personalized scenario differs from the neutral one first, and asserts that outright, because a
fresh player resolves to `.balancedHybrid` anyway and would make the test pass with the fix
deleted. Verified: deleting the holdout branch fails it.

## §6 `format`

Static and pure: a snapshot captured five minutes ago must render exactly as it did then, so
rendering touches nothing live. Labels are English (this port's convention) where Android's are
Vietnamese; the box drawing, the `►` marker on the most likely segment, the `█` posterior bars and
the kill-switch/holdout markers are kept as-is, because a bug report that looks the same on both
platforms is easier to read side by side.

One small addition over Android: when a player has no active signals, the section prints
"(none — nothing about this player is distinctive yet)" instead of an empty heading, because an
empty heading reads like a rendering bug.

Two details worth keeping:

- Numbers go through `String(format:)`. A raw `Double` renders as `0.30000000000000004` often
  enough to make a dump unreadable, and a reader who sees one stops trusting the numbers near it.
- Guardrail lines sort by count descending, ties by name. Swift's dictionary order is fixed by a
  per-PROCESS hash seed, so without the tie-break the same state renders differently between
  launches — which people diff and get false hits from. **This is not test-covered and cannot be,
  in one process:** removing the tie-break does not fail the test, because the dictionary happens
  to iterate alphabetically here. The test pins the intended order; the reasoning carries the
  rest, and the test says so.

## §7 Not ported

`BrainDebugView` — Android's on-screen overlay. It is a UIKit/Compose view, not brain behaviour,
and `format(_:)` produces the text it would display. A host that wants an overlay renders that
string in three lines of its own UI.
