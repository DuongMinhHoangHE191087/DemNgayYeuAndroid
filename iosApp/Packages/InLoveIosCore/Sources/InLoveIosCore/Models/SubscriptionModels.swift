import Foundation

public enum InLoveSku {
    public static let monthly = "vip_monthly"
    public static let yearly = "vip_yearly"
    public static let lifetime = "vip_lifetime"

    public static let allSkus: Set<String> = [monthly, yearly, lifetime]
    public static let orderedSkus: [String] = [yearly, monthly, lifetime]
}

public enum ProFeature: String, Codable, CaseIterable, Sendable {
    case adFree = "AD_FREE"
    case aiGiftConciergeUnlimited = "AI_GIFT_CONCIERGE_UNLIMITED"
    case deepPartnerDossierUnlimited = "DEEP_PARTNER_DOSSIER_UNLIMITED"
    case early14d30dReminders = "EARLY_14D_30D_REMINDERS"
    case monthiversaryCadence = "MONTHIVERSARY_CADENCE"
    case premiumWallpapersAndWidgets = "PREMIUM_WALLPAPERS_AND_WIDGETS"

    public func localizedTitle(isEnglish: Bool) -> String {
        switch self {
        case .adFree:
            return isEnglish ? "Ad-free couple space" : "Bỏ toàn bộ quảng cáo"
        case .aiGiftConciergeUnlimited:
            return isEnglish ? "Gift & love-note advisor (up to 60/month)" : "Trợ lý quà tặng & lời chúc (tối đa 60 lượt/tháng)"
        case .deepPartnerDossierUnlimited:
            return isEnglish ? "Unlimited partner profile & secret hints" : "Hồ sơ người thương & ghi chú bí mật không giới hạn"
        case .early14d30dReminders:
            return isEnglish ? "Early 14–30 day occasion reminders" : "Nhắc hẹn sớm 14–30 ngày để đặt bàn & chuẩn bị"
        case .monthiversaryCadence:
            return isEnglish ? "Monthly & 520 / 1,314-day milestones" : "Nhắc kỷ niệm tròn tháng & mốc 520, 1.314 ngày"
        case .premiumWallpapersAndWidgets:
            return isEnglish ? "Curated couple themes & home widgets" : "Hình nền đôi & widget màn hình khóa cao cấp"
        }
    }
}

public enum SacredZone: String, Codable, CaseIterable, Sendable, Hashable {
    case onboarding = "ONBOARDING"
    case paywall = "PAYWALL"
    case pinLock = "PIN_LOCK"
    case milestoneCelebrationDay = "MILESTONE_CELEBRATION_DAY"
    case memoryCaptureFlow = "MEMORY_CAPTURE_FLOW"
    case pairingAcceptFlow = "PAIRING_ACCEPT_FLOW"
}

public enum MonetizationLimits {
    public static let freeAiGenerationsPerMonth = 2
    public static let freeSecretNotesLimit = 5
    public static let warmupMs: Int64 = 60_000
    public static let interstitialCooldownMs: Int64 = 120_000
    public static let maxInterstitialsPerSession = 3
}

public struct SubscriptionPlanOffer: Identifiable, Equatable, Sendable {
    public var id: String { productId }
    public let productId: String
    public let titleVi: String
    public let titleEn: String
    public let subtitleVi: String
    public let subtitleEn: String
    public let displayPrice: String
    public let referencePriceVnd: Int64
    public let referencePriceUsd: Double
    public let savingsPercent: Int
    public let taglineVi: String
    public let taglineEn: String
    public let trialDays: Int
    public let isLifetime: Bool

    public init(
        productId: String,
        titleVi: String,
        titleEn: String,
        subtitleVi: String,
        subtitleEn: String,
        displayPrice: String,
        referencePriceVnd: Int64 = 0,
        referencePriceUsd: Double = 0,
        savingsPercent: Int = 0,
        taglineVi: String = "",
        taglineEn: String = "",
        trialDays: Int,
        isLifetime: Bool
    ) {
        self.productId = productId
        self.titleVi = titleVi
        self.titleEn = titleEn
        self.subtitleVi = subtitleVi
        self.subtitleEn = subtitleEn
        self.displayPrice = displayPrice
        self.referencePriceVnd = referencePriceVnd
        self.referencePriceUsd = referencePriceUsd
        self.savingsPercent = savingsPercent
        self.taglineVi = taglineVi
        self.taglineEn = taglineEn
        self.trialDays = trialDays
        self.isLifetime = isLifetime
    }

    public static func defaultFallbackOffers() -> [SubscriptionPlanOffer] {
        [
            SubscriptionPlanOffer(
                productId: InLoveSku.yearly,
                titleVi: "InLove Pro Năm",
                titleEn: "InLove Pro Yearly",
                subtitleVi: "Thanh toán theo năm, hủy bất kỳ lúc nào",
                subtitleEn: "Billed yearly, cancel anytime",
                displayPrice: "$17.99",
                referencePriceVnd: 199_000,
                referencePriceUsd: 17.99,
                savingsPercent: 57,
                taglineVi: "Tiết kiệm 57% · Dùng thử 3 ngày miễn phí",
                taglineEn: "Save 57% · 3-day free trial",
                trialDays: 3,
                isLifetime: false
            ),
            SubscriptionPlanOffer(
                productId: InLoveSku.monthly,
                titleVi: "InLove Pro Tháng",
                titleEn: "InLove Pro Monthly",
                subtitleVi: "Thanh toán linh hoạt theo tháng, hủy bất kỳ lúc nào",
                subtitleEn: "Flexible monthly billing, cancel anytime",
                displayPrice: "$2.99",
                referencePriceVnd: 39_000,
                referencePriceUsd: 2.99,
                savingsPercent: 0,
                taglineVi: "Trải nghiệm linh hoạt theo tháng",
                taglineEn: "Flexible month-to-month",
                trialDays: 0,
                isLifetime: false
            ),
            SubscriptionPlanOffer(
                productId: InLoveSku.lifetime,
                titleVi: "InLove Pro Trọn Đời",
                titleEn: "InLove Pro Lifetime",
                subtitleVi: "Không gia hạn · Dùng dài lâu",
                subtitleEn: "No renewal · Yours to keep",
                displayPrice: "$39.99",
                referencePriceVnd: 399_000,
                referencePriceUsd: 39.99,
                savingsPercent: 0,
                taglineVi: "Thanh toán một lần · Gắn bó trọn đời",
                taglineEn: "One-time unlock · Forever yours",
                trialDays: 0,
                isLifetime: true
            ),
        ]
    }
}

public struct EntitlementSnapshot: Codable, Equatable, Sendable {
    public var isPro: Bool
    public var activeProductId: String?
    public var isLifetime: Bool
    public var expirationDateEpochMs: Int64?
    public var lastVerifiedEpochMs: Int64

    public init(
        isPro: Bool = false,
        activeProductId: String? = nil,
        isLifetime: Bool = false,
        expirationDateEpochMs: Int64? = nil,
        lastVerifiedEpochMs: Int64 = 0
    ) {
        self.isPro = isPro
        self.activeProductId = activeProductId
        self.isLifetime = isLifetime
        self.expirationDateEpochMs = expirationDateEpochMs
        self.lastVerifiedEpochMs = lastVerifiedEpochMs
    }
}
