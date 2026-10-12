# Recurring Grants — Design Spec

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:writing-plans to turn this into an
> implementation plan, then superpowers:subagent-driven-development to execute it.

**Goal:** Port Android's `RecurringGrants` (`com.app.plugin.iap.RecurringGrants`, `DOC/53`) — the
layer that answers "this player owns a pass/subscription; is a periodic reward due, and what is
it?" The SDK never touches the game's economy: it decides eligibility and timing and hands back an
opaque `(rewardType, rewardAmount)` for the host app to grant.

This is the subsystem Phase 7 unblocked — its ownership test depends on subscription state.

**Architecture:** A `RecurringGrants` singleton (`.shared`, matching every other facade here) holds
NSLock-protected config plus a per-grant-line map of last-claim timestamps, persisted through
`BrainKeyValueStore`. `dueGrants(...)` is a pure query; `claim(...)` is the one mutating, `async`
entry point (async because it emits an event and this package never fires events from a detached
task). Ownership is the OR of `Entitlements.isActive` and
`SubscriptionLifecycle.stateFor(_:).isEntitled` — see §3.1.

**Tech Stack:** Swift Package Manager, `MonetizationBrainCore`, `Foundation.JSONSerialization`,
`NSLock`, XCTest.

---

## 1. Scope

**In scope:**
- `RecurringGrants`: `.shared`, `Configuration { eventSink, playerId, store, clock, entitlements,
  subscriptionLifecycle }`, `configure(_:)`, `activeVersion()`, internal `resetForTest()`.
- `apply(_ json: String?) -> RecurringGrantsApplyResult` — config parsing per §4.
- `dueGrants() -> [RecurringGrant]` — pure query, no side effects.
- `claim(productId:rewardType:) async -> RecurringGrantClaimResult` — the one mutating API.
- Models in `Iap/RecurringGrantModels.swift`: `RecurringGrant`, `RecurringGrantClaimResult`,
  `RecurringGrantsApplyResult`.
- **A new `BrainClock` seam** (`Support/BrainClock.swift`) — see §3.3. Genuinely new shared infrastructure,
  not reuse.
- One new event: `recurring_grant_claim` (§5).

**Out of scope:**
- **Granting anything.** The SDK returns `(rewardType, rewardAmount)`; the host app's economy code
  grants it. Same boundary as `PaywallManager` and `IapHelper.purchase`.
- **Server-authoritative timing.** Everything here is device-local wall clock. §3.4 states what
  that does and does not guarantee, because it is a real limit, not a detail.
- **Catch-up / stacking.** Deliberately preserved from Android — see §3.5.
- **Migrating `SubscriptionLifecycle` to `JsonConfigValue`.** Tracked in that file's migration note;
  a different work stream's file.
- **Retrofitting the `BrainClock` seam onto existing callers** (`AdsBrain.nowMs`, `DecisionEngine`,
  `ClickFraudGuard`'s `nowMs:` parameter, `SdkEvent.ts`). This phase introduces the seam and uses
  it; sweeping the package is separate work with its own regression surface.

## 2. Dependencies On Existing Code

- `SubscriptionLifecycle.stateFor(_:)` + `SubscriptionState.isEntitled` (Phase 7) — ownership.
- `Entitlements.isActive(_:)` (Phase 4) — the other ownership source.
- `JsonConfigValue.flag` / `.isJsonBoolean` (hardening pass) — boolean parsing. **Copy
  `PurchaseRouter.apply`/`PaywallManager.apply` as the parser template, NOT
  `SubscriptionLifecycle.apply`**, which still carries a private pre-migration duplicate.
- `BrainKeyValueStore` — persistence.
- `EventSink`/`SdkEvent` — the claim event. `sequenceId` is assigned by `EventQueue`; never set it
  at a call site.

## 3. Deviations From Android (with rationale)

### 3.1 Ownership is `isEntitled`, not `== .active` — Android silently cuts off paying customers

Android:
```kotlin
Entitlements.isActive(productId) || SubscriptionLifecycle.stateFor(productId) == SubState.ACTIVE
```

That literal equality means a subscriber who turns off auto-renew **stops receiving their rewards
immediately**, despite having already paid through the end of the period — and a subscriber in a
billing grace period stops too. Android's own `SubscriptionLifecycle` documents both states as
still entitled (`SubscriptionLifecycle.kt:31-32`), so the ownership check contradicts the class it
queries. Nothing in Android's test suite covers either state; only `LAPSED` is tested.

**Decision:** `SubscriptionLifecycle.stateFor(productId).isEntitled || Entitlements.isActive(productId)`.

`isEntitled` exists precisely for this question (Phase 7, `SubscriptionModels.swift`) and covers
`.active`, `.cancelled` and `.inGracePeriod`. `.inBillingRetry` is correctly excluded — it is
recoverable but NOT entitled, and a player whose payment has actually failed should not keep
drawing rewards while Apple retries. Tests must cover all seven states explicitly, including the
two Android leaves untested.

### 3.2 Persistence must round-trip — Android's does not, and the failure is exploitable

Android builds a composite key and then joins entries with the same delimiter the key contains:

```kotlin
private const val SEP = "|"
private fun keyOf(g: Grant) = "${g.productId}|${g.rewardType}|${g.periodHours}"
persist: lastClaim.entries.joinToString(SEP) { "${it.key}:${it.value}" }
loadLast: for (part in stored.split(SEP)) { val i = part.lastIndexOf(':'); ... out[part.substring(0, i)] = ts }
```

Verified by simulation:

```
written  : ['vip_pass|gem|24', 'vip_pass|ticket|168']
restored : ['168', '24']
```

The restored keys never match `keyOf(g)` again, so `lastClaim[key] ?: 0L` returns `0` and **every
grant is due again after every process restart**. A player force-quits and relaunches to farm
unlimited rewards — no clock manipulation required. Android has no persistence round-trip test,
which is why this survives.

**Decision:** two independent defenses, because either alone is a single point of failure.

1. **Validate at parse time.** A `product_id` or `reward_type` containing `|` or `:` is rejected
   with a diagnostic note and the line is dropped. App Store product identifiers are limited to
   alphanumerics, underscores and periods, so this costs nothing real and converts an encoding
   hazard into an operator-visible config error. Fail-closed: a dropped line grants nothing.
2. **A round-trip test is mandatory** — persist, construct a fresh instance over the same store,
   and assert the claim timestamps are recovered under keys that still match. This is the test
   whose absence let the Android bug ship, so it is called out in §8 rather than left to judgement.

### 3.3 A `BrainClock` seam is introduced, because none exists

There is no clock abstraction anywhere in this package. Every site calls `Date()` directly; the
only precedent is `ClickFraudGuard`'s per-method `nowMs: Int64? = nil` parameter. A subsystem whose
entire behavior is "has the period elapsed" cannot be tested deterministically without one, and
scattering `nowMs:` across every entry point would spread the problem rather than solve it.

**Decision:** add a minimal seam, modelled on `BrainKeyValueStore` — the package's existing
precedent for an injected dependency with a real default and an in-memory test double:

```swift
public protocol BrainClock: Sendable {
    /// Milliseconds since the Unix epoch, matching this package's existing time convention
    /// (`IapPurchase.purchaseTimeMs`, `SdkEvent.ts`) — never a `Date`.
    func nowMs() -> Int64
}

public struct SystemClock: BrainClock {
    public init() {}
    public func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
```

**Named `BrainClock`, not `Clock`**, matching `BrainKeyValueStore` — the package's other injected
seam. The Swift standard library declares its own `Clock` protocol (SE-0329), so the bare name is
ambiguous in any file importing both, and the test file had to alias around it immediately. This
package already carries one such shadowing trap (`SubscriptionRenewalState` vs. StoreKit's
typealias, documented in the Phase 7 spec §7 because it could not be renamed cheaply). Accepting a
second one when nothing yet depends on the name would be choosing the trap on purpose.

Injected via `Configuration` with a `SystemClock()` default, so no call site changes. It lives in a
new `Support/` directory rather than `Brain/` (where `BrainKeyValueStore` happens to sit) because
it is not brain-specific and filing it there would mislead the next reader.

`dueGrants()`/`claim(...)` take **no** `ts` parameter, unlike Android's `ts: Long = clock()`
defaults. A public timestamp parameter is an injection point for exactly the manipulation §3.4
describes, and the test seam belongs in the injected clock, not in the public API.

### 3.4 Wall-clock timing is kept, and the exploit is documented rather than half-mitigated

Android trusts `System.currentTimeMillis()` with no sanity check, and stores the same untrusted
value as the next period's baseline. The consequence is not the "self-punishing" one it looks like:
because each claim rewrites the baseline **forward**, a player can claim, advance the device clock
by one period, claim again, and repeat without bound. The cost to them is only a wrong clock.

Two mitigations were considered and rejected:
- **Reject backwards clock movement** (track the high-water mark): punishes honest users on every
  DST shift and NTP correction, and does not stop the forward-advance loop at all.
- **Pair with a monotonic reading**: does not survive a relaunch, so it detects only in-session
  jumps — partial cover for a full-session attack.

**Decision:** keep wall clock, and state the limit plainly in the public doc comment and in §7.
Neither mitigation prevents the attack; both add real failure modes for honest users in exchange
for slowing an attacker down. This is consistent with where this SDK already draws the line —
`Entitlements`' own doc comment says its client-side state must not be used to grant items of
redeemable value until server-side receipt validation exists, and this feature is precisely such a
grant. The honest fix is server-authoritative timing, which is a backend project.

The host app must understand: **these rewards are not fraud-proof.** Fine for cosmetic or soft
currency; not for anything with cash value.

### 3.5 No catch-up — preserved deliberately

Missing five periods grants one reward, not five. This falls out of storing a single scalar
timestamp per line rather than a missed-period counter, and Android frames it as a deliberate
economy decision (catch-up inflates the in-game economy). Preserved, and tested.

### 3.6 `clear()` does not become a public footgun; parse asymmetry is fixed

- Android's `clear()` is public and unguarded, and resets **config** (`enabled` back to `true`,
  `grants` emptied) as well as claim history — so host code can silently re-enable a feature ops
  disabled. This port exposes internal `resetForTest()`, matching every sibling here after the
  hardening pass.
- Android rejects the entire config for one malformed `grant` element, but silently defaults a
  wrong-typed `enabled` to `true` with no diagnostic. This port follows the house rule in both
  directions: a malformed element is **dropped with a note** (matching `PaywallManager`'s variant
  handling) while the rest of the config applies, and a wrong-typed `enabled` degrades to its
  default **with a note** via `JsonConfigValue.flag`.

### 3.7 Every path is synchronized

Android's `claim()` and `clear()` are `@Synchronized` while `apply()` and `dueGrants()` are not —
and `dueGrants()` reads the very `lastClaim` map that `claim()` mutates in place under a lock it
does not take. `@Volatile` protects the reference, not the contents. `apply()` additionally assigns
four fields unsynchronized, so a concurrent reload can be read torn.

**Decision:** one `NSLock`, every acquisition through a synchronous private helper, every
multi-field decision in a single acquisition (the TOCTOU rule Phase 6's review established). The
event is built under the same lock snapshot that authorised the claim, then sent after unlocking.

### 3.8 The event drops `screen_name`

Same reasoning as Phase 7: Android reads an ambient global `ScreenName.name`, and no such mutable
global exists here.

## 4. Type Definitions

```swift
public struct RecurringGrant: Sendable, Equatable {
    public let productId: String
    public let periodHours: Int
    public let rewardType: String
    public let rewardAmount: Int64
}

public struct RecurringGrantClaimResult: Sendable, Equatable {
    public let claimed: Bool
    public let rewardType: String
    public let rewardAmount: Int64
    /// Human-readable reason, for logs and debug UI — never for control flow.
    public let report: String
}

public struct RecurringGrantsApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String
}
```

**Remote config** — key `recurring_grants_ios`:

```json
{
  "version": "rg_v1",
  "enabled": true,
  "grants": [
    {"product_id": "vip_pass", "period_hours": 24,  "reward_type": "gem",    "reward_amount": 500},
    {"product_id": "vip_pass", "period_hours": 168, "reward_type": "ticket", "reward_amount": 1}
  ]
}
```

Parse rules, structurally matching `PurchaseRouter.apply`:
- `version` — required non-blank `String`; missing ⇒ whole config rejected, last-good keeps running.
- `enabled` — optional, default `true`, via `JsonConfigValue.flag` (wrong type ⇒ default + note).
- `grants` — optional array. A **line** is dropped with a note (never rejecting the whole config)
  when: it is not an object; `product_id`/`reward_type` is missing, blank, or contains `|` or `:`
  (§3.2); `period_hours` or `reward_amount` is missing, non-numeric, a JSON boolean, or `<= 0`.
- **All numerics are clamped in `Double` space before any `Int` conversion.** `Int(someDouble)`
  traps above `Int.max`, and this was a Critical finding in Phase 7 and in Phase 5 before it.
  `period_hours` clamps to `[1, 8760]` (one year); `reward_amount` to `[1, Int64.max / 2]`.
- Duplicate `(product_id, period_hours, reward_type)` lines: first wins, with a note.
- Fields assigned under one lock acquisition, only on full success.

**Persistence** — key `recurring_grants_last_claim`, pipe-delimited `compositeKey:timestampMs`
entries, split at the last `:`, where the composite key is **`productId:rewardType:periodHours`**
and §3.2's validation guarantees neither component contains `|` or `:`. Both splits are therefore
unambiguous in both directions.

> **Correction.** An earlier revision of this section specified the composite key as
> `productId|rewardType|periodHours` — which reproduces the exact Android defect §3.2 diagnoses,
> since those pipes come from the key builder and the parse-time delimiter check cannot catch them.
> Caught during implementation by the round-trip test §3.2 mandates, which is the point of
> mandating it. Recorded rather than quietly edited: the failure mode here is a spec that reads
> correct and encodes wrong, and the next reader deserves to know it fooled its own author.

## 5. Locked Event Wire Shape

### `recurring_grant_claim` (new)

Emitted only on a successful claim.

| Field | Type | Notes |
|---|---|---|
| `product_id` | string | |
| `reward_type` | string | |
| `reward_amount` | int | |
| `period_hours` | int | |
| `recurring_grants_version` | string | active config version at claim time |

Consent: not gated, matching `iap_purchase`/`subscription_state`. No PII, no receipt value.

## 6. Known Limitations

- **Device-local wall clock (§3.4).** Advancing the device clock farms rewards without bound. Not
  fixable on-device; do not use this for anything with cash value.
- **Ownership is only as good as its two sources.** `Entitlements` is client-side state trusting
  what the store reported locally, and its own doc says so.
- **No server reconciliation**, so a reinstall resets claim history and re-grants one period.
- **No calendar/timezone semantics** — periods are rolling from the last claim, so there is no
  "resets at midnight." Android documents this as a deliberate trade for needing no server.

## 7. Testing Requirements

- **The persistence round trip (§3.2)** — persist, rebuild over the same store, assert timestamps
  recover under matching keys. The test whose absence shipped the Android bug.
- A `product_id`/`reward_type` containing `|` or `:` is dropped with a note.
- Ownership across **all seven** `SubscriptionState` cases, including `.cancelled` and
  `.inGracePeriod` (entitled — the Android defect) and `.inBillingRetry` (not entitled).
- Both ownership sources true at once grants once, not twice.
- First-ever claim is immediately due; no catch-up after a long absence.
- Period boundary exactly at, just before, and just after elapsed — via the injected clock.
- Clock moved backwards does not grant early; clock moved forward is documented, not prevented.
- Every parse rule: each drop-with-note case, the `enabled` wrong-type note, numeric clamping
  including a value that only decodes as `Double` (`1e300`, a 20-digit integer).
- `claim` emits exactly one event, and none on a rejected claim.
- `dueGrants()` is pure: calling it repeatedly changes nothing.
- Config disabled hides state without destroying it; re-enabling restores prior claim timestamps.
