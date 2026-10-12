import Foundation

/// The player's currently-owned products, and the sole bridge point to an ad-control facade.
///
/// ═══ WHY THIS TYPE EXISTS ═══
///
/// Android doc 27 §B.3 records a real anti-pattern from a competitor teardown: **a subscriber
/// still sees ads**. The root cause wasn't malice — purchase state and the ad-disable flag were
/// two separate things, owned by two separate pieces of code, and they drifted. This type is the
/// ONE place: every entitlement change flows through `recompute()`, and `recompute()` is the
/// ONLY caller of the (not-yet-existing on iOS, see design spec §1) ad-control facade — no other
/// code path may call it directly.
///
/// ═══ LIMITS THAT MUST BE STATED PLAINLY ═══
///
/// This is CLIENT-SIDE state, trusting whatever the store reports on-device. It is NOT a
/// fraud-resistant source of truth — server-side entitlement verification (design spec §1,
/// deferred) is the real source of truth once it exists. For `hasRemoveAds()`, the risk is
/// acceptable: the worst case of being fooled is someone seeing fewer ads. **This type must
/// never be used to grant an item with real redeemable value** until server-side receipt
/// verification exists.
public final class Entitlements: @unchecked Sendable {
    public static let shared = Entitlements()

    private static let activeKey = "iap_active_products"
    private static let removeAdsKey = "iap_remove_ads_products"
    /// INTERNAL rather than private: `BrainPolicy` validates artifact-supplied product ids
    /// against this exact character before writing them here, and a second copy of `"|"` in that
    /// file is one edit away from drifting out of sync with the encoder that depends on it.
    static let separator = "|"

    private let lock = NSLock()
    private let store: BrainKeyValueStore
    private var active: Set<String>
    private var _removeAdsProductIds: Set<String>

    public init(store: BrainKeyValueStore = UserDefaultsBrainStore()) {
        self.store = store
        self.active = Self.loadSet(store: store, key: Self.activeKey)
        self._removeAdsProductIds = Self.loadSet(store: store, key: Self.removeAdsKey)
    }

    /// ═══ WHY THE STORE WRITE HAPPENS AFTER THE LOCK IS RELEASED ═══
    ///
    /// Every mutator here used to call `store.setString` while holding `lock`. That broke this
    /// package's own rule — the decision lock is never held across store I/O — and it was not
    /// theoretical: `BattlePass.claim` and `RecurringGrants.claim` call into `isActive` WHILE
    /// holding their own lock, so a concurrent `grant()` could make a claim path wait on a lock
    /// held across a `UserDefaults` round trip.
    ///
    /// The fix is the one Phase 9 established: mutate under the lock, take the snapshot and its
    /// sequence number in the SAME acquisition, release, then write through
    /// `SequencedStoreWriter`, which drops any write that a newer snapshot has already overtaken.
    /// Moving the write out without the sequence would have swapped a blocking lock for a lost
    /// update, which is the trade that produced the Phase 9 double-grant.
    private let writer = SequencedStoreWriter()

    private struct PendingWrite {
        let line: String
        let key: String
        let sequence: UInt64
    }

    /// MUST be called with `lock` held.
    private func pendingWriteLocked(_ values: Set<String>, key: String) -> PendingWrite {
        PendingWrite(
            line: values.joined(separator: Self.separator),
            key: key,
            sequence: writer.nextSequenceNumber()
        )
    }

    private func flush(_ pending: PendingWrite?) {
        guard let pending else { return }
        writer.write(pending.line, forKey: pending.key, sequence: pending.sequence, to: store)
    }

    private static func loadSet(store: BrainKeyValueStore, key: String) -> Set<String> {
        guard let raw = store.string(forKey: key), !raw.isEmpty else { return [] }
        return Set(raw.split(separator: Character(separator)).map(String.init))
    }

    // MARK: - Read

    public func hasRemoveAds() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return !active.isDisjoint(with: _removeAdsProductIds)
    }

    public func isActive(_ productId: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return active.contains(productId)
    }

    public func activeProductIds() -> Set<String> {
        lock.lock()
        defer { lock.unlock() }
        return active
    }

    // MARK: - Configuration

    /// Which products disable ads. Usually sourced from remote config so it can change without a
    /// release. Recomputes `hasRemoveAds()` immediately: someone who already owns a product that
    /// TODAY gets classified as remove-ads must benefit right away, not on their next purchase.
    public var removeAdsProductIds: Set<String> {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _removeAdsProductIds
        }
        set {
            lock.lock()
            _removeAdsProductIds = newValue
            let pending = pendingWriteLocked(newValue, key: Self.removeAdsKey)
            lock.unlock()
            flush(pending)
        }
    }

    // MARK: - Write

    public func grant(_ productId: String) {
        lock.lock()
        var pending: PendingWrite?
        if active.insert(productId).inserted {
            pending = pendingWriteLocked(active, key: Self.activeKey)
        }
        lock.unlock()
        flush(pending)
    }

    public func revoke(_ productId: String) {
        lock.lock()
        var pending: PendingWrite?
        if active.remove(productId) != nil {
            pending = pendingWriteLocked(active, key: Self.activeKey)
        }
        lock.unlock()
        flush(pending)
    }

    /// Resets entitlement to EXACTLY what the store reports right now.
    ///
    /// REPLACES, never unions — this is the crux. `queryOwned`/`currentEntitlements` returns
    /// what's owned AT THIS MOMENT; a cancelled subscription is simply absent from that set.
    /// Unioning would give a cancelled subscriber permanent free entitlement on that device.
    public func sync(_ productIds: Set<String>) {
        lock.lock()
        guard active != productIds else { lock.unlock(); return }
        active = productIds
        let pending = pendingWriteLocked(active, key: Self.activeKey)
        lock.unlock()
        flush(pending)
    }

    /// Test-only: clears all entitlement state.
    func resetForTest() {
        lock.lock()
        active = []
        _removeAdsProductIds = []
        store.removeObject(forKey: Self.activeKey)
        store.removeObject(forKey: Self.removeAdsKey)
        lock.unlock()
    }
}
