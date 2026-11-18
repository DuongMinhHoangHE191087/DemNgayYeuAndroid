package com.example.shared.dossier

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class LoveLanguage(
    val titleVi: String,
    val titleEn: String,
) {
    WORDS_OF_AFFIRMATION("Lời yêu thương", "Words of Affirmation"),
    QUALITY_TIME("Thời gian bên nhau", "Quality Time"),
    RECEIVING_GIFTS("Quà tặng tinh tế", "Receiving Gifts"),
    ACTS_OF_SERVICE("Hành động quan tâm", "Acts of Service"),
    PHYSICAL_TOUCH("Cái ôm & cử chỉ", "Physical Touch"),
}

@Serializable
data class PartnerSizes(
    val shirtSize: String = "",
    val pantsOrDressSize: String = "",
    val shoeSize: String = "",
    val ringSize: String = "",
    val favoriteColors: List<String> = emptyList(),
    val favoriteScents: List<String> = emptyList(),
)

@Serializable
data class PartnerFoodAndCare(
    val favoriteFoods: List<String> = emptyList(),
    val goToDrinks: String = "",
    val allergies: List<String> = emptyList(),
    val dislikesOrTaboos: List<String> = emptyList(),
)

@Serializable
data class SecretHintNote(
    val id: String,
    val note: String,
    val occasionHint: String = "",
    val estimatedPriceVnd: Long = 0L,
    val targetUrlOrShop: String = "",
    val isPurchased: Boolean = false,
    val createdAtEpochMs: Long = 0L,
)

@Serializable
data class GivenGiftRecord(
    val id: String,
    val title: String,
    val occasion: String = "",
    val givenDateIso: String = "",
    val priceVnd: Long = 0L,
    val partnerReactionRating: Int = 5,
    val notes: String = "",
)

@Serializable
data class PartnerDossier(
    val scopeId: String = "guest",
    val nickname: String = "",
    val loveLanguages: Set<LoveLanguage> = emptySet(),
    val interestCategories: Set<String> = emptySet(),
    val freeformLikes: String = "",
    val sizes: PartnerSizes = PartnerSizes(),
    val foodAndCare: PartnerFoodAndCare = PartnerFoodAndCare(),
    val secretSurpriseNotes: List<SecretHintNote> = emptyList(),
    val giftHistory: List<GivenGiftRecord> = emptyList(),
    val budgetMaxVnd: Long = 0L,
    val occasionRegion: String = "VN",
    val updatedAtEpochMs: Long = 0L,
)

data class InterestCategoryMeta(
    val key: String,
    val labelVi: String,
    val labelEn: String,
    val emoji: String,
    val aliases: Set<String>,
)

object PartnerInterestCatalog {
    val CATEGORIES: List<InterestCategoryMeta> = listOf(
        InterestCategoryMeta("coffee", "Cà phê & Trà", "Coffee & Tea", "☕", setOf("coffee", "ca phe", "cafe", "tra", "tea", "matcha")),
        InterestCategoryMeta("travel", "Du lịch", "Travel", "✈️", setOf("travel", "du lich", "phuot", "camp", "camping", "staycation")),
        InterestCategoryMeta("technology", "Công nghệ", "Technology", "🎧", setOf("technology", "tech", "cong nghe", "gadget")),
        InterestCategoryMeta("fashion", "Thời trang", "Fashion", "👗", setOf("fashion", "thoi trang", "trang suc", "jewelry", "jewellery", "dong ho")),
        InterestCategoryMeta("skincare", "Chăm sóc da", "Skincare", "🧴", setOf("skincare", "cham soc da", "my pham", "beauty", "lam dep", "spa")),
        InterestCategoryMeta("books", "Sách & Viết", "Books", "📚", setOf("books", "book", "sach", "doc sach", "journaling")),
        InterestCategoryMeta("music", "Âm nhạc", "Music", "🎵", setOf("music", "am nhac", "nhac", "concert", "vinyl")),
        InterestCategoryMeta("cinema", "Phim ảnh", "Cinema", "🎬", setOf("cinema", "phim", "dien anh", "movie", "movies", "rap chieu phim")),
        InterestCategoryMeta("cooking", "Nấu ăn & Bánh", "Cooking", "🍳", setOf("cooking", "nau an", "lam banh", "baking", "food", "am thuc")),
        InterestCategoryMeta("gaming", "Game & Board Game", "Gaming", "🎮", setOf("gaming", "game", "choi game", "board game", "boardgame")),
        InterestCategoryMeta("fitness", "Thể thao & Yoga", "Fitness", "🏃", setOf("fitness", "the thao", "gym", "yoga", "running", "cycling", "xe dap")),
        InterestCategoryMeta("flowers_scents", "Hoa & Nước hoa", "Flowers & Scents", "💐", setOf("flowers_scents", "flowers", "hoa", "nuoc hoa", "perfume", "nen thom", "scents")),
        InterestCategoryMeta("handmade", "Thủ công & Kỷ vật", "Handmade", "🧶", setOf("handmade", "thu cong", "diy", "ky vat", "tranh", "anh")),
        InterestCategoryMeta("pets", "Thú cưng", "Pets", "🐾", setOf("pets", "pet", "thu cung", "meo", "cho", "cat", "dog")),
    )

    val STANDARD_INTEREST_KEYS: List<String> = CATEGORIES.map { it.key }

    private val byKey: Map<String, InterestCategoryMeta> = CATEGORIES.associateBy { it.key }

    fun metaFor(key: String): InterestCategoryMeta? = byKey[normalizeInterestKey(key)]

    fun normalizeInterestKey(raw: String): String {
        val cleaned = stripDiacritics(raw.trim().lowercase())
        if (cleaned.isEmpty()) return ""
        if (cleaned in byKey) return cleaned
        return CATEGORIES.firstOrNull { meta ->
            cleaned in meta.aliases || meta.aliases.any { alias -> cleaned.contains(alias) }
        }?.key ?: cleaned
    }

    fun normalizeInterestsCsv(csv: String): Set<String> =
        csv.split(',', ';', '|', '\n')
            .asSequence()
            .map { normalizeInterestKey(it) }
            .filter { it.isNotEmpty() }
            .toSet()

    fun stripDiacritics(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch) {
                    'à', 'á', 'ạ', 'ả', 'ã', 'â', 'ầ', 'ấ', 'ậ', 'ẩ', 'ẫ', 'ă', 'ằ', 'ắ', 'ặ', 'ẳ', 'ẵ' -> 'a'
                    'À', 'Á', 'Ạ', 'Ả', 'Ã', 'Â', 'Ầ', 'Ấ', 'Ậ', 'Ẩ', 'Ẫ', 'Ă', 'Ằ', 'Ắ', 'Ặ', 'Ẳ', 'Ẵ' -> 'A'
                    'è', 'é', 'ẹ', 'ẻ', 'ẽ', 'ê', 'ề', 'ế', 'ệ', 'ể', 'ễ' -> 'e'
                    'È', 'É', 'Ẹ', 'Ẻ', 'Ẽ', 'Ê', 'Ề', 'Ế', 'Ệ', 'Ể', 'Ễ' -> 'E'
                    'ì', 'í', 'ị', 'ỉ', 'ĩ' -> 'i'
                    'Ì', 'Í', 'Ị', 'Ỉ', 'Ĩ' -> 'I'
                    'ò', 'ó', 'ọ', 'ỏ', 'õ', 'ô', 'ồ', 'ố', 'ộ', 'ổ', 'ỗ', 'ơ', 'ờ', 'ớ', 'ợ', 'ở', 'ỡ' -> 'o'
                    'Ò', 'Ó', 'Ọ', 'Ỏ', 'Õ', 'Ô', 'Ồ', 'Ố', 'Ộ', 'Ổ', 'Ỗ', 'Ơ', 'Ờ', 'Ớ', 'Ợ', 'Ở', 'Ỡ' -> 'O'
                    'ù', 'ú', 'ụ', 'ủ', 'ũ', 'ư', 'ừ', 'ứ', 'ự', 'ử', 'ữ' -> 'u'
                    'Ù', 'Ú', 'Ụ', 'Ủ', 'Ũ', 'Ư', 'Ừ', 'Ứ', 'Ự', 'Ử', 'Ữ' -> 'U'
                    'ỳ', 'ý', 'ỵ', 'ỷ', 'ỹ' -> 'y'
                    'Ỳ', 'Ý', 'Ỵ', 'Ỷ', 'Ỹ' -> 'Y'
                    'đ' -> 'd'
                    'Đ' -> 'D'
                    else -> ch
                },
            )
        }
    }
}

fun PartnerDossier.hasClothingOrRingSizes(): Boolean =
    sizes.shirtSize.isNotBlank() ||
        sizes.pantsOrDressSize.isNotBlank() ||
        sizes.shoeSize.isNotBlank() ||
        sizes.ringSize.isNotBlank()

fun PartnerDossier.completenessPercent(): Int {
    var score = 0
    if (nickname.isNotBlank()) score += 10
    if (loveLanguages.isNotEmpty()) score += 10
    if (interestCategories.isNotEmpty()) score += 15
    if (freeformLikes.isNotBlank()) score += 10
    if (hasClothingOrRingSizes()) score += 10
    if (sizes.favoriteColors.isNotEmpty() || sizes.favoriteScents.isNotEmpty()) score += 10
    if (foodAndCare.favoriteFoods.isNotEmpty() || foodAndCare.goToDrinks.isNotBlank()) score += 10
    if (foodAndCare.allergies.isNotEmpty() || foodAndCare.dislikesOrTaboos.isNotEmpty()) score += 10
    if (secretSurpriseNotes.isNotEmpty()) score += 10
    if (budgetMaxVnd > 0L) score += 5
    return score.coerceIn(0, 100)
}

fun PartnerDossier.missingProfileHints(isEnglish: Boolean): List<String> {
    val hints = mutableListOf<String>()
    if (nickname.isBlank()) {
        hints += if (isEnglish) "Add their nickname for warmer notes" else "Thêm biệt danh để xưng hô tự nhiên trong lời chúc"
    }
    if (loveLanguages.isEmpty()) {
        hints += if (isEnglish) "Select how they feel loved most" else "Chọn cách người ấy cảm nhận tình yêu rõ nhất"
    }
    if (interestCategories.isEmpty()) {
        hints += if (isEnglish) "Pick a few of their everyday interests" else "Chọn vài sở thích quen thuộc của người ấy"
    }
    if (freeformLikes.isBlank()) {
        hints += if (isEnglish) "Note small habits or items they mention" else "Ghi vài món đồ hoặc thói quen người ấy hay nhắc"
    }
    if (!hasClothingOrRingSizes()) {
        hints += if (isEnglish) "Save shirt, shoe, or ring sizes for easy gifting" else "Lưu size áo, giày hoặc nhẫn để chọn quà vừa vặn"
    }
    if (sizes.favoriteColors.isEmpty() && sizes.favoriteScents.isEmpty()) {
        hints += if (isEnglish) "Add favorite colors or scents" else "Thêm màu sắc hoặc mùi hương người ấy chuộng"
    }
    if (foodAndCare.favoriteFoods.isEmpty() && foodAndCare.goToDrinks.isBlank()) {
        hints += if (isEnglish) "Record favorite dishes and go-to drink" else "Ghi món ăn ruột và đồ uống hay gọi"
    }
    if (foodAndCare.allergies.isEmpty() && foodAndCare.dislikesOrTaboos.isEmpty()) {
        hints += if (isEnglish) "List allergies or things they dislike" else "Ghi chú dị ứng hoặc điều người ấy không thích"
    }
    if (secretSurpriseNotes.isEmpty()) {
        hints += if (isEnglish) "Jot down surprise hints when they drop one" else "Lưu lại gợi ý bí mật khi người ấy vô tình nhắc"
    }
    if (budgetMaxVnd <= 0L) {
        hints += if (isEnglish) "Set a target budget to narrow gift ideas" else "Đặt ngân sách dự kiến để lọc quà phù hợp"
    }
    return hints
}

fun PartnerDossier.effectiveLikesCsv(): String {
    val combined = buildList {
        addAll(interestCategories.map { it.trim() }.filter { it.isNotEmpty() })
        freeformLikes.split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token -> if (token !in this) add(token) }
    }
    return combined.joinToString(", ")
}

object PartnerDossierJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(dossier: PartnerDossier): String =
        json.encodeToString(PartnerDossier.serializer(), dossier)

    fun decodeOrNull(raw: String): PartnerDossier? =
        if (raw.isBlank()) null else runCatching {
            json.decodeFromString(PartnerDossier.serializer(), raw)
        }.getOrNull()

    fun decodeOrDefault(raw: String, fallbackScopeId: String = "guest"): PartnerDossier =
        decodeOrNull(raw) ?: PartnerDossier(scopeId = fallbackScopeId)
}

fun PartnerDossier.toJsonString(): String = PartnerDossierJson.encode(this)

fun String.toPartnerDossierOrNull(): PartnerDossier? = PartnerDossierJson.decodeOrNull(this)
