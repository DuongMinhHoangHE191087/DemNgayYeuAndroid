import Foundation

/// "This player owns a pass or subscription — is a periodic reward due, and what is it?"
/// (Android `com.app.plugin.iap.RecurringGrants`; design spec §1.)
///
/// ═══ THE SDK NEVER GRANTS ANYTHING ═══
///
/// `dueGrants()` and `claim(productId:rewardType:)` return an opaque `(rewardType, rewardAmount)`
/// pair; the host app's economy code is what actually grants it. Same boundary `PaywallManager`
/// ("what to sell", not a screen) and `IapHelper.purchase` already draw — this type owns
/// eligibility and timing, nothing else.
///
/// ═══ OWNERSHIP IS `isEntitled`, NOT `== .active` (§3.1) ═══
///
/// Android asks `SubscriptionLifecycle.stateFor(id) == ACTIVE`, which cuts off two groups of
/// people who have already paid for the period they are in: a subscriber who turned auto-renew
/// OFF (`.cancelled`), and one whose charge failed while Apple still honours the grace period
/// (`.inGracePeriod`). Android's own lifecycle class documents both as entitled, so its ownership
/// check contradicts the class it queries. Here the question is asked of the property that exists
/// to answer it:
///
///     subscriptionLifecycle.stateFor(productId).isEntitled || entitlements.isActive(productId)
///
/// `.inBillingRetry` is correctly NOT entitled: the payment has actually failed, and a player
/// whose card is being retried should not keep drawing rewards in the meantime.
///
/// ═══ NO CATCH-UP (§3.5) ═══
///
/// Missing five periods grants ONE reward, not five. A deliberate economy decision preserved from
/// Android, and a direct consequence of storing one scalar timestamp per line instead of a
/// missed-period counter.
///
/// ═══ ⚠️ THESE REWARDS ARE NOT FRAUD-PROOF (§3.4, §6) ═══
///
/// Timing is device-local wall clock. Because each claim rewrites the baseline FORWARD, a player
/// can claim, advance the device clock by one period, claim again, and repeat without bound — the
/// only cost to them is a wrong clock. Two mitigations were considered and rejected: rejecting
/// backwards clock movement punishes every honest user on a DST shift or NTP correction and does
/// not stop the forward-advance loop at all, and pairing with a monotonic reading does not survive
/// a relaunch. Neither prevents the attack; both add real failure modes for honest users.
///
/// The honest fix is server-authoritative timing, which is a backend project. Until then this is
/// the same line `Entitlements` already draws: **fine for cosmetics or soft currency, not for
/// anything with cash value.**
///
/// Two more stated limits: a reinstall resets claim history (and re-grants one period), and
/// periods are rolling from the last claim — there is no calendar/timezone "resets at midnight."
///
/// ═══ EVERY PATH IS SYNCHRONIZED (§3.7) ═══
///
/// Android synchronizes `claim()` but not `apply()` or `dueGrants()` — and `dueGrants()` reads the
/// very map `claim()` mutates. Here there is ONE lock, taken only through `withLock`, and every
/// decision that reads more than one field takes them in a single acquisition. A claim's
/// authorization (enabled + config + ownership + elapsed period) and the timestamp write that
/// consumes it are one atomic step, so no two concurrent callers can both claim one period. The
/// event is BUILT from that same snapshot and sent afterwards, outside the lock.
public final class RecurringGrants: @unchecked Sendable {
    public static let shared = RecurringGrants()

    public struct Configuration: Sendable {
        public let eventSink: EventSink
        public let playerId: String
        /// Where `lastClaimKey` lives. Defaults to `UserDefaults`, exactly as `Entitlements`/
        /// `SubscriptionLifecycle` do; tests inject an in-memory store.
        public let store: BrainKeyValueStore
        /// The time seam (§3.3) — see `Support/BrainClock.swift`.
        public let clock: BrainClock
        /// Ownership source 1 (Phase 4). Injected rather than hard-wired to `.shared` so tests can
        /// drive ownership without polluting a process-wide singleton.
        public let entitlements: Entitlements
        /// Ownership source 2 (Phase 7) — the one whose `isEntitled` §3.1 is about.
        public let subscriptionLifecycle: SubscriptionLifecycle

        public init(
            eventSink: EventSink,
            playerId: String,
            store: BrainKeyValueStore = UserDefaultsBrainStore(),
            clock: BrainClock = SystemClock(),
            entitlements: Entitlements = .shared,
            subscriptionLifecycle: SubscriptionLifecycle = .shared
        ) {
            self.eventSink = eventSink
            self.playerId = playerId
            self.store = store
            self.clock = clock
            self.entitlements = entitlements
            self.subscriptionLifecycle = subscriptionLifecycle
        }
    }

    // MARK: - Constants

    /// Internal (not private) so the test file can assert against the exact persisted key name
    /// without duplicating the string literal — matching `SubscriptionLifecycle`.
    static let lastClaimKey = "recurring_grants_last_claim"

    /// ═══ THE PERSISTED SHAPE IS THE ONE ANDROID GOT WRONG (§3.2) ═══
    ///
    /// Android builds its composite key as `productId|rewardType|periodHours` and then joins
    /// entries with that same `|`, so `['vip_pass|gem|24', 'vip_pass|ticket|168']` restores as
    /// `['168', '24']`. Restored keys never match `keyOf(grant)` again, `lastClaim[key] ?? 0`
    /// returns `0`, and **every grant is due again after every process restart** — a player
    /// force-quits and relaunches to farm unlimited rewards, no clock manipulation needed.
    ///
    /// The design spec's §4 describes that same key composition alongside a `|`-joined entry
    /// list, which does not round-trip either. So the composite key here joins its components
    /// with `:` instead: entries are `|`-separated, each entry splits at its LAST `:` into
    /// `productId:rewardType:periodHours` and a timestamp, and since §3.2's parse-time validation
    /// guarantees neither `productId` nor `rewardType` contains `|` or `:`, both splits are
    /// unambiguous in both directions. `test_claimTimestampsRoundTripAcrossAFreshInstanceOverThe
    /// SameStore` is the test that holds this — the test whose absence let the Android bug ship.
    private static let entrySeparator: Character = "|"
    private static let pairSeparator: Character = ":"

    private static let notLoadedVersion = "not loaded"
    private static let msPerHour: Int64 = 3_600_000
    /// One year. A period beyond this is operator error, and clamping bounds `periodHours *
    /// msPerHour` well inside `Int64`.
    private static let minPeriodHours = 1
    private static let maxPeriodHours = 8_760
    private static let minRewardAmount: Int64 = 1
    /// Half of `Int64.max`, so a host app can add a grant to an existing balance without having to
    /// defend against its own overflow.
    private static let maxRewardAmount: Int64 = Int64.max / 2
    /// Year 9999 in epoch milliseconds. A persisted timestamp beyond this is corruption, not
    /// history; dropping it makes the line due rather than letting a nonsense baseline sit in the
    /// map forever.
    private static let maxTimestampMs: Int64 = 253_402_300_799_000

    // MARK: - State

    private let lock = NSLock()
    /// Orders this type's writes to `lastClaimKey` without ever holding `lock` across store I/O —
    /// see `SequencedStoreWriter` for the double-grant this closes and the alternatives weighed.
    private let writer = SequencedStoreWriter()
    private var configuration: Configuration?
    private var version = RecurringGrants.notLoadedVersion
    private var enabled = true
    private var grants: [RecurringGrant] = []
    /// `keyOf(grant)` -> epoch milliseconds of the last successful claim.
    private var lastClaim: [String: Int64] = [:]

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

    /// Wires up the event sink, player id, persistence, clock and both ownership sources — and
    /// loads previously persisted claim timestamps from that store. The store read happens BEFORE
    /// the lock is taken; the lock only covers the field assignment.
    public func configure(_ configuration: Configuration) {
        let loaded = Self.loadLastClaim(configuration.store)
        withLock {
            self.configuration = configuration
            self.lastClaim = loaded
        }
    }

    public func activeVersion() -> String {
        withLock { version }
    }

    /// Test-only, and INTERNAL on purpose (§3.6). Android's equivalent (`clear()`) is public and
    /// unguarded, and resets CONFIG as well as claim history — `enabled` back to `true`, `grants`
    /// emptied — so host code can silently re-enable a feature ops disabled by remote config.
    /// Matching `Entitlements`/`PurchaseRouter`/`PaywallManager`/`SubscriptionLifecycle`, this
    /// stays inside the module.
    func resetForTest() {
        let pending = withLock { () -> (BrainKeyValueStore, UInt64)? in
            let store = configuration?.store
            configuration = nil
            version = Self.notLoadedVersion
            enabled = true
            grants = []
            lastClaim = [:]
            guard let store else { return nil }
            return (store, writer.nextSequenceNumber())
        }
        guard let pending else { return }
        writer.remove(forKey: Self.lastClaimKey, sequence: pending.1, from: pending.0)
    }

    // MARK: - Config

    /// Parses and applies a `recurring_grants_ios` remote-config payload. NEVER throws out to the
    /// caller — a malformed config just means the currently-running config keeps running, matching
    /// `PurchaseRouter`/`PaywallManager`.
    ///
    /// Strictness is deliberately UNIFORM, which Android's is not: Android rejects the WHOLE config
    /// over one malformed `grants` element while silently defaulting a wrong-typed `enabled` to
    /// `true` with no diagnostic (§3.6). Here a malformed line is dropped with a note and the rest
    /// of the config applies (matching `PaywallManager`'s variant handling), and a wrong-typed
    /// `enabled` degrades to its default WITH a note via `JsonConfigValue.flag`.
    ///
    /// Dropping a line fails CLOSED: a dropped line grants nothing.
    ///
    /// Claim history is deliberately NOT touched here. A config reload — including one that
    /// disables the feature — must never hand out a free period by resetting timestamps.
    @discardableResult
    public func apply(_ json: String?) -> RecurringGrantsApplyResult {
        guard let json, !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return reject("empty config")
        }
        guard let data = json.data(using: .utf8),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return reject("JSON did not parse to an object")
        }
        guard let ver = (root["version"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !ver.isEmpty else {
            return reject("missing 'version' field")
        }

        var notes: [String] = []
        let parsedEnabled = JsonConfigValue.flag(root["enabled"], name: "enabled", defaultingTo: true, notes: &notes)
        let parsedGrants = Self.parseGrants(root["grants"], notes: &notes)

        withLock {
            version = ver
            enabled = parsedEnabled
            grants = parsedGrants
        }

        var report = "loaded '\(ver)', \(parsedGrants.count) grant line(s), enabled=\(parsedEnabled)"
        if !notes.isEmpty {
            report += "\n  \u{26A0} " + notes.joined(separator: "\n  \u{26A0} ")
        }
        return RecurringGrantsApplyResult(applied: true, report: report)
    }

    private func reject(_ reason: String) -> RecurringGrantsApplyResult {
        RecurringGrantsApplyResult(
            applied: false,
            report: "REJECTED recurring grants config (\(reason)) — keeping '\(activeVersion())'"
        )
    }

    private static func parseGrants(_ raw: Any?, notes: inout [String]) -> [RecurringGrant] {
        guard let raw else {
            notes.append("no 'grants' array -> nothing is ever due")
            return []
        }
        guard let rawGrants = raw as? [Any] else {
            notes.append("'grants' is not an array -> nothing is ever due")
            return []
        }

        var parsed: [RecurringGrant] = []
        var seen: Set<String> = []
        for element in rawGrants {
            guard let m = element as? [String: Any] else {
                notes.append("a grant entry is not an object -> dropped")
                continue
            }
            // The forbidden delimiters are passed in, not assumed: they are exactly the
            // characters `encode`/`decode` join and split on, so an id containing one of them
            // would not survive the round trip through the store (§3.2, defense 1 of 2).
            guard let productId = JsonConfigValue.identifier(
                m["product_id"],
                field: "product_id",
                label: "a grant",
                mustNotContain: [entrySeparator, pairSeparator],
                notes: &notes
            ), let rewardType = JsonConfigValue.identifier(
                m["reward_type"],
                field: "reward_type",
                label: "a grant",
                mustNotContain: [entrySeparator, pairSeparator],
                notes: &notes
            ) else {
                continue
            }
            let label = "grant '\(productId)/\(rewardType)'"
            guard let periodHours = JsonConfigValue.clampedInt(
                m["period_hours"],
                field: "period_hours",
                label: label,
                lowerBound: minPeriodHours,
                upperBound: maxPeriodHours,
                notes: &notes
            ) else { continue }
            guard let rewardAmount = JsonConfigValue.clampedInt64(
                m["reward_amount"],
                field: "reward_amount",
                label: label,
                lowerBound: minRewardAmount,
                upperBound: maxRewardAmount,
                notes: &notes
            ) else { continue }

            let grant = RecurringGrant(
                productId: productId,
                periodHours: periodHours,
                rewardType: rewardType,
                rewardAmount: rewardAmount
            )
            guard seen.insert(keyOf(grant)).inserted else {
                notes.append("\(label) at \(periodHours)h is declared more than once -> first wins, later line dropped")
                continue
            }
            parsed.append(grant)
        }
        return parsed
    }

    // MARK: - Queries

    /// Every grant line this player currently OWNS and whose period has elapsed.
    ///
    /// PURE (§1): no mutation, no persistence, no event — calling it twice is exactly as
    /// side-effect-free as calling it once, and it does NOT consume anything. `claim` is the only
    /// mutating entry point, and the host app must call it to actually take a reward.
    ///
    /// Empty when the feature is disabled by remote config, which hides state without destroying
    /// it: flipping `enabled` back on restores exactly the claim timestamps that were already
    /// known, so a disable/enable cycle never hands out a free period.
    public func dueGrants() -> [RecurringGrant] {
        withLock {
            guard let configuration, enabled else { return [] }
            let now = configuration.clock.nowMs()
            return grants.filter { grant in
                owns(grant.productId, configuration)
                    && Self.isDue(last: lastClaim[Self.keyOf(grant)], now: now, periodHours: grant.periodHours)
            }
        }
    }

    // MARK: - Claim

    /// Consumes one period of one grant line and returns what the host app should grant.
    ///
    /// The ONE mutating entry point. `claimed == false` means nothing was consumed and nothing
    /// should be granted — `rewardAmount` is `0` on that path, and `report` says why (for logs and
    /// debug UI, never for control flow).
    ///
    /// When a product declares several lines for the same `rewardType` on different cadences, the
    /// first DUE line in config order is the one consumed (config order being the order the
    /// operator wrote, and the same order the duplicate rule's "first wins" follows). The design
    /// spec leaves this open; picking the first due line rather than simply the first line keeps
    /// every configured line independently claimable.
    ///
    /// **Integrator caveat:** it follows that several same-`rewardType` lines for one product are
    /// not individually addressable through this API — there is no way to say "claim the 168h one."
    /// `dueGrants()` can return both, so a host rendering a button per returned line should expect
    /// each `claim` to consume whichever is due, not the specific line the button was drawn from.
    /// If a host ever needs to target one exactly, the reward types should differ, or this method
    /// needs a `periodHours` argument — better to discover that here than at integration time.
    ///
    /// `async` because it awaits its own event send rather than handing it to a detached `Task` —
    /// see `SubscriptionLifecycle.observe`'s doc comment for the drop window that closes: a claim
    /// consumed, persisted and returned to the caller while its event never reaches the queue
    /// because the process ended before the detached task ran. Every other event producer in this
    /// package awaits for the same reason.
    ///
    /// Authorization and consumption are ONE lock acquisition, so two concurrent callers can never
    /// both claim the same period; the event is built from that same snapshot (its
    /// `recurring_grants_version` therefore cannot drift from the config that authorised the
    /// claim) and sent after the lock is released.
    @discardableResult
    public func claim(productId: String, rewardType: String) async -> RecurringGrantClaimResult {
        let outcome = withLock { () -> ClaimOutcome in
            func rejected(_ reason: String) -> ClaimOutcome {
                var outcome = ClaimOutcome()
                outcome.configuration = configuration
                outcome.result = RecurringGrantClaimResult(
                    claimed: false,
                    rewardType: rewardType,
                    rewardAmount: 0,
                    report: "no grant for '\(productId)'/'\(rewardType)': \(reason)"
                )
                return outcome
            }

            guard let configuration else { return rejected("RecurringGrants is not configured") }
            guard enabled else { return rejected("recurring grants are disabled by config '\(version)'") }
            let matching = grants.filter { $0.productId == productId && $0.rewardType == rewardType }
            guard !matching.isEmpty else { return rejected("no such grant line in config '\(version)'") }
            guard owns(productId, configuration) else {
                return rejected("the player does not own '\(productId)'")
            }

            let now = configuration.clock.nowMs()
            guard let grant = matching.first(where: {
                Self.isDue(last: lastClaim[Self.keyOf($0)], now: now, periodHours: $0.periodHours)
            }) else {
                // Report the line closest to elapsing, not `matching[0]`. With several cadences on
                // one `rewardType`, config order is arbitrary with respect to remaining wait: a
                // 168h line declared before a 24h line would otherwise have a support engineer
                // reading "6 days" off a debug screen when the real wait is a minute. This string
                // is diagnostic only and never drives control flow, which is exactly why it has to
                // be the honest number — nothing downstream will correct it.
                let soonest = matching.min {
                    Self.remainingMs(last: lastClaim[Self.keyOf($0)], now: now, periodHours: $0.periodHours)
                        < Self.remainingMs(last: lastClaim[Self.keyOf($1)], now: now, periodHours: $1.periodHours)
                }
                let line = soonest ?? matching[0]
                let remaining = Self.remainingMs(
                    last: lastClaim[Self.keyOf(line)], now: now, periodHours: line.periodHours
                )
                return rejected(
                    "the \(line.periodHours)h period has not elapsed yet (~\(Self.roundedUpHours(remaining))h remaining)"
                )
            }

            lastClaim[Self.keyOf(grant)] = now

            var outcome = ClaimOutcome()
            outcome.configuration = configuration
            // The snapshot and the sequence number that orders it are produced in the SAME
            // acquisition that consumed the period — that pairing is what makes "higher sequence"
            // mean "encoded from newer state".
            outcome.pending = PendingWrite(
                line: Self.encode(lastClaim),
                sequence: writer.nextSequenceNumber(),
                store: configuration.store
            )
            outcome.event = Self.claimEvent(grant, version: version, playerId: configuration.playerId)
            outcome.result = RecurringGrantClaimResult(
                claimed: true,
                rewardType: grant.rewardType,
                rewardAmount: grant.rewardAmount,
                report: "granted \(grant.rewardAmount) '\(grant.rewardType)' for '\(grant.productId)' (\(grant.periodHours)h period)"
            )
            return outcome
        }

        // Persist and emit OUTSIDE the lock — never hold it across store I/O or an await. The
        // write carries the sequence number stamped inside that same acquisition, so a snapshot
        // encoded before this one can never overwrite it (`SequencedStoreWriter`).
        if let pending = outcome.pending {
            writer.write(pending.line, forKey: Self.lastClaimKey, sequence: pending.sequence, to: pending.store)
        }
        if let event = outcome.event, let sink = outcome.configuration?.eventSink {
            await sink.send(event)
        }
        return outcome.result
    }

    private struct ClaimOutcome {
        var result = RecurringGrantClaimResult(claimed: false, rewardType: "", rewardAmount: 0, report: "")
        var pending: PendingWrite?
        var event: SdkEvent?
        var configuration: Configuration?
    }

    /// A snapshot plus the sequence number that orders it. Both are produced inside ONE `lock`
    /// acquisition; the write itself happens after the lock is released.
    private struct PendingWrite {
        let line: String
        let sequence: UInt64
        let store: BrainKeyValueStore
    }

    /// Emitted only on a successful claim (§5). Not consent-gated, matching `iap_purchase`/
    /// `subscription_state`: no PII and no receipt value. `sequenceId` is `EventQueue`'s to assign
    /// and is never set at a call site.
    private static func claimEvent(_ grant: RecurringGrant, version: String, playerId: String) -> SdkEvent {
        SdkEvent(
            eventName: "recurring_grant_claim",
            eventData: [
                "product_id": .string(grant.productId),
                "reward_type": .string(grant.rewardType),
                "reward_amount": .int(Int(grant.rewardAmount)),
                "period_hours": .int(grant.periodHours),
                "recurring_grants_version": .string(version)
            ],
            userProperty: UserProperty(userId: playerId)
        )
    }

    // MARK: - Ownership & timing

    /// §3.1. Called while holding `lock`, deliberately: ownership is part of the same atomic
    /// decision as the elapsed-period check and the timestamp write, and re-reading it outside the
    /// lock would reopen the TOCTOU window. Safe to nest: `Entitlements` and
    /// `SubscriptionLifecycle` each take their own leaf lock and neither calls back into this type,
    /// so there is no cycle to deadlock on.
    private func owns(_ productId: String, _ configuration: Configuration) -> Bool {
        configuration.subscriptionLifecycle.stateFor(productId: productId).isEntitled
            || configuration.entitlements.isActive(productId)
    }

    /// A line never claimed on this device is due immediately — that is what makes a brand-new
    /// owner's first reward instant, and (§6) what makes a reinstall re-grant one period.
    ///
    /// A clock that moved BACKWARDS simply yields a not-yet-elapsed period, so it cannot grant
    /// early. Subtraction is done with overflow reporting rather than trusting the two values:
    /// `now` comes from an injected clock and `last` from a store file, and a trap in a host app
    /// is never an acceptable answer to bad data.
    private static func isDue(last: Int64?, now: Int64, periodHours: Int) -> Bool {
        guard let last, last > 0 else { return true }
        let (elapsed, overflow) = now.subtractingReportingOverflow(last)
        guard !overflow else { return false }
        return elapsed >= Int64(periodHours) * msPerHour
    }

    /// Milliseconds still to wait before this line is due; `0` once it is. Diagnostic only — the
    /// due/not-due decision is `isDue`'s, never a comparison against this.
    ///
    /// Overflow (a corrupt or hostile stored timestamp) reports `Int64.max` rather than trapping,
    /// which sorts the line LAST when picking the soonest — the same fail-closed direction `isDue`
    /// takes when it refuses to call an overflowing line due.
    private static func remainingMs(last: Int64?, now: Int64, periodHours: Int) -> Int64 {
        guard let last, last > 0 else { return 0 }
        let (elapsed, overflow) = now.subtractingReportingOverflow(last)
        guard !overflow else { return Int64.max }
        let (remaining, subOverflow) = (Int64(periodHours) * msPerHour).subtractingReportingOverflow(elapsed)
        guard !subOverflow else { return Int64.max }
        return max(0, remaining)
    }

    /// Whole hours, rounded up, for a human-readable wait. `0` stays `0`.
    private static func roundedUpHours(_ ms: Int64) -> Int64 {
        guard ms > 0 else { return 0 }
        guard ms < Int64.max - msPerHour else { return Int64.max / msPerHour }
        return (ms + msPerHour - 1) / msPerHour
    }

    // MARK: - Persistence

    /// The identity of a grant line. Sorted, deterministic encoding keeps the persisted string
    /// stable for a given map, which makes the round-trip assertable.
    private static func keyOf(_ grant: RecurringGrant) -> String {
        "\(grant.productId)\(pairSeparator)\(grant.rewardType)\(pairSeparator)\(grant.periodHours)"
    }

    private static func encode(_ lastClaim: [String: Int64]) -> String {
        lastClaim
            .map { "\($0.key)\(pairSeparator)\($0.value)" }
            .sorted()
            .joined(separator: String(entrySeparator))
    }

    /// Entries split on `|`, each entry split at its LAST `:`. An unreadable entry is skipped
    /// rather than failing the whole load — one corrupt entry must not cost a player every other
    /// line's claim history (and, since a missing timestamp reads as "never claimed", skipping is
    /// the generous direction, matching how a reinstall already behaves).
    private static func loadLastClaim(_ store: BrainKeyValueStore) -> [String: Int64] {
        guard let raw = store.string(forKey: lastClaimKey), !raw.isEmpty else { return [:] }
        var result: [String: Int64] = [:]
        for entry in raw.split(separator: entrySeparator) {
            guard let index = entry.lastIndex(of: pairSeparator) else { continue }
            let key = String(entry[entry.startIndex..<index])
            guard !key.isEmpty,
                  let timestamp = Int64(entry[entry.index(after: index)...]),
                  timestamp > 0, timestamp <= maxTimestampMs else { continue }
            result[key] = timestamp
        }
        return result
    }
}
