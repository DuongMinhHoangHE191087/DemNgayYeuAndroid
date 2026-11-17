package com.example.domain.usecase

import com.example.data.model.GiftIdeaEntity
import com.example.data.seed.GiftIdeasSeed
import com.example.domain.usecase.GetPersonalizedGiftSuggestionsUseCase.REGION_INTL
import com.example.domain.usecase.GetPersonalizedGiftSuggestionsUseCase.REGION_VN
import com.example.ui.util.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Đặc tả hành vi hiện tại (java.time) của GetPersonalizedGiftSuggestionsUseCase.
 * B-48 đổi sang kotlinx-datetime phải giữ nguyên các test này.
 */
class GetPersonalizedGiftSuggestionsUseCaseTest {

    private fun idea(occasion: String) = GiftIdeaEntity(
        title = "Quà thử",
        category = "Quà lãng mạn",
        badgeText = "",
        tag = "",
        description = "",
        imageUrl = "",
        suggestedOccasion = occasion,
    )

    private fun suggest(ideas: List<GiftIdeaEntity>, today: LocalDate, region: String) =
        GetPersonalizedGiftSuggestionsUseCase(
            ideas = ideas,
            interests = emptySet(),
            budgetMaxVnd = null,
            birthdays = emptyList(),
            loveStart = null,
            today = today,
            isEnglish = false,
            region = region,
        )

    @Test
    fun nearestUpcomingOccasion_birthdayTomorrow_winsOverFarHoliday() {
        // Sinh nhật ngày mai (1 ngày) thắng 20/10 (6 ngày).
        val upcoming = GetPersonalizedGiftSuggestionsUseCase.nearestUpcomingOccasion(
            today = LocalDate.of(2026, 10, 14),
            birthdays = listOf(LocalDate.of(1995, 10, 15)),
            loveStart = null,
        )
        assertEquals(UpcomingOccasion("Sinh nhật", LocalDate.of(2026, 10, 15), 1), upcoming)
    }

    @Test
    fun nearestUpcomingOccasion_feb29Birthday_clampsInNonLeapYear() {
        // 2026 không nhuận: 29/02 được kẹp về 28/02, cách 8 ngày.
        val upcoming = GetPersonalizedGiftSuggestionsUseCase.nearestUpcomingOccasion(
            today = LocalDate.of(2026, 2, 20),
            birthdays = listOf(LocalDate.of(2000, 2, 29)),
            loveStart = null,
            region = REGION_INTL,
        )
        assertEquals(UpcomingOccasion("Sinh nhật", LocalDate.of(2026, 2, 28), 8), upcoming)
    }

    @Test
    fun nearestUpcomingOccasion_passedThisYear_rollsToNextYear() {
        // Sinh nhật 10/01 đã qua trong 2026 nên dịp gần nhất là 10/01/2027, cách 15 ngày.
        val upcoming = GetPersonalizedGiftSuggestionsUseCase.nearestUpcomingOccasion(
            today = LocalDate.of(2026, 12, 26),
            birthdays = listOf(LocalDate.of(2000, 1, 10)),
            loveStart = null,
        )
        assertEquals(UpcomingOccasion("Sinh nhật", LocalDate.of(2027, 1, 10), 15), upcoming)
    }

    @Test
    fun invoke_vnRegion_includesVietnameseHolidayOccasion() {
        // Vùng VN: 20/10 (6 ngày tới) là dịp nên quà gắn 20/10 được tính là hợp dịp.
        val result = suggest(listOf(idea("20/10")), LocalDate.of(2026, 10, 14), REGION_VN)
        assertEquals(1, result.size)
        assertTrue(result[0].fitsOccasion)
    }

    @Test
    fun invoke_intlRegion_usesWesternHolidays() {
        // Vùng INTL: Giáng Sinh (lễ Tây, cách 14 ngày) là dịp nên quà gắn Giáng Sinh được tính là hợp dịp.
        val christmas = suggest(listOf(idea("Giáng Sinh")), LocalDate.of(2026, 12, 10), REGION_INTL)
        assertEquals(1, christmas.size)
        assertTrue(christmas[0].fitsOccasion)

        // Vùng INTL bỏ 20/10 (chỉ có ở VN) nên quà 20/10 không hợp dịp nào.
        val vnOnly = suggest(listOf(idea("20/10")), LocalDate.of(2026, 10, 14), REGION_INTL)
        assertTrue(vnOnly.isEmpty())
    }

    @Test
    fun invoke_noInterestNoOccasion_fallsBackToAnyOccasionIdeas() {
        val anyOccasion = "Bất kỳ dịp nào"
        val ideas = listOf(
            idea(anyOccasion).copy(title = "A"),
            idea("Sinh nhật").copy(title = "Khác"),
            idea(anyOccasion).copy(title = "B"),
        )
        val result = suggest(ideas, LocalDate.of(2026, 6, 1), "")
        assertEquals(listOf("A", "B"), result.map { it.idea.title })
        result.forEach {
            assertEquals(false, it.matchesInterest)
            assertEquals(false, it.fitsOccasion)
            assertEquals("Món quà hợp với mọi dịp, dễ chuẩn bị.", it.reason)
            assertEquals("Chưa có dịp nào gần, bạn có thể chuẩn bị bất cứ lúc nào.", it.preparation)
        }

        val english = GetPersonalizedGiftSuggestionsUseCase(
            ideas = ideas,
            interests = emptySet(),
            budgetMaxVnd = null,
            birthdays = emptyList(),
            loveStart = null,
            today = LocalDate.of(2026, 6, 1),
            isEnglish = true,
        )
        assertEquals(2, english.size)
        assertEquals("A gift that fits any occasion and is easy to prepare.", english[0].reason)
        assertEquals("No occasion is close, so you can prepare at any time.", english[0].preparation)
    }

    @Test
    fun invoke_withInterestMatch_addsNoFallbackIdea() {
        val anyOccasion = "Bất kỳ dịp nào"
        val ideas = listOf(
            idea(anyOccasion).copy(title = "A"),
            idea("Sinh nhật").copy(title = "Khớp", targetInterests = "coffee"),
            idea(anyOccasion).copy(title = "B"),
        )
        val result = GetPersonalizedGiftSuggestionsUseCase(
            ideas = ideas,
            interests = setOf("coffee"),
            budgetMaxVnd = null,
            birthdays = emptyList(),
            loveStart = null,
            today = LocalDate.of(2026, 6, 1),
            isEnglish = false,
        )
        assertEquals(listOf("Khớp"), result.map { it.idea.title })
    }

    @Test
    fun invoke_fallback_skipsAnyIdeaAboveBudget() {
        val ideas = listOf(idea("Bất kỳ dịp nào").copy(priceRange = GiftIdeasSeed.PriceRange.OVER_1M))
        val result = GetPersonalizedGiftSuggestionsUseCase(
            ideas = ideas,
            interests = emptySet(),
            budgetMaxVnd = 100_000L,
            birthdays = emptyList(),
            loveStart = null,
            today = LocalDate.of(2026, 6, 1),
            isEnglish = false,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun interestKeywords_mapsKeysToKeywords() {
        // Khoá đã biết nở thành từ khoá VI + EN (trim, lowercase); khoá lạ giữ nguyên.
        assertEquals(
            setOf("coffee", "cà phê", "cafe", "travel", "du lịch", "unknownkey"),
            GetPersonalizedGiftSuggestionsUseCase.interestKeywords(setOf("coffee", " Travel ", "unknownKey")),
        )
    }

    @Test
    fun invoke_freeTextKeyword_matchesSeedThroughInterestKey() {
        // "du lịch" là từ khoá (không phải khoá) của "travel"; seed cắm trại phải khớp qua targetInterests = "travel".
        val ideas = GiftIdeasSeed.all.map { it.toEntity(AppLanguage.VI) }
        val result = GetPersonalizedGiftSuggestionsUseCase(
            ideas = ideas,
            interests = setOf("du lịch"),
            budgetMaxVnd = null,
            birthdays = emptyList(),
            loveStart = null,
            today = LocalDate.of(2026, 5, 10),
            isEnglish = false,
        )
        assertTrue(
            "camping seed missing from ${result.map { it.idea.remoteId }}",
            result.any { it.idea.remoteId == "seed_gift-camping-trip" },
        )
    }

    @Test
    fun seedInterestKeys_everyImageSeedResolves() {
        val seeds = GiftIdeasSeed.all.map { it.imageSeed }.toSet()
        val expected = listOf(
            "gift-camping-trip", "gift-weekend-trip", "gift-hot-air-balloon", "gift-board-game",
            "gift-movie-tickets", "gift-cooking-class", "gift-playlist", "gift-couple-mugs",
            "gift-perfume-set", "gift-couple-watch", "gift-couple-bracelet", "gift-love-journal",
        )
        expected.forEach { assertTrue("unknown imageSeed $it", it in seeds) }
    }
}
