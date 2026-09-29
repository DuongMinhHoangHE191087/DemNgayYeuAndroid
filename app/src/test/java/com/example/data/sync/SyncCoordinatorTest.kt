package com.example.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
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
}
