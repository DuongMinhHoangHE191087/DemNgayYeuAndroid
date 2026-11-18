package com.example.shared.ai

import com.example.shared.ai.fakes.FakeAiGiftAdvisorGateway
import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.LoveLanguage
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerFoodAndCare
import com.example.shared.dossier.PartnerSizes
import com.example.shared.dossier.SecretHintNote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SmartRuleGiftAdvisorTest {

    @Test
    fun catalog_hasAtLeast25RichArchetypes() {
        assertTrue(GiftArchetypeCatalog.all.size >= 25, "Expected >= 25 archetypes, found ${GiftArchetypeCatalog.all.size}")
    }

    @Test
    fun advisor_excludesAllergiesDislikes_andDeduplicatesPastGiftHistory() = runTest {
        val dossier = PartnerDossier(
            scopeId = "u1",
            nickname = "Linh",
            loveLanguages = setOf(LoveLanguage.QUALITY_TIME, LoveLanguage.RECEIVING_GIFTS),
            interestCategories = setOf("coffee", "flowers_scents", "fashion"),
            foodAndCare = PartnerFoodAndCare(
                allergies = listOf("caffeine", "pollen"),
                dislikesOrTaboos = listOf("đồng hồ"),
            ),
            giftHistory = listOf(
                GivenGiftRecord(
                    id = "h1",
                    title = "Nến Thơm Tinh Dầu Thiên Nhiên & Diêm Gỗ",
                    occasion = "Valentine",
                    givenDateIso = "2026-02-14",
                ),
            ),
            budgetMaxVnd = 2_000_000L,
        )

        val bundle = SmartRuleGiftAdvisor.advise(
            AiGiftRequest(
                dossier = dossier,
                occasionId = "couple_anniversary",
                occasionTitle = "Kỷ niệm 1 năm",
                daysUntilOccasion = 10,
                loveDays = 365,
                tone = RelationshipTone.DEEPLY_ROMANTIC,
                isEnglish = false,
                maxRecommendations = 8,
            ),
        )

        assertEquals(AdviceSource.SMART_LOCAL_ENGINE, bundle.source)
        val titles = bundle.recommendations.map { it.title.lowercase() }
        assertFalse(titles.any { it.contains("cà phê") || it.contains("coffee") }, "Coffee must be excluded by caffeine allergy")
        assertFalse(titles.any { it.contains("hoa tươi") || it.contains("bouquet") }, "Flowers must be excluded by pollen allergy")
        assertFalse(titles.any { it.contains("đồng hồ") || it.contains("watch") }, "Watch must be excluded by taboo")
        assertFalse(titles.any { it.contains("nến thơm tinh dầu thiên nhiên") }, "Past gift must be deduplicated")
        assertTrue(bundle.tabooWarnings.any { it.contains("caffeine") })
    }

    @Test
    fun advisor_respectsBudgetUrgency_andInjectsSizesAndSecretNotes() = runTest {
        val dossier = PartnerDossier(
            scopeId = "u2",
            nickname = "Mai",
            loveLanguages = setOf(LoveLanguage.QUALITY_TIME, LoveLanguage.WORDS_OF_AFFIRMATION),
            interestCategories = setOf("cinema", "music", "fashion"),
            sizes = PartnerSizes(shirtSize = "S", ringSize = "10", favoriteColors = listOf("Pastel Rose")),
            secretSurpriseNotes = listOf(
                SecretHintNote(id = "s1", note = "Khăn lụa cổ pastel", estimatedPriceVnd = 280_000L, isPurchased = false),
            ),
            budgetMaxVnd = 400_000L,
        )

        val bundle = SmartRuleGiftAdvisor.advise(
            AiGiftRequest(
                dossier = dossier,
                occasionId = "that_tich",
                occasionTitle = "Thất Tịch",
                daysUntilOccasion = 1,
                loveDays = 520,
                tone = RelationshipTone.SWEET,
                isEnglish = true,
                maxRecommendations = 5,
            ),
        )

        assertTrue(bundle.recommendations.isNotEmpty())
        assertTrue(bundle.recommendations.all { it.estimatedMinVnd <= 400_000L })
        assertTrue(bundle.recommendations.all { it.leadTimeDays <= 2 }, "Urgent 1-day occasion must only pick <=2d lead time")
        assertEquals("secret_s1", bundle.recommendations.first().id)
        assertEquals(3, bundle.loveNotes.size)
        assertTrue(bundle.loveNotes.first().message.contains("Mai"))
        assertTrue(bundle.loveNotes.first().message.contains("520"))
        assertFalse(bundle.summaryHeadline.contains('!'))
        assertTrue(bundle.recommendations.all { !it.reason.contains('!') && !it.preparationPlan.contains('!') })
        assertTrue(bundle.prepTimeline.all { !it.contains('!') })
    }

    @Test
    fun aiPromptBuilder_stripsPiiAndSecretNotes_whilePreservingSchema() {
        val dossier = PartnerDossier(
            scopeId = "uid_secret",
            nickname = "Nguyễn Minh Thư",
            loveLanguages = setOf(LoveLanguage.ACTS_OF_SERVICE),
            interestCategories = setOf("cooking", "travel"),
            freeformLikes = "Email minh@example.com phone 0912345678 https://shop.example.com/item thích trà đào",
            foodAndCare = PartnerFoodAndCare(allergies = listOf("Peanuts")),
            secretSurpriseNotes = listOf(
                SecretHintNote(id = "sec1", note = "Secret private item at https://private.shop", targetUrlOrShop = "https://private.shop"),
            ),
            budgetMaxVnd = 600_000L,
        )
        val payload = AiPromptBuilder.build(
            AiGiftRequest(
                dossier = dossier,
                occasionId = "partner_birthday",
                occasionTitle = "Birthday",
                daysUntilOccasion = 14,
                loveDays = 200,
                tone = RelationshipTone.THOUGHTFUL,
                isEnglish = true,
            ),
        )
        assertFalse(payload.userPrompt.contains("Nguyễn Minh Thư"))
        assertFalse(payload.userPrompt.contains("minh@example.com"))
        assertFalse(payload.userPrompt.contains("0912345678"))
        assertFalse(payload.userPrompt.contains("https://"))
        assertFalse(payload.userPrompt.contains("Secret private item"))
        assertTrue(payload.userPrompt.contains("Peanuts"))
        assertTrue(payload.responseJsonSchema.contains("recommendations"))
        assertTrue(payload.responseJsonSchema.contains("loveNotes"))
    }

    @Test
    fun fakeAiGateway_recordsRequests_andRoundTripsBundleJson() = runTest {
        val fake = FakeAiGiftAdvisorGateway()
        val req = AiGiftRequest(
            dossier = PartnerDossier(scopeId = "u1", interestCategories = setOf("books")),
            occasionId = "couple_anniversary",
            occasionTitle = "Anniversary",
        )
        val result = fake.advise(req)
        assertEquals(1, fake.recordedRequests.size)
        val encoded = AiGiftAdviceJson.encodeBundle(result)
        val decoded = AiGiftAdviceJson.decodeBundleOrNull(encoded)
        assertNotNull(decoded)
        assertEquals(result.summaryHeadline, decoded.summaryHeadline)

        fake.failureToThrow = IllegalStateException("offline")
        assertFailsWith<IllegalStateException> { fake.advise(req) }
    }
}
