package com.example.shared.ai

import com.example.shared.dossier.PartnerDossier

data class AiPromptPayload(
    val systemInstruction: String,
    val userPrompt: String,
    val responseJsonSchema: String,
    val sanitizedProfileSummary: String,
)

/**
 * Builds PII-free prompts and strict JSON schema instructions for Cloud AI (Gemini / Cloud Functions).
 * Never sends real names, emails, phone numbers, raw secret URLs, or personal identifiers.
 */
object AiPromptBuilder {

    private val emailRegex = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val urlRegex = Regex("""https?://\S+""")
    private val phoneRegex = Regex("""\+?\d[\d\s\-]{7,}\d""")

    val RESPONSE_JSON_SCHEMA: String = """
        {
          "type": "object",
          "required": ["summaryHeadline", "recommendations", "loveNotes", "tabooWarnings", "prepTimeline"],
          "properties": {
            "summaryHeadline": { "type": "string" },
            "recommendations": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["id", "title", "category", "priceRangeLabel", "reason", "preparationPlan", "whereToFind", "leadTimeDays"],
                "properties": {
                  "id": { "type": "string" },
                  "title": { "type": "string" },
                  "category": { "type": "string" },
                  "priceRangeLabel": { "type": "string" },
                  "estimatedMinVnd": { "type": "integer" },
                  "estimatedMaxVnd": { "type": "integer" },
                  "reason": { "type": "string" },
                  "preparationPlan": { "type": "string" },
                  "personalizationHint": { "type": "string" },
                  "culturalCaution": { "type": "string" },
                  "whereToFind": { "type": "string" },
                  "leadTimeDays": { "type": "integer" }
                }
              }
            },
            "loveNotes": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["id", "tone", "title", "message"],
                "properties": {
                  "id": { "type": "string" },
                  "tone": { "type": "string", "enum": ["SWEET", "PLAYFUL", "DEEPLY_ROMANTIC", "THOUGHTFUL"] },
                  "title": { "type": "string" },
                  "message": { "type": "string" }
                }
              }
            },
            "tabooWarnings": { "type": "array", "items": { "type": "string" } },
            "prepTimeline": { "type": "array", "items": { "type": "string" } }
          }
        }
    """.trimIndent()

    fun build(request: AiGiftRequest): AiPromptPayload {
        val dossier = request.dossier
        val langLabel = if (request.isEnglish) "English (en)" else "Vietnamese (vi)"
        val summary = buildSanitizedProfileSummary(dossier)

        val systemInstruction = """
            You are InLove Concierge, a warm, culturally aware romantic gift & love-note advisor.
            Output valid JSON strictly matching the schema. Never include PII or real names.
            Respect all allergies, dislikes, budget limits, and cultural taboos strictly.
            Use {{PARTNER}} as placeholder if referring to the partner directly in notes.
            Language: $langLabel.
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Occasion: ${sanitizeFreeText(request.occasionTitle)} (id=${request.occasionId})")
            appendLine("Region: ${dossier.occasionRegion.ifBlank { "VN" }}")
            appendLine("Days until occasion: ${request.daysUntilOccasion ?: "unknown"}")
            appendLine("Love days together: ${request.loveDays}")
            appendLine("Requested tone: ${request.tone.name}")
            appendLine("Max recommendations: ${request.maxRecommendations.coerceIn(1, 8)}")
            appendLine("Budget cap (VND): ${if (dossier.budgetMaxVnd > 0L) dossier.budgetMaxVnd else "flexible"}")
            appendLine("Partner profile (anonymized):")
            appendLine(summary)
            if (request.excludedGiftTitles.isNotEmpty() || dossier.giftHistory.isNotEmpty()) {
                val excluded = (request.excludedGiftTitles + dossier.giftHistory.map { it.title })
                    .map { sanitizeFreeText(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(15)
                appendLine("Exclude past/duplicate gifts: ${excluded.joinToString(", ")}")
            }
        }.trimEnd()

        return AiPromptPayload(
            systemInstruction = systemInstruction,
            userPrompt = userPrompt,
            responseJsonSchema = RESPONSE_JSON_SCHEMA,
            sanitizedProfileSummary = summary,
        )
    }

    fun buildSanitizedProfileSummary(dossier: PartnerDossier): String = buildString {
        appendLine("- Love languages: ${dossier.loveLanguages.joinToString(", ") { it.name }.ifBlank { "unspecified" }}")
        appendLine("- Interest keys: ${dossier.interestCategories.joinToString(", ").ifBlank { "unspecified" }}")
        val sanitizedLikes = sanitizeFreeText(dossier.freeformLikes)
        if (sanitizedLikes.isNotBlank()) appendLine("- Taste keywords: $sanitizedLikes")
        val colors = dossier.sizes.favoriteColors.map(::sanitizeFreeText).filter { it.isNotBlank() }
        if (colors.isNotEmpty()) appendLine("- Favorite colors: ${colors.joinToString(", ")}")
        val scents = dossier.sizes.favoriteScents.map(::sanitizeFreeText).filter { it.isNotBlank() }
        if (scents.isNotEmpty()) appendLine("- Favorite scents: ${scents.joinToString(", ")}")
        val sizeFlags = buildList {
            if (dossier.sizes.shirtSize.isNotBlank()) add("shirt:${sanitizeFreeText(dossier.sizes.shirtSize)}")
            if (dossier.sizes.pantsOrDressSize.isNotBlank()) add("bottom:${sanitizeFreeText(dossier.sizes.pantsOrDressSize)}")
            if (dossier.sizes.shoeSize.isNotBlank()) add("shoes:${sanitizeFreeText(dossier.sizes.shoeSize)}")
            if (dossier.sizes.ringSize.isNotBlank()) add("ring:${sanitizeFreeText(dossier.sizes.ringSize)}")
        }
        if (sizeFlags.isNotEmpty()) appendLine("- Sizes: ${sizeFlags.joinToString(", ")}")
        val allergies = dossier.foodAndCare.allergies.map(::sanitizeFreeText).filter { it.isNotBlank() }
        if (allergies.isNotEmpty()) appendLine("- STRICT ALLERGIES TO EXCLUDE: ${allergies.joinToString(", ")}")
        val dislikes = dossier.foodAndCare.dislikesOrTaboos.map(::sanitizeFreeText).filter { it.isNotBlank() }
        if (dislikes.isNotEmpty()) appendLine("- Dislikes/taboos to exclude: ${dislikes.joinToString(", ")}")
        if (dossier.foodAndCare.goToDrinks.isNotBlank()) {
            appendLine("- Go-to drink: ${sanitizeFreeText(dossier.foodAndCare.goToDrinks)}")
        }
    }.trimEnd()

    fun sanitizeFreeText(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(emailRegex, "[redacted]")
            .replace(urlRegex, "[link]")
            .replace(phoneRegex, "[redacted]")
            .trim()
            .take(180)
    }
}
