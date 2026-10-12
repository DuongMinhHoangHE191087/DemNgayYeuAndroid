import Foundation

/// An in-memory `RemoteConfigProvider` an app seeds itself (from its own JSON/plist/whatever).
/// The only concrete `RemoteConfigProvider` implementation Phase 1 ships.
public final class StaticRemoteConfigProvider: RemoteConfigProvider, @unchecked Sendable {
    private let lock = NSLock()
    private var values: [String: String]

    public init(values: [String: String] = [:]) {
        self.values = values
    }

    public func string(forKey key: String) -> String? {
        lock.lock()
        defer { lock.unlock() }
        return values[key]
    }

    /// Replaces the entire value set. Empty input keeps the current values rather than clearing
    /// them — a malformed config payload should never silently disable everything.
    public func update(_ newValues: [String: String]) {
        guard !newValues.isEmpty else { return }
        lock.lock()
        values = newValues
        lock.unlock()
    }

    public func fetchAndActivate() async {
        // No remote source — nothing to fetch.
    }
}
