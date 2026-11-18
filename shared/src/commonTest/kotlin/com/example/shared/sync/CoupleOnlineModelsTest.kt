package com.example.shared.sync

import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.LoveLanguage
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerFoodAndCare
import com.example.shared.dossier.PartnerSizes
import com.example.shared.dossier.SecretHintNote
import com.example.shared.sync.fakes.FakeCoupleOnlineSyncGateway
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoupleOnlineModelsTest {

    @Test
    fun onlineSyncPhase_labelsAreCleanAndNonJargon() {
        assertEquals(7, OnlineSyncPhase.entries.size)
        val forbiddenTerms = listOf("firestore", "sqlite", "room db", "sharedpreferences", "database", "cơ sở dữ liệu")
        for (phase in OnlineSyncPhase.entries) {
            assertTrue(phase.statusLabelVi.isNotBlank())
            assertTrue(phase.statusLabelEn.isNotBlank())
            assertFalse(phase.statusLabelVi.contains('!'))
            assertFalse(phase.statusLabelEn.contains('!'))
            for (term in forbiddenTerms) {
                assertFalse(phase.statusLabelVi.lowercase().contains(term), "${phase.name} VI leaked $term")
                assertFalse(phase.statusLabelEn.lowercase().contains(term), "${phase.name} EN leaked $term")
            }
        }
    }

    @Test
    fun onlineSyncPhaseResolver_mapsAllStatesCorrectly() {
        assertEquals(
            OnlineSyncPhase.GUEST_LOCAL,
            OnlineSyncPhaseResolver.resolve(false, true, true, true, true, true),
        )
        assertEquals(
            OnlineSyncPhase.BREAKUP_PENDING,
            OnlineSyncPhaseResolver.resolve(true, true, true, false, false, true),
        )
        assertEquals(
            OnlineSyncPhase.COUPLED_SYNCED,
            OnlineSyncPhaseResolver.resolve(true, true, false, false, false, true),
        )
        assertEquals(
            OnlineSyncPhase.COUPLED_OFFLINE_QUEUE,
            OnlineSyncPhaseResolver.resolve(true, true, false, false, false, false),
        )
        assertEquals(
            OnlineSyncPhase.INVITE_INCOMING,
            OnlineSyncPhaseResolver.resolve(true, false, false, true, true, true),
        )
        assertEquals(
            OnlineSyncPhase.INVITE_OUTGOING,
            OnlineSyncPhaseResolver.resolve(true, false, false, true, false, true),
        )
        assertEquals(
            OnlineSyncPhase.SIGNED_IN_SOLO,
            OnlineSyncPhaseResolver.resolve(true, false, false, false, false, true),
        )
    }

    @Test
    fun coupleCodeParser_extractsAndNormalizesUrlsAndRawCodes() {
        assertEquals("LOVE-9966", CoupleCodeParser.extractCode("https://inlove.app/pair/love-9966"))
        assertEquals("LOVE-9966", CoupleCodeParser.extractCode("https://inlove.app/pair/love-9966/"))
        assertEquals("AB_12", CoupleCodeParser.extractCode("inlove://pair?code=ab_12"))
        assertEquals("LOVE77", CoupleCodeParser.extractCode("inlove://pair/love77"))
        assertEquals("XY-88", CoupleCodeParser.extractCode("?code=xy-88&source=share"))
        assertEquals("HELLO_1", CoupleCodeParser.extractCode("code=hello_1"))
        assertEquals("PAIR-2026", CoupleCodeParser.extractCode("  pair-2026  "))
        assertEquals("", CoupleCodeParser.extractCode("   "))
        assertEquals("", CoupleCodeParser.extractCode("https://example.com/about"))
    }

    @Test
    fun dossierMergePolicy_picksNewerScalars_andUnionMergesNotesAndGiftHistory() {
        val local = PartnerDossier(
            scopeId = "uid_1",
            nickname = "Nắng",
            loveLanguages = setOf(LoveLanguage.WORDS_OF_AFFIRMATION),
            interestCategories = setOf("coffee"),
            sizes = PartnerSizes(shirtSize = "M"),
            foodAndCare = PartnerFoodAndCare(goToDrinks = "Matcha đá xay"),
            secretSurpriseNotes = listOf(
                SecretHintNote(id = "shared_n1", note = "Áo len cũ", isPurchased = false, createdAtEpochMs = 100L),
                SecretHintNote(id = "offline_n2", note = "Gấu bông mini", isPurchased = false, createdAtEpochMs = 300L),
            ),
            giftHistory = listOf(
                GivenGiftRecord(id = "offline_g2", title = "Hoa cẩm tú cầu", givenDateIso = "2026-03-08"),
            ),
            budgetMaxVnd = 500_000L,
            updatedAtEpochMs = 1_000L,
        )
        val remote = PartnerDossier(
            scopeId = "uid_1",
            nickname = "Em Bé",
            loveLanguages = setOf(LoveLanguage.QUALITY_TIME, LoveLanguage.RECEIVING_GIFTS),
            interestCategories = setOf("coffee", "books"),
            sizes = PartnerSizes(shirtSize = "S", ringSize = "12"),
            foodAndCare = PartnerFoodAndCare(goToDrinks = "Trà sữa ít đường"),
            secretSurpriseNotes = listOf(
                SecretHintNote(id = "shared_n1", note = "Áo len kem mới", isPurchased = true, createdAtEpochMs = 100L),
                SecretHintNote(id = "remote_n3", note = "Đèn ngủ mặt trăng", isPurchased = false, createdAtEpochMs = 400L),
            ),
            giftHistory = listOf(
                GivenGiftRecord(id = "remote_g1", title = "Khăn lụa pastel", givenDateIso = "2026-02-14"),
            ),
            budgetMaxVnd = 900_000L,
            updatedAtEpochMs = 2_000L,
        )

        val mergedRemoteNewer = DossierMergePolicy.merge(local = local, remote = remote)
        assertEquals("Em Bé", mergedRemoteNewer.nickname)
        assertEquals(900_000L, mergedRemoteNewer.budgetMaxVnd)
        assertEquals("S", mergedRemoteNewer.sizes.shirtSize)
        assertEquals(2_000L, mergedRemoteNewer.updatedAtEpochMs)
        assertEquals(
            setOf("shared_n1", "offline_n2", "remote_n3"),
            mergedRemoteNewer.secretSurpriseNotes.map { it.id }.toSet(),
        )
        assertTrue(mergedRemoteNewer.secretSurpriseNotes.first { it.id == "shared_n1" }.isPurchased)
        assertEquals(
            setOf("offline_g2", "remote_g1"),
            mergedRemoteNewer.giftHistory.map { it.id }.toSet(),
        )

        val localNewer = local.copy(updatedAtEpochMs = 3_000L)
        val mergedLocalNewer = DossierMergePolicy.merge(local = localNewer, remote = remote)
        assertEquals("Nắng", mergedLocalNewer.nickname)
        assertEquals(500_000L, mergedLocalNewer.budgetMaxVnd)
        assertEquals(3_000L, mergedLocalNewer.updatedAtEpochMs)
        assertFalse(mergedLocalNewer.secretSurpriseNotes.first { it.id == "shared_n1" }.isPurchased)
        assertEquals(3, mergedLocalNewer.secretSurpriseNotes.size)
        assertEquals(2, mergedLocalNewer.giftHistory.size)

        val emptyLocal = PartnerDossier(scopeId = "uid_1", updatedAtEpochMs = 0L)
        val zeroTsRemote = remote.copy(updatedAtEpochMs = 0L)
        assertEquals("Em Bé", DossierMergePolicy.merge(emptyLocal, zeroTsRemote).nickname)
    }

    @Test
    fun fakeCoupleOnlineSyncGateway_mergesAndTracksSnapshot() = runTest {
        val fake = FakeCoupleOnlineSyncGateway(
            snapshot = CoupleOnlineSyncSnapshot(phase = OnlineSyncPhase.COUPLED_SYNCED, selfUid = "u1"),
            remoteDossier = PartnerDossier(
                scopeId = "u1",
                nickname = "Mây",
                secretSurpriseNotes = listOf(SecretHintNote(id = "r1", note = "Sách thơ", createdAtEpochMs = 50L)),
                updatedAtEpochMs = 500L,
            ),
        )
        assertEquals(OnlineSyncPhase.COUPLED_SYNCED, fake.currentSyncSnapshot().phase)
        val merged = fake.syncPrivateDossier(
            PartnerDossier(
                scopeId = "u1",
                nickname = "Local",
                secretSurpriseNotes = listOf(SecretHintNote(id = "l1", note = "Hoa hồng", createdAtEpochMs = 80L)),
                updatedAtEpochMs = 200L,
            ),
        )
        assertEquals("Mây", merged.nickname)
        assertEquals(setOf("r1", "l1"), merged.secretSurpriseNotes.map { it.id }.toSet())
    }
}
