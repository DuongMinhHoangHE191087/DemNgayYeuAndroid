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
}
