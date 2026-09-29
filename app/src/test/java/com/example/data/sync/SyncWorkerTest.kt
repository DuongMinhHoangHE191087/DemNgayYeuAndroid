package com.example.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.db.AppDatabase
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
}
