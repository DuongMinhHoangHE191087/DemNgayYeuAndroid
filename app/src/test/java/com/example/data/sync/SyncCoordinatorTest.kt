package com.example.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.SharedMemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
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
}
