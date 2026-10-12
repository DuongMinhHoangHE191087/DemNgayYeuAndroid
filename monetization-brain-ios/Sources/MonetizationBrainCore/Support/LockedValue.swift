import Foundation

/// A mutable value behind a lock, so a `static var` can stay a tunable knob without being
/// nonisolated global mutable state.
///
/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// WHY THIS EXISTS, AND WHY NOT `nonisolated(unsafe)`
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// Several types here expose tuning knobs as `static var` — ad caps, holdout fractions, reward
/// weights. Under Swift 6's language mode every one of them is a hard ERROR, not a warning, and
/// the README has carried them as a known data race since Phase 1: `HoldoutManager.salt` and
/// `holdoutFraction` are reachable from `AdsBrain`'s `async` methods on arbitrary executors while
/// nothing serialises a concurrent write.
///
/// `nonisolated(unsafe)` would silence every one of those errors in a single character-for-character
/// edit, and it is the wrong tool: it asserts the race is safe rather than removing it. The race
/// here is real — a host tuning a knob from its own thread while a decision path reads it — so the
/// honest fix is the lock this codebase already uses everywhere else.
///
/// `withLock` exists alongside the plain accessor because a read-modify-write through the getter
/// and then the setter is two acquisitions: two callers can interleave and one update is lost.
/// Anything that mutates a collection in place must go through `withLock`, not `wrapped`.
///
/// The instance is held in a `static let`, so the REFERENCE is immutable and `Sendable`; this class
/// is `@unchecked Sendable` on the same promise every other lock-guarded type here makes.
final class LockedValue<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var storage: Value

    init(_ value: Value) {
        self.storage = value
    }

    /// Read or replace the whole value. For a read-modify-write, use `withLock` instead — two
    /// separate acquisitions can lose an update between them.
    var wrapped: Value {
        get {
            lock.lock()
            defer { lock.unlock() }
            return storage
        }
        set {
            lock.lock()
            storage = newValue
            lock.unlock()
        }
    }

    /// Read-modify-write in ONE acquisition.
    @discardableResult
    func withLock<Result>(_ body: (inout Value) -> Result) -> Result {
        lock.lock()
        defer { lock.unlock() }
        return body(&storage)
    }
}
