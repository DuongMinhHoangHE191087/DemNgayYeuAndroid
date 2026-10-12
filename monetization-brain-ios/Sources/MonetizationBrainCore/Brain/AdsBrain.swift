import Foundation

/// Single public entry point. Five decision-point methods (`shouldShowInterstitial`/
/// `shouldOfferRewarded`/`shouldOfferIap`/`chooseCrossModal`/`chooseAssist`) are backed by a real
/// `DecisionEngine` — guardrails, a Thompson Sampling bandit, and holdout bucketing all apply.
/// `enabled` (default `false`) and `rolloutFraction` (default `1.0`) gate whether the brain is
/// actually controlling the currently configured player (see `isActive()`): when either gate says
/// no, or before `configure(_:)`/`onSessionStart()` has run, every decision point returns its
/// caller-supplied baseline (or `true` for the boolean gates) with no `DecisionEngine` call and no
/// event emitted — "off/unconfigured" always means the exact behavior the host app had before
/// this SDK existed.
public final class AdsBrain: @unchecked Sendable {
    public static let shared = AdsBrain()

    public struct Configuration: Sendable {
        public let remoteConfig: RemoteConfigProvider
        public let eventSink: EventSink
        public let playerId: String
        public let decisionEngine: DecisionEngine
        /// The time seam. Cooldown logic here (`lastRewardedAtMs`, `lastInterstitialAtMs`,
        /// `lastIapOfferAtMs` and the elapsed check that reads them) is entirely time-dependent
        /// and had no way to be driven deterministically — a test could only sleep and hope.
        public let clock: BrainClock

        /// `decisionEngine` defaults to a freshly-constructed `DecisionEngine(eventSink: eventSink)`
        /// — NOT a bare `DecisionEngine()`, which would silently carry `eventSink: nil` and mean
        /// `brain_decision`/`brain_feedback` never reach this `Configuration`'s own event pipeline.
        /// Pass a pre-built `DecisionEngine` (e.g. one constructed with a fake `BrainKeyValueStore`
        /// and a fake `EventSink`) for deterministic tests.
        public init(
            remoteConfig: RemoteConfigProvider,
            eventSink: EventSink,
            playerId: String,
            decisionEngine: DecisionEngine? = nil,
            clock: BrainClock = SystemClock()
        ) {
            self.remoteConfig = remoteConfig
            self.eventSink = eventSink
            self.playerId = playerId
            self.decisionEngine = decisionEngine ?? DecisionEngine(eventSink: eventSink)
            self.clock = clock
        }
    }

    /// One player's in-flight session — the observable `PlayerState` snapshot plus the session
    /// counters and timestamps Android's `AdsBrain` tracks as bare private fields, plus the
    /// decision currently awaiting a downstream signal before its reward can be computed.
    ///
    /// A value type on purpose (unlike Android's in-place-mutated fields): every hook reads the
    /// current snapshot, computes the new value, and writes the WHOLE struct back under `lock` —
    /// no partial in-place mutation can race a `decide()` call reading it mid-update.
    private struct SessionState {
        var playerState: PlayerState
        var adsShownThisSession: Int = 0
        var lastInterstitialAtMs: Int64 = 0 // 0 = never
        var lastIapOfferAtMs: Int64 = 0
        var lastRewardedAtMs: Int64 = 0
        var levelAttempts: Int = 0
        var levelFails: Int = 0
        var rewardedOffered: Int = 0
        var rewardedAccepted: Int = 0
        var pendingRequest: DecisionRequest?
        var pendingResponse: DecisionResponse?
        var pendingActionHadValue: Bool = false
    }

    private var session: SessionState?
    private var _rolloutFraction: Double = 1.0

    private let lock = NSLock()
    private var configuration: Configuration?
    private var _enabled = false

    private init() {}

    /// Wires the facade up. Does not itself turn decisioning on — `enabled` stays `false` until
    /// the host app opts in, matching the Android SDK's "off by default" rule: brain features
    /// never turn themselves on.
    ///
    /// `async` because it emits one `sdk_configured` lifecycle event through the configured
    /// `eventSink` and awaits it, rather than firing it off in an untracked `Task` — an awaited
    /// call is what lets a test assert on the event deterministically instead of polling for a
    /// background task to finish.
    public func configure(_ configuration: Configuration) async {
        storeConfiguration(configuration)

        // Not gated on ConsentState.allowsDecisionLogging(): that hook is scoped to decision
        // logs specifically ("the log contains a pseudonymous id, the chosen action, and
        // propensity" — ConsentState's own doc comment), and an SDK-configuration lifecycle
        // event isn't one. The `brain_decision` event `DecisionEngine.decide` sends IS gated on
        // it (see `DecisionEngine.swift`).
        await configuration.eventSink.send(
            SdkEvent(
                eventName: "sdk_configured",
                eventData: ["sdk_version": .string(MonetizationBrainSDK.version)],
                userProperty: UserProperty(userId: configuration.playerId)
            )
        )
    }

    /// Plain synchronous helper so the lock is never acquired/released directly inside an
    /// `async` function body — `NSLock.lock()`/`unlock()` are `@available(*, noasync)` (a
    /// warning today, a hard error under the Swift 6 language mode). Calling a synchronous
    /// helper FROM an async function doesn't trigger that restriction; only locking directly
    /// inside the `async` function's own body does.
    private func storeConfiguration(_ configuration: Configuration) {
        lock.lock()
        self.configuration = configuration
        lock.unlock()
    }

    /// Thread-safe read of the current configuration, used by decision-point methods below.
    private func currentConfiguration() -> Configuration? {
        lock.lock()
        defer { lock.unlock() }
        return configuration
    }

    /// Mutates `session` under `lock` and returns whatever `body` returns — `nil` if no session
    /// exists yet (before the first `onSessionStart()`). Every method that reads OR writes
    /// `session` after construction goes through this or `currentSession()`; never `lock.lock()`
    /// directly, and never from inside an `async` function body.
    private func withSession<T>(_ body: (inout SessionState) -> T) -> T? {
        lock.lock()
        defer { lock.unlock() }
        guard var current = session else { return nil }
        let result = body(&current)
        session = current
        return result
    }

    private func currentSession() -> SessionState? {
        lock.lock()
        defer { lock.unlock() }
        return session
    }

    /// Snapshot of the current session's observable state — for a debug screen or startup log,
    /// matching Android's `currentState()`. `nil` before the first `onSessionStart()`.
    public func currentState() -> PlayerState? {
        currentSession()?.playerState
    }

    /// Interstitials shown in the current session. Read-only; the facade owns the counter.
    public func adsShownThisSession() -> Int {
        currentSession()?.adsShownThisSession ?? 0
    }

    /// Starts (or restarts) a session for the currently configured player. Unlike Android, iOS's
    /// `playerId` is fixed for a `Configuration`'s whole lifetime (a required field, not a
    /// self-generated fallback — see Phase 3 spec §1) — a real player switch means a fresh
    /// `configure(_:)` call with a new `Configuration`, not a `playerId` argument here. That still
    /// leaves a stale in-memory `session` from the PREVIOUS player around when this happens, so
    /// `resetSessionForNewSession` below does carry a "did the player change" branch — it discards
    /// the whole prior `SessionState` rather than reusing it when the incoming `playerId` differs.
    ///
    /// Resolves any decision left pending from a previous session as "player did not continue" —
    /// a session ending mid-decision (app killed, crashed) is itself the negative signal.
    public func onSessionStart() async {
        await resolvePending(playerContinued: false)
        guard let configuration = currentConfiguration() else { return }
        // Read the persisted daily count BEFORE taking the session lock: it is an in-memory read
        // on the storage side, but the rule is that nothing reaching storage runs under `lock`.
        let offersToday = configuration.decisionEngine.loadOfferCount24h()
        resetSessionForNewSession(playerId: configuration.playerId, iapOffers24h: offersToday)
        configuration.decisionEngine.startNewSession(configuration.playerId)
        await emitExperimentExposure(configuration)
    }

    /// One `experiment_exposure` row per session, for EVERY player the experiment covers —
    /// including the holdout group and players outside the rollout. Their membership flag IS the
    /// data: an experiment you can only see the treated half of cannot be measured.
    ///
    /// Gated on `enabled`, not on `isActive()`. Those differ exactly where it matters: `isActive`
    /// excludes the holdout group and anyone outside the rollout, which is precisely the
    /// population this event exists to record. `enabled == false` means no experiment is running
    /// at all, and a membership row for an experiment that is not running is noise.
    private func emitExperimentExposure(_ configuration: Configuration) async {
        guard enabled else { return }
        let record = HoldoutManager.exposureRecord(
            playerId: configuration.playerId,
            rolloutFraction: rolloutFraction
        )
        // `is_holdout`/`in_rollout` are `.int(0/1)`, not `.bool`: the backend columns are INT64
        // and Android sends 0/1. A JSON boolean survives only through Python's `int(True) == 1`,
        // which is coercion rather than conformance.
        await configuration.eventSink.send(SdkEvent(
            eventName: "experiment_exposure",
            eventData: [
                "experiment_id": .string(record.experimentId),
                "holdout_bucket": .int(record.bucket),
                "is_holdout": .int(record.isHoldout ? 1 : 0),
                "in_rollout": .int(record.inRollout ? 1 : 0),
                "salt_version": .string(record.saltVersion)
            ],
            userProperty: UserProperty(userId: configuration.playerId)
        ))
    }

    /// Plain synchronous helper — see `storeConfiguration`'s doc comment for why this can't be
    /// inlined into `onSessionStart`'s `async` body: `NSLock.lock()`/`unlock()` are
    /// `@available(*, noasync)`, so the lock/unlock pair must live in a synchronous function
    /// `onSessionStart` merely calls.
    ///
    /// A real player switch (a fresh `configure(_:)` call with a new `Configuration`'s
    /// `playerId` — see `onSessionStart`'s doc comment) discards the WHOLE previous `SessionState`,
    /// not just the session-scoped fields reset below: reusing it would carry player A's entire
    /// `PlayerState` — `purchaseCountLifetime`, `purchaseValueLifetimeUsd`, `sessionsLifetime`, and
    /// the belief history built from it — into player B's decisions and rewards, a data-integrity
    /// and privacy leak across players (Android guards against the same reuse: `AdsBrain.kt:108-115`).
    private func resetSessionForNewSession(playerId: String, iapOffers24h: Int) {
        lock.lock()
        defer { lock.unlock() }
        var current = session ?? SessionState(playerState: PlayerState(playerId: playerId))
        if current.playerState.playerId != playerId {
            current = SessionState(playerState: PlayerState(playerId: playerId))
        }
        current.playerState.sessionsLifetime += 1
        current.playerState.daysSinceLastSession = 0
        current.adsShownThisSession = 0
        current.lastInterstitialAtMs = 0
        current.lastIapOfferAtMs = 0
        current.playerState.purchasesThisSession = 0
        current.playerState.iapOffersThisSession = 0
        current.playerState.minutesSinceLastIapOffer = -1
        current.playerState.assistsThisSession = 0
        current.playerState.interstitialOpportunitiesThisSession = 0
        // Persisted, not reset: a relaunch must NOT hand back the day's allowance. This used to be
        // `= 0`, which quietly defeated the `max_iap_offers_24h` guardrail on every app restart.
        current.playerState.iapOffers24h = iapOffers24h
        session = current
    }

    /// Ends the session: any decision left pending is resolved as "player did not continue" —
    /// if an interstitial JUST showed and the session ends right after, that is a real negative
    /// signal, not a missing one.
    ///
    /// Then emits `brain_comfort` — the 4th North Star Metric (`ComfortIndex`), the counterweight
    /// to ARPDAU/revenue/subscription, all three of which are trivially improvable by making the
    /// player's experience worse. Session end is where it belongs: the session's ad exposure is
    /// final by then, and one row per session is the cadence the metric is read at.
    public func onSessionEnd() async {
        await resolvePending(playerContinued: false)
        await emitComfort()
    }

    /// ═══ `brain_comfort` (§4) ═══
    ///
    /// Not consent-gated: an aggregate quality metric carrying no profile and no identifier
    /// beyond the pseudonymous player id every event already carries. It deliberately does NOT
    /// carry `screen_name` — Android reads an ambient mutable global there that has no iOS
    /// counterpart, consistent with every event in Phases 7-9.
    ///
    /// Gated on `isActive()` like `observeIfActive`, and for the same reason: "off or out of
    /// rollout means exactly the behavior the host app had before this SDK existed" is this
    /// facade's whole contract, and an unexpected event on the wire would break it. Holdout
    /// players are INSIDE `isActive()` (holdout is decided further down, in `DecisionEngine`), so
    /// the control group still reports comfort — which is the comparison the metric exists to
    /// make.
    /// The current Comfort Index for this player, computed on demand — Android's `comfort()`.
    ///
    /// `nil` before `configure`/`onSessionStart`, because there is no player state to score. Not
    /// gated on `isActive()`, matching Android: a host dashboard reading it while the brain is off
    /// gets a number, not a refusal. Read `coverage` before `value` — only 2 of 5 components have
    /// data, so `isTrustworthy` is structurally `false` today (see `ComfortIndex`).
    public func comfort() -> ComfortResult? {
        guard let configuration = currentConfiguration(), let state = currentState() else { return nil }
        return ComfortIndex.compute(
            state: state,
            adCapPerDay: configuration.decisionEngine.adCapPerDay()
        )
    }

    private func emitComfort() async {
        guard isActive(), let configuration = currentConfiguration(), let result = comfort() else { return }
        let aggressiveness = ComfortIndex.describe(
            policyAggressiveness: configuration.decisionEngine.preventedCounts()
        )
        await configuration.eventSink.send(
            SdkEvent(
                eventName: "brain_comfort",
                eventData: [
                    "comfort_value": .double(result.value),
                    "comfort_coverage": .double(result.coverage),
                    "components_missing": .string(result.componentsMissing.joined(separator: ";")),
                    "policy_aggressiveness": .string(aggressiveness)
                ],
                userProperty: UserProperty(userId: configuration.playerId)
            )
        )
    }

    /// Takes (and clears) the pending decision, if any, returning what `resolvePending` needs to
    /// compute the reward. `nil` when nothing is pending — calling `resolvePending` when nothing
    /// is pending must be harmless (every lifecycle hook calls it unconditionally).
    private func takePendingDecision() -> (request: DecisionRequest, response: DecisionResponse, hadValue: Bool)? {
        withSession { s -> (DecisionRequest, DecisionResponse, Bool)? in
            guard let request = s.pendingRequest, let response = s.pendingResponse else { return nil }
            let hadValue = s.pendingActionHadValue
            s.pendingRequest = nil
            s.pendingResponse = nil
            s.pendingActionHadValue = false
            return (request, response, hadValue)
        }.flatMap { $0 }
    }

    /// Resolves whatever decision is currently pending with a downstream signal — the engine room
    /// of the whole design. No-op when nothing is pending, so every caller can call this
    /// unconditionally without first checking whether a decision is outstanding.
    private func resolvePending(playerContinued: Bool) async {
        guard let (request, response, hadValue) = takePendingDecision() else { return }
        guard let configuration = currentConfiguration() else { return }
        let reward = RewardModel.estimate(actionHadValue: hadValue, playerContinued: playerContinued)
        await configuration.decisionEngine.feedback(request: request, response: response, outcomeReward01: reward)
    }

    /// Whether decisioning is turned on. Defaults to `false` — every decision-point method
    /// returns its safe baseline until the host app explicitly opts in. See `isActive()`, which
    /// combines this with `rolloutFraction`.
    public var enabled: Bool {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _enabled
        }
        set {
            lock.lock()
            _enabled = newValue
            lock.unlock()
        }
    }

    /// Fraction of the population the brain actually controls, when `enabled == true`.
    /// Deterministic per-`playerId` bucketing via `HoldoutManager.isInRollout` — a player is
    /// always on the same side. Defaults to `1.0`: the safety switch is `enabled`, not this
    /// field — defaulting to `0` would make turning `enabled` on a silent no-op with nothing to
    /// diagnose. See design spec §1 for why the remote-config-driven staged-rollout use case
    /// (raising this gradually via a policy artifact) is deferred, not this field itself.
    public var rolloutFraction: Double {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _rolloutFraction
        }
        set {
            lock.lock()
            _rolloutFraction = newValue
            lock.unlock()
        }
    }

    /// Is the brain actually controlling the currently configured player? Combines both gates in
    /// one place — checking `enabled` alone and forgetting the rollout check (or vice versa) at
    /// a call site is exactly the kind of mistake that would leak an out-of-rollout player's data
    /// into `observe()`, corrupting the treatment/control comparison the rollout exists to make.
    private func isActive() -> Bool {
        guard let configuration = currentConfiguration() else { return false }
        return enabled && HoldoutManager.isInRollout(configuration.playerId, fraction: rolloutFraction)
    }

    /// `true` once `configure(_:)` has been called.
    public func isConfigured() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return configuration != nil
    }

    /// Diagnostic snapshot — call this from a debug screen or a startup log, not to gate
    /// behavior. Reflects misconfiguration honestly rather than staying silent about it.
    public func selfCheckReport() -> SelfCheckReport {
        var issues: [String] = []
        let configured = isConfigured()
        if !configured {
            issues.append("AdsBrain.configure(_:) has not been called yet.")
        }
        if !enabled {
            issues.append("AdsBrain.enabled is false — all decision points return their baseline.")
        }
        if configured && currentSession() == nil {
            issues.append("AdsBrain.onSessionStart() has not been called — all decision points return their baseline.")
        }
        if ConsentState.shared.tier() == .unknown {
            issues.append("Consent has not been configured — treated as granted by default.")
        }
        return SelfCheckReport(
            isConfigured: configured,
            isEnabled: enabled,
            consentTier: ConsentState.shared.tier(),
            issues: issues
        )
    }

    // MARK: - Decision points (real, DecisionEngine-backed)

    /// Minutes since `atMs`, or `never` if `atMs == 0` (the sentinel for "hasn't happened yet in
    /// this session"). Two different `never` values are used by callers on purpose — see each
    /// call site: `GuardrailEngine`'s cooldown rules read `-1`/`Int.max` differently depending on
    /// which guardrail is asking (Phase 3's `GuardrailEngine.evaluate` doc comments explain why).
    private func minutesSince(_ atMs: Int64, never: Int) -> Int {
        guard atMs != 0 else { return never }
        let elapsedMs = nowMsLocked() - atMs
        return Int(min(elapsedMs / 60_000, Int64(Int.max)))
    }

    /// Refreshes `minutesSinceLastIapOffer` right before a decision that needs it — `PlayerState`
    /// is a snapshot, it doesn't know the clock (same reasoning as every other timing field).
    private func refreshIapTiming() {
        _ = withSession { s -> Void in
            s.playerState.minutesSinceLastIapOffer = self.minutesSince(s.lastIapOfferAtMs, never: -1)
        }
    }

    /// @return `true` if an interstitial should show now. `true` unconditionally when
    /// unconfigured/inactive — the same "off = old behavior" contract every gate in this facade
    /// shares.
    public func shouldShowInterstitial() async -> Bool {
        guard let configuration = currentConfiguration() else { return true }
        guard isActive() else { return true }
        await resolvePending(playerContinued: true)
        // One locked call captures the opportunity-counter bump, the refreshed
        // minutesSinceLastRewarded, AND everything `decide` needs to read — avoids a window
        // between two separate locked calls where a concurrent hook could mutate `session` in
        // between (see the note after this method for why holding `lock` across the `await
        // decide(...)` below is not the fix).
        guard let snapshot = withSession({ s -> (state: PlayerState, adsShownThisSession: Int, minutesSinceLastInterstitial: Int) in
            s.playerState.interstitialOpportunitiesThisSession += 1
            s.playerState.minutesSinceLastRewarded = self.minutesSince(s.lastRewardedAtMs, never: -1)
            return (s.playerState, s.adsShownThisSession, self.minutesSince(s.lastInterstitialAtMs, never: Int.max))
        }) else { return true }
        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: .interstitialOpportunity)
        let candidates = [
            ActionCandidate(mode: .noAction, actionId: "no_action"),
            ActionCandidate(mode: .interstitial, actionId: "interstitial", isBaseline: true)
        ]
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: snapshot.state,
            candidates: candidates,
            adsShownThisSession: snapshot.adsShownThisSession,
            minutesSinceLastInterstitial: snapshot.minutesSinceLastInterstitial
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return response.chosenAction.mode == .interstitial
    }

    /// @return `true` if a rewarded ad should be offered now. The ethical "no manufactured fail"
    /// guardrail applies automatically inside `decide` — no caller-side check needed.
    public func shouldOfferRewarded() async -> Bool {
        guard let configuration = currentConfiguration() else { return true }
        guard isActive() else { return true }
        await resolvePending(playerContinued: true)
        guard let session = currentSession() else { return true }
        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: .rewardedOpportunity)
        let candidates = [
            ActionCandidate(mode: .noAction, actionId: "no_action"),
            ActionCandidate(mode: .rewardedAd, actionId: "rewarded", isBaseline: true)
        ]
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: session.playerState,
            candidates: candidates,
            adsShownThisSession: session.adsShownThisSession
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return response.chosenAction.mode == .rewardedAd
    }

    /// @return `true` if an IAP offer should be shown now. `decisionPoint` distinguishes contexts
    /// with different ethical weight (`.shopOpen` — player-initiated; `.currencyLow` — resource
    /// pressure) so the bandit learns them separately, never pooled into one arm. Frustration and
    /// spend-velocity guardrails apply automatically inside `decide`.
    ///
    /// `async` — was synchronous in Phase 1; every caller needs an `await` added.
    public func shouldOfferIap(decisionPoint: DecisionPoint = .shopOpen) async -> Bool {
        guard let configuration = currentConfiguration() else { return true }
        guard isActive() else { return true }
        await resolvePending(playerContinued: true)
        refreshIapTiming()
        guard let session = currentSession() else { return true }
        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: decisionPoint)
        let candidates = [
            ActionCandidate(mode: .noAction, actionId: "no_action"),
            ActionCandidate(mode: .iapOffer, actionId: "iap_offer", isBaseline: true)
        ]
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: session.playerState,
            candidates: candidates,
            adsShownThisSession: session.adsShownThisSession
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return response.chosenAction.mode == .iapOffer
    }

    /// Real cross-modal decision: at ONE decision point, the brain chooses between `.noAction`,
    /// `.rewardedAd`, and `.iapOffer` by the player's pay-propensity — instead of two blind
    /// Boolean gates (`shouldOfferRewarded`/`shouldOfferIap`) asking separately. Ad and purchase
    /// compete as candidates IN THE SAME ROUND, so pressure from one substitutes for the other
    /// rather than stacking.
    ///
    /// `baseline` is what the game would do without the brain — holdout, and the inactive/
    /// unconfigured fallback, always return exactly it. `allowRewarded`/`allowIapOffer` let the
    /// caller drop an arm that doesn't exist at this point (e.g. no ad fill). Both disallowed
    /// means there is no decision to make at all — return `baseline` without calling `decide`, so
    /// a one-arm non-decision never pollutes the OPE log.
    public func chooseCrossModal(
        decisionPoint: DecisionPoint,
        allowRewarded: Bool = true,
        allowIapOffer: Bool = true,
        baseline: ActionMode = .noAction
    ) async -> CrossModalDecision {
        if !allowRewarded && !allowIapOffer {
            return CrossModalDecision(action: baseline, isHoldout: false)
        }
        guard let configuration = currentConfiguration() else {
            return CrossModalDecision(action: baseline, isHoldout: false)
        }
        guard isActive() else {
            return CrossModalDecision(action: baseline, isHoldout: false)
        }
        await resolvePending(playerContinued: true)
        if allowIapOffer { refreshIapTiming() }
        guard let session = currentSession() else {
            return CrossModalDecision(action: baseline, isHoldout: false)
        }

        var candidates = [ActionCandidate(mode: .noAction, actionId: "no_action")]
        if allowRewarded {
            candidates.append(ActionCandidate(mode: .rewardedAd, actionId: "rewarded", isBaseline: baseline == .rewardedAd))
        }
        if allowIapOffer {
            candidates.append(ActionCandidate(mode: .iapOffer, actionId: "iap_offer", isBaseline: baseline == .iapOffer))
        }

        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: decisionPoint)
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: session.playerState,
            candidates: candidates,
            adsShownThisSession: session.adsShownThisSession
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return CrossModalDecision(action: response.chosenAction.mode, isHoldout: response.isHoldout)
    }

    /// Real gameplay-assist decision: chooses between `.noAction`, `.freeAction` (free gift/hint),
    /// and `.gameplayConfig` (ease difficulty) — the two modes never offered anywhere else.
    ///
    /// DELIBERATELY has no `.iapOffer` arm. Rescuing a stuck player with something they must PAY
    /// for is exploiting frustration — exactly what `ETH_FRUSTRATION_EXPLOIT` blocks elsewhere.
    /// Paid rescue is a `chooseCrossModal`/`shouldOfferIap` decision, where the guardrail can
    /// refuse it; it is structurally absent here, not merely discouraged.
    public func chooseAssist(
        decisionPoint: DecisionPoint,
        allowFreeGift: Bool = true,
        allowDifficultyTuning: Bool = true,
        baseline: ActionMode = .noAction
    ) async -> AssistDecision {
        if !allowFreeGift && !allowDifficultyTuning {
            return AssistDecision(action: baseline, isHoldout: false)
        }
        guard let configuration = currentConfiguration() else {
            return AssistDecision(action: baseline, isHoldout: false)
        }
        guard isActive() else {
            return AssistDecision(action: baseline, isHoldout: false)
        }
        await resolvePending(playerContinued: true)
        guard let session = currentSession() else {
            return AssistDecision(action: baseline, isHoldout: false)
        }

        var candidates = [ActionCandidate(mode: .noAction, actionId: "no_action")]
        if allowFreeGift {
            candidates.append(ActionCandidate(mode: .freeAction, actionId: "free_assist", isBaseline: baseline == .freeAction))
        }
        if allowDifficultyTuning {
            candidates.append(ActionCandidate(mode: .gameplayConfig, actionId: "ease_difficulty", isBaseline: baseline == .gameplayConfig))
        }

        let request = DecisionRequest(playerId: configuration.playerId, decisionPoint: decisionPoint)
        let response = await configuration.decisionEngine.decide(
            request: request,
            state: session.playerState,
            candidates: candidates,
            adsShownThisSession: session.adsShownThisSession
        )
        _ = withSession { s -> Void in
            s.pendingRequest = request
            s.pendingResponse = response
        }
        return AssistDecision(action: response.chosenAction.mode, isHoldout: response.isHoldout)
    }

    // MARK: - Task 2: behavior hooks (progression, economy, assist, rewarded/interstitial signals)

    /// Recomputes the two rate fields from their raw counters — the client-side stand-in for a
    /// feature pipeline this SDK doesn't have yet (matches Android's `recompute()`; doc 31 §1).
    private func recompute(_ s: inout SessionState) {
        s.playerState.failRateLast30m = s.levelAttempts > 0
            ? Double(s.levelFails) / Double(s.levelAttempts) : 0.0
        s.playerState.rewardedOpportunitiesRecent = s.rewardedOffered
        s.playerState.rewardedAcceptRateRecent = s.rewardedOffered > 0
            ? Double(s.rewardedAccepted) / Double(s.rewardedOffered) : 0.0
    }

    /// Feeds the latest state into belief, gated on `isActive()` — mirrors Android's private
    /// `observe()`. `DecisionEngine.observe` has its own separate consent gate
    /// (`allowsProfiling()`); this gate is about rollout/holdout membership, a different axis.
    private func observeIfActive() {
        guard isActive(), let configuration = currentConfiguration(), let state = currentState() else { return }
        // ═══ THE CRM TRIGGER IS "THE ARGMAX ACTUALLY CHANGED" (§1) ═══
        //
        // NOT "an update ran". Belief moves on nearly every observation — a signal per update
        // would push a CRM message on every level end, for every player, forever, and the
        // audience a marketing platform builds from it would never settle. What a CRM audience
        // can act on is the discrete moment the player's most likely type FLIPS.
        //
        // `beliefFor` returns the same instance `observe` mutates, so the two reads bracket the
        // update. A concurrent observer on another thread could in principle land its own update
        // between them and make this attribute the flip to the wrong observation; the signal is
        // still correct (the argmax did change, once), and the alternative — holding a lock
        // across `DecisionEngine.observe` and a host `deliver` call — is the deadlock §2.2 is
        // about.
        let belief = configuration.decisionEngine.beliefFor(state.playerId)
        let before = belief.mostLikelyType()
        guard configuration.decisionEngine.observe(state) else { return }
        let after = belief.mostLikelyType()
        guard after != before else { return }
        CrmBridge.shared.emitSegmentChange(
            playerId: state.playerId,
            from: before.rawValue,
            to: after.rawValue
        )
    }

    public func onLevelFail() async {
        await resolvePending(playerContinued: true)
        _ = withSession { s -> Void in
            s.levelAttempts += 1
            s.levelFails += 1
            s.playerState.failStreakCurrent += 1
            recompute(&s)
        }
        observeIfActive()
    }

    public func onLevelWin() async {
        await resolvePending(playerContinued: true)
        _ = withSession { s -> Void in
            s.levelAttempts += 1
            s.playerState.failStreakCurrent = 0
            s.playerState.currentLevel += 1
            recompute(&s)
        }
        observeIfActive()
    }

    public func onPurchase(usd: Double) async {
        // Credit the pending decision as having had value ONLY if it was actually an IAP offer —
        // a purchase landing while an unrelated decision (e.g. interstitial) is pending must not
        // credit that unrelated action, or the bandit learns the wrong lesson (Android's own
        // comment on `onPurchase` calls this out explicitly).
        _ = withSession { s -> Void in
            if s.pendingResponse?.chosenAction.mode == .iapOffer {
                s.pendingActionHadValue = true
            }
        }
        await resolvePending(playerContinued: true)
        // ═══ THE FLAG IS CAPTURED BEFORE THE COUNTER MOVES ═══
        //
        // `purchaseCountLifetime == 0` is only true until this same block increments it, so the
        // read and the increment have to happen in ONE locked mutation. Reading it afterwards
        // would make the first purchase indistinguishable from the second, and reading it in a
        // separate acquisition beforehand would let a concurrent purchase slip in between and
        // emit `first_purchase` twice.
        let wasFirstPurchase = withSession { s -> Bool in
            let isFirst = s.playerState.purchaseCountLifetime == 0
            s.playerState.purchasesThisSession += 1
            s.playerState.purchaseCountLifetime += 1
            s.playerState.purchaseValueLifetimeUsd += usd
            s.playerState.daysSinceLastPurchase = 0
            return isFirst
        } ?? false
        // Deliberately NOT gated on `isActive()`, unlike `observeIfActive`: a first purchase is a
        // fact about the player, not a brain decision, and the CRM channel is an independent
        // subsystem a host may use without ever enabling decisioning. `CrmBridge`'s own gates
        // (§3.3) decide whether it goes anywhere — and with no config applied, nothing does.
        //
        // "First" must mean first EVER on this device, not first since the process started.
        // `purchaseCountLifetime` is in-memory, so on its own every relaunch-then-purchase would
        // send another `first_purchase` — and a marketing platform re-runs its welcome campaign
        // each time, for a player who is not new. A persisted per-player flag closes that; it is
        // checked and set together so a second purchase in the same session cannot slip through.
        //
        // Still device-local: a reinstall, or the same player on a second device, sends it again.
        // Closing THAT needs account-level truth the SDK does not have — the same server-side
        // boundary every other phase here has run into.
        if wasFirstPurchase, let configuration = currentConfiguration(),
           !configuration.decisionEngine.hasSentFirstPurchaseSignal(playerId: configuration.playerId) {
            configuration.decisionEngine.markFirstPurchaseSignalSent(playerId: configuration.playerId)
            CrmBridge.shared.emitFirstPurchase(playerId: configuration.playerId, usd: usd)
        }
        observeIfActive()
    }

    public func onRewardedOffered(accepted: Bool) async {
        _ = withSession { s -> Void in
            s.rewardedOffered += 1
            if accepted {
                s.rewardedAccepted += 1
                s.playerState.adImpressions24h += 1
                s.lastRewardedAtMs = nowMsLocked()
            }
            recompute(&s)
            s.pendingActionHadValue = accepted
        }
        await resolvePending(playerContinued: accepted)
        observeIfActive()
    }

    /// A rewarded/interstitial impression just happened. Marks the pending decision as having had
    /// value — whether the player then continued playing is a SEPARATE question the next signal
    /// answers, not decided here (see `RewardModel`).
    public func onInterstitialShown() {
        _ = withSession { s -> Void in
            s.adsShownThisSession += 1
            s.playerState.adImpressions24h += 1
            s.lastInterstitialAtMs = nowMsLocked()
            s.pendingActionHadValue = true
        }
    }

    /// Caller decided not to show (or the ad wasn't ready). Not a value signal.
    public func onAdSkipped() {
        _ = withSession { s -> Void in s.pendingActionHadValue = false }
    }

    /// Actually showed the IAP offer on screen. NOT a value signal by itself — a shown offer
    /// nobody buys created nothing; value is only credited when `onPurchase` resolves this same
    /// pending decision. Gated on `isActive()`: these counters are only read from inside
    /// `shouldOfferIap`, which already returns `true` immediately when inactive — bumping them
    /// while inactive would create dead data with nothing to consume it.
    public func onIapOfferShown() {
        guard isActive(), let configuration = currentConfiguration() else { return }
        // Bump the persisted daily count OUTSIDE the session lock — it writes to storage, and the
        // bump is atomic on storage's own lock, so ordering it before `withSession` loses nothing.
        let offersToday = configuration.decisionEngine.bumpOfferCount24h()
        _ = withSession { s -> Void in
            s.playerState.iapOffersThisSession += 1
            s.playerState.iapOffers24h = offersToday
            s.lastIapOfferAtMs = nowMsLocked()
            s.pendingActionHadValue = false
        }
    }

    /// Actually handed the assist out (gift shown / difficulty applied). Same "not value yet"
    /// reasoning as `onIapOfferShown` — value is credited by `onAssistUsed(used: true)`.
    public func onAssistShown() {
        guard isActive() else { return }
        _ = withSession { s -> Void in
            s.playerState.assistsThisSession += 1
            s.pendingActionHadValue = false
        }
    }

    /// Outcome of the assist just shown. `used: true` is itself a continuation signal — resolve
    /// immediately, same as `onRewardedOffered(accepted: true)`. `used: false` (ignored) is NOT
    /// resolved as a negative here — the player may still self-recover and stay; wait for a real
    /// downstream signal (`onLevelWin`/`onLevelFail`/`onSessionEnd`), same as `onAdSkipped`.
    public func onAssistUsed(used: Bool) async {
        guard isActive() else { return }
        if used {
            _ = withSession { s -> Void in s.pendingActionHadValue = true }
            await resolvePending(playerContinued: true)
        } else {
            _ = withSession { s -> Void in s.pendingActionHadValue = false }
        }
    }

    /// Manual escape hatch: the host app has some OTHER activity signal worth treating as "still
    /// playing" that isn't covered by a more specific hook above.
    public func onPlayerContinued() async {
        await resolvePending(playerContinued: true)
    }

    /// MUST be called with `lock` held, or from a path that already holds it — it reads
    /// `configuration` directly rather than through `currentConfiguration()`, which would take the
    /// same non-reentrant lock and deadlock inside a `withSession` closure.
    ///
    /// Falls back to the system clock when unconfigured: a timestamp is still better than zero,
    /// and every call site here is inside a session that only exists after `configure`.
    private func nowMsLocked() -> Int64 {
        (configuration?.clock ?? SystemClock()).nowMs()
    }

    /// Test-only: clears configuration, session state, and resets `enabled`/`rolloutFraction` to
    /// their defaults. `session` must be cleared here too — `AdsBrain.shared` is a singleton
    /// reused across every test in the process, so a session left over from one test (e.g. one
    /// that called `onSessionStart()`) would otherwise leak into the next test's `sessionsLifetime`
    /// / `currentState()` assertions regardless of test declaration order.
    func resetForTest() {
        lock.lock()
        configuration = nil
        _enabled = false
        session = nil
        _rolloutFraction = 1.0
        lock.unlock()
    }
}
