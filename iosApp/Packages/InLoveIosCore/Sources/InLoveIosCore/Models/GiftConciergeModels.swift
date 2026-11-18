import Foundation

public enum RelationshipTone: String, Codable, CaseIterable, Sendable, Hashable {
    case sweet = "SWEET"
    case playful = "PLAYFUL"
    case deeplyRomantic = "DEEPLY_ROMANTIC"
    case thoughtful = "THOUGHTFUL"

    public func displayTitle(isEnglish: Bool) -> String {
        switch self {
        case .sweet: return isEnglish ? "Sweet" : "Ngọt ngào"
        case .playful: return isEnglish ? "Playful" : "Hóm hỉnh"
        case .deeplyRomantic: return isEnglish ? "Deeply Romantic" : "Sâu lắng"
        case .thoughtful: return isEnglish ? "Thoughtful" : "Tinh tế & Chân thành"
        }
    }
}

public enum AdviceSource: String, Codable, Sendable {
    case cloudAi = "CLOUD_AI"
    case smartLocalEngine = "SMART_LOCAL_ENGINE"

    public func badgeLabel(isEnglish: Bool) -> String {
        switch self {
        case .cloudAi: return isEnglish ? "Cloud AI Concierge" : "Trợ lý AI Đám mây"
        case .smartLocalEngine: return isEnglish ? "Smart Offline Engine" : "Gợi ý thông minh Offline"
        }
    }
}

public struct RichGiftRecommendation: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let category: String
    public let priceRangeLabel: String
    public let estimatedMinVnd: Int64
    public let estimatedMaxVnd: Int64
    public let matchScore: Int
    public let reason: String
    public let preparationPlan: String
    public let personalizationHint: String
    public let culturalCaution: String?
    public let matchedInterests: [String]
    public let matchedLoveLanguages: [LoveLanguage]
    public let whereToFind: String
    public let leadTimeDays: Int

    public init(
        id: String,
        title: String,
        category: String,
        priceRangeLabel: String,
        estimatedMinVnd: Int64 = 0,
        estimatedMaxVnd: Int64 = 0,
        matchScore: Int = 0,
        reason: String,
        preparationPlan: String,
        personalizationHint: String = "",
        culturalCaution: String? = nil,
        matchedInterests: [String] = [],
        matchedLoveLanguages: [LoveLanguage] = [],
        whereToFind: String = "",
        leadTimeDays: Int = 1
    ) {
        self.id = id
        self.title = title
        self.category = category
        self.priceRangeLabel = priceRangeLabel
        self.estimatedMinVnd = estimatedMinVnd
        self.estimatedMaxVnd = estimatedMaxVnd
        self.matchScore = matchScore
        self.reason = reason
        self.preparationPlan = preparationPlan
        self.personalizationHint = personalizationHint
        self.culturalCaution = culturalCaution
        self.matchedInterests = matchedInterests
        self.matchedLoveLanguages = matchedLoveLanguages
        self.whereToFind = whereToFind
        self.leadTimeDays = leadTimeDays
    }
}

public struct LoveNoteDraft: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let tone: RelationshipTone
    public let title: String
    public let message: String
    public let occasionLabel: String

    public init(
        id: String = UUID().uuidString,
        tone: RelationshipTone,
        title: String,
        message: String,
        occasionLabel: String = ""
    ) {
        self.id = id
        self.tone = tone
        self.title = title
        self.message = message
        self.occasionLabel = occasionLabel
    }
}

public struct AiGiftRequest: Codable, Equatable, Sendable {
    public let dossier: PartnerDossier
    public let occasionId: String
    public let occasionTitle: String
    public let daysUntilOccasion: Int?
    public let loveDays: Int
    public let tone: RelationshipTone
    public let isEnglish: Bool
    public let maxRecommendations: Int
    public let excludedGiftTitles: Set<String>

    public init(
        dossier: PartnerDossier,
        occasionId: String = "valentine",
        occasionTitle: String,
        daysUntilOccasion: Int? = nil,
        loveDays: Int = 0,
        tone: RelationshipTone = .sweet,
        isEnglish: Bool = false,
        maxRecommendations: Int = 5,
        excludedGiftTitles: Set<String> = []
    ) {
        self.dossier = dossier
        self.occasionId = occasionId
        self.occasionTitle = occasionTitle
        self.daysUntilOccasion = daysUntilOccasion
        self.loveDays = loveDays
        self.tone = tone
        self.isEnglish = isEnglish
        self.maxRecommendations = maxRecommendations
        self.excludedGiftTitles = excludedGiftTitles
    }
}

public struct AiGiftAdviceBundle: Codable, Equatable, Sendable {
    public let source: AdviceSource
    public let occasionId: String
    public let occasionTitle: String
    public let summaryHeadline: String
    public let recommendations: [RichGiftRecommendation]
    public let loveNotes: [LoveNoteDraft]
    public let tabooWarnings: [String]
    public let prepTimeline: [String]
    public let usedFreeAllowance: Bool
    public let remainingFreeAllowance: Int?
    public let generatedAtEpochMs: Int64

    public init(
        source: AdviceSource,
        occasionId: String,
        occasionTitle: String,
        summaryHeadline: String,
        recommendations: [RichGiftRecommendation],
        loveNotes: [LoveNoteDraft],
        tabooWarnings: [String] = [],
        prepTimeline: [String] = [],
        usedFreeAllowance: Bool = false,
        remainingFreeAllowance: Int? = nil,
        generatedAtEpochMs: Int64 = 0
    ) {
        self.source = source
        self.occasionId = occasionId
        self.occasionTitle = occasionTitle
        self.summaryHeadline = summaryHeadline
        self.recommendations = recommendations
        self.loveNotes = loveNotes
        self.tabooWarnings = tabooWarnings
        self.prepTimeline = prepTimeline
        self.usedFreeAllowance = usedFreeAllowance
        self.remainingFreeAllowance = remainingFreeAllowance
        self.generatedAtEpochMs = generatedAtEpochMs
    }
}
