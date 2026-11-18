package com.example.shared.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
enum class CoupleRelevance {
    NONE,
    OPTIONAL,
    RELEVANT,
    CORE,
}

@Serializable
enum class GiftExpectation {
    NONE,
    SMALL,
    EXPECTED,
    MAJOR,
}

@Serializable
enum class PreparationStage {
    RELAXED,
    PLAN_AHEAD_BOOKING,
    ACTION_NEEDED,
    URGENT_LAST_MINUTE,
    TODAY_CELEBRATION,
}

@Serializable
data class OccasionProfile(
    val id: String,
    val titleVi: String,
    val titleEn: String,
    val coupleRelevance: CoupleRelevance,
    val giftExpectation: GiftExpectation,
    val defaultReminderOn: Boolean,
    val recommendedLeadDays: List<Int>,
    val bookingAdvised: Boolean,
    val culturalTaboosVi: List<String>,
    val culturalTaboosEn: List<String>,
    val prepChecklistVi: List<String>,
    val prepChecklistEn: List<String>,
)

@Serializable
data class DayMilestoneSpec(
    val days: Int,
    val titleVi: String,
    val titleEn: String,
    val badgeEmoji: String,
    val isMajor: Boolean = false,
)

@Serializable
data class MonthiversaryOccurrence(
    val months: Int,
    val dateIso: String,
    val daysUntil: Int,
    val titleVi: String,
    val titleEn: String,
)

@Serializable
data class NextOccasionReadiness(
    val occasionId: String,
    val titleVi: String,
    val titleEn: String,
    val targetDateIso: String,
    val daysRemaining: Int,
    val stage: PreparationStage,
    val coupleRelevance: CoupleRelevance,
    val giftExpectation: GiftExpectation,
    val bookingAdvised: Boolean,
    val recommendedLeadDays: Int,
    val headlineVi: String,
    val headlineEn: String,
    val actionSummaryVi: String,
    val actionSummaryEn: String,
    val prepChecklistVi: List<String>,
    val prepChecklistEn: List<String>,
    val culturalTaboosVi: List<String>,
    val culturalTaboosEn: List<String>,
    val isMilestone: Boolean = false,
)

/**
 * Pure KMP behavior engine for couple milestones (Day 1 = startDate -> Day N = startDate + N - 1),
 * monthiversaries, 3-axis occasion intelligence, and next-occasion preparation readiness.
 */
object AnniversaryBehaviorEngine {

    val MILESTONE_DAYS: List<Int> = listOf(
        7, 14, 22, 30, 50, 52, 99, 100, 200, 300, 365,
        500, 520, 730, 1000, 1095, 1314, 1825, 2000, 3650,
    )

    val DAY_MILESTONE_SPECS: List<DayMilestoneSpec> = listOf(
        DayMilestoneSpec(7, "7 Ngày Đầu Tiên", "First 7 Days", "✨", false),
        DayMilestoneSpec(14, "2 Tuần Bên Nhau", "2 Weeks Together", "🌷", false),
        DayMilestoneSpec(22, "22 Ngày Ngọt Ngào", "22 Sweet Days", "💞", false),
        DayMilestoneSpec(30, "1 Tháng Yêu Nhau (30 Ngày)", "1 Month in Love (30 Days)", "🌱", true),
        DayMilestoneSpec(50, "50 Ngày Gắn Kết", "50 Days Together", "🌸", false),
        DayMilestoneSpec(52, "52 Ngày Thương Nhớ", "52 Days of Affection", "💌", false),
        DayMilestoneSpec(99, "99 Ngày Bền Chặt", "99 Days Strong", "🌺", false),
        DayMilestoneSpec(100, "Bách Nhật Yêu (100 Ngày)", "100 Days in Love", "💎", true),
        DayMilestoneSpec(200, "200 Ngày Nồng Nàn", "200 Days of Passion", "🌹", true),
        DayMilestoneSpec(300, "300 Ngày Chung Đôi", "300 Days as a Couple", "✨", true),
        DayMilestoneSpec(365, "Tròn 1 Năm Yêu (365 Ngày)", "1 Year in Love (365 Days)", "🎂", true),
        DayMilestoneSpec(500, "500 Ngày Tri Kỷ", "500 Days of Soulmates", "🌿", true),
        DayMilestoneSpec(520, "520 Ngày Yêu Thương", "520 Days of Love", "💗", true),
        DayMilestoneSpec(730, "Tròn 2 Năm Yêu (730 Ngày)", "2 Years in Love (730 Days)", "💍", true),
        DayMilestoneSpec(1000, "1000 Ngày Trọn Vẹn", "1000 Days Together", "👑", true),
        DayMilestoneSpec(1095, "3 Năm Đồng Hành (1095 Ngày)", "3 Years Together (1095 Days)", "🎊", true),
        DayMilestoneSpec(1314, "1314 Ngày Trọn Đời", "1314 Days Forever", "♾️", true),
        DayMilestoneSpec(1825, "5 Năm Đồng Hành (1825 Ngày)", "5 Years Together (1825 Days)", "🏅", true),
        DayMilestoneSpec(2000, "2000 Ngày Hạnh Phúc", "2000 Days of Happiness", "🌟", true),
        DayMilestoneSpec(3650, "10 Năm Thập Kỷ Yêu (3650 Ngày)", "10 Years Together (3650 Days)", "💎", true),
    )

    fun defaultOccasionProfiles(region: String = "VN"): List<OccasionProfile> =
        OccasionProfilesCatalog.profiles(region)

    fun profileById(id: String, region: String = "VN"): OccasionProfile? =
        defaultOccasionProfiles(region).firstOrNull { it.id == id }

    fun profileForTitleOrId(idOrTitle: String, region: String = "VN"): OccasionProfile {
        val norm = idOrTitle.trim().lowercase()
        val all = defaultOccasionProfiles(region)
        all.firstOrNull { it.id == norm }?.let { return it }
        return when {
            norm.contains("sinh nhật") || norm.contains("birthday") -> all.first { it.id == "partner_birthday" }
            norm.contains("kỷ niệm") || norm.contains("anniversary") -> all.first { it.id == "couple_anniversary" }
            norm.contains("white day") || norm.contains("valentine trắng") -> all.first { it.id == "white_day" }
            norm.contains("valentine") || norm.contains("namorados") -> all.first { it.id == "valentine" }
            norm.contains("20/10") || norm.contains("phụ nữ việt nam") -> all.first { it.id == "vn_women_day_2010" }
            norm.contains("8/3") || norm.contains("quốc tế phụ nữ") || norm.contains("women") -> all.first { it.id == "women_day_0803" }
            norm.contains("thất tịch") || norm.contains("qixi") -> all.first { it.id == "that_tich" }
            norm.contains("trung thu") || norm.contains("mid-autumn") -> all.first { it.id == "trung_thu" }
            norm.contains("giáng sinh") || norm.contains("noel") || norm.contains("christmas") -> all.first { it.id == "christmas_eve" }
            norm.contains("tết nguyên đán") || norm.contains("lunar new year") -> all.first { it.id == "tet" }
            norm.contains("rằm tháng giêng") || norm.contains("full moon") -> all.first { it.id == "ram_thang_gieng" }
            norm.contains("giỗ tổ") || norm.contains("hùng") -> all.first { it.id == "gio_to_hung_vuong" }
            norm.contains("vu lan") -> all.first { it.id == "vu_lan" }
            norm.contains("hạnh phúc") || norm.contains("happiness") -> all.first { it.id == "happiness_day" }
            norm.contains("gia đình") || norm.contains("family") -> all.first { it.id == "family_day_vn" }
            norm.contains("tết dương") || norm.contains("new year") -> all.first { it.id == "new_year_solar" }
            else -> OccasionProfile(
                id = norm.ifBlank { "custom_occasion" },
                titleVi = idOrTitle.ifBlank { "Dịp đặc biệt" },
                titleEn = idOrTitle.ifBlank { "Special Occasion" },
                coupleRelevance = CoupleRelevance.RELEVANT,
                giftExpectation = GiftExpectation.SMALL,
                defaultReminderOn = true,
                recommendedLeadDays = listOf(7, 3, 1, 0),
                bookingAdvised = false,
                culturalTaboosVi = emptyList(),
                culturalTaboosEn = emptyList(),
                prepChecklistVi = listOf("Chuẩn bị lời chúc riêng cho người thương"),
                prepChecklistEn = listOf("Prepare a thoughtful note for your partner"),
            )
        }
    }

    /** CRITICAL: Day 1 = startDate, so Day N falls on startDate + (N - 1) days (matches LoveMilestoneRules). */
    fun loveDaysOn(startDate: LocalDate, targetDate: LocalDate): Int? {
        if (targetDate < startDate) return null
        return startDate.daysUntil(targetDate) + 1
    }

    fun dateForMilestoneDay(startDate: LocalDate, dayNumber: Int): LocalDate {
        require(dayNumber >= 1) { "dayNumber must be >= 1 (Day 1 = startDate)" }
        return startDate.plus(dayNumber - 1, DateTimeUnit.DAY)
    }

    fun nextDayMilestone(startDate: LocalDate, today: LocalDate): Pair<DayMilestoneSpec, LocalDate>? {
        val currentLoveDays = loveDaysOn(startDate, today) ?: 0
        for (spec in DAY_MILESTONE_SPECS) {
            if (spec.days >= currentLoveDays) {
                val date = dateForMilestoneDay(startDate, spec.days)
                if (date >= today) return spec to date
            }
        }
        return null
    }

    fun nextMonthiversary(startDate: LocalDate, today: LocalDate): MonthiversaryOccurrence {
        var months = ((today.year - startDate.year) * 12 + (today.month.number - startDate.month.number)).coerceAtLeast(1)
        var candidate = addMonthsClamped(startDate, months)
        while (candidate < today) {
            months += 1
            candidate = addMonthsClamped(startDate, months)
        }
        val daysUntil = today.daysUntil(candidate)
        return MonthiversaryOccurrence(
            months = months,
            dateIso = candidate.toString(),
            daysUntil = daysUntil,
            titleVi = "Tròn $months Tháng Yêu Nhau",
            titleEn = "$months-Month Monthiversary",
        )
    }

    fun upcomingMonthiversaries(startDate: LocalDate, today: LocalDate, count: Int = 3): List<MonthiversaryOccurrence> {
        if (count <= 0) return emptyList()
        val first = nextMonthiversary(startDate, today)
        return (0 until count).map { offset ->
            val m = first.months + offset
            val date = addMonthsClamped(startDate, m)
            MonthiversaryOccurrence(
                months = m,
                dateIso = date.toString(),
                daysUntil = today.daysUntil(date),
                titleVi = "Tròn $m Tháng Yêu Nhau",
                titleEn = "$m-Month Monthiversary",
            )
        }
    }

    fun calculatePreparationStage(daysRemaining: Int, profile: OccasionProfile): PreparationStage {
        val maxLead = profile.recommendedLeadDays.maxOrNull() ?: 7
        return when {
            daysRemaining <= 0 -> PreparationStage.TODAY_CELEBRATION
            daysRemaining <= 3 -> PreparationStage.URGENT_LAST_MINUTE
            daysRemaining <= 7 -> PreparationStage.ACTION_NEEDED
            daysRemaining <= 21 && (profile.bookingAdvised || profile.giftExpectation == GiftExpectation.MAJOR || daysRemaining <= maxLead) ->
                PreparationStage.PLAN_AHEAD_BOOKING
            else -> PreparationStage.RELAXED
        }
    }

    fun buildReadiness(
        profile: OccasionProfile,
        targetDate: LocalDate,
        today: LocalDate,
        isMilestone: Boolean = false,
    ): NextOccasionReadiness {
        val daysRemaining = today.daysUntil(targetDate).coerceAtLeast(0)
        val stage = calculatePreparationStage(daysRemaining, profile)
        val recommendedLead = profile.recommendedLeadDays.firstOrNull { it >= daysRemaining }
            ?: profile.recommendedLeadDays.maxOrNull()
            ?: 7
        val (headlineVi, headlineEn, actionVi, actionEn) = stageCopy(stage, profile, daysRemaining)
        return NextOccasionReadiness(
            occasionId = profile.id,
            titleVi = profile.titleVi,
            titleEn = profile.titleEn,
            targetDateIso = targetDate.toString(),
            daysRemaining = daysRemaining,
            stage = stage,
            coupleRelevance = profile.coupleRelevance,
            giftExpectation = profile.giftExpectation,
            bookingAdvised = profile.bookingAdvised,
            recommendedLeadDays = recommendedLead,
            headlineVi = headlineVi,
            headlineEn = headlineEn,
            actionSummaryVi = actionVi,
            actionSummaryEn = actionEn,
            prepChecklistVi = profile.prepChecklistVi,
            prepChecklistEn = profile.prepChecklistEn,
            culturalTaboosVi = profile.culturalTaboosVi,
            culturalTaboosEn = profile.culturalTaboosEn,
            isMilestone = isMilestone,
        )
    }

    fun upcomingOccasions(
        today: LocalDate,
        loveStartDate: LocalDate?,
        partnerBirthday: LocalDate?,
        region: String = "VN",
        withinDays: Int = 90,
        disabledOccasionIds: Set<String> = emptySet(),
        includeMonthiversary: Boolean = false,
    ): List<NextOccasionReadiness> {
        val candidates = mutableListOf<NextOccasionReadiness>()
        val profiles = defaultOccasionProfiles(region).associateBy { it.id }

        if (loveStartDate != null) {
            val annualDate = nextAnnualOccurrence(loveStartDate.month.number, loveStartDate.day, today)
            if (annualDate > loveStartDate && today.daysUntil(annualDate) <= withinDays) {
                profiles["couple_anniversary"]?.let { candidates += buildReadiness(it, annualDate, today) }
            }
            nextDayMilestone(loveStartDate, today)?.let { (spec, date) ->
                if (today.daysUntil(date) <= withinDays) {
                    val msProfile = OccasionProfile(
                        id = "milestone_${spec.days}",
                        titleVi = spec.titleVi,
                        titleEn = spec.titleEn,
                        coupleRelevance = CoupleRelevance.CORE,
                        giftExpectation = if (spec.isMajor) GiftExpectation.EXPECTED else GiftExpectation.SMALL,
                        defaultReminderOn = true,
                        recommendedLeadDays = if (spec.isMajor) listOf(14, 7, 3, 1, 0) else listOf(7, 3, 1, 0),
                        bookingAdvised = spec.isMajor,
                        culturalTaboosVi = emptyList(),
                        culturalTaboosEn = emptyList(),
                        prepChecklistVi = listOf("Chụp ảnh kỷ niệm và lưu khoảnh khắc cột mốc ${spec.days} ngày"),
                        prepChecklistEn = listOf("Capture a memory photo for your ${spec.days}-day milestone"),
                    )
                    candidates += buildReadiness(msProfile, date, today, isMilestone = true)
                }
            }
            if (includeMonthiversary) {
                val month = nextMonthiversary(loveStartDate, today)
                if (month.daysUntil <= withinDays) {
                    val mDate = LocalDate.parse(month.dateIso)
                    val mProfile = OccasionProfile(
                        id = "monthiversary_${month.months}",
                        titleVi = month.titleVi,
                        titleEn = month.titleEn,
                        coupleRelevance = CoupleRelevance.RELEVANT,
                        giftExpectation = GiftExpectation.SMALL,
                        defaultReminderOn = false,
                        recommendedLeadDays = listOf(7, 3, 1, 0),
                        bookingAdvised = false,
                        culturalTaboosVi = emptyList(),
                        culturalTaboosEn = emptyList(),
                        prepChecklistVi = listOf("Gửi lời nhắn ngọt ngào nhân dịp tròn ${month.months} tháng"),
                        prepChecklistEn = listOf("Send a sweet note for ${month.months} months together"),
                    )
                    candidates += buildReadiness(mProfile, mDate, today, isMilestone = true)
                }
            }
        }

        if (partnerBirthday != null) {
            val bDate = nextAnnualOccurrence(partnerBirthday.month.number, partnerBirthday.day, today)
            if (today.daysUntil(bDate) <= withinDays) {
                profiles["partner_birthday"]?.let { candidates += buildReadiness(it, bDate, today) }
            }
        }

        addFixedAndLunarHolidays(today, region, withinDays, profiles, candidates)

        return candidates
            .filterNot { it.occasionId in disabledOccasionIds }
            .sortedWith(compareBy<NextOccasionReadiness> { it.daysRemaining }.thenByDescending { it.coupleRelevance.ordinal })
    }

    fun computeNextOccasionReadiness(
        today: LocalDate,
        loveStartDate: LocalDate?,
        partnerBirthday: LocalDate?,
        region: String = "VN",
        disabledOccasionIds: Set<String> = emptySet(),
        includeMonthiversary: Boolean = false,
    ): NextOccasionReadiness? =
        upcomingOccasions(
            today = today,
            loveStartDate = loveStartDate,
            partnerBirthday = partnerBirthday,
            region = region,
            withinDays = 366,
            disabledOccasionIds = disabledOccasionIds,
            includeMonthiversary = includeMonthiversary,
        ).firstOrNull { it.coupleRelevance >= CoupleRelevance.RELEVANT }

    fun isSacredCelebrationToday(
        today: LocalDate,
        loveStartDate: LocalDate?,
        partnerBirthday: LocalDate?,
    ): Boolean {
        val next = computeNextOccasionReadiness(today, loveStartDate, partnerBirthday) ?: return false
        return next.stage == PreparationStage.TODAY_CELEBRATION && next.coupleRelevance == CoupleRelevance.CORE
    }

    private fun addFixedAndLunarHolidays(
        today: LocalDate,
        region: String,
        withinDays: Int,
        profiles: Map<String, OccasionProfile>,
        out: MutableList<NextOccasionReadiness>,
    ) {
        val normalizedRegion = region.trim().uppercase().ifEmpty { "VN" }
        val fixedDates = listOf(
            "valentine" to (if (normalizedRegion == "BR") 6 to 12 else 2 to 14),
            "women_day_0803" to (3 to 8),
            "white_day" to (3 to 14),
            "happiness_day" to (3 to 20),
            "family_day_vn" to (6 to 28),
            "vn_women_day_2010" to (10 to 20),
            "christmas_eve" to (12 to 24),
            "new_year_solar" to (1 to 1),
        )
        for ((id, md) in fixedDates) {
            val profile = profiles[id] ?: continue
            if (profile.coupleRelevance == CoupleRelevance.NONE) continue
            val date = nextAnnualOccurrence(md.first, md.second, today)
            if (today.daysUntil(date) <= withinDays) {
                out += buildReadiness(profile, date, today)
            }
        }
        if (normalizedRegion != "INTL") {
            val lunarMap = mapOf(
                LunarHolidayId.TET to "tet",
                LunarHolidayId.THAT_TICH to "that_tich",
                LunarHolidayId.TRUNG_THU to "trung_thu",
            )
            for ((lunarId, profileId) in lunarMap) {
                val profile = profiles[profileId] ?: continue
                val date = LunarCalendar2025To2040.nextOccurrence(lunarId, today) ?: continue
                if (today.daysUntil(date) <= withinDays) {
                    out += buildReadiness(profile, date, today)
                }
            }
        }
    }

    private data class StageCopy(val headVi: String, val headEn: String, val actVi: String, val actEn: String)

    private fun stageCopy(stage: PreparationStage, profile: OccasionProfile, daysRemaining: Int): StageCopy {
        val enDayUnit = if (daysRemaining == 1) "day" else "days"
        return when (stage) {
            PreparationStage.TODAY_CELEBRATION -> StageCopy(
                "Hôm nay là ${profile.titleVi}",
                "Today is ${profile.titleEn}",
                "Dành trọn hôm nay cho hai bạn, quảng cáo đã tạm tắt",
                "Enjoy today together with ads paused",
            )
            PreparationStage.URGENT_LAST_MINUTE -> StageCopy(
                "Còn $daysRemaining ngày tới ${profile.titleVi}",
                "$daysRemaining $enDayUnit until ${profile.titleEn}",
                "Chọn quà có sẵn, thiệp tay hoặc đặt bàn ngay hôm nay",
                "Pick an in-stock gift, handwritten note, or book tonight",
            )
            PreparationStage.ACTION_NEEDED -> StageCopy(
                "Còn $daysRemaining ngày tới ${profile.titleVi}",
                "$daysRemaining $enDayUnit until ${profile.titleEn}",
                "Chốt quà và giữ lịch hẹn trước dịp cao điểm",
                "Finalize your gift and confirm plans ahead of peak rush",
            )
            PreparationStage.PLAN_AHEAD_BOOKING -> StageCopy(
                "Còn $daysRemaining ngày tới ${profile.titleVi}",
                "$daysRemaining $enDayUnit until ${profile.titleEn}",
                if (profile.bookingAdvised) "Nên đặt bàn hoặc quà làm riêng từ tuần này"
                else "Xem gợi ý phù hợp từ Hồ sơ người thương",
                if (profile.bookingAdvised) "Reserve dining or custom gifts early for better choices"
                else "Browse tailored ideas from your Partner Dossier",
            )
            PreparationStage.RELAXED -> StageCopy(
                "Còn $daysRemaining ngày tới ${profile.titleVi}",
                "$daysRemaining $enDayUnit until ${profile.titleEn}",
                "Ghi lại những món người ấy tình cờ nhắc thích",
                "Save small hints whenever they mention what they like",
            )
        }
    }

    private fun nextAnnualOccurrence(month: Int, day: Int, today: LocalDate): LocalDate {
        val thisYear = clampedDate(today.year, month, day)
        return if (thisYear < today) clampedDate(today.year + 1, month, day) else thisYear
    }

    private fun clampedDate(year: Int, month: Int, day: Int): LocalDate =
        LocalDate(year, month, minOf(day, lengthOfMonth(year, month)))

    private fun addMonthsClamped(startDate: LocalDate, monthsToAdd: Int): LocalDate {
        val totalMonths = (startDate.year * 12 + (startDate.month.number - 1)) + monthsToAdd
        val year = totalMonths / 12
        val month = (totalMonths % 12) + 1
        return clampedDate(year, month, startDate.day)
    }

    private fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
