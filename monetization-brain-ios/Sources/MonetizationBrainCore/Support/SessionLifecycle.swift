import Foundation
#if canImport(UIKit) && !os(watchOS)
import UIKit
#endif

/// Drives `AdsBrain.onSessionStart()`/`onSessionEnd()` from the app's foreground/background
/// transitions — the iOS counterpart of Android's `AppPluginBase.onStart`/`onStop`.
///
/// ═══ WHY A 30-MINUTE GAP, AND WHY NOT EVERY FOREGROUND ═══
///
/// Returning to the foreground after a long absence is a new session with the brain. Returning
/// quickly — switching apps, taking a call — is NOT, and treating it as one would hand back the
/// per-session ad and offer budgets on every app switch, making every per-session cap
/// meaningless. The threshold is the same 30 minutes the event pipeline uses to define a session,
/// so the two systems never count sessions differently when logs are reconciled. Matches Android
/// (`SESSION_GAP_MS`).
///
/// Going to the background always ends the session: an app backgrounded right after an ad IS
/// the negative signal the reward model needs. Without this hook a pending decision is only
/// resolved on the next ask — i.e. always scored as "the player stayed" — and the bandit never
/// learns that showing an ad can drive a player away.
///
/// Entitlements are re-synced on every foreground for the same reason Android does it: a
/// subscription can be cancelled in the App Store, bought on another device, or refunded — all
/// outside the app, so no event reports it. Without the sync a cancelled subscription keeps ads
/// off until the next cold start.
public enum SessionLifecycle {
    /// 30 minutes — the same threshold the event pipeline uses to define a session.
    public static let sessionGapMs: Int64 = 30 * 60 * 1000

    private static let lastBackgroundAtMs = LockedValue<Int64>(0)
    private static let installed = LockedValue(false)
    private static let clock = LockedValue<BrainClock>(SystemClock())

    /// The decision, kept pure so it is testable without UIKit: a foreground counts as a new
    /// session only if the app was backgrounded (`backgroundedAtMs > 0`) at least `sessionGapMs`
    /// ago. A clock that moved backwards yields a negative gap and is treated as "not long enough".
    public static func isNewSession(backgroundedAtMs: Int64, nowMs: Int64) -> Bool {
        guard backgroundedAtMs > 0 else { return false }
        let (gap, overflow) = nowMs.subtractingReportingOverflow(backgroundedAtMs)
        guard !overflow else { return false }
        return gap >= sessionGapMs
    }

    /// What a foreground transition does, separated from the UIKit notification so it can be
    /// driven directly. `restore` is injected for the same reason.
    static func handleForeground(
        nowMs: Int64,
        startSession: () async -> Void,
        iapIsReady: () -> Bool,
        restore: () async -> Void
    ) async {
        let backgroundedAt = lastBackgroundAtMs.wrapped
        if isNewSession(backgroundedAtMs: backgroundedAt, nowMs: nowMs) {
            await startSession()
        }
        if iapIsReady() {
            await restore()
        }
    }

    static func handleBackground(nowMs: Int64, endSession: () async -> Void) async {
        lastBackgroundAtMs.wrapped = nowMs
        await endSession()
    }

    /// Installs the observers once. Safe to call repeatedly; only the first call registers.
    /// A no-op on platforms without UIKit, so `MonetizationBrainCore` still builds for macOS.
    public static func install(clock newClock: BrainClock = SystemClock()) {
        clock.wrapped = newClock
        let firstInstall = installed.withLock { alreadyInstalled -> Bool in
            if alreadyInstalled { return false }
            alreadyInstalled = true
            return true
        }
        guard firstInstall else { return }
        #if canImport(UIKit) && !os(watchOS)
        let center = NotificationCenter.default
        center.addObserver(
            forName: UIApplication.willEnterForegroundNotification, object: nil, queue: nil
        ) { _ in
            let now = clock.wrapped.nowMs()
            Task {
                await handleForeground(
                    nowMs: now,
                    startSession: { await AdsBrain.shared.onSessionStart() },
                    iapIsReady: { IapHelper.shared.isReady() },
                    restore: { _ = await IapHelper.shared.restorePurchases() }
                )
            }
        }
        center.addObserver(
            forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: nil
        ) { _ in
            let now = clock.wrapped.nowMs()
            Task {
                await handleBackground(nowMs: now, endSession: { await AdsBrain.shared.onSessionEnd() })
            }
        }
        #endif
    }

    /// Test-only.
    static func resetForTest() {
        lastBackgroundAtMs.wrapped = 0
        clock.wrapped = SystemClock()
        // `installed` is deliberately NOT reset: observers cannot be un-registered per test, and
        // pretending they were would register a second set.
    }
}
