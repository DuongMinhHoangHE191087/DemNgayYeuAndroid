import Combine
import Foundation

public enum OnlineSyncPhase: String, Codable, CaseIterable, Sendable {
    case guestLocal = "GUEST_LOCAL"
    case signedInSolo = "SIGNED_IN_SOLO"
    case inviteOutgoing = "INVITE_OUTGOING"
    case inviteIncoming = "INVITE_INCOMING"
    case coupledSynced = "COUPLED_SYNCED"
    case coupledOfflineQueue = "COUPLED_OFFLINE_QUEUE"
    case breakupPending = "BREAKUP_PENDING"

    public var isCoupled: Bool {
        self == .coupledSynced || self == .coupledOfflineQueue || self == .breakupPending
    }

    public func statusLabel(isEnglish: Bool) -> String {
        switch self {
        case .guestLocal:
            return isEnglish ? "Local on this device" : "Đang lưu cục bộ trên máy"
        case .signedInSolo:
            return isEnglish ? "Signed in · Ready to pair" : "Đã đăng nhập · Sẵn sàng ghép đôi"
        case .inviteOutgoing:
            return isEnglish ? "Invite sent · Waiting for partner" : "Đã gửi lời mời · Đang chờ người ấy"
        case .inviteIncoming:
            return isEnglish ? "Incoming partner invite" : "Có lời mời ghép đôi mới"
        case .coupledSynced:
            return isEnglish ? "Paired & synced" : "Đã ghép đôi · Đã đồng bộ"
        case .coupledOfflineQueue:
            return isEnglish ? "Paired · Queued offline changes" : "Đã ghép đôi · Chờ đồng bộ khi có mạng"
        case .breakupPending:
            return isEnglish ? "Unpair confirmation pending" : "Đang chờ xác nhận hủy ghép đôi"
        }
    }
}

public struct CoupleInvite: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let fromUid: String
    public let fromName: String
    public let fromCode: String
    public let toCode: String
    public let createdAtEpochMs: Int64

    public init(
        id: String = UUID().uuidString,
        fromUid: String,
        fromName: String,
        fromCode: String,
        toCode: String,
        createdAtEpochMs: Int64
    ) {
        self.id = id
        self.fromUid = fromUid
        self.fromName = fromName
        self.fromCode = fromCode
        self.toCode = toCode
        self.createdAtEpochMs = createdAtEpochMs
    }
}

public struct CouplePairingState: Codable, Equatable, Sendable {
    public var phase: OnlineSyncPhase
    public var isSignedIn: Bool
    public var userUid: String?
    public var userDisplayName: String?
    public var ownCoupleCode: String
    public var coupleId: String?
    public var partnerUid: String?
    public var partnerName: String?
    public var partnerCode: String?
    public var outgoingInvite: CoupleInvite?
    public var incomingInvite: CoupleInvite?
    public var breakupRequestedBySelf: Bool
    public var pendingSyncCount: Int
    public var isNetworkReachable: Bool
    public var lastSyncedEpochMs: Int64?
    public var statusMessage: String?

    public init(
        phase: OnlineSyncPhase = .guestLocal,
        isSignedIn: Bool = false,
        userUid: String? = nil,
        userDisplayName: String? = nil,
        ownCoupleCode: String = "LOVE-9966",
        coupleId: String? = nil,
        partnerUid: String? = nil,
        partnerName: String? = nil,
        partnerCode: String? = nil,
        outgoingInvite: CoupleInvite? = nil,
        incomingInvite: CoupleInvite? = nil,
        breakupRequestedBySelf: Bool = false,
        pendingSyncCount: Int = 0,
        isNetworkReachable: Bool = true,
        lastSyncedEpochMs: Int64? = nil,
        statusMessage: String? = nil
    ) {
        self.phase = phase
        self.isSignedIn = isSignedIn
        self.userUid = userUid
        self.userDisplayName = userDisplayName
        self.ownCoupleCode = ownCoupleCode
        self.coupleId = coupleId
        self.partnerUid = partnerUid
        self.partnerName = partnerName
        self.partnerCode = partnerCode
        self.outgoingInvite = outgoingInvite
        self.incomingInvite = incomingInvite
        self.breakupRequestedBySelf = breakupRequestedBySelf
        self.pendingSyncCount = pendingSyncCount
        self.isNetworkReachable = isNetworkReachable
        self.lastSyncedEpochMs = lastSyncedEpochMs
        self.statusMessage = statusMessage
    }
}

public enum CoupleCodeParser {
    public static func extractCode(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }

        if let url = URL(string: trimmed),
           let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
           let queryCode = components.queryItems?.first(where: { $0.name.lowercased() == "code" })?.value {
            return normalizeCode(queryCode)
        }
        if let range = trimmed.range(of: "/pair/", options: [.caseInsensitive]) {
            let tail = String(trimmed[range.upperBound...])
            let codeSegment = tail.split(separator: "?").first.map(String.init) ?? tail
            return normalizeCode(codeSegment)
        }
        if trimmed.lowercased().hasPrefix("code=") {
            return normalizeCode(String(trimmed.dropFirst(5)))
        }
        return normalizeCode(trimmed)
    }

    public static func buildInviteLink(code: String) -> String {
        "https://inlove.app/pair?code=\(normalizeCode(code))"
    }

    private static func normalizeCode(_ value: String) -> String {
        let allowed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
            .filter { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" }
        return allowed
    }
}

public enum DossierMergePolicy {
    public static func merge(local: PartnerDossier, remote: PartnerDossier) -> PartnerDossier {
        let preferLocalScalars = local.updatedAtEpochMs > remote.updatedAtEpochMs
        let primary = preferLocalScalars ? local : remote
        let secondary = preferLocalScalars ? remote : local

        let mergedNotes = mergeSecretNotes(local.secretSurpriseNotes, remote.secretSurpriseNotes)
        let mergedGifts = mergeGiftHistory(local.giftHistory, remote.giftHistory)

        let mergedNickname = nonEmptyOrFallback(primary.nickname, secondary.nickname)
        let mergedLikes = nonEmptyOrFallback(primary.freeformLikes, secondary.freeformLikes)
        let mergedLanguages = primary.loveLanguages.isEmpty ? secondary.loveLanguages : primary.loveLanguages
        let mergedInterests = primary.interestCategories.isEmpty ? secondary.interestCategories : primary.interestCategories
        let mergedBudget = primary.budgetMaxVnd > 0 ? primary.budgetMaxVnd : secondary.budgetMaxVnd
        let mergedRegion = primary.occasionRegion.isEmpty ? secondary.occasionRegion : primary.occasionRegion

        return PartnerDossier(
            scopeId: nonEmptyOrFallback(primary.scopeId, secondary.scopeId),
            nickname: mergedNickname,
            loveLanguages: mergedLanguages,
            interestCategories: mergedInterests,
            freeformLikes: mergedLikes,
            sizes: mergeSizes(primary.sizes, secondary.sizes),
            foodAndCare: mergeFoodAndCare(primary.foodAndCare, secondary.foodAndCare),
            secretSurpriseNotes: mergedNotes,
            giftHistory: mergedGifts,
            budgetMaxVnd: mergedBudget,
            occasionRegion: mergedRegion,
            updatedAtEpochMs: max(local.updatedAtEpochMs, remote.updatedAtEpochMs)
        )
    }

    private static func mergeSecretNotes(_ lhs: [SecretHintNote], _ rhs: [SecretHintNote]) -> [SecretHintNote] {
        var byId: [String: SecretHintNote] = [:]
        for note in lhs + rhs {
            if let existing = byId[note.id] {
                let mergedPurchased = existing.isPurchased || note.isPurchased
                let chosen = note.createdAtEpochMs >= existing.createdAtEpochMs ? note : existing
                byId[note.id] = SecretHintNote(
                    id: chosen.id,
                    note: chosen.note,
                    occasionHint: chosen.occasionHint,
                    estimatedPriceVnd: chosen.estimatedPriceVnd,
                    targetUrlOrShop: chosen.targetUrlOrShop,
                    isPurchased: mergedPurchased,
                    createdAtEpochMs: max(existing.createdAtEpochMs, note.createdAtEpochMs)
                )
            } else {
                byId[note.id] = note
            }
        }
        return byId.values.sorted {
            if $0.createdAtEpochMs != $1.createdAtEpochMs {
                return $0.createdAtEpochMs > $1.createdAtEpochMs
            }
            return $0.id < $1.id
        }
    }

    private static func mergeGiftHistory(_ lhs: [GivenGiftRecord], _ rhs: [GivenGiftRecord]) -> [GivenGiftRecord] {
        var byId: [String: GivenGiftRecord] = [:]
        for record in lhs + rhs {
            if let existing = byId[record.id] {
                byId[record.id] = record.partnerReactionRating >= existing.partnerReactionRating ? record : existing
            } else {
                byId[record.id] = record
            }
        }
        return byId.values.sorted {
            if $0.givenDateIso != $1.givenDateIso {
                return $0.givenDateIso > $1.givenDateIso
            }
            return $0.id < $1.id
        }
    }

    private static func mergeSizes(_ primary: PartnerSizes, _ secondary: PartnerSizes) -> PartnerSizes {
        PartnerSizes(
            shirtSize: nonEmptyOrFallback(primary.shirtSize, secondary.shirtSize),
            pantsOrDressSize: nonEmptyOrFallback(primary.pantsOrDressSize, secondary.pantsOrDressSize),
            shoeSize: nonEmptyOrFallback(primary.shoeSize, secondary.shoeSize),
            ringSize: nonEmptyOrFallback(primary.ringSize, secondary.ringSize),
            favoriteColors: primary.favoriteColors.isEmpty ? secondary.favoriteColors : primary.favoriteColors,
            favoriteScents: primary.favoriteScents.isEmpty ? secondary.favoriteScents : primary.favoriteScents
        )
    }

    private static func mergeFoodAndCare(_ primary: PartnerFoodAndCare, _ secondary: PartnerFoodAndCare) -> PartnerFoodAndCare {
        PartnerFoodAndCare(
            favoriteFoods: primary.favoriteFoods.isEmpty ? secondary.favoriteFoods : primary.favoriteFoods,
            goToDrinks: nonEmptyOrFallback(primary.goToDrinks, secondary.goToDrinks),
            allergies: Array(Set(primary.allergies + secondary.allergies)).sorted(),
            dislikesOrTaboos: Array(Set(primary.dislikesOrTaboos + secondary.dislikesOrTaboos)).sorted()
        )
    }

    private static func nonEmptyOrFallback(_ primary: String, _ secondary: String) -> String {
        primary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? secondary : primary
    }
}

public protocol CloudDossierBackupSyncing: Sendable {
    func savePrivateDossierBackup(_ dossier: PartnerDossier, uid: String) async throws
    func loadPrivateDossierBackup(uid: String) async throws -> PartnerDossier?
}

public struct NoopCloudDossierBackupSyncer: CloudDossierBackupSyncing {
    public init() {}
    public func savePrivateDossierBackup(_ dossier: PartnerDossier, uid: String) async throws {}
    public func loadPrivateDossierBackup(uid: String) async throws -> PartnerDossier? { nil }
}

@MainActor
public final class IosOnlineCoupleCoordinator: ObservableObject {
    @Published public private(set) var state: CouplePairingState
    private let defaults: UserDefaults?
    private let storageKey: String
    private let cloudSyncer: any CloudDossierBackupSyncing
    private var queuedLocalDossier: PartnerDossier?

    public init(
        initialState: CouplePairingState = CouplePairingState(),
        defaults: UserDefaults? = .standard,
        storageKey: String = "inlove.coupleSyncState.v1",
        cloudSyncer: any CloudDossierBackupSyncing = NoopCloudDossierBackupSyncer()
    ) {
        self.defaults = defaults
        self.storageKey = storageKey
        self.cloudSyncer = cloudSyncer
        if let defaults,
           let data = defaults.data(forKey: storageKey),
           let decoded = try? JSONDecoder().decode(CouplePairingState.self, from: data) {
            self.state = decoded
        } else {
            self.state = initialState
        }
    }

    public func updateOwnCoupleCode(_ code: String) {
        let normalized = CoupleCodeParser.extractCode(code)
        guard !normalized.isEmpty else { return }
        state.ownCoupleCode = normalized
        persistState()
    }

    public func signInAccount(uid: String, displayName: String, isEnglish: Bool) {
        state.isSignedIn = true
        state.userUid = uid
        state.userDisplayName = displayName
        if !state.phase.isCoupled && state.phase != .inviteOutgoing && state.phase != .inviteIncoming {
            state.phase = .signedInSolo
        }
        state.statusMessage = isEnglish ? "Signed in as \(displayName)" : "Đã đăng nhập tài khoản \(displayName)"
        persistState()
    }

    public func signOutAccount(isEnglish: Bool) {
        state.isSignedIn = false
        state.userUid = nil
        state.userDisplayName = nil
        state.coupleId = nil
        state.partnerUid = nil
        state.partnerName = nil
        state.partnerCode = nil
        state.outgoingInvite = nil
        state.incomingInvite = nil
        state.breakupRequestedBySelf = false
        state.pendingSyncCount = 0
        state.phase = .guestLocal
        state.statusMessage = isEnglish ? "Switched to local mode on this device" : "Đã chuyển về chế độ lưu cục bộ trên máy"
        persistState()
    }

    @discardableResult
    public func sendInvite(rawCodeOrUrl: String, senderName: String, isEnglish: Bool, nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> Bool {
        let targetCode = CoupleCodeParser.extractCode(rawCodeOrUrl)
        guard !targetCode.isEmpty, targetCode != state.ownCoupleCode else {
            state.statusMessage = isEnglish ? "Enter a valid partner invite code." : "Vui lòng nhập mã mời hợp lệ của người ấy."
            persistState()
            return false
        }
        let invite = CoupleInvite(
            fromUid: state.userUid ?? "local-user",
            fromName: senderName,
            fromCode: state.ownCoupleCode,
            toCode: targetCode,
            createdAtEpochMs: nowEpochMs
        )
        state.outgoingInvite = invite
        state.partnerCode = targetCode
        state.phase = .inviteOutgoing
        state.statusMessage = isEnglish ? "Pairing invite sent to \(targetCode)" : "Đã gửi lời mời ghép đôi tới \(targetCode)"
        persistState()
        return true
    }

    public func receiveIncomingInvite(_ invite: CoupleInvite, isEnglish: Bool) {
        state.incomingInvite = invite
        if !state.phase.isCoupled {
            state.phase = .inviteIncoming
        }
        state.statusMessage = isEnglish
            ? "\(invite.fromName) invited you to connect"
            : "\(invite.fromName) vừa gửi lời mời ghép đôi"
        persistState()
    }

    public func acceptIncomingInvite(isEnglish: Bool, nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        guard let invite = state.incomingInvite else { return }
        state.coupleId = "couple_\(invite.fromCode)_\(state.ownCoupleCode)"
        state.partnerUid = invite.fromUid
        state.partnerName = invite.fromName
        state.partnerCode = invite.fromCode
        state.incomingInvite = nil
        state.outgoingInvite = nil
        state.breakupRequestedBySelf = false
        state.lastSyncedEpochMs = state.isNetworkReachable ? nowEpochMs : state.lastSyncedEpochMs
        state.phase = state.isNetworkReachable ? .coupledSynced : .coupledOfflineQueue
        if !state.isNetworkReachable {
            state.pendingSyncCount += 1
        }
        state.statusMessage = isEnglish ? "Connected with \(invite.fromName)" : "Đã ghép đôi với \(invite.fromName)"
        persistState()
    }

    public func confirmPartnerAcceptedOutgoingInvite(partnerName: String, isEnglish: Bool, nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        guard let outgoing = state.outgoingInvite else { return }
        state.coupleId = "couple_\(state.ownCoupleCode)_\(outgoing.toCode)"
        state.partnerName = partnerName
        state.partnerCode = outgoing.toCode
        state.outgoingInvite = nil
        state.breakupRequestedBySelf = false
        state.lastSyncedEpochMs = nowEpochMs
        state.phase = state.isNetworkReachable ? .coupledSynced : .coupledOfflineQueue
        state.statusMessage = isEnglish ? "Paired with \(partnerName)" : "Đã ghép đôi cùng \(partnerName)"
        persistState()
    }

    public func declineIncomingInvite(isEnglish: Bool) {
        state.incomingInvite = nil
        state.phase = state.isSignedIn ? .signedInSolo : .guestLocal
        state.statusMessage = isEnglish ? "Invite declined" : "Đã từ chối lời mời"
        persistState()
    }

    public func cancelOutgoingInvite(isEnglish: Bool) {
        state.outgoingInvite = nil
        state.partnerCode = nil
        state.phase = state.isSignedIn ? .signedInSolo : .guestLocal
        state.statusMessage = isEnglish ? "Invite cancelled" : "Đã hủy lời mời đã gửi"
        persistState()
    }

    public func requestTwoWayUnpair(isEnglish: Bool) {
        guard state.phase.isCoupled else { return }
        state.breakupRequestedBySelf = true
        state.phase = .breakupPending
        state.statusMessage = isEnglish
            ? "Unpair requested. Confirm both sides to disconnect."
            : "Đã tạo yêu cầu hủy ghép đôi. Cần xác nhận 2 chiều để hoàn tất."
        persistState()
    }

    public func cancelUnpairRequest(isEnglish: Bool) {
        guard state.phase == .breakupPending else { return }
        state.breakupRequestedBySelf = false
        state.phase = (state.pendingSyncCount > 0 || !state.isNetworkReachable) ? .coupledOfflineQueue : .coupledSynced
        state.statusMessage = isEnglish ? "Kept couple pairing active" : "Đã giữ nguyên trạng thái ghép đôi"
        persistState()
    }

    public func completeTwoWayUnpair(isEnglish: Bool) {
        state.coupleId = nil
        state.partnerUid = nil
        state.partnerName = nil
        state.partnerCode = nil
        state.breakupRequestedBySelf = false
        state.pendingSyncCount = 0
        state.outgoingInvite = nil
        state.incomingInvite = nil
        state.phase = state.isSignedIn ? .signedInSolo : .guestLocal
        state.statusMessage = isEnglish ? "Couple pairing disconnected" : "Đã hủy ghép đôi an toàn"
        persistState()
    }

    public func syncLocalDossierMutation(_ dossier: PartnerDossier, nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) async {
        queuedLocalDossier = dossier
        if state.isNetworkReachable, let uid = state.userUid, state.isSignedIn {
            do {
                try await cloudSyncer.savePrivateDossierBackup(dossier, uid: uid)
                if state.phase == .coupledOfflineQueue || state.phase == .coupledSynced {
                    state.phase = .coupledSynced
                    state.pendingSyncCount = 0
                    state.lastSyncedEpochMs = nowEpochMs
                }
                persistState()
                return
            } catch {
                // Fall through into offline queue when backup call fails while paired
            }
        }
        if state.phase.isCoupled {
            state.pendingSyncCount += 1
            if state.phase != .breakupPending {
                state.phase = .coupledOfflineQueue
            }
            persistState()
        }
    }

    public func handleNetworkReachabilityChanged(
        _ isReachable: Bool,
        localDossier: PartnerDossier,
        isEnglish: Bool,
        nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) async -> PartnerDossier? {
        let wasOffline = !state.isNetworkReachable
        state.isNetworkReachable = isReachable
        guard isReachable else {
            if state.phase == .coupledSynced {
                state.phase = .coupledOfflineQueue
            }
            persistState()
            return nil
        }

        let shouldFlush = wasOffline || state.pendingSyncCount > 0 || state.phase == .coupledOfflineQueue
        guard shouldFlush else {
            persistState()
            return nil
        }

        let effectiveLocal = queuedLocalDossier ?? localDossier
        var finalDossier = effectiveLocal
        if let uid = state.userUid, state.isSignedIn {
            if let remote = try? await cloudSyncer.loadPrivateDossierBackup(uid: uid) {
                finalDossier = DossierMergePolicy.merge(local: effectiveLocal, remote: remote)
            }
            try? await cloudSyncer.savePrivateDossierBackup(finalDossier, uid: uid)
        }

        if state.phase == .coupledOfflineQueue || state.phase == .coupledSynced {
            state.phase = .coupledSynced
            state.pendingSyncCount = 0
            state.lastSyncedEpochMs = nowEpochMs
            state.statusMessage = isEnglish ? "Synced automatically on reconnect" : "Đã tự đồng bộ lại khi có mạng"
        }
        queuedLocalDossier = nil
        persistState()
        return finalDossier
    }

    private func persistState() {
        guard let defaults, let data = try? JSONEncoder().encode(state) else { return }
        defaults.set(data, forKey: storageKey)
    }
}
