# Offerwall + Brain Policy — Design Spec

**Goal:** Port the last two unported Android subsystems.

- **`OfferwallManager`** (`offerwall/OfferwallManager.kt`) — the 4th monetization surface. A
  vendor-neutral adapter: the host wraps each offerwall SDK in a provider, this decides whether one
  may open and de-duplicates completion payouts.
- **`BrainPolicy`** (`brain/BrainPolicy.kt`) — remote policy artifact application. It makes no
  decisions; it writes the tuning knobs the decision path reads, under hard caps an artifact can
  never exceed.

---

## 1. Scope

**Offerwall** — `Sources/MonetizationBrainCore/Offerwall/`
- `OfferwallProvider` protocol (`providerId`, `isAvailable()`, `show(onWallClosed:)`).
- `OfferwallManager.shared`, `Configuration { eventSink, playerId, store }`, `configure(_:)`,
  `apply(_:)`, `activeVersion()`, `register(_:)`, `unregister(providerId:)`,
  `availableProviders()`, `show(providerId:onWallClosed:)`, `reportCompletion(...)`, internal
  `resetForTest()`.
- Two events: `offerwall_impression`, `offerwall_completed`.

**BrainPolicy** — `Sources/MonetizationBrainCore/Brain/BrainPolicy.swift`
- `.shared`, `apply(_ json: String?, guardrails:) -> PolicyApplyResult`, `activeVersion()`,
  `isEnabled()`, `scenarioCap(_:)`, `profileFor(_:)`, `minConfidence()`, `pendingExplorationFloor`,
  internal `resetForTest()`.
- The nine `HARD_*` ceilings, ported as-is.

**Out of scope:** any vendor offerwall SDK; Firebase transport; persisting `BrainPolicy` state
(Android doesn't either — every launch starts at built-in defaults until the next fetch).

## 2. Deviations From Android

### 2.1 `Int(Double)` traps in Swift where Kotlin's `toInt()` saturates — the whole port turns on this

Kotlin's `Double.toInt()`/`toLong()` **saturate**: `1e300.toInt()` is `Int.MAX_VALUE`, no exception.
Swift's `Int(someDouble)` **traps**. So `BrainPolicy`'s artifact parsing, which runs `num()` over
every field and then converts, is crash-proof on Android **by language accident** — and porting
`it.toInt()` as `Int(it)` would introduce a crash-on-hostile-config bug that does not exist in the
original.

A policy artifact is operator-controlled remote config. `{"max_ads_24h": 1e300}` would take down
every app instance that fetched it.

**Decision:** every numeric conversion goes through `JsonConfigValue.clamped(_:lowerBound:upperBound:)`,
which already clamps in `Double` space before converting — the exact rule this codebase has had as a
Critical finding three times. Where a field has a `HARD_*` ceiling, that ceiling IS the upper bound,
so the clamp and the safety cap are one operation rather than two chances to forget.

### 2.2 `OfferwallManager.enabled` fails open — the fifth instance of this class

`val enabled = root["enabled"] != false`. A JSON string `"false"`, a number `0`, or an absent key is
never `equals()` the Kotlin `Boolean` `false`, so all of them evaluate to **`true`**. An operator
writing `"enabled": "false"` to disable the offerwall silently enables it — a fail-open kill switch.

Notably `BrainPolicy` in the same codebase parses the same field **correctly**
(`toString().toBooleanStrictOrNull() ?: true`), so this is inconsistency rather than house style.

**Decision:** `JsonConfigValue.flag`, defaulting to `false` for Offerwall (absent means off, matching
its documented intent) and to `true` for `BrainPolicy.enabled` (which is a kill switch whose absence
means "policy is live", matching Android).

### 2.3 Offerwall dedup keys must survive a vendor-supplied delimiter

`seenTx` stores `"\(providerId):\(transactionId)"`, joined with `,` and split on `,` at load.
`providerId` is studio-authored, but **`transactionId` comes from the vendor** — a third-party SDK
callback or S2S payload. A `transactionId` containing a comma fractures that entry on the next
launch, the reconstructed key never matches either fragment, and **the completion is paid out
twice**.

This is narrower than `RecurringGrants`' unconditional corruption, but the input is less trustworthy:
operator typos are rare and vendor payload formats are not ours to choose.

**Decision:** validate both components through `JsonConfigValue.identifier` with the persistence
delimiters forbidden, at the point they enter the key. A completion whose `transactionId` contains a
delimiter is **still accepted and still paid** — losing a legitimate reward is worse than a
duplicate, which is Android's own stated trade — but it is recorded under a sanitized key and noted,
rather than silently becoming un-dedupable.

### 2.4 The load path gets tested, because on iOS it can be

Android's `loadSeenTx` parsing of a non-empty stored string is **structurally untestable**:
`PreferenceHelper` no-ops without an Android `Context`, so unit tests only ever see the empty
default. That is precisely how a `RecurringGrants`-class defect hides.

**Decision:** `BrainKeyValueStore` is injectable here, so a round-trip test over a pre-seeded store
is mandatory — including the 200-entry cap trimming oldest-first, which Android also never tests.

### 2.5 `BrainPolicy.resetForTest()` must revert what it changed

Android's `clear()` resets only `BrainPolicy`'s own three fields. Every value it wrote into
`GuardrailEngine`, `ClickFraudGuard`, `HoldoutManager`, `AdsBrain.rolloutFraction`, `RewardModel`,
`FxRates` and `Entitlements` survives. Both Android test files work around this by manually resetting
those singletons in `tearDown` — the gap is known and never asserted.

**Decision:** `resetForTest()` reverts the downstream knobs it is responsible for, so one call
restores a clean state. A reset that leaves half the system configured is a test-isolation bug
waiting to be blamed on something else.

### 2.6 Hard caps are ported exactly, and tested from the attacking side

The nine `HARD_*` constants are the security property of this subsystem: an artifact may make the
SDK **more** conservative but never less. Cooldowns clamp on the **lower** bound (a shorter cooldown
is more aggressive); frequency caps clamp on the upper. `frustrationStreakThreshold` clamps against
the constant rather than the current value, deliberately — clamping against the current value would
ratchet monotonically tighter on repeated fetches.

"Ported exactly" means the literals in `com.app.plugin.brain.BrainPolicy`, listed in §3 — not
plausible numbers of the same shape. The first cut of this port guessed them without the Kotlin in
front of it and shipped `hardMaxFrustrationStreakThreshold = 10` against Android's 5, a ceiling
that lets the SDK keep selling to a player twice as deep into a losing streak as the safety review
signed off on. The constants are asserted against those literals, not against `> 0`.

Tests must attack each cap, not merely exercise it: an artifact asking for `max_ads_24h: 9999` must
come out at 60.

### 2.7 iOS is already better placed on thread-safety

`BrainPolicy.apply` performs ~12 unsynchronized writes to non-`@Volatile` fields across five
singletons, so a decision path on another thread can read a torn mix mid-apply. On iOS those exact
knobs (`ClickFraudGuard`, `HoldoutManager`, `RewardModel`, `FxRates`) were just moved behind
`LockedValue` for the Swift 6 work, so each write is individually safe. Whole-artifact atomicity is
**not** attempted — matching Android, and noted in §4 as a limitation rather than implied.

## 3. Definitions

**Offerwall config** — key `offerwall_config_ios`:
```json
{ "version": "ow_v1", "enabled": true, "providers": ["revu", "adjoe"] }
```
`version` required non-blank (else reject, last-good runs on). `enabled` optional, **defaults
false**, wrong type ⇒ false + note. `providers` optional allow-list; absent or empty ⇒ every
registered provider eligible; a non-string element dropped with a note.

**Gate order for `show`:** config loaded → `enabled` → provider registered → not child-directed
(COPPA) → consent not `.denied` → allow-list → `isAvailable()`.

`.unknown` consent does **not** block, unlike `CrmBridge` (Phase 10): an offerwall is a surface the
player actively chooses to open, and nothing leaves the device to a third party as a result of this
gate. The contrast is deliberate and belongs in the doc comment.

**`reportCompletion` deliberately ignores `enabled`.** A kill switch stops new opens; it must not
withhold payment for work already completed. Preserved from Android.

**Policy artifact** — key `brain_policy_ios`. Per-field failures degrade with a note; only a
missing/blank `version` or unparseable JSON rejects the whole artifact. `likelihood_profiles` is the
one block that is all-or-nothing: a partial set is dropped entirely, because mixing new profiles
with old makes cross-segment comparison meaningless.

**The wire names are Android's, exactly.** The artifact is authored once, by the backend/ops team,
against `appplugin/DOC/policy_artifact_example.json` and the schema in
`appplugin/DOC/35_POLICY_ARTIFACT_SCHEMA.md`. A field name that differs from Android's by one word
is a field that silently never applies — the parser does not recognize it, and a name it does not
recognize is not an error, it is nothing at all. Several of these differ from the KNOB they feed
(`churn_risk_threshold` sets `churnRiskProtectionThreshold`), and the name on the wire is the one
that counts.

| Block | Field | Knob | Bound |
|---|---|---|---|
| — | `version` (required, non-blank) | `activeVersion()` | reject if missing |
| — | `enabled` | kill switch | default **true**, wrong type ⇒ true + note |
| `guardrails` | `max_interstitial_per_session` | `maxInterstitialPerSession` | ≤ **10** |
| `guardrails` | `min_minutes_between_interstitial` | `minMinutesBetweenInterstitial` | ≥ **1** (lower) |
| `guardrails` | `max_ads_24h` | `maxAds24h` | ≤ **60** |
| `guardrails` | `churn_risk_threshold` | `churnRiskProtectionThreshold` | `[0, 1]` |
| `iap` | `max_offers_per_session` | `maxIapOffersPerSession` | ≤ **6** |
| `iap` | `min_minutes_between_offers` | `minMinutesBetweenIapOffers` | ≥ **3** (lower) |
| `iap` | `max_purchases_per_session_before_break` | `maxPurchasesPerSessionBeforeBreak` | ≤ **5** |
| `iap` | `frustration_streak_threshold` | `frustrationStreakThreshold` | ≤ **5**, against the CONSTANT |
| `gameplay` | `max_assists_per_session` | `maxAssistsPerSession` | ≤ **8** |
| `progression` | `min_level_for_interstitial` | `minLevelForInterstitial` | ≥ 0 |
| `progression` | `min_level_for_interstitial_after_loss` | `minLevelForInterstitialAfterLoss` | ≥ 0, noted if below the win gate |
| `progression` | `min_minutes_after_rewarded` | `minMinutesAfterRewarded` | ≥ 0 |
| `progression` | `skip_first_interstitials_on_reopen` | `skipFirstInterstitialsOnReopen` | ≥ 0 |
| `progression` | `max_iap_offers_24h` | `maxIapOffers24h` | ≥ 0, **no ceiling** |
| `click_fraud` | `max_clicks_per_window` | `ClickFraudGuard.maxClicksPerWindow` | ≤ **12** |
| `click_fraud` | `window_seconds` | `ClickFraudGuard.windowMs` | **× 1000**, ≥ 1000 ms |
| `click_fraud` | `lockout_seconds` | `ClickFraudGuard.lockoutMs` | **× 1000**, ≥ 0 ms |
| `experiment` | `holdout_fraction` | `HoldoutManager.holdoutFraction` | `[0, 0.5]` |
| `experiment` | `holdout_salt` | `HoldoutManager.salt` | non-blank, else kept |
| `experiment` | `rollout_fraction` | `AdsBrain.rolloutFraction` | `[0, 1]`, noted at 0 |
| `experiment` | `exploration_floor` | `pendingExplorationFloor` | `[0, 0.5]`, noted at 0 |
| `fx_rates` | `<CODE>` | `FxRates` | > 0 and finite; applied only if ≥ 1 rate survives |
| `remove_ads_products` | (array) | `Entitlements.removeAdsProductIds` | no `|`, no newline |
| `reward` | `weight_immediate_value`, `weight_continuation` | `RewardModel` | `[0, 1]` + anti-degeneracy note |
| `scenarios` | `<NAME>.interstitial_cap` | `scenarioCap(_:)` | ≤ **10** |
| `likelihood_profiles` | `<TYPE>.{fail_rate, rewarded_accept, payer_propensity, churn_tendency}` | `profileFor(_:)` | `[0, 1]`, all-or-nothing |
| — | `min_confidence` | `PersonalizationScenario.minConfidence` | `[0, 1]` |

**The nine `HARD_*` ceilings**, verbatim from `BrainPolicy.kt`: interstitial/session **10**,
ads/24h **60**, interstitial cooldown **1** minute (lower), clicks/window **12**, IAP
offers/session **6**, IAP offer cooldown **3** minutes (lower), purchases before break **5**,
frustration streak **5**, assists/session **8**. There is no ceiling on `max_iap_offers_24h`: the
knob is OFF (`0`) by default, so every value an artifact can set is already a tightening.

**Two unit conversions, and they are the easiest thing here to get wrong.** `window_seconds` and
`lockout_seconds` are the only fields whose wire unit differs from the knob's — Android multiplies
by 1000 inside the parser. Read as milliseconds, the reference artifact's `window_seconds: 300`
becomes a 300-**millisecond** fraud window: a guard that never fires, configured by an ops team
with every reason to believe they had set five minutes.

**Every frequency cap floors at `0`, not `1`.** Android's `clampUpper` ends in `coerceAtLeast(0)`,
so `max_ads_24h: 0` is honoured — in the safe direction. Dropping it as "not a positive number"
would leave the knob at its compiled default, so an operator switching a surface off from the
artifact would silently get 40 ads a day.

## 4. Known Limitations

- **No whole-artifact atomicity** (§2.7) — a concurrent reader can see some knobs updated and others
  not. Matching Android; closing it needs one lock spanning five singletons.
- **`BrainPolicy` is not persisted** — every launch runs built-in defaults until the next fetch.
- **A vendor `transactionId` containing a delimiter is paid but not de-dupable** (§2.3) — the
  deliberate direction, since a lost legitimate reward is worse than a duplicate.

## 5. Testing Requirements

- **Offerwall:** `enabled` absent ⇒ OFF and wrong-typed ⇒ OFF (the Android bug); the full gate order;
  `.unknown` consent passing; child-directed blocking; dedup across providers; a completion with no
  `transactionId` still accepted; **a `transactionId` containing the delimiter**; the persisted
  round-trip over a pre-seeded store; the 200-entry cap trimming oldest-first; `reportCompletion`
  paying out while `enabled == false`.
- **BrainPolicy:** the **wire contract** — Android's reference artifact applied with its exact field
  names, asserting every knob both reaches its artifact value and MOVED from the one it had, since a
  mistyped field name produces silence rather than a failure; the `HARD_*` constants asserted against
  Android's literals rather than `> 0`; every `HARD_*` cap attacked from above (and cooldowns from
  below); `window_seconds`/`lockout_seconds` landing in milliseconds; `1e300` and a
  20-digit literal on every numeric field, asserting no trap; `enabled` absent ⇒ policy live;
  unknown scenario/type names skipped without killing the artifact; partial `likelihood_profiles`
  dropped wholesale; a malformed artifact leaving the previous one running; `resetForTest()`
  restoring downstream knobs, not just its own fields (§2.5).
