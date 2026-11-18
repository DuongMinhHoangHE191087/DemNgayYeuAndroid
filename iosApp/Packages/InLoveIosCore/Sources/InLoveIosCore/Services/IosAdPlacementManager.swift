import Combine
import Foundation

public enum AdSuppressionReason: Equatable, Sendable {
    case allowed
    case proEntitlement
    case noConsent
    case sacredZone(SacredZone)
    case celebrationDay
    case warmupActive
    case cooldownActive
    case sessionCapReached
}

@MainActor
public final class IosAdPlacementManager: ObservableObject {
    @Published public private(set) var activeSacredZones: Set<SacredZone> = []
    @Published public private(set) var interstitialsShownThisSession: Int = 0
    @Published public private(set) var lastInterstitialEpochMs: Int64 = 0
    @Published public private(set) var hasConsent: Bool
    @Published public private(set) var isCelebrationDay: Bool

    public let sessionStartEpochMs: Int64
    private var isPro: Bool

    public init(
        sessionStartEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        isPro: Bool = false,
        hasConsent: Bool = true,
        isCelebrationDay: Bool = false
    ) {
        self.sessionStartEpochMs = sessionStartEpochMs
        self.isPro = isPro
        self.hasConsent = hasConsent
        self.isCelebrationDay = isCelebrationDay
    }

    public func updateProStatus(_ isPro: Bool) {
        self.isPro = isPro
    }

    public func updateConsent(_ granted: Bool) {
        self.hasConsent = granted
    }

    public func updateCelebrationDay(_ active: Bool) {
        self.isCelebrationDay = active
    }

    public func enterSacredZone(_ zone: SacredZone) {
        activeSacredZones.insert(zone)
    }

    public func exitSacredZone(_ zone: SacredZone) {
        activeSacredZones.remove(zone)
    }

    public func isInSacredZone() -> Bool {
        isCelebrationDay || !activeSacredZones.isEmpty
    }

    public func shouldShowBanner() -> Bool {
        if isPro || !hasConsent || isInSacredZone() { return false }
        return true
    }

    public func evaluateInterstitial(nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> AdSuppressionReason {
        if isPro { return .proEntitlement }
        if !hasConsent { return .noConsent }
        if isCelebrationDay { return .celebrationDay }
        if let firstZone = activeSacredZones.sorted(by: { $0.rawValue < $1.rawValue }).first {
            return .sacredZone(firstZone)
        }
        if nowEpochMs - sessionStartEpochMs < MonetizationLimits.warmupMs {
            return .warmupActive
        }
        if interstitialsShownThisSession >= MonetizationLimits.maxInterstitialsPerSession {
            return .sessionCapReached
        }
        if lastInterstitialEpochMs > 0 && (nowEpochMs - lastInterstitialEpochMs < MonetizationLimits.interstitialCooldownMs) {
            return .cooldownActive
        }
        return .allowed
    }

    public func recordInterstitialShown(nowEpochMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        interstitialsShownThisSession += 1
        lastInterstitialEpochMs = nowEpochMs
    }

    public func shouldSuggestSoftPaywall(
        freeAiUsedThisMonth: Int,
        secretNotesCount: Int
    ) -> ProFeature? {
        guard !isPro else { return nil }
        if freeAiUsedThisMonth >= MonetizationLimits.freeAiGenerationsPerMonth {
            return .aiGiftConciergeUnlimited
        }
        if secretNotesCount >= MonetizationLimits.freeSecretNotesLimit {
            return .deepPartnerDossierUnlimited
        }
        return nil
    }
}
