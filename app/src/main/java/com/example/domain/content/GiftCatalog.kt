package com.example.domain.content

import com.example.data.model.GiftIdeaEntity
import com.example.data.seed.GiftIdeasSeed

/**
 * Quy tắc hiển thị catalog quà tặng. Dòng seed lấy chữ từ [GiftIdeasSeed] theo ngôn ngữ đang chọn,
 * tra theo remoteId, nên đổi ngôn ngữ đổi chữ ngay mà không cần ghi lại Room.
 *
 * ponytail: nhãn tiếng Anh của tag tra bảng [tagNamesEn] viết tay (8 tag); tag của dòng từ remote giữ nguyên.
 */
object GiftCatalog {
    const val ALL_KEY = "Tất cả"
    const val AI_KEY = "AI Đề Xuất ✨"

    const val ROMANTIC = "Quà lãng mạn"
    const val JEWELRY = "Trang sức & Nước hoa"
    const val HANDMADE = "Kỷ vật Handmade"
    const val DATES = "Địa điểm hẹn hò"
    const val SECRET = "Bất ngờ bí mật"

    data class GiftChip(val key: String, val label: String)

    data class LocalizedGift(
        val categoryKey: String,
        val title: String,
        val badge: String,
        val tag: String,
        val description: String,
        val detailsSnippet: String,
        val actionText: String,
        val isSeed: Boolean = false,
    )

    private val chipKeys = listOf(ALL_KEY, ROMANTIC, JEWELRY, HANDMADE, DATES, SECRET, AI_KEY)

    private val categoryNamesEn = mapOf(
        ROMANTIC to "Romantic Gifts",
        JEWELRY to "Jewelry & Perfume",
        HANDMADE to "Handmade Keepsakes",
        DATES to "Date Locations",
        SECRET to "Secret Surprises",
    )

    private val tagNamesEn = mapOf(
        "Kỷ niệm" to "Memories",
        "Hẹn hò" to "Dates",
        "Đôi" to "Couples",
        "Phiêu lưu" to "Adventure",
        "Tại nhà" to "At home",
        "Dễ thương" to "Cute",
        "Thư giãn" to "Relaxing",
        "Nhẹ nhàng" to "Gentle",
    )

    private val seedsByRemoteId = GiftIdeasSeed.all.associateBy { it.remoteId }

    fun chips(isEnglish: Boolean): List<GiftChip> = chipKeys.map { GiftChip(it, categoryLabel(it, isEnglish)) }

    fun filterByChip(ideas: List<GiftIdeaEntity>, key: String): List<GiftIdeaEntity> = when (key) {
        ALL_KEY -> ideas
        AI_KEY -> ideas.filter { it.isAiGenerated }
        else -> ideas.filter { it.category == key }
    }

    fun localized(idea: GiftIdeaEntity, isEnglish: Boolean): LocalizedGift {
        val tag = if (isEnglish) tagNamesEn[idea.tag] ?: idea.tag else idea.tag
        val seed = seedsByRemoteId[idea.remoteId] ?: return LocalizedGift(
            categoryKey = idea.category,
            title = idea.title,
            badge = idea.badgeText,
            tag = tag,
            description = idea.description,
            detailsSnippet = idea.detailsSnippet,
            actionText = idea.actionText,
        )
        return LocalizedGift(
            categoryKey = seed.category,
            title = if (isEnglish) seed.titleEn else seed.titleVi,
            badge = if (isEnglish) seed.badgeTextEn else seed.badgeTextVi,
            tag = tag,
            description = if (isEnglish) seed.descriptionEn else seed.descriptionVi,
            detailsSnippet = if (isEnglish) seed.detailsSnippetEn else seed.detailsSnippetVi,
            actionText = if (isEnglish) seed.actionTextEn else seed.actionTextVi,
            isSeed = true,
        )
    }

    fun categoryLabel(key: String, isEnglish: Boolean): String = when {
        key == AI_KEY -> if (isEnglish) "Personalized ✨" else "Cá nhân hóa ✨"
        !isEnglish -> key
        key == ALL_KEY -> "All"
        else -> categoryNamesEn[key] ?: key
    }

    fun priceLabel(priceRange: String, isEnglish: Boolean): String? {
        val (en, vi) = when (priceRange) {
            GiftIdeasSeed.PriceRange.UNDER_200K -> "under 200,000 ₫" to "dưới 200.000 ₫"
            GiftIdeasSeed.PriceRange.RANGE_200_500K -> "200,000–500,000 ₫" to "200.000–500.000 ₫"
            GiftIdeasSeed.PriceRange.RANGE_500K_1M -> "500,000–1,000,000 ₫" to "500.000–1.000.000 ₫"
            GiftIdeasSeed.PriceRange.OVER_1M -> "over 1,000,000 ₫" to "trên 1.000.000 ₫"
            else -> return null
        }
        return if (isEnglish) "Indicative price: $en" else "Giá tham khảo: $vi"
    }

    /**
     * ponytail: dòng cũ khớp theo URL ảnh rồi theo tiêu đề hiện tại; dòng mà cả ảnh lẫn tiêu đề đã đổi thì không khớp.
     */
    fun seedRemoteIdFor(legacy: GiftIdeaEntity): String? =
        GiftIdeasSeed.all.firstOrNull { it.imageUrl == legacy.imageUrl }?.remoteId
            ?: GiftIdeasSeed.all.firstOrNull { legacy.title == it.titleVi || legacy.title == it.titleEn }?.remoteId
}
