import Foundation

/// "A season is running — how much progress does this player have, what has it unlocked, and what
/// have they already taken?" (Android `com.app.plugin.iap.BattlePass`; design spec §1.)
///
/// ═══ THE SDK NEVER GRANTS ANYTHING ═══
///
/// `claim` returns `[BattlePassReward]`; the host app's economy code is what actually grants the
/// items. `reward.type` is the game's own vocabulary and is never interpreted here. Same boundary
/// `RecurringGrants`, `PaywallManager` and `IapHelper.purchase` already draw — this type owns the
/// arithmetic, the timing and double-claim prevention, nothing else.
///
/// ═══ PREMIUM OWNERSHIP IS `Entitlements` ALONE — NOT PHASE 8'S OR (§3.1) ═══
///
/// `RecurringGrants` ORs `SubscriptionLifecycle.stateFor(_:).isEntitled` with
/// `Entitlements.isActive(_:)`, because a recurring grant can hang off either a subscription or a
/// one-off pass. Battle pass premium is different: it is sold as a NON-RENEWING, per-season IAP
/// with a distinct product id per season, so it has no renewal state and would never appear in
/// `SubscriptionLifecycle`'s tables at all. Consulting it would be noise, not safety. Reaching for
/// Phase 8's OR by reflex would be the wrong answer to a different question.
///
/// Note what the per-season product id buys for free: last season's premium does not carry into
/// this one, so there is no season-end revocation code anywhere and none is needed. **If a studio
/// ever sells premium as an auto-renewing subscription, `isPremium(_:_:)` is the single place that
/// has to widen**, in exactly the way `RecurringGrants.owns` already does.
///
/// ═══ `lastTier` IS DELIBERATELY NOT PORTED (§3.3) ═══
///
/// Android persists `lastTier`, sets it on every tier-up and restores it at load — and nothing
/// ever reads it. Tier is a pure function of XP and the tier table (`tier(for:in:)`), so storing
/// it would be a second source of truth for something already derivable, and a restored value
/// that disagreed with recomputed XP would be a bug waiting to happen. Omitting it also shortens
/// the persisted record, which matters given §3.2 below.
///
/// ═══ ⚠️ CLAIMING IS IMPOSSIBLE ONCE A SEASON ENDS (§3.5) ═══
///
/// `claim`/`claimable` both gate on `phase == .active`. The moment `now > endMs`, unclaimed
/// rewards a player genuinely earned become PERMANENTLY unreachable — the XP and claim set survive
/// in storage and `xpTotal()`/`currentTier()` still report them, but nothing can be taken, and the
/// record is wiped when the next season is applied. Preserved from Android on purpose: a silent
/// divergence between platforms on "did I get my reward" is worse than a harsh rule. It is a
/// PRODUCT decision, not a technical one. A grace window belongs in `phase(of:now:)` and would
/// have to ship on both platforms at once.
///
/// ═══ ONE LOCK, COVERING `apply` AND EVERY READ (§3.4) ═══
///
/// Android marks `addXp`/`claim`/`resetProgress` `@Synchronized` but NOT `apply`, which writes
/// `config` with a plain field write and then calls the synchronized reset. Between those two
/// steps `config` describes the NEW season while the XP and claim set still hold the OLD one's, so
/// an `addXp`/`claim` landing in that window evaluates the new season's window and thresholds
/// against the previous season's accumulated XP. Its read accessors are unsynchronized too, while
/// `claimed` — a plain mutable set — is concurrently mutated.
///
/// Here there is ONE `NSLock`, taken only through `withLock`, and every path goes through it
/// including `apply` and every query. A config swap and its progress reset are ONE acquisition, so
/// no caller can observe the pair half-applied.
///
/// ═══ ⚠️ STATED LIMITS (§6) ═══
///
/// Season phase, catch-up activation and claim eligibility are all driven by the injected clock
/// with no server cross-check, so advancing the device clock can open or close a season early.
/// Claim anti-dupe is local state, so a jailbroken device editing storage can re-claim. Progress
/// is per-device; a reinstall starts the season over. The honest fix for all three is
/// server-authoritative progress, which is a backend project.
public final class BattlePass: @unchecked Sendable {
    public static let shared = BattlePass()

    public struct Configuration: Sendable {
        public let eventSink: EventSink
        public let playerId: String
        /// Where the progress record lives. Defaults to `UserDefaults`, exactly as `Entitlements`/
        /// `RecurringGrants` do; tests inject an in-memory store.
        public let store: BrainKeyValueStore
        /// The time seam (Phase 8) — ALL season timing goes through it, and nothing in this file
        /// ever calls `Date()`.
        public let clock: BrainClock
        /// The one and only premium-ownership source (§3.1). Injected rather than hard-wired to
        /// `.shared` so tests can drive ownership without polluting a process-wide singleton.
        public let entitlements: Entitlements

        public init(
            eventSink: EventSink,
            playerId: String,
            store: BrainKeyValueStore = UserDefaultsBrainStore(),
            clock: BrainClock = SystemClock(),
            entitlements: Entitlements = .shared
        ) {
            self.eventSink = eventSink
            self.playerId = playerId
            self.store = store
            self.clock = clock
            self.entitlements = entitlements
        }
    }

    // MARK: - Constants

    /// Internal (not private) so the test file can assert against the exact persisted key name
    /// without duplicating the string literal — matching `RecurringGrants.lastClaimKey`.
    static let stateKey = "battle_pass_state"

    /// ═══ THE PERSISTED SHAPE, AND THE ANDROID BUG IT DEFUSES (§3.2) ═══
    ///
    /// The record is `seasonId|xp|claimKey,claimKey,…`, each claim key `"<tier>:<track>"`.
    ///
    /// Android's is `"<seasonId>|<xp>|<lastTier>|<claimKeys>"`, split on `|` at load. Its own
    /// fields are safe — `xp`/`lastTier` are numeric and claim keys are `"<tier>:<TRACK>"` — but
    /// `season_id` is a PASS-THROUGH of the remote-config string, validated only for non-blank. A
    /// `season_id` containing `|` shifts every field: `parts[0]` truncates at the first pipe, and
    /// `xp`/`claimed` are then parsed from fragments of the season id, silently zeroing XP (via
    /// `toLongOrNull() ?: 0`) and losing the entire claim history on the next launch.
    ///
    /// Same delimiter-collision class as `RecurringGrants`' confirmed bug, but CONDITIONAL on an
    /// operator typo rather than guaranteed — which is exactly what makes it easy to miss. It
    /// passes every test and every season until someone names a season `summer|2026`.
    ///
    /// So `season_id` goes through `JsonConfigValue.identifier` with both delimiters below, and a
    /// hostile id is REJECTED (a structural field, so it cannot degrade) before it can ever reach
    /// storage. `test_aHostileSeasonIdNeverReachesStorageAndLeavesEarnedProgressIntact` is the
    /// round trip that holds it — Android's suite never simulates a process restart at all.
    private static let entrySeparator: Character = "|"
    private static let claimSeparator: Character = ","
    private static let pairSeparator: Character = ":"

    private static let notLoadedVersion = "not loaded"
    /// Half of `Int64.max`, matching `RecurringGrants.maxRewardAmount`: a host app can add a
    /// battle-pass XP total to its own counter without having to defend against overflow. It is
    /// also what keeps `xp + gained` provably inside `Int64` (both operands are bounded by it).
    ///
    /// It used to carry a second reason: `Double(Int64.max)` rounds UP to 2^63, which `Int64.init`
    /// cannot represent, so passing `Int64.max` as `JsonConfigValue.clamped`'s upper bound
    /// reintroduced the very trap §3.7 is about. `clamped` now caps its own ceiling, so that reason
    /// is retired and callers may pass `Int64.max` freely — but this constant stays halved on its
    /// first reason, which was always the load-bearing one.
    private static let maxXp: Int64 = Int64.max / 2
    /// Year 9999 in epoch milliseconds. Bounds the season window so `end - start` and
    /// `now - start` cannot overflow.
    private static let maxTimestampMs: Int64 = 253_402_300_799_000
    private static let maxTierNumber = 100_000
    /// The multiplier is clamped rather than rejected above this: `Double(amount) * multiplier` is
    /// clamped again in `Double` space anyway, but keeping the stored value finite and sane makes
    /// the arithmetic readable rather than relying on `inf` behaving.
    private static let maxCatchUpMultiplier = 1_000_000.0

    // MARK: - State

    private let lock = NSLock()
    /// Orders this type's writes to `stateKey` without ever holding `lock` across store I/O —
    /// see `SequencedStoreWriter` for the double-grant this closes and the alternatives weighed.
    private let writer = SequencedStoreWriter()
    private var configuration: Configuration?
    private var season: BattlePassSeason?
    /// The season the progress below BELONGS to. Separate from `season?.seasonId` because progress
    /// is loaded at `configure` time, before any config has been applied — comparing the two is
    /// what makes a rollover survive a process restart.
    private var progressSeasonId = ""
    private var xp: Int64 = 0
    /// `"<tier>:<track>"` for each consumed pair.
    private var claimed: Set<String> = []
    /// A one-shot diagnostic from the load path, surfaced in the next `apply` report and then
    /// cleared. §4 requires the load path to distinguish "no record" (a first launch, silent) from
    /// "unparseable record" (destructive — empty progress is itself a loss).
    private var loadNote: String?

    public init() {}

    /// The ONLY place `lock` is acquired. `NSLock.lock()` is `noasync`, so funnelling every
    /// acquisition through one synchronous, non-escaping helper makes it structurally impossible
    /// to take the lock inside an `async` body or hold it across an `await`.
    private func withLock<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    // MARK: - Configuration

    /// Wires up the event sink, player id, persistence, clock and the premium-ownership source —
    /// and loads previously persisted progress from that store. The store read happens BEFORE the
    /// lock is taken; the lock only covers the field assignment.
    public func configure(_ configuration: Configuration) {
        let loaded = Self.loadProgress(configuration.store)
        withLock {
            self.configuration = configuration
            self.progressSeasonId = loaded.seasonId
            self.xp = loaded.xp
            self.claimed = loaded.claimed
            self.loadNote = loaded.note
        }
    }

    public func activeVersion() -> String {
        withLock { season?.version ?? Self.notLoadedVersion }
    }

    /// Test-only, and INTERNAL on purpose (§3.6). Android's equivalent (`clear()`) is public,
    /// unguarded and documented only as "Reset (test)", and it wipes the loaded season config as
    /// well as progress — so host code can silently discard a season ops just shipped. Matching
    /// `Entitlements`/`RecurringGrants`/`SubscriptionLifecycle`, this stays inside the module.
    func resetForTest() {
        let pending = withLock { () -> (BrainKeyValueStore, UInt64)? in
            let store = configuration?.store
            configuration = nil
            season = nil
            progressSeasonId = ""
            xp = 0
            claimed = []
            loadNote = nil
            guard let store else { return nil }
            return (store, writer.nextSequenceNumber())
        }
        guard let pending else { return }
        writer.remove(forKey: Self.stateKey, sequence: pending.1, from: pending.0)
    }

    // MARK: - Config

    /// Parses and applies a `battle_pass_ios` remote-config payload. NEVER throws out to the
    /// caller — a malformed config just means the currently-running season keeps running, matching
    /// `PurchaseRouter`/`PaywallManager`/`RecurringGrants`.
    ///
    /// Structural fields reject the WHOLE config (§4): `version`, `season_id` (including the
    /// delimiter check, §3.2), the timestamps and their ordering, `tiers` and their strict
    /// ordering, a present-but-invalid `xp_cap` (§3.6) and out-of-range `catch_up` fields.
    /// Everything else degrades with a `⚠` note: a malformed reward, an absent reward list, an
    /// absent `type`/`amount`, a blank `premium_product_id`.
    ///
    /// **`xp_cap` is the deliberate exception to this package's degrade-with-a-note house rule**,
    /// and it is not an oversight to tidy up on the next parser audit. The house rule exists
    /// because degrading is normally the SAFE direction; here the default for an absent `xp_cap`
    /// is *no XP cap at all*, so degrading would fail OPEN and an operator typo would hand out
    /// unbounded progression. Absent means the operator chose uncapped; present-but-wrong means
    /// their intent is unknowable, and the safe answer is to keep the last-good config running.
    ///
    /// A season change (a different `season_id`) resets XP and claims, and the swap plus the reset
    /// are ONE lock acquisition (§3.4). The SAME `season_id` re-applied leaves progress untouched,
    /// so a mid-season config tweak never costs a player their progress.
    @discardableResult
    public func apply(_ json: String?) -> BattlePassApplyResult {
        guard let json, !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return reject("empty config")
        }
        guard let data = json.data(using: .utf8),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return reject("JSON did not parse to an object")
        }

        var notes: [String] = []
        let parsed = Self.parseSeason(root, notes: &notes)
        guard case .season(let season) = parsed else {
            guard case .rejected(let reason) = parsed else { return reject("unparseable config") }
            return reject(reason)
        }

        // The config swap and the progress reset it implies are ONE acquisition (§3.4).
        // `reusedPremiumId` is detected in the same acquisition, because it compares the incoming
        // season against the one being replaced.
        let (rolledOver, pending, reusedPremiumId) = withLock { () -> (Bool, PendingWrite?, String?) in
            // ═══ A NEW SEASON REUSING THE OLD SEASON'S PREMIUM PRODUCT ID ═══
            //
            // Per-season premium ids are what make last season's purchase stop working this
            // season — there is no revocation code anywhere, the expiry falls out of the id being
            // different. Nothing enforces that, so copying an id forward when cloning a config
            // silently grants every prior buyer the new season's premium track, free and forever.
            //
            // A NOTE, not a rejection: reuse is unlikely but not incoherent (a studio may
            // deliberately sell one pass across two seasons), and this SDK does not overrule an
            // operator's stated intent — it makes sure they can see what they stated.
            //
            // Limited on purpose: this compares against the season currently in memory, so it
            // catches a config pushed into a running app and NOT a reuse that spans a process
            // restart. Catching that needs the previous id persisted, which widens the very record
            // §3.2 keeps narrow. A partial diagnostic beats a wider attack surface.
            let previous = self.season
            self.season = season
            let reused: String? = {
                guard let previous, previous.seasonId != season.seasonId,
                      let id = season.premiumProductId, previous.premiumProductId == id
                else { return nil }
                return id
            }()
            guard progressSeasonId != season.seasonId else { return (false, nil, reused) }
            progressSeasonId = season.seasonId
            xp = 0
            claimed = []
            return (true, pendingWriteLocked(), reused)
        }
        if let pending {
            writer.write(pending.line, forKey: Self.stateKey, sequence: pending.sequence, to: pending.store)
        }

        var report = "loaded '\(season.version)' season '\(season.seasonId)', \(season.tiers.count) tier(s)"
            + ", xp_cap=\(season.xpCap.map(String.init) ?? "none")"
            + ", premium=\(season.premiumProductId ?? "none")"
            + (rolledOver ? " — new season, progress reset" : " — same season, progress kept")
        if let reusedPremiumId {
            notes.append(
                "season '\(season.seasonId)' reuses the previous season's 'premium_product_id' "
                + "('\(reusedPremiumId)') -> everyone who bought the previous season already owns "
                + "this one's premium track, because a per-season id is the ONLY thing that expires it"
            )
        }
        if let note = takeLoadNote() { notes.append(note) }
        if !notes.isEmpty {
            report += "\n  \u{26A0} " + notes.joined(separator: "\n  \u{26A0} ")
        }
        return BattlePassApplyResult(applied: true, report: report)
    }

    private func reject(_ reason: String) -> BattlePassApplyResult {
        var report = "REJECTED battle pass config (\(reason)) — keeping '\(activeVersion())'"
        if let note = takeLoadNote() { report += "\n  \u{26A0} " + note }
        return BattlePassApplyResult(applied: false, report: report)
    }

    private func takeLoadNote() -> String? {
        withLock {
            let note = loadNote
            loadNote = nil
            return note
        }
    }

    private enum SeasonParse {
        case season(BattlePassSeason)
        case rejected(String)
    }

    private static func parseSeason(_ root: [String: Any], notes: inout [String]) -> SeasonParse {
        guard let version = (root["version"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !version.isEmpty else {
            return .rejected("missing or blank 'version' field")
        }

        // §3.2. The forbidden delimiters are exactly the characters `encodeProgressLocked` joins
        // with, passed in rather than assumed: a delimiter added to the encoder without being
        // added here reopens the hole. `identifier` always rejects newlines on top of these.
        var idNotes: [String] = []
        guard let seasonId = JsonConfigValue.identifier(
            root["season_id"],
            field: "season_id",
            label: "the battle pass season",
            // `claimSeparator` is included even though `season_id` occupies field 0 and the comma
            // split only ever runs on field 2. That safety is POSITIONAL — it holds because of
            // where the field sits in the record, not because of anything about the field — and a
            // future change to the record's shape would retire it silently. Forbidding every
            // character the encoder uses makes the guarantee structural instead.
            mustNotContain: [entrySeparator, pairSeparator, claimSeparator],
            notes: &idNotes
        ) else {
            return .rejected(idNotes.last ?? "missing a valid 'season_id'")
        }

        var timeNotes: [String] = []
        guard let startMs = JsonConfigValue.clampedInt64(
            root["start_ts_ms"], field: "start_ts_ms", label: "the battle pass season",
            lowerBound: 1, upperBound: maxTimestampMs, notes: &timeNotes
        ) else {
            return .rejected(timeNotes.last ?? "missing a valid 'start_ts_ms'")
        }
        guard let endMs = JsonConfigValue.clampedInt64(
            root["end_ts_ms"], field: "end_ts_ms", label: "the battle pass season",
            lowerBound: 1, upperBound: maxTimestampMs, notes: &timeNotes
        ) else {
            return .rejected(timeNotes.last ?? "missing a valid 'end_ts_ms'")
        }
        guard endMs > startMs else {
            return .rejected("'end_ts_ms' (\(endMs)) is not after 'start_ts_ms' (\(startMs))")
        }

        // §3.6 — present-but-invalid rejects; ABSENT means uncapped.
        var xpCap: Int64?
        if let rawCap = root["xp_cap"] {
            var capNotes: [String] = []
            guard let value = JsonConfigValue.numeric(
                rawCap, field: "xp_cap", label: "the battle pass season", notes: &capNotes
            ) else {
                return .rejected(
                    (capNotes.last ?? "'xp_cap' is not a positive number")
                    + " — an ABSENT 'xp_cap' means uncapped, so an invalid one cannot degrade without failing open (§3.6)"
                )
            }
            xpCap = JsonConfigValue.clamped(value, lowerBound: 1, upperBound: maxXp)
        }

        // Through `JsonConfigValue.identifier` for its shared length ceiling (an unbounded id is
        // held for the season and passed to `Entitlements` on every claim). No delimiters are
        // forbidden because this id is never embedded in a persistence record — only `season_id`
        // is (§3.2) — so `mustNotContain` is deliberately empty rather than copied from above.
        // Absent or unusable DEGRADES with a note: unlike `season_id` it is not structural, and a
        // season with no premium track is a legitimate season.
        var premiumProductId: String?
        if root["premium_product_id"] != nil {
            var premiumNotes: [String] = []
            premiumProductId = JsonConfigValue.identifier(
                root["premium_product_id"], field: "premium_product_id",
                label: "the battle pass season", mustNotContain: [], notes: &premiumNotes
            )
        }
        if premiumProductId == nil {
            notes.append("no usable 'premium_product_id' -> the premium track is unavailable this season")
        }

        let tiers: [BattlePassTier]
        switch parseTiers(root["tiers"], notes: &notes) {
        case .rejected(let reason): return .rejected(reason)
        case .parsed(let parsed): tiers = parsed
        }

        let catchUp: BattlePassCatchUp?
        switch parseCatchUp(root["catch_up"], notes: &notes) {
        case .rejected(let reason): return .rejected(reason)
        case .parsed(let parsed): catchUp = parsed
        }

        return .season(BattlePassSeason(
            version: version,
            seasonId: seasonId,
            startMs: startMs,
            endMs: endMs,
            premiumProductId: premiumProductId,
            xpCap: xpCap,
            tiers: tiers,
            catchUp: catchUp
        ))
    }

    /// Either a parsed value or the reason the whole config is rejected. Lets the pieces of
    /// `parseSeason` fail structurally without each one re-deriving `SeasonParse`.
    private enum FieldParse<T> {
        case parsed(T)
        case rejected(String)
    }

    /// The `tiers` array. Every failure here is STRUCTURAL — a malformed tier cannot degrade,
    /// because a season whose ladder is half-parsed would hand out the wrong rewards at the wrong
    /// thresholds, which is worse than keeping the last-good season running.
    ///
    /// The strictly-increasing check on BOTH `tier` and `xp_required` is what lets
    /// `tier(for:in:)` be a simple "last one the XP meets" scan.
    private static func parseTiers(
        _ raw: Any?, notes: inout [String]
    ) -> FieldParse<[BattlePassTier]> {
        guard let raw else { return .rejected("missing 'tiers'") }
        guard let elements = raw as? [Any], !elements.isEmpty else {
            return .rejected("'tiers' is not a non-empty array")
        }
        var tiers: [BattlePassTier] = []
        for element in elements {
            guard let entry = element as? [String: Any] else {
                return .rejected("a 'tiers' entry is not an object")
            }
            var tierNotes: [String] = []
            guard let tier = JsonConfigValue.clampedInt(
                entry["tier"], field: "tier", label: "a battle pass tier",
                lowerBound: 1, upperBound: maxTierNumber, notes: &tierNotes
            ) else {
                return .rejected(tierNotes.last ?? "a tier is missing 'tier'")
            }
            guard let xpRequired = nonNegativeInt64(entry["xp_required"], upperBound: maxXp) else {
                return .rejected("tier \(tier) is missing a valid 'xp_required'")
            }
            if let previous = tiers.last, tier <= previous.tier || xpRequired <= previous.xpRequired {
                return .rejected(
                    "tiers must be strictly increasing in BOTH 'tier' and 'xp_required' "
                    + "(tier \(tier) at \(xpRequired) XP follows tier \(previous.tier) at \(previous.xpRequired) XP)"
                )
            }
            tiers.append(BattlePassTier(
                tier: tier,
                xpRequired: xpRequired,
                free: parseRewards(entry["rewards_free"], field: "rewards_free", tier: tier, notes: &notes),
                premium: parseRewards(entry["rewards_premium"], field: "rewards_premium", tier: tier, notes: &notes)
            ))
        }
        return .parsed(tiers)
    }

    /// The optional `catch_up` block.
    ///
    /// A block that is not an object carries no intent to honour and no multiplier, so it DEGRADES
    /// (fail closed — no catch-up). Fields inside a real block do NOT degrade, for the same reason
    /// `xp_cap` does not: a wrong multiplier hands out extra progression, which is the open
    /// direction, and the safe reading of an unintelligible one is to keep the last-good season.
    private static func parseCatchUp(
        _ raw: Any?, notes: inout [String]
    ) -> FieldParse<BattlePassCatchUp?> {
        guard let raw else { return .parsed(nil) }
        guard let block = raw as? [String: Any] else {
            notes.append("'catch_up' is not an object -> no catch-up this season")
            return .parsed(nil)
        }
        let enabled = JsonConfigValue.flag(
            block["enabled"], name: "catch_up.enabled", defaultingTo: false, notes: &notes
        )
        var catchUpNotes: [String] = []
        guard let fraction = JsonConfigValue.numeric(
            block["start_fraction"], field: "start_fraction", label: "'catch_up'", notes: &catchUpNotes
        ), fraction < 1 else {
            return .rejected("'catch_up.start_fraction' must be inside the open interval (0, 1)")
        }
        guard let multiplier = JsonConfigValue.numeric(
            block["multiplier"], field: "multiplier", label: "'catch_up'", notes: &catchUpNotes
        ), multiplier >= 1 else {
            return .rejected("'catch_up.multiplier' must be at least 1.0")
        }
        return .parsed(BattlePassCatchUp(
            enabled: enabled,
            startFraction: fraction,
            multiplier: Swift.min(multiplier, maxCatchUpMultiplier)
        ))
    }

    private static func parseRewards(
        _ raw: Any?, field: String, tier: Int, notes: inout [String]
    ) -> [BattlePassReward] {
        guard let raw else { return [] }
        guard let elements = raw as? [Any] else {
            notes.append("tier \(tier): '\(field)' is not an array -> treated as empty")
            return []
        }
        var rewards: [BattlePassReward] = []
        for element in elements {
            guard let entry = element as? [String: Any] else {
                notes.append("tier \(tier): a '\(field)' entry is not an object -> dropped")
                continue
            }
            guard let id = (entry["id"] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty else {
                notes.append("tier \(tier): a '\(field)' entry is missing a non-blank 'id' -> dropped")
                continue
            }
            // Same ceiling `JsonConfigValue.identifier` applies, read from the same constant so the
            // two cannot drift. Not routed through `identifier` itself because `type` is OPTIONAL
            // and defaults to `""` (§4) — `identifier`'s contract is "required and non-blank", and
            // bending it here would change what a reward config accepts. The id is not echoed back:
            // it is the thing that is too big.
            guard id.count <= JsonConfigValue.maxIdentifierLength else {
                notes.append(
                    "tier \(tier): a '\(field)' entry's 'id' is \(id.count) characters, over the "
                    + "\(JsonConfigValue.maxIdentifierLength)-character limit -> dropped"
                )
                continue
            }
            let type = (entry["type"] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard type.count <= JsonConfigValue.maxIdentifierLength else {
                notes.append(
                    "tier \(tier): reward '\(id)' has a 'type' of \(type.count) characters, over the "
                    + "\(JsonConfigValue.maxIdentifierLength)-character limit -> dropped"
                )
                continue
            }
            var amount: Int64 = 0
            if let rawAmount = entry["amount"] {
                guard let parsed = nonNegativeInt64(rawAmount, upperBound: maxXp) else {
                    notes.append("tier \(tier): reward '\(id)' has a negative or non-numeric 'amount' -> dropped")
                    continue
                }
                amount = parsed
            }
            rewards.append(BattlePassReward(id: id, type: type, amount: amount))
        }
        return rewards
    }

    /// ═══ WHY THIS IS NOT `JsonConfigValue.clampedInt64` ═══
    ///
    /// It shares the CLAMP — `JsonConfigValue.clamped`, the one place the clamp-before-convert rule
    /// is implemented — but not `numeric`'s acceptance rules, because `numeric` DROPS non-positive
    /// values and `xp_required: 0` is mandatory here: a tier reachable with no XP at all is the
    /// first rung of every battle pass, and `reward.amount: 0` is explicitly legal (§4). This is
    /// the same split `SubscriptionLifecycle.parseOpenCount` documents, and the reason
    /// `JsonConfigValue`'s own header says a parser may need the clamp without the acceptance rules.
    ///
    /// Everything else is kept identical to `numeric`: a JSON boolean is refused (`true as? Int`
    /// succeeds and returns `1`), numeric strings are accepted, and non-finite values are dropped
    /// rather than clamped because they carry no intent to honour.
    private static func nonNegativeInt64(_ raw: Any?, upperBound: Int64) -> Int64? {
        guard let raw, !JsonConfigValue.isJsonBoolean(raw) else { return nil }
        var value: Double?
        if let intValue = raw as? Int { value = Double(intValue) }
        else if let doubleValue = raw as? Double { value = doubleValue }
        else if let stringValue = raw as? String { value = Double(stringValue) }
        guard let value, value.isFinite, value >= 0 else { return nil }
        return JsonConfigValue.clamped(value, lowerBound: 0, upperBound: upperBound)
    }

    // MARK: - Queries
    //
    // PURE (§1): no mutation, no persistence, no event. Every one of them goes through the lock,
    // because `claimed` is a mutable set that `claim` writes concurrently (§3.4).

    public func phase() -> BattlePassPhase {
        withLock {
            guard let configuration, let season else { return .noSeason }
            return Self.phase(of: season, now: configuration.clock.nowMs())
        }
    }

    public func xpTotal() -> Int64 {
        withLock { season == nil ? 0 : xp }
    }

    /// The tier the player has reached: the highest tier whose `xpRequired` the XP total meets.
    ///
    /// `0` means "below the first tier", not "tier 0" — tier numbers start at 1, which is why the
    /// parser rejects a `tier` of `0`. A season whose first tier is `xp_required: 0` therefore
    /// reports tier 1 from the very start (§7).
    ///
    /// Reported even once the season has ended: the progress is real, it is simply no longer
    /// claimable (§3.5).
    public func currentTier() -> Int {
        withLock {
            guard let season else { return 0 }
            return Self.tier(for: xp, in: season)
        }
    }

    /// Whether this player owns THIS season's premium product (§3.1). `false` when the season
    /// declares no premium product at all — that means the premium track is unavailable, not free.
    public func isPremiumActive() -> Bool {
        withLock {
            guard let configuration, let season else { return false }
            return Self.isPremium(season, configuration)
        }
    }

    /// Every tier/track pair the player may claim right now, in tier order with `free` before
    /// `premium`, each carrying what claiming it would hand over.
    ///
    /// Empty once the season has ended (§3.5) or before it starts. Premium pairs appear only while
    /// the premium product is owned — so buying premium late opens every already-passed tier at
    /// once, with no catch-up code of any kind.
    ///
    /// Calling it twice is exactly as side-effect-free as calling it once: it does NOT consume
    /// anything, and `claim` is the only way to take a reward.
    public func claimable() -> [BattlePassClaimable] {
        withLock {
            guard let configuration, let season else { return [] }
            guard Self.phase(of: season, now: configuration.clock.nowMs()) == .active else { return [] }
            let premiumOwned = Self.isPremium(season, configuration)
            var result: [BattlePassClaimable] = []
            for entry in season.tiers where entry.xpRequired <= xp {
                for track in BattlePassTrack.allCases {
                    if track == .premium && !premiumOwned { continue }
                    guard !claimed.contains(Self.claimKey(tier: entry.tier, track: track)) else { continue }
                    result.append(BattlePassClaimable(
                        tier: entry.tier,
                        track: track,
                        rewards: track == .free ? entry.free : entry.premium
                    ))
                }
            }
            return result
        }
    }

    // MARK: - XP

    /// Credits XP and returns how much was ACTUALLY credited — `0` when the season is not running,
    /// when the amount is not positive, or when the cap is already reached. The host decides when
    /// XP is earned; this owns the arithmetic and the cap.
    ///
    /// ═══ THE ARITHMETIC MUST NOT TRAP (§3.7) ═══
    ///
    /// `Int64(someDouble)` TRAPS above `Int64.max`, and this is the one place where a `Double` from
    /// remote config (the catch-up multiplier) meets an `Int64` from the host (`amount`). That bug
    /// class was a Critical finding in three earlier phases of this port. So: the product is
    /// clamped in `Double` space BEFORE any integer conversion (`JsonConfigValue.clamped`), and
    /// both `xpCap - xp` and `xp + credited` use overflow-REPORTING arithmetic rather than
    /// trusting the operands.
    ///
    /// Truncation is toward zero and is preserved from Android: `addXp(3)` at `1.5×` credits 4,
    /// not 5. Android's suite only ever uses round multiples, so its truncation is untested there.
    ///
    /// `async` because crossing a tier emits an event, and the send is AWAITED rather than handed
    /// to a detached `Task` — see `SubscriptionLifecycle.observe`'s doc comment for the drop window
    /// that closes. Every other event producer in this package awaits for the same reason.
    @discardableResult
    public func addXp(_ amount: Int64) async -> Int64 {
        let outcome = withLock { () -> XpOutcome in
            var outcome = XpOutcome()
            guard let configuration, let season else { return outcome }
            outcome.configuration = configuration
            guard Self.phase(of: season, now: configuration.clock.nowMs()) == .active else { return outcome }
            guard amount > 0 else { return outcome }

            let multiplier = Self.catchUpMultiplier(season, now: configuration.clock.nowMs())
            // Clamp in `Double` space, THEN convert. `Double(amount) * multiplier` may be `inf`;
            // the clamp handles that, an `isFinite` guard would not (§3.7).
            let gained = JsonConfigValue.clamped(
                Double(amount) * multiplier, lowerBound: 0, upperBound: Self.maxXp
            )
            // An absent `xp_cap` means uncapped, which in practice means "capped at the ceiling
            // that keeps the arithmetic provably safe" — a bound no real season will ever reach.
            let cap = season.xpCap ?? Self.maxXp
            let (room, roomOverflow) = cap.subtractingReportingOverflow(xp)
            let headroom = roomOverflow ? 0 : Swift.max(0, room)
            let credited = Swift.min(gained, headroom)
            guard credited > 0 else { return outcome }

            let fromTier = Self.tier(for: xp, in: season)
            let (updated, sumOverflow) = xp.addingReportingOverflow(credited)
            xp = sumOverflow ? cap : updated
            let toTier = Self.tier(for: xp, in: season)

            outcome.credited = credited
            outcome.pending = pendingWriteLocked()
            if toTier > fromTier {
                outcome.event = Self.tierEvent(
                    season: season,
                    from: fromTier,
                    to: toTier,
                    isPremium: Self.isPremium(season, configuration),
                    playerId: configuration.playerId
                )
            }
            return outcome
        }

        // Persist and emit OUTSIDE the lock — never hold it across store I/O or an await. The
        // write carries the sequence number stamped inside that same acquisition, so a snapshot
        // encoded before this one can never overwrite it (`SequencedStoreWriter`).
        if let pending = outcome.pending {
            writer.write(pending.line, forKey: Self.stateKey, sequence: pending.sequence, to: pending.store)
        }
        if let event = outcome.event, let sink = outcome.configuration?.eventSink {
            await sink.send(event)
        }
        return outcome.credited
    }

    private struct XpOutcome {
        var credited: Int64 = 0
        var pending: PendingWrite?
        var event: SdkEvent?
        var configuration: Configuration?
    }

    // MARK: - Claim

    /// Consumes one tier/track pair and returns what the host app should grant.
    ///
    /// `claimed == false` means nothing was consumed and nothing should be granted — `rewards` is
    /// empty on that path, and `report` says why (for logs and debug UI, never for control flow).
    /// `claimed == true` with empty `rewards` is legitimate: a tier may declare no rewards on a
    /// track, and claiming it still consumes the pair.
    ///
    /// Authorization (season running, tier reached, track owned, not already claimed) and
    /// consumption (inserting the claim key) are ONE lock acquisition, so two concurrent callers
    /// can never both claim the same pair. The event is built from that same snapshot — its
    /// `battle_pass_version` and `is_premium` therefore cannot drift from the state that
    /// authorised the claim — and sent after the lock is released.
    ///
    /// Nothing is claimable once `now > endMs` (§3.5). That is deliberate and harsh; see the type
    /// doc.
    @discardableResult
    public func claim(tier: Int, track: BattlePassTrack) async -> BattlePassClaimResult {
        let outcome = withLock { () -> ClaimOutcome in
            func rejected(_ reason: String) -> ClaimOutcome {
                var outcome = ClaimOutcome()
                outcome.configuration = configuration
                outcome.result = BattlePassClaimResult(
                    claimed: false,
                    rewards: [],
                    report: "cannot claim tier \(tier) '\(track.rawValue)': \(reason)"
                )
                return outcome
            }

            guard let configuration else { return rejected("BattlePass is not configured") }
            guard let season else { return rejected("no battle pass season is loaded") }
            let phase = Self.phase(of: season, now: configuration.clock.nowMs())
            guard phase == .active else {
                // Every case named explicitly, no `default:`. `.active` and `.noSeason` are already
                // ruled out by the guards above, so a wildcard would be correct TODAY — but
                // `BattlePassPhase` is package-owned and §3.5 explicitly floats a grace window as a
                // future case. Under a wildcard that case would silently report "has not started
                // yet" with no compiler warning; named cases make the compiler demand an answer.
                switch phase {
                case .ended:
                    return rejected(
                        "season '\(season.seasonId)' has ended — unclaimed rewards are not recoverable (§3.5)"
                    )
                case .notStarted:
                    return rejected("season '\(season.seasonId)' has not started yet")
                case .active, .noSeason:
                    return rejected("season '\(season.seasonId)' is not claimable right now")
                }
            }
            guard let entry = season.tiers.first(where: { $0.tier == tier }) else {
                return rejected("no tier \(tier) in config '\(season.version)'")
            }
            guard xp >= entry.xpRequired else {
                return rejected("tier \(tier) needs \(entry.xpRequired) XP and the player has \(xp)")
            }
            let premiumOwned = Self.isPremium(season, configuration)
            if track == .premium {
                guard let productId = season.premiumProductId else {
                    return rejected("season '\(season.seasonId)' declares no premium product, so it has no premium track")
                }
                guard premiumOwned else { return rejected("the player does not own '\(productId)'") }
            }
            guard claimed.insert(Self.claimKey(tier: tier, track: track)).inserted else {
                return rejected("it has already been claimed this season")
            }

            let rewards = track == .free ? entry.free : entry.premium
            var outcome = ClaimOutcome()
            outcome.configuration = configuration
            outcome.pending = pendingWriteLocked()
            outcome.event = Self.claimEvent(
                season: season,
                tier: tier,
                track: track,
                rewardCount: rewards.count,
                isPremium: premiumOwned,
                playerId: configuration.playerId
            )
            outcome.result = BattlePassClaimResult(
                claimed: true,
                rewards: rewards,
                report: "claimed tier \(tier) '\(track.rawValue)' of season '\(season.seasonId)' -> \(rewards.count) reward(s)"
            )
            return outcome
        }

        if let pending = outcome.pending {
            writer.write(pending.line, forKey: Self.stateKey, sequence: pending.sequence, to: pending.store)
        }
        if let event = outcome.event, let sink = outcome.configuration?.eventSink {
            await sink.send(event)
        }
        return outcome.result
    }

    private struct ClaimOutcome {
        var result = BattlePassClaimResult(claimed: false, rewards: [], report: "")
        var pending: PendingWrite?
        var event: SdkEvent?
        var configuration: Configuration?
    }

    // MARK: - Events (§5)
    //
    // Neither event is consent-gated, matching the IAP-layer precedent (`iap_purchase`,
    // `subscription_state`, `recurring_grant_claim`): no PII, no receipt value. Android's
    // `screen_name` is dropped from both, as in Phases 7 and 8. `sequenceId` is `EventQueue`'s to
    // assign and is never set at a call site.

    /// Emitted only when `toTier > fromTier`.
    private static func tierEvent(
        season: BattlePassSeason, from: Int, to: Int, isPremium: Bool, playerId: String
    ) -> SdkEvent {
        SdkEvent(
            eventName: "battle_pass_tier",
            eventData: [
                "season_id": .string(season.seasonId),
                "from_tier": .int(from),
                "to_tier": .int(to),
                // `.int(0/1)`, not `.bool`: the backend column is INT64 and Android sends 0/1.
                // A JSON boolean survives only because Python's int(True) is 1 — working by
                // coercion is not the same as matching the wire contract.
                "is_premium": .int(isPremium ? 1 : 0),
                "battle_pass_version": .string(season.version)
            ],
            userProperty: UserProperty(userId: playerId)
        )
    }

    /// Emitted only on a successful claim. `track` says which list was taken; `is_premium` says
    /// whether the player OWNS premium. Both fields are present precisely because they differ — a
    /// premium owner claiming their free track reports `track: "free", is_premium: true`.
    private static func claimEvent(
        season: BattlePassSeason, tier: Int, track: BattlePassTrack,
        rewardCount: Int, isPremium: Bool, playerId: String
    ) -> SdkEvent {
        SdkEvent(
            eventName: "battle_pass_claim",
            eventData: [
                "season_id": .string(season.seasonId),
                "tier": .int(tier),
                "track": .string(track.rawValue),
                "reward_count": .int(rewardCount),
                // `.int(0/1)`, not `.bool`: the backend column is INT64 and Android sends 0/1.
                // A JSON boolean survives only because Python's int(True) is 1 — working by
                // coercion is not the same as matching the wire contract.
                "is_premium": .int(isPremium ? 1 : 0),
                "battle_pass_version": .string(season.version)
            ],
            userProperty: UserProperty(userId: playerId)
        )
    }

    // MARK: - Timing & tiers

    /// Inclusive at BOTH ends: exactly `startMs` is active, and so is exactly `endMs` (§7).
    private static func phase(of season: BattlePassSeason, now: Int64) -> BattlePassPhase {
        if now < season.startMs { return .notStarted }
        if now > season.endMs { return .ended }
        return .active
    }

    /// `1.0` unless catch-up is configured, enabled, and the season has passed its start fraction —
    /// which is INCLUSIVE: exactly at the fraction, catch-up is on.
    ///
    /// Only ever called while `phase == .active`, so `now` is inside `[startMs, endMs]` and both
    /// subtractions are non-negative; `maxTimestampMs` bounds them well inside `Int64`.
    ///
    /// Compares against an absolute threshold rather than dividing. `elapsed / span >= fraction` is
    /// algebraically the same test, but it rounds twice — once forming the ratio and once comparing
    /// it — so a fraction like `0.3` against an odd span can land a millisecond either side of the
    /// boundary for reasons no one can see from the config. Multiplying instead rounds once, and it
    /// is also what Android computes (`start + (span * fraction).toLong()`), so the two platforms
    /// switch catch-up on at the same instant for the same season rather than approximately.
    ///
    /// The `Int64(...)` conversion is safe without a clamp here, and only here: `span` is bounded
    /// by `maxTimestampMs` and `startFraction` is validated into `(0, 1)` at parse time, so the
    /// product is strictly smaller than a value that already fits.
    private static func catchUpMultiplier(_ season: BattlePassSeason, now: Int64) -> Double {
        guard let catchUp = season.catchUp, catchUp.enabled else { return 1.0 }
        let span = season.endMs - season.startMs
        let thresholdMs = Int64(Double(span) * catchUp.startFraction)
        return (now - season.startMs) >= thresholdMs ? catchUp.multiplier : 1.0
    }

    /// Tier as a PURE function of XP and the tier table (§3.3) — the reason `lastTier` is not
    /// persisted. Tiers are strictly increasing by construction, so the last one the XP total
    /// meets is the highest.
    private static func tier(for xp: Int64, in season: BattlePassSeason) -> Int {
        season.tiers.last(where: { $0.xpRequired <= xp })?.tier ?? 0
    }

    /// §3.1 — the single line that would have to widen if premium were ever sold as an
    /// auto-renewing subscription. Called while holding `lock`, deliberately: ownership is part of
    /// the same atomic decision as the tier check and the claim-key write, and re-reading it
    /// outside the lock would reopen the TOCTOU window. Safe to nest: `Entitlements` takes its own
    /// leaf lock and never calls back into this type, so there is no cycle to deadlock on.
    private static func isPremium(_ season: BattlePassSeason, _ configuration: Configuration) -> Bool {
        guard let productId = season.premiumProductId else { return false }
        return configuration.entitlements.isActive(productId)
    }

    // MARK: - Persistence

    private static func claimKey(tier: Int, track: BattlePassTrack) -> String {
        "\(tier)\(pairSeparator)\(track.rawValue)"
    }

    /// A snapshot plus the sequence number that orders it. Both are produced inside ONE `lock`
    /// acquisition; the write itself happens after the lock is released.
    private struct PendingWrite {
        let line: String
        let sequence: UInt64
        let store: BrainKeyValueStore
    }

    /// Called only while holding `lock`. Encoding the snapshot and stamping its sequence number in
    /// the same acquisition is what makes "higher sequence" mean "encoded from newer state" — split
    /// them and `SequencedStoreWriter` would order the writes into a wrong answer. `nil` when there
    /// is no store to write to, which means the type was never configured.
    private func pendingWriteLocked() -> PendingWrite? {
        guard let store = configuration?.store else { return nil }
        return PendingWrite(line: encodeProgressLocked(), sequence: writer.nextSequenceNumber(), store: store)
    }

    /// Called only while holding `lock`. Sorted, deterministic encoding keeps the persisted string
    /// stable for a given state, which is what makes the round trip assertable.
    private func encodeProgressLocked() -> String {
        let keys = claimed.sorted().joined(separator: String(Self.claimSeparator))
        return "\(progressSeasonId)\(Self.entrySeparator)\(xp)\(Self.entrySeparator)\(keys)"
    }

    private struct LoadedProgress {
        var seasonId = ""
        var xp: Int64 = 0
        var claimed: Set<String> = []
        var note: String?
    }

    /// Three `|`-separated fields, the third a `,`-separated claim-key list.
    ///
    /// A record that cannot be read at all drops to EMPTY progress — but empty progress is itself
    /// destructive (it is a player's whole season), so §4 requires the load path to tell "no
    /// record" (a first launch, silent) from "unparseable record" (diagnosed). That is what `note`
    /// carries; `apply` surfaces it.
    ///
    /// Individual claim keys are more forgiving: an unreadable one is skipped and counted rather
    /// than costing the player every other key, matching how `RecurringGrants` treats a corrupt
    /// entry.
    private static func loadProgress(_ store: BrainKeyValueStore) -> LoadedProgress {
        guard let raw = store.string(forKey: stateKey), !raw.isEmpty else { return LoadedProgress() }

        var unreadable = LoadedProgress()
        unreadable.note = "the persisted progress record was unreadable -> progress reset to empty "
            + "(a first launch with no record at all carries no note)"

        let parts = raw.split(separator: entrySeparator, omittingEmptySubsequences: false)
        guard parts.count == 3 else { return unreadable }
        let seasonId = String(parts[0])
        guard !seasonId.isEmpty else { return unreadable }
        guard let storedXp = Int64(parts[1]), storedXp >= 0 else { return unreadable }

        var progress = LoadedProgress()
        progress.seasonId = seasonId
        progress.xp = Swift.min(storedXp, maxXp)
        var noteParts: [String] = []
        if storedXp > maxXp {
            noteParts.append("the persisted XP total exceeded the ceiling -> clamped to \(maxXp)")
        }
        var dropped = 0
        for key in parts[2].split(separator: claimSeparator) {
            guard isValidClaimKey(String(key)) else {
                dropped += 1
                continue
            }
            progress.claimed.insert(String(key))
        }
        if dropped > 0 { noteParts.append("dropped \(dropped) malformed claim key(s) from the record") }
        progress.note = noteParts.isEmpty ? nil : noteParts.joined(separator: "; ")
        return progress
    }

    private static func isValidClaimKey(_ key: String) -> Bool {
        let parts = key.split(separator: pairSeparator, omittingEmptySubsequences: false)
        guard parts.count == 2, let tier = Int(parts[0]), tier >= 1 else { return false }
        return BattlePassTrack(rawValue: String(parts[1])) != nil
    }
}
