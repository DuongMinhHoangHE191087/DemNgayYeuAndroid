import Foundation

/// The install-scoped, monotonically increasing counter behind `SdkEvent.sequenceId`.
///
/// ═══ WHY THIS EXISTS AS A TYPE ═══
///
/// `SdkEvent.sequenceId` is part of the backend wire contract (`backend/schema/events_raw.json`
/// column `sequence_id`, INT64; `backend/app/normalize.py` copies it onto every ingested row),
/// but until now nothing on iOS ever assigned it — every event this SDK has sent since Phase 1
/// shipped a constant `0`. This type is the port of Android's behaviour, which is the wire
/// reference:
///
/// ```kotlin
/// // src/main/java/com/app/plugin/logevent/LogEventServer.kt:95-100
/// val sequenceId = sequenceIdMutex.withLock {
///     var id = PreferenceHelper.getInstance().get("SEQUENCE_ID", 0)
///     id += 1
///     PreferenceHelper.getInstance().setValue("SEQUENCE_ID", id)
///     id
/// }
/// ```
///
/// Read precisely, that is: ONE counter for the whole SDK (not per event type, not per session),
/// pre-incremented so the FIRST event of a fresh install is `1` and `0` stays the "never
/// assigned" value, persisted to durable storage on every single bump so it survives process
/// death and app relaunch, and never reset — not on session start, not on app launch, not on
/// player-id change. Mutual exclusion around the read-modify-write is part of the contract too;
/// Android uses a `Mutex` for exactly this reason.
///
/// ═══ WHAT THE BACKEND DOES WITH IT ═══
///
/// It stores it, and nothing else — deduplication is keyed off `dedup_key`
/// (`backend/app/normalize.py:104-130`, built from `decision_id`/`order_id`/`transaction_id`),
/// NOT off this number. So the number's only job is ordering and gap detection on ingested rows.
/// That is precisely why it is stamped once per event and then carried unchanged through
/// retries: a gap in the ingested numbers should mean "an event never arrived", and a repeat
/// should mean "the same event arrived twice".
///
/// ═══ LOCKING ═══
///
/// `NSLock.lock()` is `@available(*, noasync)`, and this package's rule is that it is never
/// acquired inside an `async` function body. Every acquisition here goes through the synchronous
/// `withLock` helper — the same structure `SubscriptionLifecycle` documents — so `next()` stays
/// a synchronous call that an `async` caller (`EventQueue.send`) may make without ever holding a
/// lock across an `await`.
public final class EventSequenceCounter: @unchecked Sendable {
    /// Namespaced rather than reusing Android's bare `"SEQUENCE_ID"`: this store is shared with
    /// every other piece of SDK state (`iap_active_products`, `brain_bandit_stats`, …) and lives
    /// in the HOST APP's `UserDefaults`, where an unprefixed all-caps key is a collision waiting
    /// to happen. The wire contract is the numbering, not the storage key.
    static let storageKey = "monetization_brain_event_sequence_id"

    private let store: BrainKeyValueStore
    private let lock = NSLock()

    /// The last number handed out. `0` means none yet, so the first `next()` returns `1` —
    /// matching Android's `get("SEQUENCE_ID", 0)` + pre-increment exactly.
    private var lastIssued: Int

    public init(store: BrainKeyValueStore = UserDefaultsBrainStore()) {
        self.store = store
        self.lastIssued = Self.loadLastIssued(from: store)
    }

    /// A missing, non-numeric or negative stored value is treated as "start over from 0" rather
    /// than trapping or propagating. Restarting the numbering produces duplicate `sequence_id`s
    /// for one install, which the backend tolerates (it dedupes on `dedup_key`); crashing the
    /// host app because a preference got corrupted does not.
    private static func loadLastIssued(from store: BrainKeyValueStore) -> Int {
        guard let raw = store.string(forKey: storageKey),
              let value = Int(raw),
              value >= 0 else { return 0 }
        return value
    }

    /// Reserves and returns the next sequence number.
    ///
    /// The new value is persisted BEFORE it is returned, and that ordering is deliberate: a
    /// process killed between the write and the caller's use loses a number (a gap the backend
    /// can see), whereas persisting afterwards would let the same number be handed out twice
    /// after a crash. A visible gap is a better failure than a silent collision.
    public func next() -> Int {
        withLock {
            // `lastIssued` is an `Int` (64-bit): at one event per millisecond this would take
            // ~292 million years to reach. The guard exists so that a corrupted-then-clamped
            // store value can never turn into an arithmetic overflow trap in the host app.
            let issued = lastIssued == Int.max ? 1 : lastIssued + 1
            lastIssued = issued
            store.setString(String(issued), forKey: Self.storageKey)
            return issued
        }
    }

    /// The last number handed out, without issuing a new one. Test/diagnostic read.
    func lastIssuedValue() -> Int {
        withLock { lastIssued }
    }

    /// The ONLY place `lock` is acquired — synchronous and non-escaping, so it is structurally
    /// impossible to take the lock in an `async` body or hold it across an `await`.
    private func withLock<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }
}
