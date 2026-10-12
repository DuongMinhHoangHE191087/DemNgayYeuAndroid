import Foundation

/// What happened to one remote-config key on one dispatch.
public enum RemoteConfigKeyStatus: String, Sendable, Equatable {
    /// The subsystem accepted the artifact and is now running it.
    case applied
    /// The key held a value, and the subsystem refused it — malformed JSON, a missing required
    /// field, a version it will not downgrade to. The subsystem keeps its last good config.
    case rejected
    /// The key is not set on the console, or is set to the empty string.
    case absent
}

/// One key's outcome, carrying the subsystem's own explanation verbatim.
public struct RemoteConfigKeyResult: Sendable, Equatable {
    public let key: String
    public let status: RemoteConfigKeyStatus
    /// The subsystem's `report` string, or a short note for `absent`. Kept as text rather than
    /// parsed: an operator reading a log needs the reason the subsystem gave, not a code.
    public let report: String

    public init(key: String, status: RemoteConfigKeyStatus, report: String) {
        self.key = key
        self.status = status
        self.report = report
    }
}

/// The result of one full dispatch, in key order.
public struct RemoteConfigApplyReport: Sendable, Equatable {
    public let results: [RemoteConfigKeyResult]

    public init(results: [RemoteConfigKeyResult]) {
        self.results = results
    }

    public var appliedKeys: [String] { keys(with: .applied) }
    public var rejectedKeys: [String] { keys(with: .rejected) }
    public var absentKeys: [String] { keys(with: .absent) }

    private func keys(with status: RemoteConfigKeyStatus) -> [String] {
        results.filter { $0.status == status }.map(\.key)
    }

    /// One line per key, for a single log statement at startup. Android logs each subsystem's
    /// report separately; one block keeps the eight lines together in a crash log instead of
    /// interleaved with whatever else the app logs while config lands.
    public func describe() -> String {
        results
            .map { "\($0.key): \($0.status.rawValue) — \($0.report)" }
            .joined(separator: "\n")
    }
}

/// Fans one remote-config payload out to the eight subsystems that take an artifact.
///
/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// WHY EIGHT KEYS AND NOT ONE
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// This mirrors Android's `FirHelperConfig.parserCfWithRemoteCf`, including the order, and its
/// reasoning is worth restating because the shape looks redundant until you know it: these eight
/// artifacts change on completely different clocks. The paywall changes per campaign (weekly),
/// the brain policy per training cycle, routing per store-fee terms, the battle pass per season
/// (fastest of all), the CRM bridge per vendor contract. Collapsing them into one blob would
/// force a full re-push of all eight to edit any one of them — and, worse, would mean one
/// malformed field takes down every subsystem at once. Here a bad artifact costs exactly its own
/// subsystem's update: `test_oneMalformedArtifactDoesNotStopTheOtherSeven` pins that.
///
/// ═══ WHY THIS TYPE IMPORTS NOTHING ═══
///
/// It talks to `RemoteConfigProvider`, not to Firebase. `MonetizationBrainCore` never imports
/// Firebase — the same finding that shaped `RemoteConfigProvider` itself, and that Android's own
/// `com.app.plugin.brain` respects. The Firebase-backed provider lives in
/// `MonetizationBrainFirebase`; a host with its own config backend conforms to the protocol and
/// gets the identical dispatch. That is also what makes the whole fan-out testable with a
/// dictionary.
///
/// ═══ WHAT IT DELIBERATELY DOES NOT DO ═══
///
/// No retry, no scheduling, no caching. `RemoteConfigProvider.fetchAndActivate` already owns the
/// fetch policy (Firebase's own minimum-fetch-interval throttle), and a second retry loop layered
/// on top would fight it and burn the fetch quota.
public final class RemoteConfigDispatcher: Sendable {
    /// The default instance, wired to every subsystem's `.shared`.
    public static let shared = RemoteConfigDispatcher()

    // MARK: - Console key names
    //
    // These strings are a CONTRACT with the Firebase console: they are what someone types when
    // creating the parameter. Each is `_ios`-suffixed rather than shared with Android — the two
    // fleets are tuned separately, and one shared key would mean an iOS-only cap change silently
    // re-tuning Android.

    public static let brainPolicyKey = "brain_policy_ios"
    public static let paywallConfigKey = "paywall_config_ios"
    public static let purchaseRoutingKey = "purchase_routing_ios"
    public static let subscriptionLifecycleKey = "subscription_lifecycle_ios"
    public static let battlePassKey = "battle_pass_ios"
    public static let recurringGrantsKey = "recurring_grants_ios"
    public static let offerwallConfigKey = "offerwall_config_ios"
    public static let crmBridgeKey = "crm_bridge_ios"

    /// Every key this dispatcher reads, in the order it applies them — Android's order, kept so
    /// an artifact set pushed to both platforms lands in the same sequence. Exposed so a host can
    /// seed console defaults or check its own remote config without re-typing the strings.
    public static let keys: [String] = [
        brainPolicyKey,
        paywallConfigKey,
        purchaseRoutingKey,
        subscriptionLifecycleKey,
        battlePassKey,
        recurringGrantsKey,
        offerwallConfigKey,
        crmBridgeKey
    ]

    private let decisionEngine: DecisionEngine
    private let paywall: PaywallManager
    private let purchaseRouter: PurchaseRouter
    private let subscriptionLifecycle: SubscriptionLifecycle
    private let battlePass: BattlePass
    private let recurringGrants: RecurringGrants
    private let offerwall: OfferwallManager
    private let crmBridge: CrmBridge

    /// Every subsystem is injectable and defaults to its singleton: production wires itself, and
    /// a test can dispatch into eight throwaway instances without leaving global state behind.
    public init(
        decisionEngine: DecisionEngine = .shared,
        paywall: PaywallManager = .shared,
        purchaseRouter: PurchaseRouter = .shared,
        subscriptionLifecycle: SubscriptionLifecycle = .shared,
        battlePass: BattlePass = .shared,
        recurringGrants: RecurringGrants = .shared,
        offerwall: OfferwallManager = .shared,
        crmBridge: CrmBridge = .shared
    ) {
        self.decisionEngine = decisionEngine
        self.paywall = paywall
        self.purchaseRouter = purchaseRouter
        self.subscriptionLifecycle = subscriptionLifecycle
        self.battlePass = battlePass
        self.recurringGrants = recurringGrants
        self.offerwall = offerwall
        self.crmBridge = crmBridge
    }

    /// Fetch, then dispatch. This is the call a host makes at startup.
    ///
    /// The order is the point: reading first and fetching afterwards would apply the PREVIOUS
    /// session's cached values and hold the new ones until the next launch — a config push that
    /// appears to take a day to roll out.
    ///
    /// A failed fetch is not an error here. `fetchAndActivate` keeps serving the last activated
    /// values, so dispatching against the cache is exactly right: the alternative, skipping the
    /// dispatch, would leave subsystems on their compiled defaults even though a good config was
    /// already on the device.
    @discardableResult
    public func refresh(from provider: RemoteConfigProvider) async -> RemoteConfigApplyReport {
        await provider.fetchAndActivate()
        return apply(from: provider)
    }

    /// Dispatches whatever the provider currently holds, without fetching.
    @discardableResult
    public func apply(from provider: RemoteConfigProvider) -> RemoteConfigApplyReport {
        RemoteConfigApplyReport(results: appliers.map { key, applier in
            // Android guards every key with `isNotEmpty`, and so does this: an unset Remote
            // Config parameter reads back as the empty string, and handing that to a parser
            // would log a rejection for a key nobody ever configured.
            guard let json = provider.string(forKey: key), !json.isEmpty else {
                return RemoteConfigKeyResult(key: key, status: .absent, report: "no value set")
            }
            let outcome = applier(json)
            return RemoteConfigKeyResult(
                key: key,
                status: outcome.applied ? .applied : .rejected,
                report: outcome.report
            )
        })
    }

    /// Key → the one call that hands that key's artifact to its owner, in dispatch order.
    ///
    /// Each entry narrows the subsystem's own result type down to `(applied, report)`, which is
    /// the shape all eight already share. Keeping the list here — rather than a `switch` inside
    /// the loop — is what makes `keys` and the dispatch provably the same list in the same order.
    private var appliers: [(key: String, apply: (String) -> (applied: Bool, report: String))] {
        [
            (Self.brainPolicyKey, { [decisionEngine] json in
                // Through the ENGINE, not straight to `BrainPolicy`: that is the path that hands
                // over the engine's own guardrails and carries `exploration_floor` to the bandit.
                let r = decisionEngine.applyPolicy(json)
                return (r.applied, r.report)
            }),
            (Self.paywallConfigKey, { [paywall] json in
                let r = paywall.apply(json)
                return (r.applied, r.report)
            }),
            (Self.purchaseRoutingKey, { [purchaseRouter] json in
                let r = purchaseRouter.apply(json)
                return (r.applied, r.report)
            }),
            (Self.subscriptionLifecycleKey, { [subscriptionLifecycle] json in
                let r = subscriptionLifecycle.apply(json)
                return (r.applied, r.report)
            }),
            (Self.battlePassKey, { [battlePass] json in
                let r = battlePass.apply(json)
                return (r.applied, r.report)
            }),
            (Self.recurringGrantsKey, { [recurringGrants] json in
                let r = recurringGrants.apply(json)
                return (r.applied, r.report)
            }),
            (Self.offerwallConfigKey, { [offerwall] json in
                let r = offerwall.apply(json)
                return (r.applied, r.report)
            }),
            (Self.crmBridgeKey, { [crmBridge] json in
                let r = crmBridge.apply(json)
                return (r.applied, r.report)
            })
        ]
    }
}
