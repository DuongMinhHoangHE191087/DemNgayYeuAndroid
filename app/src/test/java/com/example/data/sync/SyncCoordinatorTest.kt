package com.example.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.SharedMemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncCoordinatorTest {

  @Test
  fun applyRemoteAnniversary_keepsLocalPendingEdit_whenRemoteUpdateIsOlder() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()

    val local = AnniversaryDateEntity(
      title = "Local edit", dateText = "2026-01-01", syncId = "sync-1",
      updatedAt = 2000L, pendingSync = true
    )
    dao.insertAnniversaryDate(local)

    val coordinator = SyncCoordinator(dao, firestore = null, scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))
    val olderRemote = AnniversaryDateEntity(title = "Remote (stale)", dateText = "2026-01-01", syncId = "sync-1", updatedAt = 1000L)

    coordinator.applyRemoteAnniversary(olderRemote)

    val stored = dao.getAllAnniversaryDates().first().first { it.syncId == "sync-1" }
    assert(stored.title == "Local edit") { "an older remote update must not overwrite a newer local pending edit" }
    db.close()
  }

  @Test
  fun applyRemoteMemory_withDeletedRemote_andNoLocalRow_doesNotResurrectIt() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    val coordinator = SyncCoordinator(dao, firestore = null, scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))

    // Simulates the other device's tombstone (or a stale/replayed snapshot of one) arriving
    // for an item this device never synced down while it was still alive.
    val deletedRemote = SharedMemoryEntity(
      title = "Old memory", dateText = "2026-01-01", photoUri = "file:///a.jpg",
      syncId = "sync-never-seen", updatedAt = 5000L, deleted = true
    )
    coordinator.applyRemoteMemory(deletedRemote)

    assert(dao.getSharedMemoryBySyncId("sync-never-seen") == null) {
      "a tombstone with no matching local row must not create a dead row"
    }
    db.close()
  }

  @Test
  fun applyRemoteMemory_echoOfOwnTombstone_doesNotResurrectSoftDeletedLocalRow() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    val coordinator = SyncCoordinator(dao, firestore = null, scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))

    val localId = dao.insertSharedMemory(
      SharedMemoryEntity(title = "To delete", dateText = "2026-01-01", photoUri = "file:///a.jpg", syncId = "sync-2", updatedAt = 1000L)
    )
    // This device deleted it locally (soft delete — the row survives, marked deleted).
    dao.softDeleteSharedMemoryById(localId, deletedAt = 2000L)
    assert(dao.getAllSharedMemories().first().none { it.syncId == "sync-2" }) { "soft-deleted rows must not appear in getAllSharedMemories" }

    // The content listener then echoes this device's own tombstone push back.
    val echoedTombstone = SharedMemoryEntity(
      title = "To delete", dateText = "2026-01-01", photoUri = "file:///a.jpg",
      syncId = "sync-2", updatedAt = 2000L, deleted = true
    )
    coordinator.applyRemoteMemory(echoedTombstone)

    assert(dao.getAllSharedMemories().first().none { it.syncId == "sync-2" }) {
      "a resurrection bug would re-show this item as live after the remote echo"
    }
    db.close()
  }

  // --- K1: snapshots are applied in order, through plain-data seams (no QuerySnapshot needed) ---

  private fun rel(id: String, status: Any?) =
    id to mapOf<String, Any?>("user1" to "me", "user2" to "partner", "status" to status, "startDate" to 1L)

  private fun newDb() = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext<android.content.Context>(), AppDatabase::class.java
  ).allowMainThreadQueries().build()

  // firestore = null: start() still launches the sequential consumer, but registers no Firestore listener.
  private fun startedCoordinator(db: AppDatabase) =
    SyncCoordinator(db.inLoveDao(), firestore = null, scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))
      .also { it.start("me") }

  private suspend fun insertCoupledMe(db: AppDatabase) = db.inLoveDao().insertOnlineUser(
    OnlineUserEntity(uid = "me", coupleCode = "CODE", status = OnlineStatus.COUPLED, partnerId = "partner", relationshipId = "r1")
  )

  @Test
  fun relationshipSnapshots_areAppliedInOrder_lastOneWins() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    insertCoupledMe(db)
    val coordinator = startedCoordinator(db)

    coordinator.handleRelationshipDocs("me", listOf(rel("r1", "ACTIVE")))
    coordinator.handleRelationshipDocs("me", listOf(rel("r1", "TERMINATED")))
    coordinator.awaitApplied()

    assertEquals("TERMINATED", dao.getOnlineRelationshipSync("r1")!!.status)
    assertEquals(OnlineStatus.SINGLE, dao.getOnlineUserByUidSync("me")!!.status)
    coordinator.stop()
    db.close()
  }

  @Test
  fun errorSnapshot_andEmptyCacheSnapshot_changeNothingForCoupledUser() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    insertCoupledMe(db)
    val coordinator = startedCoordinator(db)
    coordinator.handleRelationshipDocs("me", listOf(rel("r1", "ACTIVE")))
    coordinator.awaitApplied()
    assertEquals(OnlineStatus.COUPLED, dao.getOnlineUserByUidSync("me")!!.status)

    coordinator.handleRelationshipDocs("me", null) // listener error / null snapshot
    coordinator.handleRelationshipDocs("me", emptyList(), fromCache = true) // "unknown", not "no relationship"
    coordinator.awaitApplied()

    assertEquals(OnlineStatus.COUPLED, dao.getOnlineUserByUidSync("me")!!.status)
    assertEquals("ACTIVE", dao.getOnlineRelationshipSync("r1")!!.status)

    // Control: an empty snapshot confirmed by the server really means no relationship.
    coordinator.handleRelationshipDocs("me", emptyList(), fromCache = false)
    coordinator.awaitApplied()
    assertEquals(OnlineStatus.SINGLE, dao.getOnlineUserByUidSync("me")!!.status)
    coordinator.stop()
    db.close()
  }

  @Test
  fun badDocuments_areSkipped_restOfSnapshotStillApplied() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    val coordinator = startedCoordinator(db)

    coordinator.handleRelationshipDocs(
      "me", listOf("bad" to mapOf<String, Any?>("user1" to listOf("x"), "user2" to "partner"), rel("r2", "ACTIVE"))
    )
    coordinator.handleInviteDocs(
      listOf("inv-bad" to mapOf<String, Any?>("senderUid" to 5L), "inv-ok" to mapOf<String, Any?>("senderUid" to "u9"))
    )
    coordinator.handleMemoryDocs(
      "r2", listOf(
        "m1" to mapOf<String, Any?>("syncId" to "s1", "title" to 5L, "relationshipId" to "r2"),
        "m2" to mapOf<String, Any?>("syncId" to "s2", "title" to "T", "relationshipId" to "r2"),
        "m3" to mapOf<String, Any?>("syncId" to "s3", "title" to "T", "relationshipId" to "other")
      )
    )
    coordinator.handleAnniversaryDocs(
      "r2", listOf(
        "a1" to mapOf<String, Any?>("syncId" to "as1", "title" to listOf(1)),
        "a2" to mapOf<String, Any?>("syncId" to "as2", "title" to "T", "relationshipId" to "r2")
      )
    )
    coordinator.awaitApplied()

    assertNull(dao.getOnlineRelationshipSync("bad"))
    assertEquals("ACTIVE", dao.getOnlineRelationshipSync("r2")!!.status)
    assertNull(dao.getInviteByIdSync("inv-bad"))
    assertNotNull(dao.getInviteByIdSync("inv-ok"))
    assertNull(dao.getSharedMemoryBySyncId("s1"))
    assertNotNull(dao.getSharedMemoryBySyncId("s2"))
    assertNull(dao.getSharedMemoryBySyncId("s3")) // another relationship's doc is ignored, as before
    assertNull(dao.getAnniversaryDateBySyncId("as1"))
    assertNotNull(dao.getAnniversaryDateBySyncId("as2"))
    coordinator.stop()
    db.close()
  }

  @Test
  fun snapshotAfterStop_isDropped() = runBlocking {
    val db = newDb()
    val dao = db.inLoveDao()
    val coordinator = startedCoordinator(db)
    val staleGen = 0L // any generation older than the current one
    coordinator.stop()

    coordinator.handleRelationshipDocs("me", listOf(rel("r9", "ACTIVE")), gen = staleGen)

    assertNull(dao.getOnlineRelationshipSync("r9"))
    db.close()
  }
}
