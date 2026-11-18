import Foundation

public enum LoveLanguage: String, Codable, CaseIterable, Sendable, Hashable {
    case wordsOfAffirmation = "WORDS_OF_AFFIRMATION"
    case qualityTime = "QUALITY_TIME"
    case receivingGifts = "RECEIVING_GIFTS"
    case actsOfService = "ACTS_OF_SERVICE"
    case physicalTouch = "PHYSICAL_TOUCH"

    public func displayTitle(isEnglish: Bool) -> String {
        switch self {
        case .wordsOfAffirmation:
            return isEnglish ? "Words of Affirmation" : "Lời Yêu Thương"
        case .qualityTime:
            return isEnglish ? "Quality Time" : "Thời Gian Chất Lượng"
        case .receivingGifts:
            return isEnglish ? "Receiving Gifts" : "Quà Tặng Tinh Tế"
        case .actsOfService:
            return isEnglish ? "Acts of Service" : "Hành Động Quan Tâm"
        case .physicalTouch:
            return isEnglish ? "Physical Touch" : "Cử Chỉ Ấm Áp"
        }
    }
}

public enum PartnerInterestCategory: String, Codable, CaseIterable, Sendable, Hashable {
    case coffee
    case travel
    case technology
    case fashion
    case skincare
    case books
    case music
    case cinema
    case cooking
    case gaming
    case fitness
    case flowersScents = "flowers_scents"
    case handmade
    case pets

    public func displayTitle(isEnglish: Bool) -> String {
        switch self {
        case .coffee: return isEnglish ? "Coffee & Matcha" : "Cà phê & Trà"
        case .travel: return isEnglish ? "Travel" : "Du lịch"
        case .technology: return isEnglish ? "Tech & Gadgets" : "Công nghệ"
        case .fashion: return isEnglish ? "Fashion" : "Thời trang"
        case .skincare: return isEnglish ? "Skincare & Beauty" : "Chăm sóc da"
        case .books: return isEnglish ? "Books" : "Đọc sách"
        case .music: return isEnglish ? "Music" : "Âm nhạc"
        case .cinema: return isEnglish ? "Cinema" : "Phim ảnh"
        case .cooking: return isEnglish ? "Cooking & Baking" : "Nấu ăn & Làm bánh"
        case .gaming: return isEnglish ? "Gaming" : "Trò chơi"
        case .fitness: return isEnglish ? "Fitness & Yoga" : "Thể thao"
        case .flowersScents: return isEnglish ? "Flowers & Scents" : "Hoa & Nước hoa"
        case .handmade: return isEnglish ? "Handmade Crafts" : "Đồ thủ công"
        case .pets: return isEnglish ? "Pets" : "Thú cưng"
        }
    }
}

public struct PartnerSizes: Codable, Equatable, Sendable {
    public var shirtSize: String
    public var pantsOrDressSize: String
    public var shoeSize: String
    public var ringSize: String
    public var favoriteColors: [String]
    public var favoriteScents: [String]

    public init(
        shirtSize: String = "",
        pantsOrDressSize: String = "",
        shoeSize: String = "",
        ringSize: String = "",
        favoriteColors: [String] = [],
        favoriteScents: [String] = []
    ) {
        self.shirtSize = shirtSize
        self.pantsOrDressSize = pantsOrDressSize
        self.shoeSize = shoeSize
        self.ringSize = ringSize
        self.favoriteColors = favoriteColors
        self.favoriteScents = favoriteScents
    }
}

public struct PartnerFoodAndCare: Codable, Equatable, Sendable {
    public var favoriteFoods: [String]
    public var goToDrinks: String
    public var allergies: [String]
    public var dislikesOrTaboos: [String]

    public init(
        favoriteFoods: [String] = [],
        goToDrinks: String = "",
        allergies: [String] = [],
        dislikesOrTaboos: [String] = []
    ) {
        self.favoriteFoods = favoriteFoods
        self.goToDrinks = goToDrinks
        self.allergies = allergies
        self.dislikesOrTaboos = dislikesOrTaboos
    }
}

public struct SecretHintNote: Codable, Equatable, Identifiable, Sendable {
    public var id: String
    public var note: String
    public var occasionHint: String
    public var estimatedPriceVnd: Int64
    public var targetUrlOrShop: String
    public var isPurchased: Bool
    public var createdAtEpochMs: Int64

    public init(
        id: String = UUID().uuidString,
        note: String,
        occasionHint: String = "",
        estimatedPriceVnd: Int64 = 0,
        targetUrlOrShop: String = "",
        isPurchased: Bool = false,
        createdAtEpochMs: Int64 = 0
    ) {
        self.id = id
        self.note = note
        self.occasionHint = occasionHint
        self.estimatedPriceVnd = estimatedPriceVnd
        self.targetUrlOrShop = targetUrlOrShop
        self.isPurchased = isPurchased
        self.createdAtEpochMs = createdAtEpochMs
    }
}

public struct GivenGiftRecord: Codable, Equatable, Identifiable, Sendable {
    public var id: String
    public var title: String
    public var occasion: String
    public var givenDateIso: String
    public var priceVnd: Int64
    public var partnerReactionRating: Int
    public var notes: String

    public init(
        id: String = UUID().uuidString,
        title: String,
        occasion: String,
        givenDateIso: String,
        priceVnd: Int64 = 0,
        partnerReactionRating: Int = 5,
        notes: String = ""
    ) {
        self.id = id
        self.title = title
        self.occasion = occasion
        self.givenDateIso = givenDateIso
        self.priceVnd = priceVnd
        self.partnerReactionRating = partnerReactionRating
        self.notes = notes
    }
}

public struct PartnerDossier: Codable, Equatable, Sendable {
    public var scopeId: String
    public var nickname: String
    public var loveLanguages: Set<LoveLanguage>
    public var interestCategories: Set<String>
    public var freeformLikes: String
    public var sizes: PartnerSizes
    public var foodAndCare: PartnerFoodAndCare
    public var secretSurpriseNotes: [SecretHintNote]
    public var giftHistory: [GivenGiftRecord]
    public var budgetMaxVnd: Int64
    public var occasionRegion: String
    public var updatedAtEpochMs: Int64

    public init(
        scopeId: String = "guest",
        nickname: String = "",
        loveLanguages: Set<LoveLanguage> = [],
        interestCategories: Set<String> = [],
        freeformLikes: String = "",
        sizes: PartnerSizes = PartnerSizes(),
        foodAndCare: PartnerFoodAndCare = PartnerFoodAndCare(),
        secretSurpriseNotes: [SecretHintNote] = [],
        giftHistory: [GivenGiftRecord] = [],
        budgetMaxVnd: Int64 = 0,
        occasionRegion: String = "VN",
        updatedAtEpochMs: Int64 = 0
    ) {
        self.scopeId = scopeId
        self.nickname = nickname
        self.loveLanguages = loveLanguages
        self.interestCategories = interestCategories
        self.freeformLikes = freeformLikes
        self.sizes = sizes
        self.foodAndCare = foodAndCare
        self.secretSurpriseNotes = secretSurpriseNotes
        self.giftHistory = giftHistory
        self.budgetMaxVnd = budgetMaxVnd
        self.occasionRegion = occasionRegion
        self.updatedAtEpochMs = updatedAtEpochMs
    }

    public func completenessPercent() -> Int {
        var score = 0
        if !nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { score += 10 }
        if !loveLanguages.isEmpty { score += 10 }
        if !interestCategories.isEmpty { score += 15 }
        if !freeformLikes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { score += 10 }
        let hasSizes = !sizes.shirtSize.isEmpty || !sizes.shoeSize.isEmpty || !sizes.ringSize.isEmpty || !sizes.pantsOrDressSize.isEmpty
        if hasSizes { score += 10 }
        if !sizes.favoriteColors.isEmpty || !sizes.favoriteScents.isEmpty { score += 10 }
        if !foodAndCare.favoriteFoods.isEmpty || !foodAndCare.goToDrinks.isEmpty { score += 10 }
        if !foodAndCare.allergies.isEmpty || !foodAndCare.dislikesOrTaboos.isEmpty { score += 10 }
        if !secretSurpriseNotes.isEmpty { score += 10 }
        if budgetMaxVnd > 0 { score += 5 }
        return min(100, score)
    }

    public func missingProfileHints(isEnglish: Bool) -> [String] {
        var hints: [String] = []
        if loveLanguages.isEmpty {
            hints.append(isEnglish ? "Select your partner's primary love language" : "Chọn ngôn ngữ tình yêu của người ấy")
        }
        if interestCategories.isEmpty {
            hints.append(isEnglish ? "Pick 2-3 favorite interest categories" : "Chọn 2-3 sở thích nổi bật")
        }
        if sizes.shirtSize.isEmpty && sizes.shoeSize.isEmpty && sizes.ringSize.isEmpty {
            hints.append(isEnglish ? "Add clothing, shoe, or ring sizes" : "Thêm size áo, giày hoặc nhẫn")
        }
        if foodAndCare.allergies.isEmpty && foodAndCare.dislikesOrTaboos.isEmpty {
            hints.append(isEnglish ? "Note any allergies or gift taboos" : "Ghi chú dị ứng hoặc điều kiêng kỵ")
        }
        if secretSurpriseNotes.isEmpty {
            hints.append(isEnglish ? "Save a secret wish hint they mentioned" : "Lưu lại món đồ người ấy từng buột miệng thích")
        }
        return hints
    }

    public func allAvoidedKeywords() -> [String] {
        (foodAndCare.allergies + foodAndCare.dislikesOrTaboos)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
            .filter { !$0.isEmpty }
    }
}
