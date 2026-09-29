package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InLoveRepositorySyncTest {

  @Test
  fun addSharedMemory_assignsSyncId_andEnqueuesOutboxEntry() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val repo = InLoveRepository(db.inLoveDao(), context)

    repo.addSharedMemory(title = "Đi biển", dateText = "2026-06-01", photoUri = "file:///beach.jpg")

    val saved = db.inLoveDao().getAllSharedMemories().first().first()
    assert(saved.syncId.isNotBlank()) { "addSharedMemory must assign a syncId" }
    assert(saved.pendingSync) { "a freshly created memory is pending push" }

    val outbox = db.inLoveDao().getPendingOutboxEntries()
    assert(outbox.size == 1)
    assert(outbox.first().entityType == "memory")
    assert(outbox.first().syncId == saved.syncId)
    db.close()
  }
}
