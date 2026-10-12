# Remote Config dispatcher + Firebase adapter — design

**Status:** implemented (751 tests).
**Closes:** the "No Firebase Remote Config adapter" parity gap from the Android audit.
**Android reference:** `src/main/java/com/app/plugin/firbase/FirHelperConfig.kt`,
`parserCfWithRemoteCf()`; and `DecisionEngine.applyPolicy` in `brain/DecisionEngine.kt`.

## §1 The gap, stated precisely

Eight subsystems on iOS expose `apply(_ json:)` — `BrainPolicy`, `PaywallManager`,
`PurchaseRouter`, `SubscriptionLifecycle`, `BattlePass`, `RecurringGrants`, `OfferwallManager`,
`CrmBridge`. Every one of them was fully ported and fully tested, and **nothing called any of
them**. A remote-config artifact could not reach the SDK unless the host wrote the fan-out
itself, which meant the entire config surface — every cap, every season, every kill switch — was
inert on iOS.

Android's `parserCfWithRemoteCf` reads twelve keys: four belong to the ad layer
(`ads_region_android`, `ads_region2_android`, `cf_ids_placement`, `cf_ads_placement`) and are out
of scope here because the ad layer is not ported; the other eight are exactly the eight `apply()`
entry points above. This phase ports those eight.

## §2 Where the dispatcher lives, and why it imports nothing

`RemoteConfigDispatcher` sits in `MonetizationBrainCore/Config/` and talks only to
`RemoteConfigProvider`. It has no Firebase import, and neither does anything else in Core — the
same rule Android's own `com.app.plugin.brain` follows.

Three things fall out of that:

1. A host with its own config backend conforms to a two-method protocol and gets the identical
   dispatch. Firebase is a supported backend, not a requirement.
2. The entire eight-way fan-out is testable against a dictionary. No network, no emulator.
3. The Firebase dependency is confined to one target that a host can simply not link.

## §3 Keys and order

| Order | Key | Applied to |
|---|---|---|
| 1 | `brain_policy_ios` | `DecisionEngine.applyPolicy` |
| 2 | `paywall_config_ios` | `PaywallManager` |
| 3 | `purchase_routing_ios` | `PurchaseRouter` |
| 4 | `subscription_lifecycle_ios` | `SubscriptionLifecycle` |
| 5 | `battle_pass_ios` | `BattlePass` |
| 6 | `recurring_grants_ios` | `RecurringGrants` |
| 7 | `offerwall_config_ios` | `OfferwallManager` |
| 8 | `crm_bridge_ios` | `CrmBridge` |

The order is Android's, kept so an artifact set pushed to both platforms lands in the same
sequence. The `_ios` suffix is not cosmetic: a key shared with Android would mean an iOS-only cap
change silently re-tuning the Android fleet.

**Why eight keys rather than one blob** (Android states this at each key, and it is worth keeping):
these artifacts change on different clocks — paywall per campaign, brain policy per training
cycle, routing per store-fee terms, battle pass per season, CRM bridge per vendor contract. One
blob would force a re-push of all eight to edit one, and a single malformed field would take down
all eight at once. With eight keys, a bad artifact costs exactly its own subsystem's update.

**Empty means absent.** Android guards every key with `isNotEmpty`; an unset Remote Config
parameter reads back as `""`, and handing that to a parser would log a rejection for a key nobody
configured. The dispatcher reports it as `.absent`, and the Firebase provider maps `""` to `nil`
at the vendor boundary as well, so the guard is a second line of defence rather than the only one.

## §4 `DecisionEngine.applyPolicy` — a second gap found on the way

The brain artifact could not be dispatched like the other seven, because `BrainPolicy.apply`
needs a `GuardrailEngine` and `DecisionEngine.guardrails` is private on purpose: handing the
object out would hand out its setters, letting code outside the package raise a safety cap it is
not allowed to raise. Android solves this with `DecisionEngine.applyPolicy(json)`; iOS had no
equivalent. It does now, and it is public for the same reason Android's is — an integrator may
load artifacts from their own source.

Porting it surfaced a live bug. Android's `applyPolicy` also pushes the artifact's
`exploration_floor` into `BanditPolicy`.

> **Correction (added after code review).** An earlier draft of this section said this ports
> "the path Android's Remote Config flow uses". It does not, and the difference matters.
> `FirHelperConfig.parserCfWithRemoteCf` calls `BrainPolicy.apply(json,
> DecisionEngine.instance.guardrails)` **directly**; `DecisionEngine.applyPolicy` has no
> production caller anywhere in `appplugin/src/main/java`, and the Kotlin method's own doc
> comment — "the default Remote Config flow already calls it" — is stale. The consequence is a
> genuine cross-platform divergence, kept on purpose: **for the same artifact field, iOS re-tunes
> the bandit's exploration floor and Android does not.** Reproducing the Android behaviour would
> mean shipping a config field that is accepted, reported as applied, and ignored — a trap for
> whoever sets it. Off-policy analysis that mixes the two platforms must segment by platform,
> the same caveat already recorded for `sequenceId`. On iOS that value was parsed, clamped, exposed as
`BrainPolicy.pendingExplorationFloor` — and dropped, with a doc comment asserting that
`BanditPolicy` had no equivalent knob. It has had `explorationFloor` since Phase 2. So
`exploration_floor` was a dead field: an operator could set it, see it accepted in the report, and
change nothing.

The fix required one API change. `pendingExplorationFloor` was a non-optional `Double` defaulting
to 0.05; `BanditPolicy.explorationFloor` defaults to 0.03. Pushing a non-optional on every apply
would mean that a paywall-only artifact — which never mentions exploration — silently re-tunes
exploration, and with it every logged propensity, and with that the validity of off-policy
evaluation. Android models it as `Double? = null` and pushes only on non-null. So does this port
now. `test_anArtifactWithoutAnExplorationFloorLeavesTheBanditAlone` pins it, and the mutation
(pushing `?? 0.05`) fails it.

`BanditPolicy.explorationFloor` also became lock-guarded in the process: `applyPolicy` writes it
from whatever thread delivered the config while `selectArm` reads it on a decision path. That
read happens after `selectArm` releases the bandit lock, so the accessor never nests inside it.

## §5 `refresh` fetches before it reads

Reading the cache first and fetching afterwards would apply the *previous* session's values and
hold the new ones until the next launch — a config push that appears to take a day to roll out.
`test_refreshFetchesBeforeReadingSoAColdStartSeesTheNewValues` uses a provider whose values only
appear after `fetchAndActivate()`, so the ordering is asserted rather than assumed.

A **failed** fetch is deliberately not an error path. Remote Config keeps serving the last
activated values, so dispatching against the cache is correct; skipping the dispatch would leave
subsystems on compiled defaults even though a good config was already on the device. The Firebase
provider keeps the error in `lastFetchError` instead of swallowing it silently.

There is no retry and no scheduling here. `fetchAndActivate` already owns the fetch policy
(Firebase's minimum-fetch-interval throttle); a retry loop layered on top would fight it and burn
the fetch quota.

## §6 The Firebase target

`MonetizationBrainFirebase` follows the `MonetizationBrainUMP` / `MonetizationBrainStoreKit`
pattern: one target, one dependency, one file. `FirebaseRemoteConfigProvider` fetches, activates,
and reads a key. The minimum fetch interval is Android's pair of numbers for Android's stated
reason — 10s in `DEBUG`, 3600s in release, because shipping the debug interval blows through the
fetch quota and gets the app throttled, which looks exactly like "config stopped updating".

**The dependency cost is real and is stated here rather than discovered later.** SwiftPM resolves
every package in the graph, so adding `firebase-ios-sdk` makes *every* consumer of this package —
including one that only wants Core — fetch Firebase's binary artifacts (Firestore, gRPC, abseil:
~144 MB of checkouts). Three things made that acceptable: this SDK's consumers are Firebase apps
already, so SwiftPM dedupes the dependency they have anyway; the unused libraries are resolved,
not linked, so the app binary is unaffected; and the alternative — a nested package — cannot be
referenced by URL from a remote repo, which would make the adapter effectively unusable. A host
that wants none of it can copy the single provider file into its own app, which is why that file
uses no private API and depends on nothing else in its target.

The version range is `11.0.0 ..< 13.0.0`, wider than the usual `from:`, because `from: "11.0.0"`
caps at major 11 and would fail resolution for any consumer already on Firebase 12. Both ends were
built here: 11.15.0 and 12.19.1 compile warning-free.

## §7 Startup order for a host

```swift
FirebaseApp.configure()
let provider = FirebaseRemoteConfigProvider()
await MonetizationBrainSDK.configure(.init(eventSink: sink, remoteConfig: provider, playerId: id))
let report = await RemoteConfigDispatcher.shared.refresh(from: provider)
```

**Dispatch comes after `configure`, and it is not folded into it.** `configure` sets
`AdsBrain.rolloutFraction` from the host's compiled config, while the brain artifact's
`experiment.rollout_fraction` sets the same knob. One of them has to win, and it should be the
artifact: it is the operator's live override, the thing that can be changed without a release.
Running the dispatch after `configure` gives exactly that, and folding it into `configure` would
have inverted it. This also matches Android, where `FirHelperConfig` is a separate step from
brain configuration.

## §7.5 Concurrency: serialised per policy, never atomic across the eight

`DecisionEngine.applyPolicy` holds an `applyLock` of its own for the whole sequence (parse and
store under `BrainPolicy`'s lock, read the floor back, write it to the bandit). Without it, two
overlapping applies can leave the bandit running artifact A's floor while
`BrainPolicy.activeVersion()` reports artifact B — both values clamped and legitimate, just not
from the same artifact, and the floor is what every logged propensity is built on. The lock is
touched by nothing else, so it never blocks a decision path even though it is held across
`BrainPolicy`'s store writes.

**The lock stands on this reasoning, not on a test, and that is worth stating.** The test written
to prove it (`test_concurrentAppliesNeverLeaveTheFloorAndVersionMismatched`) does not fail when
the lock is removed: the window is sub-microsecond and `BrainPolicy` is `final`, so there is no
seam to widen it without a test-only hook in production code. The test is kept as a deadlock and
invariant check, labelled as such — a concurrency test with no engineered overlap that passes
either way is worse than none, because it looks like coverage.

**A dispatch is not atomic across the eight subsystems**, and nothing here makes it so. Each key
is applied independently by design (§3); a host that runs two refreshes concurrently can land
key 1 from one payload and key 8 from the other. Remote Config activates a whole payload at once,
so this only arises from overlapping `refresh` calls — a host that wants all-or-nothing should
single-flight its own refresh.

## §8 What this does not cover

- The four ad-layer keys, which belong to the unported ad layer (§1).
- Firestore as a second config source. Android falls back to a `ads_config` Firestore document;
  every key it reads there is an ad-layer key, so there is nothing for the brain to read.
- `setDefaults` / in-app default values. `RemoteConfigDispatcher.keys` is public so a host can
  seed console defaults, but the SDK ships no default artifact: an absent key must mean "keep the
  compiled behaviour", not "apply a default someone forgot about".
