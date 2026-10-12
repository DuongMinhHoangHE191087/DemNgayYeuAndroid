import Foundation

/// Minimal key-value persistence `BrainStorage` needs. An injectable protocol — matching the
/// precedent `RemoteConfigProvider` already set — rather than wrapping `UserDefaults` directly,
/// so `DecisionEngine` stays unit-testable against an in-memory fake instead of polluting real
/// `UserDefaults` across test runs.
public protocol BrainKeyValueStore: Sendable {
    func string(forKey key: String) -> String?
    func setString(_ value: String, forKey key: String)
    func removeObject(forKey key: String)
}

/// Default `BrainKeyValueStore` backed by `UserDefaults`.
///
/// `@unchecked Sendable` rather than plain `Sendable`, and the distinction is deliberate.
/// `BrainKeyValueStore` requires `Sendable`, but `UserDefaults` is not marked `Sendable` by
/// Foundation, so the compiler cannot verify this — under Swift 6 that is a hard error, not a
/// warning.
///
/// The promise being made: `UserDefaults` is documented by Apple as thread-safe, and this type
/// holds it in a `let` and never mutates its own storage. So the unchecked claim is backed by a
/// real guarantee from the platform, not by hoping. It is spelled out because `@unchecked` is how
/// a genuine race gets silenced too, and a reader deserves to know which of the two this is.
public struct UserDefaultsBrainStore: BrainKeyValueStore, @unchecked Sendable {
    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public func string(forKey key: String) -> String? {
        defaults.string(forKey: key)
    }

    public func setString(_ value: String, forKey key: String) {
        defaults.set(value, forKey: key)
    }

    public func removeObject(forKey key: String) {
        defaults.removeObject(forKey: key)
    }
}

/// Durable storage for the brain, on top of an injected `BrainKeyValueStore`.
///
/// Why this exists: `DecisionEngine`'s belief/bandit state is otherwise in-memory only and is
/// lost the moment the process is killed. Without this, every app launch resets every player to
/// a uniform prior, and personalization never accumulates across sessions. This is a silent
/// failure that's easy to miss testing manually (everything looks correct within one session).
///
/// Not ported: `playerId()` (Android's self-generated stable device id) — unneeded, since
/// `AdsBrain.Configuration.playerId` is already a required field. `loadOfferCount24h`/
/// `bumpOfferCount24h` — not called by `DecisionEngine`; belongs to a call site not yet ported.
public final class BrainStorage: @unchecked Sendable {
    private static let beliefKeyPrefix = "brain_belief_"
    private static let firstPurchaseSignalKeyPrefix = "crm_first_purchase_"
    private static let banditStatsKey = "brain_bandit_stats"
    /// Internal (not private) so a test file can clear them from `UserDefaults` without
    /// duplicating the literals — the same reason `RecurringGrants.lastClaimKey` is internal.
    static let offerCountKey = "brain_offer_count_24h"
    static let offerDayKey = "brain_offer_day"

    private let store: BrainKeyValueStore

    /// ═══ THE DAILY IAP-OFFER COUNT, AND WHY IT IS A CALENDAR DAY ═══
    ///
    /// Backs `PlayerState.iapOffers24h`, which the `max_iap_offers_24h` guardrail reads. Until this
    /// existed the count lived only in memory and reset on every `onSessionStart`, so the daily
    /// ceiling that `BrainPolicy` lets an operator configure was defeated by a relaunch — a player
    /// who closed and reopened the app started the day's allowance over.
    ///
    /// The window is a UTC calendar DAY, not a sliding 24 hours, matching Android: a sliding
    /// window needs a timestamp per offer, a calendar day needs one counter and one day stamp. The
    /// known trade, stated there and here: a player just after midnight gets a fresh allowance.
    /// Acceptable for an anti-annoyance ceiling; NOT acceptable if this were ever used for a spend
    /// limit, which is why nothing about money reads it.
    ///
    /// Cached in memory behind `offerLock` after one read at init, so the bump is a
    /// read-modify-write under a lock with NO store I/O inside it — the write goes through
    /// `SequencedStoreWriter` after release, with both keys stamped by ONE sequence from the same
    /// acquisition so a day stamp can never land without its count.
    private let offerLock = NSLock()
    private var offerDay: Int
    private var offerCount: Int
    private let clock: BrainClock

    public init(store: BrainKeyValueStore, clock: BrainClock = SystemClock()) {
        self.clock = clock
        // Read BEFORE any lock exists to be held — same shape as `Entitlements`' init.
        self.offerDay = Int(store.string(forKey: Self.offerDayKey) ?? "") ?? 0
        self.offerCount = Int(store.string(forKey: Self.offerCountKey) ?? "") ?? 0
        self.store = store
    }

    public func loadBelief(playerId: String) -> String? {
        let raw = store.string(forKey: Self.beliefKeyPrefix + playerId)
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return raw
    }

    /// ═══ WRITES ARE ORDERED, NOT JUST SYNCHRONIZED ═══
    ///
    /// Phase 9's security review found that computing a snapshot under a lock and then writing it
    /// OUTSIDE that lock leaves the writes unordered: two callers can serialize in the right order
    /// and land in the wrong one, leaving the older snapshot persisted. In-memory state stays
    /// correct, so it surfaces only after a restart. In the IAP subsystems that was a double-grant;
    /// here it is belief drift — smaller, but the same defect.
    ///
    /// Every write goes through `SequencedStoreWriter`, which drops any write that is not newer
    /// than what already landed for that key. Keys are tracked independently, so per-player belief
    /// and the global bandit stats never shadow each other.
    private let writer = SequencedStoreWriter()

    /// Mint a sequence number INSIDE the acquisition that produced the snapshot you are about to
    /// save — see `BeliefState.updateAndSerialize`. Minting it at write time instead orders the
    /// writes by when they were issued rather than by what they contain, which is the bug.
    /// Has a `first_purchase` CRM signal already been sent for this player, on this device, ever?
    ///
    /// `PlayerState.purchaseCountLifetime` is in-memory and session-scoped, so on its own "first
    /// purchase" means "first since the process started" — every relaunch-then-purchase would send
    /// another one. For the highest-value trigger a marketing platform has, that means repeatedly
    /// re-running the welcome campaign for a player who is not new.
    ///
    /// One boolean key per player closes it. Deliberately NOT part of the belief record: this
    /// outlives any single season of brain state and must survive a belief reset.
    public func hasSentFirstPurchaseSignal(playerId: String) -> Bool {
        store.string(forKey: Self.firstPurchaseSignalKeyPrefix + playerId) == "1"
    }

    public func markFirstPurchaseSignalSent(playerId: String) {
        writer.write(
            "1",
            forKey: Self.firstPurchaseSignalKeyPrefix + playerId,
            sequence: nextWriteSequence(),
            to: store
        )
    }

    /// UTC day index — `nowMs / 86_400_000`, exactly Android's `today()`.
    private func today() -> Int {
        Int(clock.nowMs() / 86_400_000)
    }

    /// Offers already shown today. `0` when the stored day is not today, so a stale count from
    /// yesterday can never carry over — it is simply not read.
    public func loadOfferCount24h() -> Int {
        offerLock.lock()
        defer { offerLock.unlock() }
        return offerDay == today() ? offerCount : 0
    }

    /// Records one more offer today and returns the new total. Atomic against a concurrent bump:
    /// two callers cannot both read N and both write N+1.
    @discardableResult
    public func bumpOfferCount24h() -> Int {
        let (line, day, sequence): (String, Int, UInt64) = {
            offerLock.lock()
            defer { offerLock.unlock() }
            let day = today()
            if offerDay != day {
                offerDay = day
                offerCount = 0
            }
            offerCount += 1
            return (String(offerCount), day, writer.nextSequenceNumber())
        }()
        writer.write(String(day), forKey: Self.offerDayKey, sequence: sequence, to: store)
        writer.write(line, forKey: Self.offerCountKey, sequence: sequence, to: store)
        return Int(line) ?? 0
    }

    public func nextWriteSequence() -> UInt64 {
        writer.nextSequenceNumber()
    }

    public func saveBelief(playerId: String, serialized: String, sequence: UInt64) {
        writer.write(serialized, forKey: Self.beliefKeyPrefix + playerId, sequence: sequence, to: store)
    }

    /// Convenience for callers with no separate snapshot step, where "newest write wins" and
    /// "newest state wins" are the same thing. A caller that computes a snapshot under its own lock
    /// must use the `sequence:` overload instead.
    public func saveBelief(playerId: String, serialized: String) {
        saveBelief(playerId: playerId, serialized: serialized, sequence: nextWriteSequence())
    }

    public func clearBelief(playerId: String, sequence: UInt64) {
        writer.remove(forKey: Self.beliefKeyPrefix + playerId, sequence: sequence, from: store)
    }

    /// Removal participates in the same ordering, so a straggling `saveBelief` cannot resurrect a
    /// belief this call cleared.
    public func clearBelief(playerId: String) {
        clearBelief(playerId: playerId, sequence: nextWriteSequence())
    }

    public func loadBanditStats() -> String? {
        let raw = store.string(forKey: Self.banditStatsKey)
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return raw
    }

    public func saveBanditStats(_ serialized: String, sequence: UInt64) {
        writer.write(serialized, forKey: Self.banditStatsKey, sequence: sequence, to: store)
    }

    public func saveBanditStats(_ serialized: String) {
        saveBanditStats(serialized, sequence: nextWriteSequence())
    }
}
