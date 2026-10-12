import Foundation

/// Automatic click-fraud protection, distilled from a production teardown pattern (a competing
/// app's `config_prevent_ad_click`): more than `maxClicksPerWindow` ad clicks inside `windowMs`
/// locks ALL ads for `lockoutMs`. Protects the mediation/AdMob account from invalid traffic by
/// blocking client-side, before the request is even sent.
///
/// Defaults (6 clicks / 300s / 1800s lockout) were observed running in real production — a
/// reasonable starting point, NOT a mandatory value; production should load these from a policy
/// artifact when one exists.
///
/// Unlike `HoldoutManager`'s static state (deliberately left unsynchronized — a known, documented
/// Phase 1 gap), this is real concurrent mutable state: ad clicks can arrive from multiple call
/// sites. Lock-protected from day one.
public enum ClickFraudGuard {
    /// ═══ ONE LOCKED STATE, NOT FIVE LOCKED VARIABLES ═══
    ///
    /// These five values were five `static var`s behind one shared `NSLock` — correct, but
    /// nonisolated global mutable state, which is a hard error under Swift 6.
    ///
    /// They are gathered into ONE `LockedValue` rather than given one each, because
    /// `registerClick` reads the window, compares against `windowMs` and `maxClicksPerWindow`, and
    /// writes both `clickState` and `lockedUntilMs` — all as a single decision. Five independent
    /// locks would let a concurrent caller change the cap between the comparison and the write, so
    /// splitting them would trade a compiler error for a race. The single acquisition the original
    /// shared lock provided is preserved exactly.
    private struct State {
        var maxClicksPerWindow: Int = 6
        var windowMs: Int64 = 300_000
        var lockoutMs: Int64 = 1_800_000
        var clickState: [String: ClickWindow] = [:]
        var lockedUntilMs: [String: Int64] = [:]
    }

    private static let state = LockedValue(State())

    /// Clicks allowed inside one window before the player is locked out.
    public static var maxClicksPerWindow: Int {
        get { state.withLock { $0.maxClicksPerWindow } }
        set { state.withLock { $0.maxClicksPerWindow = newValue } }
    }

    /// Length of the counting window, in milliseconds.
    public static var windowMs: Int64 {
        get { state.withLock { $0.windowMs } }
        set { state.withLock { $0.windowMs = newValue } }
    }

    /// How long a lockout lasts, in milliseconds.
    public static var lockoutMs: Int64 {
        get { state.withLock { $0.lockoutMs } }
        set { state.withLock { $0.lockoutMs = newValue } }
    }

    private struct ClickWindow {
        var windowStartMs: Int64
        var count: Int
    }

    private static func defaultNowMs() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }

    /// Call every time a user clicks an ad (not every impression).
    public static func registerClick(playerId: String, nowMs: Int64? = nil) {
        let now = nowMs ?? defaultNowMs()
        state.withLock { s in
            var window = s.clickState[playerId] ?? ClickWindow(windowStartMs: now, count: 0)
            if now - window.windowStartMs > s.windowMs {
                window.windowStartMs = now
                window.count = 0
            }
            window.count += 1
            if window.count > s.maxClicksPerWindow {
                s.lockedUntilMs[playerId] = now + s.lockoutMs
            }
            s.clickState[playerId] = window
        }
    }

    public static func isLocked(playerId: String, nowMs: Int64? = nil) -> Bool {
        let now = nowMs ?? defaultNowMs()
        return state.withLock { s in
            guard let until = s.lockedUntilMs[playerId] else { return false }
            return now < until
        }
    }

    /// Test-only: clears all click/lockout state.
    static func resetForTest() {
        state.withLock { s in
            s.clickState.removeAll()
            s.lockedUntilMs.removeAll()
        }
    }
}
