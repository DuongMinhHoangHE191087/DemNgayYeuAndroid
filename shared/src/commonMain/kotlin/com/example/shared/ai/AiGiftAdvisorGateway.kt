package com.example.shared.ai

import com.example.shared.dossier.LoveLanguage
import com.example.shared.dossier.PartnerDossier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class RelationshipTone(
    val titleVi: String,
    val titleEn: String,
) {
    SWEET("Ngọt ngào", "Sweet"),
    PLAYFUL("Hóm hỉnh", "Playful"),
    DEEPLY_ROMANTIC("Sâu lắng", "Deeply Romantic"),
    THOUGHTFUL("Tinh tế & Chân thành", "Thoughtful"),
}

@Serializable
enum class AdviceSource {
    CLOUD_AI,
    SMART_LOCAL_ENGINE,
}

@Serializable
data class RichGiftRecommendation(
    val id: String,
    val title: String,
    val category: String,
    val priceRangeLabel: String,
    val estimatedMinVnd: Long = 0L,
    val estimatedMaxVnd: Long = 0L,
    val matchScore: Int = 0,
    val reason: String,
    val preparationPlan: String,
    val personalizationHint: String = "",
    val culturalCaution: String? = null,
    val matchedInterests: List<String> = emptyList(),
    val matchedLoveLanguages: List<LoveLanguage> = emptyList(),
    val whereToFind: String = "",
    val leadTimeDays: Int = 1,
)

@Serializable
data class LoveNoteDraft(
    val id: String,
    val tone: RelationshipTone,
    val title: String,
    val message: String,
    val occasionLabel: String = "",
)

@Serializable
data class AiGiftRequest(
    val dossier: PartnerDossier,
    val occasionId: String,
    val occasionTitle: String,
    val daysUntilOccasion: Int? = null,
    val loveDays: Int = 0,
    val tone: RelationshipTone = RelationshipTone.SWEET,
    val isEnglish: Boolean = false,
    val maxRecommendations: Int = 5,
    val excludedGiftTitles: Set<String> = emptySet(),
)

@Serializable
data class AiGiftAdviceBundle(
    val source: AdviceSource,
    val occasionId: String,
    val occasionTitle: String,
    val summaryHeadline: String,
    val recommendations: List<RichGiftRecommendation>,
    val loveNotes: List<LoveNoteDraft>,
    val tabooWarnings: List<String> = emptyList(),
    val prepTimeline: List<String> = emptyList(),
    val usedFreeAllowance: Boolean = false,
    val remainingFreeAllowance: Int? = null,
    val generatedAtEpochMs: Long = 0L,
)

/**
 * Pure KMP gateway contract for Gift & Love-Note Concierge (Cloud Gemini or Smart Local fallback).
 */
interface AiGiftAdvisorGateway {
    suspend fun advise(request: AiGiftRequest): AiGiftAdviceBundle
}

object AiGiftAdviceJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encodeRequest(request: AiGiftRequest): String =
        json.encodeToString(AiGiftRequest.serializer(), request)

    fun encodeBundle(bundle: AiGiftAdviceBundle): String =
        json.encodeToString(AiGiftAdviceBundle.serializer(), bundle)

    fun decodeBundleOrNull(raw: String): AiGiftAdviceBundle? =
        if (raw.isBlank()) null else runCatching {
            json.decodeFromString(AiGiftAdviceBundle.serializer(), raw)
        }.getOrNull()
}
