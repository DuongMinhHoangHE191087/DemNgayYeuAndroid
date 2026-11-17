package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.RelationshipStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An unpair keeps memories and anniversaries and only hides them (see RelationshipVisibility.kt). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InLoveRepositoryVisibilityTest {

  private lateinit var db: AppDatabase
  private lateinit var repo: InLoveRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    repo = InLoveRepository(db.inLoveDao(), context)
  }

  @After
  fun tearDown() {
    db.close()
  }

  private suspend fun rel(id: String, a: String, b: String, status: String) =
    db.inLoveDao().insertOnlineRelationship(
      OnlineRelationshipEntity(relationshipId = id, user1 = a, user2 = b, startDate = 0L, status = status)
    )

  private suspend fun seed(relationshipId: String?) {
    repo.addSharedMemory(title = "mem-$relationshipId", dateText = "2026-06-01", photoUri = "file:///a.jpg", relationshipId = relationshipId)
    repo.addAnniversaryDate(title = "ann-$relationshipId", dateText = "01/06/2026", relationshipId = relationshipId)
  }

  private suspend fun memories() = repo.sharedMemories.first().map { it.title }.toSet()
  private suspend fun anniversaries() = repo.anniversaryDates.first().map { it.title }.toSet()
  private suspend fun anniversaryList() = repo.getAnniversaryDatesList().map { it.title }.toSet()

  @Test
  fun terminatedOnly_hidesScopedRows_butKeepsThemInRoom() = runBlocking {
    rel("R1", "A", "B", RelationshipStatus.ACTIVE)
    seed("R1")
    rel("R1", "A", "B", RelationshipStatus.TERMINATED)

    assert(memories().isEmpty()) { "ex memory must be hidden while single" }
    assert(anniversaries().isEmpty()) { "ex anniversary must be hidden while single" }
    assert(anniversaryList().isEmpty()) { "alarm path list must hide the ex anniversary too" }
    assert(db.inLoveDao().getAllSharedMemories().first().size == 1) { "hiding never deletes the row" }
  }

  @Test
  fun samePartnerActiveAgain_bringsRowsBack() = runBlocking {
    rel("R1", "A", "B", RelationshipStatus.ACTIVE)
    seed("R1")
    rel("R1", "A", "B", RelationshipStatus.TERMINATED)
    rel("R2", "B", "A", RelationshipStatus.ACTIVE)

    assert(memories() == setOf("mem-R1")) { "same pair (any order) restores the memory" }
    assert(anniversaries() == setOf("ann-R1"))
    assert(anniversaryList() == setOf("ann-R1"))
  }

  @Test
  fun newPartner_neverSeesExRows_butSeesNewOnes() = runBlocking {
    rel("R1", "A", "B", RelationshipStatus.ACTIVE)
    seed("R1")
    rel("R1", "A", "B", RelationshipStatus.TERMINATED)
    rel("R3", "A", "C", RelationshipStatus.ACTIVE)
    seed("R3")

    assert(memories() == setOf("mem-R3")) { "ex memory must not leak to the new partner: ${memories()}" }
    assert(anniversaries() == setOf("ann-R3"))
    assert(anniversaryList() == setOf("ann-R3"))
  }

  @Test
  fun rowsWithoutRelationshipId_areAlwaysVisible() = runBlocking {
    seed(null)
    assert(memories() == setOf("mem-null")) { "solo memory visible with no relationship" }
    assert(anniversaries() == setOf("ann-null"))

    rel("R1", "A", "B", RelationshipStatus.TERMINATED)
    assert(memories() == setOf("mem-null")) { "solo memory stays visible after a breakup" }
    assert(anniversaryList() == setOf("ann-null"))
  }
}
