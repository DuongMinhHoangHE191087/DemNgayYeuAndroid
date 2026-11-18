import Foundation

public enum CoupleRelevance: String, Codable, Sendable, Comparable {
    case none = "NONE"
    case optional = "OPTIONAL"
    case relevant = "RELEVANT"
    case core = "CORE"

    private var sortOrder: Int {
        switch self {
        case .none: return 0
        case .optional: return 1
        case .relevant: return 2
        case .core: return 3
        }
    }

    public static func < (lhs: CoupleRelevance, rhs: CoupleRelevance) -> Bool {
        lhs.sortOrder < rhs.sortOrder
    }
}

public enum GiftExpectation: String, Codable, Sendable {
    case none = "NONE"
    case small = "SMALL"
    case expected = "EXPECTED"
    case major = "MAJOR"
}

public enum PreparationStage: String, Codable, Sendable {
    case relaxed = "RELAXED"
    case planAheadBooking = "PLAN_AHEAD_BOOKING"
    case actionNeeded = "ACTION_NEEDED"
    case urgentLastMinute = "URGENT_LAST_MINUTE"
    case todayCelebration = "TODAY_CELEBRATION"

    public func badgeTitle(isEnglish: Bool) -> String {
        switch self {
        case .relaxed:
            return isEnglish ? "Relaxed" : "Thư thả chuẩn bị"
        case .planAheadBooking:
            return isEnglish ? "Book Early" : "Đặt bàn & chuẩn bị sớm"
        case .actionNeeded:
            return isEnglish ? "Action Needed" : "Nên chốt quà tuần này"
        case .urgentLastMinute:
            return isEnglish ? "Urgent" : "Sắp đến rồi!"
        case .todayCelebration:
            return isEnglish ? "Today!" : "Hôm nay là ngày đặc biệt!"
        }
    }
}

public struct OccasionProfile: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let titleVi: String
    public let titleEn: String
    public let coupleRelevance: CoupleRelevance
    public let giftExpectation: GiftExpectation
    public let defaultReminderOn: Bool
    public let recommendedLeadDays: [Int]
    public let bookingAdvised: Bool
    public let culturalTaboosVi: [String]
    public let culturalTaboosEn: [String]
    public let prepChecklistVi: [String]
    public let prepChecklistEn: [String]

    public init(
        id: String,
        titleVi: String,
        titleEn: String,
        coupleRelevance: CoupleRelevance,
        giftExpectation: GiftExpectation,
        defaultReminderOn: Bool,
        recommendedLeadDays: [Int],
        bookingAdvised: Bool,
        culturalTaboosVi: [String],
        culturalTaboosEn: [String],
        prepChecklistVi: [String],
        prepChecklistEn: [String]
    ) {
        self.id = id
        self.titleVi = titleVi
        self.titleEn = titleEn
        self.coupleRelevance = coupleRelevance
        self.giftExpectation = giftExpectation
        self.defaultReminderOn = defaultReminderOn
        self.recommendedLeadDays = recommendedLeadDays
        self.bookingAdvised = bookingAdvised
        self.culturalTaboosVi = culturalTaboosVi
        self.culturalTaboosEn = culturalTaboosEn
        self.prepChecklistVi = prepChecklistVi
        self.prepChecklistEn = prepChecklistEn
    }

    public func localizedTitle(isEnglish: Bool) -> String {
        isEnglish ? titleEn : titleVi
    }
}

public struct NextOccasionReadiness: Codable, Equatable, Identifiable, Sendable {
    public var id: String { "\(occasionId)_\(targetDateIso)" }
    public let occasionId: String
    public let titleVi: String
    public let titleEn: String
    public let targetDateIso: String
    public let daysRemaining: Int
    public let stage: PreparationStage
    public let coupleRelevance: CoupleRelevance
    public let giftExpectation: GiftExpectation
    public let bookingAdvised: Bool
    public let recommendedLeadDays: Int
    public let headlineVi: String
    public let headlineEn: String
    public let actionSummaryVi: String
    public let actionSummaryEn: String
    public let prepChecklistVi: [String]
    public let prepChecklistEn: [String]
    public let culturalTaboosVi: [String]
    public let culturalTaboosEn: [String]
    public let isMilestone: Bool

    public init(
        occasionId: String,
        titleVi: String,
        titleEn: String,
        targetDateIso: String,
        daysRemaining: Int,
        stage: PreparationStage,
        coupleRelevance: CoupleRelevance,
        giftExpectation: GiftExpectation,
        bookingAdvised: Bool,
        recommendedLeadDays: Int,
        headlineVi: String,
        headlineEn: String,
        actionSummaryVi: String,
        actionSummaryEn: String,
        prepChecklistVi: [String],
        prepChecklistEn: [String],
        culturalTaboosVi: [String],
        culturalTaboosEn: [String],
        isMilestone: Bool = false
    ) {
        self.occasionId = occasionId
        self.titleVi = titleVi
        self.titleEn = titleEn
        self.targetDateIso = targetDateIso
        self.daysRemaining = daysRemaining
        self.stage = stage
        self.coupleRelevance = coupleRelevance
        self.giftExpectation = giftExpectation
        self.bookingAdvised = bookingAdvised
        self.recommendedLeadDays = recommendedLeadDays
        self.headlineVi = headlineVi
        self.headlineEn = headlineEn
        self.actionSummaryVi = actionSummaryVi
        self.actionSummaryEn = actionSummaryEn
        self.prepChecklistVi = prepChecklistVi
        self.prepChecklistEn = prepChecklistEn
        self.culturalTaboosVi = culturalTaboosVi
        self.culturalTaboosEn = culturalTaboosEn
        self.isMilestone = isMilestone
    }

    public func localizedTitle(isEnglish: Bool) -> String {
        isEnglish ? titleEn : titleVi
    }

    public func localizedHeadline(isEnglish: Bool) -> String {
        isEnglish ? headlineEn : headlineVi
    }

    public func localizedActionSummary(isEnglish: Bool) -> String {
        isEnglish ? actionSummaryEn : actionSummaryVi
    }

    public func localizedChecklist(isEnglish: Bool) -> [String] {
        isEnglish ? prepChecklistEn : prepChecklistVi
    }

    public func localizedTaboos(isEnglish: Bool) -> [String] {
        isEnglish ? culturalTaboosEn : culturalTaboosVi
    }
}

public struct LoveMilestoneInfo: Codable, Equatable, Identifiable, Sendable {
    public var id: String { "\(isMonthiversary ? "m" : "d")_\(value)" }
    public let value: Int
    public let titleVi: String
    public let titleEn: String
    public let targetDateIso: String
    public let daysRemaining: Int
    public let isMonthiversary: Bool

    public init(
        value: Int,
        titleVi: String,
        titleEn: String,
        targetDateIso: String,
        daysRemaining: Int,
        isMonthiversary: Bool = false
    ) {
        self.value = value
        self.titleVi = titleVi
        self.titleEn = titleEn
        self.targetDateIso = targetDateIso
        self.daysRemaining = daysRemaining
        self.isMonthiversary = isMonthiversary
    }

    public func localizedTitle(isEnglish: Bool) -> String {
        isEnglish ? titleEn : titleVi
    }
}
