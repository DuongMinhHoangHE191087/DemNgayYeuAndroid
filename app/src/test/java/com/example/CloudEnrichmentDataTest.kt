package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.InLoveRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudEnrichmentDataTest {

  private lateinit var db: AppDatabase
  private lateinit var repository: InLoveRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = InLoveRepository(db.inLoveDao(), context)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `initializeDefaultDataIfEmpty sets up neutral reminder cadences`() = runBlocking {
    repository.initializeDefaultDataIfEmpty()
    val cadences = db.inLoveDao().getAllReminderCadences().first()

    assertNotNull(cadences)
    assertTrue("Cadences should not be empty", cadences.isNotEmpty())
    val keys = cadences.map { it.key }
    assertTrue(keys.contains("7_days"))
    assertTrue(keys.contains("3_days"))
    assertTrue(keys.contains("1_day"))
    assertTrue(keys.contains("exact_day"))
  }

  @Test
  fun `anti-decompilation audit - no fake personal memories hardcoded in fresh database`() = runBlocking {
    repository.initializeDefaultDataIfEmpty()
    val memories = db.inLoveDao().getAllSharedMemories().first()

    // Verifies that no personal romantic diary entries or specific dates are hardcoded in the local DB
    assertEquals("Fresh installation must have zero fake personal diary entries", 0, memories.size)
  }

  @Test
  fun `presetPhotos and presetAvatars flow initialization`() = runBlocking {
    assertNotNull(repository.presetPhotos)
    assertNotNull(repository.presetAvatars)
    // Initially empty until cloud sync completes
    assertEquals(0, repository.presetPhotos.value.size)
    assertEquals(0, repository.presetAvatars.value.size)
  }

  @Test
  fun `initializeDefaultDataIfEmpty seeds the full GiftIdeasSeed catalog, not the old 2-item placeholder`() = runBlocking {
    repository.initializeDefaultDataIfEmpty(com.example.ui.util.AppLanguage.EN)
    val giftIdeas = db.inLoveDao().getAllGiftIdeas().first()

    assertEquals(
      "must seed every GiftIdeasSeed entry",
      com.example.data.seed.GiftIdeasSeed.all.size,
      giftIdeas.size
    )
    assertTrue(
      "seeding with AppLanguage.EN must produce English titles, not Vietnamese",
      giftIdeas.any { it.title == "Everlasting Rose Bouquet" }
    )
  }

  @Test
  fun `custom milestones and gift ideas created locally coexist instead of replacing each other`() = runBlocking {
    fun milestone(title: String) = com.example.data.model.MilestoneEntity(
      title = title, dateText = "", subtitle = "", categoryTag = "", secondaryTag = "", imageUrl = "", daysRemaining = 1
    )
    repository.addMilestone(milestone("A"))
    repository.addMilestone(milestone("B"))
    repository.addMilestone(milestone("C"))
    assertEquals(3, db.inLoveDao().getAllMilestones().first().size)

    fun gift(title: String) = com.example.data.model.GiftIdeaEntity(
      title = title, category = "", badgeText = "", tag = "", description = "", imageUrl = ""
    )
    db.inLoveDao().insertGiftIdeas(listOf(gift("x"), gift("y")))
    assertEquals(2, db.inLoveDao().getAllGiftIdeas().first().size)
  }

  @Test
  fun `seeding the gift catalog twice is idempotent`() = runBlocking {
    val all = com.example.data.seed.GiftIdeasSeed.all
    db.inLoveDao().insertGiftIdeas(all.map { it.toEntity(com.example.ui.util.AppLanguage.VI) })
    db.inLoveDao().insertGiftIdeas(all.map { it.toEntity(com.example.ui.util.AppLanguage.VI) })
    assertEquals(all.size, db.inLoveDao().getAllGiftIdeas().first().size)
  }

  @Test
  fun `ensureSeedGiftCatalog adopts a collapsed legacy row, keeping its id and favorite`() = runBlocking {
    val playlist = com.example.data.seed.GiftIdeasSeed.all.last()
    db.inLoveDao().insertGiftIdeas(listOf(playlist.toEntity(com.example.ui.util.AppLanguage.EN).copy(remoteId = "", isFavorited = true)))
    val legacyId = db.inLoveDao().getAllGiftIdeas().first().single().id

    repository.ensureSeedGiftCatalog(com.example.ui.util.AppLanguage.EN)

    val rows = db.inLoveDao().getAllGiftIdeas().first()
    assertEquals("one legacy row plus nineteen seeds must become twenty", com.example.data.seed.GiftIdeasSeed.all.size, rows.size)
    assertTrue("no blank remoteId may remain", rows.none { it.remoteId.isBlank() })
    val adopted = rows.single { it.remoteId == playlist.remoteId }
    assertEquals("the adopted row keeps its id", legacyId, adopted.id)
    assertTrue("the adopted row keeps its favorite", adopted.isFavorited)
  }

  @Test
  fun `ensureSeedGiftCatalog run twice keeps twenty rows and every favorite`() = runBlocking {
    val all = com.example.data.seed.GiftIdeasSeed.all
    db.inLoveDao().insertGiftIdeas(all.map { it.toEntity(com.example.ui.util.AppLanguage.VI) })
    val bracelet = db.inLoveDao().getGiftIdeaByRemoteId(all[3].remoteId)!!
    db.inLoveDao().updateGiftIdea(bracelet.copy(isFavorited = true))

    repository.ensureSeedGiftCatalog(com.example.ui.util.AppLanguage.EN)
    repository.ensureSeedGiftCatalog(com.example.ui.util.AppLanguage.EN)

    val rows = db.inLoveDao().getAllGiftIdeas().first()
    assertEquals("two runs must not duplicate seeds", all.size, rows.size)
    assertTrue("a favorite survives every re-seed", rows.single { it.remoteId == all[3].remoteId }.isFavorited)
  }

  @Test
  fun `initializeDefaultDataIfEmpty seeds holiday anniversaries from VietnameseHolidays and WesternHolidays`() = runBlocking {
    repository.initializeDefaultDataIfEmpty(com.example.ui.util.AppLanguage.VI)
    val viHolidays = db.inLoveDao().getAllAnniversaryDates().first()

    // 15 fixed + up to 5 lunar (some lunar entries may fall outside the verified-year table
    // around the seam of a calendar year and get skipped rather than guessed)
    assertTrue(
      "expected at least the 15 fixed Vietnamese holidays, got ${viHolidays.size}",
      viHolidays.size >= com.example.data.seed.VietnameseHolidays.fixedHolidays.size
    )
    assertTrue(
      "every seeded holiday dateText must be parseable dd/MM/yyyy",
      viHolidays.all { com.example.ui.components.DatePickerUtils.parseDateToUtcMillis(it.dateText) != null }
    )
    assertTrue(
      "Tết Dương Lịch should be present",
      viHolidays.any { it.title.contains("Tết Dương Lịch") }
    )
  }
}
