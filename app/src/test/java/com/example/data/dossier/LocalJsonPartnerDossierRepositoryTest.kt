package com.example.data.dossier

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.CoupleProfileEntity
import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.LoveLanguage
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerFoodAndCare
import com.example.shared.dossier.PartnerSizes
import com.example.shared.dossier.SecretHintNote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalJsonPartnerDossierRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dossierDir: File
    private var activeScope = "uid_alpha"
    private var nowMs = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dossierDir = tempFolder.newFolder("dossier")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newRepo(): LocalJsonPartnerDossierRepository =
        LocalJsonPartnerDossierRepository(
            dossierDir = dossierDir,
            dao = db.inLoveDao(),
            activeScopeProvider = { activeScope },
            clock = { nowMs },
        )

    @Test
    fun saveAndLoadRoundTripWithAtomicFileAndRoomSync() = runBlocking {
        val dao = db.inLoveDao()
        dao.insertCoupleProfile(
            CoupleProfileEntity(
                partner1Name = "Minh",
                partner1Birthday = "01/01/2000",
                partner1ProfilePicture = "",
                partner2Name = "Linh",
                partner2Birthday = "02/02/2001",
                partner2ProfilePicture = "",
            ),
        )

        val repo = newRepo()
        val dossier = PartnerDossier(
            scopeId = "uid_alpha",
            nickname = "Bông",
            loveLanguages = setOf(LoveLanguage.QUALITY_TIME, LoveLanguage.WORDS_OF_AFFIRMATION),
            interestCategories = setOf("coffee", "flowers_scents"),
            freeformLikes = "matcha latte, nến thơm gỗ thông",
            sizes = PartnerSizes(shirtSize = "M", ringSize = "11", favoriteColors = listOf("Pastel Pink")),
            foodAndCare = PartnerFoodAndCare(
                favoriteFoods = listOf("Bún chả"),
                goToDrinks = "Cold brew cam sả",
                allergies = listOf("đậu phộng"),
                dislikesOrTaboos = listOf("màu neon"),
            ),
            budgetMaxVnd = 600_000L,
            occasionRegion = "VN",
            updatedAtEpochMs = nowMs,
        )

        repo.save(dossier)
        assertEquals(dossier, repo.dossier.value)
        assertTrue("No lingering .tmp file", dossierDir.listFiles()?.none { it.name.endsWith(".tmp") } == true)

        // Verify Room CoupleProfileEntity preference columns stayed in sync
        val roomProfile = dao.getCoupleProfile().first()!!
        assertEquals(600_000L, roomProfile.budgetMaxVnd)
        assertEquals("VN", roomProfile.occasionRegion)
        assertTrue(roomProfile.likesCsv.contains("coffee"))
        assertTrue(roomProfile.likesCsv.contains("matcha latte"))

        // Re-instantiate repository and verify persistence from disk
        val reloaded = newRepo().load("uid_alpha")
        assertEquals(dossier, reloaded)
    }

    @Test
    fun secretNotesAndGiftHistoryCrudAndPerScopeIsolation() = runBlocking {
        val repo = newRepo()
        repo.load("uid_alpha")
        repo.addSecretNote(
            SecretHintNote(
                id = "note_1",
                note = "Thích túi tote vải canvas màu kem",
                occasionHint = "20/10",
                estimatedPriceVnd = 350_000L,
            ),
        )
        repo.markSecretNotePurchased("note_1", purchased = true)
        repo.addGivenGift(
            GivenGiftRecord(
                id = "gift_1",
                title = "Bó hoa tulip hồng",
                occasion = "8/3",
                givenDateIso = "2026-03-08",
                priceVnd = 450_000L,
            ),
        )

        val alpha = repo.load("uid_alpha")
        assertEquals(1, alpha.secretSurpriseNotes.size)
        assertTrue(alpha.secretSurpriseNotes.single().isPurchased)
        assertEquals(1, alpha.giftHistory.size)

        // Switch scope to uid_beta -> clean slate without touching uid_alpha
        activeScope = "uid_beta"
        val beta = repo.loadCurrentScope()
        assertEquals("uid_beta", beta.scopeId)
        assertTrue(beta.secretSurpriseNotes.isEmpty())

        // Remove note from uid_alpha
        activeScope = "uid_alpha"
        repo.loadCurrentScope()
        val afterRemove = repo.removeSecretNote("note_1")
        assertTrue(afterRemove.secretSurpriseNotes.isEmpty())

        // Clear uid_alpha leaves uid_beta file intact if saved
        repo.save(beta.copy(nickname = "Bee"))
        repo.clearScope("uid_alpha")
        assertTrue(repo.load("uid_alpha").nickname.isEmpty())
        assertEquals("Bee", repo.load("uid_beta").nickname)
    }

    @Test
    fun seedsFromLegacyRoomCoupleProfileWhenJsonMissing() = runBlocking {
        val dao = db.inLoveDao()
        dao.insertCoupleProfile(
            CoupleProfileEntity(
                partner1Name = "An",
                partner1Birthday = "01/01/2000",
                partner1ProfilePicture = "",
                partner2Name = "Trang",
                partner2Birthday = "05/05/2001",
                partner2ProfilePicture = "",
                likesCsv = "cà phê, du lịch",
                budgetMaxVnd = 800_000L,
                occasionRegion = "INTL",
            ),
        )

        val loaded = newRepo().load("guest")
        assertEquals("Trang", loaded.nickname)
        assertEquals(setOf("coffee", "travel"), loaded.interestCategories)
        assertEquals(800_000L, loaded.budgetMaxVnd)
        assertEquals("INTL", loaded.occasionRegion)
    }

    @Test
    fun corruptedJsonFallsBackSafelyWithoutCrashing() = runBlocking {
        val repo = newRepo()
        repo.save(PartnerDossier(scopeId = "uid_alpha", nickname = "Valid"))
        val file = dossierDir.listFiles()?.single { it.name.endsWith(".json") }!!
        file.writeText("{corrupt json !!!")

        val loaded = repo.load("uid_alpha")
        assertEquals("uid_alpha", loaded.scopeId)
        assertFalse(loaded.nickname == "Valid")
    }
}
