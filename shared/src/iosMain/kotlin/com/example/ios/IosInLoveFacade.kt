package com.example.ios

import com.example.shared.ai.AiGiftAdviceJson
import com.example.shared.ai.AiGiftRequest
import com.example.shared.ai.AiPromptBuilder
import com.example.shared.ai.RelationshipTone
import com.example.shared.ai.SmartRuleGiftAdvisor
import com.example.shared.calendar.AnniversaryBehaviorEngine
import com.example.shared.calendar.DayMilestoneSpec
import com.example.shared.calendar.LunarCalendar2025To2040
import com.example.shared.calendar.LunarHolidayId
import com.example.shared.calendar.MonthiversaryOccurrence
import com.example.shared.calendar.NextOccasionReadiness
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerDossierJson
import com.example.shared.dossier.PartnerInterestCatalog
import com.example.shared.dossier.SecretHintNote
import com.example.shared.dossier.completenessPercent
import com.example.shared.dossier.effectiveLikesCsv
import com.example.shared.dossier.missingProfileHints
import com.example.shared.guide.GuideChapter
import com.example.shared.guide.GuideChapterId
import com.example.shared.guide.InLoveGuideCatalog
import com.example.shared.monetization.AdDecision
import com.example.shared.monetization.AdPlacementPolicy
import com.example.shared.monetization.AdPlacementType
import com.example.shared.monetization.AdSessionState
import com.example.shared.monetization.ProFeature
import com.example.shared.monetization.ProPlanSpec
import com.example.shared.monetization.ProTierCatalog
import com.example.shared.monetization.SacredZone
import com.example.shared.monetization.SoftPaywallPrompt
import com.example.shared.monetization.UpsellTrigger
import com.example.shared.sync.CoupleCodeParser
import com.example.shared.sync.DossierMergePolicy
import com.example.shared.sync.OnlineSyncPhase
import com.example.shared.sync.OnlineSyncPhaseResolver
import com.example.shared.widget.CoupleWidgetSnapshot
import com.example.shared.widget.CoupleWidgetSnapshotJson
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val bridgeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

@Serializable
private data class MilestoneBridgeDto(
    val days: Int,
    val titleVi: String,
    val titleEn: String,
    val badgeEmoji: String,
    val isMajor: Boolean,
    val targetDateIso: String,
)

@Serializable
private data class PromptBridgeDto(
    val systemInstruction: String,
    val userPrompt: String,
    val responseJsonSchema: String,
    val sanitizedProfileSummary: String,
)

private fun parseIsoOrDmyDate(raw: String): LocalDate {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) throw IllegalArgumentException("Date string must not be blank")
    if (trimmed.length == 10 && trimmed[4] == '-' && trimmed[7] == '-') {
        return LocalDate.parse(trimmed)
    }
    val parts = trimmed.split('/', '-', '.')
    if (parts.size == 3) {
        val a = parts[0].toInt()
        val b = parts[1].toInt()
        val c = parts[2].toInt()
        return if (parts[0].length == 4) LocalDate(a, b, c) else LocalDate(c, b, a)
    }
    return LocalDate.parse(trimmed)
}

private fun parseOptionalDate(raw: String): LocalDate? =
    if (raw.isBlank()) null else runCatching { parseIsoOrDmyDate(raw) }.getOrNull()

interface IosJsonCallback { fun onResult(json: String) } // no-throw: callback into Swift

/**
 * Pure KMP love day counter & milestone facade for Swift/SwiftUI.
 */
class IosLoveCalculatorFacade {

    @Throws(Exception::class)
    fun loveDaysBetween(startDateIso: String, todayIso: String): Int {
        val start = parseIsoOrDmyDate(startDateIso)
        val today = parseIsoOrDmyDate(todayIso)
        return AnniversaryBehaviorEngine.loveDaysOn(start, today) ?: 0
    }

    @Throws(Exception::class)
    fun milestoneDateIso(startDateIso: String, dayNumber: Int): String {
        val start = parseIsoOrDmyDate(startDateIso)
        return AnniversaryBehaviorEngine.dateForMilestoneDay(start, dayNumber).toString()
    }

    fun supportedMilestoneDays(): List<Int> = AnniversaryBehaviorEngine.MILESTONE_DAYS // no-throw: immutable list

    @Throws(Exception::class)
    fun nextDayMilestoneJson(startDateIso: String, todayIso: String): String {
        val start = parseIsoOrDmyDate(startDateIso)
        val today = parseIsoOrDmyDate(todayIso)
        val pair: Pair<DayMilestoneSpec, LocalDate> = AnniversaryBehaviorEngine.nextDayMilestone(start, today) ?: return ""
        val dto = MilestoneBridgeDto(
            days = pair.first.days,
            titleVi = pair.first.titleVi,
            titleEn = pair.first.titleEn,
            badgeEmoji = pair.first.badgeEmoji,
            isMajor = pair.first.isMajor,
            targetDateIso = pair.second.toString(),
        )
        return bridgeJson.encodeToString(MilestoneBridgeDto.serializer(), dto)
    }

    @Throws(Exception::class)
    fun nextMonthiversaryJson(startDateIso: String, todayIso: String): String {
        val start = parseIsoOrDmyDate(startDateIso)
        val today = parseIsoOrDmyDate(todayIso)
        val occurrence = AnniversaryBehaviorEngine.nextMonthiversary(start, today)
        return bridgeJson.encodeToString(MonthiversaryOccurrence.serializer(), occurrence)
    }
}

/**
 * Pure KMP Lunar calendar (2025-2040) & 3-axis occasion readiness facade for Swift.
 */
class IosOccasionEngineFacade {

    @Throws(Exception::class)
    fun lunarHolidaySolarDateIso(holidayId: String, year: Int): String {
        val parsed = LunarHolidayId.valueOf(holidayId.trim().uppercase())
        return LunarCalendar2025To2040.solarDate(parsed, year)?.toString() ?: ""
    }

    fun allLunarHolidaysForYearJson(year: Int): String { // no-throw: pure lookup and JSON encoding
        val rawMap = LunarCalendar2025To2040.allForYear(year)
            .entries
            .associate { (k, v) -> k.name to v.toString() }
        return bridgeJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), rawMap)
    }

    @Throws(Exception::class)
    fun nextOccasionReadinessJson(
        todayIso: String,
        loveStartDateIso: String,
        partnerBirthdayIso: String,
        region: String,
        includeMonthiversary: Boolean,
    ): String {
        val today = parseIsoOrDmyDate(todayIso)
        val loveStart = parseOptionalDate(loveStartDateIso)
        val birthday = parseOptionalDate(partnerBirthdayIso)
        val readiness = AnniversaryBehaviorEngine.computeNextOccasionReadiness(
            today = today,
            loveStartDate = loveStart,
            partnerBirthday = birthday,
            region = region,
            disabledOccasionIds = emptySet(),
            includeMonthiversary = includeMonthiversary,
        ) ?: return ""
        return bridgeJson.encodeToString(NextOccasionReadiness.serializer(), readiness)
    }

    @Throws(Exception::class)
    fun upcomingOccasionsJson(
        todayIso: String,
        loveStartDateIso: String,
        partnerBirthdayIso: String,
        region: String,
        withinDays: Int,
        includeMonthiversary: Boolean,
    ): String {
        val today = parseIsoOrDmyDate(todayIso)
        val loveStart = parseOptionalDate(loveStartDateIso)
        val birthday = parseOptionalDate(partnerBirthdayIso)
        val list = AnniversaryBehaviorEngine.upcomingOccasions(
            today = today,
            loveStartDate = loveStart,
            partnerBirthday = birthday,
            region = region,
            withinDays = withinDays,
            disabledOccasionIds = emptySet(),
            includeMonthiversary = includeMonthiversary,
        )
        return bridgeJson.encodeToString(ListSerializer(NextOccasionReadiness.serializer()), list)
    }

    fun isSacredCelebrationToday(todayIso: String, loveStartDateIso: String, partnerBirthdayIso: String): Boolean { // no-throw: returns false on invalid date
        val today = parseOptionalDate(todayIso) ?: return false
        val loveStart = parseOptionalDate(loveStartDateIso)
        val birthday = parseOptionalDate(partnerBirthdayIso)
        return AnniversaryBehaviorEngine.isSacredCelebrationToday(today, loveStart, birthday)
    }
}

/**
 * Pure KMP Partner Dossier facade operating on canonical JSON strings.
 */
class IosPartnerDossierFacade {

    fun emptyDossierJson(scopeId: String): String = // no-throw: constructs default JSON
        PartnerDossierJson.encode(PartnerDossier(scopeId = scopeId.ifBlank { "guest" }))

    fun completenessPercent(dossierJson: String): Int = // no-throw: falls back to default dossier
        PartnerDossierJson.decodeOrDefault(dossierJson).completenessPercent()

    fun missingProfileHintsJson(dossierJson: String, isEnglish: Boolean): String { // no-throw: pure hint computation
        val hints = PartnerDossierJson.decodeOrDefault(dossierJson).missingProfileHints(isEnglish)
        return bridgeJson.encodeToString(ListSerializer(String.serializer()), hints)
    }

    fun effectiveLikesCsv(dossierJson: String): String = // no-throw: pure CSV conversion
        PartnerDossierJson.decodeOrDefault(dossierJson).effectiveLikesCsv()

    fun standardInterestKeys(): List<String> = PartnerInterestCatalog.STANDARD_INTEREST_KEYS // no-throw: immutable list

    fun normalizeInterestKey(raw: String): String = PartnerInterestCatalog.normalizeInterestKey(raw) // no-throw: pure string mapping

    fun addSecretNoteToJson( // no-throw: pure JSON transformation
        dossierJson: String,
        id: String,
        note: String,
        occasionHint: String,
        estimatedPriceVnd: Long,
        targetUrlOrShop: String,
        createdAtEpochMs: Long,
    ): String {
        val current = PartnerDossierJson.decodeOrDefault(dossierJson)
        val item = SecretHintNote(
            id = id,
            note = note,
            occasionHint = occasionHint,
            estimatedPriceVnd = estimatedPriceVnd,
            targetUrlOrShop = targetUrlOrShop,
            isPurchased = false,
            createdAtEpochMs = createdAtEpochMs,
        )
        val updated = current.copy(
            secretSurpriseNotes = listOf(item) + current.secretSurpriseNotes.filterNot { it.id == id },
            updatedAtEpochMs = createdAtEpochMs,
        )
        return PartnerDossierJson.encode(updated)
    }

    fun toggleSecretNotePurchasedInJson(dossierJson: String, noteId: String, purchased: Boolean): String { // no-throw: pure JSON transformation
        val current = PartnerDossierJson.decodeOrDefault(dossierJson)
        val updated = current.copy(
            secretSurpriseNotes = current.secretSurpriseNotes.map { item ->
                if (item.id == noteId) item.copy(isPurchased = purchased) else item
            },
        )
        return PartnerDossierJson.encode(updated)
    }
}

/**
 * Pure KMP Gift & Love-Note Concierge facade for Swift (smart offline engine + sanitized Cloud prompt).
 */
class IosGiftConciergeFacade {

    @Throws(Exception::class)
    fun generateLocalAdviceJson(
        dossierJson: String,
        occasionId: String,
        occasionTitle: String,
        daysUntilOccasion: Int,
        loveDays: Int,
        toneName: String,
        isEnglish: Boolean,
        maxRecommendations: Int,
        nowEpochMs: Long,
    ): String {
        val request = buildRequest(
            dossierJson = dossierJson,
            occasionId = occasionId,
            occasionTitle = occasionTitle,
            daysUntilOccasion = daysUntilOccasion,
            loveDays = loveDays,
            toneName = toneName,
            isEnglish = isEnglish,
            maxRecommendations = maxRecommendations,
        )
        val bundle = SmartRuleGiftAdvisor.generate(request, nowEpochMs)
        return AiGiftAdviceJson.encodeBundle(bundle)
    }

    @Throws(Exception::class)
    fun buildSanitizedCloudPromptJson(
        dossierJson: String,
        occasionId: String,
        occasionTitle: String,
        daysUntilOccasion: Int,
        loveDays: Int,
        toneName: String,
        isEnglish: Boolean,
        maxRecommendations: Int,
    ): String {
        val request = buildRequest(
            dossierJson = dossierJson,
            occasionId = occasionId,
            occasionTitle = occasionTitle,
            daysUntilOccasion = daysUntilOccasion,
            loveDays = loveDays,
            toneName = toneName,
            isEnglish = isEnglish,
            maxRecommendations = maxRecommendations,
        )
        val payload = AiPromptBuilder.build(request)
        val dto = PromptBridgeDto(
            systemInstruction = payload.systemInstruction,
            userPrompt = payload.userPrompt,
            responseJsonSchema = payload.responseJsonSchema,
            sanitizedProfileSummary = payload.sanitizedProfileSummary,
        )
        return bridgeJson.encodeToString(PromptBridgeDto.serializer(), dto)
    }

    fun observeLocalAdvice( // no-throw: catches errors and delivers fallback JSON to callback
        dossierJson: String,
        occasionId: String,
        occasionTitle: String,
        daysUntilOccasion: Int,
        loveDays: Int,
        toneName: String,
        isEnglish: Boolean,
        maxRecommendations: Int,
        nowEpochMs: Long,
        callback: IosJsonCallback,
    ) {
        val json = runCatching {
            generateLocalAdviceJson(
                dossierJson = dossierJson,
                occasionId = occasionId,
                occasionTitle = occasionTitle,
                daysUntilOccasion = daysUntilOccasion,
                loveDays = loveDays,
                toneName = toneName,
                isEnglish = isEnglish,
                maxRecommendations = maxRecommendations,
                nowEpochMs = nowEpochMs,
            )
        }.getOrElse { "{}" }
        callback.onResult(json)
    }

    private fun buildRequest(
        dossierJson: String,
        occasionId: String,
        occasionTitle: String,
        daysUntilOccasion: Int,
        loveDays: Int,
        toneName: String,
        isEnglish: Boolean,
        maxRecommendations: Int,
    ): AiGiftRequest {
        val dossier = PartnerDossierJson.decodeOrDefault(dossierJson)
        val tone = runCatching { RelationshipTone.valueOf(toneName.trim().uppercase()) }
            .getOrDefault(RelationshipTone.SWEET)
        return AiGiftRequest(
            dossier = dossier,
            occasionId = occasionId,
            occasionTitle = occasionTitle,
            daysUntilOccasion = if (daysUntilOccasion < 0) null else daysUntilOccasion,
            loveDays = loveDays,
            tone = tone,
            isEnglish = isEnglish,
            maxRecommendations = maxRecommendations,
            excludedGiftTitles = emptySet(),
        )
    }
}

/**
 * Pure KMP monetization, SacredZone ad guardrail & soft-paywall policy facade for Swift.
 */
class IosMonetizationPolicyFacade {

    fun allProSkus(): List<String> = ProTierCatalog.ALL_SKUS.sorted() // no-throw: immutable SKU list

    fun isProSku(productId: String): Boolean = ProTierCatalog.isVipSku(productId) // no-throw: pure lookup

    fun freeAiGenerationsLimit(): Int = ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH // no-throw: constant value

    fun freeSecretNotesLimit(): Int = ProTierCatalog.FREE_SECRET_NOTES_LIMIT // no-throw: constant value

    fun isFeatureUnlocked(featureName: String, isVip: Boolean, currentUsageCount: Int): Boolean { // no-throw: safe enum fallback
        val feature = runCatching { ProFeature.valueOf(featureName.trim().uppercase()) }.getOrNull() ?: return isVip
        return feature.isUnlocked(isVip = isVip, currentUsageCount = currentUsageCount)
    }

    fun evaluateAdPlacementJson( // no-throw: pure ad policy evaluation
        placementType: String,
        isVip: Boolean,
        hasConsent: Boolean,
        activeSacredZoneNames: List<String>,
        sessionStartedAtMs: Long,
        lastInterstitialShownAtMs: Long,
        interstitialsShownThisSession: Int,
        isCelebrationDay: Boolean,
        nowMs: Long,
    ): String {
        val type = runCatching { AdPlacementType.valueOf(placementType.trim().uppercase()) }
            .getOrDefault(AdPlacementType.INTERSTITIAL)
        val zones = activeSacredZoneNames.mapNotNull { name ->
            runCatching { SacredZone.valueOf(name.trim().uppercase()) }.getOrNull()
        }.toSet()
        val state = AdSessionState(
            isVip = isVip,
            hasConsent = hasConsent,
            activeSacredZones = zones,
            sessionStartedAtMs = sessionStartedAtMs,
            lastInterstitialShownAtMs = lastInterstitialShownAtMs,
            interstitialsShownThisSession = interstitialsShownThisSession,
            isCelebrationDay = isCelebrationDay,
        )
        val decision = AdPlacementPolicy.evaluate(type, state, nowMs)
        return bridgeJson.encodeToString(AdDecision.serializer(), decision)
    }

    fun softPaywallUpsellJson(triggerName: String, isVip: Boolean, currentCount: Int): String { // no-throw: pure upsell evaluation
        val trigger = runCatching { UpsellTrigger.valueOf(triggerName.trim().uppercase()) }.getOrNull() ?: return ""
        val prompt = AdPlacementPolicy.softPaywallUpsell(trigger, isVip, currentCount) ?: return ""
        return bridgeJson.encodeToString(SoftPaywallPrompt.serializer(), prompt)
    }

    fun proPlansJson(): String { // no-throw: serializes static ProTierCatalog plans
        return bridgeJson.encodeToString(ListSerializer(ProPlanSpec.serializer()), ProTierCatalog.PLANS)
    }
}

/**
 * Pure KMP online couple pairing, sync phase, and offline-to-cloud dossier merge facade for Swift.
 */
class IosOnlineSyncFacade {

    fun extractCoupleCode(raw: String): String = CoupleCodeParser.extractCode(raw) // no-throw: pure string normalization

    fun resolveSyncPhaseName( // no-throw: pure enum resolution
        isSignedIn: Boolean,
        isCoupled: Boolean,
        isBreakupPending: Boolean,
        hasOutgoingInvite: Boolean,
        hasIncomingInvite: Boolean,
        isOnline: Boolean,
    ): String = OnlineSyncPhaseResolver.resolve(
        isSignedIn = isSignedIn,
        isCoupled = isCoupled,
        isBreakupPending = isBreakupPending,
        hasOutgoingInvite = hasOutgoingInvite,
        hasIncomingInvite = hasIncomingInvite,
        isOnline = isOnline,
    ).name

    fun syncPhaseLabel(phaseName: String, isEnglish: Boolean): String { // no-throw: safe enum fallback
        val phase = runCatching { OnlineSyncPhase.valueOf(phaseName.trim().uppercase()) }
            .getOrDefault(OnlineSyncPhase.GUEST_LOCAL)
        return phase.statusLabel(isEnglish)
    }

    fun mergeDossierJson(localJson: String, remoteJson: String): String { // no-throw: safe JSON decode and merge
        val local = PartnerDossierJson.decodeOrDefault(localJson)
        val remote = PartnerDossierJson.decodeOrNull(remoteJson) ?: return PartnerDossierJson.encode(local)
        return PartnerDossierJson.encode(DossierMergePolicy.merge(local = local, remote = remote))
    }
}

/**
 * Pure KMP 4-chapter user guide catalog facade for Swift.
 */
class IosGuideFacade {

    fun allChaptersJson(): String { // no-throw: serializes static 4-chapter guide catalog
        return bridgeJson.encodeToString(ListSerializer(GuideChapter.serializer()), InLoveGuideCatalog.CHAPTERS)
    }

    @Throws(Exception::class)
    fun chapterJson(chapterId: String): String {
        val parsed = GuideChapterId.valueOf(chapterId.trim().uppercase())
        return bridgeJson.encodeToString(GuideChapter.serializer(), InLoveGuideCatalog.chapterFor(parsed))
    }
}

/**
 * Pure KMP WidgetKit JSON snapshot encoder/decoder matching App Group widget_snapshot_v1.json.
 */
class IosWidgetSnapshotCodec {

    fun encodeSnapshotJson( // no-throw: pure JSON serialization
        version: Int,
        coupleTitle: String,
        partner1Name: String,
        partner2Name: String,
        loveDays: Int,
        startDateIso: String,
        nextOccasionTitle: String,
        nextOccasionDaysRemaining: Int,
        hasNextOccasion: Boolean,
        redacted: Boolean,
        updatedAtEpochMs: Long,
    ): String {
        val snapshot = CoupleWidgetSnapshot(
            version = version,
            coupleTitle = coupleTitle,
            partner1Name = partner1Name,
            partner2Name = partner2Name,
            loveDays = loveDays,
            startDateIso = startDateIso,
            nextOccasionTitle = nextOccasionTitle,
            nextOccasionDaysRemaining = if (hasNextOccasion) nextOccasionDaysRemaining else null,
            redacted = redacted,
            updatedAtEpochMs = updatedAtEpochMs,
        )
        return CoupleWidgetSnapshotJson.encode(snapshot)
    }

    fun decodeSnapshotOrDefaultJson(rawJson: String, isEnglish: Boolean): String { // no-throw: safe decode with redaction
        val decoded = CoupleWidgetSnapshotJson.decodeOrDefault(rawJson).withPrivacyRedaction(isEnglish)
        return CoupleWidgetSnapshotJson.encode(decoded)
    }
}
