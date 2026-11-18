package com.example.shared.dossier

import com.example.shared.dossier.fakes.FakePartnerDossierStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartnerDossierTest {

    @Test
    fun standardizedInterestKeys_containAll14Categories_andNormalizeAliases() {
        val expected = listOf(
            "coffee", "travel", "technology", "fashion", "skincare",
            "books", "music", "cinema", "cooking", "gaming",
            "fitness", "flowers_scents", "handmade", "pets",
        )
        assertEquals(expected, PartnerInterestCatalog.STANDARD_INTEREST_KEYS)
        assertEquals("coffee", PartnerInterestCatalog.normalizeInterestKey("Cà Phê"))
        assertEquals("flowers_scents", PartnerInterestCatalog.normalizeInterestKey("Nước hoa"))
        assertEquals("travel", PartnerInterestCatalog.normalizeInterestKey("Du lịch"))
        assertEquals(
            setOf("coffee", "books", "pets"),
            PartnerInterestCatalog.normalizeInterestsCsv("Cà phê, books, thú cưng"),
        )
    }

    @Test
    fun completenessPercent_andMissingProfileHints_alignCleanly() {
        val empty = PartnerDossier(scopeId = "u1")
        assertEquals(0, empty.completenessPercent())
        assertEquals(10, empty.missingProfileHints(isEnglish = false).size)
        assertEquals(10, empty.missingProfileHints(isEnglish = true).size)

        val full = PartnerDossier(
            scopeId = "u1",
            nickname = "Bé Mỡ",
            loveLanguages = setOf(LoveLanguage.QUALITY_TIME, LoveLanguage.WORDS_OF_AFFIRMATION),
            interestCategories = setOf("coffee", "books"),
            freeformLikes = "Matcha latte ít đường, hoa tulip trắng hồng",
            sizes = PartnerSizes(
                shirtSize = "M",
                shoeSize = "37",
                ringSize = "11",
                favoriteColors = listOf("Hồng pastel", "Kem"),
                favoriteScents = listOf("Hoa nhài nhẹ"),
            ),
            foodAndCare = PartnerFoodAndCare(
                favoriteFoods = listOf("Bún đậu", "Dimsum"),
                goToDrinks = "Matcha đá xay",
                allergies = listOf("Đậu phộng"),
                dislikesOrTaboos = listOf("Mùi nồng"),
            ),
            secretSurpriseNotes = listOf(
                SecretHintNote(id = "n1", note = "Thích túi tote vải canvas", estimatedPriceVnd = 350_000L),
            ),
            budgetMaxVnd = 1_000_000L,
        )
        assertEquals(100, full.completenessPercent())
        assertTrue(full.missingProfileHints(isEnglish = false).isEmpty())
        assertTrue(full.missingProfileHints(isEnglish = true).isEmpty())
    }

    @Test
    fun jsonSerialization_roundTripsAndHandlesMalformedGracefully() {
        val original = PartnerDossier(
            scopeId = "uid_42",
            nickname = "Minh Anh",
            loveLanguages = setOf(LoveLanguage.RECEIVING_GIFTS),
            interestCategories = setOf("coffee", "skincare"),
            sizes = PartnerSizes(shirtSize = "S", favoriteColors = listOf("Sage")),
            foodAndCare = PartnerFoodAndCare(allergies = listOf("Caffeine")),
            secretSurpriseNotes = listOf(SecretHintNote("s1", "Son dưỡng có màu", "8/3", 280_000L, "Hasaki", false, 100L)),
            giftHistory = listOf(GivenGiftRecord("g1", "Bó hoa tulip", "Valentine", "2026-02-14", 450_000L, 5, "Rất thích")),
            budgetMaxVnd = 800_000L,
            occasionRegion = "VN",
            updatedAtEpochMs = 1770000000000L,
        )
        val encoded = original.toJsonString()
        val decoded = encoded.toPartnerDossierOrNull()
        assertNotNull(decoded)
        assertEquals(original, decoded)
        assertNull("{bad json".toPartnerDossierOrNull())
        assertNull("   ".toPartnerDossierOrNull())
        assertEquals("fallback", PartnerDossierJson.decodeOrDefault("broken", "fallback").scopeId)
    }

    @Test
    fun fakePartnerDossierStore_handlesScopesNotesAndGiftHistory() = runTest {
        val store = FakePartnerDossierStore(PartnerDossier(scopeId = "guest", nickname = "Guest"))
        val loaded = store.load("user_a")
        assertEquals("user_a", loaded.scopeId)

        store.save(loaded.copy(nickname = "Thảo"))
        store.addSecretNote(SecretHintNote(id = "note1", note = "Nến thơm gỗ tuyết tùng", estimatedPriceVnd = 320_000L))
        assertEquals(1, store.dossier.value.secretSurpriseNotes.size)

        store.markSecretNotePurchased("note1", true)
        assertTrue(store.dossier.value.secretSurpriseNotes.single().isPurchased)

        store.addGivenGift(GivenGiftRecord(id = "gift1", title = "Vòng tay bạc", occasion = "20/10"))
        assertEquals(1, store.dossier.value.giftHistory.size)

        store.removeSecretNote("note1")
        assertFalse(store.dossier.value.secretSurpriseNotes.any { it.id == "note1" })

        store.clearScope("user_a")
        assertEquals("", store.dossier.value.nickname)
    }
}
