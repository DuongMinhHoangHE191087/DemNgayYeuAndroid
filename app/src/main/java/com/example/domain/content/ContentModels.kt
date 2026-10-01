package com.example.domain.content

/**
 * Domain-level representation of a single gift-catalog entry, already resolved to one
 * language string (no titleVi/titleEn split here — see [ContentRepository.getGiftCatalog]
 * KDoc for why: the live Firestore `gift_ideas` collection this is sourced from already
 * stores a single `title` field per document, same as [com.example.data.repository
 * .InLoveRepository.fetchDynamicGiftIdeasFromFirestore] has always read it as).
 *
 * Structurally mirrors [com.example.data.model.GiftIdeaEntity] minus the Room-only
 * `id`/`isFavorited` fields, so mapping to/from the entity is a straight field copy —
 * see `GiftCatalogItem.toEntity()` in `com.example.data.content.ContentMappers`.
 */
data class GiftCatalogItem(
    val remoteId: String = "",
    val title: String,
    val category: String,
    val badgeText: String,
    val tag: String,
    val description: String,
    val imageUrl: String,
    val detailsSnippet: String = "",
    val actionText: String = "",
    val isAiGenerated: Boolean = false,
    val targetInterests: String = "",
    val suggestedOccasion: String = "",
    val priceRange: String = ""
)

/** How a holiday's next occurrence is computed — mirrors the three shapes already used by
 * `com.example.data.seed.VietnameseHolidays` / `WesternHolidays`. */
enum class HolidayKind { FIXED, LUNAR, RULE_BASED }

/**
 * Domain-level representation of a single calendar holiday. Unlike [GiftCatalogItem], both
 * language variants are kept together here (titleVi + titleEn), matching how the original
 * hardcoded `FixedHoliday`/`LunarHoliday`/`RuleBasedHoliday` records were always authored —
 * the caller picks which title to render based on the requested [com.example.ui.util
 * .AppLanguage], exactly as `InLoveRepository.buildHolidayAnniversaries` already did before
 * this change.
 *
 * Only the fields relevant to [kind] are populated; the others stay at their default (null /
 * empty). This keeps a single flat, Firestore-console-friendly document shape (one holiday =
 * one document, like the existing `gift_ideas` / `milestone_presets` collections) instead of
 * a sealed-class hierarchy that would need custom JSON adapters.
 */
data class HolidayCalendarItem(
    val kind: HolidayKind,
    val titleVi: String,
    val titleEn: String,
    val emoji: String,
    val suggestedGiftCategory: String = "",
    val isCoupleRelevant: Boolean = false,
    // FIXED only
    val month: Int? = null,
    val day: Int? = null,
    // LUNAR only: calendar year (as a string key, e.g. "2026") -> ISO-8601 date ("2026-02-17")
    val datesByYear: Map<String, String> = emptyMap(),
    // RULE_BASED only: "Nth <ruleDayOfWeek> of <ruleMonth>", e.g. month=5, SUNDAY, 2nd = Mother's Day
    val ruleMonth: Int? = null,
    val ruleDayOfWeek: String? = null,
    val ruleOccurrence: Int? = null
)
