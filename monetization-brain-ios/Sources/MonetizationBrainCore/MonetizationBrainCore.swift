import Foundation

/// One-call integration — the iOS counterpart of Android's `MonetizationSdk.configure { }`.
///
/// ═══ WHY A FACADE, WHEN EVERY SUBSYSTEM ALREADY HAS `configure` ═══
///
/// Because the ORDER matters and nothing else enforces it. Entitlements must know which products
/// remove ads BEFORE billing connects, or the first entitlement sync classifies a subscriber as
/// still seeing ads. Rollout must be set BEFORE `enabled`, or there is a window where the brain
/// is on for 100% of players. Consent declarations must land before anything reads them. Each
/// subsystem's own `configure` is correct in isolation; this is the one place the sequence is
/// written down, applied, and reported back so a test can lock it and a support engineer can
/// read it off a device.
///
/// ═══ DIVERGENCES FROM ANDROID, ALL DELIBERATE ═══
///
/// - `playerId` is REQUIRED. Android falls back to a device-scoped id; this port has required a
///   host-supplied id since Phase 1 (`AdsBrain.Configuration.playerId`), because an id that
///   survives reinstall is the only one under which a player's learned belief is worth keeping.
/// - `eventSink` and `remoteConfig` are part of the config. Android wires them in
///   `AppPluginBase`; iOS has no such base class, so they are explicit.
/// - There is no `consumableProducts`. Play cannot tell a consumable from a non-consumable, so
///   Android makes the app declare them and warns on a mismatch. StoreKit 2 reports the type on
///   every `Product`, so declaring it here would be a second source of truth for a fact the store
///   already states.
/// - `storeBackend` replaces Android's `Context` as the thing whose absence means "do not start
///   billing". A host that declares products but passes no backend gets a WARNING, not a silent
///   skip: that is a configuration mistake with no other symptom until the first purchase fails.
/// - `observeAppLifecycle` is iOS-only. Android's `AppPluginBase` drives session start/end from
///   the Activity lifecycle automatically; here it is opt-out, installed by this call.
public enum MonetizationBrainSDK {
    /// Package version — surfaces in the `Report` so a support conversation can pin which build
    /// a device is running.
    /// Reported in `Report.describe()`, in the `sdk_configured` event, and to any backend that
    /// segments by SDK build. It read `0.1.0-phase1` through eleven further phases, which made it
    /// wrong in the one place being wrong is expensive: an analyst filtering by it would have
    /// grouped every build since Phase 1 together.
    public static let version = "0.1.0"

    public struct Config: Sendable {
        public var eventSink: EventSink
        public var remoteConfig: RemoteConfigProvider
        public var playerId: String

        /// Enables the Monetization Brain. Default OFF: behaviour is identical to having no brain.
        public var brainEnabled: Bool = false
        /// Fraction of the population the brain controls. Default 1.0 — but the intended process
        /// is a staged rollout from 0.05, adjustable remotely through the policy artifact.
        public var brainRolloutFraction: Double = 1.0

        public var inappProducts: [String] = []
        public var subscriptionProducts: [String] = []
        /// Products that remove ads. Omitting one here means a paying subscriber still sees ads.
        public var removeAdsProducts: Set<String> = []
        /// The store. `nil` means billing is not started — with a warning if products were declared.
        public var storeBackend: StoreBackend? = nil

        /// The studio's LEGAL declaration about its audience (COPPA). `nil` means "not declared",
        /// and the SDK does not touch the corresponding setting. The SDK has no authority to pick
        /// a default here, not even one that looks harmless.
        public var childDirected: Bool? = nil
        /// Under the age of consent (GDPR). `nil` — see `childDirected`.
        public var underAgeOfConsent: Bool? = nil
        /// Confirms the studio handles consent another way (its own CMP, or no EEA/UK audience).
        /// Silences ONE warning in the report and changes no behaviour — on purpose: a flag that
        /// both silenced a warning and changed behaviour would get flipped just to make the
        /// warning go away.
        public var consentHandledExternally: Bool = false

        /// Install foreground/background observers that drive session start/end. iOS-only.
        public var observeAppLifecycle: Bool = true

        public var clock: BrainClock = SystemClock()
        /// Test seam — a `DecisionEngine` built over an in-memory store. Production leaves it `nil`.
        public var decisionEngine: DecisionEngine? = nil

        public init(eventSink: EventSink, playerId: String, remoteConfig: RemoteConfigProvider = StaticRemoteConfigProvider()) {
            self.eventSink = eventSink
            self.playerId = playerId
            self.remoteConfig = remoteConfig
        }
    }

    public struct Report: Sendable, Equatable {
        public let version: String
        public let brainEnabled: Bool
        public let brainRolloutFraction: Double
        public let holdoutFraction: Double
        public let policyVersion: String
        public let consentTier: ConsentTier
        public let productCount: Int
        public let billingStarted: Bool
        /// Events persisted locally and not yet delivered. `nil` when the sink is not an
        /// `EventQueue`, because only the queue can count.
        public let queuedEvents: Int?
        public let warnings: [String]
        /// The steps applied, in order — so a test can lock the sequence and support can read it.
        public let appliedOrder: [String]

        /// A fraction as a whole-number percentage, and the reason it is not
        /// `Int((f * 100).rounded())`.
        ///
        /// `Int(Double)` TRAPS on NaN — it does not produce a garbage number, it kills the host
        /// app. `holdoutFraction` is a public `static var` any host can set, and a NaN reaches
        /// `brainRolloutFraction` too (see `safeFraction`), so the bare conversion turned a
        /// diagnostic string — the one someone prints BECAUSE something looks wrong — into a
        /// crash. Clamping in `Double` space first gives 0% for a NaN and a sane number for
        /// anything else.
        private static func percent(_ fraction: Double) -> Int {
            JsonConfigValue.clamped((fraction * 100).rounded(), lowerBound: 0, upperBound: 100)
        }

        public func describe() -> String {
            var lines: [String] = []
            lines.append("╔══ MONETIZATION BRAIN SDK \(version) ══")
            lines.append("║ brain      : " + (brainEnabled ? "ON" : "OFF"))
            if brainEnabled {
                lines.append("║ rollout    : \(Self.percent(brainRolloutFraction))% of players")
                lines.append("║ holdout    : \(Self.percent(holdoutFraction))% (control group)")
            }
            lines.append("║ policy     : \(policyVersion)")
            let consentNote: String
            switch consentTier {
            case .unknown: consentNote = "  (consent flow has not run)"
            case .denied: consentNote = "  → NO personalization; guardrails still run"
            case .partial: consentNote = "  → existing profile used, nothing new accumulated"
            case .granted: consentNote = ""
            }
            lines.append("║ consent    : \(consentTier)\(consentNote)")
            lines.append("║ products   : \(productCount)" + (billingStarted ? " (billing started)" : ""))
            if let queuedEvents {
                lines.append("║ queued     : \(queuedEvents) event(s) awaiting delivery")
            }
            // Said plainly, as Android says it: the studio should know nobody is learning from
            // their logs yet.
            lines.append("║ backend    : events queue on-device and post to the configured endpoint.")
            lines.append("║              There is NO training service yet — the brain learns locally,")
            lines.append("║              per device, with no global aggregation.")
            if warnings.isEmpty {
                lines.append("║ ✓ no configuration warnings")
            } else {
                lines.append("╠══ WARNINGS ══")
                for warning in warnings { lines.append("║ ⚠ \(warning)") }
            }
            lines.append("╚══════════════════════")
            return lines.joined(separator: "\n")
        }
    }

    private static let _lastReport = LockedValue<Report?>(nil)

    /// The report from the most recent `configure`; `nil` if it has not been called.
    public static func lastReport() -> Report? {
        _lastReport.wrapped
    }

    /// Applies the configuration in the one safe order and returns what was done. Call once,
    /// early — before the first decision point and before the first purchase.
    @discardableResult
    public static func configure(_ config: Config) async -> Report {
        var order: [String] = []

        // 1. Entitlements BEFORE billing — the first sync must already know what removes ads.
        if !config.removeAdsProducts.isEmpty {
            Entitlements.shared.removeAdsProductIds = config.removeAdsProducts
            order.append("entitlements.removeAdsProducts")
        }

        // 2. Declare products.
        let allProducts = config.inappProducts + config.subscriptionProducts
        if !allProducts.isEmpty {
            IapHelper.shared.setProducts(allProducts)
            order.append("iap.setProducts")
        }

        // 3. Connect billing — only if the app actually sells something AND has a store.
        var billingStarted = false
        if !allProducts.isEmpty, let backend = config.storeBackend {
            await IapHelper.shared.configure(IapHelper.Configuration(
                backend: backend, eventSink: config.eventSink, playerId: config.playerId
            ))
            billingStarted = IapHelper.shared.isReady()
            order.append("iap.configure")
        }

        // 3b. The studio's legal declarations, BEFORE anything reads them.
        if let childDirected = config.childDirected {
            ConsentState.shared.setChildDirected(childDirected)
            order.append("consent.childDirected")
        }
        if let underAge = config.underAgeOfConsent {
            ConsentState.shared.setUnderAgeOfConsent(underAge)
            order.append("consent.underAgeOfConsent")
        }

        // 4. Brain: configure, then rollout BEFORE enabled, then the first session.
        await AdsBrain.shared.configure(AdsBrain.Configuration(
            remoteConfig: config.remoteConfig,
            eventSink: config.eventSink,
            playerId: config.playerId,
            decisionEngine: config.decisionEngine,
            clock: config.clock
        ))
        order.append("brain.configure")
        AdsBrain.shared.rolloutFraction = safeFraction(config.brainRolloutFraction)
        order.append("brain.rolloutFraction")
        AdsBrain.shared.enabled = config.brainEnabled
        order.append("brain.enabled")
        await AdsBrain.shared.onSessionStart()
        order.append("brain.onSessionStart")

        // 5. iOS-only: session start/end from the app lifecycle.
        if config.observeAppLifecycle {
            SessionLifecycle.install(clock: config.clock)
            order.append("lifecycle.observe")
        }

        let queued: Int?
        if let queue = config.eventSink as? EventQueue {
            queued = await queue.queuedCount()
        } else {
            queued = nil
        }

        let report = Report(
            version: version,
            brainEnabled: AdsBrain.shared.enabled,
            brainRolloutFraction: AdsBrain.shared.rolloutFraction,
            holdoutFraction: HoldoutManager.holdoutFraction,
            policyVersion: BrainPolicy.shared.activeVersion(),
            consentTier: ConsentState.shared.tier(),
            productCount: allProducts.count,
            billingStarted: billingStarted,
            queuedEvents: queued,
            warnings: checkConfig(config),
            appliedOrder: order
        )
        _lastReport.wrapped = report
        return report
    }

    /// Clamps a fraction into `0...1`, and handles the value `min`/`max` cannot.
    ///
    /// ═══ `min(max(x, 0), 1)` DOES NOT CLAMP A NaN ═══
    ///
    /// Every comparison with NaN is false, so both calls hand the NaN straight back and it lands
    /// in `AdsBrain.rolloutFraction`. What follows is worse than a wrong number:
    /// `HoldoutManager.isInRollout` guards on `isFinite`, so a NaN quietly means NOBODY is in the
    /// rollout — the brain is off for the entire population with `brainEnabled == true` and no
    /// symptom — and `Report.describe()` then traps converting it to a percentage. A NaN arriving
    /// from a parsed config value is ordinary; both consequences are not.
    ///
    /// 0 is the right landing: it is what the system already does with the value, and
    /// `checkConfig` says so out loud instead of leaving it silent.
    static func safeFraction(_ value: Double) -> Double {
        guard !value.isNaN else { return 0.0 }
        return min(max(value, 0.0), 1.0)
    }

    /// Catches the misconfigurations the system does NOT report on its own — the most dangerous
    /// kind, because everything looks like it is running.
    static func checkConfig(_ c: Config) -> [String] {
        var w: [String] = []
        if c.brainEnabled, c.brainRolloutFraction.isNaN {
            w.append("brainRolloutFraction is not a number (NaN) → treated as 0, so the brain controls no "
                     + "player. A NaN usually means a config value was parsed from a missing or malformed "
                     + "field; without this line it is silent, because every comparison with NaN is false.")
        } else if c.brainEnabled, c.brainRolloutFraction <= 0.0 {
            w.append("brainEnabled = true but brainRolloutFraction = 0 → the brain controls no player. "
                     + "This is a silent no-op with no symptom to diagnose.")
        }
        let declared = Set(c.inappProducts + c.subscriptionProducts)
        for id in c.removeAdsProducts.subtracting(declared).sorted() {
            w.append("removeAdsProducts contains '\(id)', which is not among the declared products — check the id.")
        }
        if !c.subscriptionProducts.isEmpty, c.removeAdsProducts.isEmpty {
            w.append("subscriptions are declared but removeAdsProducts is empty — subscribers will still see ads. "
                     + "If that is intended, ignore this.")
        }
        if !declared.isEmpty, c.storeBackend == nil {
            w.append("\(declared.count) product(s) declared but storeBackend is nil — billing was not started and no purchase can run.")
        }
        if !c.consentHandledExternally, ConsentState.shared.tier() == .unknown {
            w.append("consent tier is UNKNOWN — the consent flow has not run. Run ConsentManager.request(from:) "
                     + "or set consentHandledExternally = true if consent is handled elsewhere.")
        }
        return w
    }

    /// Test-only.
    static func resetForTest() {
        _lastReport.wrapped = nil
        SessionLifecycle.resetForTest()
    }
}
