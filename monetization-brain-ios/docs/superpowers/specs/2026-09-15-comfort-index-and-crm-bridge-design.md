# Comfort Index + CRM Bridge — Design Spec

**Goal:** Port two independent Android subsystems, both of which unblock items deferred since
Phase 3.5.

- **`ComfortIndex`** (`brain/ComfortIndex.kt`) — the 4th North Star Metric, a deliberate
  counterweight to ARPDAU/Revenue/Subscription, which are all easy to improve by making the
  player's experience worse.
- **`CrmBridge`** (`crm/CrmBridge.kt`) — a vendor-neutral outbound signal channel. The SDK carries
  no CRM dependency; the host registers a sink and the SDK hands it normalized signals.

Neither has any persistence, so the delimiter-collision class that dominated Phases 8 and 9 is
absent from both by construction. Both are pure in-memory.

---

## 1. Scope

**ComfortIndex** — `Sources/MonetizationBrainCore/Brain/ComfortIndex.swift`
- `ComfortIndex.compute(state:adCapPerDay:) -> ComfortResult` — pure, static, no instance state.
- `ComfortResult { value, coverage, componentsAvailable, componentsMissing, isTrustworthy, describe() }`.
- `policyAggressiveness(engine:) -> [String: Int]` — read-through to `GuardrailEngine`'s prevented counts.
- Wiring: `AdsBrain.onSessionEnd()` emits `brain_comfort`.

**CrmBridge** — `Sources/MonetizationBrainCore/Crm/CrmBridge.swift`
- `CrmSink` protocol (`sinkId`, `deliver(_:) -> Bool`), `EngagementSignal`, `CrmSignalKind`.
- `.shared`, `Configuration { clock }`, `apply(_:)`, `activeVersion()`, `register(_:)`,
  `unregister(sinkId:)`, `emit(_:)`, `emitSegmentChange(...)`, `emitFirstPurchase(...)`,
  internal `resetForTest()`.
- Wiring: `AdsBrain.onPurchase` (first purchase only), and the belief-argmax flip inside `observe`.

**Out of scope:** any vendor CRM SDK; the three ComfortIndex components that have no data source
(§3.1); persisting CRM config or sink registrations across launches (Android doesn't either).

## 2. Deviations From Android

### 2.1 `CrmBridge.enabled` must not fail open — Android's does

Android parses the flag as `root["enabled"] != false`. A missing key, or a wrong-typed one
(`"enabled": "false"`, or a number), is not the Kotlin literal `false`, so it evaluates to **`true`**
— the bridge silently **enables**. That contradicts its own documented default, which is OFF, and
its own stated principle that "with CRM, silence is the safe state." No Android test covers either
case.

This is the same fail-open class already fixed three times here (`PurchaseRouter.web_shop.enabled`,
`PaywallManager`'s variant weight, `SubscriptionLifecycle`'s flags). **Decision:**
`JsonConfigValue.flag(..., defaultingTo: false, ...)` — absent means off, wrong-typed means off
**with a diagnostic note**. CRM signals leave the device and reach a marketing platform; enabling
that channel by accident is not a recoverable mistake.

### 2.2 Sinks are never called while holding the lock

Android's `emit` is `@Synchronized` and calls `CrmSink.deliver(...)` inside the monitor. JVM
monitors are reentrant, so a sink calling back into the bridge is survivable there. **`NSLock` is
not reentrant** — the same shape deadlocks on iOS. `deliver` is host code the SDK cannot vet.

**Decision:** snapshot the eligible sinks under the lock, release it, then deliver. This also stops
a slow sink blocking every other bridge caller. Per-sink exceptions stay isolated: one failing sink
must not stop the others, matching Android.

### 2.3 `clear()` becomes internal `resetForTest()`

Android's is public and wipes the remote-config-derived `config` alongside the registered sinks, so
host code could silently disable the bridge until the next config fetch. Same treatment as every
sibling in this port.

### 2.4 `ComfortIndex.isTrustworthy` is structurally false, and says so

`coverage` is `componentsAvailable / 5`, and only 2 of the 5 components have a data source — so
coverage is permanently `0.4`, and `isTrustworthy` (threshold `0.6`) is permanently `false`. Android
ships this silently; a caller gating on it waits forever for a `true` that cannot arrive.

**Decision:** port it, and make the impossibility explicit in the doc comment **and in a test that
asserts it**. When a third component ever lands, that test fails and forces a conscious decision
rather than a silent behavior change. A dead predicate that announces it is dead is honest; one that
looks alive is a trap.

### 2.5 Nonsensical inputs are clamped, not laundered silently

Android feeds `adImpressions24h = -5` through `coerceIn(0, 1)` into "zero ad fatigue", and a negative
`daysSinceLastSession` into the "active today" bucket — i.e. impossible inputs quietly become
"everything is fine". Its own test asserts only that the output stays in range.

**Decision:** keep the clamping (the output range matters, and there is no better answer), but
report it: `ComfortResult` carries the components it actually used, and the doc states that negative
inputs read as best-case. The metric exists to catch player harm, so the direction of its failure
mode belongs in writing.

### 2.6 `brain_comfort` drops `screen_name`

Consistent with Phases 7–9: Android reads an ambient mutable global that has no iOS counterpart.

## 3. Definitions

### 3.1 ComfortIndex formula (faithful port)

```
fatigue    = clamp(adImpressions24h / max(adCapPerDay, 1), 0...1)
churnProxy = daysSinceLastSession >= 7 ? 0.9
           : daysSinceLastSession >= 3 ? 0.6
           : daysSinceLastSession >= 1 ? 0.3 : 0.1
value      = ((1 - fatigue) + (1 - churnProxy)) / 2        // equal weights
coverage   = 2 / 5
```

Equal weighting is **not** empirically justified — Android's own doc says so. Recorded as a known
limitation rather than presented as a calibrated model. The three absent components stay named in
`componentsMissing` so the gap is visible in the event payload rather than implied by a low number.

Guardrail-block frequency is deliberately **not** part of the value: it measures policy health, not
player experience. It is exposed separately through `policyAggressiveness`, and a test asserts that
guardrails firing does not move `value`.

### 3.2 CRM config — key `crm_bridge_ios`

```json
{ "version": "crm_v1", "enabled": true, "sinks": ["braze"] }
```

- `version` — required non-blank; missing rejects the whole config, last-good keeps running.
- `enabled` — optional, **defaults to `false`**, wrong type degrades to `false` with a note (§2.1).
- `sinks` — optional allow-list; absent or empty means every registered sink is eligible. A
  non-string element is dropped with a note (Android drops it silently).

### 3.3 `emit` gate order

Config loaded → `enabled` → **not child-directed** → **`ConsentState.allowsThirdPartySignals()`** →
at least one eligible sink. Child-directed blocks even when consent is granted (COPPA); on iOS
`childDirected` is `Bool?`, and `nil` means "not declared", which does not block — matching
Android's `== true` check.

**The consent gate is `allowsThirdPartySignals()`, not `allowsPersonalization()`, and that is a
deliberate divergence from Android.** Android uses its equivalent of `allowsPersonalization()`,
which passes for `.unknown` — so a freshly-installed app that has never asked for consent, with an
enabled remote CRM config, delivers player signals to a third-party processor by default. Raised by
the Phase 10 security review as the first time this SDK's inherited "unknown-tier default-allow"
policy reaches a channel that leaves the device for someone other than the studio.

`allowsThirdPartySignals()` passes **only `.granted`**:

- `.unknown` is refused although `allowsProfiling()` accepts it. The two are not the same question:
  a local profile built before asking can be discarded, while a signal already delivered to an
  outside processor cannot be recalled. "We had not asked yet" does not survive as a defence for
  having already sent it.
- `.partial` is refused for the reason `allowsProfiling()` already refuses it, applied with more
  force: there is no per-feature masking, so partial consent cannot tell us whether CRM was among
  the features actually consented to. When we cannot tell, the conservative reading is that it was
  not.

Matching Android's weaker gate would have meant copying a privacy default rather than choosing one.

## 4. Events

### `brain_comfort` (new)
`comfort_value` (double), `comfort_coverage` (double), `components_missing` (string, `;`-joined),
`policy_aggressiveness` (string, `key=value;…`).

Not consent-gated: it is an aggregate quality metric carrying no profile and no identifiers beyond
the pseudonymous player id every event already carries.

**CrmBridge emits no `SdkEvent` at all** — its output is `deliver` calls to host-registered sinks,
a separate channel from the backend pipeline. Matching Android.

## 5. Testing Requirements

- The exact arithmetic of `value` for known inputs — Android tests only direction and bounds.
- Churn-proxy bucket boundaries at exactly 1, 3 and 7 days — untested on Android.
- `coverage == 0.4` and `isTrustworthy == false`, asserted so a future component forces a decision (§2.4).
- Negative/impossible inputs stay in range and read as best-case (§2.5).
- Guardrails firing does not change `value`.
- CRM: **`enabled` absent ⇒ OFF**, and wrong-typed ⇒ OFF with a note — the Android bug (§2.1).
- CRM: every gate in order, including child-directed blocking a granted consent.
- CRM: a throwing sink does not stop the others; a sink that re-enters the bridge does not deadlock (§2.2).
- CRM: `unregister` — completely untested on Android.
- CRM: `sinks` allow-list filtering, and a non-string element dropped with a note.
