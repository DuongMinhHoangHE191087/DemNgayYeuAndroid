import Foundation

/// Facade for DECIDE/FEEDBACK — the ONLY entry point code outside this package should call;
/// never call `BanditPolicy`/`GuardrailEngine`/event emission directly from outside.
///
/// Ties together every primitive from Phase 2 (`BeliefState`, `RewardModel`,
/// `LikelihoodEstimator`, `BanditPolicy`) with this phase's orchestration types
/// (`GuardrailEngine`, `LagrangianBudget`, `PersonalizationScenario`, `BrainStorage`) into one
/// `decide`/`feedback`/`observe` loop. Not yet wired into `AdsBrain`'s public decision-point
/// methods — that's Phase 3.5, mirroring the Phase 1 -> Phase 1.5 split (see the Phase 3 design
/// spec §1).
public final class DecisionEngine: @unchecked Sendable {
    public static let shared = DecisionEngine()

    private let policyVersion: String
    private let bandit: BanditPolicy
    private let guardrails: GuardrailEngine
    private let storage: BrainStorage
    private let eventSink: EventSink?
    private let policy: BrainPolicy
    /// Serialises `applyPolicy`. See that method for why it is separate from `lock`.
    private let applyLock = NSLock()
    private let inspector: BrainInspector
    /// Held, not just forwarded to `BrainStorage`: `brain_feedback`'s `outcome_timestamp_ms` is a
    /// value the backend reads, and until now it came from a bare `Date()` that no test could pin.
    /// The other `Date()` call sites in this file stay as they are on purpose — see `BrainClock`.
    private let clock: BrainClock

    private let lock = NSLock()
    private var beliefStates: [String: BeliefState] = [:]
    private var budgets: [String: LagrangianBudget] = [:]

    private var _estimatedDecisionsPerSession: Int = 30

    /// Estimated decision points per session, used to convert the session budget cap into a
    /// per-decision allowance (B/T in the BwK formula). A design value, not yet measured.
    public var estimatedDecisionsPerSession: Int {
        get { lock.lock(); defer { lock.unlock() }; return _estimatedDecisionsPerSession }
        set { lock.lock(); defer { lock.unlock() }; _estimatedDecisionsPerSession = newValue }
    }

    private let runId: String = {
        let millis = Int64(Date().timeIntervalSince1970 * 1000)
        let suffix = UUID().uuidString.prefix(6).lowercased()
        return String(millis, radix: 36) + "-" + suffix
    }()
    private let counterLock = NSLock()
    private var counter: Int64 = 0

    /// - Parameter eventSink: where `brain_decision`/`brain_feedback` events go. `nil` (the
    ///   default) means `decide`/`feedback` still work correctly, just without emitting an event
    ///   — the same "usable before configured" behavior `AdsBrain` itself has in Phase 1. Phase
    ///   3.5's wiring passes `AdsBrain`'s configured sink in.
    public init(
        policyVersion: String = "brain_v0_skeleton",
        bandit: BanditPolicy = BanditPolicy(),
        guardrails: GuardrailEngine = GuardrailEngine(),
        store: BrainKeyValueStore = UserDefaultsBrainStore(),
        eventSink: EventSink? = nil,
        clock: BrainClock = SystemClock(),
        policy: BrainPolicy = .shared,
        inspector: BrainInspector = .shared
    ) {
        self.policyVersion = policyVersion
        self.bandit = bandit
        self.guardrails = guardrails
        self.storage = BrainStorage(store: store, clock: clock)
        self.clock = clock
        self.eventSink = eventSink
        self.policy = policy
        self.inspector = inspector
        // Restore bandit stats learned in previous sessions. Without this, the bandit forgets
        // everything on every app restart while belief is saved — that phase mismatch makes
        // results non-reproducible and hard to diagnose.
        self.bandit.restore(storage.loadBanditStats())
    }

    /// Player's resource budget. Each player has their own — a shared global lambda would let one
    /// player's behavior affect another's (the exact shared-state bug this port avoids elsewhere).
    public func budgetFor(_ playerId: String) -> LagrangianBudget {
        lock.lock()
        defer { lock.unlock() }
        if let existing = budgets[playerId] { return existing }
        let created = LagrangianBudget()
        budgets[playerId] = created
        return created
    }

    /// Belief, restored from storage if not already in memory. Without this restoration step,
    /// every player resets to a uniform prior on every app launch — permanently "new," and
    /// personalization never accumulates.
    public func beliefFor(_ playerId: String) -> BeliefState {
        lock.lock()
        if let existing = beliefStates[playerId] {
            lock.unlock()
            return existing
        }
        lock.unlock()
        let restored = BeliefState.deserialize(playerId: playerId, raw: storage.loadBelief(playerId: playerId))
        lock.lock()
        defer { lock.unlock() }
        if let existing = beliefStates[playerId] { return existing }
        beliefStates[playerId] = restored
        return restored
    }

    /// Feeds a new observation into belief — this is where a segment actually moves. Call
    /// whenever player state changes meaningfully (level end, rewarded seen/declined, purchase).
    ///
    /// - Returns: `true` if belief was updated; `false` if there wasn't enough data to conclude
    ///   anything, or consent doesn't allow it.
    @discardableResult
    public func observe(_ state: PlayerState) -> Bool {
        // GDPR treats personalization as profiling, so ACCUMULATING a behavioral profile needs
        // consent. Blocked here, not at the point of use: data that isn't allowed to be
        // collected must not be written down at all, not written-then-avoided-on-read.
        guard ConsentState.shared.allowsProfiling() else { return false }
        let likelihood = LikelihoodEstimator.estimate(state)
        guard !likelihood.isEmpty else { return false }
        let belief = beliefFor(state.playerId)
        // ONE acquisition covering the update, the snapshot, and the sequence stamp. Calling
        // `update` then `serialize` takes the belief's lock twice, so a concurrent observer can
        // land its own update in between and the persisted string then describes a posterior no
        // single update ever produced — and the write itself would be unordered on top of that.
        // See `BeliefState.updateAndSerialize` and `BrainStorage`'s writer banner.
        let (serialized, sequence) = belief.updateAndSerialize(likelihood: likelihood) {
            storage.nextWriteSequence()
        }
        storage.saveBelief(playerId: state.playerId, serialized: serialized, sequence: sequence)
        return true
    }

    /// The personalization scenario currently applying to this player.
    public func scenarioFor(_ playerId: String) -> PersonalizationScenario {
        PersonalizationScenario.resolve(beliefFor(playerId))
    }

    /// Accumulated bandit observations for one (segment, arm). Exposed for diagnostics/display —
    /// deliberately narrower than exposing `BanditPolicy` itself, which would allow writes from
    /// outside.
    public func observationCount(decisionPoint: DecisionPoint, segment: LatentPlayerType, armId: String) -> Double {
        bandit.observationCount(segmentKey: "\(decisionPoint.rawValue)_\(segment.rawValue)", armId: armId)
    }

    /// Loads a policy artifact (Android doc 35).
    ///
    /// ═══ WHY THE ARTIFACT COMES IN THROUGH THE ENGINE ═══
    ///
    /// `BrainPolicy.apply` needs a `GuardrailEngine` to write into, and `RemoteConfigDispatcher`
    /// cannot supply one: `guardrails` is private here on purpose (see `adCapPerDay` — handing
    /// the object out would hand out its SETTERS, letting code outside this package raise a
    /// safety cap it is not allowed to raise). Routing the artifact through the engine keeps
    /// that closed and gives the artifact the one guardrail set that decisions actually consult.
    ///
    /// The second job is the exploration floor. `BrainPolicy` parses and clamps it but has no
    /// way to reach `bandit`, which is private for the same reason. Pushing it here — and ONLY
    /// when the artifact actually named one, see `pendingExplorationFloor` — is what makes
    /// `exploration_floor` a live knob rather than a parsed-and-discarded field.
    ///
    /// ═══ A DELIBERATE DIVERGENCE FROM WHAT ANDROID **SHIPS** ═══
    ///
    /// This method is a line-for-line port of Kotlin's `DecisionEngine.applyPolicy` — but that
    /// Kotlin method has no production caller. Android's Remote Config path
    /// (`FirHelperConfig.parserCfWithRemoteCf`) calls `BrainPolicy.apply(json,
    /// DecisionEngine.instance.guardrails)` DIRECTLY, skipping the hop, so on Android today an
    /// artifact's `exploration_floor` is parsed, clamped and then never reaches the bandit. (The
    /// Kotlin method's own doc comment claims the Remote Config flow calls it. It does not.)
    ///
    /// iOS routes through here instead, which means: for the same artifact field, **iOS re-tunes
    /// the bandit's exploration floor and Android does not.** That is a real cross-platform
    /// behavioural difference and it affects logged propensities, so segment off-policy analysis
    /// by platform. It is kept this way on purpose — a parsed-and-discarded config field is a
    /// trap for whoever sets it — rather than reproducing the Android bug for symmetry.
    ///
    /// ═══ WHY A LOCK OF ITS OWN ═══
    ///
    /// Applying is three separate lock acquisitions: `BrainPolicy` parses and stores under its
    /// lock, `pendingExplorationFloor` reads back under it again, and the bandit takes its own
    /// for the write. Two concurrent applies could therefore interleave into a state where the
    /// bandit runs artifact A's floor while `BrainPolicy.activeVersion()` reports artifact B —
    /// both values legitimate and clamped, but not from the same artifact. `applyLock`
    /// serialises the whole sequence so the last apply to FINISH is the one in force.
    ///
    /// This lock is touched by nothing else, so it never blocks a decision path even though it
    /// is held across `BrainPolicy`'s store writes. It does not protect a caller that bypasses
    /// the engine and calls `BrainPolicy.apply` directly, and it does not make a dispatch across
    /// all eight subsystems atomic — nothing does; see the dispatcher's design spec.
    ///
    /// Public because an integrator may load artifacts from their own source; hosts on the
    /// default Remote Config path get this called for them by `RemoteConfigDispatcher`.
    @discardableResult
    public func applyPolicy(_ json: String?) -> PolicyApplyResult {
        applyLock.lock()
        defer { applyLock.unlock() }
        let result = policy.apply(json, guardrails: guardrails)
        if let floor = policy.pendingExplorationFloor {
            bandit.explorationFloor = floor
        }
        return result
    }

    /// Effective interstitial cap in force for this player, after merging the guardrail and
    /// scenario caps.
    public func effectiveInterstitialCap(_ playerId: String) -> Int {
        guardrails.effectiveInterstitialCap(scenarioFor(playerId))
    }

    /// The daily ad-impression ceiling currently in force — `ComfortIndex.compute`'s
    /// `adCapPerDay` argument.
    ///
    /// A read-through accessor rather than exposing `guardrails` itself: the engine owns that
    /// object, and handing it out would hand out its SETTERS too, letting a caller outside this
    /// package raise a safety cap it is not allowed to raise.
    /// Read-throughs to the persisted daily offer count — same reasoning as `adCapPerDay`:
    /// `AdsBrain` can reach the engine but not its private storage.
    public func loadOfferCount24h() -> Int {
        storage.loadOfferCount24h()
    }

    @discardableResult
    public func bumpOfferCount24h() -> Int {
        storage.bumpOfferCount24h()
    }

    public func adCapPerDay() -> Int {
        guardrails.maxAds24h
    }

    /// Read-through to the persisted CRM first-purchase flag, same reasoning as `adCapPerDay`:
    /// `AdsBrain` can reach the engine but not its private storage.
    public func hasSentFirstPurchaseSignal(playerId: String) -> Bool {
        storage.hasSentFirstPurchaseSignal(playerId: playerId)
    }

    public func markFirstPurchaseSignalSent(playerId: String) {
        storage.markFirstPurchaseSignalSent(playerId: playerId)
    }

    /// Read-throughs `BrainInspector` needs, for the reason `adCapPerDay` spells out: the engine
    /// owns `policy` and `bandit`, and handing either object out would hand out its setters —
    /// letting a debug view, of all things, change what the brain decides.
    public func activePolicyVersion() -> String { policy.activeVersion() }

    public func isPolicyEnabled() -> Bool { policy.isEnabled() }

    public func policyApplyReport() -> String { policy.lastApplyReport() }

    public func explorationFloor() -> Double { bandit.explorationFloor }

    /// A player's belief as VALUES rather than as the live object `beliefFor` hands back.
    ///
    /// `beliefFor` returns the very `BeliefState` that `decide` and `observe` mutate, and
    /// `BeliefState.update` is public — a caller that only wants to LOOK at a belief should not
    /// be holding something it can move. `BrainInspector` reads this; `beliefFor` stays as it is
    /// for callers that legitimately need the object.
    public func beliefReading(_ playerId: String) -> BeliefReading {
        let belief = beliefFor(playerId)
        let posterior = belief.posterior
        return BeliefReading(
            posterior: Dictionary(
                uniqueKeysWithValues: LatentPlayerType.allCases.map { ($0, posterior[$0] ?? 0.0) }
            ),
            mostLikelyType: belief.mostLikelyType(),
            confidence: belief.confidence(),
            isStillPrior: belief.isStillPrior()
        )
    }

    /// The cap for a scenario the caller has ALREADY resolved.
    ///
    /// `effectiveInterstitialCap(_ playerId:)` resolves the scenario itself, which is wrong for a
    /// holdout player: `decide` forces them onto `.balancedHybrid`, so the personalized cap is
    /// not the cap in force. Anything that has to report what is actually enforced resolves the
    /// scenario the way `decide` does and then asks this.
    public func effectiveInterstitialCap(for scenario: PersonalizationScenario) -> Int {
        guardrails.effectiveInterstitialCap(scenario)
    }

    /// How often each guardrail actually blocked something — `ComfortIndex.policyAggressiveness`
    /// for callers that can only reach the engine. Same read-through reasoning as `adCapPerDay`.
    public func preventedCounts() -> [String: Int] {
        ComfortIndex.policyAggressiveness(engine: guardrails)
    }

    /// Clears everything learned about a player: both in-memory and storage.
    ///
    /// Both MUST be cleared. Clearing storage alone is a subtle bug seen often in testing: the
    /// engine is a long-lived instance, so the old belief is still sitting in `beliefStates`, and
    /// whoever reset thinks the state is clean while the system is still using stale data.
    public func resetPlayer(_ playerId: String) {
        lock.lock()
        beliefStates.removeValue(forKey: playerId)
        budgets.removeValue(forKey: playerId)
        lock.unlock()
        storage.clearBelief(playerId: playerId)
    }

    /// Starts a new session: shadow price returns to its starting level, session budget resets.
    public func startNewSession(_ playerId: String) {
        budgetFor(playerId).reset()
    }

    /// Maps belief posterior to segment weights for the bandit (each latent type is its own
    /// segment; the weight is exactly b_t(k)).
    private static func segmentWeights(decisionPoint: DecisionPoint, belief: BeliefState) -> [String: Double] {
        var result: [String: Double] = [:]
        let posterior = belief.posterior
        for type in LatentPlayerType.allCases {
            let weight = posterior[type] ?? 0.0
            if weight > 0.0 {
                result["\(decisionPoint.rawValue)_\(type.rawValue)"] = weight
            }
        }
        guard result.isEmpty else { return result }
        // ═══ A DEGENERATE BELIEF MUST NOT TAKE THE APP DOWN ═══
        //
        // Empty means every weight failed `> 0` — all zero, or all NaN, since NaN fails every
        // comparison. `BanditPolicy.selectArm` would then hit `precondition(!segmentWeights.isEmpty)`
        // and crash the host ON A DECISION PATH. `BeliefState` now refuses to build such a
        // posterior (both its update and its deserialize reject non-finite values), so this is
        // unreachable by construction and therefore NOT test-covered — said plainly rather than
        // left to look like it is. It stays because "the belief is unusable" and "the app dies"
        // should never be the same event: an unusable belief is exactly the uniform prior.
        let uniform = 1.0 / Double(LatentPlayerType.allCases.count)
        for type in LatentPlayerType.allCases {
            result["\(decisionPoint.rawValue)_\(type.rawValue)"] = uniform
        }
        return result
    }

    /// DECIDE: takes context + valid candidates, returns an action that has passed guardrails and
    /// bandit ranking.
    ///
    /// `candidates` must be supplied by the CALLER per business rules (business constraints
    /// FIRST, ML ranking SECOND) — `DecisionEngine` never synthesizes SKUs/offers/creatives
    /// itself. `candidates` MUST always contain at least one `.noAction` action.
    public func decide(
        request: DecisionRequest,
        state: PlayerState,
        candidates: [ActionCandidate],
        adsShownThisSession: Int = 0,
        minutesSinceLastInterstitial: Int = Int.max
    ) async -> DecisionResponse {
        precondition(!candidates.isEmpty, "candidates must not be empty")
        precondition(candidates.contains { $0.mode == .noAction }, "candidates must always contain .noAction")

        // Control group: NOT personalized, gets the neutral scenario. Guardrails still apply
        // normally — holdout measures the value of personalization, not an excuse to drop safety.
        // A control group harmed by missing guardrails would bias the comparison artificially in
        // the brain's favor.
        let isHoldout = HoldoutManager.isHoldout(request.playerId)

        // Scenario must be resolved BEFORE guardrails, since it tightens the resource cap.
        let scenario: PersonalizationScenario = isHoldout ? .balancedHybrid : scenarioFor(request.playerId)

        var guardrailsApplied: [String] = []
        let safeCandidates = candidates.filter { candidate in
            let violation = guardrails.evaluate(
                candidate: candidate,
                state: state,
                adsShownThisSession: adsShownThisSession,
                minutesSinceLastInterstitial: minutesSinceLastInterstitial,
                scenario: scenario,
                decisionPoint: request.decisionPoint
            )
            if let violation {
                guardrailsApplied.append(violation.code)
                return false
            }
            return true
        }

        // Guardrails may remove every real candidate; .noAction always remains as a safe fallback.
        let noActionFallback = candidates.first(where: { $0.mode == .noAction })
        let finalCandidates = safeCandidates.isEmpty
            ? [noActionFallback].compactMap { $0 }
            : safeCandidates

        let belief = beliefFor(request.playerId)
        let armIds = finalCandidates.map(\.actionId)

        let chosen: ActionCandidate
        let probability: Double
        if isHoldout {
            // Control: deterministic behavior, as it was before the brain existed — no bandit, no
            // exploration. Propensity = 1.0 since no random choice was made.
            chosen = finalCandidates.first(where: { $0.isBaseline })
                ?? finalCandidates.first(where: { $0.mode == .noAction })
                ?? finalCandidates[0]
            probability = 1.0
        } else {
            let segmentWeights = Self.segmentWeights(decisionPoint: request.decisionPoint, belief: belief)
            // Lagrangian shadow price: shapes the rhythm INSIDE the budget smoothly, instead of
            // letting the bandit choose freely and then hit a hard wall and go silent.
            let budget = budgetFor(request.playerId)
            var penalties: [String: Double] = [:]
            for candidate in finalCandidates {
                penalties[candidate.actionId] = budget.penaltyFor(candidate.mode)
            }
            let (armId, p) = bandit.selectArm(
                segmentWeights: segmentWeights,
                candidateArmIds: armIds,
                penalties: penalties
            )
            chosen = finalCandidates.first(where: { $0.actionId == armId }) ?? finalCandidates[0]
            probability = p

            // Update the dual variable after choosing: spending faster than budget raises lambda.
            let cap = guardrails.effectiveInterstitialCap(scenario)
            budget.observe(
                chosenMode: chosen.mode,
                budgetPerDecision: Double(cap) / Double(max(estimatedDecisionsPerSession, 1))
            )
        }

        let response = DecisionResponse(
            decisionId: nextDecisionId(playerId: request.playerId),
            chosenAction: chosen,
            chosenActionProbability: probability,
            policyVersion: policyVersion,
            guardrailsApplied: guardrailsApplied,
            eligibleActionIds: armIds,
            segment: belief.mostLikelyType(),
            segmentConfidence: belief.confidence(),
            scenario: scenario,
            isHoldout: isHoldout
        )
        await logDecision(request: request, response: response)
        // Debug bookkeeping, and the rule it lives by: an inspector must NEVER change what the
        // brain decides, or behaviour with debug on stops matching production — the exact thing
        // that makes QA stop trusting the tool. So it runs after the decision is final, it is
        // bounded (30 records), and nothing it does can fail: unlike Android's, which wraps this
        // in try/catch, there is nothing here that throws.
        inspector.recordDecision(
            request: request,
            response: response,
            totalCandidates: candidates.count
        )
        return response
    }

    /// FEEDBACK: updates the bandit posterior, WEIGHTED by belief rather than dumped entirely
    /// into the single most-likely segment — evidence isn't lost when a player's belief shifts
    /// to a different type mid-stream.
    ///
    /// `outcomeReward01` must be computed by the caller. The full reward formula needs an
    /// LTV/churn model this SDK doesn't have yet.
    public func feedback(
        request: DecisionRequest,
        response: DecisionResponse,
        outcomeReward01: Double
    ) async {
        // The control group's outcome is NEVER fed into the bandit. Their decision came from a
        // deterministic rule, not the bandit — feeding it in would bias the posterior with data
        // that's supposed to stay independent for comparison. Still logged in full for
        // incrementality analysis on the backend.
        if !response.isHoldout {
            let belief = beliefFor(request.playerId)
            let segmentWeights = Self.segmentWeights(decisionPoint: request.decisionPoint, belief: belief)
            // The whole batch plus its snapshot in ONE acquisition. Looping `update` and then
            // calling `serialize` takes the bandit's lock once per segment plus once more, so a
            // concurrent feedback call interleaves and the persisted string mixes arms from both.
            let batch = segmentWeights.map {
                (segmentKey: $0.key, armId: response.chosenAction.actionId,
                 reward01: outcomeReward01, weight: $0.value)
            }
            let (serialized, sequence) = bandit.updateBatchAndSerialize(batch) {
                storage.nextWriteSequence()
            }
            storage.saveBanditStats(serialized, sequence: sequence)
        }
        await logFeedback(playerId: request.playerId, decisionId: response.decisionId, outcomeReward01: outcomeReward01)
    }

    // MARK: - Event emission

    private func logDecision(request: DecisionRequest, response: DecisionResponse) async {
        guard let eventSink, ConsentState.shared.allowsDecisionLogging() else { return }
        let allowsPersonalization = ConsentState.shared.allowsPersonalization()
        await eventSink.send(SdkEvent(
            eventName: "brain_decision",
            eventData: [
                "decision_id": .string(response.decisionId),
                "decision_point": .string(request.decisionPoint.rawValue),
                "chosen_action_id": .string(response.chosenAction.actionId),
                "chosen_action_mode": .string(response.chosenAction.mode.rawValue),
                "chosen_action_probability": .double(response.chosenActionProbability),
                // Original order preserved (comma-joined, not sorted) — a set that changes order
                // between calls is still the same set, but order helps catch client-side bugs
                // during backend reconciliation.
                "eligible_actions": .string(response.eligibleActionIds.joined(separator: ",")),
                "guardrails_applied": .string(response.guardrailsApplied.joined(separator: ",")),
                "segment": .string(allowsPersonalization ? (response.segment?.rawValue ?? "UNKNOWN") : "UNKNOWN"),
                "segment_confidence": .double(allowsPersonalization ? response.segmentConfidence : 0.0),
                "scenario": .string(response.scenario?.rawValue ?? "UNKNOWN"),
                // Backend's normalize.py parses this with its integer helper, not a JSON bool.
                "is_holdout": .int(response.isHoldout ? 1 : 0),
                "policy_version": .string(response.policyVersion)
            ],
            userProperty: UserProperty(userId: request.playerId)
        ))
    }

    private func logFeedback(playerId: String, decisionId: String, outcomeReward01: Double) async {
        guard let eventSink, ConsentState.shared.allowsDecisionLogging() else { return }
        await eventSink.send(SdkEvent(
            eventName: "brain_feedback",
            eventData: [
                "decision_id": .string(decisionId),
                "outcome_event": .string("outcome_reward"),
                "outcome_timestamp_ms": .int(Int(clock.nowMs())),
                "reward_value": .double(outcomeReward01)
            ],
            userProperty: UserProperty(userId: playerId)
        ))
    }

    // MARK: - Decision id

    /// Generates an ABSOLUTELY UNIQUE `decisionId`, with no dependency on storage: `runId`
    /// distinguishes app runs, a lock-protected counter guarantees uniqueness within the process
    /// even when called from multiple threads.
    private func nextDecisionId(playerId: String) -> String {
        counterLock.lock()
        counter += 1
        let n = counter
        counterLock.unlock()
        return "d_\(playerId)_\(runId)_\(n)"
    }
}
