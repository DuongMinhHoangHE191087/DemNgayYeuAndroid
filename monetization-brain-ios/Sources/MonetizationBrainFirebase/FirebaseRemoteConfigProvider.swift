import Foundation
import FirebaseRemoteConfig
import MonetizationBrainCore

/// `RemoteConfigProvider` backed by Firebase Remote Config — the iOS counterpart of Android's
/// `FirHelperConfig`.
///
/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// WHY THIS IS A SEPARATE TARGET, AND WHY IT IS THIS SMALL
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// `MonetizationBrainCore` must never import Firebase. That is not tidiness: it is what lets a
/// host with its own config backend (a self-hosted endpoint, a plist, a different vendor) get the
/// identical eight-key dispatch by conforming to a two-method protocol, and it is what lets the
/// whole fan-out be tested against a dictionary instead of a network.
///
/// So everything interesting lives in `RemoteConfigDispatcher`, over in Core, and this file is
/// only the adapter: fetch, and read a key. If you would rather not take the Firebase dependency
/// through this package at all, copying this one file into your app is a complete substitute —
/// it has no private API and nothing else in this target to bring along.
///
/// ═══ CREATE THIS **AFTER** `FirebaseApp.configure()` ═══
///
/// `RemoteConfig.remoteConfig()` raises if no Firebase app has been configured, and the default
/// argument below calls it at init time. Construct this from `application(_:didFinishLaunching…)`
/// after `FirebaseApp.configure()`, not from a stored property that might initialise earlier.
///
/// Typical wiring:
///
/// ```swift
/// FirebaseApp.configure()
/// let provider = FirebaseRemoteConfigProvider()
/// await MonetizationBrainSDK.configure(.init(eventSink: sink, remoteConfig: provider, playerId: id))
/// let report = await RemoteConfigDispatcher.shared.refresh(from: provider)
/// print(report.describe())
/// ```
public final class FirebaseRemoteConfigProvider: RemoteConfigProvider, @unchecked Sendable {
    /// How long Remote Config serves cached values before a fetch is allowed to hit the network.
    ///
    /// Android's `FirHelperConfig` uses 10s in debug and 3600s in release, with the reason
    /// written next to it: the short interval is a debug convenience, and shipping it would blow
    /// through Remote Config's fetch quota and get the app THROTTLED — which looks, from the
    /// outside, exactly like "config stopped updating". Same two numbers here, same reason.
    public static let debugMinimumFetchInterval: TimeInterval = 10
    public static let releaseMinimumFetchInterval: TimeInterval = 3600

    /// The interval this build defaults to. `DEBUG` is the flag Swift packages get from the
    /// consuming build configuration, so a release build of the host app picks the hour.
    public static var defaultMinimumFetchInterval: TimeInterval {
        #if DEBUG
        debugMinimumFetchInterval
        #else
        releaseMinimumFetchInterval
        #endif
    }

    private let remoteConfig: RemoteConfig
    private let lock = NSLock()
    private var _lastFetchError: Error?

    /// The error from the most recent failed `fetchAndActivate`, or `nil` if the last one worked.
    ///
    /// `RemoteConfigProvider.fetchAndActivate` returns `Void` by contract — a failed fetch is not
    /// an error the dispatcher can act on, because Remote Config keeps serving the last activated
    /// values and dispatching those is the correct behaviour. But a swallowed error is a support
    /// ticket nobody can answer, so the failure is kept here for a host that wants to log it.
    public var lastFetchError: Error? {
        lock.lock()
        defer { lock.unlock() }
        return _lastFetchError
    }

    /// - Parameters:
    ///   - remoteConfig: defaults to the shared instance — **requires `FirebaseApp.configure()`
    ///     to have run**, see the type doc.
    ///   - minimumFetchInterval: defaults to 10s in `DEBUG`, one hour otherwise.
    public init(
        remoteConfig: RemoteConfig = RemoteConfig.remoteConfig(),
        minimumFetchInterval: TimeInterval = FirebaseRemoteConfigProvider.defaultMinimumFetchInterval
    ) {
        self.remoteConfig = remoteConfig
        let settings = RemoteConfigSettings()
        settings.minimumFetchInterval = minimumFetchInterval
        remoteConfig.configSettings = settings
    }

    /// An unset parameter reads back as the empty string, which `RemoteConfigDispatcher` would
    /// then have to tell apart from a real value. Mapping it to `nil` here keeps that distinction
    /// where it belongs — at the boundary with the vendor — so the dispatcher's own empty-string
    /// guard is a second line of defence rather than the only one.
    public func string(forKey key: String) -> String? {
        let value = remoteConfig.configValue(forKey: key).stringValue
        return value.isEmpty ? nil : value
    }

    /// Fetches and activates. Never throws: see `lastFetchError`.
    public func fetchAndActivate() async {
        do {
            _ = try await remoteConfig.fetchAndActivate()
            record(nil)
        } catch {
            record(error)
        }
    }

    /// Taking the lock inline in an `async` function is a Swift 6 error — an `await` could
    /// resume on a different thread than the one holding it. This package's answer everywhere is
    /// the same: the locked region is its own SYNCHRONOUS function, which cannot suspend, and the
    /// async caller just calls it.
    private func record(_ error: Error?) {
        lock.lock()
        _lastFetchError = error
        lock.unlock()
    }
}
