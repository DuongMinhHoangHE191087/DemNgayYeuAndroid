package com.example.data.ai

import android.content.SharedPreferences
import com.example.data.cloud.CloudFunctionsClient
import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.shared.ai.AdviceSource
import com.example.shared.ai.AiGiftAdviceBundle
import com.example.shared.ai.AiGiftAdvisorGateway
import com.example.shared.ai.AiGiftRequest
import com.example.shared.ai.AiPromptBuilder
import com.example.shared.ai.LoveNoteDraft
import com.example.shared.ai.RelationshipTone
import com.example.shared.ai.RichGiftRecommendation
import com.example.shared.ai.SmartRuleGiftAdvisor
import com.example.shared.dossier.PartnerInterestCatalog
import com.example.shared.monetization.ProTierCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneOffset

/**
 * Hybrid implementation of `:shared`'s [AiGiftAdvisorGateway]:
 * - When signed-in and online, invokes Firebase Cloud Function `generateGiftAdvice` (Gemini 2.5 Flash
 *   with server-side smart fallback and monthly quota enforcement: 2 free/mo, 60 VIP/mo).
 * - When offline, guest, or when the Cloud call is unavailable/quota-gated, seamlessly falls back to
 *   `:shared`'s pure [SmartRuleGiftAdvisor] while tracking monthly free starter allowance locally.
 * - Never sends PII (names, emails, phones, or private secret-note URLs) to Cloud Functions.
 */
class HybridAiGiftAdvisorRepository(
    private val cloudFunctions: CloudFunctionsClient,
    private val prefs: SharedPreferences,
    private val currentUidProvider: () -> String? = { null },
    private val isOnlineProvider: () -> Boolean = { true },
    private val isVipProvider: () -> Boolean = { false },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val localFallback: AiGiftAdvisorGateway = SmartRuleGiftAdvisor,
) : AiGiftAdvisorGateway {

    private val _freeUsageThisMonth = MutableStateFlow(readCurrentMonthUsedCount())
    val freeUsageThisMonth: StateFlow<Int> = _freeUsageThisMonth.asStateFlow()

    fun refreshQuotaState() {
        _freeUsageThisMonth.value = readCurrentMonthUsedCount()
    }

    fun readCurrentMonthUsedCount(): Int {
        val key = quotaKey(currentScopeTag(), currentMonthKeyUtc(clock()))
        return prefs.getInt(key, 0).coerceAtLeast(0)
    }

    fun remainingFreeAllowance(): Int {
        if (isVipProvider()) return ProTierCatalog.VIP_AI_GENERATIONS_PER_MONTH
        return (ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH - readCurrentMonthUsedCount()).coerceAtLeast(0)
    }

    override suspend fun advise(request: AiGiftRequest): AiGiftAdviceBundle {
        val nowMs = clock()
        val uid = currentUidProvider()?.trim()?.takeIf { it.isNotEmpty() }
        val scopeTag = uid ?: GUEST_SCOPE
        val monthKey = currentMonthKeyUtc(nowMs)
        val isVip = isVipProvider()

        if (uid != null && isOnlineProvider()) {
            try {
                val payload = buildCloudRequestPayload(request)
                val response = cloudFunctions.call(CALLABLE_NAME, payload)
                return parseCloudResponse(
                    response = response,
                    request = request,
                    scopeTag = scopeTag,
                    monthKey = monthKey,
                    isVip = isVip,
                    nowMs = nowMs,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (cfe: CloudFunctionsException) {
                if (cfe.code == CloudFunctionsCode.PERMISSION_DENIED && cfe.reason == REASON_VIP_REQUIRED) {
                    persistMonthlyUsedCount(scopeTag, monthKey, ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH)
                    val fallback = localAdviceWithPrivateHints(request, nowMs)
                    return fallback.copy(
                        usedFreeAllowance = true,
                        remainingFreeAllowance = 0,
                        generatedAtEpochMs = nowMs,
                    )
                }
                // Network / transient / auth hiccup -> fall back without consuming free starter quota.
                val fallback = localAdviceWithPrivateHints(request, nowMs)
                val remaining = if (isVip) null else (ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH - readUsedCount(scopeTag, monthKey)).coerceAtLeast(0)
                return fallback.copy(
                    usedFreeAllowance = false,
                    remainingFreeAllowance = remaining,
                    generatedAtEpochMs = nowMs,
                )
            } catch (_: Throwable) {
                val fallback = localAdviceWithPrivateHints(request, nowMs)
                val remaining = if (isVip) null else (ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH - readUsedCount(scopeTag, monthKey)).coerceAtLeast(0)
                return fallback.copy(
                    usedFreeAllowance = false,
                    remainingFreeAllowance = remaining,
                    generatedAtEpochMs = nowMs,
                )
            }
        }

        val fallback = localAdviceWithPrivateHints(request, nowMs)
        if (isVip) {
            return fallback.copy(
                usedFreeAllowance = false,
                remainingFreeAllowance = null,
                generatedAtEpochMs = nowMs,
            )
        }
        val currentUsed = readUsedCount(scopeTag, monthKey)
        val nextUsed = (currentUsed + 1).coerceAtMost(ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH)
        if (nextUsed != currentUsed) {
            persistMonthlyUsedCount(scopeTag, monthKey, nextUsed)
        }
        val remaining = (ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH - nextUsed).coerceAtLeast(0)
        return fallback.copy(
            usedFreeAllowance = currentUsed < ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH,
            remainingFreeAllowance = remaining,
            generatedAtEpochMs = nowMs,
        )
    }

    private suspend fun localAdviceWithPrivateHints(request: AiGiftRequest, nowMs: Long): AiGiftAdviceBundle =
        if (localFallback === SmartRuleGiftAdvisor) {
            SmartRuleGiftAdvisor.generate(request, nowEpochMs = nowMs)
        } else {
            localFallback.advise(request)
        }

    private fun buildCloudRequestPayload(request: AiGiftRequest): Map<String, Any?> {
        val dossier = request.dossier
        val excludedPast = (dossier.giftHistory.map { it.title } + request.excludedGiftTitles)
            .map { AiPromptBuilder.sanitizeFreeText(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(15)

        return mapOf(
            "language" to if (request.isEnglish) "en" else "vi",
            "occasion" to AiPromptBuilder.sanitizeFreeText(request.occasionTitle).ifBlank {
                if (request.isEnglish) "Love Anniversary" else "Kỷ niệm tình yêu"
            },
            "occasionId" to request.occasionId,
            "daysUntil" to (request.daysUntilOccasion ?: 7).coerceIn(0, 3650),
            "loveDays" to request.loveDays.coerceAtLeast(0),
            "budgetMaxVnd" to dossier.budgetMaxVnd.coerceAtLeast(0L),
            "region" to dossier.occasionRegion.trim().uppercase().ifBlank { "VN" },
            "tone" to request.tone.name,
            "loveLanguages" to dossier.loveLanguages.map { it.name },
            "interests" to dossier.interestCategories.toList(),
            "freeformLikes" to AiPromptBuilder.sanitizeFreeText(dossier.freeformLikes),
            "allergies" to dossier.foodAndCare.allergies.map { AiPromptBuilder.sanitizeFreeText(it) }.filter { it.isNotBlank() },
            "dislikes" to dossier.foodAndCare.dislikesOrTaboos.map { AiPromptBuilder.sanitizeFreeText(it) }.filter { it.isNotBlank() },
            "favoriteColors" to dossier.sizes.favoriteColors.map { AiPromptBuilder.sanitizeFreeText(it) }.filter { it.isNotBlank() },
            "favoriteScents" to dossier.sizes.favoriteScents.map { AiPromptBuilder.sanitizeFreeText(it) }.filter { it.isNotBlank() },
            "favoriteFoods" to dossier.foodAndCare.favoriteFoods.map { AiPromptBuilder.sanitizeFreeText(it) }.filter { it.isNotBlank() },
            "goToDrinks" to AiPromptBuilder.sanitizeFreeText(dossier.foodAndCare.goToDrinks),
            "pastGifts" to excludedPast,
        )
    }

    private suspend fun parseCloudResponse(
        response: Map<String, Any?>,
        request: AiGiftRequest,
        scopeTag: String,
        monthKey: String,
        isVip: Boolean,
        nowMs: Long,
    ): AiGiftAdviceBundle {
        val rawSource = response["source"] as? String
        val source = if (rawSource == "server_smart_engine") AdviceSource.SMART_LOCAL_ENGINE else AdviceSource.CLOUD_AI

        val quotaMap = response["quota"] as? Map<*, *>
        val serverTier = (quotaMap?.get("tier") as? String)?.lowercase()
        val serverUsed = (quotaMap?.get("used") as? Number)?.toInt()
        val serverRemaining = (quotaMap?.get("remaining") as? Number)?.toInt()
        if (serverUsed != null) {
            persistMonthlyUsedCount(scopeTag, monthKey, serverUsed.coerceAtLeast(0))
        }

        val cloudRecs = parseRecommendations(response["recommendations"], request.isEnglish)
        if (cloudRecs.isEmpty()) {
            return localAdviceWithPrivateHints(request, nowMs).copy(
                usedFreeAllowance = serverTier != "vip" && !isVip,
                remainingFreeAllowance = if (isVip || serverTier == "vip") null else serverRemaining,
                generatedAtEpochMs = nowMs,
            )
        }

        // Keep private Secret Surprise Notes strictly on-device and merge them locally ahead of cloud items.
        val localBundle = SmartRuleGiftAdvisor.generate(request, nowEpochMs = nowMs)
        val privateSecretRecs = localBundle.recommendations.filter { it.id.startsWith("secret_") }
        val mergedRecs = (privateSecretRecs + cloudRecs)
            .distinctBy { PartnerInterestCatalog.stripDiacritics(it.title.lowercase()) }
            .take(request.maxRecommendations.coerceIn(1, 10))

        val loveNotes = parseLoveNotes(response["loveNotes"], request)
            .ifEmpty { localBundle.loveNotes }
        val tabooWarnings = parseStringList(response["tabooWarnings"])
            .ifEmpty { localBundle.tabooWarnings }
        val prepTimeline = parseStringList(response["prepTimeline"])
            .ifEmpty { localBundle.prepTimeline }

        val usedFree = !isVip && serverTier != "vip"
        val remainingFree = if (isVip || serverTier == "vip") null else serverRemaining ?: remainingFreeAllowance()

        val occTitle = (response["occasion"] as? String)?.takeIf { it.isNotBlank() }
            ?: request.occasionTitle
        val headline = if (request.isEnglish) {
            "${mergedRecs.size} AI Concierge picks for $occTitle"
        } else {
            "${mergedRecs.size} gợi ý Trợ lý AI cho $occTitle"
        }

        return AiGiftAdviceBundle(
            source = source,
            occasionId = request.occasionId,
            occasionTitle = occTitle,
            summaryHeadline = headline,
            recommendations = mergedRecs,
            loveNotes = loveNotes,
            tabooWarnings = tabooWarnings,
            prepTimeline = prepTimeline,
            usedFreeAllowance = usedFree,
            remainingFreeAllowance = remainingFree,
            generatedAtEpochMs = nowMs,
        )
    }

    private fun parseRecommendations(raw: Any?, isEnglish: Boolean): List<RichGiftRecommendation> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapIndexedNotNull { idx, entry ->
            val m = entry as? Map<*, *> ?: return@mapIndexedNotNull null
            val title = (m["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            val cost = (m["estimatedCostVnd"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            val steps = parseStringList(m["preparationSteps"])
            val plan = (m["preparationPlan"] as? String)?.takeIf { it.isNotBlank() }
                ?: steps.joinToString(" • ").ifBlank {
                    if (isEnglish) "Prepare ahead and include a personal card." else "Chuẩn bị trước và kèm thiệp viết tay."
                }
            RichGiftRecommendation(
                id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: "cloud_${idx + 1}",
                title = title,
                category = (m["category"] as? String)?.takeIf { it.isNotBlank() }
                    ?: if (isEnglish) "Curated" else "Gợi ý",
                priceRangeLabel = (m["priceLabel"] as? String)?.takeIf { it.isNotBlank() }
                    ?: formatCostFallback(cost, isEnglish),
                estimatedMinVnd = cost,
                estimatedMaxVnd = cost,
                matchScore = 95 - idx,
                reason = (m["reason"] as? String)?.trim().orEmpty(),
                preparationPlan = plan,
                matchedInterests = parseStringList(m["matchedInterests"]),
                leadTimeDays = (m["preparationLeadDays"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 1,
            )
        }
    }

    private fun parseLoveNotes(raw: Any?, request: AiGiftRequest): List<LoveNoteDraft> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapIndexedNotNull { idx, entry ->
            val m = entry as? Map<*, *> ?: return@mapIndexedNotNull null
            val body = ((m["body"] ?: m["message"]) as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapIndexedNotNull null
            val toneName = (m["tone"] as? String)?.trim()?.uppercase().orEmpty()
            val tone = RelationshipTone.entries.firstOrNull { it.name == toneName } ?: request.tone
            LoveNoteDraft(
                id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: "cloud_note_${idx + 1}",
                tone = tone,
                title = (m["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: if (request.isEnglish) tone.titleEn else tone.titleVi,
                message = body,
                occasionLabel = request.occasionTitle,
            )
        }
    }

    private fun parseStringList(raw: Any?): List<String> =
        (raw as? List<*>)?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) } ?: emptyList()

    private fun formatCostFallback(costVnd: Long, isEnglish: Boolean): String = when {
        costVnd <= 0L -> if (isEnglish) "Heartfelt" else "Ý nghĩa"
        costVnd < 200_000L -> if (isEnglish) "Under 200k VND" else "Dưới 200k"
        costVnd <= 500_000L -> "200k - 500k VND"
        costVnd <= 1_000_000L -> "500k - 1M VND"
        else -> if (isEnglish) "Over 1M VND" else "Trên 1 triệu"
    }

    private fun currentScopeTag(): String =
        currentUidProvider()?.trim()?.takeIf { it.isNotEmpty() } ?: GUEST_SCOPE

    private fun readUsedCount(scopeTag: String, monthKey: String): Int =
        prefs.getInt(quotaKey(scopeTag, monthKey), 0).coerceAtLeast(0)

    private fun persistMonthlyUsedCount(scopeTag: String, monthKey: String, count: Int) {
        val sanitized = count.coerceAtLeast(0)
        prefs.edit().putInt(quotaKey(scopeTag, monthKey), sanitized).apply()
        if (scopeTag == currentScopeTag() && monthKey == currentMonthKeyUtc(clock())) {
            _freeUsageThisMonth.value = sanitized
        }
    }

    private fun quotaKey(scopeTag: String, monthKey: String): String =
        "ai_advisor_usage_${scopeTag.replace(':', '_')}_$monthKey"

    private fun currentMonthKeyUtc(nowMs: Long): String {
        val zdt = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC)
        return "%04d%02d".format(zdt.year, zdt.monthValue)
    }

    companion object {
        const val CALLABLE_NAME = "generateGiftAdvice"
        private const val GUEST_SCOPE = "guest"
        private const val REASON_VIP_REQUIRED = "vip-required"
    }
}
