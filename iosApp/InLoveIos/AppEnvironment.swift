import Combine
import Foundation
import InLoveIosCore
import SwiftUI
#if canImport(WidgetKit)
import WidgetKit
#endif

public enum InLoveTab: String, CaseIterable, Hashable {
    case home
    case calendar
    case gifts
    case memories
    case settings
}

public struct LocalMemoryItem: Identifiable, Codable, Equatable, Sendable {
    public let id: String
    public var title: String
    public var dateText: String
    public var location: String
    public var note: String
    public var isFavorite: Bool
    public var imageFilename: String?
    public var createdAtEpochMs: Int64

    public init(
        id: String = UUID().uuidString,
        title: String,
        dateText: String,
        location: String = "",
        note: String = "",
        isFavorite: Bool = false,
        imageFilename: String? = nil,
        createdAtEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.title = title
        self.dateText = dateText
        self.location = location
        self.note = note
        self.isFavorite = isFavorite
        self.imageFilename = imageFilename
        self.createdAtEpochMs = createdAtEpochMs
    }
}

@MainActor
public final class AppEnvironment: ObservableObject {
    @Published public var selectedTab: InLoveTab = .home
    @Published public var isEnglish: Bool = false { didSet { persistSettings(); refreshComputedState() } }
    @Published public var coupleTitle: String = "InLove" { didSet { persistSettings(); syncWidgetSnapshot() } }
    @Published public var partner1Name: String = "Hoàng" { didSet { persistSettings(); syncWidgetSnapshot() } }
    @Published public var partner2Name: String = "Mai" { didSet { persistSettings(); syncWidgetSnapshot() } }
    @Published public var startDateIso: String = "2024-02-14" { didSet { persistSettings(); refreshComputedState() } }
    @Published public var partnerBirthdayIso: String = "2001-08-18" { didSet { persistSettings(); refreshComputedState() } }

    @Published public var coupleCode: String = "LOVE-9966" { didSet { persistSettings() } }
    @Published public var partnerPairInput: String = ""
    @Published public var pairedPartnerName: String? = nil
    @Published public var notificationsEnabled: Bool = true { didSet { persistSettings() } }
    @Published public var early14d30dEnabled: Bool = false { didSet { persistSettings() } }
    @Published public var monthiversaryEnabled: Bool = false { didSet { persistSettings(); refreshComputedState() } }
    @Published public var soundEnabled: Bool = true { didSet { persistSettings() } }
    @Published public var redactWidgetOnLockScreen: Bool = false { didSet { persistSettings(); syncWidgetSnapshot() } }

    @Published public private(set) var loveDays: Int = 1
    @Published public private(set) var nextReadiness: NextOccasionReadiness?
    @Published public private(set) var upcomingOccasions: [NextOccasionReadiness] = []
    @Published public private(set) var upcomingMilestones: [LoveMilestoneInfo] = []
    @Published public private(set) var isCelebrationDay: Bool = false

    @Published public var dossier: PartnerDossier = PartnerDossier()
    @Published public var selectedTone: RelationshipTone = .sweet
    @Published public private(set) var adviceBundle: AiGiftAdviceBundle?
    @Published public private(set) var freeAiUsedThisMonth: Int = 0
    @Published public var memories: [LocalMemoryItem] = []

    @Published public var showPaywall: Bool = false
    @Published public var paywallHighlightFeature: ProFeature?
    @Published public var showCouplePairingSheet: Bool = false
    @Published public var showGuideSheet: Bool = false
    @Published public var openDossierRequested: Bool = false
    @Published public private(set) var pairingState: CouplePairingState = CouplePairingState()
    @Published public var toastBannerMessage: String?

    public let bridge = SharedKmpBridgeAdapter()
    public let storeKit = StoreKitEntitlementManager()
    public let adManager = IosAdPlacementManager()
    public let monetizationBootstrap: IosMonetizationBrainBootstrap
    public let coupleCoordinator: IosOnlineCoupleCoordinator
    private let cloudAdapter = FirebaseCoupleCloudAdapter()
    private let networkObserver = NetworkReachabilityObserver()
    private let dossierStore: any PartnerDossierStoring
    private let calculator = LoveDaysCalculator()
    private let defaults: UserDefaults
    private var cancellables: Set<AnyCancellable> = []

    public var isPro: Bool { storeKit.isPro || monetizationBootstrap.isProEffective() }

    public var remainingFreeAiRuns: Int {
        max(0, MonetizationLimits.freeAiGenerationsPerMonth - freeAiUsedThisMonth)
    }

    public init(
        dossierStore: any PartnerDossierStoring = FilePartnerDossierStore(),
        defaults: UserDefaults = .standard
    ) {
        self.dossierStore = dossierStore
        self.defaults = defaults
        self.coupleCoordinator = IosOnlineCoupleCoordinator(
            defaults: defaults,
            cloudSyncer: cloudAdapter
        )
        self.monetizationBootstrap = IosMonetizationBrainBootstrap(
            storeKit: storeKit,
            adManager: adManager,
            defaults: defaults
        )
        self.pairingState = coupleCoordinator.state
        coupleCoordinator.$state
            .sink { [weak self] updated in
                self?.pairingState = updated
                self?.pairedPartnerName = updated.partnerName
            }
            .store(in: &cancellables)
        restoreSettings()
        coupleCoordinator.updateOwnCoupleCode(coupleCode)
        seedInitialMemoriesIfNeeded()
        refreshComputedState()
    }

    public func bootstrap() async {
        await monetizationBootstrap.configureAll()
        adManager.updateProStatus(isPro)
        dossier = await dossierStore.loadDossier(scopeId: "ios_local")
        if let uid = pairingState.userUid, pairingState.isSignedIn {
            if let remote = try? await cloudAdapter.loadPrivateDossierBackup(uid: uid) {
                let merged = bridge.mergeDossier(local: dossier, remote: remote)
                if merged != dossier {
                    dossier = merged
                    try? await dossierStore.saveDossier(merged)
                }
            }
        }
        startConnectivitySyncMonitor()
        refreshComputedState()
        await generateAdvice(
            forOccasionId: nextReadiness?.occasionId ?? "valentine",
            occasionTitle: localizedOccasionTitle,
            consumeFreeAllowance: false
        )
    }

    private var localizedOccasionTitle: String {
        nextReadiness?.localizedTitle(isEnglish: isEnglish) ?? (isEnglish ? "Love Anniversary" : "Kỷ niệm tình yêu")
    }

    public func refreshComputedState(referenceDate: Date = Date()) {
        let todayIso = calculator.formatIso(referenceDate)
        loveDays = max(1, bridge.loveDaysBetween(startDateIso: startDateIso, todayIso: todayIso))
        upcomingOccasions = bridge.upcomingOccasions(
            todayIso: todayIso,
            loveStartDateIso: startDateIso,
            partnerBirthdayIso: partnerBirthdayIso,
            region: dossier.occasionRegion,
            withinDays: 180,
            includeMonthiversary: isPro && monthiversaryEnabled
        )
        nextReadiness = upcomingOccasions.first { $0.coupleRelevance >= .relevant }
        upcomingMilestones = bridge.upcomingMilestones(
            startDateIso: startDateIso,
            todayIso: todayIso,
            includeMonthiversaries: isPro && monthiversaryEnabled
        )
        isCelebrationDay = bridge.isSacredCelebrationToday(
            todayIso: todayIso,
            loveStartDateIso: startDateIso,
            partnerBirthdayIso: partnerBirthdayIso
        )
        adManager.updateCelebrationDay(isCelebrationDay)
        adManager.updateProStatus(isPro)
        syncWidgetSnapshot()
    }

    public func saveDossier(_ updated: PartnerDossier) async {
        var copy = updated
        copy.updatedAtEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
        dossier = copy
        try? await dossierStore.saveDossier(copy)
        await coupleCoordinator.syncLocalDossierMutation(copy)
        refreshComputedState()
        await generateAdvice(
            forOccasionId: nextReadiness?.occasionId ?? "valentine",
            occasionTitle: localizedOccasionTitle,
            consumeFreeAllowance: false
        )
    }

    public func addSecretNote(note: String, occasionHint: String, estimatedPriceVnd: Int64, shop: String) async -> Bool {
        let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        if !isPro && dossier.secretSurpriseNotes.count >= MonetizationLimits.freeSecretNotesLimit {
            presentPaywall(for: .deepPartnerDossierUnlimited)
            return false
        }
        var updated = dossier
        let entry = SecretHintNote(
            id: UUID().uuidString,
            note: trimmed,
            occasionHint: occasionHint,
            estimatedPriceVnd: estimatedPriceVnd,
            targetUrlOrShop: shop,
            isPurchased: false,
            createdAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        updated.secretSurpriseNotes.insert(entry, at: 0)
        await saveDossier(updated)
        return true
    }

    public func generateAdvice(
        forOccasionId occasionId: String,
        occasionTitle: String,
        consumeFreeAllowance: Bool = true
    ) async {
        if consumeFreeAllowance && !isPro && freeAiUsedThisMonth >= MonetizationLimits.freeAiGenerationsPerMonth && adviceBundle != nil {
            presentPaywall(for: .aiGiftConciergeUnlimited)
            return
        }
        let daysUntil = upcomingOccasions.first(where: { $0.occasionId == occasionId })?.daysRemaining
        let req = AiGiftRequest(
            dossier: dossier,
            occasionId: occasionId,
            occasionTitle: occasionTitle,
            daysUntilOccasion: daysUntil,
            loveDays: loveDays,
            tone: selectedTone,
            isEnglish: isEnglish,
            maxRecommendations: 5
        )
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let bundle = bridge.generateLocalAdvice(request: req, nowEpochMs: nowMs)
        adviceBundle = bundle
        if consumeFreeAllowance && !isPro {
            freeAiUsedThisMonth += 1
            defaults.set(freeAiUsedThisMonth, forKey: "inlove.freeAiUsedThisMonth")
        }
    }

    public func presentPaywall(for feature: ProFeature? = nil) {
        paywallHighlightFeature = feature
        showPaywall = true
    }

    public func connectPartnerCode(_ rawInput: String) {
        _ = sendPairingInvite(rawInput: rawInput)
    }

    public func extractCoupleCode(_ rawInput: String) -> String {
        bridge.extractCoupleCode(rawInput)
    }

    public func signInOnlineAccount(displayName: String) {
        let uid = "ios_\(displayName.lowercased().filter { $0.isLetter || $0.isNumber })_\(coupleCode)"
        coupleCoordinator.signInAccount(uid: uid, displayName: displayName, isEnglish: isEnglish)
        toastBannerMessage = coupleCoordinator.state.statusMessage
        Task { await triggerManualReconnectSync() }
    }

    public func signOutOnlineAccount() {
        coupleCoordinator.signOutAccount(isEnglish: isEnglish)
        toastBannerMessage = coupleCoordinator.state.statusMessage
    }

    @discardableResult
    public func sendPairingInvite(rawInput: String) -> Bool {
        let code = extractCoupleCode(rawInput)
        let ok = coupleCoordinator.sendInvite(rawCodeOrUrl: code, senderName: partner1Name, isEnglish: isEnglish)
        toastBannerMessage = coupleCoordinator.state.statusMessage
        return ok
    }

    public func acceptIncomingPairingInvite() {
        coupleCoordinator.acceptIncomingInvite(isEnglish: isEnglish)
        toastBannerMessage = coupleCoordinator.state.statusMessage
    }

    public func declineIncomingPairingInvite() {
        coupleCoordinator.declineIncomingInvite(isEnglish: isEnglish)
        toastBannerMessage = coupleCoordinator.state.statusMessage
    }

    public func cancelOutgoingPairingInvite() {
        coupleCoordinator.cancelOutgoingInvite(isEnglish: isEnglish)
        toastBannerMessage = coupleCoordinator.state.statusMessage
    }

    public func requestTwoWayUnpair() {
        coupleCoordinator.requestTwoWayUnpair(isEnglish: isEnglish)
        toastBannerMessage = coupleCoordinator.state.statusMessage
    }

    public func cancelTwoWayUnpair() {
        coupleCoordinator.cancelUnpairRequest(isEnglish: isEnglish)
        toastBannerMessage = coupleCoordinator.state.statusMessage
    }

    public func completeUnpairWorkflow() {
        coupleCoordinator.completeTwoWayUnpair(isEnglish: isEnglish)
        toastBannerMessage = coupleCoordinator.state.statusMessage
    }

    public func triggerManualReconnectSync() async {
        if let merged = await coupleCoordinator.handleNetworkReachabilityChanged(
            true,
            localDossier: dossier,
            isEnglish: isEnglish
        ) {
            dossier = merged
            try? await dossierStore.saveDossier(merged)
            refreshComputedState()
        }
        toastBannerMessage = coupleCoordinator.state.statusMessage
    }

    public func performGuideQuickAction(_ action: GuideQuickAction) {
        switch action {
        case .openHome:
            selectedTab = .home
        case .openCalendar:
            selectedTab = .calendar
        case .openDossier:
            selectedTab = .gifts
            openDossierRequested = true
        case .openPairing:
            selectedTab = .settings
            showCouplePairingSheet = true
        }
    }

    private func startConnectivitySyncMonitor() {
        networkObserver.start { [weak self] isReachable in
            guard let self else { return }
            Task { @MainActor in
                if let merged = await self.coupleCoordinator.handleNetworkReachabilityChanged(
                    isReachable,
                    localDossier: self.dossier,
                    isEnglish: self.isEnglish
                ) {
                    self.dossier = merged
                    try? await self.dossierStore.saveDossier(merged)
                    self.refreshComputedState()
                }
            }
        }
    }

    public func handleIncomingURL(_ url: URL) {
        let hostOrPath = url.host?.lowercased() ?? ""
        if url.absoluteString.lowercased().contains("pair") {
            let code = extractCoupleCode(url.absoluteString)
            if !code.isEmpty {
                partnerPairInput = code
                selectedTab = .settings
                showCouplePairingSheet = true
                connectPartnerCode(code)
                return
            }
        }
        switch hostOrPath {
        case "calendar": selectedTab = .calendar
        case "gifts": selectedTab = .gifts
        case "memories": selectedTab = .memories
        case "settings": selectedTab = .settings
        default: selectedTab = .home
        }
    }

    public func addMemory(title: String, dateText: String, location: String, note: String) {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        memories.insert(LocalMemoryItem(title: trimmed, dateText: dateText, location: location, note: note), at: 0)
        persistMemories()
    }

    public func toggleFavoriteMemory(id: String) {
        guard let idx = memories.firstIndex(where: { $0.id == id }) else { return }
        memories[idx].isFavorite.toggle()
        persistMemories()
    }

    public func deleteMemory(id: String) {
        memories.removeAll { $0.id == id }
        persistMemories()
    }

    public func syncWidgetSnapshot() {
        let snapshot = CoupleWidgetSnapshot(
            version: 1,
            coupleTitle: coupleTitle,
            partner1Name: partner1Name,
            partner2Name: partner2Name,
            loveDays: loveDays,
            startDateIso: startDateIso,
            nextOccasionTitle: nextReadiness?.localizedTitle(isEnglish: isEnglish) ?? (isEnglish ? "Love Anniversary" : "Kỷ niệm tình yêu"),
            nextOccasionDaysRemaining: nextReadiness?.daysRemaining,
            redacted: redactWidgetOnLockScreen,
            updatedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        if let url = WidgetSnapshotStorage.defaultSnapshotURL() {
            try? WidgetSnapshotStorage.save(snapshot, to: url)
        }
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    private func restoreSettings() {
        isEnglish = defaults.bool(forKey: "inlove.isEnglish")
        if let title = defaults.string(forKey: "inlove.coupleTitle"), !title.isEmpty { coupleTitle = title }
        if let p1 = defaults.string(forKey: "inlove.partner1"), !p1.isEmpty { partner1Name = p1 }
        if let p2 = defaults.string(forKey: "inlove.partner2"), !p2.isEmpty { partner2Name = p2 }
        if let start = defaults.string(forKey: "inlove.startDateIso"), !start.isEmpty { startDateIso = start }
        if let bday = defaults.string(forKey: "inlove.partnerBirthdayIso"), !bday.isEmpty { partnerBirthdayIso = bday }
        if let code = defaults.string(forKey: "inlove.coupleCode"), !code.isEmpty { coupleCode = code }
        notificationsEnabled = defaults.object(forKey: "inlove.notif") as? Bool ?? true
        early14d30dEnabled = defaults.bool(forKey: "inlove.early14d30d")
        monthiversaryEnabled = defaults.bool(forKey: "inlove.monthiversary")
        soundEnabled = defaults.object(forKey: "inlove.sound") as? Bool ?? true
        redactWidgetOnLockScreen = defaults.bool(forKey: "inlove.redactWidget")
        freeAiUsedThisMonth = defaults.integer(forKey: "inlove.freeAiUsedThisMonth")
    }

    private func persistSettings() {
        defaults.set(isEnglish, forKey: "inlove.isEnglish")
        defaults.set(coupleTitle, forKey: "inlove.coupleTitle")
        defaults.set(partner1Name, forKey: "inlove.partner1")
        defaults.set(partner2Name, forKey: "inlove.partner2")
        defaults.set(startDateIso, forKey: "inlove.startDateIso")
        defaults.set(partnerBirthdayIso, forKey: "inlove.partnerBirthdayIso")
        defaults.set(coupleCode, forKey: "inlove.coupleCode")
        defaults.set(notificationsEnabled, forKey: "inlove.notif")
        defaults.set(early14d30dEnabled, forKey: "inlove.early14d30d")
        defaults.set(monthiversaryEnabled, forKey: "inlove.monthiversary")
        defaults.set(soundEnabled, forKey: "inlove.sound")
        defaults.set(redactWidgetOnLockScreen, forKey: "inlove.redactWidget")
    }

    private func seedInitialMemoriesIfNeeded() {
        if let data = defaults.data(forKey: "inlove.memories"),
           let decoded = try? JSONDecoder().decode([LocalMemoryItem].self, from: data),
           !decoded.isEmpty {
            memories = decoded
            return
        }
        memories = [
            LocalMemoryItem(
                title: isEnglish ? "First Coffee Date" : "Buổi Cà Phê Đầu Tiên",
                dateText: "14/02/2024",
                location: "Quận 1, TP.HCM",
                note: isEnglish ? "Golden sunset and our first laugh together." : "Chiều hoàng hôn và những câu chuyện không hồi kết.",
                isFavorite: true
            ),
            LocalMemoryItem(
                title: isEnglish ? "100-Day Celebration" : "Kỷ Niệm 100 Ngày Yêu",
                dateText: "23/05/2024",
                location: "Đà Lạt",
                note: isEnglish ? "Cool pine air and warm tea." : "Không khí se lạnh và tách trà ấm bên nhau.",
                isFavorite: false
            ),
        ]
    }

    private func persistMemories() {
        if let data = try? JSONEncoder().encode(memories) {
            defaults.set(data, forKey: "inlove.memories")
        }
    }
}
