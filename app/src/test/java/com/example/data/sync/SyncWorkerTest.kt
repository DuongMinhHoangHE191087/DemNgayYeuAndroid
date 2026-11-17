package com.example.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.RelationshipStatus
import com.example.data.model.SharedMemoryEntity
import com.example.data.model.SyncOutboxEntity
import java.util.concurrent.CancellationException
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
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { _, _ -> /* no-op success */ }, cloudContent = { true }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Success)
    assert(dao.getPendingOutboxEntries().none { it.id == outboxId }) {
      "a successfully pushed outbox entry must be removed"
    }
    db.close()
  }

  @Test
  fun localOnly_doWorkKeepsOutboxAndSucceeds() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    enqueueMemories(dao, count = 1)

    val pushed = mutableListOf<String>()
    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ -> pushed += path }, cloudContent = { false }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Success)
    assert(pushed.isEmpty()) { "local-only must never push, got $pushed" }
    val row = dao.getPendingOutboxEntries(limit = 100).single()
    assert(row.attemptCount == 0 && row.lastError == null) { "the outbox row must stay untouched" }
    db.close()
  }

  @Test
  fun doWork_poisonRow_isKeptWithReason_andDoesNotBlockHealthyRows() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    val adapter = SyncWorker.moshiAdapterFor<SharedMemoryEntity>()

    // Dòng hỏng: memory thiếu relationshipId. Được đỗ: giữ lại, không tính lỗi, không retry.
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
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ -> pushed += path }, cloudContent = { true }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Success) { "a parked row is not a failure, so no endless retry" }
    assert(pushed == listOf("relationships/rel_1/memories/sync-ok")) { "the healthy row must not be blocked by the poison row" }
    val remaining = dao.getPendingOutboxEntries()
    assert(remaining.none { it.syncId == "sync-ok" }) { "the healthy row must be removed after a successful push" }
    val poison = remaining.single { it.id == poisonId }
    assert(poison.attemptCount == 0) { "a parked row is not counted as a failed attempt" }
    assert(poison.lastError == null) { "a parked row must not call markOutboxAttemptFailed" }
    db.close()
  }

  @Test
  fun doWork_invalidJsonMemoryRow_isParkedKeptAndNotPushed() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    // Không phải JSON hợp lệ (JsonEncodingException), JSON sai kiểu (JsonDataException) và "null" (decode ra null).
    val badPayloads = listOf("{not json", "{\"title\": 123, \"dateText\": [1]}", "null")
    badPayloads.forEachIndexed { i, payload ->
      dao.insertOutboxEntry(
        SyncOutboxEntity(entityType = MemorySyncAdapter.entityType, syncId = "bad-$i", operation = "UPSERT", payloadJson = payload)
      )
    }

    val pushed = mutableListOf<String>()
    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ -> pushed += path }, cloudContent = { true }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Success) { "unpushable payloads must not force a retry" }
    assert(pushed.isEmpty()) { "push must not be called for a bad payload, got $pushed" }
    val remaining = dao.getPendingOutboxEntries(limit = 100)
    assert(remaining.size == badPayloads.size) { "parked rows must stay in the outbox" }
    assert(remaining.all { it.attemptCount == 0 && it.lastError == null }) { "parked rows are not failed attempts" }
    db.close()
  }

  @Test
  fun doWork_memoryWithNullRelationshipId_isParkedAndKept() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    val memory = SharedMemoryEntity(
      title = "x", dateText = "2026-01-01", photoUri = "file:///a.jpg",
      relationshipId = null, syncId = "no-rel", updatedAt = 1L, pendingSync = true
    )
    dao.insertOutboxEntry(
      outboxFor(MemorySyncAdapter.entityType, "no-rel", "UPSERT", SyncWorker.moshiAdapterFor<SharedMemoryEntity>().toJson(memory))
    )

    val pushed = mutableListOf<String>()
    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ -> pushed += path }, cloudContent = { true }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Success)
    assert(pushed.isEmpty())
    val row = dao.getPendingOutboxEntries(limit = 100).single()
    assert(row.attemptCount == 0 && row.lastError == null)
    db.close()
  }

  @Test
  fun doWork_unknownEntityType_isParkedAndKept_andGoodRowAfterItIsPushed() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    dao.insertOutboxEntry(outboxFor("alien_type", "alien-1", "UPSERT", "{}"))
    enqueueMemories(dao, count = 1)

    val pushed = mutableListOf<String>()
    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ -> pushed += path }, cloudContent = { true }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Success)
    assert(pushed == listOf("relationships/rel_1/memories/sync-0")) { "the good row must go out, got $pushed" }
    val remaining = dao.getPendingOutboxEntries(limit = 100)
    assert(remaining.map { it.syncId } == listOf("alien-1")) { "only the unknown-type row may stay" }
    assert(remaining.single().attemptCount == 0)
    db.close()
  }

  @Test
  fun doWork_endedRelationship_isParkedAndKept_thenPushedOnceActive() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    enqueueMemories(dao, count = 1)
    val pushed = mutableListOf<String>()
    val factory = SyncWorkerFactory(dao, fakeFirestorePush = { path, _ -> pushed += path }, cloudContent = { true })

    for (status in listOf(RelationshipStatus.TERMINATED, RelationshipStatus.PENDING_BREAKUP)) {
      dao.insertOnlineRelationship(OnlineRelationshipEntity(relationshipId = "rel_1", user1 = "u1", user2 = "u2", startDate = 1L, status = status))
      val result = TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build().doWork()
      assert(result is ListenableWorker.Result.Success) { "$status: a parked row is not a failure" }
      assert(pushed.isEmpty()) { "$status: nothing may be pushed, got $pushed" }
      val row = dao.getPendingOutboxEntries(limit = 100).single()
      assert(row.attemptCount == 0 && row.lastError == null) { "$status: the row must be kept untouched" }
    }

    dao.insertOnlineRelationship(OnlineRelationshipEntity(relationshipId = "rel_1", user1 = "u1", user2 = "u2", startDate = 1L, status = RelationshipStatus.ACTIVE))
    val result = TestListenableWorkerBuilder<SyncWorker>(context).setWorkerFactory(factory).build().doWork()

    assert(result is ListenableWorker.Result.Success)
    assert(pushed == listOf("relationships/rel_1/memories/sync-0")) { "the row must go out once ACTIVE, got $pushed" }
    assert(dao.getPendingOutboxEntries(limit = 100).isEmpty()) { "the pushed row must be removed" }
    db.close()
  }

  @Test
  fun doWork_cancellationFromPush_isRethrown_andRowIsNotMarkedFailed() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()
    enqueueMemories(dao, count = 1)

    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { _, _ -> throw CancellationException("stopped") }, cloudContent = { true }))
      .build()

    var rethrown = false
    try {
      worker.doWork()
    } catch (e: CancellationException) {
      rethrown = true
    }

    assert(rethrown) { "CancellationException must escape doWork, not be swallowed as a failed row" }
    val row = dao.getPendingOutboxEntries(limit = 100).single()
    assert(row.attemptCount == 0 && row.lastError == null) { "a cancelled push is not a failed attempt" }
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
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { path, _ -> pushed += path }, cloudContent = { true }))
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
      }, cloudContent = { true }))
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
      }, cloudContent = { true }))
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
