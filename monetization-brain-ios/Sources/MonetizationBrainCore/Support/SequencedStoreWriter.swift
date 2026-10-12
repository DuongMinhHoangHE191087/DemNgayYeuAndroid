import Foundation

/// Totally orders one subsystem's writes to a `BrainKeyValueStore` **without** that subsystem ever
/// holding its decision lock across store I/O.
///
/// ═══ THE RACE THIS EXISTS TO CLOSE ═══
///
/// `BattlePass`, `RecurringGrants` and `SubscriptionLifecycle` all share one shape: take the lock,
/// mutate in-memory state, encode a FULL snapshot of the new state, release the lock, then persist
/// the snapshot. Steps 1-3 are correctly atomic — two callers can never both consume the same
/// claim. Step 4 is where it came apart.
///
/// Two concurrent callers A then B encode snapshots in that order, so B's snapshot is a strict
/// superset of A's. Nothing ordered their `setString` calls, so the two writes could land in the
/// OPPOSITE order and leave A's older snapshot as the final persisted value. In-memory state stays
/// correct for the life of the process, so nothing looks wrong at runtime — but after a restart,
/// storage is missing a claim that was genuinely consumed and already handed to the host app to
/// grant, and the player claims the same reward a second time. No jailbreak, no clock tampering,
/// no storage editing: ordinary concurrency plus an ordinary force-quit.
///
/// ═══ WHY NOT JUST MOVE `setString` INSIDE THE MAIN LOCK ═══
///
/// It would close the race in three lines, and it was not rejected by reflex. This package's rule
/// is that the main decision lock is never held across store I/O — the Phase 3 fix to
/// `DecisionEngine.beliefFor` exists precisely to stop doing that. Three concrete costs here:
///
/// 1. Every PURE query (`BattlePass.xpTotal`/`claimable`/`phase`, `SubscriptionLifecycle.stateFor`/
///    `dunningNoticeDue`) takes that same lock, and callers run them on the UI thread to render a
///    screen. A `UserDefaults` write under the lock makes each of them wait on a `CFPreferences`
///    round trip.
/// 2. The store is HOST-supplied (`Configuration.store` takes any `BrainKeyValueStore`). Widening
///    the lock to cover it means calling arbitrary host code while holding the decision lock.
/// 3. That host code could reach back into the SDK. `BattlePass` already nests `Entitlements`' leaf
///    lock under its own, so a host store that consulted `Entitlements` would close a cycle.
///
/// A serial `DispatchQueue` drained asynchronously was the other candidate: it orders writes just
/// as well and never blocks. It was rejected because it makes persistence outlive the call —
/// `claim()` would return a consumed reward to the host before the write had landed, a guaranteed
/// loss window on every call traded for a rare race. That is the same trade `SubscriptionLifecycle.
/// observe` documents when it awaits its event sends instead of detaching a `Task`; making the
/// store write fire-and-forget would contradict that decision directly.
///
/// ═══ HOW THIS ORDERS THEM INSTEAD ═══
///
/// Each snapshot is stamped with a monotonically increasing sequence number **assigned inside the
/// caller's main lock**, so sequence order is encode order by construction. The write then happens
/// under this type's own lock, which also holds `lastWritten`: a write whose sequence is not newer
/// than what has already landed for that key is DROPPED rather than allowed to clobber. The main
/// lock is never held across I/O, writes are totally ordered, and a straggler loses instead of
/// winning.
///
/// `writeLock` IS held across `store.setString`. That is deliberate and is not the rule above: it
/// is a leaf taken by no decision path, no query path and no other lock in this package, so it
/// cannot participate in a cycle, and the worst it can do is make two concurrent writers queue on
/// a store call they would have serialized inside anyway.
///
/// ═══ THE COUNTER IS PER-PROCESS AND IS DELIBERATELY NOT PERSISTED ═══
///
/// The argument FOR persisting it: sequence numbers restart at 1 on every launch, so a number
/// written by yesterday's process means nothing to today's.
///
/// The argument that wins: `lastWritten` is in-memory too and starts EMPTY, so a fresh process
/// compares its sequence numbers only against its own. Nothing from a previous process is ever in
/// flight to race with — that process is gone and its writes have long since landed or been lost
/// with it. The window this closes is strictly intra-process, so an intra-process token is exactly
/// the right scope. Persisting it would add a store read on `configure` and a store write to every
/// mutation for no ordering gained, and would invent a new failure mode: a corrupt or truncated
/// counter reading high would silently DROP every subsequent write, which is a worse version of
/// the bug being fixed.
///
/// The honest limit that follows: two PROCESSES sharing one store (a host app and an app extension
/// on the same `UserDefaults` suite) are not ordered by this. They are no worse off than before —
/// each has its own empty `lastWritten` and drops nothing — but fixing that case needs a
/// compare-and-set the `BrainKeyValueStore` protocol does not offer, and would be a protocol
/// change, not a call-site one.
///
/// `UInt64` overflow is not a real bound: at one write per nanosecond it wraps in ~585 years.
final class SequencedStoreWriter: @unchecked Sendable {
    /// Guards `nextSequence` only. Held for a single increment and never across anything else, so
    /// it can be taken from inside a caller's main lock without ordering consequences.
    private let sequenceLock = NSLock()
    private var nextSequence: UInt64 = 0

    /// Guards `lastWritten` AND the store call it gates — see the type doc on why that is safe.
    private let writeLock = NSLock()
    private var lastWritten: [String: UInt64] = [:]

    init() {}

    /// Stamps a snapshot.
    ///
    /// **MUST be called while holding the caller's main lock**, in the same acquisition that
    /// produced the snapshot. That is the whole contract: it is what makes "higher sequence" mean
    /// "encoded from newer state". Calling it after releasing the lock would hand out numbers in an
    /// order unrelated to the states they describe, and this type would order the writes into a
    /// wrong answer just as confidently.
    func nextSequenceNumber() -> UInt64 {
        sequenceLock.lock()
        defer { sequenceLock.unlock() }
        nextSequence += 1
        return nextSequence
    }

    /// Persists `value` unless a newer snapshot for `key` has already landed.
    func write(_ value: String, forKey key: String, sequence: UInt64, to store: BrainKeyValueStore) {
        writeLock.lock()
        defer { writeLock.unlock() }
        guard sequence > (lastWritten[key] ?? 0) else { return }
        store.setString(value, forKey: key)
        lastWritten[key] = sequence
    }

    /// Removal participates in the same ordering. Without this a straggler `write` could resurrect
    /// a record that a later `resetForTest` had already removed, which is the same clobber in the
    /// other direction.
    func remove(forKey key: String, sequence: UInt64, from store: BrainKeyValueStore) {
        writeLock.lock()
        defer { writeLock.unlock() }
        guard sequence > (lastWritten[key] ?? 0) else { return }
        store.removeObject(forKey: key)
        lastWritten[key] = sequence
    }
}
