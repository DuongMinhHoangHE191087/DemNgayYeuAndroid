// Battle-pass value types. Flat top-level structs rather than types nested in the manager,
// matching this codebase's existing convention (`RecurringGrantModels.swift`,
// `PurchaseRoutingModels.swift`, `SubscriptionModels.swift`) even though Android nests them
// inside its `object`.

import Foundation

/// Which of a tier's two reward lists is being talked about.
///
/// The `rawValue` is the wire value of the `track` field on `battle_pass_claim` (§5) AND half of
/// a persisted claim key (`"<tier>:<track>"`, §4) — so renaming a case is a storage migration and
/// an analytics break at the same time, not a cosmetic change.
public enum BattlePassTrack: String, Sendable, Equatable, CaseIterable {
    case free
    case premium
}

/// Where the season is, relative to its absolute start/end window.
///
/// `.ended` is terminal for everything a player can do: no XP is credited and nothing can be
/// claimed once `now > endMs` (§3.5). That is a **product decision**, preserved from Android so
/// the two platforms do not silently disagree about "did I get my reward" — a player who earns a
/// tier an hour before the season closes and does not tap claim loses it. A grace window, if a
/// studio ever wants one, belongs in `BattlePass.phase(_:now:)` and nowhere else.
public enum BattlePassPhase: String, Sendable, Equatable {
    /// No config has been applied (or the manager is not configured).
    case noSeason
    case notStarted
    case active
    case ended
}

/// One item a tier hands over. **Opaque to this SDK**: `type` is the game's own vocabulary and is
/// never interpreted here. `claim` returns these; the host app's economy code is what actually
/// grants them (§1, out of scope — the same boundary `RecurringGrants` and `PaywallManager`
/// already draw).
public struct BattlePassReward: Sendable, Equatable {
    public let id: String
    /// Game-defined vocabulary. Absent in config ⇒ `""`.
    public let type: String
    /// Absent in config ⇒ `0`; negative ⇒ the whole reward is dropped at parse time.
    public let amount: Int64

    public init(id: String, type: String, amount: Int64) {
        self.id = id
        self.type = type
        self.amount = amount
    }
}

/// One rung of the season's ladder: the XP that reaches it, and the two reward lists it carries.
///
/// Tiers are required to be strictly increasing in BOTH `tier` and `xpRequired` (§4), which is
/// what makes `BattlePass.currentTier()` a pure, monotonic function of XP.
public struct BattlePassTier: Sendable, Equatable {
    public let tier: Int
    public let xpRequired: Int64
    public let free: [BattlePassReward]
    public let premium: [BattlePassReward]

    public init(tier: Int, xpRequired: Int64, free: [BattlePassReward], premium: [BattlePassReward]) {
        self.tier = tier
        self.xpRequired = xpRequired
        self.free = free
        self.premium = premium
    }
}

/// One tier/track pair the player may claim right now, with what claiming it would hand over.
///
/// The design spec leaves `claimable()`'s element shape open. It carries the rewards as well as
/// the pair so a host can render a whole claim sheet from one call without a second lookup —
/// and it is the only public reachability `BattlePassTier`'s reward lists otherwise have.
public struct BattlePassClaimable: Sendable, Equatable {
    public let tier: Int
    public let track: BattlePassTrack
    public let rewards: [BattlePassReward]

    public init(tier: Int, track: BattlePassTrack, rewards: [BattlePassReward]) {
        self.tier = tier
        self.track = track
        self.rewards = rewards
    }
}

/// Outcome of `BattlePass.claim(tier:track:)`.
///
/// `claimed` is the ONLY field control flow may branch on. On a rejected claim `rewards` is empty,
/// so a caller that ignores `claimed` and grants `rewards` anyway still grants nothing —
/// fail-closed by construction. `report` is for logs and debug UI, never for control flow.
///
/// Note that `claimed == true` with an EMPTY `rewards` is a legitimate outcome: a tier may declare
/// no rewards on one of its tracks, and claiming it still consumes the pair so it cannot be
/// claimed again.
public struct BattlePassClaimResult: Sendable, Equatable {
    public let claimed: Bool
    public let rewards: [BattlePassReward]
    public let report: String

    public init(claimed: Bool, rewards: [BattlePassReward], report: String) {
        self.claimed = claimed
        self.rewards = rewards
        self.report = report
    }
}

/// Outcome of `BattlePass.apply(_:)`. `applied == false` means the previously-loaded season is
/// still the one running; `report` says why it was rejected, and carries a `⚠` note per degraded
/// field when it was applied.
public struct BattlePassApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String

    public init(applied: Bool, report: String) {
        self.applied = applied
        self.report = report
    }
}

/// The optional end-of-season accelerator: past `startFraction` of the season's elapsed window,
/// XP is multiplied by `multiplier`.
///
/// Internal: it is parser output and a detail of how XP is credited, not something a host needs
/// to read back. `BattlePass.addXp` is the only consumer.
struct BattlePassCatchUp: Sendable, Equatable {
    let enabled: Bool
    /// In the OPEN interval `(0, 1)` — both endpoints reject the config (§4).
    let startFraction: Double
    /// At least `1.0`, clamped above to keep `Double(amount) * multiplier` in a sane range.
    let multiplier: Double
}

/// One parsed season. Internal for the same reason: the public surface is the five pure queries
/// plus `claimable()`, not the raw config.
struct BattlePassSeason: Sendable, Equatable {
    let version: String
    /// Delimiter-validated at parse time (§3.2) — it is field 0 of the persisted record, so a
    /// `|` in it would shift every subsequent field on the way back in.
    let seasonId: String
    let startMs: Int64
    let endMs: Int64
    /// `nil` when the config named no premium product, which means the premium track is simply
    /// unavailable — not that it is free.
    let premiumProductId: String?
    /// `nil` means UNCAPPED, which is why a present-but-invalid `xp_cap` rejects the whole config
    /// rather than degrading to this (§3.6): degrading here would fail OPEN.
    let xpCap: Int64?
    /// Strictly increasing in both `tier` and `xpRequired`.
    let tiers: [BattlePassTier]
    let catchUp: BattlePassCatchUp?
}
