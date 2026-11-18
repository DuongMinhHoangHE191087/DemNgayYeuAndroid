package com.example.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.data.cloud.fakes.FakeCloudFunctionsClient
import com.example.shared.ai.AdviceSource
import com.example.shared.ai.AiGiftRequest
import com.example.shared.ai.RelationshipTone
import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.LoveLanguage
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerFoodAndCare
import com.example.shared.dossier.SecretHintNote
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HybridAiGiftAdvisorRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var cloudClient: FakeCloudFunctionsClient
    private var uid: String? = "uid_1"
    private var online = true
    private var vip = false
    private var nowMs = ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("ai_advisor_test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        cloudClient = FakeCloudFunctionsClient()
    }

    private fun repo() = HybridAiGiftAdvisorRepository(
        cloudFunctions = cloudClient,
        prefs = prefs,
        currentUidProvider = { uid },
        isOnlineProvider = { online },
        isVipProvider = { vip },
        clock = { nowMs },
    )

    private fun sampleRequest(isEnglish: Boolean = false) = AiGiftRequest(
        dossier = PartnerDossier(
            scopeId = "uid_1",
            nickname = "Secret Real Name",
            loveLanguages = setOf(LoveLanguage.QUALITY_TIME),
            interestCategories = setOf("coffee"),
            freeformLikes = "Email me at test@example.com or 0912345678, thích matcha",
            foodAndCare = PartnerFoodAndCare(
                allergies = listOf("đậu phộng"),
                dislikesOrTaboos = listOf("đồng hồ"),
            ),
            secretSurpriseNotes = listOf(
                SecretHintNote(
                    id = "sec_1",
                    note = "Ly giữ nhiệt màu hồng phấn",
                    targetUrlOrShop = "https://private-shop.example.com/secret",
                    estimatedPriceVnd = 350_000L,
                ),
            ),
            giftHistory = listOf(
                GivenGiftRecord(id = "g1", title = "Khăn choàng len"),
            ),
            budgetMaxVnd = 700_000L,
            occasionRegion = "VN",
        ),
        occasionId = "valentine",
        occasionTitle = "Valentine",
        daysUntilOccasion = 4,
        loveDays = 100,
        tone = RelationshipTone.SWEET,
        isEnglish = isEnglish,
    )

    @Test
    fun signedInOnlineCallsCloudFunctionStripsPiiAndMergesPrivateSecretNotesLocally() = runBlocking {
        cloudClient.enqueue(
            mapOf(
                "source" to "gemini_ai",
                "occasion" to "Valentine",
                "recommendations" to listOf(
                    mapOf(
                        "id" to "gem_1",
                        "title" to "Bộ pha cà phê pour-over gốm sứ",
                        "category" to "Trải Nghiệm",
                        "reason" to "Đúng gu cà phê sáng.",
                        "preparationSteps" to listOf("Đặt trước 2 ngày", "Viết thiệp tay"),
                        "estimatedCostVnd" to 450000,
                        "priceLabel" to "200k - 500k VND",
                        "preparationLeadDays" to 2,
                        "matchedInterests" to listOf("coffee"),
                    ),
                ),
                "loveNotes" to listOf(
                    mapOf("id" to "n1", "tone" to "SWEET", "title" to "Ngọt", "body" to "Thương em nhiều!"),
                ),
                "tabooWarnings" to listOf("Tránh đậu phộng"),
                "prepTimeline" to listOf("Đặt trước 2 ngày"),
                "quota" to mapOf("tier" to "free", "used" to 1, "limit" to 2, "remaining" to 1),
            ),
        )

        val r = repo()
        val bundle = r.advise(sampleRequest())

        assertEquals(AdviceSource.CLOUD_AI, bundle.source)
        assertTrue(bundle.usedFreeAllowance)
        assertEquals(1, bundle.remainingFreeAllowance)
        assertEquals(1, r.freeUsageThisMonth.value)
        assertEquals(1, cloudClient.calls.size)
        val sentData = cloudClient.calls.single().data!!
        val serializedPayload = sentData.toString()
        assertFalse("Real nickname must not leave device", serializedPayload.contains("Secret Real Name"))
        assertFalse("Email must be scrubbed", serializedPayload.contains("test@example.com"))
        assertFalse("Private shop URL must not leave device", serializedPayload.contains("private-shop.example.com"))

        // Local secret note is merged at the top on-device
        assertTrue(bundle.recommendations.any { it.id == "secret_sec_1" })
        assertTrue(bundle.recommendations.any { it.id == "gem_1" })
    }

    @Test
    fun cloudQuotaExhaustedFallsBackGracefullyWithZeroRemaining() = runBlocking {
        cloudClient.enqueueError(CloudFunctionsException(CloudFunctionsCode.PERMISSION_DENIED, "vip-required"))

        val r = repo()
        val bundle = r.advise(sampleRequest())

        assertEquals(AdviceSource.SMART_LOCAL_ENGINE, bundle.source)
        assertTrue(bundle.usedFreeAllowance)
        assertEquals(0, bundle.remainingFreeAllowance)
        assertEquals(2, r.readCurrentMonthUsedCount())
        assertTrue(bundle.recommendations.isNotEmpty())
        assertTrue(bundle.loveNotes.isNotEmpty())
    }

    @Test
    fun transientCloudFailureFallsBackWithoutBurningFreeQuota() = runBlocking {
        cloudClient.enqueueError(CloudFunctionsException(CloudFunctionsCode.UNAVAILABLE, null))

        val r = repo()
        val bundle = r.advise(sampleRequest())

        assertEquals(AdviceSource.SMART_LOCAL_ENGINE, bundle.source)
        assertFalse(bundle.usedFreeAllowance)
        assertEquals(2, bundle.remainingFreeAllowance)
        assertEquals(0, r.readCurrentMonthUsedCount())
    }

    @Test
    fun offlineAndGuestTrackMonthlyAllowanceAndResetNextMonth() = runBlocking {
        uid = null
        online = false

        val r = repo()
        val first = r.advise(sampleRequest(isEnglish = true))
        assertEquals(AdviceSource.SMART_LOCAL_ENGINE, first.source)
        assertTrue(first.usedFreeAllowance)
        assertEquals(1, first.remainingFreeAllowance)
        assertEquals(0, cloudClient.calls.size)

        val second = r.advise(sampleRequest(isEnglish = true))
        assertTrue(second.usedFreeAllowance)
        assertEquals(0, second.remainingFreeAllowance)

        // Third offline run still works smoothly (no crash) and signals remaining = 0
        val third = r.advise(sampleRequest(isEnglish = true))
        assertEquals(0, third.remainingFreeAllowance)
        assertTrue(third.recommendations.isNotEmpty())

        // Advance clock to next UTC month -> quota resets
        nowMs = Instant.parse("2026-12-01T00:00:00Z").toEpochMilli()
        val dec = r.advise(sampleRequest(isEnglish = true))
        assertTrue(dec.usedFreeAllowance)
        assertEquals(1, dec.remainingFreeAllowance)
    }

    @Test
    fun vipUserOfflineHasNoFreeQuotaDeduction() = runBlocking {
        vip = true
        online = false

        val r = repo()
        val bundle = r.advise(sampleRequest())

        assertEquals(AdviceSource.SMART_LOCAL_ENGINE, bundle.source)
        assertFalse(bundle.usedFreeAllowance)
        assertNull(bundle.remainingFreeAllowance)
        assertEquals(0, r.readCurrentMonthUsedCount())
    }
}
