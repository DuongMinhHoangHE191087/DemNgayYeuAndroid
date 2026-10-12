import XCTest
@testable import MonetizationBrainCore

final class PaywallManagerTests: XCTestCase {
    override func setUp() {
        super.setUp()
        PaywallManager.shared.resetForTest()
        AdsBrain.shared.resetForTest()
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
    }

    override func tearDown() {
        PaywallManager.shared.resetForTest()
        AdsBrain.shared.resetForTest()
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
        super.tearDown()
    }

    private let wellFormedConfig = """
    {
        "version": "v1",
        "placements": {
            "shop_open": {
                "decision_point": "SHOP_OPEN",
                "variants": [
                    {"id": "a", "weight": 70, "products": ["gem_pack_1", "gem_pack_2"], "highlight": "gem_pack_1"},
                    {"id": "b", "weight": 30, "products": ["gem_pack_3"]}
                ]
            }
        }
    }
    """

    func test_activeVersionDefaultsToNotLoaded() {
        XCTAssertEqual(PaywallManager.shared.activeVersion(), "not loaded")
    }

    func test_applyWellFormedConfigSucceeds() {
        let result = PaywallManager.shared.apply(wellFormedConfig)
        XCTAssertTrue(result.applied)
        XCTAssertEqual(PaywallManager.shared.activeVersion(), "v1")
    }

    func test_applyNilConfigIsRejectedAndKeepsExistingConfig() async {
        HoldoutManager.holdoutFraction = 0.0 // nobody is holdout, so paywallFor can resolve deterministically
        _ = PaywallManager.shared.apply(wellFormedConfig)
        let result = PaywallManager.shared.apply(nil)
        XCTAssertFalse(result.applied)
        XCTAssertEqual(PaywallManager.shared.activeVersion(), "v1")
        // Not just activeVersion() — paywallFor itself must still serve the FIRST (good) config,
        // proving reject(_:) never touched `placements`/`decisionPoints`, not just `version`.
        let paywall = await PaywallManager.shared.paywallFor(placement: "shop_open", playerId: "p1")
        XCTAssertEqual(paywall?.version, "v1")
    }

    func test_applyBlankConfigIsRejected() {
        let result = PaywallManager.shared.apply("   ")
        XCTAssertFalse(result.applied)
    }

    func test_applyMissingVersionIsRejectedAndKeepsExistingConfig() {
        _ = PaywallManager.shared.apply(wellFormedConfig)
        let result = PaywallManager.shared.apply("""
        {"placements": {}}
        """)
        XCTAssertFalse(result.applied)
        XCTAssertEqual(PaywallManager.shared.activeVersion(), "v1")
    }

    func test_applyMissingPlacementsIsRejected() {
        let result = PaywallManager.shared.apply("""
        {"version": "v2"}
        """)
        XCTAssertFalse(result.applied)
    }

    func test_applyInvalidDecisionPointKeepsPlacementWithDefaultShopOpen() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "decision_point": "NOT_A_REAL_DECISION_POINT",
                    "variants": [{"id": "a", "weight": 100, "products": ["gem_pack_1"]}]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertNotNil(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"))
        XCTAssertTrue(result.report.contains("NOT_A_REAL_DECISION_POINT"))
    }

    func test_applyDropsVariantWithNoProducts() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "empty", "weight": 50, "products": []},
                        {"id": "real", "weight": 50, "products": ["gem_pack_1"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("empty"))
        // Only "real" should ever be selectable — verified structurally in Task 2's pick() tests;
        // here just confirm the placement still parsed with at least one usable variant.
        XCTAssertNotNil(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"))
    }

    func test_applyDropsPlacementWithNoPositiveWeightVariantsLeft() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "dead_placement": {
                    "variants": [{"id": "zero", "weight": 0, "products": ["gem_pack_1"]}]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied) // the JSON itself is well-formed
        XCTAssertNil(PaywallManager.shared.variantIdFor(placement: "dead_placement", playerId: "p1"))
    }

    func test_applyWeightAcceptsNumericStringAndClampsNegativeToZero() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "string_weight", "weight": "40", "products": ["gem_pack_1"]},
                        {"id": "negative_weight", "weight": -5, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        // "negative_weight" clamps to 0 (never selectable), "string_weight" parses as 40 (is)
        // — exact selection behavior is Task 2's pick() concern; here just confirm the config
        // parsed without rejecting the whole placement.
        XCTAssertNotNil(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"))
    }

    /// Weight parsing moved onto `JsonConfigValue.numeric`, and that changed one behaviour for
    /// the better: a numeric-looking string that is not finite used to return 0 SILENTLY, so an
    /// operator who typed "Infinity" saw a variant that never appeared and a report that said
    /// nothing was wrong. It is now noted, like every other unusable value.
    func test_anUnusableWeightIsNotedRatherThanSilentlyZeroed() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "infinite", "weight": "Infinity", "products": ["gem_pack_1"]},
                        {"id": "real", "weight": 10, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)

        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("'weight' is not a number"), result.report)
        XCTAssertTrue(result.report.contains("can never be selected"), result.report)
        // The placement survives on the variant that IS usable — one bad weight does not cost
        // the whole placement.
        XCTAssertEqual(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"), "real")
    }

    /// The note a variant with no usable weight gets must say what actually happens to it. Reusing
    /// `JsonConfigValue.numeric`'s own wording would have said "-> dropped", which is true for the
    /// fields that helper was written for and false here: the variant stays in the config.
    func test_theNoteSaysTheVariantSurvivesUnselectableNotThatItWasDropped() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "bad", "products": ["gem_pack_1"]},
                        {"id": "real", "weight": 10, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)

        XCTAssertTrue(result.report.contains("no 'weight' -> 0, the variant can never be selected"), result.report)
        XCTAssertFalse(result.report.contains("-> dropped"), result.report)
    }

    func test_resetForTestResetsEverything() {
        _ = PaywallManager.shared.apply(wellFormedConfig)
        PaywallManager.shared.resetForTest()
        XCTAssertEqual(PaywallManager.shared.activeVersion(), "not loaded")
        XCTAssertEqual(PaywallManager.shared.holdoutSkipCount(), 0)
        XCTAssertNil(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"))
    }

    // MARK: - Task 2: decision logic + measurement

    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    private func makeAdsBrainConfiguration(playerId: String, eventSink: EventSink) -> AdsBrain.Configuration {
        AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: eventSink,
            playerId: playerId,
            decisionEngine: DecisionEngine(store: InMemoryBrainStore(), eventSink: eventSink)
        )
    }

    /// In-memory `BrainKeyValueStore`, matching `DecisionEngineTests`' own fixture. `DecisionEngine`'s
    /// default store is a REAL `UserDefaultsBrainStore()` — its bandit posterior persists on disk
    /// and is shared by every test in the process that also uses the default store, so a
    /// `DecisionEngine(eventSink:)` built with defaults is not isolated between test methods, test
    /// files, or even separate `swift test` invocations. Tests below that need an independent,
    /// from-scratch Beta(1,1) prior use this instead.
    private final class InMemoryBrainStore: BrainKeyValueStore, @unchecked Sendable {
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? { values[key] }
        func setString(_ value: String, forKey key: String) { values[key] = value }
        func removeObject(forKey key: String) { values.removeValue(forKey: key) }
    }

    private func applyFreshOnePlacementConfig() {
        _ = PaywallManager.shared.apply(wellFormedConfig)
    }

    /// `shouldOfferIap`'s bandit decision (Thompson Sampling over fresh Beta(1,1) priors on both
    /// arms) is genuinely random — never assert what a SINGLE decision chooses (the same rule
    /// `AdsBrainTests` already follows: "this test doesn't assert WHICH action was chosen").
    ///
    /// Each attempt here is a fully INDEPENDENT decision — a brand-new `AdsBrain` session backed
    /// by a fresh, isolated `InMemoryBrainStore` (never touching the real, process-shared
    /// `UserDefaults` domain other tests' default-store `DecisionEngine`s persist into) — so no
    /// attempt's outcome is correlated with, or reinforced by, any other. This retries to REACH
    /// the "brain chose to offer" branch at least once (~50% odds per attempt, so `maxAttempts`
    /// consecutive misses has vanishing probability), then hands back that one result for the
    /// caller to assert on — it does not predict or require any single decision's outcome.
    private func firstRealPaywallOverIndependentDecisions(
        placement: String,
        playerId: String,
        sink: EventSink,
        maxAttempts: Int = 25
    ) async -> Paywall? {
        for _ in 0..<maxAttempts {
            await AdsBrain.shared.configure(AdsBrain.Configuration(
                remoteConfig: StaticRemoteConfigProvider(),
                eventSink: sink,
                playerId: playerId,
                decisionEngine: DecisionEngine(store: InMemoryBrainStore(), eventSink: sink)
            ))
            AdsBrain.shared.enabled = true
            await AdsBrain.shared.onSessionStart()
            if let result = await PaywallManager.shared.paywallFor(placement: placement, playerId: playerId) {
                return result
            }
        }
        return nil
    }

    func test_paywallForReturnsNilForUndeclaredPlacement() async {
        applyFreshOnePlacementConfig()
        let sink = FakeEventSink()
        PaywallManager.shared.configure(.init(eventSink: sink))

        let result = await PaywallManager.shared.paywallFor(placement: "nonexistent", playerId: "p1")

        XCTAssertNil(result)
        // Spec §6: "nil, no event, no brain call". AdsBrain.shared is left unconfigured (as
        // `resetForTest()` in setUp leaves it) for this test specifically so that `currentState()`
        // staying nil also shows no session/decision machinery was ever touched — the undeclared-
        // placement guard returns before the holdout check or the brain gate is ever reached.
        let sent = await sink.sentEvents
        XCTAssertTrue(sent.isEmpty, "expected no events for an undeclared placement, got \(sent)")
        XCTAssertNil(AdsBrain.shared.currentState())
    }

    func test_paywallForReturnsNilAndSkipsForHoldoutPlayerWithoutConsumingABanditDecision() async {
        applyFreshOnePlacementConfig()
        HoldoutManager.holdoutFraction = 1.0 // everyone is holdout
        let sink = FakeEventSink()
        PaywallManager.shared.configure(.init(eventSink: sink))
        await AdsBrain.shared.configure(makeAdsBrainConfiguration(playerId: "p1", eventSink: sink))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let result = await PaywallManager.shared.paywallFor(placement: "shop_open", playerId: "p1")

        XCTAssertNil(result)
        XCTAssertEqual(PaywallManager.shared.holdoutSkipCount(), 1)
        let sent = await sink.sentEvents
        // No brain_decision anywhere in this list — proves shouldOfferIap's DecisionEngine.decide
        // was never reached for a holdout player (the holdout check runs strictly first).
        XCTAssertFalse(sent.contains { $0.eventName == "brain_decision" })
        XCTAssertTrue(sent.contains { $0.eventName == "paywall_holdout_skip" })
    }

    func test_paywallForHoldoutSkipEventCarriesCorrectCounterfactualVariantAndFields() async {
        applyFreshOnePlacementConfig()
        HoldoutManager.holdoutFraction = 1.0
        let sink = FakeEventSink()
        PaywallManager.shared.configure(.init(eventSink: sink))
        await AdsBrain.shared.configure(makeAdsBrainConfiguration(playerId: "p1", eventSink: sink))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        _ = await PaywallManager.shared.paywallFor(placement: "shop_open", playerId: "p1")

        let expectedCounterfactual = PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1")
        let sent = await sink.sentEvents
        let skipEvent = sent.first { $0.eventName == "paywall_holdout_skip" }
        XCTAssertEqual(skipEvent?.eventData["placement"], .string("shop_open"))
        XCTAssertEqual(skipEvent?.eventData["counterfactual_variant_id"], .string(expectedCounterfactual ?? "unknown"))
        XCTAssertEqual(skipEvent?.eventData["paywall_version"], .string("v1"))
        XCTAssertEqual(skipEvent?.userProperty.userId, "p1")
    }

    func test_paywallForReturnsRealPaywallForNonHoldoutActivePlayer() async {
        applyFreshOnePlacementConfig()
        HoldoutManager.holdoutFraction = 0.0 // nobody is holdout
        let sink = FakeEventSink()
        PaywallManager.shared.configure(.init(eventSink: sink))

        guard let result = await firstRealPaywallOverIndependentDecisions(
            placement: "shop_open", playerId: "p1", sink: sink
        ) else {
            return XCTFail("brain never chose to offer across many independent decisions")
        }

        XCTAssertEqual(result.placement, "shop_open")
        XCTAssertEqual(result.version, "v1")
        let expectedVariantId = PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1")
        XCTAssertEqual(result.variantId, expectedVariantId)
        // Spec §6: "products matches the chosen variant's declared list and order" — not just the
        // variant id. `wellFormedConfig`'s two variants have distinct products/highlight, so this
        // also proves `Paywall` isn't accidentally wired to the wrong variant's fields.
        switch expectedVariantId {
        case "a":
            XCTAssertEqual(result.products, ["gem_pack_1", "gem_pack_2"])
            XCTAssertEqual(result.highlight, "gem_pack_1")
        case "b":
            XCTAssertEqual(result.products, ["gem_pack_3"])
            XCTAssertNil(result.highlight)
        default:
            XCTFail("unexpected variantId \(expectedVariantId ?? "nil")")
        }
    }

    func test_variantIdForIsDeterministicForSamePlayerAndPlacement() {
        applyFreshOnePlacementConfig()
        let first = PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1")
        let second = PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1")
        XCTAssertNotNil(first)
        XCTAssertEqual(first, second)
    }

    func test_pickNeverSelectsAZeroWeightVariant() {
        _ = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "zero", "weight": 0, "products": ["gem_pack_1"]},
                        {"id": "real", "weight": 100, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        // Try several distinct playerIds — with only one positive-weight variant, every one of
        // them must always resolve to "real", never "zero".
        for playerId in ["p1", "p2", "p3", "p4", "p5"] {
            XCTAssertEqual(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: playerId), "real")
        }
    }

    func test_onPaywallShownIncrementsAdsBrainIapOfferCounterAndEmitsImpressionEvent() async {
        applyFreshOnePlacementConfig()
        HoldoutManager.holdoutFraction = 0.0
        let sink = FakeEventSink()
        PaywallManager.shared.configure(.init(eventSink: sink))
        guard let paywall = await firstRealPaywallOverIndependentDecisions(
            placement: "shop_open", playerId: "p1", sink: sink
        ) else {
            return XCTFail("brain never chose to offer across many independent decisions")
        }

        await PaywallManager.shared.onPaywallShown(paywall, playerId: "p1")

        XCTAssertEqual(AdsBrain.shared.currentState()?.iapOffersThisSession, 1)
        let sent = await sink.sentEvents
        let impression = sent.first { $0.eventName == "paywall_impression" }
        XCTAssertEqual(impression?.eventData["placement"], .string("shop_open"))
        XCTAssertEqual(impression?.eventData["variant_id"], .string(paywall.variantId))
        XCTAssertEqual(impression?.eventData["product_count"], .int(paywall.products.count))
        XCTAssertEqual(impression?.eventData["paywall_version"], .string("v1"))
        XCTAssertEqual(impression?.userProperty.userId, "p1")
    }

    // MARK: - Final review fixes: parseWeight crash guard (Critical)

    /// `Int(Double)` traps on non-finite/out-of-range values — `parseWeight` must clamp instead.
    /// Each malformed variant is paired with a normal one so the placement stays usable regardless
    /// of whether the malformed weight clamps to a large-but-finite value or to `0`; the crash-
    /// regression proof is simply that `apply(_:)` returns at all instead of trapping the process.
    func test_applyClampsAbsurdlyLargeNumericWeightWithoutCrashing() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "huge", "weight": 1e30, "products": ["gem_pack_1"]},
                        {"id": "normal", "weight": 10, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertNotNil(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"))
    }

    /// Locks the one place `clampWeight` deliberately does NOT delegate to
    /// `JsonConfigValue.clamped`. The shared helper returns the UPPER bound for `+inf` — correct
    /// for a quantity, wrong for a share of an A/B split. Reachable via a numeric string, since
    /// `Double("inf")` is `+infinity`.
    ///
    /// Without the local `isFinite` guard this variant would go from unselectable to weight 10000,
    /// dominating the split on an operator typo. Every other test here would still pass.
    func test_anInfiniteWeightIsUnselectableRatherThanMaximal() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v_inf",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "inf_weight", "weight": "inf", "products": ["gem_pack_1"]},
                        {"id": "normal", "weight": 10, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        // With weight 0 the infinite variant can never be drawn; every player lands on "normal".
        for player in ["p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8"] {
            XCTAssertEqual(
                PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: player),
                "normal",
                "an unintelligible weight must never win the split"
            )
        }
    }

    func test_applyClampsNaNStringWeightWithoutCrashing() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "nan_weight", "weight": "nan", "products": ["gem_pack_1"]},
                        {"id": "normal", "weight": 10, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertNotNil(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"))
    }

    func test_applyClampsInfStringWeightWithoutCrashing() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "inf_weight", "weight": "inf", "products": ["gem_pack_1"]},
                        {"id": "normal", "weight": 10, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertNotNil(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"))
    }

    // MARK: - Final review fixes: total weight can exceed the 10,000-bucket space (Important)

    func test_applyTotalWeightExceedingBucketSpaceAddsNoteButKeepsPlacementUsable() async {
        HoldoutManager.holdoutFraction = 0.0 // nobody is holdout, so paywallFor resolves deterministically
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "a", "weight": 12500, "products": ["gem_pack_1"]},
                        {"id": "b", "weight": 12500, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("exceeds the 10000-bucket space"), result.report)
        // AdsBrain.shared is left unconfigured -> shouldOfferIap defaults to true, so this proves
        // the placement stays usable end-to-end, not just at the variantIdFor/parse level.
        let paywall = await PaywallManager.shared.paywallFor(placement: "shop_open", playerId: "p1")
        XCTAssertNotNil(paywall)
    }

    // MARK: - Final review fixes: silent config-drop paths now leave a note (Important)

    func test_applyPlacementThatIsNotAnObjectIsSkippedWithNoteAndOtherPlacementsStillParse() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "broken": "not an object",
                "shop_open": {
                    "variants": [{"id": "a", "weight": 100, "products": ["gem_pack_1"]}]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("broken"), result.report)
        XCTAssertTrue(result.report.contains("not an object"), result.report)
        XCTAssertNil(PaywallManager.shared.variantIdFor(placement: "broken", playerId: "p1"))
        XCTAssertNotNil(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"))
    }

    func test_applyVariantsArrayWithStrayNonObjectElementKeepsValidVariantWithNote() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "a", "weight": 100, "products": ["gem_pack_1"]},
                        "not_an_object"
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("a variant entry is not an object"), result.report)
        // Only "a" is a valid variant here, so this is deterministic regardless of playerId hash.
        XCTAssertEqual(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"), "a")
    }

    func test_applyVariantMissingIdIsDroppedWithNoteWhileOthersStillParse() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"weight": 50, "products": ["gem_pack_1"]},
                        {"id": "b", "weight": 50, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("missing a valid 'id'"), result.report)
        // Only "b" is a valid variant here, so this is deterministic regardless of playerId hash.
        XCTAssertEqual(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"), "b")
    }

    // MARK: - Hardening: wrong-typed optional fields degrade WITH a note, never silently

    /// `decision_point` was read as `block["decision_point"] as? String` — a non-string value
    /// (`5`, `true`, an object) failed the cast, fell straight through to the `.shopOpen` default,
    /// and left NOTHING in the report. The placement then asks the brain at a different decision
    /// point than the operator configured, invisibly. A wrong-typed field degrades to the safe
    /// default AND appends a note (design spec §3.8).
    func test_applyDecisionPointPresentButNotAStringLeavesNoteAndKeepsPlacement() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "decision_point": 5,
                    "variants": [{"id": "a", "weight": 100, "products": ["gem_pack_1"]}]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("decision_point"), result.report)
        XCTAssertEqual(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"), "a")
    }

    /// Companion: a valid `decision_point` leaves no note — the fix must not warn on the happy path.
    func test_applyValidDecisionPointLeavesNoNote() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "decision_point": "SHOP_OPEN",
                    "variants": [{"id": "a", "weight": 100, "products": ["gem_pack_1"]}]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.contains("decision_point"), result.report)
    }

    /// `parseWeight` returned `0` for anything it could not read, and a weight-0 variant is
    /// silently never selectable. With at least one OTHER usable variant in the placement, the
    /// "has no valid variants left" note never fires either — so a wrong-typed weight erased a
    /// variant from the A/B test with no diagnostic at all.
    func test_applyVariantWeightPresentButNotANumberLeavesNote() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "bad_weight", "weight": {}, "products": ["gem_pack_1"]},
                        {"id": "normal", "weight": 10, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("bad_weight"), result.report)
        XCTAssertEqual(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"), "normal")
    }

    /// The `NSNumber`/`Bool` bridging hole's mirror image: `true as? Int` SUCCEEDS and returns `1`,
    /// so `{"weight": true}` used to become a real, selectable weight-1 variant. Same discriminator
    /// (`JsonConfigValue.isJsonBoolean`) closes both directions.
    func test_applyVariantWeightAsJsonBooleanIsNotTreatedAsOneAndLeavesNote() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "bool_weight", "weight": true, "products": ["gem_pack_1"]},
                        {"id": "normal", "weight": 10, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("bool_weight"), result.report)
        // Every playerId must land on "normal": "bool_weight" must carry no weight at all.
        for playerId in ["p1", "p2", "p3", "p4", "p5"] {
            XCTAssertEqual(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: playerId), "normal")
        }
    }

    /// A variant declared with no `weight` key at all can never be selected — a declared-but-dead
    /// variant is an operator mistake worth naming, unlike an EXPLICIT `"weight": 0`, which is a
    /// deliberate "turn this variant off" and stays note-free (covered by
    /// `test_applyDropsPlacementWithNoPositiveWeightVariantsLeft`).
    func test_applyVariantWithNoWeightKeyLeavesNote() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [
                        {"id": "no_weight", "products": ["gem_pack_1"]},
                        {"id": "normal", "weight": 10, "products": ["gem_pack_2"]}
                    ]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("no_weight"), result.report)
        XCTAssertEqual(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"), "normal")
    }

    /// Element-level drops inside `products` were silent: `["gem_pack_1", 7]` shipped a
    /// one-product paywall and said nothing. The surviving products still apply (a bad element
    /// degrades itself, not the variant), but the operator gets told.
    func test_applyProductsWithNonStringElementsDropsThemAndLeavesNote() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [{"id": "a", "weight": 100, "products": ["gem_pack_1", 7, ""]}]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("products"), result.report)
        XCTAssertEqual(PaywallManager.shared.variantIdFor(placement: "shop_open", playerId: "p1"), "a")
    }

    /// Companion: an all-strings product list leaves no note.
    func test_applyWellFormedProductsLeaveNoNote() {
        let result = PaywallManager.shared.apply("""
        {
            "version": "v1",
            "placements": {
                "shop_open": {
                    "variants": [{"id": "a", "weight": 100, "products": ["gem_pack_1", "gem_pack_2"]}]
                }
            }
        }
        """)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.contains("products"), result.report)
    }
}
