/// Abstraction over "where remote-controlled configuration values come from."
///
/// Phase 1 ships only `StaticRemoteConfigProvider`. A real Firebase Remote Config-backed
/// implementation is a later, optional addition — this protocol exists so
/// `MonetizationBrainCore` never has to import Firebase to read a config value, matching the
/// Android finding that `com.app.plugin.brain` never imports Firebase directly either.
public protocol RemoteConfigProvider: Sendable {
    /// Returns the string value for `key`, or `nil` if the key is absent.
    func string(forKey key: String) -> String?

    /// Fetches the latest values and activates them. `StaticRemoteConfigProvider` treats this
    /// as a no-op since it has no remote source to fetch from.
    func fetchAndActivate() async
}
