import XCTest
@testable import InLoveIosCore

private struct MockStoreKitBackend: StoreKitBackendProtocol {
    let mockedOffers: [SubscriptionPlanOffer]
    let outcomeToReturn: StorePurchaseOutcome
    let restoredEntitlement: EntitlementSnapshot

    func loadOffers() async throws -> [SubscriptionPlanOffer] {
        mockedOffers
    }

    func purchase(productId: String) async throws -> StorePurchaseOutcome {
        outcomeToReturn
    }

    func restorePurchases() async throws -> EntitlementSnapshot {
        restoredEntitlement
    }

    func currentEntitlement() async -> EntitlementSnapshot {
        switch outcomeToReturn {
        case .purchased(let productId):
            return EntitlementSnapshot(
                isPro: true,
                activeProductId: productId,
                isLifetime: productId == InLoveSku.lifetime,
                expirationDateEpochMs: nil,
                lastVerifiedEpochMs: 1_700_000_000_000
            )
        default:
            return restoredEntitlement
        }
    }
}

final class MonetizationAndWidgetTests: XCTestCase {
    @MainActor
    func testSacredZoneAdSuppressionWarmupCooldownAndCap() {
        let startMs: Int64 = 1_000_000
        let ads = IosAdPlacementManager(sessionStartEpochMs: startMs, isPro: false, hasConsent: true, isCelebrationDay: false)

        XCTAssertTrue(ads.shouldShowBanner())
        XCTAssertEqual(ads.evaluateInterstitial(nowEpochMs: startMs + 10_000), .warmupActive)

        for zone in SacredZone.allCases {
            ads.enterSacredZone(zone)
            XCTAssertFalse(ads.shouldShowBanner())
            XCTAssertEqual(ads.evaluateInterstitial(nowEpochMs: startMs + 90_000), .sacredZone(zone))
            ads.exitSacredZone(zone)
        }

        XCTAssertEqual(ads.evaluateInterstitial(nowEpochMs: startMs + 60_000), .allowed)
        ads.recordInterstitialShown(nowEpochMs: startMs + 60_000)
        XCTAssertEqual(ads.evaluateInterstitial(nowEpochMs: startMs + 120_000), .cooldownActive)

        XCTAssertEqual(ads.evaluateInterstitial(nowEpochMs: startMs + 180_000), .allowed)
        ads.recordInterstitialShown(nowEpochMs: startMs + 180_000)
        ads.recordInterstitialShown(nowEpochMs: startMs + 310_000)
        XCTAssertEqual(ads.evaluateInterstitial(nowEpochMs: startMs + 500_000), .sessionCapReached)

        ads.updateProStatus(true)
        XCTAssertFalse(ads.shouldShowBanner())
        XCTAssertEqual(ads.evaluateInterstitial(nowEpochMs: startMs + 600_000), .proEntitlement)
        XCTAssertNil(ads.shouldSuggestSoftPaywall(freeAiUsedThisMonth: 5, secretNotesCount: 10))
    }

    @MainActor
    func testSoftPaywallUpsellTriggersForFreeUsers() {
        let ads = IosAdPlacementManager(isPro: false)
        XCTAssertEqual(ads.shouldSuggestSoftPaywall(freeAiUsedThisMonth: 2, secretNotesCount: 0), .aiGiftConciergeUnlimited)
        XCTAssertEqual(ads.shouldSuggestSoftPaywall(freeAiUsedThisMonth: 0, secretNotesCount: 5), .deepPartnerDossierUnlimited)
        XCTAssertNil(ads.shouldSuggestSoftPaywall(freeAiUsedThisMonth: 1, secretNotesCount: 4))
    }

    @MainActor
    func testStoreKitSkuMappingPurchaseAndRestore() async {
        XCTAssertEqual(InLoveSku.allSkus, ["vip_monthly", "vip_yearly", "vip_lifetime"])
        XCTAssertEqual(StoreKitEntitlementManager.mapSkuToPlanKey("vip_monthly"), "monthly")
        XCTAssertEqual(StoreKitEntitlementManager.mapSkuToPlanKey("vip_yearly"), "yearly")
        XCTAssertEqual(StoreKitEntitlementManager.mapSkuToPlanKey("vip_lifetime"), "lifetime")
        XCTAssertNil(StoreKitEntitlementManager.mapSkuToPlanKey("unknown_sku"))

        let backend = MockStoreKitBackend(
            mockedOffers: SubscriptionPlanOffer.defaultFallbackOffers(),
            outcomeToReturn: .purchased(productId: InLoveSku.yearly),
            restoredEntitlement: EntitlementSnapshot(isPro: true, activeProductId: InLoveSku.lifetime, isLifetime: true)
        )
        let manager = StoreKitEntitlementManager(backend: backend, observeTransactions: false)
        await manager.refreshAll()
        XCTAssertEqual(manager.offers.first(where: { $0.productId == InLoveSku.yearly })?.trialDays, 3)

        let outcome = await manager.purchase(productId: InLoveSku.yearly)
        XCTAssertEqual(outcome, .purchased(productId: InLoveSku.yearly))
        XCTAssertTrue(manager.isPro)
        XCTAssertTrue(manager.supportsFeature(.adFree))

        let restored = await manager.restorePurchases()
        XCTAssertTrue(restored.isPro)
        XCTAssertEqual(restored.activeProductId, InLoveSku.lifetime)
        XCTAssertTrue(restored.isLifetime)
    }

    func testWidgetSnapshotJsonRoundtripAndRedaction() throws {
        let snapshot = CoupleWidgetSnapshot(
            version: 1,
            coupleTitle: "Hoàng & Mai",
            partner1Name: "Hoàng",
            partner2Name: "Mai",
            loveDays: 520,
            startDateIso: "2025-06-15",
            nextOccasionTitle: "Lễ Thất Tịch (7/7 ÂL)",
            nextOccasionDaysRemaining: 7,
            redacted: true,
            updatedAtEpochMs: 1_700_000_000_000
        )

        let json = try WidgetSnapshotStorage.encodeJSON(snapshot)
        let decoded = try WidgetSnapshotStorage.decodeJSON(json)
        XCTAssertEqual(decoded, snapshot)

        let redacted = decoded.withPrivacyRedaction(isEnglish: true)
        XCTAssertEqual(redacted.partner1Name, "You")
        XCTAssertEqual(redacted.partner2Name, "Partner")
        XCTAssertEqual(redacted.nextOccasionTitle, "Upcoming special day")
    }
}
