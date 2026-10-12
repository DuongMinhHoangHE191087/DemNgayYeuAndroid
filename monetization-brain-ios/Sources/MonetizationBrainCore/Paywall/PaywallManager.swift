import Foundation

/// Remote-configured, A/B-tested, **holdout-native** paywall decision layer.
///
/// ═══ THE SDK DOES NOT DRAW UI ═══
///
/// `paywallFor()` returns *what to sell, in what order, with which variant* — the app draws it.
/// Same boundary `AdsBrain`'s decision points and `IapHelper.purchase(_:)` already established:
/// the brain answers "should," the caller executes. A library that draws paywalls either looks
/// out of place in every game or has to ship a theming system bigger than the feature itself —
/// which is why top-grossing games hand-roll their own paywall UI instead of using a vendor's.
///
/// ═══ NATIVE HOLDOUT — A VERIFIED MARKET WHITE SPACE ═══
///
/// No major paywall vendor (RevenueCat, Adapty, Superwall, Apphud, Qonversion) has a native
/// holdout — they measure variant A vs. B, never "paywall at all vs. NOTHING," so they can't
/// answer the absolute-value question. Here the holdout group receives NO paywall at all, and
/// the skip count is tracked so a later analysis knows the denominator — a silent skip would
/// make the control group look like it doesn't exist in the data.
///
/// ═══ BRAIN GATE — PAYWALL ASKS THE BRAIN BEFORE SHOWING ANYTHING ═══
///
/// `paywallFor()` calls `AdsBrain.shared.shouldOfferIap()` — the exact cross-modal decision every
/// other IAP-offer path uses — so paywall carries the SAME guardrails: no offer while stuck, no
/// spamming purchase velocity, no exceeding session/24h caps/cooldown. When the brain is off,
/// `shouldOfferIap` returns `true` with no side effect — paywall behaves exactly as it would
/// with no brain at all.
///
/// The check order in `paywallFor()` is DELIBERATE:
///   1. config (not loaded / placement not declared → `nil`)
///   2. **paywall holdout checked FIRST** — costs zero bandit decisions for someone who will
///      never see a paywall at all. This is itself a design choice (design spec §1): the
///      measurement is "does the WHOLE offer machine (brain + paywall) create value versus
///      nothing," the exact absolute-value question — not "the paywall UI alone."
///   3. brain gate (brain declines → `nil`)
///   4. pick a variant
///
/// The paywall control group and the brain control group are ONE group (the same
/// `HoldoutManager.isHoldout` this port already ships) — deliberate, not a shortcut: the unit
/// being measured is the whole machine, so someone in the control group gets neither a
/// personalized decision NOR a paywall. Splitting them into two groups would dilute the
/// measurement — half the machine would still run on people labeled "nothing."
public final class PaywallManager: @unchecked Sendable {
    public static let shared = PaywallManager()

    public struct Configuration: Sendable {
        public let eventSink: EventSink

        public init(eventSink: EventSink) {
            self.eventSink = eventSink
        }
    }

    /// Dedicated hash namespace for A/B variant selection — independent of both brain holdout
    /// and brain rollout. Sharing a namespace with either would correlate the two "experiments"
    /// completely (everyone in holdout would land in the exact same variant), making them
    /// impossible to run side by side.
    private static let hashNamespace = "paywall_variant"

    private let lock = NSLock()
    private var configuration: Configuration?
    private var placements: [String: [PaywallVariant]] = [:]
    private var decisionPoints: [String: DecisionPoint] = [:]
    private var version: String = "not loaded"
    private var holdoutSkips: Int = 0

    public init() {}

    public func configure(_ configuration: Configuration) {
        lock.lock()
        self.configuration = configuration
        lock.unlock()
    }

    private func currentConfiguration() -> Configuration? {
        lock.lock()
        defer { lock.unlock() }
        return configuration
    }

    public func activeVersion() -> String {
        lock.lock()
        defer { lock.unlock() }
        return version
    }

    /// Number of times a holdout player was skipped — the denominator for later analysis.
    public func holdoutSkipCount() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return holdoutSkips
    }

    /// Test-only, and INTERNAL on purpose. This was `public clear()` — the exact Android footgun
    /// design spec §3.8 diagnoses: any host code could call it in production and reset a live
    /// paywall config back to defaults, wiping an operator's remote-config state (and the holdout
    /// skip count, the denominator a running experiment is being measured against) with no signal
    /// that it happened. Narrowed to match `Entitlements`/`ConsentState`/`AdsBrain`/
    /// `SubscriptionLifecycle`, which all keep their reset hook inside the module under this name.
    func resetForTest() {
        lock.lock()
        placements = [:]
        decisionPoints = [:]
        version = "not loaded"
        holdoutSkips = 0
        lock.unlock()
    }

    // MARK: - Config

    /// Parses and applies a remote config JSON string. NEVER throws out to the caller — a
    /// malformed config just means the currently-running config keeps running, matching
    /// `BrainPolicy`'s/`FxRates`' own fail-safe principle.
    @discardableResult
    public func apply(_ json: String?) -> PaywallApplyResult {
        guard let json, !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return reject("empty config")
        }
        guard let data = json.data(using: .utf8),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return reject("JSON did not parse to an object")
        }
        guard let ver = root["version"] as? String, !ver.isEmpty else {
            return reject("missing 'version' field")
        }
        guard let rawPlacements = root["placements"] as? [String: Any] else {
            return reject("missing 'placements' field")
        }

        var notes: [String] = []
        var parsedPlacements: [String: [PaywallVariant]] = [:]
        var parsedDecisionPoints: [String: DecisionPoint] = [:]

        for (placement, value) in rawPlacements {
            guard let block = value as? [String: Any] else {
                notes.append("\(placement): not an object -> skipped")
                continue
            }

            // Which DecisionPoint the brain uses when this placement asks. Any unusable value ->
            // warn and keep the default .shopOpen; NOT dropping the whole placement over a typo
            // in an optional field.
            if let dp = Self.parseDecisionPoint(block["decision_point"], placement: placement, notes: &notes) {
                parsedDecisionPoints[placement] = dp
            }

            guard let variantList = block["variants"] as? [Any] else {
                notes.append("\(placement): 'variants' missing or not an array -> skipped")
                continue
            }
            var variants: [PaywallVariant] = []
            for element in variantList {
                guard let m = element as? [String: Any] else {
                    notes.append("\(placement): a variant entry is not an object -> skipped")
                    continue
                }
                guard let id = m["id"] as? String, !id.isEmpty else {
                    notes.append("\(placement): a variant is missing a valid 'id' -> skipped")
                    continue
                }
                let products = Self.parseProducts(m["products"], label: "\(placement)/\(id)", notes: &notes)
                guard !products.isEmpty else {
                    // An empty-products variant would render as a blank screen — drop it and
                    // say so, rather than let it reach the app.
                    notes.append("\(placement)/\(id) has no products -> skipped")
                    continue
                }
                let weight = max(0, Self.parseWeight(m["weight"], label: "\(placement)/\(id)", notes: &notes))
                variants.append(PaywallVariant(id: id, weight: weight, products: products, highlight: m["highlight"] as? String))
            }
            if variants.contains(where: { $0.weight > 0 }) {
                let totalWeight = variants.reduce(0) { $1.weight > 0 ? $0 + $1.weight : $0 }
                if totalWeight > Self.maxVariantWeight {
                    notes.append("\(placement): total variant weight \(totalWeight) exceeds the \(Self.maxVariantWeight)-bucket space -> selection is skewed")
                }
                parsedPlacements[placement] = variants
            } else {
                notes.append("\(placement) has no valid variants left -> skipped")
            }
        }

        lock.lock()
        placements = parsedPlacements
        decisionPoints = parsedDecisionPoints
        version = ver
        lock.unlock()

        var report = "loaded '\(ver)', \(parsedPlacements.count) placement(s)"
        if !notes.isEmpty {
            report += "\n  \u{26A0} " + notes.joined(separator: "\n  \u{26A0} ")
        }
        return PaywallApplyResult(applied: true, report: report)
    }

    /// A single variant's weight never needs to exceed the bucket space `pick` draws from
    /// (`HoldoutManager.bucketWith` returns `[0, 10_000)`) — clamping here also bounds the sum used
    /// in `pick`'s cumulative walk against `Int` overflow when many variants are configured.
    private static let maxVariantWeight = 10_000

    /// A weight this can't read degrades to `0`, which makes the variant permanently unselectable
    /// — invisible unless it is said out loud, because the placement's own "no valid variants
    /// left" note only fires when EVERY variant is dead. So every unreadable weight leaves a note.
    ///
    /// An EXPLICIT `"weight": 0` is not a mistake — it is how an operator turns one variant off —
    /// and stays note-free, as do the documented clamps (negative, `NaN`, absurdly large): those
    /// are wrong-VALUED numbers with defined, tested behavior, not wrong-TYPED fields.
    ///
    /// The `isJsonBoolean` guard is the same bridging hole `PurchaseRouter.web_shop.enabled` had,
    /// seen from the other side: `true as? Int` succeeds and returns `1`, so without it
    /// `{"weight": true}` would quietly become a real, selectable weight-1 variant in a live A/B
    /// test. See `JsonConfigValue.isJsonBoolean`.
    /// ═══ WHY THE EXTRACTION IS BORROWED BUT THE NOTE IS NOT ═══
    ///
    /// The int/double/string ladder — and the CFBoolean discriminator in front of it, without
    /// which `{"weight": true}` parses as a live weight of 1 — is `JsonConfigValue.numeric`'s
    /// job, and keeping a second copy here meant a fix to one never reached the other. This used
    /// its own copy until now.
    ///
    /// Its NOTE text is not borrowed. `numeric` writes "-> dropped", which is right for the
    /// fields it was built for: a malformed `period_hours` removes that grant line. A variant
    /// with an unusable weight is NOT dropped — it stays in the config and simply can never be
    /// selected, which is a different thing for an operator to read and act on. So `numeric`'s
    /// note goes into a discarded array and the accurate one is written here.
    private static func parseWeight(_ raw: Any?, label: String, notes: inout [String]) -> Int {
        guard let raw else {
            notes.append("\(label): no 'weight' -> 0, the variant can never be selected")
            return 0
        }
        var discardedNotes: [String] = []
        // `allowingNonPositive` because 0 and negatives are legitimate input here: they mean
        // "never select this variant", and `clampWeight` turns them into exactly that.
        guard let value = JsonConfigValue.numeric(
            raw,
            field: "weight",
            label: label,
            allowingNonPositive: true,
            notes: &discardedNotes
        ) else {
            notes.append("\(label): 'weight' is not a number -> 0, the variant can never be selected")
            return 0
        }
        return clampWeight(value)
    }

    /// Optional `DecisionPoint` override. `nil` (key absent) is the legitimate "use the default"
    /// case and leaves no note; every other failure — wrong type, empty string, unknown name —
    /// degrades to the same `.shopOpen` default but says so. A wrong-typed `decision_point` used
    /// to fail the `as? String` cast and fall through to the default in silence, sending a
    /// placement's decisions to a different decision point than the operator configured.
    private static func parseDecisionPoint(_ raw: Any?, placement: String, notes: inout [String]) -> DecisionPoint? {
        guard let raw else { return nil }
        guard let rawString = raw as? String, !rawString.isEmpty else {
            notes.append("\(placement): 'decision_point' is not a non-empty string -> using SHOP_OPEN")
            return nil
        }
        guard let decisionPoint = DecisionPoint(rawValue: rawString) else {
            notes.append("\(placement): decision_point '\(rawString)' is invalid -> using SHOP_OPEN")
            return nil
        }
        return decisionPoint
    }

    /// A product list's element-level drops point the SAFE way (a shorter list can only sell less)
    /// but must not be silent: a variant that quietly ships half the products it declares is an
    /// A/B result nobody can interpret. A `products` value that is not an array at all falls
    /// through to the caller's own "has no products -> skipped" note.
    private static func parseProducts(_ raw: Any?, label: String, notes: inout [String]) -> [String] {
        guard let rawArray = raw as? [Any] else { return [] }
        let usable = rawArray.compactMap { element -> String? in
            guard let product = element as? String, !product.isEmpty else { return nil }
            return product
        }
        if usable.count != rawArray.count {
            notes.append("\(label): \(rawArray.count - usable.count) of \(rawArray.count) 'products' entries are not a non-empty string -> dropped")
        }
        return usable
    }

    /// Non-finite (`NaN`/`inf`) or absurdly large weights come from operator error in remote config —
    /// degrade to a usable, clamped value rather than trapping in the host app, matching
    /// `HoldoutManager.isHoldout`'s own `NaN`-guard fail-safe (see its doc comment).
    /// ═══ WHY THE `isFinite` GUARD STAYS, INSTEAD OF DELEGATING WHOLESALE ═══
    ///
    /// The clamp-before-convert step is `JsonConfigValue.clamped`'s, so this is no longer a private
    /// copy of the rule that has been Critical three times here. But the NON-FINITE handling is
    /// deliberately still local, because the two disagree in a direction that matters:
    ///
    ///   value     this function      JsonConfigValue.clamped(0...maxVariantWeight)
    ///   +inf      0                  10000
    ///
    /// The shared helper treats `+inf` as "larger than any ceiling" and returns the upper bound,
    /// which is right for a quantity. A variant weight is not a quantity, it is a share of an A/B
    /// split — so the safe reading of an unintelligible weight is "never select this variant", not
    /// "select it above all others".
    ///
    /// This is reachable, not theoretical: `parseWeight` accepts numeric strings, and
    /// `Double("inf")` is `+infinity`. An operator typing `"weight": "inf"` would go from a variant
    /// that can never be chosen to one that dominates the split. `NaN` and `-inf` agree already;
    /// only `+inf` diverges.
    ///
    /// Same shape as `SubscriptionLifecycle.parseOpenCount`: share the clamp, keep the acceptance
    /// rule that is specific to what the number means. Locked by
    /// `test_anInfiniteWeightIsUnselectableRatherThanMaximal`.
    private static func clampWeight(_ value: Double) -> Int {
        guard value.isFinite else { return 0 }
        return JsonConfigValue.clamped(value, lowerBound: 0, upperBound: maxVariantWeight)
    }

    private func reject(_ reason: String) -> PaywallApplyResult {
        let report = "REJECTED paywall config (\(reason)) — keeping '\(activeVersion())'"
        return PaywallApplyResult(applied: false, report: report)
    }

    // MARK: - Decision

    /// - Returns: a paywall for this player, or `nil` when: config not loaded, placement not
    ///   declared, the player is in the paywall (== brain-wide) control group, or the brain
    ///   declines to offer right now (stuck, high purchase velocity, at the session/daily cap...).
    ///
    ///   `nil` means "the SDK has no opinion, run your old flow" — NEVER "show an empty
    ///   paywall." A zero-product variant is rejected at parse time specifically so this case
    ///   can never happen from a data-shape bug.
    ///
    ///   CONTRACT: each call is one bandit decision when the brain is active (a real decision-log
    ///   entry, real guardrail evaluation). Call this ONCE per offer opportunity — calling it
    ///   twice for the same screen is two decisions, skewing the decision log's propensity data.
    ///   For the same reason, do NOT call both `AdsBrain.shared.shouldOfferIap` and this method
    ///   for the same placement — that is a double decision for one opportunity.
    public func paywallFor(placement: String, playerId: String) async -> Paywall? {
        guard let variants = currentPlacements()[placement] else { return nil }

        if HoldoutManager.isHoldout(playerId) {
            incrementHoldoutSkips()
            if let eventSink = currentConfiguration()?.eventSink {
                let counterfactual = variantIdFor(placement: placement, playerId: playerId) ?? "unknown"
                await eventSink.send(SdkEvent(
                    eventName: "paywall_holdout_skip",
                    eventData: [
                        "placement": .string(placement),
                        "counterfactual_variant_id": .string(counterfactual),
                        "paywall_version": .string(activeVersion())
                    ],
                    userProperty: UserProperty(userId: playerId)
                ))
            }
            return nil
        }

        let decisionPoint = currentDecisionPoints()[placement] ?? .shopOpen
        guard await AdsBrain.shared.shouldOfferIap(decisionPoint: decisionPoint) else { return nil }

        guard let chosen = Self.pick(variants, playerId: playerId) else { return nil }
        return Paywall(
            placement: placement,
            variantId: chosen.id,
            products: chosen.products,
            highlight: chosen.highlight,
            version: activeVersion()
        )
    }

    /// This player's variant for `placement`, even if they're in the holdout group — used for
    /// analysis (the counterfactual in a holdout-skip event), never to serve a decision.
    ///
    /// ⚠️ DO NOT call this to decide what to show. Unlike `paywallFor`, it skips BOTH the holdout
    /// check and the `AdsBrain.shared.shouldOfferIap` brain gate — meaning it bypasses every
    /// guardrail behind that gate, including `ETH_FRUSTRATION_EXPLOIT` (stuck-player protection)
    /// and `IAP_VELOCITY_BREAKER` (purchase-velocity circuit breaker). Wiring this into a
    /// paywall-rendering path instead of `paywallFor` would show paywalls to the control group
    /// (destroying the holdout measurement) with zero guardrail protection. Use `paywallFor` for
    /// every real decision; this method exists only to compute the counterfactual for analysis.
    public func variantIdFor(placement: String, playerId: String) -> String? {
        guard let variants = currentPlacements()[placement] else { return nil }
        return Self.pick(variants, playerId: playerId)?.id
    }

    /// Weighted, **deterministic** pick keyed by `playerId`.
    ///
    /// Uses its OWN hash namespace (`hashNamespace`), independent of holdout and of brain
    /// rollout — sharing a namespace would put everyone in holdout into the exact same variant,
    /// fully correlating two experiments that need to run side by side.
    private static func pick(_ variants: [PaywallVariant], playerId: String) -> PaywallVariant? {
        let usable = variants.filter { $0.weight > 0 }
        guard !usable.isEmpty else { return nil }
        let total = usable.reduce(0) { $0 + $1.weight }
        guard total > 0 else { return nil }
        let bucket = HoldoutManager.bucketWith(hashNamespace, playerId) % total
        var accumulated = 0
        for variant in usable {
            accumulated += variant.weight
            if bucket < accumulated { return variant }
        }
        return usable.last
    }

    private func currentPlacements() -> [String: [PaywallVariant]] {
        lock.lock()
        defer { lock.unlock() }
        return placements
    }

    private func currentDecisionPoints() -> [String: DecisionPoint] {
        lock.lock()
        defer { lock.unlock() }
        return decisionPoints
    }

    private func incrementHoldoutSkips() {
        lock.lock()
        holdoutSkips += 1
        lock.unlock()
    }

    // MARK: - Measurement

    /// Reports that a paywall actually rendered on screen. Needed for the conversion
    /// denominator — a purchase count with no impression count to divide it by says nothing.
    ///
    /// ALSO tells the brain: an IAP offer just showed, no matter who decided to show it. Skip
    /// this call and the guardrail counters (session cap, cooldown, 24h cap) treat this
    /// impression as if it never happened, letting the NEXT offer through immediately — paywall
    /// would become a back door around the exact guardrails `paywallFor` just enforced. No-op
    /// when the brain is off.
    public func onPaywallShown(_ paywall: Paywall, playerId: String) async {
        AdsBrain.shared.onIapOfferShown()
        if let eventSink = currentConfiguration()?.eventSink {
            await eventSink.send(SdkEvent(
                eventName: "paywall_impression",
                eventData: [
                    "placement": .string(paywall.placement),
                    "variant_id": .string(paywall.variantId),
                    "product_count": .int(paywall.products.count),
                    "paywall_version": .string(paywall.version)
                ],
                userProperty: UserProperty(userId: playerId)
            ))
        }
    }
}
