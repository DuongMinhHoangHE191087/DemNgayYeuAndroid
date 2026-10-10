package com.example.domain.content

import com.example.data.seed.GiftIdeasSeed
import com.example.ui.util.AppLanguage
import org.junit.Test

class GiftCatalogTest {

  private val romantic = "Quà lãng mạn"
  private val jewelry = "Trang sức & Nước hoa"
  private val handmade = "Kỷ vật Handmade"
  private val dates = "Địa điểm hẹn hò"
  private val secret = "Bất ngờ bí mật"

  private fun seed(index: Int) = GiftIdeasSeed.all[index].toEntity(AppLanguage.VI)

  @Test
  fun chips_keysDoNotChangeWithLanguage() {
    assert(GiftCatalog.chips(false).map { it.key } == GiftCatalog.chips(true).map { it.key }) {
      "chip keys must not change with language, or the selected chip is lost on a language switch"
    }
  }

  @Test
  fun everySeedCategory_isAChip_andEveryCategoryChipSelectsASeed() {
    val keys = GiftCatalog.chips(false).map { it.key }.toSet()
    GiftIdeasSeed.all.forEach { seed ->
      assert(seed.category in keys) { "seed '${seed.titleVi}' category '${seed.category}' selects no chip" }
    }
    listOf(romantic, jewelry, handmade, dates, secret).forEach { key ->
      assert(GiftIdeasSeed.all.any { it.category == key }) { "chip '$key' selects no seed" }
    }
  }

  @Test
  fun filterByChip_matchesCategoryExactly_andAllAndAiAreSpecial() {
    val ideas = listOf(
      seed(0).copy(remoteId = "a", category = romantic),
      seed(1).copy(remoteId = "b", category = "Quà lãng mạn đặc biệt"),
      seed(2).copy(remoteId = "c", category = romantic, isAiGenerated = true),
    )
    assert(GiftCatalog.filterByChip(ideas, romantic).map { it.remoteId } == listOf("a", "c")) {
      "a chip must match its category exactly, not as a substring"
    }
    assert(GiftCatalog.filterByChip(ideas, GiftCatalog.ALL_KEY).size == 3) { "ALL keeps every row" }
    assert(GiftCatalog.filterByChip(ideas, GiftCatalog.AI_KEY).map { it.remoteId } == listOf("c")) {
      "AI keeps only AI-generated rows"
    }
  }

  @Test
  fun localized_seedRowFollowsTheRequestedLanguage() {
    val rose = GiftIdeasSeed.all.first()
    val en = GiftCatalog.localized(rose.toEntity(AppLanguage.VI), isEnglish = true)
    assert(en.title == rose.titleEn) { "EN title expected '${rose.titleEn}', got '${en.title}'" }
    assert(en.detailsSnippet == rose.detailsSnippetEn) { "EN snippet expected, got '${en.detailsSnippet}'" }
    assert(en.categoryKey == rose.category) { "the category key must be the chip key" }
  }

  @Test
  fun localized_nonSeedRowKeepsItsStoredText() {
    val remote = seed(0).copy(remoteId = "doc_1", title = "Nến thơm", tag = "Riêng tư")
    val en = GiftCatalog.localized(remote, isEnglish = true)
    assert(en.title == "Nến thơm") { "remote rows show stored text, got '${en.title}'" }
    assert(en.tag == "Riêng tư") { "an unmapped remote tag stays as stored, got '${en.tag}'" }
  }

  @Test
  fun localized_flagsOnlySeedRowsForTheIllustrationCaption() {
    assert(GiftCatalog.localized(seed(0), isEnglish = false).isSeed) { "a seed row shows the illustration caption" }
    assert(!GiftCatalog.localized(seed(0).copy(remoteId = "doc_1"), isEnglish = false).isSeed) {
      "a row that is not a seed has its own photo and no illustration caption"
    }
  }

  @Test
  fun englishTags_areAsciiForEverySeed() {
    GiftIdeasSeed.all.forEach { seed ->
      val tag = GiftCatalog.localized(seed.toEntity(AppLanguage.VI), isEnglish = true).tag
      assert(tag.all { it.code < 128 }) { "English tag '$tag' for '${seed.titleEn}' still has Vietnamese text" }
    }
  }

  @Test
  fun priceLabel_isIndicativeVndRange_andNullWhenUnknown() {
    assert(GiftCatalog.priceLabel(GiftIdeasSeed.PriceRange.RANGE_200_500K, isEnglish = true) ==
      "Indicative price: 200,000–500,000 ₫") { "EN price label" }
    assert(GiftCatalog.priceLabel(GiftIdeasSeed.PriceRange.RANGE_200_500K, isEnglish = false) ==
      "Giá tham khảo: 200.000–500.000 ₫") { "VI price label" }
    assert(GiftCatalog.priceLabel("", isEnglish = true) == null) { "a blank price shows nothing" }
    assert(GiftCatalog.priceLabel("unknown", isEnglish = false) == null) { "an unknown price shows nothing" }
  }

  @Test
  fun categoryLabel_isEnglishForEachChip_andTheKeyOtherwise() {
    assert(GiftCatalog.categoryLabel(romantic, isEnglish = true) == "Romantic Gifts")
    assert(GiftCatalog.categoryLabel(jewelry, isEnglish = true) == "Jewelry & Perfume")
    assert(GiftCatalog.categoryLabel(handmade, isEnglish = true) == "Handmade Keepsakes")
    assert(GiftCatalog.categoryLabel(dates, isEnglish = true) == "Date Locations")
    assert(GiftCatalog.categoryLabel(secret, isEnglish = true) == "Secret Surprises")
    assert(GiftCatalog.categoryLabel(romantic, isEnglish = false) == romantic)
  }

  @Test
  fun categoryLabel_aiChipIsPersonalizedInBothLanguages() {
    assert(GiftCatalog.categoryLabel(GiftCatalog.AI_KEY, isEnglish = true) == "Personalized ✨") {
      "the AI chip reads as personalized in English"
    }
    assert(GiftCatalog.categoryLabel(GiftCatalog.AI_KEY, isEnglish = false) == "Cá nhân hóa ✨") {
      "the AI chip reads as personalized in Vietnamese, not as the raw key"
    }
  }

  @Test
  fun seedCopy_claimsNothingTheAppCannotBack() {
    val banned = listOf(
      "bán chạy", "bestseller", "yêu thích nhất", "được yêu thích", "fan favorite", "most loved",
      "dị ứng", "allerg", "925", "bạc ", "bảo hành", "warranty", "chống nước", "water-resistant",
      "kèm", "included", "combo", "bắp", "dụng cụ", "tools", "thiệp", "handwritten",
      "đóng khung", "framed", "lò vi sóng", "microwave", "dishwasher", "máy rửa",
      "liên kết", "partner", "bìa da", "leather",
    )
    val numeric = Regex(
      "\\d+[\\d.]*\\s*(-\\s*\\d+\\s*)?(\\+\\s*)?(giờ|ngày|năm|trang|m\\b|m2|hours?|days?|years?|pages?)",
      RegexOption.IGNORE_CASE,
    )
    val copy = GiftIdeasSeed.all.flatMap { s ->
      listOf(
        s.titleVi, s.titleEn, s.badgeTextVi, s.badgeTextEn,
        s.descriptionVi, s.descriptionEn, s.detailsSnippetVi, s.detailsSnippetEn,
      )
    }
    val hits = copy.filter { text -> banned.any { text.contains(it, ignoreCase = true) } || numeric.containsMatchIn(text) }
    assert(hits.isEmpty()) { "unsupported claims left in seed copy: $hits" }
  }

  @Test
  fun seedRemoteIds_areTwentyDistinctNonBlankIdentities() {
    val ids = GiftIdeasSeed.all.map { it.remoteId }
    assert(ids.toSet().size == 20 && ids.none { it.isBlank() }) { "20 distinct, non-blank ids expected, got $ids" }
  }

  @Test
  fun seedRemoteIdFor_adoptsLegacyRowsByImageOrTitle_andLeavesStrangersAlone() {
    val rose = GiftIdeasSeed.all.first()
    val oldTitle = rose.toEntity(AppLanguage.VI).copy(remoteId = "", title = "Bó Hoa Hồng Sáp Kèm Thiệp Thư Tay")
    assert(GiftCatalog.seedRemoteIdFor(oldTitle) == rose.remoteId) {
      "a legacy row matches by image URL even after its title changed"
    }
    val byTitle = rose.toEntity(AppLanguage.EN).copy(remoteId = "", imageUrl = "")
    assert(GiftCatalog.seedRemoteIdFor(byTitle) == rose.remoteId) {
      "a legacy row without an image URL matches by its current title"
    }
    val stranger = rose.toEntity(AppLanguage.VI).copy(remoteId = "", title = "Quà riêng", imageUrl = "https://example.com/a.jpg")
    assert(GiftCatalog.seedRemoteIdFor(stranger) == null) { "an unmatched legacy row is left alone" }
  }
}
