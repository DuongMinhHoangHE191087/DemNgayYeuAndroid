package com.example.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.SharedMemoryEntity
import com.example.data.model.SyncOutboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {

  @Test
  fun doWork_drainsOnePendingOutboxEntry_andRemovesItOnSuccess() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()

    val memory = SharedMemoryEntity(
      title = "Test", dateText = "2026-01-01", photoUri = "file:///a.jpg",
      relationshipId = "rel_1", syncId = "sync-1", updatedAt = 1L, pendingSync = true
    )
    val outboxId = dao.insertOutboxEntry(
      SyncOutboxEntity(
        entityType = MemorySyncAdapter.entityType,
        syncId = "sync-1",
        operation = "UPSERT",
        payloadJson = SyncWorker.moshiAdapterFor<SharedMemoryEntity>().toJson(memory)
      )
    )

    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { _, _ -> /* no-op success */ }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Success)
    assert(dao.getPendingOutboxEntries().none { it.id == outboxId }) {
      "a successfully pushed outbox entry must be removed"
    }
    db.close()
  }

  @Test
  fun doWork_poisonRow_isKeptWithReason_andDoesNotBlockHealthyRows() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    val adapter = SyncWorker.moshiAdapterFor<SharedMemoryEntity>()

    // Dòng hỏng: memory thiếu relationshipId. Trước đây bị xoá im lặng; giờ phải giữ lại và ghi lý do.
    val poisonId = dao.insertOutboxEntry(
      SyncOutboxEntity(
        entityType = MemorySyncAdapter.entityType,
        syncId = "sync-poison",
        operation = "UPSERT",
        payloadJson = adapter.toJson(
          SharedMemoryEntity(
            title = "Hỏng", dateText = "2026-01-01", photoUri = "file:///a.jpg",
            relationshipId = null, syncId = "sync-poison", updatedAt = 1L, pendingSync = true
          )
        )
      )
    )
    dao.insertOutboxEntry(
      SyncOutboxEntity(
        entityType = MemorySyncAdapter.entityType,
        syncId = "sync-ok",
        operation = "UPSERT",
        payloadJson = adapter.toJson(
          SharedMemoryEntity(
            title = "Tốt", dateText = "2026-01-01", photoUri = "file:///b.jpg",
            relationshipId = "rel_1", syncId = "sync-ok", updatedAt = 2L, pendingSync = true
          )
        )
      )
    )

    val pushed = mutableListOf<String>()
    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ -> pushed += path }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Retry) { "a failed row must ask for a retry, not report success" }
    assert(pushed == listOf("relationships/rel_1/memories/sync-ok")) { "the healthy row must not be blocked by the poison row" }
    val remaining = dao.getPendingOutboxEntries()
    assert(remaining.none { it.syncId == "sync-ok" }) { "the healthy row must be removed after a successful push" }
    val poison = remaining.single { it.id == poisonId }
    assert(poison.attemptCount >= 1) { "the poison row must record the failed attempt" }
    assert(poison.lastError?.startsWith("outbox $poisonId") == true) { "lastError must name the entry id" }
    db.close()
  }

  @Test
  fun doWork_drainsEveryPendingRow_notJustTheFirstBatch() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    enqueueMemories(dao, count = 25)

    val pushed = mutableListOf<String>()
    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ -> pushed += path }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Success) { "a fully drained queue must report success" }
    assert(pushed.size == 25) { "all 25 writes must go out in one run, got ${pushed.size}" }
    assert(dao.getPendingOutboxEntries(limit = 100).isEmpty()) { "no row may stay queued after a clean run" }
    db.close()
  }

  @Test
  fun doWork_persistentlyFailingRow_doesNotHoldBackHealthyRowsInTheSameRun() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    enqueueMemories(dao, count = 25)

    val pushed = mutableListOf<String>()
    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ ->
        if (path.endsWith("/sync-3")) error("network down for sync-3")
        pushed += path
      }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Retry) { "a failed row must ask for a retry" }
    assert(pushed.size == 24) { "the 24 healthy writes must go out in the same run, got ${pushed.size}" }
    val remaining = dao.getPendingOutboxEntries(limit = 100)
    assert(remaining.map { it.syncId } == listOf("sync-3")) { "only the failing row may stay queued" }
    assert(remaining.single().attemptCount >= 1) { "the failing row must record its attempt" }
    db.close()
  }

  @Test
  fun doWork_scopeSwitchDuringPush_acknowledgesNothingAndStartsNoFurtherWrites() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    enqueueMemories(dao, count = 3)

    val pushed = mutableListOf<String>()
    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ ->
        pushed += path
        // Another account takes the device while this write is in flight.
        if (path.endsWith("/sync-0")) com.example.data.db.AccountDataVault.epoch.incrementAndGet()
      }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Retry) { "a scope switch mid-run must end the run as a retry" }
    assert(pushed == listOf("relationships/rel_1/memories/sync-0")) { "no write may start after the switch, got $pushed" }
    assert(dao.getPendingOutboxEntries(limit = 100).size == 3) { "nothing may be acknowledged after the switch" }
    db.close()
  }

  @Test
  fun anniversaryDelete_replacesTheQueuedUpsertForTheSameSyncId() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    val adapter = SyncWorker.moshiAdapterFor<AnniversaryDateEntity>()
    val item = AnniversaryDateEntity(
      title = "Ngày quen", dateText = "2026-02-14", relationshipId = "rel_1",
      syncId = "ann-1", updatedAt = 1L, pendingSync = true
    )
    val id = dao.insertAnniversaryDateWithOutbox(item, outboxFor(AnniversarySyncAdapter.entityType, "ann-1", "UPSERT", adapter.toJson(item)))

    dao.deleteAnniversaryDateWithOutbox(id, deletedAt = 2L, outbox = outboxFor(AnniversarySyncAdapter.entityType, "ann-1", "DELETE", adapter.toJson(item.copy(id = id, deleted = true, updatedAt = 2L))))

    val queued = dao.getPendingOutboxEntries(limit = 100).filter { it.syncId == "ann-1" }
    assert(queued.map { it.operation } == listOf("DELETE")) {
      "the tombstone must replace the queued upsert, or a skewed clock can resurrect the anniversary; got ${queued.map { it.operation }}"
    }
    db.close()
  }

  @Test
  fun anniversaryEdit_keepsOneQueuedUpsertPerSyncId() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    val adapter = SyncWorker.moshiAdapterFor<AnniversaryDateEntity>()
    val item = AnniversaryDateEntity(
      title = "first", dateText = "2026-02-14", relationshipId = "rel_1",
      syncId = "ann-1", updatedAt = 1L, pendingSync = true
    )
    val id = dao.insertAnniversaryDateWithOutbox(item, outboxFor(AnniversarySyncAdapter.entityType, "ann-1", "UPSERT", adapter.toJson(item)))

    val edited = item.copy(id = id, title = "edited", updatedAt = 3L)
    dao.updateAnniversaryDateWithOutbox(edited, outboxFor(AnniversarySyncAdapter.entityType, "ann-1", "UPSERT", adapter.toJson(edited)))

    val queued = dao.getPendingOutboxEntries(limit = 100).filter { it.syncId == "ann-1" }
    assert(queued.size == 1) { "an edit must replace the queued snapshot, got ${queued.size} rows" }
    assert(queued.single().payloadJson.contains("\"edited\"")) { "the queued row must carry the latest snapshot" }
    db.close()
  }

  private fun outboxFor(entityType: String, syncId: String, operation: String, payloadJson: String) =
    SyncOutboxEntity(entityType = entityType, syncId = syncId, operation = operation, payloadJson = payloadJson)

  private suspend fun enqueueMemories(dao: InLoveDao, count: Int) {
    val adapter = SyncWorker.moshiAdapterFor<SharedMemoryEntity>()
    repeat(count) { i ->
      dao.insertOutboxEntry(
        SyncOutboxEntity(
          entityType = MemorySyncAdapter.entityType,
          syncId = "sync-$i",
          operation = "UPSERT",
          payloadJson = adapter.toJson(
            SharedMemoryEntity(
              title = "m$i", dateText = "2026-01-01", photoUri = "file:///$i.jpg",
              relationshipId = "rel_1", syncId = "sync-$i", updatedAt = i.toLong(), pendingSync = true
            )
          )
        )
      )
    }
  }
}
