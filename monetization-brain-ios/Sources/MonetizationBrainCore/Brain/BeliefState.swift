import Foundation

/// Discrete Bayesian posterior over `LatentPlayerType`: b_t+1(z) ∝ P(obs|z) * b_t(z).
///
/// A reference type (class), matching every future call site's expectation that
/// `update(likelihood:)` mutates the SAME instance other holders already have a reference to —
/// Kotlin's `data class` is used here as mutable state via a `MutableMap` field, not as an
/// immutable value.
///
/// `NSLock`-protected, matching every other piece of shared mutable in-memory state in this
/// package (`ConsentState`, `AdsBrain`, `BanditPolicy`, `StaticRemoteConfigProvider`) — nothing
/// calls `update(likelihood:)` from more than one thread today (this type isn't wired into
/// `AdsBrain` yet), but leaving it unsynchronized would be a real, silent gap the moment Phase 3
/// wires it behind `AdsBrain`'s already-`async` decision points.
public final class BeliefState: @unchecked Sendable {
    public let playerId: String

    private let lock = NSLock()
    private var _posterior: [LatentPlayerType: Double]

    /// A snapshot of the current posterior. Returns a copy (dictionaries are value types in
    /// Swift) taken under the lock, so callers never see a partially-updated map.
    public var posterior: [LatentPlayerType: Double] {
        lock.lock()
        defer { lock.unlock() }
        return _posterior
    }

    public init(playerId: String) {
        self.playerId = playerId
        self._posterior = Dictionary(uniqueKeysWithValues: LatentPlayerType.allCases.map { ($0, 0.25) })
    }

    private init(playerId: String, posterior: [LatentPlayerType: Double]) {
        self.playerId = playerId
        self._posterior = posterior
    }

    /// Updates the posterior in place. `likelihood` is P(observation just happened | each type)
    /// — estimating that likelihood is the feature pipeline's (`LikelihoodEstimator`'s)
    /// responsibility, not this type's.
    public func update(likelihood: [LatentPlayerType: Double]) {
        lock.lock()
        defer { lock.unlock() }
        updateLocked(likelihood: likelihood)
    }

    /// ═══ WHY UPDATE AND SERIALIZE MUST SHARE ONE ACQUISITION ═══
    ///
    /// `update(...)` then `serialize()` takes this lock TWICE, so a second caller can land its own
    /// update in between — and the string that gets persisted then describes neither caller's
    /// result, but a state no single update ever produced. The belief is a running posterior, so a
    /// snapshot spanning two interleaved updates is not merely stale, it is arithmetically
    /// unreachable.
    ///
    /// `mintSequence` is called INSIDE the same acquisition on purpose: it makes "higher sequence"
    /// mean "serialized from newer state", which is the whole contract `SequencedStoreWriter`
    /// relies on to drop a straggling write instead of letting it clobber a newer one. Minting it
    /// outside would order the writes by when they happened to be issued rather than by what they
    /// contain — which is the bug, wearing a sequence number.
    public func updateAndSerialize(
        likelihood: [LatentPlayerType: Double],
        mintSequence: () -> UInt64
    ) -> (serialized: String, sequence: UInt64) {
        lock.lock()
        defer { lock.unlock() }
        updateLocked(likelihood: likelihood)
        return (serializeLocked(), mintSequence())
    }

    /// MUST be called with `lock` held.
    private func updateLocked(likelihood: [LatentPlayerType: Double]) {
        var normalizer = 0.0
        var updated: [LatentPlayerType: Double] = [:]
        for type in LatentPlayerType.allCases {
            let prior = _posterior[type] ?? 0.0
            let lik = likelihood[type] ?? 1.0
            let unnorm = prior * lik
            updated[type] = unnorm
            normalizer += unnorm
        }
        // ═══ `> 0` IS NOT THE SAME CHECK AS "USABLE" ═══
        //
        // An infinite normalizer passes `> 0` and then every division below is `inf / inf` — the
        // whole posterior becomes NaN in one step. NaN then survives everything downstream by
        // being invisible to comparisons: `segmentWeights` filters on `weight > 0`, which is false
        // for NaN, so the weights come back EMPTY and `BanditPolicy.selectArm`'s
        // `precondition(!segmentWeights.isEmpty)` takes the app down on the next decision.
        //
        // Not reachable from this file's own callers — `LikelihoodEstimator` returns values in
        // (0, 1] — but a guard whose whole job is to reject a degenerate update should reject the
        // degenerate case it cannot see.
        guard normalizer > 0.0, normalizer.isFinite else { return }
        for type in LatentPlayerType.allCases {
            _posterior[type] = (updated[type] ?? 0.0) / normalizer
        }
    }

    /// The type with the highest posterior. Iterates `LatentPlayerType.allCases` in declared
    /// order with a strict `>` comparison — NOT `Dictionary.max(by:)`, whose tie-breaking is
    /// non-deterministic (Swift dictionaries have no guaranteed iteration order). This matches
    /// Kotlin's `maxByOrNull` on a `LinkedHashMap`, which deterministically favors the
    /// first-inserted key on a tie.
    public func mostLikelyType() -> LatentPlayerType {
        let snapshot = posterior
        var best = LatentPlayerType.allCases[0]
        var bestValue = snapshot[best] ?? 0.0
        for type in LatentPlayerType.allCases.dropFirst() {
            let value = snapshot[type] ?? 0.0
            if value > bestValue {
                bestValue = value
                best = type
            }
        }
        return best
    }

    /// Confidence in `mostLikelyType()`; low means fall back to a more neutral/safe policy.
    public func confidence() -> Double {
        posterior.values.max() ?? 0.0
    }

    /// Has anything been learned yet, or is this still sitting at the uniform prior?
    public func isStillPrior() -> Bool {
        posterior.values.allSatisfy { abs($0 - 0.25) < 1e-6 }
    }

    /// Compact string for persistence: "TYPE:0.31|TYPE:0.19|..."
    public func serialize() -> String {
        lock.lock()
        defer { lock.unlock() }
        return serializeLocked()
    }

    /// MUST be called with `lock` held. Reads `_posterior` directly rather than going through the
    /// `posterior` accessor, which takes the same non-reentrant lock and would deadlock.
    private func serializeLocked() -> String {
        LatentPlayerType.allCases
            .map { "\($0.rawValue):\(_posterior[$0] ?? 0.0)" }
            .joined(separator: "|")
    }

    /// Restores belief from a saved string. Malformed/missing data returns a uniform prior
    /// instead of throwing — losing belief only degrades personalization temporarily; crashing
    /// would break the whole play session (fail-safe principle, doc 27).
    public static func deserialize(playerId: String, raw: String?) -> BeliefState {
        let fallback = BeliefState(playerId: playerId)
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return fallback
        }
        var parsed: [LatentPlayerType: Double] = [:]
        var total = 0.0
        for part in raw.split(separator: "|") {
            let kv = part.split(separator: ":")
            // `isFinite`, not just `>= 0`: `Double("inf")` is a successful parse and `inf >= 0` is
            // true, so a persisted "inf" used to reach the normalisation below and come back as an
            // all-NaN posterior — which this function's own doc promises never to return.
            //
            // It is SUBSUMED by the `total.isFinite` check below, and mutation testing says so:
            // deleting this line fails nothing, because any infinite value makes the total
            // infinite too. Kept because the two state different invariants — "a value must be a
            // real number" is what a reader checks for here — but it is reasoning, not coverage,
            // and pretending otherwise is how a redundant guard survives a refactor that removes
            // the one actually doing the work.
            guard kv.count == 2,
                  let type = LatentPlayerType(rawValue: String(kv[0])),
                  let value = Double(kv[1]),
                  value >= 0.0, value.isFinite else { continue }
            parsed[type] = value
            total += value
        }
        // `total` can overflow to infinity even when every part of it is finite.
        guard parsed.count == LatentPlayerType.allCases.count, total > 0.0, total.isFinite else {
            return fallback
        }
        var normalized: [LatentPlayerType: Double] = [:]
        for type in LatentPlayerType.allCases {
            normalized[type] = (parsed[type] ?? 0.0) / total
        }
        return BeliefState(playerId: playerId, posterior: normalized)
    }
}
