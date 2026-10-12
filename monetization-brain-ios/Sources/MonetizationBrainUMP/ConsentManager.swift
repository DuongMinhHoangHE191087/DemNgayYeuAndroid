#if canImport(UIKit)
import UIKit
import UserMessagingPlatform
import MonetizationBrainCore

/// Thin wrapper around Google's User Messaging Platform iOS SDK — the certified CMP Google
/// requires for serving ads to EEA/UK users. This layer only runs the UMP flow and translates
/// its result into `ConsentTier`; it does not decide policy itself. What "granted" is allowed
/// to do lives in `ConsentState`, which is testable with no platform dependency at all.
///
/// Not called automatically by `AdsBrain.configure(_:)`: the UMP flow needs a presented view
/// controller and can show a full-screen dialog. Auto-running it at launch would pop the form
/// at a time the host app doesn't control — typically over its own splash screen. The host app
/// calls `request(from:)` itself, when it chooses to.
public enum ConsentManager {
    /// Test device identifiers that force the EEA consent form to show during development.
    ///
    /// ═══ WHY `@MainActor` AND NOT `nonisolated(unsafe)` ═══
    ///
    /// A bare `public static var` is nonisolated global mutable state: a warning today and a hard
    /// ERROR under the Swift 6 language mode. `LockedValue` (Core) spells out why this package
    /// refuses `nonisolated(unsafe)` for such knobs — it asserts the race is safe instead of
    /// removing it. The main actor removes it here for the same price, and fits better than a lock
    /// would: the only reader is `request(from:)`, which is already `@MainActor`, and the only
    /// sane place for a host to set a debug flag is `didFinishLaunching`, which is too.
    ///
    /// `LockedValue` itself is not the tool for this target — it is internal to
    /// `MonetizationBrainCore`, and widening a type's visibility to serve two debug flags would be
    /// a worse trade than isolating the two flags.
    @MainActor
    public static var debugTestDeviceIdentifiers: [String] = []

    /// Forces the SDK to simulate being in the EEA for debugging. Only takes effect on devices
    /// listed in `debugTestDeviceIdentifiers`. `@MainActor` for the reason given above.
    @MainActor
    public static var debugForceEea: Bool = false

    /// Whether ads may be requested at all under the current consent state — UMP's own answer,
    /// not this SDK's `ConsentTier` translation of it.
    ///
    /// `true` when UMP has not been asked at all, deliberately and matching Android
    /// (`info?.canRequestAds() ?: true`): a host that has not configured consent — usually because
    /// it ships outside the EEA — must not silently lose every ad just by upgrading this SDK.
    /// `AdsBrain.selfCheckReport()` is where an unconfigured consent flow gets surfaced; a silent
    /// production outage is not.
    ///
    /// Distinct from `ConsentState.allowsPersonalization()`: that governs what the brain may do
    /// with a player's data, this governs whether the ad SDK may be called at all. A host wiring
    /// its own ad layer needs this one, and this is the only place it is exposed.
    public static var canRequestAds: Bool {
        ConsentInformation.shared.canRequestAds
    }

    /// Whether GDPR requires the host to offer a "privacy options" entry point — a button that
    /// re-opens the consent form. When `true` the host MUST show one and route it to
    /// `showPrivacyOptions(from:)`; when `false`, showing one is optional. This is the answer a
    /// settings screen needs, and Android exposes the same one.
    public static var isPrivacyOptionsRequired: Bool {
        ConsentInformation.shared.privacyOptionsRequirementStatus == .required
    }

    /// Runs the consent flow: updates consent info, shows the form if required, then translates
    /// the result into `ConsentState`. `onDone` is always called exactly once, even on error —
    /// not calling it back is the surest way to leave the host app's splash screen hanging.
    ///
    /// `onDone` is `@MainActor`: this callback's whole job is to let the host stop waiting —
    /// dismiss a splash screen, unblock a launch gate — so it is UI code in all but name, and the
    /// type now says on which actor it arrives instead of leaving the host to find out.
    ///
    /// `@Sendable` is the other half of that, and is required rather than decorative: UMP's
    /// completion handlers are `@Sendable`, and `@MainActor` alone does NOT make a function value
    /// `Sendable` — it says where the body runs, not that the value may cross an isolation
    /// boundary to get there. Both attributes are needed to hand `onDone` to those handlers
    /// without a data-race diagnostic.
    @MainActor
    public static func request(
        from viewController: UIViewController,
        onDone: @escaping @MainActor @Sendable (ConsentTier) -> Void = { _ in }
    ) {
        let parameters = RequestParameters()
        if let underAge = ConsentState.shared.underAgeOfConsent {
            parameters.isTaggedForUnderAgeOfConsent = underAge
        }
        if !debugTestDeviceIdentifiers.isEmpty {
            let debugSettings = DebugSettings()
            debugSettings.testDeviceIdentifiers = debugTestDeviceIdentifiers
            if debugForceEea {
                debugSettings.geography = .EEA
            }
            parameters.debugSettings = debugSettings
        }

        ConsentInformation.shared.requestConsentInfoUpdate(with: parameters) { requestError in
            if requestError != nil {
                // Failing to fetch consent info does NOT mean the user declined. Keep the
                // current tier instead of inferring a refusal nobody gave.
                //
                // Hopped to the main actor like the success path below, NOT called inline.
                // UMP does not promise which queue it calls this handler back on, and a host
                // whose `onDone` touches UI — the usual case, since this callback is what
                // dismisses a splash screen — would then get it on whatever thread UMP chose.
                // Delivering the tier on one thread on success and another on failure is the
                // kind of difference that only shows up in the field.
                Task { @MainActor in
                    onDone(ConsentState.shared.tier())
                }
                return
            }

            Task { @MainActor in
                do {
                    try await ConsentForm.loadAndPresentIfRequired(from: viewController)
                } catch {
                    // Presenting the form failed; fall through and translate whatever
                    // consentStatus UMP already has.
                }
                finish(onDone: onDone)
            }
        }
    }

    /// Re-opens the form so the user can change their choice. GDPR requires withdrawing
    /// consent to be as easy as giving it.
    @MainActor
    public static func showPrivacyOptions(
        from viewController: UIViewController,
        onDone: @escaping @MainActor @Sendable (ConsentTier) -> Void = { _ in }
    ) {
        // `finish` is `@MainActor`; this completion handler is not isolated to it, so calling
        // `finish` straight from here is a main-actor call from a nonisolated synchronous
        // context — a warning under Swift 5 and a hard error under the Swift 6 language mode.
        // The `Task { @MainActor in }` is the same hop `request(from:)` already does, and it is
        // what actually makes the `@MainActor` on `finish` true rather than merely declared.
        ConsentForm.presentPrivacyOptionsForm(from: viewController) { _ in
            Task { @MainActor in
                finish(onDone: onDone)
            }
        }
    }

    /// Clears consent state. Development use only.
    public static func reset() {
        ConsentInformation.shared.reset()
        ConsentState.shared.setTier(.unknown)
    }

    @MainActor
    private static func finish(onDone: @MainActor (ConsentTier) -> Void) {
        let tier = translate(
            ConsentInformation.shared.consentStatus,
            canRequestAds: ConsentInformation.shared.canRequestAds
        )
        ConsentState.shared.setTier(tier)
        onDone(tier)
    }

    /// `.notRequired` (outside the EEA) maps to `.granted`, not `.unknown`: there is no consent
    /// obligation there at all, which is a DEFINITE "allowed" answer — different from "nobody
    /// has asked."
    ///
    /// `.obtained` maps to `.granted` with a known limitation: UMP only reports that the consent
    /// flow completed, not which purposes the user actually ticked. Reading the TCF string to
    /// get `.partial` right is not implemented yet.
    ///
    /// ═══ WHY `canRequestAds` IS A PARAMETER AND NOT READ FROM THE SINGLETON ═══
    ///
    /// This is the only decision this target makes, and reading `ConsentInformation.shared`
    /// inside it made the whole mapping table unreachable from a test: that singleton's state is
    /// owned by Google's SDK and cannot be set from a test process. Taking the flag as an
    /// argument turns the table into a pure function — `ConsentManagerTranslateTests` covers all
    /// five statuses and both sides of the `.obtained` branch — and moves the singleton read to
    /// `finish`, which is the boundary where it belongs.
    static func translate(_ status: ConsentStatus, canRequestAds: Bool) -> ConsentTier {
        switch status {
        case .notRequired:
            return .granted
        case .obtained:
            // Fail closed: UMP reporting "the flow completed" while ALSO reporting that ads may
            // not be requested is not a grant of anything. Reading only `status` here would hand
            // back `.granted` for a user UMP has already said no for.
            return canRequestAds ? .granted : .denied
        case .required:
            return .denied
        case .unknown:
            return .unknown
        @unknown default:
            return .unknown
        }
    }
}
#endif
