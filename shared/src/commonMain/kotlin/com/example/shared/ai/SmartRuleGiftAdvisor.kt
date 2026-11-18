package com.example.shared.ai

import com.example.shared.calendar.AnniversaryBehaviorEngine
import com.example.shared.calendar.PreparationStage
import com.example.shared.dossier.LoveLanguage
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerInterestCatalog
import com.example.shared.dossier.SecretHintNote

/**
 * Pure multi-factor local gift & love-note recommendation engine.
 * Scores 28 bilingual gift archetypes + secret dossier hints against love languages,
 * standardized interests, freeform likes, sizes/colors/scents, allergies/taboos exclusion,
 * budget cap, occasion urgency, and past gift history deduplication.
 */
object SmartRuleGiftAdvisor : AiGiftAdvisorGateway {

    private val semanticAllergyAliases: Map<String, Set<String>> = mapOf(
        "caffeine" to setOf("coffee", "caffeine", "ca phe", "cafe", "pet cafe", "espresso"),
        "ca phe" to setOf("coffee", "caffeine", "ca phe", "cafe", "pet cafe", "espresso"),
        "coffee" to setOf("coffee", "caffeine", "ca phe", "cafe", "pet cafe", "espresso"),
        "cafe" to setOf("coffee", "caffeine", "ca phe", "cafe", "pet cafe", "espresso"),
        "nuts" to setOf("nuts", "peanut", "dau phong", "almond", "hat"),
        "dau phong" to setOf("nuts", "peanut", "dau phong"),
        "dairy" to setOf("dairy", "milk", "sua", "cream"),
        "sua" to setOf("dairy", "milk", "sua"),
        "scent" to setOf("scent", "fragrance", "perfume", "nuoc hoa", "mui nong"),
        "perfume" to setOf("scent", "fragrance", "perfume", "nuoc hoa", "mui nong"),
        "nuoc hoa" to setOf("scent", "fragrance", "perfume", "nuoc hoa", "mui nong"),
        "mui nong" to setOf("scent", "fragrance", "perfume", "nuoc hoa", "mui nong"),
        "pollen" to setOf("pollen", "phan hoa", "flowers"),
        "phan hoa" to setOf("pollen", "phan hoa", "flowers"),
        "hoa" to setOf("pollen", "phan hoa", "flowers"),
        "gluten" to setOf("gluten", "banh", "pastry"),
        "dong ho" to setOf("clock_watch", "watch", "dong ho"),
        "watch" to setOf("clock_watch", "watch", "dong ho"),
        "giay" to setOf("shoes", "giay"),
        "shoes" to setOf("shoes", "giay"),
    )

    override suspend fun advise(request: AiGiftRequest): AiGiftAdviceBundle = generate(request)

    fun generate(request: AiGiftRequest, nowEpochMs: Long = 0L): AiGiftAdviceBundle {
        val dossier = request.dossier
        val isEn = request.isEnglish
        val normalizedInterests = dossier.interestCategories.map { PartnerInterestCatalog.normalizeInterestKey(it) }.toSet()
        val exclusionTokens = collectExclusionTokens(dossier)
        val pastGiftTokens = collectPastGiftTokens(dossier, request.excludedGiftTitles)
        val likeTokens = collectPositiveLikeTokens(dossier)

        val secretRecommendations = scoreSecretHints(
            dossier = dossier,
            exclusionTokens = exclusionTokens,
            pastGiftTokens = pastGiftTokens,
            isEn = isEn,
        )

        val archetypeRecommendations = GiftArchetypeCatalog.all
            .asSequence()
            .filterNot { isExcludedByAllergyOrTaboo(it, exclusionTokens) }
            .filterNot { isDuplicateOfPastGift(it, pastGiftTokens) }
            .filter { passesBudget(it, dossier.budgetMaxVnd) }
            .filter { passesUrgencyLeadTime(it, request.daysUntilOccasion) }
            .map { arch ->
                val scored = scoreArchetype(arch, dossier, normalizedInterests, likeTokens, request)
                scored
            }
            .sortedByDescending { it.matchScore }
            .toList()

        val limit = request.maxRecommendations.coerceIn(1, 10)
        val merged = (secretRecommendations + archetypeRecommendations)
            .distinctBy { PartnerInterestCatalog.stripDiacritics(it.title.lowercase()) }
            .take(limit)

        val occasionProfile = AnniversaryBehaviorEngine.profileForTitleOrId(
            idOrTitle = request.occasionId.ifBlank { request.occasionTitle },
            region = dossier.occasionRegion,
        )
        val tabooWarnings = buildTabooWarnings(dossier, occasionProfile.culturalTaboosVi, occasionProfile.culturalTaboosEn, isEn)
        val prepTimeline = buildPrepTimeline(request.daysUntilOccasion, occasionProfile, isEn)
        val loveNotes = buildLoveNotes(request, isEn)

        return AiGiftAdviceBundle(
            source = AdviceSource.SMART_LOCAL_ENGINE,
            occasionId = request.occasionId.ifBlank { occasionProfile.id },
            occasionTitle = request.occasionTitle.ifBlank { if (isEn) occasionProfile.titleEn else occasionProfile.titleVi },
            summaryHeadline = buildHeadline(request, merged.size, isEn),
            recommendations = merged,
            loveNotes = loveNotes,
            tabooWarnings = tabooWarnings,
            prepTimeline = prepTimeline,
            usedFreeAllowance = false,
            remainingFreeAllowance = null,
            generatedAtEpochMs = nowEpochMs,
        )
    }

    private fun collectExclusionTokens(dossier: PartnerDossier): Set<String> {
        val raw = dossier.foodAndCare.allergies + dossier.foodAndCare.dislikesOrTaboos
        val tokens = mutableSetOf<String>()
        for (entry in raw) {
            val parts = entry.split(',', ';', '|')
            for (part in parts) {
                val norm = PartnerInterestCatalog.stripDiacritics(part.trim().lowercase())
                if (norm.isEmpty()) continue
                tokens += norm
                semanticAllergyAliases.forEach { (k, aliases) ->
                    if (norm == k || norm.contains(k)) {
                        tokens.addAll(aliases)
                    }
                }
            }
        }
        return tokens
    }

    private fun collectPastGiftTokens(dossier: PartnerDossier, extraExcluded: Set<String>): Set<String> =
        (dossier.giftHistory.map { it.title } + extraExcluded)
            .map { PartnerInterestCatalog.stripDiacritics(it.trim().lowercase()) }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun collectPositiveLikeTokens(dossier: PartnerDossier): Set<String> {
        val combined = buildList {
            add(dossier.freeformLikes)
            add(dossier.foodAndCare.goToDrinks)
            addAll(dossier.foodAndCare.favoriteFoods)
            addAll(dossier.sizes.favoriteColors)
            addAll(dossier.sizes.favoriteScents)
        }
        return combined.flatMap { text ->
            val norm = PartnerInterestCatalog.stripDiacritics(text.trim().lowercase())
            if (norm.isEmpty()) emptyList()
            else listOf(norm) + norm.split(',', ';', ' ').map { it.trim() }.filter { it.length >= 2 }
        }.toSet()
    }

    private fun isExcludedByAllergyOrTaboo(arch: GiftArchetype, exclusionTokens: Set<String>): Boolean {
        if (exclusionTokens.isEmpty()) return false
        val normTitleVi = PartnerInterestCatalog.stripDiacritics(arch.titleVi.lowercase())
        val normTitleEn = PartnerInterestCatalog.stripDiacritics(arch.titleEn.lowercase())
        val archTokens = buildSet {
            addAll(arch.allergenTags)
            addAll(arch.keywordTags)
            arch.tabooConcept?.let { add(it) }
            add(normTitleVi)
            add(normTitleEn)
        }
        return exclusionTokens.any { ex ->
            ex in archTokens ||
                (ex.length >= 4 && (normTitleVi.contains(ex) || normTitleEn.contains(ex))) ||
                arch.allergenTags.any { tag ->
                    ex == tag || (ex.length >= 4 && tag.length >= 4 && (ex.contains(tag) || tag.contains(ex)))
                } ||
                (ex.length >= 4 && arch.keywordTags.any { tag -> tag.length >= 4 && (ex == tag || tag.contains(ex)) })
        }
    }

    private fun isDuplicateOfPastGift(arch: GiftArchetype, pastTitles: Set<String>): Boolean {
        if (pastTitles.isEmpty()) return false
        val vi = PartnerInterestCatalog.stripDiacritics(arch.titleVi.lowercase())
        val en = PartnerInterestCatalog.stripDiacritics(arch.titleEn.lowercase())
        return pastTitles.any { past ->
            past == vi || past == en || (past.length >= 5 && (vi.contains(past) || en.contains(past) || past.contains(vi) || past.contains(en)))
        }
    }

    private fun passesBudget(arch: GiftArchetype, budgetMaxVnd: Long): Boolean {
        if (budgetMaxVnd <= 0L) return true
        return arch.estimatedMinVnd <= budgetMaxVnd
    }

    private fun passesUrgencyLeadTime(arch: GiftArchetype, daysUntil: Int?): Boolean {
        if (daysUntil == null) return true
        if (daysUntil <= 1) return arch.leadTimeDays <= 2
        if (daysUntil <= 3) return arch.leadTimeDays <= 5
        return true
    }

    private fun scoreSecretHints(
        dossier: PartnerDossier,
        exclusionTokens: Set<String>,
        pastGiftTokens: Set<String>,
        isEn: Boolean,
    ): List<RichGiftRecommendation> {
        return dossier.secretSurpriseNotes
            .asSequence()
            .filter { !it.isPurchased && it.note.isNotBlank() }
            .filter { dossier.budgetMaxVnd <= 0L || it.estimatedPriceVnd <= 0L || it.estimatedPriceVnd <= dossier.budgetMaxVnd }
            .filterNot { note ->
                val norm = PartnerInterestCatalog.stripDiacritics(note.note.lowercase())
                norm in pastGiftTokens || exclusionTokens.any { ex -> ex.length >= 3 && norm.contains(ex) }
            }
            .take(2)
            .mapIndexed { idx, note ->
                secretHintToRecommendation(note, idx, isEn)
            }
            .toList()
    }

    private fun secretHintToRecommendation(note: SecretHintNote, idx: Int, isEn: Boolean): RichGiftRecommendation {
        val priceLabel = when {
            note.estimatedPriceVnd > 0L -> "~${note.estimatedPriceVnd / 1000L}k VND"
            isEn -> "From secret notes"
            else -> "Từ ghi chú bí mật"
        }
        return RichGiftRecommendation(
            id = "secret_${note.id}",
            title = note.note.trim(),
            category = if (isEn) "Secret Wish" else "Điều ước bí mật",
            priceRangeLabel = priceLabel,
            estimatedMinVnd = note.estimatedPriceVnd,
            estimatedMaxVnd = note.estimatedPriceVnd,
            matchScore = 100 - idx,
            reason = if (isEn) "From your saved Secret Surprise Notes"
            else "Từ Ghi chú bí mật bạn đã lưu",
            preparationPlan = if (isEn) "Check your note details and mark bought when ready"
            else "Xem lại chi tiết ghi chú và đánh dấu đã mua khi chuẩn bị xong",
            personalizationHint = note.occasionHint,
            culturalCaution = null,
            matchedInterests = emptyList(),
            matchedLoveLanguages = listOf(LoveLanguage.RECEIVING_GIFTS),
            whereToFind = note.targetUrlOrShop.ifBlank { if (isEn) "Saved shop note" else "Cửa hàng đã ghi chú" },
            leadTimeDays = 2,
        )
    }

    private fun scoreArchetype(
        arch: GiftArchetype,
        dossier: PartnerDossier,
        normalizedInterests: Set<String>,
        likeTokens: Set<String>,
        request: AiGiftRequest,
    ): RichGiftRecommendation {
        val isEn = request.isEnglish
        val matchedInterests = arch.interestKeys.filter { it in normalizedInterests }
        val matchedLanguages = arch.loveLanguages.filter { it in dossier.loveLanguages }
        val matchedLikes = likeTokens.count { token -> token in arch.keywordTags || arch.keywordTags.any { it.contains(token) } }

        var score = 10
        score += matchedInterests.size * 12
        score += matchedLanguages.size * 10
        score += matchedLikes * 6
        if (request.occasionId.lowercase() in arch.occasionTags || "any" in arch.occasionTags) score += 6

        val sizeHint = buildWearableHint(arch, dossier, isEn)
        if (sizeHint.isNotEmpty()) score += 5

        val days = request.daysUntilOccasion
        if (days != null) {
            if (days <= 2 && arch.leadTimeDays <= 1) score += 8
            if (days in 3..5 && arch.leadTimeDays <= 3) score += 4
        }

        val culturalCaution = resolveCulturalCaution(arch, dossier.occasionRegion, isEn)
        val reason = buildReasonText(arch, matchedInterests, matchedLanguages, isEn)
        val prep = buildPrepText(arch.leadTimeDays, days, isEn)

        return RichGiftRecommendation(
            id = arch.id,
            title = if (isEn) arch.titleEn else arch.titleVi,
            category = if (isEn) arch.categoryEn else arch.categoryVi,
            priceRangeLabel = if (isEn) arch.priceRangeLabelEn else arch.priceRangeLabelVi,
            estimatedMinVnd = arch.estimatedMinVnd,
            estimatedMaxVnd = arch.estimatedMaxVnd,
            matchScore = score,
            reason = reason,
            preparationPlan = prep,
            personalizationHint = sizeHint,
            culturalCaution = culturalCaution,
            matchedInterests = matchedInterests,
            matchedLoveLanguages = matchedLanguages,
            whereToFind = if (isEn) arch.whereToFindEn else arch.whereToFindVi,
            leadTimeDays = arch.leadTimeDays,
        )
    }

    private fun buildWearableHint(arch: GiftArchetype, dossier: PartnerDossier, isEn: Boolean): String {
        val sizes = dossier.sizes
        val colorHint = sizes.favoriteColors.firstOrNull()?.let {
            if (isEn) "Preferred color: $it" else "Màu ưu tiên: $it"
        }.orEmpty()
        val scentHint = sizes.favoriteScents.firstOrNull()?.let {
            if (isEn) "Preferred scent: $it" else "Mùi hương ưa thích: $it"
        }.orEmpty()
        return when (arch.wearableHintType) {
            WearableHintType.SHIRT -> listOfNotNull(
                sizes.shirtSize.takeIf { it.isNotBlank() }?.let { if (isEn) "Shirt size: $it" else "Size áo: $it" },
                sizes.pantsOrDressSize.takeIf { it.isNotBlank() }?.let { if (isEn) "Dress/pants: $it" else "Size váy/quần: $it" },
                colorHint.takeIf { it.isNotBlank() },
            ).joinToString(" • ")
            WearableHintType.PANTS_DRESS -> sizes.pantsOrDressSize.takeIf { it.isNotBlank() }?.let {
                if (isEn) "Size: $it" else "Số đo: $it"
            }.orEmpty()
            WearableHintType.SHOES -> sizes.shoeSize.takeIf { it.isNotBlank() }?.let {
                if (isEn) "Shoe size: $it" else "Size giày: $it"
            }.orEmpty()
            WearableHintType.RING -> sizes.ringSize.takeIf { it.isNotBlank() }?.let {
                if (isEn) "Ring size: $it" else "Ni nhẫn: $it"
            }.orEmpty()
            WearableHintType.SCENT -> scentHint
            WearableHintType.COLOR -> colorHint
            WearableHintType.NONE -> ""
        }
    }

    private fun resolveCulturalCaution(arch: GiftArchetype, region: String, isEn: Boolean): String? {
        val reg = region.trim().uppercase()
        return when (arch.tabooConcept) {
            "shoes" -> if (reg in setOf("VN", "CN", "TW", "HK")) {
                if (isEn) "Cultural tip: tuck a small coin inside so gifting shoes feels lucky"
                else "Mẹo nhỏ: Gửi kèm đồng xu tượng trưng khi tặng giày để thêm may mắn"
            } else null
            "clock_watch" -> if (reg in setOf("CN", "TW", "HK", "SG", "VN")) {
                if (isEn) "Cultural note: watches can feel sensitive in East Asian gifting — pair with a warm note or choose jewelry"
                else "Lưu ý văn hóa: Một số gia đình Á Đông kiêng tặng đồng hồ — hãy kèm lời nhắn riêng hoặc chọn trang sức"
            } else null
            "flowers_white" -> if (isEn) "Cultural tip: avoid white chrysanthemums and pick pastel or red roses"
            else "Lưu ý: Tránh hoa cúc trắng, ưu tiên hoa hồng đỏ hoặc tông pastel"
            else -> null
        }
    }

    private fun buildReasonText(
        arch: GiftArchetype,
        matchedInterests: List<String>,
        matchedLanguages: List<LoveLanguage>,
        isEn: Boolean,
    ): String {
        val base = if (isEn) arch.baseReasonEn else arch.baseReasonVi
        val parts = mutableListOf<String>()
        if (matchedInterests.isNotEmpty()) {
            val labels = matchedInterests.mapNotNull { key ->
                PartnerInterestCatalog.metaFor(key)?.let { if (isEn) it.labelEn else it.labelVi }
            }
            if (labels.isNotEmpty()) {
                parts += if (isEn) "Matches interests: ${labels.joinToString(", ")}"
                else "Khớp sở thích: ${labels.joinToString(", ")}"
            }
        }
        if (matchedLanguages.isNotEmpty()) {
            val langNames = matchedLanguages.joinToString(", ") { if (isEn) it.titleEn else it.titleVi }
            parts += if (isEn) "Love language fit: $langNames" else "Hợp ngôn ngữ yêu: $langNames"
        }
        return if (parts.isEmpty()) base else "${parts.joinToString(" • ")}. $base"
    }

    private fun buildPrepText(leadTimeDays: Int, daysUntil: Int?, isEn: Boolean): String {
        val dayWordEn = if (leadTimeDays == 1) "day" else "days"
        return when {
            daysUntil != null && daysUntil <= 1 -> if (isEn) "Ready today (~$leadTimeDays $dayWordEn prep)" else "Chuẩn bị trong ngày (~$leadTimeDays ngày)"
            leadTimeDays >= 5 -> if (isEn) "Order or book at least $leadTimeDays days ahead" else "Đặt trước tối thiểu $leadTimeDays ngày"
            else -> if (isEn) "Prep takes $leadTimeDays–${leadTimeDays + 1} days" else "Chuẩn bị gọn trong $leadTimeDays–${leadTimeDays + 1} ngày"
        }
    }

    private fun buildTabooWarnings(
        dossier: PartnerDossier,
        culturalVi: List<String>,
        culturalEn: List<String>,
        isEn: Boolean,
    ): List<String> {
        val out = mutableListOf<String>()
        if (dossier.foodAndCare.allergies.isNotEmpty()) {
            val list = dossier.foodAndCare.allergies.joinToString(", ")
            out += if (isEn) "Filtered allergies: $list" else "Đã lọc dị ứng: $list"
        }
        if (dossier.foodAndCare.dislikesOrTaboos.isNotEmpty()) {
            val list = dossier.foodAndCare.dislikesOrTaboos.joinToString(", ")
            out += if (isEn) "Avoided dislikes/taboos: $list" else "Đã tránh điều không thích: $list"
        }
        out += if (isEn) culturalEn else culturalVi
        return out.distinct()
    }

    private fun buildPrepTimeline(
        daysUntil: Int?,
        profile: com.example.shared.calendar.OccasionProfile,
        isEn: Boolean,
    ): List<String> {
        if (daysUntil == null) return if (isEn) profile.prepChecklistEn else profile.prepChecklistVi
        val stage = AnniversaryBehaviorEngine.calculatePreparationStage(daysUntil, profile)
        val dayUnitEn = if (daysUntil == 1) "day" else "days"
        val stageStep = when (stage) {
            PreparationStage.TODAY_CELEBRATION -> if (isEn) "Today: focus on time together and a handwritten note" else "Hôm nay: Dành trọn thời gian bên nhau và lời chúc viết tay"
            PreparationStage.URGENT_LAST_MINUTE -> if (isEn) "$daysUntil $dayUnitEn left: pick in-stock items or instant e-tickets" else "Còn $daysUntil ngày: Chọn quà có sẵn hoặc vé đặt ngay"
            PreparationStage.ACTION_NEEDED -> if (isEn) "$daysUntil $dayUnitEn left: finalize your gift and confirm plans" else "Còn $daysUntil ngày: Chốt quà và xác nhận lịch hẹn"
            PreparationStage.PLAN_AHEAD_BOOKING -> if (isEn) "$daysUntil $dayUnitEn left: book dining or custom orders early" else "Còn $daysUntil ngày: Đặt chỗ đẹp và quà làm riêng sớm"
            PreparationStage.RELAXED -> if (isEn) "$daysUntil $dayUnitEn left: save hints whenever they mention a favorite" else "Còn $daysUntil ngày: Lưu lại gợi ý khi người ấy nhắc điều thích"
        }
        return listOf(stageStep) + (if (isEn) profile.prepChecklistEn else profile.prepChecklistVi)
    }

    private fun buildLoveNotes(request: AiGiftRequest, isEn: Boolean): List<LoveNoteDraft> {
        val partner = request.dossier.nickname.ifBlank { if (isEn) "my love" else "em yêu" }
        val occasion = request.occasionTitle.ifBlank { if (isEn) "our special day" else "ngày đặc biệt của hai đứa" }
        val daysLine = if (request.loveDays > 0) {
            if (isEn) "Through ${request.loveDays} days side by side, " else "Suốt ${request.loveDays} ngày đồng hành, "
        } else ""

        val allTones = listOf(request.tone) + RelationshipTone.entries.filterNot { it == request.tone }.take(2)
        return allTones.mapIndexed { idx, tone ->
            when (tone) {
                RelationshipTone.SWEET -> LoveNoteDraft(
                    id = "note_sweet_$idx",
                    tone = tone,
                    title = if (isEn) "Sweet Note" else "Lời chúc ngọt ngào",
                    message = if (isEn)
                        "Happy $occasion, $partner. ${daysLine}the quiet everyday moments with you remain my favorite part of every day."
                    else
                        "Chúc $partner $occasion thật ấm áp. ${daysLine}những khoảnh khắc bình yên bên nhau vẫn luôn là điều mình trân trọng nhất.",
                    occasionLabel = occasion,
                )
                RelationshipTone.PLAYFUL -> LoveNoteDraft(
                    id = "note_playful_$idx",
                    tone = tone,
                    title = if (isEn) "Playful Note" else "Lời chúc dí dỏm",
                    message = if (isEn)
                        "Happy $occasion, $partner. Still choosing you today, tomorrow, and for every coffee run after."
                    else
                        "$occasion vui vẻ nha $partner. Hôm nay tiếp tục ưu tiên ở bên nhau và ăn một bữa thật ngon nhé.",
                    occasionLabel = occasion,
                )
                RelationshipTone.DEEPLY_ROMANTIC -> LoveNoteDraft(
                    id = "note_deep_$idx",
                    tone = tone,
                    title = if (isEn) "Deeply Romantic" else "Lời chúc sâu lắng",
                    message = if (isEn)
                        "Dearest $partner, on $occasion I am grateful for the calm, steadiness, and warmth you bring into my life."
                    else
                        "$partner thương mến, nhân $occasion, cảm ơn vì đã luôn là sự bình yên và chỗ dựa dịu dàng nhất bên cạnh mình.",
                    occasionLabel = occasion,
                )
                RelationshipTone.THOUGHTFUL -> LoveNoteDraft(
                    id = "note_thoughtful_$idx",
                    tone = tone,
                    title = if (isEn) "Thoughtful Note" else "Lời chúc tinh tế",
                    message = if (isEn)
                        "Wishing you an unhurried, joyful $occasion, $partner. Hope today brings everything you have been looking forward to."
                    else
                        "Chúc $partner một $occasion nhẹ nhàng và trọn vẹn. Mong hôm nay sẽ mang đến đúng những điều bạn đang mong chờ.",
                    occasionLabel = occasion,
                )
            }
        }
    }

    private fun buildHeadline(request: AiGiftRequest, count: Int, isEn: Boolean): String {
        val occ = request.occasionTitle.ifBlank { if (isEn) "your occasion" else "dịp đặc biệt" }
        return if (isEn) "$count gift ideas for $occ, matched to their profile"
        else "$count gợi ý cho $occ dựa trên hồ sơ người thương"
    }
}
