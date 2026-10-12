import Foundation

/// A window onto what the brain currently believes and is enforcing — for debugging, QA, and
/// later a control plane (Android doc 26).
///
/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// READ-ONLY, AND THAT IS THE WHOLE DESIGN
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// The inspector must never feed back into a decision. If it did, behaviour with debugging on
/// would differ from production — the precise failure that makes QA stop trusting the tool and
/// start distrusting the bug reports made with it. So: recording happens AFTER the decision is
/// final, it is bounded, and it cannot fail.
///
/// It also never reaches into `DecisionEngine`'s internals. Android's version reads
/// `engine.guardrails` and `engine.bandit` directly; iOS keeps both private (handing those
/// objects out hands out their setters) and goes through the engine's read-through accessors
/// instead. A debug view that can raise a safety cap is not a debug view.
///
/// That applies to belief too, and less obviously: `beliefFor` returns the LIVE `BeliefState`
/// that decisions mutate, and `BeliefState.update` is public. So the inspector reads
/// `beliefReading`, which is four values and no way back to the object. `beliefFor` itself stays
/// public for callers that need the object — this is about what the inspector holds, not a
/// restriction imposed on hosts.
///
/// ═══ HOW THIS DIFFERS FROM `SelfCheckReport` ═══
///
/// `SelfCheckReport` answers "is the SDK wired up correctly" — a configuration audit, run once.
/// This answers "what is it deciding, for this player, right now", and the answer changes with
/// every decision. They are different questions and neither replaces the other.
public final class BrainInspector: @unchecked Sendable {
    public static let shared = BrainInspector()

    /// Records kept. Fixed so a long session cannot leak memory — thirty is enough to see a
    /// pattern in a bug report and small enough that nobody has to think about the cost.
    public static let maxHistory = 30

    private let lock = NSLock()
    private var history: [BrainDecisionRecord] = []
    private let clock: BrainClock

    public init(clock: BrainClock = SystemClock()) {
        self.clock = clock
    }

    // MARK: - History

    /// Called by `DecisionEngine.decide` once the response is final.
    ///
    /// INTERNAL, not public: a host that could write into the history could make a debug dump say
    /// something that never happened, and a bug report nobody can trust is worse than none.
    ///
    /// `totalCandidates` is passed in rather than derived from the response — see
    /// `BrainDecisionRecord.totalCandidates` for why Android's derivation is wrong.
    func recordDecision(
        request: DecisionRequest,
        response: DecisionResponse,
        totalCandidates: Int
    ) {
        let record = BrainDecisionRecord(
            timestampMs: clock.nowMs(),
            decisionId: response.decisionId,
            decisionPoint: request.decisionPoint,
            chosenActionId: response.chosenAction.actionId,
            chosenMode: response.chosenAction.mode,
            probability: response.chosenActionProbability,
            segment: response.segment,
            segmentConfidence: response.segmentConfidence,
            scenario: response.scenario,
            guardrailsApplied: response.guardrailsApplied,
            eligibleCount: response.eligibleActionIds.count,
            totalCandidates: totalCandidates
        )
        lock.lock()
        defer { lock.unlock() }
        history.append(record)
        if history.count > Self.maxHistory {
            history.removeFirst(history.count - Self.maxHistory)
        }
    }

    /// Most recent first — the order someone reading a dump wants, and the order Android returns.
    public func recentDecisions() -> [BrainDecisionRecord] {
        lock.lock()
        defer { lock.unlock() }
        return history.reversed()
    }

    public func clearHistory() {
        lock.lock()
        defer { lock.unlock() }
        history = []
    }

    // MARK: - Snapshot

    public func snapshot(engine: DecisionEngine, state: PlayerState) -> BrainSnapshot {
        let belief = engine.beliefReading(state.playerId)
        let isHoldout = HoldoutManager.isHoldout(state.playerId)
        // ═══ RESOLVED THE WAY `decide` RESOLVES IT, WHICH ANDROID'S SNAPSHOT DOES NOT ═══
        //
        // `decide` forces a holdout player onto `.balancedHybrid` — they are the control group
        // and are never personalized. Android's snapshot calls `scenarioFor` unconditionally, so
        // for a holdout player whose belief HAS moved, its dump prints the personalized scenario
        // and cap under a "SCENARIO IN FORCE" heading, contradicting the very decisions listed
        // lower down in the same dump. A dump that disagrees with itself is the failure this type
        // exists to avoid, so the holdout branch is reproduced here rather than the Android
        // output.
        let scenario: PersonalizationScenario = isHoldout ? .balancedHybrid : engine.scenarioFor(state.playerId)
        return BrainSnapshot(
            playerId: state.playerId,
            posterior: belief.posterior,
            segment: belief.mostLikelyType,
            confidence: belief.confidence,
            isStillPrior: belief.isStillPrior,
            scenario: scenario,
            // For a holdout player the scenario is not a low-confidence fallback, it is imposed —
            // `isHoldout` and the format's own annotation are what say so.
            scenarioIsFallback: !isHoldout && belief.confidence < PersonalizationScenario.minConfidence,
            activeSignals: LikelihoodEstimator.activeSignals(state),
            effectiveInterstitialCap: engine.effectiveInterstitialCap(for: scenario),
            guardrailsPrevented: engine.preventedCounts(),
            recentDecisions: recentDecisions(),
            policyVersion: engine.activePolicyVersion(),
            policyEnabled: engine.isPolicyEnabled(),
            policyReport: engine.policyApplyReport(),
            isHoldout: isHoldout,
            holdoutBucket: HoldoutManager.bucket(state.playerId),
            explorationFloor: engine.explorationFloor()
        )
    }

    // MARK: - Rendering

    /// Plain text for a log, a bug report, or a debug overlay.
    ///
    /// `static` and pure: rendering a snapshot must not depend on, or touch, anything live —
    /// a snapshot captured five minutes ago has to render exactly as it did then.
    public static func format(_ snapshot: BrainSnapshot) -> String {
        var lines: [String] = []
        lines.append("╔══ MONETIZATION BRAIN ══")
        lines.append("║ player: \(snapshot.playerId)")
        lines.append("║ policy: \(snapshot.policyVersion)" + (snapshot.policyEnabled ? "" : "  ⛔ KILL SWITCH ON"))
        lines.append("║ report: \(snapshot.policyReport)")
        let group = snapshot.isHoldout ? "HOLDOUT (control — NOT personalized)" : "treatment"
        lines.append(
            "║ group : \(group)  [bucket \(snapshot.holdoutBucket)/10000, "
                + "explore \(percent(snapshot.explorationFloor, decimals: 0))]"
        )

        lines.append("╠══ SEGMENT ══")
        if snapshot.isStillPrior {
            lines.append("║ ⚠ NOTHING LEARNED YET — belief is still the uniform prior")
        }
        // Sorted by probability, not by enum order: the top of this list is the answer, and
        // making someone scan an alphabetical list for the biggest number is a bad dump.
        for (type, p) in snapshot.posterior.sorted(by: { $0.value > $1.value }) {
            // Clamped in `Double` space: `Int(Double)` traps on a NaN, and a debug dump is the
            // last place that should be able to kill the app — it is what someone opens when
            // something is already wrong.
            let filled = JsonConfigValue.clamped(p * 20, lowerBound: 0, upperBound: 20)
            let bar = String(repeating: "█", count: filled)
            let mark = type == snapshot.segment ? "►" : " "
            let name = type.rawValue.padding(toLength: 26, withPad: " ", startingAt: 0)
            lines.append("║\(mark) \(name) \(fixed(p, decimals: 3)) \(bar)")
        }
        lines.append("║ confidence: \(percent(snapshot.confidence, decimals: 1))")

        lines.append("╠══ SCENARIO IN FORCE ══")
        let scenarioNote: String
        if snapshot.isHoldout {
            // Said on the scenario line itself, not only in the group line above: someone
            // scanning for "why is this player getting this treatment" reads this section.
            scenarioNote = "  (imposed: holdout control, never personalized)"
        } else if snapshot.scenarioIsFallback {
            scenarioNote = "  (fallback: not confident enough)"
        } else {
            scenarioNote = ""
        }
        lines.append("║ \(snapshot.scenario.displayName)\(scenarioNote)")
        lines.append("║ why: \(snapshot.scenario.rationale)")
        lines.append("║ interstitial cap/session : \(snapshot.effectiveInterstitialCap)")
        lines.append("║ rewarded aggressiveness  : \(fixed(snapshot.scenario.rewardedAggressiveness, decimals: 1))")
        lines.append("║ iap offer priority       : \(fixed(snapshot.scenario.iapOfferPriority, decimals: 1))")
        lines.append("║ free value generosity    : \(fixed(snapshot.scenario.freeValueGenerosity, decimals: 1))")

        lines.append("╠══ SIGNALS IN USE ══")
        if snapshot.activeSignals.isEmpty {
            lines.append("║ (none — nothing about this player is distinctive yet)")
        } else {
            lines.append(contentsOf: snapshot.activeSignals.map { "║ • \($0)" })
        }

        if !snapshot.guardrailsPrevented.isEmpty {
            lines.append("╠══ GUARDRAILS THAT BLOCKED SOMETHING ══")
            // Ties broken by name so two runs of the same state render identically — a dump that
            // reorders itself between runs is a dump people diff and get false hits from.
            let sorted = snapshot.guardrailsPrevented.sorted {
                $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key
            }
            lines.append(contentsOf: sorted.map { "║ \($0.key): \($0.value)×" })
        }

        if !snapshot.recentDecisions.isEmpty {
            lines.append("╠══ MOST RECENT DECISIONS ══")
            for d in snapshot.recentDecisions.prefix(8) {
                let blocked = d.guardrailsApplied.isEmpty
                    ? ""
                    : " ⛔\(d.guardrailsApplied.joined(separator: ","))"
                lines.append(
                    "║ \(d.decisionPoint.rawValue) → \(d.chosenMode.rawValue) (\(d.chosenActionId)) "
                        + "p=\(fixed(d.probability, decimals: 2)) "
                        + "[\(d.eligibleCount)/\(d.totalCandidates) arms]\(blocked)"
                )
            }
        }

        lines.append("╚════════════════════════")
        return lines.joined(separator: "\n")
    }

    /// `String(format:)` rather than interpolation: a raw `Double` renders as `0.30000000000000004`
    /// often enough to make a dump unreadable.
    private static func fixed(_ value: Double, decimals: Int) -> String {
        String(format: "%.\(decimals)f", value)
    }

    private static func percent(_ value: Double, decimals: Int) -> String {
        String(format: "%.\(decimals)f%%", value * 100)
    }
}
