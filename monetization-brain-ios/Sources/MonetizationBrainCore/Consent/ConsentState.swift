import Foundation

/// Current consent tier and the permissions it unlocks.
///
/// Kept separate from any UMP/consent-flow wrapper (see `MonetizationBrainUMP.ConsentManager`)
/// so the actual policy — what the SDK is and isn't allowed to do — is testable with pure
/// Swift, no platform APIs involved.
///
/// Listener closures here are non-throwing by type (`(ConsentTier) -> Void`), which is the
/// Swift-native way to get the same guarantee Android's `try/catch`-per-listener gives: a
/// well-behaved listener cannot interrupt notification of the others. (A listener that traps —
/// force-unwraps `nil`, for example — is not something either language can recover from, so
/// this is not a regression from the Android original.)
public final class ConsentState: @unchecked Sendable {
    public static let shared = ConsentState()

    private let lock = NSLock()
    private var current: ConsentTier = .unknown
    private var listeners: [(ConsentTier) -> Void] = []
    private var _childDirected: Bool?
    private var _underAgeOfConsent: Bool?

    private init() {}

    /// Whether the app targets children (COPPA). `nil` means the host app hasn't declared this;
    /// the SDK never asserts that legal claim on the app's behalf.
    public var childDirected: Bool? {
        lock.lock()
        defer { lock.unlock() }
        return _childDirected
    }

    /// Whether the user is under the age of consent (GDPR). `nil` means not declared.
    public var underAgeOfConsent: Bool? {
        lock.lock()
        defer { lock.unlock() }
        return _underAgeOfConsent
    }

    public func setChildDirected(_ value: Bool?) {
        lock.lock()
        _childDirected = value
        lock.unlock()
    }

    public func setUnderAgeOfConsent(_ value: Bool?) {
        lock.lock()
        _underAgeOfConsent = value
        lock.unlock()
    }

    public func tier() -> ConsentTier {
        lock.lock()
        defer { lock.unlock() }
        return current
    }

    /// Changes the consent tier. This is a first-class event: listeners must be notified so
    /// cached profiles can be invalidated immediately when consent is withdrawn. A no-op call
    /// (setting the tier it already is) notifies nobody.
    public func setTier(_ tier: ConsentTier) {
        lock.lock()
        guard tier != current else {
            lock.unlock()
            return
        }
        current = tier
        let listenersSnapshot = listeners
        lock.unlock()

        for listener in listenersSnapshot {
            listener(tier)
        }
    }

    public func onChange(_ listener: @escaping (ConsentTier) -> Void) {
        lock.lock()
        listeners.append(listener)
        lock.unlock()
    }

    // MARK: - Permissions

    /// Allowed to accumulate a behavioral profile (update belief state)?
    ///
    /// `.partial` deliberately returns `false`: partial consent should only use features that
    /// were actually consented to, but there's no per-feature masking yet. When we can't tell
    /// which feature is allowed, the conservative choice is to not accumulate anything.
    public func allowsProfiling() -> Bool {
        let t = tier()
        return t == .unknown || t == .granted
    }

    /// Allowed to use an EXISTING profile for personalization?
    public func allowsPersonalization() -> Bool {
        tier() != .denied
    }

    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// ALLOWED TO SEND PLAYER SIGNALS OFF-DEVICE TO A THIRD-PARTY PROCESSOR?
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    ///
    /// **Only `.granted`.** This is the strictest predicate here, deliberately stricter than
    /// `allowsPersonalization()`, and it exists because the question is different in kind.
    ///
    /// Every other consent predicate governs what this SDK does with data it already holds, on the
    /// device or against the studio's own backend. This one governs data LEAVING for a third party
    /// the player has no relationship with — a Braze/OneSignal/Clevertap-class processor reached
    /// through `CrmBridge`. That is the one decision no default can make on the player's behalf.
    ///
    /// Why `.unknown` is refused, when `allowsProfiling()` accepts it: `.unknown` means the app has
    /// not asked yet. Accumulating a local profile before asking is recoverable — the profile can
    /// be discarded. A signal already delivered to an outside processor cannot be recalled, so
    /// "we had not asked yet" is not a defence for having sent it.
    ///
    /// Why `.partial` is refused: the same reason `allowsProfiling()` refuses it, and it applies
    /// with more force here. There is no per-feature masking yet, so partial consent cannot tell us
    /// whether CRM was among the features actually consented to. When we cannot tell which feature
    /// was permitted, the conservative reading is that this one was not.
    ///
    /// This diverges from Android, which gates CRM on its equivalent of `allowsPersonalization()`
    /// and therefore delivers on `.unknown`. The divergence is intentional and is recorded in the
    /// Phase 10 spec §3.3 — matching a weaker gate would mean copying a privacy default rather than
    /// choosing one.
    public func allowsThirdPartySignals() -> Bool {
        tier() == .granted
    }

    /// Guardrails always run, at every tier. They only protect the player — no interpretation
    /// of consent regulation makes protecting a user require that user's permission first.
    public func allowsGuardrails() -> Bool { true }

    /// Decision logs are still written when consent is denied — the log contains a
    /// pseudonymous id, the chosen action, and propensity; no PII.
    public func allowsDecisionLogging() -> Bool { true }

    /// Test-only: reset all state. Not exposed outside the package.
    func resetForTest() {
        lock.lock()
        current = .unknown
        _childDirected = nil
        _underAgeOfConsent = nil
        listeners.removeAll()
        lock.unlock()
    }
}
