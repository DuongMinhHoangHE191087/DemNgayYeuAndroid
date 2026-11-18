import XCTest
@testable import InLoveIosCore

private actor MockCloudDossierSyncer: CloudDossierBackupSyncing {
    private var storedByUid: [String: PartnerDossier] = [:]
    private(set) var saveCallCount = 0

    init(initialRemote: PartnerDossier? = nil, uid: String = "uid-1") {
        if let initialRemote {
            storedByUid[uid] = initialRemote
        }
    }

    func savePrivateDossierBackup(_ dossier: PartnerDossier, uid: String) async throws {
        saveCallCount += 1
        storedByUid[uid] = dossier
    }

    func loadPrivateDossierBackup(uid: String) async throws -> PartnerDossier? {
        storedByUid[uid]
    }
}

final class OnlineSyncAndPricingTests: XCTestCase {
    func testCoupleCodeParserExtractsNormalizedCodes() {
        XCTAssertEqual(CoupleCodeParser.extractCode("https://inlove.app/pair?code=love-9966"), "LOVE-9966")
        XCTAssertEqual(CoupleCodeParser.extractCode("inlove://pair/ab_42"), "AB_42")
        XCTAssertEqual(CoupleCodeParser.extractCode("code=mint77"), "MINT77")
        XCTAssertEqual(CoupleCodeParser.extractCode("  vip_01  "), "VIP_01")
        XCTAssertEqual(CoupleCodeParser.extractCode(""), "")
    }

    func testDossierMergePolicyPrefersNewerScalarsAndUnionsListsById() {
        let localNote = SecretHintNote(
            id: "n1",
            note: "Matcha bowl",
            isPurchased: true,
            createdAtEpochMs: 1_000
        )
        let remoteNoteSameId = SecretHintNote(
            id: "n1",
            note: "Matcha bowl",
            isPurchased: false,
            createdAtEpochMs: 900
        )
        let remoteOnlyNote = SecretHintNote(
            id: "n2",
            note: "Silk scarf",
            isPurchased: false,
            createdAtEpochMs: 2_000
        )
        let localGift = GivenGiftRecord(
            id: "g1",
            title: "Flower Bouquet",
            occasion: "20/10",
            givenDateIso: "2026-10-20"
        )
        let remoteGift = GivenGiftRecord(
            id: "g2",
            title: "Lip Balm",
            occasion: "Valentine",
            givenDateIso: "2026-02-14"
        )

        let local = PartnerDossier(
            scopeId: "couple_1",
            nickname: "Bé Mây",
            freeformLikes: "Loves iced matcha",
            secretSurpriseNotes: [localNote],
            giftHistory: [localGift],
            budgetMaxVnd: 800_000,
            updatedAtEpochMs: 5_000
        )
        let remote = PartnerDossier(
            scopeId: "couple_1",
            nickname: "Old Nickname",
            freeformLikes: "Old bio",
            secretSurpriseNotes: [remoteNoteSameId, remoteOnlyNote],
            giftHistory: [remoteGift],
            budgetMaxVnd: 500_000,
            updatedAtEpochMs: 3_000
        )

        let merged = DossierMergePolicy.merge(local: local, remote: remote)
        XCTAssertEqual(merged.nickname, "Bé Mây")
        XCTAssertEqual(merged.budgetMaxVnd, 800_000)
        XCTAssertEqual(merged.updatedAtEpochMs, 5_000)
        XCTAssertEqual(merged.secretSurpriseNotes.map(\.id), ["n2", "n1"])
        XCTAssertTrue(merged.secretSurpriseNotes.first(where: { $0.id == "n1" })?.isPurchased == true)
        XCTAssertEqual(Set(merged.giftHistory.map(\.id)), ["g1", "g2"])
    }

    @MainActor
    func testOnlineCoupleCoordinatorInviteOfflineQueueReconnectAndTwoWayUnpair() async {
        let remoteNote = SecretHintNote(id: "remote-1", note: "Scented candle", createdAtEpochMs: 2_000)
        let remoteDossier = PartnerDossier(
            scopeId: "ios_local",
            nickname: "Mai",
            secretSurpriseNotes: [remoteNote],
            updatedAtEpochMs: 2_000
        )
        let cloudSyncer = MockCloudDossierSyncer(initialRemote: remoteDossier, uid: "uid-1")
        let coordinator = IosOnlineCoupleCoordinator(
            initialState: CouplePairingState(ownCoupleCode: "HOANG-01"),
            defaults: nil,
            cloudSyncer: cloudSyncer
        )

        XCTAssertEqual(coordinator.state.phase, .guestLocal)

        coordinator.signInAccount(uid: "uid-1", displayName: "Hoàng", isEnglish: false)
        XCTAssertEqual(coordinator.state.phase, .signedInSolo)

        XCTAssertTrue(coordinator.sendInvite(rawCodeOrUrl: "https://inlove.app/pair?code=mai-02", senderName: "Hoàng", isEnglish: false))
        XCTAssertEqual(coordinator.state.phase, .inviteOutgoing)
        XCTAssertEqual(coordinator.state.outgoingInvite?.toCode, "MAI-02")

        coordinator.cancelOutgoingInvite(isEnglish: false)
        XCTAssertEqual(coordinator.state.phase, .signedInSolo)

        let incoming = CoupleInvite(
            fromUid: "uid-2",
            fromName: "Mai",
            fromCode: "MAI-02",
            toCode: "HOANG-01",
            createdAtEpochMs: 1_000
        )
        coordinator.receiveIncomingInvite(incoming, isEnglish: true)
        XCTAssertEqual(coordinator.state.phase, .inviteIncoming)

        coordinator.acceptIncomingInvite(isEnglish: true, nowEpochMs: 3_000)
        XCTAssertEqual(coordinator.state.phase, .coupledSynced)
        XCTAssertEqual(coordinator.state.partnerName, "Mai")

        let offlineDropResult = await coordinator.handleNetworkReachabilityChanged(
            false,
            localDossier: PartnerDossier(scopeId: "ios_local"),
            isEnglish: true
        )
        XCTAssertNil(offlineDropResult)
        XCTAssertEqual(coordinator.state.phase, .coupledOfflineQueue)

        let localEdit = PartnerDossier(
            scopeId: "ios_local",
            nickname: "Bé Mai",
            secretSurpriseNotes: [SecretHintNote(id: "local-1", note: "Ceramic mug", createdAtEpochMs: 4_000)],
            updatedAtEpochMs: 4_000
        )
        await coordinator.syncLocalDossierMutation(localEdit, nowEpochMs: 4_000)
        XCTAssertEqual(coordinator.state.pendingSyncCount, 1)

        let mergedOnReconnect = await coordinator.handleNetworkReachabilityChanged(
            true,
            localDossier: localEdit,
            isEnglish: true,
            nowEpochMs: 5_000
        )
        XCTAssertEqual(coordinator.state.phase, .coupledSynced)
        XCTAssertEqual(coordinator.state.pendingSyncCount, 0)
        XCTAssertEqual(mergedOnReconnect?.nickname, "Bé Mai")
        XCTAssertEqual(Set(mergedOnReconnect?.secretSurpriseNotes.map(\.id) ?? []), ["local-1", "remote-1"])

        coordinator.requestTwoWayUnpair(isEnglish: true)
        XCTAssertEqual(coordinator.state.phase, .breakupPending)
        coordinator.cancelUnpairRequest(isEnglish: true)
        XCTAssertEqual(coordinator.state.phase, .coupledSynced)

        coordinator.requestTwoWayUnpair(isEnglish: true)
        coordinator.completeTwoWayUnpair(isEnglish: true)
        XCTAssertEqual(coordinator.state.phase, .signedInSolo)
        XCTAssertNil(coordinator.state.partnerName)
    }

    func testPricingCatalogAndFourChapterGuideMatchContract() {
        let offers = SubscriptionPlanOffer.defaultFallbackOffers()
        XCTAssertEqual(Set(offers.map(\.productId)), ["vip_monthly", "vip_yearly", "vip_lifetime"])

        let monthly = offers.first(where: { $0.productId == InLoveSku.monthly })
        let yearly = offers.first(where: { $0.productId == InLoveSku.yearly })
        let lifetime = offers.first(where: { $0.productId == InLoveSku.lifetime })

        XCTAssertEqual(monthly?.displayPrice, "$2.99")
        XCTAssertEqual(yearly?.displayPrice, "$17.99")
        XCTAssertEqual(yearly?.savingsPercent, 57)
        XCTAssertEqual(yearly?.trialDays, 3)
        XCTAssertEqual(lifetime?.displayPrice, "$39.99")
        XCTAssertEqual(lifetime?.isLifetime, true)

        let chapters = InLoveGuideCatalog.chapters
        XCTAssertEqual(chapters.count, 4)
        for chapter in chapters {
            XCTAssertEqual(chapter.steps.map(\.numberLabel), ["01", "02", "03"])
        }
    }
}
