# Battle Pass — Design Spec

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:writing-plans to turn this into an
> implementation plan, then superpowers:subagent-driven-development to execute it.

**Goal:** Port Android's `BattlePass` (`com.app.plugin.iap.BattlePass`, `DOC/52`) — a seasonal
progression feature. A season has an absolute start/end window, XP accumulates into tiers, each tier
carries a free and a premium reward list, and premium is unlocked by owning that season's one-off
IAP. The SDK owns the arithmetic, the timing, and double-claim prevention; the game owns deciding
when XP is earned and actually granting the reward items.

**Architecture:** A `BattlePass` singleton (`.shared`), NSLock-protected, holding one season config
plus the player's progress (XP and the set of claimed tier/track pairs), persisted through
`BrainKeyValueStore` and timed through the injected `BrainClock` (Phase 8). Premium ownership is
`Entitlements.isActive(premiumProductId)` — **not** `SubscriptionLifecycle`; see §3.1.

**Tech Stack:** Swift Package Manager, `MonetizationBrainCore`, `Foundation.JSONSerialization`,
`NSLock`, XCTest.

---

## 1. Scope

**In scope:**
- `BattlePass`: `.shared`, `Configuration { eventSink, playerId, store, clock, entitlements }`,
  `configure(_:)`, `activeVersion()`, internal `resetForTest()`.
- `apply(_ json: String?) -> BattlePassApplyResult` — config parsing per §4.
- `addXp(_ amount: Int64) async -> Int64` — returns XP actually credited. `async` because crossing a
  tier emits an event; note that *every* successful call also writes the persistence record, so it
  does store I/O whether or not a tier is crossed.
- `claim(tier:track:) async -> BattlePassClaimResult` — the one other mutating entry point.
- Pure queries: `xpTotal()`, `currentTier()`, `isPremiumActive()`, `claimable()`, `phase()`.
- Models in `Iap/BattlePassModels.swift`: `BattlePassTier`, `BattlePassReward`, `BattlePassTrack`,
  `BattlePassPhase`, `BattlePassClaimResult`, `BattlePassApplyResult`.
- Two new events: `battle_pass_tier`, `battle_pass_claim` (§5).

**Out of scope:**
- **Granting reward items.** `claim` returns `[BattlePassReward]`; the game's economy applies them.
- **Season history/archive.** One season in memory at a time, exactly as Android. A new `season_id`
  resets progress; there is no "past seasons" API.
- **Server-authoritative progress.** Device-local, same limit as Phase 8 — see §6.
- **Selling premium as a subscription.** §3.1 explains why, and what would have to change.

## 2. Dependencies On Existing Code

- `Entitlements.isActive(_:)` — premium ownership.
- `BrainClock` (Phase 8, `Support/BrainClock.swift`) — all season timing. Never `Date()`.
- `JsonConfigValue` — boolean, clamped-numeric and delimiter-validated-identifier parsing. The
  numeric/identifier helpers are being promoted out of `RecurringGrants` into this shared file; use
  them rather than writing a fourth private copy.
- `BrainKeyValueStore` — persistence.
- `EventSink`/`SdkEvent` — the two events. `sequenceId` is `EventQueue`'s to assign.

## 3. Deviations From Android (with rationale)

### 3.1 Premium ownership is `Entitlements` only — and that is correct here, unlike Phase 8

Phase 8's `RecurringGrants` ORs `SubscriptionLifecycle.stateFor(_:).isEntitled` with
`Entitlements.isActive(_:)`, because a recurring grant can hang off either a subscription or a
one-off pass. Battle pass premium is different: Android sells it as a **non-renewing, per-season
IAP** with a distinct product id per season (`DOC/52 §B.1`), so it has no renewal state and would
never appear in `SubscriptionLifecycle`'s tables at all. Consulting it would be noise, not safety.

**Decision:** `entitlements.isActive(premiumProductId)`, single source. Reaching for Phase 8's OR by
reflex would be the wrong answer to a different question.

Note what the per-season product id appears to buy for free: last season's premium does not carry
into this one, because each season names a different product id, so there is no season-end
revocation code anywhere.

**Corrected from an earlier revision of this spec, which called that a safety property. It is not —
it is a convention held up by operator discipline.** The absence of revocation code means nothing
anywhere checks that a new season's `premium_product_id` differs from the previous season's. Copy
one forward — the single most natural thing to do when cloning last season's config — and every
player who bought season 3 silently owns season 4's premium track for free, permanently, with no
diagnostic. The property is real only while the convention holds, and nothing enforces the
convention.

Not fixed in this phase, deliberately: detecting it requires persisting the previous season's
product id, which widens the persisted record — the very record §3.2 is about keeping narrow and
delimiter-safe. Recorded in §6 as a known limitation instead. If it is worth closing later, a note
(not a rejection) when the id is unchanged across a `season_id` change is the proportionate fix;
reuse could conceivably be deliberate, and this SDK does not reject an operator's stated intent.

**If a studio ever wants premium sold as an auto-renewing subscription, `isPremium` is the single
place that has to widen**, in exactly the way `RecurringGrants.owns` already does.

### 3.2 `season_id` must be delimiter-validated — this is the Android bug, latent

Android's persisted blob is `"<seasonId>|<xp>|<lastTier>|<claimKeys,comma,joined>"`, split on `|` at
load. The SDK's own fields are safe: `xp`/`lastTier` are numeric, and claim keys are
`"<tier>:<TRACK>"`, so neither can contain `|`.

But `season_id` is a **pass-through of the remote-config string**, validated only for non-blank, with
no character restriction. A `season_id` containing `|` shifts every field: `parts[0]` truncates at
the first pipe, and `xp`/`lastTier`/`claimed` are then parsed from fragments of the season id —
silently zeroing XP (via `toLongOrNull() ?: 0`) and losing the entire claim history on the next
launch.

This is the same delimiter-collision class as `RecurringGrants`' confirmed bug, but **conditional on
an operator typo rather than guaranteed**, which is exactly why it is easy to miss: it would pass
every test and every season until someone names a season `summer|2026`.

**Decision:** `season_id` goes through the shared delimiter-validated identifier parser. A
`season_id` containing `|`, `,`, `:` or a newline drops the whole config with a diagnostic note (a
structural field, so it rejects rather than degrades). A round-trip test with a hostile `season_id`
is mandatory — Android's suite never simulates a process restart at all (§7).

The comma is in that set although `season_id` sits in field 0 and the comma split only ever runs on
field 2. That safety is **positional** — a property of where the field sits, not of the field — and
a later change to the record's shape would retire it silently. Forbidding every character the
encoder uses makes the guarantee structural instead, and costs nothing.

### 3.3 `lastTier` is not ported — it is write-only state

Android persists `lastTier`, sets it on every tier-up, and restores it at load. Nothing ever reads
it: `addXp` computes `fromTier` by recomputing `currentTierInternal(cfg)` from XP, not from
`lastTier`; there is no getter; no test asserts it. It is vestigial.

**Decision:** omit it. Tier is a pure function of XP and the tier table, so storing it is a second
source of truth for something already derivable — and a restored `lastTier` that disagreed with
recomputed XP would be a bug waiting to happen. This also shortens the persisted record, which
matters given §3.2.

### 3.4 One lock, covering `apply` too — Android's has a real torn-state window

Android marks `addXp`, `claim`, `resetProgress` and `clear` `@Synchronized`, but **not `apply`** —
which writes `config` with a plain field write and then calls the synchronized `resetProgress`. So
between the new config landing and progress being reset, `config` describes the NEW season while
`xp`/`claimed` still hold the OLD one's. Any `addXp`/`claim` landing in that window evaluates the new
season's window and tier thresholds against the previous season's accumulated XP.

Every read accessor (`claimable`, `currentTier`, `xpTotal`, `phase`, `isPremiumActive`) is also
unsynchronized while `claimed` — a plain mutable set — is concurrently mutated by `claim`.

**Decision:** one `NSLock`, every path through it, including `apply` and every read. A config swap
and its progress reset are one acquisition, so no caller can observe the pair half-applied.

### 3.5 Claiming is impossible once a season ends — preserved, and stated loudly

`claim`/`claimable` both gate on `phase == .active`. The moment `now > endMs`, unclaimed rewards a
player has genuinely earned become **permanently unreachable** — the XP and claim set survive in
storage, but nothing can read them back out, and they are wiped when the next season is applied.

**Decision:** preserved, because it matches how the Android side behaves today and a silent
divergence between platforms on "did I get my reward" is worse than a harsh rule. But it is a
**product decision, not a technical one**, and it is called out here rather than buried: a player who
earns tier 30 an hour before the season ends and doesn't tap claim loses it. If the studio wants a
grace window, the change is small and belongs in `phase(...)` — it is not in this port because
changing it unilaterally would put the two platforms out of sync.

### 3.6 `clear()` does not become a public footgun; the `xp_cap` asymmetry is kept deliberately

- Android's `clear()` is public, wipes the loaded season config as well as progress, and is
  documented "Reset (test)" with no guard. Here: internal `resetForTest()`, as everywhere else.
- Android's parser is asymmetric on `xp_cap`: absent ⇒ silently uncapped (`Long.MAX_VALUE`);
  present-but-invalid (`<= 0`) ⇒ whole config rejected. **This asymmetry is correct and is kept**,
  against the usual house rule of degrade-with-a-note. The house default exists because degrading is
  normally the safe direction; here the "default" is *no XP cap at all*, so degrading fails OPEN —
  an operator typo would hand out unbounded progression. Absent means the operator chose uncapped;
  present-but-wrong means their intent is unknowable, and the safe answer is to keep the last-good
  config running. Stated explicitly so it doesn't look like an oversight the next time someone
  audits the parser for uniformity.

### 3.7 XP arithmetic must not trap

`addXp` multiplies by a `Double` catch-up multiplier: `(amount * mult).toLong()` on Android. In
Swift, `Int64(someDouble)` **traps** above `Int64.max` — the bug class that was Critical in Phases 5
and 7 and appeared again in Phase 8's parser. Both the multiplier and the amount are reachable from
config and host input.

**Decision:** clamp in `Double` space before converting, and use overflow-reporting arithmetic for
the `xp + gained` and `xpCap - xp` steps. Truncation toward zero is preserved (`addXp(3)` at `1.5×`
credits 4, not 5), and tested — Android's suite only ever uses round multiples, so the truncation is
untested there.

## 4. Type Definitions and Config

```swift
public enum BattlePassTrack: String, Sendable, Equatable, CaseIterable { case free, premium }

public enum BattlePassPhase: String, Sendable, Equatable {
    case noSeason, notStarted, active, ended
}

public struct BattlePassReward: Sendable, Equatable {
    public let id: String
    public let type: String        // game-defined vocabulary; the SDK never interprets it
    public let amount: Int64
}

public struct BattlePassTier: Sendable, Equatable {
    public let tier: Int
    public let xpRequired: Int64
    public let free: [BattlePassReward]
    public let premium: [BattlePassReward]
}

public struct BattlePassClaimResult: Sendable, Equatable {
    public let claimed: Bool
    public let rewards: [BattlePassReward]
    public let report: String
}

public struct BattlePassApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String
}

/// What `claimable()` returns. Added during implementation, because §4 named no element type and
/// `BattlePassTier`'s reward lists were otherwise unreachable from the public API — a host could
/// learn a tier was claimable but not what was in it without a second lookup.
public struct BattlePassClaimable: Sendable, Equatable {
    public let tier: Int
    public let track: BattlePassTrack
    public let rewards: [BattlePassReward]
}
```

**Note on the shared numeric helpers.** `JsonConfigValue.numeric`/`clampedInt64` reject non-positive
values, so they cannot parse `xp_required` (where `0` is not merely legal but mandatory for the
first tier, per §7) or `reward.amount` (where `0` is legal). Those two fields use
`JsonConfigValue.clamped(_:lowerBound:upperBound:)` — the one place the clamp-before-convert rule
lives — with a local non-negative *acceptance* rule layered on top. That is the same split
`SubscriptionLifecycle.parseOpenCount` already documents, and it is not a private fourth copy of the
clamp. Spelled out because §2's "use the promoted helpers" and §7's mandatory zero-XP tier otherwise
read as contradicting each other.

**Remote config** — key `battle_pass_ios`:

```json
{
  "version": "bp_v1",
  "season_id": "season_3",
  "start_ts_ms": 1725148800000,
  "end_ts_ms": 1727740800000,
  "premium_product_id": "bp_season_3_premium",
  "xp_cap": 100000,
  "tiers": [
    {"tier": 1, "xp_required": 0,
     "rewards_free": [{"id": "r1", "type": "gem", "amount": 10}],
     "rewards_premium": [{"id": "r2", "type": "skin", "amount": 1}]},
    {"tier": 2, "xp_required": 100, "rewards_free": [], "rewards_premium": []}
  ],
  "catch_up": {"enabled": true, "start_fraction": 0.5, "multiplier": 1.5}
}
```

Structural fields — reject the whole config, keeping the last-good one running: missing/blank
`version`; missing/blank/**delimiter-bearing** `season_id` (§3.2); missing or non-numeric
`start_ts_ms`/`end_ts_ms`; `end <= start`; missing or empty `tiers`; a tier missing `tier` or
`xp_required`; tiers not strictly increasing in **both** `tier` and `xp_required`; `xp_cap` present
and `<= 0` (§3.6); `catch_up.start_fraction` outside `(0, 1)` or `multiplier < 1.0`.

Degrade-with-a-note: a malformed individual reward (missing/blank `id` drops that reward);
`rewards_free`/`rewards_premium` absent ⇒ empty; `reward.type` absent ⇒ `""`; `reward.amount` absent
⇒ `0`, negative ⇒ dropped; `premium_product_id` absent or blank ⇒ no premium track available.

All numerics clamped in `Double` space before conversion (§3.7).

**Persistence** — key `battle_pass_state`. Given §3.2 and §3.3, the record is
`seasonId|xp|claimKey,claimKey,...` where each claim key is `"<tier>:<track>"`. `season_id` is
delimiter-validated at parse time, so no field can contain `|`. A corrupt or unreadable record drops
to empty progress rather than throwing — but note that "empty progress" is itself destructive, so the
load path must distinguish "no record" from "unparseable record" in its diagnostics.

## 5. Locked Event Wire Shapes

### `battle_pass_tier` (new) — emitted only when `toTier > fromTier`

`season_id`, `from_tier` (int), `to_tier` (int), `is_premium` (bool — ownership at emit time),
`battle_pass_version`.

### `battle_pass_claim` (new) — emitted only on a successful claim

`season_id`, `tier` (int), `track` (string, `"free"`/`"premium"`), `reward_count` (int),
`is_premium` (bool — **ownership**, not which track was claimed; both fields are present precisely
because they differ), `battle_pass_version`.

Android's `screen_name` is dropped from both, as in Phases 7 and 8. Neither event is consent-gated,
matching the IAP-layer precedent: no PII, no receipt value.

## 6. Known Limitations

- **Device-local wall clock.** Season phase, catch-up activation and claim eligibility are all
  driven by the injected clock with no server cross-check. Advancing the device clock can open or
  close a season early. Same class and same accepted trade as Phase 8 §3.4; the honest fix is
  server-authoritative timing.
- **Claim anti-dupe is local state.** A rooted/jailbroken device editing storage can re-claim.
  Android accepts this explicitly, at "the same risk tier as removeAds."
- **Unclaimed rewards are lost at season end** (§3.5) — a product decision, stated where integrators
  will see it.
- **Progress is per-device.** No cross-device sync; a reinstall starts the season over.
- **Nothing enforces that a new season names a new `premium_product_id`** (§3.1). Reusing the
  previous season's id grants every prior buyer the new season's premium track for free, silently.
- **A finished season's record is never collected.** If a season ends and no new config is ever
  applied, the progress record sits in storage permanently — unreachable, since claiming requires an
  active season, and overwritten only when a new season arrives. One short string, so this is
  hygiene rather than a leak.

## 7. Testing Requirements

- **A persist → fresh-instance → load round trip**, including a hostile `season_id` containing `|`
  and `:` (which must be rejected at parse time so it can never reach storage). Android's suite
  never simulates a restart at all, so its persistence is only verified by static reading.
- Tier arithmetic: tier is a pure function of XP; the `xp_required: 0` tier is reached with zero XP;
  tiers do not go backwards.
- XP cap: credited amount truncates at the cap; further `addXp` credits 0; absent `xp_cap` is
  uncapped.
- Catch-up: inactive before the fraction, active at and after it (inclusive), multiplier truncates
  toward zero (`addXp(3)` at `1.5×` ⇒ 4) — untested on Android. Activation compares against an
  absolute threshold (`start + Int64(span × fraction)`) rather than dividing to form a ratio: the
  division rounds twice, so a fraction like `0.3` against an odd span could land a millisecond
  either side of the boundary for reasons invisible from the config. The threshold form rounds once
  and is what Android computes, so both platforms switch catch-up on at the same instant.
- **Overflow:** a huge `amount`, a huge multiplier, and an `xp` near `Int64.max` must clamp, never
  trap. Include values that only decode as `Double`.
- Phase: before start, at exactly `start`, at exactly `end` (inclusive — active), after end. `addXp`
  and `claim` at exactly `end` specifically, which Android tests only for `phase()`.
- Claiming: double-claim rejected; free and premium tracks of one tier are independent; a player who
  buys premium late can claim premium for tiers already passed; nothing is claimable once ended.
- Premium: `isPremiumActive` follows the *current* season's product id, so last season's purchase
  does not carry forward.
- Season rollover: a different `season_id` resets XP and claims; the **same** `season_id` re-applied
  leaves progress untouched.
- Config: every structural rejection keeps the last-good config; every degrade path appends a note.
- Concurrency: a claim and a config swap cannot interleave into torn state; double `claim` of one
  tier under forced contention grants once (use Phase 8's `StallingClock` pattern).
