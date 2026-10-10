package com.example.domain.usecase

import com.example.data.model.GiftIdeaEntity
import com.example.data.seed.GiftIdeasSeed
import com.example.data.seed.HolidayDates
import com.example.data.seed.VietnameseHolidays
import com.example.data.seed.WesternHolidays
import com.example.domain.GiftRanker
import com.example.domain.content.GiftCatalog
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Dịp sắp tới gần nhất; [name] trùng với `suggestedOccasion` của catalog để GiftRanker so khớp. */
data class UpcomingOccasion(val name: String, val date: LocalDate, val daysUntil: Int)

data class PersonalizedGiftSuggestion(
    val idea: GiftIdeaEntity,
    val gift: GiftCatalog.LocalizedGift,
    val reason: String,
    val preparation: String,
    val matchesInterest: Boolean,
    val fitsOccasion: Boolean,
    val priceLabel: String?,
)

/**
 * Chọn tối đa 3 quà hợp sở thích chung, ngân sách và dịp trong 30 ngày tới. Thuần JVM, không ghi Room, không cần mạng.
 *
 * ponytail: catalog seed chưa có tag sở thích nên khớp theo chữ (bảng [interestKeywords]); thêm tag vào seed khi cần chính xác hơn.
 * ponytail: ngân sách `null` = không giới hạn (hồ sơ chưa nhập); chỉ lọc theo sàn của khoảng giá, không so giá thật.
 * ponytail: vùng dịp lễ chỉ có VN/INTL; rỗng = giữ mọi dịp lễ cố định như trước.
 */
object GetPersonalizedGiftSuggestionsUseCase {
    const val MAX_RESULTS = 3
    const val OCCASION_WINDOW_DAYS = 30
    const val REGION_VN = "VN"
    const val REGION_INTL = "INTL"

    private const val ANNIVERSARY = "Kỷ niệm ngày yêu"
    private const val FIRST_ANNIVERSARY = "Kỷ niệm 1 năm"
    private const val BIRTHDAY = "Sinh nhật"
    private const val TET = "Tết Nguyên Đán"

    /** Dịp lễ cố định: tên theo catalog + tiêu đề trong bảng ngày lễ tĩnh. Giáng Sinh lấy ngày gần nhất (24 hoặc 25). */
    private val fixedOccasions = listOf(
        "Valentine" to listOf("Lễ Tình Nhân Valentine", "Lễ Tình Nhân"),
        "8/3" to listOf("Quốc Tế Phụ Nữ"),
        "20/10" to listOf("Ngày Phụ Nữ Việt Nam"),
        "Giáng Sinh" to listOf("Giáng Sinh", "Đêm Giáng Sinh"),
    )

    /** Dịp chỉ có ở Việt Nam; vùng INTL bỏ qua. */
    private val vietnamOnly = setOf("8/3", "20/10")

    private val holidayMonthDays: List<Triple<String, Int, Int>> =
        VietnameseHolidays.fixedHolidays.map { Triple(it.titleVi, it.month, it.day) } +
            WesternHolidays.fixedHolidays.map { Triple(it.titleVi, it.month, it.day) }

    private val keywordsByInterest = mapOf(
        "coffee" to listOf("coffee", "cà phê", "cafe"),
        "travel" to listOf("travel", "du lịch"),
        "technology" to listOf("technology", "công nghệ"),
        "cycling" to listOf("cycling", "xe đạp", "bike"),
        "fashion" to listOf("fashion", "thời trang"),
        "music" to listOf("music", "âm nhạc"),
        "cinema" to listOf("cinema", "điện ảnh", "phim", "movie"),
        "cooking" to listOf("cooking", "nấu ăn"),
        "books" to listOf("books", "sách"),
        "gaming" to listOf("gaming", "chơi game", "board game"),
    )

    /** Mỗi khoá sở thích nở thành từ khoá VI + EN; khoá lạ giữ nguyên. */
    fun interestKeywords(keys: Set<String>): Set<String> = keys.flatMap { key ->
        val k = key.trim().lowercase()
        keywordsByInterest[k] ?: listOf(k)
    }.filter { it.isNotEmpty() }.toSet()

    /** Mức giá sàn của khoảng giá; khoảng lạ hoặc trống trả null (giữ lại). */
    private fun priceFloorVnd(priceRange: String): Long? = when (priceRange) {
        GiftIdeasSeed.PriceRange.UNDER_200K -> 0L
        GiftIdeasSeed.PriceRange.RANGE_200_500K -> 200_000L
        GiftIdeasSeed.PriceRange.RANGE_500K_1M -> 500_000L
        GiftIdeasSeed.PriceRange.OVER_1M -> 1_000_000L
        else -> null
    }

    private fun clampedDate(year: Int, month: Int, day: Int): LocalDate {
        val ym = YearMonth.of(year, month)
        return ym.atDay(minOf(day, ym.lengthOfMonth()))
    }

    /** Lần xảy ra tiếp theo (từ hôm nay trở đi) của một ngày-tháng lặp hằng năm. */
    private fun nextYearly(month: Int, day: Int, today: LocalDate): LocalDate {
        val thisYear = clampedDate(today.year, month, day)
        return if (thisYear.isBefore(today)) clampedDate(today.year + 1, month, day) else thisYear
    }

    private fun occasion(name: String, date: LocalDate, today: LocalDate) =
        UpcomingOccasion(name, date, ChronoUnit.DAYS.between(today, date).toInt())

    /**
     * Dịp gần nhất trong 0..[OCCASION_WINDOW_DAYS] ngày. Sinh nhật của cả hai người, kỷ niệm yêu, lễ cố định.
     * Tết Nguyên Đán chỉ hiện khi bảng ngày đã xác minh còn năm tới; tên Tết không trùng dịp nào nên chỉ cộng điểm "bất kỳ dịp nào".
     */
    fun nearestUpcomingOccasion(
        today: LocalDate,
        birthdays: List<LocalDate>,
        loveStart: LocalDate?,
        region: String = "",
    ): UpcomingOccasion? {
        val candidates = mutableListOf<UpcomingOccasion>()
        birthdays.forEach { candidates += occasion(BIRTHDAY, nextYearly(it.monthValue, it.dayOfMonth, today), today) }
        if (loveStart != null) {
            val next = nextYearly(loveStart.monthValue, loveStart.dayOfMonth, today)
            val name = if (next.year - loveStart.year == 1) FIRST_ANNIVERSARY else ANNIVERSARY
            candidates += occasion(name, next, today)
        }
        val intl = region == REGION_INTL
        for ((name, titles) in fixedOccasions) {
            if (intl && name in vietnamOnly) continue
            holidayMonthDays.filter { it.first in titles }
                .map { nextYearly(it.second, it.third, today) }
                .minOrNull()
                ?.let { candidates += occasion(name, it, today) }
        }
        if (!intl) {
            VietnameseHolidays.lunarHolidays.firstOrNull { it.titleVi == TET }
                ?.let { HolidayDates.nextLunar(it, today) }
                ?.let { candidates += occasion(TET, it, today) }
        }
        return candidates.filter { it.daysUntil in 0..OCCASION_WINDOW_DAYS }.minByOrNull { it.daysUntil }
    }

    operator fun invoke(
        ideas: List<GiftIdeaEntity>,
        interests: Set<String>,
        budgetMaxVnd: Long?,
        birthdays: List<LocalDate>,
        loveStart: LocalDate?,
        today: LocalDate,
        isEnglish: Boolean,
        region: String = "",
    ): List<PersonalizedGiftSuggestion> {
        val upcoming = nearestUpcomingOccasion(today, birthdays, loveStart, region)
        val keywords = interestKeywords(interests)
        val affordable = ideas.filter { idea ->
            val floor = priceFloorVnd(idea.priceRange)
            budgetMaxVnd == null || floor == null || floor <= budgetMaxVnd
        }
        val occasionName = upcoming?.name ?: ""
        return GiftRanker.rank(affordable, keywords, occasionName)
            .map { it to Pair(GiftRanker.matchesAnyInterest(it, keywords), GiftRanker.fitsOccasion(it, occasionName)) }
            .filter { (_, fit) -> fit.first || fit.second }
            .take(MAX_RESULTS)
            .map { (idea, fit) ->
                PersonalizedGiftSuggestion(
                    idea = idea,
                    gift = GiftCatalog.localized(idea, isEnglish),
                    reason = reasonText(fit.first, fit.second, isEnglish),
                    preparation = preparationText(upcoming?.daysUntil, isEnglish),
                    matchesInterest = fit.first,
                    fitsOccasion = fit.second,
                    priceLabel = GiftCatalog.priceLabel(idea.priceRange, isEnglish),
                )
            }
    }

    private fun reasonText(interest: Boolean, occasion: Boolean, isEnglish: Boolean): String = when {
        interest && occasion -> if (isEnglish) "Fits your shared interests and the coming occasion." else "Hợp sở thích chung của hai bạn và dịp sắp tới."
        interest -> if (isEnglish) "Fits your shared interests." else "Hợp sở thích chung của hai bạn."
        else -> if (isEnglish) "Fits the coming occasion." else "Hợp dịp sắp tới của hai bạn."
    }

    private fun preparationText(daysUntil: Int?, isEnglish: Boolean): String = when {
        daysUntil == null -> if (isEnglish) "No occasion is close, so you can prepare at any time." else "Chưa có dịp nào gần, bạn có thể chuẩn bị bất cứ lúc nào."
        daysUntil == 0 -> if (isEnglish) "The occasion is today, so get it ready right away." else "Dịp này là hôm nay, hãy chuẩn bị ngay."
        daysUntil <= 3 -> if (isEnglish) "Only a few days left, so order or buy it now." else "Chỉ còn vài ngày, nên đặt hoặc mua ngay."
        daysUntil <= 14 -> if (isEnglish) "About two weeks left, enough time to order and wrap it." else "Còn khoảng hai tuần, đủ thời gian đặt và gói quà."
        else -> if (isEnglish) "Almost a month left, so take your time to choose and prepare." else "Còn gần một tháng, thong thả chọn và chuẩn bị."
    }
}
