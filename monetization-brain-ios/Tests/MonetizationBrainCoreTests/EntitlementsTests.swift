import XCTest
@testable import MonetizationBrainCore

final class EntitlementsTests: XCTestCase {
    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? { values[key] }
        func setString(_ value: String, forKey key: String) { values[key] = value }
        func removeObject(forKey key: String) { values.removeValue(forKey: key) }
    }

    /// A store that blocks inside `setString`, so a write can be observed still in flight.
    private final class BlockingStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        private let gate = DispatchSemaphore(value: 0)
        private var blockNext = false

        func armBlock() { lock.lock(); blockNext = true; lock.unlock() }
        func release() { gate.signal() }

        func string(forKey key: String) -> String? {
            lock.lock(); defer { lock.unlock() }; return values[key]
        }
        func setString(_ value: String, forKey key: String) {
            lock.lock()
            let shouldBlock = blockNext
            if shouldBlock { blockNext = false }
            lock.unlock()
            if shouldBlock { gate.wait() }
            lock.lock(); values[key] = value; lock.unlock()
        }
        func removeObject(forKey key: String) {
            lock.lock(); _ = values.removeValue(forKey: key); lock.unlock()
        }
    }

    /// `Entitlements` must not hold its lock across store I/O.
    ///
    /// It used to: every mutator wrote inside the lock. `BattlePass.claim` and
    /// `RecurringGrants.claim` call `isActive` WHILE holding their own lock, so a concurrent
    /// `grant()` stalled inside `UserDefaults` would make a claim path wait behind it.
    ///
    /// Here `grant` is parked inside the store write on another thread; a read must still answer
    /// immediately. Before the fix this test times out.
    func test_readsAreNotBlockedByAWriteStalledInsideTheStore() {
        let store = BlockingStore()
        let entitlements = Entitlements(store: store)
        entitlements.grant("already_owned")

        store.armBlock()
        let writing = expectation(description: "grant returns")
        DispatchQueue.global().async {
            entitlements.grant("slow_product")
            writing.fulfill()
        }

        // Give the background grant time to reach the store and park there.
        Thread.sleep(forTimeInterval: 0.2)

        let reading = expectation(description: "read completes while the write is parked")
        DispatchQueue.global().async {
            _ = entitlements.isActive("already_owned")
            reading.fulfill()
        }
        wait(for: [reading], timeout: 2.0)

        store.release()
        wait(for: [writing], timeout: 2.0)
        XCTAssertTrue(entitlements.isActive("slow_product"), "the parked write still lands")
    }

    func test_isActiveFalseForUngrantedProduct() {
        let entitlements = Entitlements(store: InMemoryStore())
        XCTAssertFalse(entitlements.isActive("gem_pack_1"))
    }

    func test_grantMakesProductActive() {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant("vip_pass")
        XCTAssertTrue(entitlements.isActive("vip_pass"))
        XCTAssertEqual(entitlements.activeProductIds(), ["vip_pass"])
    }

    func test_revokeMakesProductInactive() {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant("vip_pass")
        entitlements.revoke("vip_pass")
        XCTAssertFalse(entitlements.isActive("vip_pass"))
    }

    func test_syncReplacesNotUnions() {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant("old_sub")
        entitlements.sync(["new_sub"])
        XCTAssertFalse(entitlements.isActive("old_sub"))
        XCTAssertTrue(entitlements.isActive("new_sub"))
    }

    func test_hasRemoveAdsTrueWhenAnActiveProductIsInRemoveAdsSet() {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.removeAdsProductIds = ["vip_pass", "remove_ads_iap"]
        XCTAssertFalse(entitlements.hasRemoveAds())
        entitlements.grant("vip_pass")
        XCTAssertTrue(entitlements.hasRemoveAds())
    }

    func test_hasRemoveAdsRecomputesImmediatelyWhenRemoveAdsSetChangesAfterGrant() {
        // A player who already owns `vip_pass` before it's classified as remove-ads must gain
        // the benefit the moment the classification changes — not on their next purchase.
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant("vip_pass")
        XCTAssertFalse(entitlements.hasRemoveAds())
        entitlements.removeAdsProductIds = ["vip_pass"]
        XCTAssertTrue(entitlements.hasRemoveAds())
    }

    func test_persistsAcrossInstancesUsingTheSameStore() {
        let store = InMemoryStore()
        let first = Entitlements(store: store)
        first.grant("vip_pass")

        let second = Entitlements(store: store)
        XCTAssertTrue(second.isActive("vip_pass"))
    }

    func test_removeAdsProductIdsAlsoPersistsAcrossInstances() {
        let store = InMemoryStore()
        let first = Entitlements(store: store)
        first.removeAdsProductIds = ["vip_pass"]
        first.grant("vip_pass")

        let second = Entitlements(store: store)
        XCTAssertTrue(second.hasRemoveAds())
    }
}
